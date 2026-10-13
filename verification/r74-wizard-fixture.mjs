import assert from 'node:assert/strict';
import path from 'node:path';

// Mount the exported production Wizard to include the first-use Units,
// Experience and Equipment steps which an established profile skips. The
// ordinary app loads the saved palette first; the same motion and CSS owners
// then operate on the Wizard. This fixture never generates or saves a plan.
export const wizardAccessibilityHtml = importMap => `<!doctype html><html><head>
<meta name="viewport" content="width=device-width,initial-scale=1">
<link rel="stylesheet" href="/app.css">${importMap}
<style>html,body,#root{height:100%;margin:0}</style></head><body><div id="root"></div>
<script type="module">
import React from '/vendor/react.js';import {createRoot} from '/vendor/react-dom-client.js';
import RootApp,{Wizard,StyleTag} from '/modules/App.js';
import {installAppMotion} from '/modules/ui-motion.js';
const owner=createRoot(document.getElementById('root'));
owner.render(React.createElement(RootApp));installAppMotion(document.getElementById('root'));
window.mountAccessibilityWizard=cycle=>owner.render(React.createElement('div',{
 className:'wpb',style:{height:'100%',display:'flex',flexDirection:'column'}},
 React.createElement(StyleTag),React.createElement('div',{style:{width:'100%',maxWidth:460,
 height:'100%',margin:'0 auto',display:'flex',flexDirection:'column'}},
 React.createElement('div',{'data-view-frame':cycle?'cycleWizard':'wizard',
 style:{height:'100%',display:'flex',flexDirection:'column'}},React.createElement(Wizard,{
 initialUnit:'lb',cycleMode:cycle,onCancel:()=>{},onDone:cfg=>{window.wizardResult=cfg;}})))));
</script></body></html>`;

