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
const BAND_BEGIN='        // BEGIN_REVIEWED_RATED_BANDS_V1';
const BAND_END='        // END_REVIEWED_RATED_BANDS_V1';
const ratedBandManifest=JSON.parse(readFileSync(join(root,
 'android-native/kotlin-engine/verification/rated-band-source-20261117.json'),'utf8'));
const ratedBandRequiredIds=[
 'band_lateral_raise','band-curl','banded-tib-raise','band-ulnar-dev',
 'lateral-walk','band-leg-curl','band-pulldown','rear-band-pull-apart',
 'band-good-morning','band-hip-thrust','band-adduction','band-abduction',
 'bands-face-pull','bands-squat','bands-tricep-ext','bands-chest-press'
];
const requestedWebVariants=[
  ['high-inc-db-press','High-Incline Dumbbell Press',['dumbbell','inclinebench'],[8,12]],
  ['bottom-half-db-fly','Bottom-Half Dumbbell Fly',['dumbbell','bench'],[12,18]],
  ['deficit-pendlay-row','Deficit Pendlay Row',['barbell'],[5,8]],
  ['low-to-high-face-pull','Low-to-High Face Pull',['cable'],[12,20]],
  ['leaning-cable-ohext','Leaning Cable Overhead Triceps Extension',['cable'],[10,15]],
  ['ad-press-barbell','AD Press (Barbell)',['barbell','inclinebench','rack'],[6,12]],
  ['ad-press-smith','AD Press (Smith Machine)',['smith','inclinebench'],[6,12]]
];
const requestedIds=requestedWebVariants.map(x=>x[0]);
// Every entry below is a concrete SOURCE fact from the active web roster.
// Kotlin separately reviews resistance units, physical implements and muscles;
// old "bodyweight" / "dumbbell" source tokens can be inaccurate and MUST NOT
// automatically authorize bodyweight or transferable loading.
const waveEightWebSource=[
  ['dumbbell_crunch','Dumbbell Crunch',['dumbbell'],[8,30],null,null],
  ['cable-radial-dev','Cable Radial Wrist Deviation',['cable'],[12,20],'legacy_v661','wrist_deviators'],
  ['hammer-radial-dev','Hammer Radial Wrist Deviation',['bodyweight'],[12,20],'legacy_v661','wrist_deviators'],
  ['hammer-ulnar-dev','Hammer Ulnar Wrist Deviation',['bodyweight'],[12,20],'legacy_v661','wrist_deviators'],
  ['wrist-roller','Wrist Roller',['bodyweight'],[10,15],'legacy_v661','wrist_extensors'],
  ['plate-front-raise','Plate Front Raise',['dumbbell'],[12,20],'legacy_v661','front_delts']
];
const waveEightIds=waveEightWebSource.map(x=>x[0]);
const waveNineWebSource=[
  ['svend-press','Svend Press',['bodyweight'],[12,20],'legacy_v661',null],
  ['gripper','Hand Gripper',['bodyweight'],[10,20],'legacy_v661','grip']
];
const waveNineIds=waveNineWebSource.map(x=>x[0]);

const requestedFixture=JSON.parse(readFileSync(join(root,
 'android-native/kotlin-engine/verification/requested-web-variants-20261117.json'),'utf8'));
assert.equal(requestedFixture.schemaVersion,1,'reviewed request fixture schema drift');
assert.equal(requestedFixture.sourcePullRequest,671,'web provenance PR drift');
assert.deepEqual(requestedFixture.records.map(x=>x.id),requestedIds,
 'seven-source provenance snapshot must retain exact order and IDs');
const allowedMuscles=new Set(['CHEST','LATS','UPPER_BACK','FRONT_DELTS',
 'SIDE_DELTS','REAR_DELTS','TRICEPS','BICEPS','BRACHIALIS','WRIST_FLEXORS',
 'WRIST_EXTENSORS','QUADS','HAMSTRINGS','GLUTES','CALVES','ABS',
 'HIP_ABDUCTORS','HIP_ADDUCTORS','SPINAL_ERECTORS','TRAPS',
 'SERRATUS_ANTERIOR','TIBIALIS_ANTERIOR']);
