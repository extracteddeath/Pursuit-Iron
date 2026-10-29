import assert from 'node:assert/strict';
import fs from 'node:fs';

const app=fs.readFileSync('modules/App.js','utf8');
const css=fs.readFileSync('app.css','utf8');
const names=['day-hit-chip','day-hit-value','timeline-now-label','muted-empty-icon',
  'score-chevron','phase-meta','settings-chevron','picker-search',
  'generation-backdrop','whats-new-scroll'];
const scope=css.slice(css.indexOf('/* B977 —'));
assert.ok(scope.length>1500,'B977 CSS presentation block required');
for(const n of names) {
  const selector='.wpb-b977-'+n;
  assert.ok(app.includes(selector.slice(1)),'Missing JSX owner: '+selector);
  assert.ok(scope.includes(selector+'{')||scope.includes(selector+','),'Missing CSS rule: '+selector);
}
assert.equal(app.split('style: {').length-1,1262,'B977 must retire 11 inline objects');
assert.equal(css.split('!important').length-1,2060,'No new priority overrides');
assert.ok(!scope.includes('!important'),'New CSS must not introduce important');
assert.ok(scope.includes('min-height:44px'),'Muscle coverage chips need accessible targets');
assert.ok(css.includes('.wpb-day-hit-chip{\n  min-height:44px;'),
  'Expressive theme must not override the chip target with a smaller minimum');
assert.ok(scope.includes('max-height:88%')&&scope.includes('overflow-y:auto'),
  'Whats New panel stays scrollable with bounded height');
assert.ok(scope.includes('pointer-events:none')&&scope.includes('overscroll-behavior:contain'),
  'Decorative picker search must not swallow input taps and modal scrolling must be contained');
assert.ok(scope.includes('prefers-reduced-motion:reduce')&&scope.includes('forced-colors:active'),
  'Reduced motion and forced colors supported');
assert.ok(app.includes('setOpenPart(on ? null : e.part)'),'Muscle coverage controls still expand/collapse');
assert.ok(app.includes("onClick: onCancel, onKeyDown:"),'Generation cancel and Escape retained');
assert.ok(app.includes('onDismiss: () => setWhatsNewModal(false)'),'Whats New dismissal preserved');
console.log('PASS B977: 11 static styles moved into shared CSS without altering actions or theme behavior.');
