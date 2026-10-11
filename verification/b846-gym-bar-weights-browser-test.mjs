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
      await page.$$eval('.wpb-gym-card button',buttons=>{
        const gym=buttons.find(b=>b.textContent.includes('Test Garage'));
        if(!gym)throw new Error('Test Garage gym missing');gym.click();
      });
      const box='.wpb-gym-bar-masses';
      await page.waitForSelector(box);
      const bar='input[aria-label="Barbell empty bar weight"]';
      const smith='input[aria-label="Smith empty bar weight"]';
      const change=async(selector,value)=>{
        await page.click(selector,{clickCount:3});
        await page.keyboard.press('Control+A');
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
