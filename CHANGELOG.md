## Build 940 / M394 — Safer Program edits and prescription integrity

- Set and rep fields retain raw typing until Apply, with inline errors and Cancel. Complete target validation prevents malformed text from becoming a different rep target or a guessed progression range. Reps-only changes preserve untouched weekly set schedules.
- Program exercise rows share styles and scalable labelled controls. Review, Start and context changes return focus to unfinished edits; system Back uses the same guarded Back action and saved-draft review.
- Malformed saved targets lead from Preview to Program for repair and cannot start a partial new workout. Existing workouts keep their persisted set values and remain resumable while the current program needs repair.
- Retire 18 static inline style objects and 33 external priority overrides; tighten the existing budgets. Training formulas, logged history and protected Workout/Focus geometry retain their existing owners.

## Build 939 / M393 — Room to browse and cohesive Library details

- Keep only a compact Library toolbar pinned. Search, muscle/equipment filters and All/Recent/Goals/Banned choices scroll with the exercises. Find brings the real search field back without clearing selections; Compare remains available in the toolbar.
- Move list, empty-state, detail, notes, history, records, attachment, technique and goal presentation into semantic CSS. Retire 115 inline style objects and 148 priority overrides, including the old rules that flattened rows and squeezed muscle badges. Keep text scaling, rounded rows, keyboard focus and the existing adaptive list/detail pane.
- Goal editing accepts a complete, finite positive weight. Malformed input no longer silently saves a numeric prefix or a guessed replacement. Add Cancel and explicit Remove goal, visible units, scalable controls and independent drafts per exercise.
- Preserve all original catalog/record projections, pinned notes, bans, training prescriptions and workout recovery. Engine 0.65.9 remains unchanged.

## Build 938 / M392 — Readable Settings choices and shared styling

- Fix theme names remaining at 11.5px when actual root text doubles. Theme labels and group captions now scale; the grid adapts to the text size and available width.
- Theme selection reuses UiChoice and native disclosure/keyboard behavior. Only real palette swatches retain data-driven inline paint.
- Consolidate six preference groups, theme presentation and Settings scroll/anchor spacing into semantic CSS. Retire 28 inline style objects, 15 external priorities and nine embedded priorities; lower the existing ceilings to 2,256 / 2,173 / 1,134 respectively. Repeated priorities stay at 14.
- Keep all original preference callbacks, training algorithms and persistence owners. Existing R76 now checks actual text scaling, theme/preference keyboard selection, persisted values, reversal and authored-content/history preservation.
- Engine 0.65.9 is unchanged. Physical Android/IME/TalkBack acceptance remains open.

## Build 937 / M391 — Even Library spacing and calmer motion

- Browser screenshot review: preserve a one-line Library title with a screen-reader-accessible result count; at narrow widths, correct the existing priority flex owner so Plan's Progression label uses two rows rather than breaking mid-word.

- Library header, search, filter strips and rows share spacing, typography, accessible target sizes and theme paint instead of 19 inline style objects. Search, selection, exercise detail and saved training retain their behavior.
- Primary navigation uses shorter one-axis lead-card motion, avoiding diagonal source-linked overshoot. Reduced-motion and system Back handling remain unchanged.
- Engine 0.65.9 and storage schema are unchanged; physical Android acceptance remains open.

## Build 936 / M390 — Stable weekly loads and load-grid regression

- Preserve the prior load across a week-to-week rep/RIR change when each completed straight set used it, met the new rep floor and the estimated decrease is at most 0.5 lb / 0.25 kg and 2%. The held load must remain selectable in the actual equipment inventory.
- Preserve meaningful weight reductions, misses, mixed loads and discrete machine notches. Earned progression and logged workout evidence are unchanged.
- Add the reported 50 lb -> 49.5 lb regression to mandatory integration contracts. Engine 0.65.9 remains in place.

## Build 935 / M389 — Safe history correction and shared editor styles

- Reproduce and prevent a logged-set loss: typing `92..5` and saving previously filtered a real set out of the completed workout. Retain raw typing; compare staged sets and values with canonical normalization before saving. Incomplete/malformed loads, numeric overflow, fractional or blank reps, invalid/clamped RIR and fractional minutes keep the editor open without touching saved history.
- Move the correction form through the existing Exit portal outside virtualized/contained history rows. Its named dialog, independently scrolling body and persistent Close/Cancel/Save controls fit the visual viewport and clear an overlaid keyboard.
- Extract the correction view into one feature module using shared actions, live theme roles and class-owned fields. Retire 31 inline style objects; tighten the combined budget to 2,303. Add no priority overrides or storage/engine schema.
- Preserve original program/day/week/unit ownership, sub-sets and target metadata. Keep all numerical decisions with normalizeEditedHistoryEntry, all persistence/revision stamps with the existing App owners, and existing performance-mirror/Undo behavior.
- Give each editor opening a new draft identity so reopening during the shared closing animation cannot revive canceled edits. Extend the existing history import/browser gate with four phone/tablet/short-window and normal/200% text correction journeys, actual Save/Cancel/Undo/reload, interrupted closing, explicit set/lift removal, numeric refusal, keyboard clearance, focus and real browser Back. Keep the 173-gate registry unchanged.
- Fix unresolved shared palette roles for completed-day markers and incomplete training maxes, using the existing on-accent and warning roles.

## Build 934 / M387–M388 — Shared menus, configuration styles and stable navigation

- Fix two reproducible scroll/interaction races: Home's saved-program target moved 60px after automatic scrolling, and Program's metadata remained 17px tall for an extra frame. Home context now scrolls naturally below a fixed app bar; Program applies its existing header geometry before next-frame pointer positioning.
- Consolidate Home program actions, Program day actions and the day picker into one menu renderer. Retain all original actions, selected/done/next-day facts, program transactions and Undo behavior.
- Measure real menu rows and current anchors instead of guessed heights or stale click rectangles. Reflow after text/viewport changes, clear the live-workout dock and navigation, and keep long labels complete.
- Focus the first action on open; preserve arrows/Home/End, reveal offscreen focused rows inside short menus, and return focus on dismissal. Tab closes the menu. Keep the existing keyboard, Back and motion owners.
- Consolidate the program switcher and gym editor into one configuration-sheet anatomy. Static presentation uses shared classes and theme roles; existing owners retain program selection, disabled future blocks, equipment, rack limits, bar weights, staged edits and cancellation.
- Retire 170 CSS priority overrides and 92 inline styles across the menu/navigation and configuration passes. Remove superseded screen-specific layers and a shadowed achievement width. Tighten external/embedded/inline budgets to 2,188/1,143/2,334; add no priority declarations.
- Compare populated sheets in both themes at four widths and normal/200% text. At narrow enlarged text, place guidance below title/Done and keep program names on a full row. Preserve normal-size geometry; allow gym choices and Done to follow enlarged text rather than fixed priority fonts. Extend existing switcher search/empty-state and gym keyboard/cancel/short-window/unit-conversion journeys.
- Keep configuration sheets above an overlaid phone keyboard through the shared backdrop visual-viewport ceiling. Extend existing navigation, menu, contrast, resize and stress journeys. Training-engine policy, saved prescriptions, history and live set ownership stay with their current owners. Physical Android/IME/TalkBack certification remains open.

## Build 933 / M386 — Safe decimal training-max editing and truthful review

- Preserve decimal points and intermediate text while entering a training max. Typing 212.5 previously produced 2125, which could multiply percentage-based workout loads.
- Stage all maxes until Apply; Cancel, Close and dismissal discard the staged edits. Apply updates the existing program draft, which still requires reviewed Save. Reject invalid or nonfinite input rather than silently stripping characters.
- Keep the existing 90% one-rep-max conversion and canonical loading-step rounding in one inline conversion panel. Back/Escape closes conversion first and restores focus to its lift.
- Show canonical staged percentage previews and exact training-max before/after values with units in save/restore review. Preserve stored-unit conversion and untouched maxes, prescriptions, logged history and entered live sets.
- Extract the editor and entry card into one feature owner, with labelled scalable fields and a persistent Close/Apply/Cancel frame. Retire 27 inline styles, tighten the combined budget to 2,426 and add no priority overrides.
- Extend existing M225 browser and B905 review contracts; retain percentage workout, lb/kg, deload, protected resume and actual finish coverage. Physical Android/IME/TalkBack acceptance remains separate.

## Build 932 / M385 — Consistent pinned-note and setup editing

- Stage physical setup and note text together in Program, Workout and Library. Save applies both; Cancel discards both. Remove clears the saved note and every saved setup field, including older equipment settings.
- Use one controlled editor and one pair of App persistence callbacks. Keep saved cues exercise-wide while scoping the active Program editor to its selected day and slot, including repeated exercises.
- Place the Program editor across the exercise card, with labelled setup inputs, 44px edit targets, scalable text, wrapping actions and visible focus/forced-color borders. Preserve the closed Workout/Focus logging layout.
- Retire 29 static inline styles into one shared CSS family and tighten the presentation budget from 2,482 to 2,453 without adding priority overrides. Engine policy and storage schema stay unchanged.
- Extend the existing R54 journey with both themes, narrow/enlarged-text reflow, reachable actions, Save/Cancel/Remove, old setup-field retention and explicit clearing, reload, cross-screen persistence and unchanged saved prescriptions, history and live set values.

## Build 931 / M384 — Usable exercise actions and one presentation owner

- Program exercise options scale the title, action labels and complete descriptions with the text-size setting. A persistent heading keeps Close visible while the action list scrolls.
- Restore the existing sheet drag-to-dismiss handler across options, gyms, program switching and achievements: presentation flags no longer prevent initialization by node identity. Keyboard focus, Escape and actual touch dismissal use the same shared owners.
- Keep removal confirmation inside the sheet, reset it on dismissal, and preserve all existing note/info/ban/removal handlers. Do not offer supersets when disabled or on the last exercise; protect the final remaining exercise from removal.
- Extract the one options renderer from App, retire 17 inline styles and 34 priority overrides, and tighten the measured ownership guards. Engine policy, prescriptions, persistence and Workout/Focus geometry remain unchanged.
- Extend existing R54 with 320/393/834/1280px and 100/200% text, scroll/Close, focus, forced colors, real browser touch, confirmation Cancel/reset and constrained actions; retain B831 contrast, geometry and motion checks on the semantic structure.

## Build 930 / M383 — Accurate selected-week sharing and readable plan inspection

- Share as text now reads the selected executable week, including alternating exercises, rotating days, deloads and authored targets. Percentage loads come from the same canonical reader as Workout, preserving units and rep-out sets instead of reimplementing scheme formulas.
- Share program has a named, height-bounded dialog, labelled selectable text, visible Close/Copy controls, contained keyboard focus, and explicit clipboard success/failure feedback. It follows enlarged text, reduced motion and forced colors.
- Load-wave values and week labels follow text size, retain full accessible names, and scroll within the chart at narrow widths. Keep the existing planned-set and relative bar-height computations.
- Move sharing into one feature owner and retire 19 static inline style objects across sharing and the chart. The counted combined presentation budget tightens from 2,518 to 2,499, without new CSS priorities or engine/persistence changes.
- Extend existing R54 selected/deload-week and responsive sharing coverage, R53 canonical chart/keyboard coverage, and M225 percentage export parity. Physical Android keyboard, TalkBack and interruption acceptance remain separate.

## Build 929 / M382 — Readable, accessible volume inspection

- Expandable muscle headings and granular details have 44px targets and follow the shared text-size tokens. Contributor rows place long session labels above exercise names on compact layouts.
- Block volume labels stay visible while scrolling later weeks; the table supports keyboard scrolling and enlarged text. Chevron motion respects reduced motion and focus stays visible in forced colors.
- Retire 38 static inline style objects into the existing themed stylesheet, and include both extracted volume owners in the existing style-debt budget. Engine policy, volume calculations and saved training keep their current owners.
- Extend the existing R53 browser journey with twelve canonical contributions, long labels, 100/200% text at 320/393/900/1280px, native keyboard controls, granular details, block/deload parity and immutable saved prescriptions.

## Build 928 / M381 — Complete, readable muscle-volume contributors

- Expanded muscle drilldowns show every contributor instead of stopping after eight; session and exercise labels wrap rather than being visually truncated.
- Canonical MEV/MAV/MRV landmarks, fractional muscle credits, program generation, active prescriptions, workout logging, and history remain unchanged.
- Extend the existing R53 browser contract to verify full contributor readability. Retain Material 3 Expressive motion and spacing owners, publish only after all release and browser checks, and keep physical Android/TalkBack as a separate acceptance gate.

## Build 927 / M380 — One owner for volume presentation and accessible drilldowns

- Extract shared muscle-volume bars and block-volume ramp into `modules/program-volume-surfaces.js`. Plan, Program and Progress use exactly one renderer and the unchanged canonical engine callbacks for zone thresholds, working sets and fractional muscle credits.
- Replace pointer-only volume-contributor headings with native keyboard-operable controls, accessible labels, `aria-expanded`, and focus-visible styling. Logged-history rows remain ordinary non-interactive labels.
- Retire obsolete historical volume-zone design commentary but preserve the MEV/MAV/MRV boundaries and composite-head MRV guard.
- Extend existing R53 Plan browser contract with Enter and Space toggling and no saved-data mutation. All existing engine, lifecycle, program/workout, accessibility, responsive, offline and hosted integrity gates remain required; real Android/TalkBack acceptance is separate.

## Build 926 / M379 — Single-owner Plan load-wave presentation

- Extract the Plan Blueprint **Volume load wave** from the oversized App owner into `modules/program-load-wave.js` without duplicating prescriptions, week counts, or training state. Pass only the existing palette from App; get planned sets from the unchanged canonical engine.
- Keep one-based weeks and the configured optional final deload; do not confuse planned working sets with fractional weekly muscle-volume credit. Reduce combined production UI source size and remove retrospective inline comments instead of adding a compatibility adapter or CSS overrides.
- Extend the existing R53 real-browser Plan contract to assert ordered week numbers, nonnegative planned sets and no misplaced deload; require exact-head lifecycle, engine, browser and release integrity, then hosted Pages byte checks. Android IME/TalkBack remains a separate physical-device gate.

## Build 925 / M378 — Stable selection geometry with one motion owner

- Plan, Progress and Library segment changes keep adjacent choices and touch targets stationary while their measured selected indicator moves. Remove redundant indicator stretch/corner springs; keep the quiet root-navigation budget from Build 924.
- The workout effort-scale spring remains, as do keyboard selection, theme-aware indicator color, reduced motion, accessibility and all canonical engine 0.65.9, persisted data, logging, and backup boundaries.
- Update the existing B831 browser contract to require stable selected geometry; full lifecycle, engine parity, browser, offline and hosted integrity gates are mandatory before launch publication. Real Android IME/TalkBack acceptance remains separate.

## Build 924 / M377 — Calmer root navigation without competing animation

- Ordinary Home/Plan/Progress/Profile/Settings navigation now animates at most the lead content card, rather than staggering up to five cards and bouncing the newly selected tab icon on top of the indicator. Preserve the one measured navigation indicator, spatial direction, and actual tapped-source relationship when appropriate.
- Keep native tab routing, 48px+ controls, actual selected states, all five tabs, keyboard navigation, fast switching, reduced motion, exercise detail transitions, sheets, and protected Workout/Focus behavior unchanged. No training, engine, storage or saved-history changes.
- Extend the existing B831 full-browser motion owner with a one-card entrance budget and no nav-icon bounce. Required before publication: source and lifecycle checks, seven browser groups, release/offline integrity, exact merged-hosted bytes and live online/offline navigation. Device/IME/TalkBack acceptance remains separate.

## Build 923 / M376 — Faster history replay and fresh coaching

- Build one current catalog lookup and source snapshot per historical analysis instead of rebuilding them for every workout. Read loading defaults without cloning them before normalization; returned inventories remain independent and editable.
- Reuse the session review's proposal when no saved review choice changes it, preserving separate set records, saved choices and Undo while avoiding a second identical history replay.
- Fix stale coaching after corrections to observed-effort provenance or the current program, and refresh time-dependent recovery without requiring a new workout. Preserve the existing calculation formulas and factual history.
- Extend the existing browser performance owner with full-app/240-workout startup, repeated real-pointer recovery, exact state preservation and a bound on redundant historical work. Retain the keystroke/completion/Undo suggestion-reuse guard and matched reference measurements.
- Keep the Home greeting and Settings gym name/count complete at enlarged text. Remeasure the Home context when its content size changes, and limit the stacked-row flex rule to its intended form rows; preserve smooth scrolling and existing controls.

## Build 922 / M375 — Protect training across app windows and offline navigation

- Prevent a stale second app window from autosaving over a workout completed elsewhere. One window owns device training/recovery storage; a second waits before opening it and can retry after the owner closes.
- Pause saving if an older app version or external writer changes training storage. Keep the current window's backup available before reloading current saved data.
- Serve the actual privacy policy from an installed app, including offline. Certify and precache the policy instead of substituting Home for every document navigation.
- Verify concurrent windows, live-state preservation, completion, ownership release, external-write recovery/export, cached navigation, offline policy content and the existing crash/update lifecycle. Refresh outdated public README claims.

## Build 921 / M374 — Readable restore review and backup ownership

- Make backup/restore guidance, merge impact, CSV summaries and unit choices respond to enlarged text. Keep each record with labeled Add/Update/Remove counts on narrow screens, with table/header semantics preserved for assistive technology.
- Move the existing backup UI and transfer handlers out of App.js into one feature owner. Retain the App's storage, merge, compatibility, unit conversion and Undo transactions; no engine or history policy changes.
- Retire 43 inline style objects and tighten the combined presentation budget to 2558. Use the existing shared controls and theme tokens without new CSS priority declarations.
- Extend the existing browser fixture with enlarged JSON/CSV previews, exact count mappings, accessible headers, full-file restore/Undo and actual CSV unit-selection/import/Undo, alongside transfer failure/cancellation and stale-file protection.

## Build 920 / M373 — Faster cycle creation through canonical exercise reuse

- Reuse the existing immutable knowledge projection for each exact built-in catalog definition. Custom definitions and edited/snapshot copies are read afresh, including same-ID copies; keep one canonical catalog and projection formula.
- Freeze nested secondary-contribution records so consumers cannot corrupt shared facts. Reuse is lazy and bounded to the built-in catalog.
- Verify all 376 catalog projections and 752 relationship comparisons against fresh copies, mutable-custom isolation and complete generated/upgraded/advanced cycle byte parity at fixed IDs/clock.
- Extend the existing performance harness with an uncached reference control. The final local control/candidate pair reduces complete cycle creation from about 3.3s to 1.0s native, and 14.7s to 4.4s with simulated 4× CPU; timing is environment-specific and is not physical Android certification. Retain the runtime-bound measurement and exact-parity evidence.
- Preserve Engine 0.65.9 training algorithms, accepted plans, constraints, manual targets, historical facts and the protected Workout/Focus layout. Retain the logging regression that prevents repeated suggestions during input/completion/Undo.

## Build 919 / M371 — Executable progression choices and exact save review

- Fix exercise-wide progression choices that changed the editor/session selection but left generated engine cells on their original method. The canonical projection now honors the selected method, including alternating exercise identity; Auto reveals the saved slot/week schedule without rewriting it.
- Share one progression-choice writer between Program and Workout. Preserve original generated prescriptions, manual set entries, completed facts and historical method snapshots; remove empty choice maps when returning to Auto.
- Replace the clipped progression-method sheet with a named dialog, persistent Close action, explicit program scope, shared selected controls and wrapping explanations. Remove 13 static inline style objects and add no CSS priorities.
- Show the current method in each weekly progression cell and identify the original generation rationale as historical. Read method names from the existing shared catalog.
- Program save review now identifies method-only edits accurately and offers exact before/after settings and weekly prescriptions. Reuse the existing revision comparison renderer; scale review typography with text size, retain keyboard containment and keep actions visible.
- Extend existing lifecycle/browser contracts across all six methods, numeric classic-double vs DDP behavior, Auto restoration, old engine mirrors, alternating identities and immutable history. Exercise the real editor save → Preview → Workout → method change → reload/resume → completed log → reviewed program update. Engine 0.65.9 progression algorithms remain unchanged.

## Build 918 / M370 — Executable plan details and authored settings validation

- Correct the progression table's week-zero offset. Use canonical session projections for each visible week, include the final week and deload, and show the actual alternating exercise and effort target.
- Keep Plan Schedule and Volume on the same browsed week; retain the week controls in Volume and restore the selected week after tab navigation. Count actual working sets separately from fractional per-muscle set credits.
- Refuse newly requested custom-program bodyweight and barbell constraints when existing exercises conflict. Explain the affected day and exercise before Save; retain authored choices and saved training.
- Validate newly entered block lengths from 1–52 weeks without silently rounding or truncating them. Preserve existing longer durations during metadata edits and keep cycle-owned lengths unchanged.
- Give Plan Info a persistent Close action, wrapping labels, accessible shared disclosures and responsive progression cards. Consolidate the reference/disclosure presentation in two feature modules and remove 56 inline style objects with no added CSS priorities.
- Extend existing browser and domain gates for canonical prescription parity, alternating lifts, selected deload volume, settings refusal/save/reload and 320–900px layouts at 200% text. Engine 0.65.9 progression policy, manual prescriptions and historical training remain unchanged.

## Build 917 / M369 — Release-content ownership and current in-app notes

- Move 201,450 characters of release history out of App.js into one dedicated catalog. Preserve all 248 historical releases, 516 entries, mixed formats and icon references exactly.
- Retire unused App icon imports and release-data exports; browser verification imports the canonical catalog directly. Count archived entries without eagerly projecting all of their metadata at startup.
- Show current launch improvements in What's New and its Settings replay; preserve dismissal, the full archive, existing notes and saved training.
- Include the new module in release/offline hashing and source ownership checks. No engine, storage or Workout/Focus geometry changes.

## Build 916 / M368 — Shared settings controls and reliable Home actions

- Fix a delayed Home-header collapse that moved program buttons by 60px after scrolling and could make Open/Switch clicks miss. Apply the existing collapse in the scroll event and remove the deferred callback.
- Program Settings now uses the same shared choice controls as the rest of the app, with 48px minimum targets, scalable labels and explicit form groups.
- Separate form scrolling keeps Save, Cancel and the dialog heading visible. Choices wrap at narrow widths and enlarged text; selection remains legible in forced colors and reduced motion.
- Move six stateless UI primitives into one reusable module; remove all 46 remaining Settings inline style objects and obsolete presentation props. No added CSS priorities.
- Preserve the existing program transaction, constraints, duration validation, cycle ownership, saved history and Engine 0.65.9. Browser checks exercise staged changes/cancellation, focus restoration, 200% text and creation → Preview → Start → resume.

## Build 915 / M367 — Program Settings dialog semantics and CSS ownership

- Treat the Program Settings sheet as one named modal dialog, with explicit Folder and Description labels and selected-state announcements for existing option and constraint controls.
- Move 12 static inline style objects from the sheet frame, header, form and save bar to semantic theme CSS; retain valid/invalid duration controls, staged drafts and Save/Cancel behavior.
- Tighten the complete cross-module inline-style ceiling to 2,716. Keep training engine 0.65.9, existing programs and workout history unchanged.

## Build 914 / M366 — Program Settings ownership and block duration styling

- Extract the Program Settings dialog into a feature-owned renderer; keep draft transactions and save/cancel control in ProgramView.
- Replace 10 static block-duration styles with theme CSS, including valid/invalid numeric state, disabled increment controls and keyboard focus treatment.
- Keep cycle-owned duration, saved program state, existing workout history and Engine 0.65.9 unchanged. Add extracted module to style regression accounting and reduce the debt ceiling.

## Build 913 / M365 — Program schedule presentation ownership

- Move the weekly schedule sheet out of App.js into one Program feature module, preserving the existing schedule reducer and swipe-to-dismiss behavior.
- Remove 15 inline JSX style objects in favor of themed CSS roles, with responsive focus, reduced-motion and forced-colors coverage.
- Include extracted source in the UI debt guard. Keep workout, cycle, saved-program and history data unchanged; Engine 0.65.9 retains the same identity.

## Build 912 / M364 — Keyboard-contained Program save review

- Give the save-review dialog a clear initial focus, Escape dismissal and Tab/Shift+Tab containment. Return keyboard focus to the invoking control when the review closes.
- Preserve conflict-disabled saves, linked cycle scope, saved program transactions and completed workout history. No engine or storage schema change.
- Add a regression guard for keyboard and focus behavior; keep the canonical Engine 0.65.9 unchanged.

## Build 909 / M362 — Prelaunch computation-boundary cleanup

- Retire 256 duplicate engine exports from App.js and migrate 49 verification consumers to the canonical engine API. Pure computation tests and standalone parity no longer need the UI module.
- Remove 280 unused imports, an unused cycle renderer and two unused constants; consolidate the UI's engine imports. Preserve training behavior, saved programs, history and backup compatibility.
- Copy standalone semantic tests without UI-import rewrites. Enforce the single computation boundary alongside the existing independent golden scenarios and lower the inline-style budget after deleting unused presentation.
- Prioritize feature ownership and shared styling in the launch plan. This pass reduces proven dead code; the large UI module and remaining presentation debt still require cleanup before declaring launch readiness.

## Build 908 / M361 — Explanations match the load you can actually use

- Reconcile prescribed-load explanations after the final equipment rounding. A 105 lb straight-set estimate rounded to 100 lb now says “Use 100 lb”; converted kilogram targets follow the same final values.
- Make “Why this prescription?” consume the same final suggestion as the workout, including selected units and eligible user choices. It no longer bypasses equipment rounding to show an earlier engine estimate.
- Read evidence from that suggestion’s matching raw exposure. Preserve day identity, normalize corrected records, exclude warmups/unfinished sets, and distinguish reported RIR from planned effort. Historical values convert once and display without long decimal tails; saved history stays unchanged.
- Extend existing source and browser regressions through the actual Repeat → set rows → reason → Why → Focus → reload flow in pounds and kilograms. Training progression policy, workout layout and engine version remain unchanged.

## Build 907 / M360 — Numbered web releases and retained training fixes

- Give all shipped M321–M359 fixes a new visible build: saved cycle/week totals and equipment-aware previews, history-scoped progression, recovery safeguards and deliberate set-effort protection.
- Advance the canonical build with `node scripts/finalize-release.mjs --next-build`. Settings, startup diagnostics, the release label and the offline cache receive the same build; repeated ordinary finalization stays stable.
- Release CI compares with the actual PR base or pre-push release and rejects changed shipped bytes without a higher build. Source-only verification changes may retain their build.
- Exercise installed-app Restart, the visible Settings build, offline reload and preserved storage in the maintained PWA update browser journey. Keep restart deliberate so an update does not interrupt training.
- Verify GitHub Pages files against the exact merged release before claiming deployment. Engine version remains 0.65.9; no training-data migration is implied by the build number.

## Build 906 / M320 — Adaptive training-cycle release

- Publish the audited adaptive-cycle work from M311–M319 under a visible new build identity, while retaining app version 4.0.0 and canonical engine version 0.65.9 to avoid inventing an engine migration.
- Existing standalone custom programs and saved fixed cycles can upgrade future blocks to adaptive exercise and split selection. Keep the original current program, training history, manually owned sets and reserved preview IDs intact.
- Finalized hypertrophy-to-strength transitions compare audited split finalists using executable weekly prescriptions, athlete-prioritized lift frequency, focused/supporting muscle continuity, and bounded set-volume protection. Refuse infeasible transitions rather than overwriting the saved program.
- Reduce duplicated weekly simulation and exercise-catalog work (M317/M319). M318's 320px mobile Chromium CPU-throttling benchmark remains in the permanent browser suite; it does not certify a physical Android device.
- Complete release integrity, independent engine audit, source-generation contracts and the complete phone/offline browser matrix before merging. GitHub Pages deployment follows the repository's existing main-branch Pages configuration. A signed Android App Bundle and Google Play Console submission are separate, **not included in this build**.

## M311 — Upgrade existing custom cycles to adaptive training

- Existing custom cycles now offer **Upgrade future blocks to adaptive** in Cycle options. The saved plan stays active; future exercise selections, strength-specific split optimization and training-frequency targets update at the next legitimate block boundary using real workout history.
- Preserve every existing session, manually edited set, current exercise, old block, prebuilt preview and user-authored future phase. Never silently overwrite a preview that already has workout history. Older legacy-first conversions retain their mandatory first fixed bridge and then become adaptive.
- Standalone custom programs retain their existing Turn into Cycle path, now explicitly labeled as an adaptive upgrade with both adaptation switches enabled by default.
- Add engine invariants and real phone/desktop conversion-to-upgrade browser coverage, plus M310 fixed-vs-adaptive cycle quality contracts.

## M310 — Real fixed-versus-adaptive training cycle quality matrix

- Deterministic before/after comparison of fixed-split and goal-adaptive cycles with identical seed, athlete, training time, equipment, focus, and week counts for four- and five-day examples.
- Verify executable weekly sets, session clocks, phase transitions, lift-practice counts and exercise-level sets, reps and RIR. Emit real generated workout examples for audit rather than inventing illustrative numbers.
- This milestone adds verification, not an unvalidated production training rule.

## M309 — Audited focused dose reallocation at strength entry

- Attempt real, bounded **working-set changes** when a prioritized muscle drops below half of the final hypertrophy week's credited dose in the first strength week. Prefer adding an existing focused accessory set within capacity; otherwise try an equal-set same-session transfer from a non-focused, non-strength accessory.
- Apply moves consistently across each work week, preserve exercise identities, all strength anchors, explicit user edits, progressions and RIR prescriptions. Each proposed result is checked against session time limits, public regional floors/ceilings, prioritized-region no-loss guards, and full weekly engine audits. Reject any move that fails.
- Keep prior-dose goals capped by useful strength-phase prescriptions: strength does not inherit a hypertrophy quota. Persist a truthful improved/limited explanation when no safe reallocation exists and let existing Cycle dose comparison report the actual executable result.
- Retain M307 seeded back, delt, chest and posterior-chain regression coverage, including independent width/thickness accounting.

## M307 — Focused muscle-region continuity at strength entry

- Audit generated 4/5-day hypertrophy-to-strength transitions against the real executable final hypertrophy week and first strength week, separately measuring lats, upper back, side/rear delts, chest, and posterior-chain focus.
- Refine the optimizer's **ranking-only** regional carryover: normal back regions retain the established 50/50 fraction of the aggregate phase target, while explicitly prioritized back work may rank candidates against 60% per region, bounded by previous productive doses. This makes insufficient lat work visible even when the reduced strength target is fully met.
- Keep all hard time, weekly fatigue, equipment, exercise preference, lifting-frequency, and complete-program audit constraints. No fixed minimum working sets or hypertrophy-volume quotas are added; maintenance and extreme prior-dose safety remain unchanged.
- Flag explicitly prioritized muscle regions that lose more than half their actual credited working sets across the phase boundary (when at least four prior credited sets were present). The compact Cycle disclosure visibly calls out these focus reductions without automatically prescribing extra strength-phase sets; ordinary and maintenance regions are not flagged.
- Add seeded focus-specific output diagnostics plus unit regressions for back-width/upper-back credit, recovered focus, maintenance exclusions, extreme prior volumes, and UI display parity. Retain Build 905 / Engine 0.65.9 and synchronize release/offline hashes.

## M306 — Compact cycle volume explanation and edited conversion regression

- Show the new M305 final-week-to-first-week regional dose comparison within the existing Cycle **Up next** card and the read-only future-block preview. Keep the full muscle breakdown collapsed by default. Display explicit before/after working sets, credited regional doses, and signed percentage changes with clear baseline semantics.
- Preserve the existing cycle screen hierarchy and design tokens. The prior opening-week set comparison is replaced with the true phase-boundary count whenever canonical transition metadata is available; older saved cycles keep their previous labels and do not invent missing information.
- Extend cycle source/UX contracts and responsive phone/tablet browser coverage to validate the disclosure, its accessibility and narrow-screen fit. Add a conversion regression for high user-owned set prescriptions (30+ set day), requiring an audited volume-preserving transition or clear refusal without overwriting the source.
- Keep Build 905 / Engine 0.65.9. No dose optimizer, strength-specific frequency, training target or fatigue policy is relaxed; update offline and release integrity hashes.

## M305 — Explain actual regional dose changes at strength entry

- Distinguish phase-adjusted regional target coverage from absolute productive training dose. A 100% strength-phase target score can coexist with a substantial reduction from the preceding hypertrophy block; do not label that as 100% volume retention.
- After final executable prescriptions are materialized, compare the previous block's last work week with the next strength block's first week using the canonical shell volume snapshot and public regional dose ledger. Preserve positive fractional set credits, show before/after sets, signed set change, percentage change and total weekly working-set change; a newly trained region has no defined percentage baseline.
- Persist the exact working-week comparison in both the cycle block's split decision and the generated program's cycle context, including for converted standalone programs and dynamically adapted transitions. It is explanatory metadata only; no extra volume quotas, exercise selection or fatigue override.
- Expand the existing M304 generation contract with verified actual-week regional accounting, target-versus-dose semantics and program/cycle metadata parity. Retain Build 905 / Engine 0.65.9 and refresh release/offline hashes.

## M302 — Priority-weighted regional carryover in strength entry

