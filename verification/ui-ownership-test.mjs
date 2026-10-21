import assert from 'node:assert/strict';
import fs from 'node:fs';

// Cohesion 09: legacy debt ceilings, never aspirational UI standards.
// Decrease alongside a verified cleanup; never raise to permit a cosmetic override.
const css = fs.readFileSync('app.css', 'utf8');
const app = fs.readFileSync('modules/App.js', 'utf8');
const MAX_IMPORTANT = 2812;
const MAX_APP_IMPORTANT = 1325; // Prevent embedded StyleTag patches bypassing the CSS budget.
const MAX_REPEAT_IMPORTANT = 30;
const MAX_INLINE_STYLE_OBJECTS = 3195; // Cohesion 20: Settings cards/rows and Profile club migrated
const allowedDebtKeys = new Set([
  "\t#root .wpb-workout .wpb-ex-nav .wpb-ex-nav-step\tborder-radius",
  "\t#root .wpb-workout .wpb-ex-nav .wpb-ex-nav-step\theight",
  "\t#root .wpb-workout .wpb-ex-nav .wpb-ex-nav-step\tmin-height",
  "\t#root .wpb-workout .wpb-ex-nav .wpb-ex-nav-step\tmin-width",
  "\t#root .wpb-workout .wpb-ex-nav .wpb-ex-nav-step\twidth",
  "\t#root .wpb-workout .wpb-exercise-heading .wpb-progression-label\tfont-size",
  "\t#root .wpb-workout .wpb-workout-tools\tmargin",
  "\t#root .wpb-workout .wpb-workout-tools\tpadding",
  "\t#root[data-pi-motion=\"expressive\"] .wpb .wpb-context-menu\tborder-color",
  "\t#root[data-pi-motion=\"expressive\"] .wpb .wpb-home-create\tbackground-color",
  "\t#root[data-pi-motion=\"expressive\"] .wpb .wpb-plan-view\tmax-width",
  "\t#root[data-pi-motion=\"expressive\"] .wpb .wpb-profile.wpb-profile .wpb-profile-stat\tmin-height",
  "\t#root[data-pi-motion=\"expressive\"] .wpb .wpb-profile.wpb-profile .wpb-profile-stat\tpadding",
  "\t#root[data-pi-motion=\"expressive\"] .wpb .wpb-profile.wpb-profile .wpb-profile-stat:not(:last-child)\tborder-right",
  "\t#root[data-pi-motion=\"expressive\"] .wpb .wpb-program.wpb-program .wpb-day-card[data-next=\"true\"]\tborder-color",
  "\t#root[data-pi-motion=\"expressive\"] .wpb .wpb-settings .wpb-experience-segmented\tgrid-template-columns",
  "\t#root[data-pi-motion=\"expressive\"] .wpb .wpb-tabbar\tbackground-color",
  "\t#root[data-pi-motion=\"expressive\"] .wpb .wpb-tabbar .wpb-tab\tmin-height",
  "\t#root[data-pi-motion=\"expressive\"] .wpb .wpb-tabbar .wpb-tab>span\tfont-size",
  "\t#root[data-pi-motion=\"expressive\"] .wpb .wpb-tabbar .wpb-tab>span\tline-height",
  "\t#root[data-pi-motion=\"expressive\"] .wpb .wpb-wizard-step[data-pi-step-enter=\"1\"]\tanimation",
  "\t#root[data-pi-motion=\"expressive\"] .wpb-exercise-detail.wpb-exercise-detail.wpb-exercise-detail .wpb-exercise-detail-body\tpadding-inline",
  "\t#root[data-pi-motion=\"expressive\"] .wpb-profile.wpb-profile.wpb-profile .wpb-profile-head\tmin-height",
  "\t#root[data-pi-motion=\"expressive\"] .wpb-progress .wpb-history-actions\tgap",
  "\t#root[data-pi-motion=\"expressive\"] .wpb-progress .wpb-progress-title\tfont-size",
  "\t#root[data-pi-motion=\"expressive\"] .wpb-progress .wpb-progress-title\tmargin-bottom",
  "\t#root[data-pi-motion=\"expressive\"] .wpb-progress>.wpb-page-header\tpadding-top",
  "\t#root[data-pi-motion=\"expressive\"] .wpb-wizard.wpb-wizard.wpb-wizard .wpb-wizard-heading-title\tfont-size",
  "\t#root[data-pi-motion=\"expressive\"] .wpb>div:has(>.wpb-tabbar)\tmax-width",
  "\t#root[data-pi-motion=\"expressive\"] .wpb>div:has(>.wpb-tabbar)\twidth"
]);

