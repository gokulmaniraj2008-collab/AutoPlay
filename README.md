# AutoPlay

> Turn natural-language tasks into scheduled Android actions.

## 🤖 AI Phone Task Automation

AutoPlay is evolving from a music scheduler into a permission-based Android task automation app.

You can type a task such as:

- `At 8 PM play a Tamil song on Spotify`
- `Open Spotify at 8 PM`
- `Call +919876543210 at 6 PM`
- `Open YouTube now`

AutoPlay normalizes the request into an action, stores it locally, and can schedule it with Android exact alarms when the required permission is enabled.

### Task flow

```text
Natural-language task
        ↓
   AutoPlay parser
        ↓
  Normalized task
        ↓
 Exact Android alarm (if scheduled)
        ↓
    Task receiver
        ↓
 Permission-aware Android action
        ↓
 Result / task history
```

This follows the same general Android integration model used by assistant-style apps: apps request supported actions through Android APIs and explicit user permissions rather than receiving unrestricted access to the phone.

## 🎵 Spotify

AutoPlay can open the installed Spotify app for Spotify tasks today. Full unattended Spotify playback is a separate integration step because Spotify requires its own authorization and App Remote integration. Spotify's official Android SDK supports remotely controlling playback in the Spotify app after user authorization, including initiating track/playlist playback.

Do not treat a simple Spotify URL launch as guaranteed unattended playback.

## 🌐 Web App

The existing web dashboard remains available for schedule management and Supabase synchronization.

## 🔄 Supabase Cloud Sync

The project contains an `autoplay_schedules` table for user-owned schedule records with Row Level Security (RLS).

```text
Website Login
     ↓
Supabase Auth
     ↓
autoplay_schedules
     ↑
Android App Login
```

Web ↔ Android synchronization should still be verified end-to-end before being described as production-complete.

## 📱 Android

Current Android stack:

- Kotlin
- Jetpack Compose
- Android AlarmManager
- Exact alarms
- Android permissions
- Task parser + task store
- Permission-aware task executor
- Existing local/cloud music scheduler
- GitHub Actions APK build

### Current Android version

**0.6.0** — AI Phone Task Automation foundation.

## 🔐 Security model

AutoPlay does **not** receive unrestricted or hidden control of the device.

Actions use explicit Android capabilities such as:

- Exact alarms for scheduled tasks
- `CALL_PHONE` for direct calls
- Android intents for supported app launches
- Spotify authorization for Spotify App Remote control

Sensitive credentials and API keys must never be committed to GitHub.

## 🚧 Current limitations

The new task engine is the foundation, not a claim of universal phone control.

- Natural-language parsing is intentionally simple in v0.6.0.
- Scheduled calls currently require a phone number rather than resolving contact names.
- Android background activity restrictions can prevent an app from freely launching arbitrary screens while the phone is unattended.
- Spotify unattended playback requires the official Spotify App Remote authorization/integration; the current task runner opens Spotify but does not bypass Spotify or Android security.
- More actions can be added through dedicated Android APIs/integrations.

## 📦 Repository

GitHub: https://github.com/gokulmaniraj2008-collab/AutoPlay

## 📄 License

This project is currently maintained as a personal development project. Licensing terms can be added when the project is ready for public release.
