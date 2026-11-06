# Compliance checklist

Drafts for the developer to review. This is a checklist, not legal advice, and does not claim compliance with any specific law.

## General checklist

| # | Item | Status | Reason |
|---|---|---|---|
| 1 | Privacy policy | Done | `docs/privacy-policy.md` (contact prabhath.kumar2000@gmail.com, effective 5 November 2026). Publish via GitHub Pages. |
| 2 | Terms of service | Done | `docs/terms-of-service.md`; same placeholders. |
| 3 | Refund policy | Not applicable | App is free, no purchases. |
| 4 | Cookie policy | Not applicable | No website tracking or cookies in the app; GitHub Pages site sets none of its own. |
| 5 | Cookie consent banner | Not applicable | No cookies used. |
| 6 | Form consents | Done | Only "form" is the Accessibility disclosure with explicit "Agree" / "No thanks". |
| 7 | Collect only needed data | Done | Collects nothing; reads window structure in memory only to find one button. |
| 8 | Audit third-party SDKs | Done | No third-party SDKs or libraries. |
| 9 | Remove dark patterns | Done | "No thanks" is offered as plainly as "Agree"; no nagging beyond re-showing the disclosure on open. |
| 10 | No hidden fees | Done | Free, no ads, no in-app purchases. |
| 11 | No fake reviews | Done | None posted; do not ask friends for incentivized reviews. |
| 12 | No unsupported claims | Done | Listing states limitations (English fallback, TalkBack, Instagram updates). |
| 13 | Alt text (content descriptions) | Done | The app has no in-app images; the overlay has a content description ("Send to new group chat, disabled by IG Group Guard"); launcher icon label comes from the app name. assets/logo.svg has an aria-label. |
| 14 | Color contrast | Done | Overlay text #C4C4C4 on #3A3A3A is about 6:1; app UI uses the system theme. |
| 15 | Keyboard navigation | Done | Uses standard focusable Buttons. |
| 16 | Business details | Done | Developer: Prabhath (github.com/prabhath112), contact prabhath.kumar2000@gmail.com in privacy policy, terms and listing. Enter the same in Play Console. |
| 17 | Age consent for kids | Done | Target 13+, not designed for children, no ads. |
| 18 | Unsubscribe link | Not applicable | No emails or marketing. |
| 19 | License stock images | Done | Logo is original, made for this project. |
| 20 | Data deletion option | Done | No data collected; "Withdraw consent and delete data" or uninstalling deletes the three stored settings (consent flag, Block/Ask choice, button screen position). |

## Google Play checklist

| Item | Status | Reason |
|---|---|---|
| Accessibility prominent disclosure | Done | Full-screen in-app disclosure on first launch, affirmative "Agree", re-shown after "No thanks". |
| Accessibility declaration + video | Action needed by developer | Fill the form and record the video per `docs/play-data-safety.md`. |
| Device and Network Abuse risk | Action needed by developer | Real rejection risk: policy disallows apps that "interfere with ... other apps on the device", and IG Group Guard overlays part of Instagram. Mitigation: single narrow purpose, user-initiated, consent first, covers only one button, never touches ads. If rejected, distribute via GitHub Releases. |
| Impersonation | Done | Title, icon and graphics do not use Instagram's name or logo; listing has a not-affiliated line. Keep screenshots free of Instagram branding as the focus. |
| AAB upload | Done | `build.sh` builds `build/ig-group-guard-release.aab` (bundletool 1.18.3, validated) signed with the upload key. |
| Target API level | Done | Manifest sets `targetSdkVersion="36"`. |
| Data safety form | Action needed by developer | Answer per `docs/play-data-safety.md` (no data collected or shared). |
| Content rating questionnaire | Action needed by developer | Complete in Play Console; no user content, ads, or purchases. |
| Target audience | Action needed by developer | Select 13 and older in Play Console; not designed for children. |
| Privacy policy URL | Done | https://ig-group-guard.vercel.app/privacy-policy (built by Vercel's GitHub integration on every push to main; mirror on GitHub Pages). Enter it in Play Console. |
| Contact email | Action needed by developer | Docs use prabhath.kumar2000@gmail.com; enter the same address in Play Console. |
| Play App Signing + upload key | Action needed by developer | Upload key created at `%USERPROFILE%\.android\ig-group-guard-upload.jks` (password in `ig-group-guard-upload.properties` next to it, SHA-256 31:DA:8A:...:64:61). Back both up somewhere safe, never commit them, and enroll in Play App Signing on first upload. |

## Before you publish

Done already: LICENSE (MIT), Play icon and feature graphic (`assets/`), signed AAB
(`build/ig-group-guard-release.aab`), content descriptions, contact details.

1. Create the GitHub repo and enable GitHub Pages from `docs/`; confirm `/privacy-policy` and `/terms` load.
2. Take the listed phone screenshots for the store listing.
3. Record the Accessibility declaration video and fill the declaration, Data safety, content rating and target audience forms.
4. Back up the upload keystore and its properties file.
5. Have someone review these drafts before relying on them.
