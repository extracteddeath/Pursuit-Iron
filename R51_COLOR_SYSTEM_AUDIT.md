# R51 — Material 3 Expressive / semantic color hierarchy

## Evidence

Owner's October 11, 2026 Rose/Dark Android Settings screenshot:
- Your gyms is charcoal; Theme and Units are purple, despite sharing one information role.
- Settings segmented controls (lb, Advanced, RIR, Std) use fills that blend into their raised rails.
- The search/section jump has a nested dark-purple field; bottom navigation and other screens use unrelated choices for selected-state fills.
- Actual concern is app-wide inconsistency and weak interaction contrast, not a request for more decorative accent.

## Research cross-check (official sources)

- Google's M3 Expressive research: https://design.google/library/expressive-material-design-google-research — expressive design prioritizes discoverability through **color, containment, size, shape, motion**; established conventions and function must not be compromised.
- Android Material 3 color roles: https://developer.android.com/develop/ui/compose/designsystems/material3 — foreground / background pairs and coherent tonal roles, not arbitrary hex choices per screen.
- Material 3 Segmented buttons: https://m3.material.io/components/segmented-buttons/overview — bounded selection inside one control group (site may require JS).
- Apple HIG Dark Mode: https://developer.apple.com/design/human-interface-guidelines/dark-mode — elevated surfaces above darker base and readable foreground.
- Atlassian semantic design tokens: https://atlassian.design/components/tokens — neutral and selected backgrounds are separate semantic roles with state variants.
- WCAG 2.2 AA 1.4.11: https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast — distinguish significant control/state indicators from adjacent colors at >=3:1.
- WCAG AA text contrast: >=4.5:1 for ordinary text.

## Implementation in app.css

One R51 role scale for 20 saved themes:
- dark canvas `#111317`;
- low-elevation card `#1B1E24`;
- neutral control rail `#22272F`;
- selected moving indicator `#6D727B`, white active label;
- restrained neutral separators; no gradients.

Light variants retain the existing Material-derived surface palette and use an opaque, dark selected control with white label.

Shared CSS selection across Settings, Plan modes, Progress tabs/grouping, Library filters, Exercise Detail tabs, and the bottom navigation. The existing JS spring still owns the selected **position and width**, but no longer determines selected **paint** from whichever old theme/background a control happened to inherit. Legacy R11 palette references map into the canonical R51 roles. Fullscreen live workout set rows, steppers, Target / Previous, amplification, timers, numeric inputs and generation are explicitly untouched.

Settings cards, picker, navigation, headers and profile/history sections use the shared page/low/raised ladder. Focus-visible rings and forced-colors support remain. No changes to stored theme preferences, onboarding, React business state, or training prescription.

## Contrast design targets

For dark rail `#22272F` against selected `#6D727B`: about **3.10:1**. White text on selected: about **4.84:1**. Token pairing must be verified in the rendered UI across all 20 saved theme presets, plus light/dark and high-contrast forced colors; these measured reference swatches do NOT by themselves certify every actual component.

## Release and acceptance gates

1. Run `node scripts/finalize-release.mjs` **after** UI code stops changing to regenerate exact hashes and PWA cache revision. Do not weaken integrity gates. Draft PR until ready for real CI; never merge with manifest mismatch.
2. Run release verification and the existing 20-theme R36 contrast browser checks, R42 Settings, R45 app-wide surfaces, R47 enlarged-text, R49 workout and B831 motion/phone/PWA suites. No accidental changes to workout logger.
3. On-device: Rose and Amethyst in Dark + a light theme; Settings, Home, Plan, Progress, Profile, Library, Exercise Detail at 320 / 393 / 430 logical px and 100/125% font scale. Check selected vs unselected, long options, dropdowns, touch targets, focus, screen-reader labels, offline update, hover/press.
4. If any theme fails, tune **only role tokens**, not per-screen hex overrides. Flag ambiguous selected buttons and do not deploy until corrected.

## Follow-up architectural task

Historical CSS contains R1–R50 cascaded app-specific overrides. After the R51 appearance is device-certified, consolidate obsolete aliases into stable semantic component tokens in a separate mechanical change, proving computed paint parity before deleting overrides. This avoids introducing another uncontrolled round of screen-by-screen color fixes.
