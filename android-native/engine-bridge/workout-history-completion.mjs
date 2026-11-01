/**
 * Environment-neutral canonical completion preparation. No file, network or store writes.
 * A finished private journal is not itself canonical history or permission to log.
 * The future native store must authenticate the original snapshot and atomically
 * CAS the returned candidate against expectedSnapshotSha256 before acknowledging.
 * Only the dedicated workout-store channel may expose this; the preview stays disconnected.
 */
import { sha256Utf8, utf8ByteLength, equalJson } from './json-identity.mjs';
import {
  parseStoredData, exerciseAt, prescribeSets, loggedWorkoutPerformance,
  EX_BY_ID, mergeStores, weeksOf
} from '../../modules/engine-api.js';
import { matchesCurrentReadOnlyProgram } from './canonical-snapshot-reference.mjs';
import { issuedNativeWorkoutFrom } from './native-workout.mjs';

const MAX_BYTES = 50 * 1024 * 1024;
const MAX_REV = Number.MAX_SAFE_INTEGER;
const MAX_DATE = 8_640_000_000_000_000;
const id = /^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$/;
const exerciseId = /^[A-Za-z0-9][A-Za-z0-9_-]{0,79}$/;
const load = /^(0|[1-9][0-9]{0,4})(\.[0-9]{1,3})?$/;
const object = v => v !== null && typeof v === 'object' && !Array.isArray(v);
const integer = (v, min, max) => Number.isSafeInteger(v) && v >= min && v <= max;
const own = (v, k) => Object.prototype.hasOwnProperty.call(v, k);
const sha = sha256Utf8;
const fail = () => { throw new TypeError('Canonical workout completion rejected'); };
const ensure = v => { if (!v) fail(); };
const exact = (v, keys) => object(v) &&
  Object.keys(v).length === keys.length && keys.every(k => own(v, k));
const validId = v => typeof v === 'string' && id.test(v);
const validSlot = v => exact(v, ['exerciseIndex','setIndex','exerciseId']) &&
  integer(v.exerciseIndex, 0, 47) && integer(v.setIndex, 0, 19) &&
  typeof v.exerciseId === 'string' && exerciseId.test(v.exerciseId);

function storeFrom(raw) {
  ensure(typeof raw === 'string' && utf8ByteLength(raw) <= MAX_BYTES);
  const source = JSON.parse(raw);
  ensure(object(source) && source.v === 13 &&
    integer(source.savedAt, 0, MAX_REV) && object(source.perf) &&
    object(source.tombs) && ['saved','history','cycles','custom','gyms']
      .every(k => Array.isArray(source[k])));
  return parseStoredData(raw);
}

function finishedJournal(raw) {
  ensure(typeof raw === 'string' && utf8ByteLength(raw) <= 256 * 1024);
  const j = JSON.parse(raw);
  ensure(exact(j, ['format','session','slots','startRevision','events','finish']) &&
    j.format === 2 && integer(j.startRevision, 0, MAX_REV));
  const s = j.session;
  ensure(exact(s, ['reference','workoutId','dayId','weekIndex','unit','startedAt']) &&
    validId(s.workoutId) && validId(s.dayId) && integer(s.weekIndex, 1, 104) &&
    ['lb','kg'].includes(s.unit) && integer(s.startedAt, 1, MAX_DATE));
  ensure(Array.isArray(j.slots) && j.slots.length >= 1 && j.slots.length <= 480 &&
    j.slots.every(validSlot) &&
    new Set(j.slots.map(v => `${v.exerciseIndex}:${v.setIndex}`)).size === j.slots.length);
  ensure(Array.isArray(j.events) && j.events.length === j.slots.length);
  const operations = new Set(), positions = new Set();
  for (const [i, event] of j.events.entries()) {
    ensure(exact(event, ['operationId','slot','disposition','values','fromRevision','toRevision']) &&
      validId(event.operationId) && !operations.has(event.operationId) &&
      validSlot(event.slot) &&
      integer(event.fromRevision, 0, MAX_REV - 1) &&
      event.fromRevision === j.startRevision + i && event.toRevision === event.fromRevision + 1 &&
      j.slots.some(slot => equalJson(slot, event.slot)));
    const position = `${event.slot.exerciseIndex}:${event.slot.setIndex}`;
    ensure(!positions.has(position));
    operations.add(event.operationId);
    positions.add(position);
    if (event.disposition === 'SKIPPED') ensure(event.values === null);
    else {
      const v = event.values;
      ensure(event.disposition === 'COMPLETED' && exact(v, ['load','unit','reps','rir']) &&
        integer(v.reps, 0, 100) && (v.rir === null || integer(v.rir, 0, 10)) &&
        ((v.load === null && v.unit === null) ||
          (typeof v.load === 'string' && load.test(v.load) && v.unit === s.unit)));
    }
  }
  const f = j.finish;
  ensure(exact(f, ['operationId','fromRevision','toRevision','completedAt','durationMs']) &&
    validId(f.operationId) && !operations.has(f.operationId) &&
    integer(f.fromRevision, 0, MAX_REV - 1) &&
    f.fromRevision === j.startRevision + j.events.length &&
    f.toRevision === f.fromRevision + 1 && integer(f.completedAt, s.startedAt, MAX_DATE) &&
    integer(f.durationMs, 0, f.completedAt - s.startedAt));
  return j;
}

