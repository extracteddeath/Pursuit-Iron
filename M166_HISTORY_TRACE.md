# M166 history volume trace

## 1. \bh\.volume\b @ 1075269

```js
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
```

## 2. \bvolume\s*: @ 1111436

```js
e 27.9%
               MVIC lateral against 33.3% anterior (and 30.3% for an actual lateral raise), and longitudinal
               imaging shows the lateral head grows minimally from vertical pressing because it acts as a
               STABILIZER there, not the force transducer. Empirical range 0.25-0.33. At 0.5 this reported
               side delts under MEV 41 -> 27; at the defensible 0.3 it is 41 -> 36. The larger number was
               the more attractive one and the wrong one. */
            /* HELD — see the coefficient note in ENGINE_RULES. Commented, not guarded: engHas THROWS on
               an unregistered rule.
               WHEN RE-ADDING: gate on an overhead movePattern and add sets * the chosen coefficient to
               side_delts. The call is written out rather than left as live syntax because gates/harness 11
               scans for engHas("name") literals and a held rule name must not appear among them. */
            secondaryOf(ex, program.engineV).forEach(([p, f]) => {
                if (p === "shoulders") {
                    const pat = movePattern(ex);
                    add(pat === "row" || pat === "pulldown" ? "rear_delts" : "front_delts", sets * f);
                }
                else if (p === "traps")
                    add("upper_traps", sets * f);
            });
        });
    });
    return sub;
}
/* ---- logged per-muscle volume: ONE window walk, ONE definition of "a set" ----------------------
 *
 * There used to be two of these. `weeklyActualVolume` and `loggedVolume` were the same function
 * written 470 lines apart — same window, same direct-plus-fractional credit, differing only in a
 * `<` versus `<=` and a parameterised `until` — and `volumeAdvice` called one of them while calling
 * `underRecoveredWeekly` (which counts the other way) in the very next line. There is now one.
 *
 * Both of them counted `p.sets.length` raw. Since v493 a logged row is not necessarily a working
 * set: myo-rep minis and drop sets are persisted with `sub: true` precisely so they can be told
 * apart, and `isWorkSet` is the rule that tells them apart. Measured, three myo minis on an overhead
 * press took logged shoulders from 3 to 6 and front delts from 3 to 6 — a doubling, on the card
 * captioned "sets you've actually logged", and then read against MEV/MRV landmarks that are defined
 * in working sets. An extension is stimulus, but it is not a set in the sense the landmark means,
 * and the planned side (weeklyVolume) has never counted it as one. Warm-ups are already excluded
 * upstream, at the point history is written; subs were not, and this is where that shows.
 *
 * The trailing-window walk is shared so the two variants cannot drift apart again: `logWindow` finds
 * the working sets per exercise, and each variant only decides where to put the credit. */
function logWindow(history, days, until, visit) {
    const since = until - days * 86400000;
    (history || []).forEach(h => {
        if (!h || h.date <= since || h.date > until)
            return;
        Object.entries(h.perf || {}).forEach(([id, p]) => {
            const ex = EX_BY_ID[id];
            if (!ex || !p)
                return;
            // isWorkSet, NOT workSetsOf: workSetsOf also demands w > 0, which is right for a 1-RM estimate
            // and wrong here — a set of unweighted pull-ups is logged at w = 0 and is still a set of back.
            const n = setsOf(p).filter(isWorkSet).length; // NOT p.sets.length — extensions are not sets
            if (n)
                visit(ex, n);
        });
    });
}
/* Per-muscle sets ACTUALLY logged in the trailing window (default 7 days), counted the SAME way
   weeklyVolume/weeklySubVolume count a program — direct sets to ex.part (1.0) plus fractional
   secondary cr
```

## 3. perf\s*: @ 1306037

```js
a = {
        ...older, ...newer, // scalar settings: newer blob wins
        v: STORE_VERSION,
        savedAt: Math.max(A.savedAt || 0, B.savedAt || 0),
        tombs,
        history: hist,
        saved: byId(A.saved, B.saved, "programs"),
        cycles: byId(A.cycles, B.cycles, "cycles"),
        /* Gyms merge by id with the same tombstone rules as programs and history. Previously they were
           not merged at all: the whole list came from whichever store had the later savedAt, so a gym
           added on one device was destroyed by the next sync from another. */
        gyms: (() => {
            const merged = byId(A.gyms, B.gyms, "gyms");
            return merged.length ? merged : (newer.gyms || older.gyms || []); // never sync to zero gyms
        })(),
        // The newer device's choice wins, unless the other device deleted that gym — then fall back
        // rather than leaving the app pointed at something that is gone.
        activeGymId: (() => {
            const surviving = byId(A.gyms, B.gyms);
            const want = newer.activeGymId || older.activeGymId;
            return surviving.some(g => g.id === want) ? want : (surviving[0] && surviving[0].id) || want;
        })(),
        custom: byId(A.custom, B.custom, "custom"),
        banned: [...new Set([...(A.banned || []), ...(B.banned || [])])],
        bwLog,
        measurements: meas,
        perf: mapMerge("perf"),
        exNotes: mapMerge("exNotes"),
        exSetup: mapMerge("exSetup"),
        goals: mapMerge("goals"),
        // Research evidence is append-only. Merge trials by trial id so restoring a backup can never
        // erase a completed control/treatment observation from the other device. The newer blob owns
        // the consent toggle, but both sides' evidence survives.
        canaryResearch: (() => {
            const ca = normalizeCanaryResearchState(A.canaryResearch), cb = normalizeCanaryResearchState(B.canaryResearch);
            const pref = aNewer ? ca : cb;
            const trials = new Map();
            [...ca.trials, ...cb.trials].forEach(t => { const prev = trials.get(t.trialId); if (!prev || (t.completedAt || t.startedAt || 0) >= (prev.completedAt || prev.startedAt || 0))
                trials.set(t.trialId, t); });
            return { ...pref, trials: [...trials.values()].slice(-200) };
        })()
    };
    return { data, stats };
}
// Carry a loaded/imported blob forward to STORE_VERSION. Un-versioned blobs predate versioning and are
// treated as v1. Returns the input untouched for a fresh install (null) or non-object junk so the
// caller's existing validity checks still apply.
function migrateStore(raw) {
    if (!raw || typeof raw !== "object")
        return raw;
    let d = raw;
    let v = typeof d.v === "number" ? d.v : 1;
    while (v < STORE_VERSION) {
        v += 1;
        const fn = STORE_MIGRATIONS[v];
        if (fn)
            d = fn(d);
    }
    return d.v === STORE_VERSION ? d : { ...d, v: STORE_VERSION };
}
// Reject malformed containers before any React state is hydrated. Missing fields remain valid for
// older saves. A newer schema must never be silently stamped with this build's older version.
function parseStoredData(raw) {
    try {
        const d = JSON.parse(raw);
        const object = v => !!v && typeof v === "object" && !Array.isArray(v);
        if (!object(d))
            throw new Error("The saved data isn't a training backup.");
        if (typeof d.v === "number" && d.v > STORE_VERSION)
            throw new Error("This data needs a newer version of Pursuit Iron. Update the app, then try again.");
        for (const k of ["saved", "history", "cycles", "custom", "gyms", "bwLog", "banned", "equipDefault"]) {
            if (d[k] != null && !Array.isArray(d[k]))
        
```

## 4. \bvolume\s*: @ 1946360

```js
ROM A `dir` FIELD. My first version filtered on `t.dir ===
       "up"`, which does not exist on a trend — exerciseTrends returns {id, name, part, sessions[]} and
       nothing else. Every lift silently failed the filter, so `siblingsProgressing` was always empty
       and the exercise-specific kind could never fire. It compiled, ran, and did nothing.
       A lift counts as still moving when its most recent session beats the best of the window before
       it: the same e1RM series plateauOf reads, asked the opposite question. Six sessions to match the
       window plateauOf uses, so a lift and its siblings are judged over the same span. */
    const progressingByPart = {};
    trends.forEach(t => {
        const px = EX_BY_ID[t.id];
        const ss = (t && t.sessions) || [];
        if (!px || ss.length < 3)
            return;
        const win = ss.slice(-6);
        const latest = win[win.length - 1] && win[win.length - 1].best;
        const priorBest = Math.max(...win.slice(0, -1).map(x => x.best || 0));
        if (!(latest > priorBest))
            return; // no recent PR — not moving
        (progressingByPart[px.part] = progressingByPart[px.part] || []).push(t.id);
    });
    const plateauCtx = (id) => {
        const px = EX_BY_ID[id];
        if (!px || !ledger)
            return null;
        const L = landmarkOf(px.part) || {};
        return {
            volume: ledger[px.part],
            floor: L.mev,
            partLabel: (PART_LABEL[px.part] || px.part).toLowerCase(),
            siblingsProgressing: (progressingByPart[px.part] || []).filter(x => x !== id)
        };
    };
    const plateauFor = (id, mergedSets, readiness) => plateauSplitByDay(hs, id, (t, d) => classifyPlateau(t, (perLiftDay[id] || {})[d] || [], readiness, plateauCtx(id)), () => classifyPlateau(trendById[id], mergedSets, readiness, plateauCtx(id)));
    const now = Date.now();
    for (const [id, sess] of Object.entries(perLift)) {
        const ex = EX_BY_ID[id];
        const last = sess[sess.length - 1];
        // gain velocity: least-squares slope of e1RM over the trailing 6 sessions, per week
        const win = sess.slice(-6);
        let slope = 0, conf = 0;
        if (win.length >= 3) {
            const t0 = win[0].date;
            const xs = win.map(s => (s.date - t0) / (7 * 86400000)); // weeks
            const ys = win.map(s => s.e1rm);
            const mx = xs.reduce((a, b) => a + b, 0) / xs.length;
            const my = ys.reduce((a, b) => a + b, 0) / ys.length;
            let num = 0, den = 0;
            for (let i = 0; i < xs.length; i++) {
                num += (xs[i] - mx) * (ys[i] - my);
                den += (xs[i] - mx) ** 2;
            }
            slope = den > 0 ? num / den : 0;
            conf = clamp((win.length - 2) / 4, 0, 1);
        }
        lifts[id] = {
            e1rm: Math.round(last.e1rm * 10) / 10,
            e1rmSlope: Math.round(slope * 100) / 100,
            slopeConfidence: Math.round(conf * 100) / 100,
            sessions: sess.length,
            /* A history entry with no usable date yields no staleness — NOT NaN. Every entry this app
               writes carries `date` (finish() stamps Date.now()), but an IMPORTED backup is arbitrary JSON
               from another device, a hand-edit, or an older schema, and `migrateStore` deliberately does not
               drop entries for missing fields — losing a logged workout is worse than carrying an odd one.
               So a dateless entry does reach here, and `now - undefined` is NaN, which then propagates into
               anything that later reads this field. Found by fuzzing the model with corrupt-but-plausible
               history; nothing consumes `staleness` yet, so it was inert — but a NaN sitting in a model
     
```

## 5. \bvolume\s*: @ 1964593

```js
e === "compound") || exs[0];
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
   of the block. Neither piece of information is dropped — the note is now simply the LAST fact in
   the feed, which is where the feed's own rule puts it: what changed today's numbers outranks what
   merely describes them. With no personal facts yet (a first block, a fresh install) the note is
   the headline, so a new lifter still gets the why. Pure and exported so the ORDER is gate-pinnable
   without a render. */
function workoutPlanCoachFact(program, day, weekIndex) {
    if (!program || program.quick)
        return null;
    const cfg = program.config || {};
    const phaseRaw = String(program.nextEngine?.phase || program.nextEngine?.program?.phase || "").replace(/_/g, " ").toLowerCase();
    if (cfg.deload && weekIndex > weeksOf(program) || phaseRaw.includes("deload") || phaseRaw.includes("recovery")) {
        return { icon: "plan", short: "Today: recover", title: "Today is a recovery session", text: "Keep the work easy, leave plenty in reserve, and finish feeling better than you started." };
    }
    if (phaseRaw.includes("peak")) {
        return { icon: "plan", short: "Today: crisp heavy reps", title: "Today: prioritize crisp heavy reps", text: "Take the full rest, keep technique tight, and stop before reps turn into grinders." };
    }
    if (phaseRaw.includes("strength") || cfg.goal === "strength") {
 
```

## 6. perf\s*: @ 2006352

```js
ported defect has lived.
 *
 * The simulated lifter is deliberately IMPERFECT — hitting the top of the range every session would
 * only ever exercise the "advance" branch. `behaviour` walks the cases that actually occur: hitting
 * targets, falling short, and repeating the same numbers for weeks (which is what a stall looks like
 * and what the plateau and style-override branches key on). */
function simulateAndAudit(o) {
    const { program, unit = "lb", weeks = null, behaviour = "hit" } = o || {};
    const findings = [];
    const history = [];
    let perf = {};
    const total = weeks || weeksOf(program);
    for (let w = 1; w <= total; w++) {
        program.days.forEach(day => {
            /* ⚠ ASK WHAT THE SLOT HOLDS **THIS WEEK**. A paired slot trains a different exercise on
               alternate weeks, so reading `day.exercises[slot]` audits and logs the wrong lift on half of
               them. `exerciseAt` is the one answer to that question; it returns the slot's own exercise
               when no pair is set, so this is inert until pairing ships. */
            day.exercises.forEach((_id, slot) => {
                const id = exerciseAt(program, day, slot, w);
                const ex = EX_BY_ID[id];
                if (!ex)
                    return;
                findings.push(...auditSets({ program, day, ex, slot, weekIndex: w, unit, perf, history, dayPerf: perf }));
            });
            // Log the day, so the NEXT week is prescribed from something the lifter did.
            const logged = {};
            day.exercises.forEach((_id, slot) => {
                const id = exerciseAt(program, day, slot, w);
                const ex = EX_BY_ID[id];
                if (!ex)
                    return;
                const isPrimary = slot === day.primaryIndex;
                let sets = null, sug = null;
                try {
                    const cell = computeCell(program, day, ex.id, slot, w);
                    const range = cellRepRange(cell, program, ex, isPrimary);
                    sug = sessionSuggestion(program, day, slot, perf, unit, w, history);
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
                   lifter could set, the eng
```

## 7. perf\s*: @ 2010033

```js
 step on a hack squat or a leg press — so the harness fabricated a load no
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
    const [coachExpan
```

## 8. \bsetsDone\s*: @ 2092366

```js
t = x.target?.reps != null ? String(x.target.reps) : null;
                    // PROVENANCE TRAVELS WITH THE SET. `sub` marks a drop set or myo mini — an extension of
                    // the set above, not a working set. Every reader downstream already filters on it
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
    return (_jsxs("div", { ref: workoutRoot, className: "wpb-page-shell wpb-workout", "data-keyboard-open": keyboardOpen ? "true" : "false", style: { position: "relative", maxHeight: viewportHeight || undefined }, children: [_jsxs("div", { className: "wpb-workout-header", style: { padding: "14px 14px 10px", borderBottom: `1px solid ${C.borderSoft}` }, children: [_jsxs("div", { style: { display: "flex", alignItems: "center", gap: 8, minWidth: 0 }, children: [_jsx("button", { onClick: () => setConfirmExit(true), "aria-label": "Leave workout", className: "pressable hit wpb-workout-close", style: { width: 40, height: 40, background: "none", border: "none", color: C.text, cursor: "pointer", padding: 0, flexShrink: 0, display: "flex", alignItems: "center", justifyContent: "center", borderRadius: 11 }, children: _jsx(X, { size: 23 }) }), _jsxs("button", { onClick: onTimerTap, onPointerDown: beginTimerHold, onPointerMove: moveTimerHold, onPointerUp: endTimerHold, onPointerCancel: endTimerHold, onContextMenu: e => e.preventDefault(), "aria-label": runPaused ? "Resume workout timer" : "Pause workout timer", "aria-description": "Long press to reset timer", title: `${runPaused ? "Resume" : "Pause"} workout timer · hold to reset`, className: "pressable wpb-workout-timer", style: { display:
```

## 9. \btotalSets\s*: @ 2097426

```js
ren: focus ? _jsx(Minimize2, { size: 18 }) : _jsx(Maximize2, { size: 18 }) }), _jsx("button", { onClick: () => setSessionMenu(true), className: "pressable hit wpb-workout-header-action", title: "Workout options", "aria-label": "Workout options", style: { ...iconBtn(), width: 40, height: 40 }, children: _jsx(MoreHorizontal, { size: 20 }) }), _jsxs("button", { onClick: () => setFinishing(true), "aria-label": "Finish workout", className: "pressable hit wpb-workout-finish wpb-workout-header-action", style: narrowSet
                                            ? { ...iconBtn(), width: 40, height: 40, background: C.accent, borderColor: C.accent, color: C.accentText }
                                            : { ...iconBtn(), width: "auto", height: 40, padding: "0 14px", gap: 6, background: C.accent, borderColor: C.accent, color: C.accentText, fontWeight: 700, fontSize: 15 }, children: [_jsx(CheckCircle2, { size: narrowSet ? 20 : 17, style: { flexShrink: 0 } }), narrowSet ? null : " Finish"] })] })] }), _jsx("div", { role: "progressbar", "aria-label": "Workout completion", "aria-valuemin": 0, "aria-valuemax": Math.max(1, totalSets), "aria-valuenow": doneSets, style: { marginTop: 8, height: 6, borderRadius: 999, background: C.bg2, overflow: "hidden" }, children: _jsx("div", { className: "wpb-bar", style: { background: C.accent, transform: `translateX(-${(1 - (totalSets ? doneSets / totalSets : 0)) * 100}%)` } }) }), _jsxs("div", { style: { display: "flex", justifyContent: "space-between", fontSize: 11, color: C.muted, marginTop: 4 }, children: [_jsxs("span", { children: [doneSets, " / ", totalSets, " sets \u00B7 ", _jsxs("button", { onClick: () => setExList(true), className: "pressable", "aria-label": "Show today's exercises", style: { background: "none", border: "none", padding: 0, font: "inherit", color: C.accentInk, fontWeight: 700, cursor: "pointer", textDecoration: "underline", textUnderlineOffset: 2 }, children: ["ex ", exIdx + 1, "/", data.length] }), readiness && readiness.factor < 1 ? ` · ${readiness.label} −${Math.round((1 - readiness.factor) * 100)}%` : ""] }), _jsxs("span", { className: "mono", children: [Math.round(volume).toLocaleString(), " ", unit] })] }), !focus && !keyboardOpen && _jsx("div", { className: "wpb-hscroll wpb-ex-nav", style: { display: "flex", gap: 8, overflowX: "auto", overflowY: "hidden", touchAction: "pan-x pan-y", marginTop: 12, paddingBottom: 2 }, children: data.map((e, i) => {
                            const exDone = workOnly(e).length > 0 && workOnly(e).every(s => s.done);
                            const on = i === exIdx;
                            return (_jsx("button", { onClick: () => goEx(i), "aria-label": `${EX_BY_ID[e.id]?.name || "Exercise"} · exercise ${i + 1} of ${data.length}`, "aria-current": on ? "step" : undefined, className: "pressable hit wpb-ex-nav-step", "data-active": on ? "1" : "0", style: {
                                    flexShrink: 0, width: 44, minWidth: 44, height: 40, padding: 0, borderRadius: 12, cursor: "pointer",
                                    border: `1px solid ${on ? C.accent : exDone ? C.accentDim : C.border}`,
                                    background: on ? C.accent : C.card, color: on ? C.accentText : exDone ? C.accentInk : C.muted,
                                    display: "inline-flex", alignItems: "center", justifyContent: "center", fontWeight: 750, fontSize: 14,
                                    transition: "transform 220ms var(--e-spring), background-color 180ms var(--e-out), border-color 180ms var(--e-out), color 180ms var(--e-out)"
                                }, children: !on && exDone ? _jsx(Check, { size: 16, strokeWidth: 3 }) : (i + 1) }, i));
                        }) })] }), saveError && _jsxs("div", { role: "alert", style: {
```

## 10. perf\s*: @ 2193920

