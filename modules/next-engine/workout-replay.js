// Explicit, local-only capture of the facts that produced an in-workout set.
// This payload intentionally excludes names, free-text notes, injury reports,
// profile, timestamps and complete program/history archives. Sharing is user-initiated.
// All fields are allowlisted rather than copied from untrusted saved objects.
import { completedHistorySets, observedHistoryRIR } from './history-contract.js';

const numeric = value => value === null || value === undefined || value === ''
    ? null : Number.isFinite(Number(value)) ? Number(value) : null;
const ordinal = value => Number.isInteger(value) && value >= 0 ? value : null;
const owner = row => row?.valueOwner === 'user' || row?.auto === false ? 'user'
    : row?.valueOwner === 'prescription' && row?.auto === true ? 'prescription' : 'unknown';
const sourceSet = (set, fallbackIndex) => ({
    setIndex: ordinal(set?.workingSetIndex) ?? fallbackIndex,
    load: numeric(set?.w), reps: numeric(set?.r),
    reportedRir: observedHistoryRIR(set)
});
// Legacy history can retain an incomplete working row without workingSetIndex.
// Match the Workout/LAST source-of-truth: count original working ordinals before
// excluding incomplete observations. Null, warmup and extension rows are not
// working positions. Compressing completed rows first falsely reassigns set 3
// to set 2 and can make a replay audit certify an unearned load change.
const positionedHistorySources = perf => {
    const completed = new Set(completedHistorySets(perf));
    return (Array.isArray(perf?.sets) ? perf.sets : [])
        .filter(set => set && !set.warm && !set.sub)
        .flatMap((set, index) => completed.has(set) ? [sourceSet(set, index)] : []);
};
const simplifiedTrace = value => value?.schemaVersion === 1 ? {
    schemaVersion: 1,
    workingSetIndex: ordinal(value.workingSetIndex),
    source: value.source ? {
        load: numeric(value.source.load), reps: numeric(value.source.reps),
        reportedRir: numeric(value.source.reportedRir),
        originalLoad: numeric(value.source.originalLoad),
        originalUnit: value.source.originalUnit === 'kg' ? 'kg' : 'lb'
    } : null,
    evaluation: {
        action: String(value.evaluation?.action || '').slice(0, 50),
        reasonCode: String(value.evaluation?.reasonCode || '').slice(0, 80),
        safeguards: Array.isArray(value.evaluation?.safeguards) ?
            value.evaluation.safeguards.map(s => String(s).slice(0, 80)).slice(0, 8) : []
    },
    target: { load: numeric(value.target?.load), reps: numeric(value.target?.reps) },
    ownership: value.ownership === 'user' ? 'user' : 'prescription'
} : null;
const liveDecision = value => value?.schemaVersion === 1 ? {
    action: String(value.action || '').slice(0, 50),
    reasonCode: String(value.reasonCode || '').slice(0, 80),
    sourceIndices: Array.isArray(value.sourceIndices) ?
        value.sourceIndices.filter(Number.isInteger).slice(0, 10) : [],
    sourceObservations: Array.isArray(value.sourceObservations)
        ? value.sourceObservations.slice(0, 10).map(observation => ({
            index: ordinal(observation.index), load: numeric(observation.load),
            reps: numeric(observation.reps), actualRIR: numeric(observation.actualRIR)
        })) : null,
    pendingIndex: ordinal(value.pendingIndex),
    fromLoad: numeric(value.fromLoad), toLoad: numeric(value.toLoad),
    fromReps: numeric(value.fromReps), toReps: numeric(value.toReps)
} : null;

