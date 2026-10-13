# Build 856 — Form continuity and responsive workout evidence

Baseline: published Build 855, PR #138, main `f1f83de91c274e90817f69fa416d0c5f38a2f250`. Release: Build 856, published in PR #140 as recorded below. Engine remains 0.65.6; all 93 non-App runtime hashes match the baseline.

## Reproduced defects and repairs

| Case | Before | Delivered behavior |
| --- | --- | --- |
| Navigation at 200% text | Fixed 15px leading clipped wrapped labels at 320px | Relative line boxes and intrinsic rail buttons keep labels within the actual controls |
| Expanded Plan at 200% text | Up Next was 198px tall in a 68px grid row, overlapping Program overview | Intrinsic content owns the supporting row; overview begins below Up Next |
| Keyboard over the gym form | Form remained attached to the 740/852px layout viewport while the visible area shrank | Existing visual viewport constrains the backdrop and sheet; focused Smith weight remains visible and retains its value |
| Nested autofocus Goal form | Opening autofocus replaced the focus-stack opener; Escape returned to the parent panel | The existing stack remembers the activating control before React autofocus and restores that button |

Gym and Goal forms also expose their real dialog titles, and gym name/equipment filter fields have explicit accessible labels. Theme colors, navigation selection, native dismissal, saved prescriptions and workout controls keep their existing owners.

## Browser evidence

The permanent R74 integration gate covers two themes (Rose/Rosewater) and seven available-window sizes: 320×740, 393×852, 600×800, 900×480, 900×1000, 1280×850 and 1600×900. It exercises Tab/Shift-Tab containment, IME ownership of Escape, scroll-lock release, real opener return, named dialogs, nested dismissal, navigation at twice rendered text size and Plan card separation. Phone keyboard reduction is emulated through the actual visual viewport event path; it is not a physical keyboard certification.

The existing R72 gate now owns R75 workout journey coverage: those seven sizes in Rose, Rosewater and Violet (21 scenarios). Every scenario types weight/reps, completes and undoes a set, records effort, opens Plates, changes exercise, minimizes/resumes with the same data/identity, and finishes to a single durable history record with the reported RIR. It checks original saved-program equality. The initial layout-only live fixture now includes the exercise-note field produced by real workouts; no production persistence/progression change was needed.

The original B825 compact logger gate, R54 browsing/workout journeys, R65 adaptive shell, 33 integration source gates and release integrity are retained. All 20 original browser gates remain; R74 is the 21st gate in the canonical registry. Permanent CI must pass at the final PR head; Pages and all four main workflows must pass at the merge before claiming deployment.

## Limits and remaining work

This is browser verification of the measured surfaces and workflows. Physical Android keyboard, TalkBack, device safe areas and actual touch/trackpad feel still require device evidence. Wider 200% form/Library/Wizard and full control-target coverage, final app-wide visual review, stylesheet consolidation and the separately recorded Home/Plan duration discrepancy remain open. The whole roadmap is not declared complete.

## Publication checkpoint

Build 856 was explicitly authorized and merged in PR #140 as `b4892045c441046c8cde4c02a1ed62e7656aed8f`, from final head `47da0c4d54ea0856e10ee1f375f8ccf750703d8a`. All four permanent PR workflows and ten checks passed. Pages and all four main workflows passed, and all 106 deployed runtime/UI/manifest/offline-shell assets match the merge. The earlier automatic-review publication block is resolved.

Local test caveats: concurrent browser/source runs hit the existing R54 Records-scroll assertion and the strict M199 feasibility timing budget. R54 subsequently passed with its unchanged tolerance; its diagnostics now include theme, width and expected/actual positions. The timing gate passed unchanged on Node 22 (361.2ms for 500 checks), and all 76 source contracts passed on that runtime. No assertion or release gate was relaxed.

Final local evidence: all 21 browser gates have passing runs (integration 10, workout 5, program 3, lifecycle 3); all 76 core contracts on Node 22; exact engine export/application parity; 36 independent standalone semantic suites; current release integrity and clean patch application against the published Build 855 tree. Remote CI, Pages and deployed identity subsequently passed as recorded above.

## Build 857 — Library and creation accessibility continuation

Baseline: published Build 856. Preserve the checked PR #139 focus-return and zoom-equivalent browser work without applying its obsolete Build 856 metadata over production.

| Reproduced case | Candidate behavior |
| --- | --- |
| Expanded detail still enters the modal stack | Existing focus/scroll owner excludes the visible supporting pane; list/search remain usable; resize refreshes that same owner |
| Back/Escape loses the source Library row | Native Back performs dismissal, then focus returns to the same selected exercise after DOM reflow |
| 200% Library status labels overlap | Text-relative grid sizing adds rows and keeps each whole label inside its control |
| 200% detail section words split and overlap | Existing tabs reflow to complete labels while keeping the moving indicator and original semantic controls |
| Library filter/custom-exercise controls below 44px | Real controls have at least 44px width/height |
| 200% creation header clips title/progress | Header wraps whole title words and preserves the progress fraction |
| Creation name relies on placeholder | Program name / Cycle name is explicitly labeled |
| Floating Demo covers fallback video heading | The redundant overlay hides only when the existing fallback is already a real video link |

