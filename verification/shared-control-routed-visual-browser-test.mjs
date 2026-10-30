// Post-B1000 pilot: capture actual routed Settings and Gallery UI, not only isolated controls.
// Uses an entirely synthetic seeded training plan. Screenshots are GitHub Actions artifacts;
// this checks geometry/state but does not claim subjective visual or physical Android approval.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import http from 'node:http';
import puppeteer from 'puppeteer-core';

const root=path.resolve(new URL('../',import.meta.url).pathname);
const program={
  id:'shared-pilot-visual',name:'Shared Controls Test Plan',custom:true,weeks:4,
  config:{name:'Shared Controls Test Plan',goal:'both',experience:'intermediate',
    progression:'manual',split:'custom',weeks:4,days:1,session:'s60',unit:'lb',deload:false},
  days:[{id:'upper',label:'Upper',primaryIndex:0,exercises:['bb-bench','inc-curl']}],
  overrides:{'upper:0':{sets:3,reps:'6-8',rir:'2'},'upper:1':{sets:2,reps:'10-15',rir:'2'}}
};
const state=theme=>({
  v:13,savedAt:1,saved:[program],cycles:[],history:[],perf:{},drafts:{},tombs:{},
  seenIntro:999,seenWhatsNew:999,pinnedId:program.id,theme,unit:'lb',unitChosen:true,
  experience:'intermediate',bodyweight:185,installDismissedAt:Date.now(),lastBackup:Date.now()
});
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html',
  '.json':'application/json','.png':'image/png','.svg':'image/svg+xml'};
const server=http.createServer((req,res)=>{
  const requested=new URL(req.url,'http://localhost').pathname;
  const file=path.resolve(root,'.'+(requested==='/'?'/index.html':requested));
  if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){
    res.writeHead(404);res.end();return;
  }
  res.setHeader('Content-Type',mime[path.extname(file)]||'application/octet-stream');
  res.end(fs.readFileSync(file));
});
await new Promise(done=>server.listen(0,'127.0.0.1',done));
let browser;
try{
  browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',
    headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
  const origin='http://127.0.0.1:'+server.address().port+'/';
  for(const theme of ['rose','rosewater']){
    for(const width of [320,393,834,1280]){
      const context=await browser.createBrowserContext();
      const page=await context.newPage(),errors=[];
      page.on('pageerror',error=>errors.push(error.message));
      try{
        await page.setViewport({width,height:width===320?740:850,
          deviceScaleFactor:1,isMobile:width<=393,hasTouch:width<=393});
        await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
        await page.evaluateOnNewDocument(seed=>
          localStorage.setItem('wpb:v1',JSON.stringify(seed)),state(theme));
        await page.goto(origin,{waitUntil:'networkidle0'});
        await page.waitForSelector('.wpb-home');
        await page.click('[data-tab="settings"]');
        await page.waitForSelector('.wpb-settings');

        const layout=()=>page.evaluate(()=>{
          const root=document.documentElement;
          return {width:innerWidth,scrollWidth:root.scrollWidth};
        });
        const top=await layout();
        assert.ok(top.scrollWidth<=width+2,'Settings spills horizontally: '+JSON.stringify({theme,width,top}));
        const nav=await page.$$eval('.wpb-settings-navigation',nodes=>nodes.map(node=>{
          const rect=node.getBoundingClientRect();
          return {text:node.textContent.trim(),height:rect.height,width:rect.width,
            selected:node.getAttribute('data-action')};
        }));
        assert.equal(nav.length,4,'All four Settings destination actions are rendered');
        assert.ok(nav.every(row=>row.height>=48&&row.selected==='tonal'),
          'Shared Settings navigation has consistent real hit targets: '+JSON.stringify(nav));
        if(width===320||width===834){
          await page.screenshot({path:path.join(root,`verification/shared-pilot-settings-top-${theme}-${width}.png`)});
        }
        const galleryButton=await page.evaluateHandle(()=>[...document.querySelectorAll('.wpb-settings-navigation')]
          .find(el=>el.textContent.includes('Browse shared programs')));
        assert.ok(galleryButton.asElement(),'Settings gallery navigation is available');
        await galleryButton.asElement().evaluate(el=>el.scrollIntoView({block:'center'}));
        if(width===320||width===834){
          await page.screenshot({path:path.join(root,`verification/shared-pilot-settings-actions-${theme}-${width}.png`)});
        }
        await galleryButton.asElement().click();
        await galleryButton.dispose();
        await page.waitForSelector('.wpb-gallery-title');
        assert.match(await page.$eval('.wpb-gallery-title',node=>node.textContent),/Shared programs/);
        assert.ok(await page.$eval('.wpb-gallery-back',el=>{
          const r=el.getBoundingClientRect();
          return r.width>=48&&r.height>=48&&el.getAttribute('aria-label')==='Back';
        }),'Gallery uses the shared accessible Back control');
        const filterCount=await page.$$('.wpb-gallery-chip');
        assert.ok(filterCount.length>=3,'Gallery filter choices are actually rendered');
        const picked=await page.$$eval('.wpb-gallery-chip',nodes=>
          nodes.filter(n=>n.getAttribute('aria-pressed')==='true').length);
        assert.ok(picked>=3,'Three Gallery filter dimensions expose selected state');
        if(width===320||width===834){
          await page.screenshot({path:path.join(root,`verification/shared-pilot-gallery-${theme}-${width}.png`)});
        }
        const galleryLayout=await layout();
        assert.ok(galleryLayout.scrollWidth<=width+2,
          'Gallery spills horizontally: '+JSON.stringify({theme,width,galleryLayout}));
        assert.deepEqual(errors,[],'No routed Settings/Gallery browser exceptions: '+theme+' '+width);
        console.log('PASS routed shared-control visuals: '+theme+' '+width+'px');
      }finally{await context.close();}
    }
  }
}finally{
  await browser?.close();
  await new Promise(done=>server.close(done));
}
