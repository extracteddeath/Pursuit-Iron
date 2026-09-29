# M166 focused source inspection

bytes: 3410955

## custom set regression

offset: 938034

```js
    if (!(p >= (isFocus ? 0.5 : 0.67)))
        return null;
    if (ex.part === "calves")
        return "Last set: static stretch — hold a loaded stretch ~30s after your final rep";
    if (STRETCH_PARTIAL_PARTS.has(ex.part))
        return "Last set: lengthened partials — extra reps in the stretched position past failure";
    if (MYO_PARTS.has(ex.part))
        return "Last set: myo-reps — to failure, then mini-sets of a few reps with brief rests";
    return null;
}
/* compute a cell {sets,reps,note,range,rir} for a given week.
   Auto mode prescribes each lift's own rep RANGE; the week selector drives
   load & RIR (and a small overreach set in the last hypertrophy week). */
function computeCell(program, day, id, slotIndex, weekIndex) {
    const ex = EX_BY_ID[id];
    if (!ex)
        return { sets: 0, reps: "—", note: "Exercise unavailable", range: null, rir: null, tech: null, missing: true };
    // Freestyle is deliberately not a generated program. It gets neutral logging defaults from the
    // Next runtime layer, never from the removed production/v661 programming rules.
    if (program?.artifactType === "freestyle" || program?.quick)
        return freestyleCellForRepRange(ex.rep);
    /* A CUSTOM program (Build your own, custom: true) is the lifter's own: their per-slot settings (sets, reps, reps-in-reserve,
       rest, technique) win, and anything they did not set gets the same neutral defaults a Quick workout uses — never the removed
       v661 rules. Without this it fell through to "This older plan is archived — rebuild it" with 0 sets. */
    if (program?.custom === true && program?.engineSource !== "pursuit-next") {
        const base = freestyleCellForRepRange(ex.rep), o = program.overrides?.[`${day?.id}:${slotIndex}`] || {};
        /* Custom-plan overrides can come from backups / old editors as arrays ([10,15], [2,2]).
           Keep the shell shape canonical just like Pursuit Next cells do. Otherwise String([10,15])
           becomes "10,15"; the old progression parser then saw only the first number and treated
           the *bottom* of a 10-15 range as the top, which could award a load increase at 10 reps. */
        const repPair = cellRepRange({ range: o.reps ?? base.reps }, program, ex, slotIndex === day?.primaryIndex);
        const reps = repPair[0] === repPair[1] ? String(repPair[0]) : `${repPair[0]}-${repPair[1]}`;
        const effort = effortBounds(o.rir ?? base.rir);
        const rir = effort ? (effort[0] === effort[1] ? String(effort[0]) : `${effort[0]}-${effort[1]}`) : (o.rir ?? base.rir);
        return { sets: canonicalShellSetCount(o.sets, base.sets) ?? base.sets, reps, range: reps, rir, rest: o.rest ?? base.rest, tech: o.techOverride ?? base.tech ?? null,
            role: base.role, progressionStyle: o.progressionStyle ?? "auto", note: "Your program", custom: true };
    }
    const nextCell = getNextShellCell(program, day, slotIndex, weekIndex);
    if (nextCell)
        return nextCell;
    /* M46: prescription fallback to the v661 set/rep/RIR engine is removed. A Pursuit Next artifact
       either has an engine-owned week cell or is considered incomplete/corrupt and fails visibly.
       Pre-Next saved programs are preserved for migration and must be rebuilt before execution. */
    return {
        sets: 0, reps: "—", note: program?.engineSource === "pursuit-next"
            ? "Prescription data is missing — rebuild this program"
            : "This older plan is archived — rebuild it to continue",
        range: null, rir: null, tech: null, missing: true, engineMissing: true
    };
}
/* Per-set effort for the LAST working set. Pursuit Next owns the prescription. The shell must not
   invent a second intensity model (the old overlay forced most last sets to failure from week 2 and
   made the Plan preview disagree with the runtime rows). Advanced techniques such as myo-reps/drop
   sets are separately engine-authored in `cell.tech`. */
function lastSetEffort(cell, ex, isPrimary, weekIndex, weeks) {
    void ex;
    void isPrimary;
    void weekIndex;
    void weeks;
    const bounds = effortBounds(cell?.rir);
    const failure = !!bounds && bounds[0] === 0 && bounds[1] === 0;
    return { rir: cell?.rir ?? null, failure };
}
/* Sources behind the programming defaults — surfaced so "evidence-based" isn't just a claim. */
const REFERENCES = [
    ["Renaissance Periodization — Dr. Mike Israetel", "Weekly volume landmarks (MEV / MAV / MRV) and RIR-based set progression — the model behind this app's per-muscle volume bars and the accumulation ramp."],
    ["Stronger By Science — Greg Nuckols", "Volume- and frequency-response meta-analyses and program-design work; the ≥2×/week frequency default and flexible rep ranges come from here."],
    ["Schoenfeld et al. — meta-analyses", "Dose–response of weekly sets (2017) and training frequency (2016), and the finding that hypertrophy is similar across ~6–30 reps when sets are taken near failure."],
    ["Pelland et al. 2026 — volume & frequency dose–response (Sports Med)", "The largest analysis of its kind, and the reason this app counts an indirect set as half a set: that method predicted growth better than counting them fully or ignoring them. It also found size and strength still improving as weekly sets rise, with diminishing returns rather than a point where more becomes harmful — so the volume ceiling here is a recovery budget, not a line past which sets stop counting. Frequency mattered for strength; for size, what mattered was the total."],
    ["Refalo et al. — proximity to failure", "Sets taken to failure and sets stopped 1–2 reps short produce comparable growth, while going to failure feels meaningfully worse and costs more recovery. Strength does not require it. Hence the ≥1 RIR floor on heavy compounds, and why the harder sets here are placed on isolations where the cost of a missed rep is lowest."],
    ["Eric Helms — The Muscle & Strength Pyramids", "RIR/RPE autoregulation and the adherence-first hierarchy the defaults follow."],
    ["Block periodization — Issurin; Helms / SBS", "Accumulate → intensify → realize for strength, and the alternating hypertrophy↔strength model behind the block-plan overlay."],
    ["Method lineage", "Several templates implement training structures that were popularised by well-known coaches and communities — tiered T1/T2/T3 loading, training-max percentage waves, ramping and straight 5×5, and weekly volume/intensity undulation. The implementations here are written from published descriptions of those structures; the templates are named for what they do, and are not affiliated with or endorsed by anyone."],
    ["Fuel & recovery", "Every progression here assumes adequate calories, protein (~1.6–2.2 g/kg) and sleep. If progress stalls with 
```

## recent sessions hit 1

offset: 1062067

```js
to a real
   pose; anything else falling through to "generic" is a coverage gap to fix, not an accepted state. */
const FIGURE_GENERIC_OK = new Set(["neck-extension", "neck-curl", "neck-harness", "neck-lateral"]);
/* Dev-only coverage guard: warns the moment an exercise is added without a real animation pose (i.e.
   it silently lands on the generic fallback and isn't on the tiny whitelist above). Mirrors
   assertExerciseData() — gated on `typeof __BUILD__ === "undefined"` so esbuild strips it from
   production builds and it never runs for end users. Exported-by-closure for the Node test harness
   via globalThis so the release suite can fail the build, not just log in dev. */
function assertFigureCoverage() {
    const gaps = [];
    EXERCISES.forEach(ex => {
        const p = figurePose(ex);
        if (p === "generic" && !FIGURE_GENERIC_OK.has(ex.id))
            gaps.push(ex.id);
    });
    if (gaps.length) {
        try {
            console.warn("[figure] no animation pose for: " + gaps.join(", "));
        }
        catch { }
    }
    return gaps;
}
if (typeof __BUILD__ === "undefined" && typeof globalThis !== "undefined") {
    try {
        globalThis.__figurePose = figurePose;
        globalThis.__assertFigureCoverage = assertFigureCoverage;
    }
    catch { }
}
if (typeof __BUILD__ === "undefined") {
    try {
        assertFigureCoverage();
    }
    catch (e) { /* never let a check break boot */ }
}
/* Heavy spinal/axial loaders: barbell squats & deadlift-family hinges, bent-over
   barbell rows, and standing barbell overhead presses. Bench presses, hip thrusts,
   chest-supported/seal rows, machine & seated work do NOT load the spine the same
   way. Used to stop too many of these from stacking in one session (recoverability). */
function isAxialLoad(ex, eng = 1) {
    if (ex.type !== "compound" || !isBarLike(ex.equip))
        return false;
    const pat = movePattern(ex);
    if (pat === "hinge" || pat === "squat")
        return true;
    /* ⚠ A SUPPORTED-ROW EXEMPTION WAS ADDED HERE AND REVERTED AS DEAD CODE. The list looked short
       (only seal-row and meadows-row named) but the `isBarLike` guard above already excludes every
       machine and chest-supported row — Chest-Supported Row measures axialCost 0 on every engine.
       I attributed a gates/axialspacing breach to it using a local replica of this function that
       omitted the isBarLike line, which made supported rows look axial when they never were. If this
       rule is ever suspected again, call isAxialLoad itself rather than re-implementing it. */
    if (pat === "row" && !["seal-row", "meadows-row"].includes(ex.id))
        return true; // bent-over barbell rows
    if (pat === "overhead" && !["seated-ohp", "landmine-press", "upright-row"].includes(ex.id))
        return true; // standing OHP / push press
    return false;
}
// Per-muscle recovery / freshness (Fitbod-style) from recent logged sessions.
// readiness 0–100%: 100 = fully recovered. Heavier recent sessions take longer to recover.
const BASE_SET_COST = 0.5; // recovery cost of one working set performed at the prescribed reserve
/* Memoized on the history array's identity. The session screen asks for a suggestion per exercise
 * (banner + prefill), autoStyleFor consults recovery inside each one, and suggestWeight's auto
 * annotation consults it again — a dozen calls per render, every one sorting and scanning the entire
 * training history to produce the identical answer. Measured at ~8ms per pass on a 200-session
 * history, i.e. half a frame budget on a fast machine before the UI draws anything. History is only
 * ever replaced immutably (every setHistory builds a new array), so reference identity is a sound
 * cache key: same array, same answer. The WeakMap lets an old history be collected the moment nothing
 * else holds it. Time-sensitivity note: readiness decays with wall-clock time, so entries expire
 * after 60s rather than living as long as the array does — recovery percentages drift by the minute,
 * not the millisecond, and a stale-by-a-minute number is indistinguishable in the UI.
 */
const __recoveryMemo = new WeakMap();
function muscleRecovery(history) {
    if (history && typeof history === "object") {
        const hit = __recoveryMemo.get(history);
        if (hit && Date.now() - hit.at < 60000)
            return hit.v;
    }
    const v = muscleRecoveryUncached(history);
    if (history && typeof history === "object")
        __recoveryMemo.set(history, { v, at: Date.now() });
    return v;
}
// --- learned recovery ------------------------------------------------------------------------
// The clock model (30 + sets·2.3h) is a population average; actual recovery is individual and muscle-
// specific. This MEASURES it from the lifter's own history: performance-on-repeat. Each time a muscle
// was retrained, pair the rest gap (hours since it was last trained) with the performance ratio (this
// session's best e1RM ÷ the previous session's). Retrain before you've recovered and performance dips;
// once recovered it holds — so a least-squares line ratio = a + b·gap identifies the rest at which
// performance returns to the prior level, i.e. the recovery time. Shrunk toward the clock model by
// confidence, clamped to a sane [18,120]h band, memoized on (history,part).
//
// Deliberately one-directional and conservative: it will RAISE recovery time for a lifter who
// demonstrably dips on short rest (b>0, intercept<1), which is the case that matters — it protects a
// slow-recovering muscle from being retrained (or trimmed) too eagerly. It stays silent (returns null →
// clock model) when rest doesn't measurably help (b≈0), because "always fine at every gap I've tried"
// cannot honestly prove a FASTER-than-clock recovery — only the absence of a dip, which isn't the same
// thing. Identifiability floor: ≥8 repeat observations and ≥24h of gap spread, or it returns null.
const __recovFitMemo = new WeakMap();
function personalRecoveryHours(history, part, clockHours) {
    if (!history || !history.length)
        return null;
    let byPart = __recovFitMemo.get(history);
    if (!byPart) {
        byPart = new Map();
        __recovFitMemo.set(history, byPart);
    }
    if (byPart.has(part))
        return byPart.get(part);
    const sess = []; // per-muscle session best e1RM, oldest→newest
    const sorted = [...(history || [])].filter(h => h && h.date).sort((a, b) => a.date - b.date);
    for (const h of sorted) {
        let best = 0;
        for (const [id, p] of Object.entries(h.perf || {})) {
            const ex = EX_BY_ID[id];
            if (!ex || ex.part !== part)
                continue;
            const ss = setsOf(p);
            for (const s of ss) {
                const w = +(s.w != null ? s.w : s.weight), r = +(s.r != null ? s.r : s.reps);
                if (w > 0 && r > 0 && !s.warm && !s.sub) {
                    const e = e1rmRIR(w, r, 0);
                    if (e > best)
                        best = e;
                }
            }
        }
        if (best > 0)
            sess.push({ date: h.date, e: best });
    }
    const obs = []; // (gapHours, performance ratio vs previous session)
    for (let i = 1; i < sess.length; i++) {
        const gap = (sess[i].date - sess[i - 1].date) / 3600000;
        if (gap <= 0 || gap > 240)
            continue;
        obs.push({ gap, ratio: sess[i].e / sess[i - 1].e });
    }
    let out = null;
    const gaps = obs.map(o => o.gap);
    const spread = obs.length ? Math.max(...gaps) - Math.min(...gaps) : 0;
    if (obs.length >= 8 && spread >= 24) {
        const n = obs.length, mx = gaps.reduce((s, g) => s + g, 0) / n, my = obs.reduce((s, o) => s + o.ratio, 0) / n;
        let num = 0, den = 0;
        for (const o of obs) {
            num += (o.gap - mx) * (o.ratio - my);
            den += (o.gap - mx) ** 2;
        }
        const b = den > 0 ? num / den : 0, a = my - b * mx;
        if (b > 1e-5) { // rest 
```

## recent sessions hit 2

offset: 1283906

```js
 (the prescription). Logging an
// easier-than-planned set (high actual RIR) → more capacity → hold/heavier; a to-failure
// set (RIR 0) → less headroom → lighter.
/* ===================== PERSONAL REP-SLOPE ===================== *
 * Epley's /30 is a population average, and the load–rep curve it describes varies a lot between
 * lifters and lifts: a high-rep responder reps 80% of max for 15 while a fast-twitch lifter gets 8,
 * and one constant cannot serve both. Every prescription in the app divides by that constant, so it
 * is the single highest-leverage number to individualize — and it can be fitted PASSIVELY, from
 * training the user already logs, with no calibration protocol and no server. That is the criterion
 * for this app: never ask for new behavior when the existing data already contains the answer.
 *
 * The fit: two work sets of the same lift in the same session at different loads pin the curve,
 * because the lifter's 1RM is the same for both —
 *      w1·(1 + (r1+q1)/s) = w2·(1 + (r2+q2)/s)   ⇒   s = (w2(r2+q2) − w1(r1+q1)) / (w1 − w2)
 * Every ramp, back-off set, and wave session yields such pairs. Straight-set-only training yields
 * none, and then the fit honestly stays at 30 (see shrinkage below) — no data, no opinion.
 *
 * Robustness, in order of what would otherwise go wrong:
 *   - pairs need a ≥5% load gap, or measurement noise dominates the divided difference;
 *   - each pair's implied slope must land in [12, 60] or the pair is discarded as corrupt
 *     (a mis-tapped rep count implies an absurd curve — better no vote than a wild one);
 *   - the centre is a MEDIAN over all pairs, so within-session fatigue (which biases pairs where the
 *     lighter set came second) is absorbed as long as both orders occur, and one bad pair moves
 *     nothing;
 *   - SHRINKAGE toward 30 by observation count: the fitted value only earns full weight after ~30
 *     pairs, so a week of data nudges rather than lurches;
 *   - the final slope is clamped to [22, 42] — outside that range the data is more likely lying
 *     than the population is wrong by that much.
 *
 * Memoized like muscleRecovery: keyed on the history array's identity (history is only replaced
 * immutably), holding a per-exercise map. The display-side strength standards and PR e1RMs stay on
 * flat Epley DELIBERATELY: those compare the lifter to population tables, and a personalized slope
 * would move the goalposts of the comparison itself. The slope personalizes what the app PRESCRIBES,
 * not how it scores you against other humans.
 */
const __slopeMemo = new WeakMap();
function personalRepSlope(history, ex) {
    if (!history || !ex || !ex.id)
        return 30;
    let byEx = __slopeMemo.get(history);
    if (!byEx) {
        byEx = new Map();
        __slopeMemo.set(history, byEx);
    }
    if (byEx.has(ex.id))
        return byEx.get(ex.id);
    const pairs = [];
    let seen = 0;
    for (const h of history) {
        if (seen >= 40)
            break; // recent sessions carry the current curve
        const p = h && h.perf && h.perf[ex.id];
        if (!p || !p.sets || p.sets.length < 2)
            continue;
        seen++;
        const work = setsOf(p).filter(t => isWorkSet(t) && t.w > 0 && t.r > 0);
        for (let i = 0; i < work.length; i++)
            for (let j = i + 1; j < work.length; j++) {
                const a = work[i], b = work[j];
                const qa = a.rir != null ? a.rir : 2, qb = b.rir != null ? b.rir : 2;
                if (Math.abs(a.w - b.w) < 0.05 * Math.max(a.w, b.w))
                    continue; // need a real load gap
                const sl = (b.w * (b.r + qb) - a.w * (a.r + qa)) / (a.w - b.w);
                if (isFinite(sl) && sl >= 12 && sl <= 60)
                    pairs.push(sl);
            }
    }
    let out = 30;
    if (pairs.length >= 6) {
        pairs.sort((x, y) => x - y);
        const m = Math.floor(pairs.length / 2);
        const med = pairs.length % 2 ? pairs[m] : (pairs[m - 1] + pairs[m]) / 2;
        const conf = Math.min(1, (pairs.length - 6) / 24);
        out = Math.max(22, Math.min(42, 30 + (med - 30) * conf));
    }
    byEx.set(ex.id, out);
    return out;
}
/* ============================== PERSISTENCE ============================== */
const MEM = { data: null };
const KEY = "wpb:v1";
const LIVE_KEY = "wpb:live";
// Current store schema version. Bump this and add a STORE_MIGRATIONS entry for EVERY change to the
// persisted shape, so old local data — and imported backups — are carried forward on load instead of
// being silently misread (changed shape) or orphaned (key bump). Keeping the migration logic in one
// versioned place is what makes future schema edits safe; the alternative is the scatter of inline
// "older saves" special-cases at each point of use.
const STORE_VERSION = 12;
const BW_LOG_CAP = 2000; // weigh-ins kept; ~5.5 years of daily entries at ~45 chars each
/* Body measurements are lengths, but they were being stamped with the WEIGHT unit — an entry read
   `{v: 34, unit: "lb"}` for a 34-inch waist. The display then derived inches-or-centimetres from
   the current weight unit, so switching lb→kg relabelled every past measurement as centimetres
   without converting it: a 34" waist silently became "34 cm". Lengths get their own unit, tagged at
   write time and honoured on read, and switchUnit converts them like every other stored number.
   Legacy rows carry the old weight tag, so lb reads as in and kg as cm. */
const lengthUnitFor = (weightUnit) => (weightUnit === "kg" ? "cm" : "in");
const asLengthUnit = (u, weightUnit) => (u === "in" || u === "cm" ? u : u === "kg" ? "cm" : u === "lb" ? "in" : lengthUnitFor(weightUnit));
const toLength = (v, from, to) => (from === to ? v : to === "cm" ? v * 2.54 : v / 2.54);
function normalizeMeasureLog(arr, to, weightUnit) {
    return (arr || [])
        .filter(e => e && e.date != null && Number(e.v) > 0)
        .map(e => { const from = asLengthUnit(e.unit, weightUnit); return { ...e, v: Math.round(toLength(Number(e.v), from, to) * 10) / 10, unit: to }; })
        .sort((a, b) => a.date - b.date);
}
// Each entry upgrades the store FROM the previous version TO its own key number. Functions must be
// pure (no React/DOM) and a no-op when their change is already applied, so re-running can't corrupt.
const STORE_MIGRATIONS = {
    // 11 -> 12: selective-promotion safety state is release-scoped and starts fail-safe. M76 ships
    // with a zero-percent manifest, so upgrading cannot change a prescription.
    12: (d) => d.selectivePromotionRuntime ? d : { ...d, selectivePromotionRuntime: defaultSelectivePromotionRuntimeState(M76_SELECTIVE_PROMOTION_MANIFEST) },
    // 10 -> 11: controlled canary research is opt-in and local. Existing users must remain
    // unenrolled after upgrade; a missing field therefore migrates to an explicit disabled state.
    11: (d) => d.canaryResearch ? d : { ...d, canaryResearch: defaultCanaryResearchState() },
    // 9 -> 10: the typed `age` integer becomes a `birth` date. An age is only true for a year and the
    // app had no way to know it had gone stale, so a lifter's strength standards quietly drifted onto
    // the wrong age band and stayed there. The exact date can't be recovered from a number, so estimate
    // mid-year of the implied birth year (±6 months, inside ageFactor's 5–6 year bands) and mark it
    // `birthEst` so the UI can ask for the real one. `age` is left in place and kept in sync on save,
    // so an older build reading this store after a downgrade still finds the field it expects.
    10: (d) => {
        if (d.birth)
            return d;
        const b = birthFromAge(d.age, d.savedAt);
        return b ? { ...d, birth: b, birthEst: true } : d;
    },
    // 7 → 8: the single `equipDefault` list becomes a list of GYMS. Equipment belongs to a place, not
    // to a person, and a lifter with a garage rack and a gym membership had no way to say so.
    // The existing list is pr
```

## recent sessions hit 3

offset: 1908073

```js
* ================= THE LIFTER MODEL ==========================================================
   One derived view of what we know about a lifter, so the session engine, the deload advisor and the
   progress screens stop each deriving their own slice and disagreeing about the same person.
   Pure, memoized on the history, and the single input to every decision below.

   Feeds effort calibration, the readiness bands and the plateau classifier. */
// --- effort calibration ----------------------------------------------------------------------
// Lifters mis-state reps-in-reserve consistently, and the error is systematic rather than random:
// "2 in reserve" is very often really 0–1. Every e1RM we compute from their sets folds that stated
// reserve in as extra reps, so the error compounds into heavier and heavier suggested loads until
// they start missing reps — a slow drift with nothing to flag it.
//
// AMRAPs and sets prescribed to failure give us a reference we can trust, because we know what was
// being asked. Every ordinary set then implies a reserve of its own:
//     impliedRIR = 30 * (ref / weight - 1) - reps
// and the gap between claimed and implied, taken across a lifter, is their bias.
//
//   bias > 0   claims more in reserve than they had  ->  their loads run too heavy
//   bias < 0   sandbagging, had more left than said  ->  their loads run too light
//
// The correction is applied when interpreting past sets (see calibrateDayPerf), never to what we ask
// of the lifter — nobody should be told to train at "RIR 3.4". It stays dormant until there are
// enough paired observations to mean something, and ramps in with confidence rather than snapping on.
const EFFORT_MIN_PAIRS = 6; // observations needed before any correction is applied
const EFFORT_MAX_BIAS = 2.5; // never correct by more than this many reps of reserve
const EFFORT_REP_CAP = 15; // shared rep cap — MUST be identical on both sides of the comparison,
// or the cap itself shows up as a phantom bias
function effortCalibration(history) {
    // CRITICAL: reference and observations must come from the SAME TIME WINDOW.
    // The first version built the reference from RECENT maximal sets but drew observations from the
    // WHOLE history. A set performed 14 weeks ago — when the lifter was genuinely weaker — was then
    // judged against today's stronger reference, which made it look as though they'd had far more in
    // reserve than they claimed. That manufactured a NEGATIVE bias in exact proportion to how much the
    // lifter had gained (measured: slow gainer 0.0, average −0.87, fast gainer −1.0, all of whom
    // report honestly), and the engine "corrected" for it by pushing their loads UP until they started
    // missing reps. Strength drift is not a reporting bias. Windowing both sides to the same recent
    // sessions holds strength approximately constant, which is the only condition under which the
    // comparison means anything.
    const WINDOW = 8; // recent sessions per lift on both sides of the comparison
    const hs = history || [];
    const bySession = {}; // exId -> [{sets, date}] newest-first
    for (const h of hs) {
        for (const [id, p] of Object.entries(h.perf || {})) {
            // setsOf, not p.sets: a corrupt backup can put nulls (or a string) in here, and this function is
            // now reached from muscleRecovery — so a malformed entry would take out the whole session view.
            const ss = setsOf(p).filter(x => x && typeof x === "object");
            if (!ss.length)
                continue;
            (bySession[id] = bySession[id] || []).push(ss);
        }
    }
    const obs = [];
    for (const [id, sessions] of Object.entries(bySession)) {
        const win = sessions.slice(0, WINDOW); // newest-first → the most recent WINDOW sessions
        // pass 1: reference, from sets the lifter was ASKED to take to maximum, within the window
        const maximal = [];
        for (const sets of win) {
            for (const s of sets) {
                const w = parseFloat(s.w), r = parseInt(s.r);
                if (!(w > 0) || !(r > 0))
                    continue;
                // Only a set PRESCRIBED as maximal (AMRAP, or RIR 0 asked and reported) is a trustworthy 1RM
                // reference. A bare "0" tapped on a set nobody asked to be maximal is ambiguous, and feeding
                // it in as ground truth poisons the reference.
                if (!(s.amrap || (s.rir === 0 && s.tr != null && s.tr <= 0)))
                    continue;
                maximal.push(e1rmRIR(w, r, 0, EFFORT_REP_CAP));
            }
        }
        if (!maximal.length)
            continue;
        // A robust CENTRE, never a maximum: the max of a noisy sample is biased upward by construction —
        // it selects for the luckiest day — and that bias flows straight into every implied reserve.
        const sorted = maximal.slice().sort((a, b) => a - b);
        const m = Math.floor(sorted.length / 2);
        const ref = sorted.length % 2 ? sorted[m] : (sorted[m - 1] + sorted[m]) / 2;
        if (!(ref > 0))
            continue;
        // pass 2: ordinary stated-reserve sets from the SAME window, against that reference
        for (const sets of win) {
            for (const s of sets) {
                const w = parseFloat(s.w), r = parseInt(s.r);
                if (!(w > 0) || !(r > 0) || s.rir == null || s.amrap)
                    continue;
                if (s.rir === 0 && (s.tr == null || s.tr <= 0))
                    continue; // reference set / unknown intent
                if (w >= ref)
                    continue; // no reserve to infer at/above ref
                const implied = 30 * (ref / w - 1) - Math.min(r, EFFORT_REP_CAP);
                if (!isFinite(implied) || implied < -3 || implied > 8)
                    continue;
                obs.push(s.rir - implied);
            }
        }
    }
    if (obs.length < EFFORT_MIN_PAIRS)
        return { bias: 0, confidence: 0, n: obs.length };
    // median is the right centre here — one mis-tapped RIR shouldn't move the calibration
    const sorted = obs.slice().sort((a, b) => a - b);
    const mid = Math.floor(sorted.length / 2);
    const med = sorted.length % 2 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2;
    const confidence = clamp((obs.length - EFFORT_MIN_PAIRS) / 18, 0, 1);
    // ramp the correction in with confidence, so it never lurches on the 6th observation
    const bias = clamp(med, -EFFORT_MAX_BIAS, EFFORT_MAX_BIAS) * confidence;
    return { bias: Math.round(bias * 100) / 100, confidence: Math.round(confidence * 100) / 100, n: obs.length };
}
// Rewrite a dayPerf map's stored reserve through the calibration, so every downstream consumer
// (suggestWeight's e1RM sizing AND the per-set DDP path, which both read these sets) interprets
// past effort accurately. This is the ONE place engine 2 touches load selection — deliberately, so
// the change is auditable in a single function rather than smeared across the progression code.
/* dayPerfFor + the calibration as ONE named step. They were paired inline inside a React useMemo,
   which meant no Node-level gate could reach the pairing: a mutation that dropped the calibration
   entirely — reverting every load to raw self-reported effort, the exact bug the calibration exists
   to fix — passed the whole suite. If a composition carries meaning, it needs a name and a test. */
function calibratedDayPerf(day, perf, history, effort) {
    return calibrateDayPerf(dayPerfFor(day, perf, history), effort);
}
/* STRICTLY THE MOST RECENT SESSION OF THIS DAY — no best-of, no ranking. Same shape and the same
   key set as dayPerfFor (both walk day.exercises, both accept a record on `reps != null` so
   assisted/bodyweight lifts stay tracked, both fall back to global perf for a lift never trained on
   this day), so the two are interchangeable as an anchor map. Extracted from <WorkoutSession>'s
 
```

## recent sessions hit 4

offset: 1960736

