import assert from 'node:assert/strict';
import { summarizeStrengthWorkWeeks, findStrengthVolumeCliff } from '../modules/next-engine/cycle-runtime-adapter.js';

// M321: the first/final sessions alone cannot protect the actual middle of a
// strength accumulation block. Fabricate a deliberately uneven five-week
// realization to independently prove which week controls each quality axis.
const full={bench_press:3,back_squat:2,deadlift:2,overhead_press:2};
const make=(week,coverage,practice,weakest,frequencies=full)=>({
    week, weeklySets:54, muscleTargetCoveragePercent:coverage,
    liftPracticePercent:practice,
    weakestPriorityLiftPracticePercent:weakest,
    liftFrequencies:{...frequencies},regionalSets:{chest:8,lats:7},
    overCapacityDays:[]
});
const rows=[
    make(1,97,100,100),
    make(2,93,100,100),
    make(3,59,91,100),
    make(4,95,47,50,{...full,bench_press:1}),
    make(5,98,100,100)
];
const before=JSON.stringify(rows);
const result=summarizeStrengthWorkWeeks(rows);
assert.equal(result.executableCoverage,59,
    'A weak middle week must control projected hypertrophy-to-strength dose coverage');
assert.equal(result.executableCoverageWeek,3);
assert.equal(result.executableLiftPractice,47);
assert.equal(result.executableLiftPracticeWeek,4);
assert.equal(result.executableWeakestPriorityLift,50);
assert.equal(result.executableWeakestPriorityLiftWeek,4);
assert.equal(result.executableLiftFrequencies.bench_press,1,
    'A middle-week loss of bench practice cannot be hidden by three sessions on both endpoints');
assert.equal(result.executableLiftFrequencies.back_squat,2);
assert.equal(result.executableLiftFrequencies.deadlift,2);
assert.equal(result.executableLiftFrequencies.overhead_press,2);
assert.equal(JSON.stringify(rows),before,'Scoring cannot rewrite executable week or lift data');
const endpoints=summarizeStrengthWorkWeeks([rows[0],rows.at(-1)]);
assert.ok(result.executableCoverage<endpoints.executableCoverage);
assert.ok(result.executableLiftPractice<endpoints.executableLiftPractice);
assert.ok(result.executableLiftFrequencies.bench_press<endpoints.executableLiftFrequencies.bench_press);
const tied=summarizeStrengthWorkWeeks([
    make(1,76,80,90),make(2,76,80,90),make(3,95,95,95)
]);
assert.equal(tied.executableCoverageWeek,1,'Tie-breaking must be stable by week order');
assert.equal(tied.executableLiftPracticeWeek,1);
assert.equal(tied.executableWeakestPriorityLiftWeek,1);
assert.throws(()=>summarizeStrengthWorkWeeks([]),
    error=>error?.code==='NEXT_CYCLE_REALIZED_WEEKS_MISSING');

const volumeRows=[make(1,93,95,95),make(2,91,95,95),make(3,89,95,95),make(4,88,95,95)];
volumeRows.forEach((row,i)=>{row.weeklySets=[52,50,24,53][i];});
assert.equal(findStrengthVolumeCliff(60,volumeRows)?.week,3,
    'A safe entry week must not conceal a later >50% executable set-volume cliff');
assert.equal(findStrengthVolumeCliff(60,volumeRows)?.weeklySets,24);
volumeRows[2].weeklySets=30;
assert.equal(findStrengthVolumeCliff(60,volumeRows),null,
    'The 50% boundary remains allowed');
assert.equal(findStrengthVolumeCliff(15,volumeRows),null,
    'Small prior blocks retain the existing no-floor policy');
assert.equal(findStrengthVolumeCliff(60,[]),null);

console.log('PASS M321: middle-week strength-practice and muscle-dose shortfalls cannot be hidden by successful endpoints; immutable and stable scoring.');
