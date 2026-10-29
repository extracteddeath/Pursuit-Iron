---
title: Pursuit Iron — Product Design System & UX Specification
version: 1.0.0
status: Proposed authoritative design contract (not a certification of implementation)
date: 2026-10-29
baseline_repository: extracteddeath/Pursuit-Iron
baseline_commit: cf8f1f8fffc2429b8bdbb8866856296266f1683e
baseline_label: Build 989
owners: Product design + UI implementation + verification
---

# PURSUIT IRON
## Product Design System & UX Specification

**Version 1.0.0 | October 29, 2026 | Android-first responsive PWA**  
**Design intent:** *Purposeful strength training, one coherent interface.*

> **Status and authority.** This document defines the **target standard**, not a statement that every requirement is implemented. It consolidates user-approved direction and the existing repository contract as of **Build 989**, commit `cf8f1f8fffc2429b8bdbb8866856296266f1683e`. Existing production behavior remains the source of truth until a requirement is implemented, tested, reviewed, and merged. The current `DESIGN_SYSTEM.md`, `UI_INTERACTIONS.md`, `NEXT_APP_UPDATE_PLAN.md`, `modules/ui-motion.js`, and `verification/ui-ownership-test.mjs` remain implementation references. Where this spec introduces a new threshold or rule, it is marked **target**. Do not replace a working implementation merely to make a diagram visually identical.

**Applies to:** Home, Plan, Program, Cycle, Workout, Focus, Library, Progress, Profile, Settings, dialogs, calculators, onboarding, backup/restore, and adaptive layouts.  
**Does not authorize:** Changing exercise prescriptions, auto-editing workout values, replacing history, introducing a new UI library, redefining progression logic, or claiming a mobile-device certification that has not been performed.

---

# 1. Design charter

## 1.1 Product promise

A strength-training interface that helps a lifter **choose a feasible plan, understand the next session, log accurately, and verify real progress** without surprise modifications. The interface must feel premium, calm, legible, tactile, responsive and trustworthy, especially under fatigue, at a crowded gym, with one hand, and without connectivity.

## 1.2 Non-negotiable product principles

1. **One dominant job per screen.** The primary action is unmistakable; related data stays together; secondary detail is disclosed only when useful.
2. **Recorded facts outrank animation.** Weight, reps, effort, rest clocks and program/history state commit synchronously and must never depend on an animation finishing.
3. **The user remains the editor.** Recommended weight changes, injury adjustments, swaps and cycle adaptations are clear proposals with scope, review and Undo when applicable; typed values are not silently overwritten.
4. **Density without crowding.** Compact gym controls are allowed; hit targets, readability, group separation and labels are not sacrificed.
5. **Motion explains relationships.** Use Material 3 Expressive principles to reinforce hierarchy, selection and continuity; no decorative layout jumping around numeric entry.
6. **Theme fidelity.** Preserve the current chosen theme and existing tinted surfaces; the spec defines semantic roles rather than a mandatory new dark purple or gray palette.
7. **Accessibility is a feature.** Support keyboard, screen readers, high contrast/forced colors, 200% actual text sizing, portrait, landscape, foldables and reduced motion.
8. **One component, one styling owner.** Shared primitives and semantic CSS tokens are preferred to screen-specific patches and duplicate `!important` declarations.
9. **Offline continuity and recoverability.** Training work survives interruption, reconnect, navigation and resume; loading errors never impersonate an empty plan.
10. **Verify the actual build.** Unit/browser checks, visual comparisons, hosted file identity and physical Android acceptance are distinct gates.

## 1.3 Editorial voice

Confident, concise, explanatory only when useful. Use gym vocabulary correctly; be neutral about performance. Show one reason and one next action, not several paragraphs of engine terminology. Distinguish **Suggested**, **Applied**, **Saved**, **Recorded**, **Unavailable**, and **Needs review**; do not label normal rep progression as a reversible program change.

---

# 2. Source of truth and change authority

| Concern | Primary owner in current repository | Design contract |
|---|---|---|
| Theme and shared presentation | `app.css` and existing theme state | All paint and static layout consume `--pi-*` semantic tokens; no duplicate palette |
| Primitive semantics | `modules/ui-primitives.js` (`UiHeader`, `UiRootHeader`, `UiAction`, `UiChoice`, `UiStatus`, `UiSection`) | Extend existing primitives before creating visually identical ones |
| Screen composition and state | `modules/App.js` and extracted feature modules | Maintain current data authority and screen navigation contracts |
| Spatial spring, feedback and panel motion | `modules/ui-motion.js` | Single owner of interruptible gesture/spring properties; CSS handles paint |
| Measured shell and notice clearance | `modules/ui-layout.js` | Measure actual dock, rail, footer and viewport instead of guessed offsets |
| Training rules and persisted facts | Existing engine APIs, program projections and persistence modules | UI displays existing canonical data; it does not derive competing training state |
| Presentation debt policy | `verification/ui-ownership-test.mjs` | Ratchet measured ceilings down, never raise them to hide duplication |
| Current working decisions | `DESIGN_SYSTEM.md`, `UI_INTERACTIONS.md`, `NEXT_APP_UPDATE_PLAN.md` | This spec organizes the target; conflicts require explicit, tested reconciliation |

**Change protocol:** If a desired visual treatment conflicts with a verified working behavior, preserve the behavior and revise the treatment. If a requirement conflicts with an existing named UI owner, consolidate the owner in the same pass; never add a parallel one. Each design change lists affected routes, semantics, data owners, tests, screenshots, risks and rollback.

---

# 3. Information architecture

## 3.1 Five persistent root destinations

| Root | Core question | Primary first-viewport content | Explicit route onward |
|---|---|---|---|
| **Home** | What am I training next? | One next-workout or Resume hero, date/program context, executable session identity, constraints; separate short Game Plan | Start/Resume, Preview, Plan |
| **Plan** | What is my saved training structure? | Selected program, active/current week, four subviews: Schedule / Volume / Progression / Block | Program details, editable review, cycle overview |
| **Progress** | What actually happened? | Recent completed records, search/filter, lift trends or body logging via named subviews | Session detail, edit history, strength standards |
| **Profile** | What achievements represent me? | Customizable profile/share card, strength score/standards, meaningful milestones | Edit card, strength comparisons, share |
| **Settings** | How does the app behave and protect my data? | Organized preferences; equipment, units, appearance, workout, accessibility, backup, diagnostics | Selected setting or confirmation dialog |

