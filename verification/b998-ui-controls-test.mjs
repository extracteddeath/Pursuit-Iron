// B998 — UI-only control correctness: no duplicate disclosure IDs or invalid stepper adjustments.
import assert from 'node:assert/strict';
import fs from 'node:fs';
const home=fs.readFileSync('modules/plan-session-surfaces.js','utf8');
const controls=fs.readFileSync('modules/workout-session-controls.js','utf8');
const css=fs.readFileSync('app.css','utf8');
assert.ok(home.includes("import { useId } from 'react'") && home.includes('const listId = useId()'),
    'Home/Plan each receive a stable unique disclosure ID');
assert.ok(home.includes('"aria-controls": listId') && home.includes('id: listId, hidden: !expanded'),
    'expanded control and disclosure reference the same instance ID');
assert.ok(home.includes('"aria-label": `Review ${count} plan adjustment'),
    'review banner communicates actual adjustment count');
assert.ok(controls.includes('Math.min(20, Math.max(minimum, Math.trunc(parsed)))') &&
    controls.includes('String(value).trim()') &&
    controls.includes('onChange(Math.max(minimum, count - 1))') &&
    controls.includes('onChange(Math.min(20, count + 1))'),
    'steppers do not emit NaN or values outside the visible 1–20 interval');
assert.ok(controls.includes('onChange: ev => onChange(ev.target.value)'),
    'direct typing remains delegated to the owner and is never silently overwritten');
// Design spec §§7.1,9.2,10: brief, true Home targets and explanatory details on demand.
assert.ok(home.includes('const compactDose = row.effective.length > 1') &&
    home.includes('const preview = technique') &&
    home.includes('const guidance = [!uniformTargets ?') &&
    home.includes('children: preview') && home.includes('children: guidance') &&
    home.includes('Review saved program set changes') &&
    home.includes('Review workout load choices') &&
    !home.includes('upcoming adjustment'),
    'Game Plan scans quickly and saved edits cannot be misrepresented as future-only');
assert.ok(css.includes('.wpb-working-set-stepper') && css.includes('.wpb-home-outline-toggle'),
    'controls retain their established themed CSS owners');
const sharedChoices=fs.readFileSync('modules/ui-choice-navigation.js','utf8');
const primitives=fs.readFileSync('modules/ui-primitives.js','utf8');
assert.ok(sharedChoices.includes("event.key === 'ArrowRight'") &&
    sharedChoices.includes("event.key === 'ArrowLeft'") &&
    sharedChoices.includes("event.key === 'Home'") &&
    sharedChoices.includes("event.key === 'End'") &&
    sharedChoices.includes("choice.button.focus({ preventScroll: true })") &&
    sharedChoices.includes("!button.disabled") &&
    sharedChoices.includes("button.getClientRects().length > 0"),
    'Native choice row supports arrow/Home/End and skips unavailable choices without scrolling the page');
assert.ok(primitives.includes("onKeyDown: event => navigateChoiceRow(event, index => onChange(options[index][0]))"),
    'Shared segmented groups use the same user-owned selection transaction on keyboard and tap');
assert.ok(controls.includes("onKeyDown: event => navigateChoiceRow(event, onSelect)") &&
    controls.includes("onFocus: event =>") &&
    controls.includes("revealExerciseInRail(rail.current, focused)"),
    'Exercise rail uses the same workout selection owner and reveals focused steps locally');
assert.ok(css.includes('scroll-snap-type:x proximity') && css.includes('.wpb-ex-nav-step:focus-visible'),
    'Workout rail exposes finger-friendly inertial navigation and visible keyboard focus');
console.log('PASS B998: unique Home outline controls, meaningful plan banner and bounded set steppers.');