```js
.slot === day.primaryIndex, e.restCustom, restScale);
                                                const fmt = r => r >= 60 ? `${Math.floor(r / 60)}:${String(r % 60).padStart(2, "0")}` : `${r}s`;
                                                return [["Reps", cell.range], ["Effort", effortLabel(cell.rir, loadMode)], ["Sets", String(wTotal)], ["Rest", fmt(restNow)]].map(([k, v]) => (_jsxs("div", { style: { flex: 1, background: C.card, borderRadius: 12, padding: "11px 4px", textAlign: "center" }, children: [_jsx("div", { className: "mono", style: { fontSize: 15, fontWeight: 600, color: C.accentInk }, children: v }), _jsx("div", { style: { fontSize: 11, color: C.muted, marginTop: 2 }, children: k })] }, k)));
                                            })() }), (() => {
                                            const _lm = (() => { try {
                                                return buildLifterModel(history, dayPerf);
                                            }
                                            catch {
                                                return null;
                                            } })();
                                            const _mus = _lm?.muscles?.[ex.part] || null;
                                            const t = explainPrescription({
                                                program, day, ex, slot: exIdx, perf: dayPerf, history, weekIndex, unit,
                                                readiness: _mus ? _mus.readiness : null, readinessCtx: _mus
                                            });
                                            if (!t)
                                                return null;
                                            const summary = t.load.reason || t.decided.styleWhy || `This lift is using ${t.decided.style} progression for today's ${goalForDay(program, day)} session.`;
                                            return (_jsxs("section", { "aria-label": `Why ${ex.name} has this prescription`, style: { marginBottom: 12, padding: "12px 13px", borderRadius: 14, background: C.accentDim, border: `1px solid ${C.accent}33` }, children: [_jsxs("div", { style: { display: "flex", alignItems: "center", gap: 7, marginBottom: 6 }, children: [_jsx(HelpCircle, { size: 15, color: C.accentInk }), _jsx("div", { style: { fontSize: 13, fontWeight: 800, color: C.text }, children: "Why this?" })] }), _jsx("div", { style: { fontSize: 13, color: C.text, lineHeight: 1.45 }, children: summary }), _jsxs("div", { style: { display: "flex", flexWrap: "wrap", gap: 6, marginTop: 9 }, children: [_jsxs("span", { className: "mono", style: { fontSize: 11, fontWeight: 700, color: C.accentInk, background: C.bg2, padding: "4px 7px", borderRadius: 8 }, children: [t.decided.sets, " sets"] }), _jsxs("span", { className: "mono", style: { fontSize: 11, fontWeight: 700, color: C.accentInk, background: C.bg2, padding: "4px 7px", borderRadius: 8 }, children: [t.decided.repRange[0], "\u2013", t.decided.repRange[1], " reps"] }), _jsxs("span", { className: "mono", style: { fontSize: 11, fontWeight: 700, color: C.accentInk, background: C.bg2, padding: "4px 7px", borderRadius: 8 }, children: [t.decided.targetRIR, " RIR"] })] }), _jsxs("details", { style: { marginTop: 8 }, children: [_jsx("summary", { style: { cursor: "pointer", color: C.accentInk, fontSize: 12, fontWeight: 700, padding: "3px 0" }, children: "How it was chosen" }), _jsxs("div", { style: { display: "grid", gap: 5, marginTop: 7, fontSize: 12, lineHeight: 1.4 }, children: [_jsxs("div", { children: [_jsx("span", { style: { color: C.muted }, children: "Progression:" }), " ", _jsx("strong", { children: t.decided.style })] }), t.read.lastPerformance && _jsxs("div", { children: [_jsx("span", { style: { color: C.mute
```

## 11. \bvolume\s*: @ 2256099

```js
) => (_jsx("div", { style: { display: "flex", gap: 4 }, children: opts.map(([v, label]) => {
                                                            const on = val[part] === v;
                                                            return (_jsx("button", { onClick: () => setter(f => ({ ...f, [part]: on ? undefined : v })), className: "pressable", style: { flex: 1, padding: "6px 4px", borderRadius: 8, cursor: "pointer", fontSize: 11, fontWeight: 600, border: `1px solid ${on ? C.accent : C.border}`, background: on ? C.accentDim : C.card, color: on ? C.accentInk : C.muted, textAlign: "center" }, children: label }, v));
                                                        }) }));
                                                    return (_jsxs("div", { style: { background: C.card, borderRadius: 12, padding: "10px 12px", marginBottom: 8 }, children: [_jsxs("div", { style: { display: "flex", alignItems: "center", marginBottom: 8 }, children: [_jsx("span", { style: { flex: 1, fontSize: 15, fontWeight: 600 }, children: PART_LABEL[part] }), nudge && _jsx("span", { style: { fontSize: 11, fontWeight: 700, color: nudge.c }, children: nudge.t })] }), nudge && (nv.blocked || (nv.advice !== 0 && nv.applied !== nv.feedback)) && (_jsx("div", { style: { fontSize: 11, color: C.faint, marginTop: -4, marginBottom: 6, lineHeight: 1.4 }, children: nv.blocked ? nv.why : `from your logged volume: ${(volumeAdvice(history, { program }).find(a => a.part === part) || {}).reason || ""}` })), _jsxs("div", { style: { display: "flex", gap: 8 }, children: [_jsxs("div", { style: { flex: 1 }, children: [_jsx("div", { style: { ...eyebrow(), marginBottom: 4 }, children: "Pump" }), axis(fbPump, setFbPump, [["flat", "Flat"], ["good", "Good"], ["huge", "Huge"]])] }), _jsxs("div", { style: { flex: 1 }, children: [_jsx("div", { style: { ...eyebrow(), marginBottom: 4 }, children: "Recovery" }), axis(fbSore, setFbSore, [["sore", "Sore"], ["ontime", "On time"], ["fresh", "Fresh"]])] })] })] }, part));
                                                })] })] })), _jsx("button", { onClick: finish, className: "pressable", style: { width: "100%", padding: "15px", borderRadius: 16, border: "none", background: C.accent, color: C.accentText, fontSize: 15, fontWeight: 700, cursor: "pointer" }, children: "Save & finish" }), _jsxs("div", { className: "wpb-finish-share-row", style: { display: "grid", gridTemplateColumns: "repeat(2,minmax(0,1fr))", gap: 8, marginTop: 8 }, children: [_jsx("button", { onClick: () => {
                                                const durMin = Math.max(1, Math.round(runElapsedMs() / 60000));
                                                const tops = recapTops().map(t => `• ${t.name}: ${t.set}`).slice(0, 8);
                                                const lines = [
                                                    `${day.label} — ${new Date().toLocaleDateString()}`,
                                                    `⏱ ${durMin} min · ${doneSets} sets · ${Math.round(volume).toLocaleString()} ${unit} total`,
                                                ];
                                                if (mainE1rm)
                                                    lines.push(`Est. 1RM ${mainE1rm.name}: ${mainE1rm.est} ${unit}`);
                                                if (prs.length)
                                                    lines.push(`New PRs: ${prs.map(p => p.name).join(", ")}`);
                                                lines.push("", ...tops);
                                                const text = lines.join("\n");
                                                try {
                                                    if (navigator.clipboard && navigator.clipboard.writeText)
                
```

## 12. \bvolume\s*: @ 2347327

```js
one[0];
        return { name: ex.name, sets: done.length, top };
    }).filter(Boolean); // no fixed cap: the card measures how many rows actually fit
    const W = 1080, H = 1350, cnv = document.createElement("canvas");
    cnv.width = W;
    cnv.height = H;
    const ctx = cnv.getContext("2d");
    if (!ctx)
        return;
    /* Renders through drawRecapCard — the same card the end-of-session screen produces. This used to
       be a second, separately-maintained layout (its own "PURSUIT IRON" eyebrow, a bare stats row
       instead of tiles, "6 × 225lb · 5" lift lines) which drifted from the recap card it sits beside,
       and carried the same class of bug: .slice(22) on the title, .slice(26) on every lift name and
       .slice(52) on the subtitle, all cutting mid-word. One renderer, one look, and the measured text
       fitting applies here too.
       A history entry has no estimated 1RM or PR list attached, so those sections are simply absent —
       drawRecapCard omits them and gives the rows the space instead. */
    drawRecapCard(ctx, {
        C,
        label: String(entry.dayLabel || "Workout"),
        dateStr: `${entry.programName ? entry.programName + " · " : ""}${new Date(entry.date).toLocaleDateString(undefined, { weekday: "long", month: "long", day: "numeric" })}`,
        durMin: entry.durationMin || 0,
        doneSets: entry.setsDone ?? 0,
        volume: Number(entry.volume || 0),
        unit,
        mainE1rm,
        prs,
        tops: lifts.map(l => ({ name: l.name, set: `${l.top.w}${unit}×${l.top.r}` }))
    }, W, H);
    const done = (blob) => {
        const file = typeof File !== "undefined" ? new File([blob], "pursuit-iron-workout.png", { type: "image/png" }) : null;
        try {
            if (file && navigator.canShare && navigator.canShare({ files: [file] })) {
                navigator.share({ files: [file], title: entry.dayLabel || "My workout" }).catch(() => { });
                return;
            }
        }
        catch { }
        try {
            const url = URL.createObjectURL(blob);
            const a = document.createElement("a");
            a.href = url;
            a.download = "pursuit-iron-workout.png";
            document.body.appendChild(a);
            a.click();
            a.remove();
            setTimeout(() => URL.revokeObjectURL(url), 1500);
        }
        catch { }
    };
    if (cnv.toBlob)
        cnv.toBlob(b => b && done(b), "image/png");
    else {
        const a = document.createElement("a");
        a.href = cnv.toDataURL("image/png");
        a.download = "pursuit-iron-workout.png";
        a.click();
    }
}
function shareStrengthScore(sscore, theme) {
    if (!sscore || typeof document === "undefined")
        return;
    const C = theme || {};
    const W = 1080, H = 1350, PAD = 104, CW = W - PAD * 2;
    const cnv = document.createElement("canvas");
    cnv.width = W;
    cnv.height = H;
    const ctx = cnv.getContext("2d");
    if (!ctx)
        return;
    const rr = (x, y, w, h, r) => { ctx.beginPath(); if (ctx.roundRect)
        ctx.roundRect(x, y, w, h, r);
    else
        ctx.rect(x, y, w, h); };
    const font = (weight, px) => { ctx.font = `${weight} ${px}px ${SHARE_FONT}`; };
    /* Same measured-text treatment as the recap card. This one never used .slice(), so nothing was
       being severed mid-word — but none of it was measured either, which is the same exposure by a
       different route: a long level name, a four-pattern breakdown squeezing the tiles, or a
       "Strongest X · building Y" line with two long labels could all run past the card edge with no
       guard anywhere. Sizes and positions below are unchanged; they simply can't overflow now. */
    const elite = sscore.levelIdx >= 4;
    const hl = elite ? (C.warn 
```

## 13. \bvolume\s*: @ 2367933

```js
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
 * per-muscle delta in [-2,+2] 
```

## 14. \bh\.volume\b @ 2477409

```js
tent: "center", position: "relative", padding: 0
                            }, children: [cell.date.getDate(), has && !isSel && _jsx("span", { style: { position: "absolute", bottom: 4, width: 4, height: 4, borderRadius: 999, background: C.accent } })] }, ci));
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
/* Achievements. Three things they must not do: reward what the lifter can't control, punish a break,
   or lie about progress. So the strength tiers are bodyweight-relative rather than absolute (a 60kg
   lifter benching bodyweight has done something a 100kg lifter hasn't), and the long layoff earns a
   badge for coming back rather than breaking a streak and saying nothing. */
const BIG_THREE = { bench: "bb-bench", squat: "back-squat", deadlift: "deadlift" };
function computeMilestones(history, opts = {}) {
    const unit = opts.unit || "kg";
    const bw = parseFloat(opts.bodyweight) > 0 ? parseFloat(opts.bodyweight) : 0; // already in `unit`
    const n = history.length;
    // Session
```

## 15. \bh\.volume\b @ 2480050

```js
`${streaks.longest}d`], ["This month", `${monthCount}`]].map(([k, v]) => (_jsxs("div", { style: { flex: 1, textAlign: "center" }, children: [_jsx("div", { className: "mono", style: { fontSize: 15, fontWeight: 700, color: C.accentInk }, children: v }), _jsx("div", { style: { fontSize: 11, color: C.muted, marginTop: 2 }, children: k })] }, k))) })] }));
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
        bestFullRun = Math.max(bestFullRun, fullRun);
    }
    let streak = 0, best = 0;
    // Rounded week-count, not exact millisecond equality — the same DST-transition fragility as the
    // daily streak above (a week spanning a DST change is ±1 hour off 7*86400000ms exactly).
    for (let i = 0; i < weeks.length; i++) {
        streak = (i > 0 && Math.round((weeks[i] - weeks[i - 1]) / 86400000) === 7) ? streak + 1 : 1;
        best = Math.max(best, streak);
    }
    // Walk the log oldest-first: personal records, exercise breadth, training hours, and the best e1RM
    // reached on each of the big three.
    const chrono = history
```

## 16. \bh\.volume\b @ 2483846

```js
tory.slice().sort((a, b) => a.date - b.date);
    const bestE1 = {}; // exercise -> best e1RM so far, in `unit`
    let prs = 0, bestPrDay = 0, earlyBird = 0, nightOwl = 0, comeback = 0, prevDate = null;
    // Hours under the bar, weekend sessions, and the single biggest session — the comment above has
    // always claimed this walk collects training hours; now it actually does.
    let totalMin = 0, weekendCount = 0, maxSessionVol = 0, totalSets = 0, totalReps = 0;
    const seenExercises = new Set();
    const sessionsPerLift = {}; // exercise -> how many separate sessions it has appeared in
    for (const h of chrono) {
        const hUnit = h.unit || unit;
        const hour = new Date(h.date).getHours();
        if (hour < 7)
            earlyBird++;
        if (hour >= 21)
            nightOwl++;
        // A gap of two weeks or more, followed by this session: they came back. That deserves a badge,
        // not a broken streak and silence.
        if (prevDate && h.date - prevDate >= 14 * 86400000)
            comeback = 1;
        prevDate = h.date;
        // A missing or junk duration counts as zero rather than NaN-ing the running total.
        totalMin += Math.max(0, parseFloat(h.durationMin) || 0);
        const dow = new Date(h.date).getDay();
        if (dow === 0 || dow === 6)
            weekendCount++;
        maxSessionVol = Math.max(maxSessionVol, toUnit(h.volume || 0, hUnit, unit));
        let prsToday = 0;
        for (const [id, p] of Object.entries(h.perf || {})) {
            seenExercises.add(id);
            sessionsPerLift[id] = (sessionsPerLift[id] || 0) + 1;
            /* Counted on reps, not weight. A set of chin-ups or planks logs no load, and gating on w > 0
               would quietly decide that bodyweight work isn't work — the one population most likely to be
               training entirely without a barbell would earn nothing here. */
            setsOf(p).forEach(x => {
                const r = parseInt(x.r != null ? x.r : x.reps);
                if (r > 0) {
                    totalSets++;
                    totalReps += r;
                }
            });
            const e1 = toUnit(sessionE1RM(p), hUnit, unit);
            if (!(e1 > 0))
                continue;
            // Only counts as a PR if we've seen the lift before — otherwise every new exercise would hand
            // out a free record on its first outing.
            if (bestE1[id] != null && e1 > bestE1[id]) {
                prs++;
                prsToday++;
            }
            if (bestE1[id] == null || e1 > bestE1[id])
                bestE1[id] = e1;
        }
        bestPrDay = Math.max(bestPrDay, prsToday);
    }
    // Bodyweight-relative strength. Locked (value 0) until a bodyweight is set in Settings, rather than
    // silently comparing against nothing.
    const ratio = (id) => (bw > 0 && bestE1[id] > 0) ? bestE1[id] / bw : 0;
    const benchR = ratio(BIG_THREE.bench), squatR = ratio(BIG_THREE.squat), deadR = ratio(BIG_THREE.deadlift);
    /* Combined big-three total, as a multiple of bodyweight. Requires ALL THREE to have been logged —
       summing whatever exists would let a strong bench and squat alone clear a total that is supposed
       to represent three lifts, which is exactly the "lie about progress" this board is meant to
       avoid. Two lifts and a blank is not a total. */
    const bigThreeR = (benchR > 0 && squatR > 0 && deadR > 0) ? benchR + squatR + deadR : 0;
    const totalHours = totalMin / 60;
    const maxLiftSessions = Object.keys(sessionsPerLift).length ? Math.max(...Object.values(sessionsPerLift)) : 0;
    // Elapsed days between first and last logged session. Deliberately NOT a streak: time served
    // counts even across breaks, so a layoff never erases it.
    
