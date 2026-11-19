import { createExerciseMap } from './exercise-db.js';
import { getNextShellCell, nextExerciseIdForShellExercise, resolveNextShellExerciseId, resolveLegacyExercise, shellPrescriptionFieldOwner, NextShellAdapterError } from './shell-projection.js';
import { advancedTechniqueFromCell } from './workout-runtime.js';
import { normalizeRequest, createMusclePrescriptions } from './prescription.js';
import { PUBLIC_MEV_REGIONS, PUBLIC_REGION_MUSCLE, directlyTargetsPublicRegion, publicMevContractApplies, publicMevForExperience, publicMevRequired, publicMevInternalSafetyCeiling, publicRegionContribution, publicMevLedger } from './public-mev.js';
import { createTrainingSetEvents } from './events.js';
import { deriveMuscleLedger } from './ledgers.js';
import { deriveArmCoverage } from './arm-coverage.js';
import { createEngineContext } from './engine-context.js';
import { auditProgram } from './arbiter.js';
import { buildProgramExplainability } from './explainability.js';
import { estimateSessionMinutes, repsForPhase, rirForPhase, restForExercise, progressionForExercise, progressionStyleForExercise } from './realizer.js';
import { prescriptionForSimulationWeek } from './simulation.js';
import { phasePolicyFor } from './phase-policy.js';
import { avoidableExerciseOverlap, primaryDirectMuscles } from './exercise-economy.js';
import { INTENT_MUSCLES } from './topology.js';
import { STRENGTH_STRUCTURES } from './config.js';
import { functionalCoverageUseKeys } from './functional-coverage.js';
import { assessWeeklyRecovery } from './weekly-recovery.js';
import { exerciseRedundancyPenalty, exerciseSetupInefficiencyPenalty } from './exercise-selection-intelligence.js';
import { sessionBandFor, shellSessionMinutes } from './shell-session-clock.js';

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
const engineRange = (value, fallback) => {
    const numbers = (Array.isArray(value) ? value : String(value ?? '').match(/\d+(?:\.\d+)?/g) ?? []).map(Number);
    return numbers.length && numbers.every(Number.isFinite) ? [numbers[0], numbers[1] ?? numbers[0]] : fallback;
};
const snapshotContext = (program, legacyExercises) => {
    const request = requestOf(program);
    return { request, exerciseMap: createExerciseMap(request.customExercises),
        legacyMap: new Map(legacyExercises.map(e => [e.id, e])) };
};

// Read the live shell roster and the exact cells used by Program/Home/Workout. Neither the
// immutable generation snapshot nor legacy slotBias is a substitute for a current prescription.
export function captureShellVolumeSnapshot(program, week, legacyExercises = [], dayId = null, sharedContext = null) {
    if (!program?.nextEngine?.program || !program.nextEngine.request || !Array.isArray(program.days))
        return null;
    const { request, exerciseMap, legacyMap } = sharedContext ?? snapshotContext(program, legacyExercises);
    const sessions = [];
    let missing = false;
    program.days.forEach((day, di) => {
        if (dayId && day.id !== dayId) return;
        const source = sessionForDay(program, day, di);
        if (!source) { missing = true; return; }
        const exercises = day.exercises.map((id, slot) => {
            const key = keyOf(day, slot);
            const exerciseId = resolveNextShellExerciseId(program, day, slot, legacyMap.get(id));
            const def = exerciseMap.get(exerciseId);
            const cell = getNextShellCell(program, day, slot, week);
            if (!def || !cell || !Number.isFinite(cell.sets)) { missing = true; return null; }
            const original = source.exercises.find(e => e.exerciseId === exerciseId) ?? source.exercises[slot];
            return { ...original, exerciseId, name: def.name, sets: cell.sets, role: cell.role,
                progressionStyle: cell.progressionStyle,
                progressionSelection: cell.ownership.progressionStyle === 'user'
                    ? { ...original?.progressionSelection, source: 'manual', confidence: 'high', reason: 'You selected this progression method.' }
                    : original?.progressionSelection,
                prescription: { ...original?.prescription,
                    reps: engineRange(cell.reps, original?.prescription?.reps),
                    rir: engineRange(cell.rir, original?.prescription?.rir),
                    restSeconds: cell.rest != null && cell.rest !== '' && Number.isFinite(Number(cell.rest)) && Number(cell.rest) >= 0 ? Number(cell.rest) : 90 },
                advancedTechnique: advancedTechniqueFromCell(cell, original?.advancedTechnique),
                supersetGroup: undefined,
                shellKey: key, shellId: id, shellSlot: slot };
        }).filter(Boolean);
        for (let slot = 0; slot < exercises.length - 1; slot++) {
            if (!program.config?.noSupersets && program.ss?.[keyOf(day, slot)] && !exercises[slot].supersetGroup && !exercises[slot + 1].supersetGroup) {
                exercises[slot].supersetGroup = `${day.id}:pair:${slot}`;
                exercises[slot + 1].supersetGroup = `${day.id}:pair:${slot}`;
            }
        }
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
    return { sessions, events, muscleLedger, regions, volume, subVolume, armCoverage: deriveArmCoverage(sessions, exerciseMap), missing };
}

// Normal floors come from the same experience/phase/priority/capacity prescriptions as the
// allocator and arbiter. The explicit 5-day accumulation contract retains its public region MEVs.
export function shellVolumeTargets(program, normalizedRequest = null) {
    const request = normalizedRequest ?? requestOf(program), phase = phaseOf(program);
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
    const context = snapshotContext(program, legacyExercises);
    const targets = shellVolumeTargets(program, context.request);
    const weeks = Array.from({ length: workWeeks(program) }, (_, i) =>
        captureShellVolumeSnapshot(program, i + 1, legacyExercises, null, context)
        ?? { sessions: [], regions: Object.fromEntries(PUBLIC_MEV_REGIONS.map(r => [r, 0])), missing: true });
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

/* What a tighter rebuild is not allowed to give up. Refitting a plan to the displayed clock rebuilds it, and the
   rebuild can pass the engine audit while quietly dropping something the first plan had. Protected: the large
   regions and anything the lifter focused. For those, a region that met its minimum may not fall under it and a
   region with direct work may not lose all of it; a new lifter's quads and hamstrings also keep their second
   weekly day (the Build 879 frequency rule). The small regions are the ones the prescription already treats as
   optional in a compact week (createMusclePrescriptions compactSmallNormal): against a limit the lifter chose,
   an unfocused calf or core lift is the right thing to lose. */
const REFIT_OPTIONAL_REGIONS = new Set(['side_delts', 'rear_delts', 'biceps', 'triceps', 'calves', 'core']);
export function shellPlanQuality(program, legacyExercises = []) {
    const audit = auditShellVolume(program, legacyExercises);
    if (audit.missing) return null;
    const context = snapshotContext(program, legacyExercises);
    const sessions = audit.weeks[0]?.sessions ?? [];
    const directDays = Object.fromEntries(PUBLIC_MEV_REGIONS.map(region => [region, sessions.filter(session =>
        session.exercises.some(ex => { const def = context.exerciseMap.get(ex.exerciseId); return def && ex.sets > 0 && directlyTargetsPublicRegion(def, region); })).length]));
    const focused = region => ['high', 'specialization', 'primary'].includes(context.request.goal.musclePriorities[PUBLIC_REGION_MUSCLE[region]]);
    return { under: audit.issues.filter(issue => issue.status === 'under').map(issue => issue.region), directDays,
        protectedRegions: PUBLIC_MEV_REGIONS.filter(region => !REFIT_OPTIONAL_REGIONS.has(region) || focused(region)),
        novice: context.request.athlete.experience === 'novice' };
}
export function shellPlanQualityKept(before, after) {
    if (!before || !after) return false;
    return before.protectedRegions.every(region =>
        (before.under.includes(region) || !after.under.includes(region))
        && after.directDays[region] >= Math.min(before.novice && (region === 'quads' || region === 'hamstrings') ? 2 : 1, before.directDays[region]));
}

function baseSessions(program, legacyExercises, snapshot = null) {
    const live = snapshot ?? captureShellVolumeSnapshot(program, 1, legacyExercises);
    return live.sessions.map((s, di) => {
        const original = sessionForDay(program, program.days[di], di);
        const exercises = s.exercises.map(e => {
            const override = program.overrides?.[e.shellKey] ?? {};
            const userSets = shellPrescriptionFieldOwner(program, override, 'sets') === 'user' ? Number(override.sets) : null;
            return { ...e, sets: (Number.isFinite(userSets) && userSets > 0 ? userSets : null)
                ?? original.exercises.find(x => x.exerciseId === e.exerciseId)?.sets ?? e.sets };
        });
        return { ...s, exercises, estimatedMinutes: estimateSessionMinutes(exercises) };
    });
}

// Block transitions read a temporary live base view, not stale generation identities. Keep base
// set allocation for ordinary slots, honor explicit edits, and recalculate events/clock/audit for
// the current roster. The immutable source and executable week cells remain separate and untouched.
export function captureShellBaseProgram(program, legacyExercises = []) {
    if (!program?.nextEngine?.program || !program.nextEngine.request) return null;
    const context = snapshotContext(program, legacyExercises);
    const live = captureShellVolumeSnapshot(program, 1, legacyExercises, null, context);
    if (!live || live.missing) return null;
    const sessions = baseSessions(program, legacyExercises, live);
    const request = context.request;
    const events = createTrainingSetEvents(sessions, request.customExercises);
    const projected = { ...program.nextEngine.program, sessions, events, muscleLedger: deriveMuscleLedger(events) };
    projected.audit = auditProgram(projected, request);
    return projected;
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
        const key = e.shellKey;
        // Removing an exercise also removes its prescription. Ownership protection must run
        // before either kind of mutation, including consolidation of a redundant movement.
        if (shellPrescriptionFieldOwner(next, next.overrides?.[key] ?? {}, 'sets') === 'user')
            return null;
        if (change.remove) {
            if (preserveRoster) return null;
            if (['reps', 'rir', 'rest', 'tech', 'role', 'progressionStyle'].some(field =>
                shellPrescriptionFieldOwner(next, next.overrides?.[key] ?? {}, field) === 'user')) return null;
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
        // A user-owned manual set prescription is not raw material for Auto-fix. Earlier repair code
        // changed both the engine snapshot and the override mirror, which made one value have two
        // writers and could silently rewrite a lifter's deliberate edit. Fail this proposal instead
        // and let the search choose another engine-owned slot.
        e.sets += change.delta;
        if (request.preferences.volumeApproach === 'minimalist' && e.sets > 3) return null;
        if (e.sets < (preserveRoster ? 1 : 2)) return null; // locked cycles may retain a one-set accessory instead of changing the roster
        changed.add(key);
        // Do not mirror generated set counts back into overrides. nextWeekPrescriptions is the
        // executable projection; nextEngine.program is the audited engine source. Overrides are only
        // for explicit user ownership plus identity metadata.
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
        // Generated slot identity lives here; its prescription/role/style live only in the regenerated
        // week cells and audited engine program. Do not create a second prescription owner in overrides.
        next.overrides[key] = { nextEngine: true, nextExerciseId: e.exerciseId, legacyExerciseId: addition.legacy.id };
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
                const generated = prescriptionForSimulationWeek(session, deload ? 'recovery' : phaseOf(next), deload ? 1 : w, deload ? 1 : workWeeks(next), { experience: request.athlete.experience }).exercises[slot];
                const prior = cells[w];
                cells[w] = { ...prior, sets: generated.sets,
                    ...(prior ? {} : { reps: generated.prescription.reps.join('-'), rir: generated.prescription.rir.join('-'),
                        rest: generated.prescription.restSeconds, role: generated.role, progressionStyle: generated.progressionStyle, tech: null }) };
            }
            next.nextWeekPrescriptions[key] = cells;
        }
    }
    const events = createTrainingSetEvents(sessions, request.customExercises);
    const engine = { ...program.nextEngine.program, sessions, events, muscleLedger: deriveMuscleLedger(events) };
    next.nextEngine.program = engine;
    // deferAudit: the caller screens the candidate with cheaper checks first and audits what survives.
    if (!options.deferAudit) withEngineAudit(next, request);
    return next;
}

function withEngineAudit(candidate, request) {
    candidate.nextEngine.program.audit = auditProgram(candidate.nextEngine.program, request);
    candidate.nextEngine.audit = candidate.nextEngine.program.audit;
    return candidate;
}

// Findings of every severity, keyed like auditCounts.
const findingCounts = audit => {
    const counts = {};
    for (const f of audit?.findings ?? []) { const key = `${f.code}:${f.sessionId ?? ''}:${f.severity}`; counts[key] = (counts[key] || 0) + 1; }
    return counts;
};
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
    const setCap = requestOf(candidate).preferences.volumeApproach === 'minimalist' ? 3 : 5;
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
                if (changedKeys.has(e.shellKey) && e.sets > Math.max(
                    setCap,
                    b.sessions[di].exercises.find(x => x.shellKey === e.shellKey)?.sets ?? 0)) return false;
        }
    }
    const oldCounts = auditCounts(baselineEngineAudit), newCounts = auditCounts(candidate.nextEngine.audit);
    return Object.entries(newCounts).every(([key, n]) => n <= (oldCounts[key] || 0));
}

