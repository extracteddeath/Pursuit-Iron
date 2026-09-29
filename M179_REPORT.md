# M179 physical Android installed-PWA certification

Target baseline: **M178 / app 3.228.0 / build 784 / Pursuit Engine 0.63.2**.

## Purpose

M176 and M178 automated the browser/source behaviors that can be reproduced deterministically, but a real Android process death and installed-PWA launcher lifecycle cannot be certified by headless Chrome. M179 turns that remaining gap into an explicit, evidence-bearing device gate rather than an informal checklist.

## Added on this branch

- `verification/M179_DEVICE_QA_TEMPLATE.json`: ten required installed-PWA scenarios covering cold start, real process kill during a workout, rest-timer restoration, ordinary background/resume, offline relaunch, idle update, active-workout update protection, deferred update after workout, device reboot persistence, and rapid lifecycle stress.
- `scripts/m179-device-cert.mjs`: an interactive device runner that snapshots the exact release manifest being tested, records device metadata and per-scenario evidence, persists results after every answer, and refuses certification when the tested app/build/runtime aggregate differs from the current checkout.
- Required scenarios cannot be silently skipped: certification succeeds only when every required scenario has a `pass` result with meaningful evidence.

## Commands

```bash
node scripts/m179-device-cert.mjs init
node scripts/m179-device-cert.mjs run
node scripts/m179-device-cert.mjs validate
```

The default result file is `verification/M179_DEVICE_QA_RESULTS.json` and should contain observations from the physical Android device.

## Status

**Harness ready; physical Android certification pending.** Do not promote this milestone based only on repository automation.
