# M225 / Build 815 / Engine 0.64.14

Recovered the complete M224 repair from the saved independent Build 814 package. Its 14 finding families remain covered by the permanent hardening suite. The maintained app source, engine modules, source evidence and independent export now include the recovered changes.

Additional reproduced defects fixed:

- Tiered progression ignored `readinessDisrupted`, `recoveryLimited`, string readiness and case/whitespace variants, allowing a 50 lb exposure to advance to 55 lb during recovery. One shared exposure resolver now covers ordinary and tiered evaluators.
- Exercise-level and set-level context could disappear in serialization or be ignored on replay. Session, exercise and set flags are additive; narrower false values cannot clear an enclosing limitation. The real logger preserves these flags, and generated/custom history consumers honor them.
- Imported booleans, arrays and objects could become numeric evidence (`true` became 1, `[15]` became 15). Only finite numbers and nonblank numeric strings qualify.
- A malformed `updatedAt` could freeze an older history revision. Invalid revision timestamps fall back to the validated workout date. The newest valid revision wins regardless of import order.
- The export parity check called the standalone generator on both sides. It now compares production generation against independent generation directly.

`verification/m225-history-provenance-test.mjs` checks 63 context/provenance routes, generated/custom logger roundtrips, strict numeric evidence, malformed revisions, additive flags and immutable tiered replay. M224 and M225 regressions are mandatory in both release verification and the adaptation contract group. The independent exporter includes both guides, both shared modules and both suites.

Ordinary progression retains its existing policy: an interrupted incomplete exercise cannot progress; a fully completed exercise may remain useful even if the rest of the session was interrupted. Tiered replay conservatively holds flagged exposures. Historical logs without original targets/units cannot reconstruct missing information. Session clocks remain estimates. Automated phone-sized browser tests do not certify physical Android hardware.

## Completed verification

| Check | Result |
|---|---|
| M224 hardening | 14 finding families and 218 percentage cases pass |
| M225 provenance | 63 context/provenance routes plus numeric/revision checks pass |
| Application release | All release gates pass; 54 authored modules parse; 79 offline entries present |
| Engine contracts | Generation 8 gates, adaptation 20 gates, quality 3 gates; all pass |
| Standalone | 32 semantic suites, 48 engine modules and 653 headless declarations pass |
| Production/export parity | Production generator matches independent generator; three splits and 177 cells agree |
| Browser | All 11 workflow suites pass locally with Chrome Headless Shell 148 at phone viewport sizes |

Browser coverage includes the actual percentage workout/save path, pounds/kilograms, deloads, custom progression, original targets, typing/resume protection, failed-save retention, renderer-crash offline recovery, PWA update/data preservation, cycle lengths, small-phone layout and theme/navigation flows. These are automated browser checks, not a physical Android device certification.

## Publication status

Build 815 is committed locally and ready for review. No changes from this turn have reached GitHub or the live app. Automatic approval review rejected the verification-branch push because it required explicit user authorization to export the source to the public `extracteddeath/Pursuit-Iron` repository. The rejection remained after repository verification and recovery of earlier publishing instructions. Publication requires that explicit approval. After approval, push the verification branch, confirm its CI jobs, then update the existing production branch/origin.

A branch under `verification/**` runs all release, engine, export and browser jobs before the production branch is updated. The delivered integration patch applies to public base commit `ee1988e2a2e9deec16df3ed65df20adcb1731dab`; it contains the complete recovered repair and this follow-up. It includes no workout database, credentials or environment configuration.
