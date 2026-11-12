# Pursuit Iron — early-launch beta contract

**Release intent:** a deliberately lean, reliable **several-month Android beta**
in which real lifters train, retain their data, report problems and help
validate coaching. Lean refers to the distribution footprint and optional
product UI, **not a reduced or partially ported training engine**. This is not
a production-store AAB milestone or permission to ship incomplete code.

## Engine parity before superiority (athlete direction, November 12, 2026)

**100% functional parity with the current web training engine is now an
explicit acceptance gate for the Kotlin engine.** The previous "not beta
blockers" clause must not be used to exclude web coaching functions in order
to launch faster. The *Android UI* may remain intentionally smaller, but no
supported plan/day/cycle may use an incomplete or weaker engine silently.

- Inventory and review every active web training capability and supported
  exercise/variant, equipment, loading, progression and volume behavior.
  Preserve the user outcome, not necessarily the JavaScript implementation.
  Custom and generated programs, all supported splits/goals/days, warmups,
  advanced techniques, fatigue toggles, Machine A/B, independently tracked
  days, coach approvals, deloads and immutable history remain in scope.
- Demonstrate parity through shared-input headless tests, native durable
  persistence, and actual Android workflow acceptance. A matching API name,
  passing source compilation, a mock UI or a single successful scenario is
  not enough. The native code must have one coherent Kotlin training policy;
  do not import JavaScript as a hidden runtime or accumulate patch layers.
- Resolve known web defects *more safely than web* (not by copying bad
  outputs), notably silent weight drops, unearned progression, invalid
  equipment increments, underdosed strength/peak phases and altered custom
  program settings. Record independent expectations and exact regression
  fixtures. Retain optional coaching-off behavior.
- Only after **all engine parity requirements pass** should the product
  be described as better. The separate engine-superiority gate then requires
  matched objective coaching results, independent human review, reliability
  proof and real-device acceptance. No release/100%/superiority claims until
  the recorded evidence supports them.
- The current catalog audit checks source-linked Kotlin starter definitions
  against the active web built-ins and reports missing identities. It is one
  necessary inventory check, **not** proof of equivalent exercise metadata,
  coaching quality or functional program parity.

### Release ordering

1. Complete and certify engine functional parity across generated/custom
   programs and longitudinal training; do not silently waive any web feature.
2. Prove improved decisions and no regression on actual paired cases,
   including individualized fatigue and all working-set loads.
