# Migration audit — source inspection (2026-10-31)

## Confirmed existing boundaries
- Web entry: `modules/main.js`; React UI is in the modules tree, including the large `modules/App.js`.
- Canonical computation: `modules/engine-api.js`; `modules/engine-shell.js` is compatibility, and `modules/next-engine/` + `modules/training-domain/` own domain implementation.
- `engine-lab/canonical-export.mjs` already exports canonical JS dependency closure; `engine-lab/verify-parity.mjs` compares standalone and production.
- Existing records and migration logic: `modules/training-domain/records.js`.
- Backup panel `modules/backup-restore-panel.js` explicitly says the App callbacks own persisted training, merge, compatibility and Undo decisions. `modules/app-storage-ownership.js` implements browser-exclusive storage ownership; Android must not bypass this.
- Existing `play/README.md` describes a planned Trusted Web Activity, not an existing Kotlin/Compose app.

## Not yet established
- Exact production backup serializer/schema and every revision/migration invariant. Read-only structural inspection is not schema certification.
- Android runtime execution of canonical JS and matching outputs for workout operations.
- Android SDK/Gradle compile success, and physical-device font scale, TalkBack, IME, reduced-motion, edge-to-edge and interruption certification.

## Non-destructive rules
Do not modify web engine, web storage, `app.css`, manifest, release or service-worker files as part of native foundation. Native storage stays empty until explicit import validation. Never print real backup record values in logs or tests.

## Next pass
1. Trace web App backup serializer and restore handlers; record schema and compatibility rules.
2. Implement isolated Android JS execution and a pinned canonical source digest.
3. Add independent golden fixtures for weight increments, DP, RIR, cycles, workout history and revision identity.
4. Build the first real Workout vertical slice after bridge certification.
5. Add Android compile and instrumentation CI, then verify on a physical Android device.

## Additional contract coverage

The separate native foundation now runs synthetic canonical v13 round-trip, correction/tombstone and chronology regressions. The read-only progression bridge also exercises actual JS decisions through deny-by-default input validation. Neither is equivalent to an embedded Android JS runtime, transactional native persistence or an approved backup importer. Keep all native writes disabled until those gates pass.
