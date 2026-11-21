#!/usr/bin/env python3
"""Verify the generated Georgia map and its metadata before APK assembly."""
from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets/offline"
MAP_FILE = ASSETS / "georgia.map"
MANIFEST_FILE = ASSETS / "georgia-map-build.json"


def main() -> int:
    if not MAP_FILE.is_file() or MAP_FILE.stat().st_size < 1_048_576:
        print("ERROR: app/src/main/assets/offline/georgia.map is missing or too small.", file=sys.stderr)
        return 2
    if not MANIFEST_FILE.is_file():
        print("ERROR: georgia-map-build.json is missing.", file=sys.stderr)
        return 3

    manifest = json.loads(MANIFEST_FILE.read_text(encoding="utf-8"))
    required_languages = {"en", "ru", "ka"}
    if not required_languages.issubset(set(manifest.get("preferredLanguagesRequested", []))):
        print("ERROR: map build did not request all required language variants.", file=sys.stderr)
        return 4
    if manifest.get("license") != "ODbL-1.0" or "OpenStreetMap contributors" not in manifest.get("attribution", ""):
        print("ERROR: OSM license or attribution metadata is missing.", file=sys.stderr)
        return 4
    if manifest.get("runtimeNetworkAccessRequired") is not False:
        print("ERROR: bundled map metadata must confirm no runtime network access.", file=sys.stderr)
        return 4
    source_url = manifest.get("sourceUrl", "")
    source_provider = manifest.get("sourceProvider")
    valid_sources = {
        "geofabrik": "https://download.geofabrik.de/europe/georgia-latest.osm.pbf",
        "openstreetmap-fr": "https://download.openstreetmap.fr/extracts/asia/georgia-latest.osm.pbf",
    }
    # Backward compatibility for a map cache produced by the original 0.4.2 pipeline.
    if source_provider is None and source_url == valid_sources["geofabrik"]:
        source_provider = "geofabrik"
    if source_provider not in valid_sources or source_url != valid_sources[source_provider]:
        print("ERROR: map metadata does not identify one of the approved Georgia-country OSM sources.", file=sys.stderr)
        return 4
    if not isinstance(manifest.get("sourcePbfMd5"), str) or len(manifest["sourcePbfMd5"]) != 32:
        print("ERROR: source PBF checksum is missing or malformed.", file=sys.stderr)
        return 4
    if manifest.get("mapFile") != MAP_FILE.name:
        print("ERROR: map manifest points to a different map file.", file=sys.stderr)
        return 4
    if manifest.get("mapFileSizeBytes") != MAP_FILE.stat().st_size:
        print("ERROR: map file size does not match the metadata.", file=sys.stderr)
        return 4

    digest_builder = hashlib.sha256()
    with MAP_FILE.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest_builder.update(chunk)
    digest = digest_builder.hexdigest()
    if digest != manifest.get("mapFileSha256"):
        print("ERROR: map file SHA-256 does not match its manifest.", file=sys.stderr)
        return 5

    print(
        "OK: Georgia Mapsforge map verified "
        f"({MAP_FILE.stat().st_size:,} bytes, SHA-256 {digest[:16]}…, requested language variants en/ru/ka)."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
