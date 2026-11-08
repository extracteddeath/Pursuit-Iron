# Warmup-aware program composition — 2026-11-07

The two preceding source checkpoints are published on
`architecture/kotlin-engine-rebuild` as `d7a42cd` and `dd091f1`. Their file trees
match the tested local commits `80601a7` and `94fe1fe`, respectively.

## Changed user behavior

New starter programs account for specific warmups, working-set duration and
rest while choosing the week. Previously the generator chose working sets
using one estimate and added warmups afterward. That could reject a usable
selection or turn an apparently feasible day into an overlong prescription.

Version 3 supplies the existing composer with the durations of its actual
prescription. Warmups occur once per exercise visit and earn zero working-dose
credit. General preparation/transitions occur once per day. The final set's
post-workout rest remains excluded from elapsed-time estimates; its authored
rest target is preserved. The final full-session audit independently recomputes
the same time and muscle dose before a draft can be saved or issued.

## Executed timing comparison

This controlled fixture tests time accounting: three Full Body days, advanced
hypertrophy, three calibrated dumbbell movements, 25-lb working weights, verified
20/25/30-lb stock and unchanged dose constraints. It is not an approved personal
program or evidence of complete muscle coverage. Minutes below are projected
from the actual set/rest plan, not observed workout durations.

| General preparation and transitions | Previous v2 result | Previous final minutes | New v3 result | New final minutes |
| --- | --- | --- | --- | --- |
| 10 min/day | Unresolved | No set draft | Unresolved | No set draft |
| 15 min/day | Unresolved | No set draft | Complete draft | 46 / 40 / 42 |
| 20 min/day | Complete draft | 54 / 45 / 44 | Complete draft | 60 / 41 / 41 |
| 30 min/day | Unresolved | 64 / 57 / 51 | Complete draft | 58 / 60 / 54 |

All complete v3 rows fit the declared 40–60-minute range and pass the declared
muscle-dose/recovery constraints. No range was widened and no preparation time
was hidden. The ten-minute selection still needs review; the bounded search
does not invent extra work just to fill the clock. These results demonstrate
correction of a prior Kotlin timing defect, not globally optimal scheduling,
evenly distributed session durations or superiority over the web coach.

## Canonical owners and persistence

- **G1 / L14–L16:** `CoachWeekComposer` reads typed per-visit timing. The existing
  `CoachSessionPrescriptionDomain` derives it from the actual policies and
  rejects a changed rest/warmup policy that understates that timing. A
  day-specific policy takes precedence over its shared purpose policy.
- **G4 compatibility:** saved v1 and v2 starter seeds replay their original
  generation. Only new v3 seeds select with warmup-aware timing. Cold reopening
  continues to require the exact saved cycle, source revision and athlete.
- **Native approval:** format 7 stores the complete timing model for new timed
  Composer programs. Existing untimed approvals retain their exact format-6
  bytes; versions 1–6 remain readable. A timed approval cold-opens and issues
  its exact warmup/rest targets through the existing journal authority.
- **G5 training:** the shipped-catalog four-day generation/logging/correction/
  Finish/next-week test now executes the v3 path. A separate native-file
  regression cold-replays and issues a saved v2 program without opting it into v3.

Separate execution against the preceding implementation verified identical
encoded bytes for the old v1/v2 cycles and an untimed native approval. The
v2-cycle and format-6 approval digests are also pinned in native regressions:

| Frozen example | SHA-256 |
| --- | --- |
| V1 converted cycle | `afcbb3033470390d9d942650b26806e1bda73c48bd645d9971e6fc5e62050b55` |
| V2 converted cycle | `be2fcc9b5d97f0697085708efa8afbecd66437d9f3642b000d97d53be16c0e54` |
| Untimed format-6 approval | `4b472914ac4dc470bac65d3c27dbd80bd7e9d6f2ef8f2655de5d8fcf36ea9b03` |

The warmup loads, reps and provisional physiological assumptions from v2 have
not changed; this pass changes how their existing durations influence the search.

## Verification and limits

Full source validation passes: 691 engine tests, 446 native storage tests and
Android Kotlin/main-manifest compilation. The isolation/scope/independent
comparison-oracle checks also pass. Focused compatibility tests additionally
verify fixed historical bytes, native cold issuance, unchanged saved v2
generation and refusal of understated timing. No APK task ran.

Reproduce the main checks with:

```sh
gradle :kotlin-engine:testDebugUnitTest :native-coach-store:testDebugUnitTest \
  :app:compileDebugKotlin :app:processDebugMainManifest --no-daemon
```

Phone interaction acceptance, catalog/direct-muscle coverage, the broader
goal/split/gym matrix, measured elapsed-time accuracy, individualized fatigue
validation and complete engine-superiority evidence remain open. Readiness and
fatigue coaching switches retain their existing behavior.