/** Static locked-cycle finishing pass. Base-set repair must finish first because rebuilding a
 * session regenerates all of its work-week cells. This pass touches generated work-week set counts
 * only, preserves exercise identity and strength roles, and never allows another region or the
 * session clock to leave its prior safe envelope. Tied worst weeks may require several individually
 * non-worsening trims before the block-wide maximum falls. */
export function reconcileLockedCycleWeekOverflows(program, legacyExercises = []) {
    let current = program, audit = auditShellVolume(program, legacyExercises), changed = false;
    const context = createEngineContext(requestOf(program));
    const safeCell = (before, after, issue) => {
        if (after.missing || deficit(after) > deficit(before) + EPS) return false;
        const bw = before.weeks[issue.week - 1], aw = after.weeks[issue.week - 1];
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
            if (!best || score > best.score + EPS) best = { candidate, after, score };
        }
        if (!best) break;
        current = best.candidate;
        audit = best.after;
        changed = true;
    }
    return { program: current, after: audit, changed,
        status: audit.issues.some(x => x.status === 'over') ? (changed ? 'partial' : 'unable') : changed ? 'success' : 'unchanged' };
}

/** True when a locked roster is already at its floor for every remaining overflow: in the over week, each lift
 * that could give back a set of that region (not protected strength work) is down to one set, so the only way
 * under the ceiling would be removing a movement — which a locked cycle may not do. Such an overflow is the
 * skeleton's minimum, not a repair failure; anything above the floor is still refused. */
export function lockedOverflowAtFloor(audit, legacyExercises = [], program = null) {
    const over = audit?.issues?.filter(x => x.status === 'over') ?? [];
    if (!over.length || audit.missing) return false;
    const context = createEngineContext(requestOf(program));
    return over.every(issue => {
        const week = audit.weeks[issue.week - 1];
        if (!week) return false;
        return week.sessions.every(session => session.exercises.every(exercise => {
            const def = context.exerciseById(exercise.exerciseId);
            if (!def || STRENGTH.has(exercise.role) || publicRegionContribution(def, issue.region) <= 0) return true;
            return exercise.sets <= 1;
        }));
    });
}

/** Finalize exact executable weeks, rather than increasing the entire base block to repair one
 * rounded week. Moves keep the roster, protected strength work, explicit edits, phase methods,
 * regional and focused-muscle envelopes, and session clock. Lookup context is shared for the pass. */
export function reconcileShellWeekDose(program, legacyExercises = []) {
    if (!program?.nextEngine?.program || !program.nextEngine.request || !Array.isArray(program.days))
        return { program, after: auditShellVolume(program, legacyExercises), timeIssues: [],
            changes: [], changed: false, status: 'unable' };
    const context = snapshotContext(program, legacyExercises), { request } = context;
    const targets = shellVolumeTargets(program, request);
    const prescriptions = createMusclePrescriptions(request, phaseOf(program));
    const cap = request.preferences.volumeApproach === 'minimalist' ? 3 : 5;
    const objective = snapshot => targets.reduce((sum, t) => sum + Math.max(0, t.mev - snapshot.regions[t.region])
        + Math.max(0, snapshot.regions[t.region] - t.mrv), 0)
        + snapshot.sessions.reduce((sum, s) => sum + Math.max(0, s.estimatedMinutes - s.maxMinutes) * 4, 0);
    const weekAudit = snapshot => auditProgram({ ...program.nextEngine.program,
        sessions: snapshot.sessions, events: snapshot.events, muscleLedger: snapshot.muscleLedger }, request);
    let current = program;
    const changes = [];
    for (let week = 1; week <= workWeeks(program); week++) {
        let before = captureShellVolumeSnapshot(current, week, legacyExercises, null, context);
        if (!before || before.missing) continue;
        for (let guard = 0; guard < 32 && objective(before) > EPS; guard++) {
            const slots = before.sessions.flatMap(s => s.exercises.map(e => ({ s, e,
                def: context.exerciseMap.get(e.exerciseId) }))).filter(x => x.def && !STRENGTH.has(x.e.role)
                && shellPrescriptionFieldOwner(current, current.overrides?.[x.e.shellKey] ?? {}, 'sets') !== 'user');
            const proposals = [];
            const propose = operations => {
                const cells = { ...current.nextWeekPrescriptions };
                for (const { key, delta } of operations) {
                    const prior = cells[key]?.[week];
                    if (!prior || !Number.isFinite(prior.sets) || prior.sets + delta < 1 || prior.sets + delta > cap) return;
                    cells[key] = { ...cells[key], [week]: { ...prior, sets: prior.sets + delta } };
                }
                const candidate = { ...current, nextWeekPrescriptions: cells };
                const after = captureShellVolumeSnapshot(candidate, week, legacyExercises, null, context);
                if (after.missing || objective(after) >= objective(before) - EPS) return;
                for (const target of targets) {
                    const lo = Math.min(target.mev, before.regions[target.region]);
                    const hi = Math.max(target.mrv, before.regions[target.region]);
                    if (after.regions[target.region] < lo - EPS || after.regions[target.region] > hi + EPS) return;
                }
                for (const p of prescriptions) {
                    const b = before.muscleLedger[p.muscle].fractionalSets;
                    const a = after.muscleLedger[p.muscle].fractionalSets;
                    const hi = publicMevInternalSafetyCeiling(request, phaseOf(program), p.muscle, p.upper) * 1.2;
                    if (a < Math.min(p.minimum * .85, b) - EPS || a > Math.max(hi, b) + EPS) return;
                }
                if (after.sessions.some((s, i) => s.estimatedMinutes > Math.max(s.maxMinutes, before.sessions[i].estimatedMinutes))) return;
                proposals.push({ candidate, after, operations, score: objective(before) - objective(after) });
            };
            const under = targets.filter(t => before.regions[t.region] < t.mev - EPS);
            const donors = slots.filter(x => x.e.sets > 1);
            const recipients = slots.filter(x => x.e.sets < cap && under.some(t => publicRegionContribution(x.def, t.region) > 0));
            for (const x of recipients) propose([{ key: x.e.shellKey, delta: 1 }]);
            for (const x of donors) propose([{ key: x.e.shellKey, delta: -1 }]);
            const baselineCounts = auditCounts(weekAudit(before));
            const selectBest = () => proposals.sort((a, b) => b.score - a.score).find(p => {
                const counts = auditCounts(weekAudit(p.after));
                return Object.entries(counts).every(([key, n]) => n <= (baselineCounts[key] || 0));
            });
            let best = selectBest();
            // Try trades when no fully audited single move survives. A numerically promising
            // single move may fail a recovery/coverage guard; it must not suppress the trade search.
            if (!best) {
                for (const r of recipients) for (const d of donors)
                    if (r.s.shellDayId === d.s.shellDayId && r.e.shellKey !== d.e.shellKey)
                        for (let n = 1; n <= Math.min(2, d.e.sets - 1); n++)
                            propose([{ key: r.e.shellKey, delta: 1 }, { key: d.e.shellKey, delta: -n }]);
                best = selectBest();
            }
            if (!best) break;
            current = best.candidate; before = best.after;
            changes.push({ week, sets: best.operations });
        }
    }
    const after = auditShellVolume(current, legacyExercises);
    const timeIssues = after.weeks.flatMap((w, wi) => w.sessions.filter(s => s.estimatedMinutes > s.maxMinutes)
        .map(s => ({ week: wi + 1, dayId: s.shellDayId, minutes: s.estimatedMinutes, maximum: s.maxMinutes })));
    return { program: current, after, timeIssues, changes, changed: current !== program,
        status: !after.missing && !after.issues.length && !timeIssues.length ? (changes.length ? 'success' : 'unchanged')
            : changes.length ? 'partial' : 'unable' };
}

// Capacity is a hard constraint. Volume floors are guidance when the schedule cannot fit them.
// Trim only engine-owned accessory sets; never shorten prescribed strength rest or overwrite edits.
export function enforceShellWeekCapacity(program, legacyExercises = []) {
    let current = program;
    const changes = [];
    for (let week = 1; week <= workWeeks(program); week++) {
        for (let guard = 0; guard < 400; guard++) {
            const before = captureShellVolumeSnapshot(current, week, legacyExercises);
            if (!before || before.missing) break;
            const over = before.sessions.filter(s => s.estimatedMinutes > s.maxMinutes);
            if (!over.length) break;
            const proposals = [];
            for (const session of over) for (const exercise of session.exercises) {
                const key = exercise.shellKey, prior = current.nextWeekPrescriptions?.[key]?.[week];
                if (STRENGTH.has(exercise.role) || exercise.sets <= 1 || !prior
                    || shellPrescriptionFieldOwner(current, current.overrides?.[key] ?? {}, 'sets') === 'user') continue;
                const candidate = { ...current, nextWeekPrescriptions: { ...current.nextWeekPrescriptions,
                    [key]: { ...current.nextWeekPrescriptions[key], [week]: { ...prior, sets: exercise.sets - 1 } } } };
                const after = captureShellVolumeSnapshot(candidate, week, legacyExercises);
                const next = after.sessions.find(s => s.shellDayId === session.shellDayId);
                const saved = session.estimatedMinutes - next.estimatedMinutes;
                if (saved >= 0) proposals.push({ candidate, key, saved: saved || .01, dayId: session.shellDayId });
            }
            const best = proposals.sort((a, b) => b.saved - a.saved || a.key.localeCompare(b.key))[0];
            if (!best) {
                const error = new NextShellAdapterError('NEXT_WEEK_CAPACITY_EXCEEDED', 'The final weekly prescription cannot fit the session limit while preserving strength work and user-owned sets. Increase session time or reduce the requested work.',
                    { suggestions: [{ code: 'increase_time', title: 'Choose a longer session' }, { code: 'reduce_work', title: 'Reduce requested work' }] });
                error.details = { week, sessions: over.map(s => ({ dayId: s.shellDayId, minutes: s.estimatedMinutes, maximum: s.maxMinutes })) };
                throw error;
            }
            current = best.candidate;
            changes.push({ week, sets: [{ key: best.key, delta: -1 }], reason: 'session_capacity' });
        }
    }
    return { program: current, changes };
}

/* The focused-region set transaction shared by the strength-entry rebalance and the focus-dose guarantee.
   One set at a time goes to the engine-owned, non-strength lift that best trains an outstanding focus region
   (optionally paid for by a same-day lift that trains no focus region). A move is kept only when every work
   week stays inside its session time, no region leaves its public bounds and the engine audit does not regress. */
