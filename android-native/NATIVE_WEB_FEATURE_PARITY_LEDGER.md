# Pursuit Iron — web-to-native feature parity ledger

**Source:** current web `CHANGELOG.md` through Build 998, `modules/release-notes.js`, canonical web code, and Kotlin rebuild source. Maintained as a behavioral acceptance checklist, **not** a list of cosmetic components to copy. The new Kotlin implementation may improve the web UX, but cannot silently drop a beneficial web feature. An existing button or matching label is *not* evidence that the feature works.

**Status vocabulary:** **Implemented/source-tested** = exact behavior is in native source, with explicit tests/CI required before release; **Partial** = some native behavior exists but meaningful web capability is missing; **Open** = not verified in current Kotlin rebuild and must not be advertised as available; **Adapt** = web-specific mechanism not appropriate for native Android, preserve the user value by a native equivalent. No status is a release sign-off without real phone/foldable/TalkBack acceptance.

## Priority 0 — functionality that must survive the rewrite

| Web capability / changelog proof | Current Kotlin result | Missing acceptance requirement |
| --- | --- | --- |
| Search exercise replacements in **workout and program** (v3.14.9, B519) | **Partial.** Program draft now supports named search and explicit replacement. Native issued workout still lacks a journal-safe swap flow. | Workout swap must preserve unaffected completed sets, ongoing timer, workout identity and crash recovery. No old target may move to a new exercise without reviewed approval. |
| Alternatives ranked by **movement pattern, family, program muscle gap** (v3.2.3, B467) | **Partial.** `CustomExerciseSwapRankingDomain` orders reviewed program options by documented pattern/family/credited muscle overlap, with visible reasons and no numeric load carryover. | Context-aware *program coverage gap*, exercise role, session fatigue and per-day training intent must be added and tested; static same-pattern ranking is not full web parity. |
| Swap choices from **active gym**, including implied equipment (v3.41.0, B581; v3.38.0, B578) | **Partial.** Program authoring uses the current verified native gym, stock, attachments and facilities; no unverified options are invented. | Audit equipment implication rules, multiple gyms, attachment variants and source revision changes during both program and workout swapping. |
| Swap **undo, confirm and scope** (v3.8.0, B488; B905; v3.16.1, B523) | **Partial.** Program replacement uses a new slot identity, explicit confirmation, immutable revision/undo receipt and re-audit; blocks on protected week overrides/supersets. | Native issued-workout undo and optional “Update my program” must be an independently auditable transaction; protect unrelated days, cycle phases and overridden weeks. |
| Minimize/resume after an in-session swap or program update (v3.92.0, build 640) | **Open.** Native journaling/recovery exists but this combined scenario is not supported by a swap transaction. | Crash/relaunch/recovery test: completed untouched sets return unchanged, swapped-out lift starts fresh, no duplicate exercise IDs or phantom completion. |
| “Why this exercise?” explain ranked candidates and source (v3.40.0 B580) | **Partial.** Native shortlist gives pattern/family/muscle evidence only. | Show the actual engine's per-candidate scoring, runner-up, constraints, coverage and later repair decisions; never fabricate a justification for a manual addition. |
| Program editing stays as **recoverable draft** until explicit Save/Discard (v3.8.0 B488) | **Partial.** Native custom-cycle edits save an undoable *draft revision*; approval is separate. | Validate unsaved-versus-saved contract, close/back confirmation, crash recovery, and independently approved future issuance. |
| Completed **Target vs Last** and full exercise history in workout (Build 824; subsequent updates) | **Partial/implemented.** Native target/last references and correction-aware history access exist. | Verify all modes, every set, dynamic progression, 200% text, and no duplicated or mismatched previous sets. |
| Equipment-rounded progression and effective RIR, including no supplied RIR (B941; recent progression fixes) | **Partial.** Kotlin load/rep/coaching reviews and exact stock checks exist. | Matched edge-case outcome comparison: separate exercise/day progress, chronic and intra-session fatigue, honest missing-RIR handling, realistic load increments and no regression. |
| Injury/restriction adjustments **during a workout** (B941, B905) | **Partial.** Restrictions and coaching protections exist in native logic. | Quick injury entry and affected-muscle/day adjustments, no inferred diagnosis, preserved completed sets and explicit reapproval. |
| **Shorter session** 45/30/20-minute choice and safe cut priority | **Open/unverified** in native screen. | Evidence-based cut order without removing required key lifts or dropping below necessary minimums; editable preview and undo. |
| Focus mode actions **Info, Warm-up, Superset, History, Swap, Notes, Plates** (v3.29.1 B563; B884) | **Open for full parity.** Native candidate Workout has partial controls. | Every action must work without leaving Focus; compact phone, tablet/foldable docking and 48dp accessible targets. |
| Warm-up ramp, collapse after completion, warm-up timing (v3.5 B481; v3.2.6 B470; v3.3 B475) | **Partial.** Native warm-up/set controls exist. | Verify load-aware ramp, per-set logging, collapsing completed warm-ups, timer integration and program/session budget. |
| Supersets, advanced sets (myo, drop, partials), accurate volume/rest (v3.3.3 B478; B884) | **Partial.** Native authoring and journal have amplifier and superset structures. | Display attachment to the owning set, technique-specific timers and measured volume with no invented equivalences. |
| Plate calculator and exact bar/Smith loading with equipment stock (B941; B983) | **Partial.** Native verified stock/plate-related source exists. | Workout tap-to-open visual plate breakdown, distinct bar weights, valid attainable increment, and equipment-specific application. |
| Session timer, rest controls, complete/skip/finish confirmations and crash recovery (B985, B979, B884) | **Partial.** Native source/receipts and timer components exist. | Full end-to-end test with pause, resume, screen-off, rest change, skip, finish and interruption; no double-applied sets. |
| Custom split/goal/cycle generator, adaptive blocks and exercise rotation (B987 + multiple cycle changelog entries) | **Partial.** Kotlin blueprint, program audit and adaptive review exist. | Real source-backed alternatives that improve prescriptions, shift frequency appropriately, preserve edits and require explicit audit/acceptance; no fake “better engine” claims. |

