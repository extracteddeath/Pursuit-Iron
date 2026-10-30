import assert from 'node:assert/strict';
import fs from 'node:fs';

const app=fs.readFileSync('modules/App.js','utf8');
const program=fs.readFileSync('modules/program-settings-sheet.js','utf8');
const css=fs.readFileSync('app.css','utf8');
const settings=app.slice(app.indexOf('function SettingsView('),app.indexOf('function CompareView('));
const gallery=app.slice(app.indexOf('function GalleryView('),app.indexOf('/* ⚠ ONE OWNER',app.indexOf('function GalleryView(')));
assert.ok(settings.length>10000&&gallery.length>4000,'Source must contain real Settings/Gallery views');

// Four navigation actions use one shared button and one feature geometry owner.
assert.equal(settings.split('className: "wpb-settings-link"').length-1,4,'Four Settings destinations share one native action family');
assert.ok(settings.includes('_jsxs(UiAction, { variant: "tonal", "data-align": "start", onClick: onLibrary'));
assert.ok(settings.includes('_jsxs(UiAction, { variant: "tonal", "data-align": "start", onClick: onGallery'));
assert.ok(!settings.includes('marginBottom: 8, background: C.card, border: `1px solid ${C.border}`, borderRadius: 16, cursor: "pointer", textAlign: "left"'),
  'Repeated Settings navigation inline card paint cannot be restored');
assert.ok(css.includes('#root .wpb .wpb-settings .wpb-settings-link.wpb-ui-action[data-action="tonal"]{'),
  'Settings link layout has one owner');

// A selectable plate reports the actual chosen state, not only a color difference.
assert.ok(settings.includes('_jsx(UiChoice, { kind: "chip", selected: on, "aria-label": `${p} ${unit} plate`'));
assert.ok(settings.includes('"aria-label": `Available ${unit} plates per side`'));
assert.ok(settings.includes('setPlates({ ...plates, [unit]: next })'), 'Plate inventory persistence remains unchanged');
assert.ok(settings.includes('className: "wpb-ui-card wpb-settings-plates-card"'));
assert.ok(!settings.includes('background: on ? C.accent : C.bg2'), 'Plate paint belongs to shared UiChoice');

// Gallery filters use the same choice states, with named groups for each dimension.
assert.ok(gallery.includes('const Chip = ({ active, onClick, children }) => (_jsx(UiChoice, { kind: "chip", selected: active'));
for(const name of ['Split filter','Training days filter','Training goal filter'])
 assert.ok(gallery.includes(name),'Gallery filter group must be named: '+name);
assert.ok(gallery.includes('_jsx(UiAction, { variant: "icon", className: "wpb-gallery-back"'));
assert.ok(gallery.includes('_jsx(UiAction, { variant: "secondary", onClick: () => { setErr(null); setRetryKey(k => k + 1); }'));
assert.ok(gallery.includes('_jsx(UiAction, { variant: "primary", className: "wpb-gallery-import", disabled: res && res.ok'));
assert.ok(gallery.includes('setResults(prev => ({ ...prev, [e.code]: r }))'), 'Import result tracking must remain intact');
assert.ok(!gallery.includes('wpb-gallery-chip--selected'), 'No duplicate Gallery selected-paint owner');
assert.ok(!css.includes('.wpb-gallery-chip--selected{'), 'Shared choice handles selected paint');

// Staged Program settings keep original writers/validation with shared footer actions.
assert.ok(program.includes("import { UiAction, UiChoice, UiSheetClose }"));
assert.ok(program.includes('_jsx(UiAction, { variant: "tonal", onClick: onClose, className: "wpb-program-settings-cancel"'));
assert.ok(program.includes('_jsx(UiAction, { variant: "primary", "aria-label": "Save program settings"'));
assert.ok(program.includes('onClick: applyCfgDraft, disabled: !canApply'));
assert.ok(program.includes('if (e.key === "Enter") e.currentTarget.blur()')&&program.includes("role:'alert'"),
  'Numeric draft validation and error announcements must stay intact');
assert.ok(program.includes('ref: attachPanel')&&program.includes('onKeyDown: onPanelKeyDown'),
  'Shared Program Settings action must not interfere with focus trapping');

// Ratchet against reintroducing presentation debt, without touching dynamic set geometry.
assert.ok((app.match(/style:\s*\{/g)||[]).length<=869,'Static inline App.js debt must keep falling');
assert.equal((css.match(/!important/g)||[]).length,2024,'Shared controls cannot add CSS priority hacks');
assert.ok(!css.includes('.wpb-gallery-chip--selected'),'Old Gallery state paint must stay retired');
console.log('PASS shared-control pilot: Settings/Gallery/Program settings use real semantics with intact callbacks.');
