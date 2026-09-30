import fs from 'node:fs';

const path = 'modules/next-engine/volume-repair.js';
let text = fs.readFileSync(path, 'utf8');
const from = `        for (const issue of audit.issues) {
            const recipients = slots.filter(x => x.def && !STRENGTH.has(x.e.role) && publicRegionContribution(x.def, issue.region) > 0)
                .sort((a, b) => publicRegionContribution(b.def, issue.region) - publicRegionContribution(a.def, issue.region) || a.e.sets - b.e.sets);
            for (const r of recipients) {
                consider([{ dayId: r.dayId, slot: r.slot, delta: issue.status === 'under' ? 1 : -1 }]);
                // Whole-week rounding can absorb a single base set without increasing the lowest
                // week. Test a bounded two-set step before interpreting that as exhausted headroom.
                if (issue.status === 'under' && r.e.sets <= 3)
                    consider([{ dayId: r.dayId, slot: r.slot, delta: 2 }]);
            }
        }`;
const to = `        for (const issue of audit.issues) {
            // Locked-cycle strength_support rows can be accessories whose identity must stay in the
            // skeleton (calves are the concrete M202 case). Keep primary/secondary strength anchors
            // fully protected, but allow an over-ceiling support row to shed sets when the exact
            // weekly shell audit and the full engine audit both approve the change.
            const recipients = slots.filter(x => x.def &&
                (!STRENGTH.has(x.e.role) || (preserveRoster && issue.status === 'over' && x.e.role === 'strength_support')) &&
                publicRegionContribution(x.def, issue.region) > 0)
                .sort((a, b) => publicRegionContribution(b.def, issue.region) - publicRegionContribution(a.def, issue.region) || a.e.sets - b.e.sets);
            for (const r of recipients) {
                const floor = preserveRoster ? 1 : 2;
                // Work-week progression is rounded from the base prescription. A one-set base change
                // can therefore leave the max week unchanged; search a small bounded decrement atomically
                // rather than falsely concluding that a locked roster cannot be reconciled.
                const deltas = issue.status === 'under' ? [1, ...(r.e.sets <= 3 ? [2] : [])]
                    : [-1, -2, -3].filter(delta => r.e.sets + delta >= floor);
                for (const delta of deltas)
                    consider([{ dayId: r.dayId, slot: r.slot, delta }]);
            }
        }`;
if (text.includes(to)) {
  console.log('M202 round 2 already applied.');
  process.exit(0);
}
if (!text.includes(from)) throw new Error('M202 round-2 candidate-loop anchor not found');
text = text.replace(from, to);
fs.writeFileSync(path, text);
console.log('M202 round 2 applied: bounded multi-set decrements + locked strength_support overflow repair.');