```js
ush({ part, planned, lo, hi, status, weeks: vr.weeks, confidence: vr.confidence });
    }
    // the actionable ones first — a mismatch is worth a glance, a match is worth a nod
    const rank = { over: 0, under: 1, in: 2 };
    out.sort((a, b) => rank[a.status] - rank[b.status] || b.weeks - a.weeks);
    return out.slice(0, 3);
}
/* ===================== COACH FACTS ===================== *
 * The engine already makes calibrated decisions — effort bias, per-muscle readiness, plateau
 * classification, a personal rep curve — but every one of them was invisible: they changed the
 * numbers silently, and a silently-changed number is indistinguishable from a wrong one. This
 * assembles the session's WHY as short declarative facts, in priority order, for a small card at the
 * top of the session. Rules of the feed:
 *   - only facts that are TRUE TODAY, computed from the same model the prescriptions use — never
 *     generic tips, never motivation. If the engine didn't act on it, it doesn't get said;
 *   - each fact names its evidence (readiness %, session counts, comparison counts) because a claim
 *     with its receipts is coaching and a claim without them is a horoscope;
 *   - capped at 4, most consequential first: what changed today's numbers outranks what merely
 *     describes them.
 * Pure function of (lifterModel, day) — testable without the UI, and cheap because the model is
 * already memoized upstream.
 */
/* Each fact carries BOTH a full `text` and a `short` headline. The short one is what the collapsed
   pill shows mid-session: truncating the full sentence with an ellipsis would cut it at whatever
   character the screen width lands on, which on a phone routinely lands before the fact. A written
   headline says the same thing in a width that fits. `short` is optional — anything without one falls
   back to its text, so a new fact is never silently blank. */
function coachFacts(lm, day, history) {
    const facts = [];
    if (!lm || !day)
        return facts;
    const exs = (day.exercises || []).map(id => EX_BY_ID[id]).filter(Boolean);
    // Coach copy is written for the lifter, not for the model debugger. Keep the underlying signals,
    // but translate each one into: WHAT TO DO -> WHY -> optional evidence. Facts also carry lightweight
    // applicability metadata (`exId`, `part`, `scope`, `priority`) so the live workout can show only the
    // one that matters for the exercise/set in front of you instead of dumping the whole session model.
    for (const ex of exs) {
        const lift = lm.lifts && lm.lifts[ex.id];
        const pl = lift && lift.plateau;
        if (!pl)
            continue;
        if (pl.kind === "fatigue") {
            facts.push({
                icon: "deload",
                key: `plateau-fatigue:${ex.id}`,
                exId: ex.id,
                priority: 100,
                short: `${ex.name}: back off today`,
                title: `Back off on ${ex.name} today`,
                text: "Recent sessions suggest fatigue is building. Keep the reps clean and take a small load reduction if the warm-up feels unusually heavy.",
                evidence: "Recent performance trend"
            });
        }
        else if (pl.kind === "true") {
            facts.push({
                icon: "recal",
                key: `plateau:${ex.id}`,
                exId: ex.id,
                priority: 85,
                short: `${ex.name}: use today's recalculated load`,
                title: `Use today's load on ${ex.name}`,
                text: "Progress has stalled, so today's target is based on your current strength instead of simply repeating the last session.",
                evidence: `${pl.since} sessions without a new best`
            });
        }
    }
    const tired = [];
    for (const part of new Set(exs.map(e => e.part))) {
        const m = lm.muscles && lm.muscles[part];
        if (m && m.readiness < 60)
            tired.push({ part, r: m.readiness });
    }
    tired.sort((a, b) => a.r - b.r);
    for (const t of tired) {
        const label = PART_LABEL[t.part] || t.part;
        facts.push({
            icon: "fatigue",
            key: `readiness:${t.part}`,
            part: t.part,
            priority: 92,
            short: `${label}: use the lighter target`,
            title: `Give ${label.toLowerCase()} a little more recovery`,
            text: "Today's target is already reduced. Follow it as written and don't add the weight back just because the first set feels easy.",
            evidence: `${label} recovery ${t.r}%`
        });
    }
    const eff = lm.effort;
    if (eff && eff.confidence > 0 && Math.abs(eff.bias) >= 0.4) {
        const harderThanLogged = eff.bias > 0;
        facts.push({
            icon: "cal",
            key: "effort-bias",
            scope: "session",
            priority: 60,
            short: "Trust today's load",
            title: "Trust today's prescribed load",
            text: harderThanLogged
                ? "Your sets usually end up harder than you rate them. Today's load already accounts for that, so avoid adding extra weight early."
                : "You usually have a little more left than you rate. Today's load already accounts for that, so use the listed target instead of deliberately holding back.",
            evidence: eff.n ? `Learned from ${eff.n} logged sets` : null
        });
    }
    const primary = exs.find(e => e.type === "compound") || exs[0];
    if (primary && history) {
        const sl = personalRepSlope(history, primary);
        if (Math.abs(sl - 30) >= 2.5) {
            const holdsWell = sl > 30;
            facts.push({
                icon: "curve",
                key: `rep-curve:${primary.id}`,
                exId: primary.id,
                priority: 70,
                short: `${primary.name}: use the listed reps`,
                title: holdsWell ? "You hold reps well as weight climbs" : "Your reps drop sooner as weight climbs",
                text: `The ${primary.name} target uses your own lifting history, so follow the listed reps and load instead of converting from a generic percentage chart.`,
                evidence: "Your logged sets"
            });
        }
    }
    // Volume response is valuable context in Progress, but it usually does not change the NEXT SET.
    // Keep the evidence available to diagnostics/other surfaces while explicitly keeping it out of the
    // live workout feed. The live Coach should earn screen space by changing an action now.
    if (history) {
        for (const part of new Set(exs.map(e => e.part))) {
            const vr = volumeResponse(history, part);
            if (vr && vr.best) {
                const label = PART_LABEL[part] || part;
                facts.push({
                    icon: "dose",
                    key: `volume:${part}`,
                    part,
                    live: false,
                    priority: 0,
                    short: `${label}: current volume is working`,
                    title: `${label} volume looks productive`,
                    text: `Your best recent progress has happened around ${vr.best.lo}–${vr.best.hi} hard sets per week. This plan is keeping ${label.toLowerCase()} near that range.`,
                    evidence: `${vr.weeks} weeks of your training`
                });
            }
        }
    }
    return facts;
}
/* ONE coach surface. The session had grown TWO, built at different times and both branded "Coach":
   a strip pinned under the session header carrying coachNote (why the block shapes today the way it
   does) and this card carrying coachFacts (what the engine did to today's numbers because of YOU).
   Stacked, they read as the app saying the same thing twice in two different voices, and the strip
   held a line of screen for the whole workout to repeat a sentence that is identical every session
   of the block. Neither piece of information is dropped — the note is now simply t
```

## recent sessions hit 5

offset: 2368064

```js
at it converts to: maturity is counted per movement
               you actually perform, and two different dumbbell presses both converting to `bb-bench` are
               two movements, not one. The conversion happens at scoring time, below. */
            if (!cur || e > cur.e || unit !== cur.unit) {
                bestById[id] = { e, unit };
                touched = true;
            }
        });
        patsThis.forEach(pt => seenPats.add(pt));
        if (weighted.some(pt => !seenPats.has(pt)))
            return; // not yet comparable — see the note above
        if (!touched && changed)
            return; // no new PR on a scorable lift → score unchanged, skip a point
        changed = true;
        /* ⚠ THE LINE AND THE NUMBER ABOVE IT ARE THE SAME FUNCTION. This block used to re-implement the
           blend — and it re-implemented a DIFFERENT one: barbell lifts only, no estimated conversions,
           so a lifter whose score included converted work saw a final point that disagreed with the
           headline on the same card ("56/100" above "· 47/100 now"). `scoreCompose` is now the only
           place that blend exists, so the last point IS the headline by construction. */
        const entries = [];
        Object.entries(bestById).forEach(([id, { e, unit }]) => {
            const use = STANDARDS[id] ? { id, val: e, est: false } : scoreEquivalent(id, e, unit, bw, bwUnit);
            if (!use)
                return;
            const lvl = strengthLevel(use.id, use.val, unit, bw, bwUnit, sex, age);
            if (!lvl)
                return;
            entries.push({ id, pat: SCORE_PATTERN[use.id] || null, est: !!use.est, lvl });
        });
        if (!entries.length)
            return;
        const composed = scoreCompose(entries, liftSessions, patSessions);
        overall.push({ date: h.date, v: composed.overall });
        Object.keys(byPat).forEach(p => { if (composed.byPat[p])
            byPat[p].push({ date: h.date, v: scoreTo100(composed.byPat[p].c) }); });
    });
    if (overall.length < 2)
        return null; // need at least two points to draw a trend
    /* NO BASELINE OFFSET IS NEEDED ANY MORE, and startIdx stays for the caller's sake.
     *
     * There used to be a "coverage-fair baseline" here that hunted for the first point at which every
     * pattern was present, because early points were computed over fewer patterns than later ones and
     * comparing across them read as a loss. That was a correction applied downstream of the real
     * problem. The weights are now fixed across the series and no point is emitted until every pattern
     * that carries weight has data, so every point in `overall` is already measured the same way and
     * index 0 is a fair comparison. */
    const startIdx = 0;
    return { overall, byPat, startIdx };
}
/* ---- auto-regulated volume: per-muscle starting-set advice for the next block ---- */
/* Is the lifter retraining this muscle before it recovers, over recent sessions? Uses the LEARNED
 * recovery time (#4) when the history has earned one, else the clock. A majority of recent inter-session
 * gaps under ~85% of the recovery window = recovery-limited — piling on volume next week would only dig
 * the hole deeper, so the weekly progression holds instead of adding. This is what carries the measured
 * recovery model into the week-to-week decision, not just the per-session readiness trim. */
function underRecoveredWeekly(history, part) {
    const sess = [];
    for (const h of (history || []).filter(h => h && h.date).sort((a, b) => a.date - b.date)) {
        let n = 0;
        for (const [id, p] of Object.entries(h.perf || {})) {
            const ex = EX_BY_ID[id];
            if (ex && ex.part === part && p.sets)
                n += p.sets.filter(s => isWorkSet(s)).length;
        }
        if (n > 0)
            sess.push({ date: h.date, sets: n });
    }
    if (sess.length < 3)
        return false;
    const recent = sess.slice(-5);
    const avgSets = recent.reduce((a, s) => a + s.sets, 0) / recent.length;
    const clock = 30 + clamp(avgSets, 0, 28) * 2.3;
    const rec = personalRecoveryHours(history, part, clock) ?? clock;
    const gaps = [];
    for (let i = 1; i < recent.length; i++) {
        const g = (recent[i].date - recent[i - 1].date) / 3600000;
        if (g > 0 && g <= 240)
            gaps.push(g);
    }
    if (!gaps.length)
        return false;
    return gaps.filter(g => g < rec * 0.85).length >= Math.ceil(gaps.length / 2);
}
/* Week-to-week volume progression — the RP-style set-progression, now personal and recovery-aware.
 * Three changes over the population version: (1) the "add a set" bump caps at the lifter's OWN MAV
 * (personalMav) rather than ramping blindly to the population MRV — at your productive ceiling it holds
 * and lets load drive; (2) a recovery gate (underRecoveredWeekly, from the measured recovery model)
 * holds volume when you've been retraining a muscle before it recovers, since more sets can't fix a
 * recovery deficit; (3) a muscle that's stalled but still BELOW its MAV is read as under-dosed and gets
 * a bump toward MAV, instead of the old blanket "stalled → cut". Output contract is unchanged: a
 * per-muscle delta in [-2,+2] with a reason; extra fields (mav, recLimited, planOverMrv) are additive.
 *
 * ⚠ PASS THE PROGRAM WHEN YOU HAVE ONE. THIS IS THE SECOND HALF OF HIS "PLANS SHOULDN'T BE TRIMMED
 * WHEN THEY WERE RUN AS PRESCRIBED", AND IT WAS OPEN FROM v586 TO v616.
 *
 * v587 fixed the readiness band, which trimmed a set every session because a population recovery
 * clock had no knowledge of the split it was judging. This function had the identical defect in a
 * different currency and it was never fixed, only surfaced: it reads LOGGED volume and has no idea
 * whether that volume was the plan or a deviation from it. So a lifter who does exactly what the app
 * prescribed gets told the app was wrong — and `autoregulate()` folds that −2 into `autoBias` on
 * every session start, which is precisely the "recovery is still trimming sets in programs" report.
 *
 * MEASURED on an on-plan lifter across 168 programs, logging every prescribed set on the program's
 * own weekday schedule: 4 programs are told to cut, always with LOGGED EXACTLY EQUAL TO PLANNED, and
 * always for a rounding-scale overshoot the plan itself designed — chest 22.4 against MRV 22, glutes
 * 26.3 against 26, hamstrings 20.4 against 20. Four tenths of a set over the ceiling, answered with a
 * two-set cut. The overage is the GENERATOR'S, and the lifter was being charged for it.
 *
 * So: when the logged volume is accounted for by the plan, the plan wins and this returns delta 0
 * with `planOverMrv`, naming the program as the thing to fix — `volumeAudit`/`regionGapFix` already
 * exist for exactly that, and a plan over its own ceiling is a program defect to surface, not sets to
 * shave silently every session. Train MORE than the plan asked and the excess is real unplanned load:
 * the trim applies unchanged, exactly as v587 kept "sooner than planned" trimming.
 *
 * Called with no program the behaviour is IDENTICAL to before, so no existing caller changes meaning
 * — but every caller that HAS a program passes it, and gates/plantrim.mjs check 4 sweeps the source
 * to keep it that way. A rule adopted by most call sites and forgotten by one is the v584 defect. */
