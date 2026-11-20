# Changelog

## 0.4.2 — fix Osmosis version check in map CI

- Fixed the map-generation workflow failing immediately after installing Osmosis.
- Removed the unsupported `osmosis --version` invocation. Osmosis treats command-line arguments as pipeline tasks, so `version` was interpreted as an unknown task type.
- The workflow now reads the installed Debian package version with `dpkg-query`, while the Mapsforge installer still validates plugin registration with the documented `osmosis --help` check.
- Bumped Android `versionCode` to 6 and `versionName` to 0.4.2.

## 0.4.1 — map update safety

- The app stores whether the active map is bundled with the APK or imported by the user.
- Bundled map installation checks the embedded SHA-256 manifest and refreshes the app-private map copy after an APK update when the bundled map changes.
- User-imported map files are preserved and are not overwritten by the bundled-map updater; importing a custom map also invalidates the bundled-map hash marker so restoring the APK map copies the actual bundled bytes.
- Added a persistent explicit map-disable setting and a restore-bundled-map action.
- Added a free-space check before copying a large map into app-private storage.
- Reduced map-writer concurrency and Java heap setting for more conservative CI memory use.

## 0.4.0 — Georgia map build pipeline

- Added GitHub Actions pipeline to retrieve the Geofabrik Georgia country PBF and validate its MD5 checksum.
- Added a CI step to build the Mapsforge Map Writer 0.25.0 Osmosis plugin.
- Added reproducible map generation in Osmosis disk-backed `type=hd` mode with requested language variants `en`, `ru`, `ka`.
- Added a checksum-keyed cache for the generated country map and its build manifest.
- Added SHA-256 verification of the map before Gradle packaging and after reading the map from the APK.
- Added app startup installation of the bundled map to app-private storage.
- Updated the map screen and documentation to distinguish generated map assets from the source ZIP.
- First successful full GitHub Actions run is still pending; the pipeline has not been run in this environment.

## 0.3.0 — Mapsforge renderer

- Added Mapsforge Android MapView renderer (Maven Central 0.25.0).
- Added local `.map` import through Android's document picker, internal copy and Mapsforge-header validation.
- Added Georgia bounding-box intersection validation before accepting a map file.
- Map viewer supports pan/zoom and a scale bar; no network tile source is configured.
- Added deterministic preferred-language selection for the current single-language Mapsforge renderer.
- Added CI manifest-merger protection directives to remove network permissions declared transitively by libraries.

## 0.2.0 — GPS tools foundation

- Added user-initiated GPS session on the Tools screen.
- Shows WGS84 coordinates, DMS, GPS accuracy, altitude, speed and movement bearing.
- Added electronic compass using Android rotation-vector sensor when available.
- Added copy-coordinates action and locally stored car marker.
- GPS listener is removed when stopped or when the screen leaves composition.
- No background location permission and no network permission.
- Added unit tests for coordinate and compass formatting.
- CI checks merged manifest after debug APK assembly.
