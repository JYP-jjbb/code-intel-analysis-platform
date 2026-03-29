#!/usr/bin/env python3
"""
Compatibility shim — the canonical entry point is checker/rf_check.py.

All arguments are forwarded transparently; behaviour is identical.

    python benchmarking/rf_check.py --jar program.jar --rank "ReLU(n-i)"
    # equivalent to:
    python checker/rf_check.py     --jar program.jar --rank "ReLU(n-i)"
"""

import os
import runpy

_here = os.path.dirname(os.path.abspath(__file__))
_impl = os.path.normpath(os.path.join(_here, "..", "checker", "rf_check.py"))
runpy.run_path(_impl, run_name="__main__")
