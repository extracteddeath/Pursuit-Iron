import assert from 'node:assert/strict';
import fs from 'node:fs';
import { staticModuleGraph, withPreloads, PRELOAD_START } from '../scripts/module-graph.mjs';
// B1035: startup and consistency.
const index = fs.readFileSync('index.html', 'utf8');
const app = fs.readFileSync('modules/App.js', 'utf8');
const css = fs.readFileSync('app.css', 'utf8');

// 1. Every statically imported module is preloaded, so the 14-level import chain is fetched in
//    parallel rather than one level per round trip. The list is generated, never hand-edited.
const graph = staticModuleGraph(index);
assert.ok(graph.length > 100 && graph[0] === 'modules/main.js', 'graph starts at main.js and covers the app');
assert.equal(withPreloads(index), index, 'modulepreload list matches the current import graph');
for (const file of graph) assert.ok(index.includes(`<link rel="modulepreload" href="./${file}">`), 'preloaded: ' + file);
assert.ok(index.indexOf(PRELOAD_START) > index.indexOf('<script type="importmap">'), 'preloads follow the import map');
assert.ok(index.indexOf(PRELOAD_START) < index.indexOf('<script type="module" src="./modules/main.js">'), 'preloads precede the entry');

// 2. Theme palettes derive on first read, not for all themes at module load.
assert.ok(!app.includes('palette: TOK('), 'No theme palette is derived eagerly');
assert.ok(app.includes('Object.defineProperty(theme, "palette", { enumerable: true, get: () => palette || (palette = TOK(source)) });'),
    'Palettes are memoized getters');
assert.ok(fs.readFileSync('modules/gym-machine-variants.js', 'utf8').includes('machineExercises().map('),
    'Machine list is sorted on first use');

// 3. One week shape: Home's strip, Plan's weeks and the streak run Monday-Sunday, and so do the
//    Progress calendar and the reminder-day chips.
assert.ok(app.includes('const DOW = [["Mon", "Monday"],') && app.includes('["Sun", "Sunday"]];'), 'Calendar headings are Monday-first');
assert.ok(app.includes('const startPad = (first.getDay() + 6) % 7; // 0=Mon'), 'Calendar grid is Monday-first');

// 4. Home's hero carries the workout, not an unlabeled "02 / 2" rotation dial, and the exercise
//    count appears once (on the session outline).
assert.ok(!app.includes('wpb-m3-day-orbit') && !css.includes('wpb-m3-day-orbit') && !css.includes('piOrbitTrace'), 'No rotation dial');
assert.ok(!/" min", _jsx\("span", \{ className: "wpb-type-faint", children: "\\u00B7" \}\), upNext\.day\.exercises\.length/.test(app),
    'Hero meta does not repeat the outline exercise count');

// 5. Plan's bleeding week rail keeps its first chip on the page gutter.
assert.ok(css.includes('#root .wpb .wpb-plan-view>.wpb-plan-week-picker{top:calc(-1*var(--pi-space-3));margin-inline:calc(-1*var(--pi-page-inset));padding-inline:calc(var(--pi-page-inset) + 4px)'),
    'Week rail padding outranks the shared choice-rail padding');
console.log('PASS B1035: parallel module preload, lazy palettes, Monday-first weeks, focused Home hero, aligned week rail.');
