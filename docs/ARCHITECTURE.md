# Архитектура

```text
Compose UI
  ├── Map / Discover / Route / Saved / Tools / Settings
  └── ViewModels + local settings
       ├── MapRenderer interface ── Mapsforge adapter
       ├── OfflineSearch interface ── SQLite + FTS index
       ├── OfflineRouter interface ── BRouter integration
       ├── ElevationProvider ── local DEM / hillshade
       ├── TrackRecorder ── Android Location + SensorManager
       ├── UserRepository ── local SQLite/Room
       └── StorageBackend ── internal dir / SAF-selected SD card

APK assets
  ├── Georgia vector map (.map)
  ├── POI + multilingual names (.sqlite)
  ├── route graph/segments and profiles
  ├── DEM / contours / hillshade
  ├── optional satellite mosaic
  └── data-manifest.json + license notices
```

## Key decisions

- No network stack is added to the Android app. The manifest must not contain INTERNET.
- The map renderer is separated from Compose UI so map engine changes do not alter screens.
- Mapsforge is the current renderer candidate: its compact vector map format is intended for offline rendering and supports XML render themes and hillshading.
- BRouter is the current offline-routing candidate. Its routing API and packaging method must be validated in an Android prototype before locking the integration.
- All region data is built at development/release time, not downloaded by the installed app.
- The one-APK decision is implemented by putting binary assets in `app/src/main/assets/offline/`; some files are extracted locally for random access.
- The SD-card choice uses SAF tree URI permissions; physical paths are not hard-coded.
