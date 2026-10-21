# Pursuit Iron interaction standard

Build 831 applies the approved compact workout prototype's interaction language to the app.
It is inspired by Material 3 Expressive; it uses the existing Pursuit Iron themes, typography and
layouts, with no new UI library or online dependency.

- Buttons compress and change corner shape on press, then settle with a spring. Keyboard and
  pointer activation share feedback. Scrolling, cancelled touches and disabled controls cannot
  leave a button pressed.
- Navigation tabs, segmented choices and switches also receive a short, theme-colored state layer
  centered on the finger (or the control for keyboard input). This is presentation-only, does
  not insert children into React controls, and is intentionally excluded from workout set rows.
  The animation is cancelled by reduced-motion preferences, and window blur releases a held press.
- Keyboard focus rings inherit each theme's foreground rather than fixed white, so light themes
  keep a visible focus indicator without adding persistent visual clutter for touch users.
- Selection is immediate. Segmented settings, Progress tabs, onboarding choices and RIR use
  their existing semantic state. RIR neighbors respond within a fixed group width.
- Root navigation uses a quiet transition; drill-down and exercise navigation retain direction.
  Transitions accept new input while moving. Existing Back history and scroll restoration own
  navigation state.
- Sheets share entrance, drag recovery and exit springs. Closing retains the mounted content until
  it settles, preserving scroll position. The existing focus stack, Escape and system Back remain
  authoritative. Reopening cancels the exit rather than allowing its old callback to dismiss it.
- Info sections and effort prompts animate their measured content height in both directions.
  Collapsing content becomes inert immediately. ResizeObserver handles text, keyboard and width changes.
- Target / Last transitions affect references only. Weight, reps, prescriptions, rest clocks and
  logged effort are never changed by motion. RIR commits immediately, confirms briefly and closes;
  the reported value stays directly below reps.
- Reduced motion applies at startup and when changed during a session. Backgrounding settles
  current animation and releases press feedback; it never pauses or adjusts workout clocks.

`modules/ui-motion.js` owns springs and shared DOM feedback. `MotionReveal` and `Exit` in
`modules/App.js` own mounted-content lifetimes. `app.css` grants the motion module ownership of
spatial properties only while its data attributes are present; legacy CSS remains a fallback.
Use native buttons with `aria-pressed`, `aria-selected`, `aria-current` or `aria-checked` for new
controls. Shared groups and screen containers participate automatically. Do not add a competing
transform transition to an owned element, reserve blank space for animation, or delay saving data.

The browser contract is `verification/b831-expressive-motion-browser-test.mjs` in the integration
shard. The existing logging gate still covers compact rows, typing, steppers, effort and Undo at
320–520px; the theme integration gate covers all twenty themes and the five main tabs at 320px.

## Layout and visual hierarchy (expressive continuation)

The Material-inspired interaction layer is also a compact visual system. It does not replace
Pursuit Iron's saved color themes or add another component library:

- Bottom navigation: icon-centered tonal selection pill, always-visible labels, existing 52px
  button targets, no new elevation stack or persistent motion.
- Home: one dominant next-workout hero, restrained tonal gradient, tighter space to the following
  actions and visually related action tiles. Do not create a second giant call-to-action.
- Program: next scheduled day gets a slim tonal cue; other days keep an even card cadence.
  Exercise rows, collapsed day details and program editing retain their existing semantics.
- Progress and exercise detail: selected subsection is a rounded segmented surface; the
  three-column summary stays legible at 320px. Data, history ordering and chart scaling are unchanged.
- Settings: jump control, row groups and selected options use consistent corner geometry and
  restrained dividers; row content, assistive labels and tap targets retain their heights.
- Workout: header/tool corners and exercise navigation join the design. **Do not style,
  expand or reorganize** `[data-testid^="set-"]`, `.wpb-set-controls`, logged RIR,
  stepper tracks, numeric fields, target/previous references or completed rows. Keep
  the muted bright-to-faded completion behavior from Builds 829–830.
- Sheets and popovers: the radius follows the page-level card family; viewport detents,
  drag handling, focus stack, keyboard avoidance, and Exit/Undo behavior remain authoritative.

Surface tones derive from the current theme via `currentColor` color mixing, not hardcoded
purple or light/dark overrides. New styles are contained in the named **PI EXPRESSIVE LAYOUT**
section of `app.css`, scoped to `#root[data-pi-motion="expressive"]` so the previous screen
geometry remains available if the shared motion module is not installed.

Verification adds layout assertions to the existing Build 831 integration browser test;
existing phone/theme, typography, keyboard, logging, and engine gates remain mandatory.

## Material 3 shared selection tracks

Progress's four subsection tabs and Settings's two-to-four-option segmented controls
use a single moving selected surface. The `modules/ui-motion.js` controller measures
each selected real button's `offsetLeft`, `offsetTop`, `offsetWidth`, and
`offsetHeight`, and springs the group's CSS variables to the new geometry; all text
and hit testing remain attached to the original React buttons.

- The moving surface reproduces the active theme's original **computed**, contrast-safe
  selected fill—not its raw accent. Lime, for example, can use a dark selected surface
  behind light text even though the separate accent is bright. A theme change resamples
  the original cascade before repainting the track; no hardcoded color is introduced.
- The pseudo-element sits behind the controls (`pointer-events:none`); no child,
  accessible-name change, navigation event, DOM restructuring, or row height change occurs.
- Resizes settle immediately to the updated responsive layout. Rapid second selections
  interrupt the same spring rather than stacking timers or blocking interaction.
- Reduced-motion preferences settle instantly. Unmounting disconnects the track observer.
- Progress is an actual keyboard tablist: Left/Right arrows select adjacent tabs (wrapping),
  Home/End select the first/last, and focus follows selection. This delegates a regular
  button click to React, so the tab content and moving indicator remain in sync. Settings
  aria-pressed toggle buttons are left independent rather than borrowing tab semantics.
