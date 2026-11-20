#!/usr/bin/env python3
"""Verify the app does not request network or background-location permissions."""
from __future__ import annotations

import argparse
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE_MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
FORBIDDEN = {
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.ACCESS_WIFI_STATE",
    "android.permission.ACCESS_BACKGROUND_LOCATION",
}


def permissions_in(path: Path) -> set[str]:
    root = ET.parse(path).getroot()
    found: set[str] = set()
    for element in root.iter():
        tag = element.tag.rsplit("}", 1)[-1]
        if tag.startswith("uses-permission"):
            name = element.attrib.get(ANDROID_NS + "name", "")
            if name:
                found.add(name)
    return found


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--include-merged",
        action="store_true",
        help="also inspect Android Gradle Plugin merged manifests under app/build",
    )
    args = parser.parse_args()

    if not SOURCE_MANIFEST.exists():
        print(f"ERROR: manifest not found: {SOURCE_MANIFEST}", file=sys.stderr)
        return 2

    manifests = [SOURCE_MANIFEST]
    if args.include_merged:
        merged_root = ROOT / "app/build/intermediates"
        if merged_root.exists():
            for candidate in merged_root.rglob("AndroidManifest.xml"):
                normalized = str(candidate).lower()
                if "merged" in normalized or "manifest" in normalized:
                    manifests.append(candidate)
        if len(manifests) == 1:
            print("WARNING: no merged manifest found; source manifest was checked.")

    violations: list[tuple[Path, list[str]]] = []
    for manifest in sorted(set(manifests)):
        found = sorted(permissions_in(manifest) & FORBIDDEN)
        if found:
            violations.append((manifest, found))

    if violations:
        for manifest, found in violations:
            print(f"ERROR: forbidden permissions in {manifest.relative_to(ROOT)}: {', '.join(found)}", file=sys.stderr)
        return 1

    checked = len(set(manifests))
    print(f"OK: checked {checked} manifest(s); no network/background-location permissions found.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
