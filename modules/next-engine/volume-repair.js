import { createExerciseMap } from './exercise-db.js';
import { getNextShellCell, nextExerciseIdForShellExercise, resolveLegacyExercise } from './app-shell-adapter.js';
import { normalizeRequest, createMusclePrescriptions } from './prescription.js';
import { PUBLIC_MEV_REGIONS, PUBLIC_REGION_MUSCLE, publicMevContractApplies, publicMevForExperience, publicMevRequired, publicMevInternalSafetyCeiling, publicRegionContribution, publicMevLedger } from './public-mev.js';
import { createTrainingSetEvents } from './events.js';
import { deriveMuscleLedger } from './ledgers.js';
import { deriveArmCoverage } from './arm-coverage.js';
import { createEngineContext } from './engine-context.js';
import { auditProgram } from './arbiter.js';
import { buildProgramExplainability } from './explainability.js';
import { estimateSessionMinutes, repsForPhase, rirForPhase, restForExercise, progressionForExercise, progressionStyleForExercise } from './realizer.js';
import { prescriptionForSimulationWeek } from './simulation.js';
import { phasePolicyFor } from './phase-policy.js';
import { avoidableExerciseOverlap } from './exercise-economy.js';
import { INTENT_MUSCLES } from './topology.js';

const EPS = .001;
const STRENGTH = new Set(['primary_strength', 'secondary_strength', 'strength_support']);
const LABELS = { upper_back: 'Upper back', lats: 'Lats', side_delts: 'Side delts', rear_delts: 'Rear delts', core: 'Core' };
const label = id => LABELS[id] || id.charAt(0).toUpperCase() + id.slice(1).replaceAll('_', ' ');
const fmt = n => Number(n.toFixed(1)).toString();
const workWeeks = p => Math.max(1, Number(p.config?.weeks ?? p.weeks) || 4);
const phaseOf = p => p.nextEngine.program.phase;
const requestOf = p => normalizeRequest(p.nextEngine.request);
const keyOf = (d, slot) => `${d.id}:${slot}`;
const sessionForDay = (p, d, index) => p.nextEngine.program.sessions.find((s, i) => d.id === `next-${i + 1}-${s.day}`) ?? p.nextEngine.program.sessions[index];

// Read the live shell roster and the exact cells used by Program/Home/Workout. Neither the
// immutable generation snapshot nor legacy slotBias is a substitute for a current prescription.
export function captureShellVolumeSnapshot(program, week, legacyExercises = [], dayId = null) {
    if (!program?.nextEngine?.program || !program.nextEngine.request)
        return null;
    const request = requestOf(program);
    const exerciseMap = createExerciseMap(request.customExercises);
    const legacyMap = new Map(legacyExercises.map(e => [e.id, e]));
    const sessions = [];
    let missing = false;
    program.days.forEach((day, di) => {
        if (dayId && day.id !== dayId) return;
        const source = sessionForDay(program, day, di);
        if (!source) { missing = true; return; }
        const exercises = day.exercises.map((id, slot) => {
            const key = keyOf(day, slot);
            const meta = program.overrides?.[key] ?? {};
            const exerciseId = meta.nextExerciseId ?? nextExerciseIdForShellExercise(legacyMap.get(id) ?? {});
            const def = exerciseMap.get(exerciseId);
            const cell = getNextShellCell(program, day, slot, week);
            if (!def || !cell || !Number.isFinite(cell.sets)) { missing = true; return null; }
            const original = source.exercises.find(e => e.exerciseId === exerciseId) ?? source.exercises[slot];
            return { ...original, exerciseId, name: def.name, sets: cell.sets, role: cell.role,
                prescription: { ...original?.prescription, restSeconds: Number(cell.rest) || 90 },
                advancedTechnique: cell.tech ? original?.advancedTechnique : undefined,
                shellKey: key, shellId: id, shellSlot: slot };
        }).filter(Boolean);
        sessions.push({ ...source, shellDayId: day.id, exercises, estimatedMinutes: estimateSessionMinutes(exercises) });
    });
    const events = createTrainingSetEvents(sessions, request.customExercises);
    const muscleLedger = deriveMuscleLedger(events);
    const regions = publicMevLedger(sessions, exerciseMap);
    const volume = Object.fromEntries(Object.entries(muscleLedger).map(([m, v]) => [m === 'core' ? 'abs' : m, v.fractionalSets]));
    delete volume.back;
    volume.lats = regions.lats;
    volume.upper_back = regions.upper_back;
    volume.shoulders = volume.front_delts + volume.side_delts + volume.rear_delts;
    delete volume.front_delts; delete volume.side_delts; delete volume.rear_delts;
    const subVolume = Object.fromEntries(['front_delts', 'side_delts', 'rear_delts'].map(m => [m, muscleLedger[m].fractionalSets]));
    return { sessions, muscleLedger, regions, volume, subVolume, armCoverage: deriveArmCoverage(sessions, exerciseMap), missing };
}