- The workout's dense set controls, completed-set fade, steppers, RIR, prior/target loads,
  sheet gestures and progression code are excluded.

The integration contract checks the indicator's exact responsive position, width, theme
fill, absence of extra DOM children and native selected semantics at 320px and 430px.

## Material 3 overlay components and contextual actions

The app now treats actual (React-owned) sheets, dialogs, program action menus,
and configuration fields as a cohesive Material-inspired family without adopting
Google colors or adding another component framework:

- Sheets have a compact drag indicator and responsive large top corners.
  The native viewport height, drag gesture, escape/dismiss stack, focus lock,
  content scrolling, recovery and safe-area handling remain unchanged.
- Dialogs keep their existing actions, destructive warning colors and content
  arrangement, with consistent focus affordances and larger rounded surfaces.
- Anchored contextual menus retain their viewport flip logic and original
  Duplicate / Delete / Cycle actions, but use rounded 44px rows and quiet
  touch-origin press feedback. An additional calculated translate keeps the
  menu above both the live-workout dock and bottom navigation; it never steals
  the hit targets or buries the last (possibly destructive) menu action.
  The offset is recomputed after entrance animation and viewport resizes.
- Menu keyboard semantics: ArrowUp/ArrowDown wrap through native menuitems;
  Home/End focus extremes; Escape delegates to the existing dismiss layer and
  returns focus to the source button. No action fires during focus movement.
- Fields in Settings and configuration overlays receive outlined focus feedback.
  Workout set inputs, RIR, compact steppers, muted completion states, history
  reference buttons and working-session dimensions remain excluded.
- Reduced motion eliminates ripple/press animations but retains full controls.

The Build 831 phone browser contract checks actual gym-sheet geometry,
program-menu alignment, keyboard focus/dismissal, and unchanged program access
at 320px and 430px alongside existing engine and logging tests.

## M3 exercise-detail selection

Exercise-detail Overview/History/Notes-style tabs share the Progress/Settings
moving selected indicator, sampled from the saved theme including tonal gradient
fills. The real buttons, existing actions and DOM geometry are untouched.
Arrow navigation and Home/End keep focus with the selected tab. The phone test
installs a transient fixture through the same observer, checks the indicator,
and leaves the app's training state and compact workout logger unchanged.

## M3 secondary screen composition

The onboarding welcome, program builder, exercise library, and exercise swap list
share the existing Pursuit Iron theme's compact hierarchy. Welcome copy and
features have closer spacing and safe-area-aware actions. Wizard heading, step
progress, selected options and footer adopt coherent geometry without adding
questions, changing defaults, increasing card height or blocking fast taps.
The exercise library keeps its dense scrolling results and filters, but uses a
smooth measured selection surface for All/Recent/Goals/Banned, improved search
focus and accessible compact chips. Swap alternatives retain their exact actions
and ranking; only their surface shape/hover changes. The touch-origin ripple
now covers these semantic options (not set logging). Reduced-motion disables
transitions. Browser regression follows the *real* Create Program and Exercise
library routes at 320/430px to check footer visibility, filter animation,
search focus, width stability and unchanged saved prescriptions.

## M3 Progress history, charts and workout-adjacent feedback

Progress > Sessions now uses the shared theme-native moving selection for
cycle/month grouping; search/period filters have accessible focus indicators
and 44px touch height. Training-calendar disclosure receives a clear state
chevron and keeps native details semantics. Dense history ledger, sticky
group headers and detail actions retain their original content and actions.
Exercise chart range tabs share measured selection motion and keyboard
Home/End/Left/Right. Horizontally scrollable metric tabs remain genuinely
scrollable, with native selected states and keyboard focus (no misleading
indicator that could scroll outside the visible range). Chart SVG coordinate
systems, plotted values, point hit targets, tooltip semantics and date-axis
data are unchanged; chart presentation improves clipping/ink precision.
Rest-card actions and completion metrics receive shared geometry without
altering weight/reps steppers, active sets, or completed-set fade.
Phone integration tests verify real history grouping and range-tab keyboard
motion at 320px/430px, retained hit geometry and saved programs.

## M3 real Exercise Detail composition and verification

The Exercise Library detail destination now has a reliable 44px Back target,
compact readable full-screen header, contained exercise figure, evenly-sized
History/Charts/Records/About tabs and consistent exercise metadata.
History is a chronological ledger instead of redundant cards; rep-max
records remain tabular and numerical. Chart metric choices remain horizontally
scrollable and keyboard-focusable; SVG values and plotted points are not
recomputed or altered. At 320/430px the browser now opens a real Back Squat
detail, checks tab hit size and horizontal overflow, switches to Charts,
confirms program data integrity, and exports screenshots of both surfaces.
All changes are scoped to Library details; the live workout's set grid,
effort, target/previous, amplifier, rest persistence and safety controls
remain unchanged.

### Full-screen Exercise Detail transition guard

The Library drill-in is a fully opaque navigated screen, **not a dimming
scrim**. The shared spring motion must never set the opacity of the entire
Exercise Detail page below 1, because doing so exposes the previous Library
rows and header behind readable content. The phone browser now checks its
computed opacity immediately after entering the real exercise, and captures
History and Charts without intermediate keyboard-focus transitions.

## Long History Chart Inspection

For 37+ recorded data points, the chart keeps every original date/value
and the date-proportional line but replaces overlapping SVG circle hit areas
with one plot-wide slider-like touch/keyboard inspector. Touch-drag selects
the nearest real session; Home/End/Left/Right navigate the exact original
chronology. A visible marker and tooltip identify the inspected session,
with full dates, and multi-year ranges include years on both axis endpoints.
For shorter histories the original individual SVG point interactions stay
unchanged. The phone test seeds 80 sessions in an independent synthetic
history without changing saved program prescriptions and verifies the full
point count, aria state and keyboard navigation at 320px and 430px.