**Secondary destinations:** Workout, Focus, Program, Cycle, Exercise Library, exercise detail, calculators, setup/onboarding, full history editor and recovery tools. Do **not** add Library or Workout as a sixth root tab to solve discoverability. Make them reachable contextually.

## 3.2 Navigation contract

- A root destination has **one stable title baseline**, a native semantic heading and no redundant Back control.
- A deep destination has one clearly labeled Back action. Android system Back, keyboard Escape where applicable, and visible Back use the same navigation guard and transaction semantics.
- Preserve each root's scroll position and selected subview when leaving and returning. Browsing a future week **does not** change the active week or saved program.
- A minimized workout shows **Resume**, not **Start**. The workout timer persists independent of the route animation.
- Keep all five root navigation labels fully accessible; avoid a visual abbreviation that produces a different accessible destination.
- The bottom nav and contextual workout dock have one measured layout owner. No tap target may be obscured by the other or by a notice.
- In-progress unsaved edits require **Apply**, **Discard**, or **Keep editing** before a navigation path that would lose them; native Back must respect the same rule.

## 3.3 Task-first flow

`Home → Preview → Workout/Focus → Complete → Progress` is the daily primary journey.  
`Plan → Program/Cycle review → compare changes → Apply → Home` is the planning journey.  
`Progress → session detail → Edit → Save/Undo → Progress` is the correction journey.  
`Settings → Backup/Restore → preview differences → Confirm → verified recovery` is the resilience journey.

Each handoff retains the **same program/workout/week identity and units**. A placeholder or loading projection must never be presented as a different saved plan.

---

# 4. Design-token architecture

## 4.1 Token layering

**Foundation** (scales and type) → **semantic roles** (canvas, card, active, warning) → **component variant** (`UiAction` primary, card featured) → **screen composition** (position/ordering only). Avoid screen-specific literals if a token or existing component role expresses the intent. `--pi-*` names already exist; this spec **does not** authorize wholesale renaming.

### 4.1.1 Color: semantic, theme-supplied

| Required token | Role and permitted use | Prohibited use |
|---|---|---|
| `--pi-color-canvas` | Application background and primary scrolling plane | Hardcoded per-screen canvas colors |
| `--pi-color-card` | Primary content card surface | Creating another card shade for each screen |
| `--pi-color-control` | Touchable control container | Pure decoration with no control |
| `--pi-color-inset` | Text field, inset control and nested content | Second shadow to imply unknown elevation |
| `--pi-color-feature` / `--pi-color-on-feature` | Sparing primary feature or hero surface | Every card or a full page background |
| `--pi-color-divider` | Subtle but distinguishable group separators | Divider after every single line |
| `--pi-color-on-control` | Primary foreground on neutral surfaces | Semitransparent text that fails contrast |
| `--pi-color-on-muted` | Supporting labels/captions | Numeric targets, errors, active selection labels |
| `--pi-color-active` / `--pi-color-on-active` | Selected actions and primary accents | Color-only selected state |
| `--pi-color-warning` | Warnings and confirmed blocking issues | Routine coaching advice |

**Theme rules.** Support light and dark; preserve existing user-selectable theme families and correct foreground pairing. All visible color pairing is contrast-tested in **every supported theme** (not only the default). Status semantics require text or icon plus color. A disabled control remains distinguishable, and the disabled appearance must not masquerade as an accepted change.

**Target contrast:** WCAG 2.2 AA: body text **≥4.5:1**, large text **≥3:1**, significant control boundaries and visual states **≥3:1** where applicable. Strongly prefer 7:1 for critical workout numerals when compatible with the selected theme. Validate *computed colors*, not token names.

### 4.1.2 Spacing scale (existing)

| Token | CSS px | Intended cadence |
|---|---:|---|
| `--pi-space-0` | 0 | Flush alignment; no arbitrary compensating margins |
| `--pi-space-1` | 4 | Icon/text micro-gap; tightly related metadata |
| `--pi-space-2` | 8 | Paired controls and compact rows |
| `--pi-space-3` | 12 | Header context and dense inner card groups |
| `--pi-space-4` | 16 | Standard inset and card subgroups |
| `--pi-space-5` | 20 | Breathing room around prominent elements |
| `--pi-space-6` | 24 | Section separation and comfortable card inset |
| `--pi-space-8` | 32 | Major section transitions |
| `--pi-space-10` | 40 | Rare hero or page transition clearance |

**Spacing contract:** Parent owns `gap`. Avoid child `margin-top` and `margin-bottom` except typography or explicit document flow. Do not combine a card's own padding, a grid gap, and a one-off child margin for the same visual interval. Exceptions to the scale require a named, measured component geometry such as a circular thumb or instrument graphic.

### 4.1.3 Type scale (existing)

| Token | Baseline | Typical role |
|---|---:|---|
| `--pi-type-caption` | 0.6875rem / 11px | Secondary compact annotation (not critical information) |
| `--pi-type-label` | 0.75rem / 12px | Short labels/metadata |
| `--pi-type-support` | 0.8125rem / 13px | Supporting copy |
| `--pi-type-body` | 0.9375rem / 15px | Main body |
| `--pi-type-control` | 1rem / 16px | Control fields, especially numeric entry |
| `--pi-type-section` | 1rem / 16px | Section headings; differentiated by weight |
| `--pi-type-heading` | 1.375rem / 22px | Feature headings |
| `--pi-type-screen-title` | 1.625rem / 26px | Root destination `h1` |

Use **relative `rem` type**, inherited text scaling and comfortable line height (body target 1.35–1.5; display 1.1–1.25). Avoid fixed-height text containers and all-caps long labels. Weight and rep numbers are tabular; a timer uses tabular numerals; set data must remain legible under zoom. **Text scaling has priority over screenshot-perfect pixel matching.** Native numeric/text inputs use a baseline of **at least 16px** to avoid unwanted mobile focus zoom.

