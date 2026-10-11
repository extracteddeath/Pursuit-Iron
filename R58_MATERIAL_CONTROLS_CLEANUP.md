# R58 — Material controls and stylesheet cleanup

The collapsed Home masthead now keeps its 56px height while its three actual
buttons measure 48×48 CSS pixels. Plan Back, Profile Edit and the Settings
section selector use the same minimum touch size. Other screen density and
the workout logging grid retain their established layout.

Settings previously split “Advanced” into two fragments on a normal phone.
The Experience control now gives each complete label enough space: four
columns on a regular phone, two on a narrow phone or at larger text, and one
when larger text meets the narrowest width. Container-query thresholds use
rem units, so changing the reading size changes the layout too. Real buttons
and their persisted choices are retained, including the two-axis selection
animation already supported by the motion coordinator.

389 obsolete standard CSS declarations were removed where the exact same
selector and at-rule context already has a later effective declaration.
Custom properties, keyframes, layers, in-rule fallbacks and newer-function
fallbacks were preserved. This removed 18,805 bytes before the control fixes.
The production stylesheet was replaced, rather than overlaid, in a local
browser comparison: 16 paired Rose/Rosewater screen states showed identical
sampled computed styles for all elements and their before/after pseudo-elements.
Those states cover Home, collapsed Home, Library, Plan, Progress, Profile,
Settings and full Program. Cleanup therefore changes CSS ownership without
changing the established visual result.

The existing R53 phone regression additionally checks real 48px header targets,
the fixed compact masthead, complete Experience text at 320/393px and 100/125%
reading sizes, all four choices, stored selection and preservation of saved
programs. Existing release, core, independent-engine, phone/offline and
20-theme contrast checks remain required before merging this release.

Local results: release integrity, 32 core integration contracts, the full
expressive-motion/contrast browser contract, the paired R53 layouts and the
R54 Library/Detail/Program/Workout contract all passed. The measured Home
masthead is 56px in both palettes at 320px and 393px; the three toolbar
actions, Plan Back, Profile Edit and Settings selector each retain 48px
interaction heights. Experience uses 4/2/2/1 columns for 393px normal,
393px larger text, 320px normal and 320px larger text respectively.

Final local validation covered all 93 existing executable gates: 74 of 75
core contracts, all 17 browser contracts and the release gate passed. The
canonical engine export also passed source identity, application parity and
all 35 independent semantic suites.

The sole local failure is the unchanged M199 wizard timing assertion: 500
checks must take under 500ms. R58 took 596.7ms initially and 582.1ms alone.
The published R57 baseline took 662.7ms on the same machine. All 93 runtime
file hashes, the aggregate engine identity and this test are identical between
R57 and R58. R57's GitHub CI passed the same gate on its Node 22 runner; this
local machine runs Node 24.19.0. The threshold and engine were left unchanged.
This result establishes an existing local performance limitation, not a CI
pass for R58. The isolated R58 branch still needs full CI before any merge.

R57 (Build 836, PR #118) is merged and deployed at
`5d0747101adc1274f070a86edf7365c19301172d`, with all post-merge workflows
passing. R58 (Build 837) is committed locally on
`ui/r58-material-controls-cleanup`. Its push was blocked by automatic approval
review, which did not accept recovered prior chat authorization. No R58
remote branch, PR or deployment was created. Resume by obtaining direct
approval, checking whether main advanced, pushing the isolated reviewed
branch, running every existing CI gate and merging only when all pass.

Source: [Android Developers — minimum touch target sizes](https://developer.android.com/develop/ui/compose/accessibility/api-defaults).
Material specifies 48dp interactive bounds. With the app's mobile viewport,
48 CSS pixels provides the corresponding web layout target; this browser
verification does not certify physical Android gesture or frame timing.
