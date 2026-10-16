# Pursuit Iron for Android (native)

A Kotlin + Jetpack Compose app in the M3 Expressive design, running the **same training engine as the web app**. The engine
is not ported: `modules/engine-api.js` is bundled with a thin app core and runs on the phone in Android's isolated
JavaScript sandbox (`androidx.javascriptengine`), with a headless WebView as the fallback on devices without it. The
data is the web app's own `wpb:v1` store, so a transfer is lossless and every number matches.

```
native/
  core/       JS app core: the App.js flows the screens need, as pure functions over the store
    src/core.js       mirrors App.js passage by passage (each mirror names its source)
    src/bridge.js     one entry point for Kotlin: PICore.call(method, argsJson) -> resultJson
    test/             core flows, bare-runtime bundle, polyfills (run in CI on every push)
  android/    the Compose app
    engine/   JsEngine, SandboxEngine, WebViewEngine, Core (serialised calls, atomic persistence)
    ui/       PiApp (bar under 600dp, rail above), screens/, theme/ (design-system tokens), common/
    smoke/    emulator run: drives the real app by accessibility labels, takes screenshots
  handoff/    pi-transfer.html, the web page that sends a user's data to the app
```

## Building

CI builds on every push to `native-app` (`.github/workflows/native-android.yml`):

1. core tests, then `node build.mjs` bundles the engine into `assets/pi-core.js` (never committed)
2. Gradle builds a **debug APK** (package suffix `.dev`, installs beside the store app) and a **release AAB**
3. an emulator installs the debug APK and runs `smoke/smoke.py`; screenshots are in the `smoke-screens-N` artifact

To build locally: run `node native/core/build.mjs`, then open `native/android` in Android Studio.

## Shipping it as an update to the current Play listing

The native app replaces the web-app (TWA) build under the **same package name**, so it reaches existing users as an update.

1. **Package name.** Play Console → your app → the name under the title. Set it as the repository variable
   `PI_APP_ID` (Settings → Secrets and variables → Actions → Variables). The default in the workflow is a guess.
2. **Version code.** Play only accepts a higher version code than any build it has. Find the current highest in
   Play Console → App bundle explorer, then set `PI_VERSION_CODE_BASE` so that base + CI run number is above it.
3. **Upload key.** Sign with the same upload key the TWA used (the `android.keystore` Bubblewrap or PWABuilder
   created). Add four repository secrets: `PI_KEYSTORE_BASE64` (`base64 -w0 android.keystore`),
   `PI_KEYSTORE_PASSWORD`, `PI_KEY_ALIAS`, `PI_KEY_PASSWORD`. If the key is lost, Play Console → App integrity →
   request an upload key reset. Play App Signing re-signs for users either way.
4. **Publish the transfer page before anyone updates.** Create the public repository
   `extracteddeath/extracteddeath.github.io`, add `handoff/pi-transfer.html` at its root as `pi-transfer.html`
   plus an empty `.nojekyll`, and turn on Pages. Check that `https://extracteddeath.github.io/pi-transfer.html` loads.
   It cannot live in the `Pursuit-Iron` repository: the web app's service worker answers every page under
   `/Pursuit-Iron/` with the cached app shell, so the page would never load for anyone who has used the app. The site
   root is the same origin, so it reads the same storage.
5. **Internal testing first.** Upload the `.aab` from the run's artifact to the internal testing track, install it
   over the current app on your own phone, and move your real data across (below).
6. **Listing.** Data safety is unchanged: nothing is collected or shared, and data stays on the device. The
   notification permission is only for the rest countdown. Take new screenshots, since the design changed.
7. **Staged rollout.** Release to production at a small percentage, then widen.

### What an existing user sees

Updating replaces the app, but their history stays where the TWA kept it: in Chrome's storage for the site. On
first launch Today shows **Bring your workouts over**. That opens `pi-transfer.html` in Chrome, which shows the
counts and a **Send to the app** button:

* a store under about 450 KB compressed travels in the `pursuitiron://handoff` link itself (one tap; most users)
* larger stores go through the share sheet (pick Pursuit Iron) or a downloaded backup file (You → Settings → Import)

The app asks before importing and merges with the web app's own `mergeStores`, so nothing on the phone is
deleted and importing twice adds nothing. A workout in progress comes across too.

## What's in this release

Native: Today (readiness, injuries, next session, cycle position, recent), the full workout (enter and adjust loads,
log and rate sets, autoregulated next-set prefill, rest with lock-screen countdown and an audible "rest over" alert in
the background, swap, why-this-weight, pain flag, notes, reorder, add/remove sets, plate calculator), summary with PRs
and milestones, Plan (weeks, days, switch program, start any day, build next block), training cycles (preset and custom
templates, adapt between blocks, detail, complete block, block review, delete with undo), the program editor (swap,
move, add, remove, supersets, rename/duplicate/move/delete days, manual sets and reps, drafts, save to one block or the
whole cycle, undo save), duplicate and delete programs, Progress (e1RM trends, records, readiness), Exercises (search,
cues, history, ban, notes), You: profile (level and XP, Strength Score with trend and next milestone, strength club,
achievements), body (bodyweight and measurements), history with a training calendar, settings (unit switching that
converts every stored load, gyms with equipment and heaviest-available limits, plates owned, smallest load jump, sex
and birth date for the standards, plate and 1RM tools, backup and import), program setup with the engine's split pick,
a home-screen widget (next workout or the one in progress, Start/Resume), phone and tablet layouts, grip mode,
keep-screen-on, haptic patterns, light/dark and three palettes.

Every list the web app syncs (programs, cycles, history) is stamped and tombstoned the same way it does, so a backup
moved either direction merges cleanly.

Also native: templates (all 75, by category, built for the active gym) with training maxes for the percentage
programs, program settings that rebuild the plan (goal, length, session time, set/rep mode, volume approach,
barbell limit, bodyweight and superset options) plus shuffle and restore of earlier layouts, comparing programs or
exercises side by side, custom exercises, training reminders (weekday notifications that survive a reboot), the
injury log outside a workout, Strong and Hevy CSV import, and workout and body CSV export.

The workout matches the web app: readiness check-in that scales loads, warm-up ramps, drop sets and myo-reps,
standard / warm-up / to-failure set types, editing logged sets, the previous workout's set beside each row, last
time's note, per-lift rest saved to the program, suggested supersets with round-based rest, a per-lift progression
change that asks at the end whether the program keeps it, goal weight, machine weight step and setup notes, form cues
and a demo link, a workout note, reset timer, skip a lift, swap into the program, and the finish sheet (PRs, goals
reached, main-lift e1RM, next-session plan, pump and soreness per muscle). Quick workouts log off-plan and can be
saved as a routine; any past workout can be repeated. Today adds the welcome screen, what's new, the fresher-day
option, deload advice with "deload now", a lighter session when recovery is low, and the recovery, strength and
weekly recap cards. Progress adds a chart and records for every lift, strength standards, correcting a logged
workout and a share image. Settings adds the effort scale (RIR or RPE), training experience, the banned list,
clearing history, resetting everything, device storage, bug reports, Ko-fi, the privacy policy and references; Plan
has the weekly schedule.

Not native yet: Wear OS (a separate watch app that needs Google Play services on the phone for the data link; the
design is in the prototype). The web app's program gallery is not ported because the web app itself no longer
imports gallery codes.

## Keeping the two apps in step

`native/core/src/core.js` mirrors specific App.js passages, each named beside its mirror. When a release changes
one of those flows in App.js, change the mirror in the same release; `native/core/test` pins the behaviour, and
the engine itself is shared, so engine-only changes need nothing here.
