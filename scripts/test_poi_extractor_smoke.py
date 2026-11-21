#!/usr/bin/env python3
"""Integration smoke test for PyOsmium node and closed-way POI extraction."""
from __future__ import annotations
import sqlite3
import tempfile
from pathlib import Path
from build_poi_database import build_from_pbf

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / "scripts/fixtures/poi-smoke.osm"


def main() -> int:
    try:
        import osmium  # noqa: F401
    except ImportError as exc:
        raise SystemExit("PyOsmium is not installed; install python3-osmium before this smoke test.") from exc
    with tempfile.TemporaryDirectory() as directory:
        output = Path(directory) / "poi.sqlite"
        summary = build_from_pbf(FIXTURE, output, (41.0, 39.95, 43.65, 46.8))
        if summary["poiCount"] != 2:
            raise SystemExit(f"Expected 2 test POIs (node + closed way), got {summary['poiCount']}.")
        db = sqlite3.connect(output)
        try:
            rows = db.execute(
                "SELECT poi_id, name_en, name_ru, name_ka, latitude, longitude FROM poi ORDER BY poi_id"
            ).fetchall()
            if len(rows) != 2:
                raise SystemExit(f"Unexpected POI row count: {len(rows)}")
            node = next(row for row in rows if row[0] == "node/1001")
            way = next(row for row in rows if row[0] == "way/2001")
            if node[1:4] != ("Test Restaurant", "Тестовый ресторан", "სატესტო რესტორანი"):
                raise SystemExit("Node's multilingual names were not preserved correctly.")
            if way[3] != "სატესტო მუზეუმი":
                raise SystemExit("Closed-way POI name was not preserved correctly.")
            if not (41.70 <= way[4] <= 41.73 and 44.80 <= way[5] <= 44.84):
                raise SystemExit("Closed-way centroid is outside the fixture area.")
            if db.execute("PRAGMA integrity_check").fetchone()[0] != "ok":
                raise SystemExit("SQLite integrity check failed.")
        finally:
            db.close()
    print("OK: PyOsmium extracted node and closed-way POIs, preserved EN/RU/KA names, and wrote a valid SQLite database.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
