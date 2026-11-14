import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {dirname,resolve,join} from 'node:path';

/**
 * Source-level UI integration sentinel. Real engine/stock behavior is also
 * exercised by Kotlin unit tests and requires device acceptance separately.
 * No JS code is included in the Android release.
 */
const root=resolve(dirname(fileURLToPath(import.meta.url)),'../..');
const onboarding=readFileSync(join(root,
    'android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinOnboardingCandidateScreen.kt'),'utf8');
const catalog=readFileSync(join(root,
    'android-native/kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/StarterExerciseTranche.kt'),'utf8');
const gymEditor=readFileSync(join(root,
    'android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinGymSourceCandidateScreen.kt'),'utf8');

assert.match(catalog,/"cable-station",LoadBasis\.CABLE_STACK/);
assert.match(onboarding,/stock\["cable-station"\]=VerifiedLoadStock\.Stack\(/,
    'onboarding must save a cable under the ID consumed by the actual Kotlin catalog');
assert.doesNotMatch(onboarding,/stock\["cable-1"\]/,
    'a disconnected standalone cable ID would make all exercises unavailable');
assert.match(onboarding,/availableReviewedAttachments=remember/);
assert.match(onboarding,/selected=confirmedAttachments\?\.contains\(id\)==true/,
    'cable grip chips must reflect the saved, explicit attachment IDs');
assert.match(onboarding,/attachments=next\.sorted\(\)\.joinToString\(", "\)/);
assert.match(onboarding,/onboardingMachineChoices=remember/);
assert.match(onboarding,/LoadBasis\.EXERCISE_OVERRIDE/,
    'machine choices must use exact source-backed physical machine IDs');
assert.match(onboarding,/if\(machineId!=id\)machineLoads=""/,
    'switching physical machines must discard the other machine\'s unsaved load values');
assert.match(onboarding,/gymConfirmed=false/g,
    'a changed physical inventory must invalidate prior confirmation');
assert.match(gymEditor,/internal fun kotlinGymAttachmentLabel/,
    'human-readable onboarding attachment labels share the gym editor');

// The runtime catalog can grow independently of the setup screen. Every
// required reviewed fixture must remain selectable by its normal name in
// BOTH onboarding and the saved gym editor, without knowing internal IDs.
const facilities=readFileSync(join(root,
    'android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinGymFacilityChoices.kt'),'utf8');
const named=[...facilities.matchAll(/"([a-z0-9-]+)" to "([^"\n]+)"/g)]
    .map(([,id,label])=>({id,label}));
assert.equal(named.length,new Set(named.map(x=>x.id)).size,
    'a physical fixture ID must have one unambiguous named choice');
const available=new Set(named.map(x=>x.id));
const manifest=JSON.parse(readFileSync(join(root,
    'android-native/kotlin-engine/verification/reviewed-catalog-expansion.json'),'utf8'));
const required=new Set(manifest.records.flatMap(x=>x.review.fixtures));
// Include the original stable prefix, whose declarations predate the
// reviewed manifest. Focus enums and non-fixture sets do not match here.
const prefix=catalog.split('// BEGIN REVIEWED_WEB_EXPANSION_V2')[0];
for(const match of prefix.matchAll(/(?:LoadBasis\.[A-Z_]+|null),setOf\(([^)]*)\)/g))
    for(const id of match[1].matchAll(/"([^"\n]+)"/g))required.add(id[1]);
assert.ok(required.size>15,'fixture coverage must inspect the actual reviewed catalog');
for(const id of required) assert.ok(available.has(id),
    'Reviewed exercises require a named physical fixture choice: '+id);
assert.match(onboarding,/NativeKotlinGroupedFacilityChoices\(/);
assert.match(gymEditor,/NativeKotlinGroupedFacilityChoices\(/);
assert.match(facilities,/if\(checked\)selected\+id else selected-id/,
    'named choices must preserve all other saved/advanced IDs');
assert.match(facilities,/Additional saved IDs are preserved/);
console.log(`PASS exact cable stock and all ${required.size} reviewed fixture names in onboarding and gym editing`);
