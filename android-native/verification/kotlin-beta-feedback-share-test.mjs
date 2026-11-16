import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {dirname,resolve,join} from 'node:path';
import {fileURLToPath} from 'node:url';

const root=resolve(dirname(fileURLToPath(import.meta.url)),'../..');
const read=(file)=>readFileSync(join(root,file),'utf8');
const screen=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCoachSettingsScreen.kt');
const domain=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinBetaFeedback.kt');
const app=read('android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCandidateJourney.kt');
assert.match(app,/NativeKotlinCoachSettingsScreen\(host,/);
assert.match(screen,/Text\("Share beta feedback"\)/);
assert.match(screen,/Text\("Report a problem"\)/);
assert.match(screen,/if\(problemOpen\) \{/);
assert.match(screen,/NativeEditSheet\(/);
for(const label of [
 'Screen or workout step (optional)',
 'Steps to reproduce (optional)',
 'What should have happened? (optional)',
 'What actually happened? (required)'
]) assert.ok(screen.includes(label),label);
assert.match(screen,/NativeKotlinBetaFeedbackFormat\.canShare\(draft\)/);
assert.match(screen,/NativeBetaFeedbackKind\.PROBLEM/);
assert.match(screen,/Intent\(Intent\.ACTION_SEND\)/);
assert.match(screen,/Intent\.createChooser\(/);
assert.match(screen,/context\.packageManager\.getPackageInfo/);
assert.match(screen,/if\(shareWithChosenApp\(draft\)\)problemOpen=false/);
assert.match(screen,/No app could open the problem report share sheet/);
assert.match(screen,/Only what you type and the installed app version are included/);
assert.doesNotMatch(screen,/Intent\.EXTRA_(EMAIL|STREAM)/);
assert.doesNotMatch(screen,/host\.reviewVerifiedKotlinHistory\(/);
assert.doesNotMatch(domain,/ownerKey|athleteId|journal\./);
assert.match(domain,/fun canShare\(draft:NativeBetaFeedbackDraft\)/);
assert.match(domain,/fun format\(draft:NativeBetaFeedbackDraft,installedVersion:String\)/);
console.log('PASS accessible structured beta problem feedback with user-selected destination and no implicit training-data attachment');
