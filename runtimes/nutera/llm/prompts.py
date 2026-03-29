"""
Prompt template management.
"""

import os
from typing import Dict


class PromptManager:
    """Manages prompt templates."""
    
    def __init__(self, prompts_dir: str):
        """
        Initialize prompt manager.
        
        Args:
            prompts_dir: Directory containing prompt template files
        """
        self.prompts_dir = prompts_dir
        self._cache: Dict[str, str] = {}
    
    def get_c_to_java_system_prompt(self) -> str:
        """Get system prompt for C→Java translation."""
        return self._load_prompt("c_to_java.txt")

    def get_rf_generation_system_prompt(self, ablation_level: int = 3) -> str:
        """
        Get system prompt for RF generation by ablation level.

        0 -> rf_generation_l0.txt
        1 -> rf_generation_l1.txt
        2 -> rf_generation_l2.txt
        3 -> rf_generation_l3.txt

        Fallback:
          - if rf_generation_l{level}.txt missing -> fallback to rf_generation_l3.txt
          - else fallback to legacy rf_generation.txt
        """
        try:
            lvl = int(ablation_level)
        except Exception:
            lvl = 3

        if lvl < 0:
            lvl = 0
        if lvl > 3:
            lvl = 3

        filename = f"rf_generation_l{lvl}.txt"
        filepath = os.path.join(self.prompts_dir, filename)

        if not os.path.exists(filepath):
            fallback_l3 = os.path.join(self.prompts_dir, "rf_generation_l3.txt")
            if os.path.exists(fallback_l3):
                filename = "rf_generation_l3.txt"
            else:
                filename = "rf_generation.txt"

        return self._load_prompt(filename)

    def _load_prompt(self, filename: str) -> str:
        """Load prompt from file with caching."""
        if filename in self._cache:
            return self._cache[filename]
        
        filepath = os.path.join(self.prompts_dir, filename)
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()
        
        self._cache[filename] = content
        return content
