# Build 870 / R87 — Keyboard continuity for workout numbers

## Concrete defect
The normal set logger already selects a weight or reps value when focused and advertises mobile keyboard actions: **Next** for weight and **Done** for reps. Neither action had an Enter handler, so the keyboard's Next could leave focus on weight rather than move to reps.

## Correction
- Enter from an editable workout weight field focuses and selects **reps in the same set**.
- Enter from editable reps blurs the input, allowing the keyboard to dismiss. No set completion, rest timer or effort editor is triggered.
- Ignore IME composition, legacy keyCode 229, and read-only/disabled logged fields. Never rewrite numeric content or propagate to another set.
- Preserve tap-to-select, stepper long-press, Target/Last, all layout and colors, state, history, and engine 0.65.7.
- No new overlay, haptics, audio, global keyboard listener or alternate motion layer.

## Verification
The existing B825 logging browser test exercises real focus and Enter across 320, 360, 390, 430, and 520px. It checks same-row Next selection, no interception of composition Enter, Done blur without completing the set, unchanged typed decimals and reps, and the existing history, stepper, RIR, Undo and compact-grid contracts.

Require all four final-commit CI workflows before merge; verify Pages and main workflows afterward. Physical Android keyboard variants still need device inspection.
