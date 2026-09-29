# M166 session-volume candidates

## Candidate 1 — score 21 — offset 716092

```js
ctly (isolation/accessory,
        // highest slot index), keeping a floor of 2 working sets.
        let target = null, targetRank = -Infinity;
        program.days.forEach(day => day.exercises.forEach((id, slot) => {
            const ex = EX_BY_ID[id];
            if (!ex || ex.part !== worst)
                return;
            const key = `${day.id}:${slot}`;
            const cur = Number(computeCell({ ...program, slotBias: bias }, day, id, slot, peak).sets) || 0;
            if (cur <= 2)
                return; // never below 2 working sets
            const rank = (ex.type === "isolation" ? 100 : 0) + slot; // prefer trimming accessories & later slots
            if (rank > targetRank) {
                targetRank = rank;
                target = key;
            }
        }));
        if (!target)
            break;
        bias[target] = (bias[target] || 0) - 1;
    }
    if (Object.keys(bias).length)
        program.slotBias = bias;
}
/* ============================== CYCLES (sequential block periodization) ==============================
   A Cycle is a thin ordered container of 2–4 full programs ("blocks"), each a complete mesocycle
   with its own goal / week count / volume emphasis. This is true Issurin-style sequencing
   (hypertrophy block → strength block → peak block) layered ON TOP of the existing program model
   rather than replacing it: every block is a normal program produced by generateProgram, so it
   renders, logs, exports and progresses with zero new plumbing, and history (keyed by programId)
   gives per-block logs and cross-block charts for free.

   Weight carryover is automatic: openers come from the per-exercise e1RM in the `perf` store, which
   suggestWeight already reads — so block 2 starts from what you actually hit in block 1. One seed is
   threaded through every block so exercise SELECTION stays coherent across blocks (same bench
   variation, same row), which is also what makes carryover land on the right lifts.

   Design defaults (both configurable, both reversible without a migration):
     • advance = "manual"  — you tap "complete block" when the weeks are done (forgiving of missed
       sessions); "auto" date-advance can read block.startedAt + weeks later without touching this.
     • onComplete = "end"  — the cycle finishes and sits in history; "loop" calls regenerateCycle to
       roll fresh blocks at the new e1RMs for indefinite long-term periodization. */
// Each block = a goal + a week count + a volume tilt. `vol` nudges baseSetsFor-style emphasis via
// the existing focus/reduce levers so a hypertrophy block genuinely carries more sets than a peak.
const CYCLE_TEMPLATES = [
    {
        id: "powerbuilding", name: "Powerbuilding", tag: "Size → strength → peak",
        blurb: "Build muscle, convert it to strength, then express it. The classic 3-block progression.",
        blocks: [
            { goal: "hypertrophy", weeks: 5, label: "Hypertrophy", note: "High volume, 8–15 reps. Bank size." }
```

## Candidate 2 — score 23 — offset 971633

```js
ART_LABEL[part] || part });
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
      
```

## Candidate 3 — score 25 — offset 1075271

```js
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
// muscleRecovery / lifterModel memos). Every current caller reads the map withou
```

## Candidate 4 — score 23 — offset 1250558

```js
up → hi again.
        //
        // Entry-point detection: we want the wave to start at hi (volume day) the first
        // time, not immediately trigger a load-up from lo reps.
        //
        // Reliable detection: if p.sets contains ONLY lo-or-below reps (no mid/hi reps
        // across any logged set in this session), AND history shows no prior mid/hi-rep
        // session for this exercise, this is a fresh entry → start at hi.
        const lastReps = (p.sets?.length ? p.sets[p.sets.length - 1].r : p.reps) || hi;
        const hasPriorWaveContext = history && history.length > 0
            ? history.some(h => {
                const hp = h.perf?.[ex.id];
                if (!hp)
                    return false;
                const hSets = hp.sets?.length ? hp.sets : [{ w: hp.weight, r: hp.reps }];
                return hSets.some(s => s.r > lo); // any prior session with mid/hi reps → in a wave
            })
            : (p.sets?.some(s => s.r > lo)); // no history but current session had hi reps at some point
        const isFreshEntry = lastReps <= lo && !hasPriorWaveContext;
        if (isFreshEntry) {
            // No wave context yet — start the wave at the volume day (hi reps, same weight)
            return { weight: pw, dir: "hold", reason: `Starting wave — aim for ${hi} reps this session`, waveTarget: hi, last: lastSummary };
        }
        // Wave target: cycle through hi → mid → lo → step-up to hi at new load
        let waveReps, waveRIR, waveNote;
        if (lastReps <= lo) {
            // Completed the wave on the heavy day → reset to the volume day (hi reps) at a load the
            // lifter's CURRENT e1RM supports for that many reps. We must NOT floor this at the heavy
            // day's weight + a step: the heavy day was a low-rep (e.g. 5-rep) load, and forcing the
            // hi-rep volume day to sit at-or-above it prescribes an impossible weight (you can't do 10
            // reps at more than your recent 5-rep weight). The e1RM target already bakes in the
            // strength gained across the wave, so progressive overload carries forward correctly.
            const e1rmTarget = loadFor(hi);
            const w = Math.max(step, Math.min(roundTo(pw * 1.12, step), roundTo(e1rmTarget, step)));
            // dir reflects what the load actually does. A wave reset usually DROPS the weight (the heavy
            // day's load can't carry into a 10-rep volume day), and labelling a 10kg drop "hold" while
            // delta says −10 hands the UI two contradicting facts about one suggestion. The reason string
            // is what carries the "this drop is planned, not a regression" message.
            const dir = w > pw + 1e-6 ? "up" : (w < pw - 1e-6 ? "down" : "hold");
            return { weight: w, dir, delta: w - pw, reason: `Wave complete — reset to ${hi} reps`, last: lastSummary };
        }
        else if (lastReps >= hi) {
            // Was at top (volume day) → step to mid-range
      
```

## Candidate 5 — score 23 — offset 1487674

