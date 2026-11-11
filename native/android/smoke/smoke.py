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


def _report_crash(kind, value, tb):
    """A crash anywhere still leaves a result: the traceback and the last step reached."""
    import traceback
    msg = "smoke crashed after step %d: %s" % (STEP[0], "".join(traceback.format_exception_only(kind, value)).strip())
    traceback.print_exception(kind, value, tb)
    try:
        with open(os.path.join(OUT, "result.txt"), "w") as f:
            f.write("\n".join(FAILS + [msg]))
    finally:
        sys.exit(1)


sys.excepthook = _report_crash


def adb(*a, check=False, timeout=60):
    try:
        r = subprocess.run(["adb", *a], capture_output=True, text=True, timeout=timeout)
    except subprocess.TimeoutExpired:
        # uiautomator dump waits for the UI to go idle; a screen that never idles must fail the step, not the run.
        print("adb timed out:", " ".join(a), flush=True)
        if check:
            raise
        return ""
    if check and r.returncode:
        raise RuntimeError(f"adb {' '.join(a)}: {r.stderr}")
    return r.stdout


def shot(name):
    STEP[0] += 1
    path = os.path.join(OUT, f"{STEP[0]:02d}-{name}.png")
    with open(path, "wb") as f:
        f.write(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True, timeout=60).stdout)
    print("shot", path, flush=True)


def record(name, taps, secs=5):
    """A short screen recording around taps (patterns located before recording, so the tap lands at once)."""
    try:
        pts = []
        for pat in taps:
            if isinstance(pat, (int, float)):
                pts.append(pat); continue
            p = find(pat, timeout=6)
            if not p:
                print("record: not found", pat, flush=True); return
            pts.append(p)
        rec = subprocess.Popen(["adb", "shell", "screenrecord", "--time-limit", str(secs), "--size", "540x1200", "/sdcard/rec.mp4"])
        time.sleep(1.5)
        for p in pts:
            if isinstance(p, (int, float)):
                time.sleep(p)
            else:
                adb("shell", "input", "tap", str(p[0]), str(p[1]))
        rec.wait(timeout=secs + 15)
        time.sleep(0.5)
        adb("pull", "/sdcard/rec.mp4", os.path.join(OUT, name + ".mp4"))
    except Exception as e:  # recording is a diagnostic; never fail the run on it
        print("record failed", name, e, flush=True)


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


def value_of(pattern, timeout=4, field=0):
    """A text field's value. uiautomator gives a Compose text field's description but not its text, so read the
    number off a screenshot inside the field's bounds."""
    rx = re.compile(pattern)
    end = time.time() + timeout
    while time.time() < end:
        for n in nodes():
            if rx.search(n.get("content-desc") or ""):
                t = (n.get("text") or "").strip()
                if t:
                    return t
                b = list(map(int, re.findall(r"\d+", n.get("bounds", ""))))
                return ocr_number(b) if len(b) == 4 else None
        time.sleep(0.6)
    return None


def ocr_number(b):
    try:
        from PIL import Image
        png = subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True, timeout=60).stdout
        path = os.path.join(OUT, "_field.png")
        with open(path, "wb") as f:
            f.write(png)
        # Enlarged with a margin: tesseract's line mode returned '' for a lone "8" in the reps field (it read "7" fine),
        # which failed the logged-set stepper check on an app that had corrected 8 → 7 → 8 correctly. If the line
        # read comes back empty, try word and single-character modes before giving up.
        # The plain crop is read first (enlarging it turned "45" into "4"); only an empty read retries enlarged.
        from PIL import ImageOps
        im = Image.open(path).crop(tuple(b)).convert("L")
        im.save(path)
        big = path.replace(".png", "-big.png")
        ImageOps.expand(im.resize((im.width * 3, im.height * 3)), border=40, fill=255 if im.getpixel((0, 0)) > 127 else 0).save(big)
        out = ""
        for src, psm in ((path, "7"), (big, "7"), (big, "8"), (big, "10")):
            out = subprocess.run(["tesseract", src, "-", "--psm", psm, "-c", "tessedit_char_whitelist=0123456789.,"],
                                 capture_output=True, text=True, timeout=60).stdout.strip()
            if out:
                break
        print("field ocr", b, "->", repr(out), flush=True)
        return out or None
    except Exception as e:
        print("field ocr failed", e, flush=True); return None


def find_desc(pattern, timeout=4):
    """The number in the first matching description ("Heavier: 62.5 kg" → 62.5)."""
    rx = re.compile(pattern)
    end = time.time() + timeout
    while time.time() < end:
        for n in nodes():
            d = n.get("content-desc") or ""
            if rx.search(d):
                m = re.search(r"[\d.]+", d)
                return float(m.group()) if m else None
        time.sleep(0.6)
    return None


def num(v):
    try:
        return float((v or "").replace(",", "."))
    except ValueError:
        return None


def seek(pattern, tries=8):
    """Find something on a long page from wherever it is scrolled: up first, then down."""
    return scroll_up_to(pattern, tries=tries) or scroll_to(pattern, tries=tries + 4)


def wizard(name, cycle=False, shots=False):
    """The setup (the prototype's flow): pick the format, a few pages of questions, then the ranked splits — wait for
    the best fit, name it, build."""
    if cycle:
        tap(r"^\|Training cycle$|Training cycle\|", "wizard-cycle")
    tap(r"^\|Continue$", "wizard-mode" if shots else None)
    for k in range(14):
        if shots and k < 5:
            shot(f"wizard-step-{k}")
        if k == 3 and shots and find(r"Skip to your ranked splits", timeout=1):
            shot("wizard-early")
        if find(r"^\|Build this (program|cycle)$", timeout=1):
            if not find(r"BEST FIT OF \d+", timeout=40):
                FAILS.append("ranked splits never showed a best fit")
            t0 = time.time()
            while find(r"SCORING", timeout=1) and time.time() - t0 < 120:
                time.sleep(2)
            print(f"ranked scoring took ~{time.time() - t0:.0f}s more")
            if find(r"SCORING", timeout=1):
                FAILS.append("ranked splits still scoring after 2 min")
            time.sleep(1.0)
            if shots:
                shot("wizard-ranked")
                if scroll_to(r"SPLITS, RANKED", tries=4, step=500):
                    shot("wizard-ranked-list")
                    if not find(r"^Rank 2: ", timeout=4):
                        FAILS.append("ranked splits list has no second split")
            if seek(r"Program name|Cycle name"):
                tap(r"Program name|Cycle name")
                adb("shell", "input", "text", name.replace(" ", "%s"))
                hide_keyboard()
                time.sleep(0.6)
            else:
                FAILS.append("ranked page has no name field")
            tap(r"^\|Build this (program|cycle)$")
            return True
        if not tap(r"^\|Next$", required=False):
            FAILS.append("wizard stuck")
            shot("wizard-stuck")
            return False
        time.sleep(0.5)
    FAILS.append("wizard never finished")
    return False


def row_scroll_to(pattern, anchor, tries=4):
    """Plan's actions: Edit program and Review block show; the rest are in the More plan actions menu."""
    if find(pattern, timeout=1):
        return True
    if tap(r"^More plan actions", required=False):
        return bool(find(pattern, timeout=3))
    return False


def dismiss_system_dialogs():
    """A cold emulator sometimes shows "<app> isn't responding" for the launcher; it is not ours. Wait it out."""
    hit = False
    # The dialog can stall uiautomator itself: close system dialogs from the shell (never a key press, which
    # would land on our app when no dialog is up), then make sure our app is the one in front.
    adb("shell", "am", "broadcast", "-a", "android.intent.action.CLOSE_SYSTEM_DIALOGS")
    resumed = [l for l in adb("shell", "dumpsys", "activity", "activities", timeout=30).splitlines() if "ResumedActivity" in l]
    if resumed and not any(PKG in l for l in resumed):
        adb("shell", "am", "start", "-n", f"{PKG}/com.pursuitiron.app.MainActivity")
        time.sleep(3)
        hit = True
    for _ in range(3):
        anr = find(r"isn't responding", timeout=1)
        if not anr:
            break
        w = find(r"^\|Wait$", timeout=1)
        if w:
            adb("shell", "input", "tap", str(w[0]), str(w[1]))
            hit = True
            time.sleep(2)
    return hit


def tap(pattern, name=None, timeout=12, required=True):
    p = find(pattern, timeout)
    if not p and dismiss_system_dialogs():
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


def tour(name, max_pages=9):
    """Full-length screens for the design comparison: a page at a time, top to bottom, into tour/."""
    d = os.path.join(OUT, "tour"); os.makedirs(d, exist_ok=True)
    last = None
    for k in range(max_pages):
        png = subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True, timeout=60).stdout
        if png == last:
            break
        with open(os.path.join(d, f"{name}_{k}.png"), "wb") as f:
            f.write(png)
        last = png
        adb("shell", "input", "swipe", "540", "1900", "540", "700", "600")
        time.sleep(1.2)
    for _ in range(max_pages):
        adb("shell", "input", "swipe", "540", "600", "540", "2000", "150")
    time.sleep(0.8)


def scroll_to(pattern, tries=6, step=900):
    for _ in range(tries):
        if find(pattern, timeout=2):
            return True
        adb("shell", "input", "swipe", "540", "1800", "540", str(1800 - step), "300")
        time.sleep(0.8)
    return bool(find(pattern, timeout=2))


def hide_keyboard():
    """Back closes the keyboard and nothing else, but only when the keyboard is actually up."""
    time.sleep(0.4)
    if "mInputShown=true" in adb("shell", "dumpsys", "input_method", timeout=30):
        adb("shell", "input", "keyevent", "4")
        time.sleep(0.8)


def back():
    adb("shell", "input", "keyevent", "4")
    time.sleep(1.2)


def scroll_up_to(pattern, tries=8):
    for _ in range(tries):
        if find(pattern, timeout=2):
            return True
        adb("shell", "input", "swipe", "540", "600", "540", "1900", "200")
        time.sleep(0.6)
    return bool(find(pattern, timeout=2))


def start_from_preview(name=None):
    """Build 898: Start opens the session preview (Time available); start the full session from it."""
    if not find(r"(?i)time available", timeout=6):
        return False
    if name:
        shot(name)
    tap(r"^Full,", required=False)
    return tap(r"Start workout$")


def close_sheet(pattern, tries=3):
    """Back until a sheet's own text is gone: a back press while the sheet is still animating in is ignored."""
    for _ in range(tries):
        back()
        if not find(pattern, timeout=2):
            return True
    FAILS.append("sheet did not close: " + pattern)
    return False


def launch():
    adb("shell", "am", "force-stop", PKG)
    adb("shell", "am", "start", "-W", "-n", f"{PKG}/com.pursuitiron.app.MainActivity")
    time.sleep(6)
    dismiss_system_dialogs()


def grant_notifications():
    tap(r"^\|Allow$|Allow$", timeout=4, required=False)


