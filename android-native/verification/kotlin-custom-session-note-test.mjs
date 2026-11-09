import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const read=(path)=>readFileSync('android-native/'+path,'utf8');
const domain=read('kotlin-engine/src/main/java/io/github/extracteddeath/pursuitiron/rebuild/CustomWorkoutJournalDomain.kt');
const codec=read('native-coach-store/src/main/java/io/github/extracteddeath/pursuitiron/nativecoachstore/NativeCustomWorkoutJournalCodec.kt');
const store=read('native-coach-store/src/main/java/io/github/extracteddeath/pursuitiron/nativecoachstore/NativeKotlinCustomWorkoutJournalFileStore.kt');
const host=read('app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCandidateHost.kt');
const screen=read('app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCustomJournalScreen.kt');
const sheet=read('app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCustomSessionNoteSheet.kt');

assert.match(domain,/data class SessionNote\(/);
assert.match(domain,/MAX_SESSION_NOTE_CHARS=2000/);
assert.match(domain,/validSessionNote\(operation\.text\)/);
assert.match(domain,/val sessionNote:String get\(\)=receipts/);
assert.match(codec,/private const val VERSION=3/);
assert.match(codec,/val version=if\(notes\) VERSION else if\(swapped\) 2 else 1/);
assert.match(codec,/writeByte\(5\)/);
assert.match(codec,/if\(version<3\)throw IOException/);
assert.match(codec,/data\.operation\(version\)/);

const save=store.slice(store.indexOf('fun saveSessionNote('),
    store.indexOf('Internal HALF of a redo-backed Finish'));
assert.match(save,/fence\.withOwnerTransaction\(ownerKey\)/);
assert.match(save,/history\.open\(ownerKey,current\.athleteId\)/);
assert.match(save,/readyForHistoryReads/);
assert.match(save,/CustomWorkoutJournalDomain\.reduce\(/);
assert.match(save,/atomic\(path\(ownerKey,workoutId\),encode\(saved\)\)/);
assert.doesNotMatch(save,/applyPendingFinish|CustomWorkoutJournalOperation\.Finish/);
assert.match(host,/localAthlete\.signedInIdentity\(\)/);
assert.match(host,/customIssuedStore\.saveSessionNote\(/);

assert.match(screen,/NativeKotlinCustomSessionNoteSheet\(/);
assert.match(screen,/host\.saveIssuedCustomSessionNote\(workoutId,operation\)/);
assert.match(screen,/CustomWorkoutJournalOperation\.SessionNote\(/);
assert.match(screen,/current\.sessionNote/);
assert.match(sheet,/rememberSaveable\(journal\.workoutId\)/);
assert.match(sheet,/if\(!busy && error==null && submitted!=null && committed==submitted\)/);
assert.match(sheet,/enabled=!busy && dirty && valid/);
assert.match(sheet,/Discard draft/);
assert.match(sheet,/Save session note/);
console.log('PASS owner-authorized, versioned and replayable custom session note with durable UI acknowledgment');
