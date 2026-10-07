# Independent engine export

The published application remains the engine authority. Export its current code into an independent Node package:

```sh
npm ci --prefix engine-lab
node engine-lab/export-engine.mjs ../pursuit-iron-engine
cd ../pursuit-iron-engine
npm test
npm run example > generated-program.json
```

The package carries all production engine modules and catalogs unchanged, headless computation extracted from `App.js`, independent semantic regression suites, hashes, a dependency/coverage index, and complete boundary source evidence. It has no runtime dependencies and mounts no UI. Read the generated README for the important distinction between headless computation and UI event-handler coordination.

`node engine-lab/verify-parity.mjs ../pursuit-iron-engine` compares exported computation against the actual app source through the app's existing Node import loader. Run this before distributing a snapshot. Changes to shared modules can be copied back file for file; shell changes must be reviewed and applied to the matching source declarations. The exported package is never precached or imported by the running app.
