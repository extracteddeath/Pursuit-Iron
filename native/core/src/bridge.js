/* One entry point for the native shell: PICore.call(method, argsJson) -> resultJson.
   Every result is { ok: true, value, dirty } or { ok: false, error }. `dirty` tells the shell
   which stores to persist after the call, so writes happen only when something changed. */
import './polyfills.js';
import * as core from './core.js';

const METHODS = Object.fromEntries(Object.entries(core).filter(([, v]) => typeof v === 'function' && v !== core.dirty));

function call(method, argsJson) {
    try {
        const fn = METHODS[method];
        if (!fn) return JSON.stringify({ ok: false, error: 'Unknown method: ' + method });
        const args = argsJson ? JSON.parse(argsJson) : {};
        const value = fn(args);
        return JSON.stringify({ ok: true, value: value === undefined ? null : value, dirty: core.dirty() });
    } catch (err) {
        return JSON.stringify({ ok: false, error: String(err && err.stack || err) });
    }
}

globalThis.PICore = { call, methods: () => Object.keys(METHODS) };
