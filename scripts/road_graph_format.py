#!/usr/bin/env python3
"""Offline road graph for Georgia: OSM classification, compact binary format, reference router.

The Android app routes on `georgia-roads.bin` entirely on the device. This module is the
single specification of that file: how OSM ways become graph edges, how the binary file is
laid out, and (as a reference implementation used by tests and CI sanity checks) how a route
is searched. The Kotlin `RoadGraph`/`Router` classes mirror the layout and the algorithm.

File layout, all little-endian:
  header (64 bytes): magic "GRT1", version, vertexCount V, edgeCount E, shapeBytes,
      nameCount N, nameBytes, minLat, minLon, maxLat, maxLon (degrees * 1e6), arcCount, zero padding
  vertices   V * (lat int32, lon int32)
  edges      E * 28 bytes: from int32, to int32, lengthDecimetres int32, shapeOffsetBytes int32,
      shapeCount uint16, class uint8, flags uint8, fwdMask uint8, backMask uint8, pad uint16, nameIndex int32 (-1 none)
  shape      pairs (lat int32, lon int32): intermediate points of each edge in digitised direction
  arcStart   (V + 1) * int32 offsets into arcs
  arcs       arcCount * int32: (edgeIndex << 1) | direction (0 = from->to, 1 = to->from)
  nameOffsets (N + 1) * int32, then nameBytes of UTF-8
"""
from __future__ import annotations

import heapq
import math
import struct
import sys
from array import array
from collections import Counter
from dataclasses import dataclass, field
from pathlib import Path

MAGIC = b"GRT1"
FORMAT_VERSION = 1
HEADER_SIZE = 64
EDGE_STRUCT = struct.Struct("<iiiiHBBBBHi")
EDGE_SIZE = EDGE_STRUCT.size  # 28
assert EDGE_SIZE == 28

PROFILE_CAR, PROFILE_OFFROAD, PROFILE_BIKE, PROFILE_FOOT = 1, 2, 4, 8
PROFILES = {"car": PROFILE_CAR, "offroad": PROFILE_OFFROAD, "bike": PROFILE_BIKE, "foot": PROFILE_FOOT}

HIGHWAY_CLASS = {
    "motorway": 1, "motorway_link": 2, "trunk": 3, "trunk_link": 4, "primary": 5, "primary_link": 6,
    "secondary": 7, "secondary_link": 8, "tertiary": 9, "tertiary_link": 10, "unclassified": 11,
    "residential": 12, "living_street": 13, "service": 14, "track": 15, "path": 16, "footway": 17,
    "cycleway": 18, "steps": 19, "pedestrian": 20, "road": 21,
}
FLAG_UNPAVED = 1
FLAG_ROUNDABOUT = 2
FLAG_ROUGH = 4
FLAG_SEVERE = 8

# Average speeds in km/h per class code (index = class). Mirrored in Kotlin `RoutingProfiles`.
_CAR = {1: 90, 2: 50, 3: 75, 4: 45, 5: 60, 6: 40, 7: 50, 8: 35, 9: 40, 10: 30, 11: 35, 12: 25, 13: 10, 14: 15, 15: 15, 21: 30}
_OFFROAD = {**_CAR, 15: 20}
_BIKE = {3: 14, 4: 14, 5: 15, 6: 15, 7: 16, 8: 16, 9: 17, 10: 17, 11: 16, 12: 17, 13: 15, 14: 15, 15: 12, 16: 10, 17: 12, 18: 18, 21: 15}
_FOOT = {c: 5 for c in range(3, 22)}
_FOOT.update({16: 4, 19: 3})
SPEED_KMH = {PROFILE_CAR: _CAR, PROFILE_OFFROAD: _OFFROAD, PROFILE_BIKE: _BIKE, PROFILE_FOOT: _FOOT}
UNPAVED_FACTOR = {PROFILE_CAR: 0.6, PROFILE_OFFROAD: 0.85, PROFILE_BIKE: 0.7, PROFILE_FOOT: 0.9}
MAX_SPEED_MPS = {PROFILE_CAR: 25.0, PROFILE_OFFROAD: 25.0, PROFILE_BIKE: 5.0, PROFILE_FOOT: 1.5}


