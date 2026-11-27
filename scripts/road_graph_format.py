#!/usr/bin/env python3
"""Offline road graph for Georgia: OSM classification, compact binary format, reference router.

The Android app routes on `georgia-roads.bin` entirely on the device. This module is the
single specification of that file: how OSM ways become graph edges, how the binary file is
laid out, and (as a reference implementation used by tests and CI sanity checks) how a route
is searched. The Kotlin `RoadGraph`/`Router` classes mirror the layout and the algorithm.

File layout, all little-endian:
  header (64 bytes): magic "GRT1", version, vertexCount V, edgeCount E, shapeBytes,
      nameCount N, nameBytes, minLat, minLon, maxLat, maxLon (degrees * 1e6), arcCount,
      turnRestrictionCount, then zero padding
  vertices   V * (lat int32, lon int32)
  edges      E * 28 bytes: from int32, to int32, lengthDecimetres int32, shapeOffsetBytes int32,
      shapeCount uint16, class uint8, flags uint8, fwdMask uint8, backMask uint8, pad uint16, nameIndex int32 (-1 none)
  shape      pairs (lat int32, lon int32): intermediate points of each edge in digitised direction
  arcStart   (V + 1) * int32 offsets into arcs
  arcs       arcCount * int32: (edgeIndex << 1) | direction (0 = from->to, 1 = to->from)
  nameOffsets (N + 1) * int32, then nameBytes of UTF-8
  turnStart (arcCount + 1) * int32 offsets into turn records
  turn records: outgoingArc int32, profileMask uint8, kind uint8, reserved uint16
"""
from __future__ import annotations

import heapq
import math
import re
import struct
import sys
from array import array
from collections import Counter
from dataclasses import dataclass, field
from pathlib import Path

MAGIC = b"GRT1"
FORMAT_VERSION = 3
HEADER_SIZE = 64
EDGE_STRUCT = struct.Struct("<iiiiHBBBBHi")
EDGE_SIZE = EDGE_STRUCT.size  # 28
assert EDGE_SIZE == 28
TURN_STRUCT = struct.Struct("<iBBH")  # outgoing arc, profile mask, kind, reserved
TURN_NO = 0
TURN_ONLY = 1

PROFILE_CAR, PROFILE_OFFROAD, PROFILE_BIKE, PROFILE_FOOT, PROFILE_MOTORCYCLE = 1, 2, 4, 8, 16
PROFILES = {"car": PROFILE_CAR, "offroad": PROFILE_OFFROAD, "bike": PROFILE_BIKE, "foot": PROFILE_FOOT, "motorcycle": PROFILE_MOTORCYCLE}

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
FLAG_STEEP = 16

# Average speeds in km/h per class code (index = class). Mirrored in Kotlin `RoutingProfiles`.
_CAR = {1: 90, 2: 50, 3: 75, 4: 45, 5: 60, 6: 40, 7: 50, 8: 35, 9: 40, 10: 30, 11: 35, 12: 25, 13: 10, 14: 15, 15: 15, 21: 30}
_OFFROAD = {**_CAR, 15: 20}
_BIKE = {3: 14, 4: 14, 5: 15, 6: 15, 7: 16, 8: 16, 9: 17, 10: 17, 11: 16, 12: 17, 13: 15, 14: 15, 15: 12, 16: 10, 17: 12, 18: 18, 21: 15}
_FOOT = {c: 5 for c in range(3, 22)}
_FOOT.update({16: 4, 19: 3})
_MOTORCYCLE = {1: 90, 2: 50, 3: 75, 4: 45, 5: 60, 6: 40, 7: 50, 8: 35, 9: 40, 10: 30, 11: 35, 12: 30, 13: 15, 14: 20, 15: 20, 21: 30}
SPEED_KMH = {PROFILE_CAR: _CAR, PROFILE_OFFROAD: _OFFROAD, PROFILE_BIKE: _BIKE, PROFILE_FOOT: _FOOT, PROFILE_MOTORCYCLE: _MOTORCYCLE}
UNPAVED_FACTOR = {PROFILE_CAR: 0.6, PROFILE_OFFROAD: 0.85, PROFILE_BIKE: 0.7, PROFILE_FOOT: 0.9, PROFILE_MOTORCYCLE: 0.75}
MAX_SPEED_MPS = {PROFILE_CAR: 25.0, PROFILE_OFFROAD: 25.0, PROFILE_BIKE: 5.0, PROFILE_FOOT: 1.5, PROFILE_MOTORCYCLE: 38.9}


