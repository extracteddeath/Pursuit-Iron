import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import puppeteer from 'puppeteer-core';

// R76: real Profile/Progress/Settings routes, not a static UI mock.
// Text enlargement is distinct from layout viewport zoom, and no app data
// may change from inspection alone. Keep workout inputs out of this scope.
const root=path.resolve(new URL('../',import.meta.url).pathname);
const program={id:'r76-access',name:'Accessible Program',custom:true,weeks:4,
  config:{name:'Accessible Program',goal:'both',experience:'intermediate',
    progression:'manual',split:'custom',weeks:4,days:1,session:'s60',unit:'lb',deload:false},
  days:[{id:'upper',label:'Upper',primaryIndex:0,exercises:['bb-bench','inc-curl']}],
  overrides:{'upper:0':{sets:3,reps:'6-8',rir:'2'},'upper:1':{sets:2,reps:'10-15',rir:'2'}}};
const initial=theme=>({v:13,savedAt:1,saved:[program],cycles:[],history:[],perf:{},
  drafts:{},tombs:{},seenIntro:999,seenWhatsNew:999,pinnedId:program.id,
  theme,unit:'lb',unitChosen:true,experience:'intermediate',bodyweight:185,
  installDismissedAt:Date.now(),lastBackup:Date.now()});
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html',
  '.json':'application/json','.webmanifest':'application/manifest+json',
  '.png':'image/png','.svg':'image/svg+xml'};
const server=http.createServer((req,res)=>{
  const name=new URL(req.url,'http://localhost').pathname;
  const file=path.resolve(root,'.'+(name==='/'?'/index.html':name));
  if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){
    res.writeHead(404);res.end();return;
  }
  res.setHeader('Content-Type',mime[path.extname(file)]||'application/octet-stream');
  res.end(fs.readFileSync(file));
});
await new Promise(resolve=>server.listen(8807,'127.0.0.1',resolve));
const settle=page=>page.evaluate(()=>new Promise(done=>requestAnimationFrame(
  ()=>requestAnimationFrame(done))));