- The cycle split optimizer now compares hypertrophy-to-strength candidates against each region's actual prior productive dose as well as the target strength phase's preferred dose. This closes an objective gap where two fully audited plans could meet the new aggregate targets but one unnecessarily discarded substantially more chest, back, leg, shoulder, or arm work.
- Use a bounded continuity penalty (at most 15 score points), weighted toward athlete-prioritized muscles. The comparison caps required preservation at the new phase's productive preferred dose, excludes explicitly reduced/maintenance muscles and does not apply to peak, intensification or recovery tapers. It does not invent hard regional quotas, reallocate sets outside canonical audit, or override lift specificity, session time, equipment or recovery.
- Store the inspected region-retention percentage and at-risk region identifiers in the selected split decision. Regression coverage verifies focused regions, maintenance, reasonable phase reductions, non-mutation and real hypertrophy-to-strength cycle metadata. Build 905 / Engine 0.65.9 with refreshed offline hashes.

## M301 — Phase-owned goals in every cycle block

- Always project the block's phase into its request goal, even when the weekly split, days and lift-practice controls are unchanged. An early return previously retained the entry goal across static/locked cycle phases, letting hypertrophy-origin strength blocks inherit hypertrophy-oriented loading, strength claims and regional dose assumptions.
- Keep baseline equipment, schedule, preferences, athlete and seed immutable. Strength accumulation, intensification and peak use strength-goal policy; hypertrophy uses hypertrophy. Mixed-style blocks keep the cycle's explicit strength intent when present (including Strength Peak's base), otherwise use mixed. Phase-specific exercise/volume/safety audits remain canonical.
- Regression-check all six authored phases, normalized strength weighting, unchanged weekly configuration, immutability, optional split adaptation, and generated cycle blocks' actual requests. Retain Build 905 / Engine 0.65.9 and refresh offline hashes.

## M300 — Preserve strength-entry volume rescue with unchanged automatic splits

- Automatic split optimization annotates even an unchanged original split as an explicit selection. Treat that as the same executable weekly topology for the existing audited, locked-roster volume fallback when a hypertrophy block enters strength accumulation with an otherwise unacceptable set cliff.
- Only use the fallback when split and training-day count really match, and never when a new priority-lift frequency was requested. Changed schedules and strength-practice requests continue through full generation, auditing and feasibility checks.
- Extend the permanent custom-cycle regression to verify the positive same-split case and rejection of changed split, days or practice. Keep Build 905 / Engine 0.65.9; refresh offline bundle integrity.

## M299 — Cycle split and schedule ownership after block overrides

- Keep an authored split or training-day override scoped to its own block. The next block without overrides returns to the cycle's baseline topology, rather than silently keeping the preceding block's actual sessions while showing the baseline in settings.
- Compare the target block's effective split and training-day count with its source. Rebuild and audit the executable weekly schedule when either changes, including first-generation, conversion, and history-driven advancement. Preserve locked rosters when the topology is unchanged.
- Extend the maintained custom-cycle integration contract with a three-block baseline → override → baseline regression checking settings, engine sessions, request days, audited status, and input immutability. Retain Build 905 / Engine 0.65.9 and refresh offline hashes.

## M298 — Executable custom cycle transitions and repeat

- Base cycle review thresholds on each block's actual training days and authored duration. One-week blocks can finish after their prescribed work with favorable evidence; older saved previews receive the same correction when read. Standalone programs and recovery exit criteria retain their existing evidence rules.
- Preserve the active block's schedule and split when inserting recovery after a training-day change. Resume the reserved planned block after recovery without changing its identity or authored phase order.
- Repeat custom cycles from their saved authored block specifications, preserving durations, weekly structure, lift-frequency targets and both independent adaptation choices. Inserted recovery is excluded from the repeated plan; completed cycles and workout history remain saved.
- Show the actual split, training-day count and explicit lift-practice targets in read-only block previews using shared layout tokens.
- Extend existing engine and real-app browser journeys through short-block advancement, schedule changes, stale saved-state repair, fatigue recovery/resumption, worker-driven custom repeat and reload. Retain Build 905 / Engine 0.65.9 and refresh offline release hashes.

## M297 — First-class Strength and integrated Profile progress

- Put Strength directly in the Progress tab bar, with Home and Profile opening that same destination. Keep setup and empty states accessible before the first workout, alongside the existing strength club and canonical score.
- Share Strength trend typography and layout between Progress and Profile. Restore separated chart headings, pattern labels and deltas; label the score-history chart for assistive technology.
- Integrate the existing level/XP projection into the top Profile card, remove the duplicate level metric and brand line, and keep bodyweight/age with identity. Use a balanced metric grid with optional explanations while retaining card preferences and PNG export content.
- Give Progress summary cards visible gaps and retain readable, horizontally accessible section tabs on narrow screens and enlarged text.
- Extend existing phone/tablet browser journeys for direct Strength navigation, chart layout, profile editing, summary spacing and integrated XP. Preserve training history, progression behavior and all prior cycle/cohesion fixes. Retain Build 905 / Engine 0.65.9 and refresh the content-addressed offline release.

## M296 — Stable opening set loads

- Realize historical recommendations using today's progression method. Ordinary double, linear, ladder, wave and e1RM methods use one working load; only the current dynamic method retains positional load targets. Explicitly held plan changes and percentage protocols retain their own owners.
- On resume, discard an untouched automatic live reduction or rep cap when its supporting completed observations are absent. Preserve genuine observed adjustments, recorded sets, manual entries and deliberately changed values.
- Extend existing core and phone-browser regressions for dynamic-to-straight transitions, opening second-set reductions, repeated reloads and protected user values. Retain Build 905 / Engine 0.65.9 and refresh the content-addressed offline release.

## M295 — Complete cohesion integration and enforce custom load increments

- Finish M294's compact, horizontally accessible exercise tags and top-aligned workout actions. Match numeric headings to the full-bleed row in both the phone flex layout and the tablet companion grid.
- Route custom-program advice through the same equipment boundary as generated programs. Held loads, per-set targets and cross-program/unit-converted references honor the exercise increment or actual rack; earned increases still reach the next selectable load. Logged history is unchanged.
- Recover remaining Cohesion 23/24 Home action ownership, compact preview/change-history spacing and complete Plan mode labels at enlarged text. Preserve the newer shared app bars, weekly-card hierarchy and sticky-header behavior.
- Extend the existing custom progression regression with 38.5 lb on 5 lb equipment, converted/reference history, executable set rows, earned increases and explicitly fractional stock. Retain Build 905 / Engine 0.65.9 and refresh release/offline hashes.

## M292 — Progress and History presentation ownership

- Move 225 fixed inline style objects from the four Progress panes into the maintained CSS family. Shared card, caption, field, list-row and history-action classes retain the current appearance; action/field owners also remove 32 repeated CSS declarations. Five chart objects keep only calculated widths/heights and data-derived ink inline.
- Let existing native selection/disabled state and explicit display-state attributes own paint. Publish the consumed warning/accent/border palette roles once, preserving the user's theme. Remove the masked group-header font declaration rather than adding a priority override.
- Fix the Load pane's full-height range bars overlapping their heading: the chart now fits both labels and bars while retaining its minimum plot height and existing values. Add a real geometry regression with populated six-week data.
- Retire 14 fully superseded embedded declarations, including ten priorities. Tighten the embedded priority ceiling to 1,229 and the inline ceiling to 2,800; external/repeated priority ceilings remain 2,360/15.
- Extend the existing real Progress browser journey across four widths, two palettes and normal/200% text: keyboard tabs, session notes/sets/search, lift/load/recovery/volume panes, bodyweight/measurement logging, same-day replacement and persistence without rewriting workout records.
- Same app/engine versions; refresh content-addressed offline cache and release hashes.

## M291 — Profile presentation ownership

- Move 83 fixed inline style objects from Profile, achievement details, Strength trends and shared sparklines into maintained CSS owners. Retain calculated progress, chart dimensions and data-derived ink inline; retire every Profile selector from the embedded stylesheet.
- Remove 59 CSS priority declarations: external 2,378 → 2,360; embedded 1,280 → 1,239. Tighten the inline ceiling from 3,108 to 3,025 and retain the repeated-priority ceiling of 15.
- Build the Profile section element map once per render, preserving saved order/visibility and canonical history/score calculations. Name achievement dialogs through the existing modal owner and expose section headings and progress semantics without changing their presentation.
- Extend the existing Profile browser journey with earned/locked badges, keyboard activation, named modal focus containment, dismissal, focus restoration and progress agreement. Same app/engine versions; refresh content-addressed offline cache and release hashes.

## M290 — Profile level-card ownership

- Move eight fixed level-card JSX styles and the embedded fallback presentation into the existing Profile stylesheet family; leave only the calculated XP fill width inline.
- Retire 21 priority declarations across the external/embedded stylesheets while preserving appearance, live palette, canonical level/XP calculations and progress semantics.
- Extend the existing Profile browser gate with level-copy containment and XP percentage/fill agreement. Same app/engine versions; content-addressed offline cache and release hashes refreshed.

## M289 — Profile Strength style ownership

- Consolidate Strength’s effective comparison-row, score typography and single-pattern styles; retire 43 CSS priority declarations and preserve the current appearance.
- Extend the existing Profile browser journey to check row alignment, complete labels and share targets at normal/enlarged text, alongside customization/export and persisted-history coverage.
- Same app/engine versions; refreshed content-addressed offline cache and release hashes. Physical Android certification remains open.

## M288 — consolidate Profile lifetime-summary ownership (October 22, 2026)

- Retired the superseded R17/R39/R40 and duplicate R77 summary declarations. The maintained Profile owner now contains the effective grouped surface, number wrapping, phone padding and large-text reflow. External CSS priority declarations decrease from 2,462 to 2,432.
- Corrected the ownership scanner to strip comments before identifying media/container contexts. The same baseline has 15 actual scoped repeats rather than the old scanner's 21; trimmed the exception list to the 14 existing keys and added adversarial scope checks. This accounting correction does not represent six additional production deletions.
- Expanded the existing populated Profile browser fixture to eight windows in both palette families, checking complete lifetime values/labels at normal and 200% text along with existing customization, export, keyboard and history-preservation behavior.
- Same-build offline cache and UI hashes finalized; exact-head CI, screenshot review and post-merge deployment verification remain required.

## M287 — retire unused design-token aliases (October 22, 2026)

- Removed 17 unused R41/R51/R57 transitional CSS declarations at their original owners, including the extra narrow-screen R57 inset. The live semantic colors, spacing tokens, control dimensions, components and actual UI rules were not replaced.
- These aliases had no reads in app.css. Added a guard against redeclaring them or using them from App.js; the existing --pi-page-inset / --pi-space-* owner remains authoritative.
- Removed redundant historical token definitions rather than adding more CSS or increasing the existing 2,462 !important ceiling. Training engine, Workout/Focus logic, navigation state and user data untouched.
- Finalize the same-build offline cache/UI integrity and verify phone/tablet browser, release and accessibility gates before merging.

## M286 — full-width reading frame on short wide landscape (October 22, 2026)

- The 900×480 adaptive-shell browser screenshot was still letterboxed to roughly 460px despite a wide viewport. Extend the existing R88 medium-width primary shell breakpoint to short windows at or above 840px wide and below 560px tall, resulting in a bounded 760px workspace with bottom navigation.
- Continue using the same five root tabs and shared root header; taller wide windows retain their vertical navigation rail. Do not stretch Workout/Focus, change adaptive engine state, move buttons, or introduce per-route width hacks.
- Update the R65 real-browser geometry contract for short landscape and its actual 900×480 screenshots; add a static single-owner guard. No new high-priority style declarations.
- Finalize same-build offline cache/manifest and require full exact-head CI plus screenshot inspection before merging.

## M285 — mixed-program and short-month history spacing (October 22, 2026)

- Extended the existing populated Progress test to 16 saved sessions across two programs and several months, checking both By program and By month grouping at 900px and 1280px in light/dark themes.
- A real wide-screen screenshot showed a large blank gap after a short October group before September began. The authoritative wide calendar sidebar now spans the grouping plus two history tracks, not merely the first history track, so shorter month groups can flow without calendar-induced empty space.
- Added per-group geometry assertions, exported grouped screenshots, and kept recorded sets and all filter behavior unchanged. Retained the existing 900px single-column view and the Workout/Focus and engine.
- No new CSS priority declarations. Same-build offline cache and hashes must be finalized and exact-head CI plus screenshots must pass before merge.

## M284 — populated Progress history on tablet and desktop (October 22, 2026)

- Added actual 16-session saved-history browser scenarios at 900px and 1280px, in light and dark palettes, with top and scrolled screenshots. Existing desktop Progress screenshots exercised only empty history and missed a reading-order defect.
- Corrected the wide Sessions grid: the Training Calendar spans the grouping and first-session row tracks, so the first logged workout starts alongside the calendar rather than being pushed below its full height and leaving a large blank right column.
- Added assertions for visible first-session position, bounded cards, opaque/stationary Progress header during scroll, and unchanged logged sets. The fixture allows the canonical save migration to populate updatedAt without mistaking it for a user edit.
- Retains the existing 900px single-column layout, all history filters/sections, independent scroll positions, Workout/Focus, and engine. No new style priorities. Same-build cache/hash finalization and all exact-head CI required.

## M283 — cross-tab phone scroll/header edge alignment (October 22, 2026)

- Eliminated the old 14px mobile scroll pane inset that conflicted with the shared 12px (320px) / 16px (393px) page inset on Settings and Progress. Home already used the canonical inset and is left owned by its existing rule.
- The responsive selector now uses the same `--pi-page-inset` token as the common root header, with one padding declaration rather than two hardcoded priority declarations. All saved data, navigation and Workout/Focus are untouched.
- Added real browser geometry checks at 320px and 393px across themes for matching left/right root header and Settings/Progress content insets, opaque headers and contiguous Settings category chrome. Existing Plan sticky week-picker checks remain in the R62 suite.
- CSS priority ceiling decreases 2,463 to 2,462. Same-build offline cache and UI hash finalization, full exact-head CI and device screenshots remain required before merging.

## M282 — Settings large-text row reading order (October 22, 2026)

- At narrow inline-size containers with enlarged system text, existing Settings rows now place preference explanations above their controls instead of splitting words into isolated letters to fit beside kg/lb selections and switches.
- Extends the already-maintained 16em Settings container query without adding new `!important`, duplication, or per-option styling. Normal-sized phone layouts keep the original compact rows; the saved settings and user interactions are unchanged.
- Added full-width copy and control-below assertions to the real-browser 320px/393px, 200%-text test, plus a stylesheet-owner regression. No new engine or Workout/Focus changes.
- Must pass the exact-head regression suite, cache revision check and visual screenshot verification before merge.

## M281 — selected bottom-navigation indicator without gray outline (October 22, 2026)

- Removed the permanent R35 2px gray-ish outline around the animated active bottom-nav pill from its original CSS owner. The selected state continues using saved-theme tonal fill, selected-icon paint and the colored destination label.
- Preserved keyboard-only focus rings and system forced-colors outlines; all five navigation routes, touch targets, pointer feedback, animation, and accessible labels remain unchanged.
- Added real-browser computed-style checks across root routes, themes and phone/tablet sizes, plus a no-regression stylesheet ownership check. No new `!important` declarations or per-screen overrides; Workout/Focus and engine untouched.
- Requires same-build offline cache/manifest rehash and exact-head release, motion, accessibility and browser CI before merge.

## M280 — shared root title word integrity (October 22, 2026)

- In the existing `UiRootHeader` title owner, use min-content intrinsic sizing and normal word breaking so fixed destination names stay intact at 320px and 200% text. If a root action does not fit beside a word, the existing flex-wrap puts that action on the next row instead of splitting “Profile.”
- Added an actual Profile browser assertion for 320px / 200% text that checks a one-line title and no title/Edit overlap. Retained all five root titles, toolbar callbacks, navigation states, theme and reduced-motion behavior; Workout/Focus and the engine are unchanged.
- Stylesheet priority ceiling remains 2,463. Same-build release cache and UI hashes require finalization and exact-head CI before merge.

## M279 — Settings category rail and control ownership (October 22, 2026)

- Removed 11 superseded Settings-specific CSS priority declarations: old category bar padding, duplicate narrow-screen insets, raised-input painting and an obsolete 44px select rule. The surviving top rail uses the shared page inset, canvas surface and 48px input target.
- Settings categories and preference descriptions now share the readable support typography token; the category choice uses the shared action corner shape. No settings values, data transfer, theme settings, switches or navigation callbacks were changed.
- At phone widths up to 420px, hide the redundant visible “Jump to” phrase so the existing labeled native section selector has more space instead of truncating its prompt; the screen-reader label stays on the select.
- Added exact-head ownership and live phone/tablet typography/layout assertions, including 320px geometry and 200% text reflow. CSS priority budget decreases from 2,474 to 2,463.
- Same-build cache/hash finalization, full CI and Android screenshot review are required before merging or declaring launch certification.

## M278 — Profile share-card spatial cohesion (October 22, 2026)

- Rebased the Profile share-card heading, label, metric and favorites spacing on the shared 4/8/12/16px scale, replacing disconnected 5/13/18px margins while preserving the metric data and share-card customization.
- Retired six obsolete priority declarations on Profile strength comparison cells, its first/last row shapes, and the narrow-profile statistics. The actual vertical strength comparison and its themed highlighted first row remain owned by the existing later responsive rules.
- Preserved the customizable top card, metric ordering, 48px actions, scores, user preferences, accessibility reflow and Workout/Focus composition. Lowered the stylesheet priority ceiling to 2,474 and added regression checks.
- Same-build release integrity, exact-head browser CI and real-device screenshot review remain required before calling the pass complete.

## M277 — Progress/History spacing ownership (October 22, 2026)

- Unified the history section/group spacing with the shared 4/8/12/16px token scale; the cycle grouping control and section headings now align to the same system.
- Removed superseded 7px session margins and duplicate header padding priority declarations, and let expanded session content inherit its card's outer corner clipping instead of drawing a nested corner.
- Preserved saved history, date grouping, filtering, chart series, keyboard focus and 48px action targets. Lowered the stylesheet priority-declaration budget from 2,483 to 2,480 and added ownership regression checks.
- Same-build service worker and release hashes are content-addressed; exact-head browser CI and device screenshots remain required for release confidence.

## M276 — Program/Cycles reading order and component density (October 22, 2026)

- Moved the Program's “Where do my edits apply?” explanation from the first scroll row into the existing Program Details sheet, maintaining the full saved-plan, future-session and cycle scope wording, and making its disclosure a 48px accessible target.
- The existing cycle link in Program remains at the top and continues using the same navigation callback and scroll-collapse ref. Its static colors, spacing, label hierarchy and icon alignment now have shared semantic CSS owners.
- Consolidated the Cycle list/detail spacing to the shared scale, reduced nonessential padding around overview, block rows and supporting information, and replaced the redundant expanded block-length summary with a concise “Training blocks” count.
- Kept native cycle expansion, canonical progress/volume, preview/callback behavior, readable 64px+ block rows, theme palette and reduced-motion treatment unchanged. Added regression checks for 320px layouts, Program Details help and responsive Cycle density. Production inline presentation decreases from 3,127 to 3,116 instances without adding CSS priority rules.

## M275 — flush sticky bars, Plan program action, active Cycle disclosure (October 22, 2026)

- Plan's one existing Full program action now sits alongside the selected program name, above the week selector and work schedule; preview blocks retain their existing route and selected week.
- Removed the transparent margin under the sticky Plan week rail and replaced translucent deep-screen header paint with opaque semantic canvas. Progress history's sticky group headings no longer leave external margins as scroll gaps.
- In Cycles, the active unfinished cycle reveals its actual block list by default; native details remain collapsible. Reuses canonical cycle progress and block navigation, no duplicate data projection.
- Updated R53 browser and ownership guards. No new CSS priorities, no changes to Workout/Focus logging or engine policy.

## M274 — Program header presentation ownership (October 22, 2026)

- Consolidated the Program detail title/rename row, animated metadata wrapper and saved status into one token-owned presentation family in app.css, preserving the exact existing handlers and scroll-collapse refs.
- Removed eight legacy inline presentation objects and lowered the permanent CI debt cap from 3,135 to 3,127.
- Added source assertions to protect these class owners against future inline regressions; preserved all Program, Cycles and Workout controls.

## M273 — Plan schedule hierarchy and shared glance styles (October 22, 2026)

- The selected week now owns the first schedule card immediately beneath the week picker and its focus context. The phase timeline contains only the other weeks, so each planned week is rendered once with the same session-row renderer, target and navigation owner.
- Preserved the single Up Next action, complete phase schedule, previewed-week memory, existing four Plan panes and program editing routes.
- Moved Plan overview labels and values into semantic shared token CSS, removing three static inline presentation objects with no new important rules.
- Extended the existing R53 real-browser gate for selected-week placement, one-per-week ownership and changing the selected week.

## Shared tab hierarchy foundations — October 22, 2026

## M271 — unified root app bars

- Put all five destination titles in one shared header/action row; remove Plan's root Back button and keep the program name as subordinate context.
- Keep Profile Edit above its scrolling share-card content, inset Progress tabs consistently, and preserve Home's smooth collapse with a stationary destination title.
- Retire 239 priority declarations and 28 inline style objects across the old independent header owners. Use immediate reduced-motion transitions to avoid stale text/layout frames.
- Extend existing browser contracts to compare title coordinates across routes, themes, window sizes and enlarged text, retaining deep-screen Back, program/cycle state and protected logging behavior.

- Home, Plan, Progress, Profile and Settings share one scalable title role; empty Plan keeps its title and the shared creation action.
- Navigation keeps full destination names. At enlarged text sizes, the bottom layout reflows and the tablet rail grows with its labels; the live dock uses the actual rail inset.
- Progress summaries preserve readable words as text grows. App/workout notices share one component and measure the real navigation, live dock and workout/rest footer before positioning.
- Retired 86 external and 12 embedded priority declarations, plus 21 inline style objects. Workout/Focus logging geometry and training calculations are preserved.
- Browser gates check full visible destination names, shared title roles and notice clearance across normal/Focus rest states. The rest of the tab placement audit remains on the roadmap.

## Focus touch selection stability — October 21, 2026

- After a touch-click, the native browser caret can move after the first paint. Recheck select-all on the second animation frame for Focus weight and reps inputs.
- Keep the immediate select-on-focus behavior and value guard: never replace user typing, readonly/composing values, progress, persisted state, timers or workout geometry.
- The existing B825 real-touch logging test remains strict (1.5s selection deadline); the candidate must pass all ten CI jobs before merge.

## Adaptive tab labels and narrow-phone Home title — October 21, 2026

- Keep complete tab names for accessible navigation, switching to short visual labels only when the tab is narrow relative to the actual font size.
- The current tab and every button retain the same route, focus, semantics and touch target; avoid breaking a single word across multiple nav lines.
- At 320px, remove the ornamental day orbit so the true workout name has the whole hero width. The actual day and week stay in Up Next.
- R74 real-browser checks now require single-line visible navigation labels under 200% text in all five tabs; engine, saved plans and Workout/Focus logging remain unchanged.

## Switch contrast and compact Home preview — October 21, 2026

- Reconciled the approved switch and Home fixes with the newer shared Settings/Profile cards.
- Restored contrasting switch tracks, contained thumbs and 48px touch targets; removed inherited legacy toggle sizing.
- Home session outline now has one spacing owner and no redundant divider or top inset.

## Consistent Settings groups and strength-club controls — October 21, 2026

- Settings preference, workout, timer, reminder, profile and data rows now share explicit themed cards and one wrapping row layout. Native inputs, switches and existing settings behavior are preserved.
- The Profile strength-club card uses shared 48px actions and responsive text/value layouts. Lifted totals, estimated totals, earned/projected status and sharing retain their existing data owners.
- Removed 38 inline style objects and 97 CSS priority declarations. Added browser checks for enlarged Settings rows, keyboard club-measure reversal, complete labels and unchanged history. Workout/Focus and training engine behavior are preserved.

## Reliable shared data controls — October 21, 2026

- Settings now delegates backup, restore preview and Undo to one stable component. All twelve backup actions reuse UiAction and existing semantic theme roles; wrapping rows preserve labels at narrow widths and 200% text without adding CSS priority overrides.
- Failed clipboard/download actions and cancelled sharing have visible outcomes. Concurrent transfer attempts are blocked. CSV copy no longer marks a full backup as saved; download messaging asks users to verify the actual file.
- Selecting an oversized or unreadable restore file clears any prior executable preview. Earlier file reads cannot replace newer selections or typed text, or reappear after closing the panel. Undo feedback stays available until explicitly closed.
- Storage protection reports denial/error, reads usage and persistence independently and no longer promises installation guarantees protection. Privacy/deletion copy describes browser storage and exported-file retention accurately.
- Updated the single launch plan and engine baseline documentation to build 905 / engine 0.65.9, plus the Play kit's current API 36 requirement, signing/domain association and final-package/device checks. No training-policy or Workout/Focus changes.

## Shared editor controls and cascade cleanup — October 20, 2026

- Profile layout/card editors and favorite-chart editing now reuse the existing UiAction component with one tonal button style, a shared 48px touch minimum, icon sizing and selected states. Screen-specific rules retain layout only; native labels, disabled states, callbacks and saved preferences are preserved.
- Removed 67 superseded declarations in Profile, Progress and Settings, including 63 priority overrides, plus 23 priority overrides replaced by the shared controls: 86 fewer CSS `!important` declarations (3,103 → 3,017). Accessibility, forced-colors and motion safeguards remain.
- Share-card choices reflow to one column when enlarged text needs more room, preserving whole labels at 200% root text size.
- The redundant-rule removal produced zero computed-style changes in a same-page comparison of 5,718 elements across 24 populated phone/tablet light/dark scenarios. Actual editor interaction, record preservation, contrast and compact workout regressions remain release gates.

## Profile share card and cohesive styles — October 20, 2026

- Profile opens with an editable share card: a name, chosen strength/streak/level/club/record/achievement metrics and up to three favorite lifts. The live preview and PNG export share one content model; records retain their estimated-1RM labels.
- Edit card and Arrange Profile are separate. Saved section order/visibility and existing metric selections survive, and resetting section layout preserves the card.
- Push and the other Strength Score rows have identical label/value insets. Profile controls use the shared themed surfaces, borders and shapes. Progress/History layout ownership was consolidated in the preceding pass.
- Workout layout, interaction behavior, engine and stored training data are preserved.

## Build 905 — Remaining prototype features

- Stage actual generated rebuilds and canonical volume repairs for review before committing. Show changed settings, exercise additions/removals/order, all weeks of set/rep/RIR/rest/technique/progression targets, and first-week duration estimates. Apply the reviewed artifact without generating again.
- Preserve exact prior layouts, including edited prescriptions, schedule, notes and settings. Restore through the same preview; retain up to three nonrecursive snapshots within a size budget. Reject stale previews and program changes during an active workout.
- Extend the existing live session lineup to portrait tablets from 800px wide and 720px tall. Preserve the original logger, Focus, active exercise, rest state and saved workout across resizing.
- Add explicit plateau choices grounded in the existing program/day-scoped detector. Reuse progression and swap controls; optionally lower only automatic remaining targets for this session, including pending warmups, rounding to available loads. Keep completed/manual work and injury/percentage prescriptions protected, remove pending technique extensions on the lighter day, and offer Undo.
- Retain existing plan-change undo, game plan, equipment inventory, quick routines, history correction, shorter-session engine and superset suggestions.

## Build 904 — Plan changes and today's game plan

- Review changed load/rep recommendations from Home and the session preview, including their engine reasons, previous completed sets and proposed targets. Group identical sets and retain individual dynamic-double differences.
- Undo or restore each exercise's upcoming recommendation. Persist the choice across reload and apply it through the same prescription boundary used by Workout, including warm-ups and technique extensions. Preserve history, progression methods, other exercises, and current set/effort/technique targets.
- Scope undo to the exact exercise, day, week, unit/loading context and recommendation. New performance or changed prescriptions expire stale choices; sync revision timestamps alone do not. Keep authored percentage and deload protocols intact.
- Turn Home's existing session outline into today's game plan, with actual suggested loads/reps, target effort, progression reasons and last-set technique instructions. Show the same game plan in Preview, including shortened-session prescriptions. Share amplifier rest timing with the logger and honor the rest setting.
- Show conditional rep-record and estimated-1RM opportunities using completed working sets and the same record calculation as Progress. Target RIR never becomes extra completed reps, and missing history, assisted lifts and high-rep estimates do not create false e1RM claims.
- Verify custom/generated prescription parity, positional undo, reload/resume recovery, source and unit isolation, record semantics, and responsive dark/light layouts.

## Build 903 — Editable set counts and complete cycle overview

- Keep three working sets as the default for an exercise added during a workout. Add an explicit set-count control in standard and Focus logging; reductions preserve completed work.
- Review added exercises and changed set counts after saving a workout. Edit the future set count, apply it to the workout day, or keep changes session-only. Pending reviews survive reload; completed workout records stay unchanged.
- Use stable exercise IDs and explicit prescription ownership for custom and generated programs. Skip injury and session-length reductions when proposing permanent program edits.
- Restyle cycle progress, blocks, schedule and next-block changes as themed cards. Show useful overview details directly and expand the cycle list's blocks by default.
- Verify custom/generated set persistence, protected logged sets, review recovery, apply/skip behavior, and phone/wide cycle layouts.

## Build 902 — Reverse EZ-Bar Curl

- Clearly name the existing EZ-bar reverse curl in both the app and canonical catalog. Keep its stable exercise ID, saved programs, history, brachialis emphasis, EZ-bar loading and 10–15 rep defaults.
- Support searches for reverse EZ bar / EZBar curl and preserve old/new CSV import names. Keep the straight-bar reverse curl separate.
- Correct the app's movement tag to elbow flexion and give the lift overhand-grip curl cues instead of generic reverse-fly instructions.

## Build 901 — Suggestions respect logged reps and effort

- Keep the programmed effort visible in TARGET after completing and rating a set; use muted text for every non-current row. Focus shows the owed set's target and reads target RIR from its prescription.
- Engine 0.65.9 caps the next untouched same-weight rep prefill from explicitly logged reps and RIR. A history-derived 11-rep target no longer survives logging 8 at 2 RIR when the next target is 1–2 RIR. This session-only guard never raises reps/load, extrapolates to another weight, or overwrites manual/completed work or authored protocols.
- Preserve two-observed-miss accessory load reductions, source-aware undo, rerating, and reload recovery. Keep weekly progression independent and retain the current plus four preceding engine versions.
- Replace “tunes the next set” with factual feedback: reps adjusted, load eased, target unchanged, or saved for future workouts.
- Audit custom/generated suggestion ownership, historical positional targets, RIR provenance, load rounding, live adjustment and recovery. Extend the existing engine and real-browser regressions with the reported sequence.

## Build 900 — Compact progression and clearer exercise controls

- Make the progression method a small themed text control beside set/muscle details, with a full tap target. Remove the duplicate full-view progression action.
- Keep exercise info, history and skip beside the name so tags, notes and progression can use the full content width.
- Wrap workout actions into visible rows with space above the target/set area. Keep all actions and their existing behavior, including Focus and percentage-program progression access.

## Build 899 — Steady screens and program creation

- Keep route canvases fully opaque and stationary. Remove stacked page/card scale and luminance pulses while preserving directional content motion, interactive gestures, controls and sheets.
- Creation steps stay opaque from their first paint; selecting, typing and asynchronous feasibility updates do not replay a page fade.
- Program/cycle builds show progress and Cancel in the existing creation footer. Other generation operations retain immediate busy/cancel behavior without dimming the entire app. Cancelling preserves setup answers and is not reported as a capacity failure.
- Preserve existing muscle-focus controls and all training prescriptions.

## Build 898 — Engine-guided shorter sessions

- Session preview offers Full, 45, 30 and 20 minutes for today only, with a review of cuts.
- Preserve key/complementary lifts, warmups, rest, rep/RIR targets and complete strength/percentage protocols. Cut optional amplifiers, extra sets, then optional movements.
- Block a shorter start when protected work exceeds the time limit. Include setup, warmups, rest, technique extensions and slower personal pace in the estimate.
- Carry the session-only plan through minimize/reload and save the actual reduced prescription. Time-limited work is neutral progression evidence; omitted work is not a failed set. Saved programs remain unchanged.

## Build 897 — Reliable update restart

- Resolve the current service-worker registration when Restart is tapped. If another window already activated the update, reload its installed version instead of sending a message to a stale worker and waiting forever.
- Show Restarting while the request is in progress, ignore repeated taps, and restore a retry with feedback if activation stalls. Preserve the save-before-restart gate and hide the update action during active or minimized workouts.
- Keep the service-worker message alive until its activation request resolves.
- Extend real-browser coverage to actual Restart taps, activation by another window, stale-worker replacement, failed saves, stalled activation and retry. Preserve saved training and workout recovery.

## Build 896 — Selected Expressive workout refinements

- Workout confirmations let taps reach the set fields beneath them; Undo remains interactive.

