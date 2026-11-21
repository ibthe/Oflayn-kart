#!/usr/bin/env python3
"""Fail fast if OSM source URLs or the Mapsforge writer build drift out of sync."""
from __future__ import annotations

import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAP_SCRIPT = ROOT / "scripts/build_georgia_map.sh"
WRITER_SCRIPT = ROOT / "scripts/install_mapsforge_writer.sh"
WORKFLOW = ROOT / ".github/workflows/android-ci.yml"
DATA_SOURCES = ROOT / "docs/DATA_SOURCES.md"
SMOKE_FIXTURE = ROOT / "scripts/fixtures/mapsforge-writer-smoke.osm"
BUNDLE_MANIFEST = ROOT / "app/src/main/assets/offline/data-manifest.json"

EXPECTED_MIRROR = "https://download.openstreetmap.fr/extracts/asia/georgia.osm.pbf"
EXPECTED_MIRROR_MD5 = "https://download.openstreetmap.fr/extracts/asia/georgia.osm.pbf.md5"
EXPECTED_GEOFABRIK = "https://download.geofabrik.de/europe/georgia-latest.osm.pbf"
EXPECTED_GEORGIA_BBOX = "41.00,39.95,43.65,46.80"
EXPECTED_SMOKE_BBOX = "41.70,44.81,41.73,44.84"
EXPECTED_PLUGIN_LOADER = "org.mapsforge.map.writer.osmosis.MapFileWriterPluginLoader"


def fail(message: str) -> int:
    print(f"ERROR: {message}", file=sys.stderr)
    return 1


