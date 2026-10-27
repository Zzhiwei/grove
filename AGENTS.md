# AGENTS.md

This file provides guidance to AI coding agents (Claude Code, etc.) when working with code in this repository.

Grove is a Pomodoro / stopwatch app. Repo layout:

- `macos/` — macOS menu-bar-only app (SwiftUI `MenuBarExtra`, macOS 13+). Built with SwiftPM and the Xcode Command Line Tools — there is no `.xcodeproj`.
- `android/` — Android port, sideloaded on the author's phone (Kotlin, Jetpack Compose, Gradle; minSdk 36 = Android 16). Decisions, milestone status, and emulator recipes: `docs/android-plan.md`.
- Root — files shared across platforms: `supabase/` (backend schema), `shared/fixtures/` (JSON test cases both test suites run — change a shared rule on both platforms and add a case here), `docs/`, `README.md`, this file, `.claude/skills/`. Keep platform-specific code out of the root.

## Commands (macOS, run from `macos/`)

```sh
swift build                                   # debug build
swift test                                    # all tests (swift-testing, not XCTest)
swift test --filter totalsGroupsFiltersAndSorts   # single test
./build.sh                                    # release build → build/Grove.app (ad-hoc signed)
open build/Grove.app                          # run; quit via the Quit button in the dropdown
```

`swift run Grove` also works, but the `LSUIElement` (no Dock icon) setting only applies inside the `.app` bundle — `build.sh` writes the `Info.plist`.

No linter is configured.

