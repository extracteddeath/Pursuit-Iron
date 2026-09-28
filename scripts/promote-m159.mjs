import fs from 'node:fs';
import { execFileSync } from 'node:child_process';
import zlib from 'node:zlib';
const expected={'modules/App.js':'9d3c89def1d321b2e42da2446b05065eec466ba3','modules/next-engine/cycle-runtime-adapter.js':'782a726272c98339c309add61ec338d1ae9b5129','CHANGELOG.md':'3e0f6a3df178950e5e051f83f8c08d8cc992b045','RELEASE_MANIFEST.json':'5c0e8030b5ed45e87305710a23c1b07448c6224d'};
for(const [p,h] of Object.entries(expected)){const got=execFileSync('git',['hash-object',p],{encoding:'utf8'}).trim();if(got!==h)throw new Error(`Refusing M159 promotion: ${p} changed (${got} != ${h})`);}
const names=['00','01','02a','02b','03'];
const b64=names.map(n=>fs.readFileSync(`scripts/m159-patch-${n}.txt`,'utf8').trim()).join('');
const patch=zlib.gunzipSync(Buffer.from(b64,'base64'));
fs.writeFileSync('.m159.patch',patch);
execFileSync('git',['apply','--check','.m159.patch'],{stdio:'inherit'});
execFileSync('git',['apply','.m159.patch'],{stdio:'inherit'});
fs.rmSync('.m159.patch');
console.log('M159 core patch applied');
