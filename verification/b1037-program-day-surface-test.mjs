import assert from 'node:assert/strict';
import fs from 'node:fs';
// B1037: a program day is one surface. Exercises are divided rows of the day card rather than
// cards inside it; muscle chips are compact AssistChips; Day focus and Add exercise close the list
// as plain sections; program-level actions are shared buttons.
const css = fs.readFileSync('app.css', 'utf8');
const app = fs.readFileSync('modules/App.js', 'utf8');

const rule = selector => { const i = css.indexOf(selector + '{'); assert.ok(i >= 0, 'missing owner ' + selector); return css.slice(i, css.indexOf('}', i)); };
const row = rule('#root[data-pi-motion="expressive"] .wpb-program .wpb-program-exercise');
for (const decl of ['background:transparent;', 'border:0;', 'border-top:1px solid var(--pi-color-divider);', 'border-radius:0;', 'margin-top:0;'])
    assert.ok(row.includes(decl), 'Exercise rows sit on the day surface: ' + decl);

const chip = rule('#root[data-pi-motion="expressive"] .wpb-program .wpb-day-hit-chip');
assert.ok(chip.includes('min-height:48px;') && chip.includes('background:transparent!important;') && chip.includes('border:0!important;'),
    'Muscle chip keeps a 48px target without drawing a 48px slab');
assert.ok(rule('#root[data-pi-motion="expressive"] .wpb-program .wpb-day-hit-chip::before').includes('inset:8px 0;'),
    'The visible pill is 32px tall inside the target');

assert.ok(!app.includes('[_jsx(PlusCircle, { size: 15 }), " Add exercise"]'), 'No dashed Add exercise card');
assert.ok(!app.includes('marginTop: 8, padding: "10px 12px", borderRadius: 12, border: `1px solid ${C.border}`'), 'No bordered Day focus box');
assert.ok(app.includes('_jsxs("section", { className: "wpb-day-focus"') && app.includes('_jsx(UiSegmented, { label: `Focus for ${day.label}`'),
    'Day focus is a section with the shared segmented control');
assert.ok(app.includes('_jsxs(UiAction, { variant: "text", className: "wpb-day-add-exercise"'), 'Add exercise is a shared text action');
assert.ok(app.includes('_jsxs(UiAction, { variant: "tonal", className: "wpb-program-add-day"'), 'Add a day is a shared tonal action');
assert.ok(app.includes('_jsxs(UiAction, { variant: "text", "aria-label": "Share program", className: "wpb-program-share-action"'),
    'Share as text is a shared text action');
assert.ok(rule('#root[data-pi-motion="expressive"] .wpb-program .wpb-schedule-day').includes('border-color:transparent!important;'),
    'Week schedule tiles are filled, not outlined; only today keeps an outline');
// B1038: filled tonal actions draw their container, not an outline; a narrow exercise row keeps
// the progression method and drops the "Auto ·" prefix first.
assert.ok(rule('#root .wpb .wpb-ui-action[data-action="tonal"]').includes('border:1px solid transparent;'), 'Tonal actions have no outline');
assert.ok(app.includes('_jsx("span", { className: "wpb-progression-auto", children: "Auto \\u00B7 " })'), 'Auto prefix is its own span');
assert.ok(css.includes('@container pi-exercise-actions (max-width:260px){#root .wpb .wpb-program .wpb-progression-auto{display:none;}}'), 'Auto prefix yields on narrow rows');
// B1039: stat tiles (Home's week recap, Profile's card metrics) are figures divided by a rule,
// not filled tiles inside their card.
assert.ok(css.includes('border-radius:0;background:transparent;box-shadow:-1px 0 0 var(--pi-color-divider);text-align:center}'), 'Home stat tiles are flat');
assert.ok(css.includes('.wpb-profile-card-metrics>div:nth-child(-n+2){padding:var(--pi-space-1) var(--pi-space-3);border-radius:0;background:transparent;box-shadow:-1px 0 0 var(--pi-color-divider)}'), 'Profile metrics are flat');
// B1040: Plan's overview figures are the flat stat row, its program routes one grouped list,
// and a finished session is marked by its check rather than struck through.
assert.ok(app.includes('_jsxs("dl", { className: "wpb-ui-stat-row wpb-plan-glance"'), 'Plan overview uses the flat stat row');
assert.ok(app.includes('_jsx(UiCard, { className: "wpb-program-create-list", "aria-label": "Start a program"'), 'Program routes are one list card');
assert.ok(!app.includes('textDecoration: d.done ? "line-through"'), 'Done sessions are not struck through');
assert.ok(app.includes('const chipCaption = w => w.status === "now" ? "This week" :'), 'The week chip does not repeat the week card count');
// B1041: the program editor's week chips name the phase when every week carries the same load,
// the day picker names the day, and only the next-due day's Start is the filled primary.
assert.ok(app.includes('caption: weekChipText(w),') && app.includes('return chipLoadsSame ? (phaseFor(program, w)?.label || weekChipCaption(w)) : weekChipCaption(w);'),
    'Identical week loads give way to phase names');
assert.ok(app.includes('className: "wpb-program-day-picker-label", children: openDayIdx >= 0 ? displayDayName(program.days[openDayIdx].label, program) : "Day"'),
    'The day picker names the open day');
assert.ok(app.includes('background: !day.exercises.length ? C.border : isNext ? C.accent : C.accentDim'), 'Only the next day Start is filled');
console.log('PASS B1037 program day: one surface with divided exercise rows, compact chips, plain Day focus and shared actions.');
