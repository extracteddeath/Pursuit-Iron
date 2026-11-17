import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const dir='android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/';
const domain=readFileSync(dir+'NativeKotlinTrainingArchiveDomain.kt','utf8');
const panel=readFileSync(dir+'NativeKotlinTrainingArchivePanel.kt','utf8');
const profile=readFileSync(dir+'NativeKotlinProfileScreen.kt','utf8');
const history=readFileSync('android-native/native-coach-store/src/main/java/io/github/extracteddeath/pursuitiron/nativecoachstore/NativeKotlinVerifiedHistoryCsvExport.kt','utf8');
const bodyweight=readFileSync(dir+'NativeKotlinBodyweightLedger.kt','utf8');
const tests=readFileSync('android-native/app/src/test/java/io/github/extracteddeath/pursuitiron/NativeKotlinTrainingArchiveDomainTest.kt','utf8');
const workflow=readFileSync('.github/workflows/android-kotlin-rebuild.yml','utf8');

assert.match(domain,/NativeKotlinVerifiedHistoryCsvExport\.prepare\(history\)/);
assert.match(domain,/history\.athleteId!=athleteId/);
assert.match(domain,/NativeBodyweightLedgerDomain\.validEntry\(it,LocalDate\.MAX\)/);
assert.match(domain,/ledger\.entries\.map\{it\.date\}\.distinct\(\)\.size!=ledger\.entries\.size/);
assert.match(domain,/ZipOutputStream\(sink,StandardCharsets\.UTF_8\)/);
assert.match(domain,/put\("training-history\.csv",csv\.csv\)/);
assert.match(domain,/put\("bodyweight-history\.csv",measurements\)/);
assert.match(domain,/put\("README\.txt",readme\)/);
assert.match(domain,/not a restorable app backup/);
assert.match(domain,/Files are unencrypted/);
assert.match(domain,/MAX_ARCHIVE_BYTES=24\*1024\*1024/);
assert.doesNotMatch(domain,/\.issue\(|\.record\(|\.approve\(|\.publish\(|\.mutate\(|\.post\(|\.upload\(/);

assert.match(panel,/ActivityResultContracts\.CreateDocument\("application\/zip"\)/);
assert.match(panel,/var pending by remember\(host\)/);
assert.match(panel,/pending=null/);
assert.match(panel,/LaunchedEffect\(athleteId\)/);
assert.match(panel,/host\.currentHistoryExportOwner\(\)/);
assert.match(panel,/host\.reviewActiveAthleteSetup\(\)/);
assert.match(panel,/host\.reviewVerifiedKotlinHistory\(\)/);
assert.match(panel,/store\.read\(before\.athleteId\)/);
assert.match(panel,/before!=after \|\| before\.athleteId!=athleteId/);
assert.match(panel,/history\.manifestRevision!=historyRevision/);
assert.match(panel,/ticket!=expected \|\| records==null/);
assert.match(panel,/check\(before!=null &&/);
assert.match(panel,/context\.contentResolver\.openOutputStream\(uri,"wt"\)/);
assert.match(panel,/output\.write\(archive\.bytes\)/);
assert.match(panel,/No records were shared/);
assert.match(panel,/It cannot restore the app/);
assert.doesNotMatch(panel,/Intent\.ACTION_SEND|https?:\/\/|startActivity\(|host\.record|host\.approve/);

assert.match(profile,/NativeKotlinTrainingArchivePanel\(/);
assert.match(profile,/athleteId=current\?\.athleteProfileId/);
assert.match(profile,/historyRevision=timeline\?\.manifestRevision/);
assert.match(history,/it=='=' \|\| it=='\+'/);
assert.match(bodyweight,/getSharedPreferences\("pursuit-bodyweight-ledger-v1",Context.MODE_PRIVATE\)/);
assert.match(workflow,/node android-native\/verification\/kotlin-verified-training-archive-test\.mjs/);

for(const title of [
'archivedHistoryAndBodyweightAreSeparatePortableFactualCsvs',
'crossAthleteAndUnverifiedHistoryAreRejectedWithoutOutput',
'malformedOrOverPreciseBodyweightRefusesEntireExport',
'emptyWeighInsStillProduceExplicitEmptyHistoryFile',
'identicalSourceProducesStablePortableBytes'])
    assert.ok(tests.includes(title),'Missing JVM archive test '+title);
console.log('PASS native beta export: single explicitly chosen ZIP, factual verified workouts + weigh-ins, owner/revision-fenced after picker, no backup claim.');
