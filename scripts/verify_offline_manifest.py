#!/usr/bin/env python3
"""Fail if the app manifest requests any network permission."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
FORBIDDEN = {
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.ACCESS_WIFI_STATE",
}


def main() -> int:
    if not MANIFEST.exists():
        print(f"ERROR: manifest not found: {MANIFEST}", file=sys.stderr)
        return 2
    root = ET.parse(MANIFEST).getroot()
    found = []
    for element in root.findall("uses-permission"):
        name = element.attrib.get(ANDROID_NS + "name", "")
        if name in FORBIDDEN:
            found.append(name)
    if found:
        print("ERROR: network permissions found:", ", ".join(found), file=sys.stderr)
        return 1
    print("OK: no network permissions in AndroidManifest.xml")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
