import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { releaseTests, verifyContractRegistry } from '../verification/contract-registry.mjs';

const root = process.cwd();
const read = p => fs.readFileSync(path.join(root, p), 'utf8');
const bytes = p => fs.readFileSync(path.join(root, p));
const sha = b => crypto.createHash('sha256').update(b).digest('hex');
const fail = msg => { console.error(`RELEASE VERIFY FAILED: ${msg}`); process.exit(1); };

const registry = verifyContractRegistry(root);
const manifest = JSON.parse(read('RELEASE_MANIFEST.json'));
const profile = JSON.parse(read('BUILD_PROFILE.json'));
const sw = read('sw.js');
const index = read('index.html');

const shellMatch = sw.match(/const SHELL=\[(.*?)\];/s);
if (!shellMatch) fail('service worker SHELL list not found');
const shell = [...shellMatch[1].matchAll(/"(\.\/[^\"]+)"/g)].map(m => m[1]);
const shellSet = new Set(shell);
if (shellSet.size !== shell.length) fail('service worker SHELL contains duplicate entries');
const missing = shell.filter(p => p !== './' && !fs.existsSync(path.join(root, p.slice(2))));
if (missing.length) fail(`missing precache files: ${missing.join(', ')}`);

if (!sw.includes(`const CACHE="${profile.cache}"`)) fail('BUILD_PROFILE cache does not match sw.js');
if (profile.milestone !== manifest.milestone) fail('BUILD_PROFILE milestone does not match manifest');
if (profile.uiMilestone !== manifest.uiMilestone) fail('BUILD_PROFILE UI milestone does not match manifest');
if (profile.cache !== manifest.cache) fail('BUILD_PROFILE cache does not match manifest');
if (!index.includes('<script type="module" src="./modules/main.js"></script>')) fail('index.html does not load canonical modules/main.js');
if (!index.includes('rel="manifest" href="./manifest.webmanifest"')) fail('index.html manifest link missing');
if (!index.includes('serviceWorker.register("./sw.js"')) fail('index.html service-worker registration missing');

if (fs.existsSync(path.join(root, 'modules/shadow-engine'))) fail('retired research runtime must not ship');
if (fs.existsSync(path.join(root, 'modules/next-engine/app-shell-adapter-capacity.js'))) fail('duplicate shell adapter must not ship');

const app = read('modules/App.js');
if (!app.includes(`const __APP_VERSION__='${manifest.appVersion}'; const __BUILD__='${manifest.build}';`))
    fail('App version/build mismatch');
const config = read('modules/next-engine/config.js');
if (!config.includes(`ENGINE_VERSION = '${manifest.engineVersion}'`))
    fail('engine version mismatch');

for (const [file, expected] of Object.entries(manifest.runtimeFiles ?? {})) {
    const full = path.join(root, file);
    if (!fs.existsSync(full)) fail(`manifest runtime file missing: ${file}`);
    if (sha(bytes(file)) !== expected) fail(`runtime hash mismatch: ${file}`);
    if (!shellSet.has('./' + file)) fail(`runtime file is not available offline: ${file}`);
}
const aggregateBlob = Object.keys(manifest.runtimeFiles ?? {}).sort()
    .map(file => `${file}:${manifest.runtimeFiles[file]}\n`).join('');
if (sha(Buffer.from(aggregateBlob)) !== manifest.runtimeAggregate) fail('runtime aggregate mismatch');

for (const [file, expected] of Object.entries(manifest.uiFiles ?? {})) {
    if (!fs.existsSync(path.join(root, file))) fail(`manifest UI file missing: ${file}`);
    if (sha(bytes(file)) !== expected) fail(`UI hash mismatch: ${file}`);
    if (file !== 'sw.js' && !shellSet.has('./' + file)) fail(`UI file is not available offline: ${file}`);
}

const jsFiles = [];
const walk = p => {
    for (const ent of fs.readdirSync(p, { withFileTypes: true })) {
        const full = path.join(p, ent.name);
        if (ent.isDirectory()) walk(full);
        else if (ent.name.endsWith('.js')) jsFiles.push(full);
    }
};
walk(path.join(root, 'modules'));
for (const file of jsFiles)
    execFileSync(process.execPath, ['--check', file], { stdio: 'ignore' });
execFileSync(process.execPath, ['--check', path.join(root, 'sw.js')], { stdio: 'ignore' });

for (const file of releaseTests)
    execFileSync(process.execPath, [path.join('verification', file)], { stdio: 'inherit', cwd: root });

console.log(
    `Release integrity OK: ${shell.length} unique precache entries; ${jsFiles.length} production JS files parse; `
    + `${Object.keys(manifest.runtimeFiles ?? {}).length} runtime hashes and `
    + `${Object.keys(manifest.uiFiles ?? {}).length} UI hashes are certified.`
);
console.log(
    `Verification ownership: ${registry.contractCount} source contracts, ${registry.browserCount} browser contracts, `
    + `${registry.releaseCount} release-specific contract.`
);
console.log(`Cache: ${profile.cache}`);
console.log(`Milestone: ${manifest.milestone} / app ${manifest.appVersion} build ${manifest.build} / engine ${manifest.engineVersion}`);