export async function verifyWizardSteps(page, { width, height, theme, root, url }) {
  // The production worker deliberately serves its app shell for navigation.
  // This isolated component fixture has its own HTML rather than that shell.
  await page.setBypassServiceWorker(true);
  const settle=()=>page.evaluate(()=>new Promise(resolve=>
    requestAnimationFrame(()=>requestAnimationFrame(resolve))));
  const resize=async (w,h,scale=1)=>{
    await page.setViewport({width:w,height:h,deviceScaleFactor:scale,
      isMobile:width<600,hasTouch:width<600});
    await settle();
  };
  const textSize=async enlarged=>{
    await page.evaluate(enlarged=>{
      if(enlarged){
        window.__r74WizardText=[...document.querySelectorAll('.wpb *')]
          .filter(el=>el instanceof HTMLElement&&el.tagName!=='STYLE')
          .map(el=>({el,size:parseFloat(getComputedStyle(el).fontSize),
            value:el.style.getPropertyValue('font-size'),priority:el.style.getPropertyPriority('font-size')}));
        window.__r74WizardText.forEach(({el,size})=>el.style.setProperty('font-size',size*2+'px','important'));
      }else{
        window.__r74WizardText.forEach(({el,value,priority})=>value?
          el.style.setProperty('font-size',value,priority):el.style.removeProperty('font-size'));
        delete window.__r74WizardText;
      }
    },enlarged);
    await settle();
  };
  const signature=()=>page.$eval('.wpb-wizard-step',el=>({
    key:el.dataset.stepKey,input:el.querySelector('input')?.value||null,
    pressed:[...el.querySelectorAll('[aria-pressed="true"],[aria-checked="true"]')]
      .map(el=>el.getAttribute('aria-label')||el.textContent.trim()),
    points:[...el.querySelectorAll('.wpb-setup-stepper .mono')].map(el=>el.textContent)
  }));
  const measure=()=>page.$eval('.wpb-wizard',el=>{
    const bounds=el=>{const r=el.getBoundingClientRect();return {
      x:r.x,y:r.y,right:r.right,bottom:r.bottom,width:r.width,height:r.height};};
    const textFits=(el,vertical=false)=>{
      const box=bounds(el),walker=document.createTreeWalker(el,NodeFilter.SHOW_TEXT),bad=[];
      while(walker.nextNode()){
        const node=walker.currentNode;
        if(!node.textContent.trim()||node.parentElement.closest('svg'))continue;
        const range=document.createRange();range.selectNodeContents(node);
        if([...range.getClientRects()].some(r=>r.width&&r.height&&
          (r.x<box.x-1||r.right>box.right+1||vertical&&(r.y<box.y-1||r.bottom>box.bottom+1))))
          bad.push(node.textContent.trim());
      }
      return bad;
    };
    const controls=[...el.querySelectorAll('button,input,select,textarea')]
      .filter(el=>el.getClientRects().length&&getComputedStyle(el).visibility!=='hidden')
      .map(el=>({label:el.getAttribute('aria-label')||el.textContent.trim(),
        ...bounds(el),bad:textFits(el,true)}));
    const reading=[...el.querySelectorAll('.wpb-wizard-heading,.wpb-wizard-option-copy,'+
      '.wpb-wizard-cycle-heading,.wpb-wizard-focus-row,.wpb-wizard-focus-summary,.wpb-wizard-review-row')]
      .filter(el=>el.getClientRects().length).map(el=>({text:el.textContent,bad:textFits(el)}));
    const step=el.querySelector('fieldset');
    const splitWords=[...el.querySelectorAll('.wpb-wizard-option-label')].flatMap(el=>{
      const node=el.firstChild;
      // A hyphen is a normal text-wrap boundary (e.g. Chest-Supported).
      return [...node.textContent.matchAll(/[^\s-]+/g)].filter(match=>{
        const range=document.createRange();range.setStart(node,match.index);
        range.setEnd(node,match.index+match[0].length);
        return range.getClientRects().length!==1;
      }).map(match=>match[0]);
    });
    return {overflow:document.documentElement.scrollWidth>innerWidth+2,controls,
      reading:reading.filter(el=>el.bad.length),
      splitWords,
      named:document.getElementById(step.getAttribute('aria-labelledby'))?.textContent.trim()||'',
      scrollHeight:el.querySelector('.wpb-page-scroll').clientHeight};
  });
  const assertGeometry=async label=>{
    const result=await measure();
    assert.equal(result.overflow,false,label+' has no document overflow');
    assert.ok(result.named,label+' names the setup group by its question');
    assert.ok(result.scrollHeight>=44,label+' retains a usable scrolling form area');
    assert.ok(result.controls.every(c=>c.label&&c.width>=44&&c.height>=44&&!c.bad.length),
      label+' exposes named 44px controls with whole text: '+JSON.stringify(
        result.controls.filter(c=>!c.label||c.width<44||c.height<44||c.bad.length)));
    assert.deepEqual(result.reading,[],label+' contains readable titles, choices, focus and review text');
    assert.deepEqual(result.splitWords,[],label+' wraps option titles between whole words');
  };
  const activate=async selector=>{
    await page.$eval(selector,el=>{el.scrollIntoView({block:'center',inline:'center'});el.focus({preventScroll:true});});
    await page.keyboard.press('Enter');
    await settle();
  };
  const stepKeys=[];
  for(const cycle of [false,true]){
    await resize(width,height);
    await page.goto(url,{waitUntil:'networkidle0'});
    await page.waitForSelector('.wpb-home');
    await page.evaluate(cycle=>window.mountAccessibilityWizard(cycle),cycle);
    await page.waitForSelector('.wpb-wizard');
    const expected=cycle?['cycletype','units','name','experience','approach','days','split',
      'session','equipment','constraints','focus','reduce','deload']:
      ['units','name','experience','goal','approach','days','split','session','equipment',
        'constraints','focus','reduce','progression','length','deload'];
    const visited=[];
    for(const key of expected){
      await page.waitForFunction(key=>document.querySelector('.wpb-wizard-step')?.dataset.stepKey===key,{},key);
      if(key==='name')await page.type('.wpb-wizard-step input','R74 '+(cycle?'cycle':'program')+' draft '+theme);
      if(key==='split'||key==='session')await page.waitForFunction(()=>
        !document.querySelector('.wpb-wizard-next-action').disabled&&
        !document.querySelector('.wpb-wizard-step').textContent.includes('Checking'),{timeout:90000});
      const label=theme+' '+width+'x'+height+' '+(cycle?'Cycle':'Program')+' '+key;
      await assertGeometry(label+' 100% text');
      await textSize(true);
      await assertGeometry(label+' 200% text');
      if(key==='focus'){
        await activate('.wpb-setup-stepper button[aria-label="Increase Chest focus"]');
        assert.equal(await page.$eval('.wpb-setup-stepper .mono',el=>el.textContent),'1',
          'Keyboard can change muscle priority at enlarged text');
      }
      if(key==='equipment'){
        const button=await page.$$('.wpb-wizard-step>button');
        const toggle=button[button.length-1];
        await toggle.evaluate(el=>{el.dataset.r74Bodyweight='1';});
        await activate('[data-r74-bodyweight="1"]');
        assert.equal(await page.$eval('[data-r74-bodyweight="1"]',el=>el.getAttribute('aria-pressed')),'true',
          'Bodyweight preference announces its selected state');
      }
      const selected=await signature();
      if(width===320&&theme==='rose'&&['split','cycletype','focus'].includes(key)){
        await page.$eval('.wpb-wizard-step',el=>{
          const target=el.querySelector('.wpb-wizard-option,.wpb-wizard-cycle-option,.wpb-wizard-focus-row');
          target.scrollIntoView({block:'start'});
        });
        await page.screenshot({path:path.join(root,'verification/r74-'+(cycle?'cycle':'program')+'-'+key+'-phone.png')});
      }
      await textSize(false);
      if(width>=900&&height>=700){
        await resize(Math.round(width/2),Math.round(height/2),2);
        await assertGeometry(label+' 200% zoom-equivalent layout');
        assert.deepEqual(await signature(),selected,'Zoom-equivalent resize retains this step\'s answers');
        await resize(width,height);
      }
      // Actual step changes and Back use the Wizard's handlers, including
      // the state-dependent step list. A repeated visit must retain answers.
      if(visited.length){
        await activate('.wpb-wizard-back-action');
        await page.waitForFunction(key=>document.querySelector('.wpb-wizard-step')?.dataset.stepKey===key,{},visited.at(-1));
        await page.waitForFunction(()=>!document.querySelector('.wpb-wizard-next-action').disabled,{timeout:90000});
        await activate('.wpb-wizard-next-action');
        await page.waitForFunction(key=>document.querySelector('.wpb-wizard-step')?.dataset.stepKey===key,{},key);
        assert.deepEqual(await signature(),selected,'Back/Next retains '+key+' answers');
      }
      visited.push(key);
      if(key!=='deload')await activate('.wpb-wizard-next-action');
    }
    assert.deepEqual(visited,expected,'Every first-use creation step was inspected');
    assert.equal(await page.$eval('.wpb-wizard-next-action',el=>el.textContent.trim()),
      cycle?'Build Cycle':'Generate Program','Final creation action remains accessible');
    assert.equal(await page.evaluate(()=>window.wizardResult),undefined,
      'Inspecting, editing and resizing a setup never submits it');
    stepKeys.push({cycle,visited});
  }
  console.log('PASS R74 all Wizard steps '+theme+' '+width+'x'+height);
  return stepKeys;
}
