import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const app='android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/';
const store='android-native/native-coach-store/src/main/java/io/github/extracteddeath/pursuitiron/nativecoachstore/';
const read=p=>readFileSync(p,'utf8');
const host=read(app+'NativeKotlinCandidateHost.kt');
const screen=read(app+'NativeKotlinCandidateHistoryScreen.kt');
const dates=read(app+'NativeKotlinHistoryDates.kt');
const ledger=read(store+'NativeKotlinHistoryManifestFileStore.kt');
const clock=read(store+'NativeHistoryCompletionClock.kt');
const csv=read(store+'NativeKotlinVerifiedHistoryCsvExport.kt');

assert.match(host,/historyStore,customIssuedStore,revisionFence,localAthlete,NativeHistoryCompletionClock\.System/);
assert.match(host,/historyStore,journalStore,correctionStore,approvalStore,revisionFence,NativeHistoryCompletionClock\.System/);
assert.match(clock,/runCatching\{epochMillis\(\)\}/);
assert.match(clock,/takeIf\{KotlinHistoryCompletionTime\.valid\(it\)\}/);
assert.match(ledger,/resolved\.completedAtEpochMillis!=intent\.completedAtEpochMillis/);
assert.match(ledger,/if\(format>=3\)data\.time\(\) else null/);
for(const name of ['NativeKotlinHistoryTransactionCoordinator.kt','NativeKotlinCustomHistoryTransaction.kt']) {
 const code=read(store+name);
 assert.match(code,/completionClock\.observe\(\)/);
 assert.match(code,/intent\.completedAtEpochMillis/);
}
assert.match(screen,/nativeHistoryMonths\(visibleSessions,historyZone\)/);
assert.match(screen,/monthSections\.forEach/);
assert.match(screen,/section\.title\(historyLocale\)/);
assert.match(screen,/items\(section\.sessions,key=\{"session:/);
assert.match(screen,/nativeHistoryDateLabel\(entry\.manifest,historyZone,historyLocale\)/);
assert.match(screen,/Older sessions and sessions without a recorded clock value stay undated/);
assert.match(screen,/filteredMovements\.take\(visibleExerciseCount\)/);
assert.match(screen,/Show more matching exercises/);
assert.doesNotMatch(screen,/filteredMovements\.take\(8\)|Showing the first 8/);
assert.match(dates,/Instant\.ofEpochMilli/);
assert.match(dates,/Date not recorded/);
assert.doesNotMatch(dates,/currentTimeMillis|Instant\.now|lastModified|creationTime/);
assert.match(csv,/listOf\("completed_at_utc"\)/);
assert.match(csv,/if\(dated\)"pursuit-iron-history-v2" else VERSION/);
console.log('PASS recorded Finish dates, durable recovery wiring, month-grouped History and lossless UTC export');
