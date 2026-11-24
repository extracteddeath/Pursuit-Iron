import { uiStyleBudget } from './ui-style-budgets.mjs';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { report } from '../scripts/audit-ui-style-ownership.mjs';

const app=fs.readFileSync('modules/App.js','utf8');
const primitives=fs.readFileSync('modules/ui-primitives.js','utf8');
const css=fs.readFileSync('app.css','utf8');
assert.equal(report.unownedStandardCardCallSites,0,'Standard cards must render through UiCard');
// B1026: the weekly recap card moved to modules/home-week.js, which still renders UiCard.
// B1028: the Home Strength Score button became a UiCard (as: "button").
// B1040: Plan's program routes are one grouped UiCard list.
assert.equal(report.shared.Card.appCalls,29,'Card adoption must cover all audited call sites');
assert.equal(report.screens.Settings.sharedCardCalls,14,'Settings has eight grouped cards plus plates inventory');
assert.ok(app.includes('_jsxs(UiCard, { as: "section", className: "wpb-profile-share-card"'),
 'Profile share card must remain a semantic section');
assert.ok(app.includes('_jsxs(UiCard, { className: "wpb-gallery-program"'),
 'Gallery card must use shared surface paint');
assert.ok(primitives.includes('export function UiCard(') && primitives.includes('wpb-ui-card'),
 'UiCard must preserve the original theme class on the same native DOM tag');
const cardOwner=primitives.slice(primitives.indexOf('export function UiCard('),
 primitives.indexOf('export function UiAction('));
assert.ok(!cardOwner.includes('style:'),'UiCard cannot introduce new inline presentation');
assert.ok((css.match(/!important/g)||[]).length <= uiStyleBudget.cssPriorityDeclarations,'No additional CSS priority');
assert.ok(css.length<=uiStyleBudget.cssCharacters,'Stylesheet must not exceed observed migration baseline');
assert.ok(report.appInlineStyleObjects<=uiStyleBudget.appInlineObjects,'No additional App inline presentation');
const diagnostics=app.slice(app.indexOf('function SettingsView('),app.indexOf('function CompareView(',app.indexOf('function SettingsView(')));
for(const role of ['wpb-settings-diagnostic-intro','wpb-settings-diagnostic-run','wpb-settings-diagnostic-full','wpb-settings-diagnostic-progress','wpb-settings-diagnostic-track','wpb-settings-diagnostic-fill','wpb-settings-diagnostic-result','wpb-settings-diagnostic-error'])
 assert.ok(diagnostics.includes(role) && css.includes('.wpb .'+role+'{'),'Diagnostics has one CSS role for '+role);
assert.ok(diagnostics.includes('onClick: () => runDeviceSelfTest("quick")') && diagnostics.includes('onClick: () => runDeviceSelfTest("full")'),'Diagnostics behavior retained');
assert.ok(diagnostics.includes('"data-outcome": selfTest.failures.length ? "fail" : "pass"'),'Result states remain data-driven');
assert.ok(diagnostics.includes('style: { width: `${Math.max(2, Math.min(100,'),'Progress animation retains only computed width');
// B1031: importing a shared program is program management — it lives in Plan (Import) on UiSheet.
const importSlice=fs.readFileSync('modules/program-import-sheet.js','utf8');
assert.ok(importSlice.includes("_jsx(UiSheet, {"),'Import uses the shared modal bottom sheet');
for(const cls of ['wpb-settings-import-input','wpb-settings-import-result','wpb-settings-import-submit'])
 assert.ok(importSlice.includes(cls) && css.includes('.wpb .'+cls+'{'),'Import UI must retain CSS owner '+cls);
assert.ok(!app.slice(app.indexOf('function SettingsView('),app.indexOf('function CompareView(')).includes('setPasteProg'),'Settings no longer hosts program import');
assert.ok(importSlice.includes('onChange: e => { setText(e.target.value); setResult(null); }'),'Native textarea edits clear a stale result');
assert.ok(importSlice.includes('disabled: !text.trim() || busy'),'Import submit must remain disabled for empty text');
assert.ok(importSlice.includes("'data-outcome': result.ok ? 'ok' : 'error'"),'Result success/errors must be visibly stateful');
console.log('PASS UI Card architecture: shared owner and source inventory remain intact.');
