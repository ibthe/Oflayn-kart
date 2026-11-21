# Данные и источники

## Базовая карта Грузии

- Страновой extract Geofabrik: https://download.geofabrik.de/europe/georgia.html
- PBF: `https://download.geofabrik.de/europe/georgia-latest.osm.pbf`
- MD5 endpoint: `https://download.geofabrik.de/europe/georgia-latest.osm.pbf.md5`
- Формат исходника: OSM PBF.
- Лицензия данных: Open Database License (ODbL) 1.0. Атрибуция: `© OpenStreetMap contributors` и ссылка https://www.openstreetmap.org/copyright.
- Карта обновляется во время еженедельного CI build или по ручному запросу; основной источник — Geofabrik, резерв — OpenStreetMap France. Runtime-приложение источники не опрашивает.

Geofabrik Georgia extract здесь означает страну Грузию на Кавказе, а не американский штат Georgia. Geofabrik использует URL из каталога `europe/georgia`; резервное зеркало OpenStreetMap France публикует ту же страну в каталоге `asia/georgia`.

## Проверенная копия исходного PBF в CI

- Подготовка исходника вынесена в `scripts/prepare_georgia_pbf.sh`, а генерация `.map` больше не выполняет сетевую загрузку.
- После проверки MD5 исходный файл и `georgia-source.json` сохраняются в GitHub Actions cache по контрольной сумме.
- Обычные повторные сборки переиспользуют PBF из cache; еженедельный workflow и ручной `refresh_map` принудительно получают свежую выгрузку.
- `cancel-in-progress: false` предотвращает автоматическое завершение работающего job при новом push в ту же ветку. Ручная отмена запуска всё ещё возможна, но сохранённый PBF останется доступен следующему запуску.
- В `/data/cache/` нет исходников приложения или бинарных карт, каталог исключён из Git и хранится в Actions cache.

## Mapsforge writer

- Writer documentation: https://github.com/mapsforge/mapsforge/blob/master/docs/Getting-Started-Map-Writer.md
- Writer — Osmosis plugin для преобразования OSM в Mapsforge `.map`.
- В CI версия writer закреплена на `0.25.0`, чтобы сборки были воспроизводимее.
- Параметр `preferred-languages=en,ru,ka` записывает дефолтный вариант и доступные языковые варианты с этими тегами. Если объект не содержит `name:ru`, Map Writer не создаёт русский перевод сам.
- Параметр `type=hd` использует дисковый режим временных данных и снижает требование к RAM; параллельность выставлена консервативно.
- Документация Map Writer рекомендует RAM-режим только для небольших PBF из-за ориентировочного расхода RAM около десятикратного размера входного файла.

## Локальный справочник POI (версия 0.5.3)

- POI извлекаются из того же проверенного OSM country PBF, который используется для Mapsforge карты.
- PyOsmium: https://github.com/osmcode/pyosmium — BSD 2-Clause, build-time only. В CI устанавливается Ubuntu package `python3-pyosmium`; модуль не входит в Android APK.
- Первая версия извлекает OSM nodes и closed ways с выбранными значениями `amenity`, `tourism`, `historic`, `natural`, `place`, `shop`, `leisure`, `waterway` и `emergency`. Relation-only POI пока не включаются.
- Сохраняются стандартные `name` и варианты `name:en`, `name:ru`, `name:ka`, если они присутствуют. Ни перевод, ни проверка качества исходных названий автоматически не выполняются.
- База SQLite хранит координаты, категорию, подкатегорию и исходные OSM tags; CI записывает SHA-256, размер и число строк в `georgia-map-build.json`.
- Поиск индексирует адресные поля, телефон, сайт, часы работы, кухню, доступность, оператора и бренд. Эти данные необязательны и отображаются только если заполнены в OSM; часы не интерпретируются как подтверждённый статус «открыто сейчас».


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


## Резервный источник выгрузки страны Georgia (CI 0.4.5)

Основной источник: `https://download.geofabrik.de/europe/georgia-latest.osm.pbf`.

Резервное зеркало: `https://download.openstreetmap.fr/extracts/asia/georgia.osm.pbf`. Каталог `asia` — способ классификации этого зеркала; сама выгрузка соответствует стране Georgia на Кавказе. В CI PBF проверяется по MD5 от того же провайдера; для резервного источника проверенный URL checksum — `https://download.openstreetmap.fr/extracts/asia/georgia.osm.pbf.md5`. Источник и MD5 записываются в `georgia-map-build.json`.

Резервный источник может обновляться реже основного. Обычные сборки используют последнюю проверенную карту из GitHub Actions cache; расписание обновляет её раз в неделю, а ручной запуск поддерживает флаг `refresh_map`.

## Сборка Mapsforge Writer в CI

Для Osmosis используется опубликованный на Maven Central артефакт `org.mapsforge:mapsforge-map-writer:0.25.0`. На GitHub-hosted Ubuntu runner JAR устанавливается в `/usr/share/osmosis/`, а строка `load /usr/share/osmosis/<writer-jar>` добавляется в `/etc/osmosis/plexus.conf`, поскольку Debian launcher запускает Osmosis из системного Plexus classpath. Успешность проверяется реальным smoke-test `--read-xml` → `--mapfile-writer`, не командой `osmosis --help`. с его runtime-зависимостями. CI собирает отдельный fat JAR в изолированном Gradle-проекте, не конфигурируя все Android/sample-проекты полного репозитория Mapsforge. В объединённый JAR должен попасть `osmosis-plugins.conf`, а реальная smoke-конвертация проверяет регистрацию и выполнение `--mapfile-writer`.


## Явная область карты Грузии (Mapsforge bbox)

В CI Mapsforge Map Writer вызывается с явным параметром `bbox=41.00,39.95,43.65,46.80` в порядке `minLatitude,minLongitude,maxLatitude,maxLongitude`. Это необходимо, поскольку PBF-зеркало в CI не предоставило writer пригодную bounding box из заголовка, и Mapsforge завершил процесс с `No valid bounding box found in input data`.

Диапазон основан на опубликованных крайних географических точках Грузии из Statistical publication 2024 Национальной службы статистики Грузии (Geostat): юг 41°02′58″ N, запад 40°00′30″ E, север 43°35′25″ N, восток 46°44′32″ E. Использован небольшой запас примерно 0,05° по каждой стороне, чтобы не отсекать граничные объекты. Источник: https://www.geostat.ge/media/67768/Yearbook_2024.pdf (раздел 1.2, стр. 11).

Параметр можно переопределить переменной окружения `GEORGIA_BBOX` при ручной сборке, но порядок координат должен сохраняться и значение должно быть четырьмя десятичными числами без пробелов.
