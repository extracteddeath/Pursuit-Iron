# R61 foundations pilot — Build 840

Baseline: Build 839, commit `871555ee2ec7ba29fb93b56c93b569878cda1988`.

## Problem and resulting behavior

Plan's nested session names inherited a startup foreground that could remain pale after a saved light theme or an in-app theme change. `applyTheme` now publishes live palette roles; runtime CSS reads those roles. Startup/recovery colors are also synchronized by the same owner. A stale startup cache cannot remain the runtime foreground.

Profile formerly interpreted a null Strength Score as missing bodyweight even when its identity header showed 185 lb. Missing bodyweight retains the Add action. With bodyweight but no supported scored lift, Profile explains the missing weighted-set evidence. Existing scored history still displays the Strength card, trend and milestones.

## Shared component pilot

`UiHeader`, `UiAction`, `UiChoice` and `UiStatus` own recurring presentation/native control semantics for migrated Home/Plan controls. Screen callbacks, scroll memory, selected state and persisted training data retain their existing owners. Static guidance is not a repeated live announcement. Animated tab tracks keep their existing motion/surface owner; location/filter choices use shared tonal foreground/surface roles.

Start workout is the strongest Home action, retaining the established tonal fill in dark themes. Create Program becomes secondary while a next session is ready and stays primary without that session. All creation, freestyle, calculator, Library and history routes remain available. Icon and secondary targets stay at least 48px; primary actions start at 52px. Plan session names can wrap. The collapsed Home masthead remains 56px with three actual 48px controls.

## Evidence and limits

The existing R53 gate now covers deliberately inconsistent startup colors, real Settings theme selection in both directions, warm reload, measured session/selected-week/action text contrast, accurate Profile missing-weight/missing-lifts/scored branches and actual Home pointer reachability. It retains the existing paired light/dark captures and 320/393px geometry checks. The new selected-week check found a 4.09:1 foreground pair; using the semantic on-feature role repairs that pair.

Existing Program, workout, browsing-context, offline and release gates remain mandatory. No gate is removed, skipped or replaced with a mirrored component-only check. The 93 runtime modules outside App.js and their aggregate must match Build 839. This release changes no training-engine, prescription, storage-schema or logging behavior.

The companion UI/UX Master Plan now assigns tablet/laptop layouts to actual screens, covering 600/840/1200/1600 CSS-pixel candidate window classes, adaptive rails, list/detail and supporting panes, bounded reading/logger widths, overlay presentation, keyboard/pointer/trackpad use and resize/zoom tests. It adds A40–A47 beneath the A37 adaptive milestone. These are future implementation slices; this release does not claim those layouts or physical-device certification.
