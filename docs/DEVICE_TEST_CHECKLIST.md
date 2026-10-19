# WAFF device-test checklist

Physical-device verification required **before any WAFF 1.0 release**. Keep every GitHub release
marked *prerelease* until all items below pass. Record results (pass/fail + device + Android version)
against the current preview tag.

## 1. YouTube Quick Download

- [ ] In the YouTube app, Share a video → **WAFF Quick Download**.
- [ ] You are returned to YouTube immediately; no WAFF UI stays open.
- [ ] The download runs in the background and appears under **WAFF → Queue** while active.
- [ ] On completion a clear **"Download successful"** notification appears with the file name, and
      tapping it opens WAFF.
- [ ] The file lands in `Downloads/WAFF/` and plays correctly.
- [ ] Failure case (e.g. a restricted/removed video): a **"YouTube Quick Download failed"**
      notification appears instead of silence.

Expected notification-permission behavior:

- Opening WAFF for the first time requests notification permission on Android 13+.
- Permission granted → WAFF success/failure notifications appear.
- Permission denied → WAFF's own notices are skipped, but Android's DownloadManager notification
  still shows transfer progress and completion.
- On Android 12+, the *"Preparing your YouTube download"* progress notification is best-effort and
  may not appear while WAFF is backgrounded (foreground-service start restrictions); the completion
  notification must still appear.

## 2. Instagram Quick Download (selection/preview)

Instagram extraction/download itself is confirmed working — regressions here are **not** expected.

- [ ] Share an Instagram post → **WAFF Quick Download** → bottom overlay appears with per-item
      preview thumbnails.
- [ ] Single image post → 1 item labeled **Image**.
- [ ] Single video post → 1 item labeled **Video** (quality dropdown when several formats exist).
- [ ] Mixed carousel → every slide listed in order with the correct Image/Video label.
- [ ] **Select all / Deselect all** toggles every item correctly.
- [ ] **Download selected (n)** queues exactly n items, then closes the overlay.

## 3. Instagram saved file types

For each downloaded item, confirm the saved file in `Downloads/WAFF/` has the right *actual* type
(open it in a gallery/player; do not trust the extension alone):

- [ ] Single image → image file
- [ ] Single video → video file
- [ ] Carousel of images → one image file per slide
- [ ] Carousel of videos → one video file per slide
- [ ] Mixed carousel → correct type per slide
- [ ] History → *File type* row matches the actual file.

## 4. History

- [ ] Tapping an entry opens *Download details*; every populated row's copy button copies that value.
- [ ] **Select all** selects every listed entry; the same button becomes **Deselect all**.
- [ ] **Delete selected** → confirm dialog states files are **not** deleted → after confirming, the
      files still exist in `Downloads/WAFF/` and only history entries are gone.
- [ ] **Clear all** → same guarantee for every completed entry.
- [ ] Note: the per-row trash button ("Delete downloaded file") *does* delete the file after its own
      confirmation dialog — this is intended and distinct from history removal.

## 5. Quality selection, clear input, navigation, responsiveness

- [ ] *Choose quality* sheet lists formats with sensible labels; a selection stays highlighted
      through download start.
- [ ] The ✕ **Clear input** button empties the link field and resets picker state.
- [ ] Home / Browser / Queue / History / Settings navigation works while a background download runs.
- [ ] Queue and History scroll smoothly with many entries; progress updates live during downloads.
- [ ] Performance has not been profiled — note any jank, ANR, or slow extraction as observations,
      not as known issues.

## 6. Release hygiene

- [ ] The current preview release is still marked **prerelease** on GitHub.
- [ ] Do **not** publish WAFF 1.0 until every item above passes.