## Priority 1 — high-value coaching, Progress and personalization

| Web capability / changelog proof | Current Kotlin result | Missing acceptance requirement |
| --- | --- | --- |
| Weekly muscle volume **MEV/MAV/MRV**, split coverage, deltoid and back balance (v3.1.10 B464; v3.2.1 B465; multiple weekly-coverage changelog fixes) | **Partial.** Native catalog credits and program dose audits exist. | Compare all goal/split/day/equipment scenarios, including biceps/brachialis and wrist flexors/extensors separately; reject under- and overdosed plans. |
| Pattern diversity, “why chosen”, program trace and repair steps (B581; changelog line 2144) | **Partial.** Native engine has audited composer/selection. | Per-phase and per-day visible reasons for choosing, replacing, trimming and rebalancing exercise slots. |
| Plateau explanations by program/day; contextual actions and undo (v3.1.3 B450; B905) | **Partial.** Native longitudinal coach reviews exist. | Distinguish fatigue, exercise boredom, lack of volume, normal progression and actually stalled performance; don't mix Upper and Push evidence. |
| Plan views **Schedule / Volume / Progression / Blueprint**, per-week prescription review (v3.23 B548; v3.22 B547) | **Partial.** Native Plan and weekly/block review exists. | Equal-or-better navigation across phase/week/day, explicit progression trajectory and true volume zones with no conflicting cards. |
| Progress historical sets, exercise search and ordered favorites (v3.32 B567; native newer pass) | **Implemented/source-tested.** Native Progress has exact exercise history, source-backed trends and ordered owner-scoped favorites. | Tablet/foldable/TalkBack acceptance; correct units and edits across all charts. |
| Strength Standards prominent in Progress (v3.29; B519; native newer pass) | **Implemented/source-tested.** Native four-lift overview and published-reference detail are separate from factual non-barbell load trends. | More trustworthy age/bodyweight source references where feasible; no unsupported percentile claim. |
| Dumbbell/bodyweight movements contributing to community Strength Score (release notes, “Dumbbells and bodyweight lifts now count toward your Strength Score”) | **Open for score parity.** Native non-barbell movement *history and factual charts* exist, but do not silently earn unsupported barbell-reference tiers. | Port or improve the actual source-attributed scoring method and its equipment/bodyweight normalization; test coverage, transparency and no invented strength percentiles. |
| Month-grouped History, workout search, corrections, session notes (v3.1.7 B459; build 743; B935) | **Partial.** Native correction-aware history works. | Search/filter/paging by period, monthly grouping and safe edit provenance. |
| Profile level/XP, achievement cards, strength club, customizable sections, share cards (M297; v3.0 B441) | **Partial/open.** Native Profile and strength history shortcuts exist. | Section order/visibility, coherent level visuals, milestones, thousand-pound club and privacy-safe shareable cards. |
| Shared programs gallery, template import, favorites and older program upgrades (v3.3 B478; B983) | **Open/unverified.** Native custom program builder exists. | Discover/import/verify real templates, avoid duplicates, equipment adaptation and revised engine upgrades with review. |
| Exercise Library: equipment/muscle filters, ban/unban, search, attachments, movement visuals (B939, v3.? B?, B578) | **Partial/open.** Curated native catalog and gym editor exist. | First-class discoverable Library, verified filters, pictures/variants, ban undo, and shared search semantics with swap. |
| “What's New”, release notes and complete settings controls (B917; latest changelog) | **Open/unverified.** Native Settings and theme options exist. | Current release highlights with archive; offline privacy/data controls and native backup/restore if retained. |

