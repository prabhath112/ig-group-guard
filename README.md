<p align="center"><img src="assets/logo.svg" width="128" alt="IG Group Guard logo: three people behind a no sign"></p>

# IG Group Guard

Android app that stops accidental group chats when sharing on Instagram.

When you share a reel or post to several people, Instagram shows **"Send to new group chat (N)"**
right under "Send separately". One stray tap creates a group of everyone you selected, without
confirmation, and exposes them to each other. IG Group Guard greys that button out and makes it
untappable. "Send separately" and Instagram's deliberate "New group" icon keep working.

IG Group Guard is an independent app. It is not affiliated with, endorsed by, or sponsored by Instagram
or Meta. Instagram is a trademark of its owner.

## Privacy in one line

No internet permission, no analytics, no ads, no SDKs, no accounts. The only thing stored is a
yes/no consent flag. Details: [privacy policy](docs/privacy-policy.md).

## How it works

- An `AccessibilityService` reads the layout of Instagram's share screen on the device (view ids,
  bounds, button labels) only to find one button.
- It matches `com.instagram.android:id/direct_send_to_group_button_vertical`, with the label text
  "Send to new group chat" as a fallback.
- It draws a `TYPE_ACCESSIBILITY_OVERLAY` over that button. The overlay swallows taps and
  shows a short message. This overlay type needs no "draw over other apps" permission.
- **Smooth appearance:** a separate window cannot follow another app's animation without
  visible steps (button position is readable only every 30-40 ms). So the service remembers
  where the button comes to rest and, from then on, shows the grey banner at once, standing
  still at that spot, while Instagram's button slides in underneath. A transparent part of the
  window reaches down over the moving button, so it is never tappable. The very first time
  after the service starts, it measures that spot (banner fades in when the button stops).
- The overlay is removed as soon as the button leaves the screen. While it is shown, the
  service re-checks every 500 ms, because Android 8 sends no event when a view disappears.
- Nothing happens until the user accepts the in-app disclosure (Google Play Accessibility policy).

## Install

1. Install `ig-group-guard-release.apk` from Releases (or from Google Play once published).
2. Open **IG Group Guard**, read the disclosure and tap **Agree**.
3. Tap **Open Accessibility settings** → **Installed apps** → **IG Group Guard** → **On**.
4. **Android 13+ sideload:** if the switch is greyed out, **Settings → Apps → IG Group Guard → ⋮ →
   Allow restricted settings**, then repeat step 3.
5. **Xiaomi / Oppo / OnePlus / Realme / Vivo / Samsung:** set battery use to Unrestricted (and
   allow Autostart where offered), or the system may stop the service.

## Build (no Gradle)

Needs Android SDK platform 36, build-tools 36.0.0, JDK 17+, Python 3, and
[bundletool](https://github.com/google/bundletool/releases) 1.18.3 (path in `BUNDLETOOL`).
Windows: run in Git Bash.

```bash
bash build.sh
```

| Output | Use |
|--------|-----|
| `build/ig-group-guard-debug.apk` | local testing (debug key) |
| `build/ig-group-guard-fixture.apk` | test fixture: fake Instagram share sheet |
| `build/ig-group-guard-release.apk` | GitHub Releases (upload key) |
| `build/ig-group-guard-release.aab` | Google Play upload (upload key) |

Release outputs need an upload key properties file (`GG_UPLOAD_KS`, `GG_UPLOAD_ALIAS`,
`GG_UPLOAD_PASS`), default `%USERPROFILE%/.android/ig-group-guard-upload.properties`. Keep the
keystore and that file private and backed up. They are never committed.

Store graphics: `python -I assets/render.py` renders `assets/play-icon-512.png` and
`assets/feature-graphic-1024x500.png` from `assets/logo.svg`.

## Test

```bash
python -I test/e2e.py -s <adb serial>
```

- **Emulator / phone without Instagram:** installs the fixture and runs TC00 to TC11 with real taps.
- **Phone with real Instagram:** open a share sheet with 2+ people selected, then run. No taps are
  sent to Instagram (RT01).

Results and manual checks: [docs/TEST_CASES.md](docs/TEST_CASES.md).

## Publishing

- [docs/COMPLIANCE.md](docs/COMPLIANCE.md): policy checklist and remaining developer actions
- [docs/store-listing.md](docs/store-listing.md): Play listing text and assets
- [docs/play-data-safety.md](docs/play-data-safety.md): Data safety form, Accessibility
  declaration, declaration video script
- [docs/privacy-policy.md](docs/privacy-policy.md), [docs/terms-of-service.md](docs/terms-of-service.md):
  served by GitHub Pages from `docs/`

**Play review risk:** Google Play's Device and Network Abuse policy disallows apps that "interfere
with ... other apps on the device". IG Group Guard changes part of another app's screen, with consent and
for one narrow purpose, but approval is not guaranteed. GitHub Releases is the fallback.

## Known limitations

- **TalkBack:** a TalkBack double-tap activates the node directly and bypasses the overlay.
- **Language:** the text fallback is English only; the id match works in every language.
- **Instagram updates:** if Instagram changes both the id and the label, IG Group Guard stops matching
  and the button simply works normally. Re-check with `uiautomator dump`, update
  `GroupGuardService.GROUP_BUTTON_ID`.
- **Dual apps / work profiles:** an accessibility service sees only its own user profile.
- **Not covered:** Instagram Lite, instagram.com in a browser, iOS (no API for this).

## License

[MIT](LICENSE). Logo is original artwork made for this project.