### 4.1.4 Shapes, controls and depth (existing)

| Role | Existing target value | Rule |
|---|---:|---|
| `--pi-shape-action` | 14px | Shared conventional controls |
| `--pi-shape-card` | 20px | Content cards |
| `--pi-shape-feature` | 24px | Intentional hero/featured card |
| `--pi-control-min` | 48px | Default effective tap target |
| `--pi-primary-min` | 52px | Dominant action target |
| `--pi-shadow-card` | none | Flat tonal hierarchy by default |
| `--pi-shadow-overlay` | existing semantic overlay | Overlays when layer separation is necessary |

Choose a larger shape only when it reinforces an important moment. A shape change on press is allowed only through the shared motion implementation. Tabs and workout steppers must not inherit decorative shapes that reduce usable hit area.

### 4.1.5 Motion tokens and ownership

Existing baseline CSS includes `--pi-motion-fast: 140ms`, `--pi-motion-standard: 220ms`, `--pi-motion-paint: 160ms`, and `--pi-ease: cubic-bezier(.2,.8,.2,1)`. Spatial motion is owned by `modules/ui-motion.js`, including its interruptible spring implementation; do **not** convert its physics into an unrelated stack of CSS transforms. Durations above are paint/fallback timing roles, **not** prescribed spring settle times.

## 4.2 New tokens require an RFC

A new token must name a reusable semantic meaning, have a defined light/dark behavior, be used by multiple components or represent a truly unique feature, and include a source/browser verification rule. Disallow `--home-purple-2`, `--workout-gap-fix`, `--m3-redo` and alias chains that hide ownership. Existing legacy aliases are migration aids, not new default APIs.

---

# 5. Layout, geometry and responsive system

## 5.1 Layout grid and ownership

- One shell manages safe-area insets, navigation, footer, scrims, keyboard and measured viewport. No root-level padding that double-applies `env(safe-area-inset-*)`.
- Root screens share `UiRootHeader`, one 48px title/action row, a stable left inset, an actual `h1`, and independently scrolling content.
- Cards use scoped parent layout; alignment comes from grid/flex constraints, not invisible spacer elements.
- The workout logger has a stable numeric input track. Opening reference/help/rest must not reflow the active weight/reps/completion row.
- Content must scroll independently of fixed chrome. Sticky elements are allowed only when needed and must preserve useful reading space.
- Notices are positioned using `modules/ui-layout.js` measured barriers. Do not hardcode toast bottom pixel guesses.

## 5.2 Adaptive layout targets

These are **target layout families**, not mandatory breakpoints; prefer container queries and measured reflow over fixed-width assumptions.

| Family | QA viewport examples | Behavior |
|---|---|---|
| Compact phone | 320px and 393px widths; 360–420px representative | One main column; one-handed primary actions; wrap secondary controls; stable logger |
| Large phone / short landscape | 520px and 900×480 with keyboard | Cap sticky chrome; reserve form viewport; control rows wrap when enlarged |
| Medium / portrait tablet | 834px width | Wider content, contextual two-column layouts where meaningful; no stretched text walls |
| Expanded tablet / desktop | 1024px and 1280px widths | Split panes/rail where useful; retain active selection and readable line length |
| Foldable | Narrow and wide posture at similar CSS widths | Adapt by available container width, avoid hinge occlusion and preserve workout state |

**Global minimums:** No unintended horizontal document scroll at 320px or 200% text; visual primary action stays reachable; controls target ≥48px unless a documented dense-only exception has an equivalent reachable action; zoom never forces clipping of required labels. WCAG 2.2 AA mandates minimum 24×24 CSS px targets or listed spacing exceptions; Pursuit Iron's 48px target is deliberately stricter.

## 5.3 Content and sticky chrome budgets

At default phone text size, the first viewport should show the screen's main question and a usable first action or record. The Library must **not** pin multiple search/filter rows above the results; only a compact toolbar stays sticky. A short-window wizard footer must scroll rather than consuming the entire form. A dynamic keyboard must never cover the single available Save/Complete/Continue action.

---

# 6. Component contracts

Every shared component provides a clear visual **default**, **pressed**, **selected**, **disabled**, **loading**, **error** and **keyboard-focus** treatment where relevant. Components preserve native controls and accessible names. The component itself owns paint and layout; features own data and side effects.

| Component | Anatomical contract | Interaction and verification |
|---|---|---|
| `UiAction` primary | 52px min, label + optional meaningful icon, one semantic fill | One dominant per task region; pending disabled state; press does not shift surrounding layout |
| `UiAction` secondary/tonal/text/icon | 48px min effective target; stable icon bounds | Distinguishable states; icon-only has `aria-label`/title; no duplicate menu action |
| `UiChoice` / segmented | Stable track, selected indicator, full name | `aria-pressed`/selected native semantics; moving indicator never blocks selection or keyboard |
| `.wpb-ui-card` | Shared radius/padding/tonal surface; semantic content hierarchy | Clickable card uses explicit native action; not all cards are buttons |
| `.wpb-ui-field` / control | Connected label, unit/assistive text, 48px min, 16px input | Raw user input preserved until explicit valid save; errors describe actual invalid value |
| `SettingsSwitch` | Contrast-safe state; readable label and description | Native checkbox/switch semantics, no simultaneous double toggle on row + control |
| Navigation tabs | Icon + unambiguous full destination label, active state | 48–52px targets; no false text truncation; scroll/selection retained |
| Inline status/notice | Brief factual message, severity and optional Undo | Anchored above live bars by measured geometry; live announcement once; never covers Complete |
| Bottom sheet / dialog | Persistent title/Close, scrolling content, fixed reachable footer when needed | Focus trap, focus restoration, Escape/Back correctness, cancellation and reopen safety |
| Context menu | Trigger-bound, fully named options; scroller if tall | Focus enters usable first action; Escape/Back restore opener; no offscreen menu |
| Search/filter | Visible clearable query; named filters and result count | Filtering does not reset route memory unnecessarily; selected chips remain visible |
| Empty state | One-line cause + one next step | Never impersonate no data during offline/schema/load failures |
| Chart/metric | Unit, time scope, accessible text alternative | Never substitute a line for an unsupported estimate or silently interpolate missing history |
| Feature hero | One dominant CTA, relevant context, restrained background | Decorative elements must not obscure date/title/CTA or duplicate secondary cards |

