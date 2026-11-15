#!/usr/bin/env python3
"""Frame pacing and start-up on the RELEASE build (R8, not debuggable), with a year of history imported.

smoke.py measures the debug build, which Compose runs interpreted and unoptimised, so its janky-frame share says
nothing about what a user gets. This installs the `benchmark` build type (release code, debug signing), imports a
long history through the same handoff link a migrating web user uses, and records cold start and frame percentiles
while scrolling every tab. The emulator renders in software, so read the numbers as a trend between runs.
Usage: perf.py <package> <outdir> <handoff-link-file>
"""
import os, re, subprocess, sys, time, xml.etree.ElementTree as ET

PKG, OUT, LINK = sys.argv[1], sys.argv[2], sys.argv[3]
os.makedirs(OUT, exist_ok=True)


def adb(*a, timeout=90):
    try:
        return subprocess.run(["adb", *a], capture_output=True, text=True, timeout=timeout).stdout
    except subprocess.TimeoutExpired:
        return ""


def find(pattern, timeout=12):
    rx, end = re.compile(pattern), time.time() + timeout
    while time.time() < end:
        adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
        xml = adb("shell", "cat", "/sdcard/ui.xml")
        if "<hierarchy" in xml:
            try:
                for n in ET.fromstring(xml[xml.index("<hierarchy"):]).iter("node"):
                    if rx.search((n.get("content-desc") or "") + "|" + (n.get("text") or "")):
                        b = list(map(int, re.findall(r"\d+", n.get("bounds", ""))))
                        if len(b) == 4 and b[2] > b[0] and b[3] > b[1]:
                            return (b[0] + b[2]) // 2, (b[1] + b[3]) // 2
            except ET.ParseError:
                pass
        time.sleep(0.8)
    return None


def tap(pattern, timeout=12):
    p = find(pattern, timeout)
    if p:
        adb("shell", "input", "tap", str(p[0]), str(p[1]))
        time.sleep(1.2)
    return bool(p)


out = []
adb("logcat", "-c")
adb("shell", "settings", "put", "global", "window_animation_scale", "0.5")
first = adb("shell", "am", "start", "-W", "-n", f"{PKG}/com.pursuitiron.app.MainActivity")
out.append("first launch: " + " / ".join(l.strip() for l in first.splitlines() if l.strip()))
time.sleep(6)
pid = adb("shell", "pidof", PKG).strip()
out.append(f"running after launch: {'yes (pid ' + pid + ')' if pid else 'NO'}")
with open(LINK) as f:
    link = f.read().strip()
adb("shell", "am", "start", "-a", "android.intent.action.VIEW", "-d", f"'{link}'", PKG)
time.sleep(4)
imported = tap(r"Bring it over", timeout=20)
out.append(f"history imported: {'yes' if imported else 'NO (numbers below are for an empty store)'}")
time.sleep(3)

starts = []
for _ in range(3):
    adb("shell", "am", "force-stop", PKG); time.sleep(1.5)
    r = adb("shell", "am", "start", "-W", "-n", f"{PKG}/com.pursuitiron.app.MainActivity")
    m = re.search(r"TotalTime: (\d+)", r)
    if m:
        starts.append(int(m.group(1)))
    time.sleep(5)
out.append("cold start ms: " + ", ".join(map(str, starts)) + (f" (median {sorted(starts)[len(starts)//2]})" if starts else ""))

for t in ["Today", "Plan", "Progress", "Exercises", "You"]:
    tap(rf"^\|{t}$|^{t}\|")
    time.sleep(1.5)
    if t == "Progress":
        tap(r"^\|History$", timeout=4)  # the history list is the one that grows with every session
        time.sleep(1.5)
    adb("shell", "dumpsys", "gfxinfo", PKG, "reset")
    for _ in range(3):
        adb("shell", "input", "swipe", "540", "1900", "540", "500", "180"); time.sleep(0.9)
    for _ in range(3):
        adb("shell", "input", "swipe", "540", "500", "540", "1900", "180"); time.sleep(0.9)
    g = adb("shell", "dumpsys", "gfxinfo", PKG)
    pick = lambda k: (re.search(k + r"[^\n]*", g) or [""])[0]
    out.append(f"{t}: " + " | ".join(x for x in [pick(r"Total frames rendered: \d+"), pick(r"Janky frames: \d+ \([\d.]+%\)"),
               pick(r"50th percentile: \d+ms"), pick(r"90th percentile: \d+ms"), pick(r"99th percentile: \d+ms")] if x))

# A release-code build that dies on launch would ship that way: keep its crash, if any, with the numbers.
crash = [l for l in adb("logcat", "-d", "-b", "crash", "-b", "main", timeout=120).splitlines()
         if "FATAL EXCEPTION" in l or ("AndroidRuntime" in l and " E " in l) or "CoreException" in l or "PICore" in l]
out.append(f"crash / engine error lines: {len(crash)}")
out.extend("  " + l[-220:] for l in crash[:25])
with open(os.path.join(OUT, "perf-release.txt"), "w") as f:
    f.write("\n".join(out) + "\n")
print("\n".join(out), flush=True)