const focusTypes=new Set(['press','pull','lower','all']);
const patterns=new Set(['HORIZONTAL_PRESS','VERTICAL_PRESS','HORIZONTAL_PULL',
 'VERTICAL_PULL','SQUAT','HINGE','ISOLATION','CORE']);
// Only explicit source tokens for a physical resistance station qualify.
// A vague "machine" token remains provisional and still requires an exact
// individually identified, verified machine stock key in Kotlin.
const machineSource=new Set(['machinerow','legcurl','calfmachine','machine',
 'machinepress','machineshoulder','pecdeck','machinelatraise','machinecurl',
 'machineext','beltsquat','hipthrustmachine','machinecrunch',
 'legext','legpress','hacksquat','reversehyper','machinepullover','kickback',
 'abduction','adduction','machineshrug']);
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
   const subregion=({'SERRATUS_ANTERIOR':'serratus','TIBIALIS_ANTERIOR':'tibialis'})[a.direct];
   if(subregion) {
     assert.equal(row.sourceSubregion,subregion,'explicit subregion evidence missing '+row.id);
     assert.equal(live.legacySubregion,subregion,'live anatomical subregion drift '+row.id);
   } else assert.equal(row.sourceSubregion??null,null,'subregion evidence must match the declared target '+row.id);
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
    // Legacy bench-only metadata cannot prove a load or a specific bench.
    // These three reviewed variants are explicitly unweighted and demand
    // the precise supported physical station, not an inferred machine/plate.
    // Do not loosen this to ALL bench-family source entries: loaded benches,
    // Smith variants and future unknowns must fail closed.
    const reviewedUnweightedFacility=({
      'decline-situp':['declinebench','decline-bench'],
      'back-ext-45':['bench','45-degree-back-extension-station'],
      'prone-y-raise':['inclinebench','incline-bench']
    })[row.id];
    const confirmedFacilityVariant=reviewedUnweightedFacility!=null &&
      equipment.length===1 &&
      equipment[0]===reviewedUnweightedFacility[0] &&
      a.fixtures.includes(reviewedUnweightedFacility[1]) &&
      a.fixtures.length===1;
    const unweightedSource=equipment.length===1 && (
      equipment[0]==='bodyweight' ||
      equipment[0]==='pullup' && a.fixtures.includes('pullup-bar') ||
      equipment[0]==='dip' && a.fixtures.includes('dip-station') ||
      confirmedFacilityVariant
    );
    assert.ok(unweightedSource,
       'do not infer unweighted loading from an ambiguous web equipment list: '+row.id);
   } else {
     assert.ok(typeof a.implement==='string'&&a.implement.length>4,
       'loaded movement needs physical implement '+row.id);
     if(a.basis==='BARBELL_TOTAL')assert.ok(
       ['straight-bar','ez-curl-bar','trap-hex-bar'].includes(a.implement),
       'no invented barbell mass');
     else if(a.basis==='DUMBBELL_PER_HAND') {
       assert.equal(a.implement,'dumbbell-rack');
       assert.deepEqual(a.attachment,null);
       assert.ok(equipment.includes('dumbbell'));
     } else if(a.basis==='KETTLEBELL_EACH') {
       assert.ok(equipment.includes('kettlebell'));
       assert.equal(a.implement,({'kb-swing':'single-kettlebell',
         'kb-gorilla-row':'matched-kettlebell-pair'})[row.id]);
       assert.equal(a.attachment,null);
     } else if(a.basis==='LANDMINE_ADDED_PLATES') {
       assert.deepEqual(equipment,['landmine']);
       assert.equal(a.implement,'landmine-station');
       assert.equal(a.attachment,null);
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
       equipment.includes({'straight-bar':'barbell','ez-curl-bar':'ezbar',
         'trap-hex-bar':'trapbar'}[a.implement]));
     if(a.basis==='BARBELL_TOTAL'&&a.implement==='ez-curl-bar')
       assert.ok(equipment.includes('ezbar'),'EZ bar source absent '+row.id);
     if(a.basis==='BARBELL_TOTAL'&&a.implement==='trap-hex-bar')
       assert.ok(equipment.includes('trapbar'),'Trap/hex bar source absent '+row.id);
   }
 }
 return manifest.records.length;
}

