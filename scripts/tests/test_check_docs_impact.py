import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from check_docs_impact import evaluate, needs_docs


class DocsImpactTest(unittest.TestCase):
    def test_all_codebase_changes_require_both_updates(self):
        for path in (
            "shaderx/src/commonMain/kotlin/effect/NewEffect.kt",
            "shaderx/src/skiaMain/kotlin/Factory.kt",
            "shaderx/src/commonTest/kotlin/EffectTest.kt",
            "samples/ShaderLab/composeApp/src/jvmTest/kotlin/AppTest.kt",
            "samples/NewSample/iosApp/App.swift",
            "gradle/libs.versions.toml", "shaderx/build.gradle.kts",
            "gradle/wrapper/gradle-wrapper.properties", ".java-version",
            "scripts/check_docs.py", "tools/banner/render.js",
            ".github/workflows/docs.yml", "requirements-docs.txt",
            "docs/stylesheets/extra.css", "docs-overrides/main.html",
            "new-tool/source.newlanguage", ".gitignore",
        ):
            with self.subTest(path=path):
                self.assertFalse(evaluate([path])[0])
                self.assertFalse(evaluate([path, "README.md"])[0])
                self.assertFalse(evaluate([path, "docs/contributing.md"])[0])
                self.assertTrue(evaluate([path, "README.md", "docs/contributing.md"])[0])

    def test_prose_and_media_only_changes_do_not_require_companion_edits(self):
        self.assertTrue(evaluate([
            "AGENTS.md", "docs/effects.md", "docs/llms.txt", "LICENSE",
            "docs/assets/shaderx-banner.png", "assets/shader_lab.gif",
            "tools/banner/flowers.webp",
        ])[0])

    def test_changelog_contributor_file_and_media_do_not_replace_a_guide(self):
        self.assertFalse(evaluate([
            "shaderx/build.gradle.kts", "README.md", "CONTRIBUTING.md",
            "CHANGELOG.md", "docs/assets/shaderx-banner.png",
        ])[0])

    def test_missing_readme_is_reported(self):
        passed, message = evaluate(["scripts/check_docs.py", "docs/maintaining.md"])
        self.assertFalse(passed)
        self.assertIn("Missing: README.md", message)

    def test_missing_guide_is_reported(self):
        passed, message = evaluate(["scripts/check_docs.py", "README.md"])
        self.assertFalse(passed)
        self.assertIn("Missing: an affected Markdown guide in docs/", message)

    def test_deleted_readme_or_guide_does_not_satisfy_check(self):
        paths = ["shaderx/build.gradle.kts", "README.md", "docs/getting-started.md"]
        for deleted in ("README.md", "docs/getting-started.md"):
            with self.subTest(deleted=deleted):
                self.assertFalse(evaluate(paths, deleted=[deleted])[0])

    def test_removing_code_still_requires_documentation(self):
        path = "shaderx/src/commonMain/kotlin/effect/OldEffect.kt"
        self.assertFalse(evaluate([path], deleted=[path])[0])
        self.assertTrue(evaluate(
            [path, "README.md", "docs/effects.md"], deleted=[path]
        )[0])

    def test_new_platform_source_sets_are_detected(self):
        self.assertTrue(needs_docs("shaderx/src/linuxMain/kotlin/Factory.kt"))

    def test_empty_diff_passes(self):
        self.assertTrue(evaluate([])[0])


if __name__ == "__main__":
    unittest.main()
