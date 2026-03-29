import json
import sympy as sp

from checker.runtime_bootstrap import ensure_runtime_prepared

_RUNTIME_BOOTSTRAP = ensure_runtime_prepared(print_diagnostics=False)

import jnius_config
if not jnius_config.vm_running:
    if _RUNTIME_BOOTSTRAP.classpath_entries:
        jnius_config.set_classpath(*_RUNTIME_BOOTSTRAP.classpath_entries)
    if _RUNTIME_BOOTSTRAP.java_library_path:
        jnius_config.add_options(
            "-Djava.library.path=" + _RUNTIME_BOOTSTRAP.java_library_path
        )

from jnius import autoclass, cast
from .loopheads import get_loop_heads  # relative import within checker_bridge package

import numpy as np

def _sympy_to_ast(e):
    """把 SymPy 表达式递归转成 JSON AST。
       支持: 加法/乘法/常数/变量/Max(x,0) 当作 ReLU(x) 使用。"""
    if isinstance(e, sp.Symbol):
        return {"op": "var", "name": str(e)}
    if isinstance(e, sp.Integer) or isinstance(e, sp.Float):
        return {"op": "const", "value": float(e)}
    # ReLU: Max(arg, 0) 或 Max(0, arg)
    if isinstance(e, sp.Max):
        args = list(e.args)
        # 找到非零那个作为 ReLU 输入
        if len(args) == 2:
            a0, a1 = args[0], args[1]
            if (isinstance(a0, (sp.Integer, sp.Float)) and float(a0) == 0.0):
                return {"op": "relu", "x": _sympy_to_ast(a1)}
            if (isinstance(a1, (sp.Integer, sp.Float)) and float(a1) == 0.0):
                return {"op": "relu", "x": _sympy_to_ast(a0)}
    # 加法
    if isinstance(e, sp.Add):
        return {"op": "add", "args": [_sympy_to_ast(a) for a in e.args]}
    # 乘法（只处理数字 * expr 或 expr * 数字 的情况；SymPy 会把线性写成加法里的乘法项）
    if isinstance(e, sp.Mul):
        coeff = 1.0
        others = []
        for a in e.args:
            if isinstance(a, (sp.Integer, sp.Float)):
                coeff *= float(a)
            else:
                others.append(_sympy_to_ast(a))
        if not others:
            return {"op": "const", "value": coeff}
        if len(others) == 1 and coeff == 1.0:
            return others[0]
        return {"op": "mul", "coeff": coeff, "x": others[0] if len(others) == 1 else {"op":"add","args":others}}
    # 默认兜底为 tofloat
    return {"op": "const", "value": float(e)}

def check_sympy(jar_name, class_name, method_name, loop_heads, input_vars, sympy_exprs, delta=1.0):
    """
    传入每个 loop head 的 PWL (SymPy) 表达式，走 Java 的通用 PWL→Z3 编码。
    返回: bool(是否满足递减), bool(是否需要不变式/辅助约束), 以及可能的反例 dict。
    """
    from jnius import autoclass, cast
    URL = autoclass('java.net.URL')
    URLClassLoader = autoclass('java.net.URLClassLoader')
    List = autoclass('java.util.List')
    HashMap = autoclass('java.util.HashMap')

    urls = [URL('jar:file:' + jar_name + '!/')]
    cl = cast("java.lang.ClassLoader", URLClassLoader.newInstance(urls))

    # 构造 JSON：按 head 顺序存每个 expr 的 AST
    ast_per_head = []
    for expr in sympy_exprs:
        ast_per_head.append(_sympy_to_ast(expr))

    payload = {
        "vars": list(input_vars),
        "delta": float(delta),
        "exprs": ast_per_head
    }
    payload_json = json.dumps(payload)

    RankChecker = autoclass('javachecker.RankChecker')
    ArrayList = autoclass('java.util.ArrayList')
    JInteger = autoclass('java.lang.Integer')
    heads = ArrayList()
    for h in loop_heads:
        heads.add(JInteger(int(h)))
    # Java 端新增: _checkPWLFromJson(cl, class, method, heads, jsonPayload, cexMap)
    cex = HashMap()
    res = RankChecker._checkPWLFromJson(cl, class_name, method_name, heads, payload_json, cex)

    cexDict = {}
    it = cex.entrySet().iterator()
    while it.hasNext():
        e = it.next()
        k, v = e.getKey(), e.getValue()
        cexDict[k] = v

    # 约定: res[0] = decreaseOK, res[1] = needInvariant(或其它诊断), res[2] = sat?
    return bool(res[0]), bool(res[1]), cexDict
