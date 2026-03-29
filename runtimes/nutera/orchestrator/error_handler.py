"""
Error handling and recovery for the NuTERA workflow.
"""

import os
import traceback
from datetime import datetime

from core import Config, Logger, Program


class ErrorHandler:
    """Unified error handling and recovery."""

    def __init__(self, config: Config, logger: Logger):
        self.config = config
        self.logger = logger

    def handle_stage_error(self, stage_name: str, error: Exception):
        """
        Handle a stage-level error.

        Logs the error, writes a timestamped error log, and either re-raises
        (when ``fail_fast`` is enabled) or continues.

        Args:
            stage_name: Name of the workflow stage (e.g., "synthesis", "checker").
            error:      The exception that was raised.
        """
        self.logger.error(f"Error in stage '{stage_name}': {error}")
        self.logger.error(traceback.format_exc())

        error_log_path = os.path.join(
            self.config.paths.logs_dir,
            f"error_{stage_name}_{datetime.now().strftime('%Y%m%d_%H%M%S')}.log",
        )

        os.makedirs(os.path.dirname(error_log_path), exist_ok=True)

        with open(error_log_path, "w", encoding="utf-8") as fh:
            fh.write(f"Stage: {stage_name}\n")
            fh.write(f"Time:  {datetime.now()}\n")
            fh.write(f"Error: {error}\n\n")
            fh.write(traceback.format_exc())

        self.logger.info(f"Error log saved to: {error_log_path}")

        if self.config.error_handling.fail_fast:
            self.logger.error("fail_fast is enabled — stopping workflow")
            raise error
        else:
            self.logger.warning(f"Continuing despite error in stage '{stage_name}'")

    def handle_unit_error(self, unit_name: str, error: Exception):
        """Internal compatibility shim for historical configs/checkpoints; delegates to handle_stage_error."""
        self.handle_stage_error(unit_name, error)

    def handle_program_error(self, program: Program, stage: str, error: Exception):
        """
        Record and log a per-program error without stopping the workflow.

        Args:
            program: The program record that failed.
            stage:   Stage name (e.g., "synthesis").
            error:   The exception that was raised.
        """
        program.error_summary = f"{stage}: {error}"
        self.logger.warning(f"Error for {program.name} at '{stage}': {error}")
