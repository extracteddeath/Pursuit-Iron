# Pursuit Iron native app — final-design implementation contract

Status: **IN PROGRESS — not full-design complete**. Branch: `architecture/kotlin-engine-rebuild`.
The next distributable APK is intentionally **deferred** until every screen in
this contract reaches a reviewed, integrated state. Source-only compilation,
unit tests and screenshots during development are allowed; do **not** trigger
disposable APK milestones in routine design commits.

## Product cohesion and improvement — binding beta requirement, 2026-11-14

Every feature carried from the web app must be deliberately integrated and
improved as part of one product. Functional parity is the starting requirement.
Preserve supported capabilities while improving the athlete's actual task:
clarity, access, continuity, feedback, accessibility or reliability. Adding a
feature, matching its old screen, passing tests or increasing catalog coverage
does not by itself complete its UX. A shared pattern can be the improvement;
each feature does not need a new widget, menu, option or explanation.

For each carried feature, review these questions before marking it complete:

1. **Purpose:** What athlete task does it serve, and when is it useful? Give
   the essential action visual priority; reveal advanced controls when useful.
2. **Placement:** Give the feature one canonical home, with contextual shortcuts
   that open the same behavior. Explain a new destination before adding it.
   Avoid competing copies, unrelated overflow actions and dead-end screens.
3. **Interaction:** Use the same vocabulary, units, components and state rules
   across Plan, Train, History, Progress and Coach, including both workout
   loggers and Focus. Returning from a tool preserves the task and entered data.
4. **Complete flow:** Review first use, ordinary use, edit, cancel, Back,
   empty/loading/error states, retry and recovery. Distinguish a draft,
   suggestion, confirmed change and recorded fact through concise feedback.
5. **Evidence of improvement:** Compare a specific task with the actual web
   flow. Record the problem, deliberate change and observed result. Use real
   interaction counts/times when claiming speed; use reviewed screenshots and
   task walkthroughs for hierarchy, comprehension and accessibility. Leave
   unobserved outcomes explicitly unverified.

Use these existing homes rather than adding isolated feature screens:

| Athlete task | Canonical home | Contextual access and return |
| --- | --- | --- |
| Build, customize or adapt a program/cycle | Plan | Coach and post-workout proposals enter the same exact plan review |
| Perform and record a session | Train | Plan Start/Resume opens the same authenticated workout |
| Use Leveling | Train, beside set entry | One optional current-workout adjustment; logged actuals use ordinary progression |
| Swap an exercise or physical machine | Train for today; Plan for future programming | Same ranked selection and verified equipment rules; explicit change scope |
| Read Last, full exercise history or correct a result | History, with direct workout access | Shared correction-aware facts; return to the original exercise and unfinished input |
| Understand performance and Strength Standards | Progress | Profile/History shortcuts retain the selected lift and their return context |
| Review coaching and proposed adjustments | Coach | Plan/Train entry points retain the proposal's exact scope and consent rules |
| Change gym, profile or settings | Secondary navigation | Consistent shared Back and tab context; no circular return pointers |

Feature review must cover the whole affected journey and its neighboring
screens, not only the new control. Shared navigation, interaction and component
changes should fix every applicable entry point. Preserve differences that
have a real purpose, such as independent machine histories and distinct
current-workout versus future-program consent.

This is an acceptance requirement, **not a declaration that these flows have
passed visual or device review**. The APK gate below remains in force.

## Shared UI authority — binding cross-stage requirement, 2026-11-26

Apply the same consolidation discipline used in S2 to S3–S6 and S8.
One owner for each fact, one interaction implementation for the same athlete
task, one visual token for each semantic role. Prefer removing duplicate
behavior over adding wrappers. Do not start a parallel UI rewrite or invent
an abstract component framework. Extend an existing owner where it works;
keep source-specific differences at the narrow typed boundary.

| Shared surface or behavior | Existing starting owner (inspect before extending) | Required consumers and ownership |
| --- | --- | --- |
| Color, shape, spacing, typography, motion, scaffold and responsive action roles | NativeDesignTokens, PursuitDesignComponents, NativeComponents, existing Material theme | All screens, sheets, dialogs, compact/expanded layouts; no screen-local copies of semantic token values or competing component families. |
| Navigation, selection, Back/return and Start/Resume | NativeKotlinNavigationState, existing Plan workspace and host | Today, Plan, Train, Profile, Settings, More; one active workout identity and one route history. Contextual shortcuts enter the canonical destination and retain state. |
| Workout overview and current prescription | Existing native plan presentation and owner-fenced prepared/issued session | Today, Plan, preview and Train render the same reviewed source revision; draft, approved, prepared, issued and completed are explicit states. Do not generate a plan in a composable or build separate estimates of its sets, loads, reps, RIR or duration. |
| Set editor, stepper, correction and completion actions | NativeSetValueFields, NativeSetEditSheet, PursuitSetEntryActions in NativeComponents; existing history observation editor | Generated, authored, manual, Focus, full workout and History correction share typed editor behavior, units, field validation and accessible labels. User-entered draft remains separate from Target and Last; an explicit record action reaches the existing journal owner. |
| Exercise heading, focus navigation and timer | PursuitExerciseHeading, PursuitWorkoutFocusHeader, NativeKotlinWorkoutFocusNavigation, NativeKotlinWorkoutRestTimer | Same exercise/slot/visit identity, saved draft and timer across both loggers and compact/wide layouts; main set vs follow-up remains typed, not a separate copy of rest arithmetic or completion rules. |
| Target/Last, prior RIR, progression reason and plate diagram | NativeKotlinWorkoutReference, existing native read-only projections and physical number authority | Plan, Train, Focus, History and coaching previews reuse verified source facts and formatting. Missing actual stays missing; visible estimate is not a target, and displayed weight is not re-rounded. |
| History, PR, Strength Standards and Progress | Existing authenticated corrected timeline/facts, strength measurement domain, History panels | One factual revision, exercise and station identity and correction-aware result. Share projection within a read; invalidate on owner/gym/revision change. No screen-specific PR/strength/load calculation or stale global cache. |
| Future edits, swaps, notes, gym and machine selection | Existing host/controllers, reviewed draft/consent and equipment authorities | Context-specific entry points use the same underlying action, ranking and validation. Distinguish today's actual, current workout change, and future-program change. Preserve independent station/day histories, per-set amplifiers and intentional progression differences. |

