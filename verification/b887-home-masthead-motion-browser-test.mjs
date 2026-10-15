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
const store={v:13,savedAt:1,saved:[program],cycles:[],history:[],perf:{},drafts:{},tombs:{},pinnedId:program.id,seenIntro:999,seenWhatsNew:999,unit:'lb',unitChosen:true,experience:'intermediate',bodyweight:185,installDismissedAt:Date.now(),lastBackup:Date.now()};
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html','.json':'application/json','.png':'image/png','.svg':'image/svg+xml'};
const server=http.createServer((req,res)=>{const file=path.resolve(root,'.'+new URL(req.url,'http://localhost').pathname);if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){res.writeHead(404);res.end();return;}res.setHeader('Content-Type',mime[path.extname(file)]||'application/octet-stream');res.end(fs.readFileSync(file));});
await new Promise(resolve=>server.listen(8797,'127.0.0.1',resolve));
let browser;
try{
 browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
 for(const width of [320,393,900])for(const theme of ['rose','rosewater']){
  const page=await browser.newPage(), errors=[];
  page.on('pageerror',e=>errors.push(e.message));
  await page.setViewport({width,height:width===900?850:790,isMobile:width<600,hasTouch:width<600});
  await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
  await page.evaluateOnNewDocument(seed=>localStorage.setItem('wpb:v1',JSON.stringify(seed)),{...store,theme});
  await page.goto('http://127.0.0.1:8797/index.html',{waitUntil:'networkidle0'});
  await page.waitForSelector('.wpb-home .wpb-home-header');
  // Wait for the one-time page entrance motion before measuring scroll-linked geometry.
  await new Promise(resolve=>setTimeout(resolve,480));
  const get=async (step,baseline)=>page.evaluate(async ({val,baseline})=>{
   const home=document.querySelector('.wpb-home'),sc=home.querySelector('.wpb-page-scroll');
   const header=home.querySelector('.wpb-home-header'),greeting=header.lastElementChild;
   // The scroll gesture uses the immutable expanded height, never the clipped size.
   const full=baseline??greeting.scrollHeight;
   sc.scrollTop=val*full;
   await new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)));
   const box=e=>{const r=e.getBoundingClientRect();return {x:r.x,y:r.y,w:r.width,h:r.height,right:r.right};};
   const brand=home.querySelector('.wpb-home-brand'),tools=home.querySelector('.wpb-home-tools');
   return {full,scroll:sc.scrollTop,maxScroll:sc.scrollHeight-sc.clientHeight,header:box(header),greeting:box(greeting),brand:box(brand),tools:box(tools),brandText:brand.textContent.trim(),shortExists:!!brand.querySelector('.wpb-home-brand-short'),compact:home.dataset.homeCompact,buttons:[...tools.querySelectorAll('button')].map(box),overflow:home.scrollWidth>home.clientWidth+1};
  },{val:step,baseline});
  const samples=[await get(0)];
  for(const step of [.3,.7,.93,.96,.99,1,1.15,.96,.93,.7,.3,0])samples.push(await get(step,samples[0].full));
  const top=samples[0],mid=samples[3],near=samples[4],end=samples[6],restored=samples.at(-1);
  assert.ok(top.maxScroll>top.full*1.3,'Home scroll region extends beyond one masthead');
  for(const item of samples){
   assert.equal(item.brandText,'Pursuit Iron',`full app identity on ${width} ${theme} scroll ${item.scroll}`);
   assert.equal(item.shortExists,false,'no Iron-only substitute in DOM');
   assert.equal(item.buttons.length,3,'all three actions persist');
   assert.ok(item.buttons.every(b=>b.w>=48&&b.h>=48),'48px touch targets preserved');
   assert.ok(item.brand.right+3<=item.tools.x&&!item.overflow,`no overlap or horizontal overflow: ${JSON.stringify(item)}`);
   assert.ok(Math.abs(item.tools.x-top.tools.x)<2.5,`tool position does not jump from ${top.tools.x}px: ${JSON.stringify(item)}`);
  }
  assert.ok(top.header.h-end.header.h>=24,'greeting releases real vertical space');
  assert.ok(end.header.h<=65,`collapsed header remains compact: ${end.header.h}`);
  assert.ok(end.scroll>=top.full-2,'collapse uses the full expanded greeting distance');
  assert.ok(Math.abs(mid.header.h-near.header.h)<top.full*.08+3,`no threshold snap at 95%: ${mid.header.h} → ${near.header.h}`);
  assert.ok(Math.abs(restored.header.h-top.header.h)<=2,'scrolling to top restores full header height');
  assert.deepEqual(errors,[],'no runtime error');
  if(width===393&&theme==='rose'){
    await page.locator('.hp-open').click({timeout:12000});
    await page.waitForSelector('.wpb-program',{timeout:12000});
  }
  await page.close();
 }
 console.log('PASS B887 Home masthead: scroll-linked smooth height, stable Pursuit Iron brand and 48px tools at 320/393/900 in both themes');
}finally{if(browser)await browser.close();await new Promise(resolve=>server.close(resolve));}
