# M342 — Deterministic multi-week engine stress audit

M340 covers full cycle creation through adaptation and final completion; M341 covers adversarial logging and fatigue. M342 extends this with multiple real engine-generated programs, varied equipment, training days, progression styles, and mixed real-session histories.

## Reproduction

Run the complete mandatory audit:

    node scripts/audit-training-lifecycle.mjs --report /tmp/lifecycle-audit.json

Or replay only the randomized longitudinal gate:

    node verification/m342-seeded-longitudinal-test.mjs

Replay one failing seed and capture the actual saved program and history at the failure boundary:

    M342_SEED=34213 M342_REPRO_DIR=/tmp/pursuit-repros node verification/m342-seeded-longitudinal-test.mjs

All seeds and event schedules are deterministic: 34201, 34207, 34213, 34219, 34223, 34231, 34237, 34243. The audit records the profile, phase/week/day, event, saved workout IDs, and exact replay command on any failure. Optional M342_REPRO_DIR writes the complete fixture. The default contract group executes all seeds on every PR.

## Coverage

Four rotating profiles include restricted dumbbell/band full body, equipment-rich upper/lower, five-day ULPPL, and restricted barbell-oriented full body. Every program executes three weeks with a deterministic mixture of successful sessions, hard/fatigued sessions, incomplete sets, manual edits, bad-day flags, interruption/restoration, and missed days. Real canonical prescriptions and workout serialization feed history and next-workout advice after each completed session.

Assertions enforce: generated-week cell/preview/live-set parity; repeat-read stability; exact logged/analytic working-set counts; unaltered manual or completed data; load protection for interrupted, bad-day and partial evidence; retained original per-exercise IDs; actual mid-workout fatigue reductions where valid; no mutation of saved program/history; no incorrect progression based on noncomparable evidence; and order-independent normalized workout/volume totals.

**Scope:** deterministic randomized regression, not exhaustive proof of all possible training histories. M340/M341 and existing M323 remain authoritative for full-cycle transition and recovery eligibility. Device-specific visual, Android process-death and input cases remain separate.