### S6 shared-surface acceptance procedure

1. Inventory each existing consumer, component, state holder, numeric source and
   mutation command; identify concrete duplicates and divergences before changing
   code. Record the canonical owner and intentional exceptions. Reuse currently
   working implementations rather than duplicating their appearance.
2. Consolidate one complete athlete flow at a time: shared typed state projection,
   one component or interaction owner, narrow callbacks to engine/store authority.
   No separate draft state for Focus versus normal Train; screen-local ephemeral
   presentation (scroll, expanded/collapsed) is allowed when it does not own a fact.
   Keep recomposition work bounded; avoid duplicate full-history reads or expensive
   projections on ordinary field edits. Never cache across an unverified revision.
3. Test paired consumers of every changed shared behavior: same target/actual,
   units, unknown RIR, exercise and station identity, consent, edited draft,
   cancellation and cold recovery. Add an actual behavioral Compose/JVM test
   where possible, with source contract as supplemental protection only.
4. Review phone, tablet/foldable, large text, both themes, screen-reader labels,
   Back and keyboard, animation interruption, loading/error and retry. Keep
   Material 3 Expressive and 48dp actions; no duplicate surface hierarchy,
   inconsistent rounded corners or repeated competing controls. Compare real
   tasks with the web UI; do not claim speed/quality without evidence.
5. A shared change is incomplete until all applicable consumers are wired and
   checked. Record the affected S6A–D and S8 acceptance IDs and exceptions in
   the existing execution ledger, not a new parallel denominator. Do not
   certify design/device completion from code inspection. Stage 2 exit remains
   prerequisite for S3; complete UI source integration and owner authorization
   remain prerequisites for any APK.

Existing source review found both generated and custom loggers already use
NativeSetValueFields, NativeSetEditSheet, PursuitSetEntryActions,
PursuitWorkoutSetReference, PursuitWorkoutFocusHeader and NativeKotlinWorkoutRestTimer;
History observation editor also uses NativeSetValueFields. These are reusable
owners, not a reason to introduce another component library. This is an
implementation/acceptance requirement, not a claim every screen is shared or
finished.

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

## Shared navigation checkpoint — 2026-11-14

The source now uses one saveable route history for Profile, Settings, gym and
contextual Coach/Progress links. Reopening an existing secondary route returns
to that route rather than creating a circular Back path. Both phone navigation
and the rail derive selection from the same task context. A shared, accessible
Back arrow replaces separate Profile/Settings controls. Exercise requests and their saved UI state stay
with their route when returning through nested screens; a deliberate ordinary
tab visit opens its overview. Popped detail caches are discarded while ordinary
tab and workout draft keys remain stable. Active journal recovery remains independently
owned. App JVM tests execute these navigation scenarios in source CI alongside
Compose compilation. Physical navigation, layout and accessibility acceptance
remain open; this checkpoint does not finish the whole-app design gate.

## Plan task continuity — 2026-11-14

Returning to Plan now reopens the verified selected program before considering
the program census. A routine visit to Profile, Settings, gym or History no
longer replaces a selected second program with a picker or unconditionally
erases its last saved edit's Undo. Current program/source records are reread
together under the native owner fence. Coaching rules, assistance settings,
consent and next-session projections are freshly checked; pending reviews are
not reused as approvals. A changed program, source or owner invalidates Undo.

Read requests wait behind an in-flight edit without enqueuing another save.
An explicit program selection or newly generated program handoff takes priority
over a passive return. Post-save refresh is bound to the still-selected
program; a late acknowledgment cannot take over another program or swallow
its pending return. Cold entry still asks when several programs exist;
neither a prior in-memory Undo nor a preferred program is fabricated.

The actual web baseline keeps `programSaveUndo` in `modules/App.js` and checks
saved/draft/working content in `canUndoProgramSave` in
`modules/training-domain/analytics.js`. This native pass restores that task
continuity and binds retained Undo to exact native ownership, full source
context and canonical builder receipts. Tests use real native files for
create/edit/return/Undo, including advanced sets, notes, week overrides,
source changes, corruption and owner changes. Source checks and JVM tests are
not a physical navigation or visual sign-off. No APK/AAB from this checkpoint.

## Saved-program discovery — 2026-11-14

Programs are accessible directly below the Plan heading. The saved-program
chooser and cold Plan entry share one searchable list, current-program
semantics and Material destination row. Real goal, authored weeks, day range
and block counts replace internal revision numbers in normal browsing.
Search covers the complete verified census before paging; Show more keeps
every record reachable. Empty, no-match, loading and failed reads have
separate recovery actions. Cancel remains available while a read is pending.