function allocateFocusedRegionSets(program, legacyExercises, context, targets, focus, desired, maxSteps = 3, rampCapped = false, accept = null) {
    let current = program;
    const changes = [];
    const weekCount = workWeeks(program);
    const permitted = (before, after, baselineAudits) => {
        if (after.missing || after.weeks.length !== weekCount) return false;
        for (let wi = 0; wi < weekCount; wi++) {
            const bw = before.weeks[wi], aw = after.weeks[wi];
            if (!aw || aw.missing) return false;
            for (const target of targets) {
                const region = target.region;
                const lower = focus.includes(region)
                    ? bw.regions[region] - EPS
                    : Math.min(target.mev, bw.regions[region]) - EPS;
                if (aw.regions[region] < lower
                    || aw.regions[region] > Math.max(target.mrv, bw.regions[region]) + EPS)
                    return false;
            }
            for (const s of aw.sessions)
                if (s.estimatedMinutes > s.maxMinutes + EPS) return false;
            const nextAudit = auditProgram({ ...program.nextEngine.program,
                sessions: aw.sessions, events: aw.events, muscleLedger: aw.muscleLedger }, context.request);
            const oldCounts = baselineAudits[wi];
            if (Object.entries(auditCounts(nextAudit)).some(([key, n]) => n > (oldCounts[key] || 0)))
                return false;
        }
        return true;
    };
    for (let step = 0; step < maxSteps; step++) {
        const before = auditShellVolume(current, legacyExercises);
        if (before.missing) break;
        const first = before.weeks[0];
        const outstanding = focus
            .filter(region => desired[region] > first.regions[region] + .05)
            .sort((a, b) => (desired[b] - first.regions[b]) - (desired[a] - first.regions[a]));
        if (!outstanding.length) break;
        const baselineAudits = before.weeks.map(week => auditCounts(auditProgram({
            ...current.nextEngine.program, sessions: week.sessions, events: week.events,
            muscleLedger: week.muscleLedger }, context.request)));
        let best = null;
        for (const region of outstanding) {
            const slots = first.sessions.flatMap(session => session.exercises.map(ex => ({
                session, ex, def: context.exerciseMap.get(ex.exerciseId)
            })));
            const editable = slot => !STRENGTH.has(slot.ex.role)
                && shellPrescriptionFieldOwner(current,
                    current.overrides?.[slot.ex.shellKey] ?? {}, 'sets') !== 'user';
            const recipients = slots.filter(slot => slot.def && editable(slot)
                && publicRegionContribution(slot.def, region) >= .4
                && Array.from({ length: weekCount }, (_, i) =>
                    current.nextWeekPrescriptions?.[slot.ex.shellKey]?.[i + 1]?.sets)
                    // rampCapped: a lift whose set ramp ends at the five-set cap can still take a set in the
                    // weeks below it (3/4/4/5 -> 4/5/5/5); the cap itself is never exceeded.
                    .every((sets, i) => Number.isFinite(sets) && (sets < 5 || (rampCapped && i > 0))))
                .sort((a, b) => publicRegionContribution(b.def, region)
                    - publicRegionContribution(a.def, region)
                    || a.ex.shellKey.localeCompare(b.ex.shellKey)).slice(0, 6);
            for (const recipient of recipients) {
                // A zero-donor candidate is preferred when genuine session
                // capacity remains. Otherwise search only same-day trades,
                // never strip prioritized work or strength-specific practice.
                const donors = slots.filter(slot => slot.session.shellDayId === recipient.session.shellDayId
                    && slot.ex.shellKey !== recipient.ex.shellKey && slot.def && editable(slot)
                    && focus.every(f => publicRegionContribution(slot.def, f) <= EPS)
                    && Array.from({ length: weekCount }, (_, i) =>
                        current.nextWeekPrescriptions?.[slot.ex.shellKey]?.[i + 1]?.sets)
                        .every(sets => Number.isFinite(sets) && sets > 1))
                    .sort((a, b) => a.ex.shellKey.localeCompare(b.ex.shellKey)).slice(0, 6);
                for (const donor of [null, ...donors]) {
                    const ops = [{ key: recipient.ex.shellKey, delta: 1 },
                        ...(donor ? [{ key: donor.ex.shellKey, delta: -1 }] : [])];
                    const cells = { ...current.nextWeekPrescriptions };
                    for (const op of ops) {
                        const existing = cells[op.key];
                        cells[op.key] = { ...existing };
                        for (let w = 1; w <= weekCount; w++)
                            cells[op.key][w] = { ...existing[w], sets: rampCapped && op.delta > 0
                                ? Math.min(5, existing[w].sets + op.delta) : existing[w].sets + op.delta };
                    }
                    const candidate = { ...current, nextWeekPrescriptions: cells };
                    const after = auditShellVolume(candidate, legacyExercises);
                    const gain = Math.min(desired[region], after.weeks[0]?.regions[region] ?? 0)
                        - Math.min(desired[region], first.regions[region]);
                    if (gain <= .05 || !permitted(before, after, baselineAudits) || (accept && !accept(candidate))) continue;
                    const score = gain - (donor ? .025 : 0);
                    if (!best || score > best.score + EPS)
                        best = { candidate, region, ops, gain, score };
                }
            }
        }
        if (!best) break;
        current = best.candidate;
        changes.push({ region: best.region, creditedSetsGained: Math.round(best.gain * 10) / 10,
            operations: best.ops, weeks: weekCount });
    }
    return { current, changes };
}

/**
 * M309: Retain feasible focused regional work at a hypertrophy -> strength
 * boundary. This is a set *reallocation* over the generated executable cells:
 * never alter exercises, strength anchors, repetitions, progression or user
 * edits. Accept moves only when every work-week is capacity-safe, public
 * regional bounds are preserved and complete engine audits do not regress.
 */
/* A strength block after hypertrophy keeps a share of every region's work, not only the focused ones. The strength
   phase's own targets sit near maintenance (upper back 10 -> 3.6 sets, side and rear delts 9 -> 4, calves 8 -> 4 in a
   five-day 90-minute plan) while its days used 35-51 of their 90 minutes: the heavy work needs longer rests, not the
   whole session. Maintaining size takes roughly a third of the building volume; a strength block is still an
   accumulation block, so each region keeps STRENGTH_ENTRY_RETENTION of its last hypertrophy week, up to the strength
   phase's ceiling. Sets go one at a time onto accessories already in the plan (never a strength lift, never past
   STRENGTH_ACCESSORY_SET_CAP, never an explicit edit), under the same limits as any added set: region and muscle
   ceilings in every week, both clocks, no new recovery collision; the finished plan may not add an engine finding in
   any week, or the last additions are taken back. What cannot be kept is reported (`strengthEntryDose`). */
