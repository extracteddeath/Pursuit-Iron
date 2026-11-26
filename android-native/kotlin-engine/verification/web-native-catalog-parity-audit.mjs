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

// Historical source reconciliation is CI-only. This produces a complete
// review queue, NOT a runtime alias table or permission to transfer old loads.
const shellInventory=JSON.parse(readFileSync(join(root,
  'android-native/STAGE1_WEB_SHELL_INVENTORY.json'),'utf8'));
const pinnedShell=JSON.parse(readFileSync(join(here,
  'pinned-shell-variant-rows.json'),'utf8'));
const shellSource=readFileSync(join(root,'modules/training-domain/catalog.js'),'utf8');
const projectionSource=readFileSync(join(root,'modules/next-engine/shell-projection.js'),'utf8');
const historicalPhysicalVariants=new Map([
  ['pullup','weighted-pullup'],
  ['chest-row','chest-supported-machine-row'],
  ['preacher','ez-bar-preacher-curl']
]);
const normalizeName=name=>name.toLowerCase().replace(/[^a-z0-9]+/g,' ').trim();
// These are the four frozen name-only matches, not runtime aliases. An
// identical name is insufficient to identify the original implement or load.
const reviewedFallbackContexts=new Map([
  ['cable-curl',{nativeId:'cable_curl',shellEquipment:['cable'],shellMuscle:'biceps',
    nativeDeclaration:'entry("cable_curl","elbow_flexion","cable-straight-bar",',
    nativePhysical:'"cable-station",LoadBasis.CABLE_STACK',
    attachment:'straight-bar',requiredContext:'Original straight-bar attachment and cable-stack load convention'}],
  ['cable-crunch',{nativeId:'cable_crunch',shellEquipment:['cable'],shellMuscle:'abs',
    nativeDeclaration:'entry("cable_crunch","trunk_flexion","cable-rope",',
    nativePhysical:'"cable-station",LoadBasis.CABLE_STACK',
    attachment:'rope',requiredContext:'Original rope attachment and cable-stack load convention'}],
  ['dumbbell-crunch',{nativeId:'dumbbell_crunch',shellEquipment:['dumbbell'],shellMuscle:'abs',
    nativeDeclaration:'entry("dumbbell_crunch","trunk_flexion","single-dumbbell-crunch",',
    nativePhysical:'DBS,LoadBasis.DUMBBELL_PER_HAND',
    attachment:null,requiredContext:'Original single-dumbbell load, not combined dumbbell weight'}],
  ['bw-pullup',{nativeId:'pullup',shellEquipment:['pullup'],shellMuscle:'lats',
    nativeDeclaration:'entry("pullup","vertical_pull","unweighted-overhand",',
    nativePhysical:'null,null,setOf("pullup-bar")',
    attachment:null,requiredContext:'Original unweighted bodyweight performance, not weighted or assisted pull-up'}]
]);

