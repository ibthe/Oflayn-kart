#!/usr/bin/env python3
"""Build a local multilingual POI SQLite database from a Georgia OSM PBF.

The extractor intentionally uses PyOsmium's disk-backed sparse_file_array index
so the GitHub runner does not need to keep every node location in RAM. It indexes
named nodes, closed ways and multipolygon relations without duplicating way-derived areas.
"""
from __future__ import annotations

import argparse
import json
import os
import sqlite3
import sys
import tempfile
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable

BBOX_DEFAULT = (41.00, 39.95, 43.65, 46.80)  # minLat,minLon,maxLat,maxLon
SCHEMA_VERSION = 1

# Category rules are deliberately explicit; they are a useful offline index,
# not a claim that OSM contains every real-world business or facility.
AMENITY = {
    "restaurant": ("food", "restaurant"), "cafe": ("food", "cafe"),
    "fast_food": ("food", "fast_food"), "food_court": ("food", "food_court"),
    "ice_cream": ("food", "ice_cream"), "bar": ("food", "bar"),
    "pub": ("food", "pub"), "biergarten": ("food", "biergarten"),
    "hotel": ("lodging", "hotel"), "motel": ("lodging", "motel"),
    "guest_house": ("lodging", "guest_house"), "hostel": ("lodging", "hostel"),
    "chalet": ("lodging", "chalet"),
    "pharmacy": ("medical", "pharmacy"), "hospital": ("medical", "hospital"),
    "clinic": ("medical", "clinic"), "doctors": ("medical", "doctors"),
    "dentist": ("medical", "dentist"), "veterinary": ("medical", "veterinary"),
    "fuel": ("transport", "fuel"), "charging_station": ("transport", "charging_station"),
    "parking": ("transport", "parking"), "bus_station": ("transport", "bus_station"),
    "bus_stop": ("transport", "bus_stop"), "taxi": ("transport", "taxi"),
    "car_rental": ("transport", "car_rental"), "car_repair": ("transport", "car_repair"),
    "car_wash": ("transport", "car_wash"), "bicycle_rental": ("transport", "bicycle_rental"),
    "police": ("services", "police"), "fire_station": ("services", "fire_station"),
    "post_office": ("services", "post_office"), "bank": ("services", "bank"),
    "atm": ("services", "atm"), "toilets": ("services", "toilets"),
    "drinking_water": ("services", "drinking_water"), "embassy": ("services", "embassy"),
    "marketplace": ("shops", "marketplace"),
    "place_of_worship": ("historic", "place_of_worship"),
    "ferry_terminal": ("transport", "ferry_terminal"),
    "library": ("services", "library"), "townhall": ("services", "townhall"),
    "courthouse": ("services", "courthouse"), "university": ("services", "university"),
    "college": ("services", "college"),
    "theatre": ("tourism", "theatre"), "cinema": ("tourism", "cinema"),
}
TOURISM = {
    "attraction": ("tourism", "attraction"), "museum": ("tourism", "museum"),
    "viewpoint": ("tourism", "viewpoint"), "artwork": ("tourism", "artwork"),
    "information": ("tourism", "information"), "gallery": ("tourism", "gallery"),
    "zoo": ("tourism", "zoo"), "theme_park": ("tourism", "theme_park"),
    "camp_site": ("lodging", "camp_site"), "caravan_site": ("lodging", "caravan_site"),
    "alpine_hut": ("lodging", "alpine_hut"), "picnic_site": ("nature", "picnic_site"),
    "hotel": ("lodging", "hotel"), "guest_house": ("lodging", "guest_house"),
    "hostel": ("lodging", "hostel"), "apartment": ("lodging", "apartment"),
    "chalet": ("lodging", "chalet"), "motel": ("lodging", "motel"),
    "wilderness_hut": ("lodging", "wilderness_hut"),
}
HISTORIC = {
    "castle", "fort", "fortress", "ruins", "archaeological_site", "monument",
    "memorial", "wayside_shrine", "manor", "city_gate", "tomb", "church",
    "monastery", "cathedral", "bridge", "tower", "city_walls", "citywalls",
    "aqueduct", "battlefield",
}
NATURAL = {"peak", "cave_entrance", "spring", "hot_spring", "waterfall", "beach", "glacier", "cave", "saddle"}
PLACE = {"city", "town", "village", "hamlet", "isolated_dwelling", "suburb", "neighbourhood", "locality", "island", "islet"}
SHOP = {"supermarket", "convenience", "bakery", "butcher", "greengrocer", "mall", "department_store", "hardware", "outdoor", "general"}
LEISURE = {"park", "nature_reserve", "marina", "swimming_pool", "playground", "sports_centre", "stadium", "golf_course", "resort"}

