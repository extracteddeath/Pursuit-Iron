# Pursuit Iron native app — final-design implementation contract

Status: **IN PROGRESS — not full-design complete**. Branch: `architecture/kotlin-engine-rebuild`.
The next distributable APK is intentionally **deferred** until every screen in
this contract reaches a reviewed, integrated state. Source-only compilation,
unit tests and screenshots during development are allowed; do **not** trigger
disposable APK milestones in routine design commits.

## Design direction / Material baseline

- Modern Material 3 Expressive with pinned Compose `material3:1.5.0-rc01`;
  prefer stable, accessible public APIs. Re-evaluate current official release
  notes before a final version pin and regression run, not after every commit.
- Preserve all original Pursuit Iron color schemes. First install **Amethyst**,
  never Lime. Theme is athlete-chosen and independent from workout/history facts.
- Own hierarchy through shared Compose components: semantic typography
  styles, single spacing scale (`PursuitSpace`), elevation/surface roles,
  standardized shapes, segmented selections, meaningful actions and consistent
  empty/loading/error states. No local ad-hoc CSS/inline colors.
- Use icon + text for discoverability until repeated use warrants icon-only.
  Core buttons require at least 48dp targets and screen-reader content labels.
- Animate state changes with Material motion and respect reduced-motion; no
  excessive bouncing, jumpy reflows, screen flashing or covering the set-entry
  action. Avoid experimental motion APIs without fallback and accessibility.
- Layout: compact phones use bottom navigation, medium/wide screens use an
  adaptive rail and two-pane History/Plan where genuinely helpful. Foldable
  and large-text layouts must never hide required completion controls.
- Preserve a **single recording authority**. UI components only show current
  native data or submit explicit, validated actions. No presentation-driven
  fabricated RIR, PR, load, effort, progressions, exercise substitutions or
  history migrations.

## Persistent information architecture

At most five top-level destinations:

1. **Plan** — next workout first, concise cycle phase, entire program outline,
   one-tap full program editor and safe undo/review of changes.
2. **Train** — resume/start, immersive active session and persistent rest timer.
3. **History** — factual completed sessions, corrections, calendar,
   exercise-specific history, source-backed PRs and export.
4. **Progress** — Strength Standards as a first-class feature, tracked lifts,
   longitudinal progress/consistency, favorites and share/export.
5. **Coach** — game plan and clear training decisions, coaching toggles,
   readiness/injury handling and genuinely reviewed next-session proposals.

Athlete Profile, gym/equipment, saved programs, appearance and settings belong
in a coherent secondary hierarchy; onboarding is a step-based flow, not another
permanent tab. Back returns to the previous level, not to setup unexpectedly.

## Screen hierarchy and required interactions

| Screen | Primary / secondary / tertiary order | Completion gate |
| --- | --- | --- |
| Home / Plan overview | Next workout + readiness → next plan summary → phase/week → full plan | No duplicated conflicting today card; 1-tap start/resume |
| Onboarding | Goal → schedule/split → equipment/load stock → review → create program | Save/Back survives rotation and preserves units/choices |
| Program / Cycle | Adaptive option → full week/day exercise list → per-set prescriptions → review/approve | Full Body, UL, PPL, ULPPL; 3–6 days; adaptive split and exercise toggles |
| Workout | Movement + set ordinal → **Target / Last** → entry weight, reps, RIR → **Complete** | Last is same day/slot and unreported RIR remains unknown |
| Workout controls | History, Info, Skip, Warm-up, Swap, Superset, Notes, timer | Open **full exercise history in one tap**, no lost unsubmitted values |
| History | Recent session → actual vs target → per-exercise ledger → corrections | Verified corrections, skips and failure are distinct; no implied PR |
| Progress | **Strength Standards** → lift-specific trajectory → training consistency | Bodyweight, comparison group, age context and source metadata explicit |
| Profile | Personal card / milestones → strength standards → favorite charts → preferences | Meets no fabricated badge or metric |
| Coach | Today's human-readable game plan → optional changes → review acceptance | Standard, Progression-only, Manual modes must remain independent |
| Gym | Grouped facilities → exact available weights/attachments → confirmation | Physical stock never substituted by a name-only equipment match |
| Settings | Appearance, assistance toggles, units/bar weights, privacy & backup | Original palettes and owned-history boundaries preserved |

