#!/usr/bin/env python3
"""A first month with the app: fresh install, onboarding, then four weeks of training on the emulator's clock.

Each simulated day sets the device date, opens the app and does what a lifter would: trains on a training day
(warm-ups skipped, every working set logged at the app's own target and rated), answers the finish page, and
on rest days just looks at Today. Screens land in <outdir> for a design/flow review; nothing here fails a build.
Usage: month.py <package> <outdir>
"""
import datetime as dt, os, re, subprocess, sys, time, xml.etree.ElementTree as ET

PKG, OUT = sys.argv[1], sys.argv[2]
os.makedirs(OUT, exist_ok=True)
STEP = [0]
NOTES = []
DAY = ["?"]


def note(msg):
    print("NOTE", DAY[0], msg, flush=True)
    NOTES.append(f"{DAY[0]}: {msg}")


def adb(*a, timeout=60):
    try:
        return subprocess.run(["adb", *a], capture_output=True, text=True, timeout=timeout).stdout
    except subprocess.TimeoutExpired:
        return ""


def shot(name):
    STEP[0] += 1
    path = os.path.join(OUT, f"{STEP[0]:03d}-{DAY[0]}-{name}.png")
    with open(path, "wb") as f:
        f.write(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True, timeout=60).stdout)


def dump():
    for _ in range(3):
        adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
        xml = adb("shell", "cat", "/sdcard/ui.xml")
        if "<hierarchy" in xml:
            try:
                return list(ET.fromstring(xml[xml.index("<hierarchy"):]).iter("node"))
            except ET.ParseError:
                pass
        time.sleep(0.8)
    return []


def center(n):
    b = list(map(int, re.findall(r"\d+", n.get("bounds", ""))))
    return ((b[0] + b[2]) // 2, (b[1] + b[3]) // 2) if len(b) == 4 and b[2] > b[0] and b[3] > b[1] else None


def label(n):
    return (n.get("content-desc") or "") + "|" + (n.get("text") or "")


def find(pattern, timeout=8):
    rx = re.compile(pattern)
    end = time.time() + timeout
    while True:
        for n in dump():
            if rx.search(label(n)) and center(n):
                return center(n)
        if time.time() > end:
            return None
        time.sleep(0.6)


def tap(pattern, timeout=8):
    p = find(pattern, timeout)
    if p:
        adb("shell", "input", "tap", str(p[0]), str(p[1]))
        time.sleep(1.0)
    return bool(p)


def back():
    adb("shell", "input", "keyevent", "4"); time.sleep(1.0)


def hide_keyboard():
    time.sleep(0.3)
    if "mInputShown=true" in adb("shell", "dumpsys", "input_method"):
        back()


def swipe_up(step=900):
    adb("shell", "input", "swipe", "540", "1800", "540", str(1800 - step), "300"); time.sleep(0.7)


def swipe_down():
    adb("shell", "input", "swipe", "540", "600", "540", "1900", "200"); time.sleep(0.5)


def to_top(n=8):
    for _ in range(n):
        swipe_down()


def scroll_to(pattern, tries=6, step=900):
    for _ in range(tries):
        if find(pattern, timeout=1):
            return True
        swipe_up(step)
    return bool(find(pattern, timeout=1))


def tour(name, pages=6):
    to_top()
    last = None
    for k in range(pages):
        png = subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True, timeout=60).stdout
        if png == last:
            break
        STEP[0] += 1
        with open(os.path.join(OUT, f"{STEP[0]:03d}-{DAY[0]}-{name}-{k}.png"), "wb") as f:
            f.write(png)
        last = png
        swipe_up(1100)
    to_top()


def set_date(d):
    adb("shell", "date", d.strftime("%m%d%H%M%Y.%S"))
    time.sleep(1)


def launch():
    adb("shell", "am", "force-stop", PKG)
    adb("shell", "am", "start", "-W", "-n", f"{PKG}/com.pursuitiron.app.MainActivity")
    time.sleep(5)
    adb("shell", "am", "broadcast", "-a", "android.intent.action.CLOSE_SYSTEM_DIALOGS")


def tab(name):
    tap(rf"^{name}\||^\|{name}$|^{name}$", timeout=5)
    time.sleep(1.2)


WEIGHTS = [(r"squat|deadlift|bench|row|press", "60"), (r"dumbbell|curl|raise|fly|kickback", "14"), (r"cable|machine|pulldown|extension|pushdown", "35")]


def start_weight(name):
    n = name.lower()
    if "dumbbell" in n or "lateral" in n or "curl" in n:
        return "14"
    if "barbell" in n or "squat" in n or "deadlift" in n:
        return "60"
    return "35"


def current_lift():
    for n in dump():
        m = re.match(r"^(.+): how to do it\|", label(n))
        if m:
            return m.group(1)
    return ""


def unlogged_check():
    """The next set's log check: a checkbox whose label is "Set N: …" and that isn't checked."""
    for n in dump():
        if re.match(r"^Set [0-9W]+: ", n.get("content-desc") or "") and n.get("checked") == "false" and center(n):
            return center(n)
    return None


