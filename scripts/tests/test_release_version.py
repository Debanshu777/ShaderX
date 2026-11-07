import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from release_version import select_version


class ReleaseVersionTest(unittest.TestCase):
    def select(self, kind="patch", tags=None, ref="refs/heads/main", run="12", attempt="1"):
        return select_version(tags if tags is not None else ["0.1.3"], kind, ref, run, attempt)

    def test_stable_bumps_from_existing_unprefixed_tags(self):
        for kind, expected in (("patch", "0.1.4"), ("minor", "0.2.0"), ("major", "1.0.0")):
            with self.subTest(kind=kind):
                result = self.select(kind)
                self.assertEqual(expected, result["version"])
                self.assertEqual(f"v{expected}", result["tag"])
                self.assertEqual("false", result["prerelease"])
                self.assertEqual("0.1.3", result["previous_tag"])

    def test_numeric_order_and_mixed_tag_formats(self):
        result = self.select(tags=["v0.9.9", "0.10.0", "v0.2.0", "v1.0.0-beta.4.1", "other"])
        self.assertEqual("0.10.1", result["version"])
        self.assertEqual("0.10.0", result["previous_tag"])

    def test_prereleases_can_use_feature_branches_and_do_not_advance_stable(self):
        tags = ["0.1.3", "v0.1.4-beta.11.1"]
        result = self.select("prerelease", tags=tags, ref="refs/heads/feature/audio")
        self.assertEqual("0.1.4-beta.12.1", result["version"])
        self.assertEqual("true", result["prerelease"])
        self.assertEqual("0.1.4", self.select(tags=tags)["version"])

    def test_retry_and_new_run_get_distinct_beta_versions(self):
        versions = {self.select("prerelease", run=run, attempt=attempt)["version"]
                    for run, attempt in (("12", "1"), ("12", "2"), ("13", "1"))}
        self.assertEqual(3, len(versions))

    def test_reserved_stable_tag_advances_next_version(self):
        self.assertEqual("0.1.5", self.select(tags=["0.1.3", "v0.1.4"])["version"])

    def test_rejects_existing_beta_in_either_tag_format(self):
        for tag in ("0.1.4-beta.12.1", "v0.1.4-beta.12.1"):
            with self.subTest(tag=tag), self.assertRaisesRegex(ValueError, "already tagged"):
                self.select("prerelease", tags=["0.1.3", tag])

    def test_stable_requires_main(self):
        for kind in ("patch", "minor", "major"):
            with self.subTest(kind=kind), self.assertRaisesRegex(ValueError, "main"):
                self.select(kind, ref="refs/heads/feature")

    def test_rejects_tag_dispatch_even_for_prerelease(self):
        with self.assertRaisesRegex(ValueError, "source branch"):
            self.select("prerelease", ref="refs/tags/v0.1.3")

    def test_rejects_missing_history_and_invalid_input(self):
        for tags in ([], ["v1.0.0-beta.1.1"], ["01.2.3"]):
            with self.subTest(tags=tags), self.assertRaisesRegex(ValueError, "No stable"):
                self.select(tags=tags)
        with self.assertRaisesRegex(ValueError, "Unknown"):
            self.select("snapshot")
        for value in ("0", "-1", "1\n2", "abc"):
            with self.subTest(value=value), self.assertRaisesRegex(ValueError, "positive"):
                self.select("prerelease", run=value)
            with self.subTest(attempt=value), self.assertRaisesRegex(ValueError, "positive"):
                self.select("prerelease", attempt=value)


if __name__ == "__main__":
    unittest.main()
