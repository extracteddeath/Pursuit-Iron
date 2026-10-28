import assert from 'node:assert/strict';
import {
  availPlatesFor, setAvailPlates, setGymBarWeights, platesPerSide, platePairsFor
} from '../modules/training-domain/analytics.js';
import { plateLoadPreview } from '../modules/training-domain/loading-tools.js';

const lb = plates => ({ platePairs: { lb: plates } });
try {
  setGymBarWeights(null);
  // Match the screenshot gym: standard 45/25/10/5/2.5 plates, no 35s.
  setAvailPlates(availPlatesFor(null, lb({ 45: 4, 25: 2, 10: 2, 5: 2, 2.5: 2 })));

  // Screenshot regression: both arrangements reach 260 lb, but the heaviest-first
  // arrangement is more conventional for a 45 lb bar and a standard plate rack.
  const result = platesPerSide(260, 'barbell', 'lb');
  assert.deepEqual(result.plates, [45, 45, 10, 5, 2.5]);
  assert.equal(45 + 2 * result.plates.reduce((sum, p) => sum + p, 0), 260);
  const preview = plateLoadPreview('260', 'barbell', 'lb');
  assert.equal(preview.status, 'exact');
  assert.equal(preview.achievable, 260);
  assert.deepEqual(preview.plates, result.plates, 'sheet and workout row share one solver');

  // Total plate count takes priority over heavier denominations.
  setAvailPlates(availPlatesFor(null, {}));
  const fewerPlates = platesPerSide(215, 'barbell', 'lb');
  assert.deepEqual(fewerPlates.plates, [45, 35, 5]);

  // Physical plate-pair limits always outrank presentation preferences.
  setAvailPlates(availPlatesFor(null, lb({ 45: 1, 25: 2, 10: 1, 5: 1, 2.5: 1 })));
  assert.deepEqual(platesPerSide(260, 'barbell', 'lb').plates, [45, 25, 25, 10, 2.5]);
  setAvailPlates(availPlatesFor(null, lb({ 45: 1, 35: 2 })));
  const limited = plateLoadPreview('185', 'barbell', 'lb');
  assert.equal(limited.status, 'exact');
  assert.deepEqual(limited.plates, [35, 35]);
  assert.equal(plateLoadPreview('365', 'barbell', 'lb').achievable, 275,
    'no phantom plates when a target exceeds stock');

  // Empty inventory, custom bar weight, and other units must remain safe.
  setAvailPlates(availPlatesFor(null, lb({})));
  assert.equal(plateLoadPreview('135', 'barbell', 'lb').status, 'no_plates');
  assert.equal(plateLoadPreview('135', 'barbell', 'lb').achievable, 45);
  setAvailPlates(availPlatesFor(null, {}));
  setGymBarWeights({ unit: 'lb', barbell: 35 });
  assert.equal(plateLoadPreview('125', 'barbell', 'lb').achievable, 125);
  assert.deepEqual(plateLoadPreview('125', 'barbell', 'lb').plates, [45]);
  setGymBarWeights(null);
  assert.equal(plateLoadPreview('100', 'barbell', 'kg').achievable, 100);
  assert.equal(plateLoadPreview('100', 'barbell', 'kg').plates.reduce((a,b)=>a+b,0), 40);
  assert.equal(platePairsFor({ platePairs: { lb: { 45: 1 } } }, 'lb')[45], 1);
  console.log('PASS plate selection: canonical heaviest-first ties, minimal count, inventory limits, exact total, custom bar and kg');
} finally {
  setAvailPlates(null);
  setGymBarWeights(null);
}
