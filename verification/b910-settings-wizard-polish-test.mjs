import assert from 'node:assert/strict';
import fs from 'node:fs';
const css=fs.readFileSync('app.css','utf8');
const app=fs.readFileSync('modules/App.js','utf8');
const block=css.slice(css.lastIndexOf('/* B910 / UI polish pass 04'));
assert.ok(block.startsWith('/* B910 / UI polish pass 04'));
assert.ok(block.includes('height:48px!important;')&&block.includes('min-width:48px!important;'),'48px real creation-header Back target and steady header');
assert.ok(block.includes('piB910QuestionEnter 170ms')&&block.includes('translate:0 4px'),'Short bounded question motion');
assert.ok(!block.includes('translate:0 -')&&!block.includes('opacity:0'),'No spring overshoot or opacity flash');
for(const s of ['.wpb-settings .wpb-segmented>button','.wpb-wizard-option','.wpb-wizard-choice','.wpb-wizard-preset'])
 assert.ok(block.includes(s),'Selection paint styles cover '+s);
assert.ok(block.includes('prefers-reduced-motion:reduce')&&block.includes('forced-colors:active')&&block.includes('transition:none!important'));
assert.ok(app.includes('className: "wpb-wizard-progress"')&&app.includes('className: "wpb-segmented"'),'Underlying progress and settings choice DOM remain');
console.log('PASS B910: stable wizard header, bounded non-flashing question motion and settings selection feedback.');
