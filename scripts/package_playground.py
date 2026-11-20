#!/usr/bin/env python3
"""Bundle ShaderLab and the visual sampler into the built docs site."""

import argparse
import json
from pathlib import Path
import shutil
import sys

from playground_assets import MANIFEST, fingerprint, validate_distribution, validate_package

ROOT = Path(__file__).resolve().parents[1]
EXAMPLES = {"sampler": ("Sampler", {"sample_nature.xml"})}
DEFAULT_DISTRIBUTION = ROOT / "samples/ShaderLab/composeApp/build/dist/wasmJs/productionExecutable"


def package(distribution, site, site_url, route="playground", sample_images=None):
    errors = validate_distribution(distribution, **({"sample_images": sample_images} if sample_images is not None else {}))
    if not (site / "index.html").is_file():
        errors.append("Build the documentation with zensical build --strict before packaging the playground")
    if errors:
        raise ValueError("\n".join(errors))
    destination = site / route
    if destination.exists():
        shutil.rmtree(destination)
    shutil.copytree(distribution, destination)
    home = destination / "index.html"
    # Keep standalone sample builds usable, while honoring a docs custom domain.
    home.write_text(home.read_text(encoding="utf-8").replace(
        "https://debanshu777.github.io/ShaderX/", site_url,
    ), encoding="utf-8")
    (destination / MANIFEST).write_text(json.dumps({
        "version": 1,
        "files": fingerprint(destination),
    }, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    errors = validate_package(destination, **({"sample_images": sample_images} if sample_images is not None else {}))
    if errors:
        raise ValueError("\n".join(errors))
    return destination


def main():
    import yaml

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--distribution", type=Path, default=DEFAULT_DISTRIBUTION)
    parser.add_argument("--site", type=Path, default=ROOT / "site")
    args = parser.parse_args()
    config = yaml.safe_load((ROOT / "mkdocs.yml").read_text(encoding="utf-8"))
    try:
        destination = package(args.distribution, args.site, config["site_url"])
    except ValueError as error:
        print(error, file=sys.stderr)
        return 1
    for slug, (module, images) in EXAMPLES.items():
        distribution = ROOT / f"samples/{module}/composeApp/build/dist/wasmJs/productionExecutable"
        try:
            package(distribution, args.site, config["site_url"], slug, images)
        except ValueError as error:
            print(f"{module}: {error}", file=sys.stderr)
            return 1
    files = fingerprint(destination)
    print(f"Packaged {len(files)} playground files ({sum(item['bytes'] for item in files.values()):,} bytes) at {destination}; packaged the four-tab sampler")
    return 0


if __name__ == "__main__":
    sys.exit(main())
