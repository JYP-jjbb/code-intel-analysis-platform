# Project Layout

Current repository structure. One source of truth for paths.

```
NuTERA_release/
├── main.py
├── Makefile
├── requirements.txt
├── requirements-lock.txt
├── Dockerfile
├── config/
│   └── default.yaml
├── orchestrator/
│   ├── __init__.py
│   ├── pipeline.py
│   └── error_handler.py
├── agents/
│   ├── __init__.py
│   ├── ranking_function_synthesizer.py
│   ├── ranking_function_checker.py
│   └── attempt_logger.py
├── core/
│   ├── __init__.py
│   ├── config.py
│   ├── models.py
│   ├── logger.py
│   └── state.py
├── llm/
│   ├── __init__.py
│   ├── client.py
│   ├── prompts.py
│   └── parser.py
├── utils/
│   ├── __init__.py
│   ├── csv_manager.py
│   └── subprocess_helper.py
├── checker/
│   ├── rf_check.py              # Standalone checker CLI (canonical)
│   ├── manual_rank_check.py     # Checker implementation
│   └── checker_bridge/
│       ├── __init__.py
│       ├── javachecker.py       # Essential
│       ├── loopheads.py         # Essential
│       ├── utils.py             # Optional (trace-based vars)
│       ├── termination.py
│       └── tracing/
├── benchmarking/
│   ├── problem-sets/            # *.csv (e.g. nuTerm_advantage_set.csv, six_sets.csv)
│   ├── rf_check.py              # Shim → checker/rf_check.py
│   ├── manual_rank_check.py    # Shim → checker/manual_rank_check.py
│   └── ...
├── deps/
│   └── javachecker/
├── libs/
└── docs/
```

**Roles**

- `main.py`: CLI `run` and `run-stage` (synthesis, checker).
- `orchestrator/`: pipeline; `agents/`: synthesizer + checker wrapper; `core/`: config, models, state; `llm/`: client and prompts.
- `checker/`: rank checker; canonical entry is `checker/rf_check.py`. `benchmarking/rf_check.py` and `benchmarking/manual_rank_check.py` are compatibility shims only.
- `checker/checker_bridge/`: Java interop. `javachecker.py` and `loopheads.py` required; `utils.py` (and thus `termination.py`, `tracing/`) optional for trace-based variable detection.
