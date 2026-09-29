import fs from 'node:fs';
import crypto from 'node:crypto';
const read=p=>fs.readFileSync(p,'utf8');
const write=(p,s)=>fs.writeFileSync(p,s);
const count=(s,n)=>s.split(n).length-1;
const once=(s,a,b,label)=>{const n=count(s,a);if(n!==1)throw new Error(`${label}: expected 1, found ${n}`);return s.replace(a,b);};

// Normal generation: one shared context and one memoized assembly transaction for every repair layer.
let gen=read('modules/next-engine/generate.js');
gen=once(gen,`import { createExerciseCatalog, createExerciseMap } from './exercise-db.js';`,`import { createEngineContext, createTransactionalEvaluator, auditVector, compareCandidateQuality } from './engine-context.js';`,'generate context import');
gen=once(gen,`    const muscles = createMusclePrescriptions(request, phase);`,`    const context = createEngineContext(request);
    const muscles = createMusclePrescriptions(request, phase);`,'generate context creation');
gen=once(gen,`    const assemble = (sessions) => {`,`    const assembleRaw = (sessions) => {`,'generate raw assemble');
gen=once(gen,`        return { base, audit: auditProgram(base, request, { skipRecoveryRealization: true }) };
    };
    // Explicit focus repair`, `        return { base, audit: auditProgram(base, request, { skipRecoveryRealization: true }) };
    };
    const transaction = createTransactionalEvaluator(assembleRaw);
    const assemble = sessions => transaction.evaluate(sessions);
    // Explicit focus repair`,'generate memoized transaction');
gen=once(gen,`    const exerciseMap = createExerciseMap(request.customExercises);`,`    const exerciseMap = context.exerciseMap;`,'generate shared exercise map');
gen=once(gen,`    const auditCounts = (audit) => ({
        critical: audit.findings.filter(f => f.severity === 'critical').length,
        major: audit.findings.filter(f => f.severity === 'major').length,
        warnings: audit.findings.filter(f => f.severity === 'warning').length
    });`,`    const auditCounts = (audit) => {
        const vector = auditVector(audit);
        return { critical: vector.critical, major: vector.major, warnings: vector.warning };
    };`,'generate audit vector');
gen=once(gen,`            if (!best || counts.critical < best.critical || counts.major < best.major || (counts.major === best.major && remaining < best.coverageFindings) || (counts.major === best.major && remaining === best.coverageFindings && counts.warnings < best.warnings)) {
                best = { checked, critical: counts.critical, major: counts.major, warnings: counts.warnings, coverageFindings: remaining };
            }`,`            const quality = auditVector(checked.audit, remaining);
            if (!best || compareCandidateQuality(quality, best.quality) < 0) {
                best = { checked, quality, critical: counts.critical, major: counts.major, warnings: counts.warnings, coverageFindings: remaining };
            }`,'functional coverage comparator');
gen=once(gen,`    const exerciseCatalog = createExerciseCatalog(request.customExercises);`,`    const exerciseCatalog = context.exerciseCatalog;`,'generate shared catalog');
gen=once(gen,`    const equipmentEligibleForSession = (def, session) => {
        const day = request.schedule.days.find(d => d.day === session.day);
        if (!day)
            return false;
        const equipment = day.equipmentOverride ?? request.equipment.available;
        if ((def.flags.bodyweight || def.equipment.includes('bodyweight')) && request.equipment.bodyweight === 'exclude')
            return false;
        const setups = [def.equipment, ...(def.equipmentAlternatives ?? [])];
        if (!setups.some(setup => setup.every(item => item === 'bodyweight' ? request.equipment.bodyweight !== 'exclude' : equipment.includes(item))))
            return false;
        if (def.flags.barbell) {
            const max = day.maxBarbellMovements ?? request.restrictions.maxBarbellMovementsPerDay;
            const used = session.exercises.filter(ex => exerciseMap.get(ex.exerciseId)?.flags.barbell).length;
            if (used >= max)
                return false;
        }
        return !request.preferences.avoidedExercises?.includes(def.id);
    };`,`    const equipmentEligibleForSession = (def, session) => {
        if (!context.equipmentEligible(def, session.day))
            return false;
        const day = context.scheduleDay(session.day);
        if (def.flags.barbell) {
            const max = day.maxBarbellMovements ?? request.restrictions.maxBarbellMovementsPerDay;
            const used = session.exercises.filter(ex => exerciseMap.get(ex.exerciseId)?.flags.barbell).length;
            if (used >= max)
                return false;
        }
        return true;
    };`,'generate shared eligibility');
