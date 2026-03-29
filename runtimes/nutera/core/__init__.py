"""
NuTERA core — data models, configuration, state management, and logging.

Primary exports
---------------
Program, CheckResult, LLMAttempt, LLMResponse, PipelineState  — data models
Config, LLMConfig, PathsConfig, CheckerConfig, PipelineConfig  — configuration
Logger, StateManager                                            — utilities
"""

from .models import (
    Program,
    CheckResult,
    LLMAttempt,
    LLMResponse,
    PipelineState,
)

from .config import (
    Config,
    LLMConfig,
    PathsConfig,
    CheckerConfig,
    ErrorHandlingConfig,
    PipelineConfig,
    LoggingConfig,
)

from .logger import Logger
from .state import StateManager

__all__ = [
    # Data models
    "Program",
    "CheckResult",
    "LLMAttempt",
    "LLMResponse",
    "PipelineState",
    # Configuration
    "Config",
    "LLMConfig",
    "PathsConfig",
    "CheckerConfig",
    "ErrorHandlingConfig",
    "PipelineConfig",
    "LoggingConfig",
    # Utilities
    "Logger",
    "StateManager",
]
