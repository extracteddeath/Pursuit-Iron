// Canonical programs domain. Maintained production source; independent of React and browser APIs.
import { setShellEquipmentExpander, splitContractGaps, splitBuildability, refusalFixes, generateNextProgramForShell, recommendNextSplitForShell, getNextShellCell, canonicalShellSetCount, cloneNextDayPrescriptions, swapNextSlotPrescriptions, removeNextSlotPrescription, nextExerciseIdForShellExercise, resolveNextShellExerciseId, remapNextShellRoster, snapshotNextShellPrescription, markUserPrescriptionOverride, clearUserPrescriptionOverride, NextShellAdapterError } from "../next-engine/app-shell-adapter.js";
import { historyNumber, convertHistoryLoad, observedHistoryRIR, completedHistorySets, historyExposureContext, progressionExposureContext, normalizeHistoryEntries, validHistoryDate, resolveHistoryDayIndex, historyLoadReason } from '../next-engine/history-contract.js';
import { programWorkingWeeks, cycleBlockMetadata } from "../program-duration.js";
import { LEGACY_EQUIP_IMPLIES, baseSetsFor, isBarLike, movePattern, secondaryOf, weeksOf } from './records.js';
import { ENGINE_V, EQUIPMENT, EXERCISES, EX_BY_ID, SPLITS } from './catalog.js';

const PART_ORDER = ["chest", "lats", "upper_back", "shoulders", "biceps", "triceps", "quads", "hamstrings", "glutes", "lower_back", "adductors", "abductors", "calves", "abs", "traps", "forearms", "neck"];

const ENGINE_RULES = {
    preferenceAware: 24,
    stretchOpener: 3,
    shortSessionRows: 7,
    tightPairs: 11,
    primaryPattern: 19,
    repeatPenalty: 13,
    honestBudget: 15,
    axialSpacing: 17,
    longSessionSlots: 18
};

const ruleEngine = (rule) => {
    const at = ENGINE_RULES[rule];
    if (at == null)
        throw new Error("unknown engine rule: " + rule);
    return at;
};

const engHas = (eng, rule) => eng >= ruleEngine(rule);

const engLacks = (eng, rule) => eng < ruleEngine(rule);

function axialCost(ex, eng = 1) { return isAxialLoad(ex, eng) ? (isBarLike(ex.equip) ? 1.0 : 0.7) : 0; }

const sameProgramContent = (a, b) => {
    if (a === b)
        return true;
    if (!a || !b)
        return false;
    const strip = ({ updatedAt, autoBias, autoTuned, ...rest }) => rest;
    return JSON.stringify(strip(a)) === JSON.stringify(strip(b));
};

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

function dayMuscleLoad(program, day) {
    const load = {};
    day.exercises.forEach((id, si) => {
        const ex = EX_BY_ID[id];
        if (!ex)
            return;
        const sets = baseSetsFor(program.config, ex, si === day.primaryIndex);
        load[ex.part] = (load[ex.part] || 0) + sets;
        secondaryOf(ex, program.engineV).forEach(([pp, f]) => { load[pp] = (load[pp] || 0) + sets * f; });
    });
    return load;
}

function dayOverlap(a, b) {
    let s = 0;
    for (const part in a)
        if (b[part])
            s += Math.min(a[part], b[part]);
    return s;
}

const AXIAL_ADJACENT_COST = 6;

const IDEAL_SLOTS = {
    1: [0],
    2: [0, 4], // Mon, Fri  — 3+3 gap (symmetric, familiar)
    3: [0, 2, 4], // Mon, Wed, Fri — 1+1+2 (classic 3-day)
    4: [0, 2, 4, 6], // Mon, Wed, Fri, Sun — 1+1+1+0 with 0-gap at wrap
    5: [0, 1, 3, 4, 6], // Mon,Tue,Thu,Fri,Sun — 0,1,0,1,0 symmetric
    6: [0, 1, 2, 3, 4, 6], // Mon–Fri + Sun — only Sat off
};

