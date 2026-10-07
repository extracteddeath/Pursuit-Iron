/** Version headers are distinct from app/store/build/engine versions. */
export const DOMAIN_SCHEMA_VERSIONS = Object.freeze({ request: 1, program: 1, prescription: 1, exposure: 1, response: 1 });

export class DomainContractError extends Error {
    constructor(code, path, message) { super(message); this.name = 'DomainContractError'; this.code = code; this.path = path; }
}
const record = x => x !== null && typeof x === 'object' && !Array.isArray(x);
const requireValue = (condition, path, message) => {
    if (!condition) throw new DomainContractError('INVALID_DOMAIN_RECORD', path, message);
};

/** Explicit legacy migration: unversioned records receive v1; unknown versions are never guessed. */
export function migrateDomainRecord(kind, input) {
    const current = DOMAIN_SCHEMA_VERSIONS[kind];
    if (!current) throw new DomainContractError('UNKNOWN_DOMAIN', kind, `Unknown domain contract: ${kind}.`);
    requireValue(record(input), kind, `A ${kind} object is required.`);
    const version = input.schemaVersion;
    if (version !== undefined && version !== current)
        throw new DomainContractError('UNSUPPORTED_DOMAIN_SCHEMA', `${kind}.schemaVersion`, `Unsupported ${kind} schema version: ${String(version)}. Update before interpreting this record.`);
    return { ...input, schemaVersion: current };
}

export function validatePrescription(input, path = 'prescription') {
    requireValue(record(input), path, 'A prescription object is required.');
    for (const [key, minimum] of [['reps', 1], ['rir', 0]]) {
        const pair = input[key];
        requireValue(Array.isArray(pair) && pair.length === 2 && pair.every(Number.isFinite)
            && pair[0] >= minimum && pair[1] >= pair[0] && (key !== 'rir' || pair[1] <= 10), `${path}.${key}`, `Invalid ${key} range.`);
    }
    requireValue(Number.isFinite(input.restSeconds) && input.restSeconds >= 0, `${path}.restSeconds`, 'Rest must be a finite non-negative duration.');
    return input;
}

/** Structural validation complements the arbiter; it does not downgrade physiological/intent findings. */
export function validateEngineProgram(input) {
    const program = migrateDomainRecord('program', input);
    requireValue(typeof program.id === 'string' && program.id.length > 0, 'program.id', 'A stable program ID is required.');
    requireValue(Array.isArray(program.sessions), 'program.sessions', 'Program sessions must be a list.');
    const ids = new Set();
    for (const [i, session] of program.sessions.entries()) {
        const path = `program.sessions[${i}]`;
        requireValue(record(session) && typeof session.id === 'string' && !ids.has(session.id), `${path}.id`, 'Session IDs must be stable and unique.');
        ids.add(session.id);
        requireValue(Number.isFinite(session.maxMinutes) && session.maxMinutes > 0, `${path}.maxMinutes`, 'A finite positive session time limit is required.');
        requireValue(Number.isFinite(session.estimatedMinutes) && session.estimatedMinutes >= 0, `${path}.estimatedMinutes`, 'Session estimates must be finite and non-negative.');
        requireValue(Array.isArray(session.exercises), `${path}.exercises`, 'Session exercises must be a list.');
        for (const [slot, exercise] of session.exercises.entries()) {
            const prefix = `${path}.exercises[${slot}]`;
            requireValue(record(exercise) && typeof exercise.exerciseId === 'string' && exercise.exerciseId.length > 0, `${prefix}.exerciseId`, 'A stable exercise ID is required.');
            requireValue(Number.isInteger(exercise.sets) && exercise.sets >= 1 && exercise.sets <= 20, `${prefix}.sets`, 'Working sets must be a whole number from 1 to 20.');
            validatePrescription(exercise.prescription, `${prefix}.prescription`);
        }
    }
    return program;
}

export function validateHistoryExposure(input) {
    const exposure = migrateDomainRecord('exposure', input);
    requireValue(typeof exposure.exerciseId === 'string' && exposure.exerciseId.length > 0, 'exposure.exerciseId', 'An exposure exercise ID is required.');
    requireValue(typeof exposure.completedAt === 'string' && Number.isFinite(Date.parse(exposure.completedAt)), 'exposure.completedAt', 'A valid exposure timestamp is required.');
    requireValue(Array.isArray(exposure.sets), 'exposure.sets', 'Exposure working sets must be a list.');
    for (const [i, set] of exposure.sets.entries()) {
        const prefix = `exposure.sets[${i}]`;
        requireValue(record(set) && Number.isFinite(set.load), `${prefix}.load`, 'Observed load must be finite; signed assistance remains supported.');
        requireValue(Number.isInteger(set.reps) && set.reps > 0, `${prefix}.reps`, 'Completed repetitions must be a positive integer.');
        requireValue(set.rir === null || (Number.isFinite(set.rir) && set.rir >= 0 && set.rir <= 10), `${prefix}.rir`, 'Observed RIR must be null or a finite value from 0 to 10.');
    }
    return exposure;
}

/** Machine-readable shape descriptors; semantic bounds remain in the validators above. */
export const DOMAIN_SCHEMAS = Object.freeze({
    request: Object.freeze({ version: 1, required: Object.freeze(['athlete', 'goal', 'schedule', 'equipment']), legacyVersion: 'unversioned' }),
    program: Object.freeze({ version: 1, required: Object.freeze(['id', 'sessions']), legacyVersion: 'unversioned' }),
    prescription: Object.freeze({ version: 1, required: Object.freeze(['reps', 'rir', 'restSeconds']), legacyVersion: 'unversioned' }),
    exposure: Object.freeze({ version: 1, required: Object.freeze(['exerciseId', 'completedAt', 'sets']), legacyVersion: 'unversioned' })
});
