/* Host APIs the engine uses that a bare JS runtime (QuickJS on Android) may not provide.
   Installed before the engine module body runs (imported first by bridge.js). */
if (typeof globalThis.structuredClone !== 'function') {
    // Structured clone for the value kinds the engine stores: plain objects, arrays, Map, Set,
    // Date, RegExp and typed arrays, with shared references and cycles preserved.
    globalThis.structuredClone = function structuredClone(value) {
        const seen = new Map();
        const clone = (v) => {
            if (v === null || typeof v !== 'object') {
                if (typeof v === 'function' || typeof v === 'symbol') throw new TypeError('structuredClone: uncloneable ' + typeof v);
                return v;
            }
            if (seen.has(v)) return seen.get(v);
            let out;
            if (Array.isArray(v)) { out = new Array(v.length); seen.set(v, out); for (let i = 0; i < v.length; i++) if (i in v) out[i] = clone(v[i]); return out; }
            if (v instanceof Date) { out = new Date(v.getTime()); seen.set(v, out); return out; }
            if (v instanceof RegExp) { out = new RegExp(v.source, v.flags); seen.set(v, out); return out; }
            if (v instanceof Map) { out = new Map(); seen.set(v, out); v.forEach((val, key) => out.set(clone(key), clone(val))); return out; }
            if (v instanceof Set) { out = new Set(); seen.set(v, out); v.forEach((val) => out.add(clone(val))); return out; }
            if (ArrayBuffer.isView(v)) { out = new v.constructor(v); seen.set(v, out); return out; }
            if (v instanceof ArrayBuffer) { out = v.slice(0); seen.set(v, out); return out; }
            out = {}; seen.set(v, out);
            for (const k of Object.keys(v)) out[k] = clone(v[k]);
            return out;
        };
        return clone(value);
    };
}
if (typeof globalThis.console === 'undefined') {
    const sink = (level) => (...args) => { try { if (typeof globalThis.__piLog === 'function') globalThis.__piLog(level, args.map(String).join(' ')); } catch { /* logging must never throw */ } };
    globalThis.console = { log: sink('log'), info: sink('info'), warn: sink('warn'), error: sink('error'), debug: sink('debug') };
}
