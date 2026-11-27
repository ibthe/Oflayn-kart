#!/usr/bin/env bash
set -euo pipefail

INPUT_OSM="${1:?Usage: build_contours_map.sh contours.osm contours.map contour-manifest.json output-manifest.json}"
OUTPUT_MAP="${2:?Usage: build_contours_map.sh contours.osm contours.map contour-manifest.json output-manifest.json}"
CONTOUR_MANIFEST="${3:?Usage: build_contours_map.sh contours.osm contours.map contour-manifest.json output-manifest.json}"
OUTPUT_MANIFEST="${4:?Usage: build_contours_map.sh contours.osm contours.map contour-manifest.json output-manifest.json}"
BBOX="${5:-41.00,39.95,43.65,46.80}"

if [[ ! -s "$INPUT_OSM" ]]; then
  echo "ERROR: generated contour OSM XML is missing or empty." >&2
  exit 2
fi

mkdir -p "$(dirname "$OUTPUT_MAP")" "$(dirname "$OUTPUT_MANIFEST")"
rm -f "$OUTPUT_MAP"
osmosis --rx file="$INPUT_OSM" --mw file="$OUTPUT_MAP" type=hd bbox="$BBOX" map-start-position=42.0,44.0
python3 scripts/verify_contours_map.py \
  --map "$OUTPUT_MAP" \
  --contour-manifest "$CONTOUR_MANIFEST" \
  --output-manifest "$OUTPUT_MANIFEST"
