"""Render Play Store PNGs from assets/logo.svg with headless Chrome/Edge.

  python -I assets/render.py

Writes assets/play-icon-512.png (full square, Play applies its own mask) and
assets/feature-graphic-1024x500.png. Stdlib only.
"""
import os
import subprocess
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
BROWSERS = [
    r"C:\Program Files\Google\Chrome\Application\chrome.exe",
    r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
    "google-chrome", "chromium",
]

with open(os.path.join(HERE, "logo.svg"), encoding="utf-8") as f:
    svg = f.read()
# Play wants a full-bleed square icon; it rounds the corners itself.
square = svg.replace('rx="24" ', "").replace('width="512" height="512"', 'width="512" height="512" style="display:block"')
inline = svg.replace('width="512" height="512"', 'width="300" height="300"')

PAGES = {
    "play-icon-512.png": (512, 512, f"<body style='margin:0'>{square}</body>"),
    "feature-graphic-1024x500.png": (1024, 500, f"""
<body style="margin:0;width:1024px;height:500px;background:#0F766E;display:flex;align-items:center;
             font-family:'Segoe UI',Roboto,Arial,sans-serif;color:#fff">
  <div style="margin-left:90px">{inline}</div>
  <div style="margin-left:60px">
    <div style="font-size:64px;font-weight:700">IG Group Guard</div>
    <div style="font-size:36px;margin-top:10px;color:#FDE68A">No more accidental group chats</div>
    <div style="font-size:24px;margin-top:22px;opacity:.9">Share to many people. Send separately.<br>Nothing leaves your phone.</div>
  </div>
</body>"""),
}


def browser():
    for b in BROWSERS:
        if os.path.isabs(b) and os.path.exists(b):
            return b
    return BROWSERS[-1]


for name, (w, h, html) in PAGES.items():
    with tempfile.NamedTemporaryFile("w", suffix=".html", delete=False, encoding="utf-8") as f:
        f.write(f"<!doctype html><meta charset='utf-8'>{html}")
    out = os.path.join(HERE, name)
    subprocess.run([browser(), "--headless=new", "--disable-gpu", "--hide-scrollbars",
                    f"--window-size={w},{h}", "--default-background-color=00000000",
                    f"--screenshot={out}", "file:///" + f.name.replace("\\", "/")],
                   check=True, capture_output=True)
    os.unlink(f.name)
    print("wrote", out)
