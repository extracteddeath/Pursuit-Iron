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


TRACE = [0]


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


CHECKS = []


def check(cond, msg):
    """A pass/fail line in result.txt: the month is a test, not just a photo album."""
    CHECKS.append(("PASS " if cond else "FAIL ") + f"{DAY[0]}: {msg}")
    print(CHECKS[-1], flush=True)
    if not cond:
        shot("fail")
    return cond


def text_of(nodes):
    return " ".join(label(n) for n in nodes)


def set_rows(nodes):
    """Each working set's log check in screen order: (label, centre, raw attrs)."""
    out = []
    for n in nodes:
        d = n.get("content-desc") or ""
        if re.match(r"^Set [0-9W]+: ", d) and center(n):
            out.append((d, center(n), n.attrib))
    return out


def tap_xy(p):
    adb("shell", "input", "tap", str(p[0]), str(p[1])); time.sleep(0.9)


def train(day_no):
    """One session start to finish, the way a lifter who follows the app would. The bot keeps its own record of
    which sets it logged (by lift and set), so it never taps a logged set a second time and un-logs it."""
    to_top()
    if not tap(r"Start workout$|Start it early$", timeout=6):
        check(False, "a training day has Start workout"); return False
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
    lifts, mine, stall, last_key, t0 = [], set(), 0, None, time.time()
    a11y_checked = [False]
    while time.time() - t0 < 600:
        nodes = dump()
        lift = next((re.match(r"^(.+): how to do it\|", label(n)).group(1) for n in nodes if re.match(r"^(.+): how to do it\|", label(n))), "")
        if lift and lift not in lifts:
            lifts.append(lift)
            if day_no == 0 and len(lifts) <= 2:
                shot("lift-" + re.sub(r"[^a-z]+", "-", lift.lower()))
        skip = next((center(n) for n in nodes if re.search(r"^\|Skip warm-up →$", label(n))), None)
        if skip:
            tap_xy(skip); continue
        rows = set_rows(nodes)
        if day_no == 0 and rows and TRACE[0] < 3:
            TRACE[0] += 1
            with open(os.path.join(OUT, "trace.txt"), "a") as f:
                f.write(f"{lift} first set node: {dict(rows[0][2])}\n")
        logged_rows = [r for r in rows if (lift + "|" + r[0].split(":")[0]) in mine]
        if logged_rows and not a11y_checked[0]:
            a11y_checked[0] = True
            check(all(r[2].get("checked") == "true" for r in logged_rows), "a logged set reports checked to accessibility services")
            with open(os.path.join(OUT, f"logged-{DAY[0]}.xml"), "w") as f:
                f.write(adb("shell", "cat", "/sdcard/ui.xml"))
        todo = [r for r in rows if (lift + "|" + r[0].split(":")[0]) not in mine and r[2].get("checked") != "true"]
        key = (lift, len(todo), todo[0][0] if todo else "")
        stall = stall + 1 if key == last_key else 0
        last_key = key
        if stall >= 4:
            note(f"stuck on {lift}: {todo[0][0] if todo else 'no set'}")
            with open(os.path.join(OUT, f"stuck-{DAY[0]}.xml"), "w") as f:
                f.write(adb("shell", "cat", "/sdcard/ui.xml"))
            shot("stuck")
            to_top(3)
            if not tap(r"^Next exercise: ", timeout=2):
                break
            stall = 0
            continue
        if todo:
            d, c, _ = todo[0]
            sid = d.split(":")[0]
            if "no weight" in d:
                # The weight cell in the same row (a first-time lift has none): type a start weight, Save & log.
                cells = [center(n) for n in nodes if re.match(r"^Weight not set, edit", label(n)) and center(n)]
                cell = min(cells, key=lambda q: abs(q[1] - c[1])) if cells else None
                if not cell or abs(cell[1] - c[1]) > 150:
                    # Bodyweight or band: no weight to type, the check logs it.
                    tap_xy(c); mine.add(lift + "|" + sid)
                    tap(rf"^{[2, 1, 3, 2][len(mine) % 4]} reps left", timeout=2); tap(r"^\|Skip$", timeout=1)
                    continue
                tap_xy(cell)
                tap(r"^Weight \((kg|lb)\), tap to type", timeout=3)
                adb("shell", "input", "text", start_weight(lift))
                hide_keyboard()
                if not tap(r"^\|Save & log$", timeout=3):
                    check(False, "the set editor has Save & log"); back(); continue
            else:
                tap_xy(c)
            mine.add(lift + "|" + sid)
            tap(rf"^{[2, 1, 3, 2][len(mine) % 4]} reps left", timeout=2)
            if len(mine) == 2 and day_no == 0:
                shot("rest")
            tap(r"^\|Skip$", timeout=1)
            continue
        # Nothing left in view: maybe below the fold, else the next lift, else the session is done.
        swipe_up(700)
        if any(r for r in set_rows(dump()) if (lift + "|" + r[0].split(":")[0]) not in mine and r[2].get("checked") != "true"):
            continue
        to_top(3)
        if not tap(r"^Next exercise: ", timeout=2):
            break
    note(f"logged {len(mine)} sets on {len(lifts)} lifts in {time.time() - t0:.0f}s")
    return finish_session()


