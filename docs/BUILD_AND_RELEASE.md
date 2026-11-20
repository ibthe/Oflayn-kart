# Сборка и выпуск

## Автоматическая сборка на GitHub Actions

Workflow `.github/workflows/android-ci.yml` запускается при push в `main`/`master`, pull request или вручную (`workflow_dispatch`). Он использует Ubuntu, JDK 21, Android SDK Platform 36, Build Tools 36.0.0 и Gradle 9.6.0.

### Что происходит в полной CI-сборке 0.4.2

1. Проверяется исходный `AndroidManifest.xml` на сетевые и фоновые location-разрешения.
2. Запускаются unit tests.
3. Workflow получает MD5 текущего `georgia-latest.osm.pbf.md5` у Geofabrik и использует его как cache key.
4. Если map cache отсутствует, устанавливается Osmosis (версия пакета читается через `dpkg-query`; не вызывайте `osmosis --version`, так как Osmosis воспринимает это как неизвестную задачу), собирается Mapsforge Map Writer 0.25.0 и устанавливается как Osmosis plugin.
5. Загружается Geofabrik Georgia country PBF, сравнивается MD5, собирается `.map` с `preferred-languages=en,ru,ka` в режиме `type=hd`.
6. Проверяются размер, языковой список и SHA-256 карты. Кешируются `georgia.map` и `georgia-map-build.json`.
7. Gradle собирает APK, затем отдельный скрипт проверяет, что оба файла действительно включены в APK и checksum карты совпадает.
8. Проверяется итоговый merged manifest и публикуется `GeorgiaOffline-debug-apk` на 14 дней.

Первый запуск workflow может быть заметно дольше, потому что ему нужно скачать и обработать страновой PBF и собрать Map Writer. Следующие запуски используют бинарный кеш, пока Geofabrik checksum не изменится. Если источник обновился, кеш автоматически создаётся заново.

**Нужно помнить:** GitHub Actions использует интернет только в процессе сборки для получения исходных открытых данных и зависимостей. Приложение на телефоне не получает разрешение `INTERNET` и не загружает карты.

## Где скачать APK

1. Открой репозиторий на GitHub.
2. Перейди во вкладку **Actions**.
3. Открой последний зелёный запуск **Android CI**.
4. Внизу страницы найди **Artifacts** и скачай `GeorgiaOffline-debug-apk`.
5. Распакуй скачанный artifact ZIP и установи APK на Android. Для debug-сборки Android может показать стандартное предупреждение о неизвестном источнике установки.

Если Actions завершился красным, скачай полный лог конкретного workflow run и отправь ZIP с логами — не нужно вручную редактировать код.

## Локальная сборка

Требуются Android Studio, Android SDK Platform 36, Build Tools 36.0.0 и JDK 17 или новее. Локальная сборка из чистого исходного ZIP не содержит бинарный `georgia.map`, потому что карта генерируется в CI. При обновлении приложения встроенный файл сравнивается с SHA-256 из `georgia-map-build.json`; он заменяется только если данные изменились. Пользовательская карта, импортированная вручную, не перезаписывается. Для полной локальной сборки сначала выполни `scripts/install_mapsforge_writer.sh` и `scripts/build_georgia_map.sh`, затем: `gradle :app:testDebugUnitTest :app:assembleDebug`.

## Один APK и размер

Карта `.map` генерируется в `app/src/main/assets/offline/` до Gradle build. Формат `.map` добавлен в `noCompress`, чтобы не хранить вторую ZIP-сжатую копию во время установки/копирования. Итоговый APK будет больше минимального приложения на размер векторной карты плюс другие assets, которые появятся позже. Перед добавлением POI, DEM, спутниковых снимков и routing graph необходимо контролировать размер APK и свободное место устройства.

## Подпись release APK

Debug APK подходит для тестирования. Для постоянных обновлений нужна своя release keystore, хранящаяся только в GitHub Actions Secrets: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`. Не помещай keystore и пароли в репозиторий, Issues или логи.