export const STRENGTH_ENTRY_RETENTION = .6;
const STRENGTH_ACCESSORY_SET_CAP = 4;
export function retainStrengthEntryDose(previous, program, legacyExercises = [], priorSnapshot = null) {
    if ((!priorSnapshot && previous?.nextEngine?.program?.phase !== 'hypertrophy_accumulation')
        || program?.nextEngine?.program?.phase !== 'strength_accumulation') return program;
    const guards = bandFillGuards(program, legacyExercises, null, { anyBand: true });
    if (!guards || guards.request.preferences.volumeApproach === 'minimalist') return program;
    const prior = priorSnapshot ?? captureShellVolumeSnapshot(previous, workWeeks(previous), legacyExercises);
    if (!prior || prior.missing) return program;
    const { context, weekCount, targets } = guards;
    // Never less than the strength phase's own minimum: the share is on top of the floor a new plan is held to.
    const desired = Object.fromEntries(targets.map(t => [t.region, Math.min(t.mrv, Math.max(t.mev, (prior.regions[t.region] || 0) * STRENGTH_ENTRY_RETENTION))]));
    const openAll = targets.map(t => t.region);
    const start = auditShellVolume(program, legacyExercises);
    if (start.missing || start.weeks.length !== weekCount) return program;
    // Regions that share a muscle share its strength-phase ceiling (lats and upper back are both "back"). Where the
    // retained shares would not fit under it together, they are scaled to what fits, in proportion to the hypertrophy
    // week, so the block is balanced rather than all lats; a trade can then move a set from one to the other.
    for (const p of createMusclePrescriptions(guards.request, 'strength_accumulation')) {
        const regions = targets.filter(t => PUBLIC_REGION_MUSCLE[t.region] === p.muscle).map(t => t.region);
        if (regions.length < 2) continue;
        const ledger = start.weeks[0].muscleLedger?.[p.muscle]?.fractionalSets, ceiling = publicMevInternalSafetyCeiling(guards.request, 'strength_accumulation', p.muscle, p.upper);
        if (!Number.isFinite(ledger) || !Number.isFinite(ceiling)) continue;
        const now = regions.reduce((n, r) => n + start.weeks[0].regions[r], 0), want = regions.reduce((n, r) => n + desired[r], 0);
        const fits = now + Math.max(0, ceiling - ledger);
        if (want > fits + EPS) for (const r of regions) desired[r] *= fits / want;
    }
    const withSet = (candidate, key) => ({ ...candidate, nextWeekPrescriptions: { ...candidate.nextWeekPrescriptions,
        [key]: Object.fromEntries(Object.entries(candidate.nextWeekPrescriptions[key]).map(([w, cell]) => [w, Number(w) <= weekCount
            ? { ...cell, sets: Math.max(cell.sets, Math.min(STRENGTH_ACCESSORY_SET_CAP, cell.sets + 1)) } : cell])) } });
    const withoutSet = (candidate, key) => ({ ...candidate, nextWeekPrescriptions: { ...candidate.nextWeekPrescriptions,
        [key]: Object.fromEntries(Object.entries(candidate.nextWeekPrescriptions[key]).map(([w, cell]) => [w, Number(w) <= weekCount
            ? { ...cell, sets: Math.max(1, cell.sets - 1) } : cell])) } });
    let baseline = null;
    const regressed = candidate => { baseline ??= weeklyAuditCounts(program, legacyExercises);
        return weeklyAuditCounts(candidate, legacyExercises).some((week, wi) => regressedCounts(week, baseline[wi] ?? {})); };
    let current = program, before = start;
    const steps = [], blocked = new Set(), restoredRegions = new Set();
    const engineContext = createEngineContext(guards.request), policy = phasePolicyFor('strength_accumulation');
    const priorIds = [...new Set((prior.sessions ?? []).flatMap(session => session.exercises.map(ex => ex.exerciseId)))];
    const restoreHypertrophyLift = (t) => {
        if (restoredRegions.has(t.region)) return null;
        restoredRegions.add(t.region);
        const first = before.weeks[0];
        const inPlan = new Set(first.sessions.flatMap(session => session.exercises.map(ex => ex.exerciseId)));
        // The hypertrophy block's own lift first (same exercise, known loads). A region under its minimum with none
        // gets one chosen as a new plan would choose it: the engine's candidates for the muscle, by how directly they
        // train the region, their hypertrophy suitability and fatigue, without duplicating what the day already has.
        const fromPrior = priorIds.map(id => engineContext.exerciseById(id));
        const underFloor = first.regions[t.region] < t.mev - EPS;
        const pool = fromPrior.some(def => def && !inPlan.has(def.id) && publicRegionContribution(def, t.region) >= .5) || !underFloor ? fromPrior
            : engineContext.candidatesForMuscle(t.muscle).filter(def => !FILL_SIDE_REGIONS.has(def.legacySubregion))
                .map(def => ({ def, worth: publicRegionContribution(def, t.region) * .6 + (def.suitability?.hypertrophy ?? 0) * .35 + (def.stability ?? 0) * .15
                    // beside heavy squats and pulls, a supported row beats a bent-over one
                    - ((def.fatigue?.systemic ?? 0) + (def.fatigue?.lowerBack ?? 0) + (def.fatigue?.axial ?? 0)) * .3 }))
                .sort((a, b) => b.worth - a.worth || a.def.id.localeCompare(b.def.id)).map(x => x.def);
        const lifts = pool
            .filter(def => def && !inPlan.has(def.id) && publicRegionContribution(def, t.region) >= .5)
            // An isolation lift first (less time and fatigue beside heavy work), a compound such as a row when only it serves.
            .map((def, rank) => ({ def, rank }))
            .sort((a, b) => (a.def.flags.compound ? 1 : 0) - (b.def.flags.compound ? 1 : 0)
                || (pool === fromPrior ? publicRegionContribution(b.def, t.region) - publicRegionContribution(a.def, t.region) || a.def.id.localeCompare(b.def.id) : a.rank - b.rank))
            .map(x => x.def);
        const days = first.sessions.map(session => ({ session, room: session.maxMinutes - session.estimatedMinutes }))
            .filter(x => x.room >= FILL_NEW_LIFT_MINUTES && (INTENT_MUSCLES[x.session.intent] ?? INTENT_MUSCLES.full).includes(t.muscle))
            .sort((a, b) => b.room - a.room);
        for (const def of lifts.filter(def => days.some(({ session }) => engineContext.equipmentEligible(def, session.day))).slice(0, 4)) for (const { session } of days) {
            if (!engineContext.equipmentEligible(def, session.day)) continue;
            const existing = session.exercises.map(e => ({ def: engineContext.exerciseById(e.exerciseId), role: e.role })).filter(x => x.def);
            if (avoidableExerciseOverlap(def, def.flags.compound ? 'hypertrophy_compound' : 'hypertrophy_isolation', existing, { priority: 'normal' })) continue;
            const legacy = resolveLegacyExercise({ exerciseId: def.id, name: def.name }, legacyExercises);
            if (!legacy || nextExerciseIdForShellExercise(legacy) !== def.id) continue;
            const role = def.flags.compound ? 'hypertrophy_compound' : 'hypertrophy_isolation';
            const exercise = { exerciseId: def.id, name: def.name, role, sets: PATTERN_LIFT_SETS, prescription: {
                reps: repsForPhase(def, role, policy), rir: rirForPhase(role, policy), restSeconds: restForExercise(role, def) },
                progression: progressionForExercise(def, role, policy, guards.request.athlete.experience),
                progressionStyle: progressionStyleForExercise(def, role, policy, guards.request.athlete.experience) };
            let candidate;
            try { candidate = withSourcePrescriptions(rebuild(current, [], legacyExercises, { dayId: session.shellDayId, legacy, exercise }, { deferAudit: true }), current); }
            catch { candidate = null; }
            // The block's taper would leave the new lift on one set from week two (a fragmented day); it keeps two.
            if (candidate) {
                const day = candidate.days.find(d => d.id === session.shellDayId), key = `${session.shellDayId}:${(day?.exercises.length ?? 1) - 1}`;
                const cells = candidate.nextWeekPrescriptions?.[key];
                if (cells) candidate = { ...candidate, nextWeekPrescriptions: { ...candidate.nextWeekPrescriptions, [key]: Object.fromEntries(Object.entries(cells)
                    .map(([w, cell]) => [w, Number(w) <= weekCount ? { ...cell, sets: Math.max(PATTERN_LIFT_SETS, cell.sets) } : cell])) } };
            }
            const after = candidate && guards.allowed(before, current, candidate, openAll);
            if (!after) continue;
            // A new lift is checked against the engine audit in every week now, so one poor fit does not cost the
            // step: the next candidate is tried.
            if (regressed(candidate)) continue;
            return { candidate, after, key: `${session.shellDayId}:+${def.id}`, added: def.name, region: t.region };
        }
        return null;
    };
    for (let step = 0; step < 40; step++) {
        const first = before.weeks[0];
        const short = targets.map(t => ({ t, gap: desired[t.region] - first.regions[t.region] }))
            .filter(x => x.gap >= .5 || first.regions[x.t.region] < x.t.mev - EPS).sort((a, b) => b.gap / Math.max(1, desired[b.t.region]) - a.gap / Math.max(1, desired[a.t.region]));
        if (!short.length) break;
        let taken = null;
        for (const { t } of short) {
            const options = first.sessions.flatMap(session => session.exercises.map(ex => ({ ex, session, def: context.exerciseMap.get(ex.exerciseId),
                weekly: Array.from({ length: weekCount }, (_, i) => current.nextWeekPrescriptions?.[ex.shellKey]?.[i + 1]?.sets) })))
                .filter(x => x.def && !blocked.has(x.ex.shellKey + ':' + t.region) && fillable(current, x.ex)
                    && x.weekly.every(Number.isFinite) && x.weekly.some(sets => sets < STRENGTH_ACCESSORY_SET_CAP)
                    && publicRegionContribution(x.def, t.region) >= .5)
                .sort((a, b) => publicRegionContribution(b.def, t.region) - publicRegionContribution(a.def, t.region)
                    || a.weekly[0] - b.weekly[0] || (a.session.estimatedMinutes - a.session.maxMinutes) - (b.session.estimatedMinutes - b.session.maxMinutes)
                    || a.ex.shellKey.localeCompare(b.ex.shellKey));
            for (const option of options) {
                const candidate = withSet(current, option.ex.shellKey);
                // Every region may pass the fill's first-week dose bound here (a pulldown set also credits biceps);
                // each stays under its strength-phase ceiling in every week.
                const after = guards.allowed(before, current, candidate, openAll);
                if (after) { taken = { candidate, after, key: option.ex.shellKey, region: t.region }; break; }
                // A muscle at its strength-phase ceiling (back = lats + upper back) can still trade: a set comes off an
                // accessory whose every region stays at or above its retained share, onto this one.
                // The ceiling is weekly, so the donor may be on any day; the trade must still be a net gain for this region (a
                // row over a pulldown is about a quarter set of upper back).
                const donors = first.sessions.flatMap(session => session.exercises).map(ex => ({ ex, def: context.exerciseMap.get(ex.exerciseId),
                    weekly: Array.from({ length: weekCount }, (_, i) => current.nextWeekPrescriptions?.[ex.shellKey]?.[i + 1]?.sets) }))
                    .filter(d => d.def && d.ex.shellKey !== option.ex.shellKey && fillable(current, d.ex) && d.weekly.every(Number.isFinite) && d.weekly[0] > 1
                        && publicRegionContribution(option.def, t.region) - publicRegionContribution(d.def, t.region) >= .2
                        && targets.every(u => !(publicRegionContribution(d.def, u.region) > 0)
                            || first.regions[u.region] - publicRegionContribution(d.def, u.region) >= desired[u.region] - EPS))
                    .sort((a, b) => b.weekly[0] - a.weekly[0] || a.ex.shellKey.localeCompare(b.ex.shellKey));
                for (const donor of donors) {
                    const traded = withoutSet(candidate, donor.ex.shellKey);
                    const tradedAfter = guards.allowed(before, current, traded, openAll);
                    if (!tradedAfter) continue;
                    taken = { candidate: traded, after: tradedAfter, key: option.ex.shellKey, from: donor.ex.shellKey, region: t.region };
                    break;
                }
                if (taken) break;
                blocked.add(option.ex.shellKey + ':' + t.region);
            }
            if (taken) break;
            // Nothing in the plan can take the set: bring back this region's lift from the hypertrophy block (the same
            // exercise, so its loads are known), on the day with the most room that trains the area, at two sets.
            const restored = restoreHypertrophyLift(t);
            if (restored) { taken = restored; break; }
        }
        if (!taken) break;
        current = taken.candidate; before = taken.after;
        steps.push({ key: taken.key, from: taken.from, added: taken.added, region: taken.region, program: current, audit: taken.after });
    }
    if (!steps.length) return program;
    // The engine audit is the expensive check (a six-day week searches day orders), so it runs on the finished plan;
    // a plan that adds a finding in any week gives back its last additions until it does not.
    let kept = steps.length;
    while (kept > 0 && regressed(steps[kept - 1].program)) kept = Math.max(0, kept - Math.max(1, Math.ceil(kept / 4)));
    if (!kept) return program;
    const final = steps[kept - 1], after = final.audit.weeks[0];
    const finalProgram = steps.slice(0, kept).some(s => s.added) ? asSavedEngineSource(final.program) : final.program;
    const tally = {};
    for (const s of steps.slice(0, kept)) { if (s.added) continue; tally[s.key] = (tally[s.key] || 0) + 1; if (s.from) tally[s.from] = (tally[s.from] || 0) - 1; }
    return { ...finalProgram, nextEngine: { ...finalProgram.nextEngine, strengthEntryDose: {
        retention: STRENGTH_ENTRY_RETENTION, comparison: 'last_hypertrophy_week_to_first_strength_week',
        target: Object.fromEntries(Object.entries(desired).map(([r, v]) => [r, Math.round(v * 10) / 10])),
        sets: Object.entries(tally).filter(([, delta]) => delta).map(([key, delta]) => ({ key, delta })),
        restored: steps.slice(0, kept).filter(s => s.added).map(s => ({ dayId: s.key.split(':')[0], exercise: s.added, region: s.region })),
        unresolved: targets.filter(t => desired[t.region] - after.regions[t.region] >= .5 || after.regions[t.region] < t.mev - EPS).map(t => t.region) } } };
}

export function rebalanceFocusedStrengthEntry(previous, program, legacyExercises = [], priorSnapshot = null) {
    // Focused regions first (their own, larger share), then every region's retained dose.
    return retainStrengthEntryDose(previous, rebalanceFocusedRegionsAtStrengthEntry(previous, program, legacyExercises, priorSnapshot),
        legacyExercises, priorSnapshot);
}
function rebalanceFocusedRegionsAtStrengthEntry(previous, program, legacyExercises = [], priorSnapshot = null) {
    if ((!priorSnapshot && previous?.nextEngine?.program?.phase !== 'hypertrophy_accumulation')
        || program?.nextEngine?.program?.phase !== 'strength_accumulation')
        return program;
    const context = snapshotContext(program, legacyExercises);
    if (context.request.preferences.volumeApproach === 'minimalist') return program;
    const prior = priorSnapshot ?? captureShellVolumeSnapshot(previous, workWeeks(previous), legacyExercises);
    if (!prior || prior.missing) return program;
    const targets = shellVolumeTargets(program, context.request);
    const prescriptions = createMusclePrescriptions(context.request, 'strength_accumulation');
    const focus = PUBLIC_MEV_REGIONS.filter(region => {
        const priority = context.request.goal.musclePriorities[PUBLIC_REGION_MUSCLE[region]];
        return ['high', 'specialization', 'primary'].includes(priority)
            && (prior.regions[region] || 0) >= 4;
    });
    if (!focus.length) return program;
    const preferred = Object.fromEntries(prescriptions.map(p => [p.muscle, p.preferred]));
    const desired = Object.fromEntries(focus.map(region => {
        const muscle = PUBLIC_REGION_MUSCLE[region];
        // Half of the actual last hypertrophy week, but capped against the
        // strength phase's useful preference. Back width and thickness are
        // separate; neither may consume the other's entire preference.
        const phaseAllowance = (Number(preferred[muscle]) || 0)
            * (muscle === 'back' ? .65 : 1.1);
        const upper = targets.find(t => t.region === region)?.mrv ?? 0;
        return [region, Math.min(prior.regions[region] * .5, phaseAllowance, upper)];
    }));
    const { current, changes } = allocateFocusedRegionSets(program, legacyExercises, context, targets, focus, desired);
    const after = captureShellVolumeSnapshot(current, 1, legacyExercises);
    const unresolved = focus.filter(region => after && after.regions[region] + .05 < desired[region]);
    return { ...current, nextEngine: { ...current.nextEngine,
        focusedStrengthDose: { status: unresolved.length ? 'limited' : changes.length ? 'improved' : 'unchanged',
            changes, unresolved, target: desired, sourceWeek: workWeeks(previous),
            comparison: 'last_hypertrophy_week_to_first_strength_week' } } };
}

/* A focus the lifter asked for has to show up as more work for that region. Priority only scales the engine's
   dose prior (x1.18 for "high"), so where the unfocused plan already sat above the focused target the focus
   bought nothing, and a different roll of the roster could even leave the region with less. Measured over 360
   focused builds: 129 unchanged, 15 lower. `unfocused` is the same request built without the focus; each
   focused region is raised toward that plan's week-1 dose plus FOCUS_MIN_GAIN effective sets, inside the
   region's ceiling, the session time and the engine audit (`accept` adds the caller's own check: the adapter
   passes the displayed clock). What cannot be reached is reported, never forced. */
const FOCUS_MIN_GAIN = 2;
export function ensureFocusedRegionDose(program, unfocused, legacyExercises = [], regions = [], accept = null) {
    if (!program?.nextEngine?.program || !unfocused?.nextEngine?.program) return program;
    const focus = [...new Set(regions)].filter(region => PUBLIC_MEV_REGIONS.includes(region));
    if (!focus.length) return program;
    const context = snapshotContext(program, legacyExercises);
    const base = captureShellVolumeSnapshot(unfocused, 1, legacyExercises);
    const now = captureShellVolumeSnapshot(program, 1, legacyExercises, null, context);
    if (!base || base.missing || !now || now.missing) return program;
    const targets = shellVolumeTargets(program, context.request);
    const gain = context.request.preferences.volumeApproach === 'minimalist' ? 1 : FOCUS_MIN_GAIN;
    const desired = Object.fromEntries(focus.map(region => {
        const upper = targets.find(t => t.region === region)?.mrv ?? 0;
        return [region, Math.max(now.regions[region] || 0, Math.min(upper, (base.regions[region] || 0) + gain))];
    }));
    const { current, changes } = allocateFocusedRegionSets(program, legacyExercises, context, targets, focus, desired, 3 * focus.length + 3, true, accept);
    const after = changes.length ? captureShellVolumeSnapshot(current, 1, legacyExercises) : now;
    const unresolved = focus.filter(region => after && after.regions[region] + .05 < desired[region]);
    return { ...current, nextEngine: { ...current.nextEngine,
        focusDose: { status: unresolved.length ? 'limited' : changes.length ? 'improved' : 'unchanged', changes, unresolved,
            target: desired, unfocused: Object.fromEntries(focus.map(region => [region, base.regions[region] || 0])) } } };
}

