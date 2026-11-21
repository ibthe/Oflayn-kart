#!/usr/bin/env python3
"""Fast unit tests for the offline POI database schema and category extraction."""
from __future__ import annotations
import sqlite3
import tempfile
import unittest
from pathlib import Path
from build_poi_database import PoiInput, classify_tags, create_database, insert_poi, normalise_search_text, point_in_bbox
from verify_poi_database import verify


class PoiDatabaseTests(unittest.TestCase):
    def test_common_categories(self):
        self.assertEqual(classify_tags({"amenity": "restaurant"}), ("food", "restaurant"))
        self.assertEqual(classify_tags({"tourism": "museum"}), ("tourism", "museum"))
        self.assertEqual(classify_tags({"amenity": "pharmacy"}), ("medical", "pharmacy"))
        self.assertEqual(classify_tags({"place": "village"}), ("settlement", "village"))
        self.assertEqual(classify_tags({"tourism": "hotel"}), ("lodging", "hotel"))
        self.assertEqual(classify_tags({"railway": "station"}), ("transport", "station"))
        self.assertIsNone(classify_tags({"highway": "residential"}))

    def test_bbox(self):
        bbox = (41.0, 39.95, 43.65, 46.8)
        self.assertTrue(point_in_bbox(42.0, 44.0, bbox))
        self.assertFalse(point_in_bbox(50.0, 44.0, bbox))

    def test_search_text_handles_three_scripts(self):
        value = normalise_search_text(["Tbilisi", "თბილისი", "Тбилиси", "Tbilisi"])
        self.assertIn("tbilisi", value)
        self.assertIn("თბილისი", value)
        self.assertIn("тбилиси", value)
        self.assertEqual(value.count("tbilisi"), 1)

    def test_database_schema_and_multilingual_row(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "poi.sqlite"
            connection = create_database(path)
            insert_poi(connection, PoiInput(
                poi_id="node/1", osm_type="node", osm_id=1, category="settlement",
                subcategory="city", name="Tbilisi", name_en="Tbilisi", name_ru="Тбилиси",
                name_ka="თბილისი", latitude=41.7151, longitude=44.8271,
                tags_json='{"place":"city"}',
            ))
            connection.commit()
            row = connection.execute("SELECT name_en,name_ru,name_ka,search_text FROM poi WHERE poi_id='node/1'").fetchone()
            connection.close()
            self.assertEqual(row[:3], ("Tbilisi", "Тбилиси", "თბილისი"))
            self.assertIn("тбилиси", row[3])
            check = sqlite3.connect(path)
            self.assertEqual(check.execute("PRAGMA integrity_check").fetchone()[0], "ok")
            check.close()

    def test_search_text_indexes_public_address_and_business_details(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "poi.sqlite"
            connection = create_database(path)
            insert_poi(connection, PoiInput(
                poi_id="node/2", osm_type="node", osm_id=2, category="food",
                subcategory="restaurant", name="Cafe", name_en="Cafe", name_ru="Кафе",
                name_ka="კაფე", latitude=41.7, longitude=44.8,
                tags_json='{"addr:street":"Rustaveli Avenue","cuisine":"Georgian","opening_hours":"Mo-Su 10:00-22:00","phone":"+995 555 010203"}',
            ))
            connection.commit()
            search_text = connection.execute("SELECT search_text FROM poi WHERE poi_id='node/2'").fetchone()[0]
            connection.close()
            self.assertIn("rustaveli avenue", search_text)
            self.assertIn("georgian", search_text)
            self.assertIn("mo-su 10:00-22:00".casefold(), search_text)
            self.assertIn("+995 555 010203".casefold(), search_text)

    def test_database_verifier_checks_metadata_and_rows(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "poi.sqlite"
            connection = create_database(path)
            for index in range(120):
                insert_poi(connection, PoiInput(
                    poi_id=f"node/{index + 1}", osm_type="node", osm_id=index + 1,
                    category="tourism", subcategory="attraction", name=f"Place {index + 1}",
                    name_en=f"Place {index + 1}", name_ru="", name_ka="",
                    latitude=41.5, longitude=44.0, tags_json='{"tourism":"attraction"}',
                ))
            connection.execute("INSERT INTO metadata(key,value) VALUES ('schemaVersion','1')")
            connection.execute("INSERT INTO metadata(key,value) VALUES ('poiCount','120')")
            connection.commit()
            connection.close()
            result = verify(path)
            self.assertEqual(result["poiCount"], 120)
            self.assertEqual(result["categoryCounts"], {"tourism": 120})


if __name__ == "__main__":
    unittest.main(verbosity=2)
