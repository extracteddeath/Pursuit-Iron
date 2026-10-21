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
