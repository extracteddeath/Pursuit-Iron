import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { execFileSync } from 'node:child_process';

// M228: export the committed production engine boundary. App.js consumes the same module,
// so the audit package no longer reconstructs production behavior by scraping the UI shell.
const root = path.resolve(new URL('../', import.meta.url).pathname);
const out = path.resolve(process.argv[2] || path.join(root, '../pursuit-iron-engine'));
if (out === root || root.startsWith(out + path.sep) || out.startsWith(root + path.sep))
  throw new Error('Choose an output directory outside the application repository.');
const source = fs.readFileSync(path.join(root, 'modules/App.js'), 'utf8');
const shell = fs.readFileSync(path.join(root, 'modules/engine-shell.js'), 'utf8');
if (!/from\s*['"]\.\/engine-shell\.js['"]/.test(source))
  throw new Error('App.js must consume the canonical engine shell.');
if (/from\s*['"]\.\/next-engine\//.test(source))
  throw new Error('App.js bypasses the canonical engine shell with a direct next-engine import.');
if (/from\s*['"](?:react|react\/|lucide-react)/.test(shell))
  throw new Error('Canonical engine shell must remain independent of React and lucide.');
const exportMatch = shell.match(/export\s*\{([\s\S]*?)\}\s*;\s*$/);
if (!exportMatch) throw new Error('Canonical engine shell export surface not found.');
const shellExports = exportMatch[1].split(',').map(s => s.trim()).filter(Boolean).sort();

fs.mkdirSync(out, { recursive: true });
const write = (file, bytes) => { fs.mkdirSync(path.dirname(path.join(out,file)),{recursive:true}); fs.writeFileSync(path.join(out,file),bytes); };
const copy = file => write(file, fs.readFileSync(path.join(root,file)));
for (const file of fs.readdirSync(path.join(root,'modules/next-engine'))) if (file.endsWith('.js')) copy('modules/next-engine/' + file);
for (const file of ['modules/engine-shell.js','modules/program-duration.js','modules/mobile-lifecycle.js','modules/legacy-research-data.js']) copy(file);
// Keep the complete UI boundary as evidence, but it is no longer engine source.
write('reference/App.production.js', source);
copy('ENGINE_ARCHITECTURE.md'); copy('RELEASE_MANIFEST.json'); copy('BUILD_PROFILE.json');
for (const file of ['M224_ENGINE_AUDIT_REPAIR.md','M225_ENGINE_HISTORY_REPORT.md','M226_ENGINE_BOUNDARY_INTEGRITY.md','M227_ENGINE_REQUEST_CANONICALIZATION.md'])
  if (fs.existsSync(path.join(root,file))) copy(file);
write('index.js', "export * as shell from './modules/engine-shell.js';\nexport * from './modules/next-engine/config.js';\nexport * from './modules/next-engine/generate.js';\nexport * from './modules/next-engine/app-shell-adapter.js';\nexport * from './modules/next-engine/cycle-runtime-adapter.js';\nexport * from './modules/next-engine/performance.js';\nexport * from './modules/next-engine/workout-runtime.js';\nexport * from './modules/next-engine/history-contract.js';\nexport * from './modules/next-engine/percentage-protocols.js';\n");
const manifest = JSON.parse(fs.readFileSync(path.join(root,'RELEASE_MANIFEST.json')));
const coverage = {
  schemaVersion: 2,
  release: { milestone: manifest.milestone, build: manifest.build, engineVersion: manifest.engineVersion },
  sourceCommit: execFileSync('git',['rev-parse','HEAD'],{cwd:root,encoding:'utf8'}).trim(),
  sourceWorkingTree: execFileSync('git',['status','--porcelain'],{cwd:root,encoding:'utf8'}).trim() ? 'modified' : 'clean',
  appSourceSha256: crypto.createHash('sha256').update(source).digest('hex'),
  canonicalShellSha256: crypto.createHash('sha256').update(shell).digest('hex'),
  engineModules: fs.readdirSync(path.join(root,'modules/next-engine')).filter(f=>f.endsWith('.js')).sort(),
  shellExports,
  directAppNextEngineImports: [...source.matchAll(/from\s*['"]\.\/next-engine\//g)].length,
  boundary: 'modules/engine-shell.js is committed production source and the sole App.js engine/domain boundary. The isolated package copies that exact module; App.js is retained only as integration evidence.'
};
write('coverage.json', JSON.stringify(coverage,null,2)+'\n');
write('package.json', JSON.stringify({name:'pursuit-iron-engine',version:manifest.engineVersion,private:true,type:'module',engines:{node:'>=22'},exports:{'.':'./index.js','./shell':'./modules/engine-shell.js','./engine/*':'./modules/next-engine/*'},scripts:{test:'node scripts/audit.mjs',example:'node examples/generate.mjs',verify:'node scripts/verify-snapshot.mjs'}},null,2)+'\n');

const shellSet = new Set(shellExports);
const suites=[]; const referenceSuites=[];
for (const file of fs.readdirSync(path.join(root,'verification'))) {
  if (!file.endsWith('.mjs')) continue;
  const text=fs.readFileSync(path.join(root,'verification',file),'utf8');
  write('reference/verification/'+file,text);
  if (/browser|import-loader/.test(file) || /puppeteer|readFileSync|\bfs\./.test(text)) { referenceSuites.push(file); continue; }
  const imported=[...text.matchAll(/import\s*\{([^}]+)\}\s*from\s*['"]\.\.\/modules\/App\.js['"]/gs)]
    .flatMap(m=>m[1].split(',').map(s=>s.trim().split(/\s+as\s+/)[0]).filter(Boolean));
  if (imported.some(n=>!shellSet.has(n))) { referenceSuites.push(file); continue; }
  write('verification/'+file,text.replaceAll('../modules/App.js','../modules/engine-shell.js'));
  if (/-(test|matrix)\.mjs$/.test(file) || ['programs-test.mjs','m198-creation-audit.mjs'].includes(file)) suites.push(file);
}
write('scripts/audit.mjs', `import {execFileSync} from 'node:child_process';\nimport {fileURLToPath} from 'node:url';\nconst root=fileURLToPath(new URL('../',import.meta.url));\nconst suites=${JSON.stringify(suites.sort(),null,2)};\nexecFileSync(process.execPath,['scripts/verify-snapshot.mjs','--runtime-only'],{cwd:root,stdio:'inherit'});\nfor(const suite of suites)execFileSync(process.execPath,['verification/'+suite],{cwd:root,stdio:'inherit'});\nconsole.log('PASS isolated engine: '+suites.length+' semantic suites; canonical shell copied from production; no browser or React mount.');\n`);
copyTo('engine-lab/verify-snapshot.mjs','scripts/verify-snapshot.mjs');
copyTo('engine-lab/example.mjs','examples/generate.mjs');
copyTo('engine-lab/PACKAGE_README.md','README.md');
function copyTo(from,to){write(to,fs.readFileSync(path.join(root,from)));}
write('reference/integration-suites.json', JSON.stringify(referenceSuites.sort(),null,2)+'\n');

const files={};
function walk(dir=''){for(const ent of fs.readdirSync(path.join(out,dir),{withFileTypes:true})){
  const f=path.posix.join(dir,ent.name); if(ent.isDirectory()) walk(f); else if(f!=='snapshot.json') files[f]=crypto.createHash('sha256').update(fs.readFileSync(path.join(out,f))).digest('hex');
}}
walk(); write('snapshot.json',JSON.stringify({schemaVersion:2,files},null,2)+'\n');
console.log(JSON.stringify({output:out,engineModules:coverage.engineModules.length,canonicalShellExports:shellExports.length,semanticSuites:suites.length}));
