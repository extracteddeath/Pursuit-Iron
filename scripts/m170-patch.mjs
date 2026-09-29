import fs from 'node:fs';
import crypto from 'node:crypto';

const read = p => fs.readFileSync(p, 'utf8');
const write = (p, s) => fs.writeFileSync(p, s);
const countOf = (s, needle) => s.split(needle).length - 1;
const once = (s, oldText, newText, label) => {
  const n = countOf(s, oldText);
  if (n !== 1) throw new Error(`${label}: expected 1 match, found ${n}`);
  return s.replace(oldText, newText);
};

let prescription = read('modules/next-engine/prescription.js');
prescription = once(prescription,
`function exerciseEligibleForRequest(ex, request) {
    if ((ex.flags.bodyweight || ex.equipment.includes('bodyweight')) && request.equipment.bodyweight === 'exclude')`,
`function exerciseEligibleForRequest(ex, request) {
    // Feasibility uses the same candidate universe as realization. An exercise the athlete
    // explicitly avoided cannot rescue a lift/muscle feasibility check and then disappear later.
    if (request.preferences?.avoidedExercises?.includes(ex.id))
        return false;
    if ((ex.flags.bodyweight || ex.equipment.includes('bodyweight')) && request.equipment.bodyweight === 'exclude')`,
'avoided exercise feasibility');
write('modules/next-engine/prescription.js', prescription);

let perf = read('modules/next-engine/performance.js');
perf = once(perf,
`        const rirReported = actual.filter(s => s.rir !== null);
        const effortInRange = rirReported.length === 0 || rirReported.every(s => s.rir >= ex.prescription.rir[0]);
        const clearOvershoot = rirReported.length > 0 && rirReported.filter(s => s.rir < Math.max(0, ex.prescription.rir[0] - 1)).length >= Math.ceil(rirReported.length / 2);`,
`        const rirReported = actual.filter(s => s.rir !== null);
        const targetRirFloor = Math.max(0, Number(ex.prescription.rir[0]) || 0);
        const belowTargetRir = rirReported.filter(s => Number(s.rir) < targetRirFloor);
        const effortInRange = rirReported.length === 0 || belowTargetRir.length === 0;
        const effortBelowTarget = belowTargetRir.length > 0;
        const repeatedEffortOvershoot = rirReported.length > 0 && belowTargetRir.length >= Math.ceil(rirReported.length / 2);
        const severeEffortOvershoot = rirReported.length > 0 && rirReported.filter(s => Number(s.rir) < Math.max(0, targetRirFloor - 1)).length >= Math.ceil(rirReported.length / 2);`,
'RIR effort signals');
perf = once(perf,
`            return { exerciseId: ex.exerciseId, exerciseName: ex.name, action: 'review', confidence: 'low', reason: 'No completed sets were logged for this prescribed exercise.', currentLoad: null, suggestedLoad: null, estimated1RM: null };`,
`            return { exerciseId: ex.exerciseId, exerciseName: ex.name, role: ex.role, reasonCode: 'no_completed_sets', action: 'review', confidence: 'low', reason: 'No completed sets were logged for this prescribed exercise.', currentLoad: null, suggestedLoad: null, estimated1RM: null };`,
'no-completed reason code');
perf = once(perf,
`            return { exerciseId: ex.exerciseId, exerciseName: ex.name, action: 'review', confidence: 'moderate', reason: 'Less than 75% of prescribed sets were completed; diagnose time, fatigue, exercise fit, or interruption before progressing.', currentLoad, suggestedLoad: currentLoad, estimated1RM };`,
`            return { exerciseId: ex.exerciseId, exerciseName: ex.name, role: ex.role, reasonCode: 'incomplete_session', action: 'review', confidence: 'moderate', reason: 'Less than 75% of prescribed sets were completed; diagnose time, fatigue, exercise fit, or interruption before progressing.', currentLoad, suggestedLoad: currentLoad, estimated1RM };`,
'incomplete-session reason code');
perf = perf.replaceAll("exerciseId: ex.exerciseId, exerciseName: ex.name, action: 'increase_load'", "exerciseId: ex.exerciseId, exerciseName: ex.name, role: ex.role, reasonCode: 'progression_success', action: 'increase_load'");
perf = once(perf,
`                    currentLoad, suggestedLoad: null, loadMode: loading.mode, suggestedLoadLabel: undefined, estimated1RM`,
`                    currentLoad, suggestedLoad: null, loadMode: loading.mode, suggestedLoadLabel: undefined, estimated1RM, role: ex.role, reasonCode: 'loading_inventory_blocked'`,
'loading-inventory reason code');
perf = once(perf,
`                    exerciseId: ex.exerciseId, exerciseName: ex.name, action: 'decrease_load', confidence: correction.confidence,`,
`                    exerciseId: ex.exerciseId, exerciseName: ex.name, role: ex.role, reasonCode: 'load_too_heavy', action: 'decrease_load', confidence: correction.confidence,`,
'load-too-heavy reason code');
perf = once(perf,
`        if (clearOvershoot || !allAtLeastBottom) {
            return { exerciseId: ex.exerciseId, exerciseName: ex.name, action: 'hold', confidence: 'moderate', reason: 'Performance or effort fell outside the target range; hold load and collect another comparable exposure before changing the program.', currentLoad, suggestedLoad: currentLoad, estimated1RM };
        }`,
`        if (effortBelowTarget) {
            const code = repeatedEffortOvershoot ? 'effort_overshoot' : 'effort_below_target';
            const detail = severeEffortOvershoot
                ? 'Repeated effort was materially harder than prescribed.'
                : 'Reported effort fell below the prescribed RIR floor.';
            return { exerciseId: ex.exerciseId, exerciseName: ex.name, role: ex.role, reasonCode: code, action: 'hold', confidence: repeatedEffortOvershoot ? 'high' : 'moderate', reason: \`${'${detail}'} Hold the load and re-enter the prescribed effort range before progressing.\`, currentLoad, suggestedLoad: currentLoad, estimated1RM };
        }
        if (!allAtLeastBottom) {
            return { exerciseId: ex.exerciseId, exerciseName: ex.name, role: ex.role, reasonCode: 'rep_floor_miss', action: 'hold', confidence: 'moderate', reason: 'Repetitions fell below the prescribed floor without enough evidence for an automatic load correction; hold and collect another comparable exposure.', currentLoad, suggestedLoad: currentLoad, estimated1RM };
        }`,
'effort and rep-floor hold split');
perf = once(perf,
`        return { exerciseId: ex.exerciseId, exerciseName: ex.name, action: 'add_reps', confidence: 'moderate', reason, currentLoad, suggestedLoad: currentLoad, suggestedReps, estimated1RM };`,
`        return { exerciseId: ex.exerciseId, exerciseName: ex.name, role: ex.role, reasonCode: 'normal_progression', action: 'add_reps', confidence: 'moderate', reason, currentLoad, suggestedLoad: currentLoad, suggestedReps, estimated1RM };`,
'normal-progression reason code');
write('modules/next-engine/performance.js', perf);

