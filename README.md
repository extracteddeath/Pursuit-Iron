# Pursuit Iron

[Pursuit Iron](https://extracteddeath.github.io/Pursuit-Iron/) is a web app for planning and logging strength training.

Build a program for strength, muscle growth, or both, using your schedule, available equipment, experience and session time. Create your own routine or link training blocks into a cycle. Your saved prescription, workout targets and logged history have separate, explicit owners.

The app includes:

- Programs and adaptive cycles with reviewed changes before they replace your saved plan.
- Exercise-specific progression, equipment loading steps, effort targets and bounded workout adjustments.
- Fast set logging, rest timers, plate calculations, notes and resume snapshots.
- Training history, lift trends, strength standards, bodyweight and measurement logs.
- Multiple gym inventories, themes, enlarged text and phone/tablet layouts.
- JSON backup/restore with a merge preview and Undo, plus workout and body-metric CSV export and Strong/Hevy workout import.

Training recommendations and strength scores are estimates based on the information available to the app. Review the proposed program and log what you actually perform.

## Data and offline use

Training is stored on your device. No account is required. After the app has loaded and its offline assets have been saved, it can open and log workouts without a connection. You can install it from a supporting browser's app/install menu.

Use one active app window at a time. A second window waits before opening the same device data, so stale copies cannot overwrite a completed workout. If an older app version changes storage unexpectedly, the current window pauses saving and offers its own backup before reloading.

Save a separate backup before changing devices or clearing browser data. A full backup contains saved programs, cycles, completed history, body logs and settings. Unfinished workouts and unsaved program drafts stay on the current device. Restore shows additions, updates and removals before you confirm; it does not silently replace your existing log.

Read the [privacy policy](https://extracteddeath.github.io/Pursuit-Iron/privacy.html) for the current data-handling details.

## Development and release verification

Production uses maintained JavaScript modules, locally hosted React and icons, a static stylesheet and a service worker. No application build step or network-hosted runtime library is required.

For local development, serve this repository on localhost, for example:

```sh
python3 -m http.server 8000
```

Use a current browser with device-storage protection support. The installed PWA requires HTTPS in production; localhost is supported for development.

The current release identity and certified file hashes are in `RELEASE_MANIFEST.json`. Prepare a new shipped build with `node scripts/finalize-release.mjs --next-build`; unchanged finalization does not increment it again. Release validation requires source contracts, the complete engine lifecycle audit, independent engine parity, all browser groups and release/offline integrity. After deployment, verify hosted bytes and replay the installed offline app.

- [Engine architecture](ENGINE_ARCHITECTURE.md)
- [Current improvement plan and acceptance evidence](NEXT_APP_UPDATE_PLAN.md)
- [Engine verification](M235_ENGINE_VERIFICATION_2_REPORT.md)
- [CI and supply-chain verification](M237_CI_SUPPLY_CHAIN_REPORT.md)

Generated screenshots, local archives and build output are ignored. Historical reports remain in Git history; current contracts and engineering guidance stay in the repository. Browser emulation does not certify physical Android interruption, keyboard or TalkBack behavior.
