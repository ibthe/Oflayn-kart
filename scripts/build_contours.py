#!/usr/bin/env python3
"""Generate a small OSM XML dataset of elevation contours from bundled GLO-90 DEM tiles."""
from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import math
import re
from pathlib import Path

import numpy as np

NODATA = -32768
TILE_PIXELS = 1200
NODE_ID_START = 8_000_000_000_000_000
WAY_ID_START = 8_100_000_000_000_000
OSM_ENTITY_TIMESTAMP = "2020-01-01T00:00:00Z"
METERS_PER_DEGREE = 111_195.0
TILE_PATTERN = re.compile(r"([NS])(\d{2})([EW])(\d{3})\.dem\.gzip$")


def tile_origin(filename: str) -> tuple[int, int]:
    match = TILE_PATTERN.fullmatch(filename)
    if not match:
        raise ValueError(f"Unexpected DEM tile name: {filename}")
    latitude = int(match.group(2)) * (-1 if match.group(1) == "S" else 1)
    longitude = int(match.group(4)) * (-1 if match.group(3) == "W" else 1)
    return latitude, longitude


def line_length_m(points: np.ndarray) -> float:
    if len(points) < 2:
        return 0.0
    mean_lat = np.radians((points[:-1, 1] + points[1:, 1]) / 2.0)
    dx = np.diff(points[:, 0]) * METERS_PER_DEGREE * np.cos(mean_lat)
    dy = np.diff(points[:, 1]) * METERS_PER_DEGREE
    return float(np.hypot(dx, dy).sum())


def simplify_line(points: np.ndarray, spacing_m: float) -> list[tuple[float, float]]:
    if len(points) < 2:
        return []
    # GLO-90 samples are about 90 m apart; decimate in C-backed NumPy rather than
    # walking every contour vertex in Python on the full-country build runner.
    step = max(1, round(spacing_m / 90.0))
    indices = np.arange(0, len(points), step, dtype=np.int64)
    if indices[-1] != len(points) - 1:
        indices = np.append(indices, len(points) - 1)
    simplified = points[indices]
    return [(float(point[0]), float(point[1])) for point in simplified]


def extract_tile_contours(
    values: np.ndarray,
    latitude: int,
    longitude: int,
    interval_m: int,
    min_length_m: float,
    point_spacing_m: float,
) -> list[tuple[int, bool, list[tuple[float, float]]]]:
    import contourpy

    valid = values != NODATA
    if int(valid.sum()) < 100:
        return []
    data = np.ma.masked_array(values[::-1, :], mask=~valid[::-1, :])
    rows, columns = data.shape
    x = longitude + (np.arange(columns, dtype=np.float64) + 0.5) / columns
    y = latitude + (np.arange(rows, dtype=np.float64) + 0.5) / rows
    minimum = int(data.min())
    maximum = int(data.max())
    first_level = max(interval_m, math.ceil(max(0, minimum) / interval_m) * interval_m)
    last_level = math.floor(maximum / interval_m) * interval_m
    if first_level > last_level:
        return []

    generator = contourpy.contour_generator(x=x, y=y, z=data, name="serial", line_type="Separate")
    output = []
    for elevation in range(first_level, last_level + 1, interval_m):
        for raw_line in generator.lines(elevation):
            if len(raw_line) < 2 or line_length_m(raw_line) < min_length_m:
                continue
            points = simplify_line(raw_line, point_spacing_m)
            if len(points) >= 2:
                output.append((elevation, elevation % 500 == 0, points))
    return output


def build_contours(
    dem_directory: Path,
    output_osm: Path,
    manifest_path: Path,
    interval_m: int = 100,
    min_length_m: float = 800.0,
    point_spacing_m: float = 180.0,
) -> dict:
    dem_manifest_path = dem_directory / "manifest.json"
    dem_manifest = json.loads(dem_manifest_path.read_text(encoding="utf-8"))
    tile_records = dem_manifest.get("tiles", [])
    if not tile_records:
        raise RuntimeError("DEM manifest contains no elevation tiles.")

    nodes_xml: list[str] = []
    ways_xml: list[str] = []
    node_id = NODE_ID_START
    way_id = WAY_ID_START
    contour_count = 0
    processed_tiles = 0
    levels: set[int] = set()

    for tile in tile_records:
        filename = tile["file"]
        latitude, longitude = tile_origin(filename)
        raw = gzip.decompress((dem_directory / filename).read_bytes())
        expected_bytes = TILE_PIXELS * TILE_PIXELS * 2
        if len(raw) != expected_bytes:
            raise ValueError(f"Unexpected DEM tile size for {filename}: {len(raw)}")
        values = np.frombuffer(raw, dtype="<i2").reshape((TILE_PIXELS, TILE_PIXELS))
        contours = extract_tile_contours(
            values, latitude, longitude, interval_m, min_length_m, point_spacing_m
        )
        processed_tiles += 1
        for elevation, major, points in contours:
            refs = []
            for lon, lat in points:
                refs.append(node_id)
                nodes_xml.append(
                    f'<node id="{node_id}" version="1" timestamp="{OSM_ENTITY_TIMESTAMP}" lat="{lat:.7f}" lon="{lon:.7f}"/>'
                )
                node_id += 1
            ways_xml.append(
                f'<way id="{way_id}" version="1" timestamp="{OSM_ENTITY_TIMESTAMP}">'
                + "".join(f'<nd ref="{ref}"/>' for ref in refs)
                + '<tag k="contour" v="elevation"/>'
                + f'<tag k="ele" v="{elevation}"/>'
                + f'<tag k="contour_major" v="{"yes" if major else "no"}"/>'
                + '<tag k="source" v="Copernicus GLO-90"/>'
                + '</way>'
            )
            way_id += 1
            contour_count += 1
            levels.add(elevation)

    if contour_count == 0:
        raise RuntimeError("No contour lines were generated from the available DEM tiles.")

    output_osm.parent.mkdir(parents=True, exist_ok=True)
    output_osm.write_text(
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        '<osm version="0.6" generator="Oflayn kart GLO-90 contour builder">\n'
        + "".join(nodes_xml)
        + "".join(ways_xml)
        + '</osm>\n',
        encoding="utf-8",
    )
    osm_sha = hashlib.sha256(output_osm.read_bytes()).hexdigest()
    manifest = {
        "format": "OSM XML 0.6",
        "source": "Copernicus WorldDEM-90",
        "intervalMeters": interval_m,
        "majorIntervalMeters": 500,
        "minimumSegmentMeters": min_length_m,
        "pointSpacingMeters": point_spacing_m,
        "tilesProcessed": processed_tiles,
        "contourWays": contour_count,
        "contourNodes": node_id - NODE_ID_START,
        "levelsMeters": sorted(levels),
        "osmFile": output_osm.name,
        "osmBytes": output_osm.stat().st_size,
        "osmSha256": osm_sha,
    }
    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(
        f"[contours] {processed_tiles} DEM tiles -> {contour_count:,} contour ways, "
        f"{node_id - NODE_ID_START:,} nodes, {output_osm.stat().st_size:,} XML bytes",
        flush=True,
    )
    return manifest


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dem-dir", type=Path, required=True)
    parser.add_argument("--output-osm", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--interval-m", type=int, default=100)
    parser.add_argument("--min-length-m", type=float, default=800.0)
    parser.add_argument("--point-spacing-m", type=float, default=180.0)
    args = parser.parse_args()
    build_contours(
        args.dem_dir, args.output_osm, args.manifest,
        args.interval_m, args.min_length_m, args.point_spacing_m
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
