#!/usr/bin/env node
// Strict, read-only structural inspection of an explicitly supplied backup copy.
import fs from 'node:fs';
const file = process.argv[2];
if (!file || process.argv.length !== 3) {
    console.error('Usage: node android-native/data-migration/inspect-backup.mjs <backup-copy.json>');
    process.exitCode = 2;
} else {
    try {
        const stat = fs.statSync(file);
        if (!stat.isFile() || stat.size > 50 * 1024 * 1024)
            throw new Error('Input must be a regular JSON file no larger than 50 MiB');
        const root = JSON.parse(fs.readFileSync(file, 'utf8'));
        if (!root || typeof root !== 'object' || Array.isArray(root))
            throw new Error('Backup must be a JSON object');
        const fields = Object.keys(root).sort();
        const fieldShapes = Object.fromEntries(fields.map(key => {
            const v = root[key];
            return [key, Array.isArray(v) ? { kind:'array', count:v.length } :
                v === null ? {kind:'null'} : {kind:typeof v}];
        }));
        console.log(JSON.stringify({ inspectionOnly:true, schemaCertified:false, fieldShapes }, null, 2));
    } catch (error) {
        console.error('Backup inspection rejected:', error.message);
        process.exitCode = 1;
    }
}
