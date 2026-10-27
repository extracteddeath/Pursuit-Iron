import assert from 'node:assert/strict';
import { readLoadingNumber, plateLoadPreview, repMaxPreview, prepareGymLoadDraft, withGymLoadingUnit, withGymBarUnit,
    normalizeGyms, setAvailPlates, availPlatesFor, setGymBarWeights } from '../modules/engine-api.js';

assert.equal(readLoadingNumber('92..5').status,'invalid');
assert.equal(readLoadingNumber('2e2').status,'invalid');
assert.equal(readLoadingNumber('8.5',{integer:true}).status,'invalid');
assert.equal(readLoadingNumber('35.').status,'invalid');
assert.equal(readLoadingNumber('.5').value,.5);
assert.equal(readLoadingNumber(0).value,0);
assert.equal(readLoadingNumber(Infinity).status,'invalid');
setGymBarWeights(null);setAvailPlates(null);
assert.equal(plateLoadPreview('92..5','barbell','lb').status,'invalid');
assert.equal(plateLoadPreview('10','barbell','lb').status,'below_bar');
assert.equal(plateLoadPreview('45','barbell','lb').status,'exact');
assert.equal(plateLoadPreview('','barbell','lb').status,'empty');
assert.equal(plateLoadPreview('99999','barbell','lb').status,'invalid');
const gym={id:'tools',name:'Home',equipment:['barbell','smith','dumbbell'],limitUnit:'kg',updatedAt:123,
    limits:{dumbbell:50,barbell:200},inventory:{machine:[10,20,30]},barWeights:{unit:'lb',barbell:35,smith:0},platePairs:{lb:{45:1,10:1}}};
const original=structuredClone(gym);
assert.equal(normalizeGyms([gym],gym.id).gyms[0].updatedAt,123,'normalization preserves the clock that identifies newer gym edits');
const lb=withGymLoadingUnit(gym,'lb');assert.equal(lb.ok,true);
assert.ok(Math.abs(lb.gym.limits.dumbbell-110.23113109243879)<1e-10,'unit switch converts the physical ceiling');
assert.ok(Math.abs(lb.gym.inventory.machine[0]-22.046226218487757)<1e-10,'every rack changes in the same transaction');
assert.deepEqual(lb.gym.barWeights,gym.barWeights,'separate bar-mass units stay separate');
assert.deepEqual(lb.gym.platePairs,gym.platePairs,'owned lb/kg plate counts are not reinterpreted');
const roundtrip=withGymLoadingUnit(lb.gym,'kg').gym;
assert.ok(Math.abs(roundtrip.limits.dumbbell-50)<1e-10);assert.ok(Math.abs(roundtrip.inventory.machine[2]-30)<1e-10);
const bars=withGymBarUnit(withGymBarUnit(gym,'kg').gym,'lb').gym.barWeights;
assert.ok(Math.abs(bars.barbell-35)<1e-10);assert.equal(bars.smith,0,'a counterbalanced Smith stays zero');
const typed={...gym,inventoryText:{dumbbell:'5, 12.5, 25'},limits:{...gym.limits,dumbbell:'75.5'}};
const staged=prepareGymLoadDraft(typed);assert.equal(staged.ok,true);assert.deepEqual(staged.gym.inventory.dumbbell,[5,12.5,25]);
assert.equal(staged.gym.inventoryText,undefined,'temporary input strings do not become stored gym schema');
const bad={...gym,inventoryText:{dumbbell:'5, nope, 25'},barWeights:{unit:'lb',barbell:'35..5'},platePairs:{lb:{45:'51',35:'1.5'}}};
assert.equal(prepareGymLoadDraft(bad).ok,false);assert.equal(prepareGymLoadDraft(bad).issues.length,4,'invalid fields are reported instead of partially saved');
assert.equal(withGymLoadingUnit(bad,'lb').ok,false,'unit conversion cannot silently discard incomplete input');
assert.equal(prepareGymLoadDraft({...gym,inventory:{machine:'bad'}}).ok,false);
assert.equal(prepareGymLoadDraft(null).ok,false);
assert.deepEqual(gym,original,'validation and unit changes leave the source untouched');
setGymBarWeights({barWeights:{unit:'lb',barbell:45}});setAvailPlates(availPlatesFor(null,{platePairs:{lb:{}}}));
const empty=plateLoadPreview('135','barbell','lb');assert.equal(empty.status,'no_plates');assert.equal(empty.achievable,45);
setAvailPlates(availPlatesFor(null,gym));
const stock=plateLoadPreview('225','barbell','lb');assert.equal(stock.achievable,155);assert.ok(stock.achievable<=stock.target);
assert.equal(repMaxPreview('200','8.5','lb').status,'invalid');
assert.equal(repMaxPreview('200','85','lb').status,'invalid','unsupported reps cannot become a silently capped estimate');
assert.equal(repMaxPreview('200x','8','lb').status,'invalid');
const rm=repMaxPreview('200','8','lb');assert.equal(rm.oneRM,253);assert.equal(rm.percentages.find(row=>row.percent===85).load,215);
for(const unit of ['lb','kg'])for(const reps of [1,5,8,12]){
 const value=repMaxPreview('123.5',reps,unit);assert.equal(value.status,'valid');
 for(const row of value.percentages)assert.ok(row.load<=value.oneRM*row.percent/100+1e-9,'a percentage never exceeds its estimated ceiling');
}
setGymBarWeights(null);setAvailPlates(null);
console.log('PASS M396 loading tools: strict whole values, bounded plate outcomes, canonical estimates, atomic unit conversion, edit clocks and immutable drafts.');
