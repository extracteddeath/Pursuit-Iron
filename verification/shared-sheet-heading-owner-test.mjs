import assert from 'node:assert/strict';
import fs from 'node:fs';

// Program sheet header refactor must preserve the existing DOM tags, title IDs,
// focus/close callbacks and feature CSS. This is anatomy ownership, not a repaint.
const prim = fs.readFileSync('modules/ui-primitives.js','utf8');
const owner = prim.slice(prim.indexOf('export function UiSheetHeading('),
    prim.indexOf('// Stateless close control for Program sheets'));
assert.ok(owner.startsWith('export function UiSheetHeading(') &&
    owner.includes('as: Element = "div"') &&
    owner.includes('names.includes("wpb-ui-sheet-heading")') &&
    owner.includes('return _jsx(Element, { ...props, className: classes });'),
    'The sheet heading must retain native DOM, caller props and single shared class');
assert.ok(!owner.includes('style:') && !owner.includes('!important'),
    'No new inline presentation or priority styling');
const sheets = [
    ['modules/program-settings-sheet.js',1,['wpb-program-settings-heading','program-settings-title','wpb-program-settings-close']],
    ['modules/program-share-sheet.js',1,['wpb-program-share-heading','program-share-title','wpb-program-share-close']],
    ['modules/program-reference-surfaces.js',2,['wpb-progression-choice-heading','wpb-program-reference-heading','program-progression-title','program-reference-title','wpb-progression-choice-close','wpb-program-reference-close']],
    ['modules/program-schedule-sheet.js',1,['wpb-program-schedule-heading','program-weekly-schedule-title','wpb-program-schedule-close']],
    ['modules/program-review-surfaces.js',1,['wpb-save-review-heading','program-save-title','wpb-save-review-close']],
    ['modules/program-exercise-options.js',1,['wpb-exercise-options-heading','program-exercise-options-title','wpb-exercise-options-identity']],
    ['modules/program-training-maxes.js',1,['wpb-training-max-heading','program-training-max-title','wpb-ui-sheet-title']]
];
let count=0;
for(const [path,expected,roles] of sheets){
    const source=fs.readFileSync(path,'utf8');
    assert.ok(source.includes('UiSheetHeading') && source.includes('UiSheetClose'),path+' must retain original dismiss owner');
    const actual=(source.match(/_jsxs\(UiSheetHeading,/g)||[]).length;
    assert.equal(actual,expected,path+' must use the single heading anatomy');
    for(const role of roles)assert.ok(source.includes(role),path+' must retain '+role);
    count+=actual;
}
const share=fs.readFileSync('modules/program-share-sheet.js','utf8');
const reference=fs.readFileSync('modules/program-reference-surfaces.js','utf8');
const exercise=fs.readFileSync('modules/program-exercise-options.js','utf8');
const maxes=fs.readFileSync('modules/program-training-maxes.js','utf8');
assert.ok(share.includes("_jsxs(UiSheetHeading, { as: 'header'") &&
    reference.includes("_jsxs(UiSheetHeading, { as: 'header'") &&
    exercise.includes("_jsxs(UiSheetHeading, { as: 'header'") &&
    maxes.includes("_jsxs(UiSheetHeading, { as: 'header'"),
    'Original semantic header elements stay headers');
for(const path of ['modules/program-settings-sheet.js','modules/program-schedule-sheet.js','modules/program-review-surfaces.js'])
    assert.ok(fs.readFileSync(path,'utf8').includes('_jsxs(UiSheetHeading, { className:'),
        path+' preserves its original div header');
assert.equal(count,8,'Eight distinct Program-sheet headings share one DOM owner');
console.log('PASS shared sheet heading: eight Program headings retain titles, labels and native DOM.');
