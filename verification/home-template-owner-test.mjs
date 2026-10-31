import assert from 'node:assert/strict';
import fs from 'node:fs';
import { report } from '../scripts/audit-ui-style-ownership.mjs';
import { uiStyleBudget } from './ui-style-budgets.mjs';
const app=fs.readFileSync('modules/App.js','utf8');
const css=fs.readFileSync('app.css','utf8');
const home=app.slice(app.indexOf('function Home('),app.indexOf('function UiNotice(',app.indexOf('function Home(')));
assert.equal(report.appInlineStyleObjects,805,'Template migration must not regain inline paint');
assert.equal(uiStyleBudget.appInlineObjects,805,'Budget must reflect retired inline styles');
assert.equal((home.match(/style:\s*\{/g)||[]).length,16,'Home template inline owners stay retired');
assert.equal(report.shared.Card.appCalls,26,'Three template actions must use UiCard with native button semantics');
for(const owner of ['wpb-template-card','wpb-template-featured','wpb-template-row','wpb-template-sheet','wpb-template-search-input','wpb-template-filter','wpb-template-gym-choice','wpb-template-filter-clear','wpb-template-browse-link'])
 assert.ok(home.includes(owner)&&css.includes('.'+owner),'Missing shared template owner '+owner);
assert.ok(home.includes('as: "button", type: "button", onClick: () => onTemplate(t)'),
 'Featured template selection retains native button and callback');
assert.ok(home.includes('onClick: () => { setBrowseOpen(false); onTemplate(t); }'),
 'Browser selection still closes then selects');
assert.ok(home.includes('selected: on, onClick: () => toggleFilter(g.facet, ch)') &&
 home.includes('selected: on, onClick: () => onSetGym && onSetGym(g.id)'),
 'Filter and gym selections retain real selected state and handlers');
assert.ok(home.includes('onChange: e => setTplQuery(e.target.value)') && home.includes('"aria-label": "Search program templates"'),
 'Search stays editable and named');
assert.ok(!home.includes('chipStyle(on)') && !home.includes('const chipStyle = (on)'),
 'Browser filter styling must come from semantic state, not computed inline paint');
assert.equal((css.match(/!important/g)||[]).length,2024,'No new priority overrides');
console.log('PASS Home template owner: native actions, selected filters, dynamic callbacks, shared tokens.');
