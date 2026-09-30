import fs from 'node:fs';
const path='modules/App.js';
let src=fs.readFileSync(path,'utf8');
function once(before,after,label){const n=src.split(before).length-1;if(n!==1)throw new Error(`${label}: expected 1 match, got ${n}`);src=src.replace(before,after);}

once(`    /* ⚠ MARK A CHOICE UNAVAILABLE ONLY IF NO REMAINING CHOICE CAN MAKE IT WORK. The split step comes BEFORE the session
       step, so a split refused at the default length may build at another: measured, 11 of 17 coverage/design refusals at
       60 minutes build at some other length — Bro 5-day "both" at 90+, bodyweight Upper/Lower 2-day at 40. The first
       version of this disabled all 17, hiding programs the lifter could have built. Now:
         split step   — blocked only if NO length the split allows builds; if only some do, it stays open with a hint;
         session step — the lengths that refuse for the chosen split are disabled, with the reason.
       Barbell-contract gaps don't depend on length. Coverage limits need a real generation (~20-50 ms each, more on a
       phone), so one check per tick in the background, marked in place, cached per full config. A blocked choice can't be
       continued with; it is NOT auto-cleared, because the recommendation effect above would re-select it and loop. */
    const [buildable, setBuildable] = useState({});
    const fpBase = { ...config, goal: effectiveGoal, name: "" };
    const buildKey = key === "split" ? "split|" + JSON.stringify({ ...fpBase, split: "" })
        : key === "session" ? "session|" + JSON.stringify({ ...fpBase, session: "" }) : "";
    useEffect(() => {
        if (!buildKey)
            return;
        let cancelled = false, timer = null;
        const base = { ...config, goal: effectiveGoal };
        const verdict = async (cfg) => {
            await new Promise(resolve => setTimeout(resolve, 0));
            if (cancelled)
                return null;
            const ck = JSON.stringify({ ...cfg, name: "" });
            let r = SPLIT_BUILD_CACHE.get(ck);
            if (!r) {
                try {
                    r = splitBuildability(cfg, EXERCISES);
                }
                catch {
                    r = { ok: true };
                }
                if (SPLIT_BUILD_CACHE.size >= 256)
                    SPLIT_BUILD_CACHE.delete(SPLIT_BUILD_CACHE.keys().next().value);
                SPLIT_BUILD_CACHE.set(ck, r);
            }
            return r;
        };
        const todo = key === "split" ? compatibleSplits.map(([k]) => ["split", k]) : sessionChoicesFor(config.split).map(x => ["session", x.id]);
        setBuildable(prev => (prev.__key === buildKey ? prev : { __key: buildKey }));
        const tick = async () => {
            if (cancelled)
                return;
            const item = todo.shift();
            if (!item)
                return;
            const [kind, id] = item;
            let r;
            if (kind === "split") {
                r = await verdict({ ...base, split: id });
                if (cancelled || !r)
                    return;
                if (!r.ok && r.kind !== "lifts") {
                    const okSessions = [];
                    for (const x of sessionChoicesFor(id)) {
                        if (x.id === base.session)
                            continue;
                        const result = await verdict({ ...base, split: id, session: x.id });
                        if (cancelled || !result)
                            return;
                        if (result.ok)
                            okSessions.push(x.id);
                    }
                    if (okSessions.length)
                        r = { ok: true, okSessions };
                }
            }
            else
                r = await verdict({ ...base, session: id });
            if (cancelled || !r)
                return;
            setBuildable(prev => ({ ...(prev.__key === buildKey ? prev : { __key: buildKey }), [id]: r }));
            timer = setTimeout(tick, 0);
        };
        timer = setTimeout(tick, 0);
        return () => { cancelled = true; clearTimeout(timer); };
    }, [buildKey, compatibleSplits]);
    const blockedHere = (id) => !!(buildable.__key === buildKey && buildable[id] && buildable[id].ok === false);
    /* Don't leave the lifter on a disabled default. 24 of 450 non-contract split choices (5.3%) refuse at the default
       60 minutes; they used to land here with 60 selected, disabled, and Next blocked. Once EVERY allowed length has a
       verdict, a blocked current length moves to the nearest one that builds (ties go longer), and the step says so.
       Cannot loop: the session step's fingerprint excludes the session itself, so moving it doesn't re-run the pass. */
    const [autoLength, setAutoLength] = useState(null);
    useEffect(() => {
        if (key !== "session" || buildable.__key !== buildKey)
            return;
        const ids = sessionChoicesFor(config.split).map(x => x.id);
        if (!ids.length || !ids.every(id => buildable[id]))
            return;
        if (!(buildable[config.session] && buildable[config.session].ok === false))
            return;
        const okIds = ids.filter(id => buildable[id].ok);
        if (!okIds.length)
            return;
        const order = SESSIONS.map(x => x.id), cur = order.indexOf(config.session);
        const best = okIds.slice().sort((a, b) => Math.abs(order.indexOf(a) - cur) - Math.abs(order.indexOf(b) - cur) || order.indexOf(b) - order.indexOf(a))[0];
        setAutoLength({ split: config.split, from: config.session, to: best });
        set({ session: best });
    }, [key, buildKey, buildable, config.session, config.split]);`,
`    /* M199 responsiveness: split-list feasibility used to run a full engine generation for every split and, on a refusal,
       every allowed session length. One check is ~140–270 ms on CI and materially longer on a phone, so the "background"
       setTimeout loop still produced repeated main-thread freezes and visible list glitches. The split step now performs only
       the engine's cheap equipment/lift contract checks. Full audited feasibility is deferred to the session step, where it
       validates exactly the session length the lifter selected. That preserves fail-closed generation while reducing N×
       expensive speculative generations to one explicit, user-visible check. */
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
            // Give the newly selected card and its "Checking…" status a guaranteed paint before CPU-heavy validation.
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
                    result = { ok: true };
                }
                if (SPLIT_BUILD_CACHE.size >= 256)
                    SPLIT_BUILD_CACHE.delete(SPLIT_BUILD_CACHE.keys().next().value);
                SPLIT_BUILD_CACHE.set(ck, result);
            }
            if (!cancelled)
                setBuildable({ __key: buildKey, [config.session]: result });
        };
        // Small debounce prevents wasted generation when someone is quickly tapping between lengths.
        timer = setTimeout(run, 90);
        return () => { cancelled = true; clearTimeout(timer); };
    }, [buildKey]);
    const blockedHere = (id) => !!(buildable.__key === buildKey && buildable[id] && buildable[id].ok === false);
    const autoLength = null;`, 'wizard feasibility scheduler');

