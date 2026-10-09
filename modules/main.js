import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
/* main.jsx — the mount. Kept deliberately tiny: App.jsx owns everything, and this file exists only
   so the bundle has an entry that touches the DOM. RootApp is App wrapped in the full-screen
   ErrorBoundary, so a render crash anywhere still shows a recoverable screen. */
import React from "react";
import { createRoot } from "react-dom/client";
import "./wizard-stability.js";
import RootApp from "./App.js";
import { installAppMotion } from "./ui-motion.js";
/* Startup is not complete when createRoot().render() RETURNS — React/Preact may commit later.
   M44 marked __pursuitMounted too early, which could suppress the pre-mount fallback while the
   first render was still capable of failing. This sentinel runs only after a real commit. */
function BootCommit() {
    React.useEffect(() => {
        const disposeMotion = installAppMotion(document.getElementById("root"));
        if (typeof window.__pursuitMarkMounted === "function")
            window.__pursuitMarkMounted();
        else
            window.__pursuitMounted = true;
        return disposeMotion;
    }, []);
    return null;
}
const rootEl = document.getElementById("root");
try {
    createRoot(rootEl).render(_jsxs(React.StrictMode, { children: [_jsx(RootApp, {}), _jsx(BootCommit, {})] }));
}
catch (err) {
    window.__pursuitBootError = err instanceof Error ? err.message : String(err);
    throw err;
}
