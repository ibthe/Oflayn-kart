#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PBF_FILE="${OSM_PBF_FILE:-${ROOT}/data/cache/georgia-latest.osm.pbf}"
META_FILE="${OSM_PBF_META_FILE:-${ROOT}/data/cache/georgia-source.json}"
FORCE_REFRESH="${FORCE_OSM_REFRESH:-false}"
MIN_PBF_BYTES=10485760

mkdir -p "$(dirname "$PBF_FILE")" "$(dirname "$META_FILE")"

SOURCE_CANDIDATES=(
  "geofabrik|Geofabrik OpenStreetMap extract: Georgia (country)|https://download.geofabrik.de/europe/georgia-latest.osm.pbf"
  "openstreetmap-fr|OpenStreetMap France mirror: Georgia (country)|https://download.openstreetmap.fr/extracts/asia/georgia.osm.pbf"
)

write_output() {
  if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
    printf '%s=%s\n' "$1" "$2" >> "$GITHUB_OUTPUT"
  fi
}

load_and_verify_cache() {
  [[ -s "$PBF_FILE" && -s "$META_FILE" ]] || return 1
  local values provider label url expected actual size
  values="$(python3 - "$META_FILE" <<'PY'
import json, sys
from pathlib import Path
p = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
required = ("sourceProvider", "sourceLabel", "sourceUrl", "sourcePbfMd5")
if any(not isinstance(p.get(k), str) or not p[k] for k in required):
    raise SystemExit(1)
print("\t".join(p[k] for k in required))
PY
)" || return 1
  IFS=$'\t' read -r provider label url expected <<< "$values"
  case "$provider|$url" in
    "geofabrik|https://download.geofabrik.de/europe/georgia-latest.osm.pbf" | \
    "openstreetmap-fr|https://download.openstreetmap.fr/extracts/asia/georgia.osm.pbf") ;;
    *) echo "Cached PBF metadata has an unsupported source; discarding cache." >&2; return 1 ;;
  esac
  [[ "$expected" =~ ^[0-9a-fA-F]{32}$ ]] || return 1
  size="$(stat -c '%s' "$PBF_FILE")"
  (( size >= MIN_PBF_BYTES )) || return 1
  actual="$(md5sum "$PBF_FILE" | awk '{ print $1 }' | tr '[:upper:]' '[:lower:]')"
  expected="$(printf '%s' "$expected" | tr '[:upper:]' '[:lower:]')"
  [[ "$actual" == "$expected" ]] || return 1

  SOURCE_PROVIDER="$provider"
  SOURCE_LABEL="$label"
  SOURCE_URL="$url"
  EXPECTED_MD5="$expected"
  return 0
}

curl_resilient() {
  curl --fail --location --show-error --silent \
    --connect-timeout 25 \
    --max-time 1800 \
    --retry 6 \
    --retry-all-errors \
    --retry-delay 8 \
    --retry-max-time 600 \
    --user-agent "GeorgiaOffline-map-builder/0.5.6 (OpenStreetMap data build)" \
    "$@"
}

SOURCE_PROVIDER=""
SOURCE_LABEL=""
SOURCE_URL=""
EXPECTED_MD5=""

echo "[pbf] Checking for a previously downloaded and checksum-verified Georgia extract."
if [[ "$FORCE_REFRESH" != "true" ]] && load_and_verify_cache; then
  echo "[pbf] Reusing verified ${SOURCE_PROVIDER} extract (${EXPECTED_MD5}); no network download required."
  write_output "provider" "$SOURCE_PROVIDER"
  write_output "source-md5" "$EXPECTED_MD5"
  write_output "pbf-downloaded" "false"
  exit 0
fi

echo "[pbf] No usable cached extract found, or refresh was requested."
PBF_PART="${PBF_FILE}.part"
MD5_FILE="${PBF_FILE}.md5.part"
META_TEMP="${META_FILE}.new"
rm -f "$PBF_PART" "$MD5_FILE" "$META_TEMP" "$PBF_FILE" "$META_FILE"

for candidate in "${SOURCE_CANDIDATES[@]}"; do
  IFS='|' read -r provider label pbf_url <<< "$candidate"
  md5_url="${pbf_url}.md5"
  echo "[pbf] Trying ${label}"
  if ! curl_resilient "$md5_url" --output "$MD5_FILE"; then
    echo "[pbf] WARNING: Cannot retrieve checksum from ${provider}; trying next source." >&2
    rm -f "$MD5_FILE"
    continue
  fi

  candidate_md5="$(awk 'NR == 1 { print $1 }' "$MD5_FILE" | tr '[:upper:]' '[:lower:]')"
  if [[ ! "$candidate_md5" =~ ^[0-9a-f]{32}$ ]]; then
    echo "[pbf] WARNING: Invalid MD5 response from ${provider}:" >&2
    cat "$MD5_FILE" >&2 || true
    rm -f "$MD5_FILE"
    continue
  fi

  echo "[pbf] Downloading country extract from ${provider}."
  if ! curl_resilient --progress-bar "$pbf_url" --output "$PBF_PART"; then
    echo "[pbf] WARNING: Download failed from ${provider}; trying next source." >&2
    rm -f "$PBF_PART"
    continue
  fi

  size="$(stat -c '%s' "$PBF_PART")"
  if [[ ! -s "$PBF_PART" ]] || (( size < MIN_PBF_BYTES )); then
    echo "[pbf] WARNING: Extract from ${provider} is unexpectedly small (${size} bytes)." >&2
    rm -f "$PBF_PART"
    continue
  fi

  actual_md5="$(md5sum "$PBF_PART" | awk '{ print $1 }' | tr '[:upper:]' '[:lower:]')"
  if [[ "$actual_md5" != "$candidate_md5" ]]; then
    echo "[pbf] WARNING: MD5 mismatch from ${provider}; trying next source." >&2
    echo "[pbf] Expected: ${candidate_md5}; actual: ${actual_md5}" >&2
    rm -f "$PBF_PART"
    continue
  fi

  SOURCE_PROVIDER="$provider"
  SOURCE_LABEL="$label"
  SOURCE_URL="$pbf_url"
  EXPECTED_MD5="$candidate_md5"
  mv "$PBF_PART" "$PBF_FILE"

  python3 - "$META_TEMP" "$SOURCE_PROVIDER" "$SOURCE_LABEL" "$SOURCE_URL" "$EXPECTED_MD5" "$size" <<'PY'
import json, sys
from datetime import datetime, timezone
from pathlib import Path
path, provider, label, url, md5, size = sys.argv[1:]
metadata = {
    "sourceProvider": provider,
    "sourceLabel": label,
    "sourceUrl": url,
    "sourcePbfMd5": md5,
    "pbfFile": "georgia-latest.osm.pbf",
    "pbfFileSizeBytes": int(size),
    "retrievedAtUtc": datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z"),
    "license": "ODbL-1.0",
    "attribution": "© OpenStreetMap contributors",
}
Path(path).write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
PY
  mv "$META_TEMP" "$META_FILE"
  rm -f "$MD5_FILE"

  echo "[pbf] Verified ${SOURCE_PROVIDER} extract: ${size} bytes, MD5 ${EXPECTED_MD5}."
  write_output "provider" "$SOURCE_PROVIDER"
  write_output "source-md5" "$EXPECTED_MD5"
  write_output "pbf-downloaded" "true"
  exit 0
done

echo "ERROR: Could not download and verify Georgia OSM PBF from any configured source." >&2
echo "Tried Geofabrik and the OpenStreetMap France mirror. See the preceding [pbf] diagnostics." >&2
exit 3
