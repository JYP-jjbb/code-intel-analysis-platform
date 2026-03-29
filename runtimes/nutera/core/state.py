"""
Checkpoint management for the NuTERA workflow.

Checkpoints are written as pickle files so that an interrupted run can be
resumed without reprocessing programs that already completed.

Checkpoint naming convention:
    after_queue_build_<timestamp>.pkl  — program queue loaded from CSV
    after_synthesis_<timestamp>.pkl    — synthesis and checking complete
"""

import os
import pickle
from datetime import datetime
from typing import Optional
from .models import PipelineState
from .config import Config


class StateManager:
    """Manages pipeline state for checkpointing and resumption."""
    
    def __init__(self, config: Config):
        self.config = config
        self.checkpoint_dir = os.path.join(
            config.paths.results_dir,
            "checkpoints"
        )
        os.makedirs(self.checkpoint_dir, exist_ok=True)
    
    def save_checkpoint(
        self,
        state: PipelineState,
        checkpoint_name: str,
    ) -> str:
        """
        Persist workflow state to a timestamped checkpoint file.

        Args:
            state:           Current :class:`~core.models.PipelineState`.
            checkpoint_name: Logical name, e.g. ``"after_synthesis"``.

        Returns:
            Absolute path of the written checkpoint file.
        """
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        filename = f"{checkpoint_name}_{timestamp}.pkl"
        filepath = os.path.join(self.checkpoint_dir, filename)
        
        with open(filepath, 'wb') as f:
            pickle.dump(state, f)
        
        return filepath
    
    def load_checkpoint(self, checkpoint_path: str) -> PipelineState:
        """
        Load pipeline state from a checkpoint file.

        Legacy checkpoints written before the current schema may carry old
        field names.  These are migrated to the current naming on load so
        that the rest of the workflow never sees the old names.

        Args:
            checkpoint_path: Path to checkpoint file.

        Returns:
            PipelineState object with current-schema fields populated.
        """
        with open(checkpoint_path, 'rb') as f:
            state = pickle.load(f)

        # Migrate old completion flags to current names.
        # unit3_completed → synthesis_completed
        if getattr(state, "unit3_completed", False) and not getattr(state, "synthesis_completed", False):
            state.synthesis_completed = True
        # Drop legacy unit fields if still present (older pickle schemas).
        for _old in ("unit1_completed", "unit2_completed", "unit3_completed", "unit1_failed"):
            try:
                delattr(state, _old)
            except AttributeError:
                pass

        return state
    
    def list_checkpoints(self) -> list:
        """
        List all available checkpoints.
        
        Returns:
            List of checkpoint file paths
        """
        checkpoints = []
        for filename in os.listdir(self.checkpoint_dir):
            if filename.endswith('.pkl'):
                filepath = os.path.join(self.checkpoint_dir, filename)
                checkpoints.append(filepath)
        
        return sorted(checkpoints, key=lambda x: os.path.getmtime(x), reverse=True)
