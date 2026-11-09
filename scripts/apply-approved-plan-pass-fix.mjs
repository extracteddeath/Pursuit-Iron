#!/usr/bin/env node
/**
 * Apply the selective PR #456 Plan-view repeat-pass fix to the large generated App.js.
 *
 * Why a script: the GitHub connector can edit smaller modules, but cannot return
 * the complete compiled App.js for a safe replacement. Run this once in a normal
 * repository checkout, inspect the one-file diff, and commit the resulting App.js.
 *
 * Usage:
 *   node scripts/apply-approved-plan-pass-fix.mjs --check   # non-mutating: 0 if wired, 1 if pending
 *   node scripts/apply-approved-plan-pass-fix.mjs           # apply if source anchors still match
 */
import fs from 'node:fs';

const file = new URL('../modules/App.js', import.meta.url);
let source = fs.readFileSync(file, 'utf8');
const imported = 'historyDayIndex, historyForProgram, earlierPassIds, homeProgramGroups,';
const beforeImport = 'historyDayIndex, historyForProgram, homeProgramGroups,';
const doneBefore = `    const dayDoneThisWeek = useMemo(() => {
        const m = {};
        historyForProgram(history, program).forEach(h => {
            if (!h || h.weekIndex == null)
                return;`;
const doneAfter = `    const dayDoneThisWeek = useMemo(() => {
        const m = {};
        // Only sessions from this pass mark this repeated block's days complete.
        const earlier = earlierPassIds(program, history);
        historyForProgram(history, program).forEach(h => {
            if (!h || h.weekIndex == null || (h.id != null && earlier.has(h.id)))
                return;`;

const includesOnce = (text, needle) => needle && text.split(needle).length === 2;
const already = source.includes(imported) && source.includes(doneAfter);
if (already) {
    console.log('Plan repeating-block completion markers already wired.');
    process.exit(0);
}
if (process.argv.includes('--check')) {
    console.error('Plan repeating-block completion markers are not wired yet.');
    process.exit(1);
}
if (!includesOnce(source, beforeImport) || !includesOnce(source, doneBefore)) {
    throw new Error('Plan source anchors no longer match. Refuse to overwrite; review ProgramView/dayDoneThisWeek and import manually.');
}
source = source.replace(beforeImport, imported).replace(doneBefore, doneAfter);
if (!includesOnce(source, imported) || !includesOnce(source, doneAfter)) {
    throw new Error('Selective Plan patch did not produce the expected unique matches.');
}
fs.writeFileSync(file, source);
console.log('Applied Plan pass markers to modules/App.js. Review its diff and add a browser regression before merging.');
