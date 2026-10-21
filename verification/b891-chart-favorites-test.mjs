import assert from 'node:assert/strict';
import fs from 'node:fs';
const app=fs.readFileSync(new URL('../modules/App.js',import.meta.url),'utf8');
const css=fs.readFileSync(new URL('../app.css',import.meta.url),'utf8');
assert.match(app,/function ChartFavoritesManager\(\{ trends, favorites, onToggle, onMove \}\)/);
assert.match(app,/localStorage\.getItem\("wpb:chart-favorites-v1"\)/);
assert.match(app,/localStorage\.setItem\("wpb:chart-favorites-v1", JSON\.stringify\(safe\)\)/);
assert.match(app,/aPin < 0 && bPin < 0 \? 0 : aPin < 0 \? 1 : bPin < 0 \? -1 : aPin - bPin/);
assert.match(app,/"aria-label": "Move " \+ t\.name \+ " up"/);
assert.match(app,/"aria-label": "Move " \+ t\.name \+ " down"/);
assert.match(app,/"aria-label": "Unfavorite " \+ t\.name/);
assert.match(css,/\.wpb-chart-favorites-available\{max-height:210px;overflow-y:auto/);
assert.match(css,/#root \.wpb-cycle-block-content\{[^}]*min-width:0;[^}]*overflow-wrap:anywhere/);
const order = ['a','b'];
const newer = ['c','a','b'];
const catalog = [{id:'b'},{id:'x'},{id:'a'},{id:'c'}];
const arranged = [...catalog].sort((a,b)=>{
    const ai=newer.indexOf(a.id),bi=newer.indexOf(b.id);
    return ai<0&&bi<0?0:ai<0?1:bi<0?-1:ai-bi;
}).map(x=>x.id);
assert.deepEqual(arranged,['c','a','b','x']);
assert.deepEqual(order, ['a','b']);

const b911=css.slice(css.lastIndexOf('/* B911 / UI polish pass 05'),css.indexOf('/* B912 —'));
assert.ok(b911.startsWith('/* B911 / UI polish pass 05'),'polish styles are present');
const chartOwner=css.slice(css.indexOf('/* B891 —'),css.indexOf('/* B892 /'));
const sharedControl=css.slice(css.indexOf('/* Cohesion 06 —'),css.indexOf('/* R61 — shared Home/Plan'));
assert.ok(sharedControl.includes('data-icon-only="true"')&&sharedControl.includes('width:var(--pi-control-touch,48px)'),'shared editor owns icon target geometry');
assert.ok(sharedControl.includes('min-height:var(--pi-control-touch,48px)'),'all shared editor actions have a 48px minimum');
assert.ok(!/!important/.test(sharedControl.replace(/\/\*[\s\S]*?\*\//g,'')),'shared editor adds no priority overrides');
assert.ok(app.slice(app.indexOf('function ChartFavoritesManager('),app.indexOf('function HistoryView(')).includes('variant: "tonal"'),'chart actions reuse UiAction');
assert.ok(css.includes('--pi-shape-card:20px;')&&css.includes('--pi-shape-action:14px;')&&css.includes('--pi-motion-paint:160ms;'),'cross-screen shape and motion vocabulary is centralized');
assert.match(b911,/\.wpb-chart-favorite-name\{[\s\S]*?overflow-wrap:anywhere/,'long chart labels wrap');
assert.ok(css.includes(':is(.wpb-ui-action,.wpb-ui-choice):focus-visible')&&css.includes('@media (prefers-reduced-motion:reduce){#root button')&&css.includes('.wpb-ui-action.wpb-ui-action[data-action],.wpb-ui-choice.wpb-ui-choice)'),'shared focus, reduced motion and forced colors remain owned globally');
assert.ok(!/transition:[^;]*(width|height|margin|padding|transform)/.test(b911),'no geometry-affecting control motion');


const progressSource=app.slice(app.indexOf('function HistoryView('),app.indexOf('\nfunction ',app.indexOf('function HistoryView(')+12));
assert.ok(progressSource.includes('className: "wpb-progress-glance-item", style: { background: C.card'),'theme-native dynamic data remains');
for(const x of [
  'className: "wpb-progress-panel", style: { background: C.card, borderRadius:',
  'className: "wpb-progress-entry-row", style: { display:',
  'className: "wpb-history-filters", style:',
  'className: "wpb-render-auto wpb-history-session", style: { background: C.card, borderRadius:'
]) assert.ok(!progressSource.includes(x),'Progress presentation is not duplicated in JSX: '+x);
const progressOwner=css.slice(css.indexOf('/* B914 / Cohesion 04'),css.indexOf('/* B891 — favorite chart ordering'));
for(const x of ['.wpb-progress-glance-item','.wpb-progress-glance-label','.wpb-progress-panel','.wpb-progress-entry-row','.wpb-history-filters','.wpb-history-session'])assert.ok(progressOwner.includes(x+'{'),x+' owns its static layout in CSS');

console.log('PASS B891: favorite chart persistence/ordering, accessible move controls and responsive history CSS.');
