# Pursuit Iron Native — accessory substitution certification gate

Status: **blocked**, research only. Do not enable a Save action merely
because the exercise catalog returns alternatives.

## Failure found in integration (2026-11-02)

The persisted Pursuit Next program stores engine-owned weekly cell objects
under `nextWeekPrescriptions[dayId:slot]`. Native replacement must **not**
reinterpret these cells as portable to an arbitrary exercise. Removing the
key leads `getNextShellCell` / `computeCell` to return missing prescriptions
and zero working sets. Copying the old cell to a different movement can
preserve incompatible rep windows, effort progression or last-set techniques.
Some generated programs have no alternatives with matching static rep
windows; a picker restricted to that overlap is not feature-complete.

The attempted implementation, not certified or active, is retained at
`feature/native-accessory-swap-prescription-gate`.

## Required engine contract

- Request: exactly one current program/revision/slot and one existing
  catalog exercise ID, with an explicit user confirmation and a protected
  active-session lock.
- Engine checks: available equipment, user exclusions, bodyweight policy,
  training intent, movement pattern, role, unilateral convention, available
  load increments, barbell/day limit, and linked superset/circuit membership.
- Engine output: re-prescribe the new movement's entire remaining
  week/phase schedule through the canonical planner, with its own valid
  reps, RIR, rest, technique eligibility, sets and progression style.
- History: do not migrate observed loads, PRs, failure notes, prior set
  revisions or personal exercise-specific prescription overrides.
- Scope: exactly one selected saved-program block unless separately
  authorized to modify other cycle blocks.
- Transaction: exact revision and source hash, bounded candidate, atomic
  publication with complete old history/other program preservation,
  idempotent retry and rejection after a concurrent modification.
- Verifier: reconstruct allowed metadata changes independently in Kotlin;
  do not trust a candidate merely because the JavaScript runtime prepared
  it. The Kotlin verifier does not invent training prescriptions.
- Acceptance: issue, resume, log, finish and correct a real workout from
  the substituted program with nonzero prescribed sets, valid increments,
  proper history and subsequent workout progression. Include multiweek,
  strength, hypertrophy and adapted custom-cycle fixtures.

## UI gate

A read-only catalog picker may be prototyped, but active Save must not be
connected until the re-prescription contract, canonical runtime tests,
Compose compilation and actual device journey pass. Show actionable
refusals for unsupported changes instead of silently reusing the previous
exercise's prescription or zeroing the workout.