/**
 * Second manually reviewed source-linked inventory. These are not
 * interchangeable dumbbell masses: the Kotlin domain requires real exact
 * manufacturer-rated nominal tension for every issued band selection.
 * Prohibited hybrid/assistance and non-rep methods have not been included.
 */
function validateRatedBandTranche(fixture,source,web) {
 assert.equal(fixture.schemaVersion,1,'rated band source schema drift');
 assert.deepEqual(fixture.records.map(x=>x.id),ratedBandRequiredIds,
   'rated band reviewer coverage or append order changed');
 const byId=new Map(web.map(x=>[x.id,x]));
 const seen=new Set();
 for(const row of fixture.records) {
   const actual=byId.get(row.id);
   assert.ok(actual,'reviewed rated-band ID missing from active web: '+row.id);
   assert.equal(row.name,actual.name,'rated-band source name drift: '+row.id);
   assert.deepEqual(row.source,{
     movementFamily:actual.movementFamily,
     equipment:actual.equipment,
     practicalReps:actual.practicalReps,
     source:actual.source??null,
     legacySubregion:actual.legacySubregion??null
   },'rated-band source identity/rep/metadata drift: '+row.id);
   assert.deepEqual(actual.equipment,['bands'],'rated-band equipment drift: '+row.id);
   assert.equal(actual.flags?.bodyweight,false,
     'hybrid band and bodyweight cannot be authored as numeric tension: '+row.id);
   const a=row.review;
   assert.equal(a.implement,'rated-resistance-band-set');
   assert.equal(a.basis,'EXERCISE_OVERRIDE');
   assert.ok(patterns.has(a.pattern));
   assert.ok(['PRIMARY','SECONDARY','ACCESSORY'].includes(a.role));
   assert.ok(focusTypes.has(a.focus));
   assert.ok(allowedMuscles.has(a.direct));
   assert.ok(a.note?.length>65 && /band/i.test(a.note),
     'missing nominal-band physical rationale '+row.id);
   assert.ok(Array.isArray(a.fixtures)&&a.fixtures.length<=4);
   assert.ok(a.fixtures.every(x=>/^[a-z0-9-]+$/.test(x)));
   assert.ok(Array.isArray(a.indirect)&&a.indirect.every(x=>
     /^[A-Z_]+:0\.(?:25|5|75)$/.test(x)));
   const muscles=[a.direct,...a.indirect.map(x=>x.split(':')[0])];
   assert.equal(new Set(muscles).size,muscles.length);
   assert.ok(muscles.every(x=>allowedMuscles.has(x)));
   const familyVariant=a.family+'|'+a.variant;
   assert.ok(!seen.has(familyVariant),'duplicate reviewed band variant '+row.id);
   seen.add(familyVariant);
 }
 assert.equal(source.split(BAND_BEGIN).length,2,'rated-band source fence missing');
 assert.equal(source.split(BAND_END).length,2,'rated-band end fence missing');
 const from=source.indexOf(BAND_BEGIN)+BAND_BEGIN.length;
 const to=source.indexOf(BAND_END);
 assert.ok(to>from);
 assert.equal(source.slice(from,to),
   '\n'+fixture.records.map(renderEntry).join(',\n')+'\n',
   'committed Kotlin band definitions differ from reviewed source fixture');
}

