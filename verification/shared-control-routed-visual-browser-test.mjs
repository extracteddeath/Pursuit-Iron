// Post-B1000 pilot: capture actual routed Settings and Gallery UI, not only isolated controls.
// Uses an entirely synthetic seeded training plan. Screenshots are GitHub Actions artifacts;
// this checks geometry/state but does not claim subjective visual or physical Android approval.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import http from 'node:http';
import puppeteer from 'puppeteer-core';

const root=path.resolve(new URL('../',import.meta.url).pathname);
const program={
  id:'shared-pilot-visual',name:'Shared Controls Test Plan',custom:true,weeks:4,
  config:{name:'Shared Controls Test Plan',goal:'both',experience:'intermediate',
    progression:'manual',split:'custom',weeks:4,days:1,session:'s60',unit:'lb',deload:false},
  days:[{id:'upper',label:'Upper',primaryIndex:0,exercises:['bb-bench','inc-curl']}],
  overrides:{'upper:0':{sets:3,reps:'6-8',rir:'2'},'upper:1':{sets:2,reps:'10-15',rir:'2'}}
};
const state=theme=>({
  v:13,savedAt:1,saved:[program],cycles:[],history:[],perf:{},drafts:{},tombs:{},
  seenIntro:999,seenWhatsNew:999,pinnedId:program.id,theme,unit:'lb',unitChosen:true,
  experience:'intermediate',bodyweight:185,installDismissedAt:Date.now(),lastBackup:Date.now()
});
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html',
  '.json':'application/json','.png':'image/png','.svg':'image/svg+xml'};
