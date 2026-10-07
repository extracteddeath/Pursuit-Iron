import { availableLoadAtOrBelow, resolveLoadingMode } from './loading.js';
import { historyNumber, progressionExposureContext } from './history-contract.js';
const pair = value => {
    const values = Array.isArray(value) ? value : String(value ?? '').split(/[-–]/);
    const numbers = values.map(historyNumber);
    return numbers.length && numbers.every(n => n !== null) ? [Math.min(...numbers), Math.max(...numbers)] : null;
};
const untouched = row => row?.valueOwner === 'prescription' && row.auto === true
    && String(row.weight) === String(row.target?.w)
    && (row.target?.prefillReps == null || String(row.reps) === String(row.target.prefillReps));

/** Reversible, conservative overlay. It cannot change the stored program or completed work. */
export function applyConservativeLiveAdjustment(args) {
    const original = args.sets;
    if (!Array.isArray(original) || !args.cell || !args.exerciseId) return { sets:original, changedRows:0, action:'maintain' };
    const range = pair(args.cell.range ?? args.cell.reps), effort = pair(args.cell.rir);
    if (!range || range[0] < 1 || args.manual || args.isAssisted
        || ['bodyweight','assisted'].includes(resolveLoadingMode(args.exerciseId,args.equipmentAvailable,args.loadingInventory)))
        return { sets:original, changedRows:0, action:'maintain' };
    const failures = [];
    original.forEach((row,index) => {
        if (!row?.done || row.warm || row.sub || row.liveAdjustment || row.recoveryLimited) return;
        const context = progressionExposureContext([row],args.context ?? {});
        if (context.nonComparable || context.interrupted || context.badDay) return;
        const reps = historyNumber(row.reps), actualRir = historyNumber(row.actualRIR);
        const missed = reps !== null && reps > 0 && reps < range[0];
        const overshot = actualRir !== null && effort && actualRir < Math.max(0,effort[0]-1);
        if (missed || overshot) failures.push(index);
    });
    const trigger = failures.length >= 2;
    let changedRows=0;
    const sets=original.map(row => {
        if (!row || row.done || row.warm || row.sub || row.added || !untouched(row)) return row;
        if (row.liveAdjustment) {
            if (trigger) return row;
            const { liveAdjustment, recoveryLimited, hint, suggested, ...rest }=row;
            changedRows++;
            return { ...rest, weight:liveAdjustment.weight, reps:liveAdjustment.reps, target:liveAdjustment.target,
                ...(liveAdjustment.suggested ? { suggested:liveAdjustment.suggested } : {}),
                ...(liveAdjustment.hint !== undefined ? { hint:liveAdjustment.hint } : {}),
                ...(liveAdjustment.recoveryLimited !== undefined ? { recoveryLimited:liveAdjustment.recoveryLimited } : {}) };
        }
        if (!trigger || row.target?.nextAction === 'percentage') return row;
        const load=historyNumber(row.weight);
        if (!(load>0)) return row;
        const reduced=args.snapLoad ? args.snapLoad(load*.95)
            : availableLoadAtOrBelow(args.exerciseId,load*.95,args.loadingInventory,args.equipmentAvailable);
        if (!(typeof reduced==='number' && Number.isFinite(reduced) && reduced<load && reduced>=load*.95-.000001)) return row;
        const reps=args.cell.ownership?.reps === 'user' ? row.reps : String(Math.ceil(range[0]));
        changedRows++;
        return { ...row, weight:String(reduced), reps,
            target:{ ...row.target,w:reduced,prefillReps:reps,nextAction:'live_recovery' },
            suggested:{ ...row.suggested,weight:String(reduced),reps },
            hint:"Adjusted for today's performance", recoveryLimited:true,
            liveAdjustment:{ schemaVersion:1, sourceIndices:failures, weight:row.weight,reps:row.reps,target:row.target,
                suggested:row.suggested,hint:row.hint,recoveryLimited:row.recoveryLimited } };
    });
    return { sets:changedRows?sets:original, changedRows, action:changedRows ? trigger?'reduce_load':'restore_pending' : 'maintain' };
}
