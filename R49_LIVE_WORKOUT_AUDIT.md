# R49 — Live workout screenshot-led hierarchy pass

## Evidence / critical UI

Owner-provided October 11 Android screenshot of a running **SekuFit Upper** workout, at exercise 1/7 (Low-Incline Barbell Press), 0/29 logged sets. The first set is 195 lb x 7, 2 RIR. The screenshot shows a selected working row with steppers, prescribed target, prior-workout comparison, per-side plate breakdown, a J-hooks 7.5 pinned setup field, autoregulation recommendation, Coach disclosure, warm-up/superset/swap/plates utilities, a large Next exercise button and an accent Finish icon.

## Findings

- Working-set data is complete and deserves focus. The editable row must stay higher contrast than surrounding surfaces; the user's established compact Target/Previous, +/- and RIR behavior must not change.
- Next exercise looked like a large bright primary call to action **before any work sets were logged**. It is navigation rather than the immediate training goal; keep it reachable, but demote its visual priority while the visible exercise still has unlogged sets.
- A bright Finish icon competed with the current set. Make the top action neutral but clearly available, retaining the save/confirm behavior.
- User-defined rack/seat/setup note is valuable but should be a quiet editable row rather than another pink card. Coach summary and warm-up/superset/swap/plate tools similarly belong to the neutral auxiliary layer.
- Avoid further increasing main screen height. Tighten only non-critical header/tool gaps, and preserve readable/full coach text, pinned data, prescription rationale, equipment settings and all exercise navigation.
- Keep the footer and numeric fields inside Android keyboard/visualViewport layout. No gradients.

## Implemented

1. Solid, neutral Finish and workout tool controls; real 48px auxiliary utility targets.
2. Cleaner Coach disclosure and editable equipment note, with existing action semantics.
3. Slightly shorter header/non-logger spacing; rest timers and editable set grid left unchanged.
4. **Normal mode only:** if *the currently rendered exercise* contains incomplete set rows, Next exercise uses the raised neutral surface instead of the bright primary fill; it is **not disabled**. The accent automatically returns when all visible rows are complete. Focused mode's primary Log set action is explicitly out of scope.
5. Browser integration verifies actual painted neutral surface, 50px+ Next target, contrast with Finish, note/coach/tool hit heights and unmodified real input field width. Existing program, workout, Android lifecycle, theme, motion, navigation and engine gates remain.

## Not changed

No changes to `modules/App.js`, training engine, weight/reps typing, numeric steppers, prior-vs-target toggle, completed set fading, RIR picker, autosuggested load, rest state, session persistence, swap logic, warm-up sets or finish confirmations. The open unrelated PR for muted Target/Previous behavior is deliberately not merged or overwritten.

## Follow-up

After deployment, review a physical-device screenshot during a first pending set, after logging a set, during rest, and after finishing the current exercise. Inspect whether the workout chrome now supports the working row without losing one-handed access. Next investigate Home/Plan estimated duration disagreement from R48; separately consider a deliberate component-level consolidation of the accumulated CSS.
