#!/usr/bin/env python3
"""Fast synthetic tests for the GLO-90 contour generator."""
from __future__ import annotations

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
    print("OK: contour tile naming, 100 m levels, major lines, geometry bounds and no-data handling.")


if __name__ == "__main__":
    main()
