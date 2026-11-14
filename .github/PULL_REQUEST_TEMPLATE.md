## Owner and scope
- [ ] Identify the canonical data, UI component, CSS selector and interaction owner changed.
- [ ] Preserve Workout/Focus and saved records, or document a specifically tested behavior change.
- [ ] No new duplicated CSS specificity layer, inline static layout or second engine/history computation.
- [ ] Record actual deletions and before/after debt totals.

## Evidence
- [ ] Review populated and empty screens, actions, errors and recovery at 320/393/834/1280px.
- [ ] Check both dark/light, affected themes, enlarged text, keyboard/focus, reduced motion and forced colors as appropriate.
- [ ] If the engine changes, add independent regression, parity/replay and provenance evidence.
- [ ] Keep all ten exact-head CI checks green before merging; never weaken an existing contract.
- [ ] Update `NEXT_APP_UPDATE_PLAN.md` without duplicating roadmaps.

## Rollback and remaining work
State unaffected components, tested compatibility and any outstanding device / Play certification. Browser tests are not physical Android certification.

## Native feature experience (when applicable)
Use the [native design contract](https://github.com/extracteddeath/Pursuit-Iron/blob/architecture/kotlin-engine-rebuild/android-native/PRODUCT_UI_DESIGN_CONTRACT.md#product-cohesion-and-improvement--binding-beta-requirement-2026-11-14).
Describe the athlete's task, the actual web-flow problem being improved, the
feature's canonical home and contextual entry points, and the complete return,
edit/cancel and recovery behavior. Identify shared components and affected
neighboring screens. Link observed evidence; distinguish source tests from
visual/device review and leave unverified outcomes open. Routine native work
must not produce an APK before the full UI is finished.
