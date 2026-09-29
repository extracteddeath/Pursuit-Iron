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
  pattern.lastIndex=0;
  if(!pattern.test(before)) throw new Error(`${label} pattern not found in ${file}`);
  pattern.lastIndex=0;
  write(file,before.replace(pattern,replacement));
}

const APP_VERSION='4.0.0';
const BUILD=787;
const ENGINE_VERSION='0.64.0';
const MILESTONE='M195';
const UI_MILESTONE='Pursuit Iron 4.0 Production Release';
const CACHE='pursuit-iron-production-v4-0-0';

replaceRequired('modules/next-engine/config.js',/export const ENGINE_VERSION = '[^']+';/,`export const ENGINE_VERSION = '${ENGINE_VERSION}';`,'engine version');
replaceRequired('modules/App.js',/const __APP_VERSION__='[^']+'; const __BUILD__='[^']+';/,`const __APP_VERSION__='${APP_VERSION}'; const __BUILD__='${BUILD}';`,'app version/build');
replaceRequired('index.html',/build:'\d+'/g,`build:'${BUILD}'`,'startup diagnostic build');
replaceRequired('index.html',/build='\d+'/g,`build='${BUILD}'`,'boot-health build');
replaceRequired('sw.js',/\/\* M188[^\n]*\*\//,'/* Pursuit Iron 4.0 production release — Engine 0.64.0 with adaptive progression lifecycle and simulation parity. */','service-worker milestone comment');
replaceRequired('sw.js',/const CACHE="pursuit-iron-production-[^"]+";/,`const CACHE="${CACHE}";`,'service-worker cache');

const changelogMarker='## Pursuit Iron 4.0.0 — M195';
let changelog=read('CHANGELOG.md');
if(!changelog.includes(changelogMarker)){
  const entry=`${changelogMarker} (build ${BUILD} / Engine ${ENGINE_VERSION})\n\n- Promotes the completed M189–M194 adaptive progression architecture to the production release line.\n- Selects progression per exercise from phase, experience, rep structure, loading characteristics, equipment, and real block duration instead of a static program-wide default.\n- Re-evaluates Auto progression from comparable per-lift history while preserving manual choices and preventing method thrashing.\n- Carries progression context correctly through standalone programs, cycle creation, block previews, real block advancement, and Adapt Between Blocks transitions.\n- Adds plain-language progression explainability, including whether Auto kept or changed a method across a block transition.\n- Aligns the longitudinal simulator and weekly progression schedule with the production runtime so torture certification exercises the same rules the shipped app uses.\n- Preserves all M188 production-torture, PWA lifecycle, premium UI, history, progression-safety, and coach-quality contracts.\n- Physical Android installed-PWA certification remains a separate manual device check and is not claimed by CI.\n\n`;
  changelog=entry+changelog;
  write('CHANGELOG.md',changelog);
}

const profile=JSON.parse(read('BUILD_PROFILE.json'));
profile.milestone=MILESTONE;
profile.source='M188 production baseline + M189-M194 adaptive progression, cycle lifecycle, explainability, and simulator parity';
profile.engine='0.64.0 coach-quality engine with adaptive per-exercise progression selection, block-transition evidence, cycle lifecycle parity, and production-aligned simulation';
profile.cache=CACHE;
profile.uiMilestone=UI_MILESTONE;
write('BUILD_PROFILE.json',`${JSON.stringify(profile,null,2)}\n`);

const manifest=JSON.parse(read('RELEASE_MANIFEST.json'));
manifest.milestone=MILESTONE;
manifest.appVersion=APP_VERSION;
manifest.build=BUILD;
manifest.engineVersion=ENGINE_VERSION;
manifest.cache=CACHE;
manifest.uiMilestone=UI_MILESTONE;
manifest.candidateStatus='ci_verified_device_test_pending';
manifest.localCandidate={
  ...(manifest.localCandidate??{}),
  name:UI_MILESTONE,
  base:`${MILESTONE} / app ${APP_VERSION} build ${BUILD} / Engine ${ENGINE_VERSION}`,
  validation:'M188 production baseline plus M189-M194 adaptive progression selection, lifecycle, explainability, cycle context, simulation parity, release integrity, production torture, and browser PWA update certification.'
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

const nextFiles=walkJs('modules/next-engine').sort();
const shadowFiles=walkJs('modules/shadow-engine').sort();
const runtimeFiles=[...nextFiles,...shadowFiles,...walkJs('vendor')].sort();
profile.nextRuntimeModules=nextFiles.length;
profile.shadowRuntimeModules=shadowFiles.length;
write('BUILD_PROFILE.json',`${JSON.stringify(profile,null,2)}\n`);

manifest.runtimeFiles=Object.fromEntries(runtimeFiles.map(file=>[file,hashFile(file)]));
manifest.runtimeAggregate=hashText(runtimeFiles.map(file=>`${file}:${manifest.runtimeFiles[file]}\n`).join(''));
const uiFiles=['modules/App.js','index.html','sw.js','BUILD_PROFILE.json','CHANGELOG.md','app.css'];
manifest.uiFiles=Object.fromEntries(uiFiles.map(file=>[file,hashFile(file)]));
write('RELEASE_MANIFEST.json',`${JSON.stringify(manifest,null,2)}\n`);

console.log(`Finalized ${MILESTONE}: Pursuit Iron ${APP_VERSION} build ${BUILD} / Engine ${ENGINE_VERSION}.`);
console.log(`Hashed ${runtimeFiles.length} runtime JS files and ${uiFiles.length} release-facing UI files.`);
