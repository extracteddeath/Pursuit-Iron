/**
 * Physical machine changes are a choice about the equipment under the same
 * planned movement. The original program, issued prescription, completed set
 * receipts and historical performance are NOT edited by either action.
 *
 * Pure functions: Gym remains the shared store's owner, and WorkoutSession
 * remains the only writer of the current session's actual sets.
 */
import { addMachineVariant, assignLegacyMachineHistory, machineStartingReference, resolveMachineVariant } from './next-engine/machine-instances.js';
import { EX_BY_ID, isMachineLike, toUnit } from './engine-api.js';

const fail=message=>({ok:false,message});
const short=name=>String(name??'').trim();

export function chooseWorkoutPhysicalMachine(row,{
    gym,sessionGymId,nextId,history=[],dayId,unit='lb'
}) {
    if(!row || !isMachineLike(EX_BY_ID[row.id]?.equip))
        return fail('Only machine exercises support physical machine A/B.');
    if(!gym || gym.id!==sessionGymId)
        return fail('The workout gym is unavailable. Choose the correct gym first.');
    let selected;
    try {selected=resolveMachineVariant(gym,row.id,nextId);}
    catch(error){return fail(String(error?.message||'Machine not found'));}
    if(selected.missing||selected.active===false)
        return fail('This machine is no longer active at this gym.');
    if(row.machineInstanceId===nextId)return {ok:true,row};
    const last=machineStartingReference(history,{
        gymId:sessionGymId,exerciseId:row.id,
        machineInstanceId:nextId,dayId,gym
    });
    const reference=last?toUnit(last.weight,last.unit||unit,unit):null;
    return {ok:true,row:{
        ...row,machineInstanceId:nextId,
        sets:row.sets.map(set=>{
            // This check MUST precede any weight or machine reassignment.
            if(set.done)return set;
            const weight=set.warm||set.sub||reference==null?''
                :String(Math.round(reference*1000)/1000);
            return {...set,machineInstanceId:nextId,weight,auto:false,
                valueOwner:'user',suggested:undefined,hint:undefined,
                target:{...set.target,w:weight}};
        })
    }};
}

export function createWorkoutPhysicalMachine(gym,exerciseId,label,makeDefault=false){
    if(!gym || !isMachineLike(EX_BY_ID[exerciseId]?.equip))
        return fail('Choose a compatible machine exercise at this gym.');
    const name=short(label);
    if(!name)return fail('Give this physical machine a name.');
    const existing=gym.machineVariants?.[exerciseId]?.instances||[];
    if(existing.some(item=>short(item.label).toLowerCase()===name.toLowerCase()))
        return fail('This name is already used at this gym. Choose that machine above or enter another name.');
    const instance={
        id:`machine_${Date.now().toString(36)}_${Math.random().toString(36).slice(2,10)}`,
        label:name,active:true
    };
    try {
        const nextGym=addMachineVariant(gym,exerciseId,instance,makeDefault);
        return {ok:true,nextGym,id:instance.id};
    }catch(error){return fail(String(error?.message||'Unable to add physical machine'));}
}

/** Only the athlete can identify which existing machine owns old unassigned sets.
 * A/B PR and load histories remain isolated; nothing edits the old receipts. */
export function associateLegacyWorkoutMachine(gym, exerciseId, machineInstanceId) {
    if(!gym || !isMachineLike(EX_BY_ID[exerciseId]?.equip))
        return fail('Choose an existing machine in the workout gym.');
    try {
        const nextGym=assignLegacyMachineHistory(gym,exerciseId,machineInstanceId);
        return {ok:true,nextGym};
    } catch(error) {return fail(String(error?.message||'History assignment could not be saved'));}
}

/** Apply a validated exercise-scoped machine catalog to its existing gym.
 * Nothing else in the gym (including other machines, gym loading settings,
 * equipment, or other saved gym identities) is rewritten. */
export function withGymMachineConfig(gyms,gymId,exerciseId,config,updatedAt){
    if(!Array.isArray(gyms)||!gymId||!exerciseId||
        !Array.isArray(config?.instances)||!config.instances.length)
        return gyms;
    return gyms.map(g=>g.id!==gymId?g:{
        ...g,updatedAt,
        machineVariants:{...g.machineVariants,[exerciseId]:config}
    });
}
