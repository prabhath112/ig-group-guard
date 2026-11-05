# Changelog

All notable changes to IG Group Guard. Versions match `versionName` in `app/AndroidManifest.xml`
and the `vX.Y.Z` release tags. The release workflow publishes the section for the tagged version
as the GitHub Release notes.

## [Unreleased]

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
