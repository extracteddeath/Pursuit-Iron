# Shared Settings segmented control — second cohesive pilot

**Base:** draft PR #425, Build 1000 runtime baseline.  
**Authority:** whole-app design audit in draft PR #424.  
**Status:** source/browser candidate only, not a production release.

The Settings page previously rendered six individually authored sets of exclusive toggle buttons (weight unit, experience, effort RIR/RPE, minimum loading increment, rest length, sex). Although they shared the .wpb-segmented CSS track, each hand-built JSX button owned its own click/aria-pressed semantics and state mapping.

This pass introduces **UiSegmented** in modules/ui-primitives.js as one native-button semantic wrapper around existing UiChoice. It exposes one named role=group with options, selected value and change callback. It uses UiChoice kind="tab" **only as a presentation exemption for the app's existing moving segmented track**; the controls remain ordinary aria-pressed buttons in a group, not an ARIA tablist. It does not add CSS paint, data storage or selection animation logic.

All six Settings groups now delegate the same action semantics to this owner. Important preservation: the minimum-increment value is unit-scoped. Switching kg/lb must not rewrite the stored old-unit value, and selecting a new increment retains its correct value/unit pair.

**Measured App.js reduction versus parent candidate:** 2,021,472 → 2,020,495 source characters (−977). No added inline styles or app.css changes; shared primitives grow by approximately 662 chars, so the combined two-source code still decreases. The component now supports six Settings groups, replacing separately implemented button loops.

**Verification:** dedicated source guard and real Chromium 320/393px, 200%-text browser fixture: native 48px button geometry, one selection per group, keyboard activation, no overflow and correct unit-scoped increment behavior. Existing source and seven-browser-shard gates must pass on exact head.

**Next:** only after verification, consider migrating comparable Program segmented choices to UiSegmented where they truly share the same role. Do not force custom week selectors, active-set steppers or multiselect groups into an exclusive segmented group.

**Acceptance boundary:** Native/React behavior is testable with CI; actual full-screen visual hierarchy, user-selected theme variants, Android IME/TalkBack and device screenshot sign-off are not automatically certified by this isolated fixture.