adb("logcat", "-c")
# Background ANR dialogs (a slow emulator launcher) are system noise; hide them like CI images usually do.
adb("shell", "settings", "put", "secure", "anr_show_background", "0")
adb("shell", "settings", "put", "global", "hide_error_dialogs", "1")
adb("shell", "settings", "put", "global", "window_animation_scale", "0.5")
adb("shell", "pm", "grant", PKG, "android.permission.POST_NOTIFICATIONS")

# 1. First run
launch()
shot("first-run")
# Onboarding asks kg or lb before anything is built (the web wizard's first step); the emulator's US locale preselects lb.
if not scroll_to(r"^\|Which units do you train in\?$", tries=3):
    FAILS.append("onboarding does not ask kg or lb")
elif not find(r"^\|Pounds · lb$", timeout=2):
    FAILS.append("onboarding unit options missing")
else:
    tap(r"^\|Kilograms · kg$")
    shot("first-run-units")
tap(r"^\|Build my program$", "setup")  # the welcome screen opens the builder
wizard("Smoke Program", shots=True)
time.sleep(4)
shot("today-with-program")
time.sleep(3)
shot("today")
# The design's Today: week strip, the coach's game plan and this week's days
# (a rest day shows recovery instead — the run's date decides which, so only a training day must have it)
if not find(r"Recover today", timeout=2) and not find(r"Today's game plan", timeout=3) and not scroll_to(r"Today's game plan", tries=4):
    FAILS.append("no game plan on Today")
else:
    shot("today-game-plan")
if not scroll_to(r"^\|This week$", tries=4):
    FAILS.append("no This week list on Today")
scroll_up_to(r"Start workout|^\|?Train .+ now", tries=8)
# The design's check-in sits on Today (training days); readiness is applied when the workout opens
# It sits under the session, folded to one row until opened
if find(r"How are you today\?", timeout=3) or scroll_to(r"How are you today\?", tries=3, step=500):
    tap(r"^How are you today\? Optional check-in", "check-in-open", required=False)
    tap(r"^Sleep: Great", "check-in")
    tap(r"^Energy: High")
    # Save sits at the foot of the open check-in, below the fold on a short screen
    if not find(r"^\|Save$|^\|Done$", timeout=2):
        scroll_to(r"^\|Save$|^\|Done$", tries=3, step=400)
    tap(r"^\|Save$|^\|Done$")
    if not find(r"^Edit today's check-in", timeout=4):
        FAILS.append("check-in did not fold into its chip")

# 2. Workout: enter a weight, log, rate, rest
# The M3 Expressive split button: its menu holds the other ways into today
scroll_up_to(r"Start workout|^\|?Train .+ now", tries=8)
if tap(r"^More ways to start", "start-split", required=False):
    if not find(r"Start now, skip the preview", timeout=3):
        FAILS.append("start split button menu missing its options")
    back()
tap(r"Start workout|^\|?Train .+ now")
# Build 898 session preview: try a shorter limit, then start the full session
if find(r"(?i)time available", timeout=6):
    shot("session-preview")
    if tap(r"^30 minutes,", "preview-30", required=False):
        if not find(r"working sets", timeout=3):
            FAILS.append("preview lost its summary after a limit")
    start_from_preview()
else:
    FAILS.append("Start did not open the session preview")
time.sleep(3)
grant_notifications()
grant_notifications()
shot("workout")
# App.js warmupRoutine: the warm-up & mobility card leads the first lift until a set is logged
if find(r"Warm-up & mobility", timeout=3):
    tap(r"Warmed up", "warmup-done")
else:
    FAILS.append("no warm-up card on the first lift")
# First workout: the one-line effort tip above the sets, dismissed for good
if find(r"good reps left", timeout=3):
    shot("effort-tip")
    tap(r"^\|Got it$")
else:
    FAILS.append("no effort tip on the first workout")
if tap(r"Weight not set, edit", "set-editor"):
    tap(r"^Weight \((kg|lb)\), tap to type")  # the design's big value, typed in place
    adb("shell", "input", "text", "60")
    time.sleep(0.6)
    shot("set-editor-typed")
    hide_keyboard()
    time.sleep(0.6)
    # The steppers start from the typed value: + from a typed 60 goes up from 60, and − comes back to it.
    W = r"^Weight \((kg|lb)\), tap to type"
    if tap(r"^More weight\|", required=False):
        time.sleep(1)
        up = num(value_of(W, field=0))
        tap(r"^Less weight\|", required=False); time.sleep(1)
        back_to = num(value_of(W, field=0))
        if not (up and up > 60 and back_to == 60):
            FAILS.append(f"set editor steppers: typed 60, + gave {up}, − gave {back_to}")
        shot("set-editor-stepped")
    else:
        FAILS.append("set editor has no weight stepper")
    if tap(r"Open the plate calculator$", "set-plates", required=False):  # only barbell lifts load plates
        back()
    tap(r"^\|Save$", "set-editor-save")
tap(r"^Set 1:", "set-logged")
tap(r"^2 reps left", "set-rated")
# The rest ring pauses and resumes the rest (App.js WorkoutRestActions)
if tap(r"^Pause rest", required=False):
    if not find(r"^Resume rest", timeout=3):
        FAILS.append("pausing the rest does not offer Resume")
    else:
        shot("rest-paused")
        tap(r"^Resume rest", required=False)
else:
    FAILS.append("rest card has no pause")
# The design's Why opens in place on the card
if tap(r"Why .* kg\?|Why .* reps\?", "why-inline"):
    tap(r"^Close explanation", required=False)
# The design's header: focus mode, the exercise list, and "Short on time?" from the clock
if tap(r"^Focus mode", "focus-mode"):
    c = find(r"Log (set|warm-up): press and hold|Reps left in the tank", timeout=4)
    if not c:
        FAILS.append("focus mode shows no set to log")
    else:
        # Entering focus brings the set to the top: its log control must sit above the rest card and the dock, no scrolling.
        lows = [p[1] for p in (find(r"^Rest, ", timeout=1), find(r"^\|Finish$", timeout=1)) if p]
        if lows and c[1] > min(lows) - 60:
            FAILS.append(f"focus mode: log control at y={c[1]} is under the rest card or dock ({min(lows)})")
    tap(r"^Focus mode")
# Discard moved off the header into the workout menu (it sat one icon from Focus)
if find(r"^Discard workout", timeout=1):
    FAILS.append("Discard workout is still a header button")
if tap(r"^Workout options", "workout-menu", required=False):
    if not find(r"Discard workout", timeout=3):
        FAILS.append("workout menu has no Discard workout")
    back()
    time.sleep(0.8)
else:
    FAILS.append("no Workout options menu in the workout header")
if tap(r"^All exercises", "exercise-list"):
    if not find(r"Today's exercises", timeout=4):
        FAILS.append("exercise list sheet did not open")
    close_sheet(r"Today's exercises")
if tap(r"Short on time\?", "time-sheet"):
    if not find(r"Pause workout timer", timeout=4):
        FAILS.append("short-on-time sheet has no pause")
    close_sheet(r"Pause workout timer")
else:
    FAILS.append("the workout clock does not open Short on time")
if tap(r"Weight 60(\.0)?, edit", "edit-logged-set"):
    if not find(r"^\|Set 1$", timeout=3) or find(r"^\|Save & log$", timeout=1):
        FAILS.append("a logged set's editor is not titled Set 1 with Save only")
    # A logged set's − and + correct it in place (they did nothing: the core locks logged sets to edits).
    R = r"^Reps, tap to type"
    r0 = num(value_of(R, field=1))
    tap(r"^Fewer reps\|", required=False); time.sleep(1)
    r1 = num(value_of(R, field=1))
    tap(r"^More reps\|", required=False); time.sleep(1)
    r2 = num(value_of(R, field=1))
    if r0 is None or r1 != r0 - 1 or r2 != r0:
        FAILS.append(f"a logged set's rep stepper does not correct it ({r0} → {r1} → {r2})")
    shot("logged-set-stepped")
    back()
# Full-screen head (as on the web): History and Skip sit beside Info; Skip has Undo
shot("workout-head")
if tap(r"^History for ", "head-history", required=False):
    time.sleep(1.2)
    back()
    time.sleep(0.8)
else:
    FAILS.append("no History button beside the exercise name")
head_name = next((n.get("content-desc")[:-len(": how to do it")] for n in nodes() if (n.get("content-desc") or "").endswith(": how to do it")), None)
if tap(r"^Skip .+ for today\|?$", "head-skip", required=False):
    if not tap(r"^\|?Undo$", "skip-undo", timeout=4, required=False):
        FAILS.append("skipping a lift offers no Undo")
    elif head_name and not find("^" + re.escape(head_name) + ": how to do it", timeout=4):
        FAILS.append("Undo did not bring the skipped lift back")
else:
    FAILS.append("no Skip button beside the exercise name")
tap(r"Exercise actions", "exercise-menu")
# B903: change today's working-set count; the finish page asks whether the program keeps it
if (find(r"One more working set", timeout=3) or scroll_to(r"One more working set", tries=3, step=400)):
    tap(r"One more working set", "set-count")
else:
    FAILS.append("exercise menu has no working-set count")
back()
# The in-workout swap: alternatives for this lift at this gym (it threw in the core and always read "No alternatives").
tap(r"Exercise actions")
if scroll_to(r"Swap exercise", tries=3, step=400) and tap(r"Swap exercise", "swap-sheet"):
    time.sleep(1.5)
    if find(r"No other lift for this muscle", timeout=2):
        FAILS.append("in-workout swap lists no alternatives")
    # The web's search and badges: every ranked option is searchable, each says why it ranks there
    if not find(r"(?i)same pattern|top match|fills gap|extra setup", timeout=4):
        FAILS.append("swap options carry no ranking badge")
    # Filter chips over the whole ranked list; Top 5 leaves at most five numbered rows.
    if not find(r"^\|?All$", timeout=3):
        FAILS.append("swap sheet has no filter chips")
    elif tap(r"^\|?Top 5$", required=False):
        time.sleep(0.8)
        if not find(r"^Rank 1: ", timeout=2):
            FAILS.append("swap Top 5 filter shows no ranked rows")
        elif find(r"^Rank 6: ", timeout=1):
            FAILS.append("swap Top 5 filter shows a sixth option")
        shot("swap-top5")
        tap(r"^\|?All$", required=False)
    if tap(r"Search replacements"):
        adb("shell", "input", "text", "press")
        time.sleep(1.2)
        shot("swap-search")
        hide_keyboard()
    for _ in range(3):
        back()
        if not find(r"^\|?Swap exercise$", timeout=2):
            break
    else:
        FAILS.append("the swap sheet does not close with Back")
else:
    back()
# The superset sheet: the first lift is the main lift (no suggestion), but you can still pair it yourself (App.js "Pair with next anyway")
tap(r"Exercise actions")
if scroll_to(r"Suggested partners", tries=3, step=400) and tap(r"Suggested partners", "superset-sheet"):
    if not find(r"Pair with next anyway", timeout=3):
        FAILS.append("superset sheet has no Pair with next anyway")
    close_sheet(r"Pair with next anyway")
