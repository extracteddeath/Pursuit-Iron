import assert from 'node:assert/strict';
import fs from 'node:fs';

const app = fs.readFileSync(new URL('../modules/App.js', import.meta.url), 'utf8');

assert.ok(!app.includes('Auto applies double-progression / load ramps across the block.'), 'stale one-size-fits-all Auto copy must be removed');
assert.ok(app.includes('Auto · choose per exercise'), 'program creation should explain that Auto selects per exercise');
assert.ok(app.includes('only changes it when your training gives enough evidence'), 'creation copy should promise evidence-based changes rather than method thrashing');
assert.ok(app.includes('Short block — Auto avoids methods that need a longer runway'), 'block-length choice should explain its progression consequence');

assert.match(app, /data-progression-selection/, 'progression view should expose the engine-owned selection summary');
assert.match(app, /program\?\.nextEngine\?\.progressionPlan/, 'UI must read the engine-owned M190 progression plan');
assert.ok(app.includes('Why these methods?'), 'progression view should make the per-exercise rationale available on demand');
assert.match(app, /data-progression-reason/, 'per-exercise progression explanations should have a stable UI hook');
assert.ok(app.includes('One unusual workout is not enough to switch it.'), 'adaptive method changes should explain anti-thrashing behavior');
assert.ok(app.includes('_jsx(ProgressionCard, { program: program, open: showProg'), 'Plan Info should include the progression card');
assert.ok(app.includes('how progression was chosen'), 'Plan Info description should identify progression as part of the reference');

// The UI may contain unrelated legacy metadata elsewhere in this very large compiled bundle. The M191
// contract is stricter and more useful: the new progression explanation surface itself must consume the
// engine-owned decision record without importing policy functions or leaking their implementation terms.
const progressionStart = app.indexOf('const selectedProgressions = Array.isArray(program?.nextEngine?.progressionPlan)');
const progressionEnd = app.indexOf('const rows = useMemo(() => {', progressionStart);
assert.ok(progressionStart >= 0 && progressionEnd > progressionStart, 'progression explanation section should be present and bounded');
const progressionUi = app.slice(progressionStart, progressionEnd);
assert.ok(!/selectProgressionStyle|reselectProgressionStyle/.test(app), 'App UI must not duplicate or rerun engine progression policy');
assert.ok(!/loadability|measurementReliable|noviceLinearEligible/.test(progressionUi), 'progression explanation UI must not expose selector implementation jargon');

for (const label of ['Double progression','Dynamic double progression','Rep ladder','Linear progression','Wave loading','e1RM autoregulation'])
  assert.ok(progressionUi.includes(label), `progression view should have a plain display label for ${label}`);

console.log('PASS M191: creation copy and Plan Info now explain engine-owned per-exercise progression selection without duplicating selector policy or exposing engine jargon.');
