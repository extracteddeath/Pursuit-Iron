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
console.log('PASS onboarding cable stock ID matches Kotlin catalog and gym choices remain explicit');
