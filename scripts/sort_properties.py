#!/usr/bin/env python3
# Copyright The OkapiCore Authors
# SPDX-License-Identifier: Apache-2.0

"""Sort Java .properties entries by key.

The formatter reads from stdin and writes to stdout so Spotless can call it as
a native command formatter.
"""

from __future__ import annotations

import sys
from collections.abc import Sequence


def has_continuation(line: str) -> bool:
    stripped = line.rstrip("\n\r")
    slash_count = 0
    for char in reversed(stripped):
        if char != "\\":
            break
        slash_count += 1
    return slash_count % 2 == 1


def split_entries(lines: Sequence[str]) -> tuple[list[str], list[list[str]]]:
    header: list[str] = []
    entries: list[list[str]] = []
    current: list[str] = []
    in_header = True

    for line in lines:
        stripped = line.strip()
        is_header_line = in_header and (stripped == "" or stripped.startswith(("#", "!")))

        if is_header_line:
            header.append(line)
            continue

        in_header = False

        if not current:
            current = [line]
        else:
            current.append(line)

        if not has_continuation(line):
            entries.append(current)
            current = []

    if current:
        entries.append(current)

    while header and header[-1].strip() == "":
        header.pop()

    return header, entries


def property_key(entry: Sequence[str]) -> str:
    if not entry:
        return ""

    line = entry[0].lstrip()
    if not line or line.startswith(("#", "!")):
        return line

    escaped = False
    for index, char in enumerate(line):
        if escaped:
            escaped = False
            continue
        if char == "\\":
            escaped = True
            continue
        if char in "=:" or char.isspace():
            return line[:index].rstrip()

    return line.rstrip("\n\r")


def sort_properties(content: str) -> str:
    lines = content.splitlines(keepends=True)
    header, entries = split_entries(lines)
    sorted_entries = sorted(entries, key=lambda entry: property_key(entry))

    output: list[str] = []
    output.extend(header)

    if header and sorted_entries:
        output.append("\n")

    for entry in sorted_entries:
        output.extend(entry)

    if output and not output[-1].endswith("\n"):
        output[-1] = output[-1] + "\n"

    return "".join(output)


def main() -> int:
    sys.stdout.write(sort_properties(sys.stdin.read()))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
