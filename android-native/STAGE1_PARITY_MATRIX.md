# Stage 1 — active-web / Kotlin parity matrix (initial inventory)
**State:** S1 inventory assembled; acceptance pending verified PR #1041 and integrated branch. **Owner:** Kotlin rebuild development. **Inventory revision:** 2 (2026-11-24).
**Pinned web:** `29faa204212a4d54088b4870341350e605939185` (Build 1016 changelog, next-engine 0.65.10).
**Pinned Kotlin:** `05c02ff2362736439ca1fcabfd7d477bfb2fa366`. Stage 0 accepted: [engine push CI](https://github.com/extracteddeath/Pursuit-Iron/actions/runs/45482709594) and [Compose push CI](https://github.com/extracteddeath/Pursuit-Iron/actions/runs/45482709540).
This matrix operationalizes [master plan](MASTER_DEVELOPMENT_PLAN.md), retaining [E01–E41 scope](kotlin-engine/ENGINE_BASELINE_COVERAGE.md) and [historical web parity ledger](NATIVE_WEB_FEATURE_PARITY_LEDGER.md). Historical statuses are NOT current proof.

## Evidence rules
MAP = source/test candidate identified, behavior not yet individually accepted. GAP = visible source-surface difference; requires disposition or verified equivalent, not automatic waiver. REVIEW = declared compatibility definition needs active route/saved-plan assessment. All are open. A passing branch-wide CI is necessary but not sufficient. Fixture names below are candidate locations for evidence inspection and further regression design; no per-row pass claimed. Required row IDs remain stable across revisions. Use stage as work owner; append exact test method, matched web fixture, result, source SHA and device requirement as evidence is reviewed. Scope is versioned below; later evidence and approved web deltas update rows without silently moving the denominator.

## 41 inherited acceptance requirements
Each E-ID links to the unchanged authoritative requirement in ENGINE_BASELINE_COVERAGE. The independent outcome below defines the next evidence work, not a substitute for its full wording.
| ID | Stage | Candidate Kotlin test locations | Required acceptance outcome | State |
|---|---|---|---|---|
| E01 | S4 | CoachAthleteSetupDomainTest; NativeKotlinGeneratedProgramJourneyTest | Persist each supported goal, experience, frequency and split; regenerate from identical reviewed inputs. | MAP |
| E02 | S4 | CoachAthleteSetupDomainTest; GeneratedProgramQualityMatrixTest | Respect chosen duration and hard cap; include actual active/rest/warmup timing. | MAP |
| E03 | S2 | EquipmentLoadDomainTest; NativeKotlinGymCatalogEditingDomainTest | Change gym and units; never lend stock or historical loads across gyms. | MAP |
| E04 | S2 | BasicProgressionExtendedCoverageTest; AssistedCounterweightProgressionDomainTest | Correct bodyweight, external load and inverse assistance; honor exclusion. | MAP |
| E05 | S4 | CoachWeekAthleteConstraintsTest; GeneratedProgramQualityMatrixTest | Honor every input toggle and locked weekday; reject conflicting hard constraints. | MAP |
| E06 | S5 | NativeKotlinCustomAuthoringSourceFileStoreTest; NativeKotlinFirstProgramSeedFileStoreTest | Save/edit/reopen exact owner-scoped setup; stale or foreign revision rejected. | MAP |
| E07 | S2 | EquipmentLoadDomainTest; KotlinSetNumberAuthorityDomainTest; KotlinVerifiedPlateLayoutDomainTest | All numeric routes use verified stock and exact load basis; reject 38.5/159.5 phantom increments. | MAP |
| E08 | S3 | FullWebCatalogCapacityTest; ReviewedBroadSourceCatalogTest; MeasuredExerciseDomainTest | Every active exercise ID and variant mapped to correct equipment, measurement, muscle and usage semantics. | MAP |
| E09 | S3 | CustomExerciseSwapRankingDomainTest; CustomExerciseSwapWeeklyCoverageTest; KotlinComposerSwapReviewDomainTest | Rank genuine alternatives; preserve role, dose and equipment; fresh load and explicit scope/Undo. | MAP |
| E10 | S3 | CoachExerciseChoiceSearchDomainTest; NativeKotlinPinnedExerciseNoteFileStoreTest | All attachments/variants discoverable; retain distinct setup cues and verified compatibility. | MAP |
| E11 | S4 | CoachWeekComposerTest; GeneratedProgramQualityMatrixTest | Generate all active/retained split-day combinations; real movement allocation and constraints. | MAP |
| E12 | S4 | MuscleVolumeAccountingDomainTest; WeeklyDoseFeasibilityDomainTest | Audit direct/indirect dose with separate muscle compartments and focus/reduction. | MAP |
| E13 | S4 | NamedLiftPracticeContinuityDomainTest; GeneratedPeakPhaseProgramTest | Frequency, movement pattern and dose meet goal/phase with realistic available equipment. | MAP |
| E14 | S4 | GeneratedProgramQualityMatrixTest; CustomCycleCoachAuditDomainTest | Use feasible session budget without padding or unjustified underfill; measure actual work and rest. | MAP |
| E15 | S4 | AdaptiveScheduleCalendarSearchTest; WeeklyDoseFeasibilityDomainTest | Infeasible input yields actionable refusal/alternative; no silent relaxation. | MAP |
| E16 | S4 | CustomCycleBuilderDomainTest; NativeKotlinCustomAuthoringDraftCoordinatorTest | Create/edit/reorder/clone custom plans; preserve per-set settings, stable identities and explicit Undo. | MAP |
| E17 | S2 | CoachSessionPrescriptionDomainTest; KotlinWorkoutSnapshotDomainTest | Issue exact achievable targets, warmups and individual reps/rest/RIR; no invented load. | MAP |
| E18 | S2 | BasicProgressionPolicyDomainTest; BasicProgressionExtendedCoverageTest; BasicProgramProgressionPlanDomainTest | All styles/protocols in detailed matrix below; next-session issuance, not merely review. | MAP |
| E19 | S4 | FatigueAwareRepTargetReviewDomainTest; SessionFatigueEvidenceDomainTest | History and observed effort justify fatigue adjustments; coaching-off remains independent; actual RIR nullable. | MAP |
| E20 | S3 | AdvancedAmplifierProtocolDomainTest; CustomWorkoutExecutionDomainTest; NativeKotlinCustomHistoryTransactionTest | All techniques retain exact owning set, activation load, stages, rest, stop, history and progression. | MAP |
| E21 | S3 | CustomSupersetAuthoringTest; CustomWorkoutExecutionDomainTest; CustomShorterSessionReviewDomainTest | Superset ordering, uneven rounds, rest, time cuts and no-superset preference. | MAP |
| E22 | S5 | KotlinWorkoutSnapshotDomainTest; NativeKotlinCustomWorkoutSnapshotCodecTest | Immutable issuance contains stable keys and full source, physical load, technique and timing context. | MAP |
| E23 | S5 | KotlinNativeJournalFileStoreTest; NativeKotlinHistoryTransactionCoordinatorTest; NativeKotlinCustomHistoryTransactionTest | Crash/retry/finish exactly once; retained original targets and corrected history. | MAP |
| E24 | S2 | KotlinWorkingSetEvidenceDomainTest; KotlinJournalLiveSetCoachDomainTest | Completed, skipped, failed and missing sets remain distinct; no unearned load increase. | MAP |
| E25 | S4 | InWorkoutCoachDomainTest; NativeKotlinCurrentLoadCoachReviewTest | Each readiness selection changes only reviewed eligible work; off has precedence; no phantom failures. | MAP |
| E26 | S3 | InWorkoutCoachDomainTest; KotlinShorterSessionReviewDomainTest | Report restriction; protect completed facts; preview and approve affected work, never infer diagnosis. | MAP |
| E27 | S3 | KotlinShorterSessionReviewDomainTest; CustomShorterSessionReviewDomainTest | 45/30/20 and Full; preserve essential work, IDs, safe skip/omission and Undo. | MAP |
| E28 | S3 | KotlinSetDraftStepDomainTest; KotlinVerifiedPlateLayoutDomainTest; NativeKotlinWorkoutMachineChoiceFileStoreTest | Mid-workout edits use verified increment/stock; preserve independent drafts and actuals. | MAP |
| E29 | S6 | KotlinWorkoutPresentationDomainTest; NativeKotlinOwnedLoadCoachingIntegrationTest | Concise review and reason, actionable controls, no repeated unsolicited changes; device acceptance. | MAP |
| E30 | S2 | BasicProgressionPolicyDomainTest; CustomBasicHistoryProgressionDomainTest; NativeKotlinOwnedLoadCoachingIntegrationTest | Strict all-set DP and per-set dynamic, repeat/hold/earned increase with corrected history and explicit approval. | MAP |
| E31 | S4 | LongitudinalCoachDomainTest; KotlinProgramPerformanceEvidenceDomainTest | Trend/stall/fatigue and missed sessions inform justified future choices, not history mutation. | MAP |
| E32 | S4 | GeneratedAdaptiveBlockSafetyTest; WholeCycleStrengthContinuityDomainTest; StrengthRosterBoundaryGuardDomainTest | 16+ weeks and repeated cycles; actual strength/peak frequency and no unjustified menu/dose collapse. | MAP |
| E33 | S4 | AdaptiveCycleOptionReviewDomainTest; NativeKotlinAdaptiveCyclePermissionInventoryTest | Adapt split/exercise independently; explicit review; preserve authored settings and locks. | MAP |
| E34 | S4 | ReviewedRecoveryBlockRecipeDomainTest; OverloadRecoveryLoadReviewTest; NativeKotlinRecoveryScheduleFenceTest | Deload justified, scheduled once, reversible; safe return and no stale approval. | MAP |
| E35 | S4 | KotlinObservedScheduleWeekDomainTest; NativeApprovedBlockWeekProjectionTest | Week boundary, missed-day schedule and correct future issuance; consistent Today/Plan/Train. | MAP |
| E36 | S5 | KotlinWorkoutJournalReplayDomainTest; KotlinNativeJournalFileStoreTest; NativeCustomWorkoutJournalCodecTest | Exact recorded data and receipt identity on cold replay; corrections do not rewrite original targets. | MAP |
| E37 | S5 | KotlinSavedHistoryCorrectionDomainTest; NativeKotlinHistoryTransactionCoordinatorTest; NativeKotlinMixedHistoryFactsReadTest | Correct older sets; recompute later references/PRs without rewriting newer facts. | MAP |
| E38 | S6 | KotlinHistoryPerformanceSummaryDomainTest; StrengthStandardsMeasurementDomainTest; NativeStrengthClubDomainTest | Correct charts, scope, dates, favorites, strength reference and bodyweight; distinguish estimates from achievements. | MAP |
| E39 | S5 | NativeKotlinEncryptedTrainingSnapshotTest; NativeKotlinCleanStoreRestoreTest; NativeKotlinVerifiedHistoryCsvExportTest | Complete encrypted restore, import scope/conflicts/Undo, update and device recovery; CSV is not backup. | MAP |
| E40 | S6 | NativeKotlinCoachAssistanceSettingsStoreTest; NativeKotlinCandidateSegmentedRootFileStoreTest | Owner-scoped profile/preferences/themes, accessibility and full device settings. | MAP |
| E41 | S7 | GeneratedUpstreamNumberTraceDomainTest; KotlinSetNumberAuthorityDomainTest; NativeKotlinApprovalFileAuthorityTest | One Kotlin runtime; versioned reproducible decisions and independent evidence, safe rollback without legacy runtime. | MAP |

## Progression styles — seven declared web styles
Web source: `modules/next-engine/progression-style.js`. Kotlin source: `BasicProgramProgressionPlanDomain.kt` declares the same seven styles; matching enums are not matched behavior. Web selection and actual load policy must be traced separately, including manual overrides and coaching disabled.
| Stable ID | Web style | Required outcome | Candidate tests | Stage/state |
|---|---|---|---|---|
| PROG-AUTO | auto | Select exercise-, role-, phase-, experience-specific method; preserve explicit manual choice | BasicProgramProgressionPlanDomainTest | S2 / MAP |
| PROG-DOUBLE | double | One attainable increase only when all relevant completed sets meet top reps; no automatic reduction | BasicProgressionPolicyDomainTest | S2 / MAP |
| PROG-DYNAMIC | dynamic | Only individual eligible sets earn increase; other sets retain independent baseline | BasicProgressionExtendedCoverageTest | S2 / MAP |
| PROG-LADDER | ladder | Preserve authored ladder and per-set rep targets; require earned advancement; bodyweight no invented load | BasicProgressionExtendedCoverageTest; BasicProgramProgressionPlanDomainTest | S2 / MAP |
| PROG-LINEAR | linear | Reviewed exact step after required success, no unearned increases after missed/failed set | BasicProgressionPolicyDomainTest | S2 / MAP |
| PROG-WAVE | wave | Preserve authored week/set load wave and correct rest/reps; confirmed training max and stock | BasicProgrammedPercentageLoadDomainTest; ReviewedStrengthWeekPeriodizationDomainTest | S2 / MAP |
| PROG-E1RM | e1rm | Estimate only from eligible observed effort and load; separate from confirmed training max; no invented evidence | ProgressionEffortDomainTest; EvidenceBackedStrengthWeekLoadDomainTest | S2 / MAP |
### Additional baseline modes and cross-mode acceptance (S2)
| Stable ID | Mode | Required outcome | Candidate tests | Stage/state |
|---|---|---|---|---|
| PROG-ASSISTANCE | Inverse counterweight | Only lower attainable assistance after earned success; no borrowed station | AssistedCounterweightProgressionDomainTest | S2 / MAP |
| PROG-BODYWEIGHT | Bodyweight | Earn reps without inventing external load | BasicProgressionExtendedCoverageTest | S2 / MAP |
| PROG-LEVELING | Leveling | Current unsaved fields only; future prescription untouched | DoubleProgressionLevelingDomainTest | S2 / MAP |
| PROG-REP-TARGET | Exact rep targets | Approved per-set targets advance only under intended rule | BasicProgressionExtendedCoverageTest | S2 / MAP |
| PROG-COACH-OFF | Coaching disabled | Preserve basic/manual path; fatigue and adaptive coaching cannot override | BasicProgressionPolicyDomainTest; NativeKotlinCoachAssistanceSettingsStoreTest | S2 / MAP |
| PROG-CORRECTION | Corrected history | Current future recommendation uses verified corrected facts; issued targets unchanged | CustomBasicHistoryProgressionDomainTest; NativeKotlinHistoryTransactionCoordinatorTest | S2 / MAP |
Linked to E04/E18/E24/E30/E36.

## Explicitly supported topology families — 26
Web source: `modules/next-engine/topology.js` SUPPORTED_SPLIT_FAMILIES, candidateStructures and splitSupportsTrainingDays (2–6). The table is an inventory of actual locked/preferred candidate branches, not a claim each is visible in every wizard or every goal. Core automatic search uses full_body, upper_lower, ppl, ulppl, pplul, custom (PPL excluded from automatic five-day search). Other families are explicitly preferred/locked compatibility contracts. Preserve saved-program reproduction as well as active selectable functionality. Kotlin `CoachWeekComposer.kt` currently declares FOUR named ComposerSplit values; authored custom equivalents may cover some outcomes, but do not establish automatic/locked generation parity. Native `CoachAthleteSetupDomain.kt` input and day-frequency support must be checked. In particular the web allows two-day programs, whereas the existing master baseline focused on 3–6.
| Stable ID | Web family | Supported locked days (source) | Observed native surface | Stage/state |
|---|---|---|---|---|
| SPLIT-FULL_BODY | full_body | 2–6 | Named native FULL_BODY; 2-day admission gap to verify | S4 / MAP |
| SPLIT-UPPER_LOWER | upper_lower | 2,4,6 | Named native UPPER_LOWER; 2-day admission gap to verify | S4 / MAP |
| SPLIT-CUSTOM | custom | 3–6 | Custom cycle authoring exists; automatic custom seed topology parity unverified | S4 / MAP |
| SPLIT-PPL | ppl | 3,5,6 | Named native PUSH_PULL_LEGS; 5-day locked variant and phase parity unverified | S4 / MAP |
| SPLIT-ULPPL | ulppl | 5 | Named native ULPPL | S4 / MAP |
| SPLIT-PPLUL | pplul | 5 | No distinct ComposerSplit; generated order and semantics need proof | S4 / GAP |
| SPLIT-HYBRID | hybrid | 5 | No distinct ComposerSplit | S4 / GAP |
| SPLIT-PHAT | phat | 5 | No distinct ComposerSplit | S4 / GAP |
| SPLIT-BRO | bro | 5,6 | No distinct ComposerSplit | S4 / GAP |
| SPLIT-GLUTE_FOCUS | glute_focus | 3,4,5 | No distinct ComposerSplit | S4 / GAP |
| SPLIT-TORSO_LIMBS | torso_limbs | 4,5 | No distinct ComposerSplit | S4 / GAP |
| SPLIT-PPLA | ppla | 4,5 | No distinct ComposerSplit | S4 / GAP |
| SPLIT-ULA | ula | 5 | No distinct ComposerSplit | S4 / GAP |
| SPLIT-ARNOLD | arnold | 6 | No distinct ComposerSplit | S4 / GAP |
| SPLIT-FULL_BODY_PATTERNS | full_body_patterns | 2–6 | No distinct ComposerSplit; pattern rotation needs evidence | S4 / GAP |
| SPLIT-PHUL | phul | 4 | No distinct ComposerSplit; heavy/volume day behavior separate | S4 / GAP |
| SPLIT-FIVE_THREE_ONE | five_three_one | 4 | No distinct ComposerSplit; percentage contract also separate | S4 / GAP |
| SPLIT-ACADEMY_PREP | academy_prep | 3,4 | No distinct ComposerSplit | S4 / GAP |
| SPLIT-GZCLP | gzclp | 4 | No distinct ComposerSplit; tiered protocol separate | S4 / GAP |
| SPLIT-RIPPLER | rippler | 4 | No distinct ComposerSplit; tiered wave separate | S4 / GAP |
| SPLIT-JT | jt | 4 | No distinct ComposerSplit; tiered powerbuilding separate | S4 / GAP |
| SPLIT-SBD_POWER | sbd_power | 3,4 | No distinct ComposerSplit | S4 / GAP |
| SPLIT-FIVE31_BEGINNER | five31_beginner | 3 | No distinct ComposerSplit; 5s PRO/FSL separate | S4 / GAP |
| SPLIT-STRENGTH_FB | strength_fb | 3 | No distinct ComposerSplit | S4 / GAP |
| SPLIT-TEXAS | texas | 3 | No distinct ComposerSplit; volume/recovery/intensity separate | S4 / GAP |
| SPLIT-UPPER_LOWER_ALT | upper_lower_alt | 3 | No distinct ComposerSplit; alternating weeks need proof | S4 / GAP |
Fixture requirement for EACH row: exercise the supported day counts and applicable goals/experience/stock, check exact day intents, loadable main lifts, movement and muscle dose, phase-specific frequency, timing and constraints; save and issue, complete, then progress/rebuild without changing locked family. Verify unsupported combinations refuse or present explicit alternatives. Pinned public catalog SPLITS contains 24 of these families and Wizard filters them by selected 2–6 day frequency (App.js lines 5730, 5839). The remaining custom and pplul are active automatic topology families, not silently dropped. All 75 template rows are separately inventoried in STAGE1_WEB_SHELL_INVENTORY.json. Their concrete goal, duration, gym and week restrictions are referenced by source line. S4 must verify those outcomes rather than only matching family names. Do not blindly add 22 enums or reproduce old branded formulas where the web itself uses a Pursuit interpretation.

## Percentage protocol inventory — nine declared schemes
Web source: `modules/next-engine/percentage-protocols.js`, PCT_SCHEMES, percentageProtocolFor, adaptPercentageSetBudget and deriveTieredLinearState. Kotlin `BasicProgrammedPercentageLoadDomain.kt` currently reviews authored weekly percentage, fixed set count and RepBand against confirmed TM and exact stock. Generic percentage support is NOT proof of all within-day ramps, plus sets, tier states, recovery schedules or max updates.
| ID | Web scheme | Independent outcome to verify (also compare actual web output) | Stage/state |
|---|---|---|---|
| PCT-531 | 531 | 5/3/1 waves, plus targets and recovery week | S2 / REVIEW |
| PCT-531BEG | 531beg | 5s PRO, FSL 5×5, two main-lift exposures and deload | S2 / REVIEW |
| PCT-MADCOW | madcow | Ramping five sets to top five-rep load, week-to-week basis | S2 / REVIEW |
| PCT-NSUNS | nsuns | Nine-set T1 ramp/backoff and top AMRAP-based max review | S2 / REVIEW |
| PCT-TEXAS | texas | Day-specific volume/recovery/intensity and weekly basis | S2 / REVIEW |
| PCT-GZCLP | gzclp | Tier state, failures and progression transitions | S2 / REVIEW |
| PCT-GVT | gvt | Volume prescription and explicit percentage basis | S2 / REVIEW |
| PCT-RIPPLER | rippler | Tiered changing intensity wave | S2 / REVIEW |
| PCT-JT | jt | Tiered powerbuilding heavy/volume structure | S2 / REVIEW |
All nine schemes are active public template configurations and named code values (catalog.js/TEMPLATES and programs.js/CODE_SCHEMES). The real percentagePlanFor → percentageProtocolFor path in training-domain/prescriptions.js executes them; all remain mandatory. Require exact authored basis, per-set percentage/reps/plus targets and week/day/tier, feasible rounding, failed/successful history, max-update rules and recovery. REVIEW here means functional evidence outstanding, not inactive/optional. Cross-link S4 programming and S5 persistence.

## Product coverage and web delta
The 14 stable FeatureCatalog groups remain mandatory references. Map to E requirements rather than replacing them: adaptive-progression (E18–19/E24/E30–34), workout-logging (E17/E20–24/E36), session-adjustments (E25–28), workout-tools (E07/E09–10/E20–21/E28), program-builder (E01–02/E05/E11–16), custom-programs (E16/E32–33), adaptive-cycles (E31–35), exercise-discovery (E08–10), history-and-prs (E23/E36–37), volume-and-trends (E12–14/E38), bodyweight (E38), backup-restore (E39), gyms-and-equipment (E03–04/E07), profile (E01/E06/E40). Each group is expanded in [STAGE1_BEHAVIOR_INVENTORY.md](STAGE1_BEHAVIOR_INVENTORY.md), with 87 stable child rows, stage owners, candidate fixtures and independent expected outcomes. Source coverage of all 144 pinned web modules is recorded in [STAGE1_WEB_SOURCE_MAP.json](STAGE1_WEB_SOURCE_MAP.json).
Build 1016 additions need separate regression trace: strength transition set continuity, actual working-set count in Info, machine/swap discoverability, old unassigned history explicitly assigned to original machine. Build 1011 physical machine/default changes and Build 1010 ULPPL/PPLUL named lift frequency and variant selection remain required. Historical draft #443–448 review by S3/S5/S6; no blind merge. No optional UI deferral removes an engine feature.

## Catalog, shell and template inventory
- [STAGE1_CATALOG_INVENTORY.json](STAGE1_CATALOG_INVENTORY.json): all 383 effective web engine IDs, 369 standard and 14 typed measured Kotlin source matches; zero absent IDs. Web source priority is curated first, then extended; duplicate ID and normalized name shadowing follows the actual web owner. 26 shadowed definitions recorded separately. Each row has a source location and S3 evidence profile. Matching identity is not reviewed physical semantics.
- [STAGE1_WEB_SHELL_INVENTORY.json](STAGE1_WEB_SHELL_INVENTORY.json): 372 public shell identifiers, 346 direct canonical matches, 22 explicit web aliases, four normalized-name candidate matches, zero unresolved names. None of the four name fallbacks is accepted until physical meaning and preserved history identity are proven. Also 75 actual template configurations including all nine percentage schemes.
- [STAGE1_BEHAVIOR_INVENTORY.md](STAGE1_BEHAVIOR_INVENTORY.md): 87 independent outcome rows (74 MAP, 12 GAP, one REVIEW), including both generated/custom paths, all eight declared phase policies, six web duration choices, four experience choices, and core UI. Source-level gaps are assigned to the relevant execution stage.
- [STAGE1_FINDINGS.md](STAGE1_FINDINGS.md): stage-owned findings, evidence baseline and explicit remaining verification obligations.
- [STAGE1_WEB_SOURCE_MAP.json](STAGE1_WEB_SOURCE_MAP.json): 144 source files assigned to child requirements; route-level App.js evidence was obtained using the full pinned blob, not the empty large-file contents result.

## Versioned inventory denominator
| Lane | Count | What is counted |
|---|---:|---|
| Original mandatory requirements | 41 | E01–E41, retained unchanged |
| Product capability groups | 14 | Cross-reference groups, not an additional pass denominator |
| Detailed behavior | 87 | Child acceptance requirements |
| Progression | 13 | Seven public styles plus six explicit baseline/cross-mode requirements |
| Topology | 26 | 24 public SPLITS plus two additional automatic families |
| Percentage protocols | 9 | All active in public templates |
| Canonical exercise identities | 383 | 369 standard, 14 measured |
| Web shell exercise references | 372 | Mapping/alias preservation, not additional unique exercises |
| Template configurations | 75 | Distinct template outcomes |
| Web source ownership | 144 | Source files mapped, not runtime acceptance cases |

These lanes overlap; do not sum them into a product completion percentage. Inventory coverage is different from functional acceptance. The new inventory has no row independently certified merely because its source or candidate fixture exists. Execution stages fill the per-row evidence from exact source/fixture SHAs and required results. Device-only cases remain S8.

## S1 exit
- Both source pins fixed; 41 inherited requirements, 14 groups and detailed source-backed child rows assigned. All declared topology families, progression styles, active percentage protocols, exact catalog and shell identities and template configurations have stable IDs and an owner, expected outcome and candidate fixture/evidence profile.
- Existing tests are candidate evidence locations; do not label the test's entire requirement passed from name, suite success or an older pinned comparison. Review actual assertions and matched current-web executions as part of the owning stage (S2/S3/S4/S5/S6 and final S7), with the baseline evidence documented in STAGE1_FINDINGS.
- Findings have explicit stage dispositions and a new-finding process in the master plan. No parity gap waived. Source-only S1 inventory validation must pass; both required CI suites must pass on the final integrated revision to record formal S1 acceptance. No APK.
