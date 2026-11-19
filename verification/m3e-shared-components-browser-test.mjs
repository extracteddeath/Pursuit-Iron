// M3 Expressive is a web design-system implementation, not Jetpack Compose.
// Verify live shared surface anatomy, icon actions, touch size and state feedback.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import http from 'node:http';
import puppeteer from 'puppeteer-core';

const root=path.resolve(new URL('../',import.meta.url).pathname);
const importMap=fs.readFileSync(path.join(root,'index.html'),'utf8')
 .match(/<script type="importmap">[\s\S]*?<\/script>/)[0];
const fixture=[
 "import React from '/vendor/react.js';",
 "import {createRoot} from '/vendor/react-dom-client.js';",
 "import {UiCard,UiAction,UiIconAction,UiChoice} from '/modules/ui-primitives.js';",
 "function Demo(){const [on,setOn]=React.useState(false);return React.createElement('main',{className:'wpb',id:'expressive-harness'},",
 "React.createElement(UiCard,{as:'section',id:'standard-card'},'Standard surface'),",
 "React.createElement(UiCard,{as:'section',tone:'featured',id:'feature-card','aria-label':'Featured training summary'},'Featured training summary'),",
 "React.createElement(UiAction,{variant:'primary',id:'primary-action',onClick:()=>{}},'Save changes'),",
 "React.createElement(UiIconAction,{id:'icon-action','aria-label':'Close preview',onClick:()=>{}},React.createElement('span',{'aria-hidden':true},'×')),",
 "React.createElement(UiChoice,{kind:'chip',id:'select-chip',selected:on,onClick:()=>setOn(v=>!v)},'Focus chest'))}",
 "createRoot(document.getElementById('root')).render(React.createElement(Demo));"
].join('\n');
const html='<!doctype html><html lang="en"><head><meta name="viewport" content="width=device-width,initial-scale=1">'
 +'<link rel="stylesheet" href="/app.css">'+importMap
 +'</head><body><div id="root" data-pi-motion="expressive"></div><script type="module">'+fixture+'</script></body></html>';
const mime={'.js':'text/javascript','.html':'text/html','.css':'text/css'};
const server=http.createServer((req,res)=>{
 const route=new URL(req.url,'http://localhost').pathname;
 if(route==='/m3e-components.html'){res.setHeader('Content-Type','text/html');res.end(html);return;}
 const file=path.resolve(root,'.'+route);
 if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){
   res.writeHead(404);res.end();return;
 }
 res.setHeader('Content-Type',mime[path.extname(file)]||'application/octet-stream');
 res.end(fs.readFileSync(file));
});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
let browser;
try {
 browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',
   headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
 const origin='http://127.0.0.1:'+server.address().port+'/m3e-components.html';
 for(const [width,height] of [[320,740],[393,850],[834,850],[1280,850]]){
  const page=await browser.newPage(),errors=[];
  try {
   page.on('pageerror',error=>errors.push(error.message));
   await page.setViewport({width,height,isMobile:width<=393,hasTouch:width<=393});
   await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
   await page.goto(origin,{waitUntil:'networkidle0'});
   await page.waitForSelector('#feature-card');
   const state=await page.evaluate(()=>{
     const ids=['standard-card','feature-card','primary-action','icon-action','select-chip'];
     const result={};
     for(const id of ids) {
       const el=document.getElementById(id),style=getComputedStyle(el),box=el.getBoundingClientRect();
       result[id]={tag:el.localName,radius:style.borderTopLeftRadius,
         background:style.backgroundColor,width:box.width,height:box.height,
         label:el.getAttribute('aria-label'),surface:el.getAttribute('data-surface'),
         role:el.getAttribute('data-action')||el.getAttribute('data-choice-kind'),
         after:{content:getComputedStyle(el,'::after').content,
           pointer:getComputedStyle(el,'::after').pointerEvents,
           transition:getComputedStyle(el,'::after').transitionDuration}};
     }
     return {elements:result,scroll:document.documentElement.scrollWidth};
   });
   const e=state.elements;
   assert.equal(e['feature-card'].tag,'section');
   assert.equal(e['feature-card'].surface,'featured');
   assert.notEqual(e['feature-card'].radius,e['standard-card'].radius,
    'Featured shape must differ from the default 24px card');
   assert.equal(e['icon-action'].label,'Close preview');
   assert.equal(e['icon-action'].role,'icon');
   assert.ok(e['icon-action'].height>=48&&e['icon-action'].width>=48,
    'Native-style icon action must preserve the 48px accessible target');
   assert.ok(e['primary-action'].height>=52,
    'High-emphasis action must retain 52px touch size');
   assert.equal(e['primary-action'].after.pointer,'none',
    'Decorative state layer must not intercept taps');
   assert.equal(e['primary-action'].after.transition,'0s',
    'Reduced-motion system preference must remove state-layer transition');
   assert.ok(state.scroll<=width+2,'No horizontal screen overflow at '+width);
   await page.click('#select-chip');
   assert.equal(await page.$eval('#select-chip',el=>el.getAttribute('aria-pressed')),'true',
     'Selection change must remain semantic and immediate');
   assert.deepEqual(errors,[],'No browser runtime exception at '+width);
   if(width===393||width===834)
    await page.screenshot({path:path.join(root,'verification/m3e-owners-'+width+'.png')});
   console.log('M3E_COMPONENT_VIEW',JSON.stringify({width,card:e['feature-card'],
     action:e['primary-action'],icon:e['icon-action']}));
  }finally{await page.close();}
 }
}finally{
 await browser?.close();
 await new Promise(done=>server.close(done));
}
console.log('PASS expressive surface, primary/icon action, choice, reduced-motion and responsive browser behavior.');
