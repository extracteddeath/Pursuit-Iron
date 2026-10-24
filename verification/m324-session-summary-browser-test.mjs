import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import puppeteer from 'puppeteer-core';
import { summaryProgram as program } from './m324-session-summary-fixture.mjs';
import { summarizeSession } from '../modules/engine-api.js';

const root = path.resolve(new URL('../', import.meta.url).pathname);
const day = program.days[0], expected = summarizeSession(program, day, 2);
const loggedAt = Date.now() - 86400000;
const history = [{ id: 'm324-week-one', programId: program.id, dayId: day.id,
    date: loggedAt, updatedAt: loggedAt, weekIndex: 1, unit: 'lb',
    perf: Object.fromEntries(day.exercises.map((id, slot) => [id, { weight: 100, reps: 8,
        sets: Array.from({ length: slot ? 2 : 3 }, () => ({ w: 100, r: slot ? 15 : 8, rir: 2, rirReported: true, done: true })) }])) }];
const store = { v: 13, savedAt: 1, saved: [program], cycles: [], history, perf: {}, drafts: {}, tombs: {},
    pinnedId: program.id, seenIntro: 999, seenWhatsNew: 999, unit: 'lb', unitChosen: true,
    experience: 'intermediate', bodyweight: 185, restAutoStart: false, warmupCard: false,
    installDismissedAt: Date.now(), lastBackup: Date.now() };
const mime = { '.js': 'text/javascript', '.css': 'text/css', '.html': 'text/html', '.json': 'application/json',
    '.png': 'image/png', '.svg': 'image/svg+xml', '.webmanifest': 'application/manifest+json' };
