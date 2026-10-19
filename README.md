# BatchShare 📦

[![Latest Release](https://img.shields.io/github/v/release/papa24-afk/batchshare?color=blue&label=Release)](https://github.com/papa24-afk/batchshare/releases/latest)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-brightgreen)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-purple)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4)](https://developer.android.com/jetpack/compose)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

> **The ultimate Android staging tray & zip bundler.**  
> Effortlessly collect, bundle, and share multiple documents and files from **WhatsApp, Telegram, Google Drive, Chrome, and your File Manager** to any app — without restrictions.

---

## 🎯 The Problem BatchShare Solves

On the standard **WhatsApp** Android app, you cannot share multiple documents or files at once to another app (like Telegram, Google Drive, or Gmail). When you select multiple documents in a chat, WhatsApp removes the external share button and only lets you forward inside WhatsApp itself. If you want to send several files to Telegram or an email, you are forced to share them **one by one, repeatedly switching apps**.

**BatchShare solves this limitation permanently.**

It acts as a smart, temporary **holding box (staging tray)**:
1. Share Document 1 from WhatsApp to **BatchShare** &rarr; BatchShare catches and holds it.
2. Share Document 2 from WhatsApp to **BatchShare** &rarr; BatchShare holds it alongside Document 1.
3. Share as many files as you need from any chat or app.
4. When ready, tap **"Zip & Share"** (or **"Share Raw"**) &rarr; BatchShare bundles everything and opens Android's system share menu to send to Telegram, WhatsApp, Gmail, Drive, or anywhere else!
5. BatchShare **auto-clears the holding box and closes itself**, leaving your phone clean and ready for your next batch.

---

## ✨ Key Features

- 📥 **Universal Holding Box**: Accumulate files from WhatsApp, Telegram, Chrome, Gallery, or Downloads one-by-one or in batches.
- 🗜️ **Fast Zip & Share**: Compress all staged documents into a clean `.zip` archive in a split second.
- 📂 **Share Raw (Unzipped)**: Prefer not to zip? Tap **Share Raw** to send all your files individually at once directly to Telegram or other apps.
- 🏷️ **1-Tap Archive Renaming**: Customize your zip name (e.g. `Client_Invoices.zip`, `Project_Docs.zip`) or pick from 1-tap quick suggestions (`Documents`, `Photos`, `Work`, `Archive`).
- 🔒 **AES-256 Password Encryption**: Protect sensitive contracts, financial receipts, or personal photos with industry-standard AES-256 encryption.
- 🚫 **Smart Duplicate Prevention**: Automatic SHA-256 checksum fingerprinting detects and skips files you've already added, keeping your archives clean.
- 🎨 **Color-Coded File Badges**: Visual recognition for different file types:
  - 📕 **Red (PDF)**: `.pdf`
  - 🖼️ **Cyan (IMG)**: `.jpg`, `.png`, `.webp`, `.heic`, `.gif`
  - 🎬 **Purple (VID)**: `.mp4`, `.mkv`, `.mov`
  - 🎵 **Green (AUD)**: `.mp3`, `.m4a`, `.wav`, `.ogg`
  - 📦 **Orange (APK / ZIP)**: `.apk`, `.zip`, `.rar`, `.7z`
  - 📄 **Blue (DOC)**: `.docx`, `.xlsx`, `.pptx`, `.txt`, `.csv`
- 👁️ **Tap to Preview**: Tap any file card in the queue to inspect it directly in your phone's default viewer before sending.
- 🔔 **Silent Status Bar Notification**: A quiet, persistent notification sits in your notification drawer while files are held, with direct **[Zip & Share]** and **[Clear]** actions without opening the app.
- 🧹 **Auto-Clear & Instant Exit**: Upon sharing, BatchShare cleans out the cache and removes itself from the Recent Apps switcher (`finishAndRemoveTask()`) so you land seamlessly in your target app.
- 🛡️ **100% Offline & Private**: Zero internet permissions. No ads, no analytics, no background tracking. Your files never leave your device.

---

## 📱 Screenshots & Workflow

```
[ WhatsApp Chat ]              [ BatchShare Holding Box ]             [ Android System Chooser ]
   Document 1 ----Share----\                                         /---> Telegram
   Document 2 ----Share-----> [ 📦 3 Files Held (14.8 MB) ] --Zip--> ----> Google Drive
   Document 3 ----Share----/   [  Zip & Share  |  Share Raw  ]       \---> Gmail / WhatsApp
```

---

## 🚀 Download & Installation

### Option 1: Direct APK Download
1. Download the latest **[BatchShare.apk](https://github.com/papa24-afk/batchshare/releases/latest)** from the [Releases](https://github.com/papa24-afk/batchshare/releases) page.
2. Open the downloaded `.apk` file on your Android device.
3. If prompted, allow **"Install unknown apps"** for your browser or file manager.
4. Tap **Install** & you're ready to batch share!

### System Requirements:
- **Android OS**: Android 8.0 (Oreo / API 26) or higher.
- **Architecture**: `arm64-v8a`, `armeabi-v7a`, `x86_64`.
- **Permissions**: Storage / FileProvider (No Internet permission required).

---

## 🛠️ Building From Source

### Prerequisites:
- **JDK**: Java Development Kit 17+
- **Android SDK**: Build tools 35.0.0, Compile SDK 35
- **Gradle**: 8.4+

### Build steps:
```bash
# Clone the repository
git clone https://github.com/papa24-afk/batchshare.git
cd batchshare

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease
```
The output APK will be located in `app/build/outputs/apk/debug/app-debug.apk`.

---

## 🔒 Privacy & Security

BatchShare is designed with a **privacy-first** architecture:
- **No Internet Access**: The app does not request `android.permission.INTERNET`. It is physically impossible for the app to send your data anywhere.
- **Local Cache**: Staged files are stored in your device's private app directory (`context.filesDir`) and cleaned up immediately after sharing.
- **AES-256 Encryption**: Password-protected zips use modern 256-bit AES encryption powered by Zip4j.

---

## 👤 Author & Credits

- **Developer**: **D!g!†&#64;L G#0$†** ([@papa24-afk](https://github.com/papa24-afk))
- **Repository**: [https://github.com/papa24-afk/batchshare](https://github.com/papa24-afk/batchshare)

---

## 📄 License

This project is open-source and licensed under the [MIT License](LICENSE).
