import assert from 'node:assert/strict';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import puppeteer from 'puppeteer-core';

const css=fs.readFileSync(new URL('../app.css',import.meta.url),'utf8');
const fixture='<!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">'+
  '<link rel="stylesheet" href="/app.css"><style>body{margin:0;padding:12px;box-sizing:border-box}*{box-sizing:border-box}</style>'+
  '<div id="root" data-pi-motion="expressive" style="--pi-color-control:#30243b;--pi-color-card:#201927;--pi-color-divider:#6c536d;--wpb-theme-border:#6c536d;--wpb-theme-accent-dim:#59426c;--wpb-theme-accent-ink:#edd0ff;--pi-ease:ease-out">'+
  '<div class="wpb"><section class="wpb-progress"><details class="wpb-chart-favorites" open><summary>Favorite charts</summary><div class="wpb-chart-favorites-content">'+
  '<div class="wpb-chart-favorite-row"><span class="wpb-chart-favorite-name">Very long incline dumbbell curl strength performance chart</span>'+
  '<button class="wpb-chart-favorite-action" aria-label="Move up">↑</button><button class="wpb-chart-favorite-action" aria-label="Move down">↓</button><button class="wpb-chart-favorite-action" aria-label="Unfavorite">×</button></div>'+
  '<button class="wpb-chart-favorite-add">Add an exercise chart</button></div></details></section></div></div>';
const server=http.createServer((req,res)=>{if(req.url==='/app.css'){res.setHeader('Content-Type','text/css');res.end(css);}else{res.setHeader('Content-Type','text/html');res.end(fixture);}});
await new Promise(r=>server.listen(0,'127.0.0.1',r));
let browser;
try{
  browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
  for(const width of [320,393,834])for(const motion of ['no-preference','reduce']){
    const page=await browser.newPage();
    await page.setViewport({width,height:800,isMobile:width<600,hasTouch:width<600});
    await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:motion}]);
    await page.goto('http://127.0.0.1:'+server.address().port+'/',{waitUntil:'load'});
    const x=await page.evaluate(()=>{
      const get=s=>[...document.querySelectorAll(s)].map(el=>{
        const r=el.getBoundingClientRect(),cs=getComputedStyle(el);
        return {width:r.width,height:r.height,left:r.left,right:r.right,transition:cs.transitionProperty,duration:cs.transitionDuration};
      });
      return {overflow:document.documentElement.scrollWidth>innerWidth+1,buttons:get('.wpb-chart-favorite-action,.wpb-chart-favorite-add'),name:get('.wpb-chart-favorite-name')[0],row:get('.wpb-chart-favorite-row')[0]};
    });
    assert.equal(x.overflow,false,'no horizontal page overflow at '+width);
    assert.ok(x.buttons.every(b=>b.width>=47.5&&b.height>=47.5),'48px controls at '+width+': '+JSON.stringify(x.buttons));
    assert.ok(x.buttons.every(b=>b.left>=-1&&b.right<=width+1),'offscreen controls at '+width);
    assert.ok(x.name.width>25&&x.name.right<=x.row.right,'wrapping title stays in row at '+width);
    if(motion==='reduce')assert.ok(x.buttons.every(b=>b.duration.split(',').every(d=>parseFloat(d)===0)),'reduced motion disables transition');
    else assert.ok(x.buttons.every(b=>!b.transition.includes('transform')&&!b.transition.includes('width')),'no geometry animation');
    await page.close();
  }
  console.log('PASS B911 browser: chart favorites 48px controls, wrapping names, no narrow overflow and reduced motion.');
} finally {if(browser)await browser.close();await new Promise(r=>server.close(r));}
