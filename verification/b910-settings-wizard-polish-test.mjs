import assert from 'node:assert/strict';
import fs from 'node:fs';
const css=fs.readFileSync('app.css','utf8');
const app=fs.readFileSync('modules/App.js','utf8');
const block=css.slice(css.lastIndexOf('/* B910 / UI polish pass 04'));
assert.ok(block.startsWith('/* B910 / UI polish pass 04'));
assert.ok(css.includes('.wpb .wpb-wizard-toolbar{')&&css.includes('min-height:var(--pi-control-min);')&&app.includes('variant: "icon", onClick: back, disabled: building'), 'Creation header uses the shared 48px icon action and growing toolbar');
assert.ok(block.includes('piB910QuestionEnter 170ms')&&block.includes('translate:0 4px'),'Short bounded question motion');
assert.ok(!block.includes('translate:0 -')&&!block.includes('opacity:0'),'No spring overshoot or opacity flash');
for(const s of ['.wpb-settings .wpb-segmented>button','.wpb-wizard-option','.wpb-wizard-choice','.wpb-wizard-preset'])
 assert.ok(block.includes(s),'Selection paint styles cover '+s);
assert.ok(block.includes('prefers-reduced-motion:reduce')&&block.includes('forced-colors:active')&&block.includes('transition:none!important'));
assert.ok(app.includes('className: "wpb-wizard-progress"')&&app.includes('className: "wpb-segmented"'),'Underlying progress and settings choice DOM remain');
console.log('PASS B910: stable wizard header, bounded non-flashing question motion and settings selection feedback.');

/* Cohesion 08: one semantic switch owner and independently named reminder choices. */
const settings=app.slice(app.indexOf('function SettingsView('),app.indexOf('\nfunction ',app.indexOf('function SettingsView(')+20));
assert.ok(app.includes('function SettingsSwitch({ className = ""'), 'Shared Settings switch exists');
assert.equal((settings.match(/_jsx\(SettingsSwitch, \{/g)||[]).length,5,'All five switches share one control');
assert.ok(!settings.includes('className: "pressable hit wpb-toggle"'),'No legacy inline toggle geometry');
for(const callback of ['setKeepAwake','setWarmupCard','setRestAutoStart','setHaptics','setReminders'])
 assert.ok(settings.includes(callback),'Preserves '+callback+' callback');
assert.ok(settings.includes('role: "group", "aria-label": "Reminder days"'),'Reminder choices are grouped');
assert.ok(settings.includes('"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"'),'Seven unique day labels');
assert.ok(settings.includes('selected: on, "aria-label": day'),'Day chips expose selection state and names');
for(const key of ['wpb-settings-switch-track','wpb-settings-switch-thumb','wpb-reminder-days','wpb-reminder-day'])
 assert.ok(css.includes('.'+key),'Shared style owner '+key);
console.log('PASS cohesion 08: Settings toggles and reminder day semantics.');