write('modules/next-engine/generate.js',gen);

// Phase transition: use the same context and transaction evaluator for continuity swaps.
let trans=read('modules/next-engine/phase-transition.js');
trans=once(trans,`import { createExerciseMap } from './exercise-db.js';`,`import { createEngineContext, createTransactionalEvaluator } from './engine-context.js';`,'transition context import');
trans=once(trans,`function isCompatibleReplacement(previous, candidate, day, request, exerciseMap) {
    const priorDef = exerciseMap.get(previous.exerciseId);
    const candidateDef = exerciseMap.get(candidate.exerciseId);
    if (!priorDef || !candidateDef)
        return false;
    if (!equipmentEligible(priorDef, day, request))
        return false;
    if (request.preferences.avoidedExercises?.includes(previous.exerciseId))
        return false;`,`function isCompatibleReplacement(previous, candidate, day, request, context) {
    const priorDef = context.exerciseById(previous.exerciseId);
    const candidateDef = context.exerciseById(candidate.exerciseId);
    if (!priorDef || !candidateDef)
        return false;
    if (!context.equipmentEligible(priorDef, day))
        return false;
    if (context.isAvoided(previous.exerciseId))
        return false;`,'transition compatibility context');
trans=once(trans,`function withExercise(program, sessionId, candidateId, previous, request) {
    const sessions = program.sessions.map(session => session.id !== sessionId ? session : {
        ...session,
        exercises: session.exercises.map(ex => ex.exerciseId !== candidateId ? ex : {
            ...ex,
            exerciseId: previous.exerciseId,
            name: previous.name
        })
    });
    const events = createTrainingSetEvents(sessions, request.customExercises);
    const muscleLedger = deriveMuscleLedger(events);
    return { ...program, sessions, events, muscleLedger };
}`,`function sessionsWithExercise(program, sessionId, candidateId, previous) {
    return program.sessions.map(session => session.id !== sessionId ? session : {
        ...session,
        exercises: session.exercises.map(ex => ex.exerciseId !== candidateId ? ex : {
            ...ex,
            exerciseId: previous.exerciseId,
            name: previous.name
        })
    });
}`,'transition session proposal');
trans=once(trans,`function transitionSessionScore(previous, target, request, exerciseMap) {`,`function transitionSessionScore(previous, target, request, exerciseMap, context) {`,'transition score context signature');
trans=once(trans,`        return def && equipmentEligible(def, target.day, request) && !request.preferences.avoidedExercises?.includes(ex.exerciseId);`,`        return def && context.equipmentEligible(def, target.day);`,'transition score eligibility');
trans=once(trans,`export function matchPriorSessionsForTransition(previousSessions, targetSessions, request) {
    const exerciseMap = createExerciseMap(request.customExercises);`,`export function matchPriorSessionsForTransition(previousSessions, targetSessions, request, suppliedContext) {
    const context = suppliedContext ?? createEngineContext(request);
    const exerciseMap = context.exerciseMap;`,'transition matcher context');
trans=once(trans,`            const local = transitionSessionScore(previous[j], target[i], request, exerciseMap);`,`            const local = transitionSessionScore(previous[j], target[i], request, exerciseMap, context);`,'transition score call');
trans=once(trans,`    const exerciseMap = createExerciseMap(request.customExercises);
    let program = generated;
    const previousByTargetSession = matchPriorSessionsForTransition(previous.sessions, program.sessions, request);`,`    const context = createEngineContext(request);
    let program = generated;
    const previousByTargetSession = matchPriorSessionsForTransition(previous.sessions, program.sessions, request, context);
    const transaction = createTransactionalEvaluator(sessions => {
        const events = createTrainingSetEvents(sessions, request.customExercises);
        const muscleLedger = deriveMuscleLedger(events);
        const { audit: _audit, ...withoutAudit } = generated;
        void _audit;
        const base = { ...withoutAudit, sessions, events, muscleLedger };
        return { base, audit: auditProgram(base, request) };
    });`,'transition transaction setup');
