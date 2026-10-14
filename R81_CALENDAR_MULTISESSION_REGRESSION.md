# R81 — Training-calendar multi-session truth contract

## Scope
A regression-only continuation after merged Build 865 / R80. One local calendar day may have more than one logged workout, even when another workout was planned but not completed. The calendar must not mistake a prescribed target for completed work.

## Permanent browser coverage
The existing `verification/r65-adaptive-shell-browser-test.mjs` creates an actual persisted two-session history for a 393 px phone in the America/New_York timezone and navigates the real Progress route.

- One calendar date is interactive, labeled as **2 workouts**, and counts as **1 training day / 2 workouts**.
- Both full session names appear when the date is opened.
- The modern completed workout shows **3 sets · 2,250 lb · 55m**.
- The scheduled-but-not-done session with `totalSets:8` and no observed completed sets correctly reads **Summary not recorded**, not **8 sets** or an invented zero.
- Milestones remain collapsed; selected-day details do not create horizontal overflow.

## Boundaries
No production code, logger, engine, prescriptions, persisted history, theme tokens, cache or manifest changed. This is an independent test slice; CI must pass on final PR SHA before merge. Real Android keyboard/TalkBack and stylesheet consolidation are still open on the master plan.
