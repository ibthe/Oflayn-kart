# Offline data assets (single-APK policy)

The release APK will embed the preprocessed Georgia data under this directory.
The current starter repository intentionally contains no generated binary map
files yet; the first real data build must be produced from recorded source dates
and license notices.

Planned release assets:

- `georgia.map` — Mapsforge vector map for the country.
- `georgia-poi.sqlite` — searchable local POI database, including localized names.
- `georgia-routing/` — BRouter-compatible route data and project profiles.
- `georgia-dem/` — Copernicus DEM GLO-30 or SRTM-derived tiles and contours.
- `georgia-satellite/` — preprocessed Copernicus/Sentinel imagery, only after
  package-size and coverage validation.
- `data-manifest.json` — data source, extraction timestamp, license, checksum,
  geographic bounds and schema version.

The user selected one APK. Therefore the intended distribution is a single
APK containing all release assets. Assets that need random file access will be
unpacked once, on-device, into app-managed storage chosen by the user (internal
storage by default, SD card through Android's Storage Access Framework when
supported). The app must show the required free space before extraction.

Do not place copyrighted commercial basemaps or tiles in this directory.