## 6.1 Iconography and nomenclature

Use existing icon assets, consistent optical size (typically 18–22px), and stable alignment. Icons must not be the sole representation of a critical training fact. Workout exercise navigation may abbreviate **Barbell → BB**, **Dumbbell → DB**, and **Extension → Ext.** in compact rows only; preserve full exercise titles in the main heading, history detail, semantic accessible name and expanded view. Do not abbreviate a term when it becomes ambiguous.

## 6.2 Numeric entry and stepper

- Set logging order: **Weight → Reps → RIR/effort**, with Complete on the **right** of the active input row, as space permits. Primary controls must remain readable at 320px and 200% text; allow a deliberate wrap rather than shrinking text.
- Short steppers have **equal visible widths** and 48px effective hit regions, with a limited visual footprint. Long press repeats only after a deliberate hold and stops on release, blur, cancellation or unmount.
- Tapping an existing numeric value selects the entire value for replacement. The focus timing must not erase the first typed digit or clobber a new input.
- Never automatically round, reduce, replace or propagate a **typed** working set. A **suggestion** for future sets is allowed, with Apply and exact rounded plate/increment explanation.
- Derived suggestions must be snapped by canonical equipment/loading rules. A 5lb-increment machine must not display a 115.5lb prescribed step; distinguish actual historical loads from suggested loads.
- For an incomplete numeric entry (e.g., `10-20oops`), preserve the raw draft, show an error and disable Save/Apply; never coerce it into a valid prescription.

## 6.3 Workout set row states

| State | Visual emphasis | Data behavior |
|---|---|---|
| Planned | Clear target/last reference; available inputs | Read-only plan is not recorded history |
| Active | Full contrast fields; single Complete button; one RIR region | No auto-save of a partial set as completed |
| Editing | Focus outline, select-all, visible units/validation | Preserve typed draft and return focus on validation error |
| Completed | Muted but readable, distinct checked/recorded marker | Immutable until explicit Edit/Undo; no duplicate completion |
| Skipped | Clearly labeled skipped state | No fake zero-weight set or completed volume |
| Adjusted | Separate suggestion/proposal marker | Do not rewrite logged or previous sets |
| Resumed after interruption | Exact saved row/effort/timer identity | No re-created PR, changed prescription or duplicate notification |

## 6.4 Progression/change notification model

**Normal progression** (adding a rep within a double-progression range) is not a structural program change and does not need a Program Changes/Undo card. **Meaningful changes** (weight increase, planned set/rep-range change, split adaptation, plateau response, equipment substitution or reduced load recommendation) show cause, impact, effective scope, and Apply/Undo only when truly reversible. A PR notification occurs **only when new confirmed logged evidence exceeds the previous comparable best**, after the set commit, once per qualifying record/event. No random, speculative, repeat or resume-triggered PR animation.

---

# 7. Screen-by-screen specification

## 7.1 Home — decide and start

**First viewport:** Stable Home title/actions; smoothly collapsing brand/date context; one session hero with correct day, program/week and executable session state; Start or Resume. The session outline is expanded by default with compact exercise names and meaningful estimates. Today's Game Plan is **separate**, concise and optionally expandable. Progression/plateau notices appear only if consequential and user-actionable.

**Content order:** (1) Header, (2) Next Workout/Resume, (3) session outline, (4) short Game Plan, (5) relevant meaningful change/Undo notice, (6) supporting program actions/progress links. Empty state: one primary Create Program action, secondary Templates/Custom/Cycles discovery. Do not produce multiple hero Create buttons.

**Pass conditions:** Existing Start/Resume destination unchanged; no duplicate create CTA; 320px and 200% titles readable; Home header collapse does not move its action buttons; no extra scroll to the primary action.

## 7.2 Plan / Program — understand and deliberately edit

**First viewport:** Same root header/insets; active program identity; active selected week/day; four coherent panes **Schedule, Volume, Progression, Block**. A clearly visible **Open full program** action near the top. Program rows use one style for exercise title, set count, rep range, volume and actions. Planned future weeks/blocks use explicit **Preview** labeling. A program change is reviewed as before/after with actual saved-week provenance.

**Content:** Selected training week is the single authority for set/frequency/volume display. Cycle changes show block, goal, split, frequency, actual saved executable-week set counts, time/equipment fit, and uncertain future adaptation. A custom program is a first-class program; opting into an adaptive cycle does not silently drop authored sets. Week navigation preserves selected Plan pane, current activity state and unsaved edits.

**Pass conditions:** Home/Plan/Preview/Workout show matching scoped session data; no `2,2` arrays as displayed set counts; equipment-incompatible exercise never appears as a guaranteed executable future week; week browsing never triggers edits; real Save/Undo transactions remain intact.

## 7.3 Workout — log one set accurately

**Anatomy (portrait phone):** Current exercise name upper-left; **Set 1 of N** beneath the name; one small progression marker beneath that; right side arranged **Skip / History / Info** with **Key Lift / Compound** tags above, no awkward wrapping. Under the header: a **single compact icon row** (Warmup, Superset, Swap, Session note, Pinned note), not duplicated in an overflow menu. Exercise navigation may use abbreviated labels, but primary heading remains full and readable.

Next: optional concise instruction, one **Target / Last** toggle with stable dimensions and order **weight → rep range → RIR**, then active weight/reps steppers and Complete action. The completed set loses emphasis; current/next set becomes prominent without moving controls unexpectedly. One active rest bar, time controls, and the existing live dock remain reachable. **No automatic value overwrite.**

**Special behavior:** Double progression initially encourages more reps with the **same prescribed weight** across exposures; weight changes are proposed when evidence supports them. If actual intra-workout performance suggests lowering weight, **suggest it**, showing what changes, not silently changing the field. Incomplete/invalid RIR or targets do not become logged facts. Rest reset uses a deliberate long press; timer lifecycle is independent of panel motion.

