import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import puppeteer from 'puppeteer-core';

const root=path.resolve(new URL('../',import.meta.url).pathname);
const program={
    id:'b1013',name:'Machine swap smoke test',custom:true,weeks:4,
    config:{unit:'lb',goal:'both',experience:'intermediate',progression:'double',
        deload:false,equipment:['machinepress']},
    days:[{id:'upper',label:'Upper',primaryIndex:0,exercises:['machine-press']}],
    overrides:{'upper:0':{sets:2,reps:'8-12',rir:'2',rest:90}}
};
const gym={id:'gym-b1013',name:'Work gym',equipment:['machinepress'],
    machineVariants:{'machine-press':{defaultId:'A',
        instances:[{id:'A',label:'Machine A',active:true}]}}};
const store={v:13,savedAt:1,saved:[program],cycles:[],history:[],perf:{},
    drafts:{},tombs:{},seenIntro:999,seenWhatsNew:999,
    pinnedId:program.id,theme:'amethyst',restAutoStart:false,
    warmupCard:false,unit:'lb',unitChosen:true,experience:'intermediate',
    gyms:[gym],activeGymId:gym.id,bodyweight:185,
    installDismissedAt:Date.now(),lastBackup:Date.now()};
const set=(weight,done)=>({weight,reps:'8',done,auto:false,
    valueOwner:'user',machineInstanceId:'A',
    target:{w:weight,reps:'8-12',rir:2}});
const live={schemaVersion:2,sessionId:'b1013-live',programId:program.id,
    dayId:'upper',weekIndex:1,dayExSig:'machine-press',
    gymId:gym.id,exIdx:0,elapsedMs:20000,restPaused:true,
    restRemain:0,restMax:0,runPaused:true,
    readiness:{label:'Normal',factor:1},
    data:[{id:'machine-press',slot:0,machineInstanceId:'A',
        note:'',sets:[set('200',true),set('200',false)]}]};
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html',
    '.json':'application/json','.svg':'image/svg+xml',
    '.webmanifest':'application/manifest+json'};
