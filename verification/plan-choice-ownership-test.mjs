import assert from 'node:assert/strict';
import fs from 'node:fs';
const app=fs.readFileSync('modules/App.js','utf8'),css=fs.readFileSync('app.css','utf8');
const plan=app.slice(app.indexOf('function PlanView('),app.indexOf('function NextWorkoutView('));
assert.ok(plan.includes('className: "wpb-plan-pane-tab"')&&plan.includes('kind: "tab"'),'semantic Plan tab choice');
assert.ok(plan.includes('className: "wpb-plan-week-chip"')&&plan.includes('onClick: () => pickWeek(w.week)'),'week behavior');
assert.ok(plan.includes('"aria-label": `Week ${w.week}`'),'accessible week label');
const choices=plan.slice(plan.indexOf('className: "wpb-plan-panes"'),plan.indexOf('pane === "schedule" && (() =>',plan.indexOf('className: "wpb-plan-panes"')));
assert.ok(choices.length>1000&&!choices.includes('style: {'),'no per-choice static styling');
for(const selector of [
  '#root[data-pi-motion="expressive"] .wpb .wpb-plan-panes>.wpb-plan-pane-tab{',
  '.wpb-plan-pane-label{', '.wpb-plan-week-picker{',
  '#root .wpb .wpb-ui-choice[data-choice-kind="summary"]{',
  '.wpb-ui-choice-label{', '.wpb-ui-choice-caption{'
]) assert.equal(css.split('\n'+selector).length-1,1,'one presentation rule: '+selector);
assert.ok(plan.includes('kind: "summary"'), 'Plan weeks reuse the shared labelled choice');
const blockNav=app.slice(app.indexOf('function CycleBlockNav('), app.indexOf('function CycleBlockPreview('));
assert.ok(blockNav.includes('kind: "summary"')&&!blockNav.includes('style: {'), 'cycle navigation reuses the same choice');
assert.ok(!app.includes('function WeekPill('), 'no private competing week renderer');
console.log('PASS selector ownership: shared labelled choice, existing pane and week navigation semantics.');


// M355: the focused/reduced-muscle explanation belongs to the selected Program week.
// Previously exercise identity used activeWeek, but sets/volume and copy used the final
// training week; alternating exercises and mid-block volume then disagreed with the UI.
const priorities = app.slice(
  app.indexOf('!manual && engHas(program.engineV, "preferenceAware")'),
  app.indexOf('!manual && program.engineSource !== "pursuit-next"')
);
assert.ok(priorities.length > 500, 'muscle-priority panel is still present');
assert.ok(priorities.includes('const week = activeWeek, volume = wkVol, direct = {};'),
  'priority set estimates must use the selected Program week');
assert.ok(priorities.includes('exerciseAt(program, day, slot, activeWeek)'),
  'alternating exercise choice uses the selected week');
assert.ok(priorities.includes('computeCell(program, day, id, slot, week)'),
  'direct-set eligibility uses the same week as identity and volume');
assert.ok(priorities.includes('"Estimated sets in week ", week'),
  'visible dose provenance names the same selected week');
console.log('PASS M355: Program muscle priority dose, direct exercises and week label share selected-week ownership.');

// M358: reuse ProgramView's memoized selected-week volume instead of
// recalculating the same expensive weekly projection for the priority explanation.
const programView = app.slice(app.indexOf('function ProgramView('),app.indexOf('function ShareProgramSheet('));
assert.ok(programView.includes('const wkVol = useMemo(() => weeklyVolume(program, activeWeek), [program, activeWeek]);'),
  'selected-week program volume must have a memoized owner');
assert.equal((programView.split('weeklyVolume(program, ').slice(1)).length,1,
  'Program view must not duplicate selected-week volume work on unrelated renders');
assert.ok(priorities.includes('const week = activeWeek, volume = wkVol, direct = {};'),
  'priority explanation must consume the memoized canonical selected-week volume');
console.log('PASS M358: one selected-week volume computation owner in ProgramView.');
