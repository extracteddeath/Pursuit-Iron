// Pure JS transport dispatcher; no browser, Android or persistence dependencies.
import * as canonical from '../../modules/engine-api.js';
import { canonicalProgressionPreview, validProgressionPreviewParams } from './progression-preview.mjs';
import { canonicalExerciseLookup, validExerciseLookupParams } from './exercise-lookup.mjs';
import { canonicalWorkoutOutline, validWorkoutOutlineParams } from './workout-outline.mjs';
import { canonicalBackupSchemaCheck, canonicalBackupProgramInventory, validBackupSchemaParams } from './backup-schema-check.mjs';

export const PROTOCOL_VERSION = 1;
const own = (value, key) => Object.prototype.hasOwnProperty.call(value, key);
const plain = value => value !== null && typeof value === 'object' &&
    !Array.isArray(value) && (Object.getPrototypeOf(value) === Object.prototype ||
    Object.getPrototypeOf(value) === null);
const error = (id, code, message) => ({
    protocolVersion: PROTOCOL_VERSION, id, ok: false, error: { code, message }
});
const success = (id, result) => ({
    protocolVersion: PROTOCOL_VERSION, id, ok: true, result
});
export function dispatchEngineRequest(request) {
    const id = plain(request) && typeof request.id === 'string' &&
        request.id.length > 0 && request.id.length <= 128 ? request.id : null;
    if (!id) return error(null, 'INVALID_REQUEST', 'A bounded nonempty request id is required');
    if (request.protocolVersion !== PROTOCOL_VERSION)
        return error(id, 'UNSUPPORTED_VERSION', 'Unsupported engine bridge protocol');
    if (typeof request.method !== 'string' || request.method.length > 60 || !plain(request.params))
        return error(id, 'INVALID_REQUEST', 'Method and parameter object required');
    if (request.method === 'progressionPreview') {
        if (!validProgressionPreviewParams(request.params))
            return error(id, 'INVALID_REQUEST', 'Invalid bounded progression preview parameters');
        try {
            return success(id, canonicalProgressionPreview(request.params));
        } catch {
            return error(id, 'ENGINE_UNAVAILABLE', 'Canonical progression preview unavailable');
        }
    }
    if (request.method === 'exerciseLookup') {
        if (!validExerciseLookupParams(request.params))
            return error(id, 'INVALID_REQUEST', 'Invalid bounded exercise lookup parameters');
        try {
            return success(id, canonicalExerciseLookup(request.params));
        } catch {
            return error(id, 'ENGINE_UNAVAILABLE', 'Canonical exercise catalog unavailable');
        }
    }
    if (request.method === 'workoutOutline') {
        if (!validWorkoutOutlineParams(request.params))
            return error(id, 'INVALID_REQUEST', 'Invalid synthetic workout outline parameters');
        try {
            return success(id, canonicalWorkoutOutline(request.params));
        } catch {
            return error(id, 'ENGINE_UNAVAILABLE', 'Canonical workout outline unavailable');
        }
    }
    if (request.method === 'backupSchemaCheck') {
        if (!validBackupSchemaParams(request.params))
            return error(id, 'INVALID_REQUEST', 'Invalid bounded canonical backup input');
        try {
            return success(id, canonicalBackupSchemaCheck(request.params));
        } catch {
            return error(id, 'SCHEMA_REJECTED', 'Canonical v13 backup structure rejected');
        }
    }
    if (request.method === 'backupProgramInventory') {
        if (!validBackupSchemaParams(request.params))
            return error(id, 'INVALID_REQUEST', 'Invalid bounded backup inventory input');
        try {
            return success(id, canonicalBackupProgramInventory(request.params));
        } catch {
            return error(id, 'SCHEMA_REJECTED', 'Backup program inventory unavailable');
        }
    }
    if (Object.keys(request.params).length !== 0)
        return error(id, 'INVALID_REQUEST', 'Unexpected method parameters');
    switch (request.method) {
        case 'handshake':
            return success(id, {
                protocolVersion: PROTOCOL_VERSION,
                engineSource: 'modules/engine-api.js',
                engineCertified: false,
                capabilities: ['handshake', 'catalogSummary', 'progressionPreview', 'exerciseLookup', 'workoutOutline', 'backupSchemaCheck', 'backupProgramInventory']
            });
        case 'catalogSummary': {
            if (!Array.isArray(canonical.EXERCISES) || !Array.isArray(canonical.TEMPLATES))
                return error(id, 'ENGINE_UNAVAILABLE', 'Canonical catalog is unavailable');
            return success(id, {
                exercises: canonical.EXERCISES.length,
                templates: canonical.TEMPLATES.length
            });
        }
        default:
            return error(id, 'UNKNOWN_METHOD', 'Method is not allowlisted');
    }
}
export function dispatchEngineJson(json) {
    if (typeof json !== 'string' || json.length > 16384)
        return JSON.stringify(error(null, 'INVALID_JSON', 'JSON request must be a bounded string'));
    try {
        const input = JSON.parse(json);
        const response = dispatchEngineRequest(input);
        return JSON.stringify(response);
    } catch {
        return JSON.stringify(error(null, 'INVALID_JSON', 'Malformed JSON request'));
    }
}

/**
 * Separate bounded channel because normal engine JSON requests remain 16KiB.
 * This path has no non-schema actions and returns no source data. Java/Kotlin
 * also independently cap the byte count before entering an isolate.
 */
export function dispatchBackupSchemaJson(json) {
    if (typeof json !== 'string' || json.length > 196608)
        return JSON.stringify(error(null, 'INVALID_JSON', 'Oversized read-only schema probe'));
    try {
        const request = JSON.parse(json);
        if (!plain(request) || !['backupSchemaCheck', 'backupProgramInventory'].includes(request.method))
            return JSON.stringify(error(null, 'INVALID_REQUEST', 'Schema probe method required'));
        return JSON.stringify(dispatchEngineRequest(request));
    } catch {
        return JSON.stringify(error(null, 'INVALID_JSON', 'Malformed schema probe request'));
    }
}