def main() -> int:
    required = (MAP_SCRIPT, WRITER_SCRIPT, WORKFLOW, DATA_SOURCES, SMOKE_FIXTURE, BUNDLE_MANIFEST)
    for path in required:
        if not path.is_file():
            return fail(f"required build file is missing: {path.relative_to(ROOT)}")

    map_script = MAP_SCRIPT.read_text(encoding="utf-8")
    writer_script = WRITER_SCRIPT.read_text(encoding="utf-8")
    workflow = WORKFLOW.read_text(encoding="utf-8")
    data_sources = DATA_SOURCES.read_text(encoding="utf-8")
    try:
        bundle_manifest = json.loads(BUNDLE_MANIFEST.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        return fail(f"the static offline bundle manifest is invalid JSON: {exc}")

    if EXPECTED_GEOFABRIK not in map_script:
        return fail("the primary Geofabrik Georgia-country source URL is missing.")
    if EXPECTED_MIRROR not in map_script:
        return fail("the OSM France fallback must use georgia.osm.pbf (not georgia-latest).")
    if EXPECTED_MIRROR_MD5 != EXPECTED_MIRROR + ".md5":
        return fail("internal expected mirror checksum URL does not match the PBF URL.")
    if 'md5_url="${pbf_url}.md5"' not in map_script:
        return fail("the checksum URL must be formed from the exact selected PBF URL.")
    if EXPECTED_MIRROR_MD5 not in data_sources:
        return fail("the documentation must record the exact mirror checksum URL.")
    if "openstreetmap.fr/extracts/asia/georgia-latest.osm.pbf" in map_script:
        return fail("the obsolete mirror filename georgia-latest.osm.pbf is still configured.")

    # Some PBF country extracts (including the mirror used in the failing CI
    # run) do not carry a header bbox. Mapsforge requires an explicit bbox in
    # this case, so always pass the national extent to the writer.
    if f'GEORGIA_BBOX="${{GEORGIA_BBOX:-{EXPECTED_GEORGIA_BBOX}}}"' not in map_script:
        return fail("the Georgia country bbox default is missing or has drifted.")
    if 'bbox="$GEORGIA_BBOX"' not in map_script:
        return fail("the full-country Mapsforge writer command must pass bbox explicitly.")
    if f'bbox={EXPECTED_SMOKE_BBOX}' not in writer_script:
        return fail("the Mapsforge writer smoke test must exercise explicit bbox handling.")

    try:
        bbox_values = [float(value) for value in EXPECTED_GEORGIA_BBOX.split(",")]
    except ValueError:
        return fail("the configured Georgia bbox contains a non-numeric value.")
    if len(bbox_values) != 4:
        return fail("the configured Georgia bbox must contain exactly four coordinates.")
    min_lat, min_lon, max_lat, max_lon = bbox_values
    if not (-90 <= min_lat < max_lat <= 90 and -180 <= min_lon < max_lon <= 180):
        return fail("the configured Georgia bbox coordinates are invalid or in the wrong order.")

    if bundle_manifest.get("mapBoundingBox") != EXPECTED_GEORGIA_BBOX:
        return fail("the static bundle manifest bbox does not match the map build bbox.")
    if bundle_manifest.get("appVersion") != "0.4.7":
        return fail("the static bundle manifest appVersion must match this source package (0.4.7).")
    if "map_bbox" not in map_script or '"mapBoundingBox": map_bbox' not in map_script:
        return fail("the generated map manifest must record the exact bbox used by Map Writer.")

    # The published Maven artifact avoids configuring all Android/sample modules
    # from the upstream multi-project. The writer loader descriptor is generated
    # explicitly so plugin discovery does not depend on Gradle resource conventions.
    for token in (
        "mavenCentral()",
        "org.mapsforge:mapsforge-map-writer:${writerVersion}",
        "configurations.runtimeClasspath",
        "zipTree(it)",
        EXPECTED_PLUGIN_LOADER,
        "osmosis-plugins.conf",
        "--mapfile-writer",
        "/usr/share/osmosis",
        "/etc/osmosis/plexus.conf",
        "load ${jar_path}",
        "mapsforge-writer-smoke.osm",
        "--read-xml",
    ):
        if token not in writer_script:
            return fail(f"Debian Osmosis writer integration is missing: {token}")
    if "git clone" in writer_script or "mapsforge-map-writer:fatJar" in writer_script:
        return fail("do not build the full upstream Mapsforge multi-project in CI.")
    if "osmosis --version" in writer_script or "osmosis --version" in workflow:
        return fail("Osmosis must not be probed with the unsupported --version task.")
    if "osmosis --help" in writer_script:
        return fail("plugin registration must be tested by executing the writer task, not generic Osmosis help.")
    try:
        fixture = ET.parse(SMOKE_FIXTURE).getroot()
    except ET.ParseError as exc:
        return fail(f"Mapsforge writer smoke-test fixture is not valid OSM XML: {exc}")
    if fixture.tag != "osm" or not fixture.findall("node") or not fixture.findall("way") or fixture.find("bounds") is None:
        return fail("Mapsforge writer smoke-test fixture must contain OSM bounds, nodes, and a way.")

    # Osmosis' OSM XML reader requires a timestamp on every entity. Keep the
    # smoke fixture representative of a real OSM export so the writer test
    # reaches map generation instead of failing in the XML reader.
    from datetime import datetime
    entities = fixture.findall("node") + fixture.findall("way")
    required_entity_attributes = ("id", "version", "timestamp", "changeset", "uid", "user")
    for index, entity in enumerate(entities, start=1):
        missing = [name for name in required_entity_attributes if not entity.get(name)]
        if missing:
            return fail(
                f"Mapsforge smoke-test entity #{index} ({entity.tag}) lacks required OSM attributes: {', '.join(missing)}."
            )
        timestamp = entity.get("timestamp", "")
        try:
            datetime.fromisoformat(timestamp.replace("Z", "+00:00"))
        except ValueError:
            return fail(f"Mapsforge smoke-test entity #{index} has an invalid ISO-8601 timestamp: {timestamp!r}.")

    if "scripts/verify_map_build_config.py" not in workflow:
        return fail("workflow must run the map builder preflight before the expensive steps.")
    if "continue-on-error: true" in workflow:
        return fail("map preparation must fail fast; do not hide failed prerequisites.")

    print("OK: OSM sources, explicit Georgia bbox, isolated Maven writer, Debian Plexus classpath registration, real writer smoke test, and fail-fast CI are configured.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