once(`        if (key === "split")
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
            return buildable.__key === buildKey && !!buildable[config.session] && !blockedHere(config.session);`, 'canNext split/session');

once(`        /* Verdict per split: contract gaps are known now; coverage results arrive from the Wizard's background pass. */
        const verdictOf = (k, gaps) => gaps.length ? { ok: false, kind: "lifts", items: gaps.map(liftName) } : (buildable[k] || null);
        /* Only blame the gym when more equipment would actually help: \`design\` means a FULL gym is refused too. */
        const lengthLabel = (id) => (SESSIONS.find(x => x.id === id)?.label || id).toLowerCase();
        const noteOf = (v) => !v ? "Checking this split…"
            : v.ok ? (v.okSessions ? \`Works at \${config.days} days with \${v.okSessions.map(lengthLabel).join(" or ")} sessions\` : null)
                : v.kind === "lifts" ? \`Needs equipment for: \${v.items.join(", ")}\`
                    /* At the split step a design verdict means EVERY allowed session length was tried (see the Wizard), so the
                       engine's top suggestion — usually "increase weekly training capacity" — would be wrong advice here. */
                    : v.kind === "design" ? \`Doesn't come out balanced at \${config.days} days with any session length\${v.items.length ? \` (\${v.items.join(", ")})\` : ""} — try another number of days or goal\`
                        : v.items.length ? \`Not enough exercises with your equipment for \${v.items.join(", ")}\` : "Not enough exercises with your equipment";
        const withV = [...compatibleSplits].map(([k, sp]) => { const g = gapsOf(k); return [k, sp, g, verdictOf(k, g)]; });
        const ordered = withV.sort((a, b) => (a[2].length > 0) - (b[2].length > 0) || (a[0] === rec ? -1 : b[0] === rec ? 1 : 0));
        const blockedEq = ordered.filter(x => x[3] && x[3].ok === false && x[3].kind !== "design").length;
        const blockedDesign = ordered.filter(x => x[3] && x[3].ok === false && x[3].kind === "design").length;
        return (_jsxs(_Fragment, { children: [_jsx(Heading, { sub: \`Choose how to spread \${config.days} training days across the week.\`, children: "Pick your split" }), _jsx("div", { role: "status", style: { fontSize: 13, color: C.muted, marginBottom: 12 }, children: ordered.some(x => !x[3]) ? "Checking your equipment and available session lengths…" : "Availability checked. Choose an available split to continue." }), blockedEq > 0 && (_jsxs("div", { role: "note", "data-testid": "split-equipment-note", style: { fontSize: 13, color: C.muted, lineHeight: 1.5, margin: "0 2px 12px" }, children: [blockedEq === 1 ? "One program can't" : \`\${blockedEq} programs can't\`, " be built with your current equipment, so ", blockedEq === 1 ? "it's" : "they're", " marked below. Add equipment in your gym settings to open ", blockedEq === 1 ? "it" : "them", " up."] })), blockedDesign > 0 && (_jsxs("div", { role: "note", "data-testid": "split-design-note", style: { fontSize: 13, color: C.muted, lineHeight: 1.5, margin: "0 2px 12px" }, children: [blockedDesign === 1 ? "One program doesn't" : \`\${blockedDesign} programs don't\`, " come out balanced at ", config.days, " days, so ", blockedDesign === 1 ? "it's" : "they're", " marked below — a different number of days usually fixes it."] })), _jsx(Col, { children: ordered.map(([k, sp, gaps, v]) => {
                        const off = !!(v && v.ok === false);
                        return (_jsx(OptionCard, { icon: Repeat, label: k === rec && !off ? \`\${sp.name} · Recommended\` : sp.name, sub: sp.blurb, selected: config.split === k && !off, disabled: off, note: noteOf(v), onClick: () => set({ split: k }) }, k));
                    }) })] }));`,
`        /* The split list stays instant: only hard equipment/lift contracts are checked here. Full program feasibility depends
           on the session length too, so it is validated once the lifter chooses that length on the next step. */
        const verdictOf = (gaps) => gaps.length ? { ok: false, kind: "lifts", items: gaps.map(liftName) } : { ok: true };
        const noteOf = (v) => v.kind === "lifts" ? \`Needs equipment for: \${v.items.join(", ")}\` : null;
        const withV = [...compatibleSplits].map(([k, sp]) => { const g = gapsOf(k); return [k, sp, g, verdictOf(g)]; });
        const ordered = withV.sort((a, b) => (a[2].length > 0) - (b[2].length > 0) || (a[0] === rec ? -1 : b[0] === rec ? 1 : 0));
        const blockedEq = ordered.filter(x => x[3].ok === false).length;
        return (_jsxs(_Fragment, { children: [_jsx(Heading, { sub: \`Choose how to spread \${config.days} training days across the week.\`, children: "Pick your split" }), _jsx("div", { role: "status", style: { fontSize: 13, color: C.muted, marginBottom: 12 }, children: "Equipment requirements are checked here. Session-time fit is confirmed on the next step." }), blockedEq > 0 && (_jsxs("div", { role: "note", "data-testid": "split-equipment-note", style: { fontSize: 13, color: C.muted, lineHeight: 1.5, margin: "0 2px 12px" }, children: [blockedEq === 1 ? "One program can't" : \`\${blockedEq} programs can't\`, " be built with your current equipment, so ", blockedEq === 1 ? "it's" : "they're", " marked below. Add equipment in your gym settings to open ", blockedEq === 1 ? "it" : "them", " up."] })), _jsx(Col, { children: ordered.map(([k, sp, gaps, v]) => {
                        const off = !v.ok;
                        return (_jsx(OptionCard, { icon: Repeat, label: k === rec && !off ? \`\${sp.name} · Recommended\` : sp.name, sub: sp.blurb, selected: config.split === k && !off, disabled: off, note: noteOf(v), onClick: () => set({ split: k }) }, k));
                    }) })] }));`, 'split-step rendering');

once(`        const sessionNote = (v) => !v ? "Checking this session length…" : v.ok ? null
            : v.kind === "lifts" ? \`Needs equipment for: \${v.items.join(", ")}\`
                : \`Can't be built at this length\${v.items && v.items.length ? \` (\${v.items.join(", ")})\` : ""}\`;`,
`        const sessionNote = (id, v) => !v ? (config.session === id ? "Checking this session length…" : null) : v.ok ? "Fits your setup"
            : v.kind === "lifts" ? \`Needs equipment for: \${v.items.join(", ")}\`
                : \`Can't be built at this length\${v.items && v.items.length ? \` (\${v.items.join(", ")})\` : ""}\`;`, 'session note');

once(`note: sessionNote(buildable[s.id]), onClick: () => set({ session: s.id })`,
`note: sessionNote(s.id, buildable[s.id]), onClick: () => set({ session: s.id })`, 'session note call');

fs.writeFileSync(path,src);
console.log('M199 wizard responsiveness patch applied.');
