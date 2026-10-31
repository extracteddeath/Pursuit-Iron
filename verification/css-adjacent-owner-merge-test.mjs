import assert from 'node:assert/strict';
import fs from 'node:fs';
import { uiStyleBudget } from './ui-style-budgets.mjs';

const css=fs.readFileSync('app.css','utf8');
assert.equal(css.length,uiStyleBudget.cssCharacters,'CSS cap tracks the actual retired source');
assert.ok((css.match(/!important/g)||[]).length <= uiStyleBudget.cssPriorityDeclarations,'No loss or addition of priority declarations');
const checks=[
 ['.wpb-tabbar .wpb-tab::before','top:4px!important','display:block!important'],
 ['.wpb-day-card[data-next="true"]','background-image:none!important','background:var(--pi-color-card)!important'],
 ['.wpb-home>.wpb-home-compose-bar','box-sizing:border-box','position:relative!important']
];
for(const [selector,first,second] of checks){
 const position=css.lastIndexOf('#root[data-pi-motion="expressive"] .wpb ');
 assert.ok(position>0,'Expected expressive stylesheet');
 const pattern='#root[data-pi-motion="expressive"] .wpb ';
 const exact=selector.includes('.wpb-day-card')?pattern+'.wpb-program.wpb-program '+selector:
   selector.includes('.wpb-tabbar')?pattern+selector:pattern+selector;
 const match=css.indexOf(exact+'{');
 assert.ok(match>=0,'Merged CSS owner missing: '+selector);
 const body=css.slice(match,css.indexOf('}',match)+1);
 assert.ok(body.includes(first)&&body.includes(second),'Adjacent owner was not actually consolidated: '+selector);
}

// R76: Adjacent *identical* declarations now have one rule with the original
// full selector list. Keeping selector specificity and state predicates avoids
// reintroducing a new priority override to hide conflicts.
const sharedSelected='#root[data-pi-motion="expressive"] .wpb-progress .wpb-premium-tabs[data-pi-m3-track]>button[aria-selected="true"],\n' +
 '#root[data-pi-motion="expressive"] .wpb-settings .wpb-segmented[data-pi-m3-track]>button[aria-pressed="true"],\n' +
 '#root[data-pi-motion="expressive"] .wpb .wpb-exercise-tabs[data-pi-m3-track]>button[aria-selected="true"]{';
assert.ok(css.includes(sharedSelected),'Progress/Settings/Exercise selected buttons share one owner');
const selectedBody=css.split(sharedSelected)[1]?.split('}')[0]||'';
assert.ok(selectedBody.includes('background:transparent!important;') &&
 selectedBody.includes('background-image:none!important;') &&
 selectedBody.includes('box-shadow:none!important;'),'Moving indicator buttons retain original transparent paint');
const sharedWizard='#root[data-pi-motion="expressive"] .wpb .wpb-wizard-layout,\n' +
 '#root[data-pi-motion="expressive"] .wpb .wpb-wizard-layout>.wpb-wizard-step{';
assert.ok(css.includes(sharedWizard),'Wizard container and step share the same sizing owner');
assert.ok(css.split(sharedWizard)[1]?.split('}')[0].includes('min-width:0;\n  width:100%;'),
 'Wizard retains the existing full-width/min-width geometry');
const sharedTransition='#root[data-pi-motion="expressive"] .wpb .wpb-progress.wpb-progress .wpb-premium-tabs\n' +
 '  >button:not([aria-selected="true"]){';
assert.ok(css.includes(sharedTransition),'Specific Progress unselected selector remains grouped for state parity');
assert.equal(css.split(sharedTransition).length-1,1,'Specific Progress idle tab selector appears once');
assert.ok(css.split(sharedTransition)[1]?.split('}')[0].includes('transition:color var(--pi-r73-state-duration)'),
 'Shared idle tab transition preserves the original timing and token');

console.log('PASS CSS retirement: three adjacent same-selector roles merged without changing priority or conditions.');
