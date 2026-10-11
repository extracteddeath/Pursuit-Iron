# R57 — screen-header and secondary-control density

## Diagnosis
After R56 Home's certified 56dp collapsed masthead, the other main destinations retained independent padding and legacy component geometry. Plan's label/back row remained unstructured (the tap target was only a glyph plus 4px padding), Progress had a large title container with four tabs, Settings separated a spacious title from its jump row, and Profile started with 26px top padding before a tall hero. Several older theme/style rules have !important specificity and use different surfaces.

## Implementation
- Plan: give its heading row a semantic class and keep its Back target 44x44dp; reduce title spacing without truncating user-authored program names or touching selected-week interactions.
- Progress: unify title container, use 44dp segmented section targets, and preserve all four sections with the same neutral semantic surface roles.
- Settings: align title and Jump to into a short, calmer cluster while maintaining a 44dp select and the existing settings section hierarchy.
- Profile: remove dead top margin, preserve Edit at 44dp, reduce the decorative level mark, and standardize hero/stats with the existing theme-derived card/control/divider roles.
- Keep Home's R56 collapsed behavior, Program's R55 expanded cards, and the live Workout/engine fully unchanged.

## Verification
Extend existing paired Rose/Rosewater R53 browser checks to measure Plan/Progress/Profile/Settings compact geometry, action target size, 320dp handling and overflow. Keep screenshots, program selection, color contrast, offline shell hashes, release checks, and engine tests. Assess actual device feel and user screenshots separately; tests establish a baseline, not a universal visual guarantee.
