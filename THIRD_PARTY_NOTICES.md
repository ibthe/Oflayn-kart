# Third-party notices

The application source-code license has not been selected yet. Each dependency and dataset retains its own license.

## OpenStreetMap data

© OpenStreetMap contributors. OpenStreetMap data is available under the Open Database License (ODbL) 1.0. Attribution and license information: https://www.openstreetmap.org/copyright

The Georgia country extract is obtained from Geofabrik: https://download.geofabrik.de/europe/georgia.html. The CI-generated Mapsforge file is a Produced Work derived from OSM data; observe ODbL attribution and any applicable share-alike obligations for derivative databases. The generated `georgia-map-build.json` records source URL, checksum, timestamp and attribution.

## Mapsforge

The Android application uses Mapsforge Android renderer and themes version 0.25.0 from Maven Central. The CI resolves the published `org.mapsforge:mapsforge-map-writer:0.25.0` Maven Central artifact and runtime dependencies into an Osmosis plugin JAR in an isolated Java-only Gradle project; it does not configure the full upstream Android/sample multi-project. The JAR is loaded by Ubuntu/Debian Osmosis through `/etc/osmosis/plexus.conf` and `/usr/share/osmosis/`. Preserve upstream license and notices. Mapsforge is licensed under GNU Lesser General Public License v3.0, subject to the terms and notices in the exact release: https://github.com/mapsforge/mapsforge

## PyOsmium

PyOsmium is used only in the GitHub Actions data-preparation workflow to extract local POI records from OSM PBF files. It is not bundled in the Android APK. PyOsmium is available under the BSD 2-Clause License; retain its upstream copyright and license notice: https://github.com/osmcode/pyosmium

## Osmosis

Osmosis is used only in the GitHub Actions data preparation workflow and is not bundled as an Android runtime component. Preserve its upstream license and notices: https://github.com/openstreetmap/osmosis

## BRouter (planned)

BRouter is a planned offline-routing candidate. It is not yet included in the APK. Its upstream project is MIT licensed: https://github.com/abrensch/brouter

## Copernicus WorldDEM-90 / Copernicus DEM GLO-90

The application bundles derived, gzip-compressed 90 m elevation tiles for Georgia from Copernicus WorldDEM-90 / Copernicus DEM GLO-90. Preserve the product attribution:

`Produced using Copernicus WorldDEM-90 © DLR e.V. 2010–2014 and © Airbus Defence and Space GmbH 2014–2018, provided under COPERNICUS by the European Union and ESA; all rights reserved.`

The dataset is a digital surface model (DSM), not bare-earth terrain. Source, public COG mirror, tile conversion and limitations are documented in `docs/DATA_SOURCES.md`.

## Rasterio and NumPy (build-time only)

The CI workflow uses the Ubuntu packages `python3-rasterio` and `python3-numpy` to read public Copernicus COG tiles and create compact offline assets. Neither package is included in the Android APK. Preserve their upstream notices: Rasterio is BSD 3-Clause and NumPy is BSD 3-Clause.

## Inter typeface

The app bundles Inter and Inter Display (Regular, Medium, SemiBold, Light), subset to Latin and Cyrillic glyphs and installed as `app/src/main/res/font/*.otf`. Copyright 2016–2023 The Inter Project Authors (https://github.com/rsms/inter). Licensed under the SIL Open Font License 1.1 (https://openfontlicense.org). The upstream copyright and license statement is kept in `app/src/main/assets/licenses/Inter-LICENSE.txt`. The subset keeps the original family names; the fonts are not sold on their own.
