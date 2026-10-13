import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import puppeteer from 'puppeteer-core';

// Real dismissible forms and reading surfaces, using the ordinary persisted
// store. Enlarged text is measured independently from whole-page zoom.
const root=path.resolve(new URL('../',import.meta.url).pathname);
const program={id:'r74-accessibility',name:'Accessibility Fixture',custom:true,weeks:4,
  config:{name:'Accessibility Fixture',goal:'both',experience:'intermediate',
    progression:'manual',split:'custom',weeks:4,days:1,session:'s60',unit:'lb',deload:false},
  days:[{id:'upper',label:'Upper',primaryIndex:0,exercises:['bb-bench','inc-curl']}],
  overrides:{'upper:0':{sets:3,reps:'6-8',rir:'2'},'upper:1':{sets:2,reps:'10-15',rir:'2'}}};
const state=theme=>({v:13,savedAt:1,saved:[program],cycles:[],history:[],perf:{},drafts:{},tombs:{},
  seenIntro:999,seenWhatsNew:999,pinnedId:program.id,theme,unit:'lb',unitChosen:true,
  experience:'intermediate',bodyweight:185,installDismissedAt:Date.now(),lastBackup:Date.now()});
const live={schemaVersion:2,programId:program.id,dayId:'upper',weekIndex:1,
  dayExSig:'bb-bench|inc-curl',exIdx:0,elapsedMs:120000,
  readiness:{label:'Normal',factor:1},restPaused:false,restRemain:0,restMax:0,runPaused:true,
  data:[{id:'bb-bench',slot:0,note:'',sets:Array.from({length:3},()=>({weight:'185',reps:'6',done:false,
    auto:false,valueOwner:'user',target:{w:'185',reps:'6-8',rir:'2'}}))},
    {id:'inc-curl',slot:1,note:'',sets:Array.from({length:2},()=>({weight:'30',reps:'10',done:false,
      auto:false,valueOwner:'user',target:{w:'30',reps:'10-15',rir:'2'}}))}]};
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html',
  '.json':'application/json','.png':'image/png','.svg':'image/svg+xml'};