```js
solation",
                progressionStyle: "auto", ...(nextExerciseId ? { nextExerciseId } : {})
            };
            const nextWeekPrescriptions = { ...(p.nextWeekPrescriptions || {}) };
            nextWeekPrescriptions[key] = buildUserAddedSlotPrescriptions({ repRange: ex?.rep, weeks: weeksOf(p), compound: ex?.type === "compound" });
            return { ...p, days, overrides, nextWeekPrescriptions, edited: true };
        });
    };
    // muscles that still have an available, unused exercise for a given day
    const addablePartsFor = (dayId) => {
        const day = program.days.find(d => d.id === dayId);
        const used = new Set(day.exercises);
        return PART_ORDER.filter(part => availableFor(part, equipSet, banned, !!program.config.noBodyweight).some(e => !used.has(e.id)));
    };
    const activeWeek = weekIndex;
    // Which day the lifter should train next (rotation- and schedule-aware), and whether they're looking
    // at their live week — used to badge the up-next day so a mid-program view is immediately actionable.
    const nextDayId = nextDueDayId(program, history);
    const viewingCurrentWeek = activeWeek === currentTrainingWeek();
    // weeklyVolume/weeklySubVolume iterate every day × exercise × set (and run computeCell); blockPlanFor
    // walks the mesocycle. Memoize so week navigation and unrelated state changes don't recompute them.
    const wkVol = useMemo(() => weeklyVolume(program, activeWeek), [program, activeWeek]);
    const wkSubVol = useMemo(() => weeklySubVolume(program, activeWeek), [program, activeWeek]);
    const blockPlan = useMemo(() => blockPlanFor(program), [program]);
    // Most recent logged performance per day → exercise (history is newest-first), for inline
    // "what to do next" arrows. Falls back to global perf for lifts never trained on this day.
    const recentDayPerf = useMemo(() => {
        const m = {};
        (history || []).forEach(h => {
            if (!h.dayId || !h.perf)
                return;
            const d = (m[h.dayId] = m[h.dayId] || {});
            // reps != null (not weight > 0) so assisted (negative) and bodyweight (0) lifts are tracked
            // per-day too, instead of being skipped and falling back to a cross-day value.
            Object.keys(h.perf).forEach(id => { if (!d[id] && h.perf[id]?.reps != null)
                d[id] = h.perf[id]; });
        });
        return m;
    }, [history]);
    const trendById = useMemo(() => Object.fromEntries(exerciseTrends(history).map(t => [t.id, t])), [history]);
    // Per-day trends for the stall chip: built once per day rather than per exercise row.
    const trendByDayEx = useMemo(() => {
        /* Per-day scoping needs every session to know which day it was. Entries written before `dayId`
           existed cannot be attributed, so they drop out of every per-day trend — and a lifter whose
           whole history predates the field would silently get no stall detection at all. If NOTHING in
   
```

## Candidate 6 — score 29 — offset 1938926

```js
 — a new session always changes the length and the newest id.
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
   ⚠ IT IS PART OF THE MEMO KEY. The cache is content-fingerp
```

## Candidate 7 — score 25 — offset 1940940

```js
ss/progression views cannot keep serving the pre-edit model. */
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
            
```

## Candidate 8 — score 20 — offset 1951050

```js
of the user's own training.
 *
 * The honesty constraints are the design:
 *   - identifiability first: it returns null without ≥8 paired weeks AND ≥6 sets of spread between
 *     the lightest and heaviest weeks. Someone who always does 12 sets has no dose-response in their
 *     data — the only honest answer is silence, not a landmark dressed up as a finding;
 *   - e1RM per week is the flat-Epley max across the muscle's lifts (standardized scale — trends,
 *     not prescriptions); gains are per-week deltas, median-aggregated within terciles so one
 *     outlier week can't mint a conclusion;
 *   - the verdict only names a best tercile when its median gain beats the runner-up by a real
 *     margin (≥0.15%/wk of the muscle's e1RM) — near-ties say "no clear difference in your data",
 *     which is itself useful (it means volume isn't your limiting variable right now);
 *   - confidence scales with paired weeks and is reported with the claim.
 * Memoized on history identity like its siblings. Group landmarks remain the prior everywhere; this
 * only ever ADDS a personal observation on top, it never silently replaces the defaults.
 */
const __doseMemo = new WeakMap();
function volumeResponse(history, part) {
    if (!history || !part)
        return null;
    let byPart = __doseMemo.get(history);
    if (!byPart) {
        byPart = new Map();
        __doseMemo.set(history, byPart);
    }
    if (byPart.has(part))
        return byPart.get(part);
    const WEEK = 7 * 86400000;
    const weeks = new Map(); // weekIndex -> { sets, e1: best flat-Epley e1RM }
    for (const h of history) {
        if (!h || !h.date)
            continue;
        const wk = Math.floor(h.date / WEEK);
        let rec = weeks.get(wk);
        if (!rec) {
            rec = { sets: 0, e1: 0 };
            weeks.set(wk, rec);
        }
        for (const [id, pf] of Object.entries(h.perf || {})) {
            const ex = EX_BY_ID[id];
            if (!ex || ex.part !== part)
                continue;
            const ss = (pf.sets && pf.sets.length) ? pf.sets : [{ w: pf.weight, r: pf.reps }];
            for (const t of ss) {
                if (!(t.w > 0) || !(t.r > 0) || t.warm)
                    continue;
                rec.sets++;
                const e = e1rmRIR(t.w, t.r, 0);
                if (e > rec.e1)
                    rec.e1 = e;
            }
        }
    }
    const idx = [...weeks.keys()].sort((a, b) => a - b);
    // Pair over TWO-WEEK windows, not single weeks. A week of honest training moves e1RM by well under
    // 1%, while the measurement moves in quanta — a 2.5kg plate at 80% is ~2%, one rep at the same load
    // ~2.5% — so week-over-week deltas are mostly zeros punctuated by rounding ticks, and the terciles
    // all median to nothing. Two-week windows put the signal at or above the instrument's resolution
    // while volume attribution stays clean (the window's own average weekly sets). Halves the sample
    // count, which the ident
```

## Candidate 9 — score 21 — offset 1973622

```js
      (finishSession)
     percent  training max, projected per block      (startNextBlock)
     generic  nothing of its own — double progression, rep ladder and the per-set dynamic variants
              all read last session's sets back out of the perf store.
   Adding a scheme means adding one entry here. */
const PROG_POLICIES = Object.freeze({ next: Object.freeze({ id: "pursuit-next" }) });
// M46: the live workout has one prescription owner. This badge is descriptive only; it never
// dispatches to an alternate progression implementation.
function policyIdFor(program, day, ex, slot, weekIndex, perf, history) {
    void perf;
    void history;
    const cell = computeCell(program, day, ex.id, slot, weekIndex);
    return `next:${cell?.progressionStyle || "auto"}`;
}
/* ---- MYO-REP MINI-SETS ------------------------------------------------------------------------
 *
 * When the block prescribes myo-reps, it said so in a CUE — "Last set: myo-reps — to failure, then
 * mini-sets of a few reps with brief rests" — and then gave the lifter nowhere to put them. The set
 * list ended at the last working set, so the app asked for work it would not let you log: the reps
 * vanish from volume, from the e1RM estimate, and from every trend that reads history. A prescription
 * you cannot record is not a prescription, it is a suggestion the app then forgets.
 *
 * So the mini-sets become real rows. Three of them: the standard myo-rep protocol is a burst of a few
 * reps after a ~15s pause, repeated until the reps fall off, and three is where most people land — it
 * is a starting point, and rows can be deleted or added like any other. Flagged `myo: true`, which the
 * rest timer ALREADY understands (it gives myo and drop sub-sets a short pause rather than parking the
 * lifter on a 90-180s working-set clock), and which the row-seeding and progression logic already skip
 * so they can't drag the working-set ladder around.
 *
 * Same load as the activation set, since a myo-rep does not strip weight — that is a drop set, which
 * is a different technique the app handles separately. Reps default low and are meant to be edited to
 * what actually happened. */
/* HOW MANY MINI-SETS A MYO-REP IS, AND WHEN IT STOPS.
 *
 * Haiden: "Myoreps should be 3-5 total sets or until you can't hit the rep range anymore, whichever
 * comes sooner." That is the technique as written — the activation set takes you to failure, then you
 * keep taking short-rest minis while you can still produce the target reps, and you stop the moment
 * you cannot. A FIXED count of three was wrong in both directions: it cut a lifter off who still had
 * two good minis in them, and it kept asking for a fourth-rep mini from someone who had already
 * failed at two.
 *
 * So three rows are PRESCRIBED (the floor, always shown), and a further row is appended — up to five
 * — only once the last one is logged AT OR ABOVE the floor of the mini range. Logging below the floor
 * appends nothing, which i
```

