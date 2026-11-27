# План разработки Georgia Offline

| Этап | Результат | Состояние |
|---|---|---|
| 0 | Репозиторий, Android-проект, навигация экранов, локальные настройки и CI | Базовый каркас есть |
| 1 | Загрузка OSM PBF, генерация `.map`, контрольные суммы и кеш CI | Реализовано; полный APK/офлайн-map CI checkpoint прошёл для commit `af717b5` |
| 2 | Mapsforge renderer, country map asset, стили и локальное отображение | Renderer и проверенная упаковка карты реализованы; финальная визуальная проверка на устройстве отложена до общего QA |
| 3 | Независимые переключатели и одновременные многоязычные подписи | Реализован multi-language MapDataStore; требуется проверка рендеринга и производительности на устройстве |
| 4 | Локальная POI SQLite, категории и поиск по трём именам | Nodes, closed ways и multipolygon relations включены; relation smoke-тест, area-weighted centroids и дедупликация одинаковых POI node/way/relation в радиусе 18 м реализованы; полнота набора на полном PBF и поисковая релевантность требуют аудита; FTS остаётся необязательной оптимизацией |
| 5 | DEM, горизонтали, hillshade и профили высот | Copernicus GLO-90, локальная выборка высот, профиль маршрута и HGT hillshade через Mapsforge реализованы; контурные линии остаются в плане |
| 6 | Офлайн-маршрутизация: авто / мотоцикл / 4×4 / велосипед / пешком | Собственный A* граф и пять профилей реализованы; мотоцикл использует отдельный access bit и OSM motorcycle tags; signed incline forward/backward и разные uphill/downhill costs учитываются; поиск вдоль маршрута реализован; node-via no_*/only_* restrictions, multi-member no_entry/no_exit, except masks и same-way U-turns учитываются; via-way/conditional restrictions и DEM-derived grade coverage ещё нужны |
| 7 | Путеводитель, практические POI, избранное и планировщик | Каталог, избранное и локальный план по дням с изменением порядка и построением маршрута дня реализованы; офлайн-путеводитель по 8 регионам содержит связанные подборки мест, последовательности остановок и советы по локальным сервисам; места ведут к поиску в офлайн-каталоге |
| 8 | GPX, запись треков, локальные средства безопасности и расширенная навигационная панель | GPX 1.1 импорт/экспорт с заметками, локальное хранение, foreground GPS recorder, crash-safe checkpoints, восстановление, показ и возврат по треку реализованы; SOS-панель показывает 112, GPS-координаты и точность; пользователь может сохранить локальный экстренный контакт, открыть набор номера или SMS с координатами; добавлены памятка действий при ДТП и расширенный локальный чек-лист; диагностика заряда/питания и read-only сети реализована |
| 9 | Спутниковые мозаики, прямое хранение на SD, контроль размера APK | Выбор внутренней памяти/SD и безопасный перенос `.map` реализованы; автоматические тесты имитируют подключённую/отключённую SD и ошибку переноса; физические карты ещё нужно проверить. Опциональный raster-pack импорт, хранение, attribution и слой Mapsforge реализованы; сами лицензированные снимки не включены в APK. |
| 10 | Реальное тестирование на Android устройствах и release APK | Финальный этап после реализации всех функций; пока не начинаем полную визуальную приёмку |

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
- [x] Успешный CI checkpoint: unit tests, road graph build, verified offline map asset, APK and instrumentation APK packaging.

### 0.5.3 — локальный POI справочник и практические сведения

- [x] PyOsmium extracts named and categorized OSM nodes, closed ways and multipolygon relations from the verified Georgia PBF; a relation smoke fixture checks multilingual names and centroid output.
- [x] SQLite schema stores English, Russian and Georgian names when present, category, coordinates and tags.
- [x] Local category/name/address/phone/website search and copy-coordinate action in the Discover screen.
- [x] POI cards display address, phone, website, opening hours, cuisine and public access details when OSM tags exist.
- [x] POI SHA-256 / row-count / SQLite-integrity checks in CI and APK validation.
- [ ] Full-country PBF extraction still needs an uncached end-to-end run and on-device search relevance review.

## Следующие этапы — функциональность прежде финальной проверки

1. Проверить визуальную плотность и производительность слоя контурных линий на разных масштабах; hillshade, контуры и профиль высот уже реализованы.
2. Довести OSM turn restrictions: поддержать via-way и conditional relations, затем добавить DEM-derived grade estimation для дорог без incline tags. Node-via no_*/only_* и signed forward/backward incline costs уже реализованы; проверить сложные перекрёстки и серпантины.
3. Завершить проверку хранения на реальных SD-картах: автоматические тесты выбора mounted/unmounted volume и сохранения исходного файла при ошибке переноса уже добавлены; остаются физическое извлечение/повторное подключение, нехватка места и большие файлы.
4. Подготовить и проверить один легально распространяемый пример спутникового пакета для Грузии, затем проверить качество, размер, скорость и атрибуцию на реальном устройстве. Формат ZIP, импорт и слой уже реализованы; сами снимки не включены в APK.
5. Проверить полноту POI на полном country PBF, распределение категорий и поисковую релевантность; relation-only extraction и cross-geometry deduplication уже реализованы и покрыты smoke/unit tests.


## Финальный QA — после завершения функциональности

- Полная проверка всех вкладок, размеров экранов, ориентаций, шрифтов и системных зон.
- Визуальная проверка языковых слоёв, POI, GPX overlays, картографических тем и поведения всех плавающих панелей.
- Полные сценарии маршрутизации, навигации, потери GPS, восстановления записи, trip planner и offline map storage.
- Android emulator + реальные устройства, затем release APK и итоговый аудит.


### 0.5.4 — диагностика обработки PBF

Добавлены периодические heartbeat-сообщения для длительного PyOsmium scan. Следующий прогон должен показать, продолжает ли извлекатель читать узлы и ways, сколько POI собрано и сколько времени заняла обработка.
