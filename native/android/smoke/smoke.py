#!/usr/bin/env python3
"""Drive the debug build on an emulator through the main flows and capture screenshots.

Controls are found by their accessibility text or content description (what TalkBack reads),
never by coordinates, so a missing label fails the run the same way it would fail a TalkBack user.
Usage: smoke.py <package> <outdir> <handoff-link-file>
"""
import os, re, subprocess, sys, time, xml.etree.ElementTree as ET

PKG, OUT, LINK = sys.argv[1], sys.argv[2], sys.argv[3]
os.makedirs(OUT, exist_ok=True)
STEP = [0]
FAILS = []


def adb(*a, check=False, timeout=60):
    r = subprocess.run(["adb", *a], capture_output=True, text=True, timeout=timeout)
    if check and r.returncode:
        raise RuntimeError(f"adb {' '.join(a)}: {r.stderr}")
    return r.stdout


def shot(name):
    STEP[0] += 1
    path = os.path.join(OUT, f"{STEP[0]:02d}-{name}.png")
    with open(path, "wb") as f:
        f.write(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True, timeout=60).stdout)
    print("shot", path, flush=True)


def nodes():
    for _ in range(3):
        adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
        xml = adb("shell", "cat", "/sdcard/ui.xml")
        if "<hierarchy" in xml:
            try:
                return ET.fromstring(xml[xml.index("<hierarchy"):]).iter("node")
            except ET.ParseError:
                pass
        time.sleep(1)
    return iter(())


def find(pattern, timeout=12):
    rx = re.compile(pattern)
    end = time.time() + timeout
    while time.time() < end:
        for n in nodes():
            label = (n.get("content-desc") or "") + "|" + (n.get("text") or "")
            if rx.search(label):
                b = re.findall(r"\d+", n.get("bounds", ""))
                if len(b) == 4:
                    x1, y1, x2, y2 = map(int, b)
                    if x2 > x1 and y2 > y1:
                        return (x1 + x2) // 2, (y1 + y2) // 2
        time.sleep(0.8)
    return None


def tap(pattern, name=None, timeout=12, required=True):
    p = find(pattern, timeout)
    if not p:
        msg = f"not found: {pattern}"
        print("FAIL", msg, flush=True)
        if required:
            FAILS.append(msg)
            shot("missing-" + re.sub(r"[^a-z0-9]+", "-", pattern.lower())[:40])
        return False
    adb("shell", "input", "tap", str(p[0]), str(p[1]))
    time.sleep(1.2)
    if name:
        shot(name)
    return True


def scroll_to(pattern, tries=6):
    for _ in range(tries):
        if find(pattern, timeout=2):
            return True
        adb("shell", "input", "swipe", "540", "1800", "540", "900", "300")
        time.sleep(0.8)
    return bool(find(pattern, timeout=2))


def back():
    adb("shell", "input", "keyevent", "4")
    time.sleep(1.2)


def launch():
    adb("shell", "am", "force-stop", PKG)
    adb("shell", "am", "start", "-W", "-n", f"{PKG}/com.pursuitiron.app.MainActivity")
    time.sleep(6)


def grant_notifications():
    tap(r"^\|Allow$|Allow$", timeout=4, required=False)


adb("logcat", "-c")
adb("shell", "settings", "put", "global", "window_animation_scale", "0.5")
adb("shell", "pm", "grant", PKG, "android.permission.POST_NOTIFICATIONS")

# 1. First run
launch()
shot("first-run")
tap(r"I'm new")
tap(r"Build my program", "setup")
tap(r"Build my program", "today-with-program", timeout=8)
time.sleep(3)
shot("today")

# 2. Workout: enter a weight, log, rate, rest
tap(r"Start workout")
time.sleep(3)
grant_notifications()
shot("workout")
if tap(r"Weight not set, edit", "set-editor"):
    tap(r"^\|Weight \(")  # the field's label, not the "Less Weight (kg)" button
    adb("shell", "input", "text", "60")
    time.sleep(0.6)
    shot("set-editor-typed")
    adb("shell", "input", "keyevent", "111")  # hide the keyboard
    time.sleep(0.6)
    if tap(r"Plates$", "set-plates"):
        back()
    tap(r"^\|Done$|Done$")
tap(r"^Set 1:", "set-logged")
tap(r"^2 reps left", "set-rated")
tap(r"Why .* kg\?|No suggestion yet", "why-sheet")
adb("shell", "input", "keyevent", "4")
time.sleep(1)
tap(r"Exercise actions", "exercise-menu")
tap(r"Something hurt", "hurt-sheet")
adb("shell", "input", "keyevent", "4")
time.sleep(1)

# 3. Minimise and resume through the mini bar
tap(r"Minimise workout", "minimised")
tap(r"Resume workout", "resumed")

# 4. Finish
tap(r"^\|Finish$|^Finish$")
tap(r"^\|Finish$|^Finish$", "summary", timeout=5, required=False)
time.sleep(1)
shot("summary")
tap(r"^\|Done$|Done$")

# 5. Tabs
for t in ["Plan", "Progress", "Exercises", "You"]:
    tap(rf"^\|{t}$|^{t}\|", t.lower())

