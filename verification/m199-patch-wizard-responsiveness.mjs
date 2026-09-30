import fs from 'node:fs';
const path='modules/App.js';
let src=fs.readFileSync(path,'utf8');
function replaceOnce(before,after,label){const n=src.split(before).length-1;if(n!==1)throw new Error(`${label}: expected 1 match, got ${n}`);src=src.replace(before,after);}
function replaceBetween(start,end,replacement,label){const a=src.indexOf(start);if(a<0)throw new Error(`${label}: start marker missing`);const b=src.indexOf(end,a+start.length);if(b<0)throw new Error(`${label}: end marker missing`);src=src.slice(0,a)+replacement+src.slice(b);}

replaceBetween(
  '    /* ⚠ MARK A CHOICE UNAVAILABLE ONLY IF NO REMAINING CHOICE CAN MAKE IT WORK.',
  '    const canNext = (() => {',
`    /* M199 responsiveness: the old split/session availability pass ran a full audited generation for every visible choice.
       A single check is already a long main-thread task; multiplying it across the list caused the split picker to hitch even
       though each check started from setTimeout(0). Hard lift/equipment contracts are cheap and stay on the split screen.
       Full audited feasibility now runs only for the session length the lifter actually selected. */
    const [buildable, setBuildable] = useState({});
    const fpBase = { ...config, goal: effectiveGoal, name: "" };
    const buildKey = key === "session" ? "session|" + JSON.stringify(fpBase) : "";
    useEffect(() => {
        if (!buildKey || !config.split || !config.session)
            return;
        let cancelled = false, timer = null;
        const cfg = { ...config, goal: effectiveGoal };
        setBuildable(prev => (prev.__key === buildKey ? prev : { __key: buildKey }));
        const run = async () => {
            // Paint the selected card's loading state before the synchronous engine validation begins.
            await new Promise(resolve => setTimeout(resolve, 0));
            if (cancelled)
                return;
            const ck = JSON.stringify({ ...cfg, name: "" });
            let result = SPLIT_BUILD_CACHE.get(ck);
            if (!result) {
                try {
                    result = splitBuildability(cfg, EXERCISES);
                }
                catch {
                    // Final generation still fails closed; a classifier failure must not strand the wizard.
                    result = { ok: true };
                }
                if (SPLIT_BUILD_CACHE.size >= 256)
                    SPLIT_BUILD_CACHE.delete(SPLIT_BUILD_CACHE.keys().next().value);
                SPLIT_BUILD_CACHE.set(ck, result);
            }
            if (!cancelled)
                setBuildable({ __key: buildKey, [config.session]: result });
        };
        // Debounce quick taps so an abandoned session length never consumes a full generation pass.
        timer = setTimeout(run, 90);
        return () => { cancelled = true; clearTimeout(timer); };
    }, [buildKey]);
    const blockedHere = (id) => !!(buildable.__key === buildKey && buildable[id] && buildable[id].ok === false);
    const autoLength = null;
`, 'wizard feasibility scheduler');

replaceOnce(
`        if (key === "split")
            return !!config.split && buildable.__key === buildKey && !!buildable[config.split] && !blockedHere(config.split);
        if (key === "session")
            return buildable.__key === buildKey && !!buildable[config.session] && !blockedHere(config.session);`,
`        if (key === "split") {
            if (!config.split)
                return false;
            try {
                return splitContractGaps({ ...config, goal: effectiveGoal, split: config.split }, EXERCISES).length === 0;
            }
            catch {
                return true;
            }
        }
        if (key === "session")
            return buildable.__key === buildKey && !!buildable[config.session] && !blockedHere(config.session);`,
'canNext split/session');

replaceBetween(
  '        /* Verdict per split: contract gaps are known now; coverage results arrive from the Wizard\'s background pass. */',
  '    if (stepKey === "session") {',
`        /* Keep this list instant. Split-specific lift/equipment contracts are deterministic and cheap; the expensive full
           generation check belongs on the next step because session length is part of that feasibility decision. */
        const verdictOf = (gaps) => gaps.length ? { ok: false, kind: "lifts", items: gaps.map(liftName) } : { ok: true };
        const noteOf = (v) => v.kind === "lifts" ? \`Needs equipment for: \${v.items.join(", ")}\` : null;
        const withV = [...compatibleSplits].map(([k, sp]) => { const g = gapsOf(k); return [k, sp, g, verdictOf(g)]; });
        const ordered = withV.sort((a, b) => (a[2].length > 0) - (b[2].length > 0) || (a[0] === rec ? -1 : b[0] === rec ? 1 : 0));
        const blockedEq = ordered.filter(x => !x[3].ok).length;
        return (_jsxs(_Fragment, { children: [_jsx(Heading, { sub: \`Choose how to spread \${config.days} training days across the week.\`, children: "Pick your split" }), _jsx("div", { role: "status", style: { fontSize: 13, color: C.muted, marginBottom: 12 }, children: "Equipment requirements are checked here. Session-time fit is confirmed on the next step." }), blockedEq > 0 && (_jsxs("div", { role: "note", "data-testid": "split-equipment-note", style: { fontSize: 13, color: C.muted, lineHeight: 1.5, margin: "0 2px 12px" }, children: [blockedEq === 1 ? "One program can't" : \`\${blockedEq} programs can't\`, " be built with your current equipment, so ", blockedEq === 1 ? "it's" : "they're", " marked below. Add equipment in your gym settings to open ", blockedEq === 1 ? "it" : "them", " up."] })), _jsx(Col, { children: ordered.map(([k, sp, gaps, v]) => {
                        const off = !v.ok;
                        return (_jsx(OptionCard, { icon: Repeat, label: k === rec && !off ? \`\${sp.name} · Recommended\` : sp.name, sub: sp.blurb, selected: config.split === k && !off, disabled: off, note: noteOf(v), onClick: () => set({ split: k }) }, k));
                    }) })] }));
    }
`, 'split-step rendering');

replaceOnce(
`        const sessionNote = (v) => !v ? "Checking this session length…" : v.ok ? null
            : v.kind === "lifts" ? \`Needs equipment for: \${v.items.join(", ")}\`
                : \`Can't be built at this length\${v.items && v.items.length ? \` (\${v.items.join(", ")})\` : ""}\`;`,
`        const sessionNote = (id, v) => !v ? (config.session === id ? "Checking this session length…" : null) : v.ok ? "Fits your setup"
            : v.kind === "lifts" ? \`Needs equipment for: \${v.items.join(", ")}\`
                : \`Can't be built at this length\${v.items && v.items.length ? \` (\${v.items.join(", ")})\` : ""}\`;`,
'session note');
replaceOnce('note: sessionNote(buildable[s.id]), onClick: () => set({ session: s.id })','note: sessionNote(s.id, buildable[s.id]), onClick: () => set({ session: s.id })','session note call');

fs.writeFileSync(path,src);
console.log('M199 wizard responsiveness patch applied.');
