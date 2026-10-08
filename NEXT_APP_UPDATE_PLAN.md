# Proposed next app update

The M265 worker update is implemented. The ideas below are proposals, not shipped features. Keep the verified training model stable and make its existing decisions easier to use during a workout.

## Recommended scope: workout flow and visible coaching

| Priority | Improvement | Concrete experience | Existing foundation |
|---|---|---|---|
| 1 | Faster set logging | A compact sticky action area for the current set: load, reps, effort, Done, Undo and Next. Keep the rest timer visible without pushing inputs off a narrow phone. | Current workout inputs, rest controls, pending-row ownership, live persistence and Android lifecycle recovery |
| 1 | A short explanation beside the target | “Add 5 lb: all three sets reached the top of the range.” A tap reveals the relevant owned workout and whether effort was actually reported. Sparse or borrowed history says so plainly. | Existing progression explanations, prescription snapshots, effort provenance and semantic reference rules |
| 2 | Review the next block before accepting it | Build a proposed next block in the background, then show exercise, set, rep and method changes with reasons. Accept commits the proposal; Cancel preserves the current plan. | Worker transport, existing Block Review, canonical transitions and ownership guards |
| 2 | Clear choices when equipment is occupied | “Today only” or “Change my program” when swapping a lift. Respect the active gym, loading conventions and movement compatibility. Record the actual performed lift honestly. | Gym inventories, custom mechanics, semantic compatibility, roster transactions and history attribution |
| 3 | Missed-week scheduling | Offer a revised calendar after a missed workout, without marking untrained work complete or silently increasing loads. Show the next workout and the remaining block clearly. | Authored-day history ownership, session cursor, schedules and cycle-duration projection |

Ship the first two together as a focused app update. They improve the screen used most often and expose intelligence already present in the engine. Add next-block preview after the workout flow is comfortable on a phone.

## Acceptance criteria for the recommended scope

- At 320, 390 and 430 pixels, the current set and its actions fit without horizontal scrolling or keyboard overlap.
- A user can log a set, report effort and move forward with few taps; Undo restores both the row and any pending automatic retuning.
- Every automatic target explanation points to the same evidence used by the canonical calculation. It never invents reported effort or progression earned from reference-only history.
- Minimize, reload, interrupted audio and offline use preserve typed inputs, completed rows, timers and pending explanations.
- Completing a set writes one owned history record. Navigating or double tapping cannot create duplicate completed work.

## Later options

- Progress comparisons that distinguish observed improvements from estimated strength, with the contributing workouts available on tap.
- Gym-specific setup notes and attachment shortcuts beside the current lift, reusing existing setup storage.
- A backup status that shows the last successful export and offers a straightforward restore preview.

Avoid expanding the next release into a new training model, nutrition app, social feed or a large settings redesign. Validate the proposed workout flow with simulated sessions and actual app button tests before adding more scope.