// Equipment-token changes do not establish mechanical equivalence. The
// following reviewed source exceptions pin the old and current physical
// conventions and keep every candidate conditional on original receipts.
const historicalEquipmentReview=new Map([
  ['inc-db-press',[['dumbbell','inclinebench'],['dumbbell','bench'],'DBS,LoadBasis.DUMBBELL_PER_HAND,setOf("incline-bench")','INCLINE_SUPPORT_REQUIRED']],
  ['machine-press',[['machinepress'],['machine'],'"chest-press-machine",LoadBasis.EXERCISE_OVERRIDE','STATION_CONTEXT_REQUIRED']],
  ['machine-shoulder',[['machineshoulder'],['machine'],'"shoulder-press-machine",LoadBasis.EXERCISE_OVERRIDE','STATION_CONTEXT_REQUIRED']],
  ['reverse-pec',[['pecdeck'],['machine'],'"reverse-pec-deck-machine",LoadBasis.EXERCISE_OVERRIDE','STATION_CONTEXT_REQUIRED']],
  ['hack-squat',[['hacksquat'],['machine'],'"hack-squat-machine",LoadBasis.EXERCISE_OVERRIDE','STATION_CONTEXT_REQUIRED']],
  ['leg-press',[['legpress'],['leg_press'],'"leg-press-machine",LoadBasis.EXERCISE_OVERRIDE','STATION_CONTEXT_REQUIRED']],
  ['leg-ext',[['legext'],['machine'],'LEG_EXTENSION,LoadBasis.EXERCISE_OVERRIDE','STATION_CONTEXT_REQUIRED']],
  ['seated-curl',[['legcurl'],['machine'],'SEATED_LEG_CURL,LoadBasis.EXERCISE_OVERRIDE','STATION_CONTEXT_REQUIRED']],
  ['standing-calf',[['calfmachine'],['machine'],'"standing-calf-machine",LoadBasis.EXERCISE_OVERRIDE','STATION_CONTEXT_REQUIRED']],
  ['pullup',[['pullup'],['pullup_bar','bodyweight'],'"weighted-pullup-resistance",LoadBasis.EXERCISE_OVERRIDE','DISTINCT_EXTERNAL_RESISTANCE']],
  ['chest-row',[['machinerow'],['dumbbell','bench'],'"chest-supported-row-machine",LoadBasis.EXERCISE_OVERRIDE','DISTINCT_EXTERNAL_RESISTANCE']],
  ['preacher',[['ezbar','preacher'],['machine'],'"ez-curl-bar",LoadBasis.BARBELL_TOTAL,setOf("preacher-bench")','DISTINCT_EXTERNAL_RESISTANCE']],
  ['bw-pullup',[['pullup'],['pullup_bar','bodyweight'],'null,null,setOf("pullup-bar")','UNWEIGHTED_CONTEXT_REQUIRED']]
]);
const legacyMissingResistance=new Map([
  ['wrist-roller','wrist-roller-handle'],['hammer-radial-dev','wrist-lever-hammer'],
  ['hammer-ulnar-dev','wrist-lever-hammer'],['neck-extension','neck-extension-device'],
  ['neck-curl','neck-flexion-device'],['neck-harness','neck-harness-device'],
  ['neck-lateral','neck-lateral-device'],['svend-press','single-weight-plate'],
  ['gripper','hand-gripper-device']
]);
function reviewEquipmentDifference(id,oldEquipment,newEquipment,nativeEntry,measurement) {
  const exact=historicalEquipmentReview.get(id);
  if(exact) {
    assert.deepEqual(oldEquipment,exact[0],'Historical equipment drift: '+id);
    assert.deepEqual(newEquipment,exact[1],'Canonical equipment drift: '+id);
    assert.ok(nativeEntry?.includes(exact[2]),'Native physical equipment drift: '+id);
    return exact[3];
  }
  assert.deepEqual(oldEquipment,[],'Unreviewed historical equipment difference: '+id);
  assert.deepEqual(newEquipment,['bodyweight'],'Unreviewed canonical equipment difference: '+id);
  const resistance=legacyMissingResistance.get(id);
  if(resistance) {
    assert.ok(nativeEntry?.includes('"'+resistance+'",LoadBasis.EXERCISE_OVERRIDE'),
      'Legacy missing-resistance convention drift: '+id);
    return 'EXPLICIT_IMPLEMENT_AND_LOAD_REQUIRED';
  }
  if(measurement) {
    assert.equal(measurement.kind,'HOLD_SECONDS','Historical bodyweight measurement drift: '+id);
    assert.equal(measurement.loadKind,'UNWEIGHTED','Historical hold load drift: '+id);
    return 'ORIGINAL_DURATION_AND_SETUP_REQUIRED';
  }
  assert.ok(nativeEntry?.includes('null,null,'),'Unreviewed bodyweight load convention: '+id);
  return 'UNWEIGHTED_CONTEXT_REQUIRED';
}

