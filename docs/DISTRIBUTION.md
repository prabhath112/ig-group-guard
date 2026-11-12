# Distribution

IG Group Guard uses the Accessibility API, which Google's India fraud protection blocks for
**sideloaded** apps (payment apps then refuse to run alongside it). Apps installed from a trusted
store are not blocked. So the install source matters.

## GitHub Releases (now)
Every `vX.Y.Z` tag publishes `ig-group-guard-release-vX.Y.Z.apk` + `.aab` + `SHA256SUMS`, signed
with the upload key, with build provenance. Direct download, but counts as sideloaded.

## IzzyOnDroid (free, planned)
IzzyOnDroid distributes the developer-signed APK straight from GitHub Releases and reads the
listing from `fastlane/metadata/android/en-US/` in this repo (title, descriptions, changelogs,
icon, feature graphic, screenshots). No rebuild on their side.

To request inclusion:
1. Make sure the latest `vX.Y.Z` GitHub Release has the APK attached (the release workflow does this).
2. Open an inclusion request issue at <https://gitlab.com/IzzyOnDroid/repo> (or their Codeberg
   mirror) with the repo URL `https://github.com/prabhath112/ig-group-guard`.
3. Note in the request that the Accessibility service is the app's single declared feature
   (covering one Instagram button), there are no trackers, ads or internet permission, and it is
   MIT-licensed. IzzyOnDroid flags accessibility services and expects this justification.

Caveat: an IzzyOnDroid install is still not a Google Play install, so Google's payment fraud check
may continue to treat the app as untrusted. Only Google Play distribution is confirmed to clear
that block.

## Google Play (confirmed fix for the payment block, $25 one-time)
Upload `ig-group-guard-release-vX.Y.Z.aab`. Requires the accessibility declaration (see
`play-data-safety.md`) and, for a new personal account, a 12-tester / 14-day closed test.

## F-Droid main repo (optional, more work)
F-Droid builds from source on its own server and signs the result. This project builds without
Gradle (`build.sh`), which does not fit F-Droid's standard templates, so it would need a custom
build recipe in their metadata. IzzyOnDroid is the lower-effort route.
