import assert from 'node:assert/strict';
import { mkdtempSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
const dir = mkdtempSync(join(tmpdir(), 'pursuit-iron-backup-test-'));
try {
    const good = join(dir,'copy.json');
    writeFileSync(good, JSON.stringify({history:[{id:'private-id',sets:[{w:225,r:5}]}],custom:[],notes:'private note'}));
    const ok = spawnSync(process.execPath,['android-native/data-migration/inspect-backup.mjs',good],{encoding:'utf8'});
    assert.equal(ok.status,0,ok.stderr);
    assert.equal(JSON.parse(ok.stdout).fieldShapes.history.count,1);
    assert.equal(ok.stdout.includes('private-id'),false);
    assert.equal(ok.stdout.includes('private note'),false);
    const invalid = join(dir,'invalid.json');
    writeFileSync(invalid,'{');
    const bad = spawnSync(process.execPath,['android-native/data-migration/inspect-backup.mjs',invalid],{encoding:'utf8'});
    assert.notEqual(bad.status,0);
    console.log('PASS backup copy inspection: read-only structure, no record values, malformed input rejected');
} finally { rmSync(dir,{recursive:true,force:true}); }