// Normal floors come from the same experience/phase/priority/capacity prescriptions as the
// allocator and arbiter. The explicit 5-day accumulation contract retains its public region MEVs.
export function shellVolumeTargets(program) {
    const request = requestOf(program), phase = phaseOf(program);
    const prescriptions = createMusclePrescriptions(request, phase);
    const contract = publicMevContractApplies(request, phase);
    return PUBLIC_MEV_REGIONS.map(region => {
        const p = prescriptions.find(p => p.muscle === PUBLIC_REGION_MUSCLE[region]);
        const splitBack = p.muscle === 'back' ? .5 : 1;
        const mev = publicMevRequired(request, region)
            ? contract ? Math.max(p.minimum * splitBack, publicMevForExperience(region, request.athlete.experience)) : p.minimum * splitBack
            : 0;
        const mav = Math.max(mev, p.preferred * splitBack);
        const mrv = Math.max(mav, publicMevInternalSafetyCeiling(request, phase, p.muscle, p.upper) * 1.2);
        return { part: region === 'core' ? 'abs' : region, region, muscle: p.muscle, label: label(region), mev, mav, mrv, priority: p.priority };
    });
}

export function shellDayMuscleBreakdown(program, day, week, legacyExercises = []) {
    const snapshot = captureShellVolumeSnapshot(program, week, legacyExercises, day.id);
    if (!snapshot) return null;
    const exerciseMap = createExerciseMap(requestOf(program).customExercises), map = {};
    for (const session of snapshot.sessions) for (const e of session.exercises) {
        const def = exerciseMap.get(e.exerciseId), credits = {};
        for (const [muscle, c] of Object.entries(def.muscles)) {
            if (muscle === 'back') continue;
            const part = ['front_delts', 'side_delts', 'rear_delts'].includes(muscle) ? 'shoulders' : muscle === 'core' ? 'abs' : muscle;
            credits[part] = (credits[part] || 0) + c.credit;
        }
        credits.lats = publicRegionContribution(def, 'lats');
        credits.upper_back = publicRegionContribution(def, 'upper_back');
        for (const [part, factor] of Object.entries(credits)) {
            if (factor <= 0) continue;
            const row = map[part] ?? (map[part] = { part, sets: 0, from: [] });
            row.sets += e.sets * factor;
            row.from.push({ id: e.shellId, name: e.name, slot: e.shellSlot, sets: e.sets, contrib: e.sets * factor, direct: factor >= .999, factor });
        }
    }
    return Object.values(map).filter(r => r.sets >= .5).map(r => ({ ...r, from: r.from.sort((a, b) => b.contrib - a.contrib) })).sort((a, b) => b.sets - a.sets);
}

export function auditShellVolume(program, legacyExercises = []) {
    if (!program?.nextEngine?.program || !program.nextEngine.request)
        return { issues: [], volBias: {}, missing: true, targets: [] };
    const targets = shellVolumeTargets(program);
    const weeks = Array.from({ length: workWeeks(program) }, (_, i) => captureShellVolumeSnapshot(program, i + 1, legacyExercises));
    const issues = [];
    for (const target of targets) {
        const values = weeks.map(s => s.regions[target.region]);
        const lo = Math.min(...values), hi = Math.max(...values);
        const status = lo + EPS < target.mev ? 'under' : hi > target.mrv + EPS ? 'over' : null;
        if (!status) continue;
        const v = status === 'under' ? lo : hi;
        const need = status === 'under' ? target.mev - v : v - target.mrv;
        issues.push({ ...target, v, status, need, fits: false,
            week: values.findIndex(n => Math.abs(n - v) < EPS) + 1,
            fix: status === 'under' ? 'Add useful sets to existing movements first; rebalance other work if the session is full.' : 'Reduce accessory sets while protecting the rest of the plan.' });
    }
    issues.sort((a, b) => (a.status === 'under' ? 0 : 1) - (b.status === 'under' ? 0 : 1) || b.need - a.need);
    return { issues, volBias: Object.fromEntries(issues.map(i => [i.part, i.status === 'under' ? Math.ceil(i.need) : -Math.ceil(i.need)])),
        targets, weeks, missing: weeks.some(w => w.missing) };
}

