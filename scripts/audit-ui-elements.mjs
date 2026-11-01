import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { inspectCascade } from './audit-css-cascade-ownership.mjs';

// Read-only coverage. Static source cannot certify computed styles or dynamic DOM.
const SCREENS=[['Home','Home'],['Plan','PlanView'],['Program','ProgramView'],
 ['Next workout','NextWorkoutView'],['Cycles','CyclesView'],['Wizard','Wizard'],
 ['Library','LibraryView'],['Progress','HistoryView'],['Profile','ProfileView'],
 ['Settings','SettingsView'],['Gallery','GalleryView'],['Workout','WorkoutSession']];
const CLICKABLE=new Set(['button','input','select','textarea','a','summary']);
const GENERIC=new Set(['pressable','hit','mono','wpb','wpb-scroll','wpb-page-shell']);
const JSX=/_jsx(?:s)?\(\s*(?:(['"])([a-z][a-z0-9-]*)\1|(Ui[A-Z]\w*))\s*,\s*\{/g;
const CLASS=/\b(?:wpb|pi)-[a-z0-9-]+/g;
const count=(s,re)=>(s.match(re)||[]).length;
const inc=(obj,key)=>(obj[key]=(obj[key]||0)+1);
function family(tag,classes){
 if(classes.some(x=>/(?:backdrop|dialog|sheet|popover|menu)/.test(x)))return 'overlay';
 if(classes.some(x=>/(?:metric|stat|score|glance)/.test(x)))return 'metric';
 if(classes.some(x=>/(?:chip|segment|choice|tab|filter|option)/.test(x)))return 'choice';
 if(['input','select','textarea'].includes(tag)||classes.some(x=>/(?:field|input|select)/.test(x)))return 'field';
 if(classes.some(x=>/(?:card|tile|panel|hero|surface)/.test(x)))return 'surface';
 if(CLICKABLE.has(tag)||classes.some(x=>/(?:action|button|toggle)/.test(x)))return 'action';
 if(['h1','h2','h3','h4','h5','h6','p','small','strong','label','time'].includes(tag))return 'typography';
 return 'layout';
}
// Find the corresponding outer props brace (ignore quoted strings and comments).
function propsOf(source,opening){
 let level=0,quote='',escaped=false,comment='';
 for(let i=opening;i<source.length;i++){
  const c=source[i],next=source[i+1];
  if(comment==='line'){if(c==='\n')comment='';continue;}
  if(comment==='block'){if(c==='*'&&next==='/'){comment='';i++;}continue;}
  if(quote){if(escaped){escaped=false;continue;}if(c==='\\'){escaped=true;continue;}if(c===quote)quote='';continue;}
  if(c==='/'&&next==='/'){comment='line';i++;continue;}
  if(c==='/'&&next==='*'){comment='block';i++;continue;}
  if(c==='"'||c==="'"||c.charCodeAt(0)===96){quote=c;continue;}
  if(c==='{')level++;
  if(c==='}'&&--level===0)return source.slice(opening+1,i);
 }
 return '';
}
// Only the outer className belongs to this element: nested child classes do not.
function ownClass(props){
 let curly=0,round=0,square=0,quote='',escaped=false;
 for(let i=0;i<props.length;i++){
  const c=props[i];
  if(quote){if(escaped){escaped=false;continue;}if(c==='\\'){escaped=true;continue;}if(c===quote)quote='';continue;}
  if(c==='"'||c==="'"||c.charCodeAt(0)===96){quote=c;continue;}
  if(c==='{'){curly++;continue;}if(c==='}'){curly--;continue;}
  if(c==='('){round++;continue;}if(c===')'){round--;continue;}
  if(c==='['){square++;continue;}if(c===']'){square--;continue;}
  if(curly||round||square||!props.startsWith('className',i))continue;
  const match=props.slice(i+9).match(/^\s*:\s*/);
  if(!match)continue;
  const pos=i+9+match[0].length,mark=props[pos];
  if(mark==='"'||mark==="'"){
   let j=pos+1;
   for(;j<props.length;j++){if(props[j]==='\\'){j++;continue;}if(props[j]===mark)break;}
   return {kind:'static',tokens:[...new Set(props.slice(pos+1,j).match(CLASS)||[])]};
  }
  return {kind:'dynamic',tokens:[]};
 }
 return {kind:'none',tokens:[]};
}
export function auditUiElements(root=process.cwd()){
 const modules=path.join(root,'modules');
 const css=fs.readFileSync(path.join(root,'app.css'),'utf8');
 const cssTokens=new Set([...css.matchAll(/\.((?:wpb|pi)-[a-z0-9-]+)/g)].map(m=>m[1]));
 const files=fs.readdirSync(modules,{withFileTypes:true}).filter(x=>x.isFile()&&x.name.endsWith('.js')).map(x=>x.name).sort();
 const perFile={},perOwner={},tokenOwners=new Map(),sharedOwners={};
 let native=0,shared=0;
 for(const name of files){
  const source=fs.readFileSync(path.join(modules,name),'utf8');
  if(!source.includes('react/jsx-runtime'))continue;
  const file='modules/'+name,ranges=[];
  if(name==='App.js')for(const [screen,symbol] of SCREENS){
   const start=source.indexOf('function '+symbol+'(');
   if(start<0)continue;
   const end=source.indexOf('\nfunction ',start+10);
   ranges.push({screen,start,end:end>start?end:source.length});
  }
  perFile[file]={elements:0,native:0,shared:0,dynamicClass:0};
  for(const hit of source.matchAll(JSX)){
   const tag=hit[2]||hit[3],isNative=!!hit[2];
   const screen=name==='App.js'?ranges.find(r=>hit.index>=r.start&&hit.index<r.end)?.screen||'App auxiliary composition':file;
   const role=perOwner[screen]||{elements:0,native:0,shared:0,rawInteractive:0,
     rawSurfaceCandidates:0,dynamicClass:0,staticClassWithoutCss:0,families:{},tags:{}};
   perOwner[screen]=role;
   const props=propsOf(source,hit.index+hit[0].length-1),cls=ownClass(props);
   const fam=family(tag,cls.tokens);
   role.elements++;perFile[file].elements++;
   inc(role.families,fam);inc(role.tags,tag);
   if(isNative){native++;role.native++;perFile[file].native++;}
   else{shared++;role.shared++;perFile[file].shared++;inc(sharedOwners,tag);}
   if(cls.kind==='dynamic'){role.dynamicClass++;perFile[file].dynamicClass++;}
   if(isNative&&CLICKABLE.has(tag))role.rawInteractive++;
   if(isNative&&fam==='surface')role.rawSurfaceCandidates++;
   for(const token of cls.tokens){
    if(!tokenOwners.has(token))tokenOwners.set(token,new Set());
    tokenOwners.get(token).add(screen);
    if(!cssTokens.has(token))role.staticClassWithoutCss++;
   }
  }
 }
 const ranked=Object.entries(perOwner).map(([owner,stats])=>({owner,...stats}))
  .sort((a,b)=>b.rawInteractive+b.rawSurfaceCandidates-a.rawInteractive-a.rawSurfaceCandidates);
 const reused=[...tokenOwners.entries()]
  .filter(([name,owners])=>owners.size>1&&!GENERIC.has(name))
  .map(([className,owners])=>({className,owners:[...owners].sort(),cssDefined:cssTokens.has(className)}))
  .sort((a,b)=>b.owners.length-a.owners.length||a.className.localeCompare(b.className));
 const unresolved=[...tokenOwners.keys()].filter(x=>!cssTokens.has(x)).sort();
 const cascade=inspectCascade(css);
 return {
  scope:'All compiled JSX calls in first-party modules/*.js, including App.js; not a rendered DOM or computed-style certificate',
  totals:{elements:native+shared,native,shared,jsxFiles:Object.keys(perFile).length,
   rawInteractive:ranked.reduce((n,x)=>n+x.rawInteractive,0),
   rawSurfaceCandidates:ranked.reduce((n,x)=>n+x.rawSurfaceCandidates,0),
   dynamicClass:ranked.reduce((n,x)=>n+x.dynamicClass,0),
   staticClassTokens:tokenOwners.size,cssClassTokens:cssTokens.size},
  perOwner:ranked,perFile,sharedOwners,
  crossOwnerClassCount:reused.length,crossOwnerClasses:reused.slice(0,100),
  staticClassWithoutCssCount:unresolved.length,staticClassWithoutCss:unresolved.slice(0,100),
  css:{characters:css.length,priorityDeclarations:cascade.priorityDeclarations,rules:cascade.rules,
   repeatedSelectors:cascade.scopedRepeatedSelectors,samePropertyOverrides:cascade.potentialSamePropertyOverrides,
   identicalDeclarationFamilies:cascade.sharedDeclarationFamilyCount,
   hotspots:cascade.hotspots.slice(0,20)},
  reviewQueue:ranked.filter(x=>x.rawInteractive||x.rawSurfaceCandidates).map(
   x=>({owner:x.owner,elements:x.elements,rawInteractive:x.rawInteractive,
     rawSurfaceCandidates:x.rawSurfaceCandidates,dynamicClass:x.dynamicClass}))
 };
}
if(process.argv[1]&&fileURLToPath(import.meta.url)===path.resolve(process.argv[1])){
 const audit=auditUiElements();
 if(process.argv.includes('--json'))console.log(JSON.stringify(audit,null,2));
 else{
  console.log('ELEMENT_COVERAGE',JSON.stringify(audit.totals));
  console.log('SHARED_OWNER_USAGE',JSON.stringify(audit.sharedOwners));
  console.log('CSS_DEBT',JSON.stringify(audit.css));
  console.log('REVIEW_QUEUE',JSON.stringify(audit.reviewQueue.slice(0,22)));
  console.log('REUSED_CLASSES',JSON.stringify(audit.crossOwnerClasses.slice(0,30)));
  console.log('STATIC_CLASS_UNRESOLVED',JSON.stringify(audit.staticClassWithoutCss.slice(0,25)));
  console.log('LIMITATION: computed styles, dynamic classes and native Android need separate validation.');
 }
}
