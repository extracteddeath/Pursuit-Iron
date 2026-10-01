import assert from 'node:assert/strict';
import { mergeStandaloneIntoGeneratedCycle, cycleProgress, planOverview, computeCell } from '../modules/App.js';
import { currentProgram, generatedCycle, savedPrograms, staleCycle } from './m215-cycle-duration-fixture.mjs';
import { programWorkingWeeks, cycleBlockMetadata } from '../modules/program-duration.js';

const before = structuredClone({ currentProgram, generatedCycle, savedPrograms, staleCycle });
const merged = mergeStandaloneIntoGeneratedCycle(currentProgram, structuredClone(generatedCycle));
assert.deepEqual(merged.cycle.blockMeta.map(m => m.weeks), [10, 5, 3],
    'converting an existing ten-week custom program must not advertise the template entry length of six');
assert.deepEqual(merged.cycle.nextEngineCycle.plannedBlocks.map(m => m.weeks), [10, 5, 3]);
assert.equal(merged.currentProgram.config.weeks, 10);
assert.deepEqual(merged.currentProgram.days, currentProgram.days);
assert.deepEqual(merged.currentProgram.overrides, currentProgram.overrides);
assert.equal(computeCell(merged.currentProgram, merged.currentProgram.days[0], 'lat-raise', 2, 8).tech,
    computeCell(currentProgram, currentProgram.days[0], 'lat-raise', 2, 8).tech,
    'duration repair must retain the actual ten-week intensifier schedule');

const progress = cycleProgress(staleCycle, savedPrograms, [], {
    cycle: staleCycle, weekIndex: 8, isDeload: false
});
assert.deepEqual(progress.blocks.map(b => b.weeks), [10, 5, 3]);
assert.deepEqual(progress.blocks.map(b => b.range), [[1, 11], [12, 16], [17, 19]], 'deload is a separate calendar week');
assert.equal(progress.totalWeeks, 19);
assert.equal(progress.curWeek, 8);
assert.match(progress.blocks[0].note, /10 weeks/);
assert.equal(cycleProgress(staleCycle, [], []).blocks[0].weeks, 6, 'metadata remains the fallback for an unavailable program');
assert.equal(programWorkingWeeks({ weeks: 6, config: { weeks: '10' } }), 10);
assert.equal(programWorkingWeeks({ weeks: 5, config: { weeks: 0 } }), 5);
for (const weeks of [1, 2, 3, 7, 9, 12]) assert.equal(programWorkingWeeks({ weeks }), weeks, `existing ${weeks}-week plans must retain their duration`);
assert.deepEqual(cycleBlockMetadata(staleCycle, [...savedPrograms].reverse()).map(b => b.weeks), [10, 5, 3], 'lookup must follow ids rather than program list order');

const overview = planOverview(savedPrograms[0], 8, staleCycle, [], savedPrograms);
assert.deepEqual(overview.phases.map(p => p.weeks), [10, 5, 3]);
assert.deepEqual(overview.phases.map(p => [p.weekFrom, p.weekTo]), [[1, 11], [12, 16], [17, 19]]);
assert.deepEqual({ currentProgram, generatedCycle, savedPrograms, staleCycle }, before, 'all duration reads and conversion must leave inputs unchanged');
console.log('PASS M215 cycle duration: ten-week standalone conversion, stale saved metadata, calendar/deload/phase ranges, future 5/3-week blocks and custom intensifier schedule agree.');
