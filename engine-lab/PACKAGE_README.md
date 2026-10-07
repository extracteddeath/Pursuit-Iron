# Pursuit Iron independent engine audit package

Use Node 22 or newer. Runtime and tests require no downloads, React, browser, UI, or user account.

```sh
npm test
npm run verify
npm run example > generated-program.json
```

`index.js` exposes generation, cycles, progression, runtime targets, and a `shell` namespace for the exact extracted prescription/template/history/loading/volume computations. Every production file in `modules/next-engine/` is copied byte for byte, including the entire catalog and all adapters. `modules/engine-shell.js` includes all top-level production declarations whose dependencies do not include the UI framework. Production equipment registration is executed after extraction.

`coverage.json` lists every included/excluded top-level declaration and its original source range/dependencies. UI-dependent declarations are identified explicitly. The exact full production `App.js` remains in `reference/App.production.js` for auditing nested event handlers and the integration boundary; it is never loaded by the headless runtime. This is necessary because the application has not yet moved all state coordination out of UI event handlers. The package contains 100% of the original engine source and boundary evidence; this does not assert that every UI event handler has become a headless API.

`npm test` checks full module imports and all included existing semantic suites, including after deliberate edits. `npm run verify` separately checks snapshot hashes and exact production-module identity. Browser/source-text integration suites remain unchanged under `reference/verification/`; they still run in the application repository. Their original assertions have not been weakened to fit the package. No private workout data is included.

Edit `modules/next-engine/*.js` to audit or improve the engine directly. For improvements to extracted shell computation, use `coverage.json` and `reference/App.production.js` to locate the exact production declaration and port the reviewed change back to `modules/App.js`. Run the isolated semantic suites and the application's integration suites before release. Never copy the generated headless file wholesale over `App.js`.

`history-contract.js` defines physical units, numeric evidence, unique history revisions and additive session/exercise/set context. The ordinary and tiered evaluators use the same exposure rules. `shell.loggedWorkoutPerformance` is the app's pure log serializer; recovery, edit and interruption flags survive its output. `percentage-protocols.js` supplies percentage waves and history-derived tiered progression; the accepted prescription owns the executable set budget. The M224 repair guide and M225 history report describe these boundaries and their limitations.

This is a reproducible export, not a second engine authority. From the application repository:

```sh
npm ci --prefix engine-lab
node engine-lab/export-engine.mjs ../pursuit-iron-engine
```

After a deliberate change, the original snapshot verifier reports modified bytes, while `npm test` continues to exercise the changed engine. Regenerate the reviewed package from production to certify a new snapshot. Source commit, dirty-state marker, build and engine version are recorded; release metadata describes the original application rather than pretending the UI runs in this package.