const server=http.createServer((req,res)=>{
  const requested=new URL(req.url,'http://localhost').pathname;
  const file=path.resolve(root,'.'+(requested==='/'?'/index.html':requested));
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
  const origin='http://127.0.0.1:'+server.address().port+'/';
  for(const theme of ['rose','rosewater']){
    for(const [width,height] of [[320,740],[393,850],[834,850],[900,480],[1280,850]]){
      const context=await browser.createBrowserContext();
      const page=await context.newPage(),errors=[];
      page.on('pageerror',error=>errors.push(error.message));
      try{
        await page.setViewport({width,height,
          deviceScaleFactor:1,isMobile:width<=393,hasTouch:width<=393});
        await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
        await page.evaluateOnNewDocument(seed=>
          localStorage.setItem('wpb:v1',JSON.stringify(seed)),state(theme));
        await page.goto(origin,{waitUntil:'networkidle0'});
        await page.waitForSelector('.wpb-home');
        await page.click('[data-tab="settings"]');
        await page.waitForSelector('.wpb-settings');

        const layout=()=>page.evaluate(()=>{
          const root=document.documentElement;
          return {width:innerWidth,scrollWidth:root.scrollWidth};
        });
        const top=await layout();
        assert.ok(top.scrollWidth<=width+2,'Settings spills horizontally: '+JSON.stringify({theme,width,top}));
        const nav=await page.$$eval('.wpb-settings-navigation',nodes=>nodes.map(node=>{
          const rect=node.getBoundingClientRect();
          return {text:node.textContent.trim(),height:rect.height,width:rect.width,
            selected:node.getAttribute('data-action')};
        }));
        assert.equal(nav.length,5,'All five Settings destinations use the same shared action');
        assert.ok(nav.some(row=>row.text.includes('Your gyms')),'Gym navigation keeps its reachable entry point');
        assert.ok(nav.every(row=>row.height>=48) && nav.filter(row=>row.selected==='tonal').length===4 &&
          nav.some(row=>row.text.includes('Your gyms')&&row.selected==='text'),
          'Four standalone tonal actions and a contained text action retain 48px hit targets: '+JSON.stringify(nav));
        if(width===320||width===834||width===900){
          await page.screenshot({path:path.join(root,`verification/shared-pilot-settings-top-${theme}-${width}.png`)});
        }
        // Inspect real routed fields, not only the isolated component fixture.
        const initialFields=await page.evaluate(()=>['input[aria-label^="Bodyweight ("]','input[aria-label="Birthday"]'].map(selector=>{
          const el=document.querySelector('.wpb-settings '+selector);
          if(!el)return null;
          const rect=el.getBoundingClientRect(),style=getComputedStyle(el);
          return {height:rect.height,width:rect.width,font:parseFloat(style.fontSize),
            shared:el.classList.contains('wpb-ui-control'),value:el.value};
        }));
        assert.ok(initialFields.every(field=>field?.shared&&field.height>=48&&field.font>=16),
          'Profile fields retain 48px native focus and at least 16px text: '+JSON.stringify(initialFields));
        // Confirm the shared segmented primitive preserves six exclusive button groups,
        // and that real preference setters still respond to interaction.
        const segments=await page.$$eval('.wpb-settings .wpb-segmented',groups=>groups.map(g=>({
          label:g.getAttribute('aria-label'),buttons:g.querySelectorAll('button').length,
          active:g.querySelectorAll('button[aria-pressed="true"]').length
        })));
        assert.equal(segments.length,6,'All six Settings segmented groups share their original track');
        assert.ok(segments.every(g=>g.label&&g.buttons>=2&&g.active===1),
          'Each Settings segmented group stays an exclusive named choice: '+JSON.stringify(segments));
        const effort='.wpb-settings .wpb-segmented[aria-label="Effort scale"] > button';
        await page.click(effort+':nth-of-type(2)');
        await page.waitForFunction(selector=>
          document.querySelector(selector)?.getAttribute('aria-pressed')==='true',
          {},effort+':nth-of-type(2)');
        await page.click(effort+':nth-of-type(1)');
        await page.waitForFunction(selector=>
          document.querySelector(selector)?.getAttribute('aria-pressed')==='true',
          {},effort+':nth-of-type(1)');
        const jumps='.wpb-settings .wpb-segmented[aria-label="Smallest jump"] > button';
        await page.click(jumps+':nth-of-type(2)');
        await page.waitForFunction(selector=>
          document.querySelector(selector)?.getAttribute('aria-pressed')==='true',
          {},jumps+':nth-of-type(2)');
        await page.click(jumps+':nth-of-type(1)');
        await page.waitForFunction(selector=>
          document.querySelector(selector)?.getAttribute('aria-pressed')==='true',
          {},jumps+':nth-of-type(1)');
        const reminderToggle=await page.$('[aria-label="Training reminders"]');
        assert.ok(reminderToggle,'Reminder switch exists');
        if(await reminderToggle.evaluate(el=>el.getAttribute('aria-checked'))!=='true')
          await reminderToggle.click();
        await page.waitForSelector('input[aria-label="Reminder time"]');
        const timeField=await page.$eval('input[aria-label="Reminder time"]',el=>({
          shared:el.classList.contains('wpb-ui-control'),height:el.getBoundingClientRect().height,
          font:parseFloat(getComputedStyle(el).fontSize),type:el.type
        }));
        assert.ok(timeField.shared&&timeField.height>=48&&timeField.font>=16&&timeField.type==='time',
          'Reminder time inherits shared paint but retains native picker: '+JSON.stringify(timeField));
        const galleryButton=await page.evaluateHandle(()=>[...document.querySelectorAll('.wpb-settings-navigation')]
          .find(el=>el.textContent.includes('Browse shared programs')));
        assert.ok(galleryButton.asElement(),'Settings gallery navigation is available');
        await galleryButton.asElement().evaluate(el=>el.scrollIntoView({block:'center'}));
        if(width===320||width===834||width===900){
          await page.screenshot({path:path.join(root,`verification/shared-pilot-settings-actions-${theme}-${width}.png`)});
        }
        await galleryButton.asElement().click();
        await galleryButton.dispose();
        await page.waitForSelector('.wpb-gallery-title');
        // Gallery data arrives asynchronously. Assert after its real filter controls mount.
        await page.waitForSelector('.wpb-gallery-chip');
        assert.match(await page.$eval('.wpb-gallery-title',node=>node.textContent),/Shared programs/);
        assert.ok(await page.$eval('.wpb-gallery-back',el=>{
          const r=el.getBoundingClientRect();
          return r.width>=48&&r.height>=48&&el.getAttribute('aria-label')==='Back';
        }),'Gallery uses the shared accessible Back control');
        const filterCount=await page.$$('.wpb-gallery-chip');
        assert.ok(filterCount.length>=3,'Gallery filter choices are actually rendered');
        const picked=await page.$$eval('.wpb-gallery-chip',nodes=>
          nodes.filter(n=>n.getAttribute('aria-pressed')==='true').length);
        assert.ok(picked>=3,'Three Gallery filter dimensions expose selected state');
        // Data-driven Gallery only renders dimensions with at least two distinct values.
        // Exercise the first visible filter row instead of assuming Split is always present.
        const filterRow=await page.$('[role="group"][aria-label$="filter"]');
        assert.ok(filterRow,'Gallery renders a named selectable filter dimension');
        const filterOptions=await filterRow.$$('.wpb-gallery-chip');
        assert.ok(filterOptions.length>=2,'A visible filter has an All and a specific choice');
        // React may replace chip nodes when the result set changes. Assert live,
        // user-visible state rather than a stale ElementHandle from before the click.
        const groupLabel=await filterRow.evaluate(el=>el.getAttribute('aria-label'));
        const selectedText=await filterOptions[1].evaluate(el=>el.textContent.trim());
        const liveChoice=async text=>page.evaluate(({groupLabel,text})=>{
          const group=[...document.querySelectorAll('[role="group"]')]
            .find(el=>el.getAttribute('aria-label')===groupLabel);
          return [...(group?.querySelectorAll('.wpb-gallery-chip')||[])]
            .find(el=>el.textContent.trim()===text)?.getAttribute('aria-pressed')||null;
        },{groupLabel,text});
        await filterOptions[1].click();
        await page.waitForFunction(({groupLabel,selectedText})=>{
          const group=[...document.querySelectorAll('[role="group"]')]
            .find(el=>el.getAttribute('aria-label')===groupLabel);
          return [...(group?.querySelectorAll('.wpb-gallery-chip')||[])]
            .some(el=>el.textContent.trim()===selectedText&&el.getAttribute('aria-pressed')==='true');
        },{},{groupLabel,selectedText});
        assert.equal(await liveChoice(selectedText),'true',
          'Real Gallery announces selected choice in its visible filter row');
        const liveGroup=await page.$('[role="group"][aria-label="'+groupLabel+'"]');
        assert.ok(liveGroup,'Filter group remains available after narrowing the results');
        const liveOptions=await liveGroup.$('.wpb-gallery-chip');
        const allOption=await liveOptions[0].evaluate(el=>el.textContent.trim());
        assert.ok(allOption,'Gallery retains its All/reset choice');
        await liveOptions[0].click();
        await page.waitForFunction(({groupLabel,allOption})=>{
          const group=[...document.querySelectorAll('[role="group"]')]
            .find(el=>el.getAttribute('aria-label')===groupLabel);
          return [...(group?.querySelectorAll('.wpb-gallery-chip')||[])]
            .some(el=>el.textContent.trim()===allOption&&el.getAttribute('aria-pressed')==='true');
        },{},{groupLabel,allOption});
        assert.equal(await liveChoice(allOption),'true',
          'Real Gallery resets selection to All');
        if(width===320||width===834||width===900){
          await page.screenshot({path:path.join(root,`verification/shared-pilot-gallery-${theme}-${width}.png`)});
        }
        const galleryCards=await page.$$eval('.wpb-gallery-program',nodes=>nodes.map(el=>{
          const css=getComputedStyle(el),rect=el.getBoundingClientRect();
          return {shared:el.classList.contains('wpb-ui-card'),width:rect.width,
            background:css.backgroundColor,border:css.borderTopWidth,radius:css.borderTopLeftRadius};
        }));
        assert.ok(galleryCards.length>0&&galleryCards.every(c=>c.shared&&c.width>0&&
          c.background!=='rgba(0, 0, 0, 0)'&&parseFloat(c.border)>0),
          'Real Gallery cards inherit shared opaque card surfaces: '+JSON.stringify(galleryCards));
        const galleryLayout=await layout();
        assert.ok(galleryLayout.scrollWidth<=width+2,
          'Gallery spills horizontally: '+JSON.stringify({theme,width,galleryLayout}));
        // Real full-screen composition, not merely the small isolated pilot fixture.
        if(width===320){
          await page.evaluate(()=>document.documentElement.style.fontSize='32px');
          const enlarged=await layout();
          assert.ok(enlarged.scrollWidth<=width+2,
            'Gallery spills horizontally with 200% text: '+JSON.stringify({theme,width,enlarged}));
          await page.screenshot({path:path.join(root,`verification/shared-pilot-gallery-200pct-${theme}-${width}.png`)});
        }
        assert.deepEqual(errors,[],'No routed Settings/Gallery browser exceptions: '+theme+' '+width);
        console.log('PASS routed shared-control visuals: '+theme+' '+width+'px');
      }finally{await context.close();}
    }
  }
}finally{
  await browser?.close();
  await new Promise(done=>server.close(done));
}
