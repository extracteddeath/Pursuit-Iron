#!/usr/bin/env python3
"""Real disposable Android UI journey. All training logs here are synthetic QA.

Uses the shipped setup/program/journal controls and native stores. No database
seeding, intent-only shortcuts, imported web data or fabricated pass receipts.
Does not grade physiological predictions, accessibility or physical phones.
"""
import argparse
import json
import pathlib
import re
import subprocess
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument("--package", required=True)
args = parser.parse_args()
package = args.package
assert re.fullmatch(r"io\.github\.extracteddeath\.pursuitiron\.kotlinlabtrial\d+", package)
out = pathlib.Path("smoke-out/journey")
out.mkdir(parents=True, exist_ok=True)
events = []
component = package + "/io.github.extracteddeath.pursuitiron.KotlinEnginePreviewActivity"


def adb(*values, binary=False):
    result = subprocess.run(["adb", *map(str, values)], check=True,
                            capture_output=True, timeout=40)
    return result.stdout if binary else result.stdout.decode("utf-8", errors="replace")


def bounds(node):
    return tuple(map(int, re.findall(r"\d+", node.attrib.get("bounds", ""))))


def tree():
    adb("shell", "uiautomator", "dump", "/sdcard/kotlin-journey.xml")
    xml = adb("exec-out", "cat", "/sdcard/kotlin-journey.xml")
    (out / "last-ui.xml").write_text(xml)
    root = ET.fromstring(xml)
    return root


def text_of(node):
    return " ".join((node.get("text", "") or node.get("content-desc", "")).split())


def find(root, label):
    return [n for n in root.iter("node") if n.get("package") == package
            and text_of(n) == label and len(bounds(n)) == 4
            and bounds(n)[2] > bounds(n)[0] and bounds(n)[3] > bounds(n)[1]]


def swipe(down=True, distance=950):
    # Swipe only inside the scrolling content, away from the fixed navigation.
    start, end = (1850, max(430, 1850 - distance)) if down else (500, min(1900, 500 + distance))
    # The outer scroll owns this gutter; editable fields cannot capture it.
    # Fast swipes fling well beyond the requested distance. In particular they
    # can skip a calibration heading that was already visible near the bottom.
    adb("shell", "input", "swipe", 22, start, 22, end, 1000)
    time.sleep(.25)


def top():
    previous = None
    for _ in range(40):
        root = tree()
        signature = [(n.get("class"), n.get("bounds"),
                      re.sub(r"\b\d{1,2}:\d{2}\b", "#time", text_of(n)))
                     for n in root.iter("node")
                     if n.get("package") == package]
        if signature == previous:
            return root
        previous = signature
        swipe(False, 1400)
    return tree()


def locate(label, *, scroll=True, limit=45, center=False):
    for attempt in range(limit if scroll else 1):
        root = tree()
        matches = find(root, label)
        with (out / "control-search.jsonl").open("a") as trace:
            trace.write(json.dumps({"control": label, "attempt": attempt,
                                    "visible": [text_of(n) for n in root.iter("node")
                                                if n.get("package") == package and text_of(n)]}) + "\n")
        if matches:
            node = matches[0]
            if center and bounds(node)[1] > 1250:
                swipe(True, min(350, bounds(node)[1] - 1100))
                continue
            return root, node
        if not scroll:
            break
        swipe()
    raise AssertionError("Control not found: " + label)