function baseSessions(program, legacyExercises) {
    const live = captureShellVolumeSnapshot(program, 1, legacyExercises);
    return live.sessions.map((s, di) => {
        const original = sessionForDay(program, program.days[di], di);
        const exercises = s.exercises.map(e => ({ ...e, sets: Number(program.overrides?.[e.shellKey]?.sets) ||
            original.exercises.find(x => x.exerciseId === e.exerciseId)?.sets || e.sets }));
        return { ...s, exercises, estimatedMinutes: estimateSessionMinutes(exercises) };
    });
}

function rebuild(program, changes, legacyExercises, addition = null, options = {}) {
    const preserveRoster = !!options.preserveRoster;
    const request = requestOf(program), sessions = baseSessions(program, legacyExercises);
    const next = { ...program, days: program.days.map(d => ({ ...d, exercises: [...d.exercises] })),
        overrides: { ...program.overrides }, nextWeekPrescriptions: { ...program.nextWeekPrescriptions }, nextEngine: { ...program.nextEngine } };
    const changed = new Set();
    for (const change of changes) {
        const s = sessions.find(s => s.shellDayId === change.dayId), e = s?.exercises[change.slot];
        if (!e) return null;
        // A locked roster can already be at the one-base-set floor while the session-level weekly
        // allocator rounds that slot up to two sets. In that case the safe repair surface is the
        // exact generated week cell, not a nonexistent zero-set base prescription.
        if (Number.isInteger(change.week) && change.week > 0) {
            const key = e.shellKey, cells = next.nextWeekPrescriptions?.[key], prior = cells?.[change.week];
            if (!prior || !Number.isFinite(prior.sets) || prior.sets + change.delta < 1) return null;
            changed.add(key);
            continue;
        }
        if (change.remove) {
            if (preserveRoster) return null;
            const di = next.days.findIndex(d => d.id === change.dayId), day = next.days[di];
            day.exercises.splice(change.slot, 1);
            day.primaryIndex = Math.max(0, day.primaryIndex - (change.slot < day.primaryIndex ? 1 : 0));
            for (const field of ['overrides', 'nextWeekPrescriptions', 'progStyle', 'ss', 'slotBias', 'autoBias', 'rounds', 'weekOff', 'pairs']) {
                if (!next[field]) continue;
                const map = {};
                for (const [key, value] of Object.entries(next[field])) {
                    const prefix = `${day.id}:`;
                    if (!key.startsWith(prefix)) { map[key] = value; continue; }
                    const match = key.slice(prefix.length).match(/^(\d+)(.*)$/);
                    if (!match) { map[key] = value; continue; }
                    const index = Number(match[1]);
                    if (index === change.slot || (field === 'ss' && index === change.slot - 1)) continue;
                    map[`${prefix}${index > change.slot ? index - 1 : index}${match[2]}`] = value;
                }
                next[field] = map;
            }
            s.exercises.splice(change.slot, 1);
            s.exercises.forEach((ex, slot) => {
                ex.shellSlot = slot; ex.shellKey = keyOf(day, slot); changed.add(ex.shellKey);
                if (ex.supersetGroup && ex.supersetGroup === e.supersetGroup) ex.supersetGroup = undefined;
            });
            continue;
        }
        e.sets += change.delta;
        if (e.sets < (preserveRoster ? 1 : 2)) return null; // locked cycles may retain a one-set accessory rather than delete the roster entry
        const key = e.shellKey;
        changed.add(key);
        if (next.overrides[key]?.sets != null)
            next.overrides[key] = { ...next.overrides[key], sets: e.sets };
    }
    if (addition) {
        if (preserveRoster) return null;
        const di = next.days.findIndex(d => d.id === addition.dayId), day = next.days[di], s = sessions[di];
        const map = createExerciseMap(request.customExercises);
        const firstIsolation = s.exercises.findIndex(e => !STRENGTH.has(e.role) && !map.get(e.exerciseId)?.flags.compound);
        const slot = firstIsolation < 0 ? day.exercises.length : firstIsolation, key = keyOf(day, slot);
        day.exercises.splice(slot, 0, addition.legacy.id);
        if (slot <= day.primaryIndex) day.primaryIndex++;
        for (const field of ['overrides', 'nextWeekPrescriptions', 'progStyle', 'ss', 'slotBias', 'autoBias', 'rounds', 'weekOff', 'pairs']) {
            if (!next[field]) continue;
            const shifted = {};
            for (const [oldKey, value] of Object.entries(next[field])) {
                const prefix = `${day.id}:`, match = oldKey.startsWith(prefix) ? oldKey.slice(prefix.length).match(/^(\d+)(.*)$/) : null;
                if (!match) { shifted[oldKey] = value; continue; }
                const index = Number(match[1]);
                if (field === 'ss' && index === slot - 1) continue;
                shifted[`${prefix}${index >= slot ? index + 1 : index}${match[2]}`] = value;
            }
            next[field] = shifted;
        }
        const e = { ...addition.exercise, shellKey: key, shellSlot: slot, shellId: addition.legacy.id };
        s.exercises.splice(slot, 0, e);
        s.exercises.forEach((ex, index) => { ex.shellSlot = index; ex.shellKey = keyOf(day, index); changed.add(ex.shellKey); });
        next.overrides[key] = { nextEngine: true, nextExerciseId: e.exerciseId, legacyExerciseId: addition.legacy.id,
            role: e.role, progressionStyle: e.progressionStyle };
        changed.add(key);
        next.nextEngine.progressionPlan = [...(next.nextEngine.progressionPlan ?? []), {
            exerciseId: e.exerciseId, exerciseName: e.name, style: e.progressionStyle, source: 'auto',
            reason: 'This movement fills a weekly volume gap. Build reps within its target range before increasing the load.'
        }];
    }
    for (const session of sessions) {
        session.estimatedMinutes = estimateSessionMinutes(session.exercises);
        if (session.exercises.some(e => changed.has(e.shellKey)))
            session.exercises.forEach(e => changed.add(e.shellKey));
        for (let slot = 0; slot < session.exercises.length; slot++) {
            const e = session.exercises[slot], key = e.shellKey;
            if (!changed.has(key)) continue;
            const cells = { ...next.nextWeekPrescriptions[key] };
            for (let w = 1; w <= workWeeks(next) + (next.config.deload ? 1 : 0); w++) {
                const deload = w > workWeeks(next);
                const generated = prescriptionForSimulationWeek(session, deload ? 'recovery' : phaseOf(next), deload ? 1 : w, deload ? 1 : workWeeks(next)).exercises[slot];
                const prior = cells[w];
                cells[w] = { ...prior, sets: generated.sets,
                    ...(prior ? {} : { reps: generated.prescription.reps.join('-'), rir: generated.prescription.rir.join('-'),
                        rest: generated.prescription.restSeconds, role: generated.role, progressionStyle: generated.progressionStyle, tech: null }) };
            }
            for (const change of changes.filter(change => Number.isInteger(change.week) && change.week > 0 && `${change.dayId}:${change.slot}` === key)) {
                const prior = cells[change.week];
                if (!prior || !Number.isFinite(prior.sets) || prior.sets + change.delta < 1) return null;
                cells[change.week] = { ...prior, sets: prior.sets + change.delta };
            }
            next.nextWeekPrescriptions[key] = cells;
        }
    }
    const events = createTrainingSetEvents(sessions, request.customExercises);
    const engine = { ...program.nextEngine.program, sessions, events, muscleLedger: deriveMuscleLedger(events) };
    engine.audit = auditProgram(engine, request);
    next.nextEngine.program = engine;
    next.nextEngine.audit = engine.audit;
    return next;
}

