import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import http from 'node:http';
import puppeteer from 'puppeteer-core';

const root=path.resolve(new URL('../',import.meta.url).pathname);
const importMap=fs.readFileSync(path.join(root,'index.html'),'utf8').match(/<script type="importmap">[\s\S]*?<\/script>/)[0];
const fixture=[
"import React from '/vendor/react.js';",
"import {createRoot} from '/vendor/react-dom-client.js';",
"import {useProgramSheetFocus} from '/modules/program-sheet-focus.js';",
"function Sheet({close,forwardedRef}){",
"const {attachPanel,onPanelKeyDown}=useProgramSheetFocus(close,forwardedRef);",
"React.useEffect(()=>{window.dragRefMatched=forwardedRef.current===document.querySelector('#panel')},[forwardedRef]);",
"return React.createElement('div',{className:'wpb-backdrop wpb-program-settings-backdrop',onClick:e=>{if(e.target===e.currentTarget)close()}},",
"React.createElement('section',{id:'panel',tabIndex:-1,ref:attachPanel,onKeyDown:onPanelKeyDown,className:'wpb-program-settings-panel'},",
"React.createElement('button',{id:'first'},'Close'),",
"React.createElement('input',{id:'field','aria-label':'Folder'}),",
"React.createElement('details',null,React.createElement('summary',{id:'summary'},'More'),React.createElement('button',{id:'hidden-action'},'Hidden action')),",
"React.createElement('button',{id:'last'},'Apply'),",
"React.createElement('button',{id:'disabled',disabled:true},'Disabled'),",
"React.createElement('button',{id:'aria-disabled','aria-disabled':true},'ARIA disabled')));}",
"function Demo(){const [open,setOpen]=React.useState(false),dragRef=React.useRef(null);",
"const close=()=>{window.closeCount=(window.closeCount||0)+1;setOpen(false)};",
"return React.createElement('main',{className:'wpb'},",
"React.createElement('button',{id:'trigger',onClick:()=>setOpen(true)},'Open settings'),",
"React.createElement('button',{id:'outside'},'Outside'),",
"open&&React.createElement(Sheet,{close,forwardedRef:dragRef}));}",
"createRoot(document.getElementById('root')).render(React.createElement(Demo));"
].join('\n');
const html='<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="/app.css">'+importMap+'</head><body><div id="root"></div><script type="module">'+fixture+'</script></body></html>';
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html'};
const server=http.createServer((req,res)=>{
 const name=new URL(req.url,'http://localhost').pathname;
 if(name==='/sheet-focus-test.html'){res.setHeader('Content-Type','text/html');res.end(html);return;}
 const file=path.resolve(root,'.'+name);
 if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){res.writeHead(404);res.end();return;}
 res.setHeader('Content-Type',mime[path.extname(file)]||'application/octet-stream');res.end(fs.readFileSync(file));
});
await new Promise(done=>server.listen(0,'127.0.0.1',done));
let browser;
try{
 browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
 const page=await browser.newPage(), errors=[];
 page.on('pageerror',e=>errors.push(e.message));
 await page.setViewport({width:393,height:740,deviceScaleFactor:2,isMobile:true,hasTouch:true});
 await page.goto('http://127.0.0.1:'+server.address().port+'/sheet-focus-test.html',{waitUntil:'networkidle0'});
 await page.click('#trigger');await page.waitForSelector('#panel');
 assert.equal(await page.$eval('#panel',el=>el===document.activeElement),true,'focus enters sheet on mount');
 await page.waitForFunction(()=>window.dragRefMatched===true);
 await page.keyboard.down('Shift');await page.keyboard.press('Tab');await page.keyboard.up('Shift');
 assert.equal(await page.$eval('#last',el=>el===document.activeElement),true,'backward Tab wraps to last active action');
 await page.keyboard.press('Tab');
 assert.equal(await page.$eval('#first',el=>el===document.activeElement),true,'forward Tab wraps past unavailable controls');
 await page.$eval('#summary',el=>el.focus());
 await page.keyboard.press('Tab');
 assert.equal(await page.$eval('#last',el=>el===document.activeElement),true,'closed details children skipped');
 await page.click('#summary');
 await page.$eval('#summary',el=>el.focus());
 await page.keyboard.press('Tab');
 assert.equal(await page.$eval('#hidden-action',el=>el===document.activeElement),true,'open details action tabbable');
 await page.evaluate(()=>{window.escapeBubbled=0;document.addEventListener('keydown',ev=>{if(ev.key==='Escape')window.escapeBubbled++})});
 await page.keyboard.press('Escape');await page.waitForFunction(()=>!document.querySelector('#panel'));
 assert.equal(await page.evaluate(()=>window.closeCount),1,'Escape closes once');
 assert.equal(await page.evaluate(()=>window.escapeBubbled),0,'background shortcuts do not see Escape');
 assert.equal(await page.$eval('#trigger',el=>el===document.activeElement),true,'closing returns focus to opener');
 await page.setViewport({width:320,height:550,deviceScaleFactor:2,isMobile:true,hasTouch:true});
 await page.evaluate(()=>document.documentElement.style.fontSize='32px');
 await page.click('#trigger');
 const m=await page.$eval('#panel',el=>{const r=el.getBoundingClientRect();return {left:r.left,right:r.right,top:r.top,bottom:r.bottom,height:innerHeight}});
 assert.ok(m.left>=-1&&m.right<=321&&m.top>=-1&&m.bottom<=m.height+1,'320px scaled sheet fits visible viewport: '+JSON.stringify(m));
 assert.deepEqual(errors,[],'no browser page errors');
 console.log('PASS B992 browser: Program sheet focus cycle, disclosure, Escape, restore, drag ref and compact geometry.');
}finally{await browser?.close();await new Promise(resolve=>server.close(resolve));}