CATEGORY_LABELS = {
    "food": "Еда и напитки", "lodging": "Жильё", "tourism": "Достопримечательности",
    "medical": "Медицина", "transport": "Транспорт и заправки", "services": "Сервисы",
    "shops": "Магазины", "historic": "История и культура", "nature": "Природа",
    "settlement": "Населённые пункты",
}

# Russian / Georgian / English search words per subcategory. Without them a
# search for "вокзал", "аэропорт" or "აეროპორტი" cannot match an object whose
# subcategory is stored as an English OSM value. These are search aliases only;
# they are not presented as translations of the object's own name.
SUBCATEGORY_SEARCH_WORDS: dict[str, tuple[str, ...]] = {
    "station": ("вокзал", "железнодорожная станция", "ж/д станция", "жд станция", "поезд", "railway station", "train station", "რკინიგზის სადგური", "მატარებლის სადგური"),
    "halt": ("ж/д остановка", "железнодорожная платформа", "остановочный пункт", "railway halt", "train stop", "რკინიგზის გაჩერება"),
    "subway_station": ("метро", "станция метро", "subway", "metro", "მეტრო", "მეტროს სადგური"),
    "light_rail_station": ("легкорельсовый транспорт", "станция", "light rail"),
    "tram_stop": ("трамвай", "трамвайная остановка", "tram stop"),
    "bus_station": ("автовокзал", "автостанция", "автобусная станция", "маршрутки", "bus station", "ავტოსადგური"),
    "bus_stop": ("автобусная остановка", "остановка", "bus stop", "გაჩერება"),
    "aerodrome": ("аэропорт", "аэродром", "airport", "aerodrome", "აეროპორტი"),
    "heliport": ("вертодром", "вертолётная площадка", "heliport"),
    "ferry_terminal": ("паром", "порт", "причал", "ferry", "ბორანი", "პორტი"),
    "cable_car_station": ("канатная дорога", "фуникулёр", "подъёмник", "cable car", "gondola", "საბაგირო"),
    "border_crossing": ("граница", "погранпереход", "пункт пропуска", "кпп", "border crossing", "საზღვარი"),
    "public_transport_station": ("транспортный узел", "станция", "public transport station"),
    "taxi": ("такси", "taxi"), "fuel": ("заправка", "азс", "бензин", "gas station", "petrol"),
    "charging_station": ("зарядка", "зарядная станция", "электромобиль", "ev charging"),
    "parking": ("парковка", "стоянка", "parking"), "car_rental": ("прокат авто", "аренда авто", "car rental"),
    "car_repair": ("автосервис", "шиномонтаж", "car repair"), "car_wash": ("автомойка", "car wash"),
    "bicycle_rental": ("прокат велосипедов", "bicycle rental"),
    "restaurant": ("ресторан", "restaurant", "რესტორანი"), "cafe": ("кафе", "кофейня", "cafe", "coffee", "კაფე"),
    "fast_food": ("фастфуд", "быстрое питание", "fast food"), "bar": ("бар", "bar"), "pub": ("паб", "pub"),
    "hotel": ("отель", "гостиница", "hotel", "სასტუმრო"), "guest_house": ("гостевой дом", "гестхаус", "guest house", "სასტუმრო სახლი"),
    "hostel": ("хостел", "hostel"), "motel": ("мотель", "motel"), "apartment": ("апартаменты", "apartment"),
    "chalet": ("шале", "chalet"), "camp_site": ("кемпинг", "палатки", "camp site", "camping"),
    "caravan_site": ("автокемпинг", "caravan site"), "alpine_hut": ("горная хижина", "приют", "alpine hut"),
    "wilderness_hut": ("хижина", "приют", "wilderness hut"), "shelter": ("укрытие", "навес", "shelter"),
    "pharmacy": ("аптека", "pharmacy", "აფთიაქი"), "hospital": ("больница", "госпиталь", "hospital", "საავადმყოფო"),
    "clinic": ("клиника", "поликлиника", "clinic"), "doctors": ("врач", "доктор", "doctors"),
    "dentist": ("стоматолог", "зубной", "dentist"), "veterinary": ("ветеринар", "veterinary"),
    "police": ("полиция", "police", "პოლიცია"), "fire_station": ("пожарная часть", "пожарные", "fire station"),
    "post_office": ("почта", "почтовое отделение", "post office"), "bank": ("банк", "bank", "ბანკი"),
    "atm": ("банкомат", "atm"), "toilets": ("туалет", "toilets", "wc"), "drinking_water": ("питьевая вода", "родник", "drinking water"),
    "embassy": ("посольство", "embassy"), "library": ("библиотека", "library"), "townhall": ("мэрия", "администрация", "town hall"),
    "courthouse": ("суд", "courthouse"), "university": ("университет", "university"), "college": ("колледж", "college"),
    "marketplace": ("рынок", "базар", "marketplace"),
    "place_of_worship": ("церковь", "храм", "мечеть", "синагога", "place of worship", "church", "ეკლესია"),
    "attraction": ("достопримечательность", "attraction"), "museum": ("музей", "museum", "მუზეუმი"),
    "viewpoint": ("смотровая площадка", "панорама", "viewpoint"), "artwork": ("арт-объект", "artwork"),
    "information": ("туристическая информация", "information"), "gallery": ("галерея", "gallery"),
    "zoo": ("зоопарк", "zoo"), "theme_park": ("парк аттракционов", "theme park"),
    "theatre": ("театр", "theatre"), "cinema": ("кинотеатр", "cinema"), "picnic_site": ("место для пикника", "picnic"),
    "castle": ("замок", "castle"), "fort": ("форт", "крепость", "fort"), "fortress": ("крепость", "fortress"),
    "ruins": ("руины", "ruins"), "archaeological_site": ("археологический памятник", "раскопки", "archaeological"),
    "monument": ("памятник", "monument"), "memorial": ("мемориал", "memorial"), "tower": ("башня", "tower"),
    "monastery": ("монастырь", "monastery"), "church": ("церковь", "church"), "cathedral": ("собор", "cathedral"),
    "bridge": ("мост", "bridge"), "city_walls": ("крепостная стена", "city walls"), "citywalls": ("крепостная стена", "city walls"),
    "peak": ("вершина", "гора", "peak", "mountain"), "saddle": ("перевал", "седловина", "pass", "saddle"),
    "mountain_pass": ("перевал", "pass", "mountain pass"),
    "cave_entrance": ("пещера", "cave"), "cave": ("пещера", "cave"), "spring": ("родник", "источник", "spring"),
    "hot_spring": ("горячий источник", "термальный источник", "hot spring"), "waterfall": ("водопад", "waterfall"),
    "beach": ("пляж", "beach"), "glacier": ("ледник", "glacier"), "park": ("парк", "park"),
    "nature_reserve": ("заповедник", "нацпарк", "nature reserve"), "marina": ("марина", "причал", "marina"),
    "swimming_pool": ("бассейн", "swimming pool"), "stadium": ("стадион", "stadium"), "golf_course": ("гольф", "golf"),
    "resort": ("курорт", "resort"), "sports_centre": ("спортцентр", "sports centre"), "playground": ("детская площадка", "playground"),
    "city": ("город", "city"), "town": ("город", "посёлок", "town"), "village": ("село", "деревня", "village", "სოფელი"),
    "hamlet": ("хутор", "селение", "hamlet"), "suburb": ("район", "suburb"), "neighbourhood": ("квартал", "микрорайон", "neighbourhood"),
    "island": ("остров", "island"), "islet": ("островок", "islet"),
    "supermarket": ("супермаркет", "supermarket"), "convenience": ("магазин", "продукты", "convenience"),
    "bakery": ("пекарня", "хлеб", "bakery"), "mall": ("торговый центр", "молл", "mall"),
}

