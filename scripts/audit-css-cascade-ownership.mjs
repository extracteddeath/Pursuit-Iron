import fs from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

// CSS inventory, not a computed-cascade oracle. Only compare selectors inside the
// same at-rule scope: a responsive variant is not automatically an override.
export function inspectCascade(css) {
  const clean=css.replace(/\/\*[\s\S]*?\*\//g, m=>' '.repeat(m.length));
  const rules=[];
  function closeBrace(open,end) {
    let depth=0,quote=null;
    for(let i=open;i<end;i++) {
      const c=clean[i];
      if(quote) { if(c==='\\') {i++;continue;} if(c===quote)quote=null;continue; }
      if(c==='"'||c==="'") {quote=c;continue;}
      if(c==='{')depth++;
      if(c==='}'&&!--depth)return i;
    }
    throw Error('Unbalanced CSS block near offset '+open);
  }
  function scan(start,end,scope=[]) {
    let from=start,i=start,quote=null;
    while(i<end) {
      const c=clean[i];
      if(quote) {if(c==='\\')i+=2;else {if(c===quote)quote=null;i++;}continue;}
      if(c==='"'||c==="'"){quote=c;i++;continue;}
      if(c===';'){from=++i;continue;}
      if(c!=='{'){i++;continue;}
      const header=clean.slice(from,i).trim().replace(/\s+/g,' ');
      const stop=closeBrace(i,end);
      if(/^@(media|supports|container|layer)\b/i.test(header))
        scan(i+1,stop,[...scope,header]);
      else if(header&&!header.startsWith('@')) {
        const declarations=clean.slice(i+1,stop);
        const props=[...declarations.matchAll(/(?:^|;)\s*([\w-]+)\s*:/g)].map(m=>m[1]);
        rules.push({selector:header,scope:scope.join(' | ')||'root',start:from,properties:[...new Set(props)]});
      }
      i=stop+1;from=i;
    }
  }
  scan(0,clean.length);
  const buckets=new Map();
  for(const rule of rules) {
    const key=rule.scope+'\0'+rule.selector;
    if(!buckets.has(key))buckets.set(key,[]);
    buckets.get(key).push(rule);
  }
  const collisions=[...buckets.values()].filter(rows=>rows.length>1).map(rows=>{
    const freq=new Map();
    for(const row of rows)for(const prop of row.properties)freq.set(prop,(freq.get(prop)||0)+1);
    return {scope:rows[0].scope,selector:rows[0].selector,blocks:rows.length,
      repeatedProperties:[...freq].filter(([,n])=>n>1).map(([key])=>key).sort(),
      locations:rows.map(row=>row.start)};
  }).sort((a,b)=>b.repeatedProperties.length-a.repeatedProperties.length||b.blocks-a.blocks);
  const areas=Object.fromEntries(['home','plan','settings','progress','profile','workout','library','onboarding']
    .map(area=>[area,{rules:rules.filter(r=>r.selector.includes('.wpb-'+area)).length,
      repeatedSelectors:collisions.filter(r=>r.selector.includes('.wpb-'+area)).length}]));
  return {cssCharacters:css.length,priorityDeclarations:(css.match(/!important/g)||[]).length,
    rules:rules.length,scopedRepeatedSelectors:collisions.length,
    potentialSamePropertyOverrides:collisions.filter(x=>x.repeatedProperties.length>0).length,
    areas,hotspots:collisions.slice(0,40)};
}

if(process.argv[1]&&fileURLToPath(import.meta.url)===path.resolve(process.argv[1])) {
  const result=inspectCascade(fs.readFileSync('app.css','utf8'));
  if(process.argv.includes('--json'))console.log(JSON.stringify(result,null,2));
  else {
    console.log('CSS cascade inventory:',JSON.stringify({
      cssCharacters:result.cssCharacters,priorityDeclarations:result.priorityDeclarations,
      rules:result.rules,scopedRepeatedSelectors:result.scopedRepeatedSelectors,
      potentialSamePropertyOverrides:result.potentialSamePropertyOverrides}));
    for(const [area,metric] of Object.entries(result.areas))console.log(area+': '+JSON.stringify(metric));
    console.log('Hotspots:',JSON.stringify(result.hotspots.slice(0,10),null,2));
  }
}
