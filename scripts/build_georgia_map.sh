#!/usr/bin/env bash
set -euo pipefail

MAPSFORGE_VERSION="${MAPSFORGE_VERSION:-0.25.0}"
# Georgia country extent with a small safety margin, in Mapsforge order:
# minLatitude,minLongitude,maxLatitude,maxLongitude.
# The OpenStreetMap France PBF mirror may omit the PBF header bounding box;
# pass this explicitly so Mapsforge does not depend on source header metadata.
GEORGIA_BBOX="${GEORGIA_BBOX:-41.00,39.95,43.65,46.80}"
WORK_DIR="${RUNNER_TEMP:-/tmp}/georgia-map-build"
PBF_FILE="${OSM_PBF_FILE:-${WORK_DIR}/georgia-latest.osm.pbf}"
OUTPUT_FILE="${MAP_OUTPUT_FILE:-$(pwd)/app/src/main/assets/offline/georgia.map}"
OUTPUT_DIR="$(dirname "$OUTPUT_FILE")"
MAP_TEMP="${OUTPUT_FILE}.new"
MANIFEST_FILE="${OUTPUT_DIR}/georgia-map-build.json"
MANIFEST_TEMP="${MANIFEST_FILE}.new"

mkdir -p "$WORK_DIR" "$OUTPUT_DIR"

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

# Geofabrik is the preferred current source. If GitHub-hosted runners cannot
# connect to it, use the documented OSM France country extract mirror. The
# mirror currently classifies Georgia under Asia; both sources contain the
# same country (not the US state of Georgia).
SOURCE_CANDIDATES=(
  "geofabrik|Geofabrik OpenStreetMap extract: Georgia (country)|https://download.geofabrik.de/europe/georgia-latest.osm.pbf"
  "openstreetmap-fr|OpenStreetMap France mirror: Georgia (country)|https://download.openstreetmap.fr/extracts/asia/georgia.osm.pbf"
)

curl_resilient() {
  curl --fail --location --show-error \
    --connect-timeout 25 \
    --max-time 1800 \
    --retry 6 \
    --retry-all-errors \
    --retry-delay 8 \
    --retry-max-time 420 \
    --user-agent "GeorgiaOffline-map-builder/0.4.7 (OpenStreetMap data build)" \
    "$@"
}

SOURCE_PROVIDER=""
SOURCE_LABEL=""
SOURCE_URL=""
EXPECTED_MD5=""
MD5_FILE="${WORK_DIR}/georgia-source.md5"
PBF_PART="${PBF_FILE}.part"

for candidate in "${SOURCE_CANDIDATES[@]}"; do
  IFS='|' read -r provider label pbf_url <<< "$candidate"
  md5_url="${pbf_url}.md5"
  rm -f "$MD5_FILE" "$PBF_PART"

  echo "Trying OSM source: ${label}"
  if ! curl_resilient "$md5_url" --output "$MD5_FILE"; then
    echo "WARNING: Could not retrieve checksum from ${provider}; trying next source." >&2
    continue
  fi

  candidate_md5="$(awk 'NR == 1 { print $1 }' "$MD5_FILE" | tr '[:upper:]' '[:lower:]')"
  if [[ ! "$candidate_md5" =~ ^[0-9a-f]{32}$ ]]; then
    echo "WARNING: Invalid MD5 response from ${provider}:" >&2
    cat "$MD5_FILE" >&2 || true
    continue
  fi

  if ! curl_resilient "$pbf_url" --output "$PBF_PART"; then
    echo "WARNING: Could not download PBF from ${provider}; trying next source." >&2
    rm -f "$PBF_PART"
    continue
  fi

  if [[ ! -s "$PBF_PART" || "$(stat -c '%s' "$PBF_PART")" -lt 10485760 ]]; then
    echo "WARNING: Download from ${provider} is unexpectedly small; trying next source." >&2
    rm -f "$PBF_PART"
    continue
  fi

  actual_md5="$(md5sum "$PBF_PART" | awk '{ print $1 }' | tr '[:upper:]' '[:lower:]')"
  if [[ "$actual_md5" != "$candidate_md5" ]]; then
    echo "WARNING: MD5 mismatch from ${provider}; trying next source." >&2
    echo "Expected: ${candidate_md5}; actual: ${actual_md5}" >&2
    rm -f "$PBF_PART"
    continue
  fi

  SOURCE_PROVIDER="$provider"
  SOURCE_LABEL="$label"
  SOURCE_URL="$pbf_url"
  EXPECTED_MD5="$candidate_md5"
  mv "$PBF_PART" "$PBF_FILE"
  echo "Using verified OSM source: ${SOURCE_PROVIDER} (${SOURCE_URL})"
  break
done

if [[ -z "$SOURCE_PROVIDER" || ! -s "$PBF_FILE" ]]; then
  echo "ERROR: Could not download and verify Georgia OSM PBF from any configured source." >&2
  echo "Tried Geofabrik and the OpenStreetMap France mirror (stable georgia.osm.pbf filename). Check provider availability and retry the workflow." >&2
  exit 3
fi

# HD mode uses disk-backed working storage to reduce RAM requirements.
export JAVACMD_OPTIONS="${JAVACMD_OPTIONS:--Xms512m -Xmx4g}"
rm -f "$MAP_TEMP" "$MANIFEST_TEMP"
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
RETRIEVED_AT="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
python3 - "$MANIFEST_TEMP" "$MAP_SHA256" "$MAP_SIZE" "$EXPECTED_MD5" "$RETRIEVED_AT" "$MAPSFORGE_VERSION" "$SOURCE_PROVIDER" "$SOURCE_LABEL" "$SOURCE_URL" "$GEORGIA_BBOX" <<'PY'
import json
import sys
from pathlib import Path

(path, map_sha256, map_size, pbf_md5, retrieved_at, writer_version,
 source_provider, source_label, source_url, map_bbox) = sys.argv[1:]
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
mv "$MANIFEST_TEMP" "$MANIFEST_FILE"

if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  echo "provider=${SOURCE_PROVIDER}" >> "$GITHUB_OUTPUT"
  echo "source-md5=${EXPECTED_MD5}" >> "$GITHUB_OUTPUT"
fi

echo "Generated $OUTPUT_FILE ($MAP_SIZE bytes, SHA-256 $MAP_SHA256) from ${SOURCE_PROVIDER}."
