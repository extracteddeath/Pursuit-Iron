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


def seek(pattern, tries=8):
    """Find something on a long page from wherever it is scrolled: up first, then down."""
    return scroll_up_to(pattern, tries=tries) or scroll_to(pattern, tries=tries + 4)


def wizard(name, cycle=False, shots=False):
    """The design's Create wizard: pick the format, answer the required name, then Next until it can build."""
    if cycle:
        tap(r"^\|Training cycle$|Training cycle\|", "wizard-cycle")
    tap(r"^\|Continue$", "wizard-mode" if shots else None)
    for k in range(22):
        if find(r"Program name|Cycle name", timeout=1):
            tap(r"Program name|Cycle name")
            adb("shell", "input", "text", name.replace(" ", "%s"))
            hide_keyboard()
            time.sleep(0.6)
        if shots and k < 4:
            shot(f"wizard-step-{k}")
        if find(r"with these settings", timeout=1):
            tap(r"with these settings", "wizard-early" if shots else None)
            return True
        if find(r"^\|(Generate Program|Build Cycle)$", timeout=1):
            tap(r"^\|(Generate Program|Build Cycle)$")
            return True
        if not tap(r"^\|Next$", required=False):
            FAILS.append("wizard stuck")
            shot("wizard-stuck")
            return False
        time.sleep(0.5)
    FAILS.append("wizard never finished")
    return False


def row_scroll_to(pattern, anchor, tries=4):
    """A horizontally scrolling row (the Plan action row): swipe it left at the anchor's height until pattern shows."""
    for _ in range(tries):
        if find(pattern, timeout=1):
            return True
        a = find(anchor, timeout=2)
        if not a:
            return False
        adb("shell", "input", "swipe", "900", str(a[1]), "250", str(a[1]), "300")
        time.sleep(0.6)
    return bool(find(pattern, timeout=1))


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
if find(r"How are you today\?", timeout=3):
    tap(r"^Sleep: Great", "check-in")
    tap(r"^Energy: High")
    tap(r"^\|Save$|^\|Done$")
    if not find(r"^Edit today's check-in", timeout=4):
        FAILS.append("check-in did not fold into its chip")

# 2. Workout: enter a weight, log, rate, rest
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
if tap(r"Weight not set, edit", "set-editor"):
    tap(r"^Weight \((kg|lb)\), tap to type")  # the design's big value, typed in place
    adb("shell", "input", "text", "60")
    time.sleep(0.6)
    shot("set-editor-typed")
    hide_keyboard()
    time.sleep(0.6)
    if tap(r"Open the plate calculator$", "set-plates", required=False):  # only barbell lifts load plates
        back()
    tap(r"^\|Save$", "set-editor-save")
tap(r"^Set 1:", "set-logged")
tap(r"^2 reps left", "set-rated")
# The design's Why opens in place on the card
if tap(r"Why .* kg\?|Why .* reps\?|No suggestion yet", "why-inline"):
    tap(r"^Close explanation", required=False)
# The design's header: focus mode, the exercise list, and "Short on time?" from the clock
if tap(r"^Focus mode", "focus-mode"):
    if not find(r"Log (set|warm-up): press and hold|Reps left in the tank", timeout=4):
        FAILS.append("focus mode shows no set to log")
    tap(r"^Focus mode")
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
    back()
tap(r"Exercise actions", "exercise-menu")
# B903: change today's working-set count; the finish page asks whether the program keeps it
if (find(r"One more working set", timeout=3) or scroll_to(r"One more working set", tries=3, step=400)):
    tap(r"One more working set", "set-count")
else:
    FAILS.append("exercise menu has no working-set count")
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
if tap(r"Pin a note for ", "pin-note", required=False):
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
    if not find(r"Here's the plan", timeout=4) or not find(r"Mark it resolved", timeout=2):
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
    shot("quick-empty")
    tap(r"Add a lift", "add-lift")
    tap(r"^\|Search exercises$")
    adb("shell", "input", "text", "curl")
    hide_keyboard()
    time.sleep(1.5)
    tap(r"^\|[A-Za-z -]*Curl[A-Za-z -]*$", "quick-added")
    if tap(r"Weight not set, edit"):
        tap(r"^Weight \((kg|lb)\), tap to type")
        adb("shell", "input", "text", "12")
        hide_keyboard()
        time.sleep(0.6)
        tap(r"^\|Save$")
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
if tap(r"^\|Barbell$", "library-barbell"):
    if not find(r"^\|\d+\+? exercises?$", timeout=4):
        FAILS.append("exercise count missing")
    tap(r"^\|Any equipment$")
