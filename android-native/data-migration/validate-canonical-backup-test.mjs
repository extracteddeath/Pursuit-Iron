import assert from 'node:assert/strict';
import { mkdtempSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
import { parseStoredData, STORE_VERSION } from '../../modules/engine-api.js';
const dir = mkdtempSync(join(tmpdir(), 'pursuit-iron-canonical-backup-'));
const run = path => spawnSync(process.execPath,
    ['android-native/data-migration/validate-canonical-backup.mjs',path],{encoding:'utf8'});
try {
    const valid = join(dir,'valid.json');
    const legacy = {v:12,savedAt:50,history:[{id:'secret-history',date:1}],
        saved:[],cycles:[],custom:[],gyms:[],perf:{},tombs:{}};
    writeFileSync(valid,JSON.stringify(legacy));
    const output = run(valid);
    assert.equal(output.status,0,output.stderr);
    const summary=JSON.parse(output.stdout);
    assert.equal(summary.migratedVersion,STORE_VERSION);
    assert.equal(summary.sourceVersion,12);
    assert.equal(summary.counts.history,1);
    assert.equal(summary.imported,false);
    assert.equal(output.stdout.includes('secret-history'),false);
    assert.equal(parseStoredData(JSON.stringify(legacy)).history[0].updatedAt,0);
    const future=join(dir,'future.json');
    writeFileSync(future,JSON.stringify({...legacy,v:STORE_VERSION+1}));
    assert.notEqual(run(future).status,0);
    const malformed=join(dir,'malformed.json');
    writeFileSync(malformed,JSON.stringify({...legacy,history:'secret-invalid'}));
    const bad=run(malformed);
    assert.notEqual(bad.status,0);
    assert.equal(bad.stderr.includes('secret-invalid'),false);
    console.log('PASS canonical backup parser migration v12 -> v13, future version rejection and no-data-leak summary');
} finally {rmSync(dir,{recursive:true,force:true});}
