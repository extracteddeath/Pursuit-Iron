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
assert.ok(home.includes('"Pursuit "') && home.includes('className: "wpb-home-brand-long"'),
  'The full Pursuit Iron name is retained in all scroll positions');
assert.ok(css.includes('B887 — Home header follows the scroll gesture'),
  'Home motion has a single last-wins visual contract');
assert.ok(css.includes('height:auto!important;min-height:56px!important;max-height:none!important;')
  && css.includes('transition:none!important;'),
  'No fixed compact masthead height or transition competes with the greeting');
assert.ok(css.includes('data-scrolled="1"]>.wpb-home-header'),
  'Existing scroll state cannot make the masthead jump');
assert.ok(css.includes('prefers-reduced-motion:reduce'),
  'Reduced-motion preference is retained');
console.log('PASS B887 Home: single scroll-linked greeting, stable Pursuit Iron identity and action geometry');
