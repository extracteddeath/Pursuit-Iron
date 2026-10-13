# Build 860 — Selected alternative improvements

Baseline: published Build 859 / `765078039140cd4cd308c02abdb281bc72b562f5`.

This release adapts compatible ideas from the supplied Claude Build 808 archive onto the current app. It retains the existing logger, responsive shell, motion owner, normalized history contracts and equipment safeguards.

## Correctness

- Saved unit-less metric templates evaluate and round in kg; the reproduced 27.5 kg case now recommends 30 kg. Newly generated templates record the lifter's unit. Suggestion results carry their evaluation unit and display conversion happens once. Warm-ups resolve their loading inventory in the same unit.
- Loading has one default inventory per unit. Partial kg inventory uses metric defaults. Explicit empty/malformed lists remain empty, and custom bars, plate pairs, exact load lists and numeric overrides convert rather than disappearing. Settings unit changes also mark the converted program/training max unit.
- A shared `liftBests` rule reads normalized history revisions, completed working sets, actual best reps and one unit. Live/finish PRs, recap, goals, Library and share cards use it; a first attempt cannot trigger a live PR. Corrected workout dates reorder history without changing Undo or the performance rebuild owner.
- One-set redistribution charges the destination clock after each move. Redundant-family consolidation rebuilds indexes after each mutation and preserves the keeper's identity. The intentional correction updates only that M230 fragment baseline; the other 66 historical fragment records remain unchanged.
- Converted/history-adapted blocks carry the lifter's session capacity band. The day-permutation search uses one scoped feasibility table; it creates no cross-request cache. Strength explanation specificity and the two-drop coaching cue match the existing audit/runtime protocol.

Engine 0.65.7 explicitly versions these corrections. The last five compatible engines are 0.65.3–0.65.7. The existing M235 property/golden gates remain mandatory; only explicit version assertions move to the new corrected baseline.

## Design

- Shared/redundant prefixes no longer crowd day headings in Plan, Program and the existing workout preview. Saved names and accessible full names remain intact.
- Preview includes the real working-set total and correct deload-inclusive week count. Exercise names wrap; text enlargement reflows its prescription rows.
- The existing theme feature role now uses a restrained accent-hue container in dark themes. Paired text, muted and faint ink clears 4.5:1 in every dark palette. Light semantic surfaces and selected-indicator ownership remain with their existing owners.

No additional global gesture/ripple controller, blanket inventory replacement or duplicate preview route is introduced. Workout stepper geometry, numeric entry, completed-row treatment, pinned-note and tool-chip theme paint, rest bars, Dynamic Double, configured gym bars and offline/live ownership retain their existing gates.

## Validation

`b860-selective-improvements-test.mjs` covers saved/new metric templates, warm-ups, actual and displayed suggestions, custom/empty/malformed inventories, mixed-unit and corrected-revision PRs, exclusion of warm-up/sub/unfinished rows, adversarial two-family consolidation, preserved day identities and dark feature contrast. The original source registry retains all 76 contracts and adds this one.

`b860-selective-improvements-browser-test.mjs` checks preview headings/full accessible names, actual prescriptions and counts, theme feature paint, 200% reflow, Back and saved-prescription equality in Rose, Rosewater and Amethyst at 320, 393, 900 and 1280px. All 21 previous browser gates remain registered. CI is the required real-browser gate because this local execution environment cannot launch Chromium's socket-dependent process.

Source shards, release integrity, canonical export/application parity and all 36 independent semantic suites must pass. Final-head workflows, Pages, post-merge workflows and all deployed asset hashes remain publication requirements. Physical Android/TalkBack/safe-area/touch acceptance and the rest of the master plan remain open.