def speed_mps(profile: int, cls: int, flags: int, incline_percent: int = 0) -> float:
    kmh = SPEED_KMH[profile].get(cls, 10)
    if flags & FLAG_UNPAVED:
        kmh = max(kmh * UNPAVED_FACTOR[profile], 3.0)
    if flags & FLAG_SEVERE:
        kmh *= {PROFILE_CAR: 0.30, PROFILE_OFFROAD: 0.55, PROFILE_BIKE: 0.20, PROFILE_FOOT: 0.65, PROFILE_MOTORCYCLE: 0.25}[profile]
    elif flags & FLAG_ROUGH:
        kmh *= {PROFILE_CAR: 0.65, PROFILE_OFFROAD: 0.80, PROFILE_BIKE: 0.55, PROFILE_FOOT: 0.85, PROFILE_MOTORCYCLE: 0.60}[profile]
    if flags & FLAG_STEEP and incline_percent == 0:
        kmh *= {PROFILE_CAR: 0.82, PROFILE_OFFROAD: 0.90, PROFILE_BIKE: 0.65, PROFILE_FOOT: 0.78, PROFILE_MOTORCYCLE: 0.75}[profile]
    if incline_percent:
        slope = min(abs(incline_percent), 40) / 100.0
        coefficient = {
            PROFILE_CAR: (1.25, 0.55),
            PROFILE_OFFROAD: (0.75, 0.35),
            PROFILE_BIKE: (2.00, 0.80),
            PROFILE_FOOT: (1.20, 0.50),
            PROFILE_MOTORCYCLE: (1.40, 0.65),
        }[profile][0 if incline_percent > 0 else 1]
        kmh *= max(0.55, 1.0 / (1.0 + slope * coefficient))
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
    incline_forward: int = 0
    incline_backward: int = 0


@dataclass
class WayRecord:
    info: RoadInfo
    refs: list
    lats: list  # degrees * 1e6
    lons: list
    way_id: int = 0


@dataclass
class TurnRestriction:
    from_way: int
    to_way: int
    via_node: int
    profile_mask: int
    kind: int


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


def _parse_incline_value(raw_value) -> float | None:
    raw = str(raw_value or "").strip().lower().replace(",", ".")
    if not raw:
        return None
    if raw == "up":
        return 10.0
    if raw == "down":
        return -10.0
    try:
        if raw.endswith("%"):
            return float(raw[:-1].strip())
        if raw.endswith("°"):
            return math.tan(math.radians(float(raw[:-1].strip()))) * 100.0
        if re.fullmatch(r"[+-]?\d+(?:\.\d+)?", raw):
            return float(raw)
    except ValueError:
        return None
    return None


def _incline_pair(tags: dict) -> tuple[int, int]:
    """Return signed forward/backward grades; positive means uphill in that travel direction."""
    base = _parse_incline_value(tags.get("incline"))
    forward_specific = _parse_incline_value(tags.get("incline:forward"))
    backward_specific = _parse_incline_value(tags.get("incline:backward"))
    if base is not None:
        forward = base
        backward = -base
    else:
        forward = -backward_specific if backward_specific is not None else 0.0
        backward = -forward_specific if forward_specific is not None else 0.0
    if forward_specific is not None:
        forward = forward_specific
    if backward_specific is not None:
        backward = backward_specific
    return (
        int(round(max(-100.0, min(100.0, forward)))),
        int(round(max(-100.0, min(100.0, backward)))),
    )


def _incline_percent(tags: dict) -> float:
    forward, backward = _incline_pair(tags)
    return float(max(abs(forward), abs(backward)))


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
    motorcycle_specific = _tri(tags, "motorcycle", "motor_vehicle", "vehicle")
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
    motorcycle_ok = pick(motorcycle_specific, cls in _CAR_CLASSES)
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
        motorcycle_ok = False
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
        (PROFILE_MOTORCYCLE, motorcycle_ok, False),
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
    incline_forward, incline_backward = _incline_pair(tags)
    if max(abs(incline_forward), abs(incline_backward)) >= 8:
        flags |= FLAG_STEEP
    name = tags.get("name:ru") or tags.get("name") or tags.get("name:en") or tags.get("ref")
    return RoadInfo(cls, flags, fwd, back, name, incline_forward, incline_backward)


