import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import puppeteer from 'puppeteer-core';

// R53: two real saved-palette contexts, same program, same phone geometry.
// Both layouts must resolve the same semantic surface roles. Screenshot
// artifacts are PAIRS rather than cherry-picked screenshots of one theme.
const root=path.resolve(new URL('../',import.meta.url).pathname);
const program={
  id:'r53-surface',name:'R53 Plan Surface',custom:true,weeks:4,
  config:{name:'R53 Plan Surface',goal:'both',experience:'intermediate',progression:'double',split:'custom',weeks:4,days:2,session:'s60',unit:'lb',deload:false},
  days:[
    {id:'upper',label:'Upper',primaryIndex:0,exercises:['bb-bench','inc-curl']},
    {id:'lower',label:'Lower',primaryIndex:1,exercises:['back-squat','leg-press']}
  ],
  overrides:{
    'upper:0':{sets:4,reps:'6-8',rir:'2'},'upper:1':{sets:3,reps:'10-15',rir:'2'},
    'lower:0':{sets:4,reps:'5-8',rir:'2'},'lower:1':{sets:3,reps:'8-12',rir:'2'}
  }
};
const initial=theme=>({
  v:13,savedAt:1,saved:[program],cycles:[],history:[],perf:{},drafts:{},tombs:{},
  seenIntro:999,seenWhatsNew:999,pinnedId:program.id,theme,
  unit:'lb',unitChosen:true,experience:'intermediate',bodyweight:185,
  installDismissedAt:Date.now(),lastBackup:Date.now()
});
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html','.json':'application/json','.png':'image/png','.svg':'image/svg+xml','.webmanifest':'application/manifest+json'};
const server=http.createServer((req,res)=>{
  const name=new URL(req.url,'http://localhost').pathname;
  const file=path.resolve(root,'.'+(name==='/'?'/index.html':name));
  if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){res.writeHead(404);res.end();return;}
  res.setHeader('Content-Type',mime[path.extname(file)]||'application/octet-stream');
  res.end(fs.readFileSync(file));
});
await new Promise(resolve=>server.listen(8795,'127.0.0.1',resolve));
let browser;
try{
  browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
  const captures={rose:{},rosewater:{}},measurements={};
  for(const theme of ['rose','rosewater']){
    const context=await browser.createBrowserContext();
    const page=await context.newPage();
    const errors=[];
    page.on('pageerror',e=>errors.push(e.message));
    await page.setViewport({width:393,height:852,deviceScaleFactor:1,isMobile:true,hasTouch:true});
    await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
    await page.evaluateOnNewDocument(store=>{
      localStorage.setItem('wpb:v1',JSON.stringify(store));
    },initial(theme));
    await page.goto('http://127.0.0.1:8795/',{waitUntil:'networkidle0'});
    await page.waitForSelector('.wpb-tabbar');
    const surfaces={};
    for(const tab of ['home','plan','progress','profile','settings']){
      await page.click('[data-tab="'+tab+'"]');
      await page.waitForSelector('[data-view-frame="'+tab+'"][data-pi-surface]');
      await page.waitForFunction(t=>document.querySelector('[data-view-frame="'+t+'"]')?.style.getPropertyValue('--pi-surface-opacity')==='1',{},tab);
      const basic=await page.evaluate(t=>{
        const root=document.querySelector('.wpb');
        const nav=root.querySelector('.wpb-tabbar');
        return {theme:JSON.parse(localStorage.getItem('wpb:v1')).theme,
          current:nav.querySelector('[aria-current="page"]')?.dataset.tab,
          overflow:root.scrollWidth>root.clientWidth+1,
          nav:getComputedStyle(nav).backgroundColor,
          page:getComputedStyle(document.querySelector(t==='plan'?'.wpb-plan-view':'.wpb-'+t)).backgroundColor};
      },tab);
      assert.equal(basic.theme,theme,'Selected saved theme persists across destinations');
      assert.equal(basic.current,tab,'Correct bottom navigation destination remains selected');
      assert.ok(!basic.overflow,'No horizontal scrolling at 393px: '+JSON.stringify(basic));
      if(tab==='plan'){
        Object.assign(basic,await page.evaluate(()=>{
          const el=sel=>document.querySelector(sel);
          const css=(sel)=>{const x=el(sel);return x?getComputedStyle(x).backgroundColor:null};
          const chip=el('.wpb-plan-week-chip[aria-pressed="true"]');
          const all=[...document.querySelectorAll('.wpb-plan-week-card')];
          return {
            weekCard:css('.wpb-plan-week-card'),
            weekImage:el('.wpb-plan-week-card')&&getComputedStyle(el('.wpb-plan-week-card')).backgroundImage,
            nested:css('.wpb-plan-session-link'),weekCount:all.length,
            currentCount:all.filter(x=>x.dataset.currentWeek==='true').length,
            chip:chip?.textContent.trim()||null,
            chipSelected:chip?.getAttribute('aria-pressed')==='true'
          };
        }));
        assert.ok(basic.weekCount>=2&&basic.currentCount===1&&basic.weekImage==='none',
          'Plan has real semantic week cards with exactly one current week: '+JSON.stringify(basic));
        assert.ok(basic.nested&&basic.chipSelected,'Plan nested row and active week are accessible: '+JSON.stringify(basic));
      }
      captures[theme][tab]=Buffer.from(await page.screenshot({type:'png'}));
      surfaces[tab]=basic;
    }
    assert.equal(surfaces.plan.weekCard,await page.evaluate(()=>{
      const card=document.querySelector('.wpb-settings .wpb-settings-card');
      return card&&getComputedStyle(card).backgroundColor;
    }), 'Settings and Plan use the SAME neutral card role: '+JSON.stringify(surfaces.plan));
    await page.click('[data-tab="home"]');
    await page.waitForSelector('.hp-open');
    await page.click('.hp-open');
    await page.waitForSelector('.wpb-program .wpb-week-rail');
    const detail=await page.evaluate(()=>{
      const root=document.querySelector('.wpb-program');
      const week=[...root.querySelectorAll('.wpb-week-rail>button')];
      const phase=root.querySelector('.wpb-phase-block>div>div');
      return {
        overflow:root.scrollWidth>root.clientWidth+1,
        activeWeeks:week.filter(b=>b.getAttribute('aria-pressed')==='true').length,
        weekCount:week.length,
        phase:phase&&getComputedStyle(phase).backgroundColor,
        phaseImage:phase&&getComputedStyle(phase).backgroundImage
      };
    });
    assert.ok(!detail.overflow&&detail.weekCount>1&&detail.activeWeeks===1,
      'Full Program has one semantic selected week and no overflow: '+JSON.stringify(detail));
    captures[theme].program=Buffer.from(await page.screenshot({type:'png'}));
    assert.equal(errors.length,0,'No runtime errors while checking paired themes: '+errors.join('; '));
    measurements[theme]={...surfaces,program:detail};
    await context.close();
  }
  // Build genuine 1:1 side-by-side PNGs for visual signoff (left dark / right light),
  // not two disjoint galleries where differences are difficult to compare.
  const canvasPage=await browser.newPage();
  const pairs=['home','plan','progress','profile','settings','program'];
  for(const tab of pairs){
    const result=await canvasPage.evaluate(async({dark,light})=>{
      const load=src=>new Promise((resolve,reject)=>{const img=new Image();img.onload=()=>resolve(img);img.onerror=reject;img.src=src;});
      const [a,b]=await Promise.all([load(dark),load(light)]);
      const c=document.createElement('canvas');c.width=a.width+b.width;c.height=Math.max(a.height,b.height);
      const ctx=c.getContext('2d');ctx.drawImage(a,0,0);ctx.drawImage(b,a.width,0);
      return c.toDataURL('image/png').split(',')[1];
    },{
      dark:'data:image/png;base64,'+captures.rose[tab].toString('base64'),
      light:'data:image/png;base64,'+captures.rosewater[tab].toString('base64')
    });
    fs.writeFileSync(path.join(root,'verification/r53-'+tab+'-phone.png'),Buffer.from(result,'base64'));
  }
  await canvasPage.close();
  console.log('PASS R53: 6 paired Rose dark/Rosewater light screenshots at 393px; matching semantic card colors, active week semantics, no page overflow; '+JSON.stringify(measurements));
}finally{
  if(browser)await browser.close();
  await new Promise(resolve=>server.close(resolve));
}
