import fs from 'node:fs';

const path = 'modules/next-engine/realizer.js';
let text = fs.readFileSync(path, 'utf8');
const marker = `                    // Appending a missing region is preferred, but a full-body day can already be at its\n`;
const first = text.indexOf(marker);
if (first < 0) throw new Error('Structural swap marker not found');
const second = text.indexOf(marker, first + marker.length);
if (second < 0) {
  console.log('No duplicate structural swap block remains.');
  process.exit(0);
}
const tailMarker = `                    if (!repaired)\n                        break;`;
const tail = text.indexOf(tailMarker, second);
if (tail < 0) throw new Error('Structural repair tail marker not found');
const firstBlock = text.slice(first, second);
const secondBlock = text.slice(second, tail);
if (firstBlock !== secondBlock) throw new Error('Two swap blocks are not byte-identical; refusing automatic cleanup.');
text = text.slice(0, second) + text.slice(tail);
fs.writeFileSync(path, text);
console.log('Removed duplicated Full Body structural swap block.');
