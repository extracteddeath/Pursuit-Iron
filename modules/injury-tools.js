// User-authored training restrictions. Area matching is a review aid, not a diagnosis.
export const INJURY_AREAS = [
    { id:'shoulder', label:'Shoulder', group:'Upper body', parts:['shoulders'] },
    { id:'elbow', label:'Elbow', group:'Upper body', parts:['biceps','triceps','forearms'] },
    { id:'wrist', label:'Wrist / hand', group:'Upper body', parts:['forearms'] },
    { id:'hip', label:'Hip / groin', group:'Lower body', parts:['glutes','adductors','abductors'] },
    { id:'knee', label:'Knee', group:'Lower body', parts:['quads','hamstrings'] },
    { id:'ankle', label:'Ankle / foot', group:'Lower body', parts:['calves'] },
    { id:'neck', label:'Neck', group:'Torso', parts:['neck','traps'] },
    { id:'upper_back', label:'Upper back', group:'Torso', parts:['upper_back','traps','lats'] },
    { id:'lower_back', label:'Lower back', group:'Torso', parts:['lower_back'] },
    { id:'chest', label:'Chest / ribs', group:'Torso', parts:['chest'] },
    { id:'core', label:'Abdomen / core', group:'Torso', parts:['abs'] }
];
export const INJURY_IMPACTS = [
    { id:'monitor', label:'Review before training', detail:'Flag related lifts so I can decide each session.' },
    { id:'reduce', label:'Reduce affected work', detail:'Prefer fewer remaining sets when I review the workout.' },
    { id:'skip', label:'Skip affected lifts', detail:'Prefer skipping related movements for the session.' }
];
export function injuryParts(entry) {
    return INJURY_AREAS.find(area=>area.id===entry?.region)?.parts || (entry?.part ? [entry.part] : []);
}
export function injuryTitle(entry, labels={}) {
    const area=INJURY_AREAS.find(area=>area.id===entry?.region)?.label || labels[entry?.part] || entry?.part || 'Injury';
    const side={left:'Left',right:'Right',both:'Both sides ·'}[entry?.side];
    return side ? side+' '+area.charAt(0).toLowerCase()+area.slice(1) : area;
}
export function injuryMatchesExercise(entry, exercise, secondary=[]) {
    const parts=injuryParts(entry);
    return !!exercise && (parts.includes(exercise.part) || secondary.some(([part])=>parts.includes(part)));
}
export function normalizeInjuryLog(rows) {
    return (Array.isArray(rows)?rows:[]).filter(row=>row && typeof row==='object' && typeof row.id==='string' && typeof row.part==='string')
        .map(row=>({...row, note:typeof row.note==='string'?row.note:'',
            side:['left','right','both'].includes(row.side)?row.side:'unspecified',
            impact:['monitor','reduce','skip'].includes(row.impact)?row.impact:'monitor' }));
}
export function mergeInjuryReports(entries, reports, sessionId) {
    let next=normalizeInjuryLog(entries);
    for(const [index, report] of (reports || []).entries()) {
        const adjustment={...report,id:sessionId+':injury:'+index,sessionId};
        const existing=next.find(entry=>entry.id===report.injuryId);
        if(existing) next=next.map(entry=>entry.id!==existing.id?entry:{...entry,
            updatedAt:Math.max(Number(entry.updatedAt)||0,Number(report.at)||0),lastAdjustment:adjustment,
            adjustments:[...(entry.adjustments || []).filter(item=>item.id!==adjustment.id),adjustment]});
        else next=[{...adjustment,impact:report.action==='skip'?'skip':'reduce'},...next.filter(entry=>entry.id!==adjustment.id)];
    }
    return next;
}