**Pass conditions:** 320/393px normal and 200% reflow, press controls, plate calculator actual load, PR truth, safe finish confirmation, persisted set resume, no scrolling controls under docks, no surprise changes to logged work.

## 7.4 Focus mode — one-handed execution

Focus is a **fully usable alternate presentation**, not a feature-reduced workout. Keep weight/reps, Complete, target/last, plate calculator, amplifiers, notes and quick exercise options accessible. Large active numeral/timer and restrained supporting labels may be more expressive here; the input row and tap targets remain fixed during active logging. Tablet/foldable may display the workout dock by default if space permits. Toggling Focus retains exact row, timer, data, scroll anchor and pending edits. A Back or minimized flow does not finalize the session.

## 7.5 Library / exercise detail — browse with room

Only a **compact toolbar** remains sticky on small phones: Back, Library, Find and Compare. Search and category/equipment/muscle filters belong to the **results scrolling content**. Search can be brought into view and focused with Find; filter state, scroll position and selected comparison items survive opening details/Back. Wide screens use a meaningful list/detail split. Each exercise detail names equipment, attachments/variants, technique, history, alternatives, substitutions and relevant loading grid. A missing attachment variant must never be silently shown as equivalent equipment.

**Pass conditions:** A usable first library result viewport at 320/393, no five-row pinned filter stack, 200% text readable, horizontal wrapping without hidden Compare/Back actions, no false duplicate exercise labels.

## 7.6 Progress — records before decoration

**Default Sessions:** Heading and compact summary → search/filters → first recent completed record → additional records → calendar and consistency trend. The calendar is useful but does not take the first phone fold. Strength, Lift and Body subviews have explicit scopes, units, drill-ins, favorites and clear empty states. Show last performed versus expected only when both values exist and the comparison is defined.

**Records:** Edited past sessions use a staged dialog with preview, Save, Cancel and Undo. Distinguish measured, estimated and absent data. A PR reflects a verified historic comparator and cannot be synthesized by entering a weaker set or reopening a log.

## 7.7 Profile — meaningful accomplishments

Profile features a customizable, shareable card with selected lifts, milestones, consistency, streaks and a stable level/strength indicator. **Strength Standards** and Thousand-Pound Club have explicit routes and recorded-vs-estimated distinction. The card customization changes **card content only**, not Profile's entire layout. App bar Edit is stable; morph/expand motion must be continuous, reversible and free of square-corner flash or long label jumps.

## 7.8 Settings and setup — preferences and safety

Settings uses named groups for Training, Workout behavior, Equipment/Gyms, Appearance, Accessibility, Data/Backup and Advanced. Avoid decorative categories that behave as an unlabeled dropdown. Each setting has a title, current-value summary if useful, short description, and correct native toggle/choice/edit control. All gym numeric fields preserve raw input until valid Done; unit conversion applies to the complete affected physical values, not just displayed unit strings.

Setup/wizard: one question per purposeful step; summary shows actual selections; responsive footer never covers the form under keyboard, 200% text or short landscape. A program/cycle build retains a draft, shows feasibility, and supports Cancel/Back without losing work.

## 7.9 Cycle review and changes — consequential decisions

A review has four explicit layers: **What changes**, **Why**, **When/where**, **What stays intact**. Show before/after rows with scope (`next workout`, `future sessions`, `next block`); when no safe proposal exists, explain why without applying partial edits. Undo restores the verified previous authored plan without erasing already completed history. The home change feed uses these same criteria and does not mistake a routine rep increment for a reversible program revision.

## 7.10 Dialogs, calculators, backup and failure screens

Dialogs: title/Close visible while body scrolls, keyboard trapped, opener restored, native Back/Escape consistent with dismissal policy. Calculators are **read-only estimators** unless an explicit Apply action exists; plate view shows achievable total and inventory limitations, not a visually attractive but unbuildable barbell. Backup/restore previews add/update/remove differences before committing. Offline/update/conflict/recovery messaging says what is safe and what needs user action. A failed persistence operation cannot show a successful Completed state.

---

# 8. Motion and choreography

## 8.1 Motion character

**Tactile and measured, never theatrical during data entry.** Preserve the prototype's successful hierarchy and choreography while keeping production state machines, existing `modules/ui-motion.js` springs and native gestures. No second spring package, no CSS transform competing on an owned element, and no transition lifecycle owning a data write.

| Interaction | Motion intent | Guardrail |
|---|---|---|
| Root destination switch | Quick, quiet change of task | Header baseline stable; scroll/selection remains remembered |
| Home header collapse | Context condenses into app bar | Title, main actions and navigable targets do not jump |
| Start/Preview/Workout | Connected parent→child relationship | Actual identity consistent across screens; no delaying Start |
| Button press | Slight tactile compression/shape response | Input and neighboring text don't shift; cancel on release/blur |
| Segmented/choice | Indicator moves to newly selected state | Selection value commits immediately; accessible state updates first |
| Workout set Complete | Clear recorded fact + next-set focus | Persist synchronously; do not animate numeric field positions |
| Rest timer/dock | Indicate continuing time and current context | No duplicate timer controls; backgrounding never corrupts elapsed time |
| Sheet open/close | Shared spatial hierarchy and focus | Cancelable, reversible, drag safe; restore origin on close |
| Proposal review/Undo | Reveal before/after and consequence | History and authored values remain stable |
| Profile share card | Cohesive card morph/expand | No square flashes, clipping, sudden baseline jumps |
| Error/validation | Direct focus to remedy | No shaking values or transient explanation that vanishes |

## 8.2 Motion implementation requirements

- Use the existing spring API (`spring`, `setMotionValue`, `stopMotion`) for spatial properties, not fixed-duration JS `setTimeout` choreography.
- Use existing paint timing tokens only for appropriate opacity/color/state transitions; no arbitrary delays in completion, save, or button availability.
- **Interruptibility:** quick repeated taps, immediate Back, resizing, orientation change, opening while closing and app backgrounding leave one correct final state, without stale callbacks.
- **Reduced motion:** `prefers-reduced-motion: reduce` must suppress nonessential movement at startup and when changed mid-session. Functional content and notifications remain intact. Avoid requiring any animation for comprehension.
- **Performance targets:** smooth device-frame updates with no repeated layout-thrashing animation; transforms/opacity when possible, measured height only when necessary; respect low-end Android devices. Do not claim a measured FPS number without profiling.

