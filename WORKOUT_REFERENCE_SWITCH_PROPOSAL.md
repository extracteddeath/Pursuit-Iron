# Workout target and previous-value switch

October 9, 2026. Proposal for the next UI change; Build 827 retains its existing TARGET/LAST toggle.

## Recommended design

Use a compact two-option control labeled **Target | Previous** above the set rows, beside the set heading. Both choices remain visible. Use a subtle purple selection indicator, a quiet surface and clear text; reserve the stronger accent for the active set. Replace the tiny refresh symbol as the only clue that the heading is interactive.

The control switches the reference column for the whole exercise. Weight and reps inputs keep their exact positions and values. Switching is a read-only comparison: it must never fill inputs, change the plan, log a set, or reset drafts.

At small widths, give the control its own compact heading line instead of taking width from the four steppers. Target remains the initial selection. Keep the choice when navigating exercises within the current workout and expose it in Focus mode too. Reuse the existing targetMode state and reference lookup rather than introduce another data path.

## Separate comparison from applying a value

Keep the active set's familiar shortcut: **Last time 210 × 2 @ 0 RIR**, using only the actual matching prior set and reported effort. Missing effort remains absent. In Previous mode, omit the duplicate shortcut text and offer a clearly labeled **Use previous weight** action beside the active-set reference.

Use previous weight copies only that row's load. Keep the intended reps, range and effort: selecting an old load should not also reintroduce a failed rep count. Offer **Use target weight** after applying a previous load or editing the load. Neither action changes completed sets, future sets or the engine prescription. Manual ownership and Undo must remain intact.

If the prior workout has fewer sets, show **No previous set** for unmatched rows. Never repeat its final set. Preserve summary-only history as an explicitly labeled lift reference, with no invented per-set effort. Handle pounds, kilograms, bodyweight and assisted loads through the existing loading conventions; no positive-load-only shortcut rule.

## Keep the full workout functionality

| Function | Treatment |
|---|---|
| Small minus/plus and long press | Stay on both sides of each input; preserve one-step tap behavior. |
| Numeric typing | Tap selects text; deletion and partial drafts remain valid while editing. |
| Complete set | Stays on the right; completion stores the displayed load and reps once. |
| Completed sets | Quiet reps-left bars with effort editing and Undo. |
| Plate breakdown | Remains under the weight field; tap opens the visual calculator. |
| Amplifiers | Keep the last-set technique label and explanation attached to the relevant set. Drop and myo work remain visible and completable. |
| Add set | Remains available below the exercise; additional sets have honest reference availability. |
| Rest | Existing rest bar, controls and exercise navigation remain available. |
| Warmup, superset, swap, notes | Keep existing actions and scope; no replacement menus required. |
| Focus and recovery | Share the existing workout state; comparison cannot erase edits on minimize, resume or offline reload. |

## Implementation and acceptance

Implement the explicit selector first, then refine load-only shortcuts using the existing editWeight ownership path. Avoid moving the row layout or changing the engine. Extend the current workout browser suite rather than create a second test registry.

Check comparison without input mutation; applying previous load without copying old reps; restoring target load; completed-set protection; manual edits through live adaptation and Undo; absent and summary-only history; generated and custom programs; percentage, bodyweight and assisted loads; lb/kg; amplifiers; add-set and Focus access. Measure 320–520px phone widths, larger text and keyboard visibility. Confirm rest and completion controls remain reachable.

This proposal preserves the compact Build 827 foundation while making comparison explicit and applying a load deliberate. It has not been implemented or published.
