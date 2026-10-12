import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import puppeteer from 'puppeteer-core';

// R65: exercise the real shell and route owners at compact, medium and expanded
// windows. No simulated DOM rail or forked state; each test seeds ordinary wpb:v1.
const root=path.resolve(new URL('../',import.meta.url).pathname);
const program={
  id:'r65-adaptive',name:'Adaptive Layout Fixture',custom:true,weeks:4,
  config:{name:'Adaptive Layout Fixture',goal:'both',experience:'intermediate',
    progression:'manual',split:'custom',weeks:4,days:1,session:'s60',unit:'lb',deload:false},
  days:[{id:'upper',label:'Upper',primaryIndex:0,exercises:['bb-bench','inc-curl']}],
  overrides:{'upper:0':{sets:4,reps:'6-8',rir:'2'},'upper:1':{sets:3,reps:'10-15',rir:'2'}}
};
const store=theme=>({
  v:13,savedAt:1,saved:[program],cycles:[],history:[{
    id:'r70-progress-fixture',programId:program.id,programName:program.name,
    dayId:'upper',dayLabel:'Upper',weekIndex:1,date:Date.now()-86400000,
    unit:'lb',duration:2400,totalSets:2,tonnage:1320,
    perf:{'bb-bench':{weight:185,reps:6,
      sets:[{w:185,r:6,done:true,rir:2,rirReported:true}]},
      'inc-curl':{weight:30,reps:12,
      sets:[{w:30,r:12,done:true,rir:2,rirReported:true}]}}
  }],perf:{},drafts:{},tombs:{},
  seenIntro:999,seenWhatsNew:999,pinnedId:program.id,theme,unit:'lb',
  unitChosen:true,experience:'intermediate',bodyweight:185,
  installDismissedAt:Date.now(),lastBackup:Date.now()
});
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html',
  '.json':'application/json','.png':'image/png','.svg':'image/svg+xml',
  '.webmanifest':'application/manifest+json'};