// Preserve executable strength-week continuity using spare accessory capacity.
// Only engine-owned cells are eligible. Each single-set addition must preserve
// regional/muscle ceilings, session time and the complete engine audit vector.
export function preserveStrengthWeekContinuity(previous, program, legacyExercises = [], priorSnapshot = null) {
    if ((!priorSnapshot && previous?.nextEngine?.program?.phase !== 'hypertrophy_accumulation')
        || program?.nextEngine?.program?.phase !== 'strength_accumulation') return program;
    const prior = priorSnapshot ?? captureShellVolumeSnapshot(previous, workWeeks(previous), legacyExercises);
    if (!prior || prior.missing) return program;
    const count = view => view.sessions.reduce((n, s) => n + s.exercises.reduce((m, e) => m + e.sets, 0), 0);
    const previousSets = count(prior), floor = Math.ceil(previousSets * .5);
    if (previousSets < 16) return program;
    const context = snapshotContext(program, legacyExercises);
    const targets = shellVolumeTargets(program, context.request);
    const prescriptions = createMusclePrescriptions(context.request, 'strength_accumulation');
    const audit = view => auditCounts(auditProgram({ ...program.nextEngine.program,
        sessions: view.sessions, events: view.events, muscleLedger: view.muscleLedger }, context.request));
    let current = program;
    const changes = [];
    for (let week = 1; week <= workWeeks(program); week++) {
        let before = captureShellVolumeSnapshot(current, week, legacyExercises, null, context);
        if (!before || before.missing) continue;
        // Adding at most the deficit is bounded and cannot overshoot the floor.
        for (let deficit = floor - count(before); deficit > 0; deficit--) {
            const baseline = audit(before);
            let accepted = null;
            const slots = before.sessions.flatMap(s => s.exercises)
                .filter(e => !STRENGTH.has(e.role) && e.sets < 5
                    && shellPrescriptionFieldOwner(current, current.overrides?.[e.shellKey] ?? {}, 'sets') !== 'user')
                .sort((a, b) => a.sets - b.sets || a.shellKey.localeCompare(b.shellKey));
            for (const exercise of slots) {
                const key = exercise.shellKey, cell = current.nextWeekPrescriptions?.[key]?.[week];
                if (!cell || !Number.isFinite(cell.sets)) continue;
                const candidate = { ...current, nextWeekPrescriptions: { ...current.nextWeekPrescriptions,
                    [key]: { ...current.nextWeekPrescriptions[key], [week]: { ...cell, sets: cell.sets + 1 } } } };
                const after = captureShellVolumeSnapshot(candidate, week, legacyExercises, null, context);
                if (!after || after.missing || count(after) !== count(before) + 1
                    || after.sessions.some(s => s.estimatedMinutes > s.maxMinutes + EPS)) continue;
                if (targets.some(t => after.regions[t.region] > Math.max(t.mrv, before.regions[t.region]) + EPS)) continue;
                if (prescriptions.some(p => after.muscleLedger[p.muscle].fractionalSets > Math.max(
                    publicMevInternalSafetyCeiling(context.request, 'strength_accumulation', p.muscle, p.upper) * 1.2,
                    before.muscleLedger[p.muscle].fractionalSets) + EPS)) continue;
                if (Object.entries(audit(after)).some(([key, n]) => n > (baseline[key] || 0))) continue;
                accepted = { candidate, after, key };
                break;
            }
            if (!accepted) break; // The caller rejects an unrepairable finalist.
            current = accepted.candidate;
            before = accepted.after;
            changes.push({ week, key: accepted.key, delta: 1 });
        }
    }
    if (!changes.length) return program;
    current = { ...current, nextEngine: { ...current.nextEngine,
        strengthWeekContinuity: { previousSets, minimumWeeklySets: floor, changes } } };
    return attachRegionalDose(current, legacyExercises, [
        ...(program.nextEngine.regionalDose?.changes ?? []),
        ...changes.map(({ week, key, delta }) => ({ week, sets: [{ key, delta }], reason: 'strength_week_continuity' }))
    ]);
}

/* Spare session time. The realizer sizes a week from its dose targets, and the band's lower edge was never a
   quota, so a lifter who chose 60-90 minutes could be handed 45-minute days of three or four lifts at two sets
   while every region sat well inside its range. A day that shows under the lower edge of the chosen band now
   takes work until it reaches the edge or nothing more fits inside the plan's own limits:
     1. a new plan's short day with fewer lifts than its band targets gets a lift for the region with the most
        room (extendSessionsTowardBand: the repair pass's own candidate rules, three sets);
     2. then lifts take sets, the smallest first (fillSessionsTowardBand).
   The limits, checked on every move:
     - the first week of a region stays at or under its modeled upper dose (the phase decides how much of the
       preferred-to-upper range a long session may use), and no week passes the region or muscle ceiling;
     - a lift starts the block on at most four sets and never passes five;
     - no day passes its limit on either clock, and the engine audit does not regress.
   Strength work, the lifter's own edits, minimalist plans and the named strength programs are left as they are.
   What stays short is reported, not forced: six 90-minute days hold more time than any lifter has productive
   work for, and the plan says how long it really runs. */
const FILL_PHASE_REACH = { hypertrophy_accumulation: 1, mixed_accumulation: .74, strength_accumulation: 0 };
const FILL_FIRST_WEEK_SETS = 4;
const FILL_REGION_GROWTH = 4 / 3;
const FILL_NEW_LIFT_SETS = 3;
const FILL_NEW_LIFT_MINUTES = 6;   // a day short by less than a lift's worth is closed with sets
const FILL_MINUTES_PER_SET = 2.5;
const FILL_NEW_LIFT_ATTEMPTS = 8;  // audited candidates per plan
const FILL_NEW_LIFT_SCREENED = 24; // candidates rebuilt and screened without the audit
const SET_CAP = 5;
// Filed under a region for the ledger, but not that region's main work: a tibialis raise does not fill a calf gap.
const FILL_SIDE_REGIONS = new Set(['tibialis', 'serratus']);
function bandFillGuards(program, legacyExercises, generatedRegions = null, { anyBand = false } = {}) {
    if (!program?.nextEngine?.program || !program.nextEngine.request || !Array.isArray(program.days)) return null;
    const band = sessionBandFor(program.config), phase = phaseOf(program), reach = FILL_PHASE_REACH[phase];
    if ((!anyBand && !(band.minMinutes > 0)) || reach == null || program.config?.percentScheme || STRENGTH_STRUCTURES.has(program.config?.split)) return null;
    const context = snapshotContext(program, legacyExercises), { request } = context;
    if (!anyBand && request.preferences.volumeApproach === 'minimalist') return null;
    const weekCount = workWeeks(program);
    const targets = shellVolumeTargets(program, request);
    const prescriptions = createMusclePrescriptions(request, phase);
    const muscleCeiling = Object.fromEntries(prescriptions.map(p => [p.muscle, publicMevInternalSafetyCeiling(request, phase, p.muscle, p.upper)]));
    // The modeled upper dose, and no more than a third (or two sets) over what the realizer gave the region: spare
    // time is spread over the week, not poured into whichever region has the highest ceiling.
    const firstWeekCeiling = Object.fromEntries(targets.map(t => {
        const modeled = t.mav + Math.max(0, t.mrv / 1.2 - t.mav) * reach, generated = generatedRegions?.[t.region];
        return [t.region, Number.isFinite(generated) ? Math.min(modeled, Math.max(generated * FILL_REGION_GROWTH, generated + 2)) : modeled];
    }));
    const displayed = (candidate, day, week, engineMinutes) => shellSessionMinutes(candidate, day, week) ?? Math.round(engineMinutes);
    const underDays = (candidate, audit) => candidate.days.map((day, di) => ({ day, di,
        minutes: displayed(candidate, day, 1, audit.weeks[0].sessions[di]?.estimatedMinutes) }))
        .filter(x => Number.isFinite(x.minutes) && x.minutes < band.minMinutes);
    // Dose and time are arithmetic and checked on every move. The engine audit can cost a quarter of a second
    // on a six-day week (it searches day orders), and generation runs on the phone, so the audit is run on the
    // finished plan, every work week (finalizeGeneratedShellVolume), and a plan that regresses is not taken.
    // `opening`: regions a movement-pattern floor is opening, which the first-week dose ceiling does not bind.
    // `giving`: accessory regions a pattern swap may take below their floor (the lift that made room).
    const allowed = (before, beforeProgram, candidate, opening = null, giving = null) => {
        const after = auditShellVolume(candidate, legacyExercises);
        if (after.missing || after.weeks.length !== weekCount) return null;
        for (let wi = 0; wi < weekCount; wi++) {
            const bw = before.weeks[wi], aw = after.weeks[wi];
            for (const t of targets) {
                const now = aw.regions[t.region], was = bw.regions[t.region];
                if ((now < Math.min(t.mev, was) - EPS && !giving?.includes(t.region)) || now > Math.max(t.mrv, was) + EPS) return null;
                if (wi === 0 && !opening?.includes(t.region) && now > Math.max(firstWeekCeiling[t.region], was) + EPS) return null;
            }
            for (const p of prescriptions) {
                const now = aw.muscleLedger[p.muscle].fractionalSets, was = bw.muscleLedger[p.muscle].fractionalSets;
                if (now > Math.max(muscleCeiling[p.muscle] * (wi === 0 ? 1 : 1.2), was) + EPS) return null;
            }
            for (let di = 0; di < aw.sessions.length; di++) {
                const s = aw.sessions[di], b = bw.sessions[di];
                if (s.estimatedMinutes > Math.max(s.maxMinutes, b.estimatedMinutes) + EPS) return null;
                const shown = displayed(candidate, candidate.days[di], wi + 1, s.estimatedMinutes);
                if (shown > Math.max(band.maxMinutes, displayed(beforeProgram, beforeProgram.days[di], wi + 1, b.estimatedMinutes))) return null;
            }
            // No new hard collision between consecutive days (the audit's recovery finding, without its search).
            if (assessWeeklyRecovery(aw.sessions, request).hardCollisions > assessWeeklyRecovery(bw.sessions, request).hardCollisions) return null;
        }
        return after;
    };
    return { band, phase, context, request, weekCount, targets, firstWeekCeiling, underDays, allowed };
}
const fillable = (program, ex) => !STRENGTH.has(ex.role)
    && shellPrescriptionFieldOwner(program, program.overrides?.[ex.shellKey] ?? {}, 'sets') !== 'user';

/* A week with no pulling at all. A two-day upper/lower strength plan came out as bench press and a lateral raise
   on the upper day, 20 minutes of its 40, with no row or pulldown anywhere: the muscle ledger credits the
   deadlift to the back, so every floor read as met while lats and upper back had no sets. The app's own
   week audit calls this an error (pattern-absent). A new plan with a whole pattern at zero gets one compound for
   it on the day with the most room that trains that area, under the same time, ceiling and audit limits as any
   other added lift. Named strength programs stay as written. */