## Candidate 10 — score 27 — offset 2010047

```js
ble" findings that were entirely its own doing. An auditor must not
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
    // suggestWeight's e1RM 
```

## Candidate 11 — score 21 — offset 2088320

```js
: r[0]]; })(); /* "8" is a range of one, not [8, NaN] */
                if (a > 0 && b >= a)
                    return [a, b];
            }
            return cellRepRange(swapCell, program, newEx, isP);
        })();
        const sug = null; // M46: never invoke the removed legacy load engine for an ad-hoc swap.
        const keepWarm = data[ei].sets.some(s => s.warm);
        const snap = data, snapIdx = exIdx;
        setData(d => d.map((e, i) => i !== ei ? e : { id: newId, slot, sets: buildSets(newEx, slot, sug, keepWarm), note: "" }));
        setSwap(false);
        if (scope === "program" && onUpdateProgramExercise) {
            onUpdateProgramExercise(day.id, oldId, newId);
            flashSnack(`Swapped in ${newEx.name} · updated program`, snap, snapIdx);
        }
        else {
            flashSnack(`Swapped in ${newEx.name}`, snap, snapIdx);
        }
    };
    const finish = () => {
        // Guard against a fast double-tap: setView/setSessionDay below are React state updates and don't
        // synchronously unmount this button, so without this a second tap before the re-render could call
        // onFinish twice — a duplicate history entry, doubled logged volume, and doubled PRs/achievements
        // for one real workout. A ref (not state) so the block is effective on the very next call, not
        // after a re-render.
        if (finishingRef.current)
            return;
        finishingRef.current = true;
        try {
            localStorage.removeItem(LIVE_KEY);
        }
        catch { } // workout completed → discard the resume snapshot
        buzz("success");
        const perfOut = {};
        data.forEach(e => {
            const s = summarizeSets(e.sets, EX_BY_ID[e.id], unit);
            const note = e.note.trim() || (perf[e.id] && perf[e.id].note) || undefined;
            if (s) {
                const doneWork = e.sets.filter(x => x.done && !x.warm && parseInt(x.reps) > 0);
                const hasPos = doneWork.some(x => parseFloat(x.weight) > 0);
                const wv = x => { const w = parseFloat(x.weight); return isNaN(w) ? 0 : w; };
                // Persist the amrap flag from the prescription onto each logged set. Percent-scheme
                // progression (5/3/1 TM projection, GZCLP stage advancement) needs to identify the
                // AMRAP set directly rather than inferring it from being the unique top-weight set —
                // that heuristic silently breaks if a manually-edited weight ties with another set.
                // RIR: prefer the explicitly-logged actual RIR; otherwise fall back to the set's target
                // RIR so the LAST TIME column still shows the intended effort (most users just tick the
                // set done without tapping an RIR, which previously left the history with no effort at all).
                const setRIR = (x) => {
                    if (x.actualRIR != null)
                        return x.actualRIR;
                    c
```

## Candidate 12 — score 25 — offset 2092397

```js
reader downstream already filters on it
                    // (`!s.warm && !s.sub`), but the flag was never PERSISTED, so on replayed history those
                    // filters matched nothing and extensions counted as full sets all over again — the exact
                    // bug v493 fixed in the live session, surviving in the log.
                    return { w: wv(x), r: parseInt(x.reps), ...(r != null ? { rir: r } : {}), ...(x.sub ? { sub: true, ...(x.kind ? { kind: x.kind } : {}) } : {}), ...(prescribedRIRof(x) != null ? { tr: prescribedRIRof(x) } : {}), ...(x.target?.amrap ? { amrap: true } : {}), ...(x.auto && pw > 0 ? { pw } : {}), ...(x.auto && pt ? { pt } : {}) };
                });
                perfOut[e.id] = { weight: s.weight, reps: s.reps, date: Date.now(), sets: logged, note };
            }
        });
        onFinish({
            programId: program.id, programName: program.name, dayLabel: day.label, dayId: day.id,
            engineV: program.engineV || 1, // which engine issued this session's prescriptions
            weekIndex, // the block week this session belongs to — lets the program view mark a day done
            setsDone: doneSets, totalSets, volume: Math.round(volume), unit,
            durationMin: Math.max(1, Math.round(runElapsedMs() / 60000)),
            /* The estimate this session was SOLD with, stored beside what it actually took. Pace can then
               be measured even after the program is deleted or edited, which is when the comparison would
               otherwise silently stop working. */
            estMin: (() => { try {
                return estimateMinutes(program, day, weekIndex);
            }
            catch {
                return undefined;
            } })(),
            perf: perfOut,
            feedback,
            feedbackRaw: (() => { const r = {}; new Set([...Object.keys(fbPump), ...Object.keys(fbSore)]).forEach(p => { r[p] = { pump: fbPump[p], sore: fbSore[p] }; }); return Object.keys(r).length ? r : undefined; })()
        });
    };
    const elapsed = Math.max(0, Math.floor(runElapsedMs() / 1000));
    const fmtEl = `${Math.floor(elapsed / 3600)}:${String(Math.floor((elapsed % 3600) / 60)).padStart(2, "0")}:${String(elapsed % 60).padStart(2, "0")}`;
    return (_jsxs("div", { ref: workoutRoot, className: "wpb-page-shell wpb-workout", "data-keyboard-open": keyboardOpen ? "true" : "false", style: { position: "relative", maxHeight: viewportHeight || undefined }, children: [_jsxs("div", { className: "wpb-workout-header", style: { padding: "14px 14px 10px", borderBottom: `1px solid ${C.borderSoft}` }, children: [_jsxs("div", { style: { display: "flex", alignItems: "center", gap: 8, minWidth: 0 }, children: [_jsx("button", { onClick: () => setConfirmExit(true), "aria-label": "Leave workout", className: "pressable hit wpb-workout-close", style: { width: 40, height: 40, background: "none", border: "none", color: C.text, cursor: "pointer", padding: 0, flexShrink: 0, displa
```

## Candidate 13 — score 21 — offset 2242151

