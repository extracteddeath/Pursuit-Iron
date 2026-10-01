import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { execFileSync } from 'node:child_process';

const root = process.cwd();
const read = p => fs.readFileSync(path.join(root,p),'utf8');
const bytes = p => fs.readFileSync(path.join(root,p));
const sha = b => crypto.createHash('sha256').update(b).digest('hex');
const fail = msg => { console.error(`RELEASE VERIFY FAILED: ${msg}`); process.exit(1); };

const manifest = JSON.parse(read('RELEASE_MANIFEST.json'));
const profile = JSON.parse(read('BUILD_PROFILE.json'));
const sw = read('sw.js');
const shellMatch = sw.match(/const SHELL=\[(.*?)\];/s);
if (!shellMatch) fail('service worker SHELL list not found');
const shell = [...shellMatch[1].matchAll(/"(\.\/[^\"]+)"/g)].map(m=>m[1]);
const missing = shell.filter(p => p !== './' && !fs.existsSync(path.join(root,p.slice(2))));
if (missing.length) fail(`missing precache files: ${missing.join(', ')}`);
if (!sw.includes(`const CACHE="${profile.cache}"`)) fail('BUILD_PROFILE cache does not match sw.js');
if (profile.milestone !== manifest.milestone) fail('BUILD_PROFILE milestone does not match manifest');
if (profile.uiMilestone !== manifest.uiMilestone) fail('BUILD_PROFILE UI milestone does not match manifest');
if (profile.cache !== manifest.cache) fail('BUILD_PROFILE cache does not match manifest');

if (!fs.existsSync(path.join(root,'verification/m178-pwa-update-browser-test.mjs'))) fail('missing M178 PWA browser lifecycle gate');
if (!fs.existsSync(path.join(root,'verification/coach-quality-oracle.mjs'))) fail('missing coach quality verification oracle');
if (!fs.existsSync(path.join(root,'verification/m180-coach-quality-matrix.mjs'))) fail('missing M180 pass-only coach-quality matrix');

const app = read('modules/App.js');
if (fs.existsSync(path.join(root, 'modules/shadow-engine'))) fail('retired research runtime must not ship');
if (fs.existsSync(path.join(root, 'modules/next-engine/app-shell-adapter-capacity.js'))) fail('duplicate shell adapter must not ship');
for (const marker of ['Turn into training cycle','convertProgramToNextCycleForShell','onConvertCycle: beginCycleConversion','Remove saved plan','Restore to library',
  'homeProgramGroups','homePhaseIdentity','data-program-group','This block starts when you finish the current one','action: "decrease_load"',
  'Correct workout log','normalizeEditedHistoryEntry','perfAfterHistoryReplace','const setHistory = useMemo(() => stampedSetter(setHistoryRaw), [])','const [lo, hi] = cellRepRange(cell, program, ex, slot === day?.primaryIndex)',
  'legacyCustomSetCount(program, day, ex, slotIndex, weekIndex, o.sets, base.sets)','cyclePhaseLabel','cycleConfigForStandaloneProgram','mergeStandaloneIntoGeneratedCycle','advanceLegacyFirstCycleBlock','historyVolumeIn(h, unit)','weeklyRecap(history, unit)','Keep the compact LAST reference to load × reps only','map(weeks => weeks?.[1] ?? weeks?.["1"])']) {
  if (!app.includes(marker)) fail(`missing App marker: ${marker}`);
}
if (!app.includes(`const __APP_VERSION__='${manifest.appVersion}'; const __BUILD__='${manifest.build}';`)) fail('App version/build mismatch');
const perf = read('modules/next-engine/performance.js');
for (const marker of ['belowRangeLoadCorrection','Holding the heaviest logged weight would repeat an off-target load',"action: 'decrease_load'"])
  if (!perf.includes(marker)) fail(`missing progression-safety marker: ${marker}`);
const config = read('modules/next-engine/config.js');
if (!config.includes(`ENGINE_VERSION = '${manifest.engineVersion}'`)) fail('engine version mismatch');

for (const [file,expected] of Object.entries(manifest.runtimeFiles ?? {})) {
  if (!fs.existsSync(path.join(root,file))) fail(`manifest runtime file missing: ${file}`);
  const actual=sha(bytes(file));
  if (actual!==expected) fail(`runtime hash mismatch: ${file}`);
}
const aggregateBlob=Object.keys(manifest.runtimeFiles ?? {}).sort().map(file=>`${file}:${manifest.runtimeFiles[file]}\n`).join('');
if (sha(Buffer.from(aggregateBlob)) !== manifest.runtimeAggregate) fail('runtime aggregate mismatch');
for (const [file,expected] of Object.entries(manifest.uiFiles ?? {})) {
  if (sha(bytes(file))!==expected) fail(`UI hash mismatch: ${file}`);
}

