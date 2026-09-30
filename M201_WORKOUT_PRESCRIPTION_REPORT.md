# M201 — Custom workout prescription integrity

Pursuit Iron 4.0.0, build 791. Pursuit Engine 0.64.2.

Baseline: live M199 build 789, commit `725219098cf883fca8d464feb2528fc1a0336bb3`, plus the recovered unpublished M200 regional-dose and equipment fixes.

## Reported problems and resulting behavior

Existing custom plans such as the supplied backup stored their last-set technique schedule implicitly. The replacement compatibility cell preserved their legacy set-count deltas but used neutral technique defaults, so every amplifier disappeared. Existing custom plans now regain their original eligible late-block isolation techniques. Introductory weeks, primary lifts, strength days, and deload weeks stay clear. Authored technique overrides, including explicitly turning a technique off, take precedence. This does not overlay the legacy technique rules on engine-generated plans.

The screenshot shows an incline-curl session whose advice says to build toward 12 reps while pending rows contain 14 and 13. Newly generated rows already read the canonical 8–12 range; the active-session restore path previously returned saved automatic fields unchanged, including numbers produced under an older range or tuner. Pending app-owned working rows now reconcile against the current cell on restore and subsequent writes. Completed sets, manual entries, loads, warmups, and myo/drop extensions retain their values. Typing reps now explicitly transfers the row to manual ownership, matching the weight and stepper inputs. A manually logged 14 remains valid, as does an automatic 14 inside an actual 10–15 range.

The screenshot and backup do not include the active `wpb:live` snapshot, so they cannot prove how those individual fields were originally produced. The release reproduces and closes the demonstrated restore gap without modifying stored history.

Eight appended exercise identities now exist in both the app and engine catalogs:

- Seated Dumbbell Lateral Raise
- Single-Arm Dumbbell Lateral Raise
- Chest-Supported Dumbbell Lateral Raise
- Seated Cable Lateral Raise
- Cuff Cable Lateral Raise
- Seated Dumbbell Rear Delt Fly
- Single-Arm Cable Rear Delt Fly
- Chest-Supported Dumbbell Rear Delt Fly

Previously existing band, side-lying, and behind-the-back lateral raises, plus prone rear-delt raises, are also connected to the setup-options browser. Every new identity has equipment requirements, side/rear-delt stimulus, engine mapping, setup text, and a distinct setup illustration. Seated and supported previews use the corresponding posture. Existing IDs and catalog indices are preserved by appending records.

## Verification

`m201-workout-prescription-test.mjs` checks the reported incline-curl history, fresh and resumed targets, array and fixed ranges, immutability, manual ownership, custom technique timing, explicit off/drop/myo behavior, equipment-filtered family browsing, and all eight app/engine identities.

`m201-workout-prescription-browser-test.mjs` uses the real workout component at 390 × 844 to verify stale automatic 14/13 fields become 12, typing/deletion and completed work survive reload, restored custom partials and myo-reps appear, and all eight movement previews render. The production PWA lifecycle and M199 Auto-fix browser gates run separately.

The recovered M200 regression and 71-route creation matrix remain part of release verification. Browser evidence is enforced in CI; this report does not claim a physical Android device test.

M200's documented follow-up findings for short sessions, minimalist targets, and locked-cycle phase boundaries remain open. They are not represented as resolved by this release.
