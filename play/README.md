# Google Play release kit

The planned Play package is a Trusted Web Activity (TWA) wrapping the live PWA at
`https://extracteddeath.github.io/Pursuit-Iron/`.

The repository currently contains the PWA and this release kit, not a built/signed Android App Bundle. Browser CI verifies the web app; it does not certify the wrapper, Play declarations or physical Android behavior. The current whole-app work queue is `NEXT_APP_UPDATE_PLAN.md`.

## 1. Digital Asset Links (removes the browser URL bar)

Android looks for `https://extracteddeath.github.io/.well-known/assetlinks.json`, at the **domain
root**. A GitHub Pages *project* site cannot serve that path, so the file must be committed to the
user-site repository **`extracteddeath/extracteddeath.github.io`** at `.well-known/assetlinks.json`,
with a `.nojekyll` file at that repository's root so the dot-folder is published.

1. Copy `assetlinks.template.json` there.
2. Set `package_name` to the app id (for example `io.github.extracteddeath.pursuitiron`).
3. Add the Play app-signing SHA-256 fingerprint from Play Console → Test and release → App integrity.
   Add the upload/local signing fingerprint only if that certificate also signs builds you distribute for testing.
   The upload key alone does not verify installs signed by Play. Never commit private keys or keystore passwords.
4. Check it: `https://digitalassetlinks.googleapis.com/v1/statements:list?source.web.site=https://extracteddeath.github.io&relation=delegate_permission/common.handle_all_urls`

## 2. Build the wrapper

Bubblewrap (`npx @bubblewrap/cli init --manifest https://extracteddeath.github.io/Pursuit-Iron/manifest.webmanifest`)
or PWABuilder. Confirm the final package id and use start URL `/Pursuit-Iron/`. Enable notification
delegation only for the notification features actually shipped and tested.

As checked October 21, 2026, new mobile apps and updates must target **Android 16 / API 36 or higher**
under the requirement effective August 31, 2026. Recheck the official policy before building:
[Target API level requirements](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en).
This is the wrapper's target SDK, not a setting in `manifest.webmanifest`. Build a signed `.aab` for Play,
retain signing material securely, and test the bundle installed through Play internal testing.

Verify domain association with the Play-distributed signing certificate, offline cold launch after the
first successful install, update/restart with an unfinished workout, Android Back, keyboard resize,
TalkBack, file share/cancellation, rest-timer recovery and notification permissions. A browser result
is not evidence that these passed in the wrapper.

[Android's TWA setup/signing guide](https://developer.android.com/develop/ui/views/layout/webapps/guide-trusted-web-activities-version2)
explains the domain-root association and why Play/local certificates may differ.

## 3. Store listing requirements

| Item | Value |
|---|---|
| Privacy policy URL | `https://extracteddeath.github.io/Pursuit-Iron/privacy.html` |
| Data safety | Draft for current web source: local-only processing, user-directed exports, no account/telemetry. Review the final wrapper, SDKs, hosting requests and current form definitions before submitting. |
| Health apps declaration | Declare the actual fitness/activity features. Complete the form even for testing tracks, and review the final listing/disclosures. |
| Content rating / target audience | Complete the questionnaire using the actual app and intended adult audience; do not assume an approved rating. |
| Ads | None. |

The [Health Content and Services policy](https://support.google.com/googleplay/android-developer/answer/16679511?hl=en)
requires a public privacy policy linked in-app and in Play Console, accurate health declarations and
appropriate health disclosures. Include this statement in the listing description for this non-medical
fitness app: “Pursuit Iron is not a medical device and does not diagnose, treat, cure, or prevent any medical condition.”
Remind users to consult a healthcare professional for medical advice, diagnosis or treatment. Review
in-app help/disclosures before submission, especially injury-related guidance; do not present fitness
recommendations as medical treatment. Remove permissions that the shipped functionality does not use.

Prepare phone screenshots and a feature graphic from the final UI. Confirm developer identity/contact,
privacy URL, support details, countries, content rating, target audience and any account-specific testing
requirements in Play Console. These items remain pending until completed against the final bundle;
this README is not a submitted declaration or an approval.

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