let response = read('modules/next-engine/response.js');
response = once(response,
`    const overshot = rir.filter(s => s.rir < Math.max(0, exercise.prescription.rir[0] - 1)).length;`,
`    const overshot = rir.filter(s => s.rir < Math.max(0, Number(exercise.prescription.rir[0]) || 0)).length;`,
'response RIR threshold');
response = once(response,
`        const positiveActions = exposures.filter(x => x.progression?.action === 'increase_load').length;
        if (relative >= .015 || positiveActions >= 2) {
            return makeDiagnosis(exercise, 'progressing', relative >= .03 || positiveActions >= 3 ? 'high' : 'moderate', exposures.length, 'Comparable performance is improving while the exercise remains executable, so changing the exercise or adding sets would create unnecessary churn.', 'maintain', false, 'Maintain the exercise and let load/rep progression continue.', 1, 0, 0);`,
`        const positiveSignals = exposures.filter(x => x.progression?.reasonCode === 'progression_success').length;
        if (relative >= .015 || positiveSignals >= 2) {
            return makeDiagnosis(exercise, 'progressing', relative >= .03 || positiveSignals >= 3 ? 'high' : 'moderate', exposures.length, 'Comparable performance is improving while the exercise remains executable, so changing the exercise or adding sets would create unnecessary churn.', 'maintain', false, 'Maintain the exercise and let load/rep progression continue.', 1, 0, 0);`,
'response progression evidence');
write('modules/next-engine/response.js', response);

