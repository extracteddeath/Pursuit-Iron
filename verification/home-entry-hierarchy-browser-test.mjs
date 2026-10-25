import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import puppeteer from 'puppeteer-core';

// Test the *visible* Home entry hierarchy, not only source string patterns.
const root=path.resolve(new URL('../',import.meta.url).pathname);
const program={id:'home-entry',name:'My Program',custom:true,weeks:4,
 config:{unit:'lb',goal:'both',experience:'intermediate',progression:'double',weeks:4,session:'s60',deload:false},
 days:[{id:'upper',label:'Upper',primaryIndex:0,exercises:['bb-bench','inc-curl']}],
 overrides:{'upper:0':{sets:3,reps:'6-8',rir:'2'}}};
const baseline={v:13,savedAt:1,saved:[],cycles:[],history:[],perf:{},drafts:{},tombs:{},
 seenIntro:999,seenWhatsNew:999,unit:'lb',unitChosen:true,experience:'intermediate',
 bodyweight:185,installDismissedAt:Date.now(),lastBackup:Date.now()};
// One real recent session makes the weekly card observable in the rendered hierarchy.
const recentSession={id:'home-entry-recent',programId:program.id,programName:program.name,
 dayId:'upper',dayLabel:'Upper',weekIndex:1,date:Date.now()-60*60*1000,
 unit:'lb',duration:1800,totalSets:2,setsDone:2,tonnage:1470,
 perf:{'bb-bench':{weight:185,reps:6,sets:[{w:185,r:6,done:true,rir:2,rirReported:true}]},
 'inc-curl':{weight:30,reps:12,sets:[{w:30,r:12,done:true,rir:2,rirReported:true}]}}};
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html','.json':'application/json',
 '.webmanifest':'application/manifest+json','.png':'image/png','.svg':'image/svg+xml'};
