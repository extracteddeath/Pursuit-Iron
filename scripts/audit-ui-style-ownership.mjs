import fs from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

// Read-only source audit; computed styles and real-device behavior require browser checks.
const app=fs.readFileSync('modules/App.js','utf8');
const css=fs.readFileSync('app.css','utf8');
const primitives=fs.readFileSync('modules/ui-primitives.js','utf8');
const count=(s,re)=>(s.match(re)||[]).length;
const blocks=[
 ['Home','function Home(','function UiNotice('],
 ['Plan','function PlanView(',null],
 ['Program','function ProgramView(',null],
 ['Next workout','function NextWorkoutView(',null],
 ['Cycles','function CyclesView(',null],
 ['Wizard','function Wizard(',null],
 ['Library','function LibraryView(',null],
 ['Progress','function HistoryView(',null],
 ['Profile','function ProfileView(',null],
 ['Settings','function SettingsView(','function CompareView('],
 ['Gallery','function GalleryView(','/* ⚠ ONE OWNER'],
 ['Workout','function WorkoutSession(',null]
];
const slice=(from,to)=>{const start=app.indexOf(from);if(start<0)return '';
 const end=to?app.indexOf(to,start+from.length):app.indexOf('\nfunction ',start+from.length);
 return app.slice(start,end>start?end:undefined);
};
const screens=Object.fromEntries(blocks.map(([name,from,to])=>{
 const s=slice(from,to);
 const literalClasses=new Set([...s.matchAll(/className:\s*"([^"]+)"/g)]
  .flatMap(m=>m[1].split(/\s+/)).filter(Boolean));
 return [name,{present:!!s,sourceCharacters:s.length,staticClassTokens:literalClasses.size,
  inlineStyleObjects:count(s,/style:\s*\{/g),sharedCardCalls:count(s,/_jsx(?:s)?\(UiCard,/g),
  sharedActionCalls:count(s,/_jsx(?:s)?\(UiAction,/g),
  sharedMetricCalls:count(s,/_jsx(?:s)?\(UiMetric,/g),
  sharedChoiceCalls:count(s,/_jsx(?:s)?\(UiChoice,/g),
  sharedSegmentedCalls:count(s,/_jsx(?:s)?\(UiSegmented,/g),
  sharedRootHeaderCalls:count(s,/_jsx(?:s)?\(UiRootHeader,/g),
  sharedSectionCalls:count(s,/_jsx(?:s)?\(UiSection,/g)}];
}));
const families=[
 ['Card','UiCard','wpb-ui-card'],['Metric','UiMetric','wpb-ui-metric'],['Action','UiAction','wpb-ui-action'],
 ['Choice','UiChoice','wpb-ui-choice'],['Segmented','UiSegmented','wpb-segmented'],
 ['Root header','UiRootHeader','wpb-root-header'],['Section','UiSection','wpb-ui-section'],
 ['Status','UiStatus','wpb-ui-status'],
 ['Sheet heading','UiSheetHeading','wpb-ui-sheet-heading'],
 ['Sheet close','UiSheetClose','wpb-ui-sheet-close']
];
const shared=Object.fromEntries(families.map(([name,symbol,cls])=>[name,{
 ownerExported:primitives.includes('export function '+symbol+'('),
 appCalls:count(app,new RegExp('_jsx(?:s)?\\('+symbol+'\\b','g')),
 cssRoleExists:css.includes('.'+cls)}]));
// M3 Expressive coverage must include components extracted out of App.js.
// These modules own visual structure only; state and training calculations stay
// with the caller or the engine. Count actual JSX calls, not import names.
const programModules=[
 'modules/program-settings-sheet.js',
 'modules/program-share-sheet.js',
 'modules/program-reference-surfaces.js',
 'modules/program-schedule-sheet.js',
 'modules/program-review-surfaces.js',
 'modules/program-exercise-options.js',
 'modules/program-training-maxes.js'
];
const programSheetCoverage=Object.fromEntries(programModules.map(file=>{
 const source=fs.readFileSync(file,'utf8');
 return [file,{
  sharedHeadingCalls:count(source,/_jsx(?:s)?\(UiSheetHeading,/g),
  sharedCloseCalls:count(source,/_jsx(?:s)?\(UiSheetClose,/g),
  sharedActionCalls:count(source,/_jsx(?:s)?\(UiAction,/g),
  inlineStyleObjects:count(source,/style:\s*\{/g)
 }];
}));
const programSheetHeadingCalls=Object.values(programSheetCoverage)
 .reduce((sum,row)=>sum+row.sharedHeadingCalls,0);
const inlineDebtRanking=Object.entries(screens)
 .filter(([,row])=>row.present)
 .sort((a,b)=>b[1].inlineStyleObjects-a[1].inlineStyleObjects)
 .map(([name,row])=>({screen:name,inlineStyleObjects:row.inlineStyleObjects,
  sourceCharacters:row.sourceCharacters}));

export const report={
 name:'Pursuit Iron shared UI ownership — source inventory',
 scope:'App.js, seven extracted Program-sheet modules, shared primitives and app.css; runtime DOM and browser CSS still need verification',
 cssCharacters:css.length,cssPriorityDeclarations:count(css,/!important/g),
 appInlineStyleObjects:count(app,/style:\s*\{/g),
 appEmbeddedPriorityDeclarations:count(app,/!important/g),
 unownedStandardCardCallSites:count(app,/_(?:jsx|jsxs)\("(?:div|section|article)", \{ className: "wpb-ui-card(?:\s|")/g),
 screens,shared,programSheetCoverage,programSheetHeadingCalls,inlineDebtRanking
};
if(process.argv[1] && fileURLToPath(import.meta.url)===path.resolve(process.argv[1])){
 if(process.argv.includes('--json')) console.log(JSON.stringify(report,null,2));
 else {
  console.log('UI ownership:',JSON.stringify({cssCharacters:report.cssCharacters,
   cssPriorityDeclarations:report.cssPriorityDeclarations,appInlineStyleObjects:report.appInlineStyleObjects,
   appEmbeddedPriorityDeclarations:report.appEmbeddedPriorityDeclarations,
   unownedStandardCardCallSites:report.unownedStandardCardCallSites}));
  for(const [name,value] of Object.entries(report.screens)) console.log(name+': '+JSON.stringify(value));
  console.log('Shared components:',JSON.stringify(report.shared));
  console.log('Program sheets:',JSON.stringify(report.programSheetCoverage));
  console.log('Inline-debt ranking:',JSON.stringify(report.inlineDebtRanking));
 }
}
