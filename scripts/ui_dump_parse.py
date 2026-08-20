#!/usr/bin/env python3
r"""ui_dump_parse.py - print uiautomator dump nodes as readable attribute rows.

uiautomator dumps are one giant XML line; this prints the interesting views as
one row each (resource-id, class, text, content-desc, bounds) so the tree is
readable and the tap coordinates (bounds centre) can be derived without a
browser (AGENTS.md "Tap by the uiautomator bounds center"). Filters by a
substring against resource-id / class / content-desc.

Usage (from repo root, uv venv):
  uv run python scripts/ui_dump_parse.py .tmp/ui.xml [--filter pad]
Exit code 0 on success; 1 when the file is unreadable or not a dump.
"""

from __future__ import annotations

import argparse
import re
import sys

NODE_RE = re.compile(r"<node ([^>]+)/?>")


def attr(node: str, name: str) -> str:
    m = re.search(name + r'="([^"]*)"', node)
    return m.group(1) if m else ""


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Print uiautomator dump views as readable rows."
    )
    parser.add_argument("file", help="path to a uiautomator dump XML")
    parser.add_argument("--filter", default="", help="substring filter on id/class/desc/text")
    args = parser.parse_args()

    try:
        with open(args.file, encoding="utf-8") as f:
            text = f.read()
    except OSError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1

    found = 0
    for m in NODE_RE.finditer(text):
        node = m.group(1)
        rid, cls, desc, txt, bounds = (
            attr(node, "resource-id"),
            attr(node, "class"),
            attr(node, "content-desc"),
            attr(node, "text"),
            attr(node, "bounds"),
        )
        haystack = " ".join([rid, cls, desc, txt])
        if args.filter and args.filter not in haystack:
            continue
        if not (rid or cls or desc or txt):
            continue
        found += 1
        print(f"id={rid or '-'} class={cls or '-'} desc={desc or '-'} "
              f"text={txt or '-'} bounds={bounds or '-'}")
    if found == 0:
        print(f"no matching nodes in {args.file}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