else:
    back()
# The progression tag on the card opens the progression sheet (it is no longer in the menu)
if tap(r"^Progression: ", "progression-sheet"):
    if not find(r"Auto \(recommended\)", timeout=4):
        FAILS.append("progression sheet has no Auto option")
    back()
# The design's set options: the set number opens type, skip and (for added sets) remove
if tap(r"^Options for set 2", "set-options"):
    if not find(r"Skip this set", timeout=4):
        FAILS.append("set options has no Skip this set")
    close_sheet(r"Skip this set")
# The design's plate bar under the sets opens the plate calculator (barbell lifts with a load)
if find(r"^Open the plate calculator", timeout=3):
    tap(r"^Open the plate calculator", "plate-bar-calc")
    # + and − land on totals the plates make: the total becomes the step's own figure.
    T = r"^Total weight, "
    hv = find_desc(r"^Heavier: [\d.]+ ")
    t0 = num(value_of(T))
    if hv and tap(r"^Heavier: ", required=False):
        time.sleep(1.2)
        t1 = num(value_of(T))
        if t1 != hv or (t0 is not None and not t1 > t0):
            FAILS.append(f"plate calculator +: {t0} → {t1}, expected {hv}")
        lt = find_desc(r"^Lighter: [\d.]+ ")
        tap(r"^Lighter: ", required=False); time.sleep(1.2)
        if num(value_of(T)) != lt:
            FAILS.append(f"plate calculator −: expected {lt}, got {value_of(T)}")
        shot("plate-calc-stepped")
    else:
        FAILS.append("plate calculator has no + step")
    close_sheet(r"Plate calculator")
# The design's add row: + Add set adds one straight away; the ⋯ beside it opens the panel with warm-ups
if scroll_to(r"^Add set", tries=4, step=500):
    tap(r"^Add set", "set-added")
    if not find(r"^Options for set \d+.*", timeout=3):
        FAILS.append("set numbers are not set-option buttons")
    tap(r"^Skip$|\|Skip$", required=False)  # the rest card floats over the add row
    scroll_to(r"^More set options", tries=4, step=400)
    tap(r"^More set options", "add-set-sheet")
    tap(r"Warm-up ramp", "warmups-added")
else:
    FAILS.append("no Add set button under the sets")
for _ in range(3):
    adb("shell", "input", "swipe", "540", "700", "540", "1900", "200")
tap(r"Exercise actions")
# The design's "Rest, goal & setup": rest chips, goal stepper, machine step and the pinned note in one sheet
if scroll_to(r"Rest, goal & setup", tries=3):
    tap(r"Rest, goal & setup", "lift-settings")
    tap(r"^\|3 min$", "rest-set")
    if not find(r"GOAL WEIGHT", timeout=4):
        FAILS.append("lift settings has no goal weight")
    close_sheet(r"GOAL WEIGHT")
else:
    FAILS.append("no Rest, goal & setup in the exercise menu")
# B932: the pinned note saves the cue and setup together and shows on the lift
# The pin lives in the lift's menu until a note is pinned (no empty chip on every lift)
tap(r"Exercise actions")
if tap(r"^\|?Pin a note$|^Pin a note\|", "pin-note", required=False):
    if tap(r"^\|Note$|Note\|", required=False):
        adb("shell", "input", "text", "Pause%son%schest")
        hide_keyboard()
        time.sleep(0.6)
    tap(r"^\|Save note$")
    if not find(r"Pinned note for .*Pause on chest", timeout=6):
        FAILS.append("pinned note did not show on the lift after Save")
    shot("pin-saved")
else:
    FAILS.append("no pinned-note chip on the lift")
tap(r"Exercise actions")
tap(r"Something hurt", "hurt-sheet")
# The design's three steps: where, how bad, the plan (dismissed without applying)
if find(r"STEP 1 OF 3", timeout=4):
    tap(r"^\|Shoulder$|Shoulder\|")
    tap(r"^\|Next$")
    tap(r"^\|Sore$|Sore\|", "hurt-level")
    tap(r"^\|Next$")
    if not find(r"Here's the plan", timeout=4) or not find(r"Feels fine now", timeout=2):
        FAILS.append("Something hurt does not reach its plan step")
    shot("hurt-plan")
else:
    FAILS.append("Something hurt is not the three-step sheet")
adb("shell", "input", "keyevent", "4")
time.sleep(1)
tap(r"workout note", "session-note")
tap(r"How did it go")
adb("shell", "input", "text", "Busy%sgym")
hide_keyboard()
time.sleep(0.6)
tap(r"Slept badly", "session-note-tag")
tap(r"^\|Done$")
if not find(r"^Workout note: ", timeout=4):
    FAILS.append("workout note not saved to the header")

# 3. Minimise and resume through the mini bar
tap(r"Minimise workout", "minimised")
tap(r"Resume workout", "resumed")
# Out of the app mid-workout: the workout (or its rest) stays in the shade as an ongoing card
adb("shell", "input", "keyevent", "KEYCODE_HOME")
time.sleep(2.5)
nd = adb("shell", "dumpsys", "notification", "--noredact") or ""
if PKG not in nd or not re.search(r"Next: |Tap to finish|Rest", nd):
    FAILS.append("no ongoing workout notification out of the app")
# Log set from the shade: Skip any rest, then the workout card's Log set logs the next set and starts its rest.
# Out of the app, so no tap(): its recovery closes the shade and brings the app back to the front.
def ocr_find(words):
    """Where the words sit on screen, read off a screenshot. The shade's cards tick (the rest and session clocks),
    so uiautomator often can't get the idle screen it needs to dump them."""
    try:
        png = subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True, timeout=60).stdout
        path = os.path.join(OUT, "_ocr.png")
        with open(path, "wb") as f:
            f.write(png)
        tsv = subprocess.run(["tesseract", path, "-", "tsv"], capture_output=True, text=True, timeout=60).stdout
    except Exception as e:
        print("ocr failed", e, flush=True); return None
    rows = [r.split("\t") for r in tsv.splitlines()[1:]]
    toks = [r for r in rows if len(r) == 12 and r[11].strip()]
    want = [w.lower() for w in words]
    for i in range(len(toks) - len(want) + 1):
        seq = toks[i:i + len(want)]
        if [t[11].strip().lower().strip(".,:") for t in seq] == want and len({tuple(t[2:5]) for t in seq}) == 1:
            x1 = int(seq[0][6]); y1 = int(seq[0][7])
            x2 = int(seq[-1][6]) + int(seq[-1][8]); y2 = max(int(t[7]) + int(t[9]) for t in seq)
            return (x1 + x2) // 2, (y1 + y2) // 2
    return None


def shade_tap(pattern, timeout, words=None):
    p = find(pattern, timeout=timeout)
    if not p and words:
        dump = adb("shell", "uiautomator", "dump", "/sdcard/ui.xml", timeout=40)
        print("shade dump:", (dump or "(no output)").strip()[:200], flush=True)
        p = ocr_find(words)
        print("shade ocr", " ".join(words), "->", p, flush=True)
    if p:
        adb("shell", "input", "tap", str(p[0]), str(p[1]))
        time.sleep(1.2)
    return bool(p)
adb("shell", "cmd", "statusbar", "expand-notifications")
time.sleep(2)
if shade_tap(r"(?i)(^|\|)skip(\||$)", 3, ["Skip"]):
    time.sleep(2.5)
if not find(r"(?i)(^|\|)log set(\||$)", timeout=4):
    # A collapsed card hides its buttons: its expand chevron opens it (a tap on the body would open the app).
    if shade_tap(r"(?i)(^|\|)expand", 3):
        time.sleep(1.5)
if not shade_tap(r"(?i)(^|\|)log set(\||$)", 5, ["Log", "set"]):
    nd = adb("shell", "dumpsys", "notification", "--noredact") or ""
    mine = [l.strip() for l in nd.splitlines() if re.search(r"android\.(title|text)=|Log set", l)]
    print("shade notification:", "\n".join(mine[:40]), flush=True)
    shot("missing-shade-log-set")
    FAILS.append("no Log set on the workout card out of the app" + (" (posted, not shown)" if "Log set" in nd else " (not posted)"))
else:
    shot("shade-log-set")
    time.sleep(3)
    nd = adb("shell", "dumpsys", "notification", "--noredact") or ""
    if not re.search(r"android\.title=String \((Rest|Warm-up rest|Myo rest)\)", nd):
        FAILS.append("Log set from the shade did not log the set and start the rest")
    shot("shade-after-log")
adb("shell", "cmd", "statusbar", "collapse")
time.sleep(1)
adb("shell", "am", "start", "-W", "-n", f"{PKG}/com.pursuitiron.app.MainActivity")
time.sleep(2)
find(r"Finish", timeout=8)

# 4. Finish
tap(r"^\|Finish$|^Finish$")
tap(r"^\|Finish$|^Finish$", timeout=5, required=False)  # "N sets not logged" confirm
time.sleep(1)
shot("finish-sheet")
if not find(r"Workout done", timeout=5):
    FAILS.append("finish page missing")
if tap(r"^\|Too easy$", "finish-feel", required=False):
    if not find(r" sets$|No room", timeout=3):
        FAILS.append("feel answer did not show next time's sets")
tap(r"^\|Moderate$", "finish-feedback", required=False)
if scroll_to(r"YOU CHANGED THE WORKOUT|KEEP THESE CHANGES", tries=6, step=500):
    tap(r"^\|Keep in program$", "finish-keep", required=False)
else:
    FAILS.append("finish page does not offer the set-count change to the program")
scroll_to(r"^\|Save session$", tries=8)
tap(r"^\|Save session$")
if not find(r"[Ss]aved", timeout=6):
    FAILS.append("no saved toast after Save session")
shot("summary")

# 4b. Quick workout, saved as a routine
if scroll_to(r"Quick workout", tries=3):
    tap(r"Quick workout")
    time.sleep(2)
    # The design's picker: pick lifts in order, then Start · N lifts
    if not find(r"Pick at least one lift", timeout=6):
        FAILS.append("Quick workout does not open the lift picker")
    shot("quick-picker")
    tap(r"Search exercises")
    adb("shell", "input", "text", "curl")
    hide_keyboard()
    time.sleep(1.5)
    tap(r"^Add [A-Za-z -]*Curl", "quick-picked")
    if not tap(r"^\|?Start · 1 lift", "quick-added"):
        FAILS.append("the picker has no Start · 1 lift after picking")
    time.sleep(2)
    tap(r"^\|Skip$", required=False)  # the readiness check-in
    if tap(r"Weight not set, edit"):
        tap(r"^Weight \((kg|lb)\), tap to type")
        adb("shell", "input", "text", "12")
        hide_keyboard()
        time.sleep(0.6)
        # Run 159: the keyboard stayed up over Save and every later step ran under the open sheet. Escape and
        # retry before giving up, and never leave the editor open behind us.
        for _ in range(3):
            if tap(r"^\|Save$", timeout=3, required=False):
                break
            adb("shell", "input", "keyevent", "111")
            time.sleep(0.8)
        else:
            FAILS.append("quick set editor: Save never became reachable")
            back(); back()
    tap(r"^Set 1:", "quick-logged")
    tap(r"^\|Finish$|^Finish$")
    tap(r"^\|Finish$|^Finish$", timeout=5, required=False)
    time.sleep(1)
    scroll_to(r"^\|Save routine$", tries=6)
    tap(r"^\|Save routine$")
    shot("quick-finish")
    scroll_to(r"^\|Save session$", tries=6)
    tap(r"^\|Save session$")
    time.sleep(2)
    shot("quick-summary")
