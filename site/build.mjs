// Builds the static site into site/dist from the Markdown in docs/.
// Pages: / (landing + latest APK download), /privacy-policy, /terms.
// The latest release is read from the GitHub API at build time, so redeploying after a release
// updates the version shown. Downloads use GitHub's /releases/latest/download/ URL, which always
// points at the newest release.
import { readFile, writeFile, mkdir, copyFile, rm } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { marked } from "marked";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const OUT = join(ROOT, "site", "dist");
const REPO = "prabhath112/ig-group-guard";
const APK = `https://github.com/${REPO}/releases/latest/download/ig-group-guard-release.apk`;
const SUMS = `https://github.com/${REPO}/releases/latest/download/SHA256SUMS`;

const esc = (s) => String(s).replace(/[&<>"']/g, (c) =>
  ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);

async function latestRelease() {
  const headers = { "User-Agent": "ig-group-guard-site", Accept: "application/vnd.github+json" };
  if (process.env.GITHUB_TOKEN) headers.Authorization = `Bearer ${process.env.GITHUB_TOKEN}`;
  try {
    const r = await fetch(`https://api.github.com/repos/${REPO}/releases/latest`, { headers });
    if (!r.ok) return null; // 404 before the first release
    const j = await r.json();
    return { tag: j.tag_name, date: (j.published_at || "").slice(0, 10), url: j.html_url };
  } catch {
    return null; // offline build: page still renders, without a version
  }
}

const page = (title, body) => `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${esc(title)}</title>
<meta name="description" content="IG Group Guard: stops accidental group chats when sharing to several people on Instagram. Not affiliated with Instagram or Meta.">
<link rel="icon" href="/logo.svg" type="image/svg+xml">
<style>
  :root { color-scheme: light dark; --fg:#1b1f23; --bg:#ffffff; --muted:#57606a; --accent:#0F766E; }
  @media (prefers-color-scheme: dark) { :root { --fg:#e6edf3; --bg:#0d1117; --muted:#9da7b3; --accent:#2dd4bf; } }
  body { margin:0; font:16px/1.6 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; color:var(--fg); background:var(--bg); }
  main { max-width: 760px; margin: 0 auto; padding: 32px 20px 64px; }
  header { display:flex; align-items:center; gap:14px; }
  header img { width:56px; height:56px; border-radius:12px; }
  a { color: var(--accent); }
  .btn { display:inline-block; padding:12px 20px; border-radius:10px; background:#0F766E; color:#fff; text-decoration:none; font-weight:600; }
  .btn:focus-visible, a:focus-visible { outline: 3px solid #FBBF24; outline-offset: 2px; }
  .muted { color: var(--muted); font-size: 14px; }
  table { border-collapse: collapse; width: 100%; } td, th { border: 1px solid #8884; padding: 6px 8px; text-align:left; }
  nav a { margin-right: 16px; }
  footer { margin-top: 48px; }
</style>
</head>
<body>
<main>
<nav aria-label="Site"><a href="/">Home</a><a href="/privacy-policy">Privacy policy</a><a href="/terms">Terms</a><a href="https://github.com/${REPO}">Source</a></nav>
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

const rel = await latestRelease();
const version = rel
  ? `Latest version <a href="${esc(rel.url)}">${esc(rel.tag)}</a> (${esc(rel.date)})`
  : "No release published yet.";

const home = `
<header><img src="/logo.svg" alt="IG Group Guard logo: three people behind a no sign"><h1>IG Group Guard</h1></header>
<p>When you share a reel or post to several people, Instagram shows <strong>"Send to new group chat"</strong>
right under "Send separately". One stray tap puts everyone you selected into one group, without asking.
IG Group Guard greys that button out so it cannot be tapped. "Send separately" keeps working.</p>
<p><a class="btn" href="${APK}">Download APK for Android</a></p>
<p class="muted">${version}. <a href="${SUMS}">SHA-256 checksums</a>. Android 8.0 or newer.</p>
<h2>Privacy</h2>
<p>No internet permission, no analytics, no ads, no accounts. Nothing leaves your phone.
Read the <a href="/privacy-policy">privacy policy</a>.</p>
<h2>Install</h2>
<ol>
<li>Download and open the APK, allow installing from your browser if Android asks.</li>
<li>Open IG Group Guard, read the notice and tap <strong>Agree</strong>.</li>
<li>Tap <strong>Open Accessibility settings</strong> → Installed apps → IG Group Guard → On.</li>
<li>Android 13 or newer: if the switch is greyed out, open Settings → Apps → IG Group Guard → ⋮ → Allow restricted settings.</li>
</ol>`;

await rm(OUT, { recursive: true, force: true });
await mkdir(OUT, { recursive: true });
await writeFile(join(OUT, "index.html"), page("IG Group Guard", home));
await writeFile(join(OUT, "privacy-policy.html"), page("IG Group Guard: Privacy policy", await md("privacy-policy.md")));
await writeFile(join(OUT, "terms.html"), page("IG Group Guard: Terms of service", await md("terms-of-service.md")));
await copyFile(join(ROOT, "assets", "logo.svg"), join(OUT, "logo.svg"));
console.log(`site built: ${rel ? rel.tag : "no release yet"}`);
