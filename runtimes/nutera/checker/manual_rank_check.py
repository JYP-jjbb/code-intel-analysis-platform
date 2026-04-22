#!/usr/bin/env python3
# Internal checker implementation — invoked via checker/rf_check.py.
# This file should not be called directly by end users; use rf_check.py instead.
#
# Standalone CLI usage (via rf_check.py):
#   python checker/rf_check.py --jar path/to/program.jar --rank "ReLU(n-i)"
#   python checker/rf_check.py --jar program.jar --rank "[ReLU(n-i), ReLU(m-j)]" --delta 1
#
# Supported rank expression formats:
#   Scalar: ReLU(n - i) + 2*ReLU(m - j + 1)
#   Vector: [ReLU(n - i), ReLU(m - j)]
#
# where ReLU(x) = max(x, 0) and the interior must be an affine expression.

import os
import sys
import pathlib
import argparse
import json
import re
import zipfile
import inspect
import subprocess
import importlib.util

ROOT = pathlib.Path(__file__).resolve().parent.parent

# Ensure repo root is on sys.path so that checker.checker_bridge is importable
# regardless of how this script is invoked (direct, runpy, subprocess, etc.)
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from checker.runtime_bootstrap import ensure_runtime_prepared

_RUNTIME_BOOTSTRAP = ensure_runtime_prepared(print_diagnostics=True)

import sympy as sp
import numpy as np

from checker.checker_bridge.javachecker import check_sum_of_relu, check_sum_of_relu_vec
from checker.checker_bridge.loopheads import get_loop_heads


def _find_java_source(jar_path: str, class_name: str):
    d = os.path.dirname(os.path.abspath(jar_path))
    cands = [
        os.path.join(d, f"{class_name}.java"),
        os.path.join(d, f"{os.path.splitext(os.path.basename(jar_path))[0]}.java"),
    ]
    for p in cands:
        if os.path.exists(p):
            return p
    # Shallow fallback search (depth <= 2)
    base = d
    for root, dirs, files in os.walk(base):
        rel = os.path.relpath(root, base)
        if rel.count(os.sep) >= 2:
            dirs[:] = []
        if f"{class_name}.java" in files:
            return os.path.join(root, f"{class_name}.java")
    return None


