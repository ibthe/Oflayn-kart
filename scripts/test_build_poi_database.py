#!/usr/bin/env python3
"""Fast unit tests for the offline POI database schema and category extraction."""
from __future__ import annotations
import sqlite3
import tempfile
import unittest
from pathlib import Path
from build_poi_database import ProgressReporter, PoiInput, classify_tags, create_database, insert_poi, normalise_search_text, point_in_bbox, polygon_centroid
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

    def test_transport_hubs_are_classified(self):
        self.assertEqual(classify_tags({"railway": "station", "public_transport": "station", "train": "yes"}), ("transport", "station"))
        self.assertEqual(classify_tags({"railway": "station", "station": "subway"}), ("transport", "subway_station"))
        self.assertEqual(classify_tags({"railway": "halt"}), ("transport", "halt"))
        self.assertEqual(classify_tags({"amenity": "bus_station"}), ("transport", "bus_station"))
        self.assertEqual(classify_tags({"public_transport": "station", "bus": "yes"}), ("transport", "bus_station"))
        self.assertEqual(classify_tags({"highway": "bus_stop"}), ("transport", "bus_stop"))
        self.assertEqual(classify_tags({"aeroway": "aerodrome"}), ("transport", "aerodrome"))
        self.assertEqual(classify_tags({"amenity": "ferry_terminal"}), ("transport", "ferry_terminal"))
        self.assertEqual(classify_tags({"aerialway": "station"}), ("transport", "cable_car_station"))
        self.assertEqual(classify_tags({"barrier": "border_control"}), ("transport", "border_crossing"))
        # a station that also carries a tourism tag stays a station
        self.assertEqual(classify_tags({"railway": "station", "tourism": "attraction"}), ("transport", "station"))

    def test_duplicate_and_noise_transport_objects_are_skipped(self):
        self.assertIsNone(classify_tags({"public_transport": "stop_position", "bus": "yes"}))
        self.assertIsNone(classify_tags({"public_transport": "platform", "train": "yes"}))
        self.assertIsNone(classify_tags({"railway": "station", "disused": "yes"}))
        self.assertIsNone(classify_tags({"amenity": "shelter", "shelter_type": "public_transport"}))
        self.assertEqual(classify_tags({"public_transport": "platform", "bus": "yes"}), ("transport", "bus_stop"))

    def test_hiking_objects(self):
        self.assertEqual(classify_tags({"natural": "saddle"}), ("nature", "saddle"))
        self.assertEqual(classify_tags({"mountain_pass": "yes"}), ("nature", "mountain_pass"))
        self.assertEqual(classify_tags({"amenity": "shelter", "shelter_type": "basic_hut"}), ("nature", "shelter"))

    def test_russian_and_georgian_category_words_are_searchable(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "poi.sqlite"
            connection = create_database(path)
            for number, (category, sub, name) in enumerate((
                ("transport", "station", "Tbilisi Central"),
                ("transport", "bus_station", "Ortachala"),
                ("transport", "aerodrome", "Shota Rustaveli Airport"),
            ), start=1):
                insert_poi(connection, PoiInput(
                    poi_id=f"node/{number}", osm_type="node", osm_id=number, category=category,
                    subcategory=sub, name=name, name_en=name, name_ru="", name_ka="",
                    latitude=41.7, longitude=44.8, tags_json="{}",
                ))
            connection.commit()
            def hits(term):
                return {r[0] for r in connection.execute("SELECT poi_id FROM poi WHERE instr(search_text, ?) > 0", (term,))}
            # substring search: "вокзал" also finds "автовокзал", which is desirable
            self.assertEqual(hits("вокзал"), {"node/1", "node/2"})
            self.assertEqual(hits("автовокзал"), {"node/2"})
            self.assertEqual(hits("аэропорт"), {"node/3"})
            self.assertEqual(hits("აეროპორტი"), {"node/3"})
            connection.close()

    def test_polygon_centroid_is_area_weighted(self):
        square = [(0.0, 0.0), (0.0, 2.0), (2.0, 2.0), (2.0, 0.0)]
        lat, lon = polygon_centroid(square)
        self.assertAlmostEqual(lat, 1.0)
        self.assertAlmostEqual(lon, 1.0)
        # many extra vertices on one side must not drag the result
        skewed = [(0.0, 0.0), (0.0, 0.5), (0.0, 1.0), (0.0, 1.5), (0.0, 2.0), (2.0, 2.0), (2.0, 0.0)]
        lat, lon = polygon_centroid(skewed)
        self.assertAlmostEqual(lat, 1.0)
        self.assertAlmostEqual(lon, 1.0)
        self.assertEqual(polygon_centroid([(1.0, 1.0), (1.0, 1.0), (1.0, 1.0)]), (1.0, 1.0))

    def test_multipolygon_centroid_weights_outer_rings_by_area(self):
        large = [(0.0, 0.0), (0.0, 4.0), (4.0, 4.0), (4.0, 0.0)]
        small = [(10.0, 10.0), (10.0, 11.0), (11.0, 11.0), (11.0, 10.0)]
        lat, lon = multipolygon_centroid([large, small])
        self.assertGreater(lat, 1.0)
        self.assertLess(lat, 2.0)
        self.assertGreater(lon, 1.0)
        self.assertLess(lon, 2.0)
        self.assertIsNone(multipolygon_centroid([]))
        self.assertIsNone(multipolygon_centroid([[(1.0, 1.0), (1.0, 1.0)]]))

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

    def test_progress_reporter_emits_entity_heartbeat(self):
        now = [0.0]
        messages = []
        reporter = ProgressReporter(
            every_entities=10,
            every_seconds=999,
            emit=messages.append,
            clock=lambda: now[0],
        )
        stats = {"nodesSeen": 9, "waysSeen": 0, "poisWritten": 1}
        reporter.report(stats)
        self.assertEqual(messages, [])
        stats["nodesSeen"] = 10
        reporter.report(stats)
        self.assertEqual(len(messages), 1)
        self.assertIn("entities=10", messages[0])
        self.assertIn("POIs=1", messages[0])

    def test_progress_reporter_emits_time_heartbeat_and_final_summary(self):
        now = [0.0]
        messages = []
        reporter = ProgressReporter(
            every_entities=1_000_000,
            every_seconds=15,
            emit=messages.append,
            clock=lambda: now[0],
        )
        stats = {"nodesSeen": 25, "waysSeen": 5, "poisWritten": 2}
        reporter.report(stats)
        now[0] = 15.1
        reporter.report(stats)
        self.assertEqual(len(messages), 1)
        self.assertIn("entities=30", messages[0])
        reporter.report(stats, final=True)
        self.assertEqual(len(messages), 2)
        self.assertIn("OSM scan complete", messages[-1])
        self.assertIn("elapsed=15.1s", messages[-1])

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
