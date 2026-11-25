// prints winning authored declarations from rules whose selector matches PAT, per element in SEL, at widths 320/393/900
import fs from 'node:fs'; import http from 'node:http'; import path from 'node:path'; import puppeteer from 'puppeteer-core';
const root=process.argv[2];const PAT=new RegExp(process.env.PAT);
const D=86400000, now=Date.now();
const program={id:'p',name:'My Program',custom:true,weeks:6,schedule:{'1':'upper','3':'lower','5':'upper'},config:{unit:'lb',goal:'both',experience:'intermediate',progression:'double',weeks:6,session:'s60',deload:false},days:[{id:'upper',label:'Upper',primaryIndex:0,exercises:['bb-bench','inc-curl']},{id:'lower',label:'Lower',primaryIndex:0,exercises:['back-squat','bb-bench','ohp','inc-curl']}]};
const history=[];for(let i=0;i<9;i++)history.push({id:'h'+i,programId:'p',programName:'My Program',dayId:'upper',dayLabel:'Upper',weekIndex:1+Math.floor(i/2),date:now-(i*3+1)*D,unit:'lb',durationMin:52,setsDone:6,totalSets:6,perf:{'bb-bench':{weight:150+i*5,reps:6,sets:[1,2,3].map(()=>({w:150+i*5,r:6,done:true,rir:2,rirReported:true}))}}});
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html','.json':'application/json','.svg':'image/svg+xml','.png':'image/png'};
const server=http.createServer((req,res)=>{const n=new URL(req.url,'http://x').pathname;const f=n==='/app.css'&&process.env.CSS_FILE?process.env.CSS_FILE:(n==='/modules/App.js'&&process.env.APP_FILE?process.env.APP_FILE:path.resolve(root,'.'+(n==='/'?'/index.html':n)));if(!fs.existsSync(f)||!fs.statSync(f).isFile()){res.writeHead(404);return res.end();}res.setHeader('Content-Type',mime[path.extname(f)]||'application/octet-stream');res.end(fs.readFileSync(f));});
await new Promise(d=>server.listen(0,'127.0.0.1',d));
const browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN,headless:true,args:['--no-sandbox']});
const wait=ms=>new Promise(r=>setTimeout(r,ms));const steps=(await import(process.env.NAV)).default;
for(const w of [+(process.env.W||393)]){const page=await browser.newPage();await page.setViewport({width:w,height:844,isMobile:w<600,hasTouch:true});
 const state={bodyweight:188.5,v:13,savedAt:1,saved:[program],pinnedId:'p',cycles:[],history,perf:{},drafts:{},tombs:{},seenIntro:999,seenWhatsNew:999,unit:'lb',unitChosen:true,experience:'intermediate',installDismissedAt:now,lastBackup:now,theme:'amethyst'};
 await page.evaluateOnNewDocument(s=>{localStorage.clear();localStorage.setItem('wpb:v1',JSON.stringify(s));},state);
 await page.goto('http://127.0.0.1:'+server.address().port+'/',{waitUntil:'networkidle0'});await page.waitForSelector('.wpb-tabbar');
 for(const fn of Object.values(steps))await fn(page,wait);await wait(700);
 await page.evaluate(()=>{const s=document.querySelector('.wpb-profile-scroll');if(s){s.style.overflow='visible';s.style.height='auto';}document.querySelectorAll('.wpb-scroll').forEach(x=>{x.style.maxHeight='none'});});
 if(process.env.CLICK){await page.evaluate(t=>{const b=[...document.querySelectorAll('button')].find(n=>n.textContent.trim().startsWith(t));b&&b.click();},process.env.CLICK);await wait(900);}
 if(process.env.SCROLL){await page.evaluate(()=>{const b=document.querySelector('.wpb-ui-modal-sheet-body');b&&(b.scrollTop=9999);});await wait(400);}await page.screenshot({path:process.env.OUT,fullPage:true});
 await page.close();}
await browser.close();server.close();
