#!/usr/bin/env python3
"""Validate the development AAR's independent runtime, compile and bytecode floors."""
import argparse
import collections
import hashlib
import io
import json
from pathlib import Path
import struct
import xml.etree.ElementTree as ET
import zipfile


def inspect(path):
    data = Path(path).read_bytes()
    with zipfile.ZipFile(io.BytesIO(data)) as aar:
        manifest = ET.fromstring(aar.read("AndroidManifest.xml"))
        minimum = int(manifest.find("uses-sdk").get("{http://schemas.android.com/apk/res/android}minSdkVersion"))
        metadata = dict(line.split("=", 1) for line in aar.read(
            "META-INF/com/android/build/gradle/aar-metadata.properties").decode().splitlines()
            if line and not line.startswith("#"))
        majors = collections.Counter()
        for name in aar.namelist():
            if name == "classes.jar" or name.startswith("libs/") and name.endswith(".jar"):
                with zipfile.ZipFile(io.BytesIO(aar.read(name))) as jar:
                    for entry in jar.namelist():
                        if entry.endswith(".class"):
                            header = jar.read(entry)[:8]
                            if header[:4] != b"\xca\xfe\xba\xbe":
                                raise ValueError(f"Invalid classfile {entry}")
                            majors[struct.unpack(">H", header[6:8])[0]] += 1
    return {"path": str(path), "sha256": hashlib.sha256(data).hexdigest(),
            "minSdk": minimum, "metadata": metadata, "classfile_majors": dict(sorted(majors.items()))}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("aar", type=Path)
    args = parser.parse_args()
    result = inspect(args.aar)
    print(json.dumps(result, indent=2))
    if result["minSdk"] != 31:
        parser.exit(1, "Expected development minSdk31\n")
    if result["metadata"].get("minCompileSdk") != "35":
        parser.exit(1, "Expected minCompileSdk35\n")
    majors = result["classfile_majors"]
    if not majors or max(majors) > 61 or 61 not in majors:
        parser.exit(1, "Expected Java17 (major61) output with no newer classfiles\n")


if __name__ == "__main__":
    main()
