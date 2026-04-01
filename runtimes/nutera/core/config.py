"""
Configuration dataclasses for the NuTERA workflow.

Loaded from ``config/default.yaml`` via :meth:`Config.load`.

The primary workflow configuration fields are:
- ``LLMConfig``     — LLM provider and generation parameters
- ``PathsConfig``   — Directory layout
- ``CheckerConfig`` — Checker invocation parameters
- ``PipelineConfig``— Workflow flags
- ``ErrorHandlingConfig``, ``ConcurrencyConfig``, ``LoggingConfig``
"""

import os
import yaml
from dataclasses import dataclass, field
from typing import Optional, Dict, Any


@dataclass
class LLMConfig:
    """LLM provider and generation configuration."""

    # Provider/model for ranking-function generation
    rf_provider: str = "openai"
    rf_model: str = "gpt-5-mini"

    # Fallback / default (currently unused by core workflow)
    default_provider: str = "openai"
    default_model: str = "gpt-5-mini"

    # API keys
    openai_api_key: Optional[str] = None
    openai_base_url: Optional[str] = None
    openrouter_api_key: Optional[str] = None
    openrouter_base_url: Optional[str] = "https://openrouter.ai/api/v1"
    google_api_key: Optional[str] = None
    deepseek_api_key: Optional[str] = None

    # Generation parameters
    temperature: float = 1.0
    max_tokens: int = 100000
    max_attempts: int = 10

    # Ablation level (0–3; controls synthesizer feedback strategy)
    ablation_level: int = 3

    # OpenRouter / Gemini extras
    gemini_show_reasoning: bool = False
    openrouter_extra_body_by_model: Dict[str, Dict[str, Any]] = field(default_factory=dict)


@dataclass
class PathsConfig:
    """Path-related configuration."""

    benchmarking_dir: str = "benchmarking"
    results_dir: str = "results"
    logs_dir: str = "logs"

    # Retained for config-file compatibility; not used by core workflow
    gen_java_script: str = "gen_java_from_c.py"
    problem_set_csv: str = "problem-sets/generated_problem_set.csv"

    def resolve(self, base_dir: str):
        """Resolve relative paths to absolute paths."""
        for key in ["benchmarking_dir", "results_dir", "logs_dir"]:
            path = getattr(self, key)
            if not os.path.isabs(path):
                setattr(self, key, os.path.abspath(os.path.join(base_dir, path)))


@dataclass
class CheckerConfig:
    """Rank-checker invocation configuration."""

    timeout: int = 120  # Per-program timeout in seconds
    delta: int = 1      # Lexicographic decrease margin


@dataclass
class ErrorHandlingConfig:
    """Error handling configuration."""

    fail_fast: bool = False
    max_retries: int = 3



@dataclass
class PipelineConfig:
    """Workflow execution flags."""

    skip_synthesis: bool = False  # Skip the synthesis stage (dry-run only)


@dataclass
class LoggingConfig:
    """Logging configuration."""

    level: str = "INFO"
    format: str = "%(asctime)s - %(name)s - %(levelname)s - %(message)s"
    file: str = "logs/pipeline.log"


# Known top-level sections in the current workflow config.
# Any other section present in a YAML file is silently ignored so that
# configs written for older versions of the project continue to load.
_KNOWN_SECTIONS = frozenset([
    "llm", "paths", "checker", "error_handling",
    "concurrency", "pipeline", "logging",
])


@dataclass
class Config:
    """
    NuTERA workflow configuration.

    Primary fields:
        llm, paths, checker, error_handling, concurrency, pipeline, logging
    """

    llm: LLMConfig = field(default_factory=LLMConfig)
    paths: PathsConfig = field(default_factory=PathsConfig)
    checker: CheckerConfig = field(default_factory=CheckerConfig)
    error_handling: ErrorHandlingConfig = field(default_factory=ErrorHandlingConfig)
    pipeline: PipelineConfig = field(default_factory=PipelineConfig)
    logging: LoggingConfig = field(default_factory=LoggingConfig)

    @classmethod
    def load(cls, config_path: str) -> "Config":
        """Load configuration from a YAML file."""
        with open(config_path, "r", encoding="utf-8") as f:
            data = yaml.safe_load(f)

        data = cls._resolve_env_vars(data)

        # Filter to known sections only; unknown top-level keys are ignored
        # so that YAML files containing sections from older project versions
        # continue to load without errors.
        known = {k: v for k, v in data.items() if k in _KNOWN_SECTIONS}

        # Migrate old pipeline flags from config files written before the
        # current schema.  The old keys are stripped before constructing
        # PipelineConfig so they never surface as dataclass fields.
        raw_pipeline: dict = dict(known.get("pipeline", {}))
        if "skip_synthesis" not in raw_pipeline:
            for _old in ("skip_unit3", "skip_unit1", "skip_unit2"):
                if raw_pipeline.get(_old):
                    raw_pipeline["skip_synthesis"] = True
                    break
        for _old in ("skip_unit3", "skip_unit1", "skip_unit2"):
            raw_pipeline.pop(_old, None)

        config = cls(
            llm=LLMConfig(**known.get("llm", {})),
            paths=PathsConfig(**known.get("paths", {})),
            checker=CheckerConfig(**known.get("checker", {})),
            error_handling=ErrorHandlingConfig(**known.get("error_handling", {})),
            pipeline=PipelineConfig(**raw_pipeline),
            logging=LoggingConfig(**known.get("logging", {})),
        )

        config_dir = os.path.dirname(os.path.abspath(config_path))
        config.paths.resolve(config_dir)

        return config

    @staticmethod
    def _resolve_env_vars(data: Dict[str, Any]) -> Dict[str, Any]:
        """Recursively resolve ``${ENV_VAR}`` patterns in config values."""
        if isinstance(data, dict):
            return {k: Config._resolve_env_vars(v) for k, v in data.items()}
        elif isinstance(data, list):
            return [Config._resolve_env_vars(item) for item in data]
        elif isinstance(data, str) and data.startswith("${") and data.endswith("}"):
            env_var = data[2:-1]
            return os.environ.get(env_var, data)
        else:
            return data

    def save(self, config_path: str):
        """Save configuration to a YAML file."""
        data = {
            "llm": self.llm.__dict__,
            "paths": self.paths.__dict__,
            "checker": self.checker.__dict__,
            "error_handling": self.error_handling.__dict__,
            "concurrency": self.concurrency.__dict__,
            "pipeline": self.pipeline.__dict__,
            "logging": self.logging.__dict__,
        }
        with open(config_path, "w", encoding="utf-8") as f:
            yaml.dump(data, f, default_flow_style=False)
