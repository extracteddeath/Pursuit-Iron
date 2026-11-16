import assert from 'node:assert/strict';
import fs from 'node:fs';
const app=fs.readFileSync('modules/App.js','utf8');
const index=fs.readFileSync('index.html','utf8');
const sw=fs.readFileSync('sw.js','utf8');
const session=app.slice(app.indexOf('function WorkoutSession('),app.indexOf('function WorkoutExerciseNav('));
// Update readiness must be a persistent choice, not a transient toast.
assert.ok(app.includes('const showUpdate = !!updateReady;'), 'update action must remain mounted on workout and minimized screens');
assert.ok(!app.includes('const showUpdate = !!updateReady && view !== "session" && !liveDockVisible;'),
    'do not hide the button while the user is training or has minimized a workout');
assert.ok(app.includes('onClick: requestUpdate, disabled: updateRestarting'),
    'the persistent button must actually invoke the update action');
assert.ok(app.includes('if (view === "session" || liveDockVisible) {\n            setConfirmUpdateRestart(true);'),
    'workout update button must ask before interrupting the session');
assert.ok(app.includes('confirmUpdateRestart && _jsx(SessionChoiceDialog'),
    'in-progress update requires a real accessible confirmation dialog');
assert.ok(app.includes('children: "Keep training"') && app.includes('children: "Save & restart"'),
    'both safe cancellation and deliberate update must be available');
assert.ok(app.includes('const on = (e) => { if (e.detail?.waiting) setUpdateReady(e.detail.waiting); };'),
    'invalid update announcements must never clear an installed update');
assert.ok(app.includes('window.addEventListener("wpb:save-workout-before-update", onSaveBeforeUpdate);'),
    'workout session must expose synchronous flush before restart');
assert.ok(app.includes('event.detail.saved = updateSaveRef.current();'),
    'the save result must be verified, not assumed');
assert.ok(app.includes('if (!receipt.handled || !receipt.saved) {'),
    'failed or unhandled workout snapshots must block restart');
assert.ok(app.includes('if (!intact) {\n                    stopUpdateAttempt('),
    'minimized workout cannot be discarded without a recoverable snapshot');
assert.ok(app.indexOf('const receipt = { handled: false, saved: false };') < app.indexOf('if (!safeStart && !(await persistAppStore()))'),
    'workout snapshot must flush before asynchronous store save');
assert.ok(app.includes('if (reg.waiting) {\n                reg.waiting.postMessage({ type: "SKIP_WAITING" });'),
    'worker is only activated after confirmation and persistence');
assert.ok(index.includes('if (!window.__pursuitUpdateRequested || reloaded) return;'),
    'controller change cannot forcibly reload an active workout without user consent');
assert.ok(sw.includes("e.data.type==='SKIP_WAITING'"),
    'service worker activation remains gated by explicit user action');
assert.ok(app.includes('window.addEventListener("pagehide", save);'),
    'background persistence remains active independent of the update prompt');
console.log('PASS B948: persistent update prompt during workouts, confirmed saved restart, no premature reload');
