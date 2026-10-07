# M223 — Icon refresh and independent engine audits

App 4.0.0 build 813; engine behavior remains 0.64.12.

## Installed icon

The live PNG files already contain the new mark. The manifest previously reused unchanged icon URLs and the service worker served the manifest from its app cache. Android's installed WebAPK metadata has its own update schedule; an app-code update alone does not immediately change its launcher icon.

The manifest now points to immutable PNG filenames containing their content hash. Its URL, id, start URL and scope stay stable. Manifest requests prefer the current network response, retain the last good response for offline use, and preserve that response on HTTP/network errors. Favicons and Apple links select the same new PNG files. App storage, saved training data and storage namespace are untouched. Android still controls when it applies the installed-icon update; Chrome's WebAPK Update action can schedule that update without a reset or reinstall.

## Independent engine package

The reproducible exporter copies all 46 production engine modules and catalogs verbatim, extracts 636 shell declarations by lexical dependency analysis, registers the existing equipment expander, and runs with no React, browser or mounted UI. It explicitly indexes the excluded UI bindings and retains the exact full original application source as reference evidence for nested event handlers. Thus no engine source is lost while the package avoids claiming all UI state coordination is already a headless API.

It includes 30 independent semantic suites, source/snapshot hashes, release and commit provenance, a deterministic generation example and application-versus-export parity verification. The snapshot identity verifier is separate from tests so changed engine code can be tested during development. A new read-only CI workflow generates, compares, verifies and audits the package on every main push/PR and supplies an artifact. The export stays outside the production offline bundle.

## Validation

- Isolated import/identity checks: all 46 production modules and 636 declarations available; no React imports.
- All 30 isolated semantic suites passed.
- Application/export comparison passed for exact catalogs/templates, three representative splits, 177 prescription cells, weekly volume and week plans.
- Release integrity passed: 77 precache entries and 52 authored JavaScript files parse, including current custom-progression, input, cycle, history and ownership checks.
- Icon gate passed: content-hash identity, stable installed identity, current online manifest and offline/HTTP-error fallback.
- Local browser installation was blocked by a truncated browser download. Existing phone/offline browser CI remains required; this report does not claim a local browser or physical Android launcher check.
