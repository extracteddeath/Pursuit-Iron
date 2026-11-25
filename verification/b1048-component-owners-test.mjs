import assert from 'node:assert/strict';
import fs from 'node:fs';
// B1048/B1049: each consolidated component has exactly one owner block. No other base rule (outside
// media/interaction states the owner deliberately leaves alone) may target it again.
const css = fs.readFileSync('app.css', 'utf8');
const app = fs.readFileSync('modules/App.js', 'utf8');
const style = app.slice(app.indexOf('return (_jsx("style", { children: `'), app.indexOf('` }));', app.indexOf('return (_jsx("style", { children: `')));
const glance = css.indexOf('/* B1048 — Progress glance'), nav = css.indexOf('/* B1049 — Navigation bar');
assert.ok(glance > 0 && nav > glance, 'Both owners exist');
const before = css.slice(0, glance);
assert.ok(!/wpb-progress-glance|data-progress-glance/.test(before), 'Progress glance has no rules outside its owner');
assert.ok(!/wpb-progress-glance|data-progress-glance/.test(style), 'Progress glance has no StyleTag rules');
assert.ok(css.slice(glance, nav).includes('.wpb-progress-glance-item:first-child{background:var(--pi-r11-hero);border-color:var(--pi-r11-accent-stroke);}'), 'The featured first tile keeps its hero fill');
// The 840px rail layout (:has), interaction states and accessibility media still reference the bar;
// plain base rules in the StyleTag are gone.
assert.ok(!/\n\.wpb-tabbar(\s|\{)|\n\.wpb-tabbar \.wpb-tab\{/.test(style), 'No StyleTag base rules for the nav bar');
assert.ok(css.includes('#root[data-pi-motion] .wpb .wpb-tabbar .wpb-tab{border-radius:16px!important;}'), 'Tabs keep their shape against the press morph');
console.log('PASS B1048/B1049: Progress glance and navigation bar each have one owner.');
