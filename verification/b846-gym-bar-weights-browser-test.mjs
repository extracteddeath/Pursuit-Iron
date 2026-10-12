import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import puppeteer from 'puppeteer-core';

const root=path.resolve(new URL('../',import.meta.url).pathname);
const store={v:13,savedAt:1,saved:[],cycles:[],history:[],perf:{},drafts:{},tombs:{},
  seenIntro:999,seenWhatsNew:999,theme:'amethyst',unit:'lb',unitChosen:true,
  experience:'intermediate',installDismissedAt:Date.now(),lastBackup:Date.now(),
  gyms:[{id:'test_home',name:'Test Garage',equipment:['barbell','smith','dumbbell'],
      barWeights:{unit:'lb',barbell:45,smith:35}},
    {id:'test_elsewhere',name:'Other Gym',equipment:['barbell','smith'],
      barWeights:{unit:'kg',barbell:20,smith:15}}],activeGymId:'test_home'};
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html','.json':'application/json',
  '.svg':'image/svg+xml','.png':'image/png','.webmanifest':'application/manifest+json'};
const server=http.createServer((req,res)=>{
  const route=new URL(req.url,'http://localhost').pathname;
  const file=path.resolve(root,'.'+(route==='/'?'/index.html':route));
  if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){
    res.writeHead(404);res.end();return;
  }
  res.setHeader('Content-Type',mime[path.extname(file)]||'application/octet-stream');
  res.end(fs.readFileSync(file));
});
await new Promise(resolve=>server.listen(8799,'127.0.0.1',resolve));
let browser;
try{
  browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',
    headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
  for(const [width,height] of [[393,852],[1024,900]]){
    const context=await browser.createBrowserContext();
    const page=await context.newPage(), errors=[];
    page.on('pageerror',e=>errors.push(e.message));
    try{
      await page.setViewport({width,height,deviceScaleFactor:1,isMobile:width<500,hasTouch:width<500});
      await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
      await page.evaluateOnNewDocument(state=>localStorage.setItem('wpb:v1',JSON.stringify(state)),store);
      await page.goto('http://127.0.0.1:8799/',{waitUntil:'networkidle0'});
      await page.click('[data-tab="settings"]');
      await page.waitForSelector('.wpb-settings-card');
      const opened=await page.evaluate(()=>{
        const b=[...document.querySelectorAll('button')].find(x=>x.textContent.includes('Your gyms'));
        if(!b)return false;b.click();return true;
      });
      assert.equal(opened,true,'settings exposes gym editor');
      await page.waitForSelector('.wpb-gym-sheet');
      // CSS sheet entrance and window reflow are asynchronous. Test the final
      // reachable geometry, not an in-flight sheetUp transform; a persistently
      // off-screen or wrong-size editor still fails at the timeout.
      try { await page.waitForFunction(({width,height})=>{
        const sheet=document.querySelector('.wpb-gym-sheet');
        if(!sheet)return false;
        const r=sheet.getBoundingClientRect();
        if(r.x<-1||r.right>width+1)return false;
        const valid=width>=840
          ? r.width<=742&&r.width>=520&&Math.abs(r.x+r.width/2-width/2)<4&&r.y>8&&r.bottom<height-8
          : r.width>=width-32&&r.x>=-1&&r.x<=16&&width-r.right<=16&&r.bottom>=height-2;
        if(!valid)return false;
        // Require 150ms of settled geometry. The entrance effect may briefly
        // reach the bottom before the final layout frame has committed.
        const signature=[r.x,r.y,r.width,r.height].map(n=>Math.round(n*10)/10).join(':');
        const prior=window.__b846StableBounds;
        if(!prior||prior.signature!==signature){
          window.__b846StableBounds={signature,at:performance.now()};
          return false;
        }
        return performance.now()-prior.at>=150;
      },{timeout:6500,polling:50},{width,height}); }
      catch (error) {
        const geometry=await page.evaluate(()=>{
          const sheet=document.querySelector('.wpb-gym-sheet');
          const outer=sheet?.parentElement;
          const rect=el=>{if(!el)return null;const r=el.getBoundingClientRect();
            return {x:r.x,y:r.y,width:r.width,height:r.height,right:r.right,bottom:r.bottom};};
          return {innerWidth,innerHeight,sheet:rect(sheet),parent:rect(outer),
            viewport:rect(document.querySelector('.wpb')),stable:window.__b846StableBounds,
            classes:sheet?.className,position:sheet&&getComputedStyle(sheet).position,
            transform:sheet&&getComputedStyle(sheet).transform,
            media:getComputedStyle(sheet).maxHeight};
        });
        throw new Error('Gym sheet geometry never settled: '+JSON.stringify({width,height,geometry}),{cause:error});
      }
      const sheetBounds = await page.$eval('.wpb-gym-sheet', element => {
        const r=element.getBoundingClientRect();
        return { x:r.x,y:r.y,width:r.width,height:r.height,right:r.right,bottom:r.bottom };
      });
      assert.ok(sheetBounds.x>=-1&&sheetBounds.right<=width+1,
        'gym sheet fits viewport width');
      if(width>=840){
        assert.ok(sheetBounds.width<=742&&sheetBounds.width>=520,
          'expanded gym editor uses contained dialog instead of full-width phone drawer');
        assert.ok(Math.abs(sheetBounds.x+sheetBounds.width/2-width/2)<4,
          'expanded gym editor is centered on available window');
        assert.ok(sheetBounds.y>8&&sheetBounds.bottom<height-8,
          'expanded gym editor is centered vertically and scrollable');
      }else{
        assert.ok(sheetBounds.width>=width-32&&sheetBounds.x>=-1&&sheetBounds.x<=16&&width-sheetBounds.right<=16&&sheetBounds.bottom>=height-2,
          'compact gym editor retains bottom-anchored near-full-width sheet with <=16px symmetric margins: '+JSON.stringify({width,height,sheetBounds}));
      }
      await page.$$eval('.wpb-gym-card button',buttons=>{
        const gym=buttons.find(b=>b.textContent.includes('Test Garage'));
        if(!gym)throw new Error('Test Garage gym missing');gym.click();
      });
      const box='.wpb-gym-bar-masses';
      await page.waitForSelector(box);
      const bar='input[aria-label="Barbell empty bar weight"]';
      const smith='input[aria-label="Smith empty bar weight"]';
      const change=async(selector,value)=>{
        await page.$eval(selector, input => { input.focus(); input.select(); });
        await page.keyboard.type(value);
      };
      await change(bar,'35');
      await change(smith,'0');
      assert.equal(await page.$eval(smith,e=>e.value),'0',
        'counterbalanced Smith of zero must remain editable');
      await page.click('.wpb-gym-bar-unit');
      assert.ok(Math.abs(Number(await page.$eval(bar,e=>e.value))-15.876)<.001,
        'unit change converts entered bar mass, not just label');
      await page.click('.wpb-gym-bar-unit');
      await page.click('.wpb-gym-done');
      await page.waitForFunction(()=>{
        const gym=JSON.parse(localStorage.getItem('wpb:v1')).gyms?.find(g=>g.id==='test_home');
        return gym?.barWeights?.smith===0 && Math.abs(gym.barWeights.barbell-35)<.01;
      });
      const saved=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')).gyms);
      assert.equal(saved.find(g=>g.id==='test_elsewhere').barWeights.smith,15,'other gym untouched');
      assert.deepEqual(errors,[]);
      console.log('PASS B846 gym settings edit and unit conversion '+width+'x'+height);
    }finally{await context.close();}
  }
}finally{if(browser)await browser.close();await new Promise(resolve=>server.close(resolve));}
