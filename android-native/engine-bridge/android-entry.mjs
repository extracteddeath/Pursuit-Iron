// Android-only startup: canonical JS engine and the web app remain untouched.
import { installCanonicalSandboxCompat } from './sandbox-compat.mjs';
import { dispatchEngineJson, dispatchEngineRequest, PROTOCOL_VERSION } from './protocol.mjs';
installCanonicalSandboxCompat();
export { dispatchEngineJson, dispatchEngineRequest, PROTOCOL_VERSION };
