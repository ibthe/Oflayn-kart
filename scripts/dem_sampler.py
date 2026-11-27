#!/usr/bin/env python3
"""Small cached sampler for the app's compressed 90 m Copernicus DEM tiles."""
from __future__ import annotations

import gzip
import math
from pathlib import Path

import numpy as np

TILE_PIXELS = 1200
NODATA = -32768


class DemSampler:
    def __init__(self, directory: Path):
        self.directory = Path(directory)
        self._tiles: dict[tuple[int, int], np.ndarray | None] = {}

    @staticmethod
    def tile_key(latitude: int, longitude: int) -> str:
        ns = "N" if latitude >= 0 else "S"
        ew = "E" if longitude >= 0 else "W"
        return f"{ns}{abs(latitude):02d}{ew}{abs(longitude):03d}"

    def _tile(self, latitude: int, longitude: int) -> np.ndarray | None:
        key = (latitude, longitude)
        if key in self._tiles:
            return self._tiles[key]
        path = self.directory / f"{self.tile_key(latitude, longitude)}.dem.gz"
        if not path.is_file():
            self._tiles[key] = None
            return None
        with gzip.open(path, "rb") as stream:
            raw = stream.read()
        expected = TILE_PIXELS * TILE_PIXELS * 2
        if len(raw) != expected:
            raise ValueError(f"Invalid DEM tile size for {path.name}: {len(raw)} bytes.")
        data = np.frombuffer(raw, dtype="<i2").reshape((TILE_PIXELS, TILE_PIXELS))
        self._tiles[key] = data
        return data

    def sample(self, latitude: float, longitude: float) -> float | None:
        if not (-90.0 <= latitude <= 90.0 and -180.0 <= longitude <= 180.0):
            return None
        tile_latitude = math.floor(latitude)
        tile_longitude = math.floor(longitude)
        data = self._tile(tile_latitude, tile_longitude)
        if data is None:
            return None

        # The raster is north-up: row zero is the north edge, column zero the west edge.
        row = int(round((tile_latitude + 1.0 - latitude) * TILE_PIXELS - 0.5))
        column = int(round((longitude - tile_longitude) * TILE_PIXELS - 0.5))
        row = max(0, min(TILE_PIXELS - 1, row))
        column = max(0, min(TILE_PIXELS - 1, column))

        window = data[
            max(0, row - 1):min(TILE_PIXELS, row + 2),
            max(0, column - 1):min(TILE_PIXELS, column + 2),
        ]
        valid = window[window != NODATA]
        if valid.size == 0:
            return None
        return float(np.median(valid))

    def __call__(self, latitude: float, longitude: float) -> float | None:
        return self.sample(latitude, longitude)