else:
    FAILS.append("no equipment filter on Exercises")

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
# The design's Programs (the program pill under Plan): every program, then shared programs (added without switching)
scroll_up_to(r"^Program: ", tries=10)
if tap(r"^Program: ", "programs-sheet") and scroll_to(r"Browse shared programs", tries=4, step=500):
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
# Lift progression lives in Program settings (the Plan action row): the block's methods, each lift's reason
scroll_up_to(r"^\|Edit program$|Review block", tries=10)
# The design's Block review page: sessions against the plan (or why it's too early), with Start next block
if tap(r"Review block", "block-review", required=False):
    if not find(r"\|Block \d+ review$", timeout=10):
        FAILS.append("block review page did not open")
    elif not find(r"sessions done|not enough|No sessions were logged", timeout=10):
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
if scroll_to(r"^\|Recent$", tries=6):
    adb("shell", "input", "swipe", "540", "1500", "540", "1000", "300")
    time.sleep(0.8)
    if tap(r"^\|[A-Z][a-z]{2} \d+ · ", "history-sheet", required=False):
        scroll_to(r"^\|Correct log$", tries=3)
        if tap(r"^\|Correct log$", "history-correct"):
            back()
        else:
            back()

# 6b. Progress: a lift's chart, strength standards
tap(r"^\|Progress$|^Progress\|", "progress-after-import")
if not find(r"vs last week|^\|Volume$", timeout=3):
    FAILS.append("weekly volume chart missing on Progress")
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
    tap(r"^\|e1RM$")
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
if not scroll_to(r"Weekly sets vs landmarks", tries=4, step=600):
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
if seek(r"^\|e1RM calculator$"):
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
    back()
else:
    FAILS.append("no Data & storage row")
# The design's Plates & bars: pairs you own per size; the heaviest load follows the counts
if scroll_to(r"^\|Plates & bars$", tries=6):
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
if scroll_to(r"^\|Rest timer & cues$", tries=6):
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
    back()
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
    back()
else:
    FAILS.append("no Help & about row")
tap(r"^\|Today$|^Today\|", "today-lb")

# 8. Training cycle: build one, open it, try to advance
tap(r"^\|Plan$|^Plan\|")
scroll_up_to(r"^\|Edit program$|Review block|^\|New$", tries=10)
row_scroll_to(r"^\|New$", r"Review block|^\|Settings$|^\|Details$|^\|Schedule$", tries=6)
# B894: an unfinished cycle setup survives closing the wizard; Discard starts fresh
if tap(r"^\|New$", "draft-setup", required=False):
    tap(r"^\|Training cycle$|Training cycle\|")
    tap(r"^\|Continue$")
    time.sleep(1)
    back(); back()
    row_scroll_to(r"^\|New$", r"Review block|^\|Settings$|^\|Details$|^\|Schedule$", tries=6)
    tap(r"^\|New$")
    if not find(r"Picking up your unfinished cycle setup", timeout=6):
        FAILS.append("unfinished cycle setup not recovered")
    else:
        shot("setup-draft")
        tap(r"^\|Discard$")
    if find(r"^\|Continue$", timeout=4):
        back()
    scroll_up_to(r"^\|Edit program$|Review block|^\|New$", tries=10)
    row_scroll_to(r"^\|New$", r"Review block|^\|Settings$|^\|Details$|^\|Schedule$", tries=6)
if tap(r"^\|New$", "cycle-setup"):
    wizard("Smoke Cycle", cycle=True)
    time.sleep(8)
    shot("cycle-built")
    adb("shell", "input", "swipe", "540", "700", "540", "1900", "200")
    if tap(r"Open cycle", "cycle-sheet"):
        if not find(r"NOW · WEEK", timeout=8):
            FAILS.append("cycle page has no current block")
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
if tap(r"^\|  Schedule$|Schedule$", "schedule-sheet", required=False):
    tap(r"^Mon", "schedule-monday")
    scroll_to(r"^\|Done$", tries=3)
    tap(r"^\|Done$")
if tap(r"Edit program", "editor"):
    if not find(r"DAY FOCUS", timeout=8):
        FAILS.append("editor has no day setup")
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
    if seek(r"Add an exercise") and tap(r"Add an exercise"):
        if tap(r"Create a custom exercise", "custom-create"):
            tap(r"Exercise name")
            adb("shell", "input", "text", "Landmine%sPress")
            back()
            if scroll_to(r"^\|Create and add$"):
                tap(r"^\|Create and add$")
                time.sleep(1.5)
                shot("custom-added")
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

# Template with percentages: build, then set training maxes
tap(r"^\|Plan$|^Plan\|")
scroll_up_to(r"^\|Edit program$|Review block|^\|New$", tries=10)
row_scroll_to(r"^\|New$", r"Review block|^\|Settings$|^\|Details$|^\|Schedule$", tries=6)
if tap(r"^\|New$"):
    if tap(r"^\|Start from a template", "templates"):
        tap(r"Search 75 templates|Search \d+ templates")
        adb("shell", "input", "text", "Main-Lift")
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
                        if not find(r"Texas Method", timeout=6):
                            FAILS.append("method switch did not show the new method")
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
if tap(r"Start workout|Resume workout|^\|?Train .+ now", required=False):  # a workout in progress reopens on its own
    start_from_preview()
time.sleep(3)
tap(r"^\|Skip$", required=False)  # readiness check
shot("tablet-workout")
if find(r"Minimise workout", timeout=4) is None:
    FAILS.append("tablet did not show the workout")
adb("shell", "wm", "size", "reset")
adb("shell", "wm", "density", "reset")

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
