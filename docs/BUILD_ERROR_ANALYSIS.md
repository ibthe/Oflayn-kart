# Анализ ошибки GitHub Actions

## Симптом из журнала

Сборка остановилась на шаге `Set up Android SDK`, до запуска Gradle:

```text
[command] .../sdkmanager tools
Warning: Failed to find package 'tools'
Error: ... sdkmanager failed with exit code 1
```

## Причина

Старый `android-actions/setup-android@v3` пытался установить пакет SDK с именем `tools`. Google удалил этот устаревший пакет из репозитория Android SDK. Это известная проблема `android-actions/setup-android` (issue #537). Поэтому `sdkmanager` завершался с кодом 1, и тесты/компиляция приложения фактически не начинались.

## Исправления в этом пакете

1. Обновлён `android-actions/setup-android` до `v4.0.2`, в котором больше не запрашивается удалённый пакет `tools`.
2. В CI явно запрашивается только `platform-tools` через action; затем отдельно устанавливаются `platforms;android-36` и `build-tools;36.0.0`.
3. Версия Android Gradle Plugin снижена с `9.4.0` до `9.3.1`: Kotlin Gradle Plugin `2.4.21` заявляет полную совместимость с AGP до `9.3.1`.
4. Версия Gradle `9.6.0` оставлена: она удовлетворяет минимальному требованию AGP 9.3 и находится в диапазоне полной совместимости Kotlin Gradle Plugin 2.4.21.
5. В `gradle.properties` установлен временный opt-out от встроенного Kotlin AGP 9, поскольку проект использует внешний Kotlin Gradle Plugin и Compose compiler plugin одной версии (`2.4.21`).
6. В CI обновлены action versions, а сборка выводит подробный stacktrace, если последующие этапы обнаружат другие проблемы.

## Границы проверки

Предоставленный журнал доказывает только ошибку установки SDK. Он не содержит результата запуска Gradle и не подтверждает/не опровергает ошибки исходного Kotlin-кода. Исправленный workflow должен запуститься в GitHub Actions; окончательное подтверждение сборки — зелёный job и опубликованный артефакт APK.


---

## Вторая ошибка из журнала `logs_121329338139.zip` (21 ноября 2026)

### Симптом из журнала

Предыдущая проблема с устаревшим SDK-пакетом `tools` устранена: action успешно
принимает лицензии и устанавливает `platform-tools`. Новый запуск остановился
на следующем шаге:

```text
sdkmanager "platforms;android-37" "build-tools;36.0.0"
Warning: Failed to find package 'platforms;android-37'
Process completed with exit code 1
```

### Причина

В стабильном каталоге Android SDK, доступном runner в этом запуске, пакет
`platforms;android-37` отсутствует. При этом `app/build.gradle.kts` запрашивал
`compileSdk = 37`, поэтому CI не мог подготовить требуемую платформу. Это произошло
до запуска Gradle: компиляция Kotlin и тесты в этом запуске ещё не выполнялись.

### Исправления в текущем полном пакете

1. `app/build.gradle.kts`: `compileSdk = 36`.
2. `.github/workflows/android-ci.yml`: установка `platforms;android-36` вместо
   `platforms;android-37`; build-tools остаётся `36.0.0`.
3. Документация README и сборки синхронизирована с API 36.
4. Версии AGP `9.3.1`, Kotlin Gradle Plugin `2.4.21` и Gradle `9.6.0` сохранены:
   опубликованная таблица совместимости Kotlin указывает для KGP 2.4.21 поддержку
   AGP вплоть до 9.3.1 и Gradle до 9.7.0; Android указывает Gradle 9.5.0 как
   минимальный для AGP 9.3.

### Что ещё не подтверждено

Этот журнал заканчивается до запуска Gradle. Поэтому текущая правка устраняет
конкретную ошибку отсутствующей платформы, но успешную компиляцию APK можно
подтвердить только новым зелёным запуском GitHub Actions. Если следующий лог
покажет ошибку Gradle/Kotlin/Compose, её нужно будет исправлять уже по этому логу.


---

## Третья ошибка из журнала `logs_121331828445.zip` (21 ноября 2026)

### Симптом

Android SDK Platform 36 установилась, Gradle 9.6.0 запустился, но задача
`:app:checkDebugAarMetadata` остановила сборку с 12 несовместимостями AAR.
Журнал показал, что Compose 1.12.1 и Lifecycle 2.11.0 требуют `compileSdk 37`,
тогда как приложение собрано с `compileSdk 36`.

Основные строки:

```text
Dependency 'androidx.compose.animation:animation-core-android:1.12.1'
requires ... compile against version 37 or later
:app is currently compiled against android-36

Dependency 'androidx.lifecycle:lifecycle-runtime-compose-android:2.11.0'
requires ... compile against version 37 or later
:app is currently compiled against android-36

12 issues were found when checking AAR metadata
```

### Причина

В предыдущем пакете был выбран Compose BOM `2026.09.00`, который подтягивает
Compose `1.12.1`, а также Activity Compose `1.13.0` и Lifecycle `2.11.0`.
Начиная с Compose 1.12.0, Android официально требует `compileSdk 37`.
Lifecycle 2.11.0 также получил обновление Compose compileSdk до API 37.
При этом доступный CI SDK настроен на Android Platform 36.

### Исправление в этом полном пакете

1. Compose BOM понижен до `2026.03.00`, который управляет Compose 1.10.5.
2. Activity Compose закреплён на версии `1.11.0`, выпущенной для API 36.
3. Lifecycle `runtime-ktx` и `runtime-compose` закреплены на `2.10.0`, до перехода
   следующей версии Lifecycle на API 37.
4. `compileSdk`, `targetSdk` и CI SDK остаются на Android API 36.
5. Остальные настройки приложения и отсутствие `INTERNET`/`ACCESS_NETWORK_STATE`
   не изменялись.

### Что подтверждено и что нет

Точное несоответствие из журнала устранено выбором библиотек предыдущего поколения,
не требующих API 37. Архив и пути файлов проверяются автоматически. В этом
окружении нет установленного Gradle и Android SDK, поэтому полную компиляцию APK
здесь выполнить и подтвердить нельзя. Окончательный результат проверяется новым
запуском GitHub Actions.


## Ошибка 4 — конфликт JVM-сеттеров в LocalSettings (2026-11-21)

**Сообщение компилятора:** `Platform declaration clash` для `setAppTheme(String)` и `setMapTheme(String)`.

**Причина:** Kotlin генерирует JVM-сеттеры для свойств `appTheme` и `mapTheme`. Одновременно класс объявлял функции с теми же JVM-именами и сигнатурами.

**Исправление:** функции сохранения настроек переименованы в `updateAppTheme(String)` и `updateMapTheme(String)`. Ссылки на обработчики в `GeorgiaOfflineApp.kt` обновлены соответственно. Имена свойств и ключи SharedPreferences сохранены, поэтому поведение и сохранённые настройки пользователя не меняются.


## Ошибка 5 — проверка каталога режимов движения (2026-11-21)

**Симптом из CI:** Kotlin и unit-тесты скомпилировались, проверка Android Manifest прошла, но задача `:app:testDebugUnitTest` завершилась неуспешно:

```text
FeatureCatalogTest > containsAllFourTravelModes FAILED
java.lang.AssertionError at FeatureCatalogTest.kt:17
2 tests completed, 1 failed
```

**Причина:** тест проверяет наличие `4×4` в `FeatureGroup.features` модуля C, однако в каталоге это значение находилось только в поле `description`, а не в списке `features`. Поэтому тест обнаруживал реальное несоответствие каталога его проверке.

**Исправление:** в модуль C добавлены отдельные пункты про автомобильную навигацию и профиль внедорожника 4×4. Проверка `containsAllFourTravelModes` расширена: теперь она явно проверяет все четыре выбранных режима — пешком, велосипед, автомобиль и 4×4.

**Что уже прошло в предыдущем CI-запуске:** установка Android SDK, проверка манифеста без сетевых разрешений, компиляция основного Kotlin-кода и компиляция тестового Kotlin-кода. Падение произошло только при выполнении одного unit-теста.

**Что ещё не подтверждено:** успешное прохождение тестов и сборка APK. Это будет видно в следующем запуске GitHub Actions.


## Обновление — GPS-инструменты и проверка merged manifest

В модуль G добавлены GPS-координаты (WGS84 и DMS), точность, GPS-высота, скорость, направление движения, электронный компас через `TYPE_ROTATION_VECTOR`, копирование координат и сохранение локальной метки автомобиля. Геолокация стартует только после явного нажатия пользователя и остановки экрана; фоновое местоположение не запрашивается.

Добавлены unit-тесты `GeoFormattingTest` для координат, DMS, нормализации азимута и сторон света. Чистая функция `GeoFormatting.kt` отдельно скомпилирована установленным Kotlin-компилятором и прошла smoke-тесты.

CI теперь дополнительно проверяет merged AndroidManifest после сборки, чтобы обнаружить сетевые или фоновые геолокационные разрешения, которые могут быть добавлены зависимостью. В этом окружении отсутствуют Gradle и Android SDK, поэтому полную компиляцию Android-приложения не подтверждаю.


---

## Шестая ошибка из журнала `logs_121364396961.zip` (21 ноября 2026)

### Что прошло успешно

- Android SDK Platform 36 и Build Tools 36.0.0 установились.
- Проверка исходного Android Manifest прошла.
- Unit-тесты прошли: `BUILD SUCCESSFUL` для `:app:testDebugUnitTest`.
- Geofabrik checksum metadata загрузилась успешно.
- Cache miss штатно привёл workflow к шагу подготовки Osmosis.

### Симптом

Job остановился в шаге `Install Osmosis` после установки системного пакета:

```text
Osmosis Version 0.49.2
Preparing pipeline.
SEVERE: Execution aborted.
OsmosisRuntimeException: Task type version doesn't exist.
```

Команда, вызвавшая ошибку:

```bash
osmosis --version
```

### Причина

Osmosis — CLI для запуска задач-потоков (pipeline). Аргументы CLI интерпретируются как имена задач; команда `--version` была воспринята как задача `version`, которой нет. Поэтому сама установка пакета `osmosis` прошла успешно, но неверная проверочная команда завершила шаг с кодом 1. Документация Mapsforge рекомендует проверять регистрацию Map Writer plugin командой `osmosis --help`, что и остаётся в `scripts/install_mapsforge_writer.sh`.

### Исправление в версии 0.4.2

1. Удалён вызов `osmosis --version` из GitHub Actions.
2. Версия установленного Debian-пакета читается безопасно:
   `dpkg-query -W -f='${Version}' osmosis`.
3. Проверка регистрации Mapsforge plugin остаётся в следующем шаге через документированную команду `osmosis --help`.
4. Android `versionName` обновлён до `0.4.2`, `versionCode` — до `6`.

### Что ещё не подтверждено

Этот журнал не дошёл до сборки Mapsforge Map Writer и создания `georgia.map`. Следующий запуск должен пройти установку Osmosis и продолжить на plugin build; возможные ошибки последующих этапов можно определить только по новому CI log.


---

## CI 0.4.4 — ошибки резервного PBF URL и полной сборки Mapsforge (21 ноября 2026)

### Что прошло успешно

- Android SDK Platform 36 и Build Tools 36.0.0 установились.
- Исходный Android manifest прошёл проверку.
- `:app:testDebugUnitTest` завершился `BUILD SUCCESSFUL`.
- Шаги подготовки карты дошли до Map Writer и загрузки OSM PBF.

### Ошибка 1: неверное имя файла контрольной суммы зеркала

В шаге генерации карты Geofabrik не принимал HTTPS-соединение (`curl: (7)`), после чего сценарий переходил на зеркало OpenStreetMap France. Там для PBF `georgia-latest.osm.pbf` запрашивался `georgia-latest.osm.pbf.md5`, но индекс зеркала публикует стабильную пару `georgia.osm.pbf` и `georgia.osm.pbf.md5`. Поэтому сервер возвращал HTTP 404.

Исправление: резервный источник теперь задан как `https://download.openstreetmap.fr/extracts/asia/georgia.osm.pbf`. Скрипт формирует checksum URL добавлением `.md5`, то есть получает существующий `https://download.openstreetmap.fr/extracts/asia/georgia.osm.pbf.md5`. Проверка metadata карты также принимает именно этот источник.

### Ошибка 2: сборка полного Mapsforge multi-project

Предыдущий `install_mapsforge_writer.sh` клонировал полный репозиторий Mapsforge 0.25.0 и запускал `:mapsforge-map-writer:fatJar`. Gradle при конфигурации корневого multi-project дополнительно настраивал Android/sample-модули и пытался разрешить десятки несвязанных старых зависимостей. В логе видны ошибки разрешения `kotlin-stdlib`, `asm`, `jdom2`, `commons-codec`, Jetifier и Android Gradle Plugin.

Исправление: сборка Writer перенесена в отдельный небольшой Gradle-проект с `java` plugin и единственной прямой зависимостью `org.mapsforge:mapsforge-map-writer:0.25.0` из Maven Central. Из `runtimeClasspath` собирается fat JAR для Osmosis; сохраняется `osmosis-plugins.conf`, а затем `osmosis --help` проверяет регистрацию `--mapfile-writer`. Этот путь использует опубликованный writer artifact и его фактические runtime-зависимости, не конфигурируя Android/sample-проекты полного upstream репозитория.

### Дополнительная защита

- В workflow добавлен `scripts/verify_map_build_config.py`: проверяет точный резервный PBF URL, соответствующий checksum URL, Maven-based isolated writer build и отсутствие скрытых `continue-on-error`.
- Ошибки установки Osmosis или Writer больше не игнорируются; следующий шаг генерации карты не запускается после неуспешного prerequisite.
- В `verify_bundled_map.py` обновлён список допустимых source URL.
- `versionName` остаётся `0.4.4`, `versionCode` — `8`.

### Что пока не подтверждено

Новый workflow ещё не запускался с этими изменениями. Статические проверки проекта и скриптов можно выполнить локально в этом пакете, но реальное разрешение Maven-зависимостей, регистрацию Osmosis plugin, создание `georgia.map` и APK должен подтвердить GitHub Actions. Нельзя считать APK собранным до зелёного CI-run и появления artifact.


## Ошибка CI 0.4.4 — Mapsforge plugin JAR не попадал в classpath Debian Osmosis

### Симптом из журнала `logs_121374043594.zip` (21 ноября 2026)

Установка Osmosis прошла успешно, отдельный fat JAR Mapsforge Map Writer также собрался:

```text
BUILD SUCCESSFUL in 9s
ERROR: Osmosis did not register the Mapsforge --mapfile-writer plugin.
```

После проверки `osmosis-plugins.conf` скрипт запускал `osmosis --help`, но plugin task не обнаруживался. До скачивания PBF и генерации карты workflow не дошёл.

### Причина

В предыдущей версии JAR копировался только в пользовательский JPF-каталог `~/.openstreetmap/osmosis/plugins`. Но Ubuntu/Debian пакет Osmosis запускается через `/etc/osmosis/plexus.conf` и системный Plexus classpath. Mapsforge Map Writer поставляет loader через classpath-ресурс `osmosis-plugins.conf`; наличие JAR в произвольном пользовательском plugin-каталоге не гарантирует, что этот ресурс будет доступен системному classloader.

Документация Osmosis описывает classpath plugin loaders и опцию `-plugin`, а пакетный Debian launcher явно использует системный classworlds/Plexus конфиг. Документация Mapsforge подтверждает имя loader-класса и формат `osmosis-plugins.conf`.

### Исправление в 0.4.5

1. Fat JAR устанавливается в `/usr/share/osmosis/mapsforge-map-writer-0.25.0-jar-with-dependencies.jar`.
2. Скрипт добавляет `load /usr/share/osmosis/mapsforge-map-writer-0.25.0-jar-with-dependencies.jar` в `/etc/osmosis/plexus.conf` (без дублирования при повторном запуске).
3. Старый пользовательский JPF-копируемый JAR удаляется, чтобы не создавать ложное впечатление успешной регистрации.
4. Удалена ненадёжная проверка через `osmosis --help`. Вместо неё выполняется реальный smoke-test: Osmosis читает минимальный OSM XML fixture и пишет временный Mapsforge `.map` файл.
5. Добавлен fixture `scripts/fixtures/mapsforge-writer-smoke.osm`; preflight проверяет наличие fixture и конфигурации системного classpath.
6. Если реальная тестовая запись `.map` не проходит, CI прекращает работу до скачивания полной выгрузки Грузии.

### Статус проверки

В текущем рабочем окружении нет установленного Osmosis/Ubuntu package, поэтому настоящий plugin load против Debian-пакета здесь не запускался. Installer проверяется синтаксически и на mock-интеграции, а реальный plugin smoke-test будет выполнен первым делом в следующем GitHub Actions run. Не считать карту или APK сгенерированными до зелёной CI-сборки и загруженного APK artifact.


## Ошибка CI 0.4.5 — отсутствовал timestamp в smoke-test OSM XML

### Симптом из журнала `logs_121379795216.zip` (21 ноября 2026)

```text
mapfile-writer version: mapsforge-map-writer-0.25.0
INFO: start reading data...
OsmosisRuntimeException: The entity timestamp attribute is missing.
```

### Что это подтверждает

Mapsforge plugin уже был найден и загружен: конструктор `MapFileWriterTask` выполнился и writer начал обработку входных данных. Следовательно, установка JAR и регистрация в `/etc/osmosis/plexus.conf` сработали. Ошибка была позже — при чтении собственного тестового OSM XML.

### Причина

Элементы `<node>` и `<way>` в `scripts/fixtures/mapsforge-writer-smoke.osm` имели `id`/`version`, но не имели обязательного `timestamp`. Osmosis XML reader требует timestamp для каждой OSM-сущности.

### Исправление в 0.4.6

1. Для каждого `node` и `way` добавлены UTC timestamps в ISO-8601 формате.
2. Добавлены стандартные `changeset`, `uid` и `user`.
3. `verify_map_build_config.py` теперь проверяет наличие обязательных атрибутов и формат timestamp у каждой сущности до запуска Osmosis.
4. Повышен Android `versionCode` до 10, `versionName` — 0.4.6.

Фактический smoke-test на Ubuntu/Osmosis должен быть повторно выполнен GitHub Actions. До его успеха нельзя утверждать, что карта или APK с картой готовы.


## Ошибка 0.4.6 — отсутствующая граница (bbox) в заголовке PBF (2026-11-21)

### Что в этом CI run прошло успешно

- Android unit tests: `BUILD SUCCESSFUL` для `:app:testDebugUnitTest`.
- OSM France PBF Грузии скачан полностью (109.4 MB) и проверен по MD5.
- Mapsforge Map Writer 0.25.0 загружен Osmosis.
- Реальный writer smoke-test завершился успешно и создал тестовый `.map` размером 325 байт.

### Симптом

При обработке полного PBF Mapsforge начал чтение и завершился:

```text
SEVERE: No valid bounding box found in input data.
Please provide valid bounding box via command line parameter 'bbox=minLat,minLon,maxLat,maxLon'.
IllegalStateException: tile based data store not initialized, missing bounding box information in input data
```

### Причина

Выгрузка PBF, полученная из резервного зеркала, не предоставила Mapsforge пригодную bounding box из header. Writer требует bbox либо из данных, либо как явный параметр. Предыдущая команда передавала `type`, стартовую позицию и языки, но не передавала `bbox`.

### Исправление в 0.4.7

1. В `scripts/build_georgia_map.sh` добавлен `GEORGIA_BBOX`, по умолчанию `41.00,39.95,43.65,46.80`.
2. В команду `--mapfile-writer` передаётся `bbox="$GEORGIA_BBOX"`.
3. Smoke-test в `scripts/install_mapsforge_writer.sh` теперь тоже передаёт явный небольшой bbox вокруг тестовых объектов.
4. `scripts/verify_map_build_config.py` проверяет наличие правильного bbox в полном map build и smoke-test.
5. В `georgia-map-build.json` записывается bounding box и порядок координат.
6. Диапазон основан на Geostat Yearbook 2024, стр. 11, крайние точки Грузии: юг 41°02′58″ N, запад 40°00′30″ E, север 43°35′25″ N, восток 46°44′32″ E; добавлен небольшой запас.

### Что ещё не подтверждено

Текущий лог подтверждает предыдущие этапы, но не новую версию. После обновления нужно снова выполнить GitHub Actions. Полная генерация карты Грузии и итоговый APK будут считаться подтверждёнными только после успешного завершения этого job.


## Версия 0.5.0 — первый локальный POI справочник

Добавлен pipeline `build_poi_database.py`: PyOsmium читает тот же проверенный PBF, используя `locations=True` и disk-backed `dense_mmap_array`, извлекает категorizованные узлы и замкнутые ways и сохраняет SQLite базу. `test_poi_extractor_smoke.py` на небольшом OSM XML fixture проверяет обе геометрии, имена на трёх письменностях, координаты и SQLite integrity. В CI добавлены проверка POI SHA-256, размер/row count и проверка наличия SQLite asset внутри APK. В приложении `PoiRepository` копирует и проверяет базу по SHA-256; экран «Места» выполняет локальный поиск и показывает все включённые name variants, имеющиеся в данных. Ограничение: relation-only POI пока не извлекаются; это первая практическая версия поискового справочника, а не полный реестр организаций.


## 0.5.3 — GitHub Actions job was canceled during country-map generation

The uploaded Actions log shows that Android unit tests passed, PyOsmium was
installed, the Mapsforge Map Writer plugin built successfully, and Osmosis
completed a real writer smoke test. The 98.09 MB Geofabrik PBF was downloaded
and checksum-verified. The last recorded message was `Using verified OSM
source: geofabrik ...`; the runner then reported `received a shutdown signal`
and `The operation was canceled`. There was no Python, Osmosis, or Mapsforge
exception recorded after the download. This is a canceled workflow run, not a
confirmed map-writer failure.

The previous workflow used `concurrency.cancel-in-progress: true`, which can
cancel an active run when another run for the same branch starts. Version
0.5.3 changes this to `false`, adds a 180-minute job timeout, separates PBF
download/verification from map generation, and saves the verified PBF in a
dedicated cache immediately. The map builder now consumes only that
checksum-verified cached PBF. A new preflight test checks valid cache reuse and
rejects a corrupted cache before network fallback.

This prevents the workflow's own concurrency policy from terminating an
active map build and makes an interrupted build cheaper to resume. It does not
prevent manual cancellation or an external GitHub-hosted runner shutdown.


## 0.5.4 — runner stopped during POI extraction

The 0.5.3 log shows that the verified PBF was available and the PyOsmium extraction started. About 55 seconds later the runner reported `The runner has received a shutdown signal` and `The operation was canceled`; there was no Python traceback or explicit extraction exception. The log does not identify whether a user cancellation or hosted-runner shutdown caused it. Version 0.5.4 adds entity-count/time heartbeats and a final summary to make POI processing visible in CI logs.


---

## Четвёртая проблема из журнала `logs_121411641907.zip` (21 ноября 2026)

### Симптом

Все проверки, юнит-тесты, восстановление PBF из кэша и сборка плагина Mapsforge прошли успешно.
Шаг `Generate multilingual offline map of Georgia` оборвался через ~50 секунд во время POI-извлечения:

```text
[poi] OSM scan progress: entities=2,500,000, nodes=2,500,000, ways=0, POIs=13,670, elapsed=39.8s
##[error]The runner has received a shutdown signal...
##[error]The operation was canceled.
```

Скорость сканирования перед обрывом упала (250 тыс. узлов за 2,5 с, затем за ~11 с), после чего runner был остановлен.

### Вероятная причина

PyOsmium использовал индекс `dense_mmap_array`. Он резервирует ~8 байт на каждый возможный ID узла
(ID OSM доходят до ~1,3·10^10), а при разреженных ID каждый узел затрагивает отдельную 4 КБ страницу.
Расход памяти/диска быстро растёт, и runner останавливается. Это вывод по симптомам (замедление и
внезапная остановка без Python-traceback), а не прямое подтверждение. В пользу этой версии говорит то, что
обрыв повторился в логах 0.5.3 и 0.5.4 примерно на одной и той же секунде, чего не бывает при случайной ручной отмене.

### Исправления

1. `scripts/build_poi_database.py`: индекс заменён на `sparse_file_array` (файл в `RUNNER_TEMP`, ~16 байт
   на реальный узел, удаляется после скана).
2. Узлы и ways без тегов пропускаются до построения словаря тегов — это ускоряет скан, так как большинство
   узлов OSM не имеет тегов.
3. `scripts/verify_map_build_config.py` обновлён под новый индекс (иначе CI падал бы на проверке конфигурации).
4. `android-ci.yml`: упрощено условие шага `Restore verified Georgia OSM PBF`.

### Не подтверждено

Реальный PBF Грузии в песочнице недоступен, поэтому скан страны не запускался: проверены только
скриптовые тесты. Подтверждением будет зелёный запуск в GitHub Actions.

### Дополнительные правки кода (21 ноября 2026)

- Устаревший `LocalClipboardManager` в `GeorgiaOfflineApp.kt` заменён на уже используемый в `ToolsScreen.kt`
  системный `ClipboardManager` (`copyToClipboard`, без suspend); координаты копируются через
  `GeoFormatting.decimalCoordinates` в том же формате, что и в экране «Инструменты».
- `app/proguard-rules.pro`: добавлены keep/dontwarn-правила для Mapsforge, так как release-сборка включает R8.
  Release в CI не собирается, правило не проверено компиляцией.
