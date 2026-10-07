/**
 * Shared candidate-selection primitives for the engine realizer.
 *
 * Static candidate pools deliberately preserve the original candidate objects.
 * Do not project exercise definitions into a reduced cache shape: downstream
 * selection and repair passes may depend on prescription, technique, provenance,
 * custom metadata, or future fields that are not part of the ranking key.
 */
export const DEFAULT_CANDIDATE_EVALUATION_LIMIT = 512;

function finiteScore(value) {
    return Number.isFinite(value) ? value : Number.NEGATIVE_INFINITY;
}

/**
 * Build a reusable static candidate pool while preserving full object identity.
 * Static filters may include equipment, bodyweight mode, and explicit avoidance;
 * dynamic constraints belong in selectBestCandidate().
 */
export function buildCandidatePool(catalog, staticAccept = () => true) {
    if (!Array.isArray(catalog))
        throw new TypeError('Candidate catalog must be an array.');
    if (typeof staticAccept !== 'function')
        throw new TypeError('Candidate staticAccept must be a function.');
    return catalog.filter((candidate, index) => staticAccept(candidate, index));
}

/**
 * Deterministically select one candidate.
 *
 * Hard constraints always run before ranking. Ranking work is bounded so a bad
 * custom catalog cannot turn one slot into unbounded scoring work. The default
 * ceiling is intentionally above the normal exercise catalog size, preserving
 * existing output while still providing a defensive upper bound.
 */
export function selectBestCandidate(pool, options = {}) {
    if (!Array.isArray(pool))
        throw new TypeError('Candidate pool must be an array.');
    const accept = options.accept ?? (() => true);
    const compare = options.compare;
    const score = options.score;
    const preCompare = options.preCompare;
    const tieBreak = options.tieBreak;
    const limit = Math.max(1, Math.floor(options.maxEvaluations ?? DEFAULT_CANDIDATE_EVALUATION_LIMIT));
    if (typeof accept !== 'function')
        throw new TypeError('Candidate accept must be a function.');
    if (compare !== undefined && typeof compare !== 'function')
        throw new TypeError('Candidate compare must be a function.');
    if (score !== undefined && typeof score !== 'function')
        throw new TypeError('Candidate score must be a function.');
    if (preCompare !== undefined && typeof preCompare !== 'function')
        throw new TypeError('Candidate preCompare must be a function.');
    if (tieBreak !== undefined && typeof tieBreak !== 'function')
        throw new TypeError('Candidate tieBreak must be a function.');

    const accepted = [];
    for (let index = 0; index < pool.length; index++) {
        const candidate = pool[index];
        if (accept(candidate, index))
            accepted.push({ candidate, index });
    }

    let shortlist = accepted;
    if (shortlist.length > limit) {
        if (preCompare) {
            shortlist = [...shortlist]
                .sort((a, b) => preCompare(a.candidate, b.candidate) || a.index - b.index)
                .slice(0, limit);
        }
        else {
            shortlist = shortlist.slice(0, limit);
        }
    }

    if (!shortlist.length)
        return {
            candidate: undefined,
            acceptedCount: accepted.length,
            evaluatedCount: 0,
            truncated: accepted.length > limit
        };

    if (compare) {
        const ranked = [...shortlist].sort((a, b) => compare(a.candidate, b.candidate) || a.index - b.index);
        return {
            candidate: ranked[0].candidate,
            acceptedCount: accepted.length,
            evaluatedCount: shortlist.length,
            truncated: accepted.length > shortlist.length
        };
    }

    let best = null;
    for (const item of shortlist) {
        const value = finiteScore(score ? score(item.candidate, item.index) : 0);
        const winsTie = best && value === best.score && tieBreak
            ? tieBreak(item.candidate, best.candidate) < 0
            : best && value === best.score && item.index < best.index;
        if (!best || value > best.score || winsTie)
            best = { ...item, score: value };
    }
    return {
        candidate: best?.candidate,
        acceptedCount: accepted.length,
        evaluatedCount: shortlist.length,
        truncated: accepted.length > shortlist.length
    };
}
