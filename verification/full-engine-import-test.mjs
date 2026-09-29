import fs from 'node:fs';
import path from 'node:path';
const files=[];
for(const dir of ['modules/next-engine','modules/shadow-engine'])
  for(const name of fs.readdirSync(dir)) if(name.endsWith('.js')) files.push('../'+path.posix.join(dir,name));
for(const file of files) await import(file);
console.log(`PASS full ESM import: ${files.length} engine/shadow modules.`);
