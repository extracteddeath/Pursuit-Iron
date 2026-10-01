import { ALL_MUSCLES } from './config.js';
export function emptyMuscleLedger() {
    return Object.fromEntries(ALL_MUSCLES.map(m => [m, { fractionalSets: 0, directSets: 0 }]));
}
export function deriveMuscleLedger(events) {
    const ledger = emptyMuscleLedger();
    for (const event of events) {
        for (const [muscle, credit] of Object.entries(event.muscles)) {
            const row = ledger[muscle];
            row.fractionalSets += credit;
            if (credit >= 1)
                row.directSets += 1;
        }
    }
    for (const m of ALL_MUSCLES) {
        ledger[m].fractionalSets = Math.round(ledger[m].fractionalSets * 10) / 10;
        ledger[m].directSets = Math.round(ledger[m].directSets * 10) / 10;
    }
    return ledger;
}