function buildWeekPlan(program, loadOf = dayMuscleLoad, eng = (program && program.engineV) || 0) {
    const n = program.days.length;
    if (n <= 0)
        return null;
    if (n >= 7)
        return program.days.map((_, i) => i); // train every day, no room for rest
    const loads = program.days.map(d => loadOf(program, d));
    // Optimal cyclic ordering: try every distinct arrangement of days around the week-cycle
    // and keep the one with the lowest TOTAL adjacent-day overlap (summed over every
    // consecutive pair, wrapping last→first). n is always ≤6 here (n≥7 returns above), so
    // fixing day 0 first and permuting the rest is at most 5! = 120 arrangements — a greedy
    // nearest-neighbor chain can get trapped in a local optimum on "ring"-shaped overlap data
    // (e.g. Legs→Push shares chest, Push→Pull shares shoulders/triceps, …, Pull→Legs closes
    // the ring) where a different arrangement reaches zero total overlap that greedy can\'t see
    // because it commits to each next-best step without looking ahead. Brute force costs <5ms
    // even at n=6 and runs once per program generation, so there\'s no reason to settle for greedy.
    /* Positions are needed BEFORE scoring now, since the objective depends on which gaps are real. */
    const posForScore = IDEAL_SLOTS[n] || null;
    const tightPairs = [];
    if (posForScore) {
        for (let k = 0; k + 1 < n; k++)
            if (posForScore[k + 1] - posForScore[k] === 1)
                tightPairs.push([k, k + 1]);
        if (n > 1 && (7 - posForScore[n - 1] + posForScore[0]) === 1)
            tightPairs.push([n - 1, 0]);
    }
    const useTight = engHas(eng, "tightPairs") && tightPairs.length > 0;
    const restIdx = Array.from({ length: n }, (_, i) => i).filter(i => i !== 0);
    function permute(arr) {
        if (arr.length <= 1)
            return [arr];
        const res = [];
        for (let i = 0; i < arr.length; i++) {
            const rest = [...arr.slice(0, i), ...arr.slice(i + 1)];
            permute(rest).forEach(p => res.push([arr[i], ...p]));
        }
        return res;
    }
    let order = [0, ...restIdx], bestTotal = Infinity, bestMax = Infinity;
    /* Engine 11 must consider rotations, so it permutes every day; earlier engines pin day 0 and
       permute the rest, which is what makes them bit-identical to before. */
    const arrangements = useTight
        ? permute(Array.from({ length: n }, (_, i) => i))
        : permute(restIdx).map(rest => [0, ...rest]);
    arrangements.forEach(candidate => {
        let total = 0, maxAdj = 0;
        /* ⚠ ENGINE 13: THE SAME LIFT ON TWO CONSECUTIVE DAYS IS WORSE THAN ANY MUSCLE OVERLAP.
           `dayOverlap` compares muscle loads, which is the right measure when the two days hold
           DIFFERENT exercises. A named program can hold the same one twice: GZCLP squats as the T1 of
           A1 and again as the T2 of A2, and its published schedule is Mon/Wed/Fri precisely so those
           never land back to back. Laid out as four days in a week they can, and muscle overlap alone
           does not object loudly enough — measured, gates/dayorder check 2 found 6 repeats, e.g.
           gzclp/4/s90 with Back Squat and Deadlift each on consecutive days.
           Repeating the exact movement is a different event from training the same muscle twice: it is
           the same bar, the same groove, the same joints, at the same intensity, without a day between.
           Cost it above any overlap value (dayOverlap is bounded by 1) so the search treats avoiding it
           as the first duty and falls back to minimising overlap once no repeat-free order exists. */
        /* ⚠ TWO HEAVY SPINAL DAYS BACK TO BACK. `dayOverlap` compares MUSCLE loads, and a deadlift day
           and a squat day barely overlap by that measure — hamstrings/glutes/back against quads — so the
           search happily put them next to each other. Axial loading is a systemic cost the muscle model
           cannot see: same spine, same bracing, same connective tissue, one night apart. MEASURED at
           engine 16: 145 of 456 adjacent training-day pairs (31.8%) had BOTH days carrying heavy axial
           load. Reported by Haiden on his own 5-day full-body-patterns program, where the week wraps with
           no rest between the last day and the first.
           Priced BETWEEN muscle overlap (bounded by 1) and repeating the exact lift (2 per repeat): worse
           than sharing a muscle, not as bad as the same bar in the same groove. `axialCost` is the same
           owner `rankFloorFill` uses, so the two passes agree on what "heavy" means. */
        /* ⚠ THE WEIGHT IS ON `dayOverlap`'S SCALE, NOT ON THE ONE THE COMMENTS CLAIM. Two places here say
           dayOverlap is "bounded by 1"; measured, it runs 0 to 21.4 with a median of 10.3. A penalty of 1
           or 2 is therefore a rounding error against a typical overlap, which is why the first version of
           this term barely moved the number (145 → 123 adjacent heavy-axial pairs) and why engine 13's
           repeat penalty of 2 is worth about a fifth of one overlap rather than more than all of it.
           Swept: 3 → 98, 6 → 76, 10 → 68, against mean adjacent overlap rising 5.86 → 5.95 / 6.13 / 6.28.
           Six nearly halves the clash for under 5% more muscle overlap; ten buys 8 more pairs for another
           2.5%. (The engine-13 repeat price is NOT re-tuned here — only 2 of 456 adjacent pairs repeat a
           lift, so it works despite the mispricing, and changing it is its own measured pass.) */
        const axialOf = (i) => (program.days[candidate[i]]?.exercises || [])
            .reduce((a, id) => a + (EX_BY_ID[id] ? axialCost(EX_BY_ID[id]) : 0), 0);
        const axialPenalty = (x, y) => {
            if (engLacks(eng, "axialSpacing"))
                return 0;
            return AXIAL_ADJACENT_COST * Math.min(axialOf(x), axialOf(y));
        };
        /* ⚠ ENGINE 19: THE DAY ORDER PRICED MUSCLES, THE BAR, AND THE SPINE — NOT THE MOVEMENT.
           Reported by Haiden on his own 5-day full_body_patterns program: "squats at the start and end of
           every week". Reproduced exactly — fb2_a opens on hack-squat and fb2_e closes on smith-squat, and
           under the schedule those two land on Thursday and Friday, back to back.
           WHY NOTHING CAUGHT IT. Two different squat variants are not the same exercise, so `repeatPenalty`
           sees nothing. They are not both heavy-axial, so `axialPenalty` is small. And their muscle overlap
           alone loses to the alternative orderings. The search was never told that squatting on consecutive
           days is its own event, distinct from training quads twice.
           MEASURED BEFORE THE FIX: 40 of 3,648 adjacent calendar-day pairs across the config space put
           the same primary pattern back to back, clustered in full_body and full_body_patterns.
           ⚠ PRICED AT 1, AND THE PRICE IS CAPPED BY A BOUND THIS TERM DID NOT SET. An earlier version of
           this comment said 6 while the constant read 2, then 3, then 2 again — and NONE of those values
           is shippable. Swept against gates/axialspacing check 5, which holds mean adjacent-day muscle
           overlap to under +5%:
               cost  0   40 clashes   overlap 5.852 (baseline)   gate 6/6
               cost  1   21 clashes   overlap within bound       gate 6/6
               cost  2   14 clashes   overlap 6.166  (+5.4%)     gate FAILS
               cost  6    1 clash     overlap 6.182  (+5.6%)     gate FAILS
               cost 12    0 clashes   overlap beyond bound       gate FAILS
           So 1 is the largest price the existing bound allows, and it halves the clash rather than
           clearing it. THE REMAINING HALF IS A PRODUCT DECISION, NOT A TUNING PROBLEM: is squatting on
           two consecutive days worse than a 5.6% rise in mean adjacent muscle overlap? If it is, check 5's
           bound is the thing to change, deliberately and with its own justification — not this constant
           quietly, which is what raising it past 1 amounts to.
           ⚠ AND MY OWN SWEEP DISAGREED WITH THE GATE AND THE GATE WAS RIGHT. Measured over s60+s90 only,
           overlap looked FLAT across costs 0..12 (5.9813 to 5.9854, a 0.07% spread) and 12 looked free.
           The gate sweeps a wider population and sees +5.6%. A measurement's scope is not the gate's
           scope, and the narrower one produced a confident wrong answer — the third time in this file's
           history that a conclusion was really a statement about where someone looked.
           Only the PRIMARY is compared. Accessories repeat patterns constantly and should. */
        const PATTERN_ADJACENT_COST = 2;
        const primaryPatternOf = (i) => {
            const day = program.days[candidate[i]];
            const id = day && day.exercises ? day.exercises[day.primaryIndex] : null;
            const ex = id ? EX_BY_ID[id] : null;
            return ex ? movePattern(ex) : null;
        };
        const patternPenalty = (x, y) => {
            if (engLacks(eng, "primaryPattern"))
                return 0;
            const a = primaryPatternOf(x), b = primaryPatternOf(y);
            return a && b && a === b ? PATTERN_ADJACENT_COST : 0;
        };
        const repeatPenalty = (x, y) => {
            const A = program.days[candidate[x]]?.exercises, B = program.days[candidate[y]]?.exercises;
            if (!A || !B)
                return 0;
            let hits = 0;
            for (const id of A)
                if (B.includes(id))
                    hits++;
            /* ⚠ 2 WAS PRICED AGAINST A dayOverlap THE COMMENT BELIEVED WAS "bounded by 1". It runs to 21.4.
               So the penalty the engine-13 note calls "the first duty" has been worth about a fifth of one
               typical overlap since it shipped — it worked anyway only because repeats are rare (2 of 456
               adjacent pairs). Engine 17 adds an axial term at 6, which would OUTRANK it and start trading
               a repeated lift away for spinal spacing — gates/dayorder checks 1 and 2 caught exactly that
               (one repeat, one 0.74 overlap pair). Repriced above the observed overlap maximum so the
               stated priority — same bar back to back is worse than any muscle overlap — is finally true. */
            return hits * (engHas(eng, "axialSpacing") ? 25 : 2);
        };
        if (useTight) {
            for (const [a, b] of tightPairs) {
                const ov = dayOverlap(loads[candidate[a]], loads[candidate[b]]) + (engHas(eng, "repeatPenalty") ? repeatPenalty(a, b) : 0) + axialPenalty(a, b) + patternPenalty(a, b);
                total += ov;
                if (ov > maxAdj)
                    maxAdj = ov;
            }
        }
        else {
            for (let i = 0; i < n; i++) {
                const ov = dayOverlap(loads[candidate[i]], loads[candidate[(i + 1) % n]])
                    + patternPenalty(i, (i + 1) % n)
                    + (engHas(eng, "repeatPenalty") ? repeatPenalty(i, (i + 1) % n) : 0)
                    + axialPenalty(i, (i + 1) % n);
                total += ov;
                if (ov > maxAdj)
                    maxAdj = ov; // the single worst back-to-back conflict in this arrangement
            }
        }
        // Primary objective: lowest total overlap around the whole week (wrap included). Tiebreak:
        // among equally-low totals, prefer the arrangement whose WORST single adjacent pair is least
        // conflicting — so an unavoidable back-to-back (common in 4/5/6-day weeks) never lands the two
        // most-overlapping days next to each other when a gentler pairing was available.
        /* ⚠ ENGINE 11 INVERTS THE TWO OBJECTIVES, AND THE ORDER OF THEM IS THE WHOLE POINT.
           Minimising TOTAL first accepts one bad collision to shave several trivial ones: measured,
           81 of 456 weeks shipped a worse worst-pair than was available, e.g. full_body/4/s60/both at
           0.29 where 0.06 existed. But a lifter does not experience a sum over the week — they
           experience the one morning they train a muscle that is still sore. The single worst
           back-to-back conflict is the quantity to minimise; total is the tiebreak between orders that
           are equally good on it. The pre-11 comment already said as much when it introduced maxAdj as
           a tiebreak ("never lands the two most-overlapping days next to each other when a gentler
           pairing was available") — engine 11 promotes that from tiebreak to objective.
           Earlier engines keep total-first exactly, so their schedules do not move. */
        const better = useTight
            ? (maxAdj < bestMax - 1e-9 || (Math.abs(maxAdj - bestMax) <= 1e-9 && total < bestTotal - 1e-9))
            : (total < bestTotal - 1e-9 || (Math.abs(total - bestTotal) <= 1e-9 && maxAdj < bestMax - 1e-9));
        if (better) {
            bestTotal = total;
            bestMax = maxAdj;
            order = candidate;
        }
    });
    // Choose slot positions: use the pre-computed ideal set when available, fall back to
    // the rounding approach for edge cases. Ideal sets push forced back-to-back pairs
    // to the week wrap (Sun→Mon) where a rest day following Sunday is most natural,
    // and avoid the mid-week Fri→Sat collision the old rounding produced for 4-day plans.
    const idealPositions = IDEAL_SLOTS[n];
    let positions;
    if (idealPositions) {
        positions = idealPositions;
    }
    else {
        const step = 7 / n, taken = new Set();
        positions = [];
        for (let k = 0; k < n; k++) {
            let pos = Math.round(k * step);
            while (taken.has(pos) && taken.size < 7)
                pos = (pos + 1) % 7; // all 7 taken (n>7, corrupt program) → allow the duplicate rather than spin forever
            taken.add(pos);
            positions.push(pos);
        }
    }
    // No rotation step needed: `order` is already the globally-optimal CYCLE (lowest total
    // adjacent overlap summed around the full week, wrap included), found by the brute-force
    // search above. Rotating a cyclic sequence relabels which day is "first" but can\'t change
    // which days end up adjacent to each other, so every rotation of this same order has the
    // identical total overlap — there\'s no remaining slot-position-specific optimization to do.
    const slots = new Array(7).fill(null);
    positions.forEach((pos, k) => { slots[pos] = order[k]; });
    return slots;
}

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
                best = Math.max(best, e1rm(convertHistoryLoad(s.w, h.unit, program.config?.unit), s.r)); });
        }));
        return best;
    };
    const [r0, r1, r2] = [bestOf(rots[0]), bestOf(rots[1]), bestOf(rots[2])];
    if (!(r0 > 0 && r1 > 0 && r2 > 0))
        return false; // need 3 full rotations of data
    return r0 <= Math.max(r1, r2) + 1e-6; // newest rotation set no new best → stalled
}

