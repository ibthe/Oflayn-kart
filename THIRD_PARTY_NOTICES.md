# Third-party notices

The application source-code license has not been selected yet. Each dependency and dataset retains its own license.

## OpenStreetMap data

© OpenStreetMap contributors. OpenStreetMap data is available under the Open Database License (ODbL) 1.0. Attribution and license information: https://www.openstreetmap.org/copyright

The Georgia country extract is obtained from Geofabrik: https://download.geofabrik.de/europe/georgia.html. The CI-generated Mapsforge file is a Produced Work derived from OSM data; observe ODbL attribution and any applicable share-alike obligations for derivative databases. The generated `georgia-map-build.json` records source URL, checksum, timestamp and attribution.

## Mapsforge

The Android application uses Mapsforge Android renderer and themes version 0.25.0 from Maven Central. The CI also builds the Mapsforge Map Writer 0.25.0 from the corresponding upstream source tag as an Osmosis plugin. Preserve upstream license and notices. Mapsforge is licensed under GNU Lesser General Public License v3.0, subject to the terms and notices in the exact release: https://github.com/mapsforge/mapsforge

## Osmosis

Osmosis is used only in the GitHub Actions data preparation workflow and is not bundled as an Android runtime component. Preserve its upstream license and notices: https://github.com/openstreetmap/osmosis

## BRouter (planned)

BRouter is a planned offline-routing candidate. It is not yet included in the APK. Its upstream project is MIT licensed: https://github.com/abrensch/brouter

## Copernicus DEM / Sentinel (planned)

Copernicus data products have their own product-specific attribution and redistribution conditions. No Copernicus raster data is included in v0.4.0. See `docs/DATA_SOURCES.md` before adding any derivative product.
