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
assert.match(overview,/if\(blockExpanded\)block\.days\.sortedBy/);

// A verified NEXT, PREPARED, or RESUMABLE day is labeled correctly.
for(const kind of ['Next','Prepared','ResumeIssued'])
    assert.match(overview,new RegExp('NativeCustomNextCycleSlot\\.'+kind));
assert.match(overview,/is NativeCustomNextCycleSlot\.ResumeIssued -> "Resume workout"/);
assert.match(overview,/is NativeCustomNextCycleSlot\.Prepared -> "Prepared workout"/);
assert.match(overview,/is NativeCustomNextCycleSlot\.Next -> "Next workout"/);
assert.match(overview,/val isNextDay=hasNextSession/);
assert.match(overview,/subtitle=\(if\(isNextDay\)"\$selectedStatus · " else ""\)/);

// Workout prescriptions remain inspectable and separately editable; this is
// not a program mutation or an unapproved week override.
assert.match(overview,/if\(showTargets\)/);
assert.match(overview,/slot\.sets\.forEachIndexed/);
assert.match(overview,/onEditDay\(block\.blockId,day\.weekday\)/);
assert.match(overview,/onEditExercise\(block\.blockId,day\.weekday,slot\.slotId\)/);
assert.match(overview,/Your workout preview includes any changes for its specific week/);
console.log('PASS native Plan full program, optional current-block focus, week hierarchy, next-day indicators and retained editors');
