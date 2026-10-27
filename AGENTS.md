# AGENTS.md

This file provides guidance to AI coding agents (Claude Code, etc.) when working with code in this repository.

Grove is a Pomodoro / stopwatch app. Repo layout:

- `macos/` — macOS menu-bar-only app (SwiftUI `MenuBarExtra`, macOS 13+). Built with SwiftPM and the Xcode Command Line Tools — there is no `.xcodeproj`.
- `android/` — Android port in progress (Kotlin, Jetpack Compose, Gradle; Android 16+). Plan and decisions: `docs/android-plan.md`. Pure logic lives in `app/src/main/java/local/grove/focus/core/` (the Kotlin twin of `GroveCore`).
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

Cloud sync (Supabase project `ryaqrgzgnhbiybtsogln`):
- Local JSON stays the source of truth; sync is push-then-pull, on launch, after each change, and every 5 min.
- Sessions are append-only with device-generated UUIDs, so upload uses `resolution=ignore-duplicates` and needs no conflict handling. Each `Session` has a local `synced` flag.
- Tags sync with a `deleted` tombstone; un-uploaded local changes live in `pendingTags` (name → deleted). Merge rules are in `GroveCore/Sync.swift` (`mergeSessions`, `mergeTags`).
- Only the publishable key ships in the app; never use a secret/service_role key. Data access relies on RLS (`auth.uid() = user_id`).
- Free-plan project created after 2026-06-03 can't customize auth email templates, hence password sign-in rather than an email OTP code.
- Sign-up sends a confirmation link; it redirects to the project's Site URL (default `localhost:3000`) after confirming, so a broken page there is harmless. Built-in SMTP is heavily rate-limited — repeated sign-ups return "For security purposes, you can only request this after N seconds".
- Ad-hoc signing changes the code signature every build, so macOS may re-prompt for Keychain access after rebuilding.
- Supabase skills (`supabase`, `supabase-postgres-best-practices`) are installed in `.claude/skills/`; load them before touching auth, schema, or RLS. Schema changes go in a new file under `supabase/migrations/`.

## Network note

On the author's machine, GitHub git operations need the local proxy, scoped to github.com only: `git -c http.https://github.com.proxy=http://127.0.0.1:8888 push`. Don't proxy other traffic (Supabase, npm).

The author's network has Zscaler TLS inspection. Java doesn't trust its CA, so `~/.gradle/gradle.properties` points Gradle at `~/.gradle/cacerts-zscaler.jks` (the JDK's default CAs + the Zscaler CA). Machine setup, not in the repo; if Gradle reports "plugin was not found" or PKIX errors, check that file.
