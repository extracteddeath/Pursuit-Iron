import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

const root = path.resolve(new URL('../', import.meta.url).pathname);
const modulesRoot = path.join(root, 'modules');

function walk(directory) {
    return fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
        const full = path.join(directory, entry.name);
        return entry.isDirectory() ? walk(full) : [full];
    });
}

const moduleFiles = walk(modulesRoot)
    .filter(file => file.endsWith('.js'))
    .map(file => path.relative(root, file).split(path.sep).join('/'))
    .sort();
const moduleSet = new Set(moduleFiles);

function resolveRelative(from, specifier) {
    if (!specifier.startsWith('.'))
        return null;
    let resolved = path.posix.normalize(path.posix.join(path.posix.dirname(from), specifier));
    if (!path.posix.extname(resolved))
        resolved += '.js';
    return moduleSet.has(resolved) ? resolved : null;
}

function dependencies(file) {
    const source = fs.readFileSync(path.join(root, file), 'utf8');
    const specs = [];
    for (const match of source.matchAll(/(?:^|\n)\s*import\s*['"]([^'"]+)['"]/g))
        specs.push(match[1]);
    for (const match of source.matchAll(/(?:^|\n)\s*(?:import|export)\s+[^;]*?\s+from\s+['"]([^'"]+)['"]/g))
        specs.push(match[1]);
    for (const match of source.matchAll(/\bimport\s*\(\s*['"]([^'"]+)['"]\s*\)/g))
        specs.push(match[1]);
    // A module-worker entry is executable production code loaded through a URL, not an import.
    for (const match of source.matchAll(/\bnew\s+Worker\s*\(\s*new\s+URL\s*\(\s*['"]([^'"]+)['"]\s*,\s*import\.meta\.url/g))
        specs.push(match[1]);
    return [...new Set(specs.map(spec => resolveRelative(file, spec)).filter(Boolean))];
}

const graph = new Map(moduleFiles.map(file => [file, dependencies(file)]));
const reachable = new Set();
const stack = ['modules/main.js'];
while (stack.length) {
    const file = stack.pop();
    if (reachable.has(file))
        continue;
    reachable.add(file);
    for (const dependency of graph.get(file) ?? [])
        stack.push(dependency);
}

const orphaned = moduleFiles.filter(file => !reachable.has(file));
assert.deepEqual(orphaned, [], 'Production modules not reachable from modules/main.js: ' + orphaned.join(', '));

for (const [file, deps] of graph)
    for (const dep of deps)
        assert.ok(moduleSet.has(dep), `${file} imports missing production module ${dep}`);

console.log(`PASS M236 runtime reachability: ${reachable.size}/${moduleFiles.length} authored production modules reachable from modules/main.js.`);
