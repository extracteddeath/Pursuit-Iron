import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import puppeteer from 'puppeteer-core';

// R65: exercise the real shell and route owners at compact, medium and expanded
// windows. No simulated DOM rail or forked state; each test seeds ordinary wpb:v1.
const root=path.resolve(new URL('../',import.meta.url).pathname);
const program={
  id:'r65-adaptive',name:'Adaptive Layout Fixture',custom:true,weeks:4,
  config:{name:'Adaptive Layout Fixture',goal:'both',experience:'intermediate',
    progression:'manual',split:'custom',weeks:4,days:1,session:'s60',unit:'lb',deload:false},
  days:[{id:'upper',label:'Upper',primaryIndex:0,exercises:['bb-bench','inc-curl']}],
  overrides:{'upper:0':{sets:4,reps:'6-8',rir:'2'},'upper:1':{sets:3,reps:'10-15',rir:'2'}}
};
const store=theme=>({
  v:13,savedAt:1,saved:[program],cycles:[],history:[],perf:{},drafts:{},tombs:{},
  seenIntro:999,seenWhatsNew:999,pinnedId:program.id,theme,unit:'lb',
  unitChosen:true,experience:'intermediate',bodyweight:185,
  installDismissedAt:Date.now(),lastBackup:Date.now()
});
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html',
  '.json':'application/json','.png':'image/png','.svg':'image/svg+xml',
  '.webmanifest':'application/manifest+json'};
const server=http.createServer((req,res)=>{
  const name=new URL(req.url,'http://localhost').pathname;
  const file=path.resolve(root,'.'+(name==='/'?'/index.html':name));
  if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){
    res.writeHead(404);res.end();return;
  }
  res.setHeader('Content-Type',mime[path.extname(file)]||'application/octet-stream');
  res.end(fs.readFileSync(file));
});
await new Promise(resolve=>server.listen(8798,'127.0.0.1',resolve));
let browser;
try{
  browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',
    headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
  const results=[];
  for(const theme of ['rose','rosewater']){
    for(const [width,height,wide] of [[393,852,false],[900,480,false],[900,1000,true],[1280,850,true]]){
      const context=await browser.createBrowserContext();
      const page=await context.newPage();
      const errors=[];page.on('pageerror',e=>errors.push(e.message));
      try{
        await page.setViewport({width,height,deviceScaleFactor:1,isMobile:!wide,hasTouch:!wide});
        await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
        await page.evaluateOnNewDocument(state=>localStorage.setItem('wpb:v1',JSON.stringify(state)),store(theme));
        await page.goto('http://127.0.0.1:8798/',{waitUntil:'networkidle0'});
        await page.waitForSelector('.wpb-tabbar');
        const geometry=()=>page.evaluate(()=>{
          const root=document.querySelector('.wpb');
          const frame=[...root.children].find(node=>node.querySelector(':scope > .wpb-tabbar'));
          const bar=document.querySelector('.wpb-tabbar');
          const bounds=el=>{const r=el.getBoundingClientRect();
            return {x:r.x,y:r.y,width:r.width,height:r.height,right:r.right,bottom:r.bottom};};
          const first=frame?.firstElementChild;
          const tabs=[...bar.querySelectorAll('[data-tab]')];
          return {frame:bounds(frame),bar:bounds(bar),content:bounds(first),
            direction:getComputedStyle(bar).flexDirection,
            tabs:tabs.map(el=>({...bounds(el),label:el.getAttribute('aria-label'),
              hit:el.contains(document.elementFromPoint(
                el.getBoundingClientRect().x+el.getBoundingClientRect().width/2,
                el.getBoundingClientRect().y+el.getBoundingClientRect().height/2))})),
            overflow:document.documentElement.scrollWidth>innerWidth+2};
        });
        const initial=await geometry();
        assert.equal(initial.tabs.length,5,'All five actual navigation controls exist');
        assert.ok(initial.tabs.every(tab=>tab.hit),'Nav targets remain clickable: '+JSON.stringify(initial));
        assert.equal(initial.overflow,false,'No horizontal viewport overflow: '+JSON.stringify(initial));
        if(wide){
          assert.equal(initial.direction,'column','Expanded screen must use an actual navigation rail');
          assert.ok(initial.frame.width>780,'Expanded content shell is no longer locked to 460px');
          assert.ok(initial.bar.width>=95&&initial.bar.height>initial.bar.width*2,
            'Rail is a vertical, independently sized surface');
          assert.ok(initial.bar.right<=initial.content.x+3,
            'Rail occupies its own column without covering content');
        }else{
          assert.equal(initial.direction,'row','Phones/short landscape retain bottom navigation');
          assert.ok(initial.frame.width<=462,'Phone shell remains its existing maximum');
          assert.ok(initial.bar.y>initial.content.y+initial.content.height-10,
            'Phone navigation stays below content');
        }
        for(const route of ['plan','progress','profile','settings','home']){
          await page.click('[data-tab="'+route+'"]');
          await page.waitForSelector('[data-view-frame="'+route+'"]');
          const state=await geometry();
          assert.equal(state.direction,initial.direction,
            'Navigation position stays stable when switching to '+route);
          assert.equal(state.overflow,false,'No document overflow on '+route);
        }
        if(wide){
          await page.focus('[data-tab="plan"]');
          await page.keyboard.press('ArrowDown');
          await page.waitForFunction(()=>document.querySelector('[data-tab="progress"]')
            ?.getAttribute('aria-current')==='page');
          await page.keyboard.press('ArrowUp');
          await page.waitForFunction(()=>document.querySelector('[data-tab="plan"]')
            ?.getAttribute('aria-current')==='page');
        }
        await page.screenshot({path:path.join(root,'verification/r65-'+theme+'-'+width+'x'+height+'-phone.png')});
        assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')).saved),[program],
          'Navigation layout does not mutate saved prescriptions');
        assert.deepEqual(errors,[],'No runtime errors: '+errors.join('; '));
        results.push({theme,width,height,mode:wide?'rail':'bottom',frame:initial.frame.width,
          rail:initial.bar.width,targets:initial.tabs.length});
      }finally{await context.close();}
    }
  }
  console.log('PASS R65 adaptive shell, directional keyboard navigation, route preservation: '+JSON.stringify(results));
}finally{
  if(browser)await browser.close();
  await new Promise(resolve=>server.close(resolve));
}