trans=once(trans,`                .filter(ex => isCompatibleReplacement(ex, candidate, session.day, request, exerciseMap))`,`                .filter(ex => isCompatibleReplacement(ex, candidate, session.day, request, context))`,'transition compatibility call');
trans=once(trans,`                const proposed = withExercise(program, session.id, candidate.exerciseId, previousExercise, request);
                const { audit: _audit, ...auditable } = proposed;
                void _audit;
                const audit = auditProgram(auditable, request);
                if (audit.result === 'pass') {
                    program = { ...proposed, audit, rationale: [...proposed.rationale, \`${'${previousExercise.name}'} was retained across the phase transition because recent performance supported continuity and the target-phase program still passed audit.\`] };`,`                const proposedSessions = sessionsWithExercise(program, session.id, candidate.exerciseId, previousExercise);
                const checked = transaction.evaluate(proposedSessions);
                if (checked.audit.result === 'pass') {
                    program = { ...checked.base, audit: checked.audit, rationale: [...program.rationale, \`${'${previousExercise.name}'} was retained across the phase transition because recent performance supported continuity and the target-phase program still passed audit.\`] };`,'transition cached candidate audit');
write('modules/next-engine/phase-transition.js',trans);

// Static cycle retargeting: shared context, structural memoization, and strict audit-vector ordering.
let cycle=read('modules/next-engine/cycle-runtime-adapter.js');
cycle=once(cycle,`import { createExerciseMap } from './exercise-db.js';`,`import { createEngineContext, createTransactionalEvaluator, auditVector, compareCandidateQuality } from './engine-context.js';`,'cycle context import');
cycle=once(cycle,`    const exerciseMap = createExerciseMap(normalized.customExercises);`,`    const context = createEngineContext(normalized);
    const exerciseMap = context.exerciseMap;`,'cycle shared exercise map');
cycle=once(cycle,`    const reaudited = (candidate) => {
        const events = createTrainingSetEvents(candidate.sessions, normalized.customExercises);
        const muscleLedger = deriveMuscleLedger(events);
        const prepared = { ...candidate, events, muscleLedger };
        const { audit: _audit, ...auditable } = prepared;
        void _audit;
        return { ...prepared, audit: auditProgram(auditable, normalized) };
    };
    const severity = (candidate) => candidate.audit.findings.reduce((sum, f) => sum + (f.severity === 'critical' ? 1000 : f.severity === 'major' ? 100 : f.severity === 'warning' ? 10 : 1), 0);`,`    const transaction = createTransactionalEvaluator(candidateSessions => {
        const events = createTrainingSetEvents(candidateSessions, normalized.customExercises);
        const muscleLedger = deriveMuscleLedger(events);
        const prepared = { ...base, sessions: candidateSessions, events, muscleLedger };
        const { audit: _audit, ...auditable } = prepared;
        void _audit;
        return { ...prepared, audit: auditProgram(auditable, normalized) };
    });
    const reaudited = (candidate) => {
        const checked = transaction.evaluate(candidate.sessions);
        return { ...candidate, events: checked.events, muscleLedger: checked.muscleLedger, audit: checked.audit };
    };`,'cycle transactional audit');
cycle=once(cycle,`        const currentScore = severity(repaired);
        let best;
        let bestScore = currentScore;
        let bestDelta = Number.POSITIVE_INFINITY;`,`        const currentQuality = auditVector(repaired.audit);
        let best;
        let bestQuality = currentQuality;
        let bestDelta = Number.POSITIVE_INFINITY;`,'cycle current quality');
cycle=once(cycle,`                    const score = severity(proposal);
                    const magnitude = Math.abs(delta);
                    if (score < bestScore || (score === bestScore && score < currentScore && magnitude < bestDelta)) {
                        best = proposal;
                        bestScore = score;
                        bestDelta = magnitude;
                    }`,`                    const magnitude = Math.abs(delta);
                    const proposalQuality = auditVector(proposal.audit, magnitude);
                    if (compareCandidateQuality(proposalQuality, currentQuality) < 0 && compareCandidateQuality(proposalQuality, bestQuality) < 0) {
                        best = proposal;
                        bestQuality = proposalQuality;
                        bestDelta = magnitude;
                    }`,'cycle set-delta comparator');
