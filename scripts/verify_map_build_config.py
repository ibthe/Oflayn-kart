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
DEM_BUILDER = ROOT / "scripts/build_dem.py"
DEM_VERIFIER = ROOT / "scripts/verify_dem.py"
DEM_TESTS = ROOT / "scripts/test_build_dem.py"
APK_VERIFIER = ROOT / "scripts/verify_apk_bundle.py"
CONTOUR_BUILDER = ROOT / "scripts/build_contours.py"
CONTOUR_TESTS = ROOT / "scripts/test_build_contours.py"
CONTOUR_MAP_BUILDER = ROOT / "scripts/build_contours_map.sh"
CONTOUR_MAP_VERIFIER = ROOT / "scripts/verify_contours_map.py"
DEM_REPOSITORY = ROOT / "app/src/main/java/app/georgia/offline/data/DemRepository.kt"
MAP_SURFACE = ROOT / "app/src/main/java/app/georgia/offline/ui/MapsforgeMapSurface.kt"
APP_UI = ROOT / "app/src/main/java/app/georgia/offline/ui/GeorgiaOfflineApp.kt"
TERRAIN_REPOSITORY = ROOT / "app/src/main/java/app/georgia/offline/data/TerrainRepository.kt"
CONTOUR_THEME_LIGHT = ROOT / "app/src/main/assets/themes/oflayn-contours-light.xml"
CONTOUR_THEME_DARK = ROOT / "app/src/main/assets/themes/oflayn-contours-dark.xml"
ANDROID_MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
TOOLS_SCREEN = ROOT / "app/src/main/java/app/georgia/offline/ui/ToolsScreen.kt"
MAP_ZOOM_POLICY = ROOT / "app/src/main/java/app/georgia/offline/ui/MapZoomPolicy.kt"
MAP_ZOOM_TESTS = ROOT / "app/src/test/java/app/georgia/offline/ui/MapZoomPolicyTest.kt"
ROAD_FORMAT = ROOT / "scripts/road_graph_format.py"
ROAD_GRAPH_READER = ROOT / "app/src/main/java/app/georgia/offline/routing/RoadGraph.kt"
ROUTING_REPOSITORY = ROOT / "app/src/main/java/app/georgia/offline/routing/RoutingRepository.kt"
ROAD_ROUTER = ROOT / "app/src/main/java/app/georgia/offline/routing/Router.kt"
ROUTING_PROFILE = ROOT / "app/src/main/java/app/georgia/offline/routing/RoutingProfile.kt"
ROAD_TESTS = ROOT / "scripts/test_build_road_graph.py"
ROAD_TURN_SMOKE = ROOT / "scripts/test_road_turn_restriction_smoke.py"
ROAD_TURN_FIXTURE = ROOT / "scripts/fixtures/road-turn-restriction-smoke.osm"
ROUTING_PROFILE_TESTS = ROOT / "app/src/test/java/app/georgia/offline/routing/RoutingProfileTest.kt"
TINY_ROAD_GRAPH = ROOT / "app/src/test/resources/tiny-roads.bin"
TINY_TURN_GRAPH = ROOT / "app/src/test/resources/tiny-turns.bin"
TINY_ONLY_TURN_GRAPH = ROOT / "app/src/test/resources/tiny-only-turns.bin"
GUIDEBOOK_REPOSITORY = ROOT / "app/src/main/java/app/georgia/offline/data/GuidebookRepository.kt"
GUIDEBOOK_TESTS = ROOT / "app/src/test/java/app/georgia/offline/GuidebookRepositoryTest.kt"
GUIDEBOOK_SOURCES = ROOT / "docs/GUIDEBOOK_CONTENT_SOURCES.md"
SAFETY_REPOSITORY = ROOT / "app/src/main/java/app/georgia/offline/data/SafetyChecklistRepository.kt"
EMERGENCY_CONTACT_REPOSITORY = ROOT / "app/src/main/java/app/georgia/offline/data/EmergencyContactRepository.kt"
SAFETY_TESTS = ROOT / "app/src/androidTest/java/app/georgia/offline/SafetyChecklistRepositoryTest.kt"
SAFETY_SOURCES = ROOT / "docs/SAFETY_CONTENT_SOURCES.md"
APP_FLOW_UI_TESTS = ROOT / "app/src/androidTest/java/app/georgia/offline/AppFlowUiTest.kt"
MAP_FILE_REPOSITORY = ROOT / "app/src/main/java/app/georgia/offline/data/MapFileRepository.kt"
MAP_STORAGE_TESTS = ROOT / "app/src/androidTest/java/app/georgia/offline/MapStorageTest.kt"
RASTER_PACKAGE_REPOSITORY = ROOT / "app/src/main/java/app/georgia/offline/data/RasterPackageRepository.kt"
RASTER_PACKAGE_TESTS = ROOT / "app/src/androidTest/java/app/georgia/offline/RasterPackageRepositoryTest.kt"
RASTER_PACK_DOCS = ROOT / "docs/OFLAYN_RASTER_PACK_FORMAT.md"
MAP_SURFACE = ROOT / "app/src/main/java/app/georgia/offline/ui/MapsforgeMapSurface.kt"

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
    required = (MAP_SCRIPT, PBF_PREP_SCRIPT, WRITER_SCRIPT, WORKFLOW, DATA_SOURCES, SMOKE_FIXTURE, BUNDLE_MANIFEST, POI_BUILDER, POI_VERIFIER, POI_TESTS, POI_SMOKE_TEST, POI_SMOKE_FIXTURE, PBF_CACHE_TEST, APP_BUILD, POI_REPOSITORY, POI_UI, DEM_BUILDER, DEM_VERIFIER, DEM_TESTS, APK_VERIFIER, DEM_REPOSITORY, MAP_SURFACE, CONTOUR_BUILDER, CONTOUR_TESTS, CONTOUR_MAP_BUILDER, CONTOUR_MAP_VERIFIER, TERRAIN_REPOSITORY, CONTOUR_THEME_LIGHT, CONTOUR_THEME_DARK, ANDROID_MANIFEST, TOOLS_SCREEN, MAP_ZOOM_POLICY, MAP_ZOOM_TESTS, ROAD_FORMAT, ROAD_GRAPH_READER, ROUTING_REPOSITORY, ROAD_ROUTER, ROUTING_PROFILE, ROAD_TESTS, ROAD_TURN_SMOKE, ROAD_TURN_FIXTURE, ROUTING_PROFILE_TESTS, TINY_ROAD_GRAPH, TINY_TURN_GRAPH, TINY_ONLY_TURN_GRAPH, GUIDEBOOK_REPOSITORY, GUIDEBOOK_TESTS, GUIDEBOOK_SOURCES, SAFETY_REPOSITORY, EMERGENCY_CONTACT_REPOSITORY, SAFETY_TESTS, SAFETY_SOURCES, APP_FLOW_UI_TESTS, MAP_FILE_REPOSITORY, MAP_STORAGE_TESTS, RASTER_PACKAGE_REPOSITORY, RASTER_PACKAGE_TESTS, RASTER_PACK_DOCS, MAP_SURFACE, APP_UI)
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
    poi_builder = POI_BUILDER.read_text(encoding="utf-8")
    poi_tests = POI_TESTS.read_text(encoding="utf-8")
    poi_verifier = POI_VERIFIER.read_text(encoding="utf-8")
    dem_builder = DEM_BUILDER.read_text(encoding="utf-8")
    dem_verifier = DEM_VERIFIER.read_text(encoding="utf-8")
    dem_tests = DEM_TESTS.read_text(encoding="utf-8")
    apk_verifier = APK_VERIFIER.read_text(encoding="utf-8")
    dem_repository = DEM_REPOSITORY.read_text(encoding="utf-8")
    map_surface = MAP_SURFACE.read_text(encoding="utf-8")
    app_ui = APP_UI.read_text(encoding="utf-8")
    contour_builder = CONTOUR_BUILDER.read_text(encoding="utf-8")
    contour_tests = CONTOUR_TESTS.read_text(encoding="utf-8")
    contour_map_builder = CONTOUR_MAP_BUILDER.read_text(encoding="utf-8")
    contour_map_verifier = CONTOUR_MAP_VERIFIER.read_text(encoding="utf-8")
    terrain_repository = TERRAIN_REPOSITORY.read_text(encoding="utf-8")
    android_manifest = ANDROID_MANIFEST.read_text(encoding="utf-8")
    tools_screen = TOOLS_SCREEN.read_text(encoding="utf-8")
    map_zoom_policy = MAP_ZOOM_POLICY.read_text(encoding="utf-8")
    map_zoom_tests = MAP_ZOOM_TESTS.read_text(encoding="utf-8")
    road_format = ROAD_FORMAT.read_text(encoding="utf-8")
    road_graph_reader = ROAD_GRAPH_READER.read_text(encoding="utf-8")
    routing_repository = ROUTING_REPOSITORY.read_text(encoding="utf-8")
    road_router = ROAD_ROUTER.read_text(encoding="utf-8")
    routing_profile = ROUTING_PROFILE.read_text(encoding="utf-8")
    road_tests = ROAD_TESTS.read_text(encoding="utf-8")
    road_turn_smoke = ROAD_TURN_SMOKE.read_text(encoding="utf-8")
    routing_profile_tests = ROUTING_PROFILE_TESTS.read_text(encoding="utf-8")
    tiny_road_graph = TINY_ROAD_GRAPH.read_bytes()
    tiny_turn_graph = TINY_TURN_GRAPH.read_bytes()
    tiny_only_turn_graph = TINY_ONLY_TURN_GRAPH.read_bytes()
    guidebook_repository = GUIDEBOOK_REPOSITORY.read_text(encoding="utf-8")
    guidebook_tests = GUIDEBOOK_TESTS.read_text(encoding="utf-8")
    guidebook_sources = GUIDEBOOK_SOURCES.read_text(encoding="utf-8")
    safety_repository = SAFETY_REPOSITORY.read_text(encoding="utf-8")
    emergency_contact_repository = EMERGENCY_CONTACT_REPOSITORY.read_text(encoding="utf-8")
    safety_tests = SAFETY_TESTS.read_text(encoding="utf-8")
    safety_sources = SAFETY_SOURCES.read_text(encoding="utf-8")
    app_flow_ui_tests = APP_FLOW_UI_TESTS.read_text(encoding="utf-8")
    map_file_repository = MAP_FILE_REPOSITORY.read_text(encoding="utf-8")
    map_storage_tests = MAP_STORAGE_TESTS.read_text(encoding="utf-8")
    raster_package_repository = RASTER_PACKAGE_REPOSITORY.read_text(encoding="utf-8")
    raster_package_tests = RASTER_PACKAGE_TESTS.read_text(encoding="utf-8")
    raster_pack_docs = RASTER_PACK_DOCS.read_text(encoding="utf-8")
    map_surface = MAP_SURFACE.read_text(encoding="utf-8")
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
    if bundle_manifest.get("roadGraphFormatVersion") != 3:
        return fail("the static bundle manifest must require turn-aware road graph format version 3.")
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
    if "poi-v4-" not in workflow:
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
    for token in ("scripts/test_build_road_graph.py", "scripts/build_road_graph.py", "scripts/verify_road_graph.py", "georgia-roads-v6-"):
        if token not in workflow:
            return fail(f"workflow must build, verify and cache the optional road graph ({token} is missing).")

    # The same verified Copernicus source must produce both local elevation samples
    # and Mapsforge-compatible HGT archives; APK packaging must preserve both.
    for token in ("def encode_hgt", "hgtFile", "hgtSha256", "1201", "ZIP_DEFLATED"):
        if token not in dem_builder:
            return fail(f"DEM builder is missing HGT hillshade generation: {token}.")
    for token in ("hgtSha256", "hgtRawBytes", "1201", "zipfile.ZipFile"):
        if token not in dem_verifier:
            return fail(f"DEM verifier is missing HGT archive validation: {token}.")
    for token in ("encode_hgt", "1201", "dtype=\">i2\""):
        if token not in dem_tests:
            return fail(f"DEM tests are missing HGT format coverage: {token}.")
    for token in ("hgtSha256", "assets/dem/", "1201", "ZipFile"):
        if token not in apk_verifier:
            return fail(f"APK verifier is missing HGT package validation: {token}.")
    if "ensureHillshadeInstalled" not in dem_repository or "MessageDigest" not in dem_repository:
        return fail("the app must install and checksum-verify HGT assets into private storage.")
    for token in ("MemoryCachingHgtReaderTileSource", "DemFolderFS", "MapsforgeThemes.HILLSHADING", "interruptAndDestroy"):
        if token not in map_surface:
            return fail(f"Mapsforge hillshade layer is missing: {token}.")
    if '"zip"' not in app_build or '"gz"' not in app_build:
        return fail("precompressed DEM and HGT assets must be stored without a second compression pass.")
    if "python3 scripts/test_build_dem.py" not in workflow or "python3 scripts/verify_dem.py" not in workflow:
        return fail("CI must run DEM/HGT encoding tests and verify the generated elevation package.")
    if "ensureHillshadeInstalled" not in poi_ui and "ensureHillshadeInstalled" not in (ROOT / "app/src/main/java/app/georgia/offline/ui/GeorgiaOfflineApp.kt").read_text(encoding="utf-8"):
        return fail("the map screen must install HGT assets before enabling natural-style hillshading.")


    for token in ("contourpy.contour_generator", "contour_major", "Copernicus GLO-90", "gzip.decompress"):
        if token not in contour_builder:
            return fail(f"Offline contour generation is missing: {token}.")
    if "python3 scripts/test_build_contours.py" not in workflow or "python3-contourpy" not in workflow:
        return fail("CI must install ContourPy and run the synthetic contour tests.")
    if "extract_tile_contours" not in contour_tests or "no-data handling" not in contour_tests:
        return fail("Contour tests must cover contour geometry and missing DEM cells.")


    if "osmosis --rx" not in contour_map_builder or "--mw" not in contour_map_builder:
        return fail("Contour OSM geometry must be packaged as a separate Mapsforge map.")
    for token in ("mapFileSha256", "runtimeNetworkAccessRequired", "existing-manifest"):
        if token not in contour_map_verifier:
            return fail(f"Contour map verification is missing: {token}.")
    if "build_contours_map.sh" not in workflow or "georgia-contours.map" not in workflow or "Install Osmosis for contour map build" not in workflow:
        return fail("CI must install Osmosis and build/cache the separate contour map before APK assembly.")
    if "mapsforge-georgia-v0.25.0-poi-v4-" not in workflow or "scripts/build_poi_database.py'" not in workflow:
        return fail("POI extraction changes must invalidate the full-country map cache.")
    if "Report full-country POI coverage and deduplication" not in workflow or "category, subcategory, COUNT(*)" not in workflow:
        return fail("CI must report full-country POI category coverage and deduplication counts.")


    for token in ("contoursMapFile", "resolveContourTheme", "contoursDataStoreRef"):
        if token not in map_surface:
            return fail(f"Mapsforge contour overlay is missing: {token}.")
    if "ensureContoursInstalled" not in terrain_repository or "mapFileSha256" not in terrain_repository:
        return fail("Contour map installation must verify the bundled checksum.")
    if "contour_major" not in CONTOUR_THEME_LIGHT.read_text(encoding="utf-8") or "contour_major" not in CONTOUR_THEME_DARK.read_text(encoding="utf-8"):
        return fail("Both light and dark contour themes must render major elevation lines.")
    required_assets = json.loads(BUNDLE_MANIFEST.read_text(encoding="utf-8")).get("requiredAssets", [])
    for asset in ("georgia-contours.map", "georgia-contours-manifest.json"):
        if asset not in required_assets:
            return fail(f"Offline data manifest must require {asset}.")


    for token in ("def deduplicate_pois", "def deduplicate_database", "max_distance_m", "duplicatesRemoved"):
        if token not in poi_builder:
            return fail(f"POI cross-geometry deduplication is missing: {token}.")
    if "test_same_place_node_way_relation_duplicates_merge_without_merging_nearby_businesses" not in poi_tests:
        return fail("POI tests must cover node/way/relation deduplication and nearby distinct businesses.")
    for token in ("relationsWritten", "duplicatesRemoved", "relationsIncluded"):
        if token not in poi_verifier:
            return fail(f"POI database verification is missing relation/dedup metadata: {token}.")
    if "poiDeduplication" not in map_script or "poiDuplicatesRemoved" not in map_script:
        return fail("The generated map manifest must record POI deduplication coverage.")


    if 'android.permission.INTERNET" tools:node="remove"' not in android_manifest:
        return fail("The app must not request INTERNET permission.")
    if 'android.permission.ACCESS_NETWORK_STATE" />' not in android_manifest:
        return fail("The app may read network status but must not gain network traffic access.")
    if "DeviceStatusPanel" not in tools_screen or "BatteryManager" not in tools_screen or "NET_CAPABILITY_VALIDATED" not in tools_screen:
        return fail("Rider tools must expose on-device battery and connectivity diagnostics.")


    if "ROUTE_AUTO_ZOOM_DELAY_MS = 7_000L" not in map_zoom_policy or "speedBasedZoomLevel" not in map_zoom_policy:
        return fail("Route auto-zoom must use the shared seven-second and speed-based zoom policy.")
    for token in ("15f", "35f", "60f", "90f"):
        if token not in map_zoom_policy:
            return fail(f"Speed-based zoom policy is missing threshold {token}.")
    if "routeOverviewWaitsSevenSecondsBeforeCloserZoom" not in map_zoom_tests or "speedZoomPullsBackAtEachRidingSpeedThreshold" not in map_zoom_tests:
        return fail("Map zoom policy regression tests are missing.")


    if "FORMAT_VERSION = 3" not in road_format or "const val FORMAT_VERSION = 3" not in road_graph_reader:
        return fail("Road graph builder and Android reader must agree on turn-aware format version 3.")
    for token in ("TURN_STRUCT", "TURN_NO", "TURN_ONLY", "turn_restrictions", "def turn_allowed"):
        if token not in road_format:
            return fail(f"Road graph format is missing turn restriction support: {token}.")
    if "turnAllowed" not in road_graph_reader or "TURN_RECORD_SIZE = 8" not in road_graph_reader or "graph.turnAllowed" not in road_router:
        return fail("Android routing must enforce incoming/outgoing OSM turn transitions.")
    if "RoadGraph.FORMAT_VERSION" not in routing_repository:
        return fail("RoutingRepository must reinstall the bundled road graph when its format changes.")
    for token in ("test_no_turn_restriction_forces_a_detour", "test_only_turn_restriction_keeps_the_allowed_exit", "test_via_way_is_counted_as_unsupported_not_silently_misapplied", "test_no_entry_and_no_exit_multiple_members_expand_all_pairs", "test_same_way_no_u_turn_blocks_only_the_reverse_arc", "test_same_way_only_u_turn_blocks_continuing_straight"):
        if token not in road_tests:
            return fail(f"OSM turn restriction regression coverage is missing: {token}.")
    for token in ("def _incline_pair", "incline_forward", "incline_backward", "edge_incline_percent"):
        if token not in road_format:
            return fail(f"Road graph format is missing directional incline support: {token}.")
    if "edgeInclinePercent" not in road_graph_reader or "edgeInclinePercent" not in road_router or "inclinePercent" not in routing_profile:
        return fail("Android routing must consume signed incline for the actual travel direction.")
    for token in ("test_incline_values_preserve_forward_and_backward_direction", "test_directional_grade_changes_route_speed", "test_binary_roundtrip_preserves_directional_incline"):
        if token not in road_tests:
            return fail(f"Directional grade regression test is missing: {token}.")
    if "uphillIsSlowerThanDownhillAndFlatForEveryProfile" not in routing_profile_tests:
        return fail("Android routing profile grade-cost regression tests are missing.")

    if "georgia-roads-v6-" not in workflow or 'python3 scripts/verify_road_graph.py "$roads"' not in workflow:
        return fail("CI must invalidate legacy road graphs and validate restored graph format before bundling.")


    if len(tiny_road_graph) < 64 or tiny_road_graph[:4] != b"GRT1" or int.from_bytes(tiny_road_graph[4:8], "little") != 3:
        return fail("Kotlin routing test fixture must use road graph format version 3.")
    for label, fixture in (("no-turn", tiny_turn_graph), ("only-turn", tiny_only_turn_graph)):
        if len(fixture) < 64 or fixture[:4] != b"GRT1" or int.from_bytes(fixture[4:8], "little") != 3:
            return fail(f"Kotlin {label} fixture must use road graph format version 3.")
        if int.from_bytes(fixture[48:52], "little") <= 0:
            return fail(f"Kotlin {label} fixture must contain encoded turn restrictions.")
    if "scripts/test_road_turn_restriction_smoke.py" not in workflow or "collect_ways" not in road_turn_smoke or "PROFILE_MOTORCYCLE" not in road_turn_smoke:
        return fail("CI must execute the PyOsmium OSM relation extraction smoke test.")
    try:
        turn_fixture = ET.parse(ROAD_TURN_FIXTURE).getroot()
    except ET.ParseError as exc:
        return fail(f"OSM turn restriction smoke fixture is invalid XML: {exc}")
    if turn_fixture.tag != "osm" or len(turn_fixture.findall("relation")) != 1:
        return fail("OSM turn restriction smoke fixture must contain exactly one relation.")
    if "restriction" not in [tag.get("k") for tag in turn_fixture.findall("relation")[0].findall("tag")]:
        return fail("OSM turn restriction smoke fixture must include a restriction tag.")
    if "scripts/make_road_graph_fixture.py" not in workflow:
        return fail("CI must regenerate the versioned Kotlin road graph fixtures before unit tests.")

    for token in ("georgia.travel/regions", "apa.gov.ge", "Editorial constraints"):
        if token not in guidebook_sources:
            return fail(f"Guidebook source documentation is missing: {token}.")
    for token in ("data class GuidebookPlace", "data class GuidebookRoute", "data class GuidebookService", "searchQuery"):
        if token not in guidebook_repository:
            return fail(f"Offline guidebook collections are missing: {token}.")
    for token in ("everyRegionHasLinkedPlacesRoutesAndPracticalServiceNotes", "tushetiWarnsAboutAbano"):
        if token not in guidebook_tests:
            return fail(f"Guidebook content regression coverage is missing: {token}.")
    if "GuidebookContent(onSearchPlace" not in POI_UI.read_text(encoding="utf-8"):
        return fail("Guidebook place entries must link back to the offline catalog search.")
    for token in ("incidentSteps", "Сначала безопасность", "Вызови 112", "emergency_contact"):
        if token not in safety_repository:
            return fail(f"Safety guidance or pre-ride checklist is missing: {token}.")
    if "fun save(context: Context" not in emergency_contact_repository or "getSharedPreferences(PREFS" not in emergency_contact_repository:
        return fail("Emergency contact storage must remain local and validated.")
    for token in ("emergencyContactIsValidatedAndStoredOnlyLocally", "expandedChecklistAndIncidentGuidanceAreComplete"):
        if token not in safety_tests:
            return fail(f"Emergency safety instrumentation coverage is missing: {token}.")
    for token in ("112.gov.ge", "2024-first-aid-guidelines", "Annex 7"):
        if token not in safety_sources:
            return fail(f"Safety content source documentation is missing: {token}.")
    for token in ("guidebookAndEmergencyToolsExposeTheirExpandedContent", "Добавить контакт", "Если произошло ДТП"):
        if token not in app_flow_ui_tests:
            return fail(f"Guidebook/emergency emulator UI coverage is missing: {token}.")

    if "internal fun availableStorageOptions" not in map_file_repository:
        return fail("Removable storage discovery must expose a testable volume-probe seam.")
    for token in ("onlyMountedRemovableVolumesAreOfferedAsSdStorage", "failedMoveKeepsTheOriginalMapAndCleansTemporaryCopy"):
        if token not in map_storage_tests:
            return fail(f"Map storage regression coverage is missing: {token}.")

    for token in ("fun importZip", "MAX_TOTAL_BYTES", "isValidImageHeader", "license.isNotBlank", "temporary.renameTo(destination)"):
        if token not in raster_package_repository:
            return fail(f"Offline raster package import safety is missing: {token}.")
    for token in ("importsAndManagesAnAttributedOfflineRasterPackage", "rejectsZipSlipEntriesWithoutWritingOutsideThePackageRoot"):
        if token not in raster_package_tests:
            return fail(f"Raster package instrumentation coverage is missing: {token}.")
    for token in ("oflayn-raster-pack", "tileScheme", "attribution", "license"):
        if token not in raster_pack_docs:
            return fail(f"Offline raster package format documentation is missing: {token}.")
    if "TileStoreLayer" not in map_surface or "rasterEnabled" not in map_surface:
        return fail("The optional offline raster package must render through a local Mapsforge tile store layer.")
    for token in ("Импортировать ZIP", "Спутниковый слой"):
        if token not in app_ui:
            return fail(f"Map UI is missing optional raster controls: {token}.")

    if "continue-on-error: true" in workflow:
        return fail("map preparation must fail fast; do not hide failed prerequisites.")

    print("OK: OSM sources, resumable PBF cache, Mapsforge writer, PyOsmium extraction, local SQLite search, node-via turn-aware routing, DEM/HGT hillshading, guidebook, rider safety, removable storage and optional offline raster packs are configured.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
