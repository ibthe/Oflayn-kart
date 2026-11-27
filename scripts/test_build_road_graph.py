#!/usr/bin/env python3
"""Tests for the offline road graph: tag classification, edge splitting, binary format and routing."""
from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

import road_graph_format as rg


def way(refs, coords, **tags):
    tags.setdefault("highway", "residential")
    info = rg.classify_way(tags)
    assert info is not None, tags
    return rg.WayRecord(info, list(refs), [round(c[0] * 1e6) for c in coords], [round(c[1] * 1e6) for c in coords])


A, B, C = (41.700, 44.800), (41.700, 44.810), (41.700, 44.820)
N, S = (41.710, 44.810), (41.690, 44.810)
MID = (41.7055, 44.8155)


def sample_ways():
    return [
        way([1, 2, 3], [A, B, C], name="Main street"),
        way([4, 2, 5], [N, B, S], name="Cross street"),
        way([3, 6, 4], [C, MID, N], oneway="yes", name="One way road"),
    ]


class ClassificationTest(unittest.TestCase):
    def test_residential_is_open_to_everyone_both_ways(self):
        info = rg.classify_way({"highway": "residential"})
        self.assertEqual(info.fwd, 15)
        self.assertEqual(info.back, 15)

    def test_oneway_restricts_vehicles_but_not_pedestrians(self):
        info = rg.classify_way({"highway": "residential", "oneway": "yes"})
        self.assertEqual(info.fwd, 15)
        self.assertEqual(info.back, rg.PROFILE_FOOT | rg.PROFILE_BIKE * 0)

    def test_reverse_oneway(self):
        info = rg.classify_way({"highway": "residential", "oneway": "-1"})
        self.assertEqual(info.fwd, rg.PROFILE_FOOT)
        self.assertEqual(info.back, 15)

    def test_motorway_is_car_only_and_oneway(self):
        info = rg.classify_way({"highway": "motorway"})
        self.assertEqual(info.fwd, rg.PROFILE_CAR | rg.PROFILE_OFFROAD)
        self.assertEqual(info.back, 0)

    def test_roundabout_is_oneway_and_flagged(self):
        info = rg.classify_way({"highway": "tertiary", "junction": "roundabout"})
        self.assertTrue(info.flags & rg.FLAG_ROUNDABOUT)
        self.assertEqual(info.back & rg.PROFILE_CAR, 0)

    def test_footway_is_foot_only(self):
        info = rg.classify_way({"highway": "footway"})
        self.assertEqual(info.fwd, rg.PROFILE_FOOT)

    def test_track_is_offroad_bike_foot_and_unpaved(self):
        info = rg.classify_way({"highway": "track"})
        self.assertEqual(info.fwd & rg.PROFILE_CAR, 0)
        self.assertTrue(info.fwd & rg.PROFILE_OFFROAD)
        self.assertTrue(info.flags & rg.FLAG_UNPAVED)

    def test_private_and_unknown_are_dropped(self):
        self.assertIsNone(rg.classify_way({"highway": "service", "access": "private"}))
        self.assertIsNone(rg.classify_way({"highway": "service", "service": "parking_aisle"}))
        self.assertIsNone(rg.classify_way({"highway": "proposed"}))
        self.assertIsNone(rg.classify_way({"building": "yes"}))

    def test_vehicle_ban_keeps_pedestrians(self):
        info = rg.classify_way({"highway": "residential", "motor_vehicle": "no"})
        self.assertEqual(info.fwd & rg.PROFILE_CAR, 0)
        self.assertTrue(info.fwd & rg.PROFILE_FOOT)

    def test_smoothness_penalties_and_vehicle_restrictions(self):
        bad = rg.classify_way({"highway": "track", "smoothness": "bad"})
        self.assertTrue(bad.flags & rg.FLAG_ROUGH)
        self.assertTrue(bad.fwd & rg.PROFILE_CAR)
        self.assertTrue(bad.fwd & rg.PROFILE_OFFROAD)

        horrible = rg.classify_way({"highway": "track", "smoothness": "horrible"})
        self.assertFalse(horrible.fwd & rg.PROFILE_CAR)
        self.assertFalse(horrible.fwd & rg.PROFILE_BIKE)
        self.assertTrue(horrible.fwd & rg.PROFILE_OFFROAD)
        self.assertTrue(horrible.fwd & rg.PROFILE_FOOT)

        very_horrible = rg.classify_way({"highway": "track", "smoothness": "very_horrible"})
        self.assertTrue(very_horrible.flags & rg.FLAG_SEVERE)
        self.assertTrue(very_horrible.fwd & rg.PROFILE_OFFROAD)
        self.assertFalse(very_horrible.fwd & rg.PROFILE_CAR)

        self.assertIsNone(rg.classify_way({"highway": "track", "smoothness": "impassable"}))

    def test_smoothness_reduces_route_speed(self):
        paved = rg.speed_mps(rg.PROFILE_OFFROAD, 15, 0)
        rough = rg.speed_mps(rg.PROFILE_OFFROAD, 15, rg.FLAG_ROUGH)
        severe = rg.speed_mps(rg.PROFILE_OFFROAD, 15, rg.FLAG_SEVERE)
        self.assertGreater(paved, rough)
        self.assertGreater(rough, severe)

    def test_name_prefers_russian(self):
        self.assertEqual(rg.classify_way({"highway": "residential", "name": "X", "name:ru": "Икс"}).name, "Икс")