---

# 9. Feedback, language and edge states

## 9.1 Notification hierarchy

| Level | Example | Delivery |
|---|---|---|
| Recorded fact | Set completed, history correction saved | Immediate in-place confirmation; optional brief notice |
| Suggested action | Lower next set by one valid increment | Clear optional suggestion with Apply/Keep; no auto-edit |
| Meaningful plan change | Set increase, progression plateau, cycle shift | Concise review card with before/after, scope, undo if applicable |
| Verified milestone | True comparable PR | One restrained celebratory moment, never speculative or replayed |
| Blocking fault | Invalid load, cannot save offline, failed restore | Persistent direct remediation and correct focus; no false success |
| Informational | Normal +1 rep, ordinary activity | Usually no separate change feed card |

## 9.2 Writing guidelines

Use **“Last 120 lb × 8”**, **“Target 8–12 reps”**, **“Suggested 115 lb for your next set”**, **“Apply to remaining sets”**, **“Keep current weight”**, **“Updated next week's sets from 3 to 4”**, **“Unable to save; your current edits remain here”**. Avoid ambiguous “Changed,” “Engine recalibrated,” and mathematical jargon without context. Abbreviations should never appear as the sole screenreader label.

## 9.3 Loading, pending, errors and Undo

- Pending action remains discoverable, with a durable progress or state indicator; **never make Update disappear before interaction is possible**.
- An Undo notice keeps sufficient lifetime to act, is reachable by keyboard, never overlaps the workout footer and does not silently expire during an active edit.
- Autosave/transient drafts have precise save status; a queued write is not a committed write.
- Loading skeletons preserve stable container geometry; offline and zero-data states are visually and semantically different.
- Failure messages include specific next steps rather than an unhelpful generic error.

---

# 10. Accessibility and inclusivity acceptance

**Baseline:** WCAG 2.2 **AA** for the responsive PWA, plus Pursuit Iron’s deliberate larger-touch design targets. The following are measurable, not merely aspirational:

1. **Visual contrast:** 4.5:1 body, 3:1 large text, 3:1 significant non-text UI elements. Test every supported theme, selected/disabled/error states and overlays with computed values.
2. **Targets:** 48px preferred for active controls, 52px for primary; WCAG 2.2 AA's minimum target rule is 24×24 CSS px or a defined exception. Focus-visible uses a clearly visible ~2px or greater outline with sufficient contrast.
3. **Text:** 200% actual document text scaling without content/action loss; support 320px CSS-wide reflow and landscape. No false truncation of program/exercise titles without a route to the complete name.
4. **Keyboard:** All controls have semantic tab order, arrow key behavior where appropriate, visible focus, Escape/Back handling, dialog focus trap and reliable return to opener.
5. **Screen readers:** Meaningful headings, native labels, controlled pressed/selected states, and restrained `aria-live` for actual saves/errors/PRs; only essential announcements, no repeated set-complete chatter.
6. **Input and error:** Connect labels and error text (`aria-describedby`), identify exact invalid field, keep raw typing, restore focus after dialogs and Undo.
7. **Color and movement:** No color-only states, respect forced colors, reduce motion at runtime, avoid flashing, retain function without visual animation.
8. **Touch and fatigue:** One-handed logging remains possible, nested gestures do not steal taps or scroll, long-press alternatives have an ordinary accessible path.
9. **Orientation and assistive tools:** Validate Android system Back/IME, TalkBack, external keyboard and foldable posture separately from browser emulation.
10. **Data trust:** No automatic field overwrite from focus handling, predictive UI, animation or background sync.

---

# 11. CSS and component implementation policy

## 11.1 One-owner rule

| Layer | Allowed | Not allowed |
|---|---|---|
| Global foundation | Reset, root tokens, accessible focus, safe-area primitives | Screen-specific shadow/color patches |
| Shared component | Named semantic class + variants and states | Multiple versions of the same action/card with different geometry |
| Feature screen | Layout order, unique responsive composition, meaningful feature accent | Static color/layout via React inline objects |
| Dynamic runtime | CSS custom property for actual progress, drag/measurement, chart coordinates | Arbitrary mixed static and dynamic `style` objects |
| Motion | One `ui-motion.js` spatial owner and optional CSS paint owner | Duplicate transform/height/transition owner for the same element |
| Legacy migration | Carefully measured deletion and rerun | End-of-file specificity patch, higher `!important` or debt ceiling |

## 11.2 CSS migration contract

1. Identify the actual owner and duplicated selectors/declarations. Inspect current computed layout on dark/light, 320/393, 200% text, medium tablet and desktop.
2. Extract a **named, semantic, reusable** declaration or shared primitive. Keep visually important existing pixel values until behavior parity is demonstrated.
3. Replace static inline JSX presentation, while leaving computed/animated values with their proper source. Remove the now-superseded CSS and dead selectors in the same verified slice.
4. Do not add new `!important` just to win the cascade. Existing `verification/ui-ownership-test.mjs` uses debt ceilings; its current visible baseline includes `MAX_IMPORTANT = 2060`, `MAX_APP_IMPORTANT = 1053`, `MAX_REPEAT_IMPORTANT = 0`, and `MAX_INLINE_STYLE_OBJECTS = 1406`. **These are ceilings, not quality goals**, and must be re-read at integration time.
5. Run the precise browser contract and source ratchet, inspect screenshots and ensure no new visual/behavior regression.
6. Only publish as part of a **coherent, larger verified batch**, not one build per trivial CSS fix. Keep update/version identity truthful.

## 11.3 CSS design review checks

- No direct hex/rgb in a component if a semantic role applies (exception: theme definition, charts with documented semantic palette or data-driven swatch).
- No new duplicate selector/property priority owner; no important-budget increase.
- No static JSX geometry/paint in any newly migrated component; explicit exception documented for measurement/data.
- Parent `gap` used instead of arbitrary vertical child margin stacking.
- Correct focus/border/selected states in all user-selectable themes.
- Class names describe purpose (`workout-set-row`, `plan-week-rail`, `profile-club-card`), not visual accident (`fixSpacing2`).
- Consistent `rem`, safe-area and container queries; no fixed truncation hiding essential actions.

