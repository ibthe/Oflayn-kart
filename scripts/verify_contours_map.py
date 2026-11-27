#!/usr/bin/env python3
"""Validate the separate Mapsforge elevation-contour map and write its asset manifest."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--map", type=Path, required=True)
    parser.add_argument("--contour-manifest", type=Path, required=True)
    parser.add_argument("--output-manifest", type=Path, required=True)
    args = parser.parse_args()

    if not args.map.is_file() or args.map.stat().st_size < 1024:
        raise SystemExit(f"ERROR: contour map is missing or too small: {args.map}")
    source = json.loads(args.contour_manifest.read_text(encoding="utf-8"))
    if source.get("contourWays", 0) <= 0 or source.get("contourNodes", 0) <= 0:
        raise SystemExit("ERROR: contour source manifest contains no generated geometry.")

    manifest = {
        "mapFile": args.map.name,
        "mapFileSizeBytes": args.map.stat().st_size,
        "mapFileSha256": sha256_file(args.map),
        "source": source.get("source", "Copernicus WorldDEM-90"),
        "intervalMeters": source.get("intervalMeters", 100),
        "majorIntervalMeters": source.get("majorIntervalMeters", 500),
        "minimumSegmentMeters": source.get("minimumSegmentMeters", 800),
        "tilesProcessed": source.get("tilesProcessed", 0),
        "contourWays": source["contourWays"],
        "contourNodes": source["contourNodes"],
        "levelsMeters": source.get("levelsMeters", []),
        "runtimeNetworkAccessRequired": False,
    }
    args.output_manifest.parent.mkdir(parents=True, exist_ok=True)
    args.output_manifest.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(
        f"[contours-verify] {args.map.name}: {manifest['mapFileSizeBytes']:,} bytes, "
        f"{manifest['contourWays']:,} ways, {manifest['contourNodes']:,} nodes, "
        f"sha256={manifest['mapFileSha256']}",
        flush=True,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
