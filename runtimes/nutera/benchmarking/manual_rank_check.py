#!/usr/bin/env python3
"""
Compatibility shim — the canonical implementation is checker/manual_rank_check.py.

End users should invoke checker/rf_check.py (or the benchmarking/rf_check.py shim).
This file exists only for backward compatibility.
"""

import os
import runpy

_here = os.path.dirname(os.path.abspath(__file__))
_impl = os.path.normpath(os.path.join(_here, "..", "checker", "manual_rank_check.py"))
runpy.run_path(_impl, run_name="__main__")
