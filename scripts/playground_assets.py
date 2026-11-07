"""Validate and fingerprint a complete ShaderLab browser distribution."""

import hashlib
from html.parser import HTMLParser
import json
from pathlib import Path, PurePosixPath

MANIFEST = "playground-manifest.json"
SAMPLE_IMAGES = {"sample_abstract.xml", "sample_landscape.xml", "sample_nature.xml", "sample_portrait.xml"}


class Scripts(HTMLParser):
    def __init__(self):
        super().__init__()
        self.sources = []

    def handle_starttag(self, tag, attrs):
        if tag == "script" and dict(attrs).get("src"):
            self.sources.append(dict(attrs)["src"])


def fingerprint(directory):
    return {
        file.relative_to(directory).as_posix(): {
            "bytes": file.stat().st_size,
            "sha256": hashlib.sha256(file.read_bytes()).hexdigest(),
        }
        for file in sorted(directory.rglob("*"))
        if file.is_file() and file.name != MANIFEST
    }


def validate_distribution(directory):
    directory = Path(directory)
    errors = []
    for name in ("index.html", "composeApp.js"):
        file = directory / name
        if not file.is_file() or file.stat().st_size == 0:
            errors.append(f"Missing or empty playground asset: {name}")
    if (directory / "index.html").is_file():
        scripts = Scripts()
        scripts.feed((directory / "index.html").read_text(encoding="utf-8"))
        if "composeApp.js" not in scripts.sources:
            errors.append("Playground HTML must load composeApp.js relative to its own directory")

    wasm_files = list(directory.rglob("*.wasm"))
    if len(wasm_files) < 2:
        errors.append("Playground requires both the Kotlin app and Skia WebAssembly binaries")
    for file in wasm_files:
        with file.open("rb") as stream:
            if stream.read(8) != b"\0asm\x01\0\0\0":
                errors.append(f"Invalid WebAssembly binary: {file.relative_to(directory)}")
    resources = directory / "composeResources"
    image_names = {file.name for file in resources.rglob("*.xml") if file.stat().st_size > 0}
    for name in sorted(SAMPLE_IMAGES - image_names):
        errors.append(f"Missing playground Compose resource: {name}")
    if any(file.is_symlink() for file in directory.rglob("*")):
        errors.append("Playground distribution must contain files, not symbolic links")
    return errors


def validate_package(directory):
    directory = Path(directory)
    errors = validate_distribution(directory)
    try:
        manifest = json.loads((directory / MANIFEST).read_text(encoding="utf-8"))
        files = manifest["files"]
        if manifest.get("version") != 1 or not isinstance(files, dict):
            raise ValueError("unsupported manifest format")
        for name in files:
            path = PurePosixPath(name)
            if path.is_absolute() or ".." in path.parts or "\\" in name:
                raise ValueError(f"unsafe asset path: {name}")
        actual = fingerprint(directory)
        for name in sorted(files.keys() - actual.keys()):
            errors.append(f"Packaged playground asset is missing: {name}")
        for name in sorted(actual.keys() - files.keys()):
            errors.append(f"Unrecorded playground asset: {name}")
        for name in sorted(files.keys() & actual.keys()):
            if files[name] != actual[name]:
                errors.append(f"Packaged playground asset changed: {name}")
    except (OSError, ValueError, KeyError, TypeError) as error:
        errors.append(f"Invalid playground manifest: {error}. Run scripts/package_playground.py after the Wasm build.")
    return errors