cycle=once(cycle,`                                const score = severity(proposal);
                                const penalty = donorPenalty + amount;
                                if (score < bestScore || (score === bestScore && score < currentScore && penalty < bestPenalty)) {
                                    best = proposal;
                                    bestScore = score;
                                    bestPenalty = penalty;
                                }`,`                                const penalty = donorPenalty + amount;
                                const proposalQuality = auditVector(proposal.audit, penalty);
                                if (compareCandidateQuality(proposalQuality, currentQuality) < 0 && compareCandidateQuality(proposalQuality, bestQuality) < 0) {
                                    best = proposal;
                                    bestQuality = proposalQuality;
                                    bestPenalty = penalty;
                                }`,'cycle atomic comparator');
write('modules/next-engine/cycle-runtime-adapter.js',cycle);

// Release metadata + offline runtime graph.
let config=read('modules/next-engine/config.js');
config=once(config,"export const ENGINE_VERSION = '0.62.6';","export const ENGINE_VERSION = '0.62.7';",'engine version');
write('modules/next-engine/config.js',config);
let app=read('modules/App.js');
app=once(app,"const __APP_VERSION__='3.220.0'; const __BUILD__='776';","const __APP_VERSION__='3.221.0'; const __BUILD__='777';",'app version');
write('modules/App.js',app);
let index=read('index.html'); index=index.replaceAll("build:'776'","build:'777'"); write('index.html',index);
let sw=read('sw.js');
sw=once(sw,'/* M170 causal progression/recovery + longitudinal transition continuity — Engine 0.62.6. */','/* M171 shared generation context + transactional repair engine — Engine 0.62.7. */','sw comment');
sw=once(sw,'const CACHE="pursuit-iron-production-m170-engine-correctness";','const CACHE="pursuit-iron-production-m171-transactional-context";','sw cache');
sw=once(sw,'  "./modules/next-engine/exercise-db.js",','  "./modules/next-engine/exercise-db.js",\n  "./modules/next-engine/engine-context.js",','sw engine-context shell');
write('sw.js',sw);
const profile=JSON.parse(read('BUILD_PROFILE.json'));
profile.milestone='M171'; profile.source='M170 + shared generation context and transactional repair evaluator'; profile.engine='0.62.7 shared context + memoized repair transactions'; profile.cache='pursuit-iron-production-m171-transactional-context'; profile.nextRuntimeModules=(profile.nextRuntimeModules||42)+1;
write('BUILD_PROFILE.json',JSON.stringify(profile,null,2)+'\n');

