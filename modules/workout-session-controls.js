// Shared Workout presentation. The session owns timer, prescription, logging,
// saved notes, exercise order and edits; these controls dispatch callbacks only.
import { jsx as _jsx, jsxs as _jsxs } from 'react/jsx-runtime';
import { useRef, useLayoutEffect, useState, useEffect } from 'react';
import { Check, ChevronDown, Repeat, Plus } from 'lucide-react';
import { createPortal } from '../vendor/react-dom.js';
import { SessionChoiceDialog } from './session-choice-dialog.js';
import { UiAction, UiField } from './ui-primitives.js';
import { latestMachineSet, machineHistory, LEGACY_MACHINE_ID, resolveMachineVariant } from './next-engine/machine-instances.js';
import { EX_BY_ID, isMachineLike, isWorkSet } from './engine-api.js';
import { workoutNavLabel } from './workout-nav-labels.js';
import { navigateChoiceRow } from './ui-choice-navigation.js';

export function selectNumericInput(event) {
    const input = event.currentTarget;
    if (!input.readOnly && !input.disabled)
        input.select();
}

/** Dismiss a numeric editor's native selection when tapping a non-editor
 * workout control. Do not prevent the gesture, alter values, or intercept
 * intentional selection and keyboard navigation inside text fields. */
export function dismissWorkoutNumericSelection(event) {
    const root = event.currentTarget;
    const input = root?.ownerDocument?.activeElement;
    const numeric = 'input[inputmode="decimal"],input[inputmode="numeric"]';
    if (!input?.matches?.(numeric) || !root.contains(input)) return;
    if (event.target?.closest?.('input,textarea,select,[contenteditable]')) return;
    if (typeof input.selectionEnd === 'number')
        input.setSelectionRange(input.selectionEnd,input.selectionEnd);
    input.blur();
}

// Reveal a single button inside its rail; never scroll the workout page itself.
function revealExerciseInRail(list, active) {
    if (!list?.isConnected || !active?.isConnected) return;
    const a = active.getBoundingClientRect(), r = list.getBoundingClientRect();
    if (a.left < r.left + 4) list.scrollLeft -= r.left + 4 - a.left;
    else if (a.right > r.right - 4) list.scrollLeft += a.right - r.right + 4;
}

export function WorkoutExerciseNav({ data, exIdx, onSelect }) {
    const rail = useRef(null);
    useLayoutEffect(() => {
        const list = rail.current, active = list?.querySelector('[aria-current="step"]');
        if (!list || !active) return;
        // Selection changes and responsive reflow both need to reveal the
        // current exercise, without scrolling the surrounding workout page.
        const reveal = () => revealExerciseInRail(list, active);
        reveal();
        if (typeof ResizeObserver !== 'function') {
            window.addEventListener('resize', reveal);
            return () => window.removeEventListener('resize', reveal);
        }
        const observer = new ResizeObserver(reveal);
        observer.observe(list);
        observer.observe(active);
        return () => observer.disconnect();
    }, [exIdx, data.length]);
    return _jsx("nav", { ref: rail, className: "wpb-hscroll wpb-ex-nav", "aria-label": "Workout exercises",
        onKeyDown: event => navigateChoiceRow(event, onSelect),
        onFocus: event => {
            const focused = event.target?.closest?.('.wpb-ex-nav-step');
            if (focused) revealExerciseInRail(rail.current, focused);
        },
        children: data.map((row, i) => {
        const work = row.sets.filter(isWorkSet), done = work.filter(s => s.done).length;
        const complete = work.length > 0 && done === work.length, active = i === exIdx;
        const name = EX_BY_ID[row.id]?.name || "Exercise";
        return _jsxs("button", { type: "button", onClick: () => onSelect(i),
            className: "pressable hit wpb-ex-nav-step", "data-active": active ? "1" : "0", "data-complete": complete ? "1" : "0",
            "aria-current": active ? "step" : undefined,
            "aria-label": `${name} · exercise ${i + 1} of ${data.length} · ${done} of ${work.length} working sets completed`,
            title: name, children: [complete && _jsx(Check, { size: 14, "aria-hidden": true }),
                _jsx("span", { className: "wpb-ex-nav-name", children: workoutNavLabel(name) }),
                _jsxs("span", { className: "mono wpb-ex-nav-count", children: [done, "/", work.length] })] }, `${row.id}:${i}`);
    }) });
}