def finish_session():
    to_top(2)
    tap(r"^\|Finish$|^Finish$", timeout=4)
    tap(r"^\|Finish$|^Finish$", timeout=3)
    time.sleep(2)
    shot("finish")
    fin = text_of(dump())
    m = re.search(r"(\d+) of (\d+) sets", fin)
    check(re.search(r"Workout (saved|done|complete)", fin) is not None, "the finish page opens")
    check(m is None, "every working set was logged" + (f" (finish page says {m.group(0)})" if m else ""))
    for ans in ("About right", "About right", "About right", "About right", "Moderate", "Recovered"):
        tap(rf"^\|{ans}$", timeout=1)
    if scroll_to(r"^\|Save session$", tries=16, step=700):
        shot("finish-bottom")
        tap(r"^\|Save session$")
    else:
        check(False, "the finish page has Save session")
    time.sleep(3)
    shot("after-save")
    to_top()
    check(find(r"(?i)done for today", timeout=4) is not None, "Today says done for today after saving")
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
check(bool(scroll_to(r"^\|Which units do you train in\?$", tries=3)), "onboarding asks kg or lb")
tap(r"^\|Kilograms · kg$")
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
for d in range(int(os.environ.get("MONTH_DAYS", "29"))):
    DAY[0] = f"d{d:02d}"
    now = start + dt.timedelta(days=d)
    set_date(now)
    launch()
    for _ in range(2):  # whatever greets the lifter today (a review, a prompt) gets one look and is closed
        if find(r"(?i)block .* review|review this block|what's new", timeout=1):
            shot("greeting"); back()
    if find(r"^Minimise workout\|", timeout=2):
        # Yesterday's workout is still open: the banner says so; finish it as the lifter would.
        check(find(r"Still open from", timeout=2) is not None, "a workout left open says how long ago it started")
        shot("left-open")
        finish_session(); sessions += 1
        launch()
    to_top()
    rest = find(r"Recover today|Rest day", timeout=2)
    scheduled = now.weekday() in (0, 2, 4, 6)  # the wizard's default four days: Mon, Wed, Fri, Sun
    check(bool(rest) != scheduled, ("a scheduled day offers a session" if scheduled else "an unscheduled day reads as a rest day"))
    # A block with no deload repeats: after its last week, Today is back on week 1 with nothing done.
    find(r"Week [0-9]+ of [0-9]+", timeout=2)
    m = next((re.search(r"Week ([0-9]+) of ([0-9]+)", label(n)) for n in dump() if re.search(r"Week [0-9]+ of [0-9]+", label(n))), None)
    total = int(m.group(2)) if m else 4
    exp = (sessions // 4) % total + 1
    check(bool(m) and int(m.group(1)) == exp, f"Today is on week {exp} after {sessions} sessions (shows {m.group(0) if m else 'no week'})")
    if not rest:
        ring = find(rf"^{sessions % 4} of 4 sessions done in week {exp}\|", timeout=2)
        check(ring is not None, f"the week ring reads {sessions % 4} of 4 done in week {exp}")
    # The block's four weeks are logged: Today says so and opens the block review, before week 1 starts again.
    if sessions == 16 and not rest:
        card = find(r"^\|?Block complete", timeout=3) or scroll_to(r"^\|?Block complete", tries=3)
        check(bool(card), "after the block's last week Today shows Block complete")
        if card:
            shot("block-complete")
            if tap(r"^\|?Review block$", timeout=4):
                check(find(r"Block 1 review|Block review", timeout=8) is not None, "Block complete opens the block review")
                shot("block-complete-review"); back(); time.sleep(1)
        to_top()
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
    if now.weekday() == 6 and d in (6, 27):
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
if int(os.environ.get("MONTH_DAYS", "29")) >= 29:
    check(sessions == 17, f"17 sessions over four weeks of Mon/Wed/Fri/Sun (got {sessions})")
with open(os.path.join(OUT, "notes.txt"), "w") as f:
    f.write("\n".join(NOTES))
fails = [c for c in CHECKS if c.startswith("FAIL")]
with open(os.path.join(OUT, "result.txt"), "w") as f:
    f.write(("FAIL" if fails else "PASS") + f" {len(CHECKS) - len(fails)}/{len(CHECKS)} checks\n" + "\n".join(fails + [c for c in CHECKS if c.startswith("PASS")]) + "\n")
print(open(os.path.join(OUT, "result.txt")).read().splitlines()[0])
sys.exit(1 if fails else 0)
