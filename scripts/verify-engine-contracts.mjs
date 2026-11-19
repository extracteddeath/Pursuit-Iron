import { execFileSync } from 'node:child_process';
import { contractGroups, verifyContractRegistry } from '../verification/contract-registry.mjs';

const registry = verifyContractRegistry();
const requested = process.argv.slice(2);
const groups = requested.length ? requested : Object.keys(contractGroups);
const failures = [];
for (const group of groups) {
    const files = contractGroups[group];
    if (!files) throw new Error(`Unknown contract group: ${group}`);
    const groupFailures = [];
    for (const file of files) {
        const started = process.hrtime.bigint();
        let passed = false;
        try {
            execFileSync(process.execPath, [
                '--no-warnings',
                '--experimental-loader',
                './verification/import-loader.mjs',
                `verification/${file}`
            ], { stdio: 'inherit' });
            passed = true;
        } catch (error) {
            const issue = { group, file, status: error.status ?? null };
            groupFailures.push(file);
            failures.push(issue);
            console.error('CONTRACT_FAILURE ' + JSON.stringify(issue));
        } finally {
            const seconds = Math.round(Number(process.hrtime.bigint() - started) / 1e7) / 100;
            console.log(`CONTRACT_TIMING ${JSON.stringify({ group, file, seconds, passed })}`);
        }
    }
    if (!groupFailures.length)
        console.log(`PASS current contracts: ${group} (${files.length} gates).`);
    else console.error(`FAIL current contracts: ${group} (${groupFailures.length}/${files.length} failed): ${groupFailures.join(', ')}`);
}
if (failures.length)
    throw new Error(`Contract verification failed (${failures.length}): ${failures.map(item => item.group + '/' + item.file).join(', ')}`);
console.log(
    `PASS verification registry: ${registry.ownedCount} executable gates; `
    + `${registry.contractCount} source contracts, ${registry.browserCount} browser contracts, `
    + `${registry.releaseCount} release-specific contract.`
);
