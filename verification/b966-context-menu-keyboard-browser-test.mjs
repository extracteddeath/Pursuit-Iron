import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import http from 'node:http';
import puppeteer from 'puppeteer-core';
const root=path.resolve(new URL('../',import.meta.url).pathname);
const im=fs.readFileSync(path.join(root,'index.html'),'utf8').match(/<script type="importmap">[\s\S]*?<\/script>/)[0];
const fixture = `
import React from '/vendor/react.js';
import {createRoot} from '/vendor/react-dom-client.js';
import {ContextMenu} from '/modules/ui-menu.js';
import {installAppMotion} from '/modules/ui-motion.js';
function Demo(){
  const [open,setOpen]=React.useState(false);
  const anchor=React.useRef(null);
  React.useEffect(()=>{const app=document.querySelector('.wpb');return installAppMotion(app)},[]);
  return React.createElement('div',{className:'wpb',style:{minHeight:'100dvh',position:'relative'}},
    React.createElement('button',{ref:anchor,id:'anchor',onClick:()=>setOpen(true)},'Open menu'),
    React.createElement('button',{id:'after'},'After menu'),
    open && React.createElement(ContextMenu,{anchor:anchor.current,label:'Fixture actions',title:'Actions',onClose:()=>setOpen(false),
      items:[{k:'disabled',label:'Unavailable',disabled:true,on:()=>{}},
        {k:'first',label:'First valid',on:()=>{}},
        {k:'second',label:'Second valid',on:()=>{}},
        {k:'third',label:'Third valid',on:()=>{}}]})
  );
}
createRoot(document.getElementById('root')).render(React.createElement(Demo));
`;
const html='<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="/app.css">'+im+'</head><body><div id="root"></div><script type="module">'+fixture+'</script></body></html>';
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html'};
const server=http.createServer((req,res)=>{
  const name=new URL(req.url,'http://localhost').pathname;
  if(name==='/menu-test.html'){res.setHeader('Content-Type','text/html');res.end(html);return;}
  const file=path.resolve(root,'.'+name);
  if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){res.writeHead(404);res.end();return;}
  res.setHeader('Content-Type',mime[path.extname(file)]||'application/octet-stream');res.end(fs.readFileSync(file));
});
await new Promise(done=>server.listen(0,'127.0.0.1',done));
let browser;
try{
 browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
 const page=await browser.newPage(),errors=[];
 page.on('pageerror',e=>errors.push(e.message));
 for(const width of [320,393,1000]){
   await page.setViewport({width,height:750,deviceScaleFactor:1,isMobile:width<500,hasTouch:width<500});
   await page.goto('http://127.0.0.1:'+server.address().port+'/menu-test.html',{waitUntil:'networkidle0'});
   await page.click('#anchor');
   await page.waitForSelector('.wpb-context-menu[data-pi-menu-ready="1"]');
   const names=()=>page.evaluate(()=>({active:document.activeElement?.textContent?.trim(),tag:document.activeElement?.tagName,menu:!!document.querySelector('.wpb-context-menu')}));
   let current=await names();
   assert.equal(current.active,'First valid','Initial focus must skip native disabled item');
   // The first action becomes temporarily unavailable while the menu stays open.
   await page.evaluate(()=>{
     const rows=[...document.querySelectorAll('[role="menuitem"]')];
     rows[1].setAttribute('aria-disabled','true');
     rows[2].setAttribute('inert','');
     rows[3].style.display='none';
     document.querySelector('.wpb-context-menu').focus({preventScroll:true});
   });
   await page.keyboard.press('ArrowUp');
   current=await names();
   assert.equal(current.tag,'DIV','No eligible items leaves focus on menu container');
   await page.evaluate(()=>{
     const rows=[...document.querySelectorAll('[role="menuitem"]')];
     rows[2].removeAttribute('inert');
     rows[3].style.display='';
   });
   await page.keyboard.press('ArrowUp');
   assert.equal((await names()).active,'Third valid','ArrowUp from container goes to last usable item');
   await page.keyboard.press('ArrowDown');
   assert.equal((await names()).active,'Second valid','ArrowDown wraps to first eligible item');
   await page.keyboard.press('Home');
   assert.equal((await names()).active,'Second valid','Home ignores aria-disabled and native disabled items');
   await page.keyboard.press('End');
   assert.equal((await names()).active,'Third valid','End chooses last valid item');
   await page.keyboard.press('Tab');
   await page.waitForFunction(()=>!document.querySelector('.wpb-context-menu'));
   assert.equal(await page.$eval('#anchor',el=>el===document.activeElement),true,'Tab close restores anchor without jumping');
   await page.keyboard.press('Tab');
   assert.equal(await page.$eval('#after',el=>el===document.activeElement),true,'Next Tab can continue after anchor');
   await page.click('#anchor');
   await page.keyboard.press('Escape');
   await page.waitForFunction(()=>!document.querySelector('.wpb-context-menu'));
   assert.equal(await page.$eval('#anchor',el=>el===document.activeElement),true,'Escape also restores anchor');
 }
 assert.deepEqual(errors,[],'no runtime errors');
 console.log('PASS B966 menu browser: phone/tablet initial focus, live eligibility, arrow navigation, Tab and Escape restoration');
}finally{if(browser)await browser.close();await new Promise(resolve=>server.close(resolve));}
