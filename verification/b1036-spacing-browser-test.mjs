// B1036: spacing and sizing are measured, not eyeballed. One 12px rhythm between root cards,
// one 16px card inset, labels never pressed against their control's edge, aligned set rows,
// section titles on the card edge, and 48px secondary actions.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import puppeteer from 'puppeteer-core';

const root=path.resolve(new URL('../',import.meta.url).pathname);
const program={id:'home-entry',name:'My Program',custom:true,weeks:6,schedule:{'1':'upper','3':'lower','5':'upper'},
 config:{unit:'lb',goal:'both',experience:'intermediate',progression:'double',weeks:6,session:'s60',deload:false},
 days:[{id:'upper',label:'Upper',primaryIndex:0,exercises:['bb-bench','inc-curl']},{id:'lower',label:'Lower',primaryIndex:0,exercises:['back-squat','bb-bench','ohp','inc-curl']}]};
const D=86400000, now=Date.now();
const QE=["barbell","dumbbell","bench","cable","machine","smith","ezbar","pullup","dip","kettlebell","bands"];
const rt=(id,name,ex,c)=>({id,name,quick:true,artifactType:'freestyle',engineV:1,createdAt:c,config:{name,goal:'both',progression:'manual',split:null,days:1,session:'s60',experience:'intermediate',weeks:1,equipment:QE,focus:{},reduce:[],barbellCap:null,noBodyweight:false,deload:false,percentScheme:null},days:[{id:'d'+id,label:name,exercises:ex,primaryIndex:0}],overrides:{}});
const sess=(i,ago,w)=>({id:'s'+i,programId:program.id,programName:program.name,dayId:'upper',dayLabel:'Upper',weekIndex:1,date:now-ago*D-3600000,unit:'lb',durationMin:52,setsDone:6,totalSets:6,
 perf:{'bb-bench':{weight:w,reps:6,sets:[1,2,3].map(()=>({w,r:6,done:true,rir:2,rirReported:true}))},'inc-curl':{weight:30,reps:12,sets:[1,2,3].map(()=>({w:30,r:12,done:true,rir:2,rirReported:true}))}}});
const history=[{...sess(9,3,100),programId:'r2'},sess(1,23,165),sess(2,16,170),sess(3,9,175),sess(4,8,175),sess(5,2,185),sess(6,0,190)];
const state={v:13,savedAt:1,saved:[program,rt('r1','Arm pump',['inc-curl','lat-raise'],now-9*D),rt('r2','Hotel gym push',['bb-bench','ohp','lat-raise','inc-curl'],now-20*D)],pinnedId:program.id,cycles:[],history,perf:{},drafts:{},tombs:{},seenIntro:999,seenWhatsNew:999,unit:'lb',unitChosen:true,experience:'intermediate',bodyweight:185,installDismissedAt:now,lastBackup:now,theme:process.env.THEME||'amethyst'};
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html','.json':'application/json','.svg':'image/svg+xml','.png':'image/png'};

const server=http.createServer((req,res)=>{const n=new URL(req.url,'http://x').pathname;const f=path.resolve(root,'.'+(n==='/'?'/index.html':n));
  if(!f.startsWith(root)||!fs.existsSync(f)||!fs.statSync(f).isFile()){res.writeHead(404);return res.end();}
  res.setHeader('Content-Type',mime[path.extname(f)]||'application/octet-stream');res.end(fs.readFileSync(f));});