function volumeAdvice(history, opts) {
    history = (Array.isArray(history) ? history : []).filter(h => h && h.date); // defensive: a corrupt sync/import can't crash the weekly loop
    const program = opts?.program || null;
    /* Read the plan at the week the lifter is actually training, defaulting to the peak — the same
       week `weeklyVolume` is audited at everywhere else, so the two sides are the same owner. */
    const planWeek = program ? (opts?.weekIndex ?? Math.max(1, weeksOf(program) - (program.config?.deload ? 1 : 0))) : null;
    let planned = null;
    if (
```

## recent sessions hit 6

offset: 2465295

```js
d.sessionsCount === 1 ? "" : "s"] })] })] }), _jsxs("div", { className: "wpb-scroll", style: { flex: 1, overflowY: "auto", padding: "16px 16px 40px" }, children: [_jsx("div", { style: { display: "flex", gap: 8, marginBottom: 16 }, children: [["Best 1RM", `${trend.prBest} ${unit}`], ["Latest", `${trend.last.top.w}${trend.last.top.r != null ? "×" + trend.last.top.r : ""}`], ["Change", `${trend.delta > 0 ? "+" : ""}${trend.delta} ${unit}`]].map(([k, v], i) => (_jsxs("div", { style: { flex: 1, background: C.card, borderRadius: 16, padding: "12px 8px", textAlign: "center" }, children: [_jsx("div", { className: "mono", style: { fontSize: 15, fontWeight: 600, color: i === 2 && trend.delta < 0 ? C.warn : C.accent }, children: v }), _jsx("div", { style: { fontSize: 11, color: C.muted, marginTop: 2 }, children: k })] }, k))) }), pl && (_jsxs("div", { style: { display: "flex", gap: 8, alignItems: "flex-start", background: C.warnDim, borderRadius: 16, padding: "12px 14px", marginBottom: 16 }, children: [_jsx(Battery, { size: 16, color: C.warn, style: { flexShrink: 0, marginTop: 1 } }), _jsxs("div", { children: [_jsx("div", { style: { fontSize: 13, fontWeight: 700, color: C.warn }, children: "Plateau detected" }), _jsx("div", { style: { fontSize: 13, color: C.text, marginTop: 2, lineHeight: 1.4 }, children: pl.advice })] })] })), lvl && (_jsxs("div", { style: { background: C.card, borderRadius: 16, padding: "14px 16px", marginBottom: 16 }, children: [_jsxs("div", { style: { display: "flex", justifyContent: "space-between", alignItems: "baseline", marginBottom: 8 }, children: [_jsx("div", { style: { fontSize: 15, fontWeight: 700 }, children: "Strength level" }), _jsxs("div", { style: { fontSize: 13, fontWeight: 700, color: C.accentInk }, children: [lvl.level, " ", _jsxs("span", { className: "mono", style: { color: C.muted, fontWeight: 500 }, children: ["\u00B7 ", lvl.ratio.toFixed(2), "\u00D7 BW"] })] })] }), _jsx("div", { style: { display: "flex", gap: 2 }, children: STD_LEVELS.slice(1).map((nm, i) => (_jsx("div", { style: { flex: 1, height: 8, borderRadius: 8, background: i < lvl.idx ? C.accent : C.bg2 } }, nm))) }), _jsx("div", { style: { display: "flex", justifyContent: "space-between", marginTop: 6, fontSize: 11, color: C.faint }, children: STD_LEVELS.slice(1).map(nm => _jsx("span", { style: { flex: 1, textAlign: "center" }, children: nm.slice(0, 4) }, nm)) }), _jsxs("div", { style: { fontSize: 11, color: C.faint, marginTop: 8, lineHeight: 1.4 }, children: ["Based on best est. 1RM (", trend.prBest, unit, ") vs ", sex, " bodyweight standards. Set bodyweight in Settings."] })] })), _jsxs("div", { style: { background: C.card, borderRadius: 16, padding: "14px 8px 6px", marginBottom: 16 }, children: [_jsx("div", { style: { fontSize: 13, color: C.muted, fontWeight: 600, padding: "0 8px 6px" }, children: "Estimated 1RM \u00B7 every set plotted" }), _jsx(ProgressChart, { trend: trend })] }), _jsx("div", { style: { ...eyebrow(), margin: "0 2px 10px" }, children: "Recent sessions" }), recent.map((s, i) => (_jsxs("div", { style: { background: C.card, borderRadius: 16, padding: 12, marginBottom: 8 }, children: [_jsxs("div", { style: { display: "flex", justifyContent: "space-between", marginBottom: 6 }, children: [_jsx("span", { style: { fontSize: 13, fontWeight: 600 }, children: dF(s.date) }), _jsxs("span", { className: "mono", style: { fontSize: 13, color: C.accentInk, fontWeight: 600 }, children: ["est 1RM ", s.best, unit] })] }), _jsx("div", { style: { display: "flex", flexWrap: "wrap", gap: 6 }, children: s.sets.map((x, j) => (_jsxs("span", { className: "mono", style: { fontSize: 13, fontWeight: 600, color: x.e1rm === s.best ? C.accent : C.text, background: x.e1rm === s.best ? C.accentDim : C.bg2, padding: "3px 8px", borderRadius: 8 }, children: [x.w, x.r != null ? `×${x.r}` : ""] }, j))) })] }, i)))] })] }));
}
function RecoveryTrendCard({ history }) {
    const rec = useMemo(() => muscleRecovery(history), [history]);
    const fb = useMemo(() => {
        const m = {};
        [...(history || [])].slice().reverse().forEach(h => { if (!h.feedback)
            return; Object.entries(h.feedback).forEach(([p, v]) => { if (v == null)
            return; (m[p] = m[p] || []).push(v); }); });
        Object.keys(m).forEach(p => m[p] = m[p].slice(-8));
        return m;
    }, [history]);
    const rows = rec.filter(r => r.daysSince != null || fb[r.part]).sort((a, b) => a.readiness - b.readiness);
    if (!rows.length)
        return null;
    const color = s => s === "fresh" ? C.accent : s === "recovering" ? (C.warn || "#e0a31e") : C.danger;
    const dot = v => v > 0 ? EFFORT_EASY : v < 0 ? EFFORT_HARD : C.muted;
    return (_jsxs("div", { style: { background: C.card, borderRadius: 16, padding: 16, marginBottom: 16 }, children: [_jsxs("div", { style: { display: "flex", alignItems: "center", gap: 8, marginBottom: 4 }, children: [_jsx(Activity, { size: 16, color: C.accentInk }), _jsx("span", { style: { fontSize: 15, fontWeight: 700 }, children: "Recovery & feedback" })] }), _jsx("div", { style: { fontSize: 13, color: C.muted, marginBottom: 12 }, children: "Freshness now, plus how each muscle has felt recently" }), rows.map(r => (_jsxs("div", { style: { marginBottom: 12 }, children: [_jsxs("div", { style: { display: "flex", justifyContent: "space-between", alignItems: "baseline", marginBottom: 4 }, children: [_jsx("span", { style: { fontSize: 13, fontWeight: 500 }, children: PART_LABEL[r.part] }), _jsxs("span", { className: "mono", style: { fontSize: 11, fontWeight: 600, color: color(r.status) }, children: [r.readiness, "%"] })] }), _jsxs("div", { style: { display: "flex", alignItems: "center", gap: 8 }, children: [_jsx("div", { style: { flex: 1, height: 5, borderRadius: 999, background: C.bg2, overflow: "hidden" }, children: _jsx("div", { style: { width: `${r.readiness}%`, height: "100%", background: color(r.status), borderRadius: 999 } }) }), fb[r.part] && _jsx("div", { style: { display: "flex", gap: 2 }, children: fb[r.part].map((v, i) => (_jsx("span", { title: v > 0 ? "Easy" : v < 0 ? "Smashed" : "Good", style: { width: 7, height: 7, borderRadius: 999, background: dot(v) } }, i))) })] })] }, r.part))), _jsx("div", { style: { fontSize: 11, color: C.faint, marginTop: 4, display: "flex", gap: 12, justifyContent: "flex-end" }, children: [["easy", EFFORT_EASY], ["good", C.muted], ["smashed", EFFORT_HARD]].map(([l, c]) => (_jsxs("span", { style: { display: "flex", alignItems: "center", gap: 4 }, children: [_jsx("span", { style: { width: 7, height: 7, borderRadius: 999, background: c } }), l] }, l))) })] }));
}
function TrainingHeatmap({ history }) {
    const today = useMemo(() => { const d = new Date(); d.setHours(0, 0, 0, 0); return d; }, []);
    const [viewMonth, setViewMonth] = useState(() => ({ y: today.getFullYear(), m: today.getMonth() }));
    const [selDay, setSelDay] = useState(null); // {key, date, sessions}
    const key = (d) => `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`;
    // Index history by day so a tapped cell can show what was trained.
    const { byDay, streaks } = useMemo(() => {
        const byDay = {};
        (history || []).forEach(h => {
            if (!h.date)
                return;
            const d = new Date(h.date);
            d.setHours(0, 0, 0, 0);
            const k = key(d);
            (byDay[k] = byDay[k] || []).push(h);
        });
        const daySet = new Set(Object.keys(byDay));
        let cur = 0;
        for (let i = 0; i < 400; i++) {
            const d = new Date(today);
            d.setDate(today.getDate() - i);
            if (daySet.has(key(d)))
                cur++;
            else if (i > 0)
                break;
        }
        // longest streak across all history
        const allDays = Object.keys(byDay).map(k => { const [y, m, dd] = k.split("-").map(Number); return new Date(y, m, dd).getTime(); }).sort((a, b) => a - b);
        let longest = 0, run = 0, prev = null;
        // Consecutive-day ch
```

## recent sessions hit 7

offset: 2621248

```js
alignItems: "center", gap: 8 }, children: [_jsx(BarChart3, { size: 16, color: C.accentInk }), _jsx("span", { style: { fontSize: 15, fontWeight: 700 }, children: "This week" })] }), r.prs > 0 && _jsxs("span", { style: { display: "flex", alignItems: "center", gap: 4, fontSize: 13, fontWeight: 700, color: C.accentInk }, children: [_jsx(Trophy, { size: 13 }), " ", r.prs, " PR", r.prs === 1 ? "" : "s"] })] }), _jsx("div", { style: { display: "flex", gap: 8 }, children: [["Workouts", r.count, null], ["Sets", r.sets, sd], [`Volume${unit ? ` (${unit})` : ""}`, `${(r.vol / 1000).toFixed(r.vol >= 10000 ? 0 : 1)}k`, vd]].map(([k, v, d]) => (_jsxs("div", { style: { flex: 1, background: C.bg2, borderRadius: 12, padding: "11px 6px", textAlign: "center" }, children: [_jsxs("div", { className: "mono", style: { fontSize: 18, fontWeight: 700, color: C.text, lineHeight: 1 }, children: [v, _jsx(RecapArrow, { d: d })] }), _jsx("div", { style: { fontSize: 11, color: C.muted, marginTop: 4, fontWeight: 600 }, children: k })] }, k))) }), r.topMuscle && _jsxs("div", { style: { fontSize: 13, color: C.muted, marginTop: 12, paddingTop: 12, borderTop: `1px solid ${C.borderSoft}` }, children: [_jsx("span", { style: { color: C.accentInk, fontWeight: 600 }, children: "Most trained:" }), " ", PART_LABEL[r.topMuscle[0]], " (", r.topMuscle[1], " sets)", r.prevCount ? ` · ${r.count > r.prevCount ? "ahead of" : r.count === r.prevCount ? "on pace with" : "behind"} last week (${r.prevCount})` : ""] })] }));
}
function MuscleRecoveryCard({ history, onLight, canLight }) {
    const rec = useMemo(() => muscleRecovery(history), [history]);
    const trained = rec.filter(r => r.daysSince != null).sort((a, b) => a.readiness - b.readiness);
    if (!trained.length)
        return null;
    const color = (s) => s === "fresh" ? C.accent : s === "recovering" ? (C.warn || "#e0a31e") : C.danger;
    const major = ["chest", "lats", "upper_back", "shoulders", "quads", "hamstrings", "glutes", "biceps", "triceps"];
    const ready = rec.filter(r => r.readiness >= 85 && major.includes(r.part)).map(r => PART_LABEL[r.part]).slice(0, 4);
    const majorTrained = rec.filter(r => major.includes(r.part) && r.daysSince != null);
    const avgReady = majorTrained.length ? majorTrained.reduce((s, r) => s + r.readiness, 0) / majorTrained.length : 100;
    const fatigued = majorTrained.filter(r => r.readiness < 50).length;
    const restAdvised = majorTrained.length >= 3 && (avgReady < 50 || fatigued >= Math.ceil(majorTrained.length * 0.6));
    return (_jsxs("div", { style: { background: C.card, borderRadius: 16, padding: "16px", marginBottom: 20 }, children: [_jsxs("div", { style: { display: "flex", alignItems: "center", gap: 8, marginBottom: 4 }, children: [_jsx(Activity, { size: 16, color: C.accentInk }), _jsx("span", { style: { fontSize: 15, fontWeight: 700 }, children: "Muscle recovery" })] }), _jsx("div", { style: { fontSize: 13, color: C.muted, marginBottom: 12 }, children: "Estimated freshness from your recent sessions" }), _jsx("div", { style: { display: "grid", gridTemplateColumns: "1fr 1fr", gap: "10px 16px" }, children: trained.slice(0, 10).map(r => (_jsxs("div", { children: [_jsxs("div", { style: { display: "flex", justifyContent: "space-between", alignItems: "baseline", marginBottom: 4 }, children: [_jsx("span", { style: { fontSize: 13, fontWeight: 500, color: C.text }, children: PART_LABEL[r.part] }), _jsxs("span", { className: "mono", style: { fontSize: 11, fontWeight: 600, color: color(r.status) }, children: [r.readiness, "%"] })] }), _jsx("div", { style: { height: 5, borderRadius: 999, background: C.bg2, overflow: "hidden" }, children: _jsx("div", { className: "wpb-bar", style: { background: color(r.status), transform: `translateX(-${100 - r.readiness}%)` } }) })] }, r.part))) }), restAdvised ? (_jsxs("div", { style: { marginTop: 12, paddingTop: 12, borderTop: `1px solid ${C.borderSoft}` }, children: [_jsxs("div", { style: { display: "flex", alignItems: "flex-start", gap: 8, fontSize: 13, color: C.text }, children: [_jsx(Battery, { size: 15, color: C.warn || "#e0a31e", style: { flexShrink: 0, marginTop: 1 } }), _jsx("span", { children: "Most muscles are still fatigued. Rest, or do a light active-recovery session \u2014 reduced volume and easy effort to drive blood flow without adding fatigue." })] }), canLight && (_jsxs("button", { onClick: onLight, className: "pressable", style: { width: "100%", marginTop: 12, padding: "11px", borderRadius: 12, border: `1px solid ${C.accent}55`, background: C.accentDim, color: C.accentInk, fontSize: 13, fontWeight: 700, cursor: "pointer", display: "flex", alignItems: "center", justifyContent: "center", gap: 6 }, children: [_jsx(Activity, { size: 14 }), " Do a light session instead"] }))] })) : ready.length > 0 && (_jsxs("div", { style: { fontSize: 13, color: C.muted, marginTop: 12, paddingTop: 12, borderTop: `1px solid ${C.borderSoft}` }, children: [_jsx("span", { style: { color: C.accentInk, fontWeight: 600 }, children: "Fresh & ready:" }), " ", ready.join(", ")] }))] }));
}
function StrengthSnapshotCard({ history, bodyweight, sex, age, unit, bwLog = [], onHistory }) {
    const snap = useMemo(() => strengthSnapshot(history, parseFloat(bodyweight), unit, sex, age), [history, bodyweight, sex, unit, age]);
    const sscore = useMemo(() => strengthScore(history, parseFloat(bodyweight), unit, sex, age), [history, bodyweight, sex, unit, age]);
    // Does the lifter have any logged lift that COULD be scored (a standard lift with a PR)? If so but
    // the score is blocked purely because bodyweight isn't set, show an unlock prompt rather than
    // rendering nothing — otherwise a brand-new user who logged a few lifts sees a mysterious blank.
    const hasScorableLift = useMemo(() => {
        if (parseFloat(bodyweight) > 0)
            return false; // already have bw → snap covers it
        // scoreTargetFor, not STANDARDS: a dumbbell-only lifter has scorable work too, and testing the
        // barbell table directly would deny them the unlock prompt and leave the same blank card the
        // prompt exists to prevent.
        return exerciseTrends(history).some(t => scoreTargetFor(t.id) && t.prBest > 0);
    }, [history, bodyweight]);
    const attrib = useMemo(() => scoreAttribution(history, bwLog, parseFloat(bodyweight), unit, sex, age), [history, bwLog, bodyweight, unit, sex, age]);
    if (!snap) {
        if (!hasScorableLift)
            return null;
        return (_jsxs("button", { onClick: onHistory, className: "pressable", style: { width: "100%", textAlign: "left", background: C.card, border: `1px dashed ${C.border}`, borderRadius: 16, padding: "14px 16px", marginBottom: 12, cursor: "pointer", color: C.text }, children: [_jsxs("div", { style: { display: "flex", alignItems: "center", gap: 6, marginBottom: 8 }, children: [_jsx(Award, { size: 15, color: C.accentInk }), _jsx("span", { style: { fontSize: 15, fontWeight: 700 }, children: "Strength Score" }), _jsx(ChevronRight, { size: 15, color: C.faint, style: { marginLeft: "auto" } })] }), _jsx("div", { style: { fontSize: 13, color: C.muted, lineHeight: 1.5 }, children: "Add your bodyweight to unlock your Strength Score \u2014 a single 0\u2013100 number with push, pull, squat and hinge breakdowns, scaled to your age and bodyweight." })] }));
    }
    /* ⚠ THE LABEL DESCRIBES THE NUMBER BESIDE IT. The score printed here is `sscore.overall` — the
       maturity-weighted 0–100 — while the level, the "next level" line and the elite highlight were
       all read off `snap.overallIdx`, an unweighted round of the raw per-lift mean on 0–5. On a real
       account that put "Advanced" next to 56 with every sub-score at Novice or below. Both now come
       from the same owner (see scoreLevelIdx). */
    const score = sscore ? sscore.overall : scoreTo100(snap.score);
    const lvlIdx = sscore ? sscore.levelIdx : scoreLevelIdx(score);
    const lvlLabel = sscore ? sscore.level : STD_LEVELS[lvlIdx];
    const nex
```

## recent sessions hit 8

offset: 2778771

```js
 The correction itself keeps its full precision where the load maths uses it."],
            [AlertTriangle, "Fixed: un-ticking a set left its numbers behind", "Completing a set retunes the next one from what you just lifted. Un-ticking it put the tick back but left the retune in place — so weights and reps written by a set you had un-logged stayed on screen with nothing to explain them, and a few mis-taps while setting up filled an exercise with numbers you never asked for. Un-ticking now puts the affected set back exactly as the program wrote it. Anything you typed yourself is left alone."],
            [Dumbbell, "The plate breakdown follows the set you're about to do", "The loading strip under an exercise was pinned to the first working set and stayed there. On a ramp it told you to load the bar for set 1 while you were standing at it for set 3. It now reads the next set you haven't logged."],
        ] },
    { version: "3.15.0", build: "520", items: [
            [AlertTriangle, "Fixed: doing exactly what the app asked could take weight off the bar", "Every set is prefilled at the bottom of its rep range — that is what the range means: start there and build up. But after you logged one, the app predicted you would get one or two fewer reps on the next set from fatigue, treated that prediction as a failed set, and stripped an increment. Log the 40x10 it asked for and the next set became 35; log 95x10 and the next became 90. The weight now only changes when a set you actually PERFORMED lands outside the range — too many reps and it goes up, a real miss and it comes down. Reps drifting down across your sets is normal and no longer costs you load."],
            [AlertTriangle, "Fixed: tightening your RIR did nothing at all", "A block that moves from 2 reps-in-reserve to 1 is asking for more effort at the same reps, so the load should step up. It never did. Unless you had rated every set by hand, the strength estimate quietly used THIS week's target as the effort for LAST week's sets, which cancels out exactly — so 2 RIR, 1 RIR and 0 RIR produced identical suggestions forever. Your logged sets already record the RIR they were prescribed at, and that is what gets read now."],
            [AlertTriangle, "Fixed: one lift, three different answers about how it progresses", "The style badge, the advice line under it and the set weights themselves each worked out the progression style separately, and could each get a different answer, because one of them was not told which day it was looking at. That is how a lift could be labelled \"e1RM autoregulation\" while the advice underneath said \"same load, build toward 15 reps\" and the sets below were laid out with per-set weights. They all ask one function now."],
            [TrendingUp, "e1RM autoregulation is temporary, and says so", "When a lift stalls, the app switches it to e1RM autoregulation to recalibrate the load. It had no way back: it checked whether you were still stalled by looking at your recent sessions, but autoregulation holds you steady on purpose, so the check could only ever see more flat sessions. The switch is now time-boxed — it hands the lift back to your program's own progression on a new best or after a few sessions either way — and the badge says why it engaged and what ends it."],
            [Zap, "Myo-reps rest for 15 seconds, not two minutes", "The short pause is the whole technique, and the first one never happened: the set that takes you to failure is an ordinary working set, so finishing it started the full working rest. The timer now looks at the set you are about to do rather than the one you just finished, so the pause before the first mini-set is short — and the first drop of a drop set is immediate."],
            [Zap, "Myo-reps and drop sets follow your actual working weight", "The mini-sets copied the weight once, when the exercise was first laid out, and then ignored it. Change the working weight afterwards — by hand, with the steppers, or because the app retuned it mid-exercise — and the extensions were left showing the old number. They are worked out from the set they extend now, every time, so a myo-rep always matches the set it came from and a drop set stays the right fraction of it."],
            [Zap, "No delete button on a myo-rep the program asked for", "Skipping a prescribed mini-set costs you nothing, so a delete control sitting at the end of the row was all risk and no benefit. Extensions you added yourself keep theirs, and the Myo button still removes a prescribed set deliberately."],
            [AlertTriangle, "Fixed: the target column could disagree with the weight box", "When the app retuned a set from the one you just logged, it changed the weight but left the target beside it reading the old prescription — so the row showed a target of 40 and a weight of 35 with nothing explaining the difference. The target follows the retune now."],
            [Target, "The suggestion names the set it came from", "The line above your sets showed your BEST set from last time while the advice beside it was worked out from your LAST one. If your reps fell across the exercise you would read \"last 40x15 — build toward 15 reps\", which looks like the app failing to notice a set it had just shown you. It now shows the set the suggestion was actually built from."],
        ] },
    { version: "3.14.9", build: "519", items: [
            [Layers, "Program cards are roomier again", "The Switch button added last build was a word taking up card width, which squeezed program names down to a few letters — worst inside training cycles, where the cards are already narrow. It is back to a compact tick; hold it for a moment and it tells you what it does. Long names now wrap onto a second line instead of being cut off, and cycle blocks got a little of their width back."],
            [Layers, "Pick a program from a list instead of hunting for its card", "There is a Switch program button on your Up Next card now. It opens a list of everything you have saved, showing how long each program is, how many days a week it runs, how much of it you have finished and which block of a cycle it belongs to — the things you would actually compare. Your current program is marked and the switch still announces itself with an Undo."],
            [Layers, "Switching programs now tells you it happened", "Changing your active program used to do it silently — the card jumped to the top of the list and nothing else. It now says which program you have switched to and offers Undo, which also puts a training cycle back to the block it was on. The control is a labelled Switch button rather than a tick sitting next to Delete."],
            [AlertTriangle, "Fixed: six side-scrolling rows could block the page from scrolling", "Rows that scroll sideways — the gym chips on Home, the equipment lists in Gyms, the chart ranges in an exercise, the gallery filters — could swallow an up-and-down swipe that started on them, so the page underneath stayed put. Same fault as the Plan tab last build, in six other places."],
            [Palette, "Plan phase colors now come from your theme", "The phase colors added in the last build were fixed values that fought most of the themes. Each phase now takes its color from your accent, shifted just enough to tell them apart, so the Plan belongs to whichever theme you are using. Deloads still sit apart on purpose."],
            [Layers, "Plan header rows line up", "The phase strip used fixed-width chips while the stat boxes above divided the width evenly, so the two rows did not share a grid. They now line up edge to edge."],
            [AlertTriangle, "Fixed: tapping a day in the Plan sent you to Home", "Opening the Plan tab straight after launching the app and tapping any day bounced you back to the Home tab instead of showing the workout. The Plan works out its own program, but the preview screen was still checking for one you had explicitly opened."],
            [Laye
```

## function volumeVerdicts hit 1

offset: 1957233

```js
   const bins = terc.filter(t => t.length >= 2).map(t => ({
            lo: Math.min(...t.map(x => x.sets)), hi: Math.max(...t.map(x => x.sets)), gain: med(t), n: t.length
        }));
        if (bins.length >= 2) {
            const ranked = bins.slice().sort((x, y) => y.gain - x.gain);
            const clear = ranked[0].gain - ranked[1].gain >= 0.15;
            out = { part, weeks: pairs.length, spread, bins,
                best: clear ? { lo: ranked[0].lo, hi: ranked[0].hi, gain: ranked[0].gain } : null,
                confidence: Math.min(1, (pairs.length - 8) / 16) };
        }
    }
    byPart.set(part, out);
    return out;
}
// Personal MAV — the lifter's own "how much is genuinely a lot" line for one muscle, learned from the
// volumeResponse dose-response verdict and shrunk toward the group prior by confidence. This is the
// payoff of the mavFor seam: the readiness trim (and anything else that asks "is there volume to spare?")
// can now defer to what THIS lifter's history shows instead of the population average — the clearest,
// best-evidenced finding in the volume literature is that the productive range is individual.
//
// Honesty is the whole design, inherited from volumeResponse: it returns a clear `best` tercile ONLY
// when the data identifies one (≥6 window-pairs, ≥5 sets of spread, a real margin over the runner-up),
// so this personalizes ONLY when the history has earned it — otherwise it hands back the group MAV
// unchanged. The signal is the TOP of the best-responding tercile: the highest weekly volume that was
// still maximally productive for this lifter. It never leaves [MEV, MRV], and it shrinks toward the
// prior by confidence — history nudges the number, it never lurches to it (same rule as effort calib).
function personalMav(history, part) {
    const groupMav = mavFor(part);
    if (!history || !history.length)
        return groupMav;
    const L = landmarkFor(part);
    const dr = volumeResponse(history, part);
    if (!dr || !dr.best)
        return groupMav;
    const signal = clamp(dr.best.hi, L.mev, L.mrv); // top of the lifter's best-responding band
    const c = clamp(dr.confidence || 0, 0, 1);
    return clamp(groupMav + c * (signal - groupMav), L.mev, L.mrv);
}
/* Compares what a program PLANS for each muscle against what the lifter's own history says has
 * actually produced progress. This is the pairing that makes the dose-response worth computing: the
 * coach card reaches you mid-session, but the volume panel is where sets are added and removed, and a
 * finding that never reaches the decision point is trivia.
 *
 * Only muscles with a clear verdict appear (volumeResponse returns best=null on near-ties and on
 * unidentifiable data), so the block is silent for anyone whose training hasn't varied enough to say
 * anything. Group landmarks still drive the bars and the volume check above it — this sits alongside
 * as evidence, never as an override. The lifter decides; the app shows its work.
 */
function volumeVerdicts(history, volume, parts) {
    if (!history || !history.length || !volume)
        return [];
    const out = [];
    for (const part of parts) {
        const planned = volume[part] || 0;
        if (planned <= 0.05)
            continue;
        const vr = volumeResponse(history, part);
        if (!vr || !vr.best)
            continue;
        const { lo, hi } = vr.best;
        const status = planned < lo - 0.5 ? "under" : planned > hi + 0.5 ? "over" : "in";
        out.push({ part, planned, lo, hi, status, weeks: vr.weeks, confidence: vr.confidence });
    }
    // the actionable ones first — a mismatch is worth a glance, a match is worth a nod
    const rank = { over: 0, under: 1, in: 2 };
    out.sort((a, b) => rank[a.status] - rank[b.status] || b.weeks - a.weeks);
    return out.slice(0, 3);
}
/* ===================== COACH FACTS ===================== *
 * The engine already makes calibrated decisions — effort bias, per-muscle readiness, plateau
 * classification, a personal rep curve — but every one of them was invisible: they changed the
 * numbers silently, and a silently-changed number is indistinguishable from a wrong one. This
 * assembles the session's WHY as short declarative facts, in priority order, for a small card at the
 * top of the session. Rules of the feed:
 *   - only facts that are TRUE TODAY, computed from the same model the prescriptions use — never
 *     generic tips, never motivation. If the engine didn't act on it, it doesn't get said;
 *   - each fact names its evidence (readiness %, session counts, comparison counts) because a claim
 *     with its receipts is coaching and a claim without them is a horoscope;
 *   - capped at 4, most consequential first: what changed today's numbers outranks what merely
 *     describes them.
 * Pure function of (lifterModel, day) — testable without the UI, and cheap because the model is
 * already memoized upstream.
 */
/* Each fact carries BOTH a full `text` and a `short` headline. The short one is what the collapsed
   pill shows mid-session: truncating the full sentence with an ellipsis would cut it at whatever
   character the screen width lands on, which on a phone routinely lands before the fact. A written
   headline says the same thing in a width that fits. `short` is optional — anything without one falls
   back to its text, so a new fact is never silently blank. */
function coachFacts(lm, day, history) {
    const facts = [];
    if (!lm || !day)
        return facts;
    const exs = (day.exercises || []).map(id => EX_BY_ID[id]).filter(Boolean);
    // Coach copy is written for the lifter, not for the model debugger. Keep the underlying signals,
    // but translate each one into: WHAT TO DO -> WHY -> optional evidence. Facts also carry lightweight
    // applicability metadata (`exId`, `part`, `scope`, `priority`) so the live workout can show only the
    // one that matters for the exercise/set in front of you instead of dumping the whole session model.
    for (const ex of exs) {
        const lift = lm.lifts && lm.lifts[ex.id];
        const pl = lift && lift.plateau;
        if (!pl)
            continue;
        if (pl.kind === "fatigue") {
            facts.push({
                icon: "deload",
                key: `plateau-fatigue:${ex.id}`,
                exId: ex.id,
                priority: 100,
                short: `${ex.name}: back off today`,
                title: `Back off on ${ex.name} today`,
                text: "Recent sessions suggest fatigue is building. Keep the reps clean and take a small load reduction if the warm-up feels unusually heavy.",
                evidence: "Recent performance trend"
            });
        }
        else if (pl.kind === "true") {
            facts.push({
                icon: "recal",
                key: `plateau:${ex.id}`,
                exId: ex.id,
                priority: 85,
                short: `${ex.name}: use today's recalculated load`,
                title: `Use today's load on ${ex.name}`,
                text: "Progress has stalled, so today's target is based on your current strength instead of simply repeating the last session.",
                evidence: `${pl.since} sessions without a new best`
            });
        }
    }
    const tired = [];
    for (const part of new Set(exs.map(e => e.part))) {
        const m = lm.muscles && lm.muscles[part];
        if (m && m.readiness < 60)
            tired.push({ part, r: m.readiness });
    }
    tired.sort((a, b) => a.r - b.r);
    for (const t of tired) {
        const label = PART_LABEL[t.part] || t.part;
        facts.push({
            icon: "fatigue",
            key: `readiness:${t.part}`,
            part: t.part,
            priority: 92,
            short: `${label}: use the lighter target`,
            title: `Give ${label.toLowerCase()} a little more recovery`,
            text: "Today's target is already reduced. Follow it as written and don't add the weight back just
```

## setsDone hit 1

offset: 971297

```js
      si >= 0 && si < sessionIds.length - 1
            ? { id: "session", label: `a ${(SESSIONS[si + 1].label || "").toLowerCase()} session`, patch: { session: sessionIds[si + 1] } }
            : null,
        nextDays ? { id: "days", label: `${nextDays} training days a week`, patch: { days: nextDays } } : null,
    ].filter(Boolean);
    const before = gaps.length;
    let best = null;
    for (const c of CANDIDATES) {
        let trial;
        try {
            trial = generateNextProgramForShell({ config: { ...cfg, ...c.patch }, banned: [], legacyExercises: EXERCISES, seed: program.seed }).program;
        }
        catch {
            continue;
        }
        const left = coverageGaps(trial).length;
        const closed = before - left;
        /* The PATCH travels with the answer. A caller that had to parse the human label back into a
           config change would be reading prose as an API — brittle, and it made the first version of
           gates/relief.mjs fail on its own regex rather than on behaviour. */
        if (closed > 0 && (!best || closed > best.closed))
            best = { ...c, closed, left, before };
    }
    return best; // null when no single change closes anything — see the header
}
function coverageGaps(program, strict = false) {
    const WATCHED = COVERED_MUSCLES; // see the definition — one owner, shared with programQuality
    const out = [];
    try {
        const sv = weeklySubVolume(program, weeksOf(program));
        const wv = weeklyVolume(program, weeksOf(program));
        for (const [region, L] of Object.entries(SUB_LANDMARKS)) {
            if (WATCHED.has(region) && L?.mev > 0 && (sv[region] || 0) < preferenceFloor(program, region) * GAP_FRACTION)
                out.push({ id: region, label: SUBMUSCLE_LABEL[region] || region });
        }
        for (const part of PART_ORDER) {
            const L = landmarkFor(part);
            if (WATCHED.has(part) && L?.mev > 0 && (wv[part] || 0) < preferenceFloor(program, part) * GAP_FRACTION && !out.some(o => o.id === part)) { // same threshold as the heads above
                out.push({ id: part, label: PART_LABEL[part] || part });
            }
        }
    }
    catch (e) {
        /* This catch previously hid a ReferenceError — a mistyped label map made the function return an
           empty array, which is indistinguishable from "this program covers everything". A silent catch
           around a whole computation turns a crash into a false clean bill of health, which is the worse
           failure. Re-thrown in test builds so a gate sees it; still swallowed in the app, where a
           missing notice must never take the screen down. */
        if (strict || (typeof process !== "undefined" && process.env && process.env.NODE_ENV === "test"))
            throw e;
        return [];
    }
    return out;
}
/* ── DO THEY ACTUALLY FINISH WHAT THEY ARE GIVEN? ────────────────────────────────────────────
 *
 * Every finished session already records `setsDone` against `totalSets`, and nothing has ever read
 * it. `paceFactor` next door uses the same entries to learn how long a session really takes and
 * calibrates the time estimate from it — this asks the other half of the question: not "was the
 * estimate right" but "did the plan get done".
 *
 * A generator can be right about volume and still produce sessions people stop halfway through, and
 * a set that is prescribed but never performed is not volume. That gap is invisible in every metric
 * the app tracks, because everything downstream reads what was LOGGED. A lifter quietly leaving the
 * last two exercises every session has a program that is too long, and the app currently reads them
 * as a lifter doing less volume than they should and adds more.
 *
 * ONLY SESSIONS THAT WERE GENUINELY ATTEMPTED. One abandoned after two sets says something about
 * that day, not about the plan; counted, a single bad afternoon drags the average enough to trigger
 * advice. The 0.25 floor and the requirement for real set counts mirror paceFactor's own guards,
 * which exist for exactly the same reason.
 *
 * NEEDS ENOUGH SESSIONS TO MEAN ANYTHING, and says so rather than guessing: `known` false tells the
 * caller to stay quiet, the same contract paceFactor uses. Six is two weeks of a three-day week —
 * below that a couple of rushed evenings look identical to a systematic problem. */
function completionRate(history = []) {
    const rates = [];
    let abandoned = 0, counted = 0;
    for (const h of history || []) {
        const done = Number(h.setsDone) || 0, total = Number(h.totalSets) || 0;
        if (total < 3)
            continue; // too small to read anything from
        counted++;
        const r = done / total;
        if (r < 0.25) {
            abandoned++;
            continue;
        } // an abandoned session, not a short one
        rates.push(Math.min(1, r));
    }
    if (rates.length < 6)
        return { rate: 1, n: rates.length, abandoned, counted, known: false, short: false };
    rates.sort((a, b) => a - b);
    const mid = rates.length % 2 ? rates[(rates.length - 1) / 2]
        : (rates[rates.length / 2 - 1] + rates[rates.length / 2]) / 2;
    /* MEDIAN, not mean: one session cut short by a fire alarm should not move the reading. Same reason
       paceFactor takes a median of its ratios. */
    return {
        rate: mid, n: rates.length, abandoned, counted, known: true,
        /* The actionable threshold. Leaving roughly one set in eight undone, session after session, is a
           plan that does not fit the lifter's day — not a lifter who lacks discipline, and the app should
           offer to shorten the session rather than quietly prescribe more volume to make up for it. */
        short: mid < 0.88
    };
}
function paceFactor(history = [], saved = []) {
    const byId = new Map(saved.map(p => [p.id, p]));
    const ratios = [];
    for (const h of history) {
        const p = byId.get(h.programId);
        const est = h.estMin || (p && p.days ? (() => {
            const d = p.days.find(x => x.id === h.dayId);
            try {
                return d ? estimateMinutes(p, d, h.weekIndex || 1) : 0;
            }
            catch {
                return 0;
            }
        })() : 0);
        const actual = Number(h.durationMin) || 0;
        /* Only sessions that were actually completed and plausibly timed: a session abandoned after two
           sets, or one whose clock ran overnight, says nothing about pace. */
        if (est >= 15 && actual >= 15 && actual <= est * 3 && (h.setsDone || 0) >= (h.totalSets || 1) * 0.6) {
            ratios.push(actual / est);
        }
    }
    if (ratios.length < 4)
        return { factor: 1, n: ratios.length, known: false };
    ratios.sort((a, b) => a - b);
    const mid = ratios.length % 2 ? ratios[(ratios.length - 1) / 2]
        : (ratios[ratios.length / 2 - 1] + ratios[ratios.length / 2]) / 2;
    return { factor: Math.min(1.6, Math.max(0.7, mid)), n: ratios.length, known: true };
}
/* The estimate as the lifter will actually experience it. Callers that show a time to a human use
   this; the generator keeps using estimateMinutes, for the reason above. */
/* ═══ ONE PRICE FOR A DAY ═══════════════════════════════════════════════════════════════════════
 * Two estimators used to price the same day: `estimateMinutes` (what the generator budgets against
 * its ceiling — no transitions) and `estimateMinutesFor` (what the plan shows — with transitions,
 * times the lifter's pace). MEASURED at v627 across 2,880 generated days: the generator believed 96
 * exceeded their ceiling; the number on screen exceeded it on 858 (30%), mean gap 7.1 minutes. A
 * forty-minute plan that shows 47 is the lifter's problem to solve, and they solve it by rushing
 * rests or dropping the tail — both of which the progression model then misreads.
 * Engine 15 prices a day ONCE, here: transitions are part of the budget the generator keeps, so the
 * c
```

## setsDone hit 2

offset: 972865

```js
 of Object.entries(SUB_LANDMARKS)) {
            if (WATCHED.has(region) && L?.mev > 0 && (sv[region] || 0) < preferenceFloor(program, region) * GAP_FRACTION)
                out.push({ id: region, label: SUBMUSCLE_LABEL[region] || region });
        }
        for (const part of PART_ORDER) {
            const L = landmarkFor(part);
            if (WATCHED.has(part) && L?.mev > 0 && (wv[part] || 0) < preferenceFloor(program, part) * GAP_FRACTION && !out.some(o => o.id === part)) { // same threshold as the heads above
                out.push({ id: part, label: PART_LABEL[part] || part });
            }
        }
    }
    catch (e) {
        /* This catch previously hid a ReferenceError — a mistyped label map made the function return an
           empty array, which is indistinguishable from "this program covers everything". A silent catch
           around a whole computation turns a crash into a false clean bill of health, which is the worse
           failure. Re-thrown in test builds so a gate sees it; still swallowed in the app, where a
           missing notice must never take the screen down. */
        if (strict || (typeof process !== "undefined" && process.env && process.env.NODE_ENV === "test"))
            throw e;
        return [];
    }
    return out;
}
/* ── DO THEY ACTUALLY FINISH WHAT THEY ARE GIVEN? ────────────────────────────────────────────
 *
 * Every finished session already records `setsDone` against `totalSets`, and nothing has ever read
 * it. `paceFactor` next door uses the same entries to learn how long a session really takes and
 * calibrates the time estimate from it — this asks the other half of the question: not "was the
 * estimate right" but "did the plan get done".
 *
 * A generator can be right about volume and still produce sessions people stop halfway through, and
 * a set that is prescribed but never performed is not volume. That gap is invisible in every metric
 * the app tracks, because everything downstream reads what was LOGGED. A lifter quietly leaving the
 * last two exercises every session has a program that is too long, and the app currently reads them
 * as a lifter doing less volume than they should and adds more.
 *
 * ONLY SESSIONS THAT WERE GENUINELY ATTEMPTED. One abandoned after two sets says something about
 * that day, not about the plan; counted, a single bad afternoon drags the average enough to trigger
 * advice. The 0.25 floor and the requirement for real set counts mirror paceFactor's own guards,
 * which exist for exactly the same reason.
 *
 * NEEDS ENOUGH SESSIONS TO MEAN ANYTHING, and says so rather than guessing: `known` false tells the
 * caller to stay quiet, the same contract paceFactor uses. Six is two weeks of a three-day week —
 * below that a couple of rushed evenings look identical to a systematic problem. */
function completionRate(history = []) {
    const rates = [];
    let abandoned = 0, counted = 0;
    for (const h of history || []) {
        const done = Number(h.setsDone) || 0, total = Number(h.totalSets) || 0;
        if (total < 3)
            continue; // too small to read anything from
        counted++;
        const r = done / total;
        if (r < 0.25) {
            abandoned++;
            continue;
        } // an abandoned session, not a short one
        rates.push(Math.min(1, r));
    }
    if (rates.length < 6)
        return { rate: 1, n: rates.length, abandoned, counted, known: false, short: false };
    rates.sort((a, b) => a - b);
    const mid = rates.length % 2 ? rates[(rates.length - 1) / 2]
        : (rates[rates.length / 2 - 1] + rates[rates.length / 2]) / 2;
    /* MEDIAN, not mean: one session cut short by a fire alarm should not move the reading. Same reason
       paceFactor takes a median of its ratios. */
    return {
        rate: mid, n: rates.length, abandoned, counted, known: true,
        /* The actionable threshold. Leaving roughly one set in eight undone, session after session, is a
           plan that does not fit the lifter's day — not a lifter who lacks discipline, and the app should
           offer to shorten the session rather than quietly prescribe more volume to make up for it. */
        short: mid < 0.88
    };
}
function paceFactor(history = [], saved = []) {
    const byId = new Map(saved.map(p => [p.id, p]));
    const ratios = [];
    for (const h of history) {
        const p = byId.get(h.programId);
        const est = h.estMin || (p && p.days ? (() => {
            const d = p.days.find(x => x.id === h.dayId);
            try {
                return d ? estimateMinutes(p, d, h.weekIndex || 1) : 0;
            }
            catch {
                return 0;
            }
        })() : 0);
        const actual = Number(h.durationMin) || 0;
        /* Only sessions that were actually completed and plausibly timed: a session abandoned after two
           sets, or one whose clock ran overnight, says nothing about pace. */
        if (est >= 15 && actual >= 15 && actual <= est * 3 && (h.setsDone || 0) >= (h.totalSets || 1) * 0.6) {
            ratios.push(actual / est);
        }
    }
    if (ratios.length < 4)
        return { factor: 1, n: ratios.length, known: false };
    ratios.sort((a, b) => a - b);
    const mid = ratios.length % 2 ? ratios[(ratios.length - 1) / 2]
        : (ratios[ratios.length / 2 - 1] + ratios[ratios.length / 2]) / 2;
    return { factor: Math.min(1.6, Math.max(0.7, mid)), n: ratios.length, known: true };
}
/* The estimate as the lifter will actually experience it. Callers that show a time to a human use
   this; the generator keeps using estimateMinutes, for the reason above. */
/* ═══ ONE PRICE FOR A DAY ═══════════════════════════════════════════════════════════════════════
 * Two estimators used to price the same day: `estimateMinutes` (what the generator budgets against
 * its ceiling — no transitions) and `estimateMinutesFor` (what the plan shows — with transitions,
 * times the lifter's pace). MEASURED at v627 across 2,880 generated days: the generator believed 96
 * exceeded their ceiling; the number on screen exceeded it on 858 (30%), mean gap 7.1 minutes. A
 * forty-minute plan that shows 47 is the lifter's problem to solve, and they solve it by rushing
 * rests or dropping the tail — both of which the progression model then misreads.
 * Engine 15 prices a day ONCE, here: transitions are part of the budget the generator keeps, so the
 * ceiling it fits to is the number the lifter sees. Programs minted earlier keep their old budget
 * (the frozen-replay contract) and their display keeps adding transitions on top, as it always did.
 * Pace is display-only either way and is measured against the same `estimateMinutes` that is stored
 * as `estMin` at session finish, so it never double-counts what the budget already carries. */
/* ⚠ ADDED WORK IS REPORTED, NOT ABSORBED. `daySeconds` is the one price both the generator and the
   session screen read, so an exercise the lifter appended used to vanish into the same number as the
   plan — the session silently got longer and nothing said so, and the day then read as over its
   budget through no fault of the plan. `addedSeconds` splits the same arithmetic by ownership so a
   caller can say "your plan is 58 minutes, plus 6 you added" instead of one figure that is neither.
   Deliberately NOT subtracted from the total: the session really does take that long, and hiding it
   would be the opposite mistake. */
function addedSeconds(program, day, weekIndex, withTransitions) {
    let sec = 0;
    (day.exercises || []).forEach((id, slot) => {
        const ex = EX_BY_ID[id];
        if (!ex)
            return;
        if (!program.overrides?.[`${day.id}:${slot}`]?.added)
            return;
        sec += exerciseSlotSec(ex, {
            sets: Number(computeCell(program, day, id, slot, weekIndex).sets) || 0,
            goal: goalForDay(program, day), isPrimary: slot === day.primaryIndex,
            linked: !!program.ss?.[`${day.id}:${s
```

## setsDone hit 3

offset: 974880

```js
r quietly leaving the
 * last two exercises every session has a program that is too long, and the app currently reads them
 * as a lifter doing less volume than they should and adds more.
 *
 * ONLY SESSIONS THAT WERE GENUINELY ATTEMPTED. One abandoned after two sets says something about
 * that day, not about the plan; counted, a single bad afternoon drags the average enough to trigger
 * advice. The 0.25 floor and the requirement for real set counts mirror paceFactor's own guards,
 * which exist for exactly the same reason.
 *
 * NEEDS ENOUGH SESSIONS TO MEAN ANYTHING, and says so rather than guessing: `known` false tells the
 * caller to stay quiet, the same contract paceFactor uses. Six is two weeks of a three-day week —
 * below that a couple of rushed evenings look identical to a systematic problem. */
function completionRate(history = []) {
    const rates = [];
    let abandoned = 0, counted = 0;
    for (const h of history || []) {
        const done = Number(h.setsDone) || 0, total = Number(h.totalSets) || 0;
        if (total < 3)
            continue; // too small to read anything from
        counted++;
        const r = done / total;
        if (r < 0.25) {
            abandoned++;
            continue;
        } // an abandoned session, not a short one
        rates.push(Math.min(1, r));
    }
    if (rates.length < 6)
        return { rate: 1, n: rates.length, abandoned, counted, known: false, short: false };
    rates.sort((a, b) => a - b);
    const mid = rates.length % 2 ? rates[(rates.length - 1) / 2]
        : (rates[rates.length / 2 - 1] + rates[rates.length / 2]) / 2;
    /* MEDIAN, not mean: one session cut short by a fire alarm should not move the reading. Same reason
       paceFactor takes a median of its ratios. */
    return {
        rate: mid, n: rates.length, abandoned, counted, known: true,
        /* The actionable threshold. Leaving roughly one set in eight undone, session after session, is a
           plan that does not fit the lifter's day — not a lifter who lacks discipline, and the app should
           offer to shorten the session rather than quietly prescribe more volume to make up for it. */
        short: mid < 0.88
    };
}
function paceFactor(history = [], saved = []) {
    const byId = new Map(saved.map(p => [p.id, p]));
    const ratios = [];
    for (const h of history) {
        const p = byId.get(h.programId);
        const est = h.estMin || (p && p.days ? (() => {
            const d = p.days.find(x => x.id === h.dayId);
            try {
                return d ? estimateMinutes(p, d, h.weekIndex || 1) : 0;
            }
            catch {
                return 0;
            }
        })() : 0);
        const actual = Number(h.durationMin) || 0;
        /* Only sessions that were actually completed and plausibly timed: a session abandoned after two
           sets, or one whose clock ran overnight, says nothing about pace. */
        if (est >= 15 && actual >= 15 && actual <= est * 3 && (h.setsDone || 0) >= (h.totalSets || 1) * 0.6) {
            ratios.push(actual / est);
        }
    }
    if (ratios.length < 4)
        return { factor: 1, n: ratios.length, known: false };
    ratios.sort((a, b) => a - b);
    const mid = ratios.length % 2 ? ratios[(ratios.length - 1) / 2]
        : (ratios[ratios.length / 2 - 1] + ratios[ratios.length / 2]) / 2;
    return { factor: Math.min(1.6, Math.max(0.7, mid)), n: ratios.length, known: true };
}
/* The estimate as the lifter will actually experience it. Callers that show a time to a human use
   this; the generator keeps using estimateMinutes, for the reason above. */
/* ═══ ONE PRICE FOR A DAY ═══════════════════════════════════════════════════════════════════════
 * Two estimators used to price the same day: `estimateMinutes` (what the generator budgets against
 * its ceiling — no transitions) and `estimateMinutesFor` (what the plan shows — with transitions,
 * times the lifter's pace). MEASURED at v627 across 2,880 generated days: the generator believed 96
 * exceeded their ceiling; the number on screen exceeded it on 858 (30%), mean gap 7.1 minutes. A
 * forty-minute plan that shows 47 is the lifter's problem to solve, and they solve it by rushing
 * rests or dropping the tail — both of which the progression model then misreads.
 * Engine 15 prices a day ONCE, here: transitions are part of the budget the generator keeps, so the
 * ceiling it fits to is the number the lifter sees. Programs minted earlier keep their old budget
 * (the frozen-replay contract) and their display keeps adding transitions on top, as it always did.
 * Pace is display-only either way and is measured against the same `estimateMinutes` that is stored
 * as `estMin` at session finish, so it never double-counts what the budget already carries. */
/* ⚠ ADDED WORK IS REPORTED, NOT ABSORBED. `daySeconds` is the one price both the generator and the
   session screen read, so an exercise the lifter appended used to vanish into the same number as the
   plan — the session silently got longer and nothing said so, and the day then read as over its
   budget through no fault of the plan. `addedSeconds` splits the same arithmetic by ownership so a
   caller can say "your plan is 58 minutes, plus 6 you added" instead of one figure that is neither.
   Deliberately NOT subtracted from the total: the session really does take that long, and hiding it
   would be the opposite mistake. */
function addedSeconds(program, day, weekIndex, withTransitions) {
    let sec = 0;
    (day.exercises || []).forEach((id, slot) => {
        const ex = EX_BY_ID[id];
        if (!ex)
            return;
        if (!program.overrides?.[`${day.id}:${slot}`]?.added)
            return;
        sec += exerciseSlotSec(ex, {
            sets: Number(computeCell(program, day, id, slot, weekIndex).sets) || 0,
            goal: goalForDay(program, day), isPrimary: slot === day.primaryIndex,
            linked: !!program.ss?.[`${day.id}:${slot}`],
            restCustom: program.overrides?.[`${day.id}:${slot}`]?.rest,
            withTransitions
        });
    });
    return sec;
}
function addedMinutes(program, day, weekIndex) { return Math.round(addedSeconds(program, day, weekIndex, true) / 60); }
function daySeconds(program, day, weekIndex, withTransitions) {
    let sec = 0;
    (day.exercises || []).forEach((_id, slot) => {
        const id = exerciseAt(program, day, slot, weekIndex);
        const ex = EX_BY_ID[id];
        if (!ex)
            return;
        sec += exerciseSlotSec(ex, {
            sets: Number(computeCell(program, day, id, slot, weekIndex).sets) || 0,
            goal: goalForDay(program, day), isPrimary: slot === day.primaryIndex,
            linked: !!program.ss?.[`${day.id}:${slot}`],
            restCustom: program.overrides?.[`${day.id}:${slot}`]?.rest,
            withTransitions
        });
    });
    return sec;
}
const budgetIncludesTransitions = (program) => engHas(program.engineV || 1, "honestBudget");
function estimateMinutesFor(program, day, weekIndex, pace) {
    return Math.round((daySeconds(program, day, weekIndex, true) / 60) * ((pace && pace.factor) || 1));
}
/* estimateMinutes — the day price the generator holds a session to. History that still applies to
   the pricing inside exerciseSlotSec (warm-up allowance from the ramp, rest scaled once, per-slot
   overrides winning outright) lives in the comment that follows; the arithmetic itself is daySeconds. */
/* Warm-up allowance derived from the RAMP, not a flat constant per type.
 *
 * This was 165s for any compound and 75s for any isolation — roughly right when every compound got
 * two warm-up sets. v470 made ramp depth scale with load, so a heavy primary now gets four, and
 * this number did not move with it. Measured: a 495 lb squat's ramp is four sets, about 360s of
 * work and rest, against a 165s allowance; across a 4-day upper/lower that left 9-15 minutes per
 * session unaccounted. Session length is a HARD CONST
```

## setsDone hit 4

offset: 1075200

```js
as grinding all of them out,
               and max() reported them identically — so the banner would tell someone they took "most of"
               a session to failure when three quarters of it was on plan. The sentence has to be true. */
            overW[ex.part] = (overW[ex.part] || 0) + overSum;
            overN[ex.part] = (overN[ex.part] || 0) + overN_;
            prescW[ex.part] = (prescW[ex.part] || 0) + prescSum;
            prescN[ex.part] = (prescN[ex.part] || 0) + overN_;
            /* ⚠ DELIBERATELY UNTHREADED. This is muscleRecoveryUncached — FATIGUE, not growth. The
               hamstrings genuinely work during a squat (25-50% MVIC co-contraction) and so accrue recovery
               debt, even though the same squat produces no measurable hypertrophy (Kubo, Plotkin). Growth
               credit and fatigue credit are different questions and the kneeFlexedHams correction applies
               only to the first. */
            secondaryOf(ex).forEach(([pp, f]) => { sess[pp] = (sess[pp] || 0) + n * f; });
        });
        Object.entries(sess).forEach(([part, sets]) => { if (!last[part])
            last[part] = { date: h.date, sets, overBy: overN[part] ? (overW[part] / overN[part]) : 0, plannedRIR: prescN[part] ? (prescW[part] / prescN[part]) : null }; });
    });
    return PART_ORDER.map(part => {
        const l = last[part];
        if (!l)
            return { part, readiness: 100, daysSince: null, status: "fresh", sets: 0 };
        const hours = (now - l.date) / 3600000;
        const clockHours = 30 + clamp(l.sets, 0, 28) * 2.3; // on-plan training recovers in ~2 days; overreach (higher cost) extends it
        /* `measured` records WHERE the recovery estimate came from. It matters downstream: a clock
           estimate is a population average and has no idea what frequency this program was designed
           around, while a measured one is this lifter's own demonstrated dip on short rest. Only the
           second is evidence worth overruling a plan with. */
        const personal = personalRecoveryHours(history, part, clockHours);
        const recoveryHours = personal ?? clockHours; // lifter's measured recovery when history earns it, else the clock model
        const readiness = Math.round(clamp(hours / recoveryHours, 0, 1) * 100);
        return { part, readiness, hoursSince: hours, measured: personal != null, overBy: l.overBy || 0, plannedRIR: l.plannedRIR != null ? l.plannedRIR : null, daysSince: Math.floor(hours / 24), status: readiness >= 85 ? "fresh" : readiness >= 55 ? "recovering" : "fatigued", sets: l.sets };
    });
}
// 7-day training recap vs the prior 7 days
function weeklyRecap(history) {
    const now = Date.now(), wk = 7 * 86400000;
    const inWin = (h, a, b) => h.date > now - a && h.date <= now - b;
    const thisW = (history || []).filter(h => h && h.date > now - wk);
    const prevW = (history || []).filter(h => inWin(h, 2 * wk, wk));
    const sets = arr => arr.reduce((s, h) => s + (h.setsDone || 0), 0);
    const vol = arr => arr.reduce((s, h) => s + (h.volume || 0), 0);
    const muscle = {};
    // Working sets, not logged rows: the weekly recap names a "top muscle", and a session with myo
    // minis on one lift used to hand that title to whatever muscle happened to run extensions.
    thisW.forEach(h => Object.entries(h.perf || {}).forEach(([id, p]) => { const ex = EX_BY_ID[id]; if (!ex || !p.sets)
        return; muscle[ex.part] = (muscle[ex.part] || 0) + setsOf(p).filter(isWorkSet).length; }));
    const topMuscle = Object.entries(muscle).sort((a, b) => b[1] - a[1])[0] || null;
    const byId = {};
    (history || []).forEach(h => Object.entries(h.perf || {}).forEach(([id, p]) => {
        if (!(p && p.weight > 0))
            return;
        const ss = setsOf(p);
        const best = ss.length ? Math.max(...ss.map(s => e1rm(s.w, s.r || 1))) : e1rm(p.weight, p.reps || 1);
        (byId[id] = byId[id] || []).push({ date: h.date, best });
    }));
    let prs = 0;
    Object.values(byId).forEach(arr => {
        const prior = arr.filter(x => x.date <= now - wk).map(x => x.best);
        const cur = arr.filter(x => x.date > now - wk).map(x => x.best);
        if (cur.length && prior.length && Math.max(...cur) > Math.max(...prior))
            prs++;
    });
    return { count: thisW.length, sets: sets(thisW), vol: Math.round(vol(thisW)), prevCount: prevW.length, prevSets: sets(prevW), prevVol: Math.round(vol(prevW)), topMuscle, prs };
}
// Memoized on program identity + week. (program, weekIndex) fully determines the result — computeCell
// reads only program-borne inputs (slotBias/autoBias/rounds/overrides/config), and every program edit
// replaces the object immutably, so identity is a sound, staleness-free key (same pattern as the
// muscleRecovery / lifterModel memos). Every current caller reads the map without mutating it, which is
// what lets the cached object be returned directly. This is what makes it cheap for the session engine's
// per-accessory readiness gate to consult weekly volume without re-scanning the whole program each time.
const __weeklyVolMemo = new WeakMap(); // program -> Map(cacheKey -> volMap)
/* The memo is keyed on a STRUCTURAL SIGNATURE, not on the program object alone.
 *
 * It used to be keyed by object identity plus week index, and programs are mutated in place during
 * generation — coverage passes append exercises, the delt floor pass writes slotBias, fitSessionTime
 * trims slots. Every one of those changes the volume, and every one of them left the cached answer
 * in place. Anything that mutated and then re-read got the number from BEFORE its own change.
 *
 * That is not a theoretical staleness. Building a part-level MEV floor on top of it, the repair loop
 * added a calf movement, re-read the volume, saw the identical number, concluded it had not worked,
 * and added another — eight calf raises into one program before the loop ran out. The pass looked
 * broken; the cache was lying to it. The existing delt sub-head pass reads volume the same way after
 * writing slotBias, so it was reading stale numbers too and quietly under-filling.
 *
 * The signature covers exactly what the computation depends on: the exercise ids in slot order, and
 * the slot biases. Both are cheap to stringify relative to walking every cell, and any mutation to
 * either produces a different key, so the cache invalidates itself instead of needing every mutation
 * site to remember to clear it — which is the discipline that failed here in the first place. */
function __volKey(program, weekIndex) {
    const days = (program.days || []).map(d => (d.exercises || []).join(",")).join("|");
    const bias = program.slotBias ? JSON.stringify(program.slotBias) : "";
    /* ⚠ ANYTHING THAT CHANGES A SET COUNT BELONGS IN THIS KEY. The memo is keyed on exercise ids and
       slotBias; `weekOff` changes what `computeCell` returns and was invisible to it, so a slot turned
       off for a week still reported its old volume — the exact "repair pass reads stale numbers" trap
       this key already carries a warning about, hit again by a new input. */
    const off = program.weekOff ? JSON.stringify(program.weekOff) : "";
    const pair = program.pairs ? JSON.stringify(program.pairs) : "";
    return `${weekIndex}::${days}::${bias}::${off}::${pair}`;
}
/* ---- PROGRAM SETUP SUMMARY --------------------------------------------------------------------
 *
 * Everything the lifter answered in the wizard is stored on program.config — split, days, session
 * length, goal, experience, progression, block length, deload, and crucially the emphasis (`focus`)
 * and de-emphasis (`reduce`) choices. None of it was ever shown back to them. The program screen
 * displayed the days it produced and the library card showed "PPL · 6 days · Strength & Size", so a
 * lifter who asked for extra shoulder work and less calf work had no way to confirm the program knew
 * that, and no way to remember months la
```

## setsDone hit 5

offset: 1938939

```js
&& kind !== "fatigue") {
        const floor = ctx.floor, vol = ctx.volume;
        if (floor > 0 && vol != null && vol < floor - 0.5)
            kind = "volume";
        else if (Array.isArray(ctx.siblingsProgressing) && ctx.siblingsProgressing.length >= 2)
            kind = "exercise";
    }
    const advice = kind === "volume"
        ? `No PR in ${base.since} sessions, and ${ctx.partLabel || "this muscle"} is getting ${Math.round(ctx.volume)} sets a week against a target of ${Math.round(ctx.floor)}. That's under-training, not a ceiling — add volume before you deload.`
        : kind === "exercise"
            ? `No PR in ${base.since} sessions, but your other ${ctx.partLabel || "work for this muscle"} is still climbing. The muscle is fine; this movement has gone stale. Swap it for a variation.`
            : kind === "fatigue"
                ? `Reps are slipping and effort is climbing over ${base.since} sessions — that's fatigue, not a strength ceiling. Deload this lift, then rebuild.`
                : kind === "true"
                    ? base.advice
                    : `No PR in ${base.since} sessions, but your numbers are just noisy rather than trending down. Hold the course — no change needed yet.`;
    return { ...base, kind, advice };
}
// --- the model ------------------------------------------------------------------------------
// Memoized: every screen and the session engine ask for this repeatedly, and it's an O(history)
// scan. The key is a content fingerprint (length + newest entry), NOT array identity: identity alone
// silently returns a stale model to any caller that mutates history in place, which is the kind of
// bug that produces confidently wrong prescriptions with no error anywhere. Cheap to compute, and
// it can't go stale — a new session always changes the length and the newest id.
let _lmCache = { key: null, val: null };
/* A CONTENT FINGERPRINT OF THE WHOLE HISTORY — not just its length and newest entry.
 *
 * This keys the memo for BOTH buildLifterModel and dayScopedTrends, so whatever it fails to notice,
 * both caches keep serving a stale answer for. It used to be `length : hs[0].id : hs[0].date`, which
 * describes only how many sessions there are and which one is newest — and `editHistoryEntry` changes
 * NONE of those. Correct a weight you fat-fingered in a session from three weeks ago and the length is
 * unchanged, the newest entry is unchanged, so the key is unchanged and the model is served from cache
 * with the old numbers still in it. Measured: editing the oldest of six sessions returned the
 * byte-identical cached object (m1 === m2) reporting a 5-session plateau where the truth was 4.
 * Plateau detection, effort calibration, readiness and every coach fact read that model.
 *
 * The comment this replaces already said "content fingerprint, not array identity" — the intent was
 * right and the implementation only ever fingerprinted the array's shape. So fingerprint the content:

 * `volume` and `setsDone` are recomputed by editHistoryEntry on every edit, so they move whenever a
 * session's contents do, and id+date still distinguish sessions from each other. djb2 over a capped
 * history is a few thousand character ops behind a memo that saves far more than that. */
function lifterModelKey(hs) {
    let h = 5381;
    const n = hs.length;
    for (let i = 0; i < n; i++) {
        const e = hs[i];
        if (!e)
            continue;
        /* Editing history must invalidate every model that consumes it. Volume/setsDone alone miss
           RIR-only corrections, set-order corrections and a changed load/reps pair whose tonnage happens
           to stay equal. Fingerprint the performed-set evidence itself, including target-vs-observed RIR
           provenance, so the coach/readiness/progression views cannot keep serving the pre-edit model. */
        let sig = `${e.id}|${e.date}|${e.volume}|${e.setsDone}`;
        const perf = e.perf || {};
        for (const exId of Object.keys(perf).sort()) {
            const p = perf[exId] || {};
            sig += `|${exId}:${p.weight ?? ""}:${p.reps ?? ""}`;
            for (const st of (Array.isArray(p.sets) ? p.sets : []))
                sig += `;${st?.w ?? ""},${st?.r ?? ""},${st?.rir ?? ""},${st?.tr ?? ""},${st?.sub ? 1 : 0}`;
        }
        for (let j = 0; j < sig.length; j++)
            h = ((h * 33) ^ sig.charCodeAt(j)) >>> 0;
    }
    return `${n}:${h}`;
}
/* `program` is OPTIONAL and affects only the two off-lift plateau causes (volume-limited and
   exercise-specific), which need to know what a muscle's weekly volume is against its floor. Every
   existing caller keeps its two-argument form and gets exactly the model it always got.
   ⚠ IT IS PART OF THE MEMO KEY. The cache is content-fingerprinted precisely because identity alone
   silently serves a stale model (v531), and a program passed in but left out of the key would do the
   same thing one level up: switch programs, get the previous program's volume floors, and read a
   plateau cause computed against a plan you are no longer running. */
function buildLifterModel(history, perf, program) {
    const hs = history || [];
    const key = lifterModelKey(hs) + "|" + (program ? `${program.id}:${program.updatedAt || ""}:${program.weeks || ""}` : "-");
    if (_lmCache.key === key && _lmCache.val)
        return _lmCache.val;
    const effort = effortCalibration(hs);
    const trends = exerciseTrends(hs);
    const recovery = muscleRecovery(hs);
    // per-session summaries per lift, oldest→newest, for slope + plateau classification.
    // perLiftDay carries the SAME summaries split by the day they were logged on — plateau is judged
    // per day (see the plateau assembly below), because a lift trained on two days is really two
    // progressions and merging them invents stalls that neither day has.
    const perLift = {};
    const perLiftDay = {};
    for (let i = hs.length - 1; i >= 0; i--) { // history is newest-first
        const h = hs[i];
        for (const [id, p] of Object.entries(h.perf || {})) {
            const ss = Array.isArray(p.sets) && p.sets.length ? p.sets : [{ w: parseFloat(p.weight), r: parseInt(p.reps), rir: null }];
            let best = 0, bestR = 0, bestRir = null;
            for (const s of ss) {
                const w = parseFloat(s.w != null ? s.w : s.weight), r = parseInt(s.r != null ? s.r : s.reps);
                if (!(w > 0) || !(r > 0))
                    continue;
                const rirRaw = s.rir != null ? s.rir : 2;
                const rirAdj = clamp(rirRaw - effort.bias, 0, 6); // interpret through the calibration
                const e = e1rmRIR(w, r, rirAdj);
                if (e > best) {
                    best = e;
                    bestR = r;
                    bestRir = s.rir != null ? s.rir : null;
                }
            }
            if (best > 0) {
                const rec = { e1rm: best, r: bestR, rir: bestRir, date: h.date };
                (perLift[id] = perLift[id] || []).push(rec);
                if (h.dayId)
                    ((perLiftDay[id] = perLiftDay[id] || {})[h.dayId] = perLiftDay[id][h.dayId] || []).push(rec);
            }
        }
    }
    const muscles = {};
    for (const m of recovery) {
        muscles[m.part] = { readiness: m.readiness, status: m.status, daysSince: m.daysSince, hoursSince: m.hoursSince, measured: m.measured, overBy: m.overBy, plannedRIR: m.plannedRIR, sets: m.sets, ...readinessBand(m.readiness, m) };
    }
    const lifts = {};
    const trendById = Object.fromEntries(trends.map(t => [t.id, t]));
    /* A lift trained on more than one day is more than one progression. See plateauSplitByDay() — the
       merged e1RM series interleaves them, and the day that structurally CANNOT set a PR (the lighter,
       higher-rep slot of a lift that also has a heavy slot) pads plateauOf()'s "sessions since PR"
       counter on every appearance. Judge each day on its own series, and keep
```

## setsDone hit 6

offset: 1939465

```js
aining, not a ceiling — add volume before you deload.`
        : kind === "exercise"
            ? `No PR in ${base.since} sessions, but your other ${ctx.partLabel || "work for this muscle"} is still climbing. The muscle is fine; this movement has gone stale. Swap it for a variation.`
            : kind === "fatigue"
                ? `Reps are slipping and effort is climbing over ${base.since} sessions — that's fatigue, not a strength ceiling. Deload this lift, then rebuild.`
                : kind === "true"
                    ? base.advice
                    : `No PR in ${base.since} sessions, but your numbers are just noisy rather than trending down. Hold the course — no change needed yet.`;
    return { ...base, kind, advice };
}
// --- the model ------------------------------------------------------------------------------
// Memoized: every screen and the session engine ask for this repeatedly, and it's an O(history)
// scan. The key is a content fingerprint (length + newest entry), NOT array identity: identity alone
// silently returns a stale model to any caller that mutates history in place, which is the kind of
// bug that produces confidently wrong prescriptions with no error anywhere. Cheap to compute, and
// it can't go stale — a new session always changes the length and the newest id.
let _lmCache = { key: null, val: null };
/* A CONTENT FINGERPRINT OF THE WHOLE HISTORY — not just its length and newest entry.
 *
 * This keys the memo for BOTH buildLifterModel and dayScopedTrends, so whatever it fails to notice,
 * both caches keep serving a stale answer for. It used to be `length : hs[0].id : hs[0].date`, which
 * describes only how many sessions there are and which one is newest — and `editHistoryEntry` changes
 * NONE of those. Correct a weight you fat-fingered in a session from three weeks ago and the length is
 * unchanged, the newest entry is unchanged, so the key is unchanged and the model is served from cache
 * with the old numbers still in it. Measured: editing the oldest of six sessions returned the
 * byte-identical cached object (m1 === m2) reporting a 5-session plateau where the truth was 4.
 * Plateau detection, effort calibration, readiness and every coach fact read that model.
 *
 * The comment this replaces already said "content fingerprint, not array identity" — the intent was
 * right and the implementation only ever fingerprinted the array's shape. So fingerprint the content:

 * `volume` and `setsDone` are recomputed by editHistoryEntry on every edit, so they move whenever a
 * session's contents do, and id+date still distinguish sessions from each other. djb2 over a capped
 * history is a few thousand character ops behind a memo that saves far more than that. */
function lifterModelKey(hs) {
    let h = 5381;
    const n = hs.length;
    for (let i = 0; i < n; i++) {
        const e = hs[i];
        if (!e)
            continue;
        /* Editing history must invalidate every model that consumes it. Volume/setsDone alone miss
           RIR-only corrections, set-order corrections and a changed load/reps pair whose tonnage happens
           to stay equal. Fingerprint the performed-set evidence itself, including target-vs-observed RIR
           provenance, so the coach/readiness/progression views cannot keep serving the pre-edit model. */
        let sig = `${e.id}|${e.date}|${e.volume}|${e.setsDone}`;
        const perf = e.perf || {};
        for (const exId of Object.keys(perf).sort()) {
            const p = perf[exId] || {};
            sig += `|${exId}:${p.weight ?? ""}:${p.reps ?? ""}`;
            for (const st of (Array.isArray(p.sets) ? p.sets : []))
                sig += `;${st?.w ?? ""},${st?.r ?? ""},${st?.rir ?? ""},${st?.tr ?? ""},${st?.sub ? 1 : 0}`;
        }
        for (let j = 0; j < sig.length; j++)
            h = ((h * 33) ^ sig.charCodeAt(j)) >>> 0;
    }
    return `${n}:${h}`;
}
/* `program` is OPTIONAL and affects only the two off-lift plateau causes (volume-limited and
   exercise-specific), which need to know what a muscle's weekly volume is against its floor. Every
   existing caller keeps its two-argument form and gets exactly the model it always got.
   ⚠ IT IS PART OF THE MEMO KEY. The cache is content-fingerprinted precisely because identity alone
   silently serves a stale model (v531), and a program passed in but left out of the key would do the
   same thing one level up: switch programs, get the previous program's volume floors, and read a
   plateau cause computed against a plan you are no longer running. */
function buildLifterModel(history, perf, program) {
    const hs = history || [];
    const key = lifterModelKey(hs) + "|" + (program ? `${program.id}:${program.updatedAt || ""}:${program.weeks || ""}` : "-");
    if (_lmCache.key === key && _lmCache.val)
        return _lmCache.val;
    const effort = effortCalibration(hs);
    const trends = exerciseTrends(hs);
    const recovery = muscleRecovery(hs);
    // per-session summaries per lift, oldest→newest, for slope + plateau classification.
    // perLiftDay carries the SAME summaries split by the day they were logged on — plateau is judged
    // per day (see the plateau assembly below), because a lift trained on two days is really two
    // progressions and merging them invents stalls that neither day has.
    const perLift = {};
    const perLiftDay = {};
    for (let i = hs.length - 1; i >= 0; i--) { // history is newest-first
        const h = hs[i];
        for (const [id, p] of Object.entries(h.perf || {})) {
            const ss = Array.isArray(p.sets) && p.sets.length ? p.sets : [{ w: parseFloat(p.weight), r: parseInt(p.reps), rir: null }];
            let best = 0, bestR = 0, bestRir = null;
            for (const s of ss) {
                const w = parseFloat(s.w != null ? s.w : s.weight), r = parseInt(s.r != null ? s.r : s.reps);
                if (!(w > 0) || !(r > 0))
                    continue;
                const rirRaw = s.rir != null ? s.rir : 2;
                const rirAdj = clamp(rirRaw - effort.bias, 0, 6); // interpret through the calibration
                const e = e1rmRIR(w, r, rirAdj);
                if (e > best) {
                    best = e;
                    bestR = r;
                    bestRir = s.rir != null ? s.rir : null;
                }
            }
            if (best > 0) {
                const rec = { e1rm: best, r: bestR, rir: bestRir, date: h.date };
                (perLift[id] = perLift[id] || []).push(rec);
                if (h.dayId)
                    ((perLiftDay[id] = perLiftDay[id] || {})[h.dayId] = perLiftDay[id][h.dayId] || []).push(rec);
            }
        }
    }
    const muscles = {};
    for (const m of recovery) {
        muscles[m.part] = { readiness: m.readiness, status: m.status, daysSince: m.daysSince, hoursSince: m.hoursSince, measured: m.measured, overBy: m.overBy, plannedRIR: m.plannedRIR, sets: m.sets, ...readinessBand(m.readiness, m) };
    }
    const lifts = {};
    const trendById = Object.fromEntries(trends.map(t => [t.id, t]));
    /* A lift trained on more than one day is more than one progression. See plateauSplitByDay() — the
       merged e1RM series interleaves them, and the day that structurally CANNOT set a PR (the lighter,
       higher-rep slot of a lift that also has a heavy slot) pads plateauOf()'s "sessions since PR"
       counter on every appearance. Judge each day on its own series, and keep the per-day summaries
       aligned with the per-day trends so classifyPlateau sees one day's reps/RIR, not two days' mixed. */
    /* ── CONTEXT FOR THE TWO OFF-LIFT PLATEAU CAUSES ────────────────────────────────────────────
     * Built once per model rather than per lift: `volumeLedger` walks the whole program, and calling it
     * inside a loop over every lift would turn an O(history) model build into an O(history x lifts) one.
     * Silently absent when there is no active program to read a floor from — classify
```

## setsDone hit 7

offset: 1939857

```js
ince} sessions — that's fatigue, not a strength ceiling. Deload this lift, then rebuild.`
                : kind === "true"
                    ? base.advice
                    : `No PR in ${base.since} sessions, but your numbers are just noisy rather than trending down. Hold the course — no change needed yet.`;
    return { ...base, kind, advice };
}
// --- the model ------------------------------------------------------------------------------
// Memoized: every screen and the session engine ask for this repeatedly, and it's an O(history)
// scan. The key is a content fingerprint (length + newest entry), NOT array identity: identity alone
// silently returns a stale model to any caller that mutates history in place, which is the kind of
// bug that produces confidently wrong prescriptions with no error anywhere. Cheap to compute, and
// it can't go stale — a new session always changes the length and the newest id.
let _lmCache = { key: null, val: null };
/* A CONTENT FINGERPRINT OF THE WHOLE HISTORY — not just its length and newest entry.
 *
 * This keys the memo for BOTH buildLifterModel and dayScopedTrends, so whatever it fails to notice,
 * both caches keep serving a stale answer for. It used to be `length : hs[0].id : hs[0].date`, which
 * describes only how many sessions there are and which one is newest — and `editHistoryEntry` changes
 * NONE of those. Correct a weight you fat-fingered in a session from three weeks ago and the length is
 * unchanged, the newest entry is unchanged, so the key is unchanged and the model is served from cache
 * with the old numbers still in it. Measured: editing the oldest of six sessions returned the
 * byte-identical cached object (m1 === m2) reporting a 5-session plateau where the truth was 4.
 * Plateau detection, effort calibration, readiness and every coach fact read that model.
 *
 * The comment this replaces already said "content fingerprint, not array identity" — the intent was
 * right and the implementation only ever fingerprinted the array's shape. So fingerprint the content:

 * `volume` and `setsDone` are recomputed by editHistoryEntry on every edit, so they move whenever a
 * session's contents do, and id+date still distinguish sessions from each other. djb2 over a capped
 * history is a few thousand character ops behind a memo that saves far more than that. */
function lifterModelKey(hs) {
    let h = 5381;
    const n = hs.length;
    for (let i = 0; i < n; i++) {
        const e = hs[i];
        if (!e)
            continue;
        /* Editing history must invalidate every model that consumes it. Volume/setsDone alone miss
           RIR-only corrections, set-order corrections and a changed load/reps pair whose tonnage happens
           to stay equal. Fingerprint the performed-set evidence itself, including target-vs-observed RIR
           provenance, so the coach/readiness/progression views cannot keep serving the pre-edit model. */
        let sig = `${e.id}|${e.date}|${e.volume}|${e.setsDone}`;
        const perf = e.perf || {};
        for (const exId of Object.keys(perf).sort()) {
            const p = perf[exId] || {};
            sig += `|${exId}:${p.weight ?? ""}:${p.reps ?? ""}`;
            for (const st of (Array.isArray(p.sets) ? p.sets : []))
                sig += `;${st?.w ?? ""},${st?.r ?? ""},${st?.rir ?? ""},${st?.tr ?? ""},${st?.sub ? 1 : 0}`;
        }
        for (let j = 0; j < sig.length; j++)
            h = ((h * 33) ^ sig.charCodeAt(j)) >>> 0;
    }
    return `${n}:${h}`;
}
/* `program` is OPTIONAL and affects only the two off-lift plateau causes (volume-limited and
   exercise-specific), which need to know what a muscle's weekly volume is against its floor. Every
   existing caller keeps its two-argument form and gets exactly the model it always got.
   ⚠ IT IS PART OF THE MEMO KEY. The cache is content-fingerprinted precisely because identity alone
   silently serves a stale model (v531), and a program passed in but left out of the key would do the
   same thing one level up: switch programs, get the previous program's volume floors, and read a
   plateau cause computed against a plan you are no longer running. */
function buildLifterModel(history, perf, program) {
    const hs = history || [];
    const key = lifterModelKey(hs) + "|" + (program ? `${program.id}:${program.updatedAt || ""}:${program.weeks || ""}` : "-");
    if (_lmCache.key === key && _lmCache.val)
        return _lmCache.val;
    const effort = effortCalibration(hs);
    const trends = exerciseTrends(hs);
    const recovery = muscleRecovery(hs);
    // per-session summaries per lift, oldest→newest, for slope + plateau classification.
    // perLiftDay carries the SAME summaries split by the day they were logged on — plateau is judged
    // per day (see the plateau assembly below), because a lift trained on two days is really two
    // progressions and merging them invents stalls that neither day has.
    const perLift = {};
    const perLiftDay = {};
    for (let i = hs.length - 1; i >= 0; i--) { // history is newest-first
        const h = hs[i];
        for (const [id, p] of Object.entries(h.perf || {})) {
            const ss = Array.isArray(p.sets) && p.sets.length ? p.sets : [{ w: parseFloat(p.weight), r: parseInt(p.reps), rir: null }];
            let best = 0, bestR = 0, bestRir = null;
            for (const s of ss) {
                const w = parseFloat(s.w != null ? s.w : s.weight), r = parseInt(s.r != null ? s.r : s.reps);
                if (!(w > 0) || !(r > 0))
                    continue;
                const rirRaw = s.rir != null ? s.rir : 2;
                const rirAdj = clamp(rirRaw - effort.bias, 0, 6); // interpret through the calibration
                const e = e1rmRIR(w, r, rirAdj);
                if (e > best) {
                    best = e;
                    bestR = r;
                    bestRir = s.rir != null ? s.rir : null;
                }
            }
            if (best > 0) {
                const rec = { e1rm: best, r: bestR, rir: bestRir, date: h.date };
                (perLift[id] = perLift[id] || []).push(rec);
                if (h.dayId)
                    ((perLiftDay[id] = perLiftDay[id] || {})[h.dayId] = perLiftDay[id][h.dayId] || []).push(rec);
            }
        }
    }
    const muscles = {};
    for (const m of recovery) {
        muscles[m.part] = { readiness: m.readiness, status: m.status, daysSince: m.daysSince, hoursSince: m.hoursSince, measured: m.measured, overBy: m.overBy, plannedRIR: m.plannedRIR, sets: m.sets, ...readinessBand(m.readiness, m) };
    }
    const lifts = {};
    const trendById = Object.fromEntries(trends.map(t => [t.id, t]));
    /* A lift trained on more than one day is more than one progression. See plateauSplitByDay() — the
       merged e1RM series interleaves them, and the day that structurally CANNOT set a PR (the lighter,
       higher-rep slot of a lift that also has a heavy slot) pads plateauOf()'s "sessions since PR"
       counter on every appearance. Judge each day on its own series, and keep the per-day summaries
       aligned with the per-day trends so classifyPlateau sees one day's reps/RIR, not two days' mixed. */
    /* ── CONTEXT FOR THE TWO OFF-LIFT PLATEAU CAUSES ────────────────────────────────────────────
     * Built once per model rather than per lift: `volumeLedger` walks the whole program, and calling it
     * inside a loop over every lift would turn an O(history) model build into an O(history x lifts) one.
     * Silently absent when there is no active program to read a floor from — classifyPlateau then
     * returns exactly the three kinds it always did. */
    const ledger = program ? (() => { try {
        return volumeLedger(program, Math.max(0, weeksOf(program) - 2));
    }
    catch {
        return null;
    } })() : null;
    /* Which lifts for a given muscle are still MOVING. `trend.dir` is the app's own read of a lift's
       direction, so this asks the same quest
```

## setsDone hit 8

offset: 2010061

```js
f, unit, w, history);
                    sets = prescribeSets(program, day, ex, slot, w, unit, sug, perf, perf, history, false);
                }
                catch {
                    return;
                }
                const work = (sets || []).filter(isWorkSet);
                if (!work.length)
                    return;
                /* SEED A STARTING LOAD ON THE FIRST SESSION. Without this the simulation is VACUOUS and looks
                   clean: with no history the engine has nothing to progress from and prescribes a blank load,
                   the simulated lifter logs 0, and every later week reads that 0 as the last performance — so
                   the lifter never lifts anything, no warm-up ramp is ever built, and the audit sweeps a
                   program in which no weight was ever prescribed. It reported ZERO findings across 2,484
                   programs and 1.5 million sets while three separate injected defects walked straight past it.
                   Caught by mutation testing, which is the only reason it was caught at all. */
                const rawSeed = ex.equip.includes("barbell") || ex.equip.includes("ezbar") || ex.equip.includes("smith") ? 95
                    : ex.equip.includes("dumbbell") ? 30
                        : ex.equip.includes("machine") || ex.equip.includes("cable") ? 60
                            : ex.equip.length === 0 ? 0 : 45;
                /* THE SEED MUST ITSELF BE LOADABLE. The first version used a flat 45, which is not a multiple
                   of the 10 lb step on a hack squat or a leg press — so the harness fabricated a load no
                   lifter could set, the engine faithfully carried it forward, and the audit reported 40
                   "load-not-loadable" findings that were entirely its own doing. An auditor must not
                   manufacture inputs that violate the invariant it is checking; that is how a sweep produces
                   confident nonsense. Snapped to the exercise's own increment. */
                const seedStep = loadStep(ex, unit) || 0;
                const seed = seedStep > 0 ? Math.max(seedStep, Math.round(rawSeed / seedStep) * seedStep) : rawSeed;
                const wt = Number(work[0].weight) || (perf[id] ? Number(perf[id].weight) : 0) || seed;
                const target = Number(work[0].reps) || 8;
                const reps = behaviour === "miss" ? Math.max(1, target - 3) : behaviour === "stall" ? target : target;
                const useW = behaviour === "stall" ? (perf[id] ? Number(perf[id].weight) || wt : wt) : wt;
                logged[id] = { weight: useW, reps, sets: work.map(() => ({ w: useW, r: reps, rir: 2 })), date: Date.now() };
            });
            if (Object.keys(logged).length) {
                history.unshift({ id: `sim-${w}-${day.id}`, programId: program.id, dayId: day.id, dayLabel: day.label, weekIndex: w, date: Date.now() - (total - w) * 86400000, perf: logged, volume: 1000, setsDone: 3 });
                perf = { ...perf, ...logged };
            }
        });
    }
    return findings;
}
function WorkoutSession({ warmupCard = true, onSetWarmupCard, program, gymEquipment = null, day: rawDay, weekIndex, unit, setUnit, perf, onExit, onFinish, onSaveRoutine, onBan, banned = [], history = [], loadMode = "rir", onEditHistory, goals = {}, onSetGoal, restAutoStart = true, restScale = 1, exNotes = {}, onSetExNote, exSetup = {}, onSetExSetup, onSetExLoadInc, onUpdateProgramExercise, onSetRest }) {
    // Resilient to a removed/unknown exercise id lingering in a saved program: drop it cleanly.
    const day = useMemo(() => (rawDay.exercises.every(id => EX_BY_ID[id]) ? rawDay : { ...rawDay, exercises: rawDay.exercises.filter(id => EX_BY_ID[id]) }), [rawDay]);
    const [histEdit, setHistEdit] = useState(null); // { histId, exId, w, r }
    // Phone-width set rows stay single-line, but the fixed reference columns and controls compact
    // through 520 CSS px so Load/Reps keep usable width on common Android viewports. Keep this
    // breakpoint in sync with the release regression gate; lowering it reintroduces the clipped row.
    const narrowSet = useNarrow(520);
    const [goalEdit, setGoalEdit] = useState(null); // { exId, w }
    const trendById = useMemo(() => Object.fromEntries(exerciseTrends(history).map(t => [t.id, t])), [history]);
    // Per-occurrence performance: the previous weight for an exercise is taken from the most
    // recent session of THIS day, so e.g. bench done fresh on one day and pre-fatigued on
    // another each progress on their own loads. Falls back to global perf for never-here lifts.
    // Interpret past sets through the lifter's RIR calibration before anything reads them.
    // suggestWeight's e1RM sizing and the per-set DDP path BOTH read dayPerf, so correcting it once
    // here keeps them consistent by construction.
    const lifterModel = useMemo(() => buildLifterModel(history, perf, program), [history, perf, program]);
    const coachSource = useMemo(() => {
        const facts = coachFacts(lifterModel, day, history);
        const plan = workoutPlanCoachFact(program, day, weekIndex);
        return plan ? [...facts, { ...plan, key: "session-plan", scope: "session", priority: 20 }] : facts;
    }, [lifterModel, day, history, program, weekIndex]);
    const [coachExpand, setCoachExpand] = useState(false);
    const [techCueOpen, setTechCueOpen] = useState(null);
    /* The LAST TIME column must show LAST TIME — the most recent session of this day, RAW, exactly as
       logged. dayPerf is the progression ANCHOR, and which session that is now depends on the lift's
       style (see anchorPerfFor): best-of-the-last-3 for styles that prescribe one session weight, and
       strictly the last session for dynamic double progression, whose per-set math is meaningless
       against a session the lifter isn't looking at. Kept separate from the anchor regardless, because
       a column reporting what you did must not be re-interpreted by the RIR calibration the engine
       applies before reading effort. The two now share one implementation of "last session". */
    const lastPerf = useMemo(() => lastDayPerf(day, perf, history), [history, day, perf]);
    const dayPerf = useMemo(() => {
        return calibratedAnchorPerf(program, day, perf, history, weekIndex, lifterModel.effort);
    }, [history, day, perf, program, weekIndex, lifterModel]);
    /* ⚠ ASK WHAT THE SLOT HOLDS THIS WEEK — see the note in the audit loop above. */
    const suggestions = useMemo(() => day.exercises.map((_id, slot) => sessionSuggestion(program, day, slot, dayPerf, unit, weekIndex, history)), [day, program, dayPerf, unit, weekIndex]);
    const buildSets = (ex, slot, sug, withWarm) => prescribeSets(program, day, ex, slot, weekIndex, unit, sug, dayPerf, perf, history, withWarm);
    // Live-session autosave: an in-progress workout (logged sets, timing) is persisted to localStorage
    // on every change so an accidental close, reload, or PWA update never loses logged work. The data
    // useState initializer rehydrates from that snapshot when it matches this exact program/day/week.
    const liveKey = LIVE_KEY;
    const dayExSig = day.exercises.join("|");
    /* ⚠ RESUME IS A MOUNT-TIME QUESTION, AND THIS USED TO BE RE-ASKED ON EVERY RENDER — WITH A SIDE
       EFFECT. This was a bare IIFE in the component body, so it re-ran on every single render of a live
       workout, and its "stale" branch DELETES the resume snapshot. Any render where the day's exercise
       signature did not match the snapshot on disk destroyed the in-progress workout silently, mid-set.
       That is the mechanism behind "the minimize and restore keeps restarting my workouts": the snapshot
       was already gone before the lifter ever tapped resume, so there was nothing to come back to and
       the session rebuilt from scratch.
       The signature drift that triggered it is fixed at its own 
```

## history/perf/volume candidate 1

offset: 409204

```js
 He had edited nothing.
   `startUpNext` calls `autoregulate`, which returns a NEW program object carrying `autoBias` (the
   volume tweak recomputed from recent performance and recovery feedback) and `autoTuned` (the up/down
   summary the session briefly shows), then `setProgram` writes it into the OPEN copy while `saved[]`
   is left alone. This deep-compare then found two fields differing and reported unsaved changes.
   MEASURED, and it is these two and nothing else: driving a real session and leaving it, the diff
   between the stored program and the draft is exactly ['autoBias','autoTuned'].

   THEY ARE NOT EDITS AND CANNOT BE SAVED MEANINGFULLY. autoBias is ephemeral by design — the comment
   at its call site says so, it is cleared and rebuilt from scratch before every session precisely so
   it can never compound, so "Update" would only bake in a number the next session immediately
   recomputes. autoTuned is a one-session display note. Neither survives a session, so neither can be
   the thing an "unsaved changes" prompt is asking the lifter to decide about.

   THE GENERAL SHAPE, because this is the second time: a blanket stringify means ANY field the app
   later writes to the program at session time reintroduces the prompt. The list below is the seam —
   if you add a field the lifter did not author, it goes here. gates/draftfalse.mjs asserts the
   behaviour (start a session, leave, nothing is dirty) rather than these two names, so a third such
   field fails the gate instead of shipping. */
const sameProgramContent = (a, b) => {
    if (a === b)
        return true;
    if (!a || !b)
        return false;
    const strip = ({ updatedAt, autoBias, autoTuned, ...rest }) => rest;
    return JSON.stringify(strip(a)) === JSON.stringify(strip(b));
};
// Preview and commit share the same output; the UI never estimates sibling impact.
function programSavePlan(edited, saved, cycles, scope = "program") {
    const before = saved.find(p => p.id === edited?.id);
    if (!edited || !before)
        return [];
    let candidates = [edited];
    if (scope === "cycle" && edited.cycleId) {
        const siblings = saved.filter(p => p.id !== edited.id && p.cycleId === edited.cycleId);
        const adapt = !!cycles.find(c => c.id === edited.cycleId)?.adaptExercises;
        candidates = [...candidates, ...propagateCycleEditsPure(edited, before, siblings, adapt)];
    }
    return candidates.flatMap(after => {
        const prior = saved.find(p => p.id === after.id);
        return prior && !sameProgramContent(prior, after) ? [{ before: prior, after }] : [];
    });
}
function programChangeLabels(before, after) {
    const changed = key => JSON.stringify(before?.[key]) !== JSON.stringify(after?.[key]);
    const labels = [];
    if (changed("name"))
        labels.push("Program name");
    if (changed("days"))
        labels.push("Training days, exercises or order");
    if (["overrides", "slotBias", "nextWeekPrescriptions", "progStyle"].some(changed))
        labels.push("Set, rep or effort targets");
    if (changed("ss"))
        labels.push("Supersets");
    if (["schedule", "weekPlan", "scheduleBase"].some(changed))
        labels.push("Schedule");
    if (["config", "weeks", "nextEngine"].some(changed))
        labels.push("Program settings or generated plan");
    if (["folder", "description"].some(changed))
        labels.push("Folder or description");
    return labels.length ? labels : ["Program details"];
}
function canUndoProgramSave(transaction, saved, drafts, working) {
    return !!transaction?.changes?.length && transaction.changes.every(({ after }) => {
        const current = saved.find(p => p.id === after.id);
        return sameProgramContent(current, after)
            && (!drafts[after.id] || sameProgramContent(drafts[after.id], after))
            && (working?.id !== after.id || sameProgramContent(working, after));
    });
}
function backupRecordImpact(before, after) {
    return [["saved", "Programs"], ["history", "Workouts"], ["cycles", "Cycles"], ["custom", "Exercises"], ["gyms", "Gyms"]].map(([key, label]) => {
        const old = new Map((before[key] || []).map(r => [r.id, r]));
        const next = new Map((after[key] || []).map(r => [r.id, r]));
        let added = 0, updated = 0, kept = 0;
        next.forEach((r, id) => { if (!old.has(id))
            added++;
        else if (!sameProgramContent(old.get(id), r))
            updated++;
        else
            kept++; });
        const removed = [...old.keys()].filter(id => !next.has(id)).length;
        return { key, label, added, updated, kept, removed, total: next.size };
    });
}
const SET_ROW_BLEED = 16; // matches the exercise pane's 16px horizontal padding
const AXIAL_ANCHOR_RELIEF = 0.6;
/* The lengthened-position ANCHOR bonus. Its size was set when the working belief was that
 * lengthened-biased training BEAT full range of motion. That claim did not replicate — Wolf et al.
 * (PeerJ 2025) found lengthened partials and full ROM equivalent in trained lifters, and a
 * pre-registered multi-site replication agreed — so on the face of it 1.25 looks overweight for what
 * survives (long vs SHORT muscle length, ES ~0.28), and I came here intending to cut it.
 *
 * MEASUREMENT SAID NO, and it is worth writing down why so nobody re-opens this on the same reasoning.
 * Swept over 1,458 configs at 1.25 / 1.1 / 0.9 / 0.6 / 0.35:
 *   - What a cut BUYS is almost nothing. Lengthened coverage runs 47.0 / … / 45.2% across a 3.5x cut,
 *     because the coverage floor re-earns long-length work on accessory slots whatever wins the
 *     anchor. Barbell anchors gain ~1pp; configs training no rear delt at all improve by ~5.
 *   - What a cut COSTS is real. Parts finishing the week under MEV rise 2258 → 2263 (at 1.1) → 2276
 *     (at 0.6), and front delts over MRV 657 → 668. Both breach gates/fitfloor.mjs, at EVERY value
 *     tried, including a 12% trim.
 * The mechanism is that lengthened movements are disproportionately multi-part COMPOUNDS — RDLs, dips,
 * Bulgarian split squats — so demoting them removes incidental coverage of the muscles they also
 * train. And MEV is a far better-supported quantity than an ES 0.28 range-of-motion preference: trading
 * the well-evidenced one to better calibrate the weakly-evidenced one is the wrong direction. The
 * number stays at 1.25 on the evidence, not on inertia. Revisit only if the coverage floor is changed
 * so it can recover those parts independently. */
const STRETCH_ANCHOR_BONUS = () => 1.25;
const anchorLoadOf = (goal) => (goal === "strength" ? 1 : goal === "both" ? 0.56 : 0);
/* Magnitude of the untrained-wrist-direction coverage bonus (see buildDaySlots). Named rather than
   inlined so the sweep that set it can be re-run against one symbol. */
const WRIST_DIR_BONUS = 2.0;
/* GENERATION ATTRIBUTION — off by default, and off is the normal state.
   `explainGeneration` switches this on, RE-RUNS the same generateProgram with the program's own
   stored seed, and switches it off again. Generation is synchronous and single-threaded, so a module
   flag is safe here and costs nothing when null: every recording site is guarded, and the scoring
   arithmetic is byte-identical either way because each helper returns the value it was handed and the
   operators and their order are untouched.
   RE-RUNNING rather than storing a trace on the program is deliberate. A stored trace would be a
   second copy of the truth that ages the moment the engine changes, and would bloat every saved
   program in the store. A re-run is generated by the CURRENT engine and can be checked against the
   program it claims to explain — which gates/genexplain.mjs does, because an attribution of a
   different program than the one on screen is worse than none. */
/* ═══ PIPELINE TRACE — WHAT EACH PASS DID, NOT JUST WHAT WAS PICKED ════════════════════════════
 * `GEN_TRACE` below answers "why this exercise" — the scoring terms behind one selection.
 * This answers a different question that nothing could: "which PASS changed my program, and to
 * what?" Generation is a chain of nine mutating passes over one object, and until now the only way
 * to see between them was to hand-patch console.logs into the source, rebuild, and delete them
 * afterwards. That was done twice in a single session, and both times it found the bug within one
 * run — a pulling movement placed by the coverage floor and cut straight back out by the time
 * fitter, and later the fact that engine 8's losses were in SELECTION rather than trimming, which
 * three reasoned attempts had failed to work out. Temporary instrumentation that keeps being
 * rebuilt is a permanent feature that has not been written yet.
 *
 * Null unless a trace is running, so the cost to normal generation is one property read per stage. */
/* ═══ REDUCE — ONE NUMBER, BECAUSE THERE WERE TWO AND THEY DISAGREED BY HALF ═══════════════════
 * A de-emphasised muscle was damped to 0.3 of its weight in the day plan and separately floored at
 * 0.6 of MEV by the coverage pass. Two owners for one lever, and the weight runs first, so 0.3 won
 * and the floor spent the rest of generation trying to claw back a muscle the plan had already
 * starved. Measured before this change: reducing a SINGLE muscle took it to ZERO in 237 of 840
 * programs — 28% — and `auditProgramWeek` never noticed, because a group with any sibling volume
 * left does not read as absent.
 *
 * ⚠ AND THE LEVER WAS THE WRONG TYPE, WHICH IS THE DEEPER POINT. A selection WEIGHT has no defined
 * relationship to an outcome: `w *= 0.3` means "this muscle loses apportionment contests", which
 * lands anywhere between a mild reduction and nothing at all depending on what else happens to be
 * competing that day. An input-side lever cannot make an output-side promise, and "train my legs
 * less" is entirely an output-side promise. So the weight is now derived FROM the volume target
 * rather than chosen independently of i
```

## history/perf/volume candidate 2

offset: 528436

```js
s, capped at the
// per-exercise ceiling; capWeeklyVolume still trims any MRV overrun afterward). Runs after coverage
// and bias seeding so the FINAL exercise lists are deduped; primaries are compounds and never in a
// family, so this only ever removes accessory isolations.
function consolidateFamilies(program) {
    const cfg = program.config;
    const manual = cfg.progression === "manual";
    program.days.forEach(day => {
        if (!Array.isArray(day.exercises) || day.exercises.length < 2)
            return;
        const seenFam = {}; // family -> survivor exId (the first picked, = highest preference)
        const keep = [];
        const absorbed = {}; // survivor exId -> sets folded in from dropped duplicates
        const primId = day.exercises[day.primaryIndex] != null ? day.exercises[day.primaryIndex] : day.exercises[0];
        day.exercises.forEach(id => {
            const fam = EX_FAMILY[id];
            if (fam && seenFam[fam] != null) {
                const survId = seenFam[fam];
                absorbed[survId] = (absorbed[survId] || 0) + baseSetsFor(cfg, EX_BY_ID[id], false);
                return; // drop this duplicate-family slot
            }
            if (fam)
                seenFam[fam] = id;
            keep.push(id);
        });
        if (keep.length === day.exercises.length)
            return; // nothing to consolidate
        const oldExercises = day.exercises;
        day.exercises = keep;
        day.primaryIndex = Math.max(0, keep.indexOf(primId));
        remapDaySlotKeys(program, day.id, oldExercises, keep);
        Object.entries(absorbed).forEach(([survId, add]) => {
            const slot = keep.indexOf(survId);
            if (slot < 0)
                return;
            const key = `${day.id}:${slot}`;
            const isP = slot === day.primaryIndex;
            if (manual) {
                const [lo, hi] = repRange(cfg.goal, EX_BY_ID[survId], isP);
                const base = baseSetsFor(cfg, EX_BY_ID[survId], isP);
                const o = program.overrides[key] || { sets: base, reps: `${lo}-${hi}` };
                program.overrides[key] = { ...o, sets: clamp((o.sets || base) + add, 2, 6) };
            }
            else {
                program.slotBias = { ...(program.slotBias || {}), [key]: (program.slotBias?.[key] || 0) + add };
            }
        });
    });
}
// When a weekly schedule is set, the next workout follows the schedule's ORDER and is the day right
// after your last completed session — so a MISSED day is picked up next instead of being skipped
// (jumping to "today's" weekday slot would silently drop that day's volume). The sequence is the
// schedule's day ids in weekday order, de-duplicated; returns -1 when there's no usable schedule.
function nextScheduledIndex(days, schedule, lastDoneId) {
    if (!schedule || !Array.isArray(days) || !days.length)
        return -1;
    const seq = [];
    for (let wd = 0; wd < 7; wd++) {
        const id = schedule[wd];
        if (id && days.some(d => d.id === id) && !seq.includes(id))
            seq.push(id);
    }
    if (!seq.length)
        return -1;
    // No session logged for this program yet: don't guess from the calendar (whichever day the
    // auto-applied suggested schedule happens to place on the week's earliest weekday, per the
    // Sun→Sat scan above) — that's essentially arbitrary relative to the program's own day order and
    // routinely landed on a day mid-rotation (e.g. "Day 4 of 5") for a program you hadn't touched yet.
    // Return -1 so the caller falls back to the same day-order-0 default used when there's no schedule
    // at all, matching the plain "start at Day 1" expectation for something you haven't started.
    if (!lastDoneId)
        return -1;
    const pos = seq.indexOf(lastDoneId);
    const nextId = pos >= 0 ? seq[(pos + 1) % seq.length] : seq[0];
    return days.findIndex(d => d.id === nextId);
}
// The day a program should open to: the next one due given your history — the day after your last
// completed session (schedule order when a schedule is set, otherwise the program's day order), so
// the program view lands on what's next rather than always the first day.
function nextDueDayId(program, history) {
    if (!program || !Array.isArray(program.days) || !program.days.length)
        return undefined;
    const entries = (history || []).filter(h => h.programId === program.id);
    // Same resolution as nextSessionCursor — this decides which day the PROGRAM VIEW opens to, so a
    // stale id here lands the lifter on the wrong day even when the cursor itself is right.
    const lastIdx = entries.length ? historyDayIndex(program.days, entries[0]) : -1;
    const lastDoneId = lastIdx >= 0 ? program.days[lastIdx].id : (entries.length ? entries[0].dayId : null);
    const si = nextScheduledIndex(program.days, program.schedule, lastDoneId);
    if (si >= 0)
        return program.days[si].id;
    const idx = lastIdx >= 0 ? (lastIdx + 1) % program.days.length : 0;
    return program.days[idx].id;
}
// Which saved program is "up next" on the home screen. Precedence, highest first:
//   1. pinnedId — a program the user explicitly created/activated (this is the authority; without it,
//      a freshly saved program would lose to whatever was trained last — the "up next shows my old
//      workout" bug),
//   2. the most recently trained program (history is newest-first),
//   3. the active block of an in-progress cycle,
//   4. the newest-created program.
// Pure and side-effect-free so it can be unit-tested and reasoned about on its own.
function pickActiveProgram(saved, history, cycles, pinnedId) {
    if (!Array.isArray(saved) || !saved.length)
        return null;
    if (pinnedId) {
        const p = saved.find(s => s.id === pinnedId);
        if (p)
            return p;
    }
    for (const h of (history || [])) {
        const p = saved.find(s => s.id === h.programId);
        if (p)
            return p;
    }
    const liveCycle = (cycles || []).find(c => !c.done && Array.isArray(c.blockIds) && c.blockIds.length);
    if (liveCycle) {
        const p = saved.find(s => s.id === liveCycle.blockIds[liveCycle.activeBlock || 0]);
        if (p)
            return p;
    }
    return saved.slice().sort((a, b) => b.createdAt - a.createdAt)[0] || null;
}
// The next session's position within a program, from its history: which day is due and which week.
// dayIndex is the day AFTER the most recent logged session FOR THIS PROGRAM (schedule order when a
// schedule is set, otherwise program-day order), wrapping at the end of the rotation; weekIndex
// advances once per full rotation and wraps (including the deload week) at the program length. A
// program with no history of its own starts at day 1, week 1 — so a freshly activated program does
// NOT inherit the day cursor of whatever you trained last. Pure, so the cursor is unit-testable.
// Continuous ("endless") mode: has a lift's estimated 1RM stopped moving? Looks at the best e1RM per
// rotation across the program's main lifts over the last few rotations; a stall (no new best) is the
// cue to pull a recovery deload forward instead of waiting for the fixed cadence. Pure + conservative
// (only consulted once a few accumulation weeks are in), so it nudges rather than thrashes.
function endlessStalled(program, entries, dpw) {
    const mains = new Set((program.days || []).map(d => d.exercises?.[d.primaryIndex]).filter(Boolean));
    if (!mains.size)
        return false;
    const rots = [[], [], []];
    entries.forEach((h, i) => { const r = Math.floor(i / dpw); if (r < 3)
        rots[r].push(h); });
    const bestOf = (rot) => {
        let best = 0;
        rot.forEach(h => Object.entries(h.perf || {}).forEach(([id, p]) => {
            if (!mains.has(id) || !p.sets)
                return;
            p.sets.forEach(s => { if (s.w > 0 && s.r > 0)
                best = Math.max(best, e1rm(s.w, s.r)); });
        }));
        return best;
    };
    const [r0, r1, r2] = [bestOf(rots[0]), bestOf(rots[1]), bestOf(rots[2])];
    if (!(r0 > 0 && r1 > 0 && r2 > 0))
        return false; // need 3 full rotations of data
    return r0 <= Math.max(r1, r2) + 1e-6; // newest rotation set no new best → stalled
}
/* WHICH DAY WAS THIS SESSION PERFORMED ON — by id, then by label. Every logged entry stores the
   `dayId` it ran on, and before day ids became deterministic (see `days` below) a regeneration threw
   fresh ids at a program and detached its entire history in one step. Making ids stable stops that
   happening again, but it does NOTHING for logs already on a lifter's phone: those carry the old
   random ids and would stay orphaned forever.
   So resolution falls back to the day LABEL, which history has always stored alongside the id and
   which is the identity a lifter actually means ("Pull B"). That reattaches existing logs at read
   time on every load path, with no migration to run, nothing to get half-applied, and no risk of
   rewriting a store on boot. Labels are not guaranteed unique the way ids are — a program can carry
   two days with the same label — so the id is always tried first and the label only rescues an entry
   the id could not place. */
/* ⚠ WHOSE HISTORY IS THIS? ONE OWNER, BECAUSE THE ANSWER IS NOT OBVIOUS AND WAS GOT WRONG.
 *
 * HIS REPORT: "I created a new program and it's showing days of it are already completed."
 * Reproduced — a brand-new upper/lower program came up with 2 of its 4 days ticked.
 *
 * THE CAUSE IS TWO CORRECT DECISIONS MEETING. v578 made day ids DETERMINISTIC, so every upper/lower
 * program the app builds names its days `upper_a`, `lower_a`, `upper_b`, `lower_b` — stable per split
 * BY DESIGN, which is exactly what lets a regenerated program keep its history. And the plan tab's
 * completion memo read the WHOLE history with no notion of which program an entry belonged to. So
 * sessions logged against last month's upper/lower marked this morning's 
```

## history/perf/volume candidate 3

offset: 528676

```js
y ever removes accessory isolations.
function consolidateFamilies(program) {
    const cfg = program.config;
    const manual = cfg.progression === "manual";
    program.days.forEach(day => {
        if (!Array.isArray(day.exercises) || day.exercises.length < 2)
            return;
        const seenFam = {}; // family -> survivor exId (the first picked, = highest preference)
        const keep = [];
        const absorbed = {}; // survivor exId -> sets folded in from dropped duplicates
        const primId = day.exercises[day.primaryIndex] != null ? day.exercises[day.primaryIndex] : day.exercises[0];
        day.exercises.forEach(id => {
            const fam = EX_FAMILY[id];
            if (fam && seenFam[fam] != null) {
                const survId = seenFam[fam];
                absorbed[survId] = (absorbed[survId] || 0) + baseSetsFor(cfg, EX_BY_ID[id], false);
                return; // drop this duplicate-family slot
            }
            if (fam)
                seenFam[fam] = id;
            keep.push(id);
        });
        if (keep.length === day.exercises.length)
            return; // nothing to consolidate
        const oldExercises = day.exercises;
        day.exercises = keep;
        day.primaryIndex = Math.max(0, keep.indexOf(primId));
        remapDaySlotKeys(program, day.id, oldExercises, keep);
        Object.entries(absorbed).forEach(([survId, add]) => {
            const slot = keep.indexOf(survId);
            if (slot < 0)
                return;
            const key = `${day.id}:${slot}`;
            const isP = slot === day.primaryIndex;
            if (manual) {
                const [lo, hi] = repRange(cfg.goal, EX_BY_ID[survId], isP);
                const base = baseSetsFor(cfg, EX_BY_ID[survId], isP);
                const o = program.overrides[key] || { sets: base, reps: `${lo}-${hi}` };
                program.overrides[key] = { ...o, sets: clamp((o.sets || base) + add, 2, 6) };
            }
            else {
                program.slotBias = { ...(program.slotBias || {}), [key]: (program.slotBias?.[key] || 0) + add };
            }
        });
    });
}
// When a weekly schedule is set, the next workout follows the schedule's ORDER and is the day right
// after your last completed session — so a MISSED day is picked up next instead of being skipped
// (jumping to "today's" weekday slot would silently drop that day's volume). The sequence is the
// schedule's day ids in weekday order, de-duplicated; returns -1 when there's no usable schedule.
function nextScheduledIndex(days, schedule, lastDoneId) {
    if (!schedule || !Array.isArray(days) || !days.length)
        return -1;
    const seq = [];
    for (let wd = 0; wd < 7; wd++) {
        const id = schedule[wd];
        if (id && days.some(d => d.id === id) && !seq.includes(id))
            seq.push(id);
    }
    if (!seq.length)
        return -1;
    // No session logged for this program yet: don't guess from the calendar (whichever day the
    // auto-applied suggested schedule happens to place on the week's earliest weekday, per the
    // Sun→Sat scan above) — that's essentially arbitrary relative to the program's own day order and
    // routinely landed on a day mid-rotation (e.g. "Day 4 of 5") for a program you hadn't touched yet.
    // Return -1 so the caller falls back to the same day-order-0 default used when there's no schedule
    // at all, matching the plain "start at Day 1" expectation for something you haven't started.
    if (!lastDoneId)
        return -1;
    const pos = seq.indexOf(lastDoneId);
    const nextId = pos >= 0 ? seq[(pos + 1) % seq.length] : seq[0];
    return days.findIndex(d => d.id === nextId);
}
// The day a program should open to: the next one due given your history — the day after your last
// completed session (schedule order when a schedule is set, otherwise the program's day order), so
// the program view lands on what's next rather than always the first day.
function nextDueDayId(program, history) {
    if (!program || !Array.isArray(program.days) || !program.days.length)
        return undefined;
    const entries = (history || []).filter(h => h.programId === program.id);
    // Same resolution as nextSessionCursor — this decides which day the PROGRAM VIEW opens to, so a
    // stale id here lands the lifter on the wrong day even when the cursor itself is right.
    const lastIdx = entries.length ? historyDayIndex(program.days, entries[0]) : -1;
    const lastDoneId = lastIdx >= 0 ? program.days[lastIdx].id : (entries.length ? entries[0].dayId : null);
    const si = nextScheduledIndex(program.days, program.schedule, lastDoneId);
    if (si >= 0)
        return program.days[si].id;
    const idx = lastIdx >= 0 ? (lastIdx + 1) % program.days.length : 0;
    return program.days[idx].id;
}
// Which saved program is "up next" on the home screen. Precedence, highest first:
//   1. pinnedId — a program the user explicitly created/activated (this is the authority; without it,
//      a freshly saved program would lose to whatever was trained last — the "up next shows my old
//      workout" bug),
//   2. the most recently trained program (history is newest-first),
//   3. the active block of an in-progress cycle,
//   4. the newest-created program.
// Pure and side-effect-free so it can be unit-tested and reasoned about on its own.
function pickActiveProgram(saved, history, cycles, pinnedId) {
    if (!Array.isArray(saved) || !saved.length)
        return null;
    if (pinnedId) {
        const p = saved.find(s => s.id === pinnedId);
        if (p)
            return p;
    }
    for (const h of (history || [])) {
        const p = saved.find(s => s.id === h.programId);
        if (p)
            return p;
    }
    const liveCycle = (cycles || []).find(c => !c.done && Array.isArray(c.blockIds) && c.blockIds.length);
    if (liveCycle) {
        const p = saved.find(s => s.id === liveCycle.blockIds[liveCycle.activeBlock || 0]);
        if (p)
            return p;
    }
    return saved.slice().sort((a, b) => b.createdAt - a.createdAt)[0] || null;
}
// The next session's position within a program, from its history: which day is due and which week.
// dayIndex is the day AFTER the most recent logged session FOR THIS PROGRAM (schedule order when a
// schedule is set, otherwise program-day order), wrapping at the end of the rotation; weekIndex
// advances once per full rotation and wraps (including the deload week) at the program length. A
// program with no history of its own starts at day 1, week 1 — so a freshly activated program does
// NOT inherit the day cursor of whatever you trained last. Pure, so the cursor is unit-testable.
// Continuous ("endless") mode: has a lift's estimated 1RM stopped moving? Looks at the best e1RM per
// rotation across the program's main lifts over the last few rotations; a stall (no new best) is the
// cue to pull a recovery deload forward instead of waiting for the fixed cadence. Pure + conservative
// (only consulted once a few accumulation weeks are in), so it nudges rather than thrashes.
function endlessStalled(program, entries, dpw) {
    const mains = new Set((program.days || []).map(d => d.exercises?.[d.primaryIndex]).filter(Boolean));
    if (!mains.size)
        return false;
    const rots = [[], [], []];
    entries.forEach((h, i) => { const r = Math.floor(i / dpw); if (r < 3)
        rots[r].push(h); });
    const bestOf = (rot) => {
        let best = 0;
        rot.forEach(h => Object.entries(h.perf || {}).forEach(([id, p]) => {
            if (!mains.has(id) || !p.sets)
                return;
            p.sets.forEach(s => { if (s.w > 0 && s.r > 0)
                best = Math.max(best, e1rm(s.w, s.r)); });
        }));
        return best;
    };
    const [r0, r1, r2] = [bestOf(rots[0]), bestOf(rots[1]), bestOf(rots[2])];
    if (!(r0 > 0 && r1 > 0 && r2 > 0))
        return false; // need 3 full rotations of data
    return r0 <= Math.max(r1, r2) + 1e-6; // newest rotation set no new best → stalled
}
/* WHICH DAY WAS THIS SESSION PERFORMED ON — by id, then by label. Every logged entry stores the
   `dayId` it ran on, and before day ids became deterministic (see `days` below) a regeneration threw
   fresh ids at a program and detached its entire history in one step. Making ids stable stops that
   happening again, but it does NOTHING for logs already on a lifter's phone: those carry the old
   random ids and would stay orphaned forever.
   So resolution falls back to the day LABEL, which history has always stored alongside the id and
   which is the identity a lifter actually means ("Pull B"). That reattaches existing logs at read
   time on every load path, with no migration to run, nothing to get half-applied, and no risk of
   rewriting a store on boot. Labels are not guaranteed unique the way ids are — a program can carry
   two days with the same label — so the id is always tried first and the label only rescues an entry
   the id could not place. */
/* ⚠ WHOSE HISTORY IS THIS? ONE OWNER, BECAUSE THE ANSWER IS NOT OBVIOUS AND WAS GOT WRONG.
 *
 * HIS REPORT: "I created a new program and it's showing days of it are already completed."
 * Reproduced — a brand-new upper/lower program came up with 2 of its 4 days ticked.
 *
 * THE CAUSE IS TWO CORRECT DECISIONS MEETING. v578 made day ids DETERMINISTIC, so every upper/lower
 * program the app builds names its days `upper_a`, `lower_a`, `upper_b`, `lower_b` — stable per split
 * BY DESIGN, which is exactly what lets a regenerated program keep its history. And the plan tab's
 * completion memo read the WHOLE history with no notion of which program an entry belonged to. So
 * sessions logged against last month's upper/lower marked this morning's new one as half finished.
 * The label fallback below would have done it even without the id collision: the labels match too.
 *
 * ⚠⚠ THE OBVIOUS FIX IS THE ONE v578 EXISTS TO PREVENT. Scoping by programId blanks every completed
 * tick th
```

## history/perf/volume candidate 4

offset: 528808

```js
progression === "manual";
    program.days.forEach(day => {
        if (!Array.isArray(day.exercises) || day.exercises.length < 2)
            return;
        const seenFam = {}; // family -> survivor exId (the first picked, = highest preference)
        const keep = [];
        const absorbed = {}; // survivor exId -> sets folded in from dropped duplicates
        const primId = day.exercises[day.primaryIndex] != null ? day.exercises[day.primaryIndex] : day.exercises[0];
        day.exercises.forEach(id => {
            const fam = EX_FAMILY[id];
            if (fam && seenFam[fam] != null) {
                const survId = seenFam[fam];
                absorbed[survId] = (absorbed[survId] || 0) + baseSetsFor(cfg, EX_BY_ID[id], false);
                return; // drop this duplicate-family slot
            }
            if (fam)
                seenFam[fam] = id;
            keep.push(id);
        });
        if (keep.length === day.exercises.length)
            return; // nothing to consolidate
        const oldExercises = day.exercises;
        day.exercises = keep;
        day.primaryIndex = Math.max(0, keep.indexOf(primId));
        remapDaySlotKeys(program, day.id, oldExercises, keep);
        Object.entries(absorbed).forEach(([survId, add]) => {
            const slot = keep.indexOf(survId);
            if (slot < 0)
                return;
            const key = `${day.id}:${slot}`;
            const isP = slot === day.primaryIndex;
            if (manual) {
                const [lo, hi] = repRange(cfg.goal, EX_BY_ID[survId], isP);
                const base = baseSetsFor(cfg, EX_BY_ID[survId], isP);
                const o = program.overrides[key] || { sets: base, reps: `${lo}-${hi}` };
                program.overrides[key] = { ...o, sets: clamp((o.sets || base) + add, 2, 6) };
            }
            else {
                program.slotBias = { ...(program.slotBias || {}), [key]: (program.slotBias?.[key] || 0) + add };
            }
        });
    });
}
// When a weekly schedule is set, the next workout follows the schedule's ORDER and is the day right
// after your last completed session — so a MISSED day is picked up next instead of being skipped
// (jumping to "today's" weekday slot would silently drop that day's volume). The sequence is the
// schedule's day ids in weekday order, de-duplicated; returns -1 when there's no usable schedule.
function nextScheduledIndex(days, schedule, lastDoneId) {
    if (!schedule || !Array.isArray(days) || !days.length)
        return -1;
    const seq = [];
    for (let wd = 0; wd < 7; wd++) {
        const id = schedule[wd];
        if (id && days.some(d => d.id === id) && !seq.includes(id))
            seq.push(id);
    }
    if (!seq.length)
        return -1;
    // No session logged for this program yet: don't guess from the calendar (whichever day the
    // auto-applied suggested schedule happens to place on the week's earliest weekday, per the
    // Sun→Sat scan above) — that's essentially arbitrary relative to the program's own day order and
    // routinely landed on a day mid-rotation (e.g. "Day 4 of 5") for a program you hadn't touched yet.
    // Return -1 so the caller falls back to the same day-order-0 default used when there's no schedule
    // at all, matching the plain "start at Day 1" expectation for something you haven't started.
    if (!lastDoneId)
        return -1;
    const pos = seq.indexOf(lastDoneId);
    const nextId = pos >= 0 ? seq[(pos + 1) % seq.length] : seq[0];
    return days.findIndex(d => d.id === nextId);
}
// The day a program should open to: the next one due given your history — the day after your last
// completed session (schedule order when a schedule is set, otherwise the program's day order), so
// the program view lands on what's next rather than always the first day.
function nextDueDayId(program, history) {
    if (!program || !Array.isArray(program.days) || !program.days.length)
        return undefined;
    const entries = (history || []).filter(h => h.programId === program.id);
    // Same resolution as nextSessionCursor — this decides which day the PROGRAM VIEW opens to, so a
    // stale id here lands the lifter on the wrong day even when the cursor itself is right.
    const lastIdx = entries.length ? historyDayIndex(program.days, entries[0]) : -1;
    const lastDoneId = lastIdx >= 0 ? program.days[lastIdx].id : (entries.length ? entries[0].dayId : null);
    const si = nextScheduledIndex(program.days, program.schedule, lastDoneId);
    if (si >= 0)
        return program.days[si].id;
    const idx = lastIdx >= 0 ? (lastIdx + 1) % program.days.length : 0;
    return program.days[idx].id;
}
// Which saved program is "up next" on the home screen. Precedence, highest first:
//   1. pinnedId — a program the user explicitly created/activated (this is the authority; without it,
//      a freshly saved program would lose to whatever was trained last — the "up next shows my old
//      workout" bug),
//   2. the most recently trained program (history is newest-first),
//   3. the active block of an in-progress cycle,
//   4. the newest-created program.
// Pure and side-effect-free so it can be unit-tested and reasoned about on its own.
function pickActiveProgram(saved, history, cycles, pinnedId) {
    if (!Array.isArray(saved) || !saved.length)
        return null;
    if (pinnedId) {
        const p = saved.find(s => s.id === pinnedId);
        if (p)
            return p;
    }
    for (const h of (history || [])) {
        const p = saved.find(s => s.id === h.programId);
        if (p)
            return p;
    }
    const liveCycle = (cycles || []).find(c => !c.done && Array.isArray(c.blockIds) && c.blockIds.length);
    if (liveCycle) {
        const p = saved.find(s => s.id === liveCycle.blockIds[liveCycle.activeBlock || 0]);
        if (p)
            return p;
    }
    return saved.slice().sort((a, b) => b.createdAt - a.createdAt)[0] || null;
}
// The next session's position within a program, from its history: which day is due and which week.
// dayIndex is the day AFTER the most recent logged session FOR THIS PROGRAM (schedule order when a
// schedule is set, otherwise program-day order), wrapping at the end of the rotation; weekIndex
// advances once per full rotation and wraps (including the deload week) at the program length. A
// program with no history of its own starts at day 1, week 1 — so a freshly activated program does
// NOT inherit the day cursor of whatever you trained last. Pure, so the cursor is unit-testable.
// Continuous ("endless") mode: has a lift's estimated 1RM stopped moving? Looks at the best e1RM per
// rotation across the program's main lifts over the last few rotations; a stall (no new best) is the
// cue to pull a recovery deload forward instead of waiting for the fixed cadence. Pure + conservative
// (only consulted once a few accumulation weeks are in), so it nudges rather than thrashes.
function endlessStalled(program, entries, dpw) {
    const mains = new Set((program.days || []).map(d => d.exercises?.[d.primaryIndex]).filter(Boolean));
    if (!mains.size)
        return false;
    const rots = [[], [], []];
    entries.forEach((h, i) => { const r = Math.floor(i / dpw); if (r < 3)
        rots[r].push(h); });
    const bestOf = (rot) => {
        let best = 0;
        rot.forEach(h => Object.entries(h.perf || {}).forEach(([id, p]) => {
            if (!mains.has(id) || !p.sets)
                return;
            p.sets.forEach(s => { if (s.w > 0 && s.r > 0)
                best = Math.max(best, e1rm(s.w, s.r)); });
        }));
        return best;
    };
    const [r0, r1, r2] = [bestOf(rots[0]), bestOf(rots[1]), bestOf(rots[2])];
    if (!(r0 > 0 && r1 > 0 && r2 > 0))
        return false; // need 3 full rotations of data
    return r0 <= Math.max(r1, r2) + 1e-6; // newest rotation set no new best → stalled
}
/* WHICH DAY WAS THIS SESSION PERFORMED ON — by id, then by label. Every logged entry stores the
   `dayId` it ran on, and before day ids became deterministic (see `days` below) a regeneration threw
   fresh ids at a program and detached its entire history in one step. Making ids stable stops that
   happening again, but it does NOTHING for logs already on a lifter's phone: those carry the old
   random ids and would stay orphaned forever.
   So resolution falls back to the day LABEL, which history has always stored alongside the id and
   which is the identity a lifter actually means ("Pull B"). That reattaches existing logs at read
   time on every load path, with no migration to run, nothing to get half-applied, and no risk of
   rewriting a store on boot. Labels are not guaranteed unique the way ids are — a program can carry
   two days with the same label — so the id is always tried first and the label only rescues an entry
   the id could not place. */
/* ⚠ WHOSE HISTORY IS THIS? ONE OWNER, BECAUSE THE ANSWER IS NOT OBVIOUS AND WAS GOT WRONG.
 *
 * HIS REPORT: "I created a new program and it's showing days of it are already completed."
 * Reproduced — a brand-new upper/lower program came up with 2 of its 4 days ticked.
 *
 * THE CAUSE IS TWO CORRECT DECISIONS MEETING. v578 made day ids DETERMINISTIC, so every upper/lower
 * program the app builds names its days `upper_a`, `lower_a`, `upper_b`, `lower_b` — stable per split
 * BY DESIGN, which is exactly what lets a regenerated program keep its history. And the plan tab's
 * completion memo read the WHOLE history with no notion of which program an entry belonged to. So
 * sessions logged against last month's upper/lower marked this morning's new one as half finished.
 * The label fallback below would have done it even without the id collision: the labels match too.
 *
 * ⚠⚠ THE OBVIOUS FIX IS THE ONE v578 EXISTS TO PREVENT. Scoping by programId blanks every completed
 * tick the moment a program is regenerated — precisely the regression v578 fixed — and it is safe
 * only because `handleRegenerate` does `p.
```

## history/perf/volume candidate 5

offset: 529095

```js
bsorbed = {}; // survivor exId -> sets folded in from dropped duplicates
        const primId = day.exercises[day.primaryIndex] != null ? day.exercises[day.primaryIndex] : day.exercises[0];
        day.exercises.forEach(id => {
            const fam = EX_FAMILY[id];
            if (fam && seenFam[fam] != null) {
                const survId = seenFam[fam];
                absorbed[survId] = (absorbed[survId] || 0) + baseSetsFor(cfg, EX_BY_ID[id], false);
                return; // drop this duplicate-family slot
            }
            if (fam)
                seenFam[fam] = id;
            keep.push(id);
        });
        if (keep.length === day.exercises.length)
            return; // nothing to consolidate
        const oldExercises = day.exercises;
        day.exercises = keep;
        day.primaryIndex = Math.max(0, keep.indexOf(primId));
        remapDaySlotKeys(program, day.id, oldExercises, keep);
        Object.entries(absorbed).forEach(([survId, add]) => {
            const slot = keep.indexOf(survId);
            if (slot < 0)
                return;
            const key = `${day.id}:${slot}`;
            const isP = slot === day.primaryIndex;
            if (manual) {
                const [lo, hi] = repRange(cfg.goal, EX_BY_ID[survId], isP);
                const base = baseSetsFor(cfg, EX_BY_ID[survId], isP);
                const o = program.overrides[key] || { sets: base, reps: `${lo}-${hi}` };
                program.overrides[key] = { ...o, sets: clamp((o.sets || base) + add, 2, 6) };
            }
            else {
                program.slotBias = { ...(program.slotBias || {}), [key]: (program.slotBias?.[key] || 0) + add };
            }
        });
    });
}
// When a weekly schedule is set, the next workout follows the schedule's ORDER and is the day right
// after your last completed session — so a MISSED day is picked up next instead of being skipped
// (jumping to "today's" weekday slot would silently drop that day's volume). The sequence is the
// schedule's day ids in weekday order, de-duplicated; returns -1 when there's no usable schedule.
function nextScheduledIndex(days, schedule, lastDoneId) {
    if (!schedule || !Array.isArray(days) || !days.length)
        return -1;
    const seq = [];
    for (let wd = 0; wd < 7; wd++) {
        const id = schedule[wd];
        if (id && days.some(d => d.id === id) && !seq.includes(id))
            seq.push(id);
    }
    if (!seq.length)
        return -1;
    // No session logged for this program yet: don't guess from the calendar (whichever day the
    // auto-applied suggested schedule happens to place on the week's earliest weekday, per the
    // Sun→Sat scan above) — that's essentially arbitrary relative to the program's own day order and
    // routinely landed on a day mid-rotation (e.g. "Day 4 of 5") for a program you hadn't touched yet.
    // Return -1 so the caller falls back to the same day-order-0 default used when there's no schedule
    // at all, matching the plain "start at Day 1" expectation for something you haven't started.
    if (!lastDoneId)
        return -1;
    const pos = seq.indexOf(lastDoneId);
    const nextId = pos >= 0 ? seq[(pos + 1) % seq.length] : seq[0];
    return days.findIndex(d => d.id === nextId);
}
// The day a program should open to: the next one due given your history — the day after your last
// completed session (schedule order when a schedule is set, otherwise the program's day order), so
// the program view lands on what's next rather than always the first day.
function nextDueDayId(program, history) {
    if (!program || !Array.isArray(program.days) || !program.days.length)
        return undefined;
    const entries = (history || []).filter(h => h.programId === program.id);
    // Same resolution as nextSessionCursor — this decides which day the PROGRAM VIEW opens to, so a
    // stale id here lands the lifter on the wrong day even when the cursor itself is right.
    const lastIdx = entries.length ? historyDayIndex(program.days, entries[0]) : -1;
    const lastDoneId = lastIdx >= 0 ? program.days[lastIdx].id : (entries.length ? entries[0].dayId : null);
    const si = nextScheduledIndex(program.days, program.schedule, lastDoneId);
    if (si >= 0)
        return program.days[si].id;
    const idx = lastIdx >= 0 ? (lastIdx + 1) % program.days.length : 0;
    return program.days[idx].id;
}
// Which saved program is "up next" on the home screen. Precedence, highest first:
//   1. pinnedId — a program the user explicitly created/activated (this is the authority; without it,
//      a freshly saved program would lose to whatever was trained last — the "up next shows my old
//      workout" bug),
//   2. the most recently trained program (history is newest-first),
//   3. the active block of an in-progress cycle,
//   4. the newest-created program.
// Pure and side-effect-free so it can be unit-tested and reasoned about on its own.
function pickActiveProgram(saved, history, cycles, pinnedId) {
    if (!Array.isArray(saved) || !saved.length)
        return null;
    if (pinnedId) {
        const p = saved.find(s => s.id === pinnedId);
        if (p)
            return p;
    }
    for (const h of (history || [])) {
        const p = saved.find(s => s.id === h.programId);
        if (p)
            return p;
    }
    const liveCycle = (cycles || []).find(c => !c.done && Array.isArray(c.blockIds) && c.blockIds.length);
    if (liveCycle) {
        const p = saved.find(s => s.id === liveCycle.blockIds[liveCycle.activeBlock || 0]);
        if (p)
            return p;
    }
    return saved.slice().sort((a, b) => b.createdAt - a.createdAt)[0] || null;
}
// The next session's position within a program, from its history: which day is due and which week.
// dayIndex is the day AFTER the most recent logged session FOR THIS PROGRAM (schedule order when a
// schedule is set, otherwise program-day order), wrapping at the end of the rotation; weekIndex
// advances once per full rotation and wraps (including the deload week) at the program length. A
// program with no history of its own starts at day 1, week 1 — so a freshly activated program does
// NOT inherit the day cursor of whatever you trained last. Pure, so the cursor is unit-testable.
// Continuous ("endless") mode: has a lift's estimated 1RM stopped moving? Looks at the best e1RM per
// rotation across the program's main lifts over the last few rotations; a stall (no new best) is the
// cue to pull a recovery deload forward instead of waiting for the fixed cadence. Pure + conservative
// (only consulted once a few accumulation weeks are in), so it nudges rather than thrashes.
function endlessStalled(program, entries, dpw) {
    const mains = new Set((program.days || []).map(d => d.exercises?.[d.primaryIndex]).filter(Boolean));
    if (!mains.size)
        return false;
    const rots = [[], [], []];
    entries.forEach((h, i) => { const r = Math.floor(i / dpw); if (r < 3)
        rots[r].push(h); });
    const bestOf = (rot) => {
        let best = 0;
        rot.forEach(h => Object.entries(h.perf || {}).forEach(([id, p]) => {
            if (!mains.has(id) || !p.sets)
                return;
            p.sets.forEach(s => { if (s.w > 0 && s.r > 0)
                best = Math.max(best, e1rm(s.w, s.r)); });
        }));
        return best;
    };
    const [r0, r1, r2] = [bestOf(rots[0]), bestOf(rots[1]), bestOf(rots[2])];
    if (!(r0 > 0 && r1 > 0 && r2 > 0))
        return false; // need 3 full rotations of data
    return r0 <= Math.max(r1, r2) + 1e-6; // newest rotation set no new best → stalled
}
/* WHICH DAY WAS THIS SESSION PERFORMED ON — by id, then by label. Every logged entry stores the
   `dayId` it ran on, and before day ids became deterministic (see `days` below) a regeneration threw
   fresh ids at a program and detached its entire history in one step. Making ids stable stops that
   happening again, but it does NOTHING for logs already on a lifter's phone: those carry the old
   random ids and would stay orphaned forever.
   So resolution falls back to the day LABEL, which history has always stored alongside the id and
   which is the identity a lifter actually means ("Pull B"). That reattaches existing logs at read
   time on every load path, with no migration to run, nothing to get half-applied, and no risk of
   rewriting a store on boot. Labels are not guaranteed unique the way ids are — a program can carry
   two days with the same label — so the id is always tried first and the label only rescues an entry
   the id could not place. */
/* ⚠ WHOSE HISTORY IS THIS? ONE OWNER, BECAUSE THE ANSWER IS NOT OBVIOUS AND WAS GOT WRONG.
 *
 * HIS REPORT: "I created a new program and it's showing days of it are already completed."
 * Reproduced — a brand-new upper/lower program came up with 2 of its 4 days ticked.
 *
 * THE CAUSE IS TWO CORRECT DECISIONS MEETING. v578 made day ids DETERMINISTIC, so every upper/lower
 * program the app builds names its days `upper_a`, `lower_a`, `upper_b`, `lower_b` — stable per split
 * BY DESIGN, which is exactly what lets a regenerated program keep its history. And the plan tab's
 * completion memo read the WHOLE history with no notion of which program an entry belonged to. So
 * sessions logged against last month's upper/lower marked this morning's new one as half finished.
 * The label fallback below would have done it even without the id collision: the labels match too.
 *
 * ⚠⚠ THE OBVIOUS FIX IS THE ONE v578 EXISTS TO PREVENT. Scoping by programId blanks every completed
 * tick the moment a program is regenerated — precisely the regression v578 fixed — and it is safe
 * only because `handleRegenerate` does `p.id = program.id`, so a regenerated program keeps its
 * identity. That dependency is load-bearing; gates/dayscope.mjs check 3 pins it.
 *
 * An entry with NO programId predates attribution and is KEPT, not dropped — the v584 rule that a
 * normaliser refuses when it cannot tell rather t
```

## history/perf/volume candidate 6

offset: 529843

```js
s = day.exercises;
        day.exercises = keep;
        day.primaryIndex = Math.max(0, keep.indexOf(primId));
        remapDaySlotKeys(program, day.id, oldExercises, keep);
        Object.entries(absorbed).forEach(([survId, add]) => {
            const slot = keep.indexOf(survId);
            if (slot < 0)
                return;
            const key = `${day.id}:${slot}`;
            const isP = slot === day.primaryIndex;
            if (manual) {
                const [lo, hi] = repRange(cfg.goal, EX_BY_ID[survId], isP);
                const base = baseSetsFor(cfg, EX_BY_ID[survId], isP);
                const o = program.overrides[key] || { sets: base, reps: `${lo}-${hi}` };
                program.overrides[key] = { ...o, sets: clamp((o.sets || base) + add, 2, 6) };
            }
            else {
                program.slotBias = { ...(program.slotBias || {}), [key]: (program.slotBias?.[key] || 0) + add };
            }
        });
    });
}
// When a weekly schedule is set, the next workout follows the schedule's ORDER and is the day right
// after your last completed session — so a MISSED day is picked up next instead of being skipped
// (jumping to "today's" weekday slot would silently drop that day's volume). The sequence is the
// schedule's day ids in weekday order, de-duplicated; returns -1 when there's no usable schedule.
function nextScheduledIndex(days, schedule, lastDoneId) {
    if (!schedule || !Array.isArray(days) || !days.length)
        return -1;
    const seq = [];
    for (let wd = 0; wd < 7; wd++) {
        const id = schedule[wd];
        if (id && days.some(d => d.id === id) && !seq.includes(id))
            seq.push(id);
    }
    if (!seq.length)
        return -1;
    // No session logged for this program yet: don't guess from the calendar (whichever day the
    // auto-applied suggested schedule happens to place on the week's earliest weekday, per the
    // Sun→Sat scan above) — that's essentially arbitrary relative to the program's own day order and
    // routinely landed on a day mid-rotation (e.g. "Day 4 of 5") for a program you hadn't touched yet.
    // Return -1 so the caller falls back to the same day-order-0 default used when there's no schedule
    // at all, matching the plain "start at Day 1" expectation for something you haven't started.
    if (!lastDoneId)
        return -1;
    const pos = seq.indexOf(lastDoneId);
    const nextId = pos >= 0 ? seq[(pos + 1) % seq.length] : seq[0];
    return days.findIndex(d => d.id === nextId);
}
// The day a program should open to: the next one due given your history — the day after your last
// completed session (schedule order when a schedule is set, otherwise the program's day order), so
// the program view lands on what's next rather than always the first day.
function nextDueDayId(program, history) {
    if (!program || !Array.isArray(program.days) || !program.days.length)
        return undefined;
    const entries = (history || []).filter(h => h.programId === program.id);
    // Same resolution as nextSessionCursor — this decides which day the PROGRAM VIEW opens to, so a
    // stale id here lands the lifter on the wrong day even when the cursor itself is right.
    const lastIdx = entries.length ? historyDayIndex(program.days, entries[0]) : -1;
    const lastDoneId = lastIdx >= 0 ? program.days[lastIdx].id : (entries.length ? entries[0].dayId : null);
    const si = nextScheduledIndex(program.days, program.schedule, lastDoneId);
    if (si >= 0)
        return program.days[si].id;
    const idx = lastIdx >= 0 ? (lastIdx + 1) % program.days.length : 0;
    return program.days[idx].id;
}
// Which saved program is "up next" on the home screen. Precedence, highest first:
//   1. pinnedId — a program the user explicitly created/activated (this is the authority; without it,
//      a freshly saved program would lose to whatever was trained last — the "up next shows my old
//      workout" bug),
//   2. the most recently trained program (history is newest-first),
//   3. the active block of an in-progress cycle,
//   4. the newest-created program.
// Pure and side-effect-free so it can be unit-tested and reasoned about on its own.
function pickActiveProgram(saved, history, cycles, pinnedId) {
    if (!Array.isArray(saved) || !saved.length)
        return null;
    if (pinnedId) {
        const p = saved.find(s => s.id === pinnedId);
        if (p)
            return p;
    }
    for (const h of (history || [])) {
        const p = saved.find(s => s.id === h.programId);
        if (p)
            return p;
    }
    const liveCycle = (cycles || []).find(c => !c.done && Array.isArray(c.blockIds) && c.blockIds.length);
    if (liveCycle) {
        const p = saved.find(s => s.id === liveCycle.blockIds[liveCycle.activeBlock || 0]);
        if (p)
            return p;
    }
    return saved.slice().sort((a, b) => b.createdAt - a.createdAt)[0] || null;
}
// The next session's position within a program, from its history: which day is due and which week.
// dayIndex is the day AFTER the most recent logged session FOR THIS PROGRAM (schedule order when a
// schedule is set, otherwise program-day order), wrapping at the end of the rotation; weekIndex
// advances once per full rotation and wraps (including the deload week) at the program length. A
// program with no history of its own starts at day 1, week 1 — so a freshly activated program does
// NOT inherit the day cursor of whatever you trained last. Pure, so the cursor is unit-testable.
// Continuous ("endless") mode: has a lift's estimated 1RM stopped moving? Looks at the best e1RM per
// rotation across the program's main lifts over the last few rotations; a stall (no new best) is the
// cue to pull a recovery deload forward instead of waiting for the fixed cadence. Pure + conservative
// (only consulted once a few accumulation weeks are in), so it nudges rather than thrashes.
function endlessStalled(program, entries, dpw) {
    const mains = new Set((program.days || []).map(d => d.exercises?.[d.primaryIndex]).filter(Boolean));
    if (!mains.size)
        return false;
    const rots = [[], [], []];
    entries.forEach((h, i) => { const r = Math.floor(i / dpw); if (r < 3)
        rots[r].push(h); });
    const bestOf = (rot) => {
        let best = 0;
        rot.forEach(h => Object.entries(h.perf || {}).forEach(([id, p]) => {
            if (!mains.has(id) || !p.sets)
                return;
            p.sets.forEach(s => { if (s.w > 0 && s.r > 0)
                best = Math.max(best, e1rm(s.w, s.r)); });
        }));
        return best;
    };
    const [r0, r1, r2] = [bestOf(rots[0]), bestOf(rots[1]), bestOf(rots[2])];
    if (!(r0 > 0 && r1 > 0 && r2 > 0))
        return false; // need 3 full rotations of data
    return r0 <= Math.max(r1, r2) + 1e-6; // newest rotation set no new best → stalled
}
/* WHICH DAY WAS THIS SESSION PERFORMED ON — by id, then by label. Every logged entry stores the
   `dayId` it ran on, and before day ids became deterministic (see `days` below) a regeneration threw
   fresh ids at a program and detached its entire history in one step. Making ids stable stops that
   happening again, but it does NOTHING for logs already on a lifter's phone: those carry the old
   random ids and would stay orphaned forever.
   So resolution falls back to the day LABEL, which history has always stored alongside the id and
   which is the identity a lifter actually means ("Pull B"). That reattaches existing logs at read
   time on every load path, with no migration to run, nothing to get half-applied, and no risk of
   rewriting a store on boot. Labels are not guaranteed unique the way ids are — a program can carry
   two days with the same label — so the id is always tried first and the label only rescues an entry
   the id could not place. */
/* ⚠ WHOSE HISTORY IS THIS? ONE OWNER, BECAUSE THE ANSWER IS NOT OBVIOUS AND WAS GOT WRONG.
 *
 * HIS REPORT: "I created a new program and it's showing days of it are already completed."
 * Reproduced — a brand-new upper/lower program came up with 2 of its 4 days ticked.
 *
 * THE CAUSE IS TWO CORRECT DECISIONS MEETING. v578 made day ids DETERMINISTIC, so every upper/lower
 * program the app builds names its days `upper_a`, `lower_a`, `upper_b`, `lower_b` — stable per split
 * BY DESIGN, which is exactly what lets a regenerated program keep its history. And the plan tab's
 * completion memo read the WHOLE history with no notion of which program an entry belonged to. So
 * sessions logged against last month's upper/lower marked this morning's new one as half finished.
 * The label fallback below would have done it even without the id collision: the labels match too.
 *
 * ⚠⚠ THE OBVIOUS FIX IS THE ONE v578 EXISTS TO PREVENT. Scoping by programId blanks every completed
 * tick the moment a program is regenerated — precisely the regression v578 fixed — and it is safe
 * only because `handleRegenerate` does `p.id = program.id`, so a regenerated program keeps its
 * identity. That dependency is load-bearing; gates/dayscope.mjs check 3 pins it.
 *
 * An entry with NO programId predates attribution and is KEPT, not dropped — the v584 rule that a
 * normaliser refuses when it cannot tell rather than guessing wrong. Dropping it would blank the
 * ticks of anyone with old logs, the same defect facing the other way.
 *
 * A named owner rather than an inline filter, because this is the third reader of history to need
 * the question answered and the first two answered it differently: `blockPlanFor` requires an exact
 * programId match (correct — it counts this program's sessions), while `recentDayPerf` deliberately
 * reads ACROSS programs (also correct — a load reference is about the lifter, not the plan).
 * Completion is program-scoped; performance is lifter-scoped. Whoever writes the fourth reader
 * should have to pick one deliberately. */
function historyForProgram(history, program) {
    const id = program?.id;
    return (Arr
```

## history/perf/volume candidate 7

offset: 530074

```js
=> {
            const slot = keep.indexOf(survId);
            if (slot < 0)
                return;
            const key = `${day.id}:${slot}`;
            const isP = slot === day.primaryIndex;
            if (manual) {
                const [lo, hi] = repRange(cfg.goal, EX_BY_ID[survId], isP);
                const base = baseSetsFor(cfg, EX_BY_ID[survId], isP);
                const o = program.overrides[key] || { sets: base, reps: `${lo}-${hi}` };
                program.overrides[key] = { ...o, sets: clamp((o.sets || base) + add, 2, 6) };
            }
            else {
                program.slotBias = { ...(program.slotBias || {}), [key]: (program.slotBias?.[key] || 0) + add };
            }
        });
    });
}
// When a weekly schedule is set, the next workout follows the schedule's ORDER and is the day right
// after your last completed session — so a MISSED day is picked up next instead of being skipped
// (jumping to "today's" weekday slot would silently drop that day's volume). The sequence is the
// schedule's day ids in weekday order, de-duplicated; returns -1 when there's no usable schedule.
function nextScheduledIndex(days, schedule, lastDoneId) {
    if (!schedule || !Array.isArray(days) || !days.length)
        return -1;
    const seq = [];
    for (let wd = 0; wd < 7; wd++) {
        const id = schedule[wd];
        if (id && days.some(d => d.id === id) && !seq.includes(id))
            seq.push(id);
    }
    if (!seq.length)
        return -1;
    // No session logged for this program yet: don't guess from the calendar (whichever day the
    // auto-applied suggested schedule happens to place on the week's earliest weekday, per the
    // Sun→Sat scan above) — that's essentially arbitrary relative to the program's own day order and
    // routinely landed on a day mid-rotation (e.g. "Day 4 of 5") for a program you hadn't touched yet.
    // Return -1 so the caller falls back to the same day-order-0 default used when there's no schedule
    // at all, matching the plain "start at Day 1" expectation for something you haven't started.
    if (!lastDoneId)
        return -1;
    const pos = seq.indexOf(lastDoneId);
    const nextId = pos >= 0 ? seq[(pos + 1) % seq.length] : seq[0];
    return days.findIndex(d => d.id === nextId);
}
// The day a program should open to: the next one due given your history — the day after your last
// completed session (schedule order when a schedule is set, otherwise the program's day order), so
// the program view lands on what's next rather than always the first day.
function nextDueDayId(program, history) {
    if (!program || !Array.isArray(program.days) || !program.days.length)
        return undefined;
    const entries = (history || []).filter(h => h.programId === program.id);
    // Same resolution as nextSessionCursor — this decides which day the PROGRAM VIEW opens to, so a
    // stale id here lands the lifter on the wrong day even when the cursor itself is right.
    const lastIdx = entries.length ? historyDayIndex(program.days, entries[0]) : -1;
    const lastDoneId = lastIdx >= 0 ? program.days[lastIdx].id : (entries.length ? entries[0].dayId : null);
    const si = nextScheduledIndex(program.days, program.schedule, lastDoneId);
    if (si >= 0)
        return program.days[si].id;
    const idx = lastIdx >= 0 ? (lastIdx + 1) % program.days.length : 0;
    return program.days[idx].id;
}
// Which saved program is "up next" on the home screen. Precedence, highest first:
//   1. pinnedId — a program the user explicitly created/activated (this is the authority; without it,
//      a freshly saved program would lose to whatever was trained last — the "up next shows my old
//      workout" bug),
//   2. the most recently trained program (history is newest-first),
//   3. the active block of an in-progress cycle,
//   4. the newest-created program.
// Pure and side-effect-free so it can be unit-tested and reasoned about on its own.
function pickActiveProgram(saved, history, cycles, pinnedId) {
    if (!Array.isArray(saved) || !saved.length)
        return null;
    if (pinnedId) {
        const p = saved.find(s => s.id === pinnedId);
        if (p)
            return p;
    }
    for (const h of (history || [])) {
        const p = saved.find(s => s.id === h.programId);
        if (p)
            return p;
    }
    const liveCycle = (cycles || []).find(c => !c.done && Array.isArray(c.blockIds) && c.blockIds.length);
    if (liveCycle) {
        const p = saved.find(s => s.id === liveCycle.blockIds[liveCycle.activeBlock || 0]);
        if (p)
            return p;
    }
    return saved.slice().sort((a, b) => b.createdAt - a.createdAt)[0] || null;
}
// The next session's position within a program, from its history: which day is due and which week.
// dayIndex is the day AFTER the most recent logged session FOR THIS PROGRAM (schedule order when a
// schedule is set, otherwise program-day order), wrapping at the end of the rotation; weekIndex
// advances once per full rotation and wraps (including the deload week) at the program length. A
// program with no history of its own starts at day 1, week 1 — so a freshly activated program does
// NOT inherit the day cursor of whatever you trained last. Pure, so the cursor is unit-testable.
// Continuous ("endless") mode: has a lift's estimated 1RM stopped moving? Looks at the best e1RM per
// rotation across the program's main lifts over the last few rotations; a stall (no new best) is the
// cue to pull a recovery deload forward instead of waiting for the fixed cadence. Pure + conservative
// (only consulted once a few accumulation weeks are in), so it nudges rather than thrashes.
function endlessStalled(program, entries, dpw) {
    const mains = new Set((program.days || []).map(d => d.exercises?.[d.primaryIndex]).filter(Boolean));
    if (!mains.size)
        return false;
    const rots = [[], [], []];
    entries.forEach((h, i) => { const r = Math.floor(i / dpw); if (r < 3)
        rots[r].push(h); });
    const bestOf = (rot) => {
        let best = 0;
        rot.forEach(h => Object.entries(h.perf || {}).forEach(([id, p]) => {
            if (!mains.has(id) || !p.sets)
                return;
            p.sets.forEach(s => { if (s.w > 0 && s.r > 0)
                best = Math.max(best, e1rm(s.w, s.r)); });
        }));
        return best;
    };
    const [r0, r1, r2] = [bestOf(rots[0]), bestOf(rots[1]), bestOf(rots[2])];
    if (!(r0 > 0 && r1 > 0 && r2 > 0))
        return false; // need 3 full rotations of data
    return r0 <= Math.max(r1, r2) + 1e-6; // newest rotation set no new best → stalled
}
/* WHICH DAY WAS THIS SESSION PERFORMED ON — by id, then by label. Every logged entry stores the
   `dayId` it ran on, and before day ids became deterministic (see `days` below) a regeneration threw
   fresh ids at a program and detached its entire history in one step. Making ids stable stops that
   happening again, but it does NOTHING for logs already on a lifter's phone: those carry the old
   random ids and would stay orphaned forever.
   So resolution falls back to the day LABEL, which history has always stored alongside the id and
   which is the identity a lifter actually means ("Pull B"). That reattaches existing logs at read
   time on every load path, with no migration to run, nothing to get half-applied, and no risk of
   rewriting a store on boot. Labels are not guaranteed unique the way ids are — a program can carry
   two days with the same label — so the id is always tried first and the label only rescues an entry
   the id could not place. */
/* ⚠ WHOSE HISTORY IS THIS? ONE OWNER, BECAUSE THE ANSWER IS NOT OBVIOUS AND WAS GOT WRONG.
 *
 * HIS REPORT: "I created a new program and it's showing days of it are already completed."
 * Reproduced — a brand-new upper/lower program came up with 2 of its 4 days ticked.
 *
 * THE CAUSE IS TWO CORRECT DECISIONS MEETING. v578 made day ids DETERMINISTIC, so every upper/lower
 * program the app builds names its days `upper_a`, `lower_a`, `upper_b`, `lower_b` — stable per split
 * BY DESIGN, which is exactly what lets a regenerated program keep its history. And the plan tab's
 * completion memo read the WHOLE history with no notion of which program an entry belonged to. So
 * sessions logged against last month's upper/lower marked this morning's new one as half finished.
 * The label fallback below would have done it even without the id collision: the labels match too.
 *
 * ⚠⚠ THE OBVIOUS FIX IS THE ONE v578 EXISTS TO PREVENT. Scoping by programId blanks every completed
 * tick the moment a program is regenerated — precisely the regression v578 fixed — and it is safe
 * only because `handleRegenerate` does `p.id = program.id`, so a regenerated program keeps its
 * identity. That dependency is load-bearing; gates/dayscope.mjs check 3 pins it.
 *
 * An entry with NO programId predates attribution and is KEPT, not dropped — the v584 rule that a
 * normaliser refuses when it cannot tell rather than guessing wrong. Dropping it would blank the
 * ticks of anyone with old logs, the same defect facing the other way.
 *
 * A named owner rather than an inline filter, because this is the third reader of history to need
 * the question answered and the first two answered it differently: `blockPlanFor` requires an exact
 * programId match (correct — it counts this program's sessions), while `recentDayPerf` deliberately
 * reads ACROSS programs (also correct — a load reference is about the lifter, not the plan).
 * Completion is program-scoped; performance is lifter-scoped. Whoever writes the fourth reader
 * should have to pick one deliberately. */
function historyForProgram(history, program) {
    const id = program?.id;
    return (Array.isArray(history) ? history : []).filter(h => h && (!h.programId || !id || h.programId === id));
}
function historyDayIndex(days, h) {
    if (!h)
        return -1;
    const byId = h.dayId ? (days || []).findIndex(d => d.id ===
```

## history/perf/volume candidate 8

offset: 530310

```js
    const [lo, hi] = repRange(cfg.goal, EX_BY_ID[survId], isP);
                const base = baseSetsFor(cfg, EX_BY_ID[survId], isP);
                const o = program.overrides[key] || { sets: base, reps: `${lo}-${hi}` };
                program.overrides[key] = { ...o, sets: clamp((o.sets || base) + add, 2, 6) };
            }
            else {
                program.slotBias = { ...(program.slotBias || {}), [key]: (program.slotBias?.[key] || 0) + add };
            }
        });
    });
}
// When a weekly schedule is set, the next workout follows the schedule's ORDER and is the day right
// after your last completed session — so a MISSED day is picked up next instead of being skipped
// (jumping to "today's" weekday slot would silently drop that day's volume). The sequence is the
// schedule's day ids in weekday order, de-duplicated; returns -1 when there's no usable schedule.
function nextScheduledIndex(days, schedule, lastDoneId) {
    if (!schedule || !Array.isArray(days) || !days.length)
        return -1;
    const seq = [];
    for (let wd = 0; wd < 7; wd++) {
        const id = schedule[wd];
        if (id && days.some(d => d.id === id) && !seq.includes(id))
            seq.push(id);
    }
    if (!seq.length)
        return -1;
    // No session logged for this program yet: don't guess from the calendar (whichever day the
    // auto-applied suggested schedule happens to place on the week's earliest weekday, per the
    // Sun→Sat scan above) — that's essentially arbitrary relative to the program's own day order and
    // routinely landed on a day mid-rotation (e.g. "Day 4 of 5") for a program you hadn't touched yet.
    // Return -1 so the caller falls back to the same day-order-0 default used when there's no schedule
    // at all, matching the plain "start at Day 1" expectation for something you haven't started.
    if (!lastDoneId)
        return -1;
    const pos = seq.indexOf(lastDoneId);
    const nextId = pos >= 0 ? seq[(pos + 1) % seq.length] : seq[0];
    return days.findIndex(d => d.id === nextId);
}
// The day a program should open to: the next one due given your history — the day after your last
// completed session (schedule order when a schedule is set, otherwise the program's day order), so
// the program view lands on what's next rather than always the first day.
function nextDueDayId(program, history) {
    if (!program || !Array.isArray(program.days) || !program.days.length)
        return undefined;
    const entries = (history || []).filter(h => h.programId === program.id);
    // Same resolution as nextSessionCursor — this decides which day the PROGRAM VIEW opens to, so a
    // stale id here lands the lifter on the wrong day even when the cursor itself is right.
    const lastIdx = entries.length ? historyDayIndex(program.days, entries[0]) : -1;
    const lastDoneId = lastIdx >= 0 ? program.days[lastIdx].id : (entries.length ? entries[0].dayId : null);
    const si = nextScheduledIndex(program.days, program.schedule, lastDoneId);
    if (si >= 0)
        return program.days[si].id;
    const idx = lastIdx >= 0 ? (lastIdx + 1) % program.days.length : 0;
    return program.days[idx].id;
}
// Which saved program is "up next" on the home screen. Precedence, highest first:
//   1. pinnedId — a program the user explicitly created/activated (this is the authority; without it,
//      a freshly saved program would lose to whatever was trained last — the "up next shows my old
//      workout" bug),
//   2. the most recently trained program (history is newest-first),
//   3. the active block of an in-progress cycle,
//   4. the newest-created program.
// Pure and side-effect-free so it can be unit-tested and reasoned about on its own.
function pickActiveProgram(saved, history, cycles, pinnedId) {
    if (!Array.isArray(saved) || !saved.length)
        return null;
    if (pinnedId) {
        const p = saved.find(s => s.id === pinnedId);
        if (p)
            return p;
    }
    for (const h of (history || [])) {
        const p = saved.find(s => s.id === h.programId);
        if (p)
            return p;
    }
    const liveCycle = (cycles || []).find(c => !c.done && Array.isArray(c.blockIds) && c.blockIds.length);
    if (liveCycle) {
        const p = saved.find(s => s.id === liveCycle.blockIds[liveCycle.activeBlock || 0]);
        if (p)
            return p;
    }
    return saved.slice().sort((a, b) => b.createdAt - a.createdAt)[0] || null;
}
// The next session's position within a program, from its history: which day is due and which week.
// dayIndex is the day AFTER the most recent logged session FOR THIS PROGRAM (schedule order when a
// schedule is set, otherwise program-day order), wrapping at the end of the rotation; weekIndex
// advances once per full rotation and wraps (including the deload week) at the program length. A
// program with no history of its own starts at day 1, week 1 — so a freshly activated program does
// NOT inherit the day cursor of whatever you trained last. Pure, so the cursor is unit-testable.
// Continuous ("endless") mode: has a lift's estimated 1RM stopped moving? Looks at the best e1RM per
// rotation across the program's main lifts over the last few rotations; a stall (no new best) is the
// cue to pull a recovery deload forward instead of waiting for the fixed cadence. Pure + conservative
// (only consulted once a few accumulation weeks are in), so it nudges rather than thrashes.
function endlessStalled(program, entries, dpw) {
    const mains = new Set((program.days || []).map(d => d.exercises?.[d.primaryIndex]).filter(Boolean));
    if (!mains.size)
        return false;
    const rots = [[], [], []];
    entries.forEach((h, i) => { const r = Math.floor(i / dpw); if (r < 3)
        rots[r].push(h); });
    const bestOf = (rot) => {
        let best = 0;
        rot.forEach(h => Object.entries(h.perf || {}).forEach(([id, p]) => {
            if (!mains.has(id) || !p.sets)
                return;
            p.sets.forEach(s => { if (s.w > 0 && s.r > 0)
                best = Math.max(best, e1rm(s.w, s.r)); });
        }));
        return best;
    };
    const [r0, r1, r2] = [bestOf(rots[0]), bestOf(rots[1]), bestOf(rots[2])];
    if (!(r0 > 0 && r1 > 0 && r2 > 0))
        return false; // need 3 full rotations of data
    return r0 <= Math.max(r1, r2) + 1e-6; // newest rotation set no new best → stalled
}
/* WHICH DAY WAS THIS SESSION PERFORMED ON — by id, then by label. Every logged entry stores the
   `dayId` it ran on, and before day ids became deterministic (see `days` below) a regeneration threw
   fresh ids at a program and detached its entire history in one step. Making ids stable stops that
   happening again, but it does NOTHING for logs already on a lifter's phone: those carry the old
   random ids and would stay orphaned forever.
   So resolution falls back to the day LABEL, which history has always stored alongside the id and
   which is the identity a lifter actually means ("Pull B"). That reattaches existing logs at read
   time on every load path, with no migration to run, nothing to get half-applied, and no risk of
   rewriting a store on boot. Labels are not guaranteed unique the way ids are — a program can carry
   two days with the same label — so the id is always tried first and the label only rescues an entry
   the id could not place. */
