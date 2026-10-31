/**
 * Browser failure evidence for route/visibility regressions.
 * Never changes app storage or navigation; emits diagnostics only after a failed wait.
 */
export async function waitForRouteOrDiagnose(page, selector, { label = 'route', errors = [], options = {} } = {}) {
  try {
    return await page.waitForSelector(selector, options);
  } catch (cause) {
    let observed;
    try {
      observed = await page.evaluate(() => {
        let storage;
        try {
          const d = JSON.parse(localStorage.getItem('wpb:v1') || 'null');
          const live = JSON.parse(localStorage.getItem('wpb:live') || 'null');
          storage = {
            saved: Array.isArray(d?.saved) ? d.saved.map(p => ({id:p.id, custom:p.custom,
              engineSource:p.engineSource, days:p.days?.length})) : null,
            pinnedId: d?.pinnedId, cycles: d?.cycles?.length, liveProgramId: live?.programId,
            liveDayId: live?.dayId, liveWeekIndex: live?.weekIndex
          };
        } catch (error) { storage = { parseError: error.message }; }
        const tabs = [...document.querySelectorAll('button[data-tab]')].map(n => ({
          id: n.dataset.tab, current: n.getAttribute('aria-current'),
          disabled: n.disabled, label: n.getAttribute('aria-label')
        }));
        return {
          url: location.pathname, title: document.title, tabs,
          viewFrames: [...document.querySelectorAll('[data-view-frame]')].map(n => n.dataset.viewFrame),
          planExists: !!document.querySelector('.wpb-plan-view'),
          planEmpty: !!document.querySelector('.wpb-plan-empty'),
          workoutExists: !!document.querySelector('.wpb-workout'),
          dock: [...document.querySelectorAll('.wpb-live-dock button')].map(n => ({
            label: n.getAttribute('aria-label'), text: n.textContent.trim().slice(0,70)
          })),
          overlayCount: document.querySelectorAll('.wpb-backdrop').length,
          loading: document.querySelector('.wpb-loading,.wpb-startup-error')?.textContent?.trim().slice(0,180),
          storage,
          visibleText: (document.body?.innerText || '').trim().slice(0,500)
        };
      });
    } catch (error) { observed = { diagnosticError: error.message }; }
    const message = JSON.stringify({label,selector,observed,pageErrors:errors.slice(-12)});
    console.error('ROUTE_FAILURE_DIAGNOSTIC ' + message);
    throw new Error('Browser route did not appear: ' + message, {cause});
  }
}
