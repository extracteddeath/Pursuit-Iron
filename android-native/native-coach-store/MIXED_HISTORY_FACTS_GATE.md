# Mixed generated/custom factual history projection — 2026-11-04

This isolated native-coach-store pass adds `NativeKotlinMixedHistoryFactsDomain`
to the existing owner-fenced `NativeKotlinMixedHistoryReadCoordinator` timeline.

- The **latest actual exercise appearance** wins Last, including skipped,
  failed and warmup-only sessions. Never cherry-pick an earlier success.
- MAIN **working** sets alone contribute physical-load summaries; warmups,
  authored drop/myo/cluster follow-ups and skipped/failed attempts cannot
  fabricate working-set records.
- Repeated custom slots keep the exact authored slot ID and effective
  saved-correction event, with immutable source journal audit remaining
  under the existing transaction owner.
- Separate records by exercise, physical load basis and unit, and normalize
  the actual load instead of silently rounding it to a proposed increment.
- Reject malformed chronology, cross-athlete facts, duplicate completion
  receipts and manifest/evidence identity mismatches.

Call `timeline.reviewWorkingSetFacts()` on a successful current-owner
`NativeKotlinMixedHistoryReadResult.Review`. The projection is read-only:
`canPublishPr`, `canIssueProgression` and `canEditProgram` are **false**.
It does not verify who constructed a timeline; authentication, full durable
receipt replay, revision gating and storage reads must remain with the
existing mixed-history read coordinator.

Local Kotlin compilation with API-matching stubs and six focused synthetic
projection tests passed before publication. This is *not* the entire
Android Gradle or device acceptance suite.

Still blocked: 128-session lifetime-history limit; lossless web/JS history
migration and cross-device reconciliation; separate longitudinal coach
decision rules for custom and Composer sessions; actual Compose
onboarding → issue → log → Finish → edit integration, device validation and
APK. No web release, other native branch or engine cutover is authorized.
