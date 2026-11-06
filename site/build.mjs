// Builds the static site into site/dist from the Markdown in docs/.
// Pages: / (landing + latest APK download), /privacy-policy, /terms. Links are relative so the
// same output works on Vercel (root) and GitHub Pages (/ig-group-guard/). No inline CSS or JS.
// The version shown comes from app/AndroidManifest.xml in this checkout (no network access at
// build time). The site is redeployed by the release workflow, so it matches the newest release.
// Download links point at the release for that version.
import { readFile, writeFile, mkdir, copyFile, rm } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { marked } from "marked";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const OUT = join(ROOT, "site", "dist");
const REPO = "prabhath112/ig-group-guard";

const esc = (s) => String(s).replace(/[&<>"']/g, (c) =>
  ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);

async function appVersion() {
  const manifest = await readFile(join(ROOT, "app", "AndroidManifest.xml"), "utf8");
  const m = manifest.match(/android:versionName="(\d+\.\d+\.\d+)"/);
  if (!m) throw new Error("versionName not found in app/AndroidManifest.xml");
  return m[1];
}

const page = (title, body) => `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${esc(title)}</title>
<meta name="description" content="IG Group Guard: stops accidental group chats when sharing to several people on Instagram. Not affiliated with Instagram or Meta.">
<link rel="icon" href="logo.svg" type="image/svg+xml">
<link rel="stylesheet" href="style.css">
</head>
<body>
<main>
<nav aria-label="Site"><a href="./">Home</a><a href="privacy-policy">Privacy policy</a><a href="terms">Terms</a><a href="https://github.com/${REPO}">Source</a></nav>
${body}
<footer class="muted">IG Group Guard is an independent app. It is not affiliated with, endorsed by, or sponsored by Instagram or Meta. Instagram is a trademark of its owner.</footer>
</main>
</body>
</html>
`;

async function md(file) {
  const raw = await readFile(join(ROOT, "docs", file), "utf8");
  return marked.parse(raw.replace(/^---\n[\s\S]*?\n---\n/, "")); // drop Jekyll front matter
}

const ver = await appVersion();
// Release files carry the version (ig-group-guard-release-vX.Y.Z.apk), so link that release directly.
const DL = `https://github.com/${REPO}/releases/download/v${ver}`;
const APK = `${DL}/ig-group-guard-release-v${ver}.apk`;
const SUMS = `${DL}/SHA256SUMS`;
const version = `Version <a href="https://github.com/${REPO}/releases/tag/v${ver}">${ver}</a>`;

const home = `
<header><img src="logo.svg" alt="IG Group Guard logo: three people behind a no sign"><h1>IG Group Guard</h1></header>
<p>When you share a reel or post to several people, Instagram shows <strong>"Send to new group chat"</strong>
right under "Send separately". One stray tap puts everyone you selected into one group, without asking.
IG Group Guard greys that button out so it cannot be tapped. "Send separately" keeps working.</p>
<p>Prefer a choice? Switch to <strong>Ask me first</strong> in the app: tapping the button then opens a
popup asking whether you really want a group with everyone selected.</p>
<p><a class="btn" href="${APK}">Download APK for Android</a></p>
<p class="muted">${version}. <a href="${SUMS}">SHA-256 checksums</a>. Android 8.0 or newer.</p>
<h2>Privacy</h2>
<p>No internet permission, no analytics, no ads, no accounts. Nothing leaves your phone.
Read the <a href="privacy-policy">privacy policy</a>.</p>
<h2>Install</h2>
<ol>
<li>Download and open the APK, allow installing from your browser if Android asks.</li>
<li>Open IG Group Guard, read the notice and tap <strong>Agree</strong>.</li>
<li>Tap <strong>Open Accessibility settings</strong> → Installed apps → IG Group Guard → On.</li>
<li>Android 13 or newer: if the switch is greyed out, open Settings → Apps → IG Group Guard → ⋮ → Allow restricted settings.</li>
</ol>
<h2 id="banking">Bank or payment app will not open?</h2>
<p>Some banking and payment apps refuse to start while any accessibility service is on. That is
their anti-fraud rule, not a fault in IG Group Guard. Pause it while banking with the
<strong>Pause for banking</strong> button in the app, the Quick Settings tile, or the volume-key
accessibility shortcut, and turn it back on afterwards.</p>
<h2 id="play-protect">"Blocked by Play Protect"?</h2>
<p>In India and some other countries, Google Play Protect blocks any app that uses the Accessibility
service when it is installed from a browser, chat app or file manager. IG Group Guard needs that
service to cover the button, so the download above can be blocked there. This is a Google rule for
sideloaded apps, not a problem with the file: you can check it against the
<a href="${SUMS}">SHA-256 checksums</a>.</p>
<ul>
<li><strong>Google Play:</strong> installs from Google Play are not blocked. The Play Store version is not published yet.</li>
<li><strong>From a computer:</strong> turn on USB debugging, connect the phone and run
<code>adb install ig-group-guard-release-v${ver}.apk</code>.</li>
</ul>
<p class="muted">We do not recommend turning Play Protect off to get around this; it protects you from real malware.</p>`;

await rm(OUT, { recursive: true, force: true });
await mkdir(OUT, { recursive: true });
await writeFile(join(OUT, "index.html"), page("IG Group Guard", home));
await writeFile(join(OUT, "privacy-policy.html"), page("IG Group Guard: Privacy policy", await md("privacy-policy.md")));
await writeFile(join(OUT, "terms.html"), page("IG Group Guard: Terms of service", await md("terms-of-service.md")));
await copyFile(join(ROOT, "assets", "logo.svg"), join(OUT, "logo.svg"));
await copyFile(join(ROOT, "site", "style.css"), join(OUT, "style.css"));
console.log(`site built: v${ver}`);