function historyDayIndex(days, h) {
    return resolveHistoryDayIndex(days, h);
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
    const explicit = entries.length > 0 && entries.every(h => Number.isInteger(Number(h.weekIndex))
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
            // Keep rotation/schedule order, but never serve a day already completed in this explicit week.
            let probe = dayIndex;
            for (let n = 0; n < dpw; n++) {
                if (!completed.has(probe)) {
                    dayIndex = probe;
                    break;
                }
                probe = (probe + 1) % dpw;
            }
        }
        return { dayIndex, weekIndex, done, scheduled, lastIdx, explicitWeekProgress: true };
    }
    const weekIndex = (Math.floor(done / dpw) % maxWeek) + 1;
    return { dayIndex, weekIndex, done, scheduled, lastIdx };
}

const CODE_ALPHA = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

const CODE_SCHEMES = ["531", "531beg", "madcow", "nsuns", "texas", "gzclp", "gvt", "rippler", "jt"];

const CODE_ASSIST = ["bbb", "triumvirate", "jack_shit"];

const CODE_CARRIED_KEYS = new Set(["split", "days", "goal", "experience", "weeks", "progression",
    "session", "percentScheme", "noBodyweight", "equipment", "focus", "focusList", "deload", "assistance"]);

const CODE_IGNORED_KEYS = new Set(["name"]);

const CODE_SPLITS = Object.keys(SPLITS);

const CODE_GOALS = ["hypertrophy", "strength", "both"];

const CODE_EXP = ["none", "beginner", "intermediate", "advanced"];

const CODE_PROG = ["auto", "manual"];

const CODE_SESSION = ["s20", "s40", "s60", "s90", "s120"];

const CODE_EQUIP = EQUIPMENT.map(e => e.id);

function expandLegacyEquipment(list) {
    const out = new Set(list);
    list.forEach(id => (LEGACY_EQUIP_IMPLIES[id] || []).forEach(fine => out.add(fine)));
    return CODE_EQUIP.filter(id => out.has(id)); // keep canonical order
}

