import io
import sys
import tempfile
import unittest
import urllib.error
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from release_version import (
    METADATA_URL, assert_unpublished, central_versions, changelog_section,
    fetch_central_versions, github_release_body, read_configured_version, select_version,
)


class ReleaseVersionTest(unittest.TestCase):
    def select(self, version="0.2.0", tags=None, ref="refs/heads/main", confirmation=None, published=None):
        return select_version(
            tags if tags is not None else ["0.1.3", "v0.1.4"], version, ref,
            version if confirmation is None else confirmation,
            published if published is not None else ["0.1.3", "0.1.4"],
        )

    def test_uses_exact_configured_stable_version_without_a_beta_or_automatic_bump(self):
        self.assertEqual(
            {"version": "0.2.0", "tag": "v0.2.0", "previous_tag": "v0.1.4"},
            self.select(),
        )
        self.assertEqual("1.0.0", self.select(version="1.0.0")["version"])

    def test_numeric_order_and_both_historical_tag_formats(self):
        result = self.select(version="0.11.0", tags=["v0.9.9", "0.10.0", "v0.11.0-beta.1"],
                             published=["0.9.9", "0.10.0"])
        self.assertEqual("0.10.0", result["previous_tag"])
        self.assertEqual("0.11.0", result["version"])

    def test_generated_notes_start_from_published_baseline_not_reserved_failed_tag(self):
        result = self.select(version="0.2.1", tags=["v0.1.4", "v0.2.0"])
        self.assertEqual("v0.1.4", result["previous_tag"])

    def test_rejects_every_source_other_than_main(self):
        for ref in ("refs/heads/feature/release", "refs/tags/v0.2.0", "main", ""):
            with self.subTest(ref=ref), self.assertRaisesRegex(ValueError, "main"):
                self.select(ref=ref)

    def test_requires_exact_dispatch_confirmation(self):
        for confirmation in ("", "0.1.4", "v0.2.0", "0.2.0 "):
            with self.subTest(confirmation=confirmation), self.assertRaisesRegex(ValueError, "confirmVersion"):
                self.select(confirmation=confirmation)

    def test_rejects_existing_reserved_coordinate_in_both_tag_formats(self):
        for tag in ("0.2.0", "v0.2.0"):
            with self.subTest(tag=tag), self.assertRaisesRegex(ValueError, "already tagged"):
                self.select(tags=["v0.1.4", tag])

    def test_rejects_published_coordinate_even_if_git_tag_history_misses_it(self):
        with self.assertRaisesRegex(ValueError, "no matching Git tag"):
            self.select(version="0.1.4", tags=["0.1.3"])
        with self.assertRaisesRegex(ValueError, "already exists"):
            self.select(version="0.2.0", tags=["v0.2.1"], published=["0.2.0", "0.2.1"])

    def test_latest_central_release_must_have_a_corresponding_tag(self):
        with self.assertRaisesRegex(ValueError, "reconcile its actual source commit"):
            self.select(tags=["0.1.3"])

    def test_rejects_downgrades_relative_to_central_and_reserved_tags(self):
        with self.assertRaisesRegex(ValueError, "newer"):
            self.select(version="0.1.2")
        with self.assertRaisesRegex(ValueError, "newer"):
            self.select(tags=["v0.1.4", "v0.3.0"])

    def test_requires_verified_stable_history(self):
        for tags in ([], ["v0.2.0-beta.1"], ["01.2.3"]):
            with self.subTest(tags=tags), self.assertRaisesRegex(ValueError, "No stable release tag"):
                self.select(tags=tags)
        with self.assertRaisesRegex(ValueError, "No stable Maven Central baseline"):
            self.select(published=["0.2.0-beta.1"])

    def test_refuses_prerelease_or_invalid_configured_versions(self):
        for version in ("v0.2.0", "0.2", "0.2.0-beta.1", "00.2.0", "0.2.0+build", "0.2.0\n"):
            with self.subTest(version=version), self.assertRaisesRegex(ValueError, "stable SemVer"):
                self.select(version=version)


