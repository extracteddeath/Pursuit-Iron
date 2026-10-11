# R59 — App flow and return navigation

Build 838 continues from deployed Build 837 / PR #119 at
`2804a5a7e3456333e82a26abf2d27f289ffc44d1`.

A production-browser audit reproduced four broken journeys on that baseline:

| Journey | Before | Build 838 |
| --- | --- | --- |
| Browse a different saved program, then tap Plan | Plan silently uses the last browsed program | Plan follows the same active program as Home |
| Plan week 3 → preview → Back | Returns to week 1 at scroll position 0 | Restores week 3 and the exact scroll position |
| Fresh Plan → day preview → View full program | Bounces to Home because no editor program was initialized | Opens that program through its existing draft-aware path, on the previewed week |
| Start from Plan or resume from Progress, then minimize | Always arrives on Program | Returns to Plan or Progress with the existing live workout intact |

The Plan keeps each program's selected week, section and disclosure state for
the current app visit. Switching the active program cannot inherit another
program's browsing state. When the lifter was viewing the current week and
completion advances the training cursor, Plan follows the newly due week and
reveals its next-workout action at the top. Deliberate browsing of another week
retains its context.

The existing main-screen scroll tracker now includes the actual Plan and day
preview scrollers. Only the primary scroller can write the route's scroll
memory; opening a nested sheet cannot overwrite it. Full Program and preview
use the existing navigation stack, preserving their Back path. Plan is also
included in the shared list of root destinations.

Starting a workout remembers its launch destination and Back stack. The day
preview is a launch step, so its caller is the return destination. Resuming
from a different screen updates the destination for the same session. The
existing workout component still persists before minimizing, and completion
still commits durable history before leaving the session. No new storage
schema or training-data persistence path is introduced.

Permanent coverage extends the existing R54 and M217 gates rather than adding
another gate or parallel implementation. R54 exercises both 320px and 393px in
Rose dark and Rosewater light, including fresh previews, full-program week
handoff, exact Back scroll restoration, remembered Plan sections, explicit
program switches, typed sets, same-session resume, one completed history record,
full-program workout returns, and the next due week. M217 additionally checks
Home returns after minimizing and finishing a workout recovered offline.

The B831 chart fixture now uses noon UTC, keeping its first sample in 2024
across local timezones while retaining the original distinct-year assertion.
Its exercise-options pointer click waits for the real 44px target to finish
moving and become unobstructed; contrast, touch-size and disclosure thresholds
are unchanged.

All 93 runtime files outside App.js remain byte-identical to Build 837. The
engine/runtime aggregate is
`a4b790fc05526542d040ea5d6322a704c4ceed23ea55ab8106c257a386202a1c`.
CSS, theme roles, workout inputs and steppers, prescription calculations,
generation, adaptation and storage transactions retain their existing owners.

Release policy: the full existing four CI workflows must pass on this exact
release head before merging. Main-branch release, core, independent-engine,
phone/offline and Pages workflows must then pass, and deployed production
assets must match the reviewed release bytes.
