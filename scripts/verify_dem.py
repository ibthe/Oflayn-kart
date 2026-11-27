#!/usr/bin/env python3
"""Verify the generated compact Copernicus DEM tile package."""
from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import math
from pathlib import Path

RAW_TILE_BYTES = 1200 * 1200 * 2


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def expected_tile_keys(bbox: list[float]) -> set[str]:
    min_lat, min_lon, max_lat, max_lon = (float(value) for value in bbox)
    if not (min_lat < max_lat and min_lon < max_lon):
        raise ValueError("Invalid DEM manifest bounding box.")
    return {
        f"{'N' if lat >= 0 else 'S'}{abs(lat):02d}{'E' if lon >= 0 else 'W'}{abs(lon):03d}"
        for lat in range(math.floor(min_lat), math.ceil(max_lat))
        for lon in range(math.floor(min_lon), math.ceil(max_lon))
    }


def verify(directory: Path) -> dict:
    manifest_path = directory / "manifest.json"
    if not manifest_path.is_file():
        raise ValueError("DEM manifest is missing.")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if manifest.get("dataset") != "Copernicus DEM GLO-90":
        raise ValueError("Unexpected DEM source.")
    if manifest.get("resolutionMeters") != 90 or manifest.get("tilePixels") != 1200:
        raise ValueError("Unexpected DEM resolution or tile dimensions.")
    expected = expected_tile_keys(manifest.get("bbox", []))
    tiles = manifest.get("tiles", [])
    missing = manifest.get("missingTiles", [])
    if not tiles:
        raise ValueError("No usable Copernicus GLO-90 tiles were generated.")
    present_keys = set()
    total = 0
    for tile in tiles:
        name = tile.get("file")
        if not isinstance(name, str) or not name.endswith(".dem.gz") or name in present_keys:
            raise ValueError(f"Invalid or duplicate DEM tile name: {name!r}")
        key = name.removesuffix(".dem.gz")
        if key not in expected:
            raise ValueError(f"Unexpected DEM tile outside the requested bounding box: {name}")
        present_keys.add(key)
        path = directory / name
        if not path.is_file():
            raise ValueError(f"DEM tile is missing: {name}")
        if sha256_file(path) != tile.get("sha256"):
            raise ValueError(f"DEM tile SHA-256 mismatch: {name}")
        with gzip.open(path, "rb") as stream:
            raw_length = len(stream.read())
        if raw_length != RAW_TILE_BYTES or tile.get("rawBytes") != RAW_TILE_BYTES:
            raise ValueError(f"Unexpected raw DEM tile length: {name} ({raw_length})")
        total += path.stat().st_size

    missing_keys = set()
    for item in missing:
        key = item.get("key") if isinstance(item, dict) else None
        if not isinstance(key, str) or key not in expected or key in missing_keys:
            raise ValueError(f"Invalid or duplicate unavailable DEM cell: {item!r}")
        if item.get("reason") != "not-present-in-public-glo-90-bucket":
            raise ValueError(f"Unexpected unavailable DEM cell reason: {item!r}")
        missing_keys.add(key)
    if present_keys & missing_keys or present_keys | missing_keys != expected:
        absent = sorted(expected - present_keys - missing_keys)
        overlap = sorted(present_keys & missing_keys)
        raise ValueError(f"DEM coverage manifest is incomplete or overlapping; unclassified={absent}, overlap={overlap}")
    return {"tiles": len(tiles), "missingTiles": len(missing), "compressedBytes": total}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("directory", type=Path)
    args = parser.parse_args()
    result = verify(args.directory)
    print(
        f"OK: verified {result['tiles']} Copernicus GLO-90 tiles, "
        f"{result['missingTiles']} unavailable cells ({result['compressedBytes']:,} bytes)."
    )


if __name__ == "__main__":
    main()
