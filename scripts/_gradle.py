"""Shared cross-platform Gradle wrapper resolution for trik-gamepad scripts.

Picks `gradlew.bat` on Windows and `./gradlew` elsewhere so the Python tooling
(gate.py, spotless_apply.py) runs unchanged on Windows / Linux / macOS.
"""

from __future__ import annotations

import os
import subprocess

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def gradle_cmd() -> str:
    return os.path.join(ROOT, "gradlew.bat") if os.name == "nt" else os.path.join(ROOT, "gradlew")


def call_gradle(label: str, *args: str, log: str | None = None) -> int:
    """Run a Gradle task with `--no-daemon`; append output to `log` when given.

    Returns the process exit code (caller decides the gate verdict).
    """
    print(f"==> {label}")
    cmd = [gradle_cmd(), *args, "--no-daemon"]
    if log is None:
        result = subprocess.run(cmd, cwd=ROOT)
    else:
        with open(log, "a", encoding="utf-8") as f:
            result = subprocess.run(cmd, cwd=ROOT, stdout=f, stderr=subprocess.STDOUT)
    return result.returncode