class GraphBuildTest(unittest.TestCase):
    def test_ways_split_at_junctions_and_keep_shape_points(self):
        data = rg.build_graph(sample_ways())
        self.assertEqual(len(data.vlat), 5)  # A, B, C, N, S (node 6 is only a shape point)
        self.assertEqual(len(data.edges), 5)
        shaped = [edge for edge in data.edges if edge[3]]
        self.assertEqual(len(shaped), 1)
        self.assertEqual(sorted(data.names), ["Cross street", "Main street", "One way road"])

    def test_binary_roundtrip(self):
        data = rg.build_graph(sample_ways())
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "roads.bin"
            summary = rg.write_graph(path, data)
            graph = rg.Graph.load(path)
        self.assertEqual((graph.V, graph.E), (5, 5))
        self.assertEqual(summary["arcCount"], 10)
        self.assertEqual(len(graph.arcs), 10)
        self.assertEqual(sorted(graph.names), sorted(data.names))
        shaped = [e for e in range(graph.E) if graph.edges[e][4] > 0]
        self.assertEqual(len(shaped), 1)
        self.assertAlmostEqual(graph.edge_points(shaped[0])[1][0], MID[0], places=5)


class RoutingTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "roads.bin"
            rg.write_graph(path, rg.build_graph(sample_ways()))
            cls.graph = rg.Graph.load(path)

    def test_straight_route_through_junction(self):
        result = self.graph.route(rg.PROFILE_CAR, 41.7001, 44.8002, 41.6999, 44.8198)
        self.assertIsNotNone(result)
        self.assertAlmostEqual(result["distance_m"], rg.dist_m(41.7, 44.8002, 41.7, 44.8198), delta=15)

    def test_oneway_forces_detour_for_cars_but_not_for_pedestrians(self):
        c_to_n_car = self.graph.route(rg.PROFILE_CAR, 41.7, 44.8199, 41.7099, 44.8101)
        n_to_c_car = self.graph.route(rg.PROFILE_CAR, 41.7099, 44.8101, 41.7, 44.8199)
        n_to_c_foot = self.graph.route(rg.PROFILE_FOOT, 41.7099, 44.8101, 41.7, 44.8199)
        self.assertLess(c_to_n_car["distance_m"], 1500)    # uses the one-way road C -> N
        self.assertGreater(n_to_c_car["distance_m"], 1800)  # must go back through the junction
        self.assertLess(n_to_c_foot["distance_m"], 1500)    # pedestrians ignore the one-way
        self.assertLess(c_to_n_car["seconds"], n_to_c_car["seconds"])

    def test_route_on_the_same_edge(self):
        result = self.graph.route(rg.PROFILE_CAR, 41.7, 44.8020, 41.7, 44.8060)
        self.assertAlmostEqual(result["distance_m"], rg.dist_m(41.7, 44.802, 41.7, 44.806), delta=10)

    def test_same_edge_against_oneway_is_not_taken(self):
        # Two points on the one-way road, target "behind" the start: a car cannot drive backwards.
        forward = self.graph.route(rg.PROFILE_CAR, 41.7002, 44.8198, 41.7054, 44.8156)
        backward = self.graph.route(rg.PROFILE_CAR, 41.7054, 44.8156, 41.7002, 44.8198)
        self.assertIsNotNone(forward)
        self.assertLess(forward["distance_m"], 1000)
        self.assertGreater(backward["distance_m"], 1500)

    def test_snap_too_far_returns_none(self):
        self.assertIsNone(self.graph.route(rg.PROFILE_CAR, 42.5, 44.8, 41.7, 44.81, max_snap_m=2000))

    def test_pedestrian_only_edge_is_invisible_to_cars(self):
        ways = sample_ways() + [way([7, 8], [(41.695, 44.80), (41.695, 44.82)], highway="footway")]
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "roads.bin"
            rg.write_graph(path, rg.build_graph(ways))
            graph = rg.Graph.load(path)
        self.assertIsNone(graph.route(rg.PROFILE_CAR, 41.6951, 44.8001, 41.6951, 44.8199, max_snap_m=300) and None)
        foot = graph.route(rg.PROFILE_FOOT, 41.6951, 44.8001, 41.6951, 44.8199)
        self.assertIsNotNone(foot)
        self.assertLess(foot["distance_m"], 1800)


if __name__ == "__main__":
    unittest.main()
