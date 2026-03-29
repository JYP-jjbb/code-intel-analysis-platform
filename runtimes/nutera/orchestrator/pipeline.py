"""
NuTERA workflow orchestrator.

The workflow is organized as:

    Prepared CSV  →  Build program queue
                  →  Ranking Function Synthesis
                  →  Rank Checking
                  →  Final Report

Input programs are loaded from a problem-set CSV.  Each row is converted into
a lightweight :class:`_ProgramRecord` that carries the fields required by the
synthesis and checking stages.
"""

from typing import List, Optional, Dict, Any, Tuple
from datetime import datetime
from dataclasses import dataclass
import csv
import os
import glob

from core import Config, Logger, PipelineState
from core.state import StateManager
from .error_handler import ErrorHandler
from agents.ranking_function_synthesizer import RankingFunctionSynthesizer
from agents.ranking_function_checker import RankingFunctionChecker


@dataclass
class _ProgramRecord:
    """
    Lightweight program record created from a problem-set CSV row.

    The synthesizer expects a program-like object with stable attributes:
        name, class_name, jar_path, function_name, to_dict()

    This record allows the workflow to start directly from prepared benchmark
    metadata without relying on earlier preprocessing stages.
    """

    name: str
    jar_path: str
    class_name: str

    function_name: str = "loop"
    method_name: str = "loop"
    function: str = "loop"
    benchmark: str = ""
    final_status: str = "Pending"

    @property
    def jar(self) -> str:
        return self.jar_path

    def to_dict(self) -> Dict[str, Any]:
        """Export a dictionary representation for downstream writers."""
        data = dict(self.__dict__)
        data.setdefault("jar", self.jar_path)
        data.setdefault("function", self.function_name)
        data.setdefault("method", self.function_name)
        data.setdefault("method_name", self.function_name)
        return data