def _infer_assume_from_main(java_path: str, target_method: str, class_name: str) -> str:
    import re

    src = open(java_path, "r", encoding="utf-8", errors="ignore").read()

    # ---------------- helpers ----------------
    def _extract_brace_block(s: str, open_idx: int):
        if open_idx < 0 or open_idx >= len(s) or s[open_idx] != "{":
            return None, None
        depth = 0
        for i in range(open_idx, len(s)):
            ch = s[i]
            if ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0:
                    return s[open_idx + 1: i], i
        # Prefix may be truncated before matching '}'; treat rest as partial block.
        return s[open_idx + 1:], len(s)

    def _strip_comments(s: str) -> str:
        s = re.sub(r"/\*.*?\*/", "", s, flags=re.S)
        s = re.sub(r"//.*", "", s)
        return s

    def _find_matching_paren(s: str, p0: int):
        depth = 0
        for i in range(p0, len(s)):
            if s[i] == "(":
                depth += 1
            elif s[i] == ")":
                depth -= 1
                if depth == 0:
                    return i
        return None

    def _extract_call_args(main_body: str, call_start: int):
        p0 = main_body.find("(", call_start)
        if p0 < 0:
            return []
        p1 = _find_matching_paren(main_body, p0)
        if p1 is None:
            return []
        arg_str = main_body[p0 + 1 : p1]

        args = []
        cur = []
        depth = 0
        for ch in arg_str:
            if ch == "(":
                depth += 1
                cur.append(ch)
            elif ch == ")":
                depth -= 1
                cur.append(ch)
            elif ch == "," and depth == 0:
                args.append("".join(cur).strip())
                cur = []
            else:
                cur.append(ch)
        if cur:
            args.append("".join(cur).strip())
        return args

    def _is_int_literal(s: str):
        s0 = re.sub(r"\s+", "", s)
        return re.fullmatch(r"-?\d+", s0) is not None

    def _is_identifier(s: str):
        s0 = re.sub(r"\s+", "", s)
        return re.fullmatch(r"[A-Za-z_]\w*", s0) is not None

    def _has_length(s: str):
        s0 = re.sub(r"\s+", "", s)
        return (".length()" in s0) or (re.search(r"\.length\b", s0) is not None)

    # ---------------- 1) formals (order matters) ----------------
    m_sig = re.search(rf"\bvoid\s+{re.escape(target_method)}\s*\((.*?)\)\s*\{{", src, re.S)
    if not m_sig:
        return ""
    sig_inside = m_sig.group(1)
    parts = [p.strip() for p in sig_inside.split(",") if p.strip()]
    formal = []
    for p in parts:
        mm = re.search(r"\b(byte|short|int|long)\s+([A-Za-z_]\w*)\b", p)
        if mm:
            formal.append(mm.group(2))
    if not formal:
        return ""
    formal_set = set(formal)

    # ---------------- 2) main body ----------------
    m_main = re.search(r"\bvoid\s+main\s*\([^)]*\)\s*\{", src)
    if not m_main:
        return ""
    main_open = src.find("{", m_main.end() - 1)
    if main_open < 0:
        return ""
    main_body, _ = _extract_brace_block(src, main_open)
    if main_body is None:
        return ""

    # ---------------- 3) locate call ----------------
    m_call = re.search(rf"\b{re.escape(target_method)}\s*\(", main_body)
    if not m_call:
        return ""
    call_pos = m_call.start()
    call_args = _extract_call_args(main_body, call_pos)
    prefix = _strip_comments(main_body[:call_pos])

    atoms = []

    # Build mapping: actual identifier -> which formal receives it
    id_to_formals = {}
    for i in range(min(len(formal), len(call_args))):
        a0 = re.sub(r"\s+", "", call_args[i])
        if _is_identifier(a0):
            id_to_formals.setdefault(a0, []).append(formal[i])

    # ---------------- 4) enclosing-if conditions ----------------
    enclosing_conds = []
    for m in re.finditer(r"\bif\s*\((.*?)\)\s*\{", main_body, re.S):
        cond = m.group(1).strip()
        brace_pos = main_body.find("{", m.end() - 1)
        if brace_pos < 0:
            continue
        body, close = _extract_brace_block(main_body, brace_pos)
        if body is None:
            continue
        if brace_pos < call_pos < close:
            enclosing_conds.append(cond)

    for cond in enclosing_conds:
        for part in re.split(r"\s*&&\s*", cond):
            part = part.strip()
            mcmp = re.match(r"^([A-Za-z_]\w*)\s*(>=|<=|==|=|>|<)\s*(-?\d+|[A-Za-z_]\w*)$", part)
            if not mcmp:
                continue
            a, op, b = mcmp.group(1), mcmp.group(2), mcmp.group(3)

            lhs_targets = [a] if a in formal_set else id_to_formals.get(a, [])
            if not lhs_targets:
                continue

            if re.fullmatch(r"-?\d+", b):
                for t in lhs_targets:
                    atoms.append(f"{t}{op}{b}")
            else:
                rhs_targets = [b] if b in formal_set else id_to_formals.get(b, [])
                if len(rhs_targets) == 1:
                    for t in lhs_targets:
                        atoms.append(f"{t}{op}{rhs_targets[0]}")

    # ---------------- 5) lightweight abstract interpretation over prefix ----------------
    tracked = set()
    for a in call_args:
        a0 = re.sub(r"\s+", "", a)
        if _is_identifier(a0):
            tracked.add(a0)

    TOP = ("top", None)
    NONNEG = ("nonneg", None)

    state = {v: TOP for v in tracked}

    def _join(d1, d2):
        if d1[0] == "top" or d2[0] == "top":
            return TOP
        if d1[0] == "const" and d2[0] == "const":
            if d1[1] == d2[1]:
                return d1
            if d1[1] >= 0 and d2[1] >= 0:
                return NONNEG
            return TOP
        if d1[0] == "nonneg" and d2[0] == "nonneg":
            return NONNEG
        if d1[0] == "const" and d2[0] == "nonneg":
            return NONNEG if d1[1] >= 0 else TOP
        if d2[0] == "const" and d1[0] == "nonneg":
            return NONNEG if d2[1] >= 0 else TOP
        return TOP

    def _eval_expr(expr: str, st):
        e0 = re.sub(r"\s+", "", expr.strip())
        if re.fullmatch(r"-?\d+", e0):
            return ("const", int(e0))
        if _has_length(e0):
            return NONNEG
        if _is_identifier(e0):
            return st.get(e0, TOP)
        return TOP

    def _process_assignment_stmt(stmt: str, st):
        t = stmt.strip()
        if not t:
            return
        t = re.sub(r"^\s*final\s+", "", t)

        decl = False
        if re.match(r"^(byte|short|int|long)\b", t):
            decl = True
            t = re.sub(r"^(byte|short|int|long)\b", "", t, count=1).strip()

        parts2 = [p.strip() for p in t.split(",")] if decl else [t]
        for one in parts2:
            if "=" not in one:
                continue
            lhs, rhs = one.split("=", 1)
            lhs = lhs.strip()
            rhs = rhs.strip()
            if not re.fullmatch(r"[A-Za-z_]\w*", lhs):
                continue
            if lhs not in st:
                continue
            st[lhs] = _eval_expr(rhs, st)

    def _parse_block(code: str, st):
        n = len(code)
        i = 0

        def skip_ws(i):
            while i < n and code[i].isspace():
                i += 1
            return i

        def starts_kw(i, kw):
            if not code.startswith(kw, i):
                return False
            j = i + len(kw)
            if j < n and (code[j].isalnum() or code[j] == "_"):
                return False
            if i > 0 and (code[i - 1].isalnum() or code[i - 1] == "_"):
                return False
            return True

        def stmt_end(i0):
            depth = 0
            i = i0
            while i < n:
                ch = code[i]
                if ch == "(":
                    depth += 1
                elif ch == ")":
                    depth = max(0, depth - 1)
                elif ch == ";" and depth == 0:
                    return i
                i += 1
            return None

        while True:
            i = skip_ws(i)
            if i >= n:
                break

            if starts_kw(i, "if"):
                i = skip_ws(i + 2)
                if i >= n or code[i] != "(":
                    se = stmt_end(i)
                    if se is None:
                        break
                    i = se + 1
                    continue

                pe = _find_matching_paren(code, i)
                if pe is None:
                    break
                i = skip_ws(pe + 1)

                if i >= n or code[i] != "{":
                    se = stmt_end(i)
                    if se is None:
                        break
                    st_then = dict(st)
                    _process_assignment_stmt(code[i:se], st_then)
                    for v in st:
                        st[v] = _join(st_then[v], st[v])
                    i = se + 1
                    continue

                then_body, then_close = _extract_brace_block(code, i)
                if then_body is None:
                    break

                st_pre = dict(st)
                st_then = dict(st)
                _parse_block(then_body, st_then)

                if then_close is not None and then_close >= n:
                    for v in st:
                        st[v] = st_then[v]
                    i = n
                    break

                i = skip_ws((then_close if then_close is not None else n) + 1)

                j = skip_ws(i)
                if starts_kw(j, "else"):
                    j = skip_ws(j + 4)
                    if j < n and code[j] == "{":
                        else_body, else_close = _extract_brace_block(code, j)
                        st_else = dict(st_pre)
                        _parse_block(else_body, st_else)
                        i = skip_ws(else_close + 1)
                    else:
                        st_else = dict(st_pre)
                        i = j
                    for v in st:
                        st[v] = _join(st_then[v], st_else[v])
                else:
                    for v in st:
                        st[v] = _join(st_then[v], st_pre[v])
                continue

            se = stmt_end(i)
            if se is None:
                break
            _process_assignment_stmt(code[i:se], st)
            i = se + 1

    _parse_block(prefix, state)

    # ---------------- 6) project call args onto formals ----------------
    for i in range(min(len(formal), len(call_args))):
        p = formal[i]
        a0 = re.sub(r"\s+", "", call_args[i])

        if _is_int_literal(a0):
            atoms.append(f"{p}=={int(a0)}")
            continue

        if _has_length(a0):
            atoms.append(f"{p}>=0")
            continue

        if _is_identifier(a0):
            d = state.get(a0, TOP)
            if d[0] == "const":
                atoms.append(f"{p}=={d[1]}")
            elif d[0] == "nonneg":
                atoms.append(f"{p}>=0")

    # ---------------- 7) de-duplicate preserving order ----------------
    seen = set()
    uniq = []
    for a in atoms:
        if a in seen:
            continue
        seen.add(a)
        uniq.append(a)

    return " && ".join(uniq)


