#!/usr/bin/env python3
"""Sanity-check a generated Georgia road graph before it is bundled into the APK."""
from __future__ import annotations

import sys
from pathlib import Path

import road_graph_format as rg

# (name, from, to, minimum km, maximum km) real-world road distances, generous bounds.
ROUTES = [
    ("Tbilisi -> Kutaisi", (41.7151, 44.8271), (42.2679, 42.6946), 200, 330),
    ("Tbilisi -> Batumi", (41.7151, 44.8271), (41.6168, 41.6367), 320, 520),
]


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: verify_road_graph.py roads.bin", file=sys.stderr)
        return 2
    path = Path(sys.argv[1])
    if not path.is_file() or path.stat().st_size < 1_000_000:
        print("ERROR: road graph is missing or unexpectedly small.", file=sys.stderr)
        return 3
    graph = rg.Graph.load(path)
    print(f"[roads-verify] vertices={graph.V:,}, edges={graph.E:,}, size={path.stat().st_size:,} bytes")
    if graph.V < 20_000 or graph.E < 30_000:
        print("ERROR: road graph is much smaller than a country-wide network.", file=sys.stderr)
        return 4
    if graph.arc_start[graph.V] != 2 * graph.E or len(graph.arcs) != 2 * graph.E:
        print("ERROR: adjacency table does not match the edge count.", file=sys.stderr)
        return 4
    for edge in graph.edges:
        if not (0 <= edge[0] < graph.V and 0 <= edge[1] < graph.V):
            print("ERROR: an edge points to a missing vertex.", file=sys.stderr)
            return 4
    if not (41.0 <= graph.min_lat / 1e6 and graph.max_lat / 1e6 <= 43.7 and 39.8 <= graph.min_lon / 1e6 and graph.max_lon / 1e6 <= 46.8):
        print("ERROR: graph extent is outside Georgia.", file=sys.stderr)
        return 4
    for name, start, end, low, high in ROUTES:
        a = graph.nearest_vertex(rg.PROFILE_CAR, *start)
        b = graph.nearest_vertex(rg.PROFILE_CAR, *end)
        result = graph.route_vertices(rg.PROFILE_CAR, a, b) if a is not None and b is not None else None
        if result is None:
            print(f"ERROR: no car route found for {name}; the graph is disconnected.", file=sys.stderr)
            return 5
        seconds, metres = result
        print(f"[roads-verify] {name}: {metres / 1000:.0f} km, {seconds / 3600:.1f} h")
        if not low <= metres / 1000 <= high:
            print(f"ERROR: {name} distance {metres / 1000:.0f} km is outside {low}-{high} km.", file=sys.stderr)
            return 5
    print("Road graph verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
