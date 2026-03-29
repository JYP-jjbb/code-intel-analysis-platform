# NuTERA Artifact

LLM-synthesized ReLU ranking functions for Java termination, verified by an SMT checker (javachecker + Z3).

**Flow:** Problem-set CSV → synthesis (LLM + checker loop) → rank checking → report.

## Reproduction

**Primary:** Docker. See [REPRODUCE.md](docs/REPRODUCE.md) for step-by-step instructions (build image, first run, reuse container, expected outputs, troubleshooting).

**Small-dataset example:** `benchmarking/problem-sets/nuTerm_advantage_set.csv` (only recommended small run; no other small-set example).

## Environment (Docker or local)

- Python 3.9+, OpenJDK 11, Z3 4.11.0 (built into `libs/`), pyjnius. LLM: set `OPENROUTER_API_KEY` or `OPENAI_API_KEY`.
- Optional: `torch` and `psutil` for trace-based variable detection; otherwise checker uses `javap`.
- In Docker, use `python3` (not `python`) for all commands.

## Outputs

- `results/final_summary.txt` — totals and percentages
- `results/program_level/program_level_after_synthesis_*.csv` — per-program status
- `results/attempt_level/attempts_*.csv` — per-attempt data
- `results/checkpoints/*.pkl` — resume checkpoint

## Layout

See [PROJECT_LAYOUT.md](docs/PROJECT_LAYOUT.md) for directory tree and roles.
