import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

// Audit Gradle's resolved :app releaseRuntimeClasspath, not just declared
// project dependencies. Keep this independent of the historical JS module.
const forbiddenProjects = [
  'engine-runtime','engine-bridge','legacy-engine','web-engine'
];
const forbiddenModules = [
  'androidx.javascriptengine:javascriptengine',
  'org.mozilla:rhino',
  'org.graalvm.js:js',
  'org.graalvm.polyglot:js',
  'com.eclipsesource.j2v8:j2v8',
  'com.squareup.duktape:duktape-android'
];
function audit(report) {
  assert.match(report,/releaseRuntimeClasspath/,
    'Missing resolved releaseRuntimeClasspath report');
  assert.ok(!/No dependencies|FAILED to resolve/i.test(report),
    'Release dependency resolution is empty or failed');
  assert.ok(report.split(/\r?\n/).some(line=>
    /(?:\+---|\\---)\s+(?:project :|[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+:)/.test(line)),
    'Resolved release classpath must contain actual Gradle dependency entries');
  const lines=report.split(/\r?\n/);
  const hits=[];
  for(const line of lines) {
    // Gradle trees use +--- / \\--- and nested | +--- connectors.
    // Match module identities at the beginning of dependency entries only,
    // avoiding unrelated explanatory prose.
    const entry=line.match(/(?:\+---|\\---)\s+(.+)$/)?.[1];
    if(!entry)continue;
    const identity=entry.trim();
    for(const project of forbiddenProjects) {
      if(new RegExp('^project :'+project+'(?:\\s|$)').test(identity))
        hits.push(identity);
    }
    for(const module of forbiddenModules) {
      if(identity===module || identity.startsWith(module+':'))
        hits.push(identity);
    }
  }
  return [...new Set(hits)];
}
function selfTest() {
  const good='releaseRuntimeClasspath - Runtime classpath of release.\n+--- project :kotlin-engine\n\\--- project :native-coach-store\n';
  assert.deepEqual(audit(good),[]);
  for(const item of [
    'project :engine-runtime',
    'project :engine-bridge',
    'androidx.javascriptengine:javascriptengine:1.1.1',
    'org.mozilla:rhino:1.7',
    'org.graalvm.js:js:24.0',
    'com.eclipsesource.j2v8:j2v8:1.0',
    'org.graalvm.polyglot:js:24.0',
    'com.squareup.duktape:duktape-android:1.0'
  ]) {
    assert.ok(audit(good+'|    \\--- '+item+'\n').length,
      'Missed forbidden runtime dependency '+item);
  }
  assert.throws(()=>audit('releaseRuntimeClasspath\nNo dependencies'));
  assert.throws(()=>audit('releaseRuntimeClasspath - Runtime classpath of release.\n'));
  assert.throws(()=>audit('releaseRuntimeClasspath\n+--- project :kotlin-engine FAILED to resolve'));
  console.log('PASS resolved runtime dependency auditor self-tests');
}
if(process.argv.includes('--self-test')) {
  selfTest();
} else {
  const file=process.argv[2];
  if(!file)throw new Error('Usage: node audit-native-beta-release-dependencies.mjs <gradle-report>');
  const report=readFileSync(file,'utf8');
  const violations=audit(report);
  if(violations.length) {
    console.error('BLOCKED: JavaScript runtime on native beta release classpath:\n'+violations.join('\n'));
    process.exitCode=1;
  } else {
    console.log('PASS: no forbidden legacy JavaScript runtime in resolved beta release classpath');
  }
}
