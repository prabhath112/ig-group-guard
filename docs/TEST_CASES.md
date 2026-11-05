# Test cases

Target: Instagram multi-select share sheet, button "Send to new group chat (N)"
(`com.instagram.android:id/direct_send_to_group_button_vertical`, verified on
Instagram 455.0.0.0.47, Android 16, 2026-11-05).

## Automated (`test/e2e.py`)

Fixture mode runs against `fixture/`, a stand-in app that uses Instagram's package name and the
same view ids, and logs every click (`FIXTURE_CLICK <name>`). The service logs its overlay state
(`GroupGuard: overlay l t r b` / `overlay hidden` / `blocked tap`).

| ID   | Case | Steps | Expected |
|------|------|-------|----------|
| TC01 | Service enabled and connected | Install, enable service | Listed in `enabled_accessibility_services`; "connected" logged |
| TC02 | Overlay covers button exactly | Open share sheet | Overlay rect == button bounds from `uiautomator dump` |
| TC03 | Taps blocked (centre, left edge, right edge) | Tap 3 points on button | No `FIXTURE_CLICK group`; 3x "blocked tap" |
| TC04 | "Send separately" still works | Tap it | `FIXTURE_CLICK separate` |
| TC05 | Button disappears / reappears | Toggle visibility twice | Overlay hidden, then back on the new bounds |
| TC06 | Layout moves (keyboard, scroll, resize) | Move buttons | Overlay follows to new bounds |
| TC07 | Leave Instagram | Press Home | Overlay hidden (no stray grey box over launcher) |
| TC08 | Instagram renames the view id | Fixture `mode=textonly` | Found by label text; tap blocked |
| TC09 | Landscape | Rotate to 90° | Overlay matches, tap blocked |
| TC00 | No consent yet | Fresh install, open fixture | Service does not draw anything |
| TC11 | Consent flow | Open app, tap Agree | Disclosure shown; status screen after Agree |
| TC12 | Service switched off/on 10x with button on screen | Toggle setting | No crash; overlay back (guards `BadTokenException`; the crash itself did not reproduce on demand, so this test is not proven to catch it) |
| TC10 | Stability | Whole run | No crash of `io.github.prabhath112.iggroupguard` in crash buffer |
| RT01 | Real Instagram, no taps | Open real share sheet, 2+ selected | Overlay rect == real button bounds |

## Manual (real Instagram)

Do these on a real phone with a test account or with recipients who know you are testing:
a tap that gets through creates a real group.

| ID   | Case | Expected |
|------|------|----------|
| MT01 | Share a reel, select 2+ people | Group button grey, reads "Group send disabled" |
| MT02 | Tap the grey button | Toast "Blocked: …"; sheet stays open; no group in Direct inbox |
| MT03 | Tap "Send separately" | Each person gets a separate DM; no group created |
| MT04 | Select 1 person | Normal single send works; no overlay drawn over anything else |
| MT05 | Type in "Write a message…" (keyboard up) | Overlay moves with the button or hides if it is covered |
| MT06 | Swipe the sheet down / close it | Overlay disappears with the sheet |
| MT07 | Top-right "New group" icon | Still works (deliberate action, not blocked) |
| MT08 | Turn the service off | Button works normally again |
| MT09 | Reboot phone | Service comes back on its own; MT01 passes |
| MT10 | Phone language not English | MT01 passes (id match), note result |
| MT11 | Dark and light theme | Overlay readable in both |
| MT12 | Large font / display size | Overlay still covers the whole button |
| MT13 | Gesture nav and 3-button nav | Overlay position correct in both |
| MT14 | TalkBack on | Known limitation: double-tap bypasses overlay; record result |

## Device matrix

| Device / emulator | Android (API) | Instagram | Mode | Result |
|-------------------|---------------|-----------|------|--------|
| OnePlus CPH2707 (ColorOS) | 16 (36) | 455.0.0.0.47 | RT01 + manual + screen recordings | Overlay window frame == button `[48,2554][1224,2685]`; static banner accepted by user after screen-recording analysis (2026-11-05) |
| Emulator gg_api35 | 15 (35) | fixture | TC00-TC12 | 13/13 pass (2026-11-05) |
| Emulator gg_api30 | 11 (30) | fixture | TC00-TC12 | 13/13 pass (2026-11-05) |
| Emulator gg_api26 | 8.0 (26, minSdk) | fixture | TC00-TC12 | 13/13 pass (2026-11-05) |

Regression proof for TC05: with the 500 ms re-check removed, API 26 fails TC05
(`hidden=False stale_block=True`: a stale overlay covered "Send separately"). Restored: passes.

Add a row for each new phone (Samsung One UI, Xiaomi HyperOS, Pixel) after running MT01-MT09.

## Findings from real-device runs

- ColorOS blocks `pm clear` for the shell; the test reinstalls instead.
- uiautomator cannot dump while a reel video plays ("could not get idle state"); RT01 uses the
  service log + window manager frame + a screenshot instead.
- On Android 16 (CPH2707) uiautomator reports the long disclosure body TextView with empty text
  although it is drawn; TC11 still checks the body text, so it fails there. Open item.
- Android 11 keeps a force-stopped accessibility service in "Crashed services" and only re-binds
  it if the app process is alive when the setting is toggled; the status screen now checks the
  running services list instead of the setting.
- Smoothness (screen recording at 60 fps): Instagram fades/slides the button in over ~330 ms;
  following it or animating our own slide produced visible steps, so the banner is shown
  standing still at the remembered spot.
