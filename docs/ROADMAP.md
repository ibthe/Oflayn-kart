# План разработки Georgia Offline

| Этап | Результат | Состояние |
|---|---|---|
| 0 | Репозиторий, Android-проект, навигация экранов, локальные настройки и CI | Базовый каркас есть |
| 1 | Загрузка OSM PBF, генерация `.map`, контрольные суммы и кеш CI | Реализовано в коде 0.4.0; нужен первый успешный CI run |
| 2 | Mapsforge renderer, country map asset, стили и локальное отображение | Renderer есть; карта встраивается через CI, отображение требует CI-проверки |
| 3 | Независимые переключатели и одновременные многоязычные подписи | Реализован multi-language MapDataStore; требуется проверка рендеринга и производительности на устройстве |
| 4 | Локальная POI SQLite, категории и поиск по трём именам | Nodes, closed ways и multipolygon relations включены; relation extraction и полнота набора требуют проверки на полном PBF; FTS остаётся необязательной оптимизацией |
| 5 | DEM, горизонтали, hillshade и профили высот | Запланировано |
| 6 | BRouter: car / bike / foot + осторожный профиль 4×4 | Запланировано |
| 7 | Путеводитель, практические POI, избранное и планировщик | Запланировано |
| 8 | GPX, запись треков, локальные средства безопасности | GPS/компас есть; GPX и track recorder запланированы |
| 9 | Спутниковые мозаики, прямое хранение на SD, контроль размера APK | Запланировано |
| 10 | Реальное тестирование на Android устройствах и release APK | Запланировано |

## Завершено ранее

### 0.2.0 — GPS инструменты

- [x] GPS только после явного действия пользователя.
- [x] WGS84, DMS, точность, высота, скорость, курс.
- [x] Электронный компас при наличии rotation vector sensor.
- [x] Копирование координат и сохранение метки автомобиля.

### 0.3.0 — Mapsforge renderer

- [x] Локальный `MapView` без online tile source.
- [x] Импорт локального `.map` через SAF и проверка пересечения bbox с Грузией.
- [x] Первый набор картографических тем.

### 0.4.1 — Map update safety

- [x] Обновление встроенной карты при изменении SHA-256 в новом APK.
- [x] Пользовательский импорт защищён от перезаписи.
- [x] Отключение/включение карты сохраняется между запусками.
- [x] Проверка свободного места перед копированием встроенной карты.

### 0.4.0 — Map build pipeline

- [x] Geofabrik Georgia primary source, OpenStreetMap France fallback, retries and provider MD5 verification.
- [x] Mapsforge Map Writer в CI, Osmosis `type=hd`.
- [x] Запрос языковых вариантов `en,ru,ka`.
- [x] Кеширование карты по checksum исходного PBF.
- [x] Метаданные SHA-256, размер и ODbL attribution.
- [x] Проверка фактического наличия карты внутри APK.
- [ ] Первый зелёный workflow run на GitHub — ожидается.

### 0.5.3 — локальный POI справочник и практические сведения

- [x] PyOsmium extracts named and categorized OSM nodes and closed ways from the same verified Georgia PBF.
- [x] SQLite schema stores English, Russian and Georgian names when present, category, coordinates and tags.
- [x] Local category/name/address/phone/website search and copy-coordinate action in the Discover screen.
- [x] POI cards display address, phone, website, opening hours, cuisine and public access details when OSM tags exist.
- [x] POI SHA-256 / row-count / SQLite-integrity checks in CI and APK validation.
- [ ] CI run must still confirm full PBF processing, APK packaging and actual device search.

## Следующие этапы

1. Получить успешный CI run и визуально проверить покрытие, подписи, скорость отрисовки и размер карты на Android.
2. Проверить на реальном рендерере, что stacked MultiMapDataStore показывает подписи en/ru/ka одновременно без чрезмерного дублирования объектов и потери производительности; при необходимости оптимизировать label-only overlay.
3. Проверить полноту relation-derived POI на полном PBF, устранить возможные дубли объектов между node/way/relation, расширить категории и сведения об организациях; затем оценить FTS и пространственный индекс.
4. Подключить BRouter segments/profiles с отдельной проверкой 4×4.
5. Добавить DEM, контуры, hillshade и локальную высоту.
6. Реализовать прямую запись больших данных в SD-card tree через SAF.
7. Только после этого добавлять спутниковые мозаики в одно APK и оценивать размер.


### 0.5.4 — диагностика обработки PBF

Добавлены периодические heartbeat-сообщения для длительного PyOsmium scan. Следующий прогон должен показать, продолжает ли извлекатель читать узлы и ways, сколько POI собрано и сколько времени заняла обработка.