---

# 12. Verification matrix and definition of done

## 12.1 Required scenario matrix

| Area | Required viewport coverage | Additional mandatory checks |
|---|---|---|
| Root navigation and headers | 320, 393, 834, 1280 CSS px | 200% text, all supported themes, keyboard focus |
| Home Start / Resume / hero | 320, 393, 834, 1280 CSS px | Reduced motion, interrupted resume, first viewport |
| Plan weeks + cycle review | 320, 393, 834, 1280 CSS px | Current versus future scope, 200% text, keyboard |
| Workout logging + Focus | 320, 393, 834, 1280 CSS px | Real typed input, IME, rest docks, reduced motion |
| Library search/filter/detail | 320, 393, 834, 1280 CSS px | Scroll memory, 200% text, useful first result |
| Progress edit and PR | 320, 393, 834, 1280 CSS px | Real comparator, error/Undo, history preservation |
| Profile card/strength | 320, 393, 834, 1280 CSS px | Morph stability, enlarged text, share controls |
| Settings/wizard and gym | 320, 393, 834, 1280 CSS px | Raw input, validation, short-window keyboard |
| Dialogs/back/keyboard | 320, 393, 834, 1280 CSS px | Focus trap/restore, Android Back, repeated reopen |

Additionally test **short landscape**, forced colors, reduced-motion preference toggled mid-flow, live keyboard opening, offline, service-worker update, physical Android and foldable layouts. The table is a target coverage matrix, not a claim tests already ran.

## 12.2 Behavioral end-to-end journeys

**J1 Create → Train → Record → Progress:** Create 3/4/5/6-day feasible plans, inspect saved next week and active gym, start a valid workout, enter sets with valid increments and effort, finish and confirm, reload and compare history. Output must match across Home/Plan/Preview/Workout/Complete/Progress.

**J2 Double progression + fatigue:** Prior session 120lb 8/8/7 target 8–12. The next suggestion must favor additional reps at same weight until criteria warrant progression. If an in-session failure implies reduced weight, show optional reduction rounded to available step, not an unrequested mutation of already typed sets.

**J3 PR:** Log a true verified comparable best; trigger exactly one PR. Reload/resume/visit history or log a weaker set; trigger **zero** additional PRs.

**J4 History correction:** Edit a prior set, verify exactly what changes and why, Save, compare current/previous display and Undo/reload. No fabricated session or double counts.

**J5 Cycle transition:** Convert a custom program to cycle, preview next block by equipment and scheduled frequency, review adaptations, apply/undo and ensure that completed facts and authored work are not lost.

**J6 Interaction recovery:** Type an unfinished target, press Android Back or a root tab, Keep editing and ensure the precise text remains; validation errors focus exact raw input. Trigger Update, ensure it doesn't disappear while actionable.

**J7 Motion/resize:** Rapidly open-close-open a sheet, rotate a viewport, press Back during exit, resume workout after backgrounding; no duplicate dialog, lost focus, timer shift or stale state.

**J8 PWA update/offline:** Start and complete a workout offline, relaunch, update hosted code, verify same persisted identity and version/asset hash; no duplicate row or stale old build pretending to be new.

## 12.3 Release blocking conditions (P0)

- Lost/modified logged data, invalid load/increment suggestion, duplicate workout completion or false PR.
- Inaccessible/hidden Complete, Save, Apply, Undo or essential Back action under keyboard, overlays or sticky chrome.
- Broken focus trapping/system Back, 200% text controls clipped, horizontal clipping of critical values.
- Theme contrast failing critical workout controls, startup blank screen, interrupted resume or blocked offline work.
- Source/behavior/browser/engine/parity/release manifest integrity fail; deployed bytes differ from validated candidate.

**Physical Android status is a separate sign-off.** Passing Chromium/Puppeteer or build hashes cannot certify TalkBack, IME behavior, OEM back gestures, installed PWA offline persistence or foldable hinge behavior.

---

# 13. Phased adoption and CSS debt reduction

The following sequence produces large, coherent passes and **prevents fresh design debt**. Each pass must both improve UX and delete superseded owners where practical.

| Phase | Scope | Exit evidence |
|---|---|---|
| **0. Freeze standard** | Adopt this specification, map current tokens/components; record exact source/debt baseline | Document linked to source commit; component owner inventory |
| **1. Common foundations** | Eliminate remaining conflicting shared paint/spacing/typography; align focus, actions, cards, controls | Theme contrast, ownership ratchet, root screens visual diffs |
| **2. Daily training** | Home → Preview → Workout/Focus header, controls, docks, notice placement, PR/DP affordances | J1/J2/J3/J7 and compact 200% viewport gates |
| **3. Planning, cycle, library** | Plan Program Week/Volume/Block, cycle proposals, library results and equipment detail | J5 + execution parity + first-library-results visibility |
| **4. Review and identity** | Progress Sessions/Strength/Body, history corrections, Profile cards/standards | J4, accessible share card and semantic charts |
| **5. Secondary workflows** | Settings, onboarding, gyms, calculators, backup/restore, long dialogs | J6/J8 + keyboard/short landscape + recoverable errors |
| **6. Global cleanup** | Remove dead CSS, duplicate `!important`, static inline style debt, redundant motion; exact final shell | Source ratchet lower, all affected screenshots and states pass |
| **7. Release acceptance** | Full engine lifecycle, browser shards, hosted/offline identity; hands-on Android/TalkBack/IME/foldable | Separate recorded web and device sign-offs |

Do not use the phase table as permission to leave a P0 correctness issue unfixed while waiting for its phase. Preserve verified functionality; no forced redesign or code churn solely for superficial token purity.

---

# 14. Acceptance rubric

Score each surface **0 = fails**, **1 = partial**, **2 = fully verified** on seven dimensions:

