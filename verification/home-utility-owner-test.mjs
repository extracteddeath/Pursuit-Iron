import assert from 'node:assert/strict';
import fs from 'node:fs';
import { report } from '../scripts/audit-ui-style-ownership.mjs';
import { uiStyleBudget } from './ui-style-budgets.mjs';

const app=fs.readFileSync('modules/App.js','utf8');
const css=fs.readFileSync('app.css','utf8');
const start=app.indexOf('function Home(');
const end=app.indexOf('function UiNotice(',start);
assert.ok(start>=0 && end>start,'Home component still has its own measured source range');
const home=app.slice(start,end);
assert.equal((home.match(/style:\s*\{/g)||[]).length,16,'Home fixed-presentation migration has no regression');
assert.equal(report.appInlineStyleObjects,803,'App-wide inline debt must not grow');
assert.equal(uiStyleBudget.appInlineObjects,803,'Tightened debt ceiling must match measured code');
for(const owner of ['pi-t-strong wpb-s956-flex-1','pi-t-text-600','pi-t-muted wpb-s956-overflow-hidden','pi-t-caption-mt2'])
 assert.ok(home.includes(owner),'Home must reuse the existing presentation utility '+owner);
for(const existing of ['onClick: () => onOpenCycles(cycleInfo.cycle.id)','onClick: onBackup',
 'cycleInfo.isDeload ? "Deload week"','cycleInfo.dayLabel','onClick: () => setBkHide(true)'])
 assert.ok(home.includes(existing),'Cycle and backup behavior must remain intact: '+existing);
assert.ok(css.includes('wpb-s956-textoverflow-ellipsis') && css.includes('pi-t-text-600') && css.includes('pi-t-caption-mt2'),
 'Utility roles must resolve in the canonical stylesheet');
assert.ok((css.match(/!important/g)||[]).length <= uiStyleBudget.cssPriorityDeclarations,'No additional CSS priority debt');
console.log('PASS Home fixed style utility reuse: five inline objects retired with original controls intact.');
