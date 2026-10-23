import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import puppeteer from 'puppeteer-core';
import { wizardAccessibilityHtml, verifyWizardSteps } from './r74-wizard-fixture.mjs';

// Real dismissible forms and reading surfaces, using the ordinary persisted
// store. Enlarged text is measured independently from whole-page zoom.
const root=path.resolve(new URL('../',import.meta.url).pathname);
const importMap=fs.readFileSync(path.join(root,'index.html'),'utf8')
  .match(/<script type="importmap">[\s\S]*?<\/script>/)[0];
const program={id:'r74-accessibility',name:'Accessibility Fixture',custom:true,weeks:4,
  config:{name:'Accessibility Fixture',goal:'both',experience:'intermediate',
    progression:'manual',split:'custom',weeks:4,days:1,session:'s60',unit:'lb',deload:false},
  days:[{id:'upper',label:'Upper',primaryIndex:0,exercises:['bb-bench','inc-curl']}],
  overrides:{'upper:0':{sets:3,reps:'6-8',rir:'2'},'upper:1':{sets:2,reps:'10-15',rir:'2'}}};
const state=theme=>({v:13,savedAt:1,saved:[program],cycles:[],history:[],perf:{},drafts:{},tombs:{},
  seenIntro:999,seenWhatsNew:999,pinnedId:program.id,theme,unit:'lb',unitChosen:true,
  experience:'intermediate',bodyweight:185,installDismissedAt:Date.now(),lastBackup:Date.now()});
const live={schemaVersion:2,programId:program.id,dayId:'upper',weekIndex:1,
  dayExSig:'bb-bench|inc-curl',exIdx:0,elapsedMs:120000,
  readiness:{label:'Normal',factor:1},restPaused:false,restRemain:0,restMax:0,runPaused:true,
  data:[{id:'bb-bench',slot:0,note:'',sets:Array.from({length:3},()=>({weight:'185',reps:'6',done:false,
    auto:false,valueOwner:'user',target:{w:'185',reps:'6-8',rir:'2'}}))},
    {id:'inc-curl',slot:1,note:'',sets:Array.from({length:2},()=>({weight:'30',reps:'10',done:false,
      auto:false,valueOwner:'user',target:{w:'30',reps:'10-15',rir:'2'}}))}]};
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html',
  '.json':'application/json','.png':'image/png','.svg':'image/svg+xml'};
