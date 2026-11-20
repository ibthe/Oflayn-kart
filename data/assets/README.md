# Offline data assets — single APK

The full country vector map is generated during GitHub Actions and added to this directory before Gradle packages the APK. The source ZIP intentionally does not include the generated binary: the `.map` file is recreated or restored from a checksum-keyed CI cache.

## Generated in v0.4.0

- `georgia.map` — Mapsforge vector map built from Geofabrik Georgia country PBF, using Map Writer 0.25.0 and `preferred-languages=en,ru,ka`.
- `georgia-map-build.json` — source PBF URL, retrieval time, source MD5, map SHA-256 and size, writer version, language variants requested, ODbL license and OSM attribution.

## Planned data packs

- `georgia-poi.sqlite` — searchable local POI database, including available localized names.
- `georgia-routing/` — BRouter-compatible route data and profiles for car, 4×4, bicycle and foot.
- `georgia-dem/` — DEM tiles, contours and hillshade.
- `georgia-satellite/` — preprocessed Sentinel imagery only after size and redistribution review.
- `data-manifest.json` — static bundle descriptor.

The selected distribution is one APK. Assets needing random access are copied from APK assets to app-private storage on first run. SD-card destination selection exists in the UI; direct writing to the selected Storage Access Framework directory is a later milestone.

The `.map` is not checked into Git to avoid adding a large generated binary to every source update. `.gitignore` excludes `*.map`; the CI job inserts the generated asset before `assembleDebug`.
