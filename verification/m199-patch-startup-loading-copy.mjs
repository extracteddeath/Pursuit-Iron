import fs from 'node:fs';
const path='index.html';
let src=fs.readFileSync(path,'utf8');
function once(before,after,label){const n=src.split(before).length-1;if(n!==1)throw new Error(`${label}: expected 1 match, got ${n}`);src=src.replace(before,after);}
once('<div id="pursuit-startup-status" class="pursuit-boot-status">Starting…</div>','<div id="pursuit-startup-status" class="pursuit-boot-status">Loading Pursuit Iron…</div>','initial startup status');
once("status('Still starting — your saved training is safe.')","status('Still loading — your saved training is safe.')",'slow startup status');
fs.writeFileSync(path,src);
console.log('M199 startup loading copy applied.');
