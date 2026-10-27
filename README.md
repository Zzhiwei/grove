# Grove 🌲

A forest-themed focus timer: pomodoro and stopwatch, tagged sessions, focus totals per tag, and optional cloud sync.

| Platform | Folder |
| --- | --- |
| macOS menu-bar app | [`macos/`](macos/README.md) |

Shared across platforms:

- `supabase/migrations/` — cloud database schema and row-level security.

## Cloud sync

Each app keeps its data locally and works offline; cloud sync via Supabase (email + password account) is optional.

1. In the app, open **Sync to cloud**, enter email + password (6+ chars), click **Create Account** once.
2. Click the confirmation link in the email (a `localhost` page afterwards is expected), then **Sign In**.

Sync runs on launch, after each session or tag change, and every 5 minutes. Signing out keeps local data.

**Using your own Supabase project:** run `supabase/migrations/*.sql` in the dashboard SQL Editor, then set the project URL and publishable key in each app (macOS: `macos/Sources/Grove/Cloud.swift`). Never put a secret / service_role key in an app.
