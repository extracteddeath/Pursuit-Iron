# Google Play release kit

Pursuit Iron ships to Play as a Trusted Web Activity (TWA) wrapping the live PWA at
`https://extracteddeath.github.io/Pursuit-Iron/`.

## 1. Digital Asset Links (removes the browser URL bar)

Android looks for `https://extracteddeath.github.io/.well-known/assetlinks.json`, at the **domain
root**. A GitHub Pages *project* site cannot serve that path, so the file must be committed to the
user-site repository **`extracteddeath/extracteddeath.github.io`** at `.well-known/assetlinks.json`,
with a `.nojekyll` file at that repository's root so the dot-folder is published.

1. Copy `assetlinks.template.json` there.
2. Set `package_name` to the app id (for example `io.github.extracteddeath.pursuitiron`).
3. Add both SHA-256 fingerprints: Play Console → Test and release → App integrity → App signing
   key, plus your upload key. Without the Play signing key, installs from Play show the URL bar.
4. Check it: `https://digitalassetlinks.googleapis.com/v1/statements:list?source.web.site=https://extracteddeath.github.io&relation=delegate_permission/common.handle_all_urls`

## 2. Build the wrapper

Bubblewrap (`npx @bubblewrap/cli init --manifest https://extracteddeath.github.io/Pursuit-Iron/manifest.webmanifest`)
or PWABuilder. Use start URL `/Pursuit-Iron/`, enable notification delegation, and target the current
Play API level.

## 3. Store listing requirements

| Item | Value |
|---|---|
| Privacy policy URL | `https://extracteddeath.github.io/Pursuit-Iron/privacy.html` |
| Data safety | No data collected or shared; no account; data stays on device (matches the policy). |
| Health apps declaration | Fitness tracking; not a medical device. |
| Content rating | Everyone / no user-generated content shared in-app. |
| Ads | None. |

## 4. When accounts (Firebase) are added

* Play requires **in-app account deletion** *and* a **web link** to request deletion for any app that
  lets users create an account. Both must delete the cloud copy, not just sign out.
* Update `privacy.html` **before** release: what is stored (the synced store), where (region), retention,
  and how deletion works. Update the Data safety form to "collected, not shared, encrypted in transit,
  deletable".
* Sync goes through `syncStoreOnce(local, provider)` in `modules/training-domain/records.js`. A Firebase
  provider only implements `pull()` / `push(data, { baseRevision })` with a transaction or
  generation-matched write. Split the store across documents: history can exceed Firestore's 1 MiB
  document limit.
* For native Google Sign-In, Health Connect and fully reliable background notifications, consider moving
  the wrapper from TWA to Capacitor. `App.js` already detects a Capacitor shell (`isNativeApp`) and uses
  its LocalNotifications plugin when present.
