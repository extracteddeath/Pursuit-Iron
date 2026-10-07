import { productionSource } from './production-source.mjs';
import fs from 'node:fs';

const app = productionSource();
const fail = msg => { console.error('CYCLE OVERVIEW UI TEST FAILED: ' + msg); process.exit(1); };

if (!app.includes('if (phase === "peak") return "Peak";')) fail('peak phase is not explicitly labeled Peak');
if (!app.includes('children: cyclePhaseLabel(b)')) fail('cycle path still derives display text from block goal');
if (app.includes('_jsx(CycleTimeline, { progress: P }), _jsxs("div", { className: "wpb-cycle-stats"')) fail('detail overview still renders a second unlabeled progress timeline');
if (!app.includes('children: [days ? stat("days/wk", days) : null, stat("sessions logged", totalSessions)]')) fail('overview stats are still redundant or sessions are ambiguously labeled');
if (!app.includes('content:"Cycle path"')) fail('phase sequence is missing its label');
console.log('Cycle overview UI integrity OK.');