- Give exercise navigation named chips with live completed/total working-set counts and distinct current/completed shapes. Reveal the selected chip when moving between lifts without scrolling the workout or changing set values.
- Put the existing progression method in a small themed badge and collapse suggestion details behind a native Target disclosure. Keep the existing engine-owned Why sheet and all prescription evidence.
- Add a remaining-time ring and pause icon to the existing rest timer. Keep all five rest controls and exercise navigation; stack timer controls inside narrow cards so the countdown stays readable.
- Group the existing optional effort choices into a tonal strip with a distinct selected shape. Preserve set steppers, Target/Last, manual-entry ownership, set-number actions, plate calculator, techniques, injury flow, session notes and Focus mode.
- Add phone/tablet browser coverage for chip progress, navigation, manual entries, rest values/controls and timer overlap in light and dark themes. No engine, storage schema or program changes.

## Build 895 — Session notes placement

- Move Session notes from above the exercise name to Workout options in both full and Focus modes.
- Remove the empty context row when no injury or Focus action is present. Keep the existing session/exercise note editors, repeat-once behavior and saved-workout recovery.
- Keep Workout options scrollable on shorter screens, including landscape layouts.

## Build 894 — Custom cycle conversion and setup recovery

- Turn an existing generated or manually built program into a custom cycle. Keep the current program as Block 1 and choose 1–7 future blocks with any supported phase order, repeats and individual 1–24 week lengths.
- Retain the existing block's exercises, authored edits, identity and workout history. Validate the 104-week limit including its current duration; preserve the existing automatic-effort policy and future-block ownership.
- Recover unfinished cycle creation and conversion setups after closing or reloading, including incomplete inputs. Setup drafts stay on this device, separate from training records, and clear after successful creation or deliberate discard; failed/cancelled generation retains the draft.
- Keep creation-sheet actions visible, prevent edits while generating, and retain 44px block controls on narrow phones.
- Keep the workout exercise-info sheet anchored to the screen while its scrolled opener animates; preserve Close, dismissal motion and entered set values.
- Add actual generated/authored conversion, draft-isolation and full-app browser coverage at 320/390/900px, plus wizard reload/failure/retry/discard checks.

## Build 893 — Custom training cycle builder

- Design training cycles with any order of supported phases, including repeated phases and individual 1–24 week block lengths.
- Move, add or remove blocks in the accessible wizard. Validate 2–8 blocks and an overall 104-week limit before generation.
- Pass the exact phase and duration sequence into the canonical engine, retaining per-block progression, recovery, preview, history and ownership safeguards.
- Include B893 generation and UI wiring regression checks; retain all Build 892 Profile and share-card improvements.

## Build 892 — Customizable Profile and share card

- Reorder or hide Profile sections without changing workout history, training data or scores.
- Customize which achievements, level, strength score, Thousand Pound Club and personal record statistics appear on a shareable image.
- Persist the layout and share-card selections across launches; include accessible controls and narrow-phone layout.
- Add B892 preference normalization and share-layout regression checks.

## Build 891 — Favorite lift chart order and history spacing

- Pin favorite logged exercises to the top of the lift charts and arrange their display order with accessible move-up and move-down controls. Search for additional lifts in the organizer.
- Persist chart favorites across app restarts without changing exercise prescriptions, performance history or PR calculations; missing exercise IDs are ignored until those lifts appear in the log again.
- Improve dense cycle-history rows, narrow-screen week/session labels and expanded workout-history set/action spacing.
- Register B891 release coverage for favorites, ordering, storage and responsive layout.

## Build 890 — Cycle effort and block browsing

- Converted custom Auto programs use the cycle's weekly effort policy instead of neutral 2-RIR defaults. Preserve explicit RIR targets, Manual progression, exercise selection, sets, reps, rest and history; retain the novice compound effort floor.
- Browse every cycle block from Plan, with each block's weeks, volume and progression. Planned and completed blocks open read-only exercise previews without changing the active training block.
- Simplify cycle details to a compact summary, one readable block list and one workout action. Collapse extra schedule information and move cycle management into Options.
- Make the cycle list compact, with expandable block rows and usable previews at phone and desktop widths.

## Build 889 — Injury journal and workout review

- Choose a joint/body area or specific muscle, side, onset date and training preference. Preview related lifts before saving.
- Edit existing injuries, mark them resolved or active again, and keep resolved records separate from active restrictions.
- Protect unsaved edits and duplicate saves; preserve earlier injuries and backup imports.
- Review any active injury during a workout. Adjustments retain completed/manual set values and link back to the original injury instead of creating duplicates.
- Improve the Home injury card, form hierarchy, themed surfaces and phone/tablet layouts.

## Build 888 — Session choices and notes

- Change an exercise's progression during a workout, then choose whether to update the program after saving. Completed and manually edited sets are preserved.
- Log and resolve injuries from Home. Active injuries link to affected-lift review in workouts; workout injury reports join the journal.
- Key, supporting, accessory and added lifts have clear role tags in both workout modes.
- Session and exercise notes have this-session, repeat-once and keep-repeating options. Notes recover with an interrupted workout and remain in history.

## Build 887 — Smooth Home header and consistent app identity

- Keep **Pursuit Iron** visible in the Home masthead while scrolling; remove the unrelated "Iron"-only abbreviation.
- Eliminate the sudden 95%-scroll fixed-height swap, padding/color change, action shift and heading font-size change. Only the greeting/title block now contracts proportionally with the real Home scroller.
- Preserve the compact 56px action row, 48px touch targets, full scroll-back expansion, reduced-motion preferences and small-screen behavior.
- Add focused source and real-browser geometry regression checks covering 320/393/900px and both light and dark color themes.

## Build 886 — Set-number actions and clear workout hierarchy

- Tap any set number to open a Material-style bottom sheet of safe, session-local set options. Working, warm-up, and to-failure classifications are selectable for unfinished normal sets without changing the entered weight/reps or saved program prescription. Previous targets are retained for switching back. Logged and specialized drop/myo rows are protected against reclassification.
- Remove the dangerous inline X on user-added and drop/myo-extension rows. Removing an unfinished added row now takes two explicit actions inside its set-number sheet. Prescribed or logged sets cannot be removed through the sheet.
- Remove the entire "Complete remaining sets" control, preventing accidental bulk logging while keeping each set's individual checkmark and the separate normal finish-workout action.
- Enlarge and rebalance the exercise name as the main workout heading. Update narrow-phone set grid selectors to keep the numbered options button in its own accessible touch area, without disturbing the symmetric weight/reps steppers and single TARGET/LAST reference.
- Add B886 source and real-browser regression gates for type changes, logged-set protection, two-step removal, readable heading and continued row integrity.

## Build 885 — Consistent set rows and one reference action

- Remove the duplicate active-set “Use last”/suggested chip row. TARGET/LAST is now the sole per-set application surface: switch the header to compare, then tap the matching reference directly. No shifting row heights when one lift has history and another does not.
- Tap LAST to reuse the matching previous **load only**, leaving today's reps and effort untouched. TARGET applies the prescribed load; a precise set-specific progression suggestion can also provide its suggested reps. Neither reference can mutate an already-completed set.
- Give weight and reps equal space, separate the four +/- touch tracks and numeric inputs, and use a two-tier row on narrow phones so values are not reduced to unusable text slots. No sideways overflow or overlapping stepper hit areas.
- Preserve long-press steppers, direct keyboard entry, timers, plate breakdown, added sets, Focus mode, tablet dock and previous-value comparison.

## Build 884 — Workout first, chrome second (R93)

- Bring the active exercise and working-set rows to the top of the normal workout screen. Keep Coach guidance fully functional, but move it after the set-entry and set-management actions instead of before the lift title.
- Rebalance phone hierarchy using the existing theme: tighter progress/navigation, larger exercise identity, coherent setup/action affordances, quieter work-set utilities, an actual upcoming exercise name in Next, and stronger active-row and numeric-entry contrast.
- Correct misleading first-time coaching when historical sets are present but a progression-owned suggestion is absent. For a genuinely new lift, explain that the lifter must choose the starting load.
- In the TARGET column, say “Set load” when the engine has not prescribed a weight instead of displaying a cryptic dash. Make the existing tap-to-reuse previous set button explicitly say “Use last.” Do not auto-overwrite typed values or invent loads.
- Keep injury actions, warm-up, superset, swap, plates, dynamic progression, add/complete remaining sets, inline notes, RIR logging, timers, Focus and tablet dock intact. Keep Focus padding and stepper geometry isolated from normal-mode layout changes, with reduced-motion and forced-color fallbacks.

## Build 883 — Native load steps after unit switching

- Repair the equipment-inventory boundary responsible for suggestions such as 159.5 lb after a 155 lb machine set. Factory 2.5 kg pin steps become the native 5 lb defaults when the athlete trains in pounds, rather than a converted 5.51 lb phantom machine increment.
- Repair legacy profiles already saved with a converted factory inventory *at read time*, without requiring users to switch units again or rewriting workout history.
- Detect unchanged factory stock separately per equipment family. Truly customized increments, explicit fractional increments, dumbbell selections, per-lift loading overrides and saved workout observations still retain their intended values.
- Add an engine regression reproducing four dynamic-double sets: 155×12 / 155×12 / 155×8 / 155×8 progresses to 160×8 / 160×8 / 155×9 / 155×9 when the equipment has 5 lb notches.
- Retain Build 882's progression rationale, Build 881's CSV import, Build 880's Play foundations and Build 879's novice programming.

## Build 882 — One honest reason beside the target

- For Pursuit Next programs the method line now names the style that actually sets the loads: the week cell's style, with a lifter's explicit choice already folded in by `getNextShellCell`. It previously ran v661's fatigue/plateau overrides through `resolveStyle`. The load path (`sessionSuggestion` → `nextWorkoutSuggestionForShell`, `prescribeSets`) never applies those overrides, so a just-trained lift read "e1RM autoregulation · temporary — take a little weight off" over dynamic double progression at last session's load, with the reference card naming the other method. Custom programs still resolve through `resolveStyle`, their real load owner.
- Coach's muscle-recovery note no longer says "Today's target is already reduced". Nothing lowers a generated target for recovery alone; only the session check-in does. `readinessBand` has no runtime caller.
- The explanation sheet existed but nothing opened it (`setWhy` was only ever called with `null`). A **Why** button beside the target now opens it.
- New browser gate `b882-workout-reason-browser-test` logs a real session through the UI, repeats it immediately, and checks that the method line, reference card, Coach and explanation sheet agree. It fails on the pre-882 tree with "e1RM autoregulation · temporary"; restoring only the old Coach copy fails check 2.

## Build 881 — Import history from Strong and Hevy

- New `training-domain/history-import.js` converts a Strong (comma or semicolon, with or without a unit column) or Hevy CSV into a backup-shaped store. It then takes the existing restore path: `mergeStores`, the add/update/remove preview and Undo. There is no second history writer.
- Ids are derived from the source rows, so re-importing is a no-op and a later correction survives a re-import. Warm-ups are left out, as in logged workouts. Drop sets become extensions, failure sets are 0 RIR, RPE becomes RIR, and absent effort stays unreported. Loads are converted to the app unit; when a Strong file has no unit, the preview asks.
- 92 of 104 common Strong/Hevy names map to the catalog; the rest are cardio, Olympic lifts and bodyweight drills. A lift the catalog lacks becomes a custom exercise when its muscle can be inferred from the name; otherwise it is skipped and named in the preview.
- The CSV preview lists only Workouts and Exercises, because the restore path's gym normalisation could otherwise report gym "updates" for an import that adds workouts.
- Gates: `b881-history-import-test` (7) and `b881-history-import-browser-test` (real panel at 360/1280 px: pick, review, choose unit, import, Undo).

## Build 880 — Play Store and accounts foundation

- `privacy.html`, linked from Settings → About. Every factual claim is checked against the code: no cross-origin fetch, no telemetry or backend SDK, the real contact address and reset control.
- `play/`: Digital Asset Links template and release notes. Asset links must live in the `extracteddeath.github.io` user-site repo, because a project site cannot serve the domain-root `/.well-known/` path. The notes also cover the Play account-deletion requirement for when accounts ship.
- `syncStoreOnce(local, provider)` in `records.js`: the single merge-then-publish loop a future Firebase provider plugs into. It retries on a revision conflict and skips a no-op write. A reference in-memory provider states the contract.
- Keep screen on switch, a per-device preference outside the synced store. It gates the existing `holdWorkoutScreenAwake` owner rather than adding a second wake lock.
- Gates: `b880-play-foundation-test` (8; the control fails on the pre-880 tree and a no-retry mutant fails check 2) and `b880-wake-lock-browser-test` (5).

## Build 879 — Novice effort and full-body frequency (engine 0.65.8)

Two corrections from the experience-tier research review. Intermediate and advanced programs are byte-identical to Build 878 across 810 generated configurations.

- **Novice multi-joint lifts stay at 2–3 RIR in every working week (3–4 on deload).** Previously every experience level received the same effort ramp, taking novice compounds to 1–2 RIR from week 3 (1,300 of 3,756 novice compound cells). Isolation work keeps its normal target, so occasional 0-RIR calibration on stable single-joint lifts remains. The floor is applied by the executable week projection — the single source for week cells, the deload week, volume repair and the simulator — and by manual-plan seeding.
- **Novices on a 2- or 3-day full-body split train quads and hamstrings directly on at least two days a week.** Previously a novice 3-day full body trained quads once a week in 12 of 27 configurations — fewer exposures than intermediates received. Topology plans the exposures, realization gives each missing exposure a real 2-set block on a day that does not already train the muscle, and the full-body structure check refuses any later repair that would drop it. A strength-goal week keeps one deadlift exposure for the hinge: the anchor already meets the hamstring dose, so no separate hamstring work is allocated.
- No new refusals and no session over its time limit across 810 novice configurations; four previously refused 20-minute dumbbell/home full-body requests now generate. Novice weekly sets +0.4%.
- New gate `b879-experience-tier-test` (10 checks), each verified against an isolating control. Two hash-pinned realizer stages are recorded as Build 879 revisions in the M230 fixture.

## Build 877 — Tablet workspace dock and full Focus workout controls

- Open the set-aware dock by default on expanded tablet/foldable workspaces, while preserving the uncluttered phone logger and allowing a manual workspace toggle.
- Replace compact destructive set-count minus with a labeled, explicit review/confirmation flow that protects completed sets and keeps the existing session-only set adjustment.
- Give Focus mode pinned exercise-note editing and session notes, a plate-calculator launch for the exact focused set load, and full prescribed amplifier technique details.
- Reuse the existing wall-clock rest controls, progression, plate arithmetic, saved exercise notes, live logger, themes and Material motion; no engine or saved-program mutation.

## Build 876 — See the upcoming exercises on Home

- Open the session outline by default so the session name is followed by all actual exercise names, prescribed working sets and rep ranges before starting.
- Keep the optional collapse control and preserve the current choice during resizing. A new program/day/week opens its own outline.
- Retain the primary Start/Resume position, existing prescription reads, workout routing and saved data.

## Build 875 — Compact, actionable set dock and workout safety

- The opt-in dock now follows the first unfinished warm-up, working, drop or myo set. Completed exercises show completion rather than suggesting the final set again.
- Show one progression-owned rep suggestion without a range. Keep editable weight/reps distinct from the recommendation, and use the actual active row for plate calculation.
- Show a prescribed amplifier only on its final working set, with the existing technique explanation.
- Morph height, corner shape and content through the shared interruptible spring owner. The dock uses the same −15/+15, restart, pause/resume and skip controls as the existing rest bar, with one visible timer surface. Respect reduced motion.
- Replace the large reference cards and full-width plate button with a compact suggestion/last-set reference and labeled plate shortcut. Tap the set heading for set-count options; minimize the dock to a small strip without losing entries or rest state.
- Add compact dock weight/reps steppers, direct numeric entry, Log, and session-only working-set count controls. Completed work cannot be removed. Dock fields stay mounted above the numeric keyboard; ordinary-row entry and Focus mode retain priority. Keep the dock opt-in.
- Show one sleeve of the barbell, with each per-side plate once in inner-to-outer order and readable labels. Keep the exact bar/Smith arithmetic and per-side loading explanation.
- Update the measured scroll reserve synchronously during dock transitions, including reduced motion.
- Injury reduction preserves completed sets and manual loads, halves remaining ordinary working sets, and removes pending drop/myo extensions. Suppress prescribed amplifiers on affected session rows, including after resume. Undo and future programs retain their existing owners.
- Add source and real-browser regression coverage for set states, exact reps, rest morph, hit targets, responsive layout, keyboard interruption, injury Undo/resume and unchanged saved prescriptions.

## Build 874 — Workout safety and set-context pilot

- Add **Report an injury** under Workout Options. Select a muscle; the app identifies today's exercises using their primary and secondary muscle involvement. Review individual matches; skip remaining sets or reduce uncompleted work about half. Previously logged sets remain intact, Undo restores the prior session, and changes are scoped to the current day, not the saved training program.
- Include injury adjustment records in the live workout snapshot and completed workout metadata.
- Convert small plate counts and the set-aware loading strip into accessible buttons that open the plate calculator with the **current set's** weight. The exercise toolbar's Plates action also follows the current set rather than set 1.
- Add a themed visual barbell diagram showing plates on **both sides**, correctly derived from existing plate denominations and selectable bar weight.
- Prevent Android long-press copy-menu interruptions on noneditable set chrome without breaking editable weight/reps inputs or their text-selection behavior.
- Separate the compact **Last** load × reps reading from the reported RIR label.
- Add an opt-in **Preview set-aware dock** in Workout Options. It reads actual Last/Suggested/Target and the prescribed final-set amplifier, offers visual plate calculation, and morphs into an elapsed-rest view driven by the existing timer. The dock is explicitly experimental and does **not** take over the logger, prediction engine or timer state.
- Keep the existing workout route, themes, training engine 0.65.7, saved program prescription, baseline logger controls, and offline resume contract.

## Build 873 — Compact Home session outline

- Home's Start workout stays the dominant control. The session exercise list now starts as a single labeled, accessible 48px disclosure row. Expand it to inspect **all** prescribed exercises, sets and rep ranges; collapse it again without leaving Home.
- The disclosure is semantically linked to its controlled list, receives full keyboard/touch targets, honors reduced-motion preferences, and retains the app's original neutral/theme roles.
- Reset an expanded outline when the active program, day or week changes; do not carry the old session's open details into the next prescription.
- Change the recent-training metric label from “This week” to **Last 7 days**, matching its existing rolling seven-day calculation.
- Keep the actual prescribed values, program list, workout logger, shortcuts, history, engine 0.65.7, offline behavior, themes and screen navigation unchanged. Extend permanent R53 mobile/desktop/zoom regression coverage.

## Build 872 — Home task-first hierarchy

- Keep the actual next workout and its Start button as the dominant Home task. The headline now describes the task consistently rather than choosing a random phrase on unscheduled days.
- When training is active, bring the compact **This week / Streak** summary immediately below the next workout; the previous lifetime total no longer distracts from recent consistency.
- Preserve the selected program library and management controls after that training summary. Recovery comes before Strength Score among deeper insights; installation prompts and release news move below training content into discovery.
- Stop pinning **Create Program** beside the bottom navigation when a session is already ready. New program and freestyle remain available as readable action rows after training content. The no-active-program creation dock remains unchanged.
- Preserve the workout logger, Plan / Progress navigation, every theme, engine 0.65.7, saved programs/history, offline loading, and session launch semantics. Extend the R53 real-browser hierarchy/contrast checks to verify active-workout vs creation priority, secondary-action reachability and recent metrics.

## Build 871 — Adaptive medium Plan workspace

- The existing 600–839px medium-window tab shell now uses up to 760px of available width on sufficiently tall windows, instead of remaining a 460px phone mockup. All five primary tabs share the same shell; bottom navigation remains the original owner.
- Plan keeps the same selected-week, Up Next, schedule, supporting Program overview, and open-day actions. The existing 24px medium spacing and 720px reading bound remain intact; the R68 desktop two-column layout and compact/short-landscape layouts are untouched.
- Extend real light/dark browser layout checks with medium windows, all five routes, and live week-selection retention while resizing compact → medium, with no overflow or saved-data changes. The workout logger, 20 themes, motion and engine 0.65.7 are unchanged.

## Build 870 — Workout weight Next / reps Done keyboard flow

- Honor the existing mobile keyboard labels: pressing Next/Enter in an editable workout weight field moves to and selects the reps input **in the same set**. Done/Enter on reps releases keyboard focus but never completes the set.
- Respect IME composition and readonly completed values; leave numeric input data, Target/Last, compact set grid, stepper long-press, rest timers, RIR, history, themes and training engine 0.65.7 unchanged.
- Certify the flow across five phone viewport widths in the existing B825 browser contract. No new sound, haptic, modal or second set-row design.

## Build 869 — readable progression rationale on phones

- Plan Info → Lift progression → Why these methods? now has a full-size native disclosure tap target and displays complete, wrapping exercise and method names rather than truncating them to one line.
- Keep the current theme colors, existing engine-owned reasons and changed/kept flags, keyboard disclosure semantics, saved plans, logged sets and workout controls exactly as before.
- Add phone-browser regression checks for tap size, long-name wrapping, horizontal overflow and native expand/collapse; no new solver, optimizer, sound or haptics layer.

## Build 868 — Responsive touch feedback without forced layout

- Repeated taps on primary navigation, selections and recorded workout dates restart the same subtle theme-native ripple by alternating identical CSS animation names. Removed forced synchronous layout read from repeated tap feedback.
- Animation completion checks the current ripple owner, preventing a cancelled older pulse from clearing a newer tap. Native clicks, button bounds, color themes, reduced-motion settings and compact workout set controls are unchanged.
- Extend the real-browser motion contract for two rapid taps, zero layout-width reads, event-owner correctness and the existing Build 867 calendar feedback.
- R84 200% zoom-navigation diagnostics remain in the integration contract; no extra router or global click handler added.

## Build 867 — smoother motion under interrupted rendering

- Improve shared spring integration at 60/120 Hz and during dropped frames with bounded, adaptive numerical substeps. Transitions keep momentum without the sluggish slow-motion caused by discarding elapsed time.
- Add restrained theme-aware fingertip feedback for calendar dates with recorded workouts. Keep the calendar's existing date controls and the workout set logger untouched.
- Extend permanent browser motion contracts for 60 Hz vs 15 Hz equivalent settling, recorded-day ink, and unchanged interaction hit targets. Existing interrupted sheet/navigation, reduced motion, offline and engine gates stay mandatory.

## Build 866 — Settings organization and direct navigation

- Rename the misleading Units collection to **Training preferences**; it includes weight unit, training experience, effort scale, load increments, and available plates.
- Clarify **Gyms & equipment** as the first Settings section, and add a direct Training preferences choice to the Settings Jump to control.
- Preserve all existing persisted values, program prescriptions, logger controls, theme colors and engine 0.65.7. Add R82 real-browser contract coverage for category and jump-to-section behavior.

## Build 860 — Clearer previews and trustworthy progression

- Correct metric-template load suggestions and warm-ups, preserve configured equipment, and carry units consistently through creation and Settings changes.
- Compare real working-set PRs in one unit across live training, finish, recap, goals, Library and share cards. Keep corrected history dates in order.
- Preserve dose when consolidating overlapping accessory families; charge moved sets to their destination time budget and honor session capacity when adapting blocks.
- Match drop-set guidance to its two logged drops, and align strength exposure explanations with the audit.
- Show useful day headings, actual working-set totals and the deload-inclusive week count in the existing preview. Reflow longer names and larger text.
- Use restrained theme-colored feature containers while retaining the compact workout logger and existing motion/selection owners.

## Build 859 — Clear Settings fields and enlarged-text choices

- Give bodyweight, the 1RM calculator, backup copy/restore and shared-program text stable screen-reader names, including the selected weight unit where relevant.
- Make Training experience respond to text-only enlargement as well as root text scaling. Preserve the existing four/two/one-column layout at ordinary reading sizes and keep whole labels at 200% text.
- Allow Reset everything and its confirmation wording to wrap inside the same button; preserve the existing two-tap confirmation and timeout.
- Extend the permanent R74 browser gate with actual accessibility-tree names, read-only export, unsaved import cancellation/draft retention and enlarged Settings control geometry. Saved prescriptions, training state and all 93 non-App runtime files remain unchanged.

## Build 858 — Readable Program and Cycle setup

- Reflow Program/Cycle options at 200% text so split names remain whole and cycle duration labels stay inside their cards. Units and workout constraints adapt to the available text space.
- Give setup choices, muscle-focus controls and optional generation a real 44px minimum target. Focus labels and totals reflow with larger text. The live workout's compact weight/reps steppers keep their existing geometry.
- Name each setup group by its current question and expose the bodyweight toggle's selected state to assistive technology.
- Extend the existing R74 gate through every first-use Program and Cycle step at all seven windows in Rose/Rosewater, normal and 200% text, Back/Next answer retention, keyboard focus selection and desktop zoom-equivalent resize. Reuse the actual exported Wizard, palette, CSS and motion owners; the inspection fixture does not generate or save plans.
- Preserve engine 0.65.6, all 93 non-App runtime files, progression, gym bar weights and workout persistence. Physical Android/TalkBack, other app forms, the final visual review and stylesheet consolidation remain open. Publication requires all permanent final-head and post-merge workflows, Pages and deployed asset identity.

## Build 857 — R74 Library focus and enlarged-text controls

- Keep the expanded Library detail nonmodal so the existing focus/scroll owner leaves its visible search and exercise list usable. Responsive resize updates that same owner; compact detail retains its modal semantics and native Back/Escape action.
- Restore keyboard focus to the selected Library exercise after Back or Escape, including responsive unmount/remount. Preserve filtered search, exercise identity and nested/IME dismissal priority.
- Reflow Library status filters and exercise-detail tabs into additional rows as text grows, keeping whole labels within their controls. Give Library filters and custom-exercise creation 44px targets. Let the header and creation progress fraction grow with text; label Program/Cycle name explicitly. Hide the duplicate Demo link only when the fallback already supplies the real video link.
- Extend the existing R74 paired-theme/seven-window journeys through filtered Library rows, keyboard entry/exit, 200% text, usable control geometry, nonmodal/modal resize, and draft/search retention at 200% zoom-equivalent layout viewports. Preserve the checked Library/zoom work from PR #139 on the published Build 856 baseline.
- Keep engine 0.65.6, all 70 canonical engine modules and the other 92 non-App runtime hashes unchanged. The only non-App runtime change is UI focus-return code. Full Wizard-step and app-wide target review, physical Android/TalkBack and final stylesheet consolidation remain open. Require all permanent final-SHA workflows, Pages and post-merge checks before publication.

## Build 856 — R74 form continuity and R75 responsive workout certification

- Let navigation labels grow with enlarged text so wrapped words remain inside their real controls. Make the expanded Plan supporting row grow to fit Up Next before Program overview begins. Keep the selected icon indicator and theme colors.
- Keep the gym form and focused empty-bar weight above the phone keyboard using the existing visual viewport signal. Give gym fields and the Gym/Goal forms explicit accessible names.
- Restore focus to the actual activating control when closing an autofocus dialog, including a goal form inside Exercise Info. Escape closes the top layer and releases its scroll lock through the existing dismissal owner.
- Add a permanently registered accessibility browser gate across seven window sizes and paired light/dark themes; cover real Tab/Shift-Tab, IME Escape, nested focus return, text enlargement and emulated keyboard clearance. Expand the workout gate to the same seven sizes in Rose, Rosewater and Violet, with typed values, Complete/Undo, reported effort, plates, navigation, minimize/resume and durable completion.
- Preserve all 93 non-App runtime hashes, engine 0.65.6, progression, saved programs and existing verification gates. Browser evidence is separate from physical Android keyboard, TalkBack and device validation; broader screen/control accessibility remains on the roadmap.

## Build 855 — R74 keyboard focus and forced-colors selection (candidate)

- Preserve visible keyboard focus on the existing Library/detail Back controls, search input and program/cycle Wizard inputs. Use the saved-theme focus role and the Windows high-contrast Highlight system color without changing hitboxes, field values or the M3 animated selection track.
- Give semantically selected navigation and segmented tabs a non-color underline when Windows forced-colors is active; no paint changes under ordinary light/dark themes. Selection still originates from aria-pressed/aria-selected/aria-current on the same controls.
- Extend the real R65 browser contract to verify named >=44px navigation targets, Plan/Progress selected state in actual forced-colors emulation and Library detail visible keyboard focus in ordinary and high-contrast modes, paired themes and four viewport classes. Full R74 form/sheet Escape, 200% text and virtual-keyboard certification remains pending; R75 full device certification is separate.
- Keep compact workout logger, typed stepper values, double progression, configured bar weights, offline state and existing release gates unchanged. Publish only after finalizer hashes, all permanent premerge checks and Pages/postmerge verification.

## Build 854 — R73 rendered palette contrast and pointer-state parity (candidate)

- Extend the existing 20-theme/5-screen audit to verify the actual selected and idle text contrast (at least 4.5:1), selected indicator distinction (at least 3:1) and nongradient control surface on both real Plan and Progress tab groups; do not trust token declarations alone.
- Bring theme-derived feature tint to nonselected segmented tabs on pointer hover and inset tint on press, covering Plan, Progress, Settings, Library and exercise-detail choices. The selected moving indicator and native click behavior remain owned by the existing UI.
- Keep selected-button paint transparent so the existing moving M3 indicator owns selection. Animate the hover/press tint only on idle buttons at 160 ms; suppress decorative transitions under reduced motion. No geometric animation, gray replacement, or changes to the phone set logger.
- Expand R65 paired-theme real-browser tests with actual desktop hover color-role matching and preserved selected tab; preserve R65 mobile, landscape and tablet geometry checks.
- This is R73 slice 2. The app-wide color/interaction system continues into further focused inspections before R74 accessibility and R75 full certification. Release requires exact final-SHA permanent CI and postmerge Pages verification.

## Build 853 — R73 theme-native states and motion (first release slice)

- Unify the visible selected, idle and focus states of the adaptive Library and live Workout exercise companion; use the saved theme's tinted surfaces, accent stroke and keyboard focus color, never generic gray.
- Apply the same 160 ms color-only motion role to existing Plan, Progress, Settings, Library and Exercise detail segmented controls. Keep the shared active-indicator position logic and all input/control dimensions untouched.
- Disable these decorative transitions for reduced-motion preferences and use system Highlight for forced-colors keyboard focus. Do not change generation, progressions, sets, rep entry, workout state or offline history.
- Extend paired-theme real-browser coverage on Plan, Progress and expanded Workout with reduced-motion, selected/idle and resize checks. This is one incremental R73 release; app-wide token auditing and R74/R75 accessibility/device certification still remain.
- Gate any publication on the exact finalized candidate SHA passing the four permanent workflows, then verify Pages and post-merge checks.

## Build 852 — R72 adaptive live Workout Experience (candidate)

- Expanded tall workout windows provide a theme-native session lineup beside the existing set logger. The lineup uses the live session's current exercise, working sets and original goEx navigation handler rather than a second workout state owner.
- Phone, medium, short-landscape and focused logging retain the proven editable weight/reps grid, steppers, completion fade, effort, rest, plate breakdown and original keyboard behavior.
- Permanent R72 paired-theme real-browser contract exercises compact and expanded layouts, exercise navigation, breakpoint resize, focused mode, overflow and saved-program preservation.
- Draft PR #135 must pass all four permanent workflows on the final released SHA before merge and Pages validation. No engine or progression modifications in this slice.

## Build 851 — R71 responsive Program and Cycle creation

- Expanded tall windows show the same working Wizard form beside a read-only, theme-native live setup overview. This overview reflects real selected format, cycle template and blocks, goal, days, split, capacity, equipment, focus and workout rules; it does not generate or save an alternate program.
- Medium, phone and short landscape windows retain the original focused setup flow, actions and keyboard behavior. Resizing preserves the same entered answers.
- Extend the permanent R65 paired-theme real-browser matrix with the actual Home→Program/Cycle Wizard flow, live input reflection, responsive geometry and no-overflow assertions.
- Preserve existing workout logging, independent Dynamic Double set progression, gym bar masses, program-generation safeguards and offline data.

## Build 850 — R70 Progress responsive hierarchy

- On expanded tall windows, keep the existing Sessions, Lifts, Volume and Body state owners while arranging training consistency and its calendar alongside the actual filtered workout history. The weekly summary stretches across both reading lanes.
- Give load/recovery insights paired chart columns when space permits. Keep the exercise list, Body logging, and phone/short-window layouts intentionally familiar and readable.
- Constrain charts, text and history filter rows to avoid horizontal clipping. Extend the permanent R65 light/dark real-browser matrix to test populated history geometry and Body panel bounds.
- No changes to workout logging, progression algorithms, prescriptions, weights, or data persistence.

## Build 849 — R69 adaptive Exercise Library

- On wide, tall windows, place the existing filtered Library list beside its selected exercise detail, preserving one selected exercise, history, notes, attachments and variant actions. Compact/medium screens retain full-screen detail.
- Identify the selected exercise accessibly and verify real click-through, theme pairing, resize reflow and persistence with the existing R65 browser contract.
- No engine, workout logger, saved workout or progression behavior changes. Preserve the material surface tokens and no-gradient rule.

## Build 848 — R68 responsive Plan supporting pane

