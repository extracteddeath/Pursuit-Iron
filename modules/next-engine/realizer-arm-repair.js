// M230 canonical arm-repair stage. Stage context is local to one realization.
import { armCoverageBias, evaluateArmCoverage } from './arm-coverage.js';
import { avoidableExerciseOverlap } from './exercise-economy.js';
import { makePlanned } from './realizer-prescriptions.js';
import { equipmentEligible, intentAcceptsMuscle } from './realizer-ranking.js';
import { estimateMinutes } from './realizer-time-budget.js';

export function repairMajorArmCoverage(context, ...args) {
const { exerciseCatalog, exerciseMap, fitProposalBySafeDosage, policy, realized, request, trackExerciseUse } = context;
return (
/* M230:PRESERVE:helper.repairMajorArmCoverage:BEGIN */
() => {
        const biasForCode = (code) => code === 'ARM_BICEPS_BIAS_MISSING' ? 'biceps_bias' :
            code === 'ARM_BRACHIALIS_BIAS_MISSING' || code === 'ARM_BRACHIORADIALIS_UNDERSERVED' ? 'brachialis_bias' :
                code === 'WRIST_FLEXION_MISSING' ? 'wrist_flexion' :
                    code === 'WRIST_EXTENSION_MISSING' ? 'wrist_extension' : null;
        const muscleForBias = (bias) => bias === 'biceps_bias' || bias === 'brachialis_bias' ? 'biceps' : 'forearms';
        const intentRank = (intent, muscle) => {
            const order = muscle === 'forearms'
                ? ['arms', 'shoulders_arms', 'limbs', 'pull', 'upper', 'back_day', 'full', 'strength_full']
                : ['arms', 'shoulders_arms', 'pull', 'back_day', 'upper', 'limbs', 'full', 'strength_full'];
            const i = order.indexOf(intent);
            return i < 0 ? 99 : i;
        };
        for (let guard = 0; guard < 8; guard++) {
            const finding = evaluateArmCoverage(realized, request, exerciseMap).findings.find(f => f.severity === 'major');
            if (!finding)
                break;
            const bias = biasForCode(finding.code);
            if (!bias)
                break;
            const muscle = muscleForBias(bias);
            const defs = exerciseCatalog
                .filter(def => armCoverageBias(def) === bias)
                .filter(def => !request.preferences.avoidedExercises?.includes(def.id))
                .sort((a, b) => b.suitability.hypertrophy - a.suitability.hypertrophy || a.setupCost - b.setupCost || a.id.localeCompare(b.id));
            const destinations = realized
                .filter(session => intentAcceptsMuscle(session.intent, muscle))
                .sort((a, b) => intentRank(a.intent, muscle) - intentRank(b.intent, muscle) || (b.maxMinutes - b.estimatedMinutes) - (a.maxMinutes - a.estimatedMinutes) || a.id.localeCompare(b.id));
            let repaired = false;
            for (const session of destinations) {
                if (session.targetExercises !== undefined && session.exercises.length >= session.targetExercises + 2)
                    continue;
                for (const def of defs) {
                    if (!equipmentEligible(def, session, request))
                        continue;
                    if (session.exercises.some(ex => ex.exerciseId === def.id))
                        continue;
                    const chosen = session.exercises.map(ex => ({ def: exerciseMap.get(ex.exerciseId), role: ex.role })).filter((x) => !!x.def);
                    if (avoidableExerciseOverlap(def, 'hypertrophy_isolation', chosen, { priority: request.goal.musclePriorities[muscle] ?? 'normal' }))
                        continue;
                    const added = makePlanned(def, 'hypertrophy_isolation', 1, policy, request.athlete.experience);
                    const initial = [...session.exercises, added];
                    const fitted = fitProposalBySafeDosage(session, initial, initial.length - 1);
                    if (!fitted)
                        continue;
                    session.exercises = fitted;
                    session.estimatedMinutes = estimateMinutes(fitted);
                    trackExerciseUse(def);
                    repaired = true;
                    break;
                }
                if (repaired)
                    break;
            }
            if (!repaired)
                break;
        }
    }
/* M230:PRESERVE:helper.repairMajorArmCoverage:END */
)(...args);
}
