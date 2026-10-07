# Engine audit repair: M224 / Build 814 / Engine 0.64.13

The independent audit found 14 defect families in the existing engine and its application boundary. This revision fixes the reproduced cases in the maintained source and exports the same computation into a runnable standalone package. It adds shared boundary rules, a pure workout serializer, a percentage-protocol module, permanent regressions, and an explicit final capacity check.

## Findings and behavior

| Finding | Resulting contract |
|---|---|
| F1: unit errors in recalibration/display | Convert historical, suggested, positional and live-advice loads once into the consumer's unit before snapping to equipment. Convert load labels too. |
| F2: target RIR mistaken for effort | Generated and custom progression share the same reported-effort provenance rule. Copied targets remain unobserved. |
| F3: dropped exposure context | Preserve session and set flags. New logs persist the real readiness check-in and substitutions. Context-limited exposures cannot bypass progression safeguards. |
| F4: final weeks over capacity | After dose reconciliation, trim only engine-owned accessory sets. If protected work cannot fit, return an explicit capacity refusal with actionable suggestions. |
| F5: instruction strings cloned as objects | Keep progression instructions as strings during reconciliation. |
| F6: null estimates counted as evidence | Count only finite positive strength estimates. Missing effort remains missing. |
| F7: empty/duplicate cycle evidence | Empty working exposures never advance adaptation. An identity contributes only its newest revision. Exclusions carry inspectable reasons. |
| F8: duplicate rows advancing weeks | Finite programs use distinct scheduled days throughout the explicit-history path, regardless of row count. |
| F9: custom edits rewriting history | The serializer saves custom targets. The custom evaluator respects saved targets even after current set-count or rep edits. |
| F10: changed-week safeguard bypass | Recalibration excludes incomplete, interrupted, edited and readiness-limited evidence, warmups, unfinished rows and technique extensions. |
| F11: mixed-unit records/trends | Normalize before comparing records or rounding estimates. Return records in their original units; series use one explicit unit. Related recovery, effort, retrospective and score readers also compare physical loads. |
| F12: disabled supersets changing clocks | Stored pair links stay dormant when Disallow supersets is enabled, in app and standalone projections. |
| F13: invalid dates poisoning all history | Exclude malformed timestamps individually so valid history remains usable. |
| F14: percentage preview/workout divergence | Preview, training-max editor and Workout share executable percentage targets, unit-aware maxes, AMRAP flags, and the accepted set budget. Saved per-set targets are used on replay. |

## Independent engine entry points

Use Node 22 or newer. No React, account, browser, package installation or app mount is required to run the exported package.

```sh
npm test
npm run verify
npm run example > generated-program.json
```

`index.js` exports generation, cycles, progression, runtime targets, history contracts, percentage protocols, and the `shell` namespace. `shell.loggedWorkoutPerformance` now serializes the same workout targets and effort provenance used by the app. It accepts explicit arguments and returns data without writing storage or invoking UI state.

| Boundary | Source and key functions |
|---|---|
| Raw history and physical units | `history-contract.js`: `historyNumber`, `convertHistoryLoad`, `observedHistoryRIR`, `completedHistorySets`, `normalizeHistoryEntries` |
| Percentage rules | `percentage-protocols.js`: `percentageProtocolFor`, `adaptPercentageSetBudget`, `deriveTieredLinearState` |
| App equipment, max editing, preview/workout | Extracted shell: `trainingMaxForUnit`, `withTrainingMax`, `percentagePlanFor`, `prescribeSets` |
| Exact log serialization | Extracted shell: `loggedWorkoutPerformance` |
| Longitudinal evidence | `workout-history-adapter.js`: `analyzeShellHistoryForNextEngine`, `deriveProgressionSelectionEvidence` |
| Final accepted weekly clock | `volume-repair.js`: `enforceShellWeekCapacity`, `finalizeGeneratedShellVolume` |

