import assert from 'node:assert/strict';
import { BARS, normalizeGyms, barWeightFor, setGymBarWeights, platesPerSide, setAvailPlates, platePairsFor, availPlatesFor } from '../modules/training-domain/analytics.js';

const original = structuredClone(BARS);
const gym = { id:'home', name:'Home', equipment:['barbell','smith'],
    barWeights:{ unit:'lb', barbell:35, smith:0 } };
const commercial = { id:'commercial', name:'Commercial', equipment:['barbell','smith'],
    barWeights:{ unit:'kg', barbell:20, smith:15 } };
const { gyms } = normalizeGyms([gym,commercial], 'home');
assert.deepEqual(gyms[0].barWeights, gym.barWeights);
assert.deepEqual(gyms[1].barWeights, commercial.barWeights);
setAvailPlates({ lb:[45,35,25,10,5,2.5],kg:[25,20,15,10,5,2.5,1.25] });
setGymBarWeights(gyms[0]);
assert.equal(barWeightFor('barbell','lb'),35);
assert.equal(barWeightFor('smith','lb'),0);
assert.equal(barWeightFor('ezbar','lb'),BARS.ezbar.lb,'non-configured bars keep their defaults');
assert.equal(platesPerSide(125,'barbell','lb').barW,35);
assert.deepEqual(platesPerSide(125,'barbell','lb').plates,[45]);
assert.equal(platesPerSide(100,'smith','lb').barW,0);
assert.deepEqual(platesPerSide(100,'smith','lb').plates,[45,5]);
assert.ok(Math.abs(barWeightFor('barbell','kg')-15.876)<.001,'unit conversion uses physical load');
setGymBarWeights(gyms[1]);
assert.equal(barWeightFor('barbell','kg'),20);
assert.equal(barWeightFor('smith','kg'),15);
assert.equal(platesPerSide(65,'smith','kg').plates.reduce((a,b)=>a+b,0),25);
setGymBarWeights(null);
assert.equal(barWeightFor('barbell','lb'),45,'no custom gym reverts to default');
assert.equal(barWeightFor('smith','lb'),35);
const invalid=normalizeGyms([{...gym,barWeights:{unit:'lb',smith:-2,barbell:'bad'}}],gym.id).gyms[0];
assert.equal(barWeightFor('ezbar','lb'),25);
assert.deepEqual(invalid.barWeights,{unit:'lb'},'invalid backup values rejected');
assert.deepEqual(BARS,original,'global bar defaults are immutable');
const finiteGym={...gym,barWeights:{unit:'lb',barbell:45},platePairs:{lb:{25:1,20:3,10:1,5:1,2.5:0},kg:{20:1,10:2}}};
// Use catalog sizes in a non-greedy case: 35 + 35 fits 70, whereas 45 strands 25.
finiteGym.platePairs.lb={45:1,35:2};
const normalized=normalizeGyms([finiteGym],finiteGym.id).gyms[0];
assert.equal(normalized.platePairs.lb[45],1,'gym normalization retains counts');
assert.equal(normalized.platePairs.lb[25],0,'unlisted sizes are absent from explicit inventory');
setGymBarWeights(normalized);setAvailPlates(availPlatesFor(null,normalized));
assert.deepEqual(platesPerSide(185,'barbell','lb').plates,[35,35],'bounded solver finds the closest achievable combination');
const limited=platesPerSide(365,'barbell','lb');
assert.deepEqual(limited.plates,[45,35,35],'never invent more pairs');
assert.equal(limited.leftover,45);
assert.deepEqual(availPlatesFor(null,{platePairs:{lb:{}}}).lb,[],'explicit zero inventory does not fall back to standard plates');
setAvailPlates(availPlatesFor(null,{platePairs:{lb:{}}}));
assert.equal(platesPerSide(135,'barbell','lb').leftover,45);
assert.equal(platePairsFor(null,'lb',[])[45],0,'an empty owned-size selection stays empty');
assert.equal(platePairsFor(null,'lb',[45])[45],4);
assert.equal(platePairsFor({platePairs:{lb:{45:Infinity,35:-1,25:1.9}}},'lb')[45],0);
assert.equal(platePairsFor({platePairs:{lb:{25:1.9}}},'lb')[25],1);
setAvailPlates(null);setGymBarWeights(null);
console.log('PASS Build 846 separate gym bar masses, zero Smith, per-side math, unit changes, gym switches and backward-compatible defaults');

const { prepareGymLoadDraft } = await import('../modules/training-domain/loading-tools.js');
const { gymBarDraftUpdate, gymPlatePairDraftUpdate } = await import('../modules/gym-load-fields.js');
// Two inputs edited in one render must not overwrite each other's pending draft.
// A subsequent plate count cannot resurrect the Smith's old 35 lb default.
const stagedStart={...gym,barWeights:{unit:'lb',barbell:45,smith:35},platePairs:{lb:{45:4,25:4}}};
const apply=(current,edit)=>({...current,...edit(current)});
const withBar=apply(stagedStart,gymBarDraftUpdate('barbell','35'));
const withSmith=apply(withBar,gymBarDraftUpdate('smith','0'));
const withPairs=apply(withSmith,gymPlatePairDraftUpdate('lb',45,'0',{lb:[45,25,10]}));
assert.deepEqual(withPairs.barWeights,{unit:'lb',barbell:'35',smith:'0'});
assert.equal(withPairs.platePairs.lb[45],'0');
assert.equal(prepareGymLoadDraft(withPairs).gym.barWeights.smith,0,'counterbalanced Smith zero survives a later plate change');
assert.equal(prepareGymLoadDraft(withPairs).gym.barWeights.barbell,35,'barbell does not revert during Smith edit');
const differentOrder=apply(apply(stagedStart,gymBarDraftUpdate('smith','0')),gymBarDraftUpdate('barbell','35'));
assert.equal(differentOrder.barWeights.smith,'0','latest-state updater retains sibling field across render timing');
console.log('PASS B962: cross-field gym edits preserve Smith zero, barbell and plate inventory');
