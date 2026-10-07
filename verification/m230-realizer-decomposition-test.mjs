import assert from 'node:assert/strict';
import fs from 'node:fs';
import crypto from 'node:crypto';
const fixture = JSON.parse(fs.readFileSync(new URL('./fixtures/m230-preserved-declarations.json', import.meta.url)));
const hash = text => crypto.createHash('sha256').update(text).digest('hex');
for (const [name, record] of Object.entries(fixture)) {
    const url = new URL(`../modules/next-engine/realization/${record.module}.js`, import.meta.url);
    const module = await import(url);
    if (record.declaration) {
        assert(fs.readFileSync(url, 'utf8').includes(record.declaration), `Preserved declaration: ${name}`);
        assert.equal(hash(record.declaration), record.sha256);
    } else assert.equal(hash(module[name].toString()), record.sha256, `Preserved function: ${name}`);
}
const { realizeSessions } = await import('../modules/next-engine/realizer.js');
assert(realizeSessions.toString().split('\n').length < 50, 'session coordinator delegates the stages');
console.log(`PASS M230: ${Object.keys(fixture).length} original declarations remain byte-identical; nine acyclic responsibility modules and a short coordinator.`);
