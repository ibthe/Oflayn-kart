#!/usr/bin/env python3
"""Verify the generated compact Copernicus DEM tile package."""
from __future__ import annotations

import argparse
import gzip
import hashlib
import json
from pathlib import Path

RAW_TILE_BYTES = 1200 * 1200 * 2


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def verify(directory: Path) -> dict:
    manifest_path = directory / "manifest.json"
    if not manifest_path.is_file():
        raise ValueError("DEM manifest is missing.")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if manifest.get("dataset") != "Copernicus DEM GLO-90":
        raise ValueError("Unexpected DEM source.")
    if manifest.get("resolutionMeters") != 90 or manifest.get("tilePixels") != 1200:
        raise ValueError("Unexpected DEM resolution or tile dimensions.")
    tiles = manifest.get("tiles", [])
    if len(tiles) != 24:
        raise ValueError(f"Expected 24 Georgia DEM tiles, found {len(tiles)}.")
    seen = set()
    total = 0
    for tile in tiles:
        name = tile.get("file")
        if not isinstance(name, str) or not name.endswith(".dem.gz") or name in seen:
            raise ValueError(f"Invalid or duplicate DEM tile name: {name!r}")
        seen.add(name)
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
    return {"tiles": len(tiles), "compressedBytes": total}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("directory", type=Path)
    args = parser.parse_args()
    result = verify(args.directory)
    print(f"OK: verified {result['tiles']} Copernicus GLO-90 tiles ({result['compressedBytes']:,} bytes).")


if __name__ == "__main__":
    main()
