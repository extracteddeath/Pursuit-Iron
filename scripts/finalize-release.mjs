import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

// Finalize the CURRENT manifest only. Historical milestone scripts and workflows cannot rewrite
// this release, and this command never commits or pushes generated changes.
const read = file => fs.readFileSync(file, 'utf8');
const write = (file, value) => fs.writeFileSync(file, value);
const sha = value => crypto.createHash('sha256').update(value).digest('hex');
const manifest = JSON.parse(read('RELEASE_MANIFEST.json'));
const profile = JSON.parse(read('BUILD_PROFILE.json'));
const walkJs = directory => fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const file = path.posix.join(directory, entry.name);
    return entry.isDirectory() ? walkJs(file) : file.endsWith('.js') ? [file] : [];
});
const runtimeFiles = [...walkJs('modules').filter(file => file !== 'modules/App.js'), ...walkJs('vendor')].sort();
write('modules/App.js', read('modules/App.js').replace(/const __BUILD__='\d+'/g, `const __BUILD__='${manifest.build}'`));
write('index.html', read('index.html').replace(/build(:|=)'\d+'/g, `build$1'${manifest.build}'`));
if (!read('modules/next-engine/config.js').includes(`ENGINE_VERSION = '${manifest.engineVersion}'`))
    throw new Error('Manifest and engine version must agree before finalizing.');
profile.nextRuntimeModules = runtimeFiles.filter(file => file.startsWith('modules/next-engine/')).length;
delete profile.shadowRuntimeModules;
const previousShell = [...read('sw.js').match(/const SHELL=\[(.*?)\];/s)[1].matchAll(/"(\.\/[^\"]+)"/g)].map(match => match[1]);
const shell = [...new Set([...previousShell.filter(file => !file.startsWith('./modules/') && !file.startsWith('./vendor/')),
    './modules/App.js', ...runtimeFiles.map(file => `./${file}`)])].sort();
write('sw.js', read('sw.js')
    .replace(/^\/\* Pursuit Iron 4\.0[^\n]*\*\//, `/* Pursuit Iron 4.0 — ${manifest.milestone} production release. */`)
    .replace(/const CACHE="[^"]+"/, `const CACHE="${manifest.cache}"`)
    .replace(/const SHELL=\[(.*?)\];/s, `const SHELL=${JSON.stringify(shell, null, 2)};`));
write('BUILD_PROFILE.json', JSON.stringify(profile, null, 2) + '\n');
// Current hashes describe current JS. Historical TypeScript/source-engine hashes live in git;
// absent source files must not appear to certify this runtime.
for (const key of ['shadowEngineVersion','validatedLiveBaseline','sourceNextEngineAggregate','sourceShadowEngineAggregate',
    'sourceFiles','shadowSourceFiles','baselineSourceNextEngineAggregate','baselineSourceFiles']) delete manifest[key];
manifest.sourceFilesStatus = 'JavaScript modules are the maintained production source. Runtime and UI hashes certify this release; historical source provenance is retained in git.';
manifest.runtimeFiles = Object.fromEntries(runtimeFiles.map(file => [file, sha(fs.readFileSync(file))]));
manifest.runtimeAggregate = sha(runtimeFiles.map(file => `${file}:${manifest.runtimeFiles[file]}\n`).join(''));
manifest.uiFiles = Object.fromEntries(['modules/App.js','index.html','sw.js','BUILD_PROFILE.json','CHANGELOG.md','app.css'].map(file => [file, sha(fs.readFileSync(file))]));
write('RELEASE_MANIFEST.json', JSON.stringify(manifest, null, 2) + '\n');
console.log(`Finalized ${manifest.milestone}: build ${manifest.build}, Engine ${manifest.engineVersion}, ${runtimeFiles.length} runtime files, ${shell.length} offline entries.`);
