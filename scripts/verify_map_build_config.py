#!/usr/bin/env python3
"""Fail fast if OSM source URLs or the Mapsforge writer build drift out of sync."""
from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAP_SCRIPT = ROOT / "scripts/build_georgia_map.sh"
WRITER_SCRIPT = ROOT / "scripts/install_mapsforge_writer.sh"
WORKFLOW = ROOT / ".github/workflows/android-ci.yml"
DATA_SOURCES = ROOT / "docs/DATA_SOURCES.md"
SMOKE_FIXTURE = ROOT / "scripts/fixtures/mapsforge-writer-smoke.osm"

EXPECTED_MIRROR = "https://download.openstreetmap.fr/extracts/asia/georgia.osm.pbf"
EXPECTED_MIRROR_MD5 = "https://download.openstreetmap.fr/extracts/asia/georgia.osm.pbf.md5"
EXPECTED_GEOFABRIK = "https://download.geofabrik.de/europe/georgia-latest.osm.pbf"
EXPECTED_PLUGIN_LOADER = "org.mapsforge.map.writer.osmosis.MapFileWriterPluginLoader"


def fail(message: str) -> int:
    print(f"ERROR: {message}", file=sys.stderr)
    return 1


def main() -> int:
    required = (MAP_SCRIPT, WRITER_SCRIPT, WORKFLOW, DATA_SOURCES, SMOKE_FIXTURE)
    for path in required:
        if not path.is_file():
            return fail(f"required build file is missing: {path.relative_to(ROOT)}")

    map_script = MAP_SCRIPT.read_text(encoding="utf-8")
    writer_script = WRITER_SCRIPT.read_text(encoding="utf-8")
    workflow = WORKFLOW.read_text(encoding="utf-8")
    data_sources = DATA_SOURCES.read_text(encoding="utf-8")

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

    if "scripts/verify_map_build_config.py" not in workflow:
        return fail("workflow must run the map builder preflight before the expensive steps.")
    if "continue-on-error: true" in workflow:
        return fail("map preparation must fail fast; do not hide failed prerequisites.")

    print("OK: OSM sources, isolated Maven writer, Debian Plexus classpath registration, real writer smoke test, and fail-fast CI are configured.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
