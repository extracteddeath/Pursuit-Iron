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
for(const w of [320,393,900]){const page=await browser.newPage();await page.setViewport({width:w,height:844,isMobile:w<600,hasTouch:true});
 const state={v:13,savedAt:1,saved:[program],pinnedId:'p',cycles:[],history,perf:{},drafts:{},tombs:{},seenIntro:999,seenWhatsNew:999,unit:'lb',unitChosen:true,experience:'intermediate',bodyweight:185,installDismissedAt:now,lastBackup:now,theme:'amethyst'};
 await page.evaluateOnNewDocument(s=>{localStorage.clear();localStorage.setItem('wpb:v1',JSON.stringify(s));},state);
 await page.goto('http://127.0.0.1:'+server.address().port+'/',{waitUntil:'networkidle0'});await page.waitForSelector('.wpb-tabbar');
 for(const fn of Object.values(steps))await fn(page,wait);await wait(700);
 const cdp=await page.target().createCDPSession();await cdp.send('DOM.enable');await cdp.send('CSS.enable');
 const {root:r}=await cdp.send('DOM.getDocument',{depth:-1});
 for(const sel of process.env.SEL.split(';')){const {nodeId}=await cdp.send('DOM.querySelector',{nodeId:r.nodeId,selector:sel});if(!nodeId){console.log(w,sel,'NONE');continue;}
  const m=await cdp.send('CSS.getMatchedStylesForNode',{nodeId});const rules=(m.matchedCSSRules||[]).map(x=>x.rule);
  const byProp={};rules.forEach(rl=>{for(const d of rl.style.cssProperties){if(!d.range||d.disabled)continue;(byProp[d.name]=byProp[d.name]||[]).push({rl,d});}});
  const out=[];for(const [p,list] of Object.entries(byProp)){const imp=list.filter(x=>x.d.important);const win=(imp.length?imp:list).slice(-1)[0];
   if(PAT.test(win.rl.selectorList.text))out.push(`${p}:${win.d.value}${win.d.important?'!':''}  [${(win.rl.media||[]).map(q=>q.text).join('|')}] ${win.rl.selectorList.text.slice(0,70)}`);}
  console.log('==',w,sel);console.log(out.sort().join('\n'));}
 await page.close();}
await browser.close();server.close();