/* ⚠ WHOSE HISTORY IS THIS? ONE OWNER, BECAUSE THE ANSWER IS NOT OBVIOUS AND WAS GOT WRONG.
 *
 * HIS REPORT: "I created a new program and it's showing days of it are already completed."
 * Reproduced — a brand-new upper/lower program came up with 2 of its 4 days ticked.
 *
 * THE CAUSE IS TWO CORRECT DECISIONS MEETING. v578 made day ids DETERMINISTIC, so every upper/lower
 * program the app builds names its days `upper_a`, `lower_a`, `upper_b`, `lower_b` — stable per split
 * BY DESIGN, which is exactly what lets a regenerated program keep its history. And the plan tab's
 * completion memo read the WHOLE history with no notion of which program an entry belonged to. So
 * sessions logged against last month's upper/lower marked this morning's new one as half finished.
 * The label fallback below would have done it even without the id collision: the labels match too.
 *
 * ⚠⚠ THE OBVIOUS FIX IS THE ONE v578 EXISTS TO PREVENT. Scoping by programId blanks every completed
 * tick the moment a program is regenerated — precisely the regression v578 fixed — and it is safe
 * only because `handleRegenerate` does `p.id = program.id`, so a regenerated program keeps its
 * identity. That dependency is load-bearing; gates/dayscope.mjs check 3 pins it.
 *
 * An entry with NO programId predates attribution and is KEPT, not dropped — the v584 rule that a
 * normaliser refuses when it cannot tell rather than guessing wrong. Dropping it would blank the
 * ticks of anyone with old logs, the same defect facing the other way.
 *
 * A named owner rather than an inline filter, because this is the third reader of history to need
 * the question answered and the first two answered it differently: `blockPlanFor` requires an exact
 * programId match (correct — it counts this program's sessions), while `recentDayPerf` deliberately
 * reads ACROSS programs (also correct — a load reference is about the lifter, not the plan).
 * Completion is program-scoped; performance is lifter-scoped. Whoever writes the fourth reader
 * should have to pick one deliberately. */
