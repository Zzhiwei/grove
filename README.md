# Grove 🌲

A forest-themed focus timer that lives entirely in the macOS menu bar.

- **Pomodoro** countdown (default 25 min, adjustable) with a chime on completion
- **Stopwatch** count-up timer
- **Tags** for every session — pick, add, or remove from the dropdown
- **Focus by tag** totals for Today, past 7 days, or past month
- Live countdown in the menu bar; no Dock icon

## Requirements

macOS 13+ and Xcode or the Command Line Tools (`xcode-select --install`).

## Build & run

```sh
./build.sh
open build/Grove.app
```

Copy `build/Grove.app` to `/Applications` to keep it. Run tests with `swift test`.

## Data

Sessions and tags are stored in `~/Library/Application Support/Grove/data.json`.
