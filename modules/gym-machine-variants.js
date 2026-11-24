import { jsx as _jsx, jsxs as _jsxs } from 'react/jsx-runtime';
import { useState } from 'react';
import { EXERCISES, isMachineLike } from './engine-api.js';
import { addMachineVariant, assignLegacyMachineHistory, editMachineVariant, setDefaultMachineVariant } from './next-engine/machine-instances.js';
import { UiAction, UiField, UiSummary } from './ui-primitives.js';

// Gym-owned settings, not program edits: the exercise still has one muscle-dose identity.
// Built on first use: sorting the library with localeCompare at module load cost ~50 ms of a
// 4x-throttled startup for a list that only appears inside a gym's machine settings.
let machineList=null;
const machineExercises=()=>machineList||(machineList=EXERCISES.filter(ex=>isMachineLike(ex.equip))
    .sort((a,b)=>a.name.localeCompare(b.name)));
const short = value => String(value ?? '').trim();
export function GymMachineVariantFields({gym,onChange}) {
    const [exerciseId,setExerciseId]=useState('machine-press');
    const [label,setLabel]=useState('Machine A');
    const [message,setMessage]=useState('');
    const saved=gym.machineVariants?.[exerciseId] ?? {defaultId:null,instances:[]};
    const instances=Array.isArray(saved.instances)?saved.instances:[];
    const configuredCount=Object.values(gym.machineVariants||{})
        .reduce((n,item)=>n+(Array.isArray(item?.instances)?item.instances.length:0),0);
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
    return _jsxs('details',{className:'wpb-gym-section wpb-gym-machine-variants',
        open:true,children:[
        _jsx(UiSummary,{children:'Machine A/B · Add physical machines'}),
        _jsx('p',{className:'wpb-gym-guidance',children:
            'Choose a machine exercise below and add Machine A, Machine B, or more. '+
            'Set the gym default, then tap Done to save. During a workout, use the '+
            'Exercise Info → Equipment and exercise to choose a physical machine. '+
            'The Program view shows the movement, not the physical machine; switching '+
            'does not change your exercise selection or combine weight histories.'}),
        _jsx('p',{className:'wpb-gym-guidance',children:
            configuredCount? `Saved physical machines in this gym: ${configuredCount}.`:
                'No Machine A/B alternatives saved in this gym yet.'}),
        _jsxs('label',{children:['Exercise',
            _jsx(UiField,{as:'select',className:'wpb-ui-control','aria-label':'Exercise with machine alternatives',value:exerciseId,
                onChange:event=>{setExerciseId(event.target.value);setMessage('');},
                children:machineExercises().map(ex=>_jsx('option',{value:ex.id,children:ex.name},ex.id))})
        ]}),
        !instances.length && _jsx('p',{className:'wpb-gym-guidance',children:
            'No physical variants for this exercise. Enter Machine A and tap Add machine, '+
            'then add Machine B for the same exercise.'}),
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
        instances.length>0 && _jsxs('div',{className:'wpb-gym-field-block',children:[
            _jsx('strong',{children:'Which original machine owns older workout history?'}),
            _jsx('p',{className:'wpb-gym-guidance',children:
                'Older sets without a machine label stay separate until you choose. '+
                'Assign them to the physical machine you used before adding alternatives; '+
                'Machine B does not inherit Machine A weights. This does not rewrite old workouts.'}),
            _jsx(UiField,{as:'select',className:'wpb-ui-control',
                'aria-label':'Original physical machine for older unlabelled history',
                value:saved.legacyHistoryMachineId||'',
                onChange:event=>edit(()=>assignLegacyMachineHistory(gym,exerciseId,
                    event.target.value||null)),
                children:[
                    _jsx('option',{value:'',children:'Keep older history unassigned'}),
                    ...instances.map(instance=>_jsx('option',{
                        value:instance.id,children:instance.label
                    },instance.id))
                ]})
        ]}),
        _jsxs('div',{className:'wpb-gym-inline',children:[
            _jsx(UiField,{as:'input',value:label,className:'wpb-ui-control','aria-label':'Name of new physical machine',maxLength:80,
                onChange:event=>setLabel(event.target.value),placeholder:'Machine A'}),
            _jsx(UiAction,{onClick:create,children:'Add machine'})
        ]}),
        message&&_jsx('p',{role:'alert',className:'wpb-gym-field-error',children:message})
    ]});
}
