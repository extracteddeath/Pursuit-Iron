import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const file='android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/NativeKotlinCustomCycleOverview.kt';
const s=readFileSync(file,'utf8');
// Every training phase is available in its own progressive-disclosure surface,
// rather than rendering all weeks' days and exercises at once.
assert.match(s,/cycle\.blocks\.forEachIndexed \{ index,block ->/);
assert.match(s,/PursuitDisclosureRow\([\s\S]*?title=block\.label/);
assert.match(s,/if\(blockExpanded\)block\.days\.sortedBy/);
assert.match(s,/rememberSaveable\(cycle\.cycleId,block\.blockId,week\)/);
assert.match(s,/mutableStateOf\(block==selectedBlock\)/);
// Current program week is located by the verified next/resumable cycle slot.
for(const v of ['Next','Prepared','ResumeIssued'])
    assert.ok(s.includes('NativeCustomNextCycleSlot.'+v));
assert.match(s,/val isSelectedDay=block==selectedBlock && day\.weekday==weekday/);
assert.match(s,/if\(isSelectedDay\)"\$selectedStatus · "/);
// Never lose actual set prescriptions, editor hooks or weekly override caveat.
assert.match(s,/if\(showTargets\)/);
assert.match(s,/slot\.sets\.forEachIndexed/);
assert.match(s,/onEditExercise\(block\.blockId,day\.weekday,slot\.slotId\)/);
assert.match(s,/Your workout preview includes any changes for its specific week/);
console.log('PASS collapsible program blocks, current day, and unchanged authored set details');