# --- END: add to javachecker.py ---

def check(jar_name, class_name, method_name, offset, ranking_args, ranking_fun):
    #for x in os.environ['CLASSPATH']:
    #    print(x)
    #exit(0)
    RankChecker = autoclass('javachecker.RankChecker')
    
    URL = autoclass('java.net.URL')
    URLClassLoader = autoclass('java.net.URLClassLoader')
    List = autoclass('java.util.List')
    
    urls = [URL('jar:file:' + jar_name + '!/')]
    cl = cast("java.lang.ClassLoader", URLClassLoader.newInstance(urls))
    #cl = URLClassLoader.newInstance(urls)

    ArrayList = autoclass('java.util.ArrayList')
    JInteger = autoclass('java.lang.Integer')
    args = ArrayList()
    [args.add(JInteger(int(x))) for x in ranking_args]
    fun = ArrayList()
    [fun.add(JInteger(int(x))) for x in ranking_fun]
    
    res = RankChecker._check(cl, class_name, method_name, offset, args, fun)
    
    return bool(res[0]), bool(res[1])


def last_line_offset(jar_name, class_name, method_name, line):

    URL = autoclass('java.net.URL')
    URLClassLoader = autoclass('java.net.URLClassLoader')

    CFGAnalyzer = autoclass('javachecker.CFGAnalyzer')

    urls = [URL('jar:file:' + jar_name + '!/')]
    cl = URLClassLoader.newInstance(urls)

    line_to_offset = CFGAnalyzer.lineToLabelOffset(cl, class_name, method_name)
    return line_to_offset.get(line).last()

def check_sum_of_relu(jar_name, class_name, method_name, ranking_heads,
                      ranking_args, ranking_out, ranking_W, ranking_b):

    #for x in os.environ['LD_LIBRARY_PATH'].split(":"):
    #    print(x)
    RankChecker = autoclass('javachecker.RankChecker')
    
    URL = autoclass('java.net.URL')
    URLClassLoader = autoclass('java.net.URLClassLoader')
    List = autoclass('java.util.List')
    HashMap = autoclass('java.util.HashMap')

    urls = [URL('jar:file:' + jar_name + '!/')]
    cl = cast("java.lang.ClassLoader", URLClassLoader.newInstance(urls))
    #cl = URLClassLoader.newInstance(urls)

    #print(ranking_W)
    #print(ranking_b)
    #print(ranking_out)

    assert len(ranking_W) == len(ranking_heads)
    assert len(ranking_b) == len(ranking_heads)
    assert len(ranking_out) == len(ranking_heads)
    fun = []
    for W,b in zip(ranking_W, ranking_b):

        assert np.shape(W)[0] == np.shape(b)[0]
        SOR = []
        for i in range(0, np.shape(W)[0]):
            SOR.append([int(round(x)) for x in W[i,:]] + [int(round(b[i]))])
        fun.append(SOR)

    ArrayList = autoclass('java.util.ArrayList')
    JInteger = autoclass('java.lang.Integer')

    # args: List<String>
    args = ArrayList()
    for s in ranking_args:
        args.add(str(s))

    # heads: List<Integer>
    heads = ArrayList()
    for h in ranking_heads:
        heads.add(JInteger(int(h)))

    # out: List<List<Integer>> ，每个 head 一行（1×H）
    out = ArrayList()
    for W in ranking_out:
        # 统一拿到一维行向量
        if hasattr(W, 'ndim'):
            row = W[0, :].ravel().tolist() if W.ndim >= 2 else W.ravel().tolist()
        else:
            # list 的情况：可能是 [[...]] 或 [...]
            row = W[0] if isinstance(W, list) and len(W) > 0 and isinstance(W[0], (list, tuple)) else W
        jrow = ArrayList()
        for x in row:
            jrow.add(JInteger(int(round(x))))

        out.add(jrow)

    # hidden: List<List<List<Integer>>> ，每个 head 是 m×(n_in+1) 的行（最后一列是 b）
    hidden = ArrayList()
    for relus in fun:  # fun 是 Python:list[ list[ list[int] ] ]
        jmat = ArrayList()
        for row in relus:
            jrow = ArrayList()
            for x in row:
                jrow.add(JInteger(int(round(x))))

            jmat.add(jrow)
        hidden.add(jmat)

    cex = HashMap()

    res = RankChecker._checkLexiReluOrCex2(cl, class_name, method_name, heads, args, out, hidden, cex)

    cexDict = {}
    i = cex.entrySet().iterator()
    while i.hasNext():
      e = i.next()
      (k,v) = e.getKey(),e.getValue()
      cexDict[k] = v

    return bool(res[0]), bool(res[2]), cexDict