const server=http.createServer((req,res)=>{
 const name=new URL(req.url,'http://localhost').pathname;
 const file=path.resolve(root,'.'+(name==='/'?'/index.html':name));
 if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){
   res.writeHead(404);res.end();return;
 }
 res.setHeader('Content-Type',mime[path.extname(file)]||'application/octet-stream');
 res.end(fs.readFileSync(file));
});
await new Promise(done=>server.listen(0,'127.0.0.1',done));
let browser;
try{
 browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',
  headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
 for(const {width,theme,populated} of [
  {width:320,theme:'amethyst',populated:false},
  {width:393,theme:'rosewater',populated:false},
  {width:834,theme:'amethyst',populated:false},
  {width:393,theme:'amethyst',populated:true}
 ]){
  const ctx=await browser.createBrowserContext(),page=await ctx.newPage(),errors=[];
  page.on('pageerror',e=>errors.push(e.message));
  await page.setViewport({width,height:width<600?840:1024,isMobile:width<600,hasTouch:width<600});
  await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
  await page.evaluateOnNewDocument(state=>localStorage.setItem('wpb:v1',JSON.stringify(state)),
    {...baseline,theme,saved:populated?[program]:[],pinnedId:populated?program.id:null,
     history:populated?[recentSession]:[]});
  await page.goto('http://127.0.0.1:'+server.address().port+'/',{waitUntil:'networkidle0'});
  await page.waitForSelector('.wpb-home .wpb-action-tile');
  if(!populated){
   const observed=await page.evaluate(()=>{
    const home=document.querySelector('.wpb-home');
    const create=[...home.querySelectorAll('button')].filter(b=>b.textContent.trim()==='Create Program');
    const style=e=>{const r=e.getBoundingClientRect();return {width:r.width,height:r.height};};
    const links=[...home.querySelectorAll('.wpb-action-tile')].map(b=>({
      text:b.textContent.trim(),size:style(b),hasLabel:!!b.querySelector('.wpb-action-tile-label'),
      flex:getComputedStyle(b).display,
      iconColor:getComputedStyle(b.querySelector('.wpb-action-tile-icon')).color
    }));
    return {create:create.length,createSize:create[0]&&style(create[0]),
      oldCards:!!home.querySelector('.wpb-empty-state'),intro:home.textContent.includes('Build my program'),
      tiles:links,overflow:document.documentElement.scrollWidth>innerWidth+1};
   });
   assert.equal(observed.create,1,'One primary empty-state create action: '+JSON.stringify(observed));
   assert.ok(observed.createSize.width>=48&&observed.createSize.height>=48,
    'Persistent create dock must have a usable hit target');
   assert.equal(observed.oldCards,false,'Do not render duplicate empty-state action card');
   assert.equal(observed.intro,false,'Do not render another large clickable first-run CTA');
   assert.ok(observed.tiles.length>=2&&observed.tiles.every(t=>t.size.height>=48&&t.flex==='flex'&&t.hasLabel),
    'Discovery routes use one CSS-owned reusable tile surface');
   assert.equal(observed.overflow,false,'Home fits '+width+'px with no horizontal overflow');
   await page.locator('.wpb-home-templates').click();
   await page.waitForFunction(()=>[...document.querySelectorAll('*')].some(n=>
     n.textContent?.trim()==='Program templates' && n.children.length===0));
   await page.locator('button[aria-label="Close"]').click();
   await page.locator('.wpb-home-create').click();
   await page.waitForSelector('.wpb-wizard-step');
  }else{
   assert.ok(await page.$('.wpb-home-hero'), 'Existing program retains Next Workout hero');
   const priority=await page.evaluate(()=>{
     const home=document.querySelector('.wpb-home'),hero=home.querySelector('.wpb-home-hero');
     const game=home.querySelector('.wpb-home-game-plan');
     const weekly=[...(hero?.parentElement?.children||[])].find(el=>
       el!==hero && el.textContent.trim().startsWith('This week') &&
       el.textContent.includes('Workouts') && el.textContent.includes('Sets'));
     return {hasWeekly:!!weekly,hasGame:!!game,
       heroTop:hero?.getBoundingClientRect().top,
       weeklyTop:weekly?.getBoundingClientRect().top,
       gameTop:game?.getBoundingClientRect().top,
       weeklyCount:weekly?.textContent.includes('Workouts'),
       redundantMetrics:home.textContent.includes('Last 7 days')};
   });
   assert.ok(priority.hasWeekly&&priority.hasGame&&priority.weeklyCount&&
     priority.heroTop<priority.weeklyTop&&priority.weeklyTop<priority.gameTop,
     'Rendered Home priority must be workout, This week, then Game Plan: '+JSON.stringify(priority));
   assert.equal(priority.redundantMetrics,false,'Home does not repeat standalone 7-day pulse');
   assert.ok(await page.$('.wpb-home-session-preview'), 'Current session outline remains visible');
   const outline=await page.$('.wpb-home-outline-toggle');
   assert.ok(outline,'Populated Home retains the expand/collapse session outline');
   // The shipped Home intentionally defaults to expanded. Do not toggle an open
   // list closed and then wait for its rows; only expand when it is collapsed.
   const initiallyExpanded=await page.$eval('.wpb-home-outline-toggle',
     el=>el.getAttribute('aria-expanded')==='true');
   if(!initiallyExpanded) await page.click('.wpb-home-outline-toggle');
   await page.waitForFunction(()=>{
     const toggle=document.querySelector('.wpb-home-outline-toggle');
     const first=document.querySelector('.wpb-home-outline-list>li');
     return toggle?.getAttribute('aria-expanded')==='true' &&
       !!first && first.getBoundingClientRect().height>0;
   });
   const gap=await page.evaluate(()=>{
    const preview=document.querySelector('.wpb-home-session-preview'),
      section=document.querySelector('.wpb-home-session-outline'),
      toggle=section.querySelector('.wpb-home-outline-toggle'),
      first=section.querySelector('.wpb-home-outline-list>li');
    return {outerInset:toggle.getBoundingClientRect().top-preview.getBoundingClientRect().top,
      previewGap:preview.getBoundingClientRect().top-document.querySelector('.wpb-home-preview-action').getBoundingClientRect().bottom,
      margin:parseFloat(getComputedStyle(section).marginTop),
      padding:parseFloat(getComputedStyle(section).paddingTop),
      listGap:first.getBoundingClientRect().top-toggle.getBoundingClientRect().bottom,
      overflow:document.documentElement.scrollWidth>innerWidth+1};
   });
   assert.ok(gap.outerInset===0&&gap.previewGap<=8&&gap.margin===0&&gap.padding===0&&gap.listGap<=6,
    'No oversized gap above Home exercise list: '+JSON.stringify(gap));
   assert.equal(gap.overflow,false,'Expanded session outline never overflows phone width');

   const selector='.wpb-home-game-plan details.wpb-game-plan-highlight-copy';
   assert.ok(await page.$(selector),'Populated Home provides Game Plan coaching disclosures');
   const compact=await page.$eval(selector,el=>{
     const summary=el.querySelector('summary'),preview=el.querySelector('.wpb-game-plan-highlight-preview');
     return {open:el.open,height:summary.getBoundingClientRect().height,
       previewVisible:preview.getClientRects().length>0,fullVisible:el.querySelector('p').getClientRects().length>0,
       named:summary.querySelector('strong')?.textContent?.trim().length>0};
   });
   assert.ok(!compact.open&&compact.height>=44&&compact.previewVisible&&!compact.fullVisible&&compact.named,
     'Collapsed Game Plan is concise, tappable and hides its full explanation: '+JSON.stringify(compact));
   await page.click(selector+' summary');
   const expanded=await page.$eval(selector,el=>{
     const full=el.querySelector('p'),preview=el.querySelector('.wpb-game-plan-highlight-preview');
     return {open:el.open,visible:full.getClientRects().length>0,
       fullText:full.textContent.trim(),previewVisible:preview.getClientRects().length>0,
       overflow:document.documentElement.scrollWidth>innerWidth+1};
   });
   assert.ok(expanded.open&&expanded.visible&&expanded.fullText.length>0&&!expanded.previewVisible&&!expanded.overflow,
     'Expanded Game Plan reveals complete guidance without duplicated preview or horizontal overflow: '+JSON.stringify(expanded));
   await page.focus(selector+' summary');
   await page.keyboard.press('Enter');
   assert.equal(await page.$eval(selector,el=>el.open),false,
     'Game Plan can be collapsed using the keyboard without changing coaching state');
   await page.click(selector+' summary');
   await page.screenshot({path:path.join(root,'verification/m269-game-plan-phone.png')});
   assert.ok(await page.$('.wpb-home-game-plan') || await page.$('.wpb-home-health'),
    'Supporting Home sections remain reachable without cloning the hero');
  }
  assert.deepEqual(errors,[], 'No Home route/runtime errors '+width+' '+theme);
  await ctx.close();
 }
 console.log('PASS Home visible hierarchy: one creation CTA, usable discovery actions, onboarding and populated session paths at 320/393/834.');
}finally{
 if(browser)await browser.close();
 await new Promise(resolve=>server.close(resolve));
}
