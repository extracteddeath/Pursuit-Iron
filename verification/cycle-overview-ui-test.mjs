import { productionSource } from './production-source.mjs';
import assert from 'node:assert/strict';
import fs from 'node:fs';
const app=productionSource();
const rows=app.slice(app.indexOf('function CycleBlockRows('),app.indexOf('function CycleBlockNav('));
assert.ok(rows.includes('children: b.label'),'phase identity comes from block labels, including Peak');
assert.ok(rows.includes('disabled: !p'),'existing future blocks are browsable');
assert.ok(rows.includes('"Preview"'),'planned rows have an explicit preview label');
assert.ok(!rows.includes('b.note'),'internal conversion provenance is not shown');
const detail=app.slice(app.indexOf('function CycleDetail('),app.indexOf('function BlockReviewView('));
assert.ok(detail.includes('children: `${P.sessions} sessions logged`'));
assert.ok(detail.includes('Cycle options'));
assert.equal((detail.match(/className: "wpb-cycle-actions"/g)||[]).length,1);
assert.ok(!detail.includes('wpb-cycle-goal-arc'),'block list provides the phase path once');
assert.ok(!rows.includes('style: {'),'Block row presentation has one CSS owner');
assert.ok(fs.readFileSync('app.css','utf8').match(/#root \.wpb-cycle-block-row\{[^}]*opacity:1;/),'All block states remain fully readable');
const explanation=app.slice(app.indexOf('function CycleDoseTransition('),app.indexOf('function CycleBlockPreview('));
assert.ok(explanation.includes('change.regions.map'),'region breakdown derives from canonical saved transition');
assert.ok(explanation.includes('previousWeeklySets')&&explanation.includes('currentWeeklySets'),'actual before/after volume visible');
assert.ok(explanation.includes('different')||explanation.includes('not the same'),'retained target coverage cannot masquerade as actual set retention');
assert.ok(detail.includes('hasDoseExplanation = !!(next?.nextEngine?.cycleTemplate?.splitDecision?.workingWeekVolumeChange') &&
    detail.includes('describeRegionalWorkingWeekChange(current, next, EXERCISES)'),
    'Up next refreshes the existing phase-transition explanation from the canonical saved executable-week projection');
assert.ok(detail.includes("_jsx(CycleDoseTransition, { change: doseChange || originalDoseChange, source: doseChange ? 'saved' : 'generation' })"),
    'Up next prefers canonical live dose, but attributes the saved generation fallback explicitly');
const preview=app.slice(app.indexOf('function CycleBlockPreview('),app.indexOf('function CycleDetail('));
assert.ok(preview.includes('CycleDoseTransition'),'future block previews share the same explanation');
assert.ok(preview.includes('previousProgram = null') &&
    preview.includes('describeRegionalWorkingWeekChange(previousProgram, program, EXERCISES)') &&
    preview.includes("_jsx(CycleDoseTransition, { change: doseChange || originalChange, source: doseChange ? 'saved' : 'generation' })"),
    'Block Preview resolves its selected phase boundary from existing saved programs, not cached generation metadata');
assert.ok(app.includes('const owner = cycles.find(c => c.blockIds?.includes(p?.id))') &&
    app.includes('previousProgram, initialWeek: cycleBlockPreview.initialWeek'),
    'Preview receives the adjacent saved block; missing boundaries do not fabricate dose comparisons');
assert.ok(explanation.includes("'Saved prescriptions' : 'Original generation snapshot · may differ after edits'"),
    'The dose explanation distinguishes current saved prescriptions from the historical generation fallback');

assert.ok(preview.includes('isFuturePreview')&&preview.includes('Read-only outlook')&&preview.includes('may adapt')&&
    preview.includes('Saved block')&&preview.includes('Read-only prescriptions'),
    'Future outlook remains explicitly provisional and is never mislabeled as the activated saved block');
assert.ok(preview.indexOf('className: "wpb-cycle-preview-week"') < preview.indexOf('className: "wpb-cycle-preview-workouts"'),
    'Selected week and compact block facts precede the executable sessions');
assert.ok(preview.indexOf('program.days.map(') < preview.indexOf('_jsx(CycleWeekOutlook'),
    'Saved workout prescriptions take priority over generation-time quality estimates');
assert.ok(preview.indexOf('program.days.map(') < preview.indexOf('splitDecision.reason'),
    'Real exercises precede the rendered optional split rationale');
assert.ok(preview.includes('_jsxs("details", { className: "wpb-cycle-preview-analysis"')&&
    preview.includes('wpb-cycle-preview-analysis-summary')&&!preview.includes('hasAnalysis &&'),
    'Saved week dose is available under one native disclosure, even without a generation report');
assert.ok(explanation.includes('const current = Array.isArray(report?.weeks) ?')&&
    explanation.includes('const workingSets = (program.days || []).reduce(')&&
    explanation.includes('hasCoverage && _jsxs("div"')&&
    explanation.includes('No original training-quality estimate is available')&&
    explanation.includes('Recovery week. Working sets are counted from saved prescriptions'),
    'Generated quality is optional, while actual saved week and deload doses stay visible');
assert.ok(preview.includes('open: initialDayId ? day.id === initialDayId : index === 0'),
    'Selected or first workout stays open at preview entry');
assert.ok(preview.includes('computeCell(program, day, id, slot, week)'),'Browsing reads the existing saved executable prescription owner');
const css=fs.readFileSync('app.css','utf8');
assert.ok(css.includes('#root .wpb-cycle-dose-row')&&css.includes('grid-template-columns:minmax(0,.85fr) minmax(0,1.7fr)'),
    'volume rows have a responsive token-owned layout');
assert.ok(!explanation.includes('style: {'),'dose panel has no inline styling');
console.log('PASS M306 Cycle dose UI: actual weekly and regional changes use one read-only disclosed component in Up next and preview.');

assert.ok(explanation.includes('focusedVolumeReductions')&&explanation.includes('Review focus'),
 'Executable priority-dose reductions are visible even before expanding the cycle breakdown');
assert.ok(explanation.includes('not an automatic instruction to add sets'),
 'An explicit focus warning does not prescribe unsafe volume');
console.log('PASS M307 Cycle UI: a large prioritized-volume reduction receives a contextual, non-prescriptive disclosure.');

