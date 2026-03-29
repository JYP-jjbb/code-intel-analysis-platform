"""
Subprocess execution helper utilities.
"""

import subprocess
import os
from typing import List, Optional, Dict


def run(
    cmd: List[str],
    cwd: Optional[str] = None,
    timeout: Optional[int] = None,
    capture_output: bool = True,
    check: bool = False,
    env: Optional[Dict[str, str]] = None
) -> subprocess.CompletedProcess:
    """
    Run a command as subprocess with better error handling.
    
    Args:
        cmd: Command and arguments as list
        cwd: Working directory
        timeout: Timeout in seconds
        capture_output: Whether to capture stdout/stderr
        check: Whether to raise exception on non-zero exit
        env: Environment variables
        
    Returns:
        CompletedProcess object
        
    Raises:
        subprocess.TimeoutExpired: If timeout is exceeded
        subprocess.CalledProcessError: If check=True and exit code is non-zero
    """
    # Merge with current environment if env is provided
    if env is not None:
        full_env = os.environ.copy()
        full_env.update(env)
    else:
        full_env = None
    
    result = subprocess.run(
        cmd,
        cwd=cwd,
        timeout=timeout,
        capture_output=capture_output,
        text=True,
        check=check,
        env=full_env,
    )
    
    return result
