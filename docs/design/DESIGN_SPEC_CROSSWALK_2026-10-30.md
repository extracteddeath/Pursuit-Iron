# Pursuit Iron — design-spec acceptance crosswalk (passes 50–200)

**Target authority:** Proposed [Design System & UX Specification v1.0.0](https://github.com/extracteddeath/Pursuit-Iron/blob/docs/pursuit-iron-design-spec-v1-20261029/docs/design/PURSUIT_IRON_DESIGN_SPEC_V1.md) (PR #415); existing `DESIGN_SYSTEM.md` and shipped Build 998 remain production owners until the proposed specification is approved. **Do not infer certification from this crosswalk.**

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


### Passes 119–125 — fixed type roles selected by net debt reduction
- **119 (§§4.1,11.2):** Re-audited the surviving repeated inline declarations after pass 118. Rejected low-value migrations where the newly invented selector would cost more code than the repeated inline styles saved; kept genuinely dynamic `eyebrow()` and button/action styling unchanged.
- **120 (§§4.1.1,7.2):** Converted seven identical muted 13px/1.5-line-height body fragments into one semantic `wpb-type-body-muted` class; conditionals and data remain the same.
- **121 (§§4.1.3,7.3):** Converted five 15px semibold workout/preview values and five 13px bold labels into existing-theme-ink type owners, preserving their `mono` classes and fixed metric widths.
- **122 (§§5.2,7.7):** Converted five identical muted 12px secondary lines and five faint 11px/1.4 rhythm footnotes into shared `wpb-type-subline` and `wpb-type-footnote` owners, preserving the 2px/8px offsets.
- **123 (§§7.2,7.10):** Converted four 13px/1.45 body-copy spans and three muted 13px/1.4 helper lines to `wpb-type-body` and `wpb-type-helper`. No interaction, keyboard, geometry, form, or training state changes.
- **124 (§11.3):** Ratcheted the B983 `style: {` ceiling from 1,037 to **1,003**, forbade all seven prior repeated literal styles, and required all seven semantic CSS owners. Style migration is bounded by an exact 732-character block rather than permitting unbounded growth elsewhere in app.css.
- **125 (§§12,13):** Continued the design-spec crosswalk with source metrics and release dependency; **34 further inline style objects retired**, reaching **92 removed since pass 110** (1,095 → 1,003). `app.css` has 2,024 `!important`, embedded App 1,014. B983's normalized legacy stylesheet ceiling of 637,454 characters remains unchanged, while the combined App/CSS legacy-debt ceiling tightens to 2,668,250.
- **Verification boundary:** Source checks are instrumented; live browser/Android and 14/14 screen rubric remain necessary. Keep this draft batch independent of the generated Build 998 release identity until the parent is fully certified and published. No data, progression, logging, storage, engine or notification changes.


### Passes 126–132 — one theme-owned primary confirmation action
- **126 (§§4,6,11.1):** Reviewed six identical primary-dialog action style objects spanning Program Save, Regenerate, Workout timer reset and exit, and Cycle completion. All six use the same semantic accent foreground/background and identical fixed geometry.
- **127–129 (§§4.1.4,5.2,6):** Replaced six literal 13px-padding / 12px-radius / accent-fill inline button styles with the shared `wpb-dialog-primary-action` class while retaining each button's existing `pressable` affordance and original `onClick`, icons, messages, conditions and confirmation logic. The shared class now ensures a 48px effective minimum hit height, with no new priority overrides.
- **130 (§§10,11.1):** Kept fill/ink sourced from existing `--wpb-theme-accent` and `--wpb-theme-accent-text` rather than hardcoding a dark-mode contrast pair; retained inherited keyboard focus behavior.
- **131–132 (§§11.3,12):** Strengthened B983 to enforce **exactly six** shared action uses, absence of the old 228-character inline action, bounded `B1004` CSS owner, no legacy CSS growth outside named type/button migration blocks, and a tighter **997 inline-object limit**. Cumulative pass-110→132: **98** fewer inline `style: {` objects, from 1,095 to 997; app.css `!important` from 2,027 to 2,024, embedded App priority still 1,014.
- **Source-size outcome:** Relative to pass 125, `App.js` shrank 1,320 characters, app.css grew 395 for one shared owner: combined shipped sources **925 characters smaller**. Relative to pass 110, combined sources are approximately 1,757 characters smaller. This is source-level verification only: confirm visual/action behavior in Chromium at 320px / enlarged text and physical Android before release.
- **Release boundary:** Build 998 is published; passes 111–132 remain on draft PR #421 toward the next approximately 50-pass release. No training engine, automatic progression, load fields, logger, history, program persistence or PR detection changes.


## Passes 111–200 — shared ownership, correctness and candidate handoff

**Scope:** This milestone records UI source consolidation, not a claim that every requirement of the proposed spec is satisfied. The two specification files are carried into this working branch from [PR #415](https://github.com/extracteddeath/Pursuit-Iron/pull/415) without promoting their *Proposed* status to final Android certification. Production remains Build 998 while a Build 999 candidate is prepared.

| Spec principle | Verified source change | Evidence / release limitation |
|---|---|---|
| §§4–6,11: shared semantic styling | Passes 111–157 converted 161 fixed inline objects to shared typography, quiet actions, compare cards and confirmation styles (1,095 → 934). | B983 source budget, themed roles, browser shards on prior exact heads; no claim that all legacy inline geometry is gone |
| §§6,7.3,8,10: understandable interaction | Passes 158–174 share meter/round controls and footnotes; round +/- controls now carry distinct spoken labels. | B983 guard and draft Chromium candidate; pre-existing 44px expanded hit zones still require physical-device review against proposed 48px preference |
| §§4.2,5.2,7.3: contrasting user-selected themes | Passes 175–190 expose live danger foreground and dim tokens; destructive actions share them without changing delete callbacks. | CSS inherits computed palette, no new fixed hex; 20 themes, forced colors, large text and physical Android are separate gates |
| §§7.3,11.2: avoid repetitive set coaching | Passes 191–200 verify per-set load-coaching banners and stale accept handler are absent; no automatic weight change; remove outdated source comment. | B951 progression/PR test verifies manual steppers remain, load advisory data stays separate from display |
| §§12–14: honest readiness | Build identity/offline hashes must be regenerated and checked after source/browser checks; no public launch or 14/14 claim without actual Android/TalkBack/IME/backup acceptance. | Current changes remain draft until all release gates pass |

**Milestone measured source state (pre-final release work):** 919 React `style: {` instances, 2,024 stylesheet `!important` declarations, both bounded by `verification/b983-cascade-debt-test.mjs`. These are debt ceilings, not endorsed layout outcomes. No training engine, PR math, history, logging persistence or program-dose changes were made during the consolidation.

**Remaining release decisions:** preserve one authoritative design contract; evaluate the new candidate against all seven Chromium shards and independent engine audits; run responsive themes and enlarged text; then regenerate Build 999 source hashes, service worker/cache and manifests. Only ship if the exact candidate passes and there is no false progression/PR/weight increment behavior.


## Design-spec passes 201–219 — bounded shared text-paint roles (draft)

- **201–216 (§§4.1.1,4.1.3,11.1):** Converted **45** repeated fixed JSX text-paint objects across Home, Program, Workout, Progress and Settings to **16 shared semantic type roles**. This includes summary labels, compact metadata, captions, comparison emphasis, chart labels and small form labels. Existing label content, class names, callbacks, user entry fields, training state and notification conditions remain untouched. Dynamic data-driven styling remains inline.
- **217 (§§4.1.1,5.2):** Shared CSS uses the saved-theme `--wpb-theme-*` foreground roles and `--pi-type-*` sizing roles. Common declarations are grouped in one small B1009 owner rather than adding repeated selectors or any `!important`.
- **218 (§§11.3,12):** B983 now enforces exact class-use counts, no retired inline objects, <=874 inline style object ceiling, <=2024 stylesheet priority declarations, and a compact 1500-character B1009 owner. The legacy CSS-size ceiling is not relaxed. The combined normalized source budget tightens by 1,002 characters (2,661,215 → 2,660,213).
- **219 (§§12–14):** Draft source-only milestone pending exact-head integration tests, seven Chromium shards, 320px/200% text/theme/IME and physical Android inspection. Build 999 remains live; no Build 1000 identity or PR merge is authorized by this source change alone.
- **Measured at authoring:** inline styles **919 → 874**; app.css `!important` **2024 → 2024**; App.js 2,023,868 → 2,022,866 characters, app.css 642,587 → 643,979 characters; combined source change **+380 characters**. Semantic paint is now shared; no training, persistence, PR logic, backup, engine version or deployed release modification.


## Design-spec passes 220–230 — genuine round controls and compact layout (draft)

- **220–223 (§§5.2,6,7.3):** Program circuit/superset round +/- actions use actual **48px** button dimensions, replacing the 28px visual box plus overlapping global pseudo hit targets. Their original action callbacks and distinct spoken labels stay unchanged. The reset-to-auto button also receives a 48px physical area.
- **224–226 (§§5.2,8,11):** A single named round-row layout owner wraps on narrow phones and enlarged text; the number and action labels remain together without introducing duplicate UI or guessed overlap margins.
- **227–229 (§§10,12):** New B1010 responsive browser gate covers 320/393px at 200% actual font size, physical touch rectangles, nonoverlap, and horizontal page containment. B983 now rejects the old pseudo-hit class, enforces shared class usage and a compact zero-priority B1010 CSS owner; original legacy debt budgets remain in place.
- **230 (§§12,14):** Source-only candidate; the Program shard, all seven Chromium shards, full engine and device gates still need fresh exact-head evaluation. Build 999 stays live; no engine, persistence, progression or history mutation.


## Design-spec passes 231–250 — extend shared semantic typography without new CSS (draft)

- **231–238 (§§4.1,4.2,11):** Reused previously established theme-aware typography roles in **54** additional JSX elements across Program, Home, Workout, Progress, Profile and Settings. The migrated parts are **fixed** color/type/line-height declarations; actual layout geometry, dynamic metrics, state-owned presentation and event handlers remain in place.
- **239–243 (§§4.1.3,5.2):** Shared type tokens replace hardcoded 11/13/15px and theme colors in selected explanatory, comparison, label and numeric subelements. No new CSS class definitions are introduced, and this pass makes no changes to the root theme, engine, training dose, logging or PR state.
- **244–247 (§§11.3,12):** Extended B1009 exact class-use contracts for the migrated roles and tightened B983 normalized App+CSS debt ceiling from **2,660,213** to **2,659,430** characters without relaxing the original stylesheet or priority limits.
- **248–250 (§§12–14):** Source/Chromium exact-head checks, enlarged-text/theme/responsive review and independent full-engine/release validation remain required before Build 1000; physical Android/TalkBack/IME remains separate. This draft does not claim design-spec 14/14 acceptance.
- **Measured:** 54 existing shared-role reuses, **764 fewer characters in maintained App.js**, unchanged 874 inline style objects (geometry/dynamic remnants remain) and unchanged 2,024 CSS `!important` declarations. Existing B1010 nonoverlapping round-target work is preserved.


## Build 1000 candidate — design-spec verification checkpoint

- **Exact source candidate:** `2fdcc2f090686ecd8fa11e5dc195da43829bdcbf` fixed B983's cumulative usage assertions without changing runtime code. The corrected candidate passed the complete 98-file integration source-contract suite.
- **Chromium evidence:** the same source candidate passed all seven browser shards (lifecycle, workout, program, integration, integration-motion, integration-accessibility and integration-data) in draft UI run `41123218314`.
- **Version identity:** the controlled finalizer committed Build 1000 at `3717233bf6d7ac599d489d5fa4272961fd737e91` with refreshed release identity. The source-first contract and browser results do not, by themselves, certify the release build or the hosted/offline assets.
- **Next gate:** require the full release, core, independent engine, phone/offline and hosted-integrity workflows to pass on the finalized exact head before merging. Physical Android/IME/TalkBack and launch-blocking prescription lifecycle issue #418 remain separate requirements.


## Post-Build 1000 consolidation — Progress status owner (candidate)

- **Actual consolidation:** equivalent up/down state paints for body measurements, trends, per-muscle volume changes and lift changes now share a small number of class-preserving :is() selectors. High-volume record warning inherits the same warning treatment; expanded history/measurement chevrons share one rotation rule.
- **Visual refinement:** numeric status deltas use tabular digits for steadier alignment. No changes to the data values, direction classification, log/history, training prescriptions, or program state.
- **Measured source:** app.css 644,317 → 643,872 characters (**445 fewer**) after a second pass that also shares Profile achievement/trend section separators and replaces fixed metric captions with the canonical responsive caption token. Unchanged stylesheet !important count of 2,024. No React inline-style objects added. No new global overrides or root selector hacks.
- **Verification:** separate Progress/Profile source role contract and a real Chromium 320/393px 200%-text test for color roles, tint, chevron state and numeric typography. Full exact-commit browser/source/engine and offline gates remain prerequisites to release; no production build increment in this branch.
