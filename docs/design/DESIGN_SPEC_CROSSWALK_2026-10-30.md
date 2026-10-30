# Pursuit Iron — design-spec acceptance crosswalk (passes 50–66)

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
