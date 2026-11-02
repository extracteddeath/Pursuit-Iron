# Native integration, then a complete Kotlin engine rebuild

## User decision — 2026-11-01

Finish and certify the integrated native system, then replace the whole training
engine in Kotlin. The existing engine has reported calculation and programming
defects. Matching its output establishes migration compatibility; it does not
establish that a training decision is correct. The replacement must be designed
from explicit requirements and independent expected results.

The web app and the other developer's native branches remain independent.
Continue committing integration work only to
`architecture/android-compose-foundation`. Preserve the APK freeze and current
user data. Start the engine rebuild on its own branch after the integration gate
below has evidence; do not silently replace an active workout's issued targets.

## Integrated-system acceptance gate

The latest `DEVELOPMENT_CHECKPOINT.md` describes actual implementation progress.
Historical architecture notes and the feature catalog are scope references.
A listed capability or a passing source-parity fixture is not a completed feature.

| Area | Required evidence |
| --- | --- |
| Onboarding and programming | Setup, equipment/units, generate, full review, explicit save, cold restore and the same saved program throughout Today/Plan/Train |
| Real training | Issued warmups/working/extension targets; actual weights/reps/RIR; Last; rest/focus; corrections; confirmed skips; authorized session changes |
| Interruption and completion | Rotation/background/process death, durable resume, disk-full/lost-ack retry, one canonical completion, and the correct next day/week |
| History and feedback | Corrected facts persist; original targets remain; records/Last/cache refresh; older edits preserve newer results; skips, omissions and failed attempts stay distinct |
| Feature integration | Account for every required capability in `FeatureCatalog`: custom programs/cycles, tools/library, trends/bodyweight, backup/restore, gyms/equipment and preferences. Required features cannot remain demonstrative or disconnected |
| Data safety | Owned writes, revision conflicts/tombstones, recovery, validated backup/import/export, stable IDs and preserved real records |
| Android interaction | Phone/foldable layouts, keyboard, large text/TalkBack, timers/awake behavior, interruptions and complete journey acceptance on Android |

Every required row needs implementation evidence, scenario results and unresolved
issues recorded before the system is described as fully integrated. Source/JVM
tests and device acceptance are separate evidence. Current integration is still
in progress; the gate has not been declared complete.

### Current integration evidence and open coverage

| Catalog capability | Current evidence / remaining integration |
| --- | --- |
| Adaptive progression | Current engine supplies issued targets and re-reads corrections; independent training correctness and Android acceptance remain open |
| Workout logging | Real logging, corrections, recovery, completion and supported failed attempts have source/JVM scenarios; device acceptance remains open |
| Session adjustments | Shortened sessions and confirmed affected-muscle skips/reductions are wired; full readiness/constraint coverage remains open |
| Workout tools | Rest/focus/extensions/history have integrated paths; complete notes, loading, substitution and superset workflows still need acceptance evidence |
| Program builder | Native setup/review/save/restore covers generated programs; all advanced authoring controls still need integration coverage |
| Custom programs | Inspection/reference contracts exist; first-class native authoring/edit/cycle parity remains open |
| Adaptive cycles | Required scope is preserved; complete native cycle transitions and authorized adaptation remain open |
| Exercise discovery | Read-only engine/catalog contracts exist; full variant/attachment/swap workflows remain open |
| History and PRs | Owned history, corrections, records, filters and Last have source/JVM coverage; calendar/import/live-feedback and Android acceptance remain open |
| Strength/volume trends | Required regional-volume/standards/chart outcomes still need complete real-data workflows |
| Bodyweight/milestones | Required measurements, bodyweight and milestone workflows remain open |
| Backup/restore | Read-only validation/inventory exists; approved import/merge, rollback/Undo and export workflows remain open |
| Gyms/equipment | Onboarding equipment is persisted; complete gym/inventory/increment/bar-setting workflows remain open |
| Profile/preferences | Initial profile settings are restored; full preference persistence and progress identity still need acceptance evidence |

This table prevents source-only progress from being mistaken for a fully
integrated system. Close the actual workflow gaps before the rebuild gate is met.

## Replacement architecture

Build a headless Kotlin domain engine with typed inputs/results, deterministic
clock/seed inputs and explicit versions. Separate program generation, cycle
decisions, progression, loadability, volume, session adjustments and factual
history interpretation. Keep persistence and Compose outside training decisions.
The shared native components and durable workflow should survive engine replacement.

Define executable contracts before implementing each domain. Existing JS can
provide an input corpus, historical data examples and diagnostic comparisons.
Expected answers must come from the agreed rules, hand-calculated fixtures and
independent invariants. Classify intentional bug fixes separately from unresolved
differences. Update the current parity-only routing/certification model for that
rule-based acceptance process when the replacement is introduced.

Preserve authored programs, movement IDs, units/equipment, per-set actuals,
original targets, corrections, custom settings and cycle ownership. Version new
plans explicitly. Let an already-started old-engine workout finish against its
pinned targets; migration must not reinterpret or erase its observations.

## Required engine contracts

| Domain | Acceptance targets |
| --- | --- |
| Progression | Explicit DP/linear/percentage rules, fatigue and actual RIR, earned increases, conservative incomplete/near-failure evidence, and user-owned loads |
| Loading | Available plates/implements, bar/Smith weights, minimums and increments, lb/kg conversion, exact quantization and explicit zero/bodyweight/assistance semantics |
| Dose | Per-muscle MEV/MAV/MRV constraints, frequency, focus/reduced muscles, phase goals, time ranges and transparent infeasible constraints |
| Muscle accounting | Separate wrist flexion/extension and biceps/brachialis; documented direct/indirect credits |
| Programming | Full body/UL/PPL/ULPPL, 3–6 days, equipment constraints, movement rotation, custom-program parity and stable slot identity |
| Cycles | Hypertrophy/strength/peak, deloads, authorized split/exercise adaptation, phase-specific frequency and preserved authored amplifiers/settings |
| Workout execution | Warmups, supersets, myo/drop protocols, rest timing and exact original working-set positions |
| Adjustments | Readiness, shorter sessions and user-reported restrictions; preserve actual exposure and distinguish a changed prescription from failed performance |
| History | Missing, skipped, attempted-zero and successful observations; revisions, PR eligibility, Last and deterministic replay |

Carry reported regressions into the independent suite: 200×3 / 205×3 / 210×2 /
215×2 against 5–8 targets; 215×2 near failure; off-increment 38.5/159.5 suggestions;
unexpected rep jumps; missing/stale RIR; custom amplifiers disappearing; and
unexplained custom-cycle dose collapse. Do not encode a guessed final prescription
for these examples before the corresponding rules are specified.

## Rebuild sequence after integration certification

1. Pin the accepted native integration revision and preserve a recoverable data copy.
2. Write the domain specification and independent regression/invariant corpus.
3. Implement the Kotlin domains behind stable app contracts, including the complete
   program -> issuance -> actual logging -> correction -> next-session lifecycle.
4. Run long, adversarial scenarios across goals, equipment, units, custom plans,
   cycles, interruptions and constraint conflicts. Diagnose every unexplained divergence.
5. Switch the native app only after rule, migration, storage and Android acceptance
   pass. Retire superseded runtime/adapters after their remaining compatibility
   responsibilities are explicitly covered. Release remains a separate action.
