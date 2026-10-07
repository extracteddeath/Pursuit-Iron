import { spawnSync } from 'node:child_process';
import { browserShards, verifyContractRegistry } from '../verification/contract-registry.mjs';

verifyContractRegistry();

const shard = process.argv[2];
const files = browserShards[shard];
if (!files)
    throw new Error(`Unknown browser verification shard: ${String(shard)}`);

const failures = [];
for (const file of files) {
    console.log(`\n=== Browser contract: ${file} ===`);
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
    if (result.status !== 0)
        failures.push(file);
}

if (failures.length)
    throw new Error(`Browser shard ${shard} failed: ${failures.join(', ')}`);

console.log(`PASS browser contracts: ${shard} (${files.length} gates).`);
