import assert from 'node:assert/strict';
import fs from 'node:fs';
// B1033 performance contract: the motion layer measures, then paints. Interleaving a layout
// read after each style write forced one full style recalculation per selection group and
// per control on every screen mount (~370 ms of a 4x-throttled Settings entry).
const motion = fs.readFileSync('modules/ui-motion.js', 'utf8');
const app = fs.readFileSync('modules/App.js', 'utf8');
const body = name => { const i = motion.indexOf(name); assert.ok(i >= 0, 'missing ' + name); return motion.slice(i, motion.indexOf('\n    }\n', i)); };
assert.ok(!motion.includes('function positionTrack('), 'Selection tracks are no longer measured and painted in one interleaved pass');
const measure = body('function measureTrack(');
assert.ok(!/setProperty|dataset\.|setMotionValue|spring\(/.test(measure.replace(/selectedSurface\(group, button\)/, '')), 'measureTrack only reads');
const apply = body('function applyTrack(');
assert.ok(!/offset(Width|Left|Top|Height)|getBoundingClientRect|getComputedStyle/.test(apply), 'applyTrack only writes');
const control = body('function control(el)');
assert.ok(!/getComputedStyle|getBoundingClientRect|setProperty/.test(control), 'control() queues; it never reads or writes layout');
const flush = motion.slice(motion.indexOf('const flushMotionWork = () => {'), motion.indexOf('\n    };', motion.indexOf('const flushMotionWork = () => {')));
const firstWrite = flush.indexOf('paintSelections(work)');
assert.ok(flush.indexOf('measureTrack(') < firstWrite && flush.indexOf('filledActionCorrection(') < firstWrite,
    'All reads in a flush happen before the first paint');
const shape = body('function shape(el, held = false)');
assert.ok(shape.includes('shapedOnce') && shape.includes('setMotionValue(el, \'--pi-control-shape\''),
    'A freshly mounted control takes its shape instantly; only a change the lifter makes morphs');
assert.ok(app.includes('const [insightsReady, setInsightsReady] = useState(false);') &&
    app.includes('insightsReady ? homeCardPlan(history, { bodyweight }) : []') &&
    app.includes('showToday && insightsReady && history.length > 0 && _jsx(DeloadAdviceCard'),
    'Home paints today first; below-the-fold insight analytics follow in the next idle period');
console.log('PASS B1033 motion batching: one read pass then one write pass, mount-time shapes snap, Home insights deferred.');
