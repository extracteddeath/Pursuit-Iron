import { comparePrograms } from './comparison.js';
function prescription(sessionExercise) {
    const p = sessionExercise.prescription;
    return `${sessionExercise.sets} × ${p.reps[0]}–${p.reps[1]} @ ${p.rir[0]}–${p.rir[1]} RIR, ${p.restSeconds}s`;
}
function summarize(label, program) {
    return {
        label, split: program.split.displayName,
        sessions: program.sessions.map(session => ({
            day: session.day, name: session.name, estimatedMinutes: session.estimatedMinutes,
            exercises: session.exercises.map(ex => ({ name: ex.name, role: ex.role, prescription: prescription(ex) }))
        }))
    };
}
function prioritySummary(request) {
    const out = [];
    for (const [muscle, priority] of Object.entries(request.goal.musclePriorities))
        if (priority === 'high' || priority === 'specialization' || priority === 'primary')
            out.push(`${muscle}: ${priority}`);
    for (const [lift, priority] of Object.entries(request.goal.liftPriorities))
        if (priority === 'high' || priority === 'primary')
            out.push(`${lift}: ${priority}`);
    return out;
}
function deterministicSwap(id, seed) {
    let hash = seed | 0;
    for (const char of id)
        hash = ((hash << 5) - hash + char.charCodeAt(0)) | 0;
    return Math.abs(hash) % 2 === 1;
}
export function createBlindCoachReviewPacket(id, baseline, candidate, request) {
    const swap = deterministicSwap(id, request.seed);
    const a = swap ? candidate : baseline;
    const b = swap ? baseline : candidate;
    return {
        id,
        profile: {
            goal: request.goal.type,
            experience: request.athlete.experience,
            days: request.schedule.days.length,
            timeCaps: request.schedule.days.map(day => `${day.day}:${day.maxMinutes}m`).join(', '),
            priorities: prioritySummary(request)
        },
        programA: summarize('A', a),
        programB: summarize('B', b),
        checklist: [
            'Would you prescribe this program as written?',
            'Does every session have a coherent purpose?',
            'Are the athlete’s stated priorities visibly protected?',
            'Is there avoidable exercise or movement redundancy?',
            'Does the weekly sequence manage fatigue and recovery plausibly?',
            'Are session-duration estimates believable within the stated caps?',
            'Are set/rep/RIR/rest prescriptions executable and role-appropriate?',
            'Could meaningful work be removed without reducing goal fulfillment?'
        ],
        reviewKey: swap ? { A: 'candidate', B: 'baseline' } : { A: 'baseline', B: 'candidate' }
    };
}
export function unblindCoachReview(record, packet, baseline, candidate, request) {
    const baselineAssessment = packet.reviewKey.A === 'baseline' ? record.programA : record.programB;
    const candidateAssessment = packet.reviewKey.A === 'candidate' ? record.programA : record.programB;
    const preferredOrigin = record.preference === 'A' ? packet.reviewKey.A : record.preference === 'B' ? packet.reviewKey.B : undefined;
    const candidatePreference = record.preference === 'equivalent' ? 'equivalent' : record.preference === 'mixed' ? 'mixed' : preferredOrigin === 'candidate' ? 'preferred' : 'not_preferred';
    return { baseline: baselineAssessment, candidate: candidateAssessment, candidatePreference, semantic: comparePrograms(baseline, candidate, request) };
}
function summarizeReference(label, reference) {
    return { label, split: reference.split, sessions: reference.sessions.map(session => ({ ...session, exercises: session.exercises.map(ex => ({ ...ex })) })) };
}
function threeWayOrder(id, seed) {
    const values = ['legacy_v661', 'candidate', 'coach_reference'];
    let hash = seed | 0;
    for (const char of id)
        hash = ((hash << 5) - hash + char.charCodeAt(0)) | 0;
    // Deterministic Fisher-Yates: no randomness, reproducible review packets.
    for (let i = values.length - 1; i > 0; i--) {
        hash = (hash * 1664525 + 1013904223) | 0;
        const j = Math.abs(hash) % (i + 1);
        [values[i], values[j]] = [values[j], values[i]];
    }
    return values;
}
export function createBlindThreeWayCoachReviewPacket(id, legacy, candidate, reference, request) {
    const order = threeWayOrder(id, request.seed);
    const labels = ['A', 'B', 'C'];
    const programFor = (origin, label) => origin === 'legacy_v661'
        ? summarize(label, legacy)
        : origin === 'candidate'
            ? summarize(label, candidate)
            : summarizeReference(label, reference);
    const programs = labels.map((label, index) => programFor(order[index], label));
    return {
        id,
        profile: {
            goal: request.goal.type,
            experience: request.athlete.experience,
            days: request.schedule.days.length,
            timeCaps: request.schedule.days.map(day => `${day.day}:${day.maxMinutes}m`).join(', '),
            priorities: prioritySummary(request)
        },
        programs,
        checklist: [
            'Would you prescribe each program as written?',
            'Does every session have a coherent primary emphasis without becoming artificially rigid?',
            'Are low-interference accessories placed where they best support performance and session balance?',
            'Are the athlete’s stated priorities visibly protected?',
            'Is there avoidable exercise or movement redundancy?',
            'Does the weekly sequence manage local and systemic fatigue plausibly?',
            'Are set/rep/RIR/rest prescriptions executable and role-appropriate?',
            'Could meaningful work be removed, moved, or consolidated without reducing goal fulfillment?'
        ],
        reviewKey: { A: order[0], B: order[1], C: order[2] }
    };
}
export const THREE_WAY_COACH_REVIEW_DIMENSIONS = [
    'session_coherence',
    'exercise_selection',
    'priority_fulfillment',
    'fatigue_management',
    'prescription_quality',
    'exercise_economy',
    'split_identity'
];
export const COACH_ISSUE_CATEGORIES = [
    'redundant_work',
    'poor_sequence',
    'fatigue_risk',
    'underdosed',
    'overdosed',
    'misplaced_exercise',
    'split_identity',
    'prescription_issue',
    'equipment_or_restriction',
    'other'
];
export function scoreCoachProgram(assessment) {
    const values = THREE_WAY_COACH_REVIEW_DIMENSIONS.map(d => assessment.scores[d]);
    return Math.round((values.reduce((sum, value) => sum + value, 0) / values.length) * 100) / 100;
}
export function validateBlindThreeWayCoachReview(record, packet) {
    const issues = [];
    if (record.packetId !== packet.id)
        issues.push(`packetId mismatch: ${record.packetId} != ${packet.id}`);
    if (!record.reviewer.trim())
        issues.push('reviewer is required');
    if (!record.reviewedAt.trim() || Number.isNaN(Date.parse(record.reviewedAt)))
        issues.push('reviewedAt must be a valid timestamp');
    for (const label of ['A', 'B', 'C']) {
        const assessment = record.programs?.[label];
        if (!assessment) {
            issues.push(`Program ${label} assessment is missing`);
            continue;
        }
        for (const dimension of THREE_WAY_COACH_REVIEW_DIMENSIONS) {
            const score = assessment.scores?.[dimension];
            if (!Number.isInteger(score) || score < 1 || score > 5)
                issues.push(`Program ${label} ${dimension} must be scored 1-5`);
        }
        if (!['pass', 'minor_changes', 'reject'].includes(assessment.grade))
            issues.push(`Program ${label} grade is invalid`);
        if (!Array.isArray(assessment.issues))
            issues.push(`Program ${label} issues must be an array`);
        const structured = assessment.issueTags ?? [];
        if (!Array.isArray(structured)) {
            issues.push(`Program ${label} structured issue tags must be an array`);
            continue;
        }
        const program = packet.programs.find(item => item.label === label);
        structured.forEach((tag, index) => {
            const prefix = `Program ${label} issue ${index + 1}`;
            if (!COACH_ISSUE_CATEGORIES.includes(tag.category))
                issues.push(`${prefix} category is invalid`);
            if (!['minor', 'major'].includes(tag.severity))
                issues.push(`${prefix} severity is invalid`);
            if (!['program', 'session', 'exercise'].includes(tag.scope))
                issues.push(`${prefix} scope is invalid`);
            if (!tag.note?.trim())
                issues.push(`${prefix} note is required`);
            if (tag.scope === 'program') {
                if (tag.sessionIndex !== undefined || tag.exerciseIndex !== undefined)
                    issues.push(`${prefix} program scope cannot include session/exercise indexes`);
            }
            else {
                if (!Number.isInteger(tag.sessionIndex) || tag.sessionIndex < 0 || !program || tag.sessionIndex >= program.sessions.length)
                    issues.push(`${prefix} session index is out of range`);
                else if (tag.scope === 'exercise') {
                    const session = program.sessions[tag.sessionIndex];
                    if (!Number.isInteger(tag.exerciseIndex) || tag.exerciseIndex < 0 || tag.exerciseIndex >= session.exercises.length)
                        issues.push(`${prefix} exercise index is out of range`);
                }
                else if (tag.exerciseIndex !== undefined)
                    issues.push(`${prefix} session scope cannot include an exercise index`);
            }
        });
    }
    if (!['A', 'B', 'C', 'equivalent', 'mixed'].includes(record.preference))
        issues.push('preference is invalid');
    if (!record.rationale.trim())
        issues.push('rationale is required before unblinding');
    return issues;
}
export function unblindThreeWayCoachReview(record, packet) {
    const validation = validateBlindThreeWayCoachReview(record, packet);
    if (validation.length)
        throw new Error(`Blind review is incomplete: ${validation.join('; ')}`);
    const byOrigin = {};
    const overallScores = {};
    for (const label of ['A', 'B', 'C']) {
        const origin = packet.reviewKey[label];
        byOrigin[origin] = record.programs[label];
        overallScores[origin] = scoreCoachProgram(record.programs[label]);
    }
    const preferredOrigin = record.preference === 'A' || record.preference === 'B' || record.preference === 'C'
        ? packet.reviewKey[record.preference]
        : record.preference;
    return {
        packetId: record.packetId,
        reviewer: record.reviewer,
        byOrigin,
        preferredOrigin,
        overallScores,
        candidateVsLegacy: Math.round((overallScores.candidate - overallScores.legacy_v661) * 100) / 100,
        candidateVsReference: Math.round((overallScores.candidate - overallScores.coach_reference) * 100) / 100,
        rationale: record.rationale
    };
}
function round2(value) { return Math.round(value * 100) / 100; }
function median(values) {
    const sorted = [...values].sort((a, b) => a - b);
    const mid = Math.floor(sorted.length / 2);
    return sorted.length % 2 ? sorted[mid] : round2((sorted[mid - 1] + sorted[mid]) / 2);
}
function mean(values) { return values.length ? values.reduce((sum, value) => sum + value, 0) / values.length : 0; }
function sign(value) { return value > 0 ? 1 : value < 0 ? -1 : 0; }
export function validateBlindThreeWayCoachReviewBatch(records, packet, minimumReviewers = 2) {
    const issues = [];
    if (records.length < minimumReviewers)
        issues.push(`at least ${minimumReviewers} completed reviewers are required before unblinding`);
    const seen = new Set();
    records.forEach((record, index) => {
        for (const issue of validateBlindThreeWayCoachReview(record, packet))
            issues.push(`review ${index + 1}: ${issue}`);
        const key = record.reviewer.trim().toLocaleLowerCase();
        if (key && seen.has(key))
            issues.push(`reviewer ${record.reviewer.trim()} submitted more than once`);
        if (key)
            seen.add(key);
    });
    return issues;
}
/**
 * Aggregates complete blind scorecards only after the batch is ready to unblind. Each reviewer
 * contributes one vote and one within-reviewer A/B/C comparison. Raw means remain inspectable,
 * while centered scores and median pairwise deltas keep a generally harsh/generous reviewer—or
 * one contrarian scorecard—from silently dominating the comparison.
 */
