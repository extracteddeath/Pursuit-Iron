# B947 workout exercise rail — concise labels

## Purpose
Reduce truncation in the horizontal exercise/progress navigation pills. This is a display-only change, not an exercise rename.

## Behavior
- Visible workout rail: whole-word abbreviated common gym terms such as Barbell → BB, Dumbbell → DB, Machine → Mach., Extension → Ext., Incline → Incl., and Overhead → OH.
- Canonical exercise titles, the main workout exercise heading, exercise history, identifiers, set completion state, and progression rules are unchanged.
- The rail continues to show the exact `completed/total` working-set count; completed exercises retain the check indicator.
- Screen readers and native title descriptions retain the original full exercise name and ordinal working-set progress.
- Unknown exercise names pass through without alteration. The formatter is idempotent, and protected word boundaries avoid rewriting substrings in words like Hyperextension.

## Verification
`verification/b947-workout-nav-abbreviations-test.mjs` is an integration contract. It checks the user's three photographed exercises, several additional names, full-name accessibility, only-the-rail scope, and preserved working-set counts.

B947 release finalization ran this test and `scripts/verify-release.mjs` successfully, incremented the release to Build 947, and refreshed the service-worker shell to include the new small formatter module. Hosted deployment is tracked separately in the GitHub Pages workflow.
