import { execFileSync } from 'node:child_process';
import { contractGroups, verifyContractRegistry } from '../verification/contract-registry.mjs';

const registry = verifyContractRegistry();
const requested = process.argv.slice(2);
const groups = requested.length ? requested : Object.keys(contractGroups);

for (const group of groups) {
    const files = contractGroups[group];
    if (!files)
        throw new Error(`Unknown contract group: ${group}`);
    for (const file of files) {
        const started = process.hrtime.bigint();
        execFileSync(process.execPath, [
            '--no-warnings',
            '--experimental-loader',
            './verification/import-loader.mjs',
            `verification/${file}`
        ], { stdio: 'inherit' });
        const seconds = Math.round(Number(process.hrtime.bigint() - started) / 1e7) / 100;
        console.log(`CONTRACT_TIMING ${JSON.stringify({ group, file, seconds })}`);
    }
    console.log(`PASS current contracts: ${group} (${files.length} gates).`);
}

console.log(
    `PASS verification registry: ${registry.ownedCount} executable gates; `
    + `${registry.contractCount} source contracts, ${registry.browserCount} browser contracts, `
    + `${registry.releaseCount} release-specific contract.`
);