write('modules/next-engine/recovery.js', `/**
 * Signed causal recovery evidence from the performance evaluator.
 * Positive = productive evidence, negative = meaningful fatigue/load stress, zero = neutral.
 */
export function recoverySignalForDecision(decision) {
    const strengthMultiplier = decision?.role === 'primary_strength' ? 1.25 : decision?.role === 'secondary_strength' ? 1.1 : 1;
    switch (decision?.reasonCode) {
        case 'progression_success': return 1;
        case 'effort_overshoot': return -1 * strengthMultiplier;
        case 'effort_below_target': return -.55 * strengthMultiplier;
        case 'load_too_heavy': return -.9 * strengthMultiplier;
        case 'rep_floor_miss': return -.65 * strengthMultiplier;
        case 'incomplete_session': return -.3;
        default: return 0;
    }
}

/**
 * Recovery assessment intentionally requires repeated multi-exercise evidence.
 * Normal progression holds, loading-inventory limits, and missing data are not fatigue evidence.
 */
export function assessRecovery(recentWorkouts) {
    const usable = recentWorkouts.filter(x => x.length > 0).slice(-5);
    if (!usable.length)
        return { status: 'normal', confidence: 'low', evidenceCount: 0, rationale: 'Not enough completed workout evidence to assess recovery.' };
    let negativeWorkouts = 0;
    let broadNegativeWorkouts = 0;
    let positiveWorkouts = 0;
    let evidenceCount = 0;
    for (const decisions of usable) {
        const signals = decisions.map(recoverySignalForDecision);
        const negatives = signals.filter(v => v < 0);
        const negativeScore = negatives.reduce((sum, v) => sum + Math.abs(v), 0);
        const positiveScore = signals.filter(v => v > 0).reduce((sum, v) => sum + v, 0);
        if (negativeScore >= .75) negativeWorkouts++;
        if (negatives.length >= 2 && negativeScore >= 1.5) broadNegativeWorkouts++;
        if (positiveScore > negativeScore) positiveWorkouts++;
        evidenceCount += negatives.length;
    }
    if (usable.length >= 3 && broadNegativeWorkouts >= 3 && positiveWorkouts === 0) {
        return { status: 'deload_recommended', confidence: usable.length >= 4 ? 'high' : 'moderate', evidenceCount,
            rationale: 'Repeated broad hard-effort/load-miss signals across at least three workouts suggest accumulated fatigue. A short recovery phase is preferable to adding work or changing multiple exercises.' };
    }
    if (usable.length >= 2 && (broadNegativeWorkouts >= 2 || negativeWorkouts >= 3)) {
        return { status: 'watch', confidence: 'moderate', evidenceCount,
            rationale: 'Recovery is worth watching because meaningful negative performance causes have repeated, but the evidence is not broad or persistent enough to justify a deload yet.' };
    }
    return { status: 'normal', confidence: usable.length >= 3 ? 'moderate' : 'low', evidenceCount,
        rationale: 'Recent performance does not show repeated broad evidence of accumulated fatigue.' };
}
`);

