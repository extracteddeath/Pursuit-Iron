# M234 — Candidate selection hardening

Engine 0.65.6 / Build 823 integration candidate.

Candidate selection now has one shared boundary for static eligibility, hard constraints and bounded ranking. Static session eligibility is cached against the canonical exercise catalog without projecting exercise definitions, so prescription, advanced-technique, provenance, semantic and future metadata remain available to every downstream stage.

The strength and hypertrophy ranking authorities retain M233 ordering for normal catalogs. Hard constraints are evaluated before ranking. If a pathological custom catalog exceeds the 512-candidate evaluation ceiling, only the expensive scoring pass is bounded; a cheap deterministic pre-rank selects the shortlist first. The source catalog is the cache owner through a WeakMap, preventing cross-realization durable state and allowing garbage collection with the catalog.

M230 decomposition remains enforced. Only the two intentionally evolved ranking snapshots were refreshed; every other preserved M230 realization fragment remains byte-certified.

Verification adds `verification/m234-candidate-optimization-test.mjs` and keeps the prior M228–M233 semantic gates in the permanent adaptation group.
