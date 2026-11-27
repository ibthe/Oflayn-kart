#!/usr/bin/env python3
"""Build compact 90 m Copernicus DEM tiles for the Georgia offline map."""
from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import math
from pathlib import Path

NODATA = -32768
TILE_PIXELS = 1200
DATASET = "Copernicus DEM GLO-90"
BASE_URL = "https://copernicus-dem-90m.s3.eu-central-1.amazonaws.com"


def tile_range(bbox: tuple[float, float, float, float]) -> list[tuple[int, int]]:
    min_lat, min_lon, max_lat, max_lon = bbox
    if not (min_lat < max_lat and min_lon < max_lon):
        raise ValueError("Invalid DEM bounding box.")
    south = math.floor(min_lat)
    north = math.ceil(max_lat) - 1
    west = math.floor(min_lon)
    east = math.ceil(max_lon) - 1
    return [(lat, lon) for lat in range(south, north + 1) for lon in range(west, east + 1)]


def tile_name(latitude: int, longitude: int) -> str:
    ns = "N" if latitude >= 0 else "S"
    ew = "E" if longitude >= 0 else "W"
    return f"Copernicus_DSM_COG_30_{ns}{abs(latitude):02d}_00_{ew}{abs(longitude):03d}_00_DEM"


def tile_key(latitude: int, longitude: int) -> str:
    ns = "N" if latitude >= 0 else "S"
    ew = "E" if longitude >= 0 else "W"
    return f"{ns}{abs(latitude):02d}{ew}{abs(longitude):03d}"


def is_missing_tile_error(error: Exception) -> bool:
    """Only a confirmed HTTP 404 means the public bucket has no tile for this cell."""
    message = str(error).lower()
    return "404" in message or "http not found" in message


def encode_elevations(values, mask) -> bytes:
    import numpy as np

    data = np.asarray(values, dtype=np.float64)
    invalid = np.asarray(mask, dtype=bool) | ~np.isfinite(data)
    encoded = np.full(data.shape, NODATA, dtype="<i2")
    valid = ~invalid
    encoded[valid] = np.rint(data[valid]).clip(-32767, 32767).astype("<i2")
    return encoded.tobytes(order="C")


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def build_tile(latitude: int, longitude: int, output: Path) -> dict | None:
    try:
        import numpy as np
        import rasterio
    except ImportError as error:
        raise RuntimeError("Install rasterio and numpy before building the DEM.") from error

    name = tile_name(latitude, longitude)
    url = f"{BASE_URL}/{name}/{name}.tif"
    print(f"[dem] downloading {tile_key(latitude, longitude)} from Copernicus GLO-90", flush=True)
    try:
        with rasterio.Env(GDAL_DISABLE_READDIR_ON_OPEN="EMPTY_DIR", CPL_VSIL_CURL_ALLOWED_EXTENSIONS=".tif"):
            with rasterio.open(url) as source:
                if source.width != TILE_PIXELS or source.height != TILE_PIXELS:
                    raise RuntimeError(
                        f"Unexpected tile dimensions for {name}: {source.width}x{source.height}; "
                        f"expected {TILE_PIXELS}x{TILE_PIXELS}."
                    )
                values = source.read(1, masked=True)
                raw = encode_elevations(values.data, np.ma.getmaskarray(values))
    except rasterio.errors.RasterioIOError as error:
        if is_missing_tile_error(error):
            print(f"[dem] no public GLO-90 tile for {tile_key(latitude, longitude)}; leaving this cell unavailable", flush=True)
            return None
        raise
    if len(raw) != TILE_PIXELS * TILE_PIXELS * 2:
        raise RuntimeError(f"Unexpected raw DEM size for {name}: {len(raw)} bytes.")
    target = output / f"{tile_key(latitude, longitude)}.dem.gz"
    temporary = target.with_suffix(target.suffix + ".tmp")
    with temporary.open("wb") as file_stream:
        with gzip.GzipFile(fileobj=file_stream, mode="wb", compresslevel=9, mtime=0) as compressed:
            compressed.write(raw)
    temporary.replace(target)
    return {
        "file": target.name,
        "sha256": sha256_file(target),
        "compressedBytes": target.stat().st_size,
        "rawBytes": len(raw),
        "width": TILE_PIXELS,
        "height": TILE_PIXELS,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--bbox", default="41.0,39.8,43.7,46.8", help="minLat,minLon,maxLat,maxLon")
    args = parser.parse_args()
    bbox = tuple(float(value) for value in args.bbox.split(","))
    if len(bbox) != 4:
        parser.error("--bbox must contain minLat,minLon,maxLat,maxLon")
    tiles = tile_range(bbox)  # type: ignore[arg-type]
    args.output.mkdir(parents=True, exist_ok=True)
    metadata = {
        "dataset": DATASET,
        "resolutionMeters": 90,
        "tilePixels": TILE_PIXELS,
        "noData": NODATA,
        "bbox": list(bbox),
        "source": "https://registry.opendata.aws/copernicus-dem/",
        "license": "Copernicus WorldDEM-90 free and open licence; attribution required",
        "tiles": [],
        "missingTiles": [],
    }
    for index, (latitude, longitude) in enumerate(tiles, start=1):
        tile = build_tile(latitude, longitude, args.output)
        if tile is None:
            metadata["missingTiles"].append({
                "key": tile_key(latitude, longitude),
                "reason": "not-present-in-public-glo-90-bucket",
            })
            continue
        metadata["tiles"].append(tile)
        print(f"[dem] {index}/{len(tiles)} {tile['file']} {tile['compressedBytes']:,} bytes", flush=True)
    if not metadata["tiles"]:
        raise RuntimeError("No Copernicus GLO-90 tiles were available for the requested area.")
    manifest = args.output / "manifest.json"
    temporary = args.output / "manifest.json.tmp"
    temporary.write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    temporary.replace(manifest)
    print(
        f"[dem] complete: {len(metadata['tiles'])} tiles, "
        f"{len(metadata['missingTiles'])} unavailable cells, "
        f"{sum(t['compressedBytes'] for t in metadata['tiles']):,} compressed bytes",
        flush=True,
    )


if __name__ == "__main__":
    main()