const CODE_EXERCISES = EXERCISES.map(e => e.id);

function programFingerprint(p) {
    const str = (p && p.days ? p.days : []).map(d => (d.exercises || []).join(",")).join("|");
    let h = 2166136261 >>> 0;
    for (let i = 0; i < str.length; i++) {
        h ^= str.charCodeAt(i);
        h = Math.imul(h, 16777619) >>> 0;
    }
    return h & 0xffff;
}

function bitsToCode(bits) {
    while (bits.length % 5)
        bits.push(0);
    let out = "";
    for (let i = 0; i < bits.length; i += 5) {
        let v = 0;
        for (let k = 0; k < 5; k++)
            v = (v << 1) | bits[i + k];
        out += CODE_ALPHA[v];
    }
    // Group in fours, but never leave a runt: 21 characters would otherwise end "…-HD6P-0", and a
    // one-character group reads like the code got cut off in the paste.
    const g = out.match(/.{1,4}/g) || [];
    if (g.length > 1 && g[g.length - 1].length < 2) {
        g[g.length - 2] += g.pop();
    }
    return "PI-" + g.join("-");
}

function codeToBits(str) {
    const clean = String(str || "").toUpperCase().replace(/^PI-?/, "").replace(/[^0-9A-Z]/g, "")
        .replace(/I/g, "1").replace(/L/g, "1").replace(/O/g, "0").replace(/U/g, "V"); // forgive the classic misreads
    const bits = [];
    for (const ch of clean) {
        const v = CODE_ALPHA.indexOf(ch);
        if (v < 0)
            return null;
        for (let k = 4; k >= 0; k--)
            bits.push((v >> k) & 1);
    }
    return bits;
}

const putBits = (bits, val, n) => { for (let k = n - 1; k >= 0; k--)
    bits.push((val >> k) & 1); };

const getBits = (bits, at, n) => { let v = 0; for (let k = 0; k < n; k++)
    v = (v << 1) | (bits[at + k] || 0); return v >>> 0; };