const PATTERN_FLOORS = [
    { regions: ['lats', 'upper_back', 'biceps'], open: ['lats', 'upper_back'] },
    { regions: ['chest', 'side_delts', 'triceps'], open: ['chest'] },
    { regions: ['quads', 'hamstrings', 'glutes'], open: ['quads'] }
];
const PATTERN_MIN_SETS = .5, PATTERN_LIFT_SETS = 2;
const PATTERN_ACCESSORY_REGIONS = new Set(['side_delts', 'rear_delts', 'calves', 'core', 'biceps', 'triceps']);
export function ensureMovementPatterns(program, legacyExercises = []) {
    const untouched = { program, changes: [] };
    const guards = bandFillGuards(program, legacyExercises, null, { anyBand: true });
    if (!guards) return untouched;
    const { phase, request, targets } = guards;
    const engineContext = createEngineContext(request), policy = phasePolicyFor(phase);
    let current = program, before = auditShellVolume(program, legacyExercises);
    if (before.missing) return untouched;
    const changes = [];
    for (const floor of PATTERN_FLOORS) {
        const first = before.weeks[0];
        if (floor.regions.reduce((sum, region) => sum + (first.regions[region] || 0), 0) >= PATTERN_MIN_SETS) continue;
        const days = first.sessions.map((session, di) => ({ session, di, minutes: session.estimatedMinutes / Math.max(1, session.maxMinutes) }))
            .sort((a, b) => a.minutes - b.minutes);
        let tried = 0;
        search: for (const region of floor.open) {
            const t = targets.find(x => x.region === region);
            if (!t) continue;
            for (const { session } of days) {
                if (!(INTENT_MUSCLES[session.intent] ?? INTENT_MUSCLES.full).includes(t.muscle)) continue;
                const existing = session.exercises.map(e => ({ def: engineContext.exerciseById(e.exerciseId), role: e.role })).filter(x => x.def);
                const candidates = engineContext.candidatesForMuscle(t.muscle).filter(def => def.flags.compound
                    && publicRegionContribution(def, region) >= .999 && engineContext.equipmentEligible(def, session.day)
                    && !session.exercises.some(e => e.exerciseId === def.id))
                    .map(def => ({ def, worth: (def.suitability?.hypertrophy ?? 0) * .35 + (def.stability ?? 0) * .15
                        - exerciseRedundancyPenalty(def, 'hypertrophy_compound', existing) - exerciseSetupInefficiencyPenalty(def, existing)
                        - (def.fatigue?.systemic ?? 0) * .1 - (def.fatigue?.lowerBack ?? 0) * .2 - (def.technicalComplexity ?? 0) * .2 }))
                    .sort((a, b) => b.worth - a.worth || a.def.id.localeCompare(b.def.id)).slice(0, 3).map(x => x.def);
                for (const def of candidates) {
                    const legacy = resolveLegacyExercise({ exerciseId: def.id, name: def.name }, legacyExercises);
                    if (!legacy || nextExerciseIdForShellExercise(legacy) !== def.id) continue;
                    if (tried++ >= FILL_NEW_LIFT_ATTEMPTS) break search;
                    const role = 'hypertrophy_compound';
                    const exercise = { exerciseId: def.id, name: def.name, role, sets: PATTERN_LIFT_SETS, prescription: {
                        reps: repsForPhase(def, role, policy), rir: rirForPhase(role, policy), restSeconds: restForExercise(role, def) },
                        progression: progressionForExercise(def, role, policy, request.athlete.experience),
                        progressionStyle: progressionStyleForExercise(def, role, policy, request.athlete.experience) };
                    // Added outright where the day has the time; otherwise in place of one accessory lift whose
                    // regions are all small ones: a week with no pulling is the worse gap.
                    const makeRoom = [null, ...session.exercises.filter(ex => fillable(current, ex) && !ex.supersetGroup).filter(ex => {
                        const lift = engineContext.exerciseById(ex.exerciseId);
                        return lift && targets.every(x => !(publicRegionContribution(lift, x.region) >= .999) || PATTERN_ACCESSORY_REGIONS.has(x.region));
                    }).sort((a, b) => a.sets - b.sets)];
                    for (const out of makeRoom) {
                        const lift = out && engineContext.exerciseById(out.exerciseId);
                        const giving = lift ? targets.filter(x => publicRegionContribution(lift, x.region) > 0 && PATTERN_ACCESSORY_REGIONS.has(x.region)).map(x => x.region) : null;
                        const candidate = withSourcePrescriptions(rebuild(current, out ? [{ dayId: session.shellDayId, slot: out.shellSlot, remove: true }] : [], legacyExercises,
                            { dayId: session.shellDayId, legacy, exercise }, { deferAudit: true }), current);
                        const after = candidate && guards.allowed(before, current, candidate, floor.regions, giving);
                        if (!after) continue;
                        if (regressedCounts(auditCounts(withEngineAudit(candidate, request).nextEngine.audit), auditCounts(current.nextEngine.program.audit))) continue;
                        current = candidate; before = after;
                        changes.push({ sets: [], addedExercise: def.name, ...(out ? { replacedExercise: out.name } : {}), dayId: session.shellDayId, reason: 'movement_pattern' });
                        break search;
                    }
                }
            }
        }
    }
    return changes.length ? { program: current, changes } : untouched;
}

/* A focused muscle with a day to spare. The focus dose (ensureFocusedRegionDose) only adds sets to lifts already
   in the plan, so a focused region with one lift at its set cap stayed where it was while another day ran
   thirteen minutes short. A new plan's focused region that is still under its preferred dose gets one more lift
   for it, on the day with the most room that trains the area, under the same limits as any added lift (the
   region's own ceiling in every week, both clocks, no new collision, no audit regression). */
export function ensureFocusLifts(program, legacyExercises = [], focus = null) {
    const untouched = { program, changes: [] };
    if (!focus?.unfocused?.nextEngine?.program || !focus.regions?.length) return untouched;
    const guards = bandFillGuards(program, legacyExercises, null, { anyBand: true });
    const base = captureShellVolumeSnapshot(focus.unfocused, 1, legacyExercises);
    if (!guards || !base || base.missing) return untouched;
    const { phase, request, targets, context } = guards;
    const gain = request.preferences.volumeApproach === 'minimalist' ? 1 : FOCUS_MIN_GAIN;
    const focused = targets.filter(t => focus.regions.includes(t.region));
    if (!focused.length) return untouched;
    const engineContext = createEngineContext(request), policy = phasePolicyFor(phase);
    let current = program, before = auditShellVolume(program, legacyExercises);
    if (before.missing) return untouched;
    const changes = [];
    let tried = 0;
    for (const t of focused) {
        const first = before.weeks[0];
        // What the focus promises (the unfocused plan's dose plus the gain, inside the ceiling) and how far off it is.
        const gap = Math.min(t.mrv, (base.regions[t.region] || 0) + gain) - first.regions[t.region];
        if (gap < 1) continue;
        // Sets go to existing lifts first (ensureFocusedRegionDose). A lift is added when they cannot take them:
        // the region has a single direct lift, or every direct lift already starts on four sets.
        const direct = first.sessions.flatMap(session => session.exercises).filter(ex => {
            const def = context.exerciseMap.get(ex.exerciseId); return def && !STRENGTH.has(ex.role) && directlyTargetsPublicRegion(def, t.region); });
        if (direct.length >= 2 && direct.some(ex => ex.sets < FILL_FIRST_WEEK_SETS)) continue;
        const covered = new Set(first.sessions.flatMap(session => session.exercises).flatMap(ex => { const def = context.exerciseMap.get(ex.exerciseId); return def ? [...functionalCoverageUseKeys(def)] : []; }));
        const days = first.sessions.map(session => ({ session, room: session.maxMinutes - session.estimatedMinutes }))
            .filter(x => x.room >= FILL_NEW_LIFT_MINUTES && (INTENT_MUSCLES[x.session.intent] ?? INTENT_MUSCLES.full).includes(t.muscle))
            .sort((a, b) => b.room - a.room);
        search: for (const { session } of days) {
            const existing = session.exercises.map(e => ({ def: engineContext.exerciseById(e.exerciseId), role: e.role })).filter(x => x.def);
            if (existing.filter(x => directlyTargetsPublicRegion(x.def, t.region)).length >= 2) continue;
            const inPlan = new Set(first.sessions.flatMap(x => x.exercises.map(e => e.exerciseId)));
            // (half credit counts: for a lats focus with only a pulldown, the lift the week is missing is a row)
            const candidates = engineContext.candidatesForMuscle(t.muscle).filter(def => publicRegionContribution(def, t.region) >= .5
                && engineContext.equipmentEligible(def, session.day) && !FILL_SIDE_REGIONS.has(def.legacySubregion)
                && !session.exercises.some(e => e.exerciseId === def.id)
                && !avoidableExerciseOverlap(def, def.flags.compound ? 'hypertrophy_compound' : 'hypertrophy_isolation', existing, { priority: t.priority }))
                .map(def => ({ def, worth: publicRegionContribution(def, t.region) * .6 + (def.suitability?.hypertrophy ?? 0) * .35 + (def.stability ?? 0) * .15
                    - exerciseRedundancyPenalty(def, def.flags.compound ? 'hypertrophy_compound' : 'hypertrophy_isolation', existing)
                    - exerciseSetupInefficiencyPenalty(def, existing) - (inPlan.has(def.id) ? 1 : 0) - (def.fatigue?.systemic ?? 0) * .1
                    // a movement the region does not have yet first: more leg curls do not answer a missing hinge
                    + ([...functionalCoverageUseKeys(def)].some(key => !covered.has(key)) ? .8 : 0) }))
                .sort((a, b) => b.worth - a.worth || a.def.id.localeCompare(b.def.id)).slice(0, 3).map(x => x.def);
            for (const def of candidates) {
                const legacy = resolveLegacyExercise({ exerciseId: def.id, name: def.name }, legacyExercises);
                if (!legacy || nextExerciseIdForShellExercise(legacy) !== def.id) continue;
                if (tried++ >= FILL_NEW_LIFT_ATTEMPTS) break search;
                const role = def.flags.compound ? 'hypertrophy_compound' : 'hypertrophy_isolation';
                const exercise = { exerciseId: def.id, name: def.name, role, sets: PATTERN_LIFT_SETS, prescription: {
                    reps: repsForPhase(def, role, policy), rir: rirForPhase(role, policy), restSeconds: restForExercise(role, def) },
                    progression: progressionForExercise(def, role, policy, request.athlete.experience),
                    progressionStyle: progressionStyleForExercise(def, role, policy, request.athlete.experience) };
                const candidate = withSourcePrescriptions(rebuild(current, [], legacyExercises, { dayId: session.shellDayId, legacy, exercise }, { deferAudit: true }), current);
                const after = candidate && guards.allowed(before, current, candidate, [t.region]);
                if (!after) continue;
                if (regressedCounts(findingCounts(withEngineAudit(candidate, request).nextEngine.audit), findingCounts(current.nextEngine.program.audit))) continue;
                current = candidate; before = after;
                changes.push({ sets: [], addedExercise: def.name, dayId: session.shellDayId, reason: 'focus' });
                break search;
            }
        }
    }
    return changes.length ? { program: current, changes } : untouched;
}

