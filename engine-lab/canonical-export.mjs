import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { execFileSync } from 'node:child_process';
import * as domain from '../modules/training-domain.js';
import * as shell from '../modules/engine-shell.js';

const root = path.resolve(new URL('../', import.meta.url).pathname);
const out = path.resolve(process.argv[2] || path.join(root, '../pursuit-iron-engine'));
if (out === root || root.startsWith(out + path.sep) || out.startsWith(root + path.sep))
    throw new Error('Choose an output directory outside the application repository.');
if (fs.existsSync(out) && fs.readdirSync(out).length)
    throw new Error('Choose an empty output directory; stale snapshot files must not survive an export.');
const write = (file, bytes) => {
    fs.mkdirSync(path.dirname(path.join(out, file)), { recursive: true });
    fs.writeFileSync(path.join(out, file), bytes);
};
const copy = (file, target = file) => write(target, fs.readFileSync(path.join(root, file)));
const walk = directory => fs.readdirSync(path.join(root, directory), { withFileTypes: true }).flatMap(entry => {
    const file = path.posix.join(directory, entry.name);
    return entry.isDirectory() ? walk(file) : [file];
});
const runtime = walk('modules').filter(f => f.endsWith('.js') && !['modules/App.js', 'modules/main.js', 'modules/wizard-stability.js'].includes(f));
for (const file of runtime) copy(file);
for (const file of walk('modules').filter(f => f.endsWith('.d.ts'))) copy(file);

write('index.js', "export * from './modules/engine-api.js';\n");
copy('modules/App.js', 'reference/App.production.js');
for (const file of ['ENGINE_ARCHITECTURE.md', 'RELEASE_MANIFEST.json', 'BUILD_PROFILE.json', ...fs.readdirSync(root).filter(f => /^M22[4-9]|^M23[0-5]/.test(f) && f.endsWith('.md'))]) copy(file);
const manifest = JSON.parse(fs.readFileSync(path.join(root, 'RELEASE_MANIFEST.json')));
const coverage = {
    schemaVersion: 2,
    release: { milestone: manifest.milestone, build: manifest.build, engineVersion: manifest.engineVersion },
    sourceCommit: execFileSync('git', ['rev-parse', 'HEAD'], { cwd: root, encoding: 'utf8' }).trim(),
    sourceWorkingTree: execFileSync('git', ['status', '--porcelain'], { cwd: root, encoding: 'utf8' }).trim() ? 'modified' : 'clean',
    appSourceSha256: crypto.createHash('sha256').update(fs.readFileSync(path.join(root, 'modules/App.js'))).digest('hex'),
    engineModules: runtime.filter(f => f.startsWith('modules/next-engine/')).map(f => f.slice('modules/next-engine/'.length)).sort(),
    canonicalRuntimeFiles: runtime.sort(),
    domainExports: Object.keys(domain).sort(),
    boundary: 'Canonical production modules are copied byte for byte. No declarations are extracted from App.js. The UI source is retained only as integration evidence.'
};
write('coverage.json', JSON.stringify(coverage, null, 2) + '\n');
const suites = [], referenceSuites = [];
for (const file of fs.readdirSync(path.join(root, 'verification'))) {
    if (!file.endsWith('.mjs')) continue;
    const text = fs.readFileSync(path.join(root, 'verification', file), 'utf8');
    write('reference/verification/' + file, text);
    const appImports = [...text.matchAll(/import\s*\{([^}]+)\}\s*from\s*['"]\.\.\/modules\/App\.js['"]/gs)]
        .flatMap(m => m[1].split(',').map(s => s.trim().split(/\s+as\s+/)[0]));
    if (/browser|import-loader|production-source/.test(file) || /productionSource|puppeteer|readFileSync|\bfs\./.test(text) || appImports.some(n => !(n in shell))) {
        referenceSuites.push(file); continue;
    }
    write('verification/' + file, text.replaceAll('../modules/App.js', '../modules/engine-shell.js'));
    if (/-(test|matrix)\.mjs$/.test(file) || file === 'm198-creation-audit.mjs') suites.push(file);
}
write('scripts/audit.mjs', `import {execFileSync} from 'node:child_process';\nimport {fileURLToPath} from 'node:url';\nconst root=fileURLToPath(new URL('../',import.meta.url));\nconst suites=${JSON.stringify(suites.sort())};\nexecFileSync(process.execPath,['scripts/verify-snapshot.mjs','--runtime-only'],{cwd:root,stdio:'inherit'});\nfor(const suite of suites)execFileSync(process.execPath,['verification/'+suite],{cwd:root,stdio:'inherit'});\nconsole.log('PASS canonical standalone engine: '+suites.length+' semantic suites.');\n`);
copy('engine-lab/verify-snapshot.mjs', 'scripts/verify-snapshot.mjs');
copy('engine-lab/example.mjs', 'examples/generate.mjs');
copy('engine-lab/PACKAGE_README.md', 'README.md');
write('reference/integration-suites.json', JSON.stringify(referenceSuites.sort(), null, 2) + '\n');
write('package.json', JSON.stringify({ name: 'pursuit-iron-engine', version: manifest.engineVersion, private: true, type: 'module', engines: { node: '>=22' }, exports: { '.': './index.js', './shell': './modules/engine-shell.js', './engine/*': './modules/next-engine/*' }, scripts: { test: 'node scripts/audit.mjs', example: 'node examples/generate.mjs', verify: 'node scripts/verify-snapshot.mjs' } }, null, 2) + '\n');
const files = {};
function hashTree(dir = '') {
    for (const entry of fs.readdirSync(path.join(out, dir), { withFileTypes: true })) {
        const file = path.posix.join(dir, entry.name);
        if (entry.isDirectory()) hashTree(file);
        else if (file !== 'snapshot.json') files[file] = crypto.createHash('sha256').update(fs.readFileSync(path.join(out, file))).digest('hex');
    }
}
hashTree();
write('snapshot.json', JSON.stringify({ schemaVersion: 2, files }, null, 2) + '\n');
console.log(JSON.stringify({ output: out, engineModules: coverage.engineModules.length, canonicalRuntimeFiles: runtime.length, domainExports: coverage.domainExports.length, semanticSuites: suites.length, extractedDeclarations: 0 }));
