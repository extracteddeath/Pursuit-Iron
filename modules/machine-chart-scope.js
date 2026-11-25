import { useMemo, useState } from 'react';
import { jsx as _jsx, jsxs as _jsxs } from 'react/jsx-runtime';
import { UiField } from './ui-primitives.js';
import {
    historyForMachineRecommendation, effectiveMachineHistoryId,
    LEGACY_MACHINE_ID, UNKNOWN_GYM_ID
} from './next-engine/machine-instances.js';

/** One trend per physical resistance stack, not a fake A→B strength decline. */
export function useMachineChartScope(history,exerciseId,gyms=[]) {
    const [selectedScope,setSelectedScope]=useState('');
    const scopes=useMemo(()=>{
        const found=new Map();
        for(const session of history||[]) {
            const perf=session?.perf?.[exerciseId];
            if(!Array.isArray(perf?.sets))continue;
            for(const set of perf.sets) {
                if(!set||set.warm||set.sub||!(Number(set.w)>0))continue;
                const gymId=session.gymId??UNKNOWN_GYM_ID;
                const gym=gyms.find(g=>g.id===gymId);
                const machineInstanceId=effectiveMachineHistoryId(set,perf,gym,exerciseId);
                const key=gymId+'|'+machineInstanceId;
                const previous=found.get(key);
                if(!previous||Number(session.date||0)>previous.latest)
                    found.set(key,{key,gymId,machineInstanceId,latest:Number(session.date||0),
                        machineLabel:gyms.find(g=>g.id===gymId)?.machineVariants?.[exerciseId]
                            ?.instances?.find(v=>v.id===machineInstanceId)?.label??null});
            }
        }
        return [...found.values()].sort((a,b)=>b.latest-a.latest);
    },[history,exerciseId,gyms]);
    const active=scopes.find(s=>s.key===selectedScope)??scopes[0];
    const comparableHistory=useMemo(()=>scopes.length>1&&active
        ?historyForMachineRecommendation(history,exerciseId,active.machineInstanceId,active.gymId,
            gyms.find(g=>g.id===active.gymId))
        :history,[history,exerciseId,active?.key,scopes.length,gyms]);
    return {scopes,active,comparableHistory,setSelectedScope};
}
export function MachineChartScopePicker({scopes,active,onSelect}) {
    if(scopes.length<=1)return null;
    return _jsxs('label',{className:'wpb-ui-field wpb-exercise-machine-scope',children:[
        _jsx('span',{children:'Compare weights on'}),
        _jsx(UiField,{as:'select','aria-label':'Physical machine for strength trend',
            value:active?.key||'',onChange:event=>onSelect(event.target.value),
            children:scopes.map(scope=>_jsx('option',{value:scope.key,
                children:scope.machineInstanceId===LEGACY_MACHINE_ID
                    ? 'Older unassigned history'
                    : `${scope.machineLabel||'Machine '+scope.machineInstanceId} · gym ${scope.gymId}`
            },scope.key))}),
        _jsx('small',{className:'wpb-ui-field-help',children:'Machines may use different resistance scales. Only comparable loads are plotted.'})
    ]});
}
