import { jsx as _jsx, jsxs as _jsxs } from 'react/jsx-runtime';
import { useMemo, useState, useEffect } from 'react';
import { UiAction, UiChoice, UiField, UiSheetClose, UiStatus } from './ui-primitives.js';
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
        options.length && _jsxs('div', {
            className:'wpb-hscroll wpb-b964-layout-10',
            role:'group','aria-label':label,
            children:[
                _jsx('span',{className:'wpb-type-caption-faint',children:label}),
                _jsx(UiChoice,{selected:selected==='all',
                    onClick:()=>onSelect('all'),children:allLabel}),
                ...options.map(option=>_jsx(UiChoice,{
                    selected:selected===option.id,
                    onClick:()=>onSelect(option.id),children:option.label
                },option.id))
            ]
        });
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
    return _jsx('div',{className:'wpb-backdrop wpb-b961-overlay-06',
        onClick:onClose,
        children:_jsxs('div',{
            ref:sheetDragRef,onClick:event=>event.stopPropagation(),
            className:'wpb-ui-sheet-panel wpb-exercise-browser-sheet',
            children:[
                _jsxs('header',{
                    className:'wpb-ui-sheet-heading',
                    children:[
                        _jsx('h2',{className:'wpb-type-value',children:title}),
                        _jsx(UiSheetClose,{onClick:onClose,'aria-label':'Close exercise picker'})
                    ]
                }),
                _jsx(UiField,{as:'input',value:query,
                    onChange:event=>setQuery(event.target.value.slice(0,80)),
                    'aria-label':'Search exercise names, setup variants and attachments',
                    placeholder:`Search ${exercises.length} exercises, attachments or inclines`
                }),
                _jsx('div',{className:'wpb-hscroll wpb-b964-layout-10',
                    role:'group','aria-label':'Target muscle',
                    children:['all',...parts].map(p=>_jsx(UiChoice,{
                        selected:part===p,onClick:()=>setPart(p),
                        children:p==='all'?'All':partLabels[p]
                    },p))
                }),
                _jsx(ExerciseVariantFacetFilters,{
                    candidates:pool,level,onLevel:setLevel,
                    attachment,onAttachment:setAttachment
                }),
                _jsx(UiStatus,{role:'status',
                    children:`${matches.length} matching setups · ${exercises.length} catalog exercises. Distinct variants retain their own recorded loads.`
                }),
                _jsxs('div',{className:'wpb-scroll wpb-b965-layout-09',
                    children:[
                        ...visible.map(ex=>_jsxs(UiAction,{
                            variant:'tonal',
                            className:'wpb-exercise-picker-choice',
                            disabled:blocked.has(ex.id),
                            'aria-label':`Choose ${displayName(ex)}`,
                            onClick:()=>onPick(ex.id),
                            children:[
                                _jsx('span',{className:'wpb-type-value',
                                    children:displayName(ex)+
                                        (blocked.has(ex.id)?' · banned':'')}),
                                _jsx('span',{className:'wpb-type-subline',
                                    children:equipmentDescription(ex)})
                            ]
                        },ex.id)),
                        visible.length<matches.length && _jsx(UiAction,{
                            variant:'tonal',className:'wpb-exercise-picker-more',
                            onClick:()=>setVisibleCount(n=>Math.min(
                                matches.length,n+40)),
                            children:`Show more (${matches.length-visible.length} remaining)`
                        }),
                        matches.length===0 && _jsx(UiStatus,{role:'status',
                            children:'No matching setups. Clear a search or variant filter.'})
                    ]
                })
            ]
        })
    });
}
