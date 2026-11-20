# Данные и источники

## Базовая карта

- OpenStreetMap Georgia extract (Geofabrik): https://download.geofabrik.de/europe/georgia.html
- Формат исходника: `.osm.pbf`.
- Данные обновляются только при выпуске новой версии приложения; приложение не обновляет их в фоне.
- Лицензия данных: ODbL 1.0. Указывать «© OpenStreetMap contributors» и ссылку https://www.openstreetmap.org/copyright.

## Высоты и рельеф

- Copernicus DEM GLO-30 / GLO-90: https://dataspace.copernicus.eu/explore-data/data-collections/copernicus-contributing-missions/collections-description/COP-DEM
- Альтернатива/дополнение: USGS SRTM 1 arc-second (около 30 м): https://www.usgs.gov/faqs/where-can-i-get-global-elevation-data
- Производные контуры и hillshade являются результатом обработки и должны сохранять attribution исходных данных.

## Спутниковый слой

- Copernicus Data Space: https://dataspace.copernicus.eu/
- Для Sentinel-2 использовать корректно подготовленные локальные мозаики и метаданные покрытия/даты/облачности.
- Включение в APK возможно только после проверки условий повторного распространения и оценки размера файла.

## Программные компоненты-кандидаты

- Mapsforge: https://github.com/mapsforge/mapsforge — локальная векторная отрисовка, POI инструменты и hillshading; лицензия LGPLv3.
- BRouter: https://github.com/abrensch/brouter — локальная маршрутизация с профилями и учётом рельефа; лицензия MIT.
- Android Jetpack Compose: https://developer.android.com/develop/ui/compose

## Обязательные метаданные для каждого набора

`dataset_id`, `version`, `created_at`, `source_url`, `source_timestamp`, `license_id`, `attribution`, `coverage_bbox`, `sha256`, `schema_version`, `notes`.
