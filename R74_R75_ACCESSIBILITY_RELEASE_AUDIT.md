# Build 856 — Form continuity and responsive workout evidence

Baseline: published Build 855, PR #138, main `f1f83de91c274e90817f69fa416d0c5f38a2f250`. Candidate: Build 856 on `ui/r74-accessibility-continuity-856`. Engine remains 0.65.6; all 93 non-App runtime hashes match the baseline.

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

Build 856 is committed locally. Automatic approval review rejected the branch push twice, including after the destination and standing publication instructions were verified. No Build 856 branch, PR, remote CI or deployment was created. The live release remains Build 855. A concrete review patch accompanies this checkpoint; publication requires new explicit approval for this build, followed by all permanent final-SHA CI and post-merge Pages/identity checks.

Local test caveats: concurrent browser/source runs hit the existing R54 Records-scroll assertion and the strict M199 feasibility timing budget. R54 subsequently passed with its unchanged tolerance; its diagnostics now include theme, width and expected/actual positions. The timing gate passed unchanged on Node 22 (361.2ms for 500 checks), and all 76 source contracts passed on that runtime. No assertion or release gate was relaxed.

Final local evidence: all 21 browser gates have passing runs (integration 10, workout 5, program 3, lifecycle 3); all 76 core contracts on Node 22; exact engine export/application parity; 36 independent standalone semantic suites; current release integrity and clean patch application against the published Build 855 tree. Remote CI and deployment remain blocked pending build-specific approval.
