import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const read = (p) => fs.readFileSync(path.join(root, p), 'utf8');
const exists = (p) => fs.existsSync(path.join(root, p));
const fail = (msg) => { console.error(`\nRELEASE INTEGRITY FAILED\n${msg}\n`); process.exit(1); };

if (!exists('sw.js')) fail('sw.js is missing');
if (!exists('index.html')) fail('index.html is missing');
if (!exists('BUILD_PROFILE.json')) fail('BUILD_PROFILE.json is missing');

const sw = read('sw.js');
const index = read('index.html');
const profile = JSON.parse(read('BUILD_PROFILE.json'));

const cacheMatch = sw.match(/const\s+CACHE\s*=\s*["']([^"']+)["']/);
if (!cacheMatch) fail('Could not read CACHE from sw.js');
if (profile.cache && cacheMatch[1] !== profile.cache) {
  fail(`Cache mismatch: sw.js uses ${cacheMatch[1]} but BUILD_PROFILE.json declares ${profile.cache}`);
}

const shellMatch = sw.match(/const\s+SHELL\s*=\s*\[([\s\S]*?)\]\s*;/);
if (!shellMatch) fail('Could not read SHELL from sw.js');
const shell = [...shellMatch[1].matchAll(/["']([^"']+)["']/g)].map((m) => m[1]);
if (!shell.length) fail('Service-worker SHELL is empty');

const normalize = (ref) => {
  const clean = ref.replace(/^\.\//, '').split(/[?#]/)[0];
  return clean === '' ? 'index.html' : clean;
};

const required = new Set(shell.map(normalize));
for (const match of index.matchAll(/(?:src|href)=["'](\.\/[^"']+)["']/g)) {
  required.add(normalize(match[1]));
}

const missing = [...required].filter((p) => !exists(p)).sort();
if (missing.length) {
  fail(`Missing ${missing.length} production runtime file(s):\n${missing.map((p) => `  - ${p}`).join('\n')}`);
}

for (const p of required) {
  if (!p.endsWith('.js')) continue;
  const src = read(p);
  for (const match of src.matchAll(/(?:from\s*|import\s*\()\s*["'](\.{1,2}\/[^"']+)["']/g)) {
    let target = path.normalize(path.join(path.dirname(p), match[1]));
    if (!path.extname(target)) target += '.js';
    if (!exists(target)) fail(`${p} imports missing runtime dependency ${target}`);
  }
}

console.log(`Release integrity OK: ${required.size} runtime files present.`);
console.log(`Service-worker cache: ${cacheMatch[1]}`);
console.log(`UI milestone: ${profile.uiMilestone ?? 'n/a'}`);