const jsFiles=[];
const walk = p => { for (const ent of fs.readdirSync(p,{withFileTypes:true})) { const full=path.join(p,ent.name); if(ent.isDirectory()) walk(full); else if(ent.name.endsWith('.js')) jsFiles.push(full); } };
walk(path.join(root,'modules'));
for (const f of jsFiles) execFileSync(process.execPath,['--check',f],{stdio:'ignore'});
execFileSync(process.execPath,['verification/progression-safety-test.mjs'],{stdio:'inherit',cwd:root});
execFileSync(process.execPath,['verification/shell-progression-safety-test.mjs'],{stdio:'inherit',cwd:root});
execFileSync(process.execPath,['--no-warnings','--experimental-loader','./verification/import-loader.mjs','./verification/prescription-integrity-test.mjs'],{stdio:'inherit',cwd:root});
execFileSync(process.execPath,['--no-warnings','--experimental-loader','./verification/import-loader.mjs','./verification/custom-progression-safety-test.mjs'],{stdio:'inherit',cwd:root});
execFileSync(process.execPath,['--no-warnings','--experimental-loader','./verification/import-loader.mjs','./verification/m201-workout-prescription-test.mjs'],{stdio:'inherit',cwd:root});
execFileSync(process.execPath,['--no-warnings','--experimental-loader','./verification/import-loader.mjs','./verification/m204-prescription-ownership-test.mjs'],{stdio:'inherit',cwd:root});
execFileSync(process.execPath,['--no-warnings','--experimental-loader','./verification/import-loader.mjs','./verification/m214-executable-prescription-sync-test.mjs'],{stdio:'inherit',cwd:root});
execFileSync(process.execPath,['--no-warnings','--experimental-loader','./verification/import-loader.mjs','./verification/m215-cycle-duration-test.mjs'],{stdio:'inherit',cwd:root});
execFileSync(process.execPath,['--no-warnings','--experimental-loader','./verification/import-loader.mjs','./verification/m205-engine-cleanup-test.mjs'],{stdio:'inherit',cwd:root});
execFileSync(process.execPath,['--no-warnings','--experimental-loader','./verification/import-loader.mjs','./verification/m167-legacy-dose-cycle-test.mjs'],{stdio:'inherit',cwd:root});
execFileSync(process.execPath,['--no-warnings','--experimental-loader','./verification/import-loader.mjs','./verification/history-edit-integrity-test.mjs'],{stdio:'inherit',cwd:root});
execFileSync(process.execPath,['--no-warnings','--experimental-loader','./verification/import-loader.mjs','./verification/history-volume-integrity-test.mjs'],{stdio:'inherit',cwd:root});
execFileSync(process.execPath,['--no-warnings','--experimental-loader','./verification/import-loader.mjs','./verification/engine-authority-test.mjs'],{stdio:'inherit',cwd:root});
execFileSync(process.execPath,['--no-warnings','--experimental-loader','./verification/import-loader.mjs','./verification/full-engine-import-test.mjs'],{stdio:'inherit',cwd:root});
execFileSync(process.execPath,['--no-warnings','--experimental-loader','./verification/import-loader.mjs','./verification/m170-engine-correctness-test.mjs'],{stdio:'inherit',cwd:root});
execFileSync(process.execPath,['--no-warnings','--experimental-loader','./verification/import-loader.mjs','./verification/m171-transaction-context-test.mjs'],{stdio:'inherit',cwd:root});
execFileSync(process.execPath,['--no-warnings','--experimental-loader','./verification/import-loader.mjs','./verification/m172-actual-strength-baseline-test.mjs'],{stdio:'inherit',cwd:root});
execFileSync(process.execPath,['--no-warnings','--experimental-loader','./verification/import-loader.mjs','./verification/m173-integration-torture-test.mjs'],{stdio:'inherit',cwd:root});
execFileSync(process.execPath,['verification/m173-pwa-resilience-test.mjs'],{stdio:'inherit',cwd:root});
execFileSync(process.execPath,['--no-warnings','--experimental-loader','./verification/import-loader.mjs','./verification/m174-causal-cycle-state-test.mjs'],{stdio:'inherit',cwd:root});
execFileSync(process.execPath,['--no-warnings','--experimental-loader','./verification/import-loader.mjs','./verification/m175-longitudinal-adaptation-memory-test.mjs'],{stdio:'inherit',cwd:root});
execFileSync(process.execPath,['--no-warnings','--experimental-loader','./verification/import-loader.mjs','./verification/m176-workout-restore-hardening-test.mjs'],{stdio:'inherit',cwd:root});
execFileSync(process.execPath,['--no-warnings','--experimental-loader','./verification/import-loader.mjs','./verification/m177-history-merge-integrity-test.mjs'],{stdio:'inherit',cwd:root});
execFileSync(process.execPath,['--no-warnings','--experimental-loader','./verification/import-loader.mjs','./verification/m180-coach-quality-oracle-test.mjs'],{stdio:'inherit',cwd:root});
execFileSync(process.execPath,['--no-warnings','--experimental-loader','./verification/import-loader.mjs','./verification/m180-coach-quality-matrix.mjs'],{stdio:'inherit',cwd:root});
execFileSync(process.execPath,['--no-warnings','--experimental-loader','./verification/import-loader.mjs','./verification/programs-test.mjs'],{stdio:'inherit',cwd:root});
execFileSync(process.execPath,['--no-warnings','--experimental-loader','./verification/import-loader.mjs','./verification/set-display-integrity-test.mjs'],{stdio:'inherit',cwd:root});
execFileSync(process.execPath,['verification/cycle-overview-ui-test.mjs'],{stdio:'inherit',cwd:root});
execFileSync(process.execPath,['verification/swap-scroll-integrity-test.mjs'],{stdio:'inherit',cwd:root});
console.log(`Release integrity OK: ${shell.length} precache entries present; ${jsFiles.length} authored JS files parse.`);
console.log(`Cache: ${profile.cache}`);
console.log(`Milestone: ${manifest.milestone} / app ${manifest.appVersion} build ${manifest.build} / engine ${manifest.engineVersion}`);
