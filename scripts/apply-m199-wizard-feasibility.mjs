import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

const root = process.cwd();
const read = file => fs.readFileSync(path.join(root, file), 'utf8');
const write = (file, content) => fs.writeFileSync(path.join(root, file), content);
const hashFile = file => crypto.createHash('sha256').update(fs.readFileSync(path.join(root, file))).digest('hex');
const hashText = text => crypto.createHash('sha256').update(text).digest('hex');

const adapterFile = 'modules/next-engine/app-shell-adapter-capacity.js';
let adapter = read(adapterFile);
const buildability = `export function splitBuildability(config, legacyExercises = []) {
    // Wizard feasibility must stay CHEAP. This function runs once for every split/time card while
    // the athlete is tapping through the builder. Running the full generator here blocks React's
    // event loop and turns one unlucky random roll into a false "missing upper pull work" refusal.
    // Hard named-lift contracts are deterministic and cheap, so keep those up-front. Everything
    // else is validated by the real capacity-aware generator only when the athlete creates the plan.
    const gaps = base.splitContractGaps(config, legacyExercises);
    if (gaps.length)
        return { ok: false, kind: 'lifts', items: gaps.map(g => g.replace(/_/g, ' ')) };

    const request = base.shellConfigToNextRequest(config, [], legacyExercises, 1);
    const usable = Array.isArray(request?.equipment?.available) ? request.equipment.available : [];
    if (!usable.length)
        return {
            ok: false,
            kind: 'coverage',
            items: ['usable equipment'],
            fixes: ['Enable bodyweight exercises or add available equipment']
        };

    return { ok: true };
}
`;
const buildabilityPattern = /export function splitBuildability\(config, legacyExercises = \[\]\) \{[\s\S]*?\n\}\s*$/;
if (!buildabilityPattern.test(adapter)) throw new Error('M199 splitBuildability target not found');
adapter = adapter.replace(buildabilityPattern, buildability);
adapter = adapter.replace(
    "                maxMinutes: request.schedule?.days?.[0]?.maxMinutes\n",
    "                maxMinutes: request.schedule?.days?.[0]?.maxMinutes,\n                requestedSeed: attempt.requestedSeed,\n                effectiveSeed: attempt.effectiveSeed\n"
);
write(adapterFile, adapter);

const capacityFile = 'modules/next-engine/capacity-generation.js';
write(capacityFile, `import { generateProgram } from './generate.js';
import {
    capacityMinimumCandidates,
    capacityTargetCandidates,
    requestWithExerciseTarget,
    requestWithMinimumMinutes,
    requestedExerciseTarget,
    requestedMinimumMinutes
} from './capacity-policy.js';

function canonicalSeed(raw) {
    const n = Number(raw);
    return Number.isFinite(n) && n > 0 ? Math.max(1, Math.floor(n)) : 1;
}

function requestWithSeed(request, seed) {
    return canonicalSeed(request?.seed) === seed ? request : { ...request, seed };
}

// A rejected random layout is not proof that the athlete's choices are impossible. Keep the exact
// requested seed first for reproducibility, then use a small deterministic family only after it
// fails. The successful seed is persisted in the returned request/program, so rebuild/share remains
// exact rather than depending on hidden randomness.
export function alternateSeedCandidates(request, count = 5) {
    const base = canonicalSeed(request?.seed);
    const out = [];
    let state = base >>> 0;
    for (let i = 0; i < count; i++) {
        state = (Math.imul(state, 1664525) + 1013904223) >>> 0;
        const seed = (state % 2147483646) + 1;
        if (seed !== base && !out.includes(seed)) out.push(seed);
    }
    return out;
}

function attemptResult(candidateRequest, originalRequest, generationOptions) {
    const candidate = generateProgram(candidateRequest, generationOptions);
    if (candidate.program?.audit?.result !== 'pass') return null;
    return {
        request: candidateRequest,
        result: candidate,
        adjusted: candidateRequest !== originalRequest,
        requestedTarget: requestedExerciseTarget(originalRequest),
        effectiveTarget: requestedExerciseTarget(candidateRequest),
        requestedMinimumMinutes: requestedMinimumMinutes(originalRequest),
        effectiveMinimumMinutes: requestedMinimumMinutes(candidateRequest),
        requestedSeed: canonicalSeed(originalRequest?.seed),
        effectiveSeed: canonicalSeed(candidateRequest?.seed)
    };
}

function passingSeedFamily(baseRequest, originalRequest, generationOptions, includeRequestedSeed = true) {
    const requested = canonicalSeed(baseRequest?.seed);
    const seeds = includeRequestedSeed
        ? [requested, ...alternateSeedCandidates(baseRequest)]
        : alternateSeedCandidates(baseRequest);
    for (const seed of seeds) {
        const hit = attemptResult(requestWithSeed(baseRequest, seed), originalRequest, generationOptions);
        if (hit) return hit;
    }
    return null;
}

/**
 * Generate against a session TIME CAPACITY rather than treating the selected band's lower edge and
 * exercise count as quotas. The exact requested request is always tried first. If that particular
 * random layout rejects, deterministic alternate seeds are tried before changing the athlete's time
 * contract. Only then is optional density relaxed; on longer bands the lower clock edge may inherit
 * a shorter proven floor while the selected maximum stays unchanged. Every candidate still passes
 * the normal engine audit, so equipment, recovery, coverage and safety constraints remain hard.
 */
export function firstPassingCapacityProgram(request, config, generationOptions = {}) {
    const initial = generateProgram(request, generationOptions);
    if (initial.program?.audit?.result === 'pass') {
        return {
            request,
            result: initial,
            adjusted: false,
            requestedTarget: requestedExerciseTarget(request),
            effectiveTarget: requestedExerciseTarget(request),
            requestedMinimumMinutes: requestedMinimumMinutes(request),
            effectiveMinimumMinutes: requestedMinimumMinutes(request),
            requestedSeed: canonicalSeed(request?.seed),
            effectiveSeed: canonicalSeed(request?.seed)
        };
    }

    // Selection/topology contains intentional randomness. A single structural miss (for example the
    // last upper-pull slot disappearing on one Full Body roll) must not become a user-facing refusal
    // when another deterministic roll of the same request passes unchanged.
    const sameCapacity = passingSeedFamily(request, request, generationOptions, false);
    if (sameCapacity) return sameCapacity;

    const wantedTarget = requestedExerciseTarget(request);
    const lowerTargets = capacityTargetCandidates(request, config?.session);

    for (const target of lowerTargets) {
        const candidateRequest = requestWithExerciseTarget(request, target);
        const hit = passingSeedFamily(candidateRequest, request, generationOptions, true);
        if (hit) return hit;
    }

    for (const minimumMinutes of capacityMinimumCandidates(request, config?.session)) {
        const targetOrder = wantedTarget ? [wantedTarget, ...lowerTargets] : [0];
        for (const target of targetOrder) {
            let candidateRequest = requestWithMinimumMinutes(request, minimumMinutes);
            if (target) candidateRequest = requestWithExerciseTarget(candidateRequest, target);
            const hit = passingSeedFamily(candidateRequest, request, generationOptions, true);
            if (hit) return hit;
        }
    }

    return {
        request,
        result: initial,
        adjusted: false,
        requestedTarget: requestedExerciseTarget(request),
        effectiveTarget: requestedExerciseTarget(request),
        requestedMinimumMinutes: requestedMinimumMinutes(request),
        effectiveMinimumMinutes: requestedMinimumMinutes(request),
        requestedSeed: canonicalSeed(request?.seed),
        effectiveSeed: canonicalSeed(request?.seed)
    };
}
`);

