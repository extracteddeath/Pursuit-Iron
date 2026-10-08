# Proposed next app update

Updated October 8, 2026 after recovering the M265 handoff. This is a product proposal; the features below have not been implemented by this document change.

## Verified starting point

M265 is merged through PR #42 at `0e454f79f067aaf7f207ef736aa2f9ea423f3969`. The four verification workflows and Pages deployment passed on that exact merge. The deployed entry page, release manifest, service worker and generation worker match the merged bytes. App 4.0.0 build 823 / Engine 0.65.6 remains the training model identity.

Interactive generation now runs in a module worker with cancellation, failure recovery and offline support. Standalone next-block building, cycle advancement, conversion and cycle roll are connected. Completed programs, cycles and workout history are retained. M265's verification covers all five worker operations and actual application buttons; this is browser evidence, not a new physical Android certification.

The current app already has Focus mode, numeric keyboard navigation, optional post-set effort ratings, automatic rest, in-session Undo, progression reasons and a detailed explanation sheet. It also already distinguishes swaps for the current session from program edits. The next update should refine those features and share their existing state and handlers.

## Recommendation: a better workout screen

Ship compact set logging and evidence beside the target together. They improve the most frequently used flow and make the verified engine easier to trust. Keep the existing purple theme and small steppers, preserve direct typing, and make each screen state have one clear primary action.

| Order | Proposal | User-visible result | Scope |
|---|---|---|---|
| First release | Compact current-set controls | Load, reps and Log set stay together; the rest timer and next set remain visible. Optional effort belongs to the set just logged. | Refine existing Focus and row views using their shared workout state. |
| First release | One clear reason beside the target | A short action and reason, with the exact contributing workout one tap away. | Condense existing coaching and connect it to canonical evidence. |
| Following release | Next-block preview and acceptance | Review what changes before a generated block is added or a future preview is replaced. | Add a proposal/accept step around existing worker transitions and Block Review. |
| Following release | Clearer temporary swaps | Today only and Change my program have visible, distinct effects; setup and attachment details stay with the performed lift. | Improve existing swap choices and history attribution. |
| Later | Missed-workout scheduling | Review a revised calendar and the next actual uncompleted workout. | Calendar changes with authored-day completion preserved. |

## Compact logging: concrete behavior

1. Opening a lift shows its name, current working set, target range, load and one reason. Completed sets remain available in a compact list; tapping one edits that exact row.
2. Load and reps keep small minus/plus controls, long press and direct numeric typing. Blank or partially typed values remain drafts. The keyboard's Next moves from load to reps; keyboard Done dismisses it and does not log a set.
3. Log set commits the displayed row once and follows the next pending set. Existing auto-rest settings control whether rest starts. A visible acknowledgment shows the actual load and reps just logged.
4. During rest, an optional effort strip labels its source explicitly: “Set 1: reps left?” A rating updates that completed row even though the next set is on screen. Skipping effort keeps it unreported and never blocks progress.
5. Undo restores the previous completion state and any untouched automatic rows changed because of that set. It preserves later manual edits. Repeated taps on Log set do not turn one completion into an accidental undo or a second completion.
6. After the last working set, the main action becomes Next exercise, or Finish workout when appropriate. Required drop-set or myo-rep work remains visible and uses the existing completion rules. Skipped warmups do not block completion.

Keep a compact rest strip in the usual view; retain the existing larger rest presentation in Focus mode. The current-set controls may stick within the workout container if measured phone behavior warrants it. They must reflow above the numeric keyboard and safe area. Avoid adding a permanent second action bar that duplicates Done, Next and the timer.

## Visible coaching: the evidence contract

The headline should answer what to do and why. A tap opens the existing explanation surface with the exact program/day, exercise, date, set rows and effort source that contributed to the target. A record shown as Last workout must remain distinct from an older anchor or starting reference used by a progression method.

These are illustrative copy examples, conditional on the canonical decision:

| Decision | Example headline | Detail that must be available |
|---|---|---|
| Earned increase | “Add 5 lb: all 3 comparable sets reached 12 reps.” | The contributing comparable exposure, its actual sets and any effort the method used. |
| Hold load | “Keep 205 lb: finish the 12–20 rep range first.” | The owned day and its five sets; a three-set workout on another day cannot stand in for it. |
| Lower load | “Use 95 lb: the last comparable workout missed the target.” | The actual reason, load change and newly prescribed reps. Lowering load must not automatically fill the maximum rep count. |
| Live adjustment | “Next set: 95 lb after two difficult sets today.” | Both observed source sets and their reported effort. The reason concerns this session and does not claim progression was earned. |
| Starting reference | “Starting load from your other workout; this day has no comparable log yet.” | The borrowed exposure and a clear reference label. Borrowed history cannot earn progression. |
| No usable history | “Choose a manageable starting load for 8–12 reps.” | The planned rep/effort target and the absence of usable evidence; do not invent a prior result. |
| Fixed protocol | “Today: the saved percentage target.” | The actual training max, percentage/set target and protocol stage. |

