# WAFF

WAFF is an Android media-downloader project in its foundation stage. The long-term goal is a private, monochrome app for identifying eligible media, selecting real formats, and managing verified downloads. **This repository does not yet extract or download media.** The current app is an early Compose foundation, not a usable downloader or a stable release.

[![Android CI](https://github.com/WHITE8IRDD/WAFF/actions/workflows/build.yml/badge.svg)](https://github.com/WHITE8IRDD/WAFF/actions/workflows/build.yml) · [GitHub Releases](https://github.com/WHITE8IRDD/WAFF/releases)

## Current status

- [x] Android/Compose project skeleton (API 26 minimum, target/compile SDK 36)
- [x] Monochrome app shell, four primary destinations, settings entry point
- [x] Share-text intent registration and host-based supported-platform URL routing
- [x] URL-router unit tests and initial CI workflow
- [ ] Extraction, format selection, and actual downloads
- [ ] Queue persistence, storage, history, and notifications
- [ ] Browser media detection, platform-specific media picker, and engine updates
- [ ] Device review, release APKs, ABI validation, and release automation

The navigation destinations other than Home are deliberately honest early states; no download controls or simulated success are presented. URL recognition is not evidence that a platform can be extracted.

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