@dataclass(frozen=True)
class PoiInput:
    poi_id: str
    osm_type: str
    osm_id: int
    category: str
    subcategory: str
    name: str
    name_en: str
    name_ru: str
    name_ka: str
    latitude: float
    longitude: float
    tags_json: str


def _classify_transport(tags: dict[str, str]) -> tuple[str, str] | None:
    """Stations, terminals, airports and similar hubs.

    Evaluated before the generic rules so that a station that also carries a
    secondary tag (for example tourism=attraction) is still found as transport,
    and so that railway=station is not downgraded to a generic public-transport
    stop when public_transport=station is present as well.
    """
    if tags.get("disused") == "yes" or tags.get("abandoned") == "yes":
        return None
    railway = tags.get("railway")
    if railway in {"station", "halt"}:
        if tags.get("station") == "subway" or tags.get("subway") == "yes":
            return ("transport", "subway_station")
        if tags.get("station") == "light_rail" or tags.get("light_rail") == "yes":
            return ("transport", "light_rail_station")
        return ("transport", railway)
    if railway == "tram_stop":
        return ("transport", "tram_stop")
    aeroway = tags.get("aeroway")
    if aeroway == "aerodrome":
        return ("transport", "aerodrome")
    if aeroway == "heliport":
        return ("transport", "heliport")
    if tags.get("amenity") == "bus_station":
        return ("transport", "bus_station")
    if tags.get("amenity") == "ferry_terminal":
        return ("transport", "ferry_terminal")
    if tags.get("aerialway") == "station":
        return ("transport", "cable_car_station")
    if tags.get("barrier") == "border_control":
        return ("transport", "border_crossing")
    if tags.get("highway") == "bus_stop":
        return ("transport", "bus_stop")
    public_transport = tags.get("public_transport")
    if public_transport == "station":
        if tags.get("bus") == "yes":
            return ("transport", "bus_station")
        if tags.get("train") == "yes":
            return ("transport", "station")
        if tags.get("subway") == "yes":
            return ("transport", "subway_station")
        if tags.get("aerialway") == "yes":
            return ("transport", "cable_car_station")
        if tags.get("ferry") == "yes":
            return ("transport", "ferry_terminal")
        return ("transport", "public_transport_station")
    if public_transport == "platform" and tags.get("bus") == "yes":
        return ("transport", "bus_stop")
    # stop_position and non-bus platforms only duplicate the stop/station object
    # above (and are usually unnamed), so they are not indexed.
    return None


