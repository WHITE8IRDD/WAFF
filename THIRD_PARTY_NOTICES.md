# Third-party notices

## NewPipe Extractor 0.26.5

- Source: https://github.com/TeamNewPipe/NewPipeExtractor/tree/v0.26.5
- Artifact: `com.github.teamnewpipe:NewPipeExtractor:0.26.5` (JitPack)
- License: GNU GPL version 3 or later; see `LICENSE`.
- Supported upstream services verified from that release's README: YouTube, SoundCloud, media.ccc.de, PeerTube (no P2P), and Bandcamp. This does **not** add Instagram, TikTok, or Facebook extraction.
- Android integration requires core-library desugaring below API 33. WAFF enables the upstream-documented NIO desugaring artifact and R8 rules.
- This library is a community extractor, not an official Google API. Availability is subject to upstream changes, network conditions, throttling, and the services' terms/access restrictions. Login-protected/private media and DRM are not promised.

The application is distributed under GPL-3.0-or-later to remain compatible with the bundled extractor. No ad or analytics SDKs are used.
