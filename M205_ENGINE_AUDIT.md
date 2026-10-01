# M205 engine cleanup and audit

Pursuit Iron 4.0.0 build 795 · Engine 0.64.5.

Baseline: M204 build 794, commit `5fcb3f18fb035bb82a9010dbd59cdb5c1acdd8ff`. The interrupted prescription-ownership work was already present on main; this update builds on it rather than applying it again.

## Changes and rationale

Removed 153 unreachable top-level declarations from `App.js`, including the old generator pipeline and unused selection/repair helpers. Removed production shadow optimization, canary assignment/outcome collection, selective promotion, and their controls. Current creation, reroll, and cycle paths call the audited engine directly. Historical backup records survive through a data-only adapter.

Consolidated capacity-aware generation into `app-shell-adapter.js`. The browser formerly replaced that module through its import map, while the wrapper loaded a second base-module instance through a query suffix. Node tests could therefore exercise a different adapter. The wrapper, substitution, speculative wizard generation, and duplicate implementation are gone. Wizard feasibility retains cheap named-lift/equipment checks; actual creation still performs the full engine audit.

Removed 40 unused numeric rule entries and five overwritten historical description properties. The nine referenced saved-plan gates and the effective historical descriptions retain their prior values. Removed unused comparison/review modules and the empty type artifact; moved the verification-only coach oracle out of the offline runtime. Retired 15 milestone patch/finalizer scripts, 26 obsolete workflows, and four stray placeholder files. Semantic regressions remain under current read-only CI.

Fixed Settings diagnostics: the old supported-engine list omitted the current engine and silently skipped its saved plans. It now shares the current compatible-version list. Reused each candidate's functional-coverage evaluation instead of evaluating the same result twice.

Release identity comes from one current manifest. Runtime/UI hashes describe present files, not absent historical sources. The offline list contains 72 entries; 50 authored JavaScript files and seven vendor files remain. All 50 authored modules are reachable from the production mount after the retired helpers are removed.

See [ENGINE_ARCHITECTURE.md](ENGINE_ARCHITECTURE.md) for the executable path, ownership rules, adaptation behavior, and the compatibility code that remains necessary.

## Behavioral and efficiency evidence

| Measurement | M204 | M205 | Result |
|---|---:|---:|---|
| Main app source bytes | 3,455,863 | 2,858,127 | 597,736 fewer; 17.30% reduction |
| All modules/vendor JavaScript bytes | 4,827,724 | 4,122,174 | 14.61% reduction |
| JavaScript files in modules/vendor | 70 | 57 | 13 fewer runtime files |
| Total generation time, 13 paired cases | 4,953.18 ms | 2,369.01 ms | 2.09× ratio in this run |
| Audited base and executable shell prescriptions | Baseline | Exact comparison | 13/13 unchanged apart from version/timestamp/research metadata |

Cases include Full Body, Upper/Lower, ULPPL, PPL, Patterns, strength, advanced hypertrophy, no supersets, no bodyweight, minimalist intent, a dumbbell gym, and explicit muscle focus/reduction. Timing is one paired sample per case in one process, always baseline first. It is not a controlled multi-run estimate or a physical-phone measurement. The structural reason for less work is removal of unconditional experimental optimization, duplicate adapter loading, and repeated coverage evaluation.

Reproduce the comparison with the M204 commit available in local git history:

```bash
node --no-warnings --experimental-loader ./verification/import-loader.mjs verification/m205-generation-parity-benchmark.mjs
```

The script reconstructs M204's browser adapter resolution and exposes its actual production wrapper in a temporary checkout. [The recorded results](verification/m205-generation-parity-results.json) include requested/effective seeds, full configuration, timing, and regional guidance findings.

## Completed checks

| Area | Evidence |
|---|---|
| Release/source integrity | Manifest hashes, offline paths, syntax, shared imports, provenance/authority, prescription ownership, history/merge/restore, and existing semantic release suites passed |
| Current Settings self-test | Current saved fixture included; 14 programs, 2,112 cells, zero failures; only the deliberately ancient fixture skipped; input unchanged |
| Creation and capacity | 71 routes, 71 wizard-enabled, zero enabled/build mismatches; five time-capacity ladders; 500 cheap feasibility calls in approximately 253 ms |
| Regional repair | 18 covered creation cases; useful overflow repair, immutable input, protected strength, existing deficits, static/adaptive cycles |
| Locked cycles | Four powerbuilding cycles reproduced four prior overflow findings; reconciliation preserved the roster and met displayed ceilings |
| Adaptation | Dose reconciliation, selection intelligence, longitudinal evidence, phase specialization, block review, manual/automatic method lifecycle, transition deltas, real cycle advancement, and simulator parity passed |
| Broader quality | 44/44 generation programs passed engine and coach-quality gates; eight multi-block simulations covered 99 weeks, second-block/week-nine integrity, determinism, and history carryover |
| Browser workout persistence | 390 px and 360 px browser viewports; current automatic rep targets, explicit manual/blank values, completed sets, custom final-set techniques, and eight exercise-variation previews survived the tested flows |
| Browser Auto-fix | Canonical open/saved program persisted and survived reload; 24 stable exercise slots; changed weekly set totals were re-audited |
| PWA lifecycle | Waiting worker surfaced, Restart activated it, controller reload completed, cache rotated, and local training storage survived |

Browser evidence comes from Chromium with phone-sized viewports, not physical iOS/Android hardware. Test names that retain earlier milestone numbers identify durable regression contracts, not old release identities. Obsolete release-number assertions were removed or updated to the current manifest.

## Remaining findings, kept visible

This cleanup preserves prescriptions; it does not declare every dose heuristic correct. The independent regional audit reproduced the previously documented M200 guidance discrepancy:

| Reproducible case | Passing base audit, but displayed weekly guidance reports |
|---|---|
| Full Body 3/s60, seed 20500 | Side delts 1 vs floor 3 in week 4; rear delts 1 vs 2 in week 5; calves 2 vs 3, core 1 vs 2, upper back 2.8 vs 3 in week 1 |
| Minimalist PPL 5/s90, seed 20510 | Nine under-target regions, including side/rear delts 4 vs 8 and upper back 5 vs 8 |
| Strength Full Body, seed 20506 | Upper back 1.8 vs floor 2.5 in week 5 |
| Dumbbell gym Full Body 3/s60, seed 20511 | Four under-target regions: side/rear delts, quads, calves |

The short-session/limited-equipment cases combine capacity-constrained base dose with rounded weekly modulation. The coarse arbiter permits its modeled tolerance and groups back differently from the shell. Minimalist prescriptions deliberately reduce modeled dose, but the public five-day accumulation contract is not approach-aware; those rules can disagree. A future dosing change must decide which guidance is appropriate and test exact working-week time, protected priorities, and honest dose changes. Lowering targets to match the generated plan would not establish correctness. M205 changes neither the target rules nor these prescriptions and suppresses none of these findings.

The standard ULPPL seed 20503 and the other six higher-capacity comparison routes reported zero regional issues. Passing quality gates and reduced computation demonstrate behavior within the tested contracts; they are not evidence of individualized physiological effectiveness.

The main app remains a large single module, and difficult generation remains synchronous. Both are maintainability/performance boundaries for subsequent work. The current update removes unnecessary execution and makes the remaining engine authority explicit without a broad UI rewrite.