```

## 17. \bh\.volume\b @ 2514366

```js
ek of this program belongs to the block it IS. Weeks are only distributed across phases
       in the standalone case, where the phases really are stretches of one program. */
    {
        let cursor = 0;
        for (const ph of phases) {
            const span = ph.weeks || (ph.key === "deload" ? 1 : ph.isBlock ? 0 : total);
            ph.weekFrom = cursor + 1;
            ph.weekTo = cursor + Math.max(span, 1);
            cursor += Math.max(span, 1);
        }
        const currentPhaseKey = (phases.find(p => p.current) || phases[0] || {}).key || null;
        for (const w of weeks) {
            if (phases.some(p => p.isBlock)) {
                w.phaseKey = currentPhaseKey;
                continue;
            }
            const ph = phases.find(p => w.week >= p.weekFrom && w.week <= p.weekTo) || phases[phases.length - 1];
            w.phaseKey = ph ? ph.key : null;
        }
    }
    return { weeks, lifts, phases, totalWeeks: total, accumWeeks: accum, hasDeload, currentWeek: weekIndex,
        dayLabels: days.map(d => d.label || "Day"), sessionsTotal, sessionsDone, upNext,
        percentDone: sessionsTotal ? Math.round((sessionsDone / sessionsTotal) * 100) : 0,
        /* the phase you are in right now, for the header tile */
        currentPhase: (phases.find(p => p.current) || phases[0] || null) };
}
function historyVolumeIn(h, unit) {
    const volume = Number(h.volume);
    if (!Number.isFinite(volume) || volume <= 0)
        return 0;
    const from = h.unit || "kg";
    return !unit || from === unit ? volume : from === "lb" && unit === "kg" ? volume / 2.2046226218 : from === "kg" && unit === "lb" ? volume * 2.2046226218 : volume;
}
function filterWorkoutHistory(history, saved, query, programId, period, now = Date.now()) {
    const names = new Map((saved || []).map(p => [p.id, p.name]));
    const words = query.trim().toLocaleLowerCase().split(/\s+/).filter(Boolean);
    const cutoff = period === "all" ? -Infinity : now - Number(period) * 86400000;
    return (history || []).filter(h => {
        if (!h || h.date == null || !Number.isFinite(new Date(h.date).getTime()))
            return false;
        if (programId !== "all" && String(h.programId || "none") !== programId)
            return false;
        if (new Date(h.date).getTime() < cutoff)
            return false;
        const text = [h.dayLabel, h.programName, names.get(h.programId), h.note, ...Object.entries(h.perf || {}).flatMap(([id, p]) => [EX_BY_ID[id]?.name || p?.name || id, p?.note])].filter(Boolean).join(" ").toLocaleLowerCase();
        return words.every(word => text.includes(word));
    }).slice().sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime());
}
function groupSessionsByCycle(history, cycles, saved, unit) {
    const progById = new Map((saved || []).map(p => [p.id, p]));
    const cycleById = new Map((cycles || []).map(c => [c.id, c]));
    const groups = new Map();
    for (const h of history || []) {
        if (!h || h.date == null || isNaN(new Date(h.date)))
            continue;
        const prog = progById.get(h.programId);
        const cycle = prog && prog.cycleId ? cycleById.get(prog.cycleId) : null;
        /* Key on the CYCLE, and carry the block within it. A deleted program leaves history behind, so
           every lookup above can miss — that is normal, not an error, and it lands in "other". */
        const key = cycle ? `c:${cycle.id}` : `p:${h.programId || "none"}`;
        let g = groups.get(key);
        if (!g) {
            g = {
                key, cycleId: cycle ? cycle.id : null,
                label: cycle ? (cycle.name || "Training cycle") : (prog ? prog.name : h.programName || "Earlier sessions"),
                isCycle: !!cycle, blocks: new Map(), items: [], count: 0, volume: 0, newes
```

## 18. \bvolume\s*: @ 2516750

```js
mes.get(h.programId), h.note, ...Object.entries(h.perf || {}).flatMap(([id, p]) => [EX_BY_ID[id]?.name || p?.name || id, p?.note])].filter(Boolean).join(" ").toLocaleLowerCase();
        return words.every(word => text.includes(word));
    }).slice().sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime());
}
function groupSessionsByCycle(history, cycles, saved, unit) {
    const progById = new Map((saved || []).map(p => [p.id, p]));
    const cycleById = new Map((cycles || []).map(c => [c.id, c]));
    const groups = new Map();
    for (const h of history || []) {
        if (!h || h.date == null || isNaN(new Date(h.date)))
            continue;
        const prog = progById.get(h.programId);
        const cycle = prog && prog.cycleId ? cycleById.get(prog.cycleId) : null;
        /* Key on the CYCLE, and carry the block within it. A deleted program leaves history behind, so
           every lookup above can miss — that is normal, not an error, and it lands in "other". */
        const key = cycle ? `c:${cycle.id}` : `p:${h.programId || "none"}`;
        let g = groups.get(key);
        if (!g) {
            g = {
                key, cycleId: cycle ? cycle.id : null,
                label: cycle ? (cycle.name || "Training cycle") : (prog ? prog.name : h.programName || "Earlier sessions"),
                isCycle: !!cycle, blocks: new Map(), items: [], count: 0, volume: 0, newest: 0
            };
            groups.set(key, g);
        }
        g.items.push(h);
        g.count++;
        if (h.date > g.newest)
            g.newest = h.date;
        const v = historyVolumeIn(h, unit);
        if (Number.isFinite(v) && v > 0)
            g.volume += v;
        /* Blocks only mean something inside a cycle. Prefer the cycle's own blockMeta label over the
           copy stamped on the program, so renaming a block in one place doesn't split the group. */
        if (cycle) {
            const meta = (cycle.blockMeta || []).find(m => m.id === h.programId);
            const bLabel = (meta && meta.label) || (prog && prog.blockLabel) || (prog && prog.name) || "Block";
            const bKey = h.programId || bLabel;
            let b = g.blocks.get(bKey);
            if (!b) {
                b = { key: bKey, label: bLabel, index: (cycle.blockIds || []).indexOf(h.programId), items: [], count: 0, volume: 0, newest: 0 };
                g.blocks.set(bKey, b);
            }
            b.items.push(h);
            b.count++;
            if (h.date > b.newest)
                b.newest = h.date;
            if (Number.isFinite(v) && v > 0)
                b.volume += v;
        }
    }
    return [...groups.values()]
        .map(g => ({
        ...g,
        /* blocks in PROGRAMME order, which is how the cycle was planned and read; a block with no
           position (its program was deleted) sorts last rather than jumping to the front on -1 */
        blocks: [...g.blocks.values()].sort((a, b) => (a.index < 0 ? 1 : b.index < 0 ? -1 : a.index - b.index))
    }))
        .sort((a, b) => b.newest - a.newest);
}
function groupSessionsByMonth(history, unit) {
    const groups = new Map();
    const thisYear = new Date().getFullYear();
    for (const h of history || []) {
        if (!h || h.date == null)
            continue;
        const d = new Date(h.date);
        if (isNaN(d))
            continue;
        const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
        let g = groups.get(key);
        if (!g) {
            g = { key, label: d.toLocaleDateString(undefined, d.getFullYear() === thisYear ? { month: "long" } : { month: "long", year: "numeric" }), items: [], count: 0, volume: 0 };
            groups.set(key, g);
        }
        g.items.push(h);
        g.count++;
        const v = historyV
```

## 19. \bvolume\s*: @ 2519025

```js
ex: (cycle.blockIds || []).indexOf(h.programId), items: [], count: 0, volume: 0, newest: 0 };
                g.blocks.set(bKey, b);
            }
            b.items.push(h);
            b.count++;
            if (h.date > b.newest)
                b.newest = h.date;
            if (Number.isFinite(v) && v > 0)
                b.volume += v;
        }
    }
    return [...groups.values()]
        .map(g => ({
        ...g,
        /* blocks in PROGRAMME order, which is how the cycle was planned and read; a block with no
           position (its program was deleted) sorts last rather than jumping to the front on -1 */
        blocks: [...g.blocks.values()].sort((a, b) => (a.index < 0 ? 1 : b.index < 0 ? -1 : a.index - b.index))
    }))
        .sort((a, b) => b.newest - a.newest);
}
function groupSessionsByMonth(history, unit) {
    const groups = new Map();
    const thisYear = new Date().getFullYear();
    for (const h of history || []) {
        if (!h || h.date == null)
            continue;
        const d = new Date(h.date);
        if (isNaN(d))
            continue;
        const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
        let g = groups.get(key);
        if (!g) {
            g = { key, label: d.toLocaleDateString(undefined, d.getFullYear() === thisYear ? { month: "long" } : { month: "long", year: "numeric" }), items: [], count: 0, volume: 0 };
            groups.set(key, g);
        }
        g.items.push(h);
        g.count++;
        const v = historyVolumeIn(h, unit);
        if (Number.isFinite(v) && v > 0)
            g.volume += v;
    }
    // newest month first, matching the order the sessions themselves are already in
    return [...groups.values()].sort((a, b) => (a.key < b.key ? 1 : -1));
}
function weeklyBodyweightTrend(bwLog, unit) {
    const entries = normalizeBwLog(bwLog, unit);
    if (entries.length < 2)
        return null;
    const last = entries[entries.length - 1].date;
    const DAY = 86400000, WEEK = 7 * DAY;
    // bucket[0] = most recent 7 days, bucket[1] = the 7 days before that, etc.
    const buckets = [];
    entries.forEach(e => {
        const idx = Math.floor((last - e.date) / WEEK);
        (buckets[idx] || (buckets[idx] = [])).push(e.w);
    });
    const avg = (arr) => arr.reduce((s, v) => s + v, 0) / arr.length;
    const weeks = buckets.map((b, i) => b && b.length ? { weekIndex: i, avg: avg(b), n: b.length } : null).filter(Boolean);
    if (weeks.length < 2)
        return null; // everything logged fell inside one 7-day window
    const current = weeks[0];
    const prior = weeks[weeks.length - 1]; // oldest populated bucket — the longest-baseline comparison available
    const spanWeeks = prior.weekIndex - current.weekIndex;
    const changeAbs = current.avg - prior.avg;
    const changePerWeek = spanWeeks > 0 ? changeAbs / spanWeeks : 0;
    const pctPerWeek = prior.avg > 0 ? (changePerWeek / prior.avg) * 100 : 0;
    return {
        currentWeekAvg: Math.round(current.avg * 10) / 10,
        priorWeekAvg: Math.round(prior.avg * 10) / 10,
        changeAbs: Math.round(changeAbs * 100) / 100,
        changePerWeek: Math.round(changePerWeek * 100) / 100,
        pctPerWeek: Math.round(pctPerWeek * 100) / 100,
        spanWeeks,
        weeksOfData: weeks.length
    };
}
/* An empty state that only describes the emptiness leaves the user to work out the way back on
   their own — and the way back is a different tab, which is exactly the navigation they would rather
   not perform. Naming the actual next day ("Start Pull - Width") beats a generic "go train" because
   it confirms the app already knows what they are doing next. Rendered only when there IS a next
   workout: with no program at all there is nothing honest to offer, and a dead button 
```

## 20. \.volume\s*= @ 2530715

```js
ourceSets = Array.isArray(rawPerf?.sets) && rawPerf.sets.length
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
    const setSet = (exId, index, key, value) => setDraft(prev => {
        const perf = { ...(prev.perf || {}) }, p = { ...(perf[exId] || {}) };
        const sets = (p.sets || []).map((st, i) => i === index ? { ...st, [key]: value } : st);
        perf[exId] = { ...p, sets };
        return { ...prev, perf };
    });
    const removeSet = (exId, index) => setDraft(prev => {
        const perf = { ...(prev.perf || {}) }, p = { ...(perf[exId] || {}) };
        const sets = (p.sets || []).filter((_, i) => i !== index);
        if (sets.length)
            perf[exId] = { ...p, sets };
        else
            delete perf[exId];
        return { ...prev, perf };
    });
    const addSet = (exId) => setDraft(prev => {
        const perf = { ...(prev.perf || {}) }, p = { ...(perf[exId] || {}) }, sets = [...(p.sets || [])];
        const last = sets[sets.length - 1];
        sets.pu
```

## 21. perf\s*: @ 2531844

```js
f = perf;
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
    const setSet = (exId, index, key, value) => setDraft(prev => {
        const perf = { ...(prev.perf || {}) }, p = { ...(perf[exId] || {}) };
        const sets = (p.sets || []).map((st, i) => i === index ? { ...st, [key]: value } : st);
        perf[exId] = { ...p, sets };
        return { ...prev, perf };
    });
    const removeSet = (exId, index) => setDraft(prev => {
        const perf = { ...(prev.perf || {}) }, p = { ...(perf[exId] || {}) };
        const sets = (p.sets || []).filter((_, i) => i !== index);
        if (sets.length)
            perf[exId] = { ...p, sets };
        else
            delete perf[exId];
        return { ...prev, perf };
    });
    const addSet = (exId) => setDraft(prev => {
        const perf = { ...(prev.perf || {}) }, p = { ...(perf[exId] || {}) }, sets = [...(p.sets || [])];
        const last = sets[sets.length - 1];
        sets.push({ w: last?.w ?? 0, r: last?.r ?? "", rir: "" });
        perf[exId] = { ...p, sets };
        return { ...prev, perf };
    });
    const removeExercise = (exId) => setDraft(prev => { const perf = { ...(prev.perf || {}) }; delete perf[exId]; return { ...prev, perf }; });
    const exEntries = Object.entries(draft.perf || {});
    const inputStyle = { width: "100%", boxSizing: "border-box", padding: "10px 8px", borderRadius: 10, border: `1px solid ${C.border}`, background: C.card, color: C.text, fontSize: 14, fontWeight: 650, textAlign: "center" };
    return createElement("div", { className: "wpb-backdrop", onClick: onClose, style: { position: "fixed", inset: 0, zIndex: 90, background: "rgba(0,0,0,.68)", display: "flex", alignItems: "flex-end" } },
        createElement("div", { onClick: e => e.stopPropagation(), className: "wpb-scroll", style: { width: "100%", maxHeight: "90%", overflowY: "auto", background: C.bg2, borderRadius: "20px 20px 0 0", border: `1px solid ${C.border}`, padding: "18px 16px calc(env(safe-area-inset-bottom) + 18px)" } },
            createElement("div", { style: { display: "flex", alig
```

## 22. \bvolume\s*: @ 2548333

```js
eparates
       something — more than one group, or a real cycle to name. */
    const cycleGroups = useMemo(() => groupSessionsByCycle(visibleHistory, cycles, saved, unit), [visibleHistory, cycles, saved, unit]);
    /* THE SWITCH IS ALWAYS OFFERED ONCE THERE IS HISTORY. Do not add a condition here.
       It shipped gated on "a program carries a cycleId", so a lifter who had never built a training
       cycle — the common case — saw no switch and could not reach the feature at all. Broadening that
       to "more than one group" still hid it from anyone running a single program. Both versions were
       me deciding the control was not worth showing on someone else's behalf, and the failure mode is
       the worst one available: a feature that is invisible to the person who asked for it.
       A grouping that yields one heading is a mild disappointment the lifter can see and undo; a
       control that never appears is indistinguishable from a broken build. The label names what the
       grouping actually did — a cycle when there is one, otherwise the program it belonged to. */
    const anyCycle = cycleGroups.some(g => g.isCycle);
    const trainingLabel = anyCycle ? "By cycle" : "By program";
    const sessionGroups = useMemo(() => {
        if (groupBy === "cycle") {
            return cycleGroups.map(g => ({
                key: g.key, label: g.label, count: g.count, volume: g.volume,
                sections: g.blocks.length
                    ? g.blocks.map(b => ({ key: b.key, label: b.label, count: b.count, items: b.items }))
                    : [{ key: g.key + ":all", label: null, count: g.count, items: g.items }]
            }));
        }
        return groupSessionsByMonth(visibleHistory, unit).map(g => ({
            key: g.key, label: g.label, count: g.count, volume: g.volume,
            sections: [{ key: g.key + ":all", label: null, count: g.count, items: g.items }]
        }));
    }, [groupBy, cycleGroups, visibleHistory, unit]);
    return (_jsxs("div", { className: "wpb-page-shell wpb-progress", children: [_jsxs("div", { className: "wpb-page-header", style: { padding: embedded ? "26px 18px 0" : "16px 16px 0", borderBottom: `1px solid ${C.borderSoft}` }, children: [_jsxs("div", { style: { display: "flex", alignItems: "center", gap: 8 }, children: [!embedded && _jsx("button", { onClick: onBack, "aria-label": "Back", className: "pressable hit", style: { background: "none", border: "none", color: C.text, cursor: "pointer", padding: 4 }, children: _jsx(ChevronLeft, { size: 26 }) }), _jsx("div", { className: embedded ? "wpb-progress-title" : undefined, style: { flex: 1, fontSize: embedded ? 26 : 20, fontWeight: 700, letterSpacing: embedded ? -0.5 : 0 }, children: embedded ? "Progress" : "History" }), !embedded && history.length > 0 && _jsx("button", { type: "button", onClick: onClear, "data-haptic": "medium", className: "pressable hit", style: { ...tinyBtn(), width: "auto", height: 32, padding: "0 10px", fontSize: 13, fontWeight: 600, color: C.danger, borderColor: C.dangerDim }, children: "Clear" })] }), _jsx("div", { role: "tablist", "aria-label": "Progress sections", className: "wpb-premium-tabs", style: { marginTop: 12 }, children: [["sessions", "Sessions"], ["progress", "Lifts"], ["volume", "Volume"], ["body", "Body"]].map(([k, label]) => (_jsx("button", { role: "tab", "aria-selected": tab === k, onClick: () => setTab(k), className: "pressable", style: {
                                padding: "10px 4px", flex: 1, background: "none", border: "none", borderBottom: `2px solid ${tab === k ? C.accent : "transparent"}`,
                                color: tab === k ? C.text : C.muted, fontSize: 15, fontWeight: 600, cursor: "pointer"
                            }, children: label }, k))) })] }), _jsxs("div"
```

## 23. \bvolume\s*: @ 2561049

```js
 parseFloat(mInputs[key]) > 0 ? C.accent : C.bg2, color: parseFloat(mInputs[key]) > 0 ? C.accentText : C.faint, fontWeight: 700, fontSize: 13, cursor: parseFloat(mInputs[key]) > 0 ? "pointer" : "default" }, children: "Log" })] }), open && _jsx(MeasurementDetail, { label: label, log: log, unit: lengthUnitFor(unit) })] }, key));
                                        })] }))] }));
                    })() : history.length === 0 ? (_jsxs("div", { style: { marginTop: 40, textAlign: "center", color: C.faint }, children: [_jsx(TrendingUp, { size: 46, color: C.border, style: { margin: "0 auto" } }), _jsx("div", { style: { marginTop: 12, fontSize: 15, color: C.muted }, children: "No workouts logged yet." }), _jsx("div", { style: { fontSize: 15, marginTop: 4 }, children: "Hit play on a day to start training." }), _jsx(EmptyStateAction, { onGoTrain: onGoTrain, nextLabel: nextLabel })] })) : tab === "volume" ? (_jsxs(_Fragment, { children: [_jsxs("div", { style: { fontSize: 13, color: C.muted, margin: "2px 2px 12px", lineHeight: 1.5 }, children: ["Sets you've actually logged per muscle over the last 7 days, against your MEV\u2013MRV landmarks", activeProgram ? " and your active program's plan for this week" : "", ". Compounds credit their secondary muscles fractionally, the same way program volume is counted."] }), _jsx(VolumeCard, { title: "Volume \u00B7 last 7 days", history: history, volume: loggedVolume(history, 7), subVolume: loggedSubVolume(history, 7), target: activeProgram ? weeklyVolume(activeProgram, activeWeek) : null, program: null, open: true, collapsible: false, emptyLabel: "Log a workout and your set volume per muscle will show up here, colored by whether each muscle is below MEV, in the productive MEV\u2013MRV zone, or over MRV." })] })) : tab === "sessions" ? (history.length === 0 ? (_jsxs("div", { style: { marginTop: 40, textAlign: "center", color: C.faint }, children: [_jsx(Calendar, { size: 46, color: C.border, style: { margin: "0 auto" } }), _jsx("div", { style: { marginTop: 12, fontSize: 15, color: C.muted }, children: "No workouts logged yet." }), _jsx("div", { style: { fontSize: 15, marginTop: 4 }, children: "Finish a session and it'll show up here, building your streak and training heatmap." }), _jsx(EmptyStateAction, { onGoTrain: onGoTrain, nextLabel: nextLabel })] })) : (_jsxs(_Fragment, { children: [_jsxs("section", { "aria-label": "Training consistency", className: "wpb-consistency-card", style: { background: C.card, border: `1px solid ${C.borderSoft}`, borderRadius: 16, padding: "14px 15px", marginBottom: 14 }, children: [_jsxs("div", { style: { display: "flex", alignItems: "baseline", gap: 10 }, children: [_jsxs("div", { style: { flex: 1, minWidth: 0 }, children: [_jsx("div", { style: { fontSize: 15, fontWeight: 750 }, children: "Consistency" }), _jsx("div", { style: { fontSize: 12, color: C.muted, marginTop: 2 }, children: progressGlance.streak ? `${progressGlance.streak}-week training streak` : "Build your first weekly streak" })] }), _jsx("div", { className: "mono", style: { fontSize: 12, color: C.faint, flexShrink: 0 }, children: progressGlance.lastDate ? `Last ${new Date(progressGlance.lastDate).toLocaleDateString(undefined, { month: "short", day: "numeric" })}` : "" })] }), _jsx("div", { role: "img", "aria-label": `Training activity over the last 28 days: ${progressGlance.activity28.filter(d => d.on).length} active days`, style: { display: "grid", gridTemplateColumns: "repeat(14,minmax(0,1fr))", gap: 4, marginTop: 12 }, children: progressGlance.activity28.map((d, i) => _jsx("span", { title: d.label, style: { aspectRatio: "1", borderRadius: 4, background: d.on ? C.accent : C.bg2, border: `1px solid ${d.on ? `${C.accent}88` : C.borderSoft}` } }, i)) }), _jsxs("div", { style: { display: "flex", justifyConte
```

## 24. \bh\.volume\b @ 2571158

```js
 children: _jsxs("button", { "aria-expanded": isOpen, "aria-label": `${h.dayLabel || "Workout"} · ${new Date(h.date).toLocaleDateString()}`, onClick: () => setOpenSession(isOpen ? null : h.id), className: "pressable", style: { flex: 1, minWidth: 0, display: "flex", alignItems: "center", gap: 12, background: "none", border: "none", padding: 0, cursor: "pointer", textAlign: "left" }, children: [_jsxs("div", { className: "wpb-history-date", style: { width: 44, height: 44, borderRadius: 12, background: C.accentDim, display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", flexShrink: 0 }, children: [_jsx("span", { className: "mono", style: { fontSize: 15, fontWeight: 600, color: C.accentInk, lineHeight: 1 }, children: new Date(h.date).getDate() }), _jsx("span", { style: { fontSize: 11, color: C.accentInk, textTransform: "uppercase" }, children: new Date(h.date).toLocaleDateString(undefined, { month: "short" }) })] }), _jsxs("div", { style: { flex: 1, minWidth: 0 }, children: [_jsx("div", { style: { fontSize: 15, fontWeight: 700, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }, children: h.dayLabel }), _jsx("div", { style: { fontSize: 13, color: C.muted, marginTop: 2 }, children: h.programName }), _jsxs("div", { className: "mono", style: { fontSize: 11, color: C.faint, marginTop: 2 }, children: [h.setsDone || 0, " sets \u00B7 ", (h.volume || 0).toLocaleString(), " ", hUnit, " \u00B7 ", h.durationMin || 0, "m"] })] }), _jsx(ChevronDown, { size: 18, color: C.muted, style: { flexShrink: 0, transition: "transform .18s", transform: isOpen ? "rotate(180deg)" : "none" } })] }) }), isOpen && (_jsxs("div", { className: "wpb-history-session-detail", style: { borderTop: `1px solid ${C.borderSoft}`, padding: "4px 14px 12px" }, children: [_jsxs("div", { className: "wpb-history-detail-head", style: { display: "flex", alignItems: "center", gap: 12, padding: "10px 0 8px" }, children: [_jsxs("div", { style: { flex: 1, minWidth: 0 }, children: [_jsx("div", { style: { fontSize: 15, fontWeight: 700, overflowWrap: "anywhere" }, children: h.dayLabel || "Workout" }), _jsx("div", { style: { fontSize: 13, color: C.muted, marginTop: 1 }, children: new Date(h.date).toLocaleDateString(undefined, { year: "numeric", month: "long", day: "numeric" }) })] }), _jsxs("div", { role: "group", "aria-label": "Workout actions", className: "wpb-history-actions", style: { display: "flex", gap: 8, flexShrink: 0 }, children: [_jsx("button", { onClick: () => shareSession(h, C, history), title: "Share this workout", "aria-label": `Share ${h.dayLabel}`, className: "pressable hit", style: { flexShrink: 0, width: 40, height: 40, borderRadius: 11, border: `1px solid ${C.border}`, background: C.card, color: C.muted, display: "flex", alignItems: "center", justifyContent: "center", cursor: "pointer" }, children: _jsx(Share2, { size: 17 }) }), onRepeat && (_jsx("button", { "aria-label": "Repeat this workout", onClick: () => onRepeat(h), title: "Repeat this workout", className: "pressable hit", style: { flexShrink: 0, width: 40, height: 40, borderRadius: 11, border: `1px solid ${C.border}`, background: C.bg2, color: C.accentInk, cursor: "pointer", display: "flex", alignItems: "center", justifyContent: "center" }, children: _jsx(RefreshCw, { size: 17 }) })), onEditEntry && (_jsx("button", { type: "button", "aria-label": `Edit ${h.dayLabel || "workout"}`, title: "Correct workout log", onClick: () => setEditSession(h.id), className: "pressable hit", style: { flexShrink: 0, width: 40, height: 40, borderRadius: 11, border: `1px solid ${C.border}`, background: C.bg2, color: C.muted, cursor: "pointer", display: "flex", alignItems: "center", justifyContent: "center", padding: 0 }, children: _jsx(Pencil, { size: 17 }) })), onDeleteEntry && (_jsx(
```

## 25. perf\s*: @ 2936313

```js
          f.forEach(x => audit.push(`${gymName}/${k}/${behaviour}: ${x.exName} wk${x.weekIndex} — ${x.detail}`));
                }
                catch (e) {
                    audit.push(`${gymName}/${k}/${behaviour}: audit threw — ${e.message}`);
                }
                await checkpoint(`Simulating ${gymName}`, 1, true);
            }
        }
    }
    // The lifter's own supported programs, with a recent relevant evidence window. Yield during large
    // programs as well as between them so one long block cannot monopolize the UI thread.
    for (const sp of supportedSaved) {
        if (!sp || !Array.isArray(sp.days)) {
            await checkpoint("Checking your programs", 1);
            continue;
        }
        const programHistory = diagnosticHistory.filter(h => !sp.id || h.programId === sp.id);
        const wks = weeksOf(sp);
        let cellBudget = 0;
        for (let w = 1; w <= wks; w++) {
            for (const d of sp.days) {
                for (let slot = 0; slot < (d.exercises || []).length; slot++) {
                    const id = d.exercises[slot];
                    const ex = EX_BY_ID[id];
                    if (!ex)
                        continue;
                    auditedSets++;
                    try {
                        auditSets({ program: sp, day: d, ex, slot, weekIndex: w, unit: "lb", perf, history: programHistory, dayPerf: perf })
                            .forEach(x => audit.push(`your "${sp.name}": ${x.exName} wk${x.weekIndex} — ${x.detail}`));
                    }
                    catch (e) {
                        audit.push(`your "${sp.name}": ${ex.name} audit threw — ${e.message}`);
                    }
                    if (++cellBudget % 8 === 0)
                        await checkpoint("Checking your programs");
                }
            }
        }
        await checkpoint("Checking your programs", 1, true);
    }
    /* PROGRAM-LEVEL ADVISORIES remain separate from failures. */
    for (const [gi, [gymName, equip]] of Object.entries(GYMS).entries()) {
        for (const k of advSplits(gi)) {
            for (const dcount of advDays(k)) {
                const cfg = { name: k, experience: "intermediate", goal: "hypertrophy", split: k, days: dcount, session: "s60", weeks: 3, equipment: equip, focus: {}, focusList: [], reduce: [], progression: "auto", deload: true, barbellCap: null, percentScheme: null };
                let p = null;
                try {
                    p = generateNextProgramForShell({ config: cfg, banned: [], legacyExercises: EXERCISES, seed: 606 }).program;
                }
                catch {
                    await checkpoint("Checking weekly structure", 1);
                    continue;
                }
                try {
                    auditProgramWeek(p, weeksOf(p), {}).forEach(x => {
                        const line = `${gymName}/${k}/${dcount}d: ${x.detail}`;
                        if (x.severity === "error")
                            fails.push(line);
                        else if (x.severity !== "info")
                            advisories.push(line);
                    });
                }
                catch { /* an advisory pass must never break the self-test */ }
                await checkpoint("Checking weekly structure", 1);
            }
        }
    }
    for (const sp of supportedSaved) {
        if (!sp || !Array.isArray(sp.days)) {
            await checkpoint("Checking your weekly structure", 1);
            continue;
        }
        try {
            auditProgramWeek(sp, weeksOf(sp), { focusList: Object.keys((sp.config && sp.config.focus) || {}) })
                .forEach(x => { const line = `your "${sp.name}": ${x.detail}`; if (x.severity === "error")
                
```

## 26. setHistory\s*\( @ 3216151

```js
eCycles);
            setLegacyStore({
                saved: (Array.isArray(d.saved) ? d.saved : []).filter(p => p && !isRuntimeProgram(p)),
                cycles: (Array.isArray(d.cycles) ? d.cycles : []).filter(c => c && c.engineSource !== "pursuit-next"),
            });
            setSavedRaw(runtimeSaved);
            setTombs(d.tombs && typeof d.tombs === "object" ? d.tombs : {});
            setCyclesRaw(runtimeCycles);
            setBanned(d.banned || []);
            if (d.equipDefault)
                setEquipDefault(d.equipDefault);
            {
                const n = normalizeGyms(d.gyms, d.activeGymId);
                setGyms(n.gyms);
                setActiveGymId(n.activeGymId);
            }
            if (d.unit)
                setUnit(d.unit);
            if (d.unitChosen)
                setUnitChosen(true);
            if (d.loadMode)
                setLoadMode(d.loadMode);
            if (["novice", "intermediate", "advanced"].includes(d.experience))
                setExperience(d.experience);
            if (d.perf)
                setPerf(d.perf); // legacy lastWeights→perf is now handled by migrateStore() on load
            // Resolved once, at the boundary — see normalizeHistoryDayIds. Needs the saved programs to
            // resolve against, so it runs on the store object rather than on state.
            if (d.history)
                setHistory(normalizeHistoryDayIds(d.history, runtimeSaved));
            if (d.seenIntro)
                setSeenIntroV(typeof d.seenIntro === "number" ? d.seenIntro : 1);
            if (typeof d.seenWhatsNew === "number")
                setSeenWhatsNew(d.seenWhatsNew);
            if (typeof d.lastBackup === "number")
                setLastBackup(d.lastBackup);
            if (typeof d.installDismissedAt === "number")
                setInstallDismissedAt(d.installDismissedAt);
            if (d.goals)
                setGoals(d.goals);
            if (d.exNotes && typeof d.exNotes === "object")
                setExNotes(d.exNotes);
            if (d.exSetup && typeof d.exSetup === "object")
                setExSetup(d.exSetup);
            if (d.drafts && typeof d.drafts === "object")
                setDrafts(d.drafts);
            if (d.pinnedId)
                setPinnedId(d.pinnedId);
            setLoaded(true);
        }).catch(err => {
            if (!alive)
                return;
            clearTimeout(slow);
            setLoadState({ status: "error", error: err });
        });
        return () => { alive = false; clearTimeout(slow); };
    }, [loadAttempt, recoveryStore, safeStart]);
    // persist on change (after initial load)
    const storageWarned = useRef(false);
    const crossTabWarned = useRef(false);
    // The native `storage` event fires in every OTHER tab/window when one of them writes to this
    // origin's storage — never in the tab that made the write. That's exactly the signal needed here:
    // if this key changed while THIS tab is open, another tab of the app is also open, and whichever
    // one saves last will silently overwrite the other's data (a finished workout only saved in the
    // other tab would be lost with no error). We don't attempt to merge or auto-reload — either could
    // discard whatever this tab hasn't saved yet — just surface it once so the user can act (finish up
    // in one tab, then reload the other before using it).
    useEffect(() => {
        const onStorage = (e) => {
            if (e.key !== KEY || crossTabWarned.current)
                return;
            crossTabWarned.current = true;
            setAppToast({ msg: "Pursuit Iron is open in another tab or window — use just one at a time, or a save could be lost. Reload the other one when you're done here." });
        };
      
```

## 27. setHistory\s*\( @ 3230280

```js
/ Per-exercise goal weights are real loads compared against what you lift, so they convert too —
        // otherwise a 225 lb bench goal would be read as 225 kg after switching and become unreachable.
        setGoals(g => { if (!g)
            return g; const o = {}; for (const id in g) {
            const n = parseFloat(g[id]);
            o[id] = n > 0 ? Math.round(n * k * 2) / 2 : g[id];
        } return o; });
        setBodyweight(bw => { const n = parseFloat(bw); return n > 0 ? String(Math.round(n * k * 2) / 2) : bw; });
        setBwLog(log => Array.isArray(log) ? log.map(e => ({ ...e, w: conv(e.w), unit: to })) : log);
        // Circumferences follow the same rule as loads: convert, never relabel.
        setMeasurements(m => { if (!m || typeof m !== "object")
            return m; const lu = lengthUnitFor(to); const o = {}; for (const k in m)
            o[k] = normalizeMeasureLog(m[k], lu, unit); return o; });
        setUnit(to);
        setUnitChosen(true);
        if (announce)
            setAppToast({ msg: `Switched to ${to} — your weights were converted` });
    };
    const resetAll = () => {
        try {
            localStorage.removeItem(LIVE_KEY);
        }
        catch { }
        setLegacyStore({ saved: [], cycles: [] });
        setLegacyReview(null);
        setDrafts({});
        setSaved([]);
        setCycles([]);
        setBanned([]);
        setHistory([]);
        setPerf({});
        setCustom([]);
        setBwLog([]);
        setMeasurements({});
        setGoals({});
        // "Erase all data" has to mean it: pinned exercise notes/setups, the body profile, and the
        // active-program pointer are all user data too — leaving them behind made a "reset" device
        // still recognizably yours. Device preferences (theme, unit, plates, haptics) stay.
        setExNotes({});
        setExSetup({});
        setPinnedId(null);
        setBodyweight("");
        setBirth("");
        setBirthEst(false);
        setSex("male");
        setMinInc({ v: 0, unit: null });
        setReminders({ enabled: false, time: "18:00", days: [1, 2, 3, 4, 5] });
        setCanaryResearch(defaultCanaryResearchState());
        setSelectivePromotionRuntime(defaultSelectivePromotionRuntimeState(M76_SELECTIVE_PROMOTION_MANIFEST));
        setProgram(null);
        setView("home");
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
                rows.push([d, h
```

## 28. setHistory\s*\( @ 3335981

```js
orkout(prev, program.id, entry));
        if (program?.nextEngine?.selectivePromotionAssignment)
            setSelectivePromotionRuntime(prev => recordSelectivePromotionWorkout(prev, program.nextEngine.selectivePromotionAssignment, program.id, entry));
        /* Both calls must use the SAME options the Achievements screen uses. Called bare, this defaulted
           to kg and a bodyweight of 0, which meant two things: for anyone logging in lb the tonnage
           badges were judged against kg thresholds and disagreed with the board they were reading, and
           every bodyweight-relative badge sat at value 0 forever — you could pull 2× bodyweight and the
           app would never say a word, because the milestone it would have announced was permanently
           locked in this code path. The before/after diff stayed self-consistent, so nothing ever looked
           broken; the badges just silently never fired. */
        const msOpts = { unit, bodyweight };
        const before = new Set(computeMilestones(history, msOpts).filter(m => m.done).map(m => m.id));
        const fresh = computeMilestones([entry, ...history], msOpts).filter(m => m.done && !before.has(m.id));
        // capHistory, not a bare slice: the byte budget has to apply to the incremental path too, or a
        // lifter logging huge sessions walks past the storage limit one workout at a time.
        setHistory(prev => capHistory([entry, ...prev]));
        if (log.perf && Object.keys(log.perf).length)
            setPerf(prev => ({ ...prev, ...log.perf }));
        // one-shot deload is consumed once the lift has been trained
        if (program?.deloadNext && log.perf) {
            const trained = Object.keys(log.perf).filter(id => program.deloadNext[id] != null);
            if (trained.length) {
                const nd = { ...program.deloadNext };
                trained.forEach(id => delete nd[id]);
                const cleared = { ...program, deloadNext: Object.keys(nd).length ? nd : undefined };
                setProgram(cleared);
                setSaved(prev => prev.map(p => p.id === cleared.id ? cleared : p));
            }
        }
        // M46: no scheme-specific legacy state mutation. Completed-set history is the only progression input.
        setSessionDay(null);
        if (program?.quick) {
            setProgram(null);
            setView("home");
        }
        else
            setView("program");
        if (fresh.length)
            setTimeout(() => setAppToast({ msg: `${fresh[0].icon} Achievement unlocked — ${fresh[0].label}${fresh.length > 1 ? ` +${fresh.length - 1} more` : ""}` }), 400);
        else
            setTimeout(() => setAppToast({ msg: "Workout logged ✓" }), 400);
    };
    const setGoalWeight = (exId, w) => setGoals(prev => { const g = { ...prev }; if (w > 0)
        g[exId] = w;
    else
        delete g[exId]; return g; });
    const commitHistoryCorrection = (histId, corrected) => {
        const original = history.find(h => h && h.id === histId);
        if (!original || !corrected)
            return;
        const clean = normalizeEditedHistoryEntry(original, corrected);
        const prevHistory = history, prevPerf = perf;
        const nextHistory = history.map(h => h?.id === histId ? clean : h);
        const nextPerf = perfAfterHistoryReplace(history, perf, histId, clean);
        setHistory(nextHistory);
        setPerf(nextPerf);
        setAppToast({
            msg: `Corrected ${clean.dayLabel || "workout"} history`,
            undo: () => { setHistory(prevHistory); setPerf(prevPerf); setAppToast(null); }
        });
    };
    const editHistoryEntry = (histId, exId, change) => {
        const original = history.find(h => h && h.id === histId);
        if (!original)
            return;
     
```

## 29. setHistory\s*\( @ 3337957

```js
ject.keys(nd).length ? nd : undefined };
                setProgram(cleared);
                setSaved(prev => prev.map(p => p.id === cleared.id ? cleared : p));
            }
        }
        // M46: no scheme-specific legacy state mutation. Completed-set history is the only progression input.
        setSessionDay(null);
        if (program?.quick) {
            setProgram(null);
            setView("home");
        }
        else
            setView("program");
        if (fresh.length)
            setTimeout(() => setAppToast({ msg: `${fresh[0].icon} Achievement unlocked — ${fresh[0].label}${fresh.length > 1 ? ` +${fresh.length - 1} more` : ""}` }), 400);
        else
            setTimeout(() => setAppToast({ msg: "Workout logged ✓" }), 400);
    };
    const setGoalWeight = (exId, w) => setGoals(prev => { const g = { ...prev }; if (w > 0)
        g[exId] = w;
    else
        delete g[exId]; return g; });
    const commitHistoryCorrection = (histId, corrected) => {
        const original = history.find(h => h && h.id === histId);
        if (!original || !corrected)
            return;
        const clean = normalizeEditedHistoryEntry(original, corrected);
        const prevHistory = history, prevPerf = perf;
        const nextHistory = history.map(h => h?.id === histId ? clean : h);
        const nextPerf = perfAfterHistoryReplace(history, perf, histId, clean);
        setHistory(nextHistory);
        setPerf(nextPerf);
        setAppToast({
            msg: `Corrected ${clean.dayLabel || "workout"} history`,
            undo: () => { setHistory(prevHistory); setPerf(prevPerf); setAppToast(null); }
        });
    };
    const editHistoryEntry = (histId, exId, change) => {
        const original = history.find(h => h && h.id === histId);
        if (!original)
            return;
        const draft = { ...original, perf: { ...(original.perf || {}) } };
        if (change == null) {
            delete draft.perf[exId];
        }
        else {
            const p = original.perf?.[exId];
            if (!p)
                return;
            const sets = (Array.isArray(p.sets) && p.sets.length ? p.sets : [{ w: p.weight ?? 0, r: p.reps }]).map(st => ({ ...st }));
            const workIdx = sets.map((st, i) => ({ st, i })).filter(x => !x.st?.sub && Number(x.st?.r) > 0)
                .sort((a, b) => (Number(b.st.w) || 0) - (Number(a.st.w) || 0) || (Number(b.st.r) || 0) - (Number(a.st.r) || 0))[0]?.i ?? 0;
            sets[workIdx] = { ...sets[workIdx], w: change.weight, r: change.reps };
            draft.perf[exId] = { ...p, sets };
        }
        commitHistoryCorrection(histId, draft);
    };
    const editHistoryWorkout = (histId, corrected) => commitHistoryCorrection(histId, corrected);
    /* DELETE A WHOLE LOGGED SESSION. Haiden asked for this twice and remembered the app once having
       it; it never did — I checked all 68 deployed builds, and what exists is "Delete entry" (ONE
       exercise inside a log) and "Clear all". His two follow-on asks — that the rotation goes back a
       day so he can redo it, and that fatigue resets — need no code of their own: both are DERIVED from
       history rather than stored, so they follow the moment the entry is gone. The mirror is the part
       that does not follow, which is what perfAfterDelete handles. */
    const deleteHistoryEntry = (histId) => {
        const gone = history.find(h => h.id === histId);
        if (!gone)
            return;
        const prevHistory = history, prevPerf = perf;
        setPerf(perfAfterDelete(history, perf, histId));
        setHistory(prev => prev.filter(h => h.id !== histId));
        /* UNDO, matching the "Clear all" precedent directly below. Deleting a workout is destructive and
           restores BOTH halves — putting hist
```

## 30. setHistory\s*\( @ 3340150

```js
=> ({ ...st }));
            const workIdx = sets.map((st, i) => ({ st, i })).filter(x => !x.st?.sub && Number(x.st?.r) > 0)
                .sort((a, b) => (Number(b.st.w) || 0) - (Number(a.st.w) || 0) || (Number(b.st.r) || 0) - (Number(a.st.r) || 0))[0]?.i ?? 0;
            sets[workIdx] = { ...sets[workIdx], w: change.weight, r: change.reps };
            draft.perf[exId] = { ...p, sets };
        }
        commitHistoryCorrection(histId, draft);
    };
    const editHistoryWorkout = (histId, corrected) => commitHistoryCorrection(histId, corrected);
    /* DELETE A WHOLE LOGGED SESSION. Haiden asked for this twice and remembered the app once having
       it; it never did — I checked all 68 deployed builds, and what exists is "Delete entry" (ONE
       exercise inside a log) and "Clear all". His two follow-on asks — that the rotation goes back a
       day so he can redo it, and that fatigue resets — need no code of their own: both are DERIVED from
       history rather than stored, so they follow the moment the entry is gone. The mirror is the part
       that does not follow, which is what perfAfterDelete handles. */
    const deleteHistoryEntry = (histId) => {
        const gone = history.find(h => h.id === histId);
        if (!gone)
            return;
        const prevHistory = history, prevPerf = perf;
        setPerf(perfAfterDelete(history, perf, histId));
        setHistory(prev => prev.filter(h => h.id !== histId));
        /* UNDO, matching the "Clear all" precedent directly below. Deleting a workout is destructive and
           restores BOTH halves — putting history back without the mirror would leave the app prescribing
           from a session it no longer believes happened, which is the same split this function exists to
           prevent, just in the other direction. */
        setAppToast({
            msg: `Deleted ${gone.dayLabel || "workout"}`,
            undo: () => { setHistory(prevHistory); setPerf(prevPerf); setAppToast(null); }
        });
    };
    // Active program = the one tied to the most recent logged workout (else newest saved).
    // Next day walks the rotation from the last day trained; week advances as the block fills.
    const upNext = useMemo(() => {
        if (!saved.length)
            return null;
        // pinned → most-recently-trained → live cycle block → newest. (See pickActiveProgram.)
        const active = pickActiveProgram(saved, history, cycles, pinnedId);
        if (!active || !active.days.length)
            return null;
        const { dayIndex, weekIndex, scheduled, lastIdx } = nextSessionCursor(active, history);
        // recovery-aware emphasis: score each day by how fresh the muscles it trains are
        const rec = muscleRecovery(history);
        const readyByPart = Object.fromEntries(rec.map(r => [r.part, r.readiness]));
        const dayScore = (day) => {
            let sum = 0, wsum = 0;
            day.exercises.forEach(id => {
                const ex = EX_BY_ID[id];
                if (!ex)
                    return;
                sum += (readyByPart[ex.part] ?? 100);
                wsum += 1;
                secondaryOf(ex).forEach(([pp, f]) => { sum += (readyByPart[pp] ?? 100) * f * 0.5; wsum += f * 0.5; });
            });
            return wsum ? sum / wsum : 100;
        };
        const schedScore = dayScore(active.days[dayIndex]);
        let alt = null, best = schedScore + 12; // only suggest a swap that's clearly fresher
        active.days.forEach((d, i) => { if (i === dayIndex || i === lastIdx)
            return; const sc = dayScore(d); if (sc > best) {
            best = sc;
            alt = { day: d, dayIndex: i, score: Math.round(sc) };
        } });
        return { program: active, day: active.days[dayIndex], dayIndex, weekInde
```

## 31. setHistory\s*\( @ 3357832

```js
ry: () => pushView("library", "home"), onCalc: () => setCalcOpen(true), onCompare: () => { setCompareMode("programs"); pushView("compare", "home"); }, bodyweight: bodyweight, sex: sex, age: age, unit: unit, needsBackup: loaded && !liveDockVisible && (saved.length > 0 || history.length >= 3) && (Date.now() - (lastBackup || 0) > 14 * 86400000), onBackup: () => { setSettingsFocus("export"); navToTab("settings"); }, whatsNew: loaded && !liveDockVisible && seenWhatsNew < WHATS_NEW_VERSION && (history.length > 0 || saved.length > 0), onDismissWhatsNew: () => setSeenWhatsNew(WHATS_NEW_VERSION), onOpenChangelog: () => setChangelogOpen(true), sessionActive: !!liveSession, hasTabBar: tabView, showInstall: loaded && !liveDockVisible && !installed && (!!installEvent || isIosSafari) && (Date.now() - (installDismissedAt || 0) > 14 * 86400000), installEvent: installEvent, isIosSafari: isIosSafari, onDismissInstall: () => { setInstallDismissedAt(Date.now()); setInstallEvent(null); } })) : view === "progress" ? (_jsx(HistoryView, { history: history, onDeleteEntry: deleteHistoryEntry, onEditEntry: editHistoryWorkout, birth: birth, embedded: true, activeProgram: upNext?.program || null, activeWeek: upNext?.weekIndex || 1, onGoTrain: startUpNext, nextLabel: upNext?.day?.label || null, focusSection: progressFocus, onFocusHandled: () => setProgressFocus(null), onClear: () => { const prev = history; setHistory([]); if (prev.length)
                                        setAppToast({ msg: `Cleared ${prev.length} workout${prev.length === 1 ? "" : "s"}`, undo: () => { setHistory(prev); setAppToast(null); } }); }, bodyweight: bodyweight, sex: sex, age: age, unit: unit, onSetProfile: (patch) => { if (patch.bodyweight !== undefined)
                                        setBodyweight(patch.bodyweight); if (patch.sex !== undefined)
                                        setSex(patch.sex); if (patch.birth !== undefined) {
                                        setBirth(patch.birth);
                                        setBirthEst(false);
                                    } }, bwLog: bwLog, onLogBodyweight: logBodyweight, goals: goals, onRepeat: repeatWorkout, measurements: measurements, onLogMeasurement: logMeasurement, cycles: cycles, saved: saved })) : view === "profile" ? (_jsx(ProfileView, { history: history, bodyweight: bodyweight, sex: sex, age: age, unit: unit, onSettings: () => { setSettingsFocus("profile"); navToTab("settings"); } })) : view === "settings" ? (_jsx(SettingsView, { experience: experience, setExperience: setExperience, selfTestData: { saved, history, perf }, gymName: activeGym ? activeGym.name : "All equipment", gymCount: gyms.length, onGyms: () => setGymsOpen(true), nextEngineOnly: nextEngineOnly, canaryResearch: canaryResearch, canarySummaryData: canarySummary(canaryResearch), canaryAnalysisData: analyzeCanaryEvidence(canaryResearch), canaryGovernanceData: buildCanaryGovernanceDossier(canaryResearch), selectivePromotionStatusData: selectivePromotionStatus(M76_SELECTIVE_PROMOTION_MANIFEST, selectivePromotionRuntime), onExportCanaryDossier: () => { try {
                                        const dossier = buildCanaryGovernanceDossier(canaryResearch);
                                        const blob = new Blob([JSON.stringify(dossier, null, 2) + "\n"], { type: "application/json" });
                                        const url = URL.createObjectURL(blob);
                                        const a = document.createElement("a");
                                        a.href = url;
                                        a.download = `pursuit-iron-canary-dossier-${new Date().toISOString().slice(0, 10)}.json`;
                                        document.body.appendChild(a);
                                   
```

## 32. perf\s*: @ 3374629

```js
me. The live session keeps writing the resume snapshot with the OLD
                                               exercise list (`db-bench|db-shoulder|dips-tri|cable-fly`) because that is what its
                                               stale `day` says, while the saved program now reads `db-incline-fly|...`. On resume
                                               the day comes from `saved`, `sessionSnapshotStatus` compares the two signatures,
                                               returns "stale" — and stale DELETES the snapshot and rebuilds the session from
                                               scratch. Every logged set of that workout is gone, which is Haiden's report: "the
                                               minimize and restore keeps restarting my workouts, especially if I close out the app
                                               and come back."
                                               Resolving by id at render makes the signature incapable of drifting: there is one
                                               day, the program's. `sessionDay` stays the pointer (it carries quick/freestyle days
                                               that are not in `saved` yet, hence the fallback). */
                                            program: program, day: liveDay, weekIndex: sessionWeek, unit: unit, setUnit: chooseUnit, perf: perf, onExit: () => { const q = program?.quick; setSessionDay(null); if (q) {
                                                setProgram(null);
                                                setView("home");
                                            }
                                            else
                                                setView("program"); }, onFinish: finishSession, onSaveRoutine: saveRoutine, restAutoStart: restAutoStart, warmupCard: warmupCard, onSetWarmupCard: setWarmupCard, restScale: restScale, onBan: addBan, banned: banned, history: history, loadMode: loadMode, onEditHistory: editHistoryEntry, goals: goals, onSetGoal: setGoalWeight, onUpdateProgramExercise: updateProgramExercise, onSetRest: setProgramRest, exNotes: exNotes, onSetExNote: (id, text) => setExNotes(prev => { const n = { ...prev }; const t = (text || "").trim(); if (t)
                                                n[id] = t;
                                            else
                                                delete n[id]; return n; }), exSetup: exSetup, onSetExSetup: (id, key, value) => setExSetup(prev => { const cur = { ...(prev[id] || {}) }; const v = (value || "").trim(); if (v)
                                                cur[key] = v;
                                            else
                                                delete cur[key]; const n = { ...prev }; if (Object.keys(cur).length)
                                                n[id] = cur;
                                            else
                                                delete n[id]; return n; }), onSetExLoadInc: setProgramExLoadInc })) : program ? (_jsx(ProgramView
                                        /* ⚠ setProgram STAYS THE DRAFT-LOCAL SETTER. Routing every edit through `commitProgram`
                                           wrote each change straight to `saved` and broke the draft model outright —
                                           gates/draftlifecycle failed 12 checks, starting with "the SAVED copy is untouched by
                                           an unsaved edit". An unsaved swap is meant to live in a draft until the lifter
                                           commits it. Only the engine card's two answers are immediate decisions rather than
                                           edits, so only they get `onComm
```

## UI lb 1 @ 616489

```js
f (ok) {
                used.add(match.id);
                used.delete(target.ex.id);
                return;
            }
            target.d.exercises = keep;
        }
    });
}
const GEN_PIPELINE = [
    { name: "daySkeleton", run: stageDaySkeleton },
    { name: "legacyWeights", when: g => engLacks(g.eng, "reduceAsCeiling") || engLacks(g.eng, "focusAsClaim"), run: stageLegacyWeights },
    { name: "daySize", run: stageDaySize },
    { name: "selectLifts", run: stageSelectLifts },
    { name: "pinScheme", run: stagePinScheme },
    { name: "tierAccessories", when: g => engHas(g.eng, "tierAccessories"), run: stageTierAccessories },
    { name: "openProgram", trace: "buildDaySlots", run: stageOpenProgram },
    { name: "applyCoverage", trace: "applyCoverage", run: g => applyCoverage(g.program, g.equipSet, g.banned, !!g.config.noBodyweight, g.globalUsed, g.eng, g.noDisplace) },
    { name: "preferBodyweightBase", trace: "preferBodyweightBase", run: g => preferBodyweightBase(g.program, g.equipSet, g.banned) },
    { name: "autoSuperset", trace: "autoSuperset", when: g => isShortSession(g) && !g.config.percentScheme, run: g => autoSuperset(g.program) },
    { name: "distributeVolBias", trace: "distributeVolBias", run: g => distributeVolBias(g.program, g.volBias) },
    { name: "seedManual", when: g => g.config.progression === "manual", run: g => seedManual(g.program) },
    { name: "consolidateFamilies", trace: "consolidateFamilies", run: g => consolidateFamilies(g.program) },
    { name: "capWeeklyVolume", trace: "capWeeklyVolume", run: g => capWeeklyVolume(g.program) },
    { name: "fitSessionTime", trace: "fitSessionTime", run: g => fitSessionTime(g.program) },
    { name: "fillToSessionFloor", trace: "fillToSessionFloor", when: g => engHas(g.eng, "sessionFloorFill"), run: g => fillToSessionFloor(g.program, g.eng) },
    /* ⚠ THE CAP GETS THE LAST WORD. `fillToSessionFloor` screens every candidate against every muscle
       it touches, and that is still not enough: adding a slot changes how sets are apportioned across
       the REST of the day, so a lift that loads no glutes at all pushed glutes from 25.8 to 26.4 on
       glute_focus/5/s120. No per-candidate arithmetic can see that — only re-running the cap can.
       Re-running is cheap and idempotent (it trims only what is over), and it means the invariant holds
       by construction rather than by the fill being clever enough. */
    { name: "capAfterFill", when: g => engHas(g.eng, "sessionFloorFill"), run: g => capWeeklyVolume(g.program) },
    /* ⚠ A DISPLACEMENT IS JUDGED AFTER THE TRIMMERS RUN, NOT WHEN IT IS MADE, AND MEASUREMENT IS WHY.
       `displaceOneFor` verifies its own swap against the real weekly volume and undoes it if a floor
       breaks — and that still left 29 of 1890 focused configs with some other muscle under its MEV,
       against ZERO at build 641. Two tightenings of the donor rule moved that 30 → 29 and left the same
       cases standing, which is the te
```

## UI lb 2 @ 616517

```js
d.add(match.id);
                used.delete(target.ex.id);
                return;
            }
            target.d.exercises = keep;
        }
    });
}
const GEN_PIPELINE = [
    { name: "daySkeleton", run: stageDaySkeleton },
    { name: "legacyWeights", when: g => engLacks(g.eng, "reduceAsCeiling") || engLacks(g.eng, "focusAsClaim"), run: stageLegacyWeights },
    { name: "daySize", run: stageDaySize },
    { name: "selectLifts", run: stageSelectLifts },
    { name: "pinScheme", run: stagePinScheme },
    { name: "tierAccessories", when: g => engHas(g.eng, "tierAccessories"), run: stageTierAccessories },
    { name: "openProgram", trace: "buildDaySlots", run: stageOpenProgram },
    { name: "applyCoverage", trace: "applyCoverage", run: g => applyCoverage(g.program, g.equipSet, g.banned, !!g.config.noBodyweight, g.globalUsed, g.eng, g.noDisplace) },
    { name: "preferBodyweightBase", trace: "preferBodyweightBase", run: g => preferBodyweightBase(g.program, g.equipSet, g.banned) },
    { name: "autoSuperset", trace: "autoSuperset", when: g => isShortSession(g) && !g.config.percentScheme, run: g => autoSuperset(g.program) },
    { name: "distributeVolBias", trace: "distributeVolBias", run: g => distributeVolBias(g.program, g.volBias) },
    { name: "seedManual", when: g => g.config.progression === "manual", run: g => seedManual(g.program) },
    { name: "consolidateFamilies", trace: "consolidateFamilies", run: g => consolidateFamilies(g.program) },
    { name: "capWeeklyVolume", trace: "capWeeklyVolume", run: g => capWeeklyVolume(g.program) },
    { name: "fitSessionTime", trace: "fitSessionTime", run: g => fitSessionTime(g.program) },
    { name: "fillToSessionFloor", trace: "fillToSessionFloor", when: g => engHas(g.eng, "sessionFloorFill"), run: g => fillToSessionFloor(g.program, g.eng) },
    /* ⚠ THE CAP GETS THE LAST WORD. `fillToSessionFloor` screens every candidate against every muscle
       it touches, and that is still not enough: adding a slot changes how sets are apportioned across
       the REST of the day, so a lift that loads no glutes at all pushed glutes from 25.8 to 26.4 on
       glute_focus/5/s120. No per-candidate arithmetic can see that — only re-running the cap can.
       Re-running is cheap and idempotent (it trims only what is over), and it means the invariant holds
       by construction rather than by the fill being clever enough. */
    { name: "capAfterFill", when: g => engHas(g.eng, "sessionFloorFill"), run: g => capWeeklyVolume(g.program) },
    /* ⚠ A DISPLACEMENT IS JUDGED AFTER THE TRIMMERS RUN, NOT WHEN IT IS MADE, AND MEASUREMENT IS WHY.
       `displaceOneFor` verifies its own swap against the real weekly volume and undoes it if a floor
       breaks — and that still left 29 of 1890 focused configs with some other muscle under its MEV,
       against ZERO at build 641. Two tightenings of the donor rule moved that 30 → 29 and left the same
       cases standing, which is the tell: the swap IS clean when i
```

## UI lb 3 @ 616547

```js
   used.delete(target.ex.id);
                return;
            }
            target.d.exercises = keep;
        }
    });
}
const GEN_PIPELINE = [
    { name: "daySkeleton", run: stageDaySkeleton },
    { name: "legacyWeights", when: g => engLacks(g.eng, "reduceAsCeiling") || engLacks(g.eng, "focusAsClaim"), run: stageLegacyWeights },
    { name: "daySize", run: stageDaySize },
    { name: "selectLifts", run: stageSelectLifts },
    { name: "pinScheme", run: stagePinScheme },
    { name: "tierAccessories", when: g => engHas(g.eng, "tierAccessories"), run: stageTierAccessories },
    { name: "openProgram", trace: "buildDaySlots", run: stageOpenProgram },
    { name: "applyCoverage", trace: "applyCoverage", run: g => applyCoverage(g.program, g.equipSet, g.banned, !!g.config.noBodyweight, g.globalUsed, g.eng, g.noDisplace) },
    { name: "preferBodyweightBase", trace: "preferBodyweightBase", run: g => preferBodyweightBase(g.program, g.equipSet, g.banned) },
    { name: "autoSuperset", trace: "autoSuperset", when: g => isShortSession(g) && !g.config.percentScheme, run: g => autoSuperset(g.program) },
    { name: "distributeVolBias", trace: "distributeVolBias", run: g => distributeVolBias(g.program, g.volBias) },
    { name: "seedManual", when: g => g.config.progression === "manual", run: g => seedManual(g.program) },
    { name: "consolidateFamilies", trace: "consolidateFamilies", run: g => consolidateFamilies(g.program) },
    { name: "capWeeklyVolume", trace: "capWeeklyVolume", run: g => capWeeklyVolume(g.program) },
    { name: "fitSessionTime", trace: "fitSessionTime", run: g => fitSessionTime(g.program) },
    { name: "fillToSessionFloor", trace: "fillToSessionFloor", when: g => engHas(g.eng, "sessionFloorFill"), run: g => fillToSessionFloor(g.program, g.eng) },
    /* ⚠ THE CAP GETS THE LAST WORD. `fillToSessionFloor` screens every candidate against every muscle
       it touches, and that is still not enough: adding a slot changes how sets are apportioned across
       the REST of the day, so a lift that loads no glutes at all pushed glutes from 25.8 to 26.4 on
       glute_focus/5/s120. No per-candidate arithmetic can see that — only re-running the cap can.
       Re-running is cheap and idempotent (it trims only what is over), and it means the invariant holds
       by construction rather than by the fill being clever enough. */
    { name: "capAfterFill", when: g => engHas(g.eng, "sessionFloorFill"), run: g => capWeeklyVolume(g.program) },
    /* ⚠ A DISPLACEMENT IS JUDGED AFTER THE TRIMMERS RUN, NOT WHEN IT IS MADE, AND MEASUREMENT IS WHY.
       `displaceOneFor` verifies its own swap against the real weekly volume and undoes it if a floor
       breaks — and that still left 29 of 1890 focused configs with some other muscle under its MEV,
       against ZERO at build 641. Two tightenings of the donor rule moved that 30 → 29 and left the same
       cases standing, which is the tell: the swap IS clean when it happens. What breaks the flo
```

## UI lb 4 @ 616568

```js
.ex.id);
                return;
            }
            target.d.exercises = keep;
        }
    });
}
const GEN_PIPELINE = [
    { name: "daySkeleton", run: stageDaySkeleton },
    { name: "legacyWeights", when: g => engLacks(g.eng, "reduceAsCeiling") || engLacks(g.eng, "focusAsClaim"), run: stageLegacyWeights },
    { name: "daySize", run: stageDaySize },
    { name: "selectLifts", run: stageSelectLifts },
    { name: "pinScheme", run: stagePinScheme },
    { name: "tierAccessories", when: g => engHas(g.eng, "tierAccessories"), run: stageTierAccessories },
    { name: "openProgram", trace: "buildDaySlots", run: stageOpenProgram },
    { name: "applyCoverage", trace: "applyCoverage", run: g => applyCoverage(g.program, g.equipSet, g.banned, !!g.config.noBodyweight, g.globalUsed, g.eng, g.noDisplace) },
    { name: "preferBodyweightBase", trace: "preferBodyweightBase", run: g => preferBodyweightBase(g.program, g.equipSet, g.banned) },
    { name: "autoSuperset", trace: "autoSuperset", when: g => isShortSession(g) && !g.config.percentScheme, run: g => autoSuperset(g.program) },
    { name: "distributeVolBias", trace: "distributeVolBias", run: g => distributeVolBias(g.program, g.volBias) },
    { name: "seedManual", when: g => g.config.progression === "manual", run: g => seedManual(g.program) },
    { name: "consolidateFamilies", trace: "consolidateFamilies", run: g => consolidateFamilies(g.program) },
    { name: "capWeeklyVolume", trace: "capWeeklyVolume", run: g => capWeeklyVolume(g.program) },
    { name: "fitSessionTime", trace: "fitSessionTime", run: g => fitSessionTime(g.program) },
    { name: "fillToSessionFloor", trace: "fillToSessionFloor", when: g => engHas(g.eng, "sessionFloorFill"), run: g => fillToSessionFloor(g.program, g.eng) },
    /* ⚠ THE CAP GETS THE LAST WORD. `fillToSessionFloor` screens every candidate against every muscle
       it touches, and that is still not enough: adding a slot changes how sets are apportioned across
       the REST of the day, so a lift that loads no glutes at all pushed glutes from 25.8 to 26.4 on
       glute_focus/5/s120. No per-candidate arithmetic can see that — only re-running the cap can.
       Re-running is cheap and idempotent (it trims only what is over), and it means the invariant holds
       by construction rather than by the fill being clever enough. */
    { name: "capAfterFill", when: g => engHas(g.eng, "sessionFloorFill"), run: g => capWeeklyVolume(g.program) },
    /* ⚠ A DISPLACEMENT IS JUDGED AFTER THE TRIMMERS RUN, NOT WHEN IT IS MADE, AND MEASUREMENT IS WHY.
       `displaceOneFor` verifies its own swap against the real weekly volume and undoes it if a floor
       breaks — and that still left 29 of 1890 focused configs with some other muscle under its MEV,
       against ZERO at build 641. Two tightenings of the donor rule moved that 30 → 29 and left the same
       cases standing, which is the tell: the swap IS clean when it happens. What breaks the floor is
       `capWeek
```

## UI lb 5 @ 647739

```js
    16,344-config sweep answered `frontOverMRV` +7 while the comparator saw no regression, because a
       muscle dropping under its MRV paid for another being pushed through it. The incoming movement
       pays secondary credit to muscles it does not name, which is exactly how a ceiling breaks without
       anyone asking for it. No muscle may end over its MRV that the undisplaced week keeps under. */
    const newUnder = [...sd.under].some(k => !sp.under.has(k));
    const newOver = [...sd.over].some(k => !sp.over.has(k));
    const newLate = [...sd.lateDays].some(([key, minutes]) => minutes > (sp.lateDays.get(key) || 0));
    const worse = newUnder || newOver || newLate || sd.late > sp.late;
    return worse ? plain : withDisp;
}
/* ⚠ WHICH DAYS HAPPEN IN A GIVEN WEEK. Until engine 29 the answer was always "all of them" — every
   volume figure, floor and ceiling iterated `program.days` outright. That is why the engine could not
   express an ALTERNATING program (ULU / LUL, StrongLifts A-B-A / B-A-B): those run a SUBSET of the
   day list each week and balance over the rotation, not the calendar week.
   A rotation is a rolling cursor over the day list. With days [Ua, La, Ub, Lb] and three sessions a
   week, session n takes days[n % 4]: week 1 is Ua·La·Ub, week 2 is Lb·Ua·La — three upper and three
   lower per fortnight, which is exactly why the method is used. `rotation` is the number of WEEKS the
   cursor takes to return to its start.
   Programs without a rotation are unchanged: every day, every week, same as before. */
/* ⚠ A SLOT CAN HOLD A PAIR. The on/off version of this (`weekOff`) frees a slot on alternate weeks
   but concentrates the volume into the on-week: measured on a real 2-set slot, doubling it took the
   day to 71 minutes against an s60 bound while the off-week's saved minutes sat idle in the wrong
   week. Pairing fixes both ends — forearms on odd weeks, calves on even, in ONE slot. The day's time
   is constant, each muscle gets DOUBLE the per-session dose, and neither costs a permanent slot.
   That matters because 267 of 2099 slots (13%) sit at the 2-set floor — biceps 57, triceps 49,
   chest 41 — which is the day spending its last minutes on a token dose. Two muscles at a real dose
   beats one muscle at a token one, and a fortnight window is what makes it scoreable.
   `pairs[dayId:slot]` names the alternate; odd weeks train the slot's own exercise, even weeks the
   alternate. Empty by default. */
function exerciseAt(program, day, slot, week) {
    const alt = program.pairs?.[`${day.id}:${slot}`];
    if (!alt)
        return day.exercises[slot];
    return (Math.max(1, week) % 2 === 0) ? alt : day.exercises[slot];
}
/* ⚠ PAIRED SLOTS — the first mechanism in this engine that serves a starved muscle WITHOUT TAKING A
   SLOT. Every earlier attempt needed one and failed for the same reason: focus displacement was
   discarded by the comparator, `reduce` freed a slot in 5 of 8 programs, `partMevFloor` could not
   
```

## UI lb 6 @ 647832

```js
cause a
       muscle dropping under its MRV paid for another being pushed through it. The incoming movement
       pays secondary credit to muscles it does not name, which is exactly how a ceiling breaks without
       anyone asking for it. No muscle may end over its MRV that the undisplaced week keeps under. */
    const newUnder = [...sd.under].some(k => !sp.under.has(k));
    const newOver = [...sd.over].some(k => !sp.over.has(k));
    const newLate = [...sd.lateDays].some(([key, minutes]) => minutes > (sp.lateDays.get(key) || 0));
    const worse = newUnder || newOver || newLate || sd.late > sp.late;
    return worse ? plain : withDisp;
}
/* ⚠ WHICH DAYS HAPPEN IN A GIVEN WEEK. Until engine 29 the answer was always "all of them" — every
   volume figure, floor and ceiling iterated `program.days` outright. That is why the engine could not
   express an ALTERNATING program (ULU / LUL, StrongLifts A-B-A / B-A-B): those run a SUBSET of the
   day list each week and balance over the rotation, not the calendar week.
   A rotation is a rolling cursor over the day list. With days [Ua, La, Ub, Lb] and three sessions a
   week, session n takes days[n % 4]: week 1 is Ua·La·Ub, week 2 is Lb·Ua·La — three upper and three
   lower per fortnight, which is exactly why the method is used. `rotation` is the number of WEEKS the
   cursor takes to return to its start.
   Programs without a rotation are unchanged: every day, every week, same as before. */
/* ⚠ A SLOT CAN HOLD A PAIR. The on/off version of this (`weekOff`) frees a slot on alternate weeks
   but concentrates the volume into the on-week: measured on a real 2-set slot, doubling it took the
   day to 71 minutes against an s60 bound while the off-week's saved minutes sat idle in the wrong
   week. Pairing fixes both ends — forearms on odd weeks, calves on even, in ONE slot. The day's time
   is constant, each muscle gets DOUBLE the per-session dose, and neither costs a permanent slot.
   That matters because 267 of 2099 slots (13%) sit at the 2-set floor — biceps 57, triceps 49,
   chest 41 — which is the day spending its last minutes on a token dose. Two muscles at a real dose
   beats one muscle at a token one, and a fortnight window is what makes it scoreable.
   `pairs[dayId:slot]` names the alternate; odd weeks train the slot's own exercise, even weeks the
   alternate. Empty by default. */
function exerciseAt(program, day, slot, week) {
    const alt = program.pairs?.[`${day.id}:${slot}`];
    if (!alt)
        return day.exercises[slot];
    return (Math.max(1, week) % 2 === 0) ? alt : day.exercises[slot];
}
/* ⚠ PAIRED SLOTS — the first mechanism in this engine that serves a starved muscle WITHOUT TAKING A
   SLOT. Every earlier attempt needed one and failed for the same reason: focus displacement was
   discarded by the comparator, `reduce` freed a slot in 5 of 8 programs, `partMevFloor` could not
   append because the clock refused, and anchors cannot create a slot at all (proven three ways)
```

## UI lb 7 @ 761006

```js
unt bought FREQUENCY and no volume: quads sat at 11–14 across every
     * day count while their own MAV is 14 and MRV is 20.
     *
     * THE CURVE, AND WHY THIS ONE. Pelland et al. 2026 — already cited in this app's own references —
     * found the dose–response still climbing as weekly sets rise, with diminishing returns rather
     * than a threshold past which more becomes harmful, and found that for SIZE what mattered was the
     * weekly total while FREQUENCY was what mattered for strength. Set that against the within-session
     * finding that per-set stimulus decays as sets pile onto one muscle in one session, and the rule
     * writes itself: higher frequency is precisely what makes a higher weekly total deliverable
     * without stacking unproductive sets into a single day. So the floor rises with the number of days
     * that actually train the muscle:
     *
     *     floor = clamp(MEV + ((freq - 1) / 2) * (MAV - MEV), MEV, MAV)
     *
     * freq 1 → MEV (one session cannot productively hold more), freq 2 → the midpoint, freq 3+ → MAV.
     * For quads that is 8 → 11 → 14.
     *
     * NEVER ABOVE MAV FROM COVERAGE. MEV→MRV stays the AUTOREGULATION range — volBias, readiness and
     * the feedback nudge own the space above MAV, and a generator that spent it up front would leave
     * them nothing to work with and every lifter starting at their recoverable ceiling.
     *
     * FROM THE LANDMARKS, NOT NEW CONSTANTS. `landmarkFor` and `mavFor` are the app's single source
     * for these numbers; hardcoding a second set here is what produced a calves floor of 6 against a
     * calves MEV of 8 — a "floor" that sat BELOW the minimum it was supposed to guarantee, which is
     * why calves showed under MEV throughout the audit. */
    /* Sub-regions answer through subRegionOf; whole parts through ex.part. One predicate so a rule can
       name either without the caller knowing which kind it got. */
    const SUBREG = new Set(["side_delts", "rear_delts", "front_delts", "upper_traps"]);
    const isPart = (ex, part) => (SUBREG.has(part) ? subRegionOf(ex) === part : ex?.part === part);
    const dayFreq = (part) => program.days.filter(d => d.exercises.some(id => isPart(EX_BY_ID[id], part))).length;
    /* ⚠ RENAMED FROM `landmarkOf`, WHICH IS ALSO A GLOBAL WITH A DIFFERENT CONTRACT. The global
       `landmarkOf(id)` resolves SUB_LANDMARKS first and so answers for heads as well as parts; this
       local one is experience-aware but head-BLIND, and it shadowed the global inside this scope only.
       Two contracts under one name, a few hundred lines apart — line 1658 calls the global, line 2296
       called this. That collision is what made an edit to `preferenceFloor` behave differently from
       how it read. Name says which one you get. */
    const landmarkForProgram = (part) => landmarkFor(part, program?.config?.experience) || {};
    const scaledFloor = (part, scale = 1) => {
        const L = landmarkForProgram(part)
```

## UI lb 8 @ 771533

```js
that makes
     * the day longer than PATTERN_SWAP_SLACK_MIN allows. Zero earns a swap; short does not — a muscle
     * with some work has a secondary source to recover from, and one at nothing has none.
     *
     * ⚠⚠ WRITTEN, MEASURED, AND HELD — THIS IS THE FIFTH FAILED ATTEMPT AND THE MOST INFORMATIVE.
     * On the widened sweep (24,840 configs) it took reduce-zeroes from 237 of 840 to 47, the best
     * result any attempt has produced — and cost partsUnderMEV +11,030 (10.1%) and zeroSide +444
     * (18.8%). The reason is structural and obvious in hindsight: every swap takes a slot from a muscle
     * that was above MEV and pushes it below. Trading a zero for a shortfall is still a trade, and at
     * this scale it is a bad one. Giving tier 1 more POWER inside the existing structure just moves the
     * damage somewhere the tier system cannot see, because the donor is chosen by a local "most
     * expendable" score with no view of what the other passes will need afterwards.
     *
     * THE FIVE ATTEMPTS, ALL MEASURED, ALL WORSE:
     *   1. costsPartZero veto in fitSessionTime      pattern-absent 6 → 22, delt zeros 0 → 5
     *   2. prefer-not-empty in the blind fallback     7 → 7 (no effect)
     *   3. that preference at every tier              7 → 8
     *   4. stop the delt floor escalating to MEV      7 → 11
     *   5. tier-1 swap for a target at zero (here)    partsUnderMEV +11,030, zeroSide +444
     *
     * The conclusion this forces is the one DESIGN-engine-v2.md predicted and it is now paid for five
     * times: NO LOCAL CHANGE INSIDE THIS STRUCTURE WORKS. Not a veto, not a preference, not a reorder,
     * not a stronger instrument. Every one of them helps the target it aims at and damages something a
     * later pass was relying on, because each pass decides alone with no view of the claims still to
     * come. The refactor is not optional polish — Stage 1b, one queue and ONE award point where every
     * claim is visible at once, is the only version of this that can work, and five measurements now
     * say so rather than one architect's opinion. Do not attempt a sixth local fix. */
    const swapForZero = (part) => {
        const wk = weeksOf(program);
        const vol0 = weeklyVolume(program, wk), sub0 = weeklySubVolume(program, wk);
        /* ⚠ IS THIS PART THE LAST OF AN ENTIRE MOVEMENT PATTERN? IF SO THE DELT-HEAD VETO BELOW MUST
           NOT STOP THE SWAP, AND THIS IS AN ORDERING BETWEEN TWO EXISTING PROTECTIONS, NOT A FIFTH RULE.
           Both are tier-1 concerns — a target at literal zero — and until now the head veto silently won
           every tie because it was written second. Measured at engine 10: 4 programs come out with NO
           PULLING VOLUME AT ALL, against 0 at engine 7, and in every one of the four the swap the guard
           refuses is a Dumbbell Lateral Raise whose removal costs nothing except side delts. So the
           app's own fix for zero side delts had begun outranki
```

## UI lb 9 @ 802210

```js
eir gym, not a preference. */
                        if (engHas(eng, "barbellCapEverywhere") && !dayBarbellRoom(program, d, rep)
                            && !isBarLike(EX_BY_ID[d.exercises[si]]?.equip))
                            continue;
                        d.exercises[si] = rep.id;
                        globalUsed.add(rep.id);
                        swapped = true;
                        break;
                    }
                }
                if (swapped)
                    return true;
            }
            /* ⚠ AND WHEN THERE IS NO FRONT-DELT PRESS TO TRADE, THE HEAD STILL GETS NOTHING.
             * The trade above is deliberately narrow — only ever from front delts, which are supplied
             * by every bench and overhead press in the week and cost nothing to give up. That covers a
             * 60-minute day carrying three presses. It does not cover a TWO-SLOT day, where the two
             * slots are typically a squat and a bench and there is no front-delt movement to convert.
             * Measured when engine 8 was first switched on: honest candidate pricing refused the append
             * on 16-20 minute days and this pass had no fallback, taking s20 programs with side delts at
             * zero from 1 of 42 to 24 of 42 — while every other session length stayed at 0. That is why
             * engine 8 was held back a release rather than shipped with a more accurate clock.
             *
             * So for a head at LITERAL ZERO only, widen the victim pool to any non-primary slot, ranked
             * and refused exactly as the movement-pattern floor does: most expendable first (furthest
             * above its own MEV), never the primary, never a trade that empties something else, and
             * never one that makes the day longer. Zero earns a wider search than "short" does — nothing
             * else in the app trains side or rear delts, so there is no other path back. */
            /* ⚠ WIDENING THIS TRIGGER WAS TRIED AND REVERTED — the `<= 0` is CALIBRATED, not an oversight.
               The tempting change: fire when the head has no DIRECT SLOT rather than no volume at all,
               because rear delts almost never hit zero (every barbell row credits them 0.3/set, so six row
               sets put them at ~1.8 while MEV is 8, and 15 of 86 programs finish under MEV with no direct
               slot — their one front-delt slot being the day's primary press, which must never be traded).
               It works mechanically: heads with no direct slot fell 22 -> 13. IT ALSO COSTS MORE THAN IT
               BUYS: gates/rotationquality went to floors 42 / zeros 12, because every extra swap takes a
               victim slot below ITS floor. Rear delts at MEV 8 need 2-3 direct slots; one slot plus row
               credit reaches ~4.8, so the trade does not even close the gap it opens.
               SAME WALL AS PAIRED SLOTS: at s60 there is no spare slot, and serving one st
```

## UI lb 10 @ 824182

```js
wed 10% of slack because a muscle under MEV is not
                   being trained properly and that outranks a tidy finish time. A PREFERENCE is not: focus
                   spending into the slack leaves the day over its real bound, `fitSessionTime` runs after
                   this pass and answers by deleting a slot, and what it deletes can be another muscle's ONLY
                   movement. Measured: focus cost another muscle its training in 24 of 126 configs at the
                   frozen engine and 3 under the ladder while this used the soft bound — the last 3 are this.
                   Tier 3 asks last and should spend last. */
                if (cur >= 4 || estimateMinutes(program, d, 2) + 4 > hiBound)
                    continue;
                const key = `${d.id}:${si}`;
                /* ⚠ VERIFY WITH `weeklyVolume`, NOT `weeklyDirectOf`, AND THIS COST A ROUND. weeklyDirectOf
                   sums `baseSetsFor`, which is the slot's BASE prescription and does not read `slotBias` at
                   all — so the bump was invisible to its own verifier, every bump looked like it bought
                   nothing, every one was dutifully undone, and the fallback could never fire. The claim was
                   raised exactly once and refused, which read as "there is no room" when the truth was "the
                   oracle cannot see the lever". `oneDeltPass` verifies with weeklySubVolume for precisely
                   this reason and I reached for the wrong neighbour. */
                const before = weeklyVolume(program, weeksOf(program))[part] || 0;
                program.slotBias = { ...(program.slotBias || {}), [key]: ((program.slotBias || {})[key] || 0) + 1 };
                /* ⚠ VERIFY THE TIME AFTER THE BUMP, NOT A +4 GUESS BEFORE IT. The pre-check estimates a set
                   at four minutes; the real cost depends on the movement and the rest scaling, and when the
                   guess ran low the day crossed its bound and `fitSessionTime` answered by deleting a whole
                   SLOT — costing the focused muscle more than the bump bought it. Measured: verifying volume
                   alone left focus harmful on 10 of 252 configs, all of them this. A pass that has to be
                   undone is cheaper than a promise that has to be broken. */
                if ((weeklyVolume(program, weeksOf(program))[part] || 0) > before &&
                    estimateMinutes(program, d, weeksOf(program)) <= hiBound)
                    return true;
                const undo = { ...program.slotBias };
                if (undo[key] > 1)
                    undo[key] -= 1;
                else
                    delete undo[key];
                program.slotBias = undo;
            }
        }
        return false;
    };
    /* ⚠ THE LAST RESORT FOR A FOCUS POINT: TAKE A SLOT, DO NOT ASK FOR A NEW ONE.
       The picker says "Each level adds one extra exercise for that muscle." MEASURED at build 
```

## UI lb 11 @ 863875

```js
ass already
     * charges lats, upper_back and biceps, and it still produces these programs — because at s20 the
     * days are ALREADY at 25 and 24 minutes against a 22-minute ceiling before anything is added, so
     * `mins + addMin > hiBound * 1.1` refuses every single append. Adding another appending floor,
     * raising its priority, or reordering the round robin would all have failed for the same reason
     * and it would have looked like the rule was not firing. THE SESSION IS FULL. The only move left
     * is to change WHAT is in it.
     *
     * SO THIS PASS SWAPS. It is the first pass in the generator that replaces a slot rather than
     * adding or removing one, which is exactly the trade doc-2 §18 describes: with the session full
     * and one group at zero, redistributing a slot beats both adding volume and doing nothing.
     *
     * SIX REFUSALS, because a swap can do more damage than an append:
     *   1. Only when the group is at essentially ZERO (PATTERN_MIN_SETS). Under-trained is the MEV
     *      floors' job; this is about absent.
     *   2. Only when the kit can actually train it — see patternTrainable.
     *   3. APPEND FIRST. A swap is the fallback, never the preference: if the ordinary floor machinery
     *      has room, use it and change nothing else.
     *   4. Never the day's primary or a pinned T2. The primary is the reason the session exists.
     *   5. Never a swap that zeroes ANOTHER group. Trading pulling for pushing is not a fix, and at
     *      two slots a day it is the obvious failure mode.
     *   7. Never a swap that takes a muscle from some volume to ZERO. Slack ordering picks the
     *      cheapest victim; it cannot refuse when every victim is expensive.
     *   6. Never a swap that makes the day LONGER. These are the programs already over their ceiling;
     *      buying coverage with more minutes would pay for it out of the promise the wizard made.
     * Then the effect is VERIFIED and the swap is rolled back if the group is still short — the same
     * contract every other pass here follows.
     *
     * ENGINE-GATED. A program minted on engine 5 regenerates to its frozen layout; this reaches new
     * programs and anyone who accepts the engine update. */
    const kit = equipSet; // already expanded by the caller — see the v581 note on raw Sets
    const wk = weeksOf(program);
    const groupVol = (parts) => { let v; try {
        v = weeklyVolume(program, wk);
    }
    catch {
        return Infinity;
    } return parts.reduce((s, m) => s + (v[m] || 0), 0); };
    const pinned = (d, si) => si === d.primaryIndex || (d.t2Index != null && si === d.t2Index);
    /* Which group a slot FEEDS, for refusal 5. Read off the exercise's own part through the same
       table the floor uses, so a part in no group (calves, abs, forearms) is freely expendable. */
    const groupOfPart = (part) => Object.entries(PATTERN_GROUPS).find(([, ps]) => ps.includes(part))?.[0] || null;
    for (
```

## UI lb 12 @ 921356

```js
oor"), run: coverPatternFloor },
    { name: "partMrvCeiling", trace: "  ·partMrvCeiling", run: coverPartMrvCeiling },
    { name: "deltMrvCeiling", trace: "  ·deltMrvCeiling", run: coverDeltMrvCeiling },
    { name: "brachialis", trace: "  ·brachialis", run: coverBrachialis },
    { name: "mavCeiling", when: c => engHas(c.eng, "mavCeiling"), run: coverMavCeiling },
    /* ⚠ `sessionPuos` IS HELD AND THEREFORE NOT LISTED. A pass with `when: () => false` still DECLARES
       its trace, and gates/pipeline check 7 requires every declared trace to appear in the trace in
       order — so a held pass left in COVERAGE_PASSES fails that check forever, at every engine. Hold a
       pass by removing its entry; `coverSessionPuos` below is untouched and ready to re-list. */
];
function applyCoverage(program, equipSet, banned, noBw, globalUsed, eng = 1, noDisplace = false) {
    runPipeline(COVERAGE_PASSES, { ...coverageContext(program, equipSet, banned, noBw, globalUsed, eng), noDisplace });
}
// Spread each muscle's recommended set delta across that muscle's exercises in the
// program (one set at a time, compounds first). Mutates program.slotBias + config.autoVolume.
function distributeVolBias(program, volBias, field = "slotBias") {
    if (!volBias || !Object.keys(volBias).length) {
        if (field === "autoBias")
            program.autoBias = {};
        return;
    }
    const peak = weeksOf(program);
    const SET_CEIL = 5; // don't pile more than this onto any single exercise — that's junk volume
    // Per-session autoregulation writes a SEPARATE `autoBias` accumulator, rebuilt from scratch each
    // call, so it's applied to the DESIGNED `slotBias` baseline and can never compound (the bug where
    // merging into slotBias every session grew set counts week over week). `slotBias` itself is only
    // written by generation and the manual volume auto-fix — the program's designed distribution.
    const target = field === "autoBias" ? {} : { ...(program.slotBias || {}) };
    // Headroom math is measured against the designed baseline only — never the ephemeral autoBias.
    const baseProg = { ...program, autoBias: undefined };
    const baseVol = weeklyVolume(baseProg, peak); // designed per-muscle weekly volume, for the MRV headroom guard
    PART_ORDER.forEach(part => {
        let delta = volBias[part] || 0;
        if (!delta)
            return;
        const slots = [];
        program.days.forEach(d => d.exercises.forEach((id, si) => {
            if (EX_BY_ID[id]?.part === part) {
                const cur = Number(computeCell(baseProg, d, id, si, peak).sets) || 0;
                slots.push({ key: `${d.id}:${si}`, comp: EX_BY_ID[id]?.type === "compound", cur });
            }
        }));
        if (!slots.length)
            return;
        if (delta > 0) {
            // MRV headroom guard: feedback/performance can only ADD volume up to a muscle's max recoverable
            // volume — never past it. This is what keeps a good-recov
```

## UI volume 1 @ 161593

```js
e-between;gap:12px;min-width:0;padding:3px 10% 8px 12%;color:${C.faint};font-size:10.5px;font-weight:600;line-height:1.2;font-variant-numeric:tabular-nums;}
      .wpb-chart-axis>span{min-width:0;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;}
      .wpb-chart-axis>span:last-child{text-align:right;margin-left:auto;}
      .wpb-chart-popover{position:absolute;top:8px;right:10px;z-index:2;max-width:calc(100% - 20px);padding:5px 8px;border-radius:8px;background:color-mix(in srgb,${C.bg2} 94%,transparent);border:1px solid ${C.borderSoft};box-shadow:0 5px 18px rgba(0,0,0,.14);color:${C.muted};font-size:11px;line-height:1.25;pointer-events:none;font-variant-numeric:tabular-nums;}
      .wpb-chart-popover b{color:${C.text};font-weight:700;}
      .wpb-exercise-chart-surface{overflow:hidden;min-width:0;max-width:100%;}

      @media(max-width:420px){
        .wpb-onboarding-scroll{padding-left:18px!important;padding-right:18px!important;}
        .wpb-onboarding-footer{margin-left:-18px!important;margin-right:-18px!important;padding-left:18px!important;padding-right:18px!important;}
        .wpb-onboarding-title{font-size:28px!important;}
      }
      /* M130 PROGRESS CHART / VOLUME CORRECTION PASS — graph plot content stays inside its SVG,
         date labels live in a responsive HTML axis instead of being scaled/clipped inside the
         viewBox, and the dedicated Progress > Volume surface no longer collapses into a blank tab. */
      .wpb-progress [data-infocard][data-collapsible="0"] .wpb-infocard-static-head{border-bottom:1px solid var(--section-rule);}
      @media(max-width:390px){
        .wpb-chart-axis{font-size:10px!important;padding-left:13%!important;padding-right:9%!important;}
      }

      /* M128 REAL-DEVICE CORRECTION PASS — driven by phone screenshots. Keep the Home composer
         physically adjacent to the tab bar, align expanded-history identity/actions, enlarge the
         workout header controls, and make the active exercise label morph instead of snapping. */
      .wpb-home-compose-bar{pointer-events:none;}
      .wpb-home-compose-bar>button{pointer-events:auto;box-shadow:0 1px 0 rgba(255,255,255,.02);}
      .wpb-home-create,.wpb-home-quick{min-height:50px!important;border-radius:13px!important;}
      /* M129 DATA / ONBOARDING CORRECTION PASS — wizard and intro action bars are now real flex
         siblings rather than sticky overlays. Override the older scroller-era negative margins and
         gradients so neither footer can cover or bleed into the answer list on a phone. */
      .wpb-wizard>.wpb-wizard-footer{margin-left:0!important;margin-right:0!important;padding-left:20px!important;padding-right:20px!important;background:${C.bg}!important;}
      .wpb-onboarding>.wpb-onboarding-footer{margin-left:0!important;margin-right:0!important;padding-left:22px!important;padding-right:22px!important;background:${C.bg}!important;}
      .wpb-history-detail-head{min-height:50px;}
      .wpb-history-actions>but
```

## UI volume 2 @ 161801

```js
nowrap;overflow:hidden;text-overflow:ellipsis;}
      .wpb-chart-axis>span:last-child{text-align:right;margin-left:auto;}
      .wpb-chart-popover{position:absolute;top:8px;right:10px;z-index:2;max-width:calc(100% - 20px);padding:5px 8px;border-radius:8px;background:color-mix(in srgb,${C.bg2} 94%,transparent);border:1px solid ${C.borderSoft};box-shadow:0 5px 18px rgba(0,0,0,.14);color:${C.muted};font-size:11px;line-height:1.25;pointer-events:none;font-variant-numeric:tabular-nums;}
      .wpb-chart-popover b{color:${C.text};font-weight:700;}
      .wpb-exercise-chart-surface{overflow:hidden;min-width:0;max-width:100%;}

      @media(max-width:420px){
        .wpb-onboarding-scroll{padding-left:18px!important;padding-right:18px!important;}
        .wpb-onboarding-footer{margin-left:-18px!important;margin-right:-18px!important;padding-left:18px!important;padding-right:18px!important;}
        .wpb-onboarding-title{font-size:28px!important;}
      }
      /* M130 PROGRESS CHART / VOLUME CORRECTION PASS — graph plot content stays inside its SVG,
         date labels live in a responsive HTML axis instead of being scaled/clipped inside the
         viewBox, and the dedicated Progress > Volume surface no longer collapses into a blank tab. */
      .wpb-progress [data-infocard][data-collapsible="0"] .wpb-infocard-static-head{border-bottom:1px solid var(--section-rule);}
      @media(max-width:390px){
        .wpb-chart-axis{font-size:10px!important;padding-left:13%!important;padding-right:9%!important;}
      }

      /* M128 REAL-DEVICE CORRECTION PASS — driven by phone screenshots. Keep the Home composer
         physically adjacent to the tab bar, align expanded-history identity/actions, enlarge the
         workout header controls, and make the active exercise label morph instead of snapping. */
      .wpb-home-compose-bar{pointer-events:none;}
      .wpb-home-compose-bar>button{pointer-events:auto;box-shadow:0 1px 0 rgba(255,255,255,.02);}
      .wpb-home-create,.wpb-home-quick{min-height:50px!important;border-radius:13px!important;}
      /* M129 DATA / ONBOARDING CORRECTION PASS — wizard and intro action bars are now real flex
         siblings rather than sticky overlays. Override the older scroller-era negative margins and
         gradients so neither footer can cover or bleed into the answer list on a phone. */
      .wpb-wizard>.wpb-wizard-footer{margin-left:0!important;margin-right:0!important;padding-left:20px!important;padding-right:20px!important;background:${C.bg}!important;}
      .wpb-onboarding>.wpb-onboarding-footer{margin-left:0!important;margin-right:0!important;padding-left:22px!important;padding-right:22px!important;background:${C.bg}!important;}
      .wpb-history-detail-head{min-height:50px;}
      .wpb-history-actions>button,.wpb-history-actions>span>button{min-width:40px!important;min-height:40px!important;}
      .wpb-workout-header-action,.wpb-workout-close{min-width:40px!important;min-height:40px!important;}
      .wpb-ex
```

## UI volume 3 @ 277519

```js
c one — the glutes
    // are the largest muscle in the body and gluteus medius (abduction) is a separate head that a
    // squat pattern barely reaches. These days make the HIP the anchor: a hinge or a thrust leads,
    // abduction gets its own slot, and the quads stay weighted enough that the leg isn't built lopsided.
    glute_a: { label: "Glutes · Hinge", prefer: { glutes: "hip-extension", hamstrings: "hip-hinge" }, parts: [["glutes", 2.1], ["hamstrings", 1.5], ["lower_back", 0.5], ["quads", 0.7], ["abs", 0.5]] },
    glute_b: { label: "Glutes · Squat", prefer: { glutes: "hip-extension", quads: "squat" }, parts: [["glutes", 1.9], ["quads", 1.5], ["abductors", 1.0], ["adductors", 0.6], ["calves", 0.7]] },
    lower_glute: { label: "Lower · Glute Bias", prefer: { glutes: "hip-extension", hamstrings: "hip-hinge" }, parts: [["glutes", 1.8], ["hamstrings", 1.3], ["quads", 1.2], ["abductors", 0.7], ["calves", 0.7]] },
    // The upper day for a lower-body-led week. Pulling outweighs pressing here: the chest-first upper
    // days above are the right call when the week is balanced, but when two or three sessions are
    // already hip work, the upper session's job is postural back volume plus delt/arm shaping.
    upper_tone: { label: "Upper · Back & Delts", prefer: { upper_back: "horizontal-pull", lats: "vertical-pull" }, parts: [["upper_back", 1.4], ["lats", 1.2], ["shoulders", 1.3], ["chest", 0.9], ["biceps", 0.8], ["triceps", 0.8]] },
    // Dedicated trunk day. Abs carry it, with the hip abductors/adductors trained directly so "core"
    // doesn't collapse into pure anterior flexion. Deliberately NO lower_back weight: the deadlift is
    // filed under lower_back and is staple-priority, so any weight at all here reliably drew a heavy
    // axial pull onto what is supposed to be the week's lightest day. Low back is covered by the
    // hinge that anchors glute_a instead.
    core_abs: { label: "Core", prefer: { abs: "spinal-flexion" }, parts: [["abs", 2.6], ["abductors", 0.7], ["adductors", 0.5]] }
};
const SPLITS = {
    full_body: { name: "Full Body", /* Was "Train everything each session", which is not what it builds. Measured across 3/4/5/6 days
             at every session length, a day covers 3-5 of the 7 major muscle groups and NEVER all seven —
             there is no session budget in which eight movement patterns fit. The rotation is the design and
             it is a good one, so the blurb now describes the rotation instead of promising a session that
             cannot exist. A split's blurb is read before anything is generated, which makes it the one
             claim a user cannot check before committing. */
        blurb: "Rotating full-body days — each session anchors on a heavy compound and covers several movement patterns, so every muscle is trained two or more times a week. Spreads fatigue across patterns and scales from low to high frequency.", days: [2, 3, 4, 5, 6], build: (d) => { const seq = ["fb_a", "fb_b", "fb
```

## UI volume 4 @ 280150

```js
   claim a user cannot check before committing. */
        blurb: "Rotating full-body days — each session anchors on a heavy compound and covers several movement patterns, so every muscle is trained two or more times a week. Spreads fatigue across patterns and scales from low to high frequency.", days: [2, 3, 4, 5, 6], build: (d) => { const seq = ["fb_a", "fb_b", "fb_c", "fb_d", "fb_e"]; return Array.from({ length: d }, (_, i) => seq[i % seq.length]); } },
    upper_lower: { name: "Upper / Lower", blurb: "Alternate upper & lower days, rotating horizontal/vertical and quad/hinge emphasis.", days: [2, 4, 6], build: (d) => Array.from({ length: d }, (_, i) => `${i % 2 === 0 ? "upper" : "lower"}${Math.floor(i / 2) % 2 === 0 ? "_a" : "_b"}`) },
    ppl: { name: "Push / Pull / Legs", blurb: "The classic, with each repeat rotating emphasis (chest/delt, width/thickness, quad/posterior).", days: [3, 5, 6], build: (d) => { const A = ["push_a", "pull_a", "legs_a"], B = ["push_b", "pull_b", "legs_b"]; return Array.from({ length: d }, (_, i) => (Math.floor(i / 3) % 2 === 0 ? A : B)[i % 3]); } },
    ulppl: { name: "Upper·Lower·Push·Pull·Legs", blurb: "Hybrid 5-day blending UL frequency with PPL volume.", days: [5], build: () => ["upper", "lower", "push", "pull", "legs"] },
    hybrid: { name: "Hybrid Strength + Size", blurb: "Heavy strength Upper & Lower to open the week, then a higher-volume hypertrophy Push/Pull/Legs — strength and size in one week, the way modern physique programs run it.", days: [5], build: () => ["upper", "lower", "push", "pull", "legs"], focus: () => ["strength", "strength", "hypertrophy", "hypertrophy", "hypertrophy"] },
    phul: { name: "Power / Hypertrophy Upper-Lower", blurb: "Heavy upper/lower sessions followed by higher-rep upper/lower sessions; The plan adapts the exact prescription and progression.", days: [4], minSession: "s60", maxSession: "s120", build: () => ["upper_power", "lower_power", "upper_hyp", "lower_hyp"], focus: () => ["strength", "strength", "hypertrophy", "hypertrophy"] },
    phat: { name: "Power–Hypertrophy 5-Day", blurb: "Two heavy power days then three higher-rep hypertrophy days.", days: [5], minSession: "s60", maxSession: "s120", build: () => ["upper_power", "lower_power", "back_shoulders", "lower_hyp", "chest_arms"], focus: () => ["strength", "strength", "hypertrophy", "hypertrophy", "hypertrophy"] },
    bro: { name: "Bro Split", blurb: "One muscle group per day, max volume each.", days: [5, 6], build: (d) => d >= 6 ? ["chest_day", "back_day", "shoulders_day", "legs_day", "arms", "legs_day"] : ["chest_day", "back_day", "shoulders_day", "legs_day", "arms"] },
    arnold: { name: "Chest + Back / Shoulders + Arms / Legs", blurb: "Chest+Back · Shoulders+Arms · Legs, twice over, with Pursuit-owned dose and progression.", days: [6], build: () => ["chest_back", "shoulders_arms", "legs_day", "chest_back", "shoulders_arms", "legs_day"] },
    five_three_one: { name: "Main-Lift Waves", blurb: "Four days, each built
```

## UI volume 5 @ 280345

```js
ned two or more times a week. Spreads fatigue across patterns and scales from low to high frequency.", days: [2, 3, 4, 5, 6], build: (d) => { const seq = ["fb_a", "fb_b", "fb_c", "fb_d", "fb_e"]; return Array.from({ length: d }, (_, i) => seq[i % seq.length]); } },
    upper_lower: { name: "Upper / Lower", blurb: "Alternate upper & lower days, rotating horizontal/vertical and quad/hinge emphasis.", days: [2, 4, 6], build: (d) => Array.from({ length: d }, (_, i) => `${i % 2 === 0 ? "upper" : "lower"}${Math.floor(i / 2) % 2 === 0 ? "_a" : "_b"}`) },
    ppl: { name: "Push / Pull / Legs", blurb: "The classic, with each repeat rotating emphasis (chest/delt, width/thickness, quad/posterior).", days: [3, 5, 6], build: (d) => { const A = ["push_a", "pull_a", "legs_a"], B = ["push_b", "pull_b", "legs_b"]; return Array.from({ length: d }, (_, i) => (Math.floor(i / 3) % 2 === 0 ? A : B)[i % 3]); } },
    ulppl: { name: "Upper·Lower·Push·Pull·Legs", blurb: "Hybrid 5-day blending UL frequency with PPL volume.", days: [5], build: () => ["upper", "lower", "push", "pull", "legs"] },
    hybrid: { name: "Hybrid Strength + Size", blurb: "Heavy strength Upper & Lower to open the week, then a higher-volume hypertrophy Push/Pull/Legs — strength and size in one week, the way modern physique programs run it.", days: [5], build: () => ["upper", "lower", "push", "pull", "legs"], focus: () => ["strength", "strength", "hypertrophy", "hypertrophy", "hypertrophy"] },
    phul: { name: "Power / Hypertrophy Upper-Lower", blurb: "Heavy upper/lower sessions followed by higher-rep upper/lower sessions; The plan adapts the exact prescription and progression.", days: [4], minSession: "s60", maxSession: "s120", build: () => ["upper_power", "lower_power", "upper_hyp", "lower_hyp"], focus: () => ["strength", "strength", "hypertrophy", "hypertrophy"] },
    phat: { name: "Power–Hypertrophy 5-Day", blurb: "Two heavy power days then three higher-rep hypertrophy days.", days: [5], minSession: "s60", maxSession: "s120", build: () => ["upper_power", "lower_power", "back_shoulders", "lower_hyp", "chest_arms"], focus: () => ["strength", "strength", "hypertrophy", "hypertrophy", "hypertrophy"] },
    bro: { name: "Bro Split", blurb: "One muscle group per day, max volume each.", days: [5, 6], build: (d) => d >= 6 ? ["chest_day", "back_day", "shoulders_day", "legs_day", "arms", "legs_day"] : ["chest_day", "back_day", "shoulders_day", "legs_day", "arms"] },
    arnold: { name: "Chest + Back / Shoulders + Arms / Legs", blurb: "Chest+Back · Shoulders+Arms · Legs, twice over, with Pursuit-owned dose and progression.", days: [6], build: () => ["chest_back", "shoulders_arms", "legs_day", "chest_back", "shoulders_arms", "legs_day"] },
    five_three_one: { name: "Main-Lift Waves", blurb: "Four days, each built around one main barbell lift (Press · Deadlift · Bench · Squat) plus targeted accessories, loaded off a training max.", days: [4], minSession: "s60", maxSession: "s120", build: () => ["t531_pres
```

## UI volume 6 @ 281402

```js
 "push", "pull", "legs"] },
    hybrid: { name: "Hybrid Strength + Size", blurb: "Heavy strength Upper & Lower to open the week, then a higher-volume hypertrophy Push/Pull/Legs — strength and size in one week, the way modern physique programs run it.", days: [5], build: () => ["upper", "lower", "push", "pull", "legs"], focus: () => ["strength", "strength", "hypertrophy", "hypertrophy", "hypertrophy"] },
    phul: { name: "Power / Hypertrophy Upper-Lower", blurb: "Heavy upper/lower sessions followed by higher-rep upper/lower sessions; The plan adapts the exact prescription and progression.", days: [4], minSession: "s60", maxSession: "s120", build: () => ["upper_power", "lower_power", "upper_hyp", "lower_hyp"], focus: () => ["strength", "strength", "hypertrophy", "hypertrophy"] },
    phat: { name: "Power–Hypertrophy 5-Day", blurb: "Two heavy power days then three higher-rep hypertrophy days.", days: [5], minSession: "s60", maxSession: "s120", build: () => ["upper_power", "lower_power", "back_shoulders", "lower_hyp", "chest_arms"], focus: () => ["strength", "strength", "hypertrophy", "hypertrophy", "hypertrophy"] },
    bro: { name: "Bro Split", blurb: "One muscle group per day, max volume each.", days: [5, 6], build: (d) => d >= 6 ? ["chest_day", "back_day", "shoulders_day", "legs_day", "arms", "legs_day"] : ["chest_day", "back_day", "shoulders_day", "legs_day", "arms"] },
    arnold: { name: "Chest + Back / Shoulders + Arms / Legs", blurb: "Chest+Back · Shoulders+Arms · Legs, twice over, with Pursuit-owned dose and progression.", days: [6], build: () => ["chest_back", "shoulders_arms", "legs_day", "chest_back", "shoulders_arms", "legs_day"] },
    five_three_one: { name: "Main-Lift Waves", blurb: "Four days, each built around one main barbell lift (Press · Deadlift · Bench · Squat) plus targeted accessories, loaded off a training max.", days: [4], minSession: "s60", maxSession: "s120", build: () => ["t531_press", "t531_deadlift", "t531_bench", "t531_squat"] },
    five31_beginner: { minBarbells: 2, name: "Main-Lift Waves · Novice", blurb: "Three days a week, two main lifts each — fixed-rep top sets plus a same-load back-off block — so every lift is trained twice a week.", days: [3], minSession: "s60", maxSession: "s90", build: (n) => ["b531_a", "b531_b", "b531_a"] },
    strength_fb: { name: "Strength Full Body", blurb: "Squat, press and pull every session — the classic barbell linear-progression template for building base strength.", days: [3], build: () => ["full_a", "full_b", "full_c"] },
    academy_prep: { name: "Academy Prep", blurb: "Builds the strength base for the law-enforcement academy fitness test — pressing endurance for push-ups, a trained trunk for sit-ups, and posterior-chain/leg work to drive sprints and the 1.5-mile run. Pair with Pursuit Rated for the running side.", days: [3, 4], build: (d) => { const seq = ["acad_a", "acad_b", "acad_c"]; return Array.from({ length: d }, (_, i) => seq[i % seq.length]); } },
    texas: { name: 
```

## UI volume 7 @ 283203

```js
ch · Squat) plus targeted accessories, loaded off a training max.", days: [4], minSession: "s60", maxSession: "s120", build: () => ["t531_press", "t531_deadlift", "t531_bench", "t531_squat"] },
    five31_beginner: { minBarbells: 2, name: "Main-Lift Waves · Novice", blurb: "Three days a week, two main lifts each — fixed-rep top sets plus a same-load back-off block — so every lift is trained twice a week.", days: [3], minSession: "s60", maxSession: "s90", build: (n) => ["b531_a", "b531_b", "b531_a"] },
    strength_fb: { name: "Strength Full Body", blurb: "Squat, press and pull every session — the classic barbell linear-progression template for building base strength.", days: [3], build: () => ["full_a", "full_b", "full_c"] },
    academy_prep: { name: "Academy Prep", blurb: "Builds the strength base for the law-enforcement academy fitness test — pressing endurance for push-ups, a trained trunk for sit-ups, and posterior-chain/leg work to drive sprints and the 1.5-mile run. Pair with Pursuit Rated for the running side.", days: [3, 4], build: (d) => { const seq = ["acad_a", "acad_b", "acad_c"]; return Array.from({ length: d }, (_, i) => seq[i % seq.length]); } },
    texas: { name: "Volume / Recovery / Intensity", blurb: "Three-day strength undulation: a higher-volume session, a deliberately lower-fatigue session, then an intensity-focused session. The plan adapts the exact sets, reps and loading.", days: [3], minSession: "s60", maxSession: "s120", build: () => ["tx_volume", "tx_recovery", "tx_intensity"] },
    gzclp: { minBarbells: 2, name: "Tiered Linear Progression", blurb: "Four-day tiered strength structure with a heavy primary lift, secondary volume work and accessories; The plan adapts the exact prescription.", days: [4], minSession: "s60", maxSession: "s120", build: () => ["gz_a1", "gz_b1", "gz_a2", "gz_b2"] },
    rippler: { minBarbells: 2, name: "Tiered Wave", blurb: "Four-day tiered strength structure with changing intensity emphasis plus secondary volume and accessories; The plan adapts the exact wave.", days: [4], minSession: "s60", maxSession: "s120", build: () => ["gz_a1", "gz_b1", "gz_a2", "gz_b2"] },
    jt: { name: "Tiered Powerbuilding", blurb: "Four-day tiered powerbuilding structure balancing heavy primary work with higher-volume secondary and accessory work; The plan adapts the exact prescription.", days: [4], minSession: "s60", maxSession: "s120", build: () => ["gz_a1", "gz_b1", "gz_a2", "gz_b2"] },
    // APPEND-ONLY BOUNDARY: CODE_SPLITS = Object.keys(SPLITS) is the split index inside every program
    // code ever issued. New splits go BELOW this line; nothing above it may be reordered or removed.
    glute_focus: { name: "Glutes & Lower Body", blurb: "Hip-dominant training — a hinge or thrust anchors each lower day with direct abduction work, balanced by a pull-led upper day, and a dedicated trunk day once you're training five times a week.", days: [3, 4, 5], build: (d) => { const seq = ["glute_a", "upper_tone", "glute_b
```

## UI volume 8 @ 283283

```js
inSession: "s60", maxSession: "s120", build: () => ["t531_press", "t531_deadlift", "t531_bench", "t531_squat"] },
    five31_beginner: { minBarbells: 2, name: "Main-Lift Waves · Novice", blurb: "Three days a week, two main lifts each — fixed-rep top sets plus a same-load back-off block — so every lift is trained twice a week.", days: [3], minSession: "s60", maxSession: "s90", build: (n) => ["b531_a", "b531_b", "b531_a"] },
    strength_fb: { name: "Strength Full Body", blurb: "Squat, press and pull every session — the classic barbell linear-progression template for building base strength.", days: [3], build: () => ["full_a", "full_b", "full_c"] },
    academy_prep: { name: "Academy Prep", blurb: "Builds the strength base for the law-enforcement academy fitness test — pressing endurance for push-ups, a trained trunk for sit-ups, and posterior-chain/leg work to drive sprints and the 1.5-mile run. Pair with Pursuit Rated for the running side.", days: [3, 4], build: (d) => { const seq = ["acad_a", "acad_b", "acad_c"]; return Array.from({ length: d }, (_, i) => seq[i % seq.length]); } },
    texas: { name: "Volume / Recovery / Intensity", blurb: "Three-day strength undulation: a higher-volume session, a deliberately lower-fatigue session, then an intensity-focused session. The plan adapts the exact sets, reps and loading.", days: [3], minSession: "s60", maxSession: "s120", build: () => ["tx_volume", "tx_recovery", "tx_intensity"] },
    gzclp: { minBarbells: 2, name: "Tiered Linear Progression", blurb: "Four-day tiered strength structure with a heavy primary lift, secondary volume work and accessories; The plan adapts the exact prescription.", days: [4], minSession: "s60", maxSession: "s120", build: () => ["gz_a1", "gz_b1", "gz_a2", "gz_b2"] },
    rippler: { minBarbells: 2, name: "Tiered Wave", blurb: "Four-day tiered strength structure with changing intensity emphasis plus secondary volume and accessories; The plan adapts the exact wave.", days: [4], minSession: "s60", maxSession: "s120", build: () => ["gz_a1", "gz_b1", "gz_a2", "gz_b2"] },
    jt: { name: "Tiered Powerbuilding", blurb: "Four-day tiered powerbuilding structure balancing heavy primary work with higher-volume secondary and accessory work; The plan adapts the exact prescription.", days: [4], minSession: "s60", maxSession: "s120", build: () => ["gz_a1", "gz_b1", "gz_a2", "gz_b2"] },
    // APPEND-ONLY BOUNDARY: CODE_SPLITS = Object.keys(SPLITS) is the split index inside every program
    // code ever issued. New splits go BELOW this line; nothing above it may be reordered or removed.
    glute_focus: { name: "Glutes & Lower Body", blurb: "Hip-dominant training — a hinge or thrust anchors each lower day with direct abduction work, balanced by a pull-led upper day, and a dedicated trunk day once you're training five times a week.", days: [3, 4, 5], build: (d) => { const seq = ["glute_a", "upper_tone", "glute_b", "upper_a", "core_abs"]; return Array.from({ length: d }, (_, i) => seq[i % se
```

## UI volume 9 @ 283492

```js
eek, two main lifts each — fixed-rep top sets plus a same-load back-off block — so every lift is trained twice a week.", days: [3], minSession: "s60", maxSession: "s90", build: (n) => ["b531_a", "b531_b", "b531_a"] },
    strength_fb: { name: "Strength Full Body", blurb: "Squat, press and pull every session — the classic barbell linear-progression template for building base strength.", days: [3], build: () => ["full_a", "full_b", "full_c"] },
    academy_prep: { name: "Academy Prep", blurb: "Builds the strength base for the law-enforcement academy fitness test — pressing endurance for push-ups, a trained trunk for sit-ups, and posterior-chain/leg work to drive sprints and the 1.5-mile run. Pair with Pursuit Rated for the running side.", days: [3, 4], build: (d) => { const seq = ["acad_a", "acad_b", "acad_c"]; return Array.from({ length: d }, (_, i) => seq[i % seq.length]); } },
    texas: { name: "Volume / Recovery / Intensity", blurb: "Three-day strength undulation: a higher-volume session, a deliberately lower-fatigue session, then an intensity-focused session. The plan adapts the exact sets, reps and loading.", days: [3], minSession: "s60", maxSession: "s120", build: () => ["tx_volume", "tx_recovery", "tx_intensity"] },
    gzclp: { minBarbells: 2, name: "Tiered Linear Progression", blurb: "Four-day tiered strength structure with a heavy primary lift, secondary volume work and accessories; The plan adapts the exact prescription.", days: [4], minSession: "s60", maxSession: "s120", build: () => ["gz_a1", "gz_b1", "gz_a2", "gz_b2"] },
    rippler: { minBarbells: 2, name: "Tiered Wave", blurb: "Four-day tiered strength structure with changing intensity emphasis plus secondary volume and accessories; The plan adapts the exact wave.", days: [4], minSession: "s60", maxSession: "s120", build: () => ["gz_a1", "gz_b1", "gz_a2", "gz_b2"] },
    jt: { name: "Tiered Powerbuilding", blurb: "Four-day tiered powerbuilding structure balancing heavy primary work with higher-volume secondary and accessory work; The plan adapts the exact prescription.", days: [4], minSession: "s60", maxSession: "s120", build: () => ["gz_a1", "gz_b1", "gz_a2", "gz_b2"] },
    // APPEND-ONLY BOUNDARY: CODE_SPLITS = Object.keys(SPLITS) is the split index inside every program
    // code ever issued. New splits go BELOW this line; nothing above it may be reordered or removed.
    glute_focus: { name: "Glutes & Lower Body", blurb: "Hip-dominant training — a hinge or thrust anchors each lower day with direct abduction work, balanced by a pull-led upper day, and a dedicated trunk day once you're training five times a week.", days: [3, 4, 5], build: (d) => { const seq = ["glute_a", "upper_tone", "glute_b", "upper_a", "core_abs"]; return Array.from({ length: d }, (_, i) => seq[i % seq.length]); } },
    /* APPENDED below the boundary, so every program code ever issued keeps its meaning: this takes the
       next free split index rather than shifting any existing one. */
    full_body_pat
```

## UI volume 10 @ 283679

```js
531_a", "b531_b", "b531_a"] },
    strength_fb: { name: "Strength Full Body", blurb: "Squat, press and pull every session — the classic barbell linear-progression template for building base strength.", days: [3], build: () => ["full_a", "full_b", "full_c"] },
    academy_prep: { name: "Academy Prep", blurb: "Builds the strength base for the law-enforcement academy fitness test — pressing endurance for push-ups, a trained trunk for sit-ups, and posterior-chain/leg work to drive sprints and the 1.5-mile run. Pair with Pursuit Rated for the running side.", days: [3, 4], build: (d) => { const seq = ["acad_a", "acad_b", "acad_c"]; return Array.from({ length: d }, (_, i) => seq[i % seq.length]); } },
    texas: { name: "Volume / Recovery / Intensity", blurb: "Three-day strength undulation: a higher-volume session, a deliberately lower-fatigue session, then an intensity-focused session. The plan adapts the exact sets, reps and loading.", days: [3], minSession: "s60", maxSession: "s120", build: () => ["tx_volume", "tx_recovery", "tx_intensity"] },
    gzclp: { minBarbells: 2, name: "Tiered Linear Progression", blurb: "Four-day tiered strength structure with a heavy primary lift, secondary volume work and accessories; The plan adapts the exact prescription.", days: [4], minSession: "s60", maxSession: "s120", build: () => ["gz_a1", "gz_b1", "gz_a2", "gz_b2"] },
    rippler: { minBarbells: 2, name: "Tiered Wave", blurb: "Four-day tiered strength structure with changing intensity emphasis plus secondary volume and accessories; The plan adapts the exact wave.", days: [4], minSession: "s60", maxSession: "s120", build: () => ["gz_a1", "gz_b1", "gz_a2", "gz_b2"] },
    jt: { name: "Tiered Powerbuilding", blurb: "Four-day tiered powerbuilding structure balancing heavy primary work with higher-volume secondary and accessory work; The plan adapts the exact prescription.", days: [4], minSession: "s60", maxSession: "s120", build: () => ["gz_a1", "gz_b1", "gz_a2", "gz_b2"] },
    // APPEND-ONLY BOUNDARY: CODE_SPLITS = Object.keys(SPLITS) is the split index inside every program
    // code ever issued. New splits go BELOW this line; nothing above it may be reordered or removed.
    glute_focus: { name: "Glutes & Lower Body", blurb: "Hip-dominant training — a hinge or thrust anchors each lower day with direct abduction work, balanced by a pull-led upper day, and a dedicated trunk day once you're training five times a week.", days: [3, 4, 5], build: (d) => { const seq = ["glute_a", "upper_tone", "glute_b", "upper_a", "core_abs"]; return Array.from({ length: d }, (_, i) => seq[i % seq.length]); } },
    /* APPENDED below the boundary, so every program code ever issued keeps its meaning: this takes the
       next free split index rather than shifting any existing one. */
    full_body_patterns: { name: "Full Body \u00B7 Pattern Rotation", blurb: "Full-body days built as pattern PAIRS rather than one anchor lift \u2014 squat with horizontal push/pull, hinge with vertical, 
```

## UI volume 11 @ 283996

```js
the strength base for the law-enforcement academy fitness test — pressing endurance for push-ups, a trained trunk for sit-ups, and posterior-chain/leg work to drive sprints and the 1.5-mile run. Pair with Pursuit Rated for the running side.", days: [3, 4], build: (d) => { const seq = ["acad_a", "acad_b", "acad_c"]; return Array.from({ length: d }, (_, i) => seq[i % seq.length]); } },
    texas: { name: "Volume / Recovery / Intensity", blurb: "Three-day strength undulation: a higher-volume session, a deliberately lower-fatigue session, then an intensity-focused session. The plan adapts the exact sets, reps and loading.", days: [3], minSession: "s60", maxSession: "s120", build: () => ["tx_volume", "tx_recovery", "tx_intensity"] },
    gzclp: { minBarbells: 2, name: "Tiered Linear Progression", blurb: "Four-day tiered strength structure with a heavy primary lift, secondary volume work and accessories; The plan adapts the exact prescription.", days: [4], minSession: "s60", maxSession: "s120", build: () => ["gz_a1", "gz_b1", "gz_a2", "gz_b2"] },
    rippler: { minBarbells: 2, name: "Tiered Wave", blurb: "Four-day tiered strength structure with changing intensity emphasis plus secondary volume and accessories; The plan adapts the exact wave.", days: [4], minSession: "s60", maxSession: "s120", build: () => ["gz_a1", "gz_b1", "gz_a2", "gz_b2"] },
    jt: { name: "Tiered Powerbuilding", blurb: "Four-day tiered powerbuilding structure balancing heavy primary work with higher-volume secondary and accessory work; The plan adapts the exact prescription.", days: [4], minSession: "s60", maxSession: "s120", build: () => ["gz_a1", "gz_b1", "gz_a2", "gz_b2"] },
    // APPEND-ONLY BOUNDARY: CODE_SPLITS = Object.keys(SPLITS) is the split index inside every program
    // code ever issued. New splits go BELOW this line; nothing above it may be reordered or removed.
    glute_focus: { name: "Glutes & Lower Body", blurb: "Hip-dominant training — a hinge or thrust anchors each lower day with direct abduction work, balanced by a pull-led upper day, and a dedicated trunk day once you're training five times a week.", days: [3, 4, 5], build: (d) => { const seq = ["glute_a", "upper_tone", "glute_b", "upper_a", "core_abs"]; return Array.from({ length: d }, (_, i) => seq[i % seq.length]); } },
    /* APPENDED below the boundary, so every program code ever issued keeps its meaning: this takes the
       next free split index rather than shifting any existing one. */
    full_body_patterns: { name: "Full Body \u00B7 Pattern Rotation", blurb: "Full-body days built as pattern PAIRS rather than one anchor lift \u2014 squat with horizontal push/pull, hinge with vertical, then unilateral, posterior chain and a pump day. Spreads pressing across the week, so the front delt takes less of it.", days: [2, 3, 4, 5, 6], build: (d) => { const seq = ["fb2_a", "fb2_b", "fb2_c", "fb2_d", "fb2_e"]; return Array.from({ length: d }, (_, i) => seq[i % seq.length]); } },
    torso_limbs: { name: "To
```

## UI volume 12 @ 284286

```js
d_a", "acad_b", "acad_c"]; return Array.from({ length: d }, (_, i) => seq[i % seq.length]); } },
    texas: { name: "Volume / Recovery / Intensity", blurb: "Three-day strength undulation: a higher-volume session, a deliberately lower-fatigue session, then an intensity-focused session. The plan adapts the exact sets, reps and loading.", days: [3], minSession: "s60", maxSession: "s120", build: () => ["tx_volume", "tx_recovery", "tx_intensity"] },
    gzclp: { minBarbells: 2, name: "Tiered Linear Progression", blurb: "Four-day tiered strength structure with a heavy primary lift, secondary volume work and accessories; The plan adapts the exact prescription.", days: [4], minSession: "s60", maxSession: "s120", build: () => ["gz_a1", "gz_b1", "gz_a2", "gz_b2"] },
    rippler: { minBarbells: 2, name: "Tiered Wave", blurb: "Four-day tiered strength structure with changing intensity emphasis plus secondary volume and accessories; The plan adapts the exact wave.", days: [4], minSession: "s60", maxSession: "s120", build: () => ["gz_a1", "gz_b1", "gz_a2", "gz_b2"] },
    jt: { name: "Tiered Powerbuilding", blurb: "Four-day tiered powerbuilding structure balancing heavy primary work with higher-volume secondary and accessory work; The plan adapts the exact prescription.", days: [4], minSession: "s60", maxSession: "s120", build: () => ["gz_a1", "gz_b1", "gz_a2", "gz_b2"] },
    // APPEND-ONLY BOUNDARY: CODE_SPLITS = Object.keys(SPLITS) is the split index inside every program
    // code ever issued. New splits go BELOW this line; nothing above it may be reordered or removed.
    glute_focus: { name: "Glutes & Lower Body", blurb: "Hip-dominant training — a hinge or thrust anchors each lower day with direct abduction work, balanced by a pull-led upper day, and a dedicated trunk day once you're training five times a week.", days: [3, 4, 5], build: (d) => { const seq = ["glute_a", "upper_tone", "glute_b", "upper_a", "core_abs"]; return Array.from({ length: d }, (_, i) => seq[i % seq.length]); } },
    /* APPENDED below the boundary, so every program code ever issued keeps its meaning: this takes the
       next free split index rather than shifting any existing one. */
    full_body_patterns: { name: "Full Body \u00B7 Pattern Rotation", blurb: "Full-body days built as pattern PAIRS rather than one anchor lift \u2014 squat with horizontal push/pull, hinge with vertical, then unilateral, posterior chain and a pump day. Spreads pressing across the week, so the front delt takes less of it.", days: [2, 3, 4, 5, 6], build: (d) => { const seq = ["fb2_a", "fb2_b", "fb2_c", "fb2_d", "fb2_e"]; return Array.from({ length: d }, (_, i) => seq[i % seq.length]); } },
    torso_limbs: { name: "Torso / Limbs", blurb: "Torso days (chest, back and shoulders) alternate with Limbs days (legs and arms). Separating heavy pressing and pulling from direct arm work gives the arms a session where they are not already fatigued.", days: [4, 5], build: (d) => { const seq = ["torso_a", "limbs_a"
```

## UI weeklyRecap( 1 @ 1074868

```js
 sets: 0 };
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
    return { count: thisW.length, sets: sets(thisW), vol: Math.round(vol(thisW)), prevCount: prevW.length, prevSets: sets(prevW), prevVol: Math.round(vol(prevW)), to
```

## UI weeklyRecap( 2 @ 2617688

```js
inTop: 4 }, children: [_jsx("span", { style: { width: 5, height: 5, borderRadius: 999, background: C.warn || "#e0a31e", flexShrink: 0, marginTop: 6 } }), r] }, i))) })), canDeload && (_jsxs("button", { onClick: onDeload, className: "pressable", style: { width: "100%", marginTop: 4, padding: "12px", borderRadius: 12, border: "none", background: C.warn || "#e0a31e", color: C.warnText, fontSize: 15, fontWeight: 700, cursor: "pointer", display: "flex", alignItems: "center", justifyContent: "center", gap: 6 }, children: [_jsx(Battery, { size: 15 }), " Train this week's deload now"] }))] }));
}
// Module-level so its identity is stable across renders — defining it inside WeeklyRecapCard
// would force React to unmount/remount every <Arrow> instance on each parent re-render instead
// of just updating props (the same anti-pattern documented above for SettingsRow).
const RecapArrow = ({ d }) => d == null ? null : _jsxs("span", { style: { fontSize: 11, fontWeight: 600, color: d > 0 ? C.accent : d < 0 ? C.warn : C.muted, marginLeft: 4 }, children: [d > 0 ? "▲" : d < 0 ? "▼" : "", d !== 0 ? `${Math.abs(d)}%` : "—"] });
function WeeklyRecapCard({ history, unit }) {
    const r = useMemo(() => weeklyRecap(history), [history]);
    if (!r.count)
        return null;
    const delta = (cur, prev) => { if (!prev)
        return null; const d = Math.round((cur - prev) / prev * 100); return d; };
    const vd = delta(r.vol, r.prevVol), sd = delta(r.sets, r.prevSets);
    return (_jsxs("div", { style: { background: C.card, borderRadius: 16, padding: 16, marginBottom: 20 }, children: [_jsxs("div", { style: { display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 12 }, children: [_jsxs("div", { style: { display: "flex", alignItems: "center", gap: 8 }, children: [_jsx(BarChart3, { size: 16, color: C.accentInk }), _jsx("span", { style: { fontSize: 15, fontWeight: 700 }, children: "This week" })] }), r.prs > 0 && _jsxs("span", { style: { display: "flex", alignItems: "center", gap: 4, fontSize: 13, fontWeight: 700, color: C.accentInk }, children: [_jsx(Trophy, { size: 13 }), " ", r.prs, " PR", r.prs === 1 ? "" : "s"] })] }), _jsx("div", { style: { display: "flex", gap: 8 }, children: [["Workouts", r.count, null], ["Sets", r.sets, sd], [`Volume${unit ? ` (${unit})` : ""}`, `${(r.vol / 1000).toFixed(r.vol >= 10000 ? 0 : 1)}k`, vd]].map(([k, v, d]) => (_jsxs("div", { style: { flex: 1, background: C.bg2, borderRadius: 12, padding: "11px 6px", textAlign: "center" }, children: [_jsxs("div", { className: "mono", style: { fontSize: 18, fontWeight: 700, color: C.text, lineHeight: 1 }, children: [v, _jsx(RecapArrow, { d: d })] }), _jsx("div", { style: { fontSize: 11, color: C.muted, marginTop: 4, fontWeight: 600 }, children: k })] }, k))) }), r.topMuscle && _jsxs("div", { style: { fontSize: 13, color: C.muted, marginTop: 12, paddingTop: 12, borderTop: `1px solid ${C.borderSoft}` }, children: [_jsx("span", { style: { color: C.accentInk, fontWeight: 60
```
