import { createExerciseMap } from './exercise-db.js';
import { deriveFunctionalCoverage } from './functional-coverage.js';
import { createMusclePrescriptions } from './prescription.js';
import { methodPolicyForSplit } from './method-policy.js';
function round(value) { return Math.round(value * 10) / 10; }
function priorityRank(p) { return p === 'primary' ? 4 : p === 'specialization' ? 3 : p === 'high' ? 2 : p === 'normal' ? 1 : 0; }
function phraseDose(actual, minimum, preferred, upper) {
    if (actual < minimum - .01)
        return 'below the intended minimum after competing constraints';
    if (actual < preferred - .01)
        return 'between the minimum and preferred productive target';
    if (actual <= preferred + .5)
        return 'at the preferred productive target';
    if (actual <= upper + .01)
        return 'above preferred while remaining inside the recoverable ceiling';
    return 'above the configured recoverable ceiling';
}
function humanMuscle(m) { return m.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase()); }
export function buildProgramExplainability(program, request, extra = []) {
    const methodPolicy = methodPolicyForSplit(program.split.family, program.split.displayName);
    const decisions = [];
    const minutes = request.schedule.days.map(d => d.maxMinutes);
    const minMinutes = request.schedule.days.map(d => d.minMinutes).filter((n) => Number.isFinite(n));
    const locked = request.preferences.lockedSplit === program.split.family;
    const preferred = request.preferences.preferredSplit === program.split.family;
    decisions.push({
        category: 'structure', subject: 'Weekly structure', outcome: `${methodPolicy.publicName} · ${program.sessions.length} days`,
        why: locked ? 'The user locked this structure, so topology search was not allowed to replace it.' : preferred ? 'The user preferred this structure and it remained feasible under the final audit.' : 'Topology search selected this structure from the feasible candidates for the requested frequency, goals and recovery pattern.',
        metrics: { days: program.sessions.length, locked, preferred }
    });
    decisions.push({
        category: 'constraint', subject: 'Session capacity', outcome: `${Math.min(...minutes)}–${Math.max(...minutes)} min available per session`,
        why: 'Time is treated as usable capacity, not a quota to minimize. Productive work can fill the selected bucket while audit prevents overrun.',
        metrics: { minimumRequestedMinutes: minMinutes.length ? Math.min(...minMinutes) : 0, maximumAllowedMinutes: Math.max(...minutes) }
    });
    if (request.equipment.bodyweight === 'exclude')
        decisions.push({ category: 'constraint', subject: 'Bodyweight exercises', outcome: 'Excluded', why: 'The user disabled bodyweight exercises, so they are ineligible during selection and repair.' });
    if (request.restrictions.allowSupersets === false)
        decisions.push({ category: 'constraint', subject: 'Supersets', outcome: 'Disabled', why: 'The user disabled supersets, so generation does not select them or rely on their time savings.' });
    decisions.push({ category: 'constraint', subject: 'Barbell density', outcome: `Up to ${request.restrictions.maxBarbellMovementsPerDay} barbell movements per day`, why: 'The configured barbell-movement ceiling is enforced during realization and audit.', metrics: { maxPerDay: request.restrictions.maxBarbellMovementsPerDay } });
    const prescriptionByMuscle = new Map(createMusclePrescriptions(request, program.phase).map(p => [p.muscle, p]));
    const prioritized = Object.keys(request.goal.musclePriorities)
        .filter(m => priorityRank(request.goal.musclePriorities[m]) >= 2)
        .sort((a, b) => priorityRank(request.goal.musclePriorities[b]) - priorityRank(request.goal.musclePriorities[a]) || a.localeCompare(b));
    for (const muscle of prioritized.slice(0, 8)) {
        const prescription = prescriptionByMuscle.get(muscle);
        const ledger = program.muscleLedger[muscle];
        if (!prescription || !ledger)
            continue;
        const actual = round(ledger.fractionalSets);
        decisions.push({
            category: 'dose', subject: humanMuscle(muscle), outcome: `${actual} effective sets · ${phraseDose(actual, prescription.minimum, prescription.preferred, prescription.upper)}`,
            why: `${request.goal.musclePriorities[muscle]} priority raises this muscle's allocation value, while experience, phase, time and recovery determine the final productive dose.`,
            metrics: { actualSets: actual, minimum: prescription.minimum, preferred: prescription.preferred, upper: prescription.upper, directSets: round(ledger.directSets), priority: request.goal.musclePriorities[muscle] }
        });
    }
    const exerciseMap = createExerciseMap(request.customExercises);
    const functional = deriveFunctionalCoverage(program.sessions, exerciseMap);
    const functionRows = [
        ['hamstrings', 'Hip extension + knee flexion', 'Hamstring work uses complementary hip-extension and knee-flexion functions.', functional.hamstrings.totalDirectSets, functional.hamstrings.hipExtensionSets, functional.hamstrings.kneeFlexionSets],
        ['calves', 'Straight-knee + bent-knee calf work', 'Calf work can distribute stimulus across straight- and bent-knee patterns.', functional.calves.totalDirectSets, functional.calves.straightKneeSets, functional.calves.bentKneeSets],
        ['triceps', 'Lengthened + non-overhead extension', 'Direct triceps work can include both lengthened and non-overhead extension patterns.', functional.triceps.totalDirectSets, functional.triceps.lengthenedSets, functional.triceps.nonOverheadSets],
        ['back', 'Vertical pull + horizontal row', 'Back work can cover both vertical pulling and horizontal rowing.', functional.back.totalDirectSets, functional.back.verticalPullSets, functional.back.horizontalRowSets]
    ];
    for (const [muscle, outcome, why, total, a, b] of functionRows) {
        if (total < 1 || priorityRank(request.goal.musclePriorities[muscle]) < 2)
            continue;
        decisions.push({ category: 'coverage', subject: humanMuscle(muscle), outcome, why, metrics: { meaningfulDirectSets: round(total), functionA: round(a), functionB: round(b) } });
    }
    const lifts = Object.entries(request.goal.liftPriorities).filter(([, p]) => priorityRank(p) >= 2);
    for (const [lift, priority] of lifts) {
        const exposures = program.sessions.flatMap(s => s.exercises).filter(ex => {
            const def = exerciseMap.get(ex.exerciseId);
            return (def?.liftSpecificity?.[lift] ?? 0) >= .45;
        });
        decisions.push({ category: 'strength', subject: lift.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase()), outcome: `${exposures.length} specific weekly exposure${exposures.length === 1 ? '' : 's'}`, why: `${priority} lift priority protects specific practice before optional accessory dose is added.`, metrics: { exposures: exposures.length, priority: String(priority) } });
    }
    decisions.push(...extra.map(x => ({ ...x, evidence: x.evidence ? [...x.evidence] : undefined, metrics: x.metrics ? { ...x.metrics } : undefined })));
    const summary = [
        `${methodPolicy.publicName} was finalized only after the complete program passed the production audit.`,
        `${program.sessions.length} sessions were built for the ${program.phase.replace(/_/g, ' ')} phase and ${request.athlete.experience} experience level.`,
        prioritized.length ? `${humanMuscle(prioritized[0])}${prioritized.length > 1 ? ` plus ${prioritized.length - 1} other priorit${prioritized.length === 2 ? 'y' : 'ies'}` : ''} received explicit priority-aware dose accounting.` : 'No muscle specialization override was requested; dose followed goal, experience, phase and capacity.'
    ];
    return { schemaVersion: 1, methodPolicy, summary, decisions };
}
export function historyDecision(subject, outcome, why, metrics, evidence) {
    return { category: 'history', subject, outcome, why, metrics, evidence };
}
export function withProgramExplainability(program, request, extra = []) {
    const priorHistory = (program.explainability?.decisions ?? []).filter(d => d.category === 'history');
    const merged = [...priorHistory, ...extra];
    return { ...program, explainability: buildProgramExplainability(program, request, merged) };
}
