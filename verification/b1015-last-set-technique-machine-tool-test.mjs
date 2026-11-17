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

// The machine-choice action is now a peer of Swap in the horizontal tools,
// not an extra exercise-heading row. It still invokes the same runtime owner,
// saved-gym machine catalog and focused chooser.
assert.match(app, /\{ k: "machine", element: _jsx\(WorkoutMachineVariantControl/);
const meta = app.indexOf('className: "wpb-exercise-meta"');
const tools = app.indexOf('className: "wpb-hscroll wpb-workout-tools', meta);
assert.ok(tools > meta);
assert.doesNotMatch(app.slice(meta, tools), /WorkoutMachineVariantControl/);
assert.match(machine, /wpb-machine-switch-trigger wpb-workout-tool/);
assert.match(machine, /onSelect\?\.\(id\)/);
assert.match(machine, /onCreate\(label,makeDefault\)/);
assert.match(machineCss, /\.wpb-workout-tools>\.wpb-machine-switch/);
assert.doesNotMatch(machineCss, /grid-row:4/);
console.log('PASS final-set technique stays with logged row in Target/Last; Machine A/B stays accessible but secondary');
