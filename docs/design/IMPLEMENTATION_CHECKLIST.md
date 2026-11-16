# Pursuit Iron — Design Spec v1 implementation checklist

This checklist is a worklist, **not a claim of current status**. Every checked item needs a linked code change, test and screenshot (where visual). Project baseline: Build 989 / `cf8f1f8fffc2429b8bdbb8866856296266f1683e`.

## Foundations
- [ ] Verify every existing theme's computed canvas/card/control/active/on-color contrast; preserve theme identities.
- [ ] Validate actual token values and use `--pi-*` across all shared components.
- [ ] Lower current `ui-ownership-test.mjs` debt ratchets without increasing duplicate priority owners.
- [ ] Verify 48px general / 52px primary controls with 320px and 200% text.
- [ ] Confirm common root header geometry and Back/focus semantics across all five tabs.

## Daily training
- [ ] Home has one next-workout (or Resume) hero and separate concise Game Plan.
- [ ] Target/Last toggle does not move logger; no silent typing or load overwrite.
- [ ] Confirm true PR fire-once behavior and suggested (not automatic) DP reductions.
- [ ] Finish/Save/Undo remains visible above dock, keyboard and notices.
- [ ] Exercise top tags/actions aligned; compact names abbreviated only in navigation.
- [ ] Focus retains plate calculator, notes, target/last, rest and amplifier functionality.

## Planning and browsing
- [ ] Compare selected/current/future week scopes consistently on Home/Plan/Preview/Workout.
- [ ] Verify adaptive cycle previews only promise saved executable exercises compatible with gym.
- [ ] Verify custom-to-cycle preserves authored weekly set intents.
- [ ] Library has one sticky toolbar; search and filters scroll with results on compact phones.
- [ ] Program edits preserve raw invalid input until explicit correction; system Back respects transaction guard.

## Review, identity, preferences
- [ ] Progress shows recent records before calendar at compact widths.
- [ ] History edits preserve attribution, completed sets, Undo and revision order.
- [ ] Profile share-card morph remains stable, no square flash or layout jump.
- [ ] Settings groups remain named, with clear current values and reversible edits.
- [ ] All dialogs trap and restore focus, survive rapid reopen and short landscape.

## Motion, accessibility, resilience
- [ ] Only `modules/ui-motion.js` owns spatial spring properties; no competing CSS motion.
- [ ] Respect reduced motion dynamically, including during an active workout.
- [ ] Test keyboard, focus, screenreader semantics, forced colors, no color-only states.
- [ ] No critical clipping at 320/393px, 200% actual text, 900x480 short landscape, 834/1280.
- [ ] Pass source, full engine, lifecycle, browser shards, release/offline identity, hosted-file parity.
- [ ] Conduct separate real Android IME/Back/TalkBack/interruption/foldable checks before claiming device readiness.

## Definition of done
Each area scores **14/14** across Purpose, Layout, Components, Motion, Training Trust, Accessibility and Resilience, with no P0 blocker. See Section 14 in the main specification.