let history = read('modules/next-engine/workout-history-adapter.js');
history = once(history, `import { assessRecovery } from './recovery.js';`, `import { assessRecovery, recoverySignalForDecision } from './recovery.js';`, 'history recovery import');
history = once(history,
`/** Runtime bridge only: asks Pursuit Engine's own performance evaluator what the next exposure should do. */`,
`export function deriveLongitudinalExerciseEvidence(diagnoses, latestDecisions = [], sourceExercises = []) {
    const successful = new Set();
    const protectedIds = new Set(sourceExercises.filter(ex => ex.role === 'primary_strength').map(ex => ex.exerciseId));
    const replaceExerciseIds = new Set();
    const techniqueLimitedExerciseIds = new Set();
    const fatigueLimitedExerciseIds = new Set();
    const diagnosisById = new Map((diagnoses ?? []).map(d => [d.exerciseId, d]));
    for (const diagnosis of diagnoses ?? []) {
        if (diagnosis.state === 'poor_fit') replaceExerciseIds.add(diagnosis.exerciseId);
        else if (diagnosis.state === 'technique_limited') { techniqueLimitedExerciseIds.add(diagnosis.exerciseId); protectedIds.add(diagnosis.exerciseId); }
        else if (diagnosis.state === 'fatigue_limited') { fatigueLimitedExerciseIds.add(diagnosis.exerciseId); protectedIds.add(diagnosis.exerciseId); }
        else if (['progressing', 'underloaded', 'possibly_understimulated'].includes(diagnosis.state) && diagnosis.confidence !== 'low') successful.add(diagnosis.exerciseId);
    }
    for (const decision of latestDecisions ?? []) {
        const diagnosis = diagnosisById.get(decision.exerciseId);
        if (recoverySignalForDecision(decision) > 0 && (!diagnosis || diagnosis.state === 'uncertain') && !replaceExerciseIds.has(decision.exerciseId))
            successful.add(decision.exerciseId);
    }
    for (const id of replaceExerciseIds) { successful.delete(id); protectedIds.delete(id); }
    return {
        successfulExerciseIds: [...successful], protectedExerciseIds: [...protectedIds], replaceExerciseIds: [...replaceExerciseIds],
        techniqueLimitedExerciseIds: [...techniqueLimitedExerciseIds], fatigueLimitedExerciseIds: [...fatigueLimitedExerciseIds]
    };
}

/** Runtime bridge only: asks Pursuit Engine's own performance evaluator what the next exposure should do. */`,
'longitudinal evidence helper');
history = once(history,
`    let positive = 0, negative = 0;
    const latest = new Map();
    for (const workout of workouts)
        for (const decision of workout.progression) {
            latest.set(decision.exerciseId, decision);
            if (decision.action === 'increase_load' || decision.action === 'add_reps')
                positive++;
            else if (decision.action === 'hold' || decision.action === 'review' || decision.action === 'decrease_load')
                negative++;
        }
    const successful = [...latest.entries()].filter(([, d]) => d.action === 'increase_load' || d.action === 'add_reps').map(([id]) => id);
    const protectedIds = sourceExercises.filter(ex => ex.role === 'primary_strength').map(ex => ex.exerciseId);`,
`    let positive = 0, negative = 0;
    const latest = new Map();
    for (const workout of workouts)
        for (const decision of workout.progression) {
            latest.set(decision.exerciseId, decision);
            const signal = recoverySignalForDecision(decision);
            if (signal > 0) positive++;
            else if (signal < 0) negative++;
        }
    const evidence = deriveLongitudinalExerciseEvidence(diagnoses, [...latest.values()], sourceExercises);
    const successful = evidence.successfulExerciseIds;
    const protectedIds = evidence.protectedExerciseIds;`,
'history causal counters');
history = once(history,
`        successfulExerciseIds: successful, protectedExerciseIds: [...new Set(protectedIds)], diagnoses,`,
`        successfulExerciseIds: successful, protectedExerciseIds: [...new Set(protectedIds)], replaceExerciseIds: evidence.replaceExerciseIds,
        techniqueLimitedExerciseIds: evidence.techniqueLimitedExerciseIds, fatigueLimitedExerciseIds: evidence.fatigueLimitedExerciseIds, diagnoses,`,
'history evidence output');
history = once(history,
`function requestAdaptedFromHistory(request, analysis) {
    if (analysis.classification !== 'fatigue_limited' && analysis.recovery.status !== 'deload_recommended')
        return request;
    return {
        ...request,
        schedule: { days: request.schedule.days.map(day => ({
                ...day,
                targetExercises: day.targetExercises === undefined ? undefined : Math.max(2, day.targetExercises - Math.max(1, Math.ceil(day.targetExercises * .2)))
            })) }
    };
}`,
`function requestAdaptedFromHistory(request, analysis) {
    const replace = new Set(analysis.replaceExerciseIds ?? []);
    let adapted = replace.size ? {
        ...request,
        preferences: { ...request.preferences, avoidedExercises: [...new Set([...(request.preferences?.avoidedExercises ?? []), ...replace])] }
    } : request;
    if (analysis.classification !== 'fatigue_limited' && analysis.recovery.status !== 'deload_recommended')
        return adapted;
    return {
        ...adapted,
        schedule: { days: adapted.schedule.days.map(day => ({
                ...day,
                targetExercises: day.targetExercises === undefined ? undefined : Math.max(2, day.targetExercises - Math.max(1, Math.ceil(day.targetExercises * .2)))
            })) }
    };
}`,
'history adaptation');
history = once(history,
`        successfulExerciseIds: analysis.successfulExerciseIds,
        protectedExerciseIds: analysis.protectedExerciseIds`,
`        successfulExerciseIds: analysis.successfulExerciseIds,
        protectedExerciseIds: analysis.protectedExerciseIds,
        replaceExerciseIds: analysis.replaceExerciseIds`,
'transition evidence');
write('modules/next-engine/workout-history-adapter.js', history);

