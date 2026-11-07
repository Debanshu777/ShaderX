#!/usr/bin/env python3
"""Create docs-only H.264 recordings and posters; preserve the README's source GIFs."""

import argparse
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]
NAMES = ("shader_lab", "shader_lab_web", "ASCIICamera", "vertical_carousal")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ffmpeg", default=shutil.which("ffmpeg"))
    args = parser.parse_args()
    if not args.ffmpeg:
        parser.error("Install FFmpeg with libx264, or pass --ffmpeg /path/to/ffmpeg")
    destination = ROOT / "docs/assets/demos"
    destination.mkdir(parents=True, exist_ok=True)
    for name in NAMES:
        source = ROOT / "assets" / f"{name}.gif"
        video = destination / f"{name}.mp4"
        common = [args.ffmpeg, "-hide_banner", "-loglevel", "error", "-y", "-i", str(source)]
        # Padding keeps all original pixels while satisfying H.264's even dimensions.
        subprocess.run(common + [
            "-vf", "pad=ceil(iw/2)*2:ceil(ih/2)*2", "-an", "-c:v", "libx264",
            "-preset", "slow", "-crf", "23", "-pix_fmt", "yuv420p",
            "-movflags", "+faststart", str(video),
        ], check=True)
        subprocess.run(common + [
            "-vf", "pad=ceil(iw/2)*2:ceil(ih/2)*2", "-frames:v", "1", "-q:v", "3",
            "-update", "1", str(destination / f"{name}-poster.jpg"),
        ], check=True)
        print(f"{name}: {source.stat().st_size:,} GIF bytes -> {video.stat().st_size:,} MP4 bytes")


if __name__ == "__main__":
    main()
