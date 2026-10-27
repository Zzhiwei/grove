# Grove for macOS

A focus timer that lives entirely in the macOS menu bar.

- **Pomodoro** countdown (default 25 min, adjustable)
- **Stopwatch** count-up timer
- **Tags** for every session — pick, add, or remove from the dropdown
- **Focus by tag** totals for Today, past 7 days, or past month
- Mac notification and a celebratory tune when a pomodoro completes
- Optional **cloud sync** — see the [root README](../README.md#cloud-sync)
- Live countdown in the menu bar; no Dock icon

## Requirements

macOS 13+ and Xcode or the Command Line Tools (`xcode-select --install`).

## Build & run

From this folder:

```sh
./build.sh
open build/Grove.app
```

Copy `build/Grove.app` to `/Applications` to keep it. Run tests with `swift test`.

## Data

Sessions and tags are stored in `~/Library/Application Support/Grove/data.json`.

Architecture: [C4 component diagram](docs/c4_architecture.jpg) (source: `docs/architecture.drawio`).
