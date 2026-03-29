"""
ReLU ranking function parser.
"""

import re
from typing import Optional


class RFParser:
    """Parser for ReLU ranking function expressions."""
    
    def parse(self, llm_output: str) -> Optional[str]:
        """
        Extract ReLU expression from LLM output.
        
        Args:
            llm_output: Complete LLM output text
            
        Returns:
            Extracted ReLU expression string, or None if parsing failed
        """
        # Remove code block markers
        cleaned = self._remove_code_blocks(llm_output)
        
        # Find lines containing ReLU
        lines = cleaned.split("\n")
        for line in lines:
            line = line.strip()
            if "ReLU" in line and self._is_valid_rf_line(line):
                return line
        
        return None
    
    def _remove_code_blocks(self, text: str) -> str:
        """Remove Markdown code block markers."""
        # Remove ```python ... ``` etc.
        text = re.sub(r'```\w*\n', '', text)
        text = text.replace('```', '')
        return text
    
    def _is_valid_rf_line(self, line: str) -> bool:
        """Check if line is a valid RF expression."""
        # Basic check: contains ReLU, doesn't contain forbidden keywords
        forbidden = ["def ", "print", "import", "//", "/*", "class ", "public "]
        if any(kw in line for kw in forbidden):
            return False
        
        # Check for basic RF patterns
        if "ReLU(" in line:
            return True
        
        return False
    
    def validate_syntax(self, rf_expr: str) -> bool:
        """
        Validate ReLU expression syntax (basic check).
        
        Args:
            rf_expr: ReLU expression string
            
        Returns:
            True if syntax appears valid, False otherwise
        """
        # Check for balanced parentheses
        if rf_expr.count('(') != rf_expr.count(')'):
            return False
        
        # Check for balanced brackets (if vector form)
        if rf_expr.count('[') != rf_expr.count(']'):
            return False
        
        # Check that it contains ReLU
        if "ReLU" not in rf_expr:
            return False
        
        # Check for forbidden operations
        forbidden_patterns = [
            r'\*\s*\w+\s*\*',  # Variable multiplication like x*y
            r'/',              # Division
            r'%',              # Modulo
            r'\*\*',           # Power
            r'max\(',          # max function
            r'min\(',          # min function
            r'abs\('           # abs function
        ]
        
        for pattern in forbidden_patterns:
            if re.search(pattern, rf_expr):
                return False
        
        return True
