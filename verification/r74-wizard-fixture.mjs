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
      '.wpb-wizard-cycle-heading,.wpb-wizard-focus-row,.wpb-wizard-focus-summary,.wpb-wizard-review-row,.wpb-wizard-title,.wpb-wizard-guidance')]
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
    const footer=el.querySelector('.wpb-wizard-footer');
    return {overflow:document.documentElement.scrollWidth>innerWidth+2,controls,
      viewportHeight:innerHeight,footer:{height:footer.getBoundingClientRect().height,client:footer.clientHeight,scroll:footer.scrollHeight},
      reading:reading.filter(el=>el.bad.length),
      splitWords,
      named:document.getElementById(step.getAttribute('aria-labelledby'))?.textContent.trim()||'',
      scrollHeight:el.querySelector('.wpb-page-scroll').clientHeight};
  });
  const assertGeometry=async label=>{
    const result=await measure();
    assert.equal(result.overflow,false,label+' has no document overflow');
    assert.ok(result.named,label+' names the setup group by its question');
    assert.ok(result.scrollHeight>=44,label+' retains a usable scrolling form area: '+JSON.stringify(result.footer));
    assert.ok(result.footer.height<=result.viewportHeight*.45+2,label+' bounds footer height without reducing text');
    if(result.footer.scroll>result.footer.client+1){
      const finish=await page.$('.wpb-wizard-finish-action');
      if(finish){
        await finish.evaluate(el=>el.focus());await settle();
        const visible=await finish.evaluate(el=>{const a=el.getBoundingClientRect(),b=el.closest('.wpb-wizard-footer').getBoundingClientRect();return a.y>=b.y-1&&a.bottom<=b.bottom+1;});
        assert.ok(visible,label+' keyboard focus reveals the complete early-finish action in the scrolling footer');
        await finish.dispose();
      }
      await page.$eval('.wpb-wizard-next-action',el=>el.focus());await settle();
      assert.ok(await page.$eval('.wpb-wizard-next-action',el=>{const a=el.getBoundingClientRect(),b=el.closest('.wpb-wizard-footer').getBoundingClientRect();return a.y>=b.y-1&&a.bottom<=b.bottom+1;}),label+' keyboard focus reveals the primary action');
    }
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
      if(key==='name'){
        const compact=width<1120||height<720;
        const toggle='.wpb-wizard-review-toggle';
        const before=await signature();
        if(compact){
          assert.equal(await page.$eval(toggle,el=>el.getAttribute('aria-expanded')),'false');
          await page.$eval('.wpb-wizard-step input',el=>el.focus());
          await page.keyboard.press('Tab');await settle();
          assert.equal(await page.$eval(toggle,el=>el===document.activeElement),true,
            label+' keyboard order reaches the setup disclosure after the name');
          await page.keyboard.press('Space');await settle();
          assert.equal(await page.$eval(toggle,el=>el.getAttribute('aria-expanded')),'true');
        }else{
          await page.$eval('.wpb-wizard-step input',el=>el.focus());
          await page.keyboard.press('Tab');await settle();
          assert.equal(await page.$eval(toggle,el=>el===document.activeElement),false,
            label+' desktop keyboard order skips the hidden disclosure control');
        }
        assert.ok(await page.$eval('.wpb-wizard-review-content',el=>
          el.getClientRects().length>0&&el.textContent.includes(document.querySelector('.wpb-wizard-step input').value)),
          label+' review reads the current name without changing the form');
        await textSize(true);await assertGeometry(label+' expanded setup 200% text');
        if(width===320&&theme==='rose')await page.screenshot({path:path.join(root,'verification/r74-'+(cycle?'cycle':'program')+'-setup-review-phone.png')});
        await textSize(false);
        assert.deepEqual(await signature(),before,'Review and text scaling preserve the current answers');
        if(compact)await activate(toggle);
        if(cycle){
          assert.equal(await page.$eval('.wpb-wizard-discard-action',el=>!!el.closest('.wpb-page-scroll')),true,
            'Discard remains available in scrolling setup content');
          assert.equal(await page.$eval('.wpb-cycle-draft-status',el=>
            !!(document.querySelector('.wpb-wizard-actions').compareDocumentPosition(el)&Node.DOCUMENT_POSITION_FOLLOWING)),true,
            'Back and Next precede the saved-status message');
        }
      }
      if(key==='cycletype'){
        const cards=await page.$$('.wpb-wizard-cycle-option');
        assert.ok(cards.length>1,'Cycle creation offers template choices');
        for(const card of cards){
          await card.evaluate(el=>{el.scrollIntoView({block:'center'});el.focus();});
          await page.keyboard.press('Enter');await settle();
          assert.equal(await card.evaluate(el=>el.getAttribute('aria-pressed')),'true',
            'Keyboard selects each template through the canonical Wizard state');
          assert.equal(await page.$$eval('.wpb-wizard-cycle-option[aria-pressed="true"]',els=>els.length),1,
            'Exactly one template is selected');
        }
        // Custom blocks retain authored values through template changes.
        const custom=await page.evaluateHandle(()=>[...document.querySelectorAll('.wpb-wizard-cycle-option')]
          .find(el=>el.textContent.includes('Custom')));
        assert.ok(custom.asElement(),'Custom cycle choice exists');
        await custom.asElement().evaluate(el=>{el.scrollIntoView({block:'center'});el.focus();});
        await page.keyboard.press('Enter');await settle();
        const weeks='.wpb-custom-cycle input[aria-label="Block 1 weeks"]';
        await page.waitForSelector(weeks);
        await page.$eval(weeks,el=>{el.focus();el.select();});
        await page.keyboard.type('7');await settle();
        await activate('.wpb-wizard-cycle-option');
        await custom.asElement().evaluate(el=>{el.scrollIntoView({block:'center'});el.focus();});
        await page.keyboard.press('Enter');await settle();
        assert.equal(await page.$eval(weeks,el=>el.value),'7','Template changes preserve custom block weeks');
        const adapt='.wpb-wizard-cycle-adapt';
        const before=await page.$eval(adapt,el=>el.getAttribute('aria-checked'));
        await activate(adapt);
        assert.notEqual(await page.$eval(adapt,el=>el.getAttribute('aria-checked')),before,
          'Keyboard adaptation changes the canonical checkbox value');
        await activate(adapt);
        assert.equal(await page.$eval(adapt,el=>el.getAttribute('aria-checked')),before,
          'Adaptation can be restored without changing custom blocks');
        await assertGeometry(label+' custom blocks 100% text');
        await textSize(true);
        await assertGeometry(label+' custom blocks 200% text');
        await textSize(false);
        // Continue the existing preset-cycle journey: custom inserts its own
        // Goal step, while the original sequence deliberately tests presets.
        await activate('.wpb-wizard-cycle-option');
        await custom.dispose();
      }

      if(key==='equipment'){
        const presets=await page.$$('.wpb-wizard-preset');
        for(const preset of presets){
          await preset.evaluate(el=>{el.scrollIntoView({block:'center',inline:'center'});el.focus();});
          await page.keyboard.press('Enter');await settle();
          assert.equal(await preset.evaluate(el=>el.getAttribute('aria-pressed')),'true',
            'Keyboard equipment preset updates its canonical selected state');
          assert.equal(await page.$$eval('.wpb-wizard-preset[aria-pressed="true"]',els=>els.length),1,
            'Only the exact equipment preset is selected');
        }
        // Restore a full gym so the shared rack restriction is available.
        await activate('.wpb-wizard-preset');
        const rack='[data-testid="no-rack-toggle"]';
        await activate(rack);
        assert.equal(await page.$eval(rack,el=>el.getAttribute('aria-pressed')),'true','No-rack exclusion is selectable');
        await activate(rack);
        assert.equal(await page.$eval(rack,el=>el.getAttribute('aria-pressed')),'false','No-rack exclusion is reversible');
        assert.equal(await page.$eval('.wpb-wizard-preset',el=>el.getAttribute('aria-pressed')),'true',
          'Removing the no-rack exclusion restores the full-gym equipment set');
        for(const preset of presets)await preset.dispose();
      }
      if(key==='constraints'){
        for(const group of ['.wpb-wizard-superset-choices','.wpb-wizard-barbell-choices']){
          const choices=await page.$$(group+' button');
          for(const choice of choices){
            await choice.evaluate(el=>{el.scrollIntoView({block:'center'});el.focus();});
            await page.keyboard.press('Enter');await settle();
            assert.equal(await choice.evaluate(el=>el.getAttribute('aria-pressed')),'true',
              'Keyboard constraint selection updates the existing configuration');
            assert.equal(await page.$$eval(group+' [aria-pressed="true"]',els=>els.length),1,
              'Constraint group has exactly one selected value');
          }
          await activate(group+' button');
          for(const choice of choices)await choice.dispose();
        }
      }
      if(key==='focus'){
        await page.$$eval('.wpb-setup-stepper button[aria-label^="Increase"]',els=>
          els.forEach((el,i)=>el.dataset.r74FocusPlus=String(i)));
        await page.$$eval('.wpb-setup-stepper button[aria-label^="Decrease"]',els=>
          els.forEach((el,i)=>el.dataset.r74FocusMinus=String(i)));
        for(const i of [0,0,1,1,2])await activate('[data-r74-focus-plus="'+i+'"]');
        assert.equal(await page.$eval('.wpb-wizard-focus-total',el=>el.textContent),'5 / 5',
          'Shared steppers retain the five-point focus limit');
        assert.ok(await page.$$eval('[data-r74-focus-plus]',els=>els.every(el=>el.disabled)),
          'Every increase is disabled at the global focus limit');
        assert.equal(await page.$eval('.wpb-wizard-focus-summary',el=>el.dataset.atLimit),'true',
          'Focus emphasis follows the actual total');
        await activate('[data-r74-focus-minus="2"]');
        assert.equal(await page.$eval('[data-r74-focus-plus="0"]',el=>el.disabled),true,
          'A muscle at two points stays capped when global capacity returns');
        assert.equal(await page.$eval('[data-r74-focus-plus="2"]',el=>el.disabled),false,
          'Reducing focus restores available capacity');
        for(const i of [0,1,2])while(!await page.$eval('[data-r74-focus-minus="'+i+'"]',el=>el.disabled))
          await activate('[data-r74-focus-minus="'+i+'"]');
        assert.equal(await page.$eval('.wpb-wizard-focus-total',el=>el.textContent),'0 / 5',
          'Focus steppers can restore the original settings');
        if(width===320&&theme==='rose'){
          await page.$eval('.wpb-wizard-focus-summary',el=>el.scrollIntoView({block:'start'}));
          await page.screenshot({path:path.join(root,'verification/r74-'+(cycle?'cycle':'program')+'-focus-normal-phone.png')});
        }
      }
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
