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
    for(const [width,height,wide] of [[393,852,false],[600,800,false],[768,900,false],[900,480,false],[900,1000,true],[1280,850,true]]){
      const context=await browser.createBrowserContext();
      const page=await context.newPage();
      // Puppeteer's media helper rejects forced-colors, but Chrome CDP supports
      // it directly. Keep a distinct per-context session for real OS-mode CSS.
      const cdp=await page.createCDPSession();
      const forcedMedia=active=>cdp.send('Emulation.setEmulatedMedia',{
        features:[
          {name:'prefers-reduced-motion',value:'reduce'},
          {name:'forced-colors',value:active?'active':'none'}
        ]
      });
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
        assert.ok(initial.tabs.every(tab=>tab.width>=44&&tab.height>=44&&tab.label),
          'R74 navigation exposes named 44px keyboard/touch targets: '+JSON.stringify(initial.tabs));
        assert.equal(initial.overflow,false,'No horizontal viewport overflow: '+JSON.stringify(initial));
        if(wide){
          assert.equal(initial.direction,'column','Expanded screen must use an actual navigation rail');
          assert.ok(initial.frame.width>780,'Expanded content shell is no longer locked to 460px');
          assert.ok(initial.bar.width>=95&&initial.bar.height>initial.bar.width*2,
            'Rail is a vertical, independently sized surface');
          assert.ok(initial.bar.right<=initial.content.x+3,
            'Rail occupies its own column without covering content');
        }else{
          assert.equal(initial.direction,'row','Compact/medium windows retain bottom navigation');
          const medium=width>=600&&width<840&&height>=640;
          if(medium){
            assert.ok(initial.frame.width>=Math.min(width,760)-3,
              'Medium windows use the available reading width: '+JSON.stringify(initial));
            assert.ok(initial.frame.width<=760,
              'Medium tab shell remains bounded for readability');
          }else{
            assert.ok(initial.frame.width<=462,
              'Compact and short-landscape windows retain the proven phone-width frame');
          }
          assert.ok(initial.bar.y>initial.content.y+initial.content.height-10,
            'Bottom navigation stays below content');
        }
        for(const route of ['plan','progress','profile','settings','home']){
          await page.click('[data-tab="'+route+'"]');
          await page.waitForSelector('[data-view-frame="'+route+'"]');
          const shell=await page.evaluate(()=>({
            title:[...document.querySelectorAll('.wpb-screen-title')].map(el=>({
              size:getComputedStyle(el).fontSize,tag:el.tagName})),
            tabs:[...document.querySelectorAll('[data-tab]')].map(el=>({
              visible:el.innerText.trim(),name:el.getAttribute('aria-label')}))
          }));
          assert.ok(shell.title.length===1&&shell.title[0].size==='26px'&&shell.title[0].tag==='H1',
            'M270 root titles share one relative typography role: '+JSON.stringify({route,shell}));
          assert.ok(shell.tabs.every(t=>t.visible===t.name),
            'M270 visible navigation keeps the full destination names at every width');
          if(route==='progress'){
            await page.evaluate(()=>document.documentElement.style.fontSize='200%');
            const wordsFit=await page.$$eval('.wpb-progress-glance-label',labels=>labels.every(label=>{
              const text=label.firstChild,box=label.closest('.wpb-progress-glance-item').getBoundingClientRect();
              if(!text||text.nodeType!==Node.TEXT_NODE)return false;
              return [...text.textContent.matchAll(/\S+/g)].every(match=>{
                const range=document.createRange();range.setStart(text,match.index);range.setEnd(text,match.index+match[0].length);
                const rects=[...range.getClientRects()].filter(r=>r.width>0);
                return rects.length===1&&rects[0].left>=box.left-1&&rects[0].right<=box.right+1;
              });
            }));
            assert.ok(wordsFit,'M270 populated summaries keep whole readable words at 200% root text');
            await page.evaluate(()=>document.documentElement.style.removeProperty('font-size'));
          }
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
            // R74: exercise actual Windows forced-colors rendering, not just
            // matching the CSS declaration. Semantic selection must remain
            // distinguishable even if the moving rail or theme fill vanishes.
            await forcedMedia(true);
            const forced=await page.evaluate(sel=>{
              const controls=[...document.querySelectorAll(sel)];
              const selected=controls.find(el=>el.getAttribute('aria-selected')==='true' ||
                el.getAttribute('aria-pressed')==='true' ||
                el.getAttribute('aria-current')==='page');
              const idle=controls.find(el=>el!==selected);
              return {
                active:matchMedia('(forced-colors:active)').matches,
                selected: selected&&getComputedStyle(selected).textDecorationLine,
                idle:idle&&getComputedStyle(idle).textDecorationLine,
                overflow:document.documentElement.scrollWidth>innerWidth+2
              };
            },selector);
            assert.ok(forced.active&&forced.selected?.includes('underline')&&
              !forced.idle?.includes('underline')&&!forced.overflow,
              'R74 system contrast retains a non-color selected indication: '+JSON.stringify({route,forced}));
            await forcedMedia(false);
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
            assert.ok(regular.properties.every(v=>v==='color'||v==='background-color'),
              route+' must not animate width, position or text input geometry: '+JSON.stringify(regular));
            // R73/B854: desktop pointer feedback resolves from the same saved
            // theme role on idle choices. Selected moving indicators are not
            // overridden, and no view selection changes on mere hover.
            if(wide){
              const hover=await page.evaluate(sel=>{
                const list=[...document.querySelectorAll(sel)];
                const index=list.findIndex(n=>n.getAttribute('aria-pressed')!=='true' &&
                  n.getAttribute('aria-selected')!=='true' &&
                  n.getAttribute('aria-current')!=='page');
                if(index<0)throw new Error('No idle '+sel+' button');
                const probe=document.createElement('i');
                probe.style.backgroundColor='var(--pi-color-feature)';
                document.querySelector('.wpb').append(probe);
                const expected=getComputedStyle(probe).backgroundColor;
                probe.remove();
                return {index:index+1,expected,
                  selected:list.filter(n=>n.getAttribute('aria-pressed')==='true' ||
                    n.getAttribute('aria-selected')==='true' ||
                    n.getAttribute('aria-current')==='page').length};
              },selector);
              assert.equal(hover.selected,1,'Exactly one semantic '+route+' tab is active');
              await page.hover(selector+':nth-of-type('+hover.index+')');
              await new Promise(resolve=>setTimeout(resolve,220));
              const hovered=await page.$eval(selector+':nth-of-type('+hover.index+')',
                node=>({paint:getComputedStyle(node).backgroundColor,
                  state:node.matches(':hover'),disabled:node.disabled,
                  rootMotion:document.querySelector('#root')?.getAttribute('data-pi-motion'),
                  fine:matchMedia('(pointer:fine)').matches,
                  hoverCapable:matchMedia('(hover:hover)').matches,
                  forcedOff:matchMedia('(forced-colors:none)').matches,
                  selected:node.getAttribute('aria-selected')==='true' ||
                    node.getAttribute('aria-pressed')==='true' ||
                    node.getAttribute('aria-current')==='page'}));
              assert.ok(hovered.state&&!hovered.selected,
                'Idle '+route+' hover never changes the selected tab');
              if(hovered.fine&&hovered.hoverCapable){
                assert.equal(hovered.paint,hover.expected,
                  'Fine-pointer hover resolves to saved-theme feature fill: '+JSON.stringify({hover,hovered}));
              }else{
                // Headless CI reports no fine hover device; honor that media
                // condition rather than pretending a synthesized pointer is one.
                assert.notEqual(hovered.paint,hover.expected,
                  'Non-hover browser should not paint a desktop-only hover layer');
              }
              await page.mouse.down();
              await new Promise(resolve=>setTimeout(resolve,220));
              const pressed=await page.$eval(selector+':nth-of-type('+hover.index+')',
                node=>{
                  const probe=document.createElement('i');
                  probe.style.backgroundColor='var(--pi-color-inset)';
                  document.querySelector('.wpb').append(probe);
                  const expected=getComputedStyle(probe).backgroundColor;
                  probe.remove();
                  return {active:node.matches(':active'),
                    paint:getComputedStyle(node).backgroundColor,expected};
                });
              assert.ok(pressed.active&&pressed.paint===pressed.expected,
                'Idle '+route+' press resolves to theme inset tint: '+JSON.stringify(pressed));
              await page.mouse.move(2,2);
              await page.mouse.up();
            }
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
                planWidth:rect(parent)?.width,
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
            if(width>=600&&width<840&&height>=640)
              assert.ok(layout.planWidth>=Math.min(width,720)-4,
                'Plan uses a readable medium-width workspace, not the 460px phone frame: '+JSON.stringify(layout));
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
            // R78: calendar is open, exact workout dates are labeled and tappable;
            // milestone content remains collapsed until the lifter chooses it.
            assert.ok(await page.$('.wpb-training-calendar'),'Training calendar is immediately available');
            assert.deepEqual(await page.$$eval('.wpb-calendar-weekdays abbr',
              items=>items.map(item=>[item.textContent,item.title])),
              [['Sun','Sunday'],['Mon','Monday'],['Tue','Tuesday'],['Wed','Wednesday'],
                ['Thu','Thursday'],['Fri','Friday'],['Sat','Saturday']],
              'Weekday headings are distinct and expose their full names');
            const monthFacts=await page.$eval('.wpb-calendar-month-stats',
              el=>el.textContent);
            assert.match(monthFacts,/Training days this month/,
              'Calendar measures actual training days, not consecutive-day activity');
            assert.match(monthFacts,/Workouts this month/,
              'Calendar distinguishes workouts from distinct training days');
            assert.doesNotMatch(monthFacts,/Current streak|Longest/,
              'Calendar avoids irrelevant daily-streak pressure');
            assert.ok(await page.$$eval('.wpb-calendar-week',rows=>rows.length>0&&rows.every(row=>row.children.length===7)),
              'Calendar weeks contain exactly seven dates, no unlabeled total column');
            assert.equal(await page.$eval('.wpb-milestones-disclosure',el=>el.open),false,
              'Milestones are collapsed by default independently of the calendar');
            assert.equal(await page.$('.wpb-consistency-card [role="img"]'),null,
              'The ambiguous 28-square consistency chart has been retired');
            const trainingDates=await page.$$eval('.wpb-calendar-date[data-workouts]',els=>els.map(el=>({
              date:el.dataset.day,count:Number(el.dataset.workouts),label:el.getAttribute('aria-label'),disabled:el.disabled
            })));
            assert.equal(trainingDates.length,1,'Calendar shows the exact recorded lift day from fixture');
            assert.ok(trainingDates[0].count===1&&!trainingDates[0].disabled&&/1 workout/.test(trainingDates[0].label),
              'Recorded lift day has clear accessible date and session count');
            await page.click('.wpb-calendar-date[data-workouts]');
            await page.waitForSelector('.wpb-calendar-day-detail');
            assert.match(await page.$eval('.wpb-calendar-day-detail',el=>el.textContent),/Upper/,
              'Tapping the exact date shows the recorded workout, not aggregate consistency');
            const legacyFacts=await page.$eval('.wpb-calendar-day-detail .wpb-calendar-session-summary',
              el=>el.textContent.trim());
            assert.equal(legacyFacts,'2 sets · 1,320 lb · 40m',
              'Legacy history detail uses completed working sets, tonnage and elapsed seconds');
            assert.ok(!legacyFacts.startsWith('0 sets'),
              'Legacy completed records cannot display a false zero-set total');
            assert.equal(await page.$eval('.wpb-milestones-disclosure',el=>el.open),false,
              'Selecting calendar history does not expand milestones');
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
        // R88: resizing within the same Plan must not reset week selection or
        // create a second navigation/data owner. Exercise the actual live DOM.
        if(width===768&&theme==='rose'){
          await page.click('[data-tab="plan"]');
          await page.waitForSelector('.wpb-plan-view [data-plan-week="2"]');
          // The seeded history can make week 2 the current/default-open week.
          // Clicking an already-expanded week would close it, so establish
          // the desired state without depending on initial scheduling.
          const weekTwoOpen=await page.$eval('.wpb-plan-view [data-plan-week="2"] > button',
            el=>el.getAttribute('aria-expanded')==='true');
          if(!weekTwoOpen) await page.click('.wpb-plan-view [data-plan-week="2"] > button');
          await page.waitForFunction(()=>document.querySelector(
            '.wpb-plan-view [data-plan-week="2"] > button')?.getAttribute('aria-expanded')==='true');
          await page.setViewport({width:393,height:852,deviceScaleFactor:1,isMobile:true,hasTouch:true});
          await page.waitForFunction(()=>document.querySelector('.wpb-plan-view')?.getBoundingClientRect().width<=462);
          assert.equal(await page.$eval('.wpb-plan-view [data-plan-week="2"] > button',
            el=>el.getAttribute('aria-expanded')),'true',
            'Selected Plan week survives compact reflow');
          await page.setViewport({width:768,height:900,deviceScaleFactor:1,isMobile:true,hasTouch:true});
          await page.waitForFunction(()=>document.querySelector('.wpb-plan-view')?.getBoundingClientRect().width>=715);
          assert.equal(await page.$eval('.wpb-plan-view [data-plan-week="2"] > button',
            el=>el.getAttribute('aria-expanded')),'true',
            'Selected Plan week survives medium reflow');
          assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+2),false,
            'Medium Plan reflow never causes horizontal overflow');
          await page.click('[data-tab="home"]');
          await page.waitForSelector('[data-view-frame="home"]');
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
        // R74: the real Library detail back action needs a focus treatment even
        // on light palettes. Keyboard modality should activate :focus-visible.
        await page.keyboard.press('Tab');
        await page.focus('.wpb-exercise-detail button[aria-label="Back to library"]');
        const backRing=await page.$eval('.wpb-exercise-detail button[aria-label="Back to library"]',
          el=>({focused:document.activeElement===el,
            visible:el.matches(':focus-visible'),
            width:parseFloat(getComputedStyle(el).outlineWidth),
            color:getComputedStyle(el).outlineColor,
            style:getComputedStyle(el).outlineStyle}));
        assert.ok(backRing.focused&&backRing.visible&&backRing.width>=2&&
          backRing.style==='solid','R74 Library back has visible keyboard focus: '+JSON.stringify(backRing));
        await forcedMedia(true);
        const forcedBack=await page.$eval('.wpb-exercise-detail button[aria-label="Back to library"]',
          el=>({visible:el.matches(':focus-visible'),width:parseFloat(getComputedStyle(el).outlineWidth),
            style:getComputedStyle(el).outlineStyle,
            color:getComputedStyle(el).outlineColor}));
        assert.ok(forcedBack.visible&&forcedBack.width>=2&&forcedBack.style==='solid',
          'R74 Library back ring survives Windows high contrast: '+JSON.stringify(forcedBack));
        await forcedMedia(false);
        // R74/B856: Escape invokes the existing Back route and returns keyboard
        // focus to the *origin* row. Closing detail may clear aria-current;
        // use the row's accessible text, not a stale selected-state attribute.
        const originRowText=await page.$eval(
          '.wpb-library-results .wpb-library-row[aria-current="true"]',
          el=>el.textContent.trim());
        await page.keyboard.press('Escape');
        await page.waitForFunction(()=>!document.querySelector('.wpb-exercise-detail'));
        await page.waitForFunction(expected=>
          document.activeElement?.matches('.wpb-library-results .wpb-library-row') &&
          document.activeElement.textContent.trim()===expected,
          {timeout:5000},originRowText);
        const escapedFocus=await page.evaluate(()=>({
          text:document.activeElement?.textContent?.trim(),
          row:document.activeElement?.matches('.wpb-library-results .wpb-library-row')
        }));
        assert.ok(escapedFocus.row&&escapedFocus.text===originRowText,
          'Escape returns focus to its source Library row: '+
          JSON.stringify({originRowText,escapedFocus}));
        // The ordinary Back button must restore the same keyboard origin.
        await page.keyboard.press('Enter');
        await page.waitForSelector('.wpb-exercise-detail button[aria-label="Back to library"]');
        await page.click('.wpb-exercise-detail button[aria-label="Back to library"]');
        await page.waitForFunction(()=>!document.querySelector('.wpb-exercise-detail'));
        await page.waitForFunction(expected=>
          document.activeElement?.matches('.wpb-library-results .wpb-library-row') &&
          document.activeElement.textContent.trim()===expected,
          {timeout:5000},originRowText);
        const backFocus=await page.evaluate(()=>document.activeElement?.textContent?.trim());
        assert.equal(backFocus,originRowText,
          'Back returns focus to the same Library origin');
        await page.click('.wpb-library-header button[aria-label="Back"]');
        await page.waitForSelector('[data-view-frame="home"]');
        // R71: exercise the real Home→Wizard path. The right-hand overview
        // reads the ONE active form; resizing never remounts or copies the draft.
        await page.$eval('.wpb-home-new-program',el=>el.scrollIntoView({block:'center'}));
        await page.click('.wpb-home-new-program');
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
        assert.equal(await page.$eval('.wpb-wizard-step input',el=>el.getAttribute('aria-label')),
          creationMode==='Training cycle'?'Cycle name':'Program name',
          'Creation name field has a stable accessible label in both modes');
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
        // R84: diagnose (do not retry or conceal) any missed primary-nav tap
        // after a rail-to-compact viewport switch. Report actual pointer hit,
        // navigation state, overlays and click delivery before blaming timing.
        const zoomNavState=route=>page.evaluate(target=>{
          const button=document.querySelector('.wpb-tabbar button[data-tab="'+target+'"]');
          const r=button?.getBoundingClientRect();
          const x=r?Math.max(0,Math.min(innerWidth-1,r.left+r.width/2)):0;
          const y=r?Math.max(0,Math.min(innerHeight-1,r.top+r.height/2)):0;
          const top=document.elementFromPoint(x,y);
          const stack=document.elementsFromPoint(x,y).slice(0,6).map(el=>({
            tag:el.tagName,cls:typeof el.className==='string'?el.className.slice(0,85):'',
            role:el.getAttribute('role')
          }));
          const frame=document.querySelector('[data-view-frame]');
          const active=document.querySelector('.wpb-tabbar button[aria-current="page"]');
          const overlay=[...document.querySelectorAll('.wpb-backdrop,.wpb-dismiss-layer,.wpb-closing')]
            .filter(el=>{const cs=getComputedStyle(el),b=el.getBoundingClientRect();
              return b.width>0&&b.height>0&&cs.display!=='none'&&cs.visibility!=='hidden';})
            .map(el=>el.className).slice(0,5);
          return {target,frame:frame?.dataset.viewFrame,active:active?.dataset.tab,
            width:innerWidth,height:innerHeight,scale:devicePixelRatio,
            navBox:r?{x:r.x,y:r.y,width:r.width,height:r.height}:null,
            navHit:!!button&&!!r&&r.width>=44&&r.height>=44&&
              button.contains(top),pointerStack:stack,overlay,
            delivered:window.__r84NavClicks||[],pageErrors:window.__r84PageErrors||[]};
        },route);
        const zoomNavClick=async(route,theme,width)=>{
          await page.waitForFunction(target=>{
            const el=document.querySelector('.wpb-tabbar button[data-tab="'+target+'"]');
            const rect=el?.getBoundingClientRect();
            const point=rect&&document.elementFromPoint(
              rect.left+rect.width/2,rect.top+rect.height/2);
            return !!rect&&rect.width>=44&&rect.height>=44&&
              el.contains(point);
          },{polling:'raf',timeout:6000},route).catch(async e=>{
            const state=await zoomNavState(route);
            throw new Error('R84 zoom nav is not hittable before click: '+
              JSON.stringify({theme,width,state,cause:e.message}));
          });
          await page.evaluate(target=>{
            window.__r84NavClicks=[];
            const el=document.querySelector('.wpb-tabbar button[data-tab="'+target+'"]');
            el.addEventListener('click',event=>window.__r84NavClicks.push({
              target,eventTrusted:event.isTrusted,activeBefore:
                document.querySelector('.wpb-tabbar [aria-current="page"]')?.dataset.tab
            }),{capture:true,once:true});
          },route);
          await page.click('[data-tab="'+route+'"]');
          try{
            await page.waitForFunction(target=>{
              const frame=document.querySelector('[data-view-frame]');
              const current=document.querySelector('.wpb-tabbar [aria-current="page"]');
              return frame?.dataset.viewFrame===target&&current?.dataset.tab===target;
            },{polling:'raf',timeout:6000},route);
          }catch(e){
            const state=await zoomNavState(route);
            await page.screenshot({path:path.join(root,
              'verification/r84-nav-'+theme+'-'+width+'-'+route+'-failure-phone.png')});
            throw new Error('R84 delivered navigation did not activate: '+
              JSON.stringify({theme,width,state,cause:e.message}));
          }
        };
        // R74/B856: browser 200% zoom halves CSS viewport dimensions while
        // doubling physical-pixel density. Exercise the original routes at the
        // zoomed layout viewport, not a fake CSS transform or font-size proxy.
        if(wide && height>=720){
          const zoomWidth=Math.round(width/2),zoomHeight=Math.round(height/2);
          await page.setViewport({width:zoomWidth,height:zoomHeight,
            deviceScaleFactor:2,isMobile:false,hasTouch:false});
          // Let the native resized shell complete layout before evaluating
          // the *actual* tap geometry; do not add an arbitrary sleep.
          await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(
            ()=>requestAnimationFrame(resolve))));
          for(const route of ['plan','progress','settings','home']){
            await zoomNavClick(route,theme,width);
            const zoom=await page.evaluate(()=>{
              const nav=[...document.querySelectorAll('.wpb-tabbar button[data-tab]')];
              const labels=nav.map(el=>({
                name:el.getAttribute('aria-label'),
                width:el.getBoundingClientRect().width,
                height:el.getBoundingClientRect().height
              }));
              return {
                overflow:document.documentElement.scrollWidth>innerWidth+2,
                nav:labels,viewport:innerWidth
              };
            });
            assert.equal(zoom.overflow,false,
              '200% zoom must preserve reflow without horizontal page overflow: '+
              JSON.stringify({route,theme,width,zoom}));
            assert.ok(zoom.nav.every(item=>item.width>=44&&item.height>=44&&item.name),
              '200% zoom retains named practical navigation targets: '+
              JSON.stringify({route,theme,width,zoom}));
          }
          await page.setViewport({width,height,deviceScaleFactor:1,
            isMobile:!wide,hasTouch:!wide});
          await page.waitForSelector('[data-view-frame="home"]');
        }
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

  // R79: a legacy date-only history entry is a local workout day, NOT UTC.
  // This previously showed Tuesday's workout under Monday west of Greenwich.
  // Use the actual America/New_York browser timezone, independently of CI TZ.
  {
    const context=await browser.createBrowserContext();
    const page=await context.newPage();
    try{
      await page.setViewport({width:393,height:852,deviceScaleFactor:1,isMobile:true,hasTouch:true});
      await page.emulateTimezone('America/New_York');
      const dateOnly=await page.evaluate(()=>{
        const now=new Date();
        return [now.getFullYear(),String(now.getMonth()+1).padStart(2,'0'),
          String(now.getDate()).padStart(2,'0')].join('-');
      });
      const [year,month,day]=dateOnly.split('-').map(Number);
      const seed=store('rose');
      seed.history[0].date=dateOnly;
      await page.evaluateOnNewDocument(data=>localStorage.setItem('wpb:v1',JSON.stringify(data)),seed);
      await page.goto('http://127.0.0.1:8798/',{waitUntil:'networkidle0'});
      await page.waitForSelector('.wpb-tabbar');
      await page.click('[data-tab="progress"]');
      await page.waitForSelector('.wpb-training-calendar');
      const actual=await page.$eval('.wpb-calendar-date[data-workouts]',el=>({
        key:el.getAttribute('data-day'),label:el.getAttribute('aria-label')
      }));
      assert.equal(actual.key,year+'-'+(month-1)+'-'+day,
        'Date-only imported workout stays on its local date in New York');
      assert.match(actual.label,/1 workout/,'Imported session remains a clickable real date');
      assert.deepEqual(await page.$$eval('.wpb-calendar-month-stats>div .mono',
        els=>els.map(el=>el.textContent.trim())),['1','1'],
        'Monthly training days and sessions both include the correct imported date');
      assert.equal(await page.$eval('.wpb-milestones-disclosure',el=>el.open),false,
        'Milestones remain collapsed for legacy date history');
      await page.click('.wpb-calendar-date[data-workouts]');
      assert.match(await page.$eval('.wpb-calendar-day-detail',el=>el.textContent),/Upper/,
        'Imported date opens its actual session details');
      assert.equal(await page.$eval('.wpb-calendar-day-detail .wpb-calendar-session-summary',
        el=>el.textContent.trim()),'2 sets · 1,320 lb · 40m',
        'Date-only imports preserve legacy session facts after calendar-day correction');
    }finally{await context.close();}
  }
  // R81: Two actual sessions on one calendar date are two workouts but only
  // one training day. A legacy session with 8 *prescribed* sets but only one
  // completed set must report one; unknown volume/duration must stay omitted.
  {
    const context=await browser.createBrowserContext();
    const page=await context.newPage();
    try{
      await page.setViewport({width:393,height:852,deviceScaleFactor:1,isMobile:true,hasTouch:true});
      await page.emulateTimezone('America/New_York');
      const dateOnly=await page.evaluate(()=>{
        const now=new Date();
        return [now.getFullYear(),String(now.getMonth()+1).padStart(2,'0'),
          String(now.getDate()).padStart(2,'0')].join('-');
      });
      const seed=store('rose');
      seed.history=[
        {id:'r81-partially-logged',programId:program.id,programName:program.name,
          dayId:'upper',dayLabel:'Morning Legacy Logged',weekIndex:1,
          date:dateOnly,unit:'lb',totalSets:8,
          perf:{'bb-bench':{sets:[{w:185,r:6,done:true}]}}},
        {id:'r81-modern-completed',programId:program.id,programName:program.name,
          dayId:'upper',dayLabel:'Evening Completed',weekIndex:1,
          date:dateOnly,unit:'lb',totalSets:10,setsDone:3,volume:2250,
          durationMin:55,perf:{'inc-curl':{sets:[{w:30,r:12,done:true}]}}}
      ];
      await page.evaluateOnNewDocument(data=>localStorage.setItem('wpb:v1',JSON.stringify(data)),seed);
      await page.goto('http://127.0.0.1:8798/',{waitUntil:'networkidle0'});
      await page.waitForSelector('.wpb-tabbar');
      await page.click('[data-tab="progress"]');
      await page.waitForSelector('.wpb-training-calendar');
      assert.deepEqual(await page.$$eval('.wpb-calendar-month-stats>div .mono',
        els=>els.map(el=>el.textContent.trim())),['1','2'],
        'Two workouts on one local date count as one training day and two workouts');
      const dates=await page.$$eval('.wpb-calendar-date[data-workouts]',
        els=>els.map(el=>({count:el.dataset.workouts,label:el.getAttribute('aria-label')})));
      assert.equal(dates.length,1,'Both sessions share one interactive training date');
      assert.equal(dates[0].count,'2','The calendar cell shows the session count');
      assert.match(dates[0].label,/2 workouts/,
        'Screen readers receive the exact number of workouts on the selected date');
      await page.click('.wpb-calendar-date[data-workouts]');
      await page.waitForSelector('.wpb-calendar-day-detail');
      const detail=await page.$eval('.wpb-calendar-day-detail',el=>el.textContent);
      assert.match(detail,/Morning Legacy Logged/,'The morning session is visible');
      assert.match(detail,/Evening Completed/,'The evening session is visible');
      const summaries=await page.$$eval(
        '.wpb-calendar-day-detail .wpb-calendar-session-summary',
        els=>els.map(el=>el.textContent.trim()).sort());
      assert.deepEqual(summaries,['1 set','3 sets · 2,250 lb · 55m'].sort(),
        'Each workout reports only recorded facts; eight prescribed sets cannot become eight completed');
      assert.equal(await page.$eval('.wpb-milestones-disclosure',el=>el.open),false,
        'Viewing multiple workouts does not unexpectedly expand milestones');
      assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+2),
        false,'Multiple workout details fit a narrow phone without horizontal overflow');
    }finally{await context.close();}
  }
  console.log('PASS R65 adaptive shell, directional keyboard navigation, route preservation: '+JSON.stringify(results));
}finally{
  if(browser)await browser.close();
  await new Promise(resolve=>server.close(resolve));
}