## Target/Last and exercise history — acceptance contract

- One shared compact Material 3 segmented Target/Last control used by BOTH
  Kotlin logger implementations. It only affects presentation, never writes.
- Target: weight → rep range → target RIR. Last: exact latest **comparable**
  program day + same exercise slot/set type and ordinal, weight → observed
  reps → **observed** RIR. If the latest appearance was skipped/failed, show
  that outcome rather than cherry-picking an older success.
- The active exercise has a visible History action. It opens a sheet with
  **all correction-aware saved main working visits** in reverse session order,
  including skips/failures and targets. It does not navigate away or discard
  unfinished set input. Never use a locally cached partial history as authority.
- Keep set entry compact and accessible. Edited values survive Target/Last,
  Focus/All, minimize/resume, configuration changes and history sheet dismiss.
- Use full exercise history also from Progress, History and exercise details.

## Strength Standards — prominent Progress feature

Current source-only first pass implements four **exact barbell** comparisons:
`barbell_bench`, `back_squat`, `conventional_deadlift`,
`barbell_ohp`. A verified corrected native 1–10-rep working set yields
a plainly labeled **Epley estimated 1RM**, not a tested 1RM or certified PR.
Use the actual logged load **only**; machine, dumbbell, Smith and barbell
figures are not interchangeable.

Bodyweight and male/female reference selection are entered **explicitly** and
are not inferred from training history. Community ratio bands must cite the
published source, include link to methodology and distinguish broad bands from
age/bodyweight-specific percentiles. Source: Strength Level 2026 published
community ratio tables for bench, squat, deadlift and shoulder press:
https://strengthlevel.com/strength-standards

Before declaring Strength Standards feature-complete, add unit conversion,
athlete-configurable saved comparison settings with privacy controls, age- and
bodyweight-specific published lookups with verifiable license/provenance,
equipment/variant-specific standards, actual tested-1RM override and a fully
accessible chart/detail view. Do not fabricate ratios, rankings or citations
where valid source tables are not integrated.

## Reusable components and ownership

- Existing: `NativeDesignTokens.kt`, `NativeComponents.kt`,
  `NativeKotlinWorkoutReference.kt`.
- New shared layout: `PursuitDesignComponents.kt` for section headers,
  exercise title/history action, empty state and historical captions.
- New specific surfaces: `NativeKotlinExerciseHistorySheet.kt` and
  `NativeKotlinStrengthStandardsScreen.kt`.
- Next components to centralize across all 12+ destination/editor screens:
  app top bars, adaptive navigation, grouped choice controls, single-row
  workout actions, fields/steppers, confirmation sheets, feedback/toast,
  chart legend, coach rationale, responsive dual-pane information sections.
- Domain calculations belong in independently testable pure Kotlin when
  promoted beyond display-only values. No formulas duplicated across Plan,
  Train, History and Progress.

## Gate BEFORE the next APK

1. Design audit checks ALL screens, not only Plan or Train, in both theme
   modes at phone, 1.6–2.0 font scale and tablet/foldable widths.
2. Shared components used consistently, dark/light contrast verified,
   navigation and back stack tested, keyboard and touch targets correct.
3. Full custom/generated workout loop; history and strength-standard cross-
   links; edit/correction/restart; no visible UI regressions.
4. Source-only checks and Compose compilation pass; actual emulator screenshots
   reviewed. No feature is silently absent or implemented only as a static
   placeholder.
