/* Layout guard for asynchronous split/session feasibility checks. */
const STYLE_ID = 'wpb-wizard-stability-v1';

function installStyles() {
    if (document.getElementById(STYLE_ID))
        return;
    const style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = `
      .wpb-wizard-option{box-sizing:border-box;min-width:0;overflow:hidden;}
      .wpb-wizard-option[aria-disabled="true"]{position:relative;padding-right:50px!important;}
      .wpb-wizard-option[aria-disabled="true"]::after{
        content:"";position:absolute;right:12px;top:50%;transform:translateY(-50%);
        width:26px;height:26px;box-sizing:border-box;border-radius:999px;
        border:2px solid currentColor;opacity:.18;pointer-events:none;
      }
      .wpb-split-step .wpb-wizard-options{overflow-anchor:none;}
      .wpb-split-step .wpb-wizard-option{
        min-height:68px!important;
        transition:opacity .14s ease,border-color .14s ease,background-color .14s ease,box-shadow .14s ease!important;
      }
      .wpb-split-step .wpb-wizard-option-icon{align-self:flex-start;margin-top:1px;}
      .wpb-split-step .wpb-wizard-option[aria-disabled="true"]::after{top:16px;transform:none;}
      @media (prefers-reduced-motion: reduce){
        .wpb-split-step .wpb-wizard-option{transition:none!important;}
      }
    `;
    document.head.appendChild(style);
}

function markCurrentStep() {
    document.querySelectorAll('.wpb-wizard-step.wpb-split-step')
        .forEach(el => el.classList.remove('wpb-split-step'));
    const heading = [...document.querySelectorAll('.wpb-wizard-heading-title')]
        .find(el => el.textContent?.trim() === 'Pick your split');
    const step = heading?.closest('.wpb-wizard-step');
    if (step)
        step.classList.add('wpb-split-step');
}

installStyles();
markCurrentStep();

const observer = new MutationObserver(markCurrentStep);
observer.observe(document.documentElement, { childList: true, subtree: true });
