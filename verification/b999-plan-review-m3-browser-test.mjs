// B999: actual Material 3 Plan change summary at 320px/200% Android text.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import http from 'node:http';
import puppeteer from 'puppeteer-core';
const root=path.resolve(new URL('../',import.meta.url).pathname);
const map=fs.readFileSync(path.join(root,'index.html'),'utf8').match(/<script type="importmap">[\s\S]*?<\/script>/)[0];
const fixture=[
"import React from '/vendor/react.js';",
"import {createRoot} from '/vendor/react-dom-client.js';",
"import {PlanChangesDialog} from '/modules/plan-session-surfaces.js';",
"function Demo(){const [open,setOpen]=React.useState(false);window.choices=[];",
"const change={undone:false,category:'Load increase',summary:'Review a higher training load',kind:'load',",
"previous:[{weight:180,reps:8}],loadChanged:true,canUndo:true,source:{date:Date.now()}};",
"const review={weekIndex:2,programId:'demo',dayId:'upper',unit:'lb',changes:[{id:'bench',",
"name:'Barbell Bench Press with a long exercise label',change,proposed:[{weight:185,reps:8}],",
"proposedReason:'Built enough reps across sessions to raise the load'}]};",
"return React.createElement('main',{className:'wpb'},",
"React.createElement('button',{id:'open-plan',onClick:()=>setOpen(true)},'Review adjustments'),",
"open&&React.createElement(PlanChangesDialog,{review,dayLabel:'Upper',busy:false,",
"onChoice:(...args)=>window.choices.push(args),onClose:()=>setOpen(false)}));}",
"createRoot(document.getElementById('root')).render(React.createElement(Demo));"
].join('\n');
const html='<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">'
    +'<link rel="stylesheet" href="/app.css">'+map+'</head><body><div id="root"></div><script type="module">'+fixture+'</script></body></html>';
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html'};
const server=http.createServer((req,res)=>{
    const name=new URL(req.url,'http://localhost').pathname;
    if(name==='/b999.html'){res.setHeader('Content-Type','text/html');res.end(html);return;}
    const filename=path.resolve(root,'.'+name);
    if(!filename.startsWith(root+path.sep)||!fs.existsSync(filename)||!fs.statSync(filename).isFile()){
        res.writeHead(404);res.end();return;
    }
    res.setHeader('Content-Type',mime[path.extname(filename)]||'application/octet-stream');
    res.end(fs.readFileSync(filename));
});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
let browser;
try{
    browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',
        headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
    const page=await browser.newPage(),errors=[];
    page.on('pageerror',err=>errors.push(err.message));
    await page.setViewport({width:320,height:650,deviceScaleFactor:2,isMobile:true,hasTouch:true});
    await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
    await page.goto('http://127.0.0.1:'+server.address().port+'/b999.html',{waitUntil:'networkidle0'});
    await page.evaluate(()=>document.documentElement.style.fontSize='32px');
    await page.click('#open-plan');
    await page.waitForSelector('.wpb-plan-changes-dialog');
    const sizes=await page.evaluate(()=>{
        const panel=document.querySelector('.wpb-plan-changes-dialog');
        const action=panel.querySelector('.wpb-plan-undo');
        const summary=panel.querySelector('.wpb-plan-change-more>summary');
        const intro=panel.querySelector('.wpb-plan-review-intro');
        const r=panel.getBoundingClientRect();
        return {left:r.left,right:r.right,top:r.top,bottom:r.bottom,height:innerHeight,
            actionHeight:action.getBoundingClientRect().height,
            summaryHeight:summary.getBoundingClientRect().height,
            textSize:parseFloat(getComputedStyle(intro).fontSize),
            focus:panel===document.activeElement};
    });
    assert.ok(sizes.left>=-1&&sizes.right<=321&&sizes.top>=-1&&sizes.bottom<=sizes.height+1,
        'Plan modal fits 320px with large text: '+JSON.stringify(sizes));
    assert.ok(sizes.actionHeight>=48&&sizes.summaryHeight>=48,
        'Plan actions and disclosure remain >=48px: '+JSON.stringify(sizes));
    assert.ok(sizes.textSize>=24,'200% text scales the review narrative: '+JSON.stringify(sizes));
    assert.equal(sizes.focus,true,'shared modal focus trap receives initial focus');
    assert.match(await page.$eval('.wpb-plan-review-intro',el=>el.textContent),/Upcoming workout only.*Completed history stays intact/);
    const visibleDiff=await page.$eval('.wpb-plan-target-diff',el=>({
        visible:!!el.getClientRects().length,text:el.innerText,labels:el.getAttribute('aria-label')
    }));
    assert.ok(visibleDiff.visible&&visibleDiff.text.includes('180 lb')&&visibleDiff.text.includes('185 lb'),
        'One-set before/after must be readable before opening Why: '+JSON.stringify(visibleDiff));
    assert.ok(visibleDiff.labels.includes('Previous and upcoming'),'Before/after has a usable screen-reader name');
    assert.equal(await page.$eval('.wpb-plan-change-more',el=>el.open),false,'Why starts collapsed, not a text wall');
    await page.click('.wpb-plan-change-more>summary');
    assert.equal(await page.$eval('.wpb-plan-change-more',el=>el.open),true,
        'details are available without changing the logged workout');
    await page.click('.wpb-plan-undo');
    assert.equal(await page.evaluate(()=>window.choices.length),1,
        'load decision is still delegated to the canonical owning callback');
    await page.keyboard.press('Escape');
    await page.waitForFunction(()=>!document.querySelector('.wpb-plan-changes-dialog'));
    assert.equal(await page.$eval('#open-plan',el=>el===document.activeElement),true,
        'Escape restores focus to opener');
    assert.deepEqual(errors,[],'no browser exceptions');
    console.log('PASS B999 browser: Plan review geometry, 200% narrative, 48px actions, callbacks and Escape.');
}finally{await browser?.close();await new Promise(resolve=>server.close(resolve));}