def press_node(root, node):
    parents = {child: parent for parent in root.iter() for child in parent}
    check = node
    while check is not None:
        assert check.get("enabled", "true") != "false", "Disabled control: " + text_of(node)
        if check.get("clickable") == "true":
            node = check
            break
        check = parents.get(check)
    x1, y1, x2, y2 = bounds(node)
    adb("shell", "input", "tap", (x1 + x2) // 2, (y1 + y2) // 2)
    time.sleep(.35)


def enabled(root, node):
    parents = {child: parent for parent in root.iter() for child in parent}
    while node is not None:
        if node.get("enabled", "true") == "false":
            return False
        node = parents.get(node)
    return True


def tap(label, *, scroll=True, limit=45):
    root, node = locate(label, scroll=scroll, limit=limit)
    end = time.monotonic() + 20
    while not enabled(root, node) and time.monotonic() < end:
        time.sleep(.3)
        root, node = locate(label, scroll=scroll, limit=limit)
    press_node(root, node)
    events.append({"tap": label})


def wait_for(label, seconds=30):
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        root = tree()
        if find(root, label):
            return root
        time.sleep(.5)
    raise AssertionError("Expected visible state: " + label)


def choice(title, value):
    root, heading = locate(title, center=True)
    bottom = bounds(heading)[3]
    candidates = [n for n in find(root, value) if bottom <= bounds(n)[1] < bottom + 550]
    assert candidates, "Choice missing below " + title + ": " + value
    press_node(root, min(candidates, key=lambda n: bounds(n)[1]))


def edit_node(root, label):
    matches = find(root, label)
    parents = {child: parent for parent in root.iter() for child in parent}
    for match in matches:
        node = match
        while node is not None:
            if node.get("class") == "android.widget.EditText":
                return node
            node = parents.get(node)
        x1, y1, x2, y2 = bounds(match)
        edits = [n for n in root.iter("node") if n.get("class") == "android.widget.EditText"
                 and len(bounds(n)) == 4 and bounds(n)[0] <= x1 < bounds(n)[2]
                 and bounds(n)[1] <= y1 < bounds(n)[3]]
        if edits:
            return edits[0]
    raise AssertionError("Editable field missing: " + label)


def dismiss_keyboard():
    state = adb("shell", "dumpsys", "input_method")
    if "mInputShown=true" in state or "mIsInputViewShown=true" in state:
        adb("shell", "input", "keyevent", "KEYCODE_BACK")


def enter(label, value):
    root, _ = locate(label, center=True)
    node = edit_node(root, label)
    press_node(root, node)
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END", *(["KEYCODE_DEL"] * 80))
    adb("shell", "input", "text", value.replace(" ", "%s"))
    dismiss_keyboard()
    events.append({"entered": label, "value": value})


def enter_below(heading, value):
    root, title = locate(heading, center=True)
    candidates = [n for n in root.iter("node") if n.get("class") == "android.widget.EditText"
                  and len(bounds(n)) == 4 and bounds(n)[1] >= bounds(title)[3]
                  and bounds(n)[1] < bounds(title)[3] + 600]
    assert candidates, "No load field below " + heading
    node = min(candidates, key=lambda n: bounds(n)[1])
    press_node(root, node)
    adb("shell", "input", "text", value)
    dismiss_keyboard()
    events.append({"calibrated": heading, "load": value})


def assert_value(label, value):
    root, _ = locate(label, center=True)
    node = edit_node(root, label)
    assert any(value == text_of(n) for n in node.iter()), "Unsubmitted value lost: " + label


def capture(name):
    (out / (name + ".png")).write_bytes(adb("exec-out", "screencap", "-p", binary=True))
    root = tree()
    ET.ElementTree(root).write(out / (name + ".xml"), encoding="unicode")
    (out / "events.json").write_text(json.dumps(events, indent=2))


def expect_saved_sets(count):
    top()
    pattern = re.compile(rf"^{count} of \d+ sets recorded$")
    end = time.monotonic() + 25
    while time.monotonic() < end:
        if any(pattern.fullmatch(text_of(n)) for n in tree().iter("node")):
            return
        time.sleep(.3)
    raise AssertionError(f"Expected exactly {count} committed sets")


def history_checkbox(label):
    root, text = locate(label, scroll=False)
    y = sum(bounds(text)[1::2]) / 2
    matches = [n for n in root.iter("node") if n.get("checkable") == "true"
               and len(bounds(n)) == 4 and abs(sum(bounds(n)[1::2]) / 2 - y) < 130]
    assert matches, "History consent checkbox missing"
    press_node(root, min(matches, key=lambda n: abs(sum(bounds(n)[1::2]) / 2 - y)))


def clear_running_rest():
    # Synthetic QA is not physical training. Use the actual timer control
    # rather than waiting for a prescribed recovery interval between records.
    root = tree()
    running = [n for n in root.iter("node") if n.get("package") == package
               and text_of(n).startswith("Rest · ")]
    if not running:
        return
    parents = {child: parent for parent in root.iter() for child in parent}
    node = running[0]
    while node is not None and node.get("long-clickable") != "true":
        node = parents.get(node)
    if node is not None:
        assert enabled(root, node), "Rest reset is disabled"
        x1, y1, x2, y2 = bounds(node)
        x, y = (x1 + x2) // 2, (y1 + y2) // 2
        adb("shell", "input", "swipe", x, y, x, y, 1100)
    else:
        controls = find(root, "Clear rest")
        assert controls, "Active rest has no reset control"
        press_node(root, controls[0])
    assert not any(text_of(n).startswith("Rest · ") for n in tree().iter("node")
                   if n.get("package") == package), "Rest reset did not clear the timer"
    events.append({"cleared_rest_through_ui": True})


try:
    capture("00-fresh-setup")
    choice("Primary goal", "Hypertrophy")
    choice("Experience", "Advanced")
    choice("Preferred training split", "Upper / Lower")
    choice("Session time preference", "40–60 min")
    choice("Hard time limit", "No hard cap")
    tap("Use this device's time zone")
    for day in ("Mon", "Tue", "Thu", "Fri"):
        choice("Your training days (3–6)", day)
    tap("Use these training days and keep the other days for rest")
    tap("Balanced training")
    tap("Use all eligible exercises")
    tap("Continue")
    top()
    choice("Weight units", "lb")
    choice("Allow bodyweight exercises", "Allow")
    choice("Allow supersets", "Don't allow")
    choice("Triceps on lower days", "Don't allow")
    enter("Maximum barbell exercises per day (0–12)", "1")
    enter("Available dumbbells · per hand", "5,10,15,20,25,30,35,40,45,50")
    top()
    choice("Weight units", "kg")
    wait_for("Change weight units?")
    tap("Keep my weights", scroll=False)
    assert_value("Available dumbbells · per hand", "5,10,15,20,25,30,35,40,45,50")
    enter("Empty barbell weight", "45")
    enter("Available plate pairs", "45x2,25x2,10x2,5x2")
    tap("Flat bench")
    tap("Squat rack")
    tap("I checked this equipment and these available weights")
    capture("01-gym-confirmed")
    tap("Review complete setup")
    top()
    wait_for("Ready to save")
    capture("02-reviewed-setup")
    tap("Save setup and build my program")
    tap("Save setup", scroll=False)
    wait_for("Build your first program")
    for name, value in (
        ("Barbell Bench Press", "135"), ("Back Squat", "135"),
        ("One-Arm Dumbbell Row", "30"), ("Dumbbell Lateral Raise", "15"),
        ("Dumbbell Curl", "25"), ("Dumbbell Overhead Triceps Extension", "25"),
        ("Dumbbell Romanian Deadlift", "30"), ("Dumbbell Calf Raise", "30"),
    ):
        top()
        enter_below(name, value)
    top()
    locate("Bodyweight Crunch", center=True)
    history_checkbox("I can perform this bodyweight movement")
    tap("Review my proposed first week")
    tap("Save this first-program draft")
    tap("Save new draft", scroll=False)
    tap("Continue to full-cycle review")
    top()
    wait_for("Next workout in your program")
    assert not any(text_of(n).startswith("Cycle ID:") for n in tree().iter("node")), \
        "Normal Plan exposes a technical cycle identifier"
    capture("02a-plan-overview")
    tap("Open full program")
    locate("Name & notes")
    tap("Author complete phase policy and session overhead")
    enter("Policy ID", "qa-unsaved-policy")
    top()
    review_root, review_button = locate("Review program approval")
    assert not enabled(review_root, review_button), "Unreviewed policy draft retained approval"
    tap("Close full program")
    tap("Open full program")
    assert_value("Policy ID", "qa-unsaved-policy")
    capture("02b-retained-review-draft")
    top()
    tap("Discard unsaved review")
    top()
    tap("Close full program")
    top()
    tap("Review program approval")
    tap("I approve this reviewed cycle", scroll=False)
    tap("History")
    tap("Review separate-history choice")
    history_checkbox("I want a new independent history, not an import.")
    tap("Start separate history", scroll=False)
    wait_for("Your first finished workout will appear here.")
    tap("Continue to my program")
    top()
    tap("Find my next workout")
    tap("Verify this exact workout")
    tap("Prepare this workout")
    tap("Issue this exact workout")
    tap("Issue workout", scroll=False)
    tap("Open workout logger")
    locate("UP NEXT")
    locate("Focus on next")
    enter("Reps completed", "6")
    top()
    tap("All sets", scroll=False)
    assert_value("Reps completed", "6")
    top()
    tap("Focus on next", scroll=False)
    assert_value("Reps completed", "6")
    top()
    tap("Last")
    assert_value("Reps completed", "6")
    top()
    tap("Target")
    assert_value("Reps completed", "6")
    adb("shell", "settings", "put", "system", "font_scale", "1.6")
    time.sleep(3)
    assert_value("Reps completed", "6")
    capture("03a-large-text-entry")
    tap("Complete")
    clear_running_rest()
    adb("shell", "settings", "put", "system", "font_scale", "1.0")
    time.sleep(3)
    expect_saved_sets(1)
    tap("Complete")
    clear_running_rest()
    expect_saved_sets(2)
    capture("03-two-saved-sets")
    adb("shell", "am", "force-stop", package)
    adb("shell", "am", "start", "-W", "-n", component)
    time.sleep(3)
    tap("Resume my saved setup")
    tap("Verify and resume", scroll=False)
    tap("Continue to my program")
    top()
    tap("Find my next workout")
    tap("Resume my workout")
    expect_saved_sets(2)
    capture("04-recovered-workout")
    for _ in range(100):
        root = tree()
        if find(root, "Complete"):
            tap("Complete", scroll=False)
            clear_running_rest()
            continue
        if any(enabled(root, n) for n in find(root, "Review Finish")):
            break
        swipe()
    else:
        raise AssertionError("Workout did not reach Finish within 100 explicit set actions")
    tap("Review Finish")
    tap("Commit native Finish", scroll=False)
    wait_for("Workout saved")
    capture("05a-finish-result")
    tap("View recorded sets", scroll=False)
    locate("Recorded: completed · 6 reps at 105")
    top()
    tap("Hide recorded sets")
    wait_for("Workout saved")
    tap("View finished workout in history")
    wait_for("Session 1")
    capture("05-saved-history")
    tap("Continue to my program")
    top()
    tap("Find my next workout")
    end = time.monotonic() + 25
    while time.monotonic() < end:
        if any(text_of(n).startswith("Week 1 · Tuesday · ") for n in tree().iter("node")):
            break
        time.sleep(.3)
    else:
        raise AssertionError("Next workout did not advance to Tuesday")
    capture("06-next-program-day")
    result = {"status": "PASS", "scope": "synthetic real-UI training journey",
              "checks": ["reviewed setup", "unit cancellation preserves stock", "generated four-day program",
                         "Plan overview and full-program disclosure",
                         "unsaved policy draft retained without approval and explicitly discarded",
                         "explicit cycle and history consent", "Focus/All preserves unsubmitted reps", "Target/Last preserves unsubmitted reps",
                         "1.6 font scale retains unsubmitted reps and completes a set",
                         "native set writes", "cold recovery", "Finish to History", "next independent day"],
              "not_certified": ["physiological prediction superiority", "physical phone", "backup/migration", "accessibility"]}
    (out / "result.json").write_text(json.dumps(result, indent=2))
    print(json.dumps(result))
except Exception as error:
    try:
        capture("failure")
    finally:
        (out / "result.json").write_text(json.dumps({"status": "FAIL", "reason": str(error)}, indent=2))
    raise
