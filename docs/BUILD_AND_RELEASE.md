# Сборка и выпуск

## Автоматическая сборка на GitHub Actions

Workflow `.github/workflows/android-ci.yml` запускается при push в `main`/`master`, pull request или вручную (`workflow_dispatch`). Он использует Ubuntu, JDK 21, Android SDK Platform 36, Build Tools 36.0.0 и Gradle 9.6.0.

### Что происходит в полной CI-сборке 0.5.3

1. Проверяется исходный `AndroidManifest.xml` на сетевые и фоновые location-разрешения; статический preflight проверяет адреса OSM/MD5, fixture и настройки регистрации Mapsforge Writer в Debian Plexus classpath.
2. Запускаются unit tests.
3. Workflow пытается восстановить последнюю проверенную карту Грузии из GitHub Actions cache. Кеш восстанавливается до любых сетевых обращений к картографическим источникам.
4. Если карта отсутствует или требуется обновление, workflow восстанавливает исходный PBF из отдельного Actions cache. Если cache не найден или вручную/по расписанию запрошено обновление, `prepare_georgia_pbf.sh` получает PBF: Geofabrik — основной источник, OpenStreetMap France — резервное зеркало `georgia.osm.pbf`; checksum доступен как `georgia.osm.pbf.md5`. Файл проверяется по MD5 и сохраняется в cache немедленно, до тяжёлой обработки.
5. После сохранения PBF workflow устанавливает Osmosis, собирает отдельный plugin JAR из Maven Central артефакта `org.mapsforge:mapsforge-map-writer:0.25.0`, устанавливает его в `/usr/share/osmosis/` и добавляет точный `load`-путь в `/etc/osmosis/plexus.conf`. Затем выполняется реальная smoke-конвертация минимального OSM XML в `.map`; только после неё разрешается собирать карту страны.
6. PyOsmium строит `georgia-poi.sqlite` из того же проверенного PBF. Включаются POI-узлы и замкнутые ways, категории, координаты, исходные OSM-теги и языковые названия `name`, `name:en`, `name:ru`, `name:ka`, если они есть в исходных данных. Индекс поиска охватывает также адрес, телефон, сайт, часы работы, кухню и часть тегов доступности/оператора. Disk-backed `dense_mmap_array` используется для location index. Relation-only объекты пока пропускаются.
7. Mapsforge создаёт `.map` с явным `bbox=41.00,39.95,43.65,46.80`, `preferred-languages=en,ru,ka` в режиме `type=hd`. Явный bbox обязателен, потому что PBF-зеркало не предоставило пригодный bbox в заголовке. Новый файл сначала создаётся как временный.
8. Проверяются map SHA-256, POI SHA-256, размер обеих баз, SQLite integrity, число POI и языковые метаданные. Оба файла и manifest кешируются вместе. Если обновление не удалось, но уже есть проверенные кешированные assets, workflow оставляет их и продолжает сборку с предупреждением.
9. Gradle собирает APK; отдельный скрипт проверяет, что и `georgia.map`, и `georgia-poi.sqlite` включены в APK без сжатия и совпадают с контрольными суммами manifest. Проверяется итоговый merged manifest, затем APK публикуется в Artifacts на 14 дней.

Обычные push-сборки используют последнюю проверенную карту из кеша и не обращаются к OSM-источникам, пока карта доступна. Если карты нет, отдельный PBF cache позволяет возобновить генерацию после отмены/сбоя без повторного скачивания источника. Concurrency настроен так, чтобы новый push не отменял уже работающую сборку карты. Обновление карты автоматически запускается раз в неделю. Чтобы принудительно обновить карту вручную, открой Actions → Android CI → Run workflow и включи `refresh_map`.

**Важно:** OpenStreetMap France группирует выгрузку страны Georgia в каталоге `asia`. Это всё равно страна Georgia на Кавказе, а не американский штат Georgia. Резервное зеркало может отставать от Geofabrik по дате обновления; фактически использованный провайдер и MD5 записываются в `georgia-map-build.json`.

GitHub Actions использует интернет только во время сборки для получения открытых исходных данных и зависимостей. Установленное приложение не получает разрешение `INTERNET` и не обращается к картографическим серверам.

## Где скачать APK

1. Открой репозиторий на GitHub.
2. Перейди во вкладку **Actions**.
3. Открой последний зелёный запуск **Android CI**.
4. Внизу страницы найди **Artifacts** и скачай `GeorgiaOffline-debug-apk`.
5. Распакуй скачанный artifact ZIP и установи APK на Android. Для debug-сборки Android может показать стандартное предупреждение о неизвестном источнике установки.

Если Actions завершился красным, скачай полный лог конкретного workflow run и отправь ZIP с логами — не нужно вручную редактировать код.

## Локальная сборка

Требуются Android Studio, Android SDK Platform 36, Build Tools 36.0.0 и JDK 17 или новее. Локальная сборка из чистого исходного ZIP не содержит бинарный `georgia.map`, потому что карта генерируется в CI. При обновлении приложения встроенный файл сравнивается с SHA-256 из `georgia-map-build.json`; он заменяется только если данные изменились. Пользовательская карта, импортированная вручную, не перезаписывается. Для полной локальной сборки сначала установи Osmosis и PyOsmium (`sudo apt-get install osmosis python3-pyosmium`), затем выполни `scripts/install_mapsforge_writer.sh` и `scripts/build_georgia_map.sh`, затем: `gradle :app:testDebugUnitTest :app:assembleDebug`.

## Локальная база объектов POI

`georgia-poi.sqlite` строится из того же проверенного country PBF с помощью PyOsmium. Первая версия индексирует узлы и замкнутые ways; relation-only объекты пока пропускаются. Приложение копирует SQLite asset в private storage при первом открытии экрана «Места», проверяет SHA-256 и выполняет локальные параметризованные SQLite-запросы. Поиск включает все сохранённые варианты имён, адрес и практические теги; карточка показывает адрес, телефон, сайт, часы работы, кухню и сведения о доступности только если они присутствуют в OSM. Часы работы отображаются как исходный текст, а не как подтверждение открытия заведения в данный момент.

## Один APK и размер

Карта `.map` генерируется в `app/src/main/assets/offline/` до Gradle build. Формат `.map` добавлен в `noCompress`, чтобы не хранить вторую ZIP-сжатую копию во время установки/копирования. Итоговый APK будет больше минимального приложения на размер векторной карты плюс другие assets, которые появятся позже. Перед добавлением DEM, спутниковых снимков и routing graph необходимо контролировать размер APK и свободное место устройства; SQLite POI база уже входит в эту версию.

## Подпись release APK

Debug APK подходит для тестирования. Для постоянных обновлений нужна своя release keystore, хранящаяся только в GitHub Actions Secrets: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`. Не помещай keystore и пароли в репозиторий, Issues или логи.


## Исправление 0.4.7 — отсутствующий bbox в PBF

CI подтверждает, что Android unit tests прошли, PBF Грузии был скачан и проверен по MD5, Mapsforge Writer 0.25.0 загрузился и прошёл настоящую тестовую запись. Полная генерация карты остановилась с `No valid bounding box found in input data`, поскольку использованный PBF не передал writer пригодную bbox из заголовка. В 0.4.7 основной writer command и smoke-test передают bbox явно. Диапазон основан на крайних точках Грузии по Geostat Yearbook 2024 с небольшим запасом.


## Version 0.5.4 — POI scan observability

The country PBF scan reports entity counts and elapsed time periodically. The runner shutdown observed in 0.5.3 was not an extractor exception; the available log does not identify who initiated shutdown.
