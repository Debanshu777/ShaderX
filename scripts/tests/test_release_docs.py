import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from release_version import check_release_versions


class ReleaseDocsTest(unittest.TestCase):
    def fixture(self, root, version="0.2.0"):
        (root / "docs").mkdir()
        sources = {
            "gradle.properties": f"shaderxVersion={version}\n",
            "README.md": f'```kotlin\nimplementation("io.github.debanshu777:shaderx:{version}")\n```\n',
            "docs/getting-started.md": (
                f'```kotlin\nimplementation("io.github.debanshu777:shaderx:{version}")\n```\n'
                f'```toml\n[versions]\nshaderx = "{version}"\n[libraries]\n'
                'shaderx = { module = "io.github.debanshu777:shaderx", version.ref = "shaderx" }\n```\n'
            ),
            "docs/llms.txt": f"Stable release coordinate: io.github.debanshu777:shaderx:{version}.\n",
            "CHANGELOG.md": f"# Changelog\n\n## {version}\n\n- Added custom authoring.\n",
        }
        for name, content in sources.items():
            (root / name).write_text(content, encoding="utf-8")

    def check_changed(self, name, old, new):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.fixture(root)
            path = root / name
            path.write_text(path.read_text(encoding="utf-8").replace(old, new), encoding="utf-8")
            return check_release_versions(root)

    def test_matching_pins_and_historical_prose_are_accepted(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.fixture(root)
            for name in ("README.md", "docs/getting-started.md", "docs/llms.txt", "CHANGELOG.md"):
                with (root / name).open("a", encoding="utf-8") as stream:
                    stream.write("\nHistorical 0.1.3 baseline; Central 0.1.4 was audited.\n")
            self.assertEqual([], check_release_versions(root))

    def test_future_release_requires_no_checker_code_change(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.fixture(root, version="0.3.0")
            self.assertEqual([], check_release_versions(root))

    def test_stale_or_dynamic_installation_pins_fail(self):
        for name in ("README.md", "docs/getting-started.md"):
            for invalid in ("0.1.4", "+", "0.2.+", "$shaderxVersion"):
                with self.subTest(name=name, invalid=invalid):
                    errors = self.check_changed(name, 'shaderx:0.2.0"', f'shaderx:{invalid}"')
                    self.assertTrue(any("installation pin" in error for error in errors))

    def test_one_stale_snippet_cannot_hide_behind_another_correct_snippet(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.fixture(root)
            with (root / "README.md").open("a", encoding="utf-8") as stream:
                stream.write('\n```kotlin\nimplementation("io.github.debanshu777:shaderx:0.1.4")\n```\n')
            self.assertTrue(any("installation pin" in error for error in check_release_versions(root)))

    def test_missing_literal_installation_snippet_fails(self):
        errors = self.check_changed("README.md", 'implementation("io.github.debanshu777:shaderx:0.2.0")', "implementation(libs.shaderx)")
        self.assertTrue(any("missing literal" in error for error in errors))

    def test_stale_missing_and_malformed_catalog_examples_fail(self):
        for old, new in (('shaderx = "0.2.0"', 'shaderx = "0.1.4"'),
                         ('shaderx = "0.2.0"', 'other = "0.2.0"'),
                         ('shaderx = "0.2.0"', 'shaderx = "0.2.0"\nshaderx = "0.2.0"')):
            with self.subTest(new=new):
                errors = self.check_changed("docs/getting-started.md", old, new)
                self.assertTrue(any("catalog" in error or "invalid TOML" in error for error in errors))

    def test_llms_target_and_changelog_heading_must_match(self):
        for name, old, new in (("docs/llms.txt", "shaderx:0.2.0", "shaderx:0.1.4"),
                               ("docs/llms.txt", "io.github.debanshu777:shaderx:0.2.0", "ShaderX"),
                               ("CHANGELOG.md", "## 0.2.0", "## Unreleased — proposed 0.2.0")):
            with self.subTest(name=name):
                self.assertTrue(self.check_changed(name, old, new))

    def test_ambiguous_version_source_fails(self):
        errors = self.check_changed("gradle.properties", "shaderxVersion=0.2.0", "shaderxVersion=0.2.0\nshaderxVersion=0.3.0")
        self.assertTrue(any("exactly one" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