function encodeProgramCode(program) {
    // M40: a Pursuit Next program is not reproducible by the legacy seed-code decoder. Text sharing
    // remains lossless; refuse a compact code rather than minting one that rebuilds under v661.
    if (program?.engineSource === "pursuit-next")
        return null;
    const c = program && program.config;
    if (!c)
        return null;
    const si = CODE_SPLITS.indexOf(c.split), gi = CODE_GOALS.indexOf(c.goal);
    const ei = CODE_EXP.indexOf(c.experience || "beginner");
    const pi = CODE_PROG.indexOf(c.progression || "auto");
    const ssi = CODE_SESSION.indexOf(c.session || "s20");
    const days = c.days | 0, weeks = c.weeks | 0, seed = program.seed >>> 0;
    if (si < 0 || gi < 0 || ei < 0 || pi < 0 || ssi < 0)
        return null; // an unrepresentable field (incl. an unknown session length) refuses rather than minting a code that rebuilds a DIFFERENT program
    if (days < 1 || days > 7 || weeks < 1 || weeks > 31)
        return null;
    // focusList IS carried now, by the v4 format below. Refuse only what the field can't hold: an
    // unknown part name, or more entries than the 4-bit count. Everything else encodes.
    const focusIdx = (c.focusList || []).map(part => PART_ORDER.indexOf(part));
    if (focusIdx.some(i => i < 0) || focusIdx.length > 15)
        return null;
    // 0 = no named scheme (the legacy 1-bit boolean still says whether percentages are on at all).
    const schemeIdx = typeof c.percentScheme === "string" ? CODE_SCHEMES.indexOf(c.percentScheme) + 1 : 0;
    if (typeof c.percentScheme === "string" && schemeIdx === 0)
        return null; // unknown scheme: refuse rather than mint a code that rebuilds a different program
    const assistIdx = typeof c.assistance === "string" ? CODE_ASSIST.indexOf(c.assistance) + 1 : 0;
    if (typeof c.assistance === "string" && assistIdx === 0)
        return null;
    // Refuse on any field the schema cannot carry, rather than dropping it silently on import.
    for (const [k, v] of Object.entries(c)) {
        if (CODE_CARRIED_KEYS.has(k) || CODE_IGNORED_KEYS.has(k))
            continue;
        const meaningful = !(v == null || v === false || (Array.isArray(v) && !v.length) ||
            (typeof v === "object" && !Array.isArray(v) && !Object.keys(v).length));
        if (meaningful)
            return null;
    }
    if (c.exerciseBias && Object.keys(c.exerciseBias).length)
        return null;
    let mask = 0;
    /* An id the vocabulary doesn't know is refused here rather than skipped. Silently dropping it
       encoded a DIFFERENT equipment list, which rebuilt a different program, which the self-check at
       the end caught as a fingerprint mismatch — safe, but the refusal arrived with no way to tell
       what was wrong. This is the same rule the unknown-KEY loop above already applies; values inside
       `equipment` were the one place it wasn't enforced. Reachable via a stale id in a store written
       by an older version, or a hand-edited config. */
    for (const id of (c.equipment || [])) {
        const i = CODE_EQUIP.indexOf(id);
        if (i < 0)
            return null;
        mask |= (1 << i);
    }
    // Detect coach overrides: diff the actual layout against a fresh regen from the same seed. Anything
    // that differs is a deliberate edit the code has to carry, or it must refuse to encode (text fallback).
    let overrides = [];
    try {
        const fresh = generateProgram(c, [], null, seed, program.engineV || ENGINE_V);
        const sameStructure = fresh.days.length === program.days.length &&
            fresh.days.every((d, i) => (program.days[i]?.exercises?.length || 0) === d.exercises.length);
        if (!sameStructure)
            return null; // added/removed slots — can't express as swaps
        for (let di = 0; di < program.days.length; di++) {
            const cur = program.days[di].exercises, base = fresh.days[di].exercises;
            for (let slot = 0; slot < cur.length; slot++) {
                if (cur[slot] === base[slot])
                    continue;
                const exIdx = CODE_EXERCISES.indexOf(cur[slot]);
                if (exIdx < 0 || di > 7 || slot > 15 || exIdx > 511)
                    return null; // outside what a swap record can hold
                overrides.push({ dayIdx: di, slot, exIdx });
            }
        }
        if (overrides.length > 31)
            return null; // more edits than the record count can hold
    }
    catch {
        return null;
    }
    const bits = [];
    /* v5 widens the equipment field. v1-v4 wrote a fixed 16-bit mask, hard-capping the library at 16
       equipment ids — fine for 11, fatal the moment a real taxonomy (leg press vs hack squat vs
       pendulum squat) needs dozens. v5 writes a self-describing field instead:
          1 bit  "has everything"  → nothing follows, so a full commercial gym stays SHORT
          else   7 bits count N, then N bits of mask
       Storing N is what makes it future-proof: a decoder that knows MORE equipment than the encoder did
       treats the surplus as absent, which is exactly right — that gym did not have it. */
    const emitEquip = (ver) => {
        if (ver < 5) {
            putBits(bits, mask & 0xffff, 16);
            return;
        }
        const have = c.equipment || [];
        const hasAll = CODE_EQUIP.every(id => have.includes(id));
        putBits(bits, hasAll ? 1 : 0, 1);
        if (hasAll)
            return;
        putBits(bits, CODE_EQUIP.length, 7);
        CODE_EQUIP.forEach(id => putBits(bits, have.includes(id) ? 1 : 0, 1));
    };
    const engineOf = (p) => (p.engineV || ENGINE_V);
    const emitBase = (baseVer, sessionBits) => {
        /* Widen only when needed: engines 0-7 keep the historical versions untouched. */
        /* ⚠ v635: A THIRD TIER, FOR THE SAME REASON THE SECOND ONE EXISTS. The four-bit engine field
           holds 0-15; ENGINE_V 16 masks to 0 and every shared program rebuilds under engine-1 rules —
           silently, with a valid checksum, exactly as the three-bit field did at engine 8. The warning
           below said to widen BEFORE the next bump; gates/progcode.mjs check 2 is what enforced it,
           going red on 27 configs the moment ENGINE_V went to 16.
           Versions 11-15 are versions 1-5 with a SIX-bit engine field (0-63). Old codes keep their own
           width forever: 1-5 read three bits, 6-10 read four, 11-15 read six. The version field itself
           is four bits, so this is the LAST widening available — the next one needs a wider `ver`. */
        const engTier = engineOf(program) > 15 ? 2 : engineOf(program) > 7 ? 1 : 0;
        const ver = baseVer + (engTier === 2 ? 10 : engTier === 1 ? 5 : 0);
        putBits(bits, ver, 4);
        /* ⚠ THREE BITS — the ORIGINAL field, kept for codes already in the wild. History follows. */
        /* ⚠ THREE BITS. THIS IS THE HARD CEILING ON ENGINE_V AND IT IS NOT DOCUMENTED ANYWHERE ELSE.
           A share code carries a seed, a config and an ENGINE — the recipient regenerates rather than
           receiving a layout, so the engine number is what makes their program match the sender's.
           At three bits the field holds 0-7. Engine 8 masks to 0, decodes as 0, and every shared program
           silently rebuilds under engine-1 rules: a different program, with no error anywhere.
           gates/progcode.mjs check 2 caught this the moment ENGINE_V went to 8, which is the only reason
           it was not shipped. RAISING ENGINE_V PAST 7 REQUIRES A CODE-FORMAT VERSION BUMP FIRST — widen
           the field and keep decoding old codes at the old width, or every code already in the wild
           becomes wrong. */
        /* ⚠ THREE BITS. THE ENGINE VERSION CANNOT EXCEED 7, AND NOTHING SAYS SO ANYWHERE ELSE.
           `& 7` does not clamp, it WRAPS: engine 8 encodes as 0, engine 9 as 1. A share code minted by
           either decodes as a different engine and rebuilds a different program — silently, with a valid
           checksum. Found when ENGINE_V was bumped to 9 for the reduce change and gates/progcode.mjs went
           red on 27 configs at once; the engine-8 hold had accidentally been protecting this too.
           THIS IS A HARD BLOCKER ON THE WHOLE ENGINE PLAN — engines 8, 9 and 10 are all specified and all
           unshippable until the field is widened, which needs a code-format version bump (`ver`) and a
           decoder that reads 3 bits for old codes and 4 for new. Do that BEFORE the next ENGINE_V bump,
           not alongside one. */
        /* ⚠ FIXED — see the note above. The engine field is 3 bits for versions 1-5 and 4 bits for the
           WIDE versions 6-10, which carry the payload of `ver - 5`. The version field itself has always
           been 4 bits (0-15) with only 1-5 used, so the wide range costs nothing and no existing code
           changes meaning: a v5 code stays a v5 code and still decodes at 3 bits, forever.
           A wide version is emitted ONLY when the engine will not fit in three bits, so nothing in the
           wild is re-encoded and the format does not churn for the 99% case. */
        const engW = engTier === 2 ? 6 : engTier === 1 ? 4 : 3;
        putBits(bits, engineOf(program) & ((1 << engW) - 1), engW);
        putBits(bits, si, 5);
        putBits(bits, days, 3);
        putBits(bits, gi, 2);
        putBits(bits, ei, 2);
        putBits(bits, weeks, 5);
        putBits(bits, pi, 2);
        putBits(bits, ssi, sessionBits);
        putBits(bits, c.percentScheme ? 1 : 0, 1);
        putBits(bits, c.noBodyweight ? 1 : 0, 1);
        emitEquip(ver);
        putBits(bits, seed, 32);
        putBits(bits, programFingerprint(program), 16); // hash of the FINAL layout — swaps included
    };
    // v1 (2-bit session) stays the compact, backward-compatible code — but only for the s20/s40 sessions it
    // can represent and only when unedited. Anything else (a longer session, or coach overrides) emits v3,
    // whose 3-bit session covers every length. v2 is still DECODED for codes already in the wild.
    const needsWideSession = ssi > 1;
    const emitOverrides = () => {
        putBits(bits, overrides.length, 5);
        overrides.forEach(o => { putBits(bits, 0, 3); putBits(bits, o.dayIdx, 3); putBits(bits, o.slot, 4); putBits(bits, o.exIdx, 9); }); // record type 0 = SWAP
    };
    // deload is NOT in v1/v3, so those decode to the app default of true (see the decoder). A program
    // that deliberately has no deload week can only be expressed by v4.
    /* Force v5 for anything the legacy field cannot represent EXACTLY. Two cases:
         - an equipment id past bit 16, which the old mask simply cannot hold; and
         - any coarse id that now implies finer ones. A v1-v4 code is expanded on decode (a code minted
           before the split said "machine" and must still get the machines), and there is no way to tell
           an old such code from a new one. So a NEW code carrying a coarse id must not use the legacy
           field at all, or its own kit would be silently widened on the way back in. */
    const have = c.equipment || [];
    const needsV5 = CODE_EQUIP.some((id, i) => i >= 16 && have.includes(id))
        || Object.keys(LEGACY_EQUIP_IMPLIES).some(parent => have.includes(parent));
    const needsV4 = focusIdx.length || schemeIdx || assistIdx || c.deload !== true;
    /* v4 and v5 carry an identical tail, so it is emitted from one place. It was written out twice,
       which is a silent decoder break waiting to happen: add a field to one branch, forget the other,
       and every code minted at the un-updated version decodes with its fields shifted by that many
       bits — a corrupt program rather than a rejected one. */
    const emitFocusTail = () => {
        putBits(bits, focusIdx.length, 4);
        focusIdx.forEach(i => putBits(bits, i, 5)); // PART_ORDER index, 17 parts in a 5-bit field
        putBits(bits, schemeIdx, 4); // 0 = none, else CODE_SCHEMES index + 1
        putBits(bits, assistIdx, 3); // 0 = none, else CODE_ASSIST index + 1
        putBits(bits, c.deload === true ? 1 : 0, 1);
    };
    if (needsV5) {
        emitBase(5, 3);
        emitOverrides();
        emitFocusTail();
    }
    else if (needsV4) {
        // v4 — everything v3 carries, plus the muscle-focus list. Emitted ONLY when there is a focus, so
        // no existing program's code changes shape: a v1 code minted before v4 existed still mints as v1.
        // Older installs reject ver 4 outright ("made by a newer version") rather than misparsing it.
        emitBase(4, 3);
        emitOverrides();
        emitFocusTail();
    }
    else if (overrides.length === 0 && !needsWideSession) {
        emitBase(1, 2); // compact v1
    }
    else {
        emitBase(3, 3); // v3: full session + optional overrides
        emitOverrides();
    }
    let sum = 0;
    for (let i = 0; i < bits.length; i += 8)
        sum = (sum + getBits(bits, i, 8)) & 0xff;
    putBits(bits, sum, 8);
    const out = bitsToCode(bits);
    /* BACKSTOP — never hand out a code that cannot rebuild what it claims to.
       The percentScheme bug shipped because minting and importing were never checked against each
       other: six templates happily produced codes that failed the fingerprint test on every import,
       so the recipient got an error and the sharer never knew. Decoding our own output and
       regenerating from it is the only check that actually proves the round trip. Any config field
       the schema silently drops now surfaces HERE, as a refusal to encode, instead of as a broken
       code in someone else's hands. */
    try {
        const back = decodeProgramCode(out);
        if (!back.ok)
            return null;
        const rebuilt = generateProgram(back.config, [], null, back.seed, back.engineV || ENGINE_V);
        (back.overrides || []).forEach(o => {
            if (o.type === "swap" && rebuilt.days[o.dayIdx] && o.slot < rebuilt.days[o.dayIdx].exercises.length && EX_BY_ID[o.exId]) {
                rebuilt.days[o.dayIdx].exercises[o.slot] = o.exId;
            }
        });
        if (programFingerprint(rebuilt) !== programFingerprint(program))
            return null;
    }
    catch {
        return null;
    }
    return out;
}

