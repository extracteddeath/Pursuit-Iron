import { spawnSync } from 'node:child_process';
import { browserShards, verifyContractRegistry } from '../verification/contract-registry.mjs';

verifyContractRegistry();

const shard = process.argv[2];
const files = browserShards[shard];
if (!files)
    throw new Error(`Unknown browser verification shard: ${String(shard)}`);

const failures = [], timings = [];
for (const file of files) {
    console.log(`\n=== Browser contract: ${file} ===`);
    const started = process.hrtime.bigint();
    const result = spawnSync(process.execPath, [
        '--no-warnings',
        '--experimental-loader',
        './verification/import-loader.mjs',
        `verification/${file}`
    ], {
        cwd: process.cwd(),
        stdio: 'inherit',
        env: process.env
    });
    const seconds = Math.round(Number(process.hrtime.bigint() - started) / 1e7) / 100;
    timings.push({ file, seconds, passed: result.status === 0 });
    console.log(`BROWSER_TIMING ${JSON.stringify(timings.at(-1))}`);
    if (result.status !== 0)
        failures.push(file);
}

console.log(`BROWSER_SHARD_TIMING ${JSON.stringify({ shard, seconds: Math.round(timings.reduce((sum, row) => sum + row.seconds, 0) * 100) / 100 })}`);
if (failures.length)
    throw new Error(`Browser shard ${shard} failed: ${failures.join(', ')}`);

console.log(`PASS browser contracts: ${shard} (${files.length} gates).`);
