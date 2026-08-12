# Kandroid agent context

This document is a concise technical handoff for coding agents. Read `README.md` first for the product overview, local build command, privacy expectations, and user-facing behavior.

## Public-repository rules

- Do not add real Kanboard server addresses, usernames, API tokens, task data, screenshots, or test credentials to the repository, issues, logs, fixtures, or documentation.
- Do not use a real account for automated tests. Protocol tests must use MockWebServer or equivalent local fixtures.
- Treat Git history as public. If sensitive material is accidentally committed, remove it from every reachable revision before pushing; deleting the current file alone is insufficient.
- Kandroid is an independent client. Do not imply that it is endorsed by, affiliated with, or an official application of Kanboard.
- Retain the GPL-3.0 license unless the project owner explicitly changes it.

## Architecture

- `KandroidApplication.kt` is the manual dependency container. It owns the Room database, repository, and credential store.
- `KandroidViewModel.kt` owns application UI state and actions. UI code should call it rather than the API directly.
- `data/KandroidDao.kt` contains the Room schema, queries, and transactional cache replacement.
- `data/KanboardRepository.kt` coordinates cache and network operations. Kanboard is authoritative; updates, moves, status changes, and archiving are optimistic and restore the previous cache entry when the server rejects them.
- `network/KanboardApi.kt` is the typed OkHttp JSON-RPC client. Keep all JSON-RPC calls here.
- `data/Models.kt` contains Room entities, API DTOs, mappings, task drafts, and flexible scalar serializers.
- `security/CredentialStore.kt` encrypts the selected server URL, username, and token with an Android Keystore AES-GCM key.
- `ui/KandroidApp.kt` contains the Compose application screens and interactions.
- `widget/` contains the Glance app widget, configuration activity, background refresh worker, and widget snapshot state.

There is intentionally no dependency-injection framework or navigation library. Application state is held in `AppUiState` and screens/dialogs are composed directly.

## Data and network behavior

- Room is the UI source of truth after setup. Refreshes replace cached board columns and task lists transactionally.
- Cached data remains readable while offline; offline changes are not queued.
- Credentials are local only. Clearing app data or uninstalling the app removes the Android Keystore key and stored credentials.
- Logging API response bodies is prohibited: they can contain credentials, server details, and private task data.
- The server URL may be entered as an HTTPS site root or full `jsonrpc.php` endpoint. The API normalizes either form.
- Cleartext traffic is disabled. Keep HTTPS enforcement in production; tests may use the explicit `allowInsecureForTests` API constructor argument with MockWebServer only.

## Kanboard compatibility

Kanboard installations can return IDs, positions, status values, and timestamps as either JSON strings or JSON numbers. Preserve `FlexibleLongSerializer` and `FlexibleStringSerializer`, and use them for new numeric-looking API fields where needed.

A JSON-RPC `result: false` means the procedure was rejected. `KanboardApi.call` converts this into a procedure-specific server error rather than decoding it as a DTO.

Due dates are sent as ISO date strings. Returned epoch-second dates are converted to UTC `YYYY-MM-DD` values. Consider timezone behavior carefully before changing this mapping.

## Database changes

- Keep exported Room schemas in `app/schemas` up to date.
- Bump the database version and provide a migration whenever changing an entity or schema.
- Projects are upserted, not globally replaced. Revoked server-side project access can therefore leave an old cached project; account for this deliberately if synchronization behavior changes.

## Tests and verification

The JVM suite uses MockWebServer and Robolectric/Glance tests. It covers JSON-RPC envelopes and failures, flexible response scalar decoding, task DTO mapping, movement parameters, and widget rendering/state behavior.

For Kotlin, API, model, repository, or widget changes, run:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

The project requires JDK 17, Android SDK 37, and Android API 26 or newer. If the machine-level `JAVA_HOME` is not configured, select Android Studio's bundled JDK and set `ANDROID_HOME` for the current shell instead of adding local machine paths to tracked files.

Run emulator or live-server checks only when explicitly authorized. Never infer success solely from a toast or snackbar; verify the resulting visible board, task, or widget state.

## Release and metadata

- Application ID: `com.kandroid.app`.
- Current SDK levels: compile/target 37, minimum 26.
- Store screenshots belong in `fastlane/metadata/android/en-US/images/phoneScreenshots/`.
- Do not commit release signing keys, keystore passwords, or local signing configuration. `.gitignore` already excludes standard signing files.
- GitHub Actions runs unit tests, Android lint, and an unsigned release build for pull requests and `main`.
- Annotated `v*` tags target the `release` environment to build and publish a signed GitHub APK. F-Droid builds remain unsigned by this repository and are signed independently.
- Release signing material must remain in GitHub environment secrets and must never be printed. See `docs/RELEASING.md` for the release contract.
