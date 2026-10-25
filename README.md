# Grove 🌲

A forest-themed focus timer that lives entirely in the macOS menu bar.

- **Pomodoro** countdown (default 25 min, adjustable)
- **Stopwatch** count-up timer
- **Tags** for every session — pick, add, or remove from the dropdown
- **Focus by tag** totals for Today, past 7 days, or past month
- Mac notification and a celebratory tune when a pomodoro completes
- Optional **cloud sync** via Supabase (email + password account)
- Live countdown in the menu bar; no Dock icon

## Requirements

macOS 13+ and Xcode or the Command Line Tools (`xcode-select --install`).

## Build & run

```sh
./build.sh
open build/Grove.app
```

Copy `build/Grove.app` to `/Applications` to keep it. Run tests with `swift test`.

## Data & cloud sync

Sessions and tags are stored in `~/Library/Application Support/Grove/data.json`.

Data stays on the Mac and works offline; cloud sync is optional.

### Cloud sync

1. In the dropdown, open **Sync to cloud**, enter email + password (6+ chars), click **Create Account** once.
2. Click the confirmation link in the email (a `localhost` page afterwards is expected), then **Sign In**.

Sync runs on launch, after each session or tag change, and every 5 minutes. Signing out keeps local data.

**Using your own Supabase project:** run `supabase/migrations/*.sql` in the dashboard SQL Editor, then set the project URL and publishable key in `Sources/Grove/Cloud.swift`. Never put a secret / service_role key in the app.
