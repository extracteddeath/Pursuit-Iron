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
 const [programs,setPrograms]=React.useState(stored),[draft,setDraft]=React.useState(null);
 const n=Number(params.get('index')||0),program=programs[n];
 const cfg=draft||{...program.config,folder:'',description:''};
 const common={cycles:[cycle],cycle,saved:programs,history:[],onBack:noop,onNew:noop,onOpenBlock:noop,onCompleteBlock:noop,onDelete:noop,onOpenCycle:noop,onReview:noop,onAdvance:noop,onNextWorkout:noop};
 const apply=()=>{const p={...program,weeks:cfg.weeks,config:{...program.config,...cfg}};const next=programs.map((v,i)=>i===n?p:v);localStorage.setItem('m215-programs',JSON.stringify(next));setPrograms(next);setDraft(null);};
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
  assert.equal(await page.$eval('button[aria-label="'+weeks+' weeks"]',n=>n.getAttribute('aria-pressed')),'true');
  if(index>0){
   assert.equal(await page.$eval('button[aria-label="'+weeks+' weeks"]',n=>n.disabled),true);
   assert.match(await page.evaluate(()=>document.body.innerText),/Set by this phase/);
  }
 }
 // Custom block lengths remain editable; the canonical saved value survives reload with stale metadata.
 await open('screen=settings&index=0');
 await page.click('button[aria-label="3 weeks"]');
 await page.waitForSelector('button[aria-label="3 weeks"][aria-pressed="true"]');
 await page.$$eval('button',nodes=>nodes.find(n=>/Save/.test(n.textContent)&&!n.disabled).click());
 await page.waitForFunction(()=>JSON.parse(localStorage.getItem('m215-programs'))[0].config.weeks===3);
 await page.reload({waitUntil:'networkidle0'});
 assert.equal(await page.$eval('button[aria-label="3 weeks"]',n=>n.getAttribute('aria-pressed')),'true');
 await page.setViewport({width:360,height:844,deviceScaleFactor:2,isMobile:true,hasTouch:true});
 assert.equal(await page.$$eval('button[aria-label$=" weeks"]',nodes=>nodes.every(n=>{const r=n.getBoundingClientRect();return r.left>=0&&r.right<=innerWidth;})),true,'all duration choices fit at 360px');
 await page.screenshot({path:path.join(root,'verification/m215-cycle-settings-phone.png'),fullPage:true});
 await open('screen=cycles');
 assert.doesNotMatch(await page.evaluate(()=>document.body.innerText),/10w|6w|6 weeks/);
 assert.deepEqual(errors,[]);
 console.log('PASS M215 phone browser: saved ten-week block reads consistently in cycle list/detail/Home; 5/3-week cycle settings show their actual phase length; custom edits survive reload and fit at 360px.');
}finally{if(browser)await browser.close();await new Promise(resolve=>server.close(resolve));}
