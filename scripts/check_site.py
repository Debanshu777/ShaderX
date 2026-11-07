#!/usr/bin/env python3
"""Validate built page metadata, local links, search, and sitemap before publishing."""

from html.parser import HTMLParser
import json
from pathlib import Path
import sys
from urllib.parse import unquote, urljoin, urlsplit
import xml.etree.ElementTree as ET

import yaml

from playground_assets import validate_package

ROOT = Path(__file__).resolve().parents[1]
SITE = ROOT / "site"


class Page(HTMLParser):
    def __init__(self):
        super().__init__()
        self.meta = {}
        self.canonicals = []
        self.urls = []
        self.media = []
        self.ids = set()
        self.title = ""
        self.h1_count = 0
        self.in_title = False
        self.in_json = False
        self.json_blocks = []

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if attrs.get("id"):
            self.ids.add(attrs["id"])
        if tag == "meta":
            self.meta[attrs.get("name", attrs.get("property", ""))] = attrs.get("content", "")
        if tag == "link" and attrs.get("rel") == "canonical":
            self.canonicals.append(attrs.get("href", ""))
        if tag == "title":
            self.in_title = True
        if tag == "h1":
            self.h1_count += 1
        if tag in ("img", "video") and set(attrs.get("class", "").split()) & {"shaderx-banner", "shaderx-demo"}:
            self.media.append((tag, attrs))
        if tag == "script" and attrs.get("type") == "application/ld+json":
            self.in_json = True
            self.json_blocks.append("")
        for key in ("href", "src", "poster"):
            if key in attrs:
                self.urls.append(attrs[key])

    def handle_endtag(self, tag):
        if tag == "title":
            self.in_title = False
        if tag == "script":
            self.in_json = False

    def handle_data(self, data):
        if self.in_title:
            self.title += data
        if self.in_json:
            self.json_blocks[-1] += data


