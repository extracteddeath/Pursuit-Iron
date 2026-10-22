import assert from 'node:assert/strict';
import fs from 'node:fs';
const app=fs.readFileSync('modules/App.js','utf8');
const css=fs.readFileSync('app.css','utf8');
const home=app.slice(app.indexOf('function Home('),app.indexOf('function Home(')+40000);
assert.ok(home.startsWith('function Home('),'Home component must remain available');
assert.ok(home.includes('useLayoutEffect(() =>') && home.includes('sc.scrollTop / full'),
  'Home greeting follows scroll from a layout effect without a first-frame flash');
assert.ok(home.includes('head.style.height = `${remaining}px`') && home.includes('head.scrollHeight'),
  'The measured greeting alone contracts continuously');
assert.ok(home.includes('progress >= .99') && !home.includes('progress >= .95'),
  'The old 95% layout swap cannot reappear');
assert.ok(!home.includes('className: "wpb-home-brand-short"'),
  'The short Iron-only brand is not rendered');
assert.ok(home.includes('children: "Pursuit Iron"') && home.includes('className: "wpb-home-brand-long"'),
  'The full Pursuit Iron identity remains in the measured context');
assert.ok(home.includes('_jsx(UiRootHeader, { title: "Home"'),
  'Home title and actions share the same root app-bar owner as other tabs');
assert.ok(css.includes('.wpb-root-title-row{display:flex;flex-wrap:wrap;') && css.includes('min-height:var(--pi-control-min)'),
  'The root action row follows shared geometry and allows enlarged-text reflow');
assert.ok(!css.includes('data-home-compact="1"]>.wpb-home-header'),
  'No separate compact-header geometry can create a threshold jump');
assert.ok(css.includes('.wpb-page-shell[data-scrolled="1"]>.wpb-root-header'),
  'Existing scroll state changes the shared app-bar fill');
assert.ok(css.includes('prefers-reduced-motion:reduce'),
  'Reduced-motion preference is retained');
console.log('PASS B887 Home: single scroll-linked greeting, stable Pursuit Iron identity and action geometry');
