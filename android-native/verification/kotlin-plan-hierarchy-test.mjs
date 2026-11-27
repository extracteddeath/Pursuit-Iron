import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const overview=readFileSync(
 'android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCustomCycleOverview.kt','utf8');

// The entire program is accessible without initially revealing every day/set.
// Current-block-only is an optional user toggle, never an inferred grant.
assert.match(overview,/var onlyNextBlock by rememberSaveable\(cycle\.cycleId\)/);
assert.match(overview,/mutableStateOf\(false\)/);
assert.match(overview,/label=\{Text\("Current block"\)\}/);
assert.match(overview,/label=\{Text\("Entire program"\)\}/);
assert.match(overview,/cycle\.blocks\.forEachIndexed \{ index,block ->/);
assert.match(overview,/val showBlock=!onlyNextBlock \|\| !hasNextSession/);
assert.match(overview,/if\(showBlock\)/);
assert.match(overview,/val startWeek=1\+cycle\.blocks\.take\(index\)\.sumOf\{it\.weeks\}/);
assert.match(overview,/val endWeek=startWeek\+block\.weeks-1/);
assert.match(overview,/PursuitDisclosureRow\([\s\S]*?title=block\.label/);
assert.match(overview,/rememberSaveable\(cycle\.cycleId,block\.blockId,week\)/);
assert.match(overview,/if\(hasNextSession\)block==selectedBlock else index==0/);
assert.match(overview,/if\(blockExpanded\)displayDays\.sortedBy/);
assert.match(overview,/val displayDays=exactWeek\?\.takeIf\{it\.blockId==block\.blockId\}\s*\?\.days \?: block\.days/);

// A verified NEXT, PREPARED, or RESUMABLE day is labeled correctly.
for(const kind of ['Next','Prepared','ResumeIssued'])
    assert.match(overview,new RegExp('NativeCustomNextCycleSlot\\.'+kind));
assert.match(overview,/is NativeCustomNextCycleSlot\.ResumeIssued -> "Resume workout"/);
assert.match(overview,/is NativeCustomNextCycleSlot\.Prepared -> "Prepared workout"/);
assert.match(overview,/is NativeCustomNextCycleSlot\.Next -> "Next workout"/);
assert.match(overview,/val isNextDay=hasNextSession/);
assert.match(overview,/subtitle=\(if\(isNextDay && exactWeek!=null\)"\$selectedStatus · " else ""\)/);

// Workout prescriptions remain inspectable and separately editable; this is
// not a program mutation or an unapproved week override.
assert.match(overview,/if\(showTargets\)/);
assert.match(overview,/slot\.sets\.forEachIndexed/);
assert.match(overview,/onEditDay\(block\.blockId,day\.weekday\)/);
assert.match(overview,/onEditExercise\(block\.blockId,day\.weekday,slot\.slotId\)/);
assert.match(overview,/Current block: full plan for week/);
assert.match(overview,/Week-specific prescription not yet verified/);

// Returning to Plan is a read of the selected native program, not another
// picker reset. Fresh policy checks still run; only a validated Undo survives.
const app='android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/';
const controller=readFileSync(app+'NativeKotlinCustomCycleController.kt','utf8');
const host=readFileSync(app+'NativeKotlinCandidateHost.kt','utf8');
const entry=readFileSync(app+'NativeKotlinPlanEntryReview.kt','utf8');
const requests=readFileSync(app+'NativeKotlinPlanEntryRequests.kt','utf8');
assert.match(controller,/host\.reviewPlanEntry\(current\.value\.draft,openedSource,/);
assert.match(controller,/applyRead\(entry\.cycleId,entry\.read,entry\.source\)/);
assert.match(controller,/recoverSavedCoachPolicy\(entry\.cycleId\)/);
assert.match(controller,/selectNextSessionIfReviewed\(\)/);
assert.match(controller,/entry\.retainedUndo\?\.let\s*\{[\s\S]*?lastUndo=it[\s\S]*?copy\(canUndo=true\)/);
assert.match(controller,/if\(entry\.undoUnavailable\)/);
assert.match(controller,/The previous Undo is no longer valid for the current program and training setup/);
assert.match(host,/fun reviewPlanEntry\([\s\S]*?revisionFence\.withOwnerTransaction\(owner\.ownerKey\)/);
assert.match(host,/NativeKotlinPlanEntryReview\.review\(localAthlete/);
assert.match(entry,/source==visibleSource/);
assert.match(entry,/visibleDraft==found/);
assert.match(entry,/CustomCycleBuilderDomain\.undo\(found,it\)/);
assert.match(entry,/identity\.signedInIdentity\(\)/);

// Navigation read requests wait behind an in-flight action; ordinary edits
// retain their original duplicate-write guard. Return never displaces an
// explicit newly-created or selected program waiting to be opened.
assert.match(controller,/if \(!pending\.compareAndSet\(false, true\)\) return/);
assert.match(controller,/if\(scope\.isActive && planEntries\.hasPending\)drainPlanEntries\(\)/);
assert.match(controller,/fun open\(cycleId: String\) = enter/);
assert.match(controller,/fun openSavedTraining\(\) = enter\(explicit=false\)/);
assert.match(controller,/fun openSavedFirstProgramForReview\([\s\S]*?\)=enter/);
assert.match(controller,/planEntries\.clear\(\)/);
assert.match(controller,/reopenAfterConfirmedFutureWeekSave\(cycleId:String\)=afterSaved\(cycleId\)/);
assert.match(requests,/previous\?\.selected!=null/);
assert.match(requests,/queued\.selected\?\.let\{return it\}/);
assert.match(requests,/if\(refresh\.currentCycle\(\)==refresh\.cycleId\)refresh\.action\(\) else queued\.resumed\?\.invoke\(\)/);
assert.match(requests,/pending\.getAndSet\(null\)/);

// One deliberate saved-program flow. Browsing is transient; choosing supplies
// only an owner-bound locator and still requires a fresh native read.
const screen=readFileSync(app+'NativeKotlinCustomCycleCandidateScreen.kt','utf8');
const picker=readFileSync(app+'NativeKotlinProgramPickerScreen.kt','utf8');
const session=readFileSync(app+'NativeKotlinProgramPicker.kt','utf8');
assert.match(screen,/if\(draft!=null\)OutlinedButton\(onClick=controller::findSavedPrograms/);
assert.match(screen,/onClick = \{controller\.refreshSavedPrograms\(\)\}/);
assert.doesNotMatch(screen,/Text\("Other programs"\)/);
assert.match(screen,/NativeKotlinSavedProgramList\(state\.savedDrafts,state\.savedProgramsOwner/);
assert.match(screen,/NativeKotlinProgramPickerSheet\(picker,draft\?\.cycleId/);
assert.match(screen,/onDismiss=controller::dismissProgramPicker/);
assert.match(screen,/onOpen=controller::openSavedProgram/);
assert.match(screen,/LaunchedEffect\(draft\?\.cycleId,showProgramEditor\)/);
assert.match(screen,/offeredInitialPolicyEditor by rememberSaveable\(draft\?\.athleteId,draft\?\.cycleId\)/);
assert.match(screen,/if\(draft!=null && !busy && !offeredInitialPolicyEditor\)/);
assert.match(picker,/NativeKotlinSavedProgramList\(state\.programs,state\.owner/);
assert.match(picker,/NativeEditSheet\(busy=false,onDismiss=onDismiss\)/);
assert.match(picker,/Text\("Cancel"\)/);
assert.match(picker,/matchingPrograms\(programs,query,currentCycleId\)/);
assert.match(picker,/matches\.take\(visibleCount\)/);
assert.match(picker,/Text\("Show more programs"\)/);
assert.doesNotMatch(picker,/saved\.revision\}/);
assert.match(controller,/val programPicker:StateFlow<NativeKotlinProgramPickerState\?>/);
assert.match(controller,/run\(preserveFeedback=true\)/);
assert.match(controller,/val entry=host\.reviewSavedPrograms\(\)/);
assert.match(controller,/fun refreshSavedPrograms\(\)=enter\s*\{\s*if\(current\.value\.draft==null\)/);
assert.match(controller,/val prepared=prepareRead\(entry\.cycleId,read,entry\.source\)/);
assert.match(controller,/host\.publishProgramViewIfOwned\(selection\.owner\)/);
assert.match(controller,/pickerSession\.claim\(token\)\s*\{[\s\S]*?applyPreparedRead\(prepared\)/);
assert.match(controller,/dismissProgramPicker\(\)=pickerSession\.dismiss/);
assert.match(entry,/selection\.owner!=owner/);
assert.match(entry,/selection\?\.cycleId \?: visibleDraft/);
assert.match(session,/if\(active!=token\)return false/);
assert.match(session,/active=null[\s\S]*?action\(\)/);
assert.match(host,/if\(localAthlete\.signedInIdentity\(\)!=owner\)false else publish\(\)/);
// The same detail composition stays mounted when the outline moves beside it.
// Size uses the available body pane and text scale, not physical screen width.
const workspace=readFileSync(app+'NativeKotlinPlanWorkspace.kt','utf8');
const navigator=readFileSync(app+'NativeKotlinPlanEditorNavigator.kt','utf8');
const dayEditor=readFileSync(app+'NativeKotlinPlanDayEditor.kt','utf8');
const presentation=readFileSync(app+'NativeKotlinPlanEditorPresentation.kt','utf8');
assert.match(workspace,/BoxWithConstraints\(Modifier\.fillMaxSize\(\)\)/);
assert.match(workspace,/nativePlanUsesTwoPanes\(maxWidth\.value,LocalDensity\.current\.fontScale\)/);
assert.match(workspace,/val navigationScroll=key\(navigationKey\) \{ rememberScrollState\(\) \}/);
assert.match(workspace,/Row\(Modifier\.fillMaxSize\(\)\)/);
assert.match(workspace,/if\(twoPane\)key\("plan-outline"\)/);
assert.match(workspace,/key\("plan-content"\) \{\s*PursuitScreenContent/);
assert.match(workspace,/scrollState=scrollState/);
assert.match(workspace,/content\(twoPane\)/);
assert.equal((workspace.match(/PursuitScreenContent\(/g)||[]).length,2);
assert.doesNotMatch(workspace,/screenWidthDp|controller\.|host\.|rememberSaveableStateHolder|\.save\(/);
assert.match(screen,/navigationKey=draft\?\.let \{ it\.athleteId to it\.cycleId \}/);
assert.match(screen,/showNavigator=showProgramEditor && draft!=null/);
for(const field of ['showProgramEditor','showProgramMetadata','expandedBlockId','expandedDayKey'])
    assert.ok(screen.includes(field+' by rememberSaveable(draft?.athleteId,draft?.cycleId)'),field+' must be owner/program scoped');
assert.match(screen,/val policyDrafts=rememberSaveableStateHolder\(\)/);
assert.match(screen,/LaunchedEffect\(twoPane,expandedDayKey,showProgramEditor\)/);
assert.match(screen,/withFrameNanos \{ \}\s*dayEditorAnchor\.bringIntoView\(\)/);
assert.match(screen,/liveRegion=LiveRegionMode\.Polite/);
assert.match(screen,/if\(!twoPane\)renderNavigator\(false\)/);
assert.match(screen,/resolveNativePlanEditorDay\(draft,expandedDayKey\)/);
assert.match(screen,/renderDayEditor\(selected\.block,selected\.day\)/);
assert.match(navigator,/if\(twoPane\) PursuitDestinationRow/);
assert.match(navigator,/onClick=\{onSelectDay\(dayKey\)\},selected=dayExpanded/);
assert.match(navigator,/onToggle=\{onSelectDay\(if\(dayExpanded\)null else dayKey\)\}/);
assert.match(navigator,/if\(dayExpanded && !twoPane\) \{\s*dayContent\(block,day\)/);
assert.match(dayEditor,/val hasWeekOverride=draft\.weekOverrides/);
assert.match(dayEditor,/swap=!hasWeekOverride && slot\.supersetGroup==null/);
assert.match(dayEditor,/enabled=actions\.swap/);
assert.match(dayEditor,/nativePlanDayIsCurrent\(draft,block,day\)/);
assert.match(dayEditor,/PursuitResponsiveActionPair\(/);
assert.doesNotMatch(dayEditor,/horizontalScroll|rememberScrollState/);
assert.match(screen,/onMoveExercise=\{slot,direction->\s*controller\.moveExercise\(block\.blockId,day\.weekday,slot,direction\)/);
assert.match(screen,/onEditDay=\{editingDay=block\.blockId to day\.weekday\}/);
assert.match(screen,/onAddExercise=\{exerciseEditor=Triple\(block\.blockId,day\.weekday,null\)\}/);
assert.doesNotMatch(navigator+dayEditor+presentation,/controller\.|host\.|java\.io|java\.nio|\.save\(|\.finish\(|\.record\(/);
assert.match(presentation,/singleOrNull\(\)/);
console.log('PASS native Plan hierarchy, responsive editing, task continuity and owner-verified saved-program chooser');

// The selected full plan and the next-session brief consume one canonical reviewed week.
const brief=readFileSync(app+'NativeKotlinNextSessionBrief.kt','utf8');
assert.match(screen,/CustomTrainingCycleDomain\.week\(it,selectedPlanWeek\)/);
assert.match(screen,/it\.verifiedDraft==draft/);
assert.match(screen,/NativeKotlinNextSessionBrief\(draft,week,weekday,resolvedPlanWeek\)/);
assert.match(screen,/NativeKotlinCustomCycleOverview\(draft,currentSelection,resolvedPlanWeek,/);
assert.match(brief,/week\?\.days\?\.singleOrNull\{it\.weekday==weekday\}/);
assert.doesNotMatch(brief,/block\.days/);
assert.match(brief,/Week-specific prescription unavailable/);
