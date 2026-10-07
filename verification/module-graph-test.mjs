import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
const root = path.resolve('modules');
const walk = dir => fs.readdirSync(dir, { withFileTypes: true }).flatMap(e => e.isDirectory() ? walk(path.join(dir,e.name)) : [path.join(dir,e.name)]);
const files = walk(root).filter(f => f.endsWith('.js'));
const graph = new Map(files.map(file => [file, [...fs.readFileSync(file,'utf8').matchAll(/(?:from\s*|import\s*)['"]([^'"]+)['"]/g)]
    .map(m => m[1]).filter(p => p.startsWith('.')).map(p => path.resolve(path.dirname(file),p))]));
const seen = new Set(), stack = [];
function visit(file) {
    assert(!stack.includes(file), `Import cycle: ${[...stack,file].map(f => path.relative(root,f)).join(' → ')}`);
    if (seen.has(file)) return;
    stack.push(file);
    for (const dependency of graph.get(file) ?? []) {
        assert(graph.has(dependency), `Missing module ${dependency}`);
        visit(dependency);
    }
    stack.pop(); seen.add(file);
}
for (const file of files) visit(file);
console.log(`PASS module graph: ${files.length} modules, no missing edges or cycles.`);
