import assert from 'node:assert/strict';
import fs from 'node:fs';

// Cohesion 09: legacy debt ceilings, never aspirational UI standards.
// Decrease alongside a verified cleanup; never raise to permit a cosmetic override.
const css = fs.readFileSync('app.css', 'utf8');
const app = fs.readFileSync('modules/App.js', 'utf8');
const MAX_IMPORTANT = 2360; // M291: consolidate the remaining Profile style owners
const MAX_APP_IMPORTANT = 1229; // M292: retire ten superseded Progress priority declarations.
const MAX_REPEAT_IMPORTANT = 15; // M288: at-rule comments no longer erase media/container scope.
const MAX_INLINE_STYLE_OBJECTS = 2800; // M292: 225 fixed Progress/History style objects retired
const allowedDebtKeys = new Set([
  "\t#root[data-pi-motion=\"expressive\"] .wpb-wizard.wpb-wizard.wpb-wizard .wpb-wizard-heading-title\tfont-size",
  "\t#root[data-pi-motion=\"expressive\"] .wpb .wpb-tabbar\tbackground-color",
  "\t#root[data-pi-motion=\"expressive\"] .wpb .wpb-program.wpb-program .wpb-day-card[data-next=\"true\"]\tborder-color",
  "\t#root[data-pi-motion=\"expressive\"] .wpb .wpb-context-menu\tborder-color",
  "@media(max-width:420px)\t#root .wpb-workout[data-focus-mode=\"false\"] .wpb-set-controls>.wpb-set-index\twidth",
  "@media(max-width:420px)\t#root .wpb-workout[data-focus-mode=\"false\"] [data-done=\"1\"] .wpb-set-controls>.wpb-set-index\twidth",
  "@media(max-width:420px)\t#root .wpb-workout[data-focus-mode=\"false\"] [data-done=\"1\"] .wpb-set-controls>.wpb-set-index\tflex",
  "\t#root .wpb-workout .wpb-ex-nav .wpb-ex-nav-step\twidth",
  "\t#root .wpb-workout .wpb-ex-nav .wpb-ex-nav-step\tmin-width",
  "\t#root .wpb-workout .wpb-ex-nav .wpb-ex-nav-step\theight",
  "\t#root .wpb-workout .wpb-ex-nav .wpb-ex-nav-step\tmin-height",
  "\t#root .wpb-workout .wpb-ex-nav .wpb-ex-nav-step\tborder-radius",
  "\t#root .wpb-workout .wpb-exercise-heading .wpb-progression-label\tfont-size",
  "\t#root[data-pi-motion=\"expressive\"] .wpb .wpb-wizard-step[data-pi-step-enter=\"1\"]\tanimation"
]);

