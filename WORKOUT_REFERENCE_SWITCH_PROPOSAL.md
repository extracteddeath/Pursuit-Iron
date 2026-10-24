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

## Final visual and interaction handoff (Build 827 baseline)

### Layout states

- **Idle and active:** the first pending set has the only tinted row and left accent rule. Future sets remain legible on the neutral surface, without their own purple fill. The complete button stays on the right and the four stepper buttons remain immediately beside their numeric fields.
- **Comparison:** replace the one-word cycling heading and refresh icon with an explicit segmented pair, **Target** and **Previous**. Use quiet background and border tokens from the selected Pursuit Iron theme; tint *only the selected segment* with a low-contrast accent mix. This is a reference control, not a new primary action. On narrow phones, place it on a separate short header line instead of shrinking the numeric tracks.
- **Logging:** press Complete once, then highlight the next pending set. Existing rest UI appears in its established location. Do not reserve a permanent blank effort strip under pending or completed rows.
- **Completed:** retain the muted, short reps-left summary and small Undo action. Tapping **Reps left** may reveal a compact, anchored effort editor, but must not reopen the entire working-set panel, create a brightly filled row, or move the active row offscreen. Dismissing the editor returns to the short summary, and actual logged weight/reps remain immutable until Undo.
- **Selected numeric field:** touching load or reps selects that field's text for replacement without toggling the reference, selecting another set, expanding the row, or allowing steppers to intercept the input hit target. Keyboard dismissal cannot complete a set.
- **Last-set amplifier, plates, add-set and substitutions:** continue to live in the existing exercise view. A compacted completed row must not erase the underlying logged sub-sets or lose any action needed to inspect or edit their method.

### Exact reference behavior

1. **Target** is the default. Its values are computed by the existing prescription owner; manual edits do not silently reset when comparison changes.
2. **Previous** displays the matching actual prior set for the same programmed day and position. Unmatched rows say **No previous set**. Imported summary-only history says **Lift reference** instead of pretending to be the last corresponding set.
3. Previous set values are *display-only*. A tap on those values must not mutate weight **or** reps. This explicitly replaces the Build 827 behavior that copied both when tapping the LAST-column reference.
4. On the active pending set, a **Use previous weight** control applies *only the prior load* via the existing manual weight-edit owner. It never copies the old reps or recorded RIR. Present a **Use target weight** action as the reversible alternative; both leave the range and effort target unchanged.
5. Completed rows never expose an apply button. Warm-ups, bodyweight and assisted conventions are not filtered out by a positive-load-only check. Missing evidence must remove the apply action rather than invent a reference.
6. Both labels must be reachable by screen reader and keyboard; expose actual selection state and preserve touch target size without invisible hit boxes overlapping adjacent inputs.
7. Keep the same comparison selection across exercises and Focus mode during the workout. Treat it as view state, not plan state. Resume must preserve typed numeric drafts and completed workout data regardless of the selected reference.

### Design acceptance

A release is blocked if any of these occur: clicking a set produces an oversized card or bright completed-state treatment; pressing Previous changes any current entry; applying the prior load changes reps; typing a weight is intercepted by a stepper; completed sets are no longer editable through effort/Undo; existing amplified sub-sets, plates, add-set or rest controls disappear; or Focus mode uses a different reference owner.

Exercise the existing browser scenarios with at least a 320px narrow viewport, 390px reference phone, 520px wide phone, long exercise names, increased text, keyboard open, and tapped/long-pressed steppers. Test both generated and custom programs, unmatched prior sets, summary-only imports, repeated row taps, recovery, and a selected amplifier. Merge implementation only after the original release, engine and offline gates pass. **This handoff finalizes the design only; it does not claim the selector has been coded or shipped.**

