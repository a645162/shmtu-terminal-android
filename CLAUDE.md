# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

- `./gradlew assembleDebug` — build debug APK
- `./gradlew assembleRelease` — build release APK
- `./gradlew :app:assembleDebug` — build app module only
- `./gradlew test` — run unit tests (currently placeholder only)
- `./gradlew :cas_demo:run` — run the CAS CLI demo (tests cas_lib interactively)

No lint/detekt/ktlint configuration exists. No CI/CD pipelines are set up.

## Project Structure

Four modules:

- **`:app`** — Android application (Jetpack Compose + Hilt + Room). Package: `cn.edu.shmtu.terminal.android`
- **`:cas_lib`** — Pure Kotlin JVM library for CAS authentication, bill parsing, hot water parsing. Package: `cn.edu.shmtu.cas`
- **`:shmtu_ocr`** — Android native library with NCNN + OpenCV for on-device CAPTCHA OCR. C++ via CMake/NDK
- **`:cas_demo`** / **`:demo`** — CLI and Android demo apps for testing `cas_lib` and `shmtu_ocr` respectively

Native dependencies (NCNN, OpenCV Mobile) are downloaded via `scripts/install_lib.py` rather than included in the repo.

## Architecture

The app follows **MVVM + Clean Architecture** with Hilt DI:

- **UI**: Compose screens → Hilt ViewModels → Use Cases / Repositories
- **Domain**: Repository interfaces (`domain/repository/`), models (`domain/model/`), use cases (`domain/usecase/`)
- **Data**: Repository implementations (`data/repository/`), Room DB, remote adapters, DataStores
- **DI**: `DatabaseModule`, `RepositoryModule`, `DataStoreModule` bind everything via Hilt

Navigation: single `NavHost` in `AppNavigation.kt` with Material3 adaptive `NavigationSuiteScaffold`. Five bottom destinations (Home, Bill, Features, Account, Settings) plus nested detail routes.

Key architectural decisions:
- **Per-account bill databases**: `BillDatabaseManager` dynamically creates separate Room databases per account/identity (not one shared DB), stored in a `ConcurrentHashMap`
- **Identity/Account hierarchy**: One Identity (person) can have multiple Accounts (e.g., undergrad + grad), each with separate credentials and bill databases
- **Dual OCR**: Local NCNN inference (shmtu_ocr) or remote TCP server, configurable via `SettingsDataStore`
- **Secure storage**: `EncryptedSharedPreferences` (AES256_GCM) for passwords, cookies, login URLs via `SecureStorage`

## CAS Authentication Flow

All SHMTU systems (E-pay bills, hot water) authenticate through CAS:

```
Access business system → 302 to CAS login → POST credentials+execution+captcha → 302 back with ticket → session established
```

**Critical rules**:
- `execution` token is one-time — must be fetched immediately before login submission, not when the user sees the captcha
- Captcha download and login POST must use the same `JSESSIONID` cookie session
- OkHttpClient uses `followRedirects(false)` — handle 302 redirect chains manually
- Two distinct auth flows:
  - **E-pay**: `EpayAuth` → `testLoginStatus` → `getBill` detects expired session → CAS login on 302
  - **Hot water**: `WechatAuth` → `getHotWater` → 302 → get `wengine_new_ticket` → get execution → CAS login → redirect

## Hot Water System

- URL: `http://hqzx.shmtu.edu.cn/cellphone/getHotWater`
- Auth: `WechatAuth` (WeChat ticket flow, not standard CAS)
- Parsing: `HotWaterParser` extracts `(temperature, water level %, building number)` from HTML
- Followed buildings are persisted to Room; hot water data itself is real-time only, not persisted

## CAPTCHA/OCR

- CAS captchas are math expressions (e.g., `3+5=8`) — OCR returns the expression string, extract the answer after `=`
- Local: NCNN model via `shmtu_ocr` JNI (`predict_validate_code(Bitmap)`)
- Remote: TCP socket to configurable server (address/port in `SettingsDataStore`)
- `Captcha.kt` in `cas_lib` handles both captcha download and remote OCR communication

## Data Layer Conventions

- **Bills**: Incremental sync, dedup by `transactionNo` with `OnConflictStrategy.IGNORE`
- **Hot water**: Real-time queries only; only the followed-buildings list is persisted
- **Account management**: Stores credentials only; login is triggered on-demand during data refresh
