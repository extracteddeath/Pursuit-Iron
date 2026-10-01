import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import puppeteer from 'puppeteer-core';
import { savedPrograms, staleCycle } from './m215-cycle-duration-fixture.mjs';

const root = path.resolve(new URL('../', import.meta.url).pathname);
const importMap = fs.readFileSync(path.join(root, 'index.html'), 'utf8').match(/<script type="importmap">[\s\S]*?<\/script>/)?.[0];
assert.ok(importMap);
const template = `<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="/app.css">${importMap}</head><body><div id="root"></div><script type="module">
import React from '/vendor/react.js';import {createRoot} from '/vendor/react-dom-client.js';
import {HomePrograms,CyclesView,CycleDetail,ProgramSettingsSheet,StyleTag} from '/modules/App.js';
const params=new URL(location.href).searchParams,screen=params.get('screen')||'cycles';
const stored=JSON.parse(localStorage.getItem('m215-programs')||'null')||${JSON.stringify(savedPrograms)};
const cycle=${JSON.stringify(staleCycle)};
localStorage.setItem('m215-programs',JSON.stringify(stored));
const noop=()=>{};
function Probe(){
 const n=Number(params.get('index')||0);
 const [programs,setPrograms]=React.useState(stored),[draft,setDraft]=React.useState({...stored[n].config,folder:'',description:''});
 const program=programs[n];
 const cfg=draft||{...program.config,folder:'',description:''};
 const common={cycles:[cycle],cycle,saved:programs,history:[],onBack:noop,onNew:noop,onOpenBlock:noop,onCompleteBlock:noop,onDelete:noop,onOpenCycle:noop,onReview:noop,onAdvance:noop,onNextWorkout:noop};
 const apply=()=>{const p={...program,weeks:cfg.weeks,config:{...program.config,...cfg}};const next=programs.map((v,i)=>i===n?p:v);localStorage.setItem('m215-programs',JSON.stringify(next));setPrograms(next);setDraft({...p.config,folder:'',description:''});};
 const content=screen==='home'?React.createElement(HomePrograms,{...common,activeId:programs[0].id,onOpen:noop,onOpenCycles:noop,onSetActive:noop,onCompare:noop,onOptions:noop})
 :screen==='detail'?React.createElement(CycleDetail,common)
 :screen==='settings'?React.createElement(ProgramSettingsSheet,{program,draft:cfg,setDraft,dirty:cfg.weeks!==program.config.weeks,onApply:apply,onClose:noop})
 :React.createElement(CyclesView,common);
 return React.createElement('div',{className:'wpb',style:{height:'100%'}},React.createElement(StyleTag),content);
}
createRoot(document.getElementById('root')).render(React.createElement(Probe));
</script></body></html>`;
const mime={'.js':'text/javascript','.mjs':'text/javascript','.css':'text/css','.html':'text/html','.json':'application/json','.png':'image/png','.svg':'image/svg+xml'};
const server=http.createServer((req,res)=>{
 const pathname=new URL(req.url,'http://localhost').pathname;
 if(pathname==='/probe.html'){res.setHeader('Content-Type','text/html');res.end(template);return;}
 const file=path.resolve(root,'.'+pathname);
 if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){res.writeHead(404);res.end();return;}
 res.setHeader('Content-Type',mime[path.extname(file)]||'application/octet-stream');res.end(fs.readFileSync(file));
});
await new Promise(resolve=>server.listen(8771,'127.0.0.1',resolve));
let browser;
try{
 browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
 const page=await browser.newPage(),errors=[];
 page.on('pageerror',e=>errors.push(e.message));
 await page.setViewport({width:390,height:844,deviceScaleFactor:2,isMobile:true,hasTouch:true});
 const open=async query=>{await page.goto('http://127.0.0.1:8771/probe.html?'+query,{waitUntil:'networkidle0'});};
 await open('screen=cycles');
 await page.waitForSelector('.wpb-cycle-block-list');
 let text=await page.evaluate(()=>document.body.innerText);
 assert.match(text,/10w/);assert.match(text,/5w/);assert.match(text,/3w/);assert.doesNotMatch(text,/6 weeks|6w/);
 assert.match(text,/of 19/,'cycle calendar includes the ten-week block plus its separate deload');
 await page.screenshot({path:path.join(root,'verification/m215-cycle-list-phone.png'),fullPage:true});
 await open('screen=detail');
 await page.waitForSelector('.wpb-cycle-stats');
 text=await page.evaluate(()=>document.body.innerText);
 assert.match(text,/10w/);assert.match(text,/5w/);assert.match(text,/3w/);assert.doesNotMatch(text,/6w/);
 await open('screen=home');
 await page.waitForSelector('.hp-card');
 assert.match(await page.evaluate(()=>document.body.innerText),/10 weeks/);
 await page.$$eval('button',nodes=>nodes.find(n=>n.textContent==='View blocks').click());
 assert.match(await page.evaluate(()=>document.body.innerText),/5 weeks/);
 assert.match(await page.evaluate(()=>document.body.innerText),/3 weeks/);
 for(const [index,weeks] of [[0,10],[1,5],[2,3]]){
  await open('screen=settings&index='+index);
  await page.waitForSelector('[data-testid="program-settings-sheet"]');
  if(index>0){
   assert.match(await page.$eval('[data-testid="cycle-phase-duration"]',n=>n.innerText),new RegExp(weeks+' weeks'));
   assert.equal(await page.$('input[aria-label="Block length in weeks"]'),null,'cycle-owned phases display their length without an editable field');
   assert.equal(await page.$('button[aria-label="Increase block length"]'),null);
   assert.match(await page.evaluate(()=>document.body.innerText),/Set by this phase/);
  }else{
   assert.equal(await page.$eval('input[aria-label="Block length in weeks"]',n=>n.value),String(weeks));
  }
 }
 // Exercise the production draft control through actual keystrokes, including incomplete replacements.
 await open('screen=settings&index=0');
 const field='input[aria-label="Block length in weeks"]',save='button[aria-label="Save program settings"]';
 await page.waitForSelector(field);
 const readWeeks=()=>page.$eval(field,n=>n.value);
 const replaceWeeks=async value=>{await page.click(field,{clickCount:3});await page.keyboard.press('Backspace');if(value)await page.type(field,value);};
 assert.equal(await page.$('button[aria-label="3 weeks"]'),null,'a single field replaces the growing preset row');
 await page.click('button[aria-label="Increase block length"]');assert.equal(await readWeeks(),'11');
 await page.click('button[aria-label="Decrease block length"]');assert.equal(await readWeeks(),'10');
 await page.focus(field);await page.keyboard.press('ArrowDown');assert.equal(await readWeeks(),'9');
 await page.keyboard.press('ArrowUp');assert.equal(await readWeeks(),'10');
 assert.equal(await page.$eval(save,n=>n.disabled),true,'returning to the saved length leaves no change');
 for(const value of ['', '0', '-1', '3.5', 'abc', '9007199254740992']){
  await replaceWeeks(value);assert.equal(await readWeeks(),value,'invalid or incomplete text stays editable');
  assert.equal(await page.$eval(save,n=>n.disabled),true,'incomplete and invalid lengths cannot be saved');
  assert.equal(await page.$eval(field,n=>n.getAttribute('aria-invalid')),'true');
  assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('m215-programs'))[0].config.weeks),10,'typing never changes the saved program');
 }
 for(const weeks of [1,2,7,9,12,3]){
  await replaceWeeks(String(weeks));assert.equal(await readWeeks(),String(weeks));
  if(weeks===1){
   assert.equal(await page.$eval('button[aria-label="Decrease block length"]',n=>n.disabled),true);
   await page.keyboard.press('ArrowDown');assert.equal(await readWeeks(),'1','decrement cannot produce a zero-week program');
  }
  await page.click(save);
  await page.waitForFunction(w=>{const p=JSON.parse(localStorage.getItem('m215-programs'))[0];return p.config.weeks===w&&p.weeks===w;},{},weeks);
  await page.reload({waitUntil:'networkidle0'});assert.equal(await readWeeks(),String(weeks),'custom duration survives reload');
 }
 for(const width of [320,360,390]){
  await page.setViewport({width,height:844,deviceScaleFactor:2,isMobile:true,hasTouch:true});
  assert.equal(await page.$eval('[data-testid="program-duration-control"]',n=>{const r=n.getBoundingClientRect();return r.left>=0&&r.right<=innerWidth;}),true,'duration field fits at '+width+'px');
  assert.equal(await page.$$eval('[data-testid="program-duration-control"] button',nodes=>nodes.length===2&&nodes.every(n=>{const r=n.getBoundingClientRect();return r.width>=44&&r.height>=44;})),true,'step buttons retain usable touch targets');
 }
 await page.screenshot({path:path.join(root,'verification/m216-compact-duration-phone.png'),fullPage:true});
 await open('screen=cycles');
 assert.doesNotMatch(await page.evaluate(()=>document.body.innerText),/10w|6w|6 weeks/);
 assert.deepEqual(errors,[]);
 console.log('PASS M215/M216 phone browser: ten-week cycle duration is consistent; cycle-owned 5/3-week phases display their length; compact stepping, typing, invalid/blank drafts and uncommon custom lengths save/reload correctly at 320–390px.');
}finally{if(browser)await browser.close();await new Promise(resolve=>server.close(resolve));}
