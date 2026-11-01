import assert from 'node:assert/strict';
import fs from 'node:fs';
const catalog = fs.readFileSync('android-native/domain-contract/src/main/java/io/github/extracteddeath/pursuitiron/domain/FeatureCatalog.kt','utf8');
const contract = fs.readFileSync('android-native/FEATURE_FIRST_ARCHITECTURE.md','utf8');
const gateway = fs.readFileSync('android-native/domain-contract/src/main/java/io/github/extracteddeath/pursuitiron/domain/EngineGateway.kt','utf8');
const required = [
  'adaptive-progression','workout-logging','session-adjustments','workout-tools',
  'program-builder','custom-programs','adaptive-cycles','exercise-discovery',
  'history-and-prs','volume-and-trends','bodyweight','backup-restore',
  'gyms-and-equipment','profile'
];
const ids = [...catalog.matchAll(/FeatureCapability\("([^"]+)"/g)].map(x=>x[1]);
assert.deepEqual([...new Set(ids)], ids, 'Feature identifiers must be unique');
assert.deepEqual(ids, required, 'Critical feature contract inventory changed unexpectedly');
assert.match(catalog,/CANONICAL_ENGINE/);
assert.match(catalog,/VERIFIED_STORAGE/);
assert.match(catalog,/ANDROID_DEVICE/);
assert.match(contract,/not a pixel, component or navigation specification/);
assert.match(contract,/Full\/45\/30\/20/);
assert.match(contract,/No screen-by-screen cloning mandate/);
assert.match(gateway,/UnconnectedEngineGateway/);
console.log('PASS native feature-first capability contract: 14 outcomes and safety gates');
