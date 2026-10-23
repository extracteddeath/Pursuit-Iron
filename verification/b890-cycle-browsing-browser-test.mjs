import assert from 'node:assert/strict';
import fs from 'node:fs';import http from 'node:http';import path from 'node:path';import puppeteer from 'puppeteer-core';
import { EXERCISES, mergeStandaloneIntoGeneratedCycle } from '../modules/App.js';
import { generateNextCycleForShell } from '../modules/next-engine/cycle-runtime-adapter.js';
import { currentProgram } from './m215-cycle-duration-fixture.mjs';
import { config as lifecycleConfig, completedHistory, ids } from './m265-generation-fixture.mjs';
const root=path.resolve(new URL('../',import.meta.url).pathname);
const cfg=JSON.parse(fs.readFileSync(path.join(root,'verification/m205-generation-parity-results.json'))).rows[3].config;
let n=0;
const generated=generateNextCycleForShell({templateId:'powerbuilding',config:cfg,legacyExercises:EXERCISES,seed:890,makeId:()=>`b890-${++n}`,adaptBetweenBlocks:true});
const merged=mergeStandaloneIntoGeneratedCycle({...currentProgram,name:'SekuFit',config:{...currentProgram.config}},generated);
const cycle={...merged.cycle,name:'SekuFit Cycle'},saved=merged.allBlocks;
const store={v:13,savedAt:1,saved,cycles:[cycle],history:[],perf:{},drafts:{},tombs:{},seenIntro:999,seenWhatsNew:999,pinnedId:saved[0].id,
 theme:'amethyst',unit:'lb',unitChosen:true,experience:'intermediate',bodyweight:185,installDismissedAt:Date.now(),lastBackup:Date.now()};

async function verifyCycleText(page,selector,width){
  await page.evaluate(()=>document.documentElement.style.fontSize='32px');
  await page.waitForFunction(selector=>{
    const label=document.querySelector(selector+' .wpb-cycle-block-title');
    return label&&parseFloat(getComputedStyle(label).fontSize)>=30;
  },{},selector);
  const look=await page.$eval(selector,root=>{
    const fits=el=>{const box=el.getBoundingClientRect(),walker=document.createTreeWalker(el,NodeFilter.SHOW_TEXT);
      while(walker.nextNode()){
        const text=walker.currentNode;if(!text.textContent.trim()||text.parentElement.closest('svg'))continue;
        const range=document.createRange();range.selectNodeContents(text);
        if([...range.getClientRects()].some(r=>r.width>1&&r.height>1&&(r.left<box.left-1||r.right>box.right+1||r.top<box.top-1||r.bottom>box.bottom+1)))return false;
      }return true;};
    const rows=[...root.querySelectorAll('.wpb-cycle-block-row')];
    return {overflow:root.scrollWidth>root.clientWidth+1,heading:fits(root.querySelector('h1')),
      rows:rows.map(row=>({height:row.offsetHeight,text:fits(row),badge:fits(row.querySelector('.wpb-cycle-block-number')),opacity:getComputedStyle(row).opacity}))};
  });
  assert.ok(!look.overflow&&look.heading&&look.rows.length===3&&look.rows.every(r=>r.height>=64&&r.text&&r.badge&&r.opacity==='1'),
    'Cycle text stays whole at 200%: '+JSON.stringify({selector,width,look}));
  if(width===320||width===1280)await page.screenshot({path:`/tmp/cohesion14-${selector.slice(1)}-${width}-200.png`});
  await page.evaluate(()=>document.documentElement.style.fontSize='');
  await page.waitForFunction(selector=>parseFloat(getComputedStyle(document.querySelector(selector+' .wpb-cycle-block-title')).fontSize)<20,{},selector);
}

const mime={'.js':'text/javascript','.mjs':'text/javascript','.css':'text/css','.html':'text/html','.json':'application/json','.png':'image/png','.svg':'image/svg+xml'};

