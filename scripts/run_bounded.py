#!/usr/bin/env python3
"""run_bounded.py - run any command under a hard wall-clock timeout with a process-tree kill.

Cross-platform bounded runner for trik-gamepad tooling (gradle, adb, npx, ...).
The critical behavior is the TREE kill on timeout: killing only the direct
process leaves children (cmd wrappers, gradle daemons, adb clients) alive and
holding the inherited output pipe, so the CALLER blocks forever even though the
tool "timed out" (hit 2026-08-13: an `adb install` during an emulator offline
blip hung the caller for ~15h). See AGENTS.md "Operational rules".

Usage:
  uv run python scripts/run_bounded.py --timeout 600 --label assembleDebug --log .tmp/x.log -- gradlew.bat --no-daemon assembleDebug
  uv run python scripts/run_bounded.py --timeout 120 -- adb -s emulator-5556 install -r app.apk

Exit code: the wrapped command's exit code, or 124 on timeout. On timeout the
log (or stdout) gets a "TIMEOUT after Ns" marker line.
"""

from __future__ import annotations

import argparse
import os
import signal
import subprocess
import sys


def kill_tree(proc: subprocess.Popen) -> None:
    """Kill the whole process tree so no child can keep a borrowed handle open."""
    if os.name == "nt":
        subprocess.run(
            ["taskkill", "/PID", str(proc.pid), "/T", "/F"],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
    else:
        try:
            os.killpg(os.getpgid(proc.pid), signal.SIGKILL)
        except (ProcessLookupError, PermissionError):
            pass


def run(timeout: float, label: str, cmd: list[str], log: str | None) -> int:
    kwargs: dict = {}
    if os.name != "nt":
        # POSIX: put the child in its own process group so killpg can reap the tree.
        kwargs["start_new_session"] = True

    if log is not None:
        f = open(log, "a", encoding="utf-8")
        sink: object = f
        close_sink = f.close
    else:
        sink = sys.stdout
        close_sink = lambda: None  # noqa: E731

    proc = subprocess.Popen(cmd, stdout=sink, stderr=subprocess.STDOUT, **kwargs)
    try:
        proc.communicate(timeout=timeout)
    except subprocess.TimeoutExpired:
        marker = f"TIMEOUT after {timeout:g}s ({label})\n"
        try:
            sys.stderr.write(marker)
        except Exception:
            pass
        if log is not None:
            try:
                with open(log, "a", encoding="utf-8") as f:
                    f.write(marker)
            except Exception:
                pass
        kill_tree(proc)
        # Reap so we do not leak a zombie; the tree is dead, so this returns fast.
        try:
            proc.communicate(timeout=5)
        except subprocess.TimeoutExpired:
            pass
        close_sink()
        return 124
    close_sink()
    return proc.returncode


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--timeout", type=float, required=True, help="hard wall-clock timeout in seconds")
    parser.add_argument("--label", default="", help="human label used in the TIMEOUT marker")
    parser.add_argument("--log", default=None, help="append output to this file instead of stdout")
    args, rest = parser.parse_known_args()
    if not rest:
        parser.error("no command given (separate with -- before the command)")
    if rest[0] == "--":
        rest = rest[1:]
    return run(args.timeout, args.label, rest, args.log)


if __name__ == "__main__":
    sys.exit(main())
