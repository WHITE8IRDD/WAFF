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
- [x] Room DAO emulator integration test wired into pull-request CI (latest emulator run pending)
- [x] NewPipe-recognized service metadata and format-selection UI (compiled in CI; live-service/device verification pending)
- [x] Save extracted subtitle tracks and thumbnails as separate files (compiled in CI; runtime verification pending)
- [x] yt-dlp fallback wired for Instagram, TikTok, and Facebook public pages (build/runtime verification pending)
- [ ] Separate audio/video stream merging and full regression matrix
- [x] Room-persisted transfer metadata joined to DownloadManager status and completed-file actions
- [ ] User-selected storage, explicit pause/resume, verified post-transfer content checks, and storage migrations
- [x] Embedded WebView browsing with DownloadListener-based direct-file offers
- [ ] General page-resource inspection, platform-specific media picker, and engine updates
- [ ] Device review, product-wide accessibility/performance review, and stable release
- [x] Preview APK build/release automation (unsigned release variant; debug build included for evaluation)

The Browser tab uses Android WebView for ordinary browsing and offers a Download action only when WebView reports a downloadable image/audio/video MIME type. It does not inspect protected page internals. The direct-file workflow requires an HTTPS server that supports HEAD and returns an image/audio/video MIME type; ambiguous responses are rejected rather than saved as media. The experimental NewPipe path uses the extractor's service registry to present streams it can resolve; it does not merge separate audio/video streams, and video-only choices are explicitly identified. Transfers are managed by Android DownloadManager, so pause/resume behavior and notifications depend on Android and the device.

## Architecture

URL routing, MIME classification, filename rules, and selection-preservation behavior are independently tested. Room persists WAFF transfer metadata while Android DownloadManager remains authoritative for transfer state and file URIs. NewPipe Extractor and yt-dlp are used on-device for metadata and stream resolution; stream compatibility and access remain dependent on upstream services. The optional FFmpeg runtime is not yet bundled, so separate audio/video streams are not merged. No ad/analytics SDKs or developer telemetry are included.

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
