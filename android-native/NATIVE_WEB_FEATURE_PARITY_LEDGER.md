# Pursuit Iron — web-to-native feature parity ledger

**Source:** current web `CHANGELOG.md` through Build 998, `modules/release-notes.js`, canonical web code, and Kotlin rebuild source. Maintained as a behavioral acceptance checklist, **not** a list of cosmetic components to copy. The new Kotlin implementation may improve the web UX, but cannot silently drop a beneficial web feature. An existing button or matching label is *not* evidence that the feature works.

**Status vocabulary:** **Implemented/source-tested** = exact behavior is in native source, with explicit tests/CI required before release; **Partial** = some native behavior exists but meaningful web capability is missing; **Open** = not verified in current Kotlin rebuild and must not be advertised as available; **Adapt** = web-specific mechanism not appropriate for native Android, preserve the user value by a native equivalent. No status is a release sign-off without real phone/foldable/TalkBack acceptance.

## Priority 0 — functionality that must survive the rewrite

| Web capability / changelog proof | Current Kotlin result | Missing acceptance requirement |
| --- | --- | --- |
| Search exercise replacements in **workout and program** (v3.14.9, B519) | **Partial/source-tested.** Program drafts support searchable ranked replacements; issued custom and Composer workouts have separate owner-gated workout-only swap paths. Composer choices now include pattern and muscle-credit search, same-pattern/shared-muscle filters, original ranking, freshly authored set targets, exact gym-stock checks and versioned swap/undo receipts. Recorded work cannot be silently reassigned. | Verify on real devices that timer, untouched sets and workout identity survive live swap, minimize and recovery. Both workout routes still need physical-device, recovery and mixed-history acceptance; no old prescription may transfer. |
| Alternatives ranked by **movement pattern, family, program muscle gap** (v3.2.3, B467) | **Partial/source-tested.** `CustomExerciseSwapRankingDomain` ranks program/issued-custom choices by pattern/family, reviewed role, credited muscles and known sole direct-day muscle coverage. Composer swap ranks same-role/focus verified choices by pattern and shared muscle credits, with search/filter that preserves the source-authored rank. Neither route carries old loads. | Full *weekly program coverage gap*, session fatigue, per-day training intent and source-based exercise frequency optimization remain open. Source-verified sole direct-muscle coverage on the current day is now a conservative within-fit tie-breaker; it does not establish equivalent sets, training effect or appropriate volume. |
| Swap choices from **active gym**, including implied equipment (v3.41.0, B581; v3.38.0, B578) | **Partial.** Program authoring and issued-custom swaps recheck the current reviewed native gym, stock, attachments and facilities; unverified alternatives cannot be authorized. | Audit equipment implication rules, multiple gyms, attachment variants and source revision changes on physical devices and across different workout routes. |
| Swap **undo, confirm and scope** (v3.8.0, B488; B905; v3.16.1, B523) | **Partial.** Program replacement has new-slot identity, explicit confirmation and revisioned undo/re-audit. Issued-custom swaps now also expose a separately confirmed, versioned and owner-fenced **Undo swap** for unstarted replacements; the entire receipt chain is retained. | Verify live undo on physical devices and against interrupted writes; add independent optional “Update my program” scope. Composer also has a distinct source-verified and journal-versioned workout-only swap/undo route; device and interrupted-write proof remain open. Preserve unrelated days, cycle phases, signed history and overridden weeks. |
| Minimize/resume after an in-session swap or program update (v3.92.0, build 640) | **Partial/source-tested.** Custom live swaps now replay from immutable original approval and use the existing journal-recovery owner; the combined device journey is not certified. | Certify crash/relaunch/recovery on device: completed untouched sets return unchanged, swapped-out lift starts fresh, no duplicate exercise IDs, lost rest timer or phantom completion. |
| “Why this exercise?” explain ranked candidates and source (v3.40.0 B580) | **Partial.** Native custom and Composer shortlists expose their reviewed pattern/role/muscle overlap, without inventing equivalent loads or source-specific coaching scores. | Show actual program-coverage constraints, per-candidate session-intent evidence, runner-up and any later repair decisions; never fabricate a quantitative recommendation for manual swaps. |
| Program editing stays as **recoverable draft** until explicit Save/Discard (v3.8.0 B488) | **Partial.** Native custom-cycle edits save an undoable *draft revision*; approval is separate. | Validate unsaved-versus-saved contract, close/back confirmation, crash recovery, and independently approved future issuance. |
| Completed **Target vs Last** and full exercise history in workout (Build 824; subsequent updates) | **Partial/implemented.** Native target/last references and correction-aware history access exist. | Verify all modes, every set, dynamic progression, 200% text, and no duplicated or mismatched previous sets. |
| Equipment-rounded progression and effective RIR, including no supplied RIR (B941; recent progression fixes) | **Partial.** Kotlin load/rep/coaching reviews and exact stock checks exist. | Matched edge-case outcome comparison: separate exercise/day progress, chronic and intra-session fatigue, honest missing-RIR handling, realistic load increments and no regression. |
| Injury/restriction adjustments **during a workout** (B941, B905) | **Partial.** Restrictions and coaching protections exist in native logic. | Quick injury entry and affected-muscle/day adjustments, no inferred diagnosis, preserved completed sets and explicit reapproval. |
| **Shorter session** 45/30/20-minute choice and safe cut priority | **Open/unverified** in native screen. | Evidence-based cut order without removing required key lifts or dropping below necessary minimums; editable preview and undo. |
| Focus mode actions **Info, Warm-up, Superset, History, Swap, Notes, Plates** (v3.29.1 B563; B884) | **Partial/source-tested.** Both issued-workout routes offer one-tap read-only Info sourced from their saved prescriptions and open full verified History without leaving the workout; both issued-workout loggers now reuse accessible rep/RIR steppers, with explicit bodyweight handling and inline validation on the custom route. Swap/undo remain separately reviewed. | Real-device Focus/All set-state retention and sheet dismissal must be accepted; muscle/technique library guidance, warmup management, superset editing, notes, and Composer-route plate handling still require their own backed interactions. Every action must work without leaving Focus on compact phones and tablets. |
| Warm-up ramp, collapse after completion, warm-up timing (v3.5 B481; v3.2.6 B470; v3.3 B475) | **Partial.** Native warm-up/set controls exist. | Verify load-aware ramp, per-set logging, collapsing completed warm-ups, timer integration and program/session budget. |
| Supersets, advanced sets (myo, drop, partials), accurate volume/rest (v3.3.3 B478; B884) | **Partial.** Native authoring and journal have amplifier and superset structures. | Display attachment to the owning set, technique-specific timers and measured volume with no invented equivalences. |
| Plate calculator and exact bar/Smith loading with equipment stock (B941; B983) | **Partial/source-tested.** Both issued workout loggers now offer a read-only plate layout for the athlete-entered barbell total. Custom workouts re-read the exact owner/gym/catalog revision; generated workouts reopen the owner journal and source-matched approved program. Both require reviewed real barbell inventory, exact attainability and bounded symmetric plate-pair arithmetic, without a default bar or silent rounding. Pure engine tests cover fractional plates, exhausted stock, invalid units and Smith exclusion. | CI and on-device acceptance pending; distinct Smith-bar handling, richer physical plate visuals, settings for bar weights and wider equipment coverage remain open. A stale or unsupported source must produce no plate instructions. |
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
- Added JVM/domain tests and source-level guards, including search that excludes the source row from visible results without losing its reviewed pattern/muscle ranking context. **GitHub Actions run 42578839538 passed all 33 Kotlin/Compose checks** on the final code revision. **Important:** this is *not* complete in-workout swap or program-dose-aware ranking parity; real-device acceptance remains open.