```js
ne && isWorkSet(s) && parseFloat(s.weight) > 0);
                        if (!work.length)
                            return null;
                        const bestNow = Math.max(...work.map(s => parseFloat(s.weight)));
                        if (bestNow < goal)
                            return null;
                        let priorBest = 0;
                        (history || []).forEach(h => (h.perf?.[e.id]?.sets || []).forEach(s => { if (s.w > priorBest)
                            priorBest = s.w; }));
                        return priorBest < goal ? { name: EX_BY_ID[e.id].name, goal } : null; // newly reached
                    }).filter(Boolean);
                    // Estimated 1RM from the main lift's top set (AMRAP) — most meaningful on percentage programs
                    const mainE1rm = (() => {
                        const mainId = day.exercises[day.primaryIndex];
                        const e = data.find(x => x.id === mainId);
                        if (!e)
                            return null;
                        // !bbb stays on top of isWorkSet: a 5/3/1 Boring-But-Big back-off is a genuine working set
                        // (it counts for volume) but it is not the top set this readout is reporting.
                        const work = e.sets.filter(s => s.done && isWorkSet(s) && !s.bbb && parseFloat(s.weight) > 0 && parseInt(s.reps) > 0);
                        if (!work.length)
                            return null;
                        const top = work.reduce((a, b) => e1rm(parseFloat(b.weight), parseInt(b.reps)) > e1rm(parseFloat(a.weight), parseInt(a.reps)) ? b : a);
                        let prevBest = 0;
                        (history || []).forEach(h => (h.perf?.[mainId]?.sets || []).forEach(s => { const v = e1rm(s.w, s.r); if (v > prevBest)
                            prevBest = v; }));
                        return { name: EX_BY_ID[mainId]?.name, weight: parseFloat(top.weight), reps: parseInt(top.reps), est: Math.round(e1rm(parseFloat(top.weight), parseInt(top.reps))), prev: Math.round(prevBest) };
                    })();
                    // Next-session load plan: run the same cross-session engine that sets next time's opening
                    // weight, so the lifter sees exactly what they earned (step up / hold & build / ease off).
                    // Percentage schemes (5/3/1, Madcow, nSuns) progress via training-max waves, not this.
                    const nextPlan = data.map(e => {
                        const ex = EX_BY_ID[e.id];
                        if (!ex)
                            return null;
                        const s = summarizeSets(e.sets, ex, unit);
                        if (!s || !(s.weight > 0))
                            return null;
                        const logged = e.sets.filter(x => x.done && !x.warm && !x.sub && parseFloat(x.weight) > 0 && parseInt(x.reps) > 0)
                            .map(x => ({ w: parseFloat(x.weight), r: p
```

## Candidate 14 — score 24 — offset 2301436

```js
     return;
        const est = e1rmRIR(s.w, s.r, loggedRIRof(s), repCap, slope); // logged rating > prescribed-at-the-time > ASSUMED_RIR
        if (est > e1) {
            e1 = est;
            bestSet = s;
        }
        num += est * s.w;
        den += s.w;
        n++;
    });
    return { e1: den > 0 ? num / den : e1, bestSet, n };
}
/* WHAT COUNTS AS A WORKING SET. Warm-ups are not work; drop sets and myo mini-sets are extensions of
   the set above, not sets of their own. The rule is three words long and has now been re-typed
   inline more than thirty times, which is why half of those sites forgot the second half of it and
   counted a 4-rep myo mini as a full set (v493), and why the flag those sites test was not even
   being saved to history until v496. One predicate, one place. Tolerates both shapes: live session
   sets carry `weight`/`reps` as strings, logged history sets carry `w`/`r` as numbers. */
function isWorkSet(s) { return !!s && !s.warm && !s.sub; }
function workSetsOf(p) { return setsOf(p).filter(s => isWorkSet(s) && s.w > 0); }
/* Per-exercise PR breakdown for the detail view's Records tab: heaviest single set, best estimated
   1RM, biggest single-set volume (w×reps), and the best weight ever hit at each rep count — the
   "rep maxes" table lifters use to gauge progress at a given intensity. Pure read over history. */
/* `unit` is the lifter's CURRENT display unit, used only as the fallback for entries that carry no
   unit tag of their own. It used to fall back to a hard-coded "kg", so a lb lifter with an imported
   or legacy entry saw their own PRs relabelled in a unit they have never trained in — 225 lb shown
   as 225 kg. finish() has stamped `unit` for a long time, so fresh sessions were never affected;
   this is the same class as the missing-`date` and missing-`volume` entries already fixed, all of
   them arriving through a restore of arbitrary JSON that migrateStore deliberately keeps rather than
   drops. The entry's own tag still wins wherever it exists — a lift recorded in kg must keep reading
   kg after a switch to lb, because a record of what you lifted is not a display preference. */
function exRecords(history, id, unit = "kg") {
    let maxWeight = null, maxE1rm = null, maxVol = null;
    const perRep = {}; // reps → { w, date }
    (Array.isArray(history) ? history : []).forEach(h => {
        const p = h.perf?.[id];
        if (!(p && p.weight > 0))
            return;
        const u = h.unit || unit;
        const sets = setsOf(p);
        sets.forEach(s => {
            if (!(s.w > 0) || s.sub)
                return; // exclude sub-sets (myo-reps, drop sets) from records
            if (betterTopSet(maxWeight, { w: s.w, r: s.r }) !== maxWeight)
                maxWeight = { w: s.w, r: s.r, date: h.date, unit: u };
            if (s.r != null) {
                const e = e1rm(s.w, s.r);
                if (!maxE1rm || e > maxE1rm.v)
                    maxE1rm = { v: e, date: h.date, unit: u };
```

## Candidate 15 — score 21 — offset 2306028

```js
eek would stretch
 * the axis. Points carry their real dates so the chart can space them honestly. */
const EX_WINDOWS = [
    { id: "1W", label: "1W", days: 7 },
    { id: "1M", label: "1M", days: 30 },
    { id: "3M", label: "3M", days: 91 },
    { id: "6M", label: "6M", days: 182 },
    { id: "1Y", label: "1Y", days: 365 },
    { id: "all", label: "All", days: null },
];
const EX_METRICS = [
    { id: "e1rm", label: "1-RM", reps: 1, kind: "load", title: "Estimated 1-rep max", blurb: "Your best set each session, converted to a one-rep max — the cleanest single measure of strength." },
    { id: "e3rm", label: "3-RM", reps: 3, kind: "load", title: "Estimated 3-rep max", blurb: "What your best set implies you could hold for three reps. An estimate, not a set you performed." },
    { id: "e10rm", label: "10-RM", reps: 10, kind: "load", title: "Estimated 10-rep max", blurb: "What your best set implies you could hold for ten reps — the load your hypertrophy work sits near." },
    { id: "top", label: "Heaviest weight", kind: "load", title: "Heaviest weight", blurb: "The heaviest load you actually put in your hands each session. No estimate involved." },
    { id: "vol", label: "Total volume", kind: "vol", title: "Total volume", blurb: "Weight × reps across every working set of the session — how much work the lift did." },
    { id: "volset", label: "Volume per set", kind: "vol", title: "Volume per set", blurb: "Session volume divided by working sets — whether each set is doing more work, independent of how many you did." },
];
const EX_METRIC_BY_ID = Object.fromEntries(EX_METRICS.map(m => [m.id, m]));
const exWindow = id => EX_WINDOWS.find(w => w.id === id) || EX_WINDOWS[EX_WINDOWS.length - 1];
/* The inverse of e1rm(): the weight that formula implies you could hold for `reps`.
   AT reps <= 1 IT RETURNS THE ESTIMATE ITSELF. Epley is not self-consistent at one rep — feeding r=1
   into e1rm() gives w*(1+1/30), so blindly inverting produced a 1-RM tab reading 275.8 while the
   Records tab, reading the same session through prBest, said 285. Two numbers for one lift, four taps
   apart, and no way for the lifter to know which to believe. The 1-RM reading IS the estimate. */
function rmAt(est, reps) { const r = Math.min(Math.max(reps || 1, 1), E1RM_REP_CAP); return r <= 1 ? est : est / (1 + r / EPLEY_SLOPE); }
function exerciseSeries(history, id, metricId = "e1rm", windowId = "all", now = Date.now()) {
    const metric = EX_METRIC_BY_ID[metricId] || EX_METRICS[0];
    const win = exWindow(windowId);
    const since = win.days == null ? -Infinity : now - win.days * 86400000;
    const rows = [];
    let unit = null;
    for (const h of (Array.isArray(history) ? history : [])) {
        if (!h || typeof h !== "object" || !h.perf)
            continue;
        if (!(h.date >= since))
            continue;
        const p = h.perf[id];
        if (!p)
            continue;
        const sets = setsOf(p).filter(s => s && s.w > 0 && !s.warm && !s.sub);
     
```

