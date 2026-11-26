import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {readFileSync, mkdirSync, writeFileSync} from 'node:fs';
import {dirname, join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {EXERCISES} from '../../../modules/next-engine/exercise-db.js';

/**
 * Bounded, executable WEB CATALOG -> KOTLIN STARTER inventory audit.
 *
 * This is one necessary part of engine parity, NEVER a certification of the
 * full engine. Runtime and training decisions remain Kotlin-only. The web
 * catalog is used as an immutable comparison baseline in Node CI only.
 *
 * A shared ID does not prove matching equipment, source metadata, loading,
 * distinct physical machine identity, or adequate coaching quality. A
 * different ID can be equivalent, but ONLY after a reviewed identity mapping
 * and behavior tests; do not silently treat similar names as equivalence.
 */
const here=dirname(fileURLToPath(import.meta.url));
const root=join(here,'../../..');
const nativePath=join(root,
  'android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/StarterExerciseTranche.kt');
const webPaths=[
  'modules/next-engine/exercise-db.js',
  'modules/next-engine/extended-exercise-catalog.js'
];
const native=readFileSync(nativePath,'utf8');
export function parseNativeStarterIds(source) {
  // Canonical web IDs include both underscores and hyphens. Never silently
  // omit valid Kotlin entries such as wrist-curl and reverse-wrist-curl.
  return [...source.matchAll(/\bentry\("([a-z0-9_-]+)"/g)].map(m=>m[1]);
}
const nativeStarterIds=parseNativeStarterIds(native);
// A separate typed model can be source-reviewed without prematurely
// claiming that legacy reps-based workout issuance/storage supports it.
const measurementCandidatePath=join(root,
  'android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/MeasuredExerciseDomain.kt');
const measuredSource=readFileSync(measurementCandidatePath,'utf8');
const measuredCandidates=[...measuredSource.matchAll(
  /MeasuredExerciseDefinition\("([^"]+)","([^"]+)",\s*MeasuredExerciseKind\.([A-Z_]+),MeasuredLoadKind\.([A-Z_]+),\s*MeasuredSideRule\.([A-Z_]+)/g
)].map(([,id,name,kind,loadKind,sideRule])=>({id,name,kind,loadKind,sideRule}));

const pinnedVariantPath=join(root,
  'android-native/kotlin-engine/verification/requested-web-variants-20261117.json');
const pinned=JSON.parse(readFileSync(pinnedVariantPath,'utf8'));
assert.equal(pinned.schemaVersion,1,'pinned source parity format changed');
assert.equal(pinned.sourcePullRequest,671,'source review provenance changed');
assert.equal(pinned.records.length,7,'all seven exact requested variants must be reviewed');
// The Kotlin rebuild intentionally does not load/execute web JavaScript code
// at runtime. Its frozen web source may be older than current main. Supply
// only the seven exact source-pinned identities from the reviewed web PR,
// checking the live branch records if they already exist.
const currentBranchById=new Map(EXERCISES.map(x=>[x.id,x]));
const missingPinned=[];
for(const record of pinned.records) {
  const existing=currentBranchById.get(record.id);
  if(existing) {
    for(const key of ['id','name','equipment','movementFamily','practicalReps','source'])
      assert.deepEqual(existing[key],record[key],
        'current web source conflicts with pinned reviewed variant: '+record.id+'/'+key);
  } else missingPinned.push(record);
}
const reviewedWebBaseline=[...EXERCISES,...missingPinned];
assert.equal(new Set(reviewedWebBaseline.map(x=>x.id)).size,
  reviewedWebBaseline.length,'reviewed web baseline has duplicate IDs');
const sha256=buffer=>createHash('sha256').update(buffer).digest('hex');

// CI-only explanations of web movements which must NOT be passed through the
// current numeric weight × reps Kotlin journal. This inventory does not
// silently re-label a timed hold as repetitions, a carry as sets or a machine
// counterweight as resistance (where more assistance would mean stronger).
const pendingMeasurementSemantics={
  ASSISTED_COUNTERWEIGHT:['assisted-dip','assisted-chinup','assisted-pullup'],
  TIMED_HOLD:['superman','plank','side-plank','plate-pinch','dead-hang',
    'hollow-hold','copenhagen','wall-sit','l-sit','weighted-plank','towel-hang'],
  DISTANCE_CARRY:['farmers','suitcase-carry'],
  // Neck v5 is source-matched with three distinct functions and real
  // individually verified implement stock; no missing neck IDs remain.
  HYBRID_BODYWEIGHT_AND_BAND:['band-pushup']
};
const pendingById=new Map();
for(const [reason,ids] of Object.entries(pendingMeasurementSemantics))
  for(const id of ids) {
    assert.ok(!pendingById.has(id),'Duplicate missing-measurement classification: '+id);
    pendingById.set(id,reason);
  }


export function reviewCatalogCoverage(legacyExercises,nativeStarter) {
  assert.ok(Array.isArray(legacyExercises) && Array.isArray(nativeStarter));
  assert.ok(legacyExercises.length>0,'missing canonical web catalog');
  assert.ok(nativeStarter.length>0,'missing Kotlin starter tranche');
  const oldIds=legacyExercises.map(x=>x.id);
  assert.equal(new Set(oldIds).size,oldIds.length,'duplicate effective web IDs');
  assert.equal(new Set(nativeStarter).size,nativeStarter.length,'duplicate native starter IDs');
  assert.ok(oldIds.every(x=>typeof x==='string' && x.trim()));
  assert.ok(nativeStarter.every(x=>typeof x==='string' && x.trim()));
  const nativeSet=new Set(nativeStarter);
  const sourceMatched=legacyExercises.filter(x=>nativeSet.has(x.id));
  const unmapped=legacyExercises.filter(x=>!nativeSet.has(x.id));
  const familyCounts={};
  for(const ex of unmapped) {
    const family=ex.movementFamily??'UNCLASSIFIED';
    familyCounts[family]=(familyCounts[family]??0)+1;
  }
  const families=Object.entries(familyCounts)
    .sort((a,b)=>b[1]-a[1]||a[0].localeCompare(b[0]))
    .map(([family,unmappedCount])=>({family,unmappedCount}));
  return {
    verdict:unmapped.length===0 ?
      'CATALOG_IDS_PRESENT_BEHAVIOR_STILL_UNVERIFIED' :
      'WEB_BUILTIN_EXERCISE_CATALOG_NOT_AT_PARITY',
    webEffectiveBuiltins:legacyExercises.length,
    kotlinStarterDefinitions:nativeStarter.length,
    exactIdMatches:sourceMatched.length,
    webBuiltinsNotInStarter:unmapped.length,
    // Never call exact matches approved/independently reviewed.
    independentlyCertifiedMappings:0,
    sourceMatchedIds:sourceMatched.map(x=>x.id),
    nativeOnlyIds:nativeStarter.filter(id=>!oldIds.includes(id)),
    missingByMovementFamily:families,
    missingByMeasurementSemantics:Object.entries(pendingMeasurementSemantics)
      .map(([reason,ids])=>({
        reason,ids:ids.filter(id=>unmapped.some(ex=>ex.id===id))
      })).filter(group=>group.ids.length)
      .concat(unmapped.filter(ex=>!pendingById.has(ex.id)).length ? [{
        reason:'UNCLASSIFIED_REVIEW_REQUIRED',
        ids:unmapped.filter(ex=>!pendingById.has(ex.id)).map(ex=>ex.id)
      }] : []),
    webUnmappedDefinitions:unmapped.map(x=>({
      id:x.id,name:x.name,movementFamily:x.movementFamily??null,
      equipment:x.equipment??[]
    })),
    releaseBlockers:[
      'Every valuable web built-in needs an independently reviewed Kotlin exercise or explicitly reviewed equivalent, with the same user outcome.',
      'Physical stock, muscle credits, rep ranges, loads, variants, machine identity, program selection and history identity require functional parity testing.',
      'Matching exercise IDs is not certification; no mappings in this audit have independent acceptance evidence.'
    ]
  };
}

function assertMeasuredCandidateSourceIntegrity() {
  const expected=Object.values(pendingMeasurementSemantics).flat().filter(
    id=>!nativeStarterIds.includes(id));
  assert.equal(expected.length,14,
    'The next tranche is precisely 11 timed, two distance and one hybrid movements');
  assert.equal(measuredCandidates.length,14,
    'Every missing movement needs a separate typed Kotlin measurement candidate');
  assert.deepEqual(new Set(measuredCandidates.map(x=>x.id)),new Set(expected));
  const sourceById=new Map(reviewedWebBaseline.map(ex=>[ex.id,ex]));
  for(const candidate of measuredCandidates) {
    const source=sourceById.get(candidate.id);
    assert.ok(source,'Missing verified web source identity: '+candidate.id);
    assert.equal(candidate.name,source.name,
      'A Kotlin measurement must use the exact web exercise name: '+candidate.id);
    const reason=[...Object.entries(pendingMeasurementSemantics)]
      .find(([,ids])=>ids.includes(candidate.id))?.[0];
    assert.equal(candidate.kind,
      reason==='TIMED_HOLD' ? 'HOLD_SECONDS' :
      reason==='DISTANCE_CARRY' ? 'CARRY_CENTIMETERS' :
      'BANDED_REPETITIONS',
      'Never reclassify held seconds, carry distance or elastic repetitions: '+candidate.id);
    assert.equal(nativeStarterIds.includes(candidate.id),false,
      'Typed candidate must not enter the reps-only issued catalog prematurely: '+candidate.id);
  }
  const byId=new Map(measuredCandidates.map(x=>[x.id,x]));
  for(const id of ['plate-pinch','weighted-plank'])
    assert.equal(byId.get(id)?.loadKind,'VERIFIED_ADDED_LOAD');
  for(const id of ['farmers','suitcase-carry'])
    assert.equal(byId.get(id)?.loadKind,'VERIFIED_PER_HAND');
  assert.equal(byId.get('band-pushup')?.loadKind,'BANDED_BODYWEIGHT');
  assert.equal(byId.get('side-plank')?.sideRule,'LEFT_AND_RIGHT');
  assert.equal(byId.get('copenhagen')?.sideRule,'LEFT_AND_RIGHT');
  assert.equal(byId.get('suitcase-carry')?.sideRule,'EXPLICIT_SINGLE_SIDE');
  // This inventory is NOT a readiness/issuance test. Actual owners, v1
  // atomic persistence, the UI, and performance-dose parity are still open.
  return {exactSourceCandidates:measuredCandidates.length,
    typedMeasurementKinds:{timed:11,distance:2,hybrid:1},
    issueAndDurableJournalCertified:0,releaseReady:false};
}

function selfTest() {
  assertMeasuredCandidateSourceIntegrity();
  assert.deepEqual(parseNativeStarterIds(
    'entry("bench","family",...)\nentry("wrist-curl","family",...)\nentry("reverse-wrist-curl","family",...)'
  ),['bench','wrist-curl','reverse-wrist-curl'],
  'hyphenated canonical IDs must not vanish from Kotlin starter inventory');
  const declaredCount=Number(native.match(/require\(definitions\.size==(\d+)\)/)?.[1]);
  assert.ok(Number.isInteger(declaredCount) && declaredCount>0,
    'Kotlin starter must declare a checked definition count');
  assert.equal(nativeStarterIds.length,declaredCount,
    'starter parser must include every declared Kotlin definition');
  const sample=[
    {id:'bench',name:'Bench',movementFamily:'horizontal_press'},
    {id:'cable_press',name:'Cable Press',movementFamily:'horizontal_press'}
  ];
  const incomplete=reviewCatalogCoverage(sample,['bench']);
  const completeAudit=reviewCatalogCoverage(reviewedWebBaseline,nativeStarterIds);
  assert.ok(nativeStarterIds.includes('spanish-squat'),
    'Only a confirmed-support, repetition-based Spanish squat is admitted');
  assert.ok(!nativeStarterIds.includes('band-pushup'),
    'Hybrid bodyweight+band resistance is not an ordinary numeric working weight');
  const unexplained=completeAudit.missingByMeasurementSemantics
    .find(group=>group.reason==='UNCLASSIFIED_REVIEW_REQUIRED');
  assert.equal(unexplained,undefined,
    'Every missing current-web exercise needs a named measurement/parity gap');
  assert.equal(completeAudit.webEffectiveBuiltins,
    EXERCISES.length+missingPinned.length);
  assert.equal(completeAudit.webEffectiveBuiltins,383,
    'frozen branch plus reviewed seven-variant source must match current web inventory');
    const pendingCount=completeAudit.missingByMeasurementSemantics.reduce(
    (n,group)=>n+group.ids.length,0);
  assert.equal(pendingCount,completeAudit.webBuiltinsNotInStarter);
  assert.equal(completeAudit.missingByMeasurementSemantics
    .find(x=>x.reason==='ASSISTED_COUNTERWEIGHT')?.ids.length??0,0,
    'All three reviewed assisted machine identities must be source matched');
  for(const id of pendingMeasurementSemantics.ASSISTED_COUNTERWEIGHT)
    assert.ok(nativeStarterIds.includes(id),'Missing reviewed assisted machine: '+id);
  assert.deepEqual(completeAudit.nativeOnlyIds,['weighted-pullup','chest-supported-machine-row','ez-bar-preacher-curl'],
    'Historical weighted pull-up is a separate native identity, not a current-web exact match');
  assert.equal(completeAudit.exactIdMatches,369,
    'The frozen current-web source denominator is unchanged');
  assert.equal(completeAudit.webBuiltinsNotInStarter,14,
    'The current reconciled web catalog must have exactly 14 remaining time/distance/hybrid gaps');
  for(const id of ['neck-extension','neck-curl','neck-harness','neck-lateral'])
    assert.ok(nativeStarterIds.includes(id),
      'A separately reviewed physical-load and V5 muscle entry is required: '+id);
  assert.equal(completeAudit.missingByMeasurementSemantics
    .find(x=>x.reason==='HYBRID_BODYWEIGHT_AND_BAND')?.ids[0],'band-pushup');
  assert.equal(incomplete.webBuiltinsNotInStarter,1);
  assert.equal(incomplete.exactIdMatches,1);
  assert.equal(incomplete.independentlyCertifiedMappings,0);
  assert.equal(incomplete.webUnmappedDefinitions[0].id,'cable_press');
  assert.equal(reviewCatalogCoverage(sample,['bench','cable_press']).verdict,
    'CATALOG_IDS_PRESENT_BEHAVIOR_STILL_UNVERIFIED');
  assert.throws(()=>reviewCatalogCoverage(sample,['bench','bench']),
    /duplicate native starter IDs/);
  assert.throws(()=>reviewCatalogCoverage([...sample,sample[0]],['bench']),
    /duplicate effective web IDs/);
  console.log('PASS exercise-parity audit self-test (no false behavior certification)');
}

const args=new Set(process.argv.slice(2));
if(args.has('--self-test')) selfTest();
else {
  if(!args.has('--audit') && !args.has('--release'))
    throw Error('Choose --self-test, --audit or --release');
  const report=reviewCatalogCoverage(reviewedWebBaseline,nativeStarterIds);
  report.source={
    legacyCatalogFiles:webPaths.map(p=>({path:p,sha256:sha256(readFileSync(join(root,p)))})),
    additionalPinnedWebSource:{path:'android-native/kotlin-engine/verification/requested-web-variants-20261117.json',
      sha256:sha256(readFileSync(pinnedVariantPath)),
      sourcePullRequest:pinned.sourcePullRequest,
      sourceCommit:pinned.sourceCommit,
      newlyAddedToFrozenBranch:missingPinned.length},
    nativeStarterPath:'android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/StarterExerciseTranche.kt',
    nativeStarterSha256:sha256(native)
  };
  report.measuredCandidateContracts=assertMeasuredCandidateSourceIntegrity();
  report.scope='Catalog source coverage only. Not program, cycle, progression or Android feature parity.';
  // Never print hundreds of rows into CI logs; full rows remain in artifact.
  console.log(JSON.stringify({
    verdict:report.verdict,
    webEffectiveBuiltins:report.webEffectiveBuiltins,
    kotlinStarterDefinitions:report.kotlinStarterDefinitions,
    exactIdMatches:report.exactIdMatches,
    webBuiltinsNotInStarter:report.webBuiltinsNotInStarter,
    independentlyCertifiedMappings:report.independentlyCertifiedMappings,
    topUnmappedFamilies:report.missingByMovementFamily.slice(0,12),
    measuredCandidateContracts:report.measuredCandidateContracts,
    missingByMeasurementSemantics:report.missingByMeasurementSemantics
  },null,2));
  if(args.has('--write-report')) {
    const path=join(here,'..','reports','web-native-catalog-parity-audit.json');
    mkdirSync(dirname(path),{recursive:true});
    writeFileSync(path,JSON.stringify(report,null,2)+'\n');
  }
  if(args.has('--release')) {
    console.error('Release blocked: exact ID inventory is not verified behavior parity. '+
      'Require reviewed mappings and paired functional/device acceptance.');
    process.exitCode=1;
  }
}
