# Play Console: Data safety and Accessibility declaration

How to fill in the two Play Console forms for IG Group Guard. Re-check the wording of each question in the Console; the form may change. If the app's data handling changes, update both forms and resubmit.

## Data safety form

| Question | Answer | Reason |
|---|---|---|
| Does your app collect or share any of the required user data types? | No | The app has no INTERNET permission, no SDKs, and keeps nothing it reads. |
| Data collected | None | Window structure is checked in memory only to find one button and is not kept. |
| Data shared | None | Nothing is sent off the device. |
| Is all user data encrypted in transit? | Not applicable | No data is transmitted. |
| Do you provide a way for users to request that their data be deleted? | Not applicable, no data collected | Only a local yes/no consent flag exists; uninstalling deletes it. |

Note: the local consent flag never leaves the device, so it is not "collected" in the sense of the form. Review the Console's definitions before answering.

## Accessibility API declaration

| Field | Answer |
|---|---|
| Is the app an accessibility tool (`isAccessibilityTool`)? | No (`isAccessibilityTool=false`) |
| Core purpose / why the API is used | See text below |
| Is personal or sensitive data collected via the API? | No |
| Is personal or sensitive data shared via the API? | No |
| Data types | None |
| Prominent disclosure in app | Yes, full screen on first launch, with "Agree" and "No thanks" |
| Video | Link to the video described below |

Core purpose text:

```
IG Group Guard prevents accidental group chat creation in Instagram. In Instagram's share sheet, a "Send to new group chat" button creates a group of all selected people with one tap and no confirmation. IG Group Guard uses the Accessibility Service API to read the window structure of Instagram's share screen (view ids, button labels, screen positions) in memory, only to locate that one button, and draws an accessibility overlay over it that blocks taps. It also receives window events from other apps only to detect that Instagram has left the screen and remove the overlay. It does not read message content or contacts, does not collect, store or share any personal data, and has no internet permission. The service does nothing until the user agrees to an in-app prominent disclosure.
```

## Declaration video script

Record the screen of a real device. Scroll slowly and pause on every text screen so it is readable. Use a test Instagram account; do not show real people's names or messages. Upload the video (for example unlisted on YouTube) and paste the link.

1. Start on the home screen. Show IG Group Guard is freshly installed (or clear its data first).
2. Open IG Group Guard. The full-screen disclosure appears. Pause long enough to read the whole text.
3. Non-consent flow: tap "No thanks". Show that the service is not active (Instagram's "Send to new group chat" button is not covered).
4. Re-trigger: close and reopen IG Group Guard. The disclosure shows again.
5. Consent flow: tap "Agree".
6. Granting the permission: go to Settings > Accessibility, find IG Group Guard, turn it on, and accept the Android system dialog. (If sideloaded on Android 13 or newer, show "Allow restricted settings" first; not needed for a Play install.)
7. Core feature: open Instagram, open the share sheet on a post, select two or more people. Show the grey overlay covering "Send to new group chat". Tap it and show the message; no group is created.
8. Show that "Send separately" still works and that Instagram's own "New group" icon still works.
9. Optional: switch to another app and back to show the overlay is removed when Instagram leaves the screen.
10. Show how to turn it off: Settings > Accessibility > IG Group Guard > off.
