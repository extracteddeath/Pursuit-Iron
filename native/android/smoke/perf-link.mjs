// A handoff link carrying a lifter with real history (default 150 sessions over about a year), for the release-build
// performance pass: Today, Progress and History cost grows with history, so measuring them on a two-workout store
// says nothing about the people who use the app the most.
import { deflateRawSync } from 'node:zlib';
import { writeFileSync } from 'node:fs';
const Real = Date; let off = 0;
class FakeDate extends Real { constructor(...a) { if (a.length === 0) super(Real.now() + off); else super(...a); } static now() { return Real.now() + off; } }
globalThis.Date = FakeDate;
const C = await import('../../core/src/core.js');
const N = Number(process.argv[3]) || 150, DAY = 864e5;
off = -Math.ceil(N * 7 / 3) * DAY;
C.init({});
C.updateSettings({ unit: 'kg' });
C.createProgram({ config: { name: 'Long history', goal: 'hypertrophy', experience: 'intermediate', split: 'upper_lower', days: 4, session: 's60',
  equipment: ['barbell', 'rack', 'bench', 'dumbbell', 'cable', 'machine', 'pullup'] }, seed: 5 });
for (let k = 0; k < N; k++) {
    off += (k % 3 === 2 ? 3 : 2) * DAY;
    C.startSession({});
    for (const e of C.session().exercises) for (const s of e.sets) {
        if (s.warm || s.sub) continue;
        if (!C.session().exercises[e.ei].sets[s.i].weight) C.act({ type: 'edit', ei: e.ei, si: s.i, field: 'weight', value: '40' });
        C.act({ type: 'toggle', ei: e.ei, si: s.i });
    }
    C.finishSession();
}
const envelope = JSON.stringify({ kind: 'pi-handoff', v: 1, exportedAt: Real.now(), store: C.exportStore(), live: null });
const d = deflateRawSync(Buffer.from(envelope)).toString('base64url');
writeFileSync(process.argv[2], 'pursuitiron://handoff?d=' + d);
console.log('perf handoff link', N, 'sessions,', d.length, 'chars');
