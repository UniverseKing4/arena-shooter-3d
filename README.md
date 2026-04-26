# Android App Template

Bare minimal Android app template. Builds, signs, and releases out of the box.

## Setup

1. Find/replace `com.app.template` → your package name
2. Find/replace `App Template` / `AppTemplate` / `App` → your app name (in `strings.xml`, `settings.gradle.kts`, `build.gradle.kts`, `.github/workflows/build.yml`)
3. Add your views to `activity_main.xml`
4. Add your logic to `MainActivity.kt`

## Build

```bash
./gradlew assembleRelease
```

## CI/CD

Push to `main` → GitHub Actions auto-increments version, builds signed APK, creates GitHub release.

## Stack

- Kotlin, Material3 DayNight, ViewBinding
- core-ktx, appcompat, material, constraintlayout, activity-ktx
- Min SDK 24 / Target SDK 34
- Gradle 8.4 / AGP 8.3.0

This README is to be updated.