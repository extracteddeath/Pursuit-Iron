#!/usr/bin/env node
// Non-destructive certification preview: reuse canonical web parser, never copy its logic.
import fs from 'node:fs';
import { parseStoredData, STORE_VERSION } from '../../modules/engine-api.js';
const file = process.argv[2];
if (!file || process.argv.length !== 3) {
    console.error('Usage: node android-native/data-migration/validate-canonical-backup.mjs <backup-copy.json>');
    process.exitCode = 2;
} else {
    try {
        const stat = fs.statSync(file);
        if (!stat.isFile() || stat.size > 50 * 1024 * 1024)
            throw new Error('Input must be a regular JSON file no larger than 50 MiB');
        const raw = fs.readFileSync(file, 'utf8');
        const parsed = JSON.parse(raw);
        if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed))
            throw new Error('Backup must be an object');
        const version = parsed.v;
        const validated = parseStoredData(raw);
        const counts = Object.fromEntries(['history','saved','cycles','custom','gyms','bwLog']
            .map(k => [k, Array.isArray(validated[k]) ? validated[k].length : 0]));
        console.log(JSON.stringify({
            validation:'canonical-web-parser-only',
            imported:false,
            writePerformed:false,
            sourceVersion:typeof version === 'number' ? version : null,
            migratedVersion:validated.v,
            canonicalVersion:STORE_VERSION,
            counts
        }, null, 2));
    } catch {
        // Deliberately suppress raw parser messages, which could contain user values.
        console.error('Canonical backup validation rejected the supplied copy.');
        process.exitCode = 1;
    }
}
