// One-shot repository housekeeping. A merged PR's exact head is the ONLY
// automatic deletion proof. Rebased, edited, open and unreviewed branches stay.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

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

async function run() {
  const repository = process.env.GITHUB_REPOSITORY;
  const token = process.env.GITHUB_TOKEN;
  const dryRun = process.env.DRY_RUN !== 'false';
  if (!repository || !token || !/^[\w.-]+\/[\w.-]+$/.test(repository))
    throw new Error('A valid GitHub Actions repository and token are required.');
  if (!dryRun && process.env.CONFIRM !== 'DELETE-MERGED')
    throw new Error('Deletion requires an explicit DELETE-MERGED confirmation.');
  const base = 'https://api.github.com/repos/' + repository + '/';
  const api = async (method, endpoint) => {
    const res = await fetch(base + endpoint, {
      method, headers: {
        Authorization: 'Bearer ' + token,
        Accept: 'application/vnd.github+json',
        'X-GitHub-Api-Version': '2022-11-28'
      }
    });
    if (!res.ok) {
      const detail = (await res.text()).slice(0, 200);
      const err = new Error(method + ' ' + endpoint + ': ' + res.status + ' ' + detail);
      err.status = res.status;
      throw err;
    }
    return res.status === 204 ? null : res.json();
  };
  const pages = async endpoint => {
    const rows = [];
    for (let page = 1; page <= 30; page++) {
      const batch = await api('GET', endpoint + (endpoint.includes('?') ? '&' : '?') + 'per_page=100&page=' + page);
      if (!Array.isArray(batch)) throw new Error('Expected paginated GitHub array for ' + endpoint);
      rows.push(...batch);
      if (batch.length < 100) return rows;
    }
    throw new Error('Pagination limit reached; refuse to proceed with incomplete evidence.');
  };
  const [repositoryInfo, branches, closed, open] = await Promise.all([
    api('GET', ''), pages('branches'), pages('pulls?state=closed'), pages('pulls?state=open')
  ]);
  const candidates = selectMergedBranchTips({
    branches, mergedPRs: closed, openPRs: open,
    repository, defaultBranch: repositoryInfo.default_branch
  });
  if (candidates.length > 500) throw new Error('Safety cap exceeded: inspect candidates manually.');
  let removed = 0, changed = 0, failed = 0;
  console.log('All branches: ' + branches.length + '; exact merged PR heads: ' + candidates.length);
  for (const branch of candidates) {
    const escaped = branch.name.split('/').map(encodeURIComponent).join('/');
    if (dryRun) {
      console.log('DRY RUN eligible: ' + branch.name + ' ' + branch.commit.sha);
      continue;
    }
    try {
      // Compare fresh tip before deletion: never discard a branch advanced
      // after the merged PR was recorded or after this run took its snapshot.
      const current = await api('GET', 'branches/' + escaped);
      if (current.protected || current.commit.sha !== branch.commit.sha) {
        changed++;
        console.log('PRESERVE changed/protected: ' + branch.name);
        continue;
      }
      await api('DELETE', 'git/refs/heads/' + escaped);
      removed++;
      console.log('DELETED merged: ' + branch.name);
    } catch (error) {
      if (error.status === 404) {
        changed++;
        console.log('PRESERVE disappeared: ' + branch.name);
      } else {
        failed++;
        console.error('FAILED ' + branch.name + ': ' + error.message);
      }
    }
  }
  const summary = 'Merged branch cleanup: ' + (dryRun ? 'DRY RUN' : 'APPLIED')
    + '\n\n| Branches reviewed | Exact merged tip candidates | Deleted | Changed/skipped | Errors |\n'
    + '|---:|---:|---:|---:|---:|\n| ' + branches.length + ' | ' + candidates.length
    + ' | ' + removed + ' | ' + changed + ' | ' + failed + ' |\n';
  if (process.env.GITHUB_STEP_SUMMARY)
    fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY, '## Repository branch hygiene\n\n' + summary);
  console.log(summary);
  if (failed) process.exitCode = 1;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url))
  run().catch(error => { console.error(error); process.exitCode = 1; });
