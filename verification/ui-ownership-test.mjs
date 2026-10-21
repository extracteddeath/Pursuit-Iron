import assert from 'node:assert/strict';
import fs from 'node:fs';

// Cohesion 09: legacy debt ceilings, never aspirational UI standards.
// Decrease alongside a verified cleanup; never raise to permit a cosmetic override.
const css = fs.readFileSync('app.css', 'utf8');
const app = fs.readFileSync('modules/App.js', 'utf8');
const MAX_IMPORTANT = 2983;
const MAX_REPEAT_IMPORTANT = 32;
const MAX_INLINE_STYLE_OBJECTS = 3378; // Cohesion 13 shared week/block choices
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
    const duplicates = repeatedImportantDeclarations(cssText);
    assert.ok(important <= MAX_IMPORTANT, 'CSS priority overrides increased: ' + important + ' > ' + MAX_IMPORTANT);
    assert.ok(inline <= MAX_INLINE_STYLE_OBJECTS, 'Inline presentation debt increased: ' + inline + ' > ' + MAX_INLINE_STYLE_OBJECTS);
    assert.ok(duplicates.length <= MAX_REPEAT_IMPORTANT, 'Repeated CSS property owners increased: ' + duplicates.length + ' > ' + MAX_REPEAT_IMPORTANT);
    assert.deepEqual([...new Set(duplicates.filter(k => !allowedDebtKeys.has(k)))], [],
        'New competing selector/property owner. Edit existing shared rule instead.');
    return { important, inline, duplicates: duplicates.length, legacyOwners: new Set(duplicates).size };
}
const report = guard(css, app);
assert.throws(() => guard(css + '\n.bad-extra{color:red!important;}', app), /CSS priority overrides increased/);
assert.ok(repeatedImportantDeclarations('.duplicate{color:red!important;} .duplicate{color:blue!important;}')
    .includes('\t.duplicate\tcolor'), 'Guard must detect duplicate priority declarations.');
for (const name of ['UiAction','UiChoice','UiStatus','UiSection','SettingsSwitch'])
    assert.equal((app.match(new RegExp('function ' + name + '\\(', 'g')) || []).length, 1,
        'Only one shared ' + name + ' component may exist.');
for (const token of ['--pi-color-card','--pi-color-control','--pi-color-feature','--pi-color-divider',
    '--pi-shape-card','--pi-shape-action','--pi-shape-feature'])
    assert.ok(css.includes(token), 'Missing semantic theme role: ' + token);
assert.ok(app.includes('function BackupRestorePanel('), 'Transfers need one canonical panel.');
console.log('PASS UI ownership: important ' + report.important + '/' + MAX_IMPORTANT +
    ', repeated ' + report.duplicates + '/' + MAX_REPEAT_IMPORTANT + ' (' + report.legacyOwners +
    ' historical owner collisions), inline styles ' + report.inline + '/' + MAX_INLINE_STYLE_OBJECTS);
