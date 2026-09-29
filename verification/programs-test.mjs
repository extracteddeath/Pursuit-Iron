import assert from 'node:assert/strict';
import * as React from '../vendor/react.js';
import {HomePrograms,homeProgramGroups,homePhaseIdentity} from '../modules/App.js';
const R=React.default || React;
const internals=R.__SECRET_INTERNALS_DO_NOT_USE_OR_YOU_WILL_BE_FIRED;
let passed=0;
const check=(name,fn)=>{fn(); console.log('PASS '+name);passed++};
const saved=[],cycles=[];
for(let c=0;c<7;c++){
 const cycle={id:'c'+c,name:c===0?'Test Cycle':c===1?'Name':'Cycle '+c,activeBlock:c===0?1:0,done:c===6,engineSource:'pursuit-next',blockIds:[],blockMeta:[]};
 for(let i=0;i<3;i++){
  const goal=i===0?'hypertrophy':'strength',label=['Hypertrophy','Strength','Peak'][i];
  const p={id:`c${c}b${i}`,name:cycle.name+' · '+label,cycleId:cycle.id,cycleIndex:i,config:{split:'ul',goal},days:[{id:'a',exercises:[]},{id:'b',exercises:[]}],weeks:4,createdAt:100-c,folder:c===1?'Favorites':''};
  saved.push(p);cycle.blockIds.push(p.id);cycle.blockMeta.push({label,goal,weeks:4});
 }
 cycles.push(cycle);
}
saved.push({id:'single',name:'My standalone',icon:'trophy',custom:true,config:{split:'ul',goal:'both'},days:[],weeks:6,createdAt:300});
const input=JSON.stringify({saved,cycles});
let states=[],cursor=0,tree,events=[];
const props={saved,cycles,activeId:'c0b1',onOpen:p=>events.push(['open',p.id]),onOpenCycles:id=>events.push(['cycle',id]),onSetActive:id=>events.push(['switch',id]),onCompare:()=>events.push(['compare']),onOptions:p=>events.push(['options',p.id])};
function render(){cursor=0;internals.ReactCurrentDispatcher.current={useState(init){const i=cursor++;if(!(i in states))states[i]=init;return [states[i],v=>states[i]=typeof v==='function'?v(states[i]):v]},useMemo(fn){return fn()}};tree=HomePrograms(props);internals.ReactCurrentDispatcher.current=null;return tree}
function nodes(n=tree){if(!n || typeof n!=='object')return [];if(Array.isArray(n))return n.flatMap(nodes);return [n,...nodes(n.props?.children ?? null)]}
const text=n=>typeof n==='string'||typeof n==='number'?String(n):Array.isArray(n)?n.map(text).join(''):n?.props?text(n.props.children):'';
const find=(pred)=>nodes().find(pred);
const button=(t)=>find(n=>n.type==='button'&&text(n)===t);
const click=b=>{assert.ok(b,'button exists');b.props.onClick({currentTarget:{closest:()=>({scrollIntoView(){}})}});render()};
const cards=()=>nodes().filter(n=>n.type==='article');
render();
check('App imports and component renders with real React elements',()=>assert.ok(React.isValidElement(tree)));
check('Cycles counted once, standalone kept',()=>assert.equal(homeProgramGroups(saved,cycles,'c0b1').length,8));
check('Current cycle is first despite newer standalone',()=>assert.equal(cards()[0].props['data-program-group'],'cycle:c0'));
check('Initial list capped at current plus four',()=>assert.equal(cards().length,5));
check('Hypertrophy, strength and peak use distinct icons',()=>assert.equal(new Set(['Hypertrophy','Strength','Peak'].map(label=>homePhaseIdentity({label,goal:'strength'}).icon)).size,3));
check('Recovery and mixed goals have their own icons',()=>assert.notEqual(homePhaseIdentity({phase:'recovery'}).icon,homePhaseIdentity({goal:'both'}).icon));
check('Current block follows actual second block',()=>assert.ok(text(cards()[0]).includes('Block 2 of 3')));
check('Standalone entry retained alongside cycles',()=>assert.ok(cards().some(n=>n.props['data-program-group']==='program:single')));
click(button('View blocks')); // first current cycle
check('Current cycle expands on demand',()=>assert.ok(text(cards()[0]).includes('Training now · 4 weeks')));
check('Future phase is marked planned and disabled',()=>assert.equal(find(n=>n.type==='button'&&n.props['aria-label']==='Planned Peak').props.disabled,true));
check('Past and planned cycle blocks cannot switch',()=>assert.ok(!nodes(cards()[0]).some(n=>n.props?.['aria-label']==='Switch to Hypertrophy'||n.props?.['aria-label']==='Switch to Peak')));
click(find(n=>n.props?.['aria-label']==='Open Strength'));
check('Open block targets correct stored program',()=>assert.deepEqual(events.at(-1),['open','c0b1']));
click(button('Hide blocks'));
check('Current cycle can collapse again',()=>assert.ok(!text(cards()[0]).includes('Training now · 4 weeks')));
click(find(n=>n.props?.['aria-label']==='Open cycle Test Cycle'));
check('Cycle title opens existing cycle screen',()=>assert.deepEqual(events.at(-1),['cycle','c0']));
click(find(n=>n.props?.['aria-label']==='Switch to My standalone'));
check('Standalone switch retains existing callback',()=>assert.deepEqual(events.at(-1),['switch','single']));
click(find(n=>n.props?.['aria-label']==='More options for My standalone'));
check('Standalone menu retains conversion/duplicate/delete entry point',()=>assert.deepEqual(events.at(-1),['options','single']));
click(button('Compare'));
check('Compare callback preserved',()=>assert.deepEqual(events.at(-1),['compare']));
click(button('Show more programs (3 remaining)'));
check('Show more reveals all remaining groups',()=>assert.equal(cards().length,8));
click(button('Show fewer programs'));
check('Show fewer restores compact list',()=>assert.equal(cards().length,5));
click(button('Standalone'));
check('Standalone filter hides cycles',()=>assert.equal(cards().length,1));
click(button('Completed'));
check('Completed filter shows only completed cycle',()=>assert.equal(cards()[0].props['data-program-group'],'cycle:c6'));
click(button('All'));
find(n=>n.type==='input').props.onChange({target:{value:'Favorites'}});render();
check('Folder search finds cycle once',()=>assert.equal(cards().length,1));
check('Folder search keeps all phases together',()=>assert.equal(nodes(cards()[0]).filter(n=>n.props?.className==='hp-phase').length,3));
find(n=>n.type==='input').props.onChange({target:{value:'nothing matches'}});render();
check('No results has a clear recovery action',()=>assert.ok(button('Clear filters')));
click(button('Clear filters'));
check('Clear filters restores initial list',()=>assert.equal(cards().length,5));
check('Browsing never mutates programs or cycle data',()=>assert.equal(JSON.stringify({saved,cycles}),input));
const originalSaved=props.saved;
props.saved=saved.filter(p=>p.id!=='c0b2');states=[];render();click(button('View blocks'));
check('Missing generated block remains visible as planned metadata',()=>assert.equal(find(n=>n.props?.['aria-label']==='Planned Peak').props.disabled,true));
props.saved=originalSaved;
props.cycles=[];states=[];render();
check('Orphaned cycle links render safely',()=>assert.ok(cards().length>0));
props.cycles=cycles;props.saved=[];states=[];render();
check('Empty collection renders safely',()=>assert.ok(text(tree).includes('0 saved plans')));
console.log(`${passed} checks passed. Component interaction checks; no browser layout or device certification claimed.`);