Module paths are relative to `modules/next-engine/`. Shell declarations are maintained in `modules/App.js` and exported verbatim, with source ranges and dependencies in `coverage.json`. The complete original app boundary is retained as reference evidence. This is one production authority with a reproducible independent export, not two hand-maintained engine forks.

## Percentage execution policy

Percentage protocols supply loading and rep waves. The accepted weekly cell owns the set budget. If a protocol has more sets, sample its wave while retaining its heaviest set and final rep-out when the budget allows. If it has fewer sets, repeat its first submaximal set before the wave. The preview explicitly labels the adaptation. Thus GVT and other capacity-adapted templates do not silently expand to an old fixed high-volume table after generation has passed its clock checks.

Explicit user rep or effort overrides make the percentage table inapplicable for that slot; the ordinary canonical runtime honors those fields. User-owned set counts remain theirs. Training maxes have a recorded storage unit; edits in another display unit convert before storage. Warmups snap in the displayed unit.

Tiered linear progression is derived from valid saved protocol history. Complete target success earns an equipment step; complete failed attempts move stages and the final failed stage resets the load conservatively. Incomplete or disrupted exposures hold. Explicit zero-rep failed attempts are preserved as attempts, never counted as completed working evidence. Replaying edited history recomputes the result without another persistent state writer.

## Verification and practical limits

`verification/m224-engine-hardening-test.mjs` covers all 14 finding families, physical-unit equivalence, actual log serialization, immutable custom targets, contextual/incomplete holds, capacity refusal and protected ownership, mixed-unit statistics, and 218 percentage cases across nine real template routes. Those cases span primary/secondary lifts, units, weeks, deloads, warmups, pending-row restoration and saved-history evaluation. Literal 5/3/1 arithmetic is checked separately from preview/workout parity. Tiered linear success, stage changes, reset and exclusions have independent history tests.

The new suite is required by the application release verifier, engine-contract verification, and the standalone test runner. The existing semantic/integration assertions remain intact. Full-package hashes, production-module hashes, source identities and declaration coverage are verified separately from behavior tests.

Historical logs that never saved their original targets cannot reveal targets that were later edited. They retain the compatibility fallback; new logs and snapshot-bearing imports preserve their real targets. Missing original units or implement conventions likewise cannot be reconstructed from raw numbers. No personal workout data is included in the tests or export.

Session caps are enforced against the existing engine's estimated clock, not a guarantee about actual elapsed gym time. Headless tests do not certify installed-app behavior on Android or iOS. Browser/device checks must pass before deployment. The app still has UI state coordination and a generated shell file; the export indexes that boundary explicitly. Further extraction can improve maintainability, but it must preserve these tested contracts.

## Recorded checks for this repair

| Check | Result |
|---|---|
| Reproduced original and boundary defects | 18 assertions pass; zero failures |
| Percentage preview/workout parity | 8 original template routes agree; permanent suite covers 218 cases across 9 routes |
| Independent semantic suites | 31 pass |
| Generation matrix | 180 configurations; 174 built and 6 explicitly refused; 1,044 working weeks; 24,648 cells; 107,458 independent checks; no structural, instruction-type or hard-time violations |
| Remaining volume guidance in matrix | 15 plans have partial capacity-limited volume guidance; these are reported, not certified as meeting every preferred volume target |
| Ownership and roster changes | 15,619 assertions; 100 reorders; zero failures |
| Cycle and recovery lifecycle | 32 routes completed; 72 advances; 16 recovery insertions and resumptions; 21,488 synthetic logged sets; zero issues |
| App/export parity | Catalogs and templates agree; three splits and 177 cells agree, including week plans and volume |
| Application release integrity | Passes source/integration gates, module parsing, release hashes and 79 offline entries |
| Quality contracts | Three existing quality gates pass |

Checks ran locally under Node 24.19.0. The package declares Node 22+ and CI targets Node 22. Browser and physical-device checks were not run locally because no browser binary is available; this candidate has not been deployed during this repair turn.

