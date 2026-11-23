// Program share codes (PI2-…), the prototype's "share a program as a code" made real.
//
// The old PI- codes stored generation SEEDS and could only rebuild a program the legacy engine had
// made, so the importer was switched off and the share sheet offered a code nothing could read.
// A PI2 code carries the program itself — every day, exercise and its sets, reps, effort and rest
// for the week being shared — and imports as an editable custom program. It never depends on which
// engine built the original, so a code stays readable across releases.
import { EX_BY_ID, EQUIPMENT, ENGINE_V, daysInWeek, weeksOf, sessionPrescriptionRows, markUserPrescriptionOverride, uid } from './engine-api.js';

const PREFIX = 'PI2-';
const MAX_DAYS = 14, MAX_SLOTS = 30;

const toBase64Url = (bytes) => {
    let bin = '';
    for (const b of bytes) bin += String.fromCharCode(b);
    return btoa(bin).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
};
const fromBase64Url = (text) => {
    const b64 = text.replace(/-/g, '+').replace(/_/g, '/');
    const bin = atob(b64 + '='.repeat((4 - b64.length % 4) % 4));
    return Uint8Array.from(bin, c => c.charCodeAt(0));
};
const canCompress = () => typeof CompressionStream === 'function' && typeof DecompressionStream === 'function';
const pipe = async (bytes, stream) => new Uint8Array(await new Response(new Blob([bytes]).stream().pipeThrough(stream)).arrayBuffer());
/* Four base-36 characters of FNV-1a over the payload: a mistyped or truncated paste is refused
   with a clear message instead of importing a different program. */
const check = (text) => {
    let h = 0x811c9dc5;
    for (let i = 0; i < text.length; i++) { h ^= text.charCodeAt(i); h = Math.imul(h, 0x01000193) >>> 0; }
    return (h % 1679616).toString(36).padStart(4, '0');
};

export function isShareCode(text) {
    return /^\s*PI2-/i.test(String(text || ''));
}

/** Resolves `{ code, skipped }` — skipped counts lifts the code cannot carry (the sender's own custom
    exercises). The body is deflated where the browser can (`z`), else plain JSON (`j`); both decode. */
export async function encodeShareCode(program, weekIndex = 1, { compress = canCompress() } = {}) {
    if (!program?.days?.length) return { code: null, skipped: 0 };
    const weeks = weeksOf(program);
    const week = Math.min(Math.max(1, Number(weekIndex) || 1), Math.max(1, weeks));
    const cfg = program.config || {};
    let skipped = 0;
    const days = daysInWeek(program, week).slice(0, MAX_DAYS).map(day => {
        const rows = sessionPrescriptionRows(program, day, week).filter(row => {
            const known = !EX_BY_ID[row.id]?.custom;
            if (!known) skipped++;
            return known;
        }).slice(0, MAX_SLOTS);
        const linked = rows.map(row => !!program.ss?.[`${day.id}:${row.slot}`]);
        return [String(day.label || 'Day').slice(0, 40), rows.map((row, i) => {
            const c = row.cell, rest = Number(c.rest);
            const slot = [row.id, Number(c.sets) || 1, String(c.range || c.reps || ''), c.rir == null ? '' : String(c.rir)];
            if (rest > 0) slot.push(rest);
            if (linked[i] && i < rows.length - 1) slot[5] = 1;
            return slot;
        }), Math.max(0, rows.findIndex(row => row.slot === day.primaryIndex))];
    }).filter(day => day[1].length);
    if (!days.length) return { code: null, skipped };
    const payload = { n: String(program.name || 'Shared program').slice(0, 60), w: Math.max(1, weeks), d: days,
        c: [cfg.goal || 'both', cfg.experience || 'intermediate', cfg.session || 's60', cfg.deload ? 1 : 0] };
    const json = new TextEncoder().encode(JSON.stringify(payload));
    const body = compress ? 'z' + toBase64Url(await pipe(json, new CompressionStream('deflate-raw'))) : 'j' + toBase64Url(json);
    return { code: PREFIX + body + '-' + check(body), skipped };
}

