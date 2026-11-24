import {readFileSync,readdirSync} from 'node:fs';
import {dirname,join} from 'node:path';
import {fileURLToPath} from 'node:url';
// Inventory structure validation only. This neither executes coaching nor grants parity/release approval.
function validateInventory(data) {
    const require=(ok,message)=>{if(!ok)throw new Error(message)};
    const {matrix,behavior,findings,canonical,shell,sourceMap,fixtureNames}=data;
    const web="29faa204212a4d54088b4870341350e605939185";
    const kotlin="05c02ff2362736439ca1fcabfd7d477bfb2fa366";
    const rows=(doc,pattern)=>doc.split("\n").filter(line=>line.startsWith("| ")&&pattern.test(line.split("|")[1].trim())).map(line=>line.split("|").slice(1,-1).map(x=>x.trim()));
    const ids=items=>items.map(x=>Array.isArray(x)?x[0]:x.id);
    function unique(items,label,expected){require(items.length===expected,label+" count "+items.length+" expected "+expected);require(new Set(ids(items)).size===items.length,label+" duplicate ID")}
    function fixtures(value){for(const name of value.split(";").map(s=>s.trim()))require(fixtureNames.includes(name)||fixtureNames.includes(name+".kt"),"missing candidate fixture "+name)}
    const e=rows(matrix,/^E\d{2}$/), prog=rows(matrix,/^PROG-/), split=rows(matrix,/^SPLIT-/), pct=rows(matrix,/^PCT-/);
    const child=rows(behavior,/^(?:[A-Z][A-Z0-9]+-[A-Z0-9-]+|INJURY|READINESS)$/), issues=rows(findings,/^FND-S1-\d{3}$/);
    unique(e,"inherited E",41);unique(prog,"progression",13);unique(split,"topology",26);unique(pct,"percentage",9);unique(child,"behavior",87);unique(issues,"findings",16);
    const covered=new Set(),validStage=/^S[2-8]$/;
    for(const [id,stage,test,outcome,state] of e){require(validStage.test(stage)&&outcome&&state==="MAP","incomplete "+id);fixtures(test)}
    for(const [id,parent,stage,source,test,outcome,state] of child){
        require(validStage.test(stage)&&source&&outcome&&["MAP","GAP","REVIEW"].includes(state),"incomplete "+id);
        fixtures(test);
        const references=[...parent.matchAll(/E(\d{2})(?:[–-](?:E)?(\d{2}))?/g)];
        require(references.length>0,"missing parent "+id);
        for(const [,a,z] of references)for(let n=Number(a);n<=Number(z??a);n++){require(n>=1&&n<=41,"invalid parent "+id);covered.add("E"+String(n).padStart(2,"0"))}
    }
    for(const [id] of e)require(covered.has(id),"unassigned parent "+id);
    for(const [id,mode,outcome,test,state] of prog){require(mode&&outcome&&/^S2 \/ MAP$/.test(state),"incomplete "+id);fixtures(test)}
    for(const [id,family,days,native,state] of split)require(family&&days&&native&&/^S4 \/ (MAP|GAP)$/.test(state),"incomplete "+id);
    for(const [id,scheme,outcome,state] of pct)require(scheme&&outcome&&/^S2 \/ REVIEW$/.test(state),"incomplete "+id);
    for(const [id,classification,scope,stage,actual,expected,test] of issues){require(classification&&scope&&/^S[2-8](?:\/S[2-8])*$/.test(stage)&&actual&&expected,"incomplete "+id);fixtures(test)}
    require(matrix.includes(web)&&matrix.includes(kotlin),"matrix source pins");
    for(const doc of [canonical,shell])require(doc.schemaVersion===1&&doc.webCommit===web&&doc.kotlinCommit===kotlin,"catalog/shell source pins");
    require(sourceMap.schemaVersion===1&&sourceMap.webCommit===web,"source map pin");
    unique(canonical.rows,"canonical exercise",383);unique(shell.shellRows,"shell identity",372);unique(shell.templates,"template",75);
    const catById=new Map(canonical.rows.map(x=>[x.webId,x]));
    for(const x of canonical.rows){
        require(x.id==="CAT-"+x.webId&&x.name&&x.webLine>0&&x.kotlinLine>0&&x.stage==="S3"&&x.state==="MAP","invalid catalog "+x.id);
        require(Array.isArray(x.webEquipment)&&x.webMovementFamily&&canonical.acceptanceProfiles[x.evidenceProfile],"incomplete catalog "+x.id);
        if(x.kotlinSource==="measured")require(x.measurement?.kind&&x.measurement?.loadKind&&x.measurement?.sideRule,"untyped measurement "+x.id);
        else require(x.kotlinSource==="standard","unmatched catalog "+x.id);
    }
    require(canonical.rows.filter(x=>x.kotlinSource==="standard").length===369&&canonical.rows.filter(x=>x.kotlinSource==="measured").length===14,"canonical identity counts");
    require(canonical.shadowedWebDefinitions.length===26&&canonical.counts.missing===0&&canonical.counts.independentlyAccepted===0,"identity audit cannot claim behavior");
    for(const x of shell.shellRows)require(x.id==="SHELL-"+x.shellId&&x.name&&x.line>0&&catById.has(x.candidateWebId)&&["exact-id","explicit-web-alias","web-name-fallback-review"].includes(x.match)&&x.state==="MAP"&&x.stage==="S3","unmapped shell "+x.id);
    for(const [kind,count] of [["exact-id",346],["explicit-web-alias",22],["web-name-fallback-review",4]])require(shell.shellRows.filter(x=>x.match===kind).length===count,"shell count "+kind);
    const publicFamilies=new Set(shell.publicSplits.map(x=>x.family)),families=new Set(split.map(x=>x[1])),schemes=new Set(pct.map(x=>x[1]));
    require(publicFamilies.size===24&&[...publicFamilies].every(x=>families.has(x))&&families.has("custom")&&families.has("pplul"),"public topology scope");
    for(const x of shell.templates)require(x.id==="TEMPLATE-"+x.templateId&&x.line>0&&x.name&&families.has(x.split)&&Number.isInteger(x.days)&&x.days>=2&&x.days<=6&&x.session&&x.goal&&x.experience&&(!x.scheme||schemes.has(x.scheme))&&x.stage==="S4"&&x.state==="MAP","incomplete template "+x.id);
    require([...schemes].every(s=>shell.templates.some(x=>x.scheme===s)),"all nine schemes must be active in templates");
    const childIds=new Set(ids(child));
    require(sourceMap.records.length===144&&new Set(sourceMap.records.map(x=>x.path)).size===144,"web source map count");
    for(const x of sourceMap.records)require(x.path.startsWith("modules/")&&/^[a-f0-9]{40}$/.test(x.blobSha)&&childIds.has(x.primaryRequirement),"unassigned source "+x.path);
    return {inherited:e.length,child:child.length,progression:prog.length,topology:split.length,percentage:pct.length,canonical:canonical.rows.length,shell:shell.shellRows.length,templates:shell.templates.length,sourceFiles:sourceMap.records.length,findings:issues.length,acceptedBehavior:0};
}
const root=join(dirname(fileURLToPath(import.meta.url)),'../..');
const read=name=>readFileSync(join(root,name),'utf8');
const data={matrix:read('STAGE1_PARITY_MATRIX.md'),behavior:read('STAGE1_BEHAVIOR_INVENTORY.md'),findings:read('STAGE1_FINDINGS.md'),canonical:JSON.parse(read('STAGE1_CATALOG_INVENTORY.json')),shell:JSON.parse(read('STAGE1_WEB_SHELL_INVENTORY.json')),sourceMap:JSON.parse(read('STAGE1_WEB_SOURCE_MAP.json')),fixtureNames:["kotlin-engine/src/test/java/io/github/extracteddeath/pursuitiron/rebuild","native-coach-store/src/test/java/io/github/extracteddeath/pursuitiron/nativecoachstore","verification"].flatMap(dir=>readdirSync(join(root,dir)))};
console.log('PASS Stage 1 inventory structure (not behavior acceptance):',JSON.stringify(validateInventory(data)));