function repeatedImportantDeclarations(source) {
    const frames = [], rules = [];
    let last = 0;
    for (let i = 0; i < source.length; i++) {
        if (source[i] === '{') {
            frames.push({ pre: source.slice(last, i).trim(), at: i });
            last = i + 1;
        } else if (source[i] === '}') {
            const frame = frames.pop();
            if (frame) {
                const body = source.slice(frame.at + 1, i);
                if (!/[{}]/.test(body)) {
                    const selector = frame.pre.replace(/\/\*[\s\S]*?\*\//g, '').trim();
                    const context = frames.filter(x => x.pre.startsWith('@')).map(x => x.pre).join('|');
                    rules.push({ selector, context, body });
                }
            }
            last = i + 1;
        }
    }
    const seen = new Set(), repeated = [];
    for (const rule of rules) {
        for (const m of rule.body.matchAll(/(?:^|;)\s*([\w-]+)\s*:\s*([^;{}]*?)\s*!important\s*(?=;|$)/g)) {
            const key = [rule.context, rule.selector, m[1]].join('\t');
            if (seen.has(key)) repeated.push(key);
            seen.add(key);
        }
    }
    return repeated;
}
function guard(cssText, appText) {
    const important = (cssText.match(/!important/g) || []).length;
    const inline = (appText.match(/style: \{/g) || []).length;
    const appImportant = (appText.match(/!important/g) || []).length;
    const duplicates = repeatedImportantDeclarations(cssText);
    assert.ok(important <= MAX_IMPORTANT, 'CSS priority overrides increased: ' + important + ' > ' + MAX_IMPORTANT);
    assert.ok(appImportant <= MAX_APP_IMPORTANT, 'Embedded stylesheet priority overrides increased: ' + appImportant + ' > ' + MAX_APP_IMPORTANT);
    assert.ok(inline <= MAX_INLINE_STYLE_OBJECTS, 'Inline presentation debt increased: ' + inline + ' > ' + MAX_INLINE_STYLE_OBJECTS);
    assert.ok(duplicates.length <= MAX_REPEAT_IMPORTANT, 'Repeated CSS property owners increased: ' + duplicates.length + ' > ' + MAX_REPEAT_IMPORTANT);
    assert.deepEqual([...new Set(duplicates.filter(k => !allowedDebtKeys.has(k)))], [],
        'New competing selector/property owner. Edit existing shared rule instead.');
    return { important, appImportant, inline, duplicates: duplicates.length, legacyOwners: new Set(duplicates).size };
}
const report = guard(css, app);
assert.throws(() => guard(css + '\n.bad-extra{color:red!important;}', app), /CSS priority overrides increased/);
assert.throws(() => guard(css, app + '\n.bad-embedded{color:red!important;}'), /Embedded stylesheet priority overrides increased/);
assert.ok(repeatedImportantDeclarations('.duplicate{color:red!important;} .duplicate{color:blue!important;}')
    .includes('\t.duplicate\tcolor'), 'Guard must detect duplicate priority declarations.');
for (const name of ['UiAction','UiChoice','UiStatus','UiSection','SettingsSwitch'])
    assert.equal((app.match(new RegExp('function ' + name + '\\(', 'g')) || []).length, 1,
        'Only one shared ' + name + ' component may exist.');
for (const token of ['--pi-color-card','--pi-color-control','--pi-color-feature','--pi-color-divider',
    '--pi-shape-card','--pi-shape-action','--pi-shape-feature'])
    assert.ok(css.includes(token), 'Missing semantic theme role: ' + token);
assert.ok(app.includes('function BackupRestorePanel('), 'Transfers need one canonical panel.');
// Canonical role vocabulary and primitive owners must survive future polish passes.
for(const token of [
  '--pi-color-active','--pi-color-on-active','--pi-ui-primary-fill','--pi-ui-primary-ink',
  '--pi-space-1','--pi-space-2','--pi-space-3','--pi-space-4','--pi-space-6',
  '--pi-type-control','--pi-type-support','--pi-shape-card','--pi-shape-action',
  '--pi-shadow-card','--pi-card-padding'
]) assert.ok(css.includes(token+':'), 'Missing canonical design token: '+token);
for(const primitive of [
  '.wpb .wpb-ui-card{','.wpb .wpb-ui-field{','.wpb .wpb-ui-control{',
  '.wpb .wpb-ui-stack{','.wpb .wpb-ui-row{'
]) assert.ok(css.includes(primitive), 'Missing shared primitive: '+primitive);
for(const selector of ['input:not([type="range"]):not(.wpb-ui-control)', 'select:not(.wpb-ui-control)', 'textarea:not(.wpb-ui-control)'])
  assert.ok(app.includes(selector),'Legacy form layer must leave shared controls to their canonical owner: '+selector);
const action=app.slice(app.indexOf('function UiAction('),app.indexOf('function UiChoice('));
assert.ok(!action.includes('style: {') && !action.includes('C.accent'),
  'Shared UiAction may not inject presentation overrides at render time');
for(const str of ['wpb-ui-card wpb-profile-share-card','wpb-ui-card wpb-custom-cycle',
  'wpb-ui-control','wpb-ui-field wpb-profile-card-field','wpb-ui-stack wpb-custom-cycle-list',
  'wpb-ui-row wpb-profile-card-actions'])
  assert.ok(app.includes(str),'Migrated screen must continue to use shared primitive: '+str);
// Cohesion 16: creation controls may not reinject static geometry or theme paint.
const wizardOption=app.slice(app.indexOf('function OptionCard('),app.indexOf('/* ============================== WIZARD'));
const wizardHeading=app.slice(app.indexOf('function Heading('),app.indexOf('/* B893:'));
const wizardUnitsMode=app.slice(app.indexOf('function StepBody('),app.indexOf('    if (stepKey === "cycletype")'));
const wizardCycle=app.slice(app.indexOf('    if (stepKey === "cycletype")'),app.indexOf('    if (stepKey === "name")'));
const wizardName=app.slice(app.indexOf('    if (stepKey === "name")'),app.indexOf('    if (stepKey === "experience")'));
for(const [owner,source] of [['OptionCard',wizardOption],['Heading/Col',wizardHeading],['Units and mode',wizardUnitsMode],['Program name',wizardName],['Cycle templates and adaptation',wizardCycle]]){
  assert.ok(source.length>100,'Missing scoped Wizard owner '+owner);
  assert.ok(!source.includes('style: {'),'Static Wizard presentation returned to JSX: '+owner);
}
const equipment=app.slice(app.indexOf('    if (stepKey === "equipment")'),app.indexOf('    if (stepKey === "focus")'));
const restrictions=app.slice(app.indexOf('function NoRackToggle('),app.indexOf('function OptionCard('));
for(const [owner,source] of [['Equipment and constraints',equipment],['Equipment restrictions',restrictions]]){
  assert.ok(source.length>100,'Missing migrated owner '+owner);
  assert.ok(!source.includes('style: {'),'Static presentation returned to '+owner);
}
assert.equal((app.match(/function EquipmentRestriction\(/g)||[]).length,1,'Equipment exclusions have one presentation owner');
assert.ok(restrictions.includes('_jsxs(UiAction,')&&restrictions.includes('"aria-pressed": !!selected'),
  'Equipment restrictions retain shared action semantics and selection state');
assert.ok(equipment.includes('role: "group", "aria-label": "Supersets"')&&
  equipment.includes('role: "group", "aria-label": "Barbell limit"'),'Constraint choices retain named groups');
assert.ok(wizardOption.includes('aria-pressed')&&wizardOption.includes('disabled')&&wizardOption.includes('kind === "radio"'),
  'Wizard choice accessibility and radio/checkbox behavior preserved');
assert.ok(wizardName.includes('"data-has-value": config.name ? "true" : "false"'),
  'Program name displays selected border from the real controlled value');
for(const selector of [
  '.wpb .wpb-wizard-option{','.wpb .wpb-wizard-option:disabled{',
  '.wpb .wpb-wizard-option[aria-pressed="true"]{',
  '.wpb .wpb-wizard-unit-option{','.wpb .wpb-wizard-mode-option{',
  '.wpb .wpb-wizard-name-input{','.wpb .wpb-wizard-options{'
])assert.ok(css.includes(selector),'Missing reusable Wizard CSS owner '+selector);
assert.ok(css.includes('.wpb .wpb-wizard-name-input[data-has-value="true"]'),
  'Controlled name input has its own selected border');
// The complete creation presentation now has no static JSX styles.
const wizardShell=app.slice(app.indexOf('function Wizard({'),app.indexOf('function Heading('));
const wizardBody=app.slice(app.indexOf('function StepBody('),app.indexOf('// Editable row'));
const setupStepper=app.slice(app.indexOf('function Stepper('),app.indexOf('// Training-phase narrative'));
assert.equal((wizardShell.match(/style: \{/g)||[]).length,1,'Only computed progress transform remains inline in Wizard');
assert.ok(wizardShell.includes('style: { transform: `translateX('),'Wizard retains calculated progress transform');
for(const [owner,source] of [['All setup questions',wizardBody],['Setup stepper',setupStepper]])
  assert.ok(source.length>100&&!source.includes('style:'),'Static inline presentation returned to '+owner);
assert.ok(wizardShell.includes('_jsxs(UiHeader,')&&wizardShell.includes('_jsx(UiAction,'),
  'Wizard chrome consumes shared header/actions');
assert.ok(wizardShell.includes('"data-initial-step": step === 0')&&
  wizardBody.includes('"data-at-limit": focusTotal >= 5')&&wizardBody.includes('"data-focused": v > 0'),
  'Real setup state owns layout and focus emphasis');
assert.ok(setupStepper.includes('disabled: value === 0')&&setupStepper.includes('disabled: plusDisabled'),
  'Shared stepper retains native limit semantics');
const plan=app.slice(app.indexOf('function PlanView('),app.indexOf('function sessionLoadText('));
assert.ok(plan.includes('className: "wpb-plan-eyebrow"'),'Plan eyebrow must be class-owned');
assert.ok(!plan.includes('style: { fontSize: 26, fontWeight: 740, color: C.text'),
  'Plan title static typography must not return to inline styles');
assert.ok(!plan.includes('className: "wpb-plan-preview-notice", style:'),
  'Plan preview typography must not return to inline styles');
// Existing runtime computed progress widths, animations and positions are permitted.
// Their removal requires an independently proved behavior-preserving migration.
console.log('PASS UI ownership: important ' + report.important + '/' + MAX_IMPORTANT +
    ', embedded ' + report.appImportant + '/' + MAX_APP_IMPORTANT + ', repeated ' + report.duplicates + '/' + MAX_REPEAT_IMPORTANT + ' (' + report.legacyOwners +
    ' historical owner collisions), inline styles ' + report.inline + '/' + MAX_INLINE_STYLE_OBJECTS);

// Cohesion 20: class-owned Settings groups and Profile club; runtime progress stays explicit.
const settingsRow = app.slice(app.indexOf('const SettingsRow ='), app.indexOf('// Browser persistence'));
assert.ok(!settingsRow.includes('style:'), 'Settings row has no inline presentation');
assert.ok(app.includes('className: "wpb-settings-title", children:'), 'Settings title has a CSS owner');
const club = app.slice(app.indexOf('function ProfileStrengthClub('), app.indexOf('/* B892: Presentation-only'));
assert.equal((club.match(/style: \{/g) || []).length, 1, 'Club keeps only its calculated progress width inline');
assert.ok(club.includes('style: { width: `${snap.thousandPct}%` }'), 'Club progress derives from the canonical snapshot');
assert.equal((club.match(/variant: "tonal"/g) || []).length, 2, 'Club share and measure controls reuse UiAction');
const settingsView = app.slice(app.indexOf('function SettingsView('), app.indexOf('/* ============================== CYCLES VIEW'));
assert.equal((settingsView.match(/className: "wpb-ui-card wpb-settings-card"/g) || []).length, 8, 'All eight Settings row groups use shared cards');
assert.ok(!settingsView.includes('style: { background: C.card, borderRadius: 16, overflow: "hidden"'), 'No unnamed legacy row cards remain');
