import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from check_docs_impact import evaluate, needs_docs


class DocsImpactTest(unittest.TestCase):
    def test_implementation_without_docs_fails(self):
        for path in (
            "shaderx/src/commonMain/kotlin/effect/NewEffect.kt",
            "shaderx/src/skiaMain/kotlin/Factory.kt",
            "shaderx/src/androidMain/kotlin/ImageProcessor.kt",
            "samples/ShaderLab/composeApp/src/commonMain/kotlin/App.kt",
            "gradle/libs.versions.toml", "shaderx/build.gradle.kts",
            "gradle/wrapper/gradle-wrapper.properties",
        ):
            with self.subTest(path=path):
                self.assertFalse(evaluate([path])[0])

    def test_tests_and_visual_assets_do_not_require_prose(self):
        self.assertTrue(evaluate([
            "shaderx/src/commonTest/kotlin/EffectTest.kt",
            "samples/ShaderLab/composeApp/src/jvmTest/kotlin/AppTest.kt",
            "docs/assets/shaderx-banner.png",
        ])[0])

    def test_changed_guide_satisfies_check(self):
        self.assertTrue(evaluate(["shaderx/build.gradle.kts", "docs/getting-started.md"])[0])

    def test_changelog_or_image_alone_does_not_satisfy_check(self):
        self.assertFalse(evaluate(["shaderx/build.gradle.kts", "CHANGELOG.md",
                                   "docs/assets/shaderx-banner.png"])[0])

    def test_explicit_rationale_satisfies_check(self):
        body = "Docs-Impact: none\nDocs-Reason: Internal refactor preserves all public behavior."
        self.assertTrue(evaluate(["shaderx/build.gradle.kts"], body)[0])

    def test_template_or_placeholder_does_not_bypass_check(self):
        for body in (
            "<!-- Docs-Impact: none\nDocs-Reason: This is a hidden template example. -->",
            "Docs-Impact: none\nDocs-Reason: N/A",
            "Docs-Impact: none\nDocs-Reason:\nThis unrelated line is not a reason.",
        ):
            with self.subTest(body=body):
                self.assertFalse(evaluate(["shaderx/build.gradle.kts"], body)[0])

    def test_new_platform_and_sample_source_sets_are_detected(self):
        self.assertTrue(needs_docs("shaderx/src/linuxMain/kotlin/Factory.kt"))
        self.assertTrue(needs_docs("samples/NewSample/iosApp/App.swift"))


if __name__ == "__main__":
    unittest.main()
