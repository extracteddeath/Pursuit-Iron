// Rendered census for primary destinations, complementing source-level JSX audit.
// It reads all visible DOM elements and computed styles, without mutating saved workouts.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import http from 'node:http';
import puppeteer from 'puppeteer-core';
const root=path.resolve(new URL('../',import.meta.url).pathname);
const program={
 id:'ui-audit-plan',name:'UI Audit Plan',custom:true,weeks:4,
 config:{name:'UI Audit Plan',goal:'both',experience:'intermediate',
 progression:'manual',split:'custom',weeks:4,days:1,session:'s60',unit:'lb',deload:false},
 days:[{id:'upper',label:'Upper',primaryIndex:0,exercises:['bb-bench','inc-curl']}],
 overrides:{'upper:0':{sets:4,reps:'6-8',rir:'2'},'upper:1':{sets:3,reps:'10-15',rir:'2'}}
};
const state=theme=>({v:13,savedAt:1,saved:[program],cycles:[],history:[],perf:{},
 drafts:{},tombs:{},seenIntro:999,seenWhatsNew:999,pinnedId:program.id,theme,unit:'lb',
 unitChosen:true,experience:'intermediate',bodyweight:185,installDismissedAt:Date.now(),
 lastBackup:Date.now()});
const mime={'.html':'text/html','.js':'text/javascript','.css':'text/css',
 '.json':'application/json','.png':'image/png','.svg':'image/svg+xml'};
const server=http.createServer((req,res)=>{
 const url=new URL(req.url,'http://localhost'),file=path.resolve(root,'.'+(url.pathname==='/'?'/index.html':url.pathname));
 if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){
  res.writeHead(404);res.end();return;
 }
 res.setHeader('Content-Type',mime[path.extname(file)]||'application/octet-stream');
 res.end(fs.readFileSync(file));
});
const routes=[['home','.wpb-home'],['plan','.wpb-plan-view'],
 ['progress','.wpb-progress'],['profile','.wpb-profile'],['settings','.wpb-settings']];
await new Promise(done=>server.listen(0,'127.0.0.1',done));
let browser;
try{
 browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',
  headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
 const observations=[];
 for(const theme of ['rose','rosewater'])for(const width of [320,393,834,1280]){
  const context=await browser.createBrowserContext(),page=await context.newPage(),errors=[];
  try{
   page.on('pageerror',e=>errors.push(e.message));
   await page.setViewport({width,height:850,isMobile:width<=393,hasTouch:width<=393});
   await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
   await page.evaluateOnNewDocument(seed=>localStorage.setItem('wpb:v1',JSON.stringify(seed)),state(theme));
   await page.goto('http://127.0.0.1:'+server.address().port+'/',{waitUntil:'networkidle0'});
   await page.waitForSelector('.wpb-home');
   for(const [tab,selector] of routes){
    if(tab!=='home')await page.click('[data-tab="'+tab+'"]');
    await page.waitForSelector(selector,{visible:true});
    const snapshot=await page.$eval(selector,container=>{
     const els=[container,...container.querySelectorAll('*')];
     const data=[],counts={total:0,visible:0,nativeInteractive:0,sharedControls:0,
      unsharedButtons:0,surfaceCandidates:0,dynamicLayoutCells:0};
     for(const el of els){
      counts.total++;
      const style=getComputedStyle(el),rect=el.getBoundingClientRect();
      if(rect.width<=0||rect.height<=0||style.display==='none'||style.visibility==='hidden')continue;
      counts.visible++;
      const classes=[...el.classList],tag=el.localName;
      const native=['button','input','select','textarea','a','summary'].includes(tag);
      const shared=classes.some(x=>/^wpb-ui-(action|card|choice|control|field|metric|sheet|section)/.test(x));
      const surface=classes.some(x=>/card|tile|panel|hero|surface/.test(x));
      if(native)counts.nativeInteractive++;
      if(shared)counts.sharedControls++;
      if(tag==='button'&&!shared)counts.unsharedButtons++;
      if(surface&&!shared)counts.surfaceCandidates++;
      if(classes.some(x=>/chart|bar|timer|set-(?:row|cell)/.test(x)))counts.dynamicLayoutCells++;
      data.push({tag,classes,aria:el.getAttribute('aria-label')||null,role:el.getAttribute('role')||null,
       css:{display:style.display,background:style.backgroundColor,color:style.color,
        fontSize:style.fontSize,fontWeight:style.fontWeight, padding:style.padding,
        border:style.borderTopColor+' '+style.borderTopWidth,
        radius:style.borderTopLeftRadius,minHeight:style.minHeight}});
     }
     return {counts,elements:data,viewportScrollWidth:document.documentElement.scrollWidth};
    });
    assert.ok(snapshot.counts.total>20,'Route rendered too few DOM elements: '+tab);
    assert.ok(snapshot.counts.visible>10,'Route content invisible: '+tab);
    observations.push({theme,width,tab,...snapshot});
   }
   assert.deepEqual(errors,[],'Runtime errors in route audit: '+JSON.stringify({theme,width,errors}));
  }finally{await context.close();}
 }
 const totals=observations.map(({theme,width,tab,counts,viewportScrollWidth})=>
  ({theme,width,tab,...counts,viewportScrollWidth}));
 fs.writeFileSync(path.join(root,'verification/ui-dom-ownership-audit.json'),
  JSON.stringify({scope:'All visible element nodes in five mounted root destinations under eight theme/viewport combinations; other routes and modal states require additional sweeps',totals,observations},null,2)+'\n');
 console.log('DOM_ELEMENT_AUDIT',JSON.stringify(totals));
 assert.equal(observations.length,40,'Five root routes at four widths and both themes');
}finally{await browser?.close();await new Promise(done=>server.close(done));}
