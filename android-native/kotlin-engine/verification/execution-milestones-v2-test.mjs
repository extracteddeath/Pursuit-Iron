import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {dirname,join} from 'node:path';
import {fileURLToPath} from 'node:url';

// Execution ownership only; never turns a mapping into behavior acceptance.
const root=join(dirname(fileURLToPath(import.meta.url)),'../..');
const read=name=>readFileSync(join(root,name),'utf8');
const json=name=>JSON.parse(read(name));
const map=json('EXECUTION_MILESTONES_V2.json');
const matrix=read('STAGE1_PARITY_MATRIX.md');
const behavior=read('STAGE1_BEHAVIOR_INVENTORY.md');
const findings=read('STAGE1_FINDINGS.md');
const checks=json('STAGE1_BEHAVIOR_INVENTORY.json');
const catalog=json('STAGE1_CATALOG_INVENTORY.json');
const shell=json('STAGE1_WEB_SHELL_INVENTORY.json');
const rows=(doc,re)=>doc.split('\n').filter(l=>l.startsWith('| ')&&re.test(l.split('|')[1].trim())).map(l=>l.split('|').slice(1,-1).map(x=>x.trim()));
const ids=(doc,re)=>rows(doc,re).map(x=>x[0]);
function exact(actual,expected,label){
    assert.equal(new Set(actual).size,actual.length,label+' duplicate ID');
    assert.deepEqual([...actual].sort(),[...expected].sort(),label+' missing or extra ID');
}
assert.equal(map.schemaVersion,2);
assert.equal(map.baseline.webCommit,checks.webCommit);
assert.equal(map.baseline.kotlinCommit,checks.kotlinCommit);
const canonical=[...ids(behavior,/^(?:[A-Z][A-Z0-9]+-[A-Z0-9-]+|INJURY|READINESS)$/),
    ...ids(matrix,/^(?:PROG|SPLIT|PCT)-/),
    ...catalog.rows.map(x=>x.id),...shell.shellRows.map(x=>x.id),...shell.templates.map(x=>x.id)];
assert.equal(canonical.length,map.baseline.canonicalWorkItems);
assert.equal(canonical.length,965);
const required=checks.rows.filter(x=>x.scope==='required').map(x=>x.id);
const optional=checks.rows.filter(x=>x.scope==='optional-candidate').map(x=>x.id);
assert.equal(required.length,map.baseline.requiredSupplementaryChecks);
assert.equal(optional.length,map.baseline.optionalReviewCandidates);
exact(map.milestones.flatMap(x=>x.canonical),canonical,'canonical ownership');
exact(map.milestones.flatMap(x=>x.checks),required,'supplementary ownership');
exact(map.optionalReviewCandidates,optional,'optional scope review');
const parentRows=rows(matrix,/^E\d{2}$/);
assert.equal(parentRows.length,map.baseline.inheritedRollups);
exact(map.rollups.map(x=>x.id),parentRows.map(x=>x[0]),'parent rollups');
const seen=new Set();
for(const m of map.milestones){
    assert.match(m.id,/^S(?:[0-9]|10)[A-E]?$/);
    assert.ok(!seen.has(m.id),m.id+' duplicate milestone');
    assert.ok(m.title&&m.exit,m.id+' missing exit');
    for(const d of m.dependencies)assert.ok(seen.has(d),m.id+' missing or cyclic dependency '+d);
    seen.add(m.id);
}
for(const r of map.rollups){
    assert.equal(r.oldStage,parentRows.find(x=>x[0]===r.id)[1]);
    assert.ok(r.milestones.length&&r.milestones.every(x=>seen.has(x)),r.id+' owner');
    const n=Number(r.id.slice(1));
    const relevant=rows(behavior,/^(?:[A-Z][A-Z0-9]+-[A-Z0-9-]+|INJURY|READINESS)$/).filter(x=>
        [...x[1].matchAll(/E(\d{2})(?:[–-](?:E)?(\d{2}))?/g)]
        .some(([,a,z])=>n>=Number(a)&&n<=Number(z??a))).map(x=>x[0]);
    const relatedChecks=checks.rows.filter(x=>x.scope==='required'&&x.parents.includes(r.id)).map(x=>x.id);
    const owners=map.milestones.filter(m=>m.canonical.some(x=>relevant.includes(x))||m.checks.some(x=>relatedChecks.includes(x))).map(m=>m.id);
    exact(r.milestones,owners,r.id+' cross-stage rollup');
}
for(const f of ids(findings,/^FND-S1-\d{3}$/))assert.ok(seen.has(map.findings[f]),f+' ownership');
for(const owner of Object.values(map.findings))assert.ok(seen.has(owner),'unknown finding owner');
for(const d of map.crossDependencies){
    assert.ok(seen.has(d.milestone));
    assert.ok(d.requiresMinimum.every(x=>required.includes(x)));
    assert.ok(d.note);
}
const master=read('MASTER_DEVELOPMENT_PLAN.md');
assert.ok(master.includes('EXECUTION_MILESTONES_V2.json')&&master.includes('EXECUTION_STATUS_V2.md'));
assert.ok(master.includes('S9 — Private device acceptance')&&master.includes('explicit owner authorization'));
console.log('PASS v2 execution mapping (not acceptance): '+canonical.length+' canonical, '+required.length+' required supplementary, '+optional.length+' optional review, '+parentRows.length+' inherited rollups; '+seen.size+' milestones.');
