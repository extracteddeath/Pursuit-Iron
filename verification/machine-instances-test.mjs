import assert from 'node:assert/strict';
import { addMachineVariant, editMachineVariant, setDefaultMachineVariant, resolveMachineVariant,
    stampMachineSet, machineHistory, latestMachineSet, comparableMachineScope,
    historyForMachineRecommendation, LEGACY_MACHINE_ID } from '../modules/next-engine/machine-instances.js';

const gymX = { id:'gymX' }, gymY={ id:'gymY' };
const A={id:'A',label:'Machine A',loading:{unit:'lb',minimum:10,increment:5,maximum:300}};
const B={id:'B',label:'Machine B',loading:{unit:'lb',minimum:5,increment:10,maximum:250}};
let x=addMachineVariant(gymX,'chest_press',A,true);
x=addMachineVariant(x,'chest_press',B);
let y=addMachineVariant(gymY,'chest_press',B,true);
y=addMachineVariant(y,'chest_press',A);
assert.equal(resolveMachineVariant(x,'chest_press').id,'A');
assert.equal(resolveMachineVariant(y,'chest_press').id,'B');
assert.equal(resolveMachineVariant(x,'chest_press','B').loading.increment,10);
assert.throws(()=>addMachineVariant(x,'chest_press',A),/Duplicate/);
assert.throws(()=>setDefaultMachineVariant(x,'chest_press','missing'),/active/);

const row=(id,date,gymId,dayId,sets)=>({id,programId:'p1',date,gymId,dayId,unit:'lb',
    perf:{chest_press:{sets}}});
const set=(w,r,id)=>stampMachineSet({w,r,done:true},{gymId:'gymX',exerciseId:'chest_press',machineInstanceId:id});
const history=[
    row('a1',1,'gymX','upper',[set(200,8,'A')]),
    row('b1',2,'gymX','upper',[set(140,8,'B')]),
    row('mixed',3,'gymX','upper',[set(145,9,'B'),set(205,8,'A')]),
    row('a-push',4,'gymX','push',[set(215,7,'A')]),
    row('gymY-b',5,'gymY','upper',[set(100,8,'B')]),
    row('legacy',6,'gymX','upper',[{w:190,r:8,done:true}])
];
assert.deepEqual(machineHistory(history,{gymId:'gymX',exerciseId:'chest_press',machineInstanceId:'A',dayId:'upper'}).map(s=>s.weight),[200,205]);
assert.deepEqual(machineHistory(history,{gymId:'gymX',exerciseId:'chest_press',machineInstanceId:'B',dayId:'upper'}).map(s=>s.weight),[140,145]);
assert.equal(latestMachineSet(history,{gymId:'gymX',exerciseId:'chest_press',machineInstanceId:'A',dayId:'upper'}).weight,205);
assert.equal(latestMachineSet(history,{gymId:'gymX',exerciseId:'chest_press',machineInstanceId:LEGACY_MACHINE_ID,dayId:'upper'}).weight,190);
assert.equal(machineHistory(history,{gymId:'gymY',exerciseId:'chest_press',machineInstanceId:'B'}).length,1);
assert.equal(comparableMachineScope({gymId:'gymX',exerciseId:'chest_press',machineInstanceId:'A',dayId:'upper'},
    {gymId:'gymX',exerciseId:'chest_press',machineInstanceId:'B',dayId:'upper'}),false);
assert.equal(comparableMachineScope({gymId:'gymX',exerciseId:'chest_press',machineInstanceId:'A',dayId:'upper'},
    {gymId:'gymX',exerciseId:'chest_press',machineInstanceId:'A',dayId:'push'}),false);
const scoped=historyForMachineRecommendation(history,'chest_press','A','gymX');
assert.deepEqual(scoped.map(h=>h.perf.chest_press?.sets.length),[1,0,1,1,0,0]);
assert.equal(scoped[2].perf.chest_press.sets[0].w,205);
assert.equal(history[2].perf.chest_press.sets.length,2,'filter never mutates saved sets');
x=setDefaultMachineVariant(x,'chest_press','B');
x=editMachineVariant(x,'chest_press','A',{active:false});
assert.equal(resolveMachineVariant(x,'chest_press').id,'B');
assert.equal(resolveMachineVariant(x,'chest_press','A',{forNewWorkout:false}).active,false);
assert.equal(machineHistory(history,{gymId:'gymX',exerciseId:'chest_press',machineInstanceId:'A'}).length,3);
console.log('Machine-instance web engine: 18 invariants pass.');

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

