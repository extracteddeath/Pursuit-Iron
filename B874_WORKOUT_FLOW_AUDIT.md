# B874 — In-session injury, plate loading, and set-aware dock experiment

## Product and safety boundaries
An acute injury is a **user-reported interruption**, not a training weakness to be corrected by the progression engine. The in-session sheet surfaces the exercise catalog's primary muscle and `secondaryOf(ex)` supporting involvement, but does not infer a diagnosis or promise the selection is medically exhaustive. Users review, include/exclude the affected exercises and choose the action. They can stop training rather than selecting an action. Skip means no more sets of the selected exercise; work already completed remains. Reduce means roughly half of remaining non-warm-up sets, with existing logged results and manually specified loads intact. Undo reverts both sets and reports, and persistence uses the live session snapshot. The permanent future program is untouched.

## Plate interaction
Two click paths open the same existing calculator: tiny plates under the **current set** and the wider strip for the next unlogged working set. The toolbar's Plate action is updated to use that same active set. The calculator draws actual two-sided plate shapes and preserves the existing numeric load-per-side, achievable load and bar selector. No hardcoded plate inventories or arithmetic duplication.

## Opt-in Material 3 Expressive dock
Choose **Workout Options → Preview set-aware dock** to inspect the prototype, using the real current session data and current set position. The floating neutral surface has grouped Last/Suggested/Target context, an optional prescribed amplifier, and plate-calculator access. During a rest interval the same container changes to a timer state, reading the existing wall-clock rest value rather than introducing a parallel timer. The prototype's data is read-only (except navigation to existing calculator), not a replacement for the input controls. Its spring treatment respects reduced motion and theme surfaces. Production adoption requires physical Android touch/IME/overlay avoidance and accessibility validation.

## Regression coverage
Existing B831 real-browser mobile fixture extends to plate readout pointer activation with correct live load, symmetrical visual barbell construction, opening and closing the opt-in dock, and explicit injury adjustment/recording without permanent program mutation. Preserve the full four-workflow gate and all workout history/offline contracts. Build 874 release finalizer updates app build, SHA-256 hashes and cache revision; no temporary finalizer is allowed in merged main.