const auditCounts = audit => {
    const counts = {};
    for (const f of audit.findings ?? []) {
        if (!['critical', 'major'].includes(f.severity)) continue;
        const key = `${f.code}:${f.sessionId ?? ''}:${f.severity}`;
        counts[key] = (counts[key] || 0) + 1;
    }
    return counts;
};
const deficit = a => a.issues.reduce((sum, i) => sum + i.need, 0);

function safeImprovement(before, after, candidate, baselineEngineAudit, changedKeys) {
    if (!candidate || after.missing || deficit(after) >= deficit(before) - EPS) return false;
    for (let w = 0; w < after.weeks.length; w++) {
        const b = before.weeks[w], a = after.weeks[w];
        for (const t of before.targets) {
            const lo = Math.min(t.mev, b.regions[t.region]), hi = Math.max(t.mrv, b.regions[t.region]);
            if (a.regions[t.region] + EPS < lo || a.regions[t.region] > hi + EPS) return false;
        }
        for (let di = 0; di < a.sessions.length; di++) {
            const s = a.sessions[di];
            if (s.estimatedMinutes > Math.max(s.maxMinutes, b.sessions[di].estimatedMinutes)) return false;
            for (const e of s.exercises)
                if (changedKeys.has(e.shellKey) && e.sets > Math.max(5, b.sessions[di].exercises.find(x => x.shellKey === e.shellKey)?.sets ?? 0)) return false;
        }
    }
    const oldCounts = auditCounts(baselineEngineAudit), newCounts = auditCounts(candidate.nextEngine.audit);
    return Object.entries(newCounts).every(([key, n]) => n <= (oldCounts[key] || 0));
}