const server=http.createServer((req,res)=>{
  const name=new URL(req.url,'http://localhost').pathname;
  const file=path.resolve(root,'.'+(name==='/'?'/index.html':name));
  if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){
    res.writeHead(404);res.end();return;
  }
  res.setHeader('Content-Type',mime[path.extname(file)]||'application/octet-stream');
  res.end(fs.readFileSync(file));
});
await new Promise(resolve=>server.listen(8800,'127.0.0.1',resolve));
let browser;
try{
  browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',
    headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
  const results=[];
  for(const theme of ['rose','rosewater']){
    for(const [width,height] of [[320,740],[393,852],[600,800],[900,480],[900,1000],[1280,850],[1600,900]]
      .filter(([w,h])=>!process.argv[2]||process.argv[2]===w+'x'+h)){
      const context=await browser.createBrowserContext(),page=await context.newPage();
      const errors=[];page.on('pageerror',e=>errors.push(e.message));
      try{
        await page.setViewport({width,height,deviceScaleFactor:1,isMobile:width<600,hasTouch:width<600});
        await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
        await page.evaluateOnNewDocument(store=>localStorage.setItem('wpb:v1',JSON.stringify(store)),state(theme));
        await page.goto('http://127.0.0.1:8800/',{waitUntil:'networkidle0'});
        await page.click('[data-tab="settings"]');
        await page.waitForSelector('.wpb-settings');
        await page.evaluate(()=>{
          const opener=[...document.querySelectorAll('.wpb-settings button')]
            .find(el=>el.textContent.includes('Your gyms'));
          if(!opener)throw new Error('Your gyms action missing');
          opener.dataset.r74Opener='gyms';opener.scrollIntoView({block:'center'});opener.focus();
        });
        await page.keyboard.press('Enter');
        await page.waitForSelector('.wpb-gym-sheet');
        await page.waitForFunction(()=>document.querySelector('.wpb-gym-sheet')?.contains(document.activeElement));
        assert.equal(await page.$eval('.wpb-gym-sheet',el=>{
          const id=el.parentElement.getAttribute('aria-labelledby');
          return document.getElementById(id)?.textContent.trim();
        }),'Your gyms','Gym dialog has a real accessible title');
        assert.equal(await page.evaluate(()=>document.activeElement.matches('input,textarea')),false,
          'Opening a form does not summon the phone keyboard');
        const stops=await page.evaluate(()=>{
          const sheet=document.querySelector('.wpb-gym-sheet');
          const controls=[...sheet.querySelectorAll('button,input,select,textarea,[tabindex]')]
            .filter(el=>el.tabIndex>=0&&!el.disabled&&el.getClientRects().length &&
              getComputedStyle(el).visibility!=='hidden');
          controls.forEach((el,i)=>el.dataset.r74Stop=String(i));return controls.length;
        });
        assert.ok(stops>=2,'Gym form exposes real controls');
        await page.focus('[data-r74-stop="'+(stops-1)+'"]');
        await page.keyboard.press('Tab');
        assert.equal(await page.evaluate(()=>document.activeElement.dataset.r74Stop),'0',
          'Tab wraps within the top form');
        await page.keyboard.down('Shift');
        await page.keyboard.press('Tab');
        await page.keyboard.up('Shift');
        assert.equal(await page.evaluate(()=>document.activeElement.dataset.r74Stop),String(stops-1),
          'Shift+Tab wraps backwards within the top form');
        await page.evaluate(()=>document.activeElement.dispatchEvent(new KeyboardEvent('keydown',
          {key:'Escape',isComposing:true,bubbles:true,cancelable:true})));
        assert.ok(await page.$('.wpb-gym-sheet'),'IME composition owns Escape');
        if(width<600){
          // Model Android's layout viewport staying full height while the
          // keyboard reduces the visual viewport. This is browser evidence,
          // not a physical IME/TalkBack certification.
          const visibleHeight=Math.round(height*.55);
          await page.click('.wpb-gym-card>div:first-child>button:nth-child(2)');
          await page.waitForSelector('.wpb-gym-sheet input');
          const keyboardInput='.wpb-gym-sheet input[aria-label="Smith empty bar weight"]';
          await page.focus(keyboardInput);
          const inputValue=await page.$eval(keyboardInput,el=>el.value);
          await page.evaluate(h=>{
            Object.defineProperty(visualViewport,'height',{configurable:true,get:()=>h});
            visualViewport.dispatchEvent(new Event('resize'));
          },visibleHeight);
          await page.waitForFunction(h=>parseFloat(document.documentElement.style.getPropertyValue('--visual-h'))===h,{},visibleHeight);
          await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
          const keyboardSheet=await page.$eval('.wpb-gym-sheet',el=>{
            const r=el.getBoundingClientRect();return {top:r.top,bottom:r.bottom,height:r.height,
              scrollable:el.scrollHeight>el.clientHeight,overflow:getComputedStyle(el).overflowY,
              visual:getComputedStyle(el).getPropertyValue('--visual-h'),max:getComputedStyle(el).maxHeight,
              parentMax:getComputedStyle(el.parentElement).maxHeight,parentHeight:el.parentElement.getBoundingClientRect().height,
              transitions:getComputedStyle(el).transition,parentTransitions:getComputedStyle(el.parentElement).transition};
          });
          assert.ok(keyboardSheet.top>=0&&keyboardSheet.bottom<=visibleHeight+1,
            'Gym form stays above the emulated phone keyboard: '+JSON.stringify({visibleHeight,keyboardSheet}));
          assert.equal(await page.$eval(keyboardInput,el=>el.value),inputValue,
            'Keyboard reflow preserves the live form value');
          const field=await page.$eval(keyboardInput,el=>{
            const r=el.getBoundingClientRect();return {top:r.top,bottom:r.bottom,
              hit:el.contains(document.elementFromPoint(r.x+r.width/2,r.y+r.height/2))};
          });
          assert.ok(field.top>=keyboardSheet.top&&field.bottom<=visibleHeight+1&&field.hit,
            'Focused Smith weight stays visible above the keyboard: '+JSON.stringify({visibleHeight,field}));
          await page.evaluate(()=>{delete visualViewport.height;visualViewport.dispatchEvent(new Event('resize'));});
        }
        await page.keyboard.press('Escape');
        await page.waitForSelector('.wpb-gym-sheet',{hidden:true});
        assert.equal(await page.evaluate(()=>document.activeElement.dataset.r74Opener),'gyms',
          'Escape restores the actual form opener');
        assert.equal(await page.$$eval('[data-wpb-lock]',els=>els.length),0,'Dismissal releases background scrolling');
        if(width===393||width===1280){
          await page.evaluate(session=>localStorage.setItem('wpb:live',JSON.stringify(session)),live);
          await page.reload({waitUntil:'networkidle0'});
          await page.waitForSelector('.wpb-live-dock button[aria-label="Resume workout"]');
          await page.click('.wpb-live-dock button[aria-label="Resume workout"]');
          await page.waitForSelector('[data-testid="set-0-0"] input[aria-label="weight"]');
          const values=await page.$$eval('[data-testid="set-0-0"] input',els=>els.map(el=>el.value));
          await page.click('button[aria-label^="Exercise info for "]');
          await page.waitForSelector('.wpb-backdrop [data-sheet-drag]');
          await page.evaluate(()=>{
            const opener=[...document.querySelectorAll('.wpb-workout button')]
              .find(el=>el.textContent.includes('Set a goal weight'));
            if(!opener)throw new Error('Workout goal opener missing');
            opener.dataset.r74Opener='goal';opener.scrollIntoView({block:'center'});opener.focus();
          });
          await page.keyboard.press('Enter');
          await page.waitForSelector('input[aria-label="Goal weight"]');
          await page.waitForFunction(()=>{
            const dialog=document.querySelector('input[aria-label="Goal weight"]')?.closest('.wpb-backdrop');
            return dialog?.hasAttribute('aria-labelledby');
          });
          assert.equal(await page.$eval('input[aria-label="Goal weight"]',el=>{
            const id=el.closest('.wpb-backdrop').getAttribute('aria-labelledby');
            return document.getElementById(id)?.textContent.trim();
          }),'Goal weight','Nested goal form has a real accessible title');
          await page.keyboard.press('Escape');
          await page.waitForSelector('input[aria-label="Goal weight"]',{hidden:true});
          assert.equal(await page.$$eval('.wpb-backdrop:not(.wpb-closing .wpb-backdrop)',els=>els.length),1,
            'Escape dismisses only the nested form');
          assert.equal(await page.evaluate(()=>document.activeElement.dataset.r74Opener),'goal',
            'An autofocus dialog restores the actual workout action');
          assert.deepEqual(await page.$$eval('[data-testid="set-0-0"] input',els=>els.map(el=>el.value)),values,
            'Cancelling a goal dialog preserves typed sets');
          await page.keyboard.press('Escape');
          await page.waitForSelector('.wpb-backdrop',{hidden:true});
          await page.evaluate(()=>history.back());
          await page.waitForSelector('.wpb-tabbar');
        }
        // Read actual text at twice its rendered size, including px-based
        // legacy labels. This is text enlargement, not a pinch-zoom claim.
        for(const route of ['home','plan','progress','profile','settings']){
          await page.focus('[data-tab="'+route+'"]');
          await page.keyboard.press('Enter');
          await page.waitForSelector('[data-view-frame="'+route+'"]');
          await page.evaluate(()=>{
            window.__r74Text=[...document.querySelectorAll('.wpb *')]
              .filter(el=>el instanceof HTMLElement && el.tagName!=='STYLE')
              .map(el=>({el,size:parseFloat(getComputedStyle(el).fontSize),
                value:el.style.getPropertyValue('font-size'),priority:el.style.getPropertyPriority('font-size')}));
            window.__r74Text.forEach(({el,size})=>el.style.setProperty('font-size',size*2+'px','important'));
          });
          await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
          const enlarged=await page.evaluate(()=>{
            const tabs=[...document.querySelectorAll('.wpb-tabbar button')].map(button=>{
              const label=button.querySelector('span'),range=document.createRange();
              range.selectNodeContents(label);const text=range.getBoundingClientRect(),box=button.getBoundingClientRect();
              return {label:button.getAttribute('aria-label'),text:{x:text.x,right:text.right,y:text.y,bottom:text.bottom},
                box:{x:box.x,right:box.right,y:box.y,bottom:box.bottom},
                hit:button.contains(document.elementFromPoint(box.x+box.width/2,box.y+box.height/2))};
            });
            const next=document.querySelector('.wpb-plan-up-next'),overview=document.querySelector('.wpb-plan-overview');
            const plan=next&&overview?{
              nextBottom:next.getBoundingClientRect().bottom,overviewTop:overview.getBoundingClientRect().top,
              nextHeight:next.getBoundingClientRect().height,nextCss:getComputedStyle(next).height,
              min:getComputedStyle(next).minHeight,max:getComputedStyle(next).maxHeight,
              transform:getComputedStyle(next).transform,rows:getComputedStyle(next.parentElement).gridTemplateRows,
              overviewRow:getComputedStyle(overview).gridRow}:null;
            return {overflow:document.documentElement.scrollWidth>innerWidth+2,tabs,plan};
          });
          if(width===320||width===1280)
            await page.screenshot({path:path.join(root,'verification/r74-'+theme+'-'+width+'-'+route+'-phone.png')});
          assert.equal(enlarged.overflow,false,'200% '+route+' text keeps document width: '+JSON.stringify({theme,width,enlarged}));
          assert.ok(enlarged.tabs.every(t=>t.hit&&t.text.x>=t.box.x-1&&t.text.right<=t.box.right+1&&
            t.text.y>=t.box.y-1&&t.text.bottom<=t.box.bottom+1),
            '200% '+route+' navigation labels fit their real controls: '+JSON.stringify({theme,width,enlarged}));
          if(route==='plan'&&width>=1060&&height>=700){
            assert.ok(enlarged.plan.nextBottom<=enlarged.plan.overviewTop+1,
              'Enlarged Plan supporting cards stay in separate rows: '+JSON.stringify(enlarged.plan));
          }
          await page.evaluate(()=>{
            window.__r74Text.forEach(({el,value,priority})=>value?
              el.style.setProperty('font-size',value,priority):el.style.removeProperty('font-size'));
            delete window.__r74Text;
          });
          await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
        }
        results.push({theme,width,height,stops});
        assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')).saved),[program],
          'Accessibility interactions preserve saved prescriptions');
        assert.deepEqual(errors,[],'No runtime errors');
        console.log('PASS R74 '+theme+' '+width+'x'+height);
      }finally{await context.close();}
    }
  }
  assert.equal(results.length,process.argv[2]?2:14,'Every requested R74 scenario ran');
  console.log('PASS R74 form focus and dismissal: '+JSON.stringify(results));
}finally{
  if(browser)await browser.close();
  await new Promise(resolve=>server.close(resolve));
}