export function reviewHistoricalShellCoverage(shell,rawSource,projection,
  frozenMissing,canonical,nativeIds,measured,nativeSource) {
  assert.equal(frozenMissing.webCommit,shell.webCommit);
  assert.equal(frozenMissing.sourceBlobSha,
    shell.sourceFiles.find(x=>x.path==='modules/training-domain/catalog.js').blobSha);
  assert.equal(frozenMissing.rows.length,7);
  const rawBlock=rawSource.split('const RAW = [')[1]?.split('\n];')[0];
  assert.ok(rawBlock,'Missing historical shell RAW source');
  const currentRows=rawBlock.split('\n').filter(line=>/^\s*\["/.test(line))
    .map(line=>JSON.parse(line.trim().replace(/,$/,'')));
  const allRows=[...currentRows];
  for(const row of frozenMissing.rows) {
    assert.ok(pinned.records.some(x=>x.id===row[0]),
      'Only the seven independently pinned source variants may be supplemented');
    const current=currentRows.find(x=>x[0]===row[0]);
    if(current)assert.deepEqual(current,row,'Pinned shell variant drift: '+row[0]);
    else allRows.push(row);
  }
  const rawById=new Map(allRows.map(row=>[row[0],row]));
  assert.equal(rawById.size,allRows.length,'Duplicate historical source row');
  const aliasBlock=projection.match(/const EXPLICIT_EXERCISE_ALIASES = \{([\s\S]*?)\};/)?.[1];
  assert.ok(aliasBlock,'Explicit web alias provenance unavailable');
  const aliases=new Map([...aliasBlock.matchAll(/([a-z0-9_]+):\s*'([a-z0-9_-]+)'/g)]
    .map(m=>[m[1],m[2]]));
  const currentById=new Map(canonical.map(x=>[x.id,x]));
  const nativeSet=new Set(nativeIds),measuredSet=new Set(measured.map(x=>x.id));
  const declarations=[...nativeSource.matchAll(/\bentry\("([a-z0-9_-]+)"/g)];
  const nativeById=new Map(declarations.map((m,i)=>[m[1],
    nativeSource.slice(m.index,declarations[i+1]?.index??nativeSource.length)]));
  assert.equal(shell.shellRows.length,372);
  assert.equal(new Set(shell.shellRows.map(x=>x.shellId)).size,372);
  const candidates=shell.shellRows.map(row=>{
    const raw=rawById.get(row.shellId),active=currentById.get(row.candidateWebId);
    assert.ok(raw&&active,'Unresolved historical source or canonical candidate: '+row.shellId);
    assert.equal(raw[1],row.name,'Historical shell name drift: '+row.shellId);
    if(row.match==='exact-id')assert.equal(row.shellId,row.candidateWebId);
    else if(row.match==='explicit-web-alias')
      assert.equal(aliases.get(row.candidateWebId),row.shellId,
        'Explicit alias source drift: '+row.shellId);
    else {
      assert.equal(row.match,'web-name-fallback-review');
      assert.equal(normalizeName(raw[1]),normalizeName(active.name),
        'Name fallback no longer matches: '+row.shellId);
    }
    const nativeId=historicalPhysicalVariants.get(row.shellId)??row.candidateWebId;
    assert.ok(nativeSet.has(nativeId)||measuredSet.has(nativeId),
      'No supported native identity candidate: '+row.shellId);
    const nativeEntry=nativeById.get(nativeId);
    const attachment=nativeEntry?.match(/attachmentId="([^"]+)"/)?.[1]??null;
    let fallbackContext=null;
    if(row.match==='web-name-fallback-review') {
      const expected=reviewedFallbackContexts.get(row.shellId);
      assert.ok(expected,'Unreviewed name fallback: '+row.shellId);
      assert.equal(nativeId,expected.nativeId,'Name fallback native identity drift: '+row.shellId);
      assert.deepEqual(raw[4],expected.shellEquipment,'Name fallback original equipment drift: '+row.shellId);
      assert.equal(raw[2],expected.shellMuscle,'Name fallback original muscle drift: '+row.shellId);
      assert.ok(nativeEntry?.startsWith(expected.nativeDeclaration) &&
        nativeEntry.includes(expected.nativePhysical),
        'Name fallback physical convention drift: '+row.shellId);
      assert.equal(attachment,expected.attachment,'Name fallback attachment drift: '+row.shellId);
      fallbackContext=expected.requiredContext;
    }
    const equipmentTokensDiffer=JSON.stringify(raw[4])!==JSON.stringify(active.equipment);
    const equipmentReview=equipmentTokensDiffer?reviewEquipmentDifference(row.shellId,raw[4],active.equipment,nativeEntry,measured.find(x=>x.id===nativeId)):null;
    return {shellId:row.shellId,shellName:raw[1],pinnedSourceLine:row.line,
      shellEquipment:raw[4],shellRepBand:raw.slice(5,7),
      canonicalCandidateId:row.candidateWebId,canonicalEquipment:active.equipment,
      nativeCandidateId:nativeId,match:row.match,equipmentTokensDiffer,
      nativeAttachment:attachment,fallbackContext,equipmentReview,
      measurement:measuredSet.has(nativeId)?'TYPED':'STANDARD_REPS',
      review:historicalPhysicalVariants.has(row.shellId)?'DISTINCT_PHYSICAL_VARIANT':
        fallbackContext?'PHYSICAL_CONTEXT_REQUIRED':
        attachment?'ATTACHMENT_AND_LOAD_CONTEXT_REQUIRED':
        equipmentTokensDiffer?'EQUIPMENT_SEMANTICS_REVIEW_REQUIRED':'IDENTITY_CANDIDATE_ONLY',
      mayTransferHistory:false,independentlyAccepted:false};
  });
  assert.deepEqual(new Set(candidates.filter(x=>x.fallbackContext).map(x=>x.shellId)),
    new Set(reviewedFallbackContexts.keys()),'Frozen name fallback coverage drift');
  for(const [id,nativeId] of historicalPhysicalVariants) {
    const item=candidates.find(x=>x.shellId===id);
    assert.equal(item.nativeCandidateId,nativeId);
    assert.notEqual(item.canonicalCandidateId,nativeId);
    assert.ok(nativeSet.has(nativeId)&&!currentById.has(nativeId),
      'Distinct historical candidate cannot collapse into canonical current-web ID');
  }
  const equipmentReviewCounts={};
  for(const item of candidates.filter(x=>x.equipmentTokensDiffer)) {
    assert.ok(item.equipmentReview,'Missing equipment review: '+item.shellId);
    equipmentReviewCounts[item.equipmentReview]=(equipmentReviewCounts[item.equipmentReview]??0)+1;
  }
  assert.equal(equipmentReviewCounts.EXPLICIT_IMPLEMENT_AND_LOAD_REQUIRED,legacyMissingResistance.size);
  assert.equal(equipmentReviewCounts.STATION_CONTEXT_REQUIRED,8);
  assert.equal(equipmentReviewCounts.DISTINCT_EXTERNAL_RESISTANCE,3);
  assert.equal(equipmentReviewCounts.INCLINE_SUPPORT_REQUIRED,1);
  assert.equal(equipmentReviewCounts.ORIGINAL_DURATION_AND_SETUP_REQUIRED,7);
  return {total:candidates.length,equipmentReviewCounts,distinctPhysicalVariants:historicalPhysicalVariants.size,
    equipmentTokenDifferences:candidates.filter(x=>x.equipmentTokensDiffer).length,
    nameFallbacks:candidates.filter(x=>x.match==='web-name-fallback-review').length,
    independentlyAccepted:0,automaticHistoryTransfers:0,candidates};
}

function historicalReview() {
  return reviewHistoricalShellCoverage(shellInventory,shellSource,projectionSource,
    pinnedShell,reviewedWebBaseline,nativeStarterIds,measuredCandidates,native);
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
  const historical=historicalReview();
  assert.equal(historical.total,372);
  assert.equal(historical.distinctPhysicalVariants,3);
  assert.equal(historical.equipmentTokenDifferences,71);
  assert.equal(historical.nameFallbacks,4);
  assert.ok(historical.candidates.every(x=>!x.mayTransferHistory&&!x.independentlyAccepted));
  const byShell=new Map(historical.candidates.map(x=>[x.shellId,x]));
  assert.equal(byShell.get('wrist-roller').equipmentReview,'EXPLICIT_IMPLEMENT_AND_LOAD_REQUIRED');
  assert.equal(byShell.get('leg-ext').equipmentReview,'STATION_CONTEXT_REQUIRED');
  assert.equal(byShell.get('side-plank').equipmentReview,'ORIGINAL_DURATION_AND_SETUP_REQUIRED');
  assert.throws(()=>reviewEquipmentDifference('gripper',[],['bodyweight'],'null,null,emptySet()',null),/missing-resistance convention drift/);
  assert.throws(()=>reviewEquipmentDifference('leg-ext',['legext'],['machine'],'null,null,emptySet()',null),/Native physical equipment drift/);
  assert.throws(()=>reviewEquipmentDifference('plank',[],['bodyweight'],null,{kind:'HOLD_SECONDS',loadKind:'VERIFIED_ADDED_LOAD'}),/Historical hold load drift/);
  assert.equal(byShell.get('pullup').nativeCandidateId,'weighted-pullup');
  assert.equal(byShell.get('bw-pullup').nativeCandidateId,'pullup');
  assert.equal(byShell.get('chest-row').nativeCandidateId,'chest-supported-machine-row');
  assert.equal(byShell.get('preacher').nativeCandidateId,'ez-bar-preacher-curl');
  assert.equal(byShell.get('cable-curl').nativeAttachment,'straight-bar');
  assert.equal(byShell.get('cable-crunch').nativeAttachment,'rope');
  assert.equal(byShell.get('plank').measurement,'TYPED');
  for(const [id,expected] of reviewedFallbackContexts) {
    const item=byShell.get(id);
    assert.equal(item.review,'PHYSICAL_CONTEXT_REQUIRED');
    assert.equal(item.fallbackContext,expected.requiredContext);
    assert.equal(item.mayTransferHistory,false);
    assert.equal(item.independentlyAccepted,false);
  }
  assert.throws(()=>reviewHistoricalShellCoverage(shellInventory,shellSource,
    projectionSource,pinnedShell,reviewedWebBaseline,nativeStarterIds,
    measuredCandidates,native.replace(
      'entry("dumbbell_crunch","trunk_flexion","single-dumbbell-crunch",',
      'entry("dumbbell_crunch","trunk_flexion","two-dumbbell-crunch",')),
    /Name fallback physical convention drift/);
  assert.throws(()=>reviewHistoricalShellCoverage(shellInventory,shellSource,
    projectionSource,pinnedShell,reviewedWebBaseline,nativeStarterIds,
    measuredCandidates,native.replace(
      /("Curated cable curl represented[^\n]*\n\s*direct\(TrainingMuscle\.BICEPS\),attachmentId=)"straight-bar"/,
      '$1"rope"')),
    /Name fallback attachment drift/);
  assert.throws(()=>reviewHistoricalShellCoverage(shellInventory,shellSource,
    projectionSource.replace("chest_supported_row: 'chest-row'","chest_supported_row: 'machine-row'"),
    pinnedShell,reviewedWebBaseline,nativeStarterIds,measuredCandidates,native),
    /Explicit alias source drift/);
  assert.throws(()=>reviewHistoricalShellCoverage(shellInventory,shellSource,
    projectionSource,pinnedShell,reviewedWebBaseline,
    nativeStarterIds.filter(x=>x!=='weighted-pullup'),measuredCandidates,native),
    /No supported native identity candidate/);
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
  report.historicalShellReview=historicalReview();
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
    historicalShellReview:{
      total:report.historicalShellReview.total,
      distinctPhysicalVariants:report.historicalShellReview.distinctPhysicalVariants,
      equipmentTokenDifferences:report.historicalShellReview.equipmentTokenDifferences,
      nameFallbacks:report.historicalShellReview.nameFallbacks,
      equipmentReviewCounts:report.historicalShellReview.equipmentReviewCounts,
      independentlyAccepted:report.historicalShellReview.independentlyAccepted
    },
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
