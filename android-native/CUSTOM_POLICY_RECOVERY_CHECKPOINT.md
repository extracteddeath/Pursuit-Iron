# Kotlin Lab: approved custom programs reopen with their coaching rules

Date: 2026-11-08. Scope: G4/G5 usable training loop.

## Athlete-visible change

Newly approved custom cycles now save the complete reviewed coaching rules
inside their existing approval record. Reopening the owned program restores
those rules, rechecks the full cycle and current gym, and feeds the existing
next-workout selector. The policy editor restores the saved values for further
editing instead of displaying an empty form. Later callbacks do not replace
unsaved edits.

This removes the previous dependence on a remembered screen for an approved
custom program. Generated plans can also recover an exact saved custom review
after deliberately editing and reapproving their original generated cycle.
Unsigned generated drafts retain their calibrated-preview recovery path.

## Authority and compatibility

- The existing `NativeKotlinCustomCycleSignoffFileStore` remains the sole
  approval owner. Version 2 saves review inputs and consent in one atomic
  owner-fenced write. No additional journal or approval authority was added.
- The unchanged consent fingerprint still binds the exact cycle, source,
  phase policies, dose/frequency targets, session overhead and all four
  optional continuity ceilings. Recovering a checksum-valid file also runs
  the complete audit and verifies that fingerprint.
- Saved inputs retain separate muscle channels, explicit zeroes, field order,
  decimal scale, phase identity and absent optional ceilings. A bounded typed
  codec deep-copies caller collections before approval and returns immutable
  collections after decoding. It neither invents nor rounds training rules.
- Program/source changes, wrong identity, revoked consent, corrupt bytes or
  altered policy content cannot restore an approved review. Existing logged
  workouts and authored prescriptions are not rewritten.
- Coaching Manual mode does not disable reopening the athlete's own approved
  program. Recovery makes no new optional coaching recommendation.
- Existing version-1 approval receipts retain their original fingerprint and
  revision. Their omitted policy inputs cannot be reconstructed from a hash.
  Custom programs with those older receipts need one explicit fresh review
  and signoff to save the rules. No automatic migration invents those values.

## Executed evidence

The complete local native persistence suite passes **468 tests**. It includes:

- A nine-session, three-week genuinely custom program using fresh disk
  recovery of all review rules before each next-session check, preparation,
  issuance, actual logging and manifest-backed Finish. Authored week overrides,
  duplicate Finish acknowledgments and nullable actual RIR remain preserved.
- Independent V1-envelope replay followed by one explicit V2 renewal, unchanged
  consent fingerprints, immutable recovered collections and Manual mode.
- Checksum-valid changes to policy rationale and session overhead, revoked
  approval, changed program/gym, wrong identity and corrupt records.
- All phases, distinct biceps/brachialis and wrist-flexion/extension channels,
  optional continuity limits, explicit zeroes, malformed input and bounded
  decimal expansion.

Android source compilation and the isolated full CI pipeline remain required.
Physical-device interaction, backup/history migration, personalized fatigue
forecast calibration and broader program/cycle quality remain open gates.
This is a restart/usability improvement, not evidence of physiological or
overall engine superiority. No new APK or production cutover is included.