const server=http.createServer((request,response)=>{
    const name=new URL(request.url,'http://127.0.0.1').pathname;
    const file=path.resolve(root,'.'+(name==='/'?'/index.html':name));
    if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||
        !fs.statSync(file).isFile()){
        response.writeHead(404);response.end();return;
    }
    response.setHeader('Content-Type',mime[path.extname(file)]||'application/octet-stream');
    response.end(fs.readFileSync(file));
});
await new Promise(done=>server.listen(0,'127.0.0.1',done));
let browser;
try{
    browser=await puppeteer.launch({
        executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',
        headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
    const page=await browser.newPage(),errors=[];
    page.on('pageerror',error=>errors.push(error.message));
    await page.setViewport({width:393,height:810,isMobile:true,
        hasTouch:true,deviceScaleFactor:2});
    await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
    await page.evaluateOnNewDocument((saved,session)=>{
        localStorage.setItem('wpb:v1',JSON.stringify(saved));
        localStorage.setItem('wpb:live',JSON.stringify(session));
    },store,live);
    await page.goto('http://127.0.0.1:'+server.address().port+'/',
        {waitUntil:'networkidle0'});
    await page.waitForSelector('.wpb-live-dock button[aria-label="Resume workout"]');
    await page.click('.wpb-live-dock button[aria-label="Resume workout"]');
    await page.waitForSelector('.wpb-machine-switch-trigger');
    const chip=await page.$eval('.wpb-machine-switch-trigger',el=>{
        const r=el.getBoundingClientRect();
        return {height:r.height,width:r.width,text:el.textContent,
            label:el.getAttribute('aria-label')};
    });
    assert.ok(chip.height>=48,'Machine chip must meet native 48px touch target');
    assert.ok(chip.text.includes('Machine A'),
        'Active physical machine is visible without a bulky card');
    await page.click('.wpb-machine-switch-trigger');
    await page.waitForSelector('.wpb-machine-switch-sheet');
    assert.equal(await page.$eval('.wpb-machine-switch-sheet',el=>el.getAttribute('aria-modal')),'true');
    assert.ok(await page.$eval('.wpb-machine-switch-sheet',
        el=>el.textContent.includes('Machine A')&&
            el.textContent.includes('Different machine here?')));
    // Name and save the machine WHILE the resumed workout is open. Previously
    // the user had to leave the workout, dig into Settings and return.
    await page.$eval('.wpb-machine-switch-create input:not([type])',
        el=>{el.value='Machine B';el.dispatchEvent(new Event('input',{bubbles:true}));});
    // Use type() so React's controlled input sees a real interaction.
    await page.$eval('.wpb-machine-switch-create input:not([type])',el=>{el.value='';});
    await page.type('.wpb-machine-switch-create input:not([type])','Machine B');
    await page.click('.wpb-machine-switch-default input');
    await page.click('.wpb-machine-switch-add');
    await page.waitForFunction(()=>
        document.querySelector('.wpb-machine-switch-name')?.textContent==='Machine B' &&
        !document.querySelector('.wpb-machine-switch-sheet'));
    await page.waitForFunction(()=>{
        const saved=JSON.parse(localStorage.getItem('wpb:v1')||'{}');
        return saved.gyms?.some(g=>g.id==='gym-b1013' &&
            g.machineVariants?.['machine-press']?.instances?.some(v=>v.label==='Machine B'));
    },{timeout:12000});
    const persisted=await page.evaluate(()=>{
        const saved=JSON.parse(localStorage.getItem('wpb:v1'));
        const live=JSON.parse(localStorage.getItem('wpb:live'));
        return {
            gym:saved.gyms.find(g=>g.id==='gym-b1013'),
            session:live.data?.find(e=>e.id==='machine-press')
        };
    });
    const variants=persisted.gym.machineVariants['machine-press'];
    const alternative=variants.instances.find(v=>v.label==='Machine B');
    assert.ok(alternative,'New machine is saved to the same gym catalog');
    assert.equal(variants.defaultId,alternative.id,
        'Explicit gym-default choice persists with newly added machine');
    assert.equal(variants.instances.find(v=>v.id==='A').label,'Machine A',
        'Original physical machine retains its own identity');
    assert.equal(persisted.session.sets[0].machineInstanceId,'A',
        'Completed working set stays with the original machine');
    assert.equal(persisted.session.sets[0].weight,'200',
        'Completed load must never be overwritten');
    assert.equal(persisted.session.sets[1].machineInstanceId,alternative.id,
        'Only unfinished work follows new physical machine');
    assert.equal(String(persisted.session.sets[1].weight),'',
        'New weight stack never silently inherits A\'s 200 lb prescription');
    // A resumed workout can switch back to the old machine in one touch.
    await page.click('.wpb-machine-switch-trigger');
    await page.waitForSelector('.wpb-machine-switch-sheet');
    await page.click('.wpb-machine-switch-option:not([disabled])');
    await page.waitForFunction(()=>
        document.querySelector('.wpb-machine-switch-name')?.textContent==='Machine A');
    // Escape and Tab remain available on a narrow, 200% text phone.
    await page.setViewport({width:320,height:590,isMobile:true,
        hasTouch:true,deviceScaleFactor:2});
    await page.evaluate(()=>document.documentElement.style.fontSize='32px');
    await page.click('.wpb-machine-switch-trigger');
    await page.waitForSelector('.wpb-machine-switch-sheet');
    const geometry=await page.$eval('.wpb-machine-switch-sheet',el=>{
        const r=el.getBoundingClientRect();
        const option=el.querySelector('.wpb-machine-switch-option').getBoundingClientRect();
        const add=el.querySelector('.wpb-machine-switch-add').getBoundingClientRect();
        return {left:r.left,right:r.right,top:r.top,bottom:r.bottom,
            scroll:el.scrollWidth,client:el.clientWidth,
            optionHeight:option.height,addHeight:add.height};
    });
    assert.ok(geometry.left>=-1&&geometry.right<=321&&geometry.top>=-1&&
        geometry.bottom<=591&&geometry.scroll<=geometry.client+2 &&
        geometry.optionHeight>=48&&geometry.addHeight>=48,
        'Machine sheet stays accessible on 320px / large-font devices: '+JSON.stringify(geometry));
    await page.keyboard.press('Escape');
    await page.waitForFunction(()=>!document.querySelector('.wpb-machine-switch-sheet'));
    assert.ok(await page.$eval('.wpb-machine-switch-trigger',
        el=>document.activeElement===el),'Dismissal restores focus to the exact machine chip');
    assert.deepEqual(errors,[],'No runtime errors from actual in-workout machine creation/switch');
    console.log('PASS browser Machine A/B: in-workout Add & use, gym default persistence, original logged set, safe new load, return switch, 320px text and focus');
}finally{
    await browser?.close();
    await new Promise(done=>server.close(done));
}
