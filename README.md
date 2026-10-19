# WAFF

WAFF is an Android media-downloader project in active development. The current app can download publicly reachable direct HTTPS image, audio, and video files through Android's DownloadManager after checking the response MIME type. **It does not extract media from platform pages (including YouTube, Instagram, TikTok, or Facebook).** This is an early milestone, not a stable release.

[![Android CI](https://github.com/WHITE8IRDD/WAFF/actions/workflows/build.yml/badge.svg)](https://github.com/WHITE8IRDD/WAFF/actions/workflows/build.yml) · [GitHub Releases](https://github.com/WHITE8IRDD/WAFF/releases)

## Current status

- [x] Android/Compose app shell (API 26 minimum, target/compile SDK 36) and monochrome navigation
- [x] Share-text intent handling and supported-platform URL recognition
- [x] Direct HTTPS media-file preflight using HTTP response MIME type
- [x] Real direct-file transfer via Android DownloadManager to `Downloads/WAFF`
- [x] Live queue status/byte progress, cancellation, and retry of failed system downloads
- [x] History search, open/share actions, and confirmed removal of completed downloads
- [x] URL, hostname, MIME, and filename-rule unit tests
- [ ] Room DAO integration test on the CI Android emulator (workflow added; awaiting pass)
- [ ] Platform-page extraction and format selection
- [x] Room-persisted transfer metadata joined to DownloadManager status and completed-file actions
- [ ] User-selected storage, explicit pause/resume, verified post-transfer content checks, and storage migrations
- [x] Embedded WebView browsing with DownloadListener-based direct-file offers
- [ ] General page-resource inspection, platform-specific media picker, and engine updates
- [ ] Device review, product-wide accessibility/performance review, and stable release
- [x] Preview APK build/release automation (unsigned release variant; debug build included for evaluation)

The Browser tab uses Android WebView for ordinary browsing and offers a Download action only when WebView reports a downloadable image/audio/video MIME type. It does not inspect protected page internals or extract platform posts. The direct-file workflow requires an HTTPS server that supports HEAD and returns an image/audio/video MIME type; ambiguous responses are rejected rather than saved as media. Transfers are managed by Android DownloadManager, so pause/resume behavior and notifications depend on Android and the device.

## Architecture

URL routing, MIME classification, and filename rules are independently tested. Room persists WAFF transfer metadata while Android DownloadManager remains authoritative for transfer state and file URIs. Platform extractors are not integrated. No ad/analytics SDKs or developer telemetry are included.

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

The application source is MIT-licensed. Third-party dependencies and future extraction engines will be reviewed and documented before integration; platform terms and access restrictions remain applicable. WAFF is not affiliated with YouTube, Instagram, TikTok, or Facebook.
