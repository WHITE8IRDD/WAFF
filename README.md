# WAFF

WAFF is an Android media-downloader project in active development. The current app can download publicly reachable direct HTTPS image, audio, and video files through Android's DownloadManager after checking the response MIME type. **It does not extract media from platform pages (including YouTube, Instagram, TikTok, or Facebook).** This is an early milestone, not a stable release.

[![Android CI](https://github.com/WHITE8IRDD/WAFF/actions/workflows/build.yml/badge.svg)](https://github.com/WHITE8IRDD/WAFF/actions/workflows/build.yml) · [GitHub Releases](https://github.com/WHITE8IRDD/WAFF/releases)

## Current status

- [x] Android/Compose app shell (API 26 minimum, target/compile SDK 36) and monochrome navigation
- [x] Share-text intent handling and supported-platform URL recognition
- [x] Direct HTTPS media-file preflight using HTTP response MIME type
- [x] Real direct-file transfer via Android DownloadManager to `Downloads/WAFF`
- [x] Live queue status/byte progress and completed-transfer list backed by DownloadManager
- [x] Initial URL and direct-media MIME unit tests; CI workflow
- [ ] Platform-page extraction and format selection
- [ ] Room-backed queue/history, user-selected storage, retry/pause controls, and verified media opening
- [ ] Browser media detection, platform-specific media picker, and engine updates
- [ ] Device review, release APKs, ABI validation, and release automation

Browser and Settings remain intentionally limited early states. URL recognition is not evidence that a platform can be extracted. The direct-file workflow requires a server that supports an HTTPS HEAD request and returns an image/audio/video MIME type; it rejects page URLs and ambiguous content rather than saving a preview or webpage as media. Transfers are managed by Android DownloadManager, so pause/resume behavior and notifications depend on Android and the device.

## Architecture

The initial code keeps URL routing in a small, independently tested core model and the Compose application shell in `app`. The project will add extraction, transfer, persistence, and storage modules only alongside working, testable functionality. No ad/analytics SDKs or developer telemetry are included.

## Build

Requirements: Android Studio with JDK 17, Android SDK Platform 36 and Build Tools 36.0.0. Open the repository in Android Studio and sync the Gradle project; or, with a compatible Gradle installation, run:

```sh
gradle :app:testDebugUnitTest
gradle :app:assembleDebug :app:assembleRelease
```

The generated outputs are under `app/build/outputs/apk/`. No wrapper is included yet because the implementation environment did not have Java/Gradle available to generate and verify one. CI provisions Java and Android SDK and performs unit tests and debug/release builds. No APK or release has been built or published as part of this foundation milestone.

## Privacy and permissions

The current manifest requests Internet access for future network functionality. It does not request broad storage, notification, or account permissions. No extraction or network transfer implementation is present. Future permissions and authentication capabilities will be documented before they are introduced.

## Licensing

The application source is MIT-licensed. Third-party dependencies and future extraction engines will be reviewed and documented before integration; platform terms and access restrictions remain applicable. WAFF is not affiliated with YouTube, Instagram, TikTok, or Facebook.
