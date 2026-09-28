import { auditProgram } from '../next-engine/arbiter.js';
import { createTrainingSetEvents } from '../next-engine/events.js';
import { createExerciseMap } from '../next-engine/exercise-db.js';
import { buildProgramExplainability } from '../next-engine/explainability.js';
import { deriveMuscleLedger } from '../next-engine/ledgers.js';
import { createMusclePrescriptions, normalizeRequest } from '../next-engine/prescription.js';
import { finalizePlannedSession, optimizeSetupAwareSessionSequence } from '../next-engine/realizer.js';
import { INTENT_MUSCLES } from '../next-engine/topology.js';
import { assessWeeklyRecovery } from '../next-engine/weekly-recovery.js';
import { SHADOW_ENGINE_VERSION, SHADOW_MAX_CANDIDATES_PER_ROUND, SHADOW_MAX_ROUNDS } from './config.js';
import { scoreShadowProgram } from './scoring.js';
const PRIORITY_WEIGHT = { maintenance: 0, normal: 1, high: 3, specialization: 5, primary: 6 };
const HIGH_PRIORITY = new Set(['high', 'specialization', 'primary']);
const FREQUENCY_MOVABLE_ROLES = new Set(['hypertrophy_compound', 'hypertrophy_isolation', 'specialization']);
const LIFT_PRIORITY_WEIGHT = { maintenance: 0, normal: .2, high: .75, specialization: .9, primary: 1 };
function normalize(input) { return input.goal?.hypertrophyWeight !== undefined ? input : normalizeRequest(input); }
function cloneSessions(program) { return structuredClone(program.sessions); }
function directMuscles(def) { return def ? Object.entries(def.muscles).filter(([, c]) => c.credit >= 1).map(([m]) => m) : []; }
function intentAllows(session, muscle) { return (INTENT_MUSCLES[session.intent] ?? INTENT_MUSCLES.full).includes(muscle); }
function warningCount(program) { return program.audit.findings.filter(f => f.severity === 'warning').length; }
function relevantLiftSpecificity(def, request) {
    if (!def)
        return 0;
    let best = 0;
    for (const [lift, priority] of Object.entries(request.goal.liftPriorities)) {
        best = Math.max(best, (def.liftSpecificity?.[lift] ?? 0) * LIFT_PRIORITY_WEIGHT[priority]);
    }
    return best;
}
function passesIndependentQualityFloor(source, program, request) {
    // A shadow candidate may improve the experimental objective, but it cannot buy that score by
    // creating new coach/function warnings or worsening the already-realized weekly recovery layout.
    // This is deliberately independent from scoreShadowProgram so the optimizer cannot grade its own
    // tradeoffs as acceptable simply because the same objective assigns them more points.
    if (warningCount(program) > warningCount(source))
        return false;
    const before = assessWeeklyRecovery(source.sessions, request), after = assessWeeklyRecovery(program.sessions, request);
    if (after.hardCollisions > before.hardCollisions)
        return false;
    if (after.score > before.score + .0001)
        return false;
    return true;
}
export function rebuildShadowProgram(source, sessions, request, extraRationale = []) {
    const finalized = sessions.map(s => finalizePlannedSession({ ...s, exercises: s.exercises.map(ex => ({ ...ex, prescription: { ...ex.prescription } })) }, request));
    const exerciseMap = createExerciseMap(request.customExercises);
    const ordered = finalized.map(s => optimizeSetupAwareSessionSequence(s, exerciseMap, request));
    const events = createTrainingSetEvents(ordered, request.customExercises);
    const muscleLedger = deriveMuscleLedger(events);
    const base = { ...source, engineVersion: SHADOW_ENGINE_VERSION, sessions: ordered, events, muscleLedger, rationale: [...source.rationale, ...extraRationale] };
    delete base.audit;
    delete base.explainability;
    const audit = auditProgram(base, request);
    const withAudit = { ...base, audit };
    return { ...withAudit, explainability: buildProgramExplainability(withAudit, request) };
}
function candidate(id, source, sessions, request, changes, continuityIds) {
    try {
        const program = rebuildShadowProgram(source, sessions, request, changes.map(c => `Shadow 0.62: ${c.summary}`));
        if (program.audit.result !== 'pass' || !passesIndependentQualityFloor(source, program, request))
            return null;
        return { id, program, score: scoreShadowProgram(program, request, continuityIds), changes };
    }
    catch {
        return null;
    }
}
function specializationReorder(source, request, continuityIds) {
    const map = createExerciseMap(request.customExercises);
    let changed = false;
    const sessions = cloneSessions(source).map(session => {
        const strength = session.exercises.filter(ex => ex.role === 'primary_strength' || ex.role === 'secondary_strength' || ex.role === 'strength_support');
        const rest = session.exercises.filter(ex => !strength.includes(ex));
        const score = (ex) => Math.max(0, ...directMuscles(map.get(ex.exerciseId)).map(m => PRIORITY_WEIGHT[request.goal.musclePriorities[m] ?? 'normal'])) + (ex.role === 'hypertrophy_compound' ? .15 : 0);
        const sorted = [...rest].sort((a, b) => score(b) - score(a));
        const next = [...strength, ...sorted];
        if (next.some((ex, i) => ex.exerciseId !== session.exercises[i]?.exerciseId))
            changed = true;
        return { ...session, exercises: next };
    });
    if (!changed)
        return null;
    return candidate('specialization-order', source, sessions, request, [{ type: 'specialization_order', summary: 'Moved priority-muscle work earlier without displacing strength anchors.' }], continuityIds);
}
function setCounterfactuals(source, request, continuityIds, previousProgram) {
    const out = [];
    const map = createExerciseMap(request.customExercises);
    const prescriptions = createMusclePrescriptions(request, source.phase);
    const pMap = new Map(prescriptions.map(p => [p.muscle, p]));
    const ledger = source.muscleLedger;
    const exerciseValue = (ex) => directMuscles(map.get(ex.exerciseId)).reduce((v, m) => {
        const p = pMap.get(m);
        if (!p)
            return v;
        const gap = Math.max(0, p.preferred - (ledger[m]?.fractionalSets ?? 0));
        return v + PRIORITY_WEIGHT[p.priority] * (1 + Math.min(3, gap));
    }, 0);
    for (const [si, session] of source.sessions.entries()) {
        for (const [ei, ex] of session.exercises.entries()) {
            const cap = request.preferences.volumeApproach === 'minimalist' ? 3 : 5;
            if (ex.sets >= cap)
                continue;
            if (session.estimatedMinutes >= session.maxMinutes)
                continue;
            const sessions = cloneSessions(source);
            sessions[si].exercises[ei].sets += 1;
            const strengthTransition = Boolean(previousProgram) && (source.phase === 'strength_accumulation' || source.phase === 'intensification' || source.phase === 'peak') && (ex.role === 'primary_strength' || ex.role === 'secondary_strength' || ex.role === 'strength_support');
            const c = candidate(`add:${si}:${ei}`, source, sessions, request, [{ type: strengthTransition ? 'phase_transition' : 'time_reallocation', sessionId: session.id, summary: strengthTransition ? `Promoted one set of ${ex.name} because the target phase can use more specific work inside the existing time band.` : `Tested one more set of ${ex.name} against the full weekly audit.` }], continuityIds);
            if (c)
                out.push(c);
            if (out.length >= SHADOW_MAX_CANDIDATES_PER_ROUND)
                return out;
        }
        const ranked = session.exercises.map((ex, i) => ({ ex, i, value: exerciseValue(ex) })).sort((a, b) => b.value - a.value);
        const receiver = ranked[0];
        const donor = [...ranked].reverse().find(x => x.i !== receiver?.i && x.ex.sets > 1 && x.value + 1 < receiver.value);
        if (receiver && donor) {
            const cap = request.preferences.volumeApproach === 'minimalist' ? 3 : 5;
            if (receiver.ex.sets < cap) {
                const sessions = cloneSessions(source);
                sessions[si].exercises[receiver.i].sets++;
                sessions[si].exercises[donor.i].sets--;
                const c = candidate(`swap:${si}:${donor.i}:${receiver.i}`, source, sessions, request, [{ type: 'counterfactual_swap', sessionId: session.id, summary: `Repriced one set from ${donor.ex.name} to ${receiver.ex.name} instead of filling time by list order.` }], continuityIds);
                if (c)
                    out.push(c);
            }
        }
    }
    return out;
}
function specializationDoseCounterfactuals(source, request, continuityIds) {
    const out = [];
    const map = createExerciseMap(request.customExercises);
    const priorityMuscles = Object.entries(request.goal.musclePriorities).filter(([, p]) => p === 'specialization' || p === 'primary');
    for (const [muscle] of priorityMuscles) {
        for (const [si, session] of source.sessions.entries()) {
            const receivers = session.exercises.map((ex, i) => ({ ex, i, credit: map.get(ex.exerciseId)?.muscles[muscle]?.credit ?? 0 })).filter(x => x.credit >= 1 && x.ex.sets < (request.preferences.volumeApproach === 'minimalist' ? 3 : 5)).sort((a, b) => b.credit - a.credit);
            if (!receivers.length)
                continue;
            const receiver = receivers[0];
            const donors = session.exercises.map((ex, i) => ({ ex, i, muscles: directMuscles(map.get(ex.exerciseId)) })).filter(x => x.i !== receiver.i && x.ex.sets > 1 && !x.muscles.includes(muscle)).map(x => ({ ...x, maxPriority: Math.max(0, ...x.muscles.map(m => PRIORITY_WEIGHT[request.goal.musclePriorities[m] ?? 'normal'])) })).filter(x => x.maxPriority <= PRIORITY_WEIGHT.normal).sort((a, b) => a.maxPriority - b.maxPriority || b.ex.sets - a.ex.sets);
            const donor = donors[0];
            if (!donor)
                continue;
            const sessions = cloneSessions(source);
            sessions[si].exercises[receiver.i].sets++;
            sessions[si].exercises[donor.i].sets--;
            const c = candidate(`specialization-dose:${muscle}:${si}:${donor.i}:${receiver.i}`, source, sessions, request, [{ type: 'counterfactual_swap', muscle, sessionId: session.id, summary: `Reallocated one set from ${donor.ex.name} to ${receiver.ex.name} so ${muscle.replaceAll('_', ' ')} specialization changes the weekly dose, not just the label.` }], continuityIds);
            if (c)
                out.push(c);
            if (out.length >= SHADOW_MAX_CANDIDATES_PER_ROUND)
                return out;
        }
    }
    return out;
}
function frequencyCounterfactuals(source, request, continuityIds) {
    const out = [];
    const map = createExerciseMap(request.customExercises);
    const priorities = Object.entries(request.goal.musclePriorities).filter(([, p]) => HIGH_PRIORITY.has(p));
    for (const [muscle, priority] of priorities) {
        const desired = request.schedule.days.length >= 5 ? (priority === 'high' ? 2 : 3) : request.schedule.days.length >= 3 ? 2 : 1;
        const hitSessions = source.sessions.filter(s => s.exercises.some(ex => (map.get(ex.exerciseId)?.muscles[muscle]?.credit ?? 0) >= 1));
        if (hitSessions.length >= desired)
            continue;
        const donor = hitSessions.flatMap(s => s.exercises.map(ex => ({ s, ex })))
            .filter(x => (map.get(x.ex.exerciseId)?.muscles[muscle]?.credit ?? 0) >= 1 && x.ex.sets >= 3 && FREQUENCY_MOVABLE_ROLES.has(x.ex.role))
            .sort((a, b) => b.ex.sets - a.ex.sets)[0];
        if (!donor?.ex)
            continue;
        const sourceIndex = source.sessions.findIndex(s => s.id === donor.s.id);
        const donorIndex = donor.s.exercises.findIndex(e => e.exerciseId === donor.ex.exerciseId);
        const targets = source.sessions.filter(s => !hitSessions.some(h => h.id === s.id) && intentAllows(s, muscle)).sort((a, b) => a.estimatedMinutes - b.estimatedMinutes);
        for (const target of targets.slice(0, 2)) {
            const targetIndex = source.sessions.findIndex(s => s.id === target.id);
            const sessions = cloneSessions(source);
            const moved = { ...sessions[sourceIndex].exercises[donorIndex], sets: 1, prescription: { ...sessions[sourceIndex].exercises[donorIndex].prescription } };
            // A moved one-set exposure is a frequency tool, not a duplicate intensity-technique or
            // cross-session superset. Those annotations belong to the original session only.
            delete moved.advancedTechnique;
            delete moved.supersetGroup;
            sessions[sourceIndex].exercises[donorIndex].sets -= 1;
            if (sessions[targetIndex].exercises.some(e => e.exerciseId === moved.exerciseId))
                continue;
            sessions[targetIndex].exercises.push(moved);
            const c = candidate(`freq:${muscle}:${sourceIndex}:${targetIndex}`, source, sessions, request, [{ type: 'specialization_frequency', muscle, sessionId: target.id, summary: `Spread one ${muscle.replaceAll('_', ' ')} set to another compatible day instead of concentrating the full dose on one exposure.` }], continuityIds);
            if (c)
                out.push(c);
        }
    }
    return out;
}
function phaseTransitionCounterfactuals(source, request, continuityIds, previousProgram) {
    if (!previousProgram)
        return [];
    if (source.phase !== 'strength_accumulation' && source.phase !== 'intensification' && source.phase !== 'peak')
        return [];
    const out = [];
    const map = createExerciseMap(request.customExercises);
    const continuity = new Set(continuityIds);
    const priorities = request.goal.musclePriorities;
    const exPriority = (ex) => Math.max(0, ...directMuscles(map.get(ex.exerciseId)).map(m => PRIORITY_WEIGHT[priorities[m] ?? 'normal']));
    const strengthValue = (ex) => { const def = map.get(ex.exerciseId); if (!def)
        return 0; return (ex.role === 'primary_strength' ? 5 : ex.role === 'secondary_strength' ? 3.5 : ex.role === 'strength_support' ? 2.4 : 0) + relevantLiftSpecificity(def, request) * 3 + (def.suitability.strength ?? 0) * 1.2 + (continuity.has(ex.exerciseId) ? 1.1 : 0); };
    for (const [si, session] of source.sessions.entries()) {
        const receivers = session.exercises.map((ex, i) => ({ ex, i, value: strengthValue(ex) })).filter(x => x.value > 2.3 && x.ex.sets < (x.ex.role === 'primary_strength' ? 5 : 4)).sort((a, b) => b.value - a.value);
        const donors = session.exercises.map((ex, i) => ({ ex, i, priority: exPriority(ex) })).filter(x => x.ex.sets > 1 && (x.ex.role === 'hypertrophy_isolation' || x.ex.role === 'hypertrophy_compound' || x.ex.role === 'specialization')).sort((a, b) => a.priority - b.priority || b.ex.sets - a.ex.sets);
        const receiver = receivers[0];
        const donor = donors.find(d => d.i !== receiver?.i && (source.phase !== 'strength_accumulation' || d.priority <= PRIORITY_WEIGHT.high));
        if (receiver && donor) {
            const sessions = cloneSessions(source);
            sessions[si].exercises[receiver.i].sets++;
            sessions[si].exercises[donor.i].sets--;
            const c = candidate(`phase-promote:${si}:${donor.i}:${receiver.i}`, source, sessions, request, [{ type: 'phase_transition', sessionId: session.id, summary: `Promoted one set of ${receiver.ex.name} and demoted one set of ${donor.ex.name} to better match ${source.phase.replaceAll('_', ' ')} without increasing weekly dose.` }], continuityIds);
            if (c)
                out.push(c);
        }
        if (source.phase === 'peak') {
            // Peaking should not spend scarce recovery on redundant low-priority accessory dose when the same
            // session can retain the movement at maintenance volume and stay audit-safe.
            for (const donor of donors.filter(d => d.priority <= PRIORITY_WEIGHT.normal && d.ex.sets >= 3).slice(0, 2)) {
                const sessions = cloneSessions(source);
                sessions[si].exercises[donor.i].sets--;
                const c = candidate(`phase-demote:${si}:${donor.i}`, source, sessions, request, [{ type: 'phase_transition', sessionId: session.id, summary: `Tested a one-set demotion of ${donor.ex.name} in peak to buy recovery without deleting the exercise.` }], continuityIds);
                if (c)
                    out.push(c);
            }
        }
        if (out.length >= SHADOW_MAX_CANDIDATES_PER_ROUND)
            return out;
    }
    return out;
}
export function optimizeShadowProgram(input, liveProgram, options) {
    const request = normalize(input), continuityIds = options?.continuityIds ?? [];
    const baselineProgram = rebuildShadowProgram(liveProgram, cloneSessions(liveProgram), request, []);
    const baseline = { id: 'baseline', program: baselineProgram, score: scoreShadowProgram(baselineProgram, request, continuityIds), changes: [] };
    let best = baseline, candidateCount = 1;
    for (let round = 0; round < SHADOW_MAX_ROUNDS; round++) {
        const pool = [];
        const reordered = specializationReorder(best.program, request, continuityIds);
        if (reordered)
            pool.push(reordered);
        pool.push(...setCounterfactuals(best.program, request, continuityIds, options?.previousProgram), ...specializationDoseCounterfactuals(best.program, request, continuityIds), ...frequencyCounterfactuals(best.program, request, continuityIds), ...phaseTransitionCounterfactuals(best.program, request, continuityIds, options?.previousProgram));
        const allowed = options?.allowedChangeTypes?.length ? new Set(options.allowedChangeTypes) : null;
        const eligible = allowed ? pool.filter(c => c.changes.length > 0 && c.changes.every(ch => allowed.has(ch.type))) : pool;
        candidateCount += eligible.length;
        const next = eligible.sort((a, b) => b.score.total - a.score.total)[0];
        if (!next || next.score.total <= best.score.total + .15)
            break;
        best = { ...next, changes: [...best.changes, ...next.changes] };
    }
    return { candidate: best, baseline, candidateCount };
}