- On suitably wide, tall windows, the existing Plan schedule and tabs become the primary reading column; the existing Up Next and program overview move into a supporting column using CSS Grid. No duplicate week selection, router, or prescription state.
- Compact and medium windows retain the established vertical Plan layout. The selected-week picker, day links, training data, themes, focus treatment and phone workout logger are unchanged.
- Extend the existing adaptive-shell real-browser test to assert Plan schedule/supporting-pane geometry and no horizontal document overflow across light/dark themes and all four viewport sizes.
- Require the unchanged four permanent CI workflows and post-merge Pages verification before shipping.

## Build 847 — R67 adaptive surfaces pilot

- On sufficiently tall tablets/laptops, limit gym editor, achievement and program-switcher sheets to readable centered surfaces. On phones and short landscape windows, preserve the familiar bottom-sheet interaction and gesture handlers.
- Expand Plan's reading width only in the rail layout; preserve all current Plan mode, selected-week and next-session state.
- Keep chosen theme surfaces, focus rings, reduced-motion support and high-contrast fallback. Do not alter workout logger layout, existing progression rules, gym bar weight behavior or user data.
- Extend the phone/desktop gym browser contract with geometry assertions; record the remaining app-wide responsive work and the release gates in UI_UX_MASTER_PLAN_R67.md.

## Build 846 — Editable barbell and Smith bar weights

- Add independent empty-bar mass settings for barbells and Smith machines in each gym. Smith bars can be 0 when counterbalanced. Changing units converts actual values instead of reinterpreting numbers.
- Plate Calculator, focused workout plate breakdowns, inline load readouts and bar selection labels use the active gym's bar mass. Logged set weights remain total load, including the bar; existing workout history is unchanged.
- Import/export and persisted gyms retain custom masses; gyms with no overrides continue using the established barbell (45 lb) and Smith (35 lb) defaults.
- Introduce node and real-browser regression contracts for separate gyms, zero-mass Smith, lb/kg switching, defaults, saving and mobile/desktop editor controls.
- No gym migration, change to progression algorithms, or unrelated theme color changes.

## Build 845 — Curl weight stepping and Dynamic Double progression

- Incline dumbbell curl steppers follow the gym's available dumbbell rack, including loads below 25 lb. Without a rack list, a coarse legacy exercise increment cannot skip from 25 lb straight to zero; light dumbbells step down by normal 5 lb / 2 kg increments.
- Dynamic Double advances each completed set independently when its own reps reach the prescribed ceiling at acceptable effort. Other sets retain their own loads and advance reps rather than inheriting a jump from the top performer.
- Incomplete, edited, interrupted and recovery-limited sessions retain their safety gates. Conventional Double Progression remains an all-sets rule.
- Preserve actual logged sets and user-entered values; no engine generation, cycle, equipment persistence, UI theme or storage migration.

## Build 844 — R65 adaptive tablet and desktop shell (candidate)

- Use a theme-native 100–112px navigation rail and wider reading-width primary screens when windows are at least 840px wide and 560px high. Keep the five existing navigation actions and moving M3 selection track.
- Keep the familiar bottom bar and capped phone layout on narrow and short landscape windows; do not touch workout logging or its controls.
- Make navigation keys follow the displayed axis (up/down for the rail, left/right for the bottom bar), and prevent the rail from masquerading as a bottom barrier to contextual menu positioning.
- Add a real-browser, paired light/dark adaptive shell contract at 393x852, 900x480, 900x1000 and 1280x850. Non-primary screens and split-pane information architecture are reserved for the next responsive slice.

## Build 843 — R64 theme-native search and contextual menus

- Use the saved theme's control, ink, and outline roles for Library search, program-switcher search, and Progress history filters.
- Contextual menus follow theme card materials while retaining the original selected-day highlight and danger red for destructive actions.
- Preserve existing 48px clear action, search/filter state, menu placement and keyboard behavior. Engine 0.65.6 and workout logger are unchanged.

## Build 842 — R63 themed materials and quiet workout tools

- Restore each dark preset's own background, card and control colors across the main screens and shared controls; use the saved accent/foreground pair for selected choices.
- Restore a restrained theme tint to pinned workout notes and subtle theme boundaries to compact coaching.
- Return Warm-up, Superset, Swap and Plates to unfilled icon/text controls with their existing 48px touch targets.
- Keep exercise-info sheets and their Close action on screen after scrolling or editing a note; preserve the sheet's own motion and drag behavior.
- Extend the existing browser checks with all 20 live theme choices and note-editor/info-menu journeys that preserve entered set values and program prescriptions.

## Build 841 — R62 Home and Plan task hierarchy

- Put Home's Start workout action before the exercise outline and saved programs before creation discovery.
- Put Plan's next session, week picker and connected session rows before the program overview; retain Volume, Progression, Blueprint and full-program access.
- Extend shared actions to text and row controls, and introduce spacing, type, shape, target-size and content-width roles for later responsive layouts.
- Use available-window insets on Home and Plan while retaining the current single-column shell; navigation rails and multiple panes remain planned work.
- Let passive notification text pass pointer input through to underlying controls while keeping Undo, action and Dismiss buttons clickable.
- Reserve layout space for Home's opaque Create/Quick bar so native scrolling cannot leave program options hidden behind its buttons.
- Anchor the selected navigation indicator to its own bar so height-only resizing cannot leave it floating over content.
- Extend the existing paired-theme browser gate with reading/action order, continuous phone/tablet/laptop resizing, retained outline/week state, short windows and enlarged text.

## Build 840 — R61 foundations pilot

- Plan session labels and shared runtime surfaces follow the live saved theme on cold start, warm start and in-app theme changes.
- Profile distinguishes missing bodyweight from missing scored lift data, and retains the existing Strength Score when evidence is available.
- Pilot shared headers, primary/secondary/icon actions, selected choices and static guidance on Home and Plan; keep native button semantics and existing navigation/motion owners.
- Give Start workout primary emphasis and Create Program secondary emphasis when a session is ready; preserve all creation and freestyle entry points.
- Keep full-size hit targets, allow Plan session names to wrap, and pair tonal selected choices with a readable foreground.
- Extend the existing phone gate with actual label contrast, stale startup cache, warm theme changes, Profile states and pointer access.
- Expand the companion UI/UX Master Plan with Material tablet/laptop navigation, pane layouts, content constraints, input behavior and resize/zoom acceptance criteria. Those adaptive layouts are planned future slices.

## Build 839 — R60 browsing context

- Exercise Library retains search, muscle, equipment and Recent/Goals/Banned filters when returning from Compare.
- Clear the exercise search with a 48px button while keeping the selected filters.
- Progress remembers its section, lift pane, searches, history filters, grouping, expanded sessions and loaded history page during the current app visit.
- Each Progress section and lift pane keeps its own scroll position; switching to Body cannot inherit a long Records offset.
- Home’s Workout history shortcut explicitly opens Sessions at the top; a fresh app visit or data reset clears browsing context.
- Extend the existing phone-flow gate in paired light/dark themes at 320/393px, including exact scroll restoration and training-record preservation.

## Build 838 — R59 app flow and return navigation

- Plan follows the active training program even after another saved program is browsed.
- Day previews open the correct full program on the selected week and return through the same preview.
- Keep each program's selected Plan week, section and disclosures during the current app visit; restore Plan and preview scroll positions on Back.
- Minimizing or finishing a workout returns to its launch screen. Resuming from a different screen updates that return destination without replacing entered sets.
- Program-switch notifications immediately clear the returned workout dock, keeping Resume and Discard tappable with reduced motion.
- Finishing the current training week reveals the next due week and its workout action; deliberately browsed weeks retain their context.
- Extend production phone journeys at 320/393px in paired light/dark themes, including completed history, active-program switching and offline recovery.

## Build 837 — R58 Material controls and CSS cleanup

- Kept the collapsed Home header at 56px with real 48px toolbar buttons; aligned Plan Back, Profile Edit and Settings Jump touch sizes.
- Prevented broken Settings experience labels with readable, text-size-aware four/two/one-column controls.
- Removed 389 superseded CSS declarations after comparing computed styles across 16 light/dark screen states.
- Extended phone regression coverage for narrow layouts, larger text and persisted experience choices.

## Build 836 — R57 cross-screen header and spacing consistency

- Tightened Plan, Progress, Profile and Settings header rhythm against one set of inset/spacing tokens rather than conflicting legacy paddings.
- Kept Back, Edit, section tabs and Settings Jump controls accessible at >=44dp, with subdued semantic surfaces and untruncated content.
- Added paired Rose light/dark phone geometry checks at 393dp and narrow 320dp widths, preserving Home's verified 56dp collapsed header.

## Build 835 — M3 Expressive R56 Home header density

- Collapsed Home now reduces its actual chrome height, instead of merely hiding the greeting while leaving the full branding/toolbar row.
- Restores a compact Iron identity, neutral toolbar surface, and three 44dp controls, with responsive 320dp layout and safe theme-aware on-colors.
- Enlarges browser coverage to test scroll-down/scroll-back header behavior, light/dark palettes, narrow-phone overflow, and live Exercise Library navigation.

## Build 834 — M3 Expressive R55 expanded Program hierarchy

- Removed the mismatched gray day/plum exercise surfaces, replacing them with shared theme-derived neutral elevations.
- Improved contrast for muscle chips, metadata, and progression controls; exercise titles now wrap instead of being cut off by badges.
- Added visual verification of the *expanded* Program to the two-theme phone screenshot/contrast audit while preserving R54 manual-week selection and exercise controls.

# Build 832 · R53 Material 3 Expressive Plan surface architecture

- Give Plan phase, week, week-picker, and session rows named, reusable semantic component classes rather than anonymous inline color blocks.
- Use neutral Material surface roles for repeated weeks and nested workouts; reserve accent for the current week, NEXT destination and completed markers.
- Remove inline week/phase panel background paints and expose full-program WeekPill active selection with aria-pressed.
- Add permanent side-by-side Rose dark / Rosewater light screenshot audits of Home, Plan, Progress, Profile, Settings and Program at 393px.
- Preserve 20-theme contrast coverage, movement data, workout logger, touch geometry, reduced motion, and offline behavior.

# R52 · semantic surfaces and theme-aware selection · Build 831

- Correct actual Settings Theme and Units containers that previously bypassed the shared dark surface roles.
- Use a consistent quiet card for Settings content and subdued Plan day cards instead of repeating deep plum panels.
- Return the user's theme hue to segmented choices and navigation, using accessible light-tonal indicators rather than unrelated slate-gray selected pills.
- Distinguish primary Progress navigation from its secondary grouping; preserve motion, 48px controls, all saved theme variants and training state.
- Add rendered Settings/Program paint assertions and screenshot evidence; no changes to logger inputs, training engine, storage, or lifecycle.

# Material 3 Expressive R51 — shared selection and neutral dark surfaces

- Use one dark page/card/control ladder across Settings and other destinations instead of purple/maroon surfaces.
- Give segmented controls and the animated bottom-nav indicator a clearly distinct selected fill with readable active text.
- Preserve workout logger geometry, spring motion, 20 user accents, existing training functionality, and light themes.
- See R51_COLOR_SYSTEM_AUDIT.md for research, contrast targets and device validation plan.

# Build 831 · expressive interactions across the app · Engine 0.65.6

- Apply shared spring feedback to buttons, selected controls, navigation and switches across the app.
- Keep sheets continuous through opening, dragging, cancellation, dismissal and quick reopening.
- Smoothly expand and collapse Info sections and RIR prompts; confirm effort within the existing picker width.
- Animate Target / Last reference changes without altering entered weights, reps or progression.
- Preserve compact, muted completed rows and inline effort; honor reduced motion and background lifecycle.
- Include browser coverage for the main tabs, sheet drag, rapid navigation, persistence and narrow phones.

# Build 830 · compact logged effort · Engine 0.65.6

- Place recorded effort directly under logged reps as 2 RIR; tap the value to edit.
- Remove the separate + effort row and completed target effort tag; unreported effort stays unreported.
- Preserve muted completed rows, original logged fields, Undo, active steppers and the latest reps-left prompt.
- Verify completed-row density and effort persistence at 320–520px.

# Build 829 · restore full faded set rows · Engine 0.65.6

- Restore original full set row renderer: logged sets retain their reference, weight and reps columns, completed check and RIR prompt.
- Dim completed rows and checks with theme tokens instead of bright filled states; preserve the strong highlight for the current incomplete set.
- Remove summary-only completed-row renderer, retain numerical tap-to-select, last-time shortcuts, rest, Focus and engine behavior.
- Add browser assertions for 320–520px, all sets completed, legible logged values and Undo.

# Build 828 · completed-set readability hotfix · Engine 0.65.6

- Restores visible set number and actual logged weight × reps on every completed set, without brightly highlighting completed rows.
- Keeps compact RIR editing and Undo, including when all sets in the exercise are complete.
- Includes mobile-width regression tests for the all-sets-complete case; no progression or engine changes.

# Build 827 · current-set focus · Engine 0.65.6

- Smaller complete buttons and narrower weight fields leave more room for the four steppers.
- Completed sets collapse into muted reps-left bars; the current set keeps the accent highlight.
- Effort edits, undo, tap-to-select, last-time shortcuts, plates and rest navigation stay available.

# Build 826 · restore workout layout baseline · Engine 0.65.6

- Restore build 824's workout rows, history shortcut, plate annotations and effort presentation; remove build 825's reserved detail space.
- Retain the history and load-correction fixes and keep exercise navigation available during rest.
- Select the contents of editable numeric fields on tap or keyboard focus for quick replacement typing.

# Build 825 · stable set logging · Engine 0.65.6

- Keep Next exercise and Finish workout available in their bottom navigation slots during rest.
- Keep LAST and TARGET in the same row space, with load × reps together and compact effort guidance.
- Preserve input positions when logging and undoing sets, and reserve one consistent strip for shortcuts and reported effort.
- Keep long load explanations behind a Why disclosure and reduce duplicated history text in LAST mode.
- Add full-app phone regressions for reference toggles, set/effort logging, navigation during rest and undo at 320–430px.

# Build 824 · workout history and load correction · Engine 0.65.6

- Use one latest-workout set reference for the Last column, Last time shortcut and Focus view.
- Keep missing prior sets empty and show reported effort without substituting planned effort.
- Correct excessive loads from completed below-range sets before the incomplete-workout hold; missing sets never earn progression or count as failure.
- Preserve explicit interruption, recovery and prescription-edit protections, along with completed and manually edited resumed rows.
- Extend the existing generated/custom progression and phone regressions with the incomplete squat ramp.

# M265 · responsive plan building · Engine 0.65.6

- Build and rebuild programs and cycles in the background, with visible status and Cancel.
- Prevent cancelled or outdated builds from replacing a newer plan; keep the current plan intact if building fails.
- Connect standalone next-block and cycle-advance actions to the audited, history-aware engine.
- Keep completed cycles, programs and workout history when rolling a fresh cycle.
- Include background building in the installed offline app.

# M234 · build 823 candidate · Engine 0.65.6

- Cache static exercise eligibility against full canonical exercise objects; never project away prescription, technique, provenance, or semantic metadata.
- Evaluate hard constraints before ranking and cap expensive ranking work for pathological custom catalogs while preserving M233 ordering for normal catalogs.
- Keep M230 decomposition verification intact; only the two deliberately evolved ranking snapshots move forward.

# M233 · build 822 candidate · Engine 0.65.5

- Add immutable semantic exercise relationships and explicit versioned custom mechanics; protect complementary functions and strength specificity.
- Use one semantic gate for phase continuity and require stricter evidence for numeric transfer than for stimulus transfer.
- Allow conservative, loadable reference-only starts from recent owned observations of reviewed barbell variants; borrowed history never earns progression.

# M232 · build 821 candidate · Engine 0.65.4

- Ease one untouched automatic accessory row only after two explicitly observed misses; preserve manual/completed work and strength protocols.
- Keep changes bounded and loadable, recover them without compounding, and roll back pending targets when source evidence is undone.
- Carry live recovery limitations into history so eased work cannot earn progression.

# M231 · build 820 candidate · Engine 0.65.3

- Learn bounded, confidence-aware dose and capacity preferences from program/day-owned completed observations.
- Track frequency, fatigue, exercise fit, progression velocity and recovery with explicit exclusions; preserve sparse-evidence priors and authored intent.
- Connect actual shell history and next-block/cycle adaptation to the canonical response model.

# M230 · build 819 candidate · Engine 0.65.2

- Split the realizer into canonical strength, hypertrophy, ranking, timing, pairing, sequencing, construction and repair owners.
- Preserve 67 source fragments and all 13 Build 816 golden scenarios; reduce the public coordinator to 317 lines.

# M229 · build 818 candidate · Engine 0.65.1

- Add versioned request, program, prescription and history contracts, core TypeScript declarations, explicit unversioned migrations and future-schema refusal.
- Separate prescription projection from generation, removing the adapter–volume dependency cycle.
- Preserve existing program and workout decisions across Build 816 golden scenarios.

# M228 · build 817 candidate · Engine 0.65.0

- Move 456 training-domain declarations from App.js into five canonical production modules; UI and independent audits import the same API.
- Distribute the engine byte for byte without AST declaration extraction.
- Preserve Build 816 output across 13 golden program/prescription/volume/scheduling scenarios and correct stale M220 assertions to enforce M226 progression ownership.

# M227 · build 816 · Engine 0.64.16

- Scope progression, stall, fatigue and training-max evidence to the active program and authored workout day; unrelated history can seed a conservative starting reference but cannot earn progression.
- Parse and bound imported percentage/tiered progression state so malformed semantic state fails safely instead of producing plausible wrong prescriptions.
- Canonicalize equipment, loading units, booleans, split names, volume strategy, exercise preferences and custom-exercise records at the public generation boundary.
- Preserve request immutability and deterministic output for cosmetically equivalent requests; add permanent M226/M227 regression gates.

# M225 · build 815 · Engine 0.64.14

- Complete the recovered M224 engine audit repairs.
- Share recovery/edit/interruption context rules across progression methods and preserve exercise/set provenance in actual workout logs.
- Reject malformed numeric evidence and recover from invalid revision timestamps.
- Verify standalone generation against production directly; require M224/M225 regression gates.

# M224 · build 814 · Engine 0.64.13

- Fix all 14 reproduced audit families: units, effort provenance, history identity, custom snapshots, capacity, instruction types and percentage runtime parity.
- Add shared history contracts, pure workout serialization and executable percentage protocols.

# M223 — Installed icon refresh and independent engine audits

- Use content-hashed PNG icon URLs with the same installed app identity and data namespace.
- Retrieve the current manifest online and retain an offline fallback; no data reset or reinstall.
- Add a reproducible independent engine export: all 46 engine modules, the complete catalog, headless shell computations, semantic tests and complete integration-boundary source evidence.
- Engine behavior remains 0.64.12.

# M222 · build 812 · Engine 0.64.12

- Custom programs use canonical progression with selected methods, completed-set and effort safeguards.
- Position-specific rep targets replace max-rep defaults; dynamic targets retain positional loads.
- Load reductions restart at the rep floor. Advice, targets and saved automatic rows stay synchronized.
- Custom settings preserve authored rosters/techniques and persist Disallow supersets.
- Existing workout edits, logged sets, import/history and cycle structure are retained.

## M221 — engine input stability (build 811)

- Normalize malformed equipment lists without throwing; explicit empty lists remain empty rather than reverting to an imaginary default inventory.
- Bound plate-combination queries to the requested load and fail closed on pathological expansion. Prevent non-finite load recommendations.
- Reject invalid strength-estimate evidence without poisoning stronger valid sets.
- Blank, partial or invalid rep ranges use safe workout defaults; completed/manual entries and loads remain untouched during restoration.
- Engine 0.64.11 retains the last five compatible engine versions. Current designs, training-dose rules and day-specific progression stay in place.
- Add permanent M221 semantic checks, including 2,394 exhaustive finite-inventory comparisons.

## M220 — day-specific double progression (build 810)

- Custom routines use the newest session of the same program and training day for repeated exercises. Other days supply a starting load only until that day has its own history.
- Suggestions and Last values share the same reference; a three-set Legs workout cannot replace a five-set Lower workout or qualify its load increase.
- The generated-program bridge also keeps its prior-session details and changed-week reference aligned with the day its evaluator used.
- Resume corrects untouched automatic custom-plan loads while preserving completed, manual, edited, added and technique sets.
- Added the reported 205/210 lb calf-raise regression to release and phone checks. M219 UI integration remains available; training-engine prescription policy is unchanged.

## M219 — UI integration (build 809)

- Highlighted labels and training details are clearer across light and dark themes, with larger tap areas for common controls.
- Finished sets and phase labels stay readable. Plan keeps its week navigation, and session estimates update when rest length or custom exercises change.
- Sheets close smoothly and respect reduced-motion settings. Buttons release without an abrupt snap.
- Workout chimes reuse one audio context while training and release it when you leave the workout.
- The recent release-note repair, compact theme picker, manual entries and scheduled final-set techniques remain available.
- The workout summary accommodates its exercise-list touch target without clipping, and all five effort choices stay together on narrow phones.
- M218 phone polish remains the baseline; the AI archive is not used as a replacement. Engine prescription policy remains 0.64.10.

## M218 — phone polish (build 808)

- Fixed “What's new couldn't load” on Home and in Settings. Current release notes now use the archive's shared formatting adapter, so plain-text entries receive a valid icon and title.
- Workout actions use quieter controls and consistent spacing. Target loads and rep ranges have deliberate separate lines, and the final-set technique stays attached to the lift and its last working row.
- Plan puts Up Next near the top, uses one completion summary, names weekly set counts clearly, and retains phase icons and the full schedule. Phase goals use readable labels.
- Choosing a week brings its expanded days into view beneath a sticky week picker; view changes retain the selected week. Initial loading centers the current week without moving the page past its overview.
- Settings displays the saved theme in a compact, keyboard-accessible disclosure. All light and dark themes remain selectable, with complete labels and persistent selections.
- Added a full-app phone gate for release-note loading, persistent dismissal, Settings replay and archive navigation, theme selection/reload, later-week navigation, technique details, decimal entry, target readability, focused mode, and Back/resume at 320–390px and landscape.
- Training-engine and storage policy remain unchanged from M217. Offline cache rotated for build 808.

## M217 — Android workout recovery (build 807)

- Saving a completed workout now commits history and performance together before removing its recovery copy. If device storage rejects the write, the workout stays open with a retry action.
- A stable workout ID prevents an interruption during cleanup from producing a second copy of a completed workout.
- Minimize and Android Back keep the workout open when its current snapshot cannot be saved. Update restart also waits for a successful store save.
- Screen-awake requests are deduplicated, released when backgrounded or closed, and reacquired on return. A late request cannot keep Home awake.
- Installed apps allow landscape and split-screen layouts. The new production browser gate covers failed writes, Back, numeric entry, paused timers, a renderer crash, offline cold restore, and completion cleanup.
- Engine prescription policy remains 0.64.10; recent intensifier and phase-duration fixes carry forward.

## M216 — compact block length (build 806)

- Replaced the expanding duration button row with one week field and minus/plus controls. Custom routines support direct typing and one-week adjustments.
- Blank, zero, negative, fractional and invalid entries remain editable and cannot be saved. The saved program changes only when a valid draft is applied.
- Generated cycle phases display their assigned duration in a compact read-only card. Saved-program duration remains authoritative throughout cycle views.
- Extended the production browser gate to cover stepping, keyboard arrows, direct entry, uncommon saved lengths, reload and 320/360/390-pixel layouts.
- Engine prescription policy remains 0.64.10; offline cache rotated for build 806.

## M215 — cycle duration consistency (build 805)

- Existing custom programs keep their actual working-week length when converted into a cycle; a 10-week routine no longer inherits the template's 6-week entry label.
- Cycle list, detail, Home block rows, Plan phase ranges and calendar totals resolve each phase's duration from its saved program. Older stale cycle metadata remains readable, with deload counted separately.
- Program settings include 3-week and existing uncommon lengths. Generated cycle phases show their actual cycle-owned duration; custom routines remain editable.
- Confirmed custom intensifiers remain on their scheduled later weeks; duration correction preserves exercises, prescriptions and history.
- Added behavior and 390/360-pixel browser gates. Engine policy remains 0.64.10; offline cache rotated for build 805.

## M214 — executable prescription synchronization (build 804)

- History evaluation and workout suggestions now read the same user-owned sets, reps, effort, rest, techniques and progression methods as the visible workout.
- New completed-workout logs retain a prescription snapshot and explicit effort provenance, so later plan edits cannot rewrite their historical targets. Unfinished rows, warmups and technique extensions cannot count as completed working sets.
- Cycle-wide swaps and reorders update movement identity and keep each later phase's own prescriptions and explicit edits attached to its lifts. Existing stale saved identities resolve from the visible roster.
- Conversion and actual block advancement use an immutable live-roster projection; retained per-lift manual methods survive locked/adaptive continuation.
- Removed the unreferenced 1,447,745-byte root bundle and duplicated technique decoder; reused catalog/snapshot work at history and repair boundaries.
- Added behavioral and phone-sized browser release gates. Pursuit Engine 0.64.10; offline cache rotated for build 804.

## M213 — zero-unused App cleanup (build 803)

- Removed the final dead App callback, the retired in-component cycle-edit propagation wrapper.
- Kept the live `propagateCycleEditsPure` implementation used by `programSavePlan` and its exported verification seam.
- App static analysis now requires zero unused bindings; the stale comment claiming the dead wrapper was the helper's only caller is gone with it.
- Engine behavior remains 0.64.9; offline cache rotated for build 803.

## M212 — App signature cleanup (build 802)

- Removed unused view/component props and their call-site plumbing.
- Removed discarded simulation/swap calculations and a no-op ProgramView state hook.
- Removed the retired next-engine-only boolean plumbing now that Pursuit Next is the sole generation path.
- Preserved historical/exported positional seams with explicit ignored parameters instead of breaking callers.
- Tightened the static gate: only the separately-audited cycle-propagation callback may remain unused after this release.
- Engine behavior remains 0.64.9; offline cache rotated for build 802.

## M211 — App pure-local cleanup (build 801)

- Removed abandoned dimensional-token scaffolding whose values were never read.
- Removed simple dead App reads/helpers and unused destructured fields without touching hooks, callbacks, prop contracts, or backup compatibility.
- Added source-level cleanup invariants so the retired locals do not silently return.
- Engine behavior remains 0.64.9; offline cache rotated for build 801.

## M210 — App shell static cleanup (build 800)

- Removed three unused shell-adapter imports and three dead module-scope constants from the App bundle.
- Removed stale comments that described the retired constants as active constraints.
- Deliberately left component props, state, and lifecycle-adjacent findings for separate context-aware review.
- Added cleanup invariants preventing the six dead bindings from returning. Engine behavior remains 0.64.9; offline cache rotated for build 800.

## M209 — Unused engine binding cleanup (build 799)

- Ran a scope-aware static unused-binding audit across all authored runtime modules.
- Removed ten unused engine-core bindings: stale imports/helpers, unused audit-selection bookkeeping, an obsolete capacity-intent table, and dead recovery/allocator reads.
- Kept App-shell findings for a separate UI-aware pass rather than mixing shell cleanup with engine cleanup.
- Added cleanup invariants preventing the removed engine bindings from returning. Pursuit Engine 0.64.9; offline cache rotated for build 799.

## M208 — Dead export cleanup (build 798)

- Audited the complete production module graph: all 50 authored runtime modules remain reachable from the real app entry point.
- Removed 15 runtime exports with zero references anywhere in production, verification, or documentation, including obsolete M81 compatibility helpers and unused diagnostic/loading APIs.
- Preserved live shell-equipment wiring plus verification-only simulation/setup contracts that still protect engine behavior.
- Added cleanup invariants preventing the removed APIs from silently returning. Pursuit Engine 0.64.8; offline cache rotated for build 798.

## M207 — Runtime cleanup and compatibility separation (build 797)

- Removed the unused coach-review proposal/approval/fixture wrapper while retaining the objective guardrails used by production audits and verification.
- Removed retired canary/promotion props from the Settings component surface; historical research/rollout records remain data-only backup compatibility and still round-trip through migration/import/export.
- Extended the cleanup regression contract so retired review APIs and dead Settings props cannot silently return.
- Preserved M206 generation, volume, progression, ownership, and recovery behavior. Pursuit Engine 0.64.7; offline cache rotated for build 797.

## M206 — Exact working-week volume (build 796)

- Unified minimalist public guidance with the existing approach-aware engine dose model while retaining standard-volume floors.
- Added immutable week-level accessory-set reconciliation across standalone generation, cycles, conversion, history adaptation, and saved-plan Auto-fix. Repairs preserve the roster, strength work, manual edits, recovery weeks, and session limits.
- Kept minimalist sets capped through weekly modulation and static/adaptive block transitions, including older converted snapshots.
- Corrected executable snapshot supersets, rep/RIR ranges, rest and technique accounting; shared lookup context and avoided needless session reconstruction for valid scalar cells.
- Added 13-route regression coverage with four reproduced rounding failures plus capacity transfers, ownership, persistence, lifecycle caps, and honest unable/partial outcomes. Engine 0.64.6; cache rotated for build 796.

## M205 — One production engine and retired-code cleanup (build 795)

- Removed the unreachable old program generator and production shadow/canary/promotion execution. Program creation and block transitions use the current audited engine directly.
- Browser and Node now use one capacity-aware shell adapter. The import-map substitution, duplicated base module, and old speculative feasibility generator are gone.
- Current programs are included in Settings self-tests; the supported list now contains the current prescription engine and its four predecessors.
- Historical custom-plan schedules, manual prescription ownership, workout history, and backup research records remain readable. Research records are handled by a small data-only compatibility module.
- Retired milestone patch/finalizer scripts, obsolete release workflows, and stray placeholder files are removed. Release metadata comes from one current manifest; CI verifies it without rewriting or pushing code.
- Removed unused comparison/review modules and the empty type artifact; the coach-quality verification oracle now lives with verification rather than in the offline runtime.
- The numeric saved-plan registry keeps only its nine used compatibility gates; duplicate, overwritten historical descriptions and obsolete generator-development notes were removed.
- Reused each candidate's functional-coverage evaluation within its repair transaction. Pursuit Engine 0.64.5; offline cache rotated for build 795.

## M204 — Prescription ownership hardening (build 794)

- Automatic Engine plans now have one prescription owner: the generated week cell/audited Engine state. Stale `overrides` values can no longer freeze sets, reps, RIR, role, or progression style after migrations, phase changes, or repairs.
- Explicit user edits are tracked per field with `prescriptionOwners`; manual-mode sets/reps/RIR/rest are seeded as user-owned, while legacy rest and technique edits remain compatible.
- Live workout rows now carry `valueOwner: "prescription"` or `"user"`, preventing restore/tuning logic from overwriting typed values because of a stale `auto` boolean.
- Volume repair no longer mirrors generated set counts into overrides and refuses to rewrite explicitly user-owned set counts. New Engine slot overrides store identity rather than duplicate prescription metadata.
- `commitProgram` now resolves once and writes the same program object to both open and saved state; it no longer updates one React store from inside another state-updater callback.
- Added an ownership regression gate plus compatibility coverage. Pursuit Engine 0.64.4; service-worker cache rotated for build 794.

## M203 — Custom-program final-set intensifier persistence (build 793)

- Custom programs no longer lose their implicit last-set technique schedule when optional legacy metadata such as `engineV`, `slotBias`, or `autoBias` is absent after migration/import. `custom: true` is now the durable identity.
- Explicit technique choices still win, including an explicit Off value. Strength days, primary lifts, introductory timing, and deload behavior are unchanged.
- The last working-set row now shows its `+ partials`, `+ myo-reps`, `+ stretch`, or `+ drop set` badge even before that row becomes active, so the intensifier is visibly attached to the correct final set.
- Regression fixtures now use a markerless saved custom program and verify both technique restoration and the visible final-set badge. Engine remains 0.64.3; service-worker cache rotated for build 793.

## M202 — Locked-cycle phase volume reconciliation (build 792)

- Locked/static cycle blocks now audit the exact weekly regional volume shown in the UI after phase retargeting.
- Over-limit accessory work is reduced transactionally while every exercise identity remains locked and strength work stays protected.
- Added a powerbuilding regression that reproduces the prior overflow, verifies roster identity, session-time fit, engine pass status, and zero remaining displayed-region overflow.
- Pursuit Engine 0.64.3; service-worker cache rotated for build 792.

## M201 — Custom workout prescriptions and exercise variations (build 791)

- Restored the implicit late-block last-set intensifiers on existing custom plans, preserving explicit technique edits and off settings.
- Pending automatic rep entries now follow the active prescribed range after resume; completed sets and manual entries remain intact. Rep typing now marks the row as manually controlled.
- Added seated, single-arm, chest-supported, and cuff lateral/rear-delt variations to the app and engine catalogs, with equipment-aware setup options and previews.
- Connected existing band, side-lying, and behind-the-back raises to lateral-raise options.
- Includes the recovered M200 regional-dose and equipment fixes. Engine 0.64.2; service-worker cache rotated for build 791.

## M200 — Preserve regional dose during generation (build 790)