// This captures a single exercise only, including its current manual edits.
// Stable serialization makes two snapshots directly diffable without dates/IDs.
export function captureWorkoutProgressionReplay({ program, day, row, suggestion, cell, history, weekIndex, unit, readiness } = {}) {
    if (!program || !day || !row || !Array.isArray(row.sets)
        || !['kg', 'lb'].includes(unit) || !Number.isInteger(weekIndex))
        throw new Error('An active, scoped workout is required for progression diagnostics.');
    const id = String(row.id || '');
    const slot = ordinal(row.slot);
    if (!id || slot === null) throw new Error('Exercise identity/slot is missing.');
    const recent = (Array.isArray(history) ? history : [])
        .filter(entry => entry?.programId === program.id && entry?.dayId === day.id
            && entry?.perf?.[id] && Array.isArray(entry.perf[id].sets))
        .sort((a, b) => Number(b.date || 0) - Number(a.date || 0)).slice(0, 8)
        .map(entry => ({
            weekIndex: ordinal(entry.weekIndex), unit: entry.unit === 'kg' ? 'kg' : 'lb',
            sets: positionedHistorySources(entry.perf[id])
        }));
    let workIndex = 0;
    const rows = row.sets.map((set, index) => {
        const kind = set?.warm ? 'warmup' : set?.sub ? 'extension' : 'work';
        const workingSetIndex = kind === 'work' ? workIndex++ : null;
        return {
            rowIndex: index, workingSetIndex, kind, done: set?.done === true,
            owner: owner(set), load: numeric(set?.weight), reps: numeric(set?.reps),
            actualRir: numeric(set?.actualRIR),
            target: { load: numeric(set?.target?.w), reps: numeric(set?.target?.prefillReps),
                range: typeof set?.target?.reps === 'string' ? set.target.reps.slice(0, 30) : null },
            trace: simplifiedTrace(set?.target?.decisionTrace),
            liveAdjustment: liveDecision(set?.liveAdjustment),
            loadAdvice: set?.loadAdvice ? {
                fromLoad: numeric(set.loadAdvice.fromLoad), toLoad: numeric(set.loadAdvice.toLoad),
                reasonCode: String(set.loadAdvice.reasonCode || '').slice(0, 80)
            } : null,
            readinessFactor: numeric(set?.readinessFactor),
            readinessBaseWeight: numeric(set?.readinessBaseWeight),
            retuneFrom: set?.retuneFrom ? {
                load: numeric(set.retuneFrom.weight), reps: numeric(set.retuneFrom.reps)
            } : null
        };
    });
    return {
        schemaVersion: 1, kind: 'pursuit-iron-progression-replay',
        exerciseId: id, slot, weekIndex, unit,
        programKind: program.custom && program.engineSource !== 'pursuit-next' ? 'custom' : 'generated',
        progressionStyle: String(cell?.progressionStyle || 'auto').slice(0, 32),
        intended: { sets: numeric(cell?.sets), repRange: String(cell?.reps || cell?.range || '').slice(0, 30),
            rir: String(cell?.rir ?? '').slice(0, 30) },
        readinessFactor: numeric(readiness?.factor),
        suggestion: suggestion ? {
            action: String(suggestion.action || '').slice(0, 50),
            reasonCode: String(suggestion.reasonCode || '').slice(0, 80),
            load: numeric(suggestion.weight), reps: numeric(suggestion.target),
            setTargets: Array.isArray(suggestion.setTargets) ?
                suggestion.setTargets.map(t => ({ load: numeric(t.weight), reps: numeric(t.reps) })).slice(0, 20) : null,
            optionalLowerLoad: numeric(suggestion.optionalLowerLoad)
        } : null,
        history: recent, rows
    };
}

