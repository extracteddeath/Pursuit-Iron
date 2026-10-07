# Canonical independent engine export

The application and standalone package share `modules/engine-api.js` and the maintained production domain modules. Export copies those modules unchanged; there is no AST declaration extraction or generated calculation fork.

```sh
node engine-lab/canonical-export.mjs ../pursuit-iron-engine
node engine-lab/verify-parity.mjs ../pursuit-iron-engine
cd ../pursuit-iron-engine
npm run verify
npm test
```

Choose an empty output directory outside the repository. The package has no runtime dependencies. Coverage lists the copied canonical source, public domain exports, release identity and hashes. UI source and browser/source integration suites remain in the application repository; parity checks verify the standalone package against that source before publication.
