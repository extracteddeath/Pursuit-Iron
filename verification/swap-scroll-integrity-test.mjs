import { productionSource } from './production-source.mjs';
import fs from 'node:fs';

const app = productionSource();
const fail = msg => { console.error('SWAP SCROLL TEST FAILED: ' + msg); process.exit(1); };

const resultAt = app.indexOf('"data-swap-results": true');
if (resultAt < 0) fail('swap results container missing');
const results = app.slice(Math.max(0, resultAt - 100), resultAt + 420);
for (const marker of ['className: "wpb-scroll wpb-swap-results"','flex: 1','minHeight: 0','overflowY: "auto"','WebkitOverflowScrolling: "touch"','touchAction: "pan-y"']) {
  if (!results.includes(marker)) fail('swap results is missing native-scroll marker: ' + marker);
}
if (!app.includes('className: "wpb-swap-list", "data-swap-list": true, style: { marginTop: 6 }')) fail('replacement list is not a non-scrolling child of the results scroller');
if (app.includes('_jsxs("div", { className: "wpb-scroll", style: { overflowY: "auto", marginTop: 6 }, children: [filtered.length')) fail('nested vertical scroller returned to replacement list');
console.log('Swap sheet single-scroller integrity OK.');
