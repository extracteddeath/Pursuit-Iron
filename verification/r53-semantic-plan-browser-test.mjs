import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import puppeteer from 'puppeteer-core';

// R53: two real saved-palette contexts, same program, same phone geometry.
// Both layouts must resolve the same semantic surface roles. Screenshot
// artifacts are PAIRS rather than cherry-picked screenshots of one theme.
const root=path.resolve(new URL('../',import.meta.url).pathname);
const program={
  id:'r53-surface',name:'R53 Plan Surface',custom:true,weeks:4,
  config:{name:'R53 Plan Surface',goal:'both',experience:'intermediate',progression:'double',split:'custom',weeks:4,days:2,session:'s60',unit:'lb',deload:false},
  days:[
    {id:'upper',label:'Upper',primaryIndex:0,exercises:['bb-bench','inc-curl']},
    {id:'lower',label:'Lower',primaryIndex:1,exercises:['back-squat','leg-press']}
  ],
  overrides:{
    'upper:0':{sets:4,reps:'6-8',rir:'2'},'upper:1':{sets:3,reps:'10-15',rir:'2'},
    'lower:0':{sets:4,reps:'5-8',rir:'2'},'lower:1':{sets:3,reps:'8-12',rir:'2'}
  }
};
const initial=theme=>({
  v:13,savedAt:1,saved:[program],cycles:[],history:[],perf:{},drafts:{},tombs:{},
  seenIntro:999,seenWhatsNew:999,pinnedId:program.id,theme,
  unit:'lb',unitChosen:true,experience:'intermediate',bodyweight:185,
  installDismissedAt:Date.now(),lastBackup:Date.now()
});
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html','.json':'application/json','.png':'image/png','.svg':'image/svg+xml','.webmanifest':'application/manifest+json'};
const server=http.createServer((req,res)=>{
  const name=new URL(req.url,'http://localhost').pathname;
  const file=path.resolve(root,'.'+(name==='/'?'/index.html':name));
  if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){res.writeHead(404);res.end();return;}
  res.setHeader('Content-Type',mime[path.extname(file)]||'application/octet-stream');
  res.end(fs.readFileSync(file));
});
await new Promise(resolve=>server.listen(8795,'127.0.0.1',resolve));
let browser;
try{
  browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
  const captures={rose:{},rosewater:{}},measurements={};
  for(const theme of ['rose','rosewater']){
    const context=await browser.createBrowserContext();
    const page=await context.newPage();
    const errors=[];
    page.on('pageerror',e=>errors.push(e.message));
    await page.setViewport({width:393,height:852,deviceScaleFactor:1,isMobile:true,hasTouch:true});
    await page.emulateMediaFeatures([{name:'prefers-reduced-motion',value:'reduce'}]);
    await page.evaluateOnNewDocument(store=>{
      localStorage.setItem('wpb:v1',JSON.stringify(store));
    },initial(theme));
    await page.goto('http://127.0.0.1:8795/',{waitUntil:'networkidle0'});
    await page.waitForSelector('.wpb-tabbar');
    const surfaces={};
    for(const tab of ['home','plan','progress','profile','settings']){
      await page.click('[data-tab="'+tab+'"]');
      await page.waitForSelector('[data-view-frame="'+tab+'"][data-pi-surface]');
      await page.waitForFunction(t=>document.querySelector('[data-view-frame="'+t+'"]')?.style.getPropertyValue('--pi-surface-opacity')==='1',{},tab);
      const basic=await page.evaluate(t=>{
        const root=document.querySelector('.wpb');
        const nav=root.querySelector('.wpb-tabbar');
        return {theme:JSON.parse(localStorage.getItem('wpb:v1')).theme,
          current:nav.querySelector('[aria-current="page"]')?.dataset.tab,
          overflow:root.scrollWidth>root.clientWidth+1,
          nav:getComputedStyle(nav).backgroundColor,
          page:getComputedStyle(document.querySelector(t==='plan'?'.wpb-plan-view':'.wpb-'+t)).backgroundColor};
      },tab);
      assert.equal(basic.theme,theme,'Selected saved theme persists across destinations');
      assert.equal(basic.current,tab,'Correct bottom navigation destination remains selected');
      assert.ok(!basic.overflow,'No horizontal scrolling at 393px: '+JSON.stringify(basic));
      if(tab==='plan'){
        Object.assign(basic,await page.evaluate(()=>{
          const el=sel=>document.querySelector(sel);
          const css=(sel)=>{const x=el(sel);return x?getComputedStyle(x).backgroundColor:null};
          const chip=el('.wpb-plan-week-chip[aria-pressed="true"]');
          const all=[...document.querySelectorAll('.wpb-plan-week-card')];
          return {
            weekCard:css('.wpb-plan-week-card'),
            weekImage:el('.wpb-plan-week-card')&&getComputedStyle(el('.wpb-plan-week-card')).backgroundImage,
            nested:css('.wpb-plan-session-link'),weekCount:all.length,
            currentCount:all.filter(x=>x.dataset.currentWeek==='true').length,
            chip:chip?.textContent.trim()||null,
            chipSelected:chip?.getAttribute('aria-pressed')==='true'
          };
        }));
        assert.ok(basic.weekCount>=2&&basic.currentCount===1&&basic.weekImage==='none',
          'Plan has real semantic week cards with exactly one current week: '+JSON.stringify(basic));
        assert.ok(basic.nested&&basic.chipSelected,'Plan nested row and active week are accessible: '+JSON.stringify(basic));
      }
      // R57: the other four destinations have distinct content structures,
      // but share density, ≥44dp secondary controls, and no clipping.
      if(tab!=='home'){
        const measure=()=>page.evaluate(which=>{
          const el=s=>document.querySelector(s);
          const box=s=>{const n=el(s);if(!n)return null;const r=n.getBoundingClientRect();
            return {top:r.top,bottom:r.bottom,height:r.height,width:r.width,left:r.left,right:r.right};};
          const root=el('.wpb');
          const out={overflow:root.scrollWidth>root.clientWidth+1,viewport:innerWidth};
          if(which==='plan'){
            out.row=box('.wpb-plan-heading-row');
            out.back=box('.wpb-plan-heading-row button[aria-label="Back"]');
            out.title=box('.wpb-plan-title');
            out.titleSize=parseFloat(getComputedStyle(el('.wpb-plan-title')).fontSize);
          }else if(which==='progress'){
            out.header=box('.wpb-progress>.wpb-page-header');
            out.titleSize=parseFloat(getComputedStyle(el('.wpb-progress-title')).fontSize);
            out.tabs=[...document.querySelectorAll('.wpb-progress .wpb-premium-tabs>button')]
              .map(b=>{const r=b.getBoundingClientRect();return {width:r.width,height:r.height}});
          }else if(which==='settings'){
            out.header=box('.wpb-settings>.wpb-page-header');
            out.jump=box('.wpb-settings>.wpb-settings-jump');
            out.picker=box('.wpb-settings-jump-control');
            out.titleSize=parseFloat(getComputedStyle(el('.wpb-settings>.wpb-page-header h1')).fontSize);
            out.select=box('.wpb-settings-jump select');
          }else if(which==='profile'){
            out.topPadding=parseFloat(getComputedStyle(el('.wpb-profile-inner')).paddingTop);
            out.head=box('.wpb-profile-head');
            out.titleSize=parseFloat(getComputedStyle(el('.wpb-profile-title')).fontSize);
            out.edit=box('.wpb-profile-edit');
            out.hero=box('.wpb-profile-hero');
            out.stats=[...document.querySelectorAll('.wpb-profile-stat')].map(b=>b.getBoundingClientRect().width);
          }
          return out;
        },tab);
        const density=await measure();
        assert.ok(!density.overflow,'R57 destination must not overflow: '+JSON.stringify({tab,density}));
        if(tab==='plan'){
          assert.ok(density.row&&density.back&&density.title&&density.back.height>=44&&density.back.width>=44,
            'Plan Back has an actual 44dp target: '+JSON.stringify(density));
          assert.ok(density.titleSize>=27&&density.titleSize<=28&&density.title.bottom-density.row.top<=118,
            'Plan header has compact hierarchy without hiding title: '+JSON.stringify(density));
        }else if(tab==='progress'){
          assert.ok(density.header&&density.header.height<=155&&density.titleSize>=26&&density.tabs.length===4&&density.tabs.every(x=>x.height>=44),
            'Progress title and four 44dp tabs fit its header: '+JSON.stringify(density));
        }else if(tab==='settings'){
          assert.ok(density.header&&density.jump&&density.picker&&density.picker.height>=48&&density.titleSize>=27&&density.select&&density.select.height>=44
            &&density.jump.bottom-density.header.top<=128,
            'Settings title and Jump control use a compact, accessible header: '+JSON.stringify(density));
        }else if(tab==='profile'){
          assert.ok(density.head&&density.edit&&density.hero&&density.titleSize>=27&&density.topPadding<=16
            &&density.edit.width>=44&&density.edit.height>=44,
            'Profile identity/edit start promptly with full-size edit action: '+JSON.stringify(density));
          assert.ok(density.hero.top-density.head.bottom<=16&&density.stats.length===3
            &&density.stats.every(w=>w>=68),'Profile cards follow a consistent density: '+JSON.stringify(density));
        }
        basic.density=density;
        if(tab==='plan'||tab==='profile'||tab==='settings'){
          await page.setViewport({width:320,height:700,deviceScaleFactor:1,isMobile:true,hasTouch:true});
          const small=await measure();
          assert.ok(!small.overflow,'320dp '+tab+' must not overflow: '+JSON.stringify(small));
          if(tab==='plan')assert.ok(small.back.width>=44&&small.title.bottom-small.row.top<=136,
            'Plan still fits at 320dp: '+JSON.stringify(small));
          if(tab==='profile')assert.ok(small.edit.height>=44&&small.stats.every(w=>w>=64),
            'Profile edit/stats remain usable at 320dp: '+JSON.stringify(small));
          if(tab==='settings')assert.ok(small.select.height>=44&&small.jump.bottom-small.header.top<=136,
            'Settings Jump remains usable at 320dp: '+JSON.stringify(small));
          basic.smallDensity=small;
          await page.setViewport({width:393,height:852,deviceScaleFactor:1,isMobile:true,hasTouch:true});
        }
      }
      captures[theme][tab]=Buffer.from(await page.screenshot({type:'png'}));
      surfaces[tab]=basic;
      if(tab==='home'){
        // R56: before the patch, scrolling hid the greeting but retained a
        // 70dp brand slab. Check real masthead height, touch areas, scroll-back
        // restoration and narrow-phone geometry in both saved palettes.
        const measure=()=>page.evaluate(()=>{
          const home=document.querySelector('.wpb-home');
          const head=home.querySelector('.wpb-home-header');
          const brand=home.querySelector('.wpb-home-brand');
          const tools=home.querySelector('.wpb-home-tools');
          const sc=home.querySelector('.wpb-page-scroll');
          const rect=x=>{const r=x.getBoundingClientRect();return {x:r.x,y:r.y,w:r.width,h:r.height,right:r.right};};
          const buttons=[...tools.querySelectorAll('button')];
          return {compact:home.dataset.homeCompact,
            header:rect(head),brand:rect(brand),tools:rect(tools),
            buttons:buttons.map(b=>({label:b.getAttribute('aria-label'),...rect(b)})),
            long:getComputedStyle(home.querySelector('.wpb-home-brand-long')).display,
            short:getComputedStyle(home.querySelector('.wpb-home-brand-short')).display,
            maxScroll:sc.scrollHeight-sc.clientHeight,
            overflow:home.scrollWidth>home.clientWidth+1,
            scrollTop:sc.scrollTop};
        });
        const expanded=await measure();
        assert.ok(expanded.maxScroll>180,'Seeded Home can actually scroll: '+JSON.stringify(expanded));
        assert.notEqual(expanded.long,'none','Full brand shown before scrolling');
        await page.$eval('.wpb-home .wpb-page-scroll',sc=>{sc.scrollTop=sc.scrollHeight;});
        await page.waitForFunction(()=>document.querySelector('.wpb-home')?.dataset.homeCompact==='1');
        const compact=await measure();
        assert.ok(compact.header.h<=64&&expanded.header.h-compact.header.h>=24,
          'Scrolled Home returns real vertical space, not only hidden text: '+JSON.stringify({expanded,compact}));
        assert.equal(compact.long,'none','Long brand no longer occupies scrolled header');
        assert.notEqual(compact.short,'none','Short brand preserves location context');
        assert.ok(compact.buttons.length===3&&compact.buttons.every(b=>b.w>=44&&b.h>=44),
          'All Home tools remain accessible 44dp touch targets: '+JSON.stringify(compact));
        assert.ok(compact.brand.right+8<=compact.tools.x&&!compact.overflow,
          'Collapsed Home brand/tools never overlap: '+JSON.stringify(compact));
        captures[theme].homeCompact=Buffer.from(await page.screenshot({type:'png'}));
        await page.setViewport({width:320,height:700,deviceScaleFactor:1,isMobile:true,hasTouch:true});
        await page.$eval('.wpb-home .wpb-page-scroll',sc=>{sc.scrollTop=sc.scrollHeight;});
        await page.waitForFunction(()=>document.querySelector('.wpb-home')?.dataset.homeCompact==='1');
        const small=await measure();
        assert.ok(small.header.h<=64&&small.brand.right+4<=small.tools.x&&!small.overflow,
          '320dp Home compact header has no collision or overflow: '+JSON.stringify(small));
        assert.ok(small.buttons.every(b=>b.w>=44&&b.h>=44),
          'Small-screen actions retain 44dp targets: '+JSON.stringify(small));
        await page.setViewport({width:393,height:852,deviceScaleFactor:1,isMobile:true,hasTouch:true});
        await page.$eval('.wpb-home .wpb-page-scroll',sc=>{sc.scrollTop=0;});
        await page.waitForFunction(()=>document.querySelector('.wpb-home')?.dataset.homeCompact==='0');
        const restored=await measure();
        assert.notEqual(restored.long,'none','Full masthead returns on scroll-to-top');
        await page.click('.wpb-home-tools button[aria-label="Exercise library"]');
        await page.waitForSelector('.wpb-library');
        await page.click('.wpb-library-header button[aria-label="Back"]');
        await page.waitForSelector('.wpb-home');
        surfaces[tab].header={expanded:expanded.header.h,compact:compact.header.h,
          compactButtons:compact.buttons.length,smallHeader:small.header.h};
      }
    }
    assert.equal(surfaces.plan.weekCard,await page.evaluate(()=>{
      const card=document.querySelector('.wpb-settings .wpb-settings-card');
      return card&&getComputedStyle(card).backgroundColor;
    }), 'Settings and Plan use the SAME neutral card role: '+JSON.stringify(surfaces.plan));
    await page.click('[data-tab="home"]');
    await page.waitForSelector('.hp-open');
    await page.click('.hp-open');
    await page.waitForSelector('.wpb-program .wpb-week-rail');
    const detail=await page.evaluate(()=>{
      const root=document.querySelector('.wpb-program');
      const week=[...root.querySelectorAll('.wpb-week-rail>button')];
      const phase=root.querySelector('.wpb-phase-block>div>div');
      return {
        overflow:root.scrollWidth>root.clientWidth+1,
        activeWeeks:week.filter(b=>b.getAttribute('aria-pressed')==='true').length,
        weekCount:week.length,
        phase:phase&&getComputedStyle(phase).backgroundColor,
        phaseImage:phase&&getComputedStyle(phase).backgroundImage
      };
    });
    assert.ok(!detail.overflow&&detail.weekCount>1&&detail.activeWeeks===1,
      'Full Program has one semantic selected week and no overflow: '+JSON.stringify(detail));
    // R54: inspect the expanded exercise view. R53 previously photographed
    // only the collapsed Program shell, hiding its legacy dark-purple rows.
    const dayPresent=await page.evaluate(()=>{
      const day=document.querySelector('.wpb-program .wpb-day-card');
      if(!day)return false;
      if(!day.querySelector('[data-exrow]'))day.querySelector('.wpb-day-toggle')?.click();
      return true;
    });
    assert.ok(dayPresent,'Full Program contains a real workout day');
    await page.waitForSelector('.wpb-program .wpb-program-exercise');
    const nested=await page.evaluate(()=>{
      const el=sel=>document.querySelector(sel);
      const css=sel=>getComputedStyle(el(sel));
      // Computed CSS color-mix() serializes to color(srgb 0..1), whereas
      // rgb() uses 0..255. Treating srgb channels as 8-bit values previously
      // fabricated contrast failures (and could fabricate false passes).
      const rgb=value=>{
        const v=String(value).trim();
        const srgb=v.match(/^color\(srgb\s+([\d.]+)\s+([\d.]+)\s+([\d.]+)/i);
        if(srgb)return srgb.slice(1,4).map(n=>Number(n)*255);
        const legacy=v.match(/^rgba?\(\s*([\d.]+)[,\s]+([\d.]+)[,\s]+([\d.]+)/i);
        if(legacy)return legacy.slice(1,4).map(Number);
        throw new Error('Unsupported computed contrast color: '+v);
      };
      const luminance=v=>v.map(n=>{n/=255;return n<=.04045?n/12.92:Math.pow((n+.055)/1.055,2.4)})
        .reduce((sum,n,i)=>sum+n*[.2126,.7152,.0722][i],0);
      const contrast=(a,b)=>{const x=luminance(rgb(a)),y=luminance(rgb(b));return (Math.max(x,y)+.05)/(Math.min(x,y)+.05)};
      const chips=[...document.querySelectorAll('.wpb-day-hit-chip')];
      const row=el('.wpb-program-exercise');
      return {
        card:css('.wpb-day-card').backgroundColor,
        row:css('.wpb-program-exercise').backgroundColor,
        nameContrast:contrast(css('.wpb-program-exercise-name').color,getComputedStyle(row).backgroundColor),
        metaContrast:contrast(css('.wpb-program-exercise-meta').color,getComputedStyle(row).backgroundColor),
        nameWrap:css('.wpb-program-exercise-name').whiteSpace,
        chips:chips.length,
        chipContrast:chips.length?contrast(getComputedStyle(chips[0]).color,getComputedStyle(chips[0]).backgroundColor):0,
        overflow:el('.wpb-program').scrollWidth>el('.wpb-program').clientWidth+1
      };
    });
    assert.notEqual(nested.card,nested.row,
      'Program exercise card must use a distinct tonal elevation: '+JSON.stringify(nested));
    assert.ok(nested.chips>0&&nested.nameWrap==='normal',
      'Muscle chips remain visible and exercise names wrap: '+JSON.stringify(nested));
    assert.ok(nested.nameContrast>=4.5&&nested.metaContrast>=4.5&&nested.chipContrast>=4.5,
      'Program name, metadata and chip labels meet WCAG AA contrast: '+JSON.stringify(nested));
    assert.ok(!nested.overflow,'No horizontal clipping with expanded exercises: '+JSON.stringify(nested));
    detail.nested=nested;
    captures[theme].program=Buffer.from(await page.screenshot({type:'png'}));
    assert.equal(errors.length,0,'No runtime errors while checking paired themes: '+errors.join('; '));
    measurements[theme]={...surfaces,program:detail};
    await context.close();
  }
  // Build genuine 1:1 side-by-side PNGs for visual signoff (left dark / right light),
  // not two disjoint galleries where differences are difficult to compare.
  const canvasPage=await browser.newPage();
  const pairs=['home','homeCompact','plan','progress','profile','settings','program'];
  for(const tab of pairs){
    const result=await canvasPage.evaluate(async({dark,light})=>{
      const load=src=>new Promise((resolve,reject)=>{const img=new Image();img.onload=()=>resolve(img);img.onerror=reject;img.src=src;});
      const [a,b]=await Promise.all([load(dark),load(light)]);
      const c=document.createElement('canvas');c.width=a.width+b.width;c.height=Math.max(a.height,b.height);
      const ctx=c.getContext('2d');ctx.drawImage(a,0,0);ctx.drawImage(b,a.width,0);
      return c.toDataURL('image/png').split(',')[1];
    },{
      dark:'data:image/png;base64,'+captures.rose[tab].toString('base64'),
      light:'data:image/png;base64,'+captures.rosewater[tab].toString('base64')
    });
    fs.writeFileSync(path.join(root,'verification/r53-'+tab+'-phone.png'),Buffer.from(result,'base64'));
  }
  await canvasPage.close();
  console.log('PASS R57: 7 paired Rose dark/Rosewater light screens, shared Plan/Progress/Profile/Settings density, 320dp overflow and 44dp controls, R56 compact Home and prior Plan/Program color checks; '+JSON.stringify(measurements));
}finally{
  if(browser)await browser.close();
  await new Promise(resolve=>server.close(resolve));
}