else:
    FAILS.append("no Quick workout entry on Today")

# 5. Tabs
for t in ["Plan", "Progress", "Exercises", "You"]:
    tap(rf"^\|{t}$|^{t}\|", t.lower())
# Motion: the avatar travels from Today's header to the middle of You; tabs fade through
tap(r"^\|Today$|^Today\|")
time.sleep(1.5)
record("motion-avatar", [r"^You(: [^|]*)?\|"])
time.sleep(1)
record("motion-tabs", [r"^\|Plan$|^Plan\|", r"^\|Progress$|^Progress\|"])

# 5a. Exercises: equipment filter and the count
tap(r"^\|Exercises$|^Exercises\|")
# Show and Equipment are dropdown chips on one row with the count
if tap(r"^Equipment: ", required=False) and tap(r"^\|Barbell$", "library-barbell"):
    if not find(r"^\d+\+? exercises?", timeout=4):
        FAILS.append("exercise count missing")
    tap(r"^Equipment: ")
    tap(r"^\|Any equipment$")
else:
    FAILS.append("no equipment filter on Exercises")
# An exercise's own sheet (the design's exercise detail)
if tap(r"^\|Barbell Bench Press$", "exercise-detail", required=False):
    time.sleep(1.5)
    shot("exercise-detail-k")
    back()

# 5b. Plan: volume wave and the week-by-week lift table
tap(r"^\|Plan$|^Plan\|")
if scroll_to(r"Every lift, week by week", tries=4):
    shot("volume-wave")
    tap(r"Every lift, week by week", "week-by-week")
    if not find(r"^\|Week by week$", timeout=6):
        FAILS.append("week-by-week sheet did not open")
    close_sheet(r"^\|Week by week$")
else:
    FAILS.append("no week-by-week entry on Plan")
# Sets by muscle on Plan (App.js VolumeCard): tap a muscle for its lifts; Heads splits shoulders into delt heads
MUSCLE_ROW = r"^[A-Z][A-Za-z /]+: [0-9.]+ sets, .* lifts"
if seek(r"^\|?Sets by muscle$|Working sets · .* muscles|working sets · .* muscles"):
    shot("muscle-volume")
    if scroll_to(MUSCLE_ROW, tries=3, step=300) and tap(MUSCLE_ROW, "muscle-lifts"):
        time.sleep(0.8)
        shot("muscle-lifts-open")
    else:
        FAILS.append("sets by muscle has no tappable muscle")
    if scroll_up_to(r"^Show muscle heads", tries=6) and tap(r"^Show muscle heads", "muscle-heads"):
        if not seek(r"^(Front|Side|Rear) Delts: "):
            FAILS.append("Heads shows no delt heads")
        else:
            shot("muscle-heads-on")
        scroll_up_to(r"^Show muscle heads", tries=8)
        tap(r"^Show muscle heads", required=False)
    else:
        FAILS.append("sets by muscle has no Heads switch")
else:
    FAILS.append("no sets by muscle on Plan")
# The design's Programs (the program pill under Plan): every program, then shared programs (added without switching)
scroll_up_to(r"^Program: ", tries=10)
prog_open = tap(r"^Program: ", "programs-sheet")
# The design's Add a shared program: paste a code, see what it is
if prog_open and scroll_to(r"Add a shared program", tries=4, step=500):
    tap(r"Add a shared program")
    tap(r"^\|Paste a sample code$", "add-shared")
    if not find(r"^\|Add to my programs$", timeout=6):
        FAILS.append("a pasted program code shows no preview")
    shot("add-shared-preview")
    back()
    time.sleep(1)
else:
    FAILS.append("no Add a shared program in Programs")
if prog_open and scroll_to(r"Browse shared programs", tries=4, step=500):
    tap(r"Browse shared programs", "gallery")
    if tap(r"^Import ", "gallery-import", required=False):
        if not find(r"is in your programs|Added to your programs", timeout=20):
            FAILS.append("gallery import did not add the program")
        shot("gallery-added")
    close_sheet(r"^\|Shared programs$")
    for _ in range(4):
        adb("shell", "input", "swipe", "540", "700", "540", "1900", "200")
else:
    FAILS.append("no Programs sheet with shared programs on Plan")
# The design's program rows: options in place (Program settings on the active one; Duplicate, then the copy
# opens with its name field, Switch now and Queue for next block; Delete with undo)
scroll_up_to(r"^Program: ", tries=10)
if tap(r"^Program: ", "programs-rows", required=False) and tap(r"^\|ACTIVE$", "program-active-open", required=False):
    if not find(r"^\|Program settings$", timeout=3) or not find(r"Switch to another before deleting it", timeout=2):
        FAILS.append("the active program row has no Program settings or delete note")
    # The row shows its main action and Edit; the rest is in its More menu
    tap(r"^More for ", required=False)
    if tap(r"^\|Duplicate$", "program-duplicate", required=False):
        if not find(r"^\|Program name$|^Program name\|", timeout=4):
            FAILS.append("a duplicated program does not open its name field")
        tap(r"^\|Cancel$", required=False)
        if not find(r"^\|Switch now$", timeout=3):
            FAILS.append("another program's row has no Switch now")
        tap(r"^More for .*\(copy\)", required=False) or tap(r"^More for ", required=False)
        if tap(r"^\|Queue for next block$", "program-queue", required=False):
            if not find(r"queued · starts after this block|^\|QUEUED$", timeout=4):
                FAILS.append("queueing a program shows no QUEUED badge or toast")
            shot("program-queued")
        else:
            FAILS.append("no Queue for next block on another program")
        tap(r"^More for .*\(copy\)", required=False) or tap(r"^More for ", required=False)
        if not tap(r"^\|Delete$", required=False):
            FAILS.append("no Delete on the copied program")
    else:
        FAILS.append("no Duplicate on the program row")
    back()
    time.sleep(1)
else:
    FAILS.append("could not open a program row's options")
# Lift progression lives in Program settings (the Plan action row): the block's methods, each lift's reason
scroll_up_to(r"^\|Edit program$|Review block", tries=10)
# The design's Block review page: sessions against the plan (or why it's too early), with Start next block
if tap(r"Review block", "block-review", required=False):
    if not find(r"\|Block \d+ review$", timeout=10):
        FAILS.append("block review page did not open")
    elif not find(r"sessions done|not enough|No sessions were logged|Too early to review", timeout=10):
        FAILS.append("block review page has no session summary")
    shot("block-review")
    close_sheet(r"\|Block \d+ review$")
    scroll_up_to(r"^\|Edit program$|Review block", tries=10)
row_scroll_to(r"^\|Settings$", r"Review block|^\|Schedule$|^\|Cycle$")
tap(r"^\|Settings$", "program-settings")
if find(r"TRAINING GOAL", timeout=15):
    # B905: a changed setting is previewed by the engine before anything is saved
    if tap(r"^\|Strength$|^Strength\|", "settings-goal", required=False):
        time.sleep(6)
        if not scroll_to(r"^\|Save · \d+ change|don’t change any lifts|isn't possible", tries=6, step=600):
            FAILS.append("program settings did not preview the rebuild")
        shot("settings-preview")
        tap(r"^\|Reset$", required=False)
elif not find(r"set by its training cycle", timeout=3):
    FAILS.append("program settings did not open")
if scroll_to(r"Pursuit chose per exercise|re-checked|locked by your choices", tries=10, step=600):
    shot("lift-progression")
else:
    FAILS.append("no lift progression in Program settings")
if not scroll_to(r"Start again from week 1|set by its training cycle", tries=10, step=700):
    FAILS.append("program settings has no layout section")
close_sheet(r"LIFT PROGRESSION|Start again from week 1")
# The design's Details: how the program was built and the block plan
scroll_up_to(r"^\|Edit program$|Review block|^\|Settings$", tries=10)
row_scroll_to(r"^\|Details$", r"^\|Settings$|^\|Schedule$|^\|Cycle$")
if tap(r"^\|Details$", "program-details"):
    if not scroll_to(r"(?i)block plan", tries=6, step=600):
        FAILS.append("program details has no block plan")
    close_sheet(r"Program details|(?i:block plan)")
# Why this lift: a Plan day row opens the engine's reasoning for that slot
if scroll_to(r"^Why .+\?", tries=4):
    tap(r"^Why .+\?", "why-this-lift")
    pat = r"Main strength lift|Compound for size|Isolation for size|You added this lift|You changed this slot"
    if not find(pat, timeout=5):
        FAILS.append("why-this-lift sheet missing its reason")
    close_sheet(pat)
else:
    FAILS.append("no why-this-lift rows on Plan")

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
# The design comparison tour: every main screen, full length, with the imported history
tour("today")
for tab_name in ["Plan", "Progress", "Exercises"]:
    tap(rf"^\|{tab_name}$|^{tab_name}\|")
    time.sleep(1.5)
    tour(tab_name.lower())
    if tab_name == "Progress" and tap(r"^\|History$", required=False):
        time.sleep(1.2)
        tour("progress-history")
        tap(r"^\|Trends$", required=False)
tap(r"^\|You$|^You\|")
time.sleep(1.5)
tour("you")
for sub in ["Body", "Settings"]:
    if tap(rf"^\|{sub}$|^{sub}\|", required=False):
        time.sleep(1.2)
        tour("you-" + sub.lower())
tap(r"^\|Profile$|^Profile\|", required=False)
tap(r"^\|Today$|^Today\|")
time.sleep(1.5)
if scroll_to(r"Fresher today|A deload looks due|Muscle recovery|This week|Strength score", tries=4):
    shot("today-insights")
# The design's weekly recap: the last seven days, four figures and Share recap
# The insights scroll above can stop below the recap (it sits above This week), so look both ways.
RECAP = r"^\|LAST 7 DAYS · |^Hide the weekly recap"
# A slow accessibility dump (animations on Today) can miss it on one pass: go back to the top and look again, slower.
if scroll_to(RECAP, tries=6, step=500) or scroll_up_to(RECAP, tries=12) or (time.sleep(2) or scroll_to(RECAP, tries=8, step=350)):
    if not find(r"^\|Share recap$", timeout=2) and not scroll_to(r"^\|Share recap$", tries=2, step=300):
        FAILS.append("weekly recap has no Share recap")
    shot("today-recap")
