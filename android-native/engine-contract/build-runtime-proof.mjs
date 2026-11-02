/** Disposable source/JVM runtime proof. This script never invokes an APK task. */
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';

const output=path.resolve(process.argv[2]??'');
assert.ok(process.argv.length===3 && output!==process.cwd(),'An empty output directory is required');
fs.mkdirSync(output,{recursive:true});
assert.equal(fs.readdirSync(output).length,0,'Do not replace a previous proof or project files');
const run=(command,args)=>{
  const result=spawnSync(command,args,{stdio:'inherit',env:process.env});
  if (result.error) throw result.error;
  assert.equal(result.status,0,'Canonical runtime proof command failed');
};
const node=process.execPath,snapshot=path.join(output,'snapshot');
run(node,['engine-lab/canonical-export.mjs',snapshot]);
run(node,[path.join(snapshot,'scripts/verify-snapshot.mjs')]);
run(node,['engine-lab/verify-parity.mjs',snapshot]);
run(node,['android-native/engine-contract/snapshot-identity.mjs',snapshot]);
run(node,['android-native/engine-bridge/stage-bundle-sources.mjs',snapshot]);
const bundle=path.join(output,'canonical-engine.js'),metadata=path.join(output,'metafile.json');
run('npx',['--yes','esbuild@0.28.2',path.join(snapshot,'android-native/engine-bridge/android-entry.mjs'),
  '--bundle','--format=iife','--platform=browser','--target=es2020',
  '--global-name=PursuitIronBridge','--legal-comments=none',`--metafile=${metadata}`,`--outfile=${bundle}`]);
run(node,['--check',bundle]);
run(node,['android-native/engine-bridge/verify-bundle-structure.mjs',bundle,metadata,snapshot]);
run(node,['android-native/engine-bridge/native-history-test.mjs',bundle]);
run(node,['android-native/engine-bridge/native-history-observation-test.mjs',bundle]);
run(node,['android-native/engine-bridge/native-failed-attempt-test.mjs',bundle]);
run(node,['android-native/engine-contract/create-bundle-manifest.mjs',snapshot,bundle,
  path.join(output,'canonical-engine-manifest.json')]);
