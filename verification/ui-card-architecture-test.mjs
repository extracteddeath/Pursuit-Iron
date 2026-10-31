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
assert.ok(css.length<=647928,'Stylesheet must not exceed observed migration baseline');
assert.ok(report.appInlineStyleObjects<=845,'No additional App inline presentation');
console.log('PASS UI Card architecture: shared owner and source inventory remain intact.');
