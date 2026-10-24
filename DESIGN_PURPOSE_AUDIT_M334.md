# M334 — Purpose-first interface audit and transfer plan

**Baseline:** Pursuit Iron, main after M333 (#305), Build 906 / Engine 0.65.9.  
**Reference:** October 21 UI/UX tab blueprint; approved Expressive prototype interaction contract (`UI_INTERACTIONS.md`); current `DESIGN_SYSTEM.md` and `NEXT_APP_UPDATE_PLAN.md`.  
**Status:** Architecture and placement review with a bounded Sessions implementation; remaining items are recommendations, **not** claims of implemented redesign or Android certification.

## Product purpose and design constraint

Pursuit Iron is a training decision-and-recording product, **not a dashboard with a workout button**. Its primary journey is *choose a feasible plan → see today's executable session → perform and record each set → review actual progress → adapt the next phase without silently changing user intent*. Every screen should advance one step of that journey. A feature appears at the point where it affects a decision; other locations show a summary and one clear route, not a competing editor.

The attractive prototype's value is **choreography and information hierarchy**: one dominant action, quiet supporting metadata, useful progressive disclosure, consistent spacing and timed, interruptible transitions. The existing animated owner already exists in `modules/ui-motion.js`. Do not add competing CSS transitions, replace it with a new motion framework, or recreate the prototype's decorative elements at the expense of usable dense workout logging.

## Destination contracts (purpose → first viewport → secondary detail)

| Destination | Question answered | Immediately actionable | Keep out of the way |
| --- | --- | --- | --- |
| **Home** | What should I train next, and can I start? | Current session identity, program/week, Start or exact Resume, constraints and preview | Full history charts, broad coaching essays, redundant 7-day totals and program-management panels |
| **Plan** | What is my saved training structure, and what can change? | Current selected week/day; explicit Schedule / Volume / Progression / Block subview and program management | Detached generic scorecards, multiple contradictory week totals, edits disguised as navigation |
| **Workout / Focus** | What is this set, and what did I actually do? | Exercise, current set, target/last reference, weight and reps, completion, rest/next action | Large explanatory cards, competing sticky layers, animation that shifts inputs, automatic logging or prescription mutation |
| **Progress** | What happened, and is training moving forward? | Sessions: search/grouping and first recent record. Strength/Lifts: explicit drill-ins. Body: current measurement and Log. | A full calendar or repeated summaries ahead of records; hidden strength standards |
| **Profile** | Which accomplishments represent me? | Editable/shareable personal card and Strength Score/standards | Unrelated workout controls and redundant analytics; the share card's content must be user-selectable |
| **Settings** | Where do I adjust a behavior or safeguard my data? | Clear category navigation: workout/training, equipment, appearance/profile, data/backup, advanced | A visually styled category dropdown presented as search, duplicates of program or workout tools, destructive actions next to ordinary preferences |

### Concrete first-viewport findings

- **Progress/Sessions:** the legacy order was overview → consistency → filters → calendar → records; records could be well below the phone fold. **M334 changes the JSX order to overview → filters → grouping/records → calendar → consistency**, while the existing wide-screen two-lane grid retains its visual placement. Browser assertions protect the first-record-before-calendar reading order on compact windows and preserve date selection, historic grouping, expanded set details and saved filters.
- **Plan:** Schedule is a live-program view, not just a calendar. Keep the current week visible and make future week/block previews unmistakably previews; browsing must retain the user's active Plan subtab. The same saved executable-week projection owns the set count, muscle volume and lift frequency at all entry points.
- **Home:** a session is a commitment and immediate action. Put progress and streaks behind a short link; keep Today's Game Plan as separate expandable context instead of repeating it inside a Next Workout hero.
- **Settings:** the jump menu remains useful, but a select should announce that it chooses a section; each destination should show category identity and current-value summaries. Reduce duplicated inline row/segmented styles in one owner, not more specificity patches.
- **Profile:** share customization applies to the top-level card, not the entire Profile layout. The score/standards surface needs an intentional first-class route, with comparable typography in featured and ordinary strength rows.

## Cycle/engine design contract: a preview must be truthful

1. **Created or converted cycle:** show phase, duration, split, weekly practice frequency, available-gym compatibility, estimated session times, actual **saved executable** week sets, and the distinction between fixed preview and future adaptation. Do not claim a future adaptive block's exact exercises are guaranteed.
2. **Split adaptation:** compare the candidate splits against goal constraints and weakest executable week (including repeated exposures on main lifts), then report the chosen split **and reason**. The user keeps the explicit `adaptTrainingSplit` choice; no silent split change on tab browsing.
3. **Volume continuity:** hypertrophy → strength is allowed to shift priorities, but the engine cannot silently collapse a high-volume authored week. Respect time/equipment ceilings, user-owned set counts, and phase-specific peak taper; reject unsafe transitions clearly.
4. **Real history:** next-block advancement uses completed, attributed workout evidence; repeated one-day sessions do not imply full-day coverage. Edits and imported history retain provenance and revision order. Failed persistence cannot finalize a session.
5. **Review / Undo:** changes show before/after, why, effective scope and what history is retained; accept, cancel, retry and Undo must use the same canonical saved identities. A gym swap or program edit must not rewrite completed sets.
6. **Cross-screen:** Home, Plan, Preview, Cycle, Workout, Complete and Progress agree when displaying the **same scoped** session or week. A browsed future week is not silently relabeled as the active week's Up Next.

### Existing executable regression owners checked

- `m304-realized-week-volume-test.mjs`: week-by-week saved prescriptions, regional change, capacity, practice and converted custom blocks.
- `m311-adaptive-upgrade-test.mjs`: custom conversion/upgrade, split selection, actual advancement, identity and authored-user protection.
- `m321-all-week-strength-quality-test.mjs` and `m323-cycle-replay-test.mjs`: weakest middle-week practice, fatigue, missing-day evidence and 3/4/5-day multiblock replay.
- `m326-cycle-refresh-test.mjs`: selected-gym equipment filtering (including individual machines), preserved metadata, safe refresh and rejection after future workouts/manual edits.
- `m327-session-swap-provenance-test.mjs`, `m328-workout-swap-undo-browser-test.mjs`, M329–M331 UI flow checks: correct exercise identity, completed sets, program siblings and Undo.
- The mandatory independent engine audit, grouped contracts, release integrity and seven browser shards must pass on each submitted source revision. Passing fixtures **does not** prove every saved user's cycle or a signed Android build.

## The deliberate motion language

| Transition | User meaning | Required behavior |
| --- | --- | --- |
| Root tab | Change task category | Short shared shell transition, stable header baseline and retained per-tab scroll/selection; no delayed data changes |
| Home → Preview → Workout | Move from decision to action | One continuous visual hierarchy with meaningful parent/child identity, not three disconnected dashboards |
| Workout set completed | A recorded fact became committed | Immediate muted completion state and focused next target; do not shift numeric controls or use delayed writes |
| Rest and resume | Time continues reliably | Timer/dock morph without duplicate controls, interruption recovery independent of animation lifecycle |
| Cycle proposal → review | Explain a consequential change | Spatially stable before/after, clear reason and scope, explicit apply/cancel and reversible Undo |
| Sheet and detail | Inspect supporting content | Shared cancelable springs, focus restoration, safe-area/keyboard and reduced-motion parity |

The motion source of truth is `modules/ui-motion.js`; use the saved theme's tonal roles rather than hardcoded accent colors. Never animate the values of weights, sets, RIR or the saved program itself. Respect reduced motion and forced colors, and test interrupted gesture, fast Back, resize and reopened sheets.

## Next ordered implementation slices

1. **M334 (this pass):** make Sessions genuinely record-first on phone without losing calendar, consistency, two-column desktop, saved filters or history actions. Preserve Workout and engine unchanged.
2. **Settings:** replace the current manually styled jump/select and repeated segmented row presentation with existing shared controls and category-based discoverability. Measure 320px, 200% text and touch sizes before removing legacy CSS.
3. **Supporting Preview/Cycle:** inspect the actual first viewport, time choices, exercise list, split explanation and persistent Start; check current versus browsed scope and the active-gym fit.
4. **Home/Plan:** verify the current-week and first-workout placement using populated and empty screens, minimize duplicate recap copy and hidden detail. No speculative engine logic.
5. **Progress Strength/Profile:** promote strength standards and explainability with a consistent share-card/score hierarchy and traceable recorded sets.
6. **App-wide motion and style consolidation:** delete superseded visual owners, reuse shared tokens and action families, and compare light/dark, 320/393/834/1280, 200% text, screenreader controls and reduced-motion states.
7. **Release boundary:** exact-head CI and deployed-file identity, plus **separate** real Android keyboard, Back, TalkBack, foldable, offline/update and signed TWA verification before Play launch.

## Guardrails

Preserve the working phone Workout/Focus layout and all per-set actions, compact logging geometry, history detail, strength-score and profile customization. No parallel engine, chart calculator, CSS patch layer, hidden features or extra nav destination. Remove obsolete owners instead of accumulating them. A change counts as finished only after **actual rendered** browser checks and exact-head CI; do not infer physical Android parity from emulation.

## M335 — from visual reference to decision hierarchy

**Status:** Implemented in M335 branch; merge and device certification are separate. The supplied phone/tablet prototypes informed the *principles* below. They are not an implementation source, component library, or feature specification.

- **Prototype trait worth transferring:** a single obvious selection target and subordinate metadata. **Pursuit Iron application:** Settings's existing native section selector is now unmistakably a section navigator, not a search input. It retains all ten named destinations, existing deep-link anchors and the saved data owners; no new settings destination, navigation system or duplicate preference cards.
- **Prototype trait worth transferring:** actions follow the question the user is answering. **Pursuit Iron application:** a cycle-block preview opens with clear future-versus-saved scope, its selected week and canonical saved week outlook, then actual exercise rows. Split explanation and regional-dose reasoning remain available *after* the training itself. It still uses the same `CycleWeekOutlook`, `computeCell`, `CycleDoseTransition`, `onViewPlan` and native disclosure owners.
- **Prototype trait worth transferring:** one quiet and repeatable control language. **Pursuit Iron application:** the section navigator's previous fixed inline padding, borders, colors and typography move to its existing `app.css` owner with semantic tokens. The current motion controller remains untouched; reduced-motion preference now also governs its navigation scroll.
- **Do not copy:** fixed colors, mock data, example workout cards, decorative transitions, oversized tablet whitespace or the prototype's inline CSS. No workout set controls, timer, saved history, gym constraints, adaptive algorithm, volume calculation or program-edit mutation changed in this pass.

**Acceptance:** release integrity and content-addressed offline cache; source/engine parity with cycle conversion, split frequency, all-week volume/equipment and actual history; program, workout, motion, accessibility and integration browser shards. On device, independently inspect Settings section picker with keyboard and TalkBack plus Cycle Preview at narrow/foldable widths. Do not report Android readiness from browser emulation.

**Remaining:** redesign the complete creation → comparison → generated cycle review → preview → Start sequence as a *single* deliberate experience after measuring rendered screen and interaction evidence. The improved preview alone is not completion of that end-to-end flow.

## M337 — setup navigation and responsive review

The creation form now keeps Back/Next ahead of its saved-draft status. Explicit Discard remains in the scrolling cycle form instead of competing with the primary navigation in the fixed footer. Failed-build status, retry, optional early generation and automatic draft recovery retain their existing owners.

Compact windows expose a named, keyboard-operated Review setup disclosure after the current question. Its contents read the same live configuration as the existing desktop overview; opening it never generates or saves a program. Expanded windows keep the always-visible side pane. Resizing preserves form answers and the compact disclosure choice. Styles extend the existing Wizard family with published tokens, without new inline paint or priority declarations.

Verification extends the existing R65 responsive and R74 form journeys: current answers in the summary, keyboard order/Space activation, 200% text, compact/expanded layouts and retained configuration. Existing custom-cycle coverage remains responsible for authored block ordering, incomplete draft recovery, failed-build retry and explicit discard. Engine/cycle contracts, independent export parity and all release/browser gates remain required on the final revision. These are browser fixtures; physical Android validation remains separate.
