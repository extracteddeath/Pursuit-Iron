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
