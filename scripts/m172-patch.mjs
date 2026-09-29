import fs from 'node:fs';
import crypto from 'node:crypto';
const read=p=>fs.readFileSync(p,'utf8');
const write=(p,s)=>fs.writeFileSync(p,s);
const count=(s,n)=>s.split(n).length-1;
const once=(s,a,b,label)=>{const n=count(s,a);if(n!==1)throw new Error(`${label}: expected 1, found ${n}`);return s.replace(a,b);};

// 1) Allocator: allow the second pass to consume the exact realized strength ledger/time instead of
//    the legacy lift-name projection. First-pass behavior remains available solely to discover topology.
let allocator=read('modules/next-engine/allocator.js');
allocator=once(allocator,
`export function allocateTraining(request, muscles, strengthClaims, phase) {
    const allocations = [];
    const projectedFromStrength = {};`,
`export function allocateTraining(request, muscles, strengthClaims, phase, options = {}) {
    const allocations = [];
    const projectedFromStrength = {};
    const actualStrengthBaseline = options.strengthBaseline;`,
'allocator signature');
allocator=once(allocator,
`        const projected = projectedStrengthContribution[claim.lift] ?? {};
        const setEquivalent = claim.role === 'heavy' ? 4 : 3;
        const scale = setEquivalent / 7;
        for (const [muscle, amount] of Object.entries(projected)) {
            projectedFromStrength[muscle] = (projectedFromStrength[muscle] ?? 0) + amount * scale;
        }
        strengthMinutes += claim.role === 'heavy' ? 20 : 15;`,
`        if (!actualStrengthBaseline) {
            const projected = projectedStrengthContribution[claim.lift] ?? {};
            const setEquivalent = claim.role === 'heavy' ? 4 : 3;
            const scale = setEquivalent / 7;
            for (const [muscle, amount] of Object.entries(projected)) {
                projectedFromStrength[muscle] = (projectedFromStrength[muscle] ?? 0) + amount * scale;
            }
            strengthMinutes += claim.role === 'heavy' ? 20 : 15;
        }`,
'legacy projection guarded');
allocator=once(allocator,
`    const byMuscle = new Map(muscles.map(p => [p.muscle, p]));`,
`    if (actualStrengthBaseline) {
        for (const [muscle, amount] of Object.entries(actualStrengthBaseline.fractional ?? {}))
            if (Number.isFinite(amount) && amount > 0)
                projectedFromStrength[muscle] = amount;
        strengthMinutes = Math.max(0, Number(actualStrengthBaseline.estimatedMinutes) || 0);
        decisions.push(`Residual muscle work was allocated from ${actualStrengthBaseline.realizedCount ?? 0} realized strength anchor${(actualStrengthBaseline.realizedCount ?? 0) === 1 ? '' : 's'} using their actual exercise stimulus and session time.`);
        decisionLog.push({
            iteration: iteration++, target: 'strength_baseline', action: 'baseline',
            reason: 'Strength anchors were selected first; their actual exercise-level muscle credits, sets, rest and shared session overhead replaced generic lift-name projections before hypertrophy allocation.'
        });
    }
    const byMuscle = new Map(muscles.map(p => [p.muscle, p]));`,
'actual strength baseline load');
allocator=once(allocator,
`            stopUtility: stopUtility === null ? null : Math.round(stopUtility * 1000) / 1000
        }`,
`            stopUtility: stopUtility === null ? null : Math.round(stopUtility * 1000) / 1000,
            strengthBaselineSource: actualStrengthBaseline ? 'realized_anchors' : 'projected_lifts',
            strengthMinutes: Math.round(strengthMinutes * 10) / 10
        }`,
'allocator baseline metrics');
write('modules/next-engine/allocator.js',allocator);

