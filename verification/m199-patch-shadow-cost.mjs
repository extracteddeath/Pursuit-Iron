import fs from 'node:fs';
const path='modules/App.js';
let src=fs.readFileSync(path,'utf8');
function once(before,after,label){const n=src.split(before).length-1;if(n!==1)throw new Error(`${label}: expected 1 match, got ${n}`);src=src.replace(before,after);}

once(
`function generateNextWithShadow(options) {
    const live = generateNextProgramForShell(options);
    const withShadow = attachShadowEvaluation(live, options.config, options.banned || [], options.seed);`,
`function generateNextWithShadow(options) {
    const live = generateNextProgramForShell(options);
    // Shadow scoring is observational research, not part of the user's program contract. It costs roughly
    // another full generation pass, so production creation no longer pays for it unless research is
    // explicitly enabled or a reviewed selective-promotion rollout is actually active.
    const promotionActive = Array.isArray(M76_SELECTIVE_PROMOTION_MANIFEST?.entries)
        && M76_SELECTIVE_PROMOTION_MANIFEST.entries.some(e => e?.status === "approved" && Number(e?.rolloutPercent) > 0);
    const withShadow = (options.canaryResearch?.enabled || promotionActive)
        ? attachShadowEvaluation(live, options.config, options.banned || [], options.seed)
        : live;`,
'program shadow gate');

once(
`function attachShadowToCycleBuild(built, banned = []) {
    if (!built?.blocks)
        return built;`,
`function attachShadowToCycleBuild(built, banned = [], researchEnabled = false) {
    // A cycle can contain several full programs. Running an observational shadow pass for every block
    // can add hundreds of milliseconds to a user-facing build without changing the saved prescription.
    if (!built?.blocks || !researchEnabled)
        return built;`,
'cycle shadow gate');

const needle='attachShadowToCycleBuild(generateNextCycleForShell({';
let cursor=0,count=0;
while((cursor=src.indexOf(needle,cursor))>=0){
    const close=src.indexOf('}), banned)',cursor+needle.length);
    if(close<0)throw new Error(`cycle shadow call ${count+1}: close marker missing`);
    const end=close+'}), banned'.length;
    src=src.slice(0,end)+', !!canaryResearch?.enabled'+src.slice(end);
    cursor=end+', !!canaryResearch?.enabled'.length;
    count++;
}
if(count!==3)throw new Error(`Expected 3 cycle shadow call sites, found ${count}`);

fs.writeFileSync(path,src);
console.log(`M199 shadow-cost patch applied; gated ${count} cycle call sites.`);