class ReleaseInputTest(unittest.TestCase):
    def read_property(self, content):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "gradle.properties"
            path.write_text(content, encoding="utf-8")
            return read_configured_version(path)

    def test_reads_single_literal_gradle_version(self):
        self.assertEqual("0.2.0", self.read_property("# shaderxVersion=0.1.4\nshaderxVersion = 0.2.0\n"))

    def test_rejects_missing_duplicate_and_nonliteral_version_properties(self):
        for content in ("", "shaderxVersion=0.2.0\nshaderxVersion=0.3.0", "shaderxVersion:0.2.0",
                        "shaderxVersion=0.2.0-beta.1", "shaderxVersion=0.2.0 # note"):
            with self.subTest(content=content), self.assertRaises(ValueError):
                self.read_property(content)

    def test_curated_notes_match_exact_version_and_stop_at_the_next_release(self):
        text = "# Changelog\n\n## 0.2.0 — prepared\n\n### Added\n\n- Custom effects.\n\n## 0.1.4\n\n- Older.\n"
        self.assertEqual("### Added\n\n- Custom effects.", changelog_section(text, "0.2.0"))

    def test_requires_nonempty_curated_notes_for_the_stable_section(self):
        for content in ("## Unreleased — proposed 0.2.0\n- Example", "## 0.2.0-beta.1\n- Example",
                        "## 0.2.0\n\n## 0.1.4\n- Older"):
            with self.subTest(content=content), self.assertRaises(ValueError):
                changelog_section(content, "0.2.0")

    def test_checks_metadata_identity_and_history(self):
        xml = b"""<metadata><groupId>io.github.debanshu777</groupId><artifactId>shaderx</artifactId>
        <versioning><release>0.1.4</release><versions><version>0.1.3</version>
        <version>0.1.4</version></versions></versioning></metadata>"""
        self.assertEqual(["0.1.3", "0.1.4"], central_versions(xml))
        for invalid in (b"not xml", xml.replace(b"shaderx", b"other"), xml.replace(b"0.1.4</release>", b"0.1.5</release>")):
            with self.subTest(invalid=invalid), self.assertRaises(ValueError):
                central_versions(invalid)


class ReleaseNotesTest(unittest.TestCase):
    def test_relative_links_are_pinned_to_release_commit_and_external_links_stay_intact(self):
        source_sha = "a" * 40
        source = "[Migration](docs/migration.md#inputs) [Plan](docs/release-plan.md) [Central](https://repo.maven.apache.org/)"
        result = github_release_body(source, "Debanshu777/ShaderX", source_sha)
        self.assertIn(f"https://github.com/Debanshu777/ShaderX/blob/{source_sha}/docs/migration.md#inputs", result)
        self.assertIn(f"https://github.com/Debanshu777/ShaderX/blob/{source_sha}/docs/release-plan.md", result)
        self.assertIn("[Central](https://repo.maven.apache.org/)", result)

    def test_notes_reject_missing_commit_and_repository_escaping_links(self):
        for source, sha in (("[secret](../outside)", "a" * 40), ("notes", "main")):
            with self.subTest(source=source), self.assertRaises(ValueError):
                github_release_body(source, "Debanshu777/ShaderX", sha)

    def test_duplicate_release_sections_are_rejected(self):
        with self.assertRaisesRegex(ValueError, "exactly one"):
            changelog_section("## 0.2.0\n- First\n## 0.2.0\n- Second", "0.2.0")


class ReleaseNetworkTest(unittest.TestCase):
    @mock.patch("release_version.urllib.request.urlopen")
    def test_exact_coordinate_404_is_the_only_accepted_absence(self, opener):
        opener.side_effect = urllib.error.HTTPError("url", 404, "Not found", {}, None)
        assert_unpublished("0.2.0")
        request = opener.call_args.args[0]
        self.assertTrue(request.full_url.endswith("/0.2.0/shaderx-0.2.0.pom"))
        self.assertEqual(20, opener.call_args.kwargs["timeout"])

    @mock.patch("release_version.urllib.request.urlopen")
    def test_existing_pom_refuses_publication_even_if_metadata_was_stale(self, opener):
        opener.return_value.__enter__.return_value = io.BytesIO(b"<project/>")
        with self.assertRaisesRegex(ValueError, "already exists"):
            assert_unpublished("0.2.0")

    @mock.patch("release_version.urllib.request.urlopen")
    def test_http_failures_and_network_errors_fail_closed(self, opener):
        for error in (urllib.error.HTTPError("url", 429, "Limit", {}, None),
                      urllib.error.HTTPError("url", 500, "Failure", {}, None),
                      urllib.error.URLError("offline"), TimeoutError()):
            with self.subTest(error=error):
                opener.side_effect = error
                with self.assertRaisesRegex(ValueError, "refusing release"):
                    assert_unpublished("0.2.0")
                with self.assertRaisesRegex(ValueError, "refusing release"):
                    fetch_central_versions()

    @mock.patch("release_version.urllib.request.urlopen")
    def test_metadata_response_is_bounded_and_has_a_timeout(self, opener):
        opener.return_value.__enter__.return_value = io.BytesIO(b"x" * (1024 * 1024 + 1))
        with self.assertRaisesRegex(ValueError, "exceeds"):
            fetch_central_versions()
        self.assertEqual(METADATA_URL, opener.call_args.args[0].full_url)
        self.assertEqual(20, opener.call_args.kwargs["timeout"])


if __name__ == "__main__":
    unittest.main()
