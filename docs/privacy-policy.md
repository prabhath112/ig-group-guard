---
title: IG Group Guard Privacy Policy
permalink: /privacy-policy
---

# IG Group Guard Privacy Policy

Effective date: 5 November 2026

This policy explains what the IG Group Guard Android app (package `io.github.prabhath112.iggroupguard`) does with information on your phone. IG Group Guard is made by Prabhath (GitHub user prabhath112).

Short version: IG Group Guard does not collect, store, or share any personal data. It has no internet access, so nothing leaves your phone.

## What IG Group Guard does

In Instagram's share sheet, when you select several people, Instagram shows a "Send to new group chat (N)" button under "Send separately". One tap on it creates a group of everyone you selected, without asking, and shows the recipients to each other. IG Group Guard covers that one button with a grey overlay that blocks taps and shows a short message. "Send separately" and Instagram's own "New group" icon keep working.

## What IG Group Guard accesses and why

IG Group Guard uses the Android Accessibility Service API. With your permission it:

- Reads the on-screen window structure of the Instagram app on your phone: view ids, screen positions (bounds) and button labels. It does this in memory, only to find the "Send to new group chat" button, so it can draw an overlay over it. In "Ask me first" mode it also reads the number shown on that button (for example "(3)") to say in its own popup how many people the group would include.
- Receives window change events from other apps, only to notice that Instagram is no longer on screen so it can remove the overlay.

It keeps nothing after each check.

## What IG Group Guard does not do

- It does not read or store your messages, contact names, profile information, or any other content.
- It does not collect, store, sell, or share any personal data.
- It has no internet permission, so it cannot send data anywhere.
- It has no analytics, no ads, no third-party SDKs, no accounts and no in-app purchases.

## What is stored on your phone

IG Group Guard stores two values in its local app preferences: whether you agreed to the in-app disclosure (yes or no), and your choice of what happens when you tap the covered button ("Block it" or "Ask me first"). Neither is personal data. Debug log lines, if any, contain only rectangle coordinates (screen positions), no personal data.

## Your consent

When you first open IG Group Guard, it shows this disclosure and asks you to tap "Agree" or "No thanks":

> IG Group Guard uses the Accessibility Service API. It reads the layout of Instagram's share screen on your phone (button names and positions) only to find the "Send to new group chat" button and cover it, so a group is never created by an accidental tap. IG Group Guard does not read your messages or contacts, does not collect or store any personal data, and has no internet access, so nothing leaves your phone. You can turn it off any time in Settings > Accessibility.

The service does nothing until you agree. If you tap "No thanks", the disclosure shows again the next time you open the app.

You can withdraw at any time by turning IG Group Guard off in Settings > Accessibility, or by uninstalling the app.

## Sharing

IG Group Guard shares no data with anyone, because it collects none.

## Children

IG Group Guard is meant for people aged 13 and older, the same minimum age as Instagram. It is not designed for children and contains no ads.

## Security

Because IG Group Guard collects no personal data and has no internet access, there is no personal data to transmit or protect on a server. The only stored values are the consent flag and the Block it / Ask me first choice, kept in the app's private storage on your phone.

## Deleting your data

"Withdraw consent and delete data" in the app, or uninstalling IG Group Guard, deletes both stored values. No other data exists, so there is nothing else to delete.

## Changes to this policy

If this policy changes, the new version will be posted at https://ig-group-guard.vercel.app/privacy-policy with a new effective date. If the app ever starts handling data differently, the in-app disclosure will be updated and you will be asked to agree again.

## Not affiliated

IG Group Guard is not affiliated with, endorsed by, or sponsored by Instagram or Meta Platforms. "Instagram" is a trademark of its owner and is used here only to describe compatibility.

## Contact

Questions about this policy: prabhath.kumar2000@gmail.com
