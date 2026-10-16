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

## What's in this first release

Native: Today (readiness, injuries, next session, recent), the full workout (enter and adjust loads, log and rate
sets, autoregulated next-set prefill, rest with lock-screen countdown, swap, why-this-weight, pain flag, notes,
reorder, add/remove sets), summary with PRs and milestones, Plan (weeks, days, switch program, start any day),
Progress (e1RM trends, records, readiness), Exercises (search, cues, history, ban, notes), You (bodyweight and
measurements, history with delete and undo, settings, backup and import), program setup with the engine's split
pick, phone and tablet layouts, grip mode, keep-screen-on, haptic patterns, light/dark and three palettes.

Not yet native (still in the web app): cycles and block review, the custom program editor, gyms and plate
inventory, the calendar, strength score, the self-test, the gallery, switching units once history exists, the
home-screen widget, Wear OS, and an audible alert when rest ends with the app in the background (the countdown
shows on the lock screen; the alert plays when the app is open).

## Keeping the two apps in step

`native/core/src/core.js` mirrors specific App.js passages, each named beside its mirror. When a release changes
one of those flows in App.js, change the mirror in the same release; `native/core/test` pins the behaviour, and
the engine itself is shared, so engine-only changes need nothing here.
