import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {CURATED_EXERCISES, EXERCISES} from '../../../modules/next-engine/exercise-db.js';
import {EXTENDED_EXERCISE_CATALOG} from '../../../modules/next-engine/extended-exercise-catalog.js';

/**
 * A SOURCE INVENTORY, not a prescriptive exercise library.
 *
 * This script reads historical web definitions only in an isolated CI job.
 * It never imports the live engine from Kotlin, never claims muscle-credit
 * coefficients are reviewed, and never changes any app/production data.
 *
 * All names, movement families, equipment and muscle values are provenance
 * facts in a staged migration report, NOT verified coaching claims.
 */
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../../..');
const current=fs.readFileSync(path.join(root,'modules/next-engine/exercise-db.js'));
const extended=fs.readFileSync(path.join(root,'modules/next-engine/extended-exercise-catalog.js'));
const sha256=buf=>createHash('sha256').update(buf).digest('hex');
const normalizeName=name=>String(name??'').toLowerCase().replace(/[^a-z0-9]+/g,' ').trim();
const idPattern=/^[A-Za-z0-9][A-Za-z0-9_:-]{0,127}$/;
const recognized=new Set([
  'chest','lats','upper_back','front_delts','side_delts','rear_delts',
  'triceps','biceps','brachialis','wrist_flexors','wrist_extensors',
  'quads','hamstrings','glutes','calves','abs'
]);
const ambiguous=new Set(['back','core','lower_back','forearms','shoulders','arms','legs','delts']);
const supportedPatterns=new Set([
  'horizontal_press','chest_adduction','horizontal_pull','vertical_pull',
  'vertical_press','shoulder_extension','lateral_raise','rear_delt',
  'squat','leg_press','hip_hinge','hip_extension','knee_extension',
  'knee_flexion','elbow_flexion','elbow_extension','calf','core'
]);

const immutableJsonCopy=value=>JSON.parse(JSON.stringify(value ?? null));
const asMuscles=definition=>{
  if(!definition?.muscles || typeof definition.muscles!=='object' ||
    Array.isArray(definition.muscles)) return {};
  return immutableJsonCopy(definition.muscles);
};

export function buildLegacyInventory({
  curated=CURATED_EXERCISES, extendedCatalog=EXTENDED_EXERCISE_CATALOG,
  effective=EXERCISES
}={}) {
  assert.ok(Array.isArray(curated) && Array.isArray(extendedCatalog));
  const rows=[
    ...curated.map((definition,i)=>({definition,source:'curated',sourceOrdinal:i})),
    ...extendedCatalog.map((definition,i)=>({definition,source:'extended',sourceOrdinal:i}))
  ];
  const winnerById=new Map(),winnerByName=new Map();
  const staged=rows.map(({definition:ex,source,sourceOrdinal})=>{
    const id=String(ex?.id??'');
    const name=String(ex?.name??'');
    const semanticName=normalizeName(name);
    const winner=winnerById.get(id)??winnerByName.get(semanticName);
    const shadowReason=winner ? winnerById.has(id)?'duplicate-id':'duplicate-name' : null;
    if(!winner) {
      winnerById.set(id,id);
      winnerByName.set(semanticName,id);
    }
    const muscles=asMuscles(ex);
    const flags=new Set();
    if(!idPattern.test(id) || !name.trim())flags.add('INVALID_IDENTITY');
    if(shadowReason)flags.add('LEGACY_SHADOWED_DEFINITION');
    const muscleNames=Object.keys(muscles);
    if(muscleNames.length===0)flags.add('NO_MUSCLE_CREDITS');
    for(const [name,credit] of Object.entries(muscles)) {
      if(ambiguous.has(name))flags.add('AMBIGUOUS_MUSCLE_'+name.toUpperCase());
      else if(!recognized.has(name))flags.add('UNMAPPED_MUSCLE_'+name.toUpperCase());
      if(typeof credit?.credit!=='number'||!Number.isFinite(credit.credit)||
          credit.credit<=0||credit.credit>1)flags.add('INVALID_CREDIT');
      if(!['primary','secondary','minor'].includes(credit?.role))
        flags.add('UNMAPPED_CREDIT_ROLE');
    }
    if(!supportedPatterns.has(ex?.movementFamily))
      flags.add('UNMAPPED_MOVEMENT_PATTERN');
    const allEquipment=Array.isArray(ex?.equipment)?ex.equipment:[];
    if(allEquipment.some(e=>typeof e!=='string'||!e.trim()))
      flags.add('INVALID_EQUIPMENT_TOKEN');
    const alternatives=Array.isArray(ex?.equipmentAlternatives)?
      ex.equipmentAlternatives:[];
    if(alternatives.length)flags.add('EQUIPMENT_ALTERNATIVE_NEEDS_VARIANT_REVIEW');
    if(allEquipment.includes('machine')||allEquipment.includes('cable'))
      flags.add('IMPLEMENT_LOAD_BASIS_UNSPECIFIED');
    if(ex?.flags?.bodyweight && allEquipment.some(e=>
        ['barbell','dumbbell','smith','machine','cable'].includes(e)))
      flags.add('BODYWEIGHT_LOAD_SEMANTICS_NEED_REVIEW');
    if(ex?.movementFamily==='elbow_extension' && allEquipment.includes('cable'))
      flags.add('ATTACHMENT_VARIANT_UNSPECIFIED');
    // Legacy metadataConfidence/curated membership cannot certify science.
    flags.add('INDEPENDENT_COEFFICIENT_REVIEW_REQUIRED');
    const equipmentGroups=[allEquipment,...alternatives].map(group=>[...group]);
    return {
      id,name,semanticName,source,sourceOrdinal,
      canonicalStatus:shadowReason?'SHADOWED':'ACTIVE',
      canonicalWinnerId:winner??id,
      shadowReason,
      movementFamily:ex?.movementFamily??null,
      equipmentGroups,
      legacyFlags:immutableJsonCopy(ex?.flags??{}),
      muscles,
      preferredReps:immutableJsonCopy(ex?.preferredReps??{}),
      practicalReps:immutableJsonCopy(ex?.practicalReps??null),
      metadataConfidence:ex?.metadataConfidence??null,
      importReviewState:'UNREVIEWED',
      reviewReasons:[...flags].sort()
    };
  });
  const effectiveIds=effective.map(x=>x.id);
  assert.equal(effectiveIds.length,new Set(effectiveIds).size,
    'Canonical web exercise IDs must remain distinct');
  assert.deepEqual(staged.filter(x=>x.canonicalStatus==='ACTIVE').map(x=>x.id),
    effectiveIds,'Catalog snapshot must match the active web catalog ordering');
  const counts={
    curated:curated.length,extended:extendedCatalog.length,
    historicalEntries:staged.length,effective:effectiveIds.length,
    shadowed:staged.filter(r=>r.canonicalStatus==='SHADOWED').length,
    unresolved:staged.filter(r=>r.importReviewState==='UNREVIEWED').length,
    ambiguousMuscle:staged.filter(r=>r.reviewReasons.some(
      f=>f.startsWith('AMBIGUOUS_MUSCLE_'))).length,
    attachmentAmbiguous:staged.filter(r=>r.reviewReasons.includes(
      'ATTACHMENT_VARIANT_UNSPECIFIED')).length,
    equipmentAlternatives:staged.filter(r=>r.reviewReasons.includes(
      'EQUIPMENT_ALTERNATIVE_NEEDS_VARIANT_REVIEW')).length
  };
  return {
    schemaVersion:1,
    provenance:{
      inputFiles:['modules/next-engine/exercise-db.js',
        'modules/next-engine/extended-exercise-catalog.js'],
      curatedSha256:sha256(current),extendedSha256:sha256(extended)
    },
    isolation:{
      authority:'LEGACY_SOURCE_ONLY',
      allCreditsUnreviewed:true,
      mayIssueWorkouts:false,
      mayApproveSubstitution:false,
      mayRewriteHistory:false
    },
    counts,records:staged
  };
}