// 2) Realizer: factor one authoritative strength-set rule, add an anchor-only preview, and support
//    pinning those exact exercise identities/sets during the final realization pass.
let realizer=read('modules/next-engine/realizer.js');
realizer=once(realizer,
`export function finalizePlannedSession(session, request) {
    const exerciseMap = createExerciseMap(request.customExercises);
    return sequenceSessionExercises(assignAccessorySupersets({ ...session, exercises: session.exercises.map(ex => ({ ...ex })), estimatedMinutes: 0 }, exerciseMap, request.restrictions.allowSupersets), exerciseMap);
}
export function realizeSessions(plans, request, targetDose = {}, directTargetDose = {}, phase) {`,
`export function finalizePlannedSession(session, request) {
    const exerciseMap = createExerciseMap(request.customExercises);
    return sequenceSessionExercises(assignAccessorySupersets({ ...session, exercises: session.exercises.map(ex => ({ ...ex })), estimatedMinutes: 0 }, exerciseMap, request.restrictions.allowSupersets), exerciseMap);
}
function strengthSetsForAllocation(allocation, session, request, policy) {
    const shortSession = session.maxMinutes <= 35;
    const denseThreeDayStrength = request.goal.type === 'strength' && request.schedule.days.length <= 3;
    const baseSets = allocation.role === 'primary_strength'
        ? (request.athlete.experience === 'novice' ? 3 : (shortSession ? 3 : 4))
        : (shortSession || denseThreeDayStrength || request.athlete.experience === 'novice' ? 2 : 3);
    return Math.max(2, Math.round(baseSets * policy.strengthVolumeMultiplier));
}
/**
 * Deterministically selects only protected strength anchors for a provisional topology. The returned
 * fractional ledger and time are the actual baseline used by the second allocator pass; the anchor map
 * is later pinned so the final program cannot silently switch to a different variant after budgeting.
 */
export function realizeStrengthAnchors(plans, request, phase) {
    const policy = phasePolicyFor(phase);
    const exerciseCatalog = createExerciseCatalog(request.customExercises);
    const ledger = { fractional: {}, direct: {} };
    const anchors = {};
    const sessionMinutes = {};
    const missingAllocationIds = [];
    let realizedCount = 0;
    for (const plan of plans) {
        const chosen = [];
        const planned = [];
        for (const allocation of plan.allocations.filter(x => x.kind === 'lift')) {
            const def = strengthCandidate(allocation, plan, request, chosen, exerciseCatalog);
            if (!def) {
                missingAllocationIds.push(allocation.id);
                continue;
            }
            const sets = strengthSetsForAllocation(allocation, plan, request, policy);
            const exercise = makePlanned(def, allocation.role, sets, policy, request.athlete.experience);
            chosen.push(def);
            planned.push(exercise);
            addToLedger(ledger, def, sets);
            anchors[allocation.id] = {
                allocationId: allocation.id, exerciseId: def.id, exerciseName: def.name,
                sets, sessionId: plan.id, day: plan.day, role: allocation.role, lift: allocation.lift
            };
            realizedCount++;
        }
        if (planned.length)
            sessionMinutes[plan.id] = estimateSessionMinutes(planned);
    }
    return {
        anchors,
        fractional: ledger.fractional,
        estimatedMinutes: Object.values(sessionMinutes).reduce((sum, value) => sum + value, 0),
        sessionMinutes,
        realizedCount,
        expectedCount: plans.reduce((sum, plan) => sum + plan.allocations.filter(x => x.kind === 'lift').length, 0),
        missingAllocationIds
    };
}
export function realizeSessions(plans, request, targetDose = {}, directTargetDose = {}, phase, options = {}) {`,
'realizer anchor preview insertion');
realizer=once(realizer,
`    const sessions = plans.map(plan => ({ plan, defs: [], exercises: [], importance: [] }));
    const ledger = { fractional: {}, direct: {} };`,
`    const sessions = plans.map(plan => ({ plan, defs: [], exercises: [], importance: [] }));
    const ledger = { fractional: {}, direct: {} };
    const pinnedStrengthAnchors = options.strengthAnchors ?? {};`,
'realizer pinned anchor map');
realizer=once(realizer,
`        for (const a of s.plan.allocations.filter(x => x.kind === 'lift')) {
            const def = strengthCandidate(a, s.plan, request, s.defs, exerciseCatalog);
            if (!def)
                continue;
            // Very short sessions need coach-like strength dosage rather than four-set anchors that leave no room
            // for the rest of the session. Preserve the exposure and progression rule, but trim one base set
            // at <=35 minutes before phase multipliers are applied.
            const shortSession = s.plan.maxMinutes <= 35;
            const denseThreeDayStrength = request.goal.type === 'strength' && request.schedule.days.length <= 3;
            // Three-day strength weeks already stack several high-specificity anchors across limited recovery
            // windows. Keep secondary exposures at two productive sets instead of automatically turning them
            // into a third fatigue-heavy set; the exposure/progression signal is preserved while overlap from
            // squat/hinge and press compounds stays recoverable.
            const baseSets = a.role === 'primary_strength' ? (request.athlete.experience === 'novice' ? 3 : (shortSession ? 3 : 4)) : (shortSession || denseThreeDayStrength || request.athlete.experience === 'novice' ? 2 : 3);
            const phaseSets = Math.max(2, Math.round(baseSets * policy.strengthVolumeMultiplier));
            add(s, a, def, phaseSets);
        }`,
`        for (const a of s.plan.allocations.filter(x => x.kind === 'lift')) {
            const pinned = pinnedStrengthAnchors[a.id];
            const def = pinned ? exerciseMap.get(pinned.exerciseId) : strengthCandidate(a, s.plan, request, s.defs, exerciseCatalog);
            if (!def)
                continue;
            if (pinned) {
                const eligible = equipmentEligible(def, s.plan, request)
                    && !request.preferences.avoidedExercises?.includes(def.id)
                    && (!pinned.sessionId || pinned.sessionId === s.plan.id)
                    && (def.liftSpecificity?.[a.lift] ?? 0) > .45;
                if (!eligible)
                    throw new Error(`Pinned strength anchor ${pinned.exerciseId} is no longer eligible for ${a.id} on ${s.plan.day}.`);
            }
            const phaseSets = pinned?.sets ?? strengthSetsForAllocation(a, s.plan, request, policy);
            add(s, a, def, phaseSets);
        }`,
'realizer pinned strength realization');
write('modules/next-engine/realizer.js',realizer);