function canonicalEntry(origin, journal, handoffSha256, issued = null) {
  const s = journal.session, f = journal.finish;
  const program = origin.saved.find(p => p.id === s.reference.programId);
  ensure(program && integer(weeksOf(program), 1, 104) && s.weekIndex <= weeksOf(program));
  const days = program.days.filter(d => d.id === s.dayId);
  ensure(days.length === 1 && days[0].exercises.length >= 1 && days[0].exercises.length <= 48);
  const day = days[0], slots = [];
  // Reuse the actual canonical runtime rows, including percentage-owned counts.
  // No Kotlin prescription, progression, summary or equipment formula lives here.
  const sources = issued?.rows || day.exercises.map((_, slot) => ({slot}));
  const rows = sources.map(source => {
    const exerciseIndex=source.slot;
    const currentId = exerciseAt(program, day, exerciseIndex, s.weekIndex);
    const ex = own(EX_BY_ID, currentId) ? EX_BY_ID[currentId] : null;
    ensure(ex); // Custom catalog installation is a separate, unverified feature.
    const prescribed = (issued ? source.sets : prescribeSets(program, day, ex, exerciseIndex, s.weekIndex,
      s.unit, null, {}, origin.perf, origin.history, false)).filter(row => !row.warm);
    ensure(prescribed.length >= 1 && prescribed.length <= 20);
    const sets = prescribed.map((target, setIndex) => {
      const slot = {exerciseIndex, setIndex, exerciseId: currentId};
      slots.push(slot);
      const event = journal.events.find(e => equalJson(e.slot, slot));
      ensure(event);
      if (event.disposition === 'SKIPPED') return {...target, done:false};
      const values = event.values;
      // Missing load is never silently changed to zero. Explicit zero is a
      // genuine user observation; negative assistance needs its own contract.
      ensure(values.load !== null && values.unit === s.unit);
      // The canonical logger only preserves zero-rep attempts for GZCLP.
      ensure(values.reps > 0 || program.config.percentScheme === 'gzclp' && Number(values.load) > 0);
      // Only the base bounds/count are re-resolved here. A retrospective
      // target calculation is NOT evidence of a suggestion actually shown
      // at workout start; never fabricate a historical decision trace.
      const {decisionTrace: _unissuedTrace, ...baseTarget} = target.target;
      const targetMetadata = issued ? target.target : baseTarget;
      return {...target, target:targetMetadata,
        weight:values.load, reps:String(values.reps), done:true,
        auto:false, valueOwner:'user', actualRIR:values.rir};
    });
    return {id:currentId, slot:exerciseIndex, sets};
  });
  ensure(equalJson(journal.slots, slots));
  const performance = loggedWorkoutPerformance(program, day, rows, s.weekIndex,
    s.unit, origin.perf, origin.history);
  // The canonical logger stamps per-exercise dates with Date.now. Use the
  // journal's durable finish time so retries are deterministic after a crash.
  for (const perf of Object.values(performance)) perf.date = f.completedAt;
  const observations = Object.values(performance).flatMap(p => p.sets);
  const completed = journal.events.filter(e => e.disposition === 'COMPLETED');
  if(issued) {
    // Sub-sets remain fully loggable but do not become new working-set
    // ordinals. The web history adapter owns how they influence volume.
    const isExtension=event=>!!issued.rows.find(row=>row.slot===event.slot.exerciseIndex)
      ?.sets.filter(set=>!set.warm)[event.slot.setIndex]?.sub;
    const completedMain=completed.filter(event=>!isExtension(event));
    const completedExtensions=completed.filter(isExtension);
    // The canonical summarizer deliberately omits zero-load rows when mixed
    // with positive loads. Do not rewrite its model; retain every original
    // observation separately and reject any other unexplained loss.
    const loggedMain=observations.filter(set=>!set.sub);
    const recorded=new Set(loggedMain.map(set=>`${set.decisionTrace?.slot}:${set.workingSetIndex}`));
    ensure(recorded.size===loggedMain.length && loggedMain.length<=completedMain.length);
    for(const event of completedMain) {
      if(recorded.has(`${event.slot.exerciseIndex}:${event.slot.setIndex}`))continue;
      ensure(Number(event.values.load)===0 && completed.some(other=>
        other.slot.exerciseId===event.slot.exerciseId && Number(other.values.load)>0));
    }
    // Extension kind, actual load and reps must survive the real canonical
    // logger exactly; never allow a silent omission masquerading as success.
    const signature=(kind,weight,reps)=>`${kind}:${Number(weight)}:${Number(reps)}`;
    const expected=completedExtensions.map(event=>{
      const row=issued.rows.find(row=>row.slot===event.slot.exerciseIndex);
      const target=row.sets.filter(set=>!set.warm)[event.slot.setIndex];
      return signature(target.kind,event.values.load,event.values.reps);
    }).sort();
    const persisted=observations.filter(set=>set.sub).map(set=>
      signature(set.kind,set.w,set.r)).sort();
    ensure(equalJson(persisted,expected));
  } else ensure(observations.length === completed.length);
  const volume = Math.round(observations.reduce((sum, row) => sum + row.w * row.r, 0));
  return {
    id:s.workoutId, date:f.completedAt, updatedAt:f.completedAt,
    programId:program.id, programName:program.name, dayId:day.id, dayLabel:day.label,
    engineV:program.engineV || 1, weekIndex:s.weekIndex, unit:s.unit,
    setsDone:completed.length, totalSets:slots.length, volume,
    durationMin:Math.max(1, Math.round(f.durationMs / 60000)), perf:performance,
    ...(issued ? {nativeSetObservations:{format:1,events:journal.events.map(event=>({
      slot:event.slot,disposition:event.disposition,values:event.values}))}} : {}),
    nativeCompletion:{format:2, handoffSha256,
      sourceSnapshotSha256:s.reference.snapshotSha256, journalRevision:f.toRevision}
  };
}