## Shared spatial navigation and page orchestration

Bottom navigation now uses the same measured, interruptible spring as
Progress/Settings/Exercise tab indicators. Instead of fading five static
pills, **one** paint-only rounded 30px tonal selection follows the real
selected button, derives its fill from the theme's computed foreground, and
resizes on layout changes. Native tab elements, icons, labels, pointer
targets, back behavior and persistence remain untouched. Rapid navigation
changes retarget the same spring instead of stacking animations.

On screen transitions, up to five top-level Home, Plan, Progress, Profile or
Settings cards enter with a brief staggered motion. This does not delay data
or interaction. It excludes all workout rows, set controls, sheets, and the
engine, and settles immediately for reduced-motion or forced-color users.
Phone regression follows actual tab navigation at 320px/430px to verify
indicator geometry, stored-program immutability and bounded page staging.

## M3 expressive disclosures and geometric continuity

Real Plan day cards now morph smoothly between compact (16px) and expanded
(21px) corner shapes, with quiet theme-derived depth and a brief content
entrance. The transition is intentionally *not* a delayed close animation:
React immediately owns mounted/unmounted content and any training state.
Card drag/reorder, day menu, start action, renaming, prescriptions, workout
sets, and every persisted field are unaffected. The visual controller now
reflects the actual open day body to the existing header button using
`aria-expanded` and a matching `aria-controls` ID.

The same disclosure observer covers expandable saved-cycle groups,
Profile achievements, native Settings diagnostics and Progress calendar
sections. It animates only entering content and available card surfaces,
never clones form controls, never intercepts native disclosure/touch
events, and stops animation for reduced motion/forced colors.
The phone regression opens and closes a real Plan day at 320px/430px
and verifies semantic state, morph geometry, minimum touch size and
unchanged saved programs.

## Expressive vs utilitarian motion boundaries

Material's expressive motions belong to navigation, sheets, menus, choices,
disclosures and major achievement moments—not every gym log control. Global
button compression now explicitly ignores the entire live `data-testid^="set-"`
row, compact set steppers/checkmarks and the Target/Last header switch. Their
own existing instant input, muted completed state, reference fade and
reported RIR semantics remain authoritative. The independent RIR choice-group
selection feedback still operates on its existing fixed width.

App Undo/status toasts keep their React-owned message, action, lifetime,
z-index and dock clearance; the presentation receives a more coherent tonal
border/corner and keyboard focus. Finished-workout summary numbers appear in
a short three-part stagger without modifying values or adding a count-up.
Reduced-motion users receive all numbers and actions immediately.

The integration browser now tests this boundary after actual weight editing,
Target/Last switching and completing a set at 320px and 430px.

## Exercise-detail visual hierarchy follow-up

Phone screenshot QA showed the generic YouTube form-video link occupied
disproportionate space above actual exercise history and charts. Within
**navigated Library Exercise Detail only**, the link now becomes a compact
horizontal information row: recognizable play affordance, readable title,
secondary destination text, full-width touch area, explicit keyboard focus.
It remains a real HTTPS link and does not preload external content.

No video source changes, training charts/data changes, or live-workout
form-link changes. The phone browser verifies link geometry and behavior
at 320px and 430px, and captures the real detail page for comparison.

## Expressive R2 — beyond animation overlays

The previous M3 pass standardized interactions but left Home's quick actions as
two similarly sized tiles with an orphaned third, gave every major surface
nearly the same corner shape and kept navigation's selection modest. This pass
recomposes existing elements rather than introducing more content or controls.

- **Home:** a sculpted Up Next hero and larger integrated Start action; two
  bento rows (featured Templates across full width, Custom Split + Cycles as
  complementary equal-width routes), still only two rows, no extra scrolling.
- **Plan:** next-day prominence plus spring-driven, asymmetrical card shapes
  on actual open/closed transitions; original session drag, start and menu
  behavior untouched.
- **Progress:** three existing metrics stay readable on a single row with
  differentiated first-stat proportion, and soft grouped containers.
- **Profile/Settings:** distinct instrument and list-group shape families.
- **Creation/Library:** more expressive selection shapes (not just color),
  differentiated decision cards, fully rounded filter chips and a brief
  motion sequence when a *new* wizard step mounts.
- **Navigation:** its existing, measured, interruptible selection track is
  wider (up to 60px) and taller (33px), with no change to five tab hitboxes
  or the live-workout dock.
- **Motion:** hero and choice presses respond with a longer spatial spring;
  option corners spring to an even more rounded selected state. No form
  cloning, data mutation or artificial delays. Reduced-motion accessibility
  remains mandatory.

Phone verification at 320px and 430px explicitly checks the bento layout,
navigation measured geometry and creation-option size; the existing full
workout, persistence, custom-program and all-theme checks remain in place.
This is an additional visible-design iteration, not a claim that every M3
Expressive idea is exhausted.

## Expressive R3 — fluid adaptive button groups

The first R2 layout did not yet reproduce M3 Expressive's characteristic
*stretch and yield* of adjacent selection buttons. Progress's four section
buttons and Library's four view modes now share that behavior: the selected
control springs to 1.20 of its relative share while neighbors soften to
0.95. Each button remains a real button with unchanged order, labels, focus
and hit height; no extra element or training state is introduced.

The already theme-aware selection track follows the **measured, animated**
button bounds rather than the old fixed layout. Follow-through is coalesced
to one frame per group and stops when the interrupted spring settles.
Other segmented controls, numeric workout controls and RIR choices remain
on their proven geometry (the RIR scale keeps its independent 1.18 growth).
When reduced motion is requested, spring values settle immediately.

Real browser checks at 320px and 430px verify selected width growth, matched
track geometry, Library overflow, keyboard tab selection and original
prescriptions.

## Material 3 Expressive R4 — connected screen composition