export function WorkingSetCountControl({ value, onChange, label, minimum = 1, disabled = false }) {
    // Keep partial input editable, but never let a stepper emit NaN,
    // fractions, or a value beyond the visible 1–20 set range.
    const parsed = Number(value);
    const count = Number.isFinite(parsed) && String(value).trim() !== ''
        ? Math.min(20, Math.max(minimum, Math.trunc(parsed))) : minimum;
    return _jsxs("div", { className: "wpb-working-set-control", children: [
        _jsx("span", { children: label }),
        _jsxs("span", { className: "wpb-working-set-stepper", children: [
            _jsx("button", { type: "button", disabled: disabled || count <= minimum, "aria-label": "Decrease " + label, onClick: () => onChange(Math.max(minimum, count - 1)), children: "−" }),
            _jsx("input", { type: "number", inputMode: "numeric", min: minimum, max: 20, step: 1, value, disabled, "aria-label": label, onFocus: selectNumericInput, onChange: ev => onChange(ev.target.value) }),
            _jsx("button", { type: "button", disabled: disabled || count >= 20, "aria-label": "Increase " + label, onClick: () => onChange(Math.min(20, count + 1)), children: "+" })] })] });
}
export function SessionNoteEditor({ label, value, repeat, onChange, onRepeat }) {
    return _jsxs("div", { className: "wpb-session-note-editor", children: [
        _jsxs("label", { children: [_jsx("strong", { children: label }), _jsx("textarea", { value, rows: 3, maxLength: 3000,
            placeholder: "What should you remember? Setup, how it felt, or what to try next time…", onChange: ev => onChange(ev.target.value) })] }),
        _jsxs("label", { children: ["Show this note", _jsx("select", { value: repeat, "aria-label": label + " note repetition", onChange: ev => onRepeat(ev.target.value),
            children: [['none','This session only'],['once','Repeat once'],['always','Keep repeating']].map(([value, text]) => _jsx("option", { value, children: text }, value)) })] })] });
}

/**
 * Machine identity is selected at the machine, not in a program-edit screen.
 * Machine choices belong in Exercise Info alongside weight increments,
 * not a clipped extra button on the narrow workout action rail.
 * One focused sheet provides existing physical machines, their own last
 * performances, quick machine creation, or the existing exercise swap.
 *
 * This is PRESENTATION ONLY. WorkoutSession owns all workout mutations and
 * the app shell owns the per-gym saved machine catalog. No history or program
 * write occurs inside this component.
 */
export function nextPhysicalMachineLabel(instances=[]) {
    const names=new Set(instances.map(item=>String(item.label||'').trim().toLowerCase()));
    for(let n=0;n<26;n++) {
        const candidate='Machine '+String.fromCharCode(65+n);
        if(!names.has(candidate.toLowerCase()))return candidate;
    }
    return 'Other machine';
}

/** Machine A/B and exercise replacement are one responsive Exercise Info
 * section, beside the per-exercise loading step; not toolbar controls. */
export function WorkoutMachineInfoActions({
    gym,exerciseId,exerciseName,selectedId,dayId,history,unit,
    onSelect,onCreate,onAssignLegacy,onSwap
}) {
    return _jsxs('section',{className:'wpb-exinfo-machine-tools',
        'aria-label':'Machine and exercise options',children:[
        _jsx('div',{className:'wpb-ey8',children:'Equipment and exercise'}),
        !!gym && isMachineLike(EX_BY_ID[exerciseId]?.equip) &&
            _jsx(WorkoutMachineVariantControl,{
                gym,exerciseId,selectedId,dayId,history,unit,
                onSelect,onCreate,onAssignLegacy
            }),
        _jsxs('button',{type:'button',className:'pressable wpb-exinfo-swap-action',
            onClick:onSwap,
            'aria-label':`Swap ${exerciseName} to another exercise`,
            children:[_jsx(Repeat,{size:17,'aria-hidden':true}),
                ' Swap to another exercise']})
    ]});
}