## Commands (Android, run from `android/`)

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"   # Android Studio's bundled JDK
./gradlew test                 # JVM unit tests (JUnit 4), incl. shared fixtures
./gradlew assembleDebug        # → app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug         # install on running emulator / connected phone
```

## Architecture (macOS; paths relative to `macos/`)

- `Sources/GroveCore` — pure, UI-free logic: `Session`, `StatRange` (today / 7 days / past calendar month), `totals()` aggregation, duration formatting, sync merge rules and Supabase row/date coding. Everything testable lives here; `Tests/GroveCoreTests` only tests this target.
- `Sources/Grove/Model.swift` — single `@MainActor ObservableObject` holding both persisted data (tags, sessions) and live timer state.
- `Sources/Grove/Cloud.swift` — Supabase client over plain `URLSession` (no SDK): email+password auth, token refresh, refresh token in Keychain, PostgREST insert/select with paging.
- `../supabase/migrations/` — schema + RLS for `sessions` and `tags`. Applied by pasting into the dashboard SQL Editor (no CLI link / access token).
- `Sources/Grove/GroveApp.swift` — `@main` app, `MenuBarExtra(.window)` scene, all views, and the forest color palette (`Color.forestNight`, `.pine`, `.moss`, `.fern`, `.bark`, `.mist`). Reuse these colors for new UI.
- `icon.swift` — draws the app icon (progress ring + `tree.fill`) with AppKit; `build.sh` runs it and packs `AppIcon.icns` via `sips` + `iconutil`. Its palette is a copy of the one above.
- `docs/` — C4 component diagram (`architecture.drawio` → `c4_architecture.jpg`).

Key behaviors that span files:
- Timer time is derived from dates (`accumulated + now - runningSince`), not tick counting, so it stays correct across sleep. The 1s `Timer.publish` only refreshes `now` and checks pomodoro completion.
- A session is recorded on Stop or pomodoro completion; early-stopped pomodoros record the partial time; sessions under 30s are discarded. Stats attribute a session entirely to its start time.
- Persistence: JSON at `~/Library/Application Support/Grove/data.json` (atomic writes). An undecodable file is renamed to `data.json.corrupt-<ts>` rather than overwritten. Pomodoro length is in `UserDefaults` key `minutes`.
- Active (unsaved) sessions are lost on quit.
- Pomodoro completion plays a synthesized tune (`celebrationWAV()` in GroveCore, generated in memory — no audio assets) and posts a `UNUserNotificationCenter` banner. Notifications are skipped when there is no app bundle (`swift run`), so test them via `build.sh`.

## Architecture (Android; paths relative to `android/app/src/main/java/local/grove/focus/`)

- `core/` — pure Kotlin, no Android imports (JVM unit tests): the twin of `GroveCore` (`Session.kt`, `Sync.kt`, `Tune.kt`) plus `Timer.kt` (`TimerState`: date-derived timer, `stop()` returns the session to record, `endsAt` for the alarm).
- `Store.kt` — single main-thread store (Compose `mutableStateOf`): tags, sessions, `pendingTags`, and the live timer, all in `filesDir/data.json` (temp file + atomic move; unreadable file kept as `data.json.corrupt-<ts>`). Also `sync(Remote)`. Hooks: `onTimerChange` → notification/alarm, `onUnsent` → schedule sync.
- `GroveApp.kt` — `Application` holding the `Store`, `Cloud`, `CloudSync`; restores notification + alarm on every process start. Also plays the tune (`AudioTrack`, PCM from `celebrationPcm()`).
- `TimerNotifications.kt` / `TimerReceiver.kt` — ongoing notification (system-drawn chronometer; Pause/Resume, Stop & Save), exact completion alarm, "Pomodoro complete 🌳" banner. No foreground service.
- `Cloud.kt` — `supabase-kt` (auth-kt + postgrest-kt, Ktor OkHttp) implementing `Remote`; `CloudSync` holds sign-in status and runs syncs with the macOS status texts.
- `MainActivity.kt` (Compose screen mirroring the macOS dropdown), `Theme.kt` (copy of the forest palette — keep in sync with `GroveApp.swift`), `res/` (adaptive icon vectors redrawn from `icon.swift`; the tree is our own path, SF Symbols can't ship on Android).

Android differences from macOS:
- Android kills background apps, so the live timer is persisted with the data (macOS loses an active session on quit), and a pomodoro can complete with the app closed: the exact alarm (`USE_EXACT_ALARM`, granted at install for sideloaded apps; Play would need justification) wakes `TimerReceiver`, which records the session and celebrates. `completeIfDue` is idempotent, so the in-app tick and the alarm can't record twice.
- Pomodoro length lives in `data.json` (`timer.minutes`), not preferences.
- Sync runs on app start/foreground, after changes, and every 5 min only while the app is open (no background sync).
- `supabase-kt` keeps the session in its default app-private storage (not encrypted, unlike the macOS Keychain); `android:allowBackup="false"` keeps it on the device. A server-rejected refresh clears the session (= signed out), like macOS.
- Notification permission is requested on first launch; without it the timer and alarm still work.

Cloud sync (Supabase project `ryaqrgzgnhbiybtsogln`, both apps):
- Local JSON stays the source of truth; sync is push-then-pull, on launch, after each change, and every 5 min.
- Sessions are append-only with device-generated UUIDs, so upload uses `resolution=ignore-duplicates` and needs no conflict handling. Each `Session` has a local `synced` flag.
- Tags sync with a `deleted` tombstone; un-uploaded local changes live in `pendingTags` (name → deleted). Merge rules are in `GroveCore/Sync.swift` and `core/Sync.kt` (`mergeSessions`, `mergeTags`), pinned by `shared/fixtures/`.
- Only the publishable key ships in the app; never use a secret/service_role key. Data access relies on RLS (`auth.uid() = user_id`).
- Free-plan project created after 2026-06-03 can't customize auth email templates, hence password sign-in rather than an email OTP code.
- Sign-up sends a confirmation link; it redirects to the project's Site URL (default `localhost:3000`) after confirming, so a broken page there is harmless. Built-in SMTP is heavily rate-limited — repeated sign-ups return "For security purposes, you can only request this after N seconds".
- Postgres returns uuids lowercase; macOS generates uppercase locally (compared as `UUID`, fine), Android stores ids as lowercase strings.
- Ad-hoc signing changes the code signature every build, so macOS may re-prompt for Keychain access after rebuilding.
- Supabase skills (`supabase`, `supabase-postgres-best-practices`) are installed in `.claude/skills/`; load them before touching auth, schema, or RLS. Schema changes go in a new file under `supabase/migrations/`.

## Network note

On the author's machine, route all network requests through the local HTTP proxy by default: `http://127.0.0.1:8888`, or `http://127.0.0.1:8889` if 8888 is down or the request fails. If neither answers, ask the author to start one (`zp [port]` in their shell). It bypasses the corporate Zscaler TLS inspection.

- Shell commands: prefix `HTTPS_PROXY=http://127.0.0.1:8888 HTTP_PROXY=http://127.0.0.1:8888 NO_PROXY=localhost,127.0.0.1`.
- Already configured (machine-level, not in the repo): this repo's git `http.proxy`, and `~/.gradle/gradle.properties` (proxy 8888, plus a trust store `~/.gradle/cacerts-zscaler.jks` with the Zscaler CA for when Gradle connects directly). To use 8889, edit the port there.
- Android emulator: Android doesn't trust the Zscaler CA ("Trust anchor for certification path not found"), so the emulator uses the proxy via `adb shell settings put global http_proxy 10.0.2.2:8888` (persists in the emulator; the emulator's `-http-proxy` flag timed out). The author's phone on a normal network needs nothing.
- The proxy has dropped mid-session before; "Can't reach the server" / "Failed to connect to …:8888" means restart it.
