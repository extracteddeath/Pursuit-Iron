import fs from 'node:fs';
import path from 'node:path';
export function engineModuleGraph() {
    const root = path.resolve(new URL('../modules/', import.meta.url).pathname);
    const graph = new Map();
    const visit = dir => {
        for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
            const file = path.join(dir, entry.name);
            if (entry.isDirectory()) visit(file);
            else if (file.endsWith('.js') && !['App.js', 'main.js', 'wizard-stability.js'].includes(entry.name)) {
                const source = fs.readFileSync(file, 'utf8');
                const refs = [...source.matchAll(/^(?:import|export)\s+(?:(?:\*[^;\n]*?)|(?:\{[\s\S]*?\})|(?:[\w$]+))\s+from\s+['"](\.[^'"]+)['"]/gm)]
                    .map(m => path.resolve(path.dirname(file), m[1]));
                graph.set(file, refs);
            }
        }
    };
    visit(root);
    return graph;
}
export function moduleCycles(graph) {
    const state = new Map(), stack = [], cycles = [];
    const visit = file => {
        if (state.get(file) === 2) return;
        if (state.get(file) === 1) { cycles.push(stack.slice(stack.indexOf(file)).concat(file)); return; }
        state.set(file, 1); stack.push(file);
        for (const next of graph.get(file) ?? []) if (graph.has(next)) visit(next);
        stack.pop(); state.set(file, 2);
    };
    for (const file of graph.keys()) visit(file);
    return cycles;
}
