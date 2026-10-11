# Build 843 — R64 theme-native search and contextual controls

## Purpose
Follow Build 842's owner-approved original themed materials and compact workout actions. Earlier Library search, switcher search, Progress history inputs and popup action menus still had independent legacy backgrounds. R64 maps these controls to the selected theme's semantic card, control, divider, on-control, active and on-active roles. No fixed gray or extra accent palette is introduced.

## Changed
- Library search and its actual 48px clear action use the current theme's control/ink roles. The existing focus-shape morph and input text selection remain owned by their current components.
- Program switcher search and Progress history filters use the same control role, including readable focus feedback; no search/filter state is changed.
- Contextual action menus keep their anchored measured position, keyboard controls, row shape and touch targets. The panel follows the theme's card; hover is quiet; active/focus uses the theme's selected background without overriding row-owned ink. Destructive action labels retain danger red and the chosen Jump-to-day row retains its original selection background.
- Forced-colors users continue to receive Canvas/CanvasText and Highlight/HighlightText rather than authored translucent tones.
- The workout set grid, warm-up tools, coaching, pinned notes, generated programs, engine version and data storage are deliberately untouched.

## Release constraints
This is a surface-only R64 stage of the existing UI/UX plan. No new component owner, animation engine, verification workflow or training policy. All four existing CI workflows, browser shards and offline integrity checks are required on the *final* PR head and again on main, followed by GitHub Pages deployment. Adaptive tablet/laptop navigation remains the next substantial layout task.

## Acceptance and evidence limits

The source change is presentation-only. Release integrity, 32 integration contracts, all four browser shards, independent engine audit and main-branch Pages remain mandatory. The browser harness uses simulated data; real Android keyboard/assistive-technology testing is not claimed by this release. R64 deliberately does not implement tablet navigation rails or related panes.
