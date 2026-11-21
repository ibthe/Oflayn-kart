# Changelog

## 0.4.3 — resilient OSM source download and map cache

- Fixed CI failure when GitHub-hosted runners cannot connect to `download.geofabrik.de` (`curl: (7)`).
- Geofabrik remains the primary OSM data source; added the verified OpenStreetMap France Georgia-country extract as a fallback source.
- Both sources are downloaded with retries and their PBF files are validated against the provider's MD5 checksum before map generation.
- The generated map metadata now records the exact provider, source URL, checksum, retrieval time and map SHA-256.
- CI restores the last verified generated map before accessing external OSM sources. Regular code changes can build with the cached map even during a provider outage.
- Added weekly map refresh and a manual `refresh_map` option to GitHub Actions. A failed refresh keeps an existing verified map; a first build with no map cache still fails clearly rather than packaging an empty placeholder.
- Map generation now writes temporary files and replaces the installed map only after a new map passes basic validation.
- Updated application version to 0.4.3 (versionCode 7).

## 0.4.2 — Osmosis version check

- Fixed the map-generation workflow failing immediately after installing Osmosis.
- Removed the unsupported `osmosis --version` invocation; Osmosis treats command-line arguments as pipeline tasks.
- The workflow reads the installed Debian package version with `dpkg-query`; the Mapsforge installer validates plugin registration with `osmosis --help`.
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
