#!/usr/bin/env python3
"""Fast synthetic tests for the GLO-90 contour generator."""
from __future__ import annotations

import gzip
import json
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path

import numpy as np

import build_contours as contours


def main() -> None:
    assert contours.tile_origin("N41E044.dem.gz") == (41, 44)
    assert contours.tile_origin("S01W001.dem.gz") == (-1, -1)

    gradient = np.tile(np.linspace(0, 1000, 120, dtype=np.int16)[:, None], (1, 120))
    lines = contours.extract_tile_contours(
        gradient,
        latitude=41,
        longitude=44,
        interval_m=250,
        min_length_m=1_000,
        point_spacing_m=180,
    )
    levels = {elevation for elevation, _, _ in lines}
    assert levels == {250, 500, 750}, levels
    assert any(elevation == 500 and major for elevation, major, _ in lines)
    for _, _, points in lines:
        assert len(points) >= 2
        assert all(44.0 <= lon <= 45.0 and 41.0 <= lat <= 42.0 for lon, lat in points)

    missing = np.full((120, 120), contours.NODATA, dtype=np.int16)
    assert contours.extract_tile_contours(missing, 41, 44, 100, 100, 180) == []

    with tempfile.TemporaryDirectory() as temporary:
        root = Path(temporary)
        dem_dir = root / "dem"
        dem_dir.mkdir()
        values = np.tile(np.linspace(0, 1000, 1200, dtype=np.int16)[:, None], (1, 1200))
        tile_name = "N41E044.dem.gz"
        (dem_dir / tile_name).write_bytes(gzip.compress(values.astype("<i2").tobytes()))
        (dem_dir / "manifest.json").write_text(
            json.dumps({"tiles": [{"file": tile_name}]}), encoding="utf-8"
        )
        osm_file = root / "contours.osm"
        manifest_file = root / "contours.json"
        generated = contours.build_contours(
            dem_dir, osm_file, manifest_file, interval_m=250,
            min_length_m=1_000, point_spacing_m=180
        )
        xml_root = ET.parse(osm_file).getroot()
        assert generated["contourWays"] == len(xml_root.findall("way"))
        assert generated["contourNodes"] == len(xml_root.findall("node"))
        assert len(xml_root.findall("way")) == 3
        assert json.loads(manifest_file.read_text(encoding="utf-8"))["osmSha256"] == generated["osmSha256"]

    print("OK: contour tile naming, geometry, major lines, no-data handling and OSM XML generation.")


if __name__ == "__main__":
    main()