def parse_rank_expression(rank_str):
    """
    Parse a rank expression string.

    Supports:
        Scalar:  ReLU(n - i) + 2*ReLU(m - j + 1)
        Vector:  [ReLU(n - i), ReLU(m - j)]

    Returns:
        exprs: list of sympy expressions (one per ranking component)
        is_vector: bool
    """
    rank_str = rank_str.strip()

    if rank_str.startswith('[') and rank_str.endswith(']'):
        is_vector = True
        inner = rank_str[1:-1].strip()
        components = [c.strip() for c in inner.split(',')]
    else:
        is_vector = False
        components = [rank_str]

    def preprocess_relu(s):
        def replace_relu(match):
            content = match.group(1)
            return f"Max({content}, 0)"

        pattern = r'ReLU\(([^()]+(?:\([^()]*\))*[^()]*)\)'
        result = s
        for _ in range(10):
            new_result = re.sub(pattern, replace_relu, result, flags=re.IGNORECASE)
            if new_result == result:
                break
            result = new_result
        return result

    exprs = []
    for comp in components:
        try:
            comp_processed = preprocess_relu(comp)
            clash = {name: sp.Symbol(name) for name in ["N", "E", "I", "O", "S"]}
            expr = sp.sympify(comp_processed, locals=clash)
            exprs.append(expr)
        except Exception as e:
            raise ValueError(f"Failed to parse component '{comp}': {e}")

    return exprs, is_vector


