/** Compare certified release identities; source-only verification edits may retain a build. */
export function verifyBuildProgression(current, previous) {
    for (const [label, release] of [['Current', current], ['Previous', previous]]) {
        if (!Number.isSafeInteger(release?.build) || release.build < 1)
            throw new Error(`${label} release build must be a positive safe integer`);
    }
    if (current.build < previous.build)
        throw new Error(`Release build cannot go backwards: ${previous.build} → ${current.build}`);
    const entries = release => Object.entries({ ...release.runtimeFiles, ...release.uiFiles })
        .sort(([a], [b]) => a.localeCompare(b));
    const changed = current.cache !== previous.cache
        || JSON.stringify(entries(current)) !== JSON.stringify(entries(previous));
    if (changed && current.build === previous.build)
        throw new Error(`Shipped files changed without increasing build ${previous.build}. Run node scripts/finalize-release.mjs --next-build`);
    return { previousBuild: previous.build, build: current.build, shippedFilesChanged: changed };
}