function historyForProgram(history, program) {
    const id = program?.id;
    return (Array.isArray(history) ? history : []).filter(h => h && (!h.programId || !id || h.programId === id));
}
function historyDayIndex(days, h) {
    if (!h)
        return -1;
    const byId = h.dayId ? (days || []).findIndex(d => d.id === h.dayId) : -1;
    if (byId >= 0)
        return byId;
    if (!h.dayLabel)
        return -1;
    return (days || []).findIndex(d => d.label === h.dayLabel);
}
/* ONE OWNER FOR HISTORY→DAY IDENTITY, applied at load rather than at ever
```

## history/perf/volume candidate 9

offset: 530830

```js
dule is set, the next workout follows the schedule's ORDER and is the day right
// after your last completed session — so a MISSED day is picked up next instead of being skipped
// (jumping to "today's" weekday slot would silently drop that day's volume). The sequence is the
// schedule's day ids in weekday order, de-duplicated; returns -1 when there's no usable schedule.
function nextScheduledIndex(days, schedule, lastDoneId) {
    if (!schedule || !Array.isArray(days) || !days.length)
        return -1;
    const seq = [];
    for (let wd = 0; wd < 7; wd++) {
        const id = schedule[wd];
        if (id && days.some(d => d.id === id) && !seq.includes(id))
            seq.push(id);
    }
    if (!seq.length)
        return -1;
    // No session logged for this program yet: don't guess from the calendar (whichever day the
    // auto-applied suggested schedule happens to place on the week's earliest weekday, per the
    // Sun→Sat scan above) — that's essentially arbitrary relative to the program's own day order and
    // routinely landed on a day mid-rotation (e.g. "Day 4 of 5") for a program you hadn't touched yet.
    // Return -1 so the caller falls back to the same day-order-0 default used when there's no schedule
    // at all, matching the plain "start at Day 1" expectation for something you haven't started.
    if (!lastDoneId)
        return -1;
    const pos = seq.indexOf(lastDoneId);
    const nextId = pos >= 0 ? seq[(pos + 1) % seq.length] : seq[0];
    return days.findIndex(d => d.id === nextId);
}
// The day a program should open to: the next one due given your history — the day after your last
// completed session (schedule order when a schedule is set, otherwise the program's day order), so
// the program view lands on what's next rather than always the first day.
function nextDueDayId(program, history) {
    if (!program || !Array.isArray(program.days) || !program.days.length)
        return undefined;
    const entries = (history || []).filter(h => h.programId === program.id);
    // Same resolution as nextSessionCursor — this decides which day the PROGRAM VIEW opens to, so a
    // stale id here lands the lifter on the wrong day even when the cursor itself is right.
    const lastIdx = entries.length ? historyDayIndex(program.days, entries[0]) : -1;
    const lastDoneId = lastIdx >= 0 ? program.days[lastIdx].id : (entries.length ? entries[0].dayId : null);
    const si = nextScheduledIndex(program.days, program.schedule, lastDoneId);
    if (si >= 0)
        return program.days[si].id;
    const idx = lastIdx >= 0 ? (lastIdx + 1) % program.days.length : 0;
    return program.days[idx].id;
}
// Which saved program is "up next" on the home screen. Precedence, highest first:
//   1. pinnedId — a program the user explicitly created/activated (this is the authority; without it,
//      a freshly saved program would lose to whatever was trained last — the "up next shows my old
//      workout" bug),
//   2. the most recently trained program (history is newest-first),
//   3. the active block of an in-progress cycle,
//   4. the newest-created program.
// Pure and side-effect-free so it can be unit-tested and reasoned about on its own.
function pickActiveProgram(saved, history, cycles, pinnedId) {
    if (!Array.isArray(saved) || !saved.length)
        return null;
    if (pinnedId) {
        const p = saved.find(s => s.id === pinnedId);
        if (p)
            return p;
    }
    for (const h of (history || [])) {
        const p = saved.find(s => s.id === h.programId);
        if (p)
            return p;
    }
    const liveCycle = (cycles || []).find(c => !c.done && Array.isArray(c.blockIds) && c.blockIds.length);
    if (liveCycle) {
        const p = saved.find(s => s.id === liveCycle.blockIds[liveCycle.activeBlock || 0]);
        if (p)
            return p;
    }
    return saved.slice().sort((a, b) => b.createdAt - a.createdAt)[0] || null;
}
// The next session's position within a program, from its history: which day is due and which week.
// dayIndex is the day AFTER the most recent logged session FOR THIS PROGRAM (schedule order when a
// schedule is set, otherwise program-day order), wrapping at the end of the rotation; weekIndex
// advances once per full rotation and wraps (including the deload week) at the program length. A
// program with no history of its own starts at day 1, week 1 — so a freshly activated program does
// NOT inherit the day cursor of whatever you trained last. Pure, so the cursor is unit-testable.
// Continuous ("endless") mode: has a lift's estimated 1RM stopped moving? Looks at the best e1RM per
// rotation across the program's main lifts over the last few rotations; a stall (no new best) is the
// cue to pull a recovery deload forward instead of waiting for the fixed cadence. Pure + conservative
// (only consulted once a few accumulation weeks are in), so it nudges rather than thrashes.
function endlessStalled(program, entries, dpw) {
    const mains = new Set((program.days || []).map(d => d.exercises?.[d.primaryIndex]).filter(Boolean));
    if (!mains.size)
        return false;
    const rots = [[], [], []];
    entries.forEach((h, i) => { const r = Math.floor(i / dpw); if (r < 3)
        rots[r].push(h); });
    const bestOf = (rot) => {
        let best = 0;
        rot.forEach(h => Object.entries(h.perf || {}).forEach(([id, p]) => {
            if (!mains.has(id) || !p.sets)
                return;
            p.sets.forEach(s => { if (s.w > 0 && s.r > 0)
                best = Math.max(best, e1rm(s.w, s.r)); });
        }));
        return best;
    };
    const [r0, r1, r2] = [bestOf(rots[0]), bestOf(rots[1]), bestOf(rots[2])];
    if (!(r0 > 0 && r1 > 0 && r2 > 0))
        return false; // need 3 full rotations of data
    return r0 <= Math.max(r1, r2) + 1e-6; // newest rotation set no new best → stalled
}
/* WHICH DAY WAS THIS SESSION PERFORMED ON — by id, then by label. Every logged entry stores the
   `dayId` it ran on, and before day ids became deterministic (see `days` below) a regeneration threw
   fresh ids at a program and detached its entire history in one step. Making ids stable stops that
   happening again, but it does NOTHING for logs already on a lifter's phone: those carry the old
   random ids and would stay orphaned forever.
   So resolution falls back to the day LABEL, which history has always stored alongside the id and
   which is the identity a lifter actually means ("Pull B"). That reattaches existing logs at read
   time on every load path, with no migration to run, nothing to get half-applied, and no risk of
   rewriting a store on boot. Labels are not guaranteed unique the way ids are — a program can carry
   two days with the same label — so the id is always tried first and the label only rescues an entry
   the id could not place. */