def expr_to_sum_of_relu(expr, input_vars):
    """
    Convert a SymPy expression to Sum-of-ReLU form:
        sum_i out[i] * ReLU(W[i] . x + b[i])

    where ReLU(z) = Max(z, 0).

    Returns (out, W, b) as numpy arrays.
    """
    import sympy as sp
    import numpy as np

    expr = sp.expand(expr)
    subs0 = {v: 0 for v in input_vars}

    relu_map = {}
    constant_term = 0.0
    linear_terms = {v: 0.0 for v in input_vars}

    def add_relu_term(coeff, affine):
        affine = sp.expand(affine)
        w = [float(affine.coeff(v)) for v in input_vars]
        b = float(affine.subs(subs0))
        key = (tuple(w), b)
        relu_map[key] = relu_map.get(key, 0.0) + float(coeff)

    for t in sp.Add.make_args(expr):
        c, rest = t.as_coeff_Mul()
        c = float(c)

        if rest == 1:
            constant_term += c
            continue

        if rest in input_vars:
            linear_terms[rest] += c
            continue

        if isinstance(rest, sp.Max):
            affine = None
            for arg in rest.args:
                if not (arg.is_number and float(arg) == 0.0):
                    affine = arg
                    break
            if affine is None:
                raise ValueError(f"Cannot find affine in ReLU term: {rest}")
            add_relu_term(c, affine)
            continue

        raise ValueError(f"Unsupported term in rank expression: {t}")

    for v, c in linear_terms.items():
        if abs(c) < 1e-12:
            continue
        add_relu_term(c, v)
        add_relu_term(-c, -v)

    if abs(constant_term) > 1e-12:
        add_relu_term(constant_term, sp.Integer(1))

    items = [(coef, Wb[0], Wb[1]) for Wb, coef in relu_map.items() if abs(coef) > 1e-12]
    if not items:
        raise ValueError("Expression must contain at least one ReLU term after normalization.")

    items.sort(key=lambda x: (x[2], x[1]))

    out = np.array([[coef for coef, _, __ in items]], dtype=np.float64)
    W = np.array([list(w) for _, w, __ in items], dtype=np.float64)
    b = np.array([bb for _, __, bb in items], dtype=np.float64)
    return out, W, b


