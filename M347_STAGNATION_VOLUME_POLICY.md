# M347 — Stagnation decisions and conservative volume adaptation

M346 detected repeated complete, comparable flat load/reps/RIR exposures. M347 makes downstream decisions **current-evidence-aware**, not cumulative:

- **Resolved plateaus reset:** a new improved exposure clears a stale stall streak, while failure counts remain available as historical diagnosis. An interrupted or noncomparable session also cannot silently maintain high-confidence stagnation.
- **Progression method decisions:** Auto may change a mismatched method after repeated *currently unresolved* stalls, even when a single session was labelled productive; improvements prevent an obsolete change. Explicit user-selected methods remain locked.
- **Volume is a separate causal decision:** six flat, complete exposures are not a controlled dose-response study. A stable plateau sets `volumeReviewRequired` and `reason: plateau_requires_dose_review` without automatically increasing preferred or maximum muscle sets. Repeated comparable failures retain the bounded dose reduction. Fatigue watches and deloads remain independent.
- **Goal protection:** identical plateau evidence cannot silently add sets when switching between strength, hypertrophy, and mixed training priorities.

The new `verification/m347-stagnation-decision-test.mjs` is mandatory in the integration registry and the twelfth lifecycle audit stage; M231's response tests enforce the non-increase contract. The canonical `scripts/finalize-release.mjs` refreshed SW/manifest/runtime hashes, giving installed PWA users a user-accepted update to the new code. The finalizer workflow was removed after use.

Run `node scripts/audit-training-lifecycle.mjs --report verification/engine-lifecycle-audit.json` and the existing 14+ repository CI checks. M347 is a targeted response-policy correction, not a general training-dose model or independent fitness validation.