The actual web `ProgramSwitcherSheet` in `modules/App.js` separates closing
from picking and searches saved names. Native browsing now preserves the open
editor, feedback and Undo. Choosing rereads the selected program under its
listed athlete's ownership; a changed record opens its latest version with
notice, while a deleted or unverifiable selection keeps the current Plan.
A cancelled or superseded read cannot publish a late selection. Opening is
read-only and does not activate a program or issue a workout. Source contracts
and real native-file JVM tests cover this behavior; phone, large-font, TalkBack
and visual/motion acceptance remain open. No APK/AAB from this checkpoint.

## Verified kettlebell setup and physical units — 2026-11-15

The existing onboarding weights section reveals optional single-bell and
matching-pair fields through Add kettlebells. A matching-pair entry states the
mass of **each** bell and explicitly confirms two matching bells; selecting a
single bell does not populate it. Changing units clears both provisional
lists and requires renewed gym confirmation. The Gym editor uses the same
reviewed equipment identities and discrete exact weights, with no stack or
plate shortcuts. Switching physical equipment clears the previous inputs.

One shared physical-unit presentation now serves initial calibration, Target/
Last, workout Info, coaching review and recorded equipment facts. Both a
two-handed swing and a matched-pair row display **per kettlebell**, without
doubling the entered mass or reusing dumbbell loading. Gorilla-row repetitions
are per arm in the reviewed definition. The actual web catalog offers
kettlebells with dumbbell equipment alternatives; native keeps explicit
bell-specific stock and calibration rather than treating those inventories
as interchangeable. Program/cycle and native-file task tests verify this
bounded behavior; no measured task-speed, visual, motion or device improvement
is asserted. Full app-wide design approval remains open.

## Shared workout display preferences — 2026-11-15

Settings now owns a Workout display section: the shared Target/Last selector,
Keep screen on (on by default), and the starting layout. Automatic starts
compact windows in Focus and windows of at least 600dp in All sets. Explicit
Focus/All sets choices override that default. Each active workout retains its
saveable current view and unsent fields through tools, minimize and recreation;
changing a starting default does not replace an existing workout's review view.
Header Focus/All sets taps also save the preference for the next workout.

Both generated and authored loggers observe the same process-owned display
source. Target/Last keeps its previous preference file/key and is updated only
by deliberate selection; mounting a screen no longer writes a stale local mode
back through an effect. Preference listeners keep the two routes and Settings
consistent. Screen-awake requests honor the switch only during active recording
and restore the View's previous flag on disposal/Finish. No training authority,
source approval, rest deadline, target or actual set is changed.

The prior native source hardcoded both starting views to Focus and unconditionally
requested screen awake. The actual web WorkoutSession initializes targetMode with
local useState; native already retained Target/Last and preserves that behavior
while removing screen-owned writeback. Pure policy tests cover the 599/600dp
boundary, explicit overrides, all 12 choice combinations, existing Last and
wrong-type/future fields. Source checks require the shared owner and recording
boundaries. Device rotation, background, TalkBack, touch and actual foldable
visual acceptance remain open; this does not finish the full UI gate.

## Explicit muscle-scope upgrades and wall-supported bodyweight work — 2026-11-15

The ordinary setup / first-program flow offers the named Serratus Push-Up and
Tibialis Raise identities. Tibialis Raise uses the reviewed unweighted,
wall-supported convention; Wall support is a named physical-fixture choice in
onboarding and Gym. Fixture selection never creates an external weight grant.
Bodyweight set entry and history keep their no-external-load convention; no 0
weight, RIR observation or unrelated calf/ab result is invented.

New policies track 22 groups, with separate serratus anterior and tibialis
anterior targets. Existing custom-policy editors retain the exact 16-, 18- or
20-group scope. Add current muscle targets is explicit and exposes missing
rows for complete athlete-entered values; it never fills zeros or changes an
old approval on opening. Source guards preserve the 20-row restore marker and
select the new scope only through that explicit action. Normal preview, review,
save, Undo, issuance, logging, correction, Finish and History owners are reused.

The actual web metadata groups serratus under core and tibialis under calves
while retaining their subregion identifiers. Native uses distinct declared
ledgers and leaves existing exercise credits intact. Real program/cycle and
native-file task tests verify this bounded change, including old-store byte
identity and regenerated old programs. This is not a measured task-speed,
physiological, visual or device improvement verdict; full UI approval remains
open. No APK/AAB.

## Persistent information architecture

### Authored superset execution

Plan's expanded day editor provides **Create superset** and a named **Edit
superset** action per group. The sheet selects two or three existing working
movements, edits the complete membership atomically, or unlinks the whole
group. It cannot steal another group's member. Cancel preserves the saved
program and Undo; failed writes preserve the sheet's choices. A successful
save closes only after the exact durable membership/revision is observed.
Program Undo restores the prior grouping and every original set/override.

The editor saves primitive owner/athlete/program/source coordinates across
recreation. Controller writes recheck that same owner, source revision,
program identity and revision. Changed setup/programs require reopening;
no old sheet is silently applied to a new program. Supersets disabled in the
reviewed setup block new membership, while an existing group can be unlinked.
The full program requires renewed review after saving. An issued workout
retains its original grouping, targets and records.

The custom workout's Up next cursor, Previous/Next Focus review and rest
projection share one pure Kotlin execution policy. A valid authored group
has two or three movements. Warm-ups stay together for each member before
the working rounds; working sets then alternate in source slot order.
Unequal counts finish the remaining sets solo. No issued slot, set key,
load, rep range, RIR, amplifier or receipt is rewritten by this navigation.

