# Google Play store listing draft

Draft for the developer to review. Check every limit and spec against the Play Console form before submitting.

## App title (max 30 characters)

`IG Group Guard: Stop Groups` (27 characters)

Note: "IG" is commonly read as Instagram. Keep the not-affiliated line in the description; if Play flags impersonation, change only the title (package can stay).

Does not contain "Instagram".

## Short description (max 80 characters)

`Covers the one-tap group chat button so you never start a group by accident.`

Count: 76 characters.

## Full description (max 4000 characters)

```
Ever selected a few people to share a post with, then tapped the wrong button and put them all in one group chat?

In Instagram's share sheet, when you pick several people, a "Send to new group chat" button appears right under "Send separately". One tap creates a group of everyone you picked, with no confirmation, and shows them to each other.

IG Group Guard covers that one button with a grey overlay. Taps on it are blocked and a short message explains why. Everything else keeps working:
- "Send separately" works as normal.
- Instagram's own "New group" icon still works when you really want a group.

PRIVACY
- No internet permission. Nothing leaves your phone.
- No data collected, stored or shared.
- Does not read your messages or contacts.
- No ads, no analytics, no accounts, no in-app purchases. Free.

HOW IT WORKS
IG Group Guard uses the Android Accessibility Service API. It reads the layout of Instagram's share screen on your phone (button names and positions) only to find that one button and cover it. It also notices when Instagram leaves the screen so it can remove the overlay. It keeps nothing.

On first launch IG Group Guard shows a clear disclosure and asks for your consent. It does nothing until you tap "Agree". You can turn it off any time in Settings > Accessibility.

IG Group Guard is not an accessibility tool for people with disabilities. It uses the Accessibility API only for the feature above.

LIMITATIONS
- Matches the English button label as a fallback.
- With TalkBack, a double-tap can still reach the button.
- An Instagram update may stop IG Group Guard from finding the button. The button then simply works as normal until IG Group Guard is updated.
- Android only. Must be turned on in Settings > Accessibility.

Open source: https://github.com/prabhath112/ig-group-guard

IG Group Guard is not affiliated with, endorsed by, or sponsored by Instagram or Meta Platforms. "Instagram" is a trademark of its owner and is used only to describe compatibility.
```

Count: about 1960 characters.

## Category

Suggested: Tools. Alternative: Communication.

## Tags

Pick from the Play Console tag list, for example: privacy, messaging tools, safety. Use only tags the Console offers.

## Contact details

- Email: prabhath.kumar2000@gmail.com
- Website: https://ig-group-guard.vercel.app/
- Privacy policy: https://ig-group-guard.vercel.app/privacy-policy
- Phone: optional, leave empty unless you want it public.

## Disclaimer line

IG Group Guard is not affiliated with, endorsed by, or sponsored by Instagram or Meta Platforms. "Instagram" is a trademark of its owner and is used only to describe compatibility.

## Screenshots to take

Phone screenshots, portrait. Avoid real names, profile photos or messages of other people: use a test account or blur them. Do not use Instagram's logo as a focus of any screenshot.

1. The prominent disclosure screen with "Agree" and "No thanks" visible.
2. Settings > Accessibility with IG Group Guard turned on.
3. Instagram share sheet with several people selected and the grey IG Group Guard overlay over "Send to new group chat", "Send separately" visible above it.
4. The overlay message shown after tapping the covered button.
5. The IG Group Guard main screen showing the service status.

## Graphic assets

| Asset | File | Spec |
|---|---|---|
| App icon | `assets/play-icon-512.png` | 512 x 512 px PNG |
| Feature graphic | `assets/feature-graphic-1024x500.png` | 1024 x 500 px, PNG or JPEG |

Both must be original artwork and must not use Instagram's name, logo, or look. Confirm current file size and format limits in the Play Console.
