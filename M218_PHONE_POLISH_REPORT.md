# M218 — Phone polish

Pursuit Iron 4.0.0 build 808 · Pursuit Engine 0.64.10.

Baseline: M217 build 807, commit `edb148fcdbb9e5d3774dc1582cd53c43f773b9c2`.

## Changes

The What's new card now formats the current release through the same adapter as the full changelog. M217 passed raw string entries to a renderer that expected an icon/title object, which caused the reported “What's new couldn't load” error. The new production-browser flow reproduced the error before the fix and covers Home loading, persistent dismissal, Settings replay, Android Back, and opening/closing the mixed-format archive without changing training data.

The workout screen gives set entry more visual priority. Focus, options, exercise info, history, and skip use quieter controls; the exercise tools form one toolbar. The progression method is a plain label, and the final-set technique keeps its expandable description beside the exercise and a readable tag on its final working row. Target load and rep range occupy deliberate separate lines, keeping ranges such as 10–15 together. Small steppers, typed values, effort reporting, and the existing set layout keep their interaction owners.

Plan shows the program name clearly, labels weekly sets, presents completion once, and puts Up Next near the top. Phase headers retain their icons and full names, with readable goal labels. Selecting a later week expands it and scrolls its days below the sticky week picker; the selected week stays centered and remains selected across Schedule, Volume, Progression, and Blueprint. Initial opening centers the current week horizontally without scrolling past the overview. The full chronological schedule remains available.

Settings displays the saved theme in one compact row. A native details disclosure opens every light and dark option, supports keyboard activation, and preserves the chosen theme on reload. Complete labels fit 320–390px layouts. This makes everyday training preferences reachable without scrolling through the theme grid first.

The release rotates the offline cache and refreshes the in-app notes. All 58 runtime/vendor modules outside App.js are byte-identical to M217, as confirmed by the runtime-file ledger and aggregate. Training and storage policy are unchanged.

## Verification

| Check | Result |
|---|---|
| Full release integrity | Pass: current hashes, offline entries, authored JS parsing, engine/shell and display contracts |
| Release notes regression | Pass: reproduced the error boundary before repair; Home and Settings render valid note icons/text; dismissal survives reload; Back and archive navigation preserve training data |
| New full-app phone flow | Pass: keyboard theme disclosure, light/dark selection, reload, complete labels, unchanged programs/history |
| Later-week navigation | Pass: weeks 10 and 5 become reachable below the sticky picker at 320/390px; all four Plan views retain selection |
| Workout entry and readability | Pass: typed 125.5 lb and 14 reps fit 320/360/390px and landscape; rep ranges and final-set labels remain readable |
| Workout behavior | Pass: expandable technique description, numeric focus, focused mode, and Back/resume preserve manual values |
| Prescription/variation regression | Pass: auto 14/13 corrections, manual 14 and blanks, later-week custom intensifiers, eight variant previews |
| Duration regression | Pass: ten-week saved phase, cycle-owned lengths, compact stepping/typing/validation, reload |
| History and volume regression | Pass: actual completion retains immutable prescription/effort provenance; Auto-fix survives canonical save/reload |
| Android recovery regression | Pass: failed writes keep workouts open, stable IDs prevent duplicate recovery, paused timers survive offline renderer replacement |
| Real service-worker update | Pass: waiting worker → Restart → reload/cache rotation preserves local training data |

The new browser flow joins the existing six Phone and offline integrity gates. Screenshot artifacts are produced by those gates for visual review.

The Android viewport gate waits for finite entry/closing animations to finish before measuring the shell. Its original bounds assertion remains unchanged; this prevents an in-flight translation from being mistaken for a layout overflow on a faster CI runner.

Local browser verification used Chromium's headless shell. Browser viewport and Back checks provide application evidence; physical Android keyboard, system font scale, gestures, and OEM lifecycle checks remain separate device-certification work.
