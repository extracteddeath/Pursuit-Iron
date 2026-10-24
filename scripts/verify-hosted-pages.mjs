#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { setTimeout as pause } from 'node:timers/promises';

const root = path.resolve(new URL('..', import.meta.url).pathname);
const origin = process.env.PURSUIT_PAGES_URL || 'https://extracteddeath.github.io/Pursuit-Iron/';
const base = new URL(origin);
if (!base.protocol.startsWith('http'))
    throw new Error('Hosted webapp must use HTTP(S)');
if (!base.pathname.endsWith('/'))
    throw new Error('PURSUIT_PAGES_URL must end in /');

const files = [
    'index.html',
    'sw.js',
    'manifest.webmanifest',
    'RELEASE_MANIFEST.json',
    'BUILD_PROFILE.json',
    'modules/main.js',
    'modules/App.js',
    'modules/engine-api.js',
    'modules/next-engine/cycle-runtime-adapter.js',
    'modules/next-engine/workout-history-adapter.js',
    'modules/next-engine/performance.js',
    'modules/next-engine/workout-runtime.js',
    'modules/training-domain/prescriptions.js'
];
const sha = buffer => crypto.createHash('sha256').update(buffer).digest('hex');
const expected = Object.fromEntries(files.map(file => [file, sha(fs.readFileSync(path.join(root, file)))]));
const profile = JSON.parse(fs.readFileSync(path.join(root, 'BUILD_PROFILE.json'), 'utf8'));
const manifest = JSON.parse(fs.readFileSync(path.join(root, 'RELEASE_MANIFEST.json'), 'utf8'));
const serviceWorker = fs.readFileSync(path.join(root, 'sw.js'), 'utf8');
if (profile.cache !== manifest.cache ||
    !serviceWorker.includes('const CACHE="' + manifest.cache + '"'))
    throw new Error('Local service-worker cache and signed release metadata do not match.');

const reportPathArg = process.argv.indexOf('--report');
const reportPath = reportPathArg === -1 ? null : process.argv[reportPathArg + 1];
if (reportPathArg !== -1 && (!reportPath || reportPath.startsWith('--')))
    throw new Error('Use --report path/to/hosted-integrity.json');

const timeoutMs = Number(process.env.PURSUIT_PAGES_VERIFY_TIMEOUT_MS || 360000);
if (!Number.isFinite(timeoutMs) || timeoutMs < 1000 || timeoutMs > 900000)
    throw new Error('PURSUIT_PAGES_VERIFY_TIMEOUT_MS is outside 1000..900000');
const deadline = Date.now() + timeoutMs;
const revision = process.env.GITHUB_SHA || 'local';
let attempt = 0;
let lastErrors = [];
let passed = false;
while (Date.now() < deadline) {
    attempt++;
    const observations = await Promise.all(files.map(async file => {
        const url = new URL(file, base);
        // Explicit revision probe bypasses CDN/browser caches, including prior
        // SW-controlled navigations. Compare *bytes*, not release-label strings.
        url.searchParams.set('__pursuit_integrity', expected[file].slice(0, 16));
        try {
            const response = await fetch(url, {
                cache: 'no-store', redirect: 'error',
                headers: { 'Cache-Control': 'no-cache' },
                signal: AbortSignal.timeout(15000)
            });
            if (!response.ok)
                return { file, ok: false, message: 'HTTP ' + response.status };
            const actual = sha(Buffer.from(await response.arrayBuffer()));
            return { file, ok: actual === expected[file],
                ...(actual === expected[file] ? {} : {
                    message: 'content hash differs',
                    expected: expected[file], actual
                }) };
        } catch (error) {
            return { file, ok: false, message: String(error?.message || error) };
        }
    }));
    lastErrors = observations.filter(result => !result.ok);
    if (!lastErrors.length) {
        passed = true;
        break;
    }
    console.log('HOSTED_WEBAPP_RETRY ' + JSON.stringify({
        attempt, remainingMs: Math.max(0, deadline - Date.now()),
        unmatchedFiles: lastErrors.map(result => result.file)
    }));
    if (Date.now() < deadline) await pause(Math.min(8000, Math.max(0, deadline - Date.now())));
}
const result = {
    schemaVersion: 1,
    status: passed ? 'pass' : 'fail',
    site: base.href,
    revision,
    cache: profile.cache,
    appVersion: manifest.appVersion,
    build: manifest.build,
    engineVersion: manifest.engineVersion,
    checkedAt: new Date().toISOString(),
    attempts: attempt,
    filesVerified: files.length - lastErrors.length,
    filesExpected: files.length,
    ...(lastErrors.length ? { errors: lastErrors } : {})
};
if (reportPath) {
    const filename = path.resolve(root, reportPath);
    fs.mkdirSync(path.dirname(filename), { recursive: true });
    fs.writeFileSync(filename, JSON.stringify(result, null, 2) + '\n');
}
console.log('HOSTED_WEBAPP_INTEGRITY ' + JSON.stringify(result));
if (!passed) process.exitCode = 1;
