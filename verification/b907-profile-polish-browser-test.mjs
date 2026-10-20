import assert from 'node:assert/strict';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import puppeteer from 'puppeteer-core';
const root=path.resolve(new URL('../',import.meta.url).pathname);
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html','.json':'application/json','.webmanifest':'application/manifest+json','.png':'image/png','.svg':'image/svg+xml'};
const server=http.createServer((req,res)=>{const f=path.resolve(root,'.'+new URL(req.url,'http://localhost').pathname);if(!f.startsWith(root+path.sep)||!fs.existsSync(f)||!fs.statSync(f).isFile()){res.writeHead(404);res.end();return;}res.setHeader('Content-Type',mime[path.extname(f)]||'application/octet-stream');res.end(fs.readFileSync(f));});
await new Promise(r=>server.listen(0,'127.0.0.1',r));
let browser;
try{
 browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
 for(const width of [320,393,834])for(const theme of ['rose','rosewater']){
  const page=await browser.newPage(),errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.setViewport({width,height:width===834?1100:850,isMobile:width<600,hasTouch:width<600});
  await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
  await page.setBypassServiceWorker(true);
  const state={v:13,savedAt:1,saved:[],cycles:[],history:[],perf:{},drafts:{},tombs:{},seenIntro:999,seenWhatsNew:999,theme,unit:'lb',unitChosen:true,experience:'intermediate',bodyweight:185,installDismissedAt:Date.now(),lastBackup:Date.now()};
  await page.evaluateOnNewDocument(s=>localStorage.setItem('wpb:v1',JSON.stringify(s)),state);
  await page.goto('http://127.0.0.1:'+server.address().port+'/index.html',{waitUntil:'networkidle0'});
  await page.waitForSelector('[data-tab="profile"]');await page.click('[data-tab="profile"]');
  await page.waitForSelector('.wpb-profile-toolbar button');
  await page.$eval('.wpb-profile-toolbar button',b=>b.click());
  await page.waitForSelector('.wpb-profile-customize-row');

  const hierarchy=await page.evaluate(()=>{
    const rect=s=>document.querySelector(s)?.getBoundingClientRect().toJSON();
    const strength=document.querySelector('.wpb-profile-strength-grid');
    return {toolbarOrder:getComputedStyle(document.querySelector('.wpb-profile-toolbar')).order,
      toolbar:rect('.wpb-profile-toolbar'),previousSectionBottom:document.querySelector('.wpb-profile-toolbar')?.previousElementSibling?.getBoundingClientRect().bottom??null,summary:rect('.wpb-profile-stat-grid'),
      gridColumns:strength?getComputedStyle(strength).gridTemplateColumns.split(' ').length:null,
      strengthRows:strength?[...strength.children].map(c=>c.getBoundingClientRect().height):[],
      overflow:document.documentElement.scrollWidth>innerWidth+1};
  });
  assert.equal(hierarchy.toolbarOrder,'0','secondary actions retain DOM and keyboard reading order');
  if(hierarchy.previousSectionBottom!=null)assert.ok(hierarchy.toolbar.top>=hierarchy.previousSectionBottom-1,'secondary actions follow the first user-selected profile section without overlapping');
  if(hierarchy.gridColumns!=null){
    assert.equal(hierarchy.gridColumns,1,'no four-card strength grid');
    assert.ok(hierarchy.strengthRows.every(h=>h>=42&&h<=100),'compact strength rows');
  }
  assert.equal(hierarchy.overflow,false,'compact Profile has no horizontal overflow');
  const geometry=await page.evaluate(()=>{
   const panel=document.querySelector('.wpb-profile-customize');
   const selectors=['.wpb-profile-edit','.wpb-profile-customize-header>button','.wpb-profile-customize-row button','.wpb-profile-share-toggles button','.wpb-profile-reset'];
   const rect=e=>{const r=e.getBoundingClientRect();return {w:r.width,h:r.height,left:r.left,right:r.right}};
   return {overflow:document.documentElement.scrollWidth>innerWidth+1,rows:[...panel.querySelectorAll('.wpb-profile-customize-row')].map(rect),targets:selectors.flatMap(s=>[...document.querySelectorAll(s)]).filter(e=>!e.disabled).map(e=>({selector:e.getAttribute('aria-label')||e.textContent.trim(),...rect(e)})),
    cols:getComputedStyle(panel.querySelector('.wpb-profile-customize-row')).gridTemplateColumns.split(' ').length,
    moveDisabled:panel.querySelector('.wpb-profile-customize-row button').disabled};
  });
  assert.equal(geometry.overflow,false,`no horizontal page overflow at ${width} ${theme}`);
  assert.equal(geometry.moveDisabled,true,'first up button disabled');
  assert.ok(geometry.targets.every(r=>r.w>=47.5&&r.h>=47.5),`48px enabled actions at ${width} ${theme}: ${JSON.stringify(geometry.targets)}`);
  assert.ok(geometry.targets.every(r=>r.left>=-1&&r.right<=width+1),`no offscreen actions at ${width} ${theme}`);
  if(width===320)assert.equal(geometry.cols,3,'320px profile controls wrap beneath full-width row title');
  await page.$eval('[aria-label="Move Level and XP down"]',b=>b.click());
  await page.waitForFunction(()=>document.querySelector('.wpb-profile-customize-row>span')?.textContent==='Lifetime summary');
  await page.$eval('[aria-label="Hide Level and XP"]',b=>b.click());
  const storage=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:profile-layout-v1')));
  assert.equal(storage.order[0],'summary');assert.ok(storage.hidden.includes('level'));
  await page.reload({waitUntil:'networkidle0'});await page.click('[data-tab="profile"]');await page.waitForSelector('.wpb-profile-toolbar button');
  await page.$eval('.wpb-profile-toolbar button',b=>b.click());await page.waitForSelector('.wpb-profile-customize-row');
  assert.equal(await page.$eval('.wpb-profile-customize-row>span',e=>e.textContent),'Lifetime summary');
  await page.$eval('[aria-label="Show Level and XP"]',b=>b.click());
  const after=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:profile-layout-v1')));
  assert.equal(after.hidden.includes('level'),false);
  assert.deepEqual(errors,[],'no runtime errors');await page.close();
 }
 console.log('PASS B907 browser: Profile touch geometry, phone/tablet layout, reordering and visibility persistence, two themes.');
} finally { if(browser)await browser.close();await new Promise(r=>server.close(r)); }
