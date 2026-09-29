from pathlib import Path
import json, hashlib

def read(p): return Path(p).read_text()
def write(p,s): Path(p).write_text(s)
def once(s, old, new, label):
    n=s.count(old)
    if n != 1:
        raise SystemExit(f'{label}: expected exactly 1 occurrence, found {n}')
    return s.replace(old,new,1)

app=read('modules/App.js')
app=once(app,"const __APP_VERSION__='3.227.0'; const __BUILD__='783';","const __APP_VERSION__='3.228.0'; const __BUILD__='784';",'app version/build')
write('modules/App.js',app)

index=read('index.html')
index=index.replace("build='783'","build='784'").replace("build:'783'","build:'784'")
if "build='783'" in index or "build:'783'" in index:
    raise SystemExit('stale build 783 remained in bootstrap diagnostics')
write('index.html',index)

sw=read('sw.js')
sw=once(sw,'/* M177 editable-history merge integrity — Engine 0.63.2. */','/* M178 real-browser PWA update lifecycle certification — Engine 0.63.2. */','sw release comment')
sw=once(sw,'const CACHE="pursuit-iron-production-m177-history-merge-integrity";','const CACHE="pursuit-iron-production-m178-pwa-update-lifecycle";','sw cache')
write('sw.js',sw)

profile=json.loads(read('BUILD_PROFILE.json'))
profile['milestone']='M178'
profile['source']='M177 + real-browser service-worker update/takeover/reload certification'
profile['cache']='pursuit-iron-production-m178-pwa-update-lifecycle'
write('BUILD_PROFILE.json',json.dumps(profile,indent=2)+'\n')

# Keep the heavyweight browser lifecycle gate separate from the fast Node release script, but make the
# production verifier require its test asset so the permanent CI workflow cannot silently lose it.
verify=read('scripts/verify-release.mjs')
anchor="const app = read('modules/App.js');"
insert="if (!fs.existsSync(path.join(root,'verification/m178-pwa-update-browser-test.mjs'))) fail('missing M178 PWA browser lifecycle gate');\n\n"+anchor
verify=once(verify,anchor,insert,'M178 browser gate presence check')
write('scripts/verify-release.mjs',verify)

changelog=read('CHANGELOG.md')
entry='''## M178 — Real-Browser PWA Update Lifecycle Certification (3.228.0 / build 784 / Engine 0.63.2)\n\n- Adds a real headless-Chrome service-worker lifecycle gate instead of relying only on source-marker tests for PWA updates.\n- The gate starts the actual app under its current worker, deploys a changed worker while the app is open, verifies the new worker waits, confirms the in-app **Update ready — Restart** action is the takeover trigger, and verifies the controller-driven reload completes under the new cache.\n- Verifies local training storage survives the update/reload and the old production cache is removed only after activation.\n- Re-audited interrupted-workout protection: the restart control remains suppressed while a workout is active or minimized, while `wpb:live` is flushed through the existing lifecycle persistence hooks. No runtime behavior change was needed.\n- Adds a permanent PWA lifecycle CI workflow so future releases run this browser-level gate in addition to the fast production integrity suite.\n\n'''
if not changelog.startswith('## M178 — Real-Browser PWA Update Lifecycle Certification'):
    changelog=entry+changelog
write('CHANGELOG.md',changelog)

report='''# M178 real-browser PWA update lifecycle certification\n\nApp **3.228.0**, build **784**, Pursuit Engine **0.63.2**, store schema **13**.\n\nM176 protected interrupted-workout restoration with deterministic Node/source tests. M178 closes the remaining PWA-update validation gap by exercising the actual service-worker lifecycle in Chrome.\n\n## Browser-certified sequence\n\n1. Launch the real app from localhost and wait for the production worker to control it.\n2. Preserve a local training-storage sentinel.\n3. Change the served worker to model a newly deployed release and force an update check.\n4. Assert the new worker remains `waiting` while the existing app continues running.\n5. Assert the app surfaces **Update ready — Restart**.\n6. Trigger Restart and require a real `controllerchange`-driven navigation.\n7. Assert the new release cache is active, the old production cache has been removed, local training storage survived, and the app completed its first React commit after reload.\n\n## Result\n\nThe current update implementation passed without a behavioral patch. The risk was missing integration coverage, not a demonstrated runtime defect. M178 therefore makes this a permanent CI contract instead of rewriting working service-worker logic.\n\nTrue Android launcher/process-kill behavior still requires a physical-device certification pass because headless Chrome cannot reproduce the OS killing an installed PWA process.\n'''
write('M178_REPORT.md',report)

workflow='''name: PWA lifecycle integrity\n\non:\n  push:\n    branches: [main]\n  pull_request:\n    branches: [main]\n  workflow_dispatch:\n\npermissions:\n  contents: read\n\njobs:\n  browser-pwa-lifecycle:\n    runs-on: ubuntu-latest\n    steps:\n      - uses: actions/checkout@v4\n      - uses: actions/setup-node@v4\n        with:\n          node-version: '22'\n      - name: Install browser test harness\n        shell: bash\n        run: |\n          set -euo pipefail\n          command -v google-chrome\n          npm install --no-save --ignore-scripts puppeteer-core@24\n      - name: Exercise service-worker update lifecycle in Chrome\n        env:\n          CHROME_BIN: /usr/bin/google-chrome\n        run: node verification/m178-pwa-update-browser-test.mjs\n'''
write('.github/workflows/pwa-lifecycle.yml',workflow)

manifest=json.loads(read('RELEASE_MANIFEST.json'))
manifest['milestone']='M178'
manifest['appVersion']='3.228.0'
manifest['build']=784
manifest['localCandidate']={
    'name':'M178 Real-Browser PWA Update Lifecycle Certification',
    'base':'M177 / app 3.227.0 build 783 / Engine 0.63.2',
    'validation':'Real Chrome service-worker takeover/reload/storage lifecycle plus the complete M177 production suite.'
}
def sha_file(p): return hashlib.sha256(Path(p).read_bytes()).hexdigest()
for f in list(manifest.get('runtimeFiles',{})):
    manifest['runtimeFiles'][f]=sha_file(f)
agg=''.join(f"{f}:{manifest['runtimeFiles'][f]}\n" for f in sorted(manifest.get('runtimeFiles',{})))
manifest['runtimeAggregate']=hashlib.sha256(agg.encode()).hexdigest()
for f in list(manifest.get('uiFiles',{})):
    manifest['uiFiles'][f]=sha_file(f)
write('RELEASE_MANIFEST.json',json.dumps(manifest,indent=2)+'\n')
