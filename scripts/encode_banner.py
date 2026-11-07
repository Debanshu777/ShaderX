#!/usr/bin/env python3
"""Encode the docs banner as lossless WebP and verify decoded RGBA pixels match the PNG."""

import argparse
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ffmpeg", default=shutil.which("ffmpeg"))
    args = parser.parse_args()
    if not args.ffmpeg:
        parser.error("Install FFmpeg with libwebp, or pass --ffmpeg /path/to/ffmpeg")
    source = ROOT / "docs/assets/shaderx-banner.png"
    destination = source.with_suffix(".webp")
    common = [args.ffmpeg, "-hide_banner", "-loglevel", "error"]
    subprocess.run(common + [
        "-y", "-i", str(source), "-c:v", "libwebp", "-lossless", "1",
        "-compression_level", "6", "-q:v", "100", str(destination),
    ], check=True)

    def pixels(path):
        return subprocess.check_output(common + [
            "-i", str(path), "-f", "rawvideo", "-pix_fmt", "rgba", "-",
        ])

    if pixels(source) != pixels(destination):
        destination.unlink()
        raise SystemExit("Decoded pixels differ; refusing to keep the WebP")
    print(f"Identical RGBA pixels: {source.stat().st_size:,} PNG bytes -> {destination.stat().st_size:,} WebP bytes")


if __name__ == "__main__":
    main()