This pass deliberately changes how the app *feels as a whole* instead of just
reshaping individual cards. The palette derives from the user's active theme
focus token; existing accessible selected colors and button text are preserved.

- **Home:** real edge-to-edge tonal top bar with an oversized responsive
  headline, sharply differentiated asymmetrical workout hero, raised start
  action, and two compact, left-aligned tonal statistic tiles. Featured
  template doorway has stronger visual priority than its two complementary
  secondary routes; no feature is removed or duplicated.
- **Progress and Settings:** the page header, tabs and grouped content read as
  connected surfaces, with distinct corner/weight families. Actual chart,
  date, history and setting controls are unchanged.
- **Plan:** scroll-snapping week rail plus a more obvious theme-tinted Next
  day container. Existing drag/schedule/saved-cycle logic remains unchanged.
- **Adaptive app bars:** the existing `data-scrolled` marker collapses visual
  padding, size and corner radius on Home, Progress and Settings when the user
  scrolls; no extra scroll listeners, intercepts or sticky overlays.
- **Navigation choreography:** the real `data-view-frame` enters from the
  previous primary tab's direction, scales subtly to rest and coordinates a
  small number of important existing content surfaces. Interruption remains
  safe because it uses the shared velocity-aware spring; screen routing and
  training data stay owned by React.
- **Sheet choreography:** leading sheet corners spring from 46px to 29px
  while opening, return toward 42px on close, and remain distinct from
  the sheet's velocity-aware vertical drag/detent. Reduced-motion and forced
  colors remain quiet.
- **Regression protection:** 320px and 430px browser checks validate true
  direction-aware navigation, actual scroll-driven header collapse, tonal
  dashboard and dashboard grid geometry, theme-specific navigation and
  unchanged workout logger/persistence. No extra package dependencies.

Prior R2/R3 were code-level expressive layout and fluid controls; R4 now
connects page *composition and motion*. This does not imply Android-device
frame timing has been certified: a physical-device visual/gesture pass is
still valuable before merging/publishing to main.

## Material 3 Expressive R5 — creation, Library, Profile and contextual utilities

This branch extends the connected visual language beyond the primary tabs while
keeping the **workout set logger, live dock, Target/Previous, RIR, typed weight
fields, timers, program data and engine code unchanged**. The Profile identity
and strength statistics gain a clear feature-first bento hierarchy. Exercise
Library gets a tonal header, prominent responsive search instrument, and
individual tactile results rather than a flat list. Creation gets a larger
step headline, uninterrupted progress track, and selected options that truly
morph their shape. Menus, dialogs, exercise detail and Settings receive an
adapted version of the same shape and feedback language without modifying
positioning or adding controls. All colors use the active theme token.

320px/430px browser geometry assertions guard Profile, creation and Library
rendering. Reduced motion and forced-colors disable decorative transforms;
focus outlines and all existing actions remain intact. This PR is stacked on
R4 PR #63, separate from the competing Plan/Progress R5 PR #62, and must be
reconciled before any combined publication.

## R6 unified Material 3 Expressive integration

The final unified candidate keeps the adaptive shell, connected navigation,
Profile/Library/Wizard overhaul, and restores the actual training-rotation
ring and compact three-exercise preview from the parallel Home work. The
verified Plan current-week bento and Progress sessions-led dashboard also
enter this branch, as does the Plan panes' measured spring selection track.
The source of every number remains its existing program, schedule or history.
Engine and workout logging remain unchanged; all deferred branches are kept
separate until the integrated CI passes. No additional app features or
persistence keys are introduced.

## Expressive R5 — the screens that still looked unchanged

The Plan overview and Progress history were previously almost entirely uniform
rows and three tiny metric chips. This pass gives both a clear Material 3
Expressive size hierarchy while *keeping the same real data and controls*.

- **Plan overview:** current week is a featured 28px-corner tile, with its
  actual week number enlarged; Sessions and Week sets become smaller supporting
  tiles. Completion stays one unobstructed seven-pixel bar under these metrics.
  Up Next has a more prominent task-card hierarchy. The existing four Plan
  panes now participate in the shared, elastic selection system, with a
  measured theme-derived track that follows actual selected button widths.
  Each pane is still activated by its original button callback.
- **Progress:** Sessions (last seven days) occupies the larger left-hand
  visual field; Sets and Recent PRs are two right-hand tiles. Labels and
  date-window copy remain visible with no recalculation or graph mutations.
  The new grid is phone-scoped and remains readable at 320px.
- **Motion and semantics:** initial page entrances include Plan's prominent
  surfaces, but never any live workout rows. The fluent tabs retain their
  existing button labels and pressed semantics. Original history, graphs,
  logging, data exports and engine remain unchanged.

Release requires passing 320px/430px visual-geometry assertions, all phone
shards, core contracts, independent engine parity and PWA release integrity.

## Material 3 Expressive 2026 color correction (R7)

Use **solid tonal surfaces** sourced from the current Pursuit theme. A hierarchy of
surface-container-low, surface-container, surface-container-high, primary-container,
and outline-variant keeps Home/Plan/Progress/Library/Profile/creation distinct without
directional light effects. Decorative linear and radial gradients were removed
from app.css; the single retained radial state layer is transient touch feedback.

Size, shape, containment and spring-led interaction supply expression; gradients
do not supply hierarchy. Primary actions preserve the user's theme fill; selected
controls use the existing measured indicators and interruptible shape motion.
High-contrast users retain explicit borders and text; reduced-motion users retain
instant functional changes. No new UI package, download-only fonts, data model,
logger geometry, load editing behavior, engine logic or persistence path is used.

**Acceptance:** inspect 320px and 430px viewport layouts, light/dark themes, top
bars before/after scrolling, the Next-day card, the History/Chart/Library detail,
dialog/backdrop, and keyboard/focus. Phone regression explicitly guards against
decorative gradients returning. Physical Android gestures and frame timings still
require a device-level check.