let transition = read('modules/next-engine/phase-transition.js');
transition = once(transition,
`function isCompatibleReplacement(previous, candidate, day, request) {
    const exerciseMap = createExerciseMap(request.customExercises);`,
`function isCompatibleReplacement(previous, candidate, day, request, exerciseMap) {`,
'transition shared exercise map');
const matcher = `function vectorSimilarity(a, b) {
    const keys = new Set([...a.keys(), ...b.keys()]);
    let overlap = 0, union = 0;
    for (const key of keys) {
        const av = a.get(key) ?? 0, bv = b.get(key) ?? 0;
        overlap += Math.min(av, bv); union += Math.max(av, bv);
    }
    return union > 0 ? overlap / union : 0;
}
function sessionMuscleVector(session, exerciseMap) {
    const out = new Map();
    for (const exercise of session.exercises ?? []) {
        const def = exerciseMap.get(exercise.exerciseId);
        if (!def) continue;
        for (const [muscle, credit] of Object.entries(def.muscles ?? {}))
            out.set(muscle, (out.get(muscle) ?? 0) + (credit.credit ?? 0) * Math.max(1, Number(exercise.sets) || 1));
    }
    return out;
}
function sessionMovementSet(session, exerciseMap) {
    return new Set((session.exercises ?? []).map(ex => exerciseMap.get(ex.exerciseId)?.movementFamily).filter(Boolean));
}
function jaccard(a, b) {
    const union = new Set([...a, ...b]);
    if (!union.size) return 0;
    let overlap = 0;
    for (const value of a) if (b.has(value)) overlap++;
    return overlap / union.size;
}
function transitionSessionScore(previous, target, request, exerciseMap) {
    let score = previous.intent === target.intent ? 5 : 0;
    score += vectorSimilarity(sessionMuscleVector(previous, exerciseMap), sessionMuscleVector(target, exerciseMap)) * 4;
    score += jaccard(sessionMovementSet(previous, exerciseMap), sessionMovementSet(target, exerciseMap)) * 3;
    const priorStrength = (previous.exercises ?? []).filter(ex => ex.role === 'primary_strength' || ex.role === 'secondary_strength').length;
    const targetStrength = (target.exercises ?? []).filter(ex => ex.role === 'primary_strength' || ex.role === 'secondary_strength').length;
    score += Math.max(0, 2 - Math.abs(priorStrength - targetStrength) * .75);
    const eligible = (previous.exercises ?? []).filter(ex => {
        const def = exerciseMap.get(ex.exerciseId);
        return def && equipmentEligible(def, target.day, request) && !request.preferences.avoidedExercises?.includes(ex.exerciseId);
    }).length;
    score += (previous.exercises?.length ? eligible / previous.exercises.length : 0) * 2;
    if (previous.day === target.day) score += .2;
    return score;
}
/** Deterministic maximum-score bipartite session matching for phase continuity. */
export function matchPriorSessionsForTransition(previousSessions, targetSessions, request) {
    const exerciseMap = createExerciseMap(request.customExercises);
    const previous = [...(previousSessions ?? [])].sort((a, b) => String(a.id).localeCompare(String(b.id)));
    const target = [...(targetSessions ?? [])].sort((a, b) => String(a.id).localeCompare(String(b.id)));
    const memo = new Map();
    const solve = (i, mask) => {
        const key = \`${'${i}:${mask}'}\`;
        if (memo.has(key)) return memo.get(key);
        if (i >= target.length) return { score: 0, pairs: [] };
        let best = solve(i + 1, mask);
        for (let j = 0; j < previous.length; j++) {
            if (mask & (1 << j)) continue;
            const local = transitionSessionScore(previous[j], target[i], request, exerciseMap);
            if (local < 1.5) continue;
            const tail = solve(i + 1, mask | (1 << j));
            const candidate = { score: local + tail.score, pairs: [[target[i].id, previous[j]], ...tail.pairs] };
            const sig = x => x.pairs.map(([id, p]) => \`${'${id}:${p.id}'}\`).join('|');
            if (candidate.score > best.score + 1e-9 || (Math.abs(candidate.score - best.score) <= 1e-9 && sig(candidate) < sig(best))) best = candidate;
        }
        memo.set(key, best); return best;
    };
    return new Map(solve(0, 0).pairs);
}
`;
transition = once(transition,
`/**
 * Generate the target phase, then make conservative audit-gated attempts to`,
`${matcher}
/**
 * Generate the target phase, then make conservative audit-gated attempts to`,
'phase transition matcher');
transition = once(transition,
`    const successful = new Set(evidence.successfulExerciseIds);
    const protectedIds = new Set(evidence.protectedExerciseIds ?? []);
    const previousByDay = new Map(previous.sessions.map(session => [session.day, session]));
    let program = generated;`,
`    const successful = new Set(evidence.successfulExerciseIds);
    const protectedIds = new Set(evidence.protectedExerciseIds ?? []);
    const replaceIds = new Set(evidence.replaceExerciseIds ?? []);
    const exerciseMap = createExerciseMap(request.customExercises);
    let program = generated;
    const previousByTargetSession = matchPriorSessionsForTransition(previous.sessions, program.sessions, request);`,
'transition matching setup');
transition = once(transition, `        const priorSession = previousByDay.get(session.day);`, `        const priorSession = previousByTargetSession.get(session.id);`, 'weekday identity removal');
transition = once(transition,
`                .filter(ex => successful.has(ex.exerciseId) || protectedIds.has(ex.exerciseId))
                .filter(ex => !alreadyUsed.has(ex.exerciseId))
                .filter(ex => isCompatibleReplacement(ex, candidate, session.day, request))`,
`                .filter(ex => successful.has(ex.exerciseId) || protectedIds.has(ex.exerciseId))
                .filter(ex => !replaceIds.has(ex.exerciseId))
                .filter(ex => !alreadyUsed.has(ex.exerciseId))
                .filter(ex => isCompatibleReplacement(ex, candidate, session.day, request, exerciseMap))`,
'transition alternatives');
write('modules/next-engine/phase-transition.js', transition);

