import assert from 'node:assert/strict';
import fs from 'node:fs';
import { uiStyleBudget } from './ui-style-budgets.mjs';

const css=fs.readFileSync('app.css','utf8');
assert.equal(css.length,uiStyleBudget.cssCharacters,'CSS cap tracks the actual retired source');
assert.equal((css.match(/!important/g)||[]).length,2024,'No loss or addition of priority declarations');
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
console.log('PASS CSS retirement: three adjacent same-selector roles merged without changing priority or conditions.');
