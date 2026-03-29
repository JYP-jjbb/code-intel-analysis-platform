"""
Ranking function checker.

Wraps ``benchmarking/rf_check.py`` and provides a stable Python interface
for checker invocation and result parsing.
"""

import os
import subprocess
import time
import re
import json
from typing import Optional

from core import Config, Logger, CheckResult


class RankingFunctionChecker:
    """Checker wrapper backed by checker/rf_check.py (benchmarking/rf_check.py is a compat shim)."""

    # Script names probed in order; manual_rank_check.py is an internal fallback.
    _SCRIPT_NAMES = ("rf_check.py", "manual_rank_check.py")

    def __init__(self, config: Config, logger: Logger):
        self.config = config
        self.logger = logger
        self.script_path = self._resolve_checker_script()

    def _resolve_checker_script(self) -> str:
        """
        Resolve the checker script path across workspace layouts.

        Search order:
        1. config.paths attributes (benchmarking_dir, etc.)
        2. cwd / workspace-relative paths
        3. Docker canonical paths (/workspace/...)
        """
        module_dir = os.path.abspath(os.path.dirname(__file__))             # .../main/agents
        package_root = os.path.abspath(os.path.join(module_dir, ".."))      # .../main
        workspace_root = os.path.abspath(os.path.join(package_root, ".."))  # .../

        search_roots = []

        if hasattr(self.config, "paths"):
            for attr in ("benchmarking_dir", "project_root", "repo_root", "workspace_root"):
                value = getattr(self.config.paths, attr, None)
                if isinstance(value, str) and value.strip():
                    search_roots.append(os.path.abspath(value.strip()))

        search_roots.extend([
            os.getcwd(),
            workspace_root,
            os.path.join(workspace_root, "checker"),        # canonical location
            os.path.join(workspace_root, "benchmarking"),   # compat shim location
            package_root,
            os.path.join(package_root, "checker"),
            os.path.join(package_root, "benchmarking"),
            "/workspace",
            "/workspace/checker",
            "/workspace/benchmarking",
            "/workspace/main",
            "/workspace/main/checker",
            "/workspace/main/benchmarking",
        ])

        seen: set = set()
        for script_name in self._SCRIPT_NAMES:
            for root in search_roots:
                candidates = [
                    os.path.join(root, script_name),
                    os.path.join(root, "benchmarking", script_name),
                ]
                for path in candidates:
                    norm = os.path.abspath(path)
                    if norm in seen:
                        continue
                    seen.add(norm)
                    if os.path.exists(norm):
                        return norm

        # Fallback: return a predictable location even if it doesn't exist yet.
        fallback_dir = getattr(
            getattr(self.config, "paths", None),
            "benchmarking_dir",
            workspace_root,
        )
        return os.path.join(fallback_dir, "rf_check.py")

    def check(
        self,
        jar_path: str,
        class_name: str,
        method_name: str,
        rank_expr: str,
        delta: Optional[int] = None,
    ) -> CheckResult:
        """
        Invoke the checker script and return a structured CheckResult.

        Args:
            jar_path:    Absolute path to the benchmark JAR.
            class_name:  Java class to analyse.
            method_name: Target method (typically "loop").
            rank_expr:   ReLU ranking-function expression string.
            delta:       Lexicographic decrease margin (default from config).

        Returns:
            A :class:`CheckResult` with status in {YES, NO, ERROR}.
        """
        if delta is None:
            delta = self.config.checker.delta

        start_time = time.time()

        cmd = [
            "python3",
            self.script_path,
            "--jar", jar_path,
            "--class", class_name,
            "--method", method_name,
            "--auto-assume-from-main",
            "--rank", rank_expr,
            "--delta", str(delta),
        ]

        self.logger.debug(f"Running checker for: {class_name}.{method_name}")
        self.logger.debug(f"RF: {rank_expr}")

        try:
            result = subprocess.run(
                cmd,
                capture_output=True,
                text=True,
                timeout=self.config.checker.timeout,
                cwd=os.path.dirname(self.script_path) or None,
            )

            execution_time = time.time() - start_time

            return self._parse_output(
                stdout=result.stdout,
                stderr=result.stderr,
                exit_code=result.returncode,
                execution_time=execution_time,
            )

        except subprocess.TimeoutExpired:
            self.logger.error(f"Checker timeout for {class_name}")
            return CheckResult(
                status="ERROR",
                counterexample=None,
                raw_output="Timeout",
                exit_code=-1,
                execution_time=self.config.checker.timeout,
                checkVec_info=None,
            )

        except Exception as exc:
            self.logger.error(f"Checker exception for {class_name}: {exc}")
            return CheckResult(
                status="ERROR",
                counterexample=None,
                raw_output=str(exc),
                exit_code=-1,
                execution_time=time.time() - start_time,
                checkVec_info=None,
            )

    def _parse_output(
        self,
        stdout: str,
        stderr: str,
        exit_code: int,
        execution_time: float,
    ) -> CheckResult:
        """Parse checker stdout/stderr into a structured CheckResult."""
        combined = stdout or ""
        if stderr:
            combined += "\n\n=== STDERR ===\n" + stderr

        checkVec_info = self._extract_checkVec_info(combined)

        result_index = combined.find("RESULT")
        if result_index == -1:
            return CheckResult(
                status="ERROR",
                raw_output=combined,
                exit_code=exit_code,
                execution_time=execution_time,
                checkVec_info=checkVec_info,
                counterexample=None,
            )

        result_section = combined[result_index:]

        if re.search(r"\bYES\b", result_section) and exit_code == 0:
            return CheckResult(
                status="YES",
                raw_output=combined,
                exit_code=exit_code,
                execution_time=execution_time,
                checkVec_info=checkVec_info,
                counterexample=None,
            )

        if re.search(r"\bNO\b", result_section):
            cex = self._extract_counterexample(result_section)
            return CheckResult(
                status="NO",
                raw_output=combined,
                exit_code=exit_code,
                execution_time=execution_time,
                checkVec_info=checkVec_info,
                counterexample=cex,
            )

        return CheckResult(
            status="ERROR",
            raw_output=combined,
            exit_code=exit_code,
            execution_time=execution_time,
            checkVec_info=checkVec_info,
            counterexample=None,
        )

    def _extract_counterexample(self, result_section: str) -> Optional[str]:
        """Extract a counterexample JSON object from the RESULT section."""
        cex_start = result_section.find("Counterexample:")
        if cex_start == -1:
            return None

        json_start = result_section.find("{", cex_start)
        if json_start == -1:
            return None

        brace_count = 0
        json_end = json_start
        for i in range(json_start, len(result_section)):
            if result_section[i] == "{":
                brace_count += 1
            elif result_section[i] == "}":
                brace_count -= 1
                if brace_count == 0:
                    json_end = i + 1
                    break

        return result_section[json_start:json_end]

    def _extract_checkVec_info(self, stdout: str) -> Optional[str]:
        """Collect [checkVec] annotation lines from checker output."""
        lines = stdout.split("\n")
        checkVec_lines = [ln for ln in lines if "[checkVec]" in ln]
        return "\n".join(checkVec_lines) if checkVec_lines else None

    def run(self, **kwargs) -> CheckResult:
        """Entry point for standalone stage execution."""
        return self.check(**kwargs)


# ---------------------------------------------------------------------------
# Backward-compatibility alias
# ---------------------------------------------------------------------------
Unit4RankChecker = RankingFunctionChecker
