import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';

const root = process.cwd();
const read = p => fs.readFileSync(path.join(root,p),'utf8');
const fail = msg => { console.error(`RELEASE VERIFY FAILED: ${msg}`); process.exit(1); };

const sw = read('sw.js');
const shellMatch = sw.match(/const SHELL=\[(.*?)\];/s);
if (!shellMatch) fail('service worker SHELL list not found');
const shell = [...shellMatch[1].matchAll(/"(\.\/[^\"]+)"/g)].map(m=>m[1]);
const missing = shell.filter(p => p !== './' && !fs.existsSync(path.join(root,p.slice(2))));
if (missing.length) fail(`missing precache files: ${missing.join(', ')}`);

const profile = JSON.parse(read('BUILD_PROFILE.json'));
if (!sw.includes(`const CACHE="${profile.cache}"`)) fail('BUILD_PROFILE cache does not match sw.js');
if (profile.uiMilestone !== 'M159 Standalone Program → Training Cycle') fail('unexpected UI milestone');

const app = read('modules/App.js');
for (const marker of ['Turn into training cycle','convertProgramToNextCycleForShell','onConvertCycle: beginCycleConversion','Remove saved plan','Restore to library']) {
  if (!app.includes(marker)) fail(`missing App marker: ${marker}`);
}
if (app.includes('Start next block')) fail('standalone Start next block action is still present');
if (!app.includes("const __APP_VERSION__='3.209.0'; const __BUILD__='764';")) fail('App version/build mismatch');

const adapter = read('modules/next-engine/cycle-runtime-adapter.js');
if (!adapter.includes('export function convertProgramToNextCycleForShell(options)')) fail('cycle conversion adapter missing');
if (!adapter.includes('const currentLegacy = clone(current);')) fail('conversion no longer preserves current program as block 1');

const jsFiles=[];
for (const dir of ['modules']) {
  const walk = p => {
    for (const ent of fs.readdirSync(p,{withFileTypes:true})) {
      const full=path.join(p,ent.name);
      if (ent.isDirectory()) walk(full);
      else if (ent.name.endsWith('.js')) jsFiles.push(full);
    }
  };
  walk(path.join(root,dir));
}
for (const f of jsFiles) execFileSync(process.execPath,['--check',f],{stdio:'ignore'});
console.log(`Release integrity OK: ${shell.length} precache entries present; ${jsFiles.length} authored JS files parse.`);
console.log(`Cache: ${profile.cache}`);
console.log(`UI milestone: ${profile.uiMilestone}`);