def classify_tags(tags: dict[str, str]) -> tuple[str, str] | None:
    transport = _classify_transport(tags)
    if transport is not None:
        return transport
    amenity = tags.get("amenity")
    if amenity == "shelter":
        if tags.get("shelter_type") in {"basic_hut", "lean_to", "rock_shelter", "weather_shelter"}:
            return ("nature", "shelter")
        return None  # bus-stop shelters are noise
    if amenity in AMENITY:
        return AMENITY[amenity]
    tourism = tags.get("tourism")
    if tourism in TOURISM:
        return TOURISM[tourism]
    historic = tags.get("historic")
    if historic in HISTORIC:
        return ("historic", historic)
    if tags.get("mountain_pass") == "yes" and tags.get("natural") != "saddle":
        return ("nature", "mountain_pass")
    natural = tags.get("natural")
    if natural in NATURAL:
        return ("nature", natural)
    place = tags.get("place")
    if place in PLACE:
        return ("settlement", place)
    shop = tags.get("shop")
    if shop in SHOP:
        return ("shops", shop)
    leisure = tags.get("leisure")
    if leisure in LEISURE:
        return ("nature", leisure)
    if tags.get("waterway") == "waterfall":
        return ("nature", "waterfall")
    if tags.get("emergency") in {"defibrillator", "phone", "assembly_point"}:
        return ("services", f"emergency_{tags['emergency']}")
    return None


