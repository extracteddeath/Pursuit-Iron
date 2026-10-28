import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import http from 'node:http';
import puppeteer from 'puppeteer-core';
const root=path.resolve(new URL('../',import.meta.url).pathname);
const im=fs.readFileSync(path.join(root,'index.html'),'utf8').match(/<script type="importmap">[\s\S]*?<\/script>/)[0];
const fixture=`import React from '/vendor/react.js';
import {createRoot} from '/vendor/react-dom-client.js';
import {SessionChoiceDialog} from '/modules/session-choice-dialog.js';
function Demo(){
 const [mode,setMode]=React.useState(null);
 window.forceClose=()=>setMode(null);
 return React.createElement('div',{className:'wpb',style:{minHeight:'100dvh'}},
 ['standard','protected','empty','disabled'].map(id=>React.createElement('button',{id:'open-'+id,key:id,onClick:()=>setMode(id)},'Open '+id)),
 mode && React.createElement(SessionChoiceDialog,{
   title:'An intentionally very long heading for the full session review experience',
   dismissible:mode==='standard',
   onClose:()=>{window.closeCount=(window.closeCount||0)+1;setMode(null)}
 },mode==='empty'?React.createElement('p',null,'Read only'):
 mode==='disabled' ? [
    React.createElement('input',{key:'field',id:'field','aria-label':'Details'}),
    React.createElement('details',{key:'closed'},React.createElement('summary',null,'Extra'),React.createElement('button',{id:'closed-action'},'Unavailable while closed')),
    React.createElement('div',{key:'inert',inert:true},React.createElement('button',{id:'inert-action'},'Inactive')),
    React.createElement('button',{key:'live',id:'last-live'},'Apply'),
    React.createElement('input',{key:'disabled-input',id:'disabled-field',disabled:true,'aria-label':'Unavailable'}),
    React.createElement('select',{key:'disabled-select',id:'disabled-select',disabled:true},React.createElement('option',null,'Disabled')),
    React.createElement('button',{key:'disabled-button',id:'disabled-action',disabled:true},'Unavailable'),
    React.createElement('input',{key:'hidden',id:'hidden-field',style:{display:'none'},'aria-label':'Hidden')
 ] : [React.createElement('input',{key:'field',id:'field','aria-label':'Details'}),
     React.createElement('button',{key:'save',id:'save'},'Save')])
 );
}
createRoot(document.getElementById('root')).render(React.createElement(Demo));`;
const html='<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="/app.css">'+im+'</head><body><div id="root"></div><script type="module">'+fixture+'</script></body></html>';
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html','.json':'application/json'};
const server=http.createServer((req,res)=>{
 const name=new URL(req.url,'http://localhost').pathname;
 if(name==='/dialog-test.html'){res.setHeader('Content-Type','text/html');res.end(html);return;}
 const file=path.resolve(root,'.'+name);
 if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){res.writeHead(404);res.end();return;}
 res.setHeader('Content-Type',mime[path.extname(file)]||'application/octet-stream');res.end(fs.readFileSync(file));
});
await new Promise(done=>server.listen(0,'127.0.0.1',done));
let browser;
try {
 browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
 const page=await browser.newPage(),errors=[];
 page.on('pageerror',e=>errors.push(e.message));
 await page.setViewport({width:393,height:844,deviceScaleFactor:2,isMobile:true,hasTouch:true});
 await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
 await page.goto('http://127.0.0.1:'+server.address().port+'/dialog-test.html',{waitUntil:'networkidle0'});
 await page.click('#open-standard');await page.waitForSelector('.wpb-session-dialog');
 assert.equal(await page.$eval('.wpb-session-dialog',el=>el===document.activeElement),true,'focus enters dialog');
 const geo=await page.$eval('.wpb-session-dialog-heading button',el=>{
   const r=el.getBoundingClientRect(),p=el.parentElement.getBoundingClientRect(),t=el.previousElementSibling.getBoundingClientRect();
   return {width:r.width,height:r.height,left:r.left,right:r.right,headingRight:t.right,parentRight:p.right};
 });
 assert.ok(geo.width>=48&&geo.height>=48&&geo.left>=geo.headingRight-1&&geo.right<=geo.parentRight+1,'48px Close aligned beside long heading');
 await page.keyboard.down('Shift');await page.keyboard.press('Tab');await page.keyboard.up('Shift');
 assert.equal(await page.$eval('#save',el=>el===document.activeElement),true,'Shift+Tab wraps to last control');
 await page.keyboard.press('Tab');
 assert.equal(await page.$eval('.wpb-session-dialog-heading button',el=>el===document.activeElement),true,'Tab wraps to Close');
 await page.keyboard.press('Escape');await page.waitForFunction(()=>!document.querySelector('.wpb-session-dialog'));
 assert.equal(await page.$eval('#open-standard',el=>el===document.activeElement),true,'return focus to trigger');
 assert.equal(await page.evaluate(()=>window.closeCount),1,'Escape invokes one close');
 await page.click('#open-protected');
 await page.$eval('.wpb-session-dialog-backdrop',el=>el.click());
 await page.keyboard.press('Escape');
 assert.ok(await page.$('.wpb-session-dialog'),'protected sheet stays open after backdrop and Escape');
 assert.equal(await page.evaluate(()=>window.closeCount),1,'no premature protected close');
 await page.keyboard.press('Tab');
 assert.equal(await page.$eval('#field',el=>el===document.activeElement),true,'protected Tab enters first field');
 await page.evaluate(()=>window.forceClose());await page.waitForFunction(()=>!document.querySelector('.wpb-session-dialog'));
 await page.click('#open-disabled');await page.waitForSelector('#last-live');
 await page.$eval('#last-live',el=>el.focus());await page.keyboard.press('Tab');
 assert.equal(await page.$eval('.wpb-session-dialog-heading button',el=>el===document.activeElement),true,'Tab skips disabled, hidden, inert and closed disclosure controls');
 await page.keyboard.down('Shift');await page.keyboard.press('Tab');await page.keyboard.up('Shift');
 assert.equal(await page.$eval('#last-live',el=>el===document.activeElement),true,'Shift+Tab wraps to last enabled visible action');
 await page.keyboard.press('Escape');await page.waitForFunction(()=>!document.querySelector('.wpb-session-dialog'));
 await page.click('#open-empty');await page.keyboard.press('Tab');
 assert.equal(await page.$eval('.wpb-session-dialog',el=>el===document.activeElement),true,'empty protected sheet retains focus');
 await page.evaluate(()=>window.forceClose());await page.waitForFunction(()=>!document.querySelector('.wpb-session-dialog'));
 await page.setViewport({width:1000,height:800,deviceScaleFactor:1,isMobile:false,hasTouch:false});
 await page.click('#open-standard');
 const tablet=await page.$eval('.wpb-session-dialog',el=>{const r=el.getBoundingClientRect();return {x:r.x,right:r.right,width:r.width};});
 assert.ok(tablet.width<=620&&tablet.x>=0&&tablet.right<=1000,'tablet bounds contained');
 assert.deepEqual(errors,[],'no browser errors');
 console.log('PASS B963/B959 browser: protected sheet, disabled/hidden/inert Tab trap, Escape, phone and tablet Close geometry');
} finally { if(browser)await browser.close();await new Promise(done=>server.close(done)); }
