// usage: node snap.mjs <root> <out.json>  env: SEL (css selector list), NAV (js file with nav steps per state), CSS_FILE override
import fs from 'node:fs'; import http from 'node:http'; import path from 'node:path'; import puppeteer from 'puppeteer-core';
const root=process.argv[2], outFile=process.argv[3];
const D=86400000, now=Date.now();
const program={id:'p',name:'My Program',custom:true,weeks:6,schedule:{'1':'upper','3':'lower','5':'upper'},config:{unit:'lb',goal:'both',experience:'intermediate',progression:'double',weeks:6,session:'s60',deload:false},days:[{id:'upper',label:'Upper',primaryIndex:0,exercises:['bb-bench','inc-curl']},{id:'lower',label:'Lower',primaryIndex:0,exercises:['back-squat','bb-bench','ohp','inc-curl']}]};
const history=[];for(let i=0;i<9;i++)history.push({id:'h'+i,programId:'p',programName:'My Program',dayId:'upper',dayLabel:'Upper',weekIndex:1+Math.floor(i/2),date:now-(i*3+1)*D,unit:'lb',durationMin:52,setsDone:6,totalSets:6,perf:{'bb-bench':{weight:150+i*5,reps:6,sets:[1,2,3].map(()=>({w:150+i*5,r:6,done:true,rir:2,rirReported:true}))}}});
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html','.json':'application/json','.svg':'image/svg+xml','.png':'image/png'};
const server=http.createServer((req,res)=>{const n=new URL(req.url,'http://x').pathname;const f=n==='/app.css'&&process.env.CSS_FILE?process.env.CSS_FILE:(n==='/modules/App.js'&&process.env.APP_FILE?process.env.APP_FILE:path.resolve(root,'.'+(n==='/'?'/index.html':n)));if(!fs.existsSync(f)||!fs.statSync(f).isFile()){res.writeHead(404);return res.end();}res.setHeader('Content-Type',mime[path.extname(f)]||'application/octet-stream');res.end(fs.readFileSync(f));});
await new Promise(d=>server.listen(0,'127.0.0.1',d));
const browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN,headless:true,args:['--no-sandbox']});
const wait=ms=>new Promise(r=>setTimeout(r,ms));
const out={};
const steps=(await import(process.env.NAV)).default;
for(const theme of (process.env.THEMES||'amethyst,rosewater').split(','))for(const w of [320,393,900])for(const fz of [16,32]){
 const page=await browser.newPage();await page.setViewport({width:w,height:844,isMobile:w<600,hasTouch:true});await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
 const state={v:13,savedAt:1,saved:[program],pinnedId:'p',cycles:[],history,perf:{},drafts:{},tombs:{},seenIntro:999,seenWhatsNew:999,unit:'lb',unitChosen:true,experience:'intermediate',bodyweight:185,installDismissedAt:now,lastBackup:now,theme};
 await page.evaluateOnNewDocument(s=>{localStorage.clear();localStorage.setItem('wpb:v1',JSON.stringify(s));},state);
 await page.goto('http://127.0.0.1:'+server.address().port+'/',{waitUntil:'networkidle0'});await page.waitForSelector('.wpb-tabbar');
 await page.evaluate(px=>{document.documentElement.style.fontSize=px+'px'},fz);
 for(const [name,fn] of Object.entries(steps)){await fn(page,wait);await wait(700);
  const r=await page.evaluate(([sel,keept])=>{const o={};const els=[...document.querySelectorAll(sel)];els.forEach((el,i)=>{const cs=getComputedStyle(el);let s={};for(let k=0;k<cs.length;k++){const n=cs[k];if(!keept&&/^(transition|animation|will-change|view-transition)/.test(n))continue;s[n]=cs.getPropertyValue(n)}
    const tag=(el.className?.baseVal??el.className)+'#'+i;o[tag]=s;for(const pe of ['::before','::after']){const c=getComputedStyle(el,pe);if(c.content!=='none'&&c.content!=='normal'){let t={};for(let k=0;k<c.length;k++)t[c[k]]=c.getPropertyValue(c[k]);o[tag+pe]=t;}}});return o;},[process.env.SEL,!!process.env.KEEPT]);
  out[`${theme}|${w}|${fz}|${name}`]=r;}
 await page.close();}
fs.writeFileSync(outFile,JSON.stringify(out));await browser.close();server.close();console.log('states',Object.keys(out).length);
