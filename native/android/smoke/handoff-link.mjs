// Builds a pursuitiron://handoff link exactly as pi-transfer.html does, from a store made by the
// real engine: two logged workouts in one program, so the import dialog has something to count.
import { deflateRawSync } from 'node:zlib';
import { writeFileSync } from 'node:fs';
import * as C from '../../core/src/core.js';

C.init({});
C.createProgram({ config: { name: 'From the web app', goal: 'both', experience: 'intermediate', split: 'full_body', days: 3, session: 's40', equipment: ['barbell', 'dumbbell', 'bench'] }, seed: 9 });
for (let k = 0; k < 2; k++) {
    C.startSession({});
    for (const e of C.session().exercises) for (const s of e.sets) {
        C.act({ type: 'edit', ei: e.ei, si: s.i, field: 'weight', value: String(40 + 5 * k) });
        C.act({ type: 'toggle', ei: e.ei, si: s.i });
    }
    C.finishSession();
}
const envelope = JSON.stringify({ kind: 'pi-handoff', v: 1, exportedAt: Date.now(), store: C.exportStore(), live: null });
const d = deflateRawSync(Buffer.from(envelope)).toString('base64url');
writeFileSync(process.argv[2], 'pursuitiron://handoff?d=' + d);
console.log('handoff link', d.length, 'chars');
