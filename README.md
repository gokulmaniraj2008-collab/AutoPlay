# AutoPlay

> Schedule your music. Let your Android phone handle the reminder and Spotify launch.

## 🌐 Web App

**Live app:** https://auto-play-4qyk.vercel.app/

Use the web dashboard to manage AutoPlay schedules and Spotify playlist links.

## 📱 Android App

**Download the AutoPlay Android app:** [Download APK](https://github.com/gokulmaniraj2008-collab/AutoPlay/releases/latest)

> The APK download becomes active when the Android APK is published in a GitHub Release.

## 🔄 Supabase Cloud Sync

AutoPlay now has the backend foundation for Web ↔ Android schedule synchronization.

### `autoplay_schedules`

The Supabase project contains an `autoplay_schedules` table for user-owned schedule records with Row Level Security (RLS) enabled.

The intended architecture is:

```text
Website Login
     ↓
Supabase Auth
     ↓
autoplay_schedules
     ↑
Android App Login
```

This allows the same authenticated user account to share schedules between the web dashboard and Android app.

### Security model

- User-owned schedule records
- `auth.users` ownership
- RLS policies for read / insert / update / delete
- Browser/mobile clients use the public Supabase client configuration
- Server/service-role secrets must never be committed to GitHub

**Important:** The Supabase database foundation is in place, but the Web and Android source-code integration with Supabase Auth and `autoplay_schedules` is still pending verification. Do not treat Web ↔ Android synchronization as complete until both clients are connected and tested with the same account.

## 📱 Project

AutoPlay is a web + Android automation project designed to let users create recurring music schedules, synchronize them through Supabase, and launch Spotify playlist links at the scheduled time.

### Current architecture

```text
Next.js Web App
      ↓
Supabase Auth + PostgreSQL
      ↓
autoplay_schedules
      ↓
Android AutoPlay App
      ↓
Android Alarm / Notification
      ↓
Spotify Playlist
```

## ✨ Features

- Schedule a daily music time
- Save a Spotify playlist URL
- Enable or disable schedules
- Test a schedule immediately
- Android alarm scheduling
- Android notification support
- Local Android schedule persistence
- Next.js web dashboard
- Supabase authentication and schedule-sync foundation
- RLS-protected user-owned schedules
- GitHub Actions Android APK build
- Vercel deployment for the web app

## 🛠️ Tech Stack

### Web
- Next.js 16
- React
- TypeScript
- Supabase
- Vercel

### Backend
- Supabase Auth
- PostgreSQL
- Row Level Security (RLS)
- `autoplay_schedules`

### Android
- Kotlin
- Jetpack Compose
- Android AlarmManager
- Android Notifications
- Gradle

## 🚧 Current Status

AutoPlay is under active development.

**Completed:**
- Supabase project connection/foundation
- `autoplay_schedules` database table
- User ownership model
- RLS policies for schedule CRUD access
- GitHub repository documentation updated for the sync architecture

**In progress:**
- Connect Web authentication to Supabase Auth
- Connect Android authentication to the same Supabase account
- Replace/bridge local schedule storage with `autoplay_schedules`
- Verify Web → Supabase → Android synchronization
- Verify Android → Supabase → Web synchronization

The Android scheduler can launch a Spotify playlist URL at the scheduled time. Actual automatic playback behavior depends on Spotify and Android device restrictions, so this should not be treated as guaranteed unattended playback yet.

## 🚀 Development

### Web

```bash
cd web
npm install
npm run dev
```

Create a `web/.env.local` file with the Supabase public project URL and publishable/anonymous client key before using cloud-backed schedule features.

### Android

Open the `android` directory in Android Studio and build the debug APK.

## 🔐 Security

Do not commit Supabase service-role keys, private API keys, signing keys, passwords, or other secrets to GitHub.

Only public client configuration intended for browser/mobile use should be exposed through environment variables.

## 📦 Repository

GitHub: https://github.com/gokulmaniraj2008-collab/AutoPlay

## 📄 License

This project is currently maintained as a personal development project. Licensing terms can be added when the project is ready for public release.
