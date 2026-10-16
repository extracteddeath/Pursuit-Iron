// Runs the shipped bundle in a bare JS context: no window, DOM, console, timers or localStorage,
// which is what the Android JS runtime provides. Anything browser-only fails here first.
import vm from 'node:vm';
import { readFileSync } from 'node:fs';
const src = readFileSync(new URL('../../android/app/src/main/assets/pi-core.js', import.meta.url), 'utf8');
const ctx = vm.createContext({});
vm.runInContext(src, ctx, { filename: 'pi-core.js' });
const call = (m, a) => { const r = JSON.parse(ctx.PICore.call(m, a ? JSON.stringify(a) : '')); if (!r.ok) throw new Error(m + ': ' + r.error); return r.value; };
let fails = 0; const ok = (c, m) => { console.log((c ? 'PASS ' : 'FAIL ') + m); if (!c) fails++; };
ok(typeof ctx.PICore?.call === 'function', 'bundle defines PICore.call in a bare context');
call('init', {});
const made = call('createProgram', { config: { name: 'Bare', unit: 'lb', goal: 'hypertrophy', experience: 'novice', split: 'full_body', days: 3, session: 's60', equipment: ['dumbbell', 'bench'] }, seed: 3 });
ok(made.ok, 'program generation runs without browser globals');
let s = call('startSession', {});
s.exercises.forEach((e) => e.sets.forEach((st) => { if (!st.warm && !st.sub) { call('act', { type: 'edit', ei: e.ei, si: st.i, field: 'weight', value: '30' }); s = call('act', { type: 'toggle', ei: e.ei, si: st.i }); } }));
ok(s.doneSets === s.totalSets, 'a full session logs in the bare runtime (' + s.doneSets + ' sets)');
const fin = call('finishSession');
ok(fin.ok, 'finish works');
const store = call('exportStore');
ok(JSON.parse(store).history.length === 1, 'export returns the store text');
const t0 = Date.now(); for (let i = 0; i < 5; i++) call('home'); const per = (Date.now() - t0) / 5;
ok(per < 250, 'home() takes ' + per.toFixed(1) + ' ms in node (budget 250)');
console.log(fails ? fails + ' FAILED' : 'ALL PASS'); process.exit(fails ? 1 : 0);
