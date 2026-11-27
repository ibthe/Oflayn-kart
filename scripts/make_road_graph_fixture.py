#!/usr/bin/env python3
"""Regenerate the tiny road graph used by the Kotlin router tests (app/src/test/resources/tiny-roads.bin).

The fixture is built by the same Python code that builds the real graph, so the Kotlin reader
is tested against the real file format.
"""
from __future__ import annotations

from pathlib import Path

import road_graph_format as rg
from test_build_road_graph import sample_ways

OUT = Path(__file__).resolve().parents[1] / "app/src/test/resources/tiny-roads.bin"

if __name__ == "__main__":
    OUT.parent.mkdir(parents=True, exist_ok=True)
    summary = rg.write_graph(OUT, rg.build_graph(sample_ways()))
    print(f"Wrote {OUT} ({summary['sizeBytes']} bytes, {summary['vertexCount']} vertices, {summary['edgeCount']} edges)")
    ways = sample_ways()
    for label, kind in (("tiny-turns.bin", rg.TURN_NO), ("tiny-only-turns.bin", rg.TURN_ONLY)):
        target = OUT.with_name(label)
        restriction = rg.TurnRestriction(100, 200, 2, rg.PROFILE_CAR, kind)
        turn_summary = rg.write_graph(target, rg.build_graph(ways, [restriction]))
        print(f"Wrote {target} ({turn_summary['sizeBytes']} bytes, {turn_summary['turnRestrictionCount']} turn rules)")
