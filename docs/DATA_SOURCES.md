# Данные и источники

## Базовая карта Грузии

- Страновой extract Geofabrik: https://download.geofabrik.de/europe/georgia.html
- PBF: `https://download.geofabrik.de/europe/georgia-latest.osm.pbf`
- MD5 endpoint: `https://download.geofabrik.de/europe/georgia-latest.osm.pbf.md5`
- Формат исходника: OSM PBF.
- Лицензия данных: Open Database License (ODbL) 1.0. Атрибуция: `© OpenStreetMap contributors` и ссылка https://www.openstreetmap.org/copyright.
- Карта обновляется во время еженедельного CI build или по ручному запросу; основной источник — Geofabrik, резерв — OpenStreetMap France. Runtime-приложение источники не опрашивает.

Geofabrik Georgia extract здесь означает страну Грузию на Кавказе, а не американский штат Georgia. Geofabrik использует URL из каталога `europe/georgia`; резервное зеркало OpenStreetMap France публикует ту же страну в каталоге `asia/georgia`.

## Mapsforge writer

- Writer documentation: https://github.com/mapsforge/mapsforge/blob/master/docs/Getting-Started-Map-Writer.md
- Writer — Osmosis plugin для преобразования OSM в Mapsforge `.map`.
- В CI версия writer закреплена на `0.25.0`, чтобы сборки были воспроизводимее.
- Параметр `preferred-languages=en,ru,ka` записывает дефолтный вариант и доступные языковые варианты с этими тегами. Если объект не содержит `name:ru`, Map Writer не создаёт русский перевод сам.
- Параметр `type=hd` использует дисковый режим временных данных и снижает требование к RAM; параллельность выставлена консервативно.
- Документация Map Writer рекомендует RAM-режим только для небольших PBF из-за ориентировочного расхода RAM около десятикратного размера входного файла.

## Высоты и рельеф (запланировано)

- Copernicus DEM GLO-30 / GLO-90: https://dataspace.copernicus.eu/explore-data/data-collections/copernicus-contributing-missions/collections-description/COP-DEM
- Альтернатива/дополнение: USGS SRTM 1 arc-second: https://www.usgs.gov/faqs/where-can-i-get-global-elevation-data
- Производные контуры и hillshade должны сохранять attribution исходного набора.

## Спутниковый слой (запланировано)

- Copernicus Data Space: https://dataspace.copernicus.eu/
- Для Sentinel-2 нужны локальные мозаики и метаданные покрытия, даты и облачности. Включение в APK возможно только после проверки условий повторного распространения и оценки размера.

## Программные компоненты

- Mapsforge Android 0.25.0: https://github.com/mapsforge/mapsforge — офлайн-векторная отрисовка и `.map`, лицензия LGPLv3.
- Osmosis: https://github.com/openstreetmap/osmosis — обработка OSM/PBF в CI.
- BRouter: https://github.com/abrensch/brouter — кандидат для будущей локальной маршрутизации, лицензия MIT; ещё не включён в приложение.
- Android Jetpack Compose: https://developer.android.com/develop/ui/compose

## Обязательные метаданные

Для каждого будущего набора хранить source URL, дату получения, исходную контрольную сумму, лицензию, attribution, bbox, SHA-256, версию схемы и notes. CI map manifest уже содержит source URL, UTC timestamp, source PBF MD5, map SHA-256, размер, версию Map Writer, ODbL и attribution.


## Резервный источник выгрузки страны Georgia (CI 0.4.3)

Основной источник: `https://download.geofabrik.de/europe/georgia-latest.osm.pbf`.

Резервное зеркало: `https://download.openstreetmap.fr/extracts/asia/georgia-latest.osm.pbf`. Каталог `asia` — способ классификации этого зеркала; сама выгрузка соответствует стране Georgia на Кавказе. В CI каждый `.osm.pbf` проверяется по `.md5` от того же провайдера. Источник и MD5 записываются в `georgia-map-build.json`.

Резервный источник может обновляться реже основного. Обычные сборки используют последнюю проверенную карту из GitHub Actions cache; расписание обновляет её раз в неделю, а ручной запуск поддерживает флаг `refresh_map`.