def infer_class_and_method(jar_path):
    """
    Best-effort inference of class name and method from a JAR file.

    Strategy:
        1. Read META-INF/MANIFEST.MF Main-Class
        2. Fall back to jar basename
        3. Default method: main

    Returns: (class_name, method_name) or (None, None)
    """
    try:
        with zipfile.ZipFile(jar_path, 'r') as zf:
            try:
                manifest = zf.read('META-INF/MANIFEST.MF').decode('utf-8')
                for line in manifest.split('\n'):
                    if line.startswith('Main-Class:'):
                        main_class = line.split(':', 1)[1].strip()
                        return main_class, 'main'
            except KeyError:
                pass

        basename = os.path.splitext(os.path.basename(jar_path))[0]
        return basename, 'main'

    except Exception as e:
        print(f"Warning: Failed to infer class/method from jar: {e}", file=sys.stderr)
        return None, None


def _extract_vars_from_obj(obj):
    """
    Extract a variable name list from a tracing return value.
    Supports tuple/list/dict structures.
    Returns list[str] or None.
    """
    if obj is None:
        return None

    if isinstance(obj, tuple) and len(obj) >= 1:
        return _extract_vars_from_obj(obj[0])

    if isinstance(obj, (list, tuple)):
        try:
            return [str(x) for x in obj]
        except Exception:
            return None

    if isinstance(obj, dict):
        for k in ("input_vars", "vars", "inputs", "inputVars", "invars"):
            if k in obj and isinstance(obj[k], (list, tuple)):
                return [str(x) for x in obj[k]]

        for k in ("meta", "result", "data"):
            if k in obj:
                got = _extract_vars_from_obj(obj[k])
                if got:
                    return got

        return None

    return None


def _vars_look_reasonable(input_vars, expected_symbols):
    """
    Return True if input_vars looks like a valid parameter list:
    - Not empty
    - Does not contain diagnostic keys like 'tracing' or 'error'
    - Covers all expected_symbols (symbols that appear in the rank expression)
    """
    if not input_vars or not isinstance(input_vars, list):
        return False

    bad = {"tracing", "error"}
    if any(v in bad for v in input_vars):
        return False

    if expected_symbols:
        s = set(input_vars)
        if not set(expected_symbols).issubset(s):
            return False

    return True


