import assert from 'node:assert/strict';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import puppeteer from 'puppeteer-core';

const root=path.resolve(new URL('../',import.meta.url).pathname);
const css=fs.readFileSync(new URL('../app.css',import.meta.url),'utf8');
const fixture='<!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">'+
  '<link rel="stylesheet" href="/app.css"><style>body{margin:0;padding:12px;box-sizing:border-box}*{box-sizing:border-box}</style>'+
  '<div id="root" data-pi-motion="expressive" style="--pi-color-control:#30243b;--pi-color-card:#201927;--pi-color-divider:#6c536d;--wpb-theme-border:#6c536d;--wpb-theme-accent-dim:#59426c;--wpb-theme-accent-ink:#edd0ff;--pi-ease:ease-out">'+
  '<div class="wpb"><section class="wpb-progress"><details class="wpb-chart-favorites" open><summary>Favorite charts</summary><div class="wpb-chart-favorites-content">'+
  '<div class="wpb-chart-favorite-row"><span class="wpb-chart-favorite-name">Very long incline dumbbell curl strength performance chart</span>'+
  '<button class="wpb-ui-action wpb-chart-favorite-action" data-action="tonal" data-icon-only="true" aria-label="Move up">↑</button><button class="wpb-ui-action wpb-chart-favorite-action" data-action="tonal" data-icon-only="true" aria-label="Move down">↓</button><button class="wpb-ui-action wpb-chart-favorite-action" data-action="tonal" data-icon-only="true" aria-label="Unfavorite">×</button></div>'+
  '<button class="wpb-ui-action wpb-chart-favorite-add" data-action="tonal" data-align="start">Add an exercise chart</button></div></details></section></div></div>';