/** Static-cycle finishing pass. Base repair and exact week-cell repair are deliberately separate:
 * rebuilding a base session regenerates its weekly cells, so mixing both mutation layers can undo a
 * safe weekly trim. This pass runs only after base repair has plateaued and changes generated work-week
 * cells only; exercise identity and the immutable engine program are untouched. */
export function reconcileLockedCycleWeekOverflows(program, legacyExercises = []) {
    let current = program, audit = auditShellVolume(program, legacyExercises), changed = false;
    const context = createEngineContext(requestOf(program));
    const safeCell = (before, after, issue) => {
        if (after.missing || deficit(after) > deficit(before) + EPS) return false;
        const bw = before.weeks[issue.week - 1], aw = after.weeks[issue.week - 1];
        // Tied worst weeks form a plateau: improving week 1 can move the same block-wide maximum
        // to week 2 without changing aggregate deficit yet. Permit that non-worsening intermediate
        // step only when the exact offending week/region itself strictly improves.
        if (!bw || !aw || aw.regions[issue.region] >= bw.regions[issue.region] - EPS) return false;
        for (let wi = 0; wi < after.weeks.length; wi++) {
            const b = before.weeks[wi], a = after.weeks[wi];
            for (const target of before.targets) {
                const lo = Math.min(target.mev, b.regions[target.region]);
                const hi = Math.max(target.mrv, b.regions[target.region]);
                if (a.regions[target.region] + EPS < lo || a.regions[target.region] > hi + EPS) return false;
            }
            for (let di = 0; di < a.sessions.length; di++)
                if (a.sessions[di].estimatedMinutes > Math.max(a.sessions[di].maxMinutes, b.sessions[di].estimatedMinutes)) return false;
        }
        return true;
    };
    for (let guard = 0; guard < 96; guard++) {
        const issue = audit.issues.find(x => x.status === 'over');
        if (!issue) break;
        const week = audit.weeks[issue.week - 1];
        const candidates = week.sessions.flatMap(session => session.exercises.map(exercise => ({ session, exercise,
            def: context.exerciseById(exercise.exerciseId) })))
            .filter(x => x.def && !STRENGTH.has(x.exercise.role) && x.exercise.sets > 1 && publicRegionContribution(x.def, issue.region) > 0)
            .sort((a, b) => (a.def.flags.compound ? 1 : 0) - (b.def.flags.compound ? 1 : 0) ||
                publicRegionContribution(b.def, issue.region) - publicRegionContribution(a.def, issue.region) ||
                b.exercise.sets - a.exercise.sets || a.exercise.shellKey.localeCompare(b.exercise.shellKey));
        let best = null;
        for (const x of candidates) {
            const key = x.exercise.shellKey, cells = current.nextWeekPrescriptions?.[key], prior = cells?.[issue.week];
            if (!prior || !Number.isFinite(prior.sets) || prior.sets <= 1) continue;
            const candidate = { ...current, nextWeekPrescriptions: { ...current.nextWeekPrescriptions,
                [key]: { ...cells, [issue.week]: { ...prior, sets: prior.sets - 1 } } } };
            const after = auditShellVolume(candidate, legacyExercises);
            if (!safeCell(audit, after, issue)) continue;
            const score = deficit(audit) - deficit(after);
            if (!best || score > best.score + EPS) best = { candidate, after, score, key, week: issue.week };
        }
        if (!best) break;
        current = best.candidate;
        audit = best.after;
        changed = true;
    }
    return { program: current, after: audit, changed,
        status: audit.issues.some(x => x.status === 'over') ? (changed ? 'partial' : 'unable') : changed ? 'success' : 'unchanged' };
}

