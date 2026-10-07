import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { pathToFileURL } from 'node:url';
import { register } from 'node:module';
import * as api from '../modules/engine-api.js';

const hash = value => crypto.createHash('sha256').update(JSON.stringify(value, (key, item) => key === 'note' && typeof item === 'string' && /^Pursuit Engine \d+\.\d+\.\d+$/.test(item) ? 'Pursuit Engine [version]' : item)).digest('hex');
const configs = JSON.parse(fs.readFileSync(new URL('./m205-generation-parity-results.json', import.meta.url))).rows;
const goldenFile = new URL('./fixtures/engine-816-golden.json', import.meta.url);
const baseline = process.env.ENGINE_BASELINE ? await import(pathToFileURL(path.resolve(process.env.ENGINE_BASELINE, 'index.js'))) : api;
const create = Boolean(process.env.WRITE_ENGINE_GOLDEN);
const expected = create ? [] : JSON.parse(fs.readFileSync(goldenFile));
if (!create) {
    const ex = api.shell.EX_BY_ID['bb-bench'];
    api.shell.setLoadInc(7, 'kg');
    assert.equal(api.shell.loadStep(ex, 'kg'), 7, 'global inventory preference reaches the canonical load calculation');
    api.shell.setExLoadInc({ 'bb-bench': { v: 3, unit: 'kg' } });
    assert.equal(api.shell.loadStep(ex, 'kg'), 3, 'exercise-specific preference wins');
    api.shell.setGymLimits({ limitUnit: 'lb', limits: { barbell: 315 } });
    assert.equal(api.shell.gymCapFor(ex, 'lb'), 315);
    api.shell.setLoadInc(0, null);
    api.shell.setExLoadInc(null);
    api.shell.setGymLimits(null);
}
const now = Date.now;
Date.now = () => 1791388800000;
try {
    for (const [i, row] of configs.entries()) {
        const engine = create ? baseline : api;
        const p = engine.generateNextProgramForShell({ config: row.config, legacyExercises: engine.shell.EXERCISES, seed: row.seed, makeId: () => `golden-${i}` }).program;
        const projection = {
            sessions: p.nextEngine.program.sessions,
            weeks: p.nextWeekPrescriptions,
            cells: p.days.flatMap(day => day.exercises.flatMap((id, slot) => [1, 3, 6].map(w => engine.shell.computeCell(p, day, id, slot, w)))),
            volume: engine.shell.weeklyVolume(p),
            weekPlan: engine.shell.buildWeekPlan(p)
        };
        const actual = { name: row.name ?? `${row.config.split}-${i}`, hash: hash(projection) };
        if (create) expected.push(actual);
        else assert.deepEqual(actual, expected[i], `Build 816 differential scenario ${i}`);
    }
} finally { Date.now = now; }
if (create) {
    fs.mkdirSync(new URL('./fixtures/', import.meta.url), { recursive: true });
    fs.writeFileSync(goldenFile, JSON.stringify(expected, null, 2) + '\n');
} else {
    register('./import-loader.mjs', import.meta.url);
    const app = await import('../modules/App.js');
    for (const name of Object.keys(api.shell)) if (name in app)
        assert.equal(app[name], api.shell[name], `UI must use the same canonical binding: ${name}`);
    const files = fs.readdirSync(new URL('../modules/training-domain/', import.meta.url));
    for (const file of files) {
        const source = fs.readFileSync(new URL('../modules/training-domain/' + file, import.meta.url), 'utf8');
        assert.doesNotMatch(source, /from\s*['"](?:react|react\/|lucide-react)|\b(?:window|document|localStorage|sessionStorage|navigator)\s*[.[]/);
    }
    assert.doesNotMatch(fs.readFileSync(new URL('../engine-lab/export-engine.mjs', import.meta.url), 'utf8'), /@babel|\.traverse|parse\(/);
}
console.log(`PASS M228: ${configs.length} Build 816 golden projections, canonical UI binding identity and browser-free computation; zero declaration extraction.`);
