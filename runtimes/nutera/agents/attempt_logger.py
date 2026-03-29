"""
Attempt-level logger for LLM attempts.
"""

import os
import csv
from datetime import datetime
from typing import List

from core import Config, LLMAttempt


class AttemptLogger:
    """Logger for LLM attempt records."""
    
    def __init__(self, config: Config):
        self.config = config
        self.csv_path = os.path.join(
            config.paths.results_dir,
            "attempt_level",
            f"attempts_{datetime.now().strftime('%Y%m%d_%H%M%S')}.csv"
        )
        
        # Ensure directory exists
        os.makedirs(os.path.dirname(self.csv_path), exist_ok=True)
        
        # Initialize CSV file
        self._init_csv()

    def _init_csv(self):
        """Initialize CSV file with header."""
        fieldnames = [
            "program_name", "attempt_number", "provider", "model",
            "start_time", "end_time", "duration_seconds",
            "prompt_summary", "llm_output", "parsed_rf",
            "check_status", "counterexample", "tokens_used",
            "empty_output_reason",  # ← 新增：必须放最后
        ]

        with open(self.csv_path, 'w', newline='', encoding='utf-8') as f:
            writer = csv.DictWriter(f, fieldnames=fieldnames)
            writer.writeheader()

    def log(self, attempt: LLMAttempt):
        fieldnames = [
            "program_name", "attempt_number", "provider", "model",
            "start_time", "end_time", "duration_seconds",
            "prompt_summary", "llm_output", "parsed_rf",
            "check_status", "counterexample", "tokens_used",
            "empty_output_reason",  # ← 新增：必须放最后
        ]

        # 先拿到原始字典
        row = attempt.to_dict()

        # 只在 EMPTY_OUTPUT 时写原因，其它情况强制空串
        if row.get("check_status") == "EMPTY_OUTPUT":
            row["empty_output_reason"] = getattr(attempt, "empty_output_reason", "") \
                                         or getattr(attempt, "smt_output", "")
        else:
            row["empty_output_reason"] = ""

        # 确保没有缺 key（避免 DictWriter 报错/缺列）
        for k in fieldnames:
            row.setdefault(k, "")

        with open(self.csv_path, 'a', newline='', encoding='utf-8') as f:
            writer = csv.DictWriter(f, fieldnames=fieldnames)
            writer.writerow(row)

    def log_batch(self, attempts: List[LLMAttempt]):
        """
        Log multiple LLM attempts.

        Args:
            attempts: List of LLMAttempt objects
        """
        for attempt in attempts:
            self.log(attempt)
