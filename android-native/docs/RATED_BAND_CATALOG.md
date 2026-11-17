# Source-reviewed resistance-band tranche (Kotlin-only beta)

**November 17, 2026.** This milestone adds **16 reviewed, repetition-based** exercises from the active web catalog. Combined native coverage advances from **345/383** to **361/383** canonical exercise identities, while functional coaching parity remains explicitly **unproven**. The source-linked authoring fixture is `kotlin-engine/verification/rated-band-source-20261117.json`; its exact Kotlin runtime definitions are in `StarterExerciseTranche.kt`.

## Physical loading contract

A resistance band has a **variable force–elongation curve**. A manufacturer-printed nominal LB/KG tension value is a device-specific **rating**, not a constant measured working-set mass and not equivalent to free weights, a cable stack, or another band brand. To avoid misleading prescriptions:

- The reviewed `rated-resistance-band-set` implement requires **exact positive discrete manufacturer-rated options**, entered by the athlete for a specific gym. Neither a generic `bodyweight` flag nor a continuous machine stack qualifies. An absent band, unknown label, out-of-list choice, unit mismatch, or unsupported increment is rejected.
- Band exercises have their own canonical IDs, muscle credits, practical repetition bands, per-exercise progression and PR/history identity. The engine may only propose changes to **available reviewed options** and must not silently translate band tension to an equivalent barbell or dumbbell weight. Machine A/B applies only to actual eligible physical machines; a band is not a machine.
- Gym editing selects the **Exact totals** inventory type and clearly labels these values as manufacturer-rated nominal band tension. Custom authoring, week composition and cycle review all fail closed on continuous or unverified band inventory. The specific anchor must also be confirmed for band pulldowns, band face pulls, leg curls and standing adduction.
- A band device or manufacturer change must be treated as a changed physical inventory or distinct gym/profile before comparing old and new numerical tension ratings; no cross-band strength equivalence is scientifically established.

The full reviewed tranche includes shoulder laterals; biceps curls; tibialis raises; ulnar deviation; lateral walks; leg curls; lat pulldowns; pull-aparts; good mornings; hip thrusts; adduction/abduction; face pulls; resistance squats; overhead triceps extensions; and standing horizontal chest press. Tibialis is not counted as calves; adductors and abductors are separate; biceps/brachialis and wrist compartments are not merged.

## Explicit exclusions (22 identities still outstanding)

**Not** included: band-resisted push-ups and Spanish squats, because their combination of bodyweight and band resistance/support needs a separately modeled **hybrid loading basis**. Assisted dip/chin-up/pull-up requires **counterweight** semantics. Planks, carries, hangs and holds require **time/distance** observations rather than fabricated reps. Loaded neck work requires a separately reviewed physical device and technique policy. Do not force these into the new numeric manufacturer-rating pathway merely to reach a catalog count.

## Acceptance / release

CI gates the immutable source metadata, the 16 reviewed Kotlin definitions, exact stock, per-exercise load identity, missing anchors, bad/unverified band settings, M3 Gym chooser, and custom/generated planning. **These tests do not prove 100% web/Kotlin engine parity, superior programming, full phone accessibility, or safe several-month backup recovery.** Keep the APK/AAB freeze until the full native UI and complete end-to-end device and parity acceptance are finished.