Read the same result/evidence used by `sessionSuggestion` and the canonical progression owners. Do not recompute progression in the view or infer success from the visible range. Per-set dynamic progression needs a reason for the selected row; a whole-lift banner can otherwise hide different loads. Manual values should say Edited by you, with the underlying plan still viewable.

Generated, template and custom programs need the same useful explanation experience. `explainPrescription` currently returns a structured explanation only for Pursuit Next programs; extending presentation to other programs must use their existing owners rather than quietly displaying a Next-only explanation as if it were universal.

Effort labels must distinguish Reported, Planned and Not reported. A planned 2 RIR must not become “you reported 2 RIR.” Superseded history revisions and unresolved imported days cannot inflate the explanation's set or session counts. Units, per-hand loads, assistance and bodyweight must use the same conventions as the numeric target.

## Example session for review

This is simulated data, not the user's training history. Assume the canonical double-progression decision has selected the increase.

- Prior comparable Upper A cable fly: 100 lb for 12, 12, 12 reps; 2 RIR reported for each completed working set.
- Current target: 105 lb, 8–12 reps, planned 2 RIR. The reason links to those three prior rows.
- Logging 105 × 8 immediately acknowledges Set 1 and displays Set 2. Effort can be recorded for Set 1 during rest or left blank.
- Undo returns Set 1 to pending without changing the logged history or later manually edited values.

The in-conversation concept uses this scenario to review layout and Log/Undo behavior. Its sample targets are illustrative; the production calculation remains the authority.

## Follow-up ideas worth keeping

| Idea | Concrete flow | Why it follows the first release |
|---|---|---|
| Preview the next block | Build in the background; summarize exercises, weekly sets, ranges and methods; show reasons for changes; Accept or Cancel. | Builds on responsive generation and clear explanations. |
| Better occupied-equipment flow | Show eligible replacements, active-gym equipment and attachment/setup differences; label Today only and Change my program; offer Undo. | Makes the existing swap scope understandable under time pressure. |
| Useful workout recap | Show actual work, meaningful changes from a comparable exposure and the next scheduled workout; keep estimated strength labeled as estimated. | Closes the loop between the target, what was logged and what happens next. |
| Gym setup shortcuts | Surface the existing seat, rack, cable and attachment notes beside the current exercise when relevant. | Saves time without another permanent coaching panel. |
| Missed-workout calendar review | Show the next uncompleted authored day and proposed dates before applying a reschedule. | Makes program continuity clearer without treating missed work as complete. |
| Clear backup status | Show the last successful export and a readable restore-impact preview. | Improves confidence in the existing device-local data model. |

For next-block preview, Accept must commit the reviewed proposal, not regenerate a different plan. Recheck request ownership at acceptance because history, custom definitions or settings may change while the preview is open. Cancel changes nothing. A preview with completed workouts must retain the existing replacement refusal; cycle roll must continue preserving completed records.

For temporary swaps, performed exercise identity and loading convention govern the log. Returning next session should restore the original programmed lift. Persisted program changes still need the existing roster transaction and phase-specific preservation rules. Neither choice should turn unrelated history into earned progression.

## Acceptance checks before implementation ships

Use the existing source and browser contracts; extend the relevant suites instead of adding another parallel gate or logging model.

| Area | Required check |
|---|---|
| Phone layout | At 320, 390 and 430 px, including a short viewport and open numeric keyboard, inputs/actions fit without horizontal scrolling or overlap. Long exercise names and increased text size remain usable. |
| Taps and typing | One tap logs an unchanged valid set; effort is an optional additional tap. Direct typing, deletion, decimals, small steppers and long press preserve input ownership. |
| Completion | Duplicate taps, rapid navigation and finish/reload produce one completed row and one owned final workout, with its stable session identity. |
| Effort | A post-set rating always targets the labeled previous row. Skipping it persists missing effort. Changing or removing it correctly re-evaluates eligible pending adjustments. |
| Undo | The source row and untouched automatic retuning roll back together; completed rows and later manual changes remain accurate. |
| Progression | Five-set Lower calf work cannot progress from three-set Push history; lower-load targets retain their proper reps; dynamic per-set decisions and fixed percentage protocols remain exact. |
| Evidence | Headline, detail and canonical result agree for earned, held, reduced, live-adjusted, reference-only, absent, legacy and revision-corrected history. No fabricated effort or session counts. |
| Program types | Generated, custom and template programs; warmups, supersets, drop sets, myo-reps and partials; assisted/bodyweight and lb/kg conventions. |
| Recovery | Minimize, reload, offline use, audio interruption and Android Back preserve drafts, completed rows, timer anchors, optional effort and the selected set. |
| Regressions | The full existing release checks remain green. Real-device input/keyboard handling needs an Android pass before claiming device certification. |

Build logging and explanation changes on an isolated branch, then validate simulated sessions through actual application controls. Keep training policy, scheduling and cycle generation changes outside that first implementation. Use this file as the single next-update plan; do not create competing roadmap documents or a new engine milestone for a brainstorm.
