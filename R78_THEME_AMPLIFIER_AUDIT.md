# Build 862 / R78 slice 1 — Home material and last-set technique alignment

Baseline: Build 861, merged PR #147. Preserves Engine 0.65.7 and the compact live logger.

- Derive strong dark feature surfaces from the existing chosen theme card (84%) and its accent (16%), rather than the oversaturated legacy feature container. This keeps Home, Plan featured containers and existing selection treatments within one coherent palette; light theme remains unchanged.
- Align last-set amplifier to the complete exercise slot without the legacy 34px indentation. Use the same card/control/divider theme roles as other exercise controls, 50px disclosure targets, accessible button labels, correct expansion, and visible keyboard focus.
- Do not modify progression, saved history, workout sessions, calendar, or live numeric input. This slice is independently reversible.

Release gate: existing B860 source and browser contracts plus all four permanent CI workflows at final PR head, Pages, post-merge CI and deployed byte identity. Physical Android/TalkBack remains unverified.

Next: R78 slice 2 — consistency summary, exact-history calendar and independent collapsed milestones.
