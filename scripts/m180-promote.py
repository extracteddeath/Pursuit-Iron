from pathlib import Path
import json, hashlib


def read(p): return Path(p).read_text()
def write(p, s): Path(p).write_text(s)
def once(s, old, new, label):
    n = s.count(old)
    if n != 1:
        raise SystemExit(f'{label}: expected exactly 1 occurrence, found {n}')
    return s.replace(old, new, 1)

# App/build identity.
app = read('modules/App.js')
app = once(app,
    "const __APP_VERSION__='3.228.0'; const __BUILD__='784';",
    "const __APP_VERSION__='3.229.0'; const __BUILD__='785';",
    'app version/build')
write('modules/App.js', app)

index = read('index.html')
index = index.replace("build='784'", "build='785'").replace("build:'784'", "build:'785'")
if "build='784'" in index or "build:'784'" in index:
    raise SystemExit('stale build 784 remained in bootstrap diagnostics')
write('index.html', index)

# Engine identity.
config = read('modules/next-engine/config.js')
config = once(config,
    "export const ENGINE_VERSION = '0.63.2';",
    "export const ENGINE_VERSION = '0.63.3';",
    'engine version')
write('modules/next-engine/config.js', config)

# First-class offline/runtime packaging for the coach-quality oracle.
sw = read('sw.js')
sw = once(sw,
    '/* M178 real-browser PWA update lifecycle certification — Engine 0.63.2. */',
    '/* M180 coach-quality oracle + mixed strength recovery protection — Engine 0.63.3. */',
    'service-worker release comment')
sw = once(sw,
    'const CACHE="pursuit-iron-production-m178-pwa-update-lifecycle";',
    'const CACHE="pursuit-iron-production-m180-coach-quality-oracle";',
    'service-worker cache')
sw = once(sw,
    '  "./modules/next-engine/arm-coverage.js",',
    '  "./modules/next-engine/arm-coverage.js",\n  "./modules/next-engine/coach-quality-oracle.js",',
    'oracle precache entry')
write('sw.js', sw)

profile = json.loads(read('BUILD_PROFILE.json'))
profile['milestone'] = 'M180'
profile['source'] = 'M178 + domain-gated coach-quality oracle and mixed-plan lower-strength recovery protection'
profile['engine'] = '0.63.3 coach-quality oracle and mixed/powerbuilding lower-strength capacity protection'
profile['nextRuntimeModules'] = 44
profile['cache'] = 'pursuit-iron-production-m180-coach-quality-oracle'
write('BUILD_PROFILE.json', json.dumps(profile, indent=2) + '\n')

# Make M180 quality checks permanent members of the production verifier.
verify = read('scripts/verify-release.mjs')
anchor = "if (!fs.existsSync(path.join(root,'verification/m178-pwa-update-browser-test.mjs'))) fail('missing M178 PWA browser lifecycle gate');"
insert = anchor + "\nif (!fs.existsSync(path.join(root,'modules/next-engine/coach-quality-oracle.js'))) fail('missing M180 coach quality oracle');\nif (!fs.existsSync(path.join(root,'verification/m180-coach-quality-matrix.mjs'))) fail('missing M180 pass-only coach-quality matrix');"
verify = once(verify, anchor, insert, 'M180 verifier presence checks')
anchor2 = "execFileSync(process.execPath,['--no-warnings','--experimental-loader','./verification/import-loader.mjs','./verification/m177-history-merge-integrity-test.mjs'],{stdio:'inherit',cwd:root});"
insert2 = anchor2 + "\nexecFileSync(process.execPath,['--no-warnings','--experimental-loader','./verification/import-loader.mjs','./verification/m180-coach-quality-oracle-test.mjs'],{stdio:'inherit',cwd:root});\nexecFileSync(process.execPath,['--no-warnings','--experimental-loader','./verification/import-loader.mjs','./verification/m180-coach-quality-matrix.mjs'],{stdio:'inherit',cwd:root});"
verify = once(verify, anchor2, insert2, 'M180 verifier test hooks')
write('scripts/verify-release.mjs', verify)

changelog = read('CHANGELOG.md')
entry = '''## M180 — Coach Quality Oracle 2.0 (3.229.0 / build 785 / Engine 0.63.3)\n\n- Adds a domain-gated coach-quality oracle above the existing structural audit, objective coach guardrails, and sampled blind-review framework. No blended score can hide a major defect in another quality domain.\n- Makes a 19-case representative program matrix pass-only across experience levels, goals, frequencies, splits, time budgets, supersets, and equipment constraints. Any review/reject result blocks release verification.\n- Fixes a real powerbuilding recovery defect found by the new oracle: generic mixed Upper/Lower plans no longer spend optional high-priority volume by stacking squat and deadlift strength work when required anchors can occupy separate lower sessions. Required strength anchors remain protected, while named strength systems retain their own exposure rules.\n- Adds M180 oracle and matrix tests to the permanent production release verifier and packages the oracle as a hashed/pre-cached Next engine module.\n- Physical Android installed-PWA process-kill certification remains the separate M179 device gate and is not claimed here.\n\n'''
if not changelog.startswith('## M180 — Coach Quality Oracle 2.0'):
    changelog = entry + changelog
write('CHANGELOG.md', changelog)

# Refresh release identity and immutable hashes only after every runtime/UI mutation above.
manifest = json.loads(read('RELEASE_MANIFEST.json'))
manifest['milestone'] = 'M180'
manifest['appVersion'] = '3.229.0'
manifest['build'] = 785
manifest['engineVersion'] = '0.63.3'
manifest['candidateStatus'] = 'local_verified_device_test_pending'
manifest['localCandidate'] = {
    'name': 'M180 Coach Quality Oracle 2.0',
    'base': 'M178 / app 3.228.0 build 784 / Engine 0.63.2',
    'validation': 'Domain-gated oracle regressions, 19-case pass-only generated-program matrix, and the complete production release suite.'
}
manifest.setdefault('runtimeFiles', {})['modules/next-engine/coach-quality-oracle.js'] = ''

def sha_file(p): return hashlib.sha256(Path(p).read_bytes()).hexdigest()
for f in list(manifest.get('runtimeFiles', {})):
    manifest['runtimeFiles'][f] = sha_file(f)
agg = ''.join(f"{f}:{manifest['runtimeFiles'][f]}\n" for f in sorted(manifest.get('runtimeFiles', {})))
manifest['runtimeAggregate'] = hashlib.sha256(agg.encode()).hexdigest()
for f in list(manifest.get('uiFiles', {})):
    manifest['uiFiles'][f] = sha_file(f)
write('RELEASE_MANIFEST.json', json.dumps(manifest, indent=2) + '\n')
