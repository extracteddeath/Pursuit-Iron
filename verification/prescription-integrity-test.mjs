import assert from 'node:assert/strict';
import { effortLabel, nextSessionCursor } from '../modules/App.js';
import { getNextShellCell } from '../modules/next-engine/app-shell-adapter.js';

let passed=0;
const check=(name,fn)=>{fn(); console.log('PASS '+name); passed++;};

check('array RIR [2,2] renders as 2 RIR',()=>assert.equal(effortLabel([2,2],'rir'),'2 RIR'));
check('array RIR [1,2] renders as a clean range',()=>assert.equal(effortLabel([1,2],'rir'),'1–2 RIR'));
check('array RIR [1,2] converts to RPE 8–9',()=>assert.equal(effortLabel([1,2],'rpe'),'RPE 8–9'));

const program={
  id:'p1',engineSource:'pursuit-next',engineSourceVersion:'0.62.5',config:{weeks:4,deload:false},
  days:[1,2,3,4,5].map(n=>({id:'d'+n,label:'D'+n,exercises:['x']})),
  overrides:{},
  nextWeekPrescriptions:{'d1:0':{1:{sets:3,reps:[5,8],rir:[2,2],rest:180}}}
};
const cell=getNextShellCell(program,program.days[0],0,1);
check('shell boundary normalizes persisted rep arrays',()=>assert.equal(cell.reps,'5-8'));
check('shell boundary normalizes persisted RIR arrays',()=>assert.equal(cell.rir,'2'));
check('shell boundary does not change saved set count',()=>assert.equal(cell.sets,3));

const h=(day,week,i)=>({id:'h'+i,programId:'p1',dayId:'d'+day,dayLabel:'D'+day,weekIndex:week,date:1000-i});
let c=nextSessionCursor(program,[h(4,1,1),h(3,1,2),h(2,1,3),h(1,1,4)]);
check('four distinct days remain in week 1',()=>assert.deepEqual([c.weekIndex,c.dayIndex],[1,4]));
c=nextSessionCursor(program,[h(1,1,1),h(1,1,2),h(1,1,3),h(1,1,4),h(1,1,5)]);
check('five duplicate logs cannot advance to week 2',()=>assert.deepEqual([c.weekIndex,c.dayIndex],[1,1]));
c=nextSessionCursor(program,[h(5,1,1),h(4,1,2),h(3,1,3),h(2,1,4),h(1,1,5)]);
check('five distinct completed days advance to week 2',()=>assert.equal(c.weekIndex,2));

console.log(`${passed} prescription-integrity checks passed.`);
