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
const sha256=buffer=>createHash('sha256').update(buffer).digest('hex');

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
    missingByMovementFamily:families,
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

function selfTest() {
  assert.deepEqual(parseNativeStarterIds(
    'entry("bench","family",...)\nentry("wrist-curl","family",...)\nentry("reverse-wrist-curl","family",...)'
  ),['bench','wrist-curl','reverse-wrist-curl'],
  'hyphenated canonical IDs must not vanish from Kotlin starter inventory');
  const declaredCount=Number(native.match(/require\\(definitions\\.size==(\\d+)\\)/)?.[1]);
  assert.ok(Number.isInteger(declaredCount) && declaredCount>0,
    'Kotlin starter must declare a checked definition count');
  assert.equal(nativeStarterIds.length,declaredCount,
    'starter parser must include every declared Kotlin definition');
  const sample=[
    {id:'bench',name:'Bench',movementFamily:'horizontal_press'},
    {id:'cable_press',name:'Cable Press',movementFamily:'horizontal_press'}
  ];
  const incomplete=reviewCatalogCoverage(sample,['bench']);
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
  const report=reviewCatalogCoverage(EXERCISES,nativeStarterIds);
  report.source={
    legacyCatalogFiles:webPaths.map(p=>({path:p,sha256:sha256(readFileSync(join(root,p)))})),
    nativeStarterPath:'android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/StarterExerciseTranche.kt',
    nativeStarterSha256:sha256(native)
  };
  report.scope='Catalog source coverage only. Not program, cycle, progression or Android feature parity.';
  // Never print hundreds of rows into CI logs; full rows remain in artifact.
  console.log(JSON.stringify({
    verdict:report.verdict,
    webEffectiveBuiltins:report.webEffectiveBuiltins,
    kotlinStarterDefinitions:report.kotlinStarterDefinitions,
    exactIdMatches:report.exactIdMatches,
    webBuiltinsNotInStarter:report.webBuiltinsNotInStarter,
    independentlyCertifiedMappings:report.independentlyCertifiedMappings,
    topUnmappedFamilies:report.missingByMovementFamily.slice(0,12)
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
