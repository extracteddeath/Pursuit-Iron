# M228 — Canonical engine boundary

Engine 0.65.0 / Build 817 integration candidate.

Moved 456 domain declarations from `modules/App.js` to five maintained modules: catalog, records, programs, prescriptions and analytics. The 8,962 lines of computation retain their original expressions. The dependency partitions are acyclic; browser APIs, rendering, persistence I/O, notifications, audio and presentation tokens remain outside computation. React handlers now call imported domain transactions and calculations. App.js is reduced from 29,206 to 20,648 lines.

`modules/engine-api.js` is the production computation API. `engine-shell.js` provides the headless compatibility surface. The application imports this same API; its exported calculation bindings are identical to the canonical bindings. There is no computed copy inside App.js.

The standalone exporter copies canonical modules byte for byte and extracts zero declarations. Coverage schema 2 lists canonical runtime files and public domain exports. Snapshot verification checks their production hashes. Full UI source remains integration evidence. Browser-only wizard initialization is excluded from the standalone runtime.

Thirteen immutable Build 816 goldens cover realized sessions, all projected weekly cells, representative prescription reads, weekly volume and week plans. Only the exact engine-version label is normalized in differential comparisons. Training decisions are not normalized away.

The initial release check exposed two stale M220 assertions that contradicted M226: cross-day LAST and cross-program earned progression. The tests now require a reference-only starting load and no progression from unrelated ownership evidence. Engine behavior was not changed to satisfy those obsolete assertions.

Validation: release integrity; generation, adaptation and quality groups; standalone semantic suites; canonical snapshot/production parity; 13 golden scenarios. All 11 phone/offline browser gates pass, including full-app startup/update, themes, custom/generated workout entry, interruption recovery, cycle duration, Auto-fix, numeric typing and percentage targets. The browser gate caught displaced global/per-exercise loading and gym-inventory setters; their state and writers now share one module, and a permanent functional preference test covers those paths.

This checkpoint does not deploy the application or produce an APK. Browser APIs still belong to UI-side orchestration; the engine remains synchronous. Those are explicit boundaries, not alternate calculation authorities.
