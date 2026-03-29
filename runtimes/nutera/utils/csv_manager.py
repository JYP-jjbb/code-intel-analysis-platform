"""
CSV file management utilities.
"""

import csv
from typing import List, Dict, Any, Optional


def read_csv(
    filepath: str,
    encoding: str = 'utf-8'
) -> List[Dict[str, str]]:
    """
    Read CSV file and return list of dictionaries.
    
    Args:
        filepath: Path to CSV file
        encoding: File encoding
        
    Returns:
        List of dictionaries, one per row
    """
    rows = []
    with open(filepath, 'r', encoding=encoding, newline='') as f:
        reader = csv.DictReader(f)
        for row in reader:
            rows.append(row)
    return rows


def write_csv(
    filepath: str,
    rows: List[Dict[str, Any]],
    fieldnames: Optional[List[str]] = None,
    encoding: str = 'utf-8'
):
    """
    Write list of dictionaries to CSV file.
    
    Args:
        filepath: Path to CSV file
        rows: List of dictionaries to write
        fieldnames: Column names (if None, use keys from first row)
        encoding: File encoding
    """
    if not rows:
        return
    
    if fieldnames is None:
        fieldnames = list(rows[0].keys())
    
    with open(filepath, 'w', encoding=encoding, newline='') as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        for row in rows:
            writer.writerow(row)


def append_csv(
    filepath: str,
    rows: List[Dict[str, Any]],
    fieldnames: Optional[List[str]] = None,
    encoding: str = 'utf-8'
):
    """
    Append rows to existing CSV file.
    
    Args:
        filepath: Path to CSV file
        rows: List of dictionaries to append
        fieldnames: Column names (if None, use keys from first row)
        encoding: File encoding
    """
    if not rows:
        return
    
    if fieldnames is None:
        fieldnames = list(rows[0].keys())
    
    with open(filepath, 'a', encoding=encoding, newline='') as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        for row in rows:
            writer.writerow(row)