else:
    FAILS.append("no weekly recap on Today after a logged week")
# The design's readiness tile opens in place into the muscle map and the per-muscle list
if scroll_to(r"^\|Readiness$", tries=6, step=500) or scroll_up_to(r"^\|Readiness$"):
    tap(r"^\|Readiness$", "readiness-open", required=False)
    if not find(r"^\|Muscle readiness$", timeout=3):
        FAILS.append("readiness tile does not open the muscle map")
    else:
        if not find(r"^Fatigue map\. ", timeout=2):
            FAILS.append("readiness panel has no body map")
        shot("today-readiness")
        tap(r"^Collapse readiness\|", required=False)
scroll_up_to(r"^\|Today$", tries=10)
if scroll_to(r"^\|Recent$", tries=6):
    adb("shell", "input", "swipe", "540", "1500", "540", "1000", "300")
    time.sleep(0.8)
    if tap(r"^\|[A-Z][a-z]{2} \d+ · ", "history-sheet", required=False):
        # The design's session sheet: stats, sets by lift, working sets by muscle, then share as a card
        if not find(r"^\|WORKING SETS BY MUSCLE$", timeout=3) and not scroll_to(r"^\|WORKING SETS BY MUSCLE$", tries=4, step=500):
            FAILS.append("session sheet has no working sets by muscle")
        if scroll_to(r"Share this session", tries=4, step=500):
            tap(r"Share this session")
            if not scroll_to(r"^\|Copy text$", tries=3, step=400):
                FAILS.append("Share this session shows no card with Copy text")
            shot("history-share")
        scroll_up_to(r"Correct this log", tries=3)
        if tap(r"Correct this log", "history-correct"):
            back()
        else:
            back()

# 6b. Progress: a lift's chart, strength standards
tap(r"^\|Progress$|^Progress\|", "progress-after-import")
if not find(r"vs last week|^\|Volume$", timeout=3):
    FAILS.append("weekly volume chart missing on Progress")
# The finish page's muscle answers feed Recovery & feedback
if scroll_to(r"^\|Recovery & feedback$", tries=8, step=600):
    if not find(r"next time\.|Sets hold\.", timeout=2) and not scroll_to(r"next time\.|Sets hold\.", tries=2, step=300):
        FAILS.append("Recovery & feedback rows have no next-time note")
    shot("progress-feedback")
else:
    FAILS.append("no Recovery & feedback on Progress after answering how a muscle felt")
scroll_up_to(r"vs last week|^\|Volume$", tries=12)
# Pinned lifts (web chart favourites): Manage opens the panel
scroll_to(r"Open lift history|^Manage pinned lifts", tries=10, step=600)
if tap(r"Open lift history", "lift-detail", required=False):
    if not find(r"Pin(ned)? to Progress", timeout=4):
        FAILS.append("lift detail has no pin toggle")
    close_sheet(r"Pin(ned)? to Progress")
if tap(r"^Manage pinned lifts", "pins-sheet"):
    if not find(r"of 6 pinned", timeout=4):
        FAILS.append("pins sheet missing its count")
    close_sheet(r"of 6 pinned")
else:
    FAILS.append("pinned lifts section missing on Progress")
if find(r"coroutine scope|Something went wrong", timeout=1):
    FAILS.append("an error snackbar showed on Progress")
if scroll_to(r"Intensity distribution", tries=6):
    shot("intensity")
else:
    FAILS.append("intensity distribution missing on Progress")
if scroll_to(r"Strength standards", tries=5):
    shot("strength-standards")

# 7. Profile, calendar, settings, gyms, tools, unit switch
tap(r"^\|You$|^You\|")
tap(r"^\|Profile$", "profile")
if scroll_to(r"^\|PR board$", tries=6):
    shot("pr-board")
else:
    FAILS.append("PR board missing on the profile")
if tap(r"^\|Rep PRs$", "rep-prs", required=False):
    if not scroll_to(r"PREDICTED|DONE", tries=3):
        FAILS.append("rep PR matrix missing")
    tap(r"^\|Est\. 1RM$")
scroll_to(r"Achievements")
shot("profile-achievements")
# The design's badges: tap one for what it takes
scroll_to(r"Century, (earned|\d+ percent)", tries=4, step=500)
if tap(r"Century, (earned|\d+ percent)", "badge-open", required=False):
    if not find(r"Log 100 training sessions", timeout=4):
        FAILS.append("badge detail missing")
    tap(r"^Close badge")
else:
    FAILS.append("no Century badge on the profile")
# The design's header: name and details in one panel
scroll_up_to(r"Edit your name and details", tries=14)
if tap(r"Edit your name and details", "me-sheet", required=False):
    if not find(r"See where I rank", timeout=4):
        FAILS.append("profile panel missing")
    tap(r"See where I rank", "standards")
    # No age entered in setup: the sheet says so instead of comparing against a made-up 31.
    if not find(r"^\|?Not set$", timeout=4) or not find(r"^\|?Add age$", timeout=2):
        FAILS.append("standards invent an age the lifter never gave")
    if not scroll_to(r"^\|Strength Club$", tries=6):
        FAILS.append("Strength Club missing from the standards")
    close_sheet(r"Strength Club")
else:
    FAILS.append("no name on the profile header")
# App.js ProfileCustomization: reorder and hide profile sections
if scroll_to(r"Customize profile", tries=12):
    tap(r"Customize profile", "profile-customize")
    if not find(r"sections shown", timeout=4):
        FAILS.append("profile customise sheet missing")
    close_sheet(r"sections shown")
else:
    FAILS.append("Customize profile button missing")
# B892: the share card shows what you pick, saved as you go
if seek(r"Share card") and tap(r"Share card", "share-card"):
    if not find(r"Share card preview", timeout=6):
        FAILS.append("share card has no preview")
    if scroll_to(r"\+ Achievements", tries=4, step=500):
        tap(r"\+ Achievements")
        if not find(r"ACHIEVEMENTS|of \d+ earned", timeout=4):
            FAILS.append("share card did not add Achievements")
        shot("share-card")
    close_sheet(r"SHOW ON CARD|Share image")
else:
    FAILS.append("Share card button missing")
# The design: History is one tap from Progress (Trends · History), with the month calendar filtering the list
tap(r"^\|Progress$|^Progress\|")
time.sleep(1.2)
if not scroll_to(r"Weekly sets per muscle", tries=4, step=600):
    FAILS.append("no weekly landmarks on Progress")
if scroll_to(r"^\|Weekly audit$", tries=4, step=600):
    shot("weekly-audit")
else:
    FAILS.append("no weekly audit on Progress")
scroll_up_to(r"^\|History$", tries=8)
if tap(r"^\|History$", "history-calendar"):
    if tap(r", \d+ workouts?(, today)?\|", "calendar-day", required=False):
        if not find(r"^\|Showing ", timeout=4):
            FAILS.append("calendar day did not filter the history")
    # The design's session sheet from the history list: sets by muscle, then Share this session as a card
    if scroll_to(r"^\|[A-Z][a-z]{2} \d+ · \d+ sets", tries=6, step=600) and tap(r"^\|[A-Z][a-z]{2} \d+ · \d+ sets", "history-sheet"):
        time.sleep(1.5)
        if not scroll_to(r"^\|WORKING SETS BY MUSCLE$", tries=4, step=500):
            FAILS.append("session sheet has no working sets by muscle")
        if scroll_to(r"Share this session", tries=4, step=500):
            tap(r"Share this session")
            if not scroll_to(r"^\|Copy text$", tries=3, step=400):
                FAILS.append("Share this session shows no card with Copy text")
            shot("history-share")
        else:
            FAILS.append("session sheet has no Share this session")
        back()
        time.sleep(1)
    else:
        FAILS.append("no session row in the history list")
    scroll_up_to(r"^\|Trends$", tries=10)
    tap(r"^\|Trends$")
else:
    FAILS.append("no History tab on Progress")
tap(r"^\|You$|^You\|")
time.sleep(1.2)
scroll_up_to(r"^\|Settings$", tries=14)
tap(r"^\|Settings$", "settings")
scroll_to(r"^\|lb$", tries=6, step=500)
if tap(r"^\|lb$", "unit-confirm"):
    tap(r"^\|Switch$", "unit-switched")
# 7a. Looks: pick a colour theme and dark mode, then put Ember and System back
if seek(r"^Lime theme"):
    tap(r"^Lime theme", "theme-lime")
    tap(r"^\|Dark$", "theme-dark")
    if not find(r"^\|Pure black$", timeout=4):
        FAILS.append("pure black row missing in dark mode")
    tap(r"^\|System$")
    scroll_to(r"^Ember theme", tries=3, step=400)
    tap(r"^Ember theme")
else:
    FAILS.append("no colour themes in settings")
if scroll_to(r"^\|Reminders$", tries=8):
    tap(r"^\|Reminders$", "reminders-sheet")
    if tap(r"^Training reminders\|", "reminders-on", required=False):
        close_sheet(r"^Training reminders\|")
    else:
        FAILS.append("reminders sheet missing its switch"); back()
else:
    FAILS.append("no Reminders row in settings")
if seek(r"^Manage gyms"):
    tap(r"^Manage gyms", "gyms-sheet")
    back()
# The design's Comfort & accessibility panel: text size and the haptic vocabulary
if seek(r"^\|Comfort & accessibility$"):
    tap(r"^\|Comfort & accessibility$", "comfort")
    if not find(r"HAPTIC PATTERNS", timeout=4):
        FAILS.append("comfort panel missing")
    tap(r"^\|Larger$", "text-larger")
    tap(r"^\|Default$")
    back()
else:
    FAILS.append("no Comfort & accessibility row")
if seek(r"^\|1RM calculator$"):
    shot("e1rm-calc")
else:
    FAILS.append("no e1RM calculator in settings")
# 7b. Settings parity: training preferences, data management, help
tap(r"^\|You$|^You\|")
for _ in range(14):
    if find(r"^\|Settings$", timeout=1):
        break
    adb("shell", "input", "swipe", "540", "700", "540", "1900", "150")
tap(r"^\|Settings$")
# Settings pages open as sheets from the settings rows
if scroll_to(r"^\|Training preferences$", tries=8):
    tap(r"^\|Training preferences$", "settings-training")
    if scroll_to(r"^\|RPE$", tries=4, step=400):
        tap(r"^\|RPE$", "settings-rpe")
        if not find(r"RPE 8", timeout=3):
            FAILS.append("effort preview does not follow the scale")
        tap(r"^\|Reps in reserve$")
    else:
        FAILS.append("effort scale missing from training preferences")
    back()
else:
    FAILS.append("no Training preferences row")