# 6. Transfer link from the web app
with open(LINK) as f:
    link = f.read().strip()
adb("shell", "am", "start", "-a", "android.intent.action.VIEW", "-d", f"'{link}'", PKG)
time.sleep(3)
shot("import-offer")
tap(r"Bring it over", "imported")
time.sleep(2)
tap(r"^\|Today$|^Today\|")
shot("today-after-import")

# 7. Profile, calendar, settings, gyms, tools, unit switch
tap(r"^\|You$|^You\|")
tap(r"^\|Profile$", "profile")
scroll_to(r"Achievements")
shot("profile-achievements")
adb("shell", "input", "swipe", "540", "700", "540", "1900", "300")
tap(r"^\|History$", "history-calendar")
tap(r": \d+ workouts?\|", "calendar-day")
tap(r"^\|Settings$", "settings")
if tap(r"^\|lb$", "unit-confirm"):
    tap(r"^\|Switch$", "unit-switched")
if scroll_to(r"^\|Gyms$"):
    tap(r"^\|Edit$", "gyms-sheet")
    back()
if scroll_to(r"Plates and 1RM"):
    tap(r"Plates and 1RM")
    tap(r"Total weight")
    adb("shell", "input", "text", "100")
    time.sleep(1)
    adb("shell", "input", "keyevent", "111")
    time.sleep(0.8)
    shot("plate-calc")
    tap(r"^\|1RM$")
    tap(r"Weight \(")
    adb("shell", "input", "text", "100")
    adb("shell", "input", "keyevent", "111")
    time.sleep(1)
    shot("one-rm")
    back()
    if find(r"One-rep max", timeout=2):
        back()
tap(r"^\|Today$|^Today\|", "today-lb")

# 8. Training cycle: build one, open it, try to advance
tap(r"^\|Plan$|^Plan\|")
if scroll_to(r"New training cycle"):
    tap(r"New training cycle", "cycle-setup")
    scroll_to(r"Adapt between blocks")
    shot("cycle-templates")
    tap(r"^\|Build my cycle$")
    time.sleep(8)
    shot("cycle-built")
    adb("shell", "input", "swipe", "540", "700", "540", "1900", "200")
    if tap(r"Open cycle", "cycle-sheet"):
        if scroll_to(r"Complete block"):
            tap(r"Complete block", "cycle-advance-confirm")
            tap(r"^\|Advance$")
            time.sleep(6)
            shot("cycle-advanced")
        back()
tap(r"^\|Today$|^Today\|", "today-cycle")

# 9. Program editor: move a lift, save this block only
tap(r"^\|Plan$|^Plan\|")
adb("shell", "input", "swipe", "540", "700", "540", "1900", "200")
if tap(r"Edit program", "editor"):
    if tap(r"\. Edit\|", "editor-slot"):
        tap(r"Move later")
    shot("editor-moved")
    if tap(r"\. Edit\|"):
        tap(r"Swap exercise", "editor-swap")
        back()
    if tap(r"^\|Save$", "editor-scope"):
        tap(r"^\|This block$", "editor-saved")
    back()

# 10. Home-screen widget: rendered by a real AppWidgetHost, then its Start button's intent
adb("shell", "appwidget", "grantbind", "--package", PKG)
adb("shell", "am", "start", "-W", "-n", f"{PKG}/com.pursuitiron.app.WidgetPreviewActivity")
time.sleep(5)
shot("widget")
adb("shell", "am", "start", "-W", "-a", "com.pursuitiron.app.action.START_WORKOUT", "-n", f"{PKG}/com.pursuitiron.app.MainActivity")
time.sleep(4)
shot("widget-start")
if find(r"Minimise workout", timeout=4) is None:
    FAILS.append("widget Start did not open the workout")
adb("shell", "am", "start", "-W", "-n", f"{PKG}/com.pursuitiron.app.WidgetPreviewActivity")
time.sleep(4)
shot("widget-live")

# 11. Tablet
adb("shell", "wm", "size", "2560x1600")
adb("shell", "wm", "density", "320")
time.sleep(3)
launch()
time.sleep(3)
shot("tablet-today")
tap(r"Start workout|Resume workout")
time.sleep(3)
shot("tablet-workout")
adb("shell", "wm", "size", "reset")
adb("shell", "wm", "density", "reset")

# Crashes or engine errors anywhere in the run
log = adb("logcat", "-d", "-b", "crash", "-b", "main", timeout=120)
crash = [l for l in log.splitlines() if "FATAL EXCEPTION" in l or ("AndroidRuntime" in l and " E " in l) or "CoreException" in l]
with open(os.path.join(OUT, "logcat-errors.txt"), "w") as f:
    f.write("\n".join(crash[-200:]))
if crash:
    FAILS.append(f"{len(crash)} crash/engine error lines in logcat")
print("FAILS:", FAILS, flush=True)
with open(os.path.join(OUT, "result.txt"), "w") as f:
    f.write("\n".join(FAILS) if FAILS else "PASS")
sys.exit(1 if FAILS else 0)
