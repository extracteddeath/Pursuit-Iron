import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const read = path => readFileSync(new URL('../' + path, import.meta.url), 'utf8');
const app = read('modules/App.js');
const machine = read('modules/workout-session-controls.js');
const css = read('app.css');
const machineCss = read('modules/workout-machine-switch.css');

// A final-set technique is NOT a Target-column datum. It belongs to the set
// even when Last is selected, and must survive completion of the logged set.
const row = app.indexOf('si === lastWorkRow && techTag && (_jsxs("details"');
assert.ok(row > 0, 'Final working set has a dedicated disclosure');
const end = app.indexOf('Only the latest unanswered set or a deliberately reopened set', row);
assert.ok(end > row, 'Disclosure is in the working-set row before effort logging');
const detail = app.slice(row, end);
assert.match(detail, /data-last-set-technique/);
assert.match(detail, /Lengthened partials/);
assert.match(detail, /techExplain\(cell\.tech\)/);
assert.doesNotMatch(detail, /!s\.done|targetMode\s*===/);
assert.doesNotMatch(app, /si === lastWorkRow && !s\.done && techTag/);
assert.match(app, /const lastWorkRow = \(\(\) => \{/);
assert.match(app, /!e\.sets\[j\]\.warm && !e\.sets\[j\]\.sub/);
assert.match(machineCss, /wpb-last-set-technique>summary/);
assert.match(machineCss, /wpb-last-set-technique-help/);
assert.match(app, /className: "wpb-set-tech-tag"/);

// Machine A/B and exercise Swap live in Exercise Info near the increment
// settings; the compact workout action rail must not clip machine names.
assert.match(app,/className: "wpb-exinfo-machine-tools"/);
assert.match(app,/isMachineLike\(ex\.equip\) && _jsx\(WorkoutMachineVariantControl/);
assert.match(app,/onAssignLegacy:id=>attachOldMachineHistoryNow\(ei,id\)/);
assert.match(app,/wpb-exinfo-swap-action/);
const toolbar = app.slice(app.indexOf('className: "wpb-hscroll wpb-workout-tools'),
    app.indexOf('className: "wpb-set-head"'));
assert.doesNotMatch(toolbar,/WorkoutMachineVariantControl|\{ k: "swap"/);
assert.match(machine,/wpb-machine-switch-trigger/);
assert.match(machine,/onSelect\?\.\(id\)/);
assert.match(machine,/onCreate\(label,makeDefault\)/);
assert.match(machine,/onAssignLegacy\(id\)/);
assert.match(machineCss,/wpb-exinfo-machine-tools/);
assert.match(machineCss,/wpb-machine-switch-add span/);
assert.doesNotMatch(machineCss,/\.wpb-workout-tools>\.wpb-machine-switch/);
console.log('PASS techniques remain on logged set rows; Machine A/B and Swap live in Exercise Info with accessible full-width controls');