if scroll_to(r"^\|Data & storage$", tries=6):
    tap(r"^\|Data & storage$", "settings-manage-data")
    if not scroll_to(r"EXPORT · SPREADSHEET", tries=3, step=400):
        FAILS.append("Data & storage has no spreadsheet export")
    if tap(r"Review Banned exercises", required=False):
        shot("banned-sheet"); back()
    # Health Connect: the opt-in row, and Android's own permission screen when it's switched on (dismissed unanswered).
    if scroll_to(r"Send to Health Connect", tries=3, step=400):
        shot("health-connect-row")
        if tap(r"^Send to Health Connect\|", required=False):
            time.sleep(3); shot("health-connect-permission"); back(); time.sleep(1.5)
            if not find(r"Data & storage", timeout=4):
                back()
        # Reading is its own switch with its own permissions (sleep, resting HR, HRV, weight).
        if scroll_to(r"Read sleep, heart rate", tries=2, step=300):
            if tap(r"^Read sleep, heart rate and weight from Health Connect\|", required=False):
                time.sleep(3); shot("health-connect-read-permission"); back(); time.sleep(1.5)
                if not find(r"Data & storage", timeout=4):
                    back()
        else:
            FAILS.append("Data & storage has no Health Connect read option")
    else:
        FAILS.append("Data & storage has no Health Connect option")
    back()
else:
    FAILS.append("no Data & storage row")
# The design's Plates & bars: pairs you own per size; the heaviest load follows the counts
if seek(r"^\|Plates & bars$"):
    tap(r"^\|Plates & bars$", "settings-plates")
    def heaviest():
        for n in nodes():
            m = re.match(r"([\d.]+) (kg|lb) is the heaviest you can load", n.get("text") or "")
            if m: return float(m.group(1))
        return None
    if not find(r"PLATES · PAIRS YOU OWN", timeout=5) or not scroll_to(r"is the heaviest you can load", tries=3, step=400):
        FAILS.append("Plates & bars has no plate pairs or heaviest load")
    else:
        h0 = heaviest()
        scroll_up_to(r"One fewer pair of (25 kg|45 lb)", tries=3)
        tap(r"One fewer pair of (25 kg|45 lb)", "plates-fewer")
        scroll_to(r"is the heaviest you can load", tries=3, step=400)
        time.sleep(1)
        h1 = heaviest()
        if h0 is None or h1 is None or not h1 < h0:
            FAILS.append(f"heaviest load does not follow the plate pairs ({h0} -> {h1})")
        scroll_up_to(r"One more pair of (25 kg|45 lb)", tries=3)
        tap(r"One more pair of (25 kg|45 lb)")
        if not scroll_to(r"^\|SMALLEST JUMP$", tries=3, step=400):
            FAILS.append("Plates & bars has no smallest jump")
        shot("settings-plates-k")
    back()
else:
    FAILS.append("no Plates & bars row")
# The calculator's + and − land on totals the plates make: empty → the bar, then the next buildable total.
if seek(r"^\|Plates & bars$") and tap(r"^\|Plates & bars$") and tap(r"Open the plate calculator", "plates-calc", required=False):
    seq = []
    for _ in range(2):
        hv = find_desc(r"^Heavier: [\d.]+ ")
        tap(r"^Heavier: ", required=False); time.sleep(1.2)
        seq.append((hv, num(value_of(r"^Total weight, "))))
    lt = find_desc(r"^Lighter: [\d.]+ ")
    tap(r"^Lighter: ", required=False); time.sleep(1.2)
    back_t = num(value_of(r"^Total weight, "))
    shot("plates-calc-stepped")
    if any(a is None or a != b for a, b in seq) or not seq[1][1] > seq[0][1] or back_t != seq[0][1] or lt != seq[0][1]:
        FAILS.append(f"plate calculator steps: {seq}, − to {lt} gave {back_t}")
    back(); time.sleep(1)
else:
    FAILS.append("Plates & bars has no Open the plate calculator")
if seek(r"^\|Rest timer & cues$"):
    tap(r"^\|Rest timer & cues$", "settings-cues")
    if not find(r"^\|Play the cue$", timeout=4) or not find(r"^\|Preview lock screen$", timeout=2):
        FAILS.append("Rest timer & cues lacks Play the cue / Preview lock screen")
    else:
        tap(r"^\|Preview lock screen$", "cues-lock-preview")
        dismiss_system_dialogs()
        time.sleep(1)
        out = adb("shell", "dumpsys", "notification", "--noredact")
        if "Sample timer" not in (out or ""):
            FAILS.append("Preview lock screen posted no sample rest notification")
    # The sample notification can take the first Back (heads-up); close until the sheet is gone.
    close_sheet(r"Preview lock screen")
else:
    FAILS.append("no Rest timer & cues row")
if scroll_to(r"^\|Help & about$", tries=6):
    tap(r"^\|Help & about$", "settings-help")
    if scroll_to(r"References & methodology", tries=4, step=500):
        tap(r"References & methodology", "references")
        if not scroll_to(r"Israetel|Nuckols|Schoenfeld", tries=3, step=400):
            FAILS.append("references do not open inline in Help & about")
    else:
        FAILS.append("references missing from Help & about")
    # Check every set: the engine's set audit over the lifter's own programs and history, plus three simulations.
    if seek(r"^\|?Check every set") and tap(r"^\|?Check every set", required=False):
        t0 = time.time()
        while not find(r"Every set checks out|problems? found", timeout=2) and time.time() - t0 < 90:
            time.sleep(2)
        shot("self-test")
        if find(r"problems? found", timeout=1):
            FAILS.append("Check every set reports problems on the smoke store")
        elif not find(r"Every set checks out", timeout=1):
            FAILS.append("Check every set never finished")
        close_sheet(r"prescribed lifts across|Checking every prescribed set")
    else:
        FAILS.append("no Check every set row in Help & about")
    back()
else:
    FAILS.append("no Help & about row")
tap(r"^\|Today$|^Today\|", "today-lb")
# The M3 Expressive split button on a training day: its menu holds the other ways into today
if tap(r"^More ways to start", "start-split-menu", required=False):
    if not find(r"Start now, skip the preview", timeout=3):
        FAILS.append("start split button menu missing its options")
    back()

# 8. Training cycle: build one, open it, try to advance
tap(r"^\|Plan$|^Plan\|")
scroll_up_to(r"^\|Edit program$|Review block|^\|New$", tries=10)
row_scroll_to(r"^\|New( program)?$", r"Review block|^\|Settings$|^\|Details$|^\|Schedule$", tries=6)
# B894: an unfinished cycle setup survives closing the wizard; Discard starts fresh
if tap(r"^\|New( program)?$", "draft-setup", required=False):
    tap(r"^\|Training cycle$|Training cycle\|")
    tap(r"^\|Continue$")
    time.sleep(1)
    back(); back()
    row_scroll_to(r"^\|New( program)?$", r"Review block|^\|Settings$|^\|Details$|^\|Schedule$", tries=6)
    tap(r"^\|New( program)?$")
    if not find(r"Picking up your unfinished cycle setup", timeout=6):
        FAILS.append("unfinished cycle setup not recovered")
    else:
        shot("setup-draft")
        tap(r"^\|Discard$")
    if find(r"^\|Continue$", timeout=4):
        back()
    scroll_up_to(r"^\|Edit program$|Review block|^\|New$", tries=10)
    row_scroll_to(r"^\|New( program)?$", r"Review block|^\|Settings$|^\|Details$|^\|Schedule$", tries=6)
# Build your own (App.js buildOwnRoutine): you pick the lifts in the editor; backing out of an empty one leaves nothing
if tap(r"^\|New( program)?$", "own-setup", required=False):
    if tap(r"Build your own", "own-dialog", required=False) and tap(r"^\|Start building$", required=False):
        if not find(r"Your lifts, our coaching", timeout=8):
            FAILS.append("build your own does not open the editor with its guide")
        else:
            shot("own-editor")
            if not scroll_to(r"Add an exercise", tries=4, step=500):
                FAILS.append("build your own editor has no Add an exercise")
            tap(r"^Close editor")
            time.sleep(1.5)
    else:
        FAILS.append("setup has no Build your own")
    if find(r"^\|Continue$", timeout=2):
        back()
    scroll_up_to(r"^\|Edit program$|Review block|^\|New$", tries=10)
    row_scroll_to(r"^\|New( program)?$", r"Review block|^\|Settings$|^\|Details$|^\|Schedule$", tries=6)
# No split fits: Strength in 20 minutes builds nothing; the wizard says so and offers the setup that does (it used to spin).
row_scroll_to(r"^\|New( program)?$", r"Review block|^\|Settings$|^\|Details$|^\|Schedule$", tries=6)
if tap(r"^\|New( program)?$", required=False):
    tap(r"^\|Single program$|Single program\|", required=False)
    tap(r"^\|Continue$", required=False)
    tap(r"Get strong, lower reps|^\|Strength$", required=False)
    tap(r"^\|Next$", required=False)
    tap(r"^\|Next$", required=False)
    if tap(r"≤20 min", required=False) and tap(r"Skip to your ranked splits", required=False):
        t0 = time.time()
        while not find(r"Nothing fits|BEST FIT OF", timeout=2) and time.time() - t0 < 120:
            time.sleep(2)
        shot("no-split-fits")
        if not find(r"Nothing fits", timeout=1):
            FAILS.append("Strength at ≤20 min did not say that nothing fits")
        else:
            t0 = time.time()
            while not find(r"Use .+ min sessions", timeout=2) and time.time() - t0 < 120:
                time.sleep(2)
            if not tap(r"Use .+ min sessions", "no-split-alternative", required=False):
                FAILS.append("no-split state offers no setup that builds")
            elif not find(r"BEST FIT OF \d+", timeout=120):
                FAILS.append("the offered session length did not rank a split")
            else:
                shot("no-split-ranked")
    else:
        FAILS.append("could not reach the ranked splits at ≤20 min")
    for _ in range(8):
        if find(r"^\|Edit program$|Review block", timeout=1):
            break
        if find(r"^\|Cancel$", timeout=1):
            tap(r"^\|Cancel$", required=False)
        else:
            back()
    row_scroll_to(r"^\|New( program)?$", r"Review block|^\|Settings$|^\|Details$|^\|Schedule$", tries=6)