Focus shows the current round, ordered movement names and concise rest
guidance. All sets keeps the linked movement names visible. An amplifier
must be explicitly logged or skipped after its completed main, using its
own short pause before changing movement. Even out-of-order logging
returns to the most recently triggered pending follow-up. Round rest
starts only from a new completed visit that resolves the whole round,
using that closing visit's authored rest. A mid-round completion clears
the old countdown. Corrections, notes, skipped and failed observations
do not masquerade as new completed work. No final post-workout rest is
created. Fresh mounts keep the existing recovered-receipt timer baseline.

Native file tests cover issue, alternating main/amplifier logs, cold reopen,
exact retry and verified Finish-to-History with all actual RIR still unknown
when not recorded. This is connected source and integration evidence;
real phone/foldable, keyboard, TalkBack, timer and motion acceptance remain
open. In-session partner suggestions and generated pairing remain separate
unfinished parity work. The APK/AAB freeze remains in force.

### Reviewed partner ideas in Plan — 2026-11-15

Selecting one movement in the superset sheet reveals partner ideas immediately
below that selected movement, keeping the anchor in place. The complete manual
list remains available. Ideas use the current day's eligible reviewed catalog
accessories, distinct direct targets, supporting-target overlap, working-set
counts and current positions. Protected main/supporting lifts, unknown source
metadata, mismatched attachments, same-family/cross-target work and members of
other groups remain manual choices. These are provisional catalog comparisons,
not physiological interference, fatigue or time-saving predictions.

Each idea explains its set count and recorded target overlap. Show more exposes
the full ranked universe. Selecting one changes only the unsaved membership;
Save superset still performs the existing exact owner/source/revision check,
and a full review is required before future issuance. A stale anchor callback
cannot replace a newer selection. Coaching-off suppresses ideas without
removing manual grouping. Source/JVM and Compose evidence do not replace real
phone/foldable, large-font, TalkBack and motion acceptance. No APK/AAB.

History now groups completed workouts by their recorded Finish month in the
device time zone and labels undated training separately. It never derives an
old date from session order, a program week, a correction, a file timestamp or
the current day. Both active native Finish owners explicitly provide the
device clock; the observation is durably bound before the journal changes.
Retries/recovery reuse it, and set corrections retain it. A missing/invalid
clock can save an undated workout. The read-only calendar browser uses those
same verified records; visual, accessibility and device acceptance remain open.

Exercise search retains every matching session and exposes more exercise
drilldowns without the previous eight-result cutoff. Dates are displayed
through the same Material section/card hierarchy for both workout routes.
CSV keeps its original v1 shape for wholly undated history; exports containing
recorded times use v2 with an appended UTC column and blank older dates. No
private session notes, reconstructed dates or backup/restore claims are added.

### Recorded-date browsing

History prioritizes exercise search, date browsing and the saved session list.
Export and navigation shortcuts share a collapsed **History options** section.
**All workouts**, **Calendar** and **Undated** are explicit choices. Calendar
opens the latest recorded month; earlier/later controls and a paged month
chooser reach every recorded month. A day filters the same list; tapping it
again or choosing **Show whole month** restores that month. Search and dates
intersect without changing the complete exercise-history drilldown.

The calendar uses the locale's first weekday and the device time zone, counts
both native workout routes, and never turns missing dates into rest days or
streak gaps. Narrow panes or large fonts use full-width recorded-day choices
instead of squeezing seven touch targets below 48dp. Grid dates have complete
date/count accessibility labels and selected semantics. An empty match keeps
clear date/search recovery actions. Date selection survives recreation and
refresh, is scoped to the verified athlete, and is ignored if the saved month
no longer exists. This is a source/JVM milestone; real phone/foldable,
large-font, TalkBack and visual/motion acceptance are still required. No APK/AAB.

The verified bar flow uses one physical-stock parser in onboarding and Gym.
Athletes explicitly choose straight, EZ or trap/hex bar and enter its actual
mass and symmetric plate pairs. Switching bar type clears provisional inputs
and confirmation; a facility checkbox alone never invents load stock. Extra
bars can be added in Gym under their own implement identities. Calibration,
workout targets and plate views use that exact stock without borrowing another
bar's mass or exercise history. Kilograms and pounds remain explicit.

New program policy v10 exposes 20 separate muscle groups, including spinal
erectors and trapezius. Saved 16- and 18-group policies retain their exact
scope in the editor. Adding current targets is an explicit action and requires
complete athlete-reviewed values; blank new rows never become guessed zeroes
or a renewed approval. This is source/JVM evidence, not device or visual
acceptance, science certification or completion of the full UI.

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

**Remaining high-impact design work:** device acceptance of responsive Plan/editor,
fully guided adaptive split/exercise suggestions across blocks, improved
next-day coaching summary, richer arbitrary exercise chart favorites and
equipment/variant-specific Strength Standards with trustworthy source tables.
All of this is still subject to explicit athlete approval for saved training
changes; no APK or AAB should be created from this source-only milestone.

## Responsive full-program editor — 2026-11-15

Plan remains the single home for authoring a cycle. Its full editor now keeps
a separately scrolling block/day outline beside the selected day's exercises
when the available body width is at least 900dp and both panes can accommodate
the current text scale. Compact windows and enlarged text retain inline day
disclosures. This uses the actual content constraints after the navigation
rail, shared Material cards, selected destination rows, typography and spacing.

Both layouts render the same stateless day controls and call the same existing
controller actions for sets, swaps, supersets, reorder, add and edit. Program
Undo, review, calibration and exact-week preview remain in the same workspace.
The detail retains one keyed composition parent when the outline moves, keeping
policy draft fields and their saveable-state owner in place. Selection is scoped
to athlete/program, survives revisions and reordering, and resolves against the
current draft. A removed or ambiguous day shows an explicit choice prompt rather
than an editor for remembered exercises. Selecting a day brings its heading into
view; its title is a polite accessibility announcement, without moving focus to
an unrelated action.

