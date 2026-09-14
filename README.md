# AutoPlay

> Schedule your music. Let your Android phone handle the reminder and Spotify launch.

## 🌐 Web App

**Live app:** https://auto-play-4qyk.vercel.app/

Use the web dashboard to manage AutoPlay schedules and Spotify playlist links.

## 📱 Android App

**Download the AutoPlay Android app:** [Download APK](https://github.com/gokulmaniraj2008-collab/AutoPlay/releases/latest)

> The APK download will become active after the Android APK is published in a GitHub Release.

## 📱 Project

AutoPlay is a web + Android automation project designed to let users create recurring music schedules, save them locally on Android, and launch Spotify playlist links at the scheduled time.

### Current architecture

```text
Next.js Web App
      ↓
   Supabase
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
- Supabase integration foundation
- GitHub Actions Android APK build
- Vercel deployment for the web app

## 🛠️ Tech Stack

### Web
- Next.js 16
- React
- TypeScript
- Supabase
- Vercel

### Android
- Kotlin
- Jetpack Compose
- Android AlarmManager
- Android Notifications
- Gradle

## 🚧 Current Status

AutoPlay is under active development.

The Android scheduler can launch a Spotify playlist URL at the scheduled time. Actual automatic playback behavior depends on Spotify and Android device restrictions, so this should not be treated as guaranteed unattended playback yet.

Web-to-Android Supabase synchronization is also still being completed and verified.

## 🚀 Development

### Web

```bash
cd web
npm install
npm run dev
```

Create a `web/.env.local` file with the Supabase public project URL and anonymous key before using cloud-backed schedule features.

### Android

Open the `android` directory in Android Studio and build the debug APK.

## 🔐 Security

Do not commit Supabase service-role keys, private API keys, signing keys, passwords, or other secrets to GitHub.

Only public client configuration intended for browser use should be exposed through `NEXT_PUBLIC_*` environment variables.

## 📦 Repository

GitHub: https://github.com/gokulmaniraj2008-collab/AutoPlay

## 📄 License

This project is currently maintained as a personal development project. Licensing terms can be added when the project is ready for public release.