function repeatedImportantDeclarations(source) {
    const frames = [], rules = [];
    let last = 0;
    for (let i = 0; i < source.length; i++) {
        if (source[i] === '{') {
            frames.push({ pre: source.slice(last, i).replace(/\/\*[\s\S]*?\*\//g, '').trim(), at: i });
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
assert.deepEqual(repeatedImportantDeclarations('.scoped{color:red!important;} /* reflow */ @media(max-width:420px){.scoped{color:blue!important;}}'), [],
    'Comments before at-rules cannot erase media scope or invent duplicate owners.');
assert.deepEqual(repeatedImportantDeclarations('/* narrow */ @media(max-width:420px){.scoped{color:red!important;}} /* same scope */ @media(max-width:420px){.scoped{color:blue!important;}}'),
    ['@media(max-width:420px)\t.scoped\tcolor'], 'Commented copies in the same media scope still count as competing owners.');
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
const jumpOwner=css.slice(css.indexOf('/* M287 — The R57 transitional spacing aliases'),css.indexOf('/* M270 — shared root typography'));
assert.ok(jumpOwner.includes('.wpb-settings.wpb-settings>.wpb-settings-jump{') &&
  jumpOwner.includes('padding:0 var(--pi-page-inset) var(--pi-space-2)!important;'),
  'Settings category rail is owned by the shared page inset and does not expose sticky gaps');
assert.ok(jumpOwner.includes('border-radius:var(--pi-shape-action)!important;') &&
  jumpOwner.includes('.wpb-settings-jump-control select{'),
  'Settings category choice shares the action shape and 48px touch owner');
assert.ok(css.includes('.wpb .wpb-settings-title{margin:var(--pi-space-4) var(--pi-space-1) var(--pi-space-2);font-size:var(--pi-type-support);') &&
  css.includes('.wpb .wpb-settings-row-description{font-size:var(--pi-type-support);'),
  'Settings headings and descriptions keep a legible shared support type role');
assert.ok(!css.includes('.wpb-settings .wpb-settings-jump{\n  padding-bottom:8px!important;') &&
  !css.includes('.wpb-settings .wpb-settings-jump{\n  padding:8px 16px 10px!important;') &&
  !css.includes('.wpb-settings .wpb-settings-jump-control select{\n  min-height:44px!important;'),
  'Old Settings layout and undersized select rules must not return');
assert.ok(css.includes('.wpb-settings-jump-control>span{display:none;}'),
  'Compact Settings removes duplicate visible category text while retaining native select semantics');

// M280: destination title words remain readable when root actions wrap at 200% text.
// No separate Profile-only header override; UiRootHeader remains the sole owner.
const rootTitleOwner=css.slice(css.indexOf('/* Root titles are destination names:'),css.indexOf('/* M271 — one root app bar'));
assert.ok(rootTitleOwner.includes('min-width:min-content;')&&
  rootTitleOwner.includes('overflow-wrap:normal;word-break:normal;'),
  'Root title must not break Profile mid-word beside the Edit action');
assert.ok(css.includes('.wpb-root-title-row{display:flex;flex-wrap:wrap;'),
  'Root title actions must be able to flow beneath text without overlap');


// M281: one tonal selected-pill signal, no permanent gray rim.
// Forced colors retain a visible outline, keyboard-only focus retains its ring.
assert.ok(!css.includes('--pi-r35-control-outline') &&
  !css.includes('outline:2px solid var(--pi-r35-control-outline);'),
  'Nav selection must not regain a normal-theme gray outline');
assert.ok(css.includes('.wpb-tabbar[data-pi-m3-track]::before{\n  background:var(--pi-color-active)!important;'),
  'Active nav indicator retains the saved theme tonal fill');
assert.ok(css.includes('outline:2px solid Highlight!important;') &&
  css.includes('.wpb .wpb-tabbar .wpb-tab:focus-visible{'),
  'Forced-colors selection and keyboard focus remain distinctly accessible');


// M282: text-only 200% enlargement uses the existing Settings body breakpoint
// rather than another global row override or inline runtime breakpoint.
const settingsContainer=css.slice(css.indexOf('/* Container em follows text-only enlargement'),css.indexOf('/* Preserve the R41 primary/secondary hierarchy'));
assert.ok(settingsContainer.includes('@container pi-settings-body (max-width:16em){') &&
  settingsContainer.includes('.wpb .wpb-settings .wpb-settings-row{\n    flex-direction:column;') &&
  settingsContainer.includes('.wpb .wpb-settings .wpb-settings-row-copy{\n    flex:none;') &&
  settingsContainer.includes('.wpb .wpb-settings .wpb-settings-row-control{\n    align-self:flex-start;'),
  'Enlarged Settings row text has full card width and owns its control below');

// M283: Root content and header share the same phone inset, including the
// explicit 320px/393px responsive tokens. Home retains its separate owner.
const phoneInsetStart=css.indexOf('/* Progress and Settings scroll panes match UiRootHeader:');
const phoneInsetCss=css.slice(phoneInsetStart,css.indexOf('@media(min-width:760px)',phoneInsetStart));
assert.ok(phoneInsetCss.includes(':is(.wpb-settings,.wpb-progress)>.wpb-page-scroll{')&&
  phoneInsetCss.includes('padding-inline:var(--pi-page-inset)!important;'),
  'Settings and Progress phone scroll panes share root header insets');
assert.ok(!css.includes('padding-left:14px!important;padding-right:14px!important;'),
  'The old 14px mobile page-scroll exception must not return');
assert.ok(css.includes('.wpb .wpb-home>.wpb-page-scroll{')&&
  css.includes('padding-inline:var(--pi-page-inset)!important;'),
  'Home keeps its canonical shared-inset owner');

// M284: wide populated Sessions must not reserve the full calendar height
// before the first history group, leaving a blank right reading lane.
assert.ok(css.includes('grid-column:1;grid-row:3 / span 3;min-width:0;margin-bottom:0!important;'),
  'Wide history calendar spans the grouping and first-session tracks');

// M286: short wide foldables use the existing medium-width shell rather than a
// narrow phone letterbox, without creating another high-priority CSS layer.
const landscapeOwner=css.slice(css.indexOf('/* M286 / R88 — one medium-width workspace'),
  css.indexOf('/* R67 — tablet and desktop adaptive sheet surfaces'));
assert.ok(landscapeOwner.includes('(min-width:840px) and (max-height:559px)')&&
  landscapeOwner.includes('max-width:760px!important;'),
  'Short wide landscape joins the existing bounded bottom-nav workspace');
assert.ok(css.includes('@media (min-width:840px) and (min-height:560px){'),
  'Full-height expanded windows keep the existing vertical navigation rail');


// M287: legacy R41/R51/R57 aliases had no CSS consumers; the old style
// declarations must not return as a second semantic spacing/theme palette.
// Check App.js too, because JS can reference a custom property dynamically.
const retiredAliasTokens=[
  ...Array.from({length:6},(_,i)=>'--pi-r41-gap-'+(i+1)),
  '--pi-r41-surface-3',
  '--pi-r51-page','--pi-r51-surface','--pi-r51-rail','--pi-r51-selected',
  '--pi-r51-on-selected','--pi-r51-on-rail','--pi-r51-separator',
  '--pi-r57-inset','--pi-r57-header-gap','--pi-r57-on-support'
];
for(const token of retiredAliasTokens){
  assert.ok(!css.includes(token)&&!app.includes(token),
    'No duplicate token owner or runtime consumer is permitted for '+token);
}
assert.ok(css.includes('padding-inline:var(--pi-page-inset)!important;'),
  'The maintained shared page inset survives transitional alias retirement');

// M288: the lifetime summary has a single Expressive normal/reflow owner.
for(const suffix of ['.wpb-profile-stat-grid','.wpb-profile-stat','.wpb-profile-stat .mono','.wpb-profile-stat:not(:last-child)'])
    assert.ok(!css.includes('#root[data-pi-motion="expressive"] .wpb-profile.wpb-profile.wpb-profile '+suffix+'{'),
        'Retired R17/R39/R40/R77 summary rules cannot return: '+suffix);

// M289: historical Strength tile/card rules cannot return above the maintained
// comparison family. Shared forced-colors and non-Expressive fallbacks survive.
const retiredStrengthPrefix='#root[data-pi-motion="expressive"] .wpb-profile.wpb-profile.wpb-profile';
for(const suffix of ['.wpb-profile-strength','.wpb-profile-strength-grid',
    '.wpb-profile-strength-cell','.wpb-profile-strength-cell:first-child',
    '.wpb-profile-strength-cell:not(:first-child)','.wpb-profile-strength-cell>.mono',
    '.wpb-profile-strength-grid:has(> .wpb-profile-strength-cell:only-child)',
    '.wpb-profile-strength-grid>.wpb-profile-strength-cell:only-child','.wpb-profile-next-strength'])
    assert.ok(!css.includes(retiredStrengthPrefix+' '+suffix+'{'),
        'Retired Strength presentation owner cannot return: '+suffix);

// M291: Profile has three data-derived progress widths and no fixed JSX presentation.
const profilePresentation=app.slice(app.indexOf('function ProfileView('),app.indexOf('// The app shell',app.indexOf('function ProfileView(')));
assert.ok(profilePresentation.length>500,'The existing Profile view remains present');
assert.equal((profilePresentation.match(/style:\s*\{/g)||[]).length,3,'Only three calculated Profile progress widths remain inline');
assert.equal((profilePresentation.match(/style:\s*\{\s*width:\s*`[^`]+%`\s*\}/g)||[]).length,3,'All remaining Profile inline presentation is calculated progress');
for(const suffix of ['.wpb-profile-hero','.wpb-profile-level-mark'])
    assert.ok(!css.includes('#root[data-pi-motion="expressive"] .wpb-profile.wpb-profile.wpb-profile '+suffix+'{'),
        'Retired R17 level-card style owner cannot return: '+suffix);

assert.ok(!app.includes('.wpb-profile-level-mark{')&&!app.includes('.wpb-profile-hero{'),
    'Level geometry cannot return to the embedded stylesheet');

// M291: Profile presentation uses class/state owners. Chart ink and dimensions
// remain data-derived; generic chart helpers do not embed fixed geometry.
function presentationFunction(name){const start=app.indexOf('function '+name+'(');return app.slice(start,app.indexOf('\nfunction ',start+12));}
const achievementDetails=presentationFunction('AchievementDetailSheet');
assert.equal((achievementDetails.match(/style:\s*\{/g)||[]).length,1,'Only the canonical detail progress width remains inline');
assert.ok(achievementDetails.includes('data-dialog-title')&&achievementDetails.includes('profile-achievement-detail-title'),
    'Achievement detail is named through the existing backdrop accessibility owner');
const strengthTrend=presentationFunction('StrengthScoreTrend');
assert.equal((strengthTrend.match(/style:\s*\{/g)||[]).length,6,'Trend uses only six data-derived ink properties');
assert.equal((strengthTrend.match(/"--pi-profile-trend-ink":/g)||[]).length,6,'Trend typography and geometry cannot return to JSX');
const sparkline=presentationFunction('Sparkline');
assert.equal((sparkline.match(/style:\s*\{/g)||[]).length,1,'Only data-derived empty-chart height remains inline');
assert.ok(/style:\s*\{\s*height\s*\}/.test(sparkline),'Shared Sparkline retains only calculated height');
assert.equal((profilePresentation.match(/const sectionContent =/g)||[]).length,1,'Build the section element map once per Profile render');
assert.ok(profilePresentation.includes('sectionContent[id]'),'Ordering selects existing section elements');
for(const role of ['faint: C.faint','"border-soft": C.borderSoft'])
    assert.equal((app.split(role).length-1),1,'Publish the live Profile palette role exactly once: '+role);

const embeddedProfileStart=app.indexOf('function StyleTag(');
const embeddedProfileStyles=app.slice(embeddedProfileStart,app.indexOf('\nfunction ',embeddedProfileStart+12));
assert.ok(!embeddedProfileStyles.includes('.wpb-profile'),'Profile styling may not return to the embedded stylesheet');


// M292: Progress keeps only calculated chart dimensions and data-derived ink inline.
const progressPresentation=app.slice(app.indexOf('function HistoryView('),app.indexOf('\nfunction ',app.indexOf('function HistoryView(')+12));
assert.equal((progressPresentation.match(/style:\s*\{/g)||[]).length,5,'Only five data-derived Progress chart objects remain inline');
assert.equal((progressPresentation.match(/"--pi-progress-chart-ink":/g)||[]).length,3,'Chart ink remains explicitly derived from chart data');
assert.ok(progressPresentation.includes('height: h')&&progressPresentation.includes('width: `${r.pct}%`'),
    'Calculated range height and canonical goal width retain their existing owners');
assert.ok(!progressPresentation.includes('style: { fontSize')&&!progressPresentation.includes('style: { background'),
    'Fixed Progress typography and paint cannot return to JSX');
for(const name of ['wpb-progress-tab','wpb-progress-lift-choice','wpb-history-session-toggle','wpb-progress-measure-toggle',
    'wpb-progress-body-log','wpb-progress-measure-log','wpb-progress-card','wpb-progress-grow'])
    assert.ok(progressPresentation.includes(name)&&css.includes('.'+name),'Progress presentation has a maintained class owner: '+name);
for(const state of ['[aria-selected="true"]','[aria-pressed="true"]','[aria-current="true"]',':not(:disabled)'])
    assert.ok(css.includes(state),'Native selection and disabled state own Progress paint: '+state);

for(const role of ['"warning-dim": C.warnDim','"warning-note": (C.warn || "#e0a31e") + "14"',
    '"warning-edge": (C.warn || "#e0a31e") + "44"','"accent-edge": C.accent + "55"',
    '"accent-faint": C.accent + "66"','"danger-edge": C.danger + "28"'])
    assert.equal(app.split(role).length-1,1,'Consumed Progress palette role has one publisher: '+role);
assert.ok(!css.includes('font-size:max(11px,.72rem);'),'The masked history group-count font rule may not return');
assert.match(css,/\.wpb-progress-range-chart\{[^}]*min-height:46px;[^}]*\}/,'Range-chart height fits its labels and data-derived bars');
assert.ok(!/\.wpb-progress-range-chart\{[^}]*[;{]height:46px;/.test(css),'Range-chart labels cannot be squeezed into a fixed bar-height container');
