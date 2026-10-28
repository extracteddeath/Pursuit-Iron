# Build 953 — Final readiness gate

## What changed
- Fixed excess visible space around the workout name, set context, progression label and action strip.
- Kept full 48px hit targets for normal Workout and Focus controls; compact spacing must not reduce interaction safety.
- Preserved training engine version 0.65.9, double progression's per-set history and opt-in fatigue reduction, PR events and persisted workouts.
- Certified source hashes and the offline service-worker cache for Build 953 with scripts/finalize-release.mjs.

## Required release gate
- Exact-head Release integrity, Core contracts, Independent engine audit, Hosted webapp integrity and all seven Phone and offline integrity browser shards must pass.
- Confirm B896 workout geometry and R23/R37/R54 48px interactive targets in light/dark themes.
- After merge, verify that main's build and hosted app/offline worker report 953; do not count branch-only results as live deployment.
- Separate physical Android keyboard/IME, interruption/resume and TalkBack checks remain for device certification.

This note intentionally is outside the service-worker shell; it does not alter release bytes or saved data.