def check_sum_of_relu_vec(jar_name, class_name, method_name, ranking_heads,
                          ranking_args, ranking_out, ranking_W, ranking_b, delta=1):
    """
    新增函数：支持 rankdim 与 loop_heads 解耦的词典序 ranking 检查。
    
    参数:
        - ranking_heads: 程序中实际的 loop head 位置列表（长度 = n_loop_heads）
        - ranking_args: 输入变量名列表（长度 = n_vars）
        - ranking_out: 每个 ranking 分量的输出权重列表（长度 = rankdim）
        - ranking_W: 每个 ranking 分量的权重矩阵列表（长度 = rankdim）
        - ranking_b: 每个 ranking 分量的偏置向量列表（长度 = rankdim）
        - delta: 词典序下降的 margin（默认 1）
    
    返回:
        (decrease: bool, invar: bool, cex: dict)
    
    语义：
        对每个 loop head h，检查循环转移关系 T_h(x, x') 是否满足：
        T_h(x, x') => R(x') <_lex R(x)
        
        其中 R(x) = [R_0(x), R_1(x), ..., R_{rankdim-1}(x)]
        词典序下降定义为：
        存在 p ∈ [0, rankdim-1]，使得：
            - 对所有 q < p: R_q(x') <= R_q(x)
            - R_p(x') <= R_p(x) - delta
    
    Java 端调用：
        RankChecker._checkLexiReluVecOrCex2(cl, class, method, heads, args, out, hidden, delta, cex)
    """
    RankChecker = autoclass('javachecker.RankChecker')
    
    URL = autoclass('java.net.URL')
    URLClassLoader = autoclass('java.net.URLClassLoader')
    List = autoclass('java.util.List')
    HashMap = autoclass('java.util.HashMap')

    urls = [URL('jar:file:' + jar_name + '!/')]
    cl = cast("java.lang.ClassLoader", URLClassLoader.newInstance(urls))

    # 【关键】不再断言 len(ranking_W) == len(ranking_heads)
    # 因为 rankdim 可以独立于 loop_heads 数量
    rankdim = len(ranking_out)
    assert len(ranking_W) == rankdim
    assert len(ranking_b) == rankdim
    
    fun = []
    for W, b in zip(ranking_W, ranking_b):
        assert np.shape(W)[0] == np.shape(b)[0]
        SOR = []
        for i in range(0, np.shape(W)[0]):
            SOR.append([int(round(x)) for x in W[i,:]] + [int(round(b[i]))])
        fun.append(SOR)

    ArrayList = autoclass('java.util.ArrayList')
    JInteger = autoclass('java.lang.Integer')
    JDouble = autoclass('java.lang.Double')

    # args: List<String>
    args = ArrayList()
    for s in ranking_args:
        args.add(str(s))

    # heads: List<Integer>
    heads = ArrayList()
    for h in ranking_heads:
        heads.add(JInteger(int(h)))

    # out: List<List<Integer>>，每个 ranking 分量一行（1×H）
    out = ArrayList()
    for W in ranking_out:
        # 统一拿到一维行向量
        if hasattr(W, 'ndim'):
            row = W[0, :].ravel().tolist() if W.ndim >= 2 else W.ravel().tolist()
        else:
            # list 的情况：可能是 [[...]] 或 [...]
            row = W[0] if isinstance(W, list) and len(W) > 0 and isinstance(W[0], (list, tuple)) else W
        jrow = ArrayList()
        for x in row:
            jrow.add(JInteger(int(round(x))))

        out.add(jrow)

    # hidden: List<List<List<Integer>>>，每个 ranking 分量是 m×(n_in+1) 的行（最后一列是 b）
    hidden = ArrayList()
    for relus in fun:  # fun 是 Python:list[ list[ list[int] ] ]
        jmat = ArrayList()
        for row in relus:
            jrow = ArrayList()
            for x in row:
                jrow.add(JInteger(int(round(x))))

            jmat.add(jrow)
        hidden.add(jmat)

    cex = HashMap()

    # 调用 Java 端新增方法
    res = RankChecker._checkLexiReluVecOrCex2(cl, class_name, method_name, heads, args, 
                                              out, hidden, int(delta), cex)

    cexDict = {}
    i = cex.entrySet().iterator()
    while i.hasNext():
      e = i.next()
      (k,v) = e.getKey(),e.getValue()
      cexDict[k] = v

    return bool(res[0]), bool(res[2]), cexDict


