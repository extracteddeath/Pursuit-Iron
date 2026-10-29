import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import puppeteer from 'puppeteer-core';
import { plannedWeek } from '../modules/engine-api.js';

// R53: two real saved-palette contexts, same program, same phone geometry.
// Both layouts must resolve the same semantic surface roles. Screenshot
// artifacts are PAIRS rather than cherry-picked screenshots of one theme.
const root=path.resolve(new URL('../',import.meta.url).pathname);
const program={
  id:'r53-surface',name:'R53 Plan Surface',custom:true,weeks:4,
  config:{name:'R53 Plan Surface',goal:'both',experience:'intermediate',progression:'double',split:'custom',weeks:4,days:2,session:'s60',unit:'lb',deload:false},
  days:[
    {id:'upper',label:'Upper',primaryIndex:0,exercises:['bb-bench','inc-curl']},
    {id:'lower',label:'Lower',primaryIndex:1,exercises:['back-squat','leg-press']}
  ],
  overrides:{
    'upper:0':{sets:4,reps:'6-8',rir:'2'},'upper:1':{sets:3,reps:'10-15',rir:'2'},
    'lower:0':{sets:4,reps:'5-8',rir:'2'},'lower:1':{sets:3,reps:'8-12',rir:'2'}
  }
};
const initial=theme=>({
  v:13,savedAt:1,saved:[program],cycles:[],history:[],perf:{},drafts:{},tombs:{},
  seenIntro:999,seenWhatsNew:999,pinnedId:program.id,theme,
  unit:'lb',unitChosen:true,experience:'intermediate',bodyweight:185,
  installDismissedAt:Date.now(),lastBackup:Date.now()
});
// R61: inspect the actual painted foreground against its composited background.
// A surface-only screenshot check missed pale session names on a light palette.
async function controlStyle(page,selector){
  return page.evaluate(sel=>{
    const node=document.querySelector(sel);
    if(!node)throw new Error('Missing control: '+sel);
    const canvas=document.createElement('canvas');canvas.width=canvas.height=1;
    const ctx=canvas.getContext('2d');
    const rgba=color=>{ctx.clearRect(0,0,1,1);ctx.fillStyle=color;ctx.fillRect(0,0,1,1);
      return [...ctx.getImageData(0,0,1,1).data];};
    const blend=(fg,bg)=>fg.slice(0,3).map((n,i)=>n*fg[3]/255+bg[i]*(1-fg[3]/255));
    const chain=[];for(let p=node;p;p=p.parentElement)chain.unshift(p);
    let bg=[255,255,255];for(const p of chain)bg=blend(rgba(getComputedStyle(p).backgroundColor),bg);
    const ink=blend(rgba(getComputedStyle(node).color),bg);
    const luminance=v=>v.map(n=>{n/=255;return n<=.04045?n/12.92:((n+.055)/1.055)**2.4;})
      .reduce((sum,n,i)=>sum+n*[.2126,.7152,.0722][i],0);
    const a=luminance(ink),b=luminance(bg),r=node.getBoundingClientRect();
    const button=node.closest('button')||node;
    const hit=document.elementFromPoint(r.left+r.width/2,r.top+r.height/2);
    return {label:node.textContent.trim(),contrast:(Math.max(a,b)+.05)/(Math.min(a,b)+.05),
      width:r.width,height:r.height,background:getComputedStyle(node).backgroundColor,
      foreground:getComputedStyle(node).color,pressed:node.getAttribute('aria-pressed'),
      action:node.dataset.action,hit:hit===button||button.contains(hit)};
  },selector);
}
async function verifyFoundationStates(browser){
  for(const theme of ['rose','rosewater'])for(const state of ['missing-weight','missing-lifts','scored']){
    const context=await browser.createBrowserContext(),page=await context.newPage();
    const errors=[];page.on('pageerror',e=>errors.push(e.message));
    const store={...initial(theme),bodyweight:state==='missing-weight'?'':185,
      history:state==='scored'?[{id:'r61-score',date:Date.now(),programId:program.id,
        programName:program.name,dayId:'upper',dayLabel:'Upper',weekIndex:1,unit:'lb',
        duration:1800,totalSets:1,tonnage:1110,
        perf:{'bb-bench':{weight:185,reps:6,sets:[{w:185,r:6,done:true,rir:2,rirReported:true}]}}}]:[]};
    try{
      await page.setViewport({width:393,height:852,deviceScaleFactor:1,isMobile:true,hasTouch:true});
      await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
      await page.evaluateOnNewDocument(seed=>{
        if(localStorage.getItem('r61-seeded'))return;
        localStorage.setItem('r61-seeded','1');localStorage.setItem('wpb:v1',JSON.stringify(seed));
        // Deliberately inconsistent startup cache: current store must win without a reload.
        localStorage.setItem('wpb:boot-theme',JSON.stringify({base:seed.theme==='rose'?'light':'dark',
          bg:'#080808',text:'#f8f8f8',muted:'#cccccc',accent:'#ffffff',onAccent:'#080808'}));
      },store);
      await page.goto('http://127.0.0.1:8795/',{waitUntil:'networkidle0'});
      await page.waitForSelector('.wpb-tabbar');
      await page.click('[data-tab="profile"]');
      await page.waitForSelector('.wpb-profile');
      const profile=await page.$eval('.wpb-profile',el=>el.textContent);
      if(state==='missing-weight'){
        assert.match(profile,/Add bodyweight to unlock/);
        await page.click('button[aria-label="Add bodyweight"]');
        await page.waitForSelector('.wpb-settings');
      }else if(state==='missing-lifts'){
        assert.match(profile,/185 lb/);assert.match(profile,/Log a weighted set for a supported lift/);
        assert.doesNotMatch(profile,/Add bodyweight to unlock/);
        assert.equal(await page.$('button[aria-label="Add bodyweight"]'),null);
      }else{
        await page.waitForSelector('.wpb-profile-strength');
        assert.equal(await page.$('.wpb-profile-setup'),null,'Scored history keeps its existing Strength card');
      }
      if(state==='missing-lifts'){
        // Exercise the Settings theme control itself, then reopen Plan and warm reload.
        const other=theme==='rose'?'rosewater':'rose';
        for(const palette of [theme,other,theme]){
          await page.click('[data-tab="settings"]');
          await page.waitForSelector('.wpb-theme-picker');
          await page.$eval('.wpb-theme-picker',el=>el.open=true);
          await page.click('[data-theme-option="'+palette+'"]');
          await page.click('[data-tab="plan"]');
          await page.waitForSelector('.wpb-plan-session-name');
          for(const width of [320,393]){
            await page.setViewport({width,height:852,deviceScaleFactor:1,isMobile:true,hasTouch:true});
            const name=await controlStyle(page,'.wpb-plan-session-name');
            assert.ok(name.contrast>=4.5,'Session label after actual theme selection: '+JSON.stringify({palette,width,name}));
            const week=await controlStyle(page,'.wpb-plan-week-chip[aria-pressed="true"]');
            assert.ok(week.contrast>=4.5&&week.height>=48,'Selected week is readable and tappable: '+JSON.stringify(week));
          }
        }
        // R63: a change to shared materials must survive every real theme choice.
        // Check painted Plan labels and selected week text after a live switch.
        if(theme==='rose'){
          await page.click('[data-tab="settings"]');
          const palettes=await page.$$eval('[data-theme-option]',buttons=>buttons.map(b=>b.dataset.themeOption));
          for(const palette of palettes){
            await page.click('[data-tab="settings"]');
            await page.$eval('.wpb-theme-picker',el=>el.open=true);
            await page.click('[data-theme-option="'+palette+'"]');
            await page.click('[data-tab="plan"]');
            await page.waitForSelector('.wpb-plan-session-name');
            const name=await controlStyle(page,'.wpb-plan-session-name');
            const week=await controlStyle(page,'.wpb-plan-week-chip[aria-pressed="true"]');
            assert.ok(name.contrast>=4.5&&week.contrast>=4.5,
              'Every theme keeps Plan names and selected weeks readable: '+JSON.stringify({palette,name,week}));
          }
          await page.click('[data-tab="settings"]');
          await page.$eval('.wpb-theme-picker',el=>el.open=true);
          await page.click('[data-theme-option="'+theme+'"]');
          console.log('PASS R63: live theme switching and actual Plan label/selected-week contrast for '+palettes.length+' presets.');
        }
        await page.reload({waitUntil:'networkidle0'});
        await page.waitForSelector('.wpb-tabbar');await page.click('[data-tab="plan"]');
        await page.waitForSelector('.wpb-plan-session-name');
        assert.ok((await controlStyle(page,'.wpb-plan-session-name')).contrast>=4.5,'Warm launch keeps readable session names');
        await page.click('[data-tab="home"]');await page.waitForSelector('.wpb-home');
        const start=await controlStyle(page,'.wpb-home .wpb-ui-action[data-action="primary"]');
        // Deferred actions are intentionally below the first viewport; bring
        // each into the live scroll viewport before checking pointer hits.
        await page.$eval('.wpb-home-new-program',el=>el.scrollIntoView({block:'center'}));
        const create=await controlStyle(page,'.wpb-home-new-program');
        await page.$eval('.wpb-home-freestyle',el=>el.scrollIntoView({block:'center'}));
        const freestyle=await controlStyle(page,'.wpb-home-freestyle');
        if(state==='scored'){
          const pulse=await page.evaluate(()=>{
            const metrics=document.querySelector('.wpb-metric-strip');
            const programList=document.querySelector('.wpb-home-programs');
            return {exists:!!metrics,labels:metrics?.textContent||'',
              ahead:!!metrics&&!!programList&&!!(metrics.compareDocumentPosition(programList)&
                Node.DOCUMENT_POSITION_FOLLOWING)};
          });
          assert.ok(pulse.exists&&pulse.ahead&&pulse.labels.includes('Last 7 days')&&
            pulse.labels.includes('Streak')&&!pulse.labels.includes('Total'),
            'R91 recent training belongs directly after the workout, before library management: '+
              JSON.stringify(pulse));
        }
        assert.equal(await page.$('.wpb-home-compose-bar'),null,
          'Ready workout never competes with an anchored Create Program action');
        assert.ok(start.contrast>=4.5&&create.contrast>=4.5&&freestyle.contrast>=4.5&&
          start.height>=52&&create.height>=44&&freestyle.height>=44,
          'R91 primary session and supporting discovery have readable, reachable targets: '+
            JSON.stringify({start,create,freestyle}));
        assert.notEqual(start.background,create.background,
          'Starting training retains stronger emphasis than program creation');
        assert.ok(start.hit&&create.hit&&freestyle.hit,
          'The next workout and deferred creation/freestyle actions remain reachable');
      }
      assert.equal(errors.length,0,'Foundation states have no runtime errors: '+errors.join('; '));
    }finally{await context.close();}
  }
  console.log('PASS R61: stale startup and warm theme changes; actual Plan label/action contrast; accurate Profile missing-weight, missing-lifts and scored states; full Home hit targets.');
}
// R62: actionable order must agree with reading/keyboard order. Resize the
// same mounted screens rather than loading independent screenshots at each size.
async function verifyTaskHierarchy(browser){
  const widths=[320,393,599,600,601,768,839,840,841,1024,1280,1366,1600,1920];
  for(const theme of ['rose','rosewater']){
    const context=await browser.createBrowserContext();
    const page=await context.newPage();
    const seed=initial(theme);
    seed.saved=[{...program,days:program.days.map((day,index)=>index?day:{...day,
      exercises:[...day.exercises,'lat-pulldown','leg-press']})}];
    const errors=[];page.on('pageerror',e=>errors.push(e.message));
    try{
      const resize=async(width,height)=>{
        await page.setViewport({width,height,deviceScaleFactor:1});
        await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
      };
      await page.setViewport({width:393,height:852,deviceScaleFactor:1});
      await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
      await page.evaluateOnNewDocument(value=>localStorage.setItem('wpb:v1',JSON.stringify(value)),seed);
      await page.goto('http://127.0.0.1:8795/',{waitUntil:'networkidle0'});
      await page.waitForSelector('.wpb-home-session-summary');
      const homeOrder=await page.evaluate(()=>{
        const el=s=>document.querySelector(s),before=(a,b)=>!!(el(a).compareDocumentPosition(el(b))&Node.DOCUMENT_POSITION_FOLLOWING);
        const start=el('.wpb-home-hero>.wpb-primary-action').getBoundingClientRect();
        const dock=el('.wpb-home-compose-bar');
        return {summaryFirst:before('.wpb-home-session-summary','.wpb-home-hero>.wpb-primary-action'),
          startFirst:before('.wpb-home-hero>.wpb-primary-action','.wpb-home-session-preview'),
          programsFirst:before('.wpb-home-programs','.wpb-action-grid'),
          dockPresent:!!dock,createDeferred:before('.wpb-home-programs','.wpb-home-new-program'),
          startHeight:start.height};
      });
      assert.ok(homeOrder.summaryFirst&&homeOrder.startFirst&&homeOrder.programsFirst&&
        homeOrder.createDeferred&&!homeOrder.dockPresent&&homeOrder.startHeight>=52,
        'R91 workout leads; program creation follows training content in DOM and focus order: '+JSON.stringify(homeOrder));
      const outlineState=()=>page.$eval('.wpb-home-session-preview',panel=>{
        const button=panel.querySelector('button[aria-expanded]');
        const content=button?.getAttribute('aria-controls') ? document.getElementById(button.getAttribute('aria-controls')) : null;
        return {button:!!button,label:button?.getAttribute('aria-label'),controls:button?.getAttribute('aria-controls'),
          expanded:button?.getAttribute('aria-expanded'),buttonHeight:button?.getBoundingClientRect().height,
          rows:content?.querySelectorAll('.wpb-m3-outline-row').length||0,hidden:content?.hidden,
          text:content?.textContent||'',overflow:panel.scrollWidth>panel.clientWidth+1};
      });
      const outlineOpen=await outlineState();
      assert.ok(outlineOpen.button&&outlineOpen.expanded==='true'&&
        !!outlineOpen.controls&&
        outlineOpen.label?.includes('Hide all 4 session exercises')&&
        outlineOpen.buttonHeight>=44&&!outlineOpen.hidden&&outlineOpen.rows===4&&!outlineOpen.overflow,
        'B876 Home shows every session exercise by default, with accessible optional collapse: '+JSON.stringify(outlineOpen));
      assert.match(outlineOpen.text,/Bench/i,'The session shows a real lift, not only the split label');
      assert.match(outlineOpen.text,/4×6[-–]8/,'The actual prescribed sets and reps are visible before tapping');
      await page.click('.wpb-home-session-preview button[aria-expanded="true"]');
      const outlineClosed=await outlineState();
      assert.ok(outlineClosed.expanded==='false'&&outlineClosed.hidden&&outlineClosed.rows===0&&
        outlineClosed.label?.includes('Show all 4 session exercises'),
        'Explicit collapse still works without navigation or hidden focusable exercise content');
      await page.click('.wpb-home-session-preview button[aria-expanded="false"]');
      assert.deepEqual(await outlineState(),outlineOpen,'Reopening restores the same actual session outline');
      const geometry=async selector=>page.$eval(selector,node=>{
        const box=node.getBoundingClientRect(),css=getComputedStyle(node);
        const nav=document.querySelector('.wpb-tabbar'),tab=nav.querySelector('[aria-current="page"]');
        const group=nav.getBoundingClientRect(),item=tab.getBoundingClientRect(),paint=getComputedStyle(nav,'::before');
        const [x,y]=paint.translate.split(' ').map(parseFloat);
        const cx=group.left+x+parseFloat(paint.width)/2,top=group.top+y;
        const dock=document.querySelector('.wpb-home-compose-bar')?.getBoundingClientRect();
        return {width:box.width,left:box.left,right:box.right,padding:parseFloat(css.paddingLeft),
          viewport:innerWidth,overflow:document.querySelector('.wpb').scrollWidth>innerWidth+1,
          navTrackAligned:cx>=item.left&&cx<=item.right&&top>=group.top&&top+parseFloat(paint.height)<=group.bottom,
          footerClear:!dock||box.bottom<=dock.top+1};
      });
      for(const width of widths){
        await resize(width,width===768?1024:800);
        const home=await geometry('.wpb-home>.wpb-page-scroll');
        assert.ok(home.width<=(width>=840?940.1:720.1)&&home.left>=-.1&&home.right<=home.viewport+.1&&!home.overflow,
          'Home stays bounded through continuous resizing: '+JSON.stringify({width,home}));
        assert.equal(home.padding,width<360?12:width<600?16:24,'Home window inset follows available width');
        assert.ok(home.navTrackAligned,'Selected navigation indicator stays with its tab after resize: '+JSON.stringify({width,home}));
        assert.ok(home.footerClear,'Home scroll viewport ends above the persistent actions: '+JSON.stringify({width,home}));
        assert.equal(await page.$eval('.wpb-home-session-preview button',b=>b.getAttribute('aria-expanded')),'true',
          'Resize preserves expanded session outline');
        if([768,1366].includes(width))await page.screenshot({path:path.join(root,`verification/r62-home-${theme}-${width}.png`)});
      }
      await page.click('[data-tab="plan"]');await page.waitForSelector('.wpb-plan-session-link');
      const planOrder=await page.evaluate(()=>{
        const el=s=>document.querySelector(s),before=(a,b)=>!!(el(a).compareDocumentPosition(el(b))&Node.DOCUMENT_POSITION_FOLLOWING);
        return {nextFirst:before('.wpb-plan-up-next','.wpb-plan-panes'),
          pickerFirst:before('.wpb-plan-week-picker','.wpb-plan-overview'),
          selectedAfterPicker:before('.wpb-plan-week-picker','.wpb-plan-selected-week'),
          selectedBeforeTimeline:before('.wpb-plan-selected-week','.wpb-plan-phase-group'),
          selectedCount:document.querySelectorAll('.wpb-plan-selected-week .wpb-plan-week-card').length,
          uniqueWeekCount:new Set([...document.querySelectorAll('[data-plan-week]')].map(x=>x.dataset.planWeek)).size,
          sessionsFirst:before('.wpb-plan-session-list','.wpb-plan-overview'),
          rows:[...document.querySelectorAll('.wpb-plan-session-list button')].map(b=>({
            height:b.getBoundingClientRect().height,radius:parseFloat(getComputedStyle(b).borderTopLeftRadius),
            gap:parseFloat(getComputedStyle(b).marginTop)}))};
      });
      const planChrome=await page.$eval('.wpb-plan-context',context=>{
         const header=context.querySelector('.wpb-plan-context-heading');
         const action=context.querySelector('.wpb-plan-open-program');
         const title=context.querySelector('.wpb-plan-title');
         const picker=document.querySelector('.wpb-plan-week-picker');
         const r=action?.getBoundingClientRect(),h=header?.getBoundingClientRect(),p=getComputedStyle(picker);
         return {actionCount:document.querySelectorAll('.wpb-plan-open-program').length,
           named:action?.getAttribute('aria-label'),height:r?.height,top:r?.top,bottom:r?.bottom,headerTop:h?.top,headerBottom:h?.bottom,
           titleTop:title?.getBoundingClientRect().top,titleBottom:title?.getBoundingClientRect().bottom,
           titleWidth:title?.getBoundingClientRect().width,headerWidth:h?.width,marginBottom:p.marginBottom,
           sticky:p.position,bg:p.backgroundColor,hasBottomRule:p.boxShadow!=='none'};
       });
       assert.ok(planChrome.actionCount===1&&/full program/i.test(planChrome.named)&&
         planChrome.height>=48&&planChrome.top>=planChrome.headerTop-1&&
         planChrome.bottom<=planChrome.headerBottom+1&&
         (Math.abs((planChrome.top+planChrome.bottom)-(planChrome.titleTop+planChrome.titleBottom))<=4
           || planChrome.top>=planChrome.titleBottom+6&&Math.abs(planChrome.titleWidth-planChrome.headerWidth)<=1),
         'Full program action sits beside or below a full-width Plan name: '+JSON.stringify(planChrome));
       assert.ok(planChrome.sticky==='sticky'&&planChrome.marginBottom==='0px'&&
         planChrome.bg!=='rgba(0, 0, 0, 0)'&&planChrome.hasBottomRule,
         'Sticky week picker fills its lower edge with an opaque themed surface: '+JSON.stringify(planChrome));
       const choices=await page.evaluate(()=>[...document.querySelectorAll('.wpb-plan-panes>button')].map(b=>({
        pane:b.dataset.planPane,pressed:b.getAttribute('aria-pressed'),height:b.getBoundingClientRect().height
      })));
      assert.deepEqual(choices.map(b=>b.pane),['schedule','volume','progression','blueprint'],'Plan keeps four panes');
      assert.ok(choices.every(b=>b.height>=44)&&choices.filter(b=>b.pressed==='true').length===1,'One active 44px+ pane');
      await page.click('[data-plan-pane="volume"]');
      assert.equal(await page.$eval('[data-plan-pane="volume"]',b=>b.getAttribute('aria-pressed')),'true');
      // M380: in a planned week the contributors' heading must be a native
      // keyboard-accessible button, not a pointer-only clickable div.
      await page.waitForSelector('.wpb-plan button[data-vol-part]');
      const rowBefore=await page.$eval('.wpb-plan button[data-vol-part]',el=>({
        part:el.dataset.volPart,expanded:el.getAttribute('aria-expanded'),
        named:el.getAttribute('aria-label'),style:getComputedStyle(el).fontSize
      }));
      assert.equal(rowBefore.expanded,'false');
      assert.ok(rowBefore.named?.includes('Volume contributors for')&&parseFloat(rowBefore.style)>=12,
        'M380: muscle-volume header has readable accessible name');
      await page.focus('.wpb-plan button[data-vol-part]');
      await page.keyboard.press('Enter');
      assert.equal(await page.$eval('.wpb-plan button[data-vol-part]',el=>el.getAttribute('aria-expanded')),
        'true','M380: Enter opens muscle-volume contributors without changing training');
      const contributors=await page.evaluate(()=>[...document.querySelectorAll('.wpb-plan [data-volume-contributor]')].map(row=>{
        const name=row.children[1],style=getComputedStyle(name);
        return {name:name.textContent.trim(),whiteSpace:style.whiteSpace,textOverflow:style.textOverflow,
          clipped:name.scrollWidth>name.clientWidth+1};
      }));
      assert.ok(contributors.length>0&&contributors.every(row=>row.name&&row.whiteSpace==='normal'&&
        row.textOverflow!=='ellipsis'&&!row.clipped),
        'M381: expanded muscle credits expose complete wrapping names: '+JSON.stringify(contributors));
      await page.keyboard.press('Space');
      assert.equal(await page.$eval('.wpb-plan button[data-vol-part]',el=>el.getAttribute('aria-expanded')),
        'false','M380: Space closes native volume contributors');
      // M379: this is the actual Plan blueprint renderer, not a duplicated
      // graph fixture. Its planned sets use canonical 1-based weeks and the
      // optional deload is exactly one final additional week.
      await page.click('[data-plan-pane="blueprint"]');
      await page.waitForSelector('[data-load-wave] [data-wave-week]');
      const loadWave = await page.$eval('[data-load-wave]', el =>
        [...el.querySelectorAll('[data-wave-week]')].map(row=>({
          week:Number(row.dataset.waveWeek),sets:Number(row.dataset.waveSets),
          deload:row.dataset.waveDeload==='1'
        })));
      assert.ok(loadWave.length>=4 &&
        loadWave.every((point,i)=>point.week===i+1&&Number.isFinite(point.sets)&&point.sets>=0) &&
        loadWave.filter(point=>point.deload).length<=1 &&
        loadWave.slice(0,-1).every(point=>!point.deload),
        'M379 uses the canonical consecutive planned weeks and one optional final deload: '+JSON.stringify(loadWave));
      assert.deepEqual(loadWave.map(point=>point.sets),loadWave.map(point=>plannedWeek(seed.saved[0],point.week).sets),
        'Load-wave values equal canonical saved-week totals');
      for(const width of [320,393,900,1280])for(const textSize of [16,32]){
        await resize(width,800);
        await page.evaluate(size=>document.documentElement.style.fontSize=size+'px',textSize);
        const wave=await page.$eval('[data-load-wave]',el=>({
          scrollable:el.scrollWidth>el.clientWidth,overflow:getComputedStyle(el).overflowX,
          tabIndex:el.tabIndex,named:el.getAttribute('aria-label'),
          rows:[...el.children].map(row=>({
            named:row.getAttribute('aria-label'),font:parseFloat(getComputedStyle(row.lastElementChild).fontSize),
            clipped:[row.firstElementChild,row.lastElementChild].some(n=>n.scrollWidth>n.clientWidth+1)
          })),pageOverflow:document.documentElement.scrollWidth>innerWidth+1
        }));
        assert.ok(!wave.pageOverflow&&wave.named&&wave.tabIndex===0&&wave.rows.every(row=>row.named&&
          !row.clipped&&row.font>=textSize*.8125-.1),'Wave labels scale and stay readable in their scroll region: '+JSON.stringify({width,textSize,wave}));
        if(width===320&&textSize===32){
          assert.ok(wave.scrollable&&wave.overflow==='auto','Long enlarged wave scrolls within the chart');
          await page.focus('[data-load-wave]');await page.keyboard.press('ArrowRight');
          await page.waitForFunction(()=>{const el=document.querySelector('[data-load-wave]');return el.scrollLeft>0;});
          await page.screenshot({path:path.join(root,`verification/r53-wave-${theme}-${textSize}-phone.png`)});
        }
      }
      await page.evaluate(()=>document.documentElement.style.fontSize='16px');
      await resize(393,800);
      await page.click('[data-plan-pane="schedule"]');
      assert.equal(await page.$eval('[data-plan-pane="schedule"]',b=>b.getAttribute('aria-pressed')),'true');
      assert.ok(planOrder.nextFirst&&planOrder.pickerFirst&&planOrder.sessionsFirst&&
        planOrder.selectedAfterPicker&&planOrder.selectedBeforeTimeline&&planOrder.selectedCount===1&&planOrder.uniqueWeekCount===4,
        'Plan puts next session, location and sessions before aggregate metrics: '+JSON.stringify(planOrder));
      assert.ok(planOrder.rows.length===2&&planOrder.rows.every(r=>r.height>=48&&r.radius===0&&r.gap===0),
        'Connected rows retain individual full targets');
      await page.click('[data-plan-week-picker] [aria-label="Week 3"]');
      assert.equal(await page.$eval('.wpb-plan-selected-week [data-plan-week]',e=>e.dataset.planWeek),'3',
        'Week picker moves the existing session card to the featured position, without duplication');
      for(const width of [...widths,393]){
        await resize(width,width===768?1024:800);
        const plan=await geometry('.wpb-plan-view');
        assert.ok(plan.width<=(width>=840?width:720.1)&&plan.left>=-.1&&plan.right<=plan.viewport+.1&&!plan.overflow,
          'Plan stays bounded through continuous resizing: '+JSON.stringify({width,plan}));
        const expectedPlanPadding=width>=840?Math.min(36,Math.max(20,width*.03))
          :width<360?12:width<600?16:24;
        assert.ok(Math.abs(plan.padding-expectedPlanPadding)<.6,
          'Plan window inset follows compact or responsive wide padding: '+JSON.stringify({width,padding:plan.padding,expectedPlanPadding}));
        assert.ok(plan.navTrackAligned,'Plan navigation indicator stays inside its bar after resize: '+JSON.stringify({width,plan}));
        assert.equal(await page.$eval('.wpb-plan-week-chip[aria-pressed="true"]',b=>b.getAttribute('aria-label')),'Week 3',
          'Resize preserves the deliberately selected week');
        if([768,1366].includes(width))await page.screenshot({path:path.join(root,`verification/r62-plan-${theme}-${width}.png`)});
      }
      await resize(900,450);
      assert.ok(!(await geometry('.wpb-plan-view')).overflow,'Short window retains a usable single scroll pane');
      await resize(320,800);
      await page.evaluate(()=>document.documentElement.style.fontSize='32px');
      assert.ok(!(await geometry('.wpb-plan-view')).overflow,'Plan reflows at 320px with enlarged text');
      await page.keyboard.press('Tab');
      const focused=await page.$eval('.wpb-plan-week-chip[aria-pressed="true"]',b=>{
        b.focus();const css=getComputedStyle(b),box=b.getBoundingClientRect();
        return {width:box.width,height:box.height,outline:css.outlineStyle};
      });
      assert.ok(focused.width>=48&&focused.height>=48&&focused.outline!=='none',
        'Week selection retains keyboard focus and target: '+JSON.stringify(focused));
      assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')).saved),seed.saved,
        'Hierarchy and resizing never modify saved training prescriptions');
      assert.equal(errors.length,0,'Hierarchy/resize flow has no runtime errors: '+errors.join('; '));
    }finally{await context.close();}
  }
  console.log('PASS R62: Home/Plan action and reading order; connected full-size rows; window-bounded adaptive tablet/laptop widths; outline/week retention during resize; short window and enlarged text.');
}
// Stress the real shared volume display with more than eight contributions,
// long session names and enlarged text. This deliberately repetitive custom
// fixture tests presentation; it is not a recommended training program.
async function verifyVolumeDetails(browser){
  for(const theme of ['rose','rosewater']){
    const context=await browser.createBrowserContext(),page=await context.newPage();
    const seed=initial(theme);
    seed.saved=[{...program,config:{...program.config,days:3,deload:true},overrides:{},
      days:Array.from({length:3},(_,i)=>({id:'volume-'+i,
        label:'Upper body session with a long descriptive name '+(i+1),primaryIndex:i,
        exercises:['bb-bench','inc-db-press','cable-fly','smith-bench']}))}];
    const errors=[];page.on('pageerror',e=>errors.push(e.message));
    try{
      await page.setViewport({width:320,height:852,deviceScaleFactor:1});
      await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
      await page.evaluateOnNewDocument(value=>{
        if(!localStorage.getItem('wpb:v1'))localStorage.setItem('wpb:v1',JSON.stringify(value));
      },seed);
      await page.goto('http://127.0.0.1:8795/',{waitUntil:'networkidle0'});
      await page.waitForSelector('.wpb-tabbar');
      await page.click('[data-tab="plan"]');
      await page.click('[data-plan-pane="volume"]');
      const trigger='.wpb-plan button[data-vol-part="chest"]';
      await page.waitForSelector(trigger);
      await page.focus(trigger);await page.keyboard.press('Enter');
      const expected=await page.evaluate(async()=>{
        const {weekMuscleBreakdown}=await import('./modules/engine-api.js');
        return weekMuscleBreakdown(JSON.parse(localStorage.getItem('wpb:v1')).saved[0],1)
          .find(entry=>entry.part==='chest').from;
      });
      assert.ok(expected.length>8,'Fixture must exercise the former eight-row cutoff');
      for(const width of [320,393,900,1280])for(const text of [16,32]){
        await page.setViewport({width,height:852,deviceScaleFactor:1});
        await page.evaluate(size=>document.documentElement.style.fontSize=size+'px',text);
        const details=await page.evaluate(sel=>{
          const button=document.querySelector(sel),box=button.getBoundingClientRect();
          const rows=[...document.querySelectorAll('.wpb-plan [data-volume-contributor]')];
          return {font:parseFloat(getComputedStyle(button).fontSize),height:box.height,
            rows:rows.map(row=>({day:row.children[0].textContent,name:row.children[1].textContent,
              clipped:[row,...row.children].some(el=>el.scrollWidth>el.clientWidth+1)}))};
        },trigger);
        assert.ok(details.height>=44&&details.font>=text*.8125-.1,
          'Volume controls scale with text and retain touch targets: '+JSON.stringify({theme,width,text,details}));
        assert.equal(details.rows.length,expected.length,'Every canonical contribution remains visible');
        assert.ok(details.rows.every((row,i)=>!row.clipped&&row.name.includes(expected[i].name)&&
          row.day===expected[i].dayLabel),'Complete contributor text reflows: '+JSON.stringify({theme,width,text,details}));
        if(width===320){
          await page.$eval('.wpb-plan [data-volume-contributor]',el=>el.scrollIntoView({block:'center'}));
          await page.screenshot({path:path.join(root,`verification/r53-volume-${theme}-${text}-phone.png`)});
        }
      }
      await page.focus(trigger);await page.keyboard.press('Space');
      assert.equal(await page.$eval(trigger,el=>el.getAttribute('aria-expanded')),'false');
      const toggle='.wpb-plan .wpb-volume-detail-toggle';
      await page.focus(toggle);await page.keyboard.press('Enter');
      assert.ok(await page.$eval(toggle,el=>el.getAttribute('aria-expanded')==='true'&&
        el.getBoundingClientRect().height>=44),'Granular detail has a full native keyboard/touch control');
      assert.ok((await page.$$('.wpb-plan .wpb-volume-sub')).length>0,'Granular volume remains visible');
      await page.keyboard.press('Space');
      const rampExpected=await page.evaluate(async()=>{
        const {weeklyVolume,weeksOf,PART_ORDER,PART_LABEL,fmtSets}=await import('./modules/engine-api.js');
        const p=JSON.parse(localStorage.getItem('wpb:v1')).saved[0];
        const volumes=Array.from({length:weeksOf(p)+1},(_,i)=>weeklyVolume(p,i+1));
        return PART_ORDER.filter(part=>volumes.some(v=>(v[part]||0)>.05)).map(part=>({
          label:PART_LABEL[part],sets:volumes.map(v=>fmtSets(v[part]||0))}));
      });
      const rampActual=await page.$$eval('.wpb-plan .wpb-volume-ramp-row',rows=>rows.map(row=>({
        label:row.firstElementChild.textContent,sets:[...row.querySelectorAll('.wpb-volume-ramp-dose')].map(el=>el.textContent)})));
      assert.deepEqual(rampActual,rampExpected,'Block table preserves all canonical weeks, muscle credits and final deload');
      await page.setViewport({width:320,height:852,deviceScaleFactor:1});
      const scroll='.wpb-plan .wpb-volume-ramp-scroll';
      await page.focus(scroll);await page.keyboard.press('ArrowRight');
      await page.waitForFunction(sel=>document.querySelector(sel).scrollLeft>0,{timeout:3000},scroll);
      assert.ok(await page.$eval(scroll,el=>[...el.querySelectorAll('.wpb-volume-ramp-label')]
        .every(label=>label.getBoundingClientRect().left>=el.getBoundingClientRect().left-.5)),
        'Muscle labels stay visible while keyboard-scrolling later weeks');
      await page.screenshot({path:path.join(root,`verification/r53-volume-ramp-${theme}-phone.png`)});
      const client=await page.createCDPSession();
      await client.send('Emulation.setEmulatedMedia',{features:[
        {name:'forced-colors',value:'active'},{name:'prefers-reduced-motion',value:'reduce'}]});
      await page.focus(trigger);
      assert.ok(await page.$eval(trigger,el=>getComputedStyle(el).outlineStyle!=='none'&&
        getComputedStyle(el.querySelector('svg')).transitionDuration==='0s'),
        'Forced colors retain focus and reduced motion removes chevron interpolation');
      await client.detach();
      assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')).saved),seed.saved,
        'Reading volume details never changes the saved prescriptions');
      assert.deepEqual(errors,[]);
    }finally{await context.close();}
  }
  console.log('PASS R53 volume details: full canonical contributor counts, long names, native keyboard controls and 320/393/900/1280px at 100/200% text in both themes.');
}
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html','.json':'application/json','.png':'image/png','.svg':'image/svg+xml','.webmanifest':'application/manifest+json'};
const server=http.createServer((req,res)=>{
  const name=new URL(req.url,'http://localhost').pathname;
  const file=path.resolve(root,'.'+(name==='/'?'/index.html':name));
  if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){res.writeHead(404);res.end();return;}
  res.setHeader('Content-Type',mime[path.extname(file)]||'application/octet-stream');
  res.end(fs.readFileSync(file));
});
await new Promise(resolve=>server.listen(8795,'127.0.0.1',resolve));
let browser;
try{
  browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
  const captures={rose:{},rosewater:{}},measurements={};
  for(const theme of ['rose','rosewater']){
    const context=await browser.createBrowserContext();
    const page=await context.newPage();
    const errors=[];
    page.on('pageerror',e=>errors.push(e.message));
    await page.setViewport({width:393,height:852,deviceScaleFactor:1,isMobile:true,hasTouch:true});
    await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
    await page.evaluateOnNewDocument(store=>{
      localStorage.setItem('wpb:v1',JSON.stringify(store));
    },initial(theme));
    await page.goto('http://127.0.0.1:8795/',{waitUntil:'networkidle0'});
    await page.waitForSelector('.wpb-tabbar');
    const surfaces={};
    for(const tab of ['home','plan','progress','profile','settings']){
      await page.click('[data-tab="'+tab+'"]');
      await page.waitForSelector('[data-view-frame="'+tab+'"][data-pi-surface]');
      await page.waitForFunction(t=>document.querySelector('[data-view-frame="'+t+'"]')?.style.getPropertyValue('--pi-surface-opacity')==='1',{},tab);
      const basic=await page.evaluate(t=>{
        const root=document.querySelector('.wpb');
        const nav=root.querySelector('.wpb-tabbar');
        return {theme:JSON.parse(localStorage.getItem('wpb:v1')).theme,
          current:nav.querySelector('[aria-current="page"]')?.dataset.tab,
          overflow:root.scrollWidth>root.clientWidth+1,
          nav:getComputedStyle(nav).backgroundColor,
          page:getComputedStyle(document.querySelector(t==='plan'?'.wpb-plan-view':'.wpb-'+t)).backgroundColor};
      },tab);
      assert.equal(basic.theme,theme,'Selected saved theme persists across destinations');
      assert.equal(basic.current,tab,'Correct bottom navigation destination remains selected');
      assert.ok(!basic.overflow,'No horizontal scrolling at 393px: '+JSON.stringify(basic));
      if(tab==='plan'){
        Object.assign(basic,await page.evaluate(()=>{
          const el=sel=>document.querySelector(sel);
          const css=(sel)=>{const x=el(sel);return x?getComputedStyle(x).backgroundColor:null};
          const chip=el('.wpb-plan-week-chip[aria-pressed="true"]');
          const all=[...document.querySelectorAll('.wpb-plan-week-card')];
          return {
            weekCard:css('.wpb-plan-week-card'),
            weekImage:el('.wpb-plan-week-card')&&getComputedStyle(el('.wpb-plan-week-card')).backgroundImage,
            nested:css('.wpb-plan-session-link'),weekCount:all.length,
            currentCount:all.filter(x=>x.dataset.currentWeek==='true').length,
            chip:chip?.textContent.trim()||null,
            chipSelected:chip?.getAttribute('aria-pressed')==='true'
          };
        }));
        assert.ok(basic.weekCount>=2&&basic.currentCount===1&&basic.weekImage==='none',
          'Plan has real semantic week cards with exactly one current week: '+JSON.stringify(basic));
        assert.ok(basic.nested&&basic.chipSelected,'Plan nested row and active week are accessible: '+JSON.stringify(basic));
      }
      // R57: the other four destinations have distinct content structures,
      // but share density, ≥48px secondary controls, and no clipping.
      if(tab!=='home'){
        const measure=()=>page.evaluate(which=>{
          const el=s=>document.querySelector(s);
          const box=s=>{const n=el(s);if(!n)return null;const r=n.getBoundingClientRect();
            return {top:r.top,bottom:r.bottom,height:r.height,width:r.width,left:r.left,right:r.right};};
          const root=el('.wpb');
          const out={overflow:root.scrollWidth>root.clientWidth+1,viewport:innerWidth};
          if(which==='plan'){
            out.row=box('.wpb-plan .wpb-root-title-row');
            out.back=box('.wpb-plan .wpb-root-header button[aria-label="Back"]');
            out.title=box('.wpb-plan .wpb-root-header h1');
            out.titleSize=parseFloat(getComputedStyle(el('.wpb-plan .wpb-root-header h1')).fontSize);
          }else if(which==='progress'){
            out.header=box('.wpb-progress>.wpb-progress-chrome');
            out.titleSize=parseFloat(getComputedStyle(el('.wpb-progress-title')).fontSize);
            out.tabs=[...document.querySelectorAll('.wpb-progress .wpb-premium-tabs>button')]
              .map(b=>{const r=b.getBoundingClientRect();return {width:r.width,height:r.height}});
          }else if(which==='settings'){
            out.header=box('.wpb-settings>.wpb-root-header');
            out.jump=box('.wpb-settings>.wpb-settings-jump');
            out.picker=box('.wpb-settings-jump-control');
            out.titleSize=parseFloat(getComputedStyle(el('.wpb-settings>.wpb-root-header h1')).fontSize);
            out.select=box('.wpb-settings-jump select');
          }else if(which==='profile'){
            out.topPadding=parseFloat(getComputedStyle(el('.wpb-profile-inner')).paddingTop);
            out.head=box('.wpb-profile-head');out.identity=box('.wpb-profile-card-caption');
            out.titleSize=parseFloat(getComputedStyle(el('.wpb-profile-title')).fontSize);
            out.edit=box('.wpb-profile-edit');
            out.shareCard=box('.wpb-profile-share-card');
            out.layoutActions=box('.wpb-profile-toolbar');
            out.hero=box('.wpb-profile-hero');
            out.stats=[...document.querySelectorAll('.wpb-profile-stat')].map(b=>b.getBoundingClientRect().width);
          }
          return out;
        },tab);
        const density=await measure();
        assert.ok(!density.overflow,'R57 destination must not overflow: '+JSON.stringify({tab,density}));
        // M270 gives every root the same 26px title role; retain each
        // destination's compact geometry and full-size action checks below.
        assert.equal(density.titleSize,26,'Root destination uses the shared title size: '+JSON.stringify({tab,density}));
        if(tab==='plan'){
          assert.ok(density.row&&!density.back&&density.title&&density.title.top>=density.row.top,
            'Plan root has no Back action and keeps its title in the shared row: '+JSON.stringify(density));
          assert.ok(density.title.bottom-density.row.top<=118,
            'Plan header has compact hierarchy without hiding title: '+JSON.stringify(density));
        }else if(tab==='progress'){
          assert.ok(density.header&&density.header.height<=155&&density.tabs.length===5&&density.tabs.every(x=>x.height>=48),
            'Progress title and five 48px tabs fit its header: '+JSON.stringify(density));
        }else if(tab==='settings'){
          assert.ok(density.header&&density.jump&&density.picker&&density.picker.height>=48&&density.select&&density.select.height>=48
            &&density.jump.bottom-density.header.top<=128,
            'Settings title and Jump control use a compact, accessible header: '+JSON.stringify(density));
        }else if(tab==='profile'){
          assert.ok(density.head&&density.edit&&density.hero&&density.topPadding<=16
            &&density.edit.width>=48&&density.edit.height>=48,
            'Profile identity/edit start promptly with full-size edit action: '+JSON.stringify(density));
          assert.ok(density.shareCard&&density.layoutActions&&density.shareCard.top>=density.head.bottom
            &&density.shareCard.top-density.head.bottom<=16
            &&density.layoutActions.top>=density.shareCard.bottom
            &&density.hero.top>=density.identity.bottom&&density.hero.bottom<=density.shareCard.bottom
            &&density.stats.length===3&&density.stats.every(w=>w>=68),
            'Share card integrates identity and XP before customizable sections, with compact gaps and no overlaps: '+JSON.stringify(density));
        }
        basic.density=density;
        if(tab==='plan'||tab==='profile'||tab==='settings'){
          await page.setViewport({width:320,height:700,deviceScaleFactor:1,isMobile:true,hasTouch:true});
          const small=await measure();
          assert.ok(!small.overflow,'320dp '+tab+' must not overflow: '+JSON.stringify(small));
          if(tab==='plan')assert.ok(!small.back&&small.title.bottom-small.row.top<=136,
            'Plan still fits at 320dp: '+JSON.stringify(small));
          if(tab==='profile')assert.ok(small.edit.height>=48&&small.shareCard.width<=320
            &&small.shareCard.top>=small.head.bottom&&small.stats.every(w=>w>=64),
            'Profile edit/stats remain usable at 320dp: '+JSON.stringify(small));
          if(tab==='settings')assert.ok(small.select.height>=48&&small.jump.bottom-small.header.top<=136,
            'Settings Jump remains usable at 320dp: '+JSON.stringify(small));
          basic.smallDensity=small;
          await page.setViewport({width:393,height:852,deviceScaleFactor:1,isMobile:true,hasTouch:true});
        }
      }
      if(tab==='settings'){
        // R58: whole preference labels must fit at narrow widths and larger
        // Android reading sizes; exercise the real selection and persistence.
        const responsive=[];
        for(const width of [393,320])for(const font of [16,20]){
          await page.setViewport({width,height:852,deviceScaleFactor:1,isMobile:true,hasTouch:true});
          await page.evaluate(px=>document.documentElement.style.fontSize=px+'px',font);
          await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
          const geometry=await page.evaluate(()=>{
            const group=document.querySelector('.wpb-experience-segmented');
            const controls=[...group.querySelectorAll('button')].map(b=>{
              const range=document.createRange();range.selectNodeContents(b);
              const rect=b.getBoundingClientRect();
              const fragments=[...range.getClientRects()].filter(r=>r.width>1&&r.height>1);
              return {label:b.textContent.trim(),width:b.offsetWidth,height:b.offsetHeight,
                fragments:fragments.length,font:parseFloat(getComputedStyle(b).fontSize),
                clipped:fragments.some(r=>r.left<rect.left-1||r.right>rect.right+1
                  ||r.top<rect.top-1||r.bottom>rect.bottom+1)};
            });
            return {controls,columns:getComputedStyle(group).gridTemplateColumns.split(' ').length,
              overflow:document.querySelector('.wpb').scrollWidth>innerWidth+1};
          });
          assert.ok(!geometry.overflow&&geometry.controls.length===4&&geometry.controls.every(b=>
            b.width>=48&&b.height>=48&&!b.clipped&&b.fragments===1&&b.font>=font*.875-.1),
            'Whole Settings experience labels and 48px targets fit '+JSON.stringify({width,font,geometry}));
          assert.equal(geometry.columns,width===393&&font===16?4:width===320&&font===20?1:2,
            'Experience controls make room for text instead of splitting words');
          await page.click('.wpb-experience-segmented>button:nth-child(4)');
          await page.waitForFunction(()=>JSON.parse(localStorage.getItem('wpb:v1')).experience==='advanced');
          assert.equal(await page.$eval('.wpb-experience-segmented>button:nth-child(4)',b=>b.getAttribute('aria-pressed')),'true');
          await page.click('.wpb-experience-segmented>button:nth-child(3)');
          await page.waitForFunction(()=>JSON.parse(localStorage.getItem('wpb:v1')).experience==='intermediate');
          responsive.push({width,font,columns:geometry.columns});
        }
        basic.experienceResponsive=responsive;
        assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')).saved),initial(theme).saved,
          'Preference layout and selection preserve saved program data');
        await page.evaluate(()=>{document.documentElement.style.fontSize='';document.querySelector('.wpb-settings>.wpb-page-scroll').scrollTop=0;});
        await page.setViewport({width:393,height:852,deviceScaleFactor:1,isMobile:true,hasTouch:true});
      }
      captures[theme][tab]=Buffer.from(await page.screenshot({type:'png'}));
      surfaces[tab]=basic;
      if(tab==='home'){
        // R56: before the patch, scrolling hid the greeting but retained a
        // 70dp brand slab. Check real masthead height, touch areas, scroll-back
        // restoration and narrow-phone geometry in both saved palettes.
        const measure=()=>page.evaluate(()=>{
          const home=document.querySelector('.wpb-home');
          const head=home.querySelector('.wpb-home-header');
          const brand=home.querySelector('.wpb-root-title-row h1');
          const tools=home.querySelector('.wpb-home-tools');
          const sc=home.querySelector('.wpb-page-scroll');
          const rect=x=>{const r=x.getBoundingClientRect();return {x:r.x,y:r.y,w:r.width,h:r.height,right:r.right};};
          const buttons=[...tools.querySelectorAll('button')];
          return {compact:home.querySelector('.wpb-home-context').getBoundingClientRect().bottom<=sc.getBoundingClientRect().top+1?'1':'0',
            header:rect(head),brand:rect(brand),tools:rect(tools),
            buttons:buttons.map(b=>({label:b.getAttribute('aria-label'),...rect(b)})),
            long:getComputedStyle(home.querySelector('.wpb-home-brand-long')).display,
            short:home.querySelector('.wpb-home-brand-short') && getComputedStyle(home.querySelector('.wpb-home-brand-short')).display,
            maxScroll:sc.scrollHeight-sc.clientHeight,
            overflow:home.scrollWidth>home.clientWidth+1,
            scrollTop:sc.scrollTop};
        });
        const expanded=await measure();
        assert.ok(expanded.maxScroll>180,'Seeded Home can actually scroll: '+JSON.stringify(expanded));
        assert.notEqual(expanded.long,'none','Full brand shown before scrolling');
        await page.$eval('.wpb-home .wpb-page-scroll',sc=>{sc.scrollTop=sc.scrollHeight;});
        await page.waitForFunction(()=>(()=>{const home=document.querySelector('.wpb-home');return home&&home.querySelector('.wpb-home-context').getBoundingClientRect().bottom<=home.querySelector('.wpb-page-scroll').getBoundingClientRect().top+1;})());
        const compact=await measure();
        assert.ok(compact.header.h<=70&&Math.abs(expanded.header.h-compact.header.h)<=1,
          'Scrolled context leaves a stable compact app bar without reserved blank space: '+JSON.stringify({expanded,compact}));
        assert.notEqual(compact.long,'none','Full Pursuit Iron identity remains in DOM while its context collapses');
        assert.equal(compact.short,null,'Home no longer substitutes the incorrect Iron-only label');
        assert.ok(compact.buttons.length===3&&compact.buttons.every(b=>b.w>=48&&b.h>=48),
          'All Home tools remain accessible 48px touch targets: '+JSON.stringify(compact));
        assert.ok(compact.brand.right+8<=compact.tools.x&&!compact.overflow,
          'Collapsed Home title/tools never overlap: '+JSON.stringify(compact));
        captures[theme].homeCompact=Buffer.from(await page.screenshot({type:'png'}));
        await page.setViewport({width:320,height:700,deviceScaleFactor:1,isMobile:true,hasTouch:true});
        await page.$eval('.wpb-home .wpb-page-scroll',sc=>{sc.scrollTop=sc.scrollHeight;});
        await page.waitForFunction(()=>(()=>{const home=document.querySelector('.wpb-home');return home&&home.querySelector('.wpb-home-context').getBoundingClientRect().bottom<=home.querySelector('.wpb-page-scroll').getBoundingClientRect().top+1;})());
        const small=await measure();
        assert.ok(small.header.h<=70&&small.brand.right+4<=small.tools.x&&!small.overflow,
          '320dp Home compact header has no collision or overflow: '+JSON.stringify(small));
        assert.ok(small.buttons.every(b=>b.w>=48&&b.h>=48),
          'Small-screen actions retain 48px targets: '+JSON.stringify(small));
        await page.setViewport({width:393,height:852,deviceScaleFactor:1,isMobile:true,hasTouch:true});
        await page.$eval('.wpb-home .wpb-page-scroll',sc=>{sc.scrollTop=0;});
        await page.waitForFunction(()=>(()=>{const home=document.querySelector('.wpb-home');return home&&home.querySelector('.wpb-home-context').getBoundingClientRect().bottom>home.querySelector('.wpb-page-scroll').getBoundingClientRect().top+1;})());
        const restored=await measure();
        assert.notEqual(restored.long,'none','Full masthead returns on scroll-to-top');
        await page.click('.wpb-home-tools button[aria-label="Exercise library"]');
        await page.waitForSelector('.wpb-library');
        await page.click('.wpb-library-header button[aria-label="Back"]');
        await page.waitForSelector('.wpb-home');
        surfaces[tab].header={expanded:expanded.header.h,compact:compact.header.h,
          compactButtons:compact.buttons.length,smallHeader:small.header.h};
      }
    }
    assert.equal(surfaces.plan.weekCard,await page.evaluate(()=>{
      const card=document.querySelector('.wpb-settings .wpb-settings-card');
      return card&&getComputedStyle(card).backgroundColor;
    }), 'Settings and Plan use the SAME neutral card role: '+JSON.stringify(surfaces.plan));
    await page.click('[data-tab="home"]');
    await page.waitForSelector('.hp-open');
    await page.click('.hp-open');
    await page.waitForSelector('.wpb-program .wpb-week-rail');
    const detail=await page.evaluate(()=>{
      const root=document.querySelector('.wpb-program');
      const week=[...root.querySelectorAll('.wpb-week-rail>button')];
      const phase=root.querySelector('.wpb-phase-block>div>div');
      return {
        overflow:root.scrollWidth>root.clientWidth+1,
        activeWeeks:week.filter(b=>b.getAttribute('aria-pressed')==='true').length,
        weekCount:week.length,
        phase:phase&&getComputedStyle(phase).backgroundColor,
        phaseImage:phase&&getComputedStyle(phase).backgroundImage
      };
    });
    assert.ok(!detail.overflow&&detail.weekCount>1&&detail.activeWeeks===1,
      'Full Program has one semantic selected week and no overflow: '+JSON.stringify(detail));
    // R54: inspect the expanded exercise view. R53 previously photographed
    // only the collapsed Program shell, hiding its legacy dark-purple rows.
    const dayPresent=await page.evaluate(()=>{
      const day=document.querySelector('.wpb-program .wpb-day-card');
      if(!day)return false;
      if(!day.querySelector('[data-exrow]'))day.querySelector('.wpb-day-toggle')?.click();
      return true;
    });
    assert.ok(dayPresent,'Full Program contains a real workout day');
    await page.waitForSelector('.wpb-program .wpb-program-exercise');
    const nested=await page.evaluate(()=>{
      const el=sel=>document.querySelector(sel);
      const css=sel=>getComputedStyle(el(sel));
      // Computed CSS color-mix() serializes to color(srgb 0..1), whereas
      // rgb() uses 0..255. Treating srgb channels as 8-bit values previously
      // fabricated contrast failures (and could fabricate false passes).
      const rgb=value=>{
        const v=String(value).trim();
        const srgb=v.match(/^color\(srgb\s+([\d.]+)\s+([\d.]+)\s+([\d.]+)/i);
        if(srgb)return srgb.slice(1,4).map(n=>Number(n)*255);
        const legacy=v.match(/^rgba?\(\s*([\d.]+)[,\s]+([\d.]+)[,\s]+([\d.]+)/i);
        if(legacy)return legacy.slice(1,4).map(Number);
        throw new Error('Unsupported computed contrast color: '+v);
      };
      const luminance=v=>v.map(n=>{n/=255;return n<=.04045?n/12.92:Math.pow((n+.055)/1.055,2.4)})
        .reduce((sum,n,i)=>sum+n*[.2126,.7152,.0722][i],0);
      const contrast=(a,b)=>{const x=luminance(rgb(a)),y=luminance(rgb(b));return (Math.max(x,y)+.05)/(Math.min(x,y)+.05)};
      const chips=[...document.querySelectorAll('.wpb-day-hit-chip')];
      const row=el('.wpb-program-exercise');
      return {
        card:css('.wpb-day-card').backgroundColor,
        row:css('.wpb-program-exercise').backgroundColor,
        nameContrast:contrast(css('.wpb-program-exercise-name').color,getComputedStyle(row).backgroundColor),
        metaContrast:contrast(css('.wpb-program-exercise-meta').color,getComputedStyle(row).backgroundColor),
        nameWrap:css('.wpb-program-exercise-name').whiteSpace,
        chips:chips.length,
        chipTargets:chips.map(chip=>({height:chip.getBoundingClientRect().height,width:chip.getBoundingClientRect().width})),
        chipContrast:chips.length?contrast(getComputedStyle(chips[0]).color,getComputedStyle(chips[0]).backgroundColor):0,
        overflow:el('.wpb-program').scrollWidth>el('.wpb-program').clientWidth+1
      };
    });
    assert.notEqual(nested.card,nested.row,
      'Program exercise card must use a distinct tonal elevation: '+JSON.stringify(nested));
    assert.ok(nested.chips>0&&nested.nameWrap==='normal',
      'Muscle chips remain visible and exercise names wrap: '+JSON.stringify(nested));
    assert.ok(nested.chipTargets.every(target=>target.width>=44&&target.height>=44),
      'B977 muscle chips keep 44px hit areas in both saved themes: '+JSON.stringify(nested));
    assert.ok(nested.nameContrast>=4.5&&nested.metaContrast>=4.5&&nested.chipContrast>=4.5,
      'Program name, metadata and chip labels meet WCAG AA contrast: '+JSON.stringify(nested));
    assert.ok(!nested.overflow,'No horizontal clipping with expanded exercises: '+JSON.stringify(nested));
    detail.nested=nested;
    captures[theme].program=Buffer.from(await page.screenshot({type:'png'}));
    // M276: reference help belongs in Details, not ahead of workout/week navigation.
    assert.equal(await page.$('.wpb-program-scroll .wpb-program-edit-scope'),null,
      'Program reading surface does not start with edit-scope documentation');
    await page.click('.wpb-program button[aria-label^="Program details"]');
    await page.waitForSelector('.wpb-program-edit-scope summary');
    const help=await page.$eval('.wpb-program-edit-scope',el=>({
      summaryHeight:el.querySelector('summary').getBoundingClientRect().height,
      text:el.querySelector('summary').textContent,
      before:el.getBoundingClientRect().width<=el.parentElement.getBoundingClientRect().width+1
    }));
    assert.ok(help.summaryHeight>=44&&help.before&&/Where do my edits apply/.test(help.text),
      'Reference help is a readable, reachable Details disclosure: '+JSON.stringify(help));
    await page.click('.wpb-program-edit-scope summary');
    assert.match(await page.$eval('.wpb-program-edit-scope',el=>el.innerText),
      /past workout logs.*Future sessions use|past workout logs.*linked cycle blocks/s,
      'Details preserves the exact saved-program and cycle editing scope');

    assert.equal(errors.length,0,'No runtime errors while checking paired themes: '+errors.join('; '));
    measurements[theme]={...surfaces,program:detail};
    await context.close();
  }
  await verifyFoundationStates(browser);
  await verifyTaskHierarchy(browser);
  await verifyVolumeDetails(browser);
  // Build genuine 1:1 side-by-side PNGs for visual signoff (left dark / right light),
  // not two disjoint galleries where differences are difficult to compare.
  const canvasPage=await browser.newPage();
  const pairs=['home','homeCompact','plan','progress','profile','settings','program'];
  for(const tab of pairs){
    const result=await canvasPage.evaluate(async({dark,light})=>{
      const load=src=>new Promise((resolve,reject)=>{const img=new Image();img.onload=()=>resolve(img);img.onerror=reject;img.src=src;});
      const [a,b]=await Promise.all([load(dark),load(light)]);
      const c=document.createElement('canvas');c.width=a.width+b.width;c.height=Math.max(a.height,b.height);
      const ctx=c.getContext('2d');ctx.drawImage(a,0,0);ctx.drawImage(b,a.width,0);
      return c.toDataURL('image/png').split(',')[1];
    },{
      dark:'data:image/png;base64,'+captures.rose[tab].toString('base64'),
      light:'data:image/png;base64,'+captures.rosewater[tab].toString('base64')
    });
    fs.writeFileSync(path.join(root,'verification/r53-'+tab+'-phone.png'),Buffer.from(result,'base64'));
  }
  await canvasPage.close();
  console.log('PASS R58: paired Rose dark/Rosewater light screens; 48px header controls and compact Home; Settings whole labels, responsive 4/2/1-column controls, real preference selection and saved data at 320/393px and 100/125% text; '+JSON.stringify(measurements));
}finally{
  if(browser)await browser.close();
  await new Promise(resolve=>server.close(resolve));
}
