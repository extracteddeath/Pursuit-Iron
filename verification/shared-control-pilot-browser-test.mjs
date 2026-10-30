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
"import {UiAction,UiChoice} from '/modules/ui-primitives.js';",
"import {ProgramSettingsSheet} from '/modules/program-settings-sheet.js';",
"const program={id:'pilot',custom:true,weeks:4,config:{unit:'lb',weeks:4,goal:'both',progression:'manual',volumeApproach:'standard'},days:[{id:'upper',label:'Upper',exercises:['bench']}]};",
"window.saved=0;window.savedDraft=null;",
"function Demo(){const [open,setOpen]=React.useState(false);const [plate,setPlate]=React.useState(true);const [filter,setFilter]=React.useState('all');const [dirty,setDirty]=React.useState(false);",
"const [draft,setDraft]=React.useState({weeks:4,goal:'both',progression:'manual',volumeApproach:'standard',folder:'',description:'',noBodyweight:false,noSupersets:false,barbellCap:null});",
"const change=fn=>{setDraft(prev=>typeof fn==='function'?fn(prev):fn);setDirty(true)};",
"return React.createElement('main',{className:'wpb'},",
"React.createElement(UiAction,{variant:'tonal',id:'pilot-settings-link','data-align':'start',className:'wpb-settings-link',onClick:()=>setOpen(true)},'Edit program settings'),",
"React.createElement('div',{role:'group','aria-label':'Available lb plates per side',className:'wpb-settings-plates-card'},React.createElement(UiChoice,{kind:'chip',className:'wpb-settings-plate-choice',selected:plate,'aria-label':'5 lb plate',onClick:()=>setPlate(x=>!x)},'5')),",
"React.createElement('div',{role:'group','aria-label':'Split filter'},['all','upper'].map(k=>React.createElement(UiChoice,{kind:'chip',className:'wpb-gallery-chip',selected:filter===k,onClick:()=>setFilter(k),key:k},k))),",
"open&&React.createElement(ProgramSettingsSheet,{program,draft,setDraft:change,dirty,onApply:()=>{window.saved++;window.savedDraft=draft;setOpen(false)},onClose:()=>setOpen(false),GOALS:{both:{label:'Both'},strength:{label:'Strength'},hypertrophy:{label:'Hypertrophy'}},selectNumericInput:e=>e.currentTarget.select()}));}",
"createRoot(document.getElementById('root')).render(React.createElement(Demo));"
].join('\n');
const html='<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="/app.css">'+importMap+'</head><body><div id="root" data-pi-motion="expressive"></div><script type="module">'+fixture+'</script></body></html>';
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html'};
const server=http.createServer((req,res)=>{
 const name=new URL(req.url,'http://localhost').pathname;
 if(name==='/pilot.html'){res.setHeader('Content-Type','text/html');res.end(html);return}
 const file=path.resolve(root,'.'+name);
 if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){res.writeHead(404);res.end();return}
 res.setHeader('Content-Type',mime[path.extname(file)]||'application/octet-stream');
 res.end(fs.readFileSync(file));
});
await new Promise(done=>server.listen(0,'127.0.0.1',done));
let browser;
try{
 browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
 const page=await browser.newPage(),errors=[];
 page.on('pageerror',e=>errors.push(e.message));
 for(const width of [393,320]){
  await page.setViewport({width,height:width===320?560:780,deviceScaleFactor:2,isMobile:true,hasTouch:true});
  await page.goto('http://127.0.0.1:'+server.address().port+'/pilot.html',{waitUntil:'networkidle0'});
  if(width===320)await page.evaluate(()=>document.documentElement.style.fontSize='32px');
  await page.waitForSelector('#pilot-settings-link');
  const plate='[aria-label="5 lb plate"]';
  assert.equal(await page.$eval(plate,el=>el.getAttribute('aria-pressed')),'true');
  await page.click(plate);
  assert.equal(await page.$eval(plate,el=>el.getAttribute('aria-pressed')),'false','Plate selection is spoken and reversible');
  await page.click('[aria-label="Split filter"] button:nth-child(2)');
  assert.equal(await page.$eval('[aria-label="Split filter"] button:nth-child(2)',el=>el.getAttribute('aria-pressed')),'true','Gallery-style chip uses the same accessible selection');
  await page.click('#pilot-settings-link');
  await page.waitForSelector('[data-testid="program-settings-sheet"]');
  const actions=await page.$$eval('.wpb-program-settings-footer .wpb-ui-action',nodes=>nodes.map(el=>{
   const r=el.getBoundingClientRect();return {action:el.dataset.action,height:r.height,width:r.width,left:r.left,right:r.right,disabled:el.disabled}
  }));
  assert.deepEqual(actions.map(x=>x.action),['tonal','primary'],'Program footer uses shared action roles');
  assert.equal(actions[1].disabled,true,'Unchanged program cannot be saved');
  assert.ok(actions.every(x=>x.height>=48&&x.width>=48&&x.left>=-1&&x.right<=width+1),'Footer actions remain reachable: '+JSON.stringify(actions));
  assert.ok(actions[1].height>=52,'Primary action is at least 52px');
  const weeks='[aria-label="Block length in weeks"]';
  // Use real keys so React receives the raw invalid draft; never mutate input.value manually.
  await page.click(weeks);
  await page.keyboard.press('ControlOrMeta+A');
  await page.keyboard.press('Backspace');
  await page.waitForFunction(()=>document.querySelector('[aria-label="Block length in weeks"]')?.getAttribute('aria-invalid')==='true');
  assert.equal(await page.$eval('.wpb-program-settings-save',el=>el.disabled),true,'Invalid raw weeks cannot save');
  await page.type(weeks,'5');
  await page.type('#program-settings-folder','Pilot');
  await page.waitForFunction(()=>document.querySelector('.wpb-program-settings-save')?.disabled===false);
  await page.click('.wpb-program-settings-save');
  assert.equal(await page.evaluate(()=>window.saved),1,'Apply callback runs once');
  assert.equal(await page.evaluate(()=>window.savedDraft.folder),'Pilot','Raw draft transaction is preserved');
  await page.click('#pilot-settings-link');
  await page.waitForSelector('[data-testid="program-settings-sheet"]');
  await page.keyboard.press('Escape');
  await page.waitForFunction(()=>!document.querySelector('[data-testid="program-settings-sheet"]'));
  assert.equal(await page.$eval('#pilot-settings-link',el=>el===document.activeElement),true,'Escape restores the opener');
  assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),'Pilot has no horizontal page clipping at '+width);
 }
 assert.deepEqual(errors,[],'No runtime errors in shared-control pilot');
 console.log('PASS shared-control pilot browser: semantic chips, editable program draft, action sizes and keyboard at 320/393px.');
} finally {await browser?.close();await new Promise(done=>server.close(done))}
