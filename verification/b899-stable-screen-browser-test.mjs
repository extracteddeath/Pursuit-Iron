import assert from 'node:assert/strict';
import fs from 'node:fs';import http from 'node:http';import path from 'node:path';import puppeteer from 'puppeteer-core';
const root=path.resolve(new URL('../',import.meta.url).pathname);
const createdName='Steady creation — Strength and Hypertrophy Weekly Training';
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
    const field=await page.$('.wpb-wizard-step input');await field.type(createdName);
    assert.equal(await page.$eval('.wpb-wizard-step input',n=>n.value),createdName);
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
  let store=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')));
  assert.deepEqual(store.saved.find(p=>p.id===program.id),empty?undefined:program);assert.deepEqual(store.history,[]);
  if(cycle)assert.equal(store.cycles.length,1,'cycle build completes through the same steady flow');
  assert.ok((await page.$eval('.wpb-program',n=>n.textContent)).includes('Steady creation'),'built plan opens directly');
  const reviewLayout=async large=>{
   await page.$eval('.wpb-program-scroll',n=>{n.scrollTop=0;});
   await page.evaluate(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))));
   const layout=await page.evaluate(()=>{
    const name=document.querySelector('.wpb-program-name-text'),header=document.querySelector('.wpb-program-header');
    const body=document.querySelector('.wpb-program-scroll'),commit=document.querySelector('.wpb-program-commit');
    const tools=document.querySelector('.wpb-program-actions');
    return {fullName:name.textContent,wrapping:getComputedStyle(name).whiteSpace,
     titleWidth:name.getBoundingClientRect().width,titleOverflow:name.scrollWidth>name.clientWidth+1,
     headerHeight:header.getBoundingClientRect().height,bodyHeight:body.clientHeight,viewport:innerHeight,
     overflow:document.documentElement.scrollWidth>innerWidth+1,
     commitBeforeTools:!commit||!!(commit.compareDocumentPosition(tools)&Node.DOCUMENT_POSITION_FOLLOWING),
     duplicateActions:document.querySelectorAll('.wpb-program-save').length};
   });
   assert.ok(layout.fullName.includes(createdName));assert.notEqual(layout.wrapping,'nowrap');
   assert.equal(layout.titleOverflow,false);assert.equal(layout.overflow,false);
   assert.ok(layout.headerHeight<=layout.viewport*.5&&layout.bodyHeight>=120,
    'Full identity leaves the form usable: '+JSON.stringify(layout));
   assert.equal(layout.commitBeforeTools,true,'Save/Review comes before secondary program tools');
   assert.ok(layout.duplicateActions<=1,'There is one program save/review action');
   const tools=await page.$$eval('.wpb-program-actions .wpb-ui-action',nodes=>nodes.map(n=>{
    const r=n.getBoundingClientRect();return {height:r.height,y:Math.round(r.y),center:Math.round(r.y+r.height/2),width:r.width,label:n.textContent||n.getAttribute('aria-label')};
   }));
   assert.ok(tools.every(t=>t.height>=48),'Program utilities retain shared touch targets');
   if(!large&&width===320&&tools.length===4){
    await page.screenshot({path:'verification/m352-tools-320-phone.png'});
    assert.ok(Math.max(...tools.map(t=>t.center))-Math.min(...tools.map(t=>t.center))<=1,
     'The standard utility group fits one narrow row: '+JSON.stringify(tools));
   }

   const primary=await page.$('.wpb-program-save');
   if(primary){
    await primary.evaluate(n=>n.scrollIntoView({block:'center'}));
    assert.equal(await primary.evaluate(n=>{const r=n.getBoundingClientRect(),hit=document.elementFromPoint(r.x+r.width/2,r.y+r.height/2);
     return r.height>=48&&r.top>=0&&r.bottom<=innerHeight&&n.contains(hit);}),true,
     'The primary save/review action remains reachable');
   }
   await page.$eval('.wpb-program-scroll',n=>{n.scrollTop=0;});
   await page.screenshot({path:`verification/m352-review-${width}-${cycle?'cycle':'program'}-${large?'large':'normal'}-phone.png`});
  };
  await reviewLayout(false);
  // M368: use the production dialog, state owner, shared controls and focus stack.
  // Selecting constraints is staged; Cancel/Escape must not mutate training or drafts.
  const settingsState=()=>page.evaluate(()=>{
   const s=JSON.parse(localStorage.getItem('wpb:v1'));
   return {saved:s.saved,cycles:s.cycles,history:s.history,drafts:s.drafts};
  });
  for(const large of [false,...([320,393,900].includes(width)?[true]:[])]){
   const before=await settingsState();
   const trigger=await page.$('[aria-label="Program settings"]');
   await trigger.evaluate(n=>n.scrollIntoView({block:'center'}));await trigger.click();
   await page.waitForSelector('[data-testid="program-settings-sheet"]');
   const oldFont=await page.evaluate(large=>{
    const font=document.documentElement.style.fontSize;
    if(large)document.documentElement.style.fontSize='32px';
    return font;
   },large);
   if(large&&width===900)await page.setViewport({width,height:480,deviceScaleFactor:1,isMobile:false,hasTouch:true});
   const geometry=await page.evaluate(()=>{
    const panel=document.querySelector('.wpb-program-settings-panel'),body=panel.querySelector('.wpb-program-settings-body'),footer=panel.querySelector('.wpb-program-settings-footer');
    const p=panel.getBoundingClientRect(),b=body.getBoundingClientRect(),f=footer.getBoundingClientRect();
    return {left:p.left,right:p.right,viewport:innerWidth,bodyHeight:b.height,bodyBottom:b.bottom,footerTop:f.top,
     overflow:body.scrollWidth-body.clientWidth,
     choices:[...panel.querySelectorAll('button[aria-pressed]')].map(n=>({height:n.getBoundingClientRect().height,inline:[...n.style].filter(k=>['background','color','font-size','height','width','padding','border'].includes(k))})),
     labelSize:parseFloat(getComputedStyle(panel.querySelector('.wpb-program-settings-choice-label')).fontSize)};
   });
   assert.ok(geometry.left>=0&&geometry.right<=geometry.viewport+1&&geometry.overflow<=1,'Settings stays within the app: '+JSON.stringify(geometry));
   assert.ok(geometry.bodyHeight>=44&&geometry.bodyBottom<=geometry.footerTop+1,'Settings retains a usable form viewport above its footer');
   assert.ok(geometry.choices.length>=10&&geometry.choices.every(n=>n.height>=48-.01&&!n.inline.length),'Shared choices have real 48px targets and no inline presentation: '+JSON.stringify(geometry));
   if(large)assert.ok(geometry.labelSize>=30,'Choice labels follow 200% text sizing');
   assert.equal(await page.$eval('[aria-label="Save program settings"]',n=>n.disabled),true);
   const groups=await page.$$('.wpb-program-settings-group');
   for(const group of groups){
    const option=await group.$('button[aria-pressed="false"]');
    if(!option)continue;
    await option.evaluate(n=>n.scrollIntoView({block:'center'}));await option.click();
    assert.equal(await option.evaluate(n=>n.getAttribute('aria-pressed')),'true','Choices update the controlled draft');
   }
   assert.equal(await page.$eval('[aria-label="Save program settings"]',n=>n.disabled),false,'A staged change enables Save');
   await page.focus('#program-settings-description');await page.type('#program-settings-description','Staged settings check');
   await page.focus('.wpb-program-settings-save');await page.keyboard.press('Tab');
   assert.equal(await page.$eval('.wpb-program-settings-close',n=>document.activeElement===n),true,'Tab wraps inside Settings through the shared focus owner');
   await page.keyboard.down('Shift');await page.keyboard.press('Tab');await page.keyboard.up('Shift');
   assert.equal(await page.$eval('.wpb-program-settings-save',n=>document.activeElement===n),true,'Shift Tab returns to the final settings action');
   for(const selector of ['.wpb-program-settings-save','.wpb-program-settings-cancel']){
    assert.equal(await page.$eval(selector,n=>{const r=n.getBoundingClientRect();return r.top>=0&&r.bottom<=innerHeight&&n.contains(document.elementFromPoint(r.x+r.width/2,r.y+r.height/2));}),true,'Settings footer remains pointer-reachable after scrolling');
   }
   await page.screenshot({path:`verification/m368-settings-${width}-${theme}-${large?'large':'normal'}-phone.png`});
   if(width===393&&!large){
    const cdp=await page.createCDPSession();
    await cdp.send('Emulation.setEmulatedMedia',{features:[{name:'prefers-reduced-motion',value:'reduce'},{name:'forced-colors',value:'active'}]});
    const forced=await page.evaluate(()=>{
     const panel=document.querySelector('.wpb-program-settings-panel');
     const selected=panel.querySelector('.wpb-ui-choice[aria-pressed="true"]'),idle=panel.querySelector('.wpb-ui-choice[aria-pressed="false"]');
     return {active:matchMedia('(forced-colors:active)').matches,selected:getComputedStyle(selected).backgroundColor,idle:getComputedStyle(idle).backgroundColor,
      motion:getComputedStyle(panel.querySelector('.wpb-program-settings-choice')).transitionProperty};
    });
    assert.ok(forced.active&&forced.selected!==forced.idle&&forced.motion==='none','Settings keeps selection and reduced motion in forced colors: '+JSON.stringify(forced));
    await cdp.send('Emulation.setEmulatedMedia',{features:[{name:'prefers-reduced-motion',value:reduced?'reduce':'no-preference'}]});
    await cdp.detach();
   }
   if(large)await page.keyboard.press('Escape');else await page.click('.wpb-program-settings-cancel');
   await page.waitForSelector('[data-testid="program-settings-sheet"]',{hidden:true});
   assert.equal(await trigger.evaluate(n=>document.activeElement===n),true,'Closing returns focus to Program settings');
   assert.deepEqual(await settingsState(),before,'Settings cancellation preserves saved plans, history, cycle and creation drafts');
   await page.evaluate(font=>{document.documentElement.style.fontSize=font;},oldFont);
   if(large&&width===900)await page.setViewport({width,height:880,deviceScaleFactor:1,isMobile:false,hasTouch:true});
  }
  if(width===393){
   const reroll=await page.$('[aria-label="Reroll exercises or enter a seed"]');await reroll.evaluate(n=>n.scrollIntoView({block:'center'}));await reroll.click();
   await page.waitForFunction(()=>[...document.querySelectorAll('.wpb-backdrop div')]
    .some(n=>n.children.length===0&&n.textContent==='Shuffle exercises'));
   await page.screenshot({path:'verification/m352-reroll-393-phone.png'});
   await page.keyboard.press('Escape');await page.waitForSelector('.wpb-program .wpb-backdrop',{hidden:true});
  }
  const beforeReflow=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')));
  if(width===900)await page.setViewport({width,height:480,deviceScaleFactor:1,isMobile:false,hasTouch:true});
  await page.evaluate(()=>{
   window.__m352Fonts=[...document.querySelectorAll('.wpb-program,.wpb-program *')]
    .filter(n=>n.tagName!=='STYLE').map(n=>[n,parseFloat(getComputedStyle(n).fontSize),n.style.getPropertyValue('font-size'),n.style.getPropertyPriority('font-size')]);
   window.__m352Fonts.forEach(([n,size])=>n.style.setProperty('font-size',size*2+'px','important'));
   window.dispatchEvent(new Event('resize'));
  });
  await reviewLayout(true);
  await page.evaluate(()=>{window.__m352Fonts.forEach(([n,size,value,priority])=>value?n.style.setProperty('font-size',value,priority):n.style.removeProperty('font-size'));delete window.__m352Fonts;window.dispatchEvent(new Event('resize'));});
  if(width===900)await page.setViewport({width,height:880,deviceScaleFactor:1,isMobile:false,hasTouch:true});
  await page.evaluate(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))));
  assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')).saved),beforeReflow.saved,
   'Reading and reflowing the generated review does not save or change programs');
  if(width===900){
   await page.click('[aria-label="Save this program"]');await page.waitForSelector('[aria-label="Program saved"]');
   store=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')));
   assert.equal(store.saved.length,beforeReflow.saved.length+1,'The moved Save action commits the new program once');
   assert.equal(store.saved.find(p=>p.id===store.pinnedId)?.name,createdName);
  }

  if(cycle){
   await page.click('.wpb-program-name');await page.waitForSelector('input[aria-label="Program name"]');
   await page.type('input[aria-label="Program name"]',' · Draft review');await page.click('[aria-label="Save name"]');
   await reviewLayout(false);
   await page.click('[aria-label="Review program changes"]');await page.waitForSelector('#program-save-title');
   assert.equal(await page.$eval('input[name="program-save-scope"]:checked',n=>n.value),'program');
   await page.click('[aria-label="Close change review"]');await page.waitForSelector('#program-save-title',{hidden:true});
   assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')).saved),store.saved,
    'Opening and cancelling the moved Review action preserves saved cycle blocks');

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
   const deletedDayDraft=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')));
   await page.evaluate(()=>window.history.back());
   await page.waitForFunction(()=>[...document.querySelectorAll('.wpb-dialog')].some(n=>n.textContent.includes('Keep your changes?')));
   assert.equal(await page.$('.wpb-preview-unavailable'),null,'Native Back reviews the pending deletion before leaving Program');
   await click('Keep editing');await page.waitForSelector('.wpb-dialog',{hidden:true});
   assert.equal(await page.$eval('#pv-day-'+expectedPreview.dayId,n=>!!n).catch(()=>false),false,'Keep editing retains the deleted-day draft');
   assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')).saved),deletedDayDraft.saved,'Native Back and Keep editing do not save the deletion');
   await page.evaluate(()=>window.history.back());
   await page.waitForFunction(()=>[...document.querySelectorAll('.wpb-dialog')].some(n=>n.textContent.includes('Keep your changes?')));
   await click('Keep draft & leave');
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