ArrayList = autoclass('java.util.ArrayList')
JInteger  = autoclass('java.lang.Integer')
JDouble   = autoclass('java.lang.Double')

def _to_java_list_of_int(py_list):
    j = ArrayList()
    for x in py_list:
        j.add(JInteger(int(x)))
    return j

def _to_java_list_of_str(py_list):
    j = ArrayList()
    for s in py_list:
        # PyJNIus 会自动转 String，这里直接 add 即可
        j.add(str(s))
    return j

def _is_scalar(x):
    return not isinstance(x, (list, tuple, np.ndarray))

def _iter_scalars(x):
    """
    把任意嵌套(list/tuple/numpy)拍平成标量流：
    - 单个数 -> 直接产出
    - list/tuple/ndarray -> 递归展开到最内层标量
    """
    if _is_scalar(x):
        yield float(x)
        return
    # numpy -> python
    if isinstance(x, np.ndarray):
        # 利用 ravel 展平
        x = x.ravel().tolist()
    for y in x:
        if _is_scalar(y):
            yield float(y)
        else:
            yield from _iter_scalars(y)

def _to_java_2d_double(py_2d):
    """
    Python: List[List[float 或 任意嵌套容器]] -> Java: List<List<Double>>
    最内层若出现 list/ndarray，会自动拍平"并入该行"。
    """
    outer = ArrayList()
    r_idx = -1
    for row in py_2d:
        r_idx += 1
        inner = ArrayList()
        c_idx = -1
        for v in row:
            c_idx += 1
            # v 可能是标量、list、ndarray；统一拍平后逐个 add
            added = False
            for s in _iter_scalars(v):
                inner.add(JDouble(float(s)))
                added = True
            if not added:
                # 兜底：说明 row 是空/None，补 0
                inner.add(JDouble(0.0))
        outer.add(inner)
    return outer

def _to_java_3d_double(py_3d):
    """
    Python: List[List[List[float 或 任意嵌套容器]]] -> Java: List<List<List<Double>>>
    最内层若出现 list/ndarray，会自动拍平"并入该行"。
    """
    lvl1 = ArrayList()
    b_idx = -1
    for mat in py_3d:            # block / summand
        b_idx += 1
        lvl2 = ArrayList()
        r_idx = -1
        for row in mat:          # row
            r_idx += 1
            lvl3 = ArrayList()
            c_idx = -1
            for v in row:        # col / 或者被嵌套的一段向量
                c_idx += 1
                added = False
                for s in _iter_scalars(v):
                    lvl3.add(JDouble(float(s)))
                    added = True
                if not added:
                    lvl3.add(JDouble(0.0))
            lvl2.add(lvl3)
        lvl1.add(lvl2)
    return lvl1

