import { productionSource } from './production-source.mjs';
import fs from 'node:fs';

const app = productionSource();
const css = fs.readFileSync('app.css', 'utf8');
const fail = msg => { console.error('SWAP SCROLL TEST FAILED: ' + msg); process.exit(1); };

const resultAt = app.indexOf('"data-swap-results": true');
if (resultAt < 0) fail('swap results container missing');
const results = app.slice(Math.max(0, resultAt - 130), resultAt + 400);
if (!results.includes('className: "wpb-scroll wpb-swap-results wpb-b976-swap-results"'))
  fail('swap results must retain native scroller and B976 presentation owner');
const selector='#root .wpb-b976-swap-results{';
const at=css.indexOf(selector);
const owner=at>=0 ? css.slice(at+selector.length,css.indexOf('}',at)) : '';
for (const marker of ['flex:1','min-height:0','overflow-y:auto','overscroll-behavior:contain',
  '-webkit-overflow-scrolling:touch','touch-action:pan-y']) {
  if (!owner.includes(marker)) fail('swap results is missing native-scroll CSS marker: '+marker);
}
if (!app.includes('className: "wpb-swap-list wpb-b962-layout-05", "data-swap-list": true')) fail('replacement list lost its single-scroll CSS owner');
if (!css.includes('#root .wpb .wpb-b962-layout-05,#root .wpb-b962-layout-05{margin-top:6px}')) fail('replacement list lost its original 6px spacing');
if (app.includes('_jsxs("div", { className: "wpb-scroll", style: { overflowY: "auto", marginTop: 6 }, children: [filtered.length')) fail('nested vertical scroller returned to replacement list');
console.log('Swap sheet single-scroller integrity OK.');
