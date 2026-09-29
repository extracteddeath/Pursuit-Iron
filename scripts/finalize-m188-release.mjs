import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

const root=process.cwd();
const read=file=>fs.readFileSync(path.join(root,file),'utf8');
const write=(file,content)=>fs.writeFileSync(path.join(root,file),content);
const hashFile=file=>crypto.createHash('sha256').update(fs.readFileSync(path.join(root,file))).digest('hex');
const hashText=text=>crypto.createHash('sha256').update(text).digest('hex');

function replaceRequired(file,pattern,replacement,label){
  const before=read(file);
  if(!pattern.test(before)) throw new Error(`${label} pattern not found in ${file}`);
  pattern.lastIndex=0;
  const after=before.replace(pattern,replacement);
  write(file,after);
}

// Engine identity: M188 includes a real runtime change in dose reconciliation, so it receives
// a new engine patch identity instead of shipping the M188 behavior under the M187 version.
replaceRequired(
  'modules/next-engine/config.js',
  /export const ENGINE_VERSION = '[^']+';/,
  "export const ENGINE_VERSION = '0.63.4';",
  'engine version'
);

// App identity follows the release manifest. This is the canonical marker already consumed by
// scripts/verify-release.mjs, so no parallel versioning scheme is introduced.
replaceRequired(
  'modules/App.js',
  /const __APP_VERSION__='[^']+'; const __BUILD__='[^']+';/,
  "const __APP_VERSION__='3.230.0'; const __BUILD__='786';",
  'app version/build'
);

// Keep startup diagnostics on the same build as the shipped app. These are intentionally narrow
// replacements so unrelated historical milestone numbers remain untouched.
replaceRequired('index.html',/build:'\d+'/g,"build:'786'",'startup diagnostic build');
replaceRequired('index.html',/build='\d+'/g,"build='786'",'boot-health build');

// Rotate the PWA cache for the production candidate. The lifecycle stays identical to M187;
// only the cache identity changes so devices cannot keep serving stale M187 runtime files.
replaceRequired(
  'sw.js',
  /\/\* M187[^\n]*\*\//,
  '/* M188 production torture + certification — Engine 0.63.4, carrying forward M187 premium UX. */',
  'service-worker milestone comment'
);
replaceRequired(
  'sw.js',
  /const CACHE="pursuit-iron-production-[^"]+";/,
  'const CACHE="pursuit-iron-production-m188-torture-certification";',
  'service-worker cache'
);

const profile=JSON.parse(read('BUILD_PROFILE.json'));
profile.milestone='M188';
profile.source='M187 premium UX + M188 production torture certification';
profile.engine='0.63.4 coach-quality oracle plus M181-M188 dose, evidence, phase specialization, explainability, UX, and production-torture contracts';
profile.cache='pursuit-iron-production-m188-torture-certification';
profile.uiMilestone='M188 Production Torture + Certification';
write('BUILD_PROFILE.json',`${JSON.stringify(profile,null,2)}\n`);

const manifest=JSON.parse(read('RELEASE_MANIFEST.json'));
manifest.milestone='M188';
manifest.appVersion='3.230.0';
manifest.build=786;
manifest.engineVersion='0.63.4';
manifest.cache='pursuit-iron-production-m188-torture-certification';
manifest.uiMilestone='M188 Production Torture + Certification';
manifest.candidateStatus='ci_verified_device_test_pending';
manifest.localCandidate={
  ...(manifest.localCandidate??{}),
  name:'M188 Production Torture + Certification',
  base:'M188 / app 3.230.0 build 786 / Engine 0.63.4',
  validation:'M181-M188 engine, longitudinal, phase, explainability, premium UX, production-torture, exhaustive runtime hashing, offline-shell completeness, and release verification.'
};

function walkJs(dir){
  const out=[];
  for(const entry of fs.readdirSync(path.join(root,dir),{withFileTypes:true})){
    const rel=path.posix.join(dir,entry.name);
    if(entry.isDirectory()) out.push(...walkJs(rel));
    else if(entry.name.endsWith('.js')) out.push(rel);
  }
  return out;
}

const runtimeFiles=[...walkJs('modules/next-engine'),...walkJs('modules/shadow-engine'),...walkJs('vendor')].sort();
manifest.runtimeFiles=Object.fromEntries(runtimeFiles.map(file=>[file,hashFile(file)]));
const aggregateBlob=runtimeFiles.map(file=>`${file}:${manifest.runtimeFiles[file]}\n`).join('');
manifest.runtimeAggregate=hashText(aggregateBlob);

const uiFiles=['modules/App.js','index.html','sw.js','BUILD_PROFILE.json','CHANGELOG.md','app.css'];
manifest.uiFiles=Object.fromEntries(uiFiles.map(file=>[file,hashFile(file)]));

write('RELEASE_MANIFEST.json',`${JSON.stringify(manifest,null,2)}\n`);

console.log(`Finalized M188 release identity: app ${manifest.appVersion} build ${manifest.build} / Engine ${manifest.engineVersion}.`);
console.log(`Hashed ${runtimeFiles.length} runtime JS files and ${uiFiles.length} release-facing UI files.`);