- Final dose cleanup now preserves achieved accumulation reserves for lats, upper back, and other public muscle regions.
- Cleanup still reduces excess accessory volume and protects strength prescriptions; new programs retain useful back work before they reach the volume display.
- Added 18 creation cases plus overflow, shortfall, immutability, and locked/adaptive cycle regression checks. Recorded follow-up issues for short sessions, minimalist targets, and locked cycle carryover.
- Engine 0.64.1; production service-worker cache rotated for build 790.

# M199 — Volume Repair Integrity (build 789)

- Auto-fix now updates engine-owned weekly prescriptions and commits the verified program to saved state.
- Existing movements absorb useful sets first; full sessions trigger accessory reallocation and redundant setup consolidation before another movement is considered.
- Volume warnings, charts, and contributors share live engine accounting and phase/experience/priority/capacity targets across all working weeks.
- Corrected fractional upper-back credit for lat-biased rows; success is checked after repair, with explicit partial/unable feedback.
- Preserved M198 creation/capacity/split guards and the newer M199 wizard fixes; rotated the PWA cache for build 789.

## M199 — Stable program builder feasibility

- Split and session-length cards no longer run full program generation while rendering, removing the UI-thread freeze/flicker in the time-selection step.
- A single unlucky generation seed can no longer turn a valid Full Body setup into a false "missing upper pull work" refusal. The requested seed is preserved when it passes; deterministic alternates are tried only after a rejection and the successful seed is saved.
- Capacity generation is now explicitly precached for installed/offline PWAs.

## M197 — Monotonic session capacity

- Longer session bands no longer become impossible just because Pursuit cannot justify filling their lower time edge.
- 60–90, 90–120, and 120+ preserve their full upper capacity while falling back to a proven shorter productive floor when needed.
- Added an end-to-end Full Body regression so a buildable 40–60 setup must remain buildable when the athlete offers more time.

## Pursuit Iron 4.0.0 — M195 (build 787 / Engine 0.64.0)

- Promotes the completed M189–M194 adaptive progression architecture to the production release line.
- Selects progression per exercise from phase, experience, rep structure, loading characteristics, equipment, and real block duration instead of a static program-wide default.
- Re-evaluates Auto progression from comparable per-lift history while preserving manual choices and preventing method thrashing.
- Carries progression context correctly through standalone programs, cycle creation, block previews, real block advancement, and Adapt Between Blocks transitions.
- Adds plain-language progression explainability, including whether Auto kept or changed a method across a block transition.
- Aligns the longitudinal simulator and weekly progression schedule with the production runtime so torture certification exercises the same rules the shipped app uses.
- Preserves all M188 production-torture, PWA lifecycle, premium UI, history, progression-safety, and coach-quality contracts.
- Physical Android installed-PWA certification remains a separate manual device check and is not claimed by CI.

## M180 — Coach Quality Oracle 2.0 (3.229.0 / build 785 / Engine 0.63.3)

- Adds a domain-gated coach-quality oracle above the existing structural audit, objective coach guardrails, and sampled blind-review framework. No blended score can hide a major defect in another quality domain.
- Makes a 19-case representative program matrix pass-only across experience levels, goals, frequencies, splits, time budgets, supersets, and equipment constraints. Any review/reject result blocks release verification.
- Fixes a real powerbuilding recovery defect found by the new oracle: generic mixed Upper/Lower plans no longer spend optional high-priority volume by stacking squat and deadlift strength work when required anchors can occupy separate lower sessions. Required strength anchors remain protected, while named strength systems retain their own exposure rules.
- Adds M180 oracle and matrix tests to the permanent production release verifier and packages the oracle as a hashed/pre-cached Next engine module.
- Physical Android installed-PWA process-kill certification remains the separate M179 device gate and is not claimed here.

## M178 — Real-Browser PWA Update Lifecycle Certification (3.228.0 / build 784 / Engine 0.63.2)

- Adds a real headless-Chrome service-worker lifecycle gate instead of relying only on source-marker tests for PWA updates.
- The gate starts the actual app under its current worker, deploys a changed worker while the app is open, verifies the new worker waits, confirms the in-app **Update ready — Restart** action is the takeover trigger, and verifies the controller-driven reload completes under the new cache.
- Verifies local training storage survives the update/reload and the old production cache is removed only after activation.
- Re-audited interrupted-workout protection: the restart control remains suppressed while a workout is active or minimized, while `wpb:live` is flushed through the existing lifecycle persistence hooks. No runtime behavior change was needed.
- Adds a permanent PWA lifecycle CI workflow so future releases run this browser-level gate in addition to the fast production integrity suite.

## M177 — Editable History Merge Integrity (3.227.0 / build 783 / Engine 0.63.2)

- Treats corrected workout history as mutable evidence during backup/device reconciliation instead of the old immutable-session assumption.
- Adds per-session `updatedAt` clocks and deletion tombstones to every history mutation while keeping hydration raw so opening the app does not manufacture edits.
- Extends the existing persistence chain to schema 13 (preserving migrations 8–12) and gives legacy history an intentionally-old conflict clock.
- Makes equal-clock legacy conflicts deterministic with store recency plus a stable final tie-breaker.
- Rebases the derived `perf` mirror from the newest retained chronological history so progression/readiness cannot keep stale corrected evidence or let an older edited workout replace a newer session.
- Adds release-gated regression coverage for both merge orders, legacy ties, delete/edit resurrection, migration preservation, and perf consistency.

## M176 — Interrupted Workout Restore Hardening (3.226.0 / build 782 / Engine 0.63.2)

- Audited the live-workout persistence path rather than rewriting it: current code already autosaves the complete session, flushes on mobile/PWA lifecycle events, restores workout/rest timers, and partially merges logged work after a program edit.
- Adds release-gated tests for exact resume, partial resume, malformed snapshot rejection, exercise-reorder merging, newly added/replaced exercises, and preservation of logged notes/sets.
- Adds source-level release guards for `visibilitychange`, `pagehide`, Page Lifecycle `freeze`, minimize-before-exit persistence, actual-training-time restoration, and paused/running rest-timer restoration.
- No workout UI or engine behavior was changed; this milestone prevents the already-correct recovery path from silently regressing.

## M175 — Longitudinal Adaptation Memory (3.225.0 / build 781 / Engine 0.63.2)

- Fixes a multi-block memory gap: an exercise diagnosed as poor fit could be avoided for the immediately following block and then become eligible again because later adaptation rebuilt from the original immutable request.
- Adds one shared carry-forward boundary for exercise avoidances. Original user bans, previously learned poor-fit replacements, and newly diagnosed poor-fit exercises now accumulate across adapted blocks.
- Applies the same behavior to standalone next-block adaptation and adaptive training-cycle advancement.
- Adaptive cycles now also pass `replaceExerciseIds` into phase-transition continuity, matching the standalone path.
- Temporary fatigue capacity reductions remain temporary; only exercise-avoidance memory is carried forward.
- Adds focused regression coverage and reruns the complete M174 production suite.

## M174 — Causal Cycle-State Hardening (3.224.0 / build 780 / Engine 0.63.1)

- Closes the remaining M170 recovery-causality gap inside `cycles.js`. Generic action labels such as `hold` and `review` no longer count as fatigue by themselves.
- Development blocks now enter recovery review only from sufficiently broad, weighted negative causes such as effort overshoot, load-too-heavy, rep-floor misses, or incomplete sessions. Loading-inventory limitations remain neutral.
- Recovery-phase exit now requires repeated positive causal evidence (`progression_success`) after the minimum recovery dose. Neutral holds/reviews neither delay recovery as fake fatigue nor count as exit confirmation.
- Adds focused regression coverage and reruns the full M173 production suite.

## M173 — Integration + Bootstrap Resilience (3.223.0 / build 779)

- Audited the M172 baseline before changing anything: history correction and cross-surface engine authority were already implemented and regression-gated, so they were preserved rather than rebuilt.
- Adds a cross-feature torture gate that runs a real generated program through bad logged evidence, History correction, re-analysis, an evidence-triggered phase transition, continuity, a newly avoided exercise, and the full target-phase audit.
- Fixes the startup health counter still comparing against build 774. The counter now uses one current-build token, so repeated failed starts accumulate correctly on build 779 instead of resetting each launch.
- Adds a PWA/mobile source-resilience gate for current-build diagnostics, dynamic viewport sizing, 16px editable inputs, contained overscroll, zoom accessibility, explicit service-worker restart, stale-script bypass, and precache path integrity.
- Pursuit Engine remains **0.63.0**; no already-verified set display, cycle overview, history editor, swap sheet, or engine-allocation behavior was redesigned.

## M172 — Actual Strength-Baseline Allocation (3.222.0 / build 778 / Engine 0.63.0)

- Realizes protected strength anchors before final hypertrophy allocation.
- Replaces generic lift-name muscle credits and 20/15-minute estimates with the selected exercise variants, actual strength sets, prescribed rest and shared session overhead.
- Locks the provisional topology for the residual pass and pins those exact strength anchors in final realization, eliminating the circular variant/projection mismatch.
- Adds integration coverage proving preview and final strength ledgers are identical.

## M171 — Shared Transactional Generation Context (3.221.0 / build 777 / Engine 0.62.7)

- Adds a per-generation catalog/schedule/eligibility context and candidate indexes.
- Memoizes structurally identical program candidates across repair loops.
- Uses one strict audit comparison order: critical → major → warning → repair objective.
- Routes generation repair, phase-transition swaps, and locked-cycle repair through the shared transaction layer.

## M170 — Engine Correctness 1–5 (3.220.0 / build 776 / Engine 0.62.6)

- Excludes avoided exercises from feasibility.
- Corrects RIR-floor handling and adds causal progression reason codes.
- Recovery ignores neutral holds/loading constraints and reacts to actual hard-effort/load evidence.
- Block adaptation uses longitudinal diagnosis.
- Phase continuity matches sessions by training identity rather than weekday.

## M169 — Swap Sheet Scrolling (3.219.0 / build 775)

- Fixes the exercise replacement list on touch devices by making the swap-results region the single vertical scroll owner.
- Removes the nested `wpb-scroll` from the ranked replacement list; that child had its own contained overscroll behavior and could consume a vertical swipe before the parent sheet moved.
- Explicitly allows native vertical panning and momentum scrolling on the results region while keeping sheet drag-to-dismiss isolated to the sheet's top grab band.
- Adds a regression gate for the single-scroller structure. Pursuit Engine 0.62.5 programming behavior is unchanged.

## M168 — Cycle Overview + Phase Semantics (3.218.0 / build 774)

- Simplifies Cycle Detail by removing the second unlabeled segmented progress bar; the overview now has one overall progress bar plus a separately labeled cycle path.
- Removes duplicate block/week counters from the stat row, leaving actionable context: training days per week and sessions logged.
- Separates block phase from training goal in the cycle path. A powerbuilding peak remains strength-goal programming internally but now displays as **Peak**, so the path reads Hypertrophy → Strength → Peak instead of Hypertrophy → Strength → Strength.
- Adds a cycle-overview regression gate so phase labeling and the simplified hierarchy cannot silently revert. Pursuit Engine 0.62.5 programming math is unchanged.

## M167 — Legacy Dose + Standalone Cycle Recovery (3.217.0 / build 773)

- Restores legacy/custom role-based working-set baselines plus saved `slotBias` and `autoBias`, fixing SekuFit being flattened to three sets across every exercise.
- Real SekuFit regression fixture now requires Lower to resolve to 5/3/4/5/5/4/5 = 31 working sets instead of 21.
- Restores “Turn into training cycle” for fixed standalone plans regardless of which engine originally created them.
- Legacy/custom conversion keeps the existing program exactly as Block 1 and uses Pursuit Next only for future blocks; the first transition activates the audited future preview, then normal Next adaptation resumes.
- Preserves M162–M166 progression, history editing, ledger-first volume, Target/LAST, generated-set canonicalization and engine-authority gates.

## M166 — Set Schedule + History Volume Integrity (3.216.0 / build 772)

- Preserves intentional custom-program week-by-week set schedules (for example 5/3/3/4) instead of flattening array-shaped authored values to the neutral fallback.
- Keeps generated Pursuit Next set counts canonicalized at the shared engine→shell boundary, so Home, Program, Plan, Preview and Workout cannot multiply a malformed persisted set shape.
- Recomputes session tonnage from actual logged working sets whenever a set ledger exists; stale cached `h.volume` can no longer override real history.
- Excludes intensifier/sub-set rows from working-set tonnage and normalizes weekly recap volume into the displayed unit before comparing weeks.
- Retains M162–M165 progression safety, 10–15 baseline behavior, editable-history integrity, distinct-day week advancement, Target/LAST layout and engine-authority gates.

# M165 · 3.215.0 · Build 771

- Fixed the generated-plan set-count boundary. Persisted/imported array-shaped counts such as `[3,3]` now normalize to one scalar `3`; ambiguous malformed values recover from the immutable Pursuit Engine snapshot when available. Because Home, Program, Plan, Preview, and Workout all read the same shell cell, the fix applies consistently instead of patching individual screens.
- Cleaned up the workout **SET / TARGET-LAST** row on phone widths. The two reference columns have more spacing, and **LAST** now shows only load × reps; RIR is no longer repeated inside the cramped reference cell when the same effort is already shown in the session suggestion/effort UI.
- Fixed cycle next-block opening-dose previews using week `0` against a 1-based prescription store. They now read week 1, so the set summary is populated from the actual opening prescription.
- Added set-shape, shared-surface, layout, and 1-based-week regression gates. Pursuit Engine **0.62.5** programming math remains unchanged; this is a shell/display-integrity release.

# M164 · 3.214.0 · Build 770

- Workout History now has a correction editor from each expanded session. You can fix date/time, duration, per-set load, reps and RIR, add/remove sets, or remove a lift. Program/day/week ownership remains locked so a typo cannot move the workout to another prescription.
- Saving a correction rebuilds the session summary and the latest-performance mirror, and history fingerprints now include performed-set/RIR evidence so readiness, progression, PR and coaching caches cannot keep using the pre-edit record.
- Fixed a real custom-program double-progression bug when rep ranges were persisted as arrays. `[10,15]` was becoming `"10,15"`, and the progression parser treated that as a single 10-rep target. A 10–15 range now holds/builds at 10 and only increases load after every prescribed set reaches 15 at the planned effort.
- Re-audited the production authority path: new generated plans are created/audited by Pursuit Engine 0.62.5; the shell reads engine-owned week cells; workout runtime realizes those exact set counts; completed-set history is sent back through the Pursuit Engine evaluator for the next-session decision. Custom plans remain user-authored and use a separate safety-compatible progression bridge.
- Engine 0.62.5 itself is unchanged in this release; M164 hardens history integrity and the shell/custom-plan bridge around it.

# M163 · 3.213.0 · Build 769

- Normalizes engine/persisted RIR arrays such as `[2,2]` to `2 RIR` instead of leaking JavaScript array formatting into Program View.
- Normalizes array-shaped rep ranges at the Pursuit Next → shell boundary (`[5,8]` → `5-8`).
- Finite blocks now advance weeks from distinct completed day/week records when modern history is available; duplicate/repeated same-day workout rows cannot falsely move the program into a later lower-volume week.
- M162 double-progression rep-floor correction remains unchanged. This release does not regenerate saved programs or intentionally reduce their set prescriptions.

# M162 · 3.212.0 · Build 768

- Fixed a progression-safety bug where a workout performed below the prescribed rep floor could make the heaviest logged set become the next session's straight-set load.
- For hard or unknown-effort misses below the rep floor, Pursuit now recalibrates from the logged performance and recommends a lower equipment-valid load that can realistically enter the programmed rep/RIR range.
- Explicitly easy short sets do not trigger an automatic load reduction; the app keeps the load and asks the lifter to execute the prescribed reps.
- The same rep-floor guard is applied to custom-program double progression.
- Fixed missing RIR values being coerced to `0 RIR` in the shell-history adapter; unreported effort now stays unreported instead of masquerading as failure.

# M161.1 · 3.211.1 · Build 767

- Fixed the nested gray input surface in All Programs search. The outer field now owns the background, border and focus ring.
- Search icon keeps its width; native search decoration is normalized.

# M161 · 3.211.0 · Build 766

- Current plan pinned above saved programs.
- Distinct phase icons and labeled phase sequence on each cycle.
- Compact bordered cards with a consistent 10px gap; block details expand on demand.
- Search by name, phase or folder; All, Cycles, Standalone, and Completed filters.
- Four saved entries initially, with Show more / Show fewer controls.
- Correct plan count: a cycle counts once, rather than once per block.

# 3.210.0 — build 765 (M160, React)

- Turn a program into a training cycle: it becomes Block 1 (history and edits kept) and the later blocks are planned for you.
- Saved plans from earlier versions can be restored to your library or removed from the list, without touching workout history.
- Programs list: a cycle shows its split, days and focus once, and each block says where it stands (Training now, Up next, Later).
- Cycles you aren't training fold away; every block of a cycle has the cycle's icon.
- The program switch button now says "Switch", and upcoming cycle blocks no longer show a button that couldn't work.

# 3.209.0 — build 764 (M159, React)

- Remove the misleading **Start next block** action from standalone program cards.
- Add **Turn into training cycle** for saved fixed-length Pursuit programs.
- Preserve the current program ID, workout history, edits, and active status as Block 1 when converting.
- Build only the future phases after the current engine phase; future preview blocks still rebuild from real workout history when advanced.
- Let the lifter choose the cycle path and whether exercises may adapt between blocks.
- Keep true cycle block advancement inside the cycle UI instead of exposing it on unrelated standalone programs.
- Carry forward the M158 restore/removal controls and resilient PWA startup/update behavior.

# 3.208.0 — build 763 (M158 + fixes, React)

- Programs you wrote yourself (Build your own) now come across when you import a backup or update, keep your own sets and reps, and can be trained — they were being discarded.
- Fixed a crash when opening a workout: Quick workouts, programs with a typed single rep target like "8", and custom programs.
- Weight suggestions now match this week's reps — heavier as the reps drop — and always land on weights your equipment can make.
- New "No squat rack" option: a home barbell without a rack skips squats, bench and presses taken from a rack.
- Self-test runs in seconds; the full engine check is still in Settings.
- No sideways scrolling on Home or in the program builder, and backups download as pursuit-iron-backup-<date>.json.
- Runs on React, the same as the original app.

# 3.189.0 — build 744 (M120)

- Merged programmer equipment, progression and upgrade fixes with M117–M119.
- Review older-plan updates before saving; originals and workout history retained.
- Responsive builder availability checks and clearer form-video search links.

# 3.188.0 — build 743 (M119)

- Search and filter workout history, with 50 workouts loaded at a time.
- Read starting prescription targets first, with detailed evidence available on demand.
- Correct mixed kg/lb grouped volumes and preserve earlier program names.

# 3.187.0 — build 742 (M118)

- Review changed programs and choose cycle edit scope before committing.
- Keep drafts when leaving; undo the last save with protection against later edits.
- Preview actual backup additions, updates and removals using the restore calculation.
- Block restores that conflict with local program drafts; restore settings and drafts when undoing an import.
- Engine remains 0.62.3.

# 3.186.0 — build 741 (M117)

- Keep workout navigation/rest controls in layout instead of overlaying set rows.
- Adjust workout height to the visible viewport and reveal the edited field when the keyboard resizes it.
- Show the current exercise name in the navigator; wrap rest controls on narrow phones.
- Add a direct next-workout preview from active cycle details, clearer planned-block labels and grouped block-completion actions.
- Engine 0.62.3 fixes short final blocks being held to an open-ended four-week review window, including existing saved cycles. Minimum-workout and recovery checks remain in force.

# 3.185.0 — build 740 (M116)

- Fix the undefined cycleChoices error when opening cycle creation.
- Share the engine template catalog between cycle picker, summary and generation.
- Match split recommendations and setup estimates to the selected cycle goal.
- Show cycle duration and adaptation mode in the setup summary; preserve answers and show persistent feedback after a rejected build.
- Reject unknown cycle templates before generating any blocks or allocating IDs.

# 3.184.0 — build 739 (M115)

- Commit typed input on every native input event in the portable runtime.
- Preserve uncompleted workout edits, selected exercise, focus and paused state across reloads.
- Improve narrow workout fields and keyboard stepping; stop held steps when the page loses focus.
- Preview and validate backups before explicit restore, including schema and malformed-entry checks.
- Explain program edit scope and planned changes between cycle blocks; advise on redundant swaps.
- Honor equipment increment overrides consistently, retain kg inventory labels and bound loading calculations.
- Repair service-worker cache misses and include runtime vendor modules in release integrity checks.

# 3.183.0 — build 738

- Restored v661 workout Weight/Reps state behavior exactly; removed the later manual input lock/precedence wrapper that could resurrect an older stepper value.
- Kept compact hold-repeat steppers with independent 44×44 touch targets.

# 3.112.1 — build 664

- Hotfix: fixed M43 startup black screen caused by an undefined icon reference.
- Added startup module-evaluation validation and a visible pre-mount error fallback.

# Pursuit Iron — Changelog

324 changes across 181 releases. Newest first.

_Generated from the app's own changelog data — do not edit by hand._

## v3.110.0 — build 661

- **Named programs prescribe their own reps** — Main-Lift Waves, Texas Method and Ramping 5×5 now show the reps the programme actually calls for rather than a generic range — 5's PRO means five reps on every main set. Accessory lifts in those programs were showing one rep range while prescribing another; both now agree. Found by the built-in self-test.

## v3.110.0 — build 661

- **Fixed: named programs showed the wrong reps on their main lifts** — Programs built on a set percentage plan — Main-Lift Waves, GZCLP, Texas Method and the rest — were showing a general rep range on their main lifts instead of the reps the plan actually calls for. Main-Lift Waves runs five reps a set across its waves, for example, and the app was asking for three to six. The plan's own prescription is now what you see.
- **Fixed: a cable fly could be treated as a main lift** — On one Main-Lift Waves day the second main lift had been swapped out, but the program still treated whatever took its place — a rear delt fly — as a heavy main lift. It is now treated as the accessory it is.

## v3.109.0 — build 660

- **More work in the same hour** — Sixty-minute sessions now pair exercises that use opposing muscles — a push with a pull, say — so you rest less between them. A session fits about fifteen percent more work and still finishes a little sooner, and far fewer muscles end up with no work at all. If your gym is too busy to hold two stations, or you would rather not superset, you can turn it off when you make a program.

## v3.108.0 — build 659

- **Your gym and your experience are remembered** — Equipment you switch on or off while making a program now stays that way for the next one — it belongs to your gym, so it only changes when you change gym. And training experience is asked once instead of every time; you can change it in Settings.
- **Your barbell limit is respected everywhere** — If you cap how many barbell movements a session can have, that limit now holds all the way through. It used to be applied when the session was first laid out and then ignored by later adjustments, so you could still end up with two barbell lifts — a squat and a row, say — on the same day.

## v3.108.0 — build 657


## v3.108.0 — build 656

- **Torso / Limbs trains your delts** — On the Torso/Limbs split, side and rear delt work now lands on the limbs day beside your arms — which is the reason that split exists. It was moving your arms there and leaving the delts behind on an already-full torso day, so rear delts finished a week well under what they need. They now clear it.

## v3.107.0 — build 655

- **Upper work lands on upper days first** — Shoulder or arm work is no longer put on a leg day just because that day had spare time — it goes to an upper day when one has room, and still lands on a leg day when nothing else can take it. Programs that deliberately mix, like full body, are unaffected.

## v3.106.0 — build 654

- **A three-day upper/lower that actually balances** — Upper and lower now alternate without resetting each week — sessions run upper, lower, upper, then lower, upper, lower. Over a fortnight each gets three sessions instead of an uneven two and one, which is how this split is meant to run and why three days never fit it before.

## v3.105.0 — build 653

- **Every training day matches its own name** — A day called “Upper · Horizontal” now opens on a flat press rather than an incline, a hinge day gets a hinge, a pull day gets its pull. Each day states the movement it is built around and the builder honours it — before this, only four of seventy-two days said what they wanted and the rest were left to whichever variation happened to score best.
- **Exercises you add now survive a shuffle** — An exercise you added yourself used to disappear the next time you shuffled the program, updated the engine or reset it. It is now carried across, kept on the same kind of day, and the time it adds is shown separately so your plan is never marked over target for work you chose to add.
- **Less filler, same training** — Roughly nine thousand sets of low-value volume removed across the program library, with fewer sessions running past their stated length.

## v3.103.0 — build 651

- **Updating or dismissing the engine notice now sticks** — Choosing to update a program, or dismissing the offer, was only being remembered until you navigated away — and the update button was also warning you that manual changes would be replaced on programs you had never edited, because the weekly schedule every program is born with was being counted as an edit. Both are fixed, and dismissing now stays dismissed until a genuinely newer engine has something new to say.
- **Start a program again from week 1** — Two ways, both in the shuffle sheet: keep the exercises you have now, or go back to the ones it was first built with. Either way it starts again at week 1 and every workout you have logged stays exactly where it is.
- **Volume targets now match your training age** — Minimum and maximum useful volumes are no longer the same for a first-year lifter and a ten-year lifter. Beginners grow on less, advanced lifters need more, and the app now scales both ends accordingly.
- **Focusing forearms or traps gives them real work** — Choosing either as a focus now sets an actual volume floor for it instead of leaving it to whatever your other lifts happen to provide.

## v3.102.0 — build 650

- **Reducing a muscle now actually reduces it** — Setting a muscle to reduce had no effect on how many exercises it got — across eight test programs the count was identical every time, and the volume sometimes went up. A reduced muscle no longer competes for its own exercises; it keeps what it earns from your compound lifts, plus a maintenance slot if it would otherwise drop too low. The freed slots go to the muscles you focused.

## v3.101.0 — build 649

- **Deleting a training cycle no longer leaves its blocks orphaned** — Removing a cycle while keeping its blocks left every block still pointing at the cycle that was gone. The library then drew them as a nameless “Training cycle” card, tapping it dropped you on the all-cycles list, and the second block was hidden inside the group entirely. Blocks you keep are now kept as ordinary programs, and any library already in this state repairs itself the next time the app opens.

## v3.100.0 — build 648

- **Your Strength Score and the level beside it agree** — The level printed next to the score is now the level of that score, and the trend line ends on the same number shown above it. Both count the same lifts, including the ones scored against a barbell equivalent.
- **What's new is a headline again** — Home shows the full description for the release you just installed and a one-line title for each older change. Nothing is dropped and the full log is still one tap away.

## v3.99.0 — build 647

- **Better full-body pattern rotation** — Rotation compares a broader layout while protecting muscle coverage, focus points, reduced muscles and session limits.
- **Stronger program checks** — Program and cycle selection check session ceilings across every training week. Invalid candidates cannot receive a clean score.

## v3.98.0 — build 646

- **Safer startup and saving** — Slow or unreadable storage no longer starts an empty account over your data. Retry or restore a backup from a dedicated recovery screen. Pending saves keep their order.
- **Clearer program and cycle setup** — Generation shows a working state, prevents duplicate submissions, and keeps your answers if it fails. Focus limits and selected options are clearer.
- **Sheets behave consistently** — Keyboard focus stays inside the open sheet and returns to the right control when nested pickers close.

## v3.97.0 — build 645

- **Focus and reduced muscles guide generation** — Focus inputs are consistent across program and cycle generation. Candidate selection respects your priorities and maintenance targets.
- **Cycles compare complete layouts** — New cycles compare several coherent candidates across all blocks before choosing a layout.
- **Reduced muscles keep less work** — A final volume pass trims eligible work while protecting other muscles and fixed prescriptions.

## v3.96.0 — build 644

- **Movement previews rebuilt** — Clearer figures and equipment, corrected pull-ups and core movements, machine-specific loading, and supported bench positions. Pause, slow down, or drag through any movement.
- **More resilient progression** — Cycle templates now keep their own progression when created from a percentage-based split. Malformed imported phase windows no longer produce invalid progression values.

## v3.95.0 — build 643

- **New cycles start in their intended phase** — Starting a cycle from a peak block no longer carries that peak into a fresh hypertrophy wave. Each block uses its template's own progression window.
- **Next-cycle loads and explanations agree** — Training-max changes use this program's AMRAP results. The preview now explains the same AMRAP set used to calculate the new load, including when summary reps differ.
- **Exercise motion follows your preferences** — Figures react immediately when reduced motion changes, stop while the app is hidden, and restart cleanly when you return or choose another exercise.

## v3.94.0 — build 642

- **Focusing a small muscle now changes your program** — The focus picker promises that each level adds an exercise for that muscle, and for the smaller ones it usually could not keep that promise — measured, focusing forearms twice left them with no work at all in 58% of programs, because adding an exercise would break the session's stated exercise count and there was no existing movement to add a set to. A focus point that cannot be filled any other way now trades: either a lift is exchanged for a version of the same movement that also works the muscle you asked for, or a slot comes from a muscle that has sets to spare. Your session keeps its length and its number of exercises, and the program is built both ways and compared, so a trade is only kept when nothing else came off worse. Neck is the exception — nothing else in the exercise library trains it, so the picker now says so instead of implying otherwise.

## v3.93.0 — build 641

- **A cycle's blocks show what makes them different** — Every block of a training cycle listed the same split, the same day count and the same emphasis — three identical pairs of lines for a three-block cycle, which is what they have in common by design, since a cycle is one program periodised. Those details now sit once on the cycle's own header and each block shows its name, so the list is about half the height and the block you're actually on is the one that stands out. A block you've edited to differ from its siblings still shows its own details.
- **The Create and Quick buttons sit at the bottom again** — On phones with a home indicator, the bar reserved space for it and so did the tab bar underneath — the same gap counted twice, which pushed the buttons up the screen and left a dead strip below them covering whatever card was behind. The space is now reserved once, by whichever is actually last on screen.

## v3.92.0 — build 640

- **Minimizing a workout no longer restarts it** — If you swapped an exercise and chose “Update my program”, the workout you were in the middle of could come back empty — most visibly after closing the app and reopening it. Two things were wrong. The session was still describing the day the way it looked before the swap, so when you came back the app compared the two, decided the saved workout belonged to a different day, and threw it away; and it was making that check on every single redraw, so the workout was often already gone before you tapped resume. Both are fixed, and a changed exercise no longer costs you the session: the day is rebuilt from your program and every set you logged against a lift that did not change comes back with it. The lift you swapped out starts fresh, as it does when you swap mid-workout.
- **Home shows you what changed, not everything it knows** — The strength score, muscle recovery and the weekly recap used to sit on Home every single day once you had trained even once — so the program you opened the app to start was below all three, and most days none of them had anything new to tell you. They now appear when they have something to say: recovery when rest is actually advised, the strength score when you set a PR, the recap when you have trained that week, and at most two at a time. Nothing is gone — all of it is still on Progress whenever you want it, and warnings you should act on, like a deload being due, are never rationed.

## v3.91.0 — build 639

- **Your second curl of the week trains the brachialis** — Every program picked the same incline curl for every biceps slot — one exercise took 53 of 53 biceps slots across the whole config space, because selection ranks by a single priority number and never looks at what the first pick already trained. When a week holds two or more biceps slots, the second now goes to a neutral-grip movement, which loads the brachialis and the forearm rather than repeating the same supinated curl. Programs with more than one curl went from none to all 21 carrying a neutral grip, and every program keeps its best supinated curl as the first pick.
- **The volume check stopped advising work you never asked for** — Forearms, traps and neck are accessory work the app does not program for you, so it has never offered to add sets to them. That silence turned out to rest on those muscles sitting at exactly zero: the neutral-grip curl above puts a fraction of a set into your forearms, and the volume check immediately started telling 126 programs to add forearm work. It now decides by whether the muscle is one the app programs, or one you have put a movement in yourself — not by whether the number happens to be zero. If you do train a muscle and are missing a direction of it, you still get told which movement closes the gap, which is the advice this rule was always meant to protect.

## v3.90.0 — build 638

- **Deadlifts now progress at the right rate on 5/3/1 and GZCLP** — The deadlift was being treated as an upper-body lift, so it went up in the small jump — 5 lb a cycle instead of 10, half what those programs prescribe. Squats got the full jump, deadlifts did not, on every named program that uses both. It was invisible because the deadlift is filed under lower back rather than legs. Deadlifts now advance like squats, in pounds and kilos. Nothing else about your loads changes.

## v3.89.0 — build 637

- **Long sessions fill up to the length you asked for** — A two-hour session used to stop early — days came in around 80 minutes, because a day was capped at nine exercises no matter how much time was left. Long sessions can now hold eleven, and a day short of its target is filled with the muscles furthest behind rather than more work for the one that already has the most. Days finishing under their target dropped by 41%, and weekly minimums missed dropped too. Nothing is added past a muscle’s ceiling, past the length you chose, or past what a named program prescribes; shorter session lengths are unchanged.

## v3.88.0 — build 636

- **Heavy days are no longer scheduled back to back** — A deadlift day followed by a squat day was only ever judged on which muscles the two shared — and a squat and a deadlift do not share many, so the app saw nothing wrong with putting them on consecutive mornings. Loading your spine hard twice in a row is a cost the muscle comparison could not see. It now counts, including between the last training day of your week and the first day of the next, which has no rest between it either. Across every program the app can build, back-to-back heavy days fell by about half. The cost is a small rise in how often two days share some muscle work, which is the lesser of the two problems.
- **The suggested week and your actual week are the same week** — The suggested-schedule calendar could show a completely different arrangement from the one your program was built with — same program, two different weeks, depending on which screen you looked at. They now come from one place.