## Candidate 16 — score 24 — offset 2367933

```js
use.est, lvl });
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
 * Three changes over the population vers
```

## Candidate 17 — score 21 — offset 2400302

```js
nts, a Banister fitness-fatigue model) was CUT. Not for effort or offline reasons — all of
   it computes fine on-device — but because the parameters are not IDENTIFIABLE from data this app
   can ever collect:

     · Volume landmarks need a dose-response curve, which needs multiple doses. Measured across the
       template sweep, weekly sets per muscle vary by a median of ONE set within a block, and 45% of
       muscle-blocks have zero variation at all. Worse, the volume nudge (volBias) is clamped to ±2
       and is itself computed FROM the performance it would have to explain — dose and response are
       endogenous, so any fitted curve is partly reading its own tail.
     · Recovery time constants need recovery observed at several different intervals. Every split
       retrains a muscle at ~2 distinct gaps, repeating weekly, forever. The design is rank-deficient.
       The only extra variation comes from skipped sessions, which are not random (people skip when
       sick, tired, or busy) — that biases the fit in an unknown direction rather than fixing it.
     · Banister/TSB was built for endurance, where load has a validated scalar. Resistance training
       has none; volume-load overweights light high-rep work and underweights heavy singles.

   Shipping confident numbers ("your chest MRV is 18 sets") on top of unidentifiable parameters is
   fabricated precision. So this is the honest version of what Phase 4 was reaching for: keep the
   volume tables as a PRIOR, and detect OVERREACH — which is identifiable, because it rests on
   signals the app observes directly and does not choose:

     1. Rep decay at CONSTANT LOAD across sessions. Same lift, same weight, fewer reps. No dose
        assumption, no fitted constant — just "you did 8, then 7, then 6 with the same bar."
     2. Effort inflation at constant load: the same work costing more reserve (RIR falling).
     3. Within-session set-to-set decay, steepening over time.

   These are the same class of signal that made Phase 2's RIR calibration work: variation the engine
   didn't manufacture. Everything below is a description of what happened, not a model of the lifter. */
// One lift's overreach picture, from constant-load comparisons only.
// Returns null when there isn't a clean comparison to make — which is most of the time, and saying
// nothing is the correct output when the data can't support a claim.
function constantLoadDecay(history, exId) {
    // Collect (date, weight, reps, rir) for the TOP working set of each session of this lift.
    const sess = [];
    for (let i = (history || []).length - 1; i >= 0; i--) { // oldest → newest
        const p = history[i]?.perf?.[exId];
        if (!p)
            continue;
        const sets = (p.sets && p.sets.length) ? p.sets : [{ w: p.weight, r: p.reps, rir: null }];
        let best = null;
        for (const s of sets) {
            const w = parseFloat(s.w != null ? s.w : s.weight), r = parseInt(s.r != null ? s.r : s.reps
```

## Candidate 18 — score 20 — offset 2477411

```js
: 4, borderRadius: 999, background: C.accent } })] }, ci));
                    })] }, wi))), selDay && (_jsxs("div", { style: { marginTop: 12, background: C.bg2, borderRadius: 12, padding: "11px 13px" }, children: [_jsx("div", { style: { fontSize: 13, fontWeight: 700, color: C.text, marginBottom: 6 }, children: selDay.date.toLocaleDateString(undefined, { weekday: "long", month: "short", day: "numeric" }) }), selDay.sessions.map((h, i) => {
                        const hUnit = h.unit || "";
                        const exers = Object.entries(h.perf || {}).map(([id, p]) => ({ id, ex: EX_BY_ID[id], p })).filter(x => x.ex);
                        return (_jsxs("div", { style: { padding: "6px 0", borderTop: i ? `1px solid ${C.borderSoft}` : "none" }, children: [_jsxs("div", { style: { display: "flex", alignItems: "baseline", gap: 8 }, children: [_jsx("span", { style: { fontSize: 13, fontWeight: 700, color: C.text, flex: 1, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }, children: h.dayLabel || h.programName || "Workout" }), _jsxs("span", { className: "mono", style: { fontSize: 11, color: C.muted, flexShrink: 0 }, children: [h.setsDone || 0, " sets \u00B7 ", (h.volume || 0).toLocaleString(), " ", hUnit, h.durationMin ? ` · ${h.durationMin}m` : ""] })] }), exers.length > 0 && (_jsx("div", { style: { marginTop: 4 }, children: exers.map(({ id, ex, p }) => {
                                        const sets = (p.sets && p.sets.length) ? p.sets : (p.weight != null ? [{ w: p.weight, r: p.reps }] : []);
                                        if (!sets.length)
                                            return null;
                                        return (_jsxs("div", { style: { display: "flex", alignItems: "baseline", gap: 8, marginTop: 4 }, children: [_jsx("span", { style: { fontSize: 11, color: C.muted, flexShrink: 0, width: 92, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }, children: ex.name }), _jsx("span", { className: "mono", style: { fontSize: 11, color: C.text, fontWeight: 600, flex: 1, lineHeight: 1.5 }, children: sets.map(s => (Number(s.w) > 0 ? Number(s.w) : "BW") + (s.r != null && s.r !== "" ? "×" + s.r : "")).join("   ") })] }, id));
                                    }) }))] }, i));
                    })] })), _jsx("div", { style: { display: "flex", gap: 8, marginTop: 12 }, children: [["Current streak", `${streaks.cur}d`], ["Longest", `${streaks.longest}d`], ["This month", `${monthCount}`]].map(([k, v]) => (_jsxs("div", { style: { flex: 1, textAlign: "center" }, children: [_jsx("div", { className: "mono", style: { fontSize: 15, fontWeight: 700, color: C.accentInk }, children: v }), _jsx("div", { style: { fontSize: 11, color: C.muted, marginTop: 2 }, children: k })] }, k))) })] }));
}
// Achievements / milestones — borrowed from Pursuit's gamification (First Step, On a Roll, Iron Habit…)
/* Achievements. Three things they must not do: reward what the lifter can't control, punish a break
```