export function extendSessionsTowardBand(program, legacyExercises = [], generatedRegions = null) {
    const untouched = { program, changes: [] };
    const guards = bandFillGuards(program, legacyExercises, generatedRegions);
    if (!guards) return untouched;
    const { band, phase, request, targets, firstWeekCeiling } = guards;
    const engineContext = createEngineContext(request), policy = phasePolicyFor(phase);
    let current = program, before = auditShellVolume(program, legacyExercises);
    if (before.missing) return untouched;
    const tried = new Set(), changes = [];
    let attempts = 0, screened = 0;
    for (let guard = 0; guard < 16 && attempts < FILL_NEW_LIFT_ATTEMPTS && screened < FILL_NEW_LIFT_SCREENED; guard++) {
        const first = before.weeks[0];
        let added = false;
        for (const { di, minutes } of guards.underDays(current, before)) {
            const session = first.sessions[di];
            const wanted = request.schedule.days.find(day => day.day === session?.day)?.targetExercises ?? 0;
            if (!session || session.exercises.length >= wanted) continue;
            // Lifts still under three sets are raised first; a lift is added for what that cannot close.
            const fromSets = session.exercises.filter(ex => fillable(current, ex))
                .reduce((sum, ex) => sum + Math.max(0, FILL_NEW_LIFT_SETS - ex.sets), 0) * FILL_MINUTES_PER_SET;
            if (band.minMinutes - minutes - fromSets < FILL_NEW_LIFT_MINUTES) continue;
            const intent = INTENT_MUSCLES[session.intent] ?? INTENT_MUSCLES.full;
            const existing = session.exercises.map(e => ({ def: engineContext.exerciseById(e.exerciseId), role: e.role })).filter(x => x.def);
            const inPlan = new Set(first.sessions.flatMap(s => s.exercises.map(e => e.exerciseId)));
            const directLifts = region => existing.filter(x => directlyTargetsPublicRegion(x.def, region)).length;
            // A region the day does not train directly yet comes first, then the one with the most room.
            const regions = targets.filter(t => intent.includes(t.muscle) && directLifts(t.region) < 2)
                .map(t => ({ t, direct: directLifts(t.region),
                    room: (firstWeekCeiling[t.region] - first.regions[t.region] - FILL_NEW_LIFT_SETS) / Math.max(firstWeekCeiling[t.region], EPS) }))
                .filter(x => x.room >= -EPS).sort((a, b) => a.direct - b.direct || b.room - a.room || a.t.region.localeCompare(b.t.region));
            const novice = request.athlete.experience === 'novice';
            // The realizer's own terms for a hypertrophy accessory: suited to the goal, stable, not a near-copy of
            // a lift already in the session or the week, cheap to set up next to what is there.
            const worth = def => {
                const role = def.flags.compound ? 'hypertrophy_compound' : 'hypertrophy_isolation';
                return (def.suitability?.hypertrophy ?? 0) * .35 + (def.stability ?? 0) * .15
                    - exerciseRedundancyPenalty(def, role, existing) - exerciseSetupInefficiencyPenalty(def, existing)
                    - (inPlan.has(def.id) ? 1 : 0) - (def.fatigue?.systemic ?? 0) * .1 - (def.technicalComplexity ?? 0) * (novice ? .3 : .1);
            };
            search: for (const { t } of regions) {
                const candidates = engineContext.candidatesForMuscle(t.muscle).filter(def => !tried.has(`${session.shellDayId}:${def.id}`)
                    && publicRegionContribution(def, t.region) >= .999 && engineContext.equipmentEligible(def, session.day)
                    && !FILL_SIDE_REGIONS.has(def.legacySubregion)
                    && !session.exercises.some(e => e.exerciseId === def.id)
                    && !avoidableExerciseOverlap(def, def.flags.compound ? 'hypertrophy_compound' : 'hypertrophy_isolation', existing, { priority: t.priority }))
                    .map(def => ({ def, worth: worth(def) })).sort((a, b) => b.worth - a.worth || a.def.id.localeCompare(b.def.id)).slice(0, 2).map(x => x.def);
                for (const def of candidates) {
                    tried.add(`${session.shellDayId}:${def.id}`);
                    // Cheap arithmetic first: every region the lift credits must have first-week room for it.
                    if (targets.some(x => first.regions[x.region] + FILL_NEW_LIFT_SETS * publicRegionContribution(def, x.region) > Math.max(firstWeekCeiling[x.region], first.regions[x.region]) + EPS)) continue;
                    const legacy = resolveLegacyExercise({ exerciseId: def.id, name: def.name }, legacyExercises);
                    if (!legacy || nextExerciseIdForShellExercise(legacy) !== def.id) continue;
                    if (screened++ >= FILL_NEW_LIFT_SCREENED) break search;
                    const role = def.flags.compound ? 'hypertrophy_compound' : 'hypertrophy_isolation';
                    const exercise = { exerciseId: def.id, name: def.name, role, sets: FILL_NEW_LIFT_SETS, prescription: {
                        reps: repsForPhase(def, role, policy), rir: rirForPhase(role, policy), restSeconds: restForExercise(role, def) },
                        progression: progressionForExercise(def, role, policy, request.athlete.experience),
                        progressionStyle: progressionStyleForExercise(def, role, policy, request.athlete.experience) };
                    const candidate = withSourcePrescriptions(rebuild(current, [], legacyExercises, { dayId: session.shellDayId, legacy, exercise }, { deferAudit: true }), current);
                    const after = candidate && guards.allowed(before, current, candidate);
                    if (!after) continue;
                    if (attempts++ >= FILL_NEW_LIFT_ATTEMPTS) break search;
                    if (regressedCounts(findingCounts(withEngineAudit(candidate, request).nextEngine.audit), findingCounts(current.nextEngine.program.audit))) continue;
                    current = candidate; before = after; added = true;
                    changes.push({ sets: [], addedExercise: def.name, dayId: session.shellDayId, reason: 'session_fill' });
                    break search;
                }
            }
            if (added) break; // the week's doses moved: re-read before the next day
        }
        if (!added) break;
    }
    return changes.length ? { program: current, changes } : untouched;
}
/* rebuild() reads its sessions from the live first week, so the engine source it stores would carry week-one
   effort and rest as the block's base, and every later projection of that source would shift them again. A new
   plan keeps the generated base prescription of every lift it already had; only the roster and sets change. */
function withSourcePrescriptions(candidate, source) {
    if (!candidate) return candidate;
    const sessions = candidate.nextEngine.program.sessions.map((session, di) => {
        const original = source.nextEngine.program.sessions[di];
        return { ...session, exercises: session.exercises.map(exercise => {
            const base = original?.exercises.find(x => x.exerciseId === exercise.exerciseId);
            return base ? { ...exercise, prescription: base.prescription, progression: base.progression ?? exercise.progression,
                progressionStyle: base.progressionStyle ?? exercise.progressionStyle } : exercise;
        }) };
    });
    candidate.nextEngine.program = { ...candidate.nextEngine.program, sessions };
    return candidate;
}
/* A generated program is plain JSON (the app saves and reloads it). A rebuilt engine source holds fresh objects
   whose optional fields are present as undefined, so the same program compared before and after a save would
   differ. Store it as the data a save would produce. */
const asSavedEngineSource = program => ({ ...program, nextEngine: { ...program.nextEngine,
    program: JSON.parse(JSON.stringify(program.nextEngine.program)),
    ...(program.nextEngine.audit ? { audit: JSON.parse(JSON.stringify(program.nextEngine.audit)) } : {}) } });
const regressedCounts = (counts, baseline) => Object.entries(counts).some(([key, n]) => n > (baseline[key] || 0));

export function fillSessionsTowardBand(program, legacyExercises = [], skipDayIds = null, generatedRegions = null) {
    const untouched = { program, changes: [] };
    const guards = bandFillGuards(program, legacyExercises, generatedRegions);
    if (!guards) return untouched;
    const { band, context, weekCount, targets, firstWeekCeiling } = guards;
    const withSets = (candidate, keys) => {
        const cells = { ...candidate.nextWeekPrescriptions };
        for (const key of keys) {
            cells[key] = { ...cells[key] };
            for (let w = 1; w <= weekCount; w++) cells[key][w] = { ...cells[key][w], sets: Math.max(cells[key][w].sets, Math.min(SET_CAP, cells[key][w].sets + 1)) };
        }
        return { ...candidate, nextWeekPrescriptions: cells };
    };
    let current = program, before = auditShellVolume(program, legacyExercises);
    if (before.missing || before.weeks.length !== weekCount) return untouched;
    const blocked = new Set(), changes = [];
    for (let round = 0; round < 48; round++) {
        const under = guards.underDays(current, before).filter(x => !skipDayIds?.has(x.day.id));
        if (!under.length) break;
        const first = before.weeks[0], pending = {};
        const picks = [];
        for (const { di } of under) {
            const session = first.sessions[di];
            const options = (session?.exercises ?? []).map(ex => ({ ex, def: context.exerciseMap.get(ex.exerciseId),
                weekly: Array.from({ length: weekCount }, (_, i) => current.nextWeekPrescriptions?.[ex.shellKey]?.[i + 1]?.sets) }))
                .filter(x => x.def && !blocked.has(x.ex.shellKey) && fillable(current, x.ex)
                    && x.weekly.every(Number.isFinite) && x.weekly[0] < FILL_FIRST_WEEK_SETS && x.weekly.some(sets => sets < SET_CAP))
                .map(x => {
                    // Room left, as a share of the ceiling, in the tightest region this lift trains.
                    let room = Infinity;
                    for (const t of targets) {
                        const credit = publicRegionContribution(x.def, t.region);
                        if (!(credit > 0)) continue;
                        const ceiling = Math.max(firstWeekCeiling[t.region], EPS);
                        room = Math.min(room, (ceiling - first.regions[t.region] - (pending[t.region] || 0) - credit) / ceiling);
                    }
                    return { ...x, room };
                })
                .filter(x => x.room >= -EPS)
                .sort((a, b) => a.weekly[0] - b.weekly[0] || b.room - a.room || a.ex.shellKey.localeCompare(b.ex.shellKey));
            const pick = options[0];
            if (!pick) continue;
            picks.push(pick.ex.shellKey);
            for (const t of targets) pending[t.region] = (pending[t.region] || 0) + publicRegionContribution(pick.def, t.region);
        }
        if (!picks.length) break;
        const together = withSets(current, picks), afterTogether = guards.allowed(before, current, together);
        if (afterTogether) { current = together; before = afterTogether; changes.push(...picks); continue; }
        for (const key of picks) {
            const one = withSets(current, [key]);
            const after = guards.allowed(before, current, one);
            if (after) { current = one; before = after; changes.push(key); }
            else blocked.add(key);
        }
    }
    if (!changes.length) return untouched;
    const tally = {};
    for (const key of changes) tally[key] = (tally[key] || 0) + 1;
    return { program: current, changes: Object.entries(tally).map(([key, delta]) => ({ sets: [{ key, delta }], reason: 'session_fill' })) };
}

/* Engine findings of every severity in each work week of the executable plan (weeks with the same sets share
   one audit). */
function weeklyAuditCounts(program, legacyExercises) {
    const request = requestOf(program), audit = auditShellVolume(program, legacyExercises), seen = new Map();
    return audit.weeks.map(week => {
        const key = JSON.stringify(week.sessions.map(s => s.exercises.map(e => [e.exerciseId, e.sets, e.supersetGroup ? 1 : 0])));
        if (!seen.has(key)) seen.set(key, findingCounts(auditProgram({ ...program.nextEngine.program,
            sessions: week.sessions, events: week.events, muscleLedger: week.muscleLedger }, request)));
        return seen.get(key);
    });
}

/* A day that still shows over its limit after every tighter build was refused (app-shell-adapter referee) is
   often next to a day with room: the engine clock saw 13 and 20 minutes where the lifter is shown 12 and 22.
   Moving a whole lift is dose-neutral, so nothing has to be given up for it: one accessory goes from the over
   day to the shortest day that trains the same muscles, when that brings the week's overshoot down, no day
   newly passes its limit, protected coverage is kept and the engine audit does not regress in any week. */
const MOVE_CANDIDATES = 6;
const acrossSessions = counts => {
    const total = {};
    for (const [key, n] of Object.entries(counts)) { const [code, , severity] = key.split(':'); total[`${code}:${severity}`] = (total[`${code}:${severity}`] || 0) + n; }
    return total;
};
export function moveLiftsOffOverDays(program, legacyExercises = [], maxMinutes = null) {
    if (!program?.nextEngine?.program || !program.nextEngine.request || !Number.isFinite(maxMinutes)) return program;
    if (program.config?.percentScheme || STRENGTH_STRUCTURES.has(program.config?.split)) return program;
    const request = requestOf(program), engineContext = createEngineContext(request), weekCount = workWeeks(program);
    const shownOf = candidate => candidate.days.map(day => {
        let worst = -Infinity;
        for (let w = 1; w <= weekCount; w++) { const m = shellSessionMinutes(candidate, day, w); if (m == null) return null; worst = Math.max(worst, m); }
        return worst;
    });
    const overOf = shown => shown.reduce((sum, m) => sum + Math.max(0, m - maxMinutes), 0);
    let current = program, shown = shownOf(program), tried = 0;
    if (shown.some(m => m == null) || overOf(shown) <= 0) return program;
    let baselineCounts = null;
    for (let move = 0; move < 3 && overOf(shown) > 0 && tried < MOVE_CANDIDATES; move++) {
        const first = captureShellVolumeSnapshot(current, 1, legacyExercises);
        if (!first || first.missing) break;
        const quality = shellPlanQuality(current, legacyExercises);
        const sources = shown.map((m, di) => ({ di, m })).filter(x => x.m > maxMinutes).sort((a, b) => b.m - a.m);
        const destinations = shown.map((m, di) => ({ di, m })).filter(x => x.m < maxMinutes).sort((a, b) => a.m - b.m);
        let accepted = null;
        search: for (const src of sources) {
            const from = first.sessions[src.di];
            const movers = from.exercises.filter(ex => fillable(current, ex) && !ex.supersetGroup).sort((a, b) => a.sets - b.sets);
            for (const mover of movers) for (const dst of destinations) {
                const to = first.sessions[dst.di], def = engineContext.exerciseById(mover.exerciseId);
                const base = current.nextEngine.program.sessions[src.di]?.exercises.find(x => x.exerciseId === mover.exerciseId);
                if (!def || !base || to.exercises.some(e => e.exerciseId === mover.exerciseId)) continue;
                const intent = INTENT_MUSCLES[to.intent] ?? INTENT_MUSCLES.full;
                if (!primaryDirectMuscles(def).every(muscle => intent.includes(muscle)) || !engineContext.equipmentEligible(def, to.day)) continue;
                const existing = to.exercises.map(e => ({ def: engineContext.exerciseById(e.exerciseId), role: e.role })).filter(x => x.def);
                if (avoidableExerciseOverlap(def, mover.role, existing, {})) continue;
                const legacy = resolveLegacyExercise({ exerciseId: def.id, name: def.name }, legacyExercises);
                if (!legacy || nextExerciseIdForShellExercise(legacy) !== def.id) continue;
                if (tried++ >= MOVE_CANDIDATES) break search;
                const { supersetGroup, shellKey, shellSlot, shellId, ...exercise } = base;
                const rebuilt = withSourcePrescriptions(rebuild(current, [{ dayId: from.shellDayId, slot: mover.shellSlot, remove: true }], legacyExercises,
                    { dayId: to.shellDayId, legacy, exercise }, { deferAudit: true }), current);
                if (!rebuilt) continue;
                let candidate;
                try { candidate = enforceShellWeekCapacity(reconcileShellWeekDose(rebuilt, legacyExercises).program, legacyExercises).program; }
                catch { continue; } // the moved week has no legal rounding: not a move
                const after = shownOf(candidate);
                if (after.some(m => m == null) || overOf(after) >= overOf(shown) || after.some((m, di) => m > Math.max(maxMinutes, shown[di]))) continue;
                const audit = auditShellVolume(candidate, legacyExercises);
                if (audit.missing || audit.issues.length > auditShellVolume(current, legacyExercises).issues.length) continue;
                if (audit.weeks.some(week => week.sessions.some(x => x.estimatedMinutes > x.maxMinutes + EPS))) continue;
                if (!shellPlanQualityKept(quality, shellPlanQuality(candidate, legacyExercises))) continue;
                // A finding that travels with the lift is not a new one: compare by code, not by session.
                baselineCounts ??= weeklyAuditCounts(program, legacyExercises).map(acrossSessions);
                const counts = weeklyAuditCounts(candidate, legacyExercises).map(acrossSessions);
                if (counts.length !== baselineCounts.length || counts.some((week, wi) => regressedCounts(week, baselineCounts[wi]))) continue;
                accepted = { candidate: withEngineAudit(candidate, request), after, lift: def.name, from: from.shellDayId, to: to.shellDayId };
                break search;
            }
        }
        if (!accepted) break;
        current = { ...accepted.candidate, nextEngine: { ...accepted.candidate.nextEngine,
            displayMoves: [...(accepted.candidate.nextEngine.displayMoves ?? []), { exercise: accepted.lift, from: accepted.from, to: accepted.to }] } };
        shown = accepted.after;
    }
    return current === program ? program : asSavedEngineSource(current);
}

