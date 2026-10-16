// Bundles the app core + canonical engine into one script for the Android JS runtime.
import { build } from 'esbuild';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
const here = dirname(fileURLToPath(import.meta.url));
const out = resolve(here, '../android/app/src/main/assets/pi-core.js');
mkdirSync(dirname(out), { recursive: true });
await build({ entryPoints: [resolve(here, 'src/bridge.js')], bundle: true, format: 'iife', platform: 'neutral', target: 'es2020',
    minify: true, legalComments: 'none', outfile: out, logLevel: 'warning', mainFields: ['module', 'main'] });
const bytes = readFileSync(out);
const hash = createHash('sha256').update(bytes).digest('hex').slice(0, 16);
writeFileSync(resolve(here, '../android/app/src/main/assets/pi-core.json'), JSON.stringify({ sha256: hash, bytes: bytes.length, builtAt: new Date().toISOString() }));
console.log('pi-core.js', bytes.length, 'bytes', hash);