def polygon_centroid(points: list[tuple[float, float]]) -> tuple[float, float]:
    """Area-weighted centroid of a closed ring given as (lat, lon) vertices.

    The previous plain vertex average is pulled toward whichever side of a
    building or station area has more vertices. Falls back to the vertex
    average for degenerate rings.
    """
    count = len(points)
    mean_lat = sum(p[0] for p in points) / count
    mean_lon = sum(p[1] for p in points) / count
    # Work relative to the mean for numerical stability.
    area2 = cx = cy = 0.0
    for index in range(count):
        y0, x0 = points[index][0] - mean_lat, points[index][1] - mean_lon
        y1, x1 = points[(index + 1) % count][0] - mean_lat, points[(index + 1) % count][1] - mean_lon
        cross = x0 * y1 - x1 * y0
        area2 += cross
        cx += (x0 + x1) * cross
        cy += (y0 + y1) * cross
    if abs(area2) < 1e-14:
        return mean_lat, mean_lon
    lat = mean_lat + cy / (3.0 * area2)
    lon = mean_lon + cx / (3.0 * area2)
    min_lat, max_lat = min(p[0] for p in points), max(p[0] for p in points)
    min_lon, max_lon = min(p[1] for p in points), max(p[1] for p in points)
    if not (min_lat <= lat <= max_lat and min_lon <= lon <= max_lon):
        return mean_lat, mean_lon
    return lat, lon


def multipolygon_centroid(outer_rings: Iterable[Iterable[tuple[float, float]]]) -> tuple[float, float] | None:
    """Combine outer-ring centroids by polygon area, ignoring invalid/degenerate rings."""
    weighted_lat = weighted_lon = total_area = 0.0
    for ring in outer_rings:
        points = list(ring)
        if len(points) < 3:
            continue
        lat, lon = polygon_centroid(points)
        area2 = 0.0
        for index, point in enumerate(points):
            next_point = points[(index + 1) % len(points)]
            area2 += point[1] * next_point[0] - next_point[1] * point[0]
        area = abs(area2) * max(0.1, __import__("math").cos(__import__("math").radians(lat)))
        if area <= 1e-14:
            continue
        weighted_lat += lat * area
        weighted_lon += lon * area
        total_area += area
    if total_area <= 0.0:
        return None
    return weighted_lat / total_area, weighted_lon / total_area


def normalise_search_text(values: Iterable[str]) -> str:
    # SQLite's built-in LIKE is not fully Unicode case-folding; normalize case in
    # Python first so Cyrillic and Georgian searches behave predictably.
    unique: list[str] = []
    seen: set[str] = set()
    for value in values:
        cleaned = " ".join((value or "").split()).strip()
        folded = cleaned.casefold()
        if folded and folded not in seen:
            unique.append(folded)
            seen.add(folded)
    return " | ".join(unique)


def create_database(path: Path) -> sqlite3.Connection:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists():
        path.unlink()
    conn = sqlite3.connect(path)
    conn.execute("PRAGMA journal_mode=OFF")
    conn.execute("PRAGMA synchronous=OFF")
    conn.execute("PRAGMA temp_store=MEMORY")
    conn.executescript("""
        CREATE TABLE poi (
            poi_id TEXT PRIMARY KEY,
            osm_type TEXT NOT NULL,
            osm_id INTEGER NOT NULL,
            category TEXT NOT NULL,
            subcategory TEXT NOT NULL,
            name TEXT NOT NULL,
            name_en TEXT NOT NULL DEFAULT '',
            name_ru TEXT NOT NULL DEFAULT '',
            name_ka TEXT NOT NULL DEFAULT '',
            latitude REAL NOT NULL,
            longitude REAL NOT NULL,
            search_text TEXT NOT NULL,
            tags_json TEXT NOT NULL
        );
        CREATE INDEX poi_category_idx ON poi(category);
        CREATE INDEX poi_lat_lon_idx ON poi(latitude, longitude);
        CREATE INDEX poi_name_idx ON poi(name);
        CREATE TABLE metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL);
    """)
    return conn