# --------------------------------------------------------------------------- graph building

@dataclass
class GraphData:
    vlat: array = field(default_factory=lambda: array("i"))
    vlon: array = field(default_factory=lambda: array("i"))
    edges: list = field(default_factory=list)  # (from, to, lenDm, shape, cls, flags, fwd, back, nameIdx, inclineFwd, inclineBack)
    names: list = field(default_factory=list)
    turn_restrictions: list = field(default_factory=list)  # (incomingArc, outgoingArc, profileMask, kind)


def build_graph(ways, restrictions=()) -> GraphData:
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
    way_arcs: dict[int, list[tuple[int, int, int]]] = {}

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
                    edge_index = len(data.edges)
                    data.edges.append((u, v, int(round(length * 10)), shape, info.cls, info.flags, info.fwd, info.back, name_idx, info.incline_forward, info.incline_backward))
                    way_arcs.setdefault(way.way_id, []).append((edge_index, way.refs[start], way.refs[i]))
                start = i
                length = 0.0
                shape = []
            else:
                shape.append((way.lats[i], way.lons[i]))
    for restriction in restrictions:
        if restriction.via_node not in vertex_of:
            continue
        incoming_arcs = []
        outgoing_arcs = []
        for edge_index, ref_from, ref_to in way_arcs.get(restriction.from_way, ()):
            edge = data.edges[edge_index]
            if ref_to == restriction.via_node:
                direction, allowed = 0, edge[6]
            elif ref_from == restriction.via_node:
                direction, allowed = 1, edge[7]
            else:
                continue
            mask = allowed & restriction.profile_mask
            if mask:
                incoming_arcs.append(((edge_index << 1) | direction, mask))
        for edge_index, ref_from, ref_to in way_arcs.get(restriction.to_way, ()):
            edge = data.edges[edge_index]
            if ref_from == restriction.via_node:
                direction, allowed = 0, edge[6]
            elif ref_to == restriction.via_node:
                direction, allowed = 1, edge[7]
            else:
                continue
            mask = allowed & restriction.profile_mask
            if mask:
                outgoing_arcs.append(((edge_index << 1) | direction, mask))
        for incoming_arc, incoming_mask in incoming_arcs:
            for outgoing_arc, outgoing_mask in outgoing_arcs:
                mask = incoming_mask & outgoing_mask
                if mask:
                    data.turn_restrictions.append((incoming_arc, outgoing_arc, mask, restriction.kind))
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
    for u, v, length_dm, pts, cls, flags, fwd, back, name_idx, incline_forward, incline_backward in data.edges:
        offset = len(shape) * 4
        for lat, lon in pts:
            shape.append(lat)
            shape.append(lon)
        incline_pair = (incline_forward & 0xFF) | ((incline_backward & 0xFF) << 8)
        edge_blob += EDGE_STRUCT.pack(u, v, length_dm, offset, len(pts), cls, flags, fwd, back, incline_pair, name_idx)
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
    by_incoming: dict[int, list[tuple[int, int, int]]] = {}
    for incoming, outgoing, mask, kind in data.turn_restrictions:
        by_incoming.setdefault(incoming, []).append((outgoing, mask, kind))
    turn_start = array("i", [0])
    turn_blob = bytearray()
    for incoming in range(len(arcs)):
        for outgoing, mask, kind in sorted(by_incoming.get(incoming, ()), key=lambda item: (item[0], item[2], item[1])):
            turn_blob += TURN_STRUCT.pack(outgoing, mask, kind, 0)
        turn_start.append(len(turn_blob) // TURN_STRUCT.size)
    min_lat = min(data.vlat) if vcount else 0
    max_lat = max(data.vlat) if vcount else 0
    min_lon = min(data.vlon) if vcount else 0
    max_lon = max(data.vlon) if vcount else 0
    header = struct.pack("<4s11i", MAGIC, FORMAT_VERSION, vcount, ecount, len(shape) * 4, len(data.names),
                         len(name_bytes), min_lat, min_lon, max_lat, max_lon, len(arcs))
    struct.pack_into("<i", header, 48, len(turn_blob) // TURN_STRUCT.size)
    header += b"\x00" * (HEADER_SIZE - len(header))
    with path.open("wb") as handle:
        for chunk in (header, _le(vert), bytes(edge_blob), _le(shape), _le(arc_start), _le(arcs), _le(name_offsets), bytes(name_bytes), _le(turn_start), bytes(turn_blob)):
            handle.write(chunk)
    return {"vertexCount": vcount, "edgeCount": ecount, "arcCount": len(arcs), "nameCount": len(data.names),
            "turnRestrictionCount": len(turn_blob) // TURN_STRUCT.size,
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
        turn_count = struct.unpack_from("<i", blob, 48)[0]
        if turn_count < 0:
            raise ValueError("invalid turn restriction count")
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
        pos += name_bytes
        self.turn_start = array("i")
        self.turn_start.frombytes(blob[pos:pos + 4 * (2 * self.E + 1)])
        pos += 4 * (2 * self.E + 1)
        if len(self.turn_start) != 2 * self.E + 1 or self.turn_start[-1] != turn_count:
            raise ValueError("invalid turn restriction index")
        self.turn_records = list(TURN_STRUCT.iter_unpack(blob[pos:pos + TURN_STRUCT.size * turn_count]))
        if len(self.turn_records) != turn_count or pos + TURN_STRUCT.size * turn_count != len(blob):
            raise ValueError("truncated or trailing turn restriction data")
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

    def edge_incline_percent(self, e: int, direction: int) -> int:
        packed = self.edges[e][9]
        value = (packed & 0xFF) if direction == 0 else ((packed >> 8) & 0xFF)
        return value - 256 if value >= 128 else value

    def edge_seconds(self, profile: int, e: int, direction: int, metres: float | None = None) -> float:
        edge = self.edges[e]
        return (self.edge_length_m(e) if metres is None else metres) / speed_mps(
            profile, edge[4], edge[5], self.edge_incline_percent(e, direction)
        )

    def usable(self, profile: int, e: int, direction: int) -> bool:
        edge = self.edges[e]
        return bool((edge[7] if direction == 0 else edge[8]) & profile)

    def turn_allowed(self, profile: int, incoming_arc: int, outgoing_arc: int) -> bool:
        only_found = False
        only_allowed = False
        prohibited = False
        for outgoing, mask, kind, _reserved in self.turn_records[self.turn_start[incoming_arc]:self.turn_start[incoming_arc + 1]]:
            if not (mask & profile):
                continue
            if kind == TURN_ONLY:
                only_found = True
                if outgoing == outgoing_arc:
                    only_allowed = True
            elif kind == TURN_NO and outgoing == outgoing_arc:
                prohibited = True
        return (not only_found or only_allowed) and not prohibited

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
        """Turn-aware A*: each state is the directed arc used to arrive at its end vertex."""
        vmax = MAX_SPEED_MPS[profile]
        best_g = dict(seeds)
        parent: dict[int, int | None] = {arc: None for arc in seeds}
        heap = []
        for arc, g in seeds.items():
            edge, direction = arc >> 1, arc & 1
            vertex = self.edges[edge][1] if direction == 0 else self.edges[edge][0]
            la, lo = self.vertex(vertex)
            heapq.heappush(heap, (g + dist_m(la, lo, goal[0], goal[1]) / vmax, g, arc))
        best_total, best_state, best_final_arc = math.inf, None, None
        closed = set()
        while heap:
            f, g, incoming_arc = heapq.heappop(heap)
            if f >= best_total:
                break
            if incoming_arc in closed or g > best_g.get(incoming_arc, math.inf):
                continue
            closed.add(incoming_arc)
            edge, direction = incoming_arc >> 1, incoming_arc & 1
            u = self.edges[edge][1] if direction == 0 else self.edges[edge][0]
            for final_arc, extra in targets.get(u, ()):
                if (final_arc < 0 or self.turn_allowed(profile, incoming_arc, final_arc)) and g + extra < best_total:
                    best_total, best_state, best_final_arc = g + extra, incoming_arc, final_arc
            for pos in range(self.arc_start[u], self.arc_start[u + 1]):
                outgoing_arc = self.arcs[pos]
                e, direction = outgoing_arc >> 1, outgoing_arc & 1
                if not self.usable(profile, e, direction) or not self.turn_allowed(profile, incoming_arc, outgoing_arc):
                    continue
                w = self.edges[e][1] if direction == 0 else self.edges[e][0]
                ng = g + self.edge_seconds(profile, e, direction)
                if ng < best_g.get(outgoing_arc, math.inf):
                    best_g[outgoing_arc] = ng
                    parent[outgoing_arc] = incoming_arc
                    la, lo = self.vertex(w)
                    heapq.heappush(heap, (ng + dist_m(la, lo, goal[0], goal[1]) / vmax, ng, outgoing_arc))
        return best_total, best_state, best_final_arc, parent

    def route(self, profile: int, lat1: float, lon1: float, lat2: float, lon2: float, max_snap_m: float = 5000.0):
        a = self.snap(profile, lat1, lon1, max_snap_m)
        b = self.snap(profile, lat2, lon2, max_snap_m)
        if a is None or b is None:
            return None
        seeds, targets = {}, {}
        ea, eb = a.edge, b.edge
        la_total = self._geom_scale(ea)
        sa_forward = self._speed(profile, ea, 0)
        sa_backward = self._speed(profile, ea, 1)
        if self.usable(profile, ea, 0):
            self._seed(seeds, ea << 1, (self.edge_length_m(ea) - a.along * la_total) / sa_forward)
        if self.usable(profile, ea, 1):
            self._seed(seeds, (ea << 1) | 1, a.along * la_total / sa_backward)
        sb_forward = self._speed(profile, eb, 0)
        sb_backward = self._speed(profile, eb, 1)
        if self.usable(profile, eb, 0):
            targets.setdefault(self.edges[eb][0], []).append((eb << 1, b.along * self._geom_scale(eb) / sb_forward))
        if self.usable(profile, eb, 1):
            targets.setdefault(self.edges[eb][1], []).append(((eb << 1) | 1, (self.edge_length_m(eb) - b.along * self._geom_scale(eb)) / sb_backward))
        candidates = []
        if ea == eb:
            delta = (b.along - a.along) * la_total
            if delta >= 0 and self.usable(profile, ea, 0):
                candidates.append((delta / sa_forward, "same", 0))
            if delta <= 0 and self.usable(profile, ea, 1):
                candidates.append((-delta / sa_backward, "same", 1))
        if seeds and targets:
            total, end_state, final_arc, parent = self._astar(profile, seeds, targets, (b.lat, b.lon))
            if end_state is not None:
                candidates.append((total, "graph", (end_state, final_arc, parent)))
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
            end_state, final_arc, parent = extra
            chain = []
            state = end_state
            while state is not None:
                chain.append(state)
                state = parent[state]
            chain.reverse()
            first_arc = chain[0]
            first_edge, start_direction = first_arc >> 1, first_arc & 1
            pts = self.edge_points(first_edge)
            points += pts[a.segment + 1:] if start_direction == 0 else list(reversed(pts[:a.segment + 1]))
            for arc in chain[1:]:
                e, direction = arc >> 1, arc & 1
                ep = self.edge_points(e)
                points += ep[1:] if direction == 0 else list(reversed(ep[:-1]))
            final_edge, final_direction = final_arc >> 1, final_arc & 1
            pts = self.edge_points(final_edge)
            if final_direction == 0:
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

    def _speed(self, profile: int, e: int, direction: int) -> float:
        edge = self.edges[e]
        return speed_mps(profile, edge[4], edge[5], self.edge_incline_percent(e, direction))

    @staticmethod
    def _seed(seeds, arc, cost):
        if cost < seeds.get(arc, math.inf):
            seeds[arc] = cost


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
    if source == target:
        return 0.0, 0.0
    seeds = {}
    for pos in range(self.arc_start[source], self.arc_start[source + 1]):
        arc = self.arcs[pos]
        edge, direction = arc >> 1, arc & 1
        if self.usable(profile, edge, direction):
            seeds[arc] = self.edge_seconds(profile, edge, direction)
    la, lo = self.vertex(target)
    total, end_state, _final_arc, parent = self._astar(profile, seeds, {target: [(-1, 0.0)]}, (la, lo))
    if end_state is None:
        return None
    distance = 0.0
    state = end_state
    while state is not None:
        distance += self.edge_length_m(state >> 1)
        state = parent[state]
    return total, distance

Graph.nearest_vertex = _nearest_vertex
Graph.route_vertices = _route_vertices