## v3.87.0 — build 635

- **Side and rear delts stop being blocked by your pressing** — Every press gives your front delts work, and that counted toward “shoulders” as a whole. When the total crossed a limit, the app refused to add any more shoulder work — including the side and rear delt work you were actually short of, which pressing does nothing for. Two parts of the app were using different shoulder limits; they now use the same one. Rear delt shortfalls fell across the board, and no existing program changes.

## v3.86.0 — build 634

- **The app no longer asks you to train muscles it never planned for** — Your program is built to cover thirteen muscle groups. Six others — traps, lower back, adductors, abductors, forearms, neck — have always been accessory work you add if you want it, and the builder has never targeted them. The volume check did not know that, so it would tell you to “add three sets to your traps” in a plan that was never going to include direct trap work, and the one-click fix would then pad some other exercise to satisfy it. Across a sweep of generated programs, roughly one in five suggestions was of that kind. Those are gone. Warnings that you are doing too MUCH of something are unchanged and still apply to every muscle, and anything you add yourself still shows up in your volume charts.

## v3.85.5 — build 633

- **The share code is checked by using it** — Sharing a program hands over a short code that rebuilds the plan on someone else’s phone. There is now a check that opens the share sheet, takes the code it shows, decodes it and rebuilds the program — then compares it lift by lift against the original. It also confirms that a program the code format genuinely cannot carry says so instead of handing out a code that would rebuild the wrong plan.

## v3.85.4 — build 632

- **Your weekly schedule is checked end to end** — Which weekday each session lands on is not decoration — the app uses it to work out how much rest a muscle got, and three separate bugs in the past came from a screen and the engine disagreeing about it. The schedule sheet is now its own component with a check that taps through it the way you would, saves, and then confirms the engine reads back exactly the week the sheet wrote. Nothing changed on screen.

## v3.85.3 — build 631

- **Program settings, tested the way you use them** — The program settings sheet is now its own component with a check that drives it exactly as a thumb would: open the program, open settings, pick an assistance style, save, commit — then read back what was stored and confirm it is the program the builder makes for that choice. Nothing changed on screen. This is the first slice out of the app’s largest screen; the rest follow the same way, one piece and one check at a time.

## v3.85.2 — build 630

- **One type scale** — Text across the app was set at fourteen different sizes between 8½ and 18 points, in half-point steps — the fingerprint of hundreds of small decisions made one screen at a time. It is now eight sizes on one scale, and a check refuses a new one. Half-point sizes rounded up, never down, so nothing got smaller; the change is subtle on any one screen and visible as consistency across all of them.

## v3.85.1 — build 629

- **Section labels look the same everywhere** — The small uppercase label above a section — “Assistance work”, “This day hit”, “Add a gym” — was drawn 113 slightly different ways across the app: seven font sizes, five letter-spacings. It is now one definition, used in 79 places, and a check refuses a hand-drawn one. Nothing else moved; this is the first of the visual clean-ups.

## v3.85.0 — build 628

- **A 40-minute plan is now a 40-minute plan** — The program builder priced a session by its sets, warm-ups and rests, and the plan screen priced it the same way plus the minutes every session actually has between exercises — walking to the next station, loading the bar. So the builder would declare a day fitted at 40 minutes and the plan would show you 47. Across every program the app can build, the builder believed 96 of 2,880 days ran over their limit; the number on screen ran over on 858 of them. From engine 15 there is one price for a day, and it is the one the builder holds the session to. The cost is stated plainly: with the minutes between exercises counted, fewer lifts fit, and about 4% fewer sets are programmed at a given session length — the sets you were never going to have time for. If you want them back, pick the next session length up; that is what the setting is for. Existing programs are unchanged.
- **What a 20-minute strength session actually costs** — Two heavy compounds with proper rest take about 30 minutes, not 20 — and the honest pricing now says so on the plan instead of hiding it. That is the one place the app still cannot fit what was asked, and it now shows it rather than pretending.

## v3.84.0 — build 627

- **Jack Shit, Triumvirate and 5×10 Supplemental now build the program they name** — Those three assistance styles were saved with your program, printed on screen and carried in every share code — and the program builder never read them. The gallery's “Main-Lift Waves · 5×10” preset came out with a full slate of accessories, and “Jack Shit — main lift only, nothing else” produced seven lifts a day; only re-picking the style in settings changed anything, and that path rebuilt each day with an old version of the selector and skipped every repair step. From engine 14 the style shapes the program at creation: Jack Shit is the four main lifts and nothing else, Triumvirate and 5×10 are the main lift plus two accessories, and switching styles in settings now rebuilds the whole program the same way. Programs you already have are unchanged unless you switch.
- **Triumvirate and Jack Shit programs can be shared by code** — A share code could carry the 5×10 style but not the other two, so those programs silently fell back to the long text share. All three now travel in the code, and a shared Jack Shit arrives as Jack Shit.

## v3.83.3 — build 626

- **Every engine rule now has a name** — A program keeps replaying under the engine version that built it — that is what makes a seed, a share code and a reroll reproduce the layout you saw. The cost was that every rule an engine added lived in the code as a bare version number, thirty-nine of them, and the only way to learn what engine 10 changed was to search for the number. Every one of those rules is now listed once, by name, next to what it does and where it is applied — and a check refuses any future rule written the old way. Programs are unchanged to the byte.
- **Exercise selection takes its settings by name** — The routine that picks a day's exercises took eighteen unnamed arguments in a fixed order. One of the two places that called it passed only nine and silently accepted defaults for the rest — including which engine version to use. Both now pass every setting by name, so an omission is visible where it happens. Nothing changed in what gets picked; the omission itself is now recorded as a decision rather than an accident.

## v3.83.2 — build 625

- **The volume repair engine is now eight named steps** — After the app picks your exercises, a second engine repairs the week: it fills each session toward the length you asked for, holds every muscle at its minimum, guarantees no movement pattern is left out, trims what is over its ceiling, and last of all trims high-frequency work the plan cannot recover from. That engine was one function of sixteen hundred lines. It is now eight named steps in a declared order, sharing one set of helpers, with the engine-version rules visible in the list rather than buried between passes. Every program the app can build was generated on both versions and is identical to the byte; the sweep that guards every generation change moved by zero. Reordering two steps changed 1,019 of them, which is how you know the order is the engine.

## v3.83.1 — build 624

- **Nothing in your programs changed — the builder underneath them did** — The program builder used to be a long hand-written sequence of steps with the rules for older program versions threaded between them. It is now one readable list of named stages, each with the condition it runs under. Every program the app can build was generated on both versions — 12,420 of them, across every engine version the app has ever shipped — and they are identical to the byte. The point is what comes next: the engine can now be improved one stage at a time with the whole sequence in view, rather than by adding one more patch to a function nobody could read end to end.
- **Every build of the app is now reproducible** — The app is assembled from a handful of libraries, and one of them was allowed to drift between versions. A fresh build of the same source came out twelve bytes different from what you were running — harmless here, but a build that cannot be reproduced cannot be trusted to prove a fix. Every library is now pinned to the exact version that shipped, and a clean install rebuilds the current app byte for byte.

## v3.83.0 — build 623

- **Programs named after a published routine now run that routine** — If you picked 5/3/1, the deadlift day did not contain a deadlift — it contained a Romanian deadlift 35 times out of 40, because the app chose the lift by which muscles the day was meant to work rather than by name. GZCLP's deadlift day had the same problem. The mechanism to pin a program's main lifts had been in the app all along, but it only ran if you arrived through the gallery; picking the same program from the wizard skipped it, and you got something wearing the name. The main lifts are now fixed whichever way you reach them, and the percentage scheme the program is named for comes with them.
- **The Texas Method's light day is light again** — Texas prescribes 5×5 squats on the volume day, a deliberately easy 2×5 on the recovery day, and one heavy set on the intensity day. The app was prescribing four working sets on all three, including the one designed for recovery — so you squatted double what the program asked on the day meant to let you recover, and the app then noticed the fatigue and suggested you cut sets. The program's own set counts now win for the lifts it prescribes. Accessory work is still ours to program.
- **A program is only offered at session lengths it can be run in** — A 5/3/1 main lift is three work sets plus warm-ups — twenty to twenty-five minutes before any assistance. The app was offering 5/3/1 at “up to 20 minutes” and GZCLP too, where the two main lifts filled every slot in the week and left no room for a row, a curl or anything else: some weeks trained no back at all. These programs now appear only in the brackets they actually fit, the same way a four-day program is only offered at four days. The same applies at the top end — two hours of a beginner template is not that template either.
- **GZCL programs are built from their own tiers** — A GZCLP day is a heavy main lift, a moderate second lift, and one or two high-rep accessories — and the accessories are where the program deliberately puts your back work, because the four barbell lifts contain no pulling at all. The app was sizing these days from your session length instead and filling the spare slots with whatever coverage wanted, which is how a GZCLP week ended up with curls and calf raises and no rows. The tiers now decide how many movements a day has and what the accessories are for; your equipment and your actual weak points still decide which movements.
- **You are never told to cut a muscle already at its minimum** — If a muscle was trained just enough and you were recovering poorly, the advice was to cut a set — which takes it below the point where training earns anything at all. Below the minimum, fatigue is a spacing problem, not a volume one, and the advice now says so instead.

## v3.82.0 — build 622

- **A new program no longer opens with days already ticked off** — If you built a new program on a split you had trained before, some of its days showed up already completed before you had done anything. The app names a program's days after the split — an upper/lower program always calls them Upper A, Lower A and so on — which is deliberate, because it is what lets a program keep your history when you regenerate it. But the completed markers were matching those names against your whole training history rather than against this program's, so last month's sessions were ticking off this morning's plan. Completion is now scoped to the program that earned it. Regenerating a program still keeps its ticks, and older sessions logged before the app tracked this are still counted rather than thrown away.

## v3.81.0 — build 621

- **Your training days are arranged so back-to-back sessions clash less** — The app decides which day of the week each session lands on, and it was solving the wrong problem — spreading overlap evenly around a ring of days as though every session sat the same distance from the next. Your week has rest days in it. On a five-day plan only three of the five pairs are genuinely back-to-back, and which two sessions landed side by side was left to chance. It now minimises the worst clash specifically: the one morning you would train a muscle that is still sore. Across every program the app can build, the volume shared by consecutive days fell by nearly half at the median, and the worst case on a split routine went from 28 shared sets to 15. No exercise selection changed and no muscle gets more or less work — only the order of your week. Programs you have already saved keep the schedule they were built with.

## v3.80.0 — build 620

- **Every week now tells you what it is for** — The week chips in your plan told you what a week costs — sets and minutes — and nothing about what it is doing. Now each one comes with a line: what it is (introduction, accumulation, intensification, peak, deload), the effort you should be leaving in the tank, the rep bracket on your main lift, how many sets have been added since week 1, and when the last-set techniques start appearing. Every word is read from the same prescription the session screen renders, so it cannot drift into describing a plan you are not actually running.

## v3.79.0 — build 619

- **Your shoulders finally get trained** — About one program in ten trained your side delts not at all — and nothing in the app could see it, because side delts are part of the shoulder rather than a muscle of their own. That is now about one in a thousand. The cause was that the app decided whether an exercise fitted your session using a cost that left out setup, walking to the machine and loading the bar, while the time it SHOWED you included all three. It packed sessions against a budget it was not really keeping, and the first thing squeezed out was the small isolation work at the end.
- **Three engine improvements, switched on together** — The rule that settles competing needs, the fix for de-emphasising a muscle, and honest session pricing have all been written and measured over the last several releases. They are now live. Across every program the app can build, muscles trained not at all dropped by 7,630 muscle-weeks and no session got longer. Some of those muscles now sit below the ideal amount rather than at zero — that is the trade, and a little of something beats none of it.
- **No program trains zero back** — A handful of short two-day plans came out with no pulling work at all — no rows, no pulldowns, nothing for your back or biceps in the entire week. The step that guarantees every movement pattern is covered was refusing to act, because the only slot it could trade was a lateral raise and a separate rule protected shoulder work above all else. A week that trains no back is a bigger problem than a week light on side delts, so the pattern check now wins that tie — but only as a last resort, after every trade that costs nothing has been tried. Across every program the app can build this moved 497 muscle-weeks off zero.
- **Where a 20-minute plan still cannot cover everything** — In a handful of two-day, 20-minute plans there are four exercise slots for the entire week, and no arrangement of four slots trains everything. The app no longer squeezes in a fifth by pretending the session is shorter than it is; instead it tells you plainly that moving to a 20–40 minute session closes most of the gaps, and how many.

## v3.78.0 — build 618

- **Focusing a muscle was making that muscle worse** — Picking a muscle to focus on could take it from 28 sets a week down to 18, and clicking it a second time was more likely to hurt than to help. The cause: focus made a muscle win more of the contests for a slot, and the slots it won were isolation work — so your overhead presses got traded for lateral raises and you ended up with less shoulder work than if you had never focused at all. Focus now asks for what it actually means: one more slot for that muscle, added on top, which can never cost it volume at the point it is granted. It still loses to any muscle that is not yet trained enough, because a preference should not buy away adequate training. Written and measured, switched on in a later release.

## v3.77.0 — build 617

- **The app and its own planner now agree on what “too much” means** — The program builder deliberately stops trimming once a muscle is within half a set of its recovery ceiling — sets come one at a time, so cutting a whole set to shave four tenths off lands further from the mark than leaving it. But the screens that judge your week had no such tolerance, so they called the builder’s own output over the limit. That is what was driving the set-cutting. Both now read one number. Measured across 756 programs: the largest overshoot anywhere is four tenths of a set, and none of them is reported as over the limit any more.
- **The volume wave shows your deload, and shows the right weeks** — The chart in your plan’s Blueprint was drawn one week behind the week chips beside it — same numbers, shifted along — and it left the deload out entirely, so the one card meant to show you the shape of your block could not show the trough at the end of it. The deload is now a bar of its own, marked, and every column matches the week it is labelled with.

## v3.76.0 — build 616

- **Your program is no longer trimmed for following it** — If you did exactly what your program prescribed, the app will not turn around and cut sets from it. The volume advice reads what you logged, and it had no way to tell “you trained more than we asked” from “you did precisely what we asked.” So a week that came out four tenths of a set above a recovery ceiling — a ceiling the app’s own plan had set — was answered by removing two sets, every session, forever. Now the plan wins for its own volume, and when a plan really is above its ceiling the app says so and points at the program instead of quietly shaving it. Train MORE than the plan asked and the adjustment still applies, exactly as before — that is real extra load and the app should react to it.

## v3.75.0 — build 615

- **The rule that settles competing needs is written** — When two parts of your program both need work and there is only room for one, the app now has a single rule for who wins: a muscle getting no work at all beats a muscle that is merely under-trained, then the bigger shortfall, then the cheaper option. It is written and measured but not yet switched on, so your programs are unchanged in this release. Measured on all 24,840 configurations, it takes programs that train your side delts not at all down by 14%.
- **A muscle trained not at all was invisible to our own testing** — The measurement that guards every program change counted muscles that were under-trained, but not muscles that got no work whatsoever \u2014 for every muscle except the side delts. So the worst thing the program builder can do was the one thing nothing watched, and a fix that took a muscle from nothing to something registered as making things worse. It is now counted, and the number is large: roughly half of all inadequately-trained muscle-weeks are muscles trained not at all. Nothing about your programs changes here \u2014 this is the instrument, and several improvements already written have been waiting on it.

## v3.74.0 — build 614

- **The program allocator is now a single queue** — The second and last of the two gap-filling steps has been converted. The step that guarantees your side and rear shoulder work no longer decides and acts on its own — it states what it needs alongside every other request, and one decision point grants them. Programs are identical to before, verified across all 24,840 configurations the app can generate. Now that both steps share one queue, the next release can change how competing needs are settled by changing a single rule, so a muscle left at zero stops losing to a top-up that merely ran earlier.

## v3.73.0 — build 613

- **First half of the new program allocator** — The step that fills gaps in your program used to decide and act in one motion, which is why fixing one thing kept breaking another. It now states what it needs and a single decision point grants it. This is the first of two such steps to be converted, and it produces byte-for-byte the same programs as before — verified across every configuration the app can generate. The change is the groundwork that lets the next release resolve conflicts between competing needs properly.

## v3.72.0 — build 612

- **Share codes are ready for the new program engine** — A program share code carries the engine version that built it, so whoever opens it rebuilds exactly the program you saw. That field was one bit too small for the engine versions now in development — a code made by a newer engine would have quietly rebuilt as something else on your friend’s phone, with no error. The field is now wider, every code already in circulation still opens exactly as before, and the check that was supposed to catch this has been fixed too: it only verified that a version was present, not that it was the right one.

## v3.71.0 — build 611

- **Groundwork, and a dead end worth recording** — Work continues on rebuilding how the app decides what goes into your program. This release adds no changes to the programs themselves. A promising fix for the muscle-reduce problem was built and measured, produced the best result so far on that specific issue, and made two other measures noticeably worse — so it was held rather than shipped. That is now the fifth such attempt, and together they confirm the larger rebuild is necessary rather than optional.

## v3.70.0 — build 610

- **The muscle focus and reduce options are now fully measured** — Two of the program creation questions — which muscles to focus on and which to reduce — were never covered by the app’s internal quality measurements, so problems with them could not be detected. They are now part of every measurement run, which tripled its size. That immediately confirmed a real issue being worked on: reducing a muscle can currently remove it from your program entirely instead of just training it less. A corrected version is built and held back until it can ship without side effects.

## v3.69.0 — build 609

- **Every build step now reports what it wanted** — The last of the program-building steps was connected to the new request tracking, completing the groundwork. Programs are unchanged. The full picture shows where the app is genuinely short of room: forty-minute sessions run completely full and still fall short of the number of exercises they promise, while two-hour sessions use only about three quarters of the time you set aside. Both are now measured rather than guessed at.

## v3.68.0 — build 608

- **Measuring what your program wanted but could not fit** — Building a program involves several steps that each try to add work, and most of what they ask for does not fit in the time available — it was simply dropped, unrecorded. The app now tracks every request and whether it was granted. Programs are unchanged. The first measurements were surprising: forty-minute sessions are the most contested of any length, granting only one request in five, and one step is turned down 86% of the time yet still occasionally outbids more important work purely because it runs earlier.

## v3.67.0 — build 607

- **Groundwork for a smarter program builder** — The part of the app that fills gaps in your program is being rebuilt so that every step states what it needs and one place decides what actually fits, instead of several steps each changing the program in turn and undoing one another. This release lays the first piece of that groundwork and changes nothing about the programs it produces — verified against every measurement the app has. It already revealed something useful: the step that tops up under-trained muscles asks for roughly fourteen times more than it ever gets, and nothing was recording those refusals.

## v3.66.0 — build 606

- **Naming the build steps correctly** — The build trace added last release grouped several steps under the wrong name, which made one of them look responsible for far more of your program than it actually is. The steps are now labelled accurately. Nothing about how programs are built has changed — but what the trace reports about it is now true, which matters for everything built on top of it.

## v3.65.0 — build 605

- **The build trace now goes one level deeper** — Filling out your program is not one step but several — topping up neglected muscles, covering missing movements, capping anything excessive — and the trace added last release could only see the group as a whole. It now shows each step separately. That immediately explained a problem that had resisted three earlier attempts: on very short sessions, one step tops up shoulder work using the last available slot, and a smaller muscle ends the week with none.

## v3.64.0 — build 604

- **Seeing exactly how a program gets built** — Your program is assembled by a chain of steps — pick the exercises, fill gaps in muscle coverage, merge duplicates, cap anything excessive, trim to fit your session length. Until now there was no way to see what each step did, only the finished result, which made some problems very hard to track down. There is now a full trace of every step: what it added, removed or swapped, how it changed each day’s length, and which muscles gained or lost volume. It found a real issue on its first use.

## v3.63.0 — build 603

- **Checking the checks** — Under the hood, this app is held together by a large set of automated checks that run on every build. This release adds one that audits the others — because a check that has quietly stopped measuring anything does not go quiet, it goes green. It immediately found that programs for the “brand new to lifting” experience level had never been measured by any of them, so that entire group of programs was going unverified. It is now covered.

## v3.62.0 — build 602

- **A twenty-minute session is now twenty minutes** — It never was. Every short session the app built ran about ten minutes long — a 20-minute plan that actually took thirty — and the app had no way of noticing, because the checks that watch session length skipped short sessions entirely. Two exercises at three hard sets simply does not fit twenty minutes, so short sessions now prescribe two working sets instead of three. Fewer sets, but the session is the length you asked for, and the time saved often makes room for a third movement. Every session length in the app now finishes inside the band you picked.
- **The exercise count shown when you pick a session length is now true** — Picking a session length showed you roughly how many exercises to expect, and for the shortest option that number was wrong — it advertised about five, more than it promised for a forty-minute session, and delivered two. The programs were always right; only the label was wrong. It now shows what you will actually get.

## v3.61.0 — build 601

- **Every week now trains your back** — About one program in ten contained no pulling work at all — no rows, no pulldowns, nothing for your back — for the whole week. Measured across 6,300 generated programs, 599 of them had it, and it was always the same pattern missing. It happened even with a full gym available: the app allocated slots muscle by muscle, and because back work is split across three muscles that each rank below chest or quads on their own, a short session could fill up before any of them earned a place. Two things now prevent it. When a movement pattern is missing and there is room, the app adds one. When there is no room, trimming a session to fit the clock is no longer allowed to take the last of it — the same protection your side and rear delts already had. Your main lift is never affected. Six configurations remain where four total exercises genuinely cannot cover everything; for those the app tells you what would fix it, as it already did.
- **Short sessions no longer lose a whole shoulder head** — Fitting a session to the clock had one last resort that ignored every protection above it, so it could drop the only side- or rear-delt movement in a program. Across the full configuration sweep this affected 370 programs; it now affects 243, and none of the remainder are caused by trimming.

## v3.60.0 — build 600

- **New programs are the best of six, not the first one rolled** — Building a program has always involved an element of chance — the app rolls a number and that decides which lifts land in which slots. It was handing over whatever came out of the first roll without ever looking at it, and the roll matters more than it sounds: measured across 126 setups, changing nothing but that number moved weekly volume by about two sets a muscle, and left a muscle with no work at all in roughly one program in six that a different roll would have covered. The app now builds six versions of your program, checks each one against your session length, your muscle coverage and its own volume targets, and gives you the best of them. Nothing else changes — your program still has a shareable code, still rerolls, still rebuilds exactly as before. It is simply no longer the first thing the dice produced.

## v3.59.0 — build 599

- **An easy week no longer costs you weight permanently** — The first week of a block is meant to be a little easier — more reps left in reserve, not a lighter bar. But the load was being sized against that eased target, so a new block started lighter than the last one finished, and the following weeks held that weight and built reps instead of climbing back. The reduction stuck, and the next block started lower again. Measured over six blocks, a lifter following the program exactly went 95 to 85 to 80 and never recovered — one notch down per block, indefinitely. Easy weeks now keep your working weight and simply ask less effort of it. A genuine drop in strength still lowers the load, from your actual performance rather than the calendar.

## v3.58.0 — build 598

- **Recovery is judged against your program’s own spacing** — If you train a muscle every day or two — a five-day full body rotation, say — you were being eased back constantly. The check that protects a day you trained exactly as scheduled required a recovery reading of 70, which quietly assumed every program leaves a muscle about two days. On a schedule built around 24-hour gaps the reading after a hard session is around 66, so chest, back and legs were all judged under-recovered every single day and trimmed, no matter how closely you followed the plan. Recovery is now measured against what your schedule’s own spacing can realistically deliver, so training on plan counts as on plan. A genuinely bad reading still eases the day back.
- **Effort notes stay inside a range you’d actually train in** — The “≈ RIR” note beside a set could print figures like 7.5, or even a negative number when a weight was heavier than the reps asked for. Those were arithmetically true and useless as targets. The note now stays quiet outside a sensible range and shows your program’s number instead.

## v3.57.0 — build 597

- **Deload weeks no longer cost you your working weight** — A deload is prescribed at a lighter load on purpose — but once you logged it, it became the weight the app progressed you from. Train a block at 95, deload at 55 as instructed, log it, and the next block started you at 55. Every deload quietly reset you to about 60% and you climbed back from there, so following the deload as told meant never really moving up across blocks. It showed up as “the app never progresses me” rather than anything obviously broken. The next load is now worked out from your last real training week, skipping the deload — while the deload week itself still lightens as it should.

## v3.56.0 — build 596

- **The app notices when your sessions don’t fit your day** — Every workout you finish has always recorded how many of the prescribed sets you actually did, and nothing ever looked at it. That mattered more than it sounds: everything else in the app reads what you LOGGED, so leaving the last two exercises undone each session looked like you were simply training less — and the response was to prescribe more, to a session that was already too long. If you consistently finish well under your prescribed sets across at least six real sessions, the up-next card now says so and suggests a shorter session. One abandoned workout won’t trigger it, and finishing your work never will.

## v3.55.0 — build 595

- **Stalled lifts get the reason that actually fits** — When a lift stopped setting PRs the app could tell fatigue from ordinary noise, but everything else came back as “you’ve hit a ceiling — deload and rebuild”. Two common causes were being missed. If the muscle is getting less weekly work than it needs, that’s under-training, not a ceiling, and deloading is the opposite of the fix — you’ll now be told how many sets it’s getting against how many it wants. And if a lift has flattened while your other work for the same muscle keeps climbing, the muscle is fine and the movement has gone stale, so you’ll be told to swap the exercise rather than change the program. Falling reps with rising effort is still read as fatigue first, whatever the volume numbers say.

## v3.54.0 — build 594

- **One place to ask how much work a muscle is getting** — Internal groundwork, no change to your programs. Two separate lookups answered that question — one for muscles, one for the individual heads of a muscle like the side and rear delt — and asking the wrong one returned nothing, which looked exactly like a muscle getting no work at all. That misread caused a real misdiagnosis while building the last release. There’s now a single lookup covering every muscle and every head, where an untrained muscle reads as zero and only a name that isn’t a muscle reads as nothing, so the two can never be confused again.

## v3.53.0 — build 593

- **When a muscle is short, we tell you what would actually fix it** — If your days, session length and equipment don’t leave room for a muscle, the app used to say a longer session would cover it — without checking. Sometimes that’s wrong: at two days a week no session length rescues a muscle that has no day to train on, and booking ninety minutes on that advice wastes your time. The app now rebuilds your program under each single change you could make — a longer session, another training day — and tells you which one actually closes the gap and how much of it. When nothing single-handedly helps, it says so plainly instead of inventing a fix, because your constraints being the limit is a real answer.

## v3.52.0 — build 592

- **Adding a training day now actually adds training** — Going from four days a week to five used to buy you almost nothing — on a full-body plan the whole week went from 106 sets to 107, and eighteen individual muscles came out with LESS work than they had on fewer days. The targets the generator filled to were fixed numbers for four muscles that happened to sit at the bare minimum, so once they were met it stopped, no matter how much room your week had. Targets are now drawn from each muscle’s own range and scale with how often you train it, and they cover every major muscle rather than four.
- **Side and rear delts are trained on purpose** — The generator treated shoulders as one muscle, so it could answer a rear-delt shortfall by adding a press. Each head is now accounted for separately, and 177 fewer generated programs contain no side-delt work at all.
- **Muscles are filled in order of need, not order of code** — Targets were satisfied one at a time down a fixed list, so whatever sat at the end of that list got whatever was left — abs came out at zero sets on some plans, and calves were short on others. Each pass now serves whichever muscle is furthest below its target, so position in a list can’t starve anything.
- **Sessions keep the exercise count they promise** — The wizard tells you roughly how many exercises to expect. The generator only ever checked the time, so it could build 8 or 9 movements into a session advertised as 7 while still landing inside the minutes.
- **A set trimmed for one muscle no longer lands on another** — When a session was shortened to fit your time, the per-exercise adjustments didn’t move with the exercises. An adjustment meant for one lift could end up applied to a different one — in one case removing a calf set that a decision about back work had asked for. Custom set counts, superset links and round counts were all affected the same way.
- **The coverage notice tells you what’s actually short** — It only spoke when a muscle had literally zero sets. Now that shortfalls get spread rather than concentrated, that meant a short session could leave six muscles under their minimum and say nothing at all. It now names anything meaningfully short, and still stays quiet on long programs where everything is covered.

## v3.51.0 — build 591

- **Easing back after a missed set no longer overshoots** — If you came up short of the bottom of a rep range, the app eased the weight back — but it sized the new weight for the TOP of the range while still asking you for the bottom. On a wide range like 10–20 reps that meant a weight picked for 20 reps and a set of 10, which is barely any effort at all. It also made the effort note beside the set report things like “≈7.5 RIR”, which was accurate about the weight and useless as a target. The eased weight is now sized for the reps you’re actually asked to do, so a small miss gets a small cut.

## v3.50.0 — build 590

- **The split we suggest is now the one that measures best** — The suggested split came from a short hand-written rule — three days meant full body, four meant upper/lower, five or six meant push/pull/legs. Nothing ever checked whether those were actually the best fit. Scoring every split that supports your day count against how well its week lands each muscle in its productive range, the old suggestion never ranked better than third for any combination of days, goal and experience. Push/pull/legs at five days was a clear example: it only has room for one leg day, so legs came out at the same volume as a three-day program while chest and back climbed past their useful ceiling. Suggestions now come from that scoring. Five days for muscle growth suggests full body; six still suggests push/pull/legs, which genuinely wins there; three days for strength suggests one of the dedicated strength templates that were in the app and never recommended. Beginners are kept away from six-day body-part splits regardless of score. Every split is still yours to choose — this only changes what we suggest first.

## v3.49.0 — build 589

- **The volume load wave shows every week again** — On longer blocks the last weeks of the chart looked missing. The row the bars sit in had a fixed height that was about 22px shorter than a full-height column needs once its set-count and week labels are counted, and because the bars hang from the bottom, anything that didn’t fit was cut off the top. Bar height rises with planned sets and a block ramps up, so the tallest columns are the last ones — which is why the early weeks looked fine and the end of the block appeared to be cut off. The chart was drawing all of it; you just couldn’t see the top. The row now sizes itself to whatever it’s drawing, so it can’t clip at any block length.

## v3.48.0 — build 588

- **Starting a workout no longer looks like editing your program** — Leaving the plan screen after a session would ask whether to keep changes you never made. Right before a workout the app tunes that session’s set volume to how you’ve been recovering, and writes the result onto the copy of the program you have open. The check for unsaved changes compared everything, so it saw those two app-written values and concluded you’d edited your program. They’re rebuilt from scratch before every session and never carried forward, so there was never anything there to save. The check now ignores them, and a real edit — a swapped exercise, a rename, a changed setting — is still caught exactly as before.

## v3.47.0 — build 587

- **Following your plan no longer costs you sets** — If a muscle looked under-recovered, the app could cut a set from that day — unless you were training on the schedule your program set out, in which case the plan was supposed to win. That exemption was measuring against the wrong week. It assumed your training days were spread evenly across seven, so on a five-day week it expected about 34 hours between sessions; your actual schedule puts two of them back to back, 24 hours apart. Turning up on Tuesday exactly as written looked like turning up early, the exemption was refused, and the set was cut. It now measures against the real weekday spacing your program schedules. A genuinely bad recovery reading can still ease a day back — that part is unchanged.

## v3.46.0 — build 586

- **You can unban an exercise again** — Banning was one tap and permanent — the only way back was Settings, which unbans everything at once. Open any exercise in the library and the ban button is now a toggle: tap it once to ban, tap it again to unban. The two places that ban an exercise and immediately swap in a replacement are unchanged, so a second tap there still means what it always did.
- **The one-click volume fix understands the forearm properly** — When a muscle came up short, the fix was always “add a set”. For forearms that was wrong: carries, hangs and pinches are isometric holds that never take the wrist through a range, so if your program only had grip work, adding sets to it couldn’t fix anything. The app now checks whether wrist flexion and extension are actually trained, and when one is missing it names the movement that closes the gap instead of quietly adding a set that does nothing. Same for side and rear delts, which get nothing from pressing.
- **The effort shown beside a weight is one that weight can deliver** — On the styles that hold the load and build reps, the weight came from your progression and the target RIR came from the block plan — and the two were never checked against each other. If you did 150×9 with one rep left, the app could still print “3 RIR” next to the same 150, which is not a set you can do. Sets now show the reserve the weight actually leaves, marked with ≈ so you can tell it apart from the plan’s number. The weights themselves are unchanged.
- **The after-workout feedback tells you what will actually happen** — The “+ set” and “− set” badges printed your rating back at you rather than what the app was going to do. Sometimes there was no room — the muscle was already at the top of what you can recover from, or every exercise for it was at its set ceiling — and nothing changed. And your rating was never the only input: your logged volume adjusts sets on its own, which is why sets were being trimmed on weeks you had marked as feeling fine. The badge now shows the real change, says “no room” when there isn’t any, and tells you when the change came from your logged volume rather than from your rating.
- **“Why this?” no longer shows undefined for past sets** — The effort breakdown from your last session was reading the wrong field for weight and reps, so every row printed “undefined × undefined” beside an otherwise correct RIR.

