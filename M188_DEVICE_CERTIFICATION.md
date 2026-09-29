# M188 Android Production Certification

Candidate branch: `m188-production-torture-certification`

M188 is not considered physically device-certified until every required case below is run against the installed PWA from this candidate on a real Android phone. Record **Pass / Fail / Blocked / Not run** and concrete evidence for every case.

| # | Case | Required evidence | Status |
|---|---|---|---|
| 1 | Cold installed-PWA start | App reaches Home without black screen, startup error, or endless loading | Not run |
| 2 | Offline cold start | Force-stop, disable network, relaunch installed PWA successfully | Not run |
| 3 | M187 → M188 update | Same-origin installed PWA updates without clearing user data | Not run |
| 4 | Update while app is open | Update-ready flow does not take over until user restarts | Not run |
| 5 | Workout process-kill restore | Log partial sets, kill process, relaunch, restore exact workout state | Not run |
| 6 | Lock / interruption restore | Lock screen or interrupt app during workout; timers and logged sets recover correctly | Not run |
| 7 | Android system/gesture Back | Back closes sheets/navigation in correct order without accidental workout loss | Not run |
| 8 | Numeric workout entry | Type/delete weight and reps; values are not overwritten by steppers; long-press stepping works | Not run |
| 9 | Compact workout layout | SET / TARGET-LAST columns remain readable with no duplicate RIR or truncation | Not run |
| 10 | Swap sheet touch scrolling | Replacement list scrolls smoothly with one vertical scroll owner; no stuck/nested scroll | Not run |
| 11 | Program/cycle hierarchy | Long names remain readable; cycle path labels Hypertrophy → Strength → Peak correctly | Not run |
| 12 | Block Review explainability | Review appears at the correct time and uses user-facing language with no engine jargon | Not run |
| 13 | History correction | Edit a completed workout and verify Progress/readiness/progression reflect the correction | Not run |
| 14 | Equipment/restriction enforcement | Generate with no supersets/bodyweight and restricted equipment; resulting plan obeys all settings | Not run |
| 15 | Adaptive cycle transition | Advance a multi-block cycle and verify next block remains non-empty, audited, and phase-appropriate | Not run |
| 16 | Backup/restore | Export, clear/reinstall or isolate storage, restore, and verify programs/history/settings survive | Not run |
| 17 | Large font / accessibility | 200% font scale remains usable with no clipped critical controls or unreachable actions | Not run |
| 18 | Landscape / split-screen / keyboard | Core navigation and workout inputs remain usable across geometry changes | Not run |
| 19 | Theme sweep | Check all shipped light/dark themes for legibility, selected/disabled/destructive states, and button colors | Not run |
| 20 | Long-session stability | Complete or extensively exercise a long 5–6 day-program workout path without runaway scrolling, stale state, or crash | Not run |

## Pass rule

- Any **Fail** blocks M188 production certification.
- A **Blocked** case must include the blocker and is not equivalent to Pass.
- Automated CI can certify generation, longitudinal behavior, release integrity, history/restore logic, and real-browser service-worker lifecycle, but it does not replace the physical Android interaction cases above.
