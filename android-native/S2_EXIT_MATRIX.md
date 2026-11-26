# Stage 2 finite exit evidence (2026-11-25 US Eastern)

Target: `architecture/kotlin-engine-rebuild`. Latest observed integrated base `9141e2a5c8335423c164d65eded6a185f5922d48` (#1138). Frozen scope remains 36 canonical IDs and 13 mandatory supplementary checks. All device-required cases remain queued for S9. Do not infer a source-certified row merely from passing CI or a named test.

## Verified integrated evidence

The table records exact submitted heads, both successful workflows, and observed integration. The newest tested head was merged after confirming no intervening base-only changes; push-workflow results are not exposed by the PR-filtered endpoint, so this is exact-head plus integration provenance, not a claim of independently retrieved push CI.

| PR | Scope | Tested head | Engine / Compose run | Integrated SHA |
|---|---|---|---|---|
| #1106 | Fixed specialist review, cold recovery, Plan-to-Train handoff | 6470db106eb737459390bfa18ed6f4faa6efccc8 | 45647012318 / 45647012003 | baa6796d1a8c33d2fde56ad2e4344c43316a86e3 |
| #1107 | Generated manual/coaching-off through correction and next issuance | b18fba50023b8f5f0350d953e6fb94001dc54fe7 | 45649870415 / 45649870651 | 7b654ff4e23841d1eb1b349bb4e48a141df56547 |
| #1114 | Single-root GZCLP complete scheduled execution | 48209a7b76c4a603cbdc4e2ba5f919514cee0021 | 45704916529 / 45704916586 | abbbeda3e976e4fb6ef599abdb25cad71f96f485 |
| #1121 | Multi-root authoring, durable consent, initial/support execution | 899216ff60d86e8dbca63f026ae59b1d6b07995b | 45707904329 / 45707904171 | 56923fb43cd04c36085b6038b05b2d563c2769f0 |
| #1124 | First multi-root transition native journal | a3dad16d173b19aeed4380feca97d68c9753bb98 | 45711846227 / 45711846167 | 321ef7bf1a85a19d5c367eebfaa6d5f33bbf703e |
| #1125 | Consecutive multi-root projection/replay | 669dcc5bc0ddefaa8e2e38c89078d43a277d95f0 | 45712167496 / 45712167481 | 9786af6b2ffb1ec4d24bb158d7275b54de4a70b9 |
| #1127 | Multi-root Plan-to-Train | 30cf3f744b982b2fa0508d3cba52b866f6a224e8 | 45713382245 / 45713382304 | 90803f047648fff4330d62c8bca8212f2aed5f6c |
| #1128 | Interleaved supporting days after transitions | 0a74e0dd2e677bdc81d3b99ddc04a46927cb9ed1 | See merged #1129/#1130 and later exact-head suites | 9e3aa09cc8d8112a11a90716b58d4281055deaca |
| #1129 | Corrected stale multi-root approval replacement | 8885bd5beea0cdd4b2ab1a6fce9e8c97b928ca18 | 45714912539 / 45714912544 | 27cae72d175195a53050f635f5eda99627911630 |
| #1130 | Frozen multi-root recovery inventory | e96c6d43ccea15c48b65709c0567711a337f2ed8 | 45715288236 / 45715288383 | 1f599ec4eec07ce0d05a3e9862f34bee87170a20 |
| #1133 (includes #1131/#1132) | Explicit e1RM; strict DP correction; independent station records | 06ef7ffa9ef3f9a96c29c5e6740a9c5941651a2e | 45717133984 / 45717133905 | ac44fb52c78f696b9067ffdac32a75ba3b4237ad |
| #1134 | Actual paired ordinary progression; editor-only Leveling and cold corrected history | d37c2b26ec3f6fc21b6eda67123ec9cd7e8ff0d1 | 45717802290 / 45717802278 | 5d73047fb1d2e4b9665f2a7b18a090f678010bb2 |
| #1135 | Real typed input, exact stock/units, corrected measured history | 8116619886d48e3cd551ddd5ef62a5c4e81262f1 | 45718056596 / 45718056556 | 08a689356047810c8c227ebe6a448b37ad8d7e92 |
| #1137 (includes #1136) | Rep-target and ladder next issue; common physical authority for landmine calibration | 2e27e492b25b8e09be75431d618448f00bf02c15 | 45723626188 / 45723626152 | c14dba9a3583f576420c270d054bc42a3d256fa2 |

| #1138 | Generated bodyweight rep progression, coaching off, actual unloaded facts, consent and cold issue | f3696a617598d16de275606a0af50af102fdde6b | [45725849557](https://github.com/extracteddeath/Pursuit-Iron/actions/runs/45725849557) / [45725849656](https://github.com/extracteddeath/Pursuit-Iron/actions/runs/45725849656) | 9141e2a5c8335423c164d65eded6a185f5922d48 |

### Integrated source equivalence checkpoint

Both #1138 actual workflow jobs succeeded, including native journal persistence/offline recovery, source compilation, fixed/tiered/ordinary web comparison, and Compose source contracts. GitHub compare in both directions reports the same common ancestor c14dba9a3583f576420c270d054bc42a3d256fa2 and the same three modified paths. Direct contents reads on tested head f3696a617598d16de275606a0af50af102fdde6b and integrated SHA 9141e2a5c8335423c164d65eded6a185f5922d48 returned identical bytes and blob SHAs for all three paths:

- EXECUTION_STATUS_V2.md: 983e3522762c837351e87b25fc63b2f61f1ab9cd
- S2_EXIT_MATRIX.md: 0cde16060b0110ee3969efbd18ab9d4c5fa46dcd
- NativeKotlinGeneratedProgramJourneyTest.kt: 3ccb4ab90ae590eba33fe2e9c5ad78e663cc4370

This establishes tested content equivalence for the reported complete changed-file set at the shared base; it is not independent push-triggered CI or a substitute for reviewing the mapped acceptance assertions. The connector's workflow lookup filters to PR events, so no push-run success is inferred.

Failed #1136/#1137 heads 72858043a94a4f4e50112da1e60110460bad6daf and 451e5f84b8d9bdbda3e0f9effc1cb56846aa4539: engine 45719339345 / 45719846500, exactly one failing test each (missing two-block continuity policy in landmine test fixture). Corrected fixture supplies required reviewed policy; no production audit weakened. Both corrected suites passed.

## Comparison results, not a superiority verdict

Frozen Stage 1 web pin `29faa204212a4d54088b4870341350e605939185`. The five relevant ordinary progression web modules (performance, loading, history, history-contract, exercise-db) were checked byte-identical to web main `32e3f2bd7cb12107814123d677d5d43d70df97f4` on 2026-11-25. Observed #1134 job 139568422262: 12/12 expected outcomes, nine exact matches and three declared differences (basic effort rules, linear floor versus web ceiling, original-goal RIR assumption for e1RM estimate). Neither floor nor effort difference is evidence of superiority. Latest combined run 45723626188 artifacts: ordinary 15074345379, fixed percentage 15072889747, tiered 15072579035. Previously verified 19 fixed numeric matches and 13 tiered expected cases (nine matches, four safety refusals). Total 44 scoped expected numeric cases, not 44 certified acceptance rows. Overall engine superiority remains NOT_PROVEN_BETTER.

## Finite outstanding source acceptance review

| Milestone / exact IDs | Verified scope and exact fixtures | Remaining review before certification |
|---|---|---|
| S2A: GYM-ROSTER, GYM-UNITS, GYM-IMPLEMENT, GYM-MACHINE, GYM-PLATES, SET-AUTHORITY; CHECK-NUM-LOAD, CHECK-WORK-PLATES, CHECK-GYM-EDIT, CHECK-GYM-MACHINE, CHECK-GYM-UNIT | S2A_PHYSICAL_LOAD_EVIDENCE.md; #1067/#1068 saved gym and source UI; #1074 gym change; #1101 physical boundaries; #1133 correctedStationRecordMovesOnlyWithinItsOriginalIssuedGymAfterColdRecovery; #1135 mixedMeasuredSessionColdReplaysAndFinishesAsOneRealHistoryWorkout; #1137 newBlockCalibrationCanExplicitlyRetainZeroAddedLandminePlates; signedMachineASlotPreparesAndColdOpensDespiteGymBDefault. | Review exact source/UI assertions for each ID, including actual station-specific earned loads, physical inventory and calculator routes. Corrected record separation is not automatically proof of a full station progression journey. |
| S2B: SET-START, SET-MANUAL, SET-LEVELING, PROG-AUTO, PROG-DOUBLE, PROG-DYNAMIC, PROG-LADDER, PROG-LINEAR, PROG-E1RM, PROG-ASSISTANCE, PROG-BODYWEIGHT, PROG-LEVELING, PROG-REP-TARGET, PROG-COACH-OFF; CHECK-NUM-FIRST, CHECK-NUM-DP-REGRESSION, CHECK-NUM-LEVELING, CHECK-NUM-OFF | #1105 four-set historical regression; #1107 generatedManualMethodAndCoachingOffPreserveCorrectedHistoryAndNextTargets; #1133 generatedE1rmMethodUsesCorrectedEffortAndExplicitNextWeekConsent and correctedGeneratedDoubleProgressionRevokesAnEarnedIncreaseWithoutChangingIssuedTargets; #1134 levelingUsesActualPyramidAndNeverRewritesSignedProgramOrOtherCompletedSets; #1137 generatedExactRepTargetsEarnOnlyOneRepWithoutChangingWeights and generatedRepLadderEarnsAdjacentWeightAndResetsToItsActualRepFloor; BasicProgressionExtendedCoverageTest, CustomBasicHistoryProgressionDomainTest, AssistedCounterweightProgressionDomainTest. | #1138 generatedBodyweightRepProgressionUsesNoLoadAndNoAuthorizedWeightStep covers generated full-week completed actuals with null load/unit/RIR/station, independently expected +1 exact rep and all advanced ordinals, zero authorized weight step, read-only review, stale consent refusal, fresh consent, cold issue and immutable prior workouts. Candidate strengthens the independent rep-target assertion at draft transport; pending CI. Remaining: assisted progression journey/source review and saved method selector/source routing for all methods. Same-prescription e1RM is verified; changed-phase calibration is a separate capability (do not claim current e1RM phase re-prescription parity). If phase re-prescription is needed for S4C/S5C, record dependency rather than indefinitely broadening S2. |
| S2C: PROGRAM-SPECIAL-TARGET, PROG-WAVE, PCT-531, PCT-531BEG, PCT-MADCOW, PCT-NSUNS, PCT-TEXAS, PCT-GZCLP, PCT-GVT, PCT-RIPPLER, PCT-JT; CHECK-GEN-RECOMMEND | #1050/#1057 pinned probes; #1096/#1098 fixed real-store; #1103 generated 5/3/1; #1104/#1106 native fixed calendar and handoff; #1114–#1130 tiered single/multi-root calendar, support days, stale approval and recovery inventory. | Minimum specialist implementation is integrated. Review exact protocol normal/boundary/negative/cold assertions and all seven method recommendations including manual override; authored wave transport and issue proof. Do not reopen tiered architecture or require broad backup/restore, UI polish or device checks here. |
| S2D: SET-MISSING-RIR, SET-FAIL-SKIP, HISTORY-CORRECT, PROG-CORRECTION; CHECK-NUM-EFFORT, CHECK-NUM-SCOPE, CHECK-NUM-CORRECTION | savedNearFailureCorrectionRevokesPreviouslyFavorableProgramEvidence; correctedUnknownActualRirIsNeverFilledFromTargetRir; diskVerifiedUpperAndPushSamePurposeProgressIndependently; machineCorrectionRecoversBothCrashWindowsAndRemainsIdempotent; #1131/#1132 through #1133; #1135 measured correction. | Verify source coverage for independent same-day/slot/station and skipped/failed outcomes. Preserve actual RIR null, do not equate assumed goal to actual. |
| S2E: SET-CHANGE-CONSENT | generated basic/manual, authored named/tiered, explicit e1RM, Leveling, typed, rep-target/ladder, actual read-only proposal to fresh consent and cold issue. | Dependency review S2A–D; then integrated numerical certification. After source exit, audit duplication/shared numbers/performance before S3. |

No automatic whole-row certification, no APK. Source verification and S9 device acceptance are separate gates. Prior snapshots remain in git history; do not continue obsolete implementation work from them.
