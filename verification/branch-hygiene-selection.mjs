// Pure, independently testable repository branch-safety classifier.
// A merged PR with exactly matching head SHA and name is the only deletion proof.
export function selectMergedBranchTips({ branches, mergedPRs, openPRs, repository, defaultBranch = 'main' }) {
  const exact = new Set(mergedPRs
    .filter(pr => pr.merged_at && pr.head?.repo?.full_name === repository)
    .map(pr => pr.head.ref + ':' + pr.head.sha));
  const active = new Set(openPRs
    .filter(pr => pr.head?.repo?.full_name === repository)
    .map(pr => pr.head.ref));
  return branches.filter(branch =>
    !branch.protected
    && ![defaultBranch, 'main', 'beta'].includes(branch.name)
    && !active.has(branch.name)
    && exact.has(branch.name + ':' + branch.commit.sha));
}