## Candidate 19 — score 21 — offset 2480052

```js
eight: 700, color: C.accentInk }, children: v }), _jsx("div", { style: { fontSize: 11, color: C.muted, marginTop: 2 }, children: k })] }, k))) })] }));
}
// Achievements / milestones — borrowed from Pursuit's gamification (First Step, On a Roll, Iron Habit…)
/* Achievements. Three things they must not do: reward what the lifter can't control, punish a break,
   or lie about progress. So the strength tiers are bodyweight-relative rather than absolute (a 60kg
   lifter benching bodyweight has done something a 100kg lifter hasn't), and the long layoff earns a
   badge for coming back rather than breaking a streak and saying nothing. */
const BIG_THREE = { bench: "bb-bench", squat: "back-squat", deadlift: "deadlift" };
function computeMilestones(history, opts = {}) {
    const unit = opts.unit || "kg";
    const bw = parseFloat(opts.bodyweight) > 0 ? parseFloat(opts.bodyweight) : 0; // already in `unit`
    const n = history.length;
    // Sessions are stored in the unit they were logged in. Summing them raw double-counts anyone who
    // has ever switched kg <-> lb, so normalise into the unit the lifter is reading right now.
    const totalVol = history.reduce((a, h) => a + toUnit(h.volume || 0, h.unit || unit, unit), 0);
    const weekKey = weekKeyOf;
    const byWeek = {}, partsByWeek = {}, idsByWeek = {};
    history.forEach(h => {
        const k = weekKey(h.date);
        byWeek[k] = (byWeek[k] || 0) + 1;
        // Distinct muscle groups touched in the week, for the coverage badge. Unknown ids (a custom
        // exercise, a lift retired from the library) simply don't contribute rather than counting as
        // a mystery group.
        const set = partsByWeek[k] || (partsByWeek[k] = new Set());
        const ids = idsByWeek[k] || (idsByWeek[k] = new Set());
        Object.keys(h.perf || {}).forEach(id => { ids.add(id); const ex = EX_BY_ID[id]; if (ex && ex.part)
            set.add(ex.part); });
    });
    // Squat, bench and deadlift all inside one training week — the frequency the big lifts actually
    // want. A week, not a session: pressing and pulling heavy on the same day is a different thing.
    const bigThreeWeek = Object.values(idsByWeek).some(x => x.has(BIG_THREE.bench) && x.has(BIG_THREE.squat) && x.has(BIG_THREE.deadlift)) ? 1 : 0;
    const weeks = Object.keys(byWeek).map(Number).sort((a, b) => a - b);
    const maxInWeek = weeks.length ? Math.max(...Object.values(byWeek)) : 0;
    const bestPartsWeek = weeks.length ? Math.max(...Object.values(partsByWeek).map(x => x.size)) : 0;
    // Longest run of CONSECUTIVE weeks that each held 3+ sessions. Stricter than the plain week streak
    // above, which a single session a week satisfies.
    let fullRun = 0, bestFullRun = 0;
    for (let i = 0; i < weeks.length; i++) {
        const consec = i > 0 && Math.round((weeks[i] - weeks[i - 1]) / 86400000) === 7;
        fullRun = byWeek[weeks[i]] >= 3 ? (consec ? fullRun + 1 : 1) : 0;
        bestFullRun = Math.max(bestFullRun, fullR
```

## Candidate 20 — score 20 — offset 2527532

```js
"wpb-chart-axis", "aria-hidden": "true", children: [_jsx("span", { children: fmtShort(log[0].date) }), log.length > 1 && _jsx("span", { children: fmtShort(latest.date) })] }), log.length === 1 && _jsx("div", { style: { padding: "2px 12px 10px", textAlign: "center", fontSize: 12, color: C.muted }, children: "Add another measurement to see a trend." })] }), _jsx("div", { style: { marginTop: 12, fontSize: 11, fontWeight: 800, letterSpacing: .65, textTransform: "uppercase", color: C.faint }, children: "Logging history" }), _jsx("div", { style: { marginTop: 5, borderTop: `1px solid ${C.borderSoft}` }, children: [...log].reverse().map((e, i) => (_jsxs("div", { style: { minHeight: 40, display: "flex", alignItems: "center", justifyContent: "space-between", gap: 12, borderBottom: `1px solid ${C.borderSoft}`, fontSize: 13 }, children: [_jsx("span", { style: { color: C.muted }, children: fmtDate(e.date) }), _jsxs("span", { className: "mono", style: { color: C.text, fontWeight: 700 }, children: [e.v, " ", unit] })] }, `${e.date}-${i}`))) })] }));
}

/* HISTORY CORRECTION IS A FIRST-CLASS DATA OPERATION, NOT A DISPLAY-ONLY PATCH.
 * A bad log can affect load progression, readiness, PRs, weekly volume and the finite-program cursor.
 * Keep structural provenance (program/day/week) locked while allowing the performed evidence itself
 * to be corrected. Every save rebuilds the summary mirror from the edited raw sets. */
function summarizeHistoryLoggedSets(sets, ex, unit) {
    const work = (sets || []).filter(st => st && !st.sub && Number(st.r) > 0);
    if (!work.length)
        return null;
    const numW = st => Number.isFinite(Number(st.w)) ? Number(st.w) : 0;
    const hasPos = work.some(st => numW(st) > 0);
    const pool = hasPos ? work.filter(st => numW(st) > 0) : work;
    if (!pool.length)
        return null;
    const weights = pool.map(numW);
    const maxW = Math.max(...weights);
    if (weights.every(w => w === weights[0])) {
        const atTop = pool.filter(st => numW(st) === maxW);
        return { weight: maxW, reps: Math.min(...atTop.map(st => Math.max(1, Math.round(Number(st.r) || 1)))) };
    }
    const step = ex ? loadStep(ex, unit) : .5;
    const avgW = roundTo(weights.reduce((a, b) => a + b, 0) / weights.length, step || .5);
    const reps = Math.round(pool.reduce((a, st) => a + Math.max(1, Math.round(Number(st.r) || 1)), 0) / pool.length);
    return { weight: avgW, reps };
}
function normalizeEditedHistoryEntry(original, draft) {
    if (!original || !draft)
        return original;
    const out = { ...original };
    const date = Number(draft.date);
    if (Number.isFinite(date) && date > 0)
        out.date = date;
    const duration = Number(draft.durationMin);
    if (Number.isFinite(duration) && duration >= 0)
        out.durationMin = Math.round(duration);
    const perf = {};
    for (const [exId, rawPerf] of Object.entries(draft.perf || {})) {
        const prior = original.perf?.[exId] || {};
        const sourceSets = Array
```

## Candidate 21 — score 20 — offset 2530462

