#!/usr/bin/env python3
"""Validate the generated offline POI SQLite database and optional summary."""
from __future__ import annotations
import json
import sqlite3
import sys
from pathlib import Path


def verify(path: Path, summary_path: Path | None = None) -> dict:
    if not path.is_file() or path.stat().st_size < 4096:
        raise ValueError("POI SQLite database is missing or unexpectedly small")
    connection = sqlite3.connect(f"file:{path}?mode=ro", uri=True)
    try:
        integrity = connection.execute("PRAGMA integrity_check").fetchone()[0]
        if integrity != "ok":
            raise ValueError(f"SQLite integrity_check: {integrity}")
        tables = {row[0] for row in connection.execute("SELECT name FROM sqlite_master WHERE type='table'")}
        if not {"poi", "metadata"}.issubset(tables):
            raise ValueError("Required poi/metadata tables are missing")
        required_columns = {row[1] for row in connection.execute("PRAGMA table_info(poi)")}
        if not {"poi_id", "category", "name", "name_en", "name_ru", "name_ka", "latitude", "longitude", "search_text", "tags_json"}.issubset(required_columns):
            raise ValueError("POI table is missing required searchable multilingual columns")
        count = int(connection.execute("SELECT COUNT(*) FROM poi").fetchone()[0])
        if count < 100:
            raise ValueError(f"POI database contains only {count} rows; refusing to bundle incomplete output")
        invalid = int(connection.execute(
            "SELECT COUNT(*) FROM poi WHERE latitude < 41.0 OR latitude > 43.65 OR longitude < 39.95 OR longitude > 46.8"
        ).fetchone()[0])
        if invalid:
            raise ValueError(f"{invalid} POIs fall outside the configured Georgia bounding box")
        category_counts = {row[0]: row[1] for row in connection.execute(
            "SELECT category, COUNT(*) FROM poi GROUP BY category ORDER BY category"
        )}
        metadata = dict(connection.execute("SELECT key,value FROM metadata"))
        if metadata.get("schemaVersion") != "1" or int(metadata.get("poiCount", "0")) != count:
            raise ValueError("POI metadata version or count does not match the database")
        result = {"poiCount": count, "categoryCounts": category_counts, "schemaVersion": int(metadata["schemaVersion"])}
    finally:
        connection.close()
    if summary_path:
        summary = json.loads(summary_path.read_text(encoding="utf-8"))
        if summary.get("poiCount") != result["poiCount"]:
            raise ValueError("POI summary count does not match SQLite database")
    return result


def main() -> int:
    if len(sys.argv) not in (2, 3):
        print("Usage: verify_poi_database.py DATABASE.sqlite [SUMMARY.json]", file=sys.stderr)
        return 2
    try:
        result = verify(Path(sys.argv[1]), Path(sys.argv[2]) if len(sys.argv) == 3 else None)
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1
    print(f"OK: verified {result['poiCount']:,} offline POIs in {len(result['categoryCounts'])} categories.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
