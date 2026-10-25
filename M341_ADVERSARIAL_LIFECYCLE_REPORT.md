# M341 — Adversarial engine lifecycle replay

M340 proves a complete ordinary cycle from generation through twelve serialized workouts, adaptation, and final completion. M341 adds one intentionally difficult but genuinely executable generated-program day. It uses the canonical prescription builder, live fatigue system, workout serializer, stored history normalization, next-workout suggestion, and immutable plan. The same exercise is evaluated in all the following contexts:

- Complete all-set ceiling success: verify a legitimate earned increase remains possible.
- Three of four working sets: ensure an incomplete workout cannot advance the working load.
- Mixed loads under fatigue: require conservative, loadable next targets with no accidental promotion of the heaviest exposure.
- Mixed loads with unreported effort: preserve the actual loads at each set position; no falsely confident consolidation.
- Two observed accessory failures, interruption and resume: one bounded reduction of one pending row, persisted recovery-limited provenance, no double-easing or earned progression.
- Unsupported early load reduction after a damaged resume: restore the original prefill rather than treat an orphaned change as evidence.
- User-owned manual row: preserve edited weight through refresh and logging and keep edited evidence noncomparable.
- Corrected historical performance: replay the newest revision exactly once across the evaluator and next-workout LAST.

Run every gate and write a revision-scoped result with:

    node scripts/audit-training-lifecycle.mjs --report /tmp/pursuit-lifecycle-audit.json

The new adversarial source contract also runs under the mandatory integration CI shard. Failures must be resolved in the owning engine/adaptor layer or in an explicitly false test assumption, with a reproducible fixture retained. The scripted replay does not substitute for physical-device interruption certification or browser-based layout checks.