// 3) Topology: expose the chosen seed and allow the second pass to rebuild the exact same structure.
let topology=read('modules/next-engine/topology.js');
topology=once(topology,
`export function solveTopology(request, allocations) {
    const days = request.schedule.days.length;
    const seeds = candidateStructures(days, request.preferences.lockedSplit, request.preferences.preferredSplit);`,
`export function solveTopology(request, allocations, options = {}) {
    const days = request.schedule.days.length;
    const seeds = options.seed ? [options.seed] : candidateStructures(days, request.preferences.lockedSplit, request.preferences.preferredSplit);`,
'topology seed lock signature');
topology=once(topology,
`    return {
        family: chosen.seed.family,`,
`    return {
        seed: chosen.seed,
        family: chosen.seed.family,`,
'topology exposes seed');
write('modules/next-engine/topology.js',topology);

// 4) Generation becomes explicitly two-pass when protected strength work exists:
//    provisional allocation/topology -> actual anchor preview -> residual allocation -> same topology + pinned anchors.
let generate=read('modules/next-engine/generate.js');
generate=once(generate,
`import { finalizePlannedSession, optimizeSetupAwareSessionSequence, progressionForExercise, repsForPhase, restForExercise, rirForPhase, realizeSessions } from './realizer.js';`,
`import { finalizePlannedSession, optimizeSetupAwareSessionSequence, progressionForExercise, repsForPhase, restForExercise, rirForPhase, realizeSessions, realizeStrengthAnchors } from './realizer.js';`,
'generate anchor import');
generate=once(generate,
`    const muscles = createMusclePrescriptions(request, phase);
    const strength = createStrengthClaims(request, phase);
    const allocation = allocateTraining(request, muscles, strength, phase);
    const topology = solveTopology(request, allocation.allocations);
    const realized = realizeSessions(topology.sessions, request, allocation.targetDose, allocation.directTargetDose, phase);`,
`    const muscles = createMusclePrescriptions(request, phase);
    const strength = createStrengthClaims(request, phase);
    const provisionalAllocation = allocateTraining(request, muscles, strength, phase);
    const provisionalTopology = solveTopology(request, provisionalAllocation.allocations);
    const hasStrengthAnchors = provisionalAllocation.allocations.some(a => a.kind === 'lift');
    const strengthBaseline = hasStrengthAnchors ? realizeStrengthAnchors(provisionalTopology.sessions, request, phase) : undefined;
    const allocation = strengthBaseline
        ? allocateTraining(request, muscles, strength, phase, { strengthBaseline })
        : provisionalAllocation;
    // Muscle dose can change after actual anchor accounting, but split identity/day contracts cannot:
    // otherwise a different topology could select different strength variants and reintroduce the same
    // circular projection error. Rebuild the chosen seed with residual muscle allocations only.
    const topology = strengthBaseline
        ? solveTopology(request, allocation.allocations, { seed: provisionalTopology.seed })
        : provisionalTopology;
    const realized = realizeSessions(topology.sessions, request, allocation.targetDose, allocation.directTargetDose, phase,
        strengthBaseline ? { strengthAnchors: strengthBaseline.anchors } : undefined);`,
'two-pass generation pipeline');
write('modules/next-engine/generate.js',generate);

