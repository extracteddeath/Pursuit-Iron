// M230 canonical dose-ledger stage. Stage context is local to one realization.
import { ALL_MUSCLES } from './config.js';

export function currentFractional(context, ...args) {
const { exerciseMap, realized } = context;
return (
/* M230:PRESERVE:helper.currentFractional:BEGIN */
() => {
        const totals = Object.fromEntries(ALL_MUSCLES.map(m => [m, 0]));
        for (const session of realized)
            for (const exercise of session.exercises) {
                const def = exerciseMap.get(exercise.exerciseId);
                if (!def)
                    continue;
                for (const [muscle, contribution] of Object.entries(def.muscles))
                    totals[muscle] += contribution.credit * exercise.sets;
            }
        return totals;
    }
/* M230:PRESERVE:helper.currentFractional:END */
)(...args);
}

export function totalsWithSessionProposal(context, ...args) {
const { exerciseMap, realized } = context;
return (
/* M230:PRESERVE:helper.totalsWithSessionProposal:BEGIN */
(sessionId, proposal) => {
        const totals = Object.fromEntries(ALL_MUSCLES.map(m => [m, 0]));
        for (const session of realized) {
            const exercises = session.id === sessionId ? proposal : session.exercises;
            for (const exercise of exercises) {
                const def = exerciseMap.get(exercise.exerciseId);
                if (!def)
                    continue;
                for (const [muscle, contribution] of Object.entries(def.muscles))
                    totals[muscle] += contribution.credit * exercise.sets;
            }
        }
        return totals;
    }
/* M230:PRESERVE:helper.totalsWithSessionProposal:END */
)(...args);
}

export function directTotalsWithSessionProposal(context, ...args) {
const { exerciseMap, realized } = context;
return (
/* M230:PRESERVE:helper.directTotalsWithSessionProposal:BEGIN */
(sessionId, proposal) => {
        const totals = Object.fromEntries(ALL_MUSCLES.map(m => [m, 0]));
        for (const session of realized) {
            const exercises = session.id === sessionId ? proposal : session.exercises;
            for (const exercise of exercises) {
                const def = exerciseMap.get(exercise.exerciseId);
                if (!def)
                    continue;
                for (const [muscle, contribution] of Object.entries(def.muscles))
                    if (contribution.credit >= 1)
                        totals[muscle] += exercise.sets;
            }
        }
        return totals;
    }
/* M230:PRESERVE:helper.directTotalsWithSessionProposal:END */
)(...args);
}

export function currentDirect(context, ...args) {
const { exerciseMap, realized } = context;
return (
/* M230:PRESERVE:helper.currentDirect:BEGIN */
() => {
        const totals = Object.fromEntries(ALL_MUSCLES.map(m => [m, 0]));
        for (const session of realized)
            for (const exercise of session.exercises) {
                const def = exerciseMap.get(exercise.exerciseId);
                if (!def)
                    continue;
                for (const [muscle, contribution] of Object.entries(def.muscles))
                    if (contribution.credit >= 1)
                        totals[muscle] += exercise.sets;
            }
        return totals;
    }
/* M230:PRESERVE:helper.currentDirect:END */
)(...args);
}