await new Promise(done=>server.listen(0,'127.0.0.1',done));
const wait=ms=>new Promise(r=>setTimeout(r,ms));
let browser;
try{
  browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
  for(const width of [320,393]){
    const page=await browser.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));
    await page.setViewport({width,height:844,deviceScaleFactor:1,isMobile:true,hasTouch:true});
    await page.evaluateOnNewDocument(s=>{if(!sessionStorage.getItem('b1036')){sessionStorage.setItem('b1036','1');localStorage.setItem('wpb:v1',JSON.stringify(s));}},state);
    await page.goto('http://127.0.0.1:'+server.address().port+'/',{waitUntil:'load'});
    await page.waitForSelector('.wpb-home-hero',{timeout:60000});await wait(900);
    const tab=async id=>{await page.click(`[data-tab="${id}"]`);await wait(700);};
    const ctx=JSON.stringify({width});

    // Home: every root card sits on the same 12px rhythm, and the hero's content shares the
    // 16px inset of the cards around it.
    const home=await page.evaluate(()=>{
      const blocks=[...document.querySelector('.wpb-home-content').children].filter(e=>e.getBoundingClientRect().height>0);
      const gaps=blocks.slice(1).map((b,i)=>Math.round(b.getBoundingClientRect().top-blocks[i].getBoundingClientRect().bottom));
      const inset=el=>{const r=el.getBoundingClientRect();return parseFloat(getComputedStyle(el).paddingLeft)+parseFloat(getComputedStyle(el).borderLeftWidth)+r.left;};
      const strip=document.querySelector('.wpb-home-strip-days').getBoundingClientRect();
      return {gaps,heroInset:inset(document.querySelector('.wpb-home-hero')),weekInset:inset(document.querySelector('.wpb-home-strip')),
        dates:[...document.querySelectorAll('.wpb-home-strip-date')].map(d=>{const r=d.getBoundingClientRect();return [r.left,r.right]}),strip:[strip.left,strip.right]};
    });
    assert.ok(home.gaps.length>=4&&home.gaps.every(g=>Math.abs(g-12)<=1),'Home root cards share one 12px rhythm '+JSON.stringify({width,gaps:home.gaps}));
    assert.ok(Math.abs(home.heroInset-home.weekInset)<=1,'Hero content shares the 16px card inset '+JSON.stringify({width,home}));
    for(let i=1;i<home.dates.length;i++)assert.ok(home.dates[i][0]-home.dates[i-1][1]>=6,'Week-strip days keep room for the today ring '+ctx);
    assert.ok(home.dates[0][0]>=home.strip[0]-.5&&home.dates.at(-1)[1]<=home.strip[1]+.5,'Week-strip days stay inside the card '+ctx);

    // Progress: section tabs fit without overflow and every label keeps an inset.
    await tab('progress');
    const tabs=await page.evaluate(()=>{const g=document.querySelector('.wpb-progress .wpb-premium-tabs');
      return {overflow:g.scrollWidth-g.clientWidth,insets:[...g.children].map(b=>{const r=b.getBoundingClientRect();const rg=document.createRange();rg.selectNodeContents(b);const t=rg.getBoundingClientRect();return Math.min(t.left-r.left,r.right-t.right);})};});
    assert.ok(tabs.overflow<=0,'Progress section tabs fit their row '+JSON.stringify({width,tabs}));
    assert.ok(tabs.insets.every(x=>x>=3.5),'Progress tab labels keep an inset '+JSON.stringify({width,tabs}));
    const rows=await page.$$eval('.wpb-history-session-toggle',bs=>bs.map(b=>b.getBoundingClientRect().height));
    assert.ok(rows.length&&rows.every(h=>h>=48),'Workout history rows are 48px targets '+ctx);

    // Settings: section titles sit on the card edge, segmented labels keep a 12px inset (2x2 on phones).
    await tab('settings');
    const settings=await page.evaluate(()=>{const t=document.querySelector('.wpb-settings-title');let card=t.nextElementSibling;
      const seg=document.querySelector('.wpb-experience-segmented');
      return {title:t.getBoundingClientRect().left,card:card.getBoundingClientRect().left,
        seg:[...seg.children].map(b=>{const r=b.getBoundingClientRect();const rg=document.createRange();rg.selectNodeContents(b);const x=rg.getBoundingClientRect();return Math.min(x.left-r.left,r.right-x.right);}),
        small:[...document.querySelectorAll('.wpb-settings .wpb-ghost-action, .wpb-settings .wpb-secondary-action')].filter(b=>b.getBoundingClientRect().height>0&&b.getBoundingClientRect().height<48).length};});
    assert.ok(Math.abs(settings.title-settings.card)<=1,'Settings section titles align with their cards '+JSON.stringify({width,settings}));
    assert.ok(settings.seg.every(x=>x>=11.5),'Training experience labels keep a 12px inset '+JSON.stringify({width,settings}));
    assert.equal(settings.small,0,'Secondary and ghost actions are 48px tall '+ctx);

    // Workout: the active set does not shift its controls against the other rows.
    await tab('home');
    await page.evaluate(()=>document.querySelector('.wpb-home-hero .wpb-primary-action').click());await wait(1500);
    await page.evaluate(()=>{const b=[...document.querySelectorAll('button')].find(b=>b.textContent.trim()==='Skip');b&&b.click();});await wait(900);
    const set=await page.evaluate(()=>[...document.querySelectorAll('[data-testid^="set-0-"]')].map(r=>Math.round(r.querySelector('.wpb-set-stepper').getBoundingClientRect().left)));
    assert.ok(set.length>=2&&set.every(x=>x===set[0]),'Set rows keep one left edge, active or not '+JSON.stringify({width,set}));
    assert.deepEqual(errors,[],'No page errors '+ctx);
    await page.close();
  }
  console.log('PASS B1036 spacing: 12px root rhythm, 16px card insets, aligned set rows and section titles, inset labels, 48px secondary actions at 320 and 393px.');
}finally{await browser?.close();server.close();}