export function validateLegacyInventory(inventory) {
  assert.equal(inventory.schemaVersion,1);
  assert.ok(inventory.records.length>150);
  assert.equal(inventory.counts.historicalEntries,inventory.records.length);
  assert.equal(inventory.counts.historicalEntries,
    inventory.counts.effective+inventory.counts.shadowed);
  assert.equal(inventory.counts.unresolved,inventory.counts.historicalEntries);
  assert.ok(inventory.records.every(r=>
    r.importReviewState==='UNREVIEWED' &&
    r.reviewReasons.includes('INDEPENDENT_COEFFICIENT_REVIEW_REQUIRED')));
  assert.equal(inventory.isolation.mayIssueWorkouts,false);
  assert.equal(inventory.isolation.mayApproveSubstitution,false);
  assert.equal(inventory.isolation.mayRewriteHistory,false);

  const byId=id=>inventory.records.filter(r=>r.id===id);
  assert.ok(byId('barbell_bench').some(r=>r.source==='curated'));
  assert.ok(byId('bb-bench').length>0);
  assert.ok(byId('db_curl').length>0);
  const backRows=inventory.records.filter(r=>
    Object.hasOwn(r.muscles,'back'));
  assert.ok(backRows.length>0,'Broad back credits must remain visible for manual mapping');
  assert.ok(backRows.every(r=>
    r.reviewReasons.includes('AMBIGUOUS_MUSCLE_BACK')));
  const cableRows=inventory.records.filter(r=>
    r.movementFamily==='elbow_extension' && r.equipmentGroups.some(g=>
      g.includes('cable')));
  assert.ok(cableRows.length>0);
  assert.ok(cableRows.every(r=>
    r.reviewReasons.includes('ATTACHMENT_VARIANT_UNSPECIFIED')));
  assert.ok(inventory.records.filter(r=>r.canonicalStatus==='SHADOWED')
    .every(r=>r.canonicalWinnerId!==r.id||r.shadowReason==='duplicate-id'));
}

if(process.argv[1] && path.resolve(process.argv[1])===
    fileURLToPath(import.meta.url)) {
  const inventory=buildLegacyInventory();
  validateLegacyInventory(inventory);
  if(process.argv.includes('--write-report')) {
    const report=path.join(root,
      'android-native/kotlin-engine/reports/legacy-exercise-inventory.json');
    fs.mkdirSync(path.dirname(report),{recursive:true});
    fs.writeFileSync(report,JSON.stringify(inventory,null,2)+'\n');
    console.log('Wrote unreviewed source inventory:',report);
  }
  console.log('PASS legacy exercise catalog staging:',JSON.stringify(inventory.counts),
    '(no prescription or review permissions)');
}