class TerminationProofPipeline:
    """
    Coordinator for the end-to-end NuTERA workflow.

    Responsibilities:
    1. Load configuration and runtime services.
    2. Build the program queue from a prepared CSV file.
    3. Execute ranking-function synthesis and SMT checking.
    4. Persist checkpoints and generate a final report.
    """

    def __init__(self, config_path: str):
        self.config = Config.load(config_path)
        self.logger = Logger(self.config.logging)
        self.state_manager = StateManager(self.config)
        self.error_handler = ErrorHandler(self.config, self.logger)

        self.synthesizer = RankingFunctionSynthesizer(self.config, self.logger)
        self.checker = RankingFunctionChecker(self.config, self.logger)

    # ------------------------------------------------------------------
    # CSV and path utilities
    # ------------------------------------------------------------------

    def _pick_first_nonempty(self, row: Dict[str, Any], keys: List[str]) -> str:
        """Return the first non-empty value among candidate column names."""
        for key in keys:
            if key in row and row[key] is not None and str(row[key]).strip() != "":
                return str(row[key]).strip()
        return ""

    @staticmethod
    def _infer_group_from_file_path(file_value: str) -> str:
        """
        Derive a benchmark group name from a problem-set File path.

        For paths of the form ``../some-benchmark-suite/ProgramDir/Program.jar``
        the first path component after ``../`` is returned as the group name.
        Returns an empty string when the pattern does not match.
        """
        if not file_value:
            return ""
        normalized = file_value.replace("\\", "/").strip()
        if normalized.startswith("../"):
            rest = normalized[3:]
            idx = rest.find("/")
            return rest[:idx] if idx != -1 else rest
        return ""

    def _collect_search_roots(self, csv_path: str) -> List[str]:
        """
        Build the list of directories that may contain benchmark artifacts.

        Expected project layout inside Docker::

            /workspace/
                main/          ← Python package (this file lives here)
                benchmarking/  ← benchmark JARs, problem-set CSVs
                libs/          ← Z3 / javachecker jars
                deps/          ← native deps

        The resolver also considers configuration-defined roots and the CSV
        location so that the workflow remains robust across local and Docker
        environments.
        """
        roots: List[str] = []

        module_dir = os.path.abspath(os.path.dirname(__file__))              # .../main/orchestrator
        package_root = os.path.abspath(os.path.join(module_dir, ".."))       # .../main
        workspace_root = os.path.abspath(os.path.join(package_root, ".."))   # .../

        roots.extend([
            os.getcwd(),
            os.path.dirname(os.path.abspath(csv_path)),
            package_root,
            os.path.join(package_root, "benchmarking"),
            os.path.join(package_root, "libs"),
            workspace_root,
            os.path.join(workspace_root, "benchmarking"),
            os.path.join(workspace_root, "libs"),
            "/workspace",
            "/workspace/main",
            "/workspace/benchmarking",
            "/workspace/libs",
        ])

        if hasattr(self.config, "paths"):
            for attr in (
                "workspace_root", "project_root", "repo_root",
                "benchmarking_dir", "benchmark_dir", "benchmark_root",
                "benchmarks_dir", "benchmarking_root", "libs_dir", "jar_dir",
            ):
                value = getattr(self.config.paths, attr, None)
                if isinstance(value, str) and value.strip():
                    roots.append(os.path.abspath(value.strip()))

        unique_roots: List[str] = []
        seen: set = set()
        for root in roots:
            norm = os.path.abspath(root)
            if norm not in seen:
                seen.add(norm)
                unique_roots.append(norm)

        return unique_roots

    def _rewrite_path_aliases(self, raw_path: str) -> List[str]:
        """
        Expand a path into candidate variants for path resolution.

        Benchmark CSV rows may contain paths from different workspace layouts.
        This method generates alternative path candidates from a raw path value,
        allowing the resolver to locate JAR files regardless of the layout used
        when the CSV was originally prepared.
        """
        if not raw_path:
            return []

        normalized = raw_path.replace("\\", "/")
        variants = [raw_path]

        # Internal compatibility shim for historical configs/checkpoints:
        # translate paths that contain older workspace prefixes.
        replacements = [
            ("/workspace/termination_agent/benchmarking/", ["/workspace/benchmarking/", "/workspace/main/benchmarking/"]),
            ("/workspace/termination_agent/libs/",         ["/workspace/libs/",          "/workspace/main/libs/"]),
            ("/workspace/termination_agent/",              ["/workspace/main/",           "/workspace/"]),
            ("termination_agent/benchmarking/",            ["benchmarking/",              "main/benchmarking/"]),
            ("termination_agent/libs/",                    ["libs/",                      "main/libs/"]),
            ("termination_agent/",                         ["main/",                      ""]),
        ]

        for old_prefix, new_prefixes in replacements:
            if old_prefix in normalized:
                for new_prefix in new_prefixes:
                    variants.append(normalized.replace(old_prefix, new_prefix, 1))

        unique_variants: List[str] = []
        seen: set = set()
        for item in variants:
            if item not in seen:
                seen.add(item)
                unique_variants.append(item)

        return unique_variants

    def _resolve_jar_path(self, file_value: str, class_name: str, csv_path: str) -> str:
        """
        Resolve a jar path from a CSV field.

        Resolution strategy:
        1. Try the raw value directly.
        2. Try project-aware rewritten aliases.
        3. If relative, anchor under known workspace roots.
        4. If a directory is given, select a matching jar inside it.
        5. Scan known libs/benchmarking locations as a final fallback.
        """
        if not file_value:
            return ""

        roots = self._collect_search_roots(csv_path)
        raw_candidates = self._rewrite_path_aliases(file_value.strip())

        candidates: List[str] = []
        for item in raw_candidates:
            candidates.append(item)
            if not item.endswith(".jar"):
                candidates.append(item + ".jar")

        expanded: List[str] = []
        for candidate in candidates:
            expanded.append(candidate)
            if not os.path.isabs(candidate):
                for root in roots:
                    expanded.append(os.path.join(root, candidate))

        unique_candidates: List[str] = []
        seen: set = set()
        for candidate in expanded:
            norm = os.path.abspath(candidate)
            if norm not in seen:
                seen.add(norm)
                unique_candidates.append(norm)

        for candidate in unique_candidates:
            if os.path.isfile(candidate) and candidate.endswith(".jar"):
                return candidate

            if os.path.isdir(candidate):
                jars = glob.glob(os.path.join(candidate, "*.jar"))
                if not jars:
                    continue
                for jar in jars:
                    if class_name and class_name in os.path.basename(jar):
                        return jar
                if len(jars) == 1:
                    return jars[0]
                jars.sort()
                return jars[0]

        fallback_dirs = []
        for root in roots:
            for sub in ("", "libs", "benchmarking"):
                path = os.path.join(root, sub) if sub else root
                if os.path.isdir(path):
                    fallback_dirs.append(path)

        checked_dirs: set = set()
        for directory in fallback_dirs:
            directory = os.path.abspath(directory)
            if directory in checked_dirs:
                continue
            checked_dirs.add(directory)

            pattern = os.path.join(directory, "**", "*.jar")
            jars = glob.glob(pattern, recursive=True)
            if class_name:
                matched = [jar for jar in jars if class_name.lower() in os.path.basename(jar).lower()]
                if matched:
                    matched.sort()
                    return matched[0]

        return ""

    def _build_program_queue_from_csv(
        self, csv_path: str
    ) -> Tuple[Dict[str, Any], List[Any]]:
        """
        Load benchmark metadata from a prepared problem-set CSV.

        Returns:
            programs_dict: class_name → program record mapping
            queue:         ordered list of program records for synthesis
        """
        if not csv_path:
            raise ValueError("problem_set_csv path is empty")

        programs_dict: Dict[str, Any] = {}
        queue: List[Any] = []

        with open(csv_path, "r", encoding="utf-8-sig", newline="") as handle:
            reader = csv.DictReader(handle)
            if not reader.fieldnames:
                raise ValueError(f"problem-set CSV has no header: {csv_path}")

            for idx, row in enumerate(reader):
                if not row:
                    continue

                file_value = self._pick_first_nonempty(
                    row,
                    keys=[
                        "File", "FILE", "file",
                        "jar_path", "jar", "jarfile", "jar_file", "jarFile",
                        "program_path", "path", "Path", "filepath", "FilePath",
                    ],
                )
                class_name = self._pick_first_nonempty(
                    row,
                    keys=["Class", "CLASS", "class", "class_name", "classname", "ClassName"],
                )
                method_name = self._pick_first_nonempty(
                    row,
                    keys=["Function", "FUNCTION", "function", "method", "Method", "method_name", "MethodName"],
                ) or "loop"
                benchmark = self._pick_first_nonempty(
                    row, keys=["benchmark", "Benchmark", "suite", "group", "category", "set"],
                ) or self._infer_group_from_file_path(file_value)

                if not class_name:
                    raise ValueError(
                        f"CSV row {idx + 2} is missing a class name. "
                        f"Detected headers: {reader.fieldnames}"
                    )

                jar_path = self._resolve_jar_path(file_value, class_name, csv_path)
                if not jar_path:
                    raise ValueError(
                        f"CSV row {idx + 2} cannot resolve a jar file from File='{file_value}' "
                        f"for class_name='{class_name}'. Headers={reader.fieldnames}"
                    )

                program = _ProgramRecord(
                    name=class_name,
                    jar_path=jar_path,
                    class_name=class_name,
                    function_name=method_name,
                    method_name=method_name,
                    function=method_name,
                    benchmark=benchmark,
                    final_status="Pending",
                )

                programs_dict[class_name] = program
                queue.append(program)

        return programs_dict, queue

    def _normalize_program_queue(self, state: PipelineState):
        """
        Ensure state.llm_queue contains program objects rather than name strings.

        Checkpoints may store the queue in different shapes.  This normalizer
        converts string-lists and dict-lists into the object representation
        expected by the synthesizer.
        """
        queue = getattr(state, "llm_queue", None)
        programs_dict = getattr(state, "programs_dict", None)

        if not queue or not isinstance(queue, list):
            return
        if not programs_dict or not isinstance(programs_dict, dict):
            return

        first = queue[0]

        if isinstance(first, str):
            normalized = []
            missing = []
            for name in queue:
                if name in programs_dict:
                    normalized.append(programs_dict[name])
                else:
                    missing.append(name)

            if missing:
                raise ValueError(
                    f"llm_queue contains names not in programs_dict: "
                    f"{missing[:5]} ... total={len(missing)}"
                )
            state.llm_queue = normalized
            return

        if isinstance(first, dict):
            normalized = []
            for item in queue:
                class_name = item.get("class_name") or item.get("name")
                jar_path = item.get("jar_path") or item.get("jar") or ""
                function_name = (
                    item.get("function_name")
                    or item.get("method_name")
                    or item.get("function")
                    or "loop"
                )
                program = _ProgramRecord(
                    name=item.get("name") or class_name,
                    jar_path=jar_path,
                    class_name=class_name,
                    function_name=function_name,
                    method_name=item.get("method_name") or function_name,
                    function=item.get("function") or function_name,
                    benchmark=item.get("benchmark", ""),
                    final_status=item.get("final_status", "Pending"),
                )
                normalized.append(program)

            state.llm_queue = normalized

    # ------------------------------------------------------------------
    # Main workflow entry
    # ------------------------------------------------------------------

    def run_full_pipeline(
        self,
        problem_set_csv: str,
        resume_from: Optional[str] = None,
    ):
        """
        Execute the end-to-end NuTERA workflow.

        Steps:
        1. Load or initialize pipeline state.
        2. Build the program queue from the input CSV when needed.
        3. Run ranking-function synthesis and checking.
        4. Generate a final report.

        Args:
            problem_set_csv: Path to the prepared benchmark CSV.
            resume_from:     Path to a previously saved checkpoint (optional).
        """
        try:
            if resume_from:
                state = self.state_manager.load_checkpoint(resume_from)
                self.logger.info(f"Resuming from checkpoint: {resume_from}")
            else:
                state = PipelineState()

            need_queue = (
                getattr(state, "programs_dict", None) is None
                or getattr(state, "llm_queue", None) is None
                or len(getattr(state, "programs_dict", {}) or {}) == 0
                or len(getattr(state, "llm_queue", []) or []) == 0
            )

            if need_queue:
                self.logger.info("=" * 60)
                self.logger.info("BUILDING PROGRAM QUEUE FROM PROBLEM-SET CSV")
                self.logger.info("=" * 60)
                self.logger.info(f"Input CSV: {problem_set_csv}")

                programs_dict, queue = self._build_program_queue_from_csv(problem_set_csv)
                state.programs_dict = programs_dict
                state.llm_queue = queue
                state.queue_built = True

                self.logger.info(f"Loaded {len(queue)} program(s) into the synthesis queue")
                self.state_manager.save_checkpoint(state, "after_queue_build")
            else:
                self._normalize_program_queue(state)
                self.logger.info("=" * 60)
                self.logger.info("USING PROGRAM QUEUE FROM CHECKPOINT")
                self.logger.info("=" * 60)
                self.logger.info(f"Queue size: {len(state.llm_queue)}")

            self._normalize_program_queue(state)

            # --- Ranking Function Synthesis + Checking ---
            synthesis_done = getattr(state, "synthesis_completed", False)
            skip_synthesis = getattr(self.config.pipeline, "skip_synthesis", False)

            if not synthesis_done and not skip_synthesis:
                self.logger.info("\n" + "=" * 60)
                self.logger.info("STAGE: RANKING FUNCTION SYNTHESIS AND CHECKING")
                self.logger.info("=" * 60)

                try:
                    updated_programs = self.synthesizer.run(llm_queue=state.llm_queue)

                    if getattr(state, "programs_dict", None) is None:
                        state.programs_dict = {}

                    for program in updated_programs:
                        state.programs_dict[program.class_name] = program

                    state.synthesis_completed = True
                    self.state_manager.save_checkpoint(state, "after_synthesis")

                    self.logger.info("Synthesis and checking stage completed")

                except Exception as exc:
                    self.error_handler.handle_stage_error("synthesis", exc)
                    if self.config.error_handling.fail_fast:
                        raise

            elif skip_synthesis:
                self.logger.info("\n" + "=" * 60)
                self.logger.info("STAGE: SYNTHESIS — skipped by configuration")
                self.logger.info("=" * 60)
                state.synthesis_completed = True

            # --- Final Report ---
            self.logger.info("\n" + "=" * 60)
            self.logger.info("GENERATING FINAL REPORT")
            self.logger.info("=" * 60)

            self._generate_final_report(getattr(state, "programs_dict", {}) or {})

            self.logger.info("\n" + "=" * 60)
            self.logger.info("WORKFLOW COMPLETED SUCCESSFULLY")
            self.logger.info("=" * 60)

        except Exception as exc:
            self.logger.error(f"Workflow failed: {exc}")
            import traceback
            self.logger.error(traceback.format_exc())
            raise

    # ------------------------------------------------------------------
    # Single-stage entry
    # ------------------------------------------------------------------

    def run_benchmark(self, stage: str, **kwargs):
        """
        Run a single workflow stage in isolation.

        Supported stages:
            ``synthesis`` – ranking-function synthesis
            ``checker``   – ranking-function checking

        Args:
            stage:    Stage identifier.
            **kwargs: Stage-specific keyword arguments forwarded to :meth:`run`.
        """
        stage_map = {
            "synthesis": self.synthesizer,
            "checker":   self.checker,
        }

        if stage not in stage_map:
            raise ValueError(
                f"Unknown stage: '{stage}'. Valid stages: synthesis, checker"
            )

        if stage == "synthesis" and "llm_queue" not in kwargs:
            csv_path = kwargs.pop("problem_set_csv", None)
            if csv_path:
                programs_dict, queue = self._build_program_queue_from_csv(csv_path)
                kwargs["llm_queue"] = queue
                self.logger.info(f"Built program queue from CSV: {csv_path}")
                self.logger.info(f"Queue size: {len(queue)}")

        self.logger.info(f"Running stage: {stage} ...")
        return stage_map[stage].run(**kwargs)

    # ------------------------------------------------------------------
    # Reporting
    # ------------------------------------------------------------------

    def _generate_final_report(self, programs_dict: Dict[str, Any]):
        """Generate an aggregate text summary for the current run."""
        total = len(programs_dict)
        stats = {
            "total": total,
            "verified_ok": 0,
            "relu_limit": 0,
            "synthesis_failed": 0,
            "synthesis_verified": 0,
        }

        for program in programs_dict.values():
            final_status = getattr(program, "final_status", "Unknown")

            if final_status == "Verified OK":
                stats["verified_ok"] += 1
                stats["synthesis_verified"] += 1
            elif final_status == "ReLU Limit":
                stats["relu_limit"] += 1
            elif final_status == "LLM MaxFail":
                stats["synthesis_failed"] += 1

        def pct(n: int) -> int:
            return 100 * n // total if total > 0 else 0

        self.logger.info("\n=== FINAL STATISTICS ===")
        self.logger.info(f"Total programs:   {stats['total']}")
        self.logger.info(f"Verified OK:      {stats['verified_ok']} ({pct(stats['verified_ok'])}%)")
        self.logger.info(f"  - Synthesis-verified: {stats['synthesis_verified']}")
        self.logger.info(f"ReLU Limit:       {stats['relu_limit']} ({pct(stats['relu_limit'])}%)")
        self.logger.info(f"Synthesis failed: {stats['synthesis_failed']} ({pct(stats['synthesis_failed'])}%)")
        self.logger.info("")

        results_dir = self.config.paths.results_dir
        os.makedirs(results_dir, exist_ok=True)
        summary_path = os.path.join(results_dir, "final_summary.txt")

        with open(summary_path, "w", encoding="utf-8") as fh:
            fh.write("NuTERA Research Artifact — Final Report\n")
            fh.write(f"Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n\n")
            fh.write(f"Total programs:   {stats['total']}\n")
            fh.write(f"Verified OK:      {stats['verified_ok']} ({pct(stats['verified_ok'])}%)\n")
            fh.write(f"  - Synthesis-verified: {stats['synthesis_verified']}\n")
            fh.write(f"ReLU Limit:       {stats['relu_limit']} ({pct(stats['relu_limit'])}%)\n")
            fh.write(f"Synthesis failed: {stats['synthesis_failed']} ({pct(stats['synthesis_failed'])}%)\n")

        self.logger.info(f"Summary saved to: {summary_path}")