**Release rule:** A web change log feature stays open until its user-observable behavior (not just its title or icon) has an equivalent native path or an explicit documented reason to omit it. The changelog is a discovery index, while current web code + native revision tests are the acceptance source. An independent engine superiority test remains necessary.

## Issued-workout swap follow-up — 2026-11-09

The native custom-workout route now has a **source-owned, durable swap** instead of a mock Swap button. The swap is available only before recording work in the affected slot and never alters the approved future program. Its source and equipment are revalidated at the journal writer, not trusted from the filter UI. Cold replay uses the immutable original approved workout; legacy unswapped v1 digests remain byte-identical. A swap writes fresh user-authored sets, not old targets, and corrected History ownership is unchanged.

The shared picker now explains and filters by **Same role** and **Same family**, using catalog-reviewed `ComposerRole` and stable family identifiers inside the existing pattern/family ordering. When all of the other day's exercises remain present in the reviewed source, it displays which sole direct-muscle groups a candidate **could cover if new working sets are authored** and which it cannot directly cover; an unknown roster suppresses this claim. Search now also matches **verified attachment IDs** (including human-friendly spacing such as “dual rope” and “V bar”) and labels an available option's attachment directly, rather than hiding the variant behind a generic exercise name. Pattern and family IDs can also be found using their readable spaces instead of requiring underscore-form codes. The live sheet has separate **Confirm workout swap** language; program-draft swaps retain **Confirm this program swap**, their own revisioned edit/undo and independent program review. This is a UX and ranking improvement, not an automated fatigue-adjusted recommendation or a program rewrite.

**Still open:** live swap undo; optional approved future-program application; corresponding Composer-workout route; cross-gym and attachment behavior; actual on-device timer, focus, accessibility, large-font, interruption and restart acceptance; independently measured coaching superiority over the web engine. Kotlin source tests and CI must pass before considering this change source-tested, and even a green build is not device approval. No APK/AAB during the whole-app design phase.

### Follow-up: current-day muscle coverage guard — 2026-11-09

Ranking now receives the **exact other exercise IDs on the selected authored program day or current issued custom workout**. Only main `WORKING`-set exercise slots count as coverage, and the removed slot itself must have at least one working set. From validated, positive, author-reviewed `DIRECT` muscle credits, the ranking computes which muscle groups receive no other direct working-set exercise on that day. A replacement gets a within-pattern/family tie-breaker for potentially retaining those muscles, and the sheet names potentially covered and absent groups. It explicitly avoids claiming that coverage is retained before the athlete authors and logs qualifying working sets. No load, set prescription, source approval, session result, or program is altered by this read-only ranking. Unknown catalog movements or source metadata intentionally disable this context ranking rather than creating an invented muscle-coverage score.

