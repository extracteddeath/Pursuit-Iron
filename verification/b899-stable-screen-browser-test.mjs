import assert from 'node:assert/strict';
import fs from 'node:fs';import http from 'node:http';import path from 'node:path';import puppeteer from 'puppeteer-core';
const root=path.resolve(new URL('../',import.meta.url).pathname);
const equipment=['barbell','rack','bench','inclinebench','dumbbell','cable','machine','smith','pullup','dip','legpress','hacksquat','legext','legcurl','calfmachine'];
const program={id:'steady',custom:true,name:'Saved before creation',weeks:6,config:{unit:'lb',goal:'both',progression:'double',experience:'intermediate'},days:[{id:'d',label:'Upper',primaryIndex:0,exercises:['bb-bench','inc-curl']}],overrides:{'d:0':{sets:3,reps:'5-8',rir:'2'},'d:1':{sets:3,reps:'8-12',rir:'2'}}};
const seed={v:13,savedAt:1,saved:[program],history:[],cycles:[],perf:{},drafts:{},tombs:{},pinnedId:program.id,theme:'amethyst',unit:'lb',unitChosen:true,experience:'intermediate',seenIntro:999,seenWhatsNew:999,equipDefault:equipment,gyms:[{id:'gym',name:'Full gym',equipment,limits:{},inventory:{},limitUnit:'lb'}],activeGymId:'gym',lastBackup:Date.now(),installDismissedAt:Date.now()};
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html','.json':'application/json','.webmanifest':'application/manifest+json','.png':'image/png','.svg':'image/svg+xml'};
const server=http.createServer((req,res)=>{let name=new URL(req.url,'http://localhost').pathname;if(name==='/')name='/index.html';const file=path.resolve(root,'.'+name);if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){res.writeHead(404);res.end();return;}res.setHeader('Content-Type',mime[path.extname(file)]||'application/octet-stream');let bytes=fs.readFileSync(file);if(name==='/modules/generation-worker.js')bytes=Buffer.from(bytes+'\nconst until=performance.now()+900;while(performance.now()<until){}\n');res.end(bytes);});
await new Promise(r=>server.listen(0,'127.0.0.1',r));
const origin=`http://127.0.0.1:${server.address().port}/`;
let browser;
try{
 browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
 for(const [width,theme,reduced,cycle=false,empty=false] of [[320,'amethyst',false],[430,'rosewater',false],[900,'amethyst',false],[393,'rosewater',true],[430,'rosewater',false,true],[320,'rosewater',true,false,true]]){
  const context=await browser.createBrowserContext(),page=await context.newPage(),errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.setViewport({width,height:880,deviceScaleFactor:1,isMobile:width<600,hasTouch:true});
  await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:reduced?'reduce':'no-preference'}]);
  await page.evaluateOnNewDocument((seed,theme)=>{if(!localStorage.getItem('b899-seeded')){localStorage.setItem('wpb:v1',JSON.stringify({...seed,theme}));localStorage.setItem('b899-seeded','1');}},{...seed,saved:empty?[]:seed.saved,pinnedId:empty?null:seed.pinnedId},theme);
  await page.goto(origin,{waitUntil:'networkidle0'});await page.waitForSelector('#root[data-pi-motion="expressive"]');
  await page.evaluate(()=>{
   window.__screenFrames=[];
   const sample=()=>{const f=document.querySelector('[data-view-frame]'),step=document.querySelector('.wpb-wizard-step');if(f){const c=getComputedStyle(f),s=step&&getComputedStyle(step);window.__screenFrames.push({view:f.dataset.viewFrame,opacity:+c.opacity,scale:c.scale,translate:c.translate,transform:c.transform,step:step?.dataset.stepKey,stepOpacity:s?+s.opacity:1,modal:!!document.querySelector('[data-generation-busy].wpb-backdrop')});}window.__screenRaf=requestAnimationFrame(sample);};window.__screenRaf=requestAnimationFrame(sample);
  });
  const click=async label=>{await page.waitForFunction(label=>[...document.querySelectorAll('button')].some(n=>n.textContent.trim()===label||n.textContent.trim().startsWith(label)),{},label);await page.evaluate(label=>[...document.querySelectorAll('button')].find(n=>n.textContent.trim()===label||n.textContent.trim().startsWith(label)).click(),label);};
  for(const tab of ['plan','progress','profile','settings','home']){
   await page.click(`[data-tab="${tab}"]`);await page.waitForSelector(`[data-view-frame="${tab}"]`);await page.evaluate(()=>new Promise(r=>setTimeout(r,190)));
   if(tab==='settings'){
    const prefs=await page.evaluate(()=>{
     const group=[...document.querySelectorAll('.wpb-settings .wpb-segmented')].find(n=>n.querySelector('button[aria-pressed]'));
     if(!group)return null;
     const button=group.querySelector('button[aria-pressed]'),rect=button.getBoundingClientRect(),computed=getComputedStyle(button);
     return {height:rect.height,transition:computed.transitionProperty,scrollWidth:document.documentElement.scrollWidth,viewport:innerWidth};
    });
    assert.ok(prefs,'Settings exposes selected preference controls');
    assert.ok(prefs.height>=48,`Settings selected preference has a real 48px hit target: ${JSON.stringify(prefs)}`);
    assert.ok(reduced?prefs.transition==='none':prefs.transition.includes('background-color'),'Settings only animates paint on selection');
    assert.ok(prefs.scrollWidth<=prefs.viewport+1,'Settings selection does not widen phone viewport');
   }
  }
  if(empty){await page.click('[data-tab="plan"]');await click('Create a program');}
  else await click('New program');
  await page.waitForSelector('.wpb-wizard-step');
  if(cycle)await click('Training cycle');
  await click('Continue');await page.waitForFunction(()=>document.querySelector('.wpb-wizard-step')?.dataset.stepKey!=='mode');
  const headerBaseline=await page.evaluate(()=>{
   const h=document.querySelector('.wpb-wizard-header'),row=h.firstElementChild,back=h.querySelector('button[aria-label="Back"]'),bar=h.querySelector('.wpb-wizard-progress');
   return {rowHeight:row.getBoundingClientRect().height,backHeight:back.getBoundingClientRect().height,
    backWidth:back.getBoundingClientRect().width,progressY:bar.getBoundingClientRect().y,headerY:h.getBoundingClientRect().y};
  });
  assert.ok(headerBaseline.rowHeight>=48&&headerBaseline.backHeight>=48&&headerBaseline.backWidth>=48,
   `Stable creation header and real 48px Back target: ${JSON.stringify(headerBaseline)}`);
  // Reach every real step. Selection, text entry, async feasibility and Back use the actual controls.
  const steps=[];
  for(let i=0;i<25;i++){
   const key=await page.$eval('.wpb-wizard-step',n=>n.dataset.stepKey);steps.push(key);
   const headerNow=await page.evaluate(()=>{const h=document.querySelector('.wpb-wizard-header');return {progressY:h.querySelector('.wpb-wizard-progress').getBoundingClientRect().y,headerY:h.getBoundingClientRect().y};});
   assert.ok(Math.abs(headerNow.progressY-headerBaseline.progressY)<=2&&Math.abs(headerNow.headerY-headerBaseline.headerY)<=2,
    `Wizard header/progress stay stationary across ${key}: ${JSON.stringify(headerNow)}`);
   if(key==='name'){
    const field=await page.$('.wpb-wizard-step input');await field.type('Steady creation');
    assert.equal(await page.$eval('.wpb-wizard-step input',n=>n.value),'Steady creation');
   }
   if(key==='focus'){
    await page.click('[aria-label="Increase Chest focus"]');
    const step=await page.$('.wpb-wizard-step');await page.evaluate(()=>new Promise(r=>setTimeout(r,100)));
    assert.equal(await step.evaluate(n=>n.isConnected),true,'selection does not remount the step');
   }
   await page.waitForFunction(()=>!document.querySelector('.wpb-wizard-next-action')?.disabled,{timeout:60000});
   const label=await page.$eval('.wpb-wizard-next-action',n=>n.textContent.trim());
   if(label==='Generate Program'||label==='Build Cycle')break;
   await page.click('.wpb-wizard-next-action');await page.waitForFunction(old=>document.querySelector('.wpb-wizard-step')?.dataset.stepKey!==old,{},key);
  }
  assert.ok(steps.includes('name')&&steps.includes('split')&&steps.includes('session')&&steps.includes('focus'));
  await page.click('.wpb-wizard-next-action');await page.waitForSelector('.wpb-wizard-footer [data-generation-busy]');
  assert.equal(await page.$('[data-generation-busy].wpb-backdrop'),null,'creating a program never dims the setup screen');
  await page.click('[data-cancel-generation]');await page.waitForSelector('[data-generation-busy]',{hidden:true});
  await page.waitForFunction(()=>!document.querySelector('.wpb-wizard-next-action').disabled);
  assert.deepEqual((await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')))).saved,empty?[]:[program],'cancel preserves saved plans');
  assert.equal(await page.$('.wpb-build-alert'),null,'cancellation is not a capacity failure');
  await page.click('.wpb-wizard-next-action');await page.waitForSelector('.wpb-wizard-footer [data-generation-busy]');
  await page.waitForSelector('.wpb-program',{timeout:60000});await page.waitForSelector('[data-generation-busy]',{hidden:true});
  await page.evaluate(()=>new Promise(r=>setTimeout(r,300)));
  const frames=await page.evaluate(()=>{cancelAnimationFrame(window.__screenRaf);return window.__screenFrames;});
  const bad=frames.filter(f=>f.opacity!==1||f.stepOpacity!==1||!['none','1'].includes(f.scale)||f.translate!=='none'||f.transform!=='none'||f.modal);
  assert.deepEqual(bad,[],`opaque first paint and stable viewport geometry at ${width}/${theme}: ${JSON.stringify(bad.slice(0,4))}`);
  const store=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')));
  assert.deepEqual(store.saved.find(p=>p.id===program.id),empty?undefined:program);assert.deepEqual(store.history,[]);
  if(cycle)assert.equal(store.cycles.length,1,'cycle build completes through the same steady flow');
  assert.ok((await page.$eval('.wpb-program',n=>n.textContent)).includes('Steady creation'),'built plan opens directly');
  if(cycle){
   await page.click('.wpb-program-name');await page.waitForSelector('input[aria-label="Program name"]');
   await page.type('input[aria-label="Program name"]',' · Draft review');await page.click('[aria-label="Save name"]');
  }
  let expectedPreview=await page.evaluate(()=>{
   const buttons=[...document.querySelectorAll('.wpb-day-start')].filter(b=>!b.disabled);
   const chosen=buttons.at(-1);chosen.dataset.m351Start='true';
   return {name:document.querySelector('.wpb-program-name-text').textContent,
    day:chosen.getAttribute('aria-label').slice('Start '.length),
    dayId:chosen.closest('[data-daycard]').id.slice('pv-day-'.length)};
  });
  await page.screenshot({path:`verification/m351-created-${width}-${cycle?'cycle':'program'}-phone.png`});
  const chosen=await page.$('[data-m351-start]');await chosen.evaluate(n=>n.scrollIntoView({block:'center'}));await chosen.click();
  await page.waitForSelector('.wpb-next-workout-view');
  assert.equal(await page.evaluate(()=>{
   const footer=document.querySelector('.wpb-next-workout-actions').getBoundingClientRect();
   return [...document.querySelectorAll('.wpb-notice')].every(n=>
    getComputedStyle(n).visibility==='hidden'||n.getBoundingClientRect().bottom<=footer.top-6);
  }),true,'Creation notices clear the persistent Start footer');
  await page.screenshot({path:`verification/m351-selected-preview-${width}-${cycle?'cycle':'program'}-phone.png`});
  assert.equal(await page.$eval('.wpb-preview-program-name',n=>n.textContent),expectedPreview.name,
   'A created program must not preview the previously active program');
  assert.equal(await page.$eval('.wpb-next-workout-view h1',n=>n.getAttribute('aria-label')),expectedPreview.day,
   'Full Program previews the selected day, including days other than Up Next');
  assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')).saved),store.saved,
   'Inspecting the created session must not save an unsaved program or replace another program');
  if(cycle){
   await click('View full program');await page.waitForSelector('.wpb-program');
   const menu=await page.$(`[aria-label="More options for ${expectedPreview.day}"]`);
   await menu.evaluate(n=>n.scrollIntoView({block:'center'}));await menu.click();await click('Delete day');
   await page.waitForSelector('.wpb-dialog');await click('Delete day');
   await page.waitForFunction(id=>!document.getElementById('pv-day-'+id),{},expectedPreview.dayId);
   await page.waitForSelector('.wpb-dialog',{hidden:true});
   await page.evaluate(()=>window.history.back());
   await page.waitForSelector('.wpb-preview-unavailable');
   assert.equal(await page.$('.wpb-preview-start'),null,'A removed selected day cannot silently become Up Next');
   await page.click('.wpb-preview-unavailable button');await page.waitForSelector('.wpb-program');
   expectedPreview=await page.evaluate(()=>{
    const b=[...document.querySelectorAll('.wpb-day-start')].find(b=>!b.disabled);b.dataset.m351Retry='true';
    return {name:document.querySelector('.wpb-program-name-text').textContent,
     day:b.getAttribute('aria-label').slice(6),dayId:b.closest('[data-daycard]').id.slice(7)};
   });
   const retry=await page.$('[data-m351-retry]');await retry.evaluate(n=>n.scrollIntoView({block:'center'}));await retry.click();
   await page.waitForSelector('.wpb-next-workout-view');
   assert.equal(await page.$eval('.wpb-preview-program-name',n=>n.textContent),expectedPreview.name);
  }
  const previewSets=await page.$$eval('[data-nextex] .wpb-preview-prescription',rows=>
   rows.reduce((sum,row)=>sum+parseInt(row.textContent,10),0));
  await page.click('.wpb-preview-start');await page.waitForSelector('.wpb-workout');
  await click('Feeling strong');
  await page.waitForFunction(()=>JSON.parse(localStorage.getItem('wpb:live')||'{}').data?.length>0);
  let live=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:live')));
  assert.notEqual(live.programId,program.id,'Start must log against the selected creation, never the older active program');
  assert.equal(live.dayId,expectedPreview.dayId);
  assert.equal(live.data.reduce((sum,row)=>sum+row.sets.filter(s=>!s.warm&&!s.sub).length,0),previewSets);
  const started=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')));
  assert.equal(started.saved.filter(p=>p.id===live.programId).length,1,'Start saves a new plan exactly once');
  assert.equal(started.pinnedId,live.programId,'Saving a new creation through Start keeps Home on that program');
  assert.deepEqual(started.saved.find(p=>p.id===program.id),empty?undefined:program);
  if(cycle)assert.deepEqual(started.saved,store.saved,'Training a draft does not silently commit saved cycle edits');
  await page.evaluate(()=>window.dispatchEvent(new Event('wpb:system-back')));await page.waitForSelector('.wpb-live-dock');
  await page.reload({waitUntil:'networkidle0'});await page.click('[aria-label="Resume workout"]');await page.waitForSelector('.wpb-workout');
  await page.waitForFunction(id=>JSON.parse(localStorage.getItem('wpb:live')||'{}').programId===id,{},live.programId);
  const resumed=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:live')));
  assert.equal(resumed.dayId,expectedPreview.dayId);
  assert.deepEqual(resumed.data.map(r=>r.id),live.data.map(r=>r.id),'Reload/resume preserves the selected creation and day');

  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1),false);
  assert.deepEqual(errors,[]);
  console.log(`PASS B899 ${width}/${theme}/reduced=${reduced}/cycle=${cycle}: ${frames.length} steady frames, ${steps.length} setup steps, inline build/cancel/retry, original plan/history intact`);
  await context.close();
 }
}finally{await browser?.close();await new Promise(r=>server.close(r));}
