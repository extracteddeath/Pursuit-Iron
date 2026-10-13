# Build 861 / R77 — Profile and Progress enlarged-text readability

Baseline: merged R76 test-only PR #145 (Build 860, Engine 0.65.7).

## User-visible repair

- At narrow widths with enlarged typography, Profile's three metrics reflow into single full-width rows instead of breaking labels into letters within the original three-column strip.
- Progress's four destination tabs reflow into two columns, keeping their entire labels readable and their 48px touch height.
- The behavior is driven by the rendered container size and type scale; it is not a device-specific breakpoint. Regular phone text keeps the compact controls.
- Saved Rose/Rosewater theme semantics, moving selection indicator, reduced-motion rules, and the compact workout logger remain unchanged. No engine or history code is touched.

## Evidence and release gate

The R76 browser contract now asserts the actual reflow structure at 320px under 200% enlarged text. The existing R76 checks continue to cover 320/393/900/1280 in two themes, actionable controls, no horizontal overflow, browser errors and unchanged saved program. The complete existing source, browser, phone/offline, independent engine and release-integrity workflows must pass on the final PR head. After merge, verify Pages and post-merge workflows.

Local release-integrity check completed successfully on the exact production UI bytes: 93 hashed runtime entries, 7 hashed UI entries, all scripts parsing and 102 offline shell entries. Physical Android IME, TalkBack, safe areas, touch and wider stylesheet consolidation remain open and must not be claimed as verified by emulation.
