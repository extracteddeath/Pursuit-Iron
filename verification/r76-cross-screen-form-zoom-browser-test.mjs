import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import puppeteer from 'puppeteer-core';

// R76: real Profile/Progress/Settings routes, not a static UI mock.
// Text enlargement is distinct from layout viewport zoom, and no app data
// may change from inspection alone. Keep workout inputs out of this scope.
const root=path.resolve(new URL('../',import.meta.url).pathname);
const program={id:'r76-access',name:'Accessible Program',custom:true,weeks:4,
  config:{name:'Accessible Program',goal:'both',experience:'intermediate',
    progression:'manual',split:'custom',weeks:4,days:1,session:'s60',unit:'lb',deload:false},
  days:[{id:'upper',label:'Upper',primaryIndex:0,exercises:['bb-bench','inc-curl']}],
  overrides:{'upper:0':{sets:3,reps:'6-8',rir:'2'},'upper:1':{sets:2,reps:'10-15',rir:'2'}}};
const initial=theme=>({v:13,savedAt:1,saved:[program],cycles:[],history:[],perf:{},
  drafts:{},tombs:{},seenIntro:999,seenWhatsNew:999,pinnedId:program.id,
  theme,unit:'lb',unitChosen:true,experience:'intermediate',bodyweight:185,
  installDismissedAt:Date.now(),lastBackup:Date.now()});
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html',
  '.json':'application/json','.webmanifest':'application/manifest+json',
  '.png':'image/png','.svg':'image/svg+xml'};
const server=http.createServer((req,res)=>{
  const name=new URL(req.url,'http://localhost').pathname;
  const file=path.resolve(root,'.'+(name==='/'?'/index.html':name));
  if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){
    res.writeHead(404);res.end();return;
  }
  res.setHeader('Content-Type',mime[path.extname(file)]||'application/octet-stream');
  res.end(fs.readFileSync(file));
});
await new Promise(resolve=>server.listen(8807,'127.0.0.1',resolve));
const settle=page=>page.evaluate(()=>new Promise(done=>requestAnimationFrame(
  ()=>requestAnimationFrame(done))));
let browser;
try{
  browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',
    headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
  for(const theme of ['rose','rosewater']) for(const [width,height] of
      [[320,740],[393,852],[900,1000],[1280,850]]){
    const context=await browser.createBrowserContext(),page=await context.newPage(),errors=[];
    page.on('pageerror',error=>errors.push(error.message));
    try{
      await page.setViewport({width,height,deviceScaleFactor:1,isMobile:width<600,hasTouch:width<600});
      await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
      await page.evaluateOnNewDocument(data=>localStorage.setItem('wpb:v1',JSON.stringify(data)),initial(theme));
      await page.goto('http://127.0.0.1:8807/',{waitUntil:'networkidle0'});
      await page.waitForSelector('.wpb-tabbar');
      for(const [route,selector,actions] of [
        ['profile','.wpb-profile',['.wpb-profile-edit']],
        ['progress','.wpb-progress',['.wpb-premium-tabs>button']],
        ['settings','.wpb-settings',['.wpb-settings-jump-control select']]
      ]){
        await page.click('[data-tab="'+route+'"]');
        await page.waitForSelector(selector);
        await settle(page);
        if(width<=393&&route==='profile'){
          const columns=await page.$eval('.wpb-profile-stat-grid',
            el=>getComputedStyle(el).gridTemplateColumns.trim().split(/\s+/).length);
          assert.equal(columns,3,'Profile keeps its compact three-stat layout at ordinary text size');
        }
        if(width<=393&&route==='progress'){
          const display=await page.$eval('.wpb-premium-tabs',el=>getComputedStyle(el).display);
          assert.equal(display,'flex','Progress retains its original flex segmented track at ordinary text size');
        }
        const before=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')).saved);
        // Use the computed initial text size, then modify ONLY typography.
        // Restore every exact inline declaration (including priority) later.
        await page.evaluate(sel=>{
          window.__r76Text=[...document.querySelectorAll(sel+', '+sel+' *')]
            .filter(el=>el instanceof HTMLElement)
            .map(el=>({el,size:parseFloat(getComputedStyle(el).fontSize),
              old:el.style.getPropertyValue('font-size'),
              priority:el.style.getPropertyPriority('font-size')}));
          for(const item of window.__r76Text)
            if(Number.isFinite(item.size))
              item.el.style.setProperty('font-size',item.size*2+'px','important');
        },selector);
        await settle(page);
        const geometry=await page.evaluate(({sel,actions})=>{
          const area=document.querySelector(sel),rect=area.getBoundingClientRect();
          const measure=selector=>[...area.querySelectorAll(selector)].map(el=>{
            el.scrollIntoView({block:'nearest'});
            const r=el.getBoundingClientRect();
            return {name:el.getAttribute('aria-label')||el.textContent.trim().slice(0,50),
              width:r.width,height:r.height,rects:el.getClientRects().length,
              disabled:el.disabled};
          });
          const summary=area.querySelector('.wpb-profile-stat-grid, .wpb-premium-tabs');
          const columns=summary?getComputedStyle(summary).gridTemplateColumns.trim().split(/\s+/).length:null;
          return {pageOverflow:document.documentElement.scrollWidth>innerWidth+2,
            areaOverflow:area.scrollWidth>area.clientWidth+2,
            width:rect.width,columns,
            actionGroups:actions.map(measure)};
        },{sel:selector,actions});
        assert.equal(geometry.pageOverflow,false,route+' has no document horizontal overflow at 200%: '+JSON.stringify(geometry));
        assert.equal(geometry.areaOverflow,false,route+' has no panel horizontal overflow at 200%: '+JSON.stringify(geometry));
        if(width<=393&&route==='profile')
          assert.equal(geometry.columns,1,'200% Profile readouts use whole-word rows instead of three cramped columns');
        if(width<=393&&route==='progress')
          assert.equal(geometry.columns,2,'200% Progress tabs use two readable columns instead of four split words');
        assert.ok(geometry.actionGroups.every(group=>group.length>0),
          route+' has its real action controls: '+JSON.stringify(geometry));
        for(const group of geometry.actionGroups) for(const target of group){
          assert.ok(target.width>=44&&target.height>=44,
            route+' action remains a practical 44px target: '+JSON.stringify(target));
        }
        await page.evaluate(()=>{
          for(const {el,old,priority} of window.__r76Text)
            if(old)el.style.setProperty('font-size',old,priority);
            else el.style.removeProperty('font-size');
          delete window.__r76Text;
        });
        assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')).saved),
          before,route+' typography inspection never edits the saved program');
        await page.screenshot({path:path.join(root,'verification/r76-'+route+'-'+theme+'-'+width+'-phone.png')});
      }
      assert.deepEqual(errors,[],'Browser routes have no uncaught errors');
      console.log('PASS R76 '+theme+' '+width+'x'+height+
        ': Profile, Progress and Settings enlarged labels/targets and unchanged saved plan');
    }finally{await context.close();}
  }
}finally{await browser?.close();await new Promise(resolve=>server.close(resolve));}
