# Engine upgrade and assurance sequence through M251

The behavior-bearing engine remains M234 / Pursuit Engine 0.65.6 / app 4.0.0 build 823. M235 verifies that behavior line. M236–M248 harden ownership, verification, CI, history provenance, and standalone packaging without changing training behavior or release identity.

| Milestone | Scope | Acceptance |
|---|---|---|
| M228 | Canonical engine boundary: one production API; retire declaration extraction | Build 816 differential goldens, headless imports, canonical binding identity |
| M229 | Versioned domain contracts/types, explicit legacy migrations, acyclic computation graph | Migration roundtrips, malformed/future-version refusal, output parity |
| M230 | Decompose the realizer into bounded stages with one coordinator | Preserved behavior, acyclic stage graph, compact facade |
| M231 | Athlete-response model with provenance-aware dose/fatigue/frequency evidence | Historical replay, sparse-evidence restraint, bounded changes |
| M232 | Conservative live autoregulation | Manual/completed rows protected, interruption roundtrip, no false progression |
| M233 | Semantic exercise graph and safe transfer/custom metadata | Compatibility, equipment, avoidance and unknown-metadata safeguards |
| M234 | Unified candidate objective and hard constraints | Determinism, bounded search, immutable candidates, audited final output |
| M235 | Verification 2.0 | Property matrix, 13/13 historical differentials, replay, mutation kills, performance budgets |
| M236 | Verification/release simplification | Unique gate ownership, four contract groups, four browser shards, runtime reachability, exact offline shell |
| M237 | CI supply-chain hardening | Pinned first-party actions, Ubuntu 24.04, locked browser dependency, read-only credentials |
| M238 | Day-scoped progression history ownership | Progression evidence must resolve to the authored program/day before advancing |
| M239 | Day-scoped adaptive-style evidence | Automatic method evidence cannot bleed across unrelated authored days |
| M240 | Cursor/history ownership | Unresolved/imported history cannot advance finite/endless/deload cursors |
| M241 | Adaptive history revision ownership | Persisted history revisions use canonical ownership/provenance rules |
| M242 | Recovery/readiness history revision ownership | Recovery evidence follows the same canonical persisted-history revision contract |
| M243 | Decision-history revision ownership | Trend/lifter-model/overreach/deload decisions consume canonical history revisions |
| M244 | Shell reference-history revision ownership | Latest/reference shell history cannot bypass canonical revision/provenance rules |
| M245 | Standalone export consolidation | Independent package carries canonical engine source only; duplicate UI/reference payload removed |
| M246 | Single engine export authority | `modules/engine-api.js` is the sole export root; `engine-shell.js` is compatibility only |
| M247 | Remove dead UI hash coupling | Standalone coverage no longer reads full UI source as metadata |
| M248 | Remove redundant exporter entrypoint | CI, package scripts and docs call `canonical-export.mjs` directly; forwarding shim removed |
| M250 | Shared repair eligibility ownership | Recovery and functional-coverage repair consume the canonical realizer ranking rules for equipment, barbell caps and primary-muscle identity instead of reimplementing them |
| M251 | Single shell-history lookup | Comparable-day progression history and cross-day starting-reference history share one revision-normalized/filter/sort pass while preserving day-specific progression ownership |

## Current ownership rules

- Production computation authority: `modules/engine-api.js`.
- Compatibility namespace: `modules/engine-shell.js`; it is not a second generator or export root.
- Standalone exporter: `engine-lab/canonical-export.mjs` only.
- Executable verification ownership: `verification/contract-registry.mjs`.
- Release identity: `RELEASE_MANIFEST.json` and `BUILD_PROFILE.json`; assurance-only milestones do not fabricate a new engine version.
- Runtime reachability: every authored production JavaScript module must remain reachable from `modules/main.js`.
- Persisted training evidence: progression, readiness, recovery, trend, overreach and deload decisions must use canonical program/day/revision ownership.

## Maintenance rule

Prefer deletion or consolidation only when ownership is provably duplicated or unreachable. Keep small modules when they own a real boundary, migration, state seam, compatibility contract, or independently testable invariant. Do not create a new gate, adapter, exporter, or engine root when an existing canonical owner can carry the responsibility.

The next behavior-changing engine line must add an explicit differential/replay baseline before replacing the verified M234/M235 behavior checkpoint.
