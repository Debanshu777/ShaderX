#!/usr/bin/env python3
"""Require a docs update or an explicit explanation for user-facing PR changes."""

import argparse
import json
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]


def needs_docs(path):
    parts = PurePosixPath(path).parts
    if path.startswith("shaderx/src/") and len(parts) > 3:
        return parts[2].endswith("Main")
    if path.startswith("samples/"):
        return not any(part.endswith("Test") for part in parts) and path.endswith(
            (".kt", ".kts", ".swift", ".xml", ".plist", ".html")
        )
    return (
        path.endswith(".gradle.kts")
        or path in {"gradle/libs.versions.toml", "gradle.properties",
                    "gradle/gradle-daemon-jvm.properties", ".java-version"}
        or path.startswith("gradle/wrapper/")
    )


def evaluate(changed, body=""):
    affected = sorted(path for path in changed if needs_docs(path))
    if not affected:
        return True, "No implementation, sample or toolchain changes require a docs review."
    if any(path.startswith("docs/") and path.endswith(".md")
           or path in {"README.md", "CONTRIBUTING.md"} for path in changed):
        return True, "Documentation was updated alongside the code; review its accuracy."

    # Ignore the example in the PR template. Treat the body as data, never shell code.
    visible = re.sub(r"<!--.*?-->", "", body, flags=re.DOTALL)
    impact = re.search(r"^Docs-Impact:\s*none\s*$", visible, re.MULTILINE | re.IGNORECASE)
    reason = re.search(r"^Docs-Reason:[ \t]*(\S[^\n]*)$", visible, re.MULTILINE | re.IGNORECASE)
    if impact and reason and len(reason.group(1).strip()) >= 20:
        return True, "No-docs rationale supplied; a reviewer must verify it."
    return False, (
        "Implementation, sample or toolchain changes need a documentation review:\n"
        + "\n".join(f"  {path}" for path in affected)
        + "\nUpdate the affected guide in docs/ or the README. For a change with no "
        "documentation impact, add Docs-Impact: none and a specific Docs-Reason: "
        "line (at least 20 characters) to the PR body. See AGENTS.md."
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", required=True, help="Trusted base commit SHA")
    parser.add_argument("--event-file", type=Path, help="GitHub pull_request event JSON")
    args = parser.parse_args()
    # A commit SHA, not a user-controlled revision expression or git option.
    if not re.fullmatch(r"[0-9a-fA-F]{40,64}", args.base):
        parser.error("--base must be a full commit SHA")
    changed = subprocess.check_output(
        ["git", "diff", "--name-only", "-z", args.base, "HEAD", "--"], cwd=ROOT
    ).decode().split("\0")
    body = ""
    if args.event_file:
        body = json.loads(args.event_file.read_text())["pull_request"].get("body") or ""
    passed, message = evaluate(changed, body)
    print(message, file=sys.stdout if passed else sys.stderr)
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(main())