/** Resolves `{ ok, program, skipped }` or `{ ok:false, reason }`. The program is a new custom program. */
export async function decodeShareCode(text) {
    const raw = String(text || '').trim().replace(/\s+/g, '');
    if (!/^PI2-/i.test(raw)) return { ok: false, reason: 'That doesn’t look like a program code. Codes start with PI2-.' };
    const rest = raw.slice(PREFIX.length), cut = rest.lastIndexOf('-');
    const body = cut > 0 ? rest.slice(0, cut) : '', sum = cut > 0 ? rest.slice(cut + 1).toLowerCase() : '';
    if (!body || check(body) !== sum) return { ok: false, reason: 'That code is incomplete or has a typo. Copy the whole code and try again.' };
    let data;
    try {
        const bytes = fromBase64Url(body.slice(1));
        if (body[0] === 'z' && !canCompress()) return { ok: false, reason: 'This browser can’t read compressed program codes. Update it and try again.' };
        const json = body[0] === 'z' ? await pipe(bytes, new DecompressionStream('deflate-raw')) : body[0] === 'j' ? bytes : null;
        data = JSON.parse(new TextDecoder().decode(json));
    }
    catch { return { ok: false, reason: 'That code is incomplete or has a typo. Copy the whole code and try again.' }; }
    if (!data || !Array.isArray(data.d)) return { ok: false, reason: 'That code doesn’t contain a program.' };
    const [goal, experience, session, deload] = Array.isArray(data.c) ? data.c : [];
    const weeks = Math.min(16, Math.max(1, Math.round(Number(data.w)) || 6));
    const name = String(data.n || 'Shared program').slice(0, 60);
    let skipped = 0;
    const days = [], overrides = {}, ss = {};
    for (const entry of data.d.slice(0, MAX_DAYS)) {
        if (!Array.isArray(entry) || !Array.isArray(entry[1])) continue;
        const id = uid(), exercises = [];
        for (const slot of entry[1].slice(0, MAX_SLOTS)) {
            const [exId, sets, reps, rir, restSec, link] = Array.isArray(slot) ? slot : [];
            if (!EX_BY_ID[exId] || EX_BY_ID[exId].custom || exercises.includes(exId)) { skipped++; continue; }
            const index = exercises.length, key = `${id}:${index}`;
            exercises.push(exId);
            let o = markUserPrescriptionOverride({}, 'sets', Math.min(20, Math.max(1, Math.round(Number(sets)) || 3)));
            if (/^\d{1,2}(-\d{1,2})?$/.test(String(reps))) o = markUserPrescriptionOverride(o, 'reps', String(reps));
            if (String(rir) !== '' && Number.isFinite(Number(rir)) && Number(rir) >= 0 && Number(rir) <= 5) o = markUserPrescriptionOverride(o, 'rir', String(rir));
            if (Number(restSec) > 0 && Number(restSec) <= 900) o = markUserPrescriptionOverride(o, 'rest', Math.round(Number(restSec)));
            overrides[key] = o;
            if (link === 1) ss[key] = true;
        }
        if (!exercises.length) continue;
        const primary = Math.round(Number(entry[2])) || 0;
        days.push({ id, label: String(entry[0] || `Day ${days.length + 1}`).slice(0, 40), type: 'custom', exercises,
            primaryIndex: primary >= 0 && primary < exercises.length ? primary : 0 });
        // A superset link on the final slot has no partner.
        delete ss[`${id}:${exercises.length - 1}`];
    }
    if (!days.length) return { ok: false, reason: 'None of the exercises in that code are in this version’s library.' };
    const program = {
        id: uid(), name, createdAt: Date.now(), engineV: ENGINE_V, custom: true, weeks, importedFrom: 'share-code',
        config: { name, experience: ['none', 'beginner', 'intermediate', 'advanced'].includes(experience) ? experience : 'intermediate',
            goal: ['hypertrophy', 'strength', 'both'].includes(goal) ? goal : 'both', split: 'custom', days: days.length,
            session: /^s\d+$/.test(String(session)) ? session : 's60', weeks, equipment: EQUIPMENT.map(eq => eq.id),
            focus: {}, focusList: [], reduce: [], progression: 'auto', deload: !!deload, barbellCap: null, percentScheme: null },
        days, overrides, ...(Object.keys(ss).length ? { ss } : {})
    };
    return { ok: true, program, skipped };
}
