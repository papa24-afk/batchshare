<p align="center">
  <img src="docs/images/logo.png" alt="BatchShare Logo" width="240" style="border-radius: 24px;">
</p>

<h1 align="center">BatchShare 📦</h1>

<p align="center">
  <a href="https://github.com/papa24-afk/batchshare/releases/latest"><img src="https://img.shields.io/github/v/release/papa24-afk/batchshare?color=blue&label=Release" alt="Latest Release"></a>
  <a href="https://developer.android.com"><img src="https://img.shields.io/badge/Platform-Android%208.0%2B-brightgreen" alt="Platform"></a>
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-1.9.22-purple" alt="Kotlin"></a>
  <a href="https://developer.android.com/jetpack/compose"><img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4" alt="Jetpack Compose"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="License: MIT"></a>
</p>

> **The ultimate Android staging tray & zip bundler.**  
> Effortlessly collect, bundle, and share multiple documents and files from **WhatsApp, Telegram, Google Drive, Chrome, and your File Manager** to any app — without restrictions.

---

## 🎯 Universal Staging Tray — Works Across ALL Apps

While **BatchShare** is famous for eliminating the frustrating limitation on **WhatsApp** (where you cannot externally share multiple documents at once), it is built as a **universal staging tray for your entire Android system**.

It works seamlessly with **any app** on your phone:
- 💬 **Messaging**: WhatsApp, Telegram, Signal, Discord, Slack, Messenger
- 🌐 **Browsers**: Chrome, Firefox, Brave, Edge, Samsung Internet
- 📁 **File Managers**: Google Files, Samsung My Files, Solid Explorer, Mi File Manager
- ☁️ **Cloud & Productivity**: Google Drive, Gmail, Outlook, Dropbox, OneDrive
- 🖼️ **Media**: Gallery, Google Photos, Downloads

### The Workflow:
1. **Share from anywhere**: Send files one by one or in batches from **any app or chat** to **BatchShare**.
2. **BatchShare collects and holds them**: Each incoming file is staged in your temporary holding tray with duplicate checks and file badge indicators.
3. **Zip & Share or Share Raw**: Bundle everything into an encrypted/renamed `.zip` archive or send them raw directly to Telegram, Drive, Gmail, or back into WhatsApp.
4. **Auto-Clear & Close**: Once shared, BatchShare automatically wipes temporary files and exits your recent apps cleanly.

---

## ✨ Key Features

- **Floating Staging Bubble (System Overlay)**: Stay in your flow without context switching. When enabled, incoming shared files stage seamlessly into a discreet, floating draggable bubble anchored to your screen edge with a live counter badge. Tap to expand an authentic Material 3 sheet to Zip & Share, inspect files, or drag down to the bottom dismiss target to clear the queue.
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

## 📱 Workflow Diagram

```
[ Any App: WhatsApp / Telegram / Chrome / Files ]
                      │
       (Share 1-by-1 or multi-select into BatchShare)
                      ▼
         [ 📦 BatchShare Holding Tray ]
         • Collects documents, images, videos
         • Color-coded badges & previews
         • Duplicate prevention & AES-256 password
                      │
         ┌────────────┴────────────┐
         ▼                         ▼
   [ Zip & Share ]            [ Share Raw ]
         │                         │
         └────────────┬────────────┘
                      ▼
       [ Android System Share Menu ]
       • Telegram / WhatsApp / Drive / Gmail / etc.
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