const server=http.createServer((req,res)=>{if(req.url==='/app.css'){res.setHeader('Content-Type','text/css');res.end(css);}else if(req.url==='/'){res.setHeader('Content-Type','text/html');res.end(fixture);}else{const file=path.resolve(root,'.'+new URL(req.url,'http://localhost').pathname);if(!file.startsWith(root+path.sep)||!fs.existsSync(file)){res.writeHead(404);res.end();return;}res.setHeader('Content-Type',({'.js':'text/javascript','.css':'text/css','.html':'text/html','.json':'application/json','.webmanifest':'application/manifest+json','.png':'image/png','.svg':'image/svg+xml'})[path.extname(file)]||'application/octet-stream');res.end(fs.readFileSync(file));}});
await new Promise(r=>server.listen(0,'127.0.0.1',r));
let browser;
try{
  browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
  for(const width of [320,393,834])for(const motion of ['no-preference','reduce']){
    const page=await browser.newPage();
    await page.setViewport({width,height:800,isMobile:width<600,hasTouch:width<600});
    await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:motion}]);
    await page.goto('http://127.0.0.1:'+server.address().port+'/',{waitUntil:'load'});
    const x=await page.evaluate(()=>{
      const get=s=>[...document.querySelectorAll(s)].map(el=>{
        const r=el.getBoundingClientRect(),cs=getComputedStyle(el);
        return {width:r.width,height:r.height,left:r.left,right:r.right,transition:cs.transitionProperty,duration:cs.transitionDuration};
      });
      return {overflow:document.documentElement.scrollWidth>innerWidth+1,buttons:get('.wpb-chart-favorite-action,.wpb-chart-favorite-add'),name:get('.wpb-chart-favorite-name')[0],row:get('.wpb-chart-favorite-row')[0]};
    });
    assert.equal(x.overflow,false,'no horizontal page overflow at '+width);
    assert.ok(x.buttons.every(b=>b.width>=47.5&&b.height>=47.5),'48px controls at '+width+': '+JSON.stringify(x.buttons));
    assert.ok(x.buttons.every(b=>b.left>=-1&&b.right<=width+1),'offscreen controls at '+width);
    assert.ok(x.name.width>25&&x.name.right<=x.row.right,'wrapping title stays in row at '+width);
    if(motion==='reduce')assert.ok(x.buttons.every(b=>b.duration.split(',').every(d=>parseFloat(d)===0)),'reduced motion disables transition');
    else assert.ok(x.buttons.every(b=>!b.transition.includes('transform')&&!b.transition.includes('width')),'no geometry animation');
    await page.close();
  }
  for(const theme of ['rose','rosewater'])for(const width of [320,393,834,1280]){
    const context=await browser.createBrowserContext(),page=await context.newPage();
    await page.setViewport({width,height:852,isMobile:width<600,hasTouch:width<600});
    await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
    const now=Date.now(),day=86400000;
    const history=Array.from({length:3},(_,i)=>({id:'shared-control-fixture-'+i,date:now-i*7*day,updatedAt:now-i*7*day,unit:'lb',dayLabel:'Full Body',sessionNote:i===0?'Retain the complete session note.':undefined,setsDone:4,volume:4000,perf:Object.fromEntries([['bb-bench',185-i*5],['back-squat',245-i*5]].map(([id,w])=>[id,{weight:w,reps:5,sets:[{w,r:5,done:true,rir:2,tr:2},{w,r:5,done:true,rir:2,tr:2}]}]))}));
    const bwLog=[{date:now-10*day,w:184,unit:'lb'},{date:now-3*day,w:185,unit:'lb'}];
    const measurements={waist:[{date:now-10*day,v:36,unit:'in'},{date:now-3*day,v:35.8,unit:'in'}]};
    const store={v:13,savedAt:1,saved:[],cycles:[],history,bwLog,measurements,perf:{},drafts:{},tombs:{},seenIntro:999,seenWhatsNew:999,theme,unit:'lb',unitChosen:true,experience:'intermediate',bodyweight:185,installDismissedAt:now,lastBackup:now};
    await page.evaluateOnNewDocument(store=>{if(!sessionStorage.getItem('fixture-seeded')){localStorage.setItem('wpb:v1',JSON.stringify(store));localStorage.setItem('wpb:chart-favorites-v1',JSON.stringify(['bb-bench','back-squat']));sessionStorage.setItem('fixture-seeded','1');}},store);
    const open=async()=>{await page.click('[data-tab="progress"]');await page.waitForSelector('.wpb-premium-tabs button');await page.$$eval('.wpb-premium-tabs button',buttons=>buttons.find(b=>b.textContent.includes('Lifts')).click());await page.waitForSelector('.wpb-chart-favorites');await page.$eval('.wpb-chart-favorites',e=>e.open=true);};
    await page.goto('http://127.0.0.1:'+server.address().port+'/index.html',{waitUntil:'networkidle0'});await open();
    const before=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')).history);
    assert.equal(await page.$eval('.wpb-chart-favorite-action',b=>b.disabled),true,'first move-up remains disabled');
    assert.ok(await page.$$eval('.wpb-chart-favorite-action,.wpb-chart-favorite-add',bs=>bs.every(b=>b.dataset.action==='tonal')),'actual chart actions use the shared native button');
    await page.$eval('.wpb-chart-favorite-row:first-child button[aria-label$=" down"]',b=>b.click());
    assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:chart-favorites-v1'))),['back-squat','bb-bench'],'shared Move forwards its callback');
    await page.$eval('.wpb-chart-favorite-row:first-child button[aria-label^="Unfavorite"]',b=>b.click());
    assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:chart-favorites-v1'))),['bb-bench'],'shared Unfavorite forwards its callback');
    await page.$$eval('.wpb-chart-favorite-add',buttons=>buttons.find(b=>b.textContent.includes('Squat')).click());
    assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:chart-favorites-v1'))),['bb-bench','back-squat'],'shared Add forwards its callback');
    assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')).history),before,'editor callbacks cannot rewrite training records');
    // M292: exercise the actual Progress panes and native controls after the
    // fixed presentation migrates to CSS. Only explicit body logs may change data.
    const chooseTab=async label=>{
      const index=['Sessions','Lifts','Volume','Body'].indexOf(label)+1;
      const button=await page.$('.wpb-premium-tabs button:nth-child('+index+')');
      await button.focus();await page.keyboard.press('Enter');
      assert.equal(await button.evaluate(b=>b.getAttribute('aria-selected')),'true','native keyboard selects '+label);
    };
    const choosePane=async id=>{const button=await page.$('[data-lift-pane="'+id+'"]');await button.focus();await page.keyboard.press('Enter');assert.equal(await button.evaluate(b=>b.getAttribute('aria-current')),'true');};
    for(const enlarged of [false,true]){
      await page.evaluate(large=>document.documentElement.style.fontSize=large?'200%':'100%',enlarged);
      await chooseTab('Sessions');
      assert.equal(await page.$$eval('.wpb-history-session',rows=>rows.length),3,'all logged sessions remain available');
      const session=await page.$('.wpb-history-session button[aria-expanded]');
      await session.focus();await page.keyboard.press('Enter');await page.waitForSelector('.wpb-history-session-detail');
      assert.ok(await page.$eval('.wpb-history-session-detail',e=>e.textContent.includes('Retain the complete session note.')&&e.querySelectorAll('.wpb-history-set').length===4),'expanded history retains notes and every working set');
      const actions=await page.$$eval('.wpb-history-actions button',bs=>bs.map(b=>({name:b.getAttribute('aria-label'),width:b.getBoundingClientRect().width,height:b.getBoundingClientRect().height})));
      assert.ok(actions.every(b=>b.name&&b.width>=44&&b.height>=44),'history actions retain named native targets '+JSON.stringify({width,theme,enlarged,actions}));
      await session.focus();await page.keyboard.press('Enter');await page.waitForFunction(()=>!document.querySelector('.wpb-history-session-detail'));
      await page.focus('#history-search');await page.keyboard.type('no matching workout');
      await page.waitForFunction(()=>!document.querySelector('.wpb-history-session'));
      const clear=await page.$('.wpb-progress-clear-filters');await clear.focus();await page.keyboard.press('Enter');
      await page.waitForFunction(()=>document.querySelectorAll('.wpb-history-session').length===3);
      await chooseTab('Lifts');await choosePane('records');
      assert.ok(await page.$eval('.wpb-progress .wpb-page-scroll',e=>e.textContent.includes('Personal records')),'real lift records remain available');
      await choosePane('load');
      assert.ok(await page.$eval('.wpb-progress .wpb-page-scroll',e=>e.textContent.includes('Intensity distribution')),'canonical logged-set distribution remains visible');
      await choosePane('recovery');await page.waitForSelector('[data-scroll-key="lifts/recovery"]');
      await chooseTab('Volume');
      assert.ok(await page.$eval('.wpb-progress .wpb-page-scroll',e=>e.textContent.includes('Volume · last 7 days')),'weekly volume remains in its existing pane');
      await chooseTab('Body');
      const weight=await page.$('input[placeholder^="Today"]'),waist=await page.$('input[aria-label="Log Waist in in"]');
      const bodyLog=await page.$('.wpb-progress-body-log'),measureLog=await page.$('.wpb-progress-measure-row button.wpb-progress-measure-log');
      assert.ok(await bodyLog.evaluate(b=>b.disabled)&&await measureLog.evaluate(b=>b.disabled),'empty numeric fields keep Log disabled');
      await weight.focus();await page.keyboard.type(enlarged?'186.6':'186.5');
      assert.equal(await bodyLog.evaluate(b=>b.disabled),false,'valid bodyweight enables its existing Log');
      await bodyLog.focus();await page.keyboard.press('Enter');
      await page.waitForFunction(w=>JSON.parse(localStorage.getItem('wpb:v1')).bwLog.at(-1).w===w,{},enlarged?186.6:186.5);
      await waist.focus();await page.keyboard.type(enlarged?'35.6':'35.5');
      assert.equal(await measureLog.evaluate(b=>b.disabled),false,'valid measurement enables its existing Log');
      await measureLog.focus();await page.keyboard.press('Enter');
      await page.waitForFunction(v=>JSON.parse(localStorage.getItem('wpb:v1')).measurements.waist.at(-1).v===v,{},enlarged?35.6:35.5);
      const bodyState=await page.evaluate(()=>{const s=JSON.parse(localStorage.getItem('wpb:v1'));return{weights:s.bwLog,waist:s.measurements.waist,expanded:document.querySelector('.wpb-progress-measure-toggle')?.getAttribute('aria-expanded')};});
      assert.ok(bodyState.weights.length===3&&bodyState.waist.length===3&&bodyState.weights.at(-1).unit==='lb'&&bodyState.waist.at(-1).unit==='in'&&bodyState.expanded==='true','same-day logging keeps one canonical dated entry and expands the existing chart');
      assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')).history),before,'Progress inspection and explicit body logs never rewrite workout history');
      if(width===320||width===1280)await page.screenshot({path:root+'/verification/m292-body-'+theme+'-'+width+'-'+(enlarged?'large':'normal')+'-phone.png'});
    }
    await page.reload({waitUntil:'networkidle0'});await open();
    assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:chart-favorites-v1'))),['bb-bench','back-squat'],'order persists after reload');
    const after=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')).history);
    const records=rows=>rows.map(({updatedAt,...record})=>record);
    assert.deepEqual(records(after),records(before),'reload preserves complete training content; sync timestamps remain owned by startup');
    assert.ok(await page.evaluate(()=>{const s=JSON.parse(localStorage.getItem('wpb:v1'));return s.bwLog.at(-1).w===186.6&&s.measurements.waist.at(-1).v===35.6;}),'explicit body records persist after reload');
    await context.close();
  }
  console.log('PASS B911 browser: chart favorites, native Progress tabs, session search/details, lift/load/recovery/volume panes, body logging, same-day replacement, normal/enlarged text and persistence without history changes.');
} finally {if(browser)await browser.close();await new Promise(r=>server.close(r));}
