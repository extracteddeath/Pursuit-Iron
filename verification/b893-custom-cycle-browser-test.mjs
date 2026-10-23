import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import puppeteer from 'puppeteer-core';
const root=path.resolve(new URL('../',import.meta.url).pathname);
const config=JSON.parse(fs.readFileSync(path.join(root,'verification/m205-generation-parity-results.json'))).rows[3].config;
const importMap=fs.readFileSync(path.join(root,'index.html'),'utf8').match(/<script type="importmap">[\s\S]*?<\/script>/)[0];
const html=`<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="/app.css">${importMap}</head><body><div id="root"></div><script type="module">
import React from '/vendor/react.js';import {createRoot} from '/vendor/react-dom-client.js';
import {Wizard,StyleTag,EXERCISES} from '/modules/App.js';
import {generateNextCycleForShell} from '/modules/next-engine/cycle-runtime-adapter.js';
createRoot(document.getElementById('root')).render(React.createElement('div',{className:'wpb'},React.createElement(StyleTag),React.createElement(Wizard,{initialEquipment:${JSON.stringify(config.equipment)},initialExperience:'intermediate',initialUnit:'lb',skipUnits:true,cycleMode:true,onCancel:()=>{},onDone:cfg=>{if(window.failNextBuild){window.failNextBuild=false;window.failed=true;return null;}window.chosen=cfg;let n=0;window.built=generateNextCycleForShell({templateId:cfg.cycleTemplate,blocks:cfg.cycleBlocks,config:cfg,legacyExercises:EXERCISES,seed:893,makeId:()=> 'b893-'+(++n),adaptBetweenBlocks:cfg.cycleAdapt,adaptTrainingSplit:cfg.cycleAdaptSplit});}})));
</script></body></html>`;
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html','.json':'application/json'};
const server=http.createServer((req,res)=>{const url=new URL(req.url,'http://localhost');if(url.pathname==='/probe.html'){res.setHeader('Content-Type','text/html');res.end(html);return;}const file=path.resolve(root,'.'+url.pathname);if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){res.writeHead(404);res.end();return;}res.setHeader('Content-Type',mime[path.extname(file)]||'application/octet-stream');res.end(fs.readFileSync(file));});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));let browser;
try{
 browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
 const page=await browser.newPage(),errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.setViewport({width:390,height:844,isMobile:true,hasTouch:true});
 await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
 await page.goto(`http://127.0.0.1:${server.address().port}/probe.html`,{waitUntil:'networkidle0'});
 await page.waitForSelector('.wpb-wizard-cycle-option');
 await page.$$eval('.wpb-wizard-cycle-option',ns=>ns.find(n=>n.textContent.includes('Custom cycle')).click());
 const field=i=>`[aria-label="Block ${i} weeks"]`;
 const replace=async(i,value)=>{await page.click(field(i));await page.keyboard.down('Control');await page.keyboard.press('KeyA');await page.keyboard.up('Control');await page.keyboard.press('Backspace');if(value)await page.type(field(i),value);};
 for(const value of ['', '0','1.5','25']){await replace(1,value);assert.equal(await page.$eval('.wpb-wizard-next-action',n=>n.disabled),true,'invalid duration '+value+' blocks Next');}
 await replace(1,'7');await page.select('[aria-label="Block 1 training phase"]','hypertrophy_accumulation');
 await page.select('[aria-label="Block 2 training phase"]','peak');await replace(2,'1');
 await page.click('[aria-label="Move block 2 earlier"]');
 assert.equal(await page.$eval(field(1),n=>n.value),'1');
 assert.equal(await page.$eval('[aria-label="Block 1 training phase"]',n=>n.value),'peak');
 await page.select('[aria-label="Block 3 training phase"]','hypertrophy_accumulation');await replace(3,'3');
 await page.reload({waitUntil:'networkidle0'});await page.waitForSelector('.wpb-custom-cycle');
 assert.equal(await page.$eval(field(1),n=>n.value),'1','custom block order/durations recover after reload');
 await replace(3,'');await page.reload({waitUntil:'networkidle0'});await page.waitForSelector('.wpb-custom-cycle');
 assert.equal(await page.$eval(field(3),n=>n.value),'','incomplete drafts stay editable after reload');assert.equal(await page.$eval('.wpb-wizard-next-action',n=>n.disabled),true);await replace(3,'3');
 await page.click('.wpb-custom-cycle-add');assert.equal(await page.$$eval('.wpb-custom-cycle-block',ns=>ns.length),4);
 await page.click('[aria-label="Remove block 4"]');
 for(const width of [320,390,900]){
  await page.setViewport({width,height:844,isMobile:true,hasTouch:true});
  assert.equal(await page.$$eval('.wpb-custom-cycle input,.wpb-custom-cycle select,.wpb-custom-cycle-block-head button',ns=>ns.every(n=>{const r=n.getBoundingClientRect();return r.left>=0&&r.right<=innerWidth;})),true,'controls fit '+width);
 }
 // Back preserves authored block drafts and custom-cycle goal is an actual step.
 await page.click('.wpb-wizard-next-action');await page.waitForSelector('[data-step-key="goal"]');
 await page.click('.wpb-wizard-back-action');await page.waitForSelector('.wpb-custom-cycle');
 assert.equal(await page.$eval(field(1),n=>n.value),'1');await page.click('.wpb-wizard-next-action');
 await page.waitForSelector('[data-step-key="goal"]');
 await page.$$eval('.wpb-wizard-option',ns=>ns.find(n=>/strength/i.test(n.textContent)&&!/both/i.test(n.textContent)).click());
 for(let step=0;step<20&&!await page.evaluate(()=>!!window.built);step++){
  const key=await page.$eval('[data-step-key]',n=>n.dataset.stepKey);
  if(key==='deload')await page.evaluate(()=>window.failNextBuild=true);
  if(key==='name')await page.type('input[aria-label="Cycle name"]','Custom phase probe');
  await page.waitForFunction(()=>!document.querySelector('.wpb-wizard-next-action').disabled,{timeout:60000});
  await page.click('.wpb-wizard-next-action');
  await page.waitForFunction(old=>window.built||window.failed||document.querySelector('[data-step-key]')?.dataset.stepKey!==old,{timeout:60000},key);
  if(await page.evaluate(()=>!!window.failed))break;
 }
 if(await page.evaluate(()=>!!window.failed)){
  assert.equal(await page.evaluate(()=>!!JSON.parse(localStorage.getItem('wpb:cycle-setup:v1')||'{}')['wizard:cycle']),true,'failed builds keep the recoverable draft');
  await page.waitForSelector('.wpb-build-alert');await page.click('.wpb-wizard-next-action');await page.waitForFunction(()=>!!window.built,{timeout:60000});
 }
 const actual=await page.evaluate(()=>({goal:window.chosen.goal,adaptSplit:window.built.cycle.adaptTrainingSplit,decisions:window.built.cycle.blockMeta.map(b=>b.splitDecision.mode),selected:window.chosen.cycleBlocks,meta:window.built.cycle.blockMeta.map(b=>({phase:b.phase,weeks:b.weeks})),blocks:window.built.blocks.map(b=>({phase:b.nextEngine.program.phase,weeks:b.config.weeks}))}));
 assert.equal(actual.goal,'strength');
 assert.equal(actual.adaptSplit,true,'the wizard hands automatic split adaptation to real cycle generation');
 assert.ok(actual.decisions.every(mode=>mode==='goal_optimized'),'every automatic block is actually optimized');
 assert.deepEqual(actual.selected,[{phase:'peak',weeks:1},{phase:'hypertrophy_accumulation',weeks:7},{phase:'hypertrophy_accumulation',weeks:3}]);
 assert.deepEqual(actual.meta,actual.selected);assert.deepEqual(actual.blocks,actual.selected);assert.deepEqual(errors,[]);
 assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:cycle-setup:v1')||'{}')['wizard:cycle']),undefined,'successful creation clears its setup draft');
 await page.reload({waitUntil:'networkidle0'});await page.waitForSelector('.wpb-wizard-cycle-option');await page.$$eval('.wpb-wizard-cycle-option',ns=>ns.find(n=>n.textContent.includes('Custom cycle')).click());await page.waitForSelector('.wpb-custom-cycle');
 await page.evaluate(()=>[...document.querySelectorAll('button')].find(b=>b.textContent==='Discard setup and close').click());
 assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:cycle-setup:v1')||'{}')['wizard:cycle']),undefined,'explicit discard clears the setup');
 console.log('PASS B893 browser: editable invalid drafts, block reorder/add/remove, responsive controls, Back retention, explicit goal and exact generated block sequence.');
}finally{if(browser)await browser.close();await new Promise(resolve=>server.close(resolve));}
