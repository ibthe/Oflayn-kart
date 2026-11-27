#!/usr/bin/env python3
"""Build the compact offline road graph (georgia-roads.bin) from the verified Georgia OSM PBF.

Only the roads the app can route on are kept; the result is a few tens of megabytes that the
Android app memory-maps. See road_graph_format.py for the file layout.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
import tempfile
import time
from array import array
from collections import Counter
from pathlib import Path

import road_graph_format as rg

BBOX_DEFAULT = (41.0, 39.8, 43.7, 46.8)


def parse_bbox(value: str) -> tuple[float, float, float, float]:
    parts = tuple(float(part) for part in value.split(","))
    if len(parts) != 4 or not (parts[0] < parts[2] and parts[1] < parts[3]):
        raise argparse.ArgumentTypeError("bbox must be minLat,minLon,maxLat,maxLon")
    return parts  # type: ignore[return-value]


def _restriction_records(tags: dict, members: list, stats: dict) -> list:
    if tags.get("type") != "restriction":
        return []
    from_ways = [ref for kind, role, ref in members if kind == "w" and role == "from"]
    to_ways = [ref for kind, role, ref in members if kind == "w" and role == "to"]
    via_nodes = [ref for kind, role, ref in members if kind == "n" and role == "via"]
    via_ways = [ref for kind, role, ref in members if kind == "w" and role == "via"]
    stats["restrictionRelations"] += 1
    if via_ways:
        stats["unsupportedViaWayRestrictions"] += 1
        return []
    if not from_ways or not to_ways or len(via_nodes) != 1:
        stats["unsupportedRestrictionRelations"] += 1
        return []
    except_values = {part.strip().lower() for part in str(tags.get("except", "")).replace(";", ",").split(",") if part.strip()}
    except_mask = 0
    if "motorcar" in except_values: except_mask |= rg.PROFILE_CAR | rg.PROFILE_OFFROAD
    if "motor_vehicle" in except_values: except_mask |= rg.PROFILE_CAR | rg.PROFILE_OFFROAD | rg.PROFILE_MOTORCYCLE
    if "vehicle" in except_values: except_mask |= rg.PROFILE_CAR | rg.PROFILE_OFFROAD | rg.PROFILE_MOTORCYCLE | rg.PROFILE_BIKE
    if "motorcycle" in except_values: except_mask |= rg.PROFILE_MOTORCYCLE
    if "4wd" in except_values: except_mask |= rg.PROFILE_OFFROAD
    if "bicycle" in except_values: except_mask |= rg.PROFILE_BIKE
    if "foot" in except_values: except_mask |= rg.PROFILE_FOOT
    keys = {
        rg.PROFILE_CAR: ("restriction:motorcar", "restriction:motor_vehicle", "restriction:vehicle", "restriction"),
        rg.PROFILE_OFFROAD: ("restriction:motorcar", "restriction:motor_vehicle", "restriction:vehicle", "restriction"),
        rg.PROFILE_MOTORCYCLE: ("restriction:motorcycle", "restriction:motor_vehicle", "restriction:vehicle", "restriction"),
        rg.PROFILE_BIKE: ("restriction:bicycle", "restriction:vehicle", "restriction"),
        rg.PROFILE_FOOT: ("restriction:foot", "restriction"),
    }
    result = []
    for profile, candidates in keys.items():
        if except_mask & profile: continue
        value = next((tags[key] for key in candidates if tags.get(key)), None)
        if value is None: continue
        value = str(value).strip().lower()
        if value.startswith("no_"): kind = rg.TURN_NO
        elif value.startswith("only_"): kind = rg.TURN_ONLY
        else: continue
        value = str(value)
        for from_way in from_ways:
            for to_way in to_ways:
                result.append(rg.TurnRestriction(from_way, to_way, via_nodes[0], profile, kind, value))
    if result: stats["nodeRestrictions"] += len(result)
    else: stats["unsupportedRestrictionRelations"] += 1
    return result


def collect_ways(input_file: Path, bbox: tuple[float, float, float, float]) -> tuple[list, list, dict]:
    import osmium  # imported lazily so the format tests run without PyOsmium

    min_lat, min_lon, max_lat, max_lon = bbox
    stats = {"waysSeen": 0, "roadWays": 0, "runs": 0, "nodeRefs": 0, "skippedMissingNodes": 0,
             "restrictionRelations": 0, "nodeRestrictions": 0, "unsupportedViaWayRestrictions": 0,
             "unsupportedRestrictionRelations": 0}
    ways: list = []
    restrictions: list = []
    started = time.monotonic()
    last_report = [started]

    class Collector(osmium.SimpleHandler):
        def way(self, way) -> None:
            stats["waysSeen"] += 1
            now = time.monotonic()
            if stats["waysSeen"] % 250_000 == 0 or now - last_report[0] > 15:
                last_report[0] = now
                print(f"[roads] scan progress: ways={stats['waysSeen']:,}, road ways={stats['roadWays']:,}, "
                      f"elapsed={now - started:.1f}s", flush=True)
            if "highway" not in way.tags:
                return
            info = rg.classify_way({tag.k: tag.v for tag in way.tags})
            if info is None:
                return
            stats["roadWays"] += 1
            refs, lats, lons = array("q"), array("i"), array("i")

            def flush() -> None:
                if len(refs) >= 2:
                    ways.append(rg.WayRecord(info, refs.tolist(), lats.tolist(), lons.tolist(), way.id))
                    stats["runs"] += 1
                    stats["nodeRefs"] += len(refs)
                del refs[:], lats[:], lons[:]

            for node in way.nodes:
                location = node.location
                if not location.valid() or not (min_lat <= location.lat <= max_lat and min_lon <= location.lon <= max_lon):
                    stats["skippedMissingNodes"] += 1
                    flush()  # a gap splits the way into separate runs
                    continue
                refs.append(node.ref)
                lats.append(round(location.lat * 1e6))
                lons.append(round(location.lon * 1e6))
            flush()

        def relation(self, relation) -> None:
            tags = {tag.k: tag.v for tag in relation.tags}
            members = [(member.type, member.role, member.ref) for member in relation.members]
            restrictions.extend(_restriction_records(tags, members, stats))

    index_dir = Path(os.environ.get("RUNNER_TEMP") or tempfile.gettempdir())
    index_file = index_dir / "georgia-road-node-locations.idx"
    index_file.unlink(missing_ok=True)
    try:
        Collector().apply_file(str(input_file), locations=True, idx=f"sparse_file_array,{index_file}")
    finally:
        index_file.unlink(missing_ok=True)
    stats["scanSeconds"] = round(time.monotonic() - started, 1)
    print(f"[roads] scan complete: {stats}", flush=True)
    return ways, restrictions, stats


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input_pbf", type=Path)
    parser.add_argument("output_bin", type=Path)
    parser.add_argument("--bbox", default=",".join(map(str, BBOX_DEFAULT)))
    parser.add_argument("--summary", type=Path, required=True)
    args = parser.parse_args()
    if not args.input_pbf.is_file() or args.input_pbf.stat().st_size < 1_000_000:
        print("ERROR: input OSM PBF is missing or unexpectedly small.", file=sys.stderr)
        return 2
    try:
        ways, restrictions, stats = collect_ways(args.input_pbf, parse_bbox(args.bbox))
        if not ways:
            raise RuntimeError("no routable roads were found in the PBF")
        started = time.monotonic()
        data = rg.build_graph(ways, restrictions)
        del ways
        print(f"[roads] graph built: vertices={len(data.vlat):,}, edges={len(data.edges):,}, "
              f"elapsed={time.monotonic() - started:.1f}s", flush=True)
        summary = rg.write_graph(args.output_bin, data)
    except Exception as exc:  # noqa: BLE001 - report any failure as a clean build error
        args.output_bin.unlink(missing_ok=True)
        print(f"ERROR: road graph generation failed: {exc}", file=sys.stderr)
        return 1
    summary["sha256"] = hashlib.sha256(args.output_bin.read_bytes()).hexdigest()
    summary["stats"] = stats
    summary["classCounts"] = dict(sorted(Counter(edge[4] for edge in data.edges).items()))
    args.summary.parent.mkdir(parents=True, exist_ok=True)
    args.summary.write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    print(f"Built {args.output_bin}: {summary['vertexCount']:,} vertices, {summary['edgeCount']:,} edges, "
          f"{summary['sizeBytes']:,} bytes.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