// 5) M172 regression: prove the final strength anchors equal the preview baseline, actual credits/time feed
//    allocation, and the actual baseline meaningfully differs from the old generic projection in strength work.
write('verification/m172-actual-strength-baseline-test.mjs',`import assert from 'node:assert/strict';
import { allocateTraining } from '../modules/next-engine/allocator.js';
import { createExerciseMap } from '../modules/next-engine/exercise-db.js';
import { createMusclePrescriptions, createStrengthClaims, normalizeRequest } from '../modules/next-engine/prescription.js';
import { realizeSessions, realizeStrengthAnchors, estimateSessionMinutes } from '../modules/next-engine/realizer.js';
import { solveTopology } from '../modules/next-engine/topology.js';

const request=normalizeRequest({
  athlete:{experience:'intermediate'}, goal:{type:'strength',musclePriorities:{},liftPriorities:{bench_press:'high',back_squat:'high',deadlift:'high'}},
  schedule:{days:[{day:'monday',minMinutes:60,maxMinutes:90,targetExercises:7},{day:'wednesday',minMinutes:60,maxMinutes:90,targetExercises:7},{day:'friday',minMinutes:60,maxMinutes:90,targetExercises:7}]},
  equipment:{available:['barbell','rack','bench','dumbbell','cable','machine','leg_press','pullup_bar','bodyweight'],bodyweight:'allow',loading:{unit:'lb',barbell:{barWeight:45,platePairs:[{weight:45,pairs:8},{weight:25,pairs:4},{weight:10,pairs:4},{weight:5,pairs:4},{weight:2.5,pairs:4}]},dumbbells:{availablePerHand:[5,10,15,20,25,30,35,40,45,50,60,70,80,90,100]},machine:{minimum:5,increment:5,maximum:500},cable:{minimum:5,increment:5,maximum:300},smith:{minimum:5,increment:5,maximum:500},exerciseOverrides:{}}},
  restrictions:{maxBarbellMovementsPerDay:3,allowSupersets:true}, preferences:{preferredSplit:'strength_fb',lockedSplit:'strength_fb',avoidedExercises:[]}, customExercises:[], seed:172
});
const phase='strength_accumulation';
const muscles=createMusclePrescriptions(request,phase); const claims=createStrengthClaims(request,phase);
const projected=allocateTraining(request,muscles,claims,phase);
const provisionalTopology=solveTopology(request,projected.allocations);
const baseline=realizeStrengthAnchors(provisionalTopology.sessions,request,phase);
assert.ok(baseline.expectedCount>0); assert.equal(baseline.realizedCount,baseline.expectedCount); assert.equal(baseline.missingAllocationIds.length,0);
assert.ok(baseline.estimatedMinutes>0);
const actual=allocateTraining(request,muscles,claims,phase,{strengthBaseline:baseline});
assert.equal(actual.metrics.strengthBaselineSource,'realized_anchors');
assert.equal(actual.metrics.strengthMinutes,Math.round(baseline.estimatedMinutes*10)/10);
for(const [muscle,credit] of Object.entries(baseline.fractional)) assert.equal(actual.projectedFromStrength[muscle],credit);
const differs=Object.keys({...projected.projectedFromStrength,...actual.projectedFromStrength}).some(m=>Math.abs((projected.projectedFromStrength[m]??0)-(actual.projectedFromStrength[m]??0))>.05);
assert.equal(differs,true,'actual anchor ledger should replace at least one generic lift projection');
const topology=solveTopology(request,actual.allocations,{seed:provisionalTopology.seed});
assert.equal(topology.family,provisionalTopology.family);
assert.deepEqual(topology.sessions.map(s=>[s.id,s.day,s.intent]),provisionalTopology.sessions.map(s=>[s.id,s.day,s.intent]));
const final=realizeSessions(topology.sessions,request,actual.targetDose,actual.directTargetDose,phase,{strengthAnchors:baseline.anchors});
const map=createExerciseMap(request.customExercises);
const finalFractional={}; let finalStrengthMinutes=0; let finalStrengthCount=0;
for(const session of final){
  const strengthExercises=session.exercises.filter(ex=>ex.role==='primary_strength'||ex.role==='secondary_strength');
  if(strengthExercises.length) finalStrengthMinutes+=estimateSessionMinutes(strengthExercises);
  for(const ex of strengthExercises){
    const def=map.get(ex.exerciseId); assert.ok(def); finalStrengthCount++;
    for(const [muscle,c] of Object.entries(def.muscles)) finalFractional[muscle]=(finalFractional[muscle]??0)+c.credit*ex.sets;
    const pins=Object.values(baseline.anchors).filter(pin=>pin.sessionId===session.id&&pin.exerciseId===ex.exerciseId&&pin.sets===ex.sets);
    assert.ok(pins.length>=1,\`final strength exercise ${'${ex.exerciseId}'} was not pinned from preview\`);
  }
}
assert.equal(finalStrengthCount,baseline.realizedCount);
for(const muscle of new Set([...Object.keys(finalFractional),...Object.keys(baseline.fractional)])) assert.ok(Math.abs((finalFractional[muscle]??0)-(baseline.fractional[muscle]??0))<1e-9,\`strength credit mismatch for ${'${muscle}'}\`);
assert.equal(finalStrengthMinutes,baseline.estimatedMinutes);
console.log('M172 actual strength baseline tests OK.');
`);

