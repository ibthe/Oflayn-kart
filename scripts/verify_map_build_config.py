#!/usr/bin/env python3
"""Fail fast if OSM source URLs or the Mapsforge writer build drift out of sync."""
from __future__ import annotations

import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAP_SCRIPT = ROOT / "scripts/build_georgia_map.sh"
PBF_PREP_SCRIPT = ROOT / "scripts/prepare_georgia_pbf.sh"
WRITER_SCRIPT = ROOT / "scripts/install_mapsforge_writer.sh"
WORKFLOW = ROOT / ".github/workflows/android-ci.yml"
DATA_SOURCES = ROOT / "docs/DATA_SOURCES.md"
SMOKE_FIXTURE = ROOT / "scripts/fixtures/mapsforge-writer-smoke.osm"
BUNDLE_MANIFEST = ROOT / "app/src/main/assets/offline/data-manifest.json"
APP_BUILD = ROOT / "app/build.gradle.kts"
POI_REPOSITORY = ROOT / "app/src/main/java/app/georgia/offline/data/PoiRepository.kt"
POI_UI = ROOT / "app/src/main/java/app/georgia/offline/ui/DiscoverScreen.kt"
POI_BUILDER = ROOT / "scripts/build_poi_database.py"
POI_VERIFIER = ROOT / "scripts/verify_poi_database.py"
POI_TESTS = ROOT / "scripts/test_build_poi_database.py"
POI_SMOKE_TEST = ROOT / "scripts/test_poi_extractor_smoke.py"
POI_SMOKE_FIXTURE = ROOT / "scripts/fixtures/poi-smoke.osm"

EXPECTED_MIRROR = "https://download.openstreetmap.fr/extracts/asia/georgia.osm.pbf"
EXPECTED_MIRROR_MD5 = "https://download.openstreetmap.fr/extracts/asia/georgia.osm.pbf.md5"
EXPECTED_GEOFABRIK = "https://download.geofabrik.de/europe/georgia-latest.osm.pbf"
EXPECTED_GEORGIA_BBOX = "41.00,39.95,43.65,46.80"
EXPECTED_SMOKE_BBOX = "41.70,44.81,41.73,44.84"
EXPECTED_PLUGIN_LOADER = "org.mapsforge.map.writer.osmosis.MapFileWriterPluginLoader"
PBF_CACHE_TEST = ROOT / "scripts/test_prepare_georgia_pbf.py"


def fail(message: str) -> int:
    print(f"ERROR: {message}", file=sys.stderr)
    return 1