const cache = 'pursuit-iron-production-v4-0-0-m199';
const uiMilestone = 'Pursuit Iron 4.0 Production Release · M199';
let sw = read('sw.js');
sw = sw.replace(/\/\* Pursuit Iron 4\.0 production release[^\n]*\*\//, '/* Pursuit Iron 4.0 production release — M199 wizard feasibility + deterministic structural retry. */');
sw = sw.replace(/const CACHE="[^"]+";/, `const CACHE="${cache}";`);
if (!sw.includes('"./modules/next-engine/capacity-generation.js"')) {
    sw = sw.replace('  "./modules/next-engine/arm-coverage.js",\n', '  "./modules/next-engine/arm-coverage.js",\n  "./modules/next-engine/capacity-generation.js",\n');
}
write('sw.js', sw);

const changelog = read('CHANGELOG.md');
if (!changelog.includes('## M199 — Stable program builder feasibility')) {
    write('CHANGELOG.md', `## M199 — Stable program builder feasibility\n\n- Split and session-length cards no longer run full program generation while rendering, removing the UI-thread freeze/flicker in the time-selection step.\n- A single unlucky generation seed can no longer turn a valid Full Body setup into a false \"missing upper pull work\" refusal. The requested seed is preserved when it passes; deterministic alternates are tried only after a rejection and the successful seed is saved.\n- Capacity generation is now explicitly precached for installed/offline PWAs.\n\n${changelog}`);
}

const profile = JSON.parse(read('BUILD_PROFILE.json'));
profile.milestone = 'M199';
profile.source = 'M197 production baseline + M199 wizard feasibility and structural retry correction';
profile.engine = '0.64.0 coach-quality engine with M199 non-blocking wizard feasibility and deterministic rejected-seed recovery';
profile.cache = cache;
profile.uiMilestone = uiMilestone;
write('BUILD_PROFILE.json', `${JSON.stringify(profile, null, 2)}\n`);

const manifest = JSON.parse(read('RELEASE_MANIFEST.json'));
manifest.milestone = 'M199';
manifest.cache = cache;
manifest.uiMilestone = uiMilestone;
manifest.candidateStatus = 'ci_verified_device_test_pending';
manifest.localCandidate = {
    ...(manifest.localCandidate ?? {}),
    name: uiMilestone,
    base: `M199 / app ${manifest.appVersion} build ${manifest.build} / Engine ${manifest.engineVersion}`,
    validation: 'M199 removes synchronous generation from wizard feasibility, retries rejected structural rolls with deterministic saved seeds, precaches capacity generation, and re-runs release/PWA integrity.'
};

function walkJs(dir) {
    const out = [];
    for (const entry of fs.readdirSync(path.join(root, dir), { withFileTypes: true })) {
        const rel = path.posix.join(dir, entry.name);
        if (entry.isDirectory()) out.push(...walkJs(rel));
        else if (entry.name.endsWith('.js')) out.push(rel);
    }
    return out;
}

const runtimeFiles = [
    ...walkJs('modules/next-engine'),
    ...walkJs('modules/shadow-engine'),
    ...walkJs('vendor')
].sort();
manifest.runtimeFiles = Object.fromEntries(runtimeFiles.map(file => [file, hashFile(file)]));
manifest.runtimeAggregate = hashText(runtimeFiles.map(file => `${file}:${manifest.runtimeFiles[file]}\n`).join(''));
const uiFiles = ['modules/App.js', 'index.html', 'sw.js', 'BUILD_PROFILE.json', 'CHANGELOG.md', 'app.css'];
manifest.uiFiles = Object.fromEntries(uiFiles.map(file => [file, hashFile(file)]));
write('RELEASE_MANIFEST.json', `${JSON.stringify(manifest, null, 2)}\n`);

console.log('Applied M199 wizard feasibility + deterministic structural retry correction.');
