#!/usr/bin/env python3
"""gate.py - canonical local quality gate for trik-gamepad.

Cross-platform replacement for the old scripts/gate.ps1.

TWO phases, deliberately (AGENTS.md "Format before you gate"):
  1. spotlessApply first - rewrites .kt files (ktfmt).
  2. the full gate - so spotlessCheck never fails on formatting and the
     formatter never races the test task's Kotlin compilation when
     org.gradle.parallel=true (one invocation lets spotlessApply rewrite .kt
     while `test` compiles the same files).

Usage (from repo root):  uv run python scripts/gate.py
Logs every command to .tmp/gate.log.

Exit code is non-zero if any step fails.
"""

from __future__ import annotations

import csv
import os
import shutil
import subprocess
import sys

from _gradle import call_gradle

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LOG_DIR = os.path.join(ROOT, ".tmp")
LOG = os.path.join(LOG_DIR, "gate.log")

# A0 baseline for the logical-SLOC token trend (see TESTING.md
# "Test quality discipline"). Reported as a trend, not a gate.
A0_BASELINE = 12659

GRADLE_STEPS = [
    ("spotlessApply", ["spotlessApply"]),
    ("test", ["test"]),
    ("lint", ["lint"]),
    ("detekt", ["detekt"]),
    ("spotbugsDebug", ["spotbugsDebug"]),
    ("jacocoTestReport", ["jacocoTestReport"]),
    ("jacocoTestCoverageVerification", ["jacocoTestCoverageVerification"]),
    ("spotlessCheck", ["spotlessCheck"]),
]


def append_log(text: str) -> None:
    with open(LOG, "a", encoding="utf-8") as f:
        f.write(text + "\n")


def run_jscpd() -> None:
    print("==> jscpd (test duplication gate)")
    npx = shutil.which("npx")
    if npx is None:
        print("FAILED: npx not found on PATH (jscpd gate requires Node.js/npx)")
        sys.exit(1)
    cmd = [npx, "-y", "jscpd", "app/src/test", "app/src/androidTest", "--config", ".jscpd.json"]
    with open(LOG, "a", encoding="utf-8") as f:
        result = subprocess.run(cmd, cwd=ROOT, stdout=f, stderr=subprocess.STDOUT)
    if result.returncode != 0:
        print(f"FAILED: jscpd test duplication gate (see {LOG})")
        sys.exit(result.returncode)


def lizard_executable() -> str | None:
    """Resolve lizard next to the running interpreter (the uv venv), else PATH."""
    bin_dir = os.path.dirname(sys.executable)
    name = "lizard.exe" if os.name == "nt" else "lizard"
    candidate = os.path.join(bin_dir, name)
    if os.path.isfile(candidate):
        return candidate
    return shutil.which("lizard")


def run_lizard_trend() -> None:
    print("==> lizard (test token trend)")
    lizard = lizard_executable()
    if lizard is None:
        warn = "WARN: lizard not installed (uv sync) - token trend skipped"
        print(warn)
        append_log(warn)
        return
    result = subprocess.run(
        [lizard, "-l", "kotlin", "app/src/test", "app/src/androidTest", "--csv"],
        cwd=ROOT,
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        print(f"FAILED: lizard (see {LOG})")
        sys.exit(result.returncode)
    total = 0
    for row in csv.reader(result.stdout.splitlines()):
        if len(row) > 2:
            try:
                total += int(row[2])
            except ValueError:
                pass
    trend = f"test logical SLOC (summed lizard token_count): {total} (A0 baseline {A0_BASELINE})"
    print(trend)
    append_log(trend)


def main() -> None:
    os.makedirs(LOG_DIR, exist_ok=True)
    with open(LOG, "w", encoding="utf-8"):
        pass
    for label, args in GRADLE_STEPS:
        rc = call_gradle(label, *args, log=LOG)
        if rc != 0:
            print(f"FAILED: {label} (see {LOG})")
            sys.exit(rc)
    run_jscpd()
    run_lizard_trend()
    print("GATE PASSED - all steps green. Log: .tmp/gate.log")


if __name__ == "__main__":
    main()
