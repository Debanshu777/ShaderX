import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from package_playground import package
from playground_assets import MANIFEST, SAMPLE_IMAGES, validate_distribution, validate_package


class PlaygroundArtifactsTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        root = Path(self.temporary.name)
        self.distribution = root / "distribution"
        self.distribution.mkdir()
        (self.distribution / "index.html").write_text('<script src="composeApp.js"></script>')
        (self.distribution / "composeApp.js").write_text('fetch("app.wasm");')
        for name in ("app.wasm", "skiko.wasm"):
            (self.distribution / name).write_bytes(b"\0asm\x01\0\0\0")
        resources = self.distribution / "composeResources/sample/drawable"
        resources.mkdir(parents=True)
        for name in SAMPLE_IMAGES:
            (resources / name).write_text("<vector />")
        self.site = root / "site"
        self.site.mkdir()
        (self.site / "index.html").write_text("<h1>Documentation</h1>")

    def test_complete_bundle_is_copied_and_fingerprinted(self):
        destination = package(self.distribution, self.site, "https://example.org/ShaderX/")
        self.assertEqual([], validate_package(destination))
        self.assertTrue((destination / "composeResources/sample/drawable/sample_nature.xml").is_file())

    def test_missing_wasm_or_resource_prevents_packaging(self):
        (self.distribution / "app.wasm").unlink()
        (self.distribution / "composeResources/sample/drawable/sample_nature.xml").unlink()
        with self.assertRaises(ValueError) as failure:
            package(self.distribution, self.site, "https://example.org/ShaderX/")
        self.assertIn("WebAssembly binaries", str(failure.exception))
        self.assertIn("sample_nature.xml", str(failure.exception))
        self.assertFalse((self.site / "playground").exists())

    def test_deleted_lazy_chunk_is_detected_after_packaging(self):
        (self.distribution / "17.composeApp.js").write_text("console.log('chunk');")
        destination = package(self.distribution, self.site, "https://example.org/ShaderX/")
        (destination / "17.composeApp.js").unlink()
        self.assertIn("Packaged playground asset is missing: 17.composeApp.js", validate_package(destination))

    def test_truncated_or_changed_assets_are_rejected(self):
        destination = package(self.distribution, self.site, "https://example.org/ShaderX/")
        (destination / "app.wasm").write_bytes(b"<html>404</html>")
        (destination / "composeApp.js").write_text("changed")
        errors = validate_package(destination)
        self.assertIn("Invalid WebAssembly binary: app.wasm", errors)
        self.assertIn("Packaged playground asset changed: composeApp.js", errors)

    def test_root_relative_entry_script_is_rejected(self):
        (self.distribution / "index.html").write_text('<script src="/composeApp.js"></script>')
        self.assertTrue(any("relative to its own directory" in error for error in validate_distribution(self.distribution)))

    def test_unsafe_manifest_paths_are_rejected(self):
        destination = package(self.distribution, self.site, "https://example.org/ShaderX/")
        (destination / MANIFEST).write_text(json.dumps({"version": 1, "files": {"../outside": {}}}))
        self.assertTrue(any("unsafe asset path" in error for error in validate_package(destination)))


if __name__ == "__main__":
    unittest.main()