This is not per-muscle MEV/MAV/MRV auditing, quantified training equivalence, program-wide gap closure, or a guarantee that a movement is an optimal substitute. Tests cover contextual ordering, no-context fallback, unavailable-elsewhere fallback and day-specific wiring. Physical usability and source CI acceptance remain independent gates.

## Custom live-swap undo continuation — 2026-11-09

The custom workout screen now provides a separate **Undo swap** action for an untouched replacement. After explicit confirmation, the native owner-scoped store commits a `CustomWorkoutJournalOperation.UndoSwap` with a revision check, never deleting the original swap or other recorded work. The pure journal reducer restores the exact historical prior slot (including a previous swap in a chain), not a current gym-derived guess. Undo is refused once any target visit in the replacement has a recorded receipt, after Finish, when the swap was already undone, when the slot has been swapped again, or on stale/cross-owner writes. Failed writes reopen the actual journal rather than pretending success.

The codec adds a receipt type to the existing v2 journal; journals containing swaps retain v2 even after undo, while untouched legacy v1 encoding remains byte-identical. Regression suites now cover cold replay, lost-ack idempotence, chain unwinding, immutable original targets, started-replacement refusal, exact recorded other-set preservation, foreign-owner denial and completing History after undo. These are source/integration assertions, **not** evidence of emulator/phone user acceptance until the latest CI and device interaction runs pass.

Still open: independent source-approved update of future program, Composer workout swaps, richer context-aware swap scoring, actual device undo/turn-screen-off recovery, and coach superiority comparisons. No APK/AAB is produced by this change.


## Published web Build 1009 baseline — 2026-11-09

The production WEB reference was updated by merged PR #456 at commit
`47ab1f2e0ad0dfa533906e25bf479145c7059463` (Build 1009).
This is a **benchmark**, not an implementation of the same changes in the
independent Kotlin engine; the other programmer's native project is unrelated.

The Kotlin superiority scorecard must explicitly exercise the following:
1. **Repeating finite programs:** after four-week completion, the next pass
   starts with week 1 and clear current-pass Plan completion markers, while
   retaining prior sessions as history. Check interruption, undo and repeated
   passes without counting old sessions twice.
2. **Physically possible loading:** 2.5 kg / 5 lb per-hand dumbbell increments
   where supported; athlete-configured racks and overrides have priority.
   A successfully logged off-grid load must not be silently lowered by default
   guessed increments. Changed rep/RIR prescriptions should choose a defensible
   attainable load without repeated downward rounding or overriding real fatigue.
3. **Practical gym station diversity:** avoid excessive cable/Smith-station
   selection when equivalent equipment is available, without trading away
   required muscle dose, split validity, physical inventory or time-feasibility.
   Test the compact-session fallback instead of failing an otherwise workable day.

These are **open comparison gates**, even if existing Kotlin unit tests happen
to pass; direct paired Build-1009-vs-native longitudinal scenarios and device
inspection have not been completed. The Kotlin coach is not yet established
as more accurate than this refreshed baseline.

### Native continuation in this pass

- The two independently authorized Kotlin workout plate viewers now share
  a schematic symmetric Material 3 loading diagram showing exact confirmed
  per-side plate counts, with a bounded preview and accessible complete labels.
  It is **display-only**; it never prescribes, logs or changes a weight, and
  verified Smith totals are still deliberately excluded from barbell layouts.
- The generated-workout exercise swap ranker now counts **distinct, positive
  DIRECT reviewed muscle credits** for a shared-muscle claim. Duplicate and
  zero-value credits cannot artificially raise the score; other eligibility,
  equipment gates and fresh target authoring remain authoritative.
- Actual large-font phone/foldable presentation and physical equipment-use
  acceptance still require device verification. No APK or web changes are part
  of this isolated Kotlin work.


## Native pinned exercise notes and reviewed setup — 2026-11-09

**Source verified** on Kotlin branch commit
`dc169209e0afc80d83ee06cc357eb7e7216e2b64`
([33/33 CI checks](https://github.com/extracteddeath/Pursuit-Iron/actions/runs/42736863410)).
Both custom and generated issued workout screens now expose a real pinned
exercise note/setup editor. Notes persist by exact signed-in athlete and
exercise across workouts; current approved workout authorization is
rechecked before read/write. The store uses atomic owner-fenced CAS
with revision/event identity, corruption detection, durable failed-ack
recovery, and a non-destructive saveable draft.

Catalog-reviewed equipment facts suggest relevant user-entered setup
fields (attachment, pulley, seat, safety pins, angle, grip etc.).
Unknown older valid values remain visible as **Saved earlier**; no
machine position, load or attachment is inferred. This is **not** a
training recommendation or a rep/load write.

This closes the source-level **pinned exercise note and setup** path,
not general session notes, exercise history annotation, multi-gym
migration, physical Android device acceptance, or the complete native
web-feature parity gate.
