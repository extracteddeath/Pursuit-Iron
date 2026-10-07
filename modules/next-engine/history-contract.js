// Shared boundary rules for imported history and app-authored workout logs.
// Targets are not observations; missing numbers never become zero.
export function historyNumber(value) {
    if (!['number', 'string'].includes(typeof value) || (typeof value === 'string' && !value.trim())) return null;
    const n = Number(value);
    return Number.isFinite(n) ? n : null;
}
export function convertHistoryLoad(value, fromUnit, toUnit) {
    const n = historyNumber(value);
    if (n === null) return null;
    const from = String(fromUnit || toUnit || 'lb').trim().toLowerCase();
    const to = String(toUnit || from).trim().toLowerCase();
    if (from === to) return n;
    if (from === 'kg' && to === 'lb') return n * 2.2046226218487757;
    if (from === 'lb' && to === 'kg') return n / 2.2046226218487757;
    return n;
}
export function observedHistoryRIR(set) {
    const rir = historyNumber(set?.rir), target = historyNumber(set?.tr);
    const reported = set?.rirReported != null ? set.rirReported === true
        : target === null || (rir !== null && Math.abs(rir - target) > .001);
    return rir !== null && reported ? Math.max(0, Math.min(10, rir)) : null;
}
export function completedHistorySets(perf) {
    return (Array.isArray(perf?.sets) ? perf.sets : []).filter(s => s && !s.sub && !s.warm
        && s.done !== false && historyNumber(s.r) > 0);
}
export function attemptedHistorySets(perf) {
    return (Array.isArray(perf?.sets) ? perf.sets : []).filter(s => s && !s.sub && !s.warm
        && s.done !== false && (historyNumber(s.r) > 0 || (historyNumber(s.r) === 0 && s.failedAttempt === true)));
}
export function historyExposureContext(value) {
    const keys = ['readinessStatus', 'readiness', 'badDay', 'readinessDisrupted', 'recoveryLimited',
        'interrupted', 'sessionInterrupted', 'workoutInterrupted', 'prescriptionEdited', 'exerciseEdited',
        'substitutionOccurred', 'exerciseSubstituted', 'manualPrescriptionOverride',
        'substituted', 'wasEdited', 'manualOverride'];
    return Object.fromEntries(keys.filter(k => value?.[k] !== undefined).map(k => [k, value[k]]));
}
// Flags are additive across session, exercise and set provenance. A false flag at
// a narrower level cannot clear a limitation recorded by the enclosing exposure.
export function progressionExposureContext(actual = [], context = {}) {
    const sources = [context, ...actual];
    const anyFlag = keys => sources.some(source => keys.some(key => source?.[key] === true));
    const readinessValues = sources.map(source => source?.readinessStatus ?? source?.readiness?.status
        ?? (typeof source?.readiness === 'string' ? source.readiness : null))
        .map(value => String(value ?? '').trim().toLowerCase().replace(/\s+/g, '_'));
    const limited = ['low', 'very_low', 'poor', 'bad', 'watch', 'recover', 'recovery', 'deload_recommended'];
    const readiness = readinessValues.find(value => limited.includes(value)) || readinessValues.find(Boolean) || '';
    return {
        nonComparable: anyFlag(['prescriptionEdited', 'exerciseEdited', 'substitutionOccurred',
            'exerciseSubstituted', 'manualPrescriptionOverride', 'substituted', 'wasEdited', 'manualOverride']),
        interrupted: anyFlag(['interrupted', 'sessionInterrupted', 'workoutInterrupted']),
        badDay: anyFlag(['badDay', 'readinessDisrupted', 'recoveryLimited']) || limited.includes(readiness),
        readiness
    };
}
export function validHistoryDate(entry) {
    const date = historyNumber(entry?.date);
    return date !== null && Math.abs(date) <= 8640000000000000;
}
// Newest revision wins for an identity. Records without an ID are independent legacy exposures.
export function normalizeHistoryEntries(history, programId) {
    const identities = new Map(), entries = [], excluded = [];
    for (const row of Array.isArray(history) ? history : []) {
        if (row?.programId !== programId) continue;
        if (!validHistoryDate(row)) { excluded.push({ id: row?.id ?? null, reason: 'invalid_date' }); continue; }
        if (!row.id) { entries.push(row); continue; }
        const prior = identities.get(String(row.id));
        if (prior) excluded.push({ id: row.id, reason: 'duplicate_identity' });
        const revisionDate = entry => validHistoryDate({ date: entry.updatedAt })
            ? historyNumber(entry.updatedAt) : historyNumber(entry.date);
        if (!prior || revisionDate(row) >= revisionDate(prior)) identities.set(String(row.id), row);
    }
    return { entries: [...entries, ...identities.values()].sort((a, b) => Number(a.date) - Number(b.date)), excluded };
}

// Resolve a persisted workout to one authored program day without guessing. Exact IDs win.
// Legacy/stale IDs may fall back to a label only when that label is unique, or when the logged
// exercise roster uniquely identifies one of the same-label days. Ambiguity stays unresolved.
export function resolveHistoryDayIndex(days, entry) {
    const roster = Array.isArray(days) ? days : [];
    if (!entry) return -1;
    const dayId = entry.dayId == null ? '' : String(entry.dayId);
    if (dayId) {
        const byId = roster.findIndex(day => String(day?.id ?? '') === dayId);
        if (byId >= 0) return byId;
    }
    const label = String(entry.dayLabel ?? '').trim();
    if (!label) return -1;
    const candidates = roster.map((day, index) => ({ day, index }))
        .filter(({ day }) => String(day?.label ?? '').trim() === label);
    if (candidates.length === 1) return candidates[0].index;
    if (candidates.length < 2) return -1;
    const loggedIds = Object.keys(entry.perf ?? {}).filter(id => entry.perf?.[id] != null);
    if (!loggedIds.length) return -1;
    const scored = candidates.map(candidate => ({
        ...candidate,
        score: loggedIds.reduce((count, id) => count + (candidate.day?.exercises ?? []).includes(id), 0)
    }));
    const best = Math.max(...scored.map(candidate => candidate.score));
    if (best <= 0) return -1;
    const winners = scored.filter(candidate => candidate.score === best);
    return winners.length === 1 ? winners[0].index : -1;
}

export function historyLoadReason(reason, unit) {
    return typeof reason === 'string' ? reason.replace(/\b(\d+(?:\.\d+)?)\s*(lb|kg)\b/g,
        (_, value, from) => `${Math.round(convertHistoryLoad(value, from, unit) * 100) / 100} ${unit}`) : reason;
}
