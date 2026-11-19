import { jsx as _jsx, jsxs as _jsxs } from 'react/jsx-runtime';
import { describeStrengthDoseLimit } from './cycle-strength-dose-surface.js';

// Generation-time coaching evidence, deliberately read-only. Each number is
// pinned to the originally generated Strength opening week; saved edits and
// finished training are never represented as being re-audited here.
export function CycleStrengthDoseSummary({ program }) {
    const report = describeStrengthDoseLimit(program?.nextEngine?.strengthEntryDose);
    if (!report) return null;
    return _jsxs("details", { className: "wpb-strength-dose-review wpb-cycle-week-outlook-detail",
        "data-strength-dose-status": report.status, children: [
        _jsx("summary", { className: "wpb-cycle-dose-summary", children: report.heading }),
        _jsx("p", { className: "wpb-cycle-dose-caption", children: report.explanation }),
        report.details.length > 0 && _jsx("dl", { className: "wpb-cycle-dose-rows", children: report.details.map(row =>
            _jsxs("div", { className: "wpb-cycle-dose-row", children: [
                _jsx("dt", { children: row.label }),
                _jsxs("dd", { children: [
                    `${row.actual} of ${row.requested} requested credited sets · feasible ${row.feasible}`,
                    _jsx("div", { className: "wpb-cycle-dose-caption", children: row.reason })
                ] })
            ] }, row.region)) }),
        report.missing > 0 && _jsx("p", { className: "wpb-cycle-dose-caption", children:
            `${report.missing} other regional limits do not have complete original dose evidence.` }),
        _jsx("p", { className: "wpb-cycle-dose-caption", children: report.caveat })
    ] });
}
