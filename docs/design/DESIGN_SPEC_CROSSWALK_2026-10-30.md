# Pursuit Iron — design-spec acceptance crosswalk (passes 50–118)

**Target authority:** Proposed [Design System & UX Specification v1.0.0](https://github.com/extracteddeath/Pursuit-Iron/blob/docs/pursuit-iron-design-spec-v1-20261029/docs/design/PURSUIT_IRON_DESIGN_SPEC_V1.md) (PR #415); existing `DESIGN_SYSTEM.md` and shipped Build 997 remain production owners until the spec itself is approved. **Do not infer certification from this crosswalk.**

| Design-spec requirement | Implemented improvement | Verification / evidence | Acceptance status |
|---|---|---|---|
| §5.1 + §11.1 one ownership layer | Shared chrome, corners, surface, animation for six Program sheets (passes 50–55) | `verification/ui-ownership-test.mjs`, `b992-program-sheet-focus-browser-test.mjs` | Source guard added; browser CI required |
| §5.2 + §6 controls ≥48px and deliberate responsive reflow | Plan filter chips raised 44→48px; week pager uses shared `UiAction` (passes 63–64) | `b996-program-ux-consolidation-browser-test.mjs` at 320px, 200% text | Pending exact-head browser check |
| §6 Search/filter has visible clear, selected chips, result count | A reserved 48px clear button appears only when a query exists; field does not jump as value changes (pass 60) | Browser checks visibility, size, text, result count | Pending exact-head browser check |
| §6 Empty state has one explanation and one next step; §9.3 recovery | No-match state shows **Show all lifts**; genuine empty program does not falsely offer reset (passes 61–62) | Browser checks no-match→reset; source checks true empty state | Pending exact-head browser check |
| §7.2 Plan week browsing must not mutate saved program | Pager keeps week scope and changes only local browsing state; bounds clamped (pass 65) | Browser tests weeks 1–2→2–3→3–4; existing engine provenance contracts | Pending exact-head browser check |
| §8.2 + §10 accessibility, motion and contrast | Existing sheet focus/restoration and shared themed buttons retained; new controls carry accessible names/disabled states | Full 7-shard CI; forced colors/reduced motion; device test separately | Pending exact-head browser check |
| §11.2/§11.3 measured CSS debt | Removed duplicate sheet/action typography and pager button geometry; new search CSS is tracked by **two exact named exceptions**, not a general ceiling increase | B983 debt budget and zero-growth `!important` ceiling | Source guard added; browser CI required |
| §14 0/1/2 rubric | Review must score purpose, layout, consistency, motion, training trust, accessibility and resilience individually | Browser + device evidence before 14/14 claim | **Not yet scored**; no certification claim |

**Visual improvement criterion:** a real recovery affordance and roomier, consistent touch targets—not a CSS-only reshuffle. Focus ring, title, and card hierarchy must remain legible at compact 320px and 200% text; all search, week and filter behavior stays view-local. **No engine/load/persistence change.**

**Release gate:** Keep this work on a draft PR until source and seven Chromium shards pass. Release identity and offline cache must be regenerated only after UI verification; physical Android/TalkBack/IME sign-off is separately required for public launch.


## Passes 67–73: Plan review comprehension and verified scope

| Spec clause | Concrete change | Evidence | Status |
|---|---|---|---|
| §7.9 What changes | One-set load comparisons now appear directly on Plan change cards, with no disclosure required. Multi-set comparisons remain grouped under native details; never duplicate the same comparison twice. | B999 source + 320px/200%-type browser asserts 180→185 lb before opening details | Await exact-head CI |
| §7.9 Why | Short disclosure **Why this change?** preserves explainable source and supporting details without crowding the initial view. | Existing B999 disclosure/keyboard and new direct-before-drawer checks | Await exact-head CI |
| §7.9 When/where | Plan review says **Upcoming workout only**; Program rebuild says **After you choose Apply changes**. | Source contracts and B999 browser text check | Await exact-head CI |
| §7.9 What stays intact | Plan review expressly names completed history, Program rebuild names completed workouts and the restorable previous layout. | B999 source + existing revision snapshot contract | Await exact-head CI |
| §§6,9.2,14 Purpose/clarity | Rebuild now shows a compact, four-part impact summary, based on the actual `reviewProgramChanges().count`, rather than repeating a generic paragraph. | B999 source guards; existing Program revision browser checks | Await exact-head CI |

**Bounded scope:** Presentation-only. The Plan `onChoice` delegate and Program rebuild `onApply` action, snapshot and failure handling are unchanged. Do not claim a 14/14 score without images/Android testing.


**Passes 74–76 verification upgrade:** B999 browser fixture now mounts the actual `ProgramRevisionReview` with a known goal-setting delta. At 320px and 200% text, it asserts all four impact layers are visibly present and not horizontally clipped, the computed count reflects the diff, focus is correctly trapped, and exactly one Apply callback fires. This tests the §7.9/§10 interaction rather than just naming CSS selectors. Until that exact-head job finishes, evidence remains pending.


**Passes 77–79 truthfulness correction (§§7.9,9.2,10):** The canonical `sessionPlanChange` distinguishes `setDelta` (saved program revision provenance) from a reversible next-workout load choice. Plan review now displays the corresponding scope honestly, and a single visible comparison is labeled **Last → Upcoming** without duplicating the same weight summary. B999 source/browser checks enforce this distinction. User data and engine classification remain untouched.


## Passes 83–89 — truthful mixed-change reviews and direct contract repair

| Spec clause | Improvement | Concrete check | Status |
|---|---|---|---|
| §7.2 / §7.9 | A Plan banner now names **saved sets**, **workout loads**, or **both** from the real review record; never shows both categories by default | B999 source guard checks all three labels; no change to review event/persistence | CI on exact final head required |
| §7.9 / §9.2 | Plan scope text varies with actual `setDelta` and `loadChanged`; a saved-only edit is not mislabeled as a workout load choice | B999 browser opens load-only, saved-set-only, and combined cases at 320px/200%-sized text | CI on exact final head required |
| §6 / §7.9 | If both set count and load change, the saved set delta remains visible; optional detailed load comparison stays inside native disclosure | B999 tests 3→4 planned sets remains visible and load details are readable on expansion | CI on exact final head required |
| §9.3 / §10 | No fake Undo offered for saved set edit; combined non-restorable load explains separate consequences | B999 source and browser tests focus restoration + one onChoice callback | CI on exact final head required |
| §12 | Repair stale source guard left by the previous wording pass; use source assertions that match the intended new semantics | `verification/b999-plan-review-m3-test.mjs`; monitor source-contract job | CI on exact final head required |

**Source of truth:** `modules/session-plan.js` supplies distinct `setDelta` and `loadChanged` flags. These passes only change text and when existing presentation branches render. Previously logged values, plan choice callbacks and actual program persistence are untouched; no extra CSS or priority declarations.


## Passes 90–98 — Home/Game Plan scanability (spec §§6,7.1,7.3,7.9,9.2,10–12)

| Requirement | Implemented improvement | Verification | Status |
|---|---|---|---|
| §7.1 concise, optional Game Plan | A collapsed row shows its effective target, with only a brief method label when relevant, rather than a full coaching paragraph | B998 Chromium at 320px/200% text | Exact-head CI required |
| §§6,9.2 expanded content must add information | Expanding a uniform prescription shows its rationale, not a duplicate of the target already visible | B998 tests 185 lb × 8 in summary and distinct reason in detail | Exact-head CI required |
| §§7.1,7.3 truthful heterogeneous sets | Compact row says **3 sets · 50 lb × 12 first**; expanding reveals all three actual effective set targets (50/45/40 lb) | B998 validates ordered full targets + source guard | Exact-head CI required |
| §7.3 special method comprehension | **Myo-reps** / **Drop set** is a short collapsed label; full technique/rest instruction stays available when expanded | B998 checks rest detail and native Enter/close behavior | Exact-head CI required |
| §§7.9,9.2 consequence scope | A Plan banner uses **plan adjustments**, not a false “upcoming” label for already-saved set edits; its supporting line correctly distinguishes saved sets, load choices, and mixed changes | B998 three banner cases and B904 full-app lifecycle | Exact-head CI required |
| §§5,10 keyboard and responsive stability | Existing themed summary controls (≥48px) and no horizontal overflow at 320px/200% text; no new action or CSS owner | B998 browser and Home entry hierarchy | Exact-head CI required |

**Source of truth:** Live `review.exercises[].effective`, `protocol.type`, `opportunity` and classified `changes`, never a second prescription model. All changes are within the shared `modules/plan-session-surfaces.js` presentation owner, with **no app CSS, motion, engine, history or persistence changes**. CI and physical-device evidence remain distinct requirements.


### Passes 102–106 — remaining action touch targets (spec §§4.1.4,5.2,6,7.3,10,12)
- **102:** Workout's pinned-note edit action now has a 48px minimum in both its theme-scoped and shared declarations (previously 44px); no new override or note behavior change.
- **103:** The actual Workout Info **Why** disclosure summary now has a 48px minimum instead of 44px. Existing native disclosure, focus/close, explanation and rest writers are unchanged.
- **104:** Gallery filter and retry actions are 48px tall, matching the already-48px import action. All original filter, offline retry and import guards remain unchanged.
- **105:** Expanded B1000/B993 browser and source checks to measure pinned-note and explanation summary geometry at enlarged text, while preserving the existing navigation and Info keyboard journey.
- **106:** Corrected the B988 no-`!important` guard to inspect only the Gallery's own CSS region, not every later module's stylesheet, and ratcheted exact chip/retry/import minima to 48px.
- **Scope:** Five CSS declaration values changed from 44px to 48px, with no CSS byte growth, new `!important`, inline styling, engine/logging/state changes or build bump. Keep draft PR #420; run full exact-head CI and real Android visual/IME checks before merge or release. This is not a screen-level 14/14 certification.


### Passes 107–110 — one visual-viewport height owner for Workout Info
- **107 (§§5.1,5.3,7.3):** Root-cause analysis of the 320×650 Chromium failure confirmed the draggable-sheet runtime's global `!important` max-height overrides were masking the dedicated Workout Info 90dvh cap; the sheet could retain the previous 850px viewport ceiling after rotation/resize.
- **108 (§§4.1.5,11.1):** Existing global compact/large-detent and tablet max-height owners now expressly exempt `.wpb-exinfo-sheet`, retaining its drag gesture and delegated dismissal while giving its existing `app.css` rule sole height authority. No new `!important`, component, token, or overriding CSS layer.
- **109 (§§5.2,10):** The short-landscape override also exempts Workout Info; the responsive B993 browser test continues asserting real 320px and 200% text containment, modal focus trap, rest controls, Escape and focus restoration, with computed-height diagnostics on failure.
- **110 (§§11–12):** Added an exact source contract for all five legacy ceiling exclusions, removed a redundant embedded stylesheet comment, and preserved the combined App/CSS debt ceiling. Release staging for PR #420 is explicitly gated on all eight draft UI jobs; never bump the build before that gate.
- **Release decision:** These passes complete the ~50-pass batch from production Build 997 (design passes 50–110). Stage Build 998 only after exact-head eight-job success, then run full engine, release, offline/host integrity before merging. Physical Android remains a separate acceptance task; no hardware certification claimed.


### Passes 111–118 — shared type ownership and safe dead-rule retirement
- **111 (§§4.1,11.1):** Counted duplicate literal inline text-paint objects in the maintained `modules/App.js`, distinguishing fixed colors/type from genuinely dynamic load or state styling. Previous baseline at pass 110: 1,095 inline `style: {` objects, 2,027 app.css `!important`, 1,014 embedded `!important`.
- **112 (§§4.1.1,4.1.3):** Replaced 19 identical faint separators and labels with a single reusable `wpb-type-faint` owner, reading the existing semantic palette rather than a hard-coded color.
- **113 (§§4.1.3,6):** Replaced 13 repeated 13px muted support labels and 11 faint 11px captions with `wpb-type-support-muted` / `wpb-type-caption-faint`, including existing mono and attributed spans without losing their classes.
- **114–115 (§§7.2,7.7,7.10):** Replaced nine repeated muted 13px section introductions and six 15px confirmation explanations with `wpb-type-section-intro` and `wpb-type-confirm-copy`. Copy, dialogs, actions, callbacks and render conditions are unchanged.
- **116 (§11.2):** Removed two completely empty legacy expressive theme selectors (including one in the <350px breakpoint), which contributed no declarations.
- **117 (§§5.2,8.2,11.1):** Removed an expressive tablet border-radius override identical to its unconditional owner, an unnecessary compact set-input gap duplicate, and a reduced-motion exercise-nav transition rule already owned by the earlier shared reduced-motion selector. The effective owner stays in place for each.
- **118 (§§11.3,12):** Strengthened B983 source gate to protect the five new type roles, prevent reintroduction of the five original repeated inline styles, hold the new 1,037 inline-object ceiling and 2,024 app.css priority ceiling, and forbid the dead tiny-screen selector.
- **Measured net:** 58 inline style objects removed (1,095→1,037, −5.3%); 3 stylesheet `!important` occurrences removed (2,027→2,024); maintained App.js shrank 524 characters and app.css shrank 33 characters; embedded `!important` unchanged at 1,014. This is a **source-level** design-spec consolidation, not a visual 14/14 acceptance or physical Android certification.
- **Release boundary:** Changes are on a separate stacked candidate based on pass 110. Do not move these into Build 998's exact-head verification, and do not merge this child ahead of the Build 998 release identity. Rebase/resync after the parent release and run the full UI/engine/release/offline gates before Build 999. No engine, load progression, workout logs, storage or PR detection changes.