let browser;
try{
  browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',
    headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
  for(const theme of ['rose','rosewater']) for(const [width,height] of
      [[320,740],[393,852],[900,1000],[1280,850]]){
    const context=await browser.createBrowserContext(),page=await context.newPage(),errors=[];
    page.on('pageerror',error=>errors.push(error.message));
    try{
      await page.setViewport({width,height,deviceScaleFactor:1,isMobile:width<600,hasTouch:width<600});
      await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
      await page.evaluateOnNewDocument(data=>localStorage.setItem('wpb:v1',JSON.stringify(data)),initial(theme));
      await page.goto('http://127.0.0.1:8807/',{waitUntil:'networkidle0'});
      await page.waitForSelector('.wpb-tabbar');
      for(const [route,selector,actions] of [
        ['profile','.wpb-profile',['.wpb-profile-edit']],
        ['progress','.wpb-progress',['.wpb-premium-tabs>button']],
        ['settings','.wpb-settings',['.wpb-settings-jump-control select']]
      ]){
        await page.click('[data-tab="'+route+'"]');
        await page.waitForSelector(selector);
        await settle(page);
        // M283: scrolling content and the canonical root header must share a
        // single left/right inset on narrow phones (Plan and Home are covered
        // separately by the R62 Plan and Home continuity contract).
        if(width<=420&&(route==='progress'||route==='settings')){
          const alignment=await page.$eval(selector,pane=>{
            const header=pane.querySelector('.wpb-root-header');
            const body=pane.querySelector(':scope > .wpb-page-scroll');
            const h=getComputedStyle(header),b=getComputedStyle(body);
            const rail=pane.querySelector(':scope > .wpb-settings-jump');
            return {headerLeft:parseFloat(h.paddingLeft),headerRight:parseFloat(h.paddingRight),
              contentLeft:parseFloat(b.paddingLeft),contentRight:parseFloat(b.paddingRight),
              headerBottom:header.getBoundingClientRect().bottom,
              railTop:rail?.getBoundingClientRect().top,
              headerOpaque:h.backgroundColor!=='rgba(0, 0, 0, 0)',
              expected:innerWidth<360?12:16};
          });
          assert.ok(alignment.headerLeft===alignment.expected&&
            alignment.headerRight===alignment.expected&&
            alignment.contentLeft===alignment.expected&&
            alignment.contentRight===alignment.expected&&alignment.headerOpaque&&
            (route!=='settings'||Math.abs(alignment.railTop-alignment.headerBottom)<=1),
            'Root header and mobile scroll pane share one inset without a Settings rail gap: '+
              JSON.stringify({route,theme,width,alignment}));
        }
        // R82: Settings navigation uses honest, findable category names.
        // It must reach the existing controls without changing their values.
        if(route==='settings'){
          const groups=await page.$eval('.wpb-settings',pane=>{
            const cards=[...pane.querySelectorAll('.wpb-settings-card')];
            const rows=[...pane.querySelectorAll('.wpb-settings-row')];
            const paint=e=>getComputedStyle(e).backgroundColor;
            return {count:cards.length,shared:cards.every(c=>c.classList.contains('wpb-ui-card')),
              opaque:cards.every(c=>paint(c)===paint(cards[0])&&paint(c)!=='rgba(0, 0, 0, 0)'),
              owned:rows.every(r=>r.parentElement.classList.contains('wpb-settings-card')),
              min:Math.min(...rows.map(r=>r.getBoundingClientRect().height))};
          });
          assert.ok(groups.count===8&&groups.shared&&groups.opaque&&groups.owned&&groups.min>=48,
            'Every Settings row belongs to an explicit shared card: '+JSON.stringify(groups));
          // M279: one final category rail, consistent 48px controls and legible section labels.
          const hierarchy=await page.$eval('.wpb-settings',pane=>{
            const rail=pane.querySelector('.wpb-settings-jump');
            const control=pane.querySelector('.wpb-settings-jump-control');
            const select=control?.querySelector('select');
            const title=pane.querySelector('.wpb-settings-title');
            const description=pane.querySelector('.wpb-settings-row-description');
            const railRect=rail?.getBoundingClientRect();
            const jumpRect=control?.getBoundingClientRect();
            return {
              railBottom:parseFloat(getComputedStyle(rail).paddingBottom),
              opaque:getComputedStyle(rail).backgroundColor!=='rgba(0, 0, 0, 0)',
              jumpHeight:jumpRect?.height,selectHeight:select?.getBoundingClientRect().height,
              jumpRadius:parseFloat(getComputedStyle(control).borderTopLeftRadius),
              titleSize:parseFloat(getComputedStyle(title).fontSize),
              descriptionSize:parseFloat(getComputedStyle(description).fontSize),
              withinViewport:railRect?.left>=-1&&railRect?.right<=innerWidth+1,
              visibleLabelHidden:getComputedStyle(control.querySelector('span')).display==='none',
              nativeSelectNamed:select.getAttribute('aria-label')==='Go to Settings section',
              selectWidth:select.getBoundingClientRect().width
            };
          });
          assert.ok(hierarchy.opaque&&hierarchy.railBottom>=8&&hierarchy.jumpHeight>=48&&
            hierarchy.selectHeight>=48&&Math.abs(hierarchy.jumpRadius-18)<1&&
            hierarchy.titleSize>=13&&hierarchy.descriptionSize>=13&&hierarchy.withinViewport&&
            hierarchy.nativeSelectNamed&&hierarchy.selectWidth>=130&&
            hierarchy.visibleLabelHidden===(width<=420),
            'Settings category rail and readable controls use shared sizes: '+JSON.stringify(hierarchy));
          const destinations=await page.$$eval('.wpb-settings-jump-control select option',
            options=>options.map(el=>[el.value,el.textContent.trim()]));
          assert.ok(destinations.some(([id,label])=>id==='training'&&label==='Gyms & equipment'),
            'Settings directs equipment lookup to the actual gym section');
          assert.ok(destinations.some(([id,label])=>id==='preferences'&&label==='Training preferences'),
            'Settings exposes training options without hiding them under Units');
          const headings=await page.$eval('.wpb-settings .wpb-page-scroll',
            pane=>pane.textContent);
          assert.ok(headings.includes('Training preferences')&&
            headings.includes('Training experience')&&headings.includes('Effort scale')&&
            headings.includes('Smallest jump')&&headings.includes('Weight unit'),
            'Related training controls remain together and readable');
          assert.equal(await page.$$eval('.wpb-settings [data-settings-anchor="preferences"]',
            anchors=>anchors.length),1,'Exactly one destination owns training preferences');
          await page.select('.wpb-settings-jump-control select','preferences');
          await page.waitForFunction(()=>document.querySelector('.wpb-settings .wpb-page-scroll')?.scrollTop>10);
          await settle(page);
          if(width<=420){
            const chrome=await page.$eval('.wpb-settings',pane=>{
              const header=pane.querySelector('.wpb-root-header');
              const rail=pane.querySelector('.wpb-settings-jump');
              const content=pane.querySelector('.wpb-page-scroll');
              return {offset:content.scrollTop,
                gap:rail.getBoundingClientRect().top-header.getBoundingClientRect().bottom,
                headerPaint:getComputedStyle(header).backgroundColor,
                railPaint:getComputedStyle(rail).backgroundColor};
            });
            assert.ok(chrome.offset>10&&Math.abs(chrome.gap)<=1&&
              chrome.headerPaint!=='rgba(0, 0, 0, 0)'&&chrome.railPaint!=='rgba(0, 0, 0, 0)',
              'Settings category rail remains directly attached to opaque header after scroll: '+JSON.stringify({width,theme,chrome}));
          }
          assert.equal(await page.$eval('.wpb-settings-jump-control select',el=>el.value),'',
            'Settings jump resets for the next deliberate navigation');
          if(width===320){
            await page.focus('.wpb-theme-picker>summary');
            await page.keyboard.press('Enter');
            await page.waitForSelector('.wpb-theme-picker[open]');
            const ordinary=await page.$$eval('.wpb-theme-option',options=>
              options.map(el=>parseFloat(getComputedStyle(el).fontSize)));
            const rootText=await page.evaluate(()=>{
              const root=document.documentElement;
              const old=root.style.fontSize;
              root.style.fontSize=parseFloat(getComputedStyle(root).fontSize)*2+'px';
              return old;
            });
            await settle(page);
            const choices=await page.$$eval('.wpb-theme-option',options=>options.map(el=>{
              const box=el.getBoundingClientRect(),label=el.lastElementChild;
              const range=document.createRange();range.selectNodeContents(label);
              return {name:label.textContent,size:parseFloat(getComputedStyle(el).fontSize),
                width:box.width,height:box.height,
                fits:[...range.getClientRects()].every(r=>r.left>=box.left-1&&r.right<=box.right+1)};
            }));
            assert.ok(choices.length===ordinary.length&&choices.every((c,i)=>
              c.size>=ordinary[i]*1.95&&c.width>=44&&c.height>=44&&c.fits),
              'Theme names scale with actual 200% root text and retain usable targets: '+JSON.stringify({ordinary,choices}));
            await page.screenshot({path:path.join(root,'verification/b938-themes-'+theme+'-enlarged-phone.png')});
            await page.evaluate(old=>document.documentElement.style.fontSize=old,rootText);
            // Use the native keyboard action, verify persisted theme selection,
            // and return to the fixture's palette before the geometry matrix.
            const alternate=theme==='rose'?'amethyst':'sage';
            await page.focus('[data-theme-option="'+alternate+'"]');
            await page.keyboard.press('Space');
            await page.waitForFunction(id=>JSON.parse(localStorage.getItem('wpb:v1')).theme===id,{},alternate);
            assert.equal(await page.$eval('[data-theme-option="'+alternate+'"]',el=>el.getAttribute('aria-pressed')),'true');
            await page.focus('[data-theme-option="'+theme+'"]');
            await page.keyboard.press('Space');
            await page.waitForFunction(id=>JSON.parse(localStorage.getItem('wpb:v1')).theme===id,{},theme);
            await page.focus('.wpb-theme-picker>summary');
            await page.keyboard.press('Enter');
            await settle(page);
            const training=await page.evaluate(()=>{
              const data=JSON.parse(localStorage.getItem('wpb:v1'));
              return {saved:data.saved.map(({updatedAt,...program})=>program),history:data.history,cycles:data.cycles,perf:data.perf};
            });
            for(const name of ['Weight unit','Training experience','Effort scale','Smallest jump','Rest length','Sex']){
              const selector='[role="group"][aria-label="'+name+'"]';
              const old=await page.$eval(selector,group=>group.querySelector('[aria-pressed="true"]').textContent);
              const option=await page.$eval(selector,group=>group.querySelector('[aria-pressed="false"]').textContent);
              await page.focus(selector+'>button[aria-pressed="false"]');
              await page.keyboard.press('Space');
              await page.waitForFunction(({selector,text})=>
                document.querySelector(selector)?.querySelector('[aria-pressed="true"]')?.textContent===text,{},
                {selector,text:option});
              const field={'Weight unit':'unit','Training experience':'experience','Effort scale':'loadMode',
                'Smallest jump':'minInc','Rest length':'restScale','Sex':'sex'}[name];
              const expected=name==='Weight unit'?option:
                name==='Training experience'?({'New':'novice','Beginner':'beginner','Intermediate':'intermediate','Advanced':'advanced'}[option]):
                name==='Effort scale'?option.toLowerCase():
                name==='Smallest jump'?{v:option==='Std'?0:Number(option),unit:option==='Std'?null:'lb'}:
                name==='Rest length'?({'Short':.7,'Standard':1,'Long':1.3}[option]):option.toLowerCase();
              await page.waitForFunction(({field,expected})=>
                JSON.stringify(JSON.parse(localStorage.getItem('wpb:v1'))[field])===JSON.stringify(expected),{},
                {field,expected});
              const handle=await page.$(selector);
              const buttons=await handle.$$('button');
              for(const button of buttons)if(await button.evaluate((el,text)=>el.textContent===text,old)){
                await button.focus();await page.keyboard.press('Space');break;
              }
              await handle.dispose();
              await page.waitForFunction(({selector,text})=>
                document.querySelector(selector)?.querySelector('[aria-pressed="true"]')?.textContent===text,{},
                {selector,text:old});
            }
            assert.deepEqual(await page.evaluate(()=>{
              const data=JSON.parse(localStorage.getItem('wpb:v1'));
              return {saved:data.saved.map(({updatedAt,...program})=>program),history:data.history,cycles:data.cycles,perf:data.perf};
            }),training,'Reverting preferences restores authored content and logged facts; edit timestamps may advance');
          }
        }
        const before=await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')).saved);
        // Use the computed initial text size, then modify ONLY typography.
        // Restore every exact inline declaration (including priority) later.
        await page.evaluate(sel=>{
          window.__r76Text=[...document.querySelectorAll(sel+', '+sel+' *')]
            .filter(el=>el instanceof HTMLElement)
            .map(el=>({el,size:parseFloat(getComputedStyle(el).fontSize),
              old:el.style.getPropertyValue('font-size'),
              priority:el.style.getPropertyPriority('font-size')}));
          for(const item of window.__r76Text)
            if(Number.isFinite(item.size))
              item.el.style.setProperty('font-size',item.size*2+'px','important');
        },selector);
        await settle(page);
        const geometry=await page.evaluate(({sel,actions})=>{
          const area=document.querySelector(sel),rect=area.getBoundingClientRect();
          const measure=selector=>[...area.querySelectorAll(selector)].map(el=>{
            el.scrollIntoView({block:'nearest'});
            const r=el.getBoundingClientRect();
            return {name:el.getAttribute('aria-label')||el.textContent.trim().slice(0,50),
              width:r.width,height:r.height,rects:el.getClientRects().length,
              disabled:el.disabled};
          });
          return {pageOverflow:document.documentElement.scrollWidth>innerWidth+2,
            areaOverflow:area.scrollWidth>area.clientWidth+2,
            width:rect.width,
            actionGroups:actions.map(measure)};
        },{sel:selector,actions});
        if(route==='settings'){
          const rows=await page.$$eval('.wpb-settings-row',els=>els.map(row=>{
            const box=row.getBoundingClientRect();
            const children=[...row.children].map(el=>el.getBoundingClientRect());
            const copy=row.querySelector('.wpb-settings-row-copy');
            const range=document.createRange();range.selectNodeContents(copy);
            const copyBox=copy.getBoundingClientRect(),control=row.querySelector('.wpb-settings-row-control');
            const controlBox=control?.getBoundingClientRect();
            return {label:copy.textContent.slice(0,45),stacked:getComputedStyle(row).flexDirection==='column',
              copyFullWidth:copyBox.width>=box.width-32,
              controlBelow:!controlBox||controlBox.top>=copyBox.bottom-1,
              fits:children.every(r=>r.left>=box.left-1&&r.right<=box.right+1)&&
                [...range.getClientRects()].every(r=>r.left>=box.left-1&&r.right<=box.right+1)};
          }));
          assert.ok(rows.every(r=>r.fits),'200% Settings row text and controls stay inside their card: '+JSON.stringify(rows));
          if(width===320||width===393)
            assert.ok(rows.length>0&&rows.every(r=>r.stacked&&r.copyFullWidth&&r.controlBelow),
              '200% Settings text uses full row before controls rather than splitting words: '+JSON.stringify(rows));
          await page.select('.wpb-settings-jump-control select','preferences');
          await settle(page);
          if(width===320||width===393)await page.screenshot({path:path.join(root,'verification/c20-settings-'+theme+'-'+width+'-enlarged-phone.png')});
        }
        // R77 fixes a failure R76's overflow-only check could not see:
        // labels were visually chopped into individual letters without overflow.
        // Inspect the real rendered responsive grid after enlarged typography.
        if(width===320 && route==='profile'){
          const columns=await page.evaluate(()=>getComputedStyle(
            document.querySelector('.wpb-profile-stat-grid')).gridTemplateColumns
              .trim().split(/\s+/).length);
          assert.equal(columns,1,'Profile metrics reflow to one readable column at 200%');
        }
        if(width===320 && route==='progress'){
          const columns=await page.evaluate(()=>getComputedStyle(
            document.querySelector('.wpb-progress .wpb-premium-tabs')).gridTemplateColumns
              .trim().split(/\s+/).length);
          assert.equal(columns,2,'Progress destination tabs reflow to two readable columns at 200%');
        }
        assert.equal(geometry.pageOverflow,false,route+' has no document horizontal overflow at 200%: '+JSON.stringify(geometry));
        assert.equal(geometry.areaOverflow,false,route+' has no panel horizontal overflow at 200%: '+JSON.stringify(geometry));
        assert.ok(geometry.actionGroups.every(group=>group.length>0),
          route+' has its real action controls: '+JSON.stringify(geometry));
        for(const group of geometry.actionGroups) for(const target of group){
          assert.ok(target.width>=44&&target.height>=44,
            route+' action remains a practical 44px target: '+JSON.stringify(target));
        }
        await page.evaluate(()=>{
          for(const {el,old,priority} of window.__r76Text)
            if(old)el.style.setProperty('font-size',old,priority);
            else el.style.removeProperty('font-size');
          delete window.__r76Text;
        });
        assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('wpb:v1')).saved),
          before,route+' typography inspection never edits the saved program');
        await page.screenshot({path:path.join(root,'verification/r76-'+route+'-'+theme+'-'+width+'-phone.png')});
      }
      assert.deepEqual(errors,[],'Browser routes have no uncaught errors');
      console.log('PASS R76 '+theme+' '+width+'x'+height+
        ': Profile, Progress and Settings enlarged labels/targets and unchanged saved plan');
    }finally{await context.close();}
  }
}finally{await browser?.close();await new Promise(resolve=>server.close(resolve));}
