# Build 866 / R82 — Findable training settings

Baseline: merged Build 865 plus the regression-only R81 merge. Canonical engine 0.65.7, workout logger, persisted storage and all 20 theme palettes are unchanged.

## Defect and correction

The **Units** heading previously contained four unrelated configuration categories: unit, training experience, effort scale and smallest load jump, followed by available plates. This mislabels training controls as units and makes them difficult to find through the Settings section jump.

- Call the existing gym section **Gyms & equipment** rather than the ambiguous **Training** heading.
- Label the existing settings collection **Training preferences**. Preserve all individual values, onChange functions and storage owners exactly.
- Add a **Training preferences** destination to the existing **Jump to** chooser. A single anchor is placed directly before that collection, and selecting it uses the original smooth scroll implementation.
- Keep the existing Appearance theme picker and During a workout controls in their original positions. No extra persistent state, new component owner or redundant navigation layer was introduced.

## Verification and boundaries

- Extend the permanent R76 real-browser contract in Rose/Rosewater at 320, 393, 900 and 1280 px. Assert both descriptive jump labels, existing training rows, unique destination, functioning scroll and selection reset. Preserve previous 200% text/target tests and saved-program nonmutation.
- Release manifest certifies the changed `modules/App.js`, index build marker, changelog, profile and service worker against the exact 102-file offline shell fingerprint.
- All four final-head workflows, including every phone-browser shard, must pass before merging. After merging, verify Pages and four main workflows.
- Physical Android/TalkBack/IME acceptance and broader stylesheet consolidation remain open; this slice does not assert physical-device certification.
