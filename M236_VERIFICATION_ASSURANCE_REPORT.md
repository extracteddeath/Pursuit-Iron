# M236 — Verification and release assurance

M236 is a post-M235 assurance milestone. It does not change Pursuit Engine behavior, the app version, build number, or engine version. The production runtime remains app 4.0.0 / Build 823 / Pursuit Engine 0.65.6.

## Why this milestone exists

M235 proved the engine candidate. M236 audits the machinery around that proof so the repository does not accumulate duplicate gates, orphaned tests, retired dependencies, unreachable production modules, or stale offline assets.

## Gate ownership

`verification/contract-registry.mjs` is the single owner map for executable regression gates.

- Source/headless contracts are divided into generation, adaptation, quality, and integration groups.
- Browser behavior remains 12 distinct contracts, executed in four balanced shards. Each browser test still runs in its own Node process.
- Release integrity owns only release/package semantics rather than rerunning engine behavior tests.
- The independent engine audit remains intentionally separate because it validates an exported package rather than the application source tree.
- Registry validation fails on missing gates, duplicate ownership, or executable tests with no owner.

`verification/m236-verification-topology-test.mjs` verifies the registry/workflow wiring, read-only workflow permissions, stale-run cancellation, browser shard ownership, release ownership, and dependency-free engine exporter.

## CI efficiency and determinism

All four workflows run automatically on pushes to `main` and on pull requests targeting `main`; feature-branch pushes do not start duplicate suites. `workflow_dispatch` remains available for intentional pre-PR verification. Branch/head-ref concurrency with `cancel-in-progress: true` cancels stale PR or main work when a newer commit supersedes it.

The browser suite now uses four shards instead of twelve separate harness-install jobs while preserving all twelve browser contracts. This reduces repeated Puppeteer installation and runner overhead without reducing behavioral coverage.

The canonical engine exporter no longer installs Babel. The old AST extraction path was retired in M228; `@babel/parser`, `@babel/traverse`, their lockfile, and the audit install step were obsolete.

## Runtime/source ownership

`verification/m236-runtime-reachability-test.mjs` walks authored JavaScript modules from `modules/main.js` using static and dynamic ESM edges. A production module that becomes unreachable fails CI instead of silently remaining in the runtime tree.

Historical M161–M165 text verification reports were removed from the active verification directory because they were unreferenced, non-executable, and already preserved by git history.

## Offline package ownership

The old release finalizer preserved every historical non-JS precache entry forever. M236 replaces that with an explicit derived shell:

- local assets referenced by `index.html`;
- icons referenced by `manifest.webmanifest`;
- `gallery.json`, which the app fetches at runtime;
- `modules/App.js`;
- every certified runtime JavaScript file.

Release verification now requires the service-worker shell to equal that derived set exactly. Missing runtime assets fail, but stale developer documents and legacy icon aliases fail too.

The current shell is reduced to 99 required entries with eight static assets. Developer/release documents and source icon assets remain available in the repository but are no longer pushed into every user's offline cache.

## Release verification

`scripts/verify-release.mjs` now focuses on release responsibilities:

- manifest/profile/cache identity;
- exact offline-shell ownership;
- runtime/UI hashes and aggregate hash;
- app/build/engine identity;
- production JavaScript parsing;
- retired-runtime absence;
- release-specific icon/PWA identity semantics.

Engine, history, progression, custom-program, and other semantic regressions live in the core-contract registry rather than being duplicated inside release verification.

## Remaining repository-governance limitation

The repository currently has no GitHub branch protection or repository ruleset on `main`. The connected GitHub integration has read/write content and PR capabilities but not repository-administration permission, so M236 cannot enforce required status checks at the GitHub settings layer. CI is structured to be suitable for required checks, but repository administration must enable that enforcement separately.