function decodeProgramCode(str) {
    const bits = codeToBits(str);
    if (!bits || bits.length < 102)
        return { ok: false, reason: "That doesn't look like a program code." };
    const rawVer = getBits(bits, 0, 4);
    if (rawVer < 1 || rawVer > 15)
        return { ok: false, reason: "That code was made by a newer version of Pursuit Iron." };
    /* Versions 6-10 are versions 1-5 with a FOUR-bit engine field, and 11-15 the same with a SIX-bit
       one (v635, ENGINE_V 16). Everything after the engine is byte-for-byte identical in all three, so
       the rest of this decoder reads `ver` and never needs to know which tier it came from. */
    const engBits = rawVer >= 11 ? 6 : rawVer >= 6 ? 4 : 3;
    const ver = rawVer >= 11 ? rawVer - 10 : rawVer >= 6 ? rawVer - 5 : rawVer;
    let at = 4;
    const take = (n) => { const v = getBits(bits, at, n); at += n; return v; };
    const engineV = take(engBits);
    const split = CODE_SPLITS[take(5)], days = take(3), goal = CODE_GOALS[take(2)], experience = CODE_EXP[take(2)];
    const weeks = take(5), progression = CODE_PROG[take(2)], session = CODE_SESSION[take(ver >= 3 ? 3 : 2)];
    const percentScheme = !!take(1), noBodyweight = !!take(1);
    let equipment;
    if (ver >= 5) {
        if (take(1))
            equipment = [...CODE_EQUIP]; // "has everything"
        else {
            const n = take(7), picked = [];
            for (let i = 0; i < n; i++) {
                const on = take(1);
                if (on && i < CODE_EQUIP.length)
                    picked.push(CODE_EQUIP[i]);
            }
            equipment = picked;
        }
    }
    else {
        const mask = take(16);
        equipment = expandLegacyEquipment(CODE_EQUIP.filter((_, i) => i < 16 && (mask & (1 << i))));
    }
    const seed = take(32) >>> 0, fp = take(16);
    const overrides = [];
    if (ver >= 2) {
        const oc = take(5);
        for (let i = 0; i < oc; i++) {
            const type = take(3);
            if (type === 0) { // SWAP record
                const dayIdx = take(3), slot = take(4), exIdx = take(9);
                const exId = CODE_EXERCISES[exIdx];
                if (!exId)
                    return { ok: false, reason: "That code references an exercise this version of Pursuit Iron doesn't have. Update, then try again." };
                overrides.push({ type: "swap", dayIdx, slot, exId });
            }
            else {
                return { ok: false, reason: "That code uses a newer coaching feature. Update Pursuit Iron to import it." };
            }
        }
    }
    // v4 tail: the muscle-focus list. Read after the overrides block so v2/v3 parsing is byte-identical.
    const focusList = [];
    let schemeName = null, assistName = null;
    /* v1-v3 never encoded `deload`, so it arrived as undefined and generateProgram read that as FALSE —
       silently deleting the deload week from every imported program. The field is unrecoverable from
       those codes, so restore the app's own default (true) rather than keep dropping a week. Safe to
       change retroactively: programFingerprint hashes the layout only and is identical either way, so
       codes already in the wild still pass their own integrity check. */
    let deload = true;
    if (ver >= 4) {
        const fc = take(4);
        for (let i = 0; i < fc; i++) {
            const part = PART_ORDER[take(5)];
            if (!part)
                return { ok: false, reason: "That code references a muscle group this version of Pursuit Iron doesn't have. Update, then try again." };
            focusList.push(part);
        }
        const si2 = take(4);
        if (si2) {
            const named = CODE_SCHEMES[si2 - 1];
            if (!named)
                return { ok: false, reason: "That code uses a training scheme this version of Pursuit Iron doesn't have. Update, then try again." };
            schemeName = named;
        }
        const ai = take(3);
        if (ai) {
            const named = CODE_ASSIST[ai - 1];
            if (!named)
                return { ok: false, reason: "That code uses supplemental work this version of Pursuit Iron doesn't have. Update, then try again." };
            assistName = named;
        }
        deload = !!take(1);
    }
    const bodyLen = at; // everything before the checksum
    const sum = getBits(bits, bodyLen, 8);
    const body = bits.slice(0, bodyLen); // slice so the final 8-bit chunk zero-pads (matches encode)
    let calc = 0;
    for (let i = 0; i < bodyLen; i += 8)
        calc = (calc + getBits(body, i, 8)) & 0xff;
    if (calc !== sum)
        return { ok: false, reason: "That code looks mistyped — check it and try again." };
    if (!split || !goal || !experience || !progression || !days)
        return { ok: false, reason: "That code is damaged." };
    const config = { split, days, goal, experience, weeks, progression, session, percentScheme, noBodyweight, equipment, deload };
    // Only attach focus for v4. A v1-v3 config must stay EXACTLY the shape it was before v4 existed, or
    // a code minted years ago could regenerate a different layout and fail its own fingerprint check.
    if (ver >= 4) {
        const focus = {};
        focusList.forEach(part => { focus[part] = (focus[part] || 0) + 1; });
        config.focus = focus;
        config.focusList = focusList;
        if (schemeName)
            config.percentScheme = schemeName; // restore the NAMED scheme, not just "on"
        if (assistName)
            config.assistance = assistName;
    }
    return { ok: true, ver, engineV, seed, fp, overrides, config };
}

