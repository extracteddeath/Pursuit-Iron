# Pursuit Iron canonical engine

This package contains the exact production computation used by the app. Import `index.js` for generation, cycle, history and workout APIs, or its `shell` namespace for custom/template compatibility calculations. No React or browser is required. The artifact contains only the transitive production dependencies of the canonical engine APIs plus engine-owned verification; UI and duplicate reference sources are intentionally excluded.

Application parity is verified before publication. Run `npm run verify` for byte-for-byte engine source identity and `npm test` for engine-owned semantic regression gates. Snapshot edits invalidate the hashes; maintain changes in the application repository and re-export into an empty directory. The package is a distribution of that source, not an independent fork.