// The all-cycles empty fallback is also exported; mount the real component to
// exercise empty/completed/missing-block states without inventing a new route.
const importMap=fs.readFileSync(path.join(root,'index.html'),'utf8').match(/<script type="importmap">[\s\S]*?<\/script>/)[0];
const cycleFixture=`<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="/app.css">${importMap}</head><body><div id="root"></div><script type="module">
import React from '/vendor/react.js';import {createRoot} from '/vendor/react-dom-client.js';
import RootApp,{CyclesView,StyleTag} from '/modules/App.js';import {installAppMotion} from '/modules/ui-motion.js';
const owner=createRoot(document.getElementById('root'));owner.render(React.createElement(RootApp));installAppMotion(document.getElementById('root'));
window.renderCyclesFixture=(theme,props)=>{window.fixtureCreations=0;window.fixtureOpens=[];owner.render(React.createElement('div',{className:'wpb',style:{height:'100%',display:'flex',flexDirection:'column'}},React.createElement(StyleTag,{theme}),React.createElement(CyclesView,{...props,onBack:()=>{},onNew:()=>window.fixtureCreations++,onOpenBlock:(c,i)=>window.fixtureOpens.push(i)})));};
</script></body></html>`;

const server=http.createServer((req,res)=>{if(new URL(req.url,'http://localhost').pathname==='/cycles-fixture.html'){res.setHeader('Content-Type','text/html');res.end(cycleFixture);return;}const file=path.resolve(root,'.'+new URL(req.url,'http://localhost').pathname);if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){res.writeHead(404);res.end();return;}res.setHeader('Content-Type',mime[path.extname(file)]||'application/octet-stream');res.end(fs.readFileSync(file));});
await new Promise(resolve=>server.listen(8790,'127.0.0.1',resolve));let browser;
try{
 browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
 const errors=[];
 for(const width of [320,390,393,834,900,1280]){
  const page=await browser.newPage();page.on('pageerror',e=>errors.push(e.message));await page.setViewport({width,height:844,isMobile:width<600,hasTouch:width<600});
  await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
  await page.evaluateOnNewDocument(store=>{if(!sessionStorage.getItem('b890-seeded')){localStorage.setItem('wpb:v1',JSON.stringify(store));sessionStorage.setItem('b890-seeded','1');}},{...store,theme:width>=900?'rosewater':'amethyst'});
  await page.goto('http://127.0.0.1:8790/index.html',{waitUntil:'networkidle0'});await page.waitForSelector('.wpb-home');
  const click=async text=>{assert.ok(await page.evaluate(t=>{const b=[...document.querySelectorAll('button')].find(b=>b.textContent.trim()===t);b?.click();return !!b;},text),text);};
  await page.click('.wpb-tabbar [aria-label="Plan"]');await page.waitForSelector('.wpb-plan-block-nav');
  assert.equal(await page.$$eval('.wpb-plan-block-nav button',ns=>ns.length),3);
  await page.evaluate(()=>{document.documentElement.style.fontSize='32px';return new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)));});
  // Measure only after the requested text size has reached the real controls.
  // A frame count alone does not establish that style recalculation completed.
  await page.waitForFunction(()=>[...document.querySelectorAll('.wpb-plan-block-nav button')]
    .every(b=>parseFloat(getComputedStyle(b.querySelector('.wpb-ui-choice-label')).fontSize)>=26),{timeout:5000});
  const controls=await page.$$eval('.wpb-plan-block-nav button',buttons=>buttons.map(b=>{
    const box=b.getBoundingClientRect(),range=document.createRange();range.selectNodeContents(b);
    return {height:b.offsetHeight,label:parseFloat(getComputedStyle(b.querySelector('.wpb-ui-choice-label')).fontSize),
      clipped:[...range.getClientRects()].some(r=>r.width>1&&r.height>1&&(r.left<box.left-1||r.right>box.right+1||r.top<box.top-1||r.bottom>box.bottom+1))};
  }));
  assert.ok(controls.every(b=>b.height>=51&&b.label>=26&&!b.clipped),'Cycle block labels stay whole at 200% text: '+JSON.stringify({width,controls}));
  await page.evaluate(()=>document.documentElement.style.fontSize='');

  await page.$$eval('.wpb-plan-block-nav button',ns=>ns[1].click());await page.waitForSelector('.wpb-plan-preview-notice');
  assert.equal(await page.$('.wpb-plan-up-next'),null);
  assert.equal(await page.$$eval('.wpb-plan-week-chip',ns=>ns.length),saved[1].config.weeks+(saved[1].config.deload?1:0));
  await page.click('.wpb-plan-session-link');await page.waitForSelector('.wpb-cycle-block-preview');
  assert.match(await page.$eval('.wpb-cycle-block-preview',n=>n.innerText),/sets · .* reps · .* RIR/);
  assert.ok(await page.$eval('.wpb-cycle-preview-facts',(n,days)=>n.textContent.includes(`${days} training days per week`),saved[1].days.length),'preview exposes the actual upcoming weekly structure');
  const expectedPreviewDose=saved[1].nextEngine?.cycleTemplate?.splitDecision?.workingWeekVolumeChange;
  assert.equal(await page.evaluate(()=>document.querySelectorAll('.wpb-cycle-block-preview .wpb-cycle-dose-detail').length),
    expectedPreviewDose?1:0,'preview shows saved volume detail only when available');
  const savedQuality=saved[1].nextEngine?.cycleTemplate?.splitDecision?.realizedQuality;
  if(savedQuality?.weeks?.length){
    const first=savedQuality.weeks[0];
    const outlook=await page.$eval('.wpb-cycle-week-outlook',node=>({
      title:node.querySelector('.wpb-cycle-week-outlook-head')?.textContent,
      week:node.dataset.week,
      facts:[...node.querySelectorAll('.wpb-cycle-week-outlook-stats dd')].map(n=>n.textContent.trim()),
      width:node.scrollWidth,clientWidth:node.clientWidth,
      summaryHeight:node.querySelector('summary')?.offsetHeight??0
    }));
    assert.equal(outlook.week,'1');
    assert.deepEqual(outlook.facts,[String(first.weeklySets),first.muscleTargetCoveragePercent+'%']);
    assert.ok(outlook.width<=outlook.clientWidth+1);
    await page.click('.wpb-cycle-week-outlook-detail summary');
    assert.ok(await page.$eval('.wpb-cycle-week-outlook-detail',n=>n.open));
    assert.match(await page.$eval('.wpb-cycle-week-outlook-note',n=>n.textContent),/not the percentage of your previous block retained/i);
    assert.equal(outlook.summaryHeight>=44,true,'lift-practice disclosure has a 44px target');
    assert.equal(await page.$eval('.wpb-cycle-block-preview',n=>n.innerText.includes('Modeled muscle-target coverage:')),false,
      'Preview must not repeat stale base-generation coverage');
  }
  assert.equal(await page.$$eval('.wpb-cycle-block-preview button',ns=>ns.some(n=>/start/i.test(n.textContent))),false);
  await page.evaluate(()=>document.documentElement.style.fontSize='32px');
  await page.waitForFunction(()=>parseFloat(getComputedStyle(document.querySelector('.wpb-cycle-preview-name')).fontSize)>=28);
  const previewZoom=await page.$eval('.wpb-cycle-block-preview',root=>({overflow:root.scrollWidth>root.clientWidth+1,
    select:root.querySelector('select').offsetHeight,summary:root.querySelector('summary').offsetHeight}));
  assert.ok(!previewZoom.overflow&&previewZoom.select>=48&&previewZoom.summary>=44,'Readable read-only preview: '+JSON.stringify({width,previewZoom}));
  await page.evaluate(()=>document.documentElement.style.fontSize='');
  await page.select('[aria-label="Preview week"]',String(saved[1].config.weeks));
  assert.equal(await page.$eval('[aria-label="Preview week"]',n=>n.value),String(saved[1].config.weeks));
  if(savedQuality?.weeks?.length){
    const last=savedQuality.weeks.at(-1);
    await page.waitForFunction(expected=>document.querySelector('.wpb-cycle-week-outlook')?.dataset.week===String(expected),{},last.week);
    assert.deepEqual(await page.evaluate(()=>[...document.querySelectorAll('.wpb-cycle-week-outlook-stats dd')].map(n=>n.textContent.trim())),
      [String(last.weeklySets),last.muscleTargetCoveragePercent+'%'],
      'Switching weeks must update the canonical executable coverage, not just the exercise list');
  }
  await page.keyboard.press('Escape');await page.waitForFunction(()=>!document.querySelector('.wpb-cycle-block-preview'));
  await page.$$eval('.wpb-plan-block-nav button',ns=>ns[2].click());await page.waitForFunction(()=>document.querySelector('.wpb-plan-title')?.textContent.includes('Peak'));
  await page.$$eval('.wpb-plan-block-nav button',ns=>ns[0].click());await page.waitForFunction(()=>!document.querySelector('.wpb-plan-preview-notice'));
  assert.ok(await page.$('.wpb-plan-up-next'));
  await page.click('.wpb-tabbar [aria-label="Home"]');await page.waitForSelector('.wpb-home');
  await page.click('[aria-label="Open cycle SekuFit Cycle"]');await page.waitForSelector('.wpb-cycle-detail');
  await verifyCycleText(page,'.wpb-cycle-detail',width);
  // Browse through the actual all-cycles route and return through its title action.
  await page.click('.wpb-cycle-detail .wpb-cycle-header [aria-label="Back"]');await page.waitForSelector('.wpb-home');
  await page.click('.wpb-home-cycles');
  await page.waitForSelector('.wpb-cycles .wpb-cycle-list-summary');
  assert.match(await page.$eval('.wpb-cycle-list-summary',n=>n.textContent),
    /Training blocks · 3/, 'Cycle list retains a compact heading before the full block sequence');
  await page.click('.wpb-cycle-list-summary');await page.waitForSelector('.wpb-cycle-list-disclosure[open]');
  await verifyCycleText(page,'.wpb-cycles',width);
  await page.click('.wpb-cycle-open');await page.waitForSelector('.wpb-cycle-detail');
  const rows=await page.$$eval('.wpb-cycle-detail .wpb-cycle-block-row' ,ns=>ns.map(n=>({disabled:n.disabled,opacity:getComputedStyle(n).opacity,h:n.getBoundingClientRect().height,text:n.innerText})));
  assert.equal(rows.length,3);assert.ok(rows.every(r=>!r.disabled&&r.opacity==='1'&&r.h>=44));
  // B908: visual hierarchy is state-specific, and local-only motion is reversible.
  const cycleLook=await page.evaluate(()=>{
    const root=document.querySelector('.wpb-cycle-detail');
    const blocks=[...root.querySelectorAll('.wpb-cycle-block-row')];
    const active=getComputedStyle(blocks[0]),planned=getComputedStyle(blocks[2]);
    const next=root.querySelector('.wpb-cycle-change-preview');
    const schedule=root.querySelector('.wpb-cycle-schedule');
    return {activeShadow:active.boxShadow,activeBg:active.backgroundColor,plannedBg:planned.backgroundColor,
      activeTransition:active.transitionDuration,plannedOpacity:planned.opacity,
      nextBorder:next?getComputedStyle(next).borderColor:null,
      scheduleBg:getComputedStyle(schedule).backgroundColor,
      overviewPadding:parseFloat(getComputedStyle(root.querySelector('.wpb-cycle-overview')).paddingTop),
      overviewMargin:parseFloat(getComputedStyle(root.querySelector('.wpb-cycle-overview')).marginBottom),
      blockPadding:parseFloat(getComputedStyle(blocks[0]).paddingTop),
      noOverflow:root.scrollWidth<=root.clientWidth+1};
  });
  assert.ok(cycleLook.activeShadow.includes('inset'),'current block keeps visible accent rail');
  assert.notEqual(cycleLook.activeBg,cycleLook.plannedBg,'current block is visually distinct from planned');
  assert.equal(cycleLook.plannedOpacity,'1','future blocks stay readable');
  assert.ok(cycleLook.noOverflow,'cycle hierarchy must not overflow');
  assert.ok(cycleLook.nextBorder&&cycleLook.scheduleBg,'cycle facts remain present');
  assert.ok(cycleLook.overviewPadding<=16.1&&cycleLook.overviewMargin<=16.1&&cycleLook.blockPadding<=16.1,
    'Cycle summary and full-size block targets use compact shared spacing: '+JSON.stringify(cycleLook));
  assert.equal(cycleLook.activeTransition,'0s','reduced motion must suppress state transitions');
  await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'no-preference'}]);
  assert.notEqual(await page.$eval('.wpb-cycle-block-row[data-state="active"]',n=>getComputedStyle(n).transitionDuration),'0s','non-reduced motion uses local transitions');
  await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
  assert.ok(rows[2].text.includes('Peak'));assert.equal(await page.$$eval('.wpb-cycle-actions button',ns=>ns.length),1);
  assert.doesNotMatch(await page.$eval('.wpb-cycle-detail',n=>n.innerText),/kept exactly|Available after/);
  assert.equal(await page.$eval('.wpb-cycle-detail',n=>n.scrollWidth<=n.clientWidth+1),true);
  const expectedDose=saved[1].nextEngine?.cycleTemplate?.splitDecision?.workingWeekVolumeChange;
  const disclosures=await page.$$eval('.wpb-cycle-detail details',ns=>ns.map(n=>({dose:n.classList.contains('wpb-cycle-dose-detail'),open:n.open})));
  assert.ok(disclosures.every(n=>n.dose&&!n.open),'existing top-level cycle facts stay expanded; dose is opt-in');
  if(expectedDose){
    assert.equal(disclosures.length,1,'Up next shows one compact dose detail');
    await page.click('.wpb-cycle-detail .wpb-cycle-dose-summary');
    const visible=await page.$eval('.wpb-cycle-detail .wpb-cycle-dose-detail',n=>({
      open:n.open,regions:n.querySelectorAll('.wpb-cycle-dose-row').length,
      text:n.innerText,overflow:n.scrollWidth>n.clientWidth+1
    }));
    assert.ok(visible.open&&visible.regions===expectedDose.regions.length&&!visible.overflow,
      'live regional breakdown is accessible and fits narrow screens');
    assert.ok(visible.text.includes(String(expectedDose.previousWeeklySets))&&visible.text.includes(String(expectedDose.currentWeeklySets)));
    await page.click('.wpb-cycle-detail .wpb-cycle-dose-summary');
  } else assert.equal(disclosures.length,0,'Older saved cycles do not invent missing regional data');
  await page.waitForSelector('[aria-label="Next block changes"]');await page.waitForSelector('[aria-label="Cycle schedule"]');
  assert.equal(await page.$eval('.wpb-cycle-overview',n=>parseFloat(getComputedStyle(n).borderRadius)>=18),true);
  await page.screenshot({path:`/tmp/b890-cycle-detail-${width}.png`});
  await page.$eval('.wpb-cycle-schedule',n=>n.scrollIntoView({block:'end'}));
  await page.screenshot({path:`/tmp/b903-cycle-facts-${width}.png`});
  if(width===393){
    const cdp=await page.createCDPSession();
    await cdp.send('Emulation.setEmulatedMedia',{features:[{name:'forced-colors',value:'active'},{name:'prefers-reduced-motion',value:'reduce'}]});
    await page.keyboard.press('Tab');
    await page.$eval('.wpb-cycle-block-row[data-state="active"]',b=>b.focus());
    const forced=await page.$$eval('.wpb-cycle-block-row',rows=>rows.map(r=>({border:getComputedStyle(r).borderColor,outline:parseFloat(getComputedStyle(r).outlineWidth)})));
    assert.notEqual(forced[0].border,forced[2].border,'Forced colors retain the current-block edge');
    assert.ok(forced[0].outline>=2,'Forced colors retain keyboard focus');
    await cdp.send('Emulation.setEmulatedMedia',{features:[{name:'prefers-reduced-motion',value:'reduce'}]});
  }
  await page.$$eval('.wpb-cycle-detail .wpb-cycle-block-row',ns=>ns[2].focus());await page.keyboard.press('Enter');await page.waitForSelector('.wpb-cycle-block-preview');
  await click('View in Plan');await page.waitForSelector('.wpb-plan-preview-notice');assert.match(await page.$eval('.wpb-plan-title',n=>n.textContent),/Peak/);
  const stored=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')));
  assert.equal(stored.pinnedId,saved[0].id);assert.equal(stored.cycles[0].activeBlock,0);assert.deepEqual(stored.history,[]);
  assert.deepEqual(stored.saved,saved,'browsing never edits saved blocks');
  if(width===320){
    await page.evaluate(missing=>{const store=JSON.parse(localStorage.getItem('wpb:v1'));store.saved=store.saved.filter(p=>p.id!==missing);localStorage.setItem('wpb:v1',JSON.stringify(store));},saved[2].id);
    await page.reload({waitUntil:'networkidle0'});await page.waitForSelector('[data-tab="plan"]');await page.click('[data-tab="plan"]');await page.waitForSelector('.wpb-plan-block-nav');
    assert.equal(await page.$eval('.wpb-plan-block-nav button:last-child',b=>b.disabled),true,'Missing planned block remains disabled');
    await page.$eval('.wpb-plan-block-nav button:last-child',b=>b.click());
    assert.equal(await page.$$eval('.wpb-plan-block-nav button[aria-pressed="true"]',bs=>bs.length),1,'Disabled choice cannot change selection');
    assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')).cycles[0].activeBlock),0,'Missing-block browsing cannot advance the cycle');
  }
  await page.close();
 }

 // M298: exercise the actual app confirmation, worker, persisted transition and repeat.
 {
  const live=generateNextCycleForShell({templateId:'custom',config:{...lifecycleConfig,name:'Lifecycle browser'},
   blocks:[{phase:'mixed_accumulation',weeks:1},{phase:'strength_accumulation',weeks:1,split:'upper_lower',days:4,priorityLift:'bench_press',frequency:2}],
   legacyExercises:EXERCISES,seed:298,makeId:ids('b890-live'),adaptTrainingSplit:true,adaptBetweenBlocks:false});
  const liveCycle={...live.cycle,onComplete:'loop'},firstHistory=completedHistory(live.blocks[0]);
  const context=await browser.createBrowserContext(),page=await context.newPage();
  page.on('pageerror',error=>errors.push(error.message));await page.setBypassServiceWorker(true);
  await page.setViewport({width:393,height:852,isMobile:true,hasTouch:true});
  await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
  await page.evaluateOnNewDocument(seed=>{if(!sessionStorage.getItem('b890-lifecycle')){
   localStorage.setItem('wpb:v1',JSON.stringify(seed));sessionStorage.setItem('b890-lifecycle','1');
  }},{...store,saved:live.blocks,cycles:[liveCycle],pinnedId:live.blocks[0].id,history:firstHistory});
  await page.goto('http://127.0.0.1:8790/index.html',{waitUntil:'networkidle0'});
  const complete=async(final=false)=>{
   await page.waitForSelector('[aria-label="Open cycle Lifecycle browser"]',{visible:true});
   const openerBefore=await page.$eval('[aria-label="Open cycle Lifecycle browser"]',el=>({
     html:el.outerHTML.slice(0,450),visible:!!el.getClientRects().length,
     text:el.innerText.slice(0,150)
   }));
   // The six responsive UI cases above already validate real pointer taps
   // into Cycle Detail. This repeated lifecycle/worker continuation uses the
   // native keyboard activation path so a transient moving Home hit target
   // cannot discard the action before the cycle-completion assertion begins.
   // Enter still dispatches the actual React button handler and must open
   // Cycle options; no fallback route or swallowed failure is permitted.
   await page.focus('[aria-label="Open cycle Lifecycle browser"]');
   await page.keyboard.press('Enter');
   try{
     await page.waitForSelector('[aria-label="Cycle options"]',{timeout:10000});
   }catch(error){
     const diagnostic=await page.evaluate(()=>{
       const saved=JSON.parse(localStorage.getItem('wpb:v1')||'{}');
       return {home:!!document.querySelector('.wpb-home'),cycleDetail:!!document.querySelector('.wpb-cycle-detail'),
         allCycles:!!document.querySelector('.wpb-cycles'),popup:!!document.querySelector('.wpb-pop'),
         cycleIds:(saved.cycles||[]).map(c=>c.id),pinnedId:saved.pinnedId,
         openerCount:document.querySelectorAll('[aria-label="Open cycle Lifecycle browser"]').length,
         screenText:document.body.innerText.slice(0,850)};
     });
     throw new Error('B890 cycle navigation did not reach Cycle options: '+JSON.stringify({
       openerBefore,diagnostic,pageErrors:errors.slice(-5)}),{cause:error});
   }
   await page.click('[aria-label="Cycle options"]');
   await page.waitForSelector('.wpb-cycle-option-actions');
   await page.$$eval('.wpb-cycle-option-actions button',bs=>bs.find(b=>b.textContent.startsWith('Complete')).click());
   await page.waitForSelector('.wpb-pop');
   await page.$$eval('.wpb-pop button',(bs,final)=>bs.find(b=>b.textContent.trim()===(final?'Roll new cycle':'Complete block')).click(),final);
  };
  await complete();
  await page.waitForFunction(()=>JSON.parse(localStorage.getItem('wpb:v1')).cycles[0].activeBlock===1,{timeout:60000});
  const entered=await page.evaluate(()=>{const s=JSON.parse(localStorage.getItem('wpb:v1'));return {cycle:s.cycles[0],program:s.saved.find(p=>p.id===s.pinnedId),saved:s.saved};});
  assert.equal(entered.program.id,live.blocks[1].id,'the real app activates its reserved next block');
  assert.deepEqual(entered.saved.find(p=>p.id===live.blocks[0].id),live.blocks[0],'completed blocks stay intact');
  assert.equal(entered.program.nextEngine.request.goal.liftFrequency.bench_press,2);
  const finalHistory=[...firstHistory,...completedHistory(entered.program)];
  await page.evaluate(history=>{const s=JSON.parse(localStorage.getItem('wpb:v1'));s.history=history;localStorage.setItem('wpb:v1',JSON.stringify(s));},finalHistory);
  await page.reload({waitUntil:'networkidle0'});await complete(true);
  await page.waitForFunction(()=>JSON.parse(localStorage.getItem('wpb:v1')).cycles.length===2,{timeout:60000});
  const rolled=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1'))),next=rolled.cycles.find(c=>c.id!==liveCycle.id);
  assert.equal(rolled.cycles.find(c=>c.id===liveCycle.id).done,true);
  assert.equal(next.continuedFrom,liveCycle.id);assert.equal(next.onComplete,'loop');
  assert.equal(next.adaptTrainingSplit,true);assert.equal(next.adaptExercises,false);
  assert.deepEqual(next.nextEngineCycle.plannedBlocks,liveCycle.nextEngineCycle.plannedBlocks,'the real worker repeats authored custom block order, duration and frequency');
  assert.equal(rolled.saved.length,4,'repeat retains the completed blocks and adds distinct programs');
  const records=rows=>rows.map(({updatedAt,...record})=>record);
  assert.deepEqual(records(rolled.history),records(finalHistory),'complete and repeat preserve all training records');
  await page.reload({waitUntil:'networkidle0'});
  await page.waitForSelector('.wpb-home');
  const reloaded=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')));
  assert.equal(reloaded.pinnedId,next.blockIds[0],'new custom cycle remains active after reload');
  assert.equal(reloaded.cycles.length,2);await context.close();
 }

 for(const [theme,width] of [['amethyst',320],['rosewater',1280]]){
  const context=await browser.createBrowserContext(),page=await context.newPage();
  page.on('pageerror',error=>errors.push(error.message));await page.setBypassServiceWorker(true);
  await page.setViewport({width,height:1000,isMobile:width<600,hasTouch:width<600});
  await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
  await page.evaluateOnNewDocument(seed=>localStorage.setItem('wpb:v1',JSON.stringify(seed)),{...store,saved:[],cycles:[],pinnedId:null,theme});
  await page.goto('http://127.0.0.1:8790/cycles-fixture.html',{waitUntil:'networkidle0'});await page.waitForSelector('.wpb-home');
  await page.evaluate(theme=>window.renderCyclesFixture(theme,{cycles:[],saved:[]}),theme);await page.waitForSelector('.wpb-cycle-empty');
  await page.evaluate(()=>document.documentElement.style.fontSize='32px');
  await page.waitForFunction(()=>parseFloat(getComputedStyle(document.querySelector('.wpb-cycle-empty .wpb-ui-action')).fontSize)>=28);
  const empty=await page.$eval('.wpb-cycles',root=>{
    const actions=[...root.querySelectorAll('button')].filter(b=>/Create a cycle|\+ New/.test(b.textContent));
    const b=actions[0],range=document.createRange();range.selectNodeContents(b);const box=b.getBoundingClientRect();
    return {count:actions.length,overflow:root.scrollWidth>root.clientWidth+1,height:box.height,
      textFits:[...range.getClientRects()].every(r=>r.left>=box.left-1&&r.right<=box.right+1&&r.top>=box.top-1&&r.bottom<=box.bottom+1)};
  });
  assert.ok(empty.count===1&&!empty.overflow&&empty.height>=48&&empty.textFits,'One accessible empty creation action: '+JSON.stringify({theme,width,empty}));
  await page.$eval('.wpb-cycle-empty .wpb-ui-action',b=>b.focus());await page.keyboard.press('Enter');
  assert.equal(await page.evaluate(()=>window.fixtureCreations),1,'Empty creation callback fires exactly once');
  await page.evaluate((theme,cycle,saved)=>window.renderCyclesFixture(theme,{cycles:[{...cycle,done:true}],saved}),theme,cycle,saved);
  await page.waitForSelector('.wpb-cycle-list-summary');await page.click('.wpb-cycle-list-summary');
  assert.equal(await page.$$eval('.wpb-cycle-block-row[data-state="done"]',rows=>rows.length),3,'Completed cycle retains its full block path');
  await verifyCycleText(page,'.wpb-cycles',width);
  await page.evaluate((theme,cycle,saved)=>window.renderCyclesFixture(theme,{cycles:[cycle],saved:saved.slice(0,2)}),theme,cycle,saved);
  await page.waitForFunction(()=>document.querySelector('.wpb-cycle-block-row:last-child')?.disabled);
  await page.$eval('.wpb-cycle-block-row:last-child',b=>b.click());
  assert.deepEqual(await page.evaluate(()=>window.fixtureOpens),[],'Missing-block row cannot open a preview');
  await context.close();
 }
 assert.deepEqual(errors,[]);console.log('PASS B890 browser: real converted cycle, all blocks and weeks in Plan, read-only exercise previews, return to current and unchanged active state at 320/390/393/834/900/1280px, including 200% Cycle list/detail/preview text, keyboard and forced colors.');
}finally{if(browser)await browser.close();await new Promise(resolve=>server.close(resolve));}
