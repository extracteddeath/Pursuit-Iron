# M232 — Conservative live autoregulation

Engine 0.65.4 / Build 821 integration candidate.

Workout's former no-op prefill hook now calls a canonical, pure live transaction. Two completed accessory observations with explicitly reported effort and repeated rep/effort misses can ease one pending automatic working row. The desired reduction is 5%; available loading must keep the actual step within 10%. Missing effort, coarse/unloadable increments, manual/changed/added rows, interruptions/edits and strength/percentage protocols retain their targets. Reps, rest, techniques, roster and set counts stay authored.

Every change records its two source rows and original pending target. Repeated events and resume cannot compound it. Undoing either observation or clearing its effort restores only an untouched pending app-owned row; typed values and completed work remain protected. Resume refresh respects the session's explicit live adjustment.

Adjusted work carries `recoveryLimited` through the production serializer. Existing additive history-context rules prevent it earning load progression or becoming durable dose-response failure evidence. The live decision never claims earned progression.

Validation: transaction immutability, observed-evidence threshold, bounded/loadable changes, completed/manual/protocol protection, idempotence, JSON recovery, undo and actual serialization/evaluator refusal. A phone component gate exercises reported misses, autosave/reload and undo through real controls. The existing custom-program phone gate remains green; baseline program generation is unchanged.


## Build 901 suggestion audit — Engine 0.65.9

Scope: custom and generated workout suggestion ownership, history-to-session realization, live effort transactions, target/actual effort rendering, serialization and recovery. Program generation/volume policy was not changed by this audit.

### Findings corrected

| Finding | Cause | Result |
| --- | --- | --- |
| Prior target RIR disappeared when the next set became active | Full rows rendered their effort status only for the active unfinished set | Every working TARGET row keeps its prescribed effort. Only the active unfinished row uses the accent; previous/upcoming targets use muted text. Actual RIR remains under logged reps. |
| A history-based next row could demand three more reps after a bottom-range set at target RIR | Per-position historical targets were generated correctly, but the live policy only reacted to two load/effort misses | An explicit current-session observation can cap the immediate untouched same-load rep prefill. A 9/11/9 prefill becomes 8 for the next set after 8 at 2 RIR against a 1–2 RIR target. |
| “Tunes the next set” promised an adjustment even for a hold/protected/final set | Static UI copy did not read the actual adjustment | Feedback distinguishes reps adjusted, load eased, unchanged target and future-workout evidence. Unrated effort is optional and guides suggestions. |
| Focus target reserve could be missing | Header read `s.rir` instead of `s.target.rir` | Focus reads the prescription and displays the owed previous set's target while the next set is active. |

### Live rep rule and safeguards

For the immediate next same-load working set, cap automatic reps at `logged reps + reported RIR − next target RIR upper bound`, floored to an integer and bounded by the planned rep range. Apply only when this lowers the existing prefill. Using the upper target bound is deliberately conservative; no extra fatigue model or assumed unreported effort is introduced. Lower/higher loads are not extrapolated, fixed-rep and authored percentage/set-target protocols are protected, and manually edited/added/completed rows are never rewritten or skipped to tune a later row.

The original two-miss accessory load guard remains separate. A prior rep cap does not disqualify a genuine later miss from that safeguard. Load reductions retain `recoveryLimited`; a within-range rep cap does not falsely label an ordinary workout as recovery limited. Both adjustments preserve their original target for source-aware undo. Re-rating recomputes from the original automatic prefill; clearing the rating restores it. Resume keeps a persisted live adjustment instead of replacing it with historical targets.

### Audited paths retained

- `performance.js` evaluates completed comparable work. Classic double requires a complete top-range exposure at acceptable effort for a heavier load. Dynamic double preserves positional load/reps and earned per-set increases. Missing, interrupted, edited, shortened and recovery-limited work cannot earn automatic progression from absent sets.
- `progressionSetTargets` realizes each historical set position rather than copying the strongest set's reps across all rows. Different historical positions can legitimately produce 9 then 11; today's observed effort now supplies the missing live guard.
- Custom and generated bridges retain day/program history ownership, explicit RIR provenance and load-unit conversion. Prescribed reserve is not treated as reported effort; the explicit `rirReported` flag survives serialization even when actual and target effort match.
- The workout runtime retains equipment-load snapping, range bounds, pending-value ownership and literal percentage protocols. Completed/manual values and warm-up/technique extensions remain protected through refresh/reload.
- Engine 0.65.9 retains the current and four preceding engine versions. The established generation golden/property gates remain in place; no baseline prescriptions were regenerated for this fix.

### Regression evidence

The permanent M232 unit gate now covers the exact 9/11/9 → 8 at 2 RIR → next 8 reproduction, independent per-row intent, unknown/invalid effort, no extrapolation across loads, manual/completed/added protection, authored protocols, re-rating, clearing, undo, repeated events and the two-miss load guard after a rep cap. The existing real-browser M232 gate reproduces the same sequence through actual controls, compares muted prior vs active target colors, checks actual vs prescribed RIR, reload persistence and Focus target parity.