export function aggregateBlindThreeWayCoachReviews(records, packet, minimumReviewers = 2) {
    const validation = validateBlindThreeWayCoachReviewBatch(records, packet, minimumReviewers);
    if (validation.length)
        throw new Error(`Blind review batch is incomplete: ${validation.join('; ')}`);
    const origins = ['legacy_v661', 'candidate', 'coach_reference'];
    const labels = ['A', 'B', 'C'];
    const reviewers = [];
    const rawByOrigin = Object.fromEntries(origins.map(origin => [origin, []]));
    const centeredByOrigin = Object.fromEntries(origins.map(origin => [origin, []]));
    const assessmentByOrigin = Object.fromEntries(origins.map(origin => [origin, []]));
    const preferenceVotes = { legacy_v661: 0, candidate: 0, coach_reference: 0, equivalent: 0, mixed: 0 };
    const labelScoresByReviewer = [];
    for (const record of records) {
        const labelScores = {};
        const rawScores = {};
        for (const label of labels) {
            const score = scoreCoachProgram(record.programs[label]);
            labelScores[label] = score;
            const origin = packet.reviewKey[label];
            rawScores[origin] = score;
            rawByOrigin[origin].push(score);
            assessmentByOrigin[origin].push(record.programs[label]);
        }
        labelScoresByReviewer.push(labelScores);
        const reviewerMean = mean(Object.values(rawScores));
        const centeredScores = Object.fromEntries(origins.map(origin => [origin, round2(rawScores[origin] - reviewerMean)]));
        for (const origin of origins)
            centeredByOrigin[origin].push(centeredScores[origin]);
        const preferred = record.preference === 'A' || record.preference === 'B' || record.preference === 'C' ? packet.reviewKey[record.preference] : record.preference;
        preferenceVotes[preferred]++;
        reviewers.push({
            reviewer: record.reviewer,
            meanScore: round2(reviewerMean),
            rawScores,
            centeredScores,
            candidateVsLegacy: round2(rawScores.candidate - rawScores.legacy_v661),
            candidateVsReference: round2(rawScores.candidate - rawScores.coach_reference)
        });
    }
    const byOrigin = {};
    for (const origin of origins) {
        const raw = rawByOrigin[origin];
        const assessments = assessmentByOrigin[origin];
        const dimensionMeans = {};
        for (const dimension of THREE_WAY_COACH_REVIEW_DIMENSIONS)
            dimensionMeans[dimension] = round2(mean(assessments.map(a => a.scores[dimension])));
        const gradeCounts = { pass: 0, minor_changes: 0, reject: 0 };
        assessments.forEach(a => gradeCounts[a.grade]++);
        byOrigin[origin] = {
            meanScore: round2(mean(raw)), medianScore: median(raw), meanCenteredScore: round2(mean(centeredByOrigin[origin])),
            scoreRange: round2(Math.max(...raw) - Math.min(...raw)), dimensionMeans, gradeCounts
        };
    }
    const deltaAggregate = (values) => {
        const med = median(values), direction = sign(med);
        const agreement = direction === 0 ? values.filter(value => sign(value) === 0).length / values.length : values.filter(value => sign(value) === direction).length / values.length;
        return { mean: round2(mean(values)), median: med, directionalAgreement: round2(agreement) };
    };
    const legacyDeltas = reviewers.map(r => r.candidateVsLegacy);
    const referenceDeltas = reviewers.map(r => r.candidateVsReference);
    const candidateVsLegacy = deltaAggregate(legacyDeltas);
    const candidateVsReference = deltaAggregate(referenceDeltas);
    let pairAgreementTotal = 0, pairAgreementCount = 0;
    for (let i = 0; i < labelScoresByReviewer.length; i++)
        for (let j = i + 1; j < labelScoresByReviewer.length; j++) {
            for (let a = 0; a < labels.length; a++)
                for (let b = a + 1; b < labels.length; b++) {
                    const left = sign(labelScoresByReviewer[i][labels[a]] - labelScoresByReviewer[i][labels[b]]);
                    const right = sign(labelScoresByReviewer[j][labels[a]] - labelScoresByReviewer[j][labels[b]]);
                    pairAgreementTotal += left === right ? 1 : (left === 0 || right === 0 ? 0.5 : 0);
                    pairAgreementCount++;
                }
        }
    const flaggedDimensions = [];
    for (const origin of origins)
        for (const dimension of THREE_WAY_COACH_REVIEW_DIMENSIONS) {
            const values = assessmentByOrigin[origin].map(a => a.scores[dimension]);
            const range = Math.max(...values) - Math.min(...values);
            if (range >= 2)
                flaggedDimensions.push({ origin, dimension, range });
        }
    const reviewerMeans = reviewers.map(r => r.meanScore);
    const medianLegacyDelta = median(legacyDeltas);
    const deltaOutlierReviewers = reviewers.filter(r => Math.abs(r.candidateVsLegacy - medianLegacyDelta) >= 1.5).map(r => r.reviewer);
    const concretePreferenceMax = Math.max(preferenceVotes.legacy_v661, preferenceVotes.candidate, preferenceVotes.coach_reference, preferenceVotes.equivalent, preferenceVotes.mixed);
    const consensusIssues = [];
    for (const origin of origins) {
        const label = labels.find(item => packet.reviewKey[item] === origin);
        const program = packet.programs.find(item => item.label === label);
        const groups = new Map();
        records.forEach(record => {
            for (const tag of record.programs[label].issueTags ?? []) {
                const key = [tag.category, tag.scope, tag.sessionIndex ?? '', tag.exerciseIndex ?? ''].join('|');
                const group = groups.get(key) ?? { tags: [], reviewers: [] };
                group.tags.push(tag);
                group.reviewers.push(record.reviewer);
                groups.set(key, group);
            }
        });
        for (const group of groups.values()) {
            const uniqueReviewers = [...new Set(group.reviewers.map(name => name.trim()).filter(Boolean))];
            if (uniqueReviewers.length < 2)
                continue;
            const tag = group.tags[0];
            const session = tag.sessionIndex === undefined ? undefined : program.sessions[tag.sessionIndex];
            const exercise = session && tag.exerciseIndex !== undefined ? session.exercises[tag.exerciseIndex] : undefined;
            const target = tag.scope === 'program' ? 'Whole program' : tag.scope === 'session' ? (session ? `${session.day} · ${session.name}` : `Session ${tag.sessionIndex + 1}`) : (exercise ? `${session.day} · ${session.name} · ${exercise.name}` : `Exercise ${tag.exerciseIndex + 1}`);
            consensusIssues.push({
                origin, category: tag.category, severity: group.tags.some(item => item.severity === 'major') ? 'major' : 'minor', scope: tag.scope,
                sessionIndex: tag.sessionIndex, exerciseIndex: tag.exerciseIndex, target, reviewerCount: uniqueReviewers.length,
                reviewerShare: round2(uniqueReviewers.length / records.length), reviewers: uniqueReviewers, notes: [...new Set(group.tags.map(item => item.note.trim()).filter(Boolean))]
            });
        }
    }
    return {
        packetId: packet.id, reviewerCount: records.length, byOrigin, preferenceVotes, candidateVsLegacy, candidateVsReference,
        agreement: {
            rankAgreement: round2(pairAgreementCount ? pairAgreementTotal / pairAgreementCount : 1),
            preferenceConsensus: round2(concretePreferenceMax / records.length),
            meanScoreRange: round2(mean(origins.map(origin => byOrigin[origin].scoreRange))),
            reviewerLeniencyRange: round2(Math.max(...reviewerMeans) - Math.min(...reviewerMeans)),
            flaggedDimensions, deltaOutlierReviewers, consensusIssues
        },
        reviewers
    };
}
export function reviewerPacketView(packet) {
    const { reviewKey: _, ...reviewer } = packet;
    return reviewer;
}
function regressionAssertion(category, scope) {
    const noun = scope === 'exercise' ? 'exercise placement' : scope === 'session' ? 'session design' : 'program design';
    const actions = {
        redundant_work: 'reject repeated low-value movement-family work when an equivalent lower-fatigue allocation exists',
        poor_sequence: 'require primary/high-skill work to precede lower-priority fatigue-producing work',
        fatigue_risk: 'cap avoidable local/systemic fatigue collisions while preserving required exposures',
        underdosed: 'protect the requested priority floor after time/capacity reconciliation',
        overdosed: 'prevent non-priority volume from exceeding the productive capacity budget',
        misplaced_exercise: 'keep flexible accessory spillover bounded by session identity and interference cost',
        split_identity: 'require the named split/session intent to remain recognizable after allocation and repair',
        prescription_issue: 'require executable role-appropriate sets/reps/RIR/rest prescriptions',
        equipment_or_restriction: 'fail closed rather than satisfying work with unavailable or restricted equipment',
        other: 'add a fixture-specific semantic assertion after human triage confirms the repeated defect'
    };
    return `${noun}: ${actions[category]}.`;
}
/**
 * Converts repeated, independently-reviewed candidate-only consensus defects into regression-test
 * candidates. This never mutates engine policy automatically: it surfaces reproducible human-review
 * signals that still require a maintainer to inspect the named packets and encode an objective test.
 */
