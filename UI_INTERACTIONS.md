# Pursuit Iron interaction standard

Build 831 applies the approved compact workout prototype's interaction language to the app.
It is inspired by Material 3 Expressive; it uses the existing Pursuit Iron themes, typography and
layouts, with no new UI library or online dependency.

- Buttons compress and change corner shape on press, then settle with a spring. Keyboard and
  pointer activation share feedback. Scrolling, cancelled touches and disabled controls cannot
  leave a button pressed.
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