/* ⚠ WHOSE HISTORY IS THIS? ONE OWNER, BECAUSE THE ANSWER IS NOT OBVIOUS AND WAS GOT WRONG.
 *
 * HIS REPORT: "I created a new program and it's showing days of it are already completed."
 * Reproduced — a brand-new upper/lower program came up with 2 of its 4 days ticked.
 *
 * THE CAUSE IS TWO CORRECT DECISIONS MEETING. v578 made day ids DETERMINISTIC, so every upper/lower
 * program the app builds names its days `upper_a`, `lower_a`, `upper_b`, `lower_b` — stable per split
 * BY DESIGN, which is exactly what lets a regenerated program keep its history. And the plan tab's
 * completion memo read the WHOLE history with no notion of which program an entry belonged to. So
 * sessions logged against last month's upper/lower marked this morning's new one as half finished.
 * The label fallback below would have done it even without the id collision: the labels match too.
 *
 * ⚠⚠ THE OBVIOUS FIX IS THE ONE v578 EXISTS TO PREVENT. Scoping by programId blanks every completed
 * tick the moment a program is regenerated — precisely the regression v578 fixed — and it is safe
 * only because `handleRegenerate` does `p.id = program.id`, so a regenerated program keeps its
 * identity. That dependency is load-bearing; gates/dayscope.mjs check 3 pins it.
 *
 * An entry with NO programId predates attribution and is KEPT, not dropped — the v584 rule that a
 * normaliser refuses when it cannot tell rather than guessing wrong. Dropping it would blank the
 * ticks of anyone with old logs, the same defect facing the other way.
 *
 * A named owner rather than an inline filter, because this is the third reader of history to need
 * the question answered and the first two answered it differently: `blockPlanFor` requires an exact
 * programId match (correct — it counts this program's sessions), while `recentDayPerf` deliberately
 * reads ACROSS programs (also correct — a load reference is about the lifter, not the plan).
 * Completion is program-scoped; performance is lifter-scoped. Whoever writes the fourth reader
 * should have to pick one deliberately. */
