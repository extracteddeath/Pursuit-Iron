import fs from 'node:fs';
const path='modules/App.js';
let src=fs.readFileSync(path,'utf8');
function once(before,after,label){const n=src.split(before).length-1;if(n!==1)throw new Error(`${label}: expected 1 match, got ${n}`);src=src.replace(before,after);}
once('const MEM = { data: null };','const MEM = { text: null };','memory fallback shape');
once('    return MEM.data == null ? null : parseStoredData(JSON.stringify(MEM.data));','    return MEM.text == null ? null : parseStoredData(MEM.text);','memory fallback read');
once('        text = JSON.stringify(data);\n        MEM.data = JSON.parse(text);','        text = JSON.stringify(data);\n        // The serialized text is already the immutable snapshot the fallback needs. Re-parsing the\n        // entire store here doubled synchronous autosave work as program/history data grew.\n        MEM.text = text;','memory fallback save');
fs.writeFileSync(path,src);
console.log('M199 persistence copy optimization applied.');