/** Verified transaction: existing set allocation, then reallocation, then one compatible new
 * movement. Every proposal uses the same public region ledger and complete engine safety audit.
 * Failed proposals are discarded; a partial repair is reported as partial, never as success. */
export function repairShellVolume(program, legacyExercises = [], options = {}) {
    const preserveRoster = !!options.preserveRoster;
    const before = auditShellVolume(program, legacyExercises);
    if (before.missing) return { program, status: 'unable', changed: false, before, after: before, message: 'Prescription data is incomplete. Rebuild this plan before fixing its volume.' };
    if (!before.issues.length) return { program, status: 'unchanged', changed: false, before, after: before, message: 'Weekly volume is already in range for this block.' };
    let current = program, audit = before;
    const changes = [];
    const request = requestOf(program), context = createEngineContext(request), phase = phaseOf(program);
    const initialEngineAudit = auditProgram({ ...program.nextEngine.program, sessions: baseSessions(program, legacyExercises),
        muscleLedger: deriveMuscleLedger(createTrainingSetEvents(baseSessions(program, legacyExercises), request.customExercises)) }, request);
    for (let guard = 0; guard < 48 && audit.issues.length; guard++) {
        const sessions = baseSessions(current, legacyExercises);
        const slots = sessions.flatMap(s => s.exercises.map((e, slot) => ({ dayId: s.shellDayId, slot, e, def: context.exerciseById(e.exerciseId), s })));
        let best = null;
        const consider = (ops, addition = null) => {
            const candidate = rebuild(current, ops, legacyExercises, addition, { preserveRoster });
            if (!candidate) return;
            const after = auditShellVolume(candidate, legacyExercises);
            const keys = new Set(ops.map(op => `${op.dayId}:${op.slot}`));
            if (addition) keys.add(`${addition.dayId}:${current.days.find(d => d.id === addition.dayId).exercises.length}`);
            if (!safeImprovement(audit, after, candidate, initialEngineAudit, keys)) return;
            const score = deficit(audit) - deficit(after);
            if (!best || score > best.score + EPS) best = { candidate, after, ops, addition, score };
        };
        const donors = slots.filter(x => !STRENGTH.has(x.e.role) && x.e.sets > 2)
            .sort((a, b) => (a.def?.flags.compound ? 1 : 0) - (b.def?.flags.compound ? 1 : 0) || b.e.sets - a.e.sets);
        const redundant = slots.filter(x => x.def && !STRENGTH.has(x.e.role) && x.s.exercises.length > 3 &&
            avoidableExerciseOverlap(x.def, x.e.role, x.s.exercises.filter(e => e.shellKey !== x.e.shellKey).map(e => ({ def: context.exerciseById(e.exerciseId), role: e.role })).filter(x => x.def)));
        for (const issue of audit.issues) {
            // Locked-cycle strength_support rows can be accessories whose identity must stay in the
            // skeleton (calves are the concrete M202 case). Keep primary/secondary strength anchors
            // fully protected, but allow an over-ceiling support row to shed sets when the exact
            // weekly shell audit and the full engine audit both approve the change.
            const recipients = slots.filter(x => x.def &&
                (!STRENGTH.has(x.e.role) || (preserveRoster && issue.status === 'over' && x.e.role === 'strength_support')) &&
                publicRegionContribution(x.def, issue.region) > 0)
                .sort((a, b) => publicRegionContribution(b.def, issue.region) - publicRegionContribution(a.def, issue.region) || a.e.sets - b.e.sets);
            for (const r of recipients) {
                // At cycle creation there are no user-edited weekly cells to preserve. If a locked
                // isolation is already at one base set but this specific week was rounded up, trim
                // that generated cell directly. The public-region transaction still checks every
                // other region and session clock before accepting it.
                if (preserveRoster && issue.status === 'over' && r.e.role === 'hypertrophy_isolation' && r.e.sets <= 1) {
                    const weekSession = audit.weeks[issue.week - 1]?.sessions.find(session => session.shellDayId === r.dayId);
                    const weekExercise = weekSession?.exercises.find(exercise => exercise.shellKey === r.e.shellKey);
                    if (weekExercise?.sets > 1)
                        consider([{ dayId: r.dayId, slot: r.slot, delta: -1, week: issue.week }]);
                }
                const floor = preserveRoster ? 1 : 2;
                // Work-week progression is rounded from the base prescription. A one-set base change
                // can therefore leave the max week unchanged; search a small bounded decrement atomically
                // rather than falsely concluding that a locked roster cannot be reconciled.
                const deltas = issue.status === 'under' ? [1, ...(r.e.sets <= 3 ? [2] : [])]
                    : [-1, -2, -3].filter(delta => r.e.sets + delta >= floor);
                for (const delta of deltas)
                    consider([{ dayId: r.dayId, slot: r.slot, delta }]);
            }
        }
        // Any legal increase on existing work wins before trades or a new setup are considered.
        if (!best) for (const issue of audit.issues.filter(i => i.status === 'under')) {
            const recipients = slots.filter(x => x.def && !STRENGTH.has(x.e.role) && x.e.sets < 5 && publicRegionContribution(x.def, issue.region) > 0);
            for (const r of recipients) for (const d of donors.filter(d => d.dayId === r.dayId && d.slot !== r.slot)) {
                for (let gain = 1; gain <= Math.min(2, 5 - r.e.sets); gain++)
                    for (let n = 1; n <= Math.min(3, d.e.sets - 2); n++) consider([{ dayId: r.dayId, slot: r.slot, delta: gain }, { dayId: d.dayId, slot: d.slot, delta: -n }]);
            }
            // Consolidate an avoidable extra setup before adding another movement. Full-body
            // identity, dose floors, explicit focus, and all shifted shell keys are re-audited.
            for (const r of recipients) for (const d of redundant.filter(d => d.dayId === r.dayId && d.slot !== r.slot))
                consider([{ dayId: r.dayId, slot: r.slot, delta: 1 }, { dayId: d.dayId, slot: d.slot, remove: true }]);
        }
        if (!best) for (const issue of audit.issues.filter(i => i.status === 'under')) {
            for (const s of sessions) {
                if (!(INTENT_MUSCLES[s.intent] ?? INTENT_MUSCLES.full).includes(issue.muscle)) continue;
                const candidates = context.candidatesForMuscle(issue.muscle).filter(def => publicRegionContribution(def, issue.region) >= .999 && context.equipmentEligible(def, s.day) &&
                    !avoidableExerciseOverlap(def, def.flags.compound ? 'hypertrophy_compound' : 'hypertrophy_isolation', s.exercises.map(e => ({ def: context.exerciseById(e.exerciseId), role: e.role })).filter(x => x.def), { priority: issue.priority }))
                    .sort((a, b) => (a.flags.bodyweight ? 1 : 0) - (b.flags.bodyweight ? 1 : 0) || (a.fatigue?.systemic ?? 0) - (b.fatigue?.systemic ?? 0) || (a.setupCost ?? 0) - (b.setupCost ?? 0) || a.id.localeCompare(b.id)).slice(0, 6);
                for (const def of candidates) {
                    const legacy = resolveLegacyExercise({ exerciseId: def.id, name: def.name }, legacyExercises);
                    // Do not let a UI compatibility sibling silently change the selected movement's stimulus.
                    if (!legacy || nextExerciseIdForShellExercise(legacy) !== def.id) continue;
                    const role = def.flags.compound ? 'hypertrophy_compound' : 'hypertrophy_isolation', policy = phasePolicyFor(phase);
                    const exercise = { exerciseId: def.id, name: def.name, role, sets: 3, prescription: {
                        reps: repsForPhase(def, role, policy), rir: rirForPhase(role, policy), restSeconds: restForExercise(role, def) },
                        progression: progressionForExercise(def, role, policy, request.athlete.experience), progressionStyle: progressionStyleForExercise(def, role, policy, request.athlete.experience) };
                    const addition = { dayId: s.shellDayId, legacy, exercise };
                    consider([], addition);
                    if (!best) for (const d of redundant.filter(d => d.dayId === s.shellDayId))
                        consider([{ dayId: d.dayId, slot: d.slot, remove: true }], addition);
                    if (!best) for (const d of donors.filter(d => d.dayId === s.shellDayId))
                        for (let n = 1; n <= Math.min(3, d.e.sets - 2); n++) consider([{ dayId: d.dayId, slot: d.slot, delta: -n }], addition);
                }
            }
        }
        // A static-cycle roster can be structurally safe while a one-set accessory is rounded
        // upward by the session-level week allocator. Once base-set proposals are exhausted, repair
        // only the offending generated work-week cell. This does not alter the exercise identity or
        // base engine prescription, so validate it at the shell layer where the change actually lives:
        // the displayed deficit must shrink, no other region may leave its prior safe envelope, and
        // no session may become longer. The engine audit is unchanged because engine sessions are unchanged.
        if (!best && preserveRoster) for (const issue of audit.issues.filter(issue => issue.status === 'over')) {
            const week = audit.weeks[issue.week - 1];
            const exact = week.sessions.flatMap(session => session.exercises.map(exercise => ({ session, exercise,
                def: context.exerciseById(exercise.exerciseId) })))
                .filter(x => x.def && x.exercise.role === 'hypertrophy_isolation' && x.exercise.sets > 1 &&
                    publicRegionContribution(x.def, issue.region) > 0)
                .sort((a, b) => publicRegionContribution(b.def, issue.region) - publicRegionContribution(a.def, issue.region) ||
                    b.exercise.sets - a.exercise.sets || a.exercise.shellKey.localeCompare(b.exercise.shellKey));
            for (const x of exact) {
                const key = x.exercise.shellKey, cells = current.nextWeekPrescriptions?.[key], prior = cells?.[issue.week];
                if (!prior || !Number.isFinite(prior.sets) || prior.sets <= 1) continue;
                const candidate = { ...current,
                    nextWeekPrescriptions: { ...current.nextWeekPrescriptions,
                        [key]: { ...cells, [issue.week]: { ...prior, sets: prior.sets - 1 } } },
                    nextEngine: { ...current.nextEngine } };
                const after = auditShellVolume(candidate, legacyExercises);
                if (after.missing || deficit(after) >= deficit(audit) - EPS) continue;
                let exactSafe = true;
                for (let wi = 0; wi < after.weeks.length && exactSafe; wi++) {
                    const beforeWeek = audit.weeks[wi], afterWeek = after.weeks[wi];
                    for (const target of audit.targets) {
                        const lo = Math.min(target.mev, beforeWeek.regions[target.region]);
                        const hi = Math.max(target.mrv, beforeWeek.regions[target.region]);
                        if (afterWeek.regions[target.region] + EPS < lo || afterWeek.regions[target.region] > hi + EPS) {
                            exactSafe = false;
                            break;
                        }
                    }
                    if (!exactSafe) break;
                    for (let di = 0; di < afterWeek.sessions.length; di++) {
                        const a = afterWeek.sessions[di], b = beforeWeek.sessions[di];
                        if (a.estimatedMinutes > Math.max(a.maxMinutes, b.estimatedMinutes)) {
                            exactSafe = false;
                            break;
                        }
                    }
                }
                if (!exactSafe) continue;
                const score = deficit(audit) - deficit(after);
                if (!best || score > best.score + EPS) best = { candidate, after,
                    ops: [{ dayId: x.session.shellDayId, slot: x.exercise.shellSlot, delta: -1, week: issue.week, weeklyCell: true }],
                    addition: null, score };
            }
        }
        if (!best) break;
        current = best.candidate; audit = best.after;
        changes.push({ sets: best.ops, addedExercise: best.addition?.exercise.name });
    }
    const changed = current !== program;
    // Re-read the returned artifact, including all work-week rounding, before describing success.
    const after = changed ? auditShellVolume(current, legacyExercises) : before;
    const fits = after.weeks.every(w => w.sessions.every(s => s.estimatedMinutes <= s.maxMinutes));
    const status = !after.issues.length && fits && current.nextEngine.audit.result === 'pass' ? 'success' : changed ? 'partial' : 'unable';
    const summaries = before.issues.filter(i => i.status === 'under').map(i => {
        const actual = Math.min(...after.weeks.map(w => w.regions[i.region]));
        return actual > i.v + EPS ? `${i.label} increased from ${fmt(i.v)} to ${fmt(actual)} weekly effective sets.` : null;
    }).filter(Boolean);
    if (changed) {
        const explainability = buildProgramExplainability(current.nextEngine.program, request);
        current = { ...current, nextEngine: { ...current.nextEngine, explainability, program: { ...current.nextEngine.program, explainability },
            volumeRepair: { status, changes, before: before.issues, remaining: after.issues } } };
    }
    const remaining = after.issues.map(i => i.label.toLowerCase()).join(', ');
    const message = status === 'success' ? (summaries.join(' ') || 'Accessory volume reduced; weekly volume is now in range.')
        : `${summaries.join(' ')}${summaries.length ? ' ' : ''}Couldn’t fully repair ${remaining || 'this plan'} within its time, set, and recovery limits. Try a longer session or swap redundant work.`;
    return { program: current, status, changed, before, after, message };
}
