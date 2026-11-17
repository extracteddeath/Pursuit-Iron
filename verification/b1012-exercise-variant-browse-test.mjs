import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {exerciseBenchLevel,exerciseAttachmentFacet,
    availableVariantFacets,matchesVariantFacets} from '../modules/exercise-variant-browse.js';

const exercise=(id,name,equip=[])=>({id,name,equip,part:'chest',type:'compound'});
for(const [name,expected] of [
    ['Low-Incline Dumbbell Press','low-incline'],
    ['Incline Dumbbell Press','incline'],
    ['High-Incline Barbell Press','high-incline'],
    ['Decline Bench Press','decline'],
    ['Flat Bench Press','flat'],
    ['Low-to-High Cable Fly',null],
    ['Cable Rope Hammer Curl',null]
])assert.equal(exerciseBenchLevel(exercise('any',name)),expected,name);

const variants=[
    exercise('pushdown','Tricep Pushdown (Bar)',['cable']),
    exercise('rope-pushdown','Rope Pushdown',['cable']),
    exercise('dual-rope-pushdown','Dual-Rope Pushdown',['cable']),
    exercise('v-bar-pushdown','V-Bar Pushdown',['cable']),
    exercise('single-pushdown','Single-Arm Pushdown',['cable']),
    exercise('ez-pushdown','EZ-Bar Pushdown',['cable']),
    exercise('plain','Cable Row',['cable']),
    exercise('db','Dumbbell Curl',['dumbbell'])
];
assert.deepEqual(variants.map(exerciseAttachmentFacet),[
    'straight-bar','rope','dual-rope','v-bar','single-handle',
    'ez-bar',null,null
]);
assert.equal(matchesVariantFacets(variants[2],{attachment:'dual-rope'}),true);
assert.equal(matchesVariantFacets(variants[1],{attachment:'dual-rope'}),false);
assert.equal(matchesVariantFacets(variants[3],{level:'incline'}),false);
const facets=availableVariantFacets([
    exercise('low','Low-Incline Barbell Press',['barbell']),...variants]);
assert.deepEqual(facets.levels,[{id:'low-incline',label:'Low incline'}]);
assert.ok(facets.attachments.some(x=>x.id==='dual-rope'));
assert.ok(facets.attachments.some(x=>x.id==='v-bar'));

const app=readFileSync('modules/App.js','utf8');
const picker=readFileSync('modules/exercise-picker-sheet.js','utf8');
assert.match(picker,/const matches=useMemo\(\(\)=>pool\.filter/);
assert.match(picker,/const visible=matches\.slice\(0,visibleCount\)/);
assert.doesNotMatch(app,/EXERCISES\.filter\(e =>[^\n]+\.slice\(0, 100\)/);
assert.match(picker,/disabled:blocked\.has\(ex\.id\)/);
assert.match(picker,/UiAction/);
assert.match(picker,/UiChoice/);
assert.match(app,/VariantExercisePickerSheet/);
assert.match(app,/const familyAlts = filtered\.filter\(a => familyIds\.has\(a\.id\)\)/);
assert.match(app,/const generalAlts = filtered\.filter\(a => !familyIds\.has\(a\.id\)\)/);
assert.match(app,/candidates: alts, level, onLevel: setLevel/);
assert.match(app,/matchesVariantFacets\(a, \{ level, attachment \}\)/);
console.log('PASS all-exercise pagination and verified pool variant filters; exact setups never transfer machine history');
