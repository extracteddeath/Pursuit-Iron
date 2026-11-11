import { jsx as _jsx, jsxs as _jsxs } from 'react/jsx-runtime';
import { useState } from 'react';
import { EXERCISES, isMachineLike } from './engine-api.js';
import { addMachineVariant, editMachineVariant, setDefaultMachineVariant } from './next-engine/machine-instances.js';
import { UiAction, UiField, UiSummary } from './ui-primitives.js';

// Gym-owned settings, not program edits: the exercise still has one muscle-dose identity.
const machineExercises=EXERCISES.filter(ex=>isMachineLike(ex.equip))
    .sort((a,b)=>a.name.localeCompare(b.name));
const short = value => String(value ?? '').trim();
export function GymMachineVariantFields({gym,onChange}) {
    const [exerciseId,setExerciseId]=useState('machine-press');
    const [label,setLabel]=useState('Machine A');
    const [message,setMessage]=useState('');
    const saved=gym.machineVariants?.[exerciseId] ?? {defaultId:null,instances:[]};
    const instances=Array.isArray(saved.instances)?saved.instances:[];
    const edit=operation=>{
        try {
            const updated=operation();
            onChange({machineVariants:updated.machineVariants});
            setMessage('');
        } catch(error) { setMessage(String(error?.message??'Cannot change machine')); }
    };
    const create=()=>{
        const name=short(label);
        if (!name) {setMessage('Enter a short machine name.');return;}
        const nextId=`machine_${Date.now().toString(36)}_${Math.random().toString(36).slice(2,8)}`;
        edit(()=>addMachineVariant(gym,exerciseId,{id:nextId,label:name,active:true},instances.length===0));
        setLabel(instances.length===0?'Machine B':'Machine C');
    };
    return _jsxs('details',{className:'wpb-gym-section wpb-gym-machine-variants',children:[
        _jsx(UiSummary,{children:'Physical machine alternatives (A/B)'}),
        _jsx('p',{className:'wpb-gym-guidance',children:'Two physical machines can train the same exercise with different weight stacks. Each machine keeps independent Last, Target, PR and progression. Choose a default for this gym.'}),
        _jsxs('label',{children:['Exercise',
            _jsx(UiField,{as:'select',className:'wpb-ui-control','aria-label':'Exercise with machine alternatives',value:exerciseId,
                onChange:event=>{setExerciseId(event.target.value);setMessage('');},
                children:machineExercises.map(ex=>_jsx('option',{value:ex.id,children:ex.name},ex.id))})
        ]}),
        ...instances.map(instance=>_jsxs('div',{className:'wpb-gym-field-block',children:[
            _jsxs('div',{className:'wpb-gym-inline',children:[
                _jsx(UiField,{as:'input',className:'wpb-ui-control','aria-label':`Name for ${instance.label}`,
                    value:instance.label,onChange:event=>edit(()=>editMachineVariant(gym,exerciseId,instance.id,{label:event.target.value}))}),
                _jsx('span',{children:instance.id===saved.defaultId?'Default machine':'Alternative'}),
                instance.active!==false&&instance.id!==saved.defaultId&&_jsx(UiAction,{
                    onClick:()=>edit(()=>setDefaultMachineVariant(gym,exerciseId,instance.id)),
                    children:'Make default'}),
                instance.id!==saved.defaultId&&_jsx(UiAction,{
                    onClick:()=>edit(()=>editMachineVariant(gym,exerciseId,instance.id,{active:instance.active===false})),
                    children:instance.active===false?'Reactivate':'Deactivate'})
            ]}),
            _jsxs('label',{children:['Weight increment',
                _jsx(UiField,{as:'input',className:'wpb-ui-control',inputMode:'decimal',type:'number',min:'0.001',step:'any',
                    'aria-label':`Weight increment for ${instance.label}`,
                    value:instance.loading?.increment??'',placeholder:'Gym default',
                    onChange:event=>{
                        const input=event.target.value;
                        const loading={...(instance.loading??{})};
                        if (input==='')delete loading.increment;
                        else loading.increment=Number(input);
                        edit(()=>editMachineVariant(gym,exerciseId,instance.id,{loading}));
                    }})
            ]})
        ]},instance.id)),
        _jsxs('div',{className:'wpb-gym-inline',children:[
            _jsx(UiField,{as:'input',value:label,className:'wpb-ui-control','aria-label':'Name of new physical machine',maxLength:80,
                onChange:event=>setLabel(event.target.value),placeholder:'Machine A'}),
            _jsx(UiAction,{onClick:create,children:'Add machine'})
        ]}),
        message&&_jsx('p',{role:'alert',className:'wpb-gym-field-error',children:message})
    ]});
}
