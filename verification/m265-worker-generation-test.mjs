import assert from 'node:assert/strict';
import fs from 'node:fs';
import { Worker as NodeWorker } from 'node:worker_threads';
import { createGenerationClient, generationSettings } from '../modules/generation-runtime.js';
import { NextShellAdapterError, generateNextProgramForShell, generateNextCycleForShell, convertProgramToNextCycleForShell,
    advanceNextCycleForShell, generateNextBlockFromShellHistory, analyzeShellHistoryForNextEngine, setRestScaleGlobal,
    setLoadInc, setExLoadInc, setAvailPlates, setGymLimits, EXERCISES, EX_BY_ID } from '../modules/engine-api.js';
import { executeGenerationTask } from '../modules/generation-worker.js';
import { config, generationInput, completedHistory, ids } from './m265-generation-fixture.mjs';

class NodeModuleWorker extends EventTarget {
    constructor() {
        super();
        const owner = new URL('../modules/generation-worker.js', import.meta.url).href;
        const code = `import { parentPort } from 'node:worker_threads'; import { generationReply } from ${JSON.stringify(owner)};
            parentPort.on('message', request => parentPort.postMessage(generationReply(request)));`;
        this.thread = new NodeWorker(new URL('data:text/javascript,' + encodeURIComponent(code)));
        this.thread.on('message', data => this.dispatchEvent(new MessageEvent('message', { data })));
        this.thread.on('error', () => this.dispatchEvent(new Event('error')));
    }
    postMessage(payload) { this.thread.postMessage(payload); }
    terminate() { this.thread.terminate(); }
}

// Timestamps legitimately reflect execution time; the complete training artifact must otherwise agree.
const withoutClock = value => {
    if (Array.isArray(value)) return value.map(withoutClock);
    if (!value || typeof value !== 'object') return value;
    return Object.fromEntries(Object.entries(value).filter(([key]) => !['createdAt', 'completedAt', 'startedAt', 'asOf'].includes(key)).map(([key, item]) => [key, withoutClock(item)]));
};
const client = createGenerationClient({ createWorker: () => new NodeModuleWorker() });
const compare = async (operation, input, canonical, prefix) => {
    const before = structuredClone(input), result = await client.run(operation, { ...input, makeId: () => prefix });
    const expected = canonical({ ...input, makeId: ids(prefix) });
    assert.deepEqual(withoutClock(result), withoutClock(expected), `${operation} must match the full canonical artifact`);
    assert.deepEqual(input, before, `${operation} cannot mutate its request`);
    return result;
};
try {
    const input = generationInput();
    const program = (await compare('program', input, generateNextProgramForShell, 'worker-program')).program;
    setRestScaleGlobal(1.25);
    await compare('program', input, generateNextProgramForShell, 'worker-long-rest');
    setRestScaleGlobal(1);
    const cycleInput = { ...input, templateId: 'powerbuilding', adaptBetweenBlocks: true };
    const cycle = await compare('cycle', cycleInput, generateNextCycleForShell, 'worker-cycle');
    assert.equal(new Set([cycle.cycle.id, ...cycle.cycle.blockIds]).size, cycle.cycle.blockIds.length + 1, 'cycle/block IDs must be distinct');
    await compare('convert_cycle', { program, templateId: 'powerbuilding', name: 'Converted', adaptBetweenBlocks: true,
        legacyExercises: input.legacyExercises, seed: input.seed }, convertProgramToNextCycleForShell, 'worker-conversion');
    const history = completedHistory(program);
    assert.equal(analyzeShellHistoryForNextEngine(program, history, input.legacyExercises).readyForNextBlock, true);
    await compare('next_block', { program, history, legacyExercises: input.legacyExercises }, generateNextBlockFromShellHistory, 'worker-next');
    const previousSettings = structuredClone(generationSettings());
    const custom = { ...input.legacyExercises.find(ex => ex.id === 'bb-bench'), id: 'custom-m265-bench', name: 'My edited bench' };
    try {
        setLoadInc(10, 'lb'); setExLoadInc({ 'bb-bench': { v: 5, unit: 'lb' } });
        setAvailPlates({ kg: [20, 10, 5, 2.5], lb: [45, 25, 10, 5] });
        setGymLimits({ limits: { dumbbell: 75 }, inventory: {}, limitUnit: 'lb' });
        const loading = structuredClone(generationSettings());
        await compare('next_block', { program, history, legacyExercises: input.legacyExercises }, generateNextBlockFromShellHistory, 'worker-loading');
        const catalog = [...input.legacyExercises, custom];
        executeGenerationTask({ operation: 'program', options: { ...input, legacyExercises: catalog }, idPrefix: 'catalog-added', settings: loading });
        assert.deepEqual(generationSettings(), loading, 'the worker entry applies the exact live loading/rest owners');
        assert.equal(EX_BY_ID[custom.id]?.name, custom.name, 'request catalog includes edited custom definitions');
        const edited = catalog.map(ex => ex.id === custom.id ? { ...ex, name: 'Renamed bench' } : ex);
        executeGenerationTask({ operation: 'program', options: { ...input, legacyExercises: edited }, idPrefix: 'catalog-edited', settings: loading });
        assert.equal(EX_BY_ID[custom.id]?.name, 'Renamed bench', 'a reused worker replaces stale custom definitions');
    } finally {
        executeGenerationTask({ operation: 'program', options: { ...input, legacyExercises: input.legacyExercises.filter(ex => ex.id !== custom.id) }, idPrefix: 'catalog-reset', settings: previousSettings });
    }
    assert.equal(EX_BY_ID[custom.id], undefined, 'removed custom definitions cannot remain in the worker catalog');
    assert.equal(EXERCISES.some(ex => ex.id === custom.id), false);
    const activeProgram = cycle.blocks[0];
    await compare('advance_cycle', { cycle: cycle.cycle, activeProgram, history: completedHistory(activeProgram), legacyExercises: input.legacyExercises },
        advanceNextCycleForShell, 'worker-advance');
    let canonicalError;
    const impossibleInput = { ...input, banned: input.legacyExercises.map(ex => ex.id) };
    try { generateNextProgramForShell(impossibleInput); } catch (error) { canonicalError = error; }
    assert(canonicalError instanceof NextShellAdapterError);
    await assert.rejects(client.run('program', impossibleInput), error => {
        assert(error instanceof NextShellAdapterError);
        assert.equal(error.code, canonicalError.code); assert.equal(error.message, canonicalError.message);
        assert.deepEqual(error.recovery, canonicalError.recovery); return true;
    });
    await assert.rejects(client.run('untrusted_operation', input), { code: 'NEXT_GENERATION_INVALID_REQUEST' });
} finally { setRestScaleGlobal(1); client.dispose(); }