let config = read('modules/next-engine/config.js');
config = once(config, "export const ENGINE_VERSION = '0.62.5';", "export const ENGINE_VERSION = '0.62.6';", 'engine version');
write('modules/next-engine/config.js', config);
let app = read('modules/App.js');
app = once(app, "const __APP_VERSION__='3.219.0'; const __BUILD__='775';", "const __APP_VERSION__='3.220.0'; const __BUILD__='776';", 'app version/build');
write('modules/App.js', app);
let index = read('index.html');
index = index.replaceAll("build:'775'", "build:'776'");
write('index.html', index);
let sw = read('sw.js');
sw = once(sw, '/* M169 swap-sheet native scrolling — Engine 0.62.5; atomic startup recovery retained. */', '/* M170 causal progression/recovery + longitudinal transition continuity — Engine 0.62.6. */', 'service worker comment');
sw = once(sw, 'const CACHE="pursuit-iron-production-m169-swap-scroll";', 'const CACHE="pursuit-iron-production-m170-engine-correctness";', 'service worker cache');
write('sw.js', sw);

const profile = JSON.parse(read('BUILD_PROFILE.json'));
profile.milestone = 'M170';
profile.source = 'M169 + engine correctness items 1-5';
profile.engine = '0.62.6 causal progression/recovery + longitudinal continuity';
profile.cache = 'pursuit-iron-production-m170-engine-correctness';
write('BUILD_PROFILE.json', JSON.stringify(profile, null, 2) + '\n');

