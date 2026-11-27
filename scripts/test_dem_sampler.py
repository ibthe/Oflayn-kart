#!/usr/bin/env python3
from __future__ import annotations

import gzip
import tempfile
import unittest
from pathlib import Path

import numpy as np

from dem_sampler import DemSampler, NODATA, TILE_PIXELS


class DemSamplerTest(unittest.TestCase):
    def write_tile(self, directory: Path, values: np.ndarray, name: str = "N41E044.dem.gz") -> Path:
        path = directory / name
        with gzip.open(path, "wb", compresslevel=1) as stream:
            stream.write(values.astype("<i2", copy=False).tobytes(order="C"))
        return path

    def test_samples_north_up_tile_and_uses_local_median(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            values = np.full((TILE_PIXELS, TILE_PIXELS), 120, dtype=np.int16)
            values[599:602, 599:602] = 120
            values[600, 600] = 900
            self.write_tile(directory, values)
            sampler = DemSampler(directory)
            self.assertEqual(120.0, sampler.sample(41.5, 44.5))
            self.assertEqual(120.0, sampler(41.5, 44.5))

    def test_missing_and_nodata_cells_return_none(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            sampler = DemSampler(directory)
            self.assertIsNone(sampler.sample(41.5, 44.5))
            values = np.full((TILE_PIXELS, TILE_PIXELS), NODATA, dtype=np.int16)
            self.write_tile(directory, values)
            self.assertIsNone(DemSampler(directory).sample(41.5, 44.5))

    def test_rejects_truncated_dem_tile(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            with gzip.open(directory / "N41E044.dem.gz", "wb") as stream:
                stream.write(b"short")
            with self.assertRaises(ValueError):
                DemSampler(directory).sample(41.5, 44.5)


if __name__ == "__main__":
    unittest.main()
