# Georgia Offline 🇬🇪

Полнофункциональный автономный атлас Грузии для Android. Цель проекта — карта
всей страны с английскими, русскими и грузинскими названиями, офлайн-поиском,
маршрутами для автомобиля/4×4/велосипеда/пешком, путеводителем, топографией,
планировщиком путешествий и локальными GPS-инструментами.

> **Статус starter:** создан Android-каркас с Compose-интерфейсом, локальными
> настройками языков/тем, выбором папки SD-карты и CI. Настоящий пакет карты,
> движок маршрутизации и база POI ещё не подключены. Приложение честно показывает
> этот статус и не загружает карту из интернета.

## Принятые решения

- Kotlin + Jetpack Compose.
- Один APK, содержащий все подготовленные картографические assets.
- Полное отсутствие сетевого разрешения `INTERNET` в AndroidManifest.
- Без аккаунта, рекламы, аналитики и сетевой телеметрии.
- Языки названий: en / ru / ka, каждый включается отдельно.
- Темы приложения: системная / светлая / тёмная.
- Стили карты: классический / топографический / ночной / контрастный.
- Внутреннее хранилище по умолчанию; выбор SD-карты через Android Storage Access Framework.
- Карта: Mapsforge; маршрутизация: BRouter (интеграция требует проверки в Android-проекте).

## Открыть проект

1. Установить актуальный Android Studio и Android SDK Platform 36.
2. Открыть папку `GeorgiaOffline` в Android Studio.
3. Использовать JDK 17 или JDK 21 (CI использует JDK 21).
4. Зафиксированы AGP 9.3.1, Gradle 9.6.0, Kotlin 2.4.21, Compose BOM 2026.03.00, Activity Compose 1.11.0 и Lifecycle 2.10.0.
5. Собрать `app` → `assembleDebug`.

В starter-архив не включён бинарный `gradle-wrapper.jar`. Если Gradle установлен,
в корне проекта выполните:

```bash
gradle wrapper --gradle-version 9.6.0 --distribution-type bin
```

После этого проект можно собирать через `./gradlew :app:assembleDebug`.
GitHub Actions использует закреплённый Gradle напрямую и не зависит от wrapper.

## Проверка отсутствия сети

```bash
python3 scripts/verify_offline_manifest.py
```

Проверка анализирует исходный manifest и завершится ошибкой, если обнаружит
`INTERNET` или `ACCESS_NETWORK_STATE`.

## Данные карты

Каталог `data/assets/` описывает структуру будущих встроенных данных. Бинарные
данные намеренно не включены в starter: карта должна собираться отдельным
контролируемым процессом из исходников с фиксируемой датой, checksum и лицензией.
См. `docs/DATA_SOURCES.md` и `docs/TECHNICAL_SPEC.md`.

## Документация

- [`docs/TECHNICAL_SPEC.md`](docs/TECHNICAL_SPEC.md) — полное ТЗ.
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — архитектура.
- [`docs/DATA_SOURCES.md`](docs/DATA_SOURCES.md) — источники и лицензии данных.
- [`docs/ROADMAP.md`](docs/ROADMAP.md) — этапы разработки.
- [`docs/BUILD_AND_RELEASE.md`](docs/BUILD_AND_RELEASE.md) — CI, локальная сборка и подпись APK.
- [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) — сторонние компоненты и атрибуция.

## Лицензии

Лицензия исходного кода самого приложения пока не выбрана: перед публикацией
репозитория нужно решить, будет ли он публичным/частным и какую лицензию кода
использовать. Географические данные и сторонние библиотеки в любом случае
регулируются собственными лицензиями; см. `THIRD_PARTY_NOTICES.md`.


## CI build fix

GitHub Actions uses `android-actions/setup-android@v4.0.2`. The previous v3 action attempted to install the retired Android SDK package `tools`, causing `Failed to find package 'tools'`. The workflow no longer requests that package.


## Последнее исправление CI (21 ноября 2026)

Новый журнал GitHub Actions показал, что установка SDK уже проходит, но workflow
запрашивал `platforms;android-37`, отсутствующий в стабильном каталоге SDK.
`compileSdk` также был установлен в 37. Оба значения приведены к доступному
Android SDK Platform 36; workflow устанавливает `platforms;android-36` и
`build-tools;36.0.0`. Подробности — в `docs/BUILD_ERROR_ANALYSIS.md`.


## Исправление зависимостей для Android API 36 (21 ноября 2026)

Новый CI-журнал показал, что Compose 1.12.1 и Lifecycle 2.11.0 требуют compileSdk 37.
Чтобы сохранить стабильную сборку на доступной платформе API 36, зависимости
зафиксированы на Compose BOM 2026.03.00 (Compose 1.10.5), Activity Compose 1.11.0
и Lifecycle 2.10.0. См. `docs/BUILD_ERROR_ANALYSIS.md`.


### Последнее исправление сборки

Исправлен конфликт JVM-сеттеров в `LocalSettings`: обработчики темы переименованы в `updateAppTheme` и `updateMapTheme`. Смотрите `docs/BUILD_ERROR_ANALYSIS.md`.
