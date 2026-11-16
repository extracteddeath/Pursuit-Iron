// Progress semantic paint must remain identical after the shared-owner cleanup.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import puppeteer from 'puppeteer-core';

const root=path.resolve(new URL('../',import.meta.url).pathname);
const html=`<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="/app.css"></head>
<body><div id="root"><main class="wpb"><section class="wpb-progress" style="box-sizing:border-box;width:100%;padding:12px">
<span class="wpb-progress-body-change" data-direction="up">+12.5</span>
<span class="wpb-progress-body-change" data-direction="down">−12.5</span>
<span class="wpb-progress-trend-value" data-direction="up">+8.2</span>
<span class="wpb-progress-trend-value" data-direction="down">−8.2</span>
<span class="wpb-progress-volume-change-value" data-direction="up">+2</span>
<span class="wpb-progress-volume-change-value" data-direction="down">−2</span>
<span class="wpb-progress-lift-change" data-direction="up">+5</span>
<span class="wpb-progress-lift-change" data-direction="down">−5</span>
<span class="wpb-progress-record-level" data-high="true">High</span>
<button class="wpb-progress-measure-toggle" aria-expanded="true"><svg class="wpb-progress-measure-chevron"></svg></button>
<button class="wpb-history-session-toggle" aria-expanded="true"><svg class="wpb-history-session-chevron"></svg></button>
</section></main></div></body></html>`;
const server=http.createServer((req,res)=>{
  if(req.url==='/fixture'){res.setHeader('Content-Type','text/html');res.end(html);return}
  if(req.url==='/app.css'){res.setHeader('Content-Type','text/css');res.end(fs.readFileSync(path.join(root,'app.css')));return}
  res.writeHead(404).end();
});
await new Promise(done=>server.listen(0,'127.0.0.1',done));
let browser;
try {
  browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
  const page=await browser.newPage(),errors=[];
  page.on('pageerror',error=>errors.push(error.message));
  for(const width of [320,393]){
    await page.setViewport({width,height:740,deviceScaleFactor:2,isMobile:true,hasTouch:true});
    await page.goto('http://127.0.0.1:'+server.address().port+'/fixture',{waitUntil:'load'});
    await page.evaluate(()=>{
      document.documentElement.style.fontSize='32px';
      const host=document.querySelector('.wpb');
      for(const [key,value] of Object.entries({
        '--wpb-theme-accent-ink':'rgb(31, 132, 213)',
        '--wpb-theme-warning':'rgb(221, 103, 37)',
        '--wpb-theme-accent':'rgb(61, 112, 223)',
        '--wpb-theme-accent-dim':'rgb(29, 48, 87)',
        '--wpb-theme-warning-dim':'rgb(75, 42, 28)'
      })) host.style.setProperty(key,value);
    });
    const result=await page.evaluate(()=>{
      const style=sel=>getComputedStyle(document.querySelector(sel));
      const status=(type,dir)=>style('.wpb-progress-'+type+'[data-direction="'+dir+'"]');
      const metric=(type,dir)=>({color:status(type,dir).color,background:status(type,dir).backgroundColor,variant:status(type,dir).fontVariantNumeric});
      return {
        bodyUp:metric('body-change','up'),bodyDown:metric('body-change','down'),
        trendUp:metric('trend-value','up'),trendDown:metric('trend-value','down'),
        volumeUp:metric('volume-change-value','up'),volumeDown:metric('volume-change-value','down'),
        liftUp:metric('lift-change','up'),liftDown:metric('lift-change','down'),
        record:{color:style('.wpb-progress-record-level').color,background:style('.wpb-progress-record-level').backgroundColor},
        chevrons:[style('.wpb-progress-measure-chevron').transform,style('.wpb-history-session-chevron').transform],
        scrollWidth:document.documentElement.scrollWidth
      };
    });
    for(const type of ['body','trend']){
      assert.equal(result[type+'Up'].color,'rgb(31, 132, 213)',type+' rising accent');
      assert.equal(result[type+'Down'].color,'rgb(221, 103, 37)',type+' declining warning');
    }
    for(const type of ['volume','lift']){
      assert.equal(result[type+'Up'].color,'rgb(61, 112, 223)',type+' improving accent');
      assert.equal(result[type+'Up'].background,'rgb(29, 48, 87)',type+' improving tint');
      assert.equal(result[type+'Down'].color,'rgb(221, 103, 37)',type+' declining warning');
      assert.equal(result[type+'Down'].background,'rgb(75, 42, 28)',type+' declining tint');
    }
    assert.equal(result.record.color,'rgb(221, 103, 37)');
    assert.equal(result.record.background,'rgb(75, 42, 28)');
    for(const type of ['body','trend','volume','lift'])
      for(const direction of ['Up','Down'])
        assert.ok(result[type+direction].variant.includes('tabular-nums'),type+direction+' numeric alignment');
    assert.ok(result.chevrons.every(t=>t.startsWith('matrix(-1')||t.includes('rotate(180deg)')), 'Both expanded disclosure chevrons rotate');
    assert.ok(result.scrollWidth<=width+1,'No 200% text horizontal page overflow at '+width);
  }
  assert.deepEqual(errors,[],'No runtime errors in Progress presentation fixture');
  console.log('PASS Progress status browser: theme-backed paint, tabular values and disclosure parity at 320/393px 200% text');
} finally {
  await browser?.close();
  await new Promise(done=>server.close(done));
}
