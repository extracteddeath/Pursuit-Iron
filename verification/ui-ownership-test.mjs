import assert from 'node:assert/strict';
import fs from 'node:fs';

// Cohesion 09: legacy debt ceilings, never aspirational UI standards.
// Decrease alongside a verified cleanup; never raise to permit a cosmetic override.
const css = fs.readFileSync('app.css', 'utf8');
const app = fs.readFileSync('modules/App.js', 'utf8');
const MAX_IMPORTANT = 2463; // M279 retired 11 superseded Settings header/control priorities
const MAX_APP_IMPORTANT = 1290; // Prevent embedded StyleTag patches bypassing the CSS budget.
const MAX_REPEAT_IMPORTANT = 21;
const MAX_INLINE_STYLE_OBJECTS = 3116; // M276: Program cycle context and edit guidance now use shared CSS
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
// Exercise the actual ceilings, even as successful cleanup creates headroom below them.
const cssOverflow = '.bad-extra{color:red!important;}\n'.repeat(MAX_IMPORTANT - report.important + 1);
const appOverflow = '.bad-embedded{color:red!important;}\n'.repeat(MAX_APP_IMPORTANT - report.appImportant + 1);
assert.throws(() => guard(css + '\n' + cssOverflow, app), /CSS priority overrides increased/);
assert.throws(() => guard(css, app + '\n' + appOverflow), /Embedded stylesheet priority overrides increased/);
assert.ok(repeatedImportantDeclarations('.duplicate{color:red!important;} .duplicate{color:blue!important;}')
    .includes('\t.duplicate\tcolor'), 'Guard must detect duplicate priority declarations.');
for (const name of ['UiRootHeader','UiAction','UiChoice','UiStatus','UiSection','SettingsSwitch'])
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
assert.ok(plan.includes('_jsx(UiRootHeader, { title: "Plan" })')&&!plan.includes('aria-label": "Back"'),'Plan root uses shared app bar without a Back action');
const rootHeader=app.slice(app.indexOf('function UiRootHeader('),app.indexOf('function UiAction('));
assert.ok(!rootHeader.includes('style:'),'Root app bar presentation is class-owned');
assert.ok(!plan.includes('style: { fontSize: 26, fontWeight: 740, color: C.text'),
  'Plan title static typography must not return to inline styles');
assert.equal((plan.match(/wpb-plan-open-program/g)||[]).length,1,
  'One full-program action belongs at the top of Plan, not a duplicate footer');
assert.ok(plan.includes('className: "wpb-plan-context-heading"') &&
  plan.indexOf('wpb-plan-open-program')<plan.indexOf('wpb-plan-panes'),
  'Full-program action stays alongside selected program context');
assert.ok(css.includes('.wpb-plan-week-picker{\n  position:sticky;top:0;z-index:3;isolation:isolate;\n  margin:0;'),
  'Sticky week rail must not expose a transparent bottom margin');
const cycles=app.slice(app.indexOf('function CyclesView('),app.indexOf('\nfunction ',app.indexOf('function CyclesView(')+1));
assert.ok(cycles.includes('defaultOpen: !c.done')&&cycles.includes('wpb-cycle-list-disclosure'),
  'Active cycle blocks remain visible by default but user-collapsible');
assert.ok(!plan.includes('className: "wpb-plan-preview-notice", style:'),
  'Plan preview typography must not return to inline styles');
// Existing runtime computed progress widths, animations and positions are permitted.
// Their removal requires an independently proved behavior-preserving migration.
const programOwner = app.slice(app.indexOf('function ProgramView('), app.indexOf('\nfunction ', app.indexOf('function ProgramView(') + 1));
for (const name of ['wpb-program-title-row','wpb-program-name-column','wpb-program-name-text',
  'wpb-program-meta-collapse','wpb-program-meta-line','wpb-program-saved-status'])
  assert.ok(programOwner.includes(name) && css.includes('.' + name + '{'),
    'Program title/status roles must have one stylesheet owner: ' + name);
const programEditHelp=programOwner.indexOf('Where do my edits apply?');
const programDetails=programOwner.indexOf('_jsx(Exit, { when: planInfo');
assert.ok(programEditHelp>programDetails && !programOwner.slice(programOwner.indexOf('wpb-program-scroll'),programOwner.indexOf('ref: pvCycle')).includes('Where do my edits apply?'),
  'Program edit scope belongs inside Details, not before the week and day plan');
for(const className of ['wpb-program-cycle-collapse','wpb-program-cycle-inset','wpb-program-cycle-link',
    'wpb-program-cycle-copy','wpb-program-cycle-name','wpb-program-cycle-meta','wpb-program-edit-scope'])
  assert.ok(programOwner.includes(className)&&css.includes('.'+className+'{'),
    'Program contextual cycle route/reference help must keep their single CSS owners: '+className);
assert.ok(!programOwner.includes('ref: pvTitle, style:') && !programOwner.includes('ref: pvNameCol, style:'),
  'Program metadata and title must not return to inline layout');
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

