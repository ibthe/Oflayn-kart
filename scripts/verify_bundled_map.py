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
POI_FILE = ASSETS / "georgia-poi.sqlite"
MANIFEST_FILE = ASSETS / "georgia-map-build.json"


def main() -> int:
    if not MAP_FILE.is_file() or MAP_FILE.stat().st_size < 1_048_576:
        print("ERROR: app/src/main/assets/offline/georgia.map is missing or too small.", file=sys.stderr)
        return 2
    if not POI_FILE.is_file() or POI_FILE.stat().st_size < 4096:
        print("ERROR: georgia-poi.sqlite is missing or unexpectedly small.", file=sys.stderr)
        return 3
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
        "openstreetmap-fr": "https://download.openstreetmap.fr/extracts/asia/georgia.osm.pbf",
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
    if manifest.get("poiFile") != POI_FILE.name:
        print("ERROR: map manifest points to a different POI database file.", file=sys.stderr)
        return 4
    if manifest.get("poiFileSizeBytes") != POI_FILE.stat().st_size:
        print("ERROR: POI database size does not match the metadata.", file=sys.stderr)
        return 4
    poi_digest_builder = hashlib.sha256()
    with POI_FILE.open("rb") as poi_stream:
        for chunk in iter(lambda: poi_stream.read(1024 * 1024), b""):
            poi_digest_builder.update(chunk)
    poi_sha = poi_digest_builder.hexdigest()
    if poi_sha != manifest.get("poiFileSha256"):
        print("ERROR: POI database SHA-256 does not match its manifest.", file=sys.stderr)
        return 5
    if int(manifest.get("poiCount", 0)) < 100 or manifest.get("poiSchemaVersion") != 1:
        print("ERROR: POI count or schema version is invalid.", file=sys.stderr)
        return 4
    if manifest.get("poiIndexVersion") != 3:
        print("ERROR: POI index revision is outdated; regenerate the database with the current extraction rules.", file=sys.stderr)
        return 4
    required_detail_tags = {"addr:*", "phone", "website", "opening_hours", "cuisine"}
    if not required_detail_tags.issubset(set(manifest.get("poiIndexedDetailTags", []))):
        print("ERROR: POI build manifest does not describe the indexed public detail tags.", file=sys.stderr)
        return 4
    # Deep SQLite validation is performed by verify_poi_database.py in the builder;
    # this check protects restored caches with a checksum and expected header.
    import sqlite3
    poi_db = sqlite3.connect(f"file:{POI_FILE}?mode=ro", uri=True)
    try:
        integrity = poi_db.execute("PRAGMA integrity_check").fetchone()[0]
        poi_count = int(poi_db.execute("SELECT COUNT(*) FROM poi").fetchone()[0])
    finally:
        poi_db.close()
    if integrity != "ok" or poi_count != int(manifest.get("poiCount", -1)):
        print("ERROR: POI SQLite integrity or row count is invalid.", file=sys.stderr)
        return 5

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
        f"({MAP_FILE.stat().st_size:,} bytes, SHA-256 {digest[:16]}…, "
        f"{poi_count:,} searchable POIs, language variants en/ru/ka requested)."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
