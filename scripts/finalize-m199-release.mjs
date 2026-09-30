import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

const root = process.cwd();
const read = file => fs.readFileSync(path.join(root, file), 'utf8');
const write = (file, content) => fs.writeFileSync(path.join(root, file), content);
const hashFile = file => crypto.createHash('sha256').update(fs.readFileSync(path.join(root, file))).digest('hex');
const hashText = text => crypto.createHash('sha256').update(text).digest('hex');

const milestone = 'M199';
const appVersion = '4.0.0';
const build = 789;
const cache = 'pursuit-iron-production-v4-0-0-m199';
const uiMilestone = 'Pursuit Iron 4.0 Production Release · M199';

function replaceRequired(file, pattern, replacement, label) {
  const before = read(file);
  if (!pattern.test(before)) throw new Error(`${label} pattern not found in ${file}`);
  pattern.lastIndex = 0;
  write(file, before.replace(pattern, replacement));
}

replaceRequired('modules/App.js', /const __APP_VERSION__='[^']+'; const __BUILD__='[^']+';/,
  `const __APP_VERSION__='${appVersion}'; const __BUILD__='${build}';`, 'app version/build');
replaceRequired('index.html', /build:'\d+'/g, `build:'${build}'`, 'startup diagnostic build');
replaceRequired('index.html', /build='\d+'/g, `build='${build}'`, 'boot-health build');
replaceRequired('sw.js', /\/\* Pursuit Iron 4\.0 production release[^\n]*\*\//,
  '/* Pursuit Iron 4.0 production release — Engine 0.64.0 with M199 responsiveness and loading pass. */', 'service-worker milestone comment');
replaceRequired('sw.js', /const CACHE="pursuit-iron-production-[^"]+";/, `const CACHE="${cache}";`, 'service-worker cache');

const changelog = read('CHANGELOG.md');
if (!changelog.includes('## M199 — Responsiveness and loading clarity')) {
  write('CHANGELOG.md', `## M199 — Responsiveness and loading clarity\n\n- Split selection no longer runs a full engine generation for every visible option; hard equipment contracts stay instant and the selected session length receives the audited feasibility check.\n- Expensive session-length validation now paints a clear Checking state first and debounces abandoned taps.\n- User-facing program/cycle creation skips observational shadow-engine passes unless research or a reviewed promotion is actually enabled.\n- Workout elapsed time updates only its clock text instead of forcing the full workout tree to rerender every second.\n- Autosave reuses its serialized fallback snapshot instead of reparsing the entire store after every save.\n- Cold start now explicitly says Pursuit Iron is loading, with the existing slow-start and recovery safeguards retained.\n\n${changelog}`);
}

const profile = JSON.parse(read('BUILD_PROFILE.json'));
profile.milestone = milestone;
profile.source = 'M198 production baseline + M199 responsiveness/loading optimization pass';
profile.engine = '0.64.0 coach-quality engine with M198 creation contract and M199 runtime responsiveness optimizations';
profile.cache = cache;
profile.uiMilestone = uiMilestone;

function walkJs(dir) {
  const out = [];
  for (const entry of fs.readdirSync(path.join(root, dir), { withFileTypes: true })) {
    const rel = path.posix.join(dir, entry.name);
    if (entry.isDirectory()) out.push(...walkJs(rel));
    else if (entry.name.endsWith('.js')) out.push(rel);
  }
  return out;
}

const nextRuntimeFiles = walkJs('modules/next-engine').sort();
const shadowRuntimeFiles = walkJs('modules/shadow-engine').sort();
const vendorRuntimeFiles = walkJs('vendor').sort();
const runtimeFiles = [...nextRuntimeFiles, ...shadowRuntimeFiles, ...vendorRuntimeFiles].sort();
profile.nextRuntimeModules = nextRuntimeFiles.length;
profile.shadowRuntimeModules = shadowRuntimeFiles.length;
write('BUILD_PROFILE.json', `${JSON.stringify(profile, null, 2)}\n`);

const manifest = JSON.parse(read('RELEASE_MANIFEST.json'));
manifest.milestone = milestone;
manifest.appVersion = appVersion;
manifest.build = build;
manifest.engineVersion = '0.64.0';
manifest.cache = cache;
manifest.uiMilestone = uiMilestone;
manifest.candidateStatus = 'ci_verified_device_test_pending';
manifest.localCandidate = {
  ...(manifest.localCandidate ?? {}),
  name: uiMilestone,
  base: `M199 / app ${appVersion} build ${build} / Engine 0.64.0`,
  validation: 'M198 creation-contract regression plus M199 responsiveness/loading contract, throttled Chrome cold-start probe, release integrity, and PWA lifecycle certification.'
};
manifest.runtimeFiles = Object.fromEntries(runtimeFiles.map(file => [file, hashFile(file)]));
const aggregateBlob = runtimeFiles.map(file => `${file}:${manifest.runtimeFiles[file]}\n`).join('');
manifest.runtimeAggregate = hashText(aggregateBlob);
const uiFiles = ['modules/App.js', 'index.html', 'sw.js', 'BUILD_PROFILE.json', 'CHANGELOG.md', 'app.css'];
manifest.uiFiles = Object.fromEntries(uiFiles.map(file => [file, hashFile(file)]));
write('RELEASE_MANIFEST.json', `${JSON.stringify(manifest, null, 2)}\n`);

console.log(`Finalized ${milestone}: app ${appVersion} build ${build} / Engine ${manifest.engineVersion}.`);
console.log(`Hashed ${runtimeFiles.length} runtime JS files and ${uiFiles.length} release-facing UI files.`);
