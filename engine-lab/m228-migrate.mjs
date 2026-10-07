import fs from 'node:fs';
import path from 'node:path';
import { parse } from '@babel/parser';
import traverse from '@babel/traverse';

const root=path.resolve(new URL('../',import.meta.url).pathname);
const appFile=path.join(root,'modules/App.js');
const shellFile=path.join(root,'modules/engine-shell.js');
const source=fs.readFileSync(appFile,'utf8');
if(fs.existsSync(shellFile) && /from\s*['"]\.\/engine-shell\.js['"]/.test(source)) {
  console.log('M228 canonical boundary already materialized.');
  process.exit(0);
}
const ast=parse(source,{sourceType:'module'});
let program;
traverse(ast,{Program(p){program=p;}});
const bindings=program.scope.bindings;
const records=new Map();
for(const [name,binding] of Object.entries(bindings)){
  const p=binding.path;
  if(binding.kind==='module'){
    const node=p.parentPath.node;
    records.set(name,{name,node,import:node.source.value,dependencies:new Set()});
    continue;
  }
  const dependencies=new Set();
  p.traverse({ReferencedIdentifier(ref){
    const b=ref.scope.getBinding(ref.node.name);
    if(b && b===bindings[ref.node.name] && ref.node.name!==name) dependencies.add(ref.node.name);
  }});
  records.set(name,{name,node:p.node,kind:p.isVariableDeclarator()?p.parentPath.node.kind:null,dependencies});
}
const excluded=new Set([...records.values()].filter(r=>r.import && !r.import.startsWith('./')).map(r=>r.name));
let changed=true;
while(changed){
  changed=false;
  for(const r of records.values()) if(!excluded.has(r.name) && [...r.dependencies].some(n=>excluded.has(n))){
    excluded.add(r.name); changed=true;
  }
}
const selected=[...records.values()].filter(r=>!excluded.has(r.name));
const chunks=[]; const imports=new Map();
for(const r of selected){
  if(r.import){ if(!imports.has(r.node.start)) imports.set(r.node.start,source.slice(r.node.start,r.node.end)); }
  else chunks.push({start:r.node.start,text:r.kind?`${r.kind} ${source.slice(r.node.start,r.node.end)};`:source.slice(r.node.start,r.node.end)});
}
const importedNames=selected.filter(r=>r.import).map(r=>r.name);
const localNames=selected.filter(r=>!r.import).map(r=>r.name);
const shell='// M228 canonical engine shell. App.js consumes this module; isolated audits copy it verbatim.\n'
  +[...imports.values()].join('\n')+'\n'
  +chunks.sort((a,b)=>a.start-b.start).map(r=>r.text).join('\n\n')
  +'\nsetShellEquipmentExpander(expandEquipment);\n'
  +`export { ${[...localNames,...importedNames].sort().join(', ')} };\n`;

const moved=new Set(selected.map(r=>r.name));
moved.delete('__APP_VERSION__'); moved.delete('__BUILD__');
const edits=[];
for(const bodyPath of program.get('body')){
  const n=bodyPath.node;
  if(bodyPath.isImportDeclaration()){
    const locals=bodyPath.get('specifiers').map(p=>p.node.local?.name).filter(Boolean);
    const movedCount=locals.filter(name=>moved.has(name)).length;
    if(movedCount && movedCount!==locals.length) throw new Error('Mixed moved/retained import is not supported: '+source.slice(n.start,n.end));
    if(movedCount) edits.push([n.start,n.end,'']);
  } else if(bodyPath.isVariableDeclaration()){
    const decPaths=bodyPath.get('declarations');
    const keep=[];
    for(const dp of decPaths){
      const names=Object.keys(dp.get('id').getBindingIdentifiers());
      const isMoved=names.length && names.every(name=>moved.has(name));
      if(!isMoved) keep.push(dp.node);
    }
    if(keep.length===decPaths.length) continue;
    if(!keep.length) edits.push([n.start,n.end,'']);
    else edits.push([n.start,n.end,`${n.kind} ${keep.map(d=>source.slice(d.start,d.end)).join(', ')};`]);
  } else if((bodyPath.isFunctionDeclaration()||bodyPath.isClassDeclaration()) && n.id && moved.has(n.id.name)){
    edits.push([n.start,n.end,'']);
  } else if(bodyPath.isExportNamedDeclaration() && bodyPath.get('declaration')?.node){
    const dp=bodyPath.get('declaration');
    const d=dp.node;
    if((dp.isFunctionDeclaration()||dp.isClassDeclaration()) && d.id && moved.has(d.id.name)){
      edits.push([n.start,n.end,'']);
    } else if(dp.isVariableDeclaration()){
      const decPaths=dp.get('declarations');
      const keep=[];
      for(const decPath of decPaths){
        const names=Object.keys(decPath.get('id').getBindingIdentifiers());
        if(!(names.length && names.every(name=>moved.has(name)))) keep.push(decPath.node);
      }
      if(keep.length!==decPaths.length){
        edits.push([n.start,n.end,keep.length ? `export ${d.kind} ${keep.map(x=>source.slice(x.start,x.end)).join(', ')};` : '']);
      }
    }
  } else if(bodyPath.isExpressionStatement() && source.slice(n.start,n.end)==='setShellEquipmentExpander(expandEquipment);'){
    edits.push([n.start,n.end,'']);
  }
}
edits.sort((a,b)=>b[0]-a[0]);
let app=source;
for(const [a,b,repl] of edits) app=app.slice(0,a)+repl+app.slice(b);
const names=[...moved].sort();
const rows=[]; for(let i=0;i<names.length;i+=8) rows.push('  '+names.slice(i,i+8).join(', ')+',');
const boundary=`import {\n${rows.join('\n')}\n} from "./engine-shell.js";\n`;
app=boundary+app;
parse(app,{sourceType:'module'});
if(/from\s*['"]\.\/next-engine\//.test(app)) throw new Error('M228 migration left a direct App -> next-engine import.');
if(app.includes('setShellEquipmentExpander(expandEquipment);')) throw new Error('M228 migration left engine registration in App.');
if(/from\s*['"](?:react|react\/|lucide-react)/.test(shell)) throw new Error('M228 shell depends on UI framework.');
fs.writeFileSync(shellFile,shell);
fs.writeFileSync(appFile,app);
console.log(JSON.stringify({movedBindings:moved.size,excludedUIBindings:excluded.size,appBytes:Buffer.byteLength(app),shellBytes:Buffer.byteLength(shell)}));
