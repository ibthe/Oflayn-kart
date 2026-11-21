# Offline data assets — single APK

The full country vector map and searchable POI database are intended to be generated during GitHub Actions and added to this directory before Gradle packages the APK. The current workflow source is configured for this; a green full CI run is still required to confirm the complete result. The source ZIP intentionally does not include these large binaries: they are recreated or restored from a checksum-keyed CI cache.

## Generated during CI (introduced in v0.5.0, extended in v0.5.1)

- `georgia.map` — Mapsforge vector map built from the verified Georgia country PBF, using Map Writer 0.25.0 and `preferred-languages=en,ru,ka`.
- `georgia-poi.sqlite` — local SQLite place index extracted from the same PBF by PyOsmium. The first version includes OSM nodes and closed ways, category, coordinates, tags and `name` / `name:en` / `name:ru` / `name:ka` fields when present. Version 0.5.1 also indexes address, phone, website, opening hours, cuisine, wheelchair, internet access, operator and brand tags when present. Relation-only POIs are not yet included.
- `georgia-map-build.json` — source PBF URL, retrieval time, source MD5, SHA-256 and size for map and POI database, POI row count, writer version, language variants, ODbL licence and attribution.
- `data-manifest.json` — static bundle descriptor and required asset list.

## Planned data packs

- `georgia-routing/` — BRouter-compatible route data and profiles for car, 4×4, bicycle and foot.
- `georgia-dem/` — DEM tiles, contours and hillshade.
- `georgia-satellite/` — preprocessed Sentinel imagery only after size and redistribution review.

The selected distribution is one APK. Assets needing random access are copied from APK assets to app-private storage on first use and checked using the SHA-256 values in `georgia-map-build.json`. SD-card destination selection exists in the UI; direct writing to the selected Storage Access Framework directory is a later milestone.

Generated `.map` and `.sqlite` binaries are not checked into Git to avoid adding large binary assets to every source update. `.gitignore` excludes them; the CI job inserts the generated assets before `assembleDebug`.