// Used by tests and support diagnostics. Never mistake a user edit, a readiness
// adjustment or an opt-in lower load for an engine-owned silent mutation.
export function auditWorkoutProgressionReplay(record) {
    if (record?.schemaVersion !== 1 || record?.kind !== 'pursuit-iron-progression-replay'
        || !Array.isArray(record.rows)) return [{code:'invalid_replay_schema'}];
    const issues = [];
    for (const row of record.rows) {
        if (row.kind !== 'work' || row.done || row.owner !== 'prescription') continue;
        const adjustment = row.liveAdjustment;
        if (adjustment && Array.isArray(adjustment.sourceIndices)) {
            const snapshots = adjustment.sourceObservations;
            const supported = Array.isArray(snapshots)
                && snapshots.length === adjustment.sourceIndices.length
                && adjustment.pendingIndex === row.rowIndex
                && snapshots.every((snapshot, i) => {
                    const source = record.rows[adjustment.sourceIndices[i]];
                    return source?.rowIndex === snapshot.index && source?.done
                        && source.load === snapshot.load && source.reps === snapshot.reps
                        && source.actualRir === snapshot.actualRIR;
                });
            if (!supported) issues.push({
                code:'stale_or_unverifiable_live_adjustment',
                workingSetIndex:row.workingSetIndex
            });
        }
        // A proposal is never consent. Older sessions can carry a valid
        // two-miss evidence fingerprint yet still have an app-owned pending load
        // silently overwritten. Check the pre-advice snapshot independently of
        // the evidence; a manually accepted edit has user ownership instead.
        if (adjustment?.action === 'suggest_pending_load_reduction') {
            const original = row.retuneFrom?.load;
            const advice = row.loadAdvice;
            const sameLoad = original != null && row.load === original
                && (row.readinessFactor != null || row.target.load == null
                    || row.target.load === original);
            if (!sameLoad)
                issues.push({ code:'unapproved_pending_load_change',
                    workingSetIndex:row.workingSetIndex });
            if (!advice || advice.fromLoad !== original
                || advice.fromLoad !== adjustment.fromLoad
                || advice.toLoad !== adjustment.toLoad
                || !(advice.toLoad > 0 && advice.toLoad < advice.fromLoad)
                || advice.reasonCode !== adjustment.reasonCode)
                issues.push({ code:'invalid_live_load_advice',
                    workingSetIndex:row.workingSetIndex });
        }
        // No current engine path authorizes an automatic pending load cut.
        // Legacy adjustment labels are evidence of an action, not user consent.
        if (adjustment?.action === 'reduce_pending_load')
            issues.push({ code:'unapproved_automatic_load_reduction',
                workingSetIndex:row.workingSetIndex });
        const pending = !row.liveAdjustment && row.readinessFactor == null;
        if (pending && row.target.load != null && row.load !== row.target.load)
            issues.push({ code:'unexplained_pending_load', workingSetIndex:row.workingSetIndex });
        if (pending && row.target.reps != null && row.reps !== row.target.reps)
            issues.push({ code:'unexplained_pending_reps', workingSetIndex:row.workingSetIndex });
        if (row.trace && row.target.load != null && row.trace.target.load !== row.target.load
            && row.readinessFactor == null)
            issues.push({ code:'target_trace_load_mismatch', workingSetIndex:row.workingSetIndex });
        if (row.trace && record.progressionStyle === 'double'
            && ['add_reps','hold','hold_load'].includes(row.trace.evaluation.action)
            && row.trace.source?.load > 0 && row.trace.target.load > 0
            && Math.abs(row.trace.source.load - row.trace.target.load) > .000001)
            issues.push({ code:'unearned_double_progression_load', workingSetIndex:row.workingSetIndex });
    }
    // Independent same-position provenance for classic rep-only progression.
    // Both the app and the opt-in CLI share this exact audit.
    if (record.progressionStyle === 'double'
        && ['add_reps', 'hold', 'hold_load'].includes(record.suggestion?.action)) {
        const comparable = Array.isArray(record.history) && record.history.find(entry =>
            entry?.weekIndex === record.weekIndex && entry?.unit === record.unit
            && Array.isArray(entry.sets) && entry.sets.length);
        if (comparable) {
            const byIndex = new Map(), duplicates = new Set();
            for (const prior of comparable.sets) {
                if (!Number.isInteger(prior?.setIndex) || prior.setIndex < 0) continue;
                if (byIndex.has(prior.setIndex)) duplicates.add(prior.setIndex);
                else byIndex.set(prior.setIndex, prior);
            }
            for (const row of record.rows) {
                if (row.kind !== 'work' || row.done || row.owner !== 'prescription'
                    || (row.readinessFactor != null && row.readinessFactor !== 1)
                    || !Number.isInteger(row.workingSetIndex)) continue;
                const source = byIndex.get(row.workingSetIndex);
                if (!source || duplicates.has(row.workingSetIndex) || !(source.load > 0)) continue;
                const target = row.target?.load;
                if (Number.isFinite(target) && Math.abs(target - source.load) > .011)
                    issues.push({ code: 'unearned_double_progression_history_load',
                        workingSetIndex: row.workingSetIndex,
                        previousLoad: source.load, proposedLoad: target });
                const proposed = record.suggestion?.setTargets?.[row.workingSetIndex]?.load;
                if (Number.isFinite(proposed) && Math.abs(proposed - source.load) > .011)
                    issues.push({ code: 'unearned_double_progression_suggestion_load',
                        workingSetIndex: row.workingSetIndex,
                        previousLoad: source.load, proposedLoad: proposed });
            }
        }
    }
    return issues;
}