5. User has reviewed and accepted the finished design. **Then**, and only
   then, run one intentionally gated APK milestone for real-device feedback.
   An AAB remains out of scope until Play Store preparation.

## Status discipline

A passing Compose build means source compiles. It does **not** mean all UI
screens are finished, all features have parity, or the coach is more accurate
than the previous web engine. Report clearly what is implemented, what is
source-tested, what is visually verified, and what remains unverified.

## Continued implementation — Profile, Progress and whole-app hierarchy

This design-only continuation adds the following **source implementation**.
Successful Kotlin compilation or source-contract tests must not be confused
with visual acceptance on a device.

- Profile is now an explicit top-bar destination rather than a sixth crowded
  navigation tab. It has verified goal, experience, split, training-weekdays,
  equipment source totals, completed working sets and completed sessions, plus
  a private local display-name preference independent of athlete identity.
- Profile routes directly to Plan, Strength Standards, gym, coaching settings,
  and original theme choices; Settings Back returns to its actual origin.
- Shared `PursuitScreenContent` centers major screens at readable large-screen
  widths while retaining scrolling and the theme spacing scale. Onboarding,
  Plan, initial program, Coach, future-week review, gym and settings use it.
- Full exercise History is reachable from both custom and generated workout
  loggers; exercise search filters the owner-verified mixed timeline without
  hiding saved corrections or faking data. The sheet separates Working sets
  from All entries (warmups and amplifier follow-ups).
- Progress now has four verified barbell standards and a selectable per-lift
  **estimated 1RM trend chart**, with accessible text values. A single
  corrected owner-verified history snapshot feeds both the chart and standards.
  Display-only physical unit conversion uses the exact kilogram-per-pound
  definition; changing the bodyweight unit converts its entered value rather
  than silently relabeling it. No percentage, level, 1RM or award is persisted
  as an authoritative coaching fact.
- Primary copy no longer identifies the implementation as a Kotlin Lab in
  headers. Debug-only IDs remain behind advanced controls where necessary.
- Source CI now watches all `NativeKotlin*.kt` and shared design component
  files. It compiles Compose and tests the engine **without** generating an
  APK during normal design commits.

**Remaining before design approval:** real-home hierarchy and all planned
Coach UX; editable program/cycle affordances; complete adaptive pane
transitions and back navigation; full Profile personalization and milestones;
strength source-specific age/bodyweight tables with verified rights/provenance;
source-quality strength graphs and favorites; coherent exercise detail and
attachments; dark/light and dynamic font QA at multiple sizes; end-to-end
accessibility and device screenshots. None of these are silently marked done.

**Distribution remains gated:** Do not trigger the disposable APK milestone
for routine UI changes. The next APK requires the entire app-wide design to be
reviewed and approved.

## Progress continuation — pinned lifts and verified chart detail (2026-11-08)

**Implemented in Kotlin Lab source; Android visual/device acceptance remains open.**

- Progress now offers opt-in pinned lifts for exactly four source-matched
  barbell strength comparisons. Selections are persisted only in app-local
  preferences under a verified native athlete ID, never inferred from workouts,
  never merged into the coaching store, and never counted as achievements.
- Profile exposes direct shortcuts to each pinned lift's chart; selecting a
  shortcut opens that exact exercise in Progress. A regular Progress-tab visit
  remains independent of the pinned-lift handoff.
- Android system Back now returns from Profile and Settings to the originating
  destination and from a pinned Progress drilldown to Profile. Active Train
  journals retain their existing explicit minimize/recovery ownership.
- Comparison inputs now default to a collapsed, clearly labeled optional
  panel rather than consuming the first screen. Logged trends remain visible
  without entering bodyweight or reference-group data.
- A chart shows the most recent 20 qualifying finished sessions, sorted by
  actual completion order. It displays first/latest estimates, signed measured
  difference, chart values in readable text, and a direct action opening the
  complete correction-aware exercise history. One session is not shown as a
  fabricated trend.