def train(day_no):
    """One session start to finish, the way a lifter who follows the app would."""
    to_top()
    if not tap(r"Start workout$|Start it early$", timeout=6):
        note("no Start on a training day"); shot("no-start"); return False
    if find(r"(?i)time available", timeout=6):
        if day_no < 3:
            shot("preview")
        tap(r"^Full,", timeout=2)
        tap(r"Start workout$", timeout=4)
    time.sleep(3)
    tap(r"^\|Allow$", timeout=2)
    if day_no == 0:
        shot("workout-first")
    tap(r"Warmed up", timeout=2)
    tap(r"^\|Got it$", timeout=2)
    lifts_seen, idle, logged, t0 = set(), 0, 0, time.time()
    while time.time() - t0 < 900:
        lift = current_lift()
        if lift and lift not in lifts_seen:
            lifts_seen.add(lift)
            if day_no == 0 and len(lifts_seen) <= 2:
                shot("lift-" + re.sub(r"[^a-z]+", "-", lift.lower()))
        tap(r"^\|Skip warm-up →$", timeout=1)
        if find(r"Weight not set, edit", timeout=1):
            tap(r"Weight not set, edit")
            tap(r"^Weight \((kg|lb)\), tap to type", timeout=3)
            adb("shell", "input", "text", start_weight(lift))
            hide_keyboard()
            if not tap(r"^\|Save & log$", timeout=3):
                tap(r"^\|Save$", timeout=2)
            logged += 1
        else:
            c = unlogged_check()
            if c:
                adb("shell", "input", "tap", str(c[0]), str(c[1])); time.sleep(1.0); logged += 1
            else:
                swipe_up(700)
                c = unlogged_check()
                if c:
                    continue
                to_top(3)
                if not tap(r"^Next exercise: ", timeout=2):
                    break
                continue
        # Rate it (the pill), alternating the answer the way a real lifter's sets vary.
        tap(rf"^{[2, 1, 3, 2][logged % 4]} reps left", timeout=2)
        if logged == 2 and day_no == 0:
            shot("rest")
        tap(r"^\|Skip$", timeout=1)
    note(f"logged {logged} sets on {len(lifts_seen)} lifts in {time.time() - t0:.0f}s")
    # Finish
    to_top(2)
    tap(r"^\|Finish$|^Finish$", timeout=4)
    tap(r"^\|Finish$|^Finish$", timeout=3)
    time.sleep(2)
    shot("finish")
    for ans in ("About right", "About right", "About right", "About right", "Moderate", "Recovered"):
        tap(rf"^\|{ans}$", timeout=1)
    if scroll_to(r"^\|Save session$", tries=8, step=700):
        shot("finish-bottom")
        tap(r"^\|Save session$")
    else:
        note("finish page: no Save session"); shot("finish-stuck")
    time.sleep(3)
    shot("after-save")
    return True


# ---------------------------------------------------------------- the month
adb("root"); time.sleep(4); adb("wait-for-device")
adb("shell", "settings", "put", "global", "auto_time", "0")
adb("shell", "settings", "put", "global", "auto_time_zone", "0")
adb("shell", "settings", "put", "global", "hide_error_dialogs", "1")
adb("shell", "pm", "grant", PKG, "android.permission.POST_NOTIFICATIONS")

start = dt.datetime(2026, 10, 5, 17, 30)  # a Monday, four weeks before the real date
DAY[0] = "d00"
set_date(start)
launch()
shot("first-launch")
tap(r"^\|Build my program$")
for k in range(14):
    if find(r"^\|Build this (program|cycle)$", timeout=1):
        t0 = time.time()
        while find(r"SCORING", timeout=1) and time.time() - t0 < 120:
            time.sleep(2)
        shot("ranked")
        tap(r"^\|Build this (program|cycle)$")
        break
    shot(f"wizard-{k}")
    if not tap(r"^\|Next$|^\|Continue$", timeout=4):
        note("wizard stuck"); break
time.sleep(4)
shot("today-day-one")
tour("today-day-one", 5)

sessions = 0
for d in range(29):
    DAY[0] = f"d{d:02d}"
    now = start + dt.timedelta(days=d)
    set_date(now)
    launch()
    for _ in range(2):  # whatever greets the lifter today (a review, a prompt) gets one look and is closed
        if find(r"(?i)block .* review|review this block|what's new", timeout=1):
            shot("greeting"); back()
    to_top()
    rest = find(r"Recover today|Rest day", timeout=2)
    first_week = d < 7
    if first_week or now.weekday() == 0:
        shot("today")
    if not rest:
        if train(sessions):
            sessions += 1
        to_top()
        if first_week:
            shot("today-after")
    # End of each week: what the lifter would look at
    if now.weekday() == 6:
        tab("Progress"); tour("progress", 6)
        tab("Plan"); tour("plan", 5)
        tab("You"); tour("you", 4)
        tab("Today")

DAY[0] = "end"
set_date(start + dt.timedelta(days=29, hours=1))
launch()
tour("today", 6)
tab("Plan"); tour("plan", 8)
if tap(r"^Review block|Review block$", timeout=4):
    tour("review", 6); back()
tab("Progress"); tour("progress", 8)
if tap(r"^\|History$", timeout=3):
    tour("history", 4)
tab("Exercises"); shot("exercises")
tab("You"); tour("you", 8)
note(f"{sessions} sessions over four weeks")
with open(os.path.join(OUT, "notes.txt"), "w") as f:
    f.write("\n".join(NOTES))
