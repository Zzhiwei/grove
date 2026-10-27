# Android app plan

Port the macOS app (`macos/`) to Android with the same features and behavior. Personal use: sideloaded onto one phone.

## Decisions

| Topic | Choice |
| --- | --- |
| Scope | Same as macOS: pomodoro, stopwatch, tags, focus totals per tag (Today / 7 Days / Month), completion tune + notification, Supabase sync |
| Timer outside the app | Ongoing notification; the system draws the live countdown (`setUsesChronometer` + `setChronometerCountDown`) |
| Pomodoro completion while app is closed | Exact alarm (`AlarmManager.setExactAndAllowWhileIdle`, `USE_EXACT_ALARM`, granted at install for sideloaded apps). No foreground service |
| Distribution | Sideload a debug-signed APK |
| Shared logic | Rewrite `GroveCore` in Kotlin; shared JSON test fixtures in `shared/fixtures/` that both test suites read |
| Cloud | `supabase-kt` (auth + postgrest), same Supabase project and tables |
| Storage | JSON file in app storage (`filesDir/data.json`): tags, sessions, pending tag changes, live timer incl. pomodoro length; Supabase sync on top |
| Min Android | 16 (`minSdk 36`); `compileSdk`/`targetSdk 37` (installed SDK) |
| Language / UI / build | Kotlin, Jetpack Compose, Gradle (Android Studio's bundled JDK) |

## Layout

```
android/                     Gradle project, single module
  app/src/main/java/local/grove/focus/
    core/                    pure Kotlin, no Android imports (JVM-unit-testable)
      Session.kt             Session, StatRange, totals, formatDuration, formatTotal, stepMinutes
      Sync.kt                RemoteSession, RemoteTag, mergeSessions, mergeTags
      Tune.kt                celebrationPcm(): ShortArray (same notes/envelope as Tune.swift)
      Timer.kt               TimerState + transitions (start/pause/stop/complete)
    Store.kt                 app-wide singleton: loads/saves data.json, owns TimerState, sync
    Cloud.kt                 supabase-kt client wrapper
    TimerNotifications.kt    ongoing notification, completion notification, exact alarm
    TimerReceiver.kt         BroadcastReceiver: alarm fired, Pause/Resume/Stop actions
    MainActivity.kt          Compose UI (mirrors macos ContentView)
    Theme.kt                 forest palette (copy of GroveApp.swift colors)
  app/src/test/...           JUnit tests for core/ + fixtures
shared/fixtures/             JSON cases used by macOS and Android tests
docs/android-plan.md         this file
```

## Differences from macOS (required by Android)

- **Timer state survives the app closing.** macOS loses an active session on quit; Android closes background apps routinely, so `TimerState` (mode, tag, minutes, sessionStart, runningSince, accumulated) is saved in `data.json` on every change.
- **Completion can happen with the app closed.** The alarm receiver loads `Store`, records the session, posts "Pomodoro complete 🌳 / N min of TAG focus. Time for a break.", plays the tune. `complete()` does nothing if the session is already stopped, so the in-app 1 s tick and the alarm can't record it twice.
- **Reboot clears alarms.** ponytail: no boot receiver; on next app open, an overdue pomodoro is completed then (recorded with its full length). Add `RECEIVE_BOOT_COMPLETED` if that proves annoying.
- **Sync** runs on app open, after each change, and every 5 min only while the app is open. No background sync (WorkManager) unless needed.
- **Refresh token** stored by `supabase-kt`'s default session storage (app-private SharedPreferences, not encrypted). `android:allowBackup="false"` so it never leaves the phone. Acceptable for a personal sideloaded app.
- No Quit button; no menu bar label.

## Notifications

- Channel `timer` (low importance, silent): ongoing, while a session is active. Running pomodoro → countdown to end time; running stopwatch → count-up from `now - elapsed`; paused → static "Paused · MM:SS". Actions: Pause/Resume, Stop & Save. Tapping opens the app.
- Channel `done` (high importance, no channel sound, since the tune plays via `AudioTrack`).
- `POST_NOTIFICATIONS` requested on first launch. If denied: the timer still works in the app, the alarm still records and plays the tune.
- Android 14+ lets users swipe away ongoing notifications; it's re-posted on the next state change.

## Milestones

Each ends with `./gradlew test` passing and a commit.

1. **Scaffold + core.** `brew install gradle` once to generate the wrapper; create `android/` (Compose app template, versions = latest stable AGP / Kotlin / Compose BOM / supabase-kt at time of writing). Port `core/` with tests. Create `shared/fixtures/` (merge-tags cases, merge-sessions, Supabase timestamp with and without fraction, formatting, stepMinutes) and switch the macOS tests in `macos/Tests/` to read the same files.
2. **Timer + UI.** `TimerState` with tests: 30 s minimum, pomodoro capped at target, elapsed from dates, pause/resume, `complete()` idempotent. `Store` persistence (atomic write via temp file + rename; unreadable file renamed to `data.json.corrupt-<ts>`, as on macOS). Compose screen: mode switch, ring, minutes stepper, tag picker/add/remove, Start/Pause/Resume, Stop & Save, stats with bars. In-app completion plays tune.
3. **Background.** Ongoing notification, exact alarm scheduled on start/resume and cancelled on pause/stop, `TimerReceiver` for the alarm and notification actions. Test on the emulator with the app swiped away.
4. **Cloud sync.** Sign up / sign in / sign out, push unsent sessions (`upsert`, ignore duplicates on `id`), push `pendingTags` (upsert on `user_id,name`), pull all rows paged by 1000, merge with `core/Sync.kt`. Same status texts as macOS.
5. **Polish + docs.** Adaptive app icon (ring + tree, forest palette), `android/README.md`, root README platform table, AGENTS.md Android section. Build APK, install on phone.

## Commands (once scaffolded, run from `android/`)

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew test               # unit tests
./gradlew assembleDebug      # → app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug       # install on running emulator / connected phone
```

The debug signing key (`~/.android/debug.keystore`) is stable on this Mac, so reinstalling an update keeps the app's data.

## Things to verify on the real phone

- Alarm fires on time with the screen off for 25+ min (battery-saving sleep).
- Countdown in the notification matches the app after reopening.
- Process killed mid-pomodoro (swipe away from recents) → still completes and records.
- Phone's battery optimizer (maker-specific) doesn't block the alarm.
