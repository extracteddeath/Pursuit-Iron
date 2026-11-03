import assert from 'node:assert/strict';
import fs from 'node:fs';

const read=(name)=>fs.readFileSync(name,'utf8');
const catalog=read('android-native/domain-contract/src/main/java/io/github/extracteddeath/pursuitiron/domain/FeatureCatalog.kt');
const scope=read('android-native/kotlin-engine/ENGINE_BASELINE_COVERAGE.md');
const ux=read('android-native/kotlin-engine/NATIVE_ENGINE_UX_IMPACT.md');
const index=read('android-native/kotlin-engine/README.md');
const plan=read('android-native/ENGINE_REBUILD_PLAN.md');
const replacement=read('android-native/kotlin-engine/FULL_REPLACEMENT_CONTRACT.md');


const ids=[...catalog.matchAll(/FeatureCapability\("([^"]+)"/g)].map(match=>match[1]);
assert.equal(ids.length,14,'14 stable native product capabilities required');
assert.equal(new Set(ids).size,ids.length,'duplicate FeatureCatalog IDs');
const lines=scope.split('\n');
for(const id of ids) {
    const found=lines.filter(line=>line.startsWith('| '+id+' |'));
    assert.equal(found.length,1,'Missing or duplicate capability ledger row: '+id);
    assert.ok(found[0].includes(' P:')||found[0].includes('| P:')||
        found[0].includes(' P;')||found[0].includes('P: '),
        'Capability row needs explicit open-work evidence: '+id);
}
for(let i=1;i<=41;i++){
    const id='E'+String(i).padStart(2,'0');
    assert.equal(lines.filter(line=>line.startsWith('- '+id+' **')).length,1,
        'Missing or duplicate basic engine requirement '+id);
}
const uxLines=ux.split('\n');
for(let i=1;i<=13;i++) {
    const id='U'+String(i).padStart(2,'0');
    assert.equal(uxLines.filter(line=>line.startsWith('| '+id+' |')).length,1,
        'Missing or duplicate native acceptance scenario '+id);
}
for(const phrase of ['Full Body','ULPPL','powerbuilding','40–60','120+',
    'biceps/brachialis','wrist-flexion/extension','superset','myo-reps',
    'adapt','backup','device','Stage','P0']) {
    assert.ok(scope.toLowerCase().includes(phrase.toLowerCase()),
        'Missing basic engine area: '+phrase);
}
for(const phrase of ['Onboarding','Plan builder','Today','Workout preview',
    'Train / one-page logger','Focus','Insights','Profile','Backup/restore',
    '20 original themes','TalkBack','U13','Never auto-merge']) {
    assert.ok(ux.toLowerCase().includes(phrase.toLowerCase()),
        'Missing native UI/UX requirement: '+phrase);
}
assert.ok(index.includes('ENGINE_BASELINE_COVERAGE.md') &&
    index.includes('NATIVE_ENGINE_UX_IMPACT.md'));
assert.ok(plan.includes('ENGINE_BASELINE_COVERAGE.md') &&
    plan.includes('NATIVE_ENGINE_UX_IMPACT.md'));
assert.ok(scope.includes('not certification')||scope.includes('not a completed'));
assert.ok(ux.includes('documentation')||ux.includes('Documented'));
// Replacement is a binding finish-line requirement, NOT a claim of readiness.
// Keep a gate for retiring the JS runtime alongside the 14 feature and UX gates.
for(const term of [
    'Kotlin-only', 'JavaScript', 'No JS as hidden fallback',
    '14 capability ledger', 'migration', 'complete logger',
    'in-progress JS', 'engine-runtime', 'release APK',
    'separate explicit product decision'
]) {
    assert.ok(replacement.toLowerCase().includes(term.toLowerCase()),
        'Missing full JS replacement / decommissioning requirement: '+term);
}
assert.ok(index.includes('FULL_REPLACEMENT_CONTRACT.md'),
    'Kotlin engine index must explicitly point to the full replacement requirement');
const app=read('android-native/app/build.gradle.kts');
const stillJs=app.includes('implementation(project(":engine-runtime"))');
assert.ok(stillJs,
    'This isolated research branch should NOT silently switch the app engine. Cutover needs a separately approved integration pass.');
console.log('PASS scope: '+ids.length+' features, E01–E41 engine requirements, U01–U13 native UX tests, and mandatory Kotlin-only final runtime cutover (currently pending).');