| Dimension | 2-point definition |
|---|---|
| Purpose and hierarchy | One clear primary task/action, relevant first-viewport data, no repeated heroes |
| Layout and rhythm | Parent-owned spacing, stable input geometry, no clipping/overlap, responsive form |
| Component consistency | Shared action/card/field/nav/dialog contract, coherent theme and typography |
| Motion and state | Interruptible purposeful motion, immediate state, reduced-motion parity |
| Training trust | Same canonical data across screens, no silent value/plan mutation, PR truth |
| Accessibility | 200%/320px, keyboard, screenreader semantics, contrast, reachable actions |
| Resilience | Correct Back/cancel/Undo, offline/resume, focus return and error recovery |

**Definition of done for a screen:** 14/14 points *and* no P0 blocker in that surface's journeys. A 12/14 is a useful progress milestone, not launch certification. A spec update requires a version bump, a rationale, affected screens, a rollback note and verification changes.

---

# 15. Decision register

| ID | Decision | Status |
|---|---|---|
| D-01 | Keep existing user theme families and semantic `--pi-*` tokens, not a replacement palette | Established requirement |
| D-02 | Use Material 3 Expressive as principles; preserve custom Pursuit Iron identity | Established requirement |
| D-03 | Keep five root destinations; Workout/Library are contextual | Grounded in live repo |
| D-04 | Use `modules/ui-motion.js` as spatial-motion owner | Grounded in live repo |
| D-05 | Preserve typed set values and committed history over all styling or suggestions | Established requirement |
| D-06 | 48px action target, 52px primary target by default | Existing tokens / target standard |
| D-07 | Normal +1-rep progression excluded from consequential change feed | Established requirement |
| D-08 | P0 fixes before cosmetic consistency; build in large verified batches | Established requirement |
| D-09 | Adopt 0/1/2 scoring rubric and phased acceptance criteria | New target proposal |
| D-10 | Document new component/token exceptions with a bounded RFC | New target proposal |

---

# Appendix A. Repo implementation map

```
Pursuit-Iron/
├─ DESIGN_SYSTEM.md                   existing implementation and migration history
├─ UI_INTERACTIONS.md                 existing interaction behavior contract
├─ NEXT_APP_UPDATE_PLAN.md            active build and acceptance backlog
├─ app.css                            canonical shared tokens and presentation
├─ modules/
│  ├─ ui-primitives.js                shared native UI components
│  ├─ ui-motion.js                    interruptible spatial springs/gestures
│  ├─ ui-layout.js                    measured notice/dock/shell geometry
│  ├─ App.js                           screen/data composition (plus extracted modules)
│  └─ ...                              feature and engine-specific authorities
└─ verification/
   ├─ ui-ownership-test.mjs           ratcheting presentation debt limits
   ├─ b831-expressive-motion-browser-test.mjs
   ├─ b945-plate-calculator-test.mjs
   ├─ b951-progression-pr-contract-test.mjs
   ├─ b980-pr-notification-browser-test.mjs
   ├─ b983-cascade-debt-test.mjs
   ├─ b994-workout-finish-browser-test.mjs
   ├─ b998-ui-controls-browser-test.mjs
   └─ ...                              source, engine and browser gates
```

**Suggested location for this living file:** `docs/design/PURSUIT_IRON_DESIGN_SPEC_V1.md` with optional adjacent visual-regression references. Do not replace the existing `DESIGN_SYSTEM.md` historical contract; link to this spec from it once adopted.

# Appendix B. Implementation examples (illustrative, not copied from running code)

```css
/* Existing tokens should supply the values; this illustrates ownership only. */
.wpb-workout-set-control {
  min-block-size: var(--pi-control-min);
  background: var(--pi-color-inset);
  color: var(--pi-color-on-control);
  border-radius: var(--pi-shape-action);
  font-size: var(--pi-type-control);
  font-variant-numeric: tabular-nums;
}
.wpb-workout-set-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) auto;
  gap: var(--pi-space-2);
  align-items: center;
}
@container (max-width: 22rem) {
  .wpb-workout-set-row { grid-template-columns: repeat(2, minmax(0,1fr)); }
  .wpb-workout-set-complete { grid-column: 1 / -1; }
}
/* Do not use the above as a reason to replace a verified existing set-grid owner. */
```

```js
// UI-only suggestion intent; validation, increments and write authority
// must remain in the maintained engine / workflow owners.
const proposedChange = {
  kind: 'remaining-set-load-suggestion',
  before: 'current typed value remains unchanged',
  proposed: 'engine-validated load on the equipment grid',
  scope: 'uncompleted sets only',
  requiresExplicitApply: true,
};
```

# Appendix C. References and evidence

**Repository baseline (2026-10-29):** [`extracteddeath/Pursuit-Iron`](https://github.com/extracteddeath/Pursuit-Iron), [`Build 989 / cf8f1f8`](https://github.com/extracteddeath/Pursuit-Iron/commit/cf8f1f8fffc2429b8bdbb8866856296266f1683e), `DESIGN_SYSTEM.md`, `UI_INTERACTIONS.md`, `DESIGN_PURPOSE_AUDIT_M334.md`, `modules/ui-motion.js`, `modules/ui-layout.js`, `modules/ui-primitives.js`, and `verification/ui-ownership-test.mjs`. These source documents include historical milestones; use current code and tests for exact implemented behavior.

**Official design guidance:** [Android Developers — Material Design 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3) (Material 3 Expressive includes advances to color, typography, shapes and motion). This spec **adapts** its principles to an existing React/PWA, not a Compose app or new dependency.

**Accessibility criteria:** [W3C WCAG 2.2](https://www.w3.org/TR/WCAG22/); [W3C Understanding Target Size (Minimum), SC 2.5.8](https://www.w3.org/WAI/WCAG22/Understanding/target-size-minimum); [W3C Understanding Focus Appearance, SC 2.4.13](https://www.w3.org/WAI/WCAG22/Understanding/focus-appearance). Focus Appearance SC 2.4.13 is an **AAA** success criterion; the design adopts its visible-focus guidance as a preferred target beyond its AA baseline.

**Evidence limit:** This specification uses repository contracts and project requirements, not a new hands-on Android validation. Implementation status, exact contrast computations across all themes, live responsive screenshots and physical-device acceptance remain to be verified separately.