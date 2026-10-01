import { createExerciseMap } from './exercise-db.js';
import { PUBLIC_MEV_REGIONS, directlyTargetsPublicRegion, publicMevContractApplies, publicMevRequired, publicRegionContribution } from './public-mev.js';
import { assessWeeklyRecovery, minimumWeeklyRecoveryScore } from './weekly-recovery.js';
import { exerciseEconomyCluster, redundantExercisePairs } from './exercise-economy.js';
const UPPER_ACCESSORY_FAMILIES = new Set(['lateral_raise', 'rear_delt', 'elbow_flexion', 'elbow_extension', 'grip', 'wrist_flexion', 'wrist_extension', 'wrist_deviation']);
const LOWER_FAMILIES = new Set(['squat', 'leg_press', 'knee_extension', 'hip_hinge', 'hip_extension', 'knee_flexion', 'calf', 'hip_abduction', 'hip_adduction', 'dorsiflexion']);
const STRENGTH_ROLES = new Set(['primary_strength', 'secondary_strength', 'strength_support']);
/**
 * Objective plausibility checks used by the production audit and verification suites.
 * These intentionally cover concrete invariants only; they do not pretend to replace human taste.
 */
export function evaluateObjectiveCoachGuardrails(program, request) {
    const findings = [];
    const defs = createExerciseMap(request.customExercises);
    program.sessions.forEach((session, sessionIndex) => {
        if (session.estimatedMinutes > session.maxMinutes) {
            findings.push({ code: 'SESSION_TIME_CAP_EXCEEDED', sessionIndex, sessionName: session.name, detail: `${session.estimatedMinutes}m exceeds ${session.maxMinutes}m cap.` });
        }
        const oneSet = session.exercises.filter(ex => ex.sets === 1).length;
        // One-set work is not automatically a defect: a long, varied session can legitimately use a
        // small top-off set. Treat fragmentation as objective clutter only when the session is compact or
        // one-set fragments make up at least half of all movements. This keeps the mutation guardrail
        // sensitive without mislabeling ordinary coach-like top-offs as failures.
        const peakMaintenanceTopOff = program.phase === 'peak' && session.exercises.length <= 5;
        const fragmented = oneSet > 2 && !peakMaintenanceTopOff && (session.maxMinutes <= 45 || oneSet * 2 >= session.exercises.length);
        if (fragmented) {
            findings.push({ code: 'ONE_SET_FRAGMENTATION', sessionIndex, sessionName: session.name, detail: `${oneSet}/${session.exercises.length} exercises are one-set fragments in a ${session.maxMinutes}-minute session.` });
        }
        // The engine has no modeled pre-exhaustion technique. Flag only LOCAL interference: an ordinary
        // accessory that directly trains a muscle a later compound materially depends on. Unrelated or
        // antagonist accessories may still sit between compounds, especially in dense time-capped sessions.
        const ordered = session.exercises.map((exercise, index) => ({ exercise, index, def: defs.get(exercise.exerciseId) })).filter(x => !!x.def);
        for (let i = 0; i < ordered.length; i++) {
            const earlier = ordered[i];
            if (earlier.def.flags.compound || STRENGTH_ROLES.has(earlier.exercise.role) || earlier.exercise.sets < 2)
                continue;
            const primary = Object.entries(earlier.def.muscles).find(([, c]) => c.role === 'primary')?.[0];
            if (!primary)
                continue;
            for (let j = i + 1; j < ordered.length; j++) {
                const later = ordered[j];
                if (!later.def.flags.compound || STRENGTH_ROLES.has(later.exercise.role))
                    continue;
                const laterDef = later.def;
                const credit = laterDef.muscles[primary]?.credit ?? 0;
                if (credit < .5)
                    continue;
                findings.push({
                    code: 'LOCAL_PREFATIGUE_BEFORE_COMPOUND', sessionIndex, sessionName: session.name,
                    detail: `${earlier.exercise.name} (${earlier.exercise.sets} sets) directly pre-fatigues ${primary} before later compound ${later.exercise.name}.`
                });
                break;
            }
        }
        const supersetIndexes = new Map();
        session.exercises.forEach((exercise, index) => { if (exercise.supersetGroup) {
            const list = supersetIndexes.get(exercise.supersetGroup) ?? [];
            list.push(index);
            supersetIndexes.set(exercise.supersetGroup, list);
        } });
        for (const [group, indexes] of supersetIndexes) {
            if (indexes.length === 2 && Math.abs(indexes[0] - indexes[1]) !== 1)
                findings.push({
                    code: 'SUPERSET_NONADJACENT', sessionIndex, sessionName: session.name,
                    detail: `${group} exercises are separated in workout order (${indexes.map(i => i + 1).join(' and ')}).`
                });
        }
        const redundantSemantic = redundantExercisePairs(session.exercises, defs, muscle => request.goal.musclePriorities[muscle]);
        if (redundantSemantic.length) {
            const pair = redundantSemantic[0];
            findings.push({
                code: pair.kind === 'compound' ? 'REDUNDANT_COMPOUND_OVERLAP' : 'REDUNDANT_SEMANTIC_OVERLAP', sessionIndex, sessionName: session.name,
                detail: `${pair.a.name ?? pair.a.exerciseId} and ${pair.b.name ?? pair.b.exerciseId} fill the same ${pair.cluster.replaceAll('_', ' ')} hypertrophy slot; consolidate sets or use a complementary pattern instead.`
            });
        }
        if (session.intent === 'full' || session.intent === 'strength_full') {
            const compoundEntries = session.exercises.map((exercise, index) => ({ exercise, index, def: defs.get(exercise.exerciseId) }))
                .filter((x) => !!x.def && x.def.flags.compound && !STRENGTH_ROLES.has(x.exercise.role));
            const cls = (family) => ['horizontal_press', 'vertical_press', 'chest_adduction'].includes(family) ? 'push' : ['horizontal_pull', 'vertical_pull', 'shoulder_extension'].includes(family) ? 'pull' : ['squat', 'leg_press', 'knee_extension'].includes(family) ? 'knee' : ['hip_hinge', 'hip_extension', 'knee_flexion'].includes(family) ? 'hinge' : family;
            for (let i = 0; i < compoundEntries.length - 1; i++) {
                const current = cls(compoundEntries[i].def.movementFamily), next = cls(compoundEntries[i + 1].def.movementFamily);
                if (current !== next)
                    continue;
                if (compoundEntries.slice(i + 2).some(x => cls(x.def.movementFamily) !== current)) {
                    findings.push({ code: 'FULL_BODY_COMPOUND_CLUSTER', sessionIndex, sessionName: session.name, detail: `Consecutive ${current} compounds are clustered while another compound pattern remains later in the full-body session.` });
                    break;
                }
            }
        }
        let sawHypertrophy = false;
        const accessoryFamilies = new Map();
        let lowerExercises = 0, upperAccessories = 0;
        for (const exercise of session.exercises) {
            const def = defs.get(exercise.exerciseId);
            const isStrength = STRENGTH_ROLES.has(exercise.role);
            if (!isStrength)
                sawHypertrophy = true;
            else if (sawHypertrophy) {
                findings.push({ code: 'STRENGTH_AFTER_HYPERTROPHY', sessionIndex, sessionName: session.name, detail: `${exercise.name} (${exercise.role}) appears after hypertrophy/accessory work.` });
            }
            if (!def)
                continue;
            if (!isStrength && !def.flags.compound) {
                const muscle = Object.entries(def.muscles).find(([, c]) => c.role === 'primary')?.[0];
                const cluster = exerciseEconomyCluster(def);
                if (cluster) {
                    const key = `${cluster}|${muscle ?? 'generic'}`;
                    const list = accessoryFamilies.get(key) ?? [];
                    list.push({ muscle });
                    accessoryFamilies.set(key, list);
                }
            }
            if (LOWER_FAMILIES.has(def.movementFamily))
                lowerExercises++;
            if (UPPER_ACCESSORY_FAMILIES.has(def.movementFamily) && !def.flags.compound)
                upperAccessories++;
        }
        for (const [family, entries] of accessoryFamilies) {
            const priorities = entries.map(x => x.muscle ? request.goal.musclePriorities[x.muscle] ?? 'normal' : 'normal');
            const specialization = priorities.some(p => p === 'high' || p === 'specialization' || p === 'primary');
            const limit = specialization ? 3 : 2;
            if (entries.length >= limit)
                findings.push({ code: 'REDUNDANT_ACCESSORY_FAMILY', sessionIndex, sessionName: session.name, detail: `${entries.length} non-compound accessory exercises fill ${family.split('|')[0].replaceAll('_', ' ')}${specialization ? ' despite specialization allowance' : ''}.` });
        }
        // A focused Squat/Deadlift Lower day may legitimately contain only one primary lower movement.
        // Identity is diluted only by upper-body spillover: >2 upper accessories is always excessive, and
        // any spillover requires at least two lower-body movements so the day still reads as Lower/Legs.
        if ((session.intent === 'lower' || session.intent === 'legs') && (upperAccessories > 2 || (upperAccessories > 0 && lowerExercises < 2))) {
            findings.push({ code: 'LOWER_DAY_IDENTITY_DILUTED', sessionIndex, sessionName: session.name, detail: `Lower/Leg session has ${lowerExercises} lower-body movements and ${upperAccessories} upper-body accessory movements.` });
        }
    });
    const currentRecovery = assessWeeklyRecovery(program.sessions, request);
    const bestRecovery = minimumWeeklyRecoveryScore(program.sessions, request);
    if (currentRecovery.score > bestRecovery.score + .0001) {
        findings.push({
            code: 'RECOVERY_TOPOLOGY_AVOIDABLE', sessionIndex: 0, sessionName: 'Weekly schedule',
            detail: `Weekday assignment recovery score ${currentRecovery.score.toFixed(1)} can be reduced to ${bestRecovery.score.toFixed(1)} without changing sessions, volume, or available training days.`
        });
    }
    if (publicMevContractApplies(request, program.phase)) {
        const concentrationLimit = request.athlete.experience === 'novice' ? .7 : request.athlete.experience === 'advanced' ? .8 : .75;
        for (const region of PUBLIC_MEV_REGIONS) {
            if (!publicMevRequired(request, region))
                continue;
            const doses = program.sessions.map((session, sessionIndex) => ({
                sessionIndex, sessionName: session.name,
                dose: session.exercises.reduce((sum, exercise) => {
                    const def = defs.get(exercise.exerciseId);
                    return sum + (def && directlyTargetsPublicRegion(def, region) ? exercise.sets * publicRegionContribution(def, region) : 0);
                }, 0),
                movableDose: session.exercises.reduce((sum, exercise) => {
                    const def = defs.get(exercise.exerciseId);
                    const movable = !STRENGTH_ROLES.has(exercise.role);
                    return sum + (movable && def && directlyTargetsPublicRegion(def, region) ? exercise.sets * publicRegionContribution(def, region) : 0);
                }, 0)
            }));
            const exposed = doses.filter(x => x.dose > .001);
            const total = exposed.reduce((sum, x) => sum + x.dose, 0);
            if (total < 6 || exposed.length < 2)
                continue;
            const max = [...exposed].sort((a, b) => b.dose - a.dose || a.sessionIndex - b.sessionIndex)[0];
            const share = max.dose / total;
            if (share > concentrationLimit + .001 && max.movableDose > 1.001)
                findings.push({
                    code: 'DIRECT_DOSE_CONCENTRATION', sessionIndex: max.sessionIndex, sessionName: max.sessionName,
                    detail: `${region} places ${max.dose.toFixed(1)}/${total.toFixed(1)} direct weekly sets (${Math.round(share * 100)}%) in one session despite ${exposed.length} direct exposures.`
                });
        }
    }
    return findings;
}
