# Security policy

## Reporting a vulnerability

Please report security issues privately through GitHub:
**Security → Report a vulnerability** on https://github.com/prabhath112/ig-group-guard
(private vulnerability reporting is enabled), or by email to prabhath.kumar2000@gmail.com.

Do not open a public issue for a vulnerability. You will get a reply within 7 days.

## Scope

- The Android app (`app/`): the accessibility service only reads Instagram's share screen
  layout on the device, has no INTERNET permission and stores only a consent flag.
- The website (`site/`, deployed to https://ig-group-guard.vercel.app): static pages, no
  scripts, strict Content-Security-Policy.
- The build and release pipeline (`.github/`): actions pinned by commit SHA, least-privilege
  tokens, signing key only in the protected `release` environment.

## Supported versions

Only the latest release receives fixes.
