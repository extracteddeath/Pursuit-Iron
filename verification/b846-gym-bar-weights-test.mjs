import assert from 'node:assert/strict';
import { BARS, normalizeGyms, barWeightFor, setGymBarWeights, platesPerSide, setAvailPlates } from '../modules/training-domain/analytics.js';

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
console.log('PASS Build 846 separate gym bar masses, zero Smith, per-side math, unit changes, gym switches and backward-compatible defaults');