class PendingWorker extends EventTarget {
    messages = []; stopped = false;
    postMessage(payload) { this.messages.push(structuredClone(payload)); }
    terminate() { this.stopped = true; }
    reply(data) { this.dispatchEvent(new MessageEvent('message', { data })); }
}
const workers = [], fakeClient = createGenerationClient({ createWorker: () => { const w = new PendingWorker(); workers.push(w); return w; }, timeoutMs: 1000 });
const controller = new AbortController(), mutable = generationInput();
const pending = fakeClient.run('program', mutable, { signal: controller.signal, settings: generationSettings() });
const rejection = assert.rejects(pending, { name: 'AbortError' });
await assert.rejects(fakeClient.run('program', mutable), { code: 'NEXT_GENERATION_BUSY' });
mutable.config = { ...mutable.config, name: 'Edited after submission' };
assert.equal(workers[0].messages[0].options.config.name, config.name, 'submitted data is a snapshot');
controller.abort(); await rejection; assert.equal(workers[0].stopped, true, 'cancellation stops actual computation');
const second = fakeClient.run('program', generationInput());
workers[0].reply({ id: 1, ok: true, result: { wrong: true } });
workers[0].dispatchEvent(new Event('error'));
workers[1].reply({ id: workers[1].messages[0].id, ok: true, result: { correct: true } });
assert.deepEqual(await second, { correct: true }, 'late replies from a terminated worker cannot replace a new job');
const failed = fakeClient.run('program', generationInput());
const failedAssertion = assert.rejects(failed, { code: 'NEXT_GENERATION_UNAVAILABLE' });
workers[1].dispatchEvent(new Event('error')); await failedAssertion;
const retry = fakeClient.run('program', generationInput());
workers[2].reply({ id: workers[2].messages[0].id, ok: true, result: { retried: true } });
assert.deepEqual(await retry, { retried: true });
workers[2].dispatchEvent(new Event('error'));
assert.equal(workers[2].stopped, true, 'an idle worker crash also discards the broken worker');
const idleRetry = fakeClient.run('program', generationInput());
workers[3].reply({ id: workers[3].messages[0].id, ok: true, result: { idleRetry: true } });
assert.deepEqual(await idleRetry, { idleRetry: true });
fakeClient.dispose();
const timeoutClient = createGenerationClient({ createWorker: () => new PendingWorker(), timeoutMs: 5 });
await assert.rejects(timeoutClient.run('program', generationInput()), { code: 'NEXT_GENERATION_TIMEOUT' }); timeoutClient.dispose();
const unsupported = createGenerationClient({ createWorker: () => { throw new Error('Worker unavailable'); } });
await assert.rejects(unsupported.run('program', generationInput()), { code: 'NEXT_GENERATION_UNAVAILABLE' }); unsupported.dispose();
const alreadyAborted = new AbortController(); alreadyAborted.abort();
await assert.rejects(fakeClient.run('program', generationInput(), { signal: alreadyAborted.signal }), { name: 'AbortError' });

const source = fs.readFileSync(new URL('../modules/App.js', import.meta.url), 'utf8');
const app = source.slice(source.indexOf('function App()'));
assert.doesNotMatch(app, /\b(?:generateNextProgramForShell|generateNextCycleForShell|convertProgramToNextCycleForShell)\(/, 'interactive generation must not return to the main thread');
for (const operation of ['program', 'cycle', 'convert_cycle', 'advance_cycle', 'next_block']) assert(app.includes(`runGeneration('${operation}'`));
assert(app.includes('generationOwnerChanged(task.owner)'), 'results must be guarded against newer app state');
assert(app.includes('generationTask.controller.abort()'), 'the visible Cancel action must abort computation');
console.log('PASS M265 worker generation: full artifact parity for all five operations, live rest/loading settings, distinct identities, typed refusals, immutable messages, cancellation, stale reply rejection, timeout/crash retry and real app wiring.');
