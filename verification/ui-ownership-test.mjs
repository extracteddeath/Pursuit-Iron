import assert from 'node:assert/strict';
import fs from 'node:fs';

// Cohesion 09: legacy debt ceilings, never aspirational UI standards.
// Decrease alongside a verified cleanup; never raise to permit a cosmetic override.
const css = fs.readFileSync('app.css', 'utf8');
const app = fs.readFileSync('modules/App.js', 'utf8');
const programReview = fs.readFileSync('modules/program-review-surfaces.js', 'utf8');
const programSchedule = fs.readFileSync('modules/program-schedule-sheet.js', 'utf8');
const programSettings = fs.readFileSync('modules/program-settings-sheet.js', 'utf8');
const manualTargets = fs.readFileSync('modules/program-manual-targets.js', 'utf8');
const backupPanel = fs.readFileSync('modules/backup-restore-panel.js', 'utf8');
const primitives = fs.readFileSync('modules/ui-primitives.js', 'utf8');
const releaseNotes = fs.readFileSync('modules/release-notes.js', 'utf8');
const disclosures = fs.readFileSync('modules/ui-disclosure.js', 'utf8');
const programReference = fs.readFileSync('modules/program-reference-surfaces.js', 'utf8');
const programVolume = fs.readFileSync('modules/program-volume-surfaces.js', 'utf8');
const loadWave = fs.readFileSync('modules/program-load-wave.js', 'utf8');
const programShare = fs.readFileSync('modules/program-share-sheet.js', 'utf8');
const exerciseOptions = fs.readFileSync('modules/program-exercise-options.js', 'utf8');
const sheetFocus = fs.readFileSync('modules/program-sheet-focus.js', 'utf8');
const exerciseNotes = fs.readFileSync('modules/exercise-notes.js', 'utf8');
const trainingMaxes = fs.readFileSync('modules/program-training-maxes.js', 'utf8');
const contextMenus = fs.readFileSync('modules/ui-menu.js', 'utf8');
const historyEditor = fs.readFileSync('modules/history-editor.js', 'utf8');
const loadingCalculator = fs.readFileSync('modules/loading-calculator.js','utf8');
const gymLoadFields = fs.readFileSync('modules/gym-load-fields.js','utf8');
const presentationSource = loadingCalculator + '\n' + gymLoadFields + '\n' + disclosures + '\n' + programReference + '\n' + releaseNotes + '\n' + primitives + '\n' + app + '\n' + programReview + '\n' + programSchedule + '\n' + programSettings + '\n' + backupPanel + '\n' + programVolume + '\n' + loadWave + '\n' + programShare + '\n' + exerciseOptions + '\n' + exerciseNotes + '\n' + trainingMaxes + '\n' + contextMenus + '\n' + historyEditor + '\n' + manualTargets;
const MAX_IMPORTANT = 2060; // M394: retire 33 exercise-row priority overrides.
const MAX_APP_IMPORTANT = 1014; // Design audit: retire duplicate Settings jump geometry/paint; measured runtime ceiling.
const MAX_REPEAT_IMPORTANT = 0; // M387: remove a competing menu border owner.
const MAX_INLINE_STYLE_OBJECTS = 1406; // B965: retire 49 additional literal presentation objects; never increase.

// M367: Settings is a named, keyboard-focusable dialog with explicit labels and state.
for(const role of ['backdrop','panel','title','intro','field-label','field','description','footer','cancel','save'])
  assert.ok(css.includes('.wpb-program-settings-'+role+'{'), 'Settings view lost semantic CSS owner '+role);
for(const token of ['role: "dialog"','"aria-modal": true','"aria-labelledby": "program-settings-title"','htmlFor: "program-settings-folder"','htmlFor: "program-settings-description"','selected: !!cfgDraft?.noSupersets','selected: !!cfgDraft?.noBodyweight','disabled: !canApply'])
  assert.ok(programSettings.includes(token), 'Settings dialog accessibility/transaction contract lost: '+token);
assert.ok(!programSettings.includes('style: iconBtn()') && !programSettings.includes('"wpb-backdrop", style:'), 'Static Settings shell styles must remain in CSS');

