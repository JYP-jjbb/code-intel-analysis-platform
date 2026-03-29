#!/usr/bin/env python3
"""
NuTERA Research Artifact — CLI entry point.

Workflow
--------
    Prepared problem-set CSV
    → Ranking Function Synthesis  (LLM + iterative SMT loop)
    → Rank Checking               (javachecker / Z3)
    → Final Report

Quick start
-----------
    python main.py run \\
        --problem-set-csv benchmarking/problem-sets/eight_sets.csv \\
        --config config/default.yaml
"""

import argparse
import sys
import json
from orchestrator import TerminationProofPipeline


def _parse_json_params(params_str: str) -> dict:
    if not params_str:
        return {}
    try:
        return json.loads(params_str)
    except json.JSONDecodeError as exc:
        raise ValueError(f"Invalid JSON in --params: {exc}") from exc


def main():
    parser = argparse.ArgumentParser(
        prog="nutera",
        description="NuTERA Research Artifact CLI",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples
--------
  # Run the full workflow
  python main.py run \\
    --problem-set-csv benchmarking/problem-sets/eight_sets.csv \\
    --config config/default.yaml

  # Resume from a saved checkpoint
  python main.py run \\
    --problem-set-csv benchmarking/problem-sets/eight_sets.csv \\
    --resume-from results/checkpoints/after_synthesis_20250101_120000.pkl

  # Run the synthesis stage only
  python main.py run-stage synthesis \\
    --problem-set-csv benchmarking/problem-sets/eight_sets.csv

  # Run the checker stage standalone
  python main.py run-stage checker \\
    --params '{"jar_path": "benchmarking/OSS/Demo/Demo.jar", "class_name": "Demo", "method_name": "loop", "rank_expr": "ReLU(n-i)"}'

  # Smoke test (3 programs)
  python main.py run \\
    --problem-set-csv benchmarking/problem-sets/smoke_test.csv
""",
    )

    subparsers = parser.add_subparsers(dest="command", help="Available commands")

    # -----------------------------------------------------------------------
    # run — full workflow
    # -----------------------------------------------------------------------
    run_parser = subparsers.add_parser(
        "run",
        help="Run the full NuTERA workflow from a prepared problem-set CSV",
    )
    run_parser.add_argument(
        "--problem-set-csv",
        required=True,
        metavar="PATH",
        help="Path to the prepared problem-set CSV file",
    )
    run_parser.add_argument(
        "--config",
        default="config/default.yaml",
        metavar="PATH",
        help="Configuration file (default: config/default.yaml)",
    )
    run_parser.add_argument(
        "--resume-from",
        metavar="PATH",
        help="Resume from a saved checkpoint (.pkl file)",
    )
    run_parser.add_argument(
        "--skip-synthesis",
        action="store_true",
        help="Skip the synthesis stage (dry-run only)",
    )
    # Hidden backward-compatibility alias — not shown in help
    run_parser.add_argument("--skip-unit3", action="store_true", help=argparse.SUPPRESS)

    # -----------------------------------------------------------------------
    # run-stage — single workflow stage
    # -----------------------------------------------------------------------
    stage_parser = subparsers.add_parser(
        "run-stage",
        help="Run a single workflow stage independently",
    )
    stage_parser.add_argument(
        "stage",
        metavar="STAGE",
        help="Stage to run: synthesis or checker",
    )
    stage_parser.add_argument(
        "--problem-set-csv",
        metavar="PATH",
        help="CSV path (synthesis stage)",
    )
    stage_parser.add_argument(
        "--config",
        default="config/default.yaml",
        metavar="PATH",
        help="Configuration file (default: config/default.yaml)",
    )
    stage_parser.add_argument(
        "--params",
        metavar="JSON",
        help="JSON string of stage parameters",
    )

    # -----------------------------------------------------------------------
    # run-unit — hidden backward-compatibility alias for run-stage
    # -----------------------------------------------------------------------
    unit_parser = subparsers.add_parser("run-unit", help=argparse.SUPPRESS)
    unit_parser.add_argument("unit")
    unit_parser.add_argument("--config", default="config/default.yaml")
    unit_parser.add_argument("--params", default=None)

    # -----------------------------------------------------------------------
    # Dispatch
    # -----------------------------------------------------------------------
    args = parser.parse_args()

    if not args.command:
        parser.print_help()
        sys.exit(1)

    config_path = getattr(args, "config", "config/default.yaml")

    try:
        pipeline = TerminationProofPipeline(config_path)
    except Exception as exc:
        print(f"Error: failed to initialise pipeline: {exc}", file=sys.stderr)
        sys.exit(1)

    try:
        if args.command == "run":
            skip = args.skip_synthesis or getattr(args, "skip_unit3", False)
            if skip:
                pipeline.config.pipeline.skip_synthesis = True
            pipeline.run_full_pipeline(
                problem_set_csv=args.problem_set_csv,
                resume_from=args.resume_from,
            )

        elif args.command in ("run-stage", "run-unit"):
            stage = getattr(args, "stage", None) or getattr(args, "unit", None)
            # Map legacy stage names to current names at the CLI boundary.
            _STAGE_ALIASES = {"unit3": "synthesis", "unit4": "checker",
                              "unit1": "synthesis", "unit2": "synthesis"}
            stage = _STAGE_ALIASES.get(stage, stage)
            params = _parse_json_params(args.params)
            csv_path = getattr(args, "problem_set_csv", None)
            if csv_path and "llm_queue" not in params and "problem_set_csv" not in params:
                params["problem_set_csv"] = csv_path
            result = pipeline.run_benchmark(stage, **params)
            if result is not None:
                print(f"\nStage '{stage}' completed.")
                print(f"Result: {result}")

    except KeyboardInterrupt:
        print("\n\nInterrupted by user", file=sys.stderr)
        sys.exit(130)
    except Exception as exc:
        print(f"\nError: {exc}", file=sys.stderr)
        import traceback
        traceback.print_exc()
        sys.exit(1)


if __name__ == "__main__":
    main()