**Source comparison:** web `ProgramView` in `modules/App.js` (verified main
`12e2bd37258c56255c687cc6abed78d1921f6c07`) uses an
`openDay` selection, expandable day cards and a day-jump menu calling
`scrollToDay`. The previous native editor nested every exercise beneath that
same scrolling block/day outline. The deliberate wide-layout change keeps the
outline available during day editing. Source checks verify shared control wiring,
independent scroll owners and a stable detail composition. JVM tests verify
selected-day resolution through real saved edits, cold reads and Undo without
changing loads, amplifiers, notes, week overrides or source files. These checks
do not measure faster editing, runtime state restoration or visual superiority.

**Acceptance still required:** resizing/folding with partially entered policy
and dialog fields; compact and wide light/dark layouts; 200% text; TalkBack
selection/heading feedback; keyboard and Back behavior; long-cycle editing,
empty/loading/error/retry states and actual save/Undo/review. No APK/AAB until
the entire app design is finished.

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
- The Program view owns this navigation, and it refreshes the saved permission record when its selected cycle, athlete or draft revision changes. A missing or corrupted permission record is **not** treated as previously approved.
- Source-only contract tests now assert the real host read/confirm/revoke calls, persisted record comparisons and independent split, exercises, schedule and day-count rights.

**Not yet implemented or certified:** a complete end-user builder that generates/ranks alternative future-cycle programs from the athlete's real equipment and constraints; full choice preview/diff acceptance UI; physical-device accessibility and multi-profile switch testing. Native generator and comparison domains exist, but their existence alone does not mean end-to-end alternative generation is finished or better than the web engine. No APK/AAB until full app-wide design and acceptance.

## Future training block comparison — 2026-11-08 continuation

- The Program overview now includes a real adjacent-block **Compare training blocks** panel when a cycle has at least two authored blocks. The athlete selects any later block, views its preceding block and can enter the exact target editor. The selected block's stable ID, not its label or ordinal, controls the preview and edit destination.
- This is backed by pure `CustomCycleBlockComparisonDomain` in the Kotlin engine. It reports observed differences in phase, split, training weekdays, week count, exercise visits, unique exercises, athlete-pinned slots, and **base working/warm-up set totals**. Exercise-exposure frequency changes are keyed by stable exercise IDs; added and removed weekdays are separate from changing the split name.
- The high-impact hypertrophy-to-strength example of **30 → 11 working sets** is displayed explicitly as a **−19-set authored base-week change**, not dismissed as harmless or automatically criticized using generic MEV/MAV/MRV thresholds. The feature does not imply an individualized optimal dose, readiness adjustment or sufficient/insufficient strength training.
- If and only if the source and cycle revisions match the current owner-reviewed Plan state, the independently audited full-cycle structural snapshot matches the exact saved draft, and every week of both blocks is present, the UI also shows **effective weekly working-set measurements including overrides**. These measurements stay labeled as unapproved when the full-cycle audit is unresolved. Stale, partial and source-mismatched audits cannot produce a comparison.
- A responsive two-column comparison at adequate width switches to stacked presentation on phones and large text. This follows the native theme and shared typography/spacing. No saved sets, coach permissions, approvals, current workouts or workout history are altered.
- The full-cycle audited view also shows direct-set credit differences by muscle in first-week comparisons, without combining biceps with brachialis or wrist flexors with wrist extensors. These are source-declared direct muscle credits, not measured hypertrophy or a universal safe range. The panel shows six changes initially, with an accessible expand-all option.
- Regression tests cover a base 30→11 set drop, actual week-override measurements, stale/partial audit rejection, unrelated absolute-week rejection, separately accounted arm/forearm direct-set credits, repeated-exercise exposure counting and lack of automatic change authority. Source navigation assertions verify Plan integration.

**Still required:** real generator inputs and multiple audited candidate future blocks; their independent full-cycle comparison, transparent option ranking, source/permission checking, actual version-fenced native approval and post-save re-audit, then phone/tablet accessibility acceptance. This new panel compares **already authored** adjacent blocks: it must not be described as generating a better future cycle. Native first-launch theme remains Amethyst. No APK or AAB before the full app design is complete.


## Verified exercise favorites across Progress and Profile — 2026-11-09

**Implemented in source; device screenshot and accessibility approval remain open.**

- The four published, source-matched barbell Strength Standards remain separate from **arbitrary exercise favorites**. A cable, machine, dumbbell or bodyweight movement cannot acquire a fabricated barbell tier or estimated-1RM chart by being favorited.
- Progress now lets the athlete search **only movement IDs actually present in the complete owner-verified mixed native workout history**, pin up to 12, reorder with separately named Move up / Move down actions, remove a pin and open the exact movement's complete correction-aware set history.
- The new lightweight favorite store keeps an explicit, ordered, athlete-scoped preference and validates IDs and the currently observed history allowlist on every write. It never stores calculated strength outcomes, issues workouts or modifies program and journal records. Broken/unavailable history exposes no arbitrary favorites.
- Profile shows the saved order of these actual exercise-history shortcuts alongside (but distinct from) the four existing source-matched strength-chart favorites. Navigation to arbitrary exercise histories performs **a second eligibility check** against the currently verified owner timeline; an old or foreign athlete's exercise ID cannot authorize a drilldown.
- The full correction-aware exercise-history sheet now also supports a real pin/unpin action, regardless of whether it was opened from Train, Coach, History or Progress. The native history is re-read before each favorite write, checked against the displayed athlete and manifest revision, and existing preferences are loaded before the pin action becomes available. Its row cache is scoped by athlete ID as well as revision.
- Recorded sessions are intentionally identified by authoritative completion order, not fabricated dates. This feature is a full-history shortcut, **not** a cross-variant strength trend or a replacement for age/bodyweight-matched licensed standards.

