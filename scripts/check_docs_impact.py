#!/usr/bin/env python3
"""Require both README and guide updates for every codebase change in a PR."""

import argparse
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
PROSE_SUFFIXES = {".md", ".rst", ".adoc"}
MEDIA_SUFFIXES = {
    ".png", ".jpg", ".jpeg", ".webp", ".gif", ".svg", ".ico",
    ".mp4", ".mov", ".webm", ".mp3", ".wav", ".ogg", ".pdf",
    ".ttf", ".otf", ".woff", ".woff2",
}


def needs_docs(path):
    """Exempt prose/media; include new languages, tests and configuration by default."""
    if not path:
        return False
    file = PurePosixPath(path)
    if file.suffix.lower() in PROSE_SUFFIXES | MEDIA_SUFFIXES:
        return False
    if file.name in {"LICENSE", "NOTICE"}:
        return False
    if path.startswith("docs/") and file.suffix.lower() == ".txt":
        return False
    return True


def evaluate(changed, deleted=()):
    affected = sorted(path for path in changed if needs_docs(path))
    if not affected:
        return True, "Only prose or media changed; companion documentation edits are not required."

    # Deleting a guide or README is not a documentation update.
    updated = set(changed) - set(deleted)
    missing = []
    if "README.md" not in updated:
        missing.append("README.md")
    if not any(path.startswith("docs/") and path.endswith(".md") for path in updated):
        missing.append("an affected Markdown guide in docs/")
    if not missing:
        return True, "Both README and guides were updated alongside the code; review their accuracy."
    return False, (
        "Codebase changes require both README and guide updates in the same PR:\n"
        + "\n".join(f"  {path}" for path in affected)
        + "\nMissing: " + " and ".join(missing)
        + ". Document the relevant behavior, architecture, setup or validation changes. "
        "PR-body rationales do not bypass this check. See AGENTS.md."
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", required=True, help="Trusted base commit SHA")
    args = parser.parse_args()
    # A commit SHA, not a user-controlled revision expression or git option.
    if not re.fullmatch(r"[0-9a-fA-F]{40,64}", args.base):
        parser.error("--base must be a full commit SHA")
    entries = subprocess.check_output(
        ["git", "diff", "--name-status", "-z", "--no-renames", args.base, "HEAD", "--"],
        cwd=ROOT,
    ).decode().rstrip("\0").split("\0")
    changes = list(zip(entries[0::2], entries[1::2]))
    passed, message = evaluate(
        [path for _, path in changes],
        deleted=[path for status, path in changes if status == "D"],
    )
    print(message, file=sys.stdout if passed else sys.stderr)
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(main())
