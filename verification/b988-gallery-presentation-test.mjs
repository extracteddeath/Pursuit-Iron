import assert from 'node:assert/strict';
import fs from 'node:fs';
const app=fs.readFileSync('modules/App.js','utf8');
const css=fs.readFileSync('app.css','utf8');
const start=app.indexOf('function GalleryView(');
const gallery=app.slice(start,app.indexOf('/* ⚠ ONE OWNER',start));
assert.ok(start>0&&gallery.length>5000);
assert.ok(!gallery.includes('style: {'),'Gallery fixed styling is fully CSS-owned');
for(const name of ['chip','header','message','retry','program','coached','tag','feedback','import','disclaimer']){
 assert.ok(gallery.includes('wpb-gallery-'+name),'Missing gallery '+name+' class');
 assert.equal(css.split('#root .wpb .wpb-gallery-'+name+'{').length-1,1,
   'Gallery '+name+' CSS has one owner');
}
assert.ok(gallery.includes('"aria-pressed": active'),'Filter state must be accessible');
assert.ok(gallery.includes('disabled: res && res.ok'),'Imported programs must prevent duplicate actions');
assert.ok(gallery.includes('setRetryKey(k => k + 1)'),'Offline retry stays functional');
const scope=css.slice(css.indexOf('/* B988 — Gallery'));
assert.ok(!scope.includes('!important'),'No new CSS priority overrides');
assert.ok(scope.includes('min-height:44px')&&scope.includes('min-height:48px'),'Touch targets remain usable');
assert.ok((app.match(/style: \{/g)||[]).length<=1203,'Inline debt budget cannot grow');
console.log('PASS B988: Gallery accessibility, styling ownership, import and offline retry');
