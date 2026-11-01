import assert from 'node:assert/strict';
import fs from 'node:fs';
const root='android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/';
const ui=fs.readFileSync(root+'NativeOnboardingScreen.kt','utf8');
const host=fs.readFileSync(root+'NativeRuntimeHost.kt','utf8');
const activity=fs.readFileSync(root+'MainActivity.kt','utf8');
const shell=fs.readFileSync(root+'NativePreviewApp.kt','utf8');
for (const field of ['name','goal','experience','unit','split','days','session','equipment','bodyweight','supersets','barbellCap'])
  assert.match(ui,new RegExp('var '+field+' by rememberSaveable'));
assert.ok(ui.includes('state.options?.splits') && ui.includes('selected.days.forEach') &&
  ui.includes('if(days !in option.days)days=option.days.first()'),
  'Split/day compatibility must be canonical option data, not a second Kotlin topology engine');
assert.ok(ui.includes('Review generated plan') && ui.includes('Save program') && ui.includes('Edit choices') &&
  ui.includes('if (proposal!=null)') && ui.includes('ReadOnlyTrainingDayRow('));
assert.ok(!ui.includes('.initialize(') && !ui.includes('generateNextProgramForShell') &&
  !ui.includes('commitAtomically') && !ui.includes('Complete set'));
assert.ok(host.includes('owner.initialize(proposal.snapshot)') &&
  host.includes('saved.creationId==proposal.creationId') &&
  host.includes('finally { bytes.fill(0) }') && host.includes('blocked=true') &&
  host.includes('actionVersion.get()==version'));
assert.ok(activity.includes('onReviewProgram=runtime::review') && activity.includes('onSaveProgram=runtime::save') &&
  activity.includes('activeProgramId=state.saved?.program?.id') && shell.includes('onboarding.saved==null'));
assert.ok(shell.includes('UnconnectedEngineGateway().status'),
  'First-program persistence must not authorize workout logging');
console.log('PASS native onboarding draft retention, canonical options, review/save separation, single-owner restore and logging gate');
