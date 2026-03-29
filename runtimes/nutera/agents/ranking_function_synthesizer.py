"""
Ranking function synthesis engine.

Uses an LLM to generate candidate ReLU-based ranking functions and validates
them through the checker in an iterative synthesis loop.
"""

import os
import json
import glob
import re
import sys
import subprocess
from typing import List, Optional, Dict, Any
from datetime import datetime

from core import Config, Logger, Program, LLMAttempt, CheckResult
from llm import LLMClient, RFParser, PromptManager
from .ranking_function_checker import RankingFunctionChecker
from .attempt_logger import AttemptLogger


class RankingFunctionSynthesizer:
    """LLM-driven synthesis stage for ReLU-based ranking functions."""

    def __init__(self, config: Config, logger: Logger):
        self.config = config
        self.logger = logger

        api_key = None
        base_url = None

        if config.llm.rf_provider == "openai":
            api_key = config.llm.openai_api_key
            base_url = config.llm.openai_base_url
        elif config.llm.rf_provider == "openrouter":
            api_key = config.llm.openrouter_api_key
            base_url = config.llm.openrouter_base_url
        elif config.llm.rf_provider == "google":
            api_key = config.llm.google_api_key
        elif config.llm.rf_provider == "deepseek":
            api_key = config.llm.deepseek_api_key

        self.llm_client = LLMClient(
            provider=config.llm.rf_provider,
            model=config.llm.rf_model,
            api_key=api_key,
            base_url=base_url,
            gemini_show_reasoning=getattr(config.llm, "gemini_show_reasoning", False),
        )

        self.rf_parser = RFParser()
        self.rank_checker = RankingFunctionChecker(config, logger)
        self.attempt_logger = AttemptLogger(config)

        prompts_dir = os.path.join(
            os.path.dirname(os.path.dirname(__file__)),
            "config", "prompts"
        )
        self.prompts = PromptManager(prompts_dir)

    def _get_openrouter_extra_body(self) -> Optional[Dict[str, Any]]:
        """
        Return an extra_body payload only for OpenRouter + Claude Haiku 4.5.
        """
        if self.llm_client.provider != "openrouter":
            return None

        model = (self.llm_client.model or "").strip()

        if model != "anthropic/claude-haiku-4.5":
            return None

        extra_by_model = getattr(self.config.llm, "openrouter_extra_body_by_model", None) or {}
        if isinstance(extra_by_model, dict):
            hit = extra_by_model.get(model)
            if isinstance(hit, dict) and hit:
                return hit

        return None

    def run(self, llm_queue: List[Program]) -> List[Program]:
        """
        Process all programs in the synthesis queue.

        Args:
            llm_queue: Ordered list of program records to synthesize.

        Returns:
            The same list with updated status fields.
        """
        updated_programs = []

        self.logger.info(f"Starting ranking-function synthesis for {len(llm_queue)} programs")

        for i, prog in enumerate(llm_queue, 1):
            self.logger.info(f"[{i}/{len(llm_queue)}] Processing {prog.name}...")
            self._process_single_program(prog)
            updated_programs.append(prog)

        self._write_program_level_csv(updated_programs)

        self.logger.info("Ranking-function synthesis completed")
        return updated_programs

    def _process_single_program(self, prog: Program):
        """
        Process one program in single-turn mode.

        Each attempt rebuilds a fresh prompt from:
        - method source or bytecode
        - optional verifier feedback
        - optional dimension constraints
        """
        max_attempts = self.config.llm.max_attempts
        parse_fail_count = 0
        smt_no_count = 0
        counterexample: Optional[str] = None

        rejected_rfs: List[str] = []
        rejected_rf_norms: set = set()
        attempt_history: List[Dict[str, str]] = []
        memory_window = getattr(self.config.llm, "rf_memory_window", 5)
        memory_char_budget = getattr(self.config.llm, "rf_memory_char_budget", 5000)

        ablation_level = self._get_ablation_level()

        target_dim: Optional[int] = None
        loop_keyword_count: Optional[int] = None

        if ablation_level in (0, 2):
            java_source_for_dim = self._extract_method_source(prog)
            loop_keyword_count = self._count_loop_keywords(java_source_for_dim)
            target_dim = self._infer_target_dim_from_source(java_source_for_dim)
            self.logger.info(
                f"  Static dimension rule active: loop_keywords={loop_keyword_count}, target_dim={target_dim}D"
            )

        for attempt_num in range(1, max_attempts + 1):
            self.logger.info(f"  Attempt {attempt_num}/{max_attempts}")

            prompt_data = self._build_prompt(
                prog,
                counterexample=counterexample,
                target_dim=target_dim,
                loop_keyword_count=loop_keyword_count,
                rejected_rfs=rejected_rfs,
                attempt_history=attempt_history[-memory_window:],
                memory_char_budget=memory_char_budget,
            )
            messages = prompt_data["messages"]
            prompt_summary = prompt_data["summary"]

            attempt = LLMAttempt(
                program_name=prog.name,
                attempt_number=attempt_num,
                provider=self.llm_client.provider,
                model=self.llm_client.model,
                start_time=datetime.now(),
                prompt_summary=prompt_summary
            )

            try:
                model = (self.llm_client.model or "").strip()

                if (
                    self.llm_client.provider == "openrouter"
                    and model == "anthropic/claude-haiku-4.5"
                ):
                    extra_body = self._get_openrouter_extra_body()
                    response = self.llm_client.chat(
                        messages=messages,
                        temperature=self.config.llm.temperature,
                        max_tokens=self.config.llm.max_tokens,
                        extra_body=extra_body,
                    )
                else:
                    response = self.llm_client.chat(
                        messages=messages,
                        temperature=self.config.llm.temperature,
                        max_tokens=self.config.llm.max_tokens,
                    )

                attempt.llm_output = response.content
                attempt.tokens_used = response.usage.get("total_tokens")
                attempt.end_time = datetime.now()
                attempt.duration_seconds = (attempt.end_time - attempt.start_time).total_seconds()

                raw_out = response.content or ""

                if raw_out.strip() == "":
                    parse_fail_count += 1
                    hint = f"Empty model output. finish_reason={getattr(response, 'finish_reason', None)}"

                    attempt.check_status = "EMPTY_OUTPUT"
                    attempt.empty_output_reason = hint
                    self.attempt_logger.log(attempt)

                    if parse_fail_count >= 5:
                        self.logger.error("  5 consecutive empty/parse failures -> ReLU Limit")
                        prog.llm_result = "No"
                        prog.final_status = "ReLU Limit"
                        return

                    continue

                snippet = self._extract_rf_snippet(raw_out)

                if snippet == "NO_RELU_SOLUTION":
                    self.logger.warning("  LLM returned NO_RELU_SOLUTION")
                    prog.llm_result = "No"
                    prog.final_status = "ReLU Limit"
                    attempt.check_status = "NO_RELU_SOLUTION"
                    self.attempt_logger.log(attempt)
                    return

                rf_expr = self.rf_parser.parse(snippet)

                if rf_expr is None:
                    parse_fail_count += 1
                    hint = self._build_parse_error_hint(raw_out)

                    self.logger.warning(f"  Parse failed ({parse_fail_count}/5)")
                    attempt.check_status = "PARSE_FAILED"
                    attempt.smt_output = hint
                    self.attempt_logger.log(attempt)

                    if parse_fail_count >= 5:
                        self.logger.error("  5 consecutive parse failures -> ReLU Limit")
                        prog.llm_result = "No"
                        prog.final_status = "ReLU Limit"
                        return

                    continue

                if not self.rf_parser.validate_syntax(rf_expr):
                    self.logger.warning(f"  RF syntax validation failed: {rf_expr}")
                    parse_fail_count += 1

                    hint = "Syntax validation failed (unbalanced brackets/parentheses or forbidden ops)."
                    attempt.check_status = "PARSE_FAILED"
                    attempt.parsed_rf = rf_expr
                    attempt.smt_output = hint
                    self.attempt_logger.log(attempt)

                    if parse_fail_count >= 5:
                        prog.llm_result = "No"
                        prog.final_status = "ReLU Limit"
                        return

                    continue

                attempt.parsed_rf = rf_expr
                self.logger.info(f"  Parsed RF: {rf_expr}")

                current_dim = self._infer_rf_dimension(rf_expr)

                # Levels 0/2: dimension is decided statically from the source.
                if ablation_level in (0, 2):
                    expected_dim = target_dim if target_dim in (1, 2) else 1
                    if current_dim != expected_dim:
                        parse_fail_count += 1
                        hint = (
                            f"Dimension constraint violated: expected_dim={expected_dim}, current_dim={current_dim}. "
                            f"For level={ablation_level}, RF dimension is determined directly from "
                            f"the number of 'for'/'while' keywords in source (loop_keywords={loop_keyword_count}), "
                            f"capped at 2D."
                        )
                        attempt.check_status = "PARSE_FAILED"
                        attempt.smt_output = hint
                        self.attempt_logger.log(attempt)

                        self.logger.warning(f"  {hint}")

                        if parse_fail_count >= 5:
                            prog.llm_result = "No"
                            prog.final_status = "ReLU Limit"
                            return

                        continue

                check_result = self.rank_checker.check(
                    jar_path=prog.jar_path,
                    class_name=prog.class_name,
                    method_name=prog.function_name,
                    rank_expr=rf_expr
                )

                attempt.check_status = check_result.status
                attempt.counterexample = check_result.counterexample

                if check_result.status == "YES":
                    self.attempt_logger.log(attempt)
                    self.logger.info("  ✓ SUCCESS! RF verified.")
                    prog.llm_result = "Yes"
                    prog.final_status = "Verified OK"
                    return

                if check_result.status == "NO":
                    self.attempt_logger.log(attempt)
                    smt_no_count += 1
                    self.logger.warning(f"  ✗ SMT returned NO ({smt_no_count}/{max_attempts})")

                    counterexample = self._extract_counterexample_summary(check_result)

                    if smt_no_count >= max_attempts:
                        self.logger.error("  Max attempts reached -> LLM MaxFail")
                        prog.llm_result = "No"
                        prog.final_status = "LLM MaxFail"
                        return

                    continue

                self.logger.error("  Checker returned ERROR")
                if check_result.raw_output:
                    tail = check_result.raw_output[-2000:]
                    self.logger.error(f"  Checker output tail:\n{tail}")

                attempt.check_status = "ERROR"
                attempt.smt_output = (check_result.raw_output or "")[-2000:]
                self.attempt_logger.log(attempt)

                continue

            except Exception as exc:
                self.logger.error(f"  Exception in attempt {attempt_num}: {exc}")
                attempt.check_status = "EXCEPTION"
                attempt.end_time = datetime.now()
                attempt.duration_seconds = (attempt.end_time - attempt.start_time).total_seconds()
                self.attempt_logger.log(attempt)

        if prog.final_status is None:
            prog.llm_result = "No"
            prog.final_status = "LLM MaxFail"

    def _get_ablation_level(self) -> int:
        """Map the configured ablation level to the supported range [0, 3]."""
        level = getattr(
            self.config.llm,
            "ablation_level",
            getattr(self.config.llm, "rankcheck_feedback_level", 3)
        )
        try:
            level = int(level)
        except Exception:
            level = 3

        return max(0, min(3, level))

    def _infer_rf_dimension(self, rf_expr: str) -> int:
        """Infer the dimension of a bracketed RF vector."""
        s = (rf_expr or "").strip()
        if not s:
            return 0

        if not (s.startswith("[") and s.endswith("]")):
            return 1

        inner = s[1:-1].strip()
        if not inner:
            return 0

        depth = 0
        dim = 1
        for ch in inner:
            if ch == "(":
                depth += 1
            elif ch == ")":
                depth = max(0, depth - 1)
            elif ch == "," and depth == 0:
                dim += 1

        return dim

    def _count_loop_keywords(self, java_source: str) -> int:
        """Count 'for' and 'while' keywords in the Java source text."""
        return len(re.findall(r"\b(?:for|while)\b", java_source or ""))

    def _infer_target_dim_from_source(self, java_source: str) -> int:
        """
        Decide the target RF dimension from loop-keyword count.

        - 0 or 1 loop keywords -> 1D
        - 2 or more loop keywords -> 2D
        """
        return 1 if self._count_loop_keywords(java_source) <= 1 else 2

    def _build_prompt(
            self,
            prog: Program,
            counterexample: Optional[str],
            target_dim: Optional[int] = None,
            loop_keyword_count: Optional[int] = None,
            rejected_rfs: Optional[List[str]] = None,
            attempt_history: Optional[List[Dict[str, str]]] = None,
            memory_char_budget: int = 5000,
    ) -> Dict[str, Any]:
        """Build a fresh single-turn prompt for one synthesis attempt."""
        java_source = self._extract_method_source(prog)
        var_info = self._get_variable_info(prog, java_source)

        ablation_level = self._get_ablation_level()
        system_prompt = self.prompts.get_rf_generation_system_prompt(ablation_level)

        if ablation_level in (0, 2):
            expected_dim = target_dim if target_dim in (1, 2) else 1
            loop_cnt = loop_keyword_count if loop_keyword_count is not None else 0

            if expected_dim == 1:
                vector_rule = "   - a bracketed vector with EXACTLY ONE component: [<comp0>]"
                dimension_policy = (
                    f"Exact dimension policy: the method contains {loop_cnt} occurrence(s) of 'for'/'while', "
                    f"so you MUST output exactly 1D."
                )
            else:
                vector_rule = "   - a bracketed vector with EXACTLY TWO components: [<comp0>, <comp1>]"
                dimension_policy = (
                    f"Exact dimension policy: the method contains {loop_cnt} occurrence(s) of 'for'/'while', "
                    f"so you MUST output exactly 2D."
                )
        else:
            vector_rule = "   - a bracketed vector: [ReLU(expr1)] or [ReLU(expr1), ReLU(expr2), ...]"
            dimension_policy = "Dimension policy: prefer 1D; use 2D~6D if needed for lexicographic decrease."

        user_parts = [
            f"Target method: {prog.class_name}.{prog.function_name}",
            f"Integer variables (parameters + locals): {', '.join(var_info['variables'])}",
            "Java source snippet:",
            "```java",
            java_source,
            "```",
            "",
            "Output MUST follow these rules:",
            "1) Output exactly ONE line.",
            "2) The line MUST be either:",
            vector_rule,
            "   - OR exactly: NO_RELU_SOLUTION",
            "3) No explanations, no code blocks (except the provided Java snippet above), no markdown, no extra lines.",
            "4) Use only the listed integer variable names. Do not invent new variables.",
            "5) Only use integer affine expressions inside ReLU(...). No division/modulo/bitwise/max/min/abs.",
            "",
            dimension_policy,
        ]

        if rejected_rfs:
            user_parts.extend([
                "",
                "Rejected RFs so far (STRICTLY FORBIDDEN; do NOT repeat any of them or trivial formatting variants):"
            ])
            for i, rf in enumerate(rejected_rfs[-8:], 1):
                user_parts.append(f"{i}) {rf}")

        if attempt_history:
            user_parts.extend([
                "",
                "Recent attempt history (use this short-memory to avoid repetition and make a structurally new revision):"
            ])

            used = 0
            for item in reversed(attempt_history[-6:]):
                block = (
                    f"- Attempt {item.get('attempt', '?')}: "
                    f"RF={item.get('rf', '')} ; "
                    f"status={item.get('status', '')}\n"
                    f"  Evidence: {item.get('evidence', '')}"
                )
                if used + len(block) > memory_char_budget:
                    break
                user_parts.append(block)
                used += len(block)

        if ablation_level == 2:
            expected_dim = target_dim if target_dim in (1, 2) else 1
            user_parts.extend([
                "",
                "Level-2 structural restriction:",
                "1) Rich expressions INSIDE a component are allowed.",
                "2) Examples of allowed forms:",
                "   - [ReLU(a)+ReLU(b)]",
                "   - [2*ReLU(x-y)]",
                "   - [ReLU(x-y)+ReLU(y), ReLU(z-1)]",
                "3) However, the RF dimension itself is fixed statically from the number of 'for'/'while' keywords in the source, capped at 2D.",
                f"4) Therefore, in this attempt you MUST output exactly {expected_dim}D.",
                "5) Do NOT switch dimension across attempts; dimension is source-determined, not history-determined.",
            ])

        if counterexample:
            user_parts.append("")
            user_parts.append("A previous attempt failed. Failure evidence:")
            user_parts.append(counterexample)

        user_prompt = "\n".join(user_parts)

        return {
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt},
            ],
            "summary": (
                f"vars={var_info['variables']}, "
                f"has_cex={counterexample is not None}, "
                f"single_turn=True, "
                f"target_dim={target_dim}, "
                f"loop_keywords={loop_keyword_count}"
            ),
        }

    def _build_feedback_user_message(
        self,
        status: str,
        last_rf_snippet: str,
        counterexample: Optional[str],
        parse_error_hint: Optional[str] = None,
    ) -> str:
        """Build a strict feedback message after an unsuccessful attempt."""
        parts = []

        if status == "PARSE_FAILED":
            parts.append("Your previous output could not be parsed as a valid ranking function.")
            if parse_error_hint:
                parts.append(f"Parsing hint: {parse_error_hint}")
            parts.append("You MUST output exactly one line.")
            parts.append("It MUST be either a bracketed list like: [ReLU(expr1), ReLU(expr2), ...] OR exactly: NO_RELU_SOLUTION.")
            parts.append("Do NOT output code blocks, explanations, markdown, or extra lines.")
            parts.append("Try again with a syntactically valid bracketed list.")

        elif status == "NO":
            parts.append("SMT verification returned NO for your previous ranking function.")
            if counterexample:
                parts.append("Failure evidence (counterexample and/or checker logs; use it to revise the RF):")
                parts.append(counterexample)

            parts.append("Hard constraints for the next attempt:")
            parts.append("1) You MUST NOT repeat the previous ranking function.")
            parts.append("2) You MUST produce a NEW ranking function that is syntactically different.")
            parts.append("3) Keep the output format: exactly one line, bracketed list [ ... ] or NO_RELU_SOLUTION.")
            if last_rf_snippet:
                parts.append(f"Previous RF (DO NOT output this again): {last_rf_snippet}")

            parts.append("Revision guidance (do not explain in output):")
            parts.append("- Change at least one affine inside a ReLU(...), or add/remove a dimension (1D/2D/3D/4D).")
            parts.append("- Consider lexicographic vectors if a single component fails.")
            parts.append("- Ensure non-negativity and strict (lexicographic) decrease per loop iteration.")

        else:
            parts.append("The previous attempt did not succeed. Please generate a different ranking function.")
            if last_rf_snippet:
                parts.append(f"Previous RF (DO NOT repeat): {last_rf_snippet}")

        return "\n".join(parts)

    def _build_parse_error_hint(self, raw_out: str) -> str:
        """Build a compact parse-error hint from raw model output."""
        if not raw_out:
            return "Empty output."
        text = raw_out.strip().replace("\n", " ")
        if len(text) > 200:
            text = text[:200] + " ..."
        return f"Model output head: {text}"

    def _extract_method_source(self, prog: Program) -> str:
        """
        Extract method source code; fall back to javap disassembly if unavailable.
        """
        prog.jar_path = self._normalize_jar_path(prog.jar_path)
        java_path = self._resolve_java_source_path(prog)
        if java_path:
            prog.java_source_path = java_path
        else:
            return self._extract_method_bytecode(prog)

        try:
            with open(prog.java_source_path, "r", encoding="utf-8", errors="ignore") as fh:
                content = fh.read()

            pattern = rf"(?m)^\s*(?:public|private|protected)?\s*(?:static\s+)?(?:final\s+)?[\w\<\>\[\]]+\s+{re.escape(prog.function_name)}\s*\("
            m = re.search(pattern, content, flags=re.M)
            if not m:
                return self._extract_method_bytecode(prog)

            start = m.start()
            brace = 0
            in_body = False
            end = len(content)
            for i in range(start, len(content)):
                if content[i] == "{":
                    brace += 1
                    in_body = True
                elif content[i] == "}":
                    if in_body:
                        brace -= 1
                        if brace == 0:
                            end = i + 1
                            break

            return content[start:end]

        except Exception as exc:
            self.logger.warning(f"Failed to extract method source: {exc}")
            return self._extract_method_bytecode(prog)

    def _extract_method_bytecode(self, prog: Program) -> str:
        """Fall back to javap disassembly when Java source is unavailable."""
        prog.jar_path = self._normalize_jar_path(prog.jar_path)
        jar_path = prog.jar_path
        cls = self._resolve_class_in_jar(jar_path, prog.class_name)

        cmd = ["javap", "-classpath", jar_path, "-c", "-p", "-l", cls]
        res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        if res.returncode != 0:
            return f"// Failed to javap (rc={res.returncode}):\n// CMD={cmd}\n// OUT={res.stdout[:1200]}"

        out = res.stdout
        lines = out.splitlines()

        start = None
        for i, ln in enumerate(lines):
            if prog.function_name in ln and "(" in ln:
                start = i
                break

        if start is None:
            return "\n".join(lines[:300])

        sig_pat = re.compile(r"^\s*(public|private|protected).*\)\s*;?\s*$")
        out_lines = []
        for ln in lines[start:]:
            if out_lines and sig_pat.match(ln):
                break
            out_lines.append(ln)
            if len(out_lines) >= 300:
                break

        return "\n".join(out_lines)

    def _get_variable_info(self, prog: Program, method_src: Optional[str] = None) -> Dict[str, Any]:
        """
        Extract integer variable names from the target method.

        Collects: parameter names, local int declarations, for-loop iterators.
        """
        text = method_src if method_src is not None else self._extract_method_source(prog)

        JAVA_KWS = {
            "if", "else", "while", "for", "return", "new", "class", "public", "private", "protected",
            "static", "void", "int", "long", "short", "byte", "boolean", "char", "float", "double",
            "true", "false", "null", "this", "super", "break", "continue", "switch", "case", "default",
            "try", "catch", "finally", "throw", "throws", "import", "package",
        }

        vars_: List[str] = []

        m = re.search(rf"{re.escape(prog.function_name)}\s*\((.*?)\)", text, flags=re.S)
        if m:
            params = m.group(1).strip()
            if params:
                for part in params.split(","):
                    part = part.strip()
                    if not part:
                        continue
                    part = part.replace("final", " ").strip()
                    tokens = part.split()
                    if not tokens:
                        continue
                    name = tokens[-1].replace("[]", "")
                    if name.isidentifier() and name not in JAVA_KWS:
                        vars_.append(name)

        for mm in re.finditer(r"\bint\s+([^;]+);", text):
            decl = mm.group(1)
            for chunk in decl.split(","):
                chunk = chunk.strip()
                if not chunk:
                    continue
                name = chunk.split("=")[0].strip().replace("[]", "")
                if name.isidentifier() and name not in JAVA_KWS:
                    vars_.append(name)

        for mm in re.finditer(r"for\s*\(\s*int\s+([A-Za-z_]\w*)\b", text):
            name = mm.group(1)
            if name not in JAVA_KWS:
                vars_.append(name)

        seen: set = set()
        filtered: List[str] = []
        for v in vars_:
            if v in (prog.class_name, prog.function_name):
                continue
            if v in {"Random", "String", "System", "Math"}:
                continue
            if v not in seen:
                seen.add(v)
                filtered.append(v)

        if not filtered:
            filtered = ["x", "y", "z"]

        return {"variables": filtered[:30], "loop_heads": []}

    def _extract_counterexample_summary(self, check_result: CheckResult) -> Optional[str]:
        """
        Build a compact verifier-feedback bundle for the next synthesis attempt.
        """
        ablation_level = self._get_ablation_level()

        if ablation_level in (0, 1):
            return None

        raw = check_result.raw_output or ""

        def one_line(s: str) -> str:
            return " ".join((s or "").strip().split())

        def compact_json_maybe(s: Optional[str], max_len: int = 800) -> Optional[str]:
            if not s:
                return None
            ss = s.strip()
            try:
                obj = json.loads(ss)
                out = json.dumps(obj, separators=(",", ":"), sort_keys=True)
            except Exception:
                out = one_line(ss)
            if len(out) > max_len:
                out = out[:max_len] + " ..."
            return out

        def pick_prefix(prefix: str, max_items: int = 200) -> Optional[str]:
            if not raw:
                return None
            picked = []
            for ln in raw.splitlines():
                t = ln.strip()
                if t.startswith(prefix):
                    rest = t[len(prefix):].strip()
                    picked.append(one_line(rest))
                    if len(picked) >= max_items:
                        break
            if not picked:
                return None
            return " | ".join([p for p in picked if p])

        def pick_input_vars() -> Optional[str]:
            if not raw:
                return None
            for ln in raw.splitlines():
                t = ln.strip()
                if t.startswith("Input variables:"):
                    return one_line(t)
            return None

        parts: List[str] = []

        # The checker script emits [rf_check] (or legacy [manual_rank_check]) prefix lines.
        for prefix in ("[rf_check]", "[manual_rank_check]"):
            checker_line = pick_prefix(prefix)
            if checker_line:
                parts.append(f"[rf_check] {checker_line}")
                break

        iv_line = pick_input_vars()
        if iv_line:
            parts.append(iv_line)

        checkvec_line = pick_prefix("[checkVec]")
        if not checkvec_line and check_result.checkVec_info:
            checkvec_line = " | ".join(
                [
                    one_line(x.replace("[checkVec]", "").strip())
                    for x in check_result.checkVec_info.splitlines()
                    if x.strip()
                ]
            )
            checkvec_line = one_line(checkvec_line) if checkvec_line else None
        if checkvec_line:
            parts.append(f"[checkVec] {checkvec_line}")

        getcex_line = pick_prefix("[getCex]")
        if getcex_line:
            parts.append(f"[getCex] {getcex_line}")

        cex_line = compact_json_maybe(check_result.counterexample)
        if cex_line:
            parts.append(f"Counterexample: {cex_line}")

        return "\n".join(parts) if parts else None

    def _write_program_level_csv(self, programs: List[Program]):
        """Write synthesis results to a timestamped program-level CSV."""
        from utils import csv_manager

        output_path = os.path.join(
            self.config.paths.results_dir,
            "program_level",
            f"program_level_after_synthesis_{datetime.now().strftime('%Y%m%d_%H%M%S')}.csv"
        )

        os.makedirs(os.path.dirname(output_path), exist_ok=True)

        fieldnames = [
            "name", "class_name", "jar_path", "function_name",
            "synthesis_result", "final_status", "error_summary",
        ]

        rows = []
        for prog in programs:
            row = {
                "name":             getattr(prog, "name", ""),
                "class_name":       getattr(prog, "class_name", ""),
                "jar_path":         getattr(prog, "jar_path", ""),
                "function_name":    getattr(prog, "function_name", ""),
                "synthesis_result": getattr(prog, "llm_result", "") or "",
                "final_status":     getattr(prog, "final_status", "") or "",
                "error_summary":    getattr(prog, "error_summary", "") or "",
            }
            rows.append(row)

        csv_manager.write_csv(output_path, rows, fieldnames=fieldnames)

        self.logger.info(f"Program-level CSV saved to: {output_path}")

    def _normalize_jar_path(self, jar_path: str) -> str:
        """
        Normalize jar paths across workspace layouts.

        Resolution order:
        1. Use the path directly if it exists.
        2. Rewrite known legacy path aliases.
        3. Search by filename in standard workspace roots.
        """
        if jar_path and os.path.exists(jar_path):
            return jar_path

        normalized = (jar_path or "").replace("\\", "/")
        filename = os.path.basename(normalized)

        module_dir = os.path.abspath(os.path.dirname(__file__))
        package_root = os.path.abspath(os.path.join(module_dir, ".."))
        workspace_root = os.path.abspath(os.path.join(package_root, ".."))

        candidates = []

        # Legacy layout aliases (kept for checkpoint compatibility).
        alias_pairs = [
            ("/workspace/termination_agent/libs/", "/workspace/libs/"),
            ("/workspace/termination_agent/benchmarking/", "/workspace/benchmarking/"),
            ("/workspace/termination_agent/", "/workspace/main/"),
            ("termination_agent/libs/", "libs/"),
            ("termination_agent/benchmarking/", "benchmarking/"),
            ("termination_agent/", "main/"),
        ]

        if normalized:
            candidates.append(normalized)
            for old, new in alias_pairs:
                if old in normalized:
                    candidates.append(normalized.replace(old, new, 1))

        search_roots = [
            os.getcwd(),
            workspace_root,
            package_root,
            os.path.join(workspace_root, "libs"),
            os.path.join(workspace_root, "benchmarking"),
            os.path.join(package_root, "libs"),
            os.path.join(package_root, "benchmarking"),
            "/workspace",
            "/workspace/main",
            "/workspace/libs",
            "/workspace/benchmarking",
        ]

        if filename:
            for root in search_roots:
                candidates.extend([
                    os.path.join(root, filename),
                    os.path.join(root, "libs", filename),
                    os.path.join(root, "benchmarking", filename),
                ])

        seen: set = set()
        for path in candidates:
            if not path:
                continue
            abs_path = os.path.abspath(path)
            if abs_path in seen:
                continue
            seen.add(abs_path)
            if os.path.exists(abs_path):
                return abs_path

        if filename:
            recursive_bases = [
                os.path.join(workspace_root, "libs"),
                os.path.join(workspace_root, "benchmarking"),
                os.path.join(package_root, "libs"),
                os.path.join(package_root, "benchmarking"),
                "/workspace/libs",
                "/workspace/benchmarking",
            ]
            checked: set = set()
            for base in recursive_bases:
                base = os.path.abspath(base)
                if base in checked or not os.path.isdir(base):
                    continue
                checked.add(base)

                hits = glob.glob(os.path.join(base, "**", filename), recursive=True)
                for hit in hits:
                    if os.path.exists(hit):
                        return hit

        return jar_path

    def _resolve_class_in_jar(self, jar_path: str, class_name: str) -> str:
        """Recover the FQCN from jar entries when the short name is ambiguous."""
        if not jar_path or not os.path.exists(jar_path):
            return class_name

        try:
            out = subprocess.check_output(["jar", "tf", jar_path], stderr=subprocess.STDOUT, text=True)
        except Exception:
            return class_name

        want = class_name.replace(".", "/") + ".class"
        lines = out.splitlines()

        if want in lines:
            return class_name

        base = os.path.basename(want)
        for ln in lines:
            if "$" in ln:
                continue
            if ln == base or ln.endswith("/" + base):
                return ln[:-6].replace("/", ".")

        return class_name

    def _resolve_java_source_path(self, prog) -> Optional[str]:
        """Resolve a Java source file associated with the target class."""
        if getattr(prog, "java_source_path", None) and os.path.exists(prog.java_source_path):
            return prog.java_source_path

        jar_dir = os.path.dirname(prog.jar_path)
        direct = os.path.join(jar_dir, f"{prog.class_name}.java")
        if os.path.exists(direct):
            return direct

        for base in {jar_dir, os.path.dirname(jar_dir)}:
            patt = os.path.join(base, "**", f"{prog.class_name}.java")
            hits = glob.glob(patt, recursive=True)
            for h in hits:
                if os.path.exists(h):
                    return h

        for base in {jar_dir, os.path.dirname(jar_dir)}:
            for h in glob.glob(os.path.join(base, "**", "*.java"), recursive=True):
                try:
                    with open(h, "r", encoding="utf-8", errors="ignore") as fh:
                        if f"class {prog.class_name}" in fh.read():
                            return h
                except Exception:
                    pass

        return None

    def _extract_rf_snippet(self, text: str) -> str:
        """Extract the RF snippet from model output."""
        if not text:
            return ""

        if re.search(r"(?m)^\s*NO_RELU_SOLUTION\s*$", text):
            return "NO_RELU_SOLUTION"

        m = re.search(r"```(?:[a-zA-Z0-9_-]+)?\n(.*?)```", text, re.S)
        if m:
            text = m.group(1).strip()

        l = text.find("[")
        r = text.rfind("]")
        if 0 <= l < r:
            return text[l:r + 1].strip()

        return text.strip()


# ---------------------------------------------------------------------------
# Backward-compatibility alias
# ---------------------------------------------------------------------------
Unit3LLMSynthesizer = RankingFunctionSynthesizer
