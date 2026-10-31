import assert from 'node:assert/strict';
import fs from 'node:fs';
import { report } from '../scripts/audit-ui-style-ownership.mjs';

const app=fs.readFileSync('modules/App.js','utf8');
const primitives=fs.readFileSync('modules/ui-primitives.js','utf8');
const css=fs.readFileSync('app.css','utf8');
assert.equal(report.unownedStandardCardCallSites,0,'Standard cards must render through UiCard');
assert.equal(report.shared.Card.appCalls,23,'Card adoption must cover all 18 audited call sites');
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
assert.equal((css.match(/!important/g)||[]).length,2024,'No additional CSS priority');
assert.ok(css.length<=652680,'Stylesheet must not exceed observed migration baseline');
assert.ok(report.appInlineStyleObjects<=831,'No additional App inline presentation');
const diagnostics=app.slice(app.indexOf('function SettingsView('),app.indexOf('function CompareView(',app.indexOf('function SettingsView(')));
for(const role of ['wpb-settings-diagnostic-intro','wpb-settings-diagnostic-run','wpb-settings-diagnostic-full','wpb-settings-diagnostic-progress','wpb-settings-diagnostic-track','wpb-settings-diagnostic-fill','wpb-settings-diagnostic-result','wpb-settings-diagnostic-error'])
 assert.ok(diagnostics.includes(role) && css.includes('.wpb .'+role+'{'),'Diagnostics has one CSS role for '+role);
assert.ok(diagnostics.includes('onClick: () => runDeviceSelfTest("quick")') && diagnostics.includes('onClick: () => runDeviceSelfTest("full")'),'Diagnostics behavior retained');
assert.ok(diagnostics.includes('"data-outcome": selfTest.failures.length ? "fail" : "pass"'),'Result states remain data-driven');
assert.ok(diagnostics.includes('style: { width: `${Math.max(2, Math.min(100,'),'Progress animation retains only computed width');
const importSlice=app.slice(app.indexOf('function SettingsView('),app.indexOf('function CompareView(',app.indexOf('function SettingsView(')));
for(const cls of ['wpb-settings-import-sheet','wpb-settings-import-input','wpb-settings-import-result','wpb-settings-import-submit'])
 assert.ok(importSlice.includes(cls) && css.includes('.wpb .'+cls+'{'),'Import UI must retain CSS owner '+cls);
assert.ok(importSlice.includes('ref: sheetDragRef, onClick: e => e.stopPropagation()'),'Import drag and backdrop propagation must survive');
assert.ok(importSlice.includes('onChange: e => setPasteProg(p => ({ ...p, text: e.target.value, result: null }))'),'Native textarea edits must keep existing state writer');
assert.ok(importSlice.includes('disabled: !pasteProg.text.trim()'),'Import submit must remain disabled for empty text');
assert.ok(importSlice.includes('"data-outcome": pasteProg.result.ok ? "ok" : "error"'),'Result success/errors must be visibly stateful');
console.log('PASS UI Card architecture: shared owner and source inventory remain intact.');
