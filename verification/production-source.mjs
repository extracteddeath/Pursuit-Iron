import fs from 'node:fs';
import { fileURLToPath } from 'node:url';
/** Source gates that span the UI/domain boundary inspect both maintained sources. */
export function productionSource() {
    const domain = new URL('../modules/training-domain/', import.meta.url);
    return fs.readFileSync(new URL('../modules/App.js', import.meta.url), 'utf8') + '\n'
        + fs.readdirSync(fileURLToPath(domain)).filter(f => f.endsWith('.js')).sort()
            .map(f => fs.readFileSync(new URL(f, domain), 'utf8')).join('\n');
}