const server = http.createServer((req, res) => {
    const file = path.resolve(root, '.' + new URL(req.url, 'http://localhost').pathname);
    if (!file.startsWith(root + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) { res.writeHead(404); res.end(); return; }
    res.setHeader('Content-Type', mime[path.extname(file)] || 'application/octet-stream'); res.end(fs.readFileSync(file));
});
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
const url = `http://127.0.0.1:${server.address().port}/index.html`;
const clickText = async (page, text) => {
    await page.waitForFunction(text => [...document.querySelectorAll('button')].some(b => b.textContent.trim() === text), {}, text);
    await page.evaluate(text => [...document.querySelectorAll('button')].find(b => b.textContent.trim() === text).click(), text);
};
let browser;
try {
    browser = await puppeteer.launch({ executablePath: process.env.CHROME_BIN || '/usr/bin/google-chrome',
        headless: true, args: ['--no-sandbox', '--disable-dev-shm-usage'] });
    for (const [width, theme] of [[320, 'amethyst'], [900, 'rosewater']]) {
        const context = await browser.createBrowserContext(), page = await context.newPage(), errors = [];
        page.on('pageerror', e => errors.push(e.message));
        await page.setViewport({ width, height: 880, deviceScaleFactor: 1, isMobile: width < 600, hasTouch: true });
        await page.emulateMediaFeatures([{ name: 'prefers-reduced-motion', value: 'reduce' }]);
        await page.evaluateOnNewDocument(initial => {
            if (!sessionStorage.getItem('m324-seeded')) {
                localStorage.setItem('wpb:v1', JSON.stringify(initial)); sessionStorage.setItem('m324-seeded', '1');
            }
        }, { ...store, theme });
        await page.goto(url, { waitUntil: 'networkidle0' });
        await page.waitForSelector('.wpb-home');
        const loadedHistory = await page.evaluate(() => JSON.parse(localStorage.getItem('wpb:v1')).history);
        assert.deepEqual(loadedHistory, history, 'Hydration must preserve genuine saved edit timestamps');
        const outline = await page.$eval('.wpb-home-session-outline', node => node.textContent);
        assert.match(outline, /Dumbbell Bench Press/);
        assert.match(outline, /Back Squat/);
        assert.doesNotMatch(outline, /Barbell Bench Press/);
        await page.click('[data-tab="plan"]');
        await page.waitForSelector('[data-plan-week="2"] .wpb-plan-session-link');
        const row = await page.$eval('[data-plan-week="2"] .wpb-plan-session-link', node => node.innerText);
        assert.match(row, /Dumbbell Bench Press/);
        assert.match(row, new RegExp(expected.sets + ' sets'));
        assert.match(row, new RegExp(expected.minutes + 'm'));
        await page.screenshot({ path: `verification/m324-plan-${width}-phone.png` });
        await page.click('[aria-label="Open full program"]');
        await page.waitForSelector('[data-daycard]');
        // Collapse the selected day to inspect its summary rather than the
        // already-correct expanded exercise rows.
        if (!await page.$('.wpb-day-exercise-preview')) await page.click('[data-daycard] .wpb-day-title');
        await page.waitForSelector('.wpb-day-exercise-preview');
        const names = await page.$eval('.wpb-day-exercise-preview', node => node.textContent);
        assert.match(names, /Dumbbell Bench Press/); assert.match(names, /Back Squat/);
        assert.doesNotMatch(names, /Barbell Bench Press/);
        await page.screenshot({ path: `verification/m324-program-${width}-phone.png` });
        await page.reload({ waitUntil: 'networkidle0' });
        await clickText(page, 'Preview · Time available');
        await page.waitForSelector('[data-nextex]');
        const previewNames = await page.$$eval('[data-nextex]', nodes => nodes.map(n => n.textContent).join(' '));
        assert.match(previewNames, /Dumbbell Bench Press/); assert.match(previewNames, /Back Squat/);
        const preview = await page.$eval('.wpb-next-workout-view', n => n.innerText);
        assert.match(preview, new RegExp(expected.sets + ' working sets'));
        assert.match(preview, new RegExp('~' + expected.minutes + ' min'));
        await page.screenshot({ path: `verification/m324-preview-${width}-phone.png` });
        await clickText(page, 'Start workout');
        await page.waitForSelector('.wpb-workout');
        await page.waitForFunction(() => [...document.querySelectorAll('button')].some(b => b.textContent.startsWith('Feeling strong')));
        await page.evaluate(() => [...document.querySelectorAll('button')].find(b => b.textContent.startsWith('Feeling strong')).click());
        await page.waitForFunction(() => JSON.parse(localStorage.getItem('wpb:live') || '{}').data?.length === 2);
        const live = await page.evaluate(() => JSON.parse(localStorage.getItem('wpb:live')));
        assert.equal(live.weekIndex, 2);
        assert.deepEqual(live.data.map(row => row.id), expected.exercises.map(row => row.id));
        assert.equal(live.data.reduce((n, row) => n + row.sets.filter(set => !set.warm && !set.sub).length, 0), expected.sets);
        assert.deepEqual(await page.evaluate(() => JSON.parse(localStorage.getItem('wpb:v1')).history), loadedHistory,
            'Browsing and starting a workout preserve the normalized loaded history, including import metadata');
        assert.deepEqual(errors, []);
        await context.close();
    }
    // M332: a real reordered week must keep Home, Plan and the opened Preview in agreement.
    for (const [width, theme, enlarged] of [[393,'amethyst',false],[320,'rosewater',true],[900,'rosewater',false]]) {
        const context=await browser.createBrowserContext(),page=await context.newPage(),errors=[];
        page.on('pageerror',e=>errors.push(e.message));
        const days=[{id:'upper',label:'Upper Body',primaryIndex:0,exercises:['bb-bench']},
            {id:'lower',label:'Lower Body',primaryIndex:0,exercises:['back-squat']},
            {id:'full',label:'Full Body',primaryIndex:0,exercises:['bb-row']}];
        const p={id:'m332-cursor',name:'Strength and Muscle · Weekly Training',custom:true,weeks:4,
            config:{unit:'lb',goal:'both',experience:'intermediate',progression:'double',weeks:4,deload:false},days,
            overrides:Object.fromEntries(days.map(d=>[d.id+':0',{sets:3,reps:'6-8',rir:2}]))};
        const h=[2,1,0].map((di,i)=>({id:'m332-log-'+i,programId:p.id,dayId:days[di].id,weekIndex:1,
            date:Date.now()-(3-i)*86400000,updatedAt:1700000000000+i,unit:'lb',setsDone:1,totalSets:3,durationMin:20,
            perf:{[days[di].exercises[0]]:{sets:[{w:100,r:6,done:true,rir:2,rirReported:true}]}}}));
        await page.setViewport({width,height:880,isMobile:width<600,hasTouch:true});
        await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
        await page.evaluateOnNewDocument(seed=>{
            if(sessionStorage.getItem('m332-seeded'))return;
            sessionStorage.setItem('m332-seeded','1');localStorage.setItem('wpb:v1',JSON.stringify(seed));
        },{...store,saved:[p],pinnedId:p.id,history:h,theme});
        await page.goto(url,{waitUntil:'networkidle0'});await page.waitForSelector('.wpb-home-hero');
        assert.match(await page.$eval('.wpb-home-hero',n=>n.textContent),/Lower Body/);
        const before=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')).history);
        const homeSets=await page.evaluate(()=>{
            const label=[...document.querySelectorAll('.wpb-home *')].find(n=>n.children.length===0&&n.textContent==='Sets');
            for(let n=label?.parentElement;n;n=n.parentElement){
                const value=parseInt(n.textContent,10);if(Number.isFinite(value))return value;
            }
            throw new Error('Home weekly set total missing');
        });
        assert.equal(homeSets,3);
        await page.click('[data-tab="progress"]');await page.waitForSelector('[data-progress-glance]');
        const totals=await page.$$eval('[data-progress-glance] .wpb-progress-glance-item',nodes=>Object.fromEntries(nodes.map(n=>
            [n.querySelector('.wpb-progress-glance-label').textContent,Number(n.querySelector('.wpb-progress-glance-value').textContent)])));
        assert.equal(totals.Sessions,3);assert.equal(totals.Sets,homeSets,'muscle credits must not inflate the completed-set tile');
        await page.click('[data-tab="plan"]');await page.waitForSelector('.wpb-plan-up-next');
        assert.match(await page.$eval('.wpb-plan-up-next',n=>n.textContent),/Lower Body/);
        assert.match(await page.$eval('.wpb-plan-up-next',n=>n.textContent),/Week 2/);
        const next=await page.$eval('[data-plan-week="2"] .wpb-plan-session-link[data-next="true"]',n=>n.textContent);
        assert.match(next,/Lower Body/);
        if(enlarged)await page.evaluate(()=>{
            const fonts=[...document.querySelectorAll('*')].filter(n=>n.nodeType===1&&n.tagName!=='STYLE')
                .map(n=>[n,parseFloat(getComputedStyle(n).fontSize)]);
            fonts.forEach(([n,size])=>n.style.setProperty('font-size',size*2+'px','important'));
        });
        const layout=await page.$eval('.wpb-plan-context-heading',n=>{
            const t=n.querySelector('h2').getBoundingClientRect(),b=n.querySelector('button').getBoundingClientRect(),r=n.getBoundingClientRect();
            return {width:r.width,titleWidth:t.width,titleBottom:t.bottom,buttonTop:b.top,buttonHeight:b.height,overflow:n.scrollWidth>n.clientWidth+1};
        });
        assert.equal(layout.overflow,false);
        assert.ok(layout.buttonHeight>=48);
        if(width<600)assert.ok(Math.abs(layout.titleWidth-layout.width)<=1&&layout.buttonTop>=layout.titleBottom+6,
            'long program name uses full width before its action: '+JSON.stringify(layout));
        await page.screenshot({path:`verification/m332-plan-${width}-phone.png`});
        await page.click('.wpb-plan-up-next');await page.waitForSelector('[data-nextex]');
        const preview=await page.$eval('.wpb-next-workout-view',n=>n.textContent);
        assert.match(preview,/Lower Body/);assert.match(preview,/Back Squat/);assert.doesNotMatch(preview,/Barbell Bench Press/);
        assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')).history),before);
        assert.deepEqual(errors,[]);await context.close();
    }
    console.log('PASS M332 browser: reordered-week Home/Plan/Preview parity and long Plan-name reflow at 393/320-large-text/900px.');
    console.log('PASS M324 browser: real Home → Plan → full Program → Preview → Workout uses week-two exercises, sets and clock, preserving history at 320/900px dark/light.');
} finally {
    if (browser) await browser.close();
    await new Promise(resolve => server.close(resolve));
}
