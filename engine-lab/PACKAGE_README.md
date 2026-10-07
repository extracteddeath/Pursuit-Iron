# Pursuit Iron independent engine audit package

Use Node 22 or newer. Runtime and tests require no browser, React mount, user account, or private workout data.

```sh
npm test
npm run verify
npm run example > generated-program.json
```

M228 makes the audit boundary the production boundary. `modules/engine-shell.js` is now committed application source; `modules/App.js` consumes it instead of owning a second copy of headless engine/domain coordination. The export copies that exact module plus every production `modules/next-engine/*.js` file byte for byte.

`coverage.json` records the canonical shell hash and export surface, the engine module list, release identity, source commit, and full `App.js` reference hash. `reference/App.production.js` remains integration evidence only; the exporter no longer parses it to reconstruct engine behavior.

`npm run verify` checks snapshot hashes, exact production module identity, the canonical shell hash, the absence of React/lucide dependencies from the shell, and that App has one engine boundary with no direct `next-engine` imports. `npm test` runs the compatible semantic suites against the same committed shell used by the app.

Edit the canonical engine modules directly, then run the isolated semantic suites and application integration/browser gates before release. There is no generated headless fork to port back into `App.js`.

The history, percentage, progression, volume, topology, realization and request-normalization modules remain authoritative under `modules/next-engine/`; the shell owns the remaining app-independent coordination needed by both the application and the standalone audit package.

From the application repository:

```sh
npm ci --prefix engine-lab
node engine-lab/export-engine.mjs ../pursuit-iron-engine
```