def _get_vars_by_javap(jar_path, class_name, method_name):
    """
    Try to recover parameter names and order via javap's LocalVariableTable.
    Only succeeds when the JAR was compiled with debug information.
    """
    try:
        out = subprocess.check_output(
            ["javap", "-classpath", jar_path, "-c", "-l", "-p", class_name],
            text=True, stderr=subprocess.STDOUT, errors="replace",
            env=os.environ,
        )
    except Exception:
        return None

    idx = out.find(method_name + "(")
    if idx < 0:
        return None

    chunk = out[idx:]
    lvt_pos = chunk.find("LocalVariableTable:")
    if lvt_pos < 0:
        return None
    lvt = chunk[lvt_pos:].splitlines()

    rows = []
    for line in lvt:
        line = line.strip()
        m = re.match(r"^(\d+)\s+(\d+)\s+(\d+)\s+(\S+)\s+(\S+)", line)
        if m:
            start = int(m.group(1))
            slot = int(m.group(3))
            name = m.group(4)
            if start == 0:
                rows.append((slot, name))

    if not rows:
        return None

    rows.sort(key=lambda x: x[0])
    names = [name for slot, name in rows if name != "this"]
    return names or None


def _get_vars_by_java_source(jar_path, class_name, method_name):
    """
    Fallback: parse sibling Java source signature to recover parameter names.
    """
    java_path = _find_java_source(jar_path, class_name)
    if not java_path:
        return None

    try:
        src = open(java_path, "r", encoding="utf-8", errors="ignore").read()
    except Exception:
        return None

    m_sig = re.search(rf"\b\w+\s+{re.escape(method_name)}\s*\((.*?)\)\s*\{{", src, re.S)
    if not m_sig:
        return None

    sig_inside = m_sig.group(1)
    parts = [p.strip() for p in sig_inside.split(",") if p.strip()]
    names = []
    for p in parts:
        m_name = re.search(r"([A-Za-z_]\w*)\s*(?:\[\s*\])?\s*$", p)
        if m_name:
            name = m_name.group(1)
            if name != "this":
                names.append(name)
    return names or None


def get_input_vars_from_jar(jar_path, class_name, method_name,
                            samples=1, limit=10, sampling_strategy='pairanticorr',
                            tracing_seed=0,
                            expected_symbols=None):
    """
    Attempt to detect input variable order for a Java method.

    Enhanced detection: uses get_and_layout_traces from checker/checker_bridge/utils.py
    when available (requires torch and psutil from the full checker environment).
    Fallback detection: uses javap LocalVariableTable (no extra dependencies).
    """
    tracing_error = None

    # 1) Enhanced variable detection via checker/checker_bridge/utils.py (trace-based).
    try:
        from checker.checker_bridge.utils import get_and_layout_traces

        loop_heads = get_loop_heads(jar_path, class_name, method_name)

        res = {}

        wanted_kwargs = {
            "samples": samples,
            "limit": limit,
            "loop_heads": loop_heads,
            "res": res,
            "sampling_strategy": sampling_strategy,
        }

        sig = inspect.signature(get_and_layout_traces)
        params = sig.parameters

        if "tracing_seed" in params:
            wanted_kwargs["tracing_seed"] = tracing_seed
        elif "seed" in params:
            wanted_kwargs["seed"] = tracing_seed
        elif "tracingSeed" in params:
            wanted_kwargs["tracingSeed"] = tracing_seed
        elif "trace_seed" in params:
            wanted_kwargs["trace_seed"] = tracing_seed

        call_kwargs = {k: v for k, v in wanted_kwargs.items() if k in params}
        ret = get_and_layout_traces(jar_path, class_name, method_name, **call_kwargs)

        cand1 = _extract_vars_from_obj(ret)
        if _vars_look_reasonable(cand1, expected_symbols):
            return cand1

        cand2 = _extract_vars_from_obj(res)
        if _vars_look_reasonable(cand2, expected_symbols):
            return cand2

        if isinstance(ret, dict) and "error" in ret:
            tracing_error = f"tracing returned error: {ret['error']}"
        elif isinstance(res, dict) and "error" in res:
            tracing_error = f"tracing res contains error: {res['error']}"

    except ModuleNotFoundError as e:
        missing = getattr(e, "name", "") or str(e)
        if missing == "torch" or "torch" in str(e):
            tracing_error = "torch is not installed"
        else:
            tracing_error = str(e)
    except Exception as e:
        tracing_error = str(e)

    if tracing_error:
        print(
            f"Warning: tracing-based auto-detect unavailable: {tracing_error}. "
            f"Falling back to javap/source-signature detection (checker verification still continues).",
            file=sys.stderr
        )

    # 2) Bytecode metadata fallback (javap LocalVariableTable).
    javap_vars = _get_vars_by_javap(jar_path, class_name, method_name)
    if _vars_look_reasonable(javap_vars, expected_symbols):
        return javap_vars

    # 3) Source signature fallback (if sibling .java is present).
    source_vars = _get_vars_by_java_source(jar_path, class_name, method_name)
    if _vars_look_reasonable(source_vars, expected_symbols):
        return source_vars

    if importlib.util.find_spec("torch") is None:
        print(
            "Warning: torch is missing; auto-detect fell back to javap/source signature. "
            "Install torch only if you need tracing-based variable inference.",
            file=sys.stderr
        )

    return None