export function WorkoutMachineVariantControl({
    gym,exerciseId,selectedId,dayId,history=[],unit,onSelect,onCreate,onSwap,onAssignLegacy
}) {
    const isMachine=isMachineLike(EX_BY_ID[exerciseId]?.equip);
    const [open,setOpen]=useState(false);
    const [name,setName]=useState('');
    const [makeDefault,setMakeDefault]=useState(false);
    const [message,setMessage]=useState('');
    const trigger=useRef(null);
    useEffect(()=>{
        // Switching exercise or gym must not carry a machine-create draft
        // into a different movement (nor keep an old confirmation open).
        setOpen(false);setName('');setMessage('');setMakeDefault(false);
    },[exerciseId,gym?.id]);

    if(!isMachine)return null;
    const config=gym?.machineVariants?.[exerciseId];
    const instances=Array.isArray(config?.instances)?config.instances:[];
    const selected=gym
        ? resolveMachineVariant(gym,exerciseId,selectedId??null,{forNewWorkout:false})
        : {id:LEGACY_MACHINE_ID,label:'Gym unavailable',active:false,legacy:true};
    const available=instances.filter(instance=>instance.active!==false ||
        instance.id===selected.id);
    const additional=selected.missing||selected.legacy ?
        [{id:selected.id,label:selected.legacy?'Unassigned legacy sets':selected.label,
            active:false,legacy:true}]:[];
    const choices=[...additional,...available.filter(item=>item.id!==selected.id ||
        !additional.some(old=>old.id===item.id))];
    const hint=nextPhysicalMachineLabel(instances);
    // Raw, untagged historical rows are not reassigned by adding Machine B.
    // An athlete may explicitly associate these old observations with the
    // original physical machine, without changing any stored receipts.
    const olderSets=gym?machineHistory(history,{
        gymId:gym.id,exerciseId,machineInstanceId:LEGACY_MACHINE_ID
    }):[];
    const legacyOwner=config?.legacyHistoryMachineId ?? null;
    const describeLast=instance=>{
        const prior=gym?latestMachineSet(history,{
            gymId:gym.id,exerciseId,machineInstanceId:instance.id,dayId,gym
        }):null;
        return prior
            ? `Last: ${prior.weight} ${prior.unit??unit} × ${prior.reps} reps`
            : 'No comparable sets yet · enter a starting weight';
    };
    const close=()=>{setOpen(false);setMessage('');};
    const choose=id=>{
        if(id===selected.id){close();return;}
        try {
            const result=onSelect?.(id);
            if(result?.ok===false)throw new Error(result.message||'Cannot use that machine');
            close();
        } catch(error){setMessage(error?.message||'Cannot change this machine');}
    };
    const attachOlder=id=>{
        if(!onAssignLegacy)return setMessage('History assignment is unavailable for this gym.');
        try {
            const result=onAssignLegacy(id);
            if(result?.ok!==true)throw new Error(result?.message||'Could not save the original machine assignment');
            setMessage(id?'Older history is now associated with this original machine.':'Older history is unassigned again.');
        }catch(error){setMessage(String(error?.message||'Cannot associate history'));}
    };
    const add=event=>{
        event.preventDefault();
        const label=String(name||hint).trim();
        if(!label){setMessage('Give this machine a short name.');return;}
        if(!gym || !onCreate){
            setMessage('Select the workout gym before adding a machine.');return;
        }
        try {
            const result=onCreate(label,makeDefault);
            if(result?.ok!==true)throw new Error(result?.message||'Could not save this machine');
            close();setName('');setMakeDefault(false);
        } catch(error){setMessage(error?.message||'Could not add this machine');}
    };
    const modal=open && createPortal(_jsx(SessionChoiceDialog,{
        title:`Choose machine · ${EX_BY_ID[exerciseId]?.name||'Exercise'}`,
        className:'wpb-machine-switch-sheet',
        onClose:close,
        children:_jsxs('div',{className:'wpb-machine-switch-content',children:[
            _jsx('p',{className:'wpb-machine-switch-intro',children:
                'Same exercise, different physical machine. Each keeps its own weights, Last, PRs and progression. Your completed sets never move.'}),
            !gym && _jsx('p',{role:'alert',className:'wpb-machine-switch-error',
                children:'Your saved workout uses a different gym. Select that gym before creating or switching machines.'}),
            !!gym && _jsxs('div',{role:'group','aria-label':'Existing physical machines',
                className:'wpb-machine-switch-options',children:[
                    _jsx('h3',{children:'Use a machine at this gym'}),
                    !choices.length && _jsx('p',{className:'wpb-machine-switch-empty',
                        children:'No machine saved yet. Add the one in front of you below.'}),
                    ...choices.map(machine=>_jsxs(UiAction,{
                        type:'button',
                        className:'wpb-machine-switch-option',
                        'data-selected':machine.id===selected.id?'true':'false',
                        'aria-pressed':machine.id===selected.id,
                        disabled:machine.id===selected.id || machine.active===false,
                        onClick:()=>choose(machine.id),
                        children:[
                            _jsxs('span',{className:'wpb-machine-switch-option-copy',children:[
                                _jsx('strong',{children:machine.label}),
                                _jsx('small',{children:describeLast(machine)})
                            ]}),
                            machine.id===selected.id
                                ? _jsx(Check,{size:20,'aria-hidden':true})
                                : _jsx(Repeat,{size:18,'aria-hidden':true})
                        ]
                    },machine.id))
                ]}),
            !!gym && olderSets.length>0 && instances.length>0 && _jsxs('section',{
                className:'wpb-machine-legacy-assignment',
                'aria-label':'Original machine for older workout history',children:[
                _jsx('h3',{children:'Where do your older sets belong?'}),
                _jsx('p',{children:`${olderSets.length} earlier set${olderSets.length===1?'':'s'} had no physical machine label. Assign them to the machine you actually used. New machines never inherit that history automatically.`}),
                _jsxs('div',{className:'wpb-machine-legacy-choices',children:[
                    ...instances.map(instance=>_jsx(UiAction,{
                        type:'button',
                        'aria-pressed':legacyOwner===instance.id,
                        disabled:instance.id===legacyOwner,
                        onClick:()=>attachOlder(instance.id),
                        children:legacyOwner===instance.id
                            ?`${instance.label} · older history attached`
                            :`Attach old sets to ${instance.label}`
                    },instance.id)),
                    !!legacyOwner && _jsx(UiAction,{
                        type:'button',onClick:()=>attachOlder(null),
                        children:'Leave older history unassigned'
                    })
                ]})
            ]}),
            !!gym && _jsxs('form',{className:'wpb-machine-switch-create',onSubmit:add,
                children:[
                    _jsxs('div',{className:'wpb-machine-switch-create-heading',children:[
                        _jsx('h3',{children:'Different machine here?'}),
                        _jsx('p',{children:'Add it now without leaving your workout.'})
                    ]}),
                    _jsxs('label',{children:[
                        _jsx('span',{children:'Machine name'}),
                        _jsx(UiField,{as:'input',value:name,maxLength:80,
                            placeholder:hint,'aria-label':'Name of physical machine to add',
                            onChange:event=>{setName(event.target.value);setMessage('');}})
                    ]}),
                    _jsxs('label',{className:'wpb-machine-switch-default',children:[
                        _jsx(UiField,{type:'checkbox',checked:makeDefault,
                            onChange:event=>setMakeDefault(event.target.checked)}),
                        _jsx('span',{children:'Use as the default for this exercise at this gym'})
                    ]}),
                    _jsxs(UiAction,{type:'submit',variant:'primary',className:'wpb-machine-switch-add',
                        children:[_jsx(Plus,{size:18,'aria-hidden':true}),
                            _jsx('span',{children:`Add ${name.trim()||hint} & use now`})]})
                ]}),
            !!onSwap && _jsx(UiAction,{type:'button',
                className:'wpb-machine-switch-swap',
                onClick:()=>{close();onSwap();},
                children:'No suitable machine? Swap to another exercise'}),
            message && _jsx('p',{role:'alert',className:'wpb-machine-switch-error',
                children:message})
        ]})
    }),document.querySelector('#root .wpb')||document.body);

    return _jsxs('div',{className:'wpb-machine-switch',
        'data-noswipe':true,children:[
        _jsxs(UiAction,{type:'button',ref:trigger,
            className:'wpb-machine-switch-trigger',
             title:`Choose physical machine · ${selected.label}`,
            'aria-label':`Machine for ${EX_BY_ID[exerciseId]?.name||'exercise'}: ${selected.label}. Choose or add machine`,
            'aria-haspopup':'dialog','aria-expanded':open,
            onClick:()=>{setOpen(true);setMessage('');},
            children:[
                _jsx(Repeat,{size:17,'aria-hidden':true}),
                _jsx('strong',{className:'wpb-machine-switch-name',children:
                    !gym?'Machine':instances.length?selected.label:'Machine'})
            ]}),
        modal
    ]});
}
