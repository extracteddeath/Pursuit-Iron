# B949 — Workout header layout audit

## Issue
The normal workout header's metadata wrapper contained the set context and
progression button in the same grid row as the tags. Its stacked height made
the right-side tags appear stranded and left an exaggerated visual gap before
the Warm-up, Superset, Swap and Session Note strip.

## Correction
- One consistent three-row grid: exercise name / actions; set context / tags;
  progression across the width.
- Keep each tag on a single line and horizontally scroll the tags when needed.
- Make the normal-mode metadata wrapper layout-transparent with `display:contents`.
- Reduce the progression row's allocated height and remove the toolbar's extra
  leading margin without modifying the set grid.
- Scope geometry to normal workout mode; Focus mode retains its own header.
- Preserve History, Info, Skip, set count, progression editor and tool actions.

## Regression coverage
- `verification/b884-workout-hierarchy-test.mjs`: maintains the owner/DOM
  structure and validates the new grid row assignments.
- `verification/b896-expressive-workout-browser-test.mjs`: evaluates phone
  and wider viewports, verifies the set context/tags alignment, progression
  spacing and the gap to the tools.
- `scripts/verify-release.mjs`: checks the certified build identity, file
  hashes and new service worker cache revision.

## Acceptance
Confirm the exercise name and control icons do not collide, the set line and
tags are aligned, the progression label occupies its own compact row, and
the toolbar does not have an artificial spacer. Check long names/tags, Focus,
and the presence or absence of optional session notes and progression reasons.

## B950 style-ownership certification

After the B949 layout repair, the UI ownership gate detected four redundant
CSS priority declarations. B950 removes those four redundant declarations,
including two obsolete exercise-navigation width priorities that were already
overridden by the later unified rail, without changing the normal workout
grid. The priority count returns to its pre-change ceiling (2073).
The B950 release workflow explicitly requires both the workout hierarchy
contract and UI ownership contract before certifying and publishing.

The finalized B950 release updates the app version stamp and service-worker
cache together, while keeping the workout-set entry behavior unchanged.