const server=http.createServer((req,res)=>{
  const name=new URL(req.url,'http://localhost').pathname;
  const file=path.resolve(root,'.'+(name==='/'?'/index.html':name));
  if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){
    res.writeHead(404);res.end();return;
  }
  res.setHeader('Content-Type',mime[path.extname(file)]||'application/octet-stream');
  res.end(fs.readFileSync(file));
});
await new Promise(resolve=>server.listen(8798,'127.0.0.1',resolve));
let browser;
try{
  browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',
    headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
  const results=[];
  for(const theme of ['rose','rosewater']){
    for(const [width,height,wide] of [[393,852,false],[900,480,false],[900,1000,true],[1280,850,true]]){
      const context=await browser.createBrowserContext();
      const page=await context.newPage();
      const errors=[];page.on('pageerror',e=>errors.push(e.message));
      try{
        await page.setViewport({width,height,deviceScaleFactor:1,isMobile:!wide,hasTouch:!wide});
        await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
        await page.evaluateOnNewDocument(state=>localStorage.setItem('wpb:v1',JSON.stringify(state)),store(theme));
        await page.goto('http://127.0.0.1:8798/',{waitUntil:'networkidle0'});
        await page.waitForSelector('.wpb-tabbar');
        const geometry=()=>page.evaluate(()=>{
          const root=document.querySelector('.wpb');
          const frame=[...root.children].find(node=>node.querySelector(':scope > .wpb-tabbar'));
          const bar=document.querySelector('.wpb-tabbar');
          const bounds=el=>{const r=el.getBoundingClientRect();
            return {x:r.x,y:r.y,width:r.width,height:r.height,right:r.right,bottom:r.bottom};};
          const first=frame?.firstElementChild;
          const tabs=[...bar.querySelectorAll('[data-tab]')];
          return {frame:bounds(frame),bar:bounds(bar),content:bounds(first),
            direction:getComputedStyle(bar).flexDirection,
            tabs:tabs.map(el=>({...bounds(el),label:el.getAttribute('aria-label'),
              hit:el.contains(document.elementFromPoint(
                el.getBoundingClientRect().x+el.getBoundingClientRect().width/2,
                el.getBoundingClientRect().y+el.getBoundingClientRect().height/2))})),
            overflow:document.documentElement.scrollWidth>innerWidth+2};
        });
        const initial=await geometry();
        assert.equal(initial.tabs.length,5,'All five actual navigation controls exist');
        assert.ok(initial.tabs.every(tab=>tab.hit),'Nav targets remain clickable: '+JSON.stringify(initial));
        assert.equal(initial.overflow,false,'No horizontal viewport overflow: '+JSON.stringify(initial));
        if(wide){
          assert.equal(initial.direction,'column','Expanded screen must use an actual navigation rail');
          assert.ok(initial.frame.width>780,'Expanded content shell is no longer locked to 460px');
          assert.ok(initial.bar.width>=95&&initial.bar.height>initial.bar.width*2,
            'Rail is a vertical, independently sized surface');
          assert.ok(initial.bar.right<=initial.content.x+3,
            'Rail occupies its own column without covering content');
        }else{
          assert.equal(initial.direction,'row','Phones/short landscape retain bottom navigation');
          assert.ok(initial.frame.width<=462,'Phone shell remains its existing maximum');
          assert.ok(initial.bar.y>initial.content.y+initial.content.height-10,
            'Phone navigation stays below content');
        }
        for(const route of ['plan','progress','profile','settings','home']){
          await page.click('[data-tab="'+route+'"]');
          await page.waitForSelector('[data-view-frame="'+route+'"]');
          // R73 semantic tabs: the same motion contract applies on Plan and
          // Progress in both palettes and across all existing viewports.
          if(route==='plan'||route==='progress'){
            const selector=route==='plan'?'.wpb-plan-panes>button':'.wpb-premium-tabs>button';
            await page.waitForSelector(selector);
            const reduced=await page.evaluate(selector=>{
              const nodes=[...document.querySelectorAll(selector)];
              return {
                count:nodes.length,
                transitions:nodes.map(n=>getComputedStyle(n).transitionDuration),
                overflow:document.documentElement.scrollWidth>innerWidth+2
              };
            },selector);
            assert.ok(reduced.count>=2,'Real '+route+' choices are present');
            assert.ok(reduced.transitions.every(v=>v.split(',').every(d=>parseFloat(d)===0)),
              'Reduced motion disables '+route+' selection transitions');
            assert.equal(reduced.overflow,false,route+' controls do not overflow');
            await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'no-preference'}]);
            const regular=await page.$eval(selector,button=>{
              const s=getComputedStyle(button);
              return {properties:s.transitionProperty.split(',').map(v=>v.trim()),
                durations:s.transitionDuration.split(',').map(v=>parseFloat(v))};
            });
            assert.ok(regular.properties.includes('color') &&
              regular.durations.some(v=>v>0&&v<=.3),
              route+' uses the shared, non-geometric color transition');
            assert.ok(regular.properties.every(v=>v==='color'),
              route+' must not animate width, position or text input geometry');
            await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
          }
          if(route==='plan'){
            await page.waitForSelector('.wpb-plan-view .wpb-plan-panes');
            await page.waitForSelector('.wpb-plan-view .wpb-plan-overview');
            const layout=await page.evaluate(()=>{
              const parent=document.querySelector('.wpb-plan-view');
              const rect=el=>{if(!el)return null;const r=el.getBoundingClientRect();
                return {x:r.x,y:r.y,right:r.right,bottom:r.bottom,width:r.width};};
              return {grid:getComputedStyle(parent).display==='grid',
                panes:rect(parent.querySelector('.wpb-plan-panes')),
                upNext:rect(parent.querySelector('.wpb-plan-up-next')),
                overview:rect(parent.querySelector('.wpb-plan-overview')),
                weekPicker:rect(parent.querySelector('.wpb-plan-week-picker')),
                weekIntent:rect(parent.querySelector('[data-week-intent]')),
                overflow:document.documentElement.scrollWidth>innerWidth+2};
            });
            const supporting=width>=1060&&height>=700;
            assert.equal(layout.grid,supporting,'Plan supporting pane follows available window');
            assert.equal(layout.overflow,false,'Plan has no horizontal overflow');
            if(supporting){
              assert.ok(layout.overview.x>=layout.panes.right-2,
                'Program overview is the supporting pane beside schedule');
              if(layout.upNext)assert.ok(layout.upNext.x>=layout.panes.right-2,
                'Same Up Next card moves beside schedule without cloning state');
              if(layout.weekPicker&&layout.weekIntent)assert.ok(
                layout.weekIntent.y-layout.weekPicker.bottom<70,
                'Supporting-pane height does not create a gap between picker and week content');
            }else{
              assert.ok(layout.overview.y>=layout.panes.bottom-2,
                'Phone, medium and short windows retain vertical Plan reading order');
            }
          }
          if(route==='progress'){
            await page.click('.wpb-progress .wpb-premium-tabs button:first-child');
            await page.waitForSelector('.wpb-consistency-card');
            const progress=await page.evaluate(()=>{
              const main=document.querySelector('.wpb-progress [data-scroll-key="sessions"]');
              const consistency=main.querySelector('.wpb-consistency-card').getBoundingClientRect();
              const filters=main.querySelector('.wpb-history-filters').getBoundingClientRect();
              const glance=main.querySelector('[data-progress-glance]').getBoundingClientRect();
              return {grid:getComputedStyle(main).display==='grid',
                consistency:{x:consistency.x,right:consistency.right,bottom:consistency.bottom},
                filters:{x:filters.x,y:filters.y},
                glance:{width:glance.width},
                viewportOverflow:document.documentElement.scrollWidth>innerWidth+2,
                historyRows:main.querySelectorAll('.wpb-history-session').length};
            });
            const expandedProgress=width>=1120&&height>=720;
            assert.equal(progress.grid,expandedProgress,
              'R70 history layout follows available window size');
            assert.ok(progress.historyRows>=1,'R70 fixture renders actual workout history');
            assert.equal(progress.viewportOverflow,false,'Progress has no horizontal overflow');
            if(expandedProgress){
              assert.ok(progress.filters.x>=progress.consistency.right-2,
                'Real history filters appear beside consistency, not below it');
              assert.ok(progress.glance.width>600,
                'Summary extends across the expanded Progress surface');
            }else{
              assert.ok(progress.filters.y>=progress.consistency.bottom-2,
                'Compact/medium Progress retains its original reading order');
            }
            await page.click('.wpb-progress .wpb-premium-tabs button:nth-child(4)');
            await page.waitForSelector('.wpb-progress-panel');
            const body=await page.evaluate(()=>{
              const panel=document.querySelector('.wpb-progress-panel');
              const r=panel.getBoundingClientRect();
              return {width:r.width,right:r.right,viewport:innerWidth};
            });
            assert.ok(body.width>180&&body.right<=body.viewport+2,
              'Body chart and summary stay inside the current window');
          }
          const state=await geometry();
          assert.equal(state.direction,initial.direction,
            'Navigation position stays stable when switching to '+route);
          assert.equal(state.overflow,false,'No document overflow on '+route);
        }
        // R69: use the real Home→Library→Detail path. Wide screens reflow
        // the existing detail into a pane; compact and medium retain overlay.
        await page.click('button[aria-label="Exercise library"]');
        await page.waitForSelector('.wpb-library-row');
        await page.click('.wpb-library-row');
        await page.waitForSelector('.wpb-exercise-detail');
        const libraryGeometry=()=>page.evaluate(()=>{
          const library=document.querySelector('.wpb-library');
          const portal=library.querySelector(':scope > .wpb-portal');
          const detail=portal?.querySelector('.wpb-exercise-detail');
          const results=library.querySelector('.wpb-library-results');
          const bounds=el=>{const r=el.getBoundingClientRect();
            return {x:r.x,y:r.y,right:r.right,bottom:r.bottom,width:r.width,height:r.height};};
          return {pane:getComputedStyle(library).display==='grid',
            positioning:getComputedStyle(detail).position,
            detail:bounds(detail),results:bounds(results),
            library:bounds(library),portal:bounds(portal),
            portalPosition:getComputedStyle(portal).position,
            portalGridColumn:getComputedStyle(portal).gridColumn,
            portalGridRow:getComputedStyle(portal).gridRow,
            portalTransform:getComputedStyle(portal).transform,
            detailTransform:getComputedStyle(detail).transform,
            detailLeft:getComputedStyle(detail).left,
            ancestor:detail.parentElement?.className,
            selected:library.querySelectorAll('.wpb-library-row[aria-current="true"]').length,
            title:detail.querySelector('.wpb-exercise-detail-header').textContent,
            overflow:document.documentElement.scrollWidth>innerWidth+2};
        });
        let libraryLayout=await libraryGeometry();
        const twoPane=width>=1120&&height>=720;
        assert.equal(libraryLayout.pane,twoPane,'Library uses a list/detail pane only on expanded tall windows');
        assert.equal(libraryLayout.overflow,false,'Library/detail must not cause document overflow');
        assert.equal(libraryLayout.selected,1,'Exactly one selected Library row is exposed');
        if(twoPane){
          assert.ok(libraryLayout.detail.x>=libraryLayout.results.right-2,
            'Detail pane sits beside the usable filtered Library results: '+JSON.stringify(libraryLayout));
          const title=libraryLayout.title;
          await page.click('.wpb-library-results > .wpb-library-row:nth-child(2)');
          libraryLayout=await libraryGeometry();
          assert.notEqual(libraryLayout.title,title,
            'Switching selected Library exercise updates the existing detail pane');
          await page.setViewport({width:900,height:1000,deviceScaleFactor:1,isMobile:false,hasTouch:false});
          const collapsed=await libraryGeometry();
          assert.equal(collapsed.pane,false,'Resizing to medium restores full-screen detail');
          assert.equal(collapsed.title,libraryLayout.title,
            'Responsive resize preserves the selected exercise and details');
          await page.setViewport({width,height,deviceScaleFactor:1,isMobile:!wide,hasTouch:!wide});
          assert.equal((await libraryGeometry()).pane,true,'Re-expanding restores the supporting pane');
        }else{
          assert.equal(libraryLayout.positioning,'fixed','Compact detail stays full-screen');
        }
        await page.click('.wpb-exercise-detail button[aria-label="Back to library"]');
        await page.waitForFunction(()=>!document.querySelector('.wpb-exercise-detail'));
        await page.click('.wpb-library-header button[aria-label="Back"]');
        await page.waitForSelector('[data-view-frame="home"]');
        // R71: exercise the real Home→Wizard path. The right-hand overview
        // reads the ONE active form; resizing never remounts or copies the draft.
        await page.click('.wpb-home-create');
        await page.waitForSelector('.wpb-wizard .wpb-wizard-step');
        const creationMode=theme==='rose'?'Training cycle':'Single program';
        const chosen=await page.evaluate(label=>{
          const control=[...document.querySelectorAll('.wpb-wizard-step button')]
            .find(el=>el.textContent.includes(label));
          if(!control)return false;
          control.click();return true;
        },creationMode);
        assert.equal(chosen,true,'Program/cycle mode is offered from the same Wizard');
        await page.click('.wpb-wizard-next-action');
        if(creationMode==='Training cycle'){
          await page.waitForFunction(()=>document.querySelector('.wpb-wizard-review-title')
            ?.textContent==='Cycle overview');
          await page.click('.wpb-wizard-next-action');
        }
        await page.waitForSelector('.wpb-wizard-step input');
        await page.type('.wpb-wizard-step input','R71 responsive '+theme);
        const reviewLayout=()=>page.evaluate(()=>{
          const wizard=document.querySelector('.wpb-wizard');
          const layout=wizard.querySelector('.wpb-wizard-layout');
          const form=layout.querySelector('.wpb-wizard-step');
          const review=layout.querySelector('.wpb-wizard-review');
          const a=form.getBoundingClientRect(),b=review.getBoundingClientRect();
          return {grid:getComputedStyle(layout).display==='grid',
            visible:getComputedStyle(review).display!=='none',
            wizardWidth:wizard.getBoundingClientRect().width,
            formRight:a.right,reviewX:b.x,
            text:review.textContent,
            input:form.querySelector('input')?.value,
            overflow:document.documentElement.scrollWidth>innerWidth+2};
        });
        const initialReview=await reviewLayout();
        const expandedWizard=width>=1120&&height>=720;
        assert.equal(initialReview.grid,expandedWizard,'Builder pane follows available window');
        assert.equal(initialReview.visible,expandedWizard,'Builder review is optional on compact screens');
        assert.equal(initialReview.overflow,false,'Builder never creates horizontal document overflow');
        if(expandedWizard){
          assert.ok(initialReview.wizardWidth>800,'Wizard expands beyond the old phone-only route');
          assert.ok(initialReview.reviewX>=initialReview.formRight-2,
            'The LIVE setup review sits beside the SAME form');
          assert.ok(initialReview.text.includes('R71 responsive '+theme),
            'Review updates immediately from the name field');
          assert.ok(initialReview.text.includes('Session capacity') &&
            initialReview.text.includes('Supersets'),'Review includes real configuration constraints');
          if(creationMode==='Training cycle')assert.ok(
            initialReview.text.includes('Phases') &&
            initialReview.text.includes('Phase exercise changes'),
            'Cycle preview uses the selected live cycle template');
          await page.setViewport({width:900,height:1000,deviceScaleFactor:1,isMobile:false,hasTouch:false});
          const mediumReview=await reviewLayout();
          assert.equal(mediumReview.visible,false,'Medium windows return to focused single form');
          assert.equal(mediumReview.input,initialReview.input,
            'Resizing the Wizard preserves the current input');
          await page.setViewport({width,height,deviceScaleFactor:1,isMobile:!wide,hasTouch:!wide});
          assert.equal((await reviewLayout()).text,initialReview.text,
            'Re-expanding restores the same live preview without reinitializing state');
        }
        await page.click('.wpb-wizard-header button[aria-label="Back"]');
        if(creationMode==='Training cycle')
          await page.click('.wpb-wizard-header button[aria-label="Back"]');
        await page.click('.wpb-wizard-header button[aria-label="Back"]');
        await page.waitForSelector('[data-view-frame="home"]');
        if(wide){
          await page.focus('[data-tab="plan"]');
          await page.keyboard.press('ArrowDown');
          await page.waitForFunction(()=>document.querySelector('[data-tab="progress"]')
            ?.getAttribute('aria-current')==='page');
          await page.keyboard.press('ArrowUp');
          await page.waitForFunction(()=>document.querySelector('[data-tab="plan"]')
            ?.getAttribute('aria-current')==='page');
        }
        await page.screenshot({path:path.join(root,'verification/r65-'+theme+'-'+width+'x'+height+'-phone.png')});
        assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')).saved),[program],
          'Navigation layout does not mutate saved prescriptions');
        assert.deepEqual(errors,[],'No runtime errors: '+errors.join('; '));
        results.push({theme,width,height,mode:wide?'rail':'bottom',frame:initial.frame.width,
          rail:initial.bar.width,targets:initial.tabs.length});
      }finally{await context.close();}
    }
  }
  console.log('PASS R65 adaptive shell, directional keyboard navigation, route preservation: '+JSON.stringify(results));
}finally{
  if(browser)await browser.close();
  await new Promise(resolve=>server.close(resolve));
}
