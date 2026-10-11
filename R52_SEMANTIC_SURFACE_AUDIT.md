# R52 — screenshot-grounded semantic surface repair

## Why R51 still looked wrong

The owner's Rose / Dark screenshots of Settings (before and after R51) and SekuFit Plan reveal a real gap in shared styling:

- `Your gyms` uses `.wpb-settings-card` and becomes quiet charcoal.
- `Theme` is actually a `<details class="wpb-theme-picker">` with an old plum background (`rgb(24,18,33)` in rendered Amethyst fixture). It is NOT `.wpb-settings-card`.
- `Weight unit`, `Training experience`, `Effort scale`, `Smallest jump` are inside an **unnamed direct wrapper containing `.wpb-settings-row`**, also painted old plum. The R51 selectors missed it.
- R51's globally gray active selection was readable but disconnected from the chosen Rose/Purple/Lime accent, so active buttons and navigation contradicted the rest of the app.
- Program repeatedly emphasized week information with old theme-tinted fills.

These points were confirmed by a real-browser computed-style and DOM ancestor audit at 320px and 430px, not inferred from CSS classes alone.

## Research

- Android Material 3: use color-role and corresponding on-color pairs, not arbitrary per-screen styles — https://developer.android.com/develop/ui/compose/designsystems/material3
- Google's Material 3 theme codelab: assign surfaces to most UI containers; reserve `primary` and `primaryContainer` for deliberate actions and hierarchy — https://developer.android.com/codelabs/m3-design-theming
- Semantic selected/neutral/background role separations in Atlassian Design — https://atlassian.design/components/tokens
- WCAG 2.2 non-text contrast requires 3:1 for essential visual state indicators; standard text 4.5:1 — https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast

## Implemented

1. Replace the R51 CSS block **in place**, rather than append a conflicting R52 cascade. Stable tokens `--pi-color-canvas`, `--pi-color-card`, `--pi-color-control`, `--pi-color-active`, paired `--pi-color-on-active` and `--pi-color-divider`.
2. Charcoal surface ladder for dark mode; light mode continues using the existing theme-appropriate Material surfaces. Use a restrained, light-tonal accent active pill in dark and a dark-tonal accent pill in light. This avoids the universal gray pill without coating large cards in bright accent.
3. Style the real Settings `.wpb-theme-picker` and anonymous direct settings-row group. Keep card paint consistent with `.wpb-settings-card`, without recoloring underlying accent theme swatches.
4. Reapply quiet cards to repeated Program day/phase surfaces; retain current/next distinction and the active week indicator.
5. Preserve the R41 Progress primary/secondary tab distinction and all existing motion geometry. CSS-only changes to rendered paint; no training engine, numeric input, completed-row behavior, program data or state storage changes.
6. Allocate proportionally more real control width to the long 'Intermediate' experience label, preserving large-text wrapping and 48px target geometry.
7. Add browser assertions for computed Settings card paint, active control contrast and Program elevation; capture R52 phone screenshots.

## Verification and release gate

- Run `node scripts/finalize-release.mjs` against this candidate and `node scripts/verify-release.mjs`, without bypassing or weakening release integrity.
- Existing 20-theme R36 checks and B831 browser integration, workout, program, and lifecycle shards must all pass, plus independent engine and core contracts.
- Android screenshot review in Rose Dark is essential: after deployment, physically verify Theme, Units, Program Week 1–3, and selected Plan/Settings navigation. CI with Amethyst and 20-theme synthetic checks is not a substitute for this real-device validation.
- Do not merge if background mismatch, light-theme readability, button overflow, or workout-set geometry regress.

Note: the temporary release workflow must not be included in the final merge.
