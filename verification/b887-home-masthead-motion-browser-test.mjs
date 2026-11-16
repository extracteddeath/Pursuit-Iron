import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import puppeteer from 'puppeteer-core';

// This tests measured geometry at both sides of the old 95% snap, not merely
// a CSS declaration. A regression used to change branding, row height,
// padding, border and tool position at exactly this threshold.
const root=path.resolve(new URL('../',import.meta.url).pathname);
const program={id:'b887-home',name:'Full Body Training',custom:true,weeks:4,config:{unit:'lb',goal:'both',experience:'intermediate',progression:'double',split:'custom',weeks:4,days:2,session:'s60',deload:false},days:[{id:'upper',label:'Upper',primaryIndex:0,exercises:['bb-bench','inc-curl']},{id:'lower',label:'Lower',primaryIndex:1,exercises:['back-squat','leg-press']}],overrides:{'upper:0':{sets:3,reps:'6-8',rir:'2'},'lower:0':{sets:4,reps:'5-8',rir:'2'}}};
// Keep a real scrollable Program roster at phone and expanded heights.
program.days=[...program.days,...program.days.map(day=>({...day,id:day.id+'-b',label:day.label+' B'}))];
program.config.days=4;
program.days[3].label='Lower B · complete posterior-chain and leg training session';
const store={v:13,savedAt:1,saved:[program],cycles:[],history:[],perf:{},drafts:{},tombs:{},pinnedId:program.id,seenIntro:999,seenWhatsNew:999,unit:'lb',unitChosen:true,experience:'intermediate',bodyweight:185,installDismissedAt:Date.now(),lastBackup:Date.now()};
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html','.json':'application/json','.png':'image/png','.svg':'image/svg+xml'};
const server=http.createServer((req,res)=>{const file=path.resolve(root,'.'+new URL(req.url,'http://localhost').pathname);if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){res.writeHead(404);res.end();return;}res.setHeader('Content-Type',mime[path.extname(file)]||'application/octet-stream');res.end(fs.readFileSync(file));});
await new Promise(resolve=>server.listen(8797,'127.0.0.1',resolve));
let browser;
try{
 browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
 for(const width of [320,393,900])for(const theme of ['rose','rosewater'])for(const cpu of (!process.argv.includes('--native-only')&&width===320?[1,4]:[1])){
  const page=await browser.newPage(), errors=[];
  page.on('pageerror',e=>errors.push(e.message));
  await page.setViewport({width,height:width===900?850:790,isMobile:width<600,hasTouch:width<600});
  const cdp=await page.createCDPSession();await cdp.send('Emulation.setCPUThrottlingRate',{rate:cpu});
  await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
  await page.evaluateOnNewDocument(seed=>localStorage.setItem('wpb:v1',JSON.stringify(seed)),{...store,theme});
  await page.goto('http://127.0.0.1:8797/index.html',{waitUntil:'networkidle0'});
  await page.waitForSelector('.wpb-home .wpb-home-header');
  // Wait for the one-time page entrance motion before measuring scroll-linked geometry.
  await new Promise(resolve=>setTimeout(resolve,480));
  const protectedState=()=>page.evaluate(()=>{const d=JSON.parse(localStorage.getItem('wpb:v1'));return {saved:d.saved,drafts:d.drafts,history:d.history,cycles:d.cycles};});
  const initial=await protectedState();
  if(cpu===1&&width===320)await page.screenshot({path:path.join(root,`verification/m387-home-${theme}-phone.png`)});
  const get=async (step,baseline)=>page.evaluate(async ({val,baseline})=>{
   const home=document.querySelector('.wpb-home'),sc=home.querySelector('.wpb-page-scroll');
   const header=home.querySelector('.wpb-home-header'),greeting=home.querySelector('.wpb-home-context');
   // The scroll gesture uses the immutable expanded height, never the clipped size.
   const full=baseline??(greeting.getBoundingClientRect().bottom-sc.getBoundingClientRect().top+sc.scrollTop);
   sc.scrollTop=val*full;
   await new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)));
   const box=e=>{const r=e.getBoundingClientRect();return {x:r.x,y:r.y,w:r.width,h:r.height,right:r.right,bottom:r.bottom};};
   const brand=home.querySelector('.wpb-root-title-row h1'),tools=home.querySelector('.wpb-home-tools');
   return {full,viewportTop:sc.getBoundingClientRect().top,scroll:sc.scrollTop,maxScroll:sc.scrollHeight-sc.clientHeight,header:box(header),greeting:box(greeting),brand:box(brand),tools:box(tools),brandText:home.querySelector('.wpb-home-brand-long').textContent.trim(),shortExists:!!brand.querySelector('.wpb-home-brand-short'),compact:home.dataset.homeCompact,buttons:[...tools.querySelectorAll('button')].map(box),overflow:home.scrollWidth>home.clientWidth+1};
  },{val:step,baseline});
  const samples=[await get(0)];
  for(const step of [.3,.7,.93,.96,.99,1,1.15,.96,.93,.7,.3,0])samples.push(await get(step,samples[0].full));
  const top=samples[0],mid=samples[3],near=samples[4],end=samples[6],restored=samples.at(-1);
  assert.ok(top.maxScroll>top.full*1.3,'Home scroll region extends beyond one masthead');
  for(const item of samples){
   assert.ok(Math.abs(item.header.h-top.header.h)<=1,'Home app-bar geometry stays fixed during scroll');
   assert.equal(item.brandText,'Pursuit Iron',`full app identity on ${width} ${theme} scroll ${item.scroll}`);
   assert.equal(item.shortExists,false,'no Iron-only substitute in DOM');
   assert.equal(item.buttons.length,3,'all three actions persist');
   assert.ok(item.buttons.every(b=>b.w>=48&&b.h>=48),'48px touch targets preserved');
   assert.ok(item.brand.right+3<=item.tools.x&&!item.overflow,`no overlap or horizontal overflow: ${JSON.stringify(item)}`);
   assert.ok(Math.abs(item.tools.x-top.tools.x)<2.5,`tool position does not jump from ${top.tools.x}px: ${JSON.stringify(item)}`);
  }
  if(width<600) assert.ok(end.header.h<=70,`phone app bar stays compact: ${end.header.h}`);
  assert.ok(end.greeting.bottom<=end.viewportTop+1,'Supporting context scrolls fully out of the viewport');
  for(const item of samples) assert.ok(Math.abs((top.greeting.y-item.greeting.y)-item.scroll)<=1,
    'Greeting follows natural scroll without a second layout movement: '+JSON.stringify(item));
  assert.ok(Math.abs(restored.greeting.y-top.greeting.y)<=1,'Scroll-to-top restores the full context');
  const scrollHandoff=await page.evaluate(async full=>{
   const home=document.querySelector('.wpb-home'),sc=home.querySelector('.wpb-page-scroll');
   const greeting=home.querySelector('.wpb-home-context');
   sc.scrollTop=full*2;
   await new Promise(r=>requestAnimationFrame(r));
   const first=greeting.getBoundingClientRect().bottom;
   await new Promise(r=>requestAnimationFrame(r));
   return {first,following:greeting.getBoundingClientRect().bottom,viewport:sc.getBoundingClientRect().top};
  },top.full);
  assert.ok(scrollHandoff.first<=scrollHandoff.viewport+1&&scrollHandoff.following<=scrollHandoff.viewport+1,
    'Header geometry is ready when next-frame pointer positioning begins: '+JSON.stringify(scrollHandoff));
  await get(0,top.full);
  assert.deepEqual(errors,[],'no runtime error');
  // A real pointer action scrolls the saved program into view. Deferred header
  // collapse used to move it between the pointer's geometry read and click,
  // so the same action landed on the card footer instead of opening the program.
  const pointerHandoff=await page.evaluate(async()=>{
   const button=document.querySelector('.hp-open');button.scrollIntoView({block:'center'});
   const point=()=>{const r=button.getBoundingClientRect();return {x:r.left+r.width/2,y:r.top+r.height/2};};
   const before=point();await new Promise(r=>requestAnimationFrame(r));const first=point();
   await new Promise(r=>requestAnimationFrame(r));return {before,first,following:point()};
  });
  assert.ok(Math.abs(pointerHandoff.before.y-pointerHandoff.first.y)<=1&&Math.abs(pointerHandoff.first.y-pointerHandoff.following.y)<=1,
   'Automatic Home scrolling does not move the located program target: '+JSON.stringify(pointerHandoff));
  await page.mouse.click(pointerHandoff.before.x,pointerHandoff.before.y);
  await page.waitForSelector('.wpb-program',{timeout:12000});
  assert.equal(await page.$eval('.wpb-program-name-text',n=>n.textContent),program.name,
    'A pointer opens the intended program immediately after Home scrolls');
  const programHandoff=await page.evaluate(async()=>{
   const sc=document.querySelector('.wpb-program-scroll'),meta=document.querySelector('.wpb-program-meta-collapse');
   sc.scrollTop=0;await new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)));
   const full=meta.scrollHeight;sc.scrollTop=full*2;
   await new Promise(r=>requestAnimationFrame(r));const first=meta.getBoundingClientRect().height;
   await new Promise(r=>requestAnimationFrame(r));
   return {first,following:meta.getBoundingClientRect().height,scroll:sc.scrollTop,full};
  });
  assert.ok(programHandoff.scroll>=programHandoff.full&&programHandoff.first<=1&&programHandoff.following<=1,
   'Program header geometry is ready before next-frame pointer positioning: '+JSON.stringify(programHandoff));
  const frames=()=>page.evaluate(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))));
  const openMenu=async(trigger,menu)=>{
   await page.$eval(trigger,el=>{el.scrollIntoView({block:'center'});el.focus();});
   await page.keyboard.press('Enter');await page.waitForSelector(menu,{visible:true});
   await page.waitForFunction(selector=>document.querySelector(selector)?.contains(document.activeElement),{},menu);
  };
  const fit=async(selector,text)=>{
   await frames();
   const layout=await page.$eval(selector,el=>{
    const r=el.getBoundingClientRect(),rows=[...el.querySelectorAll('[role="menuitem"]')];
    const anchor=[...document.querySelectorAll('[aria-controls]')].find(n=>n.getAttribute('aria-controls')===el.id&&n.getAttribute('aria-expanded')==='true');
    const a=anchor?.getBoundingClientRect();
    let bottom=innerHeight-8;
    for(const bar of document.querySelectorAll('.wpb-tabbar,.wpb-live-dock')){
     const b=bar.getBoundingClientRect();if(b.width&&b.height&&b.height<=b.width*2&&getComputedStyle(bar).visibility!=='hidden')bottom=Math.min(bottom,b.top-8);
    }
    const expectedLeft=a?Math.max(8,Math.min(innerWidth-8-el.offsetWidth,a.right-el.offsetWidth)):NaN;
    return {left:r.left,right:r.right,top:r.top,bottom:r.bottom,limit:bottom,expectedLeft,width:el.clientWidth,scroll:el.scrollWidth,
     font:parseFloat(getComputedStyle(rows[0]).fontSize),heights:rows.map(n=>n.getBoundingClientRect().height),
     labels:[...el.querySelectorAll('.wpb-context-menu-label')].every(n=>getComputedStyle(n).whiteSpace!=='nowrap'&&n.scrollWidth<=n.clientWidth+1)};
   });
   assert.ok(layout.left>=7&&layout.right<=await page.evaluate(()=>innerWidth-7)&&layout.top>=7&&layout.bottom<=layout.limit+1
    &&Math.abs(layout.left-layout.expectedLeft)<=2&&layout.scroll<=layout.width+1&&layout.font>=text*.9375-.1&&layout.heights.every(h=>h>=44)&&layout.labels,
    'Measured menu follows its anchor, viewport and text: '+JSON.stringify({theme,width,cpu,text,layout}));
  };
  const dayAction='[aria-label="More options for Upper"]';
  await openMenu(dayAction,'[data-daymenu]');
  await page.evaluate(()=>document.documentElement.style.fontSize='32px');await fit('[data-daymenu]',32);
  assert.equal(await page.$eval('[data-daymenu] [role="menuitem"]',el=>el.textContent.trim()),'Rename day');
  await page.keyboard.press('End');
  assert.equal(await page.evaluate(()=>document.activeElement.textContent.trim()),'Delete day');
  await page.keyboard.press('Enter');await page.waitForSelector('.wpb-dialog');
  await page.keyboard.press('Escape');await page.waitForSelector('.wpb-dialog',{hidden:true});
  assert.deepEqual(await protectedState(),initial,'Cancel day deletion preserves all training');
  await openMenu('[aria-label="Jump to day"]','[data-daypicker]');
  await page.setViewport({width:width===900?430:900,height:400,isMobile:width<600,hasTouch:width<600});
  await fit('[data-daypicker]',32);await page.keyboard.press('End');await frames();
  const lastFocus=await page.$eval('[data-daypicker]',el=>{
   const item=document.activeElement,r=item.getBoundingClientRect(),menu=el.getBoundingClientRect();
   return {last:item===[...el.querySelectorAll('[role="menuitem"]')].at(-1),visible:r.top>=menu.top-1&&r.bottom<=menu.bottom+1};
  });
  assert.ok(lastFocus.last&&lastFocus.visible,'End reveals the last row inside a short-window menu without moving its anchor');
  await page.setViewport({width,height:width===900?850:790,isMobile:width<600,hasTouch:width<600});
  await fit('[data-daypicker]',32);
  if(cpu===1&&width===320)await page.screenshot({path:path.join(root,`verification/m387-day-menu-${theme}-phone.png`)});
  await page.keyboard.press('Escape');await page.waitForSelector('[data-daypicker]',{hidden:true});
  assert.equal(await page.evaluate(()=>document.activeElement.getAttribute('aria-label')),'Jump to day');
  await page.evaluate(()=>document.documentElement.style.fontSize='16px');
  await openMenu('[aria-label="Jump to day"]','[data-daypicker]');await page.keyboard.press('End');await page.keyboard.press('Enter');
  await page.waitForSelector('[data-daypicker]',{hidden:true});
  await page.waitForFunction(()=>document.getElementById('pv-day-lower-b')?.getAttribute('data-open')==='true'||document.getElementById('pv-day-lower-b')?.querySelector('[aria-expanded="true"]'));
  assert.deepEqual(await protectedState(),initial,'Selecting a day is navigation, not a program edit');
  await page.$eval('.wpb-program button[aria-label="Back"]',el=>el.click());await page.waitForSelector('.wpb-home');
  const homeAction='[aria-label="More options for Full Body Training"]';
  await openMenu(homeAction,'[data-progmenu]');await page.evaluate(()=>document.documentElement.style.fontSize='32px');
  await fit('[data-progmenu]',32);await page.keyboard.press('End');
  await cdp.send('Emulation.setEmulatedMedia',{features:[{name:'prefers-reduced-motion',value:'reduce'},{name:'forced-colors',value:'active'}]});
  const forced=await page.$eval('[data-progmenu]',el=>({active:matchMedia('(forced-colors:active)').matches,
   border:parseFloat(getComputedStyle(el).borderTopWidth),outline:parseFloat(getComputedStyle(document.activeElement).outlineWidth),animation:getComputedStyle(el).animationName}));
  assert.ok(forced.active&&forced.border>=1&&forced.outline>=2&&forced.animation==='none','Menu focus/borders remain visible in forced colors and reduced motion');
  await cdp.send('Emulation.setEmulatedMedia',{features:[{name:'prefers-reduced-motion',value:'reduce'}]});
  await page.keyboard.press('Tab');
  await page.waitForSelector('[data-progmenu]',{hidden:true});
  assert.deepEqual(await protectedState(),initial,'Tab and menu inspection preserve saved programs, history and drafts');
  assert.deepEqual(errors,[]);
  console.log('PASS M387 navigation '+JSON.stringify({width,theme,cpu,homeTargetDelta:pointerHandoff.first.y-pointerHandoff.before.y,programFirstFrame:programHandoff.first,menus:3}));
  await page.close();
 }
 console.log('PASS B887/M387: natural Home scroll, first-frame Program header, native/4x pointer handoff, shared menus, scalable text, resize, keyboard/focus and immutable training');
}finally{if(browser)await browser.close();await new Promise(resolve=>server.close(resolve));}
