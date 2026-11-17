import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const dir='android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/';
const ledger=readFileSync(dir+'NativeKotlinBodyweightLedger.kt','utf8');
const panel=readFileSync(dir+'NativeKotlinBodyweightPanel.kt','utf8');
const progress=readFileSync(dir+'NativeKotlinStrengthStandardsScreen.kt','utf8');
const tests=readFileSync(
  'android-native/app/src/test/java/io/github/extracteddeath/pursuitiron/NativeKotlinBodyweightLedgerTest.kt','utf8');
const gradle=readFileSync('android-native/app/build.gradle.kts','utf8');

assert.match(gradle,/\*\*\/NativeKotlin\*\.kt/);
assert.match(ledger,/data class NativeBodyweightEntry\(/);
assert.match(ledger,/val date:LocalDate/);
assert.match(ledger,/val amount:BigDecimal/);
assert.match(ledger,/val unit:LoadUnit/);
assert.match(ledger,/NativeBodyweightRefusal\.REPLACEMENT_CONFIRMATION_REQUIRED/);
assert.match(ledger,/NativeBodyweightRefusal\.REVISION_CONFLICT/);
assert.match(ledger,/NativeBodyweightRefusal\.CORRUPT/);
assert.match(ledger,/SHA-256/);
assert.match(ledger,/getSharedPreferences\("pursuit-bodyweight-ledger-v1",Context.MODE_PRIVATE\)/);
assert.match(ledger,/fun save\(owner:String,expectedRevision:Long/);
assert.match(ledger,/@Synchronized/);
assert.match(ledger,/if\(prior\.revision!=expectedRevision\)/);
assert.match(ledger,/prefs\.edit\(\)\.putString\(key\(owner\),encoded\)\.commit\(\)/);
assert.match(ledger,/records\.map\{it\.date\}\.distinct\(\)\.size!=count/);
assert.doesNotMatch(ledger,/KotlinPerformedSet\(|RecordSet\(|issueWorkout\(|applyProgression\(|changePrescription\(/);

assert.match(panel,/host\.reviewActiveAthleteSetup\(\)/);
assert.match(panel,/if\(confirmed==owner\)store\.read\(owner\)/);
assert.match(panel,/if\(confirmed!=selectedOwner\)NativeBodyweightWrite\.Rejected/);
assert.match(panel,/if\(existing!=null\)pendingReplace=observation/);
assert.match(panel,/pendingDelete=record\.date/);
assert.match(panel,/NativeBodyweightLedgerDomain\.replace/); // through store only
assert.match(panel,/onUseForComparison\(record\.amount,record\.unit\)/);
assert.match(panel,/Canvas\(Modifier\.fillMaxWidth\(\)\.height\(164\.dp\)/);
assert.match(panel,/date\.toEpochDay\(\)\.toFloat\(\)-firstDay/);
assert.match(panel,/contentDescription="Dated bodyweight trend/);
assert.match(panel,/Bodyweight history is private to this installation/);
assert.doesNotMatch(panel,/host\.record\(|host\.issue|controller\.record\(/);

assert.match(progress,/NativeKotlinBodyweightPanel\(/);
assert.match(progress,/host=host,athleteId=profileId,preferredUnit=displayUnit/);
assert.match(progress,/onUseForComparison=\{value,unit->/);
assert.match(progress,/comparisonExpanded=true/);
assert.match(progress,/Selected a logged measurement for comparison/);
assert.doesNotMatch(progress,/NativeKotlinBodyweightPanel\([\s\S]{0,300}preferences\.edit\(/);

for(const sample of [
  'emptyAndMultiDateRoundTripAcrossColdRead',
  'sameDayCorrectionRequiresExplicitApprovalAndKeepsOtherDates',
  'deleteIsExplicitExactDateAndCannotSilentlyEraseOtherDays',
  'noFutureOrUnrealisticOrOverpreciseWeightsAreEverSaved',
  'tamperingWrongOwnerAndUnsupportedSchemaNeverResetMeasurements'
])assert.ok(tests.includes(sample), 'Missing bodyweight ledger JVM test: '+sample);
console.log('PASS Kotlin Progress: dated bodyweight log/plot, owner-fenced explicit correction/delete, no training-authority borrowing.');