function gallerySubmission(program) {
    const code = encodeProgramCode(program);
    if (!code)
        return { ok: false };
    const dec = decodeProgramCode(code);
    const coached = dec.ok ? (dec.overrides || []).length : 0;
    const preview = (program.days || []).map(d => ({
        day: d.label || d.baseLabel || "Day",
        lifts: (d.exercises || []).map(id => EX_BY_ID[id]?.name).filter(Boolean)
    }));
    const entry = { code, title: String(program.name || "Shared program"), author: "", tags: [], note: "" };
    return { ok: true, code, entry, preview, coached, config: dec.ok ? dec.config : null };
}

function galleryIssueBody(sub) {
    if (!sub || !sub.ok)
        return "";
    const L = ["### Add to `gallery.json`", "", "```json", JSON.stringify(sub.entry, null, 2), "```", "",
        "**Decoded preview** — this is exactly what the code rebuilds, review before merging:", ""];
    sub.preview.forEach(p => L.push(`- **${p.day}:** ${p.lifts.join(", ") || "—"}`));
    L.push("");
    if (sub.config)
        L.push(`Split: \`${sub.config.split}\` · ${sub.config.days} days · ${sub.config.goal}${sub.coached ? ` · ${sub.coached} coach edit${sub.coached === 1 ? "" : "s"}` : ""}`);
    L.push("", "_Submitted from Pursuit Iron. The code carries a fingerprint that must match on import, so the preview above can't drift from what imports._");
    return L.join("\n");
}

function propagateCycleEditsPure(edited, before, siblings, adapt) {
    if (adapt) {
        const subs = new Map();
        (edited.days || []).forEach((d, di) => {
            const src = before?.days?.[di];
            if (!src)
                return;
            (d.exercises || []).forEach((id, i) => {
                const old = src.exercises?.[i];
                if (old && old !== id && !d.exercises.includes(old))
                    subs.set(old, id);
            });
        });
        if (!subs.size)
            return siblings;
        return siblings.map(p => {
            let touched = false;
            const days = p.days.map(d => {
                let dayChanged = false;
                const exercises = (d.exercises || []).map(id => {
                    const to = subs.get(id);
                    if (!to || d.exercises.includes(to))
                        return id;
                    dayChanged = true;
                    touched = true;
                    return to;
                });
                return dayChanged ? { ...d, exercises } : d;
            });
            return touched ? remapNextShellRoster(p, days, EXERCISES) : p;
        });
    }
    /* NON-ADAPT (default): every phase trains the same lifts, so a genuine exercise change is meant
     * to carry across the cycle. "Genuine" is the word this used to skip.
     *
     * This rebuilt EVERY day of EVERY sibling from the just-saved block's CURRENT exercise list,
     * unconditionally — it never asked whether that list had actually changed from what it replaced.
     * Two ways that broke, both real, both silent:
     *
     *   1. AN ENGINE UPDATE. Bumping engineV regenerates a block from its OWN seed under the new
     *      engine, and engine 5 only adjusts set counts via slotBias — days.exercises comes back
     *      byte-identical. Nothing was edited. But this ran on every Save regardless, so tapping
     *      "Update" after a pure version bump overwrote every sibling's exercise selection with this
     *      block's (differently-seeded, coincidental) list. Haiden: "Updating the engine on some
     *      programs doesn't work" — the block being updated always updated fine; its SIBLINGS were
     *      the casualty, corrupted by a change that had nothing to do with them.
     *   2. A SINGLE SWAP. Changing one exercise on one day rebuilt every OTHER day on every sibling
     *      too — fixing an accessory on day 1 silently overwrote days 2 through N for any sibling
     *      whose own seed had picked something different there.
     *
     * Same mistake both times: treating "this block was saved" as "everything in it is a deliberate
     * choice to propagate." Only a day that actually DIFFERS from what it was before this save is a
     * choice; every other day, and the supersets keyed to it, are carried through untouched — on
     * every sibling. A day is compared by exercise IDENTITY at each position, not merely by length:
     * same count, different lifts, still counts as changed. */
    let anyDayChanged = false;
    const dayChanged = edited.days.map((src, di) => {
        const prevSrc = before?.days?.[di];
        const same = !!prevSrc && src.exercises.length === prevSrc.exercises.length
            && src.exercises.every((id, i) => id === prevSrc.exercises[i]);
        if (!same)
            anyDayChanged = true;
        return !same;
    });
    if (!anyDayChanged)
        return siblings; // e.g. an engine update that only touched slotBias — nothing to carry
    return siblings.map(p => {
        let touched = false;
        const touchedIds = new Set();
        const days = p.days.map((d, di) => {
            const src = edited.days[di];
            if (!src || !dayChanged[di])
                return d; // this day wasn't part of what changed — leave it alone
            touched = true;
            touchedIds.add(d.id);
            const want = d.exercises.length; // keep THIS block's slot count (science-fit per goal)
            // Take edited exercises in order; if this block has fewer slots, truncate (compounds lead).
            let next = src.exercises.slice(0, want);
            // If this block has MORE slots than the edited one, keep the originals for the extra tail.
            if (next.length < want)
                next = next.concat(d.exercises.slice(next.length, want));
            // Remap primary/T2 to the new positions where possible. WITHOUT this the sibling keeps a bare
            // INDEX into a list that just changed underneath it, so a reorder silently re-points the
            // block's primary at whatever lift now occupies that slot.
            const srcPrimaryId = src.exercises[src.primaryIndex];
            const pIdx = next.indexOf(srcPrimaryId);
            const srcT2Id = src.t2Index != null ? src.exercises[src.t2Index] : null;
            const t2Idx = srcT2Id != null ? next.indexOf(srcT2Id) : -1;
            return { ...d, exercises: next, primaryIndex: pIdx > -1 ? pIdx : d.primaryIndex, t2Index: t2Idx > -1 ? t2Idx : (d.t2Index != null && d.t2Index < want ? d.t2Index : null) };
        });
        if (!touched)
            return p; // nothing on this sibling actually needs to change
        // Rebuild supersets ONLY for the days that changed — an untouched day keeps its own pairings,
        // rather than losing them to a rebuild keyed off a day it was never part of.
        let nss = { ...(p.ss || {}) };
        days.forEach(d => { if (!touchedIds.has(d.id))
            return; Object.keys(nss).forEach(k => { if (k.startsWith(`${d.id}:`))
            delete nss[k]; }); });
        if (edited.ss) {
            days.forEach((d, di) => {
                if (!touchedIds.has(d.id))
                    return;
                const src = edited.days[di];
                if (!src)
                    return;
                Object.keys(edited.ss).forEach(k => {
                    const [dayId, slot] = k.split(":");
                    if (dayId === src.id) {
                        const exId = src.exercises[Number(slot)];
                        const ni = d.exercises.indexOf(exId);
                        if (ni > -1 && ni < d.exercises.length - 1)
                            nss[`${d.id}:${ni}`] = true;
                    }
                });
            });
        }
        return { ...remapNextShellRoster(p, days, EXERCISES), ss: nss };
    });
}

