# Changelog

## 0.2.0 — GPS tools foundation

- Added user-initiated GPS session on the Tools screen.
- Shows WGS84 coordinates, DMS, GPS accuracy, altitude, speed and movement bearing.
- Added electronic compass using Android rotation-vector sensor when available.
- Added copy-coordinates action and locally stored car marker.
- GPS listener is removed when stopped or when the screen leaves composition.
- No background location permission and no network permission.
- Added unit tests for coordinate and compass formatting.
- CI checks merged manifest after debug APK assembly.
