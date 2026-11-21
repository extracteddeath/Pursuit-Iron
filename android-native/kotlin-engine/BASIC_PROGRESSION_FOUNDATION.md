# Basic Progression Foundation — Native Kotlin

This is an intentionally **single, pure numerical decision authority**, not a
second training engine or an auto-writer. See
`BasicProgressionPolicyDomain.kt` and its exhaustive JUnit matrix.

## Modes

- **Manual logging:** return the currently approved working weights without
  consulting history, fatigue, readiness, or equipment-stock increments.
  Athletes may log other actual weights, but the log is never silently reused
  as a new prescription.
- **Strict double progression:** after **one verified comparable completed
  session**, hold all straight working-set loads until every set meets the
  prescribed upper rep bound; then propose one *verified adjacent* increment
  for **all** sets, atomically. No lower-weight fallback.
- **Per-set dynamic progression:** after one comparable completed session,
  assess each working-set ordinal independently; only eligible sets earn their
  adjacent, physically verified increment. Other set loads repeat exactly.
  Mixed loads are supported in this mode; they are never leveled to the
  heaviest set.
- **Full coaching:** separate higher-level consumer of this baseline and the
  existing longitudinal evidence/recovery/periodization policies. It is **not**
  allowed to change baseline outputs in place; proposals for fatigue,
  recoveries, future program revisions, or new rep ranges must be separately
  explained, verified, and explicitly accepted.

## Shared contracts

1. The native storage owner authenticates the completed workout and approved
   future workout; names entered in a screen are not history provenance.
2. Every progression series is scoped to athlete, program, block, day,
   exercise, physical machine, weight unit, and exact load basis. Unknown or
   mismatched identities must not combine histories.
3. The current issued load must match **each corresponding** preceding
   completed set's issued *and* actual load. A manual deviation is retained
   as historical fact but **never silently changes next week's baseline**.
4. Warmups, amplifier follow-ups, backoffs, bodyweight and assisted
   counterweights need explicit specialized decisions; this weighted
   straight-set foundation must not silently reinterpret them.
5. Numeric candidates go through the existing
   `KotlinFutureLoadNumberAuthorityDomain` and `EquipmentLoadDomain`.
   This module does not create another rounding rule, increment table, or
   writable future-set pathway. Missing inventory/precision/consent refuses
   progression, rather than guessing.
6. Decisions preserve immutable issued loads and logs. These results have no
   writer, no program grant, no journal permission, and no implicit consent.
7. Basic progression deliberately does **not** require RIR, readiness or
   fatigue information. An explicit 0 RIR is a valid logged actual, not a
   reason for a basic strategy to secretly change its rule.

## Integration gates before a user-visible switch

1. Have the authenticated native owner assemble the request from *last actual
   same-day/same-machine performance* and the immutable next prescription,
   excluding special working-set schemes.
2. Present its candidate and exact affected set ordinals **as a suggestion**,
   not as a changed next workout. Require explicit athlete acceptance and
   transactional revision checks before any future program change.
3. Route the coaching-only mode through the same physical-number validation
   and source fences, preserving its separate evidence rules and no-silent-drop
   contracts. Do not add parallel numeric writers.
4. Confirm set-count, machine, weekday and block changes either establish a
   new baseline or receive explicit athlete approval; no opportunistic mixing.
5. Run pure Kotlin unit tests, full native compilation, Compose source checks,
   owner/journal replay and end-to-end device tests. Do not ship an APK until
   the complete UI and agreed release gates are done.

## Compatibility

This addition is opt-in; existing coach call paths and frozen history receipts
remain unchanged pending the authenticated handoff. No changes to the web
engine, data schemas or app UI were made in this foundational milestone.
