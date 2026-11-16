import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import http from 'node:http';
import puppeteer from 'puppeteer-core';
const root=path.resolve(new URL('../',import.meta.url).pathname);
const html='<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="/app.css"></head><body><div id="root"><main class="wpb"><section class="wpb-ui-card" id="standard">Standard surface</section><section class="wpb-ui-card wpb-gallery-program" id="gallery"><h3 class="wpb-gallery-program-title">Community program with a long descriptive name</h3><p class="wpb-gallery-program-meta">5 day · strength and hypertrophy · 16 week block</p><p class="wpb-gallery-program-note">Fully editable after import. No changes are made to the current plan until the user chooses to import.</p></section></main></div></body></html>';
const server=http.createServer((req,res)=>{
 if(req.url==='/fixture'){res.setHeader('Content-Type','text/html');res.end(html);return;}
 if(req.url==='/app.css'){res.setHeader('Content-Type','text/css');res.end(fs.readFileSync(path.join(root,'app.css')));return;}
 res.writeHead(404).end();
});
await new Promise(done=>server.listen(0,'127.0.0.1',done));
let browser;
try{
 browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
 const page=await browser.newPage(),errors=[];
 page.on('pageerror',e=>errors.push(e.message));
 for(const width of [320,393]){
  await page.setViewport({width,height:740,deviceScaleFactor:2,isMobile:true,hasTouch:true});
  await page.goto('http://127.0.0.1:'+server.address().port+'/fixture',{waitUntil:'load'});
  if(width===320)await page.evaluate(()=>document.documentElement.style.fontSize='32px');
  const data=await page.evaluate(()=>{
   const attrs=selector=>{const el=document.querySelector(selector),s=getComputedStyle(el),r=el.getBoundingClientRect();return{
     tag:el.tagName,background:s.backgroundColor,color:s.color,border:s.borderColor,thickness:s.borderTopWidth,
     radius:s.borderTopLeftRadius,paddingLeft:s.paddingLeft,paddingRight:s.paddingRight,
     left:r.left,right:r.right,width:r.width}};
   return {normal:attrs('#standard'),gallery:attrs('#gallery'),pageWidth:document.documentElement.scrollWidth};
  });
  for(const key of ['background','color','border','thickness','radius','paddingLeft','paddingRight'])
   assert.equal(data.gallery[key],data.normal[key],key+' must be shared by Gallery and standard cards');
  assert.equal(data.gallery.tag,'SECTION','Card remains a non-interactive content container');
  assert.ok(data.gallery.left>=-1&&data.gallery.right<=width+1&&data.pageWidth<=width+1,
   'Long Gallery card remains inside '+width+'px viewport with larger text: '+JSON.stringify(data));
 }
 assert.deepEqual(errors,[],'No browser errors');
 console.log('PASS Gallery card browser: shared computed surface/radius/spacing and 320px large-type layout');
} finally{await browser?.close();await new Promise(done=>server.close(done))}