assert.equal((primitives.match(/export function UiSheetClose\(/g) || []).length, 1,
    'All Program sheets must share one accessible dismiss element');
assert.ok(primitives.includes('if (variant) return _jsx(UiAction,') &&
    primitives.includes('"data-icon-only": true') &&
    primitives.includes('className: `hit wpb-sheet-close') &&
    primitives.includes('"aria-label": label'),
    'Tonal close buttons must preserve the existing UiAction semantics and icon-only geometry');
for(const [name, source, handler] of [
    ['Exercise options', exerciseOptions, 'onClick: close'],
    ['Training maxes', trainingMaxes, 'onClick: onClose']
]) {
    assert.ok(source.includes('_jsx(UiSheetClose, { variant: \'tonal\'') &&
        source.includes(handler),
        name + ' uses the shared tonal sheet close without changing its callback');
}
assert.ok(trainingMaxes.includes("'data-wpb-system-back': !conversion || undefined"),
    'Training-max Back owner still sees its original close action');
assert.ok(!css.includes('.wpb-exercise-options-heading .wpb-sheet-close{') &&
    !css.includes('.wpb-training-max-heading .wpb-sheet-close{'),
    'Tonal sheet close sizing is owned by UiAction, not screen-specific flex overrides');
for (const [name, source] of [['schedule', programSchedule], ['settings', programSettings], ['share', programShare]]) {
    assert.ok(source.includes('_jsx(UiSheetClose,') && source.includes('wpb-program-' + name + '-close'),
        'Program ' + name + ' must share the native close action and preserve the feature focus locator');
}
assert.ok(css.includes('.wpb-ui-sheet-close{display:grid;place-items:center;min-width:48px;min-height:48px;flex-shrink:0;') &&
    css.includes('.wpb-ui-sheet-heading{display:flex;flex-shrink:0;') &&
    !['schedule','settings','share'].some(name => css.includes('.wpb-program-' + name + '-close{')) &&
    !css.includes('.wpb-program-settings-heading{') && !css.includes('.wpb-program-share-heading{'),
    'Program sheet chrome cannot be re-forked into screen-specific size owners');


// Icon actions in Workout and Rest share one measured 48px CSS geometry owner.
const iconActions = css.split('#root .wpb-workout :is(.wpb-workout-tools .wpb-workout-tool,.wpb-rest-actions>.wpb-rest-action){')[1]?.split('}')[0] || '';
for (const size of ['display:inline-flex','align-items:center','justify-content:center',
    'flex:0 0 48px','width:48px','min-width:48px','height:48px','min-height:48px','padding:0'])
    assert.ok(iconActions.includes(size), 'Shared Workout/Rest icon geometry lost: '+size);
for (const selector of ['#root .wpb-workout .wpb-workout-tools .wpb-workout-tool{',
    '#root .wpb-workout .wpb-rest-actions>.wpb-rest-action{']) {
    const paint = css.split(selector)[1]?.split('}')[0] || '';
    assert.ok(paint && !/(?:box-sizing|display|align-items|justify-content|flex|width|height|min-width|min-height|padding)\s*:/.test(paint),
        'Workout/Rest-specific style must not re-own common icon geometry: '+selector);
}

// Four Workout actions share a theme-aware keyboard ring without selector-specificity inflation.
const focusGroup = css.split('#root .wpb-workout .wpb-exercise-head-actions>button:focus-visible,')[1]?.split('}')[0] || '';
for (const selector of ['.wpb-workout-pinned-note:focus-visible,',
    '.wpb-workout-exercise-link:focus-visible,',
    '.wpb-rest-actions>.wpb-rest-action:focus-visible{'])
    assert.ok(focusGroup.includes(selector), 'Workout focus group missing: '+selector);
assert.ok(focusGroup.includes('outline:2px solid var(--wpb-theme-accent-ink);outline-offset:2px;'),
    'Shared Workout keyboard ring must remain visible and theme-aware');
for(const selector of ['.wpb-workout-pinned-note:focus-visible{',
    '.wpb-workout-exercise-link:focus-visible{'])
    assert.ok(!css.includes('#root .wpb-workout '+selector),
        'No standalone Workout focus geometry: '+selector);

// Workout Leave and header actions share one 48px control geometry owner.
const headerButtonGeometry=css.split('#root .wpb-workout :is(.wpb-workout-close,.wpb-workout-header-action){')[1]?.split('}')[0]||'';
for(const token of ['box-sizing:border-box','width:48px','height:48px',
    'background:transparent','color:var(--wpb-theme-text)','cursor:pointer'])
    assert.ok(headerButtonGeometry.includes(token),'Header shared CSS lost '+token);
for(const selector of ['.wpb-workout-close{','.wpb-workout-header-action{']){
    const feature=css.split('#root .wpb-workout '+selector)[1]?.split('}')[0]||'';
    assert.ok(feature&&!feature.includes('width:48px')&&!feature.includes('height:48px')&&!feature.includes('background:transparent'),
      'Workout header feature rules must not own shared geometry: '+selector);
}

const allowedDebtKeys = new Set(); // B954: no competing important-property owners

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
const report = guard(css, presentationSource);
assert.ok(!manualTargets.includes('style:') && !manualTargets.includes('localStorage'), 'Manual target text is local and its view is class-owned');
assert.ok(manualTargets.includes('parseManualTargets(draft)') && app.includes('withManualProgramTargets(p,'), 'Manual targets use the canonical validator and atomic writer');
assert.ok(app.includes('resumeExisting ? [] : programPrescriptionIssues(ownerProgram, day, wk || 1)'), 'New workouts reject invalid targets while existing logs remain resumable');

assert.ok(!historyEditor.includes('style:') && !historyEditor.includes('localStorage'), 'History correction has one class-owned view and no private persistence');
assert.ok(!app.includes('function HistoryEditSheet('), 'History correction must not retain an inline shadow renderer');
assert.ok(historyEditor.includes('normalizeEditedHistoryEntry(entry, draft)') && historyEditor.includes('onSave(corrected)'), 'Corrections are reviewed and saved through the canonical normalizer');
assert.ok(app.includes('portalTarget: historyRoot.current?.closest(".wpb")'), 'The shared Exit portal owns history-modal placement outside contained rows');
assert.ok(!contextMenus.includes('style:') && !contextMenus.includes('localStorage'), 'Context menus own presentation without private positioning or persistence');
assert.equal((app.match(/_jsx\(ContextMenu,/g)||[]).length,3,'Home actions, Program actions and the day picker share one menu renderer');
assert.ok(!app.includes('MENU_H') && !app.includes('dayOpts.top') && !app.includes('progOpts.top'),'Menu placement measures actual rows and anchors instead of guessed heights');
assert.ok(!trainingMaxes.includes('style:') && !trainingMaxes.includes('localStorage'), 'Training-max presentation has no private styles or persistence');
assert.ok(!app.includes('oneRmEdit') && !app.includes('setOneRmEdit'), 'One-rep conversion has one transient owner');
assert.ok(trainingMaxes.includes('onApply(changes)') && app.includes('withTrainingMax(next, id, value, unit)'), 'Training maxes apply through the canonical program-draft boundary');

assert.ok(!exerciseNotes.includes('style:') && !exerciseNotes.includes('localStorage'), 'Pinned notes use semantic CSS and the App persistence boundary');
assert.ok(!app.includes('function SetupFieldsGrid(') && !app.includes('function SetupFieldsDisplay(') && !app.includes('pinRef') && !app.includes('pnRef'), 'Pinned notes have no shadow grid or shared textarea ref');
assert.equal((exerciseNotes.match(/export function PinnedNoteEditor\(/g)||[]).length,1,'One controlled pinned-note editor');
assert.equal((app.match(/onSetExNote: updatePinnedNote/g)||[]).length,3,'All routes consume one pinned-note writer');
assert.equal((app.match(/onSetExSetup: updateExerciseSetup/g)||[]).length,3,'All routes consume one setup writer');
assert.ok(app.includes('noteEdit?.dayId === day.id && noteEdit?.slot === slot && noteEdit?.exId === ex.id'), 'Program notes belong to the selected row while saved notes remain per exercise');
assert.ok(!exerciseOptions.includes('style:') && !exerciseOptions.includes('localStorage') && !exerciseOptions.includes('setProgram'),
    'Exercise options own only presentation and transient confirmation');
assert.equal((exerciseOptions.match(/export function ProgramExerciseOptions\(/g)||[]).length,1,'One exercise-options owner');
assert.ok(app.includes('canLink: !program.config?.noSupersets && slot < day.exercises.length - 1'), 'Options obey the saved superset constraint');
assert.ok(!exerciseOptions.includes('"data-sheet-drag": true') &&
    exerciseOptions.includes('useProgramSheetFocus(dismiss, sheetDragRef)') &&
    exerciseOptions.includes('if (confirmRemove) setConfirmRemove(false); else close();') &&
    exerciseOptions.includes('ref: attachPanel') &&
    sheetFocus.includes("if (typeof forwardedRef === 'function') forwardedRef(node)") &&
    sheetFocus.includes('forwardedRef.current = node'),
    'The options sheet forwards the existing gesture ref through its shared focus owner');
assert.ok(app.includes('const SHEET_DRAG_BOUND = new WeakSet()') && app.includes('SHEET_DRAG_BOUND.has(el)'),
    'Shared gesture initialization follows node identity, independent of presentation flags');
assert.ok(!programShare.includes('style:') && !programShare.includes('C.'), 'Program sharing uses the shared CSS owner');
assert.ok(!app.includes('function ShareProgramSheet(') && !app.includes('function programToText('), 'One selected-week sharing owner');
assert.ok(programShare.includes('sessionPrescriptionRows(program, day, weekIndex)') && programShare.includes('daysInWeek(program, weekIndex)'), 'Shared text reads canonical selected-week prescriptions');

for (const name of ['InfoCard','MotionReveal']) {
    assert.equal((app.match(new RegExp('function '+name+'\\(', 'g')) || []).length, 0, 'No duplicate disclosure owner in App');
    assert.equal((disclosures.match(new RegExp('export function '+name+'\\(', 'g')) || []).length, 1, 'One shared disclosure owner');
}
for (const name of ['SetupCard','ProgressionCard','ProgramReferenceSheet','ProgressionChoiceSheet']) {
    assert.equal((app.match(new RegExp('function '+name+'\\(', 'g')) || []).length, 0, 'Reference view is not duplicated in the coordinator');
    assert.equal((programReference.match(new RegExp('export function '+name+'\\(', 'g')) || []).length, 1, 'One reference presentation owner');
}
assert.ok(!disclosures.includes('style:') && !programReference.includes('style:') && !programReference.includes('C.'),
    'Shared disclosure/reference presentation must stay in its CSS owner');
assert.equal((programReference.match(/_jsx\(UiSheetClose,/g) || []).length, 2,
    'Program reference and progression-method sheets share the native close primitive');
assert.ok(programReference.includes("className: 'wpb-ui-sheet-heading wpb-program-reference-heading'") &&
    css.includes('.wpb-program-reference-heading{--pi-ui-sheet-heading-gap:var(--pi-space-3)}') &&
    css.includes('gap:var(--pi-ui-sheet-heading-gap,var(--pi-space-2))') &&
    !css.includes('.wpb-program-reference-heading{display:flex;'),
    'Plan info must share the sheet-header geometry, customizing only its 12px token gap');
for (const role of ['progression-choice-close','program-reference-close']) {
    const match = css.match(new RegExp('\\.wpb-' + role + '\\{([^}]*)\\}'));
    assert.ok(match && !/(?:display|place-items|flex-shrink|min-width|min-height|color)\s*:/.test(match[1]),
        role + ' may own its distinct paint but must inherit shared button geometry');
}

assert.ok(app.includes('setProgram(p => withProgressionChoices(p, { [id]: style }))'),
    'Program and Workout must share the progression-choice writer');
assert.ok(programReference.includes("'aria-label': 'Close progression methods'") && programReference.includes("'aria-describedby': 'program-progression-scope'"),
    'Method choice keeps a named dialog, explicit scope and persistent Close action');
assert.ok(disclosures.includes("'aria-controls': contentId") && disclosures.includes('const contentId = useId()'),
    'Every disclosure connects its own accessible control and content');
assert.ok(programReference.includes('sessionPrescriptionRows(program, day, week)') && programReference.includes('start + 1, start + 2'),
    'Progression reads canonical one-based executable week/slot projections');
assert.ok(programReference.includes("'aria-label': 'Close program details'") && programReference.includes('role: \'dialog\''),
    'Program reference has a named dialog and an explicit close action');

assert.ok(app.includes("from './program-settings-sheet.js'") && !app.includes('function ProgramSettingsSheet('),
  'Program Settings has a single extracted presentation owner');
assert.equal((programSettings.match(/function ProgramSettingsSheet\(/g) || []).length, 1, 'Single settings view');
assert.ok(programSettings.includes("data-testid\": \"program-settings-sheet") &&
    programSettings.includes('onApply: applyCfgDraft') &&
    programSettings.includes('onClick: applyCfgDraft'),
    'Settings retains staged changes and Save/Cancel actions');
assert.ok(programSettings.includes('GOALS, sheetDragRef, selectNumericInput') &&
  app.includes('GOALS: GOALS, sheetDragRef:'),
  'Settings does not capture removed App module presentation globals');
for(const label of ['Decrease block length','Increase block length','Block length in weeks','program-weeks-error'])
  assert.ok(programSettings.includes(label), 'Settings duration control lost contract: '+label);
for(const role of ['cycle-length','cycle-length-value','cycle-length-help','length','length-control','length-step','length-label','length-input','length-error'])
  assert.ok(css.includes('.wpb-program-settings-'+role+'{'), 'Missing settings CSS role '+role);

assert.ok(app.includes("from './program-schedule-sheet.js'"), 'Program owns only schedule state');
assert.ok(!app.includes('function WeeklyScheduleSheet('), 'No duplicate schedule renderer in App');
assert.equal((programSchedule.match(/function WeeklyScheduleSheet\(/g) || []).length, 1, 'Schedule has a single view owner');
assert.ok(!programSchedule.includes('style: {') && !programSchedule.includes('C.') && !programSchedule.includes('iconBtn('), 'Schedule uses themed CSS');
for(const token of ['onCycle(weekday)', 'onClear', 'sheetDragRef', "'data-assigned'", "'data-today'", "'aria-modal': true"])
    assert.ok(programSchedule.includes(token), 'Schedule lost behavior/semantics: '+token);
for(const role of ['backdrop','sheet','heading','row','weekday','today','assignment','hint','clear'])
    assert.ok(css.includes('.wpb-program-schedule-' + role + '{'), 'Missing schedule CSS role: '+role);
for (const name of ['ProgramRevisionReview', 'ProgramLayoutHistory', 'ProgramSaveReview']) {
    assert.equal((app.match(new RegExp('function ' + name + '\\(', 'g')) || []).length, 0, name + ' must not remain in App');
    assert.equal((programReview.match(new RegExp('function ' + name + '\\(', 'g')) || []).length, 1, name + ' has one feature owner');
}
assert.ok(app.includes("from './program-review-surfaces.js'"), 'App composes the feature-owned Program review');
assert.ok(!programReview.includes('style: {') && !programReview.includes('style: iconBtn()'), 'No static inline review styles');
assert.ok(programReview.includes('useProgramSheetFocus(onClose)') &&
  programReview.includes('ref: attachPanel, tabIndex: -1, onKeyDown: onPanelKeyDown') &&
  programReview.includes('event.target === event.currentTarget') &&
  !programReview.includes('handleProgramSaveReviewKeys') &&
  !programReview.includes('autoFocus: true') &&
  sheetFocus.includes("event.key === 'Escape'") &&
  sheetFocus.includes("event.key !== 'Tab'") &&
  sheetFocus.includes('previous?.isConnected'),
  'Save review delegates keyboard dismissal, contained Tab and focus restoration to the shared owner');

assert.ok(programReview.includes('disabled: !changes.length || !!conflicts.length') &&
    programReview.includes('checked: scope === value') && programReview.includes('onChange: () => onScope(value)'),
    'Save scope and conflict guards remain owned by the existing plan transaction');
for(const role of ['backdrop','panel','heading','title','close','scroll','intro','scope','legend','option','help','count','change','name','summary','alert','undo','footer','confirm','cancel'])
    assert.ok(css.includes('.wpb-save-review-' + role + '{'), 'Missing Program review CSS owner: ' + role);
// Exercise the actual ceilings, even as successful cleanup creates headroom below them.
const cssOverflow = '.bad-extra{color:red!important;}\n'.repeat(MAX_IMPORTANT - report.important + 1);
const appOverflow = '.bad-embedded{color:red!important;}\n'.repeat(MAX_APP_IMPORTANT - report.appImportant + 1);
assert.throws(() => guard(css + '\n' + cssOverflow, presentationSource), /CSS priority overrides increased/);
assert.throws(() => guard(css, presentationSource + '\n' + appOverflow), /Embedded stylesheet priority overrides increased/);
assert.ok(repeatedImportantDeclarations('.duplicate{color:red!important;} .duplicate{color:blue!important;}')
    .includes('\t.duplicate\tcolor'), 'Guard must detect duplicate priority declarations.');
assert.deepEqual(repeatedImportantDeclarations('.scoped{color:red!important;} /* reflow */ @media(max-width:420px){.scoped{color:blue!important;}}'), [],
    'Comments before at-rules cannot erase media scope or invent duplicate owners.');
assert.deepEqual(repeatedImportantDeclarations('/* narrow */ @media(max-width:420px){.scoped{color:red!important;}} /* same scope */ @media(max-width:420px){.scoped{color:blue!important;}}'),
    ['@media(max-width:420px)\t.scoped\tcolor'], 'Commented copies in the same media scope still count as competing owners.');
for (const name of ['UiHeader','UiRootHeader','UiAction','UiChoice','UiStatus','UiSection','SettingsSwitch'])
    assert.equal((presentationSource.match(new RegExp('function ' + name + '\\(', 'g')) || []).length, 1,
        'Only one shared ' + name + ' component may exist.');
assert.ok(app.includes("from './ui-primitives.js'") && programSettings.includes("from './ui-primitives.js'"),
    'Screens and Settings consume the same shared UI owner');
for (const name of ['UiHeader','UiRootHeader','UiAction','UiChoice','UiStatus','UiSection']) {
    assert.equal((app.match(new RegExp('function ' + name + '\\(', 'g')) || []).length, 0, 'No shadow UI primitive in App: '+name);
    assert.equal((primitives.match(new RegExp('export function ' + name + '\\(', 'g')) || []).length, 1, 'One exported UI primitive: '+name);
}
assert.ok(app.includes("from './release-notes.js'") && !app.includes('const CHANGELOG = ['),
    'Release content has one feature owner, outside the application coordinator');
assert.equal((releaseNotes.match(/export const CHANGELOG = \[/g) || []).length, 1, 'One release catalog');
assert.ok(!releaseNotes.includes('engine-api') && !releaseNotes.includes('App.js') && !releaseNotes.includes('style:'),
    'Release content cannot import the coordinator, training logic or presentation overrides');
assert.ok(!app.includes('CHANGELOG_ITEMS =') && app.includes('CHANGELOG_ITEM_COUNT = CHANGELOG.reduce('),
    'Archive count does not eagerly project every historical note at startup');
assert.ok(!primitives.includes('engine-api') && !primitives.includes('useState') && !primitives.includes('localStorage'),
    'Shared presentation must not own training, state or persistence');
assert.ok(!programSettings.includes('style:') && !programSettings.includes('C.') && !programSettings.includes('eyebrow'),
    'Settings presentation is fully CSS-owned, including conditional choices');
for (const token of ['--pi-color-card','--pi-color-control','--pi-color-feature','--pi-color-divider',
    '--pi-shape-card','--pi-shape-action','--pi-shape-feature'])
    assert.ok(css.includes(token), 'Missing semantic theme role: ' + token);
assert.ok(app.includes("from './backup-restore-panel.js'") && !app.includes('function BackupRestorePanel('),
    'Backup transfers and staged review have one feature owner');
assert.equal((backupPanel.match(/export function BackupRestorePanel\(/g) || []).length, 1, 'Single backup panel');
assert.ok(!backupPanel.includes('style:') && !backupPanel.includes('C.') && !backupPanel.includes('localStorage'),
    'Backup presentation uses shared CSS and retains the App persistence boundary');
for (const token of ['transferBusyRef.current', 'requestId === fileReadRef.current', 'onPreviewImport(importText',
    'onImport(undoSnap, { replace: true })', 'draftConflicts?.length', '20 * 1024 * 1024'])
    assert.ok(backupPanel.includes(token), 'Backup transfer/restore safeguard lost: ' + token);
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
const action=primitives.slice(primitives.indexOf('function UiAction('),primitives.indexOf('function UiChoice('));
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
const wizardBody=app.slice(app.indexOf('function StepBody('),app.indexOf('function Stepper('));
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
const plan=app.slice(app.indexOf('function PlanView('),app.indexOf('function NextWorkoutView('));
assert.ok(plan.includes('_jsx(UiRootHeader, { title: "Plan" })')&&!plan.includes('aria-label": "Back"'),'Plan root uses shared app bar without a Back action');
const rootHeader=primitives.slice(primitives.indexOf('function UiRootHeader('),primitives.indexOf('function UiAction('));
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
// B955/B960: exact Program/Workout layout ownership; B960 reuses earlier CSS utilities. Dynamic style remains inline.
for (const cls of ["wpb-layout-grow-min","wpb-layout-heading-18-gap6","wpb-layout-flex-one","wpb-layout-truncate","wpb-layout-strong-15","wpb-layout-row-tight-bottom8","wpb-layout-bottom12","wpb-layout-row-gap8","wpb-layout-row-tight","wpb-layout-strong-18","wpb-layout-row-start-bottom8","wpb-layout-support-600","wpb-layout-bottom16","wpb-layout-row-gap8-top8"]) {
  assert.ok(css.includes("."+cls+","), "Missing shared presentation owner "+cls);
}
const b955Program=app.slice(app.indexOf("function ProgramView("),app.indexOf("\nfunction ",app.indexOf("function ProgramView(")+1));
const b955Workout=app.slice(app.indexOf("function WorkoutSession("),app.indexOf("\nfunction ",app.indexOf("function WorkoutSession(")+1));
assert.equal((b955Program.match(/className: "wpb-layout-/g)||[]).length,24,"Program static styles must be shared");
assert.equal((b955Workout.match(/className: "wpb-layout-/g)||[]).length,52,"Workout static styles must be shared");
// B956/B960: 46 reusable static CSS owners with scoped Program/Workout call-site contracts; B960 only increases use.
const b956Classes=[ "wpb-s956-display-flex", "wpb-s956-flexdirection-column", "wpb-s956-gap-6", "wpb-s956-alignitems-flex-start", "wpb-s956-fontweight-600", "wpb-s956-fontsize-14", "wpb-s956-justifycontent-space-between", "wpb-s956-gap-12", "wpb-s956-fontweight-700", "wpb-s956-alignitems-center", "wpb-s956-marginbottom-6", "wpb-s956-gap-8", "wpb-s956-flexwrap-wrap", "wpb-s956-overflow-hidden", "wpb-s956-flex-1", "wpb-s956-minwidth-0", "wpb-s956-fontsize-13", "wpb-s956-textoverflow-ellipsis", "wpb-s956-whitespace-nowrap", "wpb-s956-marginbottom-12", "wpb-s956-gap-4", "wpb-s956-margintop-8", "wpb-s956-flexshrink-0", "wpb-s956-textalign-right", "wpb-s956-margintop-2", "wpb-s956-margintop-6", "wpb-s956-display-grid", "wpb-s956-marginbottom-4", "wpb-s956-fontsize-15", "wpb-s956-margintop-12", "wpb-s956-fontsize-18", "wpb-s956-marginbottom-2", "wpb-s956-marginbottom-8", "wpb-s956-marginbottom-14", "wpb-s956-gap-7", "wpb-s956-alignitems-baseline", "wpb-s956-justifycontent-center", "wpb-s956-textalign-center", "wpb-s956-alignitems-flex-end", "wpb-s956-width-30", "wpb-s956-flex-0-0-30px", "wpb-s956-display-block", "wpb-s956-gap-5", "wpb-s956-gap-10", "wpb-s956-padding-3px-0", "wpb-s956-marginbottom-16" ];
assert.equal(b956Classes.length,46,"Shared CSS owner count regressed");
for(const cls of b956Classes)assert.ok(css.includes("."+cls+","),"Missing B956 CSS owner "+cls);
const b956Program=app.split("function ProgramView(")[1].split(String.fromCharCode(10)+"function ")[0];
const b956Workout=app.split("function WorkoutSession(")[1].split(String.fromCharCode(10)+"function ")[0];
assert.equal((b956Program.match(/className: "wpb-s956-/g)||[]).length,41,"Program CSS ownership regression");
assert.equal((b956Workout.match(/className: "wpb-s956-/g)||[]).length,57,"Workout CSS ownership regression");
// B957/B960: keep these shared classes and all migrated destinations; B960 adds reuse without removing B957 ownership.
const b957NewOwners=["wpb-s957-letterspacing-0-3","wpb-s957-padding-0-2px","wpb-s957-gap-2","wpb-s957-gap-16","wpb-s957-padding-3-5px-0","wpb-s957-height-100","wpb-s957-padding-14px-16px-6px","wpb-s957-margintop-4","wpb-s957-lineheight-1-5","wpb-s957-texttransform-capitalize"];
assert.equal(b957NewOwners.length,10);
for(const cls of b957NewOwners)assert.ok(css.includes("."+cls+","),"Missing B957 CSS owner "+cls);
assert.equal((app.split("function Home(")[1].split(String.fromCharCode(10)+"function ")[0].match(/className: "[^"]*wpb-s95(?:6|7)-/g)||[]).length,15,"Home shared owner regression");
assert.equal((app.split("function SettingsView(")[1].split(String.fromCharCode(10)+"function ")[0].match(/className: "[^"]*wpb-s95(?:6|7)-/g)||[]).length,20,"SettingsView shared owner regression");
assert.equal((app.split("function ProgressDetail(")[1].split(String.fromCharCode(10)+"function ")[0].match(/className: "[^"]*wpb-s95(?:6|7)-/g)||[]).length,10,"ProgressDetail shared owner regression");
assert.equal((app.split("function StrengthSnapshotCard(")[1].split(String.fromCharCode(10)+"function ")[0].match(/className: "[^"]*wpb-s95(?:6|7)-/g)||[]).length,9,"StrengthSnapshotCard shared owner regression");
assert.equal((app.split("function OneRepMaxCalc(")[1].split(String.fromCharCode(10)+"function ")[0].match(/className: "[^"]*wpb-s95(?:6|7)-/g)||[]).length,9,"OneRepMaxCalc shared owner regression");
assert.equal((app.split("function CompareView(")[1].split(String.fromCharCode(10)+"function ")[0].match(/className: "[^"]*wpb-s95(?:6|7)-/g)||[]).length,24,"CompareView shared owner regression");
assert.equal((app.split("function ExerciseCharts(")[1].split(String.fromCharCode(10)+"function ")[0].match(/className: "[^"]*wpb-s95(?:6|7)-/g)||[]).length,5,"ExerciseCharts shared owner regression");
assert.equal((app.split("function ThousandClubCard(")[1].split(String.fromCharCode(10)+"function ")[0].match(/className: "[^"]*wpb-s95(?:6|7)-/g)||[]).length,5,"ThousandClubCard shared owner regression");
assert.equal((app.split("function TrainingHeatmap(")[1].split(String.fromCharCode(10)+"function ")[0].match(/className: "[^"]*wpb-s95(?:6|7)-/g)||[]).length,8,"TrainingHeatmap shared owner regression");
assert.equal((app.split("function GalleryView(")[1].split(String.fromCharCode(10)+"function ")[0].match(/className: "[^"]*wpb-s95(?:6|7)-/g)||[]).length,6,"GalleryView shared owner regression");
assert.equal((app.split("function BlockReviewView(")[1].split(String.fromCharCode(10)+"function ")[0].match(/className: "[^"]*wpb-s95(?:6|7)-/g)||[]).length,12,"BlockReviewView shared owner regression");
assert.equal((app.split("function LegacyProgramsCard(")[1].split(String.fromCharCode(10)+"function ")[0].match(/className: "[^"]*wpb-s95(?:6|7)-/g)||[]).length,1,"LegacyProgramsCard shared owner regression");
assert.equal((app.split("function ChangelogSheet(")[1].split(String.fromCharCode(10)+"function ")[0].match(/className: "[^"]*wpb-s95(?:6|7)-/g)||[]).length,7,"ChangelogSheet shared owner regression");
// B958 cross-screen shared presentation ownership: 80 literal JSX objects retired.
const b958Names=["PlanView","NextWorkoutView","Home","SettingsView","ProgressDetail","CompareView","MilestonesCard","StrengthComparisonCard","RecoveryTrendCard","MuscleRecoveryCard","ProfileMiniForm","ConvertProgramToCycleSheet","VolumeCard","AddSheet","SwapSheet"];
const b958Counts=[11,6,2,2,1,1,4,8,7,3,4,5,3,10,13];
for(let i=0;i<b958Names.length;i++){
  const name=b958Names[i],start=app.indexOf("function "+name+"(");
  const end=app.indexOf(String.fromCharCode(10)+"function ",start+1);
  assert.ok(start>=0&&end>start,"B958 component moved "+name);
  assert.equal((app.slice(start,end).match(/className: "wpb-s95(?:6|7|8)-/g)||[]).length>=b958Counts[i],true,"B958 owner styles lost "+name);
}
for(const cls of ["wpb-s958-marginbottom-10","wpb-s958-position-relative","wpb-s958-fontweight-750"])assert.ok(css.includes("."+cls+","),"Missing B958 utility "+cls);
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
assert.ok(css.includes('.wpb-profile-card-metrics{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:0 var(--pi-space-3);margin:var(--pi-space-3) 0 0}'),
    'Share-card metric groups keep shared spacing instead of one-off margins');
assert.ok(css.includes('.wpb-profile-card-metrics>div{min-width:0;padding:var(--pi-space-2) 0;'),
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
  settingsContainer.includes('.wpb .wpb-settings .wpb-settings-row>.wpb-settings-row-copy{\n    flex:none;') &&
  !settingsContainer.includes('.wpb .wpb-settings .wpb-settings-row-copy{\n    flex:none;') &&
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

// Library discovery and detail presentation retain only measured goal progress inline.
const libraryView=app.slice(app.indexOf('function LibraryView('),app.indexOf('const INTRO_VERSION'));
assert.equal((libraryView.match(/style: \{/g)||[]).length,1,'Library retains only its calculated goal progress width');
assert.ok(libraryView.includes('style: { width: `${pct}%` }'),'Goal width stays derived from the saved goal and factual best');
assert.ok(!libraryView.includes('parseFloat(goalVal)'),'Goal saving cannot accept a partial numeric prefix');

const safeSplitBranch=app.slice(app.indexOf('if (stepKey === "split")'),app.indexOf('if (stepKey === "session")'));
assert.ok(safeSplitBranch.includes('const itemsFor = verdict => Array.isArray(verdict?.items) ? verdict.items : []'),
  'Split guidance must accept missing explanation items from feasibility verdicts');
assert.ok(!safeSplitBranch.includes('v.items.length')&&!safeSplitBranch.includes('v.items.join'),
  'Split guidance must not crash on verdicts with no items array');
