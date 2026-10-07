# Engine upgrade and assurance sequence through M237

M228–M236 are merged to `main`. M237 continues on `assurance/m237-ci-toolchain` as CI supply-chain hardening rather than an engine behavior change.

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
| M236 | Assurance simplification: unique gate ownership, CI deduplication, runtime reachability, exporter cleanup and exact offline-shell ownership | No orphan/duplicate executable gates, no unreachable authored modules, no retired exporter dependencies, exact required precache set, full existing behavior coverage |
| M237 | CI supply-chain hardening: immutable action revisions, non-persistent credentials, explicit runner OS and locked browser harness | Full-SHA official actions, Ubuntu 24.04, npm lock/integrity enforcement, `npm ci`, all prior contract/browser/export/release gates green |

Completed milestone details and limitations are recorded in the corresponding reports. A milestone with unfinished acceptance work is not reported as complete.

Verified behavior checkpoints: M228–M235. The M235 behavior-bearing verification commit `94377683c45d8449d1eec5488d76664fcb781822` passed the permanent contract matrix, release integrity, all phone/offline browser gates, and the independent engine audit. Its Verification 2.0 gate passed 26 deterministic property cases, 13/13 Build 816 differentials, stable replay, 2/2 mutation kills, and the performance budgets. M236 changes assurance infrastructure and offline packaging only; see `M236_VERIFICATION_ASSURANCE_REPORT.md`. M237 changes CI/tooling dependencies only; see `M237_CI_SUPPLY_CHAIN_REPORT.md`.