def _java_map_to_py_dict(jmap):
    # 处理 java.util.Map
    try:
        it = jmap.entrySet().iterator()
    except Exception:
        return None
    py = {}
    while it.hasNext():
        e = it.next()
        k = str(e.getKey())
        v = e.getValue()
        try:
            v = float(v)  # 处理 java.lang.Double
        except Exception:
            v = str(v)
        py[k] = v
    return py

def _coerce_cex_to_dict(cex_obj):
    # 1) Map
    d = _java_map_to_py_dict(cex_obj)
    if d is not None:
        return d
    # 2) JSON 字符串
    try:
        s = str(cex_obj)
        return json.loads(s)
    except Exception:
        pass
    # 3) (k,v) 对列表
    try:
        return {str(k): float(v) for (k, v) in cex_obj}
    except Exception:
        pass
    # 4) 兜底：转字符串
    return {"_raw": str(cex_obj)}

def check_sum_of_softplus_envelope(jarfile, classname, methodname, loop_heads, input_vars,
                                   outU, WU, bU,   # AFTER 上界: 3D, 3D, 2D
                                   outL, WL, bL):  # BEFORE 下界: 3D, 3D, 2D
    """
    显式把 Python 嵌套 list 转成 Java ArrayList，避免 PyJNIus 猜错类型。
    Java 端方法签名（你之前用 javap 看过）应为：
      (String, String, String,
       List<Integer>, List<String>,
       List<List<List<Double>>>, List<List<List<Double>>>, List<List<Double>>,
       List<List<List<Double>>>, List<List<List<Double>>>, List<List<Double>>) : Result
    """
    RankChecker = autoclass('javachecker.RankChecker')

    # 强制构造 Java List，杜绝 numpy 混入
    j_loop_heads = _to_java_list_of_int(list(loop_heads))
    j_input_vars = _to_java_list_of_str(list(input_vars))

    # ---- 头数对齐（关键修） ----
    n_heads = len(loop_heads)

    # 估个维度：输入变量数作为 n_in，若已有某个 head 的 b 可推 H，否则设 H=1
    n_in = max(1, len(input_vars))
    try:
        H_guess = max(1, max(len(_flatten_1d(bi)) for bi in (bU if bU else [])))
    except ValueError:
        H_guess = 1

    # 若任何一个列表是空或长度不足，用"最小可行 head"补齐
    def _filler_outU(_):
        return _default_head_shapes(n_in, H_guess)[0]

    def _filler_WU(_):
        return _default_head_shapes(n_in, H_guess)[1]

    def _filler_bU(_):
        return _default_head_shapes(n_in, H_guess)[2]

    def _filler_outL(_):
        return _default_head_shapes(n_in, H_guess)[0]

    def _filler_WL(_):
        return _default_head_shapes(n_in, H_guess)[1]

    def _filler_bL(_):
        return _default_head_shapes(n_in, H_guess)[2]

    outU = _pad_or_trim_to_heads(outU, n_heads, _filler_outU)
    WU = _pad_or_trim_to_heads(WU, n_heads, _filler_WU)
    bU = _pad_or_trim_to_heads(bU, n_heads, _filler_bU)
    outL = _pad_or_trim_to_heads(outL, n_heads, _filler_outL)
    WL = _pad_or_trim_to_heads(WL, n_heads, _filler_WL)
    bL = _pad_or_trim_to_heads(bL, n_heads, _filler_bL)

    # ---- 把 outU/outL 规范为 heads×1×H（已有函数）----
    outU = _normalize_out_3d(outU, bU)
    outL = _normalize_out_3d(outL, bL)

    j_outU = _to_java_3d_double(outU)
    j_WU   = _to_java_3d_double(WU)
    j_bU   = _to_java_2d_double(bU)

    j_outL = _to_java_3d_double(outL)
    j_WL   = _to_java_3d_double(WL)
    j_bL   = _to_java_2d_double(bL)

    Result = RankChecker.check_sum_of_softplus_envelope(
        jarfile, classname, methodname,
        j_loop_heads, j_input_vars,
        j_outU, j_WU, j_bU,
        j_outL, j_WL, j_bL
    )
    decrease = bool(Result.getDecrease())
    invar = bool(Result.getInvar())
    cex = _coerce_cex_to_dict(Result.getCex())
    return decrease, invar, cex


