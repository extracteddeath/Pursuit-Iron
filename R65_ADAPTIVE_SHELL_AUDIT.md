# R65 — Build 844 adaptive shell pilot

## Why
Build 843 retained a hard 460px app-frame cap across laptop and tablet screens. This artificially wastes large windows, and horizontal bottom navigation is awkward when a vertical rail is available. R65 changes only the top-level primary-destination layout and its small shared navigation-motion behavior.

## Scope
- At viewport width >=840px and height >=560px, the existing app frame can expand to 1280px. The unchanged Home, Plan, Progress, Profile and Settings tabs move from the bottom row into a 100px rail (112px at 1180px width).
- The five existing tab buttons retain their labels, React navigation callbacks, aria-current state and measured moving selection indicator; no duplicate controls or route state are introduced.
- The UI-movement module recognizes that the rail occupies the *side*, not the lower safe area, so it cannot force contextual menus upward offscreen. ArrowUp/ArrowDown follow the vertical tab order; ArrowLeft/ArrowRight continue to operate bottom navigation.
- The restored themed canvas, note tint, quiet workout tools, accent colors and every engine/logger module aside from the small nav-motion hook are preserved. Primary scroll containers can grow to 940px but remain reading-width limited.
- On phones (including very narrow screens) and short landscape windows, the existing <=460px phone shell and bottom navigation remain.
- The live workout surface and non-tab subroutes still use their established layouts. Future work: true dual-pane Plan/Progress at wide breakpoints; center appropriately sized tablet sheets; adapt Library/detail and creation views without losing saved interaction state.

## Verification
- New browser gate uses the actual production router at 393x852, 900x480, 900x1000 and 1280x850 on Rose dark and Rosewater light; checks 5 tappable destinations, rail/pane geometry, route transitions, overflow, directional arrows and saved-program integrity.
- All existing browser shards, engine contracts and offline release integrity must pass on a finalized commit before publishing.
- Device-native tablet keyboard, Android split-screen and screen reader tests are not claimed by this browser check.

## Release
Temporary certification workflow (if used) must be removed before review, leaving only the four permanent CI gates. Publish only after all checks pass on final PR head and verify main-branch Pages and post-merge checks.