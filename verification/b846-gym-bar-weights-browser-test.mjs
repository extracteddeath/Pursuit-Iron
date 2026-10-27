import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import puppeteer from 'puppeteer-core';

const root=path.resolve(new URL('../',import.meta.url).pathname);
const store={v:13,savedAt:1,saved:[],cycles:[],history:[],perf:{},drafts:{},tombs:{},
  seenIntro:999,seenWhatsNew:999,theme:'amethyst',unit:'lb',unitChosen:true,
  experience:'intermediate',installDismissedAt:Date.now(),lastBackup:Date.now(),
  gyms:[{id:'test_home',name:'Test Garage',equipment:['barbell','smith','dumbbell'],
      barWeights:{unit:'lb',barbell:45,smith:35}},
    {id:'test_elsewhere',name:'Other Gym',equipment:['barbell','smith'],
      barWeights:{unit:'kg',barbell:20,smith:15}}],activeGymId:'test_home'};
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html','.json':'application/json',
  '.svg':'image/svg+xml','.png':'image/png','.webmanifest':'application/manifest+json'};
const server=http.createServer((req,res)=>{
  const route=new URL(req.url,'http://localhost').pathname;
  const file=path.resolve(root,'.'+(route==='/'?'/index.html':route));
  if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){
    res.writeHead(404);res.end();return;
  }
  res.setHeader('Content-Type',mime[path.extname(file)]||'application/octet-stream');
  res.end(fs.readFileSync(file));
});
await new Promise(resolve=>server.listen(8799,'127.0.0.1',resolve));
let browser;
try{
  browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',
    headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
  for(const [width,height,theme,enlarged] of [[393,852,'amethyst',false],
      [1024,900,'amethyst',false],[320,740,'rose',true],
      [900,480,'rosewater',true],[1024,900,'rosewater',true]]){
    const context=await browser.createBrowserContext();
    const page=await context.newPage(), errors=[];
    page.on('pageerror',e=>errors.push(e.message));
    try{
      await page.setViewport({width,height,deviceScaleFactor:1,isMobile:width<500,hasTouch:width<500});
      await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
      await page.evaluateOnNewDocument(state=>{if(!sessionStorage.getItem('b846-seeded')){localStorage.setItem('wpb:v1',JSON.stringify(state));sessionStorage.setItem('b846-seeded','1');}},{...store,theme});
      await page.goto('http://127.0.0.1:8799/',{waitUntil:'networkidle0'});
      await page.click('[data-tab="settings"]');
      await page.waitForSelector('.wpb-settings-card');
      const opened=await page.evaluate(()=>{
        const b=[...document.querySelectorAll('button')].find(x=>x.textContent.includes('Your gyms'));
        if(!b)return false;b.click();return true;
      });
      assert.equal(opened,true,'settings exposes gym editor');
      await page.waitForSelector('.wpb-gym-sheet');
      // CSS sheet entrance and window reflow are asynchronous. Test the final
      // reachable geometry, not an in-flight sheetUp transform; a persistently
      // off-screen or wrong-size editor still fails at the timeout.
      try { await page.waitForFunction(({width,height})=>{
        const sheet=document.querySelector('.wpb-gym-sheet');
        if(!sheet)return false;
        const r=sheet.getBoundingClientRect();
        if(r.x<-1||r.right>width+1)return false;
        const valid=width>=840&&height>=640
          ? r.width<=742&&r.width>=520&&Math.abs(r.x+r.width/2-width/2)<4&&r.y>8&&r.bottom<height-8
          : width>=700 ? r.width<=562&&Math.abs(r.x+r.width/2-width/2)<4&&r.bottom<=height&&r.y>=0
          : r.width>=width-32&&r.x>=-1&&r.x<=16&&width-r.right<=16&&r.bottom>=height-2;
        if(!valid)return false;
        // Require 150ms of settled geometry. The entrance effect may briefly
        // reach the bottom before the final layout frame has committed.
        const signature=[r.x,r.y,r.width,r.height].map(n=>Math.round(n*10)/10).join(':');
        const prior=window.__b846StableBounds;
        if(!prior||prior.signature!==signature){
          window.__b846StableBounds={signature,at:performance.now()};
          return false;
        }
        return performance.now()-prior.at>=150;
      },{timeout:6500,polling:50},{width,height}); }
      catch (error) {
        const geometry=await page.evaluate(()=>{
          const sheet=document.querySelector('.wpb-gym-sheet');
          const outer=sheet?.parentElement;
          const rect=el=>{if(!el)return null;const r=el.getBoundingClientRect();
            return {x:r.x,y:r.y,width:r.width,height:r.height,right:r.right,bottom:r.bottom};};
          return {innerWidth,innerHeight,sheet:rect(sheet),parent:rect(outer),
            viewport:rect(document.querySelector('.wpb')),stable:window.__b846StableBounds,
            classes:sheet?.className,position:sheet&&getComputedStyle(sheet).position,
            transform:sheet&&getComputedStyle(sheet).transform,
            media:getComputedStyle(sheet).maxHeight};
        });
        throw new Error('Gym sheet geometry never settled: '+JSON.stringify({width,height,geometry}),{cause:error});
      }
      const sheetBounds = await page.$eval('.wpb-gym-sheet', element => {
        const r=element.getBoundingClientRect();
        return { x:r.x,y:r.y,width:r.width,height:r.height,right:r.right,bottom:r.bottom };
      });
      assert.ok(sheetBounds.x>=-1&&sheetBounds.right<=width+1,
        'gym sheet fits viewport width');
      if(width>=840&&height>=640){
        assert.ok(sheetBounds.width<=742&&sheetBounds.width>=520,
          'expanded gym editor uses contained dialog instead of full-width phone drawer');
        assert.ok(Math.abs(sheetBounds.x+sheetBounds.width/2-width/2)<4,
          'expanded gym editor is centered on available window');
        assert.ok(sheetBounds.y>8&&sheetBounds.bottom<height-8,
          'expanded gym editor is centered vertically and scrollable');
      }else if(width<700){
        assert.ok(sheetBounds.width>=width-32&&sheetBounds.x>=-1&&sheetBounds.x<=16&&width-sheetBounds.right<=16&&sheetBounds.bottom>=height-2,
          'compact gym editor retains bottom-anchored near-full-width sheet with <=16px symmetric margins: '+JSON.stringify({width,height,sheetBounds}));
      }
      await page.$$eval('.wpb-gym-card button',buttons=>{
        const gym=buttons.find(b=>b.textContent.includes('Test Garage'));
        if(!gym)throw new Error('Test Garage gym missing');gym.click();
      });
      const box='.wpb-gym-bar-masses';
      await page.waitForSelector(box);
      const enlarge=async()=>{
        if(!enlarged)return;
        await page.$eval('.wpb-gym-sheet',panel=>{
          const elements=[panel,...panel.querySelectorAll('*')].filter(e=>!e.dataset.testTextEnlarged)
            .map(e=>[e,parseFloat(getComputedStyle(e).fontSize)]);
          for(const [element,size] of elements){element.style.fontSize=size*2+'px';element.dataset.testTextEnlarged='1';}
        });
      };
      await enlarge();
      const initialGyms=await page.evaluate(()=>{const state=JSON.parse(localStorage.getItem('wpb:v1'));return {gyms:state.gyms,active:state.activeGymId};});
      await page.$eval('details.wpb-gym-bar-masses',node=>node.open=true);
      const pairs='input[aria-label="45 lb plate pairs"]';
      await page.$eval(pairs,input=>{input.focus();input.select();});await page.keyboard.type('0');
      assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')).gyms),initialGyms.gyms,'typing plate counts does not save gym edits');
      await page.$eval('input[aria-label="Filter equipment by name"]',input=>{input.focus();input.select();});
      await page.keyboard.type('dumbbell');
      const equipment='.wpb-gym-equipment-chip';
      await page.waitForSelector(equipment);
      await page.focus(equipment);await page.keyboard.press('Enter');
      assert.equal(await page.$eval(equipment,button=>button.getAttribute('aria-pressed')),'false',
        'equipment can be staged with keyboard input');
      await page.keyboard.press('Escape');
      await page.waitForSelector('.wpb-gym-sheet',{hidden:true});
      assert.deepEqual(await page.evaluate(()=>{const state=JSON.parse(localStorage.getItem('wpb:v1'));return {gyms:state.gyms,active:state.activeGymId};}),initialGyms,
        'dismissing the editor discards staged equipment edits and preserves the active gym');
      await page.$$eval('button',buttons=>buttons.find(button=>button.textContent.includes('Your gyms')).click());
      await page.waitForSelector('.wpb-gym-card');
      await page.$$eval('.wpb-gym-card button',buttons=>buttons.find(button=>button.textContent.includes('Test Garage')).click());
      await page.waitForSelector(box);await enlarge();
      const reflow=await page.$eval('.wpb-gym-sheet',panel=>{
        const rect=panel.getBoundingClientRect();
        return {overflow:panel.scrollWidth>panel.clientWidth+1,left:rect.left,right:rect.right,
          height:rect.height,viewport:innerHeight,done:panel.querySelector('.wpb-gym-done').getBoundingClientRect().height};
      });
      assert.ok(!reflow.overflow&&reflow.left>=-1&&reflow.right<=width+1&&reflow.height<=height&&reflow.done>=48,
        'configuration sheet reflows without clipping at normal/enlarged text: '+JSON.stringify({width,height,theme,enlarged,reflow}));
      if(enlarged&&width<400){
        const heading=await page.$eval('.wpb-gym-sheet>.wpb-selection-heading',element=>{
          const rect=el=>el.getBoundingClientRect().toJSON();
          return {heading:rect(element),title:rect(element.querySelector('.wpb-gym-title')),
            description:rect(element.querySelector('.wpb-selection-intro')),done:rect(element.querySelector('.wpb-gym-done'))};
        });
        assert.ok(heading.title.right<=heading.done.left+1&&
          heading.description.top>=Math.max(heading.title.bottom,heading.done.bottom)-1&&
          heading.description.width>=heading.heading.width-1,
          'enlarged configuration guidance uses the full row below title and Done: '+JSON.stringify(heading));
      }
      const bar='input[aria-label="Barbell empty bar weight"]';
      const smith='input[aria-label="Smith empty bar weight"]';
      const change=async(selector,value)=>{
        await page.$eval(selector, input => { input.focus(); input.select(); });
        await page.keyboard.type(value);
      };
      const beforeInvalid=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')).gyms);
      await change(bar,'35..5');
      await page.$$eval('.wpb-gym-done',buttons=>buttons[0].click());
      assert.equal(await page.$eval(bar,node=>node.value),'35..5','a malformed bar mass stays raw for correction');
      assert.equal(await page.$eval(bar,node=>node.getAttribute('aria-invalid')),'true');
      assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')).gyms),beforeInvalid,'invalid bar mass cannot silently reset the saved bar');
      await change(bar,'35');
      await change(smith,'0');
      await page.$eval('details.wpb-gym-bar-masses',node=>node.open=true);
      assert.equal(await page.$eval(pairs,input=>input.value),'4','cancel discarded the staged count');
      await change(pairs,'51');
      await page.$$eval('.wpb-gym-done',buttons=>buttons[0].click());
      assert.equal(await page.$eval(pairs,node=>node.value),'51','out-of-range counts are not silently clamped');
      assert.equal(await page.$eval(pairs,node=>node.getAttribute('aria-invalid')),'true');
      await change(pairs,'0');
      assert.equal(await page.$eval(smith,e=>e.value),'0',
        'counterbalanced Smith of zero must remain editable');
      await page.click('.wpb-gym-bar-unit');
      assert.ok(Math.abs(Number(await page.$eval(bar,e=>e.value))-15.876)<.001,
        'unit change converts entered bar mass, not just label');
      await page.click('.wpb-gym-bar-unit');
      assert.equal(Number(await page.$eval(bar,node=>node.value)),35,'bar unit roundtrip retains 35 exactly');
      const cap='input[aria-label="Dumbbells maximum load"]';
      await change(cap,'50');
      await page.$$eval('[aria-label="Change gym load units"]',buttons=>buttons[0].click());
      assert.ok(Math.abs(Number(await page.$eval(cap,node=>node.value))-110.231)<.001,'gym ceiling converts instead of being relabelled');
      await page.$$eval('[aria-label="Change gym load units"]',buttons=>buttons[0].click());
      assert.equal(Number(await page.$eval(cap,node=>node.value)),50);
      const rack='input[aria-label="Dumbbells available loads"]';
      await change(rack,'5, nope, 25');
      await page.$$eval('.wpb-gym-done',buttons=>buttons[0].click());
      assert.equal(await page.$eval(rack,node=>node.value),'5, nope, 25','rack input does not discard malformed entries on blur');
      await change(rack,'5, 12.5, 25');
      assert.equal(await page.$eval(rack,node=>node.value),'5, 12.5, 25','typing does not remount the rack field');
      await page.$$eval('[aria-label="Change gym load units"]',buttons=>buttons[0].click());
      assert.ok(Math.abs(Number((await page.$eval(rack,node=>node.value)).split(',').at(-1))-55.116)<.001,'unit change converts every staged rack value');
      await page.$$eval('[aria-label="Change gym load units"]',buttons=>buttons[0].click());
      assert.equal(await page.$eval(rack,node=>node.value),'5, 12.5, 25');
      await page.click('.wpb-gym-done');
      await page.waitForFunction(()=>{
        const gym=JSON.parse(localStorage.getItem('wpb:v1')).gyms?.find(g=>g.id==='test_home');
        return gym?.barWeights?.smith===0 && Math.abs(gym.barWeights.barbell-35)<.01;
      });
      const saved=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')).gyms);
      assert.deepEqual(saved.find(g=>g.id==='test_home').inventory.dumbbell,[5,12.5,25],'converted raw rack text cannot overwrite physical values');
      assert.ok(saved.find(g=>g.id==='test_home').updatedAt>0,'Done retains the gym revision clock');
      assert.equal(saved.find(g=>g.id==='test_home').inventoryText,undefined,'raw inputs do not become stored gym data');
      assert.equal(saved.find(g=>g.id==='test_home').platePairs.lb[45],0);
      const calc=await page.evaluate(async()=>{const domain=await import('/modules/training-domain/analytics.js');return domain.platesPerSide(domain.barWeightFor('barbell','lb')+90,'barbell','lb');});
      assert.ok(Math.abs(calc.barW-35)<.01);assert.ok(!calc.plates.includes(45)&&calc.leftover===0,'active gym plate math uses the saved pair inventory');
      await page.reload({waitUntil:'networkidle0'});await page.waitForSelector('#root[data-pi-motion="expressive"]');
      assert.equal((await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')).gyms)).find(g=>g.id==='test_home').platePairs.lb[45],0,'pair counts survive reload');
      assert.equal(saved.find(g=>g.id==='test_elsewhere').barWeights.smith,15,'other gym untouched');
      assert.deepEqual(errors,[]);
      console.log('PASS B846 gym staged edits, cancel, reflow and unit conversion '+JSON.stringify({width,height,theme,enlarged}));
    }finally{await context.close();}
  }
}finally{if(browser)await browser.close();await new Promise(resolve=>server.close(resolve));}
