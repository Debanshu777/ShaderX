"""Validate the configured stable release against Git history and Maven Central.

This command only prints validated coordinates. It never creates a tag or publishes.
"""

import os
import posixpath
import re
import subprocess
import tomllib
import urllib.error
import urllib.request
from urllib.parse import urlsplit, urlunsplit
import xml.etree.ElementTree as ET
from pathlib import Path


STABLE_VERSION = re.compile(r"(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)")
STABLE_TAG = re.compile(r"v?(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)")
CENTRAL_ROOT = "https://repo.maven.apache.org/maven2/io/github/debanshu777/shaderx"
METADATA_URL = f"{CENTRAL_ROOT}/maven-metadata.xml"
ROOT = Path(__file__).resolve().parents[1]


def read_configured_version(path):
    """Read the one literal version property shared with Gradle; reject ambiguity."""
    values = []
    for line in Path(path).read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line.startswith(("#", "!")) or not line:
            continue
        if re.match(r"shaderxVersion(?:\s|[=:]|$)", line):
            match = re.fullmatch(r"shaderxVersion\s*=\s*(\S+)", line)
            if not match:
                raise ValueError("Use one literal shaderxVersion=<stable version> in gradle.properties.")
            values.append(match.group(1))
    if len(values) != 1:
        raise ValueError("gradle.properties must contain exactly one shaderxVersion property.")
    if not STABLE_VERSION.fullmatch(values[0]):
        raise ValueError("shaderxVersion must be a stable SemVer version, without a v prefix or suffix.")
    return values[0]


def changelog_section(source, version):
    """Require curated release notes for exactly the configured stable version."""
    heading = re.compile(r"^## " + re.escape(version) + r"(?:[ \t]+[^\n]*)?$", re.MULTILINE)
    matches = list(heading.finditer(source))
    if len(matches) != 1:
        raise ValueError(f"CHANGELOG.md must contain exactly one release section headed ## {version}.")
    match = matches[0]
    remainder = source[match.end():]
    next_release = re.search(r"^## ", remainder, re.MULTILINE)
    section = remainder[:next_release.start()] if next_release else remainder
    if not section.strip():
        raise ValueError("The configured release needs nonempty curated changelog notes.")
    return section.strip()


def github_release_body(section, repository, source_sha):
    """Make changelog repository links work from a release page at its exact source."""
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository):
        raise ValueError("Invalid GitHub repository for release notes.")
    if not re.fullmatch(r"[0-9a-fA-F]{40}", source_sha):
        raise ValueError("Release notes require the exact Git source SHA.")

    def resolve(match):
        target = urlsplit(match.group("target"))
        if target.scheme or target.netloc or not target.path:
            return match.group(0)
        path = posixpath.normpath(target.path.lstrip("/"))
        if path == ".." or path.startswith("../"):
            raise ValueError("Release note link escapes the repository root.")
        resolved = urlunsplit(("https", "github.com", f"/{repository}/blob/{source_sha}/{path}", target.query, target.fragment))
        return match.group("prefix") + resolved + match.group("suffix")

    return re.sub(r"(?P<prefix>\[[^\]]*\]\()(?P<target>[^)\s]+)(?P<suffix>\))", resolve, section)


def check_release_versions(root):
    """Keep release-facing pins in sync; historical version prose is unrestricted."""
    errors = []
    try:
        version = read_configured_version(root / "gradle.properties")
        sources = {name: (root / name).read_text(encoding="utf-8") for name in (
            "README.md", "docs/getting-started.md", "docs/llms.txt", "CHANGELOG.md",
        )}
    except (OSError, ValueError) as error:
        return [f"Release documentation: {error}"]
    coordinate = re.compile(r"[\"']io\.github\.debanshu777:shaderx:([^\"']+)[\"']")
    for name in ("README.md", "docs/getting-started.md"):
        pins = coordinate.findall(sources[name])
        if not pins:
            errors.append(f"{name}: missing literal ShaderX installation coordinate for {version}")
        for pin in pins:
            if pin != version:
                errors.append(f"{name}: ShaderX installation pin {pin!r} must match shaderxVersion={version}")
    catalog_versions = []
    for block in re.findall(r"^```toml[ \t]*\n(.*?)^```", sources["docs/getting-started.md"], re.MULTILINE | re.DOTALL):
        try:
            catalog = tomllib.loads(block)
        except tomllib.TOMLDecodeError as error:
            errors.append(f"docs/getting-started.md: invalid TOML example: {error}")
            continue
        if "shaderx" in catalog.get("versions", {}):
            catalog_versions.append(catalog["versions"]["shaderx"])
    if not catalog_versions:
        errors.append("docs/getting-started.md: missing ShaderX version catalog entry")
    elif any(pin != version for pin in catalog_versions):
        errors.append(f"docs/getting-started.md: version catalog must match shaderxVersion={version}")
    llms_pins = re.findall(r"io\.github\.debanshu777:shaderx:([^\s`<>]+)", sources["docs/llms.txt"])
    if not llms_pins or any(pin.rstrip(".,;") != version for pin in llms_pins):
        errors.append(f"docs/llms.txt: declared ShaderX release coordinate must match shaderxVersion={version}")
    try:
        changelog_section(sources["CHANGELOG.md"], version)
    except ValueError as error:
        errors.append(str(error))
    return errors