- Only corrected, owner-verified, *completed main working* barbell sets with
  a valid 1–10-rep estimate are included. Warmups, amplifier follow-ups, skips,
  failed sets, issued targets, Smith or dumbbell sets and source-incompatible
  loads cannot become strength-chart achievements.
- Everything remains clearly labeled **estimated 1RM**. Broad external
  community thresholds are not age/bodyweight-specific percentiles. Pinning,
  reading charts or opening exercise history makes no changes to training.
- **Do not fabricate a weekly consistency calendar yet:** the verified mixed
  manifest/evidence currently provides authoritative completion *order*, but
  not a durable, authenticated completion date. Date-based grouping needs a
  versioned timestamp in both native history owners, corruption/recovery
  validation and timezone-safe presentation before any calendar or streak
  can claim to represent saved workouts.
- Added source-contract coverage for athlete-scoped preference keys, Profile
  deep links, owner-verified history, source-only chart data and complete
  history action. CI source compilation is required before declaring the
  revision green; source checks are not device screenshots or visual approval.

**Still open for the full design gate:** actual age/bodyweight-matched licensed
standards and provenance, tested 1RM entry distinguished from estimates,
variant-specific standards, accessible large-text chart QA on real devices,
customizable order of arbitrary favorite exercises (currently four supported
source-matched barbell lifts), Profile share cards, broader Progress dashboards,
and the remaining Home/Coach/program editing and navigation design passes.
No APK/AAB generation is authorized by this milestone.

## Plan and Coach hierarchy pass — 2026-11-09

**Source implementation only; visual and device acceptance remains open.**

- Program now uses the shared Material disclosure row for the read-only
  outline and exact full authoring editor. Expand/collapse is owned by
  Compose saved UI state, with an accessible expanded/collapsed state and
  56dp minimum tap target. The active editing actions continue calling the
  original native controller; the UI cannot directly rewrite prescriptions.
- The editor opens immediately after the user requests it; the read-only
  plan outline and optional calibration view are displayed in the separate
  overview mode, not placed ahead of the actual editor. Training block and
  weekday exercise lists open progressively rather than expanding every
  block and day by default. Existing exercise/set editing, move/duplicate,
  block/day creation, undo, source revision reviews and consent gates remain
  available when expanded.
- The Program editor summarizes genuine authored weeks, days and exercises.
  Developer cycle IDs/revisions are optional advanced details rather than
  primary athlete-facing content. These UI changes do not change engine
  calculations, history, workout issuance or signed consent.
- The Coach landing experience first identifies the *verified saved coaching
  policy* (Full Coach, progression-only or custom), then puts the live
  eligible-exercise review ahead of secondary Plan/Future destinations.
  Existing Manual/off mode remains independent of workout logging.
- An eligible movement exposes complete correction-aware exercise history
  directly in the Coach screen. A separate History destination works even
  when assistance is off. Prior or unapproved exercise focus is independently
  checked against the current approved program and gym.
- Review dismissal is *presentation-only* and clears pending unaccepted
  review inputs without writing a program. Accepting a proposal retains the
  existing revision-bound native confirmation and independent Train issuance.
  Internal accepted-grant IDs are behind an advanced disclosure.
- Source-contract assertions guard shared rows, actual edit wiring,
  disclosure order, source-verified eligible Coach exercise choices, history
  and navigation, and non-destructive proposal controls.

**Next validation:** complete source CI (including Compose compilation); then
manual phone/tablet/foldable visual sweeps at large font sizes, TalkBack
semantics, keyboard and focus handling, actual back navigation and a real
long-cycle editing session. A green compilation is not final design approval.

**Remaining high-impact design work:** responsive dual-pane Plan/editor,
fully guided adaptive split/exercise suggestions across blocks, improved
next-day coaching summary, richer arbitrary exercise chart favorites and
equipment/variant-specific Strength Standards with trustworthy source tables.
All of this is still subject to explicit athlete approval for saved training
changes; no APK or AAB should be created from this source-only milestone.