const server=http.createServer((req,res)=>{
  const name=new URL(req.url,'http://localhost').pathname;
  if(name==='/r74-wizard.html'){
    res.setHeader('Content-Type','text/html');res.end(wizardAccessibilityHtml(importMap));return;
  }
  const file=path.resolve(root,'.'+(name==='/'?'/index.html':name));
  if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){
    res.writeHead(404);res.end();return;
  }
  res.setHeader('Content-Type',mime[path.extname(file)]||'application/octet-stream');
  res.end(fs.readFileSync(file));
});
await new Promise(resolve=>server.listen(8800,'127.0.0.1',resolve));
let browser;
try{
  browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',
    headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
  const results=[];
  for(const theme of ['rose','rosewater']){
    for(const [width,height] of [[320,740],[393,852],[600,800],[900,480],[900,1000],[1280,850],[1600,900]]
      .filter(([w,h])=>!process.argv[2]||process.argv[2]===w+'x'+h)){
      const context=await browser.createBrowserContext(),page=await context.newPage();
      const errors=[];page.on('pageerror',e=>errors.push(e.message));
      const settle=()=>page.evaluate(()=>new Promise(resolve=>
        requestAnimationFrame(()=>requestAnimationFrame(resolve))));
      const textSize=async enlarged=>{
        await page.evaluate(enlarged=>{
          if(enlarged){
            window.__r74Text=[...document.querySelectorAll('.wpb *')]
              .filter(el=>el instanceof HTMLElement&&el.tagName!=='STYLE')
              .map(el=>({el,size:parseFloat(getComputedStyle(el).fontSize),
                value:el.style.getPropertyValue('font-size'),priority:el.style.getPropertyPriority('font-size')}));
            window.__r74Text.forEach(({el,size})=>el.style.setProperty('font-size',size*2+'px','important'));
          }else{
            window.__r74Text.forEach(({el,value,priority})=>value?
              el.style.setProperty('font-size',value,priority):el.style.removeProperty('font-size'));
            delete window.__r74Text;
          }
        },enlarged);
        await settle();
      };
      const surface=selector=>page.$eval(selector,el=>{
        const bounds=el=>{const r=el.getBoundingClientRect();return {x:r.x,y:r.y,
          right:r.right,bottom:r.bottom,width:r.width,height:r.height};};
        const textGeometry=el=>{
          const box=el.getBoundingClientRect(),walker=document.createTreeWalker(el,NodeFilter.SHOW_TEXT),rects=[];
          while(walker.nextNode()){
            const node=walker.currentNode;
            if(!node.textContent.trim()||node.parentElement.closest('svg'))continue;
            const range=document.createRange();range.selectNodeContents(node);
            rects.push(...[...range.getClientRects()].filter(r=>r.width&&r.height));
          }
          return {textFits:rects.every(r=>r.x>=box.x-1&&r.right<=box.right+1&&
            r.y>=box.y-1&&r.bottom<=box.bottom+1),lines:new Set(rects.map(r=>Math.round(r.y))).size};
        };
        return {box:bounds(el),overflow:document.documentElement.scrollWidth>innerWidth+2,
          controls:[...el.querySelectorAll('button,input,select,textarea')]
            .filter(el=>el.getClientRects().length&&getComputedStyle(el).visibility!=='hidden')
            .map(el=>{const box=bounds(el);return {
              label:el.getAttribute('aria-label')||el.textContent.trim(),classes:el.className,...box,
              inViewport:box.bottom>0&&box.y<innerHeight&&box.right>0&&box.x<innerWidth,
              ...textGeometry(el)};})};
      });
      try{
        await page.setViewport({width,height,deviceScaleFactor:1,isMobile:width<600,hasTouch:width<600});
        await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
        await page.evaluateOnNewDocument(store=>localStorage.setItem('wpb:v1',JSON.stringify(store)),state(theme));
        await page.goto('http://127.0.0.1:8800/',{waitUntil:'networkidle0'});
        await page.click('[data-tab="settings"]');
        await page.waitForSelector('.wpb-settings');
        // Read the browser's actual accessibility names, including filled
        // fields: visual row captions and numeric placeholders are not names.
        const fieldName=async selector=>{
          const client=await page.createCDPSession();
          try{
            const {root}=await client.send('DOM.getDocument');
            const {nodeId}=await client.send('DOM.querySelector',{nodeId:root.nodeId,selector});
            assert.ok(nodeId,'Settings field exists: '+selector);
            const {nodes}=await client.send('Accessibility.getPartialAXTree',{nodeId,fetchRelatives:false});
            return nodes.find(node=>!node.ignored)?.name?.value;
          }finally{await client.detach();}
        };
        for(const label of ['Bodyweight (lb)','Estimated 1RM weight (lb)','Estimated 1RM reps'])
          assert.equal(await fieldName('.wpb-settings input[aria-label="'+label+'"]'),label,
            'Settings exposes the actual field purpose to assistive technology');
        const action=async (scope,label)=>{
          const handle=await page.evaluateHandle(({scope,label})=>
            [...document.querySelectorAll(scope+' button')].find(el=>el.textContent.trim().startsWith(label)),
            {scope,label});
          const control=handle.asElement();
          assert.ok(control,'Settings action exists: '+label);
          await control.evaluate(el=>el.scrollIntoView({block:'center'}));
          await control.click();await handle.dispose();await settle();
        };
        await action('.wpb-settings','Save backup');
        const exportField='.wpb-settings textarea[aria-label="Backup or CSV text to copy"]';
        await page.waitForSelector(exportField);
        assert.equal(await fieldName(exportField),'Backup or CSV text to copy',
          'Filled export text has a stable accessible name');
        assert.equal(await page.$eval(exportField,el=>el.readOnly),true,
          'Naming export text keeps the existing read-only backup');
        await action('.wpb-settings','Done');
        await action('.wpb-settings','Restore backup');
        const restoreField='.wpb-settings textarea[aria-label="Backup text to restore"]';
        await page.waitForSelector(restoreField);
        assert.equal(await fieldName(restoreField),'Backup text to restore');
        const restoreDraft='R74 unsaved backup draft';
        await page.type(restoreField,restoreDraft);
        await textSize(true);
        assert.equal(await page.$eval(restoreField,el=>el.value),restoreDraft,
          'Text enlargement preserves the restore draft');
        await textSize(false);
        await action('.wpb-settings','Cancel');
        await action('.wpb-settings','Add a shared program');
        const programField='textarea[aria-label="Program code or text"]';
        await page.waitForSelector(programField);
        assert.equal(await fieldName(programField),'Program code or text');
        await page.type(programField,'R74 unsaved program draft');
        await page.keyboard.press('Escape');
        await page.waitForSelector(programField,{hidden:true});
        assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')).saved),[program],
          'Inspecting import fields never restores or creates a program');
        await page.evaluate(()=>{
          const opener=[...document.querySelectorAll('.wpb-settings button')]
            .find(el=>el.textContent.includes('Your gyms'));
          if(!opener)throw new Error('Your gyms action missing');
          opener.dataset.r74Opener='gyms';opener.scrollIntoView({block:'center'});opener.focus();
        });
        await page.keyboard.press('Enter');
        await page.waitForSelector('.wpb-gym-sheet');
        await page.waitForFunction(()=>document.querySelector('.wpb-gym-sheet')?.contains(document.activeElement));
        assert.equal(await page.$eval('.wpb-gym-sheet',el=>{
          const id=el.parentElement.getAttribute('aria-labelledby');
          return document.getElementById(id)?.textContent.trim();
        }),'Your gyms','Gym dialog has a real accessible title');
        assert.equal(await page.evaluate(()=>document.activeElement.matches('input,textarea')),false,
          'Opening a form does not summon the phone keyboard');
        const stops=await page.evaluate(()=>{
          const sheet=document.querySelector('.wpb-gym-sheet');
          const controls=[...sheet.querySelectorAll('button,input,select,textarea,[tabindex]')]
            .filter(el=>el.tabIndex>=0&&!el.disabled&&el.getClientRects().length &&
              getComputedStyle(el).visibility!=='hidden');
          controls.forEach((el,i)=>el.dataset.r74Stop=String(i));return controls.length;
        });
        assert.ok(stops>=2,'Gym form exposes real controls');
        await page.focus('[data-r74-stop="'+(stops-1)+'"]');
        await page.keyboard.press('Tab');
        assert.equal(await page.evaluate(()=>document.activeElement.dataset.r74Stop),'0',
          'Tab wraps within the top form');
        await page.keyboard.down('Shift');
        await page.keyboard.press('Tab');
        await page.keyboard.up('Shift');
        assert.equal(await page.evaluate(()=>document.activeElement.dataset.r74Stop),String(stops-1),
          'Shift+Tab wraps backwards within the top form');
        await page.evaluate(()=>document.activeElement.dispatchEvent(new KeyboardEvent('keydown',
          {key:'Escape',isComposing:true,bubbles:true,cancelable:true})));
        assert.ok(await page.$('.wpb-gym-sheet'),'IME composition owns Escape');
        if(width<600){
          // Model Android's layout viewport staying full height while the
          // keyboard reduces the visual viewport. This is browser evidence,
          // not a physical IME/TalkBack certification.
          const visibleHeight=Math.round(height*.55);
          await page.click('.wpb-gym-card>div:first-child>button:nth-child(2)');
          await page.waitForSelector('.wpb-gym-sheet input');
          const keyboardInput='.wpb-gym-sheet input[aria-label="Smith empty bar weight"]';
          await page.focus(keyboardInput);
          const inputValue=await page.$eval(keyboardInput,el=>el.value);
          await page.evaluate(h=>{
            Object.defineProperty(visualViewport,'height',{configurable:true,get:()=>h});
            visualViewport.dispatchEvent(new Event('resize'));
          },visibleHeight);
          await page.waitForFunction(h=>parseFloat(document.documentElement.style.getPropertyValue('--visual-h'))===h,{},visibleHeight);
          await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
          const keyboardSheet=await page.$eval('.wpb-gym-sheet',el=>{
            const r=el.getBoundingClientRect();return {top:r.top,bottom:r.bottom,height:r.height,
              scrollable:el.scrollHeight>el.clientHeight,overflow:getComputedStyle(el).overflowY,
              visual:getComputedStyle(el).getPropertyValue('--visual-h'),max:getComputedStyle(el).maxHeight,
              parentMax:getComputedStyle(el.parentElement).maxHeight,parentHeight:el.parentElement.getBoundingClientRect().height,
              transitions:getComputedStyle(el).transition,parentTransitions:getComputedStyle(el.parentElement).transition};
          });
          assert.ok(keyboardSheet.top>=0&&keyboardSheet.bottom<=visibleHeight+1,
            'Gym form stays above the emulated phone keyboard: '+JSON.stringify({visibleHeight,keyboardSheet}));
          assert.equal(await page.$eval(keyboardInput,el=>el.value),inputValue,
            'Keyboard reflow preserves the live form value');
          const field=await page.$eval(keyboardInput,el=>{
            const r=el.getBoundingClientRect();return {top:r.top,bottom:r.bottom,
              hit:el.contains(document.elementFromPoint(r.x+r.width/2,r.y+r.height/2))};
          });
          assert.ok(field.top>=keyboardSheet.top&&field.bottom<=visibleHeight+1&&field.hit,
            'Focused Smith weight stays visible above the keyboard: '+JSON.stringify({visibleHeight,field}));
          await page.evaluate(()=>{delete visualViewport.height;visualViewport.dispatchEvent(new Event('resize'));});
        }
        await page.keyboard.press('Escape');
        await page.waitForSelector('.wpb-gym-sheet',{hidden:true});
        assert.equal(await page.evaluate(()=>document.activeElement.dataset.r74Opener),'gyms',
          'Escape restores the actual form opener');
        assert.equal(await page.$$eval('[data-wpb-lock]',els=>els.length),0,'Dismissal releases background scrolling');
        if(width===393||width===1280){
          await page.evaluate(session=>localStorage.setItem('wpb:live',JSON.stringify(session)),live);
          await page.reload({waitUntil:'networkidle0'});
          await page.waitForSelector('.wpb-live-dock button[aria-label="Resume workout"]');
          await page.click('.wpb-live-dock button[aria-label="Resume workout"]');
          await page.waitForSelector('[data-testid="set-0-0"] input[aria-label="weight"]');
          const values=await page.$$eval('[data-testid="set-0-0"] input',els=>els.map(el=>el.value));
          await page.click('button[aria-label^="Exercise info for "]');
          await page.waitForSelector('.wpb-backdrop [data-sheet-drag]');
          await page.evaluate(()=>{
            const opener=[...document.querySelectorAll('.wpb-workout button')]
              .find(el=>el.textContent.includes('Set a goal weight'));
            if(!opener)throw new Error('Workout goal opener missing');
            opener.dataset.r74Opener='goal';opener.scrollIntoView({block:'center'});opener.focus();
          });
          await page.keyboard.press('Enter');
          await page.waitForSelector('input[aria-label="Goal weight"]');
          await page.waitForFunction(()=>{
            const dialog=document.querySelector('input[aria-label="Goal weight"]')?.closest('.wpb-backdrop');
            return dialog?.hasAttribute('aria-labelledby');
          });
          assert.equal(await page.$eval('input[aria-label="Goal weight"]',el=>{
            const id=el.closest('.wpb-backdrop').getAttribute('aria-labelledby');
            return document.getElementById(id)?.textContent.trim();
          }),'Goal weight','Nested goal form has a real accessible title');
          await page.keyboard.press('Escape');
          await page.waitForSelector('input[aria-label="Goal weight"]',{hidden:true});
          assert.equal(await page.$$eval('.wpb-backdrop:not(.wpb-closing .wpb-backdrop)',els=>els.length),1,
            'Escape dismisses only the nested form');
          assert.equal(await page.evaluate(()=>document.activeElement.dataset.r74Opener),'goal',
            'An autofocus dialog restores the actual workout action');
          assert.deepEqual(await page.$$eval('[data-testid="set-0-0"] input',els=>els.map(el=>el.value)),values,
            'Cancelling a goal dialog preserves typed sets');
          await page.keyboard.press('Escape');
          await page.waitForSelector('.wpb-backdrop',{hidden:true});
          await page.evaluate(()=>history.back());
          await page.waitForSelector('.wpb-tabbar');
          // End the isolated Goal fixture. Home intentionally offers Resume
          // during a live workout; creation is checked with the ordinary store.
          await page.evaluate(()=>localStorage.removeItem('wpb:live'));
          await page.reload({waitUntil:'networkidle0'});
          await page.waitForSelector('.wpb-tabbar');
        }
        // Read actual text at twice its rendered size, including px-based
        // legacy labels. This is text enlargement, not a pinch-zoom claim.
        for(const route of ['home','plan','progress','profile','settings']){
          await page.focus('[data-tab="'+route+'"]');
          await page.keyboard.press('Enter');
          await page.waitForSelector('[data-view-frame="'+route+'"]');
          await page.evaluate(()=>{
            window.__r74Text=[...document.querySelectorAll('.wpb *')]
              .filter(el=>el instanceof HTMLElement && el.tagName!=='STYLE')
              .map(el=>({el,size:parseFloat(getComputedStyle(el).fontSize),
                value:el.style.getPropertyValue('font-size'),priority:el.style.getPropertyPriority('font-size')}));
            window.__r74Text.forEach(({el,size})=>el.style.setProperty('font-size',size*2+'px','important'));
          });
          await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
          const enlarged=await page.evaluate(()=>{
            const tabs=[...document.querySelectorAll('.wpb-tabbar button')].map(button=>{
              const label=button.querySelector('span'),range=document.createRange();
              range.selectNodeContents(label);const text=range.getBoundingClientRect(),box=button.getBoundingClientRect();
              const rects=[...range.getClientRects()].filter(r=>r.width>0&&r.height>0);
              const visibleName=[...button.querySelectorAll('.wpb-tab-label-full,.wpb-tab-label-compact')]
                .filter(el=>getComputedStyle(el).display!=='none').map(el=>el.textContent).join('');
              return {label:button.getAttribute('aria-label'),visibleName,
                lines:new Set(rects.map(r=>Math.round(r.y))).size,
                text:{x:text.x,right:text.right,y:text.y,bottom:text.bottom},
                box:{x:box.x,right:box.right,y:box.y,bottom:box.bottom},
                hit:button.contains(document.elementFromPoint(box.x+box.width/2,box.y+box.height/2))};
            });
            const next=document.querySelector('.wpb-plan-up-next'),overview=document.querySelector('.wpb-plan-overview');
            const plan=next&&overview?{
              nextBottom:next.getBoundingClientRect().bottom,overviewTop:overview.getBoundingClientRect().top,
              nextHeight:next.getBoundingClientRect().height,nextCss:getComputedStyle(next).height,
              min:getComputedStyle(next).minHeight,max:getComputedStyle(next).maxHeight,
              transform:getComputedStyle(next).transform,rows:getComputedStyle(next.parentElement).gridTemplateRows,
              overviewRow:getComputedStyle(overview).gridRow}:null;
            const bar=document.querySelector('.wpb-tabbar');
            const tabIndicator=bar?.hasAttribute('data-pi-m3-track')?{
              outline:getComputedStyle(bar,'::before').outlineStyle,
              border:getComputedStyle(bar,'::before').borderTopStyle,
              selected:bar.querySelectorAll('.wpb-tab[aria-current="page"]').length
            }:null;
            const orbit=document.querySelector('.wpb-home .wpb-m3-day-orbit');
            const planHeading=document.querySelector('.wpb-plan-view .wpb-plan-title');
            const planModes=[...document.querySelectorAll('.wpb-plan-panes>.wpb-plan-pane-tab')].map(button=>{
              const label=button.querySelector('.wpb-plan-pane-label');
              if(!label)return null;
              const range=document.createRange();range.selectNodeContents(label);
              const rects=[...range.getClientRects()].filter(r=>r.width>0&&r.height>0);
              const box=button.getBoundingClientRect();
              return {name:label.textContent.trim(),lines:new Set(rects.map(r=>Math.round(r.y))).size,
                fits:rects.length>0&&rects.every(r=>r.x>=box.x-1&&r.right<=box.right+1),
                truncated:getComputedStyle(label).textOverflow==='ellipsis'&&label.scrollWidth>label.clientWidth+1};
            }).filter(Boolean);

            const title=document.querySelector('.wpb-root-header .wpb-screen-title');
            const edit=document.querySelector('.wpb-root-header .wpb-root-actions button');
            const titleRange=document.createRange();
            if(title)titleRange.selectNodeContents(title);
            const titleRects=title?[...titleRange.getClientRects()].filter(r=>r.width>0&&r.height>0):[];
            const titleBox=title?.getBoundingClientRect(),editBox=edit?.getBoundingClientRect();
            const rootTitle=title?{
              text:title.textContent.trim(),
              lines:new Set(titleRects.map(r=>Math.round(r.y))).size,
              overlapsAction:!!editBox&&titleBox.left<editBox.right-1&&titleBox.right>editBox.left+1&&
                titleBox.top<editBox.bottom-1&&titleBox.bottom>editBox.top+1
            }:null;
            return {overflow:document.documentElement.scrollWidth>innerWidth+2,tabs,plan,rootTitle,tabIndicator,
              homeOrbitVisible:!!orbit&&getComputedStyle(orbit).display!=='none',
              planReading:planHeading?{titleFits:planHeading.scrollWidth<=planHeading.clientWidth+2,modes:planModes}:null};
          });
          if(width===320||width===1280)
            await page.screenshot({path:path.join(root,'verification/r74-'+theme+'-'+width+'-'+route+'-phone.png')});
          assert.equal(enlarged.overflow,false,'200% '+route+' text keeps document width: '+JSON.stringify({theme,width,enlarged}));
          assert.ok(enlarged.tabs.every(t=>t.hit&&t.visibleName===t.label&&t.lines===1&&t.text.x>=t.box.x-1&&t.text.right<=t.box.right+1&&
            t.text.y>=t.box.y-1&&t.text.bottom<=t.box.bottom+1),
            '200% '+route+' navigation labels fit their real controls: '+JSON.stringify({theme,width,enlarged}));
          if(enlarged.tabIndicator)
            assert.ok(enlarged.tabIndicator.outline==='none'&&enlarged.tabIndicator.border==='none'&&
              enlarged.tabIndicator.selected===1,
              'Selected navigation uses one filled indicator without a permanent gray rim: '+JSON.stringify({theme,width,route,indicator:enlarged.tabIndicator}));
          if(route==='profile'&&width===320)
            assert.ok(enlarged.rootTitle?.text==='Profile'&&enlarged.rootTitle.lines===1&&
              !enlarged.rootTitle.overlapsAction,
              'Profile root title stays one intact word and Edit remains separate at 200%: '+JSON.stringify({theme,width,title:enlarged.rootTitle}));
          if(route==='home'&&width===320)
            assert.equal(enlarged.homeOrbitVisible,false,
              '320px Home day orbit must never overlap the workout heading: '+JSON.stringify({theme,width,enlarged}));
          if(route==='plan'&&width===320){
            assert.ok(enlarged.planReading?.titleFits,
              '200% Plan title wraps within the page instead of clipping: '+JSON.stringify({theme,width,enlarged}));
            assert.ok(enlarged.planReading.modes.length===4&&enlarged.planReading.modes.every(
              mode=>mode.name&&mode.fits&&!mode.truncated),
              '200% Plan pane labels show complete text in their buttons: '+JSON.stringify({theme,width,enlarged}));
          }
          if(route==='plan'&&width>=1060&&height>=700){
            assert.ok(enlarged.plan.nextBottom<=enlarged.plan.overviewTop+1,
              'Enlarged Plan supporting cards stay in separate rows: '+JSON.stringify(enlarged.plan));
          }
          if(route==='settings'){
            const experience=await surface('.wpb-experience-segmented');
            assert.ok(experience.controls.length===4&&experience.controls.every(c=>
              c.width>=44&&c.height>=44&&c.textFits&&c.lines===1),
              '200% Settings choices keep complete labels: '+JSON.stringify({theme,width,experience}));
            await page.evaluate(()=>{
              const reset=[...document.querySelectorAll('.wpb-settings button')]
                .find(el=>el.textContent.trim()==='Reset everything');
              reset.dataset.r74Reset='1';
            });
            const resetFits=await page.$eval('[data-r74-reset="1"]',el=>{
              const box=el.getBoundingClientRect(),range=document.createRange();range.selectNodeContents(el);
              return [...range.getClientRects()].every(r=>r.x>=box.x-1&&r.right<=box.right+1&&
                r.y>=box.y-1&&r.bottom<=box.bottom+1);
            });
            assert.ok(resetFits,'200% Settings reset label stays in its own button without activating reset');
          }
          await page.evaluate(()=>{
            window.__r74Text.forEach(({el,value,priority})=>value?
              el.style.setProperty('font-size',value,priority):el.style.removeProperty('font-size'));
            delete window.__r74Text;
          });
          await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
        }
        // R74/B857: enlarge the actual Library list, selected detail and live
        // creation form. Search, exercise identity and typed draft survive.
        await page.focus('[data-tab="home"]');
        await page.keyboard.press('Enter');
        await page.waitForSelector('button[aria-label="Exercise library"]');
        await page.click('button[aria-label="Exercise library"]');
        await page.waitForSelector('.wpb-library-search input');
        await page.type('.wpb-library-search input','bench');
        await page.waitForFunction(()=>document.querySelectorAll('.wpb-library-row').length>1);
        const libraryTargets=await surface('.wpb-library');
        assert.ok(libraryTargets.controls.every(c=>c.label&&c.width>=44&&c.height>=44),
          'Library exposes named 44px controls at ordinary text: '+JSON.stringify({theme,width,
            failures:libraryTargets.controls.filter(c=>!c.label||c.width<44||c.height<44)}));
        await textSize(true);
        const enlargedLibrary=await surface('.wpb-library');
        assert.equal(enlargedLibrary.overflow,false,'200% Library text reflows: '+
          JSON.stringify({theme,width,enlargedLibrary}));
        assert.equal(await page.$eval('.wpb-library-search input',el=>el.value),'bench',
          'Enlarged Library preserves the search');
        assert.ok(enlargedLibrary.controls.every(c=>!c.inViewport||c.textFits),
          'Enlarged Library labels stay inside their own controls: '+JSON.stringify({theme,width,
            failures:enlargedLibrary.controls.filter(c=>c.inViewport&&!c.textFits)}));
        // Rows use content-visibility:auto. Native scrolling must render each
        // row before checking its text against the real box, not its deferred
        // offscreen placeholder. Inspect every filtered result in turn.
        for(const row of await page.$$('.wpb-library-row')){
          await row.evaluate(el=>el.scrollIntoView({block:'center'}));
          await settle();
          const rendered=await surface('.wpb-library');
          assert.ok(rendered.controls.every(c=>!c.inViewport||c.textFits),
            'Rendered enlarged Library labels fit after scrolling: '+JSON.stringify({theme,width,
              failures:rendered.controls.filter(c=>c.inViewport&&!c.textFits)}));
        }
        await page.$eval('.wpb-library',el=>el.scrollTop=0);
        await settle();
        if(width===320||width===1280)
          await page.screenshot({path:path.join(root,'verification/r74-'+theme+'-'+width+'-library-phone.png')});
        await textSize(false);
        await page.evaluate(()=>{
          const row=document.querySelectorAll('.wpb-library-row')[1];
          row.dataset.r74LibraryOrigin='1';row.scrollIntoView({block:'center'});row.focus();
        });
        const origin=await page.$eval('[data-r74-library-origin]',el=>el.textContent.trim());
        await page.keyboard.press('Enter');
        await page.waitForSelector('.wpb-exercise-detail');
        if(width<1120||height<720){
          await page.waitForFunction(()=>document.querySelector('.wpb-exercise-detail')
            ?.contains(document.activeElement));
          assert.equal(await page.$eval('.wpb-exercise-detail',el=>el.getAttribute('aria-modal')),'true',
            'Full-screen Library detail keeps the existing modal focus owner');
          await page.keyboard.press('Tab');
          assert.equal(await page.evaluate(()=>document.activeElement?.getAttribute('aria-label')),'Back to library',
            'Back is the first keyboard action inside full-screen detail');
        }else{
          await page.waitForFunction(expected=>document.activeElement?.matches('.wpb-library-row')&&
            document.activeElement.textContent.trim()===expected,{timeout:5000},origin);
          assert.equal(await page.evaluate(()=>document.activeElement?.textContent.trim()),origin,
            'Wide list/detail selection retains keyboard focus in the visible list');
          assert.equal(await page.$eval('.wpb-exercise-detail',el=>el.hasAttribute('aria-modal')),false,
            'The supporting Library pane is nonmodal');
          await page.focus('.wpb-library-search input');
          assert.equal(await page.evaluate(()=>document.activeElement?.getAttribute('aria-label')),'Search exercises',
            'Wide detail does not trap focus away from its visible search/list');
        }
        await page.evaluate(()=>document.activeElement.dispatchEvent(new KeyboardEvent('keydown',
          {key:'Escape',isComposing:true,bubbles:true,cancelable:true})));
        assert.ok(await page.$('.wpb-exercise-detail'),'Library detail preserves IME Escape ownership');
        await textSize(true);
        const enlargedDetail=await surface('.wpb-exercise-detail');
        assert.equal(enlargedDetail.overflow,false,'200% detail text reflows: '+
          JSON.stringify({theme,width,enlargedDetail}));
        const detailTabs=await surface('.wpb-exercise-tabs');
        assert.ok(detailTabs.controls.length===4&&detailTabs.controls.every(c=>
          c.label&&c.width>=44&&c.height>=44&&c.textFits&&c.lines===1),
          '200% detail tabs keep whole, non-overlapping labels: '+JSON.stringify({theme,width,detailTabs}));
        if(width===320||width===1280)
          await page.screenshot({path:path.join(root,'verification/r74-'+theme+'-'+width+'-detail-phone.png')});
        await textSize(false);
        if(width>=900&&height>=700){
          await page.setViewport({width:Math.round(width/2),height:Math.round(height/2),
            deviceScaleFactor:2,isMobile:false,hasTouch:false});
          await settle();
          assert.equal((await surface('.wpb-exercise-detail')).overflow,false,
            'Library detail reflows at a 200% zoom-equivalent layout viewport');
          await page.waitForFunction(()=>document.querySelector('.wpb-exercise-detail')?.getAttribute('aria-modal')==='true');
          assert.equal(await page.$eval('.wpb-library-search input',el=>el.value),'bench',
            'Zoom-equivalent Library resize preserves the search');
          await page.setViewport({width,height,deviceScaleFactor:1,isMobile:width<600,hasTouch:width<600});
          await settle();
          if(width>=1120&&height>=720){
            await page.waitForFunction(()=>!document.querySelector('.wpb-exercise-detail')?.hasAttribute('aria-modal'));
            assert.equal(await page.$$eval('[data-wpb-lock]',els=>els.length),0,
              'Re-expanding Library releases the modal scroll lock');
          }
        }
        await page.focus('.wpb-exercise-detail button[aria-label="Back to library"]');
        await page.keyboard.press('Escape');
        await page.waitForFunction(expected=>!document.querySelector('.wpb-exercise-detail')&&
          document.activeElement?.matches('.wpb-library-row')&&document.activeElement.textContent.trim()===expected,
          {timeout:5000},origin);
        assert.equal(await page.$eval('.wpb-library-search input',el=>el.value),'bench',
          'Escape preserves the filtered Library list');
        await page.click('.wpb-library-header button[aria-label="Back"]');
        await page.waitForSelector('.wpb-home-new-program');
        await page.$eval('.wpb-home-new-program',el=>el.scrollIntoView({block:'center'}));
        await page.click('.wpb-home-new-program');
        await page.waitForSelector('.wpb-wizard-step button');
        await page.evaluate(()=>{
          const mode=[...document.querySelectorAll('.wpb-wizard-step button')]
            .find(el=>el.textContent.includes('Single program'));
          if(!mode)throw new Error('Single program mode missing');mode.click();
        });
        await page.click('.wpb-wizard-next-action');
        await page.waitForSelector('.wpb-wizard-step input');
        await page.type('.wpb-wizard-step input','R74 enlarged '+theme);
        const draft=await page.$eval('.wpb-wizard-step input',el=>el.value);
        await textSize(true);
        const enlargedWizard=await surface('.wpb-wizard');
        assert.equal(enlargedWizard.overflow,false,'200% Wizard text reflows: '+
          JSON.stringify({theme,width,enlargedWizard}));
        assert.equal(await page.$eval('.wpb-wizard-step input',el=>el.value),draft,
          'Enlarged creation form preserves the typed draft');
        assert.ok(enlargedWizard.controls.every(c=>c.label&&c.width>=44&&c.height>=44&&c.textFits),
          'Enlarged Wizard exposes named usable controls: '+JSON.stringify({theme,width,enlargedWizard}));
        const wizardTitle=await page.$eval('.wpb-wizard-header>div:first-child>div:not(.mono)',el=>{
          const box=el.getBoundingClientRect(),node=el.firstChild;
          return [...node.textContent.matchAll(/\S+/g)].map(match=>{
            const range=document.createRange();range.setStart(node,match.index);
            range.setEnd(node,match.index+match[0].length);
            const rects=[...range.getClientRects()];
            return {word:match[0],lines:rects.length,
              fits:rects.every(r=>r.x>=box.x-1&&r.right<=box.right+1)};
          });
        });
        assert.ok(wizardTitle.every(word=>word.lines===1&&word.fits),
          '200% Wizard title wraps between whole words: '+JSON.stringify({theme,width,wizardTitle}));
        if(width===320||width===1280)
          await page.screenshot({path:path.join(root,'verification/r74-'+theme+'-'+width+'-wizard-phone.png')});
        await textSize(false);
        if(width>=900&&height>=700){
          await page.setViewport({width:Math.round(width/2),height:Math.round(height/2),
            deviceScaleFactor:2,isMobile:false,hasTouch:false});
          await settle();
          const zoomWizard=await surface('.wpb-wizard');
          assert.equal(zoomWizard.overflow,false,'Wizard reflows at a 200% zoom-equivalent viewport');
          assert.equal(await page.$eval('.wpb-wizard-step input',el=>el.value),draft,
            'Zoom-equivalent Wizard resize preserves the current draft');
          assert.ok(zoomWizard.controls.every(c=>c.label&&c.width>=44&&c.height>=44&&c.textFits),
            'Zoom-equivalent Wizard retains usable named controls: '+JSON.stringify(zoomWizard));
          await page.setViewport({width,height,deviceScaleFactor:1,isMobile:width<600,hasTouch:width<600});
          await settle();
        }
        const wizardSteps=await verifyWizardSteps(page,{theme,width,height,root,
          url:'http://127.0.0.1:8800/r74-wizard.html'});
        results.push({theme,width,height,stops,wizardSteps});
        assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')).saved),[program],
          'Accessibility interactions preserve saved prescriptions');
        assert.deepEqual(errors,[],'No runtime errors');
        console.log('PASS R74 '+theme+' '+width+'x'+height);
      }catch(error){
        console.error('FAIL R74 '+theme+' '+width+'x'+height);
        await page.screenshot({path:path.join(root,'verification/r74-failure-phone.png')});
        throw error;
      }finally{await context.close();}
    }
  }
  assert.equal(results.length,process.argv[2]?2:14,'Every requested R74 scenario ran');
  console.log('PASS R74 form focus and dismissal: '+JSON.stringify(results));
}finally{
  if(browser)await browser.close();
  await new Promise(resolve=>server.close(resolve));
}
