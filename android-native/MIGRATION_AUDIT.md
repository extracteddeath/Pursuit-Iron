# Migration audit — first source inspection (2026-10-31)

## Confirmed existing boundaries
- Web entry: `modules/main.js`; React UI is in the modules tree, including the very large `modules/App.js`.
- Canonical training computation: `modules/engine-api.js`, with `modules/engine-shell.js` compatibility and `modules/next-engine/` + `modules/training-domain/` implementation.
- `engine-lab/canonical-export.mjs` already exports the canonical JS dependency closure and `engine-lab/verify-parity.mjs` compares standalone and production outputs.
- Existing persisted records and migration logic: `modules/training-domain/records.js`. Web persistence and backup formats must be inspected and frozen before native import.
- Existing `play/README.md` describes a *planned* Trusted Web Activity, not a current Kotlin/Compose Android app. Android package identity/signing are not finalized.

## Explicitly not yet established
- Exact production backup schema and all revision/migration invariants.
- JS engine runtime compatibility on Android (WebView sandbox, JS engine, or future Kotlin port).
- Gradle/Android SDK compile success; no Android build has been executed.
- Real-device font scale, TalkBack, keyboard/IME, reduced motion, edge-to-edge, predictive back and interruption certification.

## Non-destructive migration rule
Do not modify `modules/engine-api.js`, `modules/training-domain/`, `modules/next-engine/`, `app.css`, web manifest, release files or service worker as part of this scaffold. Native data starts empty and read-only until explicit import approval and parity gates.

## Next implementation pass
1. Inventory the backup serializer/deserializer, schema versions, revision ownership and full engine export API.
2. Define a typed JSON bridge protocol with fixture digests and errors; verify parity in Android runtime before showing actual training prescriptions.
3. Add test-only backup import validation, with hash and schema checks; preserve the original file.
4. Replace the placeholder Workout with a real native vertical slice driven by the certified bridge.
5. Add CI Android compile and instrumentation tests; review current official Gradle/Kotlin/Compose pins before release.