function cycleConfigForStandaloneProgram(program, name) {
    const cfg = { ...(program?.config || {}) };
    const days = Array.isArray(program?.days) ? program.days : [];
    const n = Math.max(1, days.length || Math.round(Number(cfg.days)) || 1);
    cfg.days = n;
    cfg.name = String(name || program?.name || cfg.name || 'Training Cycle').trim() || 'Training Cycle';
    cfg.endless = false;
    const currentSplit = String(cfg.split || '').toLowerCase();
    if (!currentSplit || currentSplit === 'custom') {
        const tags = days.map(d => `${d?.type || ''} ${d?.label || ''}`.toLowerCase());
        const has = word => tags.some(t => t.includes(word));
        const ulppl = n === 5 && ['upper','lower','push','pull','legs'].every(has);
        const upperLower = n >= 2 && tags.every(t => t.includes('upper') || t.includes('lower'));
        const ppl = n >= 3 && tags.every(t => t.includes('push') || t.includes('pull') || t.includes('legs')) && ['push','pull','legs'].every(has);
        cfg.split = ulppl ? 'ulppl' : upperLower ? 'upper_lower' : ppl ? 'ppl' : 'full_body';
    }
    return cfg;
}

function mergeStandaloneIntoGeneratedCycle(program, generated) {
    if (!program?.id || !generated?.cycle || !Array.isArray(generated.blocks) || generated.blocks.length < 2)
        throw new Error('A standalone conversion needs the current program plus at least one future engine block.');
    const generatedFuture = generated.blocks.slice(1);
    const cycle = { ...generated.cycle };
    const sourceMeta = Array.isArray(cycle.blockMeta) ? cycle.blockMeta : [];
    const current = JSON.parse(JSON.stringify(program));
    current.cycleId = cycle.id;
    current.cycleIndex = 0;
    current.blockLabel = sourceMeta[0]?.label || current.blockLabel || 'Current program';
    current.blockNote = 'Existing standalone program kept exactly as cycle Block 1';
    current.config = { ...(current.config || {}), endless: false, cyclePeriodization: true };
    generatedFuture.forEach((block, i) => { block.cycleId = cycle.id; block.cycleIndex = i + 1; });
    cycle.blockIds = [current.id, ...generatedFuture.map(b => b.id)];
    cycle.blockMeta = cycle.blockIds.map((id, i) => i === 0
        ? { ...(sourceMeta[0] || {}), id, label: current.blockLabel, note: current.blockNote, preview: false, legacyCurrent: true }
        : { ...(sourceMeta[i] || {}), id, preview: true });
    cycle.blockMeta = cycleBlockMetadata(cycle, [current, ...generatedFuture]);
    cycle.activeBlock = 0;
    cycle.startedAt = cycle.startedAt || Date.now();
    cycle.nextEngineCycle = { ...(cycle.nextEngineCycle || {}), legacyFirstBlockId: current.id, legacyFirstBlock: true,
        ...(Array.isArray(cycle.nextEngineCycle?.plannedBlocks) ? {
            plannedBlocks: cycle.nextEngineCycle.plannedBlocks.map((block, i) => i === 0 ? { ...block, weeks: weeksOf(current) } : block)
        } : {}) };
    return { cycle, currentProgram: current, blocks: generatedFuture, allBlocks: [current, ...generatedFuture], baseRequest: generated.baseRequest };
}

function advanceLegacyFirstCycleBlock(cycle, programs) {
    const legacyId = cycle?.nextEngineCycle?.legacyFirstBlockId;
    if (!legacyId || Number(cycle?.activeBlock || 0) !== 0 || cycle?.blockIds?.[0] !== legacyId)
        return null;
    const nextId = cycle?.blockIds?.[1];
    const nextProgram = (programs || []).find(p => p?.id === nextId);
    if (!nextId || !nextProgram?.config)
        throw new Error('The first future cycle block is missing; the cycle was left unchanged.');
    const blockMeta = (cycle.blockMeta || []).map((m, i) => i === 1 ? { ...m, preview: false, activatedFromLegacy: legacyId } : m);
    return { cycle: { ...cycle, activeBlock: 1, blockMeta }, nextProgram };
}

function isAxialLoad(ex, _eng = 1) {
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

function e1rm(w, reps) { return Math.round(e1rmRIR(w, reps || 0, 0)); }

const EPLEY_SLOPE = 30;

const E1RM_REP_CAP = 12;

function e1rmRIR(w, reps, rir, cap = E1RM_REP_CAP, slope = EPLEY_SLOPE) { return w * (1 + (Math.min(reps, cap) + rir) / slope); }
export { AXIAL_ADJACENT_COST, CODE_ALPHA, CODE_ASSIST, CODE_CARRIED_KEYS, CODE_EQUIP, CODE_EXERCISES, CODE_EXP, CODE_GOALS, CODE_IGNORED_KEYS, CODE_PROG, CODE_SCHEMES, CODE_SESSION, CODE_SPLITS, E1RM_REP_CAP, ENGINE_RULES, EPLEY_SLOPE, IDEAL_SLOTS, PART_ORDER, advanceLegacyFirstCycleBlock, axialCost, bitsToCode, buildWeekPlan, codeToBits, cycleConfigForStandaloneProgram, dayMuscleLoad, dayOverlap, decodeProgramCode, e1rm, e1rmRIR, encodeProgramCode, endlessStalled, engHas, engLacks, expandLegacyEquipment, galleryIssueBody, gallerySubmission, getBits, historyDayIndex, isAxialLoad, mergeStandaloneIntoGeneratedCycle, nextDueDayId, nextScheduledIndex, nextSessionCursor, programFingerprint, programSavePlan, propagateCycleEditsPure, putBits, ruleEngine, sameProgramContent };
