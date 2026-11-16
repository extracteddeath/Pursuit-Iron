// B1010: real program round controls, 320px / 200% type and target separation.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import puppeteer from 'puppeteer-core';

const root=path.resolve(new URL('../',import.meta.url).pathname);
const html=`<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<link rel="stylesheet" href="/app.css"></head><body>
<div id="root"><main class="wpb" style="padding:12px;box-sizing:border-box;width:100%">
  <section class="wpb-s956-display-flex wpb-s956-alignitems-center wpb-s956-justifycontent-space-between wpb-round-row">
    <div>Circuit · 3 exercises</div>
    <div class="wpb-layout-row-tight">
      <span>Auto sets</span>
      <div style="display:flex;align-items:center;gap:2px;padding:2px">
        <button type="button" class="pressable wpb-round-step" aria-label="Decrease rounds">−</button>
        <span class="mono" style="min-width:16px;text-align:center">3</span>
        <button type="button" class="pressable wpb-round-step" aria-label="Increase rounds">+</button>
      </div>
      <button type="button" class="pressable wpb-quiet-faint" aria-label="Back to auto sets">×</button>
    </div>
  </section>
</main></div></body></html>`;
const server=http.createServer((req,res)=>{
 const url=new URL(req.url,'http://localhost').pathname;
 if(url==='/b1010.html'){res.setHeader('Content-Type','text/html');res.end(html);return;}
 const file=path.resolve(root,'.'+url);
 if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){
   res.writeHead(404);res.end();return;
 }
 res.setHeader('Content-Type',url.endsWith('.css')?'text/css':'text/plain');
 res.end(fs.readFileSync(file));
});
await new Promise(done=>server.listen(0,'127.0.0.1',done));
let browser;
try{
 browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',
   headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
 const page=await browser.newPage(),errors=[];
 page.on('pageerror',e=>errors.push(e.message));
 for(const width of [393,320]){
   await page.setViewport({width,height:740,deviceScaleFactor:2,isMobile:true,hasTouch:true});
   await page.goto('http://127.0.0.1:'+server.address().port+'/b1010.html',{waitUntil:'load'});
   await page.evaluate(()=>{document.documentElement.style.fontSize='32px';});
   const rects=await page.evaluate(()=>{
     const els=[...document.querySelectorAll('.wpb-round-step,.wpb-round-row .wpb-quiet-faint')];
     const entries=els.map(x=>{const r=x.getBoundingClientRect();
       return {label:x.getAttribute('aria-label'),left:r.left,right:r.right,top:r.top,bottom:r.bottom,
         width:r.width,height:r.height};});
     return {entries,pageWidth:document.documentElement.scrollWidth,viewport:innerWidth};
   });
   assert.equal(rects.entries.length,3,'Two adjustments and the reset remain available');
   assert.ok(rects.entries.every(x=>x.width>=48&&x.height>=48),
     'Physical 48px hit target, not a visual pseudo hit: '+JSON.stringify({width,rects}));
   for(let i=0;i<rects.entries.length;i++)for(let j=i+1;j<rects.entries.length;j++){
     const a=rects.entries[i],b=rects.entries[j];
     const horizontal=Math.max(0,Math.min(a.right,b.right)-Math.max(a.left,b.left));
     const vertical=Math.max(0,Math.min(a.bottom,b.bottom)-Math.max(a.top,b.top));
     assert.ok(horizontal*vertical<1,'Program round controls may not overlap hit areas');
   }
   assert.ok(rects.pageWidth<=width+1,'Program row must not cause page overflow: '+JSON.stringify({width,rects}));
 }
 assert.deepEqual(errors,[],'No JS errors');
 console.log('PASS B1010 browser: real 48px Program rounds/reset targets, wrapping and zero overlap at 320/393px 200% type.');
}finally{await browser?.close();await new Promise(resolve=>server.close(resolve));}
