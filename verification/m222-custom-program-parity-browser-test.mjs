import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import http from 'node:http';
import puppeteer from 'puppeteer-core';
import { program, history, constraints } from './m222-custom-program-parity-test.mjs';
const root = path.resolve(new URL('../', import.meta.url).pathname);
async function verifyManualTargets(browser){
    const base={id:'m394-manual',name:'Authored target editing',custom:true,weeks:4,
        config:{unit:'lb',weeks:4,goal:'both',experience:'intermediate',progression:'manual',split:'custom',session:'s60',days:1,deload:false,equipment:['barbell','bench','dumbbell']},
        days:[{id:'upper',label:'Upper',primaryIndex:0,exercises:['bb-bench','inc-curl']}],
        overrides:{'upper:0':{sets:[3,4,5,6],reps:[6,8],rir:'2',rest:120},'upper:1':{sets:2,reps:'10-15',rir:'2',rest:90}}};
    for(const theme of ['rose','rosewater']){
        const p=structuredClone(base),context=await browser.createBrowserContext(),page=await context.newPage(),errors=[];
        page.on('pageerror',error=>errors.push(error.message));
        try{
            await page.setViewport({width:393,height:852,isMobile:true,hasTouch:true});
            await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
            await page.evaluateOnNewDocument(({p,theme})=>{if(localStorage.getItem('m394-seeded'))return;
                localStorage.setItem('m394-seeded','1');localStorage.setItem('wpb:v1',JSON.stringify({v:13,savedAt:1,saved:[p],pinnedId:p.id,
                    cycles:[],history:[],perf:{},drafts:{[p.id]:{...p,description:'Earlier pending edit'}},tombs:{},unit:'lb',unitChosen:true,theme,seenIntro:999,seenWhatsNew:999,
                    installDismissedAt:Date.now(),lastBackup:Date.now()}));},{p,theme});
            await page.goto('http://127.0.0.1:8776/index.html',{waitUntil:'networkidle0'});
            await page.click('.hp-open');await page.waitForSelector('.wpb-program');
            const setField='.wpb-program input[aria-label^="Sets"]',repField='.wpb-program input[aria-label^="Reps"]';
            const form='.wpb-manual-targets',apply=form+' [aria-label^="Apply targets"]',cancel=form+' [aria-label^="Cancel targets"]';
            const snapshot=()=>page.evaluate(()=>{const s=JSON.parse(localStorage.getItem('wpb:v1'));return {saved:s.saved,drafts:s.drafts,history:s.history,cycles:s.cycles};});
            const type=async(selector,value)=>{await page.focus(selector);await page.keyboard.down('Control');await page.keyboard.press('KeyA');await page.keyboard.up('Control');await page.keyboard.press('Backspace');if(value)await page.keyboard.type(value);};
            const before=await snapshot();
            assert.equal(await page.$eval(setField,n=>n.value),'3','Display the selected week rather than a serialized set schedule');
            assert.equal(await page.$eval(repField,n=>n.value),'6-8','Stored rep pairs display as an editable canonical range');
            await type(setField,'');
            assert.equal(await page.$eval(setField,el=>el.value),'','Clearing a manual set count preserves raw typing');
            assert.equal(await page.$eval(apply,n=>n.disabled),true);
            await type(repField,'10-20oops');assert.equal(await page.$eval(repField,n=>n.value),'10-20oops');
            assert.deepEqual(await snapshot(),before,'Invalid text cannot write a program or draft');
            await page.locator('.wpb-program-save').click();assert.equal(await page.$('.wpb-save-review-confirm'),null);
            await page.locator('.wpb-day-start').click();assert.equal(await page.$('.wpb-workout'),null);
            assert.equal(await page.evaluate(()=>localStorage.getItem('wpb:live')),null);
            assert.match(await page.$eval('.wpb-program',n=>n.innerText),/Apply or cancel/);
            assert.equal(await page.$eval(setField,n=>n===document.activeElement),true,'Blocked commit returns focus to the first invalid target');
            await page.evaluate(()=>new Promise(resolve=>{
                window.addEventListener('popstate',()=>requestAnimationFrame(()=>requestAnimationFrame(resolve)),{once:true});
                window.history.back();
            }));
            assert.ok(await page.$('.wpb-program'),'Native Back cannot discard unfinished target text');
            assert.equal(await page.$eval(repField,n=>n.value),'10-20oops','Native Back retains exact invalid typing');
            assert.equal(await page.$eval(setField,n=>n===document.activeElement),true,'Native Back focuses the unfinished editor');
            assert.deepEqual(await snapshot(),before,'Blocked native navigation cannot write targets');
            await page.locator(cancel).click();assert.deepEqual(await snapshot(),before);
            assert.equal(await page.$eval(setField,n=>n.value),'3');assert.equal(await page.$eval(repField,n=>n.value),'6-8');
            await page.evaluate(()=>window.history.back());
            await page.waitForSelector('.wpb-dialog');
            assert.match(await page.$eval('.wpb-dialog',n=>n.innerText),/Keep your changes\?/,'Native Back uses the existing saved-draft review');
            await page.locator('.wpb-dialog button:last-child').click();
            await page.waitForSelector('.wpb-dialog',{hidden:true});
            assert.deepEqual(await snapshot(),before,'Keep editing retains the earlier saved draft');
            await type(repField,'8–12');
            assert.equal(await page.$eval(apply,n=>n.disabled),false);
            assert.deepEqual(await snapshot(),before,'Valid raw text is still local until Apply');
            for(const [width,height,scale] of [[320,720,1],[393,540,2],[834,900,1],[1280,900,1]]){
                await page.setViewport({width,height,isMobile:true,hasTouch:true});
                await page.evaluate(scale=>document.documentElement.style.fontSize=scale===2?'32px':'16px',scale);
                const layout=await page.$eval(form,n=>({width:n.clientWidth,scroll:n.scrollWidth,fields:[...n.querySelectorAll('input,button')].map(el=>{
                    const r=el.getBoundingClientRect(),s=getComputedStyle(el);return {left:r.left,right:r.right,width:r.width,height:r.height,font:parseFloat(s.fontSize)};
                })}));
                assert.ok(layout.scroll<=layout.width+1,`Target editor reflows at ${width}/${scale}`);
                assert.ok(layout.fields.every(r=>r.left>=-1&&r.right<=width+1&&r.height>=40),`All fields/actions fit and remain touchable at ${width}/${scale}`);
                assert.ok(layout.fields.slice(0,2).every(r=>r.font>=16*scale),`Actual text scales in target fields at ${width}/${scale}`);
                await page.$eval(apply,n=>n.scrollIntoView({block:'center',behavior:'instant'}));
                assert.equal(await page.$eval(apply,n=>{const r=n.getBoundingClientRect();return r.top>=0&&r.bottom<=innerHeight;}),true,'Apply remains reachable in the short viewport');
                await page.screenshot({path:path.join(root,`verification/m394-manual-${theme}-${width}-${scale}.png`)});
            }
            await page.evaluate(()=>document.documentElement.style.fontSize='16px');
            await page.setViewport({width:393,height:852,isMobile:true,hasTouch:true});
            await page.locator(apply).click();
            await page.waitForFunction(()=>JSON.parse(localStorage.getItem('wpb:v1')).drafts['m394-manual']?.overrides?.['upper:0']?.reps==='8-12');
            const applied=await snapshot();assert.deepEqual(applied.saved,before.saved);
            assert.deepEqual(applied.drafts[p.id].overrides['upper:0'].sets,[3,4,5,6]);
            assert.equal(applied.drafts[p.id].overrides['upper:0'].rir,'2');assert.equal(applied.drafts[p.id].overrides['upper:0'].rest,120);
            await page.locator('.wpb-program-save').click();await page.waitForSelector('.wpb-save-review-confirm');
            await page.locator('.wpb-save-review-details summary').click();
            assert.match(await page.$eval('.wpb-program-change-details',n=>n.innerText),/8-12/);
            await page.locator('.wpb-save-review-confirm').click();
            await page.waitForFunction(()=>JSON.parse(localStorage.getItem('wpb:v1')).saved[0].overrides['upper:0'].reps==='8-12');
            const committed=await snapshot();assert.deepEqual(committed.saved[0].overrides['upper:0'].sets,[3,4,5,6]);
            assert.deepEqual(committed.saved[0].overrides['upper:1'],before.saved[0].overrides['upper:1']);
            assert.deepEqual(committed.history,before.history);assert.deepEqual(committed.cycles,before.cycles);
            await page.reload({waitUntil:'networkidle0'});await page.locator('.hp-open').click();
            await page.locator('.wpb-program-week-chip:nth-child(3)').click();
            await page.waitForFunction(selector=>document.querySelector(selector)?.value==='5',{},setField);
            assert.equal(await page.$eval(repField,n=>n.value),'8-12');
            await page.locator('.wpb-day-start').click();await page.waitForSelector('.wpb-next-workout-view');
            assert.match(await page.$eval('[data-nextex] .wpb-preview-prescription',n=>n.innerText),/5×8-12/);
            await page.locator('.wpb-preview-start').click();await page.waitForSelector('.wpb-workout');
            await page.waitForFunction(()=>!!JSON.parse(localStorage.getItem('wpb:live')||'null')?.data?.[0]?.sets?.length);
            const live=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:live')));
            assert.equal(live.weekIndex,3);assert.equal(live.data[0].sets.filter(s=>!s.sub&&!s.warm).length,5);
            assert.ok(live.data[0].sets.every(s=>s.target.reps==='8-12'));
            assert.deepEqual(errors,[]);
        }finally{await context.close();}
    }
    // Persisted malformed input is repairable and cannot silently omit a lift at launch.
    const context=await browser.createBrowserContext(),page=await context.newPage();
    try{
        const bad=structuredClone(base);bad.overrides['upper:0'].reps='10-20oops';
        await page.setViewport({width:393,height:852,isMobile:true,hasTouch:true});
        await page.evaluateOnNewDocument(p=>{localStorage.setItem('wpb:v1',JSON.stringify({v:13,saved:[p],pinnedId:p.id,history:[],cycles:[],perf:{},drafts:{},unit:'lb',unitChosen:true,theme:'rose',seenIntro:999,seenWhatsNew:999}));},bad);
        await page.goto('http://127.0.0.1:8776/index.html',{waitUntil:'networkidle0'});
        await page.locator('.wpb-home-hero .wpb-primary-action').click();
        assert.equal(await page.$('.wpb-workout'),null);assert.equal(await page.evaluate(()=>localStorage.getItem('wpb:live')),null);
        assert.match(await page.$eval('.wpb',n=>n.innerText),/Review the rep target/);
        await page.locator('.hp-open').click();
        assert.equal(await page.$eval('.wpb-program input[aria-label^="Reps"]',n=>n.value),'10-20oops');
        await page.locator('.wpb-day-start').click();
        assert.equal(await page.$('.wpb-workout'),null);assert.equal(await page.evaluate(()=>localStorage.getItem('wpb:live')),null);
        await page.waitForSelector('.wpb-next-workout-view');
        assert.match(await page.$eval('.wpb-preview-start',n=>n.textContent),/Review targets/);
        await page.locator('.wpb-preview-start').click();await page.waitForSelector('.wpb-program');
        assert.match(await page.$eval('.wpb-program input[aria-label^="Reps"]',n=>n.value),/10-20oops/);
        const recovery={schemaVersion:2,programId:bad.id,dayId:'upper',weekIndex:1,dayExSig:'bb-bench|inc-curl',exIdx:0,elapsedMs:120000,runPaused:true,
            data:bad.days[0].exercises.map((id,slot)=>({id,slot,sets:Array.from({length:slot?2:3},()=>({weight:slot?'30':'190',reps:slot?'10':'5',done:false,auto:false,valueOwner:'user',target:{w:slot?'30':'190',reps:slot?'10-15':'6-8',rir:'2'}}))}))};
        await page.evaluate(live=>localStorage.setItem('wpb:live',JSON.stringify(live)),recovery);
        await page.reload({waitUntil:'networkidle0'});
        await page.locator('.wpb-live-dock button[aria-label="Resume workout"]').click();
        await page.waitForSelector('.wpb-workout',{timeout:10000});
        assert.equal(await page.$eval('[data-testid="set-0-0"] input[aria-label="weight"]',n=>n.value),'190');
        assert.equal(await page.$eval('[data-testid="set-0-0"] input[aria-label="reps"]',n=>n.value),'5');
        const afterRecovery=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:live')).data);
        assert.deepEqual(afterRecovery.map(ex=>ex.sets.map(set=>[set.weight,set.reps,set.done])),recovery.data.map(ex=>ex.sets.map(set=>[set.weight,set.reps,set.done])),
            'A malformed current plan cannot block or rewrite an existing workout');

    }finally{await context.close();}
}
const importMap = fs.readFileSync(path.join(root,'index.html'),'utf8').match(/<script type="importmap">[\s\S]*?<\/script>/)[0];
const snapshot = {schemaVersion:2,programId:program.id,dayId:'push',weekIndex:1,dayExSig:'cable-fly',exIdx:0,elapsedMs:3800000,data:[{id:'cable-fly',slot:0,superset:true,sets:Array.from({length:4},(_,i)=>({weight:i===2?'17.5':'20',reps:'20',done:i===0,auto:i!==1,valueOwner:i===1?'user':'prescription',target:{w:i===2?'25':'20',reps:'10-20',rir:'2',prefillReps:'20'}}))}]};
const html = `<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"><link rel="stylesheet" href="/app.css">${importMap}</head><body><div id="root"></div><script type="module">
import React from '/vendor/react.js';import {createRoot} from '/vendor/react-dom-client.js';import {WorkoutSession,StyleTag} from '/modules/App.js';
const program=${JSON.stringify(program)},history=${JSON.stringify(history)},params=new URL(location.href).searchParams,resume=params.has('resume'); if(params.has('dynamic'))program.progStyle={'cable-fly':'dynamic'};if(params.has('down'))history[0].perf['cable-fly'].sets.forEach(s=>{s.w=25;s.r=8;s.rir=0;});if(params.has('nosuperset'))program.config.noSupersets=true;
localStorage.removeItem('wpb:live');if(resume)localStorage.setItem('wpb:live',JSON.stringify(${JSON.stringify(snapshot)}));
createRoot(document.getElementById('root')).render(React.createElement('div',{className:'wpb',style:{height:'100%'}},React.createElement(StyleTag),React.createElement(WorkoutSession,{program,day:program.days[0],weekIndex:1,unit:'lb',perf:{'seated-calf':history[0].perf['seated-calf']},history,restAutoStart:false,warmupCard:false,onExit:()=>{},onFinish:()=>{},setUnit:()=>{}})));
</script></body></html>`;
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html','.json':'application/json','.svg':'image/svg+xml','.png':'image/png'};
const server=http.createServer((req,res)=>{const pathname=new URL(req.url,'http://localhost').pathname;if(pathname==='/probe.html'){res.setHeader('Content-Type','text/html');res.end(html);return;}const file=path.resolve(root,'.'+pathname);if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){res.writeHead(404);res.end();return;}res.setHeader('Content-Type',mime[path.extname(file)]||'application/octet-stream');res.end(fs.readFileSync(file));});
await new Promise(resolve=>server.listen(8776,'127.0.0.1',resolve));let browser;
try {
    browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
    await verifyManualTargets(browser);
    console.log('PASS M394 browser: staged/invalid targets, pending-commit/native-Back guards and saved-draft review, dark/light 320–1280px and actual 200% text, weekly arrays, review/save/reload, Preview/live parity and malformed-target repair/refusal and protected recovery from an invalid current plan.');
    // Exercise the authored settings transaction through the actual router and review/save owner.
    const settingsContext=await browser.createBrowserContext(),settingsPage=await settingsContext.newPage(),settingsErrors=[];
    settingsPage.on('pageerror',e=>settingsErrors.push(e.message));
    const authored={...constraints,name:'Authored settings guard',config:{...constraints.config,unit:'lb',split:'custom',session:'s60',days:1,experience:'intermediate'}};
    await settingsPage.setViewport({width:393,height:852,isMobile:true,hasTouch:true});
    await settingsPage.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
    await settingsPage.evaluateOnNewDocument(p=>{if(!localStorage.getItem('m370-settings-seeded')){localStorage.setItem('wpb:v1',JSON.stringify({v:13,savedAt:1,saved:[p],pinnedId:p.id,cycles:[],history:[],perf:{},drafts:{},tombs:{},unit:'lb',unitChosen:true,
        theme:'rosewater',seenIntro:999,seenWhatsNew:999,installDismissedAt:Date.now(),lastBackup:Date.now()}));localStorage.setItem('m370-settings-seeded','1');}},authored);
    await settingsPage.goto('http://127.0.0.1:8776/index.html',{waitUntil:'networkidle0'});
    const store=()=>settingsPage.evaluate(()=>{const s=JSON.parse(localStorage.getItem('wpb:v1'));return {saved:s.saved,history:s.history,drafts:s.drafts,cycles:s.cycles};});
    const original=await store();await settingsPage.locator('.hp-open').click();await settingsPage.locator('[aria-label="Program settings"]').click();
    await settingsPage.waitForSelector('[data-testid="program-settings-sheet"]');
    const clickSetting=async label=>{
        const handle=await settingsPage.evaluateHandle(label=>[...document.querySelectorAll('[data-testid="program-settings-sheet"] button')].find(n=>n.textContent.trim()===label||n.textContent.trim().startsWith(label)),label);
        if(!handle.asElement())throw new Error('Settings action missing: '+label);
        await handle.asElement().evaluate(n=>n.scrollIntoView({block:'center'}));await handle.asElement().click();await handle.dispose();
    };
    const assertRefusal=async pattern=>{
        assert.equal(await settingsPage.$eval('[aria-label="Save program settings"]',n=>n.disabled),true);
        assert.match(await settingsPage.$eval('#program-settings-issues',n=>n.textContent),pattern);
        assert.deepEqual(await store(),original,'Refused choices cannot alter saved training or create a program draft');
    };
    await clickSetting('Skip bodyweight exercises');await assertRefusal(/Push-Up/);
    await settingsPage.screenshot({path:path.join(root,'verification/m370-settings-bodyweight-phone.png')});
    await settingsPage.setViewport({width:320,height:720,isMobile:true,hasTouch:true});
    await settingsPage.evaluate(()=>document.documentElement.style.fontSize='32px');
    assert.equal(await settingsPage.$eval('.wpb-program-settings-panel',n=>n.scrollWidth<=n.clientWidth+1),true,'Constraint explanation reflows at 200% text');
    await clickSetting('Skip bodyweight exercises');await clickSetting('1');await assertRefusal(/2 barbell movements/);
    await settingsPage.screenshot({path:path.join(root,'verification/m370-settings-barbell-large-phone.png')});
    await clickSetting('No limit');
    await settingsPage.evaluate(()=>document.documentElement.style.fontSize='');await settingsPage.setViewport({width:393,height:852,isMobile:true,hasTouch:true});
    await settingsPage.focus('[aria-label="Block length in weeks"]');await settingsPage.keyboard.down('Control');await settingsPage.keyboard.press('KeyA');await settingsPage.keyboard.up('Control');
    await settingsPage.keyboard.type('53');assert.equal(await settingsPage.$eval('[aria-label="Save program settings"]',n=>n.disabled),true);
    assert.equal(await settingsPage.$eval('[aria-label="Block length in weeks"]',n=>n.value),'53','Unsupported input remains editable instead of silently changing to 52');
    await settingsPage.focus('[aria-label="Block length in weeks"]');await settingsPage.keyboard.down('Control');await settingsPage.keyboard.press('KeyA');await settingsPage.keyboard.up('Control');await settingsPage.keyboard.type('4');
    await settingsPage.type('#program-settings-description','Reviewed authored settings.');
    await clickSetting('Save changes');await settingsPage.waitForSelector('[data-testid="program-settings-sheet"]',{hidden:true});
    await settingsPage.locator('.wpb-program-save').click();await settingsPage.waitForSelector('.wpb-save-review-confirm');await settingsPage.locator('.wpb-save-review-confirm').click();
    await settingsPage.waitForFunction(id=>JSON.parse(localStorage.getItem('wpb:v1')).saved.find(p=>p.id===id)?.description==='Reviewed authored settings.',{},authored.id);
    const committed=await store();assert.deepEqual(committed.saved[0].days,original.saved[0].days);assert.deepEqual(committed.saved[0].overrides,original.saved[0].overrides);
    assert.deepEqual(committed.history,original.history);assert.deepEqual(committed.cycles,original.cycles);
    await settingsPage.reload({waitUntil:'networkidle0'});assert.equal((await store()).saved[0].description,'Reviewed authored settings.');
    assert.deepEqual(settingsErrors,[]);await settingsContext.close();
    const page=await browser.newPage(),errors=[];page.on('pageerror',e=>errors.push(e.message));
    await page.setViewport({width:390,height:844,deviceScaleFactor:2,isMobile:true,hasTouch:true});
    await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
    const open=async query=>{await page.goto('http://127.0.0.1:8776/probe.html'+query,{waitUntil:'networkidle0'});await page.waitForSelector('[data-testid="set-0-1"]');const check=await page.$('.wpb-backdrop button[data-wpb-system-back]');if(check)await check.click();};
    const values=()=>page.$$eval('[data-testid^="set-0-"][data-done="0"] input[aria-label="weight"]',ns=>ns.map(n=>n.value));
    const reps=()=>page.$$eval('[data-testid^="set-0-"][data-done="0"] input[aria-label="reps"]',ns=>ns.map(n=>n.value));
    await open('');
    assert.deepEqual(await values(),['25','25','20','25']);assert.deepEqual(await reps(),['20','11','12','11']);
    assert.match(await page.$eval('.wpb-workout',n=>n.innerText),/4 prior sets/);
    await page.click('button[aria-label="Show previous workout values"]');
    const lastText=await page.$eval('.wpb-workout',n=>n.innerText);assert.match(lastText,/25×19/);assert.match(lastText,/20×11/);
    await page.screenshot({path:path.join(root,'verification/m222-progression-phone.png')});
    await open('?dynamic');assert.deepEqual(await values(),['25','25','20','25']);assert.deepEqual(await reps(),['20','11','12','11']);
    assert.match(await page.$eval('.wpb-workout',n=>n.innerText),/20–25 lb/);
    await open('?down');assert.deepEqual(await values(),['25','25','25','25'],'Double progression holds prior working loads until a reduction is chosen');assert.deepEqual(await reps(),['10','10','10','10']);
    await page.click('.wpb-load-explanation summary');
    assert.match(await page.$eval('.wpb-load-explanation',n=>n.textContent),/You can choose 20 lb/,'A lower load stays visible as optional advice');
    await open('?resume&nosuperset');assert.deepEqual(await values(),['20','17.5','25']);assert.deepEqual(await reps(),['20','20','11']);
    const resumed=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:live')).data[0].sets);
    assert.deepEqual(resumed.map(s=>s.weight),['20','20','17.5','25']);assert.deepEqual(resumed.map(s=>s.reps),['20','20','20','11']);
    assert.equal(resumed[0].done,true,'completed set keeps its logged values');
    assert.deepEqual(await page.$eval('[data-testid="set-0-0"]',row=>[row.querySelector('input[aria-label="weight"]')?.value,row.querySelector('input[aria-label="reps"]')?.value,row.querySelector('input[aria-label="weight"]')?.readOnly]),['20','20',true],'completed custom set retains full faded read-only row');
    assert.ok(!(await page.$eval('.wpb-workout',n=>n.innerText)).includes('Unlink superset'));assert.equal(await page.$eval('.wpb-workout-tools',n=>n.innerText.includes('Superset')),false);
    // Typed/manual values survive state writes and a reload.
    const input=await page.$('[data-testid="set-0-3"] input[aria-label="reps"]');await input.click();await page.keyboard.down('Control');await page.keyboard.press('KeyA');await page.keyboard.up('Control');await page.keyboard.press('Backspace');await input.type('13');
    await page.waitForFunction(()=>JSON.parse(localStorage.getItem('wpb:live')).data[0].sets[3].reps==='13');
    const state=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:live')));assert.equal(state.data[0].sets[3].valueOwner,'user');
    assert.deepEqual(errors,[]);
    console.log('PASS M222 browser: authored settings refuse bodyweight/barbell conflicts and invalid duration without changing training; valid edits save/reload; Cable Fly targets, LAST evidence, mixed DDP loads/reps, protected resume/manual typing and no-superset setting.');
}finally{if(browser)await browser.close();await new Promise(resolve=>server.close(resolve));}
