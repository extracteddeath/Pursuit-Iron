// Read-only presentation of the ORIGINAL generated Hypertrophy -> Strength
// regional-volume report. Never infer a set adjustment or re-label this as
// current volume after manual edits or future block re-generation.
const valid = value => typeof value === 'number' && Number.isFinite(value) && value >= 0;
const sets = value => valid(value) ? Number(value).toFixed(1).replace(/\\.0$/, '') : '—';
const nameOf = value => String(value || '').replaceAll('_', ' ').replace(/^./, c => c.toUpperCase());
export function describeStrengthDoseLimit(report) {
    if (!report || report.comparison !== 'last_hypertrophy_week_to_first_strength_week'
        || !report.requested || !report.actual || !report.target
        || !Array.isArray(report.unresolved) || !Array.isArray(report.limits)
        || !Object.keys(report.requested).length) return null;
    const details = report.unresolved.map(region => {
        const record = report.limits.find(x => x.region === region);
        const requested = report.requested[region];
        const feasible = report.target[region];
        const actual = report.actual[region];
        if (!valid(requested) || !valid(feasible) || !valid(actual)) return null;
        // An unrecognised reason is not silently described as an athlete
        // limitation or a recommendation to add volume.
        const reason = record?.reasonCode === 'SHARED_MUSCLE_CEILING'
            ? 'Shared muscle recovery limit' : record?.reasonCode === 'NO_SAFE_ADDITION'
                ? 'No safe addition fits this block' : 'Constraint not specified';
        return { region, label: nameOf(region), requested: sets(requested),
            feasible: sets(feasible), actual: sets(actual), reason };
    }).filter(Boolean);
    const missing = report.unresolved.length - details.length;
    const limited = report.status === 'limited' || details.length > 0 || missing > 0;
    const heading = limited
        ? `Strength volume · ${report.unresolved.length} ${report.unresolved.length === 1 ? 'area' : 'areas'} below goal`
        : 'Strength volume · planned goal covered';
    return { heading, limited, details, missing, status: limited ? 'limited' : 'met',
        explanation: limited
            ? 'The original requested Strength dose was not fully retained. The engine kept the safe amount; these are planning limits, not instructions to increase sets.'
            : 'The original regional Strength-retention goals were covered by the generated first work week.',
        caveat: 'Original generated first-week estimate. Edits, refreshed previews and completed workouts may change the actual dose. Review the current saved workout prescriptions.' };
}
