#!/usr/bin/env bash
set -euo pipefail

MAPSFORGE_VERSION="${MAPSFORGE_VERSION:-0.25.0}"
OSM_URL="https://download.geofabrik.de/europe/georgia-latest.osm.pbf"
OSM_MD5_URL="${OSM_URL}.md5"
WORK_DIR="${RUNNER_TEMP:-/tmp}/georgia-map-build"
PBF_FILE="${OSM_PBF_FILE:-${WORK_DIR}/georgia-latest.osm.pbf}"
MD5_FILE="${WORK_DIR}/georgia-latest.osm.pbf.md5"
OUTPUT_FILE="${MAP_OUTPUT_FILE:-$(pwd)/app/src/main/assets/offline/georgia.map}"
OUTPUT_DIR="$(dirname "$OUTPUT_FILE")"

mkdir -p "$WORK_DIR" "$OUTPUT_DIR"

if ! command -v osmosis >/dev/null 2>&1; then
  echo "ERROR: Osmosis is required. Run scripts/install_mapsforge_writer.sh after installing it." >&2
  exit 2
fi

curl --fail --location --retry 3 --retry-delay 2 "$OSM_MD5_URL" --output "$MD5_FILE"
EXPECTED_MD5="$(awk 'NR == 1 { print $1 }' "$MD5_FILE")"
if [[ ! "$EXPECTED_MD5" =~ ^[0-9a-fA-F]{32}$ ]]; then
  echo "ERROR: Could not read Geofabrik MD5 checksum." >&2
  cat "$MD5_FILE" >&2
  exit 3
fi

if [[ ! -s "$PBF_FILE" ]]; then
  curl --fail --location --retry 3 --retry-delay 2 "$OSM_URL" --output "$PBF_FILE"
fi
ACTUAL_MD5="$(md5sum "$PBF_FILE" | awk '{ print $1 }')"
if [[ "${ACTUAL_MD5,,}" != "${EXPECTED_MD5,,}" ]]; then
  echo "ERROR: Geofabrik PBF checksum mismatch." >&2
  echo "Expected: $EXPECTED_MD5" >&2
  echo "Actual:   $ACTUAL_MD5" >&2
  exit 4
fi

# The source PBF for Georgia is below 200 MB. HD mode uses disk-backed working
# storage to avoid depending on a large, unpredictable RAM allocation.
export JAVACMD_OPTIONS="${JAVACMD_OPTIONS:--Xms512m -Xmx4g}"
rm -f "$OUTPUT_FILE"
osmosis \
  --rb file="$PBF_FILE" \
  --mw file="$OUTPUT_FILE" \
      type=hd \
      map-start-position=41.7151,44.8271 \
      map-start-zoom=9 \
      preferred-languages=en,ru,ka \
      threads=1 \
      progress-logs=true

if [[ ! -s "$OUTPUT_FILE" || "$(stat -c '%s' "$OUTPUT_FILE")" -lt 1048576 ]]; then
  echo "ERROR: Mapsforge writer produced no usable map file." >&2
  exit 5
fi

MAP_SHA256="$(sha256sum "$OUTPUT_FILE" | awk '{ print $1 }')"
MAP_SIZE="$(stat -c '%s' "$OUTPUT_FILE")"
RETRIEVED_AT="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
python3 - "$OUTPUT_DIR/georgia-map-build.json" "$MAP_SHA256" "$MAP_SIZE" "$EXPECTED_MD5" "$RETRIEVED_AT" "$MAPSFORGE_VERSION" <<'PY'
import json
import sys
from pathlib import Path

path, map_sha256, map_size, pbf_md5, retrieved_at, writer_version = sys.argv[1:]
manifest = {
    "datasetId": "georgia-openstreetmap-mapsforge",
    "datasetVersion": retrieved_at,
    "retrievedAtUtc": retrieved_at,
    "source": "Geofabrik OpenStreetMap extract: Georgia (country)",
    "sourceUrl": "https://download.geofabrik.de/europe/georgia-latest.osm.pbf",
    "sourcePbfMd5": pbf_md5,
    "mapFile": "georgia.map",
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

echo "Generated $OUTPUT_FILE ($MAP_SIZE bytes, SHA-256 $MAP_SHA256)."
