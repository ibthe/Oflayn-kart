# Changelog

## 0.5.2 — исправлена установка PyOsmium на Ubuntu 26.04

- В GitHub Actions заменён несуществующий на Ubuntu 26.04 пакет `python3-osmium` на актуальное имя `python3-pyosmium`.
- Модуль Python по-прежнему импортируется как `osmium`; код извлечения POI не меняет API и использует тот же пакет PyOsmium.
- Обновлены preflight-проверки, smoke-test diagnostics, инструкции локальной установки и сведения об источниках.
- Android `versionName` — `0.5.2`, `versionCode` — `14`; схема POI SQLite остаётся версии 1, индекс поиска — revision 2.

## 0.5.1 — расширенные сведения о местах и поиск по контактам

- Локальный POI индекс расширен поисковыми значениями адреса, телефона, сайта, часов работы, кухни, оператора и бренда.
- Карточки мест отображают адрес, телефон, сайт, часы работы, кухню, доступность, интернет и оператора/бренд — только когда такие теги заполнены в OpenStreetMap.
- Часы работы показываются как исходная строка OSM и не интерпретируются как live-статус «открыто сейчас».
- Добавлены проверки индексации практических полей; исправлены дублирующиеся разделы в документации и обновлена инструкция копирования полного пакета.
- Android `versionName` — `0.5.1`, `versionCode` — `13`; POI schema остаётся версии 1, индекс поиска поднят до revision 2, формат таблиц SQLite не изменён.
- Полная генерация POI/карты через GitHub Actions и Android APK всё ещё требует подтверждения CI.

## 0.5.0 — офлайн-справочник мест и поиск на трёх языках

- Добавлен PyOsmium pipeline: из того же проверенного PBF Грузии извлекаются OSM nodes и closed ways с полезными тегами объектов. Используется disk-backed `dense_mmap_array` для location index.
- Создаётся SQLite база `georgia-poi.sqlite` с категориями еды, жилья, туризма, медицины, транспорта, сервисов, магазинов, истории, природы и населённых пунктов.
- В базу сохраняются `name`, `name:en`, `name:ru`, `name:ka`, координаты, тип OSM и компактная копия OSM-тегов. Поиск работает по локальному нормализованному полю и не требует FTS5 или интернет-доступа.
- Добавлены unit tests для классификации, bbox, нормализации трёх письменностей и SQLite схемы; добавлен реальный PyOsmium smoke-test на тестовых nodes/closed way.
- CI кеширует, проверяет SHA-256, SQLite integrity и row count карты и POI; проверяет, что оба файла и manifest действительно находятся внутри APK без сжатия.
- Экран «Места» получил поиск, фильтры категорий, многоязычные варианты имён и копирование координат. SQLite asset устанавливается в app-private storage по SHA-256.
- Ограничения: извлекаются nodes и closed ways; relation-only POI не включены. Обычный `name` и переводные `name:*` показываются только если присутствуют в OSM. Это начальная POI база, а не исчерпывающий справочник всех организаций.
- Android `versionName` — `0.5.0`, `versionCode` — `12`. Полную сборку APK и генерацию реальной POI базы должен подтвердить GitHub Actions.

## 0.4.7 — explicit Georgia bounding box for Mapsforge generation

- Fixed the real country-map generation failure from CI: Mapsforge Writer started reading the verified 109.4 MB Georgia PBF, then aborted because the PBF header did not provide a usable bounding box.
- Pass an explicit Georgia-country bounding box to `--mapfile-writer`: `41.00,39.95,43.65,46.80` in the documented `minLat,minLon,maxLat,maxLon` order. The values enclose the extreme points published by Geostat with a small safety margin.
- The Mapsforge writer smoke test now also passes an explicit test bbox, so the bbox argument is exercised before the full extract is processed.
- Added preflight checks that require the full-map bbox and smoke-test bbox to remain present.
- The previous CI run confirms: Android unit tests passed; the OSM mirror download and checksum verification passed; Mapsforge Writer 0.25.0 loaded and passed its real smoke test. The only failure was the missing bbox during full-country map generation.
- Bumped Android `versionName` to `0.4.7` and `versionCode` to `11`.

## 0.4.6 — valid OSM timestamp metadata in writer smoke fixture

- Fixed the real Mapsforge writer smoke test failure: the test OSM XML entities now include mandatory `timestamp` attributes in ISO-8601 UTC form.
- Added standard OSM entity metadata (`changeset`, `uid`, `user`) to every fixture node and way so the smoke input represents a valid OSM XML extract.
- Strengthened `verify_map_build_config.py` to fail before installing Osmosis if any fixture entity is missing required metadata or has an invalid timestamp.
- Confirmed from the 0.4.5 CI log that the Mapsforge plugin was already loaded successfully: `MapFileWriterTask` was instantiated and started reading the XML. The failure occurred later, in the fixture parser, not in plugin registration.
- Bumped Android `versionName` to `0.4.6` and `versionCode` to `10`.

## 0.4.5 — Osmosis Debian classpath integration

- Fixed Mapsforge Map Writer plugin loading for Ubuntu's Debian-packaged Osmosis.
- Install the fat JAR under `/usr/share/osmosis/` and register it in `/etc/osmosis/plexus.conf`; the prior user JPF plugin directory did not place the `osmosis-plugins.conf` resource on the classpath used by the Debian launcher.
- Added a real minimal OSM-to-Mapsforge smoke test before downloading the full Georgia extract.
- Added a compact OSM XML fixture and preflight checks for the Plexus classpath directive and smoke-test fixture.
- Version code raised to 9 so test APK upgrades can replace 0.4.4.

## 0.4.4 — corrected OSM fallback and isolated Mapsforge writer build

- Corrected the OpenStreetMap France mirror URL to `georgia.osm.pbf`; the matching checksum endpoint is now `georgia.osm.pbf.md5`. The previous `georgia-latest.osm.pbf.md5` URL returned HTTP 404.
- Updated map-manifest validation to accept the exact stable mirror URL while retaining the Geofabrik source URL for existing valid caches.
- Replaced the full Mapsforge source-tree Gradle build with a small isolated Gradle project that resolves the published `org.mapsforge:mapsforge-map-writer:0.25.0` artifact and its runtime dependencies from Maven Central.
- The writer package task merges runtime JARs and preserves the Osmosis plugin descriptor; CI checks that Osmosis registers `--mapfile-writer`.
- Added a preflight script that verifies source/checksum URL pairing, the Maven-based writer builder, and fail-fast workflow configuration before network or map-generation work begins.
- Removed ignored prerequisite failures; a failed Osmosis install or writer build now stops the job before attempting map generation.
- Android `versionName` is `0.4.4` and `versionCode` is `8`. The real map generation and APK packaging still require confirmation in GitHub Actions.

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