def _flatten_1d(x):
    # 任意可迭代/ndarray -> 扁平 1D python list[float]
    if isinstance(x, np.ndarray):
        x = x.ravel().tolist()
    elif isinstance(x, (list, tuple)):
        flat = []
        for v in x:
            if isinstance(v, (list, tuple, np.ndarray)):
                flat.extend(_flatten_1d(v))
            else:
                flat.append(float(v))
        return flat
    return [float(x)] if not isinstance(x, list) else [float(v) for v in x]

def _normalize_out_3d(out_list, b_list):
    """
    输入:
      - out_list: per-head 的"可能是一维/二维/空"的权重
      - b_list:   per-head 的 bias 列表，用于确定 H（隐元个数）
    输出:
      - 规整后的 3D：heads × 1 × H（且非空）
    规则:
      - 若 out[i] 是 1D -> 包成 [[row]]
      - 若 out[i] 是 2D -> 取第一行 -> [[row]]
      - 若为空 -> 用长度 H 的兜底行（全 1.0）
      - 若长度不等于 H -> 截断或 0 填充到 H
    """
    norm = []
    for i, out_i in enumerate(out_list):
        H = len(_flatten_1d(b_list[i]))
        row = []
        if out_i is None:
            row = [1.0] * H
        else:
            # 先尽量取"第一行"，否则当作 1D
            if isinstance(out_i, (list, tuple, np.ndarray)) and len(out_i) > 0 and isinstance(out_i[0], (list, tuple, np.ndarray)):
                row = _flatten_1d(out_i[0])
            else:
                row = _flatten_1d(out_i)

            if len(row) == 0:
                row = [1.0] * H

        # 对齐 H
        if len(row) < H:
            row = row + [0.0] * (H - len(row))
        elif len(row) > H:
            row = row[:H]

        norm.append([row])   # 二维 -> 包一层成 1×H
    return norm

def _pad_or_trim_to_heads(lst, n_heads, filler):
    """
    把任意 per-head 列表 lst 调到长度 n_heads。
    - lst 长 -> 截断
    - lst 短 -> 用 filler(i) 生成并补齐
    """
    L = len(lst)
    if L == n_heads:
        return lst
    if L > n_heads:
        return lst[:n_heads]
    padded = list(lst)
    for i in range(L, n_heads):
        padded.append(filler(i))
    return padded

def _default_head_shapes(n_in=1, H=1):
    """
    生成一个"最小可行"的 head：
    - out  : [[1.0]*H]
    - W    : [[0.0]*n_in] * H
    - b    : [0.0]*H
    """
    out = [[1.0]*H]
    W   = [[0.0]*n_in for _ in range(H)]
    b   = [0.0]*H
    return out, W, b


if __name__ == '__main__':

    jarfile = "../benchmarking/termination-suite/termination-crafted-lit/terminating/NoriSharmaFSE2013Fig7/NoriSharmaFSE2013Fig7.jar"
    classname = "NoriSharmaFSE2013Fig7"
    methodname = "loop"
    input_vars = ['i', 'j', 'M', 'N', 'a', 'b', 'c']

    loop_heads = get_loop_heads(jarfile, classname, methodname)

    W_to_check = [np.array([[-1.,  0., -2.,  2.,  1.,  0., -0.],
                           [-0., -2.,  2., -2.,  3.,  0., -0.],
                           [ 0., 0.,  0.,  0.,  0., 0., 0.],
                           [-2.,  1.,  3., -0., -2.,  0., -0.],
                           [-1., -1., -0.,  2., -2., 0.,  0.]])]
    b_to_check = [np.array([1., 1., 2., 3., 3.])]
    out_to_check = [np.array([[1., 1., 0., 1., 1.]])]



    decrease, invar, cex = check_sum_of_relu(jarfile, classname, methodname,
                                                           loop_heads, input_vars,
                                                           out_to_check, W_to_check, b_to_check)

    print("Decreasing: {}".format(decrease))
    print("Invar: {}".format(invar))
    print("Cex: {}".format(cex))