```js
 prior = original.perf?.[exId] || {};
        const sourceSets = Array.isArray(rawPerf?.sets) && rawPerf.sets.length
            ? rawPerf.sets
            : (rawPerf?.reps != null ? [{ w: rawPerf.weight ?? 0, r: rawPerf.reps }] : []);
        const sets = [];
        for (const raw of sourceSets) {
            if (!raw)
                continue;
            const reps = Math.round(Number(raw.r));
            if (!Number.isFinite(reps) || reps <= 0)
                continue;
            const w0 = raw.w === "" || raw.w == null ? 0 : Number(raw.w);
            if (!Number.isFinite(w0))
                continue;
            const st = { ...raw, w: w0, r: reps };
            if (raw.rir === "" || raw.rir == null || !Number.isFinite(Number(raw.rir)))
                delete st.rir;
            else
                st.rir = Math.max(0, Math.min(10, Number(raw.rir)));
            sets.push(st);
        }
        const summary = summarizeHistoryLoggedSets(sets, EX_BY_ID[exId], original.unit || "lb");
        if (!summary)
            continue;
        perf[exId] = { ...prior, ...rawPerf, weight: summary.weight, reps: summary.reps, sets, date: out.date };
    }
    out.perf = perf;
    let volume = 0, setsDone = 0;
    for (const p of Object.values(perf)) {
        for (const st of (p.sets || [])) {
            volume += (Number(st.w) || 0) * (Number(st.r) || 0);
            if (!st.sub)
                setsDone++;
        }
    }
    out.volume = Math.round(volume);
    out.setsDone = setsDone;
    return out;
}
function perfAfterHistoryReplace(history, perf, histId, replacement) {
    const old = (history || []).find(h => h && h.id === histId);
    if (!old || !replacement)
        return perf || {};
    const nextHistory = (history || []).map(h => h && h.id === histId ? replacement : h)
        .slice().sort((a, b) => (Number(b?.date) || 0) - (Number(a?.date) || 0));
    const affected = new Set([...Object.keys(old.perf || {}), ...Object.keys(replacement.perf || {})]);
    const next = { ...(perf || {}) };
    for (const exId of affected) {
        const latest = nextHistory.find(h => h?.perf?.[exId]);
        if (latest)
            next[exId] = latest.perf[exId];
        else
            delete next[exId];
    }
    return next;
}
function localHistoryDateValue(ms) {
    const d = new Date(Number(ms) || Date.now());
    const shifted = new Date(d.getTime() - d.getTimezoneOffset() * 60000);
    return shifted.toISOString().slice(0, 16);
}
function HistoryEditSheet({ entry, unit, onClose, onSave }) {
    const makeDraft = (src) => ({ ...src, perf: Object.fromEntries(Object.entries(src?.perf || {}).map(([id, p]) => [id, {
        ...p,
        sets: (Array.isArray(p?.sets) && p.sets.length ? p.sets : (p?.reps != null ? [{ w: p.weight ?? 0, r: p.reps }] : [])).map(st => ({ ...st }))
    }])) });
    const [draft, setDraft] = useState(() => makeDraft(entry));
    useEffect(() => setDraft(makeDraft(entry)), [entry?.id]);
    const setSet = (exId, index, ke
```

## Candidate 22 — score 24 — offset 3232413

