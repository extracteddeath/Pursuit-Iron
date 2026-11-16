#!/usr/bin/env node
// Local, offline replay of the current exercise's deliberately exported
// progression evidence. Does not read app storage or contact any service.
// Usage: node scripts/audit-workout-replay.mjs path/to/pursuit-progression-diagnostics.json
import fs from 'node:fs';
import { auditWorkoutProgressionReplay } from '../modules/next-engine/workout-replay.js';

const file = process.argv[2];
if (!file) {
    console.error('Usage: node scripts/audit-workout-replay.mjs <exported-diagnostics.json>');
    process.exitCode = 2;
} else {
    try {
        if (fs.statSync(file).size > 1024 * 1024)
            throw new Error('Diagnostics must be at most 1 MiB.');
        const payload = JSON.parse(fs.readFileSync(file, 'utf8'));
        const violations = auditWorkoutProgressionReplay(payload);
        const result = {
            schemaVersion: 1, audit: 'progression-replay',
            status: violations.length ? 'fail' : 'pass',
            exerciseId: String(payload.exerciseId ?? ''),
            progressionStyle: String(payload.progressionStyle ?? ''),
            unit: payload.unit,
            workSets: (payload.rows || []).filter(row => row.kind === 'work').length,
            completedSets: (payload.rows || []).filter(row => row.kind === 'work' && row.done).length,
            historyExposures: payload.history?.length ?? 0,
            violations
        };
        console.log('WORKOUT_REPLAY_AUDIT ' + JSON.stringify(result));
        if (violations.length) process.exitCode = 1;
    } catch (error) {
        console.error('Progression replay rejected: ' + String(error?.message || error));
        process.exitCode = 2;
    }
}
