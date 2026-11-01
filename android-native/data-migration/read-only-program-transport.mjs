/**
 * Node validation of exact v13 bytes and SHA-256, delegating the actual
 * projection to the SAME browser-safe policy used by the Android JS bundle.
 * This does not authorize backup import, workout execution or writes.
 */
import { matchesCurrentReadOnlyProgram } from './read-only-program-reference.mjs';
import {
  projectCanonicalSavedProgram,
  PROGRAM_SUMMARY_SOURCE, PROGRAM_SUMMARY_PROTOCOL
} from '../engine-bridge/current-program-projection.mjs';

export { PROGRAM_SUMMARY_SOURCE, PROGRAM_SUMMARY_PROTOCOL };

export function projectReadOnlySavedProgram(rawSnapshot, currentReference) {
  if (!matchesCurrentReadOnlyProgram(rawSnapshot,currentReference))
    throw new TypeError('Current canonical program reference is unavailable');
  const envelope = projectCanonicalSavedProgram(
    rawSnapshot,currentReference.programId,currentReference.snapshotSha256
  );
  if (Object.keys(envelope.reference).some(
    key => envelope.reference[key] !== currentReference[key]
  )) throw new TypeError('Current canonical program reference is unavailable');
  return envelope;
}
