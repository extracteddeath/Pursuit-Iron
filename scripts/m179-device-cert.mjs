#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import readline from 'node:readline/promises';

const root = process.cwd();
const templatePath = path.join(root, 'verification', 'M179_DEVICE_QA_TEMPLATE.json');
const manifestPath = path.join(root, 'RELEASE_MANIFEST.json');
const defaultResultsPath = path.join(root, 'verification', 'M179_DEVICE_QA_RESULTS.json');

function readJson(file) {
  return JSON.parse(fs.readFileSync(file, 'utf8'));
}

function writeJson(file, value) {
  fs.writeFileSync(file, `${JSON.stringify(value, null, 2)}\n`);
}

function currentTarget() {
  const manifest = readJson(manifestPath);
  return {
    milestone: manifest.milestone,
    appVersion: manifest.appVersion,
    build: manifest.build,
    engineVersion: manifest.engineVersion,
    runtimeAggregate: manifest.runtimeAggregate
  };
}

function makeSkeleton() {
  const template = readJson(templatePath);
  return {
    schemaVersion: template.schemaVersion,
    certificationMilestone: template.milestone,
    createdAt: new Date().toISOString(),
    completedAt: null,
    device: {
      manufacturer: '',
      model: '',
      androidVersion: '',
      browserProvider: '',
      notes: ''
    },
    target: currentTarget(),
    scenarios: template.scenarios.map(scenario => ({
      id: scenario.id,
      name: scenario.name,
      required: scenario.required,
      result: 'pending',
      evidence: '',
      observedAt: null
    }))
  };
}

function validateResults(results) {
  const template = readJson(templatePath);
  const target = currentTarget();
  const issues = [];
  const minEvidence = template.requirements.minimumEvidenceCharacters ?? 12;
  if (results.certificationMilestone !== template.milestone)
    issues.push(`milestone mismatch: ${results.certificationMilestone} != ${template.milestone}`);
  for (const key of ['milestone', 'appVersion', 'build', 'engineVersion', 'runtimeAggregate']) {
    if (results.target?.[key] !== target[key])
      issues.push(`target ${key} mismatch: tested ${results.target?.[key]} != current ${target[key]}`);
  }
  for (const field of ['manufacturer', 'model', 'androidVersion']) {
    if (!String(results.device?.[field] ?? '').trim())
      issues.push(`device.${field} is required`);
  }
  const byId = new Map((results.scenarios ?? []).map(item => [item.id, item]));
  for (const scenario of template.scenarios) {
    const result = byId.get(scenario.id);
    if (!result) {
      issues.push(`${scenario.id}: result missing`);
      continue;
    }
    if (!['pending', 'pass', 'fail', 'blocked'].includes(result.result))
      issues.push(`${scenario.id}: invalid result ${result.result}`);
    if (result.result !== 'pending' && String(result.evidence ?? '').trim().length < minEvidence)
      issues.push(`${scenario.id}: evidence must be at least ${minEvidence} characters`);
    if (scenario.required && result.result !== 'pass')
      issues.push(`${scenario.id}: required scenario is ${result.result}, not pass`);
  }
  return issues;
}

function certificationSummary(results) {
  const template = readJson(templatePath);
  const byId = new Map((results.scenarios ?? []).map(item => [item.id, item]));
  const counts = { pass: 0, fail: 0, blocked: 0, pending: 0 };
  for (const scenario of template.scenarios) {
    const value = byId.get(scenario.id)?.result ?? 'pending';
    counts[value] = (counts[value] ?? 0) + 1;
  }
  const issues = validateResults(results);
  return {
    counts,
    certified: issues.length === 0,
    issues
  };
}

function printScenario(scenario) {
  console.log(`\n[${scenario.id}] ${scenario.name}`);
  for (const line of scenario.instructions)
    console.log(`  - ${line}`);
  console.log(`Expected: ${scenario.expected}\n`);
}

async function runInteractive(resultsPath) {
  const template = readJson(templatePath);
  const results = fs.existsSync(resultsPath) ? readJson(resultsPath) : makeSkeleton();
  const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
  try {
    const fields = [
      ['manufacturer', 'Device manufacturer'],
      ['model', 'Device model'],
      ['androidVersion', 'Android version'],
      ['browserProvider', 'PWA/browser provider (optional)']
    ];
    for (const [key, label] of fields) {
      if (results.device[key])
        continue;
      results.device[key] = (await rl.question(`${label}: `)).trim();
      writeJson(resultsPath, results);
    }
    for (const scenario of template.scenarios) {
      const record = results.scenarios.find(item => item.id === scenario.id);
      if (record?.result === 'pass')
        continue;
      printScenario(scenario);
      let result;
      while (!['pass', 'fail', 'blocked', 'skip'].includes(result))
        result = (await rl.question('Result [pass/fail/blocked/skip]: ')).trim().toLowerCase();
      if (result === 'skip')
        continue;
      let evidence = '';
      while (evidence.length < (template.requirements.minimumEvidenceCharacters ?? 12))
        evidence = (await rl.question('Evidence / what you observed: ')).trim();
      record.result = result;
      record.evidence = evidence;
      record.observedAt = new Date().toISOString();
      writeJson(resultsPath, results);
      if (result === 'fail') {
        console.log('Failure recorded. Stop certification and fix/retest before claiming M179 pass.');
        break;
      }
    }
  } finally {
    rl.close();
  }
  const summary = certificationSummary(results);
  if (summary.certified) {
    results.completedAt = new Date().toISOString();
    writeJson(resultsPath, results);
    console.log(`M179 DEVICE CERTIFIED: ${summary.counts.pass} required scenarios passed.`);
  } else {
    console.log(`M179 not yet certified: ${summary.counts.pass} pass, ${summary.counts.fail} fail, ${summary.counts.blocked} blocked, ${summary.counts.pending} pending.`);
  }
}

function report(resultsPath) {
  const results = readJson(resultsPath);
  const summary = certificationSummary(results);
  console.log(JSON.stringify({ target: results.target, device: results.device, ...summary }, null, 2));
  process.exitCode = summary.certified ? 0 : 1;
}

const [command = 'help', fileArg] = process.argv.slice(2);
const resultsPath = path.resolve(root, fileArg || defaultResultsPath);

if (command === 'init') {
  if (fs.existsSync(resultsPath)) {
    console.error(`Refusing to overwrite existing results: ${resultsPath}`);
    process.exit(2);
  }
  writeJson(resultsPath, makeSkeleton());
  console.log(`Created ${resultsPath}`);
} else if (command === 'run') {
  await runInteractive(resultsPath);
} else if (command === 'validate' || command === 'report') {
  if (!fs.existsSync(resultsPath)) {
    console.error(`Results file not found: ${resultsPath}`);
    process.exit(2);
  }
  report(resultsPath);
} else {
  console.log('M179 Android device certification');
  console.log('  node scripts/m179-device-cert.mjs init [results.json]');
  console.log('  node scripts/m179-device-cert.mjs run [results.json]');
  console.log('  node scripts/m179-device-cert.mjs validate [results.json]');
}
