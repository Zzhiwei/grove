# AGENTS.md

This file provides guidance to AI coding agents (Claude Code, etc.) when working with code in this repository.

Grove is a macOS menu-bar-only Pomodoro / stopwatch app (SwiftUI `MenuBarExtra`, macOS 13+). Built with SwiftPM and the Xcode Command Line Tools — there is no `.xcodeproj`.

## Commands

```sh
swift build                                   # debug build
swift test                                    # all tests (swift-testing, not XCTest)
swift test --filter totalsGroupsFiltersAndSorts   # single test
./build.sh                                    # release build → build/Grove.app (ad-hoc signed)
open build/Grove.app                          # run; quit via the Quit button in the dropdown
```

`swift run Grove` also works, but the `LSUIElement` (no Dock icon) setting only applies inside the `.app` bundle — `build.sh` writes the `Info.plist`.

No linter is configured.

## Architecture

- `Sources/GroveCore` — pure, UI-free logic: `Session`, `StatRange` (today / 7 days / past calendar month), `totals()` aggregation, duration formatting. Everything testable lives here; `Tests/GroveCoreTests` only tests this target.
- `Sources/Grove/Model.swift` — single `@MainActor ObservableObject` holding both persisted data (tags, sessions) and live timer state.
- `Sources/Grove/GroveApp.swift` — `@main` app, `MenuBarExtra(.window)` scene, all views, and the forest color palette (`Color.forestNight`, `.pine`, `.moss`, `.fern`, `.bark`, `.mist`). Reuse these colors for new UI.

Key behaviors that span files:
- Timer time is derived from dates (`accumulated + now - runningSince`), not tick counting, so it stays correct across sleep. The 1s `Timer.publish` only refreshes `now` and checks pomodoro completion.
- A session is recorded on Stop or pomodoro completion; early-stopped pomodoros record the partial time. Stats attribute a session entirely to its start time.
- Persistence: JSON at `~/Library/Application Support/Grove/data.json` (atomic writes). An undecodable file is renamed to `data.json.corrupt-<ts>` rather than overwritten. Pomodoro length is in `UserDefaults` key `minutes`.
- Active (unsaved) sessions are lost on quit.
- Pomodoro completion plays a synthesized tune (`celebrationWAV()` in GroveCore, generated in memory — no audio assets) and posts a `UNUserNotificationCenter` banner. Notifications are skipped when there is no app bundle (`swift run`), so test them via `build.sh`.

## Network note

On the author's machine, GitHub HTTPS pushes may need the local proxy: `git -c http.proxy=http://127.0.0.1:8888 push`.
