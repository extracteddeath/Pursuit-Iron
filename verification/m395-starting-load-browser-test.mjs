import assert from 'node:assert/strict';
import fs from 'node:fs';import path from 'node:path';import http from 'node:http';import puppeteer from 'puppeteer-core';
const root=path.resolve(new URL('../',import.meta.url).pathname);
const server=http.createServer((req,res)=>{const name=new URL(req.url,'http://localhost').pathname;
    const file=path.resolve(root,'.'+(name==='/'?'/index.html':name));
    if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){res.writeHead(404);res.end();return;}
    res.setHeader('Content-Type',({'.js':'text/javascript','.css':'text/css','.html':'text/html','.json':'application/json','.png':'image/png','.webmanifest':'application/manifest+json'})[path.extname(file)]||'application/octet-stream');res.end(fs.readFileSync(file));});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
let browser;
try{
    browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
    for(const [width,theme,unit,bodyweight] of [[320,'rosewater','lb',185],[900,'rose','kg',80]]){
        const context=await browser.createBrowserContext(),page=await context.newPage(),errors=[];
        page.on('pageerror',error=>errors.push(error.message));
        await page.setViewport({width,height:850,isMobile:width<600,hasTouch:true});
        const program={id:'m395',name:'Estimate regression',custom:true,weeks:4,days:[{id:'upper',label:'Upper',primaryIndex:0,exercises:['db-bench']}],
            config:{unit,goal:'both',experience:'intermediate',progression:'double',deload:false},overrides:{'upper:0':{sets:3,reps:'8-12',rir:'2',rest:90}}};
        const seed={v:13,savedAt:1,saved:[program],pinnedId:program.id,history:[],perf:{},cycles:[],drafts:{},tombs:{},unit,bodyweight,sex:'male',experience:'intermediate',
            theme,unitChosen:true,seenIntro:999,seenWhatsNew:999,warmupCard:false,restAutoStart:false,lastBackup:Date.now(),installDismissedAt:Date.now()};
        await page.evaluateOnNewDocument(seed=>{if(!sessionStorage.getItem('m395')){localStorage.setItem('wpb:v1',JSON.stringify(seed));sessionStorage.setItem('m395','1');}},seed);
        await page.goto(`http://127.0.0.1:${server.address().port}/`,{waitUntil:'networkidle0'});
        await page.waitForSelector('.wpb-home');
        assert.match(await page.$eval('.wpb-session-game-plan',node=>node.textContent),/Starting estimate.*bodyweight and experience/,'Home labels profile-derived seeds');
        await page.evaluate(()=>[...document.querySelectorAll('button')].find(node=>node.textContent.trim()==='Preview · Time available').click());
        await page.waitForSelector('.wpb-next-workout-view');
        assert.match(await page.$eval('.wpb-session-game-plan',node=>node.textContent),/Starting estimate/,'Preview retains the estimate explanation');
        await page.click('.wpb-preview-start');await page.waitForSelector('.wpb-workout');
        await page.evaluate(()=>[...document.querySelectorAll('button')].find(node=>node.textContent.includes('Feeling strong')).click());
        await page.waitForFunction(()=>![...document.querySelectorAll('button')].some(node=>node.textContent.includes('Feeling strong')));
        await page.waitForFunction(()=>JSON.parse(localStorage.getItem('wpb:live')||'null')?.data?.[0]?.sets?.length===3);
        const snapshot=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:live')));
        const target=snapshot.data[0].sets[0].target;
        assert.ok(Number(target.w)>0);assert.equal(target.nextAction,'estimate');
        assert.match(await page.$eval('.wpb-workout-starting-hint',node=>node.textContent),/Starting estimate/,'Workout also labels the heuristic');
        assert.match(await page.$eval('[data-testid="set-0-0"]',node=>node.textContent),/estimate/);
        await page.screenshot({path:`verification/m395-estimate-${width}-phone.png`});
        const input='[data-testid="set-0-0"] input[aria-label="weight"]';
        await page.$eval(input,node=>{node.focus();node.select();});await page.keyboard.type('25');
        await page.waitForFunction(()=>JSON.parse(localStorage.getItem('wpb:live')).data[0].sets[0].weight==='25');
        await page.reload({waitUntil:'networkidle0'});await page.waitForSelector('[aria-label="Resume workout"]');await page.click('[aria-label="Resume workout"]');
        await page.waitForSelector('.wpb-workout');assert.equal(await page.$eval(input,node=>node.value),'25','resume keeps the entered load');
        assert.equal((await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:live')))).data[0].sets[0].target.w,target.w,'resume retains the original estimate snapshot');
        assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')).saved),[program]);
        assert.deepEqual(errors,[]);await context.close();
        console.log(`PASS M395 browser ${width}/${unit}: labelled Home/Preview/Workout estimate, typed load and immutable resume.`);
    }
}finally{if(browser)await browser.close();await new Promise(resolve=>server.close(resolve));}
