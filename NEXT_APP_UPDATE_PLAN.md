## Build 942 / M396 — Loading tools, complete input and gym unit integrity

Continue from published Build 941 / PR #369, merge cdab546087e79cceefba6f4bc4c2584616319790. All post-merge workflows and the actual public online/offline/manual-target/resume and estimate journeys passed. Keep the user's substantial-batch preference.

Reproduce on Build 941: calculator target 92..5 renders plates for 92; fractional 1RM reps are stripped into another integer; zero available pairs produce an unexplained dash. The gym load-unit action changes the unit token without converting limits or racks. normalizeGyms also discards updatedAt, removing the clock needed to identify newer gym edits. Preserve these failing cases before changing the owners.

Move the calculator to modules/loading-calculator.js and the gym numeric fields to modules/gym-load-fields.js. Use the shared SessionChoiceDialog, UiChoice and UiAction, retaining the original sheet gesture through an optional panel ref. One scrolling body keeps the calculator title/Close reachable. Relative type, adaptable choices, complete labels and themed status/results work at 320/393/834/1280px and actual 200% text. The narrow enlarged-text replay catches and repairs heading/selector clipping. Real touch dismissal and return focus retain the existing motion/focus owners; Workout/Focus ledger geometry stays protected.

modules/training-domain/loading-tools.js owns complete numeric parsing and read-only calculator projections, reusing production platesPerSide and e1rm. Invalid raw typing cannot turn into a lower weight, integer or partial rack. 1RM entry uses the existing model's 1–12 rep range instead of silently capping a larger entry. Percentage loads retain the original downward 5 lb/2.5 kg rounding and can open the plate view in one action. The plate view always states the actual achievable load, including empty inventories and below-bar targets.

The existing gym list remains the draft and its existing Done callback remains the writer. Raw rack/bar/limit/count fields survive edits until validation; invalid Done focuses the affected field and opens its disclosure. Convert all racks and limits together with exact history-unit conversion; bar masses keep their separate unit context. Retain zero Smith bars, unedited precision, inactive-gym values and explicit plate inventories. The transient inventoryText property never becomes saved gym data. Preserve the source gym's updatedAt through normalization. No generation, progression, set-entry, history or recovery policy is replaced.

Retire forty calculator inline objects and retain one computed plate height: combined enforced presentation budget 2,123 → 2,084. External/embedded/repeated priority ceilings stay 2,073/1,053/14. Include both new view modules in the scanner. App.js shrinks by about 14.4 kB at the implementation snapshot; this is source ownership cleanup, not a measured download or startup claim.

Extend B846/B831/B875 and add M396 domain/browser gates to the existing registry. Require all source groups, full 13-stage lifecycle audit, independent engine export/parity, all seven browser groups and release/offline identity checks on the final candidate. Publish one Build 942, then require exact hosted-file parity plus actual public online/offline/calculator/gym/typed-workout-resume replays. Physical Android/IME/TalkBack/interruption/foldable acceptance remains open.

Next: continue cohesive Program editing and inspection families and remaining shared forms; prioritize reproduced prescription, persistence and interaction defects. The broader source refactor and launch/device acceptance remain in progress.

## Build 941 / M395 — Reviewed web engine patch

Integrate the user-provided web-engine-changes.patch after Build 940 publication. The attachment repeats the recovery diff and assumes a plate-count helper absent from this branch; apply recovery once and adapt the plate helpers to the maintained source.

Starting loads are explicitly labelled heuristics, used only when the planned lift has no positive progression target. Prefer recent completed own/related sets; normalize revisions, use numeric history dates, retain precise lb/kg conversion and accept RIR zero. Use the latest completed exposure within 90 days rather than an all-time maximum. Connect bodyweight, units, experience and sex from the existing App state. Snap estimates through the existing equipment grid and respect gym ceilings. Percentage prescriptions, declined changes, session replacements, entered values and resumed workouts retain their existing authority. The label asks the user to check the first set; it does not promise automatic calibration that has not occurred.

Scale the recovery dip by the session's existing effort cost. Six on-plan hard sets keep the full dip; one light exposure produces a smaller dip. Preserve elapsed/measured recovery timing and effort calibration. This is a heuristic model change, not measured physiological recovery.

Retain legacy injury preferences, add optional descriptive severity and preview the selected preference. Legacy severity remains unknown: choosing to skip lifts is not a report of sharp pain. Severity can seed a preference, and the user can change it. Saving an injury does not automatically modify the workout; existing exercise review and explicit Apply remain required. Preserve completed sets, source linking, retry idempotence and authored preferences.

Save per-unit plate-pair counts on the gym, preserve them through normalization, and expose staged count editing under the existing gym form. Use a bounded solver for plate math so it cannot invent pairs or miss a reachable combination after a greedy dead end. An explicit empty inventory stays empty. Existing callers without pair counts retain size-only plate behavior.

Extend B846 and B889 behavioral/browser gates and add M395 evidence/seed/recovery coverage to the owned registry. Require source, lifecycle, independent export, all seven browser groups and release identity gates before merge, then verify hosted hashes and online/offline/resume behavior. Engine generator version remains 0.65.9; this app-domain policy release is Build 941. Physical Android/IME/TalkBack/interruption/foldable acceptance remains open.

## Build 940 / M394 — Authored targets, Program rows and prescription integrity

Continue from Build 939 / PR #367, merge 645cc96807f3c8bf78e875e2bfa487397803fda4. Keep the user's larger-batch preference: publish related improvements and their verification together.

Reproduce two defects before fixing them: clearing Sets inserts 1, and the rep target 10-20oops becomes 10. A follow-up replay also finds invalid cells being interpreted as a neutral 8–12 range by the progression reader. Keep raw target typing in one local presentation component until Apply. Validate complete whole-number sets (1–20) and positive whole rep counts/ranges in the canonical domain. The atomic manual-target writer marks only semantically changed fields as user-owned, preserves untouched weekly set arrays, and retains rest, RIR, techniques, rosters and history. Display the selected week's scalar set count and a canonical rep range instead of serializing stored arrays into inputs. Cancel writes nothing. Pending target editors block Review, Start, Back, week/day changes and Settings until Apply or Cancel.

Reject malformed authored/engine-owned rep ranges at canonical cell projection; they cannot produce working sets or progression decisions. Historical rep snapshots use the same complete parser. The shared App launch owner refuses invalid targets without saving or starting a partial workout. An upgrade replay reproduces the initial guard blocking a pre-existing valid workout whose saved plan is malformed. Restrict the new-prescription guard to new starts; the existing resume path retains its persisted rows and user-owned values. The added M222 recovery case preserves 190 lb × 5 and every recorded row. Preview uses its existing primary action to return directly to Program for repair. Preserve valid numeric programming, loading, progression thresholds, recorded workout facts and resume ownership.

Move the exercise-row shell, grip, heading, metadata, badges and actions to shared CSS. Manual targets use labelled, touchable fields beneath the exercise, native form submission, inline errors and shared actions. The existing M222 browser gate covers dark/light, 320/393/834/1280px, actual 200% text in a short viewport, pending-input guards, exact review/save/reload, untouched 3/4/5/6 weekly sets, matching Program/Preview/live week-three targets, persisted-invalid repair and Home launch refusal. Retain the existing settings, Cable Fly DP/DDP and protected-resume cases. Lower external priorities 2,106 → 2,073 and combined inline objects 2,141 → 2,123; embedded 1,053 and repeated priorities 14 stay unchanged. Include the new view module in the debt scanner.

Require final source, lifecycle, independent-engine, seven browser groups and release checks; publish one Build 940, then verify exact hosted bytes and online/offline/resume journeys. Physical Android/IME/TalkBack/interruption/foldable acceptance remains open. Continue the broader UI/UX and engine audit in cohesive passes.

October 27 release follow-through reproduces a further data-entry defect: native Back bypasses Program's on-screen Back action and discards pending raw targets. Nominate the existing Program Back action in the native navigation bridge so it retains the same target guard and saved-draft review. Extend M222 with the failing native-Back case, exact raw-text/focus/data preservation and Keep editing. No second navigation, persistence or prescription owner. Keep these changes in Build 940; align the release milestone and changelog before final certification.

## Build 939 / M393 — Library discovery and detail batch

**Release preference:** The user requested larger coherent batches on October 27. Finish related improvements and verification together; do not create a new build for each small change.

Continue from published Build 938 / PR #366, merge 4af05ccf8392773ea8afa4865f6993752a9e6a5c. All candidate/post-merge workflows and public dark/light online/offline/resume journeys passed, with 133/133 hosted hashes matching.

R54 reproduces the reported Library browsing defect on unchanged Build 938: its five-row pinned header is 290px tall at 393px, leaving 65.96% of the Library viewport for results. Move search and the three filter rows into the existing results scroll owner. Keep a compact toolbar with Back, Library, Find and Compare. The final 320/393px dark/light fixtures measure a 65px toolbar and 92.37% reading space after scrolling. Find returns the actual scrolling owner(s) to search and focuses the existing input without resetting filters. Preserve filter restoration, Compare/Back scroll memory, selected-chip visibility and the wide list/detail pane.

Consolidate the complete LibraryView presentation into one semantic CSS family: catalog, empty states, detail chrome, notes, history, records, metadata, movement variants, technique/demo and goal controls. Retire 115 static inline objects and superseded external/embedded Library layers. The only remaining Library inline object is the calculated goal progress width. Tighten combined inline 2,256 → 2,141; external priorities 2,173 → 2,106; embedded 1,134 → 1,053; repeated priorities remain 14. Preserve shared motion, palette, focus, selected-state, content-visibility and adaptive-pane owners. No new runtime module or parallel data/engine owner.

The same task replay reproduces a goal defect: entering 92..5 stores 92. Validate a complete finite positive decimal before Save, retain invalid raw typing, and keep updates with the existing onSetGoal writer. Give the form a visible unit label, shared actions, Cancel and explicit Remove goal. Clear staged goal text when selecting another exercise. R54 checks invalid/empty input, exact 212.5 lb save, Cancel, Remove, exercise isolation, Ban/Unban, larger text and reachable actions in a short phone window; authored training and logged facts remain unchanged. R74 protects reading space at enlarged text, and R65 selects the actual second exercise independently of the added controls node.

Require final-head source/lifecycle/independent/seven-browser/release gates, then hosted-byte parity and public online/offline/resume checks. Physical Android/IME/TalkBack/interruption/foldable acceptance stays open. The broader refactor continues with Program and other cohesive surfaces; this batch does not claim full launch acceptance.

## Build 938 / M392 — Settings choice and theme ownership

Continue after Build 937 / PR #365 merged as 7c8664eb0f659ad928177b0c7433349c0d839872. All five candidate workflows passed, including the seven browser groups. Deployed verification is recorded separately.

The extended existing R76 fixture fails on unchanged Build 937: every theme label stays 11.5px when actual document text doubles. The earlier per-element enlargement fixture could not detect that fixed-size defect. Move theme presentation from the embedded stylesheet/inline objects into the Settings CSS family, use relative type and a text-relative grid, and reuse UiChoice. Preserve the native disclosure, all palette values and existing theme callback; only the two real swatch colors remain inline.

Six preference groups retain their exact callbacks and measured moving indicator, with explicit accessible group names and shared class-owned presentation. Remove superseded theme rules, obsolete preference font/layout overrides, and inline scroll/anchor spacing. Measured combined inline objects 2,284 → 2,256; external priorities 2,188 → 2,173; embedded priorities 1,143 → 1,134; repeated priorities remain 14. No new module, storage schema, engine formula, history writer or gesture owner.

R76 covers both palettes at 320/393/900/1280px, actual 200% root text in the expanded theme chooser, complete labels and usable targets, keyboard theme selection and exact persisted preference values, reversal, and authored content/logged facts. Changing then restoring experience legitimately advances the existing program modification timestamp; the training-content comparison explicitly excludes only that timestamp. Preserve all existing Profile/Progress/Settings enlargement coverage, and run source/lifecycle/independent/seven-browser/release gates on the final candidate before merge. Require hosted hash parity and a real offline browser journey afterward.

Next: remaining Program/Library detail/editor families and source retirement, then measured device acceptance. Physical Android/IME/TalkBack/interruption/foldable checks remain open. Do not call the entire refactor or launch acceptance complete from browser passes.

## Build 935 — publication resumption and remaining refactor

The user explicitly authorized publication to the existing Pursuit Iron destination on October 26, followed by a review of the remaining refactor. This resolves the publication-scope blocker recorded historically under M388/M389 below. Resume existing PR #362, preserving its M387 work and adding the complete M388/M389 candidate. The release remains Build 935 / Engine 0.65.9. Require exact-head source, lifecycle, independent-engine, seven-browser and release checks before merge; verify the deployed files and public offline execution afterward.

Recovered candidate source matches its certified 124 runtime / eight UI hashes and 134 offline entries. The prior workspace's Git backing was no longer available; a fixed source snapshot was retained for verification and publication. Do not infer that a Git checkout with the correct release label contains the complete candidate.

Measured remaining presentation debt at this snapshot: 2,303 combined inline style objects (2,291 in App), 2,188 external priorities, 1,143 embedded priorities and 14 repeated priorities. App is 2,079,620 characters / 2,085,472 bytes and 21,771 lines. These counts include calculated runtime geometry and are not a list of styles that can all be deleted. Relative to published Build 933, the complete 934–935 slice retires 123 inline objects and 170 priorities.

| Existing owner | Inline style objects in function | Next bounded work |
| --- | ---: | --- |
| ProgramView | 345 | Continue compact configuration and exercise inspection families with staged Save/Cancel and canonical prescription readers. |
| LibraryView | 135 | Move search/filter/detail presentation into shared roles; preserve filter context, swaps and equipment constraints. |
| SettingsView | 118 | Consolidate remaining category/field/action styling; preserve storage, backup and current values. |
| CompareView | 81 | Readable side-by-side program comparison with canonical values and stable enlarged-text layout. |
| Home | 71 | Retire remaining presentation only after proving pointer targets and native scroll stay stable. |
| SwapSheet / AddSheet | 62 / 40 | Share search/result/selection structure without changing roster transactions. |
| WorkoutSession | 566 | Keep logging geometry protected. Change only bounded families with set-entry, rest, Focus, interruption and persistence replay. |

These are AST counts within each top-level function, including nested presentation; they must not be summed into a second global budget. Prioritize reproduced data/prescription defects, then Program/Library/Settings families. For each slice, remove superseded rules in the same change, lower the existing ceilings, and compare real rendered states. No second engine, style system, history ledger or menu/gesture owner.

Physical Android keyboard/IME, TalkBack, touch/Back, OS interruptions and foldable/landscape acceptance remain open. Automated browser checks do not complete that device gate. The CSS/inline reduction target is still in progress; this is not completion of the full source refactor.

## M387 — stable pointer targets, natural Home flow and shared menus (Build 934)