// M270: shared shell roles cannot silently regain per-screen typography or aliases.
const navigation = app.slice(app.indexOf('function TabBar('), app.indexOf('function OneRepMaxCalc('));
const notice = app.slice(app.indexOf('function UiNotice('), app.indexOf('function TabBar('));
assert.ok(!navigation.includes('style:') && !navigation.includes('compactLabels'), 'Navigation has one class-owned full-name layout');
assert.ok(!notice.includes('style:') && notice.includes('role: "status"'), 'Notices share class-owned accessible presentation');
for (const owner of ['Home','ProfileView','SettingsView','HistoryView','PlanView']) {
 const start=app.indexOf('function '+owner+'('), end=app.indexOf('\nfunction ', start+12);
 assert.ok(app.slice(start,end).includes('UiRootHeader'), owner+' uses the shared root app bar');
}
assert.ok(css.includes('--pi-type-screen-title:1.625rem') && css.includes('.wpb-summary-grid'), 'Scalable title and summary roles remain shared');
const noticeStyle=css.slice(css.indexOf('.wpb .wpb-notice{'),css.indexOf('.wpb .wpb-notice[data-positioned'));
assert.ok(noticeStyle.includes('transition-property:none'), 'Notice clearance cannot animate through a newly returned Resume control');


// M277: the visible History composition has one effective spatial owner.
// Prevent future sticky-edge gaps, redundant legacy declarations and nested
// card corner masks while retaining the existing 48px history actions.
const historySurfaceCss=css.slice(
    css.indexOf('/* The cycle summary is a section heading, not another pill/card. */'),
    css.indexOf('/* Rein in the page-level card radius/spacing')
);
assert.ok(historySurfaceCss.includes('gap:var(--pi-space-3)!important;'),
    'History heading and session spacing share one 12px section rhythm');
assert.ok(historySurfaceCss.includes('padding:var(--pi-space-3) var(--pi-space-1) var(--pi-space-2)!important;'),
    'History group header uses the shared spatial scale without an exposed sticky margin');
assert.ok(historySurfaceCss.includes('margin:0 0 var(--pi-space-3)!important;'),
    'History session gap uses the same scale as the section heading');
assert.ok(historySurfaceCss.includes('border-radius:0!important;'),
    'Expanded History detail inherits outer clipping instead of drawing a second corner');
assert.ok(!css.includes('.wpb-progress .wpb-history-session{\n  margin-bottom:7px!important;'),
    'Retired history session margin must not fight the effective card gap');
assert.ok(!css.includes('padding-top:11px!important;\n  padding-bottom:8px!important;'),
    'Retired history header padding must not fight the final sticky surface');

// M278: Profile's active three-score comparison has one responsive owner;
// the editable share-card uses the same 4/8/12/16px spacing vocabulary.
assert.ok(css.includes('.wpb-profile-card-metrics{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:0 var(--pi-space-4);margin:var(--pi-space-4) 0 0}'),
    'Share-card metric groups keep shared spacing instead of one-off margins');
assert.ok(css.includes('.wpb-profile-card-metrics>div{min-width:0;padding:var(--pi-space-3) 0;'),
    'Share-card metric rows keep the shared internal rhythm');
assert.ok(css.includes('.wpb-profile-strength-grid>.wpb-profile-strength-cell{'),
    'Strength comparison cells still share one explicit active row layout');
assert.ok(!css.includes('.wpb-profile .wpb-profile-strength-grid{\n  gap:6px!important;'),
    'The superseded Profile strength gap must not regain priority');
assert.ok(!css.includes('.wpb-profile .wpb-profile-strength-cell{\n  padding:9px 5px!important;'),
    'The superseded Profile strength row padding must not regain priority');
assert.ok(!css.includes('.wpb-profile-stat-grid{gap:0!important}'),
    'Responsive Profile facts must not duplicate the existing gap owner');

// M279: Settings has one final category rail owner and one class-owned card family.
// The page inset handles 320px widths without parallel hardcoded media overrides.
const jumpOwner=css.slice(css.indexOf('/* R57 — one spacing cadence'),css.indexOf('/* M270 — shared root typography'));
assert.ok(jumpOwner.includes('.wpb-settings.wpb-settings>.wpb-settings-jump{') &&
  jumpOwner.includes('padding:0 var(--pi-page-inset) var(--pi-space-2)!important;'),
  'Settings category rail is owned by the shared page inset and does not expose sticky gaps');
assert.ok(jumpOwner.includes('border-radius:var(--pi-shape-action)!important;') &&
  jumpOwner.includes('.wpb-settings-jump-control select{'),
  'Settings category choice shares the action shape and 48px touch owner');
assert.ok(css.includes('.wpb .wpb-settings-title{margin:var(--pi-space-4) var(--pi-space-1) var(--pi-space-2);font-size:var(--pi-type-support);') &&
  css.includes('.wpb .wpb-settings-row-description{font-size:var(--pi-type-support);'),
  'Settings headings and descriptions keep a legible shared support type role');
assert.ok(!css.includes('.wpb-settings .wpb-settings-jump{\\n  padding-bottom:8px!important;') &&
  !css.includes('.wpb-settings .wpb-settings-jump{\\n  padding:8px 16px 10px!important;') &&
  !css.includes('.wpb-settings .wpb-settings-jump-control select{\\n  min-height:44px!important;'),
  'Old Settings layout and undersized select rules must not return');
