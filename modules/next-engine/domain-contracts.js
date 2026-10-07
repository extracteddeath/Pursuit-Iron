/** Versioned wire records. Domain values stay independent of persistence and UI. */
export const DOMAIN_SCHEMA_VERSION = 1;
export const DOMAIN_RECORD_KINDS = Object.freeze(['request', 'program', 'prescription', 'workout', 'adaptation', 'cycle']);
const object = value => value !== null && typeof value === 'object' && !Array.isArray(value)
    && [Object.prototype, null].includes(Object.getPrototypeOf(value));
const fail = (kind, detail) => { throw new TypeError(`Invalid ${kind} record: ${detail}`); };
const id = value => typeof value === 'string' && value.trim().length > 0;
const pair = value => Array.isArray(value) && value.length === 2
    && value.every(n => typeof n === 'number' && Number.isFinite(n)) && value[0] <= value[1];
const array = (value, kind, field) => { if (!Array.isArray(value)) fail(kind, `${field} must be an array`); };

/** Validate structure here; request normalization and final audits own semantic constraints. */
export function validateDomainValue(kind, value) {
    if (!DOMAIN_RECORD_KINDS.includes(kind)) fail(kind, 'unknown record kind');
    if (!object(value)) fail(kind, 'value must be a plain object');
    if (kind === 'request') {
        for (const field of ['athlete', 'goal', 'schedule', 'equipment'])
            if (!object(value[field])) fail(kind, `${field} must be an object`);
        array(value.schedule.days, kind, 'schedule.days');
    } else if (kind === 'program') {
        array(value.sessions, kind, 'sessions');
        for (const session of value.sessions) {
            if (!object(session) || !id(session.day)) fail(kind, 'session needs a day');
            array(session.exercises, kind, 'session.exercises');
            for (const ex of session.exercises) {
                if (!object(ex) || !id(ex.exerciseId) || !Number.isInteger(ex.sets) || ex.sets < 1
                    || !object(ex.prescription) || !pair(ex.prescription.reps) || !pair(ex.prescription.rir))
                    fail(kind, 'invalid planned exercise');
            }
        }
    } else if (kind === 'prescription') {
        if (!id(value.exerciseId) || !Number.isInteger(value.sets) || value.sets < 1 || value.sets > 20)
            fail(kind, 'exerciseId and bounded working-set count are required');
    } else if (kind === 'workout') {
        if (!id(value.id) || !id(value.programId)) fail(kind, 'id and programId are required');
        if (!object(value.perf)) fail(kind, 'perf must be an object');
        for (const perf of Object.values(value.perf)) {
            if (!object(perf)) fail(kind, 'invalid exercise performance');
            array(perf.sets, kind, 'performance.sets');
        }
    } else if (kind === 'adaptation') {
        array(value.decisions, kind, 'decisions');
    } else if (kind === 'cycle') {
        if (!id(value.phase) || !Number.isInteger(value.workoutsInPhase) || value.workoutsInPhase < 0)
            fail(kind, 'phase and nonnegative workoutsInPhase are required');
    }
    return value;
}

/** Unversioned production values and v0 payload envelopes explicitly migrate to v1 value envelopes.
 * No target/effort/ownership facts are invented. Unknown fields survive the roundtrip. */
export function migrateDomainRecord(kind, input) {
    if (!object(input)) fail(kind, 'record must be a plain object');
    let value;
    if ('kind' in input && 'schemaVersion' in input) {
        if (input.kind !== kind) fail(kind, 'record kind does not match');
        if (input.schemaVersion === 0) value = input.payload;
        else if (input.schemaVersion === DOMAIN_SCHEMA_VERSION) value = input.value;
        else fail(kind, 'unsupported schema version');
    } else {
        if ('schemaVersion' in input && input.schemaVersion !== DOMAIN_SCHEMA_VERSION)
            fail(kind, 'unsupported schema version');
        value = input;
    }
    validateDomainValue(kind, value);
    // structuredClone keeps numeric values honest; it does not convert NaN to null like JSON.
    return { schemaVersion: DOMAIN_SCHEMA_VERSION, kind, value: structuredClone(value) };
}

export function readDomainRecord(kind, input) {
    return migrateDomainRecord(kind, input).value;
}
