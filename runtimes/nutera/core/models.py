"""
Core data models for the NuTERA workflow.
"""

from dataclasses import dataclass, field
from typing import Optional, List, Dict, Any
from datetime import datetime


@dataclass
class Program:
    """Benchmark program record used by the synthesis and checking stages."""

    # Identity
    name: str           # Program name (typically the Java class name)
    jar_path: str       # Absolute path to the compiled JAR
    class_name: str     # Java class name
    function_name: str  # Target method (typically "loop")

    # Status
    llm_result: Optional[str] = None     # "Yes" / "No" / None
    final_status: Optional[str] = None   # "Verified OK" / "ReLU Limit" / "LLM MaxFail"
    error_summary: str = ""

    # Source paths (optional)
    java_source_path: Optional[str] = None
    c_source_path: Optional[str] = None

    def to_dict(self) -> Dict[str, Any]:
        """Export to a flat dictionary for CSV writing."""
        return {
            "name":          self.name,
            "jar_path":      self.jar_path,
            "class_name":    self.class_name,
            "function_name": self.function_name,
            "synthesis_result": self.llm_result or "",
            "final_status":  self.final_status or "",
            "error_summary": self.error_summary,
        }

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> "Program":
        """Reconstruct a Program from a dictionary (e.g. from CSV)."""
        return cls(
            name=data.get("name", data.get("程序名称", "")),
            jar_path=data.get("jar_path", data.get("JAR路径", "")),
            class_name=data.get("class_name", data.get("程序名称", "")),
            function_name=data.get("function_name", "loop"),
            llm_result=data.get("synthesis_result", data.get("LLM最终结果")),
            final_status=data.get("final_status", data.get("最终状态")),
            error_summary=data.get("error_summary", data.get("备注", "")),
        )


@dataclass
class CheckResult:
    """Result from a checker execution (rf_check.py)."""
    
    status: str                         # "YES" / "NO" / "ERROR"
    counterexample: Optional[str]       # JSON string of counterexample
    raw_output: str                     # Full stdout
    exit_code: int                      # Process exit code
    execution_time: float               # Execution time in seconds
    checkVec_info: Optional[str]        # checkVec related information


@dataclass
class LLMAttempt:
    """Record of a single LLM attempt for RF generation."""
    
    # Context
    program_name: str
    attempt_number: int                 # 1-10
    provider: str                       # "openai" / "google" / "deepseek"
    model: str                          # Model name
    
    # Timing
    start_time: datetime
    end_time: Optional[datetime] = None
    duration_seconds: float = 0.0
    
    # Input/Output
    prompt_summary: str = ""            # Key info from prompt
    llm_output: str = ""                # Raw LLM output
    parsed_rf: Optional[str] = None     # Parsed RF expression
    
    # SMT check result
    check_status: Optional[str] = None  # "YES" / "NO" / "ERROR" / "PARSE_FAILED" / "NO_RELU_SOLUTION"
    counterexample: Optional[str] = None
    
    # Cost
    tokens_used: Optional[int] = None
    
    def to_dict(self) -> Dict[str, Any]:
        """Convert to dictionary for CSV export."""
        return {
            "program_name": self.program_name,
            "attempt_number": self.attempt_number,
            "provider": self.provider,
            "model": self.model,
            "start_time": self.start_time.isoformat(),
            "end_time": self.end_time.isoformat() if self.end_time else "",
            "duration_seconds": self.duration_seconds,
            "prompt_summary": self.prompt_summary,
            "llm_output": self.llm_output[:500],  # Truncate
            "parsed_rf": self.parsed_rf or "",
            "check_status": self.check_status or "",
            "counterexample": self.counterexample or "",
            "tokens_used": self.tokens_used or ""
        }


@dataclass
class LLMResponse:
    """Unified format for LLM responses."""
    
    content: str
    usage: Dict[str, Optional[int]]
    finish_reason: str
    raw_response: Optional[Any] = None


@dataclass
class PipelineState:
    """
    Runtime state of the NuTERA workflow.

    Persisted as pickle checkpoints so that interrupted runs can resume
    without reprocessing already-completed programs.
    """

    # --- Workflow completion flags ---
    queue_built: bool = False
    synthesis_completed: bool = False

    # --- Program data ---
    programs_dict: Dict[str, Program] = field(default_factory=dict)
    llm_queue: List[Program] = field(default_factory=list)

    # --- Metadata ---
    timestamp: datetime = field(default_factory=datetime.now)
    config_snapshot: Optional[Dict[str, Any]] = None

    # Retained for pickle compatibility with checkpoints written before the
    # current schema.  Not used by the workflow; normalised on load via
    # StateManager.load_checkpoint().
    programs: List[Program] = field(default_factory=list)
