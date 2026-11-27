#!/usr/bin/env bash
set -euo pipefail

MAPSFORGE_VERSION="${MAPSFORGE_VERSION:-0.25.0}"
# Georgia country extent with a small safety margin, in Mapsforge order:
# minLatitude,minLongitude,maxLatitude,maxLongitude.
# The OpenStreetMap France PBF mirror may omit the PBF header bounding box;
# pass this explicitly so Mapsforge does not depend on source header metadata.
GEORGIA_BBOX="${GEORGIA_BBOX:-41.00,39.95,43.65,46.80}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK_DIR="${RUNNER_TEMP:-/tmp}/georgia-map-build"
PBF_FILE="${OSM_PBF_FILE:-${ROOT}/data/cache/georgia-latest.osm.pbf}"
PBF_META="${OSM_PBF_META_FILE:-${ROOT}/data/cache/georgia-source.json}"
OUTPUT_FILE="${MAP_OUTPUT_FILE:-${ROOT}/app/src/main/assets/offline/georgia.map}"
OUTPUT_DIR="$(dirname "$OUTPUT_FILE")"
MAP_TEMP="${OUTPUT_FILE}.new"
MANIFEST_FILE="${OUTPUT_DIR}/georgia-map-build.json"
MANIFEST_TEMP="${MANIFEST_FILE}.new"
POI_TEMP="${OUTPUT_DIR}/georgia-poi.sqlite.new"
POI_SUMMARY="${WORK_DIR}/georgia-poi-summary.json"

mkdir -p "$WORK_DIR" "$OUTPUT_DIR"

# Temporary outputs live next to the final assets, which Gradle packages into the APK.
# Remove them on any exit (failure, signal) so a failed refresh cannot leak half-written
# *.new files into the APK when CI falls back to a previously cached verified map.
cleanup_temp_outputs() {
  rm -f "$MAP_TEMP" "$MANIFEST_TEMP" "$POI_TEMP"
}
trap cleanup_temp_outputs EXIT
trap 'exit 143' TERM
trap 'exit 130' INT

if [[ ! "$GEORGIA_BBOX" =~ ^[0-9]+([.][0-9]+)?,[0-9]+([.][0-9]+)?,[0-9]+([.][0-9]+)?,[0-9]+([.][0-9]+)?$ ]]; then
  echo "ERROR: GEORGIA_BBOX must be minLat,minLon,maxLat,maxLon with decimal degrees; got: ${GEORGIA_BBOX}" >&2
  exit 2
fi
IFS=',' read -r BBOX_MIN_LAT BBOX_MIN_LON BBOX_MAX_LAT BBOX_MAX_LON <<< "$GEORGIA_BBOX"
if ! awk -v min_lat="$BBOX_MIN_LAT" -v min_lon="$BBOX_MIN_LON" \
       -v max_lat="$BBOX_MAX_LAT" -v max_lon="$BBOX_MAX_LON" \
       'BEGIN { exit !(min_lat >= -90 && max_lat <= 90 && min_lon >= -180 && max_lon <= 180 && min_lat < max_lat && min_lon < max_lon) }'; then
  echo "ERROR: GEORGIA_BBOX coordinates are out of range or not ordered correctly." >&2
  exit 2
fi

if ! command -v osmosis >/dev/null 2>&1; then
  echo "ERROR: Osmosis is required. Run scripts/install_mapsforge_writer.sh after installing it." >&2
  exit 2
fi

# The country extract is downloaded and checksum-verified in a separate
# workflow step. That step saves the PBF to Actions cache before the expensive
# POI extraction and Mapsforge writer run, so cancellation never forces a
# second 98 MB download.
if [[ ! -s "$PBF_FILE" || ! -s "$PBF_META" ]]; then
  echo "ERROR: A verified Georgia OSM PBF is required. Run scripts/prepare_georgia_pbf.sh first." >&2
  exit 3
fi

IFS=$'\t' read -r SOURCE_PROVIDER SOURCE_LABEL SOURCE_URL EXPECTED_MD5 < <(
  python3 - "$PBF_META" <<'PY'
import json, sys
from pathlib import Path
p = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
for key in ("sourceProvider", "sourceLabel", "sourceUrl", "sourcePbfMd5"):
    if not isinstance(p.get(key), str) or not p[key]:
        raise SystemExit(f"Missing {key} in verified PBF metadata")
print("\t".join(p[k] for k in ("sourceProvider", "sourceLabel", "sourceUrl", "sourcePbfMd5")))
PY
)
case "$SOURCE_PROVIDER|$SOURCE_URL" in
  "geofabrik|https://download.geofabrik.de/europe/georgia-latest.osm.pbf" | \
  "openstreetmap-fr|https://download.openstreetmap.fr/extracts/asia/georgia.osm.pbf") ;;
  *) echo "ERROR: PBF metadata references an unsupported source." >&2; exit 3 ;;
esac
if [[ ! "$EXPECTED_MD5" =~ ^[0-9a-fA-F]{32}$ ]]; then
  echo "ERROR: PBF metadata contains an invalid MD5 checksum." >&2
  exit 3
fi
ACTUAL_MD5="$(md5sum "$PBF_FILE" | awk '{ print $1 }' | tr '[:upper:]' '[:lower:]')"
EXPECTED_MD5="$(printf '%s' "$EXPECTED_MD5" | tr '[:upper:]' '[:lower:]')"
if [[ "$ACTUAL_MD5" != "$EXPECTED_MD5" ]]; then
  echo "ERROR: Cached Georgia PBF checksum mismatch; run scripts/prepare_georgia_pbf.sh again." >&2
  exit 3