/* The weekly set ramp is planned on the engine clock, so a day that shows 20 minutes in week one could show 22
   in week three once a set is added, on a 20-minute limit. A day-week still over its displayed limit after the
   referee and the move gives back one set at a time in that week only: a set the ramp added first, then the
   largest lift, never a lift's last set, never strength work or the lifter's own edits, and never a set that
   takes a region under its floor. The engine audit must not regress in any week, or the plan is left alone. */
export function holdWeeksToDisplayedLimit(program, legacyExercises = [], maxMinutes = null) {
    if (!program?.nextEngine?.program || !program.nextEngine.request || !Number.isFinite(maxMinutes)) return program;
    if (program.config?.percentScheme || STRENGTH_STRUCTURES.has(program.config?.split)) return program;
    const weekCount = workWeeks(program), request = requestOf(program);
    const targets = shellVolumeTargets(program, request);
    let current = program, before = auditShellVolume(program, legacyExercises);
    if (before.missing || before.weeks.length !== weekCount) return program;
    const given = [];
    for (let wi = 0; wi < weekCount; wi++) for (let di = 0; di < current.days.length; di++) {
        for (let guard = 0; guard < 6; guard++) {
            const shown = shellSessionMinutes(current, current.days[di], wi + 1);
            if (shown == null || shown <= maxMinutes) break;
            const session = before.weeks[wi].sessions[di];
            const options = session.exercises.filter(ex => fillable(current, ex) && ex.sets >= 2)
                .map(ex => ({ ex, ramped: ex.sets - (current.nextWeekPrescriptions[ex.shellKey]?.[1]?.sets ?? ex.sets) }))
                .sort((a, b) => (b.ramped > 0) - (a.ramped > 0) || b.ex.sets - a.ex.sets || a.ex.shellKey.localeCompare(b.ex.shellKey));
            let accepted = false;
            for (const { ex } of options) {
                const cells = { ...current.nextWeekPrescriptions, [ex.shellKey]: { ...current.nextWeekPrescriptions[ex.shellKey] } };
                cells[ex.shellKey][wi + 1] = { ...cells[ex.shellKey][wi + 1], sets: ex.sets - 1 };
                const candidate = { ...current, nextWeekPrescriptions: cells };
                const after = auditShellVolume(candidate, legacyExercises);
                if (after.missing) continue;
                // A region keeps its floor, or at least what week one (which the plan was accepted on) gives it.
                if (targets.some(t => { const now = after.weeks[wi].regions[t.region];
                    return now < Math.min(t.mev, before.weeks[wi].regions[t.region]) - EPS && now < before.weeks[0].regions[t.region] - EPS; })) continue;
                current = candidate; before = after; accepted = true;
                given.push({ key: ex.shellKey, week: wi + 1 });
                break;
            }
            if (!accepted) break;
        }
    }
    if (!given.length) return program;
    const counts = weeklyAuditCounts(current, legacyExercises).map(acrossSessions), baseline = weeklyAuditCounts(program, legacyExercises).map(acrossSessions);
    if (counts.length !== baseline.length || counts.some((week, wi) => regressedCounts(week, baseline[wi]))) return program;
    return { ...current, nextEngine: { ...current.nextEngine, displayHeld: given } };
}

/** `extend`: a new plan (a standalone program or a cycle's entry block) may also take a lift on a short day.
 *  Later cycle blocks keep the roster they were given. `fill: false` returns the plan as generated (the control
 *  the fill's gate measures against). */
export function finalizeGeneratedShellVolume(program, legacyExercises = [], { extend = false, fill = true, focus = null } = {}) {
    if (!program?.nextEngine?.program || !program.nextEngine.request) return program;
    const finish = (start, fill, skipDayIds = null) => {
        const result = reconcileShellWeekDose(start.program, legacyExercises);
        const capacity = enforceShellWeekCapacity(result.program, legacyExercises);
        const filled = fill ? fillSessionsTowardBand(capacity.program, legacyExercises, skipDayIds, generatedRegions) : { program: capacity.program, changes: [] };
        return { program: filled.program, added: start.changes, sets: filled.changes,
            changes: [...start.changes, ...result.changes, ...capacity.changes, ...filled.changes] };
    };
    const asGenerated = { program, changes: [] };
    const generatedRegions = captureShellVolumeSnapshot(program, 1, legacyExercises)?.regions ?? null;
    let plain = null, plainCounts = null;
    // Findings the candidate has and the plan as generated does not: null when there are none.
    const newFindings = candidate => {
        if (!candidate.added.length && !candidate.sets.length) return null;
        const counts = weeklyAuditCounts(candidate.program, legacyExercises);
        if (counts.every(week => !Object.keys(week).length)) return null;
        plain ??= finish(asGenerated, false);
        plainCounts ??= weeklyAuditCounts(plain.program, legacyExercises);
        const keys = counts.flatMap((week, wi) => Object.keys(week).filter(key => week[key] > (plainCounts[wi]?.[key] || 0)));
        return keys.length || counts.length !== plainCounts.length ? keys : null;
    };
    // The fullest plan first. One the engine audit likes less than the plan as generated is retried without
    // the set changes on the days its new findings name, then dropped for the next (sets only, then as generated).
    const extended = () => {
        const patterned = ensureMovementPatterns(program, legacyExercises);
        const focused = ensureFocusLifts(patterned.program, legacyExercises, focus);
        const lifted = extendSessionsTowardBand(focused.program, legacyExercises, generatedRegions);
        return { program: lifted.program, changes: [...patterned.changes, ...focused.changes, ...lifted.changes] };
    };
    const starts = fill ? [...(extend ? [extended] : []), () => asGenerated] : [];
    let chosen = null;
    for (const startOf of starts) {
        const start = startOf();
        let candidate = finish(start, true), findings = newFindings(candidate);
        if (findings) {
            const sessionIds = new Set(findings.map(key => key.split(':')[1]).filter(Boolean));
            const sessions = candidate.program.nextEngine.program.sessions;
            const blamed = new Set(candidate.program.days.filter((_, di) => sessionIds.has(sessions[di]?.id)).map(day => day.id));
            if (blamed.size && findings.every(key => key.split(':')[1])) {
                candidate = finish(start, true, blamed);
                findings = newFindings(candidate);
            }
        }
        if (!findings) { chosen = candidate; break; }
    }
    chosen ??= plain ?? finish(asGenerated, false);
    const finished = attachRegionalDose(chosen.added.length ? asSavedEngineSource(chosen.program) : chosen.program, legacyExercises, chosen.changes);
    if (!chosen.added.length && !chosen.sets.length) return finished;
    const guards = bandFillGuards(finished, legacyExercises, null, { anyBand: true });
    if (!guards) return finished;
    const remaining = guards.underDays(finished, auditShellVolume(finished, legacyExercises)).map(x => ({ dayId: x.day.id, minutes: x.minutes }));
    return { ...finished, nextEngine: { ...finished.nextEngine, sessionFill: {
        status: remaining.length ? 'limited' : 'filled', minimumMinutes: guards.band.minMinutes,
        added: chosen.added.map(change => ({ dayId: change.dayId, exercise: change.addedExercise, reason: change.reason, ...(change.replacedExercise ? { replaced: change.replacedExercise } : {}) })),
        sets: chosen.sets.flatMap(change => change.sets), remaining } } };
}
function attachRegionalDose(program, legacyExercises, changes) {
    const after = auditShellVolume(program, legacyExercises);
    return { ...program, nextEngine: { ...program.nextEngine,
        regionalDose: { status: after.issues.length ? 'partial' : changes.length ? 'success' : 'unchanged',
            changed: changes.length > 0, changes,
            remaining: after.issues.map(i => ({ region: i.region, status: i.status, actual: i.v,
                minimum: i.mev, upper: i.mrv, week: i.week })), timeIssues: [] } } };
}

/** Verified transaction: existing set allocation, then reallocation, then one compatible new
 * movement. Every proposal uses the same public region ledger and complete engine safety audit.
 * Failed proposals are discarded; a partial repair is reported as partial, never as success. */
export function repairShellVolume(program, legacyExercises = [], options = {}) {
    const preserveRoster = !!options.preserveRoster;
    const before = auditShellVolume(program, legacyExercises);
    if (before.missing) return { program, status: 'unable', changed: false, before, after: before, message: 'Prescription data is incomplete. Rebuild this plan before fixing its volume.' };
    if (!before.issues.length && before.weeks.every(w => w.sessions.every(s => s.estimatedMinutes <= s.maxMinutes)))
        return { program, status: 'unchanged', changed: false, before, after: before, message: 'Weekly volume is already in range for this block.' };
    // Repair a rounded week before changing the base sets for the entire block. The same exact-week
    // transaction handles fresh generation and the user's saved-plan Auto-fix action.
    const weekly = reconcileShellWeekDose(program, legacyExercises);
    let current = weekly.program, audit = weekly.after;
    const changes = [...weekly.changes];
    const request = requestOf(program), context = createEngineContext(request), phase = phaseOf(program);
    const initialSessions = baseSessions(program, legacyExercises);
    const initialEvents = createTrainingSetEvents(initialSessions, request.customExercises);
    const initialEngineAudit = auditProgram({ ...program.nextEngine.program, sessions: initialSessions,
        events: initialEvents, muscleLedger: deriveMuscleLedger(initialEvents) }, request);
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
            const recipients = slots.filter(x => x.def && !STRENGTH.has(x.e.role) && publicRegionContribution(x.def, issue.region) > 0)
                .sort((a, b) => publicRegionContribution(b.def, issue.region) - publicRegionContribution(a.def, issue.region) || a.e.sets - b.e.sets);
            for (const r of recipients) {
                consider([{ dayId: r.dayId, slot: r.slot, delta: issue.status === 'under' ? 1 : -1 }]);
                // Whole-week rounding can absorb a single base set without increasing the lowest
                // week. Test a bounded two-set step before interpreting that as exhausted headroom.
                if (issue.status === 'under' && r.e.sets <= 3)
                    consider([{ dayId: r.dayId, slot: r.slot, delta: 2 }]);
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
                    const exercise = { exerciseId: def.id, name: def.name, role, sets: 3,
                        ...(request.preferences.volumeApproach === 'minimalist' ? { workingSetCap: 3 } : {}), prescription: {
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
        if (!best) break;
        current = best.candidate; audit = best.after;
        changes.push({ sets: best.ops, addedExercise: best.addition?.exercise.name });
    }
    // A base repair regenerates cells in a touched session. Finish exact-week reconciliation only
    // after base repair stops so those projections cannot undo a successful earlier correction.
    const finishing = current !== weekly.program ? reconcileShellWeekDose(current, legacyExercises) : weekly;
    current = finishing.program;
    if (finishing !== weekly) changes.push(...finishing.changes);
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
