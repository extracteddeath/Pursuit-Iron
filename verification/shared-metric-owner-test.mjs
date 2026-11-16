import assert from 'node:assert/strict';
import fs from 'node:fs';
import { report } from '../scripts/audit-ui-style-ownership.mjs';
import { uiStyleBudget } from './ui-style-budgets.mjs';

const app=fs.readFileSync('modules/App.js','utf8');
const primitives=fs.readFileSync('modules/ui-primitives.js','utf8');
const css=fs.readFileSync('app.css','utf8');
const view=(marker)=>{
  const start=app.indexOf('function '+marker+'(');
  const end=app.indexOf('\nfunction ',start+10);
  assert.ok(start>0 && end>start,'Missing screen boundary for '+marker);
  return app.slice(start,end);
};
const plan=view('PlanView'),progress=view('HistoryView'),profile=view('ProfileView');
const owner=primitives.slice(primitives.indexOf('export function UiMetric('),
  primitives.indexOf('export function UiAction('));
assert.ok(owner.startsWith('export function UiMetric('),'One canonical metric owner');
assert.ok(owner.includes('as: Element = "div"') &&
  owner.includes('labelFirst = false') && owner.includes('context = null'),
  'Metric supports original DOM element and visible reading order');
assert.ok(owner.includes('labelFirst ? [labelNode, valueNode] : [valueNode, labelNode]') &&
  owner.includes('if (context != null)') &&
  owner.includes('...valueProps') && owner.includes('...labelProps') &&
  owner.includes('...contextProps'),'Metric preserves feature data and order');
assert.ok(!owner.includes('style:') && !owner.includes('!important'),
  'Shared metric markup cannot introduce inline presentation or priority');
assert.ok(app.includes('UiCard, UiMetric, UiAction') &&
  report.shared.Metric.ownerExported && report.shared.Metric.appCalls===3,
  'Three actual cross-screen consumers use the exported component');
for(const [name,source] of [['Plan',plan],['Progress',progress],['Profile',profile]])
  assert.equal((source.match(/_jsx\(UiMetric,/g)||[]).length,1,name+' owns one shared metric template');
assert.ok(plan.includes('const tile = (label, value, accent) => _jsx(UiMetric,') &&
  plan.includes('labelFirst: true, label, value') &&
  plan.includes('className: "wpb-plan-glance-tile"') &&
  plan.includes('"data-featured": accent ? "true" : undefined'),
  'Plan metrics keep label-before-value and featured state');
for(const role of ['wpb-plan-glance-label','wpb-plan-glance-value'])
  assert.ok(plan.includes(role)&&css.includes('.'+role),'Plan typography unchanged: '+role);
assert.ok(progress.includes('className: "wpb-progress-glance-item"') &&
  progress.includes('valueProps: { "data-has-value": Boolean(value) }') &&
  progress.includes('context: sub') &&
  progress.includes('contextClassName: "wpb-progress-glance-context"'),
  'Progress keeps zero-value cue and relative time captions');
for(const role of ['wpb-progress-glance-value','wpb-progress-glance-label','wpb-progress-glance-context'])
  assert.ok(progress.includes(role)&&css.includes('.'+role),'Progress typography unchanged: '+role);
assert.ok(profile.includes('className: "wpb-profile-stat"') &&
  profile.includes('valueProps: { "data-has-unit": !!sub }') &&
  profile.includes('className: "wpb-profile-stat-number", children: val') &&
  profile.includes('className: "wpb-profile-stat-unit", children: sub') &&
  profile.includes('labelClassName: "wpb-profile-stat-label"'),
  'Profile keeps nested number/unit DOM and label position');
for(const role of ['wpb-profile-stat-value','wpb-profile-stat-label','wpb-profile-stat-unit'])
  assert.ok(profile.includes(role)&&css.includes('.'+role),'Profile typography unchanged: '+role);
assert.ok(report.screens.Plan.sharedMetricCalls===1 &&
  report.screens.Progress.sharedMetricCalls===1 &&
  report.screens.Profile.sharedMetricCalls===1,'Metric calls attributable to each destination');
assert.equal(report.appInlineStyleObjects,801,'Metric migration cannot add inline styles');
assert.equal(css.length,uiStyleBudget.cssCharacters,'Metric uses existing CSS only');
assert.ok((css.match(/!important/g)||[]).length<=uiStyleBudget.cssPriorityDeclarations,
  'Metric migration cannot add priority debt');
console.log('PASS shared metric ownership: Plan, Progress and Profile reuse one DOM anatomy.');
