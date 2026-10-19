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
- [x] Room integration-test sources compiled in CI; emulator execution is manual-only and remains unverified
- [x] NewPipe-recognized service metadata and format-selection UI (compiled in CI; live-service/device verification pending)
- [x] Save extracted subtitle tracks and thumbnails as separate files (compiled in CI; runtime verification pending)
- [x] yt-dlp fallback wired for Instagram, TikTok, and Facebook public pages (build/runtime verification pending)
- [x] Separate audio/video selection, persistent foreground merge queue, and MKV mux implementation (runtime/device verification pending)
- [x] Room-persisted transfer metadata joined to DownloadManager status and completed-file actions
- [x] Room 1→2 data migration and pause/resume controls for managed merge jobs (runtime testing pending)
- [ ] User-selected storage, DownloadManager pause/resume controls, and verified post-transfer content checks
- [x] Embedded WebView browsing with DownloadListener-based direct-file offers
- [ ] General page-resource inspection, platform-specific media picker, and engine updates
- [ ] Device review, product-wide accessibility/performance review, and stable release
- [x] Preview APK build/release automation (unsigned release variant; debug build included for evaluation)

The Browser tab uses Android WebView, upgrades HTTP navigations to HTTPS, blocks file/content access and mixed content, disables third-party cookies, and offers downloads only for reported image/audio/video MIME types. It does not inspect protected page internals. Users can clear WebView cookies, cache, and form data in Settings. The direct-file workflow requires an HTTPS server that supports HEAD and returns a matching image/audio/video MIME type. The experimental NewPipe/yt-dlp path presents returned formats, identifies video-only tracks, and can queue separate audio/video formats for background MKV muxing. Compilation and pull-request CI builds pass; extraction and mux playback still require live-service and device verification. Direct-file transfers use Android DownloadManager; managed merge jobs use Room and WorkManager. Android controls transfer notifications and DownloadManager pause/resume behavior.

## Architecture

URL routing, MIME classification, filename rules, and selection-preservation behavior are unit-tested. Room stores both DownloadManager metadata and managed merge-job state; Android DownloadManager remains authoritative for direct transfer status and file URIs. NewPipe Extractor and yt-dlp resolve media, while the bundled FFmpeg runtime muxes separate streams. Compatibility depends on upstream services, and merge playback remains unverified on-device. No ad/analytics SDKs or developer telemetry are included.

## Build

Requirements: Android Studio with JDK 17, Android SDK Platform 36 and Build Tools 36.0.0. Open the repository in Android Studio and sync the Gradle project; or, with a compatible Gradle installation, run:

```sh
gradle :app:testDebugUnitTest
gradle :app:assembleDebug :app:assembleRelease
```

The generated outputs are under `app/build/outputs/apk/`. No wrapper is included yet because the initial environment did not have Java/Gradle available to generate it. CI provisions Java and Android SDK and runs unit tests and debug/release builds. Preview releases are deliberately marked pre-release; the release APK is unsigned, while the CI debug APK uses a temporary debug key and is for evaluation only.

## Privacy and permissions

The app requests Internet access and foreground data-sync service access for transfers. It does not request broad storage, notification, or account permissions. Android DownloadManager handles direct-file destinations; merged outputs are stored in app-specific external storage. WAFF does not read or export WebView cookies; Android WebView can retain first-party site data until cleared in Settings. The extractor does not promise access to private, login-gated, or DRM-protected media.

## Licensing

WAFF is distributed under GNU GPL version 3 or later; see `LICENSE`. NewPipe Extractor is a GPL-3.0-or-later dependency and is documented in `THIRD_PARTY_NOTICES.md`. Platform terms and access restrictions remain applicable. WAFF is not affiliated with YouTube, Instagram, TikTok, or Facebook.
