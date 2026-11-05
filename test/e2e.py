"""End-to-end tests for IG Group Guard, driven over adb. Stdlib only.

  python -I test/e2e.py [-s SERIAL]

Fixture mode (emulator / phone without real Instagram): installs build/ig-fixture.apk, which
copies Instagram's share-sheet view ids, and runs every test case including real taps.

Real mode (real Instagram installed): NO taps are sent, because a tap that got through would
share to a real group. Open a reel share sheet, select 2+ people, then run; it checks that the
overlay sits exactly on the button.
"""
import argparse
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP_APK = os.path.join(ROOT, "build", "ig-group-guard-debug.apk")
FIXTURE_APK = os.path.join(ROOT, "build", "ig-group-guard-fixture.apk")
APP = "io.github.prabhath112.iggroupguard"
SERVICE = APP + "/.GroupGuardService"
IG = "com.instagram.android"
GROUP_ID = IG + ":id/direct_send_to_group_button_vertical"
SEPARATE_ID = IG + ":id/direct_send_button_multi_select"
GROUP_TEXT = "Send to new group chat"

serial = None
run_start = None  # device time when the run began (crash check)
since = None  # device time of the last mark_log(); logs before it are ignored
results = []


def adb(*args, check=True):
    cmd = ["adb"] + (["-s", serial] if serial else []) + list(args)
    p = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    if check and p.returncode != 0:
        raise RuntimeError(f"{' '.join(cmd)} failed: {p.stdout}{p.stderr}")
    return p.stdout


def sh(cmd):
    return adb("shell", cmd).strip()


# ---------- device helpers ----------

def dump():
    """Current UI hierarchy of the active app window (our overlay is not included)."""
    for _ in range(3):
        # Remove the old file first: when the dump fails (e.g. "could not get idle state" while a
        # video plays) `cat` would otherwise return the previous screen.
        adb("shell", "rm -f /sdcard/gg_ui.xml", check=False)
        adb("shell", "uiautomator dump /sdcard/gg_ui.xml", check=False)
        xml = adb("exec-out", "cat /sdcard/gg_ui.xml", check=False)
        if xml.lstrip().startswith("<?xml"):
            return ET.fromstring(xml[xml.index("<?xml"):].encode("utf-8"))
        time.sleep(0.5)
    raise RuntimeError("uiautomator dump failed")


def bounds(node):
    l, t, r, b = map(int, re.findall(r"\d+", node.get("bounds")))
    return (l, t, r, b)


def find_group_button(tree):
    """Bounds of the clickable group button by id, else by label text, else None."""
    parent = {c: p for p in tree.iter() for c in p}
    for n in tree.iter("node"):
        if n.get("resource-id") == GROUP_ID:
            return bounds(n)
    for n in tree.iter("node"):
        if GROUP_TEXT in (n.get("text") or ""):
            while n is not None and n.get("clickable") != "true":
                n = parent.get(n)
            return bounds(n) if n is not None else None
    return None


def find_by_id(tree, rid):
    for n in tree.iter("node"):
        if n.get("resource-id") == rid:
            return bounds(n)
    return None


def mark_log():
    """Ignore earlier log lines. Replaces `logcat -c`, which fails on some Android 8 images."""
    global since
    since = sh("date +'%m-%d %H:%M:%S.000'")
    time.sleep(1.1)  # -T has 1 s resolution in the mark


def read_log(tag):
    args = ["logcat", "-d"] + (["-T", since] if since else []) + ["-s", f"{tag}:I"]
    return adb(*args, check=False)


def guard_log():
    return read_log("GroupGuard")


def fixture_clicks():
    out = read_log("IgFixture")
    return re.findall(r"FIXTURE_CLICK (\w+)", out)


def overlay_state():
    """Last overlay state logged by the service: a (l,t,r,b) tuple, 'hidden', or None."""
    state = None
    for line in guard_log().splitlines():
        m = re.search(r"overlay (-?\d+) (-?\d+) (-?\d+) (-?\d+)", line)
        if m:
            state = tuple(map(int, m.groups()))
        elif "overlay hidden" in line:
            state = "hidden"
    return state


def wait_for(fn, timeout=6.0):
    end = time.time() + timeout
    val = fn()
    while not val and time.time() < end:
        time.sleep(0.4)
        val = fn()
    return val


def tap(x, y):
    sh(f"input tap {x} {y}")
    time.sleep(0.8)


