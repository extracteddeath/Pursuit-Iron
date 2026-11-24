# Pursuit Iron — execution status v2
**As of:** 2026-11-24. **Target:** architecture/kotlin-engine-rebuild. **Latest observed integrated SHA:** `824e713ffb0a784d6c8859606c7439b9f9eb4ad7` (#1061).
Authority: [MASTER_DEVELOPMENT_PLAN.md](MASTER_DEVELOPMENT_PLAN.md) and [EXECUTION_MILESTONES_V2.json](EXECUTION_MILESTONES_V2.json). Original inventories are immutable v1 provenance, not current stage assignments.

## Honest baseline
- S0 and S1 completed: required integrated engine/Compose baseline checks (S0 merge `05c02ff2362736439ca1fcabfd7d477bfb2fa366`, runs 45482709594 and 45482709540) and versioned Stage 1 inventory (#1041). These are not training acceptance.
- Canonical denominator: 965 items; 41 inherited rollups; 120 mandatory supplementary checks and five optional review candidates. Do not sum rollups or checks into 965. Original inventory records zero formally accepted supplementary checks. New execution ledger starts with zero **certified** canonical/required supplementary rows until the relevant assertions and layers are reviewed. This is a documentation and verification baseline, not an implementation estimate. Existing passing tests and integrated features are credited as evidence, not thrown away.
- Separate source-certified, device-verified and fully accepted counts. Never require a device result to finish a source-only gate; never claim final acceptance before S9. All applicable device checks remain required. Do not treat percentages of PRs, test cases, or lines of code as percent parity.
- Current active implementation milestone: S2C. Independent verification lane: S2A, then S2B and S2D only after preceding evidence is recorded. No more than one implementation and one independent lane; unblock active vertical journey prerequisites without broadening to all storage or UI.

| Milestone | Work package | Canonical IDs | Supplementary checks | Current gate status |
| --- | --- | ---: | ---: | --- |
| S0 | Integration baseline | 0 | 0 | Historical gate complete |
| S1 | Inventory | 0 | 0 | Historical gate complete |
| S2A | Physical load authority | 6 | 5 | Next independent evidence lane |
| S2B | Standard/manual progression | 14 | 4 | Not source-certified; inspect existing implementation |
| S2C | Specialist progression | 11 | 1 | Active implementation / partial numeric evidence |
| S2D | Factual history and correction | 4 | 3 | Not source-certified; inspect existing implementation |
| S2E | Integrated numerical certification | 1 | 0 | Not source-certified; inspect existing implementation |
| S3A | Workout execution lifecycle | 3 | 3 | Not source-certified; inspect existing implementation |
| S3B | Exercise catalog and swap | 759 | 8 | Not source-certified; inspect existing implementation |
| S3C | Techniques and supersets | 10 | 13 | Not source-certified; inspect existing implementation |
| S3D | Session tools and restrictions | 5 | 5 | Not source-certified; inspect existing implementation |
| S4A | Athlete inputs and constraints | 6 | 14 | Not source-certified; inspect existing implementation |
| S4B | Program and template generation | 105 | 5 | Not source-certified; inspect existing implementation |
| S4C | Cycle and phase construction | 6 | 18 | Not source-certified; inspect existing implementation |
| S5A | Readiness and fatigue | 2 | 2 | Not source-certified; inspect existing implementation |
| S5B | Adaptive cycles and existing Endless Plan | 4 | 4 | Not source-certified; inspect existing implementation |
| S5C | Longitudinal coaching | 1 | 2 | Not source-certified; inspect existing implementation |
| S6A | Onboarding, Today and Plan | 2 | 2 | Not source-certified; inspect existing implementation |
| S6B | Train and exercise UI | 5 | 5 | Not source-certified; inspect existing implementation |
| S6C | History, Progress and Profile | 9 | 11 | Not source-certified; inspect existing implementation |
| S6D | M3 Expressive and feedback | 2 | 2 | Not source-certified; inspect existing implementation |
| S7A | Durable history and ownership | 4 | 5 | Not source-certified; inspect existing implementation |
| S7B | Backup, restore, import and updates | 4 | 4 | Not source-certified; inspect existing implementation |
| S8A | Current parity and Kotlin runtime | 1 | 2 | Not source-certified; inspect existing implementation |
| S8B | Integrated source gate | 0 | 0 | Not source-certified; inspect existing implementation |
| S9A | Private device install/UI | 1 | 1 | Not source-certified; inspect existing implementation |
| S9B | Private device training and recovery | 0 | 1 | Not source-certified; inspect existing implementation |
| S10A | Controlled beta | 0 | 0 | Not source-certified; inspect existing implementation |
| S10B | Production decision | 0 | 0 | Not source-certified; inspect existing implementation |

Rows with zero primary IDs (e.g., S8B, S9B, S10A) still have mandatory milestone exits and shared dependencies; they are not automatic passes.

## Verified evidence retained
All listed PRs passed both required workflows on their exact submitted heads. These are scoped evidence records, **not source-certified or fully accepted rows**. Integrated squash/merge-head CI verification must be recorded separately where required.

| Evidence | Scope | Tested head / checks | Integrated SHA |
| --- | --- | --- | --- |
| #1050 | 19/19 matched fixed percentage numeric cases on eight protocols; exact named prescription intent in corrected history. Shared controlled stock, not complete issuance. | `73f390efebf61596bd1d8824ff486e229e95709c`; [engine 45505107662](https://github.com/extracteddeath/Pursuit-Iron/actions/runs/45505107662), [Compose 45505107804](https://github.com/extracteddeath/Pursuit-Iron/actions/runs/45505107804); artifact 14973453397. | `d857c4887e05b4e79b5d868114e9408872242d93` |
| #1055 | Consolidated tiered journal ledger, recovery reconciliation and reference recheck tests. | `1640a49cb078e15d7e929255046b45ccc474fc50`; [engine 45506761501](https://github.com/extracteddeath/Pursuit-Iron/actions/runs/45506761501), [Compose 45506761732](https://github.com/extracteddeath/Pursuit-Iron/actions/runs/45506761732). | `12e42aef8843a92dda32f6facd9686f7b28ecd5d` |
| #1056 | Real-store owner-fenced fixed reference review; no write or issue authority. | `557e6d9f5a71a514eb7607078fc51d70c710b0e6`; [engine 45507127525](https://github.com/extracteddeath/Pursuit-Iron/actions/runs/45507127525), [Compose 45507127502](https://github.com/extracteddeath/Pursuit-Iron/actions/runs/45507127502). | `dd64e0520d623c8a05e02342c506b30fd8a770bd` |
| #1057 | 13/13 expected GZCLP numeric/replay cases: 9 matches, 4 intentional refusals for skipped/interrupted latest and stale stage/load. Verified actual job log 138845859321 and artifact 14973946947. | `75f630e1fbb98b78f012097ff08864cac74e29be`; [engine 45507348904](https://github.com/extracteddeath/Pursuit-Iron/actions/runs/45507348904), [Compose 45507348933](https://github.com/extracteddeath/Pursuit-Iron/actions/runs/45507348933). | `d50e441bd8332741926213059fb977b567d44251` |
| #1058 | Whole-cycle named recipe + existing structural/volume/timing audit; ordinary issuance remains blocked. | `3009602111727befcd66d6999d610a48fccc7f86`; [engine 45508168826](https://github.com/extracteddeath/Pursuit-Iron/actions/runs/45508168826), [Compose 45508168851](https://github.com/extracteddeath/Pursuit-Iron/actions/runs/45508168851). | `8015698c47892a414b9d04d3115e4ebdb55e945c` |

| #1059 | Single-reference atomic draft, cold read, invalidation and stale signoff | `e159398db44125cab14238a955038f770c6000f1`; engine 45509177100, Compose 45509177401 | `d23c484f946c6ffc580febdf8bcdfdc8d4e4cc0c` |
| #1060 | Revised milestone mapping and structural CI gate | `1d804e857bf57be20996096a7502add17c2ee0dd`; engine 45510019751, Compose 45510019757 | `bf8cf731d4c014f06b1debb607bcdd89492f3ade` |
| #1063 | Atomic multi-week named draft, cold receipts and stale/partial review refusal | `ff870951d0f1d2b24a91828bfe6d22b965663d9d`; engine 45510886564, Compose 45510886588 | `0fed634328f97edc2ba6e4f4874e8a977fffd403` |
| #1064 | Saved references independently rechecked against prior consent, stock, history and full-cycle audit | `bc88a7ccd87169bcde8d0a0ea3b5279f7f1453bb`; engine 45511364003, Compose 45511363998 | `b37840f4a81225ca8c256f8b97fbe4b6aeef2a37` |
| #1061 | Physical scope consistency, off-grid historical regressions and unchanged manual behavior | `4f22b9eb283309e2de2701a41a4e8c00932b8624`; engine 45511310814, Compose 45511310692 | `824e713ffb0a784d6c8859606c7439b9f9eb4ad7` |

Web pin `29faa204212a4d54088b4870341350e605939185`. Numeric coverage = 32 expected cases passed, including four intentional safety divergences; not 32/965 completed work items, not 32 matched superiority cases. Existing superiority scorecard remains NOT_PROVEN_BETTER until comprehensive current evidence.

## Bounded active queue: S2C

| ID | Deliverable / exact exit | Status |
| --- | --- | --- |
| 2C-1 | Eight fixed recipes and GZCLP numeric authority, source intent transport, corrected facts; paired probes on pinned current web; record expected divergences. | Headless scoped evidence exists (#1050, #1055, #1057); remaining end-to-end coverage below. |
| 2C-2 | Persist explicit reference consent with exact revised unsigned draft. Cold read, stale/foreign edit refusal and no old approval inheritance. | Merged #1059 (single), #1063 (atomic batch), scoped tests passed; S2E execution evidence still outstanding. |
| 2C-3 | Complete multi-amendment receipt support and fresh current source/stock evidence; full-cycle signed audit using existing numerical/coach authority. Refuse tampered, stale, missing or mismatched proof; preserve earlier signed history. | #1058, #1063, #1064 merged: recipe audit, atomic multi-amendment receipts and cold full-cycle revalidation. Dedicated final named CAS consent and cold signed review submitted for verification; issuance not enabled. |
| 2C-4 | GZCLP authenticated initial tier/reference and exact completed ledger, explicit reviewed reset/recovery; next target issuance uses same canonical numerical policy. | Partial (#1055), approval/issuance outstanding. |
| 2C-5 | Dedicated named issuance and logging: show minimum plus/rep-out, preserve actual above target, journal finish/cold correction/next prescription, protect prepared/issued sessions. Generated and authored applicable paths; do not require complete visual polish. | Pending. |
| 2C-6 | Run all 9 protocol row normal/boundary/negative/cold real-store journeys and current pinned comparison; record exact assertions and certify applicable source layers; S9 device cases queued. | Pending. |

Stop S2C expansion once these deliverables are source-certified. General backup inventory/corruption and production restore belong to S7; broad UI polish belongs to S6; device-only checks belong to S9. If any is a concrete prerequisite for safe 2C execution, implement and test only the required boundary now, then record it as a dependency.

## Next independent evidence lane
- S2A: merged #1061; see [physical-load evidence](S2A_PHYSICAL_LOAD_EVIDENCE.md) and [additional KG journeys](https://github.com/extracteddeath/Pursuit-Iron/pull/1061#issuecomment-7147665978). Source certification not yet granted; integrated SHA and remaining exact source paths required. Inspect exact methods and execution evidence for stock, bar/Smith, dumbbell/assistance/typed loads, units, rounding, plate layout, machine/gym isolation. Test all physical pathways; preserve verified implementation.
- S2B: strict all-set double and per-set dynamic, linear/ladder/rep targets, e1RM boundaries, manual/basic mode and coaching off, explicit Leveling, DP 200/205/210/215 × missed target regression.
- S2D: correction-driven next suggestion, exact original targets, independent day/station/gym, actual null vs goal RIR, failed/skipped distinction. No silent new weight from another session.
- S2E: minimum generated and custom/manual vertical execution (review, issue, log, Finish, cold reopen, correct, next proposal). Confirm each numerical path without certifying the entirety of S3/S7. Document exact device cases for S9. Then close S2 source gate and advance.

## Evidence log format for subsequent passes
Each assertion entry records stable requirement ID(s), milestone, expected/actual outcome, exact fixture method/input hash, source SHA/web pin where relevant, dated run/job/artifact links, applicable layers (headless, real-store, source UI, device), outcome per layer, remaining dependency, current state and reviewer. A row becomes source-certified only after all applicable source layers and its independent assertions pass; device remains pending until actual S9 run; fully accepted requires applicable device evidence and reviewer acceptance. Roll up progress from the 965 primary IDs and separately the 120 checks; never mark a whole row passed from a single narrow test. Maintain failure evidence and invalidate only impacted layers on changes.
 
All findings remain in [STAGE1_FINDINGS.md](STAGE1_FINDINGS.md) with v2 ownership mapped separately; add new stable IDs for genuine scope gaps, not duplicative symptoms. No P0/P1 waivers, no APK or distribution.
