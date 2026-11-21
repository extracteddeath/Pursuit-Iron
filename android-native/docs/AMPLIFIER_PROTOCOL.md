# Advanced set amplifiers — protocol, UX and beta acceptance

**State on 2026-11-21:** The Kotlin custom engine now supports separately logged staged myo-reps, rest-pause, same-load clusters, lengthened partials and one/two independently authored physical drops. It preserves the historical single-follow-up byte formats. Custom authoring, live Focus, saved corrections, and verified History distinguish issued targets from performed observations. **Complete generated-program amplifier parity, the full native M3 Expressive UI and physical-device acceptance are not yet verified.** No release approval or APK is implied.

## Single-authority map — maintain this, not a series of workarounds

| Decision | One authoritative policy | Callers must *delegate*, not reimplement |
| --- | --- | --- |
| Valid authored amplifier/number of stages | `AdvancedAmplifierProtocolDomain.validStagedFollowUps`, `validDropSequence` | Custom exercise authoring, cycles, workout snapshots, swaps and codecs |
| Whether a drop may physically start, including the second drop | `AdvancedAmplifierProtocolDomain.eligiblePerformedDropTarget` (internal to the protocol) | `resolveFollowUpEntryLoad` used by native Focus and `CustomWorkoutExecutionDomain.followUpVisits` |
| Valid factual drop completion at the true prior weight/station | `AdvancedAmplifierProtocolDomain.validPerformedDropStage` | `CustomWorkoutJournalDomain` and `CustomSavedHistoryCorrectionDomain` |
| Numeric weight precision, actual RIR and performed-vs-issued distinction | `KotlinSetNumberAuthorityDomain.review` | Generated and custom journals and history correction |
| Session seconds including staged advanced work | `CustomWorkoutTimingDomain` | Workout Preview, Coach audit, short session, Swap / UndoSwap and Strength repair |
| Durable set/history truth | Owner-fenced journal/history stores | Compose and coaching read verified receipts; never invent or overwrite them |

**Maintenance rule:** Any new stage type or load decision changes its existing authoritative protocol first. Add corresponding cross-path tests for authoring, Focus, live receipt, saved correction, crash replay and native presentation. Do not add a fourth handwritten weight inequality to a screen or history adapter to make one test pass. A changed actual first drop can remain factual but must never silently rewrite the authored second drop or enable it without the protocol's exact physical-stage eligibility.



## Cold-reopen and issuance gate (reviewed November 17, 2026)

The native v5 custom-workout snapshot codec previously contained an outdated
**myo-only** staged-amplifier condition. Even though staged rest-pause and
cluster journaling and UI worked in the independent engine, this validator
could reject a legitimately authored workout on actual snapshot encoding,
issuing, owner-fenced journal reopen, and swap decoding. A passing pure reducer
test cannot establish file-backed beta readiness.

The codec now delegates to
`AdvancedAmplifierProtocolDomain.validStagedFollowUps`, the exact policy used
by custom authoring and cycles. The schema/wire version is **unchanged**:
existing single-follow-up and v5 myo bytes retain their old encoding.
New file-store regression fixtures must roundtrip staged rest-pause and
cluster plans and real stage receipts through both snapshot and journal
codecs, including restart, early stop, finish and historic byte stability.
This does not replace physical Android cold-process and backup testing.

## Two-stage physical drop sets — authored Kotlin and native Train (November 17, 2026)

An athlete can now explicitly author **one or two drops** after a hard
full-range activation set. Both reductions are separate **manufacturer/gym
stock-verified physical weights**, with the same unit and loading basis as
the activation set and a strictly declining sequence:

- Activation: an example 185 lb Smith total
- First drop: an athlete-confirmed 155 lb Smith total
- Second drop: an athlete-confirmed 135 lb Smith total

These values illustrate the protocol, not automatic weight selection.
The engine never derives or duplicates a second weight by percentage.
Without independently reviewed physical stock for **every** reduction,
the program cannot be approved. The athlete may still perform fewer
drops; the optional sequence is ended without inventing remaining reps
or stage receipts. The **second reduction is not unlocked** unless the
first drop was actually logged at its planned reduction and on the
activation physical station. A changed first-drop observation stays in
History without auto-authorizing drop 2. Corrections cannot silently
rebase the main weight after logged drops.

Native Plan keeps the two authored loads in an expandable set-attached
technique disclosure. Train shows distinct drop 1 of 2 / drop 2 of 2
weights and logs their actual reps and load independently. Saved History
retains the two separately prescribed weights, distinct from actual
observed loads. The extra work is *not* two additional normal sets.

The new `CustomSetAmplifier.secondDropLoad` property is transported only
in custom-cycle draft v5 and issued workout snapshot v6 (including
swap slot payloads). Existing v1–v4 cycle files and v1–v5 workout
snapshots keep their original byte identities. The journal's existing
versioned stage-key storage can persist new two-drop actuals without
altering legacy records. Cold replay, historical byte stability and
fail-closed stock policy are regression tested.

Stage-specific loads are **opt-in and custom-workout-only** in this
milestone. Machine-agnostic generated-program presets, automated
technique placement, and physical-device acceptance remain open. No APK.