def check():
    config = yaml.safe_load((ROOT / "mkdocs.yml").read_text(encoding="utf-8"))
    base = config["site_url"]
    origin = urlsplit(base)
    errors = []
    pages = {}
    for file in SITE.rglob("*.html"):
        parser = Page()
        parser.feed(file.read_text(encoding="utf-8"))
        pages[file.resolve()] = parser
    if not pages:
        print("No built HTML found. Run zensical build --strict first.", file=sys.stderr)
        return 1

    guide_titles = {}
    for guide in (ROOT / "docs").rglob("*.md"):
        relative = guide.relative_to(ROOT / "docs")
        output = (SITE / relative.parent / "index.html") if relative.name == "index.md" else (SITE / relative.with_suffix("") / "index.html")
        if output.resolve() not in pages:
            errors.append(f"Missing HTML for {relative}")
        source = guide.read_text(encoding="utf-8")
        if source.startswith("---\n") and "\n---\n" in source[4:]:
            metadata = yaml.safe_load(source[4:].split("\n---\n", 1)[0])
            if isinstance(metadata, dict) and metadata.get("title"):
                guide_titles[output.resolve()] = metadata["title"]

    titles, descriptions, canonical_urls = set(), set(), set()
    for file, page in pages.items():
        relative = file.relative_to(SITE.resolve()).as_posix()
        if relative == "404.html":
            continue
        expected = urljoin(base, relative.removesuffix("index.html"))
        verification = config.get("extra", {}).get("google_site_verification")
        if relative == "index.html" and verification and page.meta.get("google-site-verification") != verification:
            errors.append("index.html: missing or incorrect configured Search Console verification tag")
        if page.canonicals != [expected]:
            errors.append(f"{relative}: expected one canonical URL: {expected}")
        is_playground = relative == "playground/index.html"
        if not is_playground:
            canonical_urls.add(expected)
        if not page.title.strip() or page.title in titles:
            errors.append(f"{relative}: empty or duplicate title")
        titles.add(page.title)
        if file in guide_titles:
            guide_title = guide_titles[file]
            if page.title != f"{guide_title} - {config['site_name']}":
                errors.append(f"{relative}: document title must use the descriptive guide title")
            for key in ("og:title", "twitter:title"):
                if page.meta.get(key) != f"{guide_title} — {config['site_name']}":
                    errors.append(f"{relative}: {key} must use the descriptive guide title")
        description = page.meta.get("description", "").strip()
        if not description or description in descriptions:
            errors.append(f"{relative}: empty or duplicate description")
        descriptions.add(description)
        if page.h1_count != 1:
            errors.append(f"{relative}: expected one h1, found {page.h1_count}")
        for name in ("robots", "googlebot"):
            directives = page.meta.get(name, "").lower().replace(",", " ").split()
            if ("noindex" in directives or "none" in directives) and not is_playground:
                errors.append(f"{relative}: {name} prevents search indexing")
        if is_playground and "noindex" not in page.meta.get("robots", "").lower().replace(",", " ").split():
            errors.append("playground/index.html: the runtime-only demo must stay noindex; searchable content belongs in the guides")
        for tag, attrs in page.media:
            for dimension in ("width", "height"):
                value = attrs.get(dimension) or ""
                if not value.isdigit() or int(value) <= 0:
                    errors.append(f"{relative}: showcase {tag} requires a positive {dimension}")
            if tag == "video" and (attrs.get("preload") != "none" or "controls" not in attrs or "autoplay" in attrs):
                errors.append(f"{relative}: showcase video requires controls, preload=none, and no autoplay")
        for key in ("og:title", "og:description", "og:url", "og:image", "og:image:alt",
                    "twitter:card", "twitter:title", "twitter:description", "twitter:image"):
            if not page.meta.get(key):
                errors.append(f"{relative}: missing {key}")
        if page.meta.get("og:url") != expected:
            errors.append(f"{relative}: Open Graph URL differs from canonical")
        for key in ("og:image", "twitter:image"):
            if page.meta.get(key) != urljoin(base, "assets/shaderx-banner.png"):
                errors.append(f"{relative}: {key} must reference the hosted banner")

        for link in page.urls:
            parsed = urlsplit(urljoin(expected, link))
            if parsed.scheme not in ("http", "https") or parsed.netloc != origin.netloc:
                continue
            if not parsed.path.startswith(origin.path):
                errors.append(f"{relative}: link escapes project URL prefix: {link}")
                continue
            target = SITE / unquote(parsed.path[len(origin.path):])
            if target.is_dir():
                target /= "index.html"
            if not target.is_file():
                errors.append(f"{relative}: missing local target: {link}")
            elif parsed.fragment and target.suffix == ".html":
                target_page = pages.get(target.resolve())
                if target_page and unquote(parsed.fragment) not in target_page.ids:
                    errors.append(f"{relative}: missing anchor: {link}")

    sitemap = SITE / "sitemap.xml"
    if not sitemap.is_file():
        errors.append("Missing sitemap.xml")
    else:
        urls = {node.text for node in ET.parse(sitemap).iter("{http://www.sitemaps.org/schemas/sitemap/0.9}loc")}
        if urls != canonical_urls:
            errors.append(f"Sitemap differs from page canonicals: {urls ^ canonical_urls}")
    search = SITE / "search.json"
    if not search.is_file() or not json.loads(search.read_text(encoding="utf-8")).get("items"):
        errors.append("Missing or empty search index")
    home = pages.get((SITE / "index.html").resolve())
    try:
        structured = [json.loads(block) for block in home.json_blocks] if home else []
        if not any(item.get("@type") == "SoftwareSourceCode" and item.get("codeRepository") == config["repo_url"] for item in structured):
            errors.append("Missing SoftwareSourceCode structured data on the home page")
    except json.JSONDecodeError as error:
        errors.append(f"Invalid structured data: {error}")

    errors.extend(validate_package(SITE / "playground"))

    for error in errors:
        print(error, file=sys.stderr)
    if errors:
        return 1
    print(f"Checked {len(canonical_urls)} indexable pages and the playground: metadata, links, anchors, sitemap, search, structured data, and packaged assets.")
    return 0


if __name__ == "__main__":
    sys.exit(check())
