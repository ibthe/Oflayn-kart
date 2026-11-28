# Oflayn offline raster package format

Oflayn kart can optionally display a locally imported satellite or aerial tile pack above the offline vector map. The app does not download imagery or contact a tile server.

## Archive layout

The user imports one ZIP archive containing:

```text
manifest.json
tiles/
  6/
    33/
      21.jpg
  7/
    66/
      42.jpg
```

Tiles use the XYZ directory scheme `{z}/{x}/{y}`. Only 256×256 PNG or JPG tiles are accepted. The supported zoom range is 6–18 and the package must intersect Georgia.

## Manifest

```json
{
  "format": "oflayn-raster-pack",
  "version": 1,
  "name": "Georgia aerial imagery",
  "attribution": "© imagery provider",
  "license": "CC BY 4.0",
  "tileScheme": "xyz",
  "tileSize": 256,
  "extension": "jpg",
  "minZoom": 6,
  "maxZoom": 17,
  "tileCount": 125000,
  "bounds": [41.0, 40.0, 43.8, 46.8]
}
```

`bounds` is optional and ordered as south, west, north, east. Attribution and license are required. Do not package imagery unless the provider's terms permit offline storage, redistribution to the intended users, and display in this app. Include any additional notices required by the source license.

## Safety and storage

- ZIP paths are validated; absolute paths, traversal, unknown files, duplicate paths and malformed image headers are rejected.
- Import uses a temporary directory and renames it only after validation.
- Packages live separately from the vector map and can be stored beside the selected map storage location.
- Removing a package does not modify the vector map or POI database.
- If a removable storage volume is unavailable, its package is not offered until the volume is mounted again.
- The raster layer is optional, disabled after import, and uses a user-adjustable opacity so vector roads and labels remain readable.
