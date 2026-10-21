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
  const context=await browser.createBrowserContext();
  const page=await context.newPage(),errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.setViewport({width,height:width===834?1100:850,isMobile:width<600,hasTouch:width<600});
  await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
  await page.setBypassServiceWorker(true);
  const history=[0,1,2].map(i=>({id:'profile-fixture-'+i,date:Date.now()-i*7*86400000,unit:'lb',dayLabel:'Full Body',setsDone:4,volume:3500,durationMin:45,perf:Object.fromEntries([['bb-bench',185],['back-squat',245],['deadlift',315],['ohp',115]].map(([id,w])=>[id,{weight:w,reps:5,sets:[{w,r:5,done:true}]}]))}));
  const state={v:13,savedAt:1,saved:[],cycles:[],history,perf:{},drafts:{},tombs:{},seenIntro:999,seenWhatsNew:999,theme,unit:'lb',unitChosen:true,experience:'intermediate',bodyweight:185,installDismissedAt:Date.now(),lastBackup:Date.now()};
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
  if(hierarchy.previousSectionBottom!=null)assert.ok(hierarchy.toolbar.top>=hierarchy.previousSectionBottom-1,'section arrangement follows the card/editor without overlapping');
  if(hierarchy.gridColumns!=null){
    assert.equal(hierarchy.gridColumns,1,'no four-card strength grid');
    assert.ok(hierarchy.strengthRows.every(h=>h>=42&&h<=100),'compact strength rows');
  }
  assert.equal(hierarchy.overflow,false,'compact Profile has no horizontal overflow');
  await page.$eval('.wpb-profile-card-actions button', b=>b.click());
  await page.waitForSelector('.wpb-profile-card-editor');
  const rowEdges=await page.$$eval('.wpb-profile-strength-cell',cells=>cells.map(c=>({label:c.querySelector('.wpb-profile-strength-label').getBoundingClientRect().left,value:c.querySelector('.mono').getBoundingClientRect().right})));
  assert.ok(rowEdges.length>=2,'populated fixture shows strength comparisons');
  assert.ok(rowEdges.every(r=>Math.abs(r.label-rowEdges[0].label)<1&&Math.abs(r.value-rowEdges[0].value)<1),'highlighted Push and other scores align');
  const geometry=await page.evaluate(()=>{
   const panel=document.querySelector('[aria-label="Customize Profile sections"]');
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
  await page.evaluate(()=>document.documentElement.style.fontSize='200%');
  const enlarged=await page.$$eval('.wpb-profile .wpb-ui-action[data-action="tonal"]',buttons=>buttons.filter(b=>!b.disabled).map(b=>{
    const box=b.getBoundingClientRect(),walker=document.createTreeWalker(b,NodeFilter.SHOW_TEXT),rects=[];
    while(walker.nextNode()){const node=walker.currentNode;if(!node.textContent.trim())continue;const range=document.createRange();range.selectNodeContents(node);rects.push(...range.getClientRects());}
    return {label:b.textContent.trim(),fits:rects.every(r=>r.left>=box.left-1&&r.right<=box.right+1)};
  }));
  assert.ok(enlarged.length>10&&enlarged.every(b=>b.fits),'200% shared editor labels fit: '+JSON.stringify(enlarged));
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1),false,'200% editors do not cause horizontal overflow');
  await page.evaluate(()=>document.documentElement.style.removeProperty('font-size'));
  await page.type('.wpb-profile-card-field input','Alex Rivera');
  await page.$$eval('.wpb-profile-share-toggles button',buttons=>buttons.find(b=>b.textContent.includes('Favorite lifts')).click());
  await page.waitForSelector('.wpb-profile-card-favorites select');
  await page.select('.wpb-profile-card-favorites select','bb-bench');
  assert.match(await page.$eval('.wpb-profile-share-card',e=>e.innerText),/Alex Rivera[\s\S]*Barbell Bench Press/i,'live card reflects identity and favorite lift');
  const selected=await page.$$eval('.wpb-profile-card-metrics dt',els=>els.map(e=>e.textContent));
  await page.evaluate(()=>{
    window.__cardText=[];
    const fill=CanvasRenderingContext2D.prototype.fillText;
    CanvasRenderingContext2D.prototype.fillText=function(t,...args){window.__cardText.push(t);return fill.call(this,t,...args)};
    HTMLAnchorElement.prototype.click=function(){window.__cardImage=this.href};
  });
  await page.$eval('.wpb-profile-card-actions .wpb-profile-share-action',b=>b.click());
  const output=await page.evaluate(()=>({text:window.__cardText,image:window.__cardImage}));
  assert.ok(output.image.startsWith('data:image/png;base64,'),'share exports a PNG');
  assert.ok(output.text.includes('Alex Rivera'));
  for(const label of selected)assert.ok(output.text.includes(label.toUpperCase()),'export matches preview metric '+label);
  await page.$eval('[aria-label="Close share card editor"]',b=>b.click());
  await page.$eval('[aria-label="Close Profile customization"]',b=>b.click());
  await page.$eval('.wpb-profile',e=>e.scrollTop=0);
  if(width===393)await page.screenshot({path:root+'/verification/b915-profile-'+theme+'-phone.png'});
  await page.$eval('.wpb-profile-toolbar button',b=>b.click());
  await page.$eval('[aria-label="Move Level and XP down"]' ,b=>b.click());
  await page.waitForFunction(()=>document.querySelector('.wpb-profile-customize-row>span')?.textContent==='Lifetime summary');
  await page.$eval('[aria-label="Hide Level and XP"]',b=>b.click());
  const storage=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:profile-layout-v1')));
  assert.equal(storage.cardName,'Alex Rivera');assert.deepEqual(storage.favorites,['bb-bench']);
  assert.equal(storage.order[0],'summary');assert.ok(storage.hidden.includes('level'));
  await page.reload({waitUntil:'networkidle0'});await page.click('[data-tab="profile"]');await page.waitForSelector('.wpb-profile-toolbar button');
  await page.$eval('.wpb-profile-toolbar button',b=>b.click());await page.waitForSelector('.wpb-profile-customize-row');
  assert.equal(await page.$eval('.wpb-profile-customize-row>span',e=>e.textContent),'Lifetime summary');
  await page.$eval('[aria-label="Show Level and XP"]',b=>b.click());
  const after=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:profile-layout-v1')));
  assert.equal(after.hidden.includes('level'),false);assert.equal(after.cardName,'Alex Rivera');
  assert.match(await page.$eval('.wpb-profile-share-card',e=>e.innerText),/Alex Rivera[\s\S]*Barbell Bench Press/i);
  await page.$eval('.wpb-profile-reset',b=>b.click());
  assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:profile-layout-v1')).cardName),'Alex Rivera','resetting layout keeps the share card');
  assert.deepEqual(errors,[],'no runtime errors');await context.close();
 }
 console.log('PASS B907 browser: Profile touch geometry, phone/tablet layout, reordering and visibility persistence, two themes, share-card identity/favorites, matched PNG content, aligned strength rows.');
} finally { if(browser)await browser.close();await new Promise(r=>server.close(r)); }
