import { jsx as _jsx, jsxs as _jsxs } from 'react/jsx-runtime';
import { useMemo, useState, useEffect } from 'react';
import { UiAction, UiChoice, UiField, UiListItem, UiStatus } from './ui-primitives.js';
import { UiSheet } from './ui-sheet.js';
import { availableVariantFacets, matchesVariantFacets } from './exercise-variant-browse.js';

/**
 * These are only *visible* variant filters. Gym availability, historical
 * machine identity and source workout prescriptions remain elsewhere.
 * All actions use the existing Material action/choice primitives; no
 * additional ad-hoc inline button styles or release-design overrides.
 */
export function ExerciseVariantFacetFilters({ candidates, level, onLevel,
    attachment, onAttachment }) {
    const facets = useMemo(() => availableVariantFacets(candidates), [candidates]);
    const group=(label,options,selected,onSelect,allLabel) =>
        options.length ? _jsxs('div', {
            className:'wpb-ui-chip-row',
            role:'group','aria-label':label,
            children:[
                _jsx('span',{className:'wpb-ui-chip-row-label',children:label}),
                _jsx(UiChoice,{kind:'filter',selected:selected==='all',
                    onClick:()=>onSelect('all'),children:allLabel}),
                ...options.map(option=>_jsx(UiChoice,{kind:'filter',
                    selected:selected===option.id,
                    onClick:()=>onSelect(option.id),children:option.label
                },option.id))
            ]
        }) : null;
    return _jsxs('div',{className:'wpb-variant-facets',children:[
        group('Bench incline',facets.levels,level,onLevel,'Any angle'),
        group('Cable attachment',facets.attachments,attachment,onAttachment,
            'Any attachment')
    ]});
}

/** A source-backed catalog never stops at an arbitrary first-100 cutoff.
 * Filter the full effective list, then paginate. Banned entries are
 * disabled, not a pending writable selection or authority to swap.
 */
export function ExercisePickerSheet({ onPick,onClose,banned=[],title='Add exercise',
    exercises,parts,partLabels,displayName,equipmentDescription,searchable,
    sheetDragRef }) {
    const [query,setQuery]=useState('');
    const [part,setPart]=useState('all');
    const [level,setLevel]=useState('all');
    const [attachment,setAttachment]=useState('all');
    const [visibleCount,setVisibleCount]=useState(40);
    const pool=useMemo(()=>exercises.filter(ex=>
        part==='all'||ex.part===part),[exercises,part]);
    const matches=useMemo(()=>pool.filter(ex=>
        searchable(ex,query)&&matchesVariantFacets(ex,{level,attachment})),
    [pool,query,level,attachment,searchable]);
    useEffect(()=>{setVisibleCount(40)},[query,part,level,attachment]);
    const visible=matches.slice(0,visibleCount);
    const blocked=new Set(banned);
    return _jsx(UiSheet,{id:'exercise-picker',title,onClose,closeLabel:'Close exercise picker',sheetDragRef,
        className:'wpb-exercise-picker-backdrop',panelClassName:'wpb-exercise-browser-sheet',bodyClassName:'wpb-exercise-picker-body',
        toolbar:[
            _jsx(UiField,{as:'input',value:query,
                onChange:event=>setQuery(event.target.value.slice(0,80)),
                'aria-label':'Search exercise names, setup variants and attachments',
                placeholder:`Search ${exercises.length} exercises, attachments or inclines`
            },'search'),
            _jsx('div',{className:'wpb-ui-chip-row',role:'group','aria-label':'Target muscle',
                children:['all',...parts].map(p=>_jsx(UiChoice,{kind:'filter',
                    selected:part===p,onClick:()=>setPart(p),
                    children:p==='all'?'All':partLabels[p]
                },p))
            },'parts'),
            _jsx(ExerciseVariantFacetFilters,{
                candidates:pool,level,onLevel:setLevel,
                attachment,onAttachment:setAttachment
            },'facets')
        ],
        children:[
            _jsx(UiStatus,{role:'status',
                children:`${matches.length} matching setups · ${exercises.length} catalog exercises. Distinct variants retain their own recorded loads.`
            },'status'),
            _jsx('div',{className:'wpb-ui-list',children:visible.map(ex=>_jsx(UiListItem,{
                className:'wpb-exercise-picker-choice',
                disabled:blocked.has(ex.id),
                'aria-label':`Choose ${displayName(ex)}`,
                onClick:()=>onPick(ex.id),
                headline:displayName(ex)+(blocked.has(ex.id)?' · banned':''),
                supporting:equipmentDescription(ex)
            },ex.id))},'list'),
            visible.length<matches.length && _jsx(UiAction,{
                variant:'tonal',className:'wpb-exercise-picker-more',
                onClick:()=>setVisibleCount(n=>Math.min(
                    matches.length,n+40)),
                children:`Show more (${matches.length-visible.length} remaining)`
            },'more'),
            matches.length===0 && _jsx(UiStatus,{role:'status',
                children:'No matching setups. Clear a search or variant filter.'},'empty')
        ]
    });
}