3. Finish reliable lean Kotlin/Compose user journeys, encrypted export/restore,
   on-device acceptance and signed update tests; then permit an early beta.
   No frequent APKs or an AAB while these gates remain open.

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
   The Kotlin History screen now offers user-selected, owner-verified CSV
   export of completed generated/custom workouts, original targets, final
   corrected actuals, and physical machine/gym identity. That is a useful
   personal record but **not a restorable app backup**: it does not recover
   unfinished journals, program approval, equipment or app-private ownership.
   Tested complete recovery remains a separate beta release blocker.
   A versioned, password-encrypted, owner-fenced snapshot of the entire
   app-private Kotlin training directory is now implemented and tested
   against actual completed/corrected journal and History bytes; an
   isolated test-directory cold replay also succeeds. A second
   development-only recovery primitive now decrypts into private staging,
   verifies exact bytes before and after independent native-reader checks,
   refuses every existing live athlete directory, and atomically publishes
   a fully staged store on a genuinely empty install. Its CI tests cold
   reopen a real finished/corrected workout and verify interrupted staging,
   tampered input, foreign owners, and no-overwrite behavior.

   A third development-only, read-only staged core audit now shares the
   same checksummed local identity validation and native owner fence,
   reopens the current authoring source and machine roster, enumerates
   saved custom drafts, replays the complete mixed History/corrections,
   and inventories ALL generated and prepared/issued custom journals.
   Finished receipt IDs must agree with History; **unfinished journals
   are retained rather than treated as missing or completed**. CI verifies
   a real unfinished first-program workout survives encrypted export,
   clean-store atomic restore and cold reopen, and validates the completed
   program weeks after saved corrections.

   The staged audit now also inventories every physical per-workout
   machine-choice record and saved coaching-assistance policy, refusing
   foreign, orphan, renamed, pending, checksum-broken or mismatched-gym
   records. A restored Machine A choice remains tied to its issued workout
   and original gym; a saved coaching opt-out cannot silently reset to on.
   These checks are read-only and execute under the same Kotlin owner fence.

   The same staged owner-fenced audit now also reopens every pinned
   exercise note through the normal checksummed v1/v2 note decoder and
   enumerates every physical note file, including those for retired
   exercises. Damaged, foreign, renamed, unrecognized, or pending notes
   reject the restore instead of silently dropping seat/grip/attachment
   and older saved setup fields. Native CI cold-restores current and
   retired-exercise notes alongside Machine A and coaching opt-outs.

   A further owner-fenced audit now also inventories every physical
   adaptive-cycle permission, preserving both explicit granted change
   categories and revoked records from retired cycles. Corrupt, foreign,
   renamed or unresolved permission files refuse restored-store validation;
   the app never interprets a lost consent file as permission to
   automatically change a program. CI cold-restores both grants and
   revocations with the issued workout, notes, Machine A and coaching
   opt-outs unchanged.

   **No user-facing backup or restore exists yet.** This core audit is
   not the final production verifier: orphan/foreign files outside the
   covered inventories, future block activation approvals and signoffs,
   remaining private records and all supported storage versions still
   need full coverage. A future production restore must
   run before constructing an active app host, require explicit user
   consent, and pass rollback/power-interruption tests on real Android
   devices plus uninstall/reinstall acceptance before the beta gate opens.

## Not beta blockers / do not ship as misleading unfinished UI

Optional web UI breadth (such as social/sharing/leaderboards, elaborate
visual analytics, developer demos, benchmark panels and prototype-only
screens) can stay outside the lean beta. **This excludes neither web-engine
functional parity nor any supported progression, split, cycle, exercise
identity, volume constraint, gym/loading rule, custom-program authoring,
phase transition or recovery behavior.** Unvalidated probabilistic coaching
claims must remain disabled; scientific unknowns must be made explicit.
A feature can enter beta only after its **complete** flow is verified.
Do not make broken controls look like supported features.

**Important:** correctly prescribed strength/peak sessions and realistic
progression are fundamental to any *enabled* cycle, not optional polish.
Do not advertise or issue a split/phase if its weekly frequency, recovery,
time or set-dose constraints fail.

## One engine and smallest safe runtime

- Android beta **runs one Kotlin engine** for program creation, cycle,
  prescription, progression, fatigue options and workout decisions. The
  current `:engine-runtime` JS bridge and any alternative active policy are
  **development migration tools only**, not release dependencies. Android
  `MainActivity` launches the Kotlin-owned journey; the debug Lab reuses
  the same Activity. The `:app` runtime dependency on `:engine-runtime`
  has been removed. The AGP 9 `StageKotlinBetaSources` task stages only
  reviewed Kotlin journey and shared UI sources into a generated app source
  directory; historical JavaScript preview sources stay in the repository
  for audits but are excluded from Android application compilation.
  Kotlin/debug/release source compilation has passed in CI; that is NOT
  evidence of device acceptance, safe old-data import, or beta readiness.
  `verifyEarlyBetaRuntime` keeps distributable packaging blocked until the
  runtime graph is JS-free AND the currently absent
  `verification/BETA_DEVICE_ACCEPTANCE.md` on-device evidence is reviewed.
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

**Current blockers:** the Kotlin-only application source graph now builds,
but release packaging is intentionally locked. Full-cycle and next-session
issuance still need broader acceptance, on-device workout/restart testing
is pending, and private export/restore and explicit old-data migration have
not passed their beta gates. Verify the final signed artifact contains no
legacy JavaScript runtime before admitting external testers. The old
comprehensive parity ledger is a **post-beta backlog**, not a commitment
to ship everything at beta launch.
