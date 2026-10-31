# Gallery uses the standard card surface

**Source target:** Pursuit Iron design spec v1.0.0 §6, whole-app component audit #424.  
**Stack:** first control-family pilot #425 → six segmented Settings groups #426 → this Gallery card pilot.  
**Status:** verification candidate, not a release.

## Concrete change

The gallery previously had its own background, border radius, and content padding for every imported-program preview. That duplicated the role already provided by .wpb-ui-card on Settings and other screens. Gallery program previews now use `wpb-ui-card wpb-gallery-program`. The feature retains **only vertical padding rhythm**; the standard shared card owns surface color, outline/border, corner radius, and horizontal spacing. No onclick is attached to the card itself; the Import action remains a separately named native action inside it.

The external stylesheet is 644,270 characters at this stacked candidate versus 644,317 on Build 1000 (−47). Its `!important` declaration count is still 2,024. The Gallery code path gains only a shared CSS role class; no API, import, storage or training logic changed.

## Acceptance / visual intent

The standard card should be recognizable across Settings and Gallery: same computed fill, border, radius, horizontal inset and theme response, while heading/content density may vary. The Gallery must remain a **read-only content container** with an independently interactive Import button.

New browser verification asserts the exact computed surface and width at 320/393 pixels and increased font size, and a source guard prohibits reintroducing local Gallery paint.

This does not certify screenshot parity or all theme/color contrast combinations; those require a real routed app capture and device/keyboard review. The workout set logger and specialized hero surfaces are intentionally excluded from the standard-card migration.
