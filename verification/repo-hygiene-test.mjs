import test from 'node:test';
import assert from 'node:assert/strict';
import { selectMergedBranchTips } from '../scripts/prune-merged-branches.mjs';

const repository = 'extracteddeath/Pursuit-Iron';
const branch = (name, sha, protectedBranch = false) =>
  ({ name, protected: protectedBranch, commit: { sha } });
const pr = (name, sha, merged = true, source = repository) => ({
  merged_at: merged ? '2026-10-30T12:00:00Z' : null,
  head: { ref: name, sha, repo: source ? { full_name: source } : null }
});

test('only exact merged PR heads are eligible, never unmerged work or updated branches', () => {
  const branches = [
    branch('main', 'main-sha'), branch('beta','beta-sha'),
    branch('fix/merged', 'old-merged-sha'),
    branch('audit/merged', 'merged-audit-sha'),
    branch('fix/advanced-after-merge', 'new-sha'),
    branch('fix/pending-pr', 'pending-sha'),
    branch('fix/never-reviewed', 'lonely-sha'),
    branch('fix/protected', 'protected-sha', true),
    branch('fix/foreign-pr', 'fork-sha'),
    branch('fix/open-and-merged', 'open-sha')
  ];
  const mergedPRs = [
    pr('main','main-sha'),pr('beta','beta-sha'),
    pr('fix/merged','old-merged-sha'),pr('audit/merged','merged-audit-sha'),
    pr('fix/advanced-after-merge','old-sha'),
    pr('fix/pending-pr','pending-sha'),
    pr('fix/protected','protected-sha'),
    pr('fix/foreign-pr','fork-sha',true,'someone-else/fork'),
    pr('fix/open-and-merged','open-sha')
  ];
  const openPRs = [pr('fix/pending-pr', 'pending-sha', false),
    pr('fix/open-and-merged', 'open-sha', false)];
  const result = selectMergedBranchTips({
    branches,mergedPRs,openPRs,repository,defaultBranch:'main'
  });
  assert.deepEqual(result.map(row=>row.name),
    ['fix/merged','audit/merged'],
    'a closed merged PR with matching branch tip is necessary but not enough if protected/open');
  assert.deepEqual(branches[4].commit.sha,'new-sha','candidate selection never rewrites branch refs');
});

test('unknown merged status, mismatched SHA, fork and missing branch data are never proof', () => {
  const branches=[branch('docs/current','a'),branch('docs/unknown','b'),branch('docs/fork','c')];
  const mergedPRs=[
    pr('docs/current','different-sha'),pr('docs/unknown','b',false),
    pr('docs/fork','c',true,null)
  ];
  assert.deepEqual(selectMergedBranchTips({
    branches,mergedPRs,openPRs:[],repository
  }),[]);
});
