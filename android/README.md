# Grove for Android

The Grove focus timer as an Android app, for personal use (sideloaded; not on Google Play).

- **Pomodoro** countdown (default 25 min, adjustable) and **stopwatch**
- **Tags** for every session; **focus by tag** totals for Today, past 7 days, or past month
- Live countdown in an ongoing notification, with Pause / Resume and Stop & Save
- A pomodoro completes on time even with the app closed: notification + celebratory tune
- Optional **cloud sync** with the Mac app — see the [root README](../README.md#cloud-sync)

## Requirements

Android 16 or later. To build: Android Studio (for the SDK and its bundled JDK); no Android Studio needed after installing it.

## Build & install

From this folder:

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew test             # unit tests
./gradlew assembleDebug    # → app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug     # install on a connected phone (USB / wireless debugging) or running emulator
```

Or copy `app-debug.apk` to the phone and open it (allow "install unknown apps" once). Updates installed from the same Mac keep the app's data.

On first launch, allow notifications so the countdown and the "Pomodoro complete" banner can show. Some phone makers' battery savers delay alarms; if a pomodoro finishes late, exempt Grove from battery optimization.

## Data

Sessions, tags and the running timer are stored in the app's private storage (`data.json`) and work offline.