## Priority 2 — native UX quality gates, not pixel-for-pixel web cloning

- Material 3 tokens, shared components, persistent non-neon default theme, light/dark palettes and expressive motion; do not migrate web CSS machinery.
- One authoritative action and state owner per user task: no duplicate coach banners, phantom program saves, competing history calculations, or overwritten user edits.
- Narrow Android, foldable, tablet, portrait/landscape and 200%-font layouts; 48dp targets, keyboard and TalkBack semantics, correct Android Back, reduced-motion support.
- Release only after screen-by-screen behavior snapshots, end-to-end onboarding → program → workout → history → recovery, and CI evidence. A green compile does not mean visual acceptance.

## Exercise swap acceptance test contract

1. **Find:** search by readable name, pattern/family and trained muscle; show source-reviewed same-pattern matches and other alternatives separately.
2. **Rank:** explain actual catalog pattern and muscle overlap. Program-dose gap and session-role scores must be added before calling the list “coverage optimized.”
3. **Filter:** the current athlete's *active gym* and physical stock, required facilities, attachments, avoidance and relevant injury restrictions decide availability. Unknowns stay unavailable.
4. **Compare:** before/after includes new role, movement, muscle-credit and pattern changes, set count, physical load basis, notes, superset/technique consequences and affected program/week scope.
5. **Choose:** preserve old completed-set journal evidence; never transfer old numerical weights, reps, RIR, notes, techniques or progression history as if earned by the new movement.
6. **Apply:** revision- and gym-source-fenced edit with explicit confirm, optional program update, re-audit, durable undo and crash recovery. Disallow unsafe protected weekly overrides and linked supersets until lossless migration is implemented.
7. **Verify:** tests for filtered/missing gym items, equipment implications, duplicate variants, unreviewed credits, changed source revision, load mismatch, day-specific progress, pinned manual work, cancel/back, physical screen sizes and resumed workouts.

## Native work completed during this audit

- Added `CustomExerciseSwapRankingDomain` (source-reviewed pattern, family and muscle-overlap ordering, transparent explanations; no fabricated numeric transfer).
- Exposed provisional reviewed catalog metadata in `CustomExercisePickerOption`.
- Added program-draft-only `replaceSelectedExercise`, a new-slot-ID builder transaction and controller wiring with exact gym/load/set verification, override/superset refusal and undo.
- Added an explicitly confirmed searchable **Swap** picker in the native Program editor, supporting same-pattern and shared-muscle filtering. New target loads and notes start empty for each alternative.
- Added JVM/domain tests and source-level guards. **Important:** this is *not* complete in-workout swap or program-dose-aware ranking parity.

**Release rule:** A web change log feature stays open until its user-observable behavior (not just its title or icon) has an equivalent native path or an explicit documented reason to omit it. The changelog is a discovery index, while current web code + native revision tests are the acceptance source. An independent engine superiority test remains necessary.
