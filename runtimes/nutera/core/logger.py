"""
Unified logging system for the Termination Proof Agent.
"""

import logging
import os
from typing import Optional
from datetime import datetime
from .config import LoggingConfig


class Logger:
    """Unified logging system with multiple logger instances."""
    
    def __init__(self, config: LoggingConfig):
        self.config = config
        self._ensure_log_dirs()
        self._setup_loggers()
    
    def _ensure_log_dirs(self):
        """Ensure log directories exist."""
        log_dir = os.path.dirname(self.config.file)
        if log_dir:
            os.makedirs(log_dir, exist_ok=True)
    
    def _setup_loggers(self):
        """Setup multiple logger instances."""
        # Main pipeline logger
        self.main_logger = logging.getLogger("pipeline")
        self.main_logger.setLevel(getattr(logging, self.config.level))
        self.main_logger.handlers.clear()
        
        # File handler
        fh = logging.FileHandler(self.config.file, encoding='utf-8')
        fh.setFormatter(logging.Formatter(self.config.format))
        self.main_logger.addHandler(fh)
        
        # Console handler
        ch = logging.StreamHandler()
        ch.setFormatter(logging.Formatter(self.config.format))
        self.main_logger.addHandler(ch)
        
        # Program-level logger
        log_dir = os.path.dirname(self.config.file)
        self.program_logger = logging.getLogger("program")
        self.program_logger.setLevel(logging.INFO)
        self.program_logger.handlers.clear()
        prog_fh = logging.FileHandler(
            os.path.join(log_dir, "program_level.log"),
            encoding='utf-8'
        )
        prog_fh.setFormatter(logging.Formatter(self.config.format))
        self.program_logger.addHandler(prog_fh)
        
        # Attempt-level logger
        self.attempt_logger = logging.getLogger("attempt")
        self.attempt_logger.setLevel(logging.INFO)
        self.attempt_logger.handlers.clear()
        att_fh = logging.FileHandler(
            os.path.join(log_dir, "attempt_level.log"),
            encoding='utf-8'
        )
        att_fh.setFormatter(logging.Formatter(self.config.format))
        self.attempt_logger.addHandler(att_fh)
    
    def info(self, msg: str):
        """Log info message to main logger."""
        self.main_logger.info(msg)
    
    def warning(self, msg: str):
        """Log warning message to main logger."""
        self.main_logger.warning(msg)
    
    def error(self, msg: str):
        """Log error message to main logger."""
        self.main_logger.error(msg)
    
    def debug(self, msg: str):
        """Log debug message to main logger."""
        self.main_logger.debug(msg)
    
    def log_program(self, msg: str):
        """Log to program-level logger."""
        self.program_logger.info(msg)
    
    def log_attempt(self, msg: str):
        """Log to attempt-level logger."""
        self.attempt_logger.info(msg)
