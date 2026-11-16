import assert from 'node:assert/strict';
import { report } from '../scripts/audit-ui-style-ownership.mjs';
import { uiStyleBudget } from './ui-style-budgets.mjs';

// Source-only ownership inventory. It does not certify computed styles or
// Android runtime parity, and must not mutate training state or styling.
const expected=['Home','Plan','Program','Next workout','Cycles','Wizard',
    'Library','Progress','Profile','Settings','Gallery','Workout'];
for(const name of expected){
    assert.ok(report.screens[name]?.present,'Missing app-wide UI owner: '+name);
    assert.ok(report.screens[name].sourceCharacters>1000,
        'Source owner must refer to the actual screen, not a stub: '+name);
}
const modules=Object.entries(report.programSheetCoverage);
assert.equal(modules.length,7,'Seven independent Program sheet presentation modules audited');
assert.equal(report.programSheetHeadingCalls,8,'Eight shared Program sheet headings audited');
for(const [path,row] of modules){
    assert.ok(row.sharedHeadingCalls>=1,path+' must adopt the one sheet heading');
    assert.ok(row.sharedCloseCalls>=1,path+' must retain one accessible close control');
}
assert.ok(report.shared['Sheet heading'].ownerExported &&
    report.shared['Sheet heading'].cssRoleExists,
    'Shared heading must be exported and keep the original CSS family');
assert.ok(report.inlineDebtRanking.length===expected.length,
    'All major app destinations participate in the ordered style-debt ledger');
const ordered=report.inlineDebtRanking.map(x=>x.inlineStyleObjects);
assert.deepEqual(ordered,[...ordered].sort((a,b)=>b-a),
    'Priorities derive from actual source counts, not hardcoded screen choices');
assert.ok(report.appInlineStyleObjects<=uiStyleBudget.appInlineObjects,
    'The audit cannot normalize new App inline presentation debt');
assert.ok(report.cssPriorityDeclarations<=uiStyleBudget.cssPriorityDeclarations &&
    report.cssCharacters<=uiStyleBudget.cssCharacters,
    'Tracking more screens cannot add CSS or priority overrides');
console.log('PASS shared UI coverage: twelve destinations and eight Program headings measured.');
