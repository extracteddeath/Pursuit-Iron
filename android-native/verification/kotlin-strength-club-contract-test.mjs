import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
const read = (p) => readFileSync('android-native/'+p,'utf8');
const facts=read('native-coach-store/src/main/java/io/github/extracteddeath/pursuitiron/nativecoachstore/NativeKotlinMixedHistoryFactsDomain.kt');
const club=read('native-coach-store/src/main/java/io/github/extracteddeath/pursuitiron/nativecoachstore/NativeStrengthClubDomain.kt');
const panel=read('app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinStrengthClubPanel.kt');
const profile=read('app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinProfileScreen.kt');
const standards=read('app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinStrengthStandardsScreen.kt');

assert.match(facts,/bestLoggedSingleByLoad/);
assert.match(facts,/if \(actual\.reps == 1\)/);
assert.match(club,/fact\.key\.basis!=LoadBasis\.BARBELL_TOTAL/);
assert.match(club,/facts\.bestLoggedSingleByLoad\.values/);
assert.match(club,/fact\.reps!=1/);
assert.match(club,/if\(requiredLiftIds\.all \{it in rows\}\)/);
assert.match(panel,/Estimated 1RM total/);
assert.match(panel,/logged-single milestone/);
assert.match(profile,/it\.athleteId==current\?\.athleteProfileId/);
assert.match(profile,/NativeKotlinStrengthClubPanel\(strengthClub/);
assert.match(standards,/NativeKotlinStrengthClubPanel\(strengthClub/);
assert.doesNotMatch(panel,/approve\(|record\(|issue\(|saveWorkout\(/);

// Machine-specific logged bests must remain separate from the barbell-only
// community standards and from uncertified app-wide PR badges.
const physical=read('app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinMachineRecordsPanel.kt');
assert.match(facts,/data class NativeMixedMachineLoadKey/);
assert.match(facts,/val gymId:String/);
assert.match(facts,/val machineInstanceId:String/);
assert.match(facts,/val highestCompletedLoadByMachine:/);
assert.match(facts,/val bestLoggedSingleByMachine:/);
assert.match(facts,/completion\.evidence\.source\.gymId/);
assert.match(facts,/completion\.gymId/);
assert.match(facts,/KotlinMachineAlternativesDomain\.validInstanceId\(machineId\)/);
const loadAuthority=read('kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/KotlinSetNumberAuthorityDomain.kt');
assert.match(facts,/if\(!KotlinSetNumberAuthorityDomain\.machineTagContradictsFreeBarbell\(\s*target\.basis,machineId\)\)/);
assert.match(loadAuthority,/fun machineTagContradictsFreeBarbell\(basis:LoadBasis,machineInstanceId:String\?\):Boolean/);
assert.match(loadAuthority,/basis==LoadBasis\.BARBELL_TOTAL && machineInstanceId!=null/);
assert.match(facts,/Collections\.unmodifiableMap\(highestByMachine\)/);
assert.match(physical,/title="Physical machine records"/);
assert.match(physical,/Each physical machine has its own completed working-set history/);
assert.match(physical,/not comparable strength scores or PR badges/);
assert.match(facts,/val lowestAssistanceByMachine:/);
assert.match(physical,/Least assistance/);
assert.match(physical,/assistanceByMachine/);
assert.match(standards,/assistanceByMachine=verifiedFacts.lowestAssistanceByMachine/);
assert.match(facts,/if\(assisted\) \{/);
assert.match(facts,/lowestAssistanceByMachine\[station\]=record/);
// Display identity now resolves through a read-only label adapter. Preserve
// the exact history scope and require matching gym/exercise/machine on lookup.
const stationLabels=read('app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinMachineStationLabels.kt');
assert.match(physical,/NativeKotlinMachineStationLabels\.label\(\s*key\.exerciseId,key\.gymId,key\.machineInstanceId/);
assert.match(stationLabels,/it\.gymId==gymId && it\.exerciseId==exerciseId/);
assert.match(stationLabels,/it\.id==machineId/);
assert.match(physical,/singlesByMachine\[key\]/);
assert.match(physical,/onOpenHistory\(key\.exerciseId\)/);
assert.doesNotMatch(physical,/approve\(|recordWorkoutOperation\(|saveMachineRoster\(/);
assert.match(standards,/NativeKotlinMachineRecordsPanel\(/);
assert.match(standards,/verifiedFacts\.highestCompletedLoadByMachine/);
assert.match(standards,/verifiedFacts\.bestLoggedSingleByMachine/);
assert.match(standards,/host\.readMachineRoster\(\)/);
assert.match(standards,/timeline\?\.manifestRevision==stamp/);
console.log('PASS Strength Club excludes assisted counterweights from barbell PRs and displays honest gym-scoped least-assistance machine records');
