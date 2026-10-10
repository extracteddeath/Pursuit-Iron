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
