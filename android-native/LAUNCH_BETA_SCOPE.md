# Pursuit Iron — early-launch beta contract

**Release intent:** a deliberately small, reliable **several-month Android beta** in
which real lifters train, retain their data, report problems and help validate
coaching. This is **not** the eventual full product, web-feature parity release,
a production-store AAB milestone, or permission to ship incomplete code.

## Product included in the beta

1. **Onboard and edit** a lifter profile, goals, days/week, gym equipment,
   verified weight increments, and a supported split. Start from a real
   reviewed Kotlin-generated program, and permit human-edited plans through
   the **same** Kotlin feasibility/prescription rules. Only enable splits,
   phases and cycle transitions with passing end-to-end acceptance.
2. **Train, save and recover:** clear daily session, logged warmups/working
   sets, weights/reps/RIR, Last vs Target and exercise history, rest timer,
   confirm Skip/Finish, safe resume after process death, workout completion
   and cold-reopen history. Allow verified machine A/B where available,
   isolated histories, gym defaults and a safe swap without lending old loads.
   Preserve already completed sets during restrictions and edits.
3. **Progress clearly:** accurate training history and usable Strength
   Standards, plus conservative review-only recommendations based on
   measured workouts, equipment, day-specific exposure and reported/assumed
   effort. Athletes can disable suggestions or optional fatigue coaching.
   Any future prescription change requires explicit review and approval.
4. **Hear from testers:** an easy in-app **Send beta feedback / Report a
   problem** route with an optional user-authored description and app version,
   not fabricated support addresses or silent collection of medical/training
   details. Bugs/crashes must be reproducible with clear build and workflow
   versions. Add voluntary diagnostics only with appropriate notice/consent.
5. **Keep months of history:** verified private save, safe upgrades and
   recovery; a usable export/restore or other tested recovery mechanism is
   required before inviting external testers. No silent data reset, double
   completion or sign-in account mixing.

## Not beta blockers / do not ship as misleading unfinished UI

Full web parity; social/sharing/leaderboards; comprehensive visual
analytics; AI conversation and unvalidated fatigue modeling; all possible
equipment, attachment and amplifier variations; every advanced split and
adaptive phase; elaborate charts; developer demos, benchmark panels,
exploratory settings and prototype-only interactions. A feature can enter
beta only after its **complete** flow is verified; otherwise hide/remove its
release entry point and put it on the post-beta backlog. Do not make broken
controls look like supported features.

**Important:** correctly prescribed strength/peak sessions and realistic
progression are fundamental to any *enabled* cycle, not optional polish.
Do not advertise or issue a split/phase if its weekly frequency, recovery,
time or set-dose constraints fail.

## One engine and smallest safe runtime

- Android beta **runs one Kotlin engine** for program creation, cycle,
  prescription, progression, fatigue options and workout decisions. The
  current `:engine-runtime` JS bridge and any alternative active policy are
  **development migration tools only**, not release dependencies. Android
  `MainActivity` now launches the Kotlin-owned journey, and the debug Lab
  reuses that same Activity. The release compiler uses a Kotlin-only source
  allowlist, and `:engine-runtime` is a **debug-only** dependency; its
  classes are excluded from beta release. CI verifies the actual release
  compiled classes and dependency graph **without packaging an app**.
  Release packaging remains blocked by a separate explicit beta-acceptance
  gate until testing, data recovery and stable-signed updates are approved.
- Keep separate Kotlin modules only where they express a real responsibility
  (engine, signed storage, Compose), not a new generator/coaching policy per
  screen, exercise, phase or edge case. One verified physical-stock rule.
- Remove unreachable candidate/demo UI, unused runtime adapters and
  duplicated legacy JS before the beta build. Historical record **readers**
  and versioned receipt replay are allowed **only** where needed to keep
  existing athlete data reproducible and migratable; don't expose V1 as the
  default for new offers.
- Tests, science fixtures, comparison reports and source-only verification
  belong in the **development repository/CI**, not in the installed beta's
  runtime. Remove debug-only logging and hardcoded test athletes/weights from
  distributable code. No built-in background surveillance.

## Beta release acceptance — currently NOT met

A beta build is allowed only after:
- One complete Kotlin-controlled path: onboarding → confirmed gym and
  program → workout → journal/interrupt/restart → Finish → persistent
  history → correct next workout and explicit progression acceptance.
- All enabled splits and phases pass the goal-specific scientific and
  weekly constraint matrix; custom plans obey the same policy.
- Manual coaching-off, missing RIR, fatigue context, cross-day isolation,
  Machine A/B stock and separate history, sensible rep/weight increments,
  bodyweight, injury note and swapped workouts pass end-to-end tests.
- App-only feedback is discoverable; private data can survive several
  beta updates, including tested export/recovery. Accessibility (large text,
  TalkBack), offline, Android lifecycle and actual phone use pass.
- A reproducible, stable-signed beta artifact is built from the final
  reviewed source. Do not resume APK/AAB generation merely to satisfy CI.
  No current test, source build, or passing parity gate constitutes device
  acceptance.
- Provide a short beta changelog, known limitations, issue triage route and
  opt-in tester feedback. Improvements over the web engine must be measured
  against actual lifter outcomes; otherwise report accuracy as unproven.

**Current blockers:** Kotlin beta source/classpath separation is in place,
but full-cycle issuance and supported-plan science still need acceptance,
physical-device testing and signing are pending, and feedback/export plus
explicit recovery of any prior athlete data have not passed beta gates.
Release artifacts remain deliberately disabled. The old comprehensive parity ledger is a **post-beta backlog**,
not a commitment to ship everything at beta launch.
