import assert from 'node:assert/strict';
import { summarizeStrengthWorkWeeks, findStrengthVolumeCliff, strengthEntryMinimumSets, unexplainedStrengthExerciseCollapse } from '../modules/next-engine/cycle-runtime-adapter.js';

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

const { strengthEntryMinimumSets: repairFloor } = await import('../modules/next-engine/volume-repair.js');
assert.equal(repairFloor, strengthEntryMinimumSets, 'Strength repair and acceptance use the same continuity rule');
assert.equal(repairFloor(64),45, 'The reported regression requires 45, not 32 or 43 sets');
assert.equal(findStrengthVolumeCliff(64,[{week:4,weeklySets:43}])?.week,4);
assert.equal(findStrengthVolumeCliff(64,[{week:4,weeklySets:45}]),null);
assert.equal(strengthEntryMinimumSets(60),42,'Normal strength entry preserves at least 70%');
assert.equal(strengthEntryMinimumSets(82),42,
    'Large weeks below the extreme capacity class retain more than half');
assert.equal(strengthEntryMinimumSets(68),35,
    'At the 68-set boundary, do not silently accept a half-volume strength block');
assert.equal(strengthEntryMinimumSets(89),45,
    'High but not extreme weeks must preserve strictly more than half');
assert.equal(strengthEntryMinimumSets(90),45,
    'At 90 working sets, a verified time-capped extreme week may retain half');
assert.equal(strengthEntryMinimumSets(112),56,
    'A verified 112-set source may enter a capacity-limited 56-set strength week');
assert.equal(findStrengthVolumeCliff(82,[{week:2,weeklySets:48}]),null);
assert.equal(strengthEntryMinimumSets(15),0,'Small sessions skip aggregate floor');
const volumeRows=[make(1,93,95,95),make(2,91,95,95),make(3,89,95,95),make(4,88,95,95)];
volumeRows.forEach((row,i)=>{row.weeklySets=[52,50,24,53][i];});
assert.equal(findStrengthVolumeCliff(60,volumeRows)?.week,3,
    'A safe entry week must not conceal a later >30% strength-accumulation volume reduction');
assert.equal(findStrengthVolumeCliff(60,volumeRows)?.weeklySets,24);
volumeRows[2].weeklySets=41;
assert.equal(findStrengthVolumeCliff(60,volumeRows)?.week,3,
    '41 of 60 sets is below the 70% floor');
volumeRows[2].weeklySets=42;
assert.equal(findStrengthVolumeCliff(60,volumeRows),null,
    'Exactly 70% of prior weekly volume is acceptable');
assert.equal(findStrengthVolumeCliff(15,volumeRows),null,
    'Small prior blocks retain the existing no-floor policy');
assert.equal(findStrengthVolumeCliff(60,[]),null);

// Eight distinct upper-body exercises should never silently turn into
// three on the SAME Upper day of a regular strength-accumulation block.
const upper=n=>({intent:'upper',day:0,
    exercises:Array.from({length:n},(_,i)=>({exerciseId:'upper-'+i,sets:2}))});
const lower={intent:'lower',day:2,exercises:[
    {exerciseId:'squat',sets:3},{exerciseId:'hinge',sets:3}]};
const hypertrophy={phase:'hypertrophy_accumulation',split:{family:'ulppl'},
    sessions:[upper(8),lower]};
const strength={phase:'strength_accumulation',split:{family:'ulppl'},
    sessions:[upper(3),lower]};
const standard={preferences:{volumeApproach:'standard'}};
assert.deepEqual(unexplainedStrengthExerciseCollapse(hypertrophy,strength,
    {phase:'strength_accumulation'},standard),{
        day:'upper',previousExercises:8,strengthExercises:3,sessionIndex:0
    },'Count collapse must be reviewed independently of weekly set totals');
assert.equal(unexplainedStrengthExerciseCollapse(hypertrophy,
    {...strength,sessions:[upper(5),lower]},
    {phase:'strength_accumulation'},standard),null,
    'Five productive movements may be appropriate; eight is not a quota');
assert.equal(unexplainedStrengthExerciseCollapse(hypertrophy,
    {...strength,phase:'peak'}, {phase:'peak'},standard),null,
    'A short, deliberately planned Peak taper is not strength accumulation');
assert.equal(unexplainedStrengthExerciseCollapse(hypertrophy,
    {...strength,split:{family:'upper_lower'}},
    {phase:'strength_accumulation'},standard),null,
    'Different weekly splits have redistributed exercises; compare weekly coverage');
assert.equal(unexplainedStrengthExerciseCollapse(hypertrophy,strength,
    {phase:'strength_accumulation'},{preferences:{volumeApproach:'minimalist'}}),null,
    'Explicit minimalist intent must not be silently overwritten');
assert.equal(JSON.stringify(hypertrophy.sessions[0]),JSON.stringify(upper(8)),
    'Exercise continuity diagnostics cannot mutate the saved previous phase');

console.log('PASS M321: middle-week strength-practice and muscle-dose shortfalls cannot be hidden by successful endpoints; immutable and stable scoring.');
