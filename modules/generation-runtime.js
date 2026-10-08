import { NextShellAdapterError, uid, LOAD_INC, EX_LOAD_INC, AVAIL_PLATES, GYM_LIMITS, REST_SCALE } from './engine-api.js';

export function generationAbortError() {
    const error = new Error('Plan building was cancelled.');
    error.name = 'AbortError';
    return error;
}

/** Snapshot the same loading/rest owners that the foreground engine uses. */
export function generationSettings() {
    return { loadInc: LOAD_INC, exLoadInc: EX_LOAD_INC, plates: AVAIL_PLATES, gym: GYM_LIMITS, restScale: REST_SCALE };
}

/** One active job per client. Cancellation kills its computation; a later job starts cleanly. */
export function createGenerationClient({
    createWorker = () => new Worker(new URL('./generation-worker.js', import.meta.url), { type: 'module', name: 'pursuit-plan-builder' }),
    timeoutMs = 120000
} = {}) {
    let worker = null, active = null, sequence = 0;
    const unavailable = () => new NextShellAdapterError('NEXT_GENERATION_UNAVAILABLE', 'The plan builder could not start. Reload the app and try again.');
    const stop = () => { worker?.terminate(); worker = null; };
    const settle = (error, result, terminate = false) => {
        const job = active;
        if (!job) return;
        active = null;
        clearTimeout(job.timer);
        job.signal?.removeEventListener('abort', job.abort);
        if (terminate) stop();
        if (error) job.reject(error); else job.resolve(result);
    };
    const start = () => {
        if (worker) return;
        worker = createWorker();
        const owner = worker;
        worker.addEventListener('message', event => {
            const reply = event.data;
            if (!active || reply?.id !== active.id) return;
            if (reply.ok === true && reply.result && typeof reply.result === 'object') {
                settle(null, reply.result);
            } else if (reply.ok === false && typeof reply.error?.code === 'string' && typeof reply.error?.message === 'string') {
                settle(new NextShellAdapterError(reply.error.code, reply.error.message, reply.error.recovery));
            } else {
                settle(unavailable(), undefined, true);
            }
        });
        const failed = () => { if (worker !== owner) return; if (active) settle(unavailable(), undefined, true); else stop(); };
        worker.addEventListener('error', event => { event.preventDefault?.(); failed(); });
        worker.addEventListener('messageerror', failed);
    };
    return {
        run(operation, options, { signal, settings = generationSettings() } = {}) {
            if (signal?.aborted) return Promise.reject(generationAbortError());
            if (active) return Promise.reject(new NextShellAdapterError('NEXT_GENERATION_BUSY', 'A plan is already being built. Wait or cancel it first.'));
            return new Promise((resolve, reject) => {
                const id = ++sequence;
                const abort = () => settle(generationAbortError(), undefined, true);
                active = { id, resolve, reject, signal, abort,
                    timer: setTimeout(() => settle(new NextShellAdapterError('NEXT_GENERATION_TIMEOUT', 'Plan building took too long. Your current plan is unchanged. Try again.'), undefined, true), timeoutMs) };
                signal?.addEventListener('abort', abort, { once: true });
                try {
                    start();
                    const { makeId, ...input } = options;
                    // Functions never cross the structured-clone boundary. A single unique prefix
                    // produces distinct cycle/block identities without imposing an arbitrary ID budget.
                    const idPrefix = typeof makeId === 'function' ? makeId() : uid();
                    worker.postMessage({ id, operation, options: input, idPrefix, settings });
                } catch (error) {
                    settle(error instanceof NextShellAdapterError ? error : unavailable(), undefined, true);
                }
            });
        },
        dispose() { if (active) settle(generationAbortError(), undefined, true); else stop(); }
    };
}

// Diagnostics have their own lazy client; normal creation belongs to the mounted app's client.
export const diagnosticGenerationClient = createGenerationClient();
