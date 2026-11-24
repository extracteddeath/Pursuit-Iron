import assert from 'node:assert/strict';
import fs from 'node:fs';
// B1034 performance contract: keep style recalculation proportional to what changed.
// 1. A rule whose rightmost compound is a multi-argument :is()/:where() cannot be indexed by
//    class, id, tag or attribute, so the browser tests it against every element on every
//    restyle. Those lists were ~40% of all selector-matching time across the five tabs.
// 2. Values ui-motion springs every frame are registered non-inherited, so a moving sheet,
//    backdrop, control or selection track restyles itself rather than its whole subtree.
const css = fs.readFileSync('app.css', 'utf8');
const app = fs.readFileSync('modules/App.js', 'utf8');
const styleTag = app.slice(app.indexOf('function StyleTag('), app.indexOf('` }));', app.indexOf('function StyleTag(')))
    .replace(/\$\{[^{}]*(?:\{[^{}]*\}[^{}]*)*\}/g, '0');

function splitTop(text) {
    const out = []; let depth = 0, cur = '';
    for (const ch of text) {
        if (ch === '(' || ch === '[') depth++;
        else if (ch === ')' || ch === ']') depth--;
        if (ch === ',' && depth === 0) { out.push(cur); cur = ''; } else cur += ch;
    }
    out.push(cur); return out;
}
function lastCompound(selector) {
    let depth = 0, start = 0;
    for (let i = 0; i < selector.length; i++) {
        const ch = selector[i];
        if (ch === '(' || ch === '[') depth++;
        else if (ch === ')' || ch === ']') depth--;
        else if (depth === 0 && ' >+~'.includes(ch)) start = i + 1;
    }
    return selector.slice(start);
}
function unindexable(source) {
    const bad = [];
    const text = source.replace(/\/\*[\s\S]*?\*\//g, '');
    for (const match of text.matchAll(/([^{};]+)\{/g)) {
        const prelude = match[1].trim();
        if (!prelude || prelude.startsWith('@') || /^(from|to|\d+%)/.test(prelude)) continue;
        for (const selector of splitTop(prelude)) {
            const compound = lastCompound(selector.trim());
            const fn = compound.startsWith(':is(') ? 4 : compound.startsWith(':where(') ? 7 : 0;
            if (!fn) continue;
            let depth = 1, end = fn;
            for (; end < compound.length && depth; end++) {
                if (compound[end] === '(') depth++; else if (compound[end] === ')') depth--;
            }
            if (splitTop(compound.slice(fn, end - 1)).length > 1) bad.push(selector.trim().slice(0, 160));
        }
    }
    return bad;
}
for (const [name, source] of [['app.css', css], ['StyleTag', styleTag],
    ['workout-machine-switch.css', fs.readFileSync('modules/workout-machine-switch.css', 'utf8')]]) {
    const bad = unindexable(source);
    assert.deepEqual(bad, [], name + ': write one selector per target instead of a rightmost :is()/:where() list');
}
// The rewrite kept the cascade: where list arguments had lower specificity than the list,
// the padded forms are present (never-matching :not(pi-pad…) or a repeated class).
assert.ok(css.includes(':where(.wpb-ui-choice') || css.includes('.wpb-ui-choice.wpb-ui-choice.wpb-ui-choice'),
    'Mixed-specificity lists keep the :is() list specificity');
assert.ok(!/class(Name)?\s*[:=][^\n]*pi-pad/.test(app), 'pi-pad is a specificity placeholder, never a real class');

const motionValues = ['pi-surface-x', 'pi-surface-y', 'pi-surface-opacity', 'pi-surface-scale', 'pi-backdrop-opacity',
    'pi-sheet-radius', 'pi-disclosure-radius', 'pi-control-shape', 'pi-control-scale', 'pi-corner-0', 'pi-corner-1',
    'pi-corner-2', 'pi-corner-3', 'pi-choice-grow', 'pi-switch-x', 'pi-m3-track-x', 'pi-m3-track-y',
    'pi-m3-track-width', 'pi-m3-track-height'];
for (const name of motionValues)
    assert.ok(css.includes('@property --' + name + '{syntax:"*";inherits:false}'), 'Per-frame motion value is non-inherited: --' + name);
const motion = fs.readFileSync('modules/ui-motion.js', 'utf8');
for (const name of motionValues.filter(n => !/corner-/.test(n)))
    assert.ok(motion.includes("'--" + name + "'"), 'Registered value is still written by ui-motion: --' + name);
// Non-inherited values only reach the element that carries them. Every consumer must be that
// element, except the selection tracks, whose ::before inherits them explicitly.
const trackBefore = [...css.matchAll(/\[data-pi-m3-track\]::before\{([^}]*)\}/g)].map(m => m[1])
    .filter(body => /var\(--pi-m3-track-(x|y|width|height)/.test(body));
assert.equal(trackBefore.length, 3, 'Three selection-track indicators read the track geometry');
for (const body of trackBefore) {
    for (const key of ['x', 'y', 'width', 'height'])
        assert.ok(body.includes('--pi-m3-track-' + key + ':inherit'), 'Track ::before inherits --pi-m3-track-' + key);
}
const consumers = name => [...css.replace(/\/\*[\s\S]*?\*\//g, '').matchAll(new RegExp('([^{}]*)\\{([^{}]*var\\(--' + name + '\\b[^{}]*)\\}', 'g'))]
    .map(m => m[1].trim());
for (const [name, owner] of [
    ['pi-surface-y', /\[data-pi-surface\]$/], ['pi-backdrop-opacity', /\[data-pi-backdrop\]$/],
    ['pi-sheet-radius', /\[data-pi-surface\]$/], ['pi-control-shape', /button\[data-pi-control\]$/],
    ['pi-switch-x', /\[data-pi-thumb\]$/], ['pi-disclosure-radius', /\[data-pi-disclosure\]$/]]) {
    const list = consumers(name);
    assert.ok(list.length > 0, 'consumer found for --' + name);
    for (const prelude of list)
        for (const selector of splitTop(prelude))
            assert.match(selector.trim(), owner, '--' + name + ' is read only by the element ui-motion writes it on');
}
console.log('PASS B1034: no unindexable rightmost :is()/:where() lists; per-frame motion values restyle only their own element.');
