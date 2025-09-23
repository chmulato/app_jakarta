#!/usr/bin/env python
"""Thin wrapper to execute the primary Tomcat automation script (main.py).

Usage examples:
    python main_tom.py 1   # check environment
    python main_tom.py 2   # deploy on Tomcat
    python main_tom.py 8   # full E2E flow
"""

import os
import subprocess
import sys

def main() -> None:
    workspace = os.path.dirname(os.path.abspath(__file__))
    main_script = os.path.join(workspace, "main.py")

    if not os.path.exists(main_script):
        print("main.py not found in current directory.", file=sys.stderr)
        sys.exit(1)

    cmd = [sys.executable, main_script, *sys.argv[1:]]
    result = subprocess.run(cmd)
    sys.exit(result.returncode)

if __name__ == "__main__":
    main()
