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
"import {HomeSessionOutline} from '/modules/plan-session-surfaces.js';",
"import {WorkingSetCountControl} from '/modules/workout-session-controls.js';",
"function Demo(){const [count,setCount]=React.useState('');window.setWorkingSets=setCount;",
"const [a,setA]=React.useState(true);const [b,setB]=React.useState(false);",
"const review={exercises:[{id:'bench',slot:0,name:'Barbell Bench Press',cell:{sets:3,range:'8–10'}}]};",
"return React.createElement('main',{className:'wpb'},",
"React.createElement(WorkingSetCountControl,{value:count,onChange:setCount,label:'Working sets'}),",
"React.createElement(HomeSessionOutline,{review,expanded:a,onToggle:()=>setA(v=>!v)}),",
"React.createElement(HomeSessionOutline,{review,expanded:b,onToggle:()=>setB(v=>!v)}));}",
"createRoot(document.getElementById('root')).render(React.createElement(Demo));"
].join('\n');
const html='<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="/app.css">'
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
 assert.deepEqual(errors,[],'no React browser errors');
 console.log('PASS B998 browser: independent Home disclosures, bounded steppers and 320px/200% width.');
}finally{await browser?.close();await new Promise(resolve=>server.close(resolve));}