## Final-set lengthened partials (native custom plans, 2026-11-17)

**Athlete-facing contract:** Long-length or *lengthened* partial reps can be
authored as an optional continuation of the **final regular working set only**.
The main set stores the actual full-ROM repetitions first. A separate
`LENGTHENED_PARTIALS` follow-up stores only **1–8 observed partial repetitions**,
using the same logged load and physical machine, with no inserted rest.
An athlete may Skip the optional extension, correcting observations remains
explicit, and neither a skipped nor an unperformed partial becomes a receipt,
extra normal working set, full-ROM PR, or earned progression. There is **no
automatic last-set technique insertion** on every accessory.

The source web app (Build 1015, PR #679) now attaches a compact
`Last set · Lengthened partials` disclosure to the actual final working-set
row in both Target and Last views, including after completion. Native custom
Plan/Preview and Train mirror the set-level attachment: M3 authoring offers
the technique **only on the final working set**; source and workout snapshot
validators reject any earlier-set placement. In Train, the technique marker
remains on the main working row even after logging; the optional partial
follow-up is editable as a separate factual visit. Saved History must label it
`Lengthened partials · partial reps` without folding the count into full reps.
Use no extra equipment-based load conversion.

**Evidence caution:** Studies comparing lengthened partials and full ROM
sometimes find similar hypertrophy rather than universal superiority, and
limited calf-specific research on extending a set beyond failure should not be
generalized to all exercises. The appropriate ROM and load profile differ by
movement; the partial extension is **optional**, on stable suitable exercises,
and should stop when technique or comfort changes. Do not automatically
place it on heavy technical lifts, in peak-strength sessions, or when
readiness/injury concerns argue otherwise.
Sources:
- PeerJ (2025), trained individuals, partial vs full ROM:
  https://pubmed.ncbi.nlm.nih.gov/39959841/
- Calf past-failure partials (2025):
  https://pubmed.ncbi.nlm.nih.gov/39995432/
- Overview of resistance training prescription (2026):
  https://pmc.ncbi.nlm.nih.gov/articles/PMC12965823/

This covers **athlete-authored custom cycles** and their durable native
journal/history, not automatic generated-plan coaching or all multi-drop
protocols. Every claim of beta safety still requires on-device acceptance.

## Evidence and selection

- A 2026 systematic review/meta-analysis found that advanced systems did **not** produce a meaningful overall hypertrophy advantage; rest-pause may have a small advantage and drop sets/cluster systems were often comparable when effort/volume were matched. Use techniques to **save time**, for an optional finishing stimulus, or for a specific programming purpose, not as magic extra growth. https://pubmed.ncbi.nlm.nih.gov/41718208/
- Drop sets can produce similar hypertrophy in less time. They are demanding, and a drop is a *deliberate, explicit* load change, never a silent change to a straight working set. https://pubmed.ncbi.nlm.nih.gov/37523092/ and https://pubmed.ncbi.nlm.nih.gov/41920484/
- Børge Fagerli's myo-reps and Barbell Medicine's guidance: activation at around RPE 8 (often 12–20+ reps), followed by the **same weight** in short 3–5-rep mini-sets, usually 20–30 seconds between. Keep going only while performance and form hold; reach 3 reps on the final mini-set or stop sooner when falling short; at most five mini-sets. An optional stricter rule ends on first drop below the selected 4–5 target. https://www.barbellmedicine.com/blog/myo-reps/ and https://www.predatornutrition.com/en-US/articlesdetail?cid=an-interview-with-borge-fagerli-aka-blade
- Rest-pause can be time-efficient with short (~20 s) rests between additional efforts, but greater acute fatigue and discomfort justify conservative optional placement and stopping at a missed rep target rather than forcing all planned repeats. https://pmc.ncbi.nlm.nih.gov/articles/PMC11127831/ and https://pubmed.ncbi.nlm.nih.gov/28617715/
- Supersets can be time-saving but should remain a separate pairing feature; do not conflate linked supersets with amplifiers. https://pubmed.ncbi.nlm.nih.gov/39903375/

## Conservative default placement

- **Opt-in/coach-enabled only.** Default to conventional straight sets. Use at most one amplifier in a session initially, usually on the **last working set** of a stable machine/cable/isolation accessory for intermediate/advanced hypertrophy work.
- Avoid automatic myo/drop/rest-pause on heavy technical barbell work, competition-lift practice, peak-specific strength lifts, first sessions, recovery/deload days, injured regions, severe fatigue, or already-shortened sessions where safety/MEV would be compromised. Honor coach/fatigue/advanced-technique OFF switches and athletes' individually authored choices.
- A cluster is a *planned* quality/intensity method with short rests and submaximal efforts. Do not portray clusters as failure-based myo-reps.
- Retain weekly dose budget: one activation+mini-set sequence is one technique block. Extra mini-sets are **not** automatically 3–5 additional regular working sets, progression entries, or personal records.

## Full sequence contracts (myo merged; rest-pause staged slice in review)

| Technique | Issued protocol | Actuals and stopping |
| --- | --- | --- |
| Myo-reps | Activation 12–20+ at ~1–2 RIR; up to 3–5 mini-sets of 3–5 reps; same physical machine & load; ~10–30 s rest (20–30 s conservative default) | Persist **each** mini-set with its actual reps and any measured RIR. Early stop when reps hit the selected floor (usually 3 after a higher preferred target), fall below the floor, fail the stricter optional target, or form fails. Never force 3 rounds after a miss. |
| Drop set | Hard activation set; one or two deliberate 15–25% load reductions, little or no rest, each rounded to actually available stock | Each stage stores **its own verified load and reps**; display drop 1/2 separately. Missing plates/stack steps or same-weight "drops" are invalid. |
| Rest-pause | Hard opening set, 1–3 smaller 2–5-rep repeats, 15–30 s rest, generally same load | Actual reps per repeat; stop at shortfall or quality break. Distinguish from myo-reps activation/stop strategy. |
| Cluster | Athlete-authored 2–5 short clusters of 1–4 **planned** quality reps with 15–45 s intra-rest; in this stage-supported tranche every cluster uses the **same activation load and physical station** | Save each performed cluster as its own actual receipt. Stop after any unplanned (high/low) rep count, athlete End sequence/Skip/Failed or technique decline. Never generate missing stages, count clusters as regular sets or turn them into failure-oriented myo-reps. Distinct stage weights need a separately versioned stage-by-stage prescription and are **not** issued yet. |

## Native UI acceptance

1. **Plan and Preview:** a tidy Material 3 Expressive accent row says `Myo-reps` / `Drop set` / `Rest-pause` / `Cluster`, with one-line intended sequence and expandable explanation. Active method visually belongs to the **specific last working set**, not a detached exercise footer. Avoid duplicated instruction blocks and oversized decorative labels.
2. **Workout Focus:** activation set with normal weight/rep/RIR controls; after its *recorded* completion show a nested numbered sequence (`Mini 1 of 5`, etc.) with a slim progress strip, actual reps, same-machine weight, prominent Complete, and lightweight End sequence. Stage-specific entry state must survive Focus/All, rotation and cold restart. Large-font/TalkBack device acceptance remains pending.
3. **Rest:** 20–30 s between myo mini-sets, counted from a **durably acknowledged** recorded mini-set. No normal 90–180s rest until the sequence is resolved. Resume timer after background/process death. Drops should not trigger long rests.
4. **Early stop/undo:** a 5,5,4,4,3 sequence resolves without a sixth set; a 5,4,2 sequence stops at the missed floor without fake unperformed sets. Athlete may stop on form, Skip/Fail stages, correct actuals or undo according to existing journal authority without revising another exercise.
5. **Record ownership:** append-only owner/revision-fenced events for each mini-stage, unique set/stage identity; proof on cold replay, correction and existing legacy one-follow-up journal data, with **versioned** backward-compatible file/source codecs. No made-up actuals or silent schema migration.
6. **History and Progress:** show `Activation 14 · mini 5+5+4+3` or drops with **actual weights**, and distinguish technique stages from regular sets in volume/progression, PR and machine A/B history. Keep activation main-set progression and actual RIR provenance intact.
7. **Tests:** pure stop-state/range/quality evaluator, stage identity and ordering in supersets, load rounding/decline for drops, time/rest cues, crash-safe persistence, correction/undo, source reissuance, custom/generated parity, accessibility + screenshots, no legacy JS dependency. No APK before full native UI acceptance.

This contract deliberately separates a science-informed recommendation from an *issued and independently logged workout*. The custom myo-rep and rest-pause slices have been merged. This staged cluster implementation uses the same established visit IDs, explicit same-load/machine constraints, optional stop and replay. No new persisted field is required; older single-follow-up cluster snapshots stay as-is. Multi-stage **drops still require explicit separately verified weights for every drop** and cannot be faked by duplicating one follow-up. Generated program support, web parity, full device accessibility and end-to-end beta acceptance remain open after CI. No APK before the full UI is complete.


## Executable drop-stage authority (single and multi-drop)

A drop is never authorized merely because the issued activation *target*
was heavier. The shared native journal/Focus execution projection now
requires **every** drop stage, including the legacy one-follow-up case,
to be individually verified as a physical reduction from the immediately
preceding **completed actual** stage at the same machine/station and in the
same load unit. The authored load sequence must itself be structurally
valid (positive, strictly descending, same basis and unit) before any
optional drop can be offered. A changed or missing actual activation,
same-weight "drop", lighter-than-planned activation, or absent drop load
ends the optional sequence without inventing or reweighting a set.

Focus's new-entry policy refuses to prefill a single drop when the
performed activation does not exceed the authored drop load; the
authored target is otherwise preserved rather than silently derived
from the old first working set. This is read-only eligibility and
editable-field initialization, not permission to modify issued sets,
future progression, old recorded receipts, or a physical station.
Headless regressions include unchanged/lighter activation, invalid
authored reductions, valid lower loads, machine/unit mismatch and cold
journal replay. The full native device QA and broader periodization
parity gates remain open.
