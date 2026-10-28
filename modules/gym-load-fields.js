import { jsx as _jsx, jsxs as _jsxs } from 'react/jsx-runtime';
import { UiAction } from './ui-primitives.js';
import { BARS, ALL_PLATES, INVENTORY_KEYS, GYM_LIMIT_KEYS, RACK_PRESETS, platePairsFor, prepareGymLoadDraft, withGymBarUnit, withGymLoadingUnit } from './engine-api.js';

const display = value => typeof value === 'number' ? String(Math.round(value * 1000) / 1000) : value ?? '';

/** Resolve each staged edit against the latest gym, not the render that began typing. */
export const gymBarDraftUpdate = (kind, value) => gym => {
    const bars = gym.barWeights || {};
    return { barWeights: { ...bars, unit: bars.unit === 'kg' ? 'kg' : 'lb', [kind]: value } };
};
export const gymPlatePairDraftUpdate = (unit, plate, value, plates) => gym => {
    const counts = platePairsFor(gym, unit, plates?.[unit]);
    return { platePairs: { ...gym.platePairs, [unit]: {
        ...counts, ...gym.platePairs?.[unit], [plate]: value
    } } };
};

export function GymLoadFields({ gym, plates, onChange, onInvalid, selectNumericInput }) {
    const draft = prepareGymLoadDraft(gym), eq = gym.equipment || [], measure = gym.limitUnit || 'kg';
    const id = suffix => `gym-${gym.id}-${suffix}`;
    const errorFor = suffix => draft.issues.find(issue => issue.fieldId === id(suffix));
    const fieldProps = suffix => ({ id:id(suffix), 'aria-invalid':!!errorFor(suffix), 'aria-describedby':errorFor(suffix)?id(suffix)+'-help':undefined });
    const feedback = suffix => errorFor(suffix) && _jsx('p',{id:id(suffix)+'-help',role:'alert',className:'wpb-gym-field-error',children:errorFor(suffix).message});
    const apply = result => { if(result.ok) onChange({...result.gym,inventoryText:undefined}); else if(result.issues[0]) onInvalid(result.issues[0]); };
    const barKinds = ['barbell','smith'].filter(kind=>eq.includes(kind)||gym.barWeights?.[kind]!==undefined);
    const bars = gym.barWeights || {}, barUnit = bars.unit === 'kg' ? 'kg' : 'lb';
    const setRack = (key, values) => {
        const inventory={...gym.inventory}, inventoryText={...gym.inventoryText};delete inventoryText[key];
        if(values.length)inventory[key]=values;else {delete inventory[key];inventoryText[key]='';}
        onChange({inventory,inventoryText});
    };
    return _jsxs('div',{className:'wpb-gym-load-fields',children:[
        barKinds.length>0 && _jsxs('div',{className:'wpb-gym-bar-masses',children:[
            _jsx('div',{className:'wpb-gym-bar-label',children:'Empty bar weight'}),
            _jsx('p',{className:'wpb-gym-bar-description',children:'Log the total weight including the bar. Leave blank for the standard bar; a counterbalanced Smith can be 0.'}),
            ...barKinds.map(kind=>_jsxs('div',{className:'wpb-gym-field-block',children:[
                _jsxs('div',{className:'wpb-gym-inline',children:[
                    _jsx('label',{htmlFor:id(`${kind}-bar-weight`),className:'wpb-gym-bar-name',children:kind==='smith'?'Smith bar':'Barbell'}),
                    _jsx('input',{...fieldProps(`${kind}-bar-weight`),'aria-label':`${kind==='smith'?'Smith':'Barbell'} empty bar weight`,inputMode:'decimal',value:display(bars[kind]),placeholder:String(BARS[kind][barUnit]),
                        onFocus:selectNumericInput,onClick:selectNumericInput,onChange:event=>onChange(gymBarDraftUpdate(kind,event.target.value)),className:'wpb-gym-bar-field wpb-ui-control'}),
                    _jsx('span',{className:'wpb-gym-bar-measure',children:barUnit})
                ]}),feedback(`${kind}-bar-weight`)
            ]},kind)),
            _jsx(UiAction,{className:'wpb-gym-bar-unit','aria-label':'Change bar weight units',onClick:()=>apply(withGymBarUnit(gym,barUnit==='kg'?'lb':'kg')),children:`Use ${barUnit==='kg'?'lb':'kg'} instead`})
        ]}),
        (barKinds.length>0||gym.platePairs) && _jsxs('details',{className:'wpb-gym-bar-masses wpb-gym-plate-counts',children:[
            _jsx('summary',{children:'Available plate pairs'}),_jsx('p',{className:'wpb-gym-bar-description',children:'One pair is one plate on each side. Use 0 for missing sizes; up to 50 pairs of each size.'}),
            ...['lb','kg'].map(unit=>_jsxs('fieldset',{children:[_jsx('legend',{children:unit}),...ALL_PLATES[unit].map(plate=>{
                const counts=platePairsFor(gym,unit,plates[unit]), suffix=`plates-${unit}-${plate}`;
                return _jsxs('div',{className:'wpb-gym-field-block',children:[_jsxs('div',{className:'wpb-gym-inline',children:[
                    _jsx('label',{htmlFor:id(suffix),className:'wpb-gym-bar-name',children:`${plate} ${unit}`}),
                    _jsx('input',{...fieldProps(suffix),'aria-label':`${plate} ${unit} plate pairs`,inputMode:'numeric',value:gym.platePairs?.[unit]?.[plate]??counts[plate],onFocus:selectNumericInput,onClick:selectNumericInput,
                        onChange:event=>onChange(gymPlatePairDraftUpdate(unit,plate,event.target.value,plates)),className:'wpb-gym-bar-field wpb-ui-control'})
                ]}),feedback(suffix)]},plate);
            })]},unit))
        ]}),
        (eq.length>0||Object.keys(gym.inventoryText||{}).length>0) && _jsxs('div',{className:'wpb-gym-section',children:[
            _jsx('div',{className:'wpb-gym-limit-label',children:'Available loads and limits'}),
            _jsx(UiAction,{className:'wpb-gym-loading-unit','aria-label':'Change gym load units',onClick:()=>apply(withGymLoadingUnit(gym,measure==='kg'?'lb':'kg')),children:`Loads in ${measure} · use ${measure==='kg'?'lb':'kg'}`}),
            ...INVENTORY_KEYS.filter(key=>key.match(eq)||gym.inventoryText?.[key.key]!==undefined).map(key=>{
                const values=draft.gym.inventory[key.key]||[], suffix=`rack-${key.key}`;
                return _jsxs('div',{className:'wpb-gym-inventory',children:[
                    _jsxs('div',{className:'wpb-gym-inventory-heading',children:[_jsx('label',{htmlFor:id(suffix),className:'wpb-gym-inventory-name',children:key.label}),_jsx('span',{className:'wpb-gym-inventory-hint',children:`${key.sub} · ${measure}`})]}),
                    _jsx('p',{className:'wpb-gym-inventory-summary','data-has-inventory':!!values.length,children:errorFor(suffix)?'Correct the list before saving.':values.length?`${values.length} available loads · up to ${display(values.at(-1))} ${measure}`:'No rack list · use the usual increment and optional limit below.'}),
                    _jsxs('div',{className:'wpb-hscroll wpb-gym-racks',children:[...(RACK_PRESETS[key.key]||[]).filter(preset=>preset.unit===measure).map(preset=>
                        _jsx(UiAction,{onClick:()=>setRack(key.key,[...preset.v]),className:'wpb-gym-rack-preset',children:preset.name},preset.name)),
                        (values.length>0||gym.inventoryText?.[key.key]) && _jsx(UiAction,{className:'wpb-gym-clear-action',onClick:()=>setRack(key.key,[]),children:'Clear'})]}),
                    _jsx('input',{...fieldProps(suffix),'aria-label':`${key.label} available loads`,inputMode:'text',value:gym.inventoryText?.[key.key]??(gym.inventory?.[key.key]||[]).map(display).join(', '),placeholder:'e.g. 5, 10, 15, 20, 22.5',
                        onChange:event=>onChange({inventoryText:{...gym.inventoryText,[key.key]:event.target.value}}),className:'wpb-gym-inventory-field wpb-ui-control'}),feedback(suffix)
                ]},key.key);
            }),
            ...GYM_LIMIT_KEYS.filter(key=>(key.match(eq)||gym.limits?.[key.key]!==undefined)&&!((draft.gym.inventory||{})[key.key]||[]).length).map(key=>{
                const suffix=`cap-${key.key}`;
                return _jsxs('div',{className:'wpb-gym-field-block',children:[_jsxs('div',{className:'wpb-gym-inline',children:[
                    _jsxs('div',{className:'wpb-selection-copy',children:[_jsx('label',{htmlFor:id(suffix),className:'wpb-gym-limit-name',children:key.label}),_jsx('span',{className:'wpb-gym-limit-hint',children:key.sub})]}),
                    _jsx('input',{...fieldProps(suffix),'aria-label':`${key.label} maximum load`,inputMode:'decimal',onFocus:selectNumericInput,onClick:selectNumericInput,value:display(gym.limits?.[key.key]),
                        onChange:event=>onChange({limits:{...gym.limits,[key.key]:event.target.value}}),placeholder:'—',className:'wpb-gym-limit-field wpb-ui-control'}),
                    _jsx(UiAction,{className:'wpb-gym-limit-unit','aria-label':`Change ${key.label} load units`,onClick:()=>apply(withGymLoadingUnit(gym,measure==='kg'?'lb':'kg')),children:measure})
                ]}),feedback(suffix)]},key.key);
            }),
            _jsx('p',{className:'wpb-gym-guidance',children:'Leave a limit blank for no ceiling. Changing units converts every rack and limit together; it does not relabel the old numbers.'})
        ]})
    ]});
}
