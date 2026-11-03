import assert from 'node:assert/strict';
import fs from 'node:fs';

const original=fs.readFileSync('modules/App.js','utf8');
const root='android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/';
const colors=fs.readFileSync(root+'NativeDesignTokens.kt','utf8');
const main=fs.readFileSync(root+'MainActivity.kt','utf8');
const navigation=fs.readFileSync(root+'NativePreviewApp.kt','utf8');
const picker=fs.readFileSync(root+'NativeAppearanceSheet.kt','utf8');

const themes=[...original.matchAll(/^\s*(\w+): \{ name: "([^"]+)", base: "(dark|light)", accent: "(#[0-9A-Fa-f]{6})", palette: TOK\(\{ (.*) \}\) \},?$/gm)]
  .map(([,id,name,base,accent,body])=>{
    const p=Object.fromEntries([...body.matchAll(/([A-Za-z]\w*): "(.*?)"/g)]
      .map(([,k,v])=>[k,v]));
    return {id,name,base,accent,p};
  });
assert.equal(themes.length,20,'Require every shipped canonical theme, not a curated subset.');
assert.equal(themes.filter(theme=>theme.base==='light').length,10,
  'Preserve every original light theme alongside the dark choices.');
const native=[...colors.matchAll(/^\s*PursuitPalette\("(\w+)", "([^"]+)", (true|false), (.+)\),?$/gm)]
  .map(([,id,name,light,args])=>({id,name,light:light==='true',
    hex:[...args.matchAll(/Color\(0xFF([A-Fa-f0-9]{6})\)/g)].map(m=>'#'+m[1].toUpperCase())}));
assert.equal(native.length,themes.length,'Native theme names must track canonical JS.');
for(const [i,theme] of themes.entries()) {
  const actual=native[i];
  assert.equal(actual.id,theme.id);
  assert.equal(actual.name,theme.name);
  assert.equal(actual.light,theme.base==='light');
  const p=theme.p;
  assert.deepEqual(actual.hex.slice(0,12),
    [p.bg,p.bgTint,p.surface,p.surface2,p.cream,p.muted,p.mutedSoft,p.accent,
      p.onAccent,p.accentInk||p.accent,p.warm,p.danger].map(v=>v.toUpperCase()),
    theme.name+' colors must remain the original per-theme palette');
  assert.equal(actual.hex.length,17,'Preserve TOK-derived contrast ink roles.');
}
assert.match(colors,/const val DEFAULT_ID="lime"/);
assert.match(colors,/surfaceContainerLow=tint,surfaceContainer=card/);
assert.match(colors,/primary=accent,onPrimary=onAccent/);
assert.match(colors,/val accentInk:Color/);
assert.match(main,/PursuitIronTheme\(themeId=themeId\)/);
assert.match(main,/val themeUsesLightBars=PursuitThemes.resolve\(themeId\).isLight/);
assert.match(main,/bars\.isAppearanceLightStatusBars=themeUsesLightBars/);
assert.match(main,/bars\.isAppearanceLightNavigationBars=themeUsesLightBars/);
assert.match(main,/getSharedPreferences\("pursuit_native_appearance",MODE_PRIVATE\)/);
assert.match(main,/appearance\.edit\(\)\.putString\("theme_id",requested\)\.apply\(\)/);
assert.match(main,/MaterialTheme\(colorScheme=palette\.scheme, motionScheme = MotionScheme\.expressive\(\)/);
assert.ok(!main.includes('PursuitDarkColors') && !main.includes('PursuitLightColors'));
assert.match(navigation,/onThemeSelected: \(String\)->Unit = \{\}/);
assert.match(navigation,/Icon\(Icons\.Outlined\.Palette,contentDescription="Choose color theme"\)/);
assert.match(picker,/PursuitThemes\.all\.filter \{it\.isLight==light\}/);
assert.match(picker,/role=Role\.RadioButton/);
assert.ok(!picker.includes('store.') && !picker.includes('journal.'));
console.log('PASS native theme picker and 20 exact original palette inputs; no training-store coupling');