def center(b):
    return ((b[0] + b[2]) // 2, (b[1] + b[3]) // 2)


def start_fixture(mode="id"):
    sh(f"am force-stop {IG}")
    mark_log()
    sh(f"am start -n {IG}/io.github.prabhath112.iggroupguard.fixture.ShareSheetActivity --es mode {mode}")
    if not wait_for(lambda: "FIXTURE_READY" in read_log("IgFixture")):
        raise RuntimeError("fixture did not start")
    time.sleep(1.0)


def overlay_matches_button():
    b = find_group_button(dump())
    got = wait_for(lambda: overlay_state() == b and b)
    return b, overlay_state(), bool(got)


# ---------- test runner ----------

def case(tc_id, title):
    def deco(fn):
        def run():
            try:
                ok, detail = fn()
            except Exception as e:  # report and keep going
                ok, detail = False, f"error: {e}"
            results.append((tc_id, title, ok, detail))
            print(f"[{'PASS' if ok else 'FAIL'}] {tc_id} {title}  {detail}")
        run.tc_id = tc_id
        return run
    return deco


@case("TC01", "service enabled and connected")
def tc01():
    enabled = SERVICE.split("/")[0] in sh("settings get secure enabled_accessibility_services")
    connected = wait_for(lambda: "connected sdk=" in guard_log(), 8)
    return enabled and bool(connected), f"enabled={enabled} connected={bool(connected)}"


@case("TC02", "overlay covers group button exactly")
def tc02():
    b, got, ok = overlay_matches_button()
    return ok, f"button={b} overlay={got}"


@case("TC03", "taps on group button (centre + both edges) are blocked")
def tc03():
    b = find_group_button(dump())
    y = (b[1] + b[3]) // 2
    for x in ((b[0] + b[2]) // 2, b[0] + 4, b[2] - 4):
        tap(x, y)
    clicks = fixture_clicks()
    blocked = guard_log().count("blocked tap")
    return "group" not in clicks and blocked >= 3, f"fixture clicks={clicks} blocked taps={blocked}"


@case("TC04", "'Send separately' still works")
def tc04():
    b = find_by_id(dump(), SEPARATE_ID)
    tap(*center(b))
    clicks = fixture_clicks()
    return "separate" in clicks, f"fixture clicks={clicks}"


@case("TC05", "overlay removed when button disappears, returns when it reappears")
def tc05():
    tree = dump()
    toggle = find_by_id(tree, IG + ":id/fixture_toggle")
    old = find_group_button(tree)
    tap(*center(toggle))
    # No uiautomator dump here: on old Android it unbinds accessibility services and would mask a
    # stale overlay. The spot the button left must not block taps any more.
    gone = wait_for(lambda: overlay_state() == "hidden")
    before = guard_log().count("blocked tap")
    tap(*center(old))
    stale_block = guard_log().count("blocked tap") > before
    tap(*center(toggle))
    b, got, back = overlay_matches_button()
    ok = bool(gone) and not stale_block and back
    return ok, f"hidden={bool(gone)} stale_block={stale_block} back={back} overlay={got}"


@case("TC06", "overlay follows button when layout moves")
def tc06():
    before = find_group_button(dump())
    tap(*center(find_by_id(dump(), IG + ":id/fixture_move")))
    b, got, ok = overlay_matches_button()
    return ok and b != before, f"before={before} after={b} overlay={got}"


@case("TC07", "overlay removed when user goes Home")
def tc07():
    sh("input keyevent KEYCODE_HOME")
    gone = wait_for(lambda: overlay_state() == "hidden")
    return bool(gone), f"state={overlay_state()}"


@case("TC08", "fallback by label text when view id is renamed")
def tc08():
    start_fixture("textonly")
    b, got, ok = overlay_matches_button()
    if not ok:
        return False, f"button={b} overlay={got}"
    tap(*center(b))
    clicks = fixture_clicks()
    return "group" not in clicks, f"button={b} overlay={got} clicks={clicks}"


@case("TC09", "landscape: overlay matches and blocks")
def tc09():
    start_fixture("id")
    sh("settings put system accelerometer_rotation 0")
    sh("settings put system user_rotation 1")
    try:
        time.sleep(2.0)
        b, got, ok = overlay_matches_button()
        if not ok:
            return False, f"button={b} overlay={got}"
        tap(*center(b))
        clicks = fixture_clicks()
        return "group" not in clicks, f"button={b} overlay={got} clicks={clicks}"
    finally:
        sh("settings put system user_rotation 0")
        time.sleep(1.5)


@case("TC10", "no crash of the service during the run")
def tc10():
    crash = adb("logcat", "-d", "-b", "crash", "-T", run_start, check=False)
    hit = "io.github.prabhath112.iggroupguard" in crash
    return not hit, "no crash" if not hit else crash[-500:]


@case("TC00", "no consent yet: service does not touch Instagram")
def tc00():
    time.sleep(3)
    st = overlay_state()
    return not isinstance(st, tuple), f"state={st}"


@case("TC11", "consent flow: disclosure shown, Agree enables the service")
def tc11():
    # A freshly booted CI emulator can show a system dialog over the app for a few seconds
    # (seen once on API 35 in GitHub Actions), so relaunch and look again before giving up.
    shown = agree = None
    texts = []
    for _ in range(5):
        sh(f"am start -n {APP}/.MainActivity")
        time.sleep(2)
        tree = dump()
        texts = [n.get("text") or "" for n in tree.iter("node")]
        shown = any("Accessibility Service API" in t for t in texts)
        agree = next((bounds(n) for n in tree.iter("node") if n.get("text") == "Agree"), None)
        if shown and agree:
            break
        sh("input keyevent KEYCODE_HOME")  # clears most transient system dialogs
        time.sleep(3)
    if not (shown and agree):
        seen = [t for t in texts if t][:8]
        return False, f"disclosure shown={shown} agree button={agree} screen texts={seen}"
    tap(*center(agree))
    time.sleep(1)
    on = any("IG Group Guard is" in (n.get("text") or "") for n in dump().iter("node"))
    return on, f"disclosure shown={shown} status screen after Agree={on}"


@case("TC12", "service switched off/on 10x with button on screen: no crash, overlay back")
def tc12():
    start_fixture("id")
    for _ in range(10):
        rebind_service(pause=0.15)
    crash = adb("logcat", "-d", "-b", "crash", "-T", run_start, check=False)
    hit = APP in crash
    b, got, ok = overlay_matches_button()
    return not hit and ok, f"crash={hit} overlay back={ok}"


def set_mode(ask_first):
    """Pick "Ask me first" or "Block it (default)" on the app's status screen."""
    label = "Ask me first" if ask_first else "Block it (default)"
    sh(f"am start -n {APP}/.MainActivity")
    time.sleep(2)
    node = next((n for n in dump().iter("node") if n.get("text") == label), None)
    if node is None:
        return False
    tap(*center(bounds(node)))
    node = next((n for n in dump().iter("node") if n.get("text") == label), None)
    return node is not None and node.get("checked") == "true"


def confirm_popup(nth):
    """Wait for the nth "confirm shown" log line; return (count, yes_rect, no_rect) or None."""
    def get():
        hits = re.findall(r"confirm shown count=(\S+) yes=(-?\d+) (-?\d+) (-?\d+) (-?\d+) "
                          r"no=(-?\d+) (-?\d+) (-?\d+) (-?\d+)", guard_log())
        return hits[nth - 1] if len(hits) >= nth else None
    h = wait_for(get, 8)
    if not h:
        return None
    v = list(h)
    return v[0], tuple(map(int, v[1:5])), tuple(map(int, v[5:9]))


@case("TC13", "Ask me first: Cancel keeps it covered; Create group unlocks it for this sheet only")
def tc13():
    if not set_mode(True):
        return False, "could not select 'Ask me first'"
    try:
        start_fixture("id")
        tree = dump()  # all dumps before "Create group": on Android 8 a dump re-binds the service
        toggle = find_by_id(tree, IG + ":id/fixture_toggle")
        b, got, ok = overlay_matches_button()
        if not ok:
            return False, f"no overlay in ask mode: button={b} overlay={got}"
        steps = []
        tap(*center(b))                           # 1. tap the banner -> our popup
        p1 = confirm_popup(1)
        if not p1:
            return False, "popup did not open"
        steps.append(f"popup count={p1[0]}")
        tap(*center(p1[2]))                       # 2. Cancel
        cancelled = bool(wait_for(lambda: "confirm no" in guard_log()))
        still_blocked = "group" not in fixture_clicks()
        steps.append(f"cancel={cancelled} blocked={still_blocked}")
        tap(*center(b))                           # 3. tap again -> popup again -> Create group
        p2 = confirm_popup(2)
        if not p2:
            return False, "popup did not reopen; " + ", ".join(steps)
        tap(*center(p2[1]))
        unlocked = bool(wait_for(lambda: "confirm yes" in guard_log() and overlay_state() == "hidden"))
        tap(*center(b))                           # 4. Instagram's own button now gets the tap
        reached = bool(wait_for(lambda: "group" in fixture_clicks()))
        steps.append(f"unlocked={unlocked} tap reached app={reached}")
        tap(*center(toggle))                      # 5. sheet closes ...
        reset = bool(wait_for(lambda: "confirm reset" in guard_log()))
        tap(*center(toggle))                      # ... and opens again: covered again
        back = bool(wait_for(lambda: isinstance(overlay_state(), tuple), 8))
        steps.append(f"reset={reset} covered again={back}")
        ok = (p1[0] == "3" and cancelled and still_blocked and unlocked and reached and reset and back)
        return ok, ", ".join(steps)
    finally:
        set_mode(False)


def overlay_window_frame():
    """Frame of our overlay window from the window manager, or None. Needs no uiautomator."""
    out = sh("dumpsys window windows")
    m = re.search(r"iggroupguard, frame=\[Rect\((-?\d+), (-?\d+) - (-?\d+), (-?\d+)\)\]", out)
    return tuple(map(int, m.groups())) if m else None


@case("RT01", "real Instagram: overlay drawn exactly over the group button (no taps)")
def rt01():
    print("   >> Open Instagram, share any reel, select 2+ people. Waiting up to 120 s ...")
    rect = wait_for(lambda: isinstance(overlay_state(), tuple) and overlay_state(), 120)
    if not rect:
        return False, "service never saw the group button"
    time.sleep(1)
    frame = overlay_window_frame()
    shot = os.path.join(ROOT, "build", "rt01-phone.png")
    with open(shot, "wb") as f:
        f.write(subprocess.run(["adb", "-s", serial, "exec-out", "screencap", "-p"],
                               capture_output=True).stdout)
    # Independent bounds from uiautomator when the screen is idle enough to dump.
    try:
        button = find_group_button(dump())
    except RuntimeError:
        button = "n/a (uiautomator could not dump: video playing)"
    ok = frame == rect and (not isinstance(button, tuple) or button == rect)
    return ok, f"service rect={rect} window frame={frame} uiautomator button={button} screenshot={shot}"


# ---------- setup ----------

def ensure_service_enabled():
    cur = sh("settings get secure enabled_accessibility_services")
    if SERVICE not in cur:
        new = SERVICE if cur in ("", "null") else cur + ":" + SERVICE
        sh(f"settings put secure enabled_accessibility_services {new}")
        sh("settings put secure accessibility_enabled 1")


def rebind_service(pause=1.0):
    cur = sh("settings get secure enabled_accessibility_services")
    others = ":".join(s for s in cur.split(":") if s and s != SERVICE and s != "null")
    sh(f"settings put secure enabled_accessibility_services {others or 'null'}")
    time.sleep(pause)
    ensure_service_enabled()


def main():
    global serial
    ap = argparse.ArgumentParser()
    ap.add_argument("-s", "--serial")
    serial = ap.parse_args().serial

    print("device:", sh("getprop ro.product.model"), "Android", sh("getprop ro.build.version.release"),
          "API", sh("getprop ro.build.version.sdk"))
    ig = adb("shell", f"dumpsys package {IG} | grep -m1 versionName", check=False).strip()
    real = bool(ig) and "fixture" not in ig
    global run_start
    run_start = sh("date +'%m-%d %H:%M:%S.000'")

    # Fresh install = no consent stored. (`pm clear` is blocked for the shell on some OEM ROMs,
    # e.g. ColorOS: CLEAR_APP_USER_DATA denied.)
    adb("uninstall", APP, check=False)
    adb("install", APP_APK)
    # Reinstall kills the process like a force-stop. Android 11 then marks the service "crashed" and
    # only re-binds if the app process is alive when the setting is toggled, so start it first
    # (what a user does by opening the app).
    sh(f"am start -n {APP}/.MainActivity")
    time.sleep(2)
    if real:
        print("real Instagram found (", ig.strip(), ") -> real mode, no taps on Instagram")
        mark_log()
        rebind_service()
        for c in (tc01, tc11):
            c()
        sh("input keyevent KEYCODE_HOME")
        mark_log()
        cases = [rt01]
    else:
        adb("install", "-r", FIXTURE_APK)
        start_fixture("id")  # clears logcat
        # Fresh bind after the logcat clear so TC01 sees the "connected" line. Only our service
        # is toggled; other enabled accessibility services are left as they are.
        rebind_service()
        for c in (tc01, tc00, tc11):
            c()
        start_fixture("id")
        cases = [tc02, tc03, tc04, tc05, tc06, tc07, tc08, tc09, tc12, tc13, tc10]

    for c in cases:
        c()
    failed = [r for r in results if not r[2]]
    print(f"\n{len(results) - len(failed)}/{len(results)} passed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