write('verification/m170-engine-correctness-test.mjs', `import assert from 'node:assert/strict';
import { EXERCISE_MAP } from '../modules/next-engine/exercise-db.js';
import { createMusclePrescriptions, filterFeasibleLiftPriorities, normalizeRequest } from '../modules/next-engine/prescription.js';
import { evaluateWorkoutProgression } from '../modules/next-engine/performance.js';
import { assessRecovery } from '../modules/next-engine/recovery.js';
import { deriveLongitudinalExerciseEvidence } from '../modules/next-engine/workout-history-adapter.js';
import { matchPriorSessionsForTransition } from '../modules/next-engine/phase-transition.js';
const equipment=['barbell','rack','bench','dumbbell','cable','machine','smith','leg_press','pullup_bar','bodyweight'];
const requestOf=avoidedExercises=>({athlete:{experience:'intermediate'},goal:{type:'hypertrophy',musclePriorities:{},liftPriorities:{}},schedule:{days:[{day:'monday',maxMinutes:60},{day:'wednesday',maxMinutes:60},{day:'friday',maxMinutes:60}]},equipment:{available:equipment,bodyweight:'allow',loading:{unit:'lb',barbell:{barWeight:45,platePairs:[{weight:45,pairs:8},{weight:25,pairs:4},{weight:10,pairs:4},{weight:5,pairs:4},{weight:2.5,pairs:4}]},dumbbells:{availablePerHand:[5,10,15,20,25,30,35,40,45,50,55,60,65,70,75,80,85,90,95,100]},machine:{minimum:5,increment:5,maximum:500},cable:{minimum:5,increment:5,maximum:300},smith:{minimum:5,increment:5,maximum:500},exerciseOverrides:{}}},restrictions:{maxBarbellMovementsPerDay:3,allowSupersets:true},preferences:{preferredSplit:'full_body',lockedSplit:'full_body',avoidedExercises},customExercises:[],seed:170});
const benchAvoided=[...EXERCISE_MAP.values()].filter(ex=>(ex.liftSpecificity?.bench_press??0)>.45).map(ex=>ex.id);
const benchReq=normalizeRequest(requestOf(benchAvoided));
assert.equal('bench_press' in filterFeasibleLiftPriorities(benchReq,{bench_press:'high'}),false);
const chestAvoided=[...EXERCISE_MAP.values()].filter(ex=>(ex.muscles?.chest?.credit??0)>0).map(ex=>ex.id);
const chest=createMusclePrescriptions(normalizeRequest(requestOf(chestAvoided)),'hypertrophy_accumulation').find(x=>x.muscle==='chest');
assert.equal(chest.minimum,0); assert.equal(chest.preferred,0);
const evalRir=(range,rir)=>evaluateWorkoutProgression({exercises:[{exerciseId:'synthetic',name:'Synthetic',role:'hypertrophy_compound',sets:3,prescription:{reps:[5,8],rir:range},progressionStyle:'double'}]},[0,1,2].map(i=>({exerciseId:'synthetic',setIndex:i,load:100,reps:6,rir})),{})[0];
for(const [range,rir] of [[[1,2],0],[[2,3],1]]){const d=evalRir(range,rir);assert.equal(d.action,'hold');assert.equal(d.reasonCode,'effort_overshoot');}
assert.equal(evalRir([0,1],0).reasonCode,'normal_progression');
const neutral=[{reasonCode:'normal_progression',action:'add_reps',role:'hypertrophy_compound'},{reasonCode:'loading_inventory_blocked',action:'review',role:'primary_strength'}];
assert.equal(assessRecovery([neutral,neutral,neutral]).status,'normal');
const hard=[{reasonCode:'effort_overshoot',action:'hold',role:'primary_strength'},{reasonCode:'load_too_heavy',action:'decrease_load',role:'hypertrophy_compound'}];
assert.equal(assessRecovery([hard,hard,hard]).status,'deload_recommended');
const evidence=deriveLongitudinalExerciseEvidence([{exerciseId:'good',state:'progressing',confidence:'moderate'},{exerciseId:'bad-fit',state:'poor_fit',confidence:'high'},{exerciseId:'tech',state:'technique_limited',confidence:'moderate'},{exerciseId:'fatigue',state:'fatigue_limited',confidence:'moderate'},{exerciseId:'stable',state:'possibly_understimulated',confidence:'moderate'}],[{exerciseId:'uncertain-win',reasonCode:'progression_success',action:'increase_load'}],[{exerciseId:'primary',role:'primary_strength'}]);
assert.deepEqual(new Set(evidence.successfulExerciseIds),new Set(['good','stable','uncertain-win']));
assert.ok(evidence.protectedExerciseIds.includes('tech')&&evidence.protectedExerciseIds.includes('fatigue')&&evidence.protectedExerciseIds.includes('primary'));
assert.deepEqual(evidence.replaceExerciseIds,['bad-fit']);
const defs=[...EXERCISE_MAP.values()]; const press=defs.find(x=>x.movementFamily==='horizontal_press'); const row=defs.find(x=>x.movementFamily==='horizontal_pull'); const squat=defs.find(x=>x.movementFamily==='squat'); assert.ok(press&&row&&squat);
const ex=(d,role='hypertrophy_compound')=>({exerciseId:d.id,name:d.name,role,sets:3});
const previous=[{id:'prior-upper',day:'monday',intent:'upper',exercises:[ex(press),ex(row)]},{id:'prior-lower',day:'friday',intent:'lower',exercises:[ex(squat)]}];
const targets=[{id:'target-upper',day:'friday',intent:'upper',exercises:[ex(press),ex(row)]},{id:'target-lower',day:'monday',intent:'lower',exercises:[ex(squat)]}];
const matches=matchPriorSessionsForTransition(previous,targets,normalizeRequest(requestOf([])));
assert.equal(matches.get('target-upper')?.id,'prior-upper'); assert.equal(matches.get('target-lower')?.id,'prior-lower');
console.log('M170 engine correctness tests OK.');
`);

