import { createExerciseCatalog } from './exercise-db.js';
import { exerciseKnowledgeNode, exerciseSelectionRelationship } from './exercise-selection-intelligence.js';
import { convertHistoryLoad, progressionExposureContext } from './history-contract.js';
import { DomainContractError } from './domain-contracts.js';

const conventions = new Set(['barbell_total', 'per_hand', 'machine_stack', 'bodyweight', 'assistance', 'unknown']);
const roms = new Set(['full', 'partial', 'unknown']);
const plain = x => !!x && typeof x === 'object' && !Array.isArray(x);
const fullRomStrength = new Set(['barbell_bench', 'paused_bench', 'back_squat', 'deadlift', 'barbell_ohp', 'overhead_press']);
const jointActions = Object.freeze({ horizontal_press: ['shoulder_horizontal_adduction', 'elbow_extension'], chest_adduction: ['shoulder_horizontal_adduction'],
    vertical_press: ['shoulder_flexion', 'elbow_extension'], vertical_pull: ['shoulder_adduction', 'elbow_flexion'], horizontal_pull: ['shoulder_extension', 'elbow_flexion', 'scapular_retraction'],
    shoulder_extension: ['shoulder_extension'], lateral_raise: ['shoulder_abduction'], rear_delt: ['shoulder_horizontal_abduction'], elbow_flexion: ['elbow_flexion'], elbow_extension: ['elbow_extension'],
    squat: ['knee_extension', 'hip_extension'], leg_press: ['knee_extension', 'hip_extension'], knee_extension: ['knee_extension'], knee_flexion: ['knee_flexion'],
    hip_hinge: ['hip_extension'], hip_extension: ['hip_extension'], calf: ['ankle_plantar_flexion'] });

/** Explicit custom semantic claims are versioned; names never certify custom mechanics. */
export function validateCustomExerciseSemantics(input) {
    if (!plain(input) || input.schemaVersion !== 1 || typeof input.movementSubslot !== 'string' || !input.movementSubslot.trim()
        || !Array.isArray(input.jointActions) || !input.jointActions.length || input.jointActions.some(x => typeof x !== 'string' || !x.trim())
        || !Array.isArray(input.functionalBiases) || input.functionalBiases.some(x => typeof x !== 'string' || !x.trim())
        || !conventions.has(input.loadConvention) || !roms.has(input.rangeOfMotion) || !['bilateral', 'unilateral'].includes(input.laterality))
        throw new DomainContractError('INVALID_EXERCISE_SEMANTICS', 'customExercise.semanticMetadata', 'Custom semantics require v1, explicit movement/joint/function/laterality/ROM and load convention metadata.');
    return Object.freeze({ ...input, movementSubslot: input.movementSubslot.trim(), jointActions: Object.freeze([...new Set(input.jointActions)].sort()),
        functionalBiases: Object.freeze([...new Set(input.functionalBiases)].sort()) });
}

export function semanticExerciseNode(def) {
    if (!def) return null;
    const base = exerciseKnowledgeNode(def), custom = def.source === 'custom', metadata = def.semanticMetadata;
    const reviewed = !custom || (def.metadataConfidence === 'high' && metadata !== undefined);
    const explicit = metadata === undefined ? null : validateCustomExerciseSemantics(metadata);
    const specificBarbell = !!def.flags?.barbell && Math.max(0, ...Object.values(def.liftSpecificity ?? {})) >= .7;
    return Object.freeze({ ...base, schemaVersion: 1, semanticConfidence: reviewed ? 'reviewed' : 'unknown',
        movementSubslot: custom ? explicit?.movementSubslot ?? null : base.movementSubslot,
        functionalBiases: custom ? explicit?.functionalBiases ?? Object.freeze([]) : base.functionalBiases,
        jointActions: custom ? explicit?.jointActions ?? Object.freeze([]) : Object.freeze([...(jointActions[base.movementPattern] ?? [base.movementPattern])].sort()),
        unilateral: custom ? explicit?.laterality === 'unilateral' : base.unilateral,
        rangeOfMotion: explicit?.rangeOfMotion ?? (fullRomStrength.has(def.id) ? 'full' : 'unknown'),
        loadConvention: explicit?.loadConvention ?? (specificBarbell ? 'barbell_total' : def.flags?.bodyweight ? 'bodyweight' : def.equipment?.includes('dumbbell') ? 'per_hand' : 'unknown') });
}

function eligible(def, request, day) {
    if (!def || request?.preferences?.avoidedExercises?.includes(def.id)) return false;
    if (!request) return true;
    const schedule = request.schedule?.days?.find(d => d.day === day);
    if (!schedule) return false;
    const available = schedule.equipmentOverride ?? request.equipment.available;
    if (def.flags?.bodyweight && request.equipment.bodyweight === 'exclude') return false;
    return [def.equipment, ...(def.equipmentAlternatives ?? [])].some(setup => Array.isArray(setup)
        && setup.every(item => item === 'bodyweight' ? request.equipment.bodyweight !== 'exclude' : available.includes(item)));
}
const common = (a, b) => a.filter(x => b.includes(x));