def main() -> int:
    required = (MAP_SCRIPT, PBF_PREP_SCRIPT, WRITER_SCRIPT, WORKFLOW, DATA_SOURCES, SMOKE_FIXTURE, BUNDLE_MANIFEST, POI_BUILDER, POI_VERIFIER, POI_TESTS, POI_SMOKE_TEST, POI_SMOKE_FIXTURE, PBF_CACHE_TEST, APP_BUILD, POI_REPOSITORY, POI_UI)
    for path in required:
        if not path.is_file():
            return fail(f"required build file is missing: {path.relative_to(ROOT)}")

    map_script = MAP_SCRIPT.read_text(encoding="utf-8")
    pbf_prep_script = PBF_PREP_SCRIPT.read_text(encoding="utf-8")
    poi_builder = POI_BUILDER.read_text(encoding="utf-8")
    poi_verifier = POI_VERIFIER.read_text(encoding="utf-8")
    writer_script = WRITER_SCRIPT.read_text(encoding="utf-8")
    workflow = WORKFLOW.read_text(encoding="utf-8")
    app_build = APP_BUILD.read_text(encoding="utf-8")
    poi_repository = POI_REPOSITORY.read_text(encoding="utf-8")
    poi_ui = POI_UI.read_text(encoding="utf-8")
    data_sources = DATA_SOURCES.read_text(encoding="utf-8")
    try:
        bundle_manifest = json.loads(BUNDLE_MANIFEST.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        return fail(f"the static offline bundle manifest is invalid JSON: {exc}")

    if "cancel-in-progress: false" not in workflow:
        return fail("CI must not cancel an in-progress full map build when another commit is pushed.")
    if "timeout-minutes: 180" not in workflow:
        return fail("CI must allow enough time for the full country map build.")
    if "scripts/prepare_georgia_pbf.sh" not in workflow:
        return fail("CI must prepare the OSM PBF in a separate resumable step.")
    for token in ("data/cache/georgia-latest.osm.pbf", "data/cache/georgia-source.json", "georgia-osm-pbf-v1-"):
        if token not in workflow:
            return fail(f"CI must cache the verified OSM source PBF separately: missing {token}.")
    if "actions/cache/save@v4" not in workflow or "steps.prepare-pbf.outputs.pbf-downloaded == 'true'" not in workflow:
        return fail("CI must save a newly downloaded PBF cache before starting the map generator.")
    if "cancel-in-progress: true" in workflow:
        return fail("cancel-in-progress must remain disabled so a second push cannot kill map generation.")
    if "python3 scripts/test_prepare_georgia_pbf.py" not in workflow:
        return fail("CI must test PBF cache reuse and corrupt-cache rejection.")
    required_steps = (
        "name: Restore verified Georgia OSM PBF",
        "name: Prepare and checksum-verify Georgia OSM PBF",
        "name: Save verified Georgia OSM PBF cache",
        "name: Install Osmosis",
        "name: Build Mapsforge map-writer plugin",
        "name: Generate multilingual offline map of Georgia",
    )
    positions = [workflow.find(step) for step in required_steps]
    if any(position < 0 for position in positions) or positions != sorted(positions):
        return fail("PBF cache restore, prepare, save, and map-generation steps must execute in order.")
    if "sourcePbfMd5" not in pbf_prep_script or "md5sum" not in pbf_prep_script:
        return fail("the PBF preparation script must verify and persist the source checksum.")
    if "PBF_META" not in map_script or "ACTUAL_MD5" not in map_script:
        return fail("the map builder must independently re-verify the cached PBF before processing.")
    if "Trying OSM source" in map_script or "curl_resilient" in map_script:
        return fail("network download must be separate from the CPU-heavy map generation step.")

    if EXPECTED_GEOFABRIK not in pbf_prep_script:
        return fail("the primary Geofabrik Georgia-country source URL is missing.")
    if EXPECTED_MIRROR not in pbf_prep_script:
        return fail("the OSM France fallback must use georgia.osm.pbf (not georgia-latest).")
    if EXPECTED_MIRROR_MD5 != EXPECTED_MIRROR + ".md5":
        return fail("internal expected mirror checksum URL does not match the PBF URL.")
    if 'md5_url="${pbf_url}.md5"' not in pbf_prep_script:
        return fail("the checksum URL must be formed from the exact selected PBF URL.")
    if EXPECTED_MIRROR_MD5 not in data_sources:
        return fail("the documentation must record the exact mirror checksum URL.")
    if "openstreetmap.fr/extracts/asia/georgia-latest.osm.pbf" in pbf_prep_script:
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
    if bundle_manifest.get("appVersion") != "0.7.0":
        return fail("the static bundle manifest appVersion must match this source package (0.7.0).")
    if 'versionName = "0.7.0"' not in app_build or "versionCode = 23" not in app_build:
        return fail("the Android Gradle version must be 0.7.0 (versionCode 23).")
    if "PoiRepository.ensureInstalled" not in poi_ui or "PoiRepository.search" not in poi_ui or "poi.detailLines()" not in poi_ui:
        return fail("the Discover screen must install/search the local POI database and render available detail tags.")
    for token in ("poiFileSha256", "SQLiteDatabase.OPEN_READONLY", "instr(search_text, ?)", "nameEn", "nameRu", "nameKa", "fun detailLines()", "tagsJson"):
        if token not in poi_repository:
            return fail(f"PoiRepository is missing local multilingual search behavior: {token}.")
    if '"sqlite"' not in app_build:
        return fail("SQLite POI assets must be stored uncompressed in the APK.")
    if "georgia-poi.sqlite" not in bundle_manifest.get("requiredAssets", []):
        return fail("the static bundle manifest must require the local POI database asset.")
    if not {"addr:*", "phone", "website", "opening_hours", "cuisine"}.issubset(set(bundle_manifest.get("poiIndexedDetailTags", []))):
        return fail("the static POI manifest must describe its searchable practical detail tags.")
    if bundle_manifest.get("poiDatabase", {}).get("indexVersion") != 3:
        return fail("the static POI manifest must require search index revision 3.")
    if "poiIndexVersion\": 3" not in map_script:
        return fail("the generated POI build manifest must identify search index revision 3.")
    if "poi-v3-" not in workflow:
        return fail("the CI cache namespace must be bumped so older POI indexes are not reused.")
    if "python3 scripts/build_poi_database.py" not in map_script or "python3 scripts/verify_poi_database.py" not in map_script:
        return fail("the full country map build must generate and verify the POI database from the same PBF.")
    if "sparse_file_array" not in poi_builder or "locations=True" not in poi_builder:
        return fail("the PyOsmium extractor must use a disk-backed sparse_file_array OSM node-location index for way POIs.")
    if "dense_mmap_array" in poi_builder.replace("dense_mmap_array reserves", ""):
        return fail("dense_mmap_array exhausts runner memory/disk on a country PBF with sparse node IDs; do not use it.")
    if "poiFileSha256" not in map_script or "poiCount" not in map_script:
        return fail("the generated map manifest must record POI checksum and row count.")
    if "python3-pyosmium" not in workflow or "scripts/test_build_poi_database.py" not in workflow:
        return fail("CI must install PyOsmium and run the standalone POI extractor tests.")
    if "ProgressReporter" not in poi_builder or "elapsed=" not in poi_builder:
        return fail("the PyOsmium extractor must emit periodic progress and a final scan summary.")
    poi_test_source = POI_TESTS.read_text(encoding="utf-8")
    if "test_progress_reporter_emits_entity_heartbeat" not in poi_test_source:
        return fail("POI tests must cover progress logging by entity count.")
    if "test_progress_reporter_emits_time_heartbeat_and_final_summary" not in poi_test_source:
        return fail("POI tests must cover time-based progress and the final summary.")
    if "sudo apt-get install --yes osmosis python3-pyosmium" not in workflow:
        return fail("CI must install the Ubuntu package python3-pyosmium alongside Osmosis.")
    if "python3-osmium" in workflow:
        return fail("CI must use the current Ubuntu package name python3-pyosmium, not python3-osmium.")
    if "dpkg-query -W -f='${Version}' python3-pyosmium" not in workflow:
        return fail("CI must log the installed PyOsmium package version for diagnostics.")
    if "scripts/test_poi_extractor_smoke.py" not in workflow:
        return fail("CI must run an actual PyOsmium OSM-XML extraction smoke test after installing the binding.")
    try:
        poi_smoke = ET.parse(POI_SMOKE_FIXTURE).getroot()
    except ET.ParseError as exc:
        return fail(f"POI smoke-test fixture is not valid OSM XML: {exc}")
    if poi_smoke.tag != "osm" or not poi_smoke.findall("node") or not poi_smoke.findall("way"):
        return fail("POI smoke fixture must include nodes and a closed way.")
    poi_way = poi_smoke.find("way")
    if poi_way is None or len(poi_way.findall("nd")) < 4:
        return fail("POI smoke fixture must include a closed way with locations for centroid calculation.")
    for index, entity in enumerate(poi_smoke.findall("node") + poi_smoke.findall("way"), start=1):
        if not entity.get("timestamp"):
            return fail(f"POI smoke fixture entity #{index} lacks a timestamp.")
    poi_refs = [nd.get("ref") for nd in poi_way.findall("nd")]
    if poi_refs[0] != poi_refs[-1]:
        return fail("POI smoke fixture way must be closed so the centroid extraction path is tested.")
    for expected_path in ("georgia-poi.sqlite",):
        if expected_path not in workflow:
            return fail(f"workflow cache and APK checks must include {expected_path}.")
    if "POI SQLite database" not in poi_verifier and "POI" not in poi_verifier:
        return fail("POI SQLite validation script is missing its required database checks.")
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
    for token in ("scripts/test_build_road_graph.py", "scripts/build_road_graph.py", "scripts/verify_road_graph.py", "georgia-roads-v2-"):
        if token not in workflow:
            return fail(f"workflow must build, verify and cache the optional road graph ({token} is missing).")
    if "continue-on-error: true" in workflow:
        return fail("map preparation must fail fast; do not hide failed prerequisites.")

    print("OK: OSM sources, resumable PBF cache, non-canceling CI concurrency, explicit Georgia bbox, Mapsforge writer, PyOsmium extraction, local SQLite search, multilingual names and APK assets are configured.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
