import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import http from 'node:http';
import puppeteer from 'puppeteer-core';

const root=path.resolve(new URL('../',import.meta.url).pathname);
const map=fs.readFileSync(path.join(root,'index.html'),'utf8').match(/<script type="importmap">[\s\S]*?<\/script>/)[0];
const fixture=[
"import React from '/vendor/react.js';",
"import {createRoot} from '/vendor/react-dom-client.js';",
"import {UiSegmented} from '/modules/ui-primitives.js';",
"function Demo(){const [unit,setUnit]=React.useState('lb'),[inc,setInc]=React.useState({v:2.5,unit:'lb'}),[effort,setEffort]=React.useState('rir'),[experience,setExperience]=React.useState('advanced');",
"window.settingsControlState=()=>({unit,inc,effort,experience});",
"const opts=unit==='lb'?[[0,'Std'],[2.5,'2.5'],[1,'1']]:[[0,'Std'],[1,'1'],[0.5,'0.5']];",
"return React.createElement('div',{className:'wpb wpb-settings'},",
"React.createElement(UiSegmented,{label:'Weight unit',value:unit,options:[['kg','kg'],['lb','lb']],onChange:setUnit}),",
"React.createElement(UiSegmented,{label:'Smallest jump',value:inc.v&&inc.unit!==unit?null:(inc.v||0),options:opts,onChange:v=>setInc({v,unit:v?unit:null})}),",
"React.createElement(UiSegmented,{label:'Effort scale',value:effort,options:[['rir','RIR'],['rpe','RPE']],onChange:setEffort}),",
"React.createElement(UiSegmented,{label:'Training experience',className:'wpb-experience-segmented',value:experience,options:[['novice','New'],['beginner','Beginner'],['intermediate','Intermediate'],['advanced','Advanced']],onChange:setExperience}));}",
"createRoot(document.getElementById('root')).render(React.createElement(Demo));"
].join('\n');
const html='<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="/app.css">'+map+'</head><body><div id="root" data-pi-motion="expressive"></div><script type="module">'+fixture+'</script></body></html>';
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html'};
const server=http.createServer((req,res)=>{
 const p=new URL(req.url,'http://localhost').pathname;
 if(p==='/segmented.html'){res.setHeader('Content-Type','text/html');res.end(html);return;}
 const file=path.resolve(root,'.'+p);
 if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){res.writeHead(404).end();return;}
 res.setHeader('Content-Type',mime[path.extname(file)]||'application/octet-stream');res.end(fs.readFileSync(file));
});
await new Promise(done=>server.listen(0,'127.0.0.1',done));
let browser;
try{
 browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
 const page=await browser.newPage(),errors=[];
 page.on('pageerror',e=>errors.push(e.message));
 for(const width of [393,320]){
  await page.setViewport({width,height:720,deviceScaleFactor:2,isMobile:true,hasTouch:true});
  await page.goto('http://127.0.0.1:'+server.address().port+'/segmented.html',{waitUntil:'networkidle0'});
  await page.waitForSelector('[aria-label="Weight unit"]');
  if(width===320)await page.evaluate(()=>document.documentElement.style.fontSize='32px');
  const geometry=await page.$$eval('.wpb-segmented>button',nodes=>nodes.map(el=>{
   const r=el.getBoundingClientRect();return {width:r.width,height:r.height,pressed:el.getAttribute('aria-pressed'),tag:el.tagName};
  }));
  assert.ok(geometry.every(g=>g.tag==='BUTTON'&&g.width>=48&&g.height>=48),
   'All accessible segment actions retain at least 48px hit bounds at '+width+': '+JSON.stringify(geometry));
  const groups=await page.$$eval('[role="group"]',nodes=>nodes.map(el=>({
   label:el.getAttribute('aria-label'),selected:[...el.querySelectorAll('button[aria-pressed="true"]')].length
  })));
  assert.equal(groups.length,4,'All four exclusive choice groups are rendered');
  assert.deepEqual(groups.map(x=>x.selected),[1,1,1,1]);
  await page.$$eval('[aria-label="Weight unit"] button',nodes=>nodes[0].click());
  assert.deepEqual(await page.evaluate(()=>window.settingsControlState()),{
    unit:'kg',inc:{v:2.5,unit:'lb'},effort:'rir',experience:'advanced'
  },'Unit change never silently rewrites the stored minimum increment');
  assert.equal(await page.$$eval('[aria-label="Smallest jump"] button[aria-pressed="true"]',nodes=>nodes.length),0,
   'Previously selected lb-specific microplate size is not shown selected under kg');
  await page.$$eval('[aria-label="Smallest jump"] button',nodes=>nodes[2].click());
  assert.deepEqual((await page.evaluate(()=>window.settingsControlState())).inc,{v:0.5,unit:'kg'},
   'New kg increment saves value plus correct unit');
  await page.$$eval('[aria-label="Effort scale"] button',nodes=>nodes[1].click());
  assert.equal((await page.evaluate(()=>window.settingsControlState())).effort,'rpe','Effort preference preserved');
  await page.focus('[aria-label="Training experience"] button:nth-child(3)');
  await page.keyboard.press('Enter');
  assert.equal((await page.evaluate(()=>window.settingsControlState())).experience,'intermediate',
   'Keyboard activation uses native button semantics');
  assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),
   'No page overflow at '+width+' (including 200% text)');
 }
 assert.deepEqual(errors,[],'No uncaught React/browser errors');
 console.log('PASS Settings segmented browser: 320/393, 200% type, 48px hits, keyboard and unit-scoped increments');
}finally{await browser?.close();await new Promise(done=>server.close(done));}
