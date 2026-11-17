# Advanced set amplifiers — protocol, UX and beta acceptance

**State on 2026-11-17:** PR #678 merged the Kotlin custom journal's **opt-in 3–5-stage myo-rep sequence**, with one owner/revision-fenced factual receipt per performed mini-set, early stop, stage-aware Focus/All, history, short rest and versioned disk replay. This next change extends the exact same durable stage authority to **1–3 same-load rest-pause efforts**, only when explicitly authored. Old one-follow-up workouts retain legacy encoding. **Staged rest-pause still requires CI and device review; this is not beta release approval.** Multi-drop, clusters, generated-program amplifier issuance, web runtime parity and full native device acceptance remain open.

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
| Cluster | Planned 2–5 short clusters, usually 1–4 quality reps and 15–45 s intra-rest | Preserve intentional submaximal rep targets and load, don't require failure or count each cluster as a regular working set. |

## Native UI acceptance

1. **Plan and Preview:** a tidy Material 3 Expressive accent row says `Myo-reps` / `Drop set` / `Rest-pause` / `Cluster`, with one-line intended sequence and expandable explanation. Active method visually belongs to the **specific last working set**, not a detached exercise footer. Avoid duplicated instruction blocks and oversized decorative labels.
2. **Workout Focus:** activation set with normal weight/rep/RIR controls; after its *recorded* completion show a nested numbered sequence (`Mini 1 of 5`, etc.) with a slim progress strip, actual reps, same-machine weight, prominent Complete, and lightweight End sequence. Stage-specific entry state must survive Focus/All, rotation and cold restart. Large-font/TalkBack device acceptance remains pending.
3. **Rest:** 20–30 s between myo mini-sets, counted from a **durably acknowledged** recorded mini-set. No normal 90–180s rest until the sequence is resolved. Resume timer after background/process death. Drops should not trigger long rests.
4. **Early stop/undo:** a 5,5,4,4,3 sequence resolves without a sixth set; a 5,4,2 sequence stops at the missed floor without fake unperformed sets. Athlete may stop on form, Skip/Fail stages, correct actuals or undo according to existing journal authority without revising another exercise.
5. **Record ownership:** append-only owner/revision-fenced events for each mini-stage, unique set/stage identity; proof on cold replay, correction and existing legacy one-follow-up journal data, with **versioned** backward-compatible file/source codecs. No made-up actuals or silent schema migration.
6. **History and Progress:** show `Activation 14 · mini 5+5+4+3` or drops with **actual weights**, and distinguish technique stages from regular sets in volume/progression, PR and machine A/B history. Keep activation main-set progression and actual RIR provenance intact.
7. **Tests:** pure stop-state/range/quality evaluator, stage identity and ordering in supersets, load rounding/decline for drops, time/rest cues, crash-safe persistence, correction/undo, source reissuance, custom/generated parity, accessibility + screenshots, no legacy JS dependency. No APK before full native UI acceptance.

This contract deliberately separates a science-informed recommendation from an *issued and independently logged workout*. The custom myo-rep slice is merged. Staged rest-pause here reuses its versioned receipts, explicit same-load guards and early-stop behavior; multi-stage drops and clusters remain separate work and cannot be faked by duplicating a single follow-up. Generated program support, web parity, full device accessibility and end-to-end beta acceptance remain open after CI. No APK before the full UI is complete.
