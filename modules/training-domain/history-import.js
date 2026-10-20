/* ============================== IMPORT HISTORY FROM STRONG / HEVY ==============================
 * A lifter's training history is what keeps them on another app. This converts a Strong or Hevy CSV
 * export into a BACKUP-SHAPED store ({ history, custom }) and nothing else, so it travels the exact
 * path a restored backup does: the same merge (never drops a workout, dedupes by id), the same
 * add/update/remove preview, and the same Undo. There is no second history writer.
 *
 * Determinism is the safety property: every imported workout and every created exercise has an id
 * derived from its source row, so importing the same file twice changes nothing, and a later
 * correction to an imported workout (newer updatedAt) survives a re-import.
 *
 * Formats (both one row per set, both vary by app version, so every variant below is accepted):
 *   Hevy:   title,start_time,end_time,description,exercise_title,superset_id,exercise_notes,set_index,
 *           set_type,weight_lbs|weight_kg,reps,distance_miles|distance_km,duration_seconds,rpe
 *           start_time "15 Jul 2026, 09:52"; set_type normal | warmup | dropset | drop_set | failure.
 *   Strong: Date,Workout Name,Duration,Exercise Name,Set Order,Weight,Reps,Distance,Seconds,Notes,
 *           Workout Notes,RPE — comma or semicolon (semicolon files use decimal commas). Newer exports
 *           add "Weight Unit", or name the column "Weight (kg)" / "Weight (lbs)". Set Order is a number,
 *           or W (warm-up), D (drop set), F (failure). Older exports carry NO unit at all, so the caller
 *           must say which unit the file is in (the preview asks).
 */
import { summarizeSets } from './prescriptions.js';
import { STORE_VERSION } from './records.js';

const MONTHS = { jan: 0, feb: 1, mar: 2, apr: 3, may: 4, jun: 5, jul: 6, aug: 7, sep: 8, sept: 8, oct: 9, nov: 10, dec: 11 };
const LB_PER_KG = 2.2046226;

/* RFC-4180 parsing with delimiter detection from the header line. Quoted fields may contain the
   delimiter, doubled quotes and newlines. A UTF-8 BOM is ignored. */
export function parseDelimited(text) {
    const src = String(text ?? '').replace(/^﻿/, '');
    const firstLine = src.split(/\r?\n/, 1)[0] || '';
    const count = ch => { let n = 0, q = false; for (const c of firstLine) { if (c === '"') q = !q; else if (!q && c === ch) n++; } return n; };
    const delimiter = [',', ';', '\t'].sort((a, b) => count(b) - count(a))[0];
    const rows = [];
    let row = [], field = '', quoted = false;
    for (let i = 0; i < src.length; i++) {
        const c = src[i];
        if (quoted) {
            if (c === '"') {
                if (src[i + 1] === '"') { field += '"'; i++; }
                else quoted = false;
            } else field += c;
            continue;
        }
        if (c === '"') quoted = true;
        else if (c === delimiter) { row.push(field); field = ''; }
        else if (c === '\n' || c === '\r') {
            if (c === '\r' && src[i + 1] === '\n') i++;
            row.push(field); field = '';
            if (row.some(v => v !== '')) rows.push(row);
            row = [];
        } else field += c;
    }
    row.push(field);
    if (row.some(v => v !== '')) rows.push(row);
    const header = (rows.shift() || []).map(h => h.trim());
    return { header, rows, delimiter };
}

