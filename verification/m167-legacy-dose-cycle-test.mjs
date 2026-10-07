import { productionSource } from './production-source.mjs';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { computeCell, cycleConfigForStandaloneProgram, mergeStandaloneIntoGeneratedCycle, advanceLegacyFirstCycleBlock } from '../modules/App.js';

const seku={
 id:'abzg9ot',name:'SekuFit',engineV:4,custom:true,weeks:10,
 config:{name:'SekuFit',experience:'intermediate',goal:'both',split:'custom',days:1,session:'s90',weeks:10,progression:'auto',deload:true,autoVolume:true},
 days:[
  {id:'w3yrssi',label:'Upper',type:'custom',exercises:['low-inc-bb-bench','wide-pulldown','cable-lat-raise','inc-curl','chest-row','oh-cable-ext','lat-raise'],primaryIndex:0},
  {id:'8r809t4',label:'Lower',type:'custom',exercises:['deadlift','hack-squat','seated-curl','seated-calf','hip-abduction','cable-woodchop','wrist-curl'],primaryIndex:0},
  {id:'wsf4rib',label:'Push',type:'custom',exercises:['bb-bench','machine-shoulder','lat-raise','machine-press','cable-fly','oh-cable-ext','neck-extension'],primaryIndex:0},
  {id:'5b0y2v6',label:'Pull',type:'custom',exercises:['bb-row','lat-pulldown','face-pull','ez-cable-curl','hammer','rear-fly','machine-crunch','bb-shrug'],primaryIndex:0},
  {id:'smzmv8u',label:'Legs',type:'custom',exercises:['back-squat','seated-curl','walking-lunge','leg-ext','seated-calf','lying-db-ext','machine-hip-thrust'],primaryIndex:0}
 ],
 overrides:{},
 slotBias:{'8r809t4:4':1,'8r809t4:3':2,'smzmv8u:4':2,'wsf4rib:6':1},
 autoBias:{'8r809t4:2':1,'8r809t4:0':1,'8r809t4:4':1,'8r809t4:3':1,'8r809t4:5':1,'8r809t4:6':2}
};
const lower=seku.days[1];
const lowerSets=lower.exercises.map((id,slot)=>computeCell(seku,lower,id,slot,2).sets);
assert.deepEqual(lowerSets,[5,3,4,5,5,4,5],'SekuFit Lower must restore its saved role baseline + slotBias + autoBias dose');
assert.equal(lowerSets.reduce((a,b)=>a+b,0),31,'SekuFit Lower week must return to 31 working sets, not 21 all-3 sets');

const neutral={id:'new-custom',custom:true,config:{goal:'both',experience:'intermediate'},overrides:{},days:[{id:'d',label:'Custom',type:'custom',exercises:['deadlift'],primaryIndex:0}]};
assert.equal(computeCell(neutral,neutral.days[0],'deadlift',0,1).sets,3,'new custom plans without legacy bias maps keep neutral defaults');

const cfg=cycleConfigForStandaloneProgram(seku,'SekuFit Cycle');
assert.equal(cfg.days,5,'future cycle generation must use the actual five-day roster, not stale config.days=1');
assert.equal(cfg.split,'ulppl','Upper/Lower/Push/Pull/Legs custom roster must bridge to ULPPL for future engine blocks');

const fake={
 cycle:{id:'c1',engineSource:'pursuit-next',activeBlock:0,blockIds:['g0','g1','g2'],blockMeta:[{id:'g0',label:'Build',preview:false},{id:'g1',label:'Strength',preview:true},{id:'g2',label:'Peak',preview:true}],nextEngineCycle:{baseConfig:cfg}},
 blocks:[{id:'g0',config:{}},{id:'g1',config:{},cycleId:'c1'},{id:'g2',config:{},cycleId:'c1'}],baseRequest:{}
};
const merged=mergeStandaloneIntoGeneratedCycle(seku,fake);
assert.deepEqual(merged.cycle.blockIds,['abzg9ot','g1','g2']);
assert.equal(merged.currentProgram.engineSource,undefined,'legacy current block must not be relabelled as Next-owned');
assert.equal(computeCell(merged.currentProgram,merged.currentProgram.days[1],'deadlift',0,2).sets,5,'cycle conversion must not change current SekuFit prescription');
const step=advanceLegacyFirstCycleBlock(merged.cycle,merged.allBlocks);
assert.equal(step.cycle.activeBlock,1);
assert.equal(step.nextProgram.id,'g1');
assert.equal(step.cycle.blockMeta[1].preview,false);

const app=productionSource();
assert.ok(app.includes('!p.config?.endless && !p.cycleId && onConvertCycle'),'fixed standalone plans must expose Turn into training cycle');
assert.ok(!app.includes('!p.config?.endless && !p.cycleId && p.engineSource === "pursuit-next" && onConvertCycle'),'cycle action must not be hidden solely because a plan predates Next');
console.log('PASS M167 legacy-dose/cycle regression: SekuFit 31-set Lower restored; standalone cycle action restored; exact legacy Block 1 preserved; first transition activates audited Next future block.');
