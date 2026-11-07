"""Allocate a release version from repository tags without changing source files."""

import os
import re
import subprocess
from pathlib import Path


STABLE_TAG = re.compile(r"v?(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)")
RELEASE_TYPES = {"prerelease", "patch", "minor", "major"}


def select_version(tags, release_type, ref, run_number, run_attempt):
    if release_type not in RELEASE_TYPES:
        raise ValueError(f"Unknown release type: {release_type}")
    if not ref.startswith("refs/heads/"):
        raise ValueError("Select a source branch, not a tag.")
    if release_type != "prerelease" and ref != "refs/heads/main":
        raise ValueError("Stable releases must run from main; use prerelease for other branches.")

    # Accept both the existing 0.1.x tags and new v0.1.x tags. Betas never advance stable.
    stable = [
        (tuple(map(int, match.groups())), tag)
        for tag in tags
        if (match := STABLE_TAG.fullmatch(tag))
    ]
    if not stable:
        raise ValueError("No stable release tag found; fetch the complete repository tag history.")
    (major, minor, patch), previous_tag = max(stable)
    if release_type == "major":
        major, minor, patch = major + 1, 0, 0
    elif release_type == "minor":
        minor, patch = minor + 1, 0
    else:
        patch += 1
    version = f"{major}.{minor}.{patch}"
    if release_type == "prerelease":
        for value in (run_number, run_attempt):
            if not re.fullmatch(r"[1-9][0-9]*", str(value)):
                raise ValueError("Prereleases require positive GitHub run and attempt numbers.")
        version += f"-beta.{run_number}.{run_attempt}"
    if version in tags or f"v{version}" in tags:
        raise ValueError(f"Version {version} is already tagged; refusing to republish it.")
    return {
        "version": version,
        "tag": f"v{version}",
        "previous_tag": previous_tag,
        "prerelease": str(release_type == "prerelease").lower(),
    }


def main():
    tags = subprocess.check_output(["git", "tag", "--list"], text=True).splitlines()
    try:
        metadata = select_version(
            tags, os.environ["RELEASE_TYPE"], os.environ["GITHUB_REF"],
            os.environ["GITHUB_RUN_NUMBER"], os.environ["GITHUB_RUN_ATTEMPT"],
        )
    except (KeyError, ValueError) as error:
        raise SystemExit(str(error)) from error
    output = "".join(f"{key}={value}\n" for key, value in metadata.items())
    print(output, end="")
    if output_path := os.environ.get("GITHUB_OUTPUT"):
        with Path(output_path).open("a") as stream:
            stream.write(output)


if __name__ == "__main__":
    main()
