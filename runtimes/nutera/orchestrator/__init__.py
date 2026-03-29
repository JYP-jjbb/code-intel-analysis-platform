"""
Orchestrator module for pipeline coordination.
"""

from .pipeline import TerminationProofPipeline
from .error_handler import ErrorHandler

__all__ = [
    'TerminationProofPipeline',
    'ErrorHandler',
]
