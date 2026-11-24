import assert from 'node:assert/strict';
import fs from 'node:fs';
const app=fs.readFileSync('modules/App.js','utf8');
const uiModules=app+'\n'+fs.readFileSync('modules/exercise-picker-sheet.js','utf8');
const css=fs.readFileSync('app.css','utf8');
const owners=[...css.matchAll(/#root \.wpb \.(wpb-b965-layout-\d{2}),#root \.\1\{([^}]+)\}/g)];
// B1028: layout-09 (picker list scroller) retired by UiSheet's body.
// B1029: layout-35 belonged to the removed Settings 1RM calculator.
// B1030: layout-04 was the Plan phase-archive icon tile, removed with the archive.
// B1031: layout-29 belonged to the removed Progress milestones card.
assert.equal(owners.length,41,'B965 must have 41 exact CSS layout owners');
for(const [,name,body] of owners){
 assert.ok(!body.includes('!important'),'Static CSS migration must not add priority owners');
 assert.ok(uiModules.includes(name),'Every retired layout must have a JSX class owner');
 assert.equal((css.match(new RegExp('#root \\.wpb \\.'+name+',','g'))||[]).length,1,'Single CSS source for '+name);
}
assert.ok((app.match(/style: \{/g)||[]).length <= 1384,'Retired 49 inline layout objects cannot return');
assert.ok(app.includes('className: "wpb-hscroll wpb-workout-tools') && app.includes('className: "wpb-focus-values'),'Core workout logger geometry stays class-owned');
assert.ok(app.includes('data-reference-action') && app.includes('toggleDone(ei, si)'),'Keep existing set logging and target reference actions');
console.log('PASS B965: 49 static layout objects use 45 CSS owners, preserving live workout and dynamic styling.');
