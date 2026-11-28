#!/usr/bin/env python3
"""Exercise the real PyOsmium relation callback and OSM turn-restriction compiler."""
from __future__ import annotations

from pathlib import Path
import tempfile

import road_graph_format as rg
from build_road_graph import collect_ways

ROOT = Path(__file__).resolve().parent
FIXTURE = ROOT / "fixtures" / "road-turn-restriction-smoke.osm"


def main() -> int:
    ways, restrictions, stats = collect_ways(FIXTURE, (41.69, 44.79, 41.72, 44.83))
    if stats["restrictionRelations"] != 1 or stats["nodeRestrictions"] != 3:
        raise SystemExit(f"unexpected relation extraction counters: {stats}")
    if stats["unsupportedViaWayRestrictions"] or stats["unsupportedRestrictionRelations"]:
        raise SystemExit(f"the node-via smoke relation was not supported: {stats}")
    if {r.profile_mask for r in restrictions} != {
        rg.PROFILE_CAR, rg.PROFILE_OFFROAD, rg.PROFILE_MOTORCYCLE
    }:
        raise SystemExit(f"the except=bicycle;foot mask was not applied: {restrictions}")
    if any(r.value != "no_left_turn" for r in restrictions):
        raise SystemExit(f"the restriction value was not preserved: {restrictions}")
    graph = rg.build_graph(ways, restrictions)
    if len(graph.turn_restrictions) != 3:
        raise SystemExit(f"turn rules were not compiled into the road graph: {graph.turn_restrictions}")
    print("OSM node-via turn restriction extraction smoke test passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
