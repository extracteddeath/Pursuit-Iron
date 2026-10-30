import assert from 'node:assert/strict';
import fs from 'node:fs';
const app=fs.readFileSync('modules/App.js','utf8'),css=fs.readFileSync('app.css','utf8');
const workout=app.slice(app.indexOf('function WorkoutSession('),app.indexOf('function shareSession('));
const info=workout.slice(workout.indexOf('_jsx(Exit, { when: info'),workout.indexOf('_jsx(Exit, { when:',workout.indexOf('_jsx(Exit, { when: info')+1));
assert.ok(info.length>10000,'Info renderer must be covered');
const names=['meta','metric','metric-value','record-value','metric-label','why-title','why-copy','why-chip',
 'why-disclosure','muted','rest-row','rest-caption','secondary-copy','pinned','pinned-copy','trend-value',
 'trend-empty','trend-dates','cue-dot','cue-copy','tempo','tempo-copy','demo-icon','demo-copy',
 'weight-step-card','helper','history-date','set-helper','sheet','close','rest-step','rest-auto','weight-choice'];
for(const name of names){
 const role='wpb-exinfo-'+name;
 assert.ok(info.includes(role),'Missing semantic owner '+role);
 assert.ok(css.includes('.'+role+'{')||css.includes('.'+role+',')||css.includes('.'+role+')'),'No CSS role '+role);
}
for(const [names,shared] of [
  [['metric-value','record-value'],'font-size:15px;color:var(--wpb-theme-accent-ink);overflow-wrap:anywhere'],
  [['why-copy','cue-copy'],'font-size:13px;color:var(--wpb-theme-text);overflow-wrap:anywhere'],
  [['metric-label','rest-caption'],'font-size:11px;color:var(--wpb-theme-muted)'],
  [['secondary-copy','tempo-copy','demo-copy','set-helper'],'font-size:13px;color:var(--wpb-theme-muted)']
]){
  const rule='#root .wpb :is('+names.map(name=>'.wpb-exinfo-'+name).join(',')+'){'+shared+'}';
  assert.ok(css.includes(rule),'One scoped typography owner required for '+names.join(','));
}
assert.match(info,/role: "dialog", "aria-modal": true/,'Info is an actual named modal');
assert.match(info,/"aria-labelledby": "wpb-exinfo-title"/);
assert.match(info,/autoFocus: true, className: "pressable hit wpb-sheet-close wpb-exinfo-close"/);
assert.ok(info.includes("ev.key === \"Escape\"")&&info.includes("ev.key !== \"Tab\"")&&info.includes("ev.preventDefault(); ev.stopPropagation(); setInfo(false)"),
 'Info owns Escape and focus containment instead of leaking shortcuts');
assert.ok(workout.includes('infoOpenerRef.current = event.currentTarget')&&workout.includes('opener?.isConnected'),'Closing restores keyboard focus to the Info opener');
assert.ok(info.includes('"aria-pressed": active, onClick: () => onSetExLoadInc(ex.id, v, unit)'),'Load step choices retain exact state and writer');
assert.ok(info.includes('setRestCustom(exIdx, restNow + 15)')&&info.includes('setRestCustom(exIdx, Math.max(15, restNow - 15))'),'Rest changes use existing state');
const infoActionSizing=css.split('#root .wpb :is(.wpb-exinfo-close,.wpb-exinfo-rest-step,.wpb-exinfo-rest-auto,.wpb-exinfo-weight-choice){')[1]?.split('}')[0]||'';
for(const prop of ['box-sizing:border-box','min-width:48px','min-height:48px','border:1px solid var(--wpb-theme-border)','cursor:pointer'])
  assert.ok(infoActionSizing.includes(prop),'Info controls share '+prop);
assert.ok(css.includes('min-width:48px;min-height:48px'),'Rest actions must meet 48px target');
const whyDisclosure=css.split('#root .wpb .wpb-exinfo-why-disclosure{')[1]?.split('}')[0]||'';
assert.ok(whyDisclosure.includes('min-height:48px'),'Workout Info Why disclosure must meet 48px target');
assert.ok(css.includes('max-height:min(90dvh,calc(var(--visual-h'),'Sheet must account for visual viewport');
// The legacy theme's !important all-sheet cap must not override Info's dedicated
// 90dvh visual-viewport owner, including compact-landscape and detent modes.
assert.equal((app.match(/:not\(:is\(\.wpb-program-share-panel,\.wpb-exercise-options-sheet,\.wpb-exinfo-sheet\)\)\{max-height:/g)||[]).length,4,
 'Generic sheet caps exclude Info in phone, tablet and both detent selectors');
assert.ok(app.includes(':not(.wpb-exinfo-sheet){max-height:calc(var(--visual-h'),
 'Compact landscape also leaves Info viewport sizing to its scoped CSS owner');
assert.ok((app.match(/style: \{/g)||[]).length<=1101,'Fixed Workout info CSS must not return to inline style');
assert.ok((css.match(/!important/g)||[]).length<=2060,'No priority overrides added');
assert.ok(css.length<=643592,'CSS migration budget must not grow without review');
console.log('PASS B993: Workout Info presentation, focus ownership, responsive targets and CSS debt guards.');