Continue from published Build 933 (PR #361, merge f9e086f). Its 15 candidate checks, all 19 post-merge checks after one unchanged integration retry, 131 hosted hashes and four actual public offline training-max journeys passed. The first post-merge job exposed two navigation timeouts on a slow runner; do not dismiss them as infrastructure without reproducing the interaction.

The extended existing B887 fixture fails on unchanged Build 933: after scrolling Program, metadata is 17px tall in the first frame and 0px in the following frame. A separate real-pointer handoff captures Home's program target at y=431.875, then finds it at y=371.875 after the scroll event. A click using the original coordinates can miss the button. Day actions also stay 14px when the document text size doubles. Preserve these failing reproductions before changing the owners.

Move Home's supporting brand/date/greeting into the existing scrolling content, retaining the shared fixed app bar and the initial hierarchy. Delete the height/opacity clipping effect, its refs and its compact-state marker. Context now moves once with native scrolling instead of changing the scroll viewport underneath a located target. Program retains its compact metadata behavior but applies it directly in the scroll event, with natural-content resize observation for text/title reflow. The new pointer test clicks the coordinates captured before the next frame; it does not wait away a displacement or replace the action with a DOM click.

modules/ui-menu.js replaces all three menu renderers: Home program actions, Program day actions and the day picker. Screens keep the same state/actions and pass a live anchor. The existing ui-motion menu owner measures untransformed row size and actual anchor geometry, respects the visual viewport, clears the live dock/navigation, and coalesces resize/scroll work. Register/clean up per-menu size observers; no second positioning or keyboard system. The common renderer portals into the existing app root, focuses the first item, preserves Arrow/Home/End, reveals focused overflow rows without scrolling the background, and restores the anchor on dismissal. Tab closes the menu. Preserve complete day labels, selected/done/next facts, disabled actions and all program transactions.

Replace the competing menu stylesheet layers with one themed, scalable family. Remove 45 external and 15 embedded priorities; lower 2303 → 2258 external, 1198 → 1183 embedded, 15 → 14 repeated declarations, and 2426 → 2410 inline styles. Retire the old three renderers, guessed heights, stored rectangles and duplicate menu-opener tracking. Menu placement remains measured geometry, with no inline presentation objects. Engine 0.65.9, generation/progression policy, storage schema, workout logger values and historical prescriptions are unchanged.

Existing B887 covers native and simulated 4× narrow-phone pointer handoff plus 320/393/900px dark/light layouts; three menu kinds, 100/200% text, short-window resize, long labels, keyboard/focus, Delete Cancel and immutable saved/history/cycle/draft data. R53 now checks actual context visibility and the fixed compact app bar instead of the retired compact flag. B831 retains AA contrast, connected menu shapes, untransformed size-read coalescing, actual dock clearance, motion and keyboard behavior. Keep Home hierarchy, created-cycle/preview/day-delete, source/lifecycle/independent/all-seven-browser and release gates. Require exact deployed bytes and public offline replay after merge; retain the 173-entry verification registry.

Next: continue the remaining cohesive Library/Program surfaces and measured resilience work. Prioritize reproduced prescription/persistence or interaction defects. Broader debt targets and physical Android/IME/TalkBack/interruption acceptance remain open.

## M386 — safe training-max input, staged editing and truthful review (Build 933)

Continue from published Build 932 (PR #360, merge 87d4afa). Its 15 candidate and 19 post-merge checks passed, all 130 hosted file hashes matched, and both public offline note journeys passed with 131 cached entries.

The existing M225 browser owner reproduces an S1 defect on unchanged Build 932: typing 212.5 in Training maxes produces 2125. Parsing the input into the program on each keystroke removes the intermediate decimal point; the next digit becomes part of the integer. The old editor also silently strips malformed text. The extended B905 review contract fails separately: a training-max-only change produces no before/after details and is summarized only as Program details.

Move the training-max sheet, inline 1RM conversion and entry-card presentation into modules/program-training-maxes.js. Hold raw numeric text locally and validate finite, nonnegative decimal values without sanitizing invalid text into another number. Apply calls ProgramView's existing draft setter through withTrainingMax only for changed fields. Cancel/Close/backdrop/drag dismissal leaves the program draft unchanged. Program review and Save remain the persistence boundary. Preserve the existing 90% conversion, canonical loadStep/roundTo behavior, percentagePlanFor previews and stored-unit conversion. Do not roundtrip untouched maxes. Back/Escape closes the inline conversion before closing the editor, using the existing system-back and focus owners.

The existing reviewProgramChanges projection now lists exact training-max values with units and explicit Not set for clearing. The existing programChangeLabels catalog names Training maxes. This is review presentation; generation, percentage/progression formulas and Engine 0.65.9 policy remain unchanged. Use one semantic CSS family with labelled 48px numeric controls, relative type, wrapping rows and a fixed heading/footer around one scrolling body. Retire 27 inline styles, tightening 2453 → 2426; external/embedded/repeated priorities remain 2303/1198/15.

Extend the existing M225 production-router fixture with decimal lb/kg editing, malformed input, clear, staged previews, Cancel/Escape, nested conversion/focus, canonical 1RM rounding, explicit Apply and reviewed Save, exact units/untouched precision, reload, actual new-workout loads and protected history/minimized set values. Both palettes cover 320/393/834/1280px and 100/200% text, persistent hit-tested actions, forced colors and reduced motion. Keep the existing percentage peak/deload/tiered/resume/finish checks and 173-entry verification registry. Require all exact-head source/lifecycle/independent/seven-browser/release gates, then deployed hash parity and actual public offline replay.

Next: continue remaining Library/Program editing ownership, source/style retirement and measured resilience work. Prioritize reproduced prescription/persistence defects. Broader launch cleanup and physical Android/IME/TalkBack/interruption acceptance remain open.

## M385 — staged pinned notes, setup and shared editing (Build 932)

Continue from published Build 931 (PR #359, merge ae50c1c). Its candidate and post-merge gates, 129-file hosted parity and both actual public offline theme replays passed.

The extended existing R54 fixture reproduces a persistence defect on unchanged Build 931: changing J-hooks from 9.5 to 11.5 writes immediately while note text is staged. Cancel leaves the setup change saved. Three duplicated editors also share uncontrolled textarea refs; Program identifies an active editor only by exercise ID, allowing repeated exercises to open multiple editors. Its editor occupies the narrow exercise-name column rather than the card width.

Move the three renderers into one controlled modules/exercise-notes.js owner. Keep note/setup drafts local until Save and invoke the existing normalized App writers together in the same React event. Cancel only closes; Remove clears the original saved setup keys, including orphans. Keep cleared orphan inputs present until Save so Cancel remains meaningful. Consolidate the three identical root callback pairs, without moving persistence or changing its schema. Scope Program's active editor by day, slot and exercise; saved notes remain global to that exercise. Move the editor below the exercise row so it can use the card width. Retain the canonical equipment-field metadata and the shared UiAction controls.

Use one semantic CSS family for the editor and shared setup display. Inputs have associated visible labels, at least 44px edit targets and scalable type; actions wrap and remain reachable by scrolling at enlarged text. Preserve closed Workout/Focus logging geometry. Retire 29 static inline styles, tightening 2482 → 2453. External priorities remain 2303, embedded priorities 1198 and repeated priorities 15. No training engine, progression, saved prescription or history policy changes.

The existing R54 browser owner now exercises Program, Workout and Library in both palettes: typing without writes, Cancel, Save, saved-field removal, orphan retention/explicit clearing, reload, global cue consistency, repeated-exercise editor identity and unchanged saved prescriptions/history/live set values. Cover 320/393/834/1280px with 100/200% text, labelled 44px inputs, hit-tested reachable Save/Remove/Cancel and visible forced-color borders. Keep its sharing/options/tab contracts and B888 workout choices intact. Require exact-head source, lifecycle, independent engine, all seven browser groups and release/offline checks, then merged Pages hash parity and an actual public offline note journey before publication.

Next: continue cohesive Program/Library editing ownership, tab composition and source-level style retirement; prioritize reproduced prescription/persistence defects. Broader cleanup and physical Android/IME/TalkBack/interruption acceptance remain open.

## M384 — exercise actions, readable controls and working dismissal (Build 931)

Continue Program inspection from published Build 930 (PR #358, merge c413e20). Its full candidate and post-merge gates, 128-file hosted parity and four actual public offline replays passed.

The extended R54 production-browser fixture fails on unchanged Build 930: at 200% text the options title stays 17px, labels 15px and descriptions 13px. The whole sheet scrolls, carrying Close away. Its explicit data-sheet-drag=true also makes sheetDragRef return before binding the established touch handler. Inspecting options has no explicit connected dialog title. Superset actions remain visible when the program disallows them.

Move the existing renderer into modules/program-exercise-options.js, consuming shared UiAction and unchanged App callbacks. ProgramView remains the only program writer; the new owner holds only transient remove confirmation and clears it on every close path. Its labelled heading stays outside one scrolling body. Use relative type and semantic theme roles, native keyboard controls, reduced-motion/forced-color support and the original gesture ref. The shared initializer now tracks bound node identity in a WeakSet, independently of presentation flags; this also repairs premarked gym, switcher and achievement sheets without another gesture implementation. Exclude this bounded sheet from legacy height rules rather than add another priority override. Retire the old private options CSS and embedded padding rule. Keep note, info, why, ban, remove and superset transactions with their existing owners; hide invalid supersets and protect the last exercise.

Measured combined inline styles 2499 → 2482. External priorities 2336 → 2303 and embedded priorities 1199 → 1198; repeated priorities stay 15. Include the new module in the guard and tighten all affected ceilings. Combined JS/CSS source grows about 1.9k characters for the accessible structure; no download-size reduction is claimed. Engine 0.65.9, all training/persistence modules and Workout/Focus geometry remain unchanged.

Existing R54 checks both palettes at 320/393/834/1280px and 100/200% text, complete descriptions, persistent hit-tested Close after scrolling, Tab/Escape/return focus, forced colors, reduced motion, actual CDP touch dismissal, removal Cancel/reopen reset, superset toggle, info/note navigation and immutable inspection. Additional constrained fixtures cover disabled supersets, final slots and the sole remaining exercise. Existing B831 now requires an actual positive offset during the gym sheet's partial drag before settling, and still enforces AA text contrast, wrapped titles/descriptions, opaque theme surfaces and 54px actions against the new semantic heading/body selectors. Require exact-head source/lifecycle/independent/seven-browser/release checks, then merged Pages byte parity and actual public offline replay before publication. Physical Android/TalkBack/IME certification remains open.

Next: continue Program editing/inspection ownership and static-style retirement with actual task replay; prioritize prescription/persistence defects. Keep the established logger intact.

## M383 — selected-week sharing and accessible Program inspection (Build 930)

Continue the launch cleanup from published Build 929. The existing R54 production-browser journey fails on unchanged Build 929 when sharing an authored alternating program's second week: the shared exercise and prescription do not match the canonical selected-week cell. The old formatter hardcodes week 1, iterates the base roster, and implements separate percentage waves. The sheet also has unbounded content and fixed-size text; its code-copy path reports success without awaiting permission.

Move ShareProgramSheet and its plain-text formatter into one modules/program-share-sheet.js owner. App passes its selected activeWeek and unchanged history, program, unit, load-mode and dismissal/drag callbacks. Read daysInWeek/sessionPrescriptionRows for the snapshot and percentagePlanFor for configured loads; delete the original formatter and scheme arithmetic. Label the selected training/deload scope explicitly. Retain supported program-code copying and gallery drafts, but await clipboard completion and expose an honest failure with manually selectable text. Use the current shared action, palette, focus and modal owners. The structured sheet owns its bounded body/footer; legacy sheet-height selectors no longer override it.

LoadWaveCard keeps its existing plannedWeek values and bar-height calculation. Four static styles move to semantic theme CSS; week/set labels now scale, have complete accessible names, and scroll by keyboard within the chart. Share has no inline styling. The combined guard includes the new module and tightens 2518 → 2499; external priorities remain 2336, embedded priorities 1199, repeated priority declarations 15. Source ownership improves; this pass does not claim a download-size reduction.

Acceptance extends existing R54 with authored and generated programs, selected/even/deload weeks, both palettes, 320/393/834/1280px at 100/200% text, Clipboard rejection/success, Escape/Tab/return focus, forced colors, reduced motion and unchanged saved programs/history/cycles/drafts. R53 checks exact saved-week chart totals, readable/scalable labels and keyboard scroll at 320/393/900/1280px. M225 checks percentage load/units/rep-out/deload text against the canonical reader. Require exact-head source/lifecycle/independent/browser/release gates, then merged Pages/hosted-byte verification and actual public offline replay before claiming publication. Engine 0.65.9 and all training/persistence modules remain unchanged.

Next: continue cohesive Program editing/inspection and source-level style retirement. Prioritize any reproduced prescription/persistence defect; physical Android keyboard/TalkBack/interruption and broader source cleanup remain open.

## M382 — shared volume styling and usable enlarged-text inspection (Build 929)

Continue the Program/Plan ownership cleanup after Build 928 (PR #356, merge 165e7ae). Build 928 passed all six post-merge workflows and all seven browser groups, and direct public verification matched 127/127 release files.

The extended real R53 journey reproduces a 15px-high expandable muscle target on Build 928. Fixed 13px volume text also bypasses the root text-size setting. Move the 38 static styles out of the shared volume owner into semantic roles in the existing app.css; keep the eleven calculated geometry/zone-ink styles, unchanged canonical volume readers and App's existing callbacks. Native expandable controls now have 44px targets; contributor context stacks above exercise text on compact containers. The block table retains muscle labels while later weeks scroll and accepts keyboard focus/scroll. Reduced motion and forced colors stay explicit.

The existing source guard now includes program-volume-surfaces.js and program-load-wave.js. The combined actual inline count is 2556 before and 2518 after, with the cap tightened to 2518. External priorities remain 2336, embedded priorities 1199, and repeated priority owners 15. The volume JS owner shrinks by 2,008 characters and App loses unused presentation props; the expanded semantic CSS and responsive/accessibility rules increase combined JS/CSS source by 3,688 characters. This is a style-ownership and usability improvement, not a download-size reduction.

R53 adds one deliberately repetitive custom presentation fixture with twelve canonical chest contributions, long day labels, a final deload, 320/393/900/1280px at 100/200% text, native Enter/Space, granular subregions, canonical block-table parity, keyboard horizontal scrolling, pinned muscle labels, forced-color focus and reduced motion. The program/history engine remains 0.65.9. Require exact-head source/lifecycle/independent/browser/release checks and merged hosted-byte plus actual public offline replay before claiming publication. Physical Android/IME/TalkBack acceptance remains unexecuted.

Next: continue source-level Program/Plan style retirement and cohesive inspection/editing owners. Prioritize reproduced prescription, persistence or navigation defects; preserve the established Workout/Focus logging geometry.

## M377 — root navigation motion restraint (Build 924 candidate)

The existing M3 selection indicator, a five-card stagger, a connected arrival and an extra tab-icon bounce can fire in the same ordinary top-level navigation. A root-tab change should emphasize one relationship, not several independent element movements. Retain the measured tonal indicator, real tap-origin cue and direction metadata; limit animated destination content to one leading card and retire the redundant nav-icon scale owner. No animations touch training input controls. Existing B831 browser coverage now measures entrance count per real navigation and guards against resurrecting icon bounce; full release/engine/offline gates remain required. Do not treat simulated browsers as physical Android acceptance.

## M376 — bounded historical work and current coaching evidence (build 923 candidate)

The final cross-tab visual review reproduced two enlarged-text defects missed by page-level overflow checks: the Home greeting retained a 60px collapsed-height baseline while its natural text grew to 198px, and a Settings rule intended for stacked form rows prevented the gym-action copy shrinking (348px of copy inside a 292px button). The existing Home scroll owner now observes its natural children and remeasures in both enlargement directions, retaining immediate scroll writes and cleanup. Restrict the existing Settings flex override to actual form rows. Existing Home/M219 browser owners fail before these changes and check actual text-fragment containment afterward; B887 retains the complete smooth-collapse/pointer-opening contract. No added CSS priority or parallel animation/layout owner.

Whole-app profiling with 240 synthetic completed workouts identifies repeated construction of the legacy exercise map and loading defaults inside every replayed workout. Each synchronous canonical history analysis now builds one current map, base snapshot and unit-adjusted snapshot, then passes them through its existing private readers. Rebuild on every analysis call: no persistent history cache, duplicate engine or assumption that custom metadata cannot change. Loading normalization reads factory facts directly while still constructing independent output records/lists; its public default-inventory API still returns a fresh copy. Session review reuses its proposal as the effective suggestion when no saved review choices exist, retaining independently constructed proposed/effective set records and the original choice/Undo paths.

The same audit reproduced S1: correcting rirReported from true to false while retaining the same load/reps/RIR values left buildLifterModel serving its old -0.68 calibration bias. Its hand-built 32-bit key omitted factual provenance, units and staged program contents, and never refreshed time-dependent recovery without another data change. Replace that incomplete key with native serialization of complete canonical inputs, and retain one model only within a 60-second recovery window. Handle clock rollback by rebuilding. The calibration and readiness formulas are unchanged; corrected facts and elapsed time now reach them.

Extend existing M225 with a failing-before observed-effort correction, stable-input reuse and time-only refresh. Extend M221 with returned-inventory isolation. M318 now counts actual analysis/map construction, preserves its zero-duplicate-suggestion logging guard and exercises full App startup plus five real-pointer Resume/Back cycles with 240 workouts at native/4x CPU. Exact recovery identity, elapsed paused time, typed values and saved training are mandatory; timing stays diagnostic. Its HTTP-only reference reinstates the repeated map/default/proposal work without editing production files. The matched pair reduces full-journey analysis calls from 107 to 71 and catalog-map construction from 26,322 to 142 per CPU profile. Home readiness is 1,146.2 → 744.0 ms native and 6,091.4 → 2,638.4 ms at simulated 4x CPU; five-return medians are 385.1 → 243.8 ms and 2,602.1 → 1,250.8 ms. These are one cold sample and five warm samples per profile, not field Core Web Vitals or a physical-device certificate. The runtime-bound M376 report retains the complete measurements.

Fixed-clock differential checks preserve 36 complete history analyses (8,646,997 serialized characters), 216 suggestions, 36 fresh coaching models and 72 session reviews across units, empty/populated histories and six generated configurations, including in-place history corrections. All complete cycle/logger/startup workloads pass; the 4x logger input/completion/Undo p95 diagnostics are 39.4/119.1/84.9 ms with zero repeated suggestions. Require all source/lifecycle/independent/browser/release gates on the final commit, then hosted bytes and actual public online/offline execution. No added test registry entry, CSS priorities, training schema or engine-policy version. Remaining launch acceptance must include a complete software evidence review and a physical Android checklist; neither source size nor an automated pass alone establishes product perfection.

### Build 922 publication

PR #350 merged as c191a57d28e7d98fe3752a37135744e220bd2002 after all final-head checks passed. Direct hosted verification passed 125/125 files. The public-site phone replay passed exclusive ownership, completed/live data preservation, external-writer backup recovery, the actual offline privacy policy and Back protection with zero page errors. All six post-merge workflows passed on that exact merge, including all seven browser groups. The working source is synchronized to the published release.

## Launch acceptance — October 26 continuation

The user has authorized continuing through launch readiness, including validated publication. Build 922 is published and verified. The current delivery scope is the hosted web/PWA, as stated in the user's October 23/25 instructions; Play packaging/signing/store publication is a separate requested delivery. Keep correctness/data integrity ahead of cosmetic debt; evaluate evidence from the full app, not only isolated engine or empty-screen fixtures. Do not call physical Android/TalkBack certification complete from browser emulation.

## M375 — exclusive device-storage ownership and real offline documents (build 922 candidate)

**Reproduced S0:** two full app windows share device training and live recovery storage. After the first window completed a workout, changing the theme in the older window erased it: history 1 → 0. The existing one-time warning did not prevent the stale autosave. The new case is in the existing M217 production-browser owner and fails on Build 921.

Acquire one browser-managed exclusive storage lease before hydrating the App, including retries and recovery mode. A competing app stays in a readable, enlarged-text-safe gate, with no training, resume, discard or restore actions. Closing/navigating/crashing the owner releases the browser resource; retry then reads current data. App/live writers enforce the same ownership boundary. Returning cached documents reload before using old in-memory state. No training schema, algorithm, history policy or second persistence writer is introduced.

Older builds cannot honor a new lease: an unexpected external write suspends the current window and keeps its own export available for recovery rather than autosaving over changed data. The regression simulates that external writer, checks byte-preserved external storage, downloads the original window's completed-history backup, reloads the latest data and exercises navigation/Back with a new owner. Safe-start recovery cannot begin a workout or expose an existing live snapshot as a training action. Unit coverage includes a shared pending request, exclusive holders, retry, missing/denied APIs and refused writes.

**Reproduced S2:** an installed navigation to privacy.html returned the app shell, not the policy. Known cached documents now retain their own content before falling back to the SPA shell. Privacy joins the offline inventory and certified UI/hosted hashes. M223 covers document-versus-route selection; M217 opens the real policy offline and returns through Back. Update the public README to current names, features, offline/backup boundaries and one-window behavior; retire obsolete exercise counts, branded-template claims and unconditional progression promises.

Require all final-head source/lifecycle/independent/browser/release checks, then exact deployed bytes and public online/offline replay. The full local M217 journey and all 69 integration contracts pass; final publication evidence remains required. Engine 0.65.9 stays byte-identical. Next measured lead: the first full-app/240-workout profile finds repeated default inventory construction and legacy exercise lookup in history projections; measure matched unprofiled runs and prove exact output parity before optimizing. Physical Android acceptance and remaining source/UI debt remain open.

## October 26 — M374 readable restore review and single backup owner (build 921 candidate)

Reproduced in the existing B881 production-browser fixture: at 200% root text the restore impact table, CSV summary, switching guidance and unit choices all retained their 12–13px sizes. Those fixed inline values bypassed the user's text enlargement. Move the BackupRestorePanel and its CSV review into modules/backup-restore-panel.js, retaining the exact existing transfer, file-read race, preview, import and Undo handlers. The App remains the only persistence/merge owner.

Use relative theme-backed typography throughout the backup view, shared UiChoice for weight units, and a native impact table that reflows into named record/count rows at narrow text measures. Its accessible table and four named column headers remain present in Chromium's accessibility tree. Preserve exact counts, actual unit conversion, compatibility/removal warnings, draft conflicts, copy/share/download behavior and asynchronous-file invalidation. Retire all 43 inline style objects from these surfaces and include the extracted owner in the existing debt guard; tighten the combined ceiling from 2601 to 2558 without adding CSS priorities.

Extend B881 rather than add another test registry entry: JSON and CSV previews at 320/393/834px in light/dark palettes with actual doubled text, full count mappings, accessible column roles, wrapped controls and zero horizontal overflow; actual JSON restore/Undo plus CSV unit selection/import/Undo at enlarged text. Keep the existing cancelled/failed/concurrent transfer, stale file-read, oversized/unreadable file and persistence-grant cases. Require final rendered review, exact-head source/lifecycle/independent-engine/browser/release gates and deployed-byte plus online/offline replay before publication. Physical Android/TalkBack, broader tab/source debt and startup/warm-return acceptance remain open.

### Build 920 publication completed

PR #348 merged as 1f1a947098e47ba7867a58aeb10384f8cc898409; its tree equals the tested candidate. All six post-merge workflows succeeded, including Pages and hosted integrity. Direct production verification passed 122/122 certified files. A public-site mobile replay confirmed visible Build 920, generated-method selection, exact save review, canonical execution and Undo online and after a real service-worker offline reload, with training content preserved. The M373 timing figures below remain local diagnostic measurements, not a physical-device certificate.

## October 26 — M373 profiled exercise knowledge reuse (build 920 candidate)

CPU profiling the existing M318 cycle workload identified repeated construction of exerciseKnowledgeNode as a major cost during exercise comparisons. The node is derived entirely from the canonical immutable catalog. Reuse those facts by exact built-in object identity in the existing owner; custom definitions and edited/snapshot copies remain uncached. Freeze each nested secondary-contribution object before sharing it. Keep the same catalog, projection formula, contextual scoring and generation/audit search. No new production module or competing engine is introduced.

The added M182 reuse assertion fails before implementation. Afterward, all 376 built-in projections equal fresh-copy projections, 752 sampled catalog relationships preserve every field, nested shared contribution mutation is refused, and mutable same-ID custom copies reflect edits without changing canonical facts. At fixed clock/IDs the full generated/fixed/upgraded/advanced cycle payload remains byte-identical (873,412 JSON bytes; SHA-256 cf5647aa47d53777847a7d3347dbb12954c3e2eb94cd807e7aed8c4b7536be72). The quality contract group passes. The existing M318 native/4× cycle and logging workloads pass with the same accepted split, finalists and 54/58-set totals.

The final matched harness control bypasses only cache reads/writes in HTTP responses; it retains the same formula and nested freezing. Uncached → reused creation: 3309.7 → 972.4 ms native (3.40×), 14657.7 → 4402.7 ms at simulated 4× CPU (3.33×). Advancement: 504.4 → 230.0 ms native and 1946.5 → 1087.7 ms at 4×. Both retain the same split, two accepted finalists and 54/58 working-set totals. This is one sequential local control/candidate pair, bound to the finalized runtime and module hashes in verification/m373-exercise-knowledge-performance.json; timing is diagnostic and does not substitute for exact output/constraint parity. The 240-workout logger still makes zero extra suggestion calls across typing/completion/Undo. Require final-head engine/source, independent/lifecycle, browser and release/offline gates, then published-byte parity and public online/offline replay. Physical Android/TalkBack/interruption, broader source/style retirement and remaining tab/resilience work remain open.

## October 26 — M372 logging performance baseline and regression guard

Continue M357/M363 with a measured logging workload. The current Workout path already memoizes suggestions correctly; a benchmark of the production renderer confirms that typing, set completion and Undo reuse those suggestions. Extend the existing M318 browser performance owner with a generated seven-lift/23-working-set session and 240 synthetic historical workouts. Run 40 input events and ten completion/Undo pairs at native and simulated 4× CPU, verify each live-state write, and require zero additional calls to the canonical suggestion boundary after mount. Saved program/history content must remain byte-equivalent. No new registry entry or production module is needed.

A harness-only negative control removes the suggestion cache in the HTTP response and fails the new guard with 1,246 extra suggestion calls in the observed run. Production files stay untouched by instrumentation. Quantiles remain diagnostic rather than a machine-speed CI threshold. The reproducible report is verification/m372-logger-performance-baseline.json, bound to Build 919 and its runtime aggregate; the existing browser entry point accepts --logger-only and --logger-report for focused reruns. Its ordinary registry invocation still runs the existing cycle-creation/advancement measurements plus the logger contract.

| Profile | Input p50 / p95 | Complete p50 / p95 | Undo p50 / p95 | Additional suggestion calls |
|---|---|---|---|---|
| Native CPU | 33.3 / 33.5 ms | 33.3 / 33.7 ms | 33.2 / 33.4 ms | 0 |
| Simulated 4× CPU | 33.3 / 39.7 ms | 66.4 / 100.3 ms | 66.3 / 87.0 ms | 0 |

The combined gate also retains the original cycle workload: one local run measured complete creation at 3.07 s native / 12.86 s with simulated 4× CPU, and advancement at 0.43 / 1.82 s. Both retained their audited final choices and exact 54/58-set totals. Profiling background generation and cancellation remains a concrete next performance task; these figures do not establish a physical-device result.

Conditions: local preloaded assets, 393×852 viewport, reduced motion and HeadlessChrome 153. Input/click timing ends after two animation frames and a verified persisted live value; these are not field INP, cold-start or physical Android measurements. Forty input samples and ten completion/Undo samples per profile do not justify an overall speed grade. The runtime remains the published Build 919; this assurance pass adds a regression and evidence, with no shipped asset changes. Require all final-head checks before merging. Next open work includes broader source/style cleanup, full-app startup/warm-return and resilience measurements, remaining tab composition, and physical Android/TalkBack/interruption acceptance.

## October 26 — M371 executable method choices and review clarity (build 919 candidate)

The next roadmap sweep follows M358/M359 correctness before additional cosmetic work. Reproduced S1: the Program editor and Workout write exercise-ID progression choices, but getNextShellCell read only slot choices. A real generated bench selected Rep ladder in both editor and resolver while its executable cell stayed Double progression with engine ownership. The new M214 assertion fails on Build 918 before the fix. The full browser journey also exposed a review clarity defect: method-only edits were summarized as set/rep/effort changes without the actual before/after methods.

Move the existing exercise identity and explicit-choice readers into the canonical shell projection, delete their old definitions, and retain the existing domain API bindings. One exercise-wide user choice now reaches generated cells, numeric progression, runtime targets and logged prescription snapshots. It overrides the saved slot/week method without deleting it; Auto restores that underlying schedule. Old unmarked engine-written mirrors remain automatic. The existing withProgressionChoices transaction now also owns Program editor changes and retires empty maps. No progression formula, engine version, storage schema or saved generated schedule is replaced.

The feature-owned progression sheet uses shared native choices, explicit scope, wrapping explanations, 48px controls and a persistent Close action. Current weekly method labels consume the canonical cells and shared name catalog; original generation rationale is explicitly distinguished from later choices. Program save review names progression changes accurately and exposes exact before/after data through the same comparison renderer already used by rebuild/restore review. Keep the existing save, linked-block scope, conflict, Undo, keyboard and persistence owners. Review typography scales in its original CSS family. Retire thirteen inline style objects (2614 → 2601), add no CSS priorities and create no extra production module.

Acceptance extends existing M214, B888, M191 and UI ownership contracts. Verify all six selected methods at first/final weeks and in the evaluator; classic double holds a shared 100 load while DDP can advance only the qualifying first set. Verify Auto restoration, old mirrors, alternating identity and immutable actual method history. The real browser route covers staged choice, reviewed save, visible current methods, Preview, Workout, protected typed values, session method change, minimize/reload/resume, actual logged method and explicit program update. Inspect 320/393/900px, dark/light themes and 200% text; expanded save review keeps Close/Save/Keep editing reachable. M199 retains actual repair/apply/audit/reload coverage. Existing adaptation and integration source groups pass locally; full final-head engine/lifecycle/independent/browser/release gates and hosted-byte/offline verification remain required.

Next: continue the remaining source, tab-composition and resilience work from the objective roadmap. The code-debt targets and physical Android/TalkBack/interruption acceptance remain open; this slice is not a claim of complete launch readiness.

## October 25 — M370 executable Program reference and authored settings (build 918 candidate)

Reproduced launch defects before editing: the progression table sent week 0 to the canonical engine, displaying 0× for Week 1 and omitting the actual last week; paired exercises used the base identity throughout. A new custom-program constraint could save Skip bodyweight while retaining Push-Up, or a barbell cap of one with two barbell movements. Entering 53 weeks silently saved 52. Plan Volume ignored a browsed deload and displayed Week 1; its summary also summed fractional muscle credits under the label sets.

The reference view now consumes the existing sessionPrescriptionRows projection with one-based engine weeks, actual variant identities, effort and optional deload. Schedule and Volume share their existing browsing state and week rail, without writing the training cursor. Actual working-set summaries use plannedWeek; the muscle breakdown explains partial credits. A persistent Plan Info heading/Close action, wrapping disclosure headings, 48px controls and container-responsive progression cards work at narrow widths and enlarged text. Progression rationale remains available after the prescription rows.

modules/program-reference-surfaces.js and modules/ui-disclosure.js own presentation only; App retains transactions, navigation, save review and persistence. Delete the superseded definitions and 56 inline style objects, tighten the combined ceiling from 2670 to 2614, and add no CSS priorities. The shared program-duration module validates new authoring from 1–52 weeks without changing saved-duration reading. The existing domain settings transaction refuses newly requested exercise constraints with specific conflicts, examining executable odd/even alternatives. Metadata-only changes retain authored rosters, overrides and older longer plans. Program Settings presents the same transaction issues before Save.

Acceptance extends the existing M214/M215/M222 browser/domain gates, rather than adding parallel engines or state owners: generated and authored plans, alternating exercises, single-week/final/deload paging, working-set totals, retained Volume navigation, conflicting settings and valid save/reload, 320/393/900px themes and 200% text. M199 retains actual volume-repair apply/audit/reload coverage. Require exact-head source, engine, independent audit, lifecycle, browser and release/offline checks before merge, then hosted byte parity and an actual public-site offline replay. Engine 0.65.9 policy and historical training remain unchanged. Physical Android acceptance and the broader launch roadmap remain open.

Next: continue cohesive Program editing cleanup and real creation-to-finish journeys; prioritize reproduced prescription, persistence and usability defects. Do not treat this bounded sweep as complete launch certification.

## October 25 — M369 release-content ownership (build 917 candidate)

The launch cleanup removes the 201,450-character release-history array from the application coordinator. modules/release-notes.js is its sole owner; a reference comparison against Build 916 preserves every one of the 248 historical releases, 516 items, mixed entry formats and icon identities. Add concise current notes for the launch usability work, advance the existing announcement identity, and retain the existing dismissal/replay/archive behavior. Remove two now-unused App icon imports, retire release-data forwarding exports and import the catalog directly from the existing browser gate. The archive count now sums item counts without creating 516 projected note objects at startup; no measured performance claim is inferred.

App.js falls from 2,396,614 to about 2,195,200 source characters (about 8.4%); total shipped content is preserved in its feature module, not deleted or claimed as a download-size reduction. The existing style guard includes the new file and retains the 2,670 ceiling. Runtime/offline manifest regeneration, exact-head source/engine/browser checks and hosted SHA parity remain required. M218 covers current notes, dismissal/reload, Settings replay, historical mixed-format archive, themes and resumed edits. Saved training, schema and Engine 0.65.9 stay unchanged.

Next: continue cohesive ProgramView ownership and source-level style retirement using actual task replays. Physical Android acceptance and the broader launch roadmap remain open.

## October 25 — M368 shared Program Settings controls (build 916 candidate)

Observed baseline: Training goal and Barbell limit choices measured 41px high in the existing real-browser duration fixture. M368 migrates the remaining settings choices to the app's canonical UiChoice, removes all 46 inline style objects, adds named fieldset/legend groups and relative-size labels, and gives the form one scroll owner between a persistent heading and Save/Cancel footer. Narrow/enlarged-text footer actions wrap as full controls. The six unchanged stateless UI primitives now live in modules/ui-primitives.js so feature modules consume one owner without depending on App or forwarding unused palette/helpers. Tighten combined presentation debt from 2,716 to 2,670; no added CSS priorities or engine/state owner.

The broader R54 pointer journey also reproduced a production Home defect: deferred greeting collapse moved program actions after pointer geometry was read, making Open or Switch miss the intended button. A deterministic extension of the existing B887 browser gate fails on unchanged main with first-frame greeting height 60px, then 0px. Apply the same bounded collapse during the scroll event and retire the unnecessary animation-frame bookkeeping; the selected program, saved data, original smooth collapse and shared action geometry keep their existing owners. The focused R54 navigation/log/resume/finish replay passes with this correction. No browser timeout or tolerance was relaxed.

Acceptance: existing M215 duration/invalid/save/reload fixture now rejects undersized settings choices; B899 exercises every choice group, staged text, Cancel/Escape, focus restoration and byte-equivalent saved programs/cycles/history/drafts across phone/tablet and 200% text, then repeats creation, preview, start and resume. Shared-tab, keyboard, forced-color, release/offline and 13-stage lifecycle suites remain required. Complete exact-head CI and post-merge hosted-byte verification before calling Build 916 published. Physical Android certification remains open.

Next: continue ProgramView decomposition at the largest coherent editing/inspection owner; inspect real task behavior and remove obsolete styles at source. Prioritize any reproduced data or prescription defect. The app still needs broader source cleanup and physical-device launch acceptance.

## October 25 — M367 Program Settings dialog and form cleanup (build 915)

Continue the single-owner Program Settings view. Move twelve static sheet/form/footer styles into the existing themed CSS owner; add a named dialog, connected Folder and Description labels, and pressed-state semantics for goal, progression, training approach and equipment constraints. Preserve all engine inputs, controlled draft values, validation, Save/Cancel, swipe-dismiss and program persistence. The combined inline-style budget drops 2,728 → 2,716. Require release integrity, source contracts, independent engine audit, browser regression shards, offline shell hashing and hosted byte verification before calling the build live. Next: isolate repeated Program Settings choice-row styling under shared semantic controls without changing workout architecture.

## October 25 — M366 Program Settings ownership (build 914 candidate)

Extract the Program Settings dialog from App.js to `modules/program-settings-sheet.js`, retaining the existing state owner, staged save/cancel transaction, program and cycle length validity rules, and the swipe dismissal callback. Move 10 static block-duration JSX styles into dedicated theme CSS with disabled and invalid states plus forced-colors keyboard focus treatment. Count the new module in the source debt guard and reduce the ceiling 2738 → 2728. Do not change engine, saved program data, history or schema. Require exact-head release, core, independent engine, browser and offline verification before merge; validate hosted Build 914 afterward.

## October 25 — M365 Program weekly schedule ownership (build 913 candidate)

Extract the Program weekly-schedule presentation to modules/program-schedule-sheet.js while leaving the existing ProgramView reducer as the only persisted schedule writer. The existing day cycling, clearing, today marker, workout assignments and swipe-dismiss ref are retained. Retire 15 JSX style objects in favor of one set of semantic theme-backed CSS roles with keyboard focus, reduced-motion and forced-colors coverage. The style source contract includes the new module so debt cannot be hidden by extraction. Engine 0.65.9 and saved training data remain unchanged. Merge only after exact-head source, engine, browser and offline/release checks, then verify public Build 913 bytes.

## October 25 — M363 Program review ownership and CSS cleanup (build 911)

A bounded second UI ownership slice moves ProgramRevisionReview, ProgramLayoutHistory,
and ProgramSaveReview from the 2.42 MB application owner to
`modules/program-review-surfaces.js`. App composes them without duplicating state,
saved-data migrations, or training policy. The review surface consumes the existing
canonical engine API and program revision transaction. No change to Engine 0.65.9.

The save-review dialog's 19 inline style objects and the shared icon button's
inline style call are replaced by 20 named CSS roles in the existing stylesheet.
Every role consumes live `--wpb-theme-*` variables; disabled buttons still respect
conflicts and zero-diff saves, and cycle-only versus linked-block radio scope is
unchanged. CSS uses no additional `!important` and includes forced-colors support.
The source contract counts the extracted module alongside App.js so moved inline
debt cannot disappear from accounting; its ceiling becomes 2,753 (from 2,772).

Acceptance requires exact-head release, engine and browser checks; after merge,
verify Pages deployment, published bytes and offline cache with visible Build 911.
This pass makes the Program review family reusable but does not yet decompose
ProgramView's large editing and week rendering functions. Next: a similarly bounded
ProgramView section, only after observing behavior in real browser layouts.

## October 25 — M363 cohesive session presentation owners (build 910 candidate)

Extract the existing Home/Plan session outline, coaching highlights, plan-change review and
its load-formatting helpers into `modules/plan-session-surfaces.js`. Extract the already
shared keyboard-contained SessionChoiceDialog into `modules/session-choice-dialog.js`.
These two modules keep the exact JSX, callbacks and ARIA contracts; App remains the
composition owner and the canonical engine API remains the only computation writer.
No appearance, CSS selector, theme, workout, storage or engine-model change is intended.
Original function declarations are deleted instead of retained as forwarding shims.
This is one bounded ownership step, not completion of M363 or launch certification.

Gate this move with existing source, browser, offline and standalone engine checks; bump
the visible build and verify the published bytes and offline cache after merging.
Continue by co-locating the adjacent Program review family with the existing dialog
owner, then retire static style properties at source without adding CSS overrides.

# Whole-app launch readiness

## October 25 — M362 prelaunch code cleanup (build 909)

The app has not launched; its only current user is its owner. Prioritize a clean,
maintainable launch baseline over compatibility for hypothetical external code
consumers. Preserve actual saved programs, workout history and backup migrations.
This section supersedes the earlier instruction to prioritize another prescription
audit: reproduced defects still get fixed, but structural cleanup is now the main work.

First pass: retire App.js's duplicate computation export surface (256 exports),
move 49 verification consumers to the canonical engine API, remove 280 unused import
bindings, the unused CycleTimeline renderer and two unused constants. Consolidate
App's canonical API imports. The standalone exporter copies semantic tests without
rewriting UI imports; parity compares against engine-api.js without loading React.
The boundary regression retains 13 independent golden scenarios and rejects engine
exports or computation-test imports through App. No training policy or data schema changes.

This is a completed boundary cleanup, **not a launch-clean codebase**. App.js is
still about 2.44 MB. The maintained style audit counts 2,772 inline style objects;
2,344 stylesheet and 1,199 embedded priority declarations remain. Tighten the
existing budget after removal; never hide debt by moving it between files.

| Next pass | Purpose and acceptance |
|---|---|
| Cohesive feature ownership | Extract UI features with their state and dependencies at clear boundaries. Avoid duplicate state, calculators and passive forwarding layers; preserve the current Workout and Focus flows. |
| Shared presentation | Move static presentation to the existing shared primitives and semantic CSS. Delete superseded rules at their original owners, preserving themes, focus, disabled states, reduced motion and narrow screens. |
| Obsolete paths and verification | Prove reachability before deleting code. Keep source invariants, independent engine evidence and meaningful browser/device journeys; remove redundant compatibility scaffolding only when no real saved-data contract depends on it. |
| Reproducible production output | Keep one maintained authoring source with reproducible browser output. Evaluate packaging when ownership is clearer; do not introduce edited generated copies or a second implementation. |

Each published slice increments the build, passes exact-head source/engine/browser
checks, and verifies public hosted bytes plus visible/offline build identity. Keep
physical Android launch certification separate from Chromium emulation. Continue
feature and styling cleanup before declaring launch readiness.

## October 25 — M361 final-target explanation authority (build 908)

Baseline 907 was deployed and verified. Reproduced an equipment-boundary mismatch:
a generated 105 lb common-load estimate became four executable 100 lb sets after
the selected increment was applied, while the reason still said “Use 105 lb”.
The Why sheet separately bypassed that final boundary and also showed 105 lb.

The existing prescription adapter now reconciles prescribed “Use/to” loads after
display-unit conversion and equipment rounding, preserving observed evidence.
The existing explanation projection consumes `sessionSuggestion` rather than
calling the lower-level generator bridge. Its evidence follows the same raw LAST
exposure, converts once, respects reported-effort provenance and counts corrected
history once. A stronger/global anchor or another-day starting reference cannot
be relabeled as this day’s LAST. Completed history and saved plans are read-only.

M224 source regression failed before the fix; it now covers rounded/unrounded
loads, converted decimal estimates, earned increases, corrections and evidence
identity/provenance. B882’s real-browser Repeat flow covers four set rows,
expanded reason, Why, Focus and reload in lb/kg. No new engine or UI calculator,
schema, CSS or workout geometry. Publish only after exact-head gates, then verify
Pages bytes and the visible/offline build. Continue the cross-screen prescription
and progression audit after this bounded repair; physical Android remains separate.

## October 25 — M360 numbered webapp release policy

The M359 server files were current, but the visible build remained 906 through
M321–M359. Build 907 collects those deployed fixes under a new visible release.
Every subsequent change to shipped runtime/UI/offline bytes must increment the
canonical manifest build using `node scripts/finalize-release.mjs --next-build`.
Ordinary finalization remains idempotent. Keep engine-version changes tied to
actual engine model identity, independently of the always-increasing app build.

Release CI compares against the exact PR base or pre-push manifest and rejects
unchanged/regressed build numbers for changed shipped files. The existing PWA
browser journey checks the displayed build before/after Restart and after an
offline reload, preserving local training storage. Published-byte verification
must pass for the exact merged release. This supersedes older guidance permitting
same-build publication; local assembly still rotates cache identities safely.

After publishing and verifying 907, continue the progression/load and prescription
audit. Preserve user-owned entries, deliberate set effort and completed history.

## October 25 continuation — M359 authored set intent and live fatigue

Baseline: `528a857` (M358). The existing 13-stage lifecycle audit passed, but a new
reproduction exposed a live intent gap: two session-local **To failure** sets at
100 × 8 @ 0 RIR were compared with the exercise's default 1–2 RIR and reduced the
next ordinary set to 95. Set-type changes retain automatic numeric ownership;
that flag alone does not establish permission to reinterpret authored effort.

The existing `live-autoregulation.js` owner now shares one authored-intent guard
across load reductions, rep caps and restore validation. Explicit set types,
failure, AMRAP and percentage targets cannot authorize or receive these automatic
changes. Resume removes an old automatic adjustment whose sources carry authored
intent, preserving completed observations and user-owned/changed rows. Ordinary
two-miss reductions, observed rep caps and earned future progression retain their
existing owners. No UI layout, styling, stored-history/schema or program policy
changes; static style/priority counts are unchanged.

The existing M232 source regression failed before the fix (100 → 95) and now
covers source/pending protocol protection, immutable input and idempotent resume.
Its registered browser journey exercises the real set-number menu, deliberate
failure logging, subsequent target protection and reload. Acceptance requires
all exact-head CI checks, the lifecycle audit and post-merge hosted-byte parity;
physical Android certification remains open. Next: continue the progression/load
matrix and cross-screen prescription audit, prioritizing reproduced defects.

Updated October 22, 2026. This is the single current next-update plan. It supersedes the October 8 workout proposal: compact logging, coaching, session choices and program-change previews already exist. Preserve the user's current Workout and Focus experience.

## Current baseline and ownership

App 4.0.0 build 909 / Engine 0.65.9 / M234. `RELEASE_MANIFEST.json` is authoritative for shipped versions and hashes; `BUILD_PROFILE.json` records the current UI slice. Every changed published release increments its app build; content-addressed offline caches certify the exact bytes. No new training-model identity is implied by the app build.

| Area | Maintained owner | Next work and acceptance |
|---|---|---|
| Shared UI | UiRootHeader, UiAction, UiChoice and semantic theme roles in `modules/App.js`; `app.css` | Continue migrating existing controls and removing provably superseded declarations. Preserve palette, focus, disabled, motion and forced-colors behavior; never solve conflicts by adding another override layer. |
| Home, Plan, Profile, Progress | Existing screen components and shared navigation state | Review empty/populated/error states, reading order, action hierarchy and 320px/200% text layouts. Profile and chart editors already share controls. Preserve saved selections and record content. |
| Settings and transfers | SettingsView preferences; BackupRestorePanel transfers and Undo; StorageStatusCard browser capability state | Backup/restore ownership is consolidated. Continue category placement and shared selection controls while preserving preview, safe merge, Undo and truthful transfer/storage outcomes. |
| Workout and Focus | Existing shared workout state, runtime and mobile lifecycle | Retain compact rows, direct input, steppers, target/last-time behavior, rest timers, effort provenance and Undo. Verify interruption, keyboard, safe areas and exact-once completion; no visual redesign. |
| Engine | `modules/engine-api.js`, canonical next-engine and training-domain modules | Continue existing independent, differential and replay gates. Change policy only for a reproduced defect with evidence and a regression. No duplicate engine, adapter or exporter. |
| Persistence and offline updates | Existing store migration/merge/queue and service worker | Corrupt/future saves must not be overwritten. Preview before restore, preserve drafts, require successful persistence before completing a workout, verify offline cache integrity and cancellation recovery. |
| Android / Play | `play/README.md`, final TWA package and Play Console | Resolve package identity and signing certificate association at the domain root, build the release bundle, validate actual permissions/SDKs and complete account-specific declarations/testing. Repository/browser checks cannot certify a signed Android package. |



## M292 — one Progress and History presentation family

- Move the main Progress view's 225 fixed inline style objects into maintained class owners, sharing card, caption, field, row and action presentation across Sessions, Lifts, Volume and Body. Action/field families remove 32 repeated fixed CSS declarations. Keep five chart objects for calculated widths/heights and data-derived ink. Spacing values on the existing scale use its canonical tokens.
- Existing native `aria-selected`, `aria-pressed`, `aria-current` and `disabled` values own selection/control paint. Display-state attributes own expanded/reached/change presentation; publish only consumed live palette roles. Retire the old masked group-header font and 14 exact-selector embedded declarations that are fully superseded in the same CSS scope.
- Inline ceiling: **3,025 → 2,800**. Embedded priorities: **1,239 → 1,229**. External/repeated ceilings remain **2,360 / 15**. Preserve the canonical filtering, history grouping, charts, body logs, scores, callbacks and Workout/Focus experience; do not add priority patches or raise budgets.
- Screenshot review reproduced a 9px heading/number overlap when the six-week range bars reached full height. Keep the chart's 46px minimum and let its container fit labels plus bars. The native browser gate must use sufficiently populated weeks and assert heading separation and chart containment at normal/enlarged text.
- The extended B911 browser journey covers 320/393/834/1280px, two palettes, normal/200% text, native keyboard controls, full session notes/sets, filters, all panes, explicit body logs, same-day replacement and reload persistence. Require migration appearance equality for empty, single, populated, stalled, rising and flat data across modern/fallback presentation and forced colors; independently verify the intentional range-chart height correction. Require screenshot review, all exact-head CI and deployed-file identity before publication.
- Next: continue separately proven presentation cleanup in the remaining Progress helper cards/charts and shared overlays; main-view ownership does not imply those helpers are migrated. Physical Android certification remains open.

## M291 — complete Profile presentation batch

- Move 83 fixed JSX style objects across Profile, achievement details, Strength trends and shared Sparkline into the existing CSS families. Leave only calculated progress widths, empty-chart height and data-derived chart ink inline. Publish the live faint/border-soft palette roles once; preserve existing theme values.
- Retire all embedded Profile selectors and their superseded external counterparts. External priorities: **2,378 → 2,360**; embedded priorities: **1,280 → 1,239**; inline objects: **3,108 → 3,025**. Repeated-priority ceiling remains 15. No new priority patch layer or increased debt budgets.
- Create the section element map once per render instead of recreating all sections for each visible section. Preserve saved ordering/visibility, earned status, history, score calculations, share export and Workout/Focus behavior. Name achievement dialogs through the existing backdrop accessibility owner; expose heading/progress semantics and verify keyboard containment, dismissal and restored focus.
- Require before/after computed-style equality for empty, missing-bodyweight and populated Profile states, collapsed/expanded achievements and locked/earned details across five windows, two palettes, normal/200% text, ordinary/forced colors and Expressive/fallback presentation. Retain the eight-window Profile browser journey, screenshot review, final-head CI and deployed source identity before publication.
- Next: continue separately proven non-Workout presentation cleanup across the remaining screens and shared controls. Physical Android certification remains open.

## M290 — class-owned Profile level card

- Move the level card’s eight fixed JSX style objects and embedded fallback rules into the existing Profile CSS family, using live theme roles. Only the calculated XP fill width remains inline; canonical level, XP and history calculations are unchanged.
- Retire the superseded R17/R61 card/mark rules. External priorities: **2,389 → 2,378**; embedded priorities: **1,290 → 1,280**; inline objects: **3,116 → 3,108**. Repeated-priority budget remains 15. Keep both the modern and fallback appearances, shared forced colors and current Workout/Focus.
- Existing Profile browser coverage checks complete level/next-level copy and agreement between accessible XP percentage and actual fill width at all eight windows and both text sizes/palettes, alongside customization/export/history checks. Require before/after computed-style equality, screenshot review, final-head CI and deployed source identity before publication.
- Next: continue separately proven Profile achievement and remaining cross-screen presentation cleanup. Physical Android certification remains open.

## M289 — one Profile Strength comparison owner

- Consolidate the effective Strength section, comparison grid, row typography, single-pattern sizing and next-milestone presentation in the maintained Expressive Profile family. Retire the obsolete tile/card/narrow-phone declarations at their original owners. Preserve shared forced-colors, focus and non-Expressive fallbacks.
- External CSS priority ceiling drops from 2,432 to **2,389** (43 fewer). Inline, embedded and repeated-priority budgets are unchanged.
- Extend the existing populated Profile browser journey to check complete labels, matching featured/ordinary row insets and 48px share controls under normal/200% text at phone, tablet, rail and short-landscape windows. Retain customization, PNG export, saved history and preference persistence checks.
- Publication requires before/after computed-style equality for empty, single-pattern and populated Profiles in both palettes, ordinary/forced colors and normal/200% text, screenshot review and all final-head CI. Continue with separately proven non-Workout presentation cleanup; physical Android certification remains open.

## M288 — one Profile lifetime-summary style owner

- Move the effective layout and numeric wrapping into the maintained Profile summary family; retire the obsolete R17/R39/R40 normal rules, duplicate small-phone rules and duplicate R77 container reflow. External CSS priority ceiling: 2,432 (30 fewer).
- Fix comments erasing media/container scope in the existing ownership guard; retain only the 14 actual baseline collision keys, with a scoped repeat cap of 15. This is an accounting correction, not a claim of six further CSS removals.
- Existing populated Profile browser coverage now includes eight phone/tablet/desktop and short-landscape windows in both palettes, normal/200% text, complete summary labels, share-card customization/export and unchanged history. Require before/after computed-style parity, screenshot review and final-head CI before publication.
- Next: continue proving and retiring superseded non-Workout presentation owners. Physical Android keyboard/TalkBack/foldable certification remains separate from browser checks.

## M287 — prune genuinely unused semantic aliases

- Audit confirmed unused R41 spacing aliases, R41 surface-3, R51 intermediate palette aliases, and R57 inset/header labels in app.css. Remove 17 inert definitions from their original owners; use --pi-page-inset, --pi-space-* and semantic --pi-color-* instead of maintaining alternate token ladders.
- UI ownership regression guards ensure the retired names do not reappear in CSS or App.js; source layout and interactions are deliberately unchanged. The stylesheet priority ceiling stays at 2,462.
- Next: continue targeted ownership cleanup only with proven nonconsumers; certify physical Android foldables and tablets separately from browser geometry and motion checks.

## M286 — foldable and short landscape reading width

- Actual 900×480 R65 screenshot showed a 460px centered phone frame with excessive unused width. Extend the existing R88 medium-tab-shell CSS owner to cover short 840px+ landscape windows, retaining bottom nav and a maximum 760px reading surface; do not affect Workout/Focus or taller rail layouts.
- R65 screenshot/geometry regression checks now expect 750–760px at 900×480, five tappable tabs, no viewport overflow, and continuous root titles across all five destinations. CSS priority count unchanged.
- Remaining Android launch work requires a real-device short-landscape/foldable inspection, plus continued component/declaration retirement only when a superseded owner is demonstrated. Do not infer physical device parity from browser screenshots alone.

## M285 — multi-program and multi-month history continuity

- Expanded populated tablet fixtures to two saved programs, 16 sessions and multiple months in both palette families and both 900px/1280px widths. Verify grouping buttons, record completeness, scroll containment and header continuity without modifying history.
- Found a calendar-induced gap between short month groups in the wide Sessions lane. Keep the calendar as the same shared sidebar but span the grouping and two subsequent record tracks. Guard group-to-group gaps at 64px maximum in a rendered browser.
- No new `!important` styles, data-engine changes, or Workout/Focus restyling. Follow with actual Android foldable/tablet checks and harder saved-history imports, not speculative redesigns.

## M284 — populated tablet history certification

- Real 16-session browser fixtures revealed the 1280px Sessions history column began *below* the full calendar because its grid row shared the calendar's intrinsic height. The calendar now spans the grouping and first history row, letting workout records begin beside it. 900px remains a single-column reading layout.
- Cross-palette, 900/1280px top/scrolled screenshot capture and browser assertions now enforce bounded records, stationary opaque root chrome, the first-record/calendar relationship, and unchanged session data except expected migration timestamps. Keep this test fixture in CI to prevent the empty-state-only coverage gap from reopening.
- After exact-head CI, continue reviewing long multi-program/month groupings and actual Android tablet/foldable usage; avoid reworking data generation or Workout/Focus.

## M283 — cross-tab phone scroll insets and header seams

- Settings/Progress scroll bodies had a legacy 14px mobile padding rule, different from the shared header 12px at 320px and 16px at 393px. Replace this with `--pi-page-inset` and keep Home's existing canonical scroll owner. No new CSS layer or navigation changes.
- Browser regression checks on both themes verify left/right root header ↔ Settings/Progress scroll inset equality, opaque header chrome, and zero visible gap at the Settings jump rail. Plan's scroll picker stays guarded by its prior R62 sticky/opaque/margin tests.
- Static style-priority ceiling drops to 2,462. Next: use screenshot evidence to review tablet header/scroll surface transitions and narrow-phone text overflow; fix reproduced issues only, not a blanket redesign.

## M282 — accessible Settings row descriptions at 200% text

- Real-browser screenshot at 320px/200% text showed narrow Settings copy breaking a single word (“suggestions”) around the inline kg/lb control. The existing Settings body 16em container breakpoint now stacks the copy above its control rather than narrowing the description to one-third of the card.
- Regression guards inspect 320px/393px text-only enlargement, requiring full-width copy and controls positioned below. No additional `!important`; the normal-size compact row design and saved settings remain untouched.
- Next verify the real screenshot, bottom-navigation phone/tablet selected state, and sticky chrome after scroll. Do not change Workout/Focus or engine without a reproduced regression.

## M281 — navigation indicator and shared selected state

- Removed the persistent gray R35 perimeter from the shared moving selected-tab pill. Continue to express selection using the saved-theme tonal fill, icon and label; retain a focus-visible keyboard ring and a distinct forced-colors border.
- Real-browser and static contract tests guard against ordinary-theme pseudo-element outline/border returning across all five primary tabs. No duplicate tab renderer, animated-track change, navigation state change or `!important` increase.
- Next cross-tab priorities: enlarged-text Settings descriptions and category affordances, scroll/sticky bar compositing at 320/393px, and real-device sampling across light/dark theme and tablet sizes.

## M280 — cross-tab root title accessibility

- Browser screenshots revealed a one-word title split (“Profil” / “e”) at 320px and 200% text alongside Profile's Edit action. Fix in the existing shared title owner rather than creating a Profile-specific header.
- Root destination names now retain whole-word intrinsic width; existing flex-wrap moves action buttons instead of clipping title text. R74's real-browser inspection asserts the entire Profile word and no action overlap at 320px / 200% text.
- Next audit: 200% text within Settings row descriptions, cross-tab sticky header scroll positioning, phone navigation, and the tablet layout. Keep snapshots/keyboard/motion tests, actual-device evidence and release certification distinct.

## M279 — Settings control and category hierarchy

- One effective Settings category rail uses shared root alignment, page inset, themed canvas and 48px choice target; 11 superseded high-priority declarations and duplicate narrow viewport overrides have been retired.
- Category headings, explanatory text and selection shape use shared typography/shape tokens; all eight saved Settings groups, toggle behavior, category jump, transfer workflows and preferences remain canonical. For phone widths at or below 420px, hide the redundant visible jump label while preserving the native select's accessible label and section names.
- Static ownership and 320px, 393px, 900px and 1280px enlarged-text browser assertions now protect the category rail, focus/touch geometry and card layout. Priority ceiling: 2,463. Next work: cross-tab device screenshot review and targeted consolidation of repeated old button/section rules before launch; do not blanket-redesign Workout/Focus.

## M278 — Profile share-card cohesion

- Share-card heading, metrics and editable favorites use the shared spatial tokens with fewer bespoke gaps; six previously superseded strength/statistics priority declarations are removed without changing the current single-column score comparison.
- User-chosen card content, saved preferences and share actions remain untouched. The production CSS priority ceiling drops to 2,474. Exact-head browser accessibility, 320px typography and screenshots remain mandatory before merge.
- Next review Settings category hierarchy and input/control consistency, then a cross-tab device-only regression pass for sticky chrome, typography and scrolling.

## M277 — Progress/History cohesion

- History grouping and section headers now use the shared spacing scale; expanded sessions inherit outer-card clipping rather than creating nested corners. Two redundant high-priority legacy rules were removed. History data, date grouping, chart owners, filters, actions and Workout/Focus remain unchanged.
- The static CSS priority ceiling is now 2,480. New verification guards reject history spacing regressions; visual device review and exact-head CI remain required before merge.
- Next cohesion targets: Profile share-card editing scope and section density, then Settings category controls; do not layer additional priority patches onto the completed Program/Cycles or Progress hierarchy.

## M276 — Program/Cycles reading flow and density

- The Program's first scroll content now prioritizes current cycle context and controls; edit scope is available from Details. Cycle-link presentation moved from static per-element styles to shared semantic roles.
- Cycles overview, block list, rows, and support cards now share the existing spacing/shape scale without denser touch targets or changed training data. The open sequence is fully accessible, with concise list summaries.
- Inline debt cap drops from 3,127 to 3,116. The current Workout/Focus composition, the Plan program action, cycle navigation, and offline/storage behavior are preserved. Next verify real device screenshots, then move to Progress/History and Profile hierarchy; full Play certification remains separate.

## M275 — scrolling chrome and Program/Cycles follow-up

- Plan: the Full program action sits in the program heading, and the old footer action was removed. Selected-week memory, read-only block routing and all four panes remain intact.
- Plan's sticky week rail is fully opaque and flush at its bottom edge; deep-screen headers and Progress sticky history rows no longer leave translucent/margin bands while scrolling. No new important CSS declarations.
- Active Cycles disclose their complete block list by default but remain user-collapsible; the existing canonical cycle data, callbacks, progress and saved state are unchanged.
- Continue the audit with deeper Program day/action density, Cycles expanded layouts, then Progress/History, Profile and Settings. Require browser certification, release integrity and device screenshots before calling the entire app complete.

## This slice — M274 Program presentation consolidation

- The deep Program header's title row, name, collapsible metadata and saved-state indicator now use CSS classes and shared tokens rather than eight static React layout objects. This retains the custom program rename, save/review states, cycle navigation, motion refs and training data unchanged.
- The inline-object budget is lowered to 3,127; source gates reject return to per-instance layout owners. Protected Workout and Focus geometry is unchanged.
- Next priority remains populated Progress/History reading order, Profile share-card edit scopes and Settings category/control consistency.

## Previous slice — M273 selected-week-first Plan

- The selected week's existing session card appears directly under the week picker and week focus; remaining weeks stay in their original phase order. No extra session renderer, per-tab prescription projection or duplicated week data is introduced.
- Plan's three overview tile presentation objects have been migrated to semantic CSS classes; inline debt decreases from 3,138 to 3,135 in the actual production source, with a lowered non-growing gate. The existing browser suite additionally verifies the selected week has a single card even after navigation.
- Home/Workout/Focus/training engine behavior stays untouched. Next cohesion priority: Program overview and deep-cycle density, then Progress/History, Profile and Settings.

## Previous slice — M271 root app-bar hierarchy

- All five roots now share UiRootHeader, one actual title origin, semantic h1 destination labels and the same action row/insets. Root Plan has no Back button. The selected program is a subordinate h2 above its block/schedule context.
- Profile's header/Edit stay outside the scrolling share-card content; metadata and personalization retain their existing data owners. Progress's section navigation is inset below its shared title row. Home keeps its continuous context collapse with a stationary Home title and actions.
- Retired per-screen header geometry, brand and title overrides from both stylesheets. Reduced-motion CSS uses zero transition duration to avoid stale font/row measurements.
- Existing R65 now asserts title coordinates as well as typography; R53/B887/B831 follow the new root anatomy while retaining their interaction, touch, reflow and contrast checks. No parallel suite or tracker was added.
- Remaining placement work: Home/Preview relevance, current-week-first Plan flow, Progress records/filter hierarchy, Profile edit scopes, Settings categories, then overlay/device certification. This root-bar pass does not complete those batches.

## Previous slice — M270 shared shell

- Root destinations share one scalable title role and full visible navigation labels. The no-program Plan state retains the same title and shared creation action.
- Bottom navigation reflows at enlarged text; the expanded rail grows with labels and publishes its measured inset to the minimized workout dock.
- Progress summary columns depend on the actual text size, preserving whole readable words instead of splitting them into narrow fragments.
- UiNotice replaces duplicated app/workout snackbar presentation. A shared layout owner measures actual bottom barriers; route and rest/Focus size changes update clearance immediately. Layout transitions cannot carry Undo across a newly returned Resume control.
- Static presentation moves into named shared CSS owners. Root typography, navigation aliases and old notice offsets are retired instead of covered with new priority layers. Logging geometry, training data and policy are unchanged.

## Required release evidence

Use the existing contract registry and four workflows/thirteen jobs on the exact candidate head. Run relevant real-browser cases before publication; verify Pages and deployed hashes after merging. Existing engine generation, adaptation, quality and integration gates remain required even for UI-only work. Record evidence in the PR and existing changelog/design plan, rather than creating competing audit documents.

Actual Android certification remains open: TalkBack navigation, soft-keyboard resize, gesture Back, safe areas, offline cold start/update, process interruption/recovery, long-session timing, notifications and file sharing through the final signed wrapper. Test the package distributed by Play internal testing; a local upload-key build may use a different signing certificate. Do not describe browser emulation as device certification.


## Cohesion 09 — permanent ownership rules and whole-app sequence

Cohesion 08 (PR #214) merged five Settings switches and reminder-day selection. Cohesion 09 begins the app-wide **anti-duplication** process: remove two dead earlier important CSS declarations that are unconditionally superseded at the same selector/property, establish a CI debt ceiling, and progress through every screen in isolated passes. This is not a claim that the whole app, Android bundle or engine has been recertified. Do not disturb the compact Workout and Focus layouts.

### Required single sources

| Concern | Maintained authority | Prohibited alternative |
|---|---|---|
| Theme, paint, shape, surface, touch | Existing semantic roles in `app.css`; `UiAction`, `UiChoice`, `UiStatus`, `UiSection` | Per-screen palette, duplicate button/shadow/token set, new priority override stacking |
| Motion, focus, navigation | `modules/ui-motion.js`, existing overlay and route state owners | Animation that controls data, flash/remount workarounds, independent navigation copies |
| Next workout, day, week, cycle | Authored program/plan session projections and `program-duration.js` | Separate Today/Plan/Program/Cycle calculations or display values |
| Progression, exercise identity, volume, recovery | `modules/engine-api.js`, `next-engine/`, `training-domain/` | UI-specific training formulas, hidden fallback engines, replicated exercise catalog |
| Workout history, PRs, strength, charts | Canonical persisted revision/provenance and shared analytics | Counting deleted/superseded records twice; separate score or PR math per view |
| Backup, recovery, offline | `BackupRestorePanel`, transaction/recovery owner, `sw.js` | Multiple import previews, duplicate persistence, claiming canceled transfers succeeded |

**One source of truth does not mean one oversized component.** Share behavior, data projection and theme primitives where semantics truly match. Keep small presentation adapters for layouts or workflows with different needs. Screens may show the same canonical value in different forms, but may not independently recompute or persist it.

### CI anti-regression budget

`verification/ui-ownership-test.mjs` runs in the existing verification integration group. Its measured non-growing ceilings are **2,931** `!important` declarations, **32** repeated important properties at equal selector/at-rule scope (29 remaining collision keys), and **3,331** JSX inline `style: {` objects in `App.js`. It also blocks *new* colliding selector/property owners and duplicate shared component definitions. These are technical debt counters, **not** a verdict that every existing inline/dynamic value or override is incorrect. Lower the ceiling only with verified deletions. Never raise budgets or soften the test just to get a visual patch accepted. New presentation should use the established owner, not a fresh end-of-file specificity layer.

### Screen passes — follow this order

| Pass | Screens / owner | Simplification and acceptance |
|---|---|---|
| 0 · Foundation **started** | App shell, navigation, semantic roles, shared controls, CSS | Identify source for action/choice/surface/disclosure/motion; remove provably dead declarations and record counts; no unexpected computed-style differences |
| 1 · Home | `Home`, `HomeSessionOutline`, `SessionGamePlan`, resume and injury entry | One primary action, Game Plan in its own location, no card pile/flash, Next matches Plan and resumes directly; first-run/offline/recovery states |
| 2 · Plan / Program / creation / Cycles | `PlanView`, `ProgramView`, `Wizard`, `CustomCycleComposer`, `CycleDetail` / `CyclesView` | One current week/phase/duration/prescription/change-reason source; reduce duplicate section bars and option styles; keep editable custom cycle order and changes/Undo |
| 3 · Profile / Progress / History | `ProfileView`, `ProfileShareCard`, `ProgressDetail`, `HistoryView` | Share-card-first editing separate from page layout; aligned score, favorites, charts and history derive from canonical revisions; no fake empty data |
| 4 · Settings / Gyms / Library | `SettingsView`, `BackupRestorePanel`, `GymsSheet`, `LibraryView`, exercise/swap detail sheets | One controls/fields/toggles/segments system, consistent selected states, clear labels, equipment identity, safe import/Undo and responsive search |
| 5 · Workout / Focus **protected** | `WorkoutSession`, `WorkoutExerciseNav`, Focus, plates, rest, amplifier and injury overlays | **Do not redesign logger.** Consolidate only duplicate data/state owners with regression proof; retain steppers, active rows, target/last/actual RIR, sets, notes, rest, offline resume |
| 6 · Cross-screen / release | All sheets, dialogs, loading, empty and error states, adaptive shell, installed PWA and TWA | Reuse modal/focus/scroll ownership, certify small/expanded layouts, OS accessibility and signed-package Play tests |

Each pass starts with a concrete ownership inventory: actual function, state origin, CSS selectors, duplicated conditionals and dynamic inputs. Review task hierarchy (one primary action), typography, density, meaningful motion, error/empty/loading, and navigation. Prefer removing redundant wrappers and rules to introducing new classes. Do not create a second tracker: update **this** plan and `UI_INTERACTIONS.md` with what actually passed.

### Separate engine simplification track

Inventory and verify one canonical request route, exercise ID/catalog, allocation and time budget, volume dose, loading and RIR, shortened-session pruning, live auto-regulation, plan-change reasons/Undo, history revisions and records, strength/PR/analytics, import/export, and interruption recovery. Changes to engine policy require a reproduced defect or differential evidence proving equivalence, new replay/golden regression, and integration verification. UI-only changes must keep runtime engine files byte-identical. Avoid merging engine and visual changes into the same PR without a demonstrated coupling.

**Release definition of done:** each slice has a tiny diff, before/after debt meter, rendered populated/empty scenarios (320, 393, 834, 1280px), light/dark plus impacted saved themes, 200% text, keyboard/disabled/focus, reduced motion and forced colors where relevant, and target browser + all ten exact-head CI jobs green. Do not change test expectations to excuse a mismatch. Review physical Android/TalkBack, notification/keyboard/safe-area, offline interruption, signing, TWA domain association and Play internal testing separately; browser CI is not Android certification.

## Cohesion 10 — Home entry hierarchy (scoped first slice)

**Implemented for review:** the first-run Home no longer offers three parallel creation affordances (large “Build my program” card, empty-state “Build a program” button, and pinned “Create Program”). The pinned Compose dock remains the single primary creation action when no session or next workout is active. Templates, Custom split and Cycles remain available as secondary discovery routes. An existing program still keeps its start/preview/session outline, program switcher, injury entry, separated Game Plan, history and recovery surfaces. No engine, schedule, logging, storage or React navigation owner changed.

Home's discovery tiles now use `UiAction` for behavior and one scoped `app.css` owner for layout, text and semantic theme roles, rather than copying inline layout/color objects per tile. Their keys derive from immutable route IDs rather than an index that shifts with the program state. Measured `App.js` inline style-object occurrences decrease **3,410 → 3,393** (17 removed); the CI ceiling decreases to 3,393. CSS priority/duplicate limits remain 3,018 / 33. Existing Home scroll/masthead and Game Plan tests remain authoritative; the new `home-entry-hierarchy-test.mjs` and real-browser `home-entry-hierarchy-browser-test.mjs` additionally verify empty/populated states, primary-action count, discovery navigation and mobile/medium layout.

**Still open in Home:** dense populated dashboards, cycle progress duplication with Program, responsive disclosure control hierarchy, and initial Home/Plan projection equivalence over resumed and interrupted workouts. Those need individually proven behavior changes, not speculative style overrides. Next isolated pass: Program/Plan week/phase presentation ownership, or remaining Home sections where measured density warrants it. No claim of complete screen certification until the full queue is finished.

## Cohesion 11 — remove superseded Home color owner (first cascade pass)

The Home create action had an early `background-color:light-dark(...--pi-m3e-dark-support)` declaration and a later same-selector unconditional `background-color:light-dark(...--pi-r38-surface-1)` declaration, both `!important`. Remove only the earlier losing declaration. This does not touch the later effective declaration, hover/focus styling, layout, JS, state, or engine. Lower CI ceilings to **3,017** important declarations and **32** repeated important properties. This is a narrowly scoped cascade reduction, **not** completion of the Plan/Program screen pass. Continue with Plan/Program state and selector inventory next; avoid speculative layout changes.

## Subsequent slices

1. Continue cross-screen form/action ownership, prioritizing visible inconsistencies and removing redundant CSS only after computed-style or interaction proof.
2. Review navigation, dialogs, loading/refusal and recovery states across compact/expanded windows and enlarged text, retaining one navigation/data owner.
3. Fix reproduced engine or persistence defects against the existing replay/differential baselines. Keep all prescribed and observed effort, authored-day/revision ownership and manual input protections intact.
4. Complete signed Android packaging, store listing/declarations and physical-device checks before claiming launch readiness.

## Cohesion 12 — Plan week and pane ownership (published)

Seven Plan JSX layout/paint objects have been rehomed into one CSS declaration family with theme roles. Preserve four `UiChoice` panes, the week collapse/remembered selection, keyboard focus, and engine data. Extended R53 browser checks assert 44px targets and switch both directions. Inline style budget **3,393 → 3,386**; no new `!important`. Program week pill is the next separately proven pass.

The first CI run caught Plan labels inheriting the page font after removing inline typography: the global `#root button` reset outranked the new unscoped class. Keep the migrated tab rule within the existing expressive Plan owner specificity, so its font size and selected weight survive the reset without adding priority declarations. The unchanged B831 R47 text-fragment/48px target assertion remains the acceptance gate; no test tolerance is relaxed. PR #219 merged as `decf1f5`; all ten candidate and post-merge checks, Pages, 115 deployed-file hashes and the direct live phone smoke passed.


## Cohesion 13 — shared week and cycle-block choices (published)

`UiChoice` now owns a labelled/captioned `summary` variant for Plan weeks, Program weeks and cycle block navigation. `WeekPill` is removed. The existing selected state, keyboard/native disabled behavior, semantic theme roles, focus, forced colors and motion remain with the shared primitive. A single `.wpb-choice-rail` owns spacing and focus-ring clearance. Removed obsolete Program/Plan paint and shape overrides instead of adding another priority layer: **3,017 → 2,983** priority declarations and **3,386 → 3,378** inline style objects. Repeated-priority ceiling stays 32; tighten the other CI ceilings to the new actual counts.

State/data owners are unchanged: Plan uses `openWeek`/`pickWeek` and its existing plan projections; Program uses `weekIndex`/`pickWeek` and `plannedWeek`; cycle browsing uses `cycleBlockMetadata` and its existing `onSelect`/missing-block guard. The shared primitive formats presentation only. Manual-program choices remain selectable and readable when inactive; the redundant opacity fade is removed. Labels and captions scale in rem and wrap instead of clipping. Deload remains explicitly labelled and uses the same selected-state system.

Existing R54 coverage now exercises real manual/deload choices at 320/393/834/1280px in dark/light, 100/200% text, keyboard activation, forced-color selection and immutable saved programs/history. B890 adds actual 200% cycle-block labels and missing-block disabled selection without advancement. Keep the original R53 20-theme contrast and B831 motion/logger regressions unchanged. Publication requires all ten exact-head checks, Pages, post-merge checks and deployed-file identity; see the PR for final evidence.

Next: Cycle detail/list action and layout ownership, including expanded facts and read-only previews. This slice does not close the whole Program/Cycle screen queue or physical Android/Play certification.


## Cohesion 14 — Cycle list, detail and read-only preview ownership

One scoped Cycle CSS family now owns the list/detail headers, overview, block rows, facts, progress tracks, disclosures and preview content. `UiHeader` and `UiAction` retain shared header/action semantics. Remove the older embedded Cycle stylesheet and the separate later row/card patches; maintain the current card hierarchy and active/current/completed/preview distinctions. Normal-text block-row geometry and computed paint/typography were compared with the published baseline at 320/390/900px and match. The only remaining progress presentation objects supply the canonical percentage width; the pre-existing options Delete tone remains for the shared action-tone track.

The empty Cycle fallback has one creation action. The detail footer now declares the shared primary action instead of supplying ineffective inline accent paint beneath the secondary variant. Existing navigation, icon choice, advancement/deletion confirmations, `cycleProgress`, `cycleBlockMetadata` and `computeCell` retain ownership. Expanded next-block changes and schedule facts remain visible. Browsing/previewing does not advance a block or alter saved programs/history.

Typography uses relative sizes; narrow enlarged-text rows stack their index/chevron above the reading content when the card lacks space. Normal-text card layout stays the same. Headers wrap complete labels, preview controls have 48px targets and day disclosures retain native keyboard behavior. Active/future/completed states, keyboard focus, forced colors and reduced motion remain in the same family, with no new priority rules.

Measured debt: CSS priority declarations **2,983 → 2,931**; App inline style objects **3,378 → 3,331**; repeated-priority ceiling remains **32**. Embedded App CSS also loses 58 priority occurrences (**1,475 → 1,417**); add a non-growing 1,417 App priority ceiling and an executable escalation check so embedded styles cannot bypass the CSS budget. Tighten the executable ceilings to the new actual values. Keep engine/runtime files outside App byte-identical and preserve Workout/Focus.

Extend the existing B890 browser gate at 320/390/393/834/900/1280px: real all-cycles and detail navigation, 200% text, read-only prescriptions and week selection, keyboard preview, forced-color focus/state, saved data preservation, plus real exported-component empty/completed/missing-block fallback cases. Source contracts now assert the semantic Cycle content owner instead of the retired positional selector, and distinguish `transform` from `text-transform` when banning geometry animation. Require all ten candidate/main checks, Pages and deployed identity; record final evidence in the PR.

Next: Program/cycle creation controls (`Wizard` and custom block composer), followed by remaining block-review presentation. Whole-app and physical Android/Play certification remain open.


## Cohesion 16 and 17 — creation controls

Cohesion 16 (PR #225) merged as `b20ec6d` after all ten candidate checks passed: shared Wizard choices, heading/options layout, units, program/cycle mode and controlled name input are CSS-owned. The candidate also contains the browser-proven deferred Focus tap-selection correction. All unrelated Workout layout and engine behavior are preserved.

Cohesion 17 migrates cycle-template cards and the adaptation checkbox into the existing Wizard family, reusing shared cards/stacks/rows and type/spacing/theme roles. Selection follows the real ARIA state. Move cycle-heading reflow into its single class owner and retire the redundant priority rule. No training, generation, progression, storage or logger changes. Debt: 3,303 → 3,289 inline style objects; 2,923 → 2,922 external priorities; 32 → 30 repeated priority declarations (27 remaining collision keys); embedded priorities stay 1,417. Tighten all affected CI ceilings. R74 now exercises each template by keyboard, custom-week retention across template switches, reversible adaptation, and the existing enlarged text and Back/Next journeys. Candidate publication requires all ten final-head checks, Pages and post-merge evidence.

Next: remaining Wizard equipment/constraint and review presentation, then Settings/Profile cards. Full application migration and physical Android/Play certification remain open.

## Cohesion 18 — equipment and constraint ownership

Consolidate setup presets and constraint choices with shared tonal actions. Share equipment exclusion rendering between bodyweight and rack restrictions, including the existing gym-editor adapter. Move static labels, warning and horizontal-preset layout into the existing Wizard CSS owner; extend the live palette with its warning role. Debt: inline style objects 3,289 → 3,266; external priorities 2,922 → 2,918; embedded and repeated priorities unchanged. R74 covers preset selection, reversible rack exclusions and each constraint value by keyboard in addition to text/reflow/draft coverage. The extended Cohesion 17 browser gate exposed legacy 42px styling overriding shared custom-cycle fields. Exclude shared controls from that legacy form owner so the canonical 48px field and focus treatment apply. Cohesion 17 and 18 ship together through PR #226 only after all ten final-head checks and deployed identity pass.

Next: finish remaining Wizard shell, focus-point rows and split/session guidance; then Settings/Profile cards. The read-only review pane already uses class-owned presentation. Physical Android and Play certification remain open.

## Cohesion 19 — Wizard shell and focus ownership

Cohesion 17–18 merged in PR #226 as `12d6ff7`; all ten candidate/main checks passed, Pages deployed, and all 109 published runtime/UI hashes matched. Cohesion 19 completes the remaining Wizard shell, muscle-focus rows, shared setup steppers and split/session guidance. Use existing header/action/card primitives and theme/type/spacing roles. Keep only the calculated progress transform inline. Remove superseded Wizard-only CSS layers instead of appending priority patches. Preserve all configuration, generation and persistence behavior.

Measured debt: 3,266 → 3,233 inline objects; 2,918 → 2,858 external priorities; 1,417 → 1,376 embedded priorities; repeated-priority ceiling remains 30. Tighten existing budgets and guard the complete StepBody and setup stepper. R74 adds exact five-point/per-muscle limit and reversal checks plus normal/enlarged phone captures. Require all ten candidate/main checks, visual review, Pages and deployed identity before completion.

Next: remaining Settings/Profile card owners, followed by Home/Plan and Progress/History. Legacy option-card and global priority cleanup remains tracked; physical Android/Play certification is open.

Cohesion 19 short-window correction: the 200% text / 900×480 cycle-focus gate exposed an oversized footer. The named action region now uses at most 45% of the Wizard height and scrolls natively; R74 requires at least 44px of form space and checks keyboard focus reveals complete primary/early-finish actions. Scoped text-action typography overrides only the generic root button font reset, with no new priority declarations.

## Cohesion 20 — Settings groups and Profile strength club

Give all eight Settings row groups explicit shared cards. Migrate stable row/title and gym-navigation presentation into the Settings owner; remove legacy card/row/title layers and the unnamed parent selector. Preserve fields, settings callbacks, switch ownership and category navigation. Migrate the Profile club to shared cards and 48px tonal actions; retain lifted versus estimated snapshots, tiers, missing-lift guidance, units, share callbacks and progress semantics. Only the computed club progress width remains inline. Preserve all other Profile cascade priorities by using zero-specificity exclusions for the migrated club.

Measured debt: inline objects 3,233 → 3,195; external priorities 2,858 → 2,812; embedded priorities 1,376 → 1,325. Repeated-priority ceiling remains 30 (27 collision keys). Tighten source budgets and add Settings-group and club-progress ownership guards. R76 checks grouped card ownership/paint and 200% copy/control containment; B907 adds keyboard measure reversal, distinct totals, normal/enlarged club targets and preserved history. Require all ten candidate/main checks, image review, Pages and 109-file deployed identity.

Next: remaining Settings choice controls and Profile Strength/Achievements card presentation, followed by Home/Plan and Progress/History. This scoped pass does not close the whole Settings/Profile queue or physical Android/Play certification.

## October 21 visual/source tab audit — phone, portrait and landscape reference handoff

**Evidence and limits:** Compared the supplied phone/tablet reference ZIPs with the current production-source component hierarchy and app CSS; inspected GitHub Actions phone captures from the exact approved Cohesion 21 candidate, including 393px and 320px/enlarged-text states. The references are design inspiration, not a source-of-truth replacement. The approved switch/Home fix was merged in PR #230 after all ten candidate checks passed. This is a *finding log*, not a claim that every state or physical Android device is visually certified. Existing tests, app engine, Workout and Focus behavior are not changed by this audit.

| Surface | Confirmed hierarchy / strengths | Remaining gap and owner | Next acceptance gate |
|---|---|---|---|
| Shared five-tab navigation | Explicit Home, Plan, Progress, Profile, Settings routes; active page has `aria-current`, tab buttons retain accessible labels. Normal 393px presentation is coherent. | **P1**: 320px/enlarged-text captures visibly wrap bottom-tab labels within words (notably Progress/Settings), and 1280px/enlarged-text captures likewise split labels in the side rail. The 320px Home/Plan headings also crowd and break. Check `TabBar` and the existing R40 `.wpb-tabbar .wpb-tab>span` CSS owner. Do not fix by clipping, shrinking essential touch targets, or hiding accessible names. | Real rendered 320/393/834/1280px, normal and 200% text in dark/light; visible unbroken or deliberately legible nav labels, no cross-axis overflow, stable active indicator, focus/order and 48px targets. |
| Home | At 393px one clear Start workout action, Preview, an expanded session outline and a separate short Game Plan. Cohesion 21 removes stacked outline padding and restores the correct switch contrast elsewhere. | **P1**: The 320px enlarged-text Hero headline breaks awkwardly; revisit the responsive heading and summary/orbit composition, not training data. **P2**: Home header/hero have multiple legacy cascade owners. | Empty/populated/resume/injury/plan-change states; preview content matches Plan; no increased gap above first exercise, no tab-bar overlap, enlarged-text reflow. |
| Plan | At 393px Program name leads, Up Next follows, then the four Schedule/Volume/Progression/Blueprint modes and selectable weeks. | **P1/P2**: At 320px enlarged text the heading and pane/week rails become cramped. Plan title typography has multiple scattered specificity owners; migrate only after measured computed-style equivalence. | Current and previewed weeks, long manual program names, keyboard pane switching, 200% text, week labels/summary and Home↔Plan prescription parity. |
| Progress / History | Four meaningful panes (Sessions, Lifts, Volume, Body); glance tiles, filters, chart sections and empty states have understandable roles. | **P2**: Complex view retains ~237 static/dynamic inline presentation objects in `HistoryView`; dense filter/chart ownership risks mismatched typography and row spacing. The reviewed 393px shot has empty data, **not** a populated history sign-off. | Empty and populated history with long exercise names, measurement editor, charts, favorites, filters and grouping at 320/393/834/1280 and 200% text; verify no horizontal scroll/overlays. |
| Profile | At 393px the customizable share card is first, followed by Arrange Profile, level, summary stats, strength and achievements. The shared strength-club card and actions are now class owned. | **P2**: The page-title has multiple old font-size declarations; strength/achievement cards still use significant one-off inline styling. Keep share-card customization distinct from page-section arrangement; avoid merging unlike behaviors. | 320/393 and tablet, long name, absent/populated scores, emphasized push/pull/legs alignment, share export, 200% text and toggled sections. |
| Settings | Eight explicit shared cards, recognizable section jump and legible 393px control rows; Cohesion 21 restores switch track/thumb contrast with a 48px hit target. | **P2**: Four-option experience selector is visually compact; the jump control and remaining segmented choices still have legacy inline sizing. Review 320px and enlarged text without squeezing labels or adopting a second control dialect. | 320/393, normal/200%, dark/light and all saved themes; on/off switch containment, long translated labels, keyboard select, focus, settings persistence, forced-color states. |
| Program / Cycles / creation | Existing week/cycle selection and creation flows already share UiChoice and recent class-owned components; `CyclesView` has near-zero inline presentation. | **P2**: `ProgramView` remains one of the largest inline-layout owners (~456 style objects). Avoid cosmetic redesign of workout prescriptions or duplicating engine projections. | Current/next/completed cycle, 4–10 week custom order, read-only previews, session prescription, configuration forms, keyboard and enlarged text. |
| Workout / Focus | Protected compact logger with steppers, Target/Previous/Actual RIR, notes, rest timer, injury and offline recovery; existing candidate browser CI passed. | **Protected:** no speculative visual refactor in this audit. Physical Android and signed Play/TWA certification remain outstanding. | Preserve logger geometry and exact persistence; future changes require separately reviewed real phone/Focus captures and interaction replays. |

**Audited legacy ownership baseline (Cohesion 21):** 3,193 React inline style objects, 2,793 external CSS priorities and 1,325 embedded priorities. These numbers describe technical debt, not a score of visual quality. Do not raise the budgets or add an end-of-file CSS override. Canonical spacing/type/paint and shared UI components are specified in `DESIGN_SYSTEM.md`.

**Next isolated, testable passes:** (1) navigation and 320px/200% heading reflow, (2) Plan title/pane and Profile heading ownership, (3) populated Progress/History hierarchy and Settings choice controls, (4) Program overview density; retain the protected Workout/Focus design. A pass is complete only when exact-head ten-job CI, phone/tablet rendered screenshots, browser interactions and cache integrity pass. The Android physical-device checklist remains separate.

Source capture reference: [candidate phone/offline integrity run](https://github.com/extracteddeath/Pursuit-Iron/actions/runs/39685915651) (screenshots including R53/R74/R76); compare against the attached Phone, Tablet portrait and Tablet landscape design handoffs.

## October 22 full tab audit — implementation ledger

The source/rendered-screen audit at main `f1725a3` defines a task-based placement plan: Home prepares today; Plan manages training; Progress reviews history/results; Profile leads with the customizable share card; Settings configures equipment/preferences/data. Preserve all capabilities and the current Workout/Focus geometry.

1. **M270 shared shell — implemented; verification tracked in PR.** One scalable root title, full navigation names, reflowing summaries and measured shared notices. The expanded rail and minimized workout inset grow together. Existing motion stays authoritative.
2. **Home and preview — next.** Keep session outline visible; keep Game Plan concise/expandable; put time available and exercise inspection ahead of detailed preview coaching; use one safe Start/Resume action region.
3. **Plan and Program — pending.** Selected/current-week sessions directly below the week control; clear management/edit scope. Reproduce and resolve the audit fixture's phase/duration projection difference before changing labels.
4. **Progress — pending.** Show recent session records before the full calendar/dashboard; retain explicit grouping, calendar and filters; favorite-ordered Lifts and clear Volume/Body interpretation.
5. **Profile — pending.** Separate Edit details, Edit card and page layout customization; keep aligned strength/club rows and saved choices.
6. **Settings/tools — pending.** Categories by purpose, shared controls and clear global/gym/program scopes; retain every existing preference/tool and safe restore/Undo.
7. **Workout/overlays — pending certification.** Surrounding consistency only; verify keyboard/rest/active-set visibility, notes/plates/amplifiers, completion and recovery without reworking the ledger.
8. **Whole-app/device sign-off — pending.** Compare populated/empty states together, large text, themes, motion interruption, offline and physical Android. Passing CSS debt budgets alone does not certify deliberate design.


## Build 934 / M388 — shared program-switcher and gym styles (local candidate)

Continues M387 on branch `improve/m387-stable-program-navigation`. Program switching and gym setup now share configuration-sheet presentation with named state/field roles. Retired 76 static inline objects, 70 external priorities (including one newly exposed shadowed achievement width) and 40 embedded priorities. No training-policy, storage-schema or business callback changes. Final budgets: external 2,188 / embedded 1,143 / inline 2,334 / repeated 14. Combined Build 934 reduction from published Build 933: 170 priorities and 92 inline objects.

A 32-case populated sheet comparison covers two themes, four widths and normal/200% text. Normal geometry matches; gym choice chips and Done now follow doubled text rather than priority font locks. R74 caught the retired gym backdrop keyboard ceiling; the shared backdrop now owns that visual-viewport bound. Existing B831 adds search/empty/recovery checks with seven valid programs. Existing B846 adds staged equipment cancellation, keyboard toggling, short landscape and enlarged text while retaining zero-Smith and lb/kg conversion. Visual review, full local gates and publication status are recorded at the end of this section when complete.

Publication is blocked locally: automatic approval review rejected the attempted branch push because it did not recognize publication authorization for this candidate to the public repository. Connected GitHub metadata confirms the existing destination is `extracteddeath/Pursuit-Iron` and the account has admin/push access; this does not resolve the approval-scope question. No workaround push or external write was attempted after rejection. Complete local checks and preserve the candidate before requesting publication approval.


Final local validation: all 127 source contracts passed (generation 20, adaptation 32, quality 6, integration 69). All 45 browser gates passed across the seven registered groups. The first parallel lifecycle run timed out on M218's Week 5 viewport wait; the unchanged strict test passed alone, then the complete lifecycle group passed. No timeout or assertion was weakened. Final B831 additionally verifies enlarged switcher title/Done separation, full-width guidance, search and empty-state recovery; final B846 passes five window/theme/text cases with staged cancellation, keyboard equipment edits and bar conversion. R74's complete 14-case matrix was rerun after the shared header reflow. Normal-size sheet geometry matches all 16 baseline comparisons; 32 sheet states, ordinary motion, forced colors and phone/tablet captures were inspected. Engine hashes match all 81 previously certified next-engine/training-domain/API modules; AST comparison preserves every business expression and transaction in both migrated functions.

Narrow enlarged-text headers now put guidance below the title/action row, and the program-switcher percentage/chevron sit below a full-width name. Shared choice chips and Done follow text scaling; normal layout stays stable. Release packaging and the 933→934 build-progression guard pass. Physical Android/IME/TalkBack and deployed-byte verification remain separate. This is a local Build 934 candidate, not a publication claim. Next: obtain publication approval for this existing public destination, push this branch, require all exact-head CI checks, merge and verify hosted/offline bytes. Continue shared-style migration with the remaining compact configuration and Progress/History owners; preserve Workout/Focus geometry.


## Build 935 / M389 — history correction safety and editor consolidation (local candidate)

Branch `improve/m389-history-correction`, based on the complete local M388 candidate. A browser reproduction entered `92..5` in a historical load and clicked Save; four stored bench sets became three, losing the first set. The old inline editor also remained inside a contained/virtualized history row, with a clipped heading, off-screen Minutes and actions behind navigation.

The extracted editor now preserves raw typing and compares the staged values/counts to canonical normalizeEditedHistoryEntry output before saving. A refused malformed/incomplete or non-finite correction keeps the draft and original history intact. Reopening during exit motion gets a fresh draft key; a reproduced Cancel/reopen race no longer revives canceled values. The App correction transaction, stamped setter, mirror and Undo owners are unchanged. UI uses shared actions/theme/type/spacing roles with no inline styling; existing Exit and drag/focus/Back owners handle a portal outside the contained row. The sheet has a scroll body, independent reachable actions, visual-viewport bounds and enlarged-text field reflow. The theme-role audit also found undefined tokens in the completed-day glyph and incomplete training-max warning. Both now use existing live roles; app.css has no undefined --wpb-theme-* references.

Retired 31 inline objects; budgets external 2,188 / embedded 1,143 / inline 2,303 / repeated 14. No engine API or training-policy changes. Existing B881 adds four real correction matrices, mixed stored/current units, invalid load/reps/RIR/minutes refusal, kept typing, Save/Cancel/Undo/reload, sub-set/target ownership, explicit set removals, add/remove-lift cancellation, keyboard bounds, focus and browser Back. All earlier import/restore/transfer-failure tests remain. Final validation/publication status follows below.

Publication remains blocked by automatic approval review. After the user was asked to approve publication, “Continue the path towards launch readiness” was rejected as insufficient to authorize public source disclosure. No alternate export/write was attempted. PR #362 already exists at c375417 and all five workflows are green, but it contains only the M387 menu/navigation portion; local 62a65d4 has the exact same tree. Main remains f9e086f (Build 933). M388 and M389 are local. The private branch now descends from public c375417; both local commits were replayed and the full tested tree was verified identical before/after that alignment. The prior M388 branch is preserved. After explicit publication approval, push this branch, require exact-head CI, merge and verify hosted/offline bytes. Physical Android/IME/TalkBack certification remains open.

Final local evidence: all 127 source contracts passed (20 generation, 32 adaptation, 6 quality, 69 integration), and all 45 browser gates passed across the seven registered groups. After the rapid-reopen fix, the affected B881 gate passed all four matrices, including ordinary-motion cancellation, overflow refusal, mixed units, explicit removals, Undo, Back and reload; its earlier transfer/import cases also passed. The final integration source group was rerun without changing assertions. B831 waits for its existing entrance motion to settle before measuring its unchanged 70px row minimum; no threshold was relaxed. Phone/light/dark and enlarged-text captures were reviewed. All 81 next-engine/training-domain/API module hashes match published Build 933. All 21 app.css theme roles resolve to the 22 live palette publishers. Ownership budgets pass at 2,188 external priorities, 1,143 embedded priorities, 14 repeated priorities and 2,303 inline objects: 123 inline objects and 170 priorities retired across the local 934–935 candidate. Release packaging certifies 124 runtime files, 134 offline entries, the new editor precache entry and the 933→935 progression. M178 passed real waiting-update/Restart, companion activation, offline reload and preserved training storage. No physical Android/IME/TalkBack or deployed-byte claim is made.


## Build 937 / M391 — Library rhythm and one-axis route motion

- Replace 19 static Library presentation objects with theme-backed spacing owners for the header, search and clear action, two scrollable filter rows, four status choices and result rows. All data, keyboard focus, catalog behavior and training history remain owned by App.
- Use shared 4/8px spacing, 48px header/search actions and at least 44px filter targets across phone and enlarged-text layouts.
- Shorten root navigation's single lead-card entrance, prevent simultaneous diagonal tab-linked travel, and preserve separate exercise-detail motion, Workout/Focus geometry and reduced-motion semantics.
- Keep complete Plan labels in a 2-by-2 grid at 320px. Tighten the combined inline-style ceiling from 2,303 to 2,284; external/embedded/repeated priority counts stay at 2,188/1,143/14. The switcher browser check retains its 70px layout requirement and accepts only a 0.001px DOMRect floating-point tolerance after entrance settles.
- Release requires certified cache and all exact-head CI/browser groups, public offline byte verification, and physical Android acceptance separately.


## Build 953 — Workout spacing and continued web/PWA readiness

Continue from published B952. The repeated spacing report exposed a verification gap: a small gap between control boxes can still leave excessive space between their centered text. Center the title within the existing name/action row, reduce the normal progression row from 36px to 32px, remove the toolbar's 4px leading margin, and use 48px normal-mode tools. Focus retains its 48px tools through the existing rule's fallback. Edit existing owners; add no priority overrides or inline styles.

B896 now measures the actual progression/tool text gap (at most 24px), title/context spacing (at most 14px for its short-title fixture), zero-to-4px control separation and 48px tool targets. Its existing phone/tablet, Focus, rest, navigation, per-set ownership and load/reps alignment coverage stays in place. The final visible-label gap is measured against a 26px limit with 48px hit targets.

Readiness continues with all registered source contracts, complete training lifecycle audit, all seven browser groups, independent engine export/parity, release/cache integrity and deployed-byte verification. Record final evidence in the PR. Physical Android keyboard, interruption and TalkBack checks remain the device-only acceptance work in R90; the deliverable remains the web/PWA.


## Build 954 — Verified static presentation consolidation

One shared owner replaces 52 duplicated static App JSX style objects (45 icons, seven modal overlays). Remove 12 superseded same-selector/same-at-rule important declarations; preserve CSS fallbacks and all dynamic inline geometry. Tighten the enforced budgets to 2,061 external priorities, zero repeated-important collisions, and 2,032 inline objects. No training engine or storage API changes. Browser regression must cover Workout/Focus, dialogs, responsive themes, layout and accessibility. This is a measured first cleanup batch; remaining legitimate dynamic styles and legacy declarations require separate audits.


## Build 955 — Program/Workout static layout ownership

Scoped refactor of 74 exact first-prop static JSX style objects in the Program (23) and Workout (51) component owners. Reuse one shared CSS family for grow/min-width, truncation, repeated heading/type and compact layout rows. No new important declarations, no dynamic style migration, no training/recording policy or storage changes. Keep the 48px workout actions and B953 density intact. Tighten the static-inline debt ceiling from 2,032 to 1,958; require all exact-head browser, engine, offline and release checks plus production byte verification. Physical Android remains a separate sign-off.


## Build 956 — Shared static Program/Workout declarations

Migrate 88 further first-property static JSX presentation objects in Program (37) and Workout (51) into 46 reusable class-owned CSS property/value rules. Share spacing tokens for 4/8/12/16px values. Do not alter the engine, history, storage, dynamic state/geometry, timers, Focus or working set controls. No new important declarations. Lower inline style budget 1,958 to 1,870 and enforce call-site guards. This is a scoped cleanup, not a claim that all remaining inline styles are safe to remove. Require candidate/main browser, engine, offline, release and hosted byte checks; physical Android remains separately gated.


## Build 957 — Cross-screen shared style ownership

Retire 113 more literal first-property JSX inline style objects across 13 screen and card owners: Home 10, Settings 18, Progress detail 9, Strength snapshot 9, one-rep-max tool 9, comparison 21, exercise charts 1, Thousand Club 5, heatmap 7, Gallery 5, block review 12, legacy cards 1, changelog 6. Reuse 28 B956 CSS declaration owners and add only ten new CSS rules for the remaining shared values. Preserve actual computed sizes/spacing, data-driven styles, training engine, set logger, and motion. No new important rules. Ownership debt ceiling tightens 1,870 to 1,757. Full candidate and deployed browser/core/engine integrity remain compulsory; physical Android testing separate.


## Build 958 — Cross-screen shared CSS reuse

Migrate 80 strictly literal first-prop JSX style objects across Plan, Next, Home, Settings, Progress, comparison, recovery, profile form, conversion, volume and exercise sheets. Reuse 27 existing B956/B957 property owners and add just three missing declarations. No new priority overrides; preserve all dynamic training values, colors, measured geometry, accessibility callbacks, history, and animation. Tighten verified inline-style ceiling from 1,757 to 1,677. Maintain end-to-end core/engine/browser/offline gates and deployed byte verification; physical Android certification remains separate.

 
## Build 959 — Shared dialog dismissal and keyboard containment

Fix shared session dialogs so a nondismissible review cannot close from the scrim or Escape; keep Tab inside all sheets, including read-only ones, and restore focus to the original trigger on closing. Close buttons use consistent 48px targets and readable long-title wrapping on phone/tablet. Add source and real-browser regression coverage without engine, set-logging, history, storage, or dynamic animation changes. Publish after complete release, engine, UI, browser and offline integrity; Android hardware certification remains separate.

Build 959 certification note: automated source, release and browser checks must pass on the final candidate commit before merging; Android hardware testing and hosted deployment remain independent sign-offs.

 
## Build 960 — Shared CSS reuse across remaining static JSX

Retire 133 additional strictly static JSX presentation objects from App across Plan, Workout, Next, Progress, Profile and onboarding, reusing the B955–B958 property owners wherever values match and adding only required missing utilities. Each migrated value is layout or typography; retain all state-driven colors, values, calculations, transitions, inline animation and interaction ownership. Tighten verified presentation debt ceiling 1,677 → 1,544; add a permanent exact-debt/utility-ownership contract and retain the zero-duplicate-priority and 2,061-CSS-priority ceilings. This migration is paint/layout parity work, not an engine change. Complete full engine, browser, offline, version and hosted-byte verification before sign-off; physical Android review separately required.


## Build 961 — Dialog overlay and information-icon CSS ownership

Consolidate 25 repeated fixed modal backdrop style objects into a deduplicated set of static CSS variants, keeping each original overlay's backdrop opacity, z-index, alignment, padding, animation and independent dismiss callback. Move 11 repeated informational Lucide icon offsets to one shared rule. Preserve all dynamic colors, workout logging, focus ownership, program logic, movement timing and history. Reduce static inline-presentation debt ceiling 1,544 → 1,508 and add a permanent explicit owner/geometry guard. Require all candidate browser, lifecycle, release, offline and production-byte checks; physical Android remains a distinct release sign-off.

B961 certification gate: verify the finalized release's real browser shards, engine lifecycle, release identity and hosted asset parity before merge. Large static CSS overlay migration has no training state changes; mobile hardware remains separate.


## Build 962 — Atomic gym input edits and continued style consolidation

Repair staged gym bar weight/plate inventory edits by applying each change against the latest gym object, not a captured render's barWeights/platePairs. Preserve zero-weight Smith values across subsequent edits, and assert controlled-input focus/typing in real browser cases at 320/393/900/1024px. Move 17 additional fully static JSX layout objects into 17 CSS owners without changing dynamic training state or introducing priority selectors. Keep engine, workout logging, calendar, persistence and motion semantics intact. A completed, fully green PR/browser release is required before deployment. Physical Android/TalkBack certification remains separate.
