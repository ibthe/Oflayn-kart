#!/usr/bin/env python3
"""Unit tests for the offline Copernicus DEM builder."""
from __future__ import annotations

import importlib.util
from pathlib import Path

import numpy as np

SCRIPT = Path(__file__).with_name("build_dem.py")
SPEC = importlib.util.spec_from_file_location("build_dem", SCRIPT)
assert SPEC and SPEC.loader
DEM = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(DEM)


def main() -> None:
    tiles = DEM.tile_range((41.0, 39.8, 43.7, 46.8))
    assert len(tiles) == 24
    assert tiles[0] == (41, 39)
    assert tiles[-1] == (43, 46)
    assert DEM.tile_range((41.0, 44.0, 42.0, 45.0)) == [(41, 44)]
    assert DEM.tile_name(41, 44) == "Copernicus_DSM_COG_30_N41_00_E044_00_DEM"
    assert DEM.tile_key(41, 44) == "N41E044"
    assert DEM.is_missing_tile_error(RuntimeError("HTTP response code: 404"))
    assert DEM.is_missing_tile_error(RuntimeError("HTTP Not Found"))
    assert not DEM.is_missing_tile_error(RuntimeError("HTTP response code: 403"))
    assert not DEM.is_missing_tile_error(RuntimeError("connection timed out"))
    assert DEM.tile_name(-1, -2) == "Copernicus_DSM_COG_30_S01_00_W002_00_DEM"

    values = np.array([[100.4, -5.2, np.nan, 50000.0]], dtype=np.float64)
    mask = np.array([[False, False, False, False]], dtype=bool)
    encoded = np.frombuffer(DEM.encode_elevations(values, mask), dtype="<i2")
    assert encoded.tolist() == [100, -5, DEM.NODATA, 32767]

    masked = np.array([[10.0, 20.0]], dtype=np.float64)
    encoded_masked = np.frombuffer(
        DEM.encode_elevations(masked, np.array([[False, True]])), dtype="<i2"
    )
    assert encoded_masked.tolist() == [10, DEM.NODATA]

    hgt_values = np.full((DEM.TILE_PIXELS, DEM.TILE_PIXELS), 120.0, dtype=np.float64)
    hgt_values[-1, -1] = 245.0
    hgt_mask = np.zeros_like(hgt_values, dtype=bool)
    hgt_mask[0, 0] = True
    hgt_raw = DEM.encode_hgt(hgt_values, hgt_mask)
    assert len(hgt_raw) == 1201 * 1201 * 2
    hgt = np.frombuffer(hgt_raw, dtype=">i2").reshape((1201, 1201))
    assert hgt[0, 0] == DEM.NODATA
    assert hgt[0, 1] == 120
    assert hgt[-1, 0] == 120
    assert hgt[0, -1] == 120
    assert hgt[-1, -1] == 245

    try:
        DEM.tile_range((43.0, 40.0, 41.0, 45.0))
    except ValueError:
        pass
    else:
        raise AssertionError("Invalid DEM bounding box was accepted.")

    print("OK: Copernicus tile naming, Georgia coverage, no-data handling, DEM encoding and HGT hillshade encoding.")


if __name__ == "__main__":
    main()