function historyForProgram(history, program) {
    const id = program?.id;
    return (Array.isArray(history) ? history : []).filter(h => h && (!h.programId || !id || h.programId === id));
}
function historyDayIndex(days, h) {
    if (!h)
        return -1;
    const byId = h.dayId ? (days || []).findIndex(d => d.id === h.dayId) : -1;
    if (byId >= 0)
        return byId;
    if (!h.dayLabel)
        return -1;
    return (days || []).findIndex(d => d.label === h.dayLabel);
}
/* ONE OWNER FOR HISTORY→DAY IDENTITY, applied at load rather than at every reader.
 *
 * v578 made day ids deterministic and added `historyDayIndex` so pre-v578 logs — which carry random
 * ids matching nothing — could still be placed by their day LABEL. That was the right resolution but
 * the wrong SHAPE: a runtime resolver has to be adopted by every reader, and SEVEN read history by
 * dayId. v578 converted three (the rotation cursor, the schedule lookup, the plan tab's completed
 * markers) and left the rest, so `recentDayPerf` silently dropped legacy sessions from its per-day
 * an
```

## history/perf/volume candidate 10

offset: 539295

```js
er of history to need
 * the question answered and the first two answered it differently: `blockPlanFor` requires an exact
 * programId match (correct — it counts this program's sessions), while `recentDayPerf` deliberately
 * reads ACROSS programs (also correct — a load reference is about the lifter, not the plan).
 * Completion is program-scoped; performance is lifter-scoped. Whoever writes the fourth reader
 * should have to pick one deliberately. */
