# TFM Android

Android client (Kotlin + Jetpack Compose, Material 3) for
**TelegramFileManager**: manage the files you store in Telegram channels
through the server's REST API v1 + SignalR hub.

## Features

- **Guided first run**: server URL and API key setup, with a connection check
  before saving.
- **Telegram login** (server-side shared session): via **QR** (with
  `tg://login` deep link when Telegram is on the same device, and 2FA support)
  or via **phone** (code + 2FA password).
- **Channels**: saved / all / favorites, search, details with statistics,
  create channel, join by invitation, create/refresh index, recent messages
  with direct attachment download, leave channel.
- **Channel file browser**: breadcrumb navigation, type filters, sorting,
  recursive search, multi-select, create folder, rename, copy/move, delete,
  **upload files from the device** and download **to the server** (managed
  transfer) or **to the device** (DownloadManager, `Download/TFM` folder).
- **Server local storage**: browsing, filters, upload from the device,
  **send to a Telegram channel** (without duplicating bytes), rename, delete,
  clear the streaming cache.
- **Real-time transfers**: SignalR hub `/hubs/transfers` with automatic
  reconnection — speed, progress, queues; global pause/resume/stop and
  per-item pause/cancel/retry; activity badge in the bottom bar.
- **Background audio player** (Media3 + MediaSession: notification, headset
  support, queue, shuffle, repeat) with a persistent mini player.
- **Server playlists**: create, delete, reorder, add tracks from the file
  browser, play whole playlists (URLs resolved in parallel) and download them
  to the server.
- **Video streaming** (ExoPlayer, fullscreen) for both Telegram-hosted and
  server-local files.
- **Settings**: server info, Telegram account (logout), and server
  configuration (simultaneous downloads, parallel chunks, connections per
  download, hashing).

## Performance

- Independent calls run **in parallel** (`async`/`awaitAll`), e.g. info +
  user + config in Settings, or resolving playlist track URLs (with bounded
  concurrency).
- Transfer progress arrives via **push** (SignalR) instead of polling.
- Loading spinners on every screen and no main-thread blocking (all IO runs
  on coroutines).

## Build

Requirements: JDK 17+ and Android SDK 35.

```bash
./gradlew assembleDebug
```

## Release (CI)

`.github/workflows/release.yml` builds and signs the APK on every push to
`main` and publishes a GitHub Release `v<versionName>`.

Repository secrets:

| Secret | Description |
| --- | --- |
| `KEYSTORE_BASE64` | Base64-encoded `.jks` keystore. |
| `KEYSTORE_PASSWORD` | Keystore password. |
| `KEY_PASSWORD` | Key password. |
| `KEY_ALIAS` | (Optional) key alias; defaults to `tfm`. |

To bump the version: edit `versionCode` / `versionName` in
`app/build.gradle.kts`.
