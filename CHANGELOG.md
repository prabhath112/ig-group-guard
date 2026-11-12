# Changelog

All notable changes to IG Group Guard. Versions match `versionName` in `app/AndroidManifest.xml`
and the `vX.Y.Z` release tags. The release workflow publishes the section for the tagged version
as the GitHub Release notes.

## [Unreleased]

## [2.1.1] - 2026-11-12

### Fixed
- Instagram felt laggy and the banner sometimes appeared late. The service ran a full scan
  (`getWindows()` over every interactive window, plus a node-tree walk) on every Instagram
  accessibility event, with no coalescing. During event bursts (video playback, list thumbnails,
  a video-call picture-in-picture adding windows) this saturated the main thread, so the refresh
  that should place the banner backed up behind the burst. Measured on CPH2707: about 6-15% CPU
  while Instagram was foreground, dropping to near 0% after the fix.
- Banner sometimes appeared seconds late on a fresh share sheet. The watch-poll only re-armed when
  the foreground app was confirmed to be Instagram; a video frame or transition that returned no
  active-window root (reported as "unknown") stopped the poll and left the service event-only, so if
  Instagram sent no event we accept the share sheet stayed uncovered for a few seconds. The poll now
  keeps running unless a different app is confirmed in front.

### Changed
- Accessibility events are now coalesced: a burst collapses to a single scan on the next loop turn.
- Polls and the per-frame follow scan only Instagram's active window; the heavier all-windows
  `getWindows()` scan runs only on real Instagram events. Keeps the continuous poll cheap.
- The group button is looked for in Instagram's active window first (one tree); the heavier
  `getWindows()` over all windows is only a fallback. This is the main CPU and latency saver.
- The fast re-check after the group button vanishes (keyboard open on the share sheet) now lasts
  6 s instead of 2 s, so closing the keyboard re-covers the button quickly even after typing a
  longer message. Scoped to a share sheet that was just open, so general scrolling pays nothing.

## [2.1.0] - 2026-11-06

### Fixed
- Banner stuck for about a second at the wrong spot after typing a message: with the keyboard up,
  the keyboard position overwrote the remembered spot, and when the keyboard closed the banner sat
  there until the button settled. The remembered spot now only comes from the share sheet's
  opening slide; other movement (keyboard, scrolling) hides the banner while moving (taps stay
  blocked) and fades it in once the button stops (found with a screen recording on CPH2707).

### Added
- **Pause for banking** (issue #8): some banking and payment apps refuse to start while any
  accessibility service is on. New "Pause for banking" button, a Quick Settings tile (tap to pause,
  tap again to open Accessibility settings) and in-app steps for the volume-key accessibility
  shortcut.

### Changed
- The accessibility service now receives events only from Instagram; leaving Instagram is noticed
  by a 300 ms re-check while the banner is shown, and for 2 s after the button vanishes (it does
  while the keyboard closes) the service keeps checking so the banner comes back.
- The button's resting spot is learned only from a real slide-in and saved locally, so the first
  opening after a restart (for example after Pause for banking) is instant too.

## [2.0.0] - 2026-11-05

### Added
- **Ask me first** mode (choose it in the app; **Block it** stays the default). Tapping the
  covered button opens IG Group Guard's own popup, "Create a group chat?", showing how many people
  are selected. **Create group** uncovers Instagram's button for the current share screen only;
  **Cancel** or tapping outside keeps it covered. The next share asks again.
- Website and README explain the Play Protect block on sideloaded Accessibility apps (India and
  some other countries) and the ways to install.

### Changed
- Release files carry the version: `ig-group-guard-release-vX.Y.Z.apk` and `.aab`.
- Website built by Vercel's GitHub integration; mirror on GitHub Pages; CSS moved out of the
  pages so the Content-Security-Policy no longer allows inline styles.
- CI: E2E consent test retries when a system dialog covers the app on a fresh emulator;
  smoke tests retry while a new deployment propagates.
- The app stores one more local setting (Block it / Ask me first); privacy policy updated.

## [1.2.0] - 2026-11-05

### Added
- Accessibility service that covers Instagram's "Send to new group chat" share-sheet button with a
  grey, tap-swallowing banner; "Send separately" keeps working.
- Prominent disclosure and consent screen; the service does nothing until the user agrees.
- Status screen that checks the service is really running (Android 11 force-stop case), privacy
  policy link, and "Withdraw consent and delete data".
- Banner appears standing still at the button's remembered spot while the share sheet slides in.
- Signed APK and Android App Bundle, website with download link, privacy policy and terms.

### Fixed during testing
- Android 8 crash (`layoutInDisplayCutoutMode` is API 28+).
- Overlay left on screen after leaving Instagram.
- Android 8 stale overlay covering "Send separately" (no event when a view goes GONE).
- `BadTokenException` when the service is switched off while the sheet is open.
