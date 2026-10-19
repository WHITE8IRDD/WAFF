# Third-party notices

## NewPipe Extractor 0.26.5

- Source: https://github.com/TeamNewPipe/NewPipeExtractor/tree/v0.26.5
- Artifact: `com.github.teamnewpipe:NewPipeExtractor:0.26.5` (JitPack)
- License: GNU GPL version 3 or later; see `LICENSE`.
- Supported upstream services verified from that release's README: YouTube, SoundCloud, media.ccc.de, PeerTube (no P2P), and Bandcamp. This does **not** add Instagram, TikTok, or Facebook extraction.
- Android integration requires core-library desugaring below API 33. WAFF enables the upstream-documented NIO desugaring artifact and R8 rules.
- This library is a community extractor, not an official Google API. Availability is subject to upstream changes, network conditions, throttling, and the services' terms/access restrictions. Login-protected/private media and DRM are not promised.

## youtubedl-android 0.18.1

- Source: https://github.com/yausername/youtubedl-android/tree/0.18.1
- Artifact: `io.github.junkfood02.youtubedl-android:library:0.18.1` (Maven Central)
- License: GNU GPL version 3; see the upstream `LICENSE`. The bundled yt-dlp, Python runtime, and QuickJS components retain their respective upstream licenses; review their notices in the upstream source before redistributing builds.
- Bundles yt-dlp, Python, and QuickJS native/runtime components for on-device metadata and stream extraction across yt-dlp-supported public sites. This is used as a fallback for Instagram, TikTok, and Facebook links; service/site changes can break extraction, and private, DRM-protected, or login-gated content is not promised.
- The optional FFmpeg module is not included in this phase, so WAFF does not yet merge separate streams.

The application is distributed under GPL-3.0-or-later to remain compatible with the bundled extractors. No ad or analytics SDKs are used.
