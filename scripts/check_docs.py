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
        for key in ("href", "src"):
            if key in attrs:
                self.urls.append(attrs[key])


def check():
    errors = []
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
        for url in parser.urls:
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
            else:
                target = file.parent / path
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
