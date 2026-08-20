#!/usr/bin/env python3
r"""check_device_identifiers.py - reject device identifiers in committed content.

A physical device's serial, model code or IMEI can identify the exact person or
device; the guardrail (AGENTS.md "Repo hygiene", DECISIONS.md "[2026-08-15]" and
"[2026-08-20]") forbids them in repo content, CI logs and PR bodies. This hook
automates the pre-commit sweep (the 2026-08-20 decision's "future candidate"):
it greps every staged file and fails the commit with a report.

Detected shapes (additive - add a shape only when a real identifier uses it):

- IMEI:     15 consecutive digits (\b\d{15}\b).
- Model:    Samsung-style model codes `SM-<letter><digits>` (e.g.
             `SM-XXXXXX`-shaped but with a real letter+digit sequence, not the
             all-X docs placeholder, which is intentionally NOT matched).
- Serial:   Android adb serials in the observed prefix shape (`RFCX`-prefixed,
             11 characters total). Extend the regex when a different real
             serial shape leaks.

Usage (pre-commit):  entry: python scripts/check_device_identifiers.py
                     pass_filenames: true   (scans the staged files)
"""

from __future__ import annotations

import re
import sys

# Matches the whole shape, so a bare placeholder stays safe and partial strings
# (e.g. `RFCX-410-VZ2Z`) are not flagged (a real identifier would be pasted as-is).
IMEI_RE = re.compile(r"\b\d{15}\b")
MODEL_RE = re.compile(r"\bSM-[A-Z][0-9]{3,}\b")
SERIAL_RE = re.compile(r"\bRFCX[0-9A-Z]{7}\b")

PATTERNS = [
    ("IMEI", IMEI_RE),
    ("model code", MODEL_RE),
    ("serial", SERIAL_RE),
]


def main() -> int:
    bad = False
    for path in sys.argv[1:]:
        try:
            with open(path, encoding="utf-8") as f:
                text = f.read()
        except (OSError, UnicodeDecodeError):
            # A non-UTF-8 (binary) file is not a source of identifier text.
            continue
        for label, regex in PATTERNS:
            for match in regex.finditer(text):
                line_no = text.count("\n", 0, match.start()) + 1
                print(f"{path}:{line_no}: {label} '{match.group(0)}'")
                bad = True
    if bad:
        print("Device identifiers are forbidden in repo content (AGENTS.md 'Repo hygiene').")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
