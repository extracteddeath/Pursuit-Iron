import fs from 'node:fs';
const path='modules/App.js';
let src=fs.readFileSync(path,'utf8');
function once(before,after,label){const n=src.split(before).length-1;if(n!==1)throw new Error(`${label}: expected 1 match, got ${n}`);src=src.replace(before,after);}

once('    const [, forceTick] = useState(0);', '    const timerTextRef = useRef(null);', 'timer render state');
once('        forceTick(n => n + 1);\n', '', 'timer reset force render');
once('    useEffect(() => { const t = setInterval(() => forceTick(n => n + 1), 1000); return () => clearInterval(t); }, []);',
`    // The elapsed workout clock used to force-render this entire, very large Session component every
    // second. Duration math already comes from wall-clock refs, so update only the timer text node.
    // State changes such as pause/resume/reset still render normally; visibility/focus resync keeps the
    // display honest after background throttling without making every exercise/set/card rerender.
    useEffect(() => {
        const syncElapsedLabel = () => {
            const node = timerTextRef.current;
            if (!node)
                return;
            const seconds = Math.max(0, Math.floor(runElapsedMs() / 1000));
            node.textContent = \`${'${Math.floor(seconds / 3600)}'}:${'${String(Math.floor((seconds % 3600) / 60)).padStart(2, "0")}'}:${'${String(seconds % 60).padStart(2, "0")}'}\`;
        };
        syncElapsedLabel();
        if (runPaused)
            return;
        const t = setInterval(syncElapsedLabel, 1000);
        const onVisible = () => { if (document.visibilityState === "visible") syncElapsedLabel(); };
        document.addEventListener("visibilitychange", onVisible);
        window.addEventListener("focus", syncElapsedLabel);
        return () => {
            clearInterval(t);
            document.removeEventListener("visibilitychange", onVisible);
            window.removeEventListener("focus", syncElapsedLabel);
        };
    }, [runPaused]);`, 'whole-session elapsed ticker');
once('_jsx("span", { className: "mono", style: { fontWeight: 600, color: runPaused ? C.accentInk : C.text, whiteSpace: "nowrap" }, children: fmtEl })',
'_jsx("span", { ref: timerTextRef, className: "mono", style: { fontWeight: 600, color: runPaused ? C.accentInk : C.text, whiteSpace: "nowrap" }, children: fmtEl })', 'timer text ref');

fs.writeFileSync(path,src);
console.log('M199 workout timer render patch applied.');