export function validateReviewedCatalogExpansion(manifest,source,web=EXERCISES) {
 const count=validateRows(manifest,web);
 validateRatedBandTranche(ratedBandManifest,source,web);
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
 const variants=[...source.matchAll(/\bentry\("([^"]+)","([^"]+)","([^"]+)"/g)]
   .map(x=>x[2]+'|'+x[3]);
 assert.equal(new Set(variants).size,variants.length,
   'preserved curated and reviewed extensions must have unique physical family/variant keys');
 assert.deepEqual(declarations.slice(49,49+count),
   manifest.records.map(x=>x.id),
   'generated reviewed catalog must preserve its exact 49-ID prefix and review registry');
 assert.deepEqual(declarations.slice(49+count,49+count+requestedIds.length),
   requestedIds,'original seven reviewed request IDs must be append-stable');
 assert.deepEqual(
   declarations.slice(49+count+requestedIds.length,
     49+count+requestedIds.length+waveEightIds.length),
   waveEightIds,'earlier six reviewed physical implements must remain stable');
 const beforeWaveNine=49+count+requestedIds.length+waveEightIds.length;
 assert.deepEqual(declarations.slice(beforeWaveNine,beforeWaveNine+waveNineIds.length),
   waveNineIds,'two wave-nine real implements must remain append-stable');
 assert.deepEqual(declarations.slice(beforeWaveNine+waveNineIds.length),
   [...ratedBandRequiredIds,'spanish-squat',
     'assisted-dip','assisted-chinup','assisted-pullup',
     'neck-extension','neck-curl','neck-harness','neck-lateral','weighted-pullup'],
   'only reviewed canonical additions and the distinct historical weighted pull-up may follow the rated bands');
 assert.match(source,/entry\("weighted-pullup","vertical_pull","added-load-overhand",\s*ComposerPattern\.VERTICAL_PULL,ComposerRole\.PRIMARY,pull,\s*"weighted-pullup-resistance",LoadBasis\.EXERCISE_OVERRIDE,\s*setOf\("pullup-bar","weighted-pullup-setup"\),6\.\.12,/,
   'Historical weighted pull-up requires separate verified added-load identity and setup');
 const liveById=new Map(web.map(ex=>[ex.id,ex]));
 const spanish=liveById.get('spanish-squat');
 assert.ok(spanish,'supported Spanish squat missing from web source');
 assert.equal(spanish.name,'Spanish Squat');
 assert.equal(spanish.movementFamily,'squat');
 assert.deepEqual(spanish.equipment,['bands']);
 assert.deepEqual(spanish.practicalReps,[12,20]);
 assert.equal(spanish.source,'legacy_v661');
 assert.match(source,
   /entry\("spanish-squat","squat","band-supported-spanish-squat",\s*ComposerPattern\.SQUAT,ComposerRole\.ACCESSORY,lower,\s*null,null,setOf\("spanish-squat-support"\),12\.\.20/,
   'Spanish squat must demand confirmed support and have no numeric external load');
 const assisted=[
   ['assisted-dip','Assisted Dip','horizontal_press','assisted-counterweight-dip'],
   ['assisted-chinup','Assisted Chin-Up','vertical_pull','assisted-counterweight-chinup'],
   ['assisted-pullup','Assisted Pull-Up','vertical_pull','assisted-counterweight-pullup']
 ];
 for(const [id,name,family,variant] of assisted) {
   const ex=liveById.get(id);
   assert.ok(ex,'reviewed assisted machine ID absent from canonical web: '+id);
   assert.equal(ex.name,name,'assisted web name drift: '+id);
   assert.deepEqual(ex.equipment,['assisted'],'assisted web equipment drift: '+id);
   assert.deepEqual(ex.practicalReps,[8,12],'assisted rep window drift: '+id);
   assert.equal(ex.movementFamily,family,'assisted movement family drift: '+id);
   assert.equal(ex.source,'legacy_v661','assisted source version drift: '+id);
   const exact='entry("'+id+'","'+family+'","'+variant+'"';
   const from=source.indexOf(exact);
   assert.ok(from>=0,'missing reviewed assisted identity: '+id);
   assert.match(source.slice(from,from+360),
     /LoadBasis\.ASSISTANCE_COUNTERWEIGHT,\s*setOf\("assisted-pullup-dip"\),8\.\.12/,
     'assisted movement must use verified counterweight and physical fixture: '+id);
 }
 // Neck v5: the web's ambiguous 'bodyweight' equipment token is
 // inconsistent with its WEIGHTED neck exercise names. No native workout
 // can assume unloaded neck reps or invent a head-loading resistance.
 const reviewedNeck=[
   ['neck-extension','Weighted Neck Extension','neck_extension',
     'supported-weighted-extension','NECK_EXTENSORS','neck-extension-device',
     'supported-neck-extension'],
   ['neck-curl','Weighted Neck Curl','neck_flexion',
     'supported-weighted-flexion','NECK_FLEXORS','neck-flexion-device',
     'supported-neck-flexion'],
   ['neck-harness','Neck Harness Raise','neck_extension',
     'harness-resisted-extension','NECK_EXTENSORS','neck-harness-device',
     'fitted-neck-harness'],
   ['neck-lateral','Lateral Neck Raise','neck_lateral_flexion',
     'supported-weighted-lateral','NECK_LATERAL_FLEXORS','neck-lateral-device',
     'supported-neck-lateral']
 ];
 for(const [id,name,family,variant,muscle,implement,facility] of reviewedNeck) {
   const ex=liveById.get(id);
   assert.ok(ex,'neck source exercise missing: '+id);
   assert.equal(ex.name,name,'neck source name drift: '+id);
   assert.equal(ex.movementFamily,'neck','neck source family drift: '+id);
   assert.deepEqual(ex.equipment,['bodyweight'],
     'ambiguous neck source tag changed; re-review physical loading: '+id);
   assert.equal(ex.flags?.bodyweight,false,
     'weighted neck work became unweighted: '+id);
   assert.deepEqual(ex.practicalReps,[12,20],
     'reviewed neck rep window drift: '+id);
   assert.equal(ex.source,'legacy_v661','neck source provenance changed: '+id);
   const from=source.indexOf('entry("'+id+'","'+family+'","'+variant+'"');
   assert.ok(from>=0,'neck source-backed native identity absent: '+id);
   const snippet=source.slice(from,from+900);
   assert.ok(snippet.includes('"'+implement+'",LoadBasis.EXERCISE_OVERRIDE,'),
     'neck real implement stock required: '+id);
   assert.ok(snippet.includes('setOf("'+facility+'"),12..20'),
     'neck supported position and actual rep band required: '+id);
   assert.ok(snippet.includes('direct(TrainingMuscle.'+muscle+')'),
     'neck cannot borrow trap/delt/ab volume credit: '+id);
 }
 // A new source catalog ID is not evidence of identical mechanics.
 // Fail closed when the web metadata, exact ID, equipment or rep band drifts.
 const fixtureById=new Map(requestedFixture.records.map(x=>[x.id,x]));
 for(const [id,name,equipment,reps] of requestedWebVariants) {
   const ex=fixtureById.get(id);
   assert.ok(ex,'requested variant missing provenance fixture: '+id);
   assert.equal(ex.name,name,'requested variant identity/name drift: '+id);
   assert.deepEqual(ex.equipment,equipment,'requested variant physical source drift: '+id);
   assert.deepEqual(ex.practicalReps,reps,'requested variant rep band drift: '+id);
   assert.equal(ex.source,'pursuit_requested_variant_20261117',
     'requested variant must retain explicit upstream provisional identity: '+id);
   // The Kotlin rebuild branch intentionally does not change the legacy
   // browser/JS execution catalog. If the upstream web source later contains
   // this ID, it must agree exactly with the pinned review snapshot.
   const live=liveById.get(id);
   if(live) {
     for(const key of ['name','equipment','movementFamily','practicalReps','source'])
       assert.deepEqual(live[key],ex[key],
         'live web source drift from separately reviewed Kotlin exercise: '+id+'/'+key);
   }
 }
 // The new six IDs are present in live source, but source equipment
 // labels are NOT assumed to be accurate physical loading conventions.
 for(const [id,name,equipment,reps,origin,subregion] of waveEightWebSource) {
   const ex=liveById.get(id);
   assert.ok(ex,'wave-eight reviewed ID is absent from active web source: '+id);
   assert.equal(ex.name,name,'wave-eight exercise name drift: '+id);
   assert.deepEqual(ex.equipment,equipment,'wave-eight source equipment drift: '+id);
   assert.deepEqual(ex.practicalReps,reps,'wave-eight source rep range drift: '+id);
   assert.equal(ex.source??null,origin,'wave-eight source version drift: '+id);
   assert.equal(ex.legacySubregion??null,subregion,
     'wave-eight forearm/anatomical subregion drift: '+id);
 }
 // Gripper and Svend are *not* bodyweight-only despite the web token.
 // The Kotlin review requires real implement stock and treats that token
 // as an upstream source fact, NOT permission to skip gym verification.
 for(const [id,name,equipment,reps,origin,subregion] of waveNineWebSource) {
   const ex=liveById.get(id);
   assert.ok(ex,'wave-nine exercise missing from canonical source: '+id);
   assert.equal(ex.name,name,'wave-nine source name drift: '+id);
   assert.deepEqual(ex.equipment,equipment,'wave-nine metadata equipment drift: '+id);
   assert.deepEqual(ex.practicalReps,reps,'wave-nine rep band drift: '+id);
   assert.equal(ex.source,origin,'wave-nine source version drift: '+id);
   assert.equal(ex.legacySubregion??null,subregion,'wave-nine source anatomy drift: '+id);
   assert.equal(ex.flags?.bodyweight,false,
     'wave-nine source must not reclassify the loaded implement as bodyweight');
 }
 const total=49+count+requestedIds.length+waveEightIds.length+
   waveNineIds.length+ratedBandRequiredIds.length+9;
 assert.ok(source.includes('require(definitions.size=='+total+')'),
   'Kotlin exact source count must include independently reviewed additions');
 assert.ok(source.includes(') + listOf(\\n') || source.includes(') + listOf('),
   'new requested definitions must remain separate from the generated reviewed registry');
 assert.ok(source.includes('mayTransferPreviousLoad:Boolean=false')&&
   !expected.includes('mayTransferPreviousLoad'),
   'reviewed IDs may never implicitly transfer historical loading');
 return {originalStablePrefixCount:49,additions:count+requestedIds.length+waveEightIds.length+waveNineIds.length+9,total};
}

export function writeReviewedCatalogExpansion(manifest,source,web=EXERCISES) {
 const count=validateRows(manifest,web);
 const from=source.indexOf(BEGIN),to=source.indexOf(END);
 assert.ok(from>=0&&to>from,'review markers missing');
 const replacement=BEGIN+'\n'+manifest.records.map(renderEntry).join(',\n')+'\n'+END;
 const previous=requireCount(source);
 const previousGenerated=[...source.slice(from,to).matchAll(/\bentry\("/g)].length;
 return source.slice(0,from)+replacement+source.slice(to+END.length)
   .replace('require(definitions.size=='+previous+')',
     'require(definitions.size=='+(previous-previousGenerated+count)+')');
}
function requireCount(source) {
 const m=source.match(/require\(definitions\.size==(\d+)\)/);
 assert.ok(m,'missing checked Kotlin catalog count');return Number(m[1]);
}

function selfTest() {
 const m=JSON.parse(readFileSync(manifestPath,'utf8'));
 const src=readFileSync(nativePath,'utf8');
 assert.equal(validateReviewedCatalogExpansion(m,src).total,370);
 const unverifiedSupport=src.replace(
   'null,null,setOf("spanish-squat-support"),12..20',
   'null,null,emptySet(),12..20');
 assert.throws(()=>validateReviewedCatalogExpansion(m,unverifiedSupport),
   /Spanish squat must demand confirmed support/);
 const forgedAssistance=src.replace(
   'LoadBasis.ASSISTANCE_COUNTERWEIGHT,\n            setOf("assisted-pullup-dip"),8..12',
   'LoadBasis.MACHINE_STACK,\n            setOf("assisted-pullup-dip"),8..12');
 assert.throws(()=>validateReviewedCatalogExpansion(m,forgedAssistance),
   /assisted movement must use verified counterweight/);
 const waveNineDrift=EXERCISES.map(ex=>ex.id==='svend-press'
   ?{...ex,practicalReps:[5,8]}:ex);
 assert.throws(()=>validateReviewedCatalogExpansion(m,src,waveNineDrift),
   /wave-nine rep band drift/);
 const forgedBand=EXERCISES.map(ex=>ex.id==='band_lateral_raise'
   ?{...ex,practicalReps:[8,12]}:ex);
 assert.throws(()=>validateReviewedCatalogExpansion(m,src,forgedBand),
   /rated-band source identity\/rep\/metadata drift/);
 const malformedBand=structuredClone(ratedBandManifest);
 malformedBand.records.pop();
 assert.throws(()=>validateRatedBandTranche(malformedBand,src,EXERCISES),
   /reviewer coverage or append order changed/);
 const changedWeb=EXERCISES.map(ex=>ex.id==='cable-radial-dev'
   ?{...ex,practicalReps:[8,12]}:ex);
 assert.throws(()=>validateReviewedCatalogExpansion(m,src,changedWeb),
   /wave-eight source rep range drift/);
 const changed=structuredClone(m);
 changed.records[0].source.practicalReps=[1,100];
 assert.throws(()=>validateReviewedCatalogExpansion(changed,src),/source metadata drift/);
 const wrongAnatomy=structuredClone(m);
 wrongAnatomy.records.find(x=>x.id==='tibialis-raise').sourceSubregion='calves';
 assert.throws(()=>validateReviewedCatalogExpansion(wrongAnatomy,src),/subregion evidence/);
 const broadCore=structuredClone(m);
 broadCore.records.find(x=>x.id==='serratus-pushup').review.direct='ABS';
 assert.throws(()=>validateReviewedCatalogExpansion(broadCore,src),/declared target/);
 const drift=EXERCISES.map(x=>x.id==='serratus-pushup'?{...x,legacySubregion:'abs'}:x);
 assert.throws(()=>validateReviewedCatalogExpansion(m,src,drift),/anatomical subregion drift/);
 const wrongBell=structuredClone(m);
 wrongBell.records.find(x=>x.id==='kb-gorilla-row').review.implement='single-kettlebell';
 assert.throws(()=>validateReviewedCatalogExpansion(wrongBell,src));
 const wrongLandmine=structuredClone(m);
 wrongLandmine.records.find(x=>x.id==='landmine-press').review.implement='straight-bar';
 assert.throws(()=>validateReviewedCatalogExpansion(wrongLandmine,src));
 const wrongFixture=structuredClone(m);
 wrongFixture.records.find(x=>x.id==='back-ext-45').review.fixtures=['flat-bench'];
 assert.throws(()=>validateReviewedCatalogExpansion(wrongFixture,src),
   /do not infer unweighted loading/);
 const missingIncline=structuredClone(m);
 missingIncline.records.find(x=>x.id==='prone-y-raise').review.fixtures=[];
 assert.throws(()=>validateReviewedCatalogExpansion(missingIncline,src),
   /do not infer unweighted loading/);
 const missingDecline=structuredClone(m);
 missingDecline.records.find(x=>x.id==='decline-situp').review.fixtures=['flat-bench'];
 assert.throws(()=>validateReviewedCatalogExpansion(missingDecline,src),
   /do not infer unweighted loading/);
 const forged=structuredClone(m);
 forged.records[0].review.basis=null;
 assert.throws(()=>validateReviewedCatalogExpansion(forged,src),/unweighted cannot/);
 const prefixCollision=src.replace(
   'entry("db_overhead_triceps","elbow_extension","overhead-dumbbell"',
   'entry("db_overhead_triceps","elbow_extension","overhead-dumbbell-extended-web-id"');
 assert.throws(()=>validateReviewedCatalogExpansion(m,prefixCollision),
   /unique physical family\/variant/);
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
