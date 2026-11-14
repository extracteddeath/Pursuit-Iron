import assert from 'node:assert/strict';
import {readFileSync,writeFileSync} from 'node:fs';
import {dirname,join,resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {EXERCISES} from '../../../modules/next-engine/exercise-db.js';

/**
 * Deterministic, source-linked REVIEW REGISTRY for new native exercise
 * definitions. This runs only in authoring/CI. Kotlin has NO JS runtime,
 * imported web behavior, auto-calibrated load, or alternate coach.
 *
 * Every manifest entry is an explicit human-authored physical-equipment and
 * muscle-credit decision. The active web source is identity/provenance, NOT
 * medical science or permission to transfer loading/history.
 *
 * --check: fail when active web source, manifest, or committed Kotlin drift
 * --write: explicitly regenerate Kotlin definitions from the vetted manifest
 * --self-test: confirm fail-closed checks reject corrupted evidence
 */
const root=resolve(dirname(fileURLToPath(import.meta.url)),'../../..');
const nativePath=join(root,
 'android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/StarterExerciseTranche.kt');
const manifestPath=join(root,
 'android-native/kotlin-engine/verification/reviewed-catalog-expansion.json');
const BEGIN='        // BEGIN REVIEWED_WEB_EXPANSION_V2';
const END='        // END REVIEWED_WEB_EXPANSION_V2';
const allowedMuscles=new Set(['CHEST','LATS','UPPER_BACK','FRONT_DELTS',
 'SIDE_DELTS','REAR_DELTS','TRICEPS','BICEPS','BRACHIALIS','WRIST_FLEXORS',
 'WRIST_EXTENSORS','QUADS','HAMSTRINGS','GLUTES','CALVES','ABS']);
const focusTypes=new Set(['press','pull','lower','all']);
const patterns=new Set(['HORIZONTAL_PRESS','VERTICAL_PRESS','HORIZONTAL_PULL',
 'VERTICAL_PULL','SQUAT','HINGE','ISOLATION','CORE']);
// Only explicit source tokens for a physical resistance station qualify.
// A vague "machine" token remains provisional and still requires an exact
// individually identified, verified machine stock key in Kotlin.
const machineSource=new Set(['machinerow','legcurl','calfmachine','machine',
 'machinepress','machineshoulder','pecdeck','machinelatraise','machinecurl',
 'machineext','beltsquat','hipthrustmachine','machinecrunch',
 'legext','legpress','hacksquat','reversehyper','machinepullover','kickback']);
const q=x=>x===null?'null':JSON.stringify(x);

export function renderEntry(row) {
 const a=row.review,rep=row.source.practicalReps;
 const impl=a.implement==='straight-bar'?'BAR':
   a.implement==='dumbbell-rack'?'DBS':q(a.implement);
 const basis=a.basis?'LoadBasis.'+a.basis:'null';
 const fixtures=a.fixtures.length?'setOf('+a.fixtures.map(q).join(',')+')':'emptySet()';
 const credits=['direct(TrainingMuscle.'+a.direct+')',
   ...a.indirect.map(s=>{
     const [muscle,val]=s.split(':');
     return 'indirect(TrainingMuscle.'+muscle+','+q(val)+')';
   })];
 return '        entry('+q(row.id)+','+q(a.family)+','+q(a.variant)+',\n'+
   '            ComposerPattern.'+a.pattern+',ComposerRole.'+a.role+','+a.focus+',\n'+
   '            '+impl+','+basis+','+fixtures+','+rep[0]+'..'+rep[1]+',\n'+
   '            '+q(a.note)+',\n'+
   '            '+credits.join(',\n            ')+
   (a.attachment?',attachmentId='+q(a.attachment):'')+')';
}

function validateRows(manifest,web=EXERCISES) {
 assert.equal(manifest.schemaVersion,1,'review registry schema drift');
 assert.equal(manifest.originalStablePrefixCount,49);
 assert.ok(Array.isArray(manifest.records)&&manifest.records.length>0);
 assert.ok(Array.isArray(web)&&web.length>0);
 const webById=new Map(web.map(x=>[x.id,x]));
 assert.equal(webById.size,web.length,'active web source has duplicate IDs');
 const usedIds=new Set(),familyVariants=new Set();
 for(const row of manifest.records) {
   assert.match(row.id,/^[a-z0-9][a-z0-9_-]+$/);
   assert.ok(!usedIds.has(row.id),'duplicate reviewed ID '+row.id);
   usedIds.add(row.id);
   const live=webById.get(row.id);
   assert.ok(live,'reviewed ID is not in ACTIVE web catalog: '+row.id);
   assert.deepEqual(row.source,{
     movementFamily:live.movementFamily,
     equipment:live.equipment,
     practicalReps:live.practicalReps,
     source:live.source??null
   },'live source metadata drift for '+row.id+'; renew author review');
   const a=row.review;
   assert.ok(a.family?.trim()&&a.variant?.trim());
   const familyVariant=a.family+'|'+a.variant;
   assert.ok(!familyVariants.has(familyVariant),'duplicate provisional variant '+familyVariant);
   familyVariants.add(familyVariant);
   assert.ok(patterns.has(a.pattern)&&['PRIMARY','SECONDARY','ACCESSORY'].includes(a.role));
   assert.ok(focusTypes.has(a.focus));
   assert.ok(a.note?.length>=45 && !/[<>\r]/.test(a.note),'missing authored physical/muscle rationale '+row.id);
   assert.ok(allowedMuscles.has(a.direct),'unsupported anatomy '+row.id);
   assert.ok(Array.isArray(a.indirect));
   const credits=new Set([a.direct]);
   for(const credit of a.indirect) {
     assert.match(credit,/^[A-Z_]+:0\.(?:25|5|75)$/);
     const muscle=credit.split(':')[0];
     assert.ok(allowedMuscles.has(muscle)&&!credits.has(muscle),
       'duplicate/unsupported muscle credit '+row.id);
     credits.add(muscle);
   }
   assert.ok(Array.isArray(a.fixtures)&&a.fixtures.every(x=>typeof x==='string'&&x.length>1));
   assert.equal(new Set(a.fixtures).size,a.fixtures.length);
   assert.ok(Number.isInteger(row.source.practicalReps[0])&&
     Number.isInteger(row.source.practicalReps[1])&&
     row.source.practicalReps[0]>0&&
     row.source.practicalReps[0]<=row.source.practicalReps[1]);
   const equipment=row.source.equipment;
   if(a.basis===null) {
     assert.equal(a.implement,null,'unweighted cannot have phantom implement '+row.id);
     assert.equal(a.attachment,null);
     // Current canonical web source uses "pullup" and "dip" for
    // unweighted bar/station exercises. Accept those ONLY with verified
    // physical fixture IDs, never a guessed stack or a generic machine.
    const unweightedSource=equipment.length===1 && (
      equipment[0]==='bodyweight' ||
      equipment[0]==='pullup' && a.fixtures.includes('pullup-bar') ||
      equipment[0]==='dip' && a.fixtures.includes('dip-station')
    );
    assert.ok(unweightedSource,
       'do not infer unweighted loading from an ambiguous web equipment list: '+row.id);
   } else {
     assert.ok(typeof a.implement==='string'&&a.implement.length>4,
       'loaded movement needs physical implement '+row.id);
     if(a.basis==='BARBELL_TOTAL')assert.ok(
       a.implement==='straight-bar'||a.implement==='ez-curl-bar',
       'no invented barbell mass');
     else if(a.basis==='DUMBBELL_PER_HAND') {
       assert.equal(a.implement,'dumbbell-rack');
       assert.deepEqual(a.attachment,null);
       assert.ok(equipment.includes('dumbbell'));
     } else if(a.basis==='SMITH_TOTAL') {
       assert.ok(equipment.includes('smith'));
       assert.ok(a.implement.endsWith('-station'));
     } else if(a.basis==='EXERCISE_OVERRIDE') {
       assert.ok(equipment.some(x=>machineSource.has(x)),'no verified machine source '+row.id);
       assert.ok(a.implement.endsWith('-station')||a.implement.endsWith('-machine'));
     } else if(a.basis==='CABLE_STACK') {
       assert.ok(equipment.includes('cable'));
       assert.equal(a.implement,'cable-station');
       assert.ok(typeof a.attachment==='string'&&a.attachment.length>2,
         'cable movement requires an exact physical attachment '+row.id);
     } else throw Error('invalid or unsupported basis '+row.id);
     if(a.basis!=='CABLE_STACK')assert.equal(a.attachment,null);
     if(a.basis==='BARBELL_TOTAL')assert.ok(
       equipment.includes('barbell')||equipment.includes('ezbar'));
     if(a.basis==='BARBELL_TOTAL'&&a.implement==='ez-curl-bar')
       assert.ok(equipment.includes('ezbar'),'EZ bar source absent '+row.id);
   }
 }
 return manifest.records.length;
}

export function validateReviewedCatalogExpansion(manifest,source,web=EXERCISES) {
 const count=validateRows(manifest,web);
 assert.equal(source.split(BEGIN).length,2,'missing/duplicate generated start');
 assert.equal(source.split(END).length,2,'missing/duplicate generated end');
 const from=source.indexOf(BEGIN)+BEGIN.length,to=source.indexOf(END);
 assert.ok(to>from);
 const expected='\n'+manifest.records.map(renderEntry).join(',\n')+'\n';
 assert.equal(source.slice(from,to),expected,
   'committed Kotlin must exactly match the explicit review registry');
 const declarations=[...source.matchAll(/\bentry\("([a-z0-9_-]+)"/g)].map(x=>x[1]);
 assert.equal(new Set(declarations).size,declarations.length,
   'native starter contains conflicting exercise IDs');
 const variants=[...source.matchAll(/\\bentry\\("([^"]+)","([^"]+)","([^"]+)"/g)]
   .map(x=>x[2]+'|'+x[3]);
 assert.equal(new Set(variants).size,variants.length,
   'preserved curated and reviewed extensions must have unique physical family/variant keys');
 assert.deepEqual(declarations.slice(49),manifest.records.map(x=>x.id),
   'new source IDs must append after the preserved first 49');
 assert.ok(source.includes('require(definitions.size=='+(49+count)+')'),
   'Kotlin exact source count must match reviewed registry');
 assert.ok(source.includes('mayTransferPreviousLoad:Boolean=false')&&
   !expected.includes('mayTransferPreviousLoad'),
   'reviewed IDs may never implicitly transfer historical loading');
 return {originalStablePrefixCount:49,additions:count,total:49+count};
}

export function writeReviewedCatalogExpansion(manifest,source,web=EXERCISES) {
 const count=validateRows(manifest,web);
 const from=source.indexOf(BEGIN),to=source.indexOf(END);
 assert.ok(from>=0&&to>from,'review markers missing');
 const replacement=BEGIN+'\n'+manifest.records.map(renderEntry).join(',\n')+'\n'+END;
 const previous=requireCount(source);
 return source.slice(0,from)+replacement+source.slice(to+END.length)
   .replace('require(definitions.size=='+previous+')',
     'require(definitions.size=='+(49+count)+')');
}
function requireCount(source) {
 const m=source.match(/require\(definitions\.size==(\d+)\)/);
 assert.ok(m,'missing checked Kotlin catalog count');return Number(m[1]);
}

function selfTest() {
 const m=JSON.parse(readFileSync(manifestPath,'utf8'));
 const src=readFileSync(nativePath,'utf8');
 assert.equal(validateReviewedCatalogExpansion(m,src).total,281);
 const changed=structuredClone(m);
 changed.records[0].source.practicalReps=[1,100];
 assert.throws(()=>validateReviewedCatalogExpansion(changed,src),/source metadata drift/);
 const forged=structuredClone(m);
 forged.records[0].review.basis=null;
 assert.throws(()=>validateReviewedCatalogExpansion(forged,src),/unweighted cannot/);
 const altered=src.replace('BEGIN REVIEWED_WEB_EXPANSION_V2',
   'BEGIN REVIEWED_WEB_EXPANSION_TAMPER');
 assert.throws(()=>validateReviewedCatalogExpansion(m,altered),/generated start/);
 const removed=structuredClone(m); removed.records.pop();
 assert.throws(()=>validateReviewedCatalogExpansion(removed,src),
   /committed Kotlin/);
 console.log('PASS source-linked catalog review manifest rejects drift and phantom equipment');
}

if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url)) {
 const args=new Set(process.argv.slice(2));
 if(args.has('--self-test'))selfTest();
 else {
   const manifest=JSON.parse(readFileSync(manifestPath,'utf8'));
   const native=readFileSync(nativePath,'utf8');
   if(args.has('--write')) {
     const rewritten=writeReviewedCatalogExpansion(manifest,native);
     writeFileSync(nativePath,rewritten);
     console.log('Rewrote committed Kotlin-only catalog candidate; review changes before merging.');
   } else if(args.has('--check')) {
     console.log('PASS reviewed manifest and Kotlin-only extension:',
       validateReviewedCatalogExpansion(manifest,native));
   } else throw Error('Choose --check, --write or --self-test');
 }
}