**Further work:** broader exercise progression charts with equipment/rep-range comparability and real phone/foldable/TalkBack verification; first-class customizable Profile sections and strength-standard source-specific reference tables. App-source compilation passing is not a claim of visual acceptance. The feature is isolated in Kotlin Lab and has no effect on the existing web app. No APK/AAB until the complete app-wide design is approved.


## Exercise-specific performance trends — 2026-11-09 continuation

**Source implemented; all 33 Kotlin/Compose CI steps passed on 2026-11-09 in run 42570247170. Real-device acceptance remains open.**

- Any exercise with verified, correction-aware completed native history may show a factual **working-set performance** trend from Progress without inventing a strength percentile, calculated 1RM, or target load. The previously published Bench/Squat/Deadlift/Overhead Press standards remain a completely separate comparison with their own source links.
- The history projection is one pure, read-only native-coach-store domain. It excludes unfinished, skipped, failed, warm-up and custom amplifier follow-up entries; uses actual performed set weights/reps, never the issued prescriptions as achieved performance; and uses the full mixed Composer + Custom history after the owner-verified read.
- Trend series are keyed by the exact stable movement ID, **physical load basis**, and fixed performed-rep band (1–5, 6–10, 11–15, 16–30, 31–100). The displayed heaviest completed main working weight per session is converted between verified lb/kg only when the logged physical unit matches the issued verified unit. No barbell total, Smith total, dumbbell-per-hand, cable, machine or exercise-override load is merged into another basis. Distinct load sources not represented by a stable movement ID still require an exercise-catalog identity review.
- Pure unweighted bodyweight observations can be shown in a **separate best-completed-reps** chart. Logged added/assisted weights are not treated as unweighted bodyweight.
- The chart shows the selected rep and equipment basis; a limited last-20-session plot; first, latest and raw difference; a readable per-session table; accessibility text for all plotted values; and a direct link to complete correction-aware set history. This is **not** an equal-RIR evaluation, trend-adjusted relative strength score, PR certification, or recommendation to increase weight.
- History search offers a direct **Trend: exercise** action without requiring a pin. The destination validates the movement again against the latest verified timeline, opens a focused chart rather than an obstructing history sheet, and Android Back returns to History. Profile favorite chart/history deep links continue to return to Profile.
- The feature uses existing theme colors and shared cards/metric strips/graph primitives; only an athlete-selected **View trend** opens the chart, keeping the default Progress hierarchy compact. The source contract checks exercise history eligibility and non-authoritative projection status.
- Dedicated JVM regression fixtures cover real lb/kg conversion, physical load isolation, exact rep windowing, best in each session, omission of warm-ups/failed/skip/amplifier sets, invalid/missing values, unweighted bodyweight, and custom history corrections.

**Outstanding:** hands-on screen-reader, narrow-width and 200%-font usability; scientifically valid longitudinal estimates that control for effort and fatigue; sourced demographic standards for other exercise modalities. No new APK or AAB until the full app design is complete.


## Strength Standards first-screen hierarchy — 2026-11-09 follow-up

The source-level Progress screen now prioritizes **four verified barbell summaries before settings**. A shared `NativeKotlinStrengthOverview` uses the same owner-verified `mostRepsAtLoad` projection, physical `BARBELL_TOTAL` basis, and canonical Epley 1–10-rep calculation as the detailed reference view. There is one shared best-per-exercise selection, not two independently calculated definitions of a PR. A missing qualifying set is explicitly labelled; never interpreted as zero strength, an unobserved milestone, or an inferred age/sex/bodyweight ranking.

Each barbell overview card selects its corresponding verified trend. Responsive layout is one column on compact widths or large font scales (>=1.35), two on normal phones, and four on wide screens. The selected state is exposed through accessibility semantics and the existing theme roles, not hardcoded accent colors. The published reference tier is only displayed after an explicit comparison group and valid, unit-normalized bodyweight are provided.

Community comparison is one collapsed Material disclosure with a saved/unsaved summary. Its optional Save/Clear actions use a new reusable responsive action-pair component, stacking at narrow widths or enlarged text. Detailed published reference thresholds and external methodology remain reachable in a **separate collapsed disclosure**, immediately after comparison and before unrelated exercise favorites. The general exercise trends and ordered favorites remain intact. All changes are to UI presentation; native history, journal transactions, profile authoring, coach permissions, progression and workout issuing are unchanged.

**Acceptance still required:** real-device normal/large text, talkback selected-control wording, phone landscape/tablet/foldable spacing, dark/light theme readability, and exact interaction review. The source navigation contract now asserts this hierarchy, shared widgets and the history provenance. The final source revision passed all 33 Kotlin/Compose CI workflow steps in GitHub Actions run 42572780090. This is compilation and source-test evidence, not a physical Android UX sign-off. No APK/AAB until the entire native app design is ready.

## Confirmed Smith loading — 2026-11-15

Smith stock is entered with the existing bar/plate task in onboarding and the
same physical-equipment editor in Gym. The athlete chooses an exact reviewed
station, confirms its empty-bar resistance including counterbalance, and enters
real plate pairs. Changing the station, stock kind or advanced load basis clears
the prior unsaved hardware inputs. No resistance or free-bar value is assumed.
An explicit zero is supported for a confirmed counterbalanced station.