def insert_poi(conn: sqlite3.Connection, poi: PoiInput) -> None:
    category_label = CATEGORY_LABELS.get(poi.category, poi.category)
    subcategory_label = poi.subcategory.replace("_", " ")
    try:
        tags = json.loads(poi.tags_json)
    except (TypeError, json.JSONDecodeError):
        tags = {}
    # Index useful public business/location details too. These values are
    # searchable offline, but are never treated as verified opening status.
    searchable_tag_values = [
        tags.get(key, "") for key in (
            "addr:full", "addr:street", "addr:place", "addr:housenumber",
            "addr:city", "addr:town", "addr:village", "addr:postcode",
            "brand", "operator", "cuisine", "opening_hours",
            "phone", "contact:phone", "telephone", "website", "contact:website",
        )
    ]
    search_text = normalise_search_text((
        poi.name, poi.name_en, poi.name_ru, poi.name_ka,
        poi.category, category_label, poi.subcategory, subcategory_label,
        *SUBCATEGORY_SEARCH_WORDS.get(poi.subcategory, ()),
        *searchable_tag_values,
    ))
    conn.execute("""
        INSERT OR IGNORE INTO poi (
            poi_id, osm_type, osm_id, category, subcategory, name,
            name_en, name_ru, name_ka, latitude, longitude, search_text, tags_json
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """, (
        poi.poi_id, poi.osm_type, poi.osm_id, poi.category, poi.subcategory,
        poi.name, poi.name_en, poi.name_ru, poi.name_ka,
        poi.latitude, poi.longitude, search_text, poi.tags_json,
    ))


def point_in_bbox(lat: float, lon: float, bbox: tuple[float, float, float, float]) -> bool:
    min_lat, min_lon, max_lat, max_lon = bbox
    return min_lat <= lat <= max_lat and min_lon <= lon <= max_lon


def parse_bbox(value: str) -> tuple[float, float, float, float]:
    try:
        parts = tuple(float(part.strip()) for part in value.split(","))
    except ValueError as exc:
        raise argparse.ArgumentTypeError("bbox must be minLat,minLon,maxLat,maxLon") from exc
    if len(parts) != 4:
        raise argparse.ArgumentTypeError("bbox must contain four coordinates")
    min_lat, min_lon, max_lat, max_lon = parts
    if not (-90 <= min_lat < max_lat <= 90 and -180 <= min_lon < max_lon <= 180):
        raise argparse.ArgumentTypeError("bbox coordinates are out of range or incorrectly ordered")
    return parts  # type: ignore[return-value]


