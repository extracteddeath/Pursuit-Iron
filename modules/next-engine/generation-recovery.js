import { normalizeRequest } from './prescription.js';
function suggestionForFinding(finding, request) {
    switch (finding.code) {
        case 'TIME_LIMIT': return [
            { code: 'increase_time', title: 'Add session time', detail: 'Increase the affected day by about 10–15 minutes, or give the engine another training day to distribute the same priorities.', priority: 100 },
            { code: 'add_training_day', title: 'Add an available day', detail: 'An additional training day can distribute required work without compressing rest or removing primary work.', priority: 85 }
        ];
        case 'BARBELL_LIMIT': return [
            { code: 'relax_barbell_limit', title: 'Raise the barbell limit', detail: `Your current limit is ${request.restrictions.maxBarbellMovementsPerDay} barbell movement${request.restrictions.maxBarbellMovementsPerDay === 1 ? '' : 's'} per day. Raising it by one may resolve the conflict.`, priority: 95 },
            { code: 'use_auto_split', title: 'Let the engine choose the split', detail: 'If a split preference is selected, switching back to automatic topology can create more room for scarce barbell work.', priority: 70 }
        ];
        case 'UNAVAILABLE_EQUIPMENT': return [
            { code: 'broaden_equipment', title: 'Broaden available equipment', detail: 'The required training role could not be realized with the current equipment profile. Add an available implement or choose a broader equipment setup.', priority: 100 }
        ];
        case 'BODYWEIGHT_EXCLUDED': return [
            { code: 'allow_bodyweight', title: 'Allow bodyweight movements', detail: 'Allowing bodyweight work gives the engine another valid way to satisfy this training role.', priority: 95 }
        ];
        case 'LIFT_EXPOSURE_MISSING': return [
            { code: 'add_training_day', title: 'Create room for the priority lift', detail: 'Add time or another training day so the primary lift can receive two sufficiently specific exposures.', priority: 100 },
            { code: 'reduce_lift_priority', title: 'Lower this lift priority', detail: 'If two specific exposures are not essential, change the lift from Primary to High/Normal rather than silently under-serving it.', priority: 75 }
        ];
        case 'MUSCLE_UNDER_MIN': return [
            { code: 'increase_time', title: 'Increase weekly training capacity', detail: 'A muscle minimum could not be realized under the current time and recovery budget.', priority: 80 },
            { code: 'reduce_specialization', title: 'Reduce competing specialization', detail: 'Lower one or more specialization priorities so minimum work can be redistributed more evenly.', priority: 70 }
        ];
        case 'UNKNOWN_EXERCISE':
        case 'MISSING_PROGRESSION':
            return [{ code: 'report_issue', title: 'Report this engine issue', detail: 'This is an internal generation problem rather than a constraint you should have to work around. Export support diagnostics if it happens on-device.', priority: 120 }];
        default:
            return [];
    }
}
export function buildGenerationRecoveryPlan(audit, requestInput) {
    const request = normalizeRequest(requestInput);
    const blockers = audit.findings.filter(finding => finding.severity === 'critical' || finding.severity === 'major');
    const suggestions = new Map();
    for (const finding of blockers) {
        for (const suggestion of suggestionForFinding(finding, request)) {
            const current = suggestions.get(suggestion.code);
            if (!current || suggestion.priority > current.priority)
                suggestions.set(suggestion.code, suggestion);
        }
    }
    if (!suggestions.size)
        suggestions.set('report_issue', { code: 'report_issue', title: 'Review the rejected program', detail: 'The independent audit rejected this program without a known user-adjustable recovery path. Keep your current data and export support diagnostics.', priority: 50 });
    return {
        blockingCodes: [...new Set(blockers.map(finding => finding.code))],
        suggestions: [...suggestions.values()].sort((a, b) => b.priority - a.priority || a.title.localeCompare(b.title)).slice(0, 4)
    };
}
