# M203 — Custom-program final-set intensifier repair

Pursuit Iron 4.0.0, build 793. Pursuit Engine 0.64.3.

## Root cause

M201 restored implicit custom-program last-set techniques only when the saved program still carried optional legacy metadata (`engineV`, `slotBias`, or `autoBias`). A migrated/imported program can correctly retain `custom: true` while losing those optional markers, causing every computed custom cell to return `tech: null`. The browser regression fixture itself carried those legacy markers, so it could not reproduce the user's saved-program path.

## Repair

`custom: true` is now the durable identity for this compatibility path. Explicit per-slot technique edits still take precedence, including explicit Off. The periodized eligibility rules are otherwise unchanged. The workout set list also keeps the intensifier badge visible on the final working-set row before that row becomes active, making the attachment unambiguous.

## Verification

The unit regression deletes `engineV`, `slotBias`, and `autoBias` from the custom fixture and requires late-block partials/myo-reps to remain. The phone browser regression uses the same markerless custom-program shape and requires both the technique cue and the visible final-set `+ partials` badge. Existing rep-entry, variation, volume-repair, release-integrity, and PWA gates remain active on main.
