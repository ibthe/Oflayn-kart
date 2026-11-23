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
