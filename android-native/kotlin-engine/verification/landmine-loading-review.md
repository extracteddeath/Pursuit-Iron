# Landmine exercise and loading review — 2026-11-15

This is an engineering-provisional source review, not an independent exercise,
physiology, dose or coaching certification. Runtime definitions are Kotlin.

The active web catalog has three exact identities: `meadows-row`,
`landmine-press` and `lm-squat`. Each names landmine equipment, is flagged as
barbell work, and declares an 8–12 practical rep band. The native definitions
preserve those IDs, bands and explicit direct target. Broad source “back” is
provisionally mapped to upper back for Meadows row; it never earns a second
full lat credit. The source's indirect coefficients remain unvalidated
bookkeeping assumptions. The distinct landmine families do not establish
free-bar squat or overhead-press practice or interchangeable performance.

## Physical evidence and declared convention

- [Rogue's landmine instructions](https://assets.roguefitness.com/instructions/IS0164/IS0164_CUST_INSTR_V1)
  distinguish the landmine sleeve, bar, handle, collar and added plates.
- [Rogue's landmine overview](https://www.roguefitness.com/strength-equipment/strength-training/landmines)
  describes a bar inserted into an anchored pivot and separate mounting options.
- [ACE's landmine exercise discussion](https://www.acefitness.org/continuing-education/certified/september-2025/8948/the-ace-workout-builder-for-landmine-training/)
  describes the anchored/free ends and changing force direction during these
  movements. It does not establish numeric set-credit or loading equivalence.

The app deliberately records **added plates at the loaded end only** in
`LANDMINE_ADDED_PLATES`. This is an explicit app input convention, not a claim
that manufacturers universally log this way or that plate mass equals the
athlete's applied force. Bar, pivot, handle and mounting/base mass are excluded;
there is no half-bar assumption, angle correction, doubled plate total or
estimated equivalent free-bar load. Zero explicitly means no added plates:
the bar still provides resistance. A blank input never confirms zero.

A separately confirmed `landmine-station` includes the actual bar and anchored
pivot. The exact entered totals must be attainable on that station; straight
bar, cable, machine and dumbbell inventory cannot prove them. A list of totals
does not prove a physical plate layout, so the symmetric bar plate calculator
never supplies instructions for this basis. Landmine exercises count toward
the athlete's barbell-per-day cap through the existing composer and audits.

## Actual web comparison

At verified `main` commit `12e2bd37258c56255c687cc6abed78d1921f6c07`,
`modules/training-domain/analytics.js` maps landmine equipment to `barbell` in
`barFor`; the generic plate helper then uses `(total - barWeight) / 2` per side.
`modules/training-domain/starting-load.js` also assigns landmines a generic
0.6 implement factor. This native path deliberately uses an explicitly chosen
added-plates convention and exercise-specific calibration, with no symmetric
layout or generic free-bar force conversion. Existing web logs must therefore
be inspected and mapped explicitly during migration; no historical numeric
load can be copied across these conventions automatically.

## Verification and limits

Tests cover source identity, station/basis/unit mismatches, zero and fractional
plate totals, unavailable increments, barbell caps, explicit per-exercise
calibration, real first-program generation and every converted cycle week.
The canonical optional progression review may offer the next confirmed added
plate total from zero only after its usual comparable-session/rep/effort and
athlete step-limit checks. It never writes targets. Current-workout Leveling
includes explicit landmine zeros in its average while excluding completed rows
from edit keys; logged work continues through the ordinary journal.

Native-file tests exercise source, consent, preparation, issuance, actual loads
that differ from targets, correction, retries, cold reads, Finish and verified
History/CSV. The existing independently captured v8/v9/v10 native archives
remain required byte-preservation/replay tests. The enum value is appended;
existing persisted enum names/order, source/approval schemas and original
consent scopes do not change.

Physical-device, large-font, TalkBack, visual, motion and complete app acceptance
remain open. These additions do not prove full engine parity, individualized
force/RIR prediction accuracy or superiority. No APK/AAB is authorized.
