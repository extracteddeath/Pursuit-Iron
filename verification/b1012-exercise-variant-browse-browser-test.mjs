import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import puppeteer from 'puppeteer-core';

const root=path.resolve(new URL('../',import.meta.url).pathname);
const fixture=`<!doctype html><html lang="en"><head>
<meta name="viewport" content="width=device-width,initial-scale=1">
<link rel="stylesheet" href="./app.css">
<script type="importmap">{"imports":{"react":"./vendor/react.js","react/jsx-runtime":"./vendor/react-jsx-runtime.js","react-dom/client":"./vendor/react-dom-client.js","lucide-react":"./vendor/lucide-react.js"}}</script></head>
<body><div class="wpb" id="root"></div>
<script type="module">
import {createRoot} from 'react-dom/client';
import {jsx} from 'react/jsx-runtime';
import {ExercisePickerSheet} from './modules/exercise-picker-sheet.js';
const generic=Array.from({length:155},(_,n)=>({
 id:'generic-'+n,name:'Generic Chest '+n,part:'chest',equip:['dumbbell']
}));
const variations=[
 {id:'low',name:'Low-Incline Barbell Press',part:'chest',equip:['barbell','inclinebench']},
 {id:'high',name:'High-Incline Barbell Press',part:'chest',equip:['barbell','inclinebench']},
 {id:'dual-rope-pushdown',name:'Dual-Rope Pushdown',part:'chest',equip:['cable']},
 {id:'v-bar-pushdown',name:'V-Bar Pushdown',part:'chest',equip:['cable']}
];
const exercises=generic.concat(variations);
window.__picked=null;
createRoot(document.querySelector('#root')).render(jsx(ExercisePickerSheet,{
 onPick:id=>window.__picked=id,onClose:()=>window.__closed=true,
 banned:['v-bar-pushdown'],title:'Choose an exercise',
 exercises,parts:['chest'],partLabels:{chest:'Chest'},
 displayName:e=>e.name,equipmentDescription:e=>e.equip.join(' · '),
 searchable:(e,q)=>String(e.name+' '+e.id).toLowerCase().includes(
  String(q).trim().toLowerCase()),sheetDragRef:null
}));
</script></body></html>`;
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html',
 '.json':'application/json','.png':'image/png','.svg':'image/svg+xml'};
const server=http.createServer((req,res)=>{
 const url=new URL(req.url,'http://localhost').pathname;
 if(url==='/variant-fixture.html') {
  res.setHeader('Content-Type','text/html');res.end(fixture);return;
 }
 const resolved=path.resolve(root,'.'+url);
 if(!resolved.startsWith(root+path.sep) || !fs.existsSync(resolved) ||
    !fs.statSync(resolved).isFile()) {
  res.writeHead(404);res.end();return;
 }
 res.setHeader('Content-Type',mime[path.extname(resolved)]||'text/plain');
 res.end(fs.readFileSync(resolved));
});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
let browser;
try {
 browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',
  headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
 const page=await browser.newPage(),errors=[];
 page.on('pageerror',error=>errors.push(error.message));
 await page.setViewport({width:320,height:800,deviceScaleFactor:2,isMobile:true,hasTouch:true});
 await page.goto('http://127.0.0.1:'+server.address().port+'/variant-fixture.html',
  {waitUntil:'load'});
 await page.waitForSelector('button[aria-label="Choose Generic Chest 0"]');
 const count=await page.$eval('[role="status"]',n=>n.textContent);
 assert.match(count,/159 matching setups/,'full 159 exercise choices are indexed');
 assert.equal(await page.$$('button[aria-label^="Choose Generic Chest"]')
    .then(xs=>xs.length),40,'first 40 are rendered but not a hard catalog cap');
 const more=async()=>{
  await page.evaluate(()=>[...document.querySelectorAll('button')]
   .find(n=>n.textContent.startsWith('Show more'))?.click());
 };
 await more();
 await page.waitForSelector('button[aria-label="Choose Generic Chest 70"]');
 await more();
 await page.waitForSelector('button[aria-label="Choose Generic Chest 110"]');
 await page.evaluate(()=>{
  const chip=[...document.querySelectorAll('button')].find(b=>
   b.textContent.trim()==='High incline');
  if(!chip)throw Error('High incline filter hidden');chip.click();
 });
 await page.waitForSelector('button[aria-label="Choose High-Incline Barbell Press"]');
 assert.equal(await page.$('button[aria-label="Choose Low-Incline Barbell Press"]'),null,
  'low incline must not leak into high incline');
 await page.evaluate(()=>{
  [...document.querySelectorAll('button')].find(b=>b.textContent.trim()==='Any angle').click();
  [...document.querySelectorAll('button')].find(b=>b.textContent.trim()==='Dual rope').click();
 });
 await page.waitForSelector('button[aria-label="Choose Dual-Rope Pushdown"]');
 assert.equal(await page.$('button[aria-label="Choose V-Bar Pushdown"]'),null,
  'exact attachment filter cannot match V-bar');
 await page.click('button[aria-label="Choose Dual-Rope Pushdown"]');
 assert.equal(await page.evaluate(()=>window.__picked),'dual-rope-pushdown');
 await page.evaluate(()=>{
  // A filter reset correctly returns pagination to page one. Pick the V-bar
  // facet explicitly rather than assuming the 159th row remains rendered.
  [...document.querySelectorAll('button')].find(b=>b.textContent.trim()==='V-bar').click();
 });
 await page.waitForSelector('button[aria-label="Choose V-Bar Pushdown"]');
 assert.equal(await page.$eval('button[aria-label="Choose V-Bar Pushdown"]',el=>el.disabled),true,
  'banned source exercise cannot be picked even after clearing filters');
 assert.deepEqual(errors,[],'new picker has no uncaught browser errors');
 console.log('PASS B1012 real Chromium: 159 options, >100 reachability, incline/dual-rope exclusivity and banned selection at 320px.');
} finally {
 await browser?.close();
 await new Promise(resolve=>server.close(resolve));
}
