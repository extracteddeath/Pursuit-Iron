import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { parse } from '@babel/parser';
import traverse from '@babel/traverse';

// Export current production computation, never a hand-maintained engine fork.
const root = path.resolve(new URL('../', import.meta.url).pathname);
const out = path.resolve(process.argv[2] || path.join(root, '../pursuit-iron-engine'));
if (out === root || root.startsWith(out + path.sep) || out.startsWith(root + path.sep))
  throw new Error('Choose an output directory outside the application repository.');
const source = fs.readFileSync(path.join(root, 'modules/App.js'), 'utf8');
const ast = parse(source, { sourceType: 'module' });
let program;
traverse(ast, { Program(p) { program = p; } });
const bindings = program.scope.bindings;
const records = new Map();
const statements = ast.program.body.filter(n => !['VariableDeclaration','ImportDeclaration','FunctionDeclaration','ClassDeclaration','ExportNamedDeclaration','ExportDefaultDeclaration'].includes(n.type));
for (const n of statements) {
  const text = source.slice(n.start,n.end);
  if (text === 'setShellEquipmentExpander(expandEquipment);') continue;
  if (n.type === 'IfStatement' && /^if \(typeof __BUILD__ === "undefined"/.test(text)) continue;
  throw new Error(`Unreviewed production initializer at ${n.start}: ${text.slice(0,100)}`);
}
for (const [name, binding] of Object.entries(bindings)) {
  const p = binding.path;
  if (binding.kind === 'module') {
    const node = p.parentPath.node;
    records.set(name, { name, node, import: node.source.value, dependencies: new Set() });
    continue;
  }
  const dependencies = new Set();
  p.traverse({ ReferencedIdentifier(ref) {
    const b = ref.scope.getBinding(ref.node.name);
    if (b && b === bindings[ref.node.name] && ref.node.name !== name) dependencies.add(ref.node.name);
  }});
  records.set(name, { name, node: p.node, kind: p.isVariableDeclarator() ? p.parentPath.node.kind : null, dependencies });
}
const excluded = new Set([...records.values()].filter(r => r.import && !r.import.startsWith('./')).map(r => r.name));
let changed = true;
while (changed) {
  changed = false;
  for (const r of records.values()) if (!excluded.has(r.name) && [...r.dependencies].some(n => excluded.has(n))) {
    excluded.add(r.name); changed = true;
  }
}
const selected = [...records.values()].filter(r => !excluded.has(r.name));
const chunks = [];
const imports = new Map();
for (const r of selected) {
  if (r.import) {
    if (!imports.has(r.node.start)) imports.set(r.node.start, source.slice(r.node.start, r.node.end));
  } else {
    chunks.push({ start: r.node.start, text: r.kind ? `${r.kind} ${source.slice(r.node.start, r.node.end)};` : source.slice(r.node.start, r.node.end) });
  }
}
const importedNames = selected.filter(r => r.import).map(r => r.name);
const localNames = selected.filter(r => !r.import).map(r => r.name);
const shell = '// Generated verbatim from production top-level declarations. No React or UI runtime.\n'
  + [...imports.values()].join('\n') + '\n'
  + chunks.sort((a,b) => a.start-b.start).map(r => r.text).join('\n\n')
  + '\nsetShellEquipmentExpander(expandEquipment);\n'
  + `export { ${[...localNames, ...importedNames].sort().join(', ')} };\n`;
fs.mkdirSync(out, { recursive: true });
const write = (file, bytes) => { fs.mkdirSync(path.dirname(path.join(out,file)),{recursive:true}); fs.writeFileSync(path.join(out,file),bytes); };
const copy = file => write(file, fs.readFileSync(path.join(root,file)));
for (const file of fs.readdirSync(path.join(root,'modules/next-engine'))) if (file.endsWith('.js')) copy('modules/next-engine/' + file);
for (const file of ['modules/program-duration.js','modules/mobile-lifecycle.js','modules/legacy-research-data.js']) copy(file);
write('modules/engine-shell.js', shell);
// Keep the full source boundary as evidence: nested UI event handlers must not silently vanish
// from an audit. It is never imported by the independent engine runtime.
write('reference/App.production.js', source);
copy('ENGINE_ARCHITECTURE.md'); copy('RELEASE_MANIFEST.json'); copy('BUILD_PROFILE.json');
for (const file of ['M224_ENGINE_AUDIT_REPAIR.md', 'M225_ENGINE_HISTORY_REPORT.md'])
  if (fs.existsSync(path.join(root,file))) copy(file);
write('index.js', "export * as shell from './modules/engine-shell.js';\nexport * from './modules/next-engine/config.js';\nexport * from './modules/next-engine/generate.js';\nexport * from './modules/next-engine/app-shell-adapter.js';\nexport * from './modules/next-engine/cycle-runtime-adapter.js';\nexport * from './modules/next-engine/performance.js';\nexport * from './modules/next-engine/workout-runtime.js';\nexport * from './modules/next-engine/history-contract.js';\nexport * from './modules/next-engine/percentage-protocols.js';\n");
const manifest = JSON.parse(fs.readFileSync(path.join(root,'RELEASE_MANIFEST.json')));
const coverage = {
  schemaVersion: 1, release: { milestone: manifest.milestone, build: manifest.build, engineVersion: manifest.engineVersion },
  sourceCommit: execFileSync('git',['rev-parse','HEAD'],{cwd:root,encoding:'utf8'}).trim(),
  sourceWorkingTree: execFileSync('git',['status','--porcelain'],{cwd:root,encoding:'utf8'}).trim() ? 'modified' : 'clean',
  appSourceSha256: crypto.createHash('sha256').update(source).digest('hex'),
  topLevelStatements: statements.map(n=>({start:n.start,end:n.end,source:source.slice(n.start,n.end),handling:n.type==='IfStatement'?'development-only guard; false with the production build constant':'production equipment registration retained'})),
  engineModules: fs.readdirSync(path.join(root,'modules/next-engine')).filter(f=>f.endsWith('.js')).sort(),
  includedDeclarations: selected.map(r => ({ name:r.name, start:r.node.start, end:r.node.end, dependencies:[...r.dependencies].sort(), kind:r.import ? 'import' : r.kind || r.node.type })),
  excludedDeclarations: [...excluded].sort().map(name=>({name, reason:records.get(name).import ? 'UI framework import' : 'transitively depends on UI framework', start:records.get(name).node.start,end:records.get(name).node.end})),
  boundary: 'All engine modules and every top-level declaration independent of UI-framework bindings are included. The exact complete App.js is retained for nested UI event-handler and boundary audits. UI-dependent declarations are indexed rather than represented as headless code.'
};
write('coverage.json', JSON.stringify(coverage,null,2)+'\n');
write('package.json', JSON.stringify({name:'pursuit-iron-engine',version:manifest.engineVersion,private:true,type:'module',engines:{node:'>=22'},exports:{'.':'./index.js','./shell':'./modules/engine-shell.js','./engine/*':'./modules/next-engine/*'},scripts:{test:'node scripts/audit.mjs',example:'node examples/generate.mjs',verify:'node scripts/verify-snapshot.mjs','verify:published':'node scripts/verify-snapshot.mjs --published-release'}},null,2)+'\n');
// Existing semantic suites run against extracted computation. UI source-text gates remain
// in the reference integration set instead of weakening their original assertions.
const suites=[];
const referenceSuites=[];
for (const file of fs.readdirSync(path.join(root,'verification'))) {
  if (!file.endsWith('.mjs')) continue;
  const text=fs.readFileSync(path.join(root,'verification',file),'utf8');
  write('reference/verification/'+file,text);
  if (/browser|import-loader/.test(file) || /puppeteer|readFileSync|\bfs\./.test(text)) {referenceSuites.push(file);continue;}
  const exported=[...text.matchAll(/import\s*\{([^}]+)\}\s*from\s*['"]\.\.\/modules\/App\.js['"]/gs)]
    .flatMap(m=>m[1].split(',').map(s=>s.trim().split(/\s+as\s+/)[0]));
  if (exported.some(n=>excluded.has(n))) {referenceSuites.push(file);continue;}
  write('verification/'+file,text.replaceAll('../modules/App.js','../modules/engine-shell.js'));
  if (/-(test|matrix)\.mjs$/.test(file) || ['programs-test.mjs','m198-creation-audit.mjs'].includes(file)) suites.push(file);
}
write('scripts/audit.mjs', `import {execFileSync} from 'node:child_process';\nimport {fileURLToPath} from 'node:url';\nconst root=fileURLToPath(new URL('../',import.meta.url));\nconst suites=${JSON.stringify(suites.sort(),null,2)};\nexecFileSync(process.execPath,['scripts/verify-snapshot.mjs','--runtime-only'],{cwd:root,stdio:'inherit'});\nfor(const suite of suites)execFileSync(process.execPath,['verification/'+suite],{cwd:root,stdio:'inherit'});\nconsole.log('PASS isolated engine: '+suites.length+' semantic suites; no browser, React, or app mount.');\n`);
copyTo('engine-lab/verify-snapshot.mjs','scripts/verify-snapshot.mjs');
copyTo('engine-lab/example.mjs','examples/generate.mjs');
copyTo('engine-lab/PACKAGE_README.md','README.md');
function copyTo(from,to){write(to,fs.readFileSync(path.join(root,from)));}
write('reference/integration-suites.json', JSON.stringify(referenceSuites.sort(),null,2)+'\n');
// Hash all distributed bytes. The generated shell and copied production modules are independently
// certified; editing intentionally invalidates this snapshot until re-exported/reviewed.
const files={};
function walk(dir=''){for(const ent of fs.readdirSync(path.join(out,dir),{withFileTypes:true})){
  const f=path.posix.join(dir,ent.name);if(ent.isDirectory())walk(f);else if(f!=='snapshot.json')files[f]=crypto.createHash('sha256').update(fs.readFileSync(path.join(out,f))).digest('hex');
}}
walk();write('snapshot.json',JSON.stringify({schemaVersion:1,files},null,2)+'\n');
console.log(JSON.stringify({output:out,engineModules:coverage.engineModules.length,headlessDeclarations:selected.length,excludedUIBindings:excluded.size,semanticSuites:suites.length}));
