import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import http from 'node:http';
import puppeteer from 'puppeteer-core';

const root=path.resolve(new URL('../',import.meta.url).pathname);
const map=fs.readFileSync(path.join(root,'index.html'),'utf8')
    .match(/<script type="importmap">[\s\S]*?<\/script>/)[0];
const fixture=[
"import React from '/vendor/react.js';",
"import {createRoot} from '/vendor/react-dom-client.js';",
"import {HomeSessionOutline,SessionGamePlan,PlanChangeBanner} from '/modules/plan-session-surfaces.js';",
"import {WorkingSetCountControl} from '/modules/workout-session-controls.js';",
"const gameReview={unit:'lb',exercises:[{id:'bench',slot:0,name:'Barbell Bench Press',effective:[{weight:185,reps:8},{weight:185,reps:8}],protocol:{type:'straight'},change:{undone:false,changed:true},reason:'Earn more reps before raising the load.',opportunity:null},{id:'myo',slot:0,name:'Cable Curl',effective:[{weight:50,reps:12},{weight:45,reps:13},{weight:40,reps:14}],protocol:{type:'myo_reps'},change:{undone:false,changed:false},reason:'Control fatigue across the set.',opportunity:null},{id:'pr',slot:0,name:'Seated Row',effective:[{weight:100,reps:8}],protocol:{type:'straight'},change:{undone:false,changed:false},opportunity:{type:'reps',bestReps:8},reason:'Keep the reps clean.'}]};",
"const savedReview={changes:[{change:{setDelta:{before:3,after:4},loadChanged:false,undone:false}}]};",
"const loadReview={changes:[{change:{setDelta:null,loadChanged:true,undone:false}}]};",
"const mixedReview={changes:[{change:{setDelta:{before:3,after:4},loadChanged:true,undone:false}}]};",
"window.bannerClicks=0;",
"function Demo(){const [count,setCount]=React.useState('');window.setWorkingSets=setCount;",
"const [a,setA]=React.useState(true);const [b,setB]=React.useState(false);",
"const review={exercises:[{id:'bench',slot:0,name:'Barbell Bench Press',cell:{sets:3,range:'8–10'}}]};",
"return React.createElement('main',{className:'wpb'},",
"React.createElement(WorkingSetCountControl,{value:count,onChange:setCount,label:'Working sets'}),",
"React.createElement(HomeSessionOutline,{review,expanded:a,onToggle:()=>setA(v=>!v)}),",
"React.createElement(HomeSessionOutline,{review,expanded:b,onToggle:()=>setB(v=>!v)}),",
"React.createElement('section',{id:'game-plan-test'},React.createElement(SessionGamePlan,{review:gameReview,home:true,restScale:1.15})),",
"React.createElement('section',{id:'plan-banner-test'},",
"React.createElement(PlanChangeBanner,{review:savedReview,onClick:()=>window.bannerClicks++}),",
"React.createElement(PlanChangeBanner,{review:loadReview,onClick:()=>window.bannerClicks++}),",
"React.createElement(PlanChangeBanner,{review:mixedReview,onClick:()=>window.bannerClicks++})));}",
"createRoot(document.getElementById('root')).render(React.createElement(Demo));"
].join('\n');
const html='<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="/app.css">'
    +map+'</head><body><div id="root"></div><script type="module">'+fixture+'</script></body></html>';
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html'};
const server=http.createServer((req,res)=>{
 const pathname=new URL(req.url,'http://localhost').pathname;
 if(pathname==='/b998.html'){res.setHeader('Content-Type','text/html');res.end(html);return;}
 const filename=path.resolve(root,'.'+pathname);
 if(!filename.startsWith(root+path.sep)||!fs.existsSync(filename)||!fs.statSync(filename).isFile()){
  res.writeHead(404);res.end();return;
 }
 res.setHeader('Content-Type',mime[path.extname(filename)]||'application/octet-stream');
 res.end(fs.readFileSync(filename));
});
await new Promise(done=>server.listen(0,'127.0.0.1',done));
let browser;
try{
 browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',
   headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
 const page=await browser.newPage(),errors=[];
 page.on('pageerror',e=>errors.push(e.message));
 await page.setViewport({width:393,height:740,deviceScaleFactor:2,isMobile:true,hasTouch:true});
 await page.goto('http://127.0.0.1:'+server.address().port+'/b998.html',{waitUntil:'networkidle0'});
 await page.waitForSelector('.wpb-home-outline-toggle');
 const ids=await page.$$eval('.wpb-home-outline-toggle',buttons=>buttons.map(btn=>{
   const id=btn.getAttribute('aria-controls'),panel=document.getElementById(id);
   return {id,exists:!!panel,expanded:btn.getAttribute('aria-expanded')};
 }));
 assert.equal(ids.length,2);
 assert.notEqual(ids[0].id,ids[1].id,'distinct mounted disclosures never reuse the same ID');
 assert.ok(ids.every(row=>row.exists),'each toggle points to its own live panel');
 assert.deepEqual(ids.map(x=>x.expanded),['true','false']);
 await page.$$eval('.wpb-home-outline-toggle',items=>items[1].click());
 assert.equal(await page.$$eval('.wpb-home-outline-toggle',nodes=>nodes[1].getAttribute('aria-expanded')),'true');
 const add='[aria-label="Increase Working sets"]',minus='[aria-label="Decrease Working sets"]';
 await page.click(add);
 assert.equal(await page.$eval('[aria-label="Working sets"]',el=>el.value),'2','blank draft step is finite');
 await page.click(minus);
 assert.equal(await page.$eval('[aria-label="Working sets"]',el=>el.value),'1');
 await page.evaluate(()=>window.setWorkingSets('200'));
 await page.waitForFunction(()=>document.querySelector('[aria-label="Working sets"]')?.value==='200'
    && document.querySelector('[aria-label="Increase Working sets"]')?.disabled===true);
 assert.equal(await page.$eval(add,el=>el.disabled),true,'max is enforced in UI');
 await page.click(minus);
 assert.equal(await page.$eval('[aria-label="Working sets"]',el=>el.value),'19',
   'above-bound text clamps to an actual 1–20 integer before step');
 await page.evaluate(()=>window.setWorkingSets('2.5'));
 await page.waitForFunction(()=>document.querySelector('[aria-label="Working sets"]')?.value==='2.5');
 await page.click(add);
 assert.equal(await page.$eval('[aria-label="Working sets"]',el=>el.value),'3',
   'fractional typed value never emits a fractional or NaN step');
 await page.setViewport({width:320,height:550,deviceScaleFactor:2,isMobile:true,hasTouch:true});
 await page.evaluate(()=>document.documentElement.style.fontSize='32px');
 const geometry=await page.$eval('.wpb-working-set-control',el=>{
   const r=el.getBoundingClientRect();return {left:r.left,right:r.right,width:innerWidth};
 });
 assert.ok(geometry.left>=-1&&geometry.right<=321,'compact set control fits screen '+JSON.stringify(geometry));

 // Design spec §§6,7.1,9.2,10: collapsed advice shows an actionable target instead of repeating its explanation.
 const previews=await page.evaluate(()=>Array.from(document.querySelectorAll('#game-plan-test .wpb-game-plan-highlight-copy')).map(el=>({
   id:el.closest('[data-game-plan-exercise]')?.getAttribute('data-game-plan-exercise'),
   preview:el.querySelector('.wpb-game-plan-highlight-preview')?.textContent,
   explanation:el.querySelector('p')?.textContent,
   open:el.open,summaryHeight:el.querySelector('summary')?.getBoundingClientRect().height
 })));
 const highlight=Object.fromEntries(previews.map(row=>[row.id,row]));
 assert.equal(previews.length,3,'Game Plan shows no more than three prioritised highlights');
 assert.match(highlight.bench.preview,/2 sets · 185 lb × 8/);
 assert.equal(highlight.bench.explanation,'Earn more reps before raising the load.',
   'Opening a uniform target reveals its reason, not another copy of the target');
 assert.ok(!highlight.bench.explanation.includes('185 lb × 8'));
 assert.match(highlight.myo.preview,/3 sets · 50 lb × 12 first · Myo-reps/);
 assert.match(highlight.myo.explanation,/Working sets:.*50 lb × 12.*45 lb × 13.*40 lb × 14/);
 assert.match(highlight.myo.explanation,/second rests/,'Myo-rest detail stays available on expansion');
 assert.match(highlight.pr.preview,/100 lb × 8/);
 assert.match(await page.$eval('[data-game-plan-exercise="pr"] strong',el=>el.textContent),/PR chance/,
   'A projected PR must never masquerade as a recorded milestone');
 assert.ok(previews.every(row=>!row.open&&row.summaryHeight>=48),
   'Native disclosure remains a >=48px target under 320px/200% typography');
 await page.click('#game-plan-test [data-game-plan-exercise="myo"] summary');
 assert.equal(await page.$eval('[data-game-plan-exercise="myo"] details',el=>el.open),true);
 assert.equal(await page.$eval('[data-game-plan-exercise="myo"] .wpb-game-plan-highlight-preview',el=>getComputedStyle(el).display),'none',
   'Expanded detail hides the collapsed target instead of showing duplicate copy');
 await page.focus('#game-plan-test [data-game-plan-exercise="myo"] summary');
 await page.keyboard.press('Enter');
 assert.equal(await page.$eval('[data-game-plan-exercise="myo"] details',el=>el.open),false,
   'Keyboard operates the native details without modifying the plan');
 const banners=await page.evaluate(()=>Array.from(document.querySelectorAll('#plan-banner-test .wpb-plan-change-banner')).map(el=>el.innerText));
 assert.equal(banners.length,3);
 assert.ok(banners.every(x=>x.includes('1 plan adjustment')&&!x.includes('upcoming adjustment')),
   'An already-saved revision must not be mislabeled as upcoming-only');
 assert.match(banners[0],/saved program set changes/);
 assert.match(banners[1],/workout load choices/);
 assert.match(banners[2],/saved sets and workout loads/);
 assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),
   'Game Plan targets and Plan banners must wrap at 320px/200% text');
 assert.deepEqual(errors,[],'no React browser errors');
 console.log('PASS B998 browser: independent Home disclosures, bounded steppers and 320px/200% width.');
}finally{await browser?.close();await new Promise(resolve=>server.close(resolve));}
