// B995: Program settings / schedule / training-max and share accessibility contract.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { resolvedWeeklySchedule } from '../modules/program-schedule-sheet.js';

const schedule = fs.readFileSync('modules/program-schedule-sheet.js', 'utf8');
const maxes = fs.readFileSync('modules/program-training-maxes.js', 'utf8');
const settings = fs.readFileSync('modules/program-settings-sheet.js', 'utf8');
const share = fs.readFileSync('modules/program-share-sheet.js', 'utf8');
const days = [{ id: 'upper', label: 'Upper' }, { id: 'lower', label: 'Lower' }];
const program = { days, schedule: { 1: 'upper', 2: 'retired', 4: 'lower', 6: null } };
const resolved = resolvedWeeklySchedule(program);
assert.deepEqual(resolved.map(row => row.weekday), [1, 2, 3, 4, 5, 6, 0], 'week begins Monday and ends Sunday');
assert.deepEqual(resolved.map(row => row.day?.id || null),
    ['upper', null, null, 'lower', null, null, null], 'obsolete IDs are not counted or shown as workouts');
assert.equal(resolved.filter(row => row.day).length, 2, 'only executable assignments count toward training days');
assert.equal(resolvedWeeklySchedule({ days: [], schedule: { 1: 'upper' } }).filter(row => row.day).length, 0);
assert.equal(resolvedWeeklySchedule({ days, schedule: { 0: 'lower' } })[6].day.id, 'lower');
assert.equal(resolvedWeeklySchedule({ days, schedule: { 1: 'upper' } })[0].day, days[0],
    'the rendered assignment retains the original day object');
assert.ok(schedule.includes("role: 'status', 'aria-live': 'polite', 'aria-atomic': true"),
    'schedule changes announce the corrected day count');
assert.ok(schedule.includes("day?.label || 'Rest day'") && schedule.includes("'. Change assignment'"),
    'each schedule action exposes its current assignment instead of only the weekday');
assert.ok(schedule.includes("'data-assigned': day ? 'true' : 'false'"),
    'selected day appearance is derived from the same valid assignment');
assert.ok(schedule.includes("Object.keys(schedule).length > 0"),
    'obsolete schedule keys remain clearable');
assert.ok(schedule.includes("panelRef.current?.querySelector('.wpb-program-schedule-close')?.focus"),
    'clearing the schedule transfers focus before removing its button');
assert.ok(maxes.includes("count} of ${total} main lifts set. Edit training maxes"),
    'summary exposes completion status to screen readers');
assert.ok(maxes.includes("'aria-describedby': `${inputId}-1rm-feedback`") &&
    maxes.includes("id: `${inputId}-1rm-feedback`, role: 'status'"),
    '1RM conversion describes validation and the live calculated result');
assert.ok(maxes.includes("'aria-invalid': source == null || (source > 0 && !canConvert)"),
    'malformed conversion values have input error semantics');
assert.ok(settings.includes('"aria-describedby": settingsIssues.length ? "program-settings-issues"'),
    'disabled save identifies unresolved settings issues');
assert.ok(settings.includes('(!validWeeks && !cycleLengthOwned ? "program-weeks-error"'),
    'invalid block length describes the reason save is unavailable');
assert.ok(share.includes("feedback?.state === 'pending'") && share.includes("'Copying…'"),
    'clipboard pending operation is announced separately from success');
console.log('PASS B995: Program schedule validity, assignment announcements, training-max conversion, settings save guidance and clipboard pending status.');
