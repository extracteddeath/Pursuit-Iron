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
        // A separate history-to-target invariant catches rep-only load drift even when
        // an exported live row has no decisionTrace. This never changes a workout.
        const historicalLoadIssues = [];
        if (payload.progressionStyle === 'double'
            && ['add_reps', 'hold', 'hold_load'].includes(payload.suggestion?.action)) {
            const comparable = Array.isArray(payload.history) && payload.history.find(entry =>
                entry?.weekIndex === payload.weekIndex && entry?.unit === payload.unit
                && Array.isArray(entry.sets) && entry.sets.length > 0);
            if (comparable) {
                const byIndex = new Map(), duplicate = new Set();
                for (const source of comparable.sets) {
                    if (!Number.isInteger(source.setIndex) || source.setIndex < 0) continue;
                    if (byIndex.has(source.setIndex)) duplicate.add(source.setIndex);
                    else byIndex.set(source.setIndex, source);
                }
                for (const row of payload.rows || []) {
                    if (row.kind !== 'work' || row.done || row.owner !== 'prescription'
                        || row.readinessFactor != null && row.readinessFactor !== 1
                        || !Number.isInteger(row.workingSetIndex)) continue;
                    const prior = byIndex.get(row.workingSetIndex);
                    if (!prior || duplicate.has(row.workingSetIndex)
                        || !(prior.load > 0)) continue;
                    const target = row.target?.load;
                    if (Number.isFinite(target) && Math.abs(target - prior.load) > 0.011)
                        historicalLoadIssues.push({
                            code: 'unearned_double_progression_history_load',
                            workingSetIndex: row.workingSetIndex,
                            previousLoad: prior.load, proposedLoad: target
                        });
                    const proposed = payload.suggestion?.setTargets?.[row.workingSetIndex]?.load;
                    if (Number.isFinite(proposed) && Math.abs(proposed - prior.load) > 0.011)
                        historicalLoadIssues.push({
                            code: 'unearned_double_progression_suggestion_load',
                            workingSetIndex: row.workingSetIndex,
                            previousLoad: prior.load, proposedLoad: proposed
                        });
                }
            }
        }
        const violations = [...auditWorkoutProgressionReplay(payload), ...historicalLoadIssues];
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