## v3.45.0 — build 585

- **The self-test now checks whether the week itself makes sense** — Checking each set individually can't see a week that trains no back at all. A second layer now looks at the whole week: whether any major movement pattern has been left out entirely, whether a muscle you asked the app to emphasise actually gets enough work, and whether anything exceeds the volume ceiling the generator enforces. It found a real problem — some short-session and low-day-count programs contain no pulling work whatsoever, which is now recorded and tracked so it can't spread while it's being fixed. Findings are split into problems and advisories, because a set that asks for an impossible weight is a bug, while a week sitting slightly under a volume guideline is a judgement call — those guidelines are population averages, not facts about you.

## v3.44.0 — build 584

- **Older workouts are properly attached to your program again** — Workouts logged before day markers became stable were reconnected in some places but not others. The parts that decide whether a lift has stalled or plateaued were among the ones still looking at the old markers, so for anyone with older history they saw nothing at all — a lift stuck at the same weight for four sessions looked brand new, and the app never stepped in to adjust it. Reconnection now happens once, when your data loads, so every part of the app sees the same thing. Nothing is rewritten on your device; if a workout can't be matched with certainty it's left exactly as it is rather than guessed at.

## v3.43.0 — build 583

- **The self-test now actually checks your sets** — It was only ever checking the shape of a program — that days had exercises and exercises had a number of sets — with a fully-stocked gym and no training history at all. That meant it never looked at a single prescribed weight, and none of the progression machinery ever ran, which is why it kept saying “all clear” while real problems went unnoticed. It now simulates a lifter training week after week across four different gym setups — hitting targets, missing them, and stalling — and checks over a hundred thousand individual prescribed sets against rules about whether a set makes sense to a human: a weight you can actually load on that machine, a warm-up that climbs and stays lighter than your work set, a rep target inside the range shown next to it, a drop set no heavier than the set it came from. It then runs the same checks over your own programs and your real logged history.

## v3.42.0 — build 582

- **You can delete a logged workout — and redo that day** — Every workout in your history now has a delete button beside Repeat. It takes two taps to confirm, and you get an Undo afterwards if you change your mind. Deleting properly undoes everything that session caused: your rotation goes back so the app asks for that day again, the muscles it trained stop counting as fatigued, and the loads it suggests roll back to the session before it. If a lift has no earlier session to fall back on, the app forgets it rather than quietly keeping numbers from a workout you deleted.

## v3.41.0 — build 581

- **Swap alternatives now come from the gym you're actually in** — If you added a machine to your gym after building a program, that program never offered it — the swap list was still reading the equipment you had when the program was created. It now uses your active gym. Two related fixes came with it: equipment that implies other equipment (an incline bench implies a bench) was being dropped entirely, hiding every alternative behind it, and the same problem existed in three separate places — the plan tab, the swap list during a workout, and the “ban this exercise” replacement, which picks a substitute for you rather than showing a list. Your program still generates against the equipment it was built for, so opening a plan somewhere else won't rewrite your training.

## v3.40.0 — build 580

- **Now you can see why each exercise was chosen, not just why the weight is what it is** — Last update explained the weight on workout day. This one explains the pick itself. Tap the new question mark on any exercise in your plan and you get the actual scoring: every alternative the app weighed for that slot, what each one scored, and the individual reasons that made up the score — the day's preferred angle, patterns already trained, spinal load carried over, lengthened-position coverage, staple bias, and the rest. Lowest score wins, and the margin over the runner-up is shown. It also tells you the truth in the awkward cases: when a lift wasn't chosen by scoring at all but added afterwards to hit a volume target, when a later balancing pass replaced what scoring picked, and when a day has been edited since it was built so the original scoring no longer describes it.

## v3.39.0 — build 579

- **Every exercise can now tell you why it asked for that** — New “Why this?” button on any exercise in a workout. It lays out the whole decision: the weight and the reason behind it, the rep range, sets and target RIR, which progression style is running and what made the app pick it, any plateau or fatigue override and what will clear it, and everything it read about you — last session, estimated 1RM, stalled sessions, recovery. It also shows, set by set, whether the effort it used was one you rated yourself, the one the plan asked for that day, or an assumption because neither was recorded. The panel reads the same engine that built your sets rather than working anything out on its own, so it cannot drift away from what you are actually being told to lift.

## v3.38.0 — build 578

- **Your logged workouts stay attached to the day you did them** — Every workout you log records which day of your program it was. Those day markers were being generated randomly, so any time a program was rebuilt — shuffled, updated, moved to a newer engine, or changed to a different number of days — all of them stopped matching and your history came unstuck from the program in one go. That is why the app served up a day you had just trained, and why finished workouts stopped showing as finished on the plan tab. Day markers are now derived from what the day actually is, so they survive a rebuild, a reshuffle and even reordering your days by hand. Workouts you logged before this update are reattached automatically by their day name — nothing to restore, and no need to log them again.
- **Swap suggestions were missing equipment you have** — When a program's kit listed something that implies another piece — an incline bench implies a bench — the swap list ignored the implied one, hiding every alternative that needed it. On a dumbbell-and-incline-bench setup that was four missing chest options alone.

## v3.37.1 — build 577

- **Two forearm slots go to the front and back of the wrist, not the side** — Radial and ulnar deviation is a small assistance role through a short range, so when a week only has room for two wrist movements they should be the palm-side and back-of-hand ones. That is what happened already, but only by accident of where the new movements sat in the exercise list — nothing was actually enforcing it, and reordering the library would have quietly changed it. The side is now explicitly the third slot's direction: it is picked once flexion and extension are both covered, and not before.

## v3.37.0 — build 576

- **Side-of-wrist work exists now — and programs actually use it** — The last release could spread forearm work between the front and back of the wrist but not the side, because the library had no radial or ulnar deviation movement in it at all. Four are added: hammer radial and ulnar deviation, which need nothing but a hammer, plus cable and band versions. Adding them was not enough on its own — measured across 42 forearm-focused programs, the new movements were picked zero times, losing every slot to the older staples and to carries. A program that buys a third forearm slot now trains all three directions, and grip work keeps its place.
- **Wrist spread was being applied for the wrong reason** — The rule that spreads forearm work recorded which wrist direction it had trained in only one of the two places it picks exercises — so for almost every program it recorded nothing, and grip holds were being pushed out of every slot unconditionally rather than making way for whatever was actually missing. The spread you saw was right; the reason behind it was not. It now tracks what the week has covered, which is what lets the third direction be found at all.

## v3.36.1 — build 575

- **Myo-reps grow past three on every lift, not just loaded ones** — The 3-to-5 rule shipped yesterday only ran once a weight was on the row. On a bodyweight or assisted movement — and on any lift the first time you do it, before there is a suggestion to fill in — the row opens blank, so logging the third mini added nothing and the set ended early. A blank load means you haven’t typed a weight yet, not that the extension is over.

## v3.36.0 — build 574

- **Myo-reps and drop sets no longer cost you weight** — An extension of a set is short by design and always comes last, and four separate places read “the last set” straight off the list — so a mini counted as the set that decides next session’s load. Taking a 50 lb lateral raise to 20/20/20 and then adding three 4-rep minis had earned an increase to 60; the app prescribed 45 and told you to ease back. Using the technique the block asked for was costing you load every session, and the harder you extended the set the more it took away. Extensions are now invisible to progression — the same session with and without them gives the same answer.
- **Working up in weight is no longer read as a missed set** — 180×6 and then 210×4 came back as “ease back to 180” — a weight you had already beaten for more reps that day. At a fixed load the last set is the right thing to judge; when the load itself moves, the last set is the TOP set, and being at the bottom of the rep range is what a top set is for. A session with varying loads is now judged on what it averaged, which is the same basis the “Last” reference already used. Straight sets are unchanged.
- **Myo-reps run 3 to 5 mini-sets, and stop when you do** — It always asked for exactly three. Three are still laid out up front, and a fourth and a fifth are added only when you actually hit the mini rep range — come in under it and the extension is over. Whichever comes first, which is how the technique is meant to run.
- **Wrist work now covers both directions, not just one** — Every forearm movement — wrist curls, reverse wrist curls, carries, hangs, pinches, a gripper — read as the same movement internally, so the app could not tell a palm-side curl from a back-of-hand one, or wrist work from a static grip hold. Measured across 252 forearm-focused programs: 325 grip slots against 48 wrist, and not one program trained both wrist directions. A program with more than one forearm slot now trains flexion and extension, and grip work keeps its place once both are covered. Side-of-wrist work (radial and ulnar deviation) still has no movement in the library, so that direction is not yet coverable.

## v3.35.0 — build 573

- **Five splits finally have a ready-made program** — Full Body · Pattern Rotation, Torso / Limbs, Push / Pull / Legs / Arms, Upper / Lower / Arms and Powerlifting · SBD Wave were all in the app, and none of them appeared anywhere in Templates — the only way to reach one was to build a custom split, which is the route you take when you already know what you want. There are now nine templates covering them, so the browse list runs to 74. Nothing that already existed has changed: no template’s configuration was touched, and a release check now fails the build if a split is ever left with no way in again.

## v3.34.1 — build 572

- **Fixed: the hamstring template was the same program as the plain PPL** — “PPL — hamstring emphasis” in the shared programs list promised swaps for more direct hamstring work, and was in fact an identical copy of the standard six-day Push / Pull / Legs — same exercises, same volume, different name. It is now a real hamstring build: leg curls and Nordic curls in place of hip thrusts and walking lunges, with about a quarter more hamstring volume a week. Every shared program is now checked before release, so a template cannot claim something it does not do.

## v3.34.0 — build 571

- **Four new themes** — Emerald and Gold join the dark themes, Sage and Ochre the light ones. They fill gaps rather than repeat what was there — a true green, a warm gold, a muted sage and a deep ochre — so every theme still gives you a different app to look at. All eighteen are now checked for readable text on buttons before they ship.

## v3.33.1 — build 570

- **Your Profile score now says what it rests on, and what is next** — The Strength Score on your Profile is the one people screenshot, and it was the only place still presenting the number flat. If your score comes entirely from dumbbell or bodyweight lifts it is now marked as an estimate here too, exactly as it is on your home screen. Both cards also name the next target on your weakest pattern — in the weight you actually load, so a dumbbell target reads per hand.

## v3.33.0 — build 569

- **The Lifts tab is split into four views** — It had grown into one long scroll of ten sections, and several of them were not about individual lifts at all — volume against targets, next block’s volume, fatigue and recovery had all collected there. It now works like the Plan tab: Exercises for your per-lift charts and search, which is where it opens; Records for personal bests, strength standards and goals; Load for intensity and volume; Recovery for fatigue. Nothing was removed, and tapping the Strength Score card on your home screen still takes you straight to the standards.

## v3.32.1 — build 568

- **Fixed: “+3 more exercises” on Up Next now shows them** — The Up Next card lists the first five exercises of the session and counted the rest underneath. That count looked like something you could tap, and was not. Tap it now and the rest of the session opens in place, with the sets and reps for each; tap again to fold it back to a summary.

## v3.32.0 — build 567

- **Search your lifts on the Progress screen** — The Lifts tab keeps one chart for every exercise you have ever logged, so the longer you train the further you scroll to reach any one of them. There is now a search box above the list. It matches muscles and shorthand as well as names — typing “chest” finds your presses, “rdl” finds Romanian deadlifts — using the same matching as the exercise picker, so the same words find the same lifts wherever you search from.

## v3.31.1 — build 566

- **Drag to reorder today's exercises** — The day list now has a handle on each row — press and drag to move a lift up or down the session, past as many positions as you like in one go. The arrows are still there for a quick single step, and remain the way to do it with a keyboard or screen reader. Undo covers the whole drag rather than each step of it.
- **Fixed: rearranging a later exercise moved you onto it** — Moving a lift from the day list jumped the workout to that lift, so reorganising what was still to come lost your place. You now stay on the exercise you were actually doing.

## v3.31.0 — build 565

- **See the whole day, and rearrange it, from one list** — Tap the exercise counter at the top of a workout to pull up everything you are doing today. Each lift shows how many sets you have left, the one you are on is marked, and tapping any of them jumps straight there. You can move lifts up and down to change the order — useful when a rack or machine is taken — skip one for today, or add another, all without leaving the list. Reordering, skipping and adding were already possible, but only from the card of the lift you happened to be standing on, so changing the order meant walking to each one in turn.

## v3.30.0 — build 564

- **Supersets show the whole group, with everyone's progress** — A linked pair or trio only ever named the exercise either side of the one you were on — so standing on the middle movement of three, you were told about two neighbours without being told they were one round, and nothing said how many sets each still had left. Now every movement in the group sits in a row: its position (A1, A2, A3), its name, how far through it is, and which one you are on. Tap any of them to go straight there instead of swiping back and forth between machines. It shows in focused mode too, which is exactly when you are moving between stations.

## v3.29.1 — build 563

- **Focused mode stops hiding things you need** — The row of actions under a lift — Info, Warm-up, Superset, History, Swap, Pin a note, Plates — was hidden in focused mode, so using it meant dropping out of the mode and back again. The warm-up button was the one that hurt: a ramp is only built when the app already knows your working weight, so the first session of a brand-new program has none, and that button is the only way to add them. In focused mode there was no way to reach it. The last-set technique — partials, myo-reps, a loaded stretch — now also shows on the set it actually applies to, which matters more here than anywhere, because focused mode shows you one set at a time.

## v3.29.0 — build 562

- **Dumbbells and bodyweight lifts now count toward your Strength Score** — Every strength standard in the app was a barbell one, so anyone training with dumbbells got a permanently empty panel telling them to log a squat, bench, deadlift or press — lifts their own program never prescribed and their gym might not contain. Four of the twelve programs the app itself offers could never produce a score. Dumbbell presses, rows and RDLs, and weighted pull-ups, chin-ups and dips, are now measured against their closest barbell equivalent and marked “Est.” so it is clear which numbers came off a bar. Push-ups, goblet squats and split squats are deliberately left out: a rep count is not a load, and a made-up level is worse than none. If you already train with a barbell, your score has not moved by a single point.
- **Your score now says why it moved, and what to lift next** — Strength standards are relative to bodyweight, so gaining weight lowers the score even when every lift holds — correct arithmetic, and baffling to read. The card now separates the two: how much came from your training and how much from the scale. It also names the next target on your weakest pattern, taken from the same table that set your level, so the goal can never disagree with the rank beside it.
- **Fixed: one unknown exercise could stop the app from opening** — A saved program can name an exercise a given version does not have — after restoring a backup from a newer build, or on an older install. That single unknown movement stopped the whole app from starting, and because the program is stored on the device, reloading hit the same wall every time. The exercise now shows as unavailable in that one slot and everything else carries on. Your program is left untouched, so the lift returns intact once the app knows it again.
- **Fixed: the backup reminder could go quiet without a backup existing** — Opening the backup screen was enough to mark you as backed up, so tapping the reminder, glancing at it and going back silenced it for another two weeks with nothing saved anywhere. Only actually sharing, downloading or copying the file counts now. The error screen can also save a backup before you do anything else, since reloading cannot help when the problem is the stored data itself.
- **Fixed: personal records could show the wrong unit** — A workout entry that arrived without a unit recorded on it — from an imported backup, or a much older version — was labelled in kilograms no matter which unit you train in. It now follows your setting, while entries that do carry their own unit keep it, because a record of what you lifted should not change because a preference did.
- **Fixed: panels showed two grab handles** — The bar you pull to close a panel was drawn twice on most of them, leaving a doubled line across the top. Toasts and the resume banner were also being treated as draggable panels, which put a stray handle on them and pushed their text sideways.

## v3.28.1 — build 561

- **Fixed: pulling a sheet down did nothing** — Dragging a panel down by its top was released last version and did not actually work on a device. Panels open with a slide-up animation, and a browser gives an animation priority over the position the drag was setting — so the sheet was being told to move and the animation kept overruling it. The drag now takes that animation off before it starts moving anything.

## v3.28.0 — build 560

- **Pull a sheet down to close it** — Every panel that slides up from the bottom — exercise pickers, plan info, settings, the lot — can now be dragged down by its top to dismiss, with a grab bar showing where. Closing one previously meant reaching for a small X or tapping the strip above it, which on a large phone is exactly where a thumb does not reach. A short pull springs back, so a scroll that overshoots will not throw the panel away.

## v3.27.3 — build 559

- **Internal: every program the app can build is now measured** — The release sweep that checks generated programs against volume landmarks was walking a hand-written list of splits, session lengths and training days — and that list had fallen behind. Ten of the twenty-three splits, both two-hour session lengths, and every two-day program were never checked. The sweep now covers all of them, and a new check fails the build if anything the app offers is ever left out again.

## v3.27.2 — build 558

- **Internal: the set shortcuts are now tested by using them** — The buttons that restore a suggested load or fill in last time’s numbers were checked only by inspecting the code that draws them. A release check now builds a training history, walks into a real session and taps them, confirming each fills the row with exactly the numbers it names — and that it withdraws once taken.

## v3.27.1 — build 557

- **The two shortcuts under a set are readable now** — Restoring the suggested load, or filling in what you lifted last time, were the smallest text in the app — a pair of nine-pixel numbers squeezed under the target, distinguishable only by a tiny arrow, with nothing on screen saying what tapping one would do. They are now proper buttons under the row, each labelled: “Suggested 185×8” and “Last time 180×7”.

## v3.27.0 — build 556

- **Programs now say what they cannot fit** — At 20 to 40 minutes a session holds three or four exercises, so a week is about a dozen movements for the whole body — and something has to give, usually side and rear delts. That prioritising is right, but it was happening silently: you could train a program for months without knowing it contained no lateral raise. A short program now states which muscles get no direct work, so you can lengthen the session, add the movement yourself, or accept it knowingly. It only appears when a muscle gets nothing at all, and never on programs of 90 minutes or more.

## v3.26.1 — build 555

- **Fixed: two bench options that read as the same thing** — Your gym’s equipment list offered “Adjustable Bench” and “Adjustable / Incline Bench” side by side, so it was natural to tick one and move on — and whichever you skipped quietly removed a whole class of pressing from your programs. Ticking only the incline one is what made an incline day open on a flat barbell bench, because no incline press was available to choose. The first is now labelled “Flat Bench”, and selecting an adjustable or decline bench now implies you can also bench flat, since those benches do. Worth re-checking your gym: if you only ticked one, you should now see both flat and incline work appear.

## v3.26.0 — build 554

- **Session times now include the gym, not just the lifting** — The estimate counted your sets, your rest and your warm-up ramp — and nothing for walking to the next station, waiting for it, or loading the first working weight. It read about a quarter short of a real session. Changing exercise is now costed, more for a barbell than a cable and almost nothing for a superset, so the time on the card is the time on the clock. Your programs are unchanged: this corrects what is shown, not how sessions are built.

## v3.25.1 — build 553

- **Fixed: exercises that did not match the day they were on** — In Full Body · Pattern Rotation the squat day could open on a leg press and the unilateral day on a bilateral squat — the pattern was in the day’s name and nowhere in how exercises were chosen. Each day now states the movement it wants, so the squat day opens on a squat, the hinge day on a hinge, the unilateral day on a lunge or split squat, and the deadlift day on a deadlift. Later exercises in the day are still free to vary. A floor press could also open an incline day, because the app did not recognize it as a flat press and so never scored it against the day’s angle.

## v3.25.0 — build 552

- **Fixed: Full Body · Pattern Rotation never programmed a deadlift, and pressed with dumbbells** — Every day in that split was led by a leg movement, and only a day’s main lift draws the heavy barbell variant — so pressing stayed an accessory even in strength and peak blocks, and picked a dumbbell version. No day asked for lower back work either, so a conventional deadlift could never be chosen. The rotation now leads with squat, hinge, incline press, deadlift, then the pump day, so each of the big lifts anchors a session and waves down into low reps like the original Full Body does. Existing programs are unchanged.
- **Session times now learn how long you actually take** — The estimate priced your sets and rest but not the gym around them — walking to the next rack, waiting for one, loading plates. It now compares what past sessions were estimated at against the clock they actually took and adjusts what it shows you. It needs four completed sessions before it changes anything, ignores one left running by accident, and does not alter how programs are built.

## v3.24.0 — build 551

- **The session length picker tells the truth now** — It said “~6 exercises” next to “90 to 120 minutes” — six exercises is about an hour. The count shown is now measured from what the app actually builds, and it accounts for your goal, because rest length decides how much fits: the same time holds more hypertrophy work than strength work. An automated check regenerates programs before each release and fails if any of these numbers drifts from reality.
- **Known: long sessions still come up short** — Choosing 90-120 minutes or longer currently produces days of around 80 minutes. The picker no longer overstates what you will get, but the underlying shortfall is not fixed yet — it is now measured on every release rather than going unnoticed.

## v3.23.2 — build 550

- **Internal: two blind spots closed in the program checks** — The measurement sweep only ever built 40, 60 and 90 minute sessions, so the two longest lengths the app offers — where it struggles most to fill a day — were never checked. It now covers every length. The check harness that deliberately breaks the app to prove the tests still work also no longer writes to the source it is testing.

## v3.23.1 — build 549

- **Internal: the release checks now prove they can fail** — A suite of automated checks is only evidence if it is capable of going red. Eight known past defects are now deliberately reintroduced before each release and every one must be caught, with a harmless control change that must NOT be flagged — so a check that has quietly stopped testing anything is detected rather than trusted. The program measurement sweep also refuses to compare a run that generated nothing, and now reads volume at the first week of a block as well as the peak.

## v3.23.0 — build 548

- **The Plan tab now has four views** — Schedule, Volume, Progression and Blueprint. Schedule is what it always was, with a week strip across the top carrying each week’s sets and running time. Volume shows the week against the landmarks, and tapping a muscle lists the lifts that fed it — including compounds feeding it indirectly, and at what rate. Progression is every lift week by week with its sets, rep target and effort, searchable and filterable. Blueprint charts the block’s planned sets per week alongside its phases. The header also states the week, your session frequency, the sets that week prescribes and the phase it belongs to, over a bar of workouts completed.
- **Home is more compact** — Browse templates, build your own and training cycles were three full-width buttons stacked down the page. They are now one block of four tiles alongside a quick unguided session, each showing its own count. A new program can also be started straight from the header of your program list.

## v3.22.0 — build 547

- **See the session before you start it** — The card on Home now lists the first lifts in today's workout with their sets and reps, and says how many more are in the session — so you can tell what you are walking into without starting it first.
- **New: lift progression** — A table in Plan info showing every lift week by week — sets, rep target and effort — so you can see where the block steps up and what the deload actually removes, instead of paging through one week at a time. Searchable, and filterable to primary lifts, upper or lower.
- **New: volume load wave** — The block’s planned sets per week as a chart, so an accumulation ramp or a deload trough is visible at a glance.
- **Weekly volume now shows what fed each muscle** — Tap a muscle to see which lifts contributed to its weekly total, including compounds that feed it indirectly and at what rate — the reason a total can read higher than the direct work you remember doing.
- **Fixed: muscles at the top of their healthy range read as out of range** — Splitting the volume bands last release left three places still treating only the lower half of the MEV–MRV range as productive, so a well-trained muscle could be counted as outside its range on the progress and volume screens.

## v3.21.0 — build 546

- **Four more splits** — Torso / Limbs (4-5 days) alternates chest-back-shoulder days with leg-and-arm days, so arms are trained in a session where they are not already spent. Push / Pull / Legs / Arms adds a dedicated shoulders-and-arms day to PPL. Upper / Lower / Arms does the same for a 5-day upper-lower. Powerlifting · SBD Wave builds the week around squat, bench and deadlift with hypertrophy accessories behind them. All four are additions — no existing program changes.
- **Weekly volume now reads in four bands, not three** — A muscle between its minimum and its ceiling used to read simply as “productive”, whether it sat in the middle of the range or right at the top. The band now splits at the point the app itself uses when deciding whether to trim a session, so “high” is distinguishable from “productive”, and each band explains what it means.

## v3.20.0 — build 545

- **New split: Full Body · Pattern Rotation** — A second way to run full body, alongside the existing one — each day pairs a leg pattern with an upper push or pull rather than hanging everything off one anchor lift: squat with horizontal push/pull, hinge with vertical, then unilateral, posterior chain, and a pump day. Spreading the pressing across the week means the front delt takes much less of it. In exchange, side and rear delts get slightly less coverage than the original full body, so it suits you better if you already do your own lateral and rear work. The original Full Body is unchanged and still the default.

## v3.19.0 — build 544

- **New: focused mode for the workout screen** — A button in the workout header strips the screen back to one large card for the set you are on — load and reps as big numbers in their own labelled tiles, each with full-size hold-to-repeat steppers, the target and what you lifted at that set position last time on one quiet line above, and a row of pips showing every set in the exercise. Everything you set up before a lift steps out of the way: the exercise strip, coach note, pinned cues, the suggestion banner, warm-up and superset controls, notes. It starts at your first working set rather than walking you through the warm-up ramp, the arrows move between sets so you can go back and correct one, and the rest clock is much larger. Logging is the one wide button in the bar at the bottom, with the effort question offered beside it rather than demanded. Your choice is remembered if you reload mid-workout.
- **Fixed: unreadable text on light themes** — A few buttons and labels referred to a color that does not exist, so they fell back to the page text color and became near-invisible on Mint, Clay, Stone, Slate, Lilac and Rosewater. All of them now use the correct color.
- **Tighter set list** — The effort tag under each logged set was taking a full line of padding, which made a finished exercise read as loose.

## v3.18.1 — build 536

- **Fixed: the rest timer never started on myo-reps in the last exercise** — Once the final working set of a session was logged, the app treated the workout as over and stopped starting rest timers — so any myo-rep or drop set still waiting got no timer at all. Because it triggered on the last working set rather than a position in the list, it hit whichever exercise you happened to finish last. Rest now runs whenever anything is still unlogged.

## v3.18.0 — build 535

- **Rope exercises now show you holding the ends, not the middle** — Every rope movement drew the rope splitting at your hands, so it looked like you were pinching the middle of it. A rope hangs from a carabiner and each hand takes an end, and the strands pull in a straight line toward the machine — face pulls, rope pushdowns, rope hammer curls and rope rows all show that now.

## v3.17.9 — build 534

- **Front raises and face pulls now show the right movement** — Both were drawn as a lateral raise — arms sweeping out to the sides. A front raise is the arm travelling forward to shoulder height, and a face pull is a high pull toward the head with the elbows above the wrists, so both now animate the movement you are actually doing. Every other shoulder raise is unchanged.

## v3.17.8 — build 533

- **Fixed: dynamic double progression could prescribe less than you just lifted** — A lift on Dynamic double could open with weights BELOW the ones in its own Last Time column, and announce a set advancing when nothing in that column had topped the rep range. The app was progressing it from a different workout than the one you were looking at: it normally anchors on your best of the last three sessions of that day, so one off day can't drag your loads down, and it ranks those sessions by the single best set in each. On per-set progression that's the wrong question — an older session can win on set 1 alone while every other set in it was lighter, and then sets 2, 3 and 4 get prescribed from those lighter numbers. Dynamic double now advances each set from your most recent session, so every set progresses from what you actually did last time and the Last Time column is what the prescription is built on. Every other progression style keeps the best-of-three anchor and its protection against a single bad day.

## v3.17.7 — build 532

- **Saved program cards are readable by screen readers** — The card that opens a program had no label of its own, so a screen reader read out everything printed on it run together — the name, the Active badge, the split, the day count — as one long string. It now announces itself as "Open <program name>".

## v3.17.6 — build 531

- **Fixed: correcting an old workout didn't update your training insights** — If you went back and fixed a weight or rep count in a session from a while ago, the app kept using its previous read of your training — plateau detection, effort calibration, recovery and every coach note carried on from the numbers you'd just corrected. It only noticed changes to how MANY workouts you had and which was newest, so an edit in the middle was invisible to it. It now notices any change to what's actually in them.

## v3.17.5 — build 530

- **Fixed: a lift stuck at the same numbers wasn't reported as stalled** — Plateau detection could only see a lift that was getting WEAKER. If your estimated max simply stopped moving — which is what a plateau nearly always looks like — every session tied for "best" and the newest one was read as a fresh record, so the count reset to zero every time. The result was the app switching a stuck lift to recalibrated loading and telling you why in the session, while the coach card said nothing at all. Both now measure it the same way, and adding reps or sets at the same weight still correctly counts as progress rather than a stall.

## v3.17.4 — build 529

- **Fixed: the 1RM calculator's percentage table could ask for more than your max** — Training percentages were rounded to the nearest plate increment, which rounds up as often as down — so an estimated max of 263 lb printed "100% = 265", four pounds heavier than the number directly above it. Every row leaned the same way. They now round down, so no percentage ever asks for more load than the estimate it came from, and 100% stops contradicting the max it's derived from.

## v3.17.3 — build 528

- **Fixed: a workout with a missing date could poison a lift's stats** — A backup restored from another device can contain a workout entry with no date on it — the app never drops those, because losing a logged session is worse than carrying an odd one. But the training model measured "days since you last trained this" straight off that date and produced a nonsense value for the whole lift. Nothing displayed it yet, so nobody saw it; it would have surfaced the first time something did.

## v3.17.2 — build 527

- **The bottom navigation is now readable by screen readers** — The five tabs along the bottom were unlabelled buttons — a screen reader announced them without saying which was which, and nothing marked the one you were on beyond its color. Each tab now carries its name and reports itself as the current page.

## v3.17.1 — build 526

- **What's new on the home screen is a summary again** — Each entry was printing its full write-up — some of them over a hundred words — three deep on the screen you open to start training. They're now trimmed to a line or two each, and the complete text is still there under Full changelog.

## v3.17.0 — build 525

- **Program cards: one button to switch, everything else in a menu** — A saved program carried four icon buttons in a row — switch, next block, duplicate and delete — all the same size and shape, sitting side by side on a screen you scroll past. Delete looked exactly like the one next to it. Switching is the only one you reach for on purpose, so it keeps its tick; the rest moved behind a three-dot menu that names each action in words. The program name gets the space back, and the row stops growing every time a new action is added.

## v3.16.2 — build 524

- **Fixed: unsaved program edits could disappear when opened from the Plan tab** — Editing a saved program holds your changes until you hit Save, so a half-finished edit survives leaving the screen. Opening that program from the Plan tab's "Open full program" loaded the last SAVED version instead of your pending edit — and because the app then saw nothing pending, it discarded the edit entirely. No prompt, no undo. That route now resumes your in-progress changes like every other way into a program.

## v3.16.1 — build 523

- **Fixed: updating a cycle block's engine could scramble other blocks in the cycle** — The block you updated always updated correctly — the damage landed on the OTHER phases of the same cycle. Saving any change to a cycle block was treated as "carry this block's exercises to every phase," with no check for whether anything had actually changed. An engine update regenerates a block from its own settings and usually doesn't touch which exercises it uses at all — but every other phase in the cycle got overwritten with that block's list anyway. The same flaw meant swapping a single exercise on one day could silently overwrite unrelated days on other phases too. Only what actually changed is carried across the cycle now; everything else — including a lift you're mid-block on — is left exactly as it was.

## v3.16.0 — build 522

- **Your plan stops losing a set to autoregulation every session** — If you train a muscle again before it's had time to recover — a five- or six-day full body, most often — the app was trimming an accessory set at the start of most sessions. It was only ever removing volume it already considered spare, but it made that decision fresh every workout, so the volume your program said it would train was never the volume you actually trained. That decision now happens once, when the program is built. You get a plan that's honest about what it's asking for, and autoregulation goes back to responding to a bad day rather than to your schedule.
- **What it will and won't take** — It only removes volume above the point where extra sets stop paying for themselves, never below what a muscle needs to grow, never from your main lift, and never from a muscle you asked for more of. A muscle whose schedule already leaves it time to recover is left completely alone — measured across 864 program configurations, four- and two-day-a-week splits come out identical.
- **Existing programs are offered the update, not given it** — Programs keep running the engine they were built on, so nothing changes mid-block. The Plan screen offers the update and says what it changes; you can take it or keep the program as it is.

## v3.15.1 — build 521

- **Fixed: an effort reading of "0.050000000000000044 RIR"** — The app measures how far your own effort ratings sit from what your performance implies, and quietly corrects for it — so a logged 1 RIR might be read as 0.05. That correction is right and worth keeping, but the number was being printed exactly as computed, floating-point noise and all. Effort now displays to the nearest half rep everywhere it appears. The correction itself keeps its full precision where the load maths uses it.
- **Fixed: un-ticking a set left its numbers behind** — Completing a set retunes the next one from what you just lifted. Un-ticking it put the tick back but left the retune in place — so weights and reps written by a set you had un-logged stayed on screen with nothing to explain them, and a few mis-taps while setting up filled an exercise with numbers you never asked for. Un-ticking now puts the affected set back exactly as the program wrote it. Anything you typed yourself is left alone.
- **The plate breakdown follows the set you're about to do** — The loading strip under an exercise was pinned to the first working set and stayed there. On a ramp it told you to load the bar for set 1 while you were standing at it for set 3. It now reads the next set you haven't logged.

## v3.15.0 — build 520

