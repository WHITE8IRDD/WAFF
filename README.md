# WAFF

WAFF is an Android media-downloader project in active development. The app can download publicly reachable direct HTTPS image, audio, and video files through Android's DownloadManager after checking the response MIME type. It also has an experimental extraction flow: NewPipe handles recognized services, while bundled yt-dlp is used as a fallback for public Instagram, TikTok, and Facebook links. WAFF lists returned formats, saves available subtitles or thumbnails, and queues a selected direct stream. Availability varies by service; private, DRM-protected, login-gated, or rate-limited media is not promised.

[![Android CI](https://github.com/WHITE8IRDD/WAFF/actions/workflows/build.yml/badge.svg)](https://github.com/WHITE8IRDD/WAFF/actions/workflows/build.yml) · [GitHub Releases](https://github.com/WHITE8IRDD/WAFF/releases)

## Current status

- [x] Android/Compose app shell (API 26 minimum, target/compile SDK 36) and monochrome navigation
- [x] Share-text intent handling and supported-platform URL recognition
- [x] Direct HTTPS media-file preflight using HTTP response MIME type
- [x] Real direct-file transfer via Android DownloadManager to `Downloads/WAFF`
- [x] Live queue status/byte progress, cancellation, and retry of failed system downloads
- [x] History search, open/share actions, and confirmed removal of completed downloads
- [x] URL, hostname, MIME, filename-safety, and filter-preserving media-selection unit tests
- [x] Room transfer metadata implementation
- [ ] Room DAO emulator integration test (manual CI dispatch only; successful run pending)
- [x] NewPipe-recognized service metadata and format-selection UI (compiled in CI; live-service/device verification pending)
- [x] Save extracted subtitle tracks and thumbnails as separate files (compiled in CI; runtime verification pending)
- [x] yt-dlp fallback wired for Instagram, TikTok, and Facebook public pages (build/runtime verification pending)
- [x] Separate audio/video format selection, foreground merge queue, and MKV mux workflow (build verified; device/runtime verification pending)
- [x] Room-persisted transfer metadata joined to DownloadManager status and completed-file actions
- [ ] User-selected storage, explicit pause/resume, verified post-transfer content checks, and storage migrations
- [x] Embedded WebView browsing with DownloadListener-based direct-file offers
- [ ] General page-resource inspection, platform-specific media picker, and engine updates
- [ ] Device review, product-wide accessibility/performance review, and stable release
- [x] Preview APK build/release automation (unsigned release variant; debug build included for evaluation)

The Browser tab uses Android WebView for HTTPS browsing and offers media downloads only when WebView reports an image/audio/video MIME type. It blocks non-HTTPS navigation, file/content access, mixed content, and third-party cookies; it does not inspect protected page internals. The direct-file workflow requires an HTTPS server that supports HEAD and returns an image/audio/video MIME type; ambiguous responses are rejected. The experimental NewPipe/yt-dlp extraction path presents returned formats and can queue separate video/audio formats for background MKV muxing. Merge compilation has passed CI, but playback and extraction behavior still require device/live-service verification. Direct-file transfers use Android DownloadManager; merge jobs use a Room-backed WorkManager queue and app-private external storage. Pause/resume behavior and notifications depend on Android and the device.

## Architecture

URL routing, MIME classification, filename rules, and selection-preservation behavior are unit-tested. Room persists metadata for both DownloadManager transfers and managed merge jobs. NewPipe Extractor and yt-dlp resolve media; FFmpeg is bundled for background muxing. Compatibility and access depend on upstream services and must be verified on-device. No ad/analytics SDKs or developer telemetry are included.

## Build

Requirements: Android Studio with JDK 17, Android SDK Platform 36 and Build Tools 36.0.0. Open the repository in Android Studio and sync the Gradle project; or, with a compatible Gradle installation, run:

```sh
gradle :app:testDebugUnitTest
gradle :app:assembleDebug :app:assembleRelease
```

The generated outputs are under `app/build/outputs/apk/`. No wrapper is included yet because the initial environment did not have Java/Gradle available to generate it. CI provisions Java and Android SDK and runs unit tests and debug/release builds. Preview releases are deliberately marked pre-release; the release APK is unsigned, while the CI debug APK uses a temporary debug key and is for evaluation only.

## Privacy and permissions

The app requests Internet access for link preflight and transfers. It does not request broad storage, notification, or account permissions; Android DownloadManager handles the direct-file destination. No cookies or authentication material are collected. Future permissions will be justified and documented before introduction.

## Licensing

WAFF is distributed under GNU GPL version 3 or later; see `LICENSE`. NewPipe Extractor is a GPL-3.0-or-later dependency and is documented in `THIRD_PARTY_NOTICES.md`. Platform terms and access restrictions remain applicable. WAFF is not affiliated with YouTube, Instagram, TikTok, or Facebook.
