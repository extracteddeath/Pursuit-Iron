// DEBUG-ONLY Android instrumentation helper. Never ship in native app assets.
// Accepts only the local synthetic fixed fixture input, no data writes.
import { canonicalProgressionPreview, validProgressionPreviewParams } from './progression-preview.mjs';
export function diagnoseProgressionJson(json) {
  try {
    if (typeof json !== 'string' || json.length > 16384)
      return JSON.stringify({state:'INVALID_DIAGNOSTIC_INPUT'});
    const input = JSON.parse(json);
    if (!validProgressionPreviewParams(input))
      return JSON.stringify({state:'SCHEMA_REJECTED'});
    const result = canonicalProgressionPreview(input);
    return JSON.stringify({state:'OK',action:result.action});
  } catch (e) {
    const name = String(e?.name ?? 'Error').slice(0, 64);
    // No personal inputs. Diagnostic artifacts never ship to users.
    const message = String(e?.message ?? 'No message').slice(0, 400);
    return JSON.stringify({state:'ENGINE_EXCEPTION', name, message});
  }
}