```js
"home");
    };
    const exportData = () => JSON.stringify({ v: STORE_VERSION, savedAt: Date.now(), exportedAt: Date.now(), tombs, saved: withLegacy(saved, "saved"), cycles: withLegacy(cycles, "cycles"), banned, equipDefault, gyms, activeGymId, unit, unitChosen, experience, loadMode, perf, history, custom, theme, bodyweight, sex, birth, birthEst, age, bwLog, measurements, reminders, restAutoStart, warmupCard, restScale, haptics, minInc, plates, goals, exNotes, exSetup, seenIntroV, seenWhatsNew, lastBackup, installDismissedAt, pinnedId, canaryResearch, selectivePromotionRuntime }, null, 2);
    // Local calendar date for exports — slicing toISOString() takes the UTC date, which puts an
    // evening session in any UTC-negative timezone on the *next* day in the CSV.
    const isoLocal = (t) => { const d = new Date(t); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`; };
    const exportCSV = () => {
        const esc = c => { const s = String(c ?? ""); return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s; };
        const rows = [["Date", "Program", "Day", "Exercise", "Muscle", "Top weight", "Top reps", "Sets", "Volume", "Unit", "Duration (min)"]];
        [...history].sort((a, b) => a.date - b.date).forEach(h => {
            const d = isoLocal(h.date);
            const entries = Object.entries(h.perf || {});
            if (!entries.length)
                rows.push([d, h.programName, h.dayLabel, "", "", "", "", 0, 0, h.unit || unit, h.durationMin || ""]);
            entries.forEach(([id, p]) => {
                const ex = EX_BY_ID[id];
                const vol = Math.round((p.sets || []).reduce((a, s) => a + (s.w || 0) * (s.r || 0), 0));
                rows.push([d, h.programName, h.dayLabel, ex ? ex.name : id, ex ? PART_LABEL[ex.part] : "", p.weight ?? "", p.reps ?? "", (p.sets || []).length, vol, h.unit || unit, h.durationMin || ""]);
            });
        });
        return rows.map(r => r.map(esc).join(",")).join("\n");
    };
    const exportBodyCSV = () => {
        const esc = c => { const s = String(c ?? ""); return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s; };
        const rows = [["Date", "Metric", "Value", "Unit"]];
        // Export in ONE unit each, converted — a CSV mixing lb and kg rows under a column that names the
        // current setting is worse than useless: nothing downstream can tell which rows were converted.
        const lu = lengthUnitFor(unit);
        normalizeBwLog(bwLog, unit).forEach(e => rows.push([isoLocal(e.date), "Bodyweight", e.w, e.unit]));
        Object.entries(measurements || {}).forEach(([k, arr]) => normalizeMeasureLog(arr, lu, unit).forEach(e => rows.push([isoLocal(e.date), k.charAt(0).toUpperCase() + k.slice(1), e.v, e.unit])));
        return rows.map(r => r.map(esc).join(",")).join("\n");
    };
    const importProgramText = (text) => {
        // A code and a plain-text plan arrive through the same box, becau
```

## Candidate 23 — score 21 — offset 3405423

```js
color: C.accentText, fontSize: 15, fontWeight: 700, cursor: "pointer", display: "flex", alignItems: "center", justifyContent: "center", gap: 8 }, children: [_jsx(Wand2, { size: 18 }), " Build my program"] }), _jsx("button", { onClick: () => { setSeenIntroV(INTRO_VERSION); setSeenWhatsNew(WHATS_NEW_VERSION); setHomeBrowse(true); setView("home"); }, className: "pressable wpb-secondary-action wpb-onboarding-secondary", style: { width: "100%", minHeight: 44, padding: "10px 13px", marginTop: 7, borderRadius: 12, border: `1px solid ${C.border}`, background: "none", color: C.text, fontSize: 14, fontWeight: 650, cursor: "pointer" }, children: "Browse templates instead" })] })] }))] })] }));
}
// Wrap the whole app so a render crash ANYWHERE (not just inside the two sections that already have
// their own closer ErrorBoundary) shows a recoverable full-screen message instead of silently
// white-screening with no way back in. Data is autosaved to localStorage independent of render state.
export default function RootApp() {
    return _jsx(ErrorBoundary, { full: true, children: _jsx(App, {}) });
}
export { exerciseSlotSec, candidateMinutes, extraSetMinutes, WARMUP_SET_SEC, baseSetsFor, loggedVolume, loggedSubVolume, pressAngle, movePattern, availableFor, warmupSets, warmupCount, rankSwapAlts, starvedRegions, programSetupChips, weeklySubVolume, directSubVolume, SUB_LANDMARKS, subRegionOf, weeklyVolume, landmarkFor, coverageGaps, expandEquipment, EQUIP_IMPLIES, paceFactor, estimateMinutesFor, sessionExercisePlan, volumeZone, partZone, mavFor, PLANNED_ROOM_RIR, EX_BY_ID, CHANGELOG, CHANGELOG_ITEMS, WHATS_NEW_MAX, groupSessionsByMonth, groupSessionsByCycle, planOverview, phaseTone, PCT_SCHEMES, MACHINE_SETUP, SETUP_LABELS, plateauSessions, pickTopSet, setupFieldsFor, setupFieldsWithStored, navPush, navPop, propagateCycleEditsPure, ALL_EQUIP_IDS, EQUIPMENT, ageFactor, strengthLevel, strengthSnapshot, STANDARDS, STD_LEVELS, ageFrom, birthParts, birthFromAge, todayISO, normalizeBwLog, normalizeMeasureLog, weeklyBodyweightTrend, lengthUnitFor, asLengthUnit, BW_LOG_CAP, STORE_MIGRATIONS, calibratedDayPerf, onPlanExempt, plannedGapHours, prescribedRIRof, isBarLike, isMachineLike, barFor, BARS, shareSession, shareStrengthScore, setGymLimits, gymCapFor, normalizeGyms, templateFacets, templateEmphasis, ExerciseFigure, figurePose, EXERCISES, assertFigureCoverage, FIGURE_GENERIC_OK, Exit, mergeStores, sessionSnapshotStatus, mergeSessionData, mergedSetCount, nextPrefillTargetIndex, encodeProgramCode, decodeProgramCode, decodeGallery, gallerySubmission, galleryIssueBody, programFingerprint, personalRepSlope, coachFacts, volumeResponse, volumeVerdicts, STRETCH_FOCUS, STRETCH_FOCUS_E2, STRETCH_FOCUS_E3, lastSetTech, techSetTag, techExplain, capHistory, HISTORY_CAP, HISTORY_BYTES, rirTrend, sessionCoach, coverageRelief, volumeLedger, landmarkOf, completionRate, recommendedSplit, SPLIT_RECOMMENDATIONS, NOVICE_INELIGIBLE_SPLITS, parseRIRNum, sameProgramContent, rirAtLoad, e1rmAn
```

## Candidate 24 — score 21 — offset 3407284

```js
, EQUIPMENT, ageFactor, strengthLevel, strengthSnapshot, STANDARDS, STD_LEVELS, ageFrom, birthParts, birthFromAge, todayISO, normalizeBwLog, normalizeMeasureLog, weeklyBodyweightTrend, lengthUnitFor, asLengthUnit, BW_LOG_CAP, STORE_MIGRATIONS, calibratedDayPerf, onPlanExempt, plannedGapHours, prescribedRIRof, isBarLike, isMachineLike, barFor, BARS, shareSession, shareStrengthScore, setGymLimits, gymCapFor, normalizeGyms, templateFacets, templateEmphasis, ExerciseFigure, figurePose, EXERCISES, assertFigureCoverage, FIGURE_GENERIC_OK, Exit, mergeStores, sessionSnapshotStatus, mergeSessionData, mergedSetCount, nextPrefillTargetIndex, encodeProgramCode, decodeProgramCode, decodeGallery, gallerySubmission, galleryIssueBody, programFingerprint, personalRepSlope, coachFacts, volumeResponse, volumeVerdicts, STRETCH_FOCUS, STRETCH_FOCUS_E2, STRETCH_FOCUS_E3, lastSetTech, techSetTag, techExplain, capHistory, HISTORY_CAP, HISTORY_BYTES, rirTrend, sessionCoach, coverageRelief, volumeLedger, landmarkOf, completionRate, recommendedSplit, SPLIT_RECOMMENDATIONS, NOVICE_INELIGIBLE_SPLITS, parseRIRNum, sameProgramContent, rirAtLoad, e1rmAnchorOf, regionGapsFor, regionGapFix, REGION_REQUIRED, previewVolumeNudge, coachNote, coachIntent, exerciseSeries, rmAt, EX_METRICS, EX_WINDOWS, exRecords, cycleProgress, blockReview, lastTopSet, betterTopSet, e1rmRIR, loadAtRIR, EPLEY_SLOPE, E1RM_REP_CAP, ASSUMED_RIR, isWorkSet, workSetsOf, dayMuscleBreakdown, weekMuscleBreakdown, plannedWeek, dayMuscleVolume, capWords, WHATS_NEW_WORDS, homeCardPlan, HOME_INSIGHT_BUDGET, WhatsNewCard, PATTERNS, patternOf, sameMovement, lengthOf, parseStoredData, recordReleaseDiag, readReleaseDiagnostics, clearReleaseDiagnostics, releaseDiagnosticsText };
export { ENGINE_V, ENGINES, ENGINE_RULES, engHas, engLacks, engineInfo, STORE_VERSION, migrateStore, GOALS, SESSIONS, EXP, THEMES, normalizeMusclePreferences, preferenceFloor, GEN_PIPELINE, runPipeline, openGeneration, schemeTierCount, COVERAGE_PASSES, coverageContext, prescribedDaySize, ASSISTANCE_SIZE, candidateSlotMin, daySeconds, advanceCycle, templateConfig, TEMPLATES, CYCLE_TEMPLATES, SPLITS, DAY_TEMPLATES, templateIntent, TEMPLATE_CATS, TEMPLATE_FILTER_GROUPS, sfrOf, axialCost, fitSessionTime, MRV_GRAIN, weekIntent, historyForProgram, IDEAL_SLOTS, dayMuscleLoad, dayOverlap, distributeVolBias, capWeeklyVolume, buildWeekPlan, programQuality, compareQuality, COVERED_MUSCLES, BEST_OF_N, traceGeneration, setPipeTrace, setClaimTrace, emitClaim, compositeMrv, PATTERN_GROUPS, PATTERN_MIN_SETS, patternTrainable, patternGapsOf, weeksOf, phaseFor, estimateMinutes, addedMinutes, addedSeconds, fitChip, daysInWeek, exerciseAt, rotationOf, fitSessionToTime, blockRetro, volumeAudit, LANDMARKS, secondaryOf, PART_ORDER, FOCUS_CAVEAT, WEEK_ORDER_TERMS, computeCell, pctSetsFor, gzPlanFor, gzAdvance, projectNextTM, nextTMEvidence, ddpNextForSet, lastSetEffort, styleFor, autoStyleFor, plateauOf, plateauOfLift, plateauSplitByDay, dayScopedTrends, fitText, fitFon
```

## Custom-set expression

offset 938034

```js
orRepRange(ex.rep), o = program.overrides?.[`${day?.id}:${slotIndex}`] || {};
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
function lastSetEffort(cell, ex, isPrim
```
