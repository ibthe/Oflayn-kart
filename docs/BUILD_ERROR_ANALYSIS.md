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
2. В CI явно запрашивается только `platform-tools` через action; затем отдельно устанавливаются `platforms;android-37` и `build-tools;36.0.0`.
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
