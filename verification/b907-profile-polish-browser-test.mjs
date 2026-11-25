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
 for(const [width,height] of [[320,740],[393,852],[600,800],[834,1100],[900,480],[900,1000],[1280,850],[1600,900]])for(const theme of ['rose','rosewater']){
  const context=await browser.createBrowserContext();
  const page=await context.newPage(),errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.setViewport({width,height,isMobile:width<600,hasTouch:width<600});
  await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
  await page.setBypassServiceWorker(true);
  const history=[0,1,2].map(i=>({id:'profile-fixture-'+i,date:Date.now()-i*7*86400000,unit:'lb',dayLabel:'Full Body',setsDone:4,volume:3500,durationMin:45,perf:Object.fromEntries([['bb-bench',185],['back-squat',245],['deadlift',315],['ohp',115]].map(([id,w])=>[id,{weight:w,reps:5,sets:[{w,r:5,done:true}]}]))}));
  const state={v:13,savedAt:1,saved:[],cycles:[],history,perf:{},drafts:{},tombs:{},seenIntro:999,seenWhatsNew:999,theme,unit:'lb',unitChosen:true,experience:'intermediate',bodyweight:185,installDismissedAt:Date.now(),lastBackup:Date.now()};
  await page.evaluateOnNewDocument(s=>localStorage.setItem('wpb:v1',JSON.stringify(s)),state);
  await page.goto('http://127.0.0.1:'+server.address().port+'/index.html',{waitUntil:'networkidle0'});
  await page.waitForSelector('[data-tab="profile"]');await page.click('[data-tab="profile"]');
  await page.waitForSelector('.wpb-profile-strength-link');
  const historyBefore=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')).history);
  // B1030: Profile is identity. The score, standards and the 1,000 lb club live once, in Progress;
  // Profile carries a single row there instead of second copies.
  assert.equal(await page.$('.wpb-profile-club, .wpb-profile-stat-grid, .wpb-profile-strength-grid, .wpb-profile-toolbar'),null,
    'Profile does not repeat the Progress record');
  for(const enlarged of [false,true]){
    await page.evaluate(large=>large?document.documentElement.style.fontSize='200%':document.documentElement.style.removeProperty('font-size'),enlarged);
    if(width===393&&!enlarged){
      await page.$eval('.wpb-profile .wpb-page-scroll',e=>e.scrollTop=0);
      await page.screenshot({path:root+'/verification/m297-profile-'+theme+'-phone.png'});
    }
    // M290: XP semantics and actual fill agree, with complete level/next-level
    // copy at ordinary and enlarged text; presentation migration cannot clip it.
    const level=await page.$eval('.wpb-profile-hero',hero=>{
      const box=hero.getBoundingClientRect(),track=hero.querySelector('[role="progressbar"]'),fill=track.querySelector('.wpb-ui-wavy-active')||track.firstElementChild; // B1028: wavy indicator's active wave
      const t=track.getBoundingClientRect(),f=fill.getBoundingClientRect(),value=Number(track.getAttribute('aria-valuenow'));
      const walker=document.createTreeWalker(hero,NodeFilter.SHOW_TEXT),fragments=[];
      while(walker.nextNode()){if(!walker.currentNode.textContent.trim())continue;const range=document.createRange();range.selectNodeContents(walker.currentNode);fragments.push(...range.getClientRects());}
      return {value,roleName:track.getAttribute('aria-label'),fillError:Math.abs(f.width-t.width*value/100),
        complete:fragments.every(r=>r.left>=box.left-1&&r.right<=box.right+1&&r.top>=box.top-1&&r.bottom<=box.bottom+1),
        overflow:hero.scrollWidth>hero.clientWidth+1};
    });
    assert.ok(Number.isFinite(level.value)&&level.value>=0&&level.value<=100&&level.fillError<1&&
      /^Level \d+ progress$/.test(level.roleName)&&level.complete&&!level.overflow,
      'Level copy and canonical XP fill remain complete '+JSON.stringify({width,height,theme,enlarged,level}));
    if([320,393,1280].includes(width))await (await page.$('.wpb-profile-hero')).screenshot({
      path:root+'/verification/m290-level-'+theme+'-'+width+'x'+height+'-'+(enlarged?'enlarged':'normal')+'-phone.png'});
    // M291: headings, badges and detail sheets share the existing modal/focus
    // owner; browsing never edits history or the earned/locked state.
    const headingRoles=await page.$$eval('#profile-achievements-title',nodes=>nodes.map(n=>({role:n.getAttribute('role'),level:n.getAttribute('aria-level')})));
    assert.ok(headingRoles.length===1&&headingRoles.every(h=>h.role==='heading'&&h.level==='2'),'Profile sections expose a coherent heading outline');
    const toggle=await page.$('.wpb-profile-achievement-toggle');
    const toggleTarget=await toggle.evaluate(el=>{
      const r=el.getBoundingClientRect();
      return {width:r.width,height:r.height,left:r.left,right:r.right};
    });
    assert.ok(toggleTarget.width>=48&&toggleTarget.height>=48,
      'Achievement disclosure meets 48px touch standard: '+JSON.stringify({width,height,theme,enlarged,toggleTarget}));
    await toggle.focus();await page.keyboard.press('Space');
    assert.equal(await toggle.evaluate(e=>e.getAttribute('aria-expanded')),'true');
    await page.waitForSelector('.wpb-profile-achievement-board');
    const seams=await page.evaluate(()=>['.wpb-profile-achievement-toggle','.wpb-profile-next-achievement','.wpb-profile-achievement-board'].flatMap(selector=>{
      const e=document.querySelector(selector);if(!e)return [];
      const s=getComputedStyle(e);return [{selector,top:[s.borderTopLeftRadius,s.borderTopRightRadius],bottom:[s.borderBottomLeftRadius,s.borderBottomRightRadius],divider:s.borderTopWidth,background:s.backgroundColor}];
    }));
    assert.ok(seams.length>=2,'Profile disclosure and expanded content are rendered');
    for(const seam of seams){
      assert.ok([...seam.top,...seam.bottom].every(x=>parseFloat(x)===0),'Profile internal list corners stay square '+JSON.stringify(seam));
      assert.equal(parseFloat(seam.divider),1,'Profile preserves the internal divider');
      if(seam.selector.endsWith('board'))assert.equal(seam.background,'rgba(0, 0, 0, 0)','Expanded achievements remain on the parent surface');
    }
    const badges=await page.$$eval('.wpb-profile-achievement-tile',nodes=>nodes.map(n=>({label:n.getAttribute('aria-label'),earned:n.getAttribute('data-earned'),width:n.getBoundingClientRect().width,height:n.getBoundingClientRect().height})));
    assert.ok(badges.length>10&&badges.some(b=>b.earned==='1')&&badges.some(b=>b.earned==='0')&&badges.every(b=>b.width>=44&&b.height>=44),'earned and locked badges retain complete native targets');
    for(const earned of ['0','1']){
      const selector='.wpb-profile-achievement-tile[data-earned="'+earned+'"]';
      const tile=await page.$(selector);await tile.focus();await page.keyboard.press('Enter');
      await page.waitForSelector('.wpb-achievement-sheet');
      const dialog=await page.$eval('.wpb-profile-achievement-backdrop',backdrop=>{
        const sheet=backdrop.querySelector('.wpb-achievement-sheet'),title=document.getElementById(backdrop.getAttribute('aria-labelledby'));
        const track=sheet.querySelector('[role="progressbar"]');
        const value=track?Number(track.getAttribute('aria-valuenow')):null;
        const fillError=track?Math.abs(track.firstElementChild.getBoundingClientRect().width-track.getBoundingClientRect().width*value/100):0;
        const close=sheet.querySelector('button[aria-label="Close"]').getBoundingClientRect();
        return {role:backdrop.getAttribute('role'),modal:backdrop.getAttribute('aria-modal'),name:title?.textContent,
          nested:sheet.querySelectorAll('[role="dialog"]').length+(sheet.getAttribute('role')==='dialog'?1:0),focus:backdrop.contains(document.activeElement),
          earned:!!sheet.querySelector('.wpb-achievement-earned'),value,fillError,close:{width:close.width,height:close.height}};
      });
      assert.ok(dialog.role==='dialog'&&dialog.modal==='true'&&dialog.name&&dialog.nested===0&&dialog.focus&&dialog.close.width>=48&&dialog.close.height>=48,
        'One named detail dialog owns native focus and Close '+JSON.stringify({width,height,theme,enlarged,dialog}));
      assert.equal(dialog.earned,earned==='1');
      if(earned==='0')assert.ok(dialog.value>=0&&dialog.value<=100&&dialog.fillError<1,'Locked detail progress uses its canonical percentage');
      await page.keyboard.press('Tab');
      assert.ok(await page.$eval('.wpb-profile-achievement-backdrop',e=>e.contains(document.activeElement)),'Tab stays within the shared modal');
      if([320,393,1280].includes(width))await (await page.$('.wpb-achievement-sheet')).screenshot({path:root+'/verification/m291-detail-'+earned+'-'+theme+'-'+width+'-'+(enlarged?'enlarged':'normal')+'-phone.png'});
      await page.keyboard.press('Escape');
      await page.waitForFunction(()=>!document.querySelector('.wpb-achievement-sheet'));
      await page.waitForFunction(sel=>document.activeElement===document.querySelector(sel),{},selector);
    }
    await toggle.focus();await page.keyboard.press('Enter');
    await page.waitForFunction(()=>!document.querySelector('.wpb-profile-achievement-board'));
    const link=await page.$eval('.wpb-profile-strength-link',e=>{
      // Supporting text may clamp at 200% (Compose ListItem: two lines); the headline must stay whole.
      const r=e.getBoundingClientRect(),walker=document.createTreeWalker(e.querySelector('.wpb-ui-list-headline'),NodeFilter.SHOW_TEXT),text=[];
      while(walker.nextNode()){if(!walker.currentNode.textContent.trim())continue;const range=document.createRange();range.selectNodeContents(walker.currentNode);text.push(...range.getClientRects());}
      return {width:r.width,height:r.height,left:r.left,right:r.right,overflow:e.scrollWidth>e.clientWidth+1,
        textFits:text.every(t=>t.left>=r.left-1&&t.right<=r.right+1&&t.top>=r.top-1&&t.bottom<=r.bottom+1)};
    });
    assert.ok(link.height>=48&&link.left>=-1&&link.right<=width+1&&!link.overflow&&link.textFits,
      'Strength row is a complete 48px target '+JSON.stringify({width,height,theme,enlarged,link}));
  }
  await page.evaluate(()=>document.documentElement.style.removeProperty('font-size'));
  assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')).history),historyBefore,'Profile browsing never changes training history');
  await page.click('.wpb-profile-strength-link');
  await page.waitForFunction(()=>document.querySelector('.wpb-premium-tabs [aria-selected="true"]')?.textContent==='Strength');
  await page.click('[data-tab="profile"]');await page.waitForSelector('.wpb-profile-strength-link');

  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1),false,'compact Profile has no horizontal overflow');
  await page.$eval('.wpb-profile-card-actions button', b=>b.click());
  await page.waitForSelector('.wpb-profile-card-editor');
  // B1055: the editor is a bottom sheet; measure it once its entry motion has settled.
  await page.waitForFunction(()=>document.getAnimations().every(a=>a.playState!=='running'||a.effect?.getTiming?.().iterations===Infinity));
  // The read-only metrics explanation is a real disclosure, not a tiny caption.
  const metricSummary=await page.$eval('.wpb-profile-card-details>summary',el=>{
    const r=el.getBoundingClientRect();
    return {w:r.width,h:r.height,l:r.left,right:r.right};
  });
  assert.ok(metricSummary.w>=48&&metricSummary.h>=48&&metricSummary.l>=-1&&metricSummary.right<=width+1,
    'Profile metric explanation has a reachable 48px disclosure: '+JSON.stringify({width,theme,metricSummary}));
  await page.$eval('.wpb-profile-card-details>summary',el=>el.click());
  assert.equal(await page.$eval('.wpb-profile-card-details',el=>el.open),true,
    'Metrics explanation opens without changing card selections');
  await page.$eval('.wpb-profile-card-details>summary',el=>el.click());
  const geometry=await page.evaluate(()=>{
   const selectors=['.wpb-profile-edit','.wpb-profile-edit-sheet .wpb-ui-sheet-close','.wpb-profile-share-toggles button','.wpb-profile-strength-link'];
   // Filter chips are 32px visual with the shared 48px hit area (::after inset), as Material specifies.
   const rect=e=>{const r=e.getBoundingClientRect(),hit=e.matches('[data-choice-kind="filter"]')?getComputedStyle(e,'::after'):null;
     const dy=hit?Math.max(0,-parseFloat(hit.top)||0):0;return {w:r.width,h:r.height+2*dy,left:r.left,right:r.right}};
   return {overflow:document.documentElement.scrollWidth>innerWidth+1,targets:selectors.flatMap(s=>[...document.querySelectorAll(s)]).filter(e=>!e.disabled).map(rect)};
  });
  assert.equal(geometry.overflow,false,`no horizontal page overflow at ${width} ${theme}`);
  assert.ok(geometry.targets.length>4&&geometry.targets.every(r=>r.w>=47.5&&r.h>=47.5),`48px enabled actions at ${width} ${theme}: ${JSON.stringify(geometry.targets)}`);
  assert.ok(geometry.targets.every(r=>r.left>=-1&&r.right<=width+1),`no offscreen actions at ${width} ${theme}`);
  await page.evaluate(()=>document.documentElement.style.fontSize='200%');
  const enlarged=await page.$$eval('.wpb-profile .wpb-ui-action[data-action="tonal"],.wpb-profile-edit-sheet .wpb-ui-choice,.wpb-profile-edit-sheet .wpb-ui-action',buttons=>buttons.filter(b=>!b.disabled).map(b=>{
    const box=b.getBoundingClientRect(),walker=document.createTreeWalker(b,NodeFilter.SHOW_TEXT),rects=[];
    while(walker.nextNode()){const node=walker.currentNode;if(!node.textContent.trim())continue;const range=document.createRange();range.selectNodeContents(node);rects.push(...range.getClientRects());}
    return {label:b.textContent.trim(),fits:rects.every(r=>r.left>=box.left-1&&r.right<=box.right+1)};
  }));
  assert.ok(enlarged.length>6&&enlarged.every(b=>b.fits),'200% shared editor labels fit: '+JSON.stringify(enlarged));
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1),false,'200% editors do not cause horizontal overflow');
  await page.evaluate(()=>document.documentElement.style.removeProperty('font-size'));
  await page.type('.wpb-profile-card-field input','Alex Rivera');
  await page.$$eval('.wpb-profile-share-toggles button',buttons=>buttons.find(b=>b.textContent.includes('Favorite lifts')).click());
  await page.waitForSelector('.wpb-profile-card-favorites select');
  await page.select('.wpb-profile-card-favorites select','bb-bench');
  assert.match(await page.$eval('.wpb-profile-person',e=>e.innerText),/Alex Rivera/,'live profile reflects the name');
  assert.match(await page.$eval('.wpb-profile-share-card',e=>e.innerText),/Barbell Bench Press/i,'live card reflects the favorite lift');
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
  await page.$eval('.wpb-profile-edit-sheet .wpb-ui-sheet-close',b=>b.click());
  await page.$eval('.wpb-profile',e=>e.scrollTop=0);
  if(width===393)await page.screenshot({path:root+'/verification/b915-profile-'+theme+'-phone.png'});
  const storage=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:profile-layout-v1')));
  assert.equal(storage.cardName,'Alex Rivera');assert.deepEqual(storage.favorites,['bb-bench']);
  await page.reload({waitUntil:'networkidle0'});await page.click('[data-tab="profile"]');await page.waitForSelector('.wpb-profile-strength-link');
  assert.match(await page.$eval('.wpb-profile-person',e=>e.innerText)+await page.$eval('.wpb-profile-share-card',e=>e.innerText),/Alex Rivera[\s\S]*Barbell Bench Press/i,'profile name and share card persist');
  assert.deepEqual(errors,[],'no runtime errors');await context.close();
 }
 console.log('PASS B907 browser: Profile identity card, one 48px link to the Progress strength record, phone/tablet layout at 200% text, two themes, share-card identity/favorites persistence and matched PNG content.');
} finally { if(browser)await browser.close();await new Promise(r=>server.close(r)); }