const headerKey = h => String(h || '').trim().toLowerCase();
export function detectWorkoutCsv(text) {
    if (typeof text !== 'string' || text.length < 20 || /^\s*[{[]/.test(text)) return null;
    const { header } = parseDelimited(text.slice(0, 4000));
    const keys = new Set(header.map(headerKey));
    if (keys.has('exercise_title') && keys.has('set_index') && keys.has('start_time')) return 'hevy';
    if (keys.has('exercise name') && keys.has('set order') && (keys.has('date') || keys.has('workout name'))) return 'strong';
    return null;
}

/* Numbers: "102.5", "102,5" (decimal comma), "1,102.5" (thousands comma in a dot-decimal file). */
function num(raw, decimalComma) {
    if (raw == null) return null;
    let s = String(raw).trim();
    if (!s) return null;
    if (decimalComma) s = s.replace(/\./g, '').replace(',', '.');
    else s = s.replace(/,(?=\d{3}(\D|$))/g, '');
    const n = Number(s);
    return Number.isFinite(n) ? n : null;
}

/* Local wall-clock times — the lifter's own clock, which is what both apps export. */
function parseDate(raw) {
    const s = String(raw || '').trim();
    if (!s) return null;
    let m = s.match(/^(\d{4})-(\d{1,2})-(\d{1,2})(?:[ T](\d{1,2}):(\d{2})(?::(\d{2}))?)?/);
    if (m) return new Date(+m[1], +m[2] - 1, +m[3], +(m[4] || 0), +(m[5] || 0), +(m[6] || 0)).getTime();
    m = s.match(/^(\d{1,2})\s+([A-Za-z]{3,4})[a-z]*\.?\s+(\d{4}),?\s+(\d{1,2}):(\d{2})(?:\s*([AaPp][Mm]))?/);
    if (m && MONTHS[m[2].toLowerCase()] != null) return new Date(+m[3], MONTHS[m[2].toLowerCase()], +m[1], hour24(+m[4], m[6]), +m[5]).getTime();
    m = s.match(/^([A-Za-z]{3,4})[a-z]*\.?\s+(\d{1,2}),?\s+(\d{4}),?\s+(\d{1,2}):(\d{2})(?:\s*([AaPp][Mm]))?/);
    if (m && MONTHS[m[1].toLowerCase()] != null) return new Date(+m[3], MONTHS[m[1].toLowerCase()], +m[2], hour24(+m[4], m[6]), +m[5]).getTime();
    m = s.match(/^(\d{1,2})\/(\d{1,2})\/(\d{2,4}),?\s*(?:(\d{1,2}):(\d{2})(?::\d{2})?\s*([AaPp][Mm])?)?/);
    if (m) {
        const y = +m[3] < 100 ? 2000 + +m[3] : +m[3];
        return new Date(y, +m[1] - 1, +m[2], hour24(+(m[4] || 0), m[6]), +(m[5] || 0)).getTime();
    }
    return null;
}
function hour24(h, ampm) {
    if (!ampm) return h;
    const pm = /p/i.test(ampm);
    return pm ? (h % 12) + 12 : h % 12;
}
/* Strong durations: "1h 23m", "45m", "50s", "01:23:00", "83:00", or plain seconds. */
function parseDurationMin(raw, seconds = false) {
    const s = String(raw || '').trim();
    if (!s) return null;
    if (/^\d+(\.\d+)?$/.test(s)) return Math.max(1, Math.round(Number(s) / (seconds ? 60 : 1)));
    const hm = s.match(/(?:(\d+)\s*h)?\s*(?:(\d+)\s*m(?:in)?)?\s*(?:(\d+)\s*s)?/i);
    if (hm && (hm[1] || hm[2] || hm[3])) return Math.max(1, Math.round((+(hm[1] || 0)) * 60 + (+(hm[2] || 0)) + (+(hm[3] || 0)) / 60));
    const c = s.split(':').map(Number);
    if (c.every(Number.isFinite) && c.length >= 2) return Math.max(1, Math.round(c.length === 3 ? c[0] * 60 + c[1] + c[2] / 60 : c[0] + c[1] / 60));
    return null;
}

/* Stable 32-bit FNV-1a, base36 — ids only need to be deterministic and collision-unlikely. */
function hash(text) {
    let h = 0x811c9dc5;
    for (let i = 0; i < text.length; i++) { h ^= text.charCodeAt(i); h = Math.imul(h, 0x01000193) >>> 0; }
    return h.toString(36);
}
const slug = s => String(s || '').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '').slice(0, 40) || 'x';

/* ------------------------------ exercise matching ------------------------------ */
// Names both apps use, mapped to the catalog by hand. Keyed by normalized name (see `normName`).
const ALIASES = {
    'squat barbell': 'back-squat', 'back squat barbell': 'back-squat', 'squat': 'back-squat',
    'high bar squat barbell': 'high-bar-squat', 'low bar squat barbell': 'low-bar-squat',
    'deadlift barbell': 'deadlift', 'conventional deadlift barbell': 'deadlift', 'deadlift': 'deadlift',
    'bench press barbell': 'bb-bench', 'bench press': 'bb-bench', 'flat bench press barbell': 'bb-bench',
    'overhead press barbell': 'ohp', 'strict military press barbell': 'ohp', 'military press barbell': 'ohp', 'overhead press': 'ohp', 'shoulder press barbell': 'ohp',
    'bent over row barbell': 'bb-row', 'barbell row': 'bb-row', 'pendlay row barbell': 'pendlay-row',
    'pull up': 'bw-pullup', 'pullup': 'bw-pullup', 'pull up assisted': 'assisted-pullup', 'pull up weighted': 'pullup', 'weighted pull up': 'pullup', 'chin up': 'chinup', 'chinup': 'chinup', 'chin up assisted': 'assisted-chinup',
    'lat pulldown cable': 'lat-pulldown', 'lat pulldown machine': 'lat-pulldown', 'lat pulldown': 'lat-pulldown', 'lat pulldown close grip cable': 'close-pulldown', 'lat pulldown wide grip cable': 'wide-pulldown',
    'seated row cable': 'seated-row', 'seated cable row v grip cable': 'seated-row', 'seated row machine': 'machine-row', 'cable row': 'seated-row',
    'bicep curl dumbbell': 'db-curl', 'biceps curl dumbbell': 'db-curl', 'bicep curl barbell': 'bb-curl', 'bicep curl cable': 'cable-curl', 'bicep curl ez bar': 'ez-curl', 'ez bar biceps curl': 'ez-curl', 'bicep curl machine': 'machine-curl',
    'reverse curl': 'reverse-curl', 'reverse ez bar curl': 'reverse-curl', 'reverse ezbar curl': 'reverse-curl', 'ez bar reverse curl': 'reverse-curl', 'reverse curl ez bar': 'reverse-curl',
    'hammer curl dumbbell': 'hammer', 'hammer curl cable': 'rope-hammer-curl', 'preacher curl barbell': 'preacher', 'preacher curl ez bar': 'preacher', 'preacher curl dumbbell': 'preacher', 'concentration curl dumbbell': 'concentration-curl',
    'triceps pushdown cable straight bar': 'straight-bar-pushdown', 'triceps pushdown cable rope': 'rope-pushdown', 'triceps pushdown': 'pushdown', 'triceps pushdown cable': 'pushdown', 'triceps rope pushdown': 'rope-pushdown',
    'triceps extension cable': 'oh-cable-ext', 'overhead triceps extension cable': 'oh-cable-ext', 'triceps extension dumbbell': 'db-oh-ext', 'skullcrusher barbell': 'skullcrusher', 'skullcrusher ez bar': 'skullcrusher', 'skullcrusher dumbbell': 'lying-db-ext',
    'leg press': 'leg-press', 'leg press machine': 'leg-press', 'leg extension machine': 'leg-ext', 'leg extension': 'leg-ext',
    'lying leg curl machine': 'lying-curl', 'seated leg curl machine': 'seated-curl', 'leg curl machine': 'lying-curl', 'standing leg curl machine': 'standing-curl',
    'romanian deadlift barbell': 'rdl', 'romanian deadlift dumbbell': 'db-rdl', 'stiff leg deadlift barbell': 'stiff-deadlift', 'straight leg deadlift': 'stiff-deadlift',
    'hip thrust barbell': 'hip-thrust', 'hip thrust machine': 'machine-hip-thrust', 'glute bridge': 'glute-bridge',
    'lateral raise dumbbell': 'lat-raise', 'lateral raise cable': 'cable-lat-raise', 'lateral raise machine': 'machine-lat-raise', 'front raise dumbbell': 'front-raise',
    'reverse fly dumbbell': 'rear-fly', 'rear delt reverse fly dumbbell': 'rear-fly', 'reverse fly machine': 'reverse-pec', 'rear delt reverse fly machine': 'reverse-pec', 'face pull cable': 'face-pull', 'face pull': 'face-pull',
    'incline bench press barbell': 'inc-bb-bench', 'incline bench press dumbbell': 'inc-db-press', 'bench press dumbbell': 'db-bench', 'decline bench press barbell': 'decline-bench', 'decline bench press dumbbell': 'decline-db-press', 'incline bench press smith machine': 'smith-incline', 'incline bench press smith': 'smith-incline',
    'chest press machine': 'machine-press', 'shoulder press machine': 'machine-shoulder', 'shoulder press dumbbell': 'db-shoulder', 'seated overhead press dumbbell': 'seated-db-press', 'overhead press dumbbell': 'db-shoulder', 'arnold press dumbbell': 'arnold',
    'chest fly dumbbell': 'db-fly', 'chest fly machine': 'pec-deck', 'pec deck machine': 'pec-deck', 'butterfly pec deck': 'pec-deck', 'cable crossover': 'cable-crossover', 'chest fly cable': 'cable-fly',
    'calf raise machine': 'standing-calf', 'standing calf raise machine': 'standing-calf', 'standing calf raise': 'standing-calf', 'seated calf raise machine': 'seated-calf', 'seated calf raise': 'seated-calf',
    'front squat barbell': 'front-squat', 'goblet squat kettlebell': 'goblet', 'goblet squat dumbbell': 'goblet', 'hack squat machine': 'hack-squat', 'hack squat': 'hack-squat',
    'bulgarian split squat dumbbell': 'bulgarian', 'bulgarian split squat': 'bulgarian', 'lunge dumbbell': 'walking-lunge', 'walking lunge dumbbell': 'walking-lunge', 'lunge bodyweight': 'bw-lunge',
    'shrug dumbbell': 'db-shrug', 'shrug barbell': 'bb-shrug', 'dip': 'dips-tri', 'triceps dip': 'dips-tri', 'chest dip': 'dips-chest', 'dip assisted': 'assisted-dip', 'dip weighted': 'dips-tri', 'push up': 'pushup', 'pushup': 'pushup',
    'plank': 'plank', 'crunch': 'crunch', 'cable crunch': 'cable-crunch', 'hanging leg raise': 'hanging-raise', 'hanging knee raise': 'hanging-knee', 'sit up': 'situp', 'russian twist': 'russian-twist', 'ab wheel': 'ab-wheel',
    't bar row': 'tbar-row', 't bar row machine': 'tbar-row', 'dumbbell row': 'db-row', 'bent over one arm row dumbbell': 'db-row', 'one arm dumbbell row': 'db-row', 'iso lateral row machine': 'machine-row',
    'good morning barbell': 'good-morning', 'sumo deadlift barbell': 'sumo-dl', 'trap bar deadlift': 'trap-bar-deadlift', 'deadlift trap bar': 'trap-bar-deadlift', 'rack pull barbell': 'rack-pull',
    'upright row barbell': 'upright-row', 'farmers walk': 'farmers', 'farmers carry': 'farmers', 'kettlebell swing': 'kb-swing', 'back extension': 'back-ext-45', 'hyperextension': 'back-ext-45', 'wrist curl barbell': 'wrist-curl', 'wrist curl dumbbell': 'wrist-curl'
};
const EQUIP_WORDS = { barbell: 'barbell', dumbbell: 'dumbbell', cable: 'cable', smith: 'smith', kettlebell: 'kettlebell', bands: 'band', ezbar: 'ez',
    machinepress: 'machine', machinerow: 'machine', machineshoulder: 'machine', machinelatraise: 'machine', machinecurl: 'machine', machineext: 'machine',
    machinecrunch: 'machine', machineshrug: 'machine', machinepullover: 'machine', pecdeck: 'machine', legpress: 'machine', legext: 'machine', legcurl: 'machine',
    calfmachine: 'machine', hacksquat: 'machine', hipthrustmachine: 'machine', abduction: 'machine', adduction: 'machine', trapbar: 'trap', assisted: 'assisted' };
const MOVES = new Set(['press', 'row', 'curl', 'squat', 'deadlift', 'raise', 'fly', 'pulldown', 'pullup', 'chinup', 'pushdown', 'extension', 'lunge',
    'thrust', 'bridge', 'crunch', 'plank', 'shrug', 'dip', 'pushup', 'kickback', 'pullover', 'rdl', 'carry', 'swing', 'situp', 'rollout', 'crossover', 'twist', 'pull', 'step']);
const QUALIFIERS = ['incline', 'decline', 'wrist', 'reverse', 'single', 'seated', 'standing', 'lying', 'front', 'rear', 'close', 'wide', 'sumo', 'smith',
    'hammer', 'overhead', 'romanian', 'stiff', 'bulgarian', 'goblet', 'hack', 'preacher', 'spider', 'concentration', 'assisted', 'weighted', 'deficit', 'paused', 'leg'];
const SYN = [[/\bpull[\s-]?ups?\b/g, 'pullup'], [/\bchin[\s-]?ups?\b/g, 'chinup'], [/\bpush[\s-]?ups?\b/g, 'pushup'], [/\bsit[\s-]?ups?\b/g, 'situp'],
    [/\bt[\s-]?bar\b/g, 't bar'], [/\bez[\s-]?bar\b/g, 'ez bar'], [/\bskull[\s-]?crushers?\b/g, 'skullcrusher'], [/\bdb\b/g, 'dumbbell'], [/\bbb\b/g, 'barbell'],
    [/\bpull[\s-]?downs?\b/g, 'pulldown'], [/\bpush[\s-]?downs?\b/g, 'pushdown'], [/\bflyes\b/g, 'fly'], [/\bflys\b/g, 'fly'], [/\bbiceps?\b/g, 'bicep'], [/\btriceps?\b/g, 'triceps'],
    [/\bfarmer'?s\b/g, 'farmers'], [/\bwalks?\b/g, 'walk'], [/\bcurls\b/g, 'curl'], [/\braises\b/g, 'raise'], [/\brows\b/g, 'row'], [/\bpresses\b/g, 'press'],
    [/\bsquats\b/g, 'squat'], [/\blunges\b/g, 'lunge'], [/\bdips\b/g, 'dip'], [/\bshrugs\b/g, 'shrug'], [/\bextensions\b/g, 'extension'], [/\bcrunches\b/g, 'crunch'],
    [/\bromanian deadlift\b/g, 'romanian deadlift'], [/\brdl\b/g, 'romanian deadlift'], [/\bsmith machine\b/g, 'smith'], [/\bbodyweight\b/g, 'bodyweight']];
function normName(raw) {
    let s = String(raw || '').toLowerCase().replace(/[()\-–—/,.&+]/g, ' ').replace(/[^a-z0-9' ]/g, ' ');
    for (const [re, to] of SYN) s = s.replace(re, to);
    // "bicep curl" reads more naturally, but alias keys and catalog both say "bicep" after SYN.
    return s.replace(/'/g, '').replace(/\s+/g, ' ').trim();
}
const tokens = s => normName(s).split(' ').filter(t => t && !['the', 'with', 'and', 'a', 'on', 'grip', 'standard'].includes(t));

export function createExerciseMatcher(catalog) {
    const list = (catalog || []).filter(e => e && e.id && e.name);
    const byId = new Map(list.map(e => [e.id, e]));
    const entries = list.map(e => {
        const t = new Set(tokens(e.name));
        (e.equip || []).forEach(q => { if (EQUIP_WORDS[q]) t.add(EQUIP_WORDS[q]); });
        return { ex: e, t };
    });
    const cache = new Map();
    return function match(rawName) {
        if (cache.has(rawName)) return cache.get(rawName);
        const key = normName(rawName);
        let result = null;
        const alias = ALIASES[key];
        if (alias && byId.has(alias)) result = { id: alias, how: 'alias' };
        if (!result) {
            const exact = list.find(e => normName(e.name) === key);
            if (exact) result = { id: exact.id, how: 'exact' };
        }
        if (!result) {
            const q = new Set(tokens(rawName));
            const qMoves = [...q].filter(t => MOVES.has(t));
            let best = null, second = 0;
            for (const { ex, t } of entries) {
                if (qMoves.length && !qMoves.some(m => t.has(m))) continue;
                let inter = 0;
                q.forEach(x => { if (t.has(x)) inter++; });
                let score = (2 * inter) / (q.size + t.size);
                // A qualifier names a DIFFERENT lift (incline vs flat, wrist curl vs curl, Smith vs free bar):
                // one the query states but the candidate lacks, or the reverse, costs real confidence.
                QUALIFIERS.forEach(word => { if (q.has(word) !== t.has(word)) score -= q.has(word) ? 0.2 : 0.1; });
                const rank = score - (ex.pri || 0) / 1e6;
                if (!best || rank > best.rank) { if (best) second = Math.max(second, best.score); best = { ex, score, rank }; }
                else second = Math.max(second, score);
            }
            if (best && qMoves.length && best.score >= 0.67 && best.score - second >= 0) result = { id: best.ex.id, how: 'fuzzy', score: Math.round(best.score * 100) / 100 };
        }
        cache.set(rawName, result);
        return result;
    };
}

/* For a lift the catalog doesn't have, infer what it trains so it can become a custom exercise. If the
   name says nothing reliable, return null and the lift is reported as skipped — a guessed muscle would
   quietly corrupt the volume model. */
const PART_RULES = [
    [/\b(leg curl|hamstring|nordic|glute ham)\b/, 'hamstrings', 'isolation'], [/\bwrist\b/, 'forearms', 'isolation'], [/\bcurl\b/, 'biceps', 'isolation'],
    [/\b(pushdown|triceps|skullcrusher|kickback triceps|jm press)\b/, 'triceps', 'isolation'], [/\bcalf\b/, 'calves', 'isolation'], [/\bshrug\b/, 'traps', 'isolation'],
    [/\b(abduct)/, 'abductors', 'isolation'], [/\b(adduct)/, 'adductors', 'isolation'], [/\b(hip thrust|glute|bridge|kickback)\b/, 'glutes', 'compound'],
    [/\b(rear delt|reverse fly|face pull|lateral raise|front raise|upright row)\b/, 'shoulders', 'isolation'],
    [/\b(overhead press|shoulder press|military|arnold|push press)\b/, 'shoulders', 'compound'],
    [/\b(pulldown|pullup|chinup|pullover)\b/, 'lats', 'compound'], [/\brow\b/, 'upper_back', 'compound'],
    [/\b(fly|crossover|pec deck)\b/, 'chest', 'isolation'], [/\b(bench|chest press|pushup|incline press|decline press)\b/, 'chest', 'compound'],
    [/\b(romanian|stiff leg|good morning)\b/, 'hamstrings', 'compound'], [/\b(deadlift|back extension|hyperextension)\b/, 'lower_back', 'compound'],
    [/\b(squat|leg press|lunge|step up|leg extension|split squat)\b/, 'quads', 'compound'],
    [/\b(crunch|plank|situp|leg raise|knee raise|twist|ab wheel|rollout|oblique|hollow|v up)\b/, 'abs', 'isolation'], [/\bneck\b/, 'neck', 'isolation'], [/\bdip\b/, 'triceps', 'compound']];
function inferPart(rawName) {
    const n = normName(rawName);
    for (const [re, part, type] of PART_RULES) if (re.test(n)) return { part, type };
    return null;
}
function equipFromName(rawName) {
    const n = normName(rawName);
    const out = [];
    if (/\bbarbell\b/.test(n)) out.push('barbell');
    if (/\bdumbbell\b/.test(n)) out.push('dumbbell');
    if (/\bcable\b/.test(n)) out.push('cable');
    if (/\bsmith\b/.test(n)) out.push('smith');
    if (/\bkettlebell\b/.test(n)) out.push('kettlebell');
    if (/\bband\b/.test(n)) out.push('bands');
    if (/\bez bar\b/.test(n)) out.push('ezbar');
    if (/\bmachine\b/.test(n)) out.push('machinepress');
    return out;
}

/* ------------------------------ conversion ------------------------------ */
function columnIndex(header) {
    const idx = {};
    header.forEach((h, i) => { idx[headerKey(h)] = i; });
    const pick = (...names) => { for (const n of names) if (idx[n] != null) return idx[n]; return null; };
    return pick;
}

function readHevy(parsed) {
    const col = columnIndex(parsed.header);
    const c = {
        title: col('title'), start: col('start_time'), end: col('end_time'), desc: col('description'), ex: col('exercise_title'),
        notes: col('exercise_notes'), idx: col('set_index'), type: col('set_type'), reps: col('reps'), rpe: col('rpe'),
        wLb: col('weight_lbs'), wKg: col('weight_kg'), dist: col('distance_miles', 'distance_km', 'distance_meters'), dur: col('duration_seconds')
    };
    const fileUnit = c.wKg != null ? 'kg' : c.wLb != null ? 'lb' : null;
    return parsed.rows.map(r => {
        const t = String(r[c.type] ?? '').trim().toLowerCase();
        return {
            workoutKey: `${r[c.title]}|${r[c.start]}`, title: (r[c.title] || '').trim() || 'Workout', start: parseDate(r[c.start]), end: parseDate(r[c.end]),
            durationMin: null, exercise: (r[c.ex] || '').trim(), note: (r[c.notes] || '').trim(),
            kind: t === 'warmup' ? 'warm' : (t === 'dropset' || t === 'drop_set' || t === 'drop') ? 'drop' : t === 'failure' ? 'failure' : 'normal',
            weight: num(r[c.wKg ?? c.wLb], false), reps: num(r[c.reps], false), rpe: num(r[c.rpe], false),
            cardio: (num(r[c.dist], false) || num(r[c.dur], false)) && !num(r[c.reps], false)
        };
    }).map(x => ({ ...x, fileUnit }));
}

function readStrong(parsed) {
    const col = columnIndex(parsed.header);
    const decimalComma = parsed.delimiter === ';';
    const weightCol = col('weight', 'weight (kg)', 'weight (lbs)', 'weight (lb)');
    const weightHeader = weightCol != null ? headerKey(parsed.header[weightCol]) : '';
    const headerUnit = /kg/.test(weightHeader) ? 'kg' : /lb/.test(weightHeader) ? 'lb' : null;
    const c = {
        date: col('date'), name: col('workout name'), num: col('workout #'), dur: col('duration', 'workout duration'), durSec: col('duration (sec)'),
        ex: col('exercise name'), order: col('set order'), weight: weightCol, unit: col('weight unit'), reps: col('reps'), rpe: col('rpe'),
        dist: col('distance', 'distance (meters)', 'distance (km)', 'distance (mi)'), secs: col('seconds'), notes: col('notes')
    };
    return parsed.rows.map(r => {
        const order = String(r[c.order] ?? '').trim().toUpperCase();
        const rowUnit = c.unit != null ? (/kg/i.test(r[c.unit] || '') ? 'kg' : /lb/i.test(r[c.unit] || '') ? 'lb' : null) : null;
        const reps = num(r[c.reps], decimalComma);
        return {
            workoutKey: `${r[c.num] ?? ''}|${r[c.date]}|${r[c.name]}`, title: (r[c.name] || '').trim() || 'Workout', start: parseDate(r[c.date]), end: null,
            durationMin: c.durSec != null ? parseDurationMin(r[c.durSec], true) : parseDurationMin(r[c.dur]),
            exercise: (r[c.ex] || '').trim(), note: (r[c.notes] || '').trim(),
            kind: order === 'W' ? 'warm' : order === 'D' ? 'drop' : order === 'F' ? 'failure' : /^\d+$/.test(order) ? 'normal' : 'skip',
            weight: num(r[c.weight], decimalComma), reps, rpe: num(r[c.rpe], decimalComma),
            cardio: (num(r[c.dist], decimalComma) || num(r[c.secs], decimalComma)) && !reps,
            fileUnit: rowUnit || headerUnit
        };
    });
}

const round1 = n => Math.round(n * 10) / 10;
function convertWeight(w, from, to) {
    if (!(w > 0)) return 0;
    if (!from || !to || from === to) return round1(w);
    return round1(to === 'lb' ? w * LB_PER_KG : w / LB_PER_KG);
}

/**
 * Convert a Strong/Hevy CSV into a backup-shaped store.
 * options: { catalog, unit (the app's unit), weightUnit (override for files that carry none), now }
 * Returns { ok, source, store, summary } or { ok: false, msg }.
 */
export function workoutCsvToStore(text, { catalog = [], unit = 'lb', weightUnit = null, now = Date.now() } = {}) {
    const source = detectWorkoutCsv(text);
    if (!source) return { ok: false, msg: 'This file is not a Strong or Hevy workout export.' };
    const parsed = parseDelimited(text);
    const rows = (source === 'hevy' ? readHevy(parsed) : readStrong(parsed)).filter(r => r.exercise);
    if (!rows.length) return { ok: false, msg: `The ${source === 'hevy' ? 'Hevy' : 'Strong'} file has no sets in it.` };
    const fileUnitKnown = rows.some(r => r.fileUnit);
    const assumedUnit = weightUnit || unit;
    const match = createExerciseMatcher(catalog);
    const label = source === 'hevy' ? 'Hevy' : 'Strong';
    const custom = new Map(), unmatched = new Map(), matched = new Map();
    let warmupsSkipped = 0, cardioSkipped = 0, badDates = 0;
    const resolve = name => {
        const m = match(name);
        if (m) { matched.set(name, m.id); return m.id; }
        const id = `custom-import-${slug(name)}-${hash(normName(name))}`;
        if (custom.has(name)) return id;
        const inferred = inferPart(name);
        if (!inferred) return null;
        custom.set(name, { id, name, part: inferred.part, type: inferred.type, equip: equipFromName(name), custom: true, pri: 9000,
            rep: inferred.type === 'compound' ? [6, 10] : [10, 15], importedFrom: source, updatedAt: 0 });
        return id;
    };
    const workouts = new Map();
    for (const r of rows) {
        if (r.kind === 'skip') continue;
        if (r.cardio) { cardioSkipped++; continue; }
        if (!Number.isFinite(r.start)) { badDates++; continue; }
        if (r.kind === 'warm') { warmupsSkipped++; continue; }
        if (!(r.reps > 0)) continue;
        const exId = resolve(r.exercise);
        if (!exId) { unmatched.set(r.exercise, (unmatched.get(r.exercise) || 0) + 1); continue; }
        if (!workouts.has(r.workoutKey)) workouts.set(r.workoutKey, { title: r.title, start: r.start, end: r.end, durationMin: r.durationMin, ex: new Map() });
        const w = workouts.get(r.workoutKey);
        if (!w.ex.has(exId)) w.ex.set(exId, { sets: [], note: r.note });
        const from = r.fileUnit || assumedUnit;
        w.ex.get(exId).sets.push({
            weight: convertWeight(r.weight, from, unit), reps: Math.round(r.reps), done: true,
            ...(r.kind === 'drop' ? { sub: true, kind: 'drop' } : {}),
            rir: r.kind === 'failure' ? 0 : (r.rpe != null && r.rpe >= 1 && r.rpe <= 10 ? Math.max(0, Math.min(5, Math.round(10 - r.rpe))) : null)
        });
    }
    const exById = new Map([...catalog.map(e => [e.id, e]), ...[...custom.values()].map(e => [e.id, e])]);
    const history = [];
    let setCount = 0;
    for (const [key, w] of workouts) {
        const perf = {};
        let volume = 0, sets = 0;
        for (const [exId, e] of w.ex) {
            const summary = summarizeSets(e.sets, exById.get(exId), unit);
            if (!summary) continue;
            const logged = e.sets.map(s => ({ w: s.weight, r: s.reps, ...(s.rir != null ? { rir: s.rir, rirReported: true } : {}), ...(s.sub ? { sub: true, kind: s.kind } : {}) }));
            logged.forEach(s => { volume += s.w * s.r; if (!s.sub) sets++; });
            perf[exId] = { weight: summary.weight, reps: summary.reps, date: w.start, sets: logged, ...(e.note ? { note: e.note } : {}) };
        }
        if (!Object.keys(perf).length) continue;
        setCount += sets;
        const durationMin = w.durationMin || (w.end > w.start ? Math.max(1, Math.round((w.end - w.start) / 60000)) : undefined);
        history.push({
            id: `import-${source}-${hash(key)}`, date: w.start, updatedAt: w.start,
            programId: `import-${source}`, programName: label, dayLabel: w.title, dayId: `import-${source}-${slug(w.title)}`,
            setsDone: sets, totalSets: sets, volume: Math.round(volume), unit,
            ...(durationMin ? { durationMin } : {}),
            perf, imported: { source, at: now }
        });
    }
    if (!history.length) return { ok: false, msg: `No strength sets could be imported from this ${label} file.` };
    history.sort((a, b) => b.date - a.date);
    return {
        ok: true, source,
        store: { v: STORE_VERSION, savedAt: 0, tombs: {}, history, custom: [...custom.values()] },
        summary: {
            source, label, workouts: history.length, sets: setCount, warmupsSkipped, cardioSkipped, badDates,
            weightUnit: fileUnitKnown ? null : assumedUnit, unitAssumed: !fileUnitKnown,
            matched: matched.size, customCreated: [...custom.values()].map(e => e.name),
            unmatched: [...unmatched.entries()].map(([name, sets]) => ({ name, sets })),
            first: history[history.length - 1].date, last: history[0].date
        }
    };
}