def central_versions(xml_bytes):
    """Verify the identity and stable baseline in Central's metadata."""
    try:
        root = ET.fromstring(xml_bytes)
    except ET.ParseError as error:
        raise ValueError("Maven Central returned invalid metadata; refusing release.") from error
    if root.findtext("groupId") != "io.github.debanshu777" or root.findtext("artifactId") != "shaderx":
        raise ValueError("Maven Central metadata has an unexpected artifact identity.")
    versions = [entry.text for entry in root.findall("versioning/versions/version") if entry.text]
    if not any(STABLE_VERSION.fullmatch(version) for version in versions):
        raise ValueError("Maven Central metadata has no stable version history; refusing release.")
    declared_release = root.findtext("versioning/release")
    if not declared_release or declared_release not in versions:
        raise ValueError("Maven Central release metadata is incomplete; refusing release.")
    return versions


def fetch_central_versions():
    request = urllib.request.Request(METADATA_URL, headers={"User-Agent": "ShaderX-release-validation"})
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            data = response.read(1024 * 1024 + 1)
    except (OSError, urllib.error.URLError) as error:
        raise ValueError("Could not verify Maven Central metadata; refusing release.") from error
    if len(data) > 1024 * 1024:
        raise ValueError("Maven Central metadata exceeds the validation limit; refusing release.")
    return central_versions(data)


def assert_unpublished(version):
    """Check the exact POM as well as metadata, which may lag new publications."""
    url = f"{CENTRAL_ROOT}/{version}/shaderx-{version}.pom"
    request = urllib.request.Request(url, headers={"User-Agent": "ShaderX-release-validation"})
    try:
        with urllib.request.urlopen(request, timeout=20):
            pass
    except urllib.error.HTTPError as error:
        if error.code == 404:
            return
        raise ValueError("Could not establish that the release coordinate is unused; refusing release.") from error
    except (OSError, urllib.error.URLError) as error:
        raise ValueError("Could not establish that the release coordinate is unused; refusing release.") from error
    raise ValueError(f"Version {version} already exists on Maven Central; refusing to republish it.")


def select_version(tags, configured_version, ref, confirmation, published_versions):
    if ref != "refs/heads/main":
        raise ValueError("Stable releases must run from the main source branch, not a feature branch or tag.")
    match = STABLE_VERSION.fullmatch(configured_version)
    if not match:
        raise ValueError("Release version must be a stable SemVer version, without a v prefix or suffix.")
    if confirmation != configured_version:
        raise ValueError("confirmVersion must exactly match shaderxVersion in gradle.properties.")
    requested = tuple(map(int, match.groups()))
    stable_tags = [
        (tuple(map(int, match.groups())), tag)
        for tag in tags
        if (match := STABLE_TAG.fullmatch(tag))
    ]
    if not stable_tags:
        raise ValueError("No stable release tag found; fetch and reconcile repository history before release.")
    if configured_version in tags or f"v{configured_version}" in tags:
        raise ValueError(f"Version {configured_version} is already tagged; refusing to republish it.")
    stable_published = [
        (tuple(map(int, match.groups())), version)
        for version in published_versions
        if (match := STABLE_VERSION.fullmatch(version))
    ]
    if not stable_published:
        raise ValueError("No stable Maven Central baseline was verified; refusing release.")
    latest_published, latest_published_text = max(stable_published)
    if latest_published_text not in tags and f"v{latest_published_text}" not in tags:
        raise ValueError(
            f"Latest Central stable {latest_published_text} has no matching Git tag; "
            "reconcile its actual source commit before release."
        )
    if configured_version in published_versions:
        raise ValueError(f"Version {configured_version} already exists on Maven Central; refusing to republish it.")
    latest_tagged, _ = max(stable_tags)
    if requested <= latest_published or requested <= latest_tagged:
        raise ValueError("Release version must be newer than every stable published or reserved tagged version.")
    # Generated notes start at the published baseline, not a newer reserved/failed release tag.
    previous_tag = (f"v{latest_published_text}" if f"v{latest_published_text}" in tags else latest_published_text)
    return {"version": configured_version, "tag": f"v{configured_version}", "previous_tag": previous_tag}


def main():
    try:
        configured = read_configured_version(ROOT / "gradle.properties")
        documentation_errors = check_release_versions(ROOT)
        if documentation_errors:
            raise ValueError("Release documentation is inconsistent: " + "; ".join(documentation_errors))
        # Reject the source branch/confirmation before any external request.
        ref, confirmation = os.environ["GITHUB_REF"], os.environ["CONFIRM_VERSION"]
        if ref != "refs/heads/main":
            raise ValueError("Stable releases must run from the main source branch.")
        if confirmation != configured:
            raise ValueError("confirmVersion must exactly match shaderxVersion in gradle.properties.")
        tags = subprocess.check_output(["git", "tag", "--list"], cwd=ROOT, text=True).splitlines()
        metadata = select_version(tags, configured, ref, confirmation, fetch_central_versions())
        assert_unpublished(configured)
    except (KeyError, ValueError) as error:
        raise SystemExit(str(error)) from error
    output = "".join(f"{key}={value}\n" for key, value in metadata.items())
    print(output, end="")
    if output_path := os.environ.get("GITHUB_OUTPUT"):
        with Path(output_path).open("a", encoding="utf-8") as stream:
            stream.write(output)


if __name__ == "__main__":
    main()
