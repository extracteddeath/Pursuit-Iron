# M237 — CI toolchain and supply-chain hardening

M237 hardens the verification toolchain after M236. It does not change Pursuit Iron training behavior, app version, build number, service-worker cache identity, or Pursuit Engine version.

## Immutable GitHub Actions

All repository-owned CI workflows now pin reviewed first-party actions to full commit SHAs rather than mutable major tags:

- `actions/checkout` v7.0.1 → `3d3c42e5aac5ba805825da76410c181273ba90b1`
- `actions/setup-node` v7.0.0 → `820762786026740c76f36085b0efc47a31fe5020`
- `actions/upload-artifact` v7.0.1 → `043fb46d1a93c77aae656e7c1c64a875d1fc6a0a`

Checkout uses `persist-credentials: false` in every workflow. The jobs need read access only and do not need credentials left in the repository configuration after checkout.

## Runner stability

Repository workflows use `ubuntu-24.04` rather than `ubuntu-latest`. This keeps the OS family explicit while still receiving GitHub's maintained Ubuntu 24.04 runner-image updates.

Node remains on the supported `22` line intentionally. That continues to exercise the engine/export package at its supported floor instead of only testing a newer runtime.

## Locked browser harness

Browser verification is now an explicit private development package:

- root `package.json` declares `puppeteer-core: 25.12.0` exactly;
- `package-lock.json` is npm lockfile v3;
- the committed lock contains 26 package records;
- non-root package records include registry resolution and SHA-512 integrity;
- the browser workflow uses `npm ci --ignore-scripts --no-audit --no-fund`;
- no workflow-time dependency resolution remains.

The verification package is tooling only. It is not part of the service-worker offline shell or the certified application runtime.

## Browser interaction compatibility

Puppeteer 25 exposed a brittle M215 test helper that relied on triple-click selection to clear a controlled text field. The production duration input still preserves blank and invalid staged text as intended. The test now uses focus/click followed by `Control+A` and `Backspace`, which represents the intended replacement action without depending on triple-click selection behavior.

## Permanent regression gate

`verification/m237-ci-supply-chain-test.mjs` enforces:

- Ubuntu 24.04 runner family;
- only reviewed GitHub Actions dependencies;
- full 40-character action SHA pins;
- the exact reviewed action revisions above;
- non-persistent checkout credentials;
- exact Puppeteer declaration and lockfile agreement;
- npm lockfile v3;
- registry URL + SHA-512 integrity for locked packages;
- `npm ci` rather than workflow-time `npm install`;
- install scripts, npm audit, and funding network chatter disabled during browser-harness install.

The test is owned by the M236 contract registry's integration group, so it cannot silently disappear without failing the verification ownership gate.

## Scope boundary

M237 is assurance/tooling work only. Build 823 / app 4.0.0 / Pursuit Engine 0.65.6 remain the production identity. M235 remains the engine behavior verification milestone; M236 owns verification/release topology; M237 owns the CI dependency/toolchain supply chain.
