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
assert.match(css,/\.wpb-cycles \.wpb-cycle-block-row>span:nth-child\(2\)/);
const order = ['a','b'];
const newer = ['c','a','b'];
const catalog = [{id:'b'},{id:'x'},{id:'a'},{id:'c'}];
const arranged = [...catalog].sort((a,b)=>{
    const ai=newer.indexOf(a.id),bi=newer.indexOf(b.id);
    return ai<0&&bi<0?0:ai<0?1:bi<0?-1:ai-bi;
}).map(x=>x.id);
assert.deepEqual(arranged,['c','a','b','x']);
assert.deepEqual(order, ['a','b']);

const b911=css.slice(css.lastIndexOf('/* B911 / UI polish pass 05'),css.indexOf('/* B912 — Prototype-informed Profile hierarchy'));
assert.ok(b911.startsWith('/* B911 / UI polish pass 05'),'polish styles are present');
const chartOwner=css.slice(css.indexOf('/* B891 —'),css.indexOf('/* B892 /'));
assert.ok(chartOwner.includes('flex:0 0 var(--pi-control-touch);width:var(--pi-control-touch);min-width:var(--pi-control-touch);height:var(--pi-control-touch);min-height:var(--pi-control-touch)'), '48px controls have one geometry owner');
assert.ok(chartOwner.includes('min-height:var(--pi-control-touch)'), 'add action has same 48px geometry owner');
assert.ok(!b911.includes('min-height:48px;')&&!b911.includes('min-width:48px;'),'later polish no longer duplicates touch geometry');
assert.ok(css.includes('--pi-shape-card:20px;')&&css.includes('--pi-shape-action:14px;')&&css.includes('--pi-motion-paint:160ms;'),'cross-screen shape and motion vocabulary is centralized');
assert.match(b911,/\.wpb-chart-favorite-name\{[\s\S]*?overflow-wrap:anywhere/,'long chart labels wrap');
assert.ok(b911.includes(':focus-visible')&&b911.includes('prefers-reduced-motion:reduce')&&b911.includes('forced-colors:active'));
assert.ok(!/transition:[^;]*(width|height|margin|padding|transform)/.test(b911),'no geometry-affecting control motion');

console.log('PASS B891: favorite chart persistence/ordering, accessible move controls and responsive history CSS.');