function historyForProgram(history, program) {
    const id = program?.id;
    return (Array.isArray(history) ? history : []).filter(h => h && (!h.programId || !id || h.programId === id));
}
function historyDayIndex(days, h) {
    if (!h)
        return -1;
    const byId = h.dayId ? (days || []).findIndex(d => d.id === h.dayId) : -1;
    if (byId >= 0)
        return byId;
    if (!h.dayLabel)
        return -1;
    return (days || []).findIndex(d => d.label === h.dayLabel);
}
/* ONE OWNER FOR HISTORY→DAY IDENTITY, applied at load rather than at every reader.
 *
 * v578 made day ids deterministic and added `historyDayIndex` so pre-v578 logs — which carry random
 * ids matching nothing — could still be placed by their day LABEL. That was the right resolution but
 * the wrong SHAPE: a runtime resolver has to be adopted by every reader, and SEVEN read history by
 * dayId. v578 converted three (the rotation cursor, the schedule lookup, the plan tab's completed
 * markers) and left the rest, so `recentDayPerf` silently dropped legacy sessions from its per-day
 * anchor, the session-length calibrator skipped them, and plateau scoping survived only because it
 * happens to fall back when a lift has been trained on a single day. That is precisely the failure
 * Haiden pointed at in the self-test: a rule updated in one place and not the others.
 *
 * So the resolution happens ONCE, at the boundary, exactly where `normalizeGyms` already does the
 * same job for gyms. After this runs, every reader — including ones nobody has written yet — sees a
 * dayId that resolves, and none of them needs to know legacy ids ever existed.
 *
 * IN MEMORY, NOT WRITTEN BACK. The store is left untouched; this shapes what the app holds. A boot
 * that rewrites the user's saved data to fix a display problem can only ever make a bad situation
 * worse if it is wrong, and normalising is cheap enough to redo on every load.
 *
 * CONSERVATIVE BY CONSTRUCTION. An entry is rewritten ONLY when its id matches no day of its own
 * program AND its label matches exactly one. Ambiguous (two days share a label) or unresolvable
 * entries are left exactly as they are — a wrong reattachment is worse than none, because it moves a
 * logged session onto a day the lifter never trained. */
/* ⚠ AN ORPHANED `cycleId` RENDERS AS A CYCLE THAT ISN'T THERE, AND THE UI SAYS NOTHING.
 *
 * HIS REPORT: "the training cycles on the home page glitch out. Phases can become separated from the
 * cycle. On mine there's a Training Cycle named 'Training Cycle' that when you click on it it just
 * takes you to the all training cycles page."
 *
 * Both halves are one cause. `doDeleteCycle(id, alsoBlocks=false)` removed the cycle record and left
 * every block's `cycleId` pointing at it. Nothing cleared it and nothing repaired it on load, so:
 *   · the library groups the orphans by their dead id and titles the card from
 *     `(c && c.name) || "Training cycle"` — the fallback fires BECAUSE the record is gone, which is
 *     the nameless cycle he is looking at;
 *   · tapping it routes to `cycleDetail`, `cycles.find(...)` returns undefined, and the view falls
 *     back to `CyclesView` — the all-cycles page, silently;
 *   · the blocks keep `cycleIndex` and `blockLabel`, so they still present as phases of nothing.
 * The import path makes orphans too: it drops any cycle whose `blockIds` don't survive the import
 * while keeping the programs.
 *
 * RESOLVED ONCE, AT THE BOUNDARY — the v584 shape, the same job `normalizeGyms` and
 * `normalizeHistoryDayIds` already do here. A runtime resolver would have to be adopted by every
 * reader (the library group, the home card, cycle propagation, the switcher, block review), and the
 * v578 half-conversion is exactly what that costs. Every reader, INCLUDING ONES NOT YET WRITTEN,
 * sees a cycleId that resolves or no cycleId at all.
 *
 * CONSERVATIVE, deliberately: a link is cleared ONLY when the id matches no cycle in the store at
 * all. A program whose cycle exists is never touched, however stale its other fields look —
 * detaching a block from a live cycle would lose real structure, and that is the worse error.
 * Returns the SAME array when nothing is orphaned, so a boot with clean data is a no-op. */
function normalizeCycleLinks(saved, cycles) {
    if (!Array.isArray(saved) || !saved.length)
        return Array.isArray(saved) ? saved : [];
    const live = new Set((Array.isArray(cycles) ? cycles : []).map(c => c && c.id).filter(Boolean));
    let touched = false;
    const out = saved.map(p => {
        if (!p || !p.cycleId || live.has(p.cycleId))
            return p;
        touched = true;
        const { cycleId, cycleIndex, blockLabel, ...rest } = p;
        return rest;
    });
    return touched ? out : saved;
}
function normalizeHistoryDayIds(history, saved) {
    if (!Array.isArray(history) || !history.length || !Array.isArray(saved) || !saved.length)
        return history || [];
    const byProgram = new Map(saved.filter(p => p && p.id).map(p => [p.id, p]));
    let changed = false;
    const out = history.map(h => {
        if (!h || !h.dayId || !h.dayLabel)
            return h;
        const prog = byProgram.get(h.programId);
        if (!prog || !Array.isArray(prog.days))
            return h;
        if (prog.days.some(d => d.id === h.dayId))
            return h; // already resolves
        const matches = prog.days.filter(d => d.label === h.dayLabel);
        if (matches.length !== 1)
            return h; // unresolvable or ambiguous
        changed = true;
        return { ...h, dayId: matches[0].id };
    });
    return changed ? out : history;
}
function nextSessionCursor(program, history) {
    const days = program.days || [];
    const dpw = days.length || 1;
    const entries = (history || []).filter(h => h.programId === program.id);
    const done = entries.length;
    const lastDoneId = entries.length ? entries[0].dayId : null;
    // Resolved, not matched raw: a pre-v578 entry carries a random dayId that no longer exists, and
    // reading -1 here is what silently restarted the rotation and re-served the day just trained.
    const lastIdx = entries.length ? historyDayIndex(days, entries[0]) : -1;
    let dayIndex = lastIdx >= 0 ? (lastIdx + 1) % dpw : 0;
    let scheduled = false;
    /* The SCHEDULE lookup needs the resolved id too. Fixing only `lastIdx` above left this line reading
       the raw stored id, so a reattached legacy entry advanced the plain rotation correctly and was then
       overridden by a schedule lookup that still could not place it — landing on the wrong day anyway.
       Caught by the reattachment check; a fix that stops one reader consulting a stale id has to follow
       that id everywhere it flows. */
    const resolvedLastId = lastIdx >= 0 ? days[lastIdx].id : lastDoneId;
    const si = nextScheduledIndex(days, program.schedule, resolvedLastId);
    if (si >= 0) {
        dayIndex = si;
        scheduled = true;
    }
    if (program.config?.endless) {
        // CONTINUOUS MODE — no fixed block, never "completes". Runs a rolling accumulation → deload wave
        // forever: load and volume autoregulate every session, and a light deload rotation is inserted
        // automatically when accumulation has run its course (a soft cap) OR a main lift stalls, whichever
        // comes first. Deloads are detected straight from history (a session logged at the deload sentinel
        // week, weekIndex > cap), so the cadence self-corrects even when a stall pulls one in early.
        const cap = weeksOf(program); // accumulation weeks before an auto-deload
        const isDeloadEntry = (h) => h.weekIndex != null && h.weekIndex > cap;
        let headDeloads = 0;
        for (const h of entries) {
            if (isDeloadEntry(h))
                headDeloads++;
            else
                break;
        }
        // Mid deload rotation → keep the rest of the rotation light until every day has been deloaded.
        if (headDeloads > 0 && headDeloads < dpw) {
            return { dayIndex, weekIndex: cap + 1, done, scheduled, lastIdx, endless: true, accumWk: 0, deload: true };
        }
        // Otherwise count the accumulation sessions since the last deload and place us in the wave.
        let sinceDeload = 0;
        for (const h of entries) {
            if (isDeloadEntry(h))
                break;
            sinceDeload++;
        }
        const accumWk = Math.floor(sinceDeload / dpw) + 1;
        const stalled = accumWk >= 3 && endlessStalled(program, entries, dpw);
        const deloadDue = accumWk > cap || stalled;
        const weekIndex = deloadDue ? cap + 1 : Math.min(accumWk, cap);
        return { dayIndex, weekIndex, done, scheduled, lastIdx, endless: true, accumWk: deloadDue ? 0 : accumWk, deload: deloadDue, stalled };
    }
    const maxWeek = Math.max(1, weeksOf(program) + (program.config?.deload ? 1 : 0));
    /* A FINITE PROGRAM ADVANCES WHEN ITS DISTINCT SCHEDULED DAYS ARE DONE — not merely after N history
       rows exist. `done / daysPerWeek` was subtly wrong because repeating a day, restoring a duplicated
       history entry, or logging the same planned day twice counted as two steps through the block. That
       can jump Program View into week 2/3/a deload even though a day in the current week was never
       completed; since later weeks can intentionally carry fewer sets, it looks exactly like the program
       "dropped a lot of sets". New history rows already carry both weekIndex and
```

## history/perf/volume candidate 11

offset: 540565

```js
has to be adopted by every reader, and SEVEN read history by
 * dayId. v578 converted three (the rotation cursor, the schedule lookup, the plan tab's completed
 * markers) and left the rest, so `recentDayPerf` silently dropped legacy sessions from its per-day
 * anchor, the session-length calibrator skipped them, and plateau scoping survived only because it
 * happens to fall back when a lift has been trained on a single day. That is precisely the failure
 * Haiden pointed at in the self-test: a rule updated in one place and not the others.
 *
 * So the resolution happens ONCE, at the boundary, exactly where `normalizeGyms` already does the
 * same job for gyms. After this runs, every reader — including ones nobody has written yet — sees a
 * dayId that resolves, and none of them needs to know legacy ids ever existed.
 *
 * IN MEMORY, NOT WRITTEN BACK. The store is left untouched; this shapes what the app holds. A boot
 * that rewrites the user's saved data to fix a display problem can only ever make a bad situation
 * worse if it is wrong, and normalising is cheap enough to redo on every load.
 *
 * CONSERVATIVE BY CONSTRUCTION. An entry is rewritten ONLY when its id matches no day of its own
 * program AND its label matches exactly one. Ambiguous (two days share a label) or unresolvable
 * entries are left exactly as they are — a wrong reattachment is worse than none, because it moves a
 * logged session onto a day the lifter never trained. */
/* ⚠ AN ORPHANED `cycleId` RENDERS AS A CYCLE THAT ISN'T THERE, AND THE UI SAYS NOTHING.
 *
 * HIS REPORT: "the training cycles on the home page glitch out. Phases can become separated from the
 * cycle. On mine there's a Training Cycle named 'Training Cycle' that when you click on it it just
 * takes you to the all training cycles page."
 *
 * Both halves are one cause. `doDeleteCycle(id, alsoBlocks=false)` removed the cycle record and left
 * every block's `cycleId` pointing at it. Nothing cleared it and nothing repaired it on load, so:
 *   · the library groups the orphans by their dead id and titles the card from
 *     `(c && c.name) || "Training cycle"` — the fallback fires BECAUSE the record is gone, which is
 *     the nameless cycle he is looking at;
 *   · tapping it routes to `cycleDetail`, `cycles.find(...)` returns undefined, and the view falls
 *     back to `CyclesView` — the all-cycles page, silently;
 *   · the blocks keep `cycleIndex` and `blockLabel`, so they still present as phases of nothing.
 * The import path makes orphans too: it drops any cycle whose `blockIds` don't survive the import
 * while keeping the programs.
 *
 * RESOLVED ONCE, AT THE BOUNDARY — the v584 shape, the same job `normalizeGyms` and
 * `normalizeHistoryDayIds` already do here. A runtime resolver would have to be adopted by every
 * reader (the library group, the home card, cycle propagation, the switcher, block review), and the
 * v578 half-conversion is exactly what that costs. Every reader, INCLUDING ONES NOT YET WRITTEN,
 * sees a cycleId that resolves or no cycleId at all.
 *
 * CONSERVATIVE, deliberately: a link is cleared ONLY when the id matches no cycle in the store at
 * all. A program whose cycle exists is never touched, however stale its other fields look —
 * detaching a block from a live cycle would lose real structure, and that is the worse error.
 * Returns the SAME array when nothing is orphaned, so a boot with clean data is a no-op. */
function normalizeCycleLinks(saved, cycles) {
    if (!Array.isArray(saved) || !saved.length)
        return Array.isArray(saved) ? saved : [];
    const live = new Set((Array.isArray(cycles) ? cycles : []).map(c => c && c.id).filter(Boolean));
    let touched = false;
    const out = saved.map(p => {
        if (!p || !p.cycleId || live.has(p.cycleId))
            return p;
        touched = true;
        const { cycleId, cycleIndex, blockLabel, ...rest } = p;
        return rest;
    });
    return touched ? out : saved;
}
function normalizeHistoryDayIds(history, saved) {
    if (!Array.isArray(history) || !history.length || !Array.isArray(saved) || !saved.length)
        return history || [];
    const byProgram = new Map(saved.filter(p => p && p.id).map(p => [p.id, p]));
    let changed = false;
    const out = history.map(h => {
        if (!h || !h.dayId || !h.dayLabel)
            return h;
        const prog = byProgram.get(h.programId);
        if (!prog || !Array.isArray(prog.days))
            return h;
        if (prog.days.some(d => d.id === h.dayId))
            return h; // already resolves
        const matches = prog.days.filter(d => d.label === h.dayLabel);
        if (matches.length !== 1)
            return h; // unresolvable or ambiguous
        changed = true;
        return { ...h, dayId: matches[0].id };
    });
    return changed ? out : history;
}
function nextSessionCursor(program, history) {
    const days = program.days || [];
    const dpw = days.length || 1;
    const entries = (history || []).filter(h => h.programId === program.id);
    const done = entries.length;
    const lastDoneId = entries.length ? entries[0].dayId : null;
    // Resolved, not matched raw: a pre-v578 entry carries a random dayId that no longer exists, and
    // reading -1 here is what silently restarted the rotation and re-served the day just trained.
    const lastIdx = entries.length ? historyDayIndex(days, entries[0]) : -1;
    let dayIndex = lastIdx >= 0 ? (lastIdx + 1) % dpw : 0;
    let scheduled = false;
    /* The SCHEDULE lookup needs the resolved id too. Fixing only `lastIdx` above left this line reading
       the raw stored id, so a reattached legacy entry advanced the plain rotation correctly and was then
       overridden by a schedule lookup that still could not place it — landing on the wrong day anyway.
       Caught by the reattachment check; a fix that stops one reader consulting a stale id has to follow
       that id everywhere it flows. */
    const resolvedLastId = lastIdx >= 0 ? days[lastIdx].id : lastDoneId;
    const si = nextScheduledIndex(days, program.schedule, resolvedLastId);
    if (si >= 0) {
        dayIndex = si;
        scheduled = true;
    }
    if (program.config?.endless) {
        // CONTINUOUS MODE — no fixed block, never "completes". Runs a rolling accumulation → deload wave
        // forever: load and volume autoregulate every session, and a light deload rotation is inserted
        // automatically when accumulation has run its course (a soft cap) OR a main lift stalls, whichever
        // comes first. Deloads are detected straight from history (a session logged at the deload sentinel
        // week, weekIndex > cap), so the cadence self-corrects even when a stall pulls one in early.
        const cap = weeksOf(program); // accumulation weeks before an auto-deload
        const isDeloadEntry = (h) => h.weekIndex != null && h.weekIndex > cap;
        let headDeloads = 0;
        for (const h of entries) {
            if (isDeloadEntry(h))
                headDeloads++;
            else
                break;
        }
        // Mid deload rotation → keep the rest of the rotation light until every day has been deloaded.
        if (headDeloads > 0 && headDeloads < dpw) {
            return { dayIndex, weekIndex: cap + 1, done, scheduled, lastIdx, endless: true, accumWk: 0, deload: true };
        }
        // Otherwise count the accumulation sessions since the last deload and place us in the wave.
        let sinceDeload = 0;
        for (const h of entries) {
            if (isDeloadEntry(h))
                break;
            sinceDeload++;
        }
        const accumWk = Math.floor(sinceDeload / dpw) + 1;
        const stalled = accumWk >= 3 && endlessStalled(program, entries, dpw);
        const deloadDue = accumWk > cap || stalled;
        const weekIndex = deloadDue ? cap + 1 : Math.min(accumWk, cap);
        return { dayIndex, weekIndex, done, scheduled, lastIdx, endless: true, accumWk: deloadDue ? 0 : accumWk, deload: deloadDue, stalled };
    }
    const maxWeek = Math.max(1, weeksOf(program) + (program.config?.deload ? 1 : 0));
    /* A FINITE PROGRAM ADVANCES WHEN ITS DISTINCT SCHEDULED DAYS ARE DONE — not merely after N history
       rows exist. `done / daysPerWeek` was subtly wrong because repeating a day, restoring a duplicated
       history entry, or logging the same planned day twice counted as two steps through the block. That
       can jump Program View into week 2/3/a deload even though a day in the current week was never
       completed; since later weeks can intentionally carry fewer sets, it looks exactly like the program
       "dropped a lot of sets". New history rows already carry both weekIndex and dayId, so during the
       first pass through a block we can use those facts instead of an inferred row count. Legacy/mixed
       history (missing either field), and post-block looping behavior, keep the old count fallback rather
       than guessing at data that cannot be reconstructed safely. */
    const firstPass = done < maxWeek * dpw;
    const explicit = firstPass && entries.length > 0 && entries.every(h => Number.isInteger(Number(h.weekIndex))
        && Number(h.weekIndex) >= 1 && Number(h.weekIndex) <= maxWeek && historyDayIndex(days, h) >= 0);
    if (explicit) {
        let weekIndex = 1;
        let completed = new Set();
        for (let w = 1; w <= maxWeek; w++) {
            completed = new Set(entries.filter(h => Number(h.weekIndex) === w).map(h => historyDayIndex(days, h)).filter(i => i >= 0));
            if (completed.size < dpw) {
                weekIndex = w;
                break;
            }
            if (w === maxWeek) {
                // Full first pass complete: preserve the historical wrap-to-week-1 behavior.
                weekIndex = 1;
                completed = new Set();
            }
        }
        if (completed.size < dpw) {
            // Keep rotation/schedule order, but never serve a day already com
```

## history/perf/volume candidate 12

offset: 540579

```js
ted by every reader, and SEVEN read history by
 * dayId. v578 converted three (the rotation cursor, the schedule lookup, the plan tab's completed
 * markers) and left the rest, so `recentDayPerf` silently dropped legacy sessions from its per-day
 * anchor, the session-length calibrator skipped them, and plateau scoping survived only because it
 * happens to fall back when a lift has been trained on a single day. That is precisely the failure
 * Haiden pointed at in the self-test: a rule updated in one place and not the others.
 *
 * So the resolution happens ONCE, at the boundary, exactly where `normalizeGyms` already does the
 * same job for gyms. After this runs, every reader — including ones nobody has written yet — sees a
 * dayId that resolves, and none of them needs to know legacy ids ever existed.
 *
 * IN MEMORY, NOT WRITTEN BACK. The store is left untouched; this shapes what the app holds. A boot
 * that rewrites the user's saved data to fix a display problem can only ever make a bad situation
 * worse if it is wrong, and normalising is cheap enough to redo on every load.
 *
 * CONSERVATIVE BY CONSTRUCTION. An entry is rewritten ONLY when its id matches no day of its own
 * program AND its label matches exactly one. Ambiguous (two days share a label) or unresolvable
 * entries are left exactly as they are — a wrong reattachment is worse than none, because it moves a
 * logged session onto a day the lifter never trained. */
/* ⚠ AN ORPHANED `cycleId` RENDERS AS A CYCLE THAT ISN'T THERE, AND THE UI SAYS NOTHING.
 *
 * HIS REPORT: "the training cycles on the home page glitch out. Phases can become separated from the
 * cycle. On mine there's a Training Cycle named 'Training Cycle' that when you click on it it just
 * takes you to the all training cycles page."
 *
 * Both halves are one cause. `doDeleteCycle(id, alsoBlocks=false)` removed the cycle record and left
 * every block's `cycleId` pointing at it. Nothing cleared it and nothing repaired it on load, so:
 *   · the library groups the orphans by their dead id and titles the card from
 *     `(c && c.name) || "Training cycle"` — the fallback fires BECAUSE the record is gone, which is
 *     the nameless cycle he is looking at;
 *   · tapping it routes to `cycleDetail`, `cycles.find(...)` returns undefined, and the view falls
 *     back to `CyclesView` — the all-cycles page, silently;
 *   · the blocks keep `cycleIndex` and `blockLabel`, so they still present as phases of nothing.
 * The import path makes orphans too: it drops any cycle whose `blockIds` don't survive the import
 * while keeping the programs.
 *
 * RESOLVED ONCE, AT THE BOUNDARY — the v584 shape, the same job `normalizeGyms` and
 * `normalizeHistoryDayIds` already do here. A runtime resolver would have to be adopted by every
 * reader (the library group, the home card, cycle propagation, the switcher, block review), and the
 * v578 half-conversion is exactly what that costs. Every reader, INCLUDING ONES NOT YET WRITTEN,
 * sees a cycleId that resolves or no cycleId at all.
 *
 * CONSERVATIVE, deliberately: a link is cleared ONLY when the id matches no cycle in the store at
 * all. A program whose cycle exists is never touched, however stale its other fields look —
 * detaching a block from a live cycle would lose real structure, and that is the worse error.
 * Returns the SAME array when nothing is orphaned, so a boot with clean data is a no-op. */
function normalizeCycleLinks(saved, cycles) {
    if (!Array.isArray(saved) || !saved.length)
        return Array.isArray(saved) ? saved : [];
    const live = new Set((Array.isArray(cycles) ? cycles : []).map(c => c && c.id).filter(Boolean));
    let touched = false;
    const out = saved.map(p => {
        if (!p || !p.cycleId || live.has(p.cycleId))
            return p;
        touched = true;
        const { cycleId, cycleIndex, blockLabel, ...rest } = p;
        return rest;
    });
    return touched ? out : saved;
}
function normalizeHistoryDayIds(history, saved) {
    if (!Array.isArray(history) || !history.length || !Array.isArray(saved) || !saved.length)
        return history || [];
    const byProgram = new Map(saved.filter(p => p && p.id).map(p => [p.id, p]));
    let changed = false;
    const out = history.map(h => {
        if (!h || !h.dayId || !h.dayLabel)
            return h;
        const prog = byProgram.get(h.programId);
        if (!prog || !Array.isArray(prog.days))
            return h;
        if (prog.days.some(d => d.id === h.dayId))
            return h; // already resolves
        const matches = prog.days.filter(d => d.label === h.dayLabel);
        if (matches.length !== 1)
            return h; // unresolvable or ambiguous
        changed = true;
        return { ...h, dayId: matches[0].id };
    });
    return changed ? out : history;
}
function nextSessionCursor(program, history) {
    const days = program.days || [];
    const dpw = days.length || 1;
    const entries = (history || []).filter(h => h.programId === program.id);
    const done = entries.length;
    const lastDoneId = entries.length ? entries[0].dayId : null;
    // Resolved, not matched raw: a pre-v578 entry carries a random dayId that no longer exists, and
    // reading -1 here is what silently restarted the rotation and re-served the day just trained.
    const lastIdx = entries.length ? historyDayIndex(days, entries[0]) : -1;
    let dayIndex = lastIdx >= 0 ? (lastIdx + 1) % dpw : 0;
    let scheduled = false;
    /* The SCHEDULE lookup needs the resolved id too. Fixing only `lastIdx` above left this line reading
       the raw stored id, so a reattached legacy entry advanced the plain rotation correctly and was then
       overridden by a schedule lookup that still could not place it — landing on the wrong day anyway.
       Caught by the reattachment check; a fix that stops one reader consulting a stale id has to follow
       that id everywhere it flows. */
    const resolvedLastId = lastIdx >= 0 ? days[lastIdx].id : lastDoneId;
    const si = nextScheduledIndex(days, program.schedule, resolvedLastId);
    if (si >= 0) {
        dayIndex = si;
        scheduled = true;
    }
    if (program.config?.endless) {
        // CONTINUOUS MODE — no fixed block, never "completes". Runs a rolling accumulation → deload wave
        // forever: load and volume autoregulate every session, and a light deload rotation is inserted
        // automatically when accumulation has run its course (a soft cap) OR a main lift stalls, whichever
        // comes first. Deloads are detected straight from history (a session logged at the deload sentinel
        // week, weekIndex > cap), so the cadence self-corrects even when a stall pulls one in early.
        const cap = weeksOf(program); // accumulation weeks before an auto-deload
        const isDeloadEntry = (h) => h.weekIndex != null && h.weekIndex > cap;
        let headDeloads = 0;
        for (const h of entries) {
            if (isDeloadEntry(h))
                headDeloads++;
            else
                break;
        }
        // Mid deload rotation → keep the rest of the rotation light until every day has been deloaded.
        if (headDeloads > 0 && headDeloads < dpw) {
            return { dayIndex, weekIndex: cap + 1, done, scheduled, lastIdx, endless: true, accumWk: 0, deload: true };
        }
        // Otherwise count the accumulation sessions since the last deload and place us in the wave.
        let sinceDeload = 0;
        for (const h of entries) {
            if (isDeloadEntry(h))
                break;
            sinceDeload++;
        }
        const accumWk = Math.floor(sinceDeload / dpw) + 1;
        const stalled = accumWk >= 3 && endlessStalled(program, entries, dpw);
        const deloadDue = accumWk > cap || stalled;
        const weekIndex = deloadDue ? cap + 1 : Math.min(accumWk, cap);
        return { dayIndex, weekIndex, done, scheduled, lastIdx, endless: true, accumWk: deloadDue ? 0 : accumWk, deload: deloadDue, stalled };
    }
    const maxWeek = Math.max(1, weeksOf(program) + (program.config?.deload ? 1 : 0));
    /* A FINITE PROGRAM ADVANCES WHEN ITS DISTINCT SCHEDULED DAYS ARE DONE — not merely after N history
       rows exist. `done / daysPerWeek` was subtly wrong because repeating a day, restoring a duplicated
       history entry, or logging the same planned day twice counted as two steps through the block. That
       can jump Program View into week 2/3/a deload even though a day in the current week was never
       completed; since later weeks can intentionally carry fewer sets, it looks exactly like the program
       "dropped a lot of sets". New history rows already carry both weekIndex and dayId, so during the
       first pass through a block we can use those facts instead of an inferred row count. Legacy/mixed
       history (missing either field), and post-block looping behavior, keep the old count fallback rather
       than guessing at data that cannot be reconstructed safely. */
    const firstPass = done < maxWeek * dpw;
    const explicit = firstPass && entries.length > 0 && entries.every(h => Number.isInteger(Number(h.weekIndex))
        && Number(h.weekIndex) >= 1 && Number(h.weekIndex) <= maxWeek && historyDayIndex(days, h) >= 0);
    if (explicit) {
        let weekIndex = 1;
        let completed = new Set();
        for (let w = 1; w <= maxWeek; w++) {
            completed = new Set(entries.filter(h => Number(h.weekIndex) === w).map(h => historyDayIndex(days, h)).filter(i => i >= 0));
            if (completed.size < dpw) {
                weekIndex = w;
                break;
            }
            if (w === maxWeek) {
                // Full first pass complete: preserve the historical wrap-to-week-1 behavior.
                weekIndex = 1;
                completed = new Set();
            }
        }
        if (completed.size < dpw) {
            // Keep rotation/schedule order, but never serve a day already completed in this
```
