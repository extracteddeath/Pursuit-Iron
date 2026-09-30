import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

const root = process.cwd();
const read = file => fs.readFileSync(path.join(root, file), 'utf8');
const write = (file, content) => fs.writeFileSync(path.join(root, file), content);
const hashFile = file => crypto.createHash('sha256').update(fs.readFileSync(path.join(root, file))).digest('hex');
const hashText = text => crypto.createHash('sha256').update(text).digest('hex');

const milestone = 'M196';
const cache = 'pursuit-iron-production-v4-0-0-m196';
const uiMilestone = 'Pursuit Iron 4.0 Production Release · M196';

function replaceRequired(file, pattern, replacement, label) {
  const before = read(file);
  if (!pattern.test(before)) throw new Error(`${label} pattern not found in ${file}`);
  pattern.lastIndex = 0;
  write(file, before.replace(pattern, replacement));
}

// Rotate the cache only as part of the finalized release identity. The adapter's query-string base
// import is served offline by the service worker's ignoreSearch lookup, so the query URL itself does
// not belong in the precache manifest.
replaceRequired(
  'sw.js',
  /const CACHE="pursuit-iron-production-[^"]+";/,
  `const CACHE="${cache}";`,
  'service-worker cache'
);

const profile = JSON.parse(read('BUILD_PROFILE.json'));
profile.milestone = milestone;
profile.source = 'M195 production baseline + M196 session-capacity and split-picker stability patch';
profile.engine = '0.64.0 coach-quality engine with M196 soft session-capacity fallback and stable split feasibility UX';
profile.cache = cache;
profile.uiMilestone = uiMilestone;
write('BUILD_PROFILE.json', `${JSON.stringify(profile, null, 2)}\n`);

const manifest = JSON.parse(read('RELEASE_MANIFEST.json'));
manifest.milestone = milestone;
manifest.cache = cache;
manifest.uiMilestone = uiMilestone;
manifest.candidateStatus = 'ci_verified_device_test_pending';
manifest.localCandidate = {
  ...(manifest.localCandidate ?? {}),
  name: 'Pursuit Iron 4.0 Production Release · M196',
  base: `M196 / app ${manifest.appVersion} build ${manifest.build} / Engine ${manifest.engineVersion}`,
  validation: 'M195 production baseline plus M196 soft session-capacity fallback, split-picker stability, offline adapter routing, focused regression coverage, release integrity, and PWA lifecycle certification.'
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

console.log(`Finalized ${milestone}: app ${manifest.appVersion} build ${manifest.build} / Engine ${manifest.engineVersion}.`);
console.log(`Hashed ${runtimeFiles.length} runtime JS files and ${uiFiles.length} release-facing UI files.`);
