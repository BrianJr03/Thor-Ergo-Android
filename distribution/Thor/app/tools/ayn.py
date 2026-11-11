#!/usr/bin/env python3
"""Run the optional non-GUI button helper without changing global Python paths."""
from pathlib import Path
import runpy
import sys
sys.path.insert(0,str(Path(__file__).resolve().parent.parent/'src'))
runpy.run_module('armada_dual_screen_input.ergo_ayn',run_name='__main__')