/**
 * Prepare a candidate only. The caller owns exact-origin authentication, the
 * active store, an atomic expected-hash write, and history-before-journal cleanup.
 * An identical already-published record is acknowledged without another merge.
 * Deleted/corrected/conflicting identities are NEVER overwritten or resurrected.
 */
export function prepareCanonicalWorkoutHistory(originRaw, currentRaw, handoffRaw, issuedRaw = null) {
  try {
    const journal = finishedJournal(handoffRaw);
    ensure(matchesCurrentReadOnlyProgram(originRaw, journal.session.reference));
    const origin = storeFrom(originRaw), current = storeFrom(currentRaw);
    const issued = issuedRaw===null ? null : issuedNativeWorkoutFrom(originRaw,issuedRaw);
    if(issued) ensure(equalJson(issued.plan.session,journal.session) && equalJson(issued.slots,journal.slots));
    // JSON whitespace/property order must not change idempotency identity.
    const identity=issued ? {journal,issuedWorkoutSha256:sha(issuedRaw)} : journal;
    const semanticHandoff = JSON.stringify(identity, (_key, value) => object(value)
      ? Object.fromEntries(Object.keys(value).sort().map(key => [key, value[key]])) : value);
    // Canonical helpers use optional undefined fields. Compare exactly what
    // survives a real JSON store round-trip, rather than in-memory optionals.
    const entry = JSON.parse(JSON.stringify(canonicalEntry(origin, journal, sha(semanticHandoff),issued?.plan)));
    const existing = current.history.filter(h => h.id === entry.id);
    ensure(!own(current.tombs, entry.id));
    let candidateRaw;
    if (existing.length) {
      ensure(existing.length === 1 && equalJson(existing[0], entry));
      candidateRaw = currentRaw;
    } else {
      // Pending completion never silently rebases onto a changed/deleted plan
      // or other concurrent store edit. A later conflict UI can resolve it.
      ensure(matchesCurrentReadOnlyProgram(currentRaw, journal.session.reference) &&
        !current.saved.some(p => p.id === entry.id) &&
        !current.cycles.some(c => c.id === entry.id) &&
        !current.custom.some(c => c.id === entry.id) &&
        !current.gyms.some(g => g.id === entry.id));
      // One-shot deload consumption is app-owned today. Do not publish a
      // partial transaction that silently leaves it armed for the next workout.
      ensure(!Object.keys(origin.saved.find(p => p.id === entry.programId).deloadNext || {}).length);
      const next = mergeStores(current, {...current,
        savedAt:Math.max(current.savedAt, entry.updatedAt), history:[entry]}).data;
      // Canonical history limits must not silently discard old workouts here.
      ensure(next.history.length === current.history.length + 1 &&
        next.history.some(h => equalJson(h, entry)) &&
        current.history.every(h => next.history.some(n => equalJson(n, h))));
      candidateRaw = JSON.stringify(next);
      storeFrom(candidateRaw); // authoritative v13 round-trip before any write
    }
    return Object.freeze({
      source:'modules/engine-api.js:loggedWorkoutPerformance+mergeStores',
      preparedOnly:true, persisted:false, approvedForWorkoutExecution:false,
      sourceSnapshotSha256:sha(originRaw), inputHandoffSha256:sha(handoffRaw),
      expectedSnapshotSha256:sha(currentRaw),
      candidateSnapshotSha256:sha(candidateRaw), candidateSnapshot:candidateRaw,
      alreadyPresent:existing.length === 1, historyEntry:entry
    });
  } catch {
    // Canonical parser errors can contain raw backup bytes. Never forward them.
    fail();
  }
}
