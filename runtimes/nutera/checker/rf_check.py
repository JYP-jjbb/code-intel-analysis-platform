#!/usr/bin/env python3
"""
rf_check.py — NuTERA ranking function checker (public entry point).

Verifies program termination given a user-supplied ranking function
and a compiled Java program JAR.

Usage
-----
    python checker/rf_check.py --jar path/to/program.jar --rank "ReLU(n-i)"
    python checker/rf_check.py --jar program.jar --rank "[ReLU(n-i), ReLU(m-j)]" --delta 1

    # Legacy path still works via the compatibility shim:
    python benchmarking/rf_check.py --jar program.jar --rank "ReLU(n-i)"

Supported rank expression formats
-----------------------------------
  Scalar:   ReLU(n - i) + 2*ReLU(m - j + 1)
  Vector:   [ReLU(n - i), ReLU(m - j)]

where ReLU(x) = max(x, 0) and the interior must be an affine expression.

Path configuration
------------------
The javachecker JAR location defaults to the Docker workspace layout:
    /workspace/deps/javachecker/build/libs/javachecker-uber.jar

Override via environment variable before running:
    export JAVACHECKER_JAR=/custom/path/javachecker-uber.jar

See docs/README_ARTIFACT.md and docs/REPRODUCE.md.

Exit code semantics
-------------------
The delegated checker implementation treats both RESULT=YES and RESULT=NO as
successful checker completion (exit code 0). Non-zero exit codes are reserved
for runtime/infrastructure errors.
"""

import os
import runpy
import sys

# -----------------------------------------------------------------
# Probe common javachecker JAR paths so both Docker and local runs work.
# -----------------------------------------------------------------
if "JAVACHECKER_JAR" not in os.environ:
    _root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    _candidates = [
        os.path.join(_root, "deps", "javachecker", "build", "libs", "javachecker-uber.jar"),
        os.path.join(_root, "libs", "javachecker-uber.jar"),
        "/workspace/deps/javachecker/build/libs/javachecker-uber.jar",
    ]
    for _cand in _candidates:
        if _cand and os.path.exists(_cand):
            os.environ["JAVACHECKER_JAR"] = os.path.abspath(_cand)
            break

# -----------------------------------------------------------------
# Delegate to the checker implementation.
# sys.argv is forwarded so argparse in the implementation sees original args.
# -----------------------------------------------------------------
_impl = os.path.join(os.path.dirname(os.path.abspath(__file__)), "manual_rank_check.py")
runpy.run_path(_impl, run_name="__main__")
