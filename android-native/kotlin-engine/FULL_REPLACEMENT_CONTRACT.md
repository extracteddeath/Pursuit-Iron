# Pursuit Iron — complete JavaScript replacement contract

**Decision: the rebuilt Jetpack Compose + Kotlin Android app is the long-term
successor to the JavaScript/web product, not a permanent native shell around the
JavaScript training engine.** This is a release requirement, not an optional
architectural direction. The new engine must be **better than** the legacy
coaching engine; reproducing its known bugs is not considered parity.

**Current status (2026-11-03): NOT CUT OVER.** The separate Kotlin engine is a
headless, non-issuing review laboratory. The current native preview still
depends on the canonical JavaScript engine runtime. Existing JS-based training
must remain functional on its current branch while an independently tested
replacement is developed. This contract does not authorize publishing,
decommissioning the website, modifying another developer's native branch, or
migrating real user data.

## Target release architecture

- **Training engine: Kotlin-only.** Program generation, equipment and exercise
  selection, scheduling, sets/reps/RIR/rest, individual progression, fatigue,
  recovery, deloads, adaptive phases/cycles, gym restrictions, workout
  adjustment, and next-session decisions must run in Kotlin on Android. No
  JavaScript engine, WebView, JS sandbox, remote web computation, or opaque
  legacy adapter may remain on the production workout path.
- **UI: native Compose.** All 14 feature capabilities and the native UX
  acceptance scenarios must work as real Android interactions, not placeholders
  or embedded web layouts. The original themes/colors are retained and
  implemented with coherent Material 3 Expressive tokens and motion.
- **Data: native, durable, versioned.** Use explicit schemas for athlete, gyms,
  equipment, custom programs, cycles, immutable issued sessions, actual set
  receipts, corrections, history, PRs, progress, backups, and preferences.
  The native owner handles idempotent writes, recovery and revision conflicts;
  Kotlin coaching policies must not own storage or write observed history.
- **No JS as hidden fallback after cutover.** Failure to issue a safe Kotlin
  workout is a transparent, recoverable native error, not a silent fallback to
  a different coaching engine. A rollback requires a deliberate versioned
  application/data recovery procedure, never replaying two engines against
  the same active session.

## Replacement scope — no feature left in JavaScript

The [14 capability ledger](ENGINE_BASELINE_COVERAGE.md) and
[required screen/UX ledger](NATIVE_ENGINE_UX_IMPACT.md) are the minimum
functional coverage. Replacements include onboarding and gym profiles;
Full Body/UL/PPL/ULPPL, three to six training days; hypertrophy, strength and
powerbuilding; individually verified equipment, attachments and load
increments; 40–60/60–90/120+ session preferences and optional hard limit;
MEV/MAV/MRV with **separate** biceps/brachialis and wrist flexion/extension;
custom programs and first-class edits; adaptive hypertrophy -> strength ->
peak/cycle frequency, exercise and split changes; progression, fatigue,
evidence-based RIR, safe rep/load recommendations and rest; supersets,
warmups, myo/drop amplifiers; readiness, restrictions, deloads, injury and
short-session adjustments; workout preview, complete logger, interruptions,
focus, corrections and finish; Last, progression, charts, PRs and bodyweight;
profiles, all 20 themes and accessible responsive UI; historical migration,
backup, restore, conflict handling, undo and recovery.

A JS-only feature is a **cutover blocker** unless it has been explicitly
removed from the successor product's requirements with the user's approval;
it cannot simply be declared out of scope to reach launch.

## Implementation sequence and ownership gates

1. **Build an independent coach, not a line-for-line translation.** Use the
   legacy JS engine only to discover feature coverage, historic schemas,
   user-visible semantics and regression cases. Specify better decisions
   independently and test known failures (unearned all-set 215 lb increases,
   invalid rep jumps, phantom 38.5/159.5 increments, lost custom amplifiers,
   huge phase-volume collapse).
2. **Finish Kotlin domain coverage.** Assemble versioned athlete/profile,
   physical inventory, exercise library, programming, working-set, fatigue,
   progression, adjustments, cycles and long-term history models with
   deterministic multiweek and adverse-input tests.
3. **Replace the app's issuance and journal pipeline.** Introduce a native
   `KotlinCoachPort` and a native-owned durable session/receipt layer. The
   old JS bridge may be used only for isolated comparison/testing before the
   final switch, never to secretly prescribe alongside Kotlin.
4. **Migrate and reconcile data.** Implement inspect -> stage -> validate ->
   preview diff -> explicit user approval -> atomic import; preserve stable
   exercise identity or flag a mapping for review. Keep archived source bytes
   and tested rollback points. Never overwrite historical actuals or replace
   issued targets on an in-progress JS session. Resume it under its original
   immutable rules **before** switching, or offer a safe explicit closeout.
5. **Compare quality on complete end-to-end scenarios.** Exercise/plan
   compatibility, whole-week dose, time, strength frequency, two-session DP,
   near-failure and missed sessions, phase transitions, substitutions,
   equipment precision, corrections, cold launch, process death, disk-full
   recovery, accessibility and full UI consistency must pass on device.
   Compare *outcomes and invariants*, not raw JS output parity.
6. **Explicit product cutover and runtime removal.** Only once every prior
   gate passes, make Kotlin the sole Android training authority, delete the
   JS workout engine dependency and its bundled JS assets from the production
   app, remove dead JS adapters, disallow fallback, and verify a cold-started
   offline release APK has no runnable JS training path.
7. **Product transition separate from Android code removal.** The legacy web
   product can stay accessible during development, controlled migration and
   rollback. Any later web hosting shutdown, redirect, data transfer or
   release publication is a separate explicit product decision.

## Mandatory objective launch evidence

A release candidate is **not ready** unless all statements below are proven:

- All 14 capabilities and E01–E41 requirements have documented and passing
  Kotlin-owned or native-owned implementation and real-device acceptance.
- New-user onboarding -> real plan -> full workout -> finish -> next
  session works offline; custom programs and backups also cold-restore.
- User's historic records, revisions, PRs, preferences and gym weights are
  preserved or explicitly reconciled; no silent data-loss or phantom workout.
- No native training decision depends on a JS runtime, WebView, JS network
  fallback or remote service. Final `app` Gradle dependencies exclude
  `:engine-runtime`. Packaged artifacts have no executable legacy training
  JS asset. All used production engine APIs originate in the Kotlin engine.
- Before transition, current JS-issued in-progress workouts retain their
  original prescriptions and history; source revision conflicts fail closed.
- Full adversarial tests, paired coaching-quality evaluation, Android phone
  and foldable UX, accessibility, all themes, process-death recovery and
  user acceptance are passed. No unresolved critical engine bugs.
- A recorded release decision approves switching authority. Passing a
  headless unit test, compiling Compose or generating an APK does **not**
  individually satisfy this contract.

## Scope and branch isolation

`architecture/kotlin-engine-rebuild` is the isolated Kotlin design/test
track. `architecture/android-compose-foundation` is the native UI/integration
track. Both require an intentional integration plan; do not merge or modify
the other native developer's code or live web application as a side effect.
The existing JS engine is **temporary reference and migration input**—the
released Kotlin product cannot depend on it for training.