/** Intent transfer and numeric load transfer are independent decisions. Every numeric transfer is reference-only. */
export function evaluateExerciseTransfer(from, to, { role = 'hypertrophy_compound', request, day } = {}) {
    const reject = reason => Object.freeze({ allowed: false, loadReferenceAllowed: false, referenceFraction: null, referenceOnly: true, earnedProgression: false, reason });
    if (!from || !to) return reject('unknown_identity');
    if (!eligible(to, request, day) || !eligible(from, request, day)) return reject('equipment_or_avoidance');
    if (from.id === to.id) return Object.freeze({ allowed: true, loadReferenceAllowed: true, referenceFraction: 1, referenceOnly: true, earnedProgression: false, reason: 'exact_identity' });
    const a = semanticExerciseNode(from), b = semanticExerciseNode(to);
    if (a.semanticConfidence !== 'reviewed' || b.semanticConfidence !== 'reviewed') return reject('unknown_semantics');
    const primary = common(a.primaryStimulus, b.primaryStimulus);
    if (!primary.length || a.compound !== b.compound || a.unilateral !== b.unilateral) return reject('stimulus_or_execution_mismatch');
    const strength = role === 'primary_strength' || role === 'secondary_strength';
    const lift = common(Object.keys(a.strengthSpecificity), Object.keys(b.strengthSpecificity))
        .find(id => (a.strengthSpecificity[id] ?? 0) >= (role === 'primary_strength' ? .85 : .5) && (b.strengthSpecificity[id] ?? 0) >= (role === 'primary_strength' ? .85 : .5));
    if (strength && !lift) return reject('strength_specificity_mismatch');
    const sameSubslot = a.movementSubslot !== null && a.movementSubslot === b.movementSubslot;
    const sameFunctions = a.functionalBiases.length === b.functionalBiases.length && common(a.functionalBiases, b.functionalBiases).length === a.functionalBiases.length;
    const sameJoints = a.jointActions.length === b.jointActions.length && common(a.jointActions, b.jointActions).length === a.jointActions.length;
    const relationship = exerciseSelectionRelationship(from, to);
    if (!sameSubslot || !sameFunctions || !sameJoints || relationship.stimulusSimilarity < .5) return reject('complementary_function_or_pattern');
    const numeric = a.loadConvention === 'barbell_total' && b.loadConvention === 'barbell_total' && a.rangeOfMotion === 'full' && b.rangeOfMotion === 'full' && !!lift;
    return Object.freeze({ allowed: true, loadReferenceAllowed: numeric, referenceFraction: numeric ? .8 : null,
        referenceOnly: true, earnedProgression: false, reason: numeric ? 'conservative_specific_barbell_reference' : 'intent_only_no_numeric_equivalence' });
}

/** Lazy immutable graph over canonical catalog nodes. Edges carry typed semantic/transfer evidence. */
export function createSemanticExerciseGraph(customExercises = []) {
    const catalog = createExerciseCatalog(customExercises).map(def => structuredClone(def)), defs = new Map(catalog.map(def => [def.id, def])), nodes = new Map(catalog.map(def => [def.id, semanticExerciseNode(def)])), edges = new Map();
    return Object.freeze({ size: nodes.size, node: id => nodes.get(id) ?? null,
        transfer: (fromId, toId, context) => evaluateExerciseTransfer(defs.get(fromId), defs.get(toId), context),
        relations(id) {
            if (!defs.has(id)) return Object.freeze([]);
            if (edges.has(id)) return edges.get(id);
            const source = defs.get(id), a = nodes.get(id), result = [];
            for (const candidate of catalog) {
                if (candidate.id === id) continue;
                const b = nodes.get(candidate.id); if (a.semanticConfidence !== 'reviewed' || b.semanticConfidence !== 'reviewed') continue;
                if (!common(a.primaryStimulus, b.primaryStimulus).length) continue;
                const decision = evaluateExerciseTransfer(source, candidate), relationship = exerciseSelectionRelationship(source, candidate);
                result.push(Object.freeze({ targetId: candidate.id, type: decision.allowed ? 'intent_transfer' : 'complementary_stimulus',
                    score: relationship.score, ...decision }));
            }
            const value = Object.freeze(result.sort((a, b) => b.score - a.score || a.targetId.localeCompare(b.targetId))); edges.set(id, value); return value;
        } });
}

export function transferExerciseStartingReference({ graph, fromId, toId, sets, sourceUnit, targetUnit, context, snapLoad }) {
    const decision = graph.transfer(fromId, toId, context);
    if (!decision.loadReferenceAllowed || !['lb', 'kg'].includes(sourceUnit) || !['lb', 'kg'].includes(targetUnit)) return { ...decision, load: null };
    const completed = (Array.isArray(sets) ? sets : []).filter(s => s?.done === true && !s.warm && !s.sub);
    const flags = progressionExposureContext(completed, context);
    if (flags.interrupted || flags.nonComparable || flags.badDay || completed.length < 2
        || completed.some(s => s.painFlag === true || s.techniqueQuality === 'poor' || !Number.isFinite(s.load) || s.load <= 0 || !Number.isInteger(s.reps) || s.reps <= 0 || s.rir === null || !Number.isFinite(s.rir) || s.rir < 0 || s.rir > 10))
        return { ...decision, load: null, reason: 'insufficient_comparable_observed_source' };
    const source = Math.min(...completed.map(s => convertHistoryLoad(s.load, sourceUnit, targetUnit))), raw = source * decision.referenceFraction;
    const load = typeof snapLoad === 'function' ? snapLoad(raw) : null;
    return { ...decision, load: Number.isFinite(load) && load > 0 && load <= raw ? load : null, sourceExerciseId: fromId, targetExerciseId: toId };
}
