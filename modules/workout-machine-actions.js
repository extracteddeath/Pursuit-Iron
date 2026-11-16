// UI adapter for choosing a physical weight stack mid-workout. The existing
// machine-instances engine owns catalog validity and comparable history;
// WorkoutSession still owns its journal and the root gym store owns persistence.
import { addMachineVariant, latestMachineSet, resolveMachineVariant } from './next-engine/machine-instances.js';
import { EX_BY_ID, isMachineLike } from './engine-api.js';

export function makeWorkoutMachineActions({
    getRow, gymProfile, sessionGymId, history, dayId, unit, toUnit,
    setData, onSaveGymMachineVariant
}) {
    const switchPhysicalMachine=(ei,nextId,sourceGym=gymProfile)=>{
        const row=getRow(ei);
        if(!row || !sourceGym || sourceGym.id!==sessionGymId)
            return {ok:false,message:'The workout gym is unavailable. Choose the correct gym first.'};
        let chosen;
        try {chosen=resolveMachineVariant(sourceGym,row.id,nextId);}
        catch(error){return {ok:false,message:String(error?.message||'Unknown machine')};}
        if(chosen.active===false || chosen.missing)
            return {ok:false,message:'This machine is no longer active at this gym.'};
        if(row.machineInstanceId===nextId)return {ok:true};
        const last=latestMachineSet(history,{
            gymId:sessionGymId,exerciseId:row.id,machineInstanceId:nextId,dayId});
        const reference=last?toUnit(last.weight,last.unit||unit,unit):null;
        setData(rows=>rows.map((item,i)=>{
            if(i!==ei)return item;
            return {...item,machineInstanceId:nextId,sets:item.sets.map(set=>{
                if(set.done)return set; // completed set retains its original physical machine
                // Never reuse a different stack's attempted load as this machine's baseline.
                const weight=set.warm||set.sub?'':reference==null?''
                    :String(Math.round(reference*1000)/1000);
                return {...set,machineInstanceId:nextId,weight,auto:false,
                    valueOwner:'user',suggested:undefined,hint:undefined,
                    target:{...set.target,w:weight}};
            })};
        }));
        return {ok:true};
    };
    const addPhysicalMachineNow=(ei,name,makeDefault=false)=>{
        const row=getRow(ei);
        if(!row || !gymProfile || gymProfile.id!==sessionGymId ||
            !isMachineLike(EX_BY_ID[row.id]?.equip) || !onSaveGymMachineVariant)
            return {ok:false,message:'Choose your workout gym before adding a machine.'};
        const instance={
            id:`machine_${Date.now().toString(36)}_${Math.random().toString(36).slice(2,10)}`,
            label:String(name||'').trim(),active:true
        };
        let nextGym;
        try {nextGym=addMachineVariant(gymProfile,row.id,instance,makeDefault);}
        catch(error){return {ok:false,message:String(error?.message||'Invalid machine')};}
        // Root store owns the validated per-gym configuration. Refuse to switch
        // when the catalog was not accepted by the owner.
        const written=onSaveGymMachineVariant(
            sessionGymId,row.id,nextGym.machineVariants[row.id]);
        if(!written)return {ok:false,message:'Could not save this machine at the active gym.'};
        // Parent props update asynchronously; the newly validated gym is
        // authoritative for this immediate switch, never stale old props.
        return switchPhysicalMachine(ei,instance.id,nextGym);
    };
    return {switchPhysicalMachine,addPhysicalMachineNow};
}