export function discoverCoachRegressionCandidates(batches, minimumPackets = 2, minimumReviewers = 2) {
    const packetSets = Object.fromEntries(['legacy_v661', 'candidate', 'coach_reference'].map(origin => [origin, new Map()]));
    const candidateExamples = new Map();
    for (const batch of batches) {
        const aggregate = aggregateBlindThreeWayCoachReviews(batch.records, batch.packet, minimumReviewers);
        for (const issue of aggregate.agreement.consensusIssues) {
            const key = `${issue.category}|${issue.scope}`;
            const set = packetSets[issue.origin].get(key) ?? new Set();
            set.add(batch.packet.id);
            packetSets[issue.origin].set(key, set);
            if (issue.origin === 'candidate') {
                const examples = candidateExamples.get(key) ?? [];
                examples.push({ packetId: batch.packet.id, target: issue.target, severity: issue.severity, reviewerCount: issue.reviewerCount });
                candidateExamples.set(key, examples);
            }
        }
    }
    const out = [];
    for (const [key, packets] of packetSets.candidate.entries()) {
        const [category, scope] = key.split('|');
        const candidatePacketCount = packets.size;
        const legacyPacketCount = packetSets.legacy_v661.get(key)?.size ?? 0;
        const referencePacketCount = packetSets.coach_reference.get(key)?.size ?? 0;
        if (candidatePacketCount < minimumPackets || candidatePacketCount <= Math.max(legacyPacketCount, referencePacketCount))
            continue;
        out.push({
            id: `coach-${category}-${scope}`, category, scope, candidatePacketCount, legacyPacketCount, referencePacketCount,
            examples: candidateExamples.get(key) ?? [], recommendedAssertion: regressionAssertion(category, scope)
        });
    }
    return out.sort((a, b) => b.candidatePacketCount - a.candidatePacketCount || a.id.localeCompare(b.id));
}