if tap(r"^\|New( program)?$", "cycle-setup"):
    wizard("Smoke Cycle", cycle=True)
    time.sleep(8)
    shot("cycle-built")
    adb("shell", "input", "swipe", "540", "700", "540", "1900", "200")
    if tap(r"Open cycle", "cycle-sheet"):
        if not find(r"NOW · WEEK", timeout=8):
            FAILS.append("cycle page has no current block")
        # Web parity: the cycle's icon changes from its page, and later blocks can be rebuilt as adaptive for this gym
        if not find(r"^Change cycle icon", timeout=3):
            FAILS.append("cycle page has no Change cycle icon")
        if not seek(r"(Refresh|Upgrade) future blocks"):
            FAILS.append("cycle page offers no Refresh/Upgrade future blocks")
        else:
            shot("cycle-refresh")
        scroll_up_to(r"NOW · WEEK", tries=8)
        # The design's cycle editor: add a block after the current one and save; the later blocks are rebuilt
        if tap(r"^\|?Edit$", "cycle-edit", required=False):
            if scroll_to(r"Add a block", tries=6, step=600):
                tap(r"Add a block", "cycle-added")
                tap(r"Save cycle")
                if not find(r"^\|?Edit$", timeout=90) or not seek(r"BLOCK 4$"):
                    FAILS.append("cycle edit did not save")
                shot("cycle-saved")
            else:
                FAILS.append("cycle editor has no Add a block")
        else:
            FAILS.append("cycle page has no Edit")
        if scroll_to(r"End this block early", tries=8, step=600):
            tap(r"End this block early", "cycle-end-confirm")
            if tap(r"^\|?Review block$", "cycle-review", required=False):
                # Early in a block the review's main action is Keep training; moving on is behind a confirmation.
                if find(r"\|Move on early$|\|End cycle early$", timeout=6):
                    tap(r"\|Move on early$|\|End cycle early$", "cycle-move-early")
                    if not find(r"planned sessions in this block", timeout=4):
                        FAILS.append("moving on early has no confirmation")
                if find(r"\|Start block \d+$|\|Complete cycle$", timeout=10):
                    tap(r"\|Start block \d+$|\|Complete cycle$")
                    time.sleep(6)
                    shot("cycle-advanced")
                else:
                    shot("cycle-review-too-early")
                    back()
        else:
            FAILS.append("cycle page has no End this block early")
        back()
tap(r"^\|Today$|^Today\|", "today-cycle")

# 9. Program editor: move a lift, save this block only
tap(r"^\|Plan$|^Plan\|")
adb("shell", "input", "swipe", "540", "700", "540", "1900", "200")
row_scroll_to(r"^\|Schedule$", r"Review block")
if tap(r"^\|  Schedule$|Schedule$", "schedule-sheet", required=False):
    tap(r"^Mon", "schedule-monday")
    # Monday now repeats a workout and skips another, so the engine's week is offered.
    scroll_up_to(r"^\|Suggested week$", tries=3)
    if not find(r"^\|Suggested week$", timeout=4):
        FAILS.append("a week that skips a workout offers no suggested week")
    # With nothing scheduled the engine's week is offered; two "Lower" days say which lift each is.
    scroll_to(r"^\|Clear schedule$", tries=3)
    tap(r"^\|Clear schedule$")
    scroll_up_to(r"^\|Suggested week$", tries=3)
    if find(r"^\|Suggested week$", timeout=6):
        shot("schedule-suggested")
        if not find(r"^\|back squat$", timeout=3) or not find(r"^\|deadlift$", timeout=3):
            FAILS.append("suggested week does not tell the two Lower days apart")
        tap(r"^\|Use this$")
        if find(r"^\|Suggested week$", timeout=3):
            FAILS.append("suggested week still offered after Use this")
    else:
        FAILS.append("cleared schedule offers no suggested week")
    scroll_to(r"^\|Done$", tries=3)
    tap(r"^\|Done$")
if tap(r"Edit program", "editor"):
    if not find(r"^Edit program|^\|Edit program$", timeout=8):
        FAILS.append("editor did not open")
    # Every week of the block: picking another week shows its targets
    if tap(r"^Week 1: \d+ sets", "editor-week-1", required=False):
        if not find(r"Week 1’s targets are shown|This week’s targets are shown", timeout=6):
            FAILS.append("editor week chips do not switch the week")
    if not find(r"lifts · [\d.]+ sets", timeout=4):
        FAILS.append("editor shows no day summaries")
    tap(r"^Move .+ later\|", "editor-moved")
    scroll_to(r"^Swap .+\|", tries=4, step=600)
    if tap(r"^Swap .+\|", "editor-swap"):
        if not find(r"No other lift|·", timeout=8):
            FAILS.append("editor swap shows no alternatives")
        tap(r"^Swap .+\|")
    if tap(r"^More options for ", "editor-more", required=False):
        if not (find(r"Why this lift\?", timeout=4) or scroll_to(r"Why this lift\?", tries=3, step=500)):
            FAILS.append("editor more menu missing Why this lift")
        tap(r"^More options for ")
    # Superset rounds: pair a lift with the next, then give the group a set number of rounds
    if tap(r"^More options for ", required=False):
        if seek(r"Superset with the next lift"):
            tap(r"Superset with the next lift")
            if seek(r"^\|?(SUPERSET|CIRCUIT)"):
                tap(r"^More rounds", required=False)
                if find(r"^\d+ rounds", timeout=4):
                    shot("editor-rounds")
                else:
                    FAILS.append("superset group took no rounds")
            else:
                FAILS.append("no superset header after pairing")
        else:
            back()
    # Drag to reorder: the cards fold to one line, the first lift is dragged two places down by its handle
    if seek(r"Reorder$") and tap(r"Reorder$", "editor-reorder"):
        first = find(r"^Drag to reorder ", timeout=4)
        names = [n.get("content-desc") for n in nodes() if (n.get("content-desc") or "").startswith("Drag to reorder ")]
        if first and len(names) >= 3:
            x, y = first
            adb("shell", "input", "swipe", str(x), str(y), str(x), str(y + int(2.2 * 68 * 2.625)), "1400")
            time.sleep(2.0)
            shot("editor-reordered")
            after = [n.get("content-desc") for n in nodes() if (n.get("content-desc") or "").startswith("Drag to reorder ")]
            if after == names:
                FAILS.append("dragging a lift did not reorder the day")
        else:
            FAILS.append("reorder mode shows no drag handles")
        tap(r"Done$", required=False)
    else:
        FAILS.append("editor has no Reorder")
    # Day setup sits under the lifts, folded: it opens to the day focus
    if seek(r"(^|\|)Day setup"):
        tap(r"(^|\|)Day setup", "editor-day-setup")
        # It opens below the fold at the foot of the list: look down, not up.
        if not (find(r"DAY FOCUS", timeout=2) or scroll_to(r"DAY FOCUS", tries=4, step=500)):
            FAILS.append("day setup does not open to the day focus")
        tap(r"(^|\|)Day setup", required=False) if find(r"(^|\|)Day setup", timeout=1) else None
    else:
        FAILS.append("editor has no day setup")
    if seek(r"Add an exercise") and tap(r"Add an exercise"):
        if tap(r"Create a custom exercise", "custom-create"):
            tap(r"Exercise name")
            # A name not in the library (a library name is refused, and the sheet says so)
            adb("shell", "input", "text", "Kneeling%sCable%sChop")
            back()
            if scroll_to(r"^\|Save and add$"):
                tap(r"^\|Save and add$")
                time.sleep(1.5)
                shot("custom-added")
            else:
                FAILS.append("New exercise has no Save and add")
                back()
    if tap(r"^\|Save$", "editor-scope"):
        tap(r"^\|This block$", "editor-saved", required=False)  # asked only for a block in a cycle
    if seek(r"^\|Settings$") and tap(r"^\|Settings$", "editor-program-settings", required=False):
        back()
    back()

# Injury log from outside a workout (the design keeps it in Settings)
tap(r"^\|You$|^You\|")
tap(r"^\|Body$")
if scroll_to(r"^\|Progress photos$", tries=5):
    shot("progress-photos")
else:
    FAILS.append("progress photos missing on Body")
# The design's Body: a stepper and Log today for bodyweight, stepper rows for measurements
if scroll_up_to(r"^\|Log today$|^\|Logged today$", tries=8):
    if tap(r"^\|Log today$", "bw-log", required=False) and not find(r"^\|Logged today$", timeout=4):
        FAILS.append("bodyweight Log today did not log")
if not scroll_to(r"Waist trend|^\|Waist$", tries=4):
    FAILS.append("measurement rows missing on Body")
scroll_up_to(r"^\|Settings$", tries=14)
tap(r"^\|Settings$")
if scroll_to(r"^\|Injury log$", tries=10):
    tap(r"^\|Injury log$", "injury-log")
    if find(r"^\|Log an injury$", timeout=2):
        tap(r"^\|Log an injury$")
    tap(r"^\|Lower body$")
    tap(r"^\|Knee$")
    if scroll_to(r"Save injury"):
        tap(r"Save injury")
        time.sleep(1.5)
        shot("injury-saved")
    back()
    # The design's protection card on Today: Feels fine now resolves it
    tap(r"^\|Today$|^Today\|")
    if scroll_up_to(r"Knee protection", tries=6):
        shot("today-protection")
        tap(r"^\|Feels fine now$")
        time.sleep(1.5)
        if find(r"Knee protection", timeout=2):
            FAILS.append("Feels fine now did not resolve the injury")
    else:
        FAILS.append("no protection card on Today for an open injury")

# Template with percentages: build, then set training maxes
tap(r"^\|Plan$|^Plan\|")
scroll_up_to(r"^\|Edit program$|Review block|^\|New$", tries=10)
row_scroll_to(r"^\|New( program)?$", r"Review block|^\|Settings$|^\|Details$|^\|Schedule$", tries=6)
if tap(r"^\|New( program)?$"):
    if tap(r"^\|Start from a template", "templates"):
        tap(r"Search 75 templates|Search \d+ templates")
        adb("shell", "input", "text", "Main-Lift")
        hide_keyboard()
        time.sleep(1)
        if not find(r"Main-Lift Waves", timeout=3):  # the emulator keyboard sometimes adds a stray key: clear and retype
            tap(r"Search 75 templates|Search \d+ templates")
            adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
            for _ in range(20): adb("shell", "input", "keyevent", "KEYCODE_DEL")
            adb("shell", "input", "text", "Waves")
            hide_keyboard()
            time.sleep(1)
        if tap(r"% of max", "template-picked"):
            time.sleep(6)
            shot("training-max-sheet")
            if not find(r"^\|Percentage program$", timeout=4):
                FAILS.append("percentage program panel missing")
            tap(r"From a 1RM")
            if tap(r"1RM \((kg|lb)\)"):
                adb("shell", "input", "text", "140")
                hide_keyboard()
                tap(r"^\|Set$")
                time.sleep(1.5)
                shot("training-max-set")
            # Method: switch an existing program to another percentage method
            scroll_up_to(r"Change method", tries=4)
            if tap(r"Change method", "methods-open"):
                if tap(r"Texas Method", "method-confirm", required=False):
                    if tap(r"^\|Switch$", "method-switched"):
                        # The panel names the method as its template does, and its max as the method's own basis.
                        if not find(r"Volume / Recovery / Intensity", timeout=6):
                            FAILS.append("method switch did not show the new method")
                        if not find(r"5-rep max", timeout=3):
                            FAILS.append("Texas method still asks for a training max instead of its 5-rep max")
                else:
                    FAILS.append("percentage methods not listed")
            back(); back()
        else:
            back(); back()

# Compare
tap(r"^\|Plan$|^Plan\|")
scroll_up_to(r"^Program: ", tries=10)
if tap(r"^Program: ") and tap(r"^\|Compare$", "compare"):
    back()

