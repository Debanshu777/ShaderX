#!/usr/bin/env python3
"""Check repository Markdown links and guide metadata without making network calls."""

from html.parser import HTMLParser
from pathlib import Path
import sys
from urllib.parse import unquote, urlsplit

import markdown
import yaml

ROOT = Path(__file__).resolve().parents[1]
REPO_PATH = "/Debanshu777/ShaderX/"


class Links(HTMLParser):
    def __init__(self):
        super().__init__()
        self.urls = []

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        for key in ("href", "src", "poster"):
            if key in attrs:
                self.urls.append((tag, key, attrs[key]))


def check():
    errors = []
    readme = (ROOT / "README.md").read_text(encoding="utf-8")
    for asset in ("assets/shader_lab.gif", "assets/shader_lab_web.gif",
                  "assets/ASCIICamera.gif", "assets/vertical_carousal.gif",
                  "docs/assets/shaderx-banner.png"):
        if f'src="{asset}"' not in readme:
            errors.append(f"README.md: restore the inline showcase image {asset}")
    descriptions = set()
    guides = sorted((ROOT / "docs").rglob("*.md"))
    files = guides + [ROOT / name for name in ("README.md", "CONTRIBUTING.md", "CHANGELOG.md")]
    for file in files:
        source = file.read_text(encoding="utf-8")
        if file in guides:
            if not source.startswith("---\n") or "\n---\n" not in source[4:]:
                errors.append(f"{file.relative_to(ROOT)}: missing YAML front matter")
                continue
            header, source = source[4:].split("\n---\n", 1)
            meta = yaml.safe_load(header)
            if not isinstance(meta, dict) or not all(isinstance(meta.get(k), str) and meta[k].strip() for k in ("title", "description")):
                errors.append(f"{file.relative_to(ROOT)}: title and description must be nonempty strings")
                continue
            if meta["description"] in descriptions:
                errors.append(f"{file.relative_to(ROOT)}: duplicate description")
            descriptions.add(meta["description"])

        parser = Links()
        parser.feed(markdown.markdown(source, extensions=["fenced_code", "tables"]))
        for tag, attribute, url in parser.urls:
            parsed = urlsplit(url)
            path = unquote(parsed.path)
            if parsed.netloc == "github.com" and path.startswith(REPO_PATH):
                rest = path[len(REPO_PATH):]
                # Verify source links into this repository; release/issue URLs are remote.
                if rest.startswith(("blob/main/", "tree/main/")):
                    target = ROOT / rest.split("/", 2)[2]
                else:
                    continue
            elif parsed.scheme or parsed.netloc or not path:
                continue
            elif file in guides and tag == "video" and attribute == "poster":
                # The builder rewrites src/href but leaves video posters as-is.
                # Resolve posters from the generated /guide/ URL instead.
                page_directory = file.parent if file.name == "index.md" else file.with_suffix("")
                target = (page_directory / path).resolve()
            else:
                target = file.parent / path
            # The production browser app is assembled into the site after Zensical.
            if target.resolve() == (ROOT / "docs/playground").resolve():
                target = ROOT / "samples/ShaderLab/composeApp/src/wasmJsMain/resources/index.html"
            if not target.exists():
                errors.append(f"{file.relative_to(ROOT)}: missing link target {url}")

    for error in errors:
        print(error, file=sys.stderr)
    if errors:
        return 1
    print(f"Checked metadata for {len(guides)} guides and file links in {len(files)} Markdown files.")
    return 0


if __name__ == "__main__":
    sys.exit(check())