Both issued workout routes place View plates beside the entered load, including
Focus mode and authored follow-up sets. They share one read-only Material sheet
and schematic, with Smith-specific resistance and accessible diagram labels.
Old totals-only Smith stock produces an honest unavailable result until its
physical details are confirmed in Gym. Cancelling or viewing leaves actuals,
targets and the approved program unchanged. Different stations remain separate.

This source checkpoint does not certify final layout, motion, accessibility,
phone/foldable usability or full UI completion. The APK/AAB freeze remains.

## Landmine inventory and shared load language — 2026-11-15

Onboarding offers **Add a landmine station** beside existing optional equipment.
Its field confirms the bar/pivot and lists attainable added-plate totals; zero
requires explicit entry. Changing units clears it with the rest of provisional
inventory. Gym retains its existing searchable physical-stock home, exact-list
input and source-fenced save. An opaque machine entry cannot shadow the station.

The shared label **landmine added plates** and weight suffix **added plates**
follow the same convention through calibration, both workout routes, Last,
Info, reviews and History. The free-bar plate viewer cannot invent a symmetric
layout or bar mass for this list. Current Leveling averages landmine zero rows
as unloaded-bar work and only offers unfinished row keys; acceptance changes
unsent input, followed by ordinary explicit set logging. It does not change
issued targets or future programs.

The actual web `barFor` route maps a landmine to the generic barbell plate
helper. This native flow instead makes the chosen added-plate convention
visible at entry and throughout the workout, and refuses a guessed symmetric
plate diagram. It requires explicit movement calibration rather than the web
starting-load module's generic 0.6 landmine factor. The source comparison is
recorded in the landmine loading review; no faster interaction or better force
prediction is claimed.

Source/JVM checks cover these bindings and native task flow. Real phone/tablet
entry, unit-change/cancel/retry, 200% text, TalkBack and workout-sheet return
remain unverified. Whole-app design is still unfinished; no APK/AAB.

## Exercise discovery and preference editing — 2026-11-16

**Purpose and placement:** Gym owns equipment and future exercise preferences.
Profile now describes its exercise library, and Gym places **Browse exercises**
ahead of the long inventory. The old plain catalog list is removed. No competing
primary tab or second training-selection authority is added.

**Task flow:** browse the exact saved source; search names, muscles, movement
metadata, equipment, fixtures or attachments; filter the complete result before
paging; inspect requirements and direct/supporting targets; open the shared
verified Exercise History sheet and return; stage Include/Exclude; Save or
Discard; optionally Undo the last confirmed preference edit; use **Review your
program** to enter the existing native Plan review. Eligibility comes from the
canonical native picker, never from search text or merely having a display label.
Pending inclusion does not claim a newly verified exercise before Save.

Search, filters, paging, detail and unsent choices have saveable UI state inside
the verified owner/athlete scope. The draft's original revision and exclusions
remain fixed until an explicit reload; source changes disable Save. Back/swipe
with changes requests discard, while in-flight writes block dismissal. Nested
History keeps the library's filter/edit task in place. The shared native edit
sheet now supports an optional stationary footer with scrollable content, so
Save/Close stay reachable for a long catalog and adapt through the shared action
pair. Other sheets keep their prior default body.

The actual web `LibraryView` at verified main
`12e2bd37258c56255c687cc6abed78d1921f6c07` exposes search,
muscle/equipment/flag filters and a detail ban control. The previous native
screen offered an unfiltered name list under the inventory. This continuation
integrates searchable discovery with actual gym eligibility and explicit
pending/save/discard/Undo boundaries. Shared labels, Material surfaces, radio
selection semantics, 48dp targets and the canonical History sheet avoid parallel
widget or historical-fact implementations. No measured speed or visual
superiority is claimed.

**Evidence:** real source files cover exclusion save/cold read/Undo, stale
revision refusal, preserved authored programs and zero writes during browsing.
A native issued workout continues recording and correction and reaches Finish /
History after an exclusion, while a separately prepared future workout cannot
use stale consent, including after preference Undo. Independent UI/source checks
guard actual save/History/Plan wiring and the owner-scoped draft and footer.

**Still required:** physical phone/tablet/foldable layouts, both themes, 200%
text, keyboard, TalkBack, Back/swipe, failed-save recovery and process-death
restoration with pending choices. Movement visuals, exercise goals/comparison,
custom exercise creation and full library parity remain separate work. Whole-app
UI acceptance is unfinished. No APK/AAB.


## Confirmed remaining-exercise skips — 2026-11-16

Both generated and custom Train flows offer **Skip remaining exercise** from
Skip confirmation in Focus and the exercise controls in All sets. A shared
Material confirmation lists remaining warm-ups, working sets and any already
unlocked amplifier follow-ups. It keeps the exact exercise (custom: authored
slot) and reviewed journal revision; a changed workout requires fresh review.
Previously completed, failed, skipped and corrected entries remain untouched,
and repeated copies of a custom movement and superset partners stay separate.

One Kotlin selection policy consumes the currently issued snapshot. The normal
owner-fenced writers append ordinary atomic SKIPPED receipts, with no new
persistence format, invented actuals, load edits or program changes. Partial
writes retain each committed receipt; recovery and a newly confirmed review
select only unresolved entries. Finish remains a separate history transaction.
Pure reducer and real native-file tests cover immutable targets, cold recovery,
stale revisions, identity refusal, repeated movements and conditional amplifier
entries. Source/CI verification does not establish device or accessibility
acceptance; full-design and beta release gates remain open. No APK/AAB.


## Warm-up task continuity — 2026-11-16