write('verification/m171-transaction-context-test.mjs',`import assert from 'node:assert/strict';
import fs from 'node:fs';
import { createEngineContext, createTransactionalEvaluator, programFingerprint, auditVector, compareCandidateQuality } from '../modules/next-engine/engine-context.js';
import { normalizeRequest } from '../modules/next-engine/prescription.js';
const request=normalizeRequest({athlete:{experience:'intermediate'},goal:{type:'hypertrophy',musclePriorities:{},liftPriorities:{}},schedule:{days:[{day:'monday',maxMinutes:60},{day:'friday',maxMinutes:60}]},equipment:{available:['barbell','rack','bench','dumbbell','cable','machine','bodyweight'],bodyweight:'allow',loading:{unit:'lb',barbell:{barWeight:45,platePairs:[{weight:45,pairs:8}]},dumbbells:{availablePerHand:[10,20,30,40,50]},machine:{minimum:5,increment:5,maximum:500},cable:{minimum:5,increment:5,maximum:300},smith:{minimum:5,increment:5,maximum:500},exerciseOverrides:{}}},restrictions:{maxBarbellMovementsPerDay:3,allowSupersets:true},preferences:{preferredSplit:'full_body',lockedSplit:'full_body',avoidedExercises:[]},customExercises:[],seed:171});
const context=createEngineContext(request); assert.ok(Object.isFrozen(context)); assert.equal(context.scheduleDay('monday').day,'monday'); assert.ok(context.exerciseCatalog.length>0);
const candidate=context.exerciseCatalog.find(x=>context.equipmentEligible(x,'monday')); assert.ok(candidate); assert.equal(context.exerciseById(candidate.id),candidate);
const sessions=[{id:'a',day:'monday',intent:'full',maxMinutes:60,exercises:[{exerciseId:candidate.id,role:'hypertrophy_compound',sets:3,prescription:{reps:[8,12],rir:[1,3],restSeconds:90},progression:'double'}]}];
let calls=0; const tx=createTransactionalEvaluator(s=>{calls++;return {fingerprint:programFingerprint(s)};});
const first=tx.evaluate(sessions); const second=tx.evaluate(structuredClone(sessions)); assert.equal(first,second); assert.equal(calls,1); assert.equal(tx.stats().hits,1); assert.equal(tx.stats().misses,1);
const changed=structuredClone(sessions); changed[0].exercises[0].sets=4; assert.notEqual(programFingerprint(sessions),programFingerprint(changed)); tx.evaluate(changed); assert.equal(calls,2);
const qA=auditVector({findings:[{severity:'warning'}]},99); const qB=auditVector({findings:[{severity:'warning'},{severity:'warning'}]},0); assert.ok(compareCandidateQuality(qA,qB)<0);
const qC=auditVector({findings:[{severity:'warning'}]},2); assert.ok(compareCandidateQuality(qC,qA)<0);
for(const [file,markers] of [['modules/next-engine/generate.js',['createEngineContext(request)','createTransactionalEvaluator(assembleRaw)']],['modules/next-engine/phase-transition.js',['createEngineContext(request)','createTransactionalEvaluator(sessions =>']],['modules/next-engine/cycle-runtime-adapter.js',['createEngineContext(normalized)','createTransactionalEvaluator(candidateSessions =>']]]){const src=fs.readFileSync(new URL('../'+file,import.meta.url),'utf8');for(const marker of markers)assert.ok(src.includes(marker),file+' missing '+marker);}
console.log('M171 shared context/transaction tests OK.');
`);
let verify=read('scripts/verify-release.mjs');
if(!verify.includes('verification/m171-transaction-context-test.mjs')){const hook="execFileSync(process.execPath,['--no-warnings','--experimental-loader','./verification/import-loader.mjs','./verification/m170-engine-correctness-test.mjs'],{stdio:'inherit',cwd:root});";verify=once(verify,hook,hook+"\nexecFileSync(process.execPath,['--no-warnings','--experimental-loader','./verification/import-loader.mjs','./verification/m171-transaction-context-test.mjs'],{stdio:'inherit',cwd:root});",'M171 verify hook');}
write('scripts/verify-release.mjs',verify);
let changelog=read('CHANGELOG.md'); const entry=`## M171 — Shared Transactional Generation Context (3.221.0 / build 777 / Engine 0.62.7)\n\n- Adds a per-generation catalog/schedule/eligibility context and candidate indexes.\n- Memoizes structurally identical program candidates across repair loops.\n- Uses one strict audit comparison order: critical → major → warning → repair objective.\n- Routes generation repair, phase-transition swaps, and locked-cycle repair through the shared transaction layer.\n\n`; if(!changelog.startsWith('## M171 —'))changelog=entry+changelog; write('CHANGELOG.md',changelog);
write('M171_REPORT.md',`# M171 shared generation context and transactional repair\n\nApp **3.221.0**, build **777**, Pursuit Engine **0.62.7**.\n\nItem 6 is implemented across normal generation repair, phase-transition continuity repair, and static-cycle repair. Candidate structure is fingerprinted and memoized, catalog/schedule/equipment/avoidance lookups are shared per generation, and candidate ordering is lexicographic by critical, major, warning, then objective.\n`);
const manifest=JSON.parse(read('RELEASE_MANIFEST.json')); manifest.milestone='M171'; manifest.appVersion='3.221.0'; manifest.build=777; manifest.engineVersion='0.62.7'; manifest.runtimeFiles['modules/next-engine/engine-context.js']=''; manifest.localCandidate={name:'M171 Shared Transactional Context',base:'M170 / Engine 0.62.6',validation:'M171 context/memoization/comparator tests plus full release suite.'};
const hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex'); for(const file of Object.keys(manifest.runtimeFiles||{}))manifest.runtimeFiles[file]=hash(file); const blob=Object.keys(manifest.runtimeFiles||{}).sort().map(file=>`${file}:${manifest.runtimeFiles[file]}\n`).join(''); manifest.runtimeAggregate=crypto.createHash('sha256').update(Buffer.from(blob)).digest('hex'); for(const file of Object.keys(manifest.uiFiles||{}))manifest.uiFiles[file]=hash(file); write('RELEASE_MANIFEST.json',JSON.stringify(manifest,null,2)+'\n');
console.log('M171 patch applied.');
