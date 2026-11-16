# Build 1002 candidate — Settings fields and routed UI verification

Baseline: published Build 1001 on `main` (commit `18661287b96d734d7dc7d8527322c013230c78b7`), engine `0.65.9`.

## Cohesive changes

- Port the remaining Settings-only UI semantics from validated pre-B1001 pilot #425 onto the **current** Build 1001 sources rather than merging the divergent branch. The App.js replacement is constrained to `SettingsView`; outside that function the original B1001 code is byte-identical.
- Replace duplicate inline static theme/border/typography paint on the reminder-time, bodyweight and birthday inputs with the already-existing `wpb-ui-control` role. Keep native input types, unit-aware labels, change callbacks, compact width and birthday estimation border/warning.
- Use the shared UiAction text variant for the Gyms navigation row inside its existing card; preserve the one-card surface and original handler.
- Preserve unit-scoped smallest-jump semantics via the published B1001 `UiSegmented` `value` prop. A valid nonzero increment stays scoped to the unit that owns it, rather than displaying an invalid option after a unit change.
- Adopt the live routed Settings/Gallery browser check from #425, including 320, 393, 834, short-landscape 900 and 1280 widths, both themes, 200% text, named groups, keyboard/touch geometry, actual filter toggle/reset and real screenshots. The original stale ElementHandle filter assertion has been replaced by a live-DOM assertion.
- Register that browser check in the existing integration-accessibility shard. Keep all B1001 design owner, engine, release, offline and source contracts.

## Measured source boundary

App.js: 2,020,507 -> 2,020,233 characters (-274). Its non-Settings bytes remain identical. No app.css change, no new CSS priority declarations, and no training engine/storage mutation. Unlike repeated pass counting, this is one complete native-field component family with routed visual verification.

## Acceptance gates

1. All integration source contracts and seven Chromium browser groups on the exact PR head.
2. Monotonic Build 1002 finalization against Build 1001, with runtime, offline and service worker identities regenerated together.
3. Independent engine audit, release integrity, browser/phone/offline and hosted deployment workflows after build finalization.
4. Subjective screenshot review and physical Android/TalkBack/keyboard testing remain outstanding; **not** a 14/14 hardware certification.

Independent progression issue #418 remains a launch blocker; this pass does not alter prescription decisions.