// Release/version metadata.
let config=read('modules/next-engine/config.js'); config=once(config,"export const ENGINE_VERSION = '0.62.7';","export const ENGINE_VERSION = '0.63.0';",'engine version'); write('modules/next-engine/config.js',config);
let app=read('modules/App.js'); app=once(app,"const __APP_VERSION__='3.221.0'; const __BUILD__='777';","const __APP_VERSION__='3.222.0'; const __BUILD__='778';",'app version'); write('modules/App.js',app);
let index=read('index.html'); index=index.replaceAll("build:'777'","build:'778'"); write('index.html',index);
let sw=read('sw.js');
sw=once(sw,'/* M171 shared generation context + transactional repair engine — Engine 0.62.7. */','/* M172 actual realized strength baseline allocator — Engine 0.63.0. */','sw comment');
sw=once(sw,'const CACHE="pursuit-iron-production-m171-transactional-context";','const CACHE="pursuit-iron-production-m172-actual-strength-baseline";','sw cache'); write('sw.js',sw);
const profile=JSON.parse(read('BUILD_PROFILE.json')); profile.milestone='M172'; profile.source='M171 + actual realized strength baseline allocation'; profile.engine='0.63.0 two-pass actual strength-anchor residual allocator'; profile.cache='pursuit-iron-production-m172-actual-strength-baseline'; write('BUILD_PROFILE.json',JSON.stringify(profile,null,2)+'\n');
let verify=read('scripts/verify-release.mjs');
if(!verify.includes('verification/m172-actual-strength-baseline-test.mjs')){const hook="execFileSync(process.execPath,['--no-warnings','--experimental-loader','./verification/import-loader.mjs','./verification/m171-transaction-context-test.mjs'],{stdio:'inherit',cwd:root});";verify=once(verify,hook,hook+"\nexecFileSync(process.execPath,['--no-warnings','--experimental-loader','./verification/import-loader.mjs','./verification/m172-actual-strength-baseline-test.mjs'],{stdio:'inherit',cwd:root});",'M172 verify hook');} write('scripts/verify-release.mjs',verify);
let changelog=read('CHANGELOG.md'); const entry=`## M172 — Actual Strength-Baseline Allocation (3.222.0 / build 778 / Engine 0.63.0)\n\n- Realizes protected strength anchors before final hypertrophy allocation.\n- Replaces generic lift-name muscle credits and 20/15-minute estimates with the selected exercise variants, actual strength sets, prescribed rest and shared session overhead.\n- Locks the provisional topology for the residual pass and pins those exact strength anchors in final realization, eliminating the circular variant/projection mismatch.\n- Adds integration coverage proving preview and final strength ledgers are identical.\n\n`; if(!changelog.startsWith('## M172 —'))changelog=entry+changelog; write('CHANGELOG.md',changelog);
write('M172_REPORT.md',`# M172 actual realized strength baseline allocation\n\nApp **3.222.0**, build **778**, Pursuit Engine **0.63.0**.\n\nItem 7 replaces hard-coded strength projections with a deterministic two-pass pipeline. The first pass exists only to choose topology and strength anchors. Their actual exercise identities, set counts, muscle credits and anchor-session time become the baseline for residual hypertrophy allocation. The second pass keeps the topology fixed and pins the same anchors, so budgeting and the delivered program cannot diverge.\n`);
const manifest=JSON.parse(read('RELEASE_MANIFEST.json')); manifest.milestone='M172'; manifest.appVersion='3.222.0'; manifest.build=778; manifest.engineVersion='0.63.0'; manifest.localCandidate={name:'M172 Actual Strength Baseline Allocation',base:'M171 / Engine 0.62.7',validation:'M172 two-pass anchor integration test plus full release regression/torture suite.'};
const hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex'); for(const file of Object.keys(manifest.runtimeFiles||{}))manifest.runtimeFiles[file]=hash(file); const blob=Object.keys(manifest.runtimeFiles||{}).sort().map(file=>`${file}:${manifest.runtimeFiles[file]}\n`).join(''); manifest.runtimeAggregate=crypto.createHash('sha256').update(Buffer.from(blob)).digest('hex'); for(const file of Object.keys(manifest.uiFiles||{}))manifest.uiFiles[file]=hash(file); write('RELEASE_MANIFEST.json',JSON.stringify(manifest,null,2)+'\n');
console.log('M172 patch applied.');