fi
echo "[map] Using verified ${SOURCE_PROVIDER} PBF (${ACTUAL_MD5})."
# HD mode uses disk-backed working storage to reduce RAM requirements.
export JAVACMD_OPTIONS="${JAVACMD_OPTIONS:--Xms512m -Xmx4g}"
rm -f "$MAP_TEMP" "$MANIFEST_TEMP" "$POI_TEMP" "$POI_SUMMARY"

# Build the searchable local POI index from the exact same verified OSM PBF.
# PyOsmium uses a disk-backed location index to keep runner memory bounded.
echo "[poi] Starting local POI extraction from the verified country PBF."
python3 scripts/build_poi_database.py \
  "$PBF_FILE" "$POI_TEMP" \
  --bbox "$GEORGIA_BBOX" \
  --summary "$POI_SUMMARY"
python3 scripts/verify_poi_database.py "$POI_TEMP" "$POI_SUMMARY"

echo "[map] Starting Mapsforge writer for the full Georgia bounding box."
osmosis \
  --rb file="$PBF_FILE" \
  --mw file="$MAP_TEMP" \
      type=hd \
      bbox="$GEORGIA_BBOX" \
      map-start-position=41.7151,44.8271 \
      map-start-zoom=9 \
      preferred-languages=en,ru,ka \
      threads=1 \
      progress-logs=true

if [[ ! -s "$MAP_TEMP" || "$(stat -c '%s' "$MAP_TEMP")" -lt 1048576 ]]; then
  echo "ERROR: Mapsforge writer produced no usable map file." >&2
  exit 4
fi

MAP_SHA256="$(sha256sum "$MAP_TEMP" | awk '{ print $1 }')"
MAP_SIZE="$(stat -c '%s' "$MAP_TEMP")"
POI_SHA256="$(sha256sum "$POI_TEMP" | awk '{ print $1 }')"
POI_SIZE="$(stat -c '%s' "$POI_TEMP")"
POI_COUNT="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["poiCount"])' "$POI_SUMMARY")"
RETRIEVED_AT="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
python3 - "$MANIFEST_TEMP" "$MAP_SHA256" "$MAP_SIZE" "$EXPECTED_MD5" "$RETRIEVED_AT" "$MAPSFORGE_VERSION" "$SOURCE_PROVIDER" "$SOURCE_LABEL" "$SOURCE_URL" "$GEORGIA_BBOX" "$POI_SHA256" "$POI_SIZE" "$POI_COUNT" <<'PY'
import json
import sys
from pathlib import Path

(path, map_sha256, map_size, pbf_md5, retrieved_at, writer_version,
 source_provider, source_label, source_url, map_bbox,
 poi_sha256, poi_size, poi_count) = sys.argv[1:]
manifest = {
    "datasetId": "georgia-openstreetmap-mapsforge",
    "datasetVersion": retrieved_at,
    "retrievedAtUtc": retrieved_at,
    "source": source_label,
    "sourceProvider": source_provider,
    "sourceUrl": source_url,
    "sourcePbfMd5": pbf_md5,
    "mapFile": "georgia.map",
    "mapBoundingBox": map_bbox,
    "mapBoundingBoxOrder": ["minLatitude", "minLongitude", "maxLatitude", "maxLongitude"],
    "mapFileSha256": map_sha256,
    "mapFileSizeBytes": int(map_size),
    "poiFile": "georgia-poi.sqlite",
    "poiFileSha256": poi_sha256,
    "poiFileSizeBytes": int(poi_size),
    "poiCount": int(poi_count),
    "poiSchemaVersion": 1,
    "poiIndexVersion": 3,
    "poiCategoryCatalog": ["food", "lodging", "tourism", "medical", "transport", "services", "shops", "historic", "nature", "settlement"],
    "poiSearchLanguages": ["en", "ru", "ka"],
    "poiIndexedDetailTags": ["addr:*", "phone", "contact:phone", "telephone", "website", "contact:website", "opening_hours", "cuisine", "wheelchair", "internet_access", "operator", "brand"],
    "poiGeometryCoverage": "OSM nodes, closed ways and multipolygon relations; full-country duplicate and coverage review remains pending.",
    "mapWriter": "Mapsforge Map Writer",
    "mapWriterVersion": writer_version,
    "preferredLanguagesRequested": ["en", "ru", "ka"],
    "languageCoverage": "Only OSM name variants present in the source are written; no automatic translation is performed.",
    "license": "ODbL-1.0",
    "attribution": "© OpenStreetMap contributors",
    "copyrightUrl": "https://www.openstreetmap.org/copyright",
    "runtimeNetworkAccessRequired": False,
}
Path(path).write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
PY

# Replace the previous cached map only after the new map and its metadata exist.
mv "$MAP_TEMP" "$OUTPUT_FILE"
mv "$POI_TEMP" "${OUTPUT_DIR}/georgia-poi.sqlite"
mv "$MANIFEST_TEMP" "$MANIFEST_FILE"

if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  echo "provider=${SOURCE_PROVIDER}" >> "$GITHUB_OUTPUT"
  echo "source-md5=${EXPECTED_MD5}" >> "$GITHUB_OUTPUT"
fi

echo "Generated $OUTPUT_FILE ($MAP_SIZE bytes, SHA-256 $MAP_SHA256) from ${SOURCE_PROVIDER}."
echo "Generated ${OUTPUT_DIR}/georgia-poi.sqlite ($POI_SIZE bytes, $POI_COUNT POIs, SHA-256 $POI_SHA256)."
