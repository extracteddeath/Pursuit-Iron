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
  await page.waitForSelector('.wpb-profile-toolbar button');
  // Cohesion 20: the real club card keeps distinct read-only totals and native keyboard controls.
  const club='.wpb-profile-club';
  await page.waitForSelector(club);
  const historyBefore=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')).history);
  const total=await page.$eval(club+' .wpb-profile-club-value',e=>e.textContent);
  const estimated=await page.$(club+' .wpb-profile-club-mode button:nth-child(2)');
  await estimated.focus();await page.keyboard.press('Enter');
  assert.equal(await estimated.evaluate(e=>e.getAttribute('aria-pressed')),'true','keyboard selects estimated measure');
  assert.notEqual(await page.$eval(club+' .wpb-profile-club-value',e=>e.textContent),total,'estimated total differs from actual lifted weight');
  assert.match(await page.$eval(club+' .wpb-profile-club-caption',e=>e.textContent),/estimated 1RM/);
  const lifted=await page.$(club+' .wpb-profile-club-mode button:first-child');
  await lifted.focus();await page.keyboard.press('Space');
  assert.equal(await lifted.evaluate(e=>e.getAttribute('aria-pressed')),'true','keyboard restores Lifted');
  assert.equal(await page.$eval(club+' .wpb-profile-club-value',e=>e.textContent),total);
  for(const enlarged of [false,true]){
    await page.evaluate(large=>large?document.documentElement.style.fontSize='200%':document.documentElement.style.removeProperty('font-size'),enlarged);
    if(width===393&&!enlarged){
      await page.$eval('.wpb-profile .wpb-page-scroll',e=>e.scrollTop=0);
      await page.screenshot({path:root+'/verification/m297-profile-'+theme+'-phone.png'});
    }
    // M290: XP semantics and actual fill agree, with complete level/next-level
    // copy at ordinary and enlarged text; presentation migration cannot clip it.
    const level=await page.$eval('.wpb-profile-hero',hero=>{
      const box=hero.getBoundingClientRect(),track=hero.querySelector('[role="progressbar"]'),fill=track.firstElementChild;
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
    const headingRoles=await page.$$eval('#profile-strength-title,#profile-achievements-title',nodes=>nodes.map(n=>({role:n.getAttribute('role'),level:n.getAttribute('aria-level')})));
    assert.ok(headingRoles.length===2&&headingRoles.every(h=>h.role==='heading'&&h.level==='2'),'Profile sections expose a coherent heading outline');
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
    // M288: lifetime totals retain one grouped surface at phone, tablet,
    // short landscape and rail sizes, including actual 200% root text.
    const summary=await page.$eval('.wpb-profile-stat-grid',e=>{
      const box=e.getBoundingClientRect();
      return {count:e.children.length,columns:getComputedStyle(e).gridTemplateColumns.split(' ').length,
        overflow:e.scrollWidth>e.clientWidth+1,
        cells:[...e.children].map(c=>{
          const r=c.getBoundingClientRect(),walker=document.createTreeWalker(c,NodeFilter.SHOW_TEXT),text=[];
          while(walker.nextNode()){
            if(!walker.currentNode.textContent.trim())continue;
            const range=document.createRange();range.selectNodeContents(walker.currentNode);text.push(...range.getClientRects());
          }
          return {contained:r.left>=box.left-1&&r.right<=box.right+1,
            textFits:text.every(t=>t.left>=r.left-1&&t.right<=r.right+1&&t.top>=r.top-1&&t.bottom<=r.bottom+1)};
        })};
    });
    assert.ok(summary.count===3&&[1,3].includes(summary.columns)&&!summary.overflow&&
      summary.cells.every(c=>c.contained&&c.textFits),
      'Lifetime summary values/labels remain complete '+JSON.stringify({width,height,theme,enlarged,summary}));
    if([320,393,1280].includes(width))await (await page.$('.wpb-profile-stat-grid')).screenshot({
      path:root+'/verification/m288-summary-'+theme+'-'+width+'x'+height+'-'+(enlarged?'enlarged':'normal')+'-phone.png'});
    // M289: the featured score shares label/value edges with every other row,
    // including enlarged text; the share action retains its native 48px target.
    const strength=await page.$eval('.wpb-profile-strength',section=>{
      const box=section.getBoundingClientRect();
      const rows=[...section.querySelectorAll('.wpb-profile-strength-cell')].map(cell=>{
        const r=cell.getBoundingClientRect(),label=cell.querySelector('.wpb-profile-strength-label'),value=cell.querySelector('.mono');
        const l=label.getBoundingClientRect(),v=value.getBoundingClientRect();
        const range=document.createRange();range.selectNodeContents(label);
        return {left:l.left,right:v.right,contained:r.left>=box.left-1&&r.right<=box.right+1,
          textFits:[...range.getClientRects()].every(t=>t.left>=r.left-1&&t.right<=r.right+1&&t.top>=r.top-1&&t.bottom<=r.bottom+1)};
      });
      const share=section.querySelector('button[aria-label="Share Strength Score"]').getBoundingClientRect();
      return {rows,share:{width:share.width,height:share.height},overflow:section.scrollWidth>section.clientWidth+1};
    });
    assert.ok(strength.rows.length>=2&&!strength.overflow&&
      strength.rows.every(r=>r.contained&&r.textFits&&Math.abs(r.left-strength.rows[0].left)<1&&Math.abs(r.right-strength.rows[0].right)<1)&&
      strength.share.width>=48&&strength.share.height>=48,
      'Strength rows and share control stay aligned and complete '+JSON.stringify({width,height,theme,enlarged,strength}));
    if([320,393,1280].includes(width))await (await page.$('.wpb-profile-strength')).screenshot({
      path:root+'/verification/m289-strength-'+theme+'-'+width+'x'+height+'-'+(enlarged?'enlarged':'normal')+'-phone.png'});
    const card=await page.$eval(club,e=>{
      const box=e.getBoundingClientRect();
      const controls=[...e.querySelectorAll('button')].map(b=>{
        const r=b.getBoundingClientRect(),range=document.createRange();range.selectNodeContents(b);
        return {width:r.width,height:r.height,fits:[...range.getClientRects()].every(t=>t.left>=r.left-1&&t.right<=r.right+1&&t.top>=r.top-1&&t.bottom<=r.bottom+1)};
      });
      return {overflow:e.scrollWidth>e.clientWidth+1,controls,
        values:[...e.querySelectorAll('.wpb-profile-club-lift')].every(l=>{const r=l.getBoundingClientRect();return r.left>=box.left-1&&r.right<=box.right+1})};
    });
    assert.ok(!card.overflow&&card.values&&card.controls.every(b=>b.width>=47.5&&b.height>=47.5&&b.fits),
      'club has complete labels, 48px controls and readable values '+JSON.stringify({width,theme,enlarged,card}));
    if(width===320)await (await page.$(club)).screenshot({path:root+'/verification/c20-club-'+theme+'-'+(enlarged?'enlarged':'normal')+'-phone.png'});
  }
  await page.evaluate(()=>document.documentElement.style.removeProperty('font-size'));
  assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')).history),historyBefore,'club browsing never changes training history');

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
  await page.$eval('[aria-label="Move Lifetime summary down"]' ,b=>b.click());
  await page.waitForFunction(()=>document.querySelector('.wpb-profile-customize-row>span')?.textContent==='Level and XP');
  await page.$eval('[aria-label="Hide Level and XP"]',b=>b.click());
  const storage=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:profile-layout-v1')));
  assert.equal(storage.cardName,'Alex Rivera');assert.deepEqual(storage.favorites,['bb-bench']);
  assert.ok(storage.order.indexOf('strength')<storage.order.indexOf('summary'));assert.ok(storage.hidden.includes('level'));
  await page.reload({waitUntil:'networkidle0'});await page.click('[data-tab="profile"]');await page.waitForSelector('.wpb-profile-toolbar button');
  await page.$eval('.wpb-profile-toolbar button',b=>b.click());await page.waitForSelector('.wpb-profile-customize-row');
  assert.equal(await page.$eval('.wpb-profile-customize-row>span',e=>e.textContent),'Level and XP');
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
