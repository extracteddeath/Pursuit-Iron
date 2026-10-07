import { productionSource } from './production-source.mjs';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import { planOverviewMemo, planOverview, setRestScaleGlobal, EXERCISES, EX_BY_ID, WhatsNewCard } from '../modules/App.js';

const program = { id: 'm219', name: 'Overview cache', custom: true, engineV: 70, weeks: 4,
    config: { goal: 'both', experience: 'intermediate', weeks: 4, split: 'custom', days: 1, session: 's60', deload: false },
    days: [{ id: 'upper', label: 'Upper', primaryIndex: 0, exercises: ['bb-bench', 'inc-curl'] }],
    overrides: { 'upper:0': { sets: 4, reps: '6-8', rir: '2' }, 'upper:1': { sets: 3, reps: '10-15', rir: '2' } } };
const history = [], saved = [program];
setRestScaleGlobal(1);
const first = planOverviewMemo(program, 1, null, history, saved);
assert.equal(planOverviewMemo(program, 1, null, history, saved), first, 'unchanged inputs reuse the expensive overview');
setRestScaleGlobal(1.3);
const long = planOverviewMemo(program, 1, null, history, saved);
assert.notEqual(long, first, 'rest settings invalidate the cache');
assert.ok(long.weeks[0].minutes > first.weeks[0].minutes, 'longer rests increase displayed time');
assert.deepEqual(long, planOverview(program, 1, null, history, saved));
const index = EXERCISES.findIndex(ex => ex.id === 'inc-curl'), original = EXERCISES[index];
try {
    const renamed = { ...original, name: 'Renamed custom movement' };
    EXERCISES[index] = renamed; EX_BY_ID[original.id] = renamed;
    const edited = planOverviewMemo(program, 1, null, history, saved);
    assert.notEqual(edited, long, 'same-count catalog replacement invalidates the cache');
    assert.ok(edited.lifts.some(lift => lift.name === renamed.name));
    const logged = [{ programId: program.id, dayId: 'upper', weekIndex: 1 }];
    const completed = planOverviewMemo(program, 1, null, logged, saved);
    assert.equal(completed.weeks[0].done, 1, 'new history updates completion');
    assert.equal(planOverviewMemo(program, 2, null, logged, saved).currentWeek, 2);
} finally { EXERCISES[index] = original; EX_BY_ID[original.id] = original; setRestScaleGlobal(1); }

function inspect(node) {
    if (!node || typeof node !== 'object') return;
    if (Array.isArray(node)) { node.forEach(inspect); return; }
    if ('type' in node && 'props' in node) {
        assert.ok(node.type != null, 'release notes cannot contain an invalid icon element');
        inspect(node.props.children);
    }
}
inspect(WhatsNewCard({}));
const source = productionSource();
const scope = vm.createContext({});
vm.runInContext(source.slice(source.indexOf('const _fillLum ='), source.indexOf('/* ═══ TYPE TOKENS')) + `
globalThis.measure = colors => Object.entries(THEMES).flatMap(([theme, t]) => {
    Object.assign(C, t.palette);
    const surfaces = [C.bg, C.bg2, C.card, C.cardHi].map(_hx2rgb);
    return colors.map(color => {
        const ink = catInk(color);
        const grounds = surfaces.concat(surfaces.map(g => _blend(color + '22', g)), surfaces.map(g => _blend(ink + '22', g)));
        return { theme, color, ratio: _worst(_hx2rgb(ink), grounds) };
    });
});`, scope);
// All literal six-digit colors in App includes every level, phase, effort and pattern color.
const colors = [...new Set([...source.matchAll(/["'](#[0-9a-fA-F]{6})["']/g)].map(m => m[1]))];
const ratios = scope.measure(colors);
assert.ok(ratios.every(x => x.ratio >= 4.5), JSON.stringify(ratios.filter(x => x.ratio < 4.5).slice(0, 5)));

let contexts = 0, closed = 0, started = 0, disconnected = 0, resumed = 0;
const oscillators = [];
class AudioContextMock {
    constructor() { contexts++; this.state = 'suspended'; this.currentTime = 0; this.destination = {}; }
    resume() { resumed++; this.state = 'running'; return Promise.resolve(); }
    close() { closed++; this.state = 'closed'; return Promise.resolve(); }
    createOscillator() {
        const o = { frequency: { setValueAtTime() {} }, connect() {}, start() { started++; }, stop() {}, disconnect() { disconnected++; } };
        oscillators.push(o); return o;
    }
    createGain() { return { gain: { setValueAtTime() {}, exponentialRampToValueAtTime() {} }, connect() {}, disconnect() { disconnected++; } }; }
}
const audio = vm.createContext({ window: { AudioContext: AudioContextMock } });
vm.runInContext(source.slice(source.indexOf('let _beepCtx = null;'), source.indexOf('const fmtTime =')) + '\nglobalThis.audio = { warm: beepContext, unlock: unlockBeep, beep, release: releaseBeepContext };', audio);
audio.audio.warm(); audio.audio.unlock(); audio.audio.beep(); audio.audio.beep();
assert.equal(contexts, 1); assert.equal(started, 2); assert.equal(resumed, 1);
oscillators.forEach(o => o.onended()); assert.equal(disconnected, 4, 'tones release their nodes');
audio.audio.warm().state = 'interrupted'; audio.audio.unlock(); assert.equal(resumed, 2, 'interrupted audio is retried on touch');
audio.audio.release(); audio.audio.release(); assert.equal(closed, 1, 'workout cleanup closes the context once');
audio.audio.warm(); assert.equal(contexts, 2, 'a later workout can get a fresh context'); audio.audio.release();
console.log(`PASS M219: overview cache reuse and rest/catalog/history/week invalidation; valid current-note icons; ${ratios.length} theme/color checks against plain, original and derived tints; audio reuse, interruption unlock, node cleanup and workout exit.`);