- **Fixed: doing exactly what the app asked could take weight off the bar** — Every set is prefilled at the bottom of its rep range — that is what the range means: start there and build up. But after you logged one, the app predicted you would get one or two fewer reps on the next set from fatigue, treated that prediction as a failed set, and stripped an increment. Log the 40x10 it asked for and the next set became 35; log 95x10 and the next became 90. The weight now only changes when a set you actually PERFORMED lands outside the range — too many reps and it goes up, a real miss and it comes down. Reps drifting down across your sets is normal and no longer costs you load.
- **Fixed: tightening your RIR did nothing at all** — A block that moves from 2 reps-in-reserve to 1 is asking for more effort at the same reps, so the load should step up. It never did. Unless you had rated every set by hand, the strength estimate quietly used THIS week's target as the effort for LAST week's sets, which cancels out exactly — so 2 RIR, 1 RIR and 0 RIR produced identical suggestions forever. Your logged sets already record the RIR they were prescribed at, and that is what gets read now.
- **Fixed: one lift, three different answers about how it progresses** — The style badge, the advice line under it and the set weights themselves each worked out the progression style separately, and could each get a different answer, because one of them was not told which day it was looking at. That is how a lift could be labelled "e1RM autoregulation" while the advice underneath said "same load, build toward 15 reps" and the sets below were laid out with per-set weights. They all ask one function now.
- **e1RM autoregulation is temporary, and says so** — When a lift stalls, the app switches it to e1RM autoregulation to recalibrate the load. It had no way back: it checked whether you were still stalled by looking at your recent sessions, but autoregulation holds you steady on purpose, so the check could only ever see more flat sessions. The switch is now time-boxed — it hands the lift back to your program's own progression on a new best or after a few sessions either way — and the badge says why it engaged and what ends it.
- **Myo-reps rest for 15 seconds, not two minutes** — The short pause is the whole technique, and the first one never happened: the set that takes you to failure is an ordinary working set, so finishing it started the full working rest. The timer now looks at the set you are about to do rather than the one you just finished, so the pause before the first mini-set is short — and the first drop of a drop set is immediate.
- **Myo-reps and drop sets follow your actual working weight** — The mini-sets copied the weight once, when the exercise was first laid out, and then ignored it. Change the working weight afterwards — by hand, with the steppers, or because the app retuned it mid-exercise — and the extensions were left showing the old number. They are worked out from the set they extend now, every time, so a myo-rep always matches the set it came from and a drop set stays the right fraction of it.
- **No delete button on a myo-rep the program asked for** — Skipping a prescribed mini-set costs you nothing, so a delete control sitting at the end of the row was all risk and no benefit. Extensions you added yourself keep theirs, and the Myo button still removes a prescribed set deliberately.
- **Fixed: the target column could disagree with the weight box** — When the app retuned a set from the one you just logged, it changed the weight but left the target beside it reading the old prescription — so the row showed a target of 40 and a weight of 35 with nothing explaining the difference. The target follows the retune now.
- **The suggestion names the set it came from** — The line above your sets showed your BEST set from last time while the advice beside it was worked out from your LAST one. If your reps fell across the exercise you would read "last 40x15 — build toward 15 reps", which looks like the app failing to notice a set it had just shown you. It now shows the set the suggestion was actually built from.

## v3.14.9 — build 519

- **Program cards are roomier again** — The Switch button added last build was a word taking up card width, which squeezed program names down to a few letters — worst inside training cycles, where the cards are already narrow. It is back to a compact tick; hold it for a moment and it tells you what it does. Long names now wrap onto a second line instead of being cut off, and cycle blocks got a little of their width back.
- **Pick a program from a list instead of hunting for its card** — There is a Switch program button on your Up Next card now. It opens a list of everything you have saved, showing how long each program is, how many days a week it runs, how much of it you have finished and which block of a cycle it belongs to — the things you would actually compare. Your current program is marked and the switch still announces itself with an Undo.
- **Switching programs now tells you it happened** — Changing your active program used to do it silently — the card jumped to the top of the list and nothing else. It now says which program you have switched to and offers Undo, which also puts a training cycle back to the block it was on. The control is a labelled Switch button rather than a tick sitting next to Delete.
- **Fixed: six side-scrolling rows could block the page from scrolling** — Rows that scroll sideways — the gym chips on Home, the equipment lists in Gyms, the chart ranges in an exercise, the gallery filters — could swallow an up-and-down swipe that started on them, so the page underneath stayed put. Same fault as the Plan tab last build, in six other places.
- **Plan phase colors now come from your theme** — The phase colors added in the last build were fixed values that fought most of the themes. Each phase now takes its color from your accent, shifted just enough to tell them apart, so the Plan belongs to whichever theme you are using. Deloads still sit apart on purpose.
- **Plan header rows line up** — The phase strip used fixed-width chips while the stat boxes above divided the width evenly, so the two rows did not share a grid. They now line up edge to edge.
- **Fixed: tapping a day in the Plan sent you to Home** — Opening the Plan tab straight after launching the app and tapping any day bounced you back to the Home tab instead of showing the workout. The Plan works out its own program, but the preview screen was still checking for one you had explicitly opened.
- **Training phases now look like what they ask of you** — Each phase carries its own color and icon — building volume, raising intensity, sharpening, and a cooler quieter one for deloads — in the strip at the top and again on the block it heads, so a phase is one recognisable thing wherever it appears rather than four identical chips.
- **Plan days now say what the session actually is** — Each day in the Plan listed only its name, set count and length. It now also shows the lift it is built around, how many exercises it holds and the muscles it works, so you can tell two days apart without opening either. The phase box no longer cuts long names off half way through.
- **The Plan opens on what's next, and days preview before they start** — The plan now leads with the next workout you owe, and the week you are in is already open when you arrive. Days you have logged are ticked off and the next one is marked. Tapping a day opens a preview of it — the lifts, sets and target loads — with Start as a separate step, instead of dropping you straight into a session.
- **Fixed the phase weeks shown for a cycle** — When a program was one block of a training cycle, the phase strip worked out the later blocks' week ranges from the current block's length, showing impossible spans like "W6-6" and "W10-6". Each block's weeks now add up across the cycle.
- **The Plan tab is now a scrollable week list** — The first version laid the weeks out as a wide grid that had to scroll sideways, which on a phone stopped the page underneath from scrolling at all. It is a straight list now: one row per week showing what it holds and how many of its sessions you have done, grouped under the phase it belongs to, and each week opens to its training days. The nav bar also no longer disappears when you open the tab.
- **New Plan tab — your whole program at a glance** — Until now you could only see one week at a time. The Plan tab lays the entire block out: which phase you are in, a grid of every week against every training day with the sets and time each holds, and an expandable table showing how each lift builds week by week. The deload is marked everywhere it appears. Tap any cell to jump straight into that day at that week.
- **The history grouping switch now always appears** — The By cycle / By month switch added in the last build only showed up if you had built a training cycle, so most people never saw it. It is now always above the session list, and it reads By program when you have not run a cycle, since that is what it groups by.
- **History can group by training cycle** — Your workout list can now be grouped by the cycle and block you were running instead of by calendar month, so a block that ran across the end of a month reads as one block rather than two. Switch between By cycle and By month above the list. Sessions from standalone programs keep their own group.
- **Switch the Strength Club between what you lifted and your estimated max** — The club total is now yours to define. Tap Lifted for the heaviest weight you have actually put on the bar across squat, bench and deadlift — the way the 1000 lb club is traditionally counted — or Est. 1RM for what your best sets project to. The card says which one it is showing, and all three lifts always use the same measure.
- **History no longer blanks out on an incomplete entry** — A workout entry missing its volume figure — which can happen with imported backups or sessions saved by much older versions — took the entire History tab down instead of just showing zero for that one line.
- **Strength score over time now tracks strength, not bookkeeping** — The score-over-time chart could show a long decline while every one of your lifts was getting stronger. Lifts you train less often were counted less at first and more later, so the line drifted downward as the weighting caught up, and the card would report something like a 16-point drop and a lower level. Every point on the chart is now weighted the same way, so the line moves when your lifts move and stays put when they don't.
- **Strength standards no longer crashes the Progress tab** — Tapping the edit button on the Strength standards card took the whole Progress tab down with a blank error. The card was doing a piece of its calculation in a place that only ran some of the time, which is something React refuses to allow. It runs every time now. The earlier attempt at this fix in build 501 changed the wrong thing and the crash survived it.
- **Up Next shows the workout, not the gym notes** — The Up Next screen was listing your pinned notes and machine setup under every lift. That is information you need standing at the machine, not deciding whether to train, and it pushed the actual prescription down the screen. Notes and setup still appear in the session itself, where you use them.
- **Myo-reps show correctly in history** — When you view a past session, myo-reps and drop sets are now labeled as M/D instead of numbered like working sets, so you can tell at a glance which sets were extensions. Records (PRs, best e1RM, top volume) now exclude extensions correctly.
- **Strength standards edit fixed** — Clicking the edit button to update your profile on the Strength standards card now works correctly.
- **Search for exercises when swapping** — When replacing an exercise in a workout or program, tap the exercise list to filter it by name instead of scrolling through all available options.
- **Unweighted exercises count in recovery trends** — Pull-ups and dips logged without weight now include set count in exerciseTrends and underRecoveredWeekly, giving an honest recovery cost to bodyweight work.
- **"Sets you've actually logged" means working sets** — Myo mini-sets and drop sets are extensions of the set above, and every other screen already treats them that way — but the weekly logged-volume counter added them in as full sets. Three minis on an overhead press showed as six sets of shoulders instead of three, and then got compared against volume landmarks that are defined in working sets. There were also two copies of that counter, and the weekly volume advice used one of each.
- **Editing one block of a cycle keeps the others intact** — Saving an edited block carries the change to its sibling blocks. The rule that does the carrying existed twice — once for real, once in a simplified copy that the tests were checking — and the copy could not remap a day's main lift or its supersets. Reordering a day in one block could quietly point another block's primary at the wrong exercise.

## v3.12.0 — build 498

- **Tap a muscle to see what trains it** — "This day hits" showed numbers like Glutes 7.7 with no way to ask where 7.7 came from — and on a day with no glute exercise on it, that reads like a bug rather than three lifts each paying a share. Tap any muscle now and it opens the exercises behind it, with the arithmetic shown: direct work counts its full sets, secondary work shows the sets and the share it contributes.
- **The breakdown is the number** — It is built from the same pass that produces the chip rather than a second walk over the day, so what you open always adds up to what you tapped.

## v3.11.2 — build 497

- **One strength curve, not eight** — The formula behind every estimated max in the app was written out by hand in eight different places, in four slightly different variants. That is why the same lift could read 291 in one screen and 300 in another: a fix applied to one copy never reached the rest. There is one now, and everything else asks it.
- **The rep-target table agreed with nothing** — Its one-rep row divided your 1RM by an extra factor, so a 300 lb max was listed as a 291 lb single — while the strength charts, correctly, said 300. Same cause: a second copy of the maths. Fixed, and the whole table now matches the charts.
- **Warm-ups and drop sets counted consistently** — The rule for what counts as a working set was re-typed dozens of times and some copies had drifted, so a few older screens still counted a drop set or a myo mini as a full set. They all ask the same question now.

## v3.11.1 — build 496

- **"Last 225×5" when you did 225×6** — Two screens were printing the wrong number for the same reason. The weight and reps stored against a lift are deliberately the fewest reps you held at the top weight — the cautious figure the app uses to pick your next load. Printed as "last 225×5" on the program view, it reported a set you had already beaten. Personal records had a second version of the same problem: among sets at the same weight it kept whichever it saw first, so the first time you ever touched 225 owned the record no matter what you did on it later. Both now read your best actual set.
- **Drop sets stay drop sets in your history** — Myo mini-sets and drop sets are marked as extensions while you train, but that mark was thrown away when the session was saved — so anything reading your history later counted them as full working sets again, including the new volume-per-set chart. The mark is saved with them now.

## v3.11.0 — build 495

- **The cycles tab tells you where you are** — It listed the plan and nothing else — no progress, no dates, no sense of what you had actually done. Every cycle now carries a progress bar, the week you are on out of the total, how many weeks are left, how many sessions you have logged against it, and the calendar window it runs in. All of that already existed one screen deeper; the list just could not reach it.
- **Finished blocks can be reviewed** — A completed block used to be a checkmark. Tap Review and you get what actually happened in it: which lifts moved and by how much, each muscle's weekly volume set against whether it progressed, and a concrete change to carry into the next block. It is offered when you complete a block too — the moment it is most worth reading.
- **One note at a time, not four** — Every block printed its full intent, so a four-block cycle was four paragraphs you read once months ago. Only the block you are in shows its note now. Finished blocks show what you logged, upcoming ones show which weeks they land in, and the detail page still has every word.

## v3.10.0 — build 494

- **Six ways to read a lift, over any range** — The exercise library charted one thing — estimated 1RM, every session you have ever logged. It now charts estimated 1-RM, 3-RM and 10-RM, your heaviest actual weight, total volume, and volume per set, across 1W, 1M, 3M, 6M, 1Y or all time. The rep maxes come from inverting the same formula the rest of the app uses, so the 1-RM you see here is the same number your records tab calls your best.
- **The exercise page is a full screen now** — It was a sheet sliding up over the list, capped at most of the screen height, with the library still showing behind it. What is in there — the demo, every session you have logged, the charts, your PRs and rep maxes — is a destination, not an aside. It gets the whole screen and its own back button.
- **The charts are spaced by date, not by session** — The old chart put every session an equal step apart, so a fortnight off looked identical to training twice in a week. Points now sit where they actually happened, which is what makes the range buttons mean anything.

## v3.9.3 — build 493

- **Myo-rep minis stopped counting as full sets** — When the app adds myo-reps to a lift, the three short mini-sets were being treated as three more working sets — so a five-set exercise showed eight numbered sets, the session's set total was inflated, and each mini claimed a last-session set that never existed. They're extensions of the set above and now read that way: indented, marked M, and out of the count.
- **A 4-rep myo mini no longer rewrote your set of twelve** — The same problem reached your logs. The summary each lift is remembered by takes the lowest reps you held at the top weight — and it was counting the mini-sets, so twelve reps of lateral raises were recorded as four, and the next session's target was cut to match. Drop sets did the same through their stripped load. Extensions are now excluded from that summary, which is also what your next opening weight is built from.
- **A carried-over weight says it's carried over** — When a lift only has one recorded weight rather than a set-by-set record, that figure stands in for every set — but it looked exactly like a real per-set record, so set five appeared to report a set you may never have done. It's now tagged LIFT.

## v3.9.2 — build 492

- **The set you just finished waits for you** — Ticking a set sent the workout straight on to the next one — and asked how hard the last one was inside the row it had just dimmed and moved past. The set you've finished now stays lit until you've said what it felt like, and says plainly that it's optional. The next set goes live at the same moment, so ignoring the question costs you nothing but reaching for the steppers.
- **The effort a set was asked for stays on the set** — The target — 2 RIR, 85%, to failure — vanished the instant you logged the set, which is the exact moment you're asked what it actually felt like. It stays put now, so the number you're comparing against is still on screen. Logged efforts on earlier sets collapse to a tag you can tap to change.

## v3.9.1 — build 491

- **One coach on the workout screen, not two** — The session had two coach panels stacked at the top saying different things in the same voice — a strip explaining what the block is asking of you this week, and a card explaining what the app changed about today's numbers for you specifically. They're one card now. The block's note is still there, at the end of the coach note, and it's the first thing you read when there's nothing personal to say yet.
- **Plan info reads as one screen** — "How this was built", "Weekly volume" and "Block plan" had each been built with their own card style — different corners, different backgrounds, different heading sizes — which made three sections of the same reference look like three unrelated things. They share one card now.

## v3.9.0 — build 490

- **Hold the + or − to run the weight up** — Getting from an empty bar to a working weight was around eighteen taps. Press and hold either stepper — weight or reps — and it repeats, starting slow enough to stop on the number you want and speeding up the longer you hold. A single tap still moves exactly one increment.

## v3.8.1 — build 489

- **Your record is your best set, not your worst** — Bench 225×6 and then 225×5, and the record read 225×5. The app keeps a cautious summary of each lift — the reps you held across every set — because that's the right number for deciding your next load. It was also being used as the set you're remembered by, where it reports a set you already beat. Records and PR estimates now both come from your best logged set.

## v3.8.0 — build 488

- **Editing a program waits for you to save it** — Swapping an exercise used to commit straight to your saved program — no button press, no warning on the way out. Changes are now held as a draft: the Save button tells you when something's pending, going back asks whether to keep or discard, and the version in your library doesn't change until you say so. The draft is written to your device as you edit, so it survives closing the app or a crash mid-edit — reopen and it's still there, still asking.

## v3.7.0 — build 487

- **The set you're on reads the way it used to** — The target, the effort it's asked at and the set you're beating are stacked in one column again, and the plate breakdown is back under the weight it describes. Both still appear only on the set you're working. The highlight now runs the full width of the screen instead of floating as an inset card, and the set number has room to breathe.
- **A lift with one recorded weight uses it for every set** — If the app only had a summary of a lift — one weight and rep count, which is what older saves and carried-over lifts store — only your first set showed it. Set two onward read "new", as if the same lift in the same session had no history. That summary now stands as the reference for every working set.

## v3.6.3 — build 486

- **Day menu fix, without the extra dependency** — The previous fix worked but pulled in a library the single-file build can't load. Same result, no new dependency: the menu is now rendered above the day list instead of inside a day card.

## v3.6.2 — build 485

- **The set you're on reads as one line again** — The last update put the effort target, technique cue, where the weight came from and last session's set on separate lines — but only on the set you're working, which made that row the tallest on screen and shifted every row below it each time you logged a set. Same information, now a single strip under the row. The plate breakdown moved into it too, so it's back and it's with the rest of what that set needs.

## v3.6.1 — build 484

- **The day menu opens where you tapped** — The last fix wasn't enough: the menu could still vanish on a collapsed day, or open far down the screen on an open one. A performance optimisation on the day card was quietly redefining what "anchored to the screen" meant, so the menu was being placed relative to the card instead of the window. It now renders outside the card entirely.

## v3.6.0 — build 483

- **Tapping your next workout shows just that workout** — It used to open your whole program — every day of the split, every week — and leave you to find the session you were already being pointed at. Now it opens the day itself: what's in it, what each lift asks for, your pinned notes, and Start. The full program is one tap away under "View full program", and Back brings you straight back here.

## v3.5.1 — build 482

- **The day menu no longer gets cut off** — On a collapsed day, the 3-dot menu was clipped by the day's own card. It now opens against the screen, and flips above the button when it would run off the bottom.
- **You can decline an engine update** — The offer to rebuild a program on a newer engine had no "no". It does now — "Keep it as it is" dismisses it for good on that program. A future engine will ask again, since a new correction is a new question, and Plan info keeps a way to change your mind.

## v3.5.0 — build 481

- **The workout screen shows the set you're on** — Every set row looked the same, so finding your place mid-workout meant reading the table. The set you're on now keeps full contrast and an accent rule; the rest dim. The plate breakdown, the effort target, the technique cue and last session's set follow that row instead of appearing under all six at once — same information, arriving as you reach it.
- **Warm-ups get out of the way once they're done** — A ramp set carried the same full-size row as a top set, four of them above the work. Once they're all logged they collapse to one line. Tap it to bring them back — a mis-logged ramp is still fixable.
- **The coach note shrinks once you start lifting** — It's the card you want before the first set and a banner you're reading past by the third. After your first logged set it becomes a one-line pill, still a tap from the full note.
- **Plan info moved off the top of your program** — "How this was built", "Weekly volume" and "Block plan" now live behind a Plan button next to the day selector. All three are unchanged — they're reference you consult, not something you read on the way to training.
- **A saved program stops looking like a construction site** — While you're building, the day controls stay out as buttons. Once saved, rename and duplicate collapse into a 3-dot menu — which also gives Delete a home it never had here. The exercise rows lost their up/down arrows (the drag handle already did that job), the RIR tooltip, and the pinned-note readout; notes still show during the session, where you act on them.
- **Leaving an unsaved program asks first** — Back used to discard a freshly built program silently. It now offers to save it.

## v3.4.0 — build 480

- **Update an older program to the newest engine** — A program keeps the version of the training engine it was built on, so its prescriptions never shift underneath you mid-block. That protection had no exit: a program built last month could never receive a correction made since, including corrections the research forced. Any program built on an earlier engine now says so, lists in plain language what updating would change, and rebuilds on request using the same seed — so everything that changes is the update and nothing is a reshuffle. Your logged sets are untouched, and the version you had waits under Earlier shuffles if you want it back.
- **What the volume ceiling actually means** — Weekly sets above the ceiling were labelled junk volume. The best current evidence — a 2026 analysis of 67 studies — finds size and strength still improving as weekly sets rise, with diminishing returns rather than a point where more starts to hurt. The ceiling is a recovery budget worth watching when progress or readiness slides, not a line past which your sets stopped counting, and it now says so. Same numbers, same colors, honest claim. The same analysis confirmed that counting an indirect set as half a set predicts growth best, which is what this app already does.

## v3.3.4 — build 479

- **Strength and peak blocks lift the barbell** — A strength or peak block was given its heavy sets and reps on whichever lift suited muscle growth best — so a full-gym strength program could be built on a dumbbell bench and a hack squat, with no barbell bench, squat or deadlift anywhere in it. The main lift of a strength-focused day is now the barbell you can add weight to and test. Hypertrophy blocks are unchanged, and every block in a cycle shares the same main lift so your logged weights still carry forward.
- **Ban an exercise from a saved program** — Banning a lift from the exercise menu was only offered while a program was still unsaved — which meant it never appeared for a training cycle at all, since a cycle saves its blocks the moment it's created. It's now available wherever the menu is.
- **The clock stops cutting your side delts** — When a session ran long, the exercise it dropped to fit the time was chosen by looking at the whole shoulder total — a number your pressing already fills out. So the last lateral raise in the day looked free to cut, and side or rear delt work could disappear from a program entirely even though it had just been added on purpose. Trimming for time now knows the difference between the three heads. Across a 1,458-program sweep, programs training no side delts at all fell from 33 to 18, and side and rear delt work below the minimum effective dose fell by about a fifth.

## v3.3.3 — build 478

- **Bench angles match the day** — A day called "Push · Chest" could be built around an incline press while the flat bench landed on the delt day, and "Incline + Pull" could get a flat bench. Days that name an angle now anchor on the right variant.
- **Myo-reps you can actually log** — When a block prescribes myo-reps, the mini-sets now appear in your set list instead of only being mentioned in a note — so those reps count toward your volume and estimates.
- **Report a bug** — Settings has a Report a bug section that emails pursuitiron@gmail.com with your version and build already filled in.

## v3.3.2 — build 477

- **No more empty training days** — With a very limited equipment list and bodyweight excluded, a program could generate a day with no exercises in it at all. The fallback that fills a day only ever considered the first muscle it wanted; it now tries everything the day was meant to cover.

## v3.3.1 — build 476

- **"Train this less" no longer means "barely at all"** — De-emphasising a muscle was cutting it by around 90% — chest went from about 11 sets a week to 1, which is below the level needed to hold what you've built. It now keeps a maintenance dose and gives the rest of the room to what you actually asked for.

## v3.3.0 — build 475

- **Session estimates count your warm-ups** — Longer warm-ups for heavy lifts weren't being counted in the session time estimate, so a session budgeted at 90 minutes could really take 100 or more — and because the app fills sessions up to that estimate, it was overfilling them. Warm-up time is now costed from the actual ramp.

## v3.2.9 — build 474

- **Two muscles were being counted twice** — Reverse Curl and Curtsy Lunge each credited their own main muscle a second time as a supporting one, inflating that muscle's weekly volume by half. That made a muscle look like it had met its target when it hadn't, or trip a ceiling it shouldn't have.

## v3.2.8 — build 473

- **Full Body described accurately** — The Full Body split said it trains everything each session. It doesn't — it rotates, covering several movement patterns a day so every muscle gets trained two or more times a week. That's the better design, and now it's what the description says.

## v3.2.7 — build 472

- **Template labels match what they build** — Two templates were labelled as pure strength programs while being configured for strength and size. Every template's day count, goal and structure is now checked against what it actually generates.

## v3.2.6 — build 470

- **Warm-ups that match the weight** — How many warm-up sets a lift got depended on where it sat in the session, not on how heavy it was — so a 495 lb squat in the second slot ramped straight from 345 to 495 in one jump, and the same lift at 135 got the identical ramp. Warm-ups now scale with the actual load, so heavy work is approached properly and light work isn't padded.
- **Bad data can't become a prescription** — A damaged or hand-edited backup could put an impossible weight into your history — negative, or absurdly large — and the app would hand it straight back as the weight to load next session. Those rows are now ignored the same way a missing one is.

## v3.2.5 — build 469

- **Delt coverage across every split** — The side and rear delt work added recently was only ever verified on a few splits. Checking all eighteen found programs that trained neither head at all — mostly on shorter sessions, where there's no room to add anything. Those now convert one front-delt press, which the rest of your pressing already covers, into the work that's actually missing.

## v3.2.4 — build 468

- **Side and rear delts now hit their target** — Lateral and rear-delt work is spread across two days instead of stacked onto one. Stacking meant a later step that merges duplicate isolations quietly folded them back together and lost sets — so side delts fell short in nearly every program even though the app thought it had fixed them. They now clear their target, and splitting the work across days suits delts better anyway.

## v3.2.3 — build 467

- **Side and rear delts survive a strength block** — The check that guarantees side and rear delt work only ran on hypertrophy goals — and on a strength goal every shoulder slot fills with presses, so a powerbuilding peak block could end up with three pressing movements and no lateral or rear work at all. It now runs whatever your goal is.
- **Sessions closer to the length you picked** — Choosing "90 to 120 minutes" was producing 65-minute sessions, because session length set an exercise count that was never checked against the actual duration. Days now fill toward the bottom of the range you chose, spending the extra time on whichever muscle is furthest below its target.
- **Swaps offer what your program is missing** — Swapping an exercise now floats options that fill a gap in your current program to the top — so replacing an overhead press suggests a lateral raise when your side delts are short, instead of listing every other press first.
- **Barbell limit label fixed** — The program summary said "Barbell cap 1 lb". It's a limit on barbell movements per session, not a weight, and now says so.

## v3.2.2 — build 466

- **See how a program was built** — The choices you made when you created a program — what to emphasise, what to cut back, session length, block length, deload — are now shown on the program screen under "How this was built", and the emphasis and reduction choices appear on each card in your library. Those two in particular change your set counts without changing the exercise list, so there was previously no way to confirm they'd been applied.
- **Strength standards render fix** — The strength standards section could fail to load for some histories. Fixed, and when a section does fail it now names which part of the app it happened in.

## v3.2.1 — build 465

- **Side and rear delts stop getting skipped** — Shoulder volume was being judged as one number, and that number looked healthy while the three heads underneath it were badly out of balance — front delts well past their useful ceiling from all the pressing, side and rear delts below the minimum to grow in almost every program. Each head is now checked against its own targets, so the ones that only grow from deliberate work actually get it.

## v3.2.0 — build 464

- **Volume that stays inside the lines** — High-frequency plans were pushing delts and triceps past the point where extra sets cost recovery without buying growth — worst case a third over the ceiling, and it got worse the more days you trained. Every press was quietly adding shoulder volume the plan wasn't counting. Programs now check both ends: enough work to grow, not so much that you're training your own fatigue.

## v3.1.10 — build 464

- **Every muscle gets enough work** — Generated programs now guarantee each muscle clears its minimum effective volume for the week. Calves were the worst offender — they came in under the minimum in about four of every five programs, at any training frequency. The fix respects your time limit and any muscle you've asked for less of, and it won't push another muscle past its recoverable ceiling to get there.

## v3.1.9 — build 462

- **The plan's volume is the volume you train** — Recovery used to compare your session against the AVERAGE gap between times a muscle is trained — a spacing your split may never actually schedule. On any uneven rotation that made the plan's own session look early, and a set got trimmed from a day the program deliberately chose. It now uses the tightest gap the rotation really produces, so following the plan means training the volume it designed. Train sooner than that and it still adjusts.

## v3.1.8 — build 461

- **It knows its own programming** — A block that programs near-failure work no longer reports it as you overreaching. It still trims an accessory set when you train hard on one day's rest — that's the point of autoregulation — but it names the block instead of telling you that you pushed too hard, because on an intensification week you did exactly what was asked.

## v3.1.7 — build 459

- **History grouped by month** — Your session list now breaks into months with a sticky heading that carries that month's session count and total volume — so scrolling back through a year tells you something instead of just going on forever.
- **Fixes** — Programs failed to open once you had a few sessions logged — that's fixed. The unit you pick in Settings is no longer asked for again when you build a program, gym equipment is sorted alphabetically so the two hip machines sit together, and the Back and Done buttons no longer collide with the text beside them.

## v3.1.6 — build 454

- **Find things in Settings** — Settings jumps straight to a section now instead of making you scroll past everything — Training, Appearance, Workout, Profile, Data, Backup, About. And an empty Progress tab offers your next workout by name rather than just telling you it's empty.

## v3.1.5 — build 453

- **Program setup you can cut short** — The builder now shows which question you're on out of how many, and once you've set your equipment you can generate straight away — the remaining questions all have sensible defaults and are there to refine, not to finish.

## v3.1.4 — build 452

- **The warm-up card remembers you** — If you have your own warm-up, tap Hide once and the checklist stays gone — it used to come back at the top of every single session. Settings can bring it back. Browsing templates also puts the recommended ones first now.

## v3.1.3 — build 450

- **Plateaus counted per day** — If a lift appears on two days of your program — heavy on one, higher reps on the other — the lighter day's sets were being counted as sessions without a PR, so the app called a plateau on a lift that was progressing fine. Stalls are now tracked per training day.
- **Better share cards** — Your top set now breaks ties on reps, so logging 195×4 and then 195×6 shares the 195×6. Shared images also carry the Pursuit Iron name.

## v3.1.2 — build 449

- **Machine settings are back** — Pin the seat, back pad, thigh pad, ankle pad and start position for a machine and the app remembers them for next time. This had quietly stopped working on most machines — leg curls, leg extensions, pec decks, chest presses and more offered no settings at all. All 116 machine exercises have them again, and only the ones each machine actually has.

## v3.1.1 — build 448

- **Back always gets you out** — Opening a cycle block, jumping to its cycle, then opening a block from there could trap the back button bouncing between the two screens with no way home. Navigation now remembers the whole path you took instead of just the last step.

## v3.1.0 — build 447

- **A home screen that answers the question** — Today's workout now sits at the top, above the streak and totals — the thing you opened the app for is the first thing you see. The template carousel retires itself once you're a few sessions into a program, so your own library isn't buried under suggestions you've already passed on.
- **Easier to hit, easier to see** — Small controls — the +/− steppers, reorder arrows, sheet close buttons — now have a full-size touch area even though they still look the same, so you miss them less with a thumb mid-set. Sheets close with Escape, announce themselves properly, and there's a visible focus outline if you're using a keyboard.
- **Templates named for what they do** — Program templates now carry descriptive names — Main-Lift Waves, Tiered Linear Progression, Ramping 5×5, Straight 5×5 — instead of borrowing coaches' and brands' names. The programming is unchanged: same percentages, same progression, same saved programs and share codes. Only the labels moved.
- **Text you can actually read** — Every color the app uses as text was measured against the surface behind it, and the ones that fell short were corrected — in all fourteen themes. Secondary labels, the amber warnings and the accent links are all legible now, including on the light themes where they were faintest. The themes still look like themselves.
- **Your birthday, not your age** — Strength standards scale by age, and a typed-in age quietly went stale — a lifter who entered 39 kept being scored against 39-year-olds for years. Enter a birthday once and it stays right. If you'd set an age before, it's been carried over as an estimate; set the real date and it'll sharpen.
- **Bodyweight tracking that keeps up** — Weigh-ins now hold about five and a half years instead of six months, and each one remembers the unit it was taken in — so switching between lb and kg, or merging two devices, no longer shows a phantom gain or loss. Body measurements convert properly between inches and centimeters too.

## v3.0 — build 441

- **Fits smaller screens properly** — The workout screen now rearranges itself on compact and folded phones — the weight and rep steppers drop to their own line instead of getting squeezed, the elapsed timer keeps its full readout, and Finish shrinks to a check so nothing runs off the edge. Every bottom button clears the gesture bar too.
- **Set up each gym you train in** — Your commercial gym, your garage, the hotel rack — each one keeps its own equipment list, and switching gyms rebuilds what you're offered. Programs are built for the place you're actually standing in.
- **Tell it the weights you own** — Enter your real dumbbell rack — 5, 10, 15 or up to 100 — and it stops asking for loads that don't exist. At the heaviest bell it holds the weight and builds reps instead of inventing a 62.5.
- **Plateau warnings that mean it** — A stall is now judged per training day, so the same lift on push day and leg day is never mixed into one misleading line. And working through a rep range set by set counts as progress, because it is.
- **Share a session** — Any workout in Progress becomes a clean card — your lifts, top sets, volume and time.
- **History where you need it** — Open any exercise in a program to see every session you've logged for it on that day, with your best marked.