# 9b. Tour: every tab top to bottom, for side-by-side review against the design canvas
def tour(tab_pattern, name, pages=6):
    if not tap(tab_pattern, required=False):
        return
    time.sleep(1.2)
    for _ in range(4):
        adb("shell", "input", "swipe", "540", "700", "540", "2000", "150")
    time.sleep(0.8)
    for k in range(pages):
        shot(f"tour-{name}-{k}")
        adb("shell", "input", "swipe", "540", "1800", "540", "800", "500")
        time.sleep(0.9)
for pat, nm in [(r"^\|Today$|^Today\|", "today"), (r"^\|Plan$|^Plan\|", "plan"), (r"^\|Progress$|^Progress\|", "progress"),
                (r"^\|Exercises$|^Exercises\|", "exercises"), (r"^\|You$|^You\|", "you")]:
    tour(pat, nm)
if tap(r"^\|Settings$", required=False):
    time.sleep(1)
    for k in range(4):
        shot(f"tour-settings-{k}")
        adb("shell", "input", "swipe", "540", "1800", "540", "800", "500")
        time.sleep(0.9)
tap(r"^\|Today$|^Today\|", required=False)
# 9c. The same tabs in dark mode (the app follows the system), a few pages each, for contrast review
adb("shell", "cmd", "uimode", "night", "yes")
time.sleep(2.5)
for pat, nm in [(r"^\|Today$|^Today\|", "today"), (r"^\|Plan$|^Plan\|", "plan"), (r"^\|Progress$|^Progress\|", "progress"),
                (r"^\|Exercises$|^Exercises\|", "exercises"), (r"^\|You$|^You\|", "you")]:
    tour(pat, "dark-" + nm, pages=3)
adb("shell", "cmd", "uimode", "night", "no")
time.sleep(2.5)
tap(r"^\|Today$|^Today\|", required=False)

# 9d. Launcher shortcuts (long-press the icon): Start workout and Quick workout are published
sc = adb("shell", "dumpsys", "shortcut", timeout=60) or ""
if "Quick workout" not in sc or "Start workout" not in sc:
    FAILS.append("launcher shortcuts not published")

# 10. Home-screen widget: rendered by a real AppWidgetHost, then its Start button's intent
adb("shell", "appwidget", "grantbind", "--package", PKG)
adb("shell", "am", "start", "-W", "-n", f"{PKG}/com.pursuitiron.app.WidgetPreviewActivity")
time.sleep(5)
shot("widget")
adb("shell", "am", "start", "-W", "-a", "com.pursuitiron.app.action.START_WORKOUT", "-n", f"{PKG}/com.pursuitiron.app.MainActivity")
time.sleep(4)
shot("widget-start")
tap(r"^\|Skip$", required=False)  # the readiness check-in opens over a new workout
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
if tap(r"Start workout|Start it early|Resume workout|^\|?Train .+ now", required=False):  # a workout in progress reopens on its own
    start_from_preview()
time.sleep(3)
tap(r"^\|Skip$", required=False)  # readiness check
shot("tablet-workout")
if find(r"Minimise workout", timeout=4) is None:
    FAILS.append("tablet did not show the workout")
# The tablet tabs beside the design's: minimise the workout, then each tab on the rail
tap(r"Minimise workout", required=False)
time.sleep(1.5)
for pat, nm in [(r"^\|Plan$|^Plan\|", "tablet-plan"), (r"^\|Progress$|^Progress\|", "tablet-progress"), (r"^\|Exercises$|^Exercises\|", "tablet-exercises"), (r"^\|You$|^You\|", "tablet-you")]:
    if tap(pat, required=False):
        time.sleep(2)
        shot(nm)
adb("shell", "wm", "size", "reset")
adb("shell", "wm", "density", "reset")

# 12. Large text and targets: every tab at 130% font scale. Tappable controls under 44dp, and tappable controls
# TalkBack can't name, are written to a11y.txt (with screenshots) for review; they don't fail the run yet.
def a11y_pass():
    m = re.search(r"(\d+)\s*$", adb("shell", "wm", "density").strip())
    dpx = int(m.group(1)) / 160 if m else 2.625
    sz = re.search(r"(\d+)x(\d+)\s*$", adb("shell", "wm", "size").strip())
    screen_h = int(sz.group(2)) if sz else 2400
    adb("shell", "settings", "put", "system", "font_scale", "1.3")
    time.sleep(1)
    launch()
    time.sleep(3)
    tap(r"^\|Skip$", required=False)            # a workout in progress reopens with its check-in
    tap(r"Minimise workout", required=False)    # the tabs sit under an open workout
    time.sleep(1.5)
    issues, scanned = [], 0
    sw = int(sz.group(1)) if sz else 1080
    def label_of(n):
        own = (n.get("content-desc") or "") + (n.get("text") or "")
        if own.strip():
            return own.strip()
        for c in n.iter("node"):
            t = (c.get("content-desc") or "") + (c.get("text") or "")
            if t.strip():
                return t.strip()
        return ""
    for pat, nm in [(r"^\|Today$|^Today\|", "today"), (r"^\|Plan$|^Plan\|", "plan"), (r"^\|Progress$|^Progress\|", "progress"),
                    (r"^\|Exercises$|^Exercises\|", "exercises"), (r"^\|You$|^You\|", "you")]:
        if not tap(pat, required=False):
            continue
        scanned += 1
        time.sleep(1.2)
        # A control is measured at the size it shows; one half under a bar or past a scroll edge looks small, so a
        # control only counts as small if it is small every time it appears across the pages.
        seen = {}
        for k in range(3):
            shot(f"a11y-{nm}-{k}")
            ns = list(nodes())
            # The workout bar and the tab bar cover the list's bottom: rows reaching them are cut off, not small.
            floor = min([int(re.findall(r"\d+", m.get("bounds", "0,0,0,0"))[1]) for m in ns
                         if (re.match(r"^(WORKOUT IN PROGRESS|Today|Plan|Progress|Exercises|You)$", m.get("text") or "")
                             or (m.get("content-desc") or "").startswith("Resume workout, "))
                         and len(re.findall(r"\d+", m.get("bounds", ""))) == 4 and int(re.findall(r"\d+", m.get("bounds"))[1]) > screen_h // 2] or [screen_h])
            viewports = [tuple(map(int, re.findall(r"\d+", m.get("bounds", "")))) for m in ns
                         if m.get("scrollable") == "true" and len(re.findall(r"\d+", m.get("bounds", ""))) == 4]
            for n in ns:
                if n.get("clickable") != "true" or n.get("package") != PKG:
                    continue
                b = re.findall(r"\d+", n.get("bounds", ""))
                if len(b) != 4:
                    continue
                x1, y1, x2, y2 = map(int, b)
                w, h = (x2 - x1) / dpx, (y2 - y1) / dpx
                lab = label_of(n)
                # Touching an edge of the screen or the bars: not the control's real size or label.
                # ...or at the edge of the list it scrolls in (the list's own bounds are its viewport).
                if any(abs(y1 - v[1]) <= 3 or abs(y2 - v[3]) <= 3 for v in viewports):
                    continue
                if y1 <= 40 * dpx or y2 >= min(screen_h - 100 * dpx, floor - 8 * dpx) or x1 <= 0 or x2 >= sw - 1:
                    continue
                if not lab:
                    issues.append(f"{nm}: unlabelled control {int(w)}x{int(h)}dp at {x1},{y1}")
                else:
                    pw, ph = seen.get(lab, (0, 0))
                    seen[lab] = (max(pw, w), max(ph, h))
            adb("shell", "input", "swipe", "540", "1800", "540", "900", "500")
            time.sleep(0.9)
        issues.extend(f"{nm}: small target '{lab[:40]}' {int(w)}x{int(h)}dp" for lab, (w, h) in seen.items() if w < 44 or h < 44)
    adb("shell", "settings", "put", "system", "font_scale", "1.0")
    uniq = list(dict.fromkeys(issues))
    with open(os.path.join(OUT, "a11y.txt"), "w") as f:
        f.write("\n".join(uniq) if uniq else ("no small or unlabelled targets on %d tabs" % scanned if scanned else "no tabs reached; nothing checked"))
    if not scanned:
        FAILS.append("large-text pass reached no tabs")
    print("a11y issues:", len(uniq), flush=True)
# Performance probe: cold start and frame pacing while scrolling each tab. The emulator renders in software, so these
# are a trend line between runs (and a catch for something an order of magnitude off), not real-phone numbers.
def perf_pass():
    out = []
    starts = []
    for _ in range(3):
        adb("shell", "am", "force-stop", PKG); time.sleep(1.5)
        r = adb("shell", "am", "start", "-W", "-n", f"{PKG}/com.pursuitiron.app.MainActivity", timeout=90)
        m = re.search(r"TotalTime: (\d+)", r)
        if m: starts.append(int(m.group(1)))
        time.sleep(4)
    out.append("cold start ms: " + ", ".join(map(str, starts)) + (f" (median {sorted(starts)[len(starts)//2]})" if starts else ""))
    for t in ["Today", "Plan", "Progress", "Exercises", "You"]:
        tap(rf"^\|{t}$|^{t}\|", required=False); time.sleep(1.5)
        adb("shell", "dumpsys", "gfxinfo", PKG, "reset")
        for _ in range(3):
            adb("shell", "input", "swipe", "540", "1900", "540", "500", "180"); time.sleep(0.9)
        for _ in range(3):
            adb("shell", "input", "swipe", "540", "500", "540", "1900", "180"); time.sleep(0.9)
        g = adb("shell", "dumpsys", "gfxinfo", PKG, timeout=60)
        pick = lambda k: (re.search(k + r"[^\n]*", g) or [""])[0]
        out.append(f"{t}: " + " | ".join(x for x in [pick(r"Total frames rendered: \d+"), pick(r"Janky frames: \d+ \([\d.]+%\)"),
                   pick(r"50th percentile: \d+ms"), pick(r"90th percentile: \d+ms"), pick(r"99th percentile: \d+ms")] if x))
    with open(os.path.join(OUT, "perf.txt"), "w") as f:
        f.write("\n".join(out) + "\n")
    print("\n".join(out), flush=True)
try:
    perf_pass()
except Exception as e:  # a review aid; never lose the rest of the run to it
    print("perf pass failed", e, flush=True)
try:
    a11y_pass()
except Exception as e:  # a review aid; never lose the rest of the run to it
    print("a11y pass failed", e, flush=True)
    adb("shell", "settings", "put", "system", "font_scale", "1.0")

# Any ANR during the run, whoever it belonged to, with its reason, so ours can never hide behind the launcher's.
full = adb("logcat", "-d", "-b", "main", "-b", "system", "-b", "crash", timeout=120)
anr = []
lines = full.splitlines()
for i, l in enumerate(lines):
    if "ANR in" in l:
        anr.extend(lines[i:i + 12]); anr.append("----")
with open(os.path.join(OUT, "anr.txt"), "w") as f:
    f.write("\n".join(anr))
if any(("ANR in " + PKG) in l for l in anr):
    FAILS.append("ANR in the app")

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