def speed_mps(profile: int, cls: int, flags: int) -> float:
    kmh = SPEED_KMH[profile].get(cls, 10)
    if flags & FLAG_UNPAVED:
        kmh = max(kmh * UNPAVED_FACTOR[profile], 3.0)
    if flags & FLAG_SEVERE:
        kmh *= {PROFILE_CAR: 0.30, PROFILE_OFFROAD: 0.55, PROFILE_BIKE: 0.20, PROFILE_FOOT: 0.65}[profile]
    elif flags & FLAG_ROUGH:
        kmh *= {PROFILE_CAR: 0.65, PROFILE_OFFROAD: 0.80, PROFILE_BIKE: 0.55, PROFILE_FOOT: 0.85}[profile]
    return max(kmh, 1.0) / 3.6


def dist_m(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    """Haversine distance in metres between two points given in degrees."""
    p1, p2 = math.radians(lat1), math.radians(lat2)
    a = math.sin((p2 - p1) / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(math.radians(lon2 - lon1) / 2) ** 2
    return 2 * 6371008.8 * math.asin(math.sqrt(a))


# --------------------------------------------------------------------------- OSM tags -> road

@dataclass
class RoadInfo:
    cls: int
    flags: int
    fwd: int
    back: int
    name: str | None = None


@dataclass
class WayRecord:
    info: RoadInfo
    refs: list
    lats: list  # degrees * 1e6
    lons: list


_DENY = {"no", "private", "customers", "permit"}
_ALLOW = {"yes", "designated", "permissive", "destination", "official", "delivery"}
_UNPAVED_SURFACES = {"unpaved", "dirt", "gravel", "ground", "grass", "sand", "mud", "earth", "pebblestone", "dirt/sand", "grass_paver", "woodchips"}
_CAR_CLASSES = set(range(1, 15)) | {21}


def _tri(tags: dict, *keys: str):
    """First present access tag among keys: True (allowed), False (denied) or None."""
    for key in keys:
        value = tags.get(key)
        if value is None:
            continue
        if value in _DENY:
            return False
        if value in _ALLOW:
            return True
    return None


def classify_way(tags: dict) -> RoadInfo | None:
    highway = tags.get("highway")
    cls = HIGHWAY_CLASS.get(highway)
    if cls is None:
        return None
    if tags.get("area") == "yes" or tags.get("highway") == "proposed":
        return None
    if cls == 14 and tags.get("service") in {"parking_aisle", "drive-through", "emergency_access"}:
        return None

    general = _tri(tags, "access")
    car_specific = _tri(tags, "motor_vehicle", "motorcar", "vehicle")
    bike_specific = _tri(tags, "bicycle", "vehicle")
    foot_specific = _tri(tags, "foot")

    def pick(specific, base_ok):
        if specific is not None:
            return specific
        if general is False:
            return False
        return base_ok

    car_ok = pick(car_specific, cls in _CAR_CLASSES)
    if cls in _CAR_CLASSES and car_specific is True:
        car_ok = True
    offroad_ok = pick(car_specific, cls in _CAR_CLASSES or cls == 15)
    if cls == 15 and car_specific is False:
        offroad_ok = False
    if cls == 15 and general is False and car_specific is None:
        offroad_ok = False

    bike_base = cls not in {1, 2, 19, 20}
    if cls in {16, 17, 20}:
        bike_base = False  # needs an explicit bicycle=yes/designated
    bike_ok = pick(bike_specific, bike_base)
    if cls in {16, 17, 20} and bike_specific is True:
        bike_ok = True

    foot_base = cls not in {1, 2, 18}
    foot_ok = pick(foot_specific, foot_base)
    if cls == 18 and foot_specific is True:
        foot_ok = True

    # Smoothness is a routing quality constraint, not just a visual tag.
    smoothness = tags.get("smoothness")
    if smoothness in {"horrible", "very_horrible", "impassable"}:
        car_ok = False
        bike_ok = False
    if smoothness == "impassable":
        offroad_ok = False
        foot_ok = False

    # Direction of travel.
    oneway = tags.get("oneway")
    roundabout = tags.get("junction") in {"roundabout", "circular"}
    implied = cls in {1, 2} or roundabout
    fwd_only = oneway in {"yes", "true", "1"} or (implied and oneway not in {"no", "false", "0", "-1", "reverse"})
    back_only = oneway in {"-1", "reverse"}
    bike_free = tags.get("oneway:bicycle") == "no" or any(
        str(tags.get(k, "")).startswith("opposite") for k in ("cycleway", "cycleway:left", "cycleway:right", "cycleway:both")
    ) or cls == 18

    fwd = back = 0
    for bit, ok, ignore_oneway in (
        (PROFILE_CAR, car_ok, False), (PROFILE_OFFROAD, offroad_ok, False),
        (PROFILE_BIKE, bike_ok, bike_free), (PROFILE_FOOT, foot_ok, True),
    ):
        if not ok:
            continue
        if ignore_oneway or not (fwd_only or back_only):
            fwd |= bit
            back |= bit
        elif fwd_only:
            fwd |= bit
        else:
            back |= bit
    if not (fwd | back):
        return None

    flags = 0
    surface = tags.get("surface")
    tracktype = tags.get("tracktype")
    if surface in _UNPAVED_SURFACES or (surface is None and (cls == 15 or tracktype in {"grade3", "grade4", "grade5"})):
        flags |= FLAG_UNPAVED
    if roundabout:
        flags |= FLAG_ROUNDABOUT
    if smoothness in {"bad", "very_bad", "horrible"}:
        flags |= FLAG_ROUGH
    elif smoothness == "very_horrible":
        flags |= FLAG_SEVERE
    name = tags.get("name:ru") or tags.get("name") or tags.get("name:en") or tags.get("ref")
    return RoadInfo(cls, flags, fwd, back, name)


# --------------------------------------------------------------------------- graph building

@dataclass
class GraphData:
    vlat: array = field(default_factory=lambda: array("i"))
    vlon: array = field(default_factory=lambda: array("i"))
    edges: list = field(default_factory=list)  # (from, to, lenDm, shape[(lat,lon)], cls, flags, fwd, back, nameIdx)
    names: list = field(default_factory=list)


def build_graph(ways) -> GraphData:
    """Split road ways at junctions and endpoints into edges between graph vertices."""
    ways = list(ways)
    counts: Counter = Counter()
    for way in ways:
        counts.update(way.refs)
        counts[way.refs[0]] += 1
        counts[way.refs[-1]] += 1
    data = GraphData()
    vertex_of: dict[int, int] = {}
    name_index: dict[str, int] = {}

    def vertex(ref: int, lat: int, lon: int) -> int:
        index = vertex_of.get(ref)
        if index is None:
            index = len(data.vlat)
            vertex_of[ref] = index
            data.vlat.append(lat)
            data.vlon.append(lon)
        return index

    for way in ways:
        info = way.info
        name_idx = -1
        if info.name:
            name_idx = name_index.setdefault(info.name, len(name_index))
            if name_idx == len(data.names):
                data.names.append(info.name)
        start = 0
        length = 0.0
        shape: list = []
        for i in range(1, len(way.refs)):
            length += dist_m(way.lats[i - 1] / 1e6, way.lons[i - 1] / 1e6, way.lats[i] / 1e6, way.lons[i] / 1e6)
            if counts[way.refs[i]] >= 2:
                u = vertex(way.refs[start], way.lats[start], way.lons[start])
                v = vertex(way.refs[i], way.lats[i], way.lons[i])
                if length > 0.0 or u != v:
                    data.edges.append((u, v, int(round(length * 10)), shape, info.cls, info.flags, info.fwd, info.back, name_idx))
                start = i
                length = 0.0
                shape = []
            else:
                shape.append((way.lats[i], way.lons[i]))
    return data


def _le(a: array) -> bytes:
    if sys.byteorder == "big":
        a = array(a.typecode, a)
        a.byteswap()
    return a.tobytes()


def write_graph(path: Path, data: GraphData) -> dict:
    vcount, ecount = len(data.vlat), len(data.edges)
    vert = array("i")
    for lat, lon in zip(data.vlat, data.vlon):
        vert.append(lat)
        vert.append(lon)
    shape = array("i")
    edge_blob = bytearray()
    degree = [0] * (vcount + 1)
    for u, v, length_dm, pts, cls, flags, fwd, back, name_idx in data.edges:
        offset = len(shape) * 4
        for lat, lon in pts:
            shape.append(lat)
            shape.append(lon)
        edge_blob += EDGE_STRUCT.pack(u, v, length_dm, offset, len(pts), cls, flags, fwd, back, 0, name_idx)
        degree[u] += 1
        degree[v] += 1
    arc_start = array("i", [0] * (vcount + 1))
    for i in range(vcount):
        arc_start[i + 1] = arc_start[i] + degree[i]
    fill = array("i", arc_start[:vcount])
    arcs = array("i", [0] * arc_start[vcount])
    for index, edge in enumerate(data.edges):
        u, v = edge[0], edge[1]
        arcs[fill[u]] = index << 1
        fill[u] += 1
        arcs[fill[v]] = (index << 1) | 1
        fill[v] += 1
    name_bytes = bytearray()
    name_offsets = array("i", [0])
    for name in data.names:
        name_bytes += name.encode("utf-8")
        name_offsets.append(len(name_bytes))
    min_lat = min(data.vlat) if vcount else 0
    max_lat = max(data.vlat) if vcount else 0
    min_lon = min(data.vlon) if vcount else 0
    max_lon = max(data.vlon) if vcount else 0
    header = struct.pack("<4s11i", MAGIC, FORMAT_VERSION, vcount, ecount, len(shape) * 4, len(data.names),
                         len(name_bytes), min_lat, min_lon, max_lat, max_lon, len(arcs))
    header += b"\x00" * (HEADER_SIZE - len(header))
    with path.open("wb") as handle:
        for chunk in (header, _le(vert), bytes(edge_blob), _le(shape), _le(arc_start), _le(arcs), _le(name_offsets), bytes(name_bytes)):
            handle.write(chunk)
    return {"vertexCount": vcount, "edgeCount": ecount, "arcCount": len(arcs), "nameCount": len(data.names),
            "sizeBytes": path.stat().st_size, "bbox": [min_lat / 1e6, min_lon / 1e6, max_lat / 1e6, max_lon / 1e6]}


# --------------------------------------------------------------------------- reading + reference router

@dataclass
class Snap:
    edge: int
    segment: int
    lat: float
    lon: float
    along: float   # metres from the edge's `from` vertex
    distance: float  # metres from the query point


class Graph:
    def __init__(self, blob: bytes):
        if len(blob) < HEADER_SIZE or blob[:4] != MAGIC:
            raise ValueError("not a Georgia road graph")
        (_, version, self.V, self.E, shape_bytes, self.N, name_bytes,
         self.min_lat, self.min_lon, self.max_lat, self.max_lon, arc_count) = struct.unpack_from("<4s11i", blob, 0)
        if version != FORMAT_VERSION:
            raise ValueError(f"unsupported road graph version {version}")
        pos = HEADER_SIZE
        self.vert = array("i")
        self.vert.frombytes(blob[pos:pos + 8 * self.V])
        pos += 8 * self.V
        self.edges = list(EDGE_STRUCT.iter_unpack(blob[pos:pos + EDGE_SIZE * self.E]))
        pos += EDGE_SIZE * self.E
        self.shape = array("i")
        self.shape.frombytes(blob[pos:pos + shape_bytes])
        pos += shape_bytes
        self.arc_start = array("i")
        self.arc_start.frombytes(blob[pos:pos + 4 * (self.V + 1)])
        pos += 4 * (self.V + 1)
        self.arcs = array("i")
        self.arcs.frombytes(blob[pos:pos + 4 * arc_count])
        pos += 4 * arc_count
        offsets = array("i")
        offsets.frombytes(blob[pos:pos + 4 * (self.N + 1)])
        pos += 4 * (self.N + 1)
        text = blob[pos:pos + name_bytes]
        self.names = [text[offsets[i]:offsets[i + 1]].decode("utf-8") for i in range(self.N)]
        if sys.byteorder == "big":
            raise RuntimeError("reference reader assumes a little-endian host")

    @classmethod
    def load(cls, path: Path) -> "Graph":
        return cls(Path(path).read_bytes())

    # edge accessors -------------------------------------------------------
    def vertex(self, v: int) -> tuple:
        return self.vert[2 * v] / 1e6, self.vert[2 * v + 1] / 1e6

    def edge_points(self, e: int) -> list:
        u, v, _len, off, count = self.edges[e][:5]
        pts = [self.vertex(u)]
        base = off // 4
        for k in range(count):
            pts.append((self.shape[base + 2 * k] / 1e6, self.shape[base + 2 * k + 1] / 1e6))
        pts.append(self.vertex(v))
        return pts

    def edge_length_m(self, e: int) -> float:
        return self.edges[e][2] / 10.0

    def edge_seconds(self, profile: int, e: int, metres: float | None = None) -> float:
        edge = self.edges[e]
        return (self.edge_length_m(e) if metres is None else metres) / speed_mps(profile, edge[5], edge[6])

    def usable(self, profile: int, e: int, direction: int) -> bool:
        edge = self.edges[e]
        return bool((edge[7] if direction == 0 else edge[8]) & profile)

    # snapping -------------------------------------------------------------
    def snap(self, profile: int, lat: float, lon: float, max_m: float = 5000.0) -> Snap | None:
        best: Snap | None = None
        k = math.cos(math.radians(lat))
        for e in range(self.E):
            if not (self.edges[e][7] | self.edges[e][8]) & profile:
                continue
            pts = self.edge_points(e)
            walked = 0.0
            for i in range(len(pts) - 1):
                (la, lo), (lb, lob) = pts[i], pts[i + 1]
                seg = dist_m(la, lo, lb, lob)
                ax, ay = (lo - lon) * k, la - lat
                bx, by = (lob - lon) * k, lb - lat
                dx, dy = bx - ax, by - ay
                denom = dx * dx + dy * dy
                t = 0.0 if denom == 0 else max(0.0, min(1.0, -(ax * dx + ay * dy) / denom))
                plat, plon = la + (lb - la) * t, lo + (lob - lo) * t
                d = dist_m(lat, lon, plat, plon)
                if best is None or d < best.distance:
                    best = Snap(e, i, plat, plon, walked + seg * t, d)
                walked += seg
        if best is None or best.distance > max_m:
            return None
        return best

    # search ---------------------------------------------------------------
    def _astar(self, profile: int, seeds: dict, targets: dict, goal: tuple):
        """Multi-source / multi-target A*. seeds: vertex -> seconds; targets: vertex -> extra seconds."""
        vmax = MAX_SPEED_MPS[profile]
        best_g = dict(seeds)
        parent: dict = {v: None for v in seeds}
        heap = []
        for v, g in seeds.items():
            la, lo = self.vertex(v)
            heapq.heappush(heap, (g + dist_m(la, lo, goal[0], goal[1]) / vmax, g, v))
        best_total, best_vertex = math.inf, None
        closed = set()
        while heap:
            f, g, u = heapq.heappop(heap)
            if f >= best_total:
                break
            if u in closed or g > best_g.get(u, math.inf):
                continue
            closed.add(u)
            if u in targets and g + targets[u] < best_total:
                best_total, best_vertex = g + targets[u], u
            for pos in range(self.arc_start[u], self.arc_start[u + 1]):
                arc = self.arcs[pos]
                e, direction = arc >> 1, arc & 1
                if not self.usable(profile, e, direction):
                    continue
                w = self.edges[e][1] if direction == 0 else self.edges[e][0]
                ng = g + self.edge_seconds(profile, e)
                if ng < best_g.get(w, math.inf):
                    best_g[w] = ng
                    parent[w] = (u, arc)
                    la, lo = self.vertex(w)
                    heapq.heappush(heap, (ng + dist_m(la, lo, goal[0], goal[1]) / vmax, ng, w))
        return best_total, best_vertex, parent

    def route(self, profile: int, lat1: float, lon1: float, lat2: float, lon2: float, max_snap_m: float = 5000.0):
        a = self.snap(profile, lat1, lon1, max_snap_m)
        b = self.snap(profile, lat2, lon2, max_snap_m)
        if a is None or b is None:
            return None
        seeds, seed_info, targets, target_info = {}, {}, {}, {}
        ea = a.edge
        la_total = self._geom_scale(ea)
        sa = self._speed(profile, ea)
        if self.usable(profile, ea, 0):
            cost = (self.edge_length_m(ea) - a.along * la_total) / sa
            self._seed(seeds, seed_info, self.edges[ea][1], cost, (0, "to"))
        if self.usable(profile, ea, 1):
            cost = a.along * la_total / sa
            self._seed(seeds, seed_info, self.edges[ea][0], cost, (1, "from"))
        eb = b.edge
        sb = self._speed(profile, eb)
        if self.usable(profile, eb, 0):  # arrive at `from`, drive forward to the point
            targets[self.edges[eb][0]] = b.along * self._geom_scale(eb) / sb
            target_info[self.edges[eb][0]] = "from"
        if self.usable(profile, eb, 1):  # arrive at `to`, drive backward to the point
            cost = (self.edge_length_m(eb) - b.along * self._geom_scale(eb)) / sb
            vtx = self.edges[eb][1]
            if cost < targets.get(vtx, math.inf):
                targets[vtx] = cost
                target_info[vtx] = "to"
        candidates = []
        if ea == eb:
            delta = (b.along - a.along) * la_total
            if delta >= 0 and self.usable(profile, ea, 0):
                candidates.append((delta / sa, "same", 0))
            if delta <= 0 and self.usable(profile, ea, 1):
                candidates.append((-delta / sa, "same", 1))
        if seeds and targets:
            total, end_vertex, parent = self._astar(profile, seeds, targets, (b.lat, b.lon))
            if end_vertex is not None:
                candidates.append((total, "graph", (end_vertex, parent)))
        if not candidates:
            return None
        seconds, kind, extra = min(candidates, key=lambda item: item[0])
        points = [(a.lat, a.lon)]
        if kind == "same":
            pts = self.edge_points(ea)
            if extra == 0:
                points += pts[a.segment + 1:b.segment + 1]
            else:
                points += list(reversed(pts[b.segment + 1:a.segment + 1]))
            points.append((b.lat, b.lon))
        else:
            end_vertex, parent = extra
            chain = []
            v = end_vertex
            while parent[v] is not None:
                u, arc = parent[v]
                chain.append(arc)
                v = u
            chain.reverse()
            first_vertex = v
            pts = self.edge_points(ea)
            points += pts[a.segment + 1:] if seed_info[first_vertex][1] == "to" else list(reversed(pts[:a.segment + 1]))
            for arc in chain:
                e, direction = arc >> 1, arc & 1
                ep = self.edge_points(e)
                points += ep[1:] if direction == 0 else list(reversed(ep[:-1]))
            pts = self.edge_points(eb)
            if target_info[end_vertex] == "from":
                points += pts[1:b.segment + 1]
            else:
                points += list(reversed(pts[b.segment + 1:-1]))
            points.append((b.lat, b.lon))
        distance = sum(dist_m(p[0], p[1], q[0], q[1]) for p, q in zip(points, points[1:]))
        return {"seconds": seconds, "distance_m": distance, "points": points}

    # helpers
    def _geom_scale(self, e: int) -> float:
        pts = self.edge_points(e)
        geom = sum(dist_m(p[0], p[1], q[0], q[1]) for p, q in zip(pts, pts[1:]))
        return 1.0 if geom == 0 else self.edge_length_m(e) / geom

    def _speed(self, profile: int, e: int) -> float:
        return speed_mps(profile, self.edges[e][5], self.edges[e][6])

    @staticmethod
    def _seed(seeds, info, vertex, cost, tag):
        if cost < seeds.get(vertex, math.inf):
            seeds[vertex] = cost
            info[vertex] = tag


# ------------------------------------------------------------------ vertex-level helpers (CI sanity check)

def _nearest_vertex(self: "Graph", profile: int, lat: float, lon: float) -> int | None:
    """Nearest vertex that has at least one arc usable by the profile (brute force, for checks only)."""
    best, best_d = None, math.inf
    k = math.cos(math.radians(lat))
    for v in range(self.V):
        vlat, vlon = self.vert[2 * v] / 1e6, self.vert[2 * v + 1] / 1e6
        approx = ((vlat - lat) ** 2 + ((vlon - lon) * k) ** 2)
        if approx >= best_d:
            continue
        for pos in range(self.arc_start[v], self.arc_start[v + 1]):
            arc = self.arcs[pos]
            if self.usable(profile, arc >> 1, arc & 1):
                best, best_d = v, approx
                break
    return best


def _route_vertices(self: "Graph", profile: int, source: int, target: int):
    """Shortest travel time and road distance between two vertices, or None."""
    la, lo = self.vertex(target)
    total, end, parent = self._astar(profile, {source: 0.0}, {target: 0.0}, (la, lo))
    if end is None:
        return None
    distance, v = 0.0, end
    while parent[v] is not None:
        u, arc = parent[v]
        distance += self.edge_length_m(arc >> 1)
        v = u
    return total, distance


Graph.nearest_vertex = _nearest_vertex
Graph.route_vertices = _route_vertices