let verify = read('scripts/verify-release.mjs');
if (!verify.includes('verification/m170-engine-correctness-test.mjs')) {
  const hook = "execFileSync(process.execPath,['--no-warnings','--experimental-loader','./verification/import-loader.mjs','./verification/full-engine-import-test.mjs'],{stdio:'inherit',cwd:root});";
  verify = once(verify, hook, hook + "\nexecFileSync(process.execPath,['--no-warnings','--experimental-loader','./verification/import-loader.mjs','./verification/m170-engine-correctness-test.mjs'],{stdio:'inherit',cwd:root});", 'M170 test hook');
}
write('scripts/verify-release.mjs', verify);
let changelog=read('CHANGELOG.md');
const entry=`## M170 — Engine Correctness 1–5 (3.220.0 / build 776 / Engine 0.62.6)\n\n- Excludes avoided exercises from feasibility.\n- Corrects RIR-floor handling and adds causal progression reason codes.\n- Recovery ignores neutral holds/loading constraints and reacts to actual hard-effort/load evidence.\n- Block adaptation uses longitudinal diagnosis.\n- Phase continuity matches sessions by training identity rather than weekday.\n\n`;
if(!changelog.startsWith('## M170 —')) changelog=entry+changelog;
write('CHANGELOG.md',changelog);
write('M170_REPORT.md',`# M170 engine correctness items 1–5\n\nApp **3.220.0**, build **776**, Pursuit Engine **0.62.6**.\n\nCloses avoided-feasibility parity, exact RIR-floor handling, causal recovery signals, longitudinal block adaptation, and weekday-independent phase continuity. Full existing release verification plus the M170 focused regression gate is required.\n`);
const manifest=JSON.parse(read('RELEASE_MANIFEST.json'));
manifest.milestone='M170'; manifest.appVersion='3.220.0'; manifest.build=776; manifest.engineVersion='0.62.6';
manifest.localCandidate={name:'M170 Engine Correctness 1-5',base:'M169 / Engine 0.62.5',validation:'Focused M170 engine correctness tests plus full release regression suite.'};
const hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
for(const file of Object.keys(manifest.runtimeFiles||{})) manifest.runtimeFiles[file]=hash(file);
const aggregateBlob=Object.keys(manifest.runtimeFiles||{}).sort().map(file=>`${file}:${manifest.runtimeFiles[file]}\n`).join('');
manifest.runtimeAggregate=crypto.createHash('sha256').update(Buffer.from(aggregateBlob)).digest('hex');
for(const file of Object.keys(manifest.uiFiles||{})) manifest.uiFiles[file]=hash(file);
write('RELEASE_MANIFEST.json',JSON.stringify(manifest,null,2)+'\n');
console.log('M170 patch applied.');
