import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

const root = process.cwd();
const read = file => fs.readFileSync(path.join(root, file), 'utf8');
const write = (file, content) => fs.writeFileSync(path.join(root, file), content);
const hashFile = file => crypto.createHash('sha256').update(fs.readFileSync(path.join(root, file))).digest('hex');
const hashText = text => crypto.createHash('sha256').update(text).digest('hex');

const milestone = 'M197';
const appVersion = '4.0.0';
const build = 788;
const cache = 'pursuit-iron-production-v4-0-0-m197';
const uiMilestone = 'Pursuit Iron 4.0 Production Release · M197';

function replaceRequired(file, pattern, replacement, label) {
  const before = read(file);
  if (!pattern.test(before)) throw new Error(`${label} pattern not found in ${file}`);
  pattern.lastIndex = 0;
  write(file, before.replace(pattern, replacement));
}

replaceRequired(
  'modules/App.js',
  /const __APP_VERSION__='[^']+'; const __BUILD__='[^']+';/,
  `const __APP_VERSION__='${appVersion}'; const __BUILD__='${build}';`,
  'app version/build'
);
replaceRequired('index.html', /build:'\d+'/g, `build:'${build}'`, 'startup diagnostic build');
replaceRequired('index.html', /build='\d+'/g, `build='${build}'`, 'boot-health build');
replaceRequired(
  'sw.js',
  /\/\* Pursuit Iron 4\.0 production release[^\n]*\*\//,
  '/* Pursuit Iron 4.0 production release — Engine 0.64.0 with M197 monotonic session-capacity fix. */',
  'service-worker milestone comment'
);
replaceRequired(
  'sw.js',
  /const CACHE="pursuit-iron-production-[^"]+";/,
  `const CACHE="${cache}";`,
  'service-worker cache'
);

const changelog = read('CHANGELOG.md');
if (!changelog.includes('## M197 — Monotonic session capacity')) {
  write('CHANGELOG.md', `## M197 — Monotonic session capacity\n\n- Longer session bands no longer become impossible just because Pursuit cannot justify filling their lower time edge.\n- 60–90, 90–120, and 120+ preserve their full upper capacity while falling back to a proven shorter productive floor when needed.\n- Added an end-to-end Full Body regression so a buildable 40–60 setup must remain buildable when the athlete offers more time.\n\n${changelog}`);
}

const profile = JSON.parse(read('BUILD_PROFILE.json'));
profile.milestone = milestone;
profile.source = 'M196 production baseline + M197 monotonic session-capacity correction';
profile.engine = '0.64.0 coach-quality engine with M197 soft lower-time fallback and stable split feasibility UX';
profile.cache = cache;
profile.uiMilestone = uiMilestone;
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
  base: `M197 / app ${appVersion} build ${build} / Engine 0.64.0`,
  validation: 'M196 production baseline plus M197 monotonic long-band capacity fallback, end-to-end Full Body buildability regression, release integrity, and PWA lifecycle certification.'
};

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

manifest.runtimeFiles = Object.fromEntries(runtimeFiles.map(file => [file, hashFile(file)]));
const aggregateBlob = runtimeFiles.map(file => `${file}:${manifest.runtimeFiles[file]}\n`).join('');
manifest.runtimeAggregate = hashText(aggregateBlob);

const uiFiles = ['modules/App.js', 'index.html', 'sw.js', 'BUILD_PROFILE.json', 'CHANGELOG.md', 'app.css'];
manifest.uiFiles = Object.fromEntries(uiFiles.map(file => [file, hashFile(file)]));
write('RELEASE_MANIFEST.json', `${JSON.stringify(manifest, null, 2)}\n`);

console.log(`Finalized ${milestone}: app ${appVersion} build ${build} / Engine ${manifest.engineVersion}.`);
console.log(`Hashed ${runtimeFiles.length} runtime JS files and ${uiFiles.length} release-facing UI files.`);