class ProgressReporter:
    """Emit visible POI extraction heartbeats during large country PBF scans.

    GitHub Actions logs are the user's only view into the long-running offline
    data build. A periodic line makes real progress visible and distinguishes
    a slow scan from a canceled runner.
    """

    def __init__(
        self,
        every_entities: int = 250_000,
        every_seconds: float = 15.0,
        emit=None,
        clock=None,
    ) -> None:
        if every_entities < 1 or every_seconds <= 0:
            raise ValueError("progress intervals must be positive")
        self.every_entities = every_entities
        self.every_seconds = every_seconds
        self.emit = emit or (lambda message: print(message, flush=True))
        self.clock = clock or time.monotonic
        self.started = self.clock()
        self.last_report = self.started
        self.next_entity_report = every_entities

    def report(self, stats: dict, *, final: bool = False) -> None:
        now = self.clock()
        total = int(stats["nodesSeen"]) + int(stats["waysSeen"]) + int(stats.get("relationsSeen", 0))
        if not final and total < self.next_entity_report and now - self.last_report < self.every_seconds:
            return

        elapsed = max(0.0, now - self.started)
        prefix = "[poi] OSM scan complete" if final else "[poi] OSM scan progress"
        self.emit(
            f"{prefix}: entities={total:,}, nodes={stats['nodesSeen']:,}, "
            f"ways={stats['waysSeen']:,}, relations={stats.get('relationsSeen', 0):,}, "
            f"POIs={stats['poisWritten']:,}, "
            f"elapsed={elapsed:.1f}s"
        )
        self.last_report = now
        self.next_entity_report = ((total // self.every_entities) + 1) * self.every_entities


def build_from_pbf(input_file: Path, output_file: Path, bbox: tuple[float, float, float, float]) -> dict:
    try:
        import osmium  # type: ignore
    except ImportError as exc:
        raise RuntimeError("PyOsmium is missing. Install Ubuntu package python3-pyosmium before running the POI builder.") from exc

    conn = create_database(output_file)
    stats = {"nodesSeen": 0, "waysSeen": 0, "relationsSeen": 0, "relationsWritten": 0, "poisWritten": 0, "relationsNotProcessed": False}
    progress = ProgressReporter()

    class Handler(osmium.SimpleHandler):
        def _insert(self, obj, osm_type: str, lat: float, lon: float) -> None:
            tags = {tag.k: tag.v for tag in obj.tags}
            classification = classify_tags(tags)
            if classification is None or not point_in_bbox(lat, lon, bbox):
                return
            category, subcategory = classification
            name = tags.get("name", "").strip()
            name_en = tags.get("name:en", "").strip()
            name_ru = tags.get("name:ru", "").strip()
            name_ka = tags.get("name:ka", "").strip()
            fallback = name or name_en or name_ru or name_ka or CATEGORY_LABELS.get(category, category)
            poi = PoiInput(
                poi_id=f"{osm_type}/{obj.id}", osm_type=osm_type, osm_id=int(obj.id),
                category=category, subcategory=subcategory, name=fallback,
                name_en=name_en, name_ru=name_ru, name_ka=name_ka,
                latitude=lat, longitude=lon,
                tags_json=json.dumps(tags, ensure_ascii=False, sort_keys=True, separators=(",", ":")),
            )
            insert_poi(conn, poi)
            stats["poisWritten"] += 1
            if stats["poisWritten"] % 5000 == 0:
                conn.commit()

        def node(self, node) -> None:
            stats["nodesSeen"] += 1
            progress.report(stats)
            if len(node.tags) == 0:
                return  # most OSM nodes are untagged way vertices; skip them cheaply
            if not node.location.valid():
                return
            self._insert(node, "node", float(node.location.lat), float(node.location.lon))

        def way(self, way) -> None:
            stats["waysSeen"] += 1
            progress.report(stats)
            if len(way.tags) == 0:
                return
            tags = {tag.k: tag.v for tag in way.tags}
            if classify_tags(tags) is None:
                return
            refs = list(way.nodes)
            if len(refs) < 3 or refs[0].ref != refs[-1].ref:
                return  # only closed OSM ways are treated as POI areas
            locations = [node.location for node in refs[:-1] if node.location.valid()]
            if len(locations) != len(refs) - 1:
                return  # avoid a misleading centroid when the way has missing node locations
            lat, lon = polygon_centroid([(float(l.lat), float(l.lon)) for l in locations])
            self._insert(way, "way", lat, lon)

        def area(self, area) -> None:
            # Closed ways are already indexed by way(); only add relation-derived areas here.
            if area.from_way():
                return
            stats["relationsSeen"] += 1
            progress.report(stats)
            tags = {tag.k: tag.v for tag in area.tags}
            if classify_tags(tags) is None:
                return
            outer_rings = []
            for ring in area.outer_rings():
                points = []
                for node in ring:
                    if not node.location.valid():
                        points = []
                        break
                    points.append((float(node.lat), float(node.lon)))
                if points:
                    outer_rings.append(points)
            centroid = multipolygon_centroid(outer_rings)
            if centroid is None:
                return
            lat, lon = centroid
            if not point_in_bbox(lat, lon, bbox):
                return
            self._insert(area, "relation", lat, lon)
            stats["relationsWritten"] += 1

    handler = Handler()
    scan_started = time.monotonic()
    print(
        f"[poi] Scanning {input_file.name} ({input_file.stat().st_size:,} bytes) "
        "with a disk-backed node-location index; progress is reported every "
        "250,000 entities or 15 seconds.",
        flush=True,
    )
    # dense_mmap_array reserves ~8 bytes per possible node ID (OSM IDs reach ~1.3e10)
    # and touches a separate 4 KB page for sparsely spaced IDs, which exhausts runner
    # RAM/disk and gets the job killed. sparse_file_array stores only existing nodes
    # (16 bytes each) in a regular file, so memory stays bounded.
    index_dir = Path(os.environ.get("RUNNER_TEMP") or tempfile.gettempdir())
    index_file = index_dir / "georgia-node-locations.idx"
    index_file.unlink(missing_ok=True)
    try:
        handler.apply_file(str(input_file), locations=True, idx=f"sparse_file_array,{index_file}")
    finally:
        index_file.unlink(missing_ok=True)
    scan_elapsed = time.monotonic() - scan_started
    progress.report(stats, final=True)
    conn.commit()

    category_counts = {
        row[0]: row[1] for row in conn.execute(
            "SELECT category, COUNT(*) FROM poi GROUP BY category ORDER BY category"
        )
    }
    count = int(conn.execute("SELECT COUNT(*) FROM poi").fetchone()[0])
    if count == 0:
        conn.close()
        output_file.unlink(missing_ok=True)
        raise RuntimeError("No POIs were extracted from the OSM PBF; refusing to publish an empty database.")

    metadata = {
        "schemaVersion": str(SCHEMA_VERSION),
        "dataset": "Georgia OpenStreetMap POI subset",
        "createdAtUtc": datetime.now(timezone.utc).isoformat(),
        "poiCount": str(count),
        "sourceFile": input_file.name,
        "bbox": ",".join(str(part) for part in bbox),
        "relationsIncluded": "false",
    }
    conn.executemany("INSERT OR REPLACE INTO metadata(key,value) VALUES (?,?)", metadata.items())
    conn.commit()
    integrity = conn.execute("PRAGMA integrity_check").fetchone()[0]
    conn.close()
    if integrity != "ok":
        output_file.unlink(missing_ok=True)
        raise RuntimeError(f"SQLite integrity check failed: {integrity}")

    return {
        "schemaVersion": SCHEMA_VERSION,
        "poiCount": count,
        "categoryCounts": category_counts,
        "stats": stats,
        "scanElapsedSeconds": round(scan_elapsed, 1),
        "relationsIncluded": True,
        "bbox": ",".join(str(part) for part in bbox),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input_pbf", type=Path)
    parser.add_argument("output_sqlite", type=Path)
    parser.add_argument("--bbox", default=",".join(map(str, BBOX_DEFAULT)))
    parser.add_argument("--summary", type=Path, required=True)
    args = parser.parse_args()
    bbox = parse_bbox(args.bbox)
    if not args.input_pbf.is_file() or args.input_pbf.stat().st_size < 1_000_000:
        print("ERROR: input OSM PBF is missing or unexpectedly small.", file=sys.stderr)
        return 2
    try:
        summary = build_from_pbf(args.input_pbf, args.output_sqlite, bbox)
    except Exception as exc:
        print(f"ERROR: POI database generation failed: {exc}", file=sys.stderr)
        return 1
    args.summary.parent.mkdir(parents=True, exist_ok=True)
    args.summary.write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Built {args.output_sqlite}: {summary['poiCount']:,} POIs across {len(summary['categoryCounts'])} categories.")
    print(json.dumps(summary["categoryCounts"], ensure_ascii=False, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