## 2026 adoption R8 — proven list and menu patterns (October 10, 2026)

This pass deliberately borrows **behaviors**, not Google branding or gradients.
Sources: Google's 2026 Compose Material 3 release notes
(https://developer.android.com/jetpack/androidx/releases/compose-material3),
Expressive menu/group APIs (https://developer.android.com/reference/kotlin/androidx/compose/material3/MenuDefaults),
and observed Gmail/Drive list containment
(https://9to5google.com/2025/09/02/gmail-material-3-expressive-containers/).

- **Library:** content-sized list rows rather than clipped fixed-line labels. Each
  row keeps original navigation and filtering; first/last edges have a stronger
  enclosure, middle rows are quiet, and presses morph their own corners. Do not
  alter row ordering, scroll ownership, search or attachment information.
- **Settings:** a contiguous section container with shape-aware first/middle/last
  rows. Keyboard focus/press emphasize only the currently operated control.
  Avoid allocating extra height between items or increasing screen density.
- **Context menus:** bounded popup with 44px minimum item targets, clear outer
  corner grouping and lower-corner focused/pressed feedback. Preserve focus
  restoration, Arrow/Home/End navigation, dismissal, anchor flipping and dock
  clearance.
- **Progress history:** separate source-backed sessions with focus affordance,
  not synthetic metrics, extra action buttons or a larger section footprint.
- **Motion/accessibility:** existing R6 measured selection springs remain the
  sole interaction owner. Reduced motion disables decorative transitions; forced
  colors retain explicit boundaries. No gradient return, download-only assets
  or new persistence paths.

**Implementation boundary:** This is a paint/shape/typography pass only.
Engine, workout set inputs/steppers, timers, current/previous weights,
target prescriptions, RIR, amplifiers, completion, Undo, focus mode, offline
storage and generated/custom program behavior must remain unchanged.

**Acceptance:** 320px and 430px phone browser results, all four Action workflow
groups, release manifest and offline digest. Inspect on a physical Android
device for gesture latency before claiming native-like device performance.

## R9 — Source-connected navigation and fluid selection (October 2026)

Research inspiration: Android Material container-transform motion
(https://github.com/material-components/material-components-android/blob/master/docs/theming/Motion.md),
the October 7, 2026 Compose Material 3 release notes
(https://developer.android.com/jetpack/androidx/releases/compose-material3),
and the January 2026 Google Drive expressive search/tab grouping
(https://9to5google.com/2026/01/06/google-drive-m3-expressive-redesign-complete/).

- A valid tap on a top-level destination or Home/Library entry records the
  **actual source bounding box** before the React click handler navigates.
  When a different route renders, its first real content surface travels a
  short, clamped distance from that origin. No duplicate DOM, crossfade image
  or replacement router is added; content is selectable immediately.
- Only the first non-workout content card receives the source-linked entrance.
  Remaining first-screen elements keep their existing five-item bounded
  stagger. Sources expire quickly and are not used to animate stale routes.
  Workouts, steppers, numeric fields, RIR and rest bars are excluded.
- Measured selector indicators briefly compress horizontally and morph their
  corner radius while moving; the button hit boxes and native tab roles remain
  unchanged. The existing interruptible spring remains the sole owner of track
  geometry, including rapid back/forward taps.
- The R8 post-merge phone integration's transient 430px disclosure failure
  is addressed by waiting for the measured accordion content to settle before
  checking the **same unchanged** 44px hit-target and >40px panel conditions.
  We do not lower the accessibility acceptance threshold.
- Respect reduced motion and forced colors; no decorative gradients, no
  new UI dependencies, persistence migrations or engine changes.

**Acceptance:** all four pre-merge workflows, 320/430px browser geometry,
route link origins, selector rest state, workout drafts and generated/custom
programs. Post-merge Pages deployment and all four verification workflows
must be rechecked independently.

## R10 — Dark Expressive tonal emphasis and action priority

The real Android Home screenshot on October 10, 2026 shows two identical,
oversized vivid accent fills (Start Workout and persistent Create Program)
against a nearly-black canvas. In a dark M3 Expressive interface that defeats
the intended visual hierarchy: a user can no longer tell the next task from
the optional action.

- Keep the **Start Workout** CTA filled, but give the dark color scheme a
  deeper theme-native accent container with contrast-safe light foreground.
  The user's accent hue remains recognizable, without a neon full-width slab.
- The existing **Create Program** control is now visually secondary: a
  quiet near-surface fill plus subtle outline and legible foreground. Preserve
  its button, text, click handler, hit area, dock location, and feature parity.
- Use the existing light-dark() CSS color-scheme resolution so users
  explicitly selecting a light theme are not forced into Android OS dark style.
- Neutralize lower-level dark container fills; reduce the previously
  over-tinted Hero without changing card size, day indicator, exercises
  disclosure, workout length, program labels, or motion.
- Add only a shared spring-controlled press/shape response and ephemeral
  touch ink to Create Program, no competing animation implementation.
- The phone regression checks the real Home Start and Create buttons for
  44px+ hit size, WCAG-level text contrast in dark mode, one primary versus
  one quieter secondary fill, and absence of decorative gradients.
- Keep workout steppers, completed sets, Target/Previous toggles,
  rest intervals and RIR exactly as they were. Preserve forced-colors,
  reduced-motion, light themes, and offline PWA behavior.

This is an app-wide **color role principle**, not a blanket reduction of
accent saturation: focused primary actions retain emphasis, while support
containers and secondary actions should not compete.

## R11 — Consistent dark-theme hierarchy beyond Home (October 2026)

The user-approved R10 correction removed two competing saturated Home calls
to action. R11 extends the same design rule to the rest of the *actual*
secondary surfaces: Plan, Progress, Profile, and Exercise Library.

- **Plan:** the current week and next training day remain distinct themed
  primary containers; companion facts, week selectors, and day lists receive
  quiet neutral dark surfaces. The next session has a narrow accent edge,
  not a full bright background. Keep 4-pane selection and day disclosures.
- **Progress:** first measured metric retains prominence through size and a
  restrained theme container, while the other metrics and session history
  are neutral. Do not alter charts, PR values, time ranges, or persistence.
- **Profile:** strength summary uses the same spotlight role; identity,
  achievements and subsidiary cells are neutral, grouped by existing shapes.
- **Library:** search has a clearer raised input role; filters, header and
  exercise results are neutral until a row is selected, pressed or focused.
  Preserve data, keyboard flow, actual touch targets and filtering.
- **Theme correctness:** dark containers derive from boot bg/fg and the
  theme's own primary role, never hard-coded purple/pink. Light-theme roles
  remain unchanged. High-contrast mode receives explicit Canvas borders.
- **Motion:** R9 origin-linked navigation and moving segment indicators
  remain the only spatial motion owners. This is color-role polish, not a
  separate animation framework.
- **Acceptance:** test real rendered Plan/Progress/Profile/Library containers
  at 320px and 430px, and run all 20-theme, offline, core contract and engine
  gates. Existing workout set row, RIR, Target/Last and Create Program controls
  must retain identical behavior.

Physical Android screenshots and gesture/frame performance require a separate
device review; browser CI is necessary but not a device certification.

## R12 — Library-to-detail continuity and design-token consolidation

The same shared motion controller used by top-level R9 navigation now records
the exact tapped Exercise Library row before React opens its existing detail
overlay. The detail title and exercise figure inherit a bounded origin offset
(maximum 22px horizontally and 15px vertically), with a short decelerated
content entrance. No DOM element is cloned, no route or exercise data is
changed, and no loading state is introduced.

The detail **backdrop stays fully opaque from its first rendered frame**.
Back remains in its original 44px target and is immediately usable. The
source is cleared after one use, and a row tap cannot leak into the next
top-level tab transition. Stale source positions expire automatically.
The user can toggle reduced motion while the detail is open without losing
readability or turning the overlay transparent.

Maintenance: R7 first declared the app's three surface-container color
roles; R10 re-declared those exact same variables at the end of the CSS.
Those redundant overrides are now folded into their authoritative R7
definitions, preserving identical light/dark values while reducing cascade
ambiguity. R11 role overrides remain deliberately screen-specific.

Acceptance: verify a real filtered exercise row and its actual detail
overlay at 320px and 430px; check bounded source offsets, first-frame
opacity, chart tabs, content navigation, reduced-motion, and Back target.
Run the full 20-theme browser, release integrity, offline, engine and
custom-program tests. No changes to the strength engine, logging controls,
program construction, persistence, rest timer or RIR.

## R33 — Keyboard-consistent top navigation and calm quick-switch arrivals

- The five native bottom navigation buttons now accept ArrowLeft/ArrowRight with wraparound,
  plus Home/End to jump to the first/last destination. Focus follows the selected
  button and React performs the actual route update; indicators are paint-only.
- Rapid consecutive destination changes keep the directional page transition,
  but suppress the first-card origin translation as well as staggered card reveals.
  This prevents repeated content jumps from the bottom bar on fast taps.
- The browser contract checks actual keyboard activation, semantic route state,
  one mounted main view, focus retention and zero program mutations at 320/430px.
- Reduced motion, workout logging, set layout, timers, sheet gestures, library
  contents, training engine and offline support are unchanged.

## R34 / R35 — adaptive composition and contrast audit

- R34: primary tab labels can wrap at narrow widths instead of clipping or
  shrinking hit targets; safe-area left/right padding follows the device. The
  Profile strength figures and Progress/Home cards adapt at 320–359px without
  rearranging data or changing the two-column strength comparison.
- R34: on low-height landscape devices, decorative page-header spacing
  compresses without reducing tap targets or changing fixed workout controls.
- R35: selection is not conveyed by a faint color wash alone. The actual
  spring-driven bottom-tab pill has a high-contrast, theme-derived perimeter,
  while retaining subdued filled surfaces in dark mode.
- R35: primary action and bottom navigation remain contrast-tested against
  their rendered backgrounds; native keyboard focus is visible on primary,
  creation, and onboarding actions in both light and dark schemes.
- Acceptance: the existing M219 phone suite now probes all saved themes, all
  five tabs, the actual selected tab label (4.5:1), the meaningful tab
  indicator perimeter (3:1), and the filled primary action (4.5:1). This
  does not imply that every untested element in the app has been certified.
  Full release integrity, phone/browser, independent engine and offline gates
  remain required. Inactive controls have the standard WCAG exemptions.
- No changes to exercise selection, set values, RIR, Target/Last, rest timing,
  program history, or engine algorithms.

R35 follow-up: an opaque, theme-neutral perimeter supplies reliable selection
contrast. The shared UI motion module only corrects filled-action text if its
computed solid background and foreground fall below 4.5:1; it preserves the
saved theme's own on-color otherwise. This explicitly repairs the Lime primary
action pairing found by the 20-theme regression without hardcoding a Lime-only
color, adding an online dependency, or touching workout logs.

## R37 — Standalone Android targets and chart focus clarity

Android's accessibility guidance recommends 48dp targets for interactive
controls and at least 3:1 contrast for non-text indicators. The previous
Expressive surface pass retained a few 44px standalone targets; this pass
expands only those that can grow without stealing space from exercise
weight/reps entry:
- Sheet close actions and Gym Done have at least 48px dimensions.
- Full-screen exercise detail Back has an explicit 48px square hit area,
  without enlarging its icon glyph or title.
- Workout tool actions, rest controls, and Progress history actions
  have 48px minimum targets; rest actions already wrap at 320px.
- **Do not resize** the compact logger steppers, set completion row, Target/
  Previous controls, RIR selectors, live exercise navigation, or graph points.
- The keyboard-controlled SVG history-chart scrubber retains its
  accessible slider semantics, Home/End/Arrow keys and pan-y touch scrolling,
  but renders a clearly visible 2.5px full-opacity dashed focus stroke.
  Windows forced-colors/high-contrast uses Highlight instead.
- Standalone icon buttons use a currentColor focus ring rather than an
  accent that may have insufficient contrast in a light theme.

The existing b831 mobile-browser contract checks both 320px and 430px chart
keyboard focus, bounds, input/RIR persistence, rest tools, sheet close actions
and Back. M219 additionally checks real Light-theme Gym Done and workout
toolbar hit geometry. No training state or storage is changed.

## R38 — Screenshot-driven navigation and tonal-home evolution

The October 11 Android capture exposed a crowded selected bottom-navigation
pill (the outlined capsule visually touches the "Home" label) and mixed
maroon/plum/gray surfaces across the header, Next Workout and supporting
score card. This is a *design-system* fix, not a change to exercise data.

- Bottom tabs retain their five native buttons, stateful routes, touch
  feedback, keyboard navigation and interruptible selected indicator. Each
  button has a 56px icon-plus-label stack, 22px icon, and an explicit 9px
  label offset. The track is a measured 27px icon-only pill, 6px above
  the label in its resting geometry. Neither pill nor icon may collide with
  text at Android 320px/430px widths.
- The moving pill uses an opaque, theme-derived tonal fill instead of a
  translucent, nearly-empty capsule. Label and non-text selected-state
  contrasts are still tested across all 20 saved themes. No fixed brand
  hue is added to the bottom shell.
- The neutral-rich dark surfaces are derived from the user's chosen theme
  base, mixed with a small amount of charcoal, rather than competing
  pink-purple overlays. Home app bar, next-session hero, session outline,
  score context and anchored Create/Quick actions follow a consistent
  tonal elevation ladder. Only the meaningful status and Start Workout CTA
  carry stronger accent. Light themes keep their existing design roles.
- R38 changes only paint, navigation measurement and the associated browser
  contracts. Protected set rows, numeric inputs and steppers, RIR, Target/
  Previous, workout data, program generation and History are unchanged.
- CI checks the actual indicator-to-label and icon-to-label gaps, rendered
  opaque selection, 4.5:1 selected text and 3:1 selected-state perimeter,
  five destinations, and Home dock-vs-navigation clearance across themes.
  Reduced motion and forced-colors remain functional.

The screenshot is a product review reference; physical Android tap and
TalkBack inspection still require a device after deployment.

## R39 — Profile and Progress scannability

The October 11 (430px Android) browser captures made two issues visible:
three Profile mini-stats were separate floating tiles, and a lone strength
category took one half of a two-column comparison. The Progress overview used
too-small labels in three bulky cards while its search/filter controls had
their own competing shapes.

- Profile statistics share a single bounded, three-column metric strip with
  fine separators. Values are still individually visible and retain exact
  program history/volume semantics. The empty second half of a single-pattern
  strength grid is reclaimed automatically using :has() and :only-child.
  Two or more real patterns still use the existing two-column layout.
- Profile sections gain consistent inner padding, so total-lift measurements
  and badges do not run into the edge of their container. Edit and Share are
  unchanged actions, with 48px touch targets.
- Progress Sessions summary cards become denser and more readable. Secondary
  labels are 11–11.5px instead of 10px. Search and filter controls are
  at least 48px high; grouping and expandable histories have at least 44px
  targets. Theme-derived quiet/raised/accent roles carry over from R38.
- The normal history data, session count, score calculations, PRs, search,
  grouping, and all visible actions are unchanged. No cards are duplicated,
  no empty summaries are hidden, and no workout logging code is touched.
- Browser certification now inspects the *rendered* Profile three-way
  grouping, singleton strength bounds, section padding, Progress metric
  label sizes, filter hit areas and horizontal overflows at 320px and 430px
  before the branch can merge.

## R40 — Enlarged reading scale, short viewports and first-run clarity

Material 3 Expressive R39 made the primary dashboards more scannable.
R40 addresses the remaining fixed-pixel-size trap: on Android large-font
settings, bottom tabs may lose labels and 3-column metric subtitles can
become tiny or clipped even though the viewport itself remains the same.

- The five native bottom navigation buttons retain the R38 icon-only
  indicator and all navigation callbacks. Their label line may grow and
  wrap instead of being cut off by a fixed 56px button height. This is
  presentation only; text cannot cover the selected indicator.
- Profile identity and its supporting stat labels, Progress summary
  subtitles/history hints, and Plan summary captions acquire rem-based
  minimums. At the normal font baseline they retain the R39 geometry;
  increasing the browser/OS reading base enlarges text without stealing
  room from compact workout steppers or limiting pinch zoom.
- Actual Profile first-run/low-history setup content shares the quiet
  tonal surface family used by live data. First-run welcome feature
  descriptions can grow in height. No fake workouts or metrics are added.
- Short landscape viewports compact *only* page headers. Scrolling and
  bottom navigation remain available. Search/selection focus rings
  become clear at normal, high-contrast and keyboard input settings.
- The browser contract programmatically sets root font size to 20px
  (125% of the 16px baseline), then checks all five destinations at
  320px and 430px, the real Plan/Progress/Profile labels, navigation
  wrapping and horizontal overflow, and the no-program welcome path.
  It also confirms original saved programs are byte-for-byte unchanged.

This is targeted accessibility coverage, not a claim of complete TalkBack
or Android OS font-scaling certification. Native font scaling, TalkBack,
keyboard-inset and physical-device touch behavior still need manual review.

## Cohesive design-system cleanup (October 20, 2026)

The phone and tablet prototypes are **direction**, not a parity target. Home, Plan, Progress, Profile, Cycle, Settings, Library and Workout must feel authored by the same designer. The live theme palette, existing navigation and workout logging remain authoritative.

**Surface hierarchy:** one primary action per task; feature surfaces use `--pi-shape-feature`, regular panels `--pi-shape-card`, and buttons/rows `--pi-shape-action`. `--pi-color-card`, `--pi-color-control`, `--pi-color-feature`, `--pi-color-divider` and the saved theme's accent own color; do not introduce another purple/gray/red subsystem. Use `--pi-control-touch` for non-logger touch targets and `--pi-motion-paint` for paint-only micro-interactions. Different workflows may intentionally use different arrangements of these shared roles.

**CSS ownership:** Prefer editing the original rule and its shared token over appending later competing selectors. The first consolidation moves chart-favorite sizing to the B891 owner (including 320px) instead of fighting B891's 34px base and 32px narrow override from B911. Repeated `!important` declarations frequently override inline styles and cannot be deleted blindly. For each subsequent screen, identify the actual inline/layout owner, remove superseded selectors only after real-browser computed-style checks, and do not change the logging engine, data, user-selected themes or accessibility state.

**Full-app verification order:** Home and Plan task hierarchy; Profile, Progress and History; Cycle and Program creation; Settings and Library; live Workout/Focus (specialized compact controls); then dark/light/20-theme, text-zoom, keyboard/reduced-motion and physical Android/TalkBack certification. Preserve the current permanent test gates; no weakening tests to accommodate visual changes.

## Cohesion 09 — prevent drift (enforced alongside full CI)

New styling must reuse an existing semantic theme role and shared action, choice, surface or status owner. Static geometry belongs in a screen CSS owner; dynamic, user-controlled colors/measurements stay with the component where necessary. Do not append new `!important` rules to fix cascade disputes: edit the earlier selector or consolidate shared tokens instead. The automated debt budgets and collision allowlist live in `verification/ui-ownership-test.mjs` and must decrease as old CSS is proved redundant.

Before modifying a screen, name the unique owner of data, layout, motion and accessibility state. A responsive second presentation must not fork persisted state, engine calculations or navigation. Preserve specialized Workout/Focus logging, target/last controls, timers and recovery. Verify computed styles and real interactions before deleting legacy declarations; blanket regex removal and visual-only screenshots are insufficient. Every UI slice stays separately revertible, passes exact-head CI, and updates the **existing** `NEXT_APP_UPDATE_PLAN.md` rather than adding a competing roadmap.

## Cohesion 10 — Home actions and first-run hierarchy

When Home has no active program, the persistent Create Program dock is the **only** primary create CTA. Templates, Custom split and Cycles are supporting discovery actions, not repeated large creation cards. When a program exists, retain the Next Workout primary action and independent Game Plan and session outline. Home action tiles use shared `UiAction` semantics and a single scoped CSS layout owner; theme selection comes from `--pi-color-*` roles. Action route IDs are stable across state transitions, avoiding remount/reordering artifacts. Do not reintroduce duplicate first-run buttons under a new label, an end-of-file CSS patch, or a second source of next-day/progression truth. Verification owners: `verification/home-entry-hierarchy-test.mjs`, `verification/home-entry-hierarchy-browser-test.mjs`, existing B887 masthead and B909 separated coaching tests.


### Cohesion 12 — Plan selector presentation

Plan retains the same four shared `UiChoice` panes and remembered/collapsible week selection. Tab and week-picker paint/layout live in the Plan CSS family; JSX supplies canonical program data and choice state. Tab typography must beat the global button font reset and preserve full labels at enlarged text sizes. Keep the existing moving indicator, keyboard focus, 48px effective targets, semantic colors and forced-color selection. B831's real text-fragment bounds and R53's pane/week, palette and responsive journeys verify these interactions. Workout/Focus and training policy are unchanged.


### Cohesion 13 — weeks and cycle blocks share one choice

Plan weeks, Program weeks and cycle block navigation use `UiChoice`'s `summary` variant and the same horizontal choice rail. The existing screen callbacks and canonical projections still own browsing and training state. Label/caption typography, wrapping, minimum 51px targets and focus clearance are CSS-owned beside the shared primitive; selected/disabled/keyboard/forced-color states use its existing owners. Inactive manual weeks retain full readable contrast and can be selected; deload stays explicit. Missing cycle blocks are disabled and cannot change the active block. Browsing never saves a prescription or advances a cycle. R54 covers Program text scaling, keyboard/deload and data preservation; B890 covers cycle text scaling and missing-block handling; R53/B831 continue to cover palette, geometry, motion and the compact logger.


### Cohesion 14 — one Cycle presentation owner

Cycle list/detail and read-only block previews share the scoped Cycle CSS family. State/prescription/percentage values remain with their canonical projections; progress fill width stays data-driven. `UiHeader` and `UiAction` retain header and action behavior. Empty Cycle fallback offers one Create action; populated lists retain New, and the detail footer uses the shared primary variant. Expanded schedule and next-block facts remain expanded. The block path stays readable for active/planned/completed/missing states; missing records cannot open previews. Relative type and a narrow-card container query make enlarged labels readable without changing normal-text row layout. Native details/select/button behavior, visible keyboard focus, high-contrast state and paint-only/reduced motion remain required. B890 exercises real routes plus the exported empty/completed fallback and preserves the original no-advance/no-write assertions.


### Cohesion 17 — cycle creation

Template cards and adaptation use the shared card surface and Wizard text roles. Canonical Wizard state supplies `aria-pressed` and `aria-checked`; CSS owns their paint. The adaptation mark is decorative; its named checkbox button remains keyboard operable. Custom block drafts survive template changes. Cycle name/duration wrapping lives beside template styles, with no second responsive priority owner. R74 checks keyboard template/adaptation changes, custom week retention, normal/enlarged text and all step navigation; generation and saved programs remain untouched.