The existing Warm-up shortcut now filters the shared issued-exercise details
sheet to prescribed warm-ups rather than opening every working set. Both
workout routes show saved warm-up outcomes and offer a direct jump to the
next unlogged warm-up in Focus. This is navigation only: recorded results,
targets, selected physical machines, other set drafts and the rest timer keep
their existing owners. Closing or opening History restores normal Info scope.
Normal Info also shows main-entry outcome status and explicitly authored
amplifier follow-up work time. Custom working-set labels restart after warm-ups,
matching Focus and the logger while preserving immutable receipt ordinals;
six JVM presentation tests cover numbering, slots, physical units and timing. Missing work duration is shown as unknown.
This source change still requires Android compilation and physical-device,
large-font and TalkBack acceptance; the whole-app design gate remains open.


## Recorded loading conventions — 2026-11-16

Saved-workout details and full exercise History now show the saved physical
loading convention beside each target and comparable actual: per hand, per
kettlebell, landmine added plates, total barbell/Smith, or the recorded stack.
Both saved-set correction editors disclose their unit and convention. Use
Last uses the same actual-value formatter, while Info shares the target
formatter. Values remain exact; no dumbbell/bell doubling, added bar mass,
rounding or unit conversion occurs in presentation. Missing actual load/unit
stays explicitly unreported, and only an explicitly bodyweight prescription
can label an unweighted actual as Bodyweight. Six focused JVM cases and the
existing Info tests exercise these distinctions. History ownership, machine
isolation, correction receipts, issued targets and numeric inputs retain their
existing owners. Android compilation and device/accessibility acceptance
remain separate gates; no APK/AAB is produced by this source milestone.

## Responsive Plan exercise cards — 2026-11-16 (pending CI)

The same inline/two-pane selected-day editor now uses numbered Material
exercise cards with working/warm-up counts, pinned and week-override state,
and human-readable superset partners. Set editing and exercise swaps use
one existing responsive action pair; reorder buttons are always reachable
without horizontal scrolling and keep 48dp minimum touch height. At large
font scales buttons stack rather than compress or hide off-screen.
Superset creation/editing is grouped once at day level; Add exercise and
Edit day remain visible beneath the movements.

A pure, source-bound UI projection rejects stale, ambiguous and busy draft
coordinates and preserves week-override and linked-superset swap guards.
Independent JVM tests verify those states and that viewing action eligibility
never writes to stored programs. This changes no engine prescription,
written workout, approval or draft-store authority. Android CI and actual
phone/foldable/large-font/TalkBack review remain separate acceptance gates.
No APK or AAB.

## Native Train superset-round shortcut — 2026-11-16 (pending CI)

The custom Focus toolbar now has a real **Superset rounds** tool only
when the selected exercise is part of a valid, issued Kotlin superset.
The sheet displays original movement and set order, target reps and RIR,
recorded/completed/skipped/failed states, and an outstanding amplifier
follow-up. Tapping a verified set or follow-up returns to the exact
saveable Focus editor, without writing a set, amending a prescription,
changing physical machines or restarting rest. The same engine-owned
execution domain produces workout order and round-completion status;
malformed groups do not gain a fake superset control. The generated
workout toolbar keeps the optional action absent because no generated
linked group is represented in its issued snapshot.

Source tests and independent app JVM tests cover actual grouped sets,
missing follow-ups, failed/skipped outcomes and invalid group sizes.
This is not phone/foldable, 200% text or TalkBack acceptance. No APK/AAB.

## In-workout movement concern: safe note and exercise-skip handoff — 2026-11-16 (pending CI)

Both generated and custom native Train now connect **Report concern** to
a shared source-verified movement concern guide, rather than immediately
opening only the session note. The guide explains that a low-readiness
movement restriction affects optional coaching, not any issued target.
The athlete can choose **Write session note**, which opens the existing
explicit Save flow, or **Review skipping remaining exercise**, which opens
the existing separately confirmed, revision-fenced bulk skip workflow.
Already recorded actuals and past exercise history remain untouched.
For custom sessions the selection is scoped by immutable authored slot,
not by an exercise ID that might appear twice in the same workout.

An outdated/finished journal disables both actions; a no-selection guide
still permits a written note but cannot fabricate an exercise to skip.
The guide is not an injury diagnosis, does not persist a medical record,
and never silently reduces weight, logs a skipped set, edits a program,
or creates the next session. One pure Kotlin action projection plus
four JVM tests and exact-source navigation contracts cover revision
changes, preserved recorded work, absent selections and already-complete
exercises. Android source and phone accessibility acceptance remain
separate gates. No APK/AAB.

## Concern → verified replacement without hidden navigation — 2026-11-16 (pending CI)

The in-workout concern guide now provides **Review replacement exercises**
as an explicit third option alongside writing a session note or confirming
skips. It opens the **existing current-gym/owner-verified Kotlin swap
reviewer**, never chooses an alternative automatically or changes a signed
program. Both generated and custom workouts use the same strict
historical-record eligibility: any previously recorded set in the
exercise/slot, including an earlier corrected set, disables replacement.
Custom linked supersets cannot be swapped in-place; the athlete can
review explicit skips or change the program separately.

The guide displays original recorded/remaining counts and never offers
replacement if no exact current unresolved exercise exists. On selecting
replacement, the current journal revision and unstarted exercise
eligibility are checked **again** before the swap reviewer is opened.
The eventual replacement still has to pass its original independent
catalog, machine/gym, immutable-snapshot and consent gates. This is
navigation only: no automatic load reduction, logged set, medical
inference, or next-program mutation. App JVM cases and source contracts
assert stale, linked, corrected and unstarted boundaries. Real-device
large-font/TalkBack and full parity acceptance remain open. No APK/AAB.