## Direct Plan editing and next-session focus — 2026-11-08 continuation

- The saved Program outline now has **Edit this training day** and **Edit exercise sets** actions. Both carry the exact authored block, weekday and stable exercise-slot ID into the existing native editor, rather than constructing parallel plans or changing prescriptions from the outline. The exercise editor retains its exact current draft, validated equipment choices, review and source-revision guards.
- Editor disclosures are controlled and saveable per current cycle: opening a day selects the right block/day, and a deep exercise edit opens its existing set sheet. Ordinary block/day expansion is still user-controlled. Changing view mode restores the shared scroll position to the top so an editor is not stranded under a previous long outline.
- The next approved custom session now shows a compact, *source-based* base-plan focus: phase and block, exercise count, working and warm-up set count, and up to four exercise names. It is rendered only for a verified Next or Prepared selection and explicitly distinguishes the base outline from exact week-specific loads and overrides. It makes no coaching estimates and never starts/issues a workout.
- Shared `PursuitScreenContent` accepts an optional owned Compose scroll state; all other screens retain the original default. Source contracts now guard correct deep-link targets, no invented session facts, precise selection authority and history-safe editor writes.
- Device visual acceptance (including 200% font, TalkBack, foldable and long-cycle navigation) and end-to-end workout quality evidence remain necessary. Do not interpret source CI as release approval. **No APK/AAB until the whole app design is finished and reviewed.**

## Athlete-controlled adaptive cycle rights — 2026-11-08 continuation

The native Program screen now exposes a **real, cycle-scoped adaptation permission editor**, using the signed-in `NativeKotlinCandidateHost` and
`NativeKotlinAdaptiveCyclePermissionFileStore`. Permissions are actual durable, revision-checked native state, **not decorative toggles**. The default is no rights granted.

- Separate controls for **Adapt training split**, **Adapt exercises**, **Adapt workout days** and **Adapt days per week**. Future block/phase choices and athlete-pinned exercise removal are independent rights. Advanced controls cover goal, attachments, supersets, sets, loads, reps, RIR, rest, amplifiers, cues and individual week overrides. Every displayed control maps directly to a `CustomCycleChangeKind`.
- Loading is asynchronous, owner-fenced and strictly read from disk. The existing receipt revision (or 0 for no grant) is required on Save; a conflicting grant is never overwritten silently.
- Permission selection alone never writes anything. The athlete must affirm a separate acknowledgment and choose Save. A successful native commit is **re-read from current owner-scoped storage**; a success message requires matching athlete/cycle, committed revision and exact permitted kinds.
- Revocation is a distinct real native operation, also re-read and verified after the revisioned commit. Revoking rights does not delete a custom cycle, existing signed workouts or saved history.
- This grants the coach permission to **propose reviewable changes only**. It is not an adaptive candidate producer, a split generator, automatic block conversion, current-session edit, signoff or workout issuer. Existing full-cycle audit, exercise source validity, future-schedule/prospective profile consent, permissions and final proposal acceptance all remain independent requirements.
- The Program view owns this navigation, and it refreshes the saved permission record when its verified cycle/athlete/source revision changes. A missing or corrupted permission record is **not** treated as previously approved.
- Source-only contract tests now assert the real host read/confirm/revoke calls, persisted record comparisons and independent split, exercises, schedule and day-count rights.

**Not yet implemented or certified:** a complete end-user builder that generates/ranks alternative future-cycle programs from the athlete's real equipment and constraints; full choice preview/diff acceptance UI; physical-device accessibility and multi-profile switch testing. Native generator and comparison domains exist, but their existence alone does not mean end-to-end alternative generation is finished or better than the web engine. No APK/AAB until full app-wide design and acceptance.
