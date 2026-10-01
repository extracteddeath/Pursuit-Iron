import fs from 'node:fs';
import path from 'node:path';
const files=[];
for(const dir of ['modules/next-engine'])
  for(const name of fs.readdirSync(dir)) if(name.endsWith('.js')) files.push('../'+path.posix.join(dir,name));
for(const file of files) await import(file);
await import('../modules/legacy-research-data.js');
console.log(`PASS full ESM import: ${files.length} current engine modules and data-only backup compatibility.`);
