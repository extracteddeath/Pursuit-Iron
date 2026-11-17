import assert from 'node:assert/strict';

// Release-only certification: executable web Machine A/B facts remain in the
// ordinary draft source gate; exact offline digests remain mandatory for a
// distributable build, and are never waived by the draft UI workflow.
// Release certification must include every cached module and static asset.
import fs from 'node:fs';
import crypto from 'node:crypto';
const hash = value => crypto.createHash('sha256').update(value).digest('hex');
const worker = fs.readFileSync(new URL('../sw.js', import.meta.url),'utf8');
const start = worker.indexOf('const SHELL=');
const end = worker.indexOf('];',start);
assert.ok(start>=0 && end>start,'service-worker shell list missing');
const shell = JSON.parse(worker.slice(start+'const SHELL='.length,end+1));
const cacheHash = hash(shell.sort().map(path=>
    path+':'+hash(fs.readFileSync(new URL('../'+path.slice(2),import.meta.url)))+
    String.fromCharCode(10)
).join('')).slice(0,16);
console.log('MACHINE_A_B_SHELL_REVISION='+cacheHash);
const release=JSON.parse(fs.readFileSync(new URL('../RELEASE_MANIFEST.json',import.meta.url),'utf8'));
const profile=JSON.parse(fs.readFileSync(new URL('../BUILD_PROFILE.json',import.meta.url),'utf8'));
assert.equal(profile.cache,release.cache,'web cache must belong to the exact shipped release');
assert.equal(profile.cache.endsWith('-r'+cacheHash),true,
    'new physical-machine modules must be included in exact offline shell digest');
assert.ok(worker.includes('const CACHE="'+profile.cache+'"'),
    'the service worker must use the certified release cache');
for(const [file,digest] of Object.entries(release.runtimeFiles))
    assert.equal(hash(fs.readFileSync(new URL('../'+file,import.meta.url))),digest,
        'uncertified production runtime: '+file);
for(const [file,digest] of Object.entries(release.uiFiles))
    assert.equal(hash(fs.readFileSync(new URL('../'+file,import.meta.url))),digest,
        'uncertified production UI: '+file);
const aggregate=Object.keys(release.runtimeFiles).sort()
    .map(file=>file+':'+release.runtimeFiles[file]+String.fromCharCode(10)).join('');
assert.equal(hash(aggregate),release.runtimeAggregate,'runtime aggregate mismatch');
console.log('MACHINE_A_B_RELEASE_CERTIFIED build='+release.build+
    ' modules='+Object.keys(release.runtimeFiles).length);