def main():
    parser = argparse.ArgumentParser(
        description="NuTERA rank function checker: verify termination with a user-provided ranking function.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
    python checker/rf_check.py --jar path/to/program.jar --rank "ReLU(n-i)"
    python checker/rf_check.py --jar program.jar --rank "[ReLU(n-i), ReLU(m-j)]" --class MyClass --method loop
    python checker/rf_check.py --jar program.jar --rank "ReLU(n-i) + 2*ReLU(m-j+1)" --delta 1
        """
    )

    parser.add_argument('--jar', required=True, help='Path to the JAR file')
    parser.add_argument('--rank', required=True, help='Rank function expression (scalar or vector)')
    parser.add_argument('--class', dest='klass', help='Class name (auto-inferred if not provided)')
    parser.add_argument('--method', help='Method name (default: main or auto-inferred)')
    parser.add_argument('--delta', type=int, default=1, help='Lexicographic decrease margin (default: 1)')
    parser.add_argument('--rankdim', type=int, help='Ranking dimension (inferred from rank expression if not provided)')
    parser.add_argument('--samples', type=int, default=1, help='Number of samples for input var detection (default: 1)')
    parser.add_argument('--sampling', default='pairanticorr', help='Sampling strategy (default: pairanticorr)')
    parser.add_argument('--vars', default=None,
                        help='Manually specify input variable order, e.g. "x,y,n" or "x y n"')
    parser.add_argument("--assume", type=str, default="", help="Manual assume, e.g. 'x>=1 && y>=1 && p==1'")
    parser.add_argument("--auto-assume-from-main", action="store_true",
                        help="Infer assume from the Java main() that calls the target method")

    args = parser.parse_args()

    jar_path = os.path.abspath(args.jar)
    if not os.path.exists(jar_path):
        print(f"ERROR: Jar file not found: {jar_path}", file=sys.stderr)
        sys.exit(1)

    print(f"Jar file: {jar_path}")

    try:
        exprs, is_vector = parse_rank_expression(args.rank)
        rankdim = len(exprs)
        print(f"Parsed rank expression ({'vector' if is_vector else 'scalar'}, rankdim={rankdim}):")
        for i, expr in enumerate(exprs):
            print(f"  R_{i}(x) = {expr}")
    except Exception as e:
        print(f"ERROR: Failed to parse rank expression: {e}", file=sys.stderr)
        sys.exit(1)

    if args.rankdim is not None and args.rankdim != rankdim:
        print(f"Warning: --rankdim={args.rankdim} conflicts with parsed rankdim={rankdim}, using parsed value")

    class_name = args.klass
    method_name = args.method

    if class_name is None or method_name is None:
        inferred_class, inferred_method = infer_class_and_method(jar_path)
        if class_name is None:
            class_name = inferred_class
        if method_name is None:
            method_name = inferred_method

    if class_name is None or method_name is None:
        print("ERROR: Could not infer class/method. Please provide --class and --method.", file=sys.stderr)
        sys.exit(1)

    print(f"Class: {class_name}, Method: {method_name}")

    assume = (args.assume or "").strip()
    auto = ""

    if args.auto_assume_from_main and not assume:
        java_path = _find_java_source(jar_path, class_name)
        if java_path:
            auto = _infer_assume_from_main(java_path, method_name, class_name)
        if auto:
            assume = auto

    if assume:
        os.environ["RANKCHECKER_ASSUME"] = assume
        print(f"[rf_check] assume = {assume}")

        try:
            from jnius import autoclass
            System = autoclass("java.lang.System")
            System.setProperty("RANKCHECKER_ASSUME", assume)
        except Exception as e:
            print(f"[rf_check] WARN: cannot set JVM property via jnius: {e}")

    else:
        os.environ.pop("RANKCHECKER_ASSUME", None)
        try:
            from jnius import autoclass
            System = autoclass("java.lang.System")
            System.clearProperty("RANKCHECKER_ASSUME")
        except Exception:
            pass

    try:
        loop_heads = get_loop_heads(jar_path, class_name, method_name)
        print(f"Loop heads: {loop_heads}")
    except Exception as e:
        print(f"ERROR: Failed to get loop heads: {e}", file=sys.stderr)
        sys.exit(1)

    expected = sorted({str(s) for expr in exprs for s in expr.free_symbols})

    if args.vars:
        input_vars = [v for v in re.split(r'[,\s]+', args.vars.strip()) if v]
    else:
        input_vars = get_input_vars_from_jar(
            jar_path, class_name, method_name,
            samples=args.samples,
            limit=10,
            sampling_strategy=args.sampling,
            tracing_seed=0,
            expected_symbols=expected
        )

    if input_vars is None or not set(expected).issubset(set(input_vars)):
        print("Warning: Failed to auto-detect valid input vars, extracting from rank expression (may be UNSAFE)")
        input_vars = expected

    DROP_AUX = {"args", "this"}
    if input_vars is not None:
        input_vars = [v for v in input_vars if not (v in DROP_AUX and v not in expected)]

    print(f"Input variables: {input_vars}")

    out_to_check = []
    W_to_check = []
    b_to_check = []

    for i, expr in enumerate(exprs):
        try:
            out, W, b = expr_to_sum_of_relu(expr, [sp.Symbol(v) for v in input_vars])
            out_to_check.append(out)
            W_to_check.append(W)
            b_to_check.append(b)
            print(f"\nRanking component {i}:")
            print(f"  out = {out}")
            print(f"  W shape = {W.shape}, b shape = {b.shape}")
        except Exception as e:
            print(f"ERROR: Failed to convert rank component {i} to Sum-of-ReLU: {e}", file=sys.stderr)
            sys.exit(1)

    try:
        decrease, invar, cex = check_sum_of_relu_vec(
            jar_path, class_name, method_name,
            loop_heads, input_vars,
            out_to_check, W_to_check, b_to_check,
            delta=args.delta
        )
    except Exception as e:
        print(f"ERROR: Checker failed: {e}", file=sys.stderr)
        import traceback
        traceback.print_exc()
        sys.exit(1)

    print("\n" + "=" * 60)
    print("RESULT")
    print("=" * 60)

    if decrease:
        print("YES")
        print("Termination was proven with the provided ranking function.")
    else:
        print("NO")
        print("The ranking function does not prove termination.")
        if cex:
            print("\nCounterexample:")
            print(json.dumps(cex, indent=2))
        else:
            print("(No counterexample provided)")

    # IMPORTANT:
    # A checker conclusion of NO is a valid business result (disproved candidate),
    # not a runtime failure. Keep exit code 0 for both YES/NO, and reserve non-zero
    # for real execution errors/exceptions handled above.
    sys.exit(0)


if __name__ == '__main__':
    main()
