# Changelog

All notable changes to IG Group Guard. Versions match `versionName` in `app/AndroidManifest.xml`
and the `vX.Y.Z` release tags. The release workflow publishes the section for the tagged version
as the GitHub Release notes.

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
