# Сборка и выпуск

## GitHub Actions

Workflow `.github/workflows/android-ci.yml` запускает unit tests и собирает
`app/build/outputs/apk/debug/` на каждом push/PR. APK загружается как артефакт
GitHub Actions на 14 дней. CI использует Android SDK Platform 36, Build Tools 36.0.0, AGP 9.3.1, Gradle 9.6.0, Kotlin 2.4.21, Compose BOM 2026.03.00, Activity Compose 1.11.0, Lifecycle 2.10.0 и JDK 21.

## Локальная сборка

Требуются Android Studio с поддержкой AGP 9.3.1, Android SDK Platform 36,
Build Tools 36.0.0 и JDK 17 или новее. В `gradle.properties` временно отключён встроенный Kotlin AGP 9, чтобы использовать внешний Kotlin Gradle Plugin 2.4.21 и Compose compiler той же версии. Если в окружении нет Gradle Wrapper,
установить Gradle 9.6.0 и выполнить:

```bash
./scripts/bootstrap-wrapper.sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

## Один APK

Все релизные файлы данных помещаются в `app/src/main/assets/offline/` до
выполнения Gradle-сборки. Папка не должна содержать тестовые или неполные данные.
`data-manifest.json` должен соответствовать реальному содержимому и контрольным
суммам SHA-256. Большие файлы `.map`, `.rd5`, `.dem`, `.mbtiles`, `.sqlite` и `.pbf`
настраиваются как `noCompress`.

## Подпись релиза

Debug APK подходит для тестирования, но CI debug-ключ не является постоянным
ключом для публикации обновлений. Для стабильных установок нужно создать
собственный release keystore и хранить его только в GitHub Actions Secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Не помещать keystore и пароли в репозиторий, Issues или логи. После этого добавить
release signing config в отдельный workflow и публиковать подписанный APK в GitHub
Releases.
