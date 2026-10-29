// Real Chromium regression for the B996 consolidated Program modal and disclosure owners.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import http from 'node:http';
import puppeteer from 'puppeteer-core';
const root=path.resolve(new URL('../',import.meta.url).pathname);
const importMap=fs.readFileSync(path.join(root,'index.html'),'utf8')
  .match(/<script type="importmap">[\s\S]*?<\/script>/)[0];
const fixture=[
"import React from '/vendor/react.js';",
"import {createRoot} from '/vendor/react-dom-client.js';",
"import {Info} from 'lucide-react';",
"import {ProgramSaveReview} from '/modules/program-review-surfaces.js';",
"import {ProgramExerciseOptions} from '/modules/program-exercise-options.js';",
"import {HistoryEditSheet} from '/modules/history-editor.js';",
"import {ProgramReferenceSheet,ProgressionChoiceSheet} from '/modules/program-reference-surfaces.js';",
"import {InfoCard} from '/modules/ui-disclosure.js';",
"function Demo(){const [sheet,setSheet]=React.useState('');const [expanded,setExpanded]=React.useState(false);",
"return React.createElement('main',{className:'wpb'},",
"React.createElement('button',{id:'open-review',onClick:()=>setSheet('review')},'Review changes'),",
"React.createElement('button',{id:'open-exercise',onClick:()=>setSheet('exercise')},'Exercise options'),",
"React.createElement('button',{id:'open-history',onClick:()=>setSheet('history')},'Edit history'),",
"React.createElement('button',{id:'open-reference',onClick:()=>setSheet('reference')},'Program reference'),",
"React.createElement('button',{id:'open-progression',onClick:()=>setSheet('progression')},'Progression method'),",
"React.createElement(InfoCard,{icon:Info,title:'Details',subtitle:'Show and hide',open:expanded,onToggle:()=>setExpanded(x=>!x)},",
"React.createElement('button',{id:'inside-reveal'},'Inside')),",
"sheet==='review'&&React.createElement(ProgramSaveReview,{program:{name:'Test',cycleId:null},changes:[],scope:'program',onScope:()=>{},",
"conflicts:[],onConfirm:()=>{},onClose:()=>setSheet('')}),",
"sheet==='exercise'&&React.createElement(ProgramExerciseOptions,{exercise:{name:'Bench Press',part:'chest',type:'compound'},primary:true,",
"canRemove:true,canLink:false,linked:false,note:'',setup:null,onNote:()=>{},onSuperset:()=>{},onWhy:()=>{},onInfo:()=>{},",
"onBan:()=>{},onRemove:()=>{},onClose:()=>setSheet('')}),",
"sheet==='history'&&React.createElement(HistoryEditSheet,{entry:{id:'h1',date:Date.now(),",
"programId:'p1',dayId:'d1',dayLabel:'Upper',durationMin:45,unit:'lb',",
"perf:{'back-squat':{weight:200,reps:5,sets:[{w:200,r:5,rir:2}]}},volume:1000,setsDone:1},",
"unit:'lb',onClose:()=>setSheet(''),onSave:()=>setSheet(''),",
"selectNumericInput:e=>e.currentTarget.select()}),",
"sheet==='reference'&&React.createElement(ProgramReferenceSheet,{onClose:()=>setSheet('')},",
"React.createElement('p',null,'Reference content')),",
"sheet==='progression'&&React.createElement(ProgressionChoiceSheet,{exerciseName:'Bench Press',",
"selected:'double',automaticLabel:'Double progression',linearIncrement:5,unit:'lb',",
"onChoose:()=>setSheet(''),onClose:()=>setSheet('')}));}",
"createRoot(document.getElementById('root')).render(React.createElement(Demo));"
].join('\n');
const html='<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="/app.css">'
  +importMap+'</head><body><div id="root"></div><script type="module">'+fixture+'</script></body></html>';
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html'};
const server=http.createServer((req,res)=>{
  const name=new URL(req.url,'http://localhost').pathname;
  if(name==='/b996.html'){res.setHeader('Content-Type','text/html');res.end(html);return;}
  const file=path.resolve(root,'.'+name);
  if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){
    res.writeHead(404);res.end();return;
  }
  res.setHeader('Content-Type',mime[path.extname(file)]||'application/octet-stream');
  res.end(fs.readFileSync(file));
});
await new Promise(done=>server.listen(0,'127.0.0.1',done));
let browser;
try {
  browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',
    headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
  const page=await browser.newPage(),errors=[];
  page.on('pageerror',e=>errors.push(e.message));
  await page.setViewport({width:393,height:740,deviceScaleFactor:2,isMobile:true,hasTouch:true});
  await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
  await page.goto('http://127.0.0.1:'+server.address().port+'/b996.html',{waitUntil:'networkidle0'});
  await page.click('#open-review');
  await page.waitForSelector('.wpb-save-review-panel');
  assert.equal(await page.$eval('.wpb-save-review-panel',el=>el===document.activeElement),true,
    'shared dialog owner receives initial focus');
  await page.keyboard.down('Shift');await page.keyboard.press('Tab');await page.keyboard.up('Shift');
  assert.equal(await page.$eval('.wpb-save-review-cancel',el=>el===document.activeElement),true,
    'Shift+Tab wraps past disabled save and closed disclosures');
  await page.keyboard.press('Tab');
  assert.equal(await page.$eval('.wpb-save-review-close',el=>el===document.activeElement),true,
    'forward Tab wraps to first active control');
  await page.evaluate(()=>{window.escapeBubbled=0;document.addEventListener('keydown',e=>{if(e.key==='Escape')window.escapeBubbled++})});
  await page.keyboard.press('Escape');await page.waitForFunction(()=>!document.querySelector('.wpb-save-review-panel'));
  assert.equal(await page.evaluate(()=>window.escapeBubbled),0,'background shortcuts cannot consume modal Escape');
  assert.equal(await page.$eval('#open-review',el=>el===document.activeElement),true,
    'review close restores trigger focus');
  await page.click('#open-exercise');await page.waitForSelector('.wpb-exercise-options-sheet');
  await page.click('[data-exercise-action="remove"]');
  await page.waitForSelector('.wpb-exercise-options-confirm');
  await page.keyboard.press('Escape');
  await page.waitForFunction(()=>!document.querySelector('.wpb-exercise-options-confirm'));
  assert.ok(await page.$('.wpb-exercise-options-sheet'),'first Escape cancels confirmation, not entire sheet');
  await page.waitForFunction(()=>document.activeElement?.getAttribute('data-exercise-action')==='remove');
  assert.equal(await page.$eval('[data-exercise-action="remove"]',el=>el===document.activeElement),true,
    'cancelling removal returns focus to its own action');
  await page.keyboard.press('Escape');
  await page.waitForFunction(()=>!document.querySelector('.wpb-exercise-options-sheet'));
  assert.equal(await page.$eval('#open-exercise',el=>el===document.activeElement),true,
    'second Escape closes the sheet and restores trigger focus');
  await page.click('#open-history');
  await page.waitForSelector('.wpb-history-editor-sheet');
  assert.equal(await page.$eval('.wpb-history-editor-sheet',el=>el===document.activeElement),true,
    'History correction receives consistent initial focus');
  await page.keyboard.press('Escape');
  await page.waitForFunction(()=>!document.querySelector('.wpb-history-editor-sheet'));
  assert.equal(await page.$eval('#open-history',el=>el===document.activeElement),true,
    'history Escape restores the edit launcher without leaving a stale overlay');
  await page.evaluate(()=>{window.ResizeObserver=undefined;});
  await page.click('.wpb-info-card-toggle');
  await page.waitForSelector('#inside-reveal');
  assert.equal(await page.$eval('.wpb-info-card-toggle',el=>el.getAttribute('aria-expanded')), 'true');
  await page.click('.wpb-info-card-toggle');
  await page.waitForFunction(()=>document.querySelector('.wpb-info-card-toggle')?.getAttribute('aria-expanded')==='false');
  await page.setViewport({width:320,height:550,deviceScaleFactor:2,isMobile:true,hasTouch:true});
  await page.evaluate(()=>document.documentElement.style.fontSize='32px');
  await page.click('#open-review');
  const bounds=await page.$eval('.wpb-save-review-panel',el=>{
    const r=el.getBoundingClientRect();return {left:r.left,right:r.right,top:r.top,bottom:r.bottom,height:innerHeight};
  });
  assert.ok(bounds.left>=-1&&bounds.right<=321&&bounds.top>=-1&&bounds.bottom<=bounds.height+1,
    '320px/200% save review must fit viewport: '+JSON.stringify(bounds));
  await page.keyboard.press('Escape');
  await page.waitForFunction(()=>!document.querySelector('.wpb-save-review-panel'));
  for(const [trigger,selector] of [['#open-reference','.wpb-program-reference-panel'],
                                    ['#open-progression','.wpb-progression-choice-panel']]){
    await page.click(trigger);await page.waitForSelector(selector);
    const result=await page.$eval(selector,el=>{
      const r=el.getBoundingClientRect();
      return {left:r.left,right:r.right,top:r.top,bottom:r.bottom,height:innerHeight,
        focus:el===document.activeElement};
    });
    assert.ok(result.left>=-1&&result.right<=321&&result.top>=-1&&result.bottom<=result.height+1,
      selector+' must fit 320px scaled viewport '+JSON.stringify(result));
    assert.equal(result.focus,true,selector+' shares accessible initial focus');
    await page.keyboard.press('Escape');
    await page.waitForFunction(selector=>!document.querySelector(selector),{},selector);
    assert.equal(await page.$eval(trigger,el=>el===document.activeElement),true,
      selector+' restores its trigger after Escape');
  }
  assert.deepEqual(errors,[],'no runtime errors from shared Program focus or motion fallback');
  console.log('PASS B996 browser: Program review focus/geometry, destructive Escape, focus restore and resilient disclosure.');
} finally {await browser?.close();await new Promise(resolve=>server.close(resolve));}
