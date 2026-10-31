import assert from 'node:assert/strict';
import fs from 'node:fs';

const app=fs.readFileSync('modules/App.js','utf8');
const primitives=fs.readFileSync('modules/ui-primitives.js','utf8');
const css=fs.readFileSync('app.css','utf8');
const start=app.indexOf('function SettingsView(');
const end=app.indexOf('function CompareView(',start);
const settings=app.slice(start,end);
assert.ok(start>0&&end>start,'Find the real settings view');
assert.equal((settings.match(/_jsx\(UiSegmented,/g)||[]).length,6,'Six Settings segments share one role owner');
for(const name of ['Weight unit','Training experience','Effort scale','Smallest jump','Rest length','Sex']){
 assert.ok(settings.includes('_jsx(UiSegmented, { label: "'+name+'"'),'Semantically named group: '+name);
}
assert.ok(!settings.includes('role: "group", "aria-label": "Weight unit"') &&
 !settings.includes('role: "group", "aria-label": "Rest length"') &&
 !settings.includes('role: "group", "aria-label": "Smallest jump"'),
 'No parallel per-screen native segmented button renderers');
assert.ok(settings.includes('isSelected: v => (minInc.v || 0) === v && (v === 0 || minInc.unit === unit)') &&
 settings.includes('onChange: v => setMinInc({ v, unit: v ? unit : null })'),
 'Plate increment selection must remain unit scoped and persist canonical v/unit');
assert.ok(settings.includes('onChange: setLoadMode') && settings.includes('onChange: setRestScale') &&
 settings.includes('onChange: setSex') && settings.includes('onChange: setExperience') &&
 settings.includes('onChange: setUnit'),
 'Six settings writers remain owned by real settings view');
assert.ok(primitives.includes('export function UiSegmented(') &&
 primitives.includes('role: "group", "aria-label": label') &&
 primitives.includes('kind: "tab", selected: Object.is(value, key)') &&
 primitives.includes('onClick: () => onChange(key)'),
 'Shared native button role preserves exclusive aria-pressed and callback');
assert.ok(css.includes('.wpb .wpb-settings .wpb-segmented>button{display:flex;align-items:center;justify-content:center;min-width:48px;min-height:48px;'),
 'Existing Settings segmented geometry remains unchanged');
assert.equal((settings.match(/_jsx\("div", \{ className: "wpb-segmented/g)||[]).length,0,
 'Settings must not keep six parallel markup/selection implementations');
assert.equal((css.match(/!important/g)||[]).length,2024,'No CSS priority expansion');
assert.ok((app.match(/style: \{/g)||[]).length<=869,'No inline style debt expansion');
console.log('PASS shared Settings segmented controls: six value writers, UI role, 48px hit and no CSS debt growth.');
