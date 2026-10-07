# Engine upgrade sequence through M235

Continuation branch: `engine/m228-m231`. Each milestone is committed only with its own passing evidence. The application release stays separate from this engineering sequence; `main` is not advanced by these checkpoints.

| Milestone | Scope | Acceptance |
|---|---|---|
| M228 | Canonical engine boundary: move domain computation/coordination out of App.js; one API; retire declaration extraction | Build 816 differential goldens, headless imports, canonical binding identity, release/engine/browser gates |
| M229 | Versioned domain contracts/types, explicit legacy migrations, eliminate adapter/volume cycle | Migration roundtrips, malformed/future-version rejection, acyclic module graph, legacy output parity |
| M230 | Decompose realizer into strength, hypertrophy, ranking, time, sequencing/pairing, session construction and repair | Byte-preserved declarations and unchanged golden programs |
| M231 | Athlete response: dose, fatigue, frequency, exercise success, progression velocity, recovery and confidence | Ownership/provenance, sparse-evidence restraint, historical replay, bounded changes |
| M232 | Conservative live autoregulation | Completed/manual rows protected, intent preserved, no earned progression from limited work, interruption roundtrip |
| M233 | Semantic exercise graph and safe transfer/custom metadata | Explicit semantic compatibility, equipment/avoidance constraints, unknown metadata fails closed |
| M234 | Unified candidate objective and constraints instead of competing repair acceptance rules | Hard constraints first, deterministic tie handling, bounded search, immutable candidates, audited final output |
| M235 | Verification 2.0: properties, mutation checks, replay, benchmarks and differential testing | Reproducible runner, meaningful mutation kills, stable replay, performance budgets, complete integration evidence |

Completed milestone details and limitations are recorded in the corresponding reports. A milestone with unfinished acceptance work is not reported as complete.

Verified checkpoints: M228 and M229. The continuation remains on the engineering branch.
