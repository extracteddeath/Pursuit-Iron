import { uiStyleBudget } from './ui-style-budgets.mjs';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { report } from '../scripts/audit-ui-style-ownership.mjs';
const app=fs.readFileSync('modules/App.js','utf8');
const css=fs.readFileSync('app.css','utf8');
const settings=app.slice(app.indexOf('function SettingsView('),app.indexOf('function CompareView('));
const service=settings.slice(settings.indexOf('"data-settings-anchor": "feedback"'),settings.indexOf('"data-settings-anchor": "diagnostics"'));
assert.ok(service.length>=4800,'Settings service and identity sections must remain present');
assert.equal((settings.match(/style:\s*\{/g)||[]).length,6,'Settings has six remaining inline values after reset control migration');
assert.ok(report.appInlineStyleObjects<=uiStyleBudget.appInlineObjects,'App-wide inline presentation debt must not grow');
for(const token of ['wpb-settings-feedback','wpb-settings-note','wpb-settings-cta','wpb-settings-support','wpb-settings-support-cta','wpb-settings-about-brand','wpb-settings-version','wpb-settings-info-link','wpb-settings-reference-name'])
  assert.ok(service.includes(token)&&css.includes('.wpb .'+token),'Missing Settings component owner: '+token);
assert.ok(service.includes('as: "a", href: SUPPORT_URL')&&service.includes('as: "a", href: "./privacy.html"'),'Support and privacy stay native anchors');
assert.ok(service.includes('variant: "tonal", onClick: onShowWhatsNew'),'Changelog action keeps its callback');
assert.ok(service.includes('mailto:'+'${BUG_EMAIL}'),'Bug report must preserve mailto subject/body');
assert.ok(!service.includes('background: C.card') && !service.includes('borderRadius: 12'),'Old support-card inline painting must stay retired');
assert.equal((css.match(/!important/g)||[]).length,2024,'No priority rules were added');
console.log('PASS Settings presentation: 20 static inline styles migrated to shared owners.');
