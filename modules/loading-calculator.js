import { jsx as _jsx, jsxs as _jsxs } from 'react/jsx-runtime';
import { useState } from 'react';
import { SessionChoiceDialog } from './session-choice-dialog.js';
import { UiChoice, UiAction } from './ui-primitives.js';
import { BARS, BAR_LABEL, barWeightFor, plateLoadPreview, repMaxPreview } from './engine-api.js';

export function PlateCalcSheet({ unit, onClose, initialBar = 'barbell', initialWeight = '', initialTab = 'plates', sheetDragRef, selectNumericInput }) {
    const [tab, setTab] = useState(initialTab === 'orm' ? 'orm' : 'plates');
    const [bar, setBar] = useState(BARS[initialBar] ? initialBar : 'barbell');
    const [weight, setWeight] = useState(initialWeight == null ? '' : String(initialWeight));
    const [ormWeight, setOrmWeight] = useState(''), [ormReps, setOrmReps] = useState('');
    const load = plateLoadPreview(weight, bar, unit), estimate = repMaxPreview(ormWeight, ormReps, unit);
    const inputProps = { inputMode:'decimal', onFocus:selectNumericInput, onClick:selectNumericInput, className:'mono wpb-ui-control wpb-calculator-input' };
    const label = value => Math.round(value * 1000000) / 1000000;
    const plateView = _jsxs('div', { className:'wpb-calculator-pane', children:[
        _jsx('div', { role:'group', 'aria-label':'Bar type', className:'wpb-calculator-bars', children:Object.keys(BARS).map(kind =>
            _jsxs(UiChoice, { selected:bar===kind, className:'wpb-calculator-bar', onClick:()=>setBar(kind), children:[
                _jsx('strong', {children:BAR_LABEL[kind] || kind}), _jsx('span', {children:`${barWeightFor(kind,unit)} ${unit} empty`})
            ] }, kind)) }),
        _jsxs('div', {className:'wpb-calculator-field',children:[
            _jsx('label', {htmlFor:'calculator-target',children:`Target weight (${unit})`}),
            _jsx('input', {...inputProps,id:'calculator-target',placeholder:`Target weight (${unit})`,value:weight,onChange:event=>setWeight(event.target.value),
                'aria-invalid':load.status==='invalid','aria-describedby':'calculator-target-help'}),
            _jsx('p', {id:'calculator-target-help',className:load.status==='invalid'?'wpb-calculator-error':'wpb-calculator-help',role:load.status==='invalid'?'alert':undefined,
                children:load.status==='invalid'?'Enter a complete, nonnegative weight within the supported loading range.':'Include the empty bar in the total. Plates reflect your selected gym.'})
        ]}),
        !['empty','invalid'].includes(load.status) && _jsxs('section', {className:'wpb-calculator-result','data-load-result':load.status,'aria-live':'polite',children:[
            load.status==='below_bar' ? _jsx('p', {children:`This target is below the ${load.barW} ${unit} empty bar.`}) : _jsxs('div', {children:[
                _jsx('strong', {className:'mono wpb-calculator-load',children:`${label(load.achievable)} ${unit}`}),
                _jsx('p', {children:load.status==='exact'?(load.plates.length?'Exact load · same plates on each side':'Empty bar · no plates needed')
                    :load.status==='no_plates'?'Closest loadable. No available plate combination fits this target.':'Closest loadable at or below your target.'})
            ]}),
            load.plates.length>0 && _jsxs('div', {children:[
                _jsx('div', {className:'wpb-visual-plate-bar wpb-calculator-visual',role:'img','data-plate-side':'one',
                    'aria-label':`One side of the barbell: ${load.plates.join(', ')} ${unit} plates, inner to outer. ${load.barW} ${unit} bar.`,
                    children:_jsxs('div', {className:'wpb-calculator-sleeve','aria-hidden':true,children:[
                        _jsx('span', {className:'wpb-calculator-shaft'}),
                        ...load.plates.map((plate,index)=>_jsx('span', {className:'wpb-visual-plate wpb-calculator-plate','data-plate':plate,
                            style: { '--wpb-plate-height':`${Math.round(36+Math.min(1,plate/Math.max(...load.plates))*56)}px`},children:plate},index)),
                        _jsx('span', {className:'wpb-calculator-collar'})
                    ]})}),
                _jsx('p', {children:'Load these plates on each side:'}),
                _jsx('div', {className:'wpb-calculator-plate-list',children:load.plates.map((plate,index)=>_jsx('span',{className:'mono',children:`${plate} ${unit}`},index))}),
                _jsx('p', {children:`${load.barW} ${unit} bar + ${load.plates.length} ${load.plates.length===1?'plate':'plates'} per side`})
            ]})
        ]})
    ]});
    const ormView = _jsxs('div', {className:'wpb-calculator-pane',children:[
        _jsxs('div', {className:'wpb-calculator-fields',children:[
            _jsxs('div', {className:'wpb-calculator-field',children:[_jsx('label',{htmlFor:'calculator-orm-weight',children:`Weight (${unit})`}),
                _jsx('input',{...inputProps,id:'calculator-orm-weight',value:ormWeight,onChange:event=>setOrmWeight(event.target.value),
                    'aria-invalid':estimate.weightStatus==='invalid','aria-describedby':'calculator-orm-help'})]}),
            _jsxs('div', {className:'wpb-calculator-field',children:[_jsx('label',{htmlFor:'calculator-orm-reps',children:'Completed reps'}),
                _jsx('input',{...inputProps,id:'calculator-orm-reps',inputMode:'numeric',value:ormReps,onChange:event=>setOrmReps(event.target.value),
                    'aria-invalid':estimate.repsStatus==='invalid','aria-describedby':'calculator-orm-help'})]})
        ]}),
        _jsx('p',{id:'calculator-orm-help',className:estimate.status==='invalid'?'wpb-calculator-error':'wpb-calculator-help',role:estimate.status==='invalid'?'alert':undefined,
            children:estimate.status==='invalid'?`Enter a positive weight and a whole rep count from 1 to ${estimate.maxReps}.`:`Use 1–${estimate.maxReps} completed reps. This is an estimate, not a tested maximum.`}),
        estimate.status==='valid' && _jsxs('div',{className:'wpb-calculator-result',children:[
            _jsx('h3',{children:'Estimated 1RM'}),
            _jsx('strong',{className:'mono wpb-calculator-max','data-estimated-max':estimate.oneRM,children:`${estimate.oneRM} ${unit}`}),
            _jsx('p',{children:'Epley estimate · training percentages below'}),
            _jsx('div',{className:'wpb-calculator-percentages',children:estimate.percentages.map(({percent,load})=>
                _jsxs(UiAction,{'data-calculator-percent':percent,'aria-label':`${percent}%: ${load} ${unit}. View plates`,onClick:()=>{setWeight(String(load));setTab('plates');},children:[
                    _jsx('span',{children:`${percent}%`}),_jsx('strong',{className:'mono',children:`${load} ${unit}`})
                ]},percent))}),
            _jsx('p',{children:`Loads round down to ${estimate.increment} ${unit} steps. Tap a load to check the plates your gym can provide.`})
        ]})
    ]});
    return _jsx(SessionChoiceDialog,{title:'Calculators',onClose,panelRef:sheetDragRef,className:'wpb-loading-calculator',children:_jsxs('div',{className:'wpb-calculator-body wpb-scroll',children:[
        _jsx('div',{role:'group','aria-label':'Calculator',className:'wpb-calculator-tabs',children:[['plates','Plates'],['orm','1RM estimate']].map(([id,text])=>
            _jsx(UiChoice,{selected:tab===id,onClick:()=>setTab(id),children:text},id))}),
        tab==='plates'?plateView:ormView
    ]})});
}