The R74 preflight passed all 14 light/dark/seven-window scenarios. It now checks the filtered Library list after scrolling each result into rendering, full-screen detail keyboard entry, the nonmodal supporting pane, Back/Escape and IME ownership, named 44px Library controls, 200% text labels, Library/Wizard zoom-equivalent CSS viewport reflow and exact search/draft retention. The isolated nested-Goal live fixture is torn down before checking creation because Home intentionally presents Resume during a live workout. No production workout state is rewritten by this fixture cleanup.

The existing R65 program/cycle journeys cover both accessible name labels and Library Back/Escape return. All original logger, workout, offline and source gates remain required. Engine 0.65.6 and all 70 canonical engine modules are unchanged; 92 of 93 non-App runtime hashes remain identical, with only the presentation module ui-motion.js changed.

Final candidate gate results and publication are tracked in PR/main Actions; all four final-SHA workflows, Pages and deployed byte identity must pass before marking Build 857 published. Physical Android/TalkBack/safe-area/touch/trackpad evidence, full Wizard-step/app-wide target coverage and final stylesheet/visual consolidation remain open, together with the separate duration discrepancy.

Final Build 857 local evidence: all 21 browser gates have passing runs (integration nine plus the corrected R44/B831 rerun, workout five, program three, lifecycle three); all 76 source contracts passed on Node 22, with the unchanged M199 500-check budget passing at 272.3ms. Exact canonical export/application parity, source identity and 36 independent semantic suites passed, alongside release integrity. The broad gate caught an over-broad custom-action minimum-height selector affecting Library result rows; it was restricted to the custom action and the original R44 row-height assertion passed. No tolerance or acceptance assertion was lowered.

## Build 858 — Program/Cycle creation continuation

Baseline: published Build 857 / PR #141, main `91941890a4da4ad787f3d8723420870fc7cf561b`.

| Reproduced defect | Repair |
| --- | --- |
| Enlarged split names extend beyond the option's narrow text column | The existing setup layout owns a text-relative container query; option icons/status sit above full-width text when space is limited. Titles wrap between whole words. |
| Long cycle names and duration metadata crowd each other | The title/duration row wraps into another line within the same option. |
| Units and Superset/Barbell choices retain columns too narrow for enlarged words | Auto-fit grids use the available text space; the same selection handlers and values remain. |
| Muscle-focus controls are 34px and optional generation lacks a 44px minimum | Setup-only 44px targets, readable values and wrapping muscle labels/totals. Live workout steppers keep their existing owner and dimensions. |
| The bodyweight toggle exposes paint without its selected state | The same native button now carries `aria-pressed`; each fieldset is named by its current question. |

The permanently registered R74 gate now includes all 15 first-use Program and 13 first-use Cycle steps, including profile questions skipped for returning users. Seven windows in Rose/Rosewater produce 392 step visits at normal and 200% text. Each visit measures actual control rectangles, names, complete control text, readable choice/review regions and whole option words. It uses real keyboard selection for focus and bodyweight preferences, then Back/Next to verify retained answers. The six expanded/tall theme/window scenarios additionally halve their CSS layout viewport and double pixel density, retaining each step's answers. This is zoom-equivalent layout evidence, separate from physical browser zoom.

The fixture mounts the exported production Wizard after the ordinary app loads its saved theme; it uses the original CSS, palette, motion and configuration owners. Its completion callback only observes configuration, and inspection never invokes that callback or writes a plan. Existing real Home/creation routes and all logging, progress, bar-weight, history, worker-generation and offline checks remain required.

Local verification uses Node 24 and Chromium 153 in this environment. Permanent release CI uses its existing Node 22 / Google Chrome setup. The original 76 source contracts and all 93 non-App runtime hashes remain unchanged; canonical engine 0.65.6, export/application parity and independent semantic suites retain their existing gates. Release finalization certifies all 102 required offline entries, and deployed verification must cover the same 106 manifest/runtime/UI assets as Build 857.

Publication remains gated on the four permanent workflows at the final candidate head, then Pages, the four post-merge workflows and deployed byte identity. The current Wizard browser review does not certify physical Android keyboard/TalkBack/safe areas, touch/trackpad feel, other app forms/targets, final app-wide visual/usability review or stylesheet consolidation. The historical Home/Plan duration item is closed by R50/M219 and is not a new remaining defect.
