#!/usr/bin/env python3
"""Verify the debug APK physically contains the generated offline Georgia map."""
from __future__ import annotations

import hashlib
import json
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APK_DIR = ROOT / "app/build/outputs/apk/debug"
APK_PATH = Path(sys.argv[1]) if len(sys.argv) > 1 else next(iter(APK_DIR.glob("*.apk")), None)
MAP_ENTRY = "assets/offline/georgia.map"
BUILD_MANIFEST_ENTRY = "assets/offline/georgia-map-build.json"
POI_ENTRY = "assets/offline/georgia-poi.sqlite"


def main() -> int:
    if APK_PATH is None or not APK_PATH.is_file():
        print("ERROR: debug APK not found.", file=sys.stderr)
        return 2

    with zipfile.ZipFile(APK_PATH) as apk:
        entries = set(apk.namelist())
        if MAP_ENTRY not in entries or BUILD_MANIFEST_ENTRY not in entries or POI_ENTRY not in entries:
            print("ERROR: APK does not contain the map, searchable POI database, and build manifest.", file=sys.stderr)
            return 3
        build_manifest = json.loads(apk.read(BUILD_MANIFEST_ENTRY))
        map_info = apk.getinfo(MAP_ENTRY)
        poi_info = apk.getinfo(POI_ENTRY)
        if map_info.compress_type != zipfile.ZIP_STORED:
            print("ERROR: georgia.map should be stored uncompressed in the APK.", file=sys.stderr)
            return 6
        if poi_info.compress_type != zipfile.ZIP_STORED:
            print("ERROR: georgia-poi.sqlite should be stored uncompressed in the APK.", file=sys.stderr)
            return 6
        digest_builder = hashlib.sha256()
        map_size = 0
        with apk.open(MAP_ENTRY) as map_stream:
            for chunk in iter(lambda: map_stream.read(1024 * 1024), b""):
                map_size += len(chunk)
                digest_builder.update(chunk)
        poi_digest = hashlib.sha256()
        poi_size = 0
        with apk.open(POI_ENTRY) as poi_stream:
            for chunk in iter(lambda: poi_stream.read(1024 * 1024), b""):
                poi_size += len(chunk)
                poi_digest.update(chunk)

    digest = digest_builder.hexdigest()
    poi_sha = poi_digest.hexdigest()
    if digest != build_manifest.get("mapFileSha256"):
        print("ERROR: APK map checksum differs from the bundled metadata.", file=sys.stderr)
        return 4
    if map_size < 1_048_576:
        print("ERROR: bundled map is unexpectedly small.", file=sys.stderr)
        return 5
    if build_manifest.get("mapFileSizeBytes") != map_size:
        print("ERROR: APK map size differs from the bundled metadata.", file=sys.stderr)
        return 5
    if poi_sha != build_manifest.get("poiFileSha256"):
        print("ERROR: APK POI checksum differs from the bundled metadata.", file=sys.stderr)
        return 6
    if build_manifest.get("poiFileSizeBytes") != poi_size or build_manifest.get("poiCount", 0) < 100:
        print("ERROR: APK POI size or count differs from the bundled metadata.", file=sys.stderr)
        return 6

    print(
        f"OK: {APK_PATH.name} contains georgia.map ({map_size:,} bytes) and "
        f"georgia-poi.sqlite ({poi_size:,} bytes, {build_manifest['poiCount']:,} POIs)."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
