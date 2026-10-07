// M228 canonical engine shell. App.js consumes this module; isolated audits copy it verbatim.
import { PCT_SCHEMES, percentageProtocolFor, adaptPercentageSetBudget, deriveTieredLinearState } from './next-engine/percentage-protocols.js';
import { historyNumber, convertHistoryLoad, observedHistoryRIR, completedHistorySets, historyExposureContext, progressionExposureContext, normalizeHistoryEntries, validHistoryDate, historyLoadReason } from './next-engine/history-contract.js';
import { holdWorkoutScreenAwake } from "./mobile-lifecycle.js";
import { setShellEquipmentExpander, splitContractGaps, splitBuildability, refusalFixes, generateNextProgramForShell, recommendNextSplitForShell, getNextShellCell, canonicalShellSetCount, cloneNextDayPrescriptions, swapNextSlotPrescriptions, removeNextSlotPrescription, nextExerciseIdForShellExercise, resolveNextShellExerciseId, remapNextShellRoster, snapshotNextShellPrescription, markUserPrescriptionOverride, clearUserPrescriptionOverride, NextShellAdapterError } from "./next-engine/app-shell-adapter.js";
import { nextWorkoutSuggestionForShell, nextWorkoutSuggestionFromPerformedShell } from "./next-engine/workout-history-adapter.js";
import { generateNextCycleForShell, convertProgramToNextCycleForShell, nextCycleTemplatesForShell } from "./next-engine/cycle-runtime-adapter.js";
import { buildRuntimeSetTargets, customProgramProgressionStyle, refreshPendingSetTargets, reconcilePendingRepTargets, techniqueProtocolFromCell, freestyleCellForRepRange, buildUserAddedSlotPrescriptions } from "./next-engine/workout-runtime.js";
import { evaluateWorkoutProgression } from "./next-engine/performance.js";
import { deriveArmCoverage } from "./next-engine/arm-coverage.js";
import { deriveFunctionalCoverage } from "./next-engine/functional-coverage.js";
import { EXERCISE_MAP as NEXT_EXERCISE_MAP } from "./next-engine/exercise-db.js";
import { avoidableExerciseOverlap } from "./next-engine/exercise-economy.js";
import { ENGINE_VERSION, ENGINE_COMPATIBLE_VERSIONS } from "./next-engine/config.js";
import { captureShellVolumeSnapshot, auditShellVolume, repairShellVolume, shellVolumeTargets, shellDayMuscleBreakdown } from "./next-engine/volume-repair.js";
import { programWorkingWeeks, cycleBlockMetadata } from "./program-duration.js";
import { emptyRetiredTrialData, preserveRetiredTrialData, emptyRetiredRolloutData, preserveRetiredRolloutData } from "./legacy-research-data.js";
const __APP_VERSION__='4.0.0';

const __BUILD__='816';

const _fillLum = (h) => { const x = h.replace("#", ""); const f = (c) => { c /= 255; return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4); }; return 0.2126 * f(parseInt(x.slice(0, 2), 16)) + 0.7152 * f(parseInt(x.slice(2, 4), 16)) + 0.0722 * f(parseInt(x.slice(4, 6), 16)); };

const _onFill = (h) => {
    const L = _fillLum(h);
    const rW = 1.05 / (L + 0.05), rK = (L + 0.05) / (_fillLum("#1A1508") + 0.05);
    return rK >= rW ? "#1A1508" : "#FFFFFF";
};

const _hx2rgb = (h) => { const x = h.replace("#", ""); return [0, 2, 4].map((i) => parseInt(x.slice(i, i + 2), 16)); };

const _alphaOf = (h) => { const x = h.replace("#", ""); return x.length === 8 ? parseInt(x.slice(6, 8), 16) / 255 : 1; };

const _rgbLum = (r) => { const f = (c) => { c /= 255; return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4); }; return 0.2126 * f(r[0]) + 0.7152 * f(r[1]) + 0.0722 * f(r[2]); };

const _ratio = (a, b) => { const A = _rgbLum(a), B = _rgbLum(b); return (Math.max(A, B) + 0.05) / (Math.min(A, B) + 0.05); };

const _blend = (fg, bgRgb) => { const a = _alphaOf(fg), f = _hx2rgb(fg); return f.map((c, i) => Math.round(c * a + bgRgb[i] * (1 - a))); };

const _rgb2hsl = ([r, g, b]) => { r /= 255; g /= 255; b /= 255; const mx = Math.max(r, g, b), mn = Math.min(r, g, b), l = (mx + mn) / 2, d = mx - mn; let h = 0, s = 0; if (d) {
    s = l > 0.5 ? d / (2 - mx - mn) : d / (mx + mn);
    h = mx === r ? ((g - b) / d + (g < b ? 6 : 0)) : mx === g ? ((b - r) / d + 2) : ((r - g) / d + 4);
    h /= 6;
} return [h, s, l]; };

const _hsl2rgb = ([h, s, l]) => { if (!s) {
    const v = Math.round(l * 255);
    return [v, v, v];
} const q = l < 0.5 ? l * (1 + s) : l + s - l * s, p2 = 2 * l - q; const f = (t) => { t = (t + 1) % 1; if (t < 1 / 6)
    return p2 + (q - p2) * 6 * t; if (t < 0.5)
    return q; if (t < 2 / 3)
    return p2 + (q - p2) * (2 / 3 - t) * 6; return p2; }; return [f(h + 1 / 3), f(h), f(h - 1 / 3)].map((v) => Math.round(v * 255)); };

const _rgb2hx = (r) => "#" + r.map((v) => Math.max(0, Math.min(255, v)).toString(16).padStart(2, "0")).join("");

const _worst = (rgb, bgs) => bgs.reduce((m, b) => Math.min(m, _ratio(rgb, b)), Infinity);

const _ink = (base, bgs, target) => {
    const rgb = _hx2rgb(base);
    if (!rgb.every(Number.isFinite) || !bgs.length)
        return base;
    if (_worst(rgb, bgs) >= target)
        return base; // already legible — leave the designer's colour alone
    const lightest = bgs.reduce((a, b) => (_rgbLum(a) > _rgbLum(b) ? a : b));
    const lift = _rgbLum(lightest) < 0.5; // dark surfaces → lighten the ink, light surfaces → darken it
    const [h, s, l0] = _rgb2hsl(rgb);
    let lo = lift ? l0 : 0, hi = lift ? 1 : l0;
    for (let i = 0; i < 24; i++) {
        const m = (lo + hi) / 2, ok = _worst(_hsl2rgb([h, s, m]), bgs) >= target;
        if (lift) {
            if (ok)
                hi = m;
            else
                lo = m;
        }
        else if (ok)
            lo = m;
        else
            hi = m;
    }
    const out = _hsl2rgb([h, s, lift ? hi : lo]);
    return _worst(out, bgs) >= target ? _rgb2hx(out) : (lift ? "#FFFFFF" : "#000000"); // pure black/white if the hue can't get there
};

const INK_AA = 4.5;

const INK_SECONDARY = 6.0;

const TOK = (p) => {
    const surf = [p.bg, p.bgTint, p.surface, p.surface2].map(_hx2rgb);
    const onDim = (dim) => surf.concat(surf.map((s) => _blend(dim, s))); // chips: ink over a translucent tint over a surface
    const accentDim = p.accent + "22", dangerDim = p.danger + "1E", warnDim = p.warm + "1E";
    const warn = _ink(p.warm, onDim(warnDim), INK_AA);
    const danger = _ink(p.danger, onDim(dangerDim), INK_AA);
    return {
        bg: p.bg, bg2: p.bgTint, card: p.surface, cardHi: p.surface2,
        border: p.border, borderSoft: p.borderSoft,
        text: p.cream, muted: _ink(p.muted, surf, INK_SECONDARY), faint: _ink(p.mutedSoft, surf, INK_AA),
        accent: p.accent, accentInk: _ink(p.accentInk || p.accent, onDim(accentDim), INK_AA), accentDim, accentText: p.onAccent,
        danger, dangerDim, dangerText: _onFill(danger),
        warn, warnDim, warnText: _onFill(warn)
    };
};

const THEMES = {
    lime: { name: "Lime", base: "dark", accent: "#D4FF3D", palette: TOK({ bg: "#0A0908", bgTint: "#0F0D10", surface: "#15131A", surface2: "#1E1A25", border: "rgba(255,255,255,0.08)", borderSoft: "rgba(255,255,255,0.05)", cream: "#F2EEE4", muted: "#A29EA8", mutedSoft: "#6F6B76", accent: "#D4FF3D", onAccent: "#0A0908", warm: "#E4BC5D", danger: "#FF5D6C" }) },
    ember: { name: "Ember", base: "dark", accent: "#FF8A3D", palette: TOK({ bg: "#120907", bgTint: "#17100C", surface: "#1E1511", surface2: "#2A1D17", border: "rgba(255,200,150,0.09)", borderSoft: "rgba(255,200,150,0.04)", cream: "#F5EDE2", muted: "#A3968A", mutedSoft: "#786D64", accent: "#FF8A3D", onAccent: "#120907", warm: "#F5B85D", danger: "#FF5D6C" }) },
    ice: { name: "Ice", base: "dark", accent: "#5DD4E4", palette: TOK({ bg: "#090D12", bgTint: "#0D1219", surface: "#131A22", surface2: "#1B2430", border: "rgba(160,200,255,0.09)", borderSoft: "rgba(160,200,255,0.04)", cream: "#E8EDF2", muted: "#8A94A0", mutedSoft: "#68717C", accent: "#5DD4E4", onAccent: "#090D12", warm: "#E4C25D", danger: "#FF5D8A" }) },
    rose: { name: "Rose", base: "dark", accent: "#FF6B8A", palette: TOK({ bg: "#12091A", bgTint: "#170C20", surface: "#1D1227", surface2: "#281A35", border: "rgba(255,180,220,0.09)", borderSoft: "rgba(255,180,220,0.04)", cream: "#F0E8EE", muted: "#A0939E", mutedSoft: "#756B73", accent: "#FF6B8A", onAccent: "#12091A", warm: "#E4A55D", danger: "#FF5D6C" }) },
    forest: { name: "Forest", base: "dark", accent: "#A8DC5D", palette: TOK({ bg: "#080C0A", bgTint: "#0C120F", surface: "#121A15", surface2: "#1A2620", border: "rgba(180,220,180,0.09)", borderSoft: "rgba(180,220,180,0.04)", cream: "#E8EFE6", muted: "#8A948A", mutedSoft: "#68726E", accent: "#A8DC5D", onAccent: "#080C0A", warm: "#D4B85D", danger: "#E4615D" }) },
    light: { name: "Mint", base: "light", accent: "#238070", palette: TOK({ bg: "#F4F3F0", bgTint: "#EDECEA", surface: "#FFFFFF", surface2: "#F8F7F5", border: "rgba(20,20,18,0.10)", borderSoft: "rgba(20,20,18,0.05)", cream: "#1A1A18", muted: "#6B6A66", mutedSoft: "#8C8B88", accent: "#238070", onAccent: "#FFFFFF", warm: "#B87A2E", danger: "#CC3F3F" }) },
    clay: { name: "Clay", base: "light", accent: "#BE573A", palette: TOK({ bg: "#F2EDE1", bgTint: "#ECE5D4", surface: "#FFFFFF", surface2: "#FAF5EB", border: "rgba(26,21,16,0.10)", borderSoft: "rgba(26,21,16,0.05)", cream: "#1A1510", muted: "#78685A", mutedSoft: "#998A79", accent: "#BE573A", onAccent: "#FFFFFF", warm: "#B88936", danger: "#C4413D" }) },
    graphite: { name: "Graphite", base: "dark", accent: "#D8D8DC", palette: TOK({ bg: "#0B0B0C", bgTint: "#101012", surface: "#161618", surface2: "#1F1F22", border: "rgba(255,255,255,0.09)", borderSoft: "rgba(255,255,255,0.05)", cream: "#F0F0F1", muted: "#A0A0A6", mutedSoft: "#6C6C72", accent: "#D8D8DC", onAccent: "#0B0B0C", warm: "#D8C08A", danger: "#FF5D6C" }) },
    amethyst: { name: "Amethyst", base: "dark", accent: "#B388FF", palette: TOK({ bg: "#0C0913", bgTint: "#110D1A", surface: "#181221", surface2: "#231A30", border: "rgba(200,180,255,0.10)", borderSoft: "rgba(200,180,255,0.05)", cream: "#ECE6F2", muted: "#9D93AB", mutedSoft: "#6F687C", accent: "#B388FF", onAccent: "#0C0913", warm: "#E4C25D", danger: "#FF5D8A" }) },
    stone: { name: "Stone", base: "light", accent: "#3F3F46", palette: TOK({ bg: "#EFEFEF", bgTint: "#E7E7E7", surface: "#FFFFFF", surface2: "#F6F6F6", border: "rgba(20,20,20,0.11)", borderSoft: "rgba(20,20,20,0.05)", cream: "#1B1B1D", muted: "#6A6A6E", mutedSoft: "#8D8D90", accent: "#3F3F46", onAccent: "#FFFFFF", warm: "#A87E3C", danger: "#C43F3F" }) },
    // Patrol — a tactical, law-enforcement-styled theme: near-black slate surfaces with a cool blue
    // undertone and a duty-blue accent (the authoritative "police blue"). Built to sit alongside the
    // Pursuit family's tactical identity. Amber warnings read like dash/duty indicators.
    patrol: { name: "Patrol", base: "dark", accent: "#2563EB", palette: TOK({ bg: "#070A0F", bgTint: "#0B0F16", surface: "#11161F", surface2: "#19202C", border: "rgba(140,170,220,0.11)", borderSoft: "rgba(140,170,220,0.05)", cream: "#E6EBF2", muted: "#8C97A8", mutedSoft: "#646D7B", accent: "#2563EB", accentInk: "#4D8DF7", onAccent: "#F4F7FC", warm: "#F0B429", danger: "#EF4444" }) },
    slate: { name: "Slate", base: "light", accent: "#2563EB", palette: TOK({ bg: "#F0F2F6", bgTint: "#E7EBF1", surface: "#FFFFFF", surface2: "#F6F8FB", border: "rgba(20,30,50,0.11)", borderSoft: "rgba(20,30,50,0.05)", cream: "#15191F", muted: "#5B6470", mutedSoft: "#878F9A", accent: "#2563EB", onAccent: "#FFFFFF", warm: "#9A6B1E", danger: "#C43F3F" }) },
    lilac: { name: "Lilac", base: "light", accent: "#7C4DD4", palette: TOK({ bg: "#F4F1F8", bgTint: "#ECE6F3", surface: "#FFFFFF", surface2: "#F8F5FC", border: "rgba(40,25,60,0.10)", borderSoft: "rgba(40,25,60,0.05)", cream: "#1C1726", muted: "#6B6178", mutedSoft: "#938A9E", accent: "#7C4DD4", onAccent: "#FFFFFF", warm: "#9A6B1E", danger: "#C43F5A" }) },
    /* Four added later. Hue was the constraint, not taste: the existing fourteen already cover
       yellow-green, orange, cyan, pink, purple, blue, terracotta and neutral grey, so a new theme that
       is merely a shade of one of those is a longer list with nothing more to choose from. These take
       the gaps — true green, gold, muted sage and ochre.

       `onAccent` is the one token TOK does not fix for you. Every text colour goes through _ink, which
       drags it along its own lightness axis until it clears AA against the surfaces it sits on, so a
       careless `muted` self-corrects. `accentText` is passed straight through to sit on the accent
       FILL, where nothing measures it — a dark label on a dark accent stays exactly as dark as it was
       written. Each of these was measured against its accent before being added, and gate check 91 now
       holds every shipped palette to it. */
    emerald: { name: "Emerald", base: "dark", accent: "#34D399", palette: TOK({ bg: "#05100B", bgTint: "#081511", surface: "#0D1C16", surface2: "#14281F", border: "rgba(160,230,200,0.09)", borderSoft: "rgba(160,230,200,0.05)", cream: "#E8F2EC", muted: "#93A79C", mutedSoft: "#63756B", accent: "#34D399", onAccent: "#04120C", warm: "#E4BC5D", danger: "#FF5D6C" }) },
    gold: { name: "Gold", base: "dark", accent: "#F5C542", palette: TOK({ bg: "#100D07", bgTint: "#15110A", surface: "#1C1710", surface2: "#272016", border: "rgba(255,225,160,0.09)", borderSoft: "rgba(255,225,160,0.05)", cream: "#F3EDE0", muted: "#A9A08C", mutedSoft: "#726B5C", accent: "#F5C542", onAccent: "#14100A", warm: "#E58F4E", danger: "#FF5D6C" }) },
    sage: { name: "Sage", base: "light", accent: "#2F6B4F", palette: TOK({ bg: "#F1F4F0", bgTint: "#E8EDE6", surface: "#FFFFFF", surface2: "#F7FAF6", border: "rgba(20,35,25,0.10)", borderSoft: "rgba(20,35,25,0.05)", cream: "#16201A", muted: "#5C6B62", mutedSoft: "#84918A", accent: "#2F6B4F", onAccent: "#FFFFFF", warm: "#A8761C", danger: "#C0303F" }) },
    ochre: { name: "Ochre", base: "light", accent: "#9A5B10", palette: TOK({ bg: "#F7F2E8", bgTint: "#F0E9DA", surface: "#FFFFFF", surface2: "#FBF7EF", border: "rgba(45,32,12,0.10)", borderSoft: "rgba(45,32,12,0.05)", cream: "#201A10", muted: "#6E6048", mutedSoft: "#93856D", accent: "#9A5B10", onAccent: "#FFFFFF", warm: "#7A6A12", danger: "#B93A32" }) },
    rosewater: { name: "Rosewater", base: "light", accent: "#C43B63", palette: TOK({ bg: "#F8F0F1", bgTint: "#F2E6E9", surface: "#FFFFFF", surface2: "#FCF5F6", border: "rgba(50,20,28,0.10)", borderSoft: "rgba(50,20,28,0.05)", cream: "#241519", muted: "#7A6469", mutedSoft: "#9C868D", accent: "#C43B63", onAccent: "#FFFFFF", warm: "#B07A2E", danger: "#C43F3F" }) },
    ocean: { name: "Ocean", base: "light", accent: "#0A6F8E", palette: TOK({ bg: "#EEF6F8", bgTint: "#E3EFF2", surface: "#FFFFFF", surface2: "#F6FBFC", border: "rgba(15,45,55,0.10)", borderSoft: "rgba(15,45,55,0.05)", cream: "#142126", muted: "#53666C", mutedSoft: "#7C8D92", accent: "#0A6F8E", onAccent: "#FFFFFF", warm: "#A66A1F", danger: "#BE3F50" }) },
    citrus: { name: "Citrus", base: "light", accent: "#748600", palette: TOK({ bg: "#F7F9EC", bgTint: "#EEF2DD", surface: "#FFFFFF", surface2: "#FBFCEF", border: "rgba(40,48,12,0.10)", borderSoft: "rgba(40,48,12,0.05)", cream: "#1D2112", muted: "#646B4E", mutedSoft: "#8A9074", accent: "#748600", onAccent: "#0A0D00", warm: "#9B6D18", danger: "#BB3F3B" }) }
};

const C = { ...THEMES.lime.palette };

const _catInkCache = new Map();

const catInk = (hex) => {
    if (typeof hex !== "string" || !/^#[0-9a-fA-F]{6}$/.test(hex))
        return hex;
    const key = C.bg + C.bg2 + C.card + C.cardHi + hex;
    let v = _catInkCache.get(key);
    if (!v) {
        const surf = [C.bg, C.bg2, C.card, C.cardHi].map(_hx2rgb);
        // Include both the original chip tint and the derived ink's tint. Recheck after each
        // adjustment because changing the ink also changes a self-tinted background.
        const originalTint = surf.map(g => _blend(hex + "22", g));
        v = hex;
        for (let i = 0; i < 8; i++) {
            const grounds = surf.concat(originalTint, surf.map(g => _blend(v + "22", g)));
            if (_worst(_hx2rgb(v), grounds) >= INK_AA + .1)
                break;
            v = _ink(v, grounds, INK_AA + .1);
        }
        _catInkCache.set(key, v);
    }
    return v;
};

const eyebrow = () => ({ fontSize: 11, fontWeight: 800, letterSpacing: .5, color: C.faint, textTransform: "uppercase" });

const eyebrowAccent = () => ({ fontSize: 11, fontWeight: 800, letterSpacing: .5, color: C.accentInk, textTransform: "uppercase" });

function applyTheme(id) {
    Object.assign(C, (THEMES[id] || THEMES.lime).palette);
    // Keep the mobile status-bar / address-bar tint in sync with the theme so it feels native.
    try {
        if (typeof document !== "undefined") {
            let m = document.querySelector('meta[name="theme-color"]');
            if (!m) {
                m = document.createElement("meta");
                m.setAttribute("name", "theme-color");
                document.head.appendChild(m);
            }
            m.setAttribute("content", C.bg);
            /* Focus ring colour, published as a CSS variable so the stylesheet can draw a visible focus
               indicator in the current theme. It has to be a variable rather than an inline style: focus
               styling only exists as a :focus-visible rule, and there are ~1,400 controls — none of which
               should have to know about it. See the .wpb :focus-visible rule in StyleTag. */
            document.documentElement.style.setProperty("--wpb-focus", C.accentInk || C.accent);
            document.documentElement.style.setProperty("--wpb-focus-bg", C.bg);
            const activeTheme = THEMES[id] || THEMES.lime;
            document.documentElement.style.colorScheme = activeTheme.base === "light" ? "light" : "dark";
            // Tiny startup-theme cache: the portable shell can paint the user's real theme before React
            // parses the full store, eliminating the dark flash light-theme users otherwise see on launch.
            try {
                localStorage.setItem("wpb:boot-theme", JSON.stringify({ base: activeTheme.base, bg: C.bg, text: C.text, muted: C.muted, accent: C.accent, onAccent: C.accentText }));
            }
            catch { }
        }
    }
    catch { }
}

function isNativeApp() {
    try {
        const cap = typeof window !== "undefined" ? window.Capacitor : null;
        if (!cap)
            return false;
        return typeof cap.isNativePlatform === "function" ? cap.isNativePlatform() : !!cap.isNative;
    }
    catch {
        return false;
    }
}

const REMINDER_ID_BASE = 8100;

function nativeNotifier() {
    try {
        return (typeof window !== "undefined" && window.Capacitor?.Plugins?.LocalNotifications) || null;
    }
    catch {
        return null;
    }
}

function remindersAreReliable() { return !!nativeNotifier(); }

async function scheduleNativeReminders(reminders) {
    const LN = nativeNotifier();
    if (!LN)
        return false;
    try {
        // always clear the previous schedule first — otherwise editing the time leaves the old one armed
        const ids = Array.from({ length: 7 }, (_, i) => ({ id: REMINDER_ID_BASE + i }));
        try {
            await LN.cancel({ notifications: ids });
        }
        catch { }
        if (!reminders?.enabled || !reminders.days?.length)
            return true;
        try {
            const perm = await LN.checkPermissions();
            if (perm?.display !== "granted") {
                const req = await LN.requestPermissions();
                if (req?.display !== "granted")
                    return false;
            }
        }
        catch { }
        const [hh, mm] = String(reminders.time || "18:00").split(":").map(Number);
        await LN.schedule({
            notifications: reminders.days.map((d) => ({
                id: REMINDER_ID_BASE + d,
                title: "Time to train",
                body: "Your next Pursuit Iron session is waiting.",
                schedule: { on: { weekday: d + 1, hour: hh, minute: mm }, repeats: true, allowWhileIdle: true }, // Capacitor weekday: 1=Sun
            }))
        });
        return true;
    }
    catch {
        return false;
    }
}

function ensureNotifyPermission() {
    try {
        if (isNativeApp())
            return; // native build: handled by the notifications plugin, not the web API
        if (typeof window !== "undefined" && "Notification" in window && Notification.permission === "default") {
            Notification.requestPermission().catch(() => { });
        }
    }
    catch { }
}

let HAPTICS_ON = true;

function setHapticsEnabled(v) { HAPTICS_ON = !!v; }

function hapticsSupported() { try {
    return typeof navigator !== "undefined" && typeof navigator.vibrate === "function";
}
catch {
    return false;
} }

const HAPTIC = { tap: 18, light: 12, medium: 30, success: [22, 50, 22], strong: [40, 60, 40], alert: [180, 90, 180] };

function buzz(pattern = "tap") {
    if (!HAPTICS_ON)
        return;
    try {
        const p = typeof pattern === "string" ? (HAPTIC[pattern] || HAPTIC.tap) : pattern;
        if (navigator.vibrate)
            navigator.vibrate(p);
    }
    catch { }
}

function sortVisualLayers(nodes) {
    return nodes.sort((a, b) => {
        const za = Number.parseInt(getComputedStyle(a).zIndex, 10);
        const zb = Number.parseInt(getComputedStyle(b).zIndex, 10);
        const dz = (Number.isFinite(za) ? za : 0) - (Number.isFinite(zb) ? zb : 0);
        if (dz)
            return dz;
        if (a === b || !a.compareDocumentPosition)
            return 0;
        return (a.compareDocumentPosition(b) & 4) ? -1 : 1;
    });
}

function openBackdrops() {
    try {
        const open = [...document.querySelectorAll(".wpb-backdrop")].filter(bd => !bd.closest(".wpb-closing"));
        // DOM order is not the same thing as visual order in this app: confirmation dialogs, sheets and
        // drill-in surfaces intentionally use different z-index bands. Focus trapping therefore follows
        // the VISUALLY topmost backdrop, not merely the last one rendered.
        return sortVisualLayers(open);
    }
    catch {
        return [];
    }
}

function openDismissLayers() {
    try {
        return sortVisualLayers([...document.querySelectorAll(".wpb-backdrop, .wpb-dismiss-layer")].filter(el => !el.closest(".wpb-closing")));
    }
    catch {
        return [];
    }
}

const backdropFocus = new Map();

let activeBackdrop = null;

let dialogTitleId = 0;

function syncBackdropAria(forceReset = false) {
    try {
        const open = forceReset ? [] : openBackdrops();
        const top = open[open.length - 1] || null;
        const closed = [...backdropFocus.keys()].filter(bd => !open.includes(bd));
        const returnTo = closed.reverse().map(bd => backdropFocus.get(bd)).find(el => el?.isConnected && (!top || top.contains(el)) && !el.closest(".wpb-closing"));
        closed.forEach(bd => {
            backdropFocus.delete(bd);
            bd.removeAttribute("data-wpb-dialog");
            bd.removeAttribute("role");
            bd.removeAttribute("aria-modal");
        });
        open.forEach(bd => {
            if (!backdropFocus.has(bd)) {
                backdropFocus.set(bd, document.activeElement);
                bd.setAttribute("data-wpb-dialog", "1");
                bd.setAttribute("role", "dialog");
                const title = bd.querySelector("h1, h2, h3, [data-dialog-title]");
                if (title && !bd.hasAttribute("aria-label") && !bd.hasAttribute("aria-labelledby")) {
                    if (!title.id)
                        title.id = `wpb-dialog-title-${++dialogTitleId}`;
                    bd.setAttribute("aria-labelledby", title.id);
                }
            }
            if (bd !== top)
                bd.removeAttribute("aria-modal");
        });
        if (!top) {
            activeBackdrop = null;
            if (returnTo && returnTo !== document.body)
                returnTo.focus({ preventScroll: true });
            return;
        }
        if (top !== activeBackdrop) {
            activeBackdrop = top;
            const panel = top.firstElementChild;
            if (panel && !panel.hasAttribute("tabindex"))
                panel.setAttribute("tabindex", "-1");
            // Focus the panel, not an input: opening a sheet should not open the phone keyboard.
            if (!top.contains(document.activeElement))
                (returnTo || panel)?.focus({ preventScroll: true });
        }
        if (top.contains(document.activeElement))
            top.setAttribute("aria-modal", "true");
    }
    catch { }
}

function backdropTabStops(top) {
    return [...top.querySelectorAll('button, a[href], input, select, textarea, [tabindex], [contenteditable="true"], summary')].filter(el => {
        if (el.tabIndex < 0 || el.matches(":disabled") || el.closest('[hidden], [inert], [aria-hidden="true"], .wpb-closing'))
            return false;
        for (let p = el; p && p !== top; p = p.parentElement) {
            const css = getComputedStyle(p);
            if (css.display === "none" || css.visibility === "hidden")
                return false;
        }
        return true;
    }).sort((a, b) => (a.tabIndex || Infinity) - (b.tabIndex || Infinity));
}

function containBackdropFocus(e) {
    const open = openBackdrops(), top = open[open.length - 1];
    if (!top)
        return;
    const panel = top.firstElementChild;
    if (!panel)
        return;
    if (!panel.hasAttribute("tabindex"))
        panel.setAttribute("tabindex", "-1");
    if (e.type === "focusin") {
        if (!top.contains(e.target))
            panel.focus({ preventScroll: true });
        return;
    }
    if (e.key !== "Tab" || e.defaultPrevented || e.isComposing)
        return;
    const stops = backdropTabStops(top), first = stops[0], last = stops[stops.length - 1];
    const active = document.activeElement;
    if (!first || !stops.includes(active) || (e.shiftKey ? active === first : active === last)) {
        e.preventDefault();
        (e.shiftKey ? last || panel : first || panel).focus({ preventScroll: true });
    }
}

function closeTopBackdrop() {
    const open = openDismissLayers();
    const top = open[open.length - 1];
    if (!top)
        return false;
    try {
        // Full-screen destinations and intentionally non-tap-dismiss sheets can nominate the action that
        // System Back / Escape should mean. This keeps Back semantic without making the entire backdrop
        // clickable (e.g. exercise detail -> its Back button; readiness -> Skip).
        const semanticBack = top.querySelector?.("[data-wpb-system-back]");
        if (semanticBack)
            semanticBack.click();
        else
            top.dispatchEvent(new MouseEvent("click", { bubbles: true, cancelable: true }));
        return true;
    }
    catch {
        return false;
    }
}

function syncBackdropLock(forceUnlock) {
    try {
        const open = forceUnlock ? [] : openBackdrops();
        const has = open.length > 0;
        if (has) {
            open.forEach(bd => {
                let p = bd.parentElement;
                while (p && p !== document.body) {
                    const oy = getComputedStyle(p).overflowY;
                    if ((oy === "auto" || oy === "scroll") && p.scrollHeight > p.clientHeight + 1) {
                        if (!p.hasAttribute("data-wpb-lock")) {
                            p.setAttribute("data-wpb-lock", p.style.overflowY || "");
                            p.style.overflowY = "hidden";
                        }
                        break;
                    }
                    p = p.parentElement;
                }
            });
        }
        else {
            document.querySelectorAll("[data-wpb-lock]").forEach(el => {
                el.style.overflowY = el.getAttribute("data-wpb-lock") || "";
                el.removeAttribute("data-wpb-lock");
            });
        }
    }
    catch { }
}

const PART_LABEL = {
    chest: "Chest", lats: "Lats", upper_back: "Upper Back", shoulders: "Shoulders", biceps: "Biceps",
    triceps: "Triceps", quads: "Quads", hamstrings: "Hamstrings", glutes: "Glutes", lower_back: "Lower Back",
    calves: "Calves", abs: "Abs / Core", traps: "Traps", forearms: "Forearms", neck: "Neck",
    adductors: "Adductors", abductors: "Abductors"
};

const FOCUS_CAVEAT = { neck: "Rarely fits — most sessions have no room for direct neck work" };

const PART_ORDER = ["chest", "lats", "upper_back", "shoulders", "biceps", "triceps", "quads", "hamstrings", "glutes", "lower_back", "adductors", "abductors", "calves", "abs", "traps", "forearms", "neck"];

const VIEW_DEPTH = { home: 0, program: 1, library: 1, cycles: 1, compare: 1, progress: 1, settings: 1, wizard: 1, cycleWizard: 1, session: 2, cycleDetail: 2 };

const newGymId = () => `gym_${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`;

function normalizeGyms(list, activeId) {
    const arr = (Array.isArray(list) ? list : []).filter(g => g && typeof g === "object").map((g, i) => ({
        id: typeof g.id === "string" && g.id ? g.id : `gym_${i}`,
        name: String(g.name || `Gym ${i + 1}`).slice(0, 40),
        equipment: (Array.isArray(g.equipment) ? g.equipment : []).filter(e => ALL_EQUIP_IDS.includes(e)),
        // Heaviest implement available AT THIS GYM. null = no ceiling. Stored with the unit they were
        // typed in, so switching the app between kg and lb can't silently reinterpret "75" as 75 kg.
        limits: sanitizeLimits(g.limits),
        inventory: sanitizeInventory(g.inventory),
        limitUnit: g.limitUnit === "kg" || g.limitUnit === "lb" ? g.limitUnit : "kg"
    }));
    const gyms = arr.length ? arr : [
        { id: "gym_commercial", name: "Commercial gym", equipment: [...ALL_EQUIP_IDS] },
        { id: "gym_home", name: "Home gym", equipment: [...GYM_PRESETS[2].equipment] },
    ];
    const active = gyms.some(g => g.id === activeId) ? activeId : gyms[0].id;
    return { gyms, activeGymId: active };
}

const GYM_LIMIT_KEYS = [
    { key: "dumbbell", label: "Dumbbells", noun: "dumbbell", sub: "heaviest per hand", match: (eq) => eq.includes("dumbbell") },
    { key: "kettlebell", label: "Kettlebells", noun: "kettlebell", sub: "heaviest bell", match: (eq) => eq.includes("kettlebell") },
    { key: "barbell", label: "Barbell", noun: "barbell load", sub: "heaviest total load", match: (eq) => eq.includes("barbell") || eq.includes("ezbar") || eq.includes("smith") },
    { key: "machine", label: "Machines & cables", noun: "stack", sub: "heaviest stack", match: (eq) => isMachineLike(eq) },
];

const INVENTORY_KEYS = [
    { key: "dumbbell", label: "Dumbbells", sub: "per hand", match: (eq) => eq.includes("dumbbell") },
    { key: "kettlebell", label: "Kettlebells", sub: "each bell", match: (eq) => eq.includes("kettlebell") },
    { key: "machine", label: "Machines & cables", sub: "stack notches", match: (eq) => isMachineLike(eq) && !eq.includes("dumbbell") },
];

const RACK_PRESETS = {
    dumbbell: [
        { name: "Commercial rack", unit: "lb", v: [5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60, 65, 70, 75, 80, 85, 90, 95, 100] },
        { name: "Commercial (kg)", unit: "kg", v: [2.5, 5, 7.5, 10, 12.5, 15, 17.5, 20, 22.5, 25, 27.5, 30, 32.5, 35, 40, 45, 50] },
        { name: "Adjustable 5–52.5", unit: "lb", v: [5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 52.5] },
        { name: "Adjustable 2–24 kg", unit: "kg", v: [2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24] },
    ],
    kettlebell: [
        { name: "Standard (kg)", unit: "kg", v: [8, 12, 16, 20, 24, 28, 32, 36, 40] },
        { name: "Standard (lb)", unit: "lb", v: [18, 26, 35, 44, 53, 62, 70, 79] },
    ],
    machine: [
        { name: "5 lb stack", unit: "lb", v: Array.from({ length: 40 }, (_, i) => (i + 1) * 5) },
        { name: "5 kg stack", unit: "kg", v: Array.from({ length: 30 }, (_, i) => (i + 1) * 5) },
    ]
};

function sanitizeInventory(inv) {
    const out = {};
    INVENTORY_KEYS.forEach(({ key }) => {
        const list = Array.isArray(inv && inv[key]) ? inv[key] : null;
        if (!list)
            return;
        const clean = [...new Set(list.map(Number).filter(v => Number.isFinite(v) && v > 0))].sort((a, b) => a - b);
        if (clean.length)
            out[key] = clean;
    });
    return out;
}

function sanitizeLimits(l) {
    const out = {};
    GYM_LIMIT_KEYS.forEach(({ key }) => {
        const v = l && Number(l[key]);
        if (Number.isFinite(v) && v > 0)
            out[key] = v;
    });
    return out;
}

let GYM_LIMITS = null;

function setGymLimits(gym) {
    GYM_LIMITS = gym ? { limits: gym.limits || {}, inventory: gym.inventory || {}, unit: gym.limitUnit || gym.unit || "kg" } : null;
}

function gymRackFor(ex, unit) {
    if (!GYM_LIMITS || !ex)
        return null;
    const eq = ex.equip || [];
    if (!eq.length)
        return null;
    const hit = INVENTORY_KEYS.find(k => k.match(eq));
    const list = hit ? GYM_LIMITS.inventory[hit.key] : null;
    if (!list || !list.length)
        return null;
    const from = GYM_LIMITS.unit;
    return list.map(v => toUnit(v, from, unit)).sort((a, b) => a - b);
}

function gymCapFor(ex, unit) {
    if (!GYM_LIMITS || !ex)
        return Infinity;
    const eq = ex.equip || [];
    if (!eq.length)
        return Infinity; // bodyweight has no implement ceiling
    // An explicit rack is authoritative: its heaviest entry IS the ceiling, so the two can never disagree.
    const rack = gymRackFor(ex, unit);
    if (rack && rack.length)
        return rack[rack.length - 1];
    const hit = GYM_LIMIT_KEYS.find(k => k.match(eq));
    const v = hit ? GYM_LIMITS.limits[hit.key] : null;
    return v > 0 ? toUnit(v, GYM_LIMITS.unit, unit) : Infinity;
}

const gymById = (gyms, id) => (gyms || []).find(g => g.id === id) || (gyms || [])[0] || null;

const EQUIPMENT = [
    { id: "barbell", label: "Barbell", cat: "Bars" }, { id: "dumbbell", label: "Dumbbells", cat: "Free Weights" },
    { id: "bench", label: "Flat Bench", cat: "Benches & Racks" }, { id: "cable", label: "Cable Machine", cat: "Machines" },
    { id: "machine", label: "Selectorized Machines", cat: "Machines" }, { id: "smith", label: "Smith Machine", cat: "Machines" },
    { id: "ezbar", label: "EZ Curl Bar", cat: "Bars" }, { id: "pullup", label: "Pull-up Bar", cat: "Bodyweight" },
    { id: "dip", label: "Dip Station", cat: "Bodyweight" }, { id: "kettlebell", label: "Kettlebells", cat: "Free Weights" },
    { id: "bands", label: "Resistance Bands", cat: "Bands" },
    /* APPEND-ONLY BOUNDARY — CODE_EQUIP is index-based in every program code ever issued. New ids go
       BELOW; nothing above may be reordered or removed.
       "Selectorized Machines" was one bucket holding 44 distinct machines, so a gym with a leg press
       but no hack squat had no way to say so. These split the ones that most often differ between
       gyms. Existing users are granted every id their old coarse category implied (see the store
       migration and LEGACY_EQUIP_IMPLIES), so nobody's available exercises change on upgrade. */
    { id: "legpress", label: "Leg Press", cat: "Machines" },
    { id: "hacksquat", label: "Hack / V-Squat", cat: "Machines" },
    { id: "legext", label: "Leg Extension", cat: "Machines" },
    { id: "legcurl", label: "Leg Curl", cat: "Machines" },
    { id: "pecdeck", label: "Pec Deck", cat: "Machines" },
    { id: "machinerow", label: "Chest-Supported Row", cat: "Machines" },
    { id: "assisted", label: "Assisted Pull-up / Dip", cat: "Machines" },
    { id: "calfmachine", label: "Calf Raise Machine", cat: "Machines" },
    { id: "abduction", label: "Hip Abduction", cat: "Machines" },
    { id: "reversehyper", label: "Reverse Hyper", cat: "Benches & Racks" },
    { id: "ghd", label: "Glute-Ham Developer", cat: "Benches & Racks" },
    { id: "preacher", label: "Preacher Bench", cat: "Benches & Racks" },
    // Tranche 2 — the rest of the selectorized bucket. All cat:"Machines", so isMachineLike picks
    // them up automatically and no engine predicate needs touching.
    { id: "machinepress", label: "Chest Press Machine", cat: "Machines" },
    { id: "machineshoulder", label: "Shoulder Press Machine", cat: "Machines" },
    { id: "machinelatraise", label: "Lateral Raise Machine", cat: "Machines" },
    { id: "machinecurl", label: "Curl Machine", cat: "Machines" },
    { id: "machineext", label: "Triceps Machine", cat: "Machines" },
    { id: "beltsquat", label: "Belt Squat", cat: "Machines" },
    { id: "hipthrustmachine", label: "Hip Thrust Machine", cat: "Machines" },
    { id: "kickback", label: "Glute Kickback Machine", cat: "Machines" },
    { id: "machinecrunch", label: "Ab Crunch Machine", cat: "Machines" },
    { id: "machineshrug", label: "Shrug Machine", cat: "Machines" },
    { id: "machinepullover", label: "Pullover Machine", cat: "Machines" },
    { id: "adduction", label: "Hip Adduction", cat: "Machines" },
    // Tranche 3 — loadable bars that are NOT a straight barbell. Unlike the machine tranches these
    // cannot be split blind: `includes("barbell")` drives the fatigue model, axial-load cost, the
    // per-day bar-lift cap, the increment model and the progression style. Every one of those means
    // "a loaded bar" and now asks isBarLike; only the plate maths still means a specific bar, because
    // a trap bar weighs 25kg and a straight bar 20kg.
    { id: "trapbar", label: "Trap / Hex Bar", cat: "Bars" },
    { id: "safetybar", label: "Safety Squat Bar", cat: "Bars" },
    { id: "landmine", label: "Landmine", cat: "Bars" },
    /* Tranche 4 — benches. `bench` appears in NO engine predicate (unlike barbell), so this is a pure
       availability split: it only changes which exercises a gym can do. `bench` now means a flat bench;
       an adjustable one adds inclinebench. Most people who own a bench own an adjustable, few own a
       decline, and the preacher station finally has exercises pointing at it. */
    { id: "inclinebench", label: "Adjustable / Incline Bench", cat: "Benches & Racks" },
    { id: "declinebench", label: "Decline Bench", cat: "Benches & Racks" },
];

const EQUIP_CATS = ["Free Weights", "Bars", "Machines", "Benches & Racks", "Bands", "Bodyweight"];

const MACHINE_EQUIP = new Set(EQUIPMENT.filter(e => e.cat === "Machines").map(e => e.id));

const isMachineLike = (eq) => (eq || []).some(q => MACHINE_EQUIP.has(q));

const BAR_EQUIP = new Set(["barbell", "trapbar", "safetybar", "landmine"]);

const isBarLike = (eq) => (eq || []).some(q => BAR_EQUIP.has(q));

const ALL_EQUIP_IDS = EQUIPMENT.map(e => e.id);

const GYM_PRESETS = [
    { key: "commercial", name: "Commercial gym", equipment: [...ALL_EQUIP_IDS] },
    { key: "garage", name: "Garage gym", equipment: ["barbell", "dumbbell", "bench", "ezbar", "pullup", "bands", "preacher"] },
    { key: "home", name: "Home gym", equipment: ["dumbbell", "bench", "bands", "pullup"] },
    { key: "minimal", name: "Minimal kit", equipment: ["bands", "pullup"] },
    { key: "bodyweight", name: "Bodyweight only", equipment: [] },
];

const RAW = [
    ["bb-bench", "Barbell Bench Press", "chest", "compound", ["barbell", "bench", "rack"], 5, 10],
    ["inc-db-press", "Incline Dumbbell Press", "chest", "compound", ["dumbbell", "inclinebench"], 8, 12],
    ["inc-bb-bench", "Incline Barbell Press", "chest", "compound", ["barbell", "inclinebench", "rack"], 6, 10],
    ["low-inc-bb-bench", "Low-Incline Barbell Press", "chest", "compound", ["barbell", "inclinebench", "rack"], 6, 10],
    ["high-inc-bb-bench", "High-Incline Barbell Press", "chest", "compound", ["barbell", "inclinebench", "rack"], 6, 10],
    ["machine-press", "Machine Chest Press", "chest", "compound", ["machinepress"], 8, 12],
    ["db-bench", "Dumbbell Bench Press", "chest", "compound", ["dumbbell", "bench"], 8, 12],
    ["dips-chest", "Chest Dip", "chest", "compound", ["dip"], 6, 12],
    ["assisted-dip", "Assisted Dip", "chest", "compound", ["assisted"], 8, 12],
    ["cable-fly", "Cable Fly", "chest", "isolation", ["cable"], 12, 20],
    ["inc-cable-fly", "Low-to-High Cable Fly", "chest", "isolation", ["cable"], 12, 20],
    ["pec-deck", "Pec Deck", "chest", "isolation", ["pecdeck"], 12, 20],
    ["smith-bench", "Smith Machine Bench Press", "chest", "compound", ["smith", "bench"], 6, 10],
    ["db-fly", "Dumbbell Fly", "chest", "isolation", ["dumbbell", "bench"], 12, 18],
    ["pushup", "Push-Up", "chest", "compound", [], 10, 20],
    ["pullup", "Weighted Pull-Up", "lats", "compound", ["pullup"], 6, 12],
    ["lat-pulldown", "Lat Pulldown", "lats", "compound", ["cable"], 8, 12],
    ["chest-row", "Chest-Supported Row", "upper_back", "compound", ["machinerow"], 8, 12],
    ["bb-row", "Barbell Row", "upper_back", "compound", ["barbell"], 6, 10],
    ["seated-row", "Seated Cable Row", "upper_back", "compound", ["cable"], 8, 12],
    ["db-row", "One-Arm Dumbbell Row", "upper_back", "compound", ["dumbbell", "bench"], 8, 12],
    ["tbar-row", "T-Bar Row", "upper_back", "compound", ["barbell"], 8, 12],
    ["machine-row", "Machine Row", "upper_back", "compound", ["machinerow"], 8, 12],
    ["deadlift", "Deadlift", "lower_back", "compound", ["barbell"], 4, 6],
    ["straight-pulldown", "Straight-Arm Pulldown", "lats", "isolation", ["cable"], 12, 20],
    ["inv-row", "Inverted Row", "upper_back", "compound", [], 8, 15],
    ["superman", "Superman Hold", "lower_back", "isolation", [], 12, 20],
    ["ohp", "Overhead Press", "shoulders", "compound", ["barbell", "rack"], 5, 10],
    ["db-shoulder", "Dumbbell Shoulder Press", "shoulders", "compound", ["dumbbell"], 8, 12],
    ["lat-raise", "Dumbbell Lateral Raise", "shoulders", "isolation", ["dumbbell"], 12, 20],
    ["cable-lat-raise", "Cable Lateral Raise", "shoulders", "isolation", ["cable"], 12, 20],
    ["band-lateral-raise", "Band Lateral Raise", "shoulders", "isolation", ["bands"], 15, 30, null, "shoulder-abduction"],
    ["machine-shoulder", "Machine Shoulder Press", "shoulders", "compound", ["machineshoulder"], 8, 12],
    ["rear-fly", "Rear Delt Fly", "shoulders", "isolation", ["dumbbell"], 12, 20],
    ["reverse-pec", "Reverse Pec Deck", "shoulders", "isolation", ["pecdeck"], 12, 20],
    ["face-pull", "Face Pull", "shoulders", "isolation", ["cable"], 12, 20],
    ["arnold", "Arnold Press", "shoulders", "compound", ["dumbbell"], 8, 12],
    ["front-raise", "Front Raise", "shoulders", "isolation", ["dumbbell"], 10, 15],
    ["pike-pushup", "Pike Push-Up", "shoulders", "compound", [], 8, 15],
    ["inc-curl", "Incline Dumbbell Curl", "biceps", "isolation", ["dumbbell", "inclinebench"], 8, 12],
    ["db-curl", "Dumbbell Curl", "biceps", "isolation", ["dumbbell"], 8, 12],
    ["bayesian-curl", "Bayesian Cable Curl", "biceps", "isolation", ["cable"], 10, 15],
    ["ez-curl", "EZ-Bar Curl", "biceps", "isolation", ["ezbar"], 8, 12],
    ["preacher", "Preacher Curl", "biceps", "isolation", ["ezbar", "preacher"], 10, 15],
    ["cable-curl", "Cable Curl", "biceps", "isolation", ["cable"], 10, 15],
    ["hammer", "Hammer Curl", "biceps", "isolation", ["dumbbell"], 10, 15, "brachialis"],
    ["bb-curl", "Barbell Curl", "biceps", "isolation", ["barbell"], 8, 12],
    ["chinup", "Chin-Up", "lats", "compound", ["pullup"], 6, 12],
    ["assisted-chinup", "Assisted Chin-Up", "lats", "compound", ["assisted"], 8, 12],
    ["band-curl", "Band Curl", "biceps", "isolation", ["bands"], 12, 20],
    ["oh-cable-ext", "Overhead Cable Extension", "triceps", "isolation", ["cable"], 10, 15],
    ["pushdown", "Tricep Pushdown (Bar)", "triceps", "isolation", ["cable"], 10, 15],
    ["rope-pushdown", "Rope Pushdown", "triceps", "isolation", ["cable"], 10, 15],
    ["ez-pushdown", "EZ-Bar Pushdown", "triceps", "isolation", ["cable"], 10, 15],
    ["cgbp", "Close-Grip Bench Press", "triceps", "compound", ["barbell", "bench", "rack"], 6, 10],
    ["skullcrusher", "Skull Crusher", "triceps", "isolation", ["ezbar", "bench"], 8, 12],
    ["db-oh-ext", "DB Overhead Extension", "triceps", "isolation", ["dumbbell"], 10, 15],
    ["dips-tri", "Triceps Dip", "triceps", "compound", ["dip"], 8, 12],
    ["diamond-pushup", "Diamond Push-Up", "triceps", "compound", [], 10, 20],
    ["bench-dip", "Bench Dip", "triceps", "compound", [], 10, 20],
    ["back-squat", "Back Squat", "quads", "compound", ["barbell", "rack"], 5, 8],
    ["hack-squat", "Hack Squat", "quads", "compound", ["hacksquat"], 8, 12],
    ["leg-press", "Leg Press", "quads", "compound", ["legpress"], 8, 15],
    ["front-squat", "Front Squat", "quads", "compound", ["barbell", "rack"], 5, 8],
    ["bulgarian", "Bulgarian Split Squat", "quads", "compound", ["dumbbell"], 8, 12],
    ["leg-ext", "Leg Extension", "quads", "isolation", ["legext"], 12, 20],
    ["smith-squat", "Smith Machine Squat", "quads", "compound", ["smith"], 8, 12],
    ["goblet", "Goblet Squat", "quads", "compound", ["dumbbell"], 8, 15],
    ["walking-lunge", "Walking Lunge", "quads", "compound", ["dumbbell"], 10, 15],
    ["bw-squat", "Bodyweight Squat", "quads", "compound", [], 15, 25],
    ["bw-lunge", "Bodyweight Lunge", "quads", "compound", [], 12, 20],
    ["bw-bulgarian", "BW Bulgarian Split Squat", "quads", "compound", [], 10, 20],
    ["rdl", "Romanian Deadlift", "hamstrings", "compound", ["barbell"], 6, 10],
    ["lying-curl", "Lying Leg Curl", "hamstrings", "isolation", ["legcurl"], 10, 15],
    ["seated-curl", "Seated Leg Curl", "hamstrings", "isolation", ["legcurl"], 10, 15],
    ["db-rdl", "Dumbbell RDL", "hamstrings", "compound", ["dumbbell"], 8, 12],
    ["good-morning", "Good Morning", "hamstrings", "compound", ["barbell", "rack"], 8, 12],
    ["slrdl", "Single-Leg RDL", "hamstrings", "compound", [], 10, 15],
    ["nordic", "Nordic Curl", "hamstrings", "isolation", [], 5, 10],
    ["hip-thrust", "Barbell Hip Thrust", "glutes", "compound", ["barbell", "bench"], 8, 12],
    ["db-hip-thrust", "Dumbbell Hip Thrust", "glutes", "compound", ["dumbbell", "bench"], 10, 15],
    ["cable-kickback", "Cable Kickback", "glutes", "isolation", ["cable"], 12, 20],
    ["sl-hip-thrust", "Single-Leg Hip Thrust", "glutes", "compound", [], 10, 15],
    ["glute-bridge", "Glute Bridge", "glutes", "compound", [], 12, 20],
    ["sumo-dl", "Sumo Deadlift", "glutes", "compound", ["barbell"], 4, 8],
    ["standing-calf", "Standing Calf Raise", "calves", "isolation", ["calfmachine"], 8, 15],
    ["seated-calf", "Seated Calf Raise", "calves", "isolation", ["calfmachine"], 12, 20],
    ["smith-calf", "Smith Calf Raise", "calves", "isolation", ["smith"], 10, 15],
    ["db-calf", "Dumbbell Calf Raise", "calves", "isolation", ["dumbbell"], 12, 20],
    ["bw-calf", "Bodyweight Calf Raise", "calves", "isolation", [], 15, 25],
    ["cable-crunch", "Cable Crunch", "abs", "isolation", ["cable"], 10, 20],
    ["hanging-raise", "Hanging Leg Raise", "abs", "isolation", ["pullup"], 8, 15],
    ["leg-raise", "Lying Leg Raise", "abs", "isolation", [], 10, 20],
    ["crunch", "Crunch", "abs", "isolation", [], 12, 25],
    ["dumbbell-crunch", "Dumbbell Crunch", "abs", "isolation", ["dumbbell"], 10, 20],
    ["bicycle", "Bicycle Crunch", "abs", "isolation", [], 15, 25],
    ["russian-twist", "Russian Twist", "abs", "isolation", [], 15, 25, "obliques"],
    ["plank", "Plank", "abs", "isolation", [], 12, 20],
    ["cable-woodchop", "Cable Woodchopper", "abs", "isolation", ["cable"], 12, 20, "obliques"],
    ["oblique-crunch", "Oblique Crunch", "abs", "isolation", [], 15, 25, "obliques"],
    ["hanging-oblique", "Hanging Oblique Raise", "abs", "isolation", ["pullup"], 10, 15, "obliques"],
    ["serratus-pushup", "Serratus Push-Up", "abs", "isolation", [], 12, 20, "serratus"],
    ["cable-serratus", "Cable Serratus Pull", "abs", "isolation", ["cable"], 12, 20, "serratus"],
    ["banded-tib-raise", "Banded Tibialis Raise", "calves", "isolation", ["bands"], 15, 25, "tibialis"],
    ["bb-shrug", "Barbell Shrug", "traps", "isolation", ["barbell"], 10, 15],
    ["db-shrug", "Dumbbell Shrug", "traps", "isolation", ["dumbbell"], 12, 20],
    ["cable-shrug", "Cable Shrug", "traps", "isolation", ["cable"], 12, 20],
    ["wrist-curl", "Wrist Curl", "forearms", "isolation", ["dumbbell"], 12, 20, "wrist_flexors"],
    ["reverse-curl", "Reverse Curl", "biceps", "isolation", ["ezbar"], 10, 15, "brachialis"],
    ["farmers", "Farmer's Carry", "forearms", "compound", ["dumbbell"], 10, 15, "grip"],
    // ---- extended library (more variety for generation, swaps & manual add) ----
    // chest
    ["decline-bench", "Decline Barbell Press", "chest", "compound", ["barbell", "declinebench", "rack"], 6, 10],
    ["decline-db-press", "Decline Dumbbell Press", "chest", "compound", ["dumbbell", "declinebench"], 8, 12],
    ["incline-machine-press", "Incline Machine Press", "chest", "compound", ["machinepress"], 8, 12],
    ["floor-press", "Floor Press", "chest", "compound", ["barbell"], 6, 10],
    ["machine-dip", "Machine Chest Dip", "chest", "compound", ["machinepress"], 8, 12],
    ["high-cable-fly", "High-to-Low Cable Fly", "chest", "isolation", ["cable"], 12, 20],
    ["incline-pushup", "Incline Push-Up", "chest", "compound", [], 12, 20],
    ["decline-pushup", "Decline Push-Up", "chest", "compound", [], 10, 20],
    ["cable-press", "Standing Cable Press", "chest", "compound", ["cable"], 10, 15],
    // back
    ["pendlay-row", "Pendlay Row", "upper_back", "compound", ["barbell"], 5, 8],
    ["meadows-row", "Meadows Row", "upper_back", "compound", ["landmine"], 8, 12],
    ["seal-row", "Seal Row", "upper_back", "compound", ["barbell", "bench"], 8, 12],
    ["inc-db-row", "Incline Dumbbell Row", "upper_back", "compound", ["dumbbell", "inclinebench"], 8, 12],
    ["wide-pulldown", "Wide-Grip Lat Pulldown", "lats", "compound", ["cable"], 8, 12],
    ["close-pulldown", "Close-Grip Pulldown", "lats", "compound", ["cable"], 8, 12],
    ["neutral-pulldown", "Neutral-Grip Pulldown", "lats", "compound", ["cable"], 8, 12],
    ["one-arm-pulldown", "Single-Arm Lat Pulldown", "lats", "compound", ["cable"], 10, 15],
    ["assisted-pullup", "Assisted Pull-Up", "lats", "compound", ["assisted"], 8, 12],
    ["rack-pull", "Rack Pull", "lower_back", "compound", ["barbell", "rack"], 4, 8],
    ["kroc-row", "Kroc Row", "upper_back", "compound", ["dumbbell"], 10, 20],
    ["db-pullover", "Dumbbell Pullover", "lats", "isolation", ["dumbbell", "bench"], 10, 15],
    ["floor-db-pullover", "Floor Dumbbell Pullover", "lats", "isolation", ["dumbbell"], 10, 15],
    ["bent-db-pullover", "Bent-Over Dumbbell Pullover", "lats", "isolation", ["dumbbell"], 12, 15],
    // shoulders
    ["seated-ohp", "Seated Barbell Press", "shoulders", "compound", ["barbell", "bench", "rack"], 6, 10],
    ["seated-db-press", "Seated Dumbbell Press", "shoulders", "compound", ["dumbbell", "bench"], 8, 12],
    ["smith-ohp", "Smith Machine Shoulder Press", "shoulders", "compound", ["smith"], 8, 12],
    ["landmine-press", "Landmine Press", "shoulders", "compound", ["landmine"], 8, 12],
    ["machine-lat-raise", "Machine Lateral Raise", "shoulders", "isolation", ["machinelatraise"], 12, 20],
    ["leaning-lat-raise", "Leaning Cable Lateral Raise", "shoulders", "isolation", ["cable"], 12, 20],
    ["cable-rear-fly", "Cable Rear Delt Fly", "shoulders", "isolation", ["cable"], 12, 20],
    ["cable-front-raise", "Cable Front Raise", "shoulders", "isolation", ["cable"], 10, 15],
    ["upright-row", "Upright Row", "shoulders", "compound", ["barbell"], 8, 12],
    ["db-upright-row", "Dumbbell Upright Row", "shoulders", "compound", ["dumbbell"], 10, 15],
    // biceps
    ["concentration-curl", "Concentration Curl", "biceps", "isolation", ["dumbbell"], 10, 15],
    ["spider-curl", "Spider Curl", "biceps", "isolation", ["dumbbell", "inclinebench"], 10, 15],
    ["machine-curl", "Machine Curl", "biceps", "isolation", ["machinecurl"], 10, 15],
    ["rope-hammer-curl", "Cable Rope Hammer Curl", "biceps", "isolation", ["cable"], 10, 15, "brachialis"],
    ["cross-hammer", "Cross-Body Hammer Curl", "biceps", "isolation", ["dumbbell"], 10, 15, "brachialis"],
    ["zottman-curl", "Zottman Curl", "biceps", "isolation", ["dumbbell"], 10, 15, "brachialis"],
    ["drag-curl", "Drag Curl", "biceps", "isolation", ["barbell"], 10, 15],
    // triceps
    ["v-bar-pushdown", "V-Bar Pushdown", "triceps", "isolation", ["cable"], 10, 15],
    ["straight-bar-pushdown", "Straight-Bar Pushdown", "triceps", "isolation", ["cable"], 10, 15],
    ["dual-rope-pushdown", "Dual-Rope Pushdown", "triceps", "isolation", ["cable"], 10, 15],
    ["single-pushdown", "Single-Arm Pushdown", "triceps", "isolation", ["cable"], 12, 20],
    ["jm-press", "JM Press", "triceps", "compound", ["barbell", "bench", "rack"], 6, 10],
    ["tricep-kickback", "Tricep Kickback", "triceps", "isolation", ["dumbbell"], 12, 20],
    ["machine-ext", "Machine Triceps Extension", "triceps", "isolation", ["machineext"], 10, 15],
    ["ez-oh-ext", "EZ-Bar Overhead Extension", "triceps", "isolation", ["ezbar"], 10, 15],
    // quads
    ["pendulum-squat", "Pendulum Squat", "quads", "compound", ["hacksquat"], 8, 12],
    ["belt-squat", "Belt Squat", "quads", "compound", ["beltsquat"], 10, 15],
    ["box-squat", "Box Squat", "quads", "compound", ["barbell", "rack"], 5, 8],
    ["split-squat", "Split Squat", "quads", "compound", ["dumbbell"], 8, 12],
    ["step-up", "Dumbbell Step-Up", "quads", "compound", ["dumbbell"], 10, 15],
    ["reverse-lunge", "Reverse Lunge", "quads", "compound", ["dumbbell"], 10, 15],
    ["single-leg-press", "Single-Leg Press", "quads", "compound", ["legpress"], 10, 15],
    ["sissy-squat", "Sissy Squat", "quads", "isolation", [], 10, 20],
    // hamstrings
    ["stiff-deadlift", "Stiff-Leg Deadlift", "hamstrings", "compound", ["barbell"], 6, 10],
    ["standing-curl", "Standing Leg Curl", "hamstrings", "isolation", ["legcurl"], 10, 15],
    ["ghr", "Glute-Ham Raise", "hamstrings", "compound", [], 6, 12],
    ["pull-through", "Cable Pull-Through", "hamstrings", "compound", ["cable"], 12, 20],
    ["kb-swing", "Kettlebell Swing", "hamstrings", "compound", ["kettlebell"], 12, 20],
    // glutes
    ["machine-hip-thrust", "Machine Hip Thrust", "glutes", "compound", ["hipthrustmachine"], 8, 12],
    ["smith-hip-thrust", "Smith Machine Hip Thrust", "glutes", "compound", ["smith", "bench"], 8, 12],
    ["hip-abduction", "Hip Abduction Machine", "abductors", "isolation", ["abduction"], 12, 20],
    ["cable-abduction", "Cable Hip Abduction", "abductors", "isolation", ["cable"], 12, 20],
    ["reverse-hyper", "Reverse Hyperextension", "glutes", "isolation", ["reversehyper"], 10, 15],
    ["frog-pump", "Frog Pump", "glutes", "isolation", [], 15, 25],
    // calves
    ["leg-press-calf", "Leg Press Calf Raise", "calves", "isolation", ["legpress"], 10, 15],
    ["donkey-calf", "Donkey Calf Raise", "calves", "isolation", ["calfmachine"], 12, 20],
    ["single-calf", "Single-Leg Calf Raise", "calves", "isolation", ["dumbbell"], 12, 20],
    // abs
    ["ab-wheel", "Ab Wheel Rollout", "abs", "isolation", [], 8, 15],
    ["hanging-knee", "Hanging Knee Raise", "abs", "isolation", ["pullup"], 10, 20],
    ["machine-crunch", "Machine Crunch", "abs", "isolation", ["machinecrunch"], 12, 20],
    ["decline-situp", "Decline Sit-Up", "abs", "isolation", ["declinebench"], 12, 20],
    ["toes-to-bar", "Toes to Bar", "abs", "isolation", ["pullup"], 8, 15],
    ["side-plank", "Side Plank", "abs", "isolation", [], 12, 20, "obliques"],
    ["pallof", "Pallof Press", "abs", "isolation", ["cable"], 12, 20],
    ["woodchopper", "Low-to-High Cable Chop", "abs", "isolation", ["cable"], 12, 20, "obliques"],
    ["v-up", "V-Up", "abs", "isolation", [], 12, 20],
    ["mountain-climber", "Mountain Climber", "abs", "isolation", [], 20, 40],
    // traps
    ["smith-shrug", "Smith Machine Shrug", "traps", "isolation", ["smith"], 12, 20],
    ["machine-shrug", "Machine Shrug", "traps", "isolation", ["machineshrug"], 12, 20],
    ["behind-shrug", "Behind-the-Back Shrug", "traps", "isolation", ["barbell", "rack"], 12, 20],
    // forearms
    ["reverse-wrist-curl", "Reverse Wrist Curl", "forearms", "isolation", ["dumbbell"], 12, 20, "wrist_extensors"],
    ["behind-wrist-curl", "Behind-Back Wrist Curl", "forearms", "isolation", ["barbell"], 15, 20, "wrist_flexors"],
    ["wrist-roller", "Wrist Roller", "forearms", "isolation", [], 10, 15, "wrist_extensors"],
    /* RADIAL / ULNAR DEVIATION — the third wrist direction, and until now the library had none of it.
       v574 measured the coverage problem and could only fix two thirds of it: flexion and extension had
       movements to select, "side" had nothing, so no generation rule could reach it and check 15 of
       gates/extensions.mjs pinned the gap rather than leaving it to be rediscovered.
       Deviation needs a torque ACROSS the hand, which takes either an offset lever (a hammer, a sledge,
       a bar held at one end) or a cable/band pulling perpendicular to the arc. A symmetric dumbbell held
       in the middle produces no deviation moment at all, which is why there is no dumbbell entry here.
       The hammer variants carry NO equipment id for the same reason the wrist roller and the hand
       gripper do not: a hammer is a dedicated implement the gym editor does not model, and minting an id
       for it would append to CODE_EQUIP, which is index-based and shared by every program code ever
       issued. Rep ranges sit with the other wrist work — small, endurance-biased musculature moving
       through roughly 50 degrees, not a loadable prime mover. */
    ["hammer-radial-dev", "Hammer Radial Wrist Deviation", "forearms", "isolation", [], 12, 20, "wrist_deviators"],
    ["hammer-ulnar-dev", "Hammer Ulnar Wrist Deviation", "forearms", "isolation", [], 12, 20, "wrist_deviators"],
    ["cable-radial-dev", "Cable Radial Wrist Deviation", "forearms", "isolation", ["cable"], 12, 20, "wrist_deviators"],
    ["band-ulnar-dev", "Band Ulnar Wrist Deviation", "forearms", "isolation", ["bands"], 15, 25, "wrist_deviators"],
    ["plate-pinch", "Plate Pinch Hold", "forearms", "isolation", ["dumbbell"], 10, 15, "grip"],
    // ---- granular variations (grip / angle / unilateral) ----
    ["push-press", "Barbell Push Press", "shoulders", "compound", ["barbell", "rack"], 4, 8],
    ["side-lying-raise", "Side-Lying Lateral Raise", "shoulders", "isolation", ["dumbbell", "bench"], 12, 20],
    ["single-cable-raise", "Single-Arm Cable Lateral Raise", "shoulders", "isolation", ["cable"], 12, 20],
    ["underhand-row", "Underhand-Grip Barbell Row", "upper_back", "compound", ["barbell"], 8, 12],
    ["underhand-pulldown", "Underhand-Grip Lat Pulldown", "lats", "compound", ["cable"], 8, 12],
    ["single-cable-row", "Single-Arm Cable Row", "upper_back", "compound", ["cable"], 10, 15],
    ["close-cable-row", "Close-Grip Seated Cable Row", "lats", "compound", ["cable"], 8, 12],
    ["wide-cable-row", "Wide-Grip Seated Cable Row", "upper_back", "compound", ["cable"], 10, 15],
    ["underhand-cable-row", "Underhand Seated Cable Row", "lats", "compound", ["cable"], 8, 12],
    ["rope-cable-row", "Rope Seated Cable Row", "upper_back", "compound", ["cable"], 10, 15],
    ["dual-cable-row", "Dual-Handle Seated Cable Row", "upper_back", "compound", ["cable"], 8, 12],
    ["wide-machine-row", "Wide-Grip Machine Row", "upper_back", "compound", ["machinerow"], 10, 15],
    ["neutral-machine-row", "Neutral-Grip Machine Row", "upper_back", "compound", ["machinerow"], 8, 12],
    ["wide-tbar-row", "Wide-Grip T-Bar Row", "upper_back", "compound", ["barbell"], 8, 12],
    ["kelso-shrug", "Kelso Shrug", "traps", "isolation", ["machineshrug"], 12, 20],
    ["single-cable-curl", "Single-Arm Cable Curl", "biceps", "isolation", ["cable"], 10, 15],
    ["incline-hammer", "Incline Hammer Curl", "biceps", "isolation", ["dumbbell", "inclinebench"], 10, 15, "brachialis"],
    // neck
    ["neck-extension", "Weighted Neck Extension", "neck", "isolation", [], 12, 20],
    ["neck-curl", "Weighted Neck Curl", "neck", "isolation", [], 12, 20],
    ["neck-harness", "Neck Harness Raise", "neck", "isolation", [], 12, 20],
    ["neck-lateral", "Lateral Neck Raise", "neck", "isolation", [], 12, 20],
    // --- expansion: additional staples across thinner categories ---
    ["cable-crossover", "Cable Crossover", "chest", "isolation", ["cable"], 12, 20],
    ["squeeze-press", "Dumbbell Squeeze Press", "chest", "compound", ["dumbbell", "bench"], 10, 15],
    ["tate-press", "Tate Press", "triceps", "isolation", ["dumbbell"], 10, 15],
    ["z-press", "Z Press", "shoulders", "compound", ["barbell", "rack"], 5, 10],
    ["cable-y-raise", "Cable Y-Raise", "shoulders", "isolation", ["cable"], 12, 20],
    ["bstance-hip-thrust", "B-Stance Hip Thrust", "glutes", "compound", ["dumbbell", "bench"], 10, 15],
    ["curtsy-lunge", "Curtsy Lunge", "glutes", "compound", ["dumbbell"], 10, 15],
    ["lateral-walk", "Banded Lateral Walk", "abductors", "isolation", ["bands"], 15, 25],
    ["band-leg-curl", "Banded Leg Curl", "hamstrings", "isolation", ["bands"], 12, 20],
    ["jefferson-curl", "Jefferson Curl", "hamstrings", "isolation", ["dumbbell"], 8, 12],
    ["bw-single-calf", "Single-Leg BW Calf Raise", "calves", "isolation", [], 15, 25],
    ["trap-bar-shrug", "Trap Bar Shrug", "traps", "isolation", ["trapbar"], 10, 15],
    ["power-shrug", "Power Shrug", "traps", "isolation", ["barbell"], 6, 10],
    ["dead-hang", "Dead Hang", "forearms", "isolation", ["pullup"], 10, 15, "grip"],
    ["suitcase-carry", "Suitcase Carry", "forearms", "compound", ["dumbbell"], 10, 15, "grip"],
    ["dead-bug", "Dead Bug", "abs", "isolation", [], 10, 20],
    ["hollow-hold", "Hollow Body Hold", "abs", "isolation", [], 15, 30],
    ["dragon-flag", "Dragon Flag", "abs", "isolation", [], 5, 12],
    ["cable-side-bend", "Cable Side Bend", "abs", "isolation", ["cable"], 12, 20],
    ["copenhagen", "Copenhagen Plank", "adductors", "isolation", [], 10, 20],
    ["cyclist-squat", "Cyclist Squat", "quads", "compound", ["barbell", "rack"], 8, 12],
    ["spanish-squat", "Spanish Squat", "quads", "isolation", ["bands"], 12, 20],
    // ---- expansion 2: deeper variety per muscle (angles, implements, unilateral) ----
    // chest
    ["incline-cable-press", "Incline Cable Press", "chest", "compound", ["cable"], 10, 15],
    ["smith-incline", "Smith Machine Incline Press", "chest", "compound", ["smith", "inclinebench"], 6, 10],
    ["low-incline-db", "Low-Incline Dumbbell Press", "chest", "compound", ["dumbbell", "inclinebench"], 8, 12],
    ["band-pushup", "Banded Push-Up", "chest", "compound", ["bands"], 10, 20],
    ["svend-press", "Svend Press", "chest", "isolation", [], 12, 20],
    // back
    ["yates-row", "Yates Row", "upper_back", "compound", ["barbell"], 8, 12],
    ["cs-db-row", "Chest-Supported Dumbbell Row", "upper_back", "compound", ["dumbbell", "bench"], 8, 12],
    ["cable-pullover", "Cable Lat Pullover", "lats", "isolation", ["cable"], 12, 20],
    ["machine-pullover", "Machine Pullover", "lats", "isolation", ["machinepullover"], 10, 15],
    ["trap-bar-deadlift", "Trap Bar Deadlift", "lower_back", "compound", ["trapbar"], 4, 8],
    ["snatch-deadlift", "Snatch-Grip Deadlift", "lower_back", "compound", ["barbell"], 4, 6],
    ["renegade-row", "Renegade Row", "upper_back", "compound", ["dumbbell"], 8, 12],
    ["band-pulldown", "Banded Lat Pulldown", "lats", "compound", ["bands"], 12, 20],
    // shoulders
    ["cable-upright-row", "Cable Upright Row", "shoulders", "compound", ["cable"], 10, 15],
    ["bradford-press", "Bradford Press", "shoulders", "compound", ["barbell", "rack"], 8, 12],
    ["viking-press", "Viking Press", "shoulders", "compound", ["machineshoulder"], 8, 12],
    ["rear-band-pull-apart", "Band Pull-Apart", "shoulders", "isolation", ["bands"], 15, 25],
    ["plate-front-raise", "Plate Front Raise", "shoulders", "isolation", ["dumbbell"], 12, 20],
    ["prone-y-raise", "Prone Y-Raise", "shoulders", "isolation", ["inclinebench"], 12, 20],
    // biceps
    ["ez-cable-curl", "EZ-Bar Cable Curl", "biceps", "isolation", ["cable"], 10, 15],
    ["waiter-curl", "Waiter Curl", "biceps", "isolation", ["dumbbell"], 10, 15],
    ["seated-incline-curl", "Seated Incline Cable Curl", "biceps", "isolation", ["cable", "inclinebench"], 10, 15],
    ["rev-grip-curl", "Reverse-Grip Barbell Curl", "biceps", "isolation", ["barbell"], 10, 15, "brachialis"],
    ["pinwheel-curl", "Pinwheel Curl", "biceps", "isolation", ["dumbbell"], 10, 15, "brachialis"],
    // triceps
    ["underhand-pushdown", "Reverse-Grip Pushdown", "triceps", "isolation", ["cable"], 12, 20],
    ["pjr-pullover", "PJR Pullover", "triceps", "isolation", ["ezbar", "bench"], 8, 12],
    ["lying-db-ext", "Lying Dumbbell Extension", "triceps", "isolation", ["dumbbell", "bench"], 10, 15],
    ["cable-tri-kickback", "Cable Triceps Kickback", "triceps", "isolation", ["cable"], 12, 20],
    ["california-press", "California Press", "triceps", "compound", ["ezbar", "bench"], 8, 12],
    // quads
    ["zercher-squat", "Zercher Squat", "quads", "compound", ["barbell", "rack"], 6, 10],
    ["lm-squat", "Landmine Squat", "quads", "compound", ["landmine"], 8, 12],
    ["heels-up-goblet", "Heels-Elevated Goblet Squat", "quads", "compound", ["dumbbell"], 10, 15],
    ["wall-sit", "Wall Sit", "quads", "isolation", [], 20, 40],
    ["pistol-squat", "Pistol Squat", "quads", "compound", [], 5, 12],
    ["v-squat", "V-Squat Machine", "quads", "compound", ["hacksquat"], 8, 12],
    // hamstrings
    ["cable-rdl", "Cable Romanian Deadlift", "hamstrings", "compound", ["cable"], 10, 15],
    ["slider-curl", "Slider Leg Curl", "hamstrings", "isolation", [], 8, 15],
    ["band-good-morning", "Banded Good Morning", "hamstrings", "compound", ["bands"], 12, 20],
    ["single-lying-curl", "Single-Leg Lying Curl", "hamstrings", "isolation", ["legcurl"], 10, 15],
    // glutes
    ["kas-glute-bridge", "Kas Glute Bridge", "glutes", "compound", ["barbell", "bench"], 10, 15],
    ["band-hip-thrust", "Banded Hip Thrust", "glutes", "compound", ["bands"], 15, 25],
    ["glute-kickback-machine", "Glute Kickback Machine", "glutes", "isolation", ["kickback"], 12, 20],
    ["sumo-squat", "Sumo Squat", "glutes", "compound", ["dumbbell"], 10, 15],
    ["step-through-lunge", "Step-Through Lunge", "glutes", "compound", ["dumbbell"], 10, 15],
    // calves
    ["hack-calf", "Hack Squat Calf Raise", "calves", "isolation", ["hacksquat"], 10, 15],
    ["tibialis-raise", "Tibialis Raise", "calves", "isolation", [], 15, 25, "tibialis"],
    ["single-leg-press-calf", "Single-Leg Press Calf Raise", "calves", "isolation", ["legpress"], 12, 20],
    // abs
    ["reverse-crunch", "Reverse Crunch", "abs", "isolation", [], 12, 20],
    ["cable-reverse-crunch", "Cable Reverse Crunch", "abs", "isolation", ["cable"], 12, 20],
    ["l-sit", "L-Sit Hold", "abs", "isolation", [], 10, 20],
    ["weighted-plank", "Weighted Plank", "abs", "isolation", ["dumbbell"], 20, 40],
    ["windshield-wiper", "Hanging Windshield Wiper", "abs", "isolation", ["pullup"], 8, 15],
    ["stir-pot", "Stir the Pot", "abs", "isolation", [], 10, 20],
    // traps
    ["incline-shrug", "Incline Dumbbell Shrug", "traps", "isolation", ["dumbbell", "inclinebench"], 12, 20],
    ["cable-face-shrug", "Cable Face Shrug", "traps", "isolation", ["cable"], 12, 20],
    // forearms
    ["cable-wrist-curl", "Cable Wrist Curl", "forearms", "isolation", ["cable"], 12, 20, "wrist_flexors"],
    ["gripper", "Hand Gripper", "forearms", "isolation", [], 10, 20, "grip"],
    ["towel-hang", "Towel Dead Hang", "forearms", "isolation", ["pullup"], 10, 15, "grip"],
    // ---- expansion 3: commonly-expected staples ----
    ["bw-pullup", "Pull-Up", "lats", "compound", ["pullup"], 5, 12],
    ["back-ext-45", "45° Back Extension", "lower_back", "compound", ["bench"], 12, 20],
    ["situp", "Sit-Up", "abs", "isolation", [], 15, 25],
    // ---- adductors (inner thigh) ----
    ["adduction-machine", "Hip Adduction Machine", "adductors", "isolation", ["adduction"], 12, 20],
    ["cable-adduction", "Cable Hip Adduction", "adductors", "isolation", ["cable"], 12, 20],
    ["band-adduction", "Banded Hip Adduction", "adductors", "isolation", ["bands"], 15, 25],
    ["cossack-squat", "Cossack Squat", "adductors", "compound", ["dumbbell"], 10, 15],
    ["adductor-sumo", "Wide-Stance Sumo Squat", "adductors", "compound", ["dumbbell"], 10, 15],
    // ---- abductors (hip / glute medius) ----
    ["standing-cable-abduction", "Standing Cable Hip Abduction", "abductors", "isolation", ["cable"], 12, 20],
    ["side-lying-abduction", "Side-Lying Hip Abduction", "abductors", "isolation", [], 15, 25],
    ["band-abduction", "Seated Banded Abduction", "abductors", "isolation", ["bands"], 15, 25],
    // ---- expansion 4: additional movement variations (de-duplicated) ----
    ["db-floor-press", "Dumbbell Floor Press", "chest", "compound", ["dumbbell"], 8, 12],
    ["db-neutral-press", "Neutral-Grip Dumbbell Bench Press", "chest", "compound", ["dumbbell", "bench"], 8, 12],
    ["db-incline-fly", "Incline Dumbbell Fly", "chest", "isolation", ["dumbbell", "inclinebench"], 10, 15],
    ["bodyweight-deficit-pushup", "Deficit Push-Up", "chest", "compound", [], 10, 20],
    ["helms-row", "Helms Dumbbell Row", "lats", "compound", ["dumbbell", "bench"], 8, 12],
    ["kb-gorilla-row", "Kettlebell Gorilla Row", "upper_back", "compound", ["kettlebell"], 8, 12],
    ["t-bar-chest-supported", "Chest-Supported T-Bar Row", "upper_back", "compound", ["machinerow"], 8, 12],
    ["bodyweight-doorway-row", "Bodyweight Doorway Row", "upper_back", "compound", [], 12, 20],
    ["doorway-biceps-curl", "Doorway Biceps Curl", "biceps", "isolation", [], 10, 20],
    ["prone-rear-delt-raise", "Prone Rear Delt Raise", "shoulders", "isolation", [], 12, 25],
    ["db-lu-raise", "Lu Lateral Raise", "shoulders", "isolation", ["dumbbell"], 12, 15],
    ["prone-db-rear-fly", "Prone Incline Bench Rear Delt Fly", "shoulders", "isolation", ["dumbbell", "inclinebench"], 12, 20],
    ["cable-behind-back-lateral", "Behind-the-Back Cable Lateral Raise", "shoulders", "isolation", ["cable"], 12, 15],
    ["db-6-way-raise", "Dumbbell 6-Way Lateral Raise", "shoulders", "isolation", ["dumbbell"], 10, 12],
    ["cable-rear-delt-row", "Standing Cable Rear Delt Row", "shoulders", "isolation", ["cable"], 12, 15],
    ["bands-face-pull", "Banded Face Pull", "shoulders", "isolation", ["bands"], 15, 20],
    ["db-deficit-lunge", "Deficit Dumbbell Reverse Lunge", "quads", "compound", ["dumbbell"], 8, 12],
    ["smith-reverse-lunge", "Smith Machine Reverse Lunge", "quads", "compound", ["smith"], 8, 12],
    ["db-deficit-step-up", "Deficit Dumbbell Step-Up", "quads", "compound", ["dumbbell", "bench"], 10, 12],
    ["jefferson-squat", "Barbell Jefferson Squat", "quads", "compound", ["barbell"], 6, 10],
    ["bodyweight-reverse-nordic", "Bodyweight Reverse Nordic Curl", "quads", "isolation", [], 8, 12],
    ["bands-squat", "Banded Resistance Squat", "quads", "compound", ["bands"], 15, 25],
    ["deficit-db-rdl", "Deficit Dumbbell Romanian Deadlift", "hamstrings", "compound", ["dumbbell"], 8, 12],
    ["db-good-morning", "Dumbbell Good Morning", "hamstrings", "compound", ["dumbbell"], 10, 15],
    ["deficit-barbell-deadlift", "Deficit Barbell Deadlift", "hamstrings", "compound", ["barbell"], 4, 6],
    ["cable-behind-back-curl", "Behind-the-Back Cable Bicep Curl", "biceps", "isolation", ["cable"], 12, 15],
    ["cable-cross-body-extension", "Cross-Body Cable Tricep Extension", "triceps", "isolation", ["cable"], 12, 15],
    ["bands-tricep-ext", "Banded Overhead Tricep Extension", "triceps", "isolation", ["bands"], 15, 20],
    ["db-weighted-crunch", "Incline Dumbbell Weighted Crunch", "abs", "isolation", ["dumbbell", "inclinebench"], 10, 15],
    ["bands-chest-press", "Banded Horizontal Chest Press", "chest", "compound", ["bands"], 12, 15],
    ["db-incline-shrug", "Incline Bench Dumbbell Shrug", "traps", "isolation", ["dumbbell", "inclinebench"], 12, 15],
    // --- Powerlifting competition-lift variations (specialized; appended last so they stay opt-in
    // choices in swap/library rather than crowding out staples in auto-generation) ---
    ["high-bar-squat", "High-Bar Back Squat", "quads", "compound", ["barbell", "rack"], 5, 8],
    ["low-bar-squat", "Low-Bar Back Squat", "quads", "compound", ["barbell", "rack"], 4, 8],
    ["paused-squat", "Paused Squat", "quads", "compound", ["barbell", "rack"], 3, 6],
    ["pin-squat", "Pin Squat", "quads", "compound", ["barbell", "rack"], 3, 6],
    ["tempo-squat", "Tempo Squat", "quads", "compound", ["barbell", "rack"], 4, 8],
    ["spoto-press", "Spoto Press", "chest", "compound", ["barbell", "rack"], 3, 6],
    ["larsen-press", "Larsen Press", "chest", "compound", ["barbell", "rack"], 4, 8],
    ["board-press", "Board Press", "chest", "compound", ["barbell", "rack"], 3, 6],
    ["pin-bench", "Pin Press (Bench)", "chest", "compound", ["barbell", "rack"], 3, 6],
    ["pause-bench", "3s Paused Bench Press", "chest", "compound", ["barbell", "rack"], 3, 6],
    ["paused-deadlift", "Paused Deadlift", "lower_back", "compound", ["barbell"], 3, 6],
    ["pin-press-ohp", "Pin Press (Overhead)", "shoulders", "compound", ["barbell", "rack"], 4, 8],
    ["barbell-skullover", "Barbell Skullover", "triceps", "isolation", ["barbell"], 8, 12],
    // Appended so saved exercise indices and existing generation priorities stay stable.
    ["seated-db-lat-raise", "Seated Dumbbell Lateral Raise", "shoulders", "isolation", ["dumbbell", "bench"], 12, 20, "side_delts", "shoulder-abduction"],
    ["single-db-lat-raise", "Single-Arm Dumbbell Lateral Raise", "shoulders", "isolation", ["dumbbell"], 12, 20, "side_delts", "shoulder-abduction"],
    ["chest-supported-lat-raise", "Chest-Supported Dumbbell Lateral Raise", "shoulders", "isolation", ["dumbbell", "inclinebench"], 12, 20, "side_delts", "shoulder-abduction"],
    ["seated-cable-lat-raise", "Seated Cable Lateral Raise", "shoulders", "isolation", ["cable", "bench"], 12, 20, "side_delts", "shoulder-abduction"],
    ["cuff-cable-lat-raise", "Cuff Cable Lateral Raise", "shoulders", "isolation", ["cable"], 12, 20, "side_delts", "shoulder-abduction"],
    ["seated-rear-fly", "Seated Dumbbell Rear Delt Fly", "shoulders", "isolation", ["dumbbell", "bench"], 12, 20, "rear_delts", "shoulder-horizontal-abduction"],
    ["single-cable-rear-fly", "Single-Arm Cable Rear Delt Fly", "shoulders", "isolation", ["cable"], 12, 20, "rear_delts", "shoulder-horizontal-abduction"],
    ["chest-supported-rear-fly", "Chest-Supported Dumbbell Rear Delt Fly", "shoulders", "isolation", ["dumbbell", "inclinebench"], 12, 20, "rear_delts", "shoulder-horizontal-abduction"],
];

const PATTERN_BY_ID = { "doorway-biceps-curl": "elbow-flexion", "prone-rear-delt-raise": "shoulder-horizontal-abduction", "ab-wheel": "anti-extension", "adduction-machine": "hip-adduction", "adductor-sumo": "hip-adduction", "arnold": "vertical-push", "assisted-chinup": "vertical-pull", "assisted-dip": "horizontal-push", "assisted-pullup": "vertical-pull", "back-ext-45": "hip-hinge", "back-squat": "squat", "band-abduction": "hip-abduction", "band-adduction": "hip-adduction", "band-curl": "elbow-flexion", "band-good-morning": "hip-hinge", "band-hip-thrust": "hip-extension", "band-leg-curl": "knee-flexion", "band-pulldown": "vertical-pull", "band-pushup": "horizontal-push", "band-ulnar-dev": "wrist-deviation", "banded-tib-raise": "dorsiflexion", "bands-chest-press": "horizontal-push", "bands-face-pull": "shoulder-horizontal-abduction", "bands-squat": "squat", "bands-tricep-ext": "elbow-extension-overhead", "barbell-skullover": "elbow-extension-overhead", "bayesian-curl": "elbow-flexion", "bb-bench": "horizontal-push", "bb-curl": "elbow-flexion", "bb-row": "horizontal-pull", "bb-shrug": "shrug", "behind-shrug": "shrug", "behind-wrist-curl": "wrist-flexion", "belt-squat": "squat", "bench-dip": "elbow-extension-neutral", "bent-db-pullover": "pullover", "bicycle": "spinal-flexion", "board-press": "horizontal-push", "bodyweight-deficit-pushup": "horizontal-push", "bodyweight-doorway-row": "horizontal-pull", "bodyweight-reverse-nordic": "knee-extension", "box-squat": "squat", "bradford-press": "vertical-push", "bstance-hip-thrust": "hip-extension", "bulgarian": "lunge", "bw-bulgarian": "lunge", "bw-calf": "plantarflexion-straight-knee", "bw-lunge": "lunge", "bw-pullup": "vertical-pull", "bw-single-calf": "plantarflexion-straight-knee", "bw-squat": "squat", "cable-abduction": "hip-abduction", "cable-adduction": "hip-adduction", "cable-behind-back-curl": "elbow-flexion", "cable-behind-back-lateral": "shoulder-abduction", "cable-cross-body-extension": "elbow-extension-neutral", "cable-crossover": "shoulder-horizontal-adduction", "cable-crunch": "spinal-flexion", "cable-curl": "elbow-flexion", "cable-face-shrug": "shrug", "cable-fly": "shoulder-horizontal-adduction", "cable-front-raise": "shoulder-flexion", "cable-kickback": "hip-abduction", "cable-lat-raise": "shoulder-abduction", "cable-press": "horizontal-push", "cable-pullover": "pullover", "cable-radial-dev": "wrist-deviation", "cable-rdl": "hip-hinge", "cable-rear-delt-row": "shoulder-horizontal-abduction", "cable-rear-fly": "shoulder-horizontal-abduction", "cable-reverse-crunch": "spinal-flexion", "cable-serratus": "anti-extension", "cable-shrug": "shrug", "cable-side-bend": "lateral-flexion", "cable-tri-kickback": "elbow-extension-neutral", "cable-upright-row": "shoulder-abduction", "cable-woodchop": "anti-rotation", "cable-wrist-curl": "wrist-flexion", "cable-y-raise": "scapular-upward-rotation", "california-press": "elbow-extension-neutral", "cgbp": "elbow-extension-neutral", "chest-row": "horizontal-pull", "chinup": "vertical-pull", "close-cable-row": "horizontal-pull", "close-pulldown": "vertical-pull", "concentration-curl": "elbow-flexion", "copenhagen": "hip-adduction", "cossack-squat": "hip-adduction", "cross-hammer": "elbow-flexion-neutral", "crunch": "spinal-flexion", "cs-db-row": "horizontal-pull", "curtsy-lunge": "hip-extension", "cyclist-squat": "knee-extension", "db-6-way-raise": "shoulder-abduction", "db-bench": "horizontal-push", "db-calf": "plantarflexion-straight-knee", "db-curl": "elbow-flexion", "db-deficit-lunge": "lunge", "db-deficit-step-up": "lunge", "db-floor-press": "horizontal-push", "db-fly": "shoulder-horizontal-adduction", "db-good-morning": "hip-hinge", "db-hip-thrust": "hip-extension", "db-incline-fly": "shoulder-horizontal-adduction", "db-incline-shrug": "shrug", "db-lu-raise": "shoulder-abduction", "db-neutral-press": "horizontal-push", "db-oh-ext": "elbow-extension-overhead", "db-pullover": "pullover", "db-rdl": "hip-hinge", "db-row": "horizontal-pull", "db-shoulder": "vertical-push", "db-shrug": "shrug", "db-upright-row": "shoulder-abduction", "db-weighted-crunch": "spinal-flexion", "dead-bug": "anti-extension", "dead-hang": "grip-static", "deadlift": "hip-hinge", "decline-bench": "horizontal-push", "decline-db-press": "horizontal-push", "decline-pushup": "horizontal-push", "decline-situp": "spinal-flexion", "deficit-barbell-deadlift": "hip-hinge", "deficit-db-rdl": "hip-hinge", "diamond-pushup": "elbow-extension-neutral", "dips-chest": "horizontal-push", "dips-tri": "elbow-extension-neutral", "donkey-calf": "plantarflexion-straight-knee", "drag-curl": "elbow-flexion", "dragon-flag": "anti-extension", "dual-cable-row": "horizontal-pull", "dual-rope-pushdown": "elbow-extension-neutral", "ez-cable-curl": "elbow-flexion", "ez-curl": "elbow-flexion", "ez-oh-ext": "elbow-extension-overhead", "ez-pushdown": "elbow-extension-neutral", "face-pull": "shoulder-horizontal-abduction", "farmers": "grip-static", "floor-db-pullover": "pullover", "floor-press": "horizontal-push", "frog-pump": "hip-extension", "front-raise": "shoulder-flexion", "front-squat": "squat", "ghr": "hip-hinge", "glute-bridge": "hip-extension", "glute-kickback-machine": "hip-abduction", "goblet": "squat", "good-morning": "hip-hinge", "gripper": "grip-static", "hack-calf": "plantarflexion-bent-knee", "hack-squat": "squat", "hammer": "elbow-flexion-neutral", "hammer-radial-dev": "wrist-deviation", "hammer-ulnar-dev": "wrist-deviation", "hanging-knee": "spinal-flexion", "hanging-oblique": "anti-rotation", "hanging-raise": "spinal-flexion", "heels-up-goblet": "squat", "helms-row": "horizontal-pull", "high-bar-squat": "squat", "high-cable-fly": "shoulder-horizontal-adduction", "high-inc-bb-bench": "incline-push", "hip-abduction": "hip-abduction", "hip-thrust": "hip-extension", "hollow-hold": "anti-extension", "inc-bb-bench": "incline-push", "inc-cable-fly": "shoulder-horizontal-adduction", "inc-curl": "elbow-flexion", "inc-db-press": "incline-push", "inc-db-row": "horizontal-pull", "incline-cable-press": "incline-push", "incline-hammer": "elbow-flexion-neutral", "incline-machine-press": "incline-push", "incline-pushup": "incline-push", "incline-shrug": "shrug", "inv-row": "horizontal-pull", "jefferson-curl": "knee-flexion", "jefferson-squat": "squat", "jm-press": "elbow-extension-neutral", "kas-glute-bridge": "hip-extension", "kb-gorilla-row": "horizontal-pull", "kb-swing": "hip-hinge", "kelso-shrug": "shrug", "kroc-row": "horizontal-pull", "l-sit": "anti-extension", "landmine-press": "vertical-push", "larsen-press": "horizontal-push", "lat-pulldown": "vertical-pull", "lat-raise": "shoulder-abduction", "lateral-walk": "hip-abduction", "leaning-lat-raise": "shoulder-abduction", "leg-ext": "knee-extension", "leg-press": "squat", "leg-press-calf": "plantarflexion-bent-knee", "leg-raise": "spinal-flexion", "lm-squat": "squat", "low-bar-squat": "squat", "low-inc-bb-bench": "incline-push", "low-incline-db": "incline-push", "lying-curl": "knee-flexion", "lying-db-ext": "elbow-extension-neutral", "machine-crunch": "spinal-flexion", "machine-curl": "elbow-flexion", "machine-dip": "horizontal-push", "machine-ext": "elbow-extension-neutral", "machine-hip-thrust": "hip-extension", "machine-lat-raise": "shoulder-abduction", "machine-press": "horizontal-push", "machine-pullover": "pullover", "machine-row": "horizontal-pull", "machine-shoulder": "vertical-push", "machine-shrug": "shrug", "meadows-row": "horizontal-pull", "mountain-climber": "anti-extension", "neck-curl": "neck", "neck-extension": "neck", "neck-harness": "neck", "neck-lateral": "neck", "neutral-machine-row": "horizontal-pull", "neutral-pulldown": "vertical-pull", "nordic": "knee-flexion", "oblique-crunch": "anti-rotation", "oh-cable-ext": "elbow-extension-overhead", "ohp": "vertical-push", "one-arm-pulldown": "vertical-pull", "pallof": "anti-rotation", "pause-bench": "horizontal-push", "paused-deadlift": "hip-hinge", "paused-squat": "squat", "pec-deck": "shoulder-horizontal-adduction", "pendlay-row": "horizontal-pull", "pendulum-squat": "squat", "pike-pushup": "vertical-push", "pin-bench": "horizontal-push", "pin-press-ohp": "vertical-push", "pin-squat": "squat", "pinwheel-curl": "elbow-flexion", "pistol-squat": "lunge", "pjr-pullover": "elbow-extension-overhead", "plank": "anti-extension", "plate-front-raise": "shoulder-flexion", "plate-pinch": "grip-static", "power-shrug": "shrug", "preacher": "elbow-flexion", "prone-db-rear-fly": "shoulder-horizontal-abduction", "prone-y-raise": "scapular-upward-rotation", "pull-through": "hip-hinge", "pullup": "vertical-pull", "push-press": "vertical-push", "pushdown": "elbow-extension-neutral", "pushup": "horizontal-push", "rack-pull": "hip-hinge", "rdl": "hip-hinge", "rear-band-pull-apart": "shoulder-horizontal-abduction", "rear-fly": "shoulder-horizontal-abduction", "renegade-row": "horizontal-pull", "rev-grip-curl": "elbow-flexion-neutral", "reverse-crunch": "spinal-flexion", "reverse-curl": "wrist-extension", "reverse-hyper": "hip-extension", "reverse-lunge": "lunge", "reverse-pec": "shoulder-horizontal-abduction", "reverse-wrist-curl": "wrist-extension", "rope-cable-row": "horizontal-pull", "rope-hammer-curl": "elbow-flexion-neutral", "rope-pushdown": "elbow-extension-neutral", "russian-twist": "anti-rotation", "seal-row": "horizontal-pull", "seated-calf": "plantarflexion-bent-knee", "seated-curl": "knee-flexion", "seated-db-press": "vertical-push", "seated-incline-curl": "elbow-flexion", "seated-ohp": "vertical-push", "seated-row": "horizontal-pull", "serratus-pushup": "anti-extension", "side-lying-abduction": "hip-abduction", "side-lying-raise": "shoulder-abduction", "side-plank": "anti-extension", "single-cable-curl": "elbow-flexion", "single-cable-raise": "shoulder-abduction", "single-cable-row": "horizontal-pull", "single-calf": "plantarflexion-straight-knee", "single-leg-press": "squat", "single-leg-press-calf": "plantarflexion-bent-knee", "single-lying-curl": "knee-flexion", "single-pushdown": "elbow-extension-neutral", "sissy-squat": "knee-extension", "situp": "spinal-flexion", "skullcrusher": "elbow-extension-overhead", "sl-hip-thrust": "hip-extension", "slider-curl": "knee-flexion", "slrdl": "hip-hinge", "smith-bench": "horizontal-push", "smith-calf": "plantarflexion-straight-knee", "smith-hip-thrust": "hip-extension", "smith-incline": "incline-push", "smith-ohp": "vertical-push", "smith-reverse-lunge": "lunge", "smith-shrug": "shrug", "smith-squat": "squat", "snatch-deadlift": "hip-hinge", "spanish-squat": "squat", "spider-curl": "elbow-flexion", "split-squat": "lunge", "spoto-press": "horizontal-push", "squeeze-press": "shoulder-horizontal-adduction", "standing-cable-abduction": "hip-abduction", "standing-calf": "plantarflexion-straight-knee", "standing-curl": "knee-flexion", "step-through-lunge": "hip-extension", "step-up": "lunge", "stiff-deadlift": "hip-hinge", "stir-pot": "anti-extension", "straight-bar-pushdown": "elbow-extension-neutral", "straight-pulldown": "vertical-pull", "suitcase-carry": "grip-static", "sumo-dl": "hip-extension", "sumo-squat": "squat", "superman": "hip-hinge", "svend-press": "shoulder-horizontal-adduction", "t-bar-chest-supported": "horizontal-pull", "tate-press": "elbow-extension-neutral", "tbar-row": "horizontal-pull", "tempo-squat": "squat", "tibialis-raise": "dorsiflexion", "toes-to-bar": "spinal-flexion", "towel-hang": "grip-static", "trap-bar-deadlift": "hip-hinge", "trap-bar-shrug": "shrug", "tricep-kickback": "elbow-extension-neutral", "underhand-cable-row": "horizontal-pull", "underhand-pulldown": "vertical-pull", "underhand-pushdown": "elbow-extension-neutral", "underhand-row": "horizontal-pull", "upright-row": "shoulder-abduction", "v-bar-pushdown": "elbow-extension-neutral", "v-squat": "squat", "v-up": "spinal-flexion", "viking-press": "vertical-push", "waiter-curl": "elbow-flexion", "walking-lunge": "lunge", "wall-sit": "isometric-hold", "weighted-plank": "anti-extension", "wide-cable-row": "horizontal-pull", "wide-machine-row": "horizontal-pull", "wide-pulldown": "vertical-pull", "wide-tbar-row": "horizontal-pull", "windshield-wiper": "anti-rotation", "woodchopper": "anti-rotation", "wrist-curl": "wrist-flexion", "wrist-roller": "grip-static", "yates-row": "horizontal-pull", "z-press": "vertical-push", "zercher-squat": "squat", "zottman-curl": "elbow-flexion" };

const EXERCISES = RAW.map(([id, name, part, type, equip, lo, hi, region, pattern], i) => ({ id, name, part, type, equip, rep: [lo, hi], pri: i, ...(region ? { region } : {}), ...((pattern || PATTERN_BY_ID[id]) ? { pattern: pattern || PATTERN_BY_ID[id] } : {}) }));

const PATTERNS = [
    "horizontal-push", "incline-push", "vertical-push", "shoulder-horizontal-adduction",
    "horizontal-pull", "vertical-pull", "pullover",
    "hip-hinge", "knee-flexion", "knee-extension", "squat", "lunge", "hip-extension", "hip-abduction", "hip-adduction",
    "elbow-flexion", "elbow-flexion-neutral",
    "elbow-extension-overhead", "elbow-extension-neutral",
    "shoulder-abduction", "shoulder-horizontal-abduction", "shoulder-external-rotation", "shrug",
    "plantarflexion-straight-knee", "plantarflexion-bent-knee",
    "wrist-flexion", "wrist-extension", "wrist-deviation", "grip-static",
    "spinal-flexion", "lateral-flexion", "anti-extension", "anti-rotation", "neck", "shoulder-flexion",
    "dorsiflexion", "isometric-hold", "scapular-upward-rotation",
];

const EX_BY_ID = Object.fromEntries(EXERCISES.map(e => [e.id, e]));

const EX_ABBREV = {
    rdl: "romanian deadlift", ohp: "overhead press", bss: "bulgarian split squat", sldl: "stiff leg deadlift",
    db: "dumbbell", bb: "barbell", ez: "ez bar", cgbp: "close grip bench", gm: "good morning",
    ghr: "glute ham raise", rfess: "rear foot elevated split squat", jm: "jm press", bor: "bent over row",
    pjr: "pull", hspu: "handstand push", ohs: "overhead squat", sldf: "stiff leg deadlift"
};

const MUSCLE_SYNONYM = {
    chest: "chest", pec: "chest", pecs: "chest", lats: "lats", lat: "lats", "upper back": "upper_back",
    back: "lats", mid_back: "upper_back", "lower back": "lower_back", spine: "lower_back", erector: "lower_back",
    shoulders: "shoulders", shoulder: "shoulders", delt: "shoulders", delts: "shoulders", deltoid: "shoulders",
    bicep: "biceps", biceps: "biceps", bis: "biceps", tricep: "triceps", triceps: "triceps", tris: "triceps",
    quad: "quads", quads: "quads", thigh: "quads", ham: "hamstrings", hams: "hamstrings", hamstring: "hamstrings",
    glute: "glutes", glutes: "glutes", butt: "glutes", calf: "calves", calves: "calves",
    abs: "abs", ab: "abs", core: "abs", trap: "traps", traps: "traps", forearm: "forearms", forearms: "forearms",
    abductor: "abductors", adductor: "adductors"
};

function exMatches(ex, query) {
    const ql = query.trim().toLowerCase();
    if (!ql)
        return true;
    const name = ex.name.toLowerCase();
    const part = ex.part;
    const tokens = ql.split(/\s+/).filter(Boolean);
    return tokens.every(tok => {
        if (MUSCLE_SYNONYM[tok] && MUSCLE_SYNONYM[tok] === part)
            return true;
        const expanded = EX_ABBREV[tok];
        if (expanded && expanded.split(/\s+/).every(w => name.includes(w)))
            return true;
        if (name.includes(tok))
            return true;
        if (part.replace("_", " ").includes(tok))
            return true;
        return false;
    });
}

const ASSIST_IDS = new Set(["assisted-pullup", "assisted-chinup", "assisted-dip"]);

const isAssistedEx = (ex) => !!ex && (ASSIST_IDS.has(ex.id) || /^assisted-/.test(ex.id));

const WEIGHTED_SWAP = {
    "assisted-pullup": "pullup", "bw-pullup": "pullup", "assisted-chinup": "chinup",
    "assisted-dip": "dips-chest", "dips-tri": "dips-chest",
    "pushup": "dips-chest", "diamond-pushup": "cgbp", "bench-dip": "dips-tri",
    "bw-squat": "goblet", "bw-lunge": "walking-lunge", "bw-bulgarian": "bulgarian",
    "inv-row": "bb-row", "pike-pushup": "db-shoulder"
};

const SPLITS = {
    full_body: { name: "Full Body", /* Was "Train everything each session", which is not what it builds. Measured across 3/4/5/6 days
             at every session length, a day covers 3-5 of the 7 major muscle groups and NEVER all seven —
             there is no session budget in which eight movement patterns fit. The rotation is the design and
             it is a good one, so the blurb now describes the rotation instead of promising a session that
             cannot exist. A split's blurb is read before anything is generated, which makes it the one
             claim a user cannot check before committing. */
        blurb: "Rotating full-body days — each session anchors on a heavy compound and covers several movement patterns, so every muscle is trained two or more times a week. Spreads fatigue across patterns and scales from low to high frequency.", days: [2, 3, 4, 5, 6], build: (d) => { const seq = ["fb_a", "fb_b", "fb_c", "fb_d", "fb_e"]; return Array.from({ length: d }, (_, i) => seq[i % seq.length]); } },
    upper_lower: { name: "Upper / Lower", blurb: "Alternate upper & lower days, rotating horizontal/vertical and quad/hinge emphasis.", days: [2, 4, 6], build: (d) => Array.from({ length: d }, (_, i) => `${i % 2 === 0 ? "upper" : "lower"}${Math.floor(i / 2) % 2 === 0 ? "_a" : "_b"}`) },
    ppl: { name: "Push / Pull / Legs", blurb: "The classic, with each repeat rotating emphasis (chest/delt, width/thickness, quad/posterior).", days: [3, 5, 6], build: (d) => { const A = ["push_a", "pull_a", "legs_a"], B = ["push_b", "pull_b", "legs_b"]; return Array.from({ length: d }, (_, i) => (Math.floor(i / 3) % 2 === 0 ? A : B)[i % 3]); } },
    ulppl: { name: "Upper·Lower·Push·Pull·Legs", blurb: "Hybrid 5-day blending UL frequency with PPL volume.", days: [5], build: () => ["upper", "lower", "push", "pull", "legs"] },
    hybrid: { name: "Hybrid Strength + Size", blurb: "Heavy strength Upper & Lower to open the week, then a higher-volume hypertrophy Push/Pull/Legs — strength and size in one week, the way modern physique programs run it.", days: [5], build: () => ["upper", "lower", "push", "pull", "legs"], focus: () => ["strength", "strength", "hypertrophy", "hypertrophy", "hypertrophy"] },
    phul: { name: "Power / Hypertrophy Upper-Lower", blurb: "Heavy upper/lower sessions followed by higher-rep upper/lower sessions; The plan adapts the exact prescription and progression.", days: [4], minSession: "s60", maxSession: "s120", build: () => ["upper_power", "lower_power", "upper_hyp", "lower_hyp"], focus: () => ["strength", "strength", "hypertrophy", "hypertrophy"] },
    phat: { name: "Power–Hypertrophy 5-Day", blurb: "Two heavy power days then three higher-rep hypertrophy days.", days: [5], minSession: "s60", maxSession: "s120", build: () => ["upper_power", "lower_power", "back_shoulders", "lower_hyp", "chest_arms"], focus: () => ["strength", "strength", "hypertrophy", "hypertrophy", "hypertrophy"] },
    bro: { name: "Bro Split", blurb: "One muscle group per day, max volume each.", days: [5, 6], build: (d) => d >= 6 ? ["chest_day", "back_day", "shoulders_day", "legs_day", "arms", "legs_day"] : ["chest_day", "back_day", "shoulders_day", "legs_day", "arms"] },
    arnold: { name: "Chest + Back / Shoulders + Arms / Legs", blurb: "Chest+Back · Shoulders+Arms · Legs, twice over, with Pursuit-owned dose and progression.", days: [6], build: () => ["chest_back", "shoulders_arms", "legs_day", "chest_back", "shoulders_arms", "legs_day"] },
    five_three_one: { name: "Main-Lift Waves", blurb: "Four days, each built around one main barbell lift (Press · Deadlift · Bench · Squat) plus targeted accessories, loaded off a training max.", days: [4], minSession: "s60", maxSession: "s120", build: () => ["t531_press", "t531_deadlift", "t531_bench", "t531_squat"] },
    five31_beginner: { minBarbells: 2, name: "Main-Lift Waves · Novice", blurb: "Three days a week, two main lifts each — fixed-rep top sets plus a same-load back-off block — so every lift is trained twice a week.", days: [3], minSession: "s60", maxSession: "s90", build: (_n) => ["b531_a", "b531_b", "b531_a"] },
    strength_fb: { name: "Strength Full Body", blurb: "Squat, press and pull every session — the classic barbell linear-progression template for building base strength.", days: [3], build: () => ["full_a", "full_b", "full_c"] },
    academy_prep: { name: "Academy Prep", blurb: "Builds the strength base for the law-enforcement academy fitness test — pressing endurance for push-ups, a trained trunk for sit-ups, and posterior-chain/leg work to drive sprints and the 1.5-mile run. Pair with Pursuit Rated for the running side.", days: [3, 4], build: (d) => { const seq = ["acad_a", "acad_b", "acad_c"]; return Array.from({ length: d }, (_, i) => seq[i % seq.length]); } },
    texas: { name: "Volume / Recovery / Intensity", blurb: "Three-day strength undulation: a higher-volume session, a deliberately lower-fatigue session, then an intensity-focused session. The plan adapts the exact sets, reps and loading.", days: [3], minSession: "s60", maxSession: "s120", build: () => ["tx_volume", "tx_recovery", "tx_intensity"] },
    gzclp: { minBarbells: 2, name: "Tiered Linear Progression", blurb: "Four-day tiered strength structure with a heavy primary lift, secondary volume work and accessories; The plan adapts the exact prescription.", days: [4], minSession: "s60", maxSession: "s120", build: () => ["gz_a1", "gz_b1", "gz_a2", "gz_b2"] },
    rippler: { minBarbells: 2, name: "Tiered Wave", blurb: "Four-day tiered strength structure with changing intensity emphasis plus secondary volume and accessories; The plan adapts the exact wave.", days: [4], minSession: "s60", maxSession: "s120", build: () => ["gz_a1", "gz_b1", "gz_a2", "gz_b2"] },
    jt: { name: "Tiered Powerbuilding", blurb: "Four-day tiered powerbuilding structure balancing heavy primary work with higher-volume secondary and accessory work; The plan adapts the exact prescription.", days: [4], minSession: "s60", maxSession: "s120", build: () => ["gz_a1", "gz_b1", "gz_a2", "gz_b2"] },
    // APPEND-ONLY BOUNDARY: CODE_SPLITS = Object.keys(SPLITS) is the split index inside every program
    // code ever issued. New splits go BELOW this line; nothing above it may be reordered or removed.
    glute_focus: { name: "Glutes & Lower Body", blurb: "Hip-dominant training — a hinge or thrust anchors each lower day with direct abduction work, balanced by a pull-led upper day, and a dedicated trunk day once you're training five times a week.", days: [3, 4, 5], build: (d) => { const seq = ["glute_a", "upper_tone", "glute_b", "upper_a", "core_abs"]; return Array.from({ length: d }, (_, i) => seq[i % seq.length]); } },
    /* APPENDED below the boundary, so every program code ever issued keeps its meaning: this takes the
       next free split index rather than shifting any existing one. */
    full_body_patterns: { name: "Full Body \u00B7 Pattern Rotation", blurb: "Full-body days built as pattern PAIRS rather than one anchor lift \u2014 squat with horizontal push/pull, hinge with vertical, then unilateral, posterior chain and a pump day. Spreads pressing across the week, so the front delt takes less of it.", days: [2, 3, 4, 5, 6], build: (d) => { const seq = ["fb2_a", "fb2_b", "fb2_c", "fb2_d", "fb2_e"]; return Array.from({ length: d }, (_, i) => seq[i % seq.length]); } },
    torso_limbs: { name: "Torso / Limbs", blurb: "Torso days (chest, back and shoulders) alternate with Limbs days (legs and arms). Separating heavy pressing and pulling from direct arm work gives the arms a session where they are not already fatigued.", days: [4, 5], build: (d) => { const seq = ["torso_a", "limbs_a", "torso_b", "limbs_b", "torso_a"]; return Array.from({ length: d }, (_, i) => seq[i % seq.length]); } },
    ppla: { name: "Push / Pull / Legs / Arms", blurb: "PPL with a dedicated fourth session for shoulders and arms, so side delts, biceps and triceps are trained fresh rather than on whatever is left after pressing.", days: [4, 5], build: (d) => (d >= 5 ? ["push_a", "pull_a", "legs_a", "upper_b", "shoulders_arms"] : ["push_a", "pull_a", "legs_a", "shoulders_arms"]) },
    ula: { name: "Upper / Lower / Arms", blurb: "Two upper days, two lower days, and a dedicated arms and weak-point day \u2014 for lifters whose arms and side delts lag the rest.", days: [5], build: () => ["upper_a", "lower_a", "upper_b", "lower_b", "shoulders_arms"] },
    sbd_power: { name: "Powerlifting \u00B7 SBD Wave", blurb: "Built around the squat, bench and deadlift, with hypertrophy accessories behind them. Three days pairs the lifts across sessions; four gives each its own day plus an upper power day.", days: [3, 4], build: (d) => (d >= 4 ? ["t531_squat", "t531_bench", "t531_deadlift", "upper_power"] : ["sbd_squat_bench", "sbd_bench_deadlift", "sbd_squat_deadlift"]), focus: (d) => (d >= 4 ? ["strength", "strength", "strength", "hypertrophy"] : ["strength", "strength", "hypertrophy"]) },
    /* ⚠ APPENDED AT THE END ON PURPOSE. SPLITS order IS an index: program codes encode a split by its
       position, so inserting anywhere but the end shifts every split after it and old shared codes decode
       to the WRONG PROGRAM. gates/generator check 28 caught exactly that ("prefix drifted") when this sat
       next to `upper_lower`, where it reads better and breaks compatibility. New splits go last, always. */
    /* ⚠ THE ALTERNATING UPPER/LOWER. `upper_lower` offers 2, 4 and 6 days because an even count divides
       cleanly into one week. THREE does not, and the established answer is not a third day type — it is
       to keep alternating ACROSS the week boundary: U L U | L U L, so upper and lower each get THREE
       sessions per fortnight. Balanced, and the reason the method is standard.
       `rotation: 2` makes the volume model judge targets over the FORTNIGHT. Without it the engine
       scores a two-week structure against a one-week yardstick and calls every other week under-dosed —
       which is why I refused this feature once, wrongly.
       FOUR day types so the rolling cursor never repeats a session back to back. `build` returns the
       POOL; the `rotationPool` stage keeps three of them in `program.days` (the week) and parks all four
       in `program.pool`, so `days.length` still means "sessions per week" for its 68 readers. */
    upper_lower_alt: { name: "Upper / Lower \u00b7 Alternating", blurb: "Three sessions a week, alternating upper and lower without resetting each week \u2014 each gets three sessions per fortnight.", days: [3], rotation: 2, minSession: "s40",
        build: () => ["upper_a", "lower_a", "upper_b", "lower_b"] },
};

const NOVICE_INELIGIBLE_SPLITS = new Set(["arnold", "bro", "ppl", "ppla", "phat", "glute_focus", "sbd_power", "five_three_one", "jt", "rippler"]);

const SESSIONS = [
    { id: "s20", label: "Up to 20 minutes", count: 2 },
    { id: "s40", label: "20 to 40 minutes", count: 3 },
    { id: "s60", label: "40 to 60 minutes", count: 4 },
    { id: "s90", label: "60 to 90 minutes", count: 5 },
    { id: "s120", label: "90 to 120 minutes", count: 6 },
    { id: "s120p", label: "Over 120 minutes", count: 7 },
];

const EXP = {
    none: { label: "None", sub: "Brand new to lifting", setBase: 2 },
    beginner: { label: "Beginner", sub: "< 1 year training", setBase: 3 },
    intermediate: { label: "Intermediate", sub: "1–3 years training", setBase: 3 },
    advanced: { label: "Advanced", sub: "3+ years, dialled-in", setBase: 4 }
};

const ICON_COLORS = {
    lime: "#B8E62E", cyan: "#3ECFCF", blue: "#4C8DFF", violet: "#A78BFA",
    pink: "#F472B6", red: "#F0553F", amber: "#F0A93B", green: "#4ADE80",
    slate: "#94A3B8"
};

const ICON_COLOR_KEYS = Object.keys(ICON_COLORS);

const iconColorOf = (x) => (x && x.iconColor && ICON_COLORS[x.iconColor]) || C.accent;

const TEMPLATE_CATS = ["Strength", "Powerbuilding", "Hypertrophy", "Specialization", "Tactical"];

const INTENT_FROM_CAT = { Hypertrophy: "Hypertrophy", Powerbuilding: "Powerbuilding", Strength: "Strength", Tactical: "Tactical" };

function templateIntent(t) {
    if (t.cfg.focus && Object.keys(t.cfg.focus).length)
        return "Specialization";
    if (INTENT_FROM_CAT[t.cat])
        return INTENT_FROM_CAT[t.cat];
    const g = t.cfg.goal;
    return g === "strength" ? "Strength" : g === "hypertrophy" ? "Hypertrophy" : "Powerbuilding";
}

function templateFacets(t) {
    const c = t.cfg;
    const exp = (c.experience === "none" || c.experience === "beginner") ? "Beginner" : c.experience === "advanced" ? "Advanced" : "Intermediate";
    const eq = c.equipment;
    let setting = "Full gym";
    // `eq` absent means "whatever the lifter owns" → assume a gym. An explicitly EMPTY array is the
    // opposite claim — the template needs nothing at all — and used to fall through to "Full gym",
    // hiding a no-equipment program from the one chip that would surface it.
    if (Array.isArray(eq)) {
        // "Bodyweight" has to mean bodyweight. This used to lump bands, a pull-up bar and a dip station
        // in with it, so the chip surfaced programs that then prescribed banded pulldowns and hanging
        // leg raises — the filter was making a promise the generator could not keep. Those kits get
        // their own value; the per-template `tag` names the specific kit ("Bands only", "Bar + bands").
        if (!eq.length)
            setting = "Bodyweight";
        else if (eq.every(x => x === "bands" || x === "pullup" || x === "dip"))
            setting = "Minimal kit";
        else if (isMachineLike(eq) || eq.includes("smith"))
            setting = "Machines";
        else
            setting = "Home"; // dumbbell / kettlebell kit
    }
    return { exp, setting, days: c.days, quick: c.session === "s40", emphasis: templateEmphasis(t) };
}

const EMPHASIS_LABEL = {
    glutes: "Glutes", hamstrings: "Glutes", abductors: "Glutes", adductors: "Glutes",
    abs: "Core", lower_back: "Core",
    biceps: "Arms", triceps: "Arms", forearms: "Arms",
    shoulders: "Shoulders", traps: "Shoulders",
    lats: "Back", upper_back: "Back",
    chest: "Chest", quads: "Legs", calves: "Legs"
};

const EMPHASIS_BY_SPLIT = { glute_focus: ["Glutes"] };

function templateEmphasis(t) {
    const out = new Set(EMPHASIS_BY_SPLIT[t.cfg.split] || []);
    Object.entries(t.cfg.focus || {}).forEach(([part, n]) => { if (n > 0 && EMPHASIS_LABEL[part])
        out.add(EMPHASIS_LABEL[part]); });
    return [...out];
}

const TEMPLATE_FILTER_GROUPS = [
    { facet: "exp", chips: ["Beginner", "Intermediate", "Advanced"] },
    { facet: "setting", chips: ["Full gym", "Home", "Machines", "Minimal kit", "Bodyweight"] }, // "Bodyweight" means literally no equipment; "Minimal kit" is a band, bar or dip station
    { facet: "days", chips: [2, 3, 4, 5, 6], label: (d) => `${d} day` },
    { facet: "quick", chips: ["Quick ≤40m"] },
    { facet: "emphasis", chips: ["Glutes", "Core", "Back", "Shoulders", "Arms", "Chest", "Legs"] },
];

const TEMPLATES = [
    { id: "ppl6", cat: "Hypertrophy", featured: true, featuredRank: 10, name: "Push / Pull / Legs", tag: "6 days · Hypertrophy", desc: "High-volume classic, trained six times a week", cfg: { split: "ppl", days: 6, session: "s90", goal: "hypertrophy", experience: "intermediate", weeks: 6 } },
    { id: "ppl3", cat: "Hypertrophy", name: "Push / Pull / Legs · 3 Day", tag: "3 days · Hypertrophy", desc: "PPL run once through — full coverage in three sessions", cfg: { split: "ppl", days: 3, session: "s90", goal: "hypertrophy", experience: "intermediate", weeks: 6 } },
    { id: "ul4", cat: "Powerbuilding", featured: true, featuredRank: 20, name: "Upper / Lower", tag: "4 days · Strength & Size", desc: "Balanced heavy lifts plus hypertrophy work", cfg: { split: "upper_lower", days: 4, session: "s90", goal: "both", experience: "intermediate", weeks: 6 } },
    { id: "ul2", cat: "Beginner", name: "Upper / Lower · 2 Day", tag: "2 days · Beginner", desc: "Whole body across two sessions — ideal when time is tight", cfg: { split: "upper_lower", days: 2, session: "s60", goal: "both", experience: "beginner", weeks: 4 } },
    { id: "ul6", cat: "Powerbuilding", name: "Upper / Lower · 6 Day", tag: "6 days · Powerbuilding", desc: "High-frequency upper/lower for advanced lifters", cfg: { split: "upper_lower", days: 6, session: "s90", goal: "both", experience: "advanced", weeks: 6 } },
    { id: "fb3", cat: "Beginner", featured: true, featuredRank: 40, name: "Full Body", tag: "3 days · Beginner", desc: "Hit everything three times a week", cfg: { split: "full_body", days: 3, session: "s60", goal: "hypertrophy", experience: "beginner", weeks: 4 } },
    { id: "fb2", cat: "Beginner", name: "Full Body · 2 Day", tag: "2 days · Beginner", desc: "Minimal-time full body, twice a week", cfg: { split: "full_body", days: 2, session: "s60", goal: "both", experience: "beginner", weeks: 4 } },
    { id: "fb4", cat: "Hypertrophy", name: "Full Body · 4 Day", tag: "4 days · Hypertrophy", desc: "Four rotating full-body sessions for high frequency", cfg: { split: "full_body", days: 4, session: "s90", goal: "hypertrophy", experience: "intermediate", weeks: 6 } },
    { id: "fb5", cat: "Hypertrophy", name: "Full Body · High Frequency", tag: "5 days · Hypertrophy", desc: "High-frequency full body with rotating emphasis and recovery-aware exercise selection", cfg: { split: "full_body", days: 5, session: "s90", goal: "hypertrophy", experience: "intermediate", weeks: 6 } },
    { id: "ulppl5", cat: "Powerbuilding", name: "Upper · Lower · PPL", tag: "5 days · Powerbuilding", desc: "Hybrid blending upper/lower frequency with PPL volume", cfg: { split: "ulppl", days: 5, session: "s90", goal: "both", experience: "intermediate", weeks: 6 } },
    { id: "hybrid5", cat: "Powerbuilding", featured: true, featuredRank: 50, name: "Hybrid Strength + Size", tag: "5 days · Strength + Hypertrophy", desc: "Heavy strength Upper/Lower, then a hypertrophy Push/Pull/Legs", cfg: { split: "hybrid", days: 5, session: "s90", goal: "both", experience: "intermediate", weeks: 6 } },
    { id: "phul", cat: "Powerbuilding", name: "Power / Hypertrophy Upper-Lower", tag: "4 days · Power + Size", desc: "Heavy upper/lower sessions followed by higher-rep upper/lower sessions; Pursuit owns the exact prescription", cfg: { split: "phul", days: 4, session: "s90", goal: "both", experience: "intermediate", weeks: 6 } },
    { id: "phat", cat: "Powerbuilding", name: "Power–Hypertrophy 5-Day", tag: "5 days · Power + Size", desc: "Two power days plus three hypertrophy days", cfg: { split: "phat", days: 5, session: "s90", goal: "both", experience: "advanced", weeks: 6 } },
    { id: "arnold", cat: "Hypertrophy", name: "Chest + Back / Shoulders + Arms / Legs", tag: "6 days · Hypertrophy", desc: "Chest+Back · Shoulders+Arms · Legs, twice, with Pursuit-owned dose and progression", cfg: { split: "arnold", days: 6, session: "s90", goal: "hypertrophy", experience: "advanced", weeks: 6 } },
    { id: "bro5", cat: "Hypertrophy", name: "Bro Split", tag: "5 days · Hypertrophy", desc: "One muscle group per day, maximum volume", cfg: { split: "bro", days: 5, session: "s90", goal: "hypertrophy", experience: "intermediate", weeks: 6 } },
    { id: "bro6", cat: "Hypertrophy", name: "Bro Split · 6 Day", tag: "6 days · Hypertrophy", desc: "Bro split with an extra leg day for more lower-body volume", cfg: { split: "bro", days: 6, session: "s90", goal: "hypertrophy", experience: "advanced", weeks: 6 } },
    { id: "p531", cat: "Strength", featured: true, featuredRank: 70, name: "Main-Lift Waves", tag: "4 days · Strength", desc: "One main barbell lift anchors each day while Pursuit adapts the wave, dose and progression", cfg: { split: "five_three_one", days: 4, session: "s60", goal: "strength", experience: "intermediate", weeks: 4, percentScheme: "531" } },
    { id: "p531beg", cat: "Beginner", featured: true, featuredRank: 80, name: "Main-Lift Waves · Novice", tag: "3 days · Strength + Size", desc: "A novice three-day main-lift wave with two prioritized barbell exposures per session and Pursuit-owned assistance", cfg: { split: "five31_beginner", days: 3, session: "s90", goal: "both", experience: "beginner", weeks: 4, percentScheme: "531beg" } },
    { id: "p531bbb", cat: "Powerbuilding", name: "Main-Lift Waves · High-Volume Supplemental", tag: "4 days · Strength + Size", desc: "Main-lift wave work plus higher-volume supplemental work, with dose adapted to the training goal", cfg: { split: "five_three_one", days: 4, session: "s90", goal: "both", experience: "intermediate", weeks: 4, percentScheme: "531", assistance: "bbb" } },
    { id: "academy", cat: "Tactical", featured: true, featuredRank: 100, name: "Academy Prep", tag: "3 days · Academy fitness test", desc: "Builds the strength base to pass the law-enforcement academy fitness test — push-up & sit-up endurance plus leg and posterior-chain work for the sprint and 1.5-mile run. Pair with Pursuit Rated for the running side.", cfg: { split: "academy_prep", days: 3, session: "s60", goal: "both", experience: "beginner", weeks: 6 } },
    { id: "academy4", cat: "Tactical", name: "Academy Prep · 4 Day", tag: "4 days · Academy fitness test", desc: "Higher-frequency academy prep — four rotating full-body sessions emphasizing the muscles the Cooper-standard test demands", cfg: { split: "academy_prep", days: 4, session: "s60", goal: "both", experience: "beginner", weeks: 6 } },
    { id: "stronglifts", cat: "Beginner", name: "Straight 5×5", tag: "3 days · Beginner strength", desc: "Straight 5×5 on the big barbell lifts, alternating two workouts", cfg: { split: "strength_fb", days: 3, session: "s60", goal: "strength", experience: "beginner", weeks: 4 } },
    { id: "madcow", cat: "Strength", name: "Ramping 5×5", tag: "3 days · Intermediate strength", desc: "Full-body 5×5 ramping to a top set each session", cfg: { split: "strength_fb", days: 3, session: "s60", goal: "strength", experience: "intermediate", weeks: 6, percentScheme: "madcow" } },
    { id: "nsuns", cat: "Powerbuilding", name: "High-Volume Wave LP", tag: "4 days · Strength + Size", desc: "High-volume primary-lift waves plus secondary work, with load and volume progression built into the plan", cfg: { split: "five_three_one", days: 4, session: "s120", goal: "both", experience: "intermediate", weeks: 4, percentScheme: "nsuns", deload: false } },
    { id: "redditppl", cat: "Powerbuilding", name: "Heavy + Volume PPL", tag: "6 days · Powerbuilding", desc: "Six-day push/pull/legs combining prioritized compounds with hypertrophy accessories", cfg: { split: "ppl", days: 6, session: "s90", goal: "both", experience: "intermediate", weeks: 6 } },
    { id: "ppl5", cat: "Hypertrophy", name: "Push / Pull / Legs · 5 Day", tag: "5 days · Hypertrophy", desc: "PPL at five sessions a week for extra push & pull frequency", cfg: { split: "ppl", days: 5, session: "s90", goal: "hypertrophy", experience: "intermediate", weeks: 6 } },
    { id: "texas", cat: "Strength", name: "Volume / Recovery / Intensity", tag: "3 days · Intermediate strength", desc: "Three-day strength undulation across higher-volume, lower-fatigue and intensity-focused sessions", cfg: { split: "texas", days: 3, session: "s90", goal: "strength", experience: "intermediate", weeks: 6, percentScheme: "texas", deload: false } },
    { id: "gzclp", cat: "Beginner", name: "Tiered Linear Progression", tag: "4 days · Beginner strength + size", desc: "Four-day tiered structure with primary strength, secondary volume and accessory work", cfg: { split: "gzclp", days: 4, session: "s90", goal: "both", experience: "beginner", weeks: 6, percentScheme: "gzclp", deload: false } },
    { id: "greyskull", cat: "Beginner", name: "AMRAP Linear Progression", tag: "3 days · Beginner strength", desc: "Linear progression with an AMRAP final set on every main lift to squeeze out extra reps", cfg: { split: "strength_fb", days: 3, session: "s60", goal: "strength", experience: "beginner", weeks: 4 } },
    { id: "gvt", cat: "Hypertrophy", name: "High-Volume Upper / Lower", tag: "4 days · Hypertrophy", desc: "High-volume upper/lower training with productive dose capped by time and recovery rather than a fixed 10×10 promise", cfg: { split: "upper_lower", days: 4, session: "s120", goal: "hypertrophy", experience: "advanced", weeks: 4, percentScheme: "gvt", deload: false } },
    { id: "rippler", cat: "Strength", name: "Tiered Wave", tag: "4 days · Intermediate strength + size", desc: "Tiered strength work with changing intensity emphasis plus secondary volume and accessories", cfg: { split: "rippler", days: 4, session: "s90", goal: "both", experience: "intermediate", weeks: 9, percentScheme: "rippler", deload: false } },
    { id: "jt2", cat: "Powerbuilding", name: "Tiered Powerbuilding", tag: "4 days · Strength + Size", desc: "Tiered powerbuilding balancing heavy primary work with higher-volume secondary and accessory work", cfg: { split: "jt", days: 4, session: "s120", goal: "both", experience: "advanced", weeks: 12, percentScheme: "jt", deload: false } },
    { id: "dbhome", cat: "Home", featured: true, featuredRank: 110, name: "Dumbbell Home", tag: "4 days · Dumbbells + bench", desc: "Full muscle coverage with nothing but dumbbells and a bench — built for the spare-room gym", cfg: { split: "upper_lower", days: 4, session: "s60", goal: "hypertrophy", experience: "intermediate", weeks: 6, equipment: ["dumbbell", "bench"] } },
    { id: "bwanywhere", cat: "Home", name: "Train Anywhere", tag: "3 days · Bands only", desc: "Train in a hotel room, park or living room — bodyweight and a band, 40-minute sessions", cfg: { split: "full_body", days: 3, session: "s40", goal: "hypertrophy", experience: "beginner", weeks: 4, equipment: ["bands"] } },
    { id: "glutebuilder", cat: "Specialization", name: "Glute Builder", tag: "4 days · Lower-body focus", desc: "Upper/lower with doubled glute volume and extra hamstring work — hip thrusts, RDLs and lunges lead", cfg: { split: "upper_lower", days: 4, session: "s90", goal: "hypertrophy", experience: "intermediate", weeks: 6, focus: { glutes: 2, hamstrings: 1 }, focusList: ["glutes", "glutes", "hamstrings"] } },
    { id: "armspec", cat: "Specialization", name: "Arm Specialization", tag: "4 days · Arm focus", desc: "Upper/lower with doubled biceps and triceps volume — curl and extension work layered onto heavy pressing and pulling to bring up lagging arms", cfg: { split: "upper_lower", days: 4, session: "s90", goal: "hypertrophy", experience: "intermediate", weeks: 6, focus: { biceps: 1, triceps: 1 }, focusList: ["biceps", "triceps"] } },
    { id: "deltspec", cat: "Specialization", name: "Boulder Shoulders", tag: "4 days · Delt focus", desc: "Upper/lower with tripled shoulder volume — overhead pressing plus lateral and rear-delt isolation for capped, 3-D delts", cfg: { split: "upper_lower", days: 4, session: "s90", goal: "hypertrophy", experience: "intermediate", weeks: 6, focus: { shoulders: 2 }, focusList: ["shoulders", "shoulders"] } },
    { id: "backspec", cat: "Specialization", name: "Back Width & Thickness", tag: "4 days · Back focus", desc: "Upper/lower with extra lat and upper-back volume — vertical pulls for width, heavy rows for thickness", cfg: { split: "upper_lower", days: 4, session: "s90", goal: "hypertrophy", experience: "intermediate", weeks: 6, focus: { lats: 1, upper_back: 1 }, focusList: ["lats", "upper_back"] } },
    { id: "chestspec", cat: "Specialization", name: "Chest Focus", tag: "4 days · Chest focus", desc: "Upper/lower with doubled chest volume — flat and incline pressing plus a fly variation to build a fuller chest", cfg: { split: "upper_lower", days: 4, session: "s90", goal: "hypertrophy", experience: "intermediate", weeks: 6, focus: { chest: 2 }, focusList: ["chest", "chest"] } },
    { id: "kbhome", cat: "Home", name: "Kettlebell Home", tag: "3 days · Kettlebells", desc: "Full-body strength with kettlebells, a pull-up bar and bands — compact kit, big coverage", cfg: { split: "full_body", days: 3, session: "s40", goal: "both", experience: "intermediate", weeks: 6, equipment: ["kettlebell", "pullup", "bands"] } },
    { id: "machines", cat: "Beginner", name: "Machine Circuit", tag: "3 days · Machines only", desc: "Joint-friendly machines and cables with guided movement paths — ideal for brand-new gym-goers", cfg: { split: "full_body", days: 3, session: "s60", goal: "hypertrophy", experience: "none", weeks: 4, equipment: ["machine", "cable", "smith"] } },
    { id: "fierce5", cat: "Beginner", name: "Balanced Novice A/B", tag: "3 days · Strength + Size", desc: "A balanced novice full-body A/B with prioritized compounds and enough accessory volume to build a physique", cfg: { split: "full_body", days: 3, session: "s60", goal: "both", experience: "beginner", weeks: 6 } },
    { id: "lylegbr", cat: "Hypertrophy", name: "Intermediate Upper / Lower", tag: "4 days · Hypertrophy", desc: "Intermediate upper/lower with moderate productive volume, repeated movement exposure and progression across the week", cfg: { split: "upper_lower", days: 4, session: "s90", goal: "hypertrophy", experience: "intermediate", weeks: 8 } },
    { id: "omnistrength", cat: "Powerbuilding", featured: true, featuredRank: 120, name: "Omni Strength", tag: "4 days · Strength + Aesthetics", desc: "High-frequency full body with a rotating heavy anchor each session and wide exercise variety — built for the jack-of-all-trades lifter chasing strength and an aesthetic physique at once", cfg: { split: "full_body", days: 4, session: "s90", goal: "both", experience: "intermediate", weeks: 8 } },
    { id: "express_fb", cat: "Quick", featured: false, name: "Express Full Body", tag: "3 days · ~40 min", desc: "Auto-supersetted full-body sessions that fit a lunch break — strength and size in 40 minutes flat", cfg: { split: "full_body", days: 3, session: "s40", goal: "both", experience: "intermediate", weeks: 6 } },
    { id: "express_ul", cat: "Quick", name: "Lunch-Break Split", tag: "4 days · ~40 min", desc: "An upper/lower split compressed to 40-minute sessions with paired accessories — busy weeks covered", cfg: { split: "upper_lower", days: 4, session: "s40", goal: "hypertrophy", experience: "intermediate", weeks: 6 } },
    /* ---- Hip-dominant / glute-led -------------------------------------------------------------
       Training doesn't divide by sex, and these aren't labelled as if it did — but the goal cluster
       the library was NOT serving is real: a hip-dominant physique with the upper-body work biased
       toward back and delts rather than chest. Every previous lower day here opened on a squat, so
       "glute training" meant one accessory bolted onto a quad session. The glute_focus split does the
       structural work (a hinge or thrust anchors the day, abduction gets a real slot) and `focus`
       stacks the volume on top. Named for what they train, not for who's expected to run them. */
    { id: "glutes3", cat: "Specialization", featured: true, featuredRank: 30, name: "Glute Builder · 3 Day", tag: "3 days · Glute focus", desc: "Hip thrusts, RDLs and abduction work anchor two hip-dominant lower days, balanced by one pull-led upper day — the shortest week that still builds glutes properly", cfg: { split: "glute_focus", days: 3, session: "s60", goal: "hypertrophy", experience: "beginner", weeks: 6, focus: { glutes: 1 }, focusList: ["glutes"] } },
    { id: "glutes4", cat: "Specialization", name: "Glute Builder · 4 Day", tag: "4 days · Glute focus", desc: "Two hip-anchored lower days and two upper days, with doubled glute volume and extra hamstring work spread across the week", cfg: { split: "glute_focus", days: 4, session: "s90", goal: "hypertrophy", experience: "intermediate", weeks: 6, focus: { glutes: 2, hamstrings: 1 }, focusList: ["glutes", "glutes", "hamstrings"] } },
    { id: "glutes5", cat: "Specialization", name: "Glutes & Core · 5 Day", tag: "5 days · Glute + trunk focus", desc: "The full hip-dominant week — two glute days, two upper days and a dedicated trunk session training abs, low back and the hip abductors directly", cfg: { split: "glute_focus", days: 5, session: "s90", goal: "hypertrophy", experience: "intermediate", weeks: 8, focus: { glutes: 2, abs: 1 }, focusList: ["glutes", "glutes", "abs"] } },
    { id: "deltglute", cat: "Specialization", name: "Delts & Glutes", tag: "4 days · Shoulder + glute focus", desc: "The two muscles that most change a silhouette, trained hard in the same week — lateral and rear delt volume up top, thrusts and abduction below", cfg: { split: "glute_focus", days: 4, session: "s90", goal: "hypertrophy", experience: "intermediate", weeks: 6, focus: { glutes: 2, shoulders: 2 }, focusList: ["glutes", "glutes", "shoulders", "shoulders"] } },
    { id: "lowerlean", cat: "Specialization", name: "Lower Body Focus", tag: "4 days · Legs + glutes", desc: "Three lower days to one upper — quads, hamstrings, glutes and adductors all get direct work, for anyone whose legs are the priority rather than an afterthought", cfg: { split: "glute_focus", days: 4, session: "s90", goal: "hypertrophy", experience: "intermediate", weeks: 6, focus: { glutes: 1, quads: 1, hamstrings: 1 }, focusList: ["glutes", "quads", "hamstrings"] } },
    { id: "fbsculpt", cat: "Beginner", featured: true, featuredRank: 90, name: "Full Body Sculpt", tag: "3 days · Beginner", desc: "A balanced full-body start with extra glute, shoulder and core volume — no 'toning' myths, just the muscle-building work that actually changes how you look", cfg: { split: "full_body", days: 3, session: "s60", goal: "hypertrophy", experience: "none", weeks: 6, focus: { glutes: 1, shoulders: 1, abs: 1 }, focusList: ["glutes", "shoulders", "abs"] } },
    { id: "coreback", cat: "Specialization", name: "Core & Posture", tag: "4 days · Trunk + upper back", desc: "Upper/lower with doubled core and upper-back volume — rows, rear delts and direct trunk work for the desk-bound", cfg: { split: "upper_lower", days: 4, session: "s60", goal: "hypertrophy", experience: "beginner", weeks: 6, focus: { abs: 2, upper_back: 1 }, focusList: ["abs", "abs", "upper_back"] } },
    /* ---- Bodyweight ---------------------------------------------------------------------------
       Minimum kit is a band or a bar, deliberately: with literally nothing, the exercise library has
       zero lat and zero biceps options, so a true no-equipment program would ship a week with no
       back work in it. A £10 band or a doorway bar closes that hole, and both are already first-class
       equipment ids, so the generator handles them without special-casing. */
    /* True zero-equipment. There is no lat or biceps ISOLATION without a band or bar, which is why
       these were held back — but the Inverted Row (a table edge, nothing bought) carries back work
       twice a week, and that is enough for an honest full-body week. These are what make the
       "Bodyweight" filter mean something instead of returning an empty list. */
    { id: "bwzero3", cat: "Home", name: "No Equipment", tag: "3 days · Nothing at all", desc: "Push-ups, squats, lunges, hinges and inverted rows under a table — a complete week needing not one piece of equipment", cfg: { split: "full_body", days: 3, session: "s60", goal: "hypertrophy", experience: "none", weeks: 6, equipment: [] } },
    { id: "bwzerogl", cat: "Specialization", name: "No Equipment · Glutes & Core", tag: "3 days · Nothing at all", desc: "Glute bridges, single-leg hip thrusts and frog pumps with direct trunk work — hip-dominant training that needs no kit and no floor space to speak of", cfg: { split: "glute_focus", days: 3, session: "s60", goal: "hypertrophy", experience: "none", weeks: 6, equipment: [], focus: { glutes: 2, abs: 1 }, focusList: ["glutes", "glutes", "abs"] } },
    { id: "bwband3", cat: "Home", featured: true, featuredRank: 60, name: "Bodyweight & Bands", tag: "3 days · Bands only", desc: "Full-body training with one set of resistance bands — push-ups, squats, hinges and banded pulls, no gym and no weights", cfg: { split: "full_body", days: 3, session: "s60", goal: "hypertrophy", experience: "beginner", weeks: 6, equipment: ["bands"] } },
    { id: "bwbar4", cat: "Home", name: "Calisthenics Foundations", tag: "4 days · Bar + dip + bands", desc: "Pull-ups, dips, push-ups and single-leg work on a four-day rotation — the classic bodyweight progression, run as a real hypertrophy program", cfg: { split: "full_body", days: 4, session: "s60", goal: "hypertrophy", experience: "intermediate", weeks: 8, equipment: ["pullup", "dip", "bands"] } },
    { id: "bwppl", cat: "Home", name: "Calisthenics Push / Pull / Legs", tag: "3 days · Bar + dip + bands", desc: "PPL run on bodyweight and bands — pressing, pulling and single-leg volume split across three focused sessions", cfg: { split: "ppl", days: 3, session: "s60", goal: "hypertrophy", experience: "intermediate", weeks: 6, equipment: ["pullup", "dip", "bands"] } },
    { id: "bwglute", cat: "Specialization", name: "Band Glutes & Core", tag: "3 days · Bands only", desc: "Glute bridges, single-leg hip thrusts and banded abduction plus direct trunk work — a hip-dominant program that fits in a living room", cfg: { split: "glute_focus", days: 3, session: "s40", goal: "hypertrophy", experience: "none", weeks: 6, equipment: ["bands"], focus: { glutes: 2, abs: 1 }, focusList: ["glutes", "glutes", "abs"] } },
    { id: "bwul4", cat: "Home", name: "Calisthenics Upper / Lower", tag: "4 days · Bar + dip + bands", desc: "Four-day upper/lower run without a gym — pull-ups, dips and push-up variations up top, single-leg and hinge work below", cfg: { split: "upper_lower", days: 4, session: "s60", goal: "hypertrophy", experience: "intermediate", weeks: 8, equipment: ["pullup", "dip", "bands"] } },
    /* ---- Home gym ------------------------------------------------------------------------------ */
    { id: "db1pair", cat: "Home", name: "One Pair of Dumbbells", tag: "3 days · Dumbbells only", desc: "No bench, no rack, one pair of dumbbells — floor presses, goblet squats and rows covering every muscle group in three sessions", cfg: { split: "full_body", days: 3, session: "s60", goal: "hypertrophy", experience: "beginner", weeks: 6, equipment: ["dumbbell"] } },
    { id: "dbppl", cat: "Home", name: "Dumbbell Push / Pull / Legs", tag: "3 days · Dumbbells + bench", desc: "The PPL structure run on a home kit — pressing, rowing and leg volume with dumbbells and an adjustable bench", cfg: { split: "ppl", days: 3, session: "s60", goal: "hypertrophy", experience: "intermediate", weeks: 6, equipment: ["dumbbell", "bench"] } },
    { id: "dbglute", cat: "Specialization", name: "Home Glute Builder", tag: "3 days · Dumbbells + bands", desc: "Hip-dominant training for a spare-room setup — dumbbell RDLs, split squats and hip thrusts with banded abduction filling the gaps", cfg: { split: "glute_focus", days: 3, session: "s60", goal: "hypertrophy", experience: "beginner", weeks: 6, equipment: ["dumbbell", "bands", "bench"], focus: { glutes: 2 }, focusList: ["glutes", "glutes"] } },
    { id: "garage4", cat: "Powerbuilding", name: "Garage Gym", tag: "4 days · Barbell + rack", desc: "Barbell, bench, dumbbells and a pull-up bar — the standard home setup, run as a proper strength-and-size upper/lower", cfg: { split: "upper_lower", days: 4, session: "s90", goal: "both", experience: "intermediate", weeks: 8, equipment: ["barbell", "bench", "dumbbell", "pullup"] } },
    { id: "dbul2", cat: "Home", name: "Dumbbell Upper / Lower · 2 Day", tag: "2 days · Dumbbells + bench", desc: "Whole body across two home sessions — the minimum viable week when training time is the constraint, not equipment", cfg: { split: "upper_lower", days: 2, session: "s60", goal: "both", experience: "beginner", weeks: 6, equipment: ["dumbbell", "bench"] } },
    /* ---- The splits that had no template ---------------------------------------------------------
       Five splits shipped with nothing pointing at them: Full Body · Pattern Rotation and the four
       ported afterwards (Torso / Limbs, PPL / Arms, Upper / Lower / Arms, SBD Wave). A split with no
       template is reachable ONLY through Custom split, which is the one route a lifter takes when they
       already know what they want to build — so the splits added to give people new options were
       invisible to exactly the people the options were for. One entry per day count the split offers
       that is worth a distinct recommendation; gates/templatereach.mjs now fails the build if a split
       is ever left unreachable again. Additions only — no existing template's config is touched. */
    { id: "fbpat3", cat: "Powerbuilding", name: "Full Body · Pattern Rotation", tag: "3 days · Strength & Size", desc: "Each session is led by one of the big patterns — squat, hinge, incline press — with the rest of the body trained around it, so a barbell lift anchors every day", cfg: { split: "full_body_patterns", days: 3, session: "s90", goal: "both", experience: "intermediate", weeks: 6 } },
    { id: "fbpat5", cat: "Hypertrophy", name: "Full Body · Pattern Rotation · 5 Day", tag: "5 days · Hypertrophy", desc: "Five rotating full-body sessions, each anchored on a different pattern — high frequency without stacking pressing onto the front delts", cfg: { split: "full_body_patterns", days: 5, session: "s90", goal: "hypertrophy", experience: "intermediate", weeks: 6 } },
    { id: "torso4", cat: "Powerbuilding", name: "Torso / Limbs", tag: "4 days · Strength & Size", desc: "Chest, back and shoulders on one day; legs and arms on the next — arms get a session where they are not already spent from pressing and rowing", cfg: { split: "torso_limbs", days: 4, session: "s90", goal: "both", experience: "intermediate", weeks: 6 } },
    { id: "torso5", cat: "Hypertrophy", name: "Torso / Limbs · 5 Day", tag: "5 days · Hypertrophy", desc: "The torso/limbs alternation run five times a week, so every muscle is trained a little over twice", cfg: { split: "torso_limbs", days: 5, session: "s90", goal: "hypertrophy", experience: "intermediate", weeks: 6 } },
    { id: "ppla4", cat: "Hypertrophy", name: "Push / Pull / Legs / Arms", tag: "4 days · Hypertrophy", desc: "PPL with a fourth session for shoulders and arms, so side delts, biceps and triceps are trained fresh instead of on what is left after pressing", cfg: { split: "ppla", days: 4, session: "s90", goal: "hypertrophy", experience: "intermediate", weeks: 6 } },
    { id: "ppla5", cat: "Hypertrophy", name: "Push / Pull / Legs / Arms · 5 Day", tag: "5 days · Hypertrophy", desc: "The arms-day PPL with a fifth session, adding back the second exposure the four-day version trades away", cfg: { split: "ppla", days: 5, session: "s90", goal: "hypertrophy", experience: "intermediate", weeks: 6 } },
    { id: "ulalt3", cat: "Hypertrophy", name: "Upper / Lower \u00b7 Alternating", tag: "3 days \u00b7 Balanced over a fortnight", desc: "Upper and lower alternate without resetting each week \u2014 sessions run upper, lower, upper then lower, upper, lower, so each gets three sessions per fortnight instead of an uneven two and one", cfg: { split: "upper_lower_alt", days: 3, session: "s60", goal: "hypertrophy", experience: "intermediate", weeks: 6 } },
    { id: "ula5", cat: "Powerbuilding", name: "Upper / Lower / Arms", tag: "5 days · Strength & Size", desc: "Two upper, two lower and a dedicated arm day — the upper/lower frequency with the direct arm volume it usually has no room for", cfg: { split: "ula", days: 5, session: "s90", goal: "both", experience: "intermediate", weeks: 6 } },
    { id: "sbd3", cat: "Strength", name: "Powerlifting · SBD Wave", tag: "3 days · Strength", desc: "The week is built around squat, bench and deadlift, with hypertrophy accessories behind each of them rather than competing for the session", cfg: { split: "sbd_power", days: 3, session: "s90", goal: "strength", experience: "intermediate", weeks: 8 } },
    { id: "sbd4", cat: "Strength", name: "Powerlifting · SBD Wave · 4 Day", tag: "4 days · Strength", desc: "The same competition-lift wave over four sessions, giving the accessory work its own day instead of the tail of a heavy one", cfg: { split: "sbd_power", days: 4, session: "s90", goal: "strength", experience: "advanced", weeks: 8 } },
];

const templateConfig = (t, equipment) => ({
    name: t.name, experience: "intermediate", goal: "hypertrophy", days: 4, split: "full_body", session: "s60",
    focus: {}, focusList: [], reduce: [], progression: "auto", weeks: 4, barbellCap: null, deload: true, percentScheme: null, assistance: null,
    ...t.cfg, equipment: t.cfg.equipment || equipment, // home templates force their own minimal kit
});

const TOTAL_WEEKS = 4;

const APP_VERSION = (typeof __APP_VERSION__ !== "undefined") ? __APP_VERSION__ : "2.6-dev";

const ENGINE_V = 33;

const GENERATION_ROUTE = "pursuit-next-only";

const ENGINE_RULES = {
    preferenceAware: 24,
    stretchOpener: 3,
    shortSessionRows: 7,
    tightPairs: 11,
    primaryPattern: 19,
    repeatPenalty: 13,
    honestBudget: 15,
    axialSpacing: 17,
    longSessionSlots: 18
};

const ruleEngine = (rule) => {
    const at = ENGINE_RULES[rule];
    if (at == null)
        throw new Error("unknown engine rule: " + rule);
    return at;
};

const engHas = (eng, rule) => eng >= ruleEngine(rule);

const engLacks = (eng, rule) => eng < ruleEngine(rule);

const ENGINES = {

    33: { label: "Named programs prescribe their own reps", changes: ["Programs built on a set percentage plan — 5/3/1 and its variants, GZCLP, Texas Method — now show the reps that plan actually calls for on its main lifts. Main-Lift Waves is five reps a set across its waves, for example; the app had been showing a general rep range instead, which did not match the programme you chose."] },



    32: { label: "Volume and recovery, measured the way the research measures them", changes: [
            "Every muscle is now held to the same volume targets instead of a different number for each one. Across sixty-seven studies the growth response turned out to be the same curve for every muscle \u2014 what differs is how much work each already gets from your compound lifts, which Pursuit Iron tracks separately.",
            "Squats no longer count toward hamstring volume, and conventional deadlifts count much less. Imaging studies measure no hamstring growth from squatting: the muscle works hard, but it barely changes length, and length change is what drives growth. Expect more curls, Romanian deadlifts and good mornings."
        ] },
    31: { label: "Your barbell limit is respected everywhere", changes: ["If you cap how many barbell movements a session can have, that limit now holds all the way through. Previously it was applied when the session was first laid out and then ignored by later adjustments, so you could still end up with two barbell lifts — a squat and a row, say — on the same day."] },
    30: { label: "Torso / Limbs trains your delts", changes: ["On the Torso/Limbs split, side and rear delt work now lands on the limbs day alongside your arms \u2014 which is the reason the split exists. Previously it moved your arms there and left the delts behind on an already-full torso day."] },

    29: { label: "Lower days stay lower", changes: ["Shoulder or arm work will no longer be placed on a leg day just because that day had spare time. It already worked this way in reverse — a squat was never bolted onto an upper day — and now both directions match."] },
    28: {
        label: "Days match their own names",
        changes: ["Every training day now states the movement it is built around — a horizontal push day gets a flat press, a hinge day gets a hinge, an incline day gets an incline — instead of leaving it to whichever variation happened to score best."]
    },
    27: {
        label: "Abs, forearms and traps stop being forgotten",
        changes: ["Core and lower back now get a minimum amount of work in every program, and focusing forearms or traps gives them exercises of their own instead of leaving them to whatever your other lifts happen to provide."]
    },
    26: {
        label: "Reducing a muscle actually reduces it",
        changes: ["A muscle you set to reduce no longer competes for its own exercises \u2014 it keeps only what it earns from compound lifts, plus a maintenance slot if it would otherwise fall too low. Previously the setting could leave the number of exercises unchanged."]
    },
    25: {
        label: "More balanced full-body rotation",
        changes: ["Full-body pattern rotation compares a broader layout with its existing layout, preserving muscle coverage, focus work and session limits. Candidate checks cover every training week."]
    },
    24: {
        label: "Your priorities guide program and cycle selection",
        changes: ["Focus points and reduced muscles are interpreted consistently. Reduced muscles get a final maintenance-volume pass without cutting other muscles below their existing coverage. New cycles compare complete candidate cycles while keeping their shared lifts coherent."]
    },
    23: {
        label: "Focusing a small muscle now actually changes your program",
        changes: [
            "The focus picker promises that each level adds an exercise for that muscle, and for the small ones it usually could not keep that promise \u2014 measured, focusing forearms twice left them at zero sets in 58% of programs and neck in 92%, because adding an exercise would break the session's stated exercise count and there was no existing movement to add a set to. A focus point that cannot be filled any other way now takes a slot from a muscle that has sets to spare, and the program is rebuilt both ways and compared, so the trade is kept only when nothing else came off worse."
        ]
    },
    22: {
        label: "Your second curl of the week trains the brachialis",
        changes: [
            "Every program picked the same incline curl for every biceps slot \u2014 measured, one exercise took 53 of 53 slots across the whole config space, because selection ranks by a single priority number and never looks at what the first pick already trained. When the week has two or more biceps slots, the second one now goes to a neutral-grip movement (hammer curl and its variants), which loads the brachialis underneath the biceps rather than repeating the same supinated curl.",
        ]
    },
    21: {
        label: "Full-body pattern rotation trains your delts and stops over-serving legs",
        changes: [
            "The pattern rotation asked for quads and hamstrings on four days each, which took a quarter of every program and split leg work across five movements at two sets apiece. Side and rear delts were not asked for at all, so nine in ten programs came in under the minimum for them. Hamstrings now sit on two days, and the side and rear delt heads are requested directly again. The slots this frees are why focusing a small muscle like forearms can now actually be granted.",
        ]
    },
    20: {
        label: "The pump day stops opening on a squat",
        changes: [
            "The unilateral day in the pattern rotation is meant to be the one day of the week that does not load your spine, but it kept being built around a Smith or hack squat \u2014 so a five-day week could open AND close on a squat. A day can now refuse to be anchored on a movement its own design excludes, and it picks a leg press or a lunge instead. Programs you already saved keep the order they were built with.",
        ]
    },
    19: {
        label: "Two days built on the same movement are not put side by side",
        changes: [
            "The week order compared days on the muscles they shared, so two days whose MAIN lift was the same movement \u2014 two squat-led days, or two hinge-led days \u2014 could still land back to back if the rest of their work differed. Sharing a main movement now counts against putting them on consecutive days.",
        ]
    },
    18: {
        label: "Long sessions fill up to the length you asked for",
        changes: [
            "A two-hour session used to stop early \u2014 days came in around 80 minutes because a day was capped at nine exercises no matter how much time was left. Long sessions can now hold eleven, and a day short of its target is filled with the muscles furthest behind rather than more work for the one that already has the most.",
        ]
    },
    17: {
        label: "Heavy back-to-back days are spaced out",
        changes: [
            "Two days that both load the spine hard \u2014 a deadlift day followed by a squat day \u2014 were only compared on which muscles they shared, and a squat and a deadlift do not share many. They now count as a clash, including between the last day of your week and the first day of the next, which has no rest between it either.",
        ]
    },
    16: {
        label: "Side and rear delts stop being blocked by your pressing volume",
        changes: [
            "Pressing gives the front delt a lot of work, which counted toward your shoulders as a whole and could make the app think your shoulders were already full \u2014 so it refused to add the side and rear delt work you were actually short of. It now reads the same shoulder limit the rest of the app enforces.",
        ]
    },
    15: {
        label: "The session length you asked for is the session length you get",
        changes: [
            "The program builder now budgets the time between exercises \u2014 walking to the next station, loading the bar \u2014 the way the plan screen already showed it. Before, 30% of generated days showed a longer session than the builder believed it had made.",
        ]
    },
    14: {
        label: "A program's assistance setting is honoured when the program is built",
        changes: [
            "\u201cJack Shit\u201d, \u201cTriumvirate\u201d and \u201c5\u00d710 Supplemental\u201d now shape the program the moment it is created or shared, not only after you re-pick them in settings.",
        ]
    },
    /* ENGINE 13 SHIPPED at v623 (the note below is the pre-ship measurement, kept for its numbers).
       ⚠ (was: "gated but not shipped — ENGINE_V is 12")
       It is registered anyway because gates/generator.mjs checks 20 and 23 require the registry to
       cover the shipped engine THE MOMENT ENGINE_V moves, and a behaviour gated on an engine with no
       entry is a half-made change that looks complete until the flag flips. Verified by setting
       ENGINE_V to 13 and running the suite: progcode 8/8 (the code format carries 13 without
       widening), enginefuzz 8/8, changelogcap 11/11, and generator 24/26 — the two failures being
       exactly these registry checks, which this entry answers.
       ⚠ IT IS NOT READY TO SHIP, and the reason is in the numbers. A/B at build 622 confined the whole
       effect to the named-program bucket (the app-designed splits moved no ceiling at all, which is
       what the change intends), but inside that bucket muscle-weeks at zero rose 6,738 → 7,847 and
       side delts at zero rose 12 → 132: pinning the canonical barbell lifts costs more session time
       than the accessory variants it replaces, and the coverage floors cannot recover the difference.
       frontOverMRV 315 → 486 is the one to treat as blocking — volume above maximum recoverable is an
       overreach risk, not a question of how faithful to a published program the app should be.
       ⚠ THOSE FIGURES ARE FROM THE 9,072-CONFIG SWEEP AND MUST BE RE-MEASURED. base.json is now 18,144
       configs (the equipment kits joined it), so the wording above cannot be quoted to a lifter until
       it is re-run — a limited kit is exactly where pinning a barbell lift bites hardest, and that
       population was not in the sweep these numbers came from. */
    13: {
        label: "Programs based on published routines now run the lifts those routines are built on",
        changes: [
            "5/3/1, the GZCL programs, the Texas Method and their relatives are built on specific barbell lifts \\u2014 the whole method is adding weight to the same movement week after week. Picking one of these from the program builder gave you the right day structure but let the app choose each day's main lift, so the deadlift day was often a Romanian deadlift and the bench day an incline press. Chosen from the gallery, the same program was correct. Two different programs under one name.",
            "The main lift is now fixed for these programs whichever way you reach them, and the percentage scheme they are named for comes with them.",
            "Nothing changed for any program the app designs itself, and programs you have already saved keep the engine they were built with.",
        ]
    },
    /* ⚠ THIS ENTRY DESCRIBES THE `emptiesPart` GUARD IN fitSessionTime, WHICH IS STILL MARKED A
       PROTOTYPE THERE. It is registered because ENGINE_V is 12 and gates/generator.mjs checks 20 and
       23 require the registry to cover the shipped engine — an engine whose behaviour is not
       described to the lifter is a defect whatever its merits. Registering it is not a decision to
       ship it: if ENGINE_V goes back to 11 this entry is harmless, and if the guard is reworked the
       wording below must be re-measured before it is trusted.
       The numbers quoted are from an A/B of engine 11 against 12 at build 622, 9,072 configs, taken
       AFTER the day-count duplication in _measure.mjs was fixed — the pre-fix sweep counted
       fixed-schedule splits five times each and every ceiling it ever reported was inflated. */
    12: {
        label: "The clock stops emptying a muscle when it could take the set from somewhere else",
        changes: [
            "When a session runs over its time budget the app removes work until it fits. It ranked what to remove by what each cut would cost, but it had no way to see the difference between taking a muscle from six sets to three and taking it from three sets to nothing. Both read as \\u201cthis muscle is under-trained\\u201d, so emptying one entirely was sometimes ranked among the safest cuts available.",
            "It now prices emptying a muscle separately and takes it last. Across every program the app can build, muscle-weeks trained not at all fell by 564; most of those muscles now sit below the ideal amount instead, which is the same trade the last change made \\u2014 a little of something beats none of it. No program lost a muscle that was previously trained properly.",
            "The programs based on published routines \\u2014 5/3/1, the GZCL family, Texas Method, PHUL, PHAT \\u2014 were measured separately from the ones the app designs itself, because a published program's shape is not the app's choice. Both improved.",
            "Programs you have already saved keep the engine they were built with.",
        ]
    },
    11: {
        label: "Your training days are arranged so back-to-back sessions clash less",
        changes: [
            "The app arranges which day of the week each session lands on, and it was solving the wrong problem: it spread overlap evenly around a ring of days as though every session were the same distance apart. Your week has rest days in it. On a five-day plan only three of the five pairs are genuinely back-to-back, and which two sessions landed next to each other was left to chance.",
            "It now minimises the worst back-to-back clash specifically \u2014 the single morning you would train a muscle that is still sore \u2014 rather than an average nobody experiences. Across every program the app can build, the volume shared by consecutive days fell by nearly half at the median, and the worst case on a split routine dropped from 28 shared sets to 15.",
            "No exercise selection changed and no muscle gets more or less work. Only the order of your week moved. Programs you have already saved keep the schedule they were built with.",
        ]
    },
    10: {
        label: "Sessions priced honestly, and shoulders finally get trained",
        changes: [
            "The app decided whether an exercise fitted your session using a cost that left out setup, walking to the machine and loading the bar \u2014 while the duration it SHOWED you included all three. So it packed sessions against a budget it was not really keeping, and the first thing squeezed out was the small isolation work at the end.",
            "Side and rear delts were the casualty: about one program in ten trained your side delts not at all, and nothing in the app could see it because they are a part of the shoulder rather than a muscle of their own. That is now down to roughly one in a thousand.",
            "Exercises are now priced the way sessions are actually timed. Across every program the app can build, muscles trained not at all dropped by 7,630 muscle-weeks. Some of those now sit below the ideal amount rather than at zero, which is the trade \u2014 a little of something beats none of it \u2014 and no session got longer.",
        ]
    },
    8: {
        label: "Competing needs are settled by one rule",
        changes: [
            "When two parts of your program both needed work and there was room for only one, whichever check happened to run first simply won. A muscle getting no work at all could lose a slot to a muscle that was already trained and merely wanted a little more.",
            "There is now one rule: a muscle with nothing beats a muscle that is short, which beats a preference. Everything states what it needs and one decision point settles it.",
            "Focusing a muscle also changed. It used to make that muscle win more contests for a slot, and the slots it won were isolation work \u2014 so your presses got traded for raises and focusing shoulders could leave you with LESS shoulder work than not focusing at all. Focus now asks for an extra slot outright, and loses to any muscle that is not yet trained enough.",
        ]
    },
    9: {
        label: "Reducing a muscle now means less, not none",
        changes: [
            "Asking for less work on a muscle used to make it lose out on exercises rather than simply doing fewer of them \u2014 and in about one program in four it dropped that muscle from the week entirely. It could also make your week LONGER overall, because the freed time quietly went to other muscles.",
            "A reduced muscle is now held to a light maintenance amount: fewer sets than normal, but never nothing. Time freed up is only used if something else genuinely needs it, so asking for less work now actually gives you less work.",
        ]
    },
    7: {
        label: "Short sessions stay short",
        changes: [
            "A twenty-minute session was allowed to grow to six exercises while the app told you to expect five \u2014 a number that was never true, and was actually higher than the one shown for a forty-minute session.",
            "Short sessions are now held to what fits the time you gave, and the exercise count shown while you pick a session length is the count you will actually get.",
        ]
    },
    6: {
        label: "Every week trains every movement",
        changes: [
            "About one program in ten contained no pulling work at all \u2014 no rows, no pulldowns, nothing for your back \u2014 for the entire week. It was worst on short sessions, and it happened even with a full gym available.",
            "When there is no room to add a movement, the app now trades the least necessary exercise in your week for one that fills the gap, rather than leaving the gap. Your main lift is never the one traded, and a session never gets longer.",
        ]
    },
    5: {
        label: "Volume decided when the plan is written",
        changes: [
            "For a muscle you train on back-to-back days, spare volume is removed while the program is being built instead of a set being trimmed from most sessions. The plan you read is the plan you train.",
            "Only volume the app already treated as expendable is removed, and never below what a muscle needs to grow. Your main lift and anything you asked for more of are untouched.",
        ]
    },
    4: {
        label: "Loadable main lifts",
        changes: [
            "Strength and peak blocks build on a barbell lift you can add weight to, instead of whichever lift suited muscle growth best.",
            "Trimming a session to fit the clock no longer drops your last side- or rear-delt movement.",
            "Filling a muscle's remaining volume avoids repeating a movement pattern the day already has.",
        ]
    },
    3: { label: "Lengthened-position coverage", changes: ["Each muscle gets at least some work in a stretched position each week."] },
    2: { label: "Baseline", changes: [] }
};

const engineInfo = (v) => ENGINES[v] || { label: `Engine ${v}`, changes: [] };

function publicTrainingCopy(value) {
    return String(value ?? "")
        .replace(/\bPursuit Engine(?:\s+\d+(?:\.\d+)*)?/gi, "Pursuit Iron")
        .replace(/\bengine[- ]version(s)?\b/gi, (_, plural) => `program version${plural || ""}`)
        .replace(/\b(newer|older|new) engine\b/gi, (m, age) => `${age} program version`)
        .replace(/\bengine update(s)?\b/gi, (_, plural) => `program update${plural || ""}`)
        .replace(/\bengine improvement(s)?\b/gi, (_, plural) => `program-builder improvement${plural || ""}`)
        .replace(/\bengine rule(s)?\b/gi, (_, plural) => `program rule${plural || ""}`)
        .replace(/\bvolume repair engine\b/gi, "volume repair process")
        .replace(/\bprogram engine\b/gi, "program builder")
        .replace(/\bengine\s+\d+(?:\.\d+)*\b/gi, "that program version")
        .replace(/\bengine's\b/gi, "program logic's")
        .replace(/\bengine\b/gi, "program logic")
        .replace(/\bscorer(s)?\b/gi, (_, plural) => `exercise selection${plural ? " systems" : " logic"}`)
        .replace(/\baudited\b/gi, "checked")
        .replace(/\baudit\b/gi, "quality check")
        .replace(/\bcanary\b/gi, "preview")
        .replace(/\bshadow\b/gi, "comparison");
}

const BUG_EMAIL = "pursuitiron@gmail.com";

const BUILD_NUM = (typeof __BUILD__ !== "undefined") ? __BUILD__ : "dev";

const SUPPORT_URL = "https://ko-fi.com/extracteddeath";

const SUPPORT_BLURB = "This app is built by a tiny independent team and is completely free — no ads, no accounts, no subscriptions, and your data never leaves your device. If it's helped your training, a small tip keeps development going and new features coming.";

const weeksOf = program => programWorkingWeeks(program, TOTAL_WEEKS);

function blockPhase(program, weekIndex, singleWeekVal = 0.5) {
    const weeks = weeksOf(program);
    let phase = weeks <= 1 ? singleWeekVal : clamp((weekIndex - 1) / Math.max(1, weeks - 1), 0, 1);
    const pw = program?.config?.phaseWindow;
    if (!Number.isFinite(phase))
        phase = 0;
    if (Array.isArray(pw) && pw.length === 2 && pw.every(Number.isFinite) && pw[0] >= 0 && pw[1] <= 1 && pw[0] <= pw[1])
        phase = pw[0] + phase * (pw[1] - pw[0]);
    return phase;
}

const goalForDay = (program, day) => (day && day.focus) || program.config.goal;

const clamp = (n, lo, hi) => Math.max(lo, Math.min(hi, n));

const uid = () => Math.random().toString(36).slice(2, 9);

function availableFor(part, equipSet, banned, noBw = false) {
    return EXERCISES.filter(e => e.part === part && !banned.includes(e.id) && e.equip.every(q => equipSet.has(q)) && (e.equip.length > 0 || !noBw));
}

const TECHNICAL_LIFTS = new Set([
    "good-morning", "zercher-squat", "snatch-deadlift", "deficit-barbell-deadlift",
    "jefferson-curl", "jefferson-squat", "pistol-squat", "z-press", "bradford-press",
    "power-shrug", "pendlay-row", "meadows-row", "ghr", "copenhagen",
]);

function swapOverlapNames(candidate, peers = [], program = null, day = null) {
    const def = NEXT_EXERCISE_MAP.get(nextExerciseIdForShellExercise(candidate));
    if (!def)
        return [];
    const role = def.flags.compound ? "hypertrophy_compound" : "hypertrophy_isolation";
    return peers.filter(Boolean).filter(peer => {
        const pd = NEXT_EXERCISE_MAP.get(nextExerciseIdForShellExercise(peer));
        if (!pd)
            return false;
        const slot = day?.exercises?.indexOf(peer.id);
        const peerRole = slot >= 0 ? (getNextShellCell(program, day, slot, 1)?.role ?? program?.overrides?.[`${day.id}:${slot}`]?.role) : null;
        return avoidableExerciseOverlap(def, role, [{ def: pd, role: peerRole || (pd.flags.compound ? "hypertrophy_compound" : "hypertrophy_isolation") }], { priority: "normal" });
    }).map(peer => peer.name);
}

function rankSwapAlts(cur, pool, starved = null, peers = [], program = null, day = null) {
    const curPat = movePattern(cur);
    const overlap = new Map(pool.map(ex => [ex.id, swapOverlapNames(ex, peers, program, day).length]));
    const fills = (ex) => (starved && starved.size && starved.has(subRegionOf(ex)) && subRegionOf(ex) !== subRegionOf(cur)) ? 0 : 1;
    return [...pool].sort((a, b) => {
        const redundancy = overlap.get(a.id) - overlap.get(b.id);
        if (redundancy)
            return redundancy;
        const fa = fills(a) - fills(b);
        if (fa)
            return fa;
        const pa = (movePattern(a) === curPat ? 0 : 1) - (movePattern(b) === curPat ? 0 : 1);
        if (pa)
            return pa;
        const ta = (a.type === cur.type ? 0 : 1) - (b.type === cur.type ? 0 : 1);
        if (ta)
            return ta;
        return (a.pri || 0) - (b.pri || 0);
    });
}

function starvedRegions(program) {
    const out = new Set();
    {
        const sv = weeklySubVolume(program, weeksOf(program));
        for (const [region, L] of Object.entries(SUB_LANDMARKS))
            if (L?.mev && (sv[region] || 0) < L.mev)
                out.add(region);
    }
    return out;
}

function axialCost(ex, eng = 1) { return isAxialLoad(ex, eng) ? (isBarLike(ex.equip) ? 1.0 : 0.7) : 0; }

const EQUIP_IMPLIES = { inclinebench: ["bench"], declinebench: ["bench"] };

function expandEquipment(list) {
    const out = new Set(list || []);
    for (const id of [...out])
        (EQUIP_IMPLIES[id] || []).forEach(x => out.add(x));
    /* A barbell implies a rack unless the gym says "No squat rack" — see the note on EQUIPMENT (02-static-data). */
    if (out.has("barbell") && !out.has("no-rack"))
        out.add("rack");
    return out;
}

const sameProgramContent = (a, b) => {
    if (a === b)
        return true;
    if (!a || !b)
        return false;
    const strip = ({ updatedAt, autoBias, autoTuned, ...rest }) => rest;
    return JSON.stringify(strip(a)) === JSON.stringify(strip(b));
};

function programSavePlan(edited, saved, cycles, scope = "program") {
    const before = saved.find(p => p.id === edited?.id);
    if (!edited || !before)
        return [];
    let candidates = [edited];
    if (scope === "cycle" && edited.cycleId) {
        const siblings = saved.filter(p => p.id !== edited.id && p.cycleId === edited.cycleId);
        const adapt = !!cycles.find(c => c.id === edited.cycleId)?.adaptExercises;
        candidates = [...candidates, ...propagateCycleEditsPure(edited, before, siblings, adapt)];
    }
    return candidates.flatMap(after => {
        const prior = saved.find(p => p.id === after.id);
        return prior && !sameProgramContent(prior, after) ? [{ before: prior, after }] : [];
    });
}

function programChangeLabels(before, after) {
    const changed = key => JSON.stringify(before?.[key]) !== JSON.stringify(after?.[key]);
    const labels = [];
    if (changed("name"))
        labels.push("Program name");
    if (changed("days"))
        labels.push("Training days, exercises or order");
    if (["overrides", "slotBias", "nextWeekPrescriptions", "progStyle"].some(changed))
        labels.push("Set, rep or effort targets");
    if (changed("ss"))
        labels.push("Supersets");
    if (["schedule", "weekPlan", "scheduleBase"].some(changed))
        labels.push("Schedule");
    if (["config", "weeks", "nextEngine"].some(changed))
        labels.push("Program settings or generated plan");
    if (["folder", "description"].some(changed))
        labels.push("Folder or description");
    return labels.length ? labels : ["Program details"];
}

function canUndoProgramSave(transaction, saved, drafts, working) {
    return !!transaction?.changes?.length && transaction.changes.every(({ after }) => {
        const current = saved.find(p => p.id === after.id);
        return sameProgramContent(current, after)
            && (!drafts[after.id] || sameProgramContent(drafts[after.id], after))
            && (working?.id !== after.id || sameProgramContent(working, after));
    });
}

function backupRecordImpact(before, after) {
    return [["saved", "Programs"], ["history", "Workouts"], ["cycles", "Cycles"], ["custom", "Exercises"], ["gyms", "Gyms"]].map(([key, label]) => {
        const old = new Map((before[key] || []).map(r => [r.id, r]));
        const next = new Map((after[key] || []).map(r => [r.id, r]));
        let added = 0, updated = 0, kept = 0;
        next.forEach((r, id) => { if (!old.has(id))
            added++;
        else if (!sameProgramContent(old.get(id), r))
            updated++;
        else
            kept++; });
        const removed = [...old.keys()].filter(id => !next.has(id)).length;
        return { key, label, added, updated, kept, removed, total: next.size };
    });
}

const SET_ROW_BLEED = 16;

const REDUCE_FRACTION = 0.6;

const MRV_GRAIN = 0.5;

function dayMuscleLoad(program, day) {
    const load = {};
    day.exercises.forEach((id, si) => {
        const ex = EX_BY_ID[id];
        if (!ex)
            return;
        const sets = baseSetsFor(program.config, ex, si === day.primaryIndex);
        load[ex.part] = (load[ex.part] || 0) + sets;
        secondaryOf(ex, program.engineV).forEach(([pp, f]) => { load[pp] = (load[pp] || 0) + sets * f; });
    });
    return load;
}

function dayOverlap(a, b) {
    let s = 0;
    for (const part in a)
        if (b[part])
            s += Math.min(a[part], b[part]);
    return s;
}

const AXIAL_ADJACENT_COST = 6;

const IDEAL_SLOTS = {
    1: [0],
    2: [0, 4], // Mon, Fri  — 3+3 gap (symmetric, familiar)
    3: [0, 2, 4], // Mon, Wed, Fri — 1+1+2 (classic 3-day)
    4: [0, 2, 4, 6], // Mon, Wed, Fri, Sun — 1+1+1+0 with 0-gap at wrap
    5: [0, 1, 3, 4, 6], // Mon,Tue,Thu,Fri,Sun — 0,1,0,1,0 symmetric
    6: [0, 1, 2, 3, 4, 6], // Mon–Fri + Sun — only Sat off
};

function buildWeekPlan(program, loadOf = dayMuscleLoad, eng = (program && program.engineV) || 0) {
    const n = program.days.length;
    if (n <= 0)
        return null;
    if (n >= 7)
        return program.days.map((_, i) => i); // train every day, no room for rest
    const loads = program.days.map(d => loadOf(program, d));
    // Optimal cyclic ordering: try every distinct arrangement of days around the week-cycle
    // and keep the one with the lowest TOTAL adjacent-day overlap (summed over every
    // consecutive pair, wrapping last→first). n is always ≤6 here (n≥7 returns above), so
    // fixing day 0 first and permuting the rest is at most 5! = 120 arrangements — a greedy
    // nearest-neighbor chain can get trapped in a local optimum on "ring"-shaped overlap data
    // (e.g. Legs→Push shares chest, Push→Pull shares shoulders/triceps, …, Pull→Legs closes
    // the ring) where a different arrangement reaches zero total overlap that greedy can\'t see
    // because it commits to each next-best step without looking ahead. Brute force costs <5ms
    // even at n=6 and runs once per program generation, so there\'s no reason to settle for greedy.
    /* Positions are needed BEFORE scoring now, since the objective depends on which gaps are real. */
    const posForScore = IDEAL_SLOTS[n] || null;
    const tightPairs = [];
    if (posForScore) {
        for (let k = 0; k + 1 < n; k++)
            if (posForScore[k + 1] - posForScore[k] === 1)
                tightPairs.push([k, k + 1]);
        if (n > 1 && (7 - posForScore[n - 1] + posForScore[0]) === 1)
            tightPairs.push([n - 1, 0]);
    }
    const useTight = engHas(eng, "tightPairs") && tightPairs.length > 0;
    const restIdx = Array.from({ length: n }, (_, i) => i).filter(i => i !== 0);
    function permute(arr) {
        if (arr.length <= 1)
            return [arr];
        const res = [];
        for (let i = 0; i < arr.length; i++) {
            const rest = [...arr.slice(0, i), ...arr.slice(i + 1)];
            permute(rest).forEach(p => res.push([arr[i], ...p]));
        }
        return res;
    }
    let order = [0, ...restIdx], bestTotal = Infinity, bestMax = Infinity;
    /* Engine 11 must consider rotations, so it permutes every day; earlier engines pin day 0 and
       permute the rest, which is what makes them bit-identical to before. */
    const arrangements = useTight
        ? permute(Array.from({ length: n }, (_, i) => i))
        : permute(restIdx).map(rest => [0, ...rest]);
    arrangements.forEach(candidate => {
        let total = 0, maxAdj = 0;
        /* ⚠ ENGINE 13: THE SAME LIFT ON TWO CONSECUTIVE DAYS IS WORSE THAN ANY MUSCLE OVERLAP.
           `dayOverlap` compares muscle loads, which is the right measure when the two days hold
           DIFFERENT exercises. A named program can hold the same one twice: GZCLP squats as the T1 of
           A1 and again as the T2 of A2, and its published schedule is Mon/Wed/Fri precisely so those
           never land back to back. Laid out as four days in a week they can, and muscle overlap alone
           does not object loudly enough — measured, gates/dayorder check 2 found 6 repeats, e.g.
           gzclp/4/s90 with Back Squat and Deadlift each on consecutive days.
           Repeating the exact movement is a different event from training the same muscle twice: it is
           the same bar, the same groove, the same joints, at the same intensity, without a day between.
           Cost it above any overlap value (dayOverlap is bounded by 1) so the search treats avoiding it
           as the first duty and falls back to minimising overlap once no repeat-free order exists. */
        /* ⚠ TWO HEAVY SPINAL DAYS BACK TO BACK. `dayOverlap` compares MUSCLE loads, and a deadlift day
           and a squat day barely overlap by that measure — hamstrings/glutes/back against quads — so the
           search happily put them next to each other. Axial loading is a systemic cost the muscle model
           cannot see: same spine, same bracing, same connective tissue, one night apart. MEASURED at
           engine 16: 145 of 456 adjacent training-day pairs (31.8%) had BOTH days carrying heavy axial
           load. Reported by Haiden on his own 5-day full-body-patterns program, where the week wraps with
           no rest between the last day and the first.
           Priced BETWEEN muscle overlap (bounded by 1) and repeating the exact lift (2 per repeat): worse
           than sharing a muscle, not as bad as the same bar in the same groove. `axialCost` is the same
           owner `rankFloorFill` uses, so the two passes agree on what "heavy" means. */
        /* ⚠ THE WEIGHT IS ON `dayOverlap`'S SCALE, NOT ON THE ONE THE COMMENTS CLAIM. Two places here say
           dayOverlap is "bounded by 1"; measured, it runs 0 to 21.4 with a median of 10.3. A penalty of 1
           or 2 is therefore a rounding error against a typical overlap, which is why the first version of
           this term barely moved the number (145 → 123 adjacent heavy-axial pairs) and why engine 13's
           repeat penalty of 2 is worth about a fifth of one overlap rather than more than all of it.
           Swept: 3 → 98, 6 → 76, 10 → 68, against mean adjacent overlap rising 5.86 → 5.95 / 6.13 / 6.28.
           Six nearly halves the clash for under 5% more muscle overlap; ten buys 8 more pairs for another
           2.5%. (The engine-13 repeat price is NOT re-tuned here — only 2 of 456 adjacent pairs repeat a
           lift, so it works despite the mispricing, and changing it is its own measured pass.) */
        const axialOf = (i) => (program.days[candidate[i]]?.exercises || [])
            .reduce((a, id) => a + (EX_BY_ID[id] ? axialCost(EX_BY_ID[id]) : 0), 0);
        const axialPenalty = (x, y) => {
            if (engLacks(eng, "axialSpacing"))
                return 0;
            return AXIAL_ADJACENT_COST * Math.min(axialOf(x), axialOf(y));
        };
        /* ⚠ ENGINE 19: THE DAY ORDER PRICED MUSCLES, THE BAR, AND THE SPINE — NOT THE MOVEMENT.
           Reported by Haiden on his own 5-day full_body_patterns program: "squats at the start and end of
           every week". Reproduced exactly — fb2_a opens on hack-squat and fb2_e closes on smith-squat, and
           under the schedule those two land on Thursday and Friday, back to back.
           WHY NOTHING CAUGHT IT. Two different squat variants are not the same exercise, so `repeatPenalty`
           sees nothing. They are not both heavy-axial, so `axialPenalty` is small. And their muscle overlap
           alone loses to the alternative orderings. The search was never told that squatting on consecutive
           days is its own event, distinct from training quads twice.
           MEASURED BEFORE THE FIX: 40 of 3,648 adjacent calendar-day pairs across the config space put
           the same primary pattern back to back, clustered in full_body and full_body_patterns.
           ⚠ PRICED AT 1, AND THE PRICE IS CAPPED BY A BOUND THIS TERM DID NOT SET. An earlier version of
           this comment said 6 while the constant read 2, then 3, then 2 again — and NONE of those values
           is shippable. Swept against gates/axialspacing check 5, which holds mean adjacent-day muscle
           overlap to under +5%:
               cost  0   40 clashes   overlap 5.852 (baseline)   gate 6/6
               cost  1   21 clashes   overlap within bound       gate 6/6
               cost  2   14 clashes   overlap 6.166  (+5.4%)     gate FAILS
               cost  6    1 clash     overlap 6.182  (+5.6%)     gate FAILS
               cost 12    0 clashes   overlap beyond bound       gate FAILS
           So 1 is the largest price the existing bound allows, and it halves the clash rather than
           clearing it. THE REMAINING HALF IS A PRODUCT DECISION, NOT A TUNING PROBLEM: is squatting on
           two consecutive days worse than a 5.6% rise in mean adjacent muscle overlap? If it is, check 5's
           bound is the thing to change, deliberately and with its own justification — not this constant
           quietly, which is what raising it past 1 amounts to.
           ⚠ AND MY OWN SWEEP DISAGREED WITH THE GATE AND THE GATE WAS RIGHT. Measured over s60+s90 only,
           overlap looked FLAT across costs 0..12 (5.9813 to 5.9854, a 0.07% spread) and 12 looked free.
           The gate sweeps a wider population and sees +5.6%. A measurement's scope is not the gate's
           scope, and the narrower one produced a confident wrong answer — the third time in this file's
           history that a conclusion was really a statement about where someone looked.
           Only the PRIMARY is compared. Accessories repeat patterns constantly and should. */
        const PATTERN_ADJACENT_COST = 2;
        const primaryPatternOf = (i) => {
            const day = program.days[candidate[i]];
            const id = day && day.exercises ? day.exercises[day.primaryIndex] : null;
            const ex = id ? EX_BY_ID[id] : null;
            return ex ? movePattern(ex) : null;
        };
        const patternPenalty = (x, y) => {
            if (engLacks(eng, "primaryPattern"))
                return 0;
            const a = primaryPatternOf(x), b = primaryPatternOf(y);
            return a && b && a === b ? PATTERN_ADJACENT_COST : 0;
        };
        const repeatPenalty = (x, y) => {
            const A = program.days[candidate[x]]?.exercises, B = program.days[candidate[y]]?.exercises;
            if (!A || !B)
                return 0;
            let hits = 0;
            for (const id of A)
                if (B.includes(id))
                    hits++;
            /* ⚠ 2 WAS PRICED AGAINST A dayOverlap THE COMMENT BELIEVED WAS "bounded by 1". It runs to 21.4.
               So the penalty the engine-13 note calls "the first duty" has been worth about a fifth of one
               typical overlap since it shipped — it worked anyway only because repeats are rare (2 of 456
               adjacent pairs). Engine 17 adds an axial term at 6, which would OUTRANK it and start trading
               a repeated lift away for spinal spacing — gates/dayorder checks 1 and 2 caught exactly that
               (one repeat, one 0.74 overlap pair). Repriced above the observed overlap maximum so the
               stated priority — same bar back to back is worse than any muscle overlap — is finally true. */
            return hits * (engHas(eng, "axialSpacing") ? 25 : 2);
        };
        if (useTight) {
            for (const [a, b] of tightPairs) {
                const ov = dayOverlap(loads[candidate[a]], loads[candidate[b]]) + (engHas(eng, "repeatPenalty") ? repeatPenalty(a, b) : 0) + axialPenalty(a, b) + patternPenalty(a, b);
                total += ov;
                if (ov > maxAdj)
                    maxAdj = ov;
            }
        }
        else {
            for (let i = 0; i < n; i++) {
                const ov = dayOverlap(loads[candidate[i]], loads[candidate[(i + 1) % n]])
                    + patternPenalty(i, (i + 1) % n)
                    + (engHas(eng, "repeatPenalty") ? repeatPenalty(i, (i + 1) % n) : 0)
                    + axialPenalty(i, (i + 1) % n);
                total += ov;
                if (ov > maxAdj)
                    maxAdj = ov; // the single worst back-to-back conflict in this arrangement
            }
        }
        // Primary objective: lowest total overlap around the whole week (wrap included). Tiebreak:
        // among equally-low totals, prefer the arrangement whose WORST single adjacent pair is least
        // conflicting — so an unavoidable back-to-back (common in 4/5/6-day weeks) never lands the two
        // most-overlapping days next to each other when a gentler pairing was available.
        /* ⚠ ENGINE 11 INVERTS THE TWO OBJECTIVES, AND THE ORDER OF THEM IS THE WHOLE POINT.
           Minimising TOTAL first accepts one bad collision to shave several trivial ones: measured,
           81 of 456 weeks shipped a worse worst-pair than was available, e.g. full_body/4/s60/both at
           0.29 where 0.06 existed. But a lifter does not experience a sum over the week — they
           experience the one morning they train a muscle that is still sore. The single worst
           back-to-back conflict is the quantity to minimise; total is the tiebreak between orders that
           are equally good on it. The pre-11 comment already said as much when it introduced maxAdj as
           a tiebreak ("never lands the two most-overlapping days next to each other when a gentler
           pairing was available") — engine 11 promotes that from tiebreak to objective.
           Earlier engines keep total-first exactly, so their schedules do not move. */
        const better = useTight
            ? (maxAdj < bestMax - 1e-9 || (Math.abs(maxAdj - bestMax) <= 1e-9 && total < bestTotal - 1e-9))
            : (total < bestTotal - 1e-9 || (Math.abs(total - bestTotal) <= 1e-9 && maxAdj < bestMax - 1e-9));
        if (better) {
            bestTotal = total;
            bestMax = maxAdj;
            order = candidate;
        }
    });
    // Choose slot positions: use the pre-computed ideal set when available, fall back to
    // the rounding approach for edge cases. Ideal sets push forced back-to-back pairs
    // to the week wrap (Sun→Mon) where a rest day following Sunday is most natural,
    // and avoid the mid-week Fri→Sat collision the old rounding produced for 4-day plans.
    const idealPositions = IDEAL_SLOTS[n];
    let positions;
    if (idealPositions) {
        positions = idealPositions;
    }
    else {
        const step = 7 / n, taken = new Set();
        positions = [];
        for (let k = 0; k < n; k++) {
            let pos = Math.round(k * step);
            while (taken.has(pos) && taken.size < 7)
                pos = (pos + 1) % 7; // all 7 taken (n>7, corrupt program) → allow the duplicate rather than spin forever
            taken.add(pos);
            positions.push(pos);
        }
    }
    // No rotation step needed: `order` is already the globally-optimal CYCLE (lowest total
    // adjacent overlap summed around the full week, wrap included), found by the brute-force
    // search above. Rotating a cyclic sequence relabels which day is "first" but can\'t change
    // which days end up adjacent to each other, so every rotation of this same order has the
    // identical total overlap — there\'s no remaining slot-position-specific optimization to do.
    const slots = new Array(7).fill(null);
    positions.forEach((pos, k) => { slots[pos] = order[k]; });
    return slots;
}

const EX_FAMILY = {
    // triceps pushdowns (cable, vertical pressdown — swap the handle)
    "pushdown": "tri-pushdown", "rope-pushdown": "tri-pushdown", "ez-pushdown": "tri-pushdown",
    "v-bar-pushdown": "tri-pushdown", "straight-bar-pushdown": "tri-pushdown",
    "dual-rope-pushdown": "tri-pushdown", "single-pushdown": "tri-pushdown", "underhand-pushdown": "tri-pushdown",
    // triceps overhead extensions
    "oh-cable-ext": "tri-ohext", "db-oh-ext": "tri-ohext", "ez-oh-ext": "tri-ohext", "bands-tricep-ext": "tri-ohext",
    // triceps kickbacks
    "tricep-kickback": "tri-kickback", "cable-tri-kickback": "tri-kickback",
    // side-delt lateral raises
    "lat-raise": "side-raise", "cable-lat-raise": "side-raise", "machine-lat-raise": "side-raise",
    "leaning-lat-raise": "side-raise", "single-cable-raise": "side-raise",
    "seated-db-lat-raise": "side-raise", "single-db-lat-raise": "side-raise", "chest-supported-lat-raise": "side-raise",
    "seated-cable-lat-raise": "side-raise", "cuff-cable-lat-raise": "side-raise",
    "band-lateral-raise": "side-raise", "side-lying-raise": "side-raise", "cable-behind-back-lateral": "side-raise",
    // rear-delt flyes
    "rear-fly": "rear-delt-fly", "reverse-pec": "rear-delt-fly", "cable-rear-fly": "rear-delt-fly",
    "seated-rear-fly": "rear-delt-fly", "single-cable-rear-fly": "rear-delt-fly", "chest-supported-rear-fly": "rear-delt-fly", "prone-rear-delt-raise": "rear-delt-fly",
    // chest flyes (flat)
    "cable-fly": "chest-fly", "pec-deck": "chest-fly", "db-fly": "chest-fly",
    // glute kickbacks
    "cable-kickback": "glute-kickback", "glute-kickback-machine": "glute-kickback"
};

const VARIANT_PRESENTATION = {
    // Triceps pushdowns
    "pushdown": { key: "pushdown-straight", name: "Straight-Bar Pushdown", setup: "Straight bar", detail: "Straight-bar attachment", visual: "straight-bar", order: 40 },
    "straight-bar-pushdown": { key: "pushdown-straight", name: "Straight-Bar Pushdown", setup: "Straight bar", detail: "Straight-bar attachment", visual: "straight-bar", order: 40, preferred: true },
    "rope-pushdown": { key: "pushdown-rope", name: "Rope Pushdown", setup: "Rope attachment", detail: "Rope attachment", visual: "rope", order: 10 },
    "dual-rope-pushdown": { key: "pushdown-dual-rope", name: "Dual-Rope Pushdown", setup: "Dual rope", detail: "Dual-rope attachment", visual: "dual-rope", order: 20 },
    "v-bar-pushdown": { key: "pushdown-vbar", name: "V-Bar Pushdown", setup: "V-bar", detail: "V-bar attachment", visual: "v-bar", order: 30 },
    "ez-pushdown": { key: "pushdown-ez", name: "EZ-Bar Pushdown", setup: "EZ-bar", detail: "EZ-bar attachment", visual: "ez-bar", order: 50 },
    "single-pushdown": { key: "pushdown-single", name: "Single-Arm Pushdown", setup: "Single handle", detail: "Single D-handle", visual: "single-handle", order: 60 },
    "underhand-pushdown": { key: "pushdown-underhand", name: "Reverse-Grip Pushdown", setup: "Underhand bar", detail: "Straight bar · underhand grip", visual: "underhand-bar", order: 70 },
    // Triceps overhead extensions
    "oh-cable-ext": { key: "ohext-rope", name: "Rope Overhead Cable Extension", setup: "Rope attachment", detail: "High cable · rope attachment", visual: "overhead-rope", order: 10 },
    "db-oh-ext": { key: "ohext-db", name: "DB Overhead Extension", setup: "Single dumbbell", detail: "Single dumbbell", visual: "overhead-dumbbell", order: 20 },
    "ez-oh-ext": { key: "ohext-ez", name: "EZ-Bar Overhead Extension", setup: "EZ curl bar", detail: "EZ curl bar", visual: "overhead-ez", order: 30 },
    "bands-tricep-ext": { key: "ohext-band", name: "Banded Overhead Tricep Extension", setup: "Resistance band", detail: "Anchored resistance band", visual: "overhead-band", order: 40 },
    // Triceps kickbacks
    "tricep-kickback": { key: "tri-kickback-db", name: "Dumbbell Triceps Kickback", setup: "Dumbbell", detail: "Single dumbbell", visual: "triceps-kickback-db", order: 10 },
    "cable-tri-kickback": { key: "tri-kickback-cable", name: "Cable Triceps Kickback", setup: "Cable handle", detail: "Low cable · single handle", visual: "triceps-kickback-cable", order: 20 },
    // Side-delt lateral raises. These are deliberately distinct: ordinary cable, lean-away cable and
    // cross-body single-arm cable should never show the same picture again.
    "lat-raise": { key: "lat-db", name: "Dumbbell Lateral Raise", setup: "Dumbbells", detail: "Bilateral dumbbells", visual: "lateral-db", order: 10 },
    "cable-lat-raise": { key: "lat-cable", name: "Cable Lateral Raise", setup: "Standard cable", detail: "Low cable · same-side handle", visual: "lateral-cable", order: 20 },
    "leaning-lat-raise": { key: "lat-lean", name: "Leaning Cable Lateral Raise", setup: "Lean-away cable", detail: "Low cable · lean-away setup", visual: "lateral-lean", order: 30 },
    "single-cable-raise": { key: "lat-cross", name: "Single-Arm Cable Lateral Raise", setup: "Cross-body cable", detail: "Low cable · cross-body single arm", visual: "lateral-cross", order: 40 },
    "machine-lat-raise": { key: "lat-machine", name: "Machine Lateral Raise", setup: "Lateral-raise machine", detail: "Seated lateral-raise machine", visual: "lateral-machine", order: 50 },
    "seated-db-lat-raise": { key: "lat-seated-db", name: "Seated Dumbbell Lateral Raise", setup: "Dumbbells + bench", detail: "Seated upright · no leg drive", visual: "lateral-seated-db", order: 12 },
    "single-db-lat-raise": { key: "lat-single-db", name: "Single-Arm Dumbbell Lateral Raise", setup: "Single dumbbell", detail: "One arm at a time", visual: "lateral-single-db", order: 14 },
    "chest-supported-lat-raise": { key: "lat-supported-db", name: "Chest-Supported Dumbbell Lateral Raise", setup: "Dumbbells + incline bench", detail: "Chest braced on an incline bench", visual: "lateral-supported-db", order: 16 },
    "seated-cable-lat-raise": { key: "lat-seated-cable", name: "Seated Cable Lateral Raise", setup: "Cable + bench", detail: "Seated upright · low cable", visual: "lateral-seated-cable", order: 22 },
    "cuff-cable-lat-raise": { key: "lat-cuff", name: "Cuff Cable Lateral Raise", setup: "Cable cuff", detail: "Cuff around the wrist · low cable", visual: "lateral-cuff", order: 24 },
    "cable-behind-back-lateral": { key: "lat-behind", name: "Behind-the-Back Cable Lateral Raise", setup: "Cable behind the hips", detail: "Low cable · arm starts behind the torso", visual: "lateral-behind", order: 42 },
    "band-lateral-raise": { key: "lat-band", name: "Band Lateral Raise", setup: "Resistance band", detail: "Band anchored under the feet", visual: "lateral-band", order: 60 },
    "side-lying-raise": { key: "lat-side-lying", name: "Side-Lying Lateral Raise", setup: "Dumbbell + bench", detail: "Side-lying · one arm", visual: "lateral-side-lying", order: 70 },
    // Rear-delt flyes
    "rear-fly": { key: "rear-db", name: "Dumbbell Rear Delt Fly", setup: "Dumbbells", detail: "Bent-over dumbbells", visual: "rear-db", order: 10 },
    "cable-rear-fly": { key: "rear-cable", name: "Cable Rear Delt Fly", setup: "Dual cable", detail: "Dual cable · cross-body", visual: "rear-cable", order: 20 },
    "reverse-pec": { key: "rear-machine", name: "Reverse Pec Deck", setup: "Reverse pec deck", detail: "Reverse pec-deck machine", visual: "rear-machine", order: 30 },
    "seated-rear-fly": { key: "rear-seated-db", name: "Seated Dumbbell Rear Delt Fly", setup: "Dumbbells + bench", detail: "Seated · torso hinged over the thighs", visual: "rear-seated-db", order: 12 },
    "single-cable-rear-fly": { key: "rear-single-cable", name: "Single-Arm Cable Rear Delt Fly", setup: "Single cable handle", detail: "One arm · cable across the torso", visual: "rear-single-cable", order: 22 },
    "chest-supported-rear-fly": { key: "rear-supported-db", name: "Chest-Supported Dumbbell Rear Delt Fly", setup: "Dumbbells + incline bench", detail: "Chest braced on an incline bench", visual: "rear-supported-db", order: 14 },
    "prone-rear-delt-raise": { key: "rear-prone-bodyweight", name: "Prone Rear Delt Raise", setup: "Bodyweight", detail: "Prone on the floor · unloaded arms", visual: "rear-prone-bodyweight", order: 40 },
    // Chest flyes
    "db-fly": { key: "chest-db", name: "Dumbbell Fly", setup: "Dumbbells + bench", detail: "Dumbbells · flat bench", visual: "chest-db", order: 10 },
    "cable-fly": { key: "chest-cable", name: "Cable Fly", setup: "Dual cable", detail: "Dual cable · standing fly", visual: "chest-cable", order: 20 },
    "pec-deck": { key: "chest-machine", name: "Pec Deck", setup: "Pec-deck machine", detail: "Seated pec-deck machine", visual: "chest-machine", order: 30 },
    // Glute kickbacks
    "cable-kickback": { key: "glute-cable", name: "Cable Kickback", setup: "Ankle strap", detail: "Low cable · ankle strap", visual: "glute-kickback-cable", order: 10 },
    "glute-kickback-machine": { key: "glute-machine", name: "Glute Kickback Machine", setup: "Kickback machine", detail: "Dedicated kickback machine", visual: "glute-kickback-machine", order: 20 }
};

function variantMeta(ex) { return ex ? (VARIANT_PRESENTATION[ex.id] || null) : null; }

function movementFamilyOptions(ex, equipSet = null, banned = []) {
    if (!ex)
        return [];
    const family = EX_FAMILY[ex.id];
    if (!family)
        return [];
    const blocked = new Set(banned || []);
    const currentKey = variantMeta(ex)?.key || ex.id;
    const byKey = new Map();
    EXERCISES.forEach(a => {
        if (a.id === ex.id || EX_FAMILY[a.id] !== family || blocked.has(a.id))
            return;
        if (equipSet && !a.equip.every(q => equipSet.has(q)))
            return;
        const meta = variantMeta(a);
        const key = meta?.key || a.id;
        if (key === currentKey)
            return; // do not offer a differently-named alias of the current setup
        const prior = byKey.get(key);
        if (!prior || (!!meta?.preferred && !variantMeta(prior)?.preferred))
            byKey.set(key, a);
    });
    return [...byKey.values()].sort((a, b) => (variantMeta(a)?.order ?? 999) - (variantMeta(b)?.order ?? 999) || a.name.localeCompare(b.name));
}

function movementFamilyTitle(ex) {
    const family = ex && EX_FAMILY[ex.id];
    if (family === "tri-pushdown")
        return "Attachment options";
    if (family === "tri-ohext")
        return "Overhead extension options";
    if (family === "tri-kickback")
        return "Triceps kickback options";
    if (family === "side-raise")
        return "Lateral raise options";
    if (family === "rear-delt-fly")
        return "Rear-delt fly options";
    if (family === "chest-fly")
        return "Chest fly options";
    if (family === "glute-kickback")
        return "Glute kickback options";
    return "Setup options";
}

function movementFamilyDescription(ex) {
    const family = ex && EX_FAMILY[ex.id];
    if (family === "tri-ohext")
        return "Same overhead-extension pattern with a rope cable, dumbbell, EZ-bar, or resistance band.";
    if (family === "tri-pushdown")
        return "Same pushdown pattern with a different cable attachment or single-arm setup.";
    if (family === "tri-kickback")
        return "Same triceps kickback pattern with either a dumbbell or a low cable.";
    if (family === "side-raise")
        return "Compare standing, seated, supported, side-lying, cable, cuff, band, and machine lateral raises.";
    if (family === "rear-delt-fly")
        return "Compare standing, seated, chest-supported, single-arm cable, dual-cable, and machine rear-delt fly setups.";
    if (family === "chest-fly")
        return "Compare dumbbell-and-bench, standing cable, and pec-deck chest fly setups.";
    if (family === "glute-kickback")
        return "Compare an ankle-strap cable kickback with the dedicated kickback machine.";
    return "Same movement, different attachment or setup. Choose a variant to view it.";
}

function attachmentLabel(ex) {
    const id = String(ex?.id || "");
    // M157 compatibility: the legacy overhead-cable id predates attachment-aware naming.
    if (id === "oh-cable-ext")
        return "Rope attachment";
    const meta = variantMeta(ex);
    if (meta?.setup)
        return meta.setup;
    const n = String(ex?.name || "").toLowerCase();
    if (id.includes("dual-rope") || n.includes("dual-rope"))
        return "Dual rope";
    if (id.includes("rope") || n.includes("rope"))
        return "Rope attachment";
    if (id.includes("v-bar") || n.includes("v-bar"))
        return "V-bar";
    if (id.includes("straight-bar") || id === "pushdown" || n.includes("straight bar"))
        return "Straight bar";
    if (id === "ez-pushdown" || (id.includes("ez-") && EX_FAMILY[id] === "tri-pushdown"))
        return "EZ-bar";
    if (id.includes("single-pushdown") || n.includes("single-arm pushdown"))
        return "Single handle";
    return null;
}

function variantDisplayName(ex) {
    if (!ex)
        return "Exercise";
    // M157 compatibility and a clearer canonical name for the old cable-extension id.
    if (ex.id === "oh-cable-ext")
        return "Rope Overhead Cable Extension";
    return variantMeta(ex)?.name || ex.name;
}

function friendlyEquipment(ex) {
    if (!ex?.equip?.length)
        return variantMeta(ex)?.detail || "Bodyweight";
    const base = ex.equip.map(q => EQUIPMENT.find(e => e.id === q)?.label || String(q).replace(/([a-z])([A-Z])/g, "$1 $2").replace(/[-_]/g, " ")).join(", ");
    const detail = variantMeta(ex)?.detail || attachmentLabel(ex);
    return detail ? `${base} · ${detail}` : base;
}

function nextScheduledIndex(days, schedule, lastDoneId) {
    if (!schedule || !Array.isArray(days) || !days.length)
        return -1;
    const seq = [];
    for (let wd = 0; wd < 7; wd++) {
        const id = schedule[wd];
        if (id && days.some(d => d.id === id) && !seq.includes(id))
            seq.push(id);
    }
    if (!seq.length)
        return -1;
    // No session logged for this program yet: don't guess from the calendar (whichever day the
    // auto-applied suggested schedule happens to place on the week's earliest weekday, per the
    // Sun→Sat scan above) — that's essentially arbitrary relative to the program's own day order and
    // routinely landed on a day mid-rotation (e.g. "Day 4 of 5") for a program you hadn't touched yet.
    // Return -1 so the caller falls back to the same day-order-0 default used when there's no schedule
    // at all, matching the plain "start at Day 1" expectation for something you haven't started.
    if (!lastDoneId)
        return -1;
    const pos = seq.indexOf(lastDoneId);
    const nextId = pos >= 0 ? seq[(pos + 1) % seq.length] : seq[0];
    return days.findIndex(d => d.id === nextId);
}

function nextDueDayId(program, history) {
    if (!program || !Array.isArray(program.days) || !program.days.length)
        return undefined;
    const entries = (history || []).filter(h => h.programId === program.id);
    // Same resolution as nextSessionCursor — this decides which day the PROGRAM VIEW opens to, so a
    // stale id here lands the lifter on the wrong day even when the cursor itself is right.
    const lastIdx = entries.length ? historyDayIndex(program.days, entries[0]) : -1;
    const lastDoneId = lastIdx >= 0 ? program.days[lastIdx].id : (entries.length ? entries[0].dayId : null);
    const si = nextScheduledIndex(program.days, program.schedule, lastDoneId);
    if (si >= 0)
        return program.days[si].id;
    const idx = lastIdx >= 0 ? (lastIdx + 1) % program.days.length : 0;
    return program.days[idx].id;
}

function pickActiveProgram(saved, history, cycles, pinnedId) {
    if (!Array.isArray(saved) || !saved.length)
        return null;
    if (pinnedId) {
        const p = saved.find(s => s.id === pinnedId);
        if (p)
            return p;
    }
    for (const h of (history || [])) {
        const p = saved.find(s => s.id === h.programId);
        if (p)
            return p;
    }
    const liveCycle = (cycles || []).find(c => !c.done && Array.isArray(c.blockIds) && c.blockIds.length);
    if (liveCycle) {
        const p = saved.find(s => s.id === liveCycle.blockIds[liveCycle.activeBlock || 0]);
        if (p)
            return p;
    }
    return saved.slice().sort((a, b) => b.createdAt - a.createdAt)[0] || null;
}

function endlessStalled(program, entries, dpw) {
    const mains = new Set((program.days || []).map(d => d.exercises?.[d.primaryIndex]).filter(Boolean));
    if (!mains.size)
        return false;
    const rots = [[], [], []];
    entries.forEach((h, i) => { const r = Math.floor(i / dpw); if (r < 3)
        rots[r].push(h); });
    const bestOf = (rot) => {
        let best = 0;
        rot.forEach(h => Object.entries(h.perf || {}).forEach(([id, p]) => {
            if (!mains.has(id) || !p.sets)
                return;
            p.sets.forEach(s => { if (s.w > 0 && s.r > 0)
                best = Math.max(best, e1rm(convertHistoryLoad(s.w, h.unit, program.config?.unit), s.r)); });
        }));
        return best;
    };
    const [r0, r1, r2] = [bestOf(rots[0]), bestOf(rots[1]), bestOf(rots[2])];
    if (!(r0 > 0 && r1 > 0 && r2 > 0))
        return false; // need 3 full rotations of data
    return r0 <= Math.max(r1, r2) + 1e-6; // newest rotation set no new best → stalled
}

function historyForProgram(history, program) {
    const id = program?.id;
    return (Array.isArray(history) ? history : []).filter(h => h && (!h.programId || !id || h.programId === id));
}

function historyDayIndex(days, h) {
    if (!h)
        return -1;
    const byId = h.dayId ? (days || []).findIndex(d => d.id === h.dayId) : -1;
    if (byId >= 0)
        return byId;
    if (!h.dayLabel)
        return -1;
    return (days || []).findIndex(d => d.label === h.dayLabel);
}

function normalizeCycleLinks(saved, cycles) {
    if (!Array.isArray(saved) || !saved.length)
        return Array.isArray(saved) ? saved : [];
    const live = new Set((Array.isArray(cycles) ? cycles : []).map(c => c && c.id).filter(Boolean));
    let touched = false;
    const out = saved.map(p => {
        if (!p || !p.cycleId || live.has(p.cycleId))
            return p;
        touched = true;
        const { cycleId, cycleIndex, blockLabel, ...rest } = p;
        return rest;
    });
    return touched ? out : saved;
}

function normalizeHistoryDayIds(history, saved) {
    if (!Array.isArray(history) || !history.length || !Array.isArray(saved) || !saved.length)
        return history || [];
    const byProgram = new Map(saved.filter(p => p && p.id).map(p => [p.id, p]));
    let changed = false;
    const out = history.map(h => {
        if (!h || !h.dayId || !h.dayLabel)
            return h;
        const prog = byProgram.get(h.programId);
        if (!prog || !Array.isArray(prog.days))
            return h;
        if (prog.days.some(d => d.id === h.dayId))
            return h; // already resolves
        const matches = prog.days.filter(d => d.label === h.dayLabel);
        if (matches.length !== 1)
            return h; // unresolvable or ambiguous
        changed = true;
        return { ...h, dayId: matches[0].id };
    });
    return changed ? out : history;
}

function nextSessionCursor(program, history) {
    const days = program.days || [];
    const dpw = days.length || 1;
    const entries = (history || []).filter(h => h.programId === program.id);
    const done = entries.length;
    const lastDoneId = entries.length ? entries[0].dayId : null;
    // Resolved, not matched raw: a pre-v578 entry carries a random dayId that no longer exists, and
    // reading -1 here is what silently restarted the rotation and re-served the day just trained.
    const lastIdx = entries.length ? historyDayIndex(days, entries[0]) : -1;
    let dayIndex = lastIdx >= 0 ? (lastIdx + 1) % dpw : 0;
    let scheduled = false;
    /* The SCHEDULE lookup needs the resolved id too. Fixing only `lastIdx` above left this line reading
       the raw stored id, so a reattached legacy entry advanced the plain rotation correctly and was then
       overridden by a schedule lookup that still could not place it — landing on the wrong day anyway.
       Caught by the reattachment check; a fix that stops one reader consulting a stale id has to follow
       that id everywhere it flows. */
    const resolvedLastId = lastIdx >= 0 ? days[lastIdx].id : lastDoneId;
    const si = nextScheduledIndex(days, program.schedule, resolvedLastId);
    if (si >= 0) {
        dayIndex = si;
        scheduled = true;
    }
    if (program.config?.endless) {
        // CONTINUOUS MODE — no fixed block, never "completes". Runs a rolling accumulation → deload wave
        // forever: load and volume autoregulate every session, and a light deload rotation is inserted
        // automatically when accumulation has run its course (a soft cap) OR a main lift stalls, whichever
        // comes first. Deloads are detected straight from history (a session logged at the deload sentinel
        // week, weekIndex > cap), so the cadence self-corrects even when a stall pulls one in early.
        const cap = weeksOf(program); // accumulation weeks before an auto-deload
        const isDeloadEntry = (h) => h.weekIndex != null && h.weekIndex > cap;
        let headDeloads = 0;
        for (const h of entries) {
            if (isDeloadEntry(h))
                headDeloads++;
            else
                break;
        }
        // Mid deload rotation → keep the rest of the rotation light until every day has been deloaded.
        if (headDeloads > 0 && headDeloads < dpw) {
            return { dayIndex, weekIndex: cap + 1, done, scheduled, lastIdx, endless: true, accumWk: 0, deload: true };
        }
        // Otherwise count the accumulation sessions since the last deload and place us in the wave.
        let sinceDeload = 0;
        for (const h of entries) {
            if (isDeloadEntry(h))
                break;
            sinceDeload++;
        }
        const accumWk = Math.floor(sinceDeload / dpw) + 1;
        const stalled = accumWk >= 3 && endlessStalled(program, entries, dpw);
        const deloadDue = accumWk > cap || stalled;
        const weekIndex = deloadDue ? cap + 1 : Math.min(accumWk, cap);
        return { dayIndex, weekIndex, done, scheduled, lastIdx, endless: true, accumWk: deloadDue ? 0 : accumWk, deload: deloadDue, stalled };
    }
    const maxWeek = Math.max(1, weeksOf(program) + (program.config?.deload ? 1 : 0));
    /* A FINITE PROGRAM ADVANCES WHEN ITS DISTINCT SCHEDULED DAYS ARE DONE — not merely after N history
       rows exist. `done / daysPerWeek` was subtly wrong because repeating a day, restoring a duplicated
       history entry, or logging the same planned day twice counted as two steps through the block. That
       can jump Program View into week 2/3/a deload even though a day in the current week was never
       completed; since later weeks can intentionally carry fewer sets, it looks exactly like the program
       "dropped a lot of sets". New history rows already carry both weekIndex and dayId, so during the
       first pass through a block we can use those facts instead of an inferred row count. Legacy/mixed
       history (missing either field), and post-block looping behavior, keep the old count fallback rather
       than guessing at data that cannot be reconstructed safely. */
    const explicit = entries.length > 0 && entries.every(h => Number.isInteger(Number(h.weekIndex))
        && Number(h.weekIndex) >= 1 && Number(h.weekIndex) <= maxWeek && historyDayIndex(days, h) >= 0);
    if (explicit) {
        let weekIndex = 1;
        let completed = new Set();
        for (let w = 1; w <= maxWeek; w++) {
            completed = new Set(entries.filter(h => Number(h.weekIndex) === w).map(h => historyDayIndex(days, h)).filter(i => i >= 0));
            if (completed.size < dpw) {
                weekIndex = w;
                break;
            }
            if (w === maxWeek) {
                // Full first pass complete: preserve the historical wrap-to-week-1 behavior.
                weekIndex = 1;
                completed = new Set();
            }
        }
        if (completed.size < dpw) {
            // Keep rotation/schedule order, but never serve a day already completed in this explicit week.
            let probe = dayIndex;
            for (let n = 0; n < dpw; n++) {
                if (!completed.has(probe)) {
                    dayIndex = probe;
                    break;
                }
                probe = (probe + 1) % dpw;
            }
        }
        return { dayIndex, weekIndex, done, scheduled, lastIdx, explicitWeekProgress: true };
    }
    const weekIndex = (Math.floor(done / dpw) % maxWeek) + 1;
    return { dayIndex, weekIndex, done, scheduled, lastIdx };
}

const CODE_ALPHA = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

const CODE_SCHEMES = ["531", "531beg", "madcow", "nsuns", "texas", "gzclp", "gvt", "rippler", "jt"];

const CODE_ASSIST = ["bbb", "triumvirate", "jack_shit"];

const CODE_CARRIED_KEYS = new Set(["split", "days", "goal", "experience", "weeks", "progression",
    "session", "percentScheme", "noBodyweight", "equipment", "focus", "focusList", "deload", "assistance"]);

const CODE_IGNORED_KEYS = new Set(["name"]);

const CODE_SPLITS = Object.keys(SPLITS);

const CODE_GOALS = ["hypertrophy", "strength", "both"];

const CODE_EXP = ["none", "beginner", "intermediate", "advanced"];

const CODE_PROG = ["auto", "manual"];

const CODE_SESSION = ["s20", "s40", "s60", "s90", "s120"];

const CODE_EQUIP = EQUIPMENT.map(e => e.id);

const LEGACY_EQUIP_IMPLIES = {
    machine: ["legpress", "hacksquat", "legext", "legcurl", "pecdeck", "machinerow", "assisted",
        "calfmachine", "abduction", "reversehyper", "ghd",
        "machinepress", "machineshoulder", "machinelatraise", "machinecurl", "machineext", "beltsquat", "hipthrustmachine", "kickback", "machinecrunch", "machineshrug", "machinepullover", "adduction"],
    barbell: ["trapbar", "safetybar", "landmine"],
    /* A code minted before this split said only "bench", and every incline, decline and preacher
       movement was available to it. Granting the three keeps those layouts identical. This is about
       reproducing history, not about what owning a bench implies in real life — the gym editor lets
       anyone untick what they do not have. */
    bench: ["inclinebench", "declinebench", "preacher"]
};

function expandLegacyEquipment(list) {
    const out = new Set(list);
    list.forEach(id => (LEGACY_EQUIP_IMPLIES[id] || []).forEach(fine => out.add(fine)));
    return CODE_EQUIP.filter(id => out.has(id)); // keep canonical order
}

const CODE_EXERCISES = EXERCISES.map(e => e.id);

function programFingerprint(p) {
    const str = (p && p.days ? p.days : []).map(d => (d.exercises || []).join(",")).join("|");
    let h = 2166136261 >>> 0;
    for (let i = 0; i < str.length; i++) {
        h ^= str.charCodeAt(i);
        h = Math.imul(h, 16777619) >>> 0;
    }
    return h & 0xffff;
}

function bitsToCode(bits) {
    while (bits.length % 5)
        bits.push(0);
    let out = "";
    for (let i = 0; i < bits.length; i += 5) {
        let v = 0;
        for (let k = 0; k < 5; k++)
            v = (v << 1) | bits[i + k];
        out += CODE_ALPHA[v];
    }
    // Group in fours, but never leave a runt: 21 characters would otherwise end "…-HD6P-0", and a
    // one-character group reads like the code got cut off in the paste.
    const g = out.match(/.{1,4}/g) || [];
    if (g.length > 1 && g[g.length - 1].length < 2) {
        g[g.length - 2] += g.pop();
    }
    return "PI-" + g.join("-");
}

function codeToBits(str) {
    const clean = String(str || "").toUpperCase().replace(/^PI-?/, "").replace(/[^0-9A-Z]/g, "")
        .replace(/I/g, "1").replace(/L/g, "1").replace(/O/g, "0").replace(/U/g, "V"); // forgive the classic misreads
    const bits = [];
    for (const ch of clean) {
        const v = CODE_ALPHA.indexOf(ch);
        if (v < 0)
            return null;
        for (let k = 4; k >= 0; k--)
            bits.push((v >> k) & 1);
    }
    return bits;
}

const putBits = (bits, val, n) => { for (let k = n - 1; k >= 0; k--)
    bits.push((val >> k) & 1); };

const getBits = (bits, at, n) => { let v = 0; for (let k = 0; k < n; k++)
    v = (v << 1) | (bits[at + k] || 0); return v >>> 0; };

function encodeProgramCode(program) {
    // M40: a Pursuit Next program is not reproducible by the legacy seed-code decoder. Text sharing
    // remains lossless; refuse a compact code rather than minting one that rebuilds under v661.
    if (program?.engineSource === "pursuit-next")
        return null;
    const c = program && program.config;
    if (!c)
        return null;
    const si = CODE_SPLITS.indexOf(c.split), gi = CODE_GOALS.indexOf(c.goal);
    const ei = CODE_EXP.indexOf(c.experience || "beginner");
    const pi = CODE_PROG.indexOf(c.progression || "auto");
    const ssi = CODE_SESSION.indexOf(c.session || "s20");
    const days = c.days | 0, weeks = c.weeks | 0, seed = program.seed >>> 0;
    if (si < 0 || gi < 0 || ei < 0 || pi < 0 || ssi < 0)
        return null; // an unrepresentable field (incl. an unknown session length) refuses rather than minting a code that rebuilds a DIFFERENT program
    if (days < 1 || days > 7 || weeks < 1 || weeks > 31)
        return null;
    // focusList IS carried now, by the v4 format below. Refuse only what the field can't hold: an
    // unknown part name, or more entries than the 4-bit count. Everything else encodes.
    const focusIdx = (c.focusList || []).map(part => PART_ORDER.indexOf(part));
    if (focusIdx.some(i => i < 0) || focusIdx.length > 15)
        return null;
    // 0 = no named scheme (the legacy 1-bit boolean still says whether percentages are on at all).
    const schemeIdx = typeof c.percentScheme === "string" ? CODE_SCHEMES.indexOf(c.percentScheme) + 1 : 0;
    if (typeof c.percentScheme === "string" && schemeIdx === 0)
        return null; // unknown scheme: refuse rather than mint a code that rebuilds a different program
    const assistIdx = typeof c.assistance === "string" ? CODE_ASSIST.indexOf(c.assistance) + 1 : 0;
    if (typeof c.assistance === "string" && assistIdx === 0)
        return null;
    // Refuse on any field the schema cannot carry, rather than dropping it silently on import.
    for (const [k, v] of Object.entries(c)) {
        if (CODE_CARRIED_KEYS.has(k) || CODE_IGNORED_KEYS.has(k))
            continue;
        const meaningful = !(v == null || v === false || (Array.isArray(v) && !v.length) ||
            (typeof v === "object" && !Array.isArray(v) && !Object.keys(v).length));
        if (meaningful)
            return null;
    }
    if (c.exerciseBias && Object.keys(c.exerciseBias).length)
        return null;
    let mask = 0;
    /* An id the vocabulary doesn't know is refused here rather than skipped. Silently dropping it
       encoded a DIFFERENT equipment list, which rebuilt a different program, which the self-check at
       the end caught as a fingerprint mismatch — safe, but the refusal arrived with no way to tell
       what was wrong. This is the same rule the unknown-KEY loop above already applies; values inside
       `equipment` were the one place it wasn't enforced. Reachable via a stale id in a store written
       by an older version, or a hand-edited config. */
    for (const id of (c.equipment || [])) {
        const i = CODE_EQUIP.indexOf(id);
        if (i < 0)
            return null;
        mask |= (1 << i);
    }
    // Detect coach overrides: diff the actual layout against a fresh regen from the same seed. Anything
    // that differs is a deliberate edit the code has to carry, or it must refuse to encode (text fallback).
    let overrides = [];
    try {
        const fresh = generateProgram(c, [], null, seed, program.engineV || ENGINE_V);
        const sameStructure = fresh.days.length === program.days.length &&
            fresh.days.every((d, i) => (program.days[i]?.exercises?.length || 0) === d.exercises.length);
        if (!sameStructure)
            return null; // added/removed slots — can't express as swaps
        for (let di = 0; di < program.days.length; di++) {
            const cur = program.days[di].exercises, base = fresh.days[di].exercises;
            for (let slot = 0; slot < cur.length; slot++) {
                if (cur[slot] === base[slot])
                    continue;
                const exIdx = CODE_EXERCISES.indexOf(cur[slot]);
                if (exIdx < 0 || di > 7 || slot > 15 || exIdx > 511)
                    return null; // outside what a swap record can hold
                overrides.push({ dayIdx: di, slot, exIdx });
            }
        }
        if (overrides.length > 31)
            return null; // more edits than the record count can hold
    }
    catch {
        return null;
    }
    const bits = [];
    /* v5 widens the equipment field. v1-v4 wrote a fixed 16-bit mask, hard-capping the library at 16
       equipment ids — fine for 11, fatal the moment a real taxonomy (leg press vs hack squat vs
       pendulum squat) needs dozens. v5 writes a self-describing field instead:
          1 bit  "has everything"  → nothing follows, so a full commercial gym stays SHORT
          else   7 bits count N, then N bits of mask
       Storing N is what makes it future-proof: a decoder that knows MORE equipment than the encoder did
       treats the surplus as absent, which is exactly right — that gym did not have it. */
    const emitEquip = (ver) => {
        if (ver < 5) {
            putBits(bits, mask & 0xffff, 16);
            return;
        }
        const have = c.equipment || [];
        const hasAll = CODE_EQUIP.every(id => have.includes(id));
        putBits(bits, hasAll ? 1 : 0, 1);
        if (hasAll)
            return;
        putBits(bits, CODE_EQUIP.length, 7);
        CODE_EQUIP.forEach(id => putBits(bits, have.includes(id) ? 1 : 0, 1));
    };
    const engineOf = (p) => (p.engineV || ENGINE_V);
    const emitBase = (baseVer, sessionBits) => {
        /* Widen only when needed: engines 0-7 keep the historical versions untouched. */
        /* ⚠ v635: A THIRD TIER, FOR THE SAME REASON THE SECOND ONE EXISTS. The four-bit engine field
           holds 0-15; ENGINE_V 16 masks to 0 and every shared program rebuilds under engine-1 rules —
           silently, with a valid checksum, exactly as the three-bit field did at engine 8. The warning
           below said to widen BEFORE the next bump; gates/progcode.mjs check 2 is what enforced it,
           going red on 27 configs the moment ENGINE_V went to 16.
           Versions 11-15 are versions 1-5 with a SIX-bit engine field (0-63). Old codes keep their own
           width forever: 1-5 read three bits, 6-10 read four, 11-15 read six. The version field itself
           is four bits, so this is the LAST widening available — the next one needs a wider `ver`. */
        const engTier = engineOf(program) > 15 ? 2 : engineOf(program) > 7 ? 1 : 0;
        const ver = baseVer + (engTier === 2 ? 10 : engTier === 1 ? 5 : 0);
        putBits(bits, ver, 4);
        /* ⚠ THREE BITS — the ORIGINAL field, kept for codes already in the wild. History follows. */
        /* ⚠ THREE BITS. THIS IS THE HARD CEILING ON ENGINE_V AND IT IS NOT DOCUMENTED ANYWHERE ELSE.
           A share code carries a seed, a config and an ENGINE — the recipient regenerates rather than
           receiving a layout, so the engine number is what makes their program match the sender's.
           At three bits the field holds 0-7. Engine 8 masks to 0, decodes as 0, and every shared program
           silently rebuilds under engine-1 rules: a different program, with no error anywhere.
           gates/progcode.mjs check 2 caught this the moment ENGINE_V went to 8, which is the only reason
           it was not shipped. RAISING ENGINE_V PAST 7 REQUIRES A CODE-FORMAT VERSION BUMP FIRST — widen
           the field and keep decoding old codes at the old width, or every code already in the wild
           becomes wrong. */
        /* ⚠ THREE BITS. THE ENGINE VERSION CANNOT EXCEED 7, AND NOTHING SAYS SO ANYWHERE ELSE.
           `& 7` does not clamp, it WRAPS: engine 8 encodes as 0, engine 9 as 1. A share code minted by
           either decodes as a different engine and rebuilds a different program — silently, with a valid
           checksum. Found when ENGINE_V was bumped to 9 for the reduce change and gates/progcode.mjs went
           red on 27 configs at once; the engine-8 hold had accidentally been protecting this too.
           THIS IS A HARD BLOCKER ON THE WHOLE ENGINE PLAN — engines 8, 9 and 10 are all specified and all
           unshippable until the field is widened, which needs a code-format version bump (`ver`) and a
           decoder that reads 3 bits for old codes and 4 for new. Do that BEFORE the next ENGINE_V bump,
           not alongside one. */
        /* ⚠ FIXED — see the note above. The engine field is 3 bits for versions 1-5 and 4 bits for the
           WIDE versions 6-10, which carry the payload of `ver - 5`. The version field itself has always
           been 4 bits (0-15) with only 1-5 used, so the wide range costs nothing and no existing code
           changes meaning: a v5 code stays a v5 code and still decodes at 3 bits, forever.
           A wide version is emitted ONLY when the engine will not fit in three bits, so nothing in the
           wild is re-encoded and the format does not churn for the 99% case. */
        const engW = engTier === 2 ? 6 : engTier === 1 ? 4 : 3;
        putBits(bits, engineOf(program) & ((1 << engW) - 1), engW);
        putBits(bits, si, 5);
        putBits(bits, days, 3);
        putBits(bits, gi, 2);
        putBits(bits, ei, 2);
        putBits(bits, weeks, 5);
        putBits(bits, pi, 2);
        putBits(bits, ssi, sessionBits);
        putBits(bits, c.percentScheme ? 1 : 0, 1);
        putBits(bits, c.noBodyweight ? 1 : 0, 1);
        emitEquip(ver);
        putBits(bits, seed, 32);
        putBits(bits, programFingerprint(program), 16); // hash of the FINAL layout — swaps included
    };
    // v1 (2-bit session) stays the compact, backward-compatible code — but only for the s20/s40 sessions it
    // can represent and only when unedited. Anything else (a longer session, or coach overrides) emits v3,
    // whose 3-bit session covers every length. v2 is still DECODED for codes already in the wild.
    const needsWideSession = ssi > 1;
    const emitOverrides = () => {
        putBits(bits, overrides.length, 5);
        overrides.forEach(o => { putBits(bits, 0, 3); putBits(bits, o.dayIdx, 3); putBits(bits, o.slot, 4); putBits(bits, o.exIdx, 9); }); // record type 0 = SWAP
    };
    // deload is NOT in v1/v3, so those decode to the app default of true (see the decoder). A program
    // that deliberately has no deload week can only be expressed by v4.
    /* Force v5 for anything the legacy field cannot represent EXACTLY. Two cases:
         - an equipment id past bit 16, which the old mask simply cannot hold; and
         - any coarse id that now implies finer ones. A v1-v4 code is expanded on decode (a code minted
           before the split said "machine" and must still get the machines), and there is no way to tell
           an old such code from a new one. So a NEW code carrying a coarse id must not use the legacy
           field at all, or its own kit would be silently widened on the way back in. */
    const have = c.equipment || [];
    const needsV5 = CODE_EQUIP.some((id, i) => i >= 16 && have.includes(id))
        || Object.keys(LEGACY_EQUIP_IMPLIES).some(parent => have.includes(parent));
    const needsV4 = focusIdx.length || schemeIdx || assistIdx || c.deload !== true;
    /* v4 and v5 carry an identical tail, so it is emitted from one place. It was written out twice,
       which is a silent decoder break waiting to happen: add a field to one branch, forget the other,
       and every code minted at the un-updated version decodes with its fields shifted by that many
       bits — a corrupt program rather than a rejected one. */
    const emitFocusTail = () => {
        putBits(bits, focusIdx.length, 4);
        focusIdx.forEach(i => putBits(bits, i, 5)); // PART_ORDER index, 17 parts in a 5-bit field
        putBits(bits, schemeIdx, 4); // 0 = none, else CODE_SCHEMES index + 1
        putBits(bits, assistIdx, 3); // 0 = none, else CODE_ASSIST index + 1
        putBits(bits, c.deload === true ? 1 : 0, 1);
    };
    if (needsV5) {
        emitBase(5, 3);
        emitOverrides();
        emitFocusTail();
    }
    else if (needsV4) {
        // v4 — everything v3 carries, plus the muscle-focus list. Emitted ONLY when there is a focus, so
        // no existing program's code changes shape: a v1 code minted before v4 existed still mints as v1.
        // Older installs reject ver 4 outright ("made by a newer version") rather than misparsing it.
        emitBase(4, 3);
        emitOverrides();
        emitFocusTail();
    }
    else if (overrides.length === 0 && !needsWideSession) {
        emitBase(1, 2); // compact v1
    }
    else {
        emitBase(3, 3); // v3: full session + optional overrides
        emitOverrides();
    }
    let sum = 0;
    for (let i = 0; i < bits.length; i += 8)
        sum = (sum + getBits(bits, i, 8)) & 0xff;
    putBits(bits, sum, 8);
    const out = bitsToCode(bits);
    /* BACKSTOP — never hand out a code that cannot rebuild what it claims to.
       The percentScheme bug shipped because minting and importing were never checked against each
       other: six templates happily produced codes that failed the fingerprint test on every import,
       so the recipient got an error and the sharer never knew. Decoding our own output and
       regenerating from it is the only check that actually proves the round trip. Any config field
       the schema silently drops now surfaces HERE, as a refusal to encode, instead of as a broken
       code in someone else's hands. */
    try {
        const back = decodeProgramCode(out);
        if (!back.ok)
            return null;
        const rebuilt = generateProgram(back.config, [], null, back.seed, back.engineV || ENGINE_V);
        (back.overrides || []).forEach(o => {
            if (o.type === "swap" && rebuilt.days[o.dayIdx] && o.slot < rebuilt.days[o.dayIdx].exercises.length && EX_BY_ID[o.exId]) {
                rebuilt.days[o.dayIdx].exercises[o.slot] = o.exId;
            }
        });
        if (programFingerprint(rebuilt) !== programFingerprint(program))
            return null;
    }
    catch {
        return null;
    }
    return out;
}

function decodeProgramCode(str) {
    const bits = codeToBits(str);
    if (!bits || bits.length < 102)
        return { ok: false, reason: "That doesn't look like a program code." };
    const rawVer = getBits(bits, 0, 4);
    if (rawVer < 1 || rawVer > 15)
        return { ok: false, reason: "That code was made by a newer version of Pursuit Iron." };
    /* Versions 6-10 are versions 1-5 with a FOUR-bit engine field, and 11-15 the same with a SIX-bit
       one (v635, ENGINE_V 16). Everything after the engine is byte-for-byte identical in all three, so
       the rest of this decoder reads `ver` and never needs to know which tier it came from. */
    const engBits = rawVer >= 11 ? 6 : rawVer >= 6 ? 4 : 3;
    const ver = rawVer >= 11 ? rawVer - 10 : rawVer >= 6 ? rawVer - 5 : rawVer;
    let at = 4;
    const take = (n) => { const v = getBits(bits, at, n); at += n; return v; };
    const engineV = take(engBits);
    const split = CODE_SPLITS[take(5)], days = take(3), goal = CODE_GOALS[take(2)], experience = CODE_EXP[take(2)];
    const weeks = take(5), progression = CODE_PROG[take(2)], session = CODE_SESSION[take(ver >= 3 ? 3 : 2)];
    const percentScheme = !!take(1), noBodyweight = !!take(1);
    let equipment;
    if (ver >= 5) {
        if (take(1))
            equipment = [...CODE_EQUIP]; // "has everything"
        else {
            const n = take(7), picked = [];
            for (let i = 0; i < n; i++) {
                const on = take(1);
                if (on && i < CODE_EQUIP.length)
                    picked.push(CODE_EQUIP[i]);
            }
            equipment = picked;
        }
    }
    else {
        const mask = take(16);
        equipment = expandLegacyEquipment(CODE_EQUIP.filter((_, i) => i < 16 && (mask & (1 << i))));
    }
    const seed = take(32) >>> 0, fp = take(16);
    const overrides = [];
    if (ver >= 2) {
        const oc = take(5);
        for (let i = 0; i < oc; i++) {
            const type = take(3);
            if (type === 0) { // SWAP record
                const dayIdx = take(3), slot = take(4), exIdx = take(9);
                const exId = CODE_EXERCISES[exIdx];
                if (!exId)
                    return { ok: false, reason: "That code references an exercise this version of Pursuit Iron doesn't have. Update, then try again." };
                overrides.push({ type: "swap", dayIdx, slot, exId });
            }
            else {
                return { ok: false, reason: "That code uses a newer coaching feature. Update Pursuit Iron to import it." };
            }
        }
    }
    // v4 tail: the muscle-focus list. Read after the overrides block so v2/v3 parsing is byte-identical.
    const focusList = [];
    let schemeName = null, assistName = null;
    /* v1-v3 never encoded `deload`, so it arrived as undefined and generateProgram read that as FALSE —
       silently deleting the deload week from every imported program. The field is unrecoverable from
       those codes, so restore the app's own default (true) rather than keep dropping a week. Safe to
       change retroactively: programFingerprint hashes the layout only and is identical either way, so
       codes already in the wild still pass their own integrity check. */
    let deload = true;
    if (ver >= 4) {
        const fc = take(4);
        for (let i = 0; i < fc; i++) {
            const part = PART_ORDER[take(5)];
            if (!part)
                return { ok: false, reason: "That code references a muscle group this version of Pursuit Iron doesn't have. Update, then try again." };
            focusList.push(part);
        }
        const si2 = take(4);
        if (si2) {
            const named = CODE_SCHEMES[si2 - 1];
            if (!named)
                return { ok: false, reason: "That code uses a training scheme this version of Pursuit Iron doesn't have. Update, then try again." };
            schemeName = named;
        }
        const ai = take(3);
        if (ai) {
            const named = CODE_ASSIST[ai - 1];
            if (!named)
                return { ok: false, reason: "That code uses supplemental work this version of Pursuit Iron doesn't have. Update, then try again." };
            assistName = named;
        }
        deload = !!take(1);
    }
    const bodyLen = at; // everything before the checksum
    const sum = getBits(bits, bodyLen, 8);
    const body = bits.slice(0, bodyLen); // slice so the final 8-bit chunk zero-pads (matches encode)
    let calc = 0;
    for (let i = 0; i < bodyLen; i += 8)
        calc = (calc + getBits(body, i, 8)) & 0xff;
    if (calc !== sum)
        return { ok: false, reason: "That code looks mistyped — check it and try again." };
    if (!split || !goal || !experience || !progression || !days)
        return { ok: false, reason: "That code is damaged." };
    const config = { split, days, goal, experience, weeks, progression, session, percentScheme, noBodyweight, equipment, deload };
    // Only attach focus for v4. A v1-v3 config must stay EXACTLY the shape it was before v4 existed, or
    // a code minted years ago could regenerate a different layout and fail its own fingerprint check.
    if (ver >= 4) {
        const focus = {};
        focusList.forEach(part => { focus[part] = (focus[part] || 0) + 1; });
        config.focus = focus;
        config.focusList = focusList;
        if (schemeName)
            config.percentScheme = schemeName; // restore the NAMED scheme, not just "on"
        if (assistName)
            config.assistance = assistName;
    }
    return { ok: true, ver, engineV, seed, fp, overrides, config };
}

function decodeGallery(data) {
    const raw = Array.isArray(data) ? data : (data && Array.isArray(data.programs) ? data.programs : []);
    return raw.map(e => {
        if (!e || typeof e.code !== "string")
            return null;
        const d = decodeProgramCode(e.code);
        if (!d.ok)
            return null;
        return {
            code: e.code,
            title: String(e.title || "Shared program").slice(0, 80),
            author: String(e.author || "").slice(0, 40),
            tags: Array.isArray(e.tags) ? e.tags.slice(0, 6).map(t => String(t).slice(0, 24)) : [],
            note: String(e.note || "").slice(0, 240),
            split: d.config.split, days: d.config.days, goal: d.config.goal,
            equipmentCount: (d.config.equipment || []).length,
            coached: (d.overrides || []).length
        };
    }).filter(Boolean);
}

function gallerySubmission(program) {
    const code = encodeProgramCode(program);
    if (!code)
        return { ok: false };
    const dec = decodeProgramCode(code);
    const coached = dec.ok ? (dec.overrides || []).length : 0;
    const preview = (program.days || []).map(d => ({
        day: d.label || d.baseLabel || "Day",
        lifts: (d.exercises || []).map(id => EX_BY_ID[id]?.name).filter(Boolean)
    }));
    const entry = { code, title: String(program.name || "Shared program"), author: "", tags: [], note: "" };
    return { ok: true, code, entry, preview, coached, config: dec.ok ? dec.config : null };
}

function galleryIssueBody(sub) {
    if (!sub || !sub.ok)
        return "";
    const L = ["### Add to `gallery.json`", "", "```json", JSON.stringify(sub.entry, null, 2), "```", "",
        "**Decoded preview** — this is exactly what the code rebuilds, review before merging:", ""];
    sub.preview.forEach(p => L.push(`- **${p.day}:** ${p.lifts.join(", ") || "—"}`));
    L.push("");
    if (sub.config)
        L.push(`Split: \`${sub.config.split}\` · ${sub.config.days} days · ${sub.config.goal}${sub.coached ? ` · ${sub.coached} coach edit${sub.coached === 1 ? "" : "s"}` : ""}`);
    L.push("", "_Submitted from Pursuit Iron. The code carries a fingerprint that must match on import, so the preview above can't drift from what imports._");
    return L.join("\n");
}

function preferenceFloor(program, part) {
    /* ⚠ JUDGE A PROGRAM BY THE LANDMARKS IT WAS BUILT WITH. This took `program` already and then asked
       `landmarkOf(part)` without it, so a floor was always resolved from the legacy table — which meant
       a program generated under uniform landmarks got scored against the old per-muscle numbers. That
       is how gates/rotationquality reported "lost floors" for muscles sitting comfortably above their
       own floor: apples measured with an orange yardstick. `program.landmarks` is stamped at
       generation exactly so this question has one answer. */
    /* ⚠ ONLY DIVERGE WHEN THE PROGRAM SAYS SO. Reading `landmarkFor(part, experience, program)`
       unconditionally changed the floor for EVERY engine, because the original `landmarkOf` defaulted
       experience differently — frozen engines 27-30 all moved. A program stamped with uniform landmarks
       answers for itself; every other program keeps the exact call it always made. */
    const L = program?.landmarks?.uniform
        ? landmarkFor(part, program.config?.experience, program)
        : landmarkOf(part);
    const base = Number(L?.mev) || 0;
    if (!engHas(program.engineV, "preferenceAware"))
        return base;
    if (!PART_ORDER.includes(part) && !SUB_LANDMARKS[part])
        return 0; // unlandmarked regions have no invented floor
    const parent = ["front_delts", "side_delts", "rear_delts"].includes(part) ? "shoulders" : part;
    return (program.config?.reduce || []).includes(parent) ? Math.ceil(base * REDUCE_FRACTION) : base;
}

function exerciseAt(program, day, slot, week) {
    const alt = program.pairs?.[`${day.id}:${slot}`];
    if (!alt)
        return day.exercises[slot];
    return (Math.max(1, week) % 2 === 0) ? alt : day.exercises[slot];
}

function perWeekOf(program) {
    const n = Number(program.config?.days) || program.days.length;
    return Math.max(1, Math.min(n, program.days.length));
}

function rotationOf(program) {
    const declared = Number(program.rotation || SPLITS[program.config?.split]?.rotation || 1);
    if (Number.isFinite(declared) && declared > 1)
        return Math.round(declared);
    const alternates = Object.keys(program.pairs || {}).length > 0 || Object.keys(program.weekOff || {}).length > 0;
    return alternates ? 2 : 1;
}

function poolOf(program) {
    return (program.pool && program.pool.length) ? program.pool : program.days;
}

function daysInWeek(program, week) {
    if (rotationOf(program) <= 1)
        return program.days;
    const pool = poolOf(program), per = perWeekOf(program), n = pool.length;
    const start = ((Math.max(1, week) - 1) * per) % n;
    return Array.from({ length: per }, (_, i) => pool[(start + i) % n]);
}

function capWeeklyVolume(program) {
    if (program.config.percentScheme || program.config.progression === "manual")
        return;
    const peak = weeksOf(program); // last hard week carries the most volume
    const bias = { ...(program.slotBias || {}) };
    for (let pass = 0; pass < 40; pass++) {
        // current projected volume per muscle at peak week
        const vol = {};
        program.days.forEach(day => day.exercises.forEach((id, slot) => {
            const ex = EX_BY_ID[id];
            if (!ex)
                return;
            const sets = Number(computeCell({ ...program, slotBias: bias }, day, id, slot, peak).sets) || 0;
            vol[ex.part] = (vol[ex.part] || 0) + sets;
            secondaryOf(ex, program.engineV).forEach(([pp, f]) => { vol[pp] = (vol[pp] || 0) + sets * f; });
        }));
        // worst over-MRV muscle (composite parts like shoulders use the summed per-head ceiling,
        // so the front delt's large indirect pressing volume doesn't trigger phantom trimming)
        let worst = null, worstOver = 0;
        for (const part in vol) {
            const over = vol[part] - compositeMrv(part);
            if (over > worstOver) {
                worstOver = over;
                worst = part;
            }
        }
        if (!worst || worstOver < MRV_GRAIN)
            break; // MRV_GRAIN, not a literal — volumeZone reads the same constant
        // trim one set from the lowest-priority slot training `worst` directly (isolation/accessory,
        // highest slot index), keeping a floor of 2 working sets.
        let target = null, targetRank = -Infinity;
        program.days.forEach(day => day.exercises.forEach((id, slot) => {
            const ex = EX_BY_ID[id];
            if (!ex || ex.part !== worst)
                return;
            const key = `${day.id}:${slot}`;
            const cur = Number(computeCell({ ...program, slotBias: bias }, day, id, slot, peak).sets) || 0;
            if (cur <= 2)
                return; // never below 2 working sets
            const rank = (ex.type === "isolation" ? 100 : 0) + slot; // prefer trimming accessories & later slots
            if (rank > targetRank) {
                targetRank = rank;
                target = key;
            }
        }));
        if (!target)
            break;
        bias[target] = (bias[target] || 0) - 1;
    }
    if (Object.keys(bias).length)
        program.slotBias = bias;
}

const NAV_TABS = ["home", "progress", "profile", "settings"];

function navPush(stack, origin, next) {
    if (NAV_TABS.includes(next))
        return [];
    const i = stack.indexOf(origin);
    const base = i > -1 ? stack.slice(0, i) : stack; // returning to a screen unwinds to it, never stacks on it
    return [...base, origin];
}

function navPop(stack) {
    const st = [...stack];
    const to = st.pop();
    return { stack: st, view: to || "home" }; // an empty stack always lands home, never nowhere
}

function propagateCycleEditsPure(edited, before, siblings, adapt) {
    if (adapt) {
        const subs = new Map();
        (edited.days || []).forEach((d, di) => {
            const src = before?.days?.[di];
            if (!src)
                return;
            (d.exercises || []).forEach((id, i) => {
                const old = src.exercises?.[i];
                if (old && old !== id && !d.exercises.includes(old))
                    subs.set(old, id);
            });
        });
        if (!subs.size)
            return siblings;
        return siblings.map(p => {
            let touched = false;
            const days = p.days.map(d => {
                let dayChanged = false;
                const exercises = (d.exercises || []).map(id => {
                    const to = subs.get(id);
                    if (!to || d.exercises.includes(to))
                        return id;
                    dayChanged = true;
                    touched = true;
                    return to;
                });
                return dayChanged ? { ...d, exercises } : d;
            });
            return touched ? remapNextShellRoster(p, days, EXERCISES) : p;
        });
    }
    /* NON-ADAPT (default): every phase trains the same lifts, so a genuine exercise change is meant
     * to carry across the cycle. "Genuine" is the word this used to skip.
     *
     * This rebuilt EVERY day of EVERY sibling from the just-saved block's CURRENT exercise list,
     * unconditionally — it never asked whether that list had actually changed from what it replaced.
     * Two ways that broke, both real, both silent:
     *
     *   1. AN ENGINE UPDATE. Bumping engineV regenerates a block from its OWN seed under the new
     *      engine, and engine 5 only adjusts set counts via slotBias — days.exercises comes back
     *      byte-identical. Nothing was edited. But this ran on every Save regardless, so tapping
     *      "Update" after a pure version bump overwrote every sibling's exercise selection with this
     *      block's (differently-seeded, coincidental) list. Haiden: "Updating the engine on some
     *      programs doesn't work" — the block being updated always updated fine; its SIBLINGS were
     *      the casualty, corrupted by a change that had nothing to do with them.
     *   2. A SINGLE SWAP. Changing one exercise on one day rebuilt every OTHER day on every sibling
     *      too — fixing an accessory on day 1 silently overwrote days 2 through N for any sibling
     *      whose own seed had picked something different there.
     *
     * Same mistake both times: treating "this block was saved" as "everything in it is a deliberate
     * choice to propagate." Only a day that actually DIFFERS from what it was before this save is a
     * choice; every other day, and the supersets keyed to it, are carried through untouched — on
     * every sibling. A day is compared by exercise IDENTITY at each position, not merely by length:
     * same count, different lifts, still counts as changed. */
    let anyDayChanged = false;
    const dayChanged = edited.days.map((src, di) => {
        const prevSrc = before?.days?.[di];
        const same = !!prevSrc && src.exercises.length === prevSrc.exercises.length
            && src.exercises.every((id, i) => id === prevSrc.exercises[i]);
        if (!same)
            anyDayChanged = true;
        return !same;
    });
    if (!anyDayChanged)
        return siblings; // e.g. an engine update that only touched slotBias — nothing to carry
    return siblings.map(p => {
        let touched = false;
        const touchedIds = new Set();
        const days = p.days.map((d, di) => {
            const src = edited.days[di];
            if (!src || !dayChanged[di])
                return d; // this day wasn't part of what changed — leave it alone
            touched = true;
            touchedIds.add(d.id);
            const want = d.exercises.length; // keep THIS block's slot count (science-fit per goal)
            // Take edited exercises in order; if this block has fewer slots, truncate (compounds lead).
            let next = src.exercises.slice(0, want);
            // If this block has MORE slots than the edited one, keep the originals for the extra tail.
            if (next.length < want)
                next = next.concat(d.exercises.slice(next.length, want));
            // Remap primary/T2 to the new positions where possible. WITHOUT this the sibling keeps a bare
            // INDEX into a list that just changed underneath it, so a reorder silently re-points the
            // block's primary at whatever lift now occupies that slot.
            const srcPrimaryId = src.exercises[src.primaryIndex];
            const pIdx = next.indexOf(srcPrimaryId);
            const srcT2Id = src.t2Index != null ? src.exercises[src.t2Index] : null;
            const t2Idx = srcT2Id != null ? next.indexOf(srcT2Id) : -1;
            return { ...d, exercises: next, primaryIndex: pIdx > -1 ? pIdx : d.primaryIndex, t2Index: t2Idx > -1 ? t2Idx : (d.t2Index != null && d.t2Index < want ? d.t2Index : null) };
        });
        if (!touched)
            return p; // nothing on this sibling actually needs to change
        // Rebuild supersets ONLY for the days that changed — an untouched day keeps its own pairings,
        // rather than losing them to a rebuild keyed off a day it was never part of.
        let nss = { ...(p.ss || {}) };
        days.forEach(d => { if (!touchedIds.has(d.id))
            return; Object.keys(nss).forEach(k => { if (k.startsWith(`${d.id}:`))
            delete nss[k]; }); });
        if (edited.ss) {
            days.forEach((d, di) => {
                if (!touchedIds.has(d.id))
                    return;
                const src = edited.days[di];
                if (!src)
                    return;
                Object.keys(edited.ss).forEach(k => {
                    const [dayId, slot] = k.split(":");
                    if (dayId === src.id) {
                        const exId = src.exercises[Number(slot)];
                        const ni = d.exercises.indexOf(exId);
                        if (ni > -1 && ni < d.exercises.length - 1)
                            nss[`${d.id}:${ni}`] = true;
                    }
                });
            });
        }
        return { ...remapNextShellRoster(p, days, EXERCISES), ss: nss };
    });
}

const PATTERN_GROUPS = { pulling: ["lats", "upper_back", "biceps"], pushing: ["chest", "shoulders", "triceps"], legs: ["quads", "hamstrings", "glutes"] };

const PATTERN_MIN_SETS = 0.5;

function patternTrainable(parts, kit) {
    if (!kit)
        return true;
    return parts.some(m => { try {
        return availableFor(m, kit, [], false).length > 0;
    }
    catch {
        return true;
    } });
}

function distributeVolBias(program, volBias, field = "slotBias") {
    if (!volBias || !Object.keys(volBias).length) {
        if (field === "autoBias")
            program.autoBias = {};
        return;
    }
    const peak = weeksOf(program);
    const SET_CEIL = 5; // don't pile more than this onto any single exercise — that's junk volume
    // Per-session autoregulation writes a SEPARATE `autoBias` accumulator, rebuilt from scratch each
    // call, so it's applied to the DESIGNED `slotBias` baseline and can never compound (the bug where
    // merging into slotBias every session grew set counts week over week). `slotBias` itself is only
    // written by generation and the manual volume auto-fix — the program's designed distribution.
    const target = field === "autoBias" ? {} : { ...(program.slotBias || {}) };
    // Headroom math is measured against the designed baseline only — never the ephemeral autoBias.
    const baseProg = { ...program, autoBias: undefined };
    const baseVol = weeklyVolume(baseProg, peak); // designed per-muscle weekly volume, for the MRV headroom guard
    PART_ORDER.forEach(part => {
        let delta = volBias[part] || 0;
        if (!delta)
            return;
        const slots = [];
        program.days.forEach(d => d.exercises.forEach((id, si) => {
            if (EX_BY_ID[id]?.part === part) {
                const cur = Number(computeCell(baseProg, d, id, si, peak).sets) || 0;
                slots.push({ key: `${d.id}:${si}`, comp: EX_BY_ID[id]?.type === "compound", cur });
            }
        }));
        if (!slots.length)
            return;
        if (delta > 0) {
            // MRV headroom guard: feedback/performance can only ADD volume up to a muscle's max recoverable
            // volume — never past it. This is what keeps a good-recovery signal from pushing a muscle into
            // junk/unrecoverable volume ("add only when there's headroom below MRV").
            const headroom = Math.max(0, Math.floor(compositeMrv(part) - (baseVol[part] || 0)));
            delta = Math.min(delta, headroom);
            if (delta <= 0)
                return;
            // ADD: round-robin one set at a time onto the slots with the most headroom, isolation-first
            // (don't load a heavy compound past its productive set count), until the delta is spent or
            // every slot hits the per-exercise ceiling. This actually delivers all `need` sets instead of
            // one-per-slot, so a muscle with a single isolation can still climb several sets toward MEV.
            slots.sort((a, b) => (a.comp === b.comp ? 0 : a.comp ? 1 : -1)); // isolation first for added volume
            let guard = 0;
            while (delta > 0 && guard++ < 60) {
                // pick the slot with the lowest projected total (cur + already-added bias) under the ceiling
                let best = null;
                for (const s of slots) {
                    const projected = s.cur + (target[s.key] || 0);
                    if (projected >= SET_CEIL)
                        continue;
                    if (!best || projected < best.projected)
                        best = { ...s, projected };
                }
                if (!best)
                    break; // everything at ceiling
                target[best.key] = (target[best.key] || 0) + 1;
                delta--;
            }
        }
        else {
            // REMOVE: peel sets off the highest-volume slots first, never below 1 working set
            slots.sort((a, b) => (a.comp === b.comp ? 0 : a.comp ? -1 : 1)); // compounds last to shed
            let rem = -delta, guard = 0;
            while (rem > 0 && guard++ < 60) {
                let best = null;
                for (const s of slots) {
                    const projected = s.cur + (target[s.key] || 0);
                    if (projected <= 1)
                        continue; // keep at least one working set
                    if (!best || projected > best.projected)
                        best = { ...s, projected };
                }
                if (!best)
                    break;
                target[best.key] = (target[best.key] || 0) - 1;
                rem--;
            }
        }
    });
    if (field === "autoBias")
        program.autoBias = target;
    else if (Object.keys(target).length) {
        program.slotBias = target;
        program.config = { ...program.config, autoVolume: true };
    }
}

function cellRepRange(cell, program, ex, isPrimary) {
    void program;
    void ex;
    void isPrimary;
    const parse = (value) => {
        if (Array.isArray(value) && value.length >= 2) {
            const a = Number(value[0]), b = Number(value[1]);
            if (a > 0 && b >= a)
                return [a, b];
        }
        const raw = String(value ?? "").trim();
        if (!raw)
            return null;
        const nums = raw.split(/[-–]/).map(Number).filter(Number.isFinite);
        if (nums.length >= 2 && nums[0] > 0 && nums[1] >= nums[0])
            return [nums[0], nums[1]];
        if (nums.length === 1 && nums[0] > 0)
            return [nums[0], nums[0]];
        return null;
    };
    /* M46: the engine-owned cell is the only source of planned rep targets. A missing range does not
       fall back to the removed v661 rep-range policy. The neutral 8–12 default is used only for
       freestyle/unplanned logging surfaces that have no engine prescription. */
    return parse(cell?.range) || parse(cell?.reps) || [8, 12];
}

function repRange(goal, ex, isPrimary, phase) {
    const [lo, hi] = ex.rep;
    const strength = goal === "strength" || (goal === "both" && isPrimary);
    if (!strength)
        return [lo, hi];
    // Isolation (single-joint) lifts don't get loaded into low-rep strength territory even in a
    // strength block: heavy 3–6 rep curls, flyes, or lateral raises are awkward, joint-stressful,
    // and build no meaningful strength the way compounds do — their job is hypertrophy volume. Keep
    // them in their natural range (trimming only the very top rep so they're not pure burnout sets).
    // Strength periodization belongs on the compounds, where it actually drives force production.
    if (ex.type === "isolation") {
        const iHi = Math.max(lo + 2, hi - 2);
        return [lo, Math.max(lo + 1, iHi)];
    }
    const sLo = clamp(Math.round(lo * 0.56), 2, Math.max(2, lo - 1));
    const sHi = clamp(Math.round(hi * 0.6), sLo + 1, hi);
    if (phase == null)
        return [sLo, sHi];
    if (phase < 0.4) { // accumulation — volume base at moderate loads
        const aLo = Math.round((sLo + lo) / 2);
        const aHi = Math.max(aLo + 1, Math.round((sHi + hi) / 2));
        return [aLo, aHi];
    }
    if (phase < 0.8)
        return [sLo, sHi]; // intensification — the classic strength zone
    const rLo = Math.max(2, sLo - 1); // realization / peak — heavy doubles-to-triples territory
    const rHi = Math.max(rLo + 1, sHi - 2);
    return [rLo, rHi];
}

function baseSetsFor(config, ex, isPrimary) {
    const comp = ex.type === "compound";
    let s;
    if (config.goal === "strength")
        s = isPrimary ? 5 : comp ? 4 : 2;
    else if (config.goal === "hypertrophy")
        s = isPrimary ? 4 : comp ? 3 : 3;
    else /* both */
        s = isPrimary ? 4 : comp ? 3 : 3;
    if (config.experience === "none")
        s = Math.max(2, s - 1);
    else if (config.experience === "beginner")
        s = isPrimary ? Math.max(3, s - 1) : s;
    else if (config.experience === "advanced")
        s += isPrimary ? 1 : 0;
    return clamp(s, 2, 5);
}

const STRETCH_PARTIAL_PARTS = new Set(["chest", "biceps", "triceps", "lats", "quads", "hamstrings", "glutes", "calves", "abs"]);

const MYO_PARTS = new Set(["shoulders", "traps", "forearms"]);

function techSetTag(cue) {
    if (!cue)
        return null;
    if (cue.includes("lengthened partial"))
        return "+ partials";
    if (cue.includes("static stretch"))
        return "+ stretch";
    if (cue.includes("myo-rep"))
        return "+ myo-reps";
    if (/drop/i.test(cue))
        return "+ drop set";
    return null;
}

function lastSetTech(ex, strength, isFocus, p, weeks) {
    if (strength || ex.type !== "isolation" || weeks < 3)
        return null;
    if (!(p >= (isFocus ? 0.5 : 0.67)))
        return null;
    if (ex.part === "calves")
        return "Last set: static stretch — hold a loaded stretch ~30s after your final rep";
    if (STRETCH_PARTIAL_PARTS.has(ex.part))
        return "Last set: lengthened partials — extra reps in the stretched position past failure";
    if (MYO_PARTS.has(ex.part))
        return "Last set: myo-reps — to failure, then mini-sets of a few reps with brief rests";
    return null;
}

function customAuthoredSetCount(raw, fallback, weekIndex = 1) {
    const base = canonicalShellSetCount(fallback) ?? 3;
    if (!Array.isArray(raw))
        return canonicalShellSetCount(raw, base) ?? base;
    const values = raw.map(v => canonicalShellSetCount(v)).filter(v => v != null);
    if (!values.length)
        return base;
    const w = Number(weekIndex);
    const idx = Number.isFinite(w) ? Math.max(0, Math.floor(w) - 1) : 0;
    return values[Math.min(idx, values.length - 1)] ?? base;
}

function legacyCustomSetCount(program, day, ex, slotIndex, weekIndex, rawSets, neutralFallback = 3) {
    const slotMap = program?.slotBias || {};
    const autoMap = program?.autoBias || {};
    const hasLegacyDose = Object.keys(slotMap).length > 0 || Object.keys(autoMap).length > 0;
    if (!hasLegacyDose)
        return customAuthoredSetCount(rawSets, neutralFallback, weekIndex);
    const key = `${day?.id}:${slotIndex}`;
    const roleBase = baseSetsFor(program?.config || {}, ex, slotIndex === day?.primaryIndex);
    const authoredBase = rawSets == null ? roleBase : customAuthoredSetCount(rawSets, roleBase, weekIndex);
    const finiteDelta = v => Number.isFinite(Number(v)) ? Number(v) : 0;
    return clamp(Math.round(authoredBase + finiteDelta(slotMap[key]) + finiteDelta(autoMap[key])), 1, 5);
}

function customLastSetTechnique(program, day, ex, slotIndex, weekIndex) {
    const o = program?.overrides?.[`${day?.id}:${slotIndex}`] || {};
    if (Object.prototype.hasOwnProperty.call(o, 'techOverride'))
        return o.techOverride || null;
    if (Object.prototype.hasOwnProperty.call(o, 'tech'))
        return o.tech || null;
    /* M203 — the custom flag is the durable identity. Older migrations/imports can legitimately
       lose engineV/slotBias/autoBias while still preserving `custom: true`, which made every
       implicit last-set amplifier disappear even though the program itself remained runnable.
       This helper is only called from the custom-program branch of computeCell, so do not gate the
       authored program's technique schedule on optional legacy metadata. Explicit technique edits
       above still win, including an explicit null/empty value meaning "off". */
    const authoredCustom = program?.custom === true;
    const weeks = weeksOf(program);
    if (!authoredCustom || program?.config?.progression === 'manual' || slotIndex === day?.primaryIndex
        || (program?.config?.deload && weekIndex > weeks))
        return null;
    const focused = (program?.config?.focusList || []).includes(ex.part) || Number(program?.config?.focus?.[ex.part]) > 0;
    return lastSetTech(ex, goalForDay(program, day) === 'strength', focused, blockPhase(program, weekIndex, 0), weeks);
}

function cycleConfigForStandaloneProgram(program, name) {
    const cfg = { ...(program?.config || {}) };
    const days = Array.isArray(program?.days) ? program.days : [];
    const n = Math.max(1, days.length || Math.round(Number(cfg.days)) || 1);
    cfg.days = n;
    cfg.name = String(name || program?.name || cfg.name || 'Training Cycle').trim() || 'Training Cycle';
    cfg.endless = false;
    const currentSplit = String(cfg.split || '').toLowerCase();
    if (!currentSplit || currentSplit === 'custom') {
        const tags = days.map(d => `${d?.type || ''} ${d?.label || ''}`.toLowerCase());
        const has = word => tags.some(t => t.includes(word));
        const ulppl = n === 5 && ['upper','lower','push','pull','legs'].every(has);
        const upperLower = n >= 2 && tags.every(t => t.includes('upper') || t.includes('lower'));
        const ppl = n >= 3 && tags.every(t => t.includes('push') || t.includes('pull') || t.includes('legs')) && ['push','pull','legs'].every(has);
        cfg.split = ulppl ? 'ulppl' : upperLower ? 'upper_lower' : ppl ? 'ppl' : 'full_body';
    }
    return cfg;
}

function mergeStandaloneIntoGeneratedCycle(program, generated) {
    if (!program?.id || !generated?.cycle || !Array.isArray(generated.blocks) || generated.blocks.length < 2)
        throw new Error('A standalone conversion needs the current program plus at least one future engine block.');
    const generatedFuture = generated.blocks.slice(1);
    const cycle = { ...generated.cycle };
    const sourceMeta = Array.isArray(cycle.blockMeta) ? cycle.blockMeta : [];
    const current = JSON.parse(JSON.stringify(program));
    current.cycleId = cycle.id;
    current.cycleIndex = 0;
    current.blockLabel = sourceMeta[0]?.label || current.blockLabel || 'Current program';
    current.blockNote = 'Existing standalone program kept exactly as cycle Block 1';
    current.config = { ...(current.config || {}), endless: false, cyclePeriodization: true };
    generatedFuture.forEach((block, i) => { block.cycleId = cycle.id; block.cycleIndex = i + 1; });
    cycle.blockIds = [current.id, ...generatedFuture.map(b => b.id)];
    cycle.blockMeta = cycle.blockIds.map((id, i) => i === 0
        ? { ...(sourceMeta[0] || {}), id, label: current.blockLabel, note: current.blockNote, preview: false, legacyCurrent: true }
        : { ...(sourceMeta[i] || {}), id, preview: true });
    cycle.blockMeta = cycleBlockMetadata(cycle, [current, ...generatedFuture]);
    cycle.activeBlock = 0;
    cycle.startedAt = cycle.startedAt || Date.now();
    cycle.nextEngineCycle = { ...(cycle.nextEngineCycle || {}), legacyFirstBlockId: current.id, legacyFirstBlock: true,
        ...(Array.isArray(cycle.nextEngineCycle?.plannedBlocks) ? {
            plannedBlocks: cycle.nextEngineCycle.plannedBlocks.map((block, i) => i === 0 ? { ...block, weeks: weeksOf(current) } : block)
        } : {}) };
    return { cycle, currentProgram: current, blocks: generatedFuture, allBlocks: [current, ...generatedFuture], baseRequest: generated.baseRequest };
}

function advanceLegacyFirstCycleBlock(cycle, programs) {
    const legacyId = cycle?.nextEngineCycle?.legacyFirstBlockId;
    if (!legacyId || Number(cycle?.activeBlock || 0) !== 0 || cycle?.blockIds?.[0] !== legacyId)
        return null;
    const nextId = cycle?.blockIds?.[1];
    const nextProgram = (programs || []).find(p => p?.id === nextId);
    if (!nextId || !nextProgram?.config)
        throw new Error('The first future cycle block is missing; the cycle was left unchanged.');
    const blockMeta = (cycle.blockMeta || []).map((m, i) => i === 1 ? { ...m, preview: false, activatedFromLegacy: legacyId } : m);
    return { cycle: { ...cycle, activeBlock: 1, blockMeta }, nextProgram };
}

function applyCustomProgramSettings(program, draft) {
    const weeks = Math.max(1, Math.min(52, Math.round(Number(draft.weeks) || weeksOf(program))));
    const { folder, description, ...config } = draft;
    return { ...program, weeks, folder: String(folder || '').trim() || undefined,
        description: description || undefined, config: { ...program.config, ...config, weeks } };
}

function computeCell(program, day, id, slotIndex, weekIndex) {
    const ex = EX_BY_ID[id];
    if (!ex)
        return { sets: 0, reps: "—", note: "Exercise unavailable", range: null, rir: null, tech: null, missing: true };
    // Freestyle is deliberately not a generated program. It gets neutral logging defaults from the
    // Next runtime layer, never from the removed production/v661 programming rules.
    if (program?.artifactType === "freestyle" || program?.quick)
        return freestyleCellForRepRange(ex.rep);
    /* A CUSTOM program (Build your own, custom: true) is the lifter's own: their per-slot settings (sets, reps, reps-in-reserve,
       rest, technique) win, and anything they did not set gets the same neutral defaults a Quick workout uses — never the removed
       v661 rules. Without this it fell through to "This older plan is archived — rebuild it" with 0 sets. */
    if (program?.custom === true && program?.engineSource !== "pursuit-next") {
        const base = freestyleCellForRepRange(ex.rep), o = program.overrides?.[`${day?.id}:${slotIndex}`] || {};
        /* Custom-plan overrides can come from backups / old editors as arrays ([10,15], [2,2]).
           Keep the shell shape canonical just like Pursuit Next cells do. Otherwise String([10,15])
           becomes "10,15"; the old progression parser then saw only the first number and treated
           the *bottom* of a 10-15 range as the top, which could award a load increase at 10 reps. */
        const repPair = cellRepRange({ range: o.reps ?? base.reps }, program, ex, slotIndex === day?.primaryIndex);
        const reps = repPair[0] === repPair[1] ? String(repPair[0]) : `${repPair[0]}-${repPair[1]}`;
        const effort = effortBounds(o.rir ?? base.rir);
        const rir = effort ? (effort[0] === effort[1] ? String(effort[0]) : `${effort[0]}-${effort[1]}`) : (o.rir ?? base.rir);
        return { sets: legacyCustomSetCount(program, day, ex, slotIndex, weekIndex, o.sets, base.sets), reps, range: reps, rir, rest: o.rest ?? base.rest, tech: customLastSetTechnique(program, day, ex, slotIndex, weekIndex),
            role: base.role, progressionStyle: program.progStyle?.[id] ?? o.progressionStyle ?? "auto", note: "Your program", custom: true };
    }
    const nextCell = getNextShellCell(program, day, slotIndex, weekIndex);
    if (nextCell)
        return nextCell;
    /* M46: prescription fallback to the v661 set/rep/RIR engine is removed. A Pursuit Next artifact
       either has an engine-owned week cell or is considered incomplete/corrupt and fails visibly.
       Pre-Next saved programs are preserved for migration and must be rebuilt before execution. */
    return {
        sets: 0, reps: "—", note: program?.engineSource === "pursuit-next"
            ? "Prescription data is missing — rebuild this program"
            : "This older plan is archived — rebuild it to continue",
        range: null, rir: null, tech: null, missing: true, engineMissing: true
    };
}

function lastSetEffort(cell, ex, isPrimary, weekIndex, weeks) {
    void ex;
    void isPrimary;
    void weekIndex;
    void weeks;
    const bounds = effortBounds(cell?.rir);
    const failure = !!bounds && bounds[0] === 0 && bounds[1] === 0;
    return { rir: cell?.rir ?? null, failure };
}

const REFERENCES = [
    ["Renaissance Periodization — Dr. Mike Israetel", "Weekly volume landmarks (MEV / MAV / MRV) and RIR-based set progression — the model behind this app's per-muscle volume bars and the accumulation ramp."],
    ["Stronger By Science — Greg Nuckols", "Volume- and frequency-response meta-analyses and program-design work; the ≥2×/week frequency default and flexible rep ranges come from here."],
    ["Schoenfeld et al. — meta-analyses", "Dose–response of weekly sets (2017) and training frequency (2016), and the finding that hypertrophy is similar across ~6–30 reps when sets are taken near failure."],
    ["Pelland et al. 2026 — volume & frequency dose–response (Sports Med)", "The largest analysis of its kind, and the reason this app counts an indirect set as half a set: that method predicted growth better than counting them fully or ignoring them. It also found size and strength still improving as weekly sets rise, with diminishing returns rather than a point where more becomes harmful — so the volume ceiling here is a recovery budget, not a line past which sets stop counting. Frequency mattered for strength; for size, what mattered was the total."],
    ["Refalo et al. — proximity to failure", "Sets taken to failure and sets stopped 1–2 reps short produce comparable growth, while going to failure feels meaningfully worse and costs more recovery. Strength does not require it. Hence the ≥1 RIR floor on heavy compounds, and why the harder sets here are placed on isolations where the cost of a missed rep is lowest."],
    ["Eric Helms — The Muscle & Strength Pyramids", "RIR/RPE autoregulation and the adherence-first hierarchy the defaults follow."],
    ["Block periodization — Issurin; Helms / SBS", "Accumulate → intensify → realize for strength, and the alternating hypertrophy↔strength model behind the block-plan overlay."],
    ["Method lineage", "Several templates implement training structures that were popularised by well-known coaches and communities — tiered T1/T2/T3 loading, training-max percentage waves, ramping and straight 5×5, and weekly volume/intensity undulation. The implementations here are written from published descriptions of those structures; the templates are named for what they do, and are not affiliated with or endorsed by anyone."],
    ["Fuel & recovery", "Every progression here assumes adequate calories, protein (~1.6–2.2 g/kg) and sleep. If progress stalls with no obvious training cause, audit recovery and intake first."],
];

function blockPlanFor(program) {
    const cfg = program?.config || {};
    if (cfg.percentScheme)
        return null; // 5/3/1, GZCL, Texas, etc. periodize themselves
    const weeks = weeksOf(program);
    if (weeks < 3)
        return null;
    const a = Math.max(1, Math.round(weeks / 3));
    const b = Math.max(a + 1, Math.round((2 * weeks) / 3));
    const rng = (lo, hi) => lo >= hi ? `Wk ${lo}` : `Wk ${lo}–${hi}`;
    const deloadPhase = cfg.deload ? { w: `Wk ${weeks + 1}`, label: "Deload", detail: "Roughly half the working sets, ~15–20% lighter, nothing under 4 RIR. The point is recovery, not progress — volume sensitivity resets so the next block can start a touch heavier." } : null;
    if (cfg.goal === "strength") {
        return {
            name: "Block periodization — accumulate → intensify → realize",
            src: "Issurin's block model; volume-to-intensity trade per Stronger By Science / Helms",
            phases: [
                { w: rng(1, a), label: "Accumulation", detail: "Compounds at moderate loads; start around 3 RIR and add load (or a set) each week. Build a base and groove technique — resist maxing out early." },
                { w: rng(a + 1, b), label: "Intensification", detail: "Loads climb, reps drop, RIR falls to 1–2. Bar speed and clean technique are the KPIs; trim isolation volume so the heavy work recovers." },
                { w: rng(b + 1, weeks), label: "Realization · peak", detail: "Heaviest loads, lowest reps, near-limit effort. Accessories sit at maintenance. This is where the strength you built gets expressed." },
                ...(deloadPhase ? [deloadPhase] : []),
            ]
        };
    }
    if (cfg.goal === "both") {
        return {
            name: "Powerbuilding block — build size, then express it",
            src: "Alternating hypertrophy ↔ strength emphasis (Helms / Stronger By Science)",
            phases: [
                { w: rng(1, a), label: "Hypertrophy base", detail: "Moderate reps near 3 RIR; accumulate volume and quality reps. Add a set to a lagging muscle when recovery allows." },
                { w: rng(a + 1, b), label: "Build", detail: "Keep the volume but push load on the main lifts; RIR drifts toward 1–2. Size from the first block starts turning into strength." },
                { w: rng(b + 1, weeks), label: "Intensity · peak", detail: "Heaviest compound work of the block at 0–1 RIR, accessories at maintenance. Express the strength, then reset." },
                ...(deloadPhase ? [deloadPhase] : []),
            ]
        };
    }
    // hypertrophy (default)
    return {
        name: "Hypertrophy mesocycle — MEV → MRV volume ramp",
        src: "Renaissance Periodization set-progression model (Israetel); Schoenfeld volume meta-analyses",
        phases: [
            { w: rng(1, a), label: "Base · MEV", detail: "Start near your minimum effective volume at ~3 RIR. Groove technique and log every load — this is the baseline the rest of the block builds on." },
            { w: rng(a + 1, b), label: "Ramp", detail: "Volume climbs (the generator adds sets across the block) and RIR drifts to 1–2, with small load bumps wherever reps allow. Emphasized muscles gain the most." },
            { w: rng(b + 1, weeks), label: "Overreach · MRV", detail: "Full volume at 0–1 RIR on isolation. Pumps and some DOMS are expected — you're deliberately near the top of what you can recover from." },
            ...(deloadPhase ? [deloadPhase] : []),
        ]
    };
}

function techExplain(tag) {
    const t = (tag || "").toLowerCase();
    if (t.includes("lengthened partial"))
        return "On your last set, once you can't get a full rep, keep doing partial reps in the bottom (stretched) position. Training a muscle while it's lengthened is one of the best-supported ways to drive extra growth in a lagging area.";
    if (t.includes("myo-rep"))
        return "On your last set, take it to true failure, then rest ~10–15 seconds and squeeze out a few more reps; repeat for 2–4 mini-sets. It's a time-efficient way to pack extra effective reps near failure onto a smaller muscle.";
    if (t.includes("drop") || t.includes("reduce load"))
        return "On the final working set, reach the prescribed stopping point, then reduce the load by about 20–30% and continue for another controlled set with the same technique. The lighter load extends the set without turning every set into a maximal-effort set.";
    if (t.includes("static stretch") || t.includes("loaded stretch"))
        return "After your last set, hold the working weight in the fully stretched position for about 30 seconds. Keep the position controlled and stop if it causes joint pain; the goal is extra tension in the target muscle, not forcing range.";
    if (t.includes("intro") || t.includes("ease into"))
        return "The first week of a new block is an intro: volume is pulled back so you can re-acclimate to the block's new exercises, loads and rep focus without digging a fatigue hole. Keep effort moderate and leave reps in reserve — the harder, higher-volume work ramps from next week.";
    if (t.includes("technique focus") || t.includes("ease in") || t.includes("mev"))
        return "Early in the block the loads are intentionally submaximal. Use these weeks to groove clean technique and build a base — the harder, heavier work comes later. Don't rush the weight up yet.";
    if (t.includes("add load") || t.includes("add a set"))
        return "Mid-block: you've established a base, so now progressively add load (and volume, for hypertrophy) week to week while keeping form crisp. This is where most of the adaptation happens.";
    if (t.includes("near limit") || t.includes("heavy"))
        return "Late-block intensity: loads are near your working limit and reps are tough. Push the working sets close to failure with good technique. Expect this to feel hard — that's the point before the deload.";
    if (t.includes("peak"))
        return "Peak week — the heaviest loads of the block on this lift. Treat it as a test of the strength you've built, then recover.";
    if (t.includes("overreach") || t.includes("top volume"))
        return "The highest-volume week of the block. It should feel like a lot — you're deliberately overreaching so the following deload lets you bounce back stronger.";
    if (t.includes("push volume"))
        return "Keep adding working sets and/or load this week. For hypertrophy, accumulating volume across the block is the main driver — chase a bit more than last week.";
    if (t.includes("circuit"))
        return "These are circuit-style rounds: move between exercises with little rest to keep the heart rate up and pack work into less time.";
    if (t.includes("deload"))
        return "Deload week — intentionally light and low-volume. The goal is recovery, not progress: keep the bar moving fast and crisp, and resist the urge to add weight.";
    return "Follow the last-set instruction shown for this exercise, keep the reps controlled, and stop the technique if your form breaks down.";
}

const SESSION_BOUNDS = { s20: [0, 20], s40: [20, 40], s60: [40, 60], s90: [60, 90], s120: [90, 120], s120p: [120, Infinity] };

let REST_SCALE = 1;

function setRestScaleGlobal(v) { REST_SCALE = (v > 0 ? v : 1); }

function effectiveRest(goal, ex, isPrimary, restCustom, scale = REST_SCALE) {
    if (restCustom > 0)
        return Math.max(10, Math.round(restCustom)); // explicit override → honoured exactly
    return Math.max(10, Math.round(restSec(goal, ex, isPrimary) * (scale || 1)));
}

const LEGACY_EX_TYPICAL_KEYS = new Set(["s40", "s60", "s90", "s120", "s120p"]);

const SESSION_EX_TYPICAL = {
    /* ⚠ s20 WAS MISSING, AND THE FALLBACK BELOW TURNED THAT INTO A LIE ON SCREEN. With no row here,
       `SESSION_EX_TYPICAL[session] || SESSION_EX_TYPICAL.s60` handed back the SIXTY-MINUTE row, so the
       wizard advertised "~5 exercises" against "Up to 20 minutes" — more than the "~3" it promised for
       twenty-to-forty, which is the inversion that made this findable at all. The generator was never
       confused: it reads SESSIONS[].count, targets 2, and delivers 2. Only the label was wrong.
       A missing key plus a silent default is worse than a missing key: it produced a confident,
       specific, wrong number instead of an obvious blank. */
    s20: { hypertrophy: 2, strength: 2, both: 2 },
    s40: { hypertrophy: 3, strength: 3, both: 3 }, // v628: engine 15 prices transitions, three fit in forty minutes (measured 2.7 across 66 days)
    s60: { hypertrophy: 5, strength: 5, both: 5 },
    s90: { hypertrophy: 7, strength: 6, both: 6 },
    s120: { hypertrophy: 9, strength: 8, both: 8 },
    s120p: { hypertrophy: 11, strength: 10, both: 11 }, // v637: measured 10.8 / 9.8 / 10.6 across 66 days each; strength fills less because its days are fewer, bigger lifts
};

function sessionExercisePlan(session, goal = "hypertrophy", _experience = "intermediate", eng = ENGINE_V) {
    const [lo, hi] = SESSION_BOUNDS[session] || [0, 60];
    const row = (engLacks(eng, "shortSessionRows") && !LEGACY_EX_TYPICAL_KEYS.has(session) ? null : SESSION_EX_TYPICAL[session]) || SESSION_EX_TYPICAL.s60;
    let count = row[goal] ?? row.hypertrophy;
    /* Engine 15 prices transitions into the budget and three lifts is what forty minutes of hypertrophy
       work holds (measured 2.7 across 66 days). The row above says 3; programs minted before engine 15
       keep the 4 they were built under, because this number is also the ceiling the pattern floor may
       fill a day to (`count + 1`), so it is a generation input and stays frozen with the engine. */
    if (session === "s40" && goal === "hypertrophy" && engLacks(eng, "honestBudget"))
        count = 4;
    /* The label is a promise about what the app will build, so it moves with the engine that builds it:
       before engine 18 the long buckets were capped at 9 exercises and the row said 9. Frozen for older
       programs for the same reason the s40 row is — this count is also the ceiling the pattern floor may
       fill a day to, which makes it a generation input, not decoration. */
    if ((session === "s120p" || session === "s120") && engLacks(eng, "longSessionSlots"))
        count = Math.min(count, 9);
    return { count, lo, hi };
}

function restSec(goal, ex, isPrimary) {
    const comp = ex.type === "compound";
    const strength = goal === "strength" || (goal === "both" && isPrimary);
    if (comp)
        return strength ? 180 : 135;
    return strength ? 105 : 75;
}

const WARMUP_SET_SEC = 60;

function exerciseSlotSec(ex, { sets, goal, isPrimary = false, linked = false, restCustom = 0, scale = REST_SCALE, withTransitions = false }) {
    if (!ex)
        return 0;
    /* The warm-up is charged even when sets is 0, because estimateMinutes has always charged it and
       this helper exists to be exactly what estimateMinutes was. The FITTER's own cost function
       deliberately returns 0 for a dropped slot instead; that is a real difference between the two,
       and it is one of the reasons the fitter still prices separately. Do not "unify" that away
       without re-running gates/fitfloor.mjs — it moves generated output. */
    const rest = linked ? 30 : effectiveRest(goal, ex, isPrimary, restCustom, scale);
    /* CHANGING EXERCISE COSTS TIME, and this model priced it at zero.
       Sets, rest and the warm-up ramp were all charged; walking to the next station, waiting for it,
       collecting dumbbells, and loading the first working weight were not — so the estimate measured
       a lifter alone in a perfect gym. Reported by a lifter whose 90-120 minute sessions the app was
       calling ~78: short by roughly a quarter, consistently, which is the shape of a fixed per-
       exercise cost rather than a rest-length error.

       A compound costs more than an isolation here for the same reason it costs more to warm up: a
       barbell needs loading and a rack needs claiming, where a cable stack needs a pin moved. A
       SUPERSETTED slot pays almost nothing — the whole point is that you are already standing at both
       stations, which is why `linked` collapses the rest above and the transition here too.

       This raises every estimate, which matters because the generator treats session length as a hard
       constraint: days that used to read as under their band now read inside it, which is the correct
       answer for the lifter who reported it, and it removes the pressure to pad a two-hour session
       with exercises it never had time for. */
    /* OFF BY DEFAULT, and that is the important part. Session length is a HARD CONSTRAINT in the
       generator: charge it for transitions and it fits fewer exercises, which measured as 14,175 sets
       lost and seven ceilings rising — days that already fill their band would have been trimmed
       below it. The lifter who reported this has programs that already run 90-120 minutes; the volume
       was right and only the NUMBER SHOWN was wrong. So the generator keeps budgeting with the model
       it was tuned against, and the figures a human reads are billed for the gym they train in. */
    const transition = withTransitions ? (linked ? 15 : (ex.type === "compound" ? 105 : 60)) : 0;
    return warmupCount(ex, isPrimary) * WARMUP_SET_SEC + (sets || 0) * (45 + rest) + transition;
}

const GAP_FRACTION = 0.5;

const COVERED_MUSCLES = new Set(["side_delts", "rear_delts", "chest", "lats", "upper_back", "shoulders",
    "quads", "hamstrings", "glutes", "biceps", "triceps", "calves", "abs"]);

function coverageRelief(program) {
    const gaps = coverageGaps(program);
    if (!gaps.length)
        return null;
    const cfg = program && program.config;
    if (!cfg)
        return null;
    const sessionIds = SESSIONS.map(x => x.id);
    const si = sessionIds.indexOf(cfg.session);
    const splitDays = (SPLITS[cfg.split] && SPLITS[cfg.split].days) || [];
    const nextDays = splitDays.filter(d => d > (cfg.days || 0)).sort((a, b) => a - b)[0];
    const CANDIDATES = [
        si >= 0 && si < sessionIds.length - 1
            ? { id: "session", label: `a ${(SESSIONS[si + 1].label || "").toLowerCase()} session`, patch: { session: sessionIds[si + 1] } }
            : null,
        nextDays ? { id: "days", label: `${nextDays} training days a week`, patch: { days: nextDays } } : null,
    ].filter(Boolean);
    const before = gaps.length;
    let best = null;
    for (const c of CANDIDATES) {
        let trial;
        try {
            trial = generateNextProgramForShell({ config: { ...cfg, ...c.patch }, banned: [], legacyExercises: EXERCISES, seed: program.seed }).program;
        }
        catch {
            continue;
        }
        const left = coverageGaps(trial).length;
        const closed = before - left;
        /* The PATCH travels with the answer. A caller that had to parse the human label back into a
           config change would be reading prose as an API — brittle, and it made the first version of
           gates/relief.mjs fail on its own regex rather than on behaviour. */
        if (closed > 0 && (!best || closed > best.closed))
            best = { ...c, closed, left, before };
    }
    return best; // null when no single change closes anything — see the header
}

function coverageGaps(program, strict = false) {
    const WATCHED = COVERED_MUSCLES; // see the definition — one owner, shared with programQuality
    const out = [];
    try {
        const sv = weeklySubVolume(program, weeksOf(program));
        const wv = weeklyVolume(program, weeksOf(program));
        for (const [region, L] of Object.entries(SUB_LANDMARKS)) {
            if (WATCHED.has(region) && L?.mev > 0 && (sv[region] || 0) < preferenceFloor(program, region) * GAP_FRACTION)
                out.push({ id: region, label: SUBMUSCLE_LABEL[region] || region });
        }
        for (const part of PART_ORDER) {
            const L = landmarkFor(part);
            if (WATCHED.has(part) && L?.mev > 0 && (wv[part] || 0) < preferenceFloor(program, part) * GAP_FRACTION && !out.some(o => o.id === part)) { // same threshold as the heads above
                out.push({ id: part, label: PART_LABEL[part] || part });
            }
        }
    }
    catch (e) {
        /* This catch previously hid a ReferenceError — a mistyped label map made the function return an
           empty array, which is indistinguishable from "this program covers everything". A silent catch
           around a whole computation turns a crash into a false clean bill of health, which is the worse
           failure. Re-thrown in test builds so a gate sees it; still swallowed in the app, where a
           missing notice must never take the screen down. */
        if (strict || (typeof process !== "undefined" && process.env && process.env.NODE_ENV === "test"))
            throw e;
        return [];
    }
    return out;
}

function completionRate(history = []) {
    const rates = [];
    let abandoned = 0, counted = 0;
    for (const h of history || []) {
        const done = Number(h.setsDone) || 0, total = Number(h.totalSets) || 0;
        if (total < 3)
            continue; // too small to read anything from
        counted++;
        const r = done / total;
        if (r < 0.25) {
            abandoned++;
            continue;
        } // an abandoned session, not a short one
        rates.push(Math.min(1, r));
    }
    if (rates.length < 6)
        return { rate: 1, n: rates.length, abandoned, counted, known: false, short: false };
    rates.sort((a, b) => a - b);
    const mid = rates.length % 2 ? rates[(rates.length - 1) / 2]
        : (rates[rates.length / 2 - 1] + rates[rates.length / 2]) / 2;
    /* MEDIAN, not mean: one session cut short by a fire alarm should not move the reading. Same reason
       paceFactor takes a median of its ratios. */
    return {
        rate: mid, n: rates.length, abandoned, counted, known: true,
        /* The actionable threshold. Leaving roughly one set in eight undone, session after session, is a
           plan that does not fit the lifter's day — not a lifter who lacks discipline, and the app should
           offer to shorten the session rather than quietly prescribe more volume to make up for it. */
        short: mid < 0.88
    };
}

function paceFactor(history = [], saved = []) {
    const byId = new Map(saved.map(p => [p.id, p]));
    const ratios = [];
    for (const h of history) {
        const p = byId.get(h.programId);
        const est = h.estMin || (p && p.days ? (() => {
            const d = p.days.find(x => x.id === h.dayId);
            try {
                return d ? estimateMinutes(p, d, h.weekIndex || 1) : 0;
            }
            catch {
                return 0;
            }
        })() : 0);
        const actual = Number(h.durationMin) || 0;
        /* Only sessions that were actually completed and plausibly timed: a session abandoned after two
           sets, or one whose clock ran overnight, says nothing about pace. */
        if (est >= 15 && actual >= 15 && actual <= est * 3 && (h.setsDone || 0) >= (h.totalSets || 1) * 0.6) {
            ratios.push(actual / est);
        }
    }
    if (ratios.length < 4)
        return { factor: 1, n: ratios.length, known: false };
    ratios.sort((a, b) => a - b);
    const mid = ratios.length % 2 ? ratios[(ratios.length - 1) / 2]
        : (ratios[ratios.length / 2 - 1] + ratios[ratios.length / 2]) / 2;
    return { factor: Math.min(1.6, Math.max(0.7, mid)), n: ratios.length, known: true };
}

function addedSeconds(program, day, weekIndex, withTransitions) {
    let sec = 0;
    (day.exercises || []).forEach((id, slot) => {
        const ex = EX_BY_ID[id];
        if (!ex)
            return;
        if (!program.overrides?.[`${day.id}:${slot}`]?.added)
            return;
        sec += exerciseSlotSec(ex, {
            sets: Number(computeCell(program, day, id, slot, weekIndex).sets) || 0,
            goal: goalForDay(program, day), isPrimary: slot === day.primaryIndex,
            linked: !program.config?.noSupersets && !!program.ss?.[`${day.id}:${slot}`],
            restCustom: program.overrides?.[`${day.id}:${slot}`]?.rest,
            withTransitions
        });
    });
    return sec;
}

function addedMinutes(program, day, weekIndex) { return Math.round(addedSeconds(program, day, weekIndex, true) / 60); }

function daySeconds(program, day, weekIndex, withTransitions) {
    let sec = 0;
    (day.exercises || []).forEach((_id, slot) => {
        const id = exerciseAt(program, day, slot, weekIndex);
        const ex = EX_BY_ID[id];
        if (!ex)
            return;
        sec += exerciseSlotSec(ex, {
            sets: Number(computeCell(program, day, id, slot, weekIndex).sets) || 0,
            goal: goalForDay(program, day), isPrimary: slot === day.primaryIndex,
            linked: !program.config?.noSupersets && !!program.ss?.[`${day.id}:${slot}`],
            restCustom: program.overrides?.[`${day.id}:${slot}`]?.rest,
            withTransitions
        });
    });
    return sec;
}

const budgetIncludesTransitions = (program) => engHas(program.engineV || 1, "honestBudget");

function estimateMinutesFor(program, day, weekIndex, pace) {
    return Math.round((daySeconds(program, day, weekIndex, true) / 60) * ((pace && pace.factor) || 1));
}

function estimateMinutes(program, day, weekIndex) {
    return Math.round(daySeconds(program, day, weekIndex, budgetIncludesTransitions(program)) / 60);
}

function fitChip(program, mins, addedMins = 0) {
    const planMins = Math.max(0, mins - (addedMins || 0));
    const chip = fitChipFor(program, planMins);
    return addedMins > 0 ? { ...chip, text: `${chip.text} +${addedMins}m added` } : chip;
}

function fitChipFor(program, mins) {
    const [lo, hi] = SESSION_BOUNDS[program.config.session] || [0, Infinity];
    if (mins > hi)
        return { text: "over target", color: C.warn, dim: C.warnDim };
    if (mins < lo * 0.7)
        return { text: "under target", color: C.muted, dim: C.bg2 };
    return { text: "on target", color: C.accentInk, dim: C.accentDim };
}

const STRETCH_FOCUS_E2 = new Set([
    // chest — deep stretch under load
    "db-fly", "cable-fly", "inc-cable-fly", "high-cable-fly", "cable-crossover", "dips-chest",
    // lats — overhead / pullover stretch
    "db-pullover", "floor-db-pullover", "bent-db-pullover",
    // triceps — overhead long-head stretch
    "oh-cable-ext", "db-oh-ext", "ez-oh-ext", "skullcrusher",
    // biceps — incline / behind-body stretch
    "inc-curl", "bayesian-curl", "incline-hammer",
    // hamstrings — hip hinge at length + seated curl + eccentric
    "rdl", "db-rdl", "slrdl", "stiff-deadlift", "good-morning", "seated-curl", "nordic", "ghr",
    // quads — deep knee flexion
    "sissy-squat", "hack-squat", "pendulum-squat", "bulgarian", "split-squat",
    // side delts — cable behind the body
    "leaning-lat-raise",
    // abs — loaded stretch at the top
    "cable-crunch",
    // calves — straight-knee gastroc stretch
    "standing-calf", "donkey-calf", "leg-press-calf",
]);

const STRETCH_FOCUS_E3 = new Set([
    ...STRETCH_FOCUS_E2,
    // chest — dumbbell pressing reaches a deeper bottom than the barbell (no bar to stop the descent)
    "db-bench", "inc-db-press", "low-incline-db", "db-incline-fly", "bodyweight-deficit-pushup",
    // lats — straight-arm pulldown loads the fully-lengthened lat; cable/machine pullovers likewise
    "straight-pulldown", "cable-pullover", "machine-pullover",
    // side delts — behind-the-back cable + side-lying put the delt under load at length
    "cable-behind-back-lateral", "side-lying-raise",
    // triceps — lying extensions and the skullover/PJR family travel back overhead → long head at length
    "lying-db-ext", "barbell-skullover", "pjr-pullover",
    // glutes — deep hip flexion under load (NOT thrusts, which peak shortened)
    "sumo-squat", "step-through-lunge",
]);

const stretchTable = (eng) => (engHas(eng, "stretchOpener") ? STRETCH_FOCUS_E3 : STRETCH_FOCUS_E2);

const isStretchFocus = (id, eng = 2) => stretchTable(eng).has(id);

const MACHINE_SETUP = {
    // pressing and pulling: fit the seat, then the pad you brace against
    machinepress: ["seat", "back", "rom"], // chest press: seat height, back pad, handle start depth
    machineshoulder: ["seat", "back"],
    machinerow: ["seat", "back", "rom"], // chest-supported row: chest pad fore/aft + handle start
    machinepullover: ["seat", "back", "rom"],
    machineext: ["seat", "back"],
    machinecurl: ["seat", "arm"], // preacher-style elbow pad — not a backrest, not a thigh pad
    machinelatraise: ["seat", "rom"], // seat height sets shoulder-to-pivot; arm pads have a start
    pecdeck: ["seat", "rom"], // seat height + arm-pad start; the pad is a chest rest, not adjustable
    machineshrug: ["seat"],
    machinecrunch: ["seat", "pad", "rom"], // seat, leg restraint, range stop
    // legs
    legpress: ["seat", "back"], // backrest angle (45°) or seat carriage (horizontal)
    hacksquat: ["shoulder"], // you stand in it — shoulder pad height is the fit
    beltsquat: ["belt"], // belt height / lever position
    legext: ["seat", "back", "pad", "ankle", "rom"],
    hipthrustmachine: ["seat", "pad"], // seat/back carriage + the lap pad
    kickback: ["pad", "rom"], // hip pad height + start position
    abduction: ["seat", "back", "rom"], // the start-width lever is a real, distinct setting
    adduction: ["seat", "back", "rom"],
    // bodyweight assist
    assisted: ["knee"], // knee/foot platform — no seat, no backrest
    // legacy coarse id: users who never re-picked equipment still carry it
    machine: ["seat", "back"]
};

const SETUP_FIELD_ORDER = ["seat", "back", "shoulder", "hip", "knee", "belt", "arm", "pad", "ankle", "rom", "pulley", "bench", "hooks", "safety"];

function setupFieldsFor(ex) {
    const equip = new Set(ex.equip || []);
    const name = (ex.name || "").toLowerCase();
    const floorStart = ex.id.includes("deadlift");
    const keys = new Set();
    for (const id of equip)
        (MACHINE_SETUP[id] || []).forEach(k => keys.add(k));
    // --- leg curl: posture decides. Seated has a backrest to set against; lying and standing don't.
    if (equip.has("legcurl")) {
        const lying = /\blying\b|\bprone\b/.test(name), standing = /\bstanding\b/.test(name);
        if (!lying && !standing) {
            keys.add("seat");
            keys.add("back");
        }
        ["pad", "ankle", "rom"].forEach(k => keys.add(k));
    }
    // --- calf: standing sets shoulder-pad height; seated/donkey load the thigh or hips instead.
    if (equip.has("calfmachine")) {
        if (/\bstanding\b/.test(name))
            keys.add("shoulder");
        else if (/\bdonkey\b/.test(name))
            keys.add("hip"); // you lean under a hip pad, bent at the waist
        else {
            keys.add("seat");
            keys.add("pad");
        }
    }
    /* --- cable: the STATION decides, not the fact that it's a stack. A pulldown seats you under the
       bar with a thigh pad; a seated row seats you at a low pulley; everything else — pushdowns, curls,
       flies, face pulls, kickbacks, woodchoppers — you stand or kneel for, and the only thing to set is
       pulley height. That last case is most of them, which is exactly what the old blanket rule got
       wrong on every one. */
    if (equip.has("cable")) {
        keys.add("pulley");
        /* A straight-arm pulldown is a STANDING movement that happens to have "pulldown" in its name —
           matching the word alone gave it a seat and a thigh pad it has never had. */
        const straightArm = /straight/.test(ex.id) || /straight-arm|straight arm/.test(name);
        if (!straightArm && (/pulldown|pull-down/.test(ex.id) || /pulldown/.test(name))) {
            keys.add("seat");
            keys.add("pad");
        }
        else if (/\bseated\b/.test(name))
            keys.add("seat");
    }
    if (equip.has("bench"))
        keys.add("bench");
    // Smith and barbell are rack hardware: set the hooks and the safeties. Floor-start pulls have neither.
    if ((isBarLike(ex.equip) || equip.has("smith")) && !floorStart) {
        keys.add("hooks");
        keys.add("safety");
    }
    return SETUP_FIELD_ORDER.filter(k => keys.has(k)).map(k => ({ key: k, label: setupLabel(k) }));
}

const SETUP_LABELS = { seat: "Seat", back: "Back pad", pad: "Thigh pad", ankle: "Ankle pad", rom: "Start position", shoulder: "Shoulder pad", hip: "Hip pad", knee: "Knee platform", belt: "Belt height", arm: "Arm pad", pulley: "Pulley", bench: "Bench", hooks: "J-hooks", safety: "Safety bars" };

const setupLabel = (k) => SETUP_LABELS[k] || k;

function setupFieldsWithStored(ex, value) {
    const derived = ex ? setupFieldsFor(ex) : [];
    const seen = new Set(derived.map(f => f.key));
    const orphans = Object.keys(value || {})
        .filter(k => !seen.has(k) && value[k])
        .map(k => ({ key: k, label: setupLabel(k), orphan: true }));
    return [...derived, ...orphans];
}

function clearSetup(id, stored, onSetExSetup) {
    Object.keys(stored || {}).forEach(k => onSetExSetup?.(id, k, ""));
}

const SHARE_FONT = 'system-ui, -apple-system, "Segoe UI", Roboto, sans-serif';

function fitText(ctx, text, maxW) {
    const s = String(text == null ? "" : text);
    if (!s || maxW <= 0)
        return "";
    if (ctx.measureText(s).width <= maxW)
        return s;
    let lo = 0, hi = s.length;
    while (lo < hi) {
        const mid = Math.ceil((lo + hi) / 2);
        if (ctx.measureText(s.slice(0, mid) + "…").width <= maxW)
            lo = mid;
        else
            hi = mid - 1;
    }
    return lo > 0 ? s.slice(0, lo).replace(/[\s,·-]+$/, "") + "…" : "…";
}

function fitFont(ctx, text, maxW, weight, max, min) {
    for (let px = max; px > min; px -= 2) {
        ctx.font = `${weight} ${px}px ${SHARE_FONT}`;
        if (ctx.measureText(text).width <= maxW)
            return px;
    }
    ctx.font = `${weight} ${min}px ${SHARE_FONT}`;
    return min;
}

function wrapList(ctx, items, maxW, maxLines) {
    const list = (items || []).map(x => String(x)).filter(Boolean);
    if (!list.length || maxW <= 0 || maxLines <= 0)
        return [];
    const lines = [];
    let cur = null, placed = 0;
    for (const it of list) {
        const cand = cur === null ? it : `${cur}, ${it}`;
        if (ctx.measureText(cand).width <= maxW) {
            cur = cand;
            placed++;
            continue;
        }
        if (cur !== null)
            lines.push(cur);
        if (lines.length >= maxLines) {
            cur = null;
            break;
        }
        cur = ctx.measureText(it).width > maxW ? fitText(ctx, it, maxW) : it;
        placed++;
    }
    if (cur !== null && lines.length < maxLines)
        lines.push(cur);
    const rest = list.length - placed;
    if (rest > 0 && lines.length) {
        const tag = ` +${rest} more`;
        const last = lines[lines.length - 1];
        lines[lines.length - 1] = ctx.measureText(last + tag).width <= maxW
            ? last + tag
            : fitText(ctx, last, Math.max(0, maxW - ctx.measureText(tag).width)) + tag;
    }
    return lines;
}

const SHARE_BRAND = "PURSUIT IRON";

function drawShareBrand(ctx, C, PAD, y, CW, tagline) {
    ctx.textAlign = "left";
    ctx.font = `800 30px ${SHARE_FONT}`;
    ctx.fillStyle = C.accentInk || C.accent || "#8BD450";
    ctx.fillText(SHARE_BRAND, PAD, y);
    const brandW = ctx.measureText(SHARE_BRAND).width;
    if (tagline) {
        ctx.font = `600 26px ${SHARE_FONT}`;
        ctx.fillStyle = C.faint || "#646D7B";
        const room = CW - brandW - 22;
        if (room > 120)
            ctx.fillText(fitText(ctx, `· ${tagline}`, room), PAD + brandW + 16, y + 3);
    }
}

function pickTopSet(sets, weightOf, repsOf) {
    let best = null;
    for (const s of sets || []) {
        const w = weightOf(s), r = repsOf(s);
        if (!(w > 0) || !(r > 0))
            continue;
        if (!best) {
            best = s;
            continue;
        }
        const bw = weightOf(best), br = repsOf(best);
        if (w > bw || (w === bw && r > br))
            best = s;
    }
    return best;
}

function drawRecapCard(ctx, o, W = 1080, H = 1350) {
    const { C, label, dateStr, durMin, doneSets, volume, unit, mainE1rm, prs = [], tops = [] } = o;
    const M = 56, PAD = 104, CW = W - PAD * 2;
    const rr = (x, y, w, h, r) => { if (ctx.roundRect) {
        ctx.beginPath();
        ctx.roundRect(x, y, w, h, r);
    }
    else {
        ctx.beginPath();
        ctx.rect(x, y, w, h);
    } };
    const font = (weight, px) => { ctx.font = `${weight} ${px}px ${SHARE_FONT}`; };
    ctx.fillStyle = C.bg;
    ctx.fillRect(0, 0, W, H);
    ctx.fillStyle = C.card;
    rr(M, M, W - M * 2, H - M * 2, 44);
    ctx.fill();
    ctx.fillStyle = C.accent;
    rr(M, M, W - M * 2, 18, 9);
    ctx.fill();
    ctx.textBaseline = "top";
    ctx.fillStyle = C.accent;
    font(800, 30);
    ctx.fillText("WORKOUT COMPLETE", PAD, 132);
    // Shrink the headline before ever truncating it — a day label is short enough that a couple of
    // points usually buys the whole word back. The old code cut it at 20 characters regardless.
    ctx.fillStyle = C.text;
    fitFont(ctx, label, CW, 800, 76, 48);
    ctx.fillText(fitText(ctx, label, CW), PAD, 176);
    ctx.fillStyle = C.muted;
    font(500, 34);
    ctx.fillText(fitText(ctx, dateStr, CW), PAD, 268);
    const tiles = [["MIN", String(durMin)], ["SETS", String(doneSets)], ["VOLUME", Math.round(volume).toLocaleString()]];
    const tw = (CW - 32) / 3;
    tiles.forEach((t, i) => {
        const x = PAD + i * (tw + 16);
        ctx.fillStyle = C.bg2;
        rr(x, 360, tw, 150, 22);
        ctx.fill();
        // A six-figure total overflowed a fixed 56px tile value, so the number is sized to its own tile.
        ctx.fillStyle = C.accent;
        fitFont(ctx, t[1], tw - 48, 800, 56, 32);
        ctx.fillText(t[1], x + 24, 384);
        ctx.fillStyle = C.muted;
        font(700, 24);
        ctx.fillText(fitText(ctx, t[0] + (t[0] === "VOLUME" ? " " + unit : ""), tw - 48), x + 24, 452);
    });
    let y = 560;
    if (mainE1rm) {
        ctx.fillStyle = C.text;
        font(700, 36);
        ctx.fillText(fitText(ctx, `Est. 1RM ${mainE1rm.name}: ${mainE1rm.est} ${unit}`, CW), PAD, y);
        y += 64;
    }
    /* The reported bug. Three PRs fit; seven did not, and the old line was severed at 44 characters
       with no ellipsis and no indication anything was missing. Names now wrap to a second line when
       there's more than one line's worth, and whatever still doesn't fit is counted rather than
       silently dropped. One line advances 64px exactly as before, so the common case is unchanged. */
    if (prs.length) {
        ctx.fillStyle = C.accent;
        font(700, 34);
        const labelW = ctx.measureText("New PRs: ").width;
        const lines = wrapList(ctx, prs.map(p => p.name), CW - labelW, 2);
        lines.forEach((ln, i) => { ctx.fillText((i === 0 ? "New PRs: " : "") + ln, PAD + (i === 0 ? 0 : labelW), y); y += 44; });
        y += 20;
    }
    y += 10;
    ctx.fillStyle = C.faint;
    font(700, 26);
    ctx.fillText("TOP SETS", PAD, y);
    y += 48;
    /* Rows are budgeted against the space actually left above the footer rather than a fixed
       slice(0, 7): a long title, an e1RM line and a second PR line all push down from here, and the
       last rows used to run underneath the footer text. */
    const footerY = H - 150, rowH = 58, ROW_INK = 36;
    // Rows are spaced rowH apart but only ROW_INK tall, so the limit is where the LAST row's ink ends,
    // not where its slot would. Budgeting by slot (the obvious `(space) / rowH`) loses a whole row and
    // was dropping the seventh lift off a card the original fitted exactly.
    const room = Math.max(0, Math.floor((footerY - 8 - ROW_INK - y) / rowH) + 1);
    const shown = tops.slice(0, tops.length > room ? Math.max(0, room - 1) : room);
    shown.forEach(t => {
        font(700, 36);
        const vw = ctx.measureText(t.set).width;
        ctx.fillStyle = C.accent;
        ctx.textAlign = "right";
        ctx.fillText(t.set, W - PAD, y);
        ctx.textAlign = "left";
        // The name gets whatever the right-aligned value leaves free, so however long an exercise name
        // is the two can never collide in the middle. The old cap was a flat 24 characters.
        ctx.fillStyle = C.text;
        font(600, 36);
        ctx.fillText(fitText(ctx, t.name, CW - vw - 28), PAD, y);
        y += rowH;
    });
    const extra = tops.length - shown.length;
    if (extra > 0) {
        ctx.fillStyle = C.faint;
        font(600, 28);
        ctx.fillText(`+${extra} more ${extra === 1 ? "lift" : "lifts"}`, PAD, y);
    }
    drawShareBrand(ctx, C, PAD, footerY, CW, "tracked set by set");
}

function formatSetup(setup) {
    if (!setup)
        return "";
    return Object.entries(setup).filter(([, v]) => v).map(([k, v]) => `${setupLabel(k)} ${v}`).join(" · ");
}

const SECONDARY = {
    "bb-bench": [["triceps", .5], ["shoulders", .4]], "inc-bb-bench": [["triceps", .5], ["shoulders", .5]],
    "low-inc-bb-bench": [["triceps", .5], ["shoulders", .45]], "high-inc-bb-bench": [["triceps", .45], ["shoulders", .6]],
    "ez-pushdown": [],
    "inc-db-press": [["triceps", .5], ["shoulders", .5]], "db-bench": [["triceps", .5], ["shoulders", .4]],
    "machine-press": [["triceps", .4], ["shoulders", .3]], "smith-bench": [["triceps", .5], ["shoulders", .4]],
    "dips-chest": [["triceps", .5], ["shoulders", .3]], "assisted-dip": [["triceps", .5], ["shoulders", .3]], "pushup": [["triceps", .4], ["shoulders", .3]],
    "ohp": [["triceps", .5]], "db-shoulder": [["triceps", .4]], "machine-shoulder": [["triceps", .4]],
    "arnold": [["triceps", .4]], "pike-pushup": [["triceps", .4]],
    "bb-row": [["biceps", .4], ["shoulders", .3]], "chest-row": [["biceps", .4], ["shoulders", .3]],
    "seated-row": [["biceps", .4], ["shoulders", .3]], "db-row": [["biceps", .4]], "tbar-row": [["biceps", .4], ["shoulders", .3]],
    "machine-row": [["biceps", .4], ["shoulders", .3]], "inv-row": [["biceps", .4]],
    "pullup": [["biceps", .5]], "lat-pulldown": [["biceps", .5]], "chinup": [["biceps", .6]], "assisted-chinup": [["biceps", .6]],
    "deadlift": [["hamstrings", .5], ["glutes", .5], ["traps", .3]], "sumo-dl": [["lower_back", .4], ["quads", .4], ["hamstrings", .3]],
    "back-squat": [["glutes", .5], ["hamstrings", .3]], "front-squat": [["glutes", .4], ["hamstrings", .2]],
    "hack-squat": [["glutes", .4]], "leg-press": [["glutes", .4], ["hamstrings", .2]], "smith-squat": [["glutes", .4], ["hamstrings", .2]],
    "bulgarian": [["glutes", .5], ["hamstrings", .3]], "goblet": [["glutes", .4]], "walking-lunge": [["glutes", .5], ["hamstrings", .2]],
    "bw-squat": [["glutes", .4]], "bw-lunge": [["glutes", .4]], "bw-bulgarian": [["glutes", .5]],
    "rdl": [["glutes", .5], ["lower_back", .3]], "db-rdl": [["glutes", .5], ["lower_back", .2]], "good-morning": [["glutes", .4], ["lower_back", .3]],
    "slrdl": [["glutes", .5]], "hip-thrust": [["hamstrings", .3]], "db-hip-thrust": [["hamstrings", .3]], "sl-hip-thrust": [["hamstrings", .3]], "glute-bridge": [["hamstrings", .2]],
    "cgbp": [["chest", .4], ["shoulders", .2]], "dips-tri": [["chest", .4], ["shoulders", .2]], "diamond-pushup": [["chest", .4]], "bench-dip": [["chest", .2]],
    "hammer": [["forearms", .4]], "farmers": [["traps", .4]],
    // extended library
    "decline-bench": [["triceps", .5], ["shoulders", .3]], "decline-db-press": [["triceps", .5], ["shoulders", .3]],
    "incline-machine-press": [["triceps", .4], ["shoulders", .4]], "floor-press": [["triceps", .5], ["shoulders", .3]],
    "machine-dip": [["triceps", .5], ["shoulders", .3]], "incline-pushup": [["triceps", .4], ["shoulders", .3]],
    "decline-pushup": [["triceps", .4], ["shoulders", .4]], "cable-press": [["triceps", .4], ["shoulders", .3]],
    "pendlay-row": [["biceps", .4], ["shoulders", .3]], "meadows-row": [["biceps", .4], ["shoulders", .3]],
    "seal-row": [["biceps", .4], ["shoulders", .3]], "inc-db-row": [["biceps", .4]],
    "wide-pulldown": [["biceps", .4]], "close-pulldown": [["biceps", .5]], "neutral-pulldown": [["biceps", .5]],
    "one-arm-pulldown": [["biceps", .4]], "assisted-pullup": [["biceps", .5]], "rack-pull": [["traps", .4], ["hamstrings", .3], ["glutes", .3]],
    "kroc-row": [["biceps", .4], ["traps", .3]], "db-pullover": [["chest", .3]], "floor-db-pullover": [["chest", .3]], "bent-db-pullover": [["chest", .2]],
    "seated-ohp": [["triceps", .5]], "seated-db-press": [["triceps", .4]], "smith-ohp": [["triceps", .4]],
    "landmine-press": [["triceps", .4], ["chest", .3]], "upright-row": [["traps", .4]], "db-upright-row": [["traps", .4]],
    "cross-hammer": [["forearms", .4]], "rope-hammer-curl": [["forearms", .4]], "zottman-curl": [["forearms", .5]],
    "jm-press": [["chest", .4]],
    "pendulum-squat": [["glutes", .4], ["hamstrings", .2]], "belt-squat": [["glutes", .4]], "box-squat": [["glutes", .5], ["hamstrings", .3]],
    "split-squat": [["glutes", .4], ["hamstrings", .2]], "step-up": [["glutes", .5], ["hamstrings", .2]],
    "reverse-lunge": [["glutes", .5], ["hamstrings", .2]], "single-leg-press": [["glutes", .4]],
    "stiff-deadlift": [["glutes", .4], ["lower_back", .3]], "ghr": [["glutes", .4], ["calves", .2]],
    "pull-through": [["glutes", .5], ["lower_back", .2]], "kb-swing": [["glutes", .4], ["lower_back", .2]],
    "machine-hip-thrust": [["hamstrings", .3]], "smith-hip-thrust": [["hamstrings", .3]], "reverse-hyper": [["hamstrings", .3], ["lower_back", .2]],
    "push-press": [["triceps", .4]], "underhand-row": [["biceps", .5]], "underhand-pulldown": [["biceps", .5]], "single-cable-row": [["biceps", .4]],
    "close-cable-row": [["biceps", .5], ["upper_back", .4]], "wide-cable-row": [["biceps", .3], ["shoulders", .3]],
    "underhand-cable-row": [["biceps", .5], ["upper_back", .4]], "rope-cable-row": [["biceps", .3], ["shoulders", .3]],
    "dual-cable-row": [["biceps", .4], ["lats", .4], ["shoulders", .3]],
    "wide-machine-row": [["biceps", .3], ["shoulders", .3]], "neutral-machine-row": [["biceps", .4], ["lats", .4]],
    "wide-tbar-row": [["biceps", .4], ["shoulders", .3]],
    "squeeze-press": [["triceps", .4], ["shoulders", .3]], "z-press": [["triceps", .5]],
    "bstance-hip-thrust": [["hamstrings", .3]], "curtsy-lunge": [["hamstrings", .2]],
    "cyclist-squat": [["glutes", .2]], "suitcase-carry": [["traps", .3], ["abs", .3]],
    "trap-bar-shrug": [], "power-shrug": [["upper_back", .2]], "jefferson-curl": [["glutes", .3], ["lower_back", .3]],
    "tate-press": [["chest", .2]], "cable-crossover": [],
    // expansion 2 + 3 compounds
    "incline-cable-press": [["triceps", .4], ["shoulders", .4]], "smith-incline": [["triceps", .5], ["shoulders", .4]],
    "low-incline-db": [["triceps", .5], ["shoulders", .5]], "band-pushup": [["triceps", .4], ["shoulders", .3]],
    "yates-row": [["biceps", .4], ["traps", .3]], "cs-db-row": [["biceps", .4]], "renegade-row": [["biceps", .4], ["abs", .3]],
    "band-pulldown": [["biceps", .4]], "bw-pullup": [["biceps", .5]],
    "trap-bar-deadlift": [["traps", .4], ["hamstrings", .4], ["glutes", .4], ["quads", .3]],
    "snatch-deadlift": [["traps", .4], ["hamstrings", .4], ["glutes", .4]],
    "cable-upright-row": [["traps", .4]], "bradford-press": [["triceps", .5]], "viking-press": [["triceps", .4]],
    "california-press": [["chest", .4]],
    "zercher-squat": [["glutes", .5], ["hamstrings", .3], ["abs", .3]], "lm-squat": [["glutes", .4], ["hamstrings", .2]],
    "heels-up-goblet": [["glutes", .3]], "pistol-squat": [["glutes", .4]], "v-squat": [["glutes", .4]],
    "cable-rdl": [["glutes", .5], ["lower_back", .2]], "band-good-morning": [["glutes", .4], ["lower_back", .3]],
    "back-ext-45": [["glutes", .5], ["hamstrings", .4]],
    "kas-glute-bridge": [["hamstrings", .3]], "band-hip-thrust": [["hamstrings", .3]],
    "sumo-squat": [["quads", .3], ["hamstrings", .2]], "step-through-lunge": [["quads", .4], ["hamstrings", .2]],
    // adductors / abductors
    "hip-abduction": [["glutes", .3]], "cable-abduction": [["glutes", .3]], "lateral-walk": [["glutes", .3]],
    "standing-cable-abduction": [["glutes", .3]], "side-lying-abduction": [["glutes", .2]], "band-abduction": [["glutes", .2]],
    "copenhagen": [["abs", .3]], "cossack-squat": [["glutes", .4], ["quads", .3]],
    "adductor-sumo": [["glutes", .4], ["quads", .3], ["hamstrings", .2]],
    "adduction-machine": [], "cable-adduction": [], "band-adduction": [],
    // expansion 4
    "db-floor-press": [["triceps", 0.4], ["shoulders", 0.2]],
    "db-neutral-press": [["triceps", 0.4], ["shoulders", 0.3]],
    "bodyweight-deficit-pushup": [["triceps", 0.4], ["shoulders", 0.3]],
    "bands-chest-press": [["triceps", 0.4], ["shoulders", 0.3]],
    "helms-row": [["biceps", 0.4]],
    "kb-gorilla-row": [["biceps", 0.4]],
    "t-bar-chest-supported": [["biceps", 0.4]],
    "bodyweight-doorway-row": [["biceps", 0.3]],
    "db-deficit-lunge": [["glutes", 0.4], ["hamstrings", 0.2]],
    "smith-reverse-lunge": [["glutes", 0.4], ["hamstrings", 0.2]],
    "db-deficit-step-up": [["glutes", 0.4], ["hamstrings", 0.2]],
    "jefferson-squat": [["glutes", 0.5], ["hamstrings", 0.3], ["lower_back", 0.2]],
    "bands-squat": [["glutes", 0.4]],
    "deficit-db-rdl": [["glutes", 0.5], ["lower_back", 0.3]],
    "db-good-morning": [["glutes", 0.4], ["lower_back", 0.3]],
    "deficit-barbell-deadlift": [["glutes", 0.5], ["lower_back", 0.4], ["traps", 0.3]],
    "cable-rear-delt-row": [["upper_back", 0.2]],
    "bands-face-pull": [["upper_back", 0.2]],
    "db-incline-shrug": [["upper_back", 0.2]]
};

const squatHamCredit = (ex, eng) => {
    if (!eng || true)
        return null; /* HELD — see the corrections note in ENGINE_RULES */
    const kpat = movePattern(ex);
    if (kpat === "squat" || kpat === "legpress")
        return 0;
    /* conventional and trap-bar pulls only — an RDL is hamstrings-primary and never reaches here */
    if (kpat === "hinge" && ex.part !== "hamstrings")
        return 0.1;
    return null;
    const pat = movePattern(ex);
    return (pat === "squat" || pat === "legpress") ? "hamstrings" : null;
};

const secondaryOf = (ex, eng) => {
    /* ⚠ RETURN THE ORIGINAL ARRAY WHEN NOTHING IS BEING CORRECTED. Rebuilding it unconditionally with
       flatMap produced the same VALUES but fresh arrays, and gates/frozen moved engines 27-30 —
       identical numbers, different object identity, and something downstream is sensitive to it. Only
       allocate when the correction actually applies. */
    const cap = squatHamCredit(ex, eng);
    const raw = SECONDARY[ex.id] || [];
    const base = cap == null ? raw : raw.flatMap(([p, f]) => p === "hamstrings" ? (cap > 0 ? [[p, Math.min(f, cap)]] : []) : [[p, f]]);
    // Compound back movements train both regions: rows hit the lats, vertical pulls hit the
    // mid-back/rhomboids. Credit the non-primary region so volume tracking reflects reality.
    let out = base;
    if (ex.type === "compound") {
        if (ex.part === "upper_back" && !base.some(([p]) => p === "lats"))
            out = [...out, ["lats", 0.4]];
        if (ex.part === "lats" && !base.some(([p]) => p === "upper_back"))
            out = [...out, ["upper_back", 0.4]];
    }
    // Heavy horizontal rows and hinges load the traps hard through scapular retraction / shrug at
    // lockout — crediting that keeps trap volume realistic (otherwise traps sit under MEV on most
    // splits despite all the rowing). Don't double-credit anything that already lists traps.
    const rowsHinges = ex.part === "upper_back" || (ex.type === "compound" && (ex.part === "lower_back" || /deadlift|row|rdl|good-morning|rack-pull|shrug|pull/.test(ex.id)));
    if (rowsHinges && !out.some(([p]) => p === "traps"))
        out = [...out, ["traps", 0.3]];
    return out;
};

const EX_SPECIFIC = {
    // chest
    "bb-bench": "Tuck elbows ~75°, touch just below the nipples, keep wrists stacked over elbows.",
    "inc-bb-bench": "Set the bench ~30° — steeper just turns it into a shoulder press.",
    "low-inc-bb-bench": "Shallow ~15–20° bench — emphasizes the upper chest with less front-delt takeover.",
    "high-inc-bb-bench": "Steep ~45° bench — upper-chest and front-delt focused; expect lighter loads than a flat press.",
    "ez-pushdown": "Angled EZ bar eases wrist strain; elbows pinned, full lockout then a controlled stretch.",
    "inc-db-press": "~30° incline; let the dumbbells stretch a touch below the shoulders.",
    "db-fly": "Hug-a-barrel path with soft, fixed elbows — stretch wide, squeeze hands together.",
    "cable-fly": "Soft fixed elbows; get a deep stretch, then squeeze the hands all the way together.",
    "pec-deck": "Drive with the elbows/upper arms, not the hands; pause on the squeeze.",
    "machine-dip": "Lean the torso forward over the hands to bias the chest.",
    // lats / back
    "lat-pulldown": "Pull the bar to your collarbone with a slight lean-back; lead with the elbows.",
    "db-pullover": "Slight elbow bend; feel the lats stretch overhead — keep the hips low.",
    "bb-row": "Hinge ~45° with a flat braced back; pull to the lower ribs, no heaving.",
    "pendlay-row": "From a dead stop each rep — explosive pull to the lower chest, reset on the floor.",
    "tbar-row": "Flat back, pull the handle into your stomach, squeeze the mid-back.",
    "chest-row": "Chest pinned to the pad; let the arms hang fully, then row to the hips.",
    "seal-row": "Full hang at the bottom, row to the lower ribs, pause and squeeze.",
    "seated-row": "Tall chest, pull to the navel, control back to a full stretch — don't round.",
    // hinge
    "deadlift": "Take the slack out first, push the floor away, drag the bar up your legs.",
    "rack-pull": "Set the pins just below the knee; brace hard and push the floor away.",
    "rdl": "Push the hips back, soft knees — stop when you lose the hamstring stretch, don't round to the floor.",
    "db-rdl": "Hips back, dumbbells close to the legs; chase the stretch, not the floor.",
    "good-morning": "Hips travel back, flat braced back; light load, feel the hamstrings.",
    // shoulders
    "ohp": "Squeeze glutes, brace; push your head 'through the window' at lockout.",
    "db-shoulder": "Press in a slight arc; stop just short of clanging the dumbbells together.",
    "lat-raise": "Lead with the elbows, slight pour-the-pitcher, stop at shoulder height.",
    "cable-lat-raise": "Constant cable tension — lead with the elbow, control all the way down.",
    "rear-fly": "Lead with the elbows, think 'pull apart', pause at the back.",
    "reverse-pec": "Drive the elbows back and apart; pause on the squeeze, slow return.",
    "face-pull": "Pull to your forehead, knuckles to the ceiling, externally rotate at the end.",
    "upright-row": "Lead with the elbows to about chest height; back off if the shoulders pinch.",
    // biceps
    "bb-curl": "Pin the elbows at your sides; no swing — control the lowering.",
    "db-curl": "Supinate as you curl; keep the elbows still, slow the negative.",
    "preacher": "Don't relax at the bottom — keep tension across the full stretch.",
    "hammer": "Neutral grip throughout; control the negative — hits brachialis and forearm.",
    "inc-curl": "Let the arms hang back behind the torso for a big biceps stretch.",
    "bayesian-curl": "Step out so the cable pulls the arm behind you — stretch-loaded the whole rep.",
    // triceps
    "pushdown": "Elbows pinned to your sides; full lockout, then a controlled stretch.",
    "rope-pushdown": "Spread the rope apart at the bottom and lock out hard.",
    "skullcrusher": "Elbows still and slightly back; lower behind the head for a deeper stretch.",
    "oh-cable-ext": "Overhead position maximizes the stretch — keep the elbows in and high.",
    "cgbp": "Hands ~shoulder-width, elbows tucked, drive to a full lockout.",
    // quads / legs
    "back-squat": "Big brace, sit between the hips, knees track over the toes, drive up evenly.",
    "front-squat": "Tall chest, elbows high; let the knees travel forward over the toes.",
    "hack-squat": "Control to depth; don't let the lower back round off the pad at the bottom.",
    "leg-press": "Full but controlled depth; never let the lower back peel off the seat.",
    "bulgarian": "Most of the load through the front heel; drop straight down, not forward.",
    "split-squat": "Front heel drives; keep the torso tall and the rear knee under the hip.",
    "leg-ext": "Pause and squeeze hard at full extension; control the lowering.",
    "walking-lunge": "Step long enough to keep the front shin near-vertical; push through the heel.",
    // hamstrings / glutes
    "lying-curl": "Curl through the full range; keep the hips pinned to the pad.",
    "seated-curl": "The seated position pre-stretches the hamstring — drive through full range.",
    "hip-thrust": "Tuck the chin and ribs, drive through the heels, squeeze hard at the top.",
    "sumo-dl": "Wide stance, knees out, chest up; push the floor apart on the way up.",
    // calves
    "standing-calf": "Pause 1–2s in the bottom stretch, then a full contraction at the top.",
    "seated-calf": "Bent knee biases the soleus — slow, deep stretch on every rep.",
    "leg-press-calf": "Big stretch at the bottom, full plantarflexion at the top, no bouncing.",
    // abs
    "cable-crunch": "Round the spine down toward the knees; keep the hips fixed.",
    "pallof": "Resist the twist — brace and press straight out, return slow.",
    "machine-crunch": "Crunch through the spine, not the hips; pause on the contraction.",
    // traps / forearms
    "bb-shrug": "Straight up and down; pause at the top — no rolling the shoulders.",
    "db-shrug": "Shrug straight up to the ears, brief hold, control down.",
    "wrist-curl": "Let the bar roll to the fingertips, then curl up — slow and full range.",
    "hammer-radial-dev": "Forearm flat, neutral grip, hammer stood upright — tilt the head back toward the thumb side only.",
    "hammer-ulnar-dev": "Same setup, opposite direction — lower toward the little-finger side and control it back.",
    "cable-radial-dev": "Arm fixed at the side; move only the wrist toward the thumb. Light — the range is short.",
    "band-ulnar-dev": "Anchor low, wrist neutral; deviate toward the little finger and resist the return.",
    "farmers": "Tall posture, braced trunk, smooth steps — grip is the limiter.",
    // powerlifting competition variations
    "high-bar-squat": "Bar on the traps, upright torso, knees travel forward — quad-biased and deep.",
    "low-bar-squat": "Bar across the rear delts, more hip hinge and forward lean; sit back into the hips.",
    "paused-squat": "Hold 2–3s at the bottom dead-still, then drive — no bounce out of the hole.",
    "pin-squat": "Settle onto the pins, kill the stretch reflex, then squat it up from a dead stop.",
    "tempo-squat": "Lower over a slow 3–4s count; controlled, no bounce, then stand normally.",
    "spoto-press": "Pause an inch off the chest, hold the bar still, then press — builds raw pressing strength.",
    "larsen-press": "Feet up on the bench (or flat on the floor) — no leg drive, all chest and triceps.",
    "board-press": "Touch the board, brief pause, press — overloads the mid-range and lockout.",
    "pin-bench": "Press from a dead stop off the pins at chest height — no stretch reflex to help.",
    "pause-bench": "Touch, hold 3s motionless on the chest, then press — the competition command.",
    "paused-deadlift": "Pause 1–2s just below the knee, stay tight, then finish the pull.",
    "pin-press-ohp": "Press from a dead stop off the pins at forehead height — builds the sticking point.",
    "barbell-skullover": "A skull-crusher that travels back overhead — keep tension on the long head of the triceps."
};

function assertExerciseData() {
    const ids = new Set(EXERCISES.map(e => e.id));
    const parts = new Set(Object.keys(PART_LABEL));
    const warn = (m) => { try {
        console.warn("[data] " + m);
    }
    catch { } };
    // id-keyed objects: every KEY must be a real exercise id
    [["WEIGHTED_SWAP", WEIGHTED_SWAP], ["SECONDARY", SECONDARY], ["EX_SPECIFIC", EX_SPECIFIC], ["EX_FAMILY", EX_FAMILY]]
        .forEach(([name, obj]) => Object.keys(obj).forEach(k => { if (!ids.has(k))
        warn(`${name}: key "${k}" is not a known exercise id`); }));
    // id Sets: every member must be a real exercise id
    [["ASSIST_IDS", ASSIST_IDS], ["TECHNICAL_LIFTS", TECHNICAL_LIFTS]]
        .forEach(([name, set]) => set.forEach(k => { if (!ids.has(k))
        warn(`${name}: "${k}" is not a known exercise id`); }));
    // WEIGHTED_SWAP values are the graduate-to lift — they must resolve too
    Object.entries(WEIGHTED_SWAP).forEach(([k, v]) => { if (!ids.has(v))
        warn(`WEIGHTED_SWAP["${k}"] -> "${v}" is not a known exercise id`); });
    // SECONDARY values reference muscle parts (not ids) — validate the part names + factor shape
    Object.entries(SECONDARY).forEach(([k, list]) => (list || []).forEach(([part, f]) => {
        if (!parts.has(part))
            warn(`SECONDARY["${k}"] references unknown part "${part}"`);
        if (typeof f !== "number" || !(f >= 0))
            warn(`SECONDARY["${k}"] has a non-numeric factor for "${part}"`);
    }));
    // STRETCH_FOCUS entries must resolve to real exercises (catch a typo silently doing nothing).
    // E3 is a superset of E2, so validating it covers both tables.
    STRETCH_FOCUS_E3.forEach(id => { if (!ids.has(id))
        warn(`STRETCH_FOCUS "${id}" is not a known exercise id`); });
}

function tempoFor(ex) {
    switch (movePattern(ex)) {
        case "press":
        case "overhead":
        case "pushup":
        case "dip": return "2s down · brief pause · drive up";
        case "squat": return "2–3s down · no bounce · drive up";
        case "hinge": return "2–3s down · feel the stretch · drive up";
        case "hipthrust": return "drive up · 1s squeeze · 2s down";
        case "row":
        case "pulldown": return "pull · 1s squeeze · 2s return";
        case "fly":
        case "lateral": return "1s up · 2–3s down · no swing";
        case "curl":
        case "triceps":
        case "supinetriceps": return "1s up · squeeze · 2–3s down";
        case "legext":
        case "legcurl": return "1s up · 1s squeeze · 2s down";
        case "calf": return "1s up · 2s stretch at the bottom";
        case "ab": return "controlled crunch · 2s return";
        case "shrug": return "1s up · 1s hold · 2s down";
        default: return ex.type === "compound" ? "2s down · controlled up" : "2–3s down · controlled";
    }
}

function cuesFor(ex) {
    const spec = EX_SPECIFIC[ex.id];
    const base = patternCues(ex);
    return spec ? [spec, ...base] : base;
}

function patternCues(ex) {
    const n = ex.id, has = (...k) => k.some(s => n.includes(s));
    if (ex.part === "chest" && has("fly", "pec"))
        return ["Soft, fixed elbow angle throughout", "Stretch wide, squeeze hands toward each other", "Slow eccentric — no bouncing at the bottom"];
    if (ex.part === "chest" || (ex.part === "triceps" && has("cgbp", "dip", "pushup")))
        return ["Shoulder blades back & down, slight arch", "Elbows ~45° from torso, not flared", "Touch lower chest, drive through mid-foot"];
    if (has("ohp", "shoulder", "arnold", "pike"))
        return ["Brace abs & squeeze glutes — no excess lean", "Bar/dumbbells over mid-foot at lockout", "Press in a slight arc, head 'through' at top"];
    if (has("lat-raise", "lateral", "rear", "reverse", "face"))
        return ["Lead with the elbows, not the hands", "Slight forward lean; stop ~shoulder height", "Control the lowering for 2–3 sec"];
    if (has("pulldown", "pullup", "chinup"))
        return ["Depress & lead with the elbows", "Full stretch at the top, no swinging", "Drive elbows to the hips, squeeze lats"];
    if (ex.part === "lats")
        return ["Depress the shoulder, lead with the elbow", "Full stretch overhead, no swinging", "Drive the elbows down & in, squeeze the lats"];
    if (ex.part === "upper_back")
        return ["Hinge slightly, flat back, braced", "Pull elbows toward the hips", "Pause & squeeze the shoulder blades together"];
    if (ex.part === "lower_back")
        return ["Brace hard, neutral spine throughout", "Drive from the hips, don't round under load", "Control the range — slow, no jerking"];
    if (has("deadlift", "rdl", "good-morning", "slrdl"))
        return ["Brace hard, neutral spine, lats tight", "Keep the bar dragging close to the body", "Hips back on the way down, drive through floor"];
    if (has("squat", "leg-press", "lunge", "bulgarian", "goblet"))
        return ["Big breath, brace the core before descending", "Knees track over the toes", "Control to depth, drive up evenly"];
    if (has("hip-thrust", "glute-bridge", "kickback"))
        return ["Tuck the chin, ribs down", "Drive through the heels", "Squeeze glutes hard at the top, pause"];
    if (ex.part === "biceps" || has("curl"))
        return ["Pin the elbows, no swinging", "Full stretch at the bottom", "Squeeze at the top, slow the negative"];
    if (ex.part === "triceps")
        return ["Keep elbows tucked & still", "Lock out fully each rep", "Control the stretch, don't bounce"];
    if (ex.part === "calves")
        return ["Pause & stretch at the bottom", "Full plantarflexion at the top", "Slow, controlled — no bouncing"];
    if (ex.part === "abs")
        return ["Move through the spine, not the hips", "Exhale and crunch hard", "Control the return, keep tension"];
    if (ex.part === "neck")
        return ["Move slowly through a comfortable range", "Light load, high reps — never jerk", "Build up volume gradually over weeks"];
    if (ex.part === "adductors")
        return ["Control the stretch — feel the inner thigh lengthen", "Squeeze legs together at the top", "Slow, full range — no bouncing out of the stretch"];
    if (ex.part === "abductors")
        return ["Drive the knee out, lead with the heel", "Slight forward lean loads the glute medius", "Pause at the top, control the return"];
    if (ex.type === "compound")
        return ["Brace your core before each rep", "Full range of motion under control", "Own the eccentric — 2 sec down"];
    return ["Slow eccentric, full range of motion", "Squeeze the target muscle at peak", "Keep tension — avoid using momentum"];
}

function movePattern(ex) {
    const n = ex.id, has = (...k) => k.some(s => n.includes(s));
    if (ex.part === "neck")
        return "generic";
    if (ex.part === "adductors")
        return "adduction";
    if (has("side-lying-abduction"))
        return "sidelyingabduction";
    if (has("band-abduction"))
        return "seatedabduction";
    if (ex.part === "abductors")
        return "abduction";
    if (has("seated-calf"))
        return "seatedcalf";
    if (has("leg-press-calf", "hack-calf", "single-leg-press-calf"))
        return "legpresscalf";
    if (has("donkey-calf"))
        return "donkeycalf";
    if (ex.part === "calves")
        return "calf";
    // Isometric plank-family holds — resisting movement is the entire point, not a dynamic crunch.
    if (ex.part === "abs" && has("plank", "stir-pot"))
        return "plank";
    // Push-up family: a moving plank on the hands. Catches this before the abs/triceps/chest branches
    // below so diamond-pushup (part=triceps, previously a standing triceps-extension pose) and
    // serratus-pushup (part=abs, previously a dynamic crunch) get the correct pose too, not just the
    // plain chest-classified variants.
    if (has("pushup"))
        return "pushup";
    if (ex.part === "abs")
        return "ab";
    if (ex.part === "traps" || has("shrug"))
        return "shrug";
    if (has("dead-hang"))
        return "deadhang";
    if (ex.part === "forearms")
        return "curl";
    if (has("dips-chest", "dips-tri", "assisted-dip", "machine-dip"))
        return "dip";
    if (ex.id === "db-fly" || ex.id === "db-incline-fly")
        return "dbfly"; // supine on a bench, not standing
    if (ex.part === "chest")
        return has("fly", "pec", "crossover") ? "fly" : "press";
    if (has("ohp", "shoulder", "arnold", "push-press", "pike", "landmine", "z-press") || (has("upright") && !has("row")))
        return "overhead";
    if (ex.part === "shoulders" && has("press"))
        return "overhead"; // seated/standing shoulder presses
    if (has("lat-raise", "lateral", "rear", "reverse", "face", "front-raise", "y-raise", "raise", "fly") && ex.part === "shoulders")
        return "lateral";
    if (has("pulldown", "pullup", "chinup", "straight-pulldown"))
        return "pulldown";
    if (has("deadlift", "rdl", "good-morning", "slrdl", "stiff", "pull-through", "swing", "ghr", "hyper", "rack"))
        return "hinge";
    if (has("hip-thrust", "glute-bridge", "thrust", "bridge", "frog", "abduction"))
        return "hipthrust";
    if (has("upright-row"))
        return "uprightrow"; // standing vertical pull to the chin — not a hinge
    if (has("seated-row", "cable-row") && !has("upright"))
        return "seatedrow"; // seated horizontal pull from a low stack
    if (has("row") && !has("pulldown"))
        return "row"; // a row is a horizontal pull even when it emphasizes the lats (close/underhand grips)
    if (ex.part === "upper_back" && has("row", "pull", "pendlay", "meadows", "seal", "kroc"))
        return "row";
    if (ex.part === "lats")
        return "pulldown";
    if (ex.part === "upper_back")
        return "row";
    if (has("superman"))
        return "superman";
    if (ex.part === "lower_back")
        return "hinge";
    if (ex.part === "quads" && (has("leg-ext", "extension")))
        return "legext";
    if (has("bulgarian", "lunge", "split-squat", "pistol"))
        return "lunge";
    if (has("leg-press"))
        return "legpress";
    if (has("step-up"))
        return "stepup";
    if (has("squat", "leg-press", "lunge", "split", "bulgarian", "step-up", "goblet", "pendulum", "belt", "sissy", "hack"))
        return "squat";
    if (ex.part === "quads")
        return "squat";
    if (has("standing-curl"))
        return "standingcurl"; // upright, not prone
    if (ex.part === "hamstrings" && has("curl"))
        return "legcurl";
    if (ex.part === "hamstrings" || ex.part === "glutes")
        return "hinge";
    if (ex.part === "biceps" || has("curl"))
        return "curl";
    if (ex.part === "triceps") {
        // Close-grip/JM/California press are lying BENCH PRESSES with a triceps emphasis — the bar
        // travels to the chest exactly like a regular bench press, not a floor/standing extension.
        if (has("cgbp", "jm-press", "california-press"))
            return "press";
        // Skull crusher, lying DB extension, and the PJR pullover are lying EXTENSIONS — the bar/dumbbells
        // travel toward the forehead with the upper arm fixed, a different motion from either a bench
        // press or the standing overhead extension the plain "triceps" pose below is built for.
        if (has("skullcrusher", "lying-db-ext", "pjr-pullover"))
            return "supinetriceps";
        return "triceps";
    }
    return "generic";
}

const FIGURE_VARIANTS = {
    "bench-dip": "benchdip", "side-lying-raise": "lyinglateral",
    "seated-db-lat-raise": "seatedlateral", "seated-cable-lat-raise": "seatedlateral",
    "chest-supported-lat-raise": "supportedraise", "chest-supported-rear-fly": "supportedraise", "seated-rear-fly": "seatedrearfly",
    "slrdl": "singlerdl", "dragon-flag": "dragonflag", "pike-pushup": "pikepress", "reverse-hyper": "reversehyper", "z-press": "zpress", "bent-db-pullover": "bentpullover",
    "machine-press": "machinepress", "incline-machine-press": "machinepress", "machine-row": "seatedrow", "wide-machine-row": "seatedrow", "neutral-machine-row": "seatedrow",
    "inv-row": "invertedrow", "bodyweight-doorway-row": "invertedrow",
    "leg-raise": "legraise", "reverse-crunch": "reversecrunch", "cable-reverse-crunch": "reversecrunch",
    "dead-bug": "deadbug", "bicycle": "bicycle", "hollow-hold": "hollowhold", "v-up": "vup", "l-sit": "lhold",
    "side-plank": "sideplank", "copenhagen": "sideplank", "ab-wheel": "abwheel", "cable-crunch": "cablecrunch", "machine-crunch": "seatedcrunch", "russian-twist": "twist",
    "wall-sit": "wallhold", "rear-fly": "rearfly", "reverse-pec": "rearfly", "cable-rear-fly": "rearfly", "cable-rear-delt-row": "rearfly", "prone-db-rear-fly": "rearfly", "rear-band-pull-apart": "rearfly",
    "neck-extension": "neck", "neck-curl": "neck", "neck-harness": "neck", "neck-lateral": "neck",
    "sldl": "singlerdl", "bodyweight-reverse-nordic": "reversenordic"
};

function figurePose(ex) {
    if (FIGURE_VARIANTS[ex.id])
        return FIGURE_VARIANTS[ex.id];
    const n = ex.id, has = (...k) => k.some(s => n.includes(s));
    const eq = new Set(ex.equip || []);
    // Hanging ab work is done SUSPENDED from an overhead bar (torso vertical, legs raise) — the plain
    // "ab" pose is a supine floor crunch, an entirely different body orientation.
    if (ex.part === "abs" && (eq.has("pullup") || has("hanging", "toes-to-bar", "windshield")))
        return "hangingab";
    // Standing horizontal presses (cable / band / Svend) press forward while standing UPRIGHT — they
    // were sharing the supine bench-press pose, drawing the lifter lying on a bench that isn't there.
    if (ex.id === "pec-deck")
        return "pecdeck"; // visibly bent forearms against pads, not a straight-arm fly
    /* Front raises are shoulder FLEXION (arm forward) and face pulls are a high horizontal pull with
       the elbows above the wrists. Both were falling into `lateral`, which ABDUCTS the arms out to the
       sides — the wrong plane of motion for one and the wrong movement entirely for the other. These
       must be matched before any shoulder/part-level branch below. */
    if (has("front-raise"))
        return "frontraise"; // front-raise, cable-front-raise, plate-front-raise
    if (has("face-pull"))
        return "facepull"; // face-pull, bands-face-pull
    if (ex.part === "chest" && (has("cable-press", "svend") || (has("bands-chest-press"))))
        return "standpress";
    // Loaded carries & static grip holds: stand tall with the load hanging at the sides. Previously
    // routed through "curl" (part=forearms), animating a dynamic elbow flexion for an isometric hold.
    if (has("plate-pinch"))
        return "platepinch";
    if (has("wrist-roller"))
        return "wristroller";
    /* RADIAL / ULNAR DEVIATION IS A DIFFERENT PLANE FROM A WRIST CURL, and reusing `wristcurl` for it
       would be the v534 defect deliberately re-committed: the drawn body would flex the wrist through
       130 degrees of a sagittal arc for a movement that deviates it through about 50 in the frontal
       one. Matched here, above the `wrist-curl` branch, and by an id fragment that branch cannot also
       claim — `-dev` rather than anything containing "wrist-curl". */
    if (has("-radial-dev", "-ulnar-dev"))
        return "wristdeviate";
    if (has("gripper"))
        return "gripper";
    if (has("farmer", "suitcase", "carry"))
        return "carry";
    // A towel dead-hang is a HANG from a bar (straight arms), not a forearm curl.
    if (has("towel-hang"))
        return "deadhang";
    // Glute kickback drives the working leg BACKWARD from a hinged/supported torso — the "hinge" pose
    // it fell into shows a standing bent-over lift holding a weight, with no leg drive at all.
    if (ex.part === "glutes" && has("kickback"))
        return "glutekick";
    // Reverse hyper & 45° back extension are prone SPINAL EXTENSIONS pivoting over a pad, not a
    // standing barbell hinge.
    if (has("reverse-hyper", "back-ext"))
        return "hyperext";
    // Nordic curl & glute-ham raise are KNEELING hamstring lowers (body rigid from the knee), not a
    // standing hip hinge holding a weight.
    if (has("nordic", "ghr"))
        return "kneelcurl";
    // Standing anti-rotation / chop / side-bend cable core work is done STANDING, resisting a cable
    // from the side — the supine "ab" crunch pose misrepresents the whole movement.
    if (ex.part === "abs" && has("woodchop"))
        return "woodchop";
    if (ex.part === "abs" && has("side-bend"))
        return "sidebend";
    if (ex.part === "abs" && has("serratus") && !has("pushup"))
        return "serratuspull";
    if (ex.part === "abs" && has("pallof"))
        return "standcore";
    if (has("serratus-pushup"))
        return "pushup"; // serratus pushup is done in the pushup/plank position
    // Mountain climbers are performed from a PLANK/hands-down position, not a supine crunch.
    if (has("mountain-climber"))
        return "mountainclimber";
    // Jefferson curl is a standing spinal-flexion hinge with a weight — it was mis-caught by the seated
    // leg-curl remap purely because its id contains "curl".
    if (has("jefferson-curl"))
        return "hinge";
    // Cossack / wide-stance sumo squats are STANDING squats (lateral or wide), not a seated adductor
    // machine (which is where the adductors-part branch sent them).
    if (has("cossack", "adductor-sumo"))
        return "squat";
    // Copenhagen plank is a SIDE-PLANK isometric, not a seated adductor squeeze.
    if (has("copenhagen"))
        return "plank";
    // Triceps split: the coarse classifier lumps every triceps isolation into one overhead-extension
    // pose, so pushdowns looked like overhead extensions. Route each to its real movement.
    if (ex.part === "triceps") {
        if (has("dip"))
            return "dip"; // bench dip / dips are dips, not extensions
        if (has("pushdown", "pressdown"))
            return "pushdown"; // the pinned-elbow downward pushdown
        if (has("kickback"))
            return "trikickback"; // hinged-torso backward extension
        if (has("skullover", "tate"))
            return "supinetriceps"; // lying extensions
        // cgbp/jm/california -> press, skullcrusher/lying-db/pjr -> supinetriceps are handled in movePattern;
        // everything remaining (overhead cable/DB/EZ ext, cross-body, machine ext) is the overhead "triceps".
    }
    // Glute bridge / frog pump are done FLAT ON THE FLOOR (shoulders on the ground), not on a bench like
    // a hip thrust — route them to the floor bridge pose.
    if (ex.id === "glute-bridge" || ex.id === "frog-pump")
        return "bridge";
    // Curl family: everything but a plain standing curl gets its own setup.
    if (has("wrist-curl"))
        return "wristcurl"; // forearm/wrist work, not an elbow curl
    if (has("preacher"))
        return "preacher"; // upper arm on an angled pad
    if (has("concentration"))
        return "concentration"; // seated, elbow braced on the thigh
    if (has("spider"))
        return "spider"; // prone/chest-supported on an incline
    if (has("inc-curl") || has("incline-curl") || has("incline-hammer") || has("seated-incline"))
        return "inclinecurl"; // reclined on an incline bench
    if (has("straight-pulldown") || has("straight-arm"))
        return "straightarm"; // straight-arm pulldown: shoulder isolation
    if (has("pullover"))
        return "pullover"; // single-joint lat/chest pullover, not a pulldown
    if (has("good-morning"))
        return "goodmorning"; // bar on the back, not in the hands
    if (has("swing"))
        return "swing"; // kettlebell/dumbbell swing arc
    if (has("rdl") || has("romanian") || has("stiff-leg") || has("sldl") || has("straight-leg-deadlift") || has("stiff-deadlift"))
        return "rdl"; // straighter-kneed hip hinge
    // Everything else: use the shared classifier, plus the existing seated-leg-curl remap.
    let p = movePattern(ex);
    if (p === "legcurl" && /seated/.test(ex.id))
        p = "seatedcurl";
    return p;
}

const FIGURE_GENERIC_OK = new Set(["neck-extension", "neck-curl", "neck-harness", "neck-lateral"]);

function assertFigureCoverage() {
    const gaps = [];
    EXERCISES.forEach(ex => {
        const p = figurePose(ex);
        if (p === "generic" && !FIGURE_GENERIC_OK.has(ex.id))
            gaps.push(ex.id);
    });
    if (gaps.length) {
        try {
            console.warn("[figure] no animation pose for: " + gaps.join(", "));
        }
        catch { }
    }
    return gaps;
}

function isAxialLoad(ex, _eng = 1) {
    if (ex.type !== "compound" || !isBarLike(ex.equip))
        return false;
    const pat = movePattern(ex);
    if (pat === "hinge" || pat === "squat")
        return true;
    /* ⚠ A SUPPORTED-ROW EXEMPTION WAS ADDED HERE AND REVERTED AS DEAD CODE. The list looked short
       (only seal-row and meadows-row named) but the `isBarLike` guard above already excludes every
       machine and chest-supported row — Chest-Supported Row measures axialCost 0 on every engine.
       I attributed a gates/axialspacing breach to it using a local replica of this function that
       omitted the isBarLike line, which made supported rows look axial when they never were. If this
       rule is ever suspected again, call isAxialLoad itself rather than re-implementing it. */
    if (pat === "row" && !["seal-row", "meadows-row"].includes(ex.id))
        return true; // bent-over barbell rows
    if (pat === "overhead" && !["seated-ohp", "landmine-press", "upright-row"].includes(ex.id))
        return true; // standing OHP / push press
    return false;
}

const BASE_SET_COST = 0.5;

const __recoveryMemo = new WeakMap();

function muscleRecovery(history) {
    if (history && typeof history === "object") {
        const hit = __recoveryMemo.get(history);
        if (hit && Date.now() - hit.at < 60000)
            return hit.v;
    }
    const v = muscleRecoveryUncached(history);
    if (history && typeof history === "object")
        __recoveryMemo.set(history, { v, at: Date.now() });
    return v;
}

const __recovFitMemo = new WeakMap();

function personalRecoveryHours(history, part, clockHours) {
    if (!history || !history.length)
        return null;
    let byPart = __recovFitMemo.get(history);
    if (!byPart) {
        byPart = new Map();
        __recovFitMemo.set(history, byPart);
    }
    if (byPart.has(part))
        return byPart.get(part);
    const sess = []; // per-muscle session best e1RM, oldest→newest
    const sorted = [...(history || [])].filter(h => h && h.date).sort((a, b) => a.date - b.date);
    for (const h of sorted) {
        let best = 0;
        for (const [id, p] of Object.entries(h.perf || {})) {
            const ex = EX_BY_ID[id];
            if (!ex || ex.part !== part)
                continue;
            const ss = setsOf(p);
            for (const s of ss) {
                const w = +(s.w != null ? s.w : s.weight), r = +(s.r != null ? s.r : s.reps);
                if (w > 0 && r > 0 && !s.warm && !s.sub) {
                    const e = e1rmRIR(convertHistoryLoad(w, h.unit, "kg"), r, 0);
                    if (e > best)
                        best = e;
                }
            }
        }
        if (best > 0)
            sess.push({ date: h.date, e: best });
    }
    const obs = []; // (gapHours, performance ratio vs previous session)
    for (let i = 1; i < sess.length; i++) {
        const gap = (sess[i].date - sess[i - 1].date) / 3600000;
        if (gap <= 0 || gap > 240)
            continue;
        obs.push({ gap, ratio: sess[i].e / sess[i - 1].e });
    }
    let out = null;
    const gaps = obs.map(o => o.gap);
    const spread = obs.length ? Math.max(...gaps) - Math.min(...gaps) : 0;
    if (obs.length >= 8 && spread >= 24) {
        const n = obs.length, mx = gaps.reduce((s, g) => s + g, 0) / n, my = obs.reduce((s, o) => s + o.ratio, 0) / n;
        let num = 0, den = 0;
        for (const o of obs) {
            num += (o.gap - mx) * (o.ratio - my);
            den += (o.gap - mx) ** 2;
        }
        const b = den > 0 ? num / den : 0, a = my - b * mx;
        if (b > 1e-5) { // rest must measurably help, else recovery isn't identified
            const est = clamp((1.0 - a) / b, 18, 120); // gap at which performance returns to prior level
            const conf = clamp((n - 8) / 16, 0, 1) * clamp(spread / 48, 0, 1);
            out = clamp(clockHours + conf * (est - clockHours), 18, 120);
        }
    }
    byPart.set(part, out);
    return out;
}

function muscleRecoveryUncached(history) {
    const now = Date.now();
    /* Read effort through the SAME calibration the load engine uses. Fatigue is driven by how close to
       failure a set actually was, not by how close the lifter believed it was — and the app already
       measures the gap between those two and corrects load selection for it. Leaving recovery on the
       raw numbers meant the engine held two contradictory views of the same lifter: sizing their loads
       as though a logged "2 RIR" were really 0, while charging fatigue as though it were a comfortable
       2. For someone logging conservatively this UNDER-states fatigue, so it can raise trims, not lower
       them; that is the honest direction. A lifter with no measurable bias gets bias 0 and is unaffected. */
    const effortBias = (effortCalibration(history) || {}).bias || 0;
    const last = {}; // part -> {date, sets}
    const sorted = [...(history || [])].filter(h => h && h.date).sort((a, b) => b.date - a.date);
    sorted.forEach(h => {
        const sess = {}, overW = {}, overN = {}, prescW = {}, prescN = {};
        Object.entries(h.perf || {}).forEach(([id, p]) => {
            const ex = EX_BY_ID[id];
            if (!ex || !p.sets || !p.sets.length)
                return;
            // Fatigue per set scales with how close to failure it was: a set taken to failure (RIR 0)
            // costs full fatigue, a set left with reps in reserve (what most prescriptions call for)
            // costs less. So training at the prescribed RIR stops accruing failure-level fatigue.
            // Fatigue cost per set is RELATIVE to what was prescribed, not absolute. Training AT the
            // prescribed reserve is by design recoverable, so each such set costs a flat baseline. Cost
            // only climbs when you OVER-reach — logging fewer reps in reserve than prescribed (grinding
            // closer to failure than the plan asked). Leaving MORE in reserve than prescribed costs a
            // touch less. So logging the prescribed RIR never reads as if you trained to failure, and the
            // recovery estimate stops over-stating fatigue on a normal, on-plan session.
            /* DELIBERATELY RAW RIR — do not "fix" this to use the effort calibration.
               It looks like an inconsistency: effortCalibration can conclude a lifter logs ~2 RIR
               conservative, and this reads their logged 2 at face value. But the calibrated RIR already
               reaches the LOAD through dayPerf -> sessionSuggestion (measured: 155lb raw vs 145lb
               calibrated on the same session), so the prescription has already been sized down to put
               their true effort back at the intended reserve. Applying the bias a second time here would
               charge them again for a gap the load has already closed — a conservative logger would sit in
               permanent overreach and be trimmed every session forever.
               The invariant that makes this correct is gated: if the calibration ever stops reaching the
               prescription, gate_templates fails, and THEN this read needs to change. */
            /* Also carry the PRESCRIBED reserve, not just the overshoot. An overshoot of 0.75 means two
               completely different things depending on how much room the plan left: against a prescription
               of RIR 4 the lifter blew through the plan, but against a prescription of RIR 0.76 — which is
               what week 4 of an intensification block actually asks for — it is a quarter of a rep, and the
               plan was already at the edge on purpose. Without this the model could not tell them apart. */
            let overSum = 0, overN_ = 0, prescSum = 0;
            const n = setsOf(p).reduce((a, s) => {
                const presc = s.tr != null ? s.tr : 2; // prescription (default RIR 2 for legacy logs)
                // Calibrated, not raw: what the reserve ACTUALLY was. Unlogged sets still assume on-plan.
                const logged = s.rir != null ? clamp(s.rir - effortBias, 0, 6) : presc;
                const over = Math.max(0, presc - logged); // ground out BELOW plan → harder than asked
                const under = Math.max(0, logged - presc); // left ABOVE plan → easier than asked
                overSum += over;
                overN_++;
                prescSum += presc;
                return a + clamp(BASE_SET_COST + over * 0.2 - under * 0.06, 0.3, 1.3);
            }, 0);
            sess[ex.part] = (sess[ex.part] || 0) + n;
            /* Accumulate weighted by SETS, not Math.max. Taking one accessory to failure while doing the
               other three lifts exactly as prescribed is not the same session as grinding all of them out,
               and max() reported them identically — so the banner would tell someone they took "most of"
               a session to failure when three quarters of it was on plan. The sentence has to be true. */
            overW[ex.part] = (overW[ex.part] || 0) + overSum;
            overN[ex.part] = (overN[ex.part] || 0) + overN_;
            prescW[ex.part] = (prescW[ex.part] || 0) + prescSum;
            prescN[ex.part] = (prescN[ex.part] || 0) + overN_;
            /* ⚠ DELIBERATELY UNTHREADED. This is muscleRecoveryUncached — FATIGUE, not growth. The
               hamstrings genuinely work during a squat (25-50% MVIC co-contraction) and so accrue recovery
               debt, even though the same squat produces no measurable hypertrophy (Kubo, Plotkin). Growth
               credit and fatigue credit are different questions and the kneeFlexedHams correction applies
               only to the first. */
            secondaryOf(ex).forEach(([pp, f]) => { sess[pp] = (sess[pp] || 0) + n * f; });
        });
        Object.entries(sess).forEach(([part, sets]) => { if (!last[part])
            last[part] = { date: h.date, sets, overBy: overN[part] ? (overW[part] / overN[part]) : 0, plannedRIR: prescN[part] ? (prescW[part] / prescN[part]) : null }; });
    });
    return PART_ORDER.map(part => {
        const l = last[part];
        if (!l)
            return { part, readiness: 100, daysSince: null, status: "fresh", sets: 0 };
        const hours = (now - l.date) / 3600000;
        const clockHours = 30 + clamp(l.sets, 0, 28) * 2.3; // on-plan training recovers in ~2 days; overreach (higher cost) extends it
        /* `measured` records WHERE the recovery estimate came from. It matters downstream: a clock
           estimate is a population average and has no idea what frequency this program was designed
           around, while a measured one is this lifter's own demonstrated dip on short rest. Only the
           second is evidence worth overruling a plan with. */
        const personal = personalRecoveryHours(history, part, clockHours);
        const recoveryHours = personal ?? clockHours; // lifter's measured recovery when history earns it, else the clock model
        const readiness = Math.round(clamp(hours / recoveryHours, 0, 1) * 100);
        return { part, readiness, hoursSince: hours, measured: personal != null, overBy: l.overBy || 0, plannedRIR: l.plannedRIR != null ? l.plannedRIR : null, daysSince: Math.floor(hours / 24), status: readiness >= 85 ? "fresh" : readiness >= 55 ? "recovering" : "fatigued", sets: l.sets };
    });
}

function weeklyRecap(history, unit) {
    const now = Date.now(), wk = 7 * 86400000;
    const inWin = (h, a, b) => h.date > now - a && h.date <= now - b;
    const thisW = (history || []).filter(h => h && h.date > now - wk);
    const prevW = (history || []).filter(h => inWin(h, 2 * wk, wk));
    const sets = arr => arr.reduce((s, h) => s + (h.setsDone || 0), 0);
    const vol = arr => arr.reduce((s, h) => s + historyVolumeIn(h, unit || h?.unit), 0);
    const muscle = {};
    // Working sets, not logged rows: the weekly recap names a "top muscle", and a session with myo
    // minis on one lift used to hand that title to whatever muscle happened to run extensions.
    thisW.forEach(h => Object.entries(h.perf || {}).forEach(([id, p]) => { const ex = EX_BY_ID[id]; if (!ex || !p.sets)
        return; muscle[ex.part] = (muscle[ex.part] || 0) + setsOf(p).filter(isWorkSet).length; }));
    const topMuscle = Object.entries(muscle).sort((a, b) => b[1] - a[1])[0] || null;
    const byId = {};
    (history || []).forEach(h => Object.entries(h.perf || {}).forEach(([id, p]) => {
        if (!(p && p.weight > 0))
            return;
        const ss = setsOf(p);
        const best = ss.length ? Math.max(...ss.map(s => e1rm(convertHistoryLoad(s.w, h.unit, unit), s.r || 1))) : e1rm(convertHistoryLoad(p.weight, h.unit, unit), p.reps || 1);
        (byId[id] = byId[id] || []).push({ date: h.date, best });
    }));
    let prs = 0;
    Object.values(byId).forEach(arr => {
        const prior = arr.filter(x => x.date <= now - wk).map(x => x.best);
        const cur = arr.filter(x => x.date > now - wk).map(x => x.best);
        if (cur.length && prior.length && Math.max(...cur) > Math.max(...prior))
            prs++;
    });
    return { count: thisW.length, sets: sets(thisW), vol: Math.round(vol(thisW)), prevCount: prevW.length, prevSets: sets(prevW), prevVol: Math.round(vol(prevW)), topMuscle, prs };
}

const __weeklyVolMemo = new WeakMap();

function __volKey(program, weekIndex) {
    const days = (program.days || []).map(d => (d.exercises || []).join(",")).join("|");
    const bias = program.slotBias ? JSON.stringify(program.slotBias) : "";
    /* ⚠ ANYTHING THAT CHANGES A SET COUNT BELONGS IN THIS KEY. The memo is keyed on exercise ids and
       slotBias; `weekOff` changes what `computeCell` returns and was invisible to it, so a slot turned
       off for a week still reported its old volume — the exact "repair pass reads stale numbers" trap
       this key already carries a warning about, hit again by a new input. */
    const off = program.weekOff ? JSON.stringify(program.weekOff) : "";
    const pair = program.pairs ? JSON.stringify(program.pairs) : "";
    return `${weekIndex}::${days}::${bias}::${off}::${pair}`;
}

function weeklyVolume(program, weekIndex) {
    if (program?.engineSource === "pursuit-next") {
        const snapshot = captureShellVolumeSnapshot(program, weekIndex, EXERCISES);
        if (snapshot) return snapshot.volume;
    }
    const key = __volKey(program, weekIndex);
    let byWeek = __weeklyVolMemo.get(program);
    if (byWeek) {
        const hit = byWeek.get(key);
        if (hit)
            return hit;
    }
    else {
        byWeek = new Map();
        __weeklyVolMemo.set(program, byWeek);
    }
    /* ⚠ TWO OWNERS FOR ONE FACT. `weeklyVolumeOf` in the generator computes the same thing and both
       iterated `program.days` independently — so teaching one about alternating weeks left the other
       reporting every day every week, and a rotating program read 7.0 in both weeks while its rotation
       mean said 5.5. `daysInWeek` is the single answer to "which days happen in week N"; both callers
       ask it. Non-rotating programs get `program.days` unchanged. */
    const map = {};
    daysInWeek(program, weekIndex).forEach(day => {
        day.exercises.forEach((_id, slot) => {
            const id = exerciseAt(program, day, slot, weekIndex);
            const ex = EX_BY_ID[id];
            if (!ex)
                return; // guard against a stale/unknown exercise id (matches weeklySubVolume)
            const sets = Number(computeCell(program, day, id, slot, weekIndex).sets) || 0;
            map[ex.part] = (map[ex.part] || 0) + sets; // direct
            secondaryOf(ex, program.engineV).forEach(([p, f]) => { map[p] = (map[p] || 0) + sets * f; }); // fractional
        });
    });
    if (byWeek.size > 64)
        byWeek.clear(); // a mutating generation pass churns keys; keep the map bounded
    byWeek.set(key, map);
    return map;
}

const fmtSets = (v) => (Math.abs(v - Math.round(v)) < 0.05 ? String(Math.round(v)) : v.toFixed(1));

function weekIntent(program, weekIndex) {
    if (!program?.days?.length)
        return null;
    const weeks = weeksOf(program);
    const isDeload = !!program.config?.deload && weekIndex > weeks;
    const p = blockPhase(program, Math.min(weekIndex, weeks), 0.5);
    /* Sample the program's actual roster: the day's primary compound and, if it has one, an isolation.
       Both come back through the same calls the session screen makes. */
    let primary = null, isoEx = null, isoDay = null, primDay = null, primSlot = -1, isoSlot = -1;
    for (const d of program.days) {
        (d.exercises || []).forEach((id, i) => {
            const ex = EX_BY_ID[id];
            if (!ex)
                return;
            if (!primary && i === d.primaryIndex) {
                primary = ex;
                primDay = d;
                primSlot = i;
            }
            if (!isoEx && ex.type === "isolation") {
                isoEx = ex;
                isoDay = d;
                isoSlot = i;
            }
        });
        if (primary && isoEx)
            break;
    }
    if (!primary)
        return null;
    /* ⚠ READ THE CELL, NOT THE RULE BEHIND IT. The first version called `rirFor` and `repRange`
       directly and was WRONG for every percentage scheme: on a 5/3/1 block it announced "4–8 reps" on
       the overhead press while `computeCell` — the function the plan tab and the session screen both
       render from — prescribed 3-6, because PCT_SCHEMES overrides the generic bracket. The RIR
       happened to agree, so a check on effort alone passed while the sentence contradicted the screen
       beside it. `computeCell` is the owner for what a week actually prescribes; ask it. */
    const wk = Math.min(weekIndex, weeks + (isDeload ? 1 : 0));
    const primCell = computeCell(program, primDay, primary.id, primSlot, wk);
    const isoCell = isoEx ? computeCell(program, isoDay, isoEx.id, isoSlot, wk) : null;
    const primRir = String(primCell.rir);
    const isoRir = isoCell ? String(isoCell.rir) : null;
    const primRange = String(primCell.range || "").split("-");
    /* The intensity technique is asked for, not assumed — `lastSetTech` owns when it appears, and it
       declines for strength work, for blocks under three weeks, and in the intro and deload weeks. */
    const focusSet = new Set(program.config?.focusList || []);
    const tech = isoEx && !isDeload
        ? lastSetTech(isoEx, goalForDay(program, isoDay) === "strength", focusSet.has(isoEx.part), p, weeks)
        : null;
    const here = plannedWeek(program, weekIndex);
    const first = plannedWeek(program, 1);
    const setDelta = (Number(here.sets) || 0) - (Number(first.sets) || 0);
    let label, detail;
    if (isDeload) {
        label = "Deload";
        /* ⚠ A DELOAD DOES NOT ALWAYS CUT SETS, AND SAYING SO WHEN IT DOES NOT IS THE EXACT FAILURE THIS
           WHOLE FUNCTION EXISTS TO AVOID. An s20 program already prescribes the two-set minimum, so its
           deload has nothing left to remove and backs off on EFFORT alone — measured on
           full_body/2/s20: 8 sets in the deload against 8 in week 1. The first draft printed "cut the
           sets — 8 against 8", which is a summary contradicting itself in the same sentence.
           gates/weekintent.mjs check 2 caught it; read the volume rather than assuming it moved. */
        detail = here.sets < first.sets
            ? `hold your loads, cut the sets — ${here.sets} against ${first.sets} in week 1 — and stop well short at ${primRir} RIR`
            : `hold your loads and your ${here.sets} sets, but stop well short at ${primRir} RIR — this week backs off on effort, not volume`;
    }
    else if (weekIndex === 1) {
        label = weeks === 1 ? "Single week" : "Introduction";
        detail = `find your loads at ${primRir} RIR on ${primary.name.toLowerCase()}, ${primRange.join("\u2013")} reps` +
            (isoRir ? `, ${isoRir} RIR on the isolation work` : "");
    }
    else if (weekIndex >= weeks) {
        label = "Peak";
        detail = `the hardest week — ${primRir} RIR on the main lifts` +
            (isoRir ? `, ${isoRir} on isolations` : "") +
            (setDelta > 0 ? `, and ${setDelta} more sets than week 1` : "");
    }
    else {
        label = p < 0.5 ? "Accumulation" : "Intensification";
        detail = `${primRir} RIR on the main lifts` +
            (isoRir ? `, ${isoRir} on isolations` : "") +
            (setDelta > 0 ? ` · ${setDelta} sets added since week 1` : setDelta < 0 ? ` · ${-setDelta} sets fewer than week 1` : "");
    }
    if (tech)
        detail += ` · ${tech.replace(/^Last set: /, "").split(" \u2014 ")[0]} on the last set of suitable isolations`;
    return { label, detail, deload: isDeload };
}

function plannedWeek(program, weekIndex) {
    let sets = 0, mins = 0;
    ((program && program.days) || []).forEach(d => {
        (d.exercises || []).forEach((id, slot) => {
            sets += Number(computeCell(program, d, id, slot, weekIndex).sets) || 0;
        });
        mins += Number(estimateMinutes(program, d, weekIndex)) || 0;
    });
    return { sets: Math.round(sets), mins: Math.round(mins) };
}

function weekMuscleBreakdown(program, weekIndex) {
    const map = {};
    ((program && program.days) || []).forEach(day => {
        dayMuscleBreakdown(program, day, weekIndex).forEach(e => {
            const t = map[e.part] || (map[e.part] = { part: e.part, sets: 0, from: [] });
            t.sets += e.sets;
            e.from.forEach(f => t.from.push({ ...f, dayLabel: day.label }));
        });
    });
    return Object.values(map).map(e => ({ ...e, from: e.from.sort((a, b) => b.contrib - a.contrib) }));
}

function dayMuscleBreakdown(program, day, weekIndex) {
    if (program?.engineSource === "pursuit-next") {
        const rows = shellDayMuscleBreakdown(program, day, weekIndex, EXERCISES);
        if (rows) return rows;
    }
    const map = {};
    ((day && day.exercises) || []).forEach((id, slot) => {
        const ex = EX_BY_ID[id];
        if (!ex)
            return;
        const sets = Number(computeCell(program, day, id, slot, weekIndex).sets) || 0;
        if (!(sets > 0))
            return;
        const add = (part, contrib, direct, factor) => {
            const e = map[part] || (map[part] = { part, sets: 0, from: [] });
            e.sets += contrib;
            e.from.push({ id, name: ex.name, slot, sets, contrib, direct, factor });
        };
        add(ex.part, sets, true, 1);
        secondaryOf(ex, program.engineV).forEach(([pt, f]) => add(pt, sets * f, false, f));
    });
    return Object.values(map)
        .filter(e => e.sets >= 0.5)
        .map(e => ({ ...e, from: [...e.from].sort((a, b) => b.contrib - a.contrib || a.slot - b.slot) }))
        .sort((a, b) => b.sets - a.sets);
}

function dayMuscleVolume(program, day, weekIndex) {
    return dayMuscleBreakdown(program, day, weekIndex).map(e => [e.part, e.sets]);
}

function exerciseProfile(ex) {
    const muscles = { [ex.part]: 1 };
    secondaryOf(ex).forEach(([p, f]) => { muscles[p] = Math.max(muscles[p] || 0, f); });
    return {
        part: ex.part,
        type: ex.type,
        equip: ex.equip || [],
        rep: ex.rep || [],
        pattern: movePattern(ex),
        tempo: tempoFor(ex),
        axial: isAxialLoad(ex),
        cue: (cuesFor(ex) || [])[0] || "",
        muscles
    };
}

function volumeLedger(program, weekIndex) {
    /* EVERY landmark-carrying id present, explicitly zero when untrained. Both source functions omit a
       muscle they found no work for, so `ledger.forearms` came back undefined on a program with no
       forearm work — the right quantity, expressed the one way that is indistinguishable from having
       asked a wrong key. Seeding zeros makes `undefined` mean exactly one thing: that id is not a
       muscle this app models. A caller can then trust `0` and be caught immediately on a typo. */
    const out = {};
    PART_ORDER.forEach(k => { out[k] = 0; });
    Object.keys(SUB_LANDMARKS).forEach(k => { out[k] = 0; });
    Object.assign(out, weeklyVolume(program, weekIndex) || {});
    const sub = weeklySubVolume(program, weekIndex) || {};
    /* Sub-regions LAST and never merged into their parent: a delt head is its own quantity with its
       own landmark, and summing heads into `shoulders` would produce a number no landmark describes
       (measured during v592: summed heads read 62 against a raw shoulder MRV of 26). */
    for (const k of Object.keys(sub))
        out[k] = sub[k];
    return out;
}

function landmarkOf(id) {
    if (SUB_LANDMARKS[id])
        return SUB_LANDMARKS[id];
    return landmarkFor(id) || null;
}

function weeklySubVolume(program, weekIndex) {
    if (program?.engineSource === "pursuit-next") {
        const snapshot = captureShellVolumeSnapshot(program, weekIndex, EXERCISES);
        if (snapshot) return snapshot.subVolume;
    }
    const sub = {};
    const add = (k, v) => { if (k)
        sub[k] = (sub[k] || 0) + v; };
    daysInWeek(program, weekIndex).forEach(day => {
        day.exercises.forEach((_id, slot) => {
            const id = exerciseAt(program, day, slot, weekIndex);
            const ex = EX_BY_ID[id];
            if (!ex)
                return;
            const sets = Number(computeCell(program, day, id, slot, weekIndex).sets) || 0;
            add(subRegionOf(ex), sets); // direct → finest region
            /* ⚠ PRESSING TRAINS THE LATERAL DELT, AND THE MODEL SAID IT DID NOT. An overhead press credits
               front delts directly and triceps as a secondary — nothing reached side delts, so every
               pressing set counted ZERO toward a head that sits under MEV in 41 of 86 programs. The
               reference fractional-attribution table lists the lateral deltoid as a SECONDARY SYNERGIST of
               the overhead press at w = 0.5, alongside triceps.
               Same argument `secondaryOf` already makes for traps ("otherwise traps sit under MEV on most
               splits despite all the rowing"): the volume was being trained and simply not counted.
               MEASURED: side delts under MEV 41 -> 28 of 86 programs, with no slot moved. Engine-gated,
               because it changes the volume arithmetic for every existing program. */
            /* ⚠ PRESSING TRAINS THE LATERAL DELT — AT 0.3, NOT 0.5. An overhead press credits front delts
               directly and triceps as a secondary, and NOTHING reached side delts, so every pressing set
               counted zero toward a head under MEV in 41 of 86 programs.
               THE COEFFICIENT IS THE WHOLE ARGUMENT. One source put the lateral delt at 0.5 as a secondary
               synergist; the EMG and imaging evidence does not support that — Campos et al. measure 27.9%
               MVIC lateral against 33.3% anterior (and 30.3% for an actual lateral raise), and longitudinal
               imaging shows the lateral head grows minimally from vertical pressing because it acts as a
               STABILIZER there, not the force transducer. Empirical range 0.25-0.33. At 0.5 this reported
               side delts under MEV 41 -> 27; at the defensible 0.3 it is 41 -> 36. The larger number was
               the more attractive one and the wrong one. */
            /* HELD — see the coefficient note in ENGINE_RULES. Commented, not guarded: engHas THROWS on
               an unregistered rule.
               WHEN RE-ADDING: gate on an overhead movePattern and add sets * the chosen coefficient to
               side_delts. The call is written out rather than left as live syntax because gates/harness 11
               scans for engHas("name") literals and a held rule name must not appear among them. */
            secondaryOf(ex, program.engineV).forEach(([p, f]) => {
                if (p === "shoulders") {
                    const pat = movePattern(ex);
                    add(pat === "row" || pat === "pulldown" ? "rear_delts" : "front_delts", sets * f);
                }
                else if (p === "traps")
                    add("upper_traps", sets * f);
            });
        });
    });
    return sub;
}

function logWindow(history, days, until, visit) {
    const since = until - days * 86400000;
    (history || []).forEach(h => {
        if (!h || h.date <= since || h.date > until)
            return;
        Object.entries(h.perf || {}).forEach(([id, p]) => {
            const ex = EX_BY_ID[id];
            if (!ex || !p)
                return;
            // isWorkSet, NOT workSetsOf: workSetsOf also demands w > 0, which is right for a 1-RM estimate
            // and wrong here — a set of unweighted pull-ups is logged at w = 0 and is still a set of back.
            const n = setsOf(p).filter(isWorkSet).length; // NOT p.sets.length — extensions are not sets
            if (n)
                visit(ex, n);
        });
    });
}

function loggedVolume(history, days = 7, until = Date.now()) {
    const map = {};
    logWindow(history, days, until, (ex, n) => {
        map[ex.part] = (map[ex.part] || 0) + n; // direct
        secondaryOf(ex).forEach(([part, f]) => { map[part] = (map[part] || 0) + n * f; }); // fractional
    });
    return map;
}

function loggedSubVolume(history, days = 7, until = Date.now()) {
    const sub = {};
    const add = (k, v) => { if (k)
        sub[k] = (sub[k] || 0) + v; };
    logWindow(history, days, until, (ex, n) => {
        add(subRegionOf(ex), n);
        secondaryOf(ex).forEach(([part, f]) => {
            if (part === "shoulders") {
                const pat = movePattern(ex);
                add(pat === "row" || pat === "pulldown" ? "rear_delts" : "front_delts", n * f);
            }
            else if (part === "traps")
                add("upper_traps", n * f);
        });
    });
    return sub;
}

function feedbackDelta(pump, sore) {
    if (sore === "sore")
        return -1;
    if (sore === "fresh")
        return pump === "huge" ? 0 : 1;
    if (sore === "ontime")
        return 0;
    if (pump === "flat")
        return 1;
    if (pump === "huge")
        return 0;
    return undefined; // "good" pump alone, or nothing selected
}

function effortBounds(rir) {
    if (rir == null || rir === "")
        return null;
    let vals = null;
    if (Array.isArray(rir)) {
        vals = rir.slice(0, 2).map(Number).filter(Number.isFinite);
    }
    else if (typeof rir === "number") {
        if (!Number.isFinite(rir))
            return null;
        vals = [rir];
    }
    else {
        const raw = String(rir).trim();
        if (!raw)
            return null;
        // Accept the canonical 1-2 form plus legacy array stringification ("2,2") and en dashes.
        vals = raw.split(/\s*[-,–]\s*/).slice(0, 2).map(Number).filter(Number.isFinite);
    }
    if (!vals || !vals.length)
        return null;
    const a = vals[0], b = vals.length > 1 ? vals[1] : vals[0];
    return a <= b ? [a, b] : [b, a];
}

function effortValueLabel(rir) {
    const bounds = effortBounds(rir);
    if (!bounds)
        return "—";
    const half = (n) => { const r = Math.round(n * 2) / 2; return Number.isInteger(r) ? String(r) : r.toFixed(1); };
    return bounds[0] === bounds[1] ? half(bounds[0]) : `${half(bounds[0])}–${half(bounds[1])}`;
}

function effortLabel(rir, mode) {
    const bounds = effortBounds(rir);
    if (!bounds)
        return "—";
    const conv = n => 10 - n;
    const half = (n) => { const r = Math.round(n * 2) / 2; return Number.isInteger(r) ? String(r) : r.toFixed(1); };
    const [lo, hi] = bounds;
    if (mode !== "rpe")
        return lo === hi ? `${half(lo)} RIR` : `${half(lo)}–${half(hi)} RIR`;
    // Higher RIR means lower RPE, so reverse the converted bounds for an ascending RPE range.
    const rpeLo = conv(Math.round(hi)), rpeHi = conv(Math.round(lo));
    return rpeLo === rpeHi ? `RPE ${rpeLo}` : `RPE ${rpeLo}–${rpeHi}`;
}

const LANDMARKS = {
    chest: { mev: 8, mrv: 22 }, lats: { mev: 8, mrv: 25 }, upper_back: { mev: 8, mrv: 22 }, shoulders: { mev: 8, mrv: 26 },
    lower_back: { mev: 2, mrv: 12 },
    biceps: { mev: 8, mrv: 24 }, triceps: { mev: 6, mrv: 22 }, quads: { mev: 8, mrv: 20 },
    hamstrings: { mev: 6, mrv: 20 }, glutes: { mev: 4, mrv: 26 }, calves: { mev: 8, mrv: 22 },
    abs: { mev: 6, mrv: 25 }, traps: { mev: 6, mrv: 26 }, forearms: { mev: 4, mrv: 20 }, neck: { mev: 4, mrv: 16 },
    adductors: { mev: 4, mrv: 16 }, abductors: { mev: 4, mrv: 16 }
};

const EXP_BAND = {
    novice: { mev: 0.6, mrv: 0.75 },
    beginner: { mev: 0.75, mrv: 0.85 },
    intermediate: { mev: 1, mrv: 1 },
    advanced: { mev: 1.25, mrv: 1.1 },
};

const UNIFORM_LANDMARK = { mev: 4, mav: 18, mrv: 31 };

const uniformLandmarkFor = (part) => {
    const sub = (typeof SUB_LANDMARKS !== "undefined") ? SUB_LANDMARKS[part] : null;
    return (sub && Number(sub.mev) === 0) ? { ...UNIFORM_LANDMARK, mev: 0 } : { ...UNIFORM_LANDMARK };
};

const landmarkFor = (part, exp = "intermediate", src = 0) => {
    if (src?.engineSource === "pursuit-next" && src.nextEngine?.request) {
        const targets = shellVolumeTargets(src);
        const target = targets.find(t => t.part === part || t.region === part);
        if (target) return target;
        if (part === "shoulders") {
            const heads = targets.filter(t => ["side_delts", "rear_delts"].includes(t.region));
            return { mev: heads.reduce((n, t) => n + t.mev, 0), mav: heads.reduce((n, t) => n + t.mav, 0), mrv: heads.reduce((n, t) => n + t.mrv, 0) };
        }
    }
    const stamped = src && typeof src === "object" ? src.landmarks : null;
    if (stamped)
        return stamped.uniform ? uniformLandmarkFor(part) : landmarkForLegacy(part, exp);
    /* held: the uniform table returns here when the rule is registered again */
    /* ⚠ FRONT DELTS NEED AN EXCEPTION WHEN THIS SHIPS, AND THE ATTEMPT BELOW DID NOT WORK. Under
       uniform landmarks the anterior deltoid gets a floor of 4, but its entire requirement is met by
       compound pressing (reconciled DIRECT requirement: zero) — this app has carried MEV 0 for it for
       years. 13 of 20 floor breaches under uniform landmarks were front delts alone.
       The tried fix — read `landmarkForLegacy` and keep a zero — FAILED because front_delts lives in
       SUB_LANDMARKS and landmarkForLegacy does not resolve sub-heads. Check SUB_LANDMARKS explicitly. */
    return landmarkForLegacy(part, exp);
};

const landmarkForLegacy = (part, exp = "intermediate") => {
    const base = (typeof SUB_LANDMARKS !== "undefined" && SUB_LANDMARKS[part]) || LANDMARKS[part] || { mev: 8, mrv: 22 };
    const k = EXP_BAND[exp] || EXP_BAND.intermediate;
    if (k.mev === 1 && k.mrv === 1)
        return base;
    // front delts have MEV 0 by design (pressing supplies them) — scaling zero must stay zero.
    return { ...base, mev: base.mev ? Math.max(1, Math.round(base.mev * k.mev)) : 0, mrv: Math.round(base.mrv * k.mrv) };
};

const mavFor = (part, src = 0) => { const L = landmarkFor(part, "intermediate", src); return L.mav != null ? L.mav : (L.mev + L.mrv) / 2; };

const DELT_HEAD = (id) => {
    if (/rear|face-pull|reverse-fly|rev-fly|reverse-pec|cable-rear/.test(id))
        return "rear_delts";
    if (/lat-raise|lateral|side-lying|upright|y-raise|lu-raise|cable-raise/.test(id))
        return "side_delts";
    if (/front-raise|ohp|shoulder|arnold|press|pike|landmine|push-press|z-press/.test(id))
        return "front_delts";
    return "side_delts";
};

const SUBMUSCLE_LABEL = {
    front_delts: "Front Delts", side_delts: "Side Delts", rear_delts: "Rear Delts",
    upper_traps: "Upper Traps", serratus: "Serratus", obliques: "Obliques", tibialis: "Tibialis",
    /* The forearm is not one muscle any more than the deltoid is. Wrist FLEXORS (palm side) and wrist
       EXTENSORS (back of the hand) are separate groups with separate jobs, and GRIP work — carries,
       hangs, pinches, a gripper — is an isometric hold that trains neither through a range. Naming them
       is what lets the coverage rule below spread the work instead of piling it all in one place. */
    wrist_flexors: "Wrist Flexors", wrist_extensors: "Wrist Extensors", wrist_deviators: "Wrist Deviators", grip: "Grip"
};

const SUB_LANDMARKS = {
    front_delts: { mev: 0, mrv: 12 }, side_delts: { mev: 8, mrv: 26 }, rear_delts: { mev: 8, mrv: 24 },
    upper_traps: { mev: 4, mrv: 26 }, serratus: { mev: 4, mrv: 16 }, obliques: { mev: 4, mrv: 16 }, tibialis: { mev: 4, mrv: 16 }
};

function subRegionOf(ex) {
    if (!ex)
        return null;
    if (ex.part === "shoulders")
        return DELT_HEAD(ex.id);
    if (ex.region)
        return ex.region; // explicit tag (serratus/obliques/tibialis/upper_traps)
    if (ex.part === "traps")
        return "upper_traps";
    return null;
}

const REGION_REQUIRED = {
    // Flexion and extension are the two with real cross-sectional area and real carryover; deviation is
    // a ~50° assistance role and is the THIRD slot's direction (v577). A gap rule that demanded all
    // three would fire on almost every program, and a rule that fires on everything is a broken rule.
    forearms: ["wrist_flexors", "wrist_extensors"],
    // Side and rear delts get nothing for free — pressing feeds the front head only. Same class.
    shoulders: ["side_delts", "rear_delts"]
};

function regionGapsFor(program, part) {
    const req = REGION_REQUIRED[part];
    if (!req || !program || !Array.isArray(program.days))
        return [];
    const seen = new Set();
    program.days.forEach(d => (d.exercises || []).forEach(id => {
        const ex = EX_BY_ID[id];
        if (!ex || ex.part !== part)
            return;
        const r = subRegionOf(ex);
        if (r)
            seen.add(r);
    }));
    return req.filter(r => !seen.has(r));
}

const REGION_MOVEMENT = {
    wrist_flexors: { name: "wrist flexion", eg: "a wrist curl" },
    wrist_extensors: { name: "wrist extension", eg: "a reverse wrist curl" },
    wrist_deviators: { name: "wrist deviation", eg: "a hammer radial/ulnar deviation" },
    side_delts: { name: "side delt", eg: "a lateral raise" },
    rear_delts: { name: "rear delt", eg: "a reverse fly or face pull" }
};

function regionGapFix(part, gaps) {
    if (!gaps.length)
        return null;
    const m = gaps.map(g => REGION_MOVEMENT[g] || { name: (SUBMUSCLE_LABEL[g] || g).toLowerCase(), eg: "a direct movement" });
    const list = m.length === 1 ? m[0].name : m.map(x => x.name).join(" or ");
    const egs = m.map(x => x.eg).join(" and ");
    return part === "forearms"
        ? `No ${list} work — carries, hangs and pinches are isometric holds and never take the wrist through a range, so more sets on them cannot fix this. Add ${egs}.`
        : `No direct ${list} work — pressing feeds the front head only, so these get nothing for free. Add ${egs}.`;
}

function volumeZone(part, sets, program = 0) {
    const { mev, mrv } = landmarkFor(part, program?.config?.experience || "intermediate", program);
    const mav = mavFor(part, program);
    /* FOUR BANDS, NOT THREE. "Productive" covered everything from MEV to MRV — a range wide enough
       that 9 sets and 21 sets read identically, when one is the middle of the growth range and the
       other is at the edge of what the week budgets. Splitting it at MAV is the same information the
       generator already uses (mavFor is the readiness-trim's own threshold), just shown.

       THE SUBTITLES ARE DELIBERATELY WEAKER THAN THE ONES THIS IDEA CAME WITH. The AI Studio build
       paired these bands with "Below maintenance threshold" and "Exceeds systemic recovery". Both
       assert more than the app knows:
         - MEV is the MINIMUM EFFECTIVE volume, not maintenance. Maintenance volume is a separate and
           LOWER landmark, so a lifter under MEV is very often still maintaining. "Below maintenance"
           tells them they are losing muscle, which the number does not say.
         - "Exceeds systemic recovery" is the same over-claim as the old "junk volume" label removed
           above, in new words: it states as fact that the lifter cannot recover from these sets.
           MRV is what THIS APP budgets for, and the dose-response evidence has gains still rising at
           the top of the studied range.
       So the bands are adopted and the claims are not. */
    if (sets < mev)
        return { color: C.muted, label: "below MEV", sub: "under the volume that reliably drives growth" };
    if (sets <= mav)
        return { color: C.accentInk, label: "productive", sub: "inside the range this app plans for" };
    /* `mrv + MRV_GRAIN`, matching what `capWeeklyVolume` is willing to leave. Without the grain this
       line called the generator's own deliberate output a defect — see the note at MRV_GRAIN. */
    if (sets <= mrv + MRV_GRAIN)
        return { color: C.accentInk, label: "high", sub: "near the top of the planned range" };
    return { color: C.warn, label: "over MRV", sub: "past what this app budgets to recover from" }; // label text is user-visible — see the note above
}

const COMPOSITE_HEADS = { shoulders: ["front_delts", "side_delts", "rear_delts"] };

function compositeMrv(part, program = 0) {
    const heads = COMPOSITE_HEADS[part];
    if (!heads)
        return landmarkFor(part, "intermediate", program).mrv;
    if (program && typeof program === "object" && program.landmarks?.uniform) {
        return heads.reduce((s) => s + landmarkFor("side_delts", "intermediate", program).mrv, 0);
    }
    return heads.reduce((s, h) => s + (SUB_LANDMARKS[h]?.mrv || 0), 0);
}

function partZone(part, sets, subVolume, program = null) {
    if (program?.engineSource === "pursuit-next") return volumeZone(part, sets, program);
    const heads = COMPOSITE_HEADS[part];
    if (!heads || !subVolume)
        return volumeZone(part, sets);
    const { mev } = landmarkFor(part);
    if (sets < mev)
        return { color: C.muted, label: "below MEV", sub: "under the volume that reliably drives growth" };
    // Only DIRECT-work heads can meaningfully be "over MRV". Heads with mev=0 (front delts) are
    // fed almost entirely by indirect volume from compound pressing, so reading above their small
    // direct-work MRV is expected and healthy — flagging it would defeat the whole composite fix.
    const anyHeadOver = heads.some(h => {
        const lm = SUB_LANDMARKS[h];
        if (!lm || lm.mev === 0)
            return false;
        return (subVolume[h] || 0) > (lm.mrv != null ? lm.mrv + MRV_GRAIN : Infinity);
    });
    if (anyHeadOver)
        return { color: C.warn, label: "a head over MRV", sub: "one deltoid head is past its own ceiling" };
    /* Composites get the same MAV split as everything else, so a shoulder total near its ceiling no
       longer reads identically to one in the middle of the range. The over-MRV verdict still belongs
       to the per-head test above — only the productive band is being subdivided here. */
    return sets <= mavFor(part)
        ? { color: C.accentInk, label: "productive", sub: "inside the range this app plans for" }
        : { color: C.accentInk, label: "high", sub: "near the top of the planned range" };
}

const roundTo = (x, step) => {
    const v = Number(x), st = Number(step);
    if (!Number.isFinite(v))
        return 0;
    if (!Number.isFinite(st) || st <= 0)
        return v;
    return Math.round(v / st) * st;
};

const LOWER_PARTS = ["quads", "hamstrings", "glutes"];

function lowerBodyLift(ex) {
    if (!ex)
        return false;
    if (LOWER_PARTS.includes(ex.part))
        return true;
    return ex.part === "lower_back" && movePattern(ex) === "hinge" && isBarLike(ex.equip);
}

let LOAD_INC = { v: 0, unit: null };

function setLoadInc(v, unit) { LOAD_INC = { v: v || 0, unit: unit || null }; }

let EX_LOAD_INC = {};

function setExLoadInc(map) { EX_LOAD_INC = map || {}; }

function loadStep(ex, unit) {
    // A per-exercise increment (e.g. a specific machine's pin-stack notch) wins over everything —
    // it's the user telling us exactly how this machine actually moves.
    if (ex && EX_LOAD_INC[ex.id] && EX_LOAD_INC[ex.id].v > 0 && EX_LOAD_INC[ex.id].unit === unit)
        return EX_LOAD_INC[ex.id].v;
    // User-set global plate increment wins next when it applies (their actual smallest jump).
    if (LOAD_INC.v > 0 && LOAD_INC.unit === unit)
        return LOAD_INC.v;
    const lower = LOWER_PARTS.includes(ex.part);
    // Fixed/selectorized loads jump coarser than a micro-loadable barbell: a dumbbell pair or a
    // machine stack typically moves in ~5 lb / 2.5 kg notches per hand or plate, and you can't
    // split it. Reflect that so progression math doesn't assume bar-fine increments it can't make.
    const eq = ex.equip || [];
    const fixedJump = (eq.includes("dumbbell") || eq.includes("kettlebell")) && !isBarLike(eq);
    const machineJump = isMachineLike(eq) && !isBarLike(eq) && !eq.includes("dumbbell");
    // Isolations on a machine/cable (triceps pushdown, lateral raise, curl, leg extension) should not
    // jump a full pin-stack notch — 10 lb on a small muscle is a 15-30% leap that stalls progress.
    // Treat compound machine work (leg press, pulldown, row, hack squat) as the coarse 10 lb stack,
    // but step isolations at 5 lb (and cables, which usually have finer increments, at 5 lb too).
    // Only a true selectorized/plate machine stack (leg press, hack squat, plate-loaded row) jumps
    // coarse and can't be split. Cables usually have add-on weights or finer pins, and any barbell
    // takes a pair of 2.5 lb plates — so all barbell & freeweight work, all cables, and machine
    // ISOLATIONS step the fine 5 lb / 2.5 kg. That keeps progression sustainable (a 10 lb jump on a
    // squat or a pulldown is a big leap most weeks); the coarse 10 lb / 5 kg is reserved for compound
    // machine stacks where it's genuinely the smallest notch (and any machine can be set exactly via
    // its per-exercise increment).
    const eqCable = eq.includes("cable") && !isBarLike(eq) && !eq.includes("dumbbell");
    const coarseMachine = machineJump && !eqCable && ex.type !== "isolation"; // compound plate/pin stack
    if (unit === "lb") {
        if (fixedJump)
            return lower ? 10 : 5; // DB/KB pairs move in ~5 lb-per-hand notches
        if (coarseMachine)
            return 10; // leg press / hack squat / plate-loaded stack
        return 5; // barbell, freeweight, cables, machine isolations
    }
    if (fixedJump)
        return lower ? 4 : 2;
    if (coarseMachine)
        return 5;
    return 2.5;
}

function trainingMaxForUnit(program, exerciseId, unit) {
    return convertHistoryLoad(program.trainingMax?.[exerciseId], program.trainingMaxUnit || program.config?.unit || unit, unit);
}

function withTrainingMax(program, exerciseId, value, unit) {
    const storageUnit = program.trainingMaxUnit || program.config?.unit || unit;
    const load = convertHistoryLoad(value, unit, storageUnit);
    return { ...program, trainingMaxUnit: storageUnit,
        trainingMax: { ...(program.trainingMax || {}), [exerciseId]: load > 0 ? load : 0 }, edited: true };
}

function pctSetsFor(scheme, tm, weekIndex, weeksTotal, unit, ex, dayType, tier = "t1") {
    return percentageProtocolFor({ scheme, tm, weekIndex, weeksTotal, dayType, tier,
        snapLoad: value => loadableAtOrBelow(ex, value, unit) });
}

function percentagePlanFor(program, day, ex, slot, weekIndex, unit, history = []) {
    const tier = tierOf(day, slot);
    if (!tier || !program.config?.percentScheme) return null;
    const cell = computeCell(program, day, ex.id, slot, weekIndex);
    if (cell.ownership?.reps === 'user' || cell.ownership?.rir === 'user') return null;
    const sourceUnit = program.trainingMaxUnit || program.config?.unit || unit;
    const tm = trainingMaxForUnit(program, ex.id, unit);
    let plan;
    if (program.config.percentScheme === 'gzclp') {
        const source = gzPlanFor(program, day, ex, slot, sourceUnit, history);
        plan = source ? { ...source, sets: source.sets.map(t => ({ ...t,
            weight: loadableAtOrBelow(ex, convertHistoryLoad(t.weight, sourceUnit, unit), unit) })) } : null;
    } else plan = pctSetsFor(program.config.percentScheme, tm, weekIndex, weeksOf(program), unit, ex, day.type, tier);
    if (!plan?.sets?.length) return null;
    if (program.config.percentScheme === 'gzclp' && weekIndex > weeksOf(program))
        plan = { ...plan, label: 'Deload · light', sets: plan.sets.map(t => ({ ...t,
            weight: loadableAtOrBelow(ex, t.weight * .6, unit), reps: String(t.reps).replace('+',''), amrap: false })) };
    return adaptPercentageSetBudget(plan, Number(cell.sets));
}

function tierOf(day, slot) {
    if (!day)
        return null;
    if (slot === day.primaryIndex)
        return "t1";
    if (day.t2Index != null && slot === day.t2Index)
        return "t2";
    return null;
}

function groupPctSets(sets) {
    const g = [];
    (sets || []).forEach(s => {
        const last = g[g.length - 1];
        if (last && last.weight === s.weight && last.reps === s.reps)
            last.count++;
        else
            g.push({ weight: s.weight, reps: s.reps, count: 1 });
    });
    return g;
}

const GZ_T1_STAGES = [
    { label: "T1 · 5×3+", n: 5, reps: 3 },
    { label: "T1 · 6×2+", n: 6, reps: 2 },
    { label: "T1 · 10×1+", n: 10, reps: 1 },
];

const GZ_T2_STAGES = [
    { label: "T2 · 3×10", n: 3, reps: 10 },
    { label: "T2 · 3×8", n: 3, reps: 8 },
    { label: "T2 · 3×6", n: 3, reps: 6 },
];

function gzTierOf(program, day, slot) {
    if (program?.config?.percentScheme !== "gzclp" || !day)
        return null;
    if (slot === day.primaryIndex)
        return "t1";
    if (day.t2Index != null && slot === day.t2Index)
        return "t2";
    return null;
}

function gzWorkWeight(program, exId, tier, unit, ex) {
    const step = loadStep(ex, unit);
    const t1 = historyNumber(program.trainingMax?.[exId]) ?? 0;
    if (tier === "t1")
        return t1;
    const t2 = historyNumber(program.gz?.[exId]?.t2w) ?? 0;
    if (t2 > 0)
        return t2;
    return t1 > 0 ? roundTo(t1 * 0.66, step) : 0; // a sensible T2 start (~10RM ≈ ⅔ of the 3RM weight)
}

function gzPlanFor(program, day, ex, slot, unit, history = []) {
    const tier = gzTierOf(program, day, slot);
    if (!tier)
        return null;
    const w = gzWorkWeight(program, ex.id, tier, unit, ex);
    if (!(w > 0))
        return null;
    const defs = tier === "t1" ? GZ_T1_STAGES : GZ_T2_STAGES;
    const rawStage = Number(program.gzStage?.[ex.id + ':' + tier]);
    const initialStage = Number.isInteger(rawStage) ? Math.max(0, Math.min(rawStage, defs.length - 1)) : 0;
    const replay = deriveTieredLinearState({ programId: program.id, exerciseId: ex.id, tier,
        initialStage, initialLoad: w, entries: history, unit,
        nextLoad: weight => loadableAbove(ex, weight, unit),
        resetLoad: weight => loadableAtOrBelow(ex, weight * .85, unit) });
    const stage = replay.stage, st = defs[stage];
    const sets = [];
    for (let i = 0; i < st.n; i++) {
        const amrap = i === st.n - 1; // last set is AMRAP — it drives the progression
        sets.push({ weight: replay.weight, reps: amrap ? st.reps + "+" : String(st.reps), amrap, pct: 100 });
    }
    return { label: st.label, sets, tier, stage };
}

function nextTMEvidence(program, history, id) {
    const last = progressionHistoryForProgram(program, history).find(h => h.perf?.[id]);
    const sets = last?.perf?.[id]?.sets;
    const amrap = Array.isArray(sets) && sets.length
        ? (sets.find(s => s.amrap) || sets[sets.length - 1]) : null;
    const reps = amrap ? (parseInt(amrap.r) || 0) : (last ? parseInt(last.perf[id].reps) || 0 : null);
    const factor = reps == null ? 1 : reps === 0 ? 0 : reps >= 8 ? 2 : reps <= 2 ? 0.5 : 1;
    const note = reps == null ? "no AMRAP logged — standard jump" : reps === 0 ? "missed — holding"
        : reps >= 8 ? `${reps} reps — big jump` : reps <= 2 ? `${reps} reps — small jump` : `${reps} reps — standard jump`;
    return { reps, factor, note };
}

function projectNextTM(program, history, unit) {
    unit = program.trainingMaxUnit || program.config?.unit || unit;
    const out = {};
    const tm = program.trainingMax || {};
    Object.keys(tm).forEach(id => {
        const ex = EX_BY_ID[id], current = historyNumber(tm[id]);
        if (!ex || current === null || !(current > 0)) {
            out[id] = current ?? 0;
            return;
        }
        const lower = lowerBodyLift(ex);
        // Floor the cycle bump at this lift's smallest real step so a custom coarse increment (e.g. a
        // machine-based main lift) doesn't get rounded back to the current TM, stalling progression.
        const inc = Math.max(unit === "lb" ? (lower ? 10 : 5) : (lower ? 5 : 2.5), loadStep(ex, unit));
        const { factor } = nextTMEvidence(program, history, id);
        out[id] = factor > 0 ? roundTo(current + inc * factor, loadStep(ex, unit)) : current;
    });
    return out;
}

function summarizeSets(sets, ex, unit) {
    /* SUB-SETS ARE NOT WORKING SETS. Drop sets and myo mini-sets are extensions performed at or below
       the working load, deliberately short — a 4-rep mini or a stripped-load rep-out. Folding them in
       corrupted the summary in both branches: at a shared load the straight-set branch takes the MIN
       reps at the top weight, so one 4-rep mini reported the whole lift as 4 reps; at mixed loads the
       ramp branch averaged the stripped weight in and under-prescribed. The summary is what next
       session's opening load is built from, so it must describe the work, not the extensions. */
    const done = sets.filter(s => s.done && !s.warm && !s.sub && parseInt(s.reps) > 0);
    if (!done.length)
        return null;
    const wv = s => { const w = parseFloat(s.weight); return isNaN(w) ? 0 : w; };
    // If any working set carried real load it's a loaded lift — summarise from the loaded sets only.
    // Otherwise it's bodyweight (all 0/blank) or assisted (negative = machine/band help): keep those
    // sets, and pick the "best" as the one closest to bodyweight — heaviest load, or LEAST assistance.
    const hasPos = done.some(s => parseFloat(s.weight) > 0);
    const pool = hasPos ? done.filter(s => parseFloat(s.weight) > 0) : done;
    const weights = pool.map(wv);
    const maxW = Math.max(...weights);
    // Straight sets (every working set at the same load): summarise from the top tier exactly as before.
    if (weights.every(w => w === weights[0])) {
        const atTop = pool.filter(s => wv(s) === maxW);
        const reps = Math.min(...atTop.map(s => parseInt(s.reps)));
        return { weight: maxW, reps };
    }
    // Ramped / varying loads (e.g. 125→135→145→155): a single heaviest set isn't a load you actually
    // held across the session, so anchoring next session on it over-prescribes. Summarise from the
    // AVERAGE working load and the average reps instead — a weight you can realistically repeat across
    // all sets. (Per-set e1RM and PRs still come from the raw `sets` array, so a top-set PR isn't lost.)
    // Round the average onto the exercise's REAL increment (pin-stack notch / plate pair), not a flat
    // 0.5 — a raw mean like 36.5 on a 5 lb machine is a weight that doesn't exist, and it leaked into
    // "hold the same load" suggestions and the "Last" display as an impossible setting.
    const stepW = ex ? loadStep(ex, unit) : 0.5;
    const avgW = roundTo(weights.reduce((a, b) => a + b, 0) / weights.length, stepW);
    const reps = Math.round(pool.reduce((a, s) => a + parseInt(s.reps), 0) / pool.length);
    return { weight: avgW, reps };
}

const MOBILITY = {
    quads: ["Bodyweight squats × 15", "Walking lunges × 10/side", "Ankle rocks × 10/side"],
    hamstrings: ["Leg swings front-to-back × 10/side", "Bodyweight hip hinges × 12"],
    glutes: ["Glute bridges × 15", "Lateral band walks × 10/side"],
    upper_back: ["Cat–cow × 8", "Band pull-aparts × 15", "Scapular pull-ups / dead hang × 20s"],
    lower_back: ["Cat–cow × 8", "Bird-dog × 8/side", "Bodyweight hip hinges × 12"],
    lats: ["Dead hang × 20–30s", "Band lat pulldowns × 15"],
    chest: ["Band chest opener × 10", "Scapular push-ups × 10", "Push-ups × 8"],
    shoulders: ["Shoulder dislocates (band/PVC) × 10", "Arm circles × 10 each way", "Band external rotations × 12"],
    biceps: ["Light band curls × 15"],
    triceps: ["Band press-downs × 15", "Elbow circles × 10"],
    core: ["Dead bug × 8/side", "Cat–cow × 8"],
    calves: ["Ankle rocks × 12", "Calf raises × 15"],
    traps: ["Shrug rolls × 10", "Band pull-aparts × 15"],
    forearms: ["Wrist circles × 10", "Wrist flexor/extensor stretch × 20s"]
};

function warmupRoutine(day) {
    if (!day || !day.exercises || !day.exercises.length)
        return null;
    const order = [day.primaryIndex, ...day.exercises.map((_, i) => i).filter(i => i !== day.primaryIndex)];
    const parts = [];
    order.forEach(i => { const ex = EX_BY_ID[day.exercises[i]]; if (ex && !parts.includes(ex.part))
        parts.push(ex.part); });
    const moves = ["3–5 min easy cardio to raise your core temperature"];
    parts.slice(0, 3).forEach(p => (MOBILITY[p] || []).forEach(m => { if (!moves.includes(m))
        moves.push(m); }));
    const out = moves.slice(0, 6);
    out.push("Then ramp with 1–2 light sets on your first lift before working weight");
    return out;
}

function warmupCount(ex, isPrimary, workingWeight = 0, unit = "kg") {
    const comp = ex.type === "compound";
    const heavy = ex.rep[0] <= 6; // low-rep, heavily loaded
    let n = (isPrimary && comp) ? (heavy ? 3 : 2) : comp ? (heavy ? 2 : 1) : 1;
    /* ---- Depth must follow the LOAD, not just the slot ------------------------------------------
     *
     * The rules above key off the exercise's default rep range and whether it happens to be the day's
     * primary. Neither is the thing that makes a warm-up necessary. A back squat sitting in slot two
     * scored 2 warm-ups no matter the weight, so at 495 lb the ramp ran 250 → 345 → 495: a 150 lb,
     * 43% jump straight into the top set, on the most axially loaded movement there is. The same lift
     * at 135 lb got the identical ramp, which is over-warming a light day and under-warming a heavy one
     * with one rule.
     *
     * So scale with how far above the empty bar the working set actually is. A bar-plus-a-plate squat
     * needs one or two feeler sets; a triple-bodyweight squat needs to be approached. Ratios are to the
     * bar because that is the floor you must pass through anyway, and they only ever ADD steps — this
     * can shorten nothing, so no light day gets a longer ramp than before. Capped at 4 by warmupPlan;
     * useless steps are pruned downstream by warmupSets. */
    if (comp && workingWeight > 0) {
        const bar = BARS[barFor(ex)]?.[unit] || 0;
        if (bar > 0) {
            const mult = workingWeight / bar;
            if (mult >= 3)
                n = Math.max(n, 3); // ~135+ on a 45lb bar: needs a real ramp
            if (mult >= 5)
                n = Math.max(n, 4); // ~225+: approach it in four
        }
        else {
            /* Machines and cables have no bar to measure against, so use the lift's own load step as the
               yardstick — 20+ increments above nothing is a heavy machine press however it is loaded. */
            const step = loadStep(ex, unit) || 1;
            if (workingWeight / step >= 20)
                n = Math.max(n, 3);
        }
    }
    return n;
}

const WARMUP_PYRAMIDS = {
    1: { pct: [0.60], reps: [8] },
    2: { pct: [0.50, 0.70], reps: [8, 5] },
    3: { pct: [0.45, 0.65, 0.85], reps: [8, 5, 3] },
    4: { pct: [0.45, 0.60, 0.75, 0.85], reps: [8, 5, 4, 2] }
};

function warmupPlan(ex, isPrimary, workingWeight = 0, unit = "kg") {
    const n = clamp(warmupCount(ex, isPrimary, workingWeight, unit), 0, 4);
    return n >= 1 ? WARMUP_PYRAMIDS[n] : null;
}

function warmupSets(ex, isPrimary, workingWeight, unit) {
    const plan = warmupPlan(ex, isPrimary, workingWeight, unit);
    if (!plan || !(workingWeight > 0))
        return [];
    const step = loadStep(ex, unit);
    const bar = BARS[barFor(ex)]?.[unit] || 0;
    // Nothing to ramp if the working set is at/under the bar, or only a step or two above it. For
    // isolations the bar floor is usually 0 (cables/machines/dumbbells), so require a few increments
    // of load before a feeler set is worthwhile — a 60% warm-up of a light isolation isn't useful.
    const minWork = ex.type === "isolation" ? bar + step * 3 : bar + step;
    if (workingWeight <= minWork)
        return [];
    const out = [];
    const seen = new Set();
    plan.pct.forEach((p, i) => {
        const w = roundTo(workingWeight * p, step);
        if (w <= bar)
            return; // can't load below the empty bar
        if (w >= workingWeight)
            return; // not a warm-up if it meets/exceeds the work weight
        if (seen.has(w))
            return; // coarse rounding can collapse two steps onto one load
        seen.add(w);
        out.push({ weight: String(w), reps: String(plan.reps[i]), warm: true, done: false, target: { w: String(w), reps: String(plan.reps[i]) } });
    });
    return out;
}

const BARS = { barbell: { kg: 20, lb: 45 }, women: { kg: 15, lb: 35 }, ezbar: { kg: 10, lb: 25 }, smith: { kg: 15, lb: 35 }, trap: { kg: 25, lb: 55 }, ssb: { kg: 30, lb: 65 } };

const BAR_LABEL = { barbell: "Barbell", women: "Women's", ezbar: "EZ bar", smith: "Smith", trap: "Trap bar", ssb: "SSB" };

const PLATES = { kg: [25, 20, 15, 10, 5, 2.5, 1.25], lb: [45, 35, 25, 10, 5, 2.5] };

const ALL_PLATES = { kg: [25, 20, 15, 10, 5, 2.5, 1.25, 0.5, 0.25], lb: [45, 35, 25, 10, 5, 2.5, 1.25, 1, 0.5] };

let AVAIL_PLATES = { kg: null, lb: null };

function setAvailPlates(p) { AVAIL_PLATES = p || { kg: null, lb: null }; }

function barFor(ex) {
    if (ex.equip.includes("barbell"))
        return "barbell";
    if (ex.equip.includes("trapbar"))
        return "trap";
    if (ex.equip.includes("safetybar"))
        return "ssb";
    if (ex.equip.includes("ezbar"))
        return "ezbar";
    if (ex.equip.includes("smith"))
        return "smith";
    if (ex.equip.includes("landmine"))
        return "barbell"; // a landmine IS a straight bar, one end pinned
    return null;
}

function platesPerSide(total, bar, unit) {
    const barW = BARS[bar]?.[unit] ?? 0;
    let perSide = (total - barW) / 2;
    if (!Number.isFinite(perSide) || perSide > 2000)
        return { barW, plates: [], leftover: 0 }; // NaN/Infinity/absurd input (corrupt log, cleared field) must not reach the greedy loop
    if (!(perSide > 0))
        return { barW, plates: [], leftover: 0 };
    const list = (((AVAIL_PLATES[unit] && AVAIL_PLATES[unit].length) ? [...AVAIL_PLATES[unit]].sort((a, b) => b - a) : PLATES[unit]) || []).filter(p => Number.isFinite(p) && p > 0); // a 0/negative/NaN plate (possible via a corrupt import) would spin the greedy loop forever; unknown unit → empty list, not a throw
    // Greedy largest-first — the conventional loading order lifters expect, and provably exact for the
    // canonical default sets (every denomination is a multiple of the smallest).
    const gPlates = [];
    let rem = perSide;
    for (const p of list) {
        while (rem >= p - 1e-6) {
            gPlates.push(p);
            rem -= p;
        }
    }
    const gLeft = Math.round(rem * 100) / 100;
    if (gLeft <= 1e-6)
        return { barW, plates: gPlates, leftover: 0 };
    // Greedy stranded a remainder. With a CUSTOM (non-canonical) plate set an exact combination may
    // still exist that greedy can't see (e.g. [25,20] can't make 45 greedily but 25+20 does). Solve it
    // exactly via bounded DP on a 0.25-unit grid (all plate denominations are whole multiples of it),
    // minimizing plate count. Only override greedy if the solver actually gets CLOSER to the target —
    // so canonical sets and genuinely-unreachable fractions keep the familiar greedy display.
    const grid = 0.25;
    const T = Math.floor(perSide / grid + 1e-9); // FLOOR, not round: the solver must never target above the true per-side weight, or it "solves" by overloading the bar (negative leftover)
    // Denominations and their grid-units MUST be filtered together: `pick` stores indices into this
    // array, and the reconstruction reads the plate from the same index. Filtering only the units (as an
    // earlier version did) shifts every DP solution onto the wrong — larger — denominations whenever any
    // plate exceeds the target (e.g. loading a 25 where a 1.25 was solved).
    const denoms = [...new Set(list)].map(p => ({ p, u: Math.round(p / grid) })).filter(d => d.u > 0 && d.u <= T);
    if (denoms.length && T <= 4000) { // cap guards against a corrupted/absurd load blowing up the DP array
        const cnt = new Array(T + 1).fill(Infinity);
        cnt[0] = 0;
        const pick = new Array(T + 1).fill(-1);
        for (let s = 1; s <= T; s++) {
            for (let i = 0; i < denoms.length; i++) {
                const u = denoms[i].u;
                if (u <= s && cnt[s - u] + 1 < cnt[s]) {
                    cnt[s] = cnt[s - u] + 1;
                    pick[s] = i;
                }
            }
        }
        let s = T;
        while (s > 0 && cnt[s] === Infinity)
            s--;
        const gSum = perSide - gLeft; // weight greedy actually loaded
        if (s * grid > gSum + 1e-6) { // exact solver got closer → use it
            const plates = [];
            let cur = s;
            while (cur > 0) {
                const i = pick[cur];
                plates.push(denoms[i].p);
                cur -= denoms[i].u;
            }
            plates.sort((a, b) => b - a);
            return { barW, plates, leftover: Math.round((perSide - s * grid) * 100) / 100 };
        }
    }
    return { barW, plates: gPlates, leftover: gLeft };
}

function linearInc(ex, unit) {
    const lower = lowerBodyLift(ex);
    return unit === "lb" ? (lower ? 10 : 5) : (lower ? 5 : 2.5);
}

const PROG_STYLES = {
    auto: "Auto",
    double: "Double progression",
    dynamic: "Dynamic double",
    ladder: "Rep ladder",
    linear: "Linear LP",
    wave: "Wave loading",
    e1rm: "e1RM autoregulation"
};

function styleFor(program, id) {
    const s = program?.progStyle?.[id];
    if (!s)
        return "auto";
    /* ⚠ ENGINE-WRITTEN IS NOT LIFTER-CHOSEN. Next-engine programs used to have the engine's week-1 style written here, in the
       same map as a lifter's explicit choice, so resolveStyle treated it as one: the per-week block schedule never reached
       the workout and v661's adaptive rules never ran. New programs no longer write it (app-shell-adapter). For programs
       saved before that, an entry the lifter did NOT set (progStyleLifter) that equals the engine's own week-1 style for
       this exercise is engine-written, so it reads as auto. Anything the lifter set always wins. */
    if (program?.engineSource === "pursuit-next" && !program?.progStyleLifter?.[id] && engineWeekOneStyles(program, id).has(s))
        return "auto";
    return s;
}

function engineWeekOneStyles(program, id) {
    const out = new Set();
    for (const d of program?.days || [])
        (d.exercises || []).forEach((x, slot) => {
            if (x !== id)
                return;
            const st = program?.nextWeekPrescriptions?.[`${d.id}:${slot}`]?.[1]?.progressionStyle;
            if (st && st !== "auto")
                out.add(st);
        });
    return out;
}

function resolveStyle(program, ex, isPrimary, weekIndex, perf, history, dayId) {
    if (program?.custom === true && program?.engineSource !== "pursuit-next") {
        const day = program.days?.find(d => d.id === dayId), slot = day?.exercises?.indexOf(ex?.id);
        const cell = slot >= 0 ? computeCell(program, day, ex.id, slot, weekIndex) : null;
        const requested = program.progStyle?.[ex?.id] ?? cell?.progressionStyle ?? "auto";
        const nextEx = NEXT_EXERCISE_MAP.get(nextExerciseIdForShellExercise(ex)) || {
            flags: { compound: ex?.type === "compound", barbell: isBarLike(ex?.equip) }, equipment: ex?.equip || [] };
        return customProgramProgressionStyle(nextEx, cell, requested, program.config);
    }
    const explicit = styleFor(program, ex?.id);
    return explicit === "auto"
        ? autoStyleFor(program, ex, isPrimary, weekIndex, perf, history, dayId)
        : explicit;
}

function progressionHistoryForProgram(program, history) {
    const rows = (Array.isArray(history) ? history : []).filter(validHistoryDate);
    if (!rows.length)
        return [];
    const programId = program?.id;
    if (programId != null) {
        const own = rows.filter(h => h?.programId != null && String(h.programId) === String(programId));
        if (own.length)
            return own.slice().sort((a, b) => Number(b.date) - Number(a.date));
        // Once any tagged program history exists, unowned/other-program rows are ambiguous for adaptive
        // stall/fatigue decisions. A new program must establish its own comparable evidence.
        if (rows.some(h => h?.programId != null))
            return [];
    }
    // Pure legacy history predating program ownership remains usable and is made order-independent.
    return rows.slice().sort((a, b) => Number(b.date) - Number(a.date));
}

function plateauSessions(history, exId, dayId, limit = 6, program = null) {
    const scoped = program ? progressionHistoryForProgram(program, history) : (Array.isArray(history) ? history : []);
    const withEx = scoped.filter(h => historyNumber(h?.perf?.[exId]?.weight) > 0);
    if (!dayId)
        return withEx.slice(0, limit);
    const sameDay = withEx.filter(h => h?.dayId != null && String(h.dayId) === String(dayId));
    // If this lift has only ever been trained on one identified day, scoping changed nothing — use
    // everything so a regenerated day ID does not silently lose otherwise unambiguous legacy history.
    const days = new Set(withEx.map(h => h.dayId == null ? null : String(h.dayId)).filter(Boolean));
    return (days.size <= 1 ? withEx : sameDay).slice(0, limit);
}

const STALL_WINDOW = 8;

function stallCountFor(perf, ex, history, dayId, program = null) {
    if (!perf || !ex || !history)
        return 0;
    const p = perf[ex.id];
    if (!p?.weight)
        return 0;
    // history is newest-first already — take the front directly. (An earlier .reverse() here grabbed
    // the OLDEST sessions, which inverted prIdx and made progressing lifters look stalled.)
    const sessions = plateauSessions(history, ex.id, dayId, STALL_WINDOW, program);
    if (sessions.length < 3)
        return 0;
    const e1rms = sessions.map(h => {
        const hp = h.perf[ex.id];
        const sets = hp.sets?.length ? hp.sets : [{ w: hp.weight, r: hp.reps }];
        return Math.max(...sets.filter(s => s.r > 0).map(s => e1rmRIR(s.w, s.r, ASSUMED_RIR)));
    }).filter(v => v > 0);
    if (e1rms.length < 3)
        return 0;
    /* Sessions since the last STRICT PR. The old form — indexOf(max) — has a tie-breaking hole that
       hid the most common plateau there is: on a DEAD-FLAT run every e1RM is equal, indexOf returns
       the first of the ties (the newest session), and that reads as "PR today, zero stall". The
       detector could only see a plateau if you had got WEAKER; merely going nowhere was invisible.
       Walk from the newest instead: a session is the PR only if it strictly beats everything older. */
    let prIdx = e1rms.length - 1;
    for (let i = 0; i < e1rms.length; i++) {
        const olderMax = e1rms.length > i + 1 ? Math.max(...e1rms.slice(i + 1)) : 0;
        if (e1rms[i] > olderMax + 1e-6) {
            prIdx = i;
            break;
        }
    }
    return prIdx;
}

const STALL_ENGAGE = 4;

const E1RM_HOLD = 3;

function styleOverride(program, ex, isPrimary, weekIndex, perf, history, dayId) {
    if (!ex || styleFor(program, ex.id) !== "auto")
        return null; // an explicit choice is not ours to override
    if (program?.config?.percentScheme)
        return null; // percent schemes own their periodization
    const exp = program?.config?.experience || "intermediate";
    if (exp === "none" || exp === "beginner")
        return null; // beginners are on linear by structure
    const scopedHistory = progressionHistoryForProgram(program, history);
    const stall = stallCountFor(perf, ex, scopedHistory, dayId);
    if (stall >= STALL_ENGAGE) {
        if (stall < STALL_ENGAGE + E1RM_HOLD) {
            const left = STALL_ENGAGE + E1RM_HOLD - stall;
            return {
                style: "e1rm", kind: "plateau", stall,
                why: `Recalibrating — ${stall} sessions without a new best`,
                clears: `Back to your program's progression on a new best, or after ${left} more session${left === 1 ? "" : "s"}`
            };
        }
        return null; // held its window and did not break the stall — hand the lift back
    }
    const fatigued = scopedHistory.length ? (() => {
        const rec = muscleRecovery(scopedHistory);
        const pr = rec.find(r => r.part === ex.part);
        return !!(pr && pr.readiness < 55);
    })() : false;
    if (fatigued) {
        return {
            style: ex.type === "compound" ? "e1rm" : "double", kind: "fatigue", stall,
            /* The text used to say "load eased to match", but no suggestion path eases the load for this readiness score — the engine's
               suggestion (engine programs) and the lifter's history (custom programs) are not adjusted by it. Say what is true and what to
               do, consistent with the coach's "back off today" advice. */
            why: `${PART_LABEL[ex.part] || ex.part} is still recovering — keep the reps clean, and take a little weight off if the warm-up feels heavy`,
            clears: "Back to your program's progression once the muscle has recovered"
        };
    }
    return null;
}

function linearStalled(perf, ex, history, program = null) {
    if (!perf || !ex)
        return false;
    const p = perf[ex.id];
    if (!p?.weight || !p.reps)
        return false;
    const scopedHistory = program ? progressionHistoryForProgram(program, history) : (Array.isArray(history) ? history : []);
    if (scopedHistory.length < 3)
        return false;
    const recent = scopedHistory.filter(h => historyNumber(h?.perf?.[ex.id]?.weight) > 0).slice(0, 4);
    if (recent.length < 3)
        return false;
    const weights = recent.map(h => h.perf[ex.id].weight);
    return weights[0] === weights[1] && weights[1] === weights[2];
}

function nextEngineStyleFor(program, ex, dayId, weekIndex) {
    if (program?.engineSource !== "pursuit-next" || !ex)
        return null;
    const days = program.days || [];
    const order = dayId ? days.filter(d => d.id === dayId).concat(days.filter(d => d.id !== dayId)) : days;
    for (const d of order) {
        const slot = (d.exercises || []).indexOf(ex.id);
        if (slot < 0)
            continue;
        const byWeek = program.nextWeekPrescriptions?.[`${d.id}:${slot}`];
        const st = (byWeek?.[weekIndex] || byWeek?.[1])?.progressionStyle;
        if (st && st !== "auto")
            return st;
    }
    return null;
}

function autoStyleDetail(program, ex, isPrimary, weekIndex, perf = null, history = null, dayId = null) {
    const R = (style, why, at = null) => ({ style, why, at });
    const goal = program?.config?.goal || "hypertrophy";
    const exp = program?.config?.experience || "intermediate";
    const weeks = weeksOf(program);
    const phase = blockPhase(program, weekIndex, 0.5); // honors cycle-block phaseWindow
    const comp = ex?.type === "compound";
    const strength = goal === "strength" || (goal === "both" && isPrimary);
    /* ⚠ NEXT-ENGINE PROGRAMS: the engine owns the BASE style — per slot and per week, i.e. the block schedule — and v661's
       runtime rules apply on top exactly as they do for legacy programs: a percent-scheme lift stays e1RM (structural), a
       stalled beginner graduates from linear, then plateau override -> soft stall nudge -> fatigue override. */
    const nextBase = nextEngineStyleFor(program, ex, dayId, weekIndex);
    if (nextBase) {
        if (program?.config?.percentScheme && nextBase === "e1rm")
            return R("e1rm", "percent-scheme lift — load is matched to %TM, not to a rep range");
        if (nextBase === "linear")
            return linearStalled(perf, ex, history, program)
                ? R("double", "beginner compound whose linear progression stalled — same weight for 3 sessions, so it graduates to double progression")
                : R("linear", "beginner compound — linear progression is the simplest thing that still works");
        const stallN = stallCountFor(perf, ex, history, dayId, program);
        const ovN = styleOverride(program, ex, isPrimary, weekIndex, perf, history, dayId);
        if (ovN && ovN.kind === "plateau")
            return R(ovN.style, `plateau override — ${ovN.why || "a hard plateau was detected"}${ovN.clears ? `. ${ovN.clears}` : ""}`, { stallSessions: stallN, override: ovN });
        if (stallN >= 2 && !strength)
            return R("double", `stalled ${stallN} sessions and the goal is not strength — nudged to the simpler style until the stall clears`, { stallSessions: stallN });
        if (ovN)
            return R(ovN.style, `${ovN.kind} override — ${ovN.why || "an adaptive override is active"}${ovN.clears ? `. ${ovN.clears}` : ""}`, { stallSessions: stallN, override: ovN });
        return R(nextBase, `Planned progression — ${nextBase} for this lift in week ${weekIndex} of the block`);
    }
    // ── 1. Structural: percent-scheme programs own their own periodization ──────
    if (program?.config?.percentScheme)
        return R("e1rm", "percent-scheme program — load is matched to %TM, not to a rep range");
    // ── 2. Beginners: linear is maximally effective and simple ────────────────────
    if (exp === "none" || exp === "beginner") {
        if (comp) {
            // Check for LP stall: 3+ consecutive sessions without hitting the rep target → graduate
            const lpStall = linearStalled(perf, ex, history, program);
            if (lpStall)
                return R("double", "beginner compound whose linear progression stalled — same weight for 3 sessions, so it graduates to double progression");
            return R("linear", `beginner (${exp}) compound — linear progression is the simplest thing that still works`);
        }
        return R("double", `beginner (${exp}) isolation — double progression`);
    }
    // ── 3/4. Adaptive overrides — plateau, then fatigue ───────────────────────────
    /* Both used to be decided HERE, in their own inline branches, while suggestWeightUncapped
       re-derived the same two conditions separately in order to caption them. Two deciders for one
       decision. They now come from styleOverride, which owns the conditions, the reason strings AND
       the exit — so a style can never be adopted without an answer to "what ends this?".
       PRECEDENCE IS PRESERVED EXACTLY: hard plateau, then the soft-plateau nudge, then fatigue. The
       soft nudge stays inline because it is not an override with a hold window; it swaps one
       rep-range style for a simpler one and reverses itself the moment the stall clears. */
    const stallSessions = stallCountFor(perf, ex, history, dayId, program);
    const ov = styleOverride(program, ex, isPrimary, weekIndex, perf, history, dayId);
    if (ov && ov.kind === "plateau")
        return R(ov.style, `plateau override — ${ov.why || "a hard plateau was detected"}${ov.clears ? `. ${ov.clears}` : ""}`, { stallSessions, override: ov });
    if (stallSessions >= 2 && !strength)
        return R("double", `stalled ${stallSessions} sessions and the goal is not strength — nudged to the simpler style until the stall clears`, { stallSessions });
    if (ov)
        return R(ov.style, `${ov.kind} override — ${ov.why || "an adaptive override is active"}${ov.clears ? `. ${ov.clears}` : ""}`, { stallSessions, override: ov });
    // ── 5. Base style by goal × experience × phase ────────────────────────────────
    // Per-set DDP ("dynamic") lets each set settle on its own load, which is ideal on a machine,
    // cable, or dumbbell (a pin or a DB swap is instant) but a chore on a loaded barbell — you'd strip
    // plates between sets of the same lift. So for auto, a BARBELL compound's accumulation work uses
    // DOUBLE PROGRESSION instead: one weight, hold it and build toward the top of the rep range, then
    // an e1RM-accurate load jump once the range is sustained. Crucially it reads the LAST (most
    // fatigued) set to decide the load, so it fits heavy compounds where reps naturally fall across
    // sets (8→7→6→5) — unlike a single shared "rep ladder" target, which anchors on one set and can
    // read as "do fewer reps than your best." Non-barbell keeps true per-set DDP. (The rep ladder
    // remains available as a manual choice — it suits machines/isolation, where reps hold across sets.)
    const barbell = isBarLike(ex?.equip);
    const accumStyle = (comp && barbell) ? "double" : "dynamic";
    if (strength && comp && isPrimary) {
        // Short blocks (≤4 weeks) don't have enough sessions to complete a meaningful
        // wave cycle (hi→mid→lo→load-up needs ≥3 sessions). Skip wave and go straight
        // from accumulation to e1RM intensity.
        if (weeks <= 4) {
            return phase >= 0.5
                ? R("e1rm", `strength primary compound, short block (${weeks} weeks) past halfway (phase ${phase.toFixed(2)}) — skip wave, go straight to e1RM`, { phase, weeks })
                : R(accumStyle, `strength primary compound, short block (${weeks} weeks), still accumulating (phase ${phase.toFixed(2)})`, { phase, weeks });
        }
        // Peak phase (>75% through block): e1RM precision-targets the load for intensity
        if (phase >= 0.75)
            return R("e1rm", `strength primary compound in the peak phase (${phase.toFixed(2)} >= 0.75) — e1RM precision-targets the load`, { phase });
        // Mid-block intensification: wave loading (hi→mid→lo reps = volume/medium/heavy)
        if (phase >= 0.35)
            return R("wave", `strength primary compound mid-block (phase ${phase.toFixed(2)}) — wave loading`, { phase });
        // Early accumulation: rep ladder (barbell) or per-set DDP (machine/DB) — build volume + reps
        return R(accumStyle, `strength primary compound early in the block (phase ${phase.toFixed(2)}) — accumulation${barbell ? ", on a barbell so double rather than per-set" : ""}`, { phase });
    }
    if (strength && comp && !isPrimary) {
        // Accessory compounds in strength programs: accumulation, switching to e1rm late
        return phase >= 0.65
            ? R("e1rm", `strength ACCESSORY compound late in the block (phase ${phase.toFixed(2)} >= 0.65) — switch to e1RM`, { phase })
            : R(accumStyle, `strength ACCESSORY compound, still accumulating (phase ${phase.toFixed(2)})`, { phase });
    }
    // Hypertrophy: compounds → per-set DDP (machine/DB) or rep ladder (barbell); isolations →
    //              double progression (simpler, sufficient for single-joint)
    if (!strength) {
        if (comp)
            return R(accumStyle, `hypertrophy compound${barbell ? " on a barbell — double progression, since stripping plates between sets is a chore" : " on a machine or dumbbell — per-set dynamic, where changing the load is instant"}`);
        return R("double", "hypertrophy isolation — double progression is simpler and sufficient for single-joint work");
    }
    // "Both" goal, isolation or non-primary
    if (comp)
        return R(accumStyle, `"both" goal, non-primary compound${barbell ? " on a barbell" : ""} — accumulation`);
    return R("double", '"both" goal, isolation — double progression');
}

function autoStyleFor(program, ex, isPrimary, weekIndex, perf = null, history = null, dayId = null) {
    return autoStyleDetail(program, ex, isPrimary, weekIndex, perf, history, dayId).style;
}

function explainPrescription(o) {
    const { program, day, ex, slot, perf, history, weekIndex } = o || {};
    if (!program || !day || !ex || program.engineSource !== "pursuit-next")
        return null;
    const cell = computeCell(program, day, ex.id, slot, weekIndex);
    if (!cell || cell.missing)
        return null;
    const range = cellRepRange(cell, program, ex, slot === day.primaryIndex);
    const sug = nextWorkoutSuggestionForShell(program, history || [], EXERCISES, day, slot, weekIndex);
    const last = perf && perf[ex.id] ? perf[ex.id] : null;
    const lastSets = Array.isArray(last?.sets) ? last.sets : [];
    const rirRows = lastSets.map((st, i) => ({
        set: i + 1,
        weight: st.w != null ? st.w : st.weight,
        reps: st.r != null ? st.r : st.reps,
        rir: st.rir != null ? st.rir : st.tr != null ? st.tr : "—",
        source: st.rir != null ? "you rated it" : st.tr != null ? "planned target" : "not reported"
    })).filter(r => r.weight != null || r.reps != null);
    const lastWork = [...lastSets].reverse().find(st => Number(st?.w ?? st?.weight) > 0 && Number(st?.r ?? st?.reps) > 0);
    const lastW = Number(last?.weight ?? lastWork?.w ?? lastWork?.weight);
    const lastR = Number(last?.reps ?? lastWork?.r ?? lastWork?.reps);
    return {
        lift: { id: ex.id, name: ex.name, part: ex.part, type: ex.type, equip: ex.equip, isPrimary: slot === day.primaryIndex, slot },
        context: {
            day: { id: day.id, label: day.label, type: day.type }, weekIndex, weeksTotal: weeksOf(program),
            phase: program?.nextEngine?.phase || null, goal: program?.config?.goal || null, deload: false,
            experience: program?.config?.experience, percentScheme: null
        },
        read: {
            lastPerformance: Number.isFinite(lastW) && Number.isFinite(lastR) ? { weight: lastW, reps: lastR, sets: rirRows.length || null } : null,
            perSet: rirRows.length ? rirRows : null,
            e1rm: Number.isFinite(lastW) && Number.isFinite(lastR) ? e1rm(lastW, lastR) : null,
            historyEntriesForThisDay: (history || []).filter(h => h?.programId === program.id && (h?.dayId === day.id || h?.dayLabel === day.label)).length,
            stallSessions: 0, plateauSessions: 0, readiness: null
        },
        decided: {
            repRange: range, targetRIR: effortValueLabel(cell.rir), styleSource: "Pursuit Iron",
            style: cell.progressionStyle || "auto", styleWhy: sug?.reason || "The saved week plan owns this prescription.",
            styleAt: null, override: null, sets: Number(cell.sets) || 0
        },
        load: sug ? {
            weight: sug.weight, direction: sug.dir, delta: sug.weight != null && Number.isFinite(lastW) ? sug.weight - lastW : null,
            reason: sug.reason || null, increment: null, snappedToRack: false, rack: null, cappedByGym: false,
            gymCeiling: null, swapTo: null
        } : { weight: null, reason: "No comparable completed workout yet; log this exposure and the next comparable session will use it for progression." },
        warmups: []
    };
}

function perfAfterDelete(history, perf, deletedId) {
    const gone = (history || []).find(h => h && h.id === deletedId);
    if (!gone || !gone.perf)
        return perf || {};
    const remaining = (history || [])
        .filter(h => h && h.id !== deletedId)
        .slice()
        .sort((a, b) => (b.date || 0) - (a.date || 0));
    const next = { ...(perf || {}) };
    Object.keys(gone.perf).forEach(exId => {
        const prior = remaining.find(h => h.perf && h.perf[exId]);
        if (prior)
            next[exId] = prior.perf[exId];
        else
            delete next[exId];
    });
    return next;
}

function parseRIRNum(r) {
    const bounds = effortBounds(r);
    return bounds ? (bounds[0] + bounds[1]) / 2 : 2;
}

function prescribedRIRof(set) {
    if (!set || !set.target)
        return null;
    if (set.target.failure)
        return 0;
    const t = set.target.rir;
    const n = typeof t === "number" ? t : parseRIRNum(t);
    return Number.isFinite(n) ? n : null;
}

const __slopeMemo = new WeakMap();

function personalRepSlope(history, ex) {
    if (!history || !ex || !ex.id)
        return 30;
    let byEx = __slopeMemo.get(history);
    if (!byEx) {
        byEx = new Map();
        __slopeMemo.set(history, byEx);
    }
    if (byEx.has(ex.id))
        return byEx.get(ex.id);
    const pairs = [];
    let seen = 0;
    for (const h of history) {
        if (seen >= 40)
            break; // recent sessions carry the current curve
        const p = h && h.perf && h.perf[ex.id];
        if (!p || !p.sets || p.sets.length < 2)
            continue;
        seen++;
        const work = setsOf(p).filter(t => isWorkSet(t) && t.w > 0 && t.r > 0);
        for (let i = 0; i < work.length; i++)
            for (let j = i + 1; j < work.length; j++) {
                const a = work[i], b = work[j];
                const qa = a.rir != null ? a.rir : 2, qb = b.rir != null ? b.rir : 2;
                if (Math.abs(a.w - b.w) < 0.05 * Math.max(a.w, b.w))
                    continue; // need a real load gap
                const sl = (b.w * (b.r + qb) - a.w * (a.r + qa)) / (a.w - b.w);
                if (isFinite(sl) && sl >= 12 && sl <= 60)
                    pairs.push(sl);
            }
    }
    let out = 30;
    if (pairs.length >= 6) {
        pairs.sort((x, y) => x - y);
        const m = Math.floor(pairs.length / 2);
        const med = pairs.length % 2 ? pairs[m] : (pairs[m - 1] + pairs[m]) / 2;
        const conf = Math.min(1, (pairs.length - 6) / 24);
        out = Math.max(22, Math.min(42, 30 + (med - 30) * conf));
    }
    byEx.set(ex.id, out);
    return out;
}

const MEM = { data: null };

const KEY = "wpb:v1";

const LIVE_KEY = "wpb:live";

const STORE_VERSION = 13;

const BW_LOG_CAP = 2000;

const lengthUnitFor = (weightUnit) => (weightUnit === "kg" ? "cm" : "in");

const asLengthUnit = (u, weightUnit) => (u === "in" || u === "cm" ? u : u === "kg" ? "cm" : u === "lb" ? "in" : lengthUnitFor(weightUnit));

const toLength = (v, from, to) => (from === to ? v : to === "cm" ? v * 2.54 : v / 2.54);

function normalizeMeasureLog(arr, to, weightUnit) {
    return (arr || [])
        .filter(e => e && e.date != null && Number(e.v) > 0)
        .map(e => { const from = asLengthUnit(e.unit, weightUnit); return { ...e, v: Math.round(toLength(Number(e.v), from, to) * 10) / 10, unit: to }; })
        .sort((a, b) => a.date - b.date);
}

const STORE_MIGRATIONS = {
    // 12 -> 13: completed workout history became editable in M164, so a same-id session can now have
    // two legitimate versions. Give history the same per-record conflict clock used by other mutable
    // syncable lists. Legacy records deliberately start at zero so any real M177+ edit wins.
    13: (d) => {
        if (!Array.isArray(d.history))
            return d;
        const history = d.history.map(x => (x && typeof x === "object" && x.updatedAt == null) ? { ...x, updatedAt: 0 } : x);
        return { ...d, history };
    },
    // 11 -> 12: selective-promotion safety state is release-scoped and starts fail-safe. M76 ships
    // with a zero-percent manifest, so upgrading cannot change a prescription.
    12: (d) => d.selectivePromotionRuntime ? d : { ...d, selectivePromotionRuntime: emptyRetiredRolloutData() },
    // 10 -> 11: controlled canary research is opt-in and local. Existing users must remain
    // unenrolled after upgrade; a missing field therefore migrates to an explicit disabled state.
    11: (d) => d.canaryResearch ? d : { ...d, canaryResearch: emptyRetiredTrialData() },
    // 9 -> 10: the typed `age` integer becomes a `birth` date. An age is only true for a year and the
    // app had no way to know it had gone stale, so a lifter's strength standards quietly drifted onto
    // the wrong age band and stayed there. The exact date can't be recovered from a number, so estimate
    // mid-year of the implied birth year (±6 months, inside ageFactor's 5–6 year bands) and mark it
    // `birthEst` so the UI can ask for the real one. `age` is left in place and kept in sync on save,
    // so an older build reading this store after a downgrade still finds the field it expects.
    10: (d) => {
        if (d.birth)
            return d;
        const b = birthFromAge(d.age, d.savedAt);
        return b ? { ...d, birth: b, birthEst: true } : d;
    },
    // 7 → 8: the single `equipDefault` list becomes a list of GYMS. Equipment belongs to a place, not
    // to a person, and a lifter with a garage rack and a gym membership had no way to say so.
    // The existing list is preserved as the ACTIVE gym so nobody's programs change on upgrade, and it
    // is named from its own contents rather than assumed — calling a full commercial kit "Home gym"
    // would be a silent lie. A second preset is added so the pair the app promises always exists.
    // `equipDefault` is deliberately left in place and kept in sync: an older build reading this store
    // after a downgrade still finds the field it expects.
    // 8 -> 9: "Selectorized Machines" split into specific apparatus. Grant every gym the fine ids its
    // coarse category already implied, so no user's available exercises change on upgrade. Granting is
    // conditional on the parent: a bands-only gym must not acquire a leg press.
    9: (d) => {
        const grow = (g) => {
            const eq = new Set(Array.isArray(g.equipment) ? g.equipment : []);
            Object.entries(LEGACY_EQUIP_IMPLIES).forEach(([parent, fine]) => { if (eq.has(parent))
                fine.forEach(f => eq.add(f)); });
            return { ...g, equipment: [...eq] };
        };
        /* Defensive: a store can arrive at v8 without gyms — a partial import, a merge from a device that
           never wrote them, a hand-edited backup. Rebuilding them here rather than assuming means the
           migration can never hand the app a gym-less store. normalizeGyms would paper over it on load,
           but a migration that silently drops the user's equipment is not something to leave to a later
           safety net. */
        const base = (Array.isArray(d.gyms) && d.gyms.length) ? d : STORE_MIGRATIONS[8]({ ...d, gyms: null });
        return { ...base, gyms: (Array.isArray(base.gyms) ? base.gyms : []).map(grow),
            equipDefault: Array.isArray(base.equipDefault) ? grow({ equipment: base.equipDefault }).equipment : base.equipDefault };
    },
    8: (d) => {
        if (Array.isArray(d.gyms) && d.gyms.length)
            return d;
        const eq = Array.isArray(d.equipDefault) && d.equipDefault.length ? d.equipDefault : [...ALL_EQUIP_IDS];
        const isFull = ALL_EQUIP_IDS.every(x => eq.includes(x));
        const mine = { id: "gym_1", name: isFull ? "Commercial gym" : "My gym", equipment: eq };
        const other = isFull
            ? { id: "gym_2", name: "Home gym", equipment: [...GYM_PRESETS[2].equipment] }
            : { id: "gym_2", name: "Commercial gym", equipment: [...ALL_EQUIP_IDS] };
        return { ...d, gyms: [mine, other], activeGymId: mine.id };
    },
    // 1 → 2: the legacy `lastWeights` map (weight only) becomes the per-exercise `perf` store
    // (weight + reps). Previously handled inline at hydration; centralized here so load AND import
    // both get it. No-op when `perf` already exists (everyone past the original change).
    2: (d) => {
        if (!d.perf && d.lastWeights) {
            const perf = {};
            Object.entries(d.lastWeights).forEach(([k, v]) => { perf[k] = { weight: v, reps: null }; });
            return { ...d, perf };
        }
        return d;
    },
    // 2 → 3: repair runaway auto-volume. Per-session autoregulation used to be merged into `slotBias`
    // and persisted, so set counts compounded every session (programs grew longer week over week).
    // Autoregulation now lives in a separate `autoBias` field rebuilt fresh each session. Reset each
    // auto-progression program's designed volume to its clean structural baseline (base sets, then the
    // MRV cap) — exactly what a freshly generated block uses — and drop any stored autoBias. Manual
    // per-set edits (overrides / circuit rounds) and all progression state are left untouched.
    3: (d) => {
        if (!Array.isArray(d.saved))
            return d;
        const saved = d.saved.map(p => {
            if (!p || !p.config)
                return p;
            if (p.config.progression === "manual" || p.config.percentScheme)
                return p; // never auto-volumed
            if (!p.slotBias && !p.autoBias)
                return p; // nothing accumulated
            const np = { ...p, slotBias: {}, autoBias: undefined };
            if (np.config.autoVolume)
                np.config = { ...np.config, autoVolume: false };
            try {
                capWeeklyVolume(np);
            }
            catch { } // re-enforce the MRV ceiling on the clean baseline
            return np;
        });
        return { ...d, saved };
    },
    // 3 → 4: barbell compounds no longer default to the rep ladder (auto now picks double progression,
    // which fits lifts whose reps fall across sets). Drop any progStyle override that PINS a barbell
    // compound to "ladder" so it falls back to auto → double. Machine/isolation ladders — where reps
    // hold across sets and the ladder is a fine choice — are left exactly as the lifter set them, as
    // are all other explicit styles. A no-op for the common case (styles were never saved, just
    // auto-resolved), which the auto change already corrects.
    4: (d) => {
        if (!Array.isArray(d.saved))
            return d;
        // Frozen deliberately: this is a v4-era migration, a historical artifact. It should keep
        // classifying the way it did when it was written, not drift with today's taxonomy.
        const isBarbellCompound = (id) => { const ex = EX_BY_ID[id]; return !!ex && ex.type === "compound" && (ex.equip || []).includes("barbell"); };
        const saved = d.saved.map(p => {
            if (!p || !p.progStyle)
                return p;
            const ps = { ...p.progStyle };
            let changed = false;
            Object.keys(ps).forEach(id => { if (ps[id] === "ladder" && isBarbellCompound(id)) {
                delete ps[id];
                changed = true;
            } });
            if (!changed)
                return p;
            return { ...p, progStyle: Object.keys(ps).length ? ps : undefined };
        });
        return { ...d, saved };
    },
    // 4 → 5: additive only. Everything that existed before this migration was produced by engine 1, so
    // stamp it as such; that's what lets legacy programs keep engine-1 behaviour while new ones are
    // generated on engine 2. Logged sets also start carrying pw/pt (the weight and rep target that were
    // prescribed) from here on — older sets simply lack the snapshot.
    // 5 -> 6: everything moves to engine 2. Engine 1 was kept alive so that programs generated by it
    // would not have their prescriptions change mid-block underneath the lifter. With no engine-1
    // programs left in the wild, that promise has nothing to protect, and carrying two engines forever
    // to honour it would be paying rent on an empty room. Restamped here rather than defaulted at read
    // time so the store is self-describing: a program says which engine made it, and now they all say 2.
    6: (d) => {
        const stamp = (x) => (x && typeof x === "object") ? { ...x, engineV: 2 } : x;
        const out = { ...d };
        if (Array.isArray(d.saved))
            out.saved = d.saved.map(stamp);
        if (Array.isArray(d.cycles))
            out.cycles = d.cycles.map(stamp);
        // History is a RECORD of what happened. A session prescribed by engine 1 was prescribed by engine
        // 1, and rewriting that would be falsifying the log — the replay/eval tooling reads this field to
        // know which policy issued each set. Left exactly as it was.
        return out;
    },
    5: (d) => {
        const stamp = (x) => (x && typeof x === "object" && x.engineV == null) ? { ...x, engineV: 1 } : x;
        const out = { ...d };
        if (Array.isArray(d.saved))
            out.saved = d.saved.map(stamp);
        if (Array.isArray(d.cycles))
            out.cycles = d.cycles.map(stamp);
        if (Array.isArray(d.history))
            out.history = d.history.map(stamp);
        return out;
    },
    // 6 → 7: groundwork for merging two devices' data. Two things were missing and neither can be
    // reconstructed after the fact, which is why they go in now rather than when a sync backend lands:
    //
    //   `tombs`  — deletions. A merge that only unions records can never delete anything: remove a
    //              program on your phone, merge with the tablet that still has it, and it comes back
    //              from the dead. A deletion has to be a FACT that travels, not an absence.
    //   updatedAt — which of two versions of the same program is the newer one. Without it, a merge
    //              has to guess, and guessing about someone's training data is not acceptable.
    //
    // Existing records get updatedAt = 0: any edit on any device beats a record that predates the
    // concept, which is the only safe default (it can never cause a newer edit to lose).
    7: (d) => {
        const stamp = (x) => (x && typeof x === "object" && x.updatedAt == null) ? { ...x, updatedAt: 0 } : x;
        const out = { ...d, tombs: d.tombs && typeof d.tombs === "object" ? d.tombs : {} };
        if (Array.isArray(d.saved))
            out.saved = d.saved.map(stamp);
        if (Array.isArray(d.cycles))
            out.cycles = d.cycles.map(stamp);
        if (Array.isArray(d.custom))
            out.custom = d.custom.map(stamp);
        return out;
    }
};

function mergeStores(local, incoming) {
    if (!incoming || typeof incoming !== "object")
        return { data: local, stats: null };
    if (!local || typeof local !== "object")
        return { data: incoming, stats: null };
    const A = migrateStore(local), B = migrateStore(incoming);
    const aNewer = (A.savedAt || 0) >= (B.savedAt || 0);
    const older = aNewer ? B : A, newer = aNewer ? A : B;
    const tombs = { ...(A.tombs || {}) };
    Object.entries(B.tombs || {}).forEach(([id, t]) => { if (!(tombs[id] >= t))
        tombs[id] = t; });
    const stats = { history: 0, programs: 0, cycles: 0, custom: 0, bw: 0, conflicts: 0 };
    const byId = (a = [], b = [], count) => {
        const m = new Map(), sourceClock = new Map();
        const add = (rows, storeClock) => (Array.isArray(rows) ? rows : []).forEach(r => {
            if (!r || r.id == null)
                return;
            const prev = m.get(r.id);
            if (!prev) {
                m.set(r.id, r);
                sourceClock.set(r.id, storeClock);
                return;
            }
            if (prev === r)
                return;
            const prevSig = JSON.stringify(prev), nextSig = JSON.stringify(r);
            if (prevSig === nextSig)
                return;
            const pu = prev.updatedAt || 0, ru = r.updatedAt || 0;
            const pc = sourceClock.get(r.id) || 0;
            // Per-record clocks are authoritative. Store recency only breaks legacy/equal-clock ties;
            // the serialised signature is the final stable tie-breaker when both clocks are identical.
            if (ru > pu || (ru === pu && (storeClock > pc || (storeClock === pc && nextSig > prevSig)))) {
                m.set(r.id, r);
                sourceClock.set(r.id, storeClock);
            }
            stats.conflicts++;
        });
        add(a, A.savedAt || 0);
        add(b, B.savedAt || 0);
        // a tombstone removes a record only if the deletion happened AFTER the record's last edit
        const out = [...m.values()].filter(r => !(tombs[r.id] != null && tombs[r.id] >= (r.updatedAt || 0)));
        if (count)
            stats[count] = Math.max(0, out.length - (Array.isArray(a) ? a.length : 0));
        return out;
    };
    /* HISTORY_CAP is applied AFTER the union, so a merge of two well-used devices can discard real
       sessions — 400 + 400 keeps 500 and drops 300. Two things were wrong with that being invisible:
       byId() recorded stats.history from the pre-trim union, so the restore summary announced "400 new
       workouts" when only 100 survived; and the discarded sessions were never mentioned at all. The
       count is now recomputed against what was actually kept, and anything the cap removed is reported
       separately so the summary can say so out loud. Trimming the oldest is still the policy — silently
       overstating what was imported is what this fixes. */
    const histUnion = byId(A.history, B.history, "history");
    const hist = capHistory(histUnion); // count ceiling + byte budget, oldest-first
    const aLen = Array.isArray(A.history) ? A.history.length : 0;
    stats.history = Math.max(0, hist.length - aLen); // net sessions actually gained
    stats.historyDropped = Math.max(0, histUnion.length - hist.length); // lost to the cap
    const mapMerge = (key) => {
        const oa = older[key] || {}, nb = newer[key] || {};
        return { ...oa, ...nb }; // keys only the older side has survive; shared keys take the newer blob
    };
    // `perf` is a derived latest-performance mirror. A per-session correction may beat a globally newer
    // store snapshot, so rebuild exercises represented in retained history from their newest session.
    const mergedPerf = mapMerge("perf"), perfSeen = new Set();
    hist.slice().sort((x, y) => (Number(y?.date) || 0) - (Number(x?.date) || 0)).forEach(h => {
        Object.entries(h?.perf || {}).forEach(([exId, p]) => {
            if (!perfSeen.has(exId)) {
                mergedPerf[exId] = p;
                perfSeen.add(exId);
            }
        });
    });
    /* A weigh-in serialises to ~45 chars, so 200 of them was never a storage decision — it was a
     placeholder that quietly deleted the oldest data of anyone who steps on a scale daily, after
     about six months. History now retains ~6.8 years; a body-weight trend that expires first makes
     the long-range comparison it exists for impossible. 2000 daily weigh-ins is ~5.5 years for ~90KB. */
    const bwKey = (e) => String(e && e.date);
    const bw = [...(Array.isArray(A.bwLog) ? A.bwLog : []), ...(Array.isArray(B.bwLog) ? B.bwLog : [])]
        .filter(e => e && e.date != null && e.w != null)
        .reduce((m, e) => (m.has(bwKey(e)) ? m : m.set(bwKey(e), e)), new Map());
    const bwLog = [...bw.values()].sort((x, y) => x.date - y.date).slice(-BW_LOG_CAP);
    stats.bw = Math.max(0, bwLog.length - (Array.isArray(A.bwLog) ? A.bwLog.length : 0));
    const meas = { ...(older.measurements || {}) };
    Object.entries(newer.measurements || {}).forEach(([d, v]) => {
        meas[d] = (v && typeof v === "object" && meas[d] && typeof meas[d] === "object") ? { ...meas[d], ...v } : v;
    });
    const data = {
        ...older, ...newer, // scalar settings: newer blob wins
        v: STORE_VERSION,
        savedAt: Math.max(A.savedAt || 0, B.savedAt || 0),
        tombs,
        history: hist,
        saved: byId(A.saved, B.saved, "programs"),
        cycles: byId(A.cycles, B.cycles, "cycles"),
        /* Gyms merge by id with the same tombstone rules as programs and history. Previously they were
           not merged at all: the whole list came from whichever store had the later savedAt, so a gym
           added on one device was destroyed by the next sync from another. */
        gyms: (() => {
            const merged = byId(A.gyms, B.gyms, "gyms");
            return merged.length ? merged : (newer.gyms || older.gyms || []); // never sync to zero gyms
        })(),
        // The newer device's choice wins, unless the other device deleted that gym — then fall back
        // rather than leaving the app pointed at something that is gone.
        activeGymId: (() => {
            const surviving = byId(A.gyms, B.gyms);
            const want = newer.activeGymId || older.activeGymId;
            return surviving.some(g => g.id === want) ? want : (surviving[0] && surviving[0].id) || want;
        })(),
        custom: byId(A.custom, B.custom, "custom"),
        banned: [...new Set([...(A.banned || []), ...(B.banned || [])])],
        bwLog,
        measurements: meas,
        perf: mergedPerf,
        exNotes: mapMerge("exNotes"),
        exSetup: mapMerge("exSetup"),
        goals: mapMerge("goals"),
        // Research evidence is append-only. Merge trials by trial id so restoring a backup can never
        // erase a completed control/treatment observation from the other device. The newer blob owns
        // the consent toggle, but both sides' evidence survives.
        canaryResearch: (() => {
            const ca = preserveRetiredTrialData(A.canaryResearch), cb = preserveRetiredTrialData(B.canaryResearch);
            const pref = aNewer ? ca : cb;
            const trials = new Map();
            [...ca.trials, ...cb.trials].forEach(t => { const prev = trials.get(t.trialId); if (!prev || (t.completedAt || t.startedAt || 0) >= (prev.completedAt || prev.startedAt || 0))
                trials.set(t.trialId, t); });
            return { ...pref, trials: [...trials.values()].slice(-200) };
        })()
    };
    return { data, stats };
}

function migrateStore(raw) {
    if (!raw || typeof raw !== "object")
        return raw;
    let d = raw;
    let v = typeof d.v === "number" ? d.v : 1;
    while (v < STORE_VERSION) {
        v += 1;
        const fn = STORE_MIGRATIONS[v];
        if (fn)
            d = fn(d);
    }
    return d.v === STORE_VERSION ? d : { ...d, v: STORE_VERSION };
}

function parseStoredData(raw) {
    try {
        const d = JSON.parse(raw);
        const object = v => !!v && typeof v === "object" && !Array.isArray(v);
        if (!object(d))
            throw new Error("The saved data isn't a training backup.");
        if (typeof d.v === "number" && d.v > STORE_VERSION)
            throw new Error("This data needs a newer version of Pursuit Iron. Update the app, then try again.");
        for (const k of ["saved", "history", "cycles", "custom", "gyms", "bwLog", "banned", "equipDefault"]) {
            if (d[k] != null && !Array.isArray(d[k]))
                throw new Error(`The saved ${k} list couldn't be read.`);
        }
        for (const k of ["perf", "drafts", "tombs", "measurements", "goals", "exNotes", "exSetup", "minInc", "plates", "reminders", "canaryResearch", "selectivePromotionRuntime"]) {
            if (d[k] != null && !object(d[k]))
                throw new Error(`The saved ${k} settings couldn't be read.`);
        }
        for (const k of ["saved", "history", "cycles", "custom", "gyms", "bwLog"]) {
            if ((d[k] || []).some(v => !object(v)))
                throw new Error(`A saved ${k} entry couldn't be read.`);
        }
        const programs = [...(d.saved || []), ...Object.values(d.drafts || {})];
        if (programs.some(p => !object(p) || !object(p.config) || !Array.isArray(p.days) || p.days.some(day => !object(day) || !Array.isArray(day.exercises))))
            throw new Error("A saved program couldn't be read.");
        if ((d.cycles || []).some(c => !Array.isArray(c.blockIds)))
            throw new Error("A saved cycle couldn't be read.");
        if (d.reminders?.days != null && !Array.isArray(d.reminders.days))
            throw new Error("The saved reminder days couldn't be read.");
        return migrateStore(d);
    }
    catch (cause) {
        const error = new Error(cause instanceof SyntaxError ? "The saved file is incomplete or unreadable." : cause.message);
        error.raw = raw; // recovery exports the exact bytes, including malformed JSON
        throw error;
    }
}

async function loadStore() {
    let raw, readError;
    // Only an unavailable backend may fall back. A successful read of corrupt data is NOT an empty
    // install, and must not cause a different backend (or defaults) to overwrite the original.
    try {
        if (typeof localStorage !== "undefined")
            raw = localStorage.getItem(KEY);
    }
    catch (e) {
        readError = e;
    }
    if (raw !== undefined)
        return raw === null ? null : parseStoredData(raw);
    try {
        if (typeof window !== "undefined" && window.storage) {
            const r = await window.storage.get(KEY);
            raw = r == null ? null : r.value;
            if (raw === undefined)
                throw new Error("Storage returned no readable value.");
        }
    }
    catch (e) {
        readError = e;
    }
    if (raw !== undefined)
        return raw === null ? null : parseStoredData(raw);
    if (readError)
        throw new Error("Device storage isn't responding. Try again when it becomes available.");
    return MEM.data == null ? null : parseStoredData(JSON.stringify(MEM.data));
}

async function writeStoreText(key, text) {
    try {
        if (typeof localStorage !== "undefined") {
            localStorage.setItem(key, text);
            return true;
        }
    }
    catch { }
    try {
        if (typeof window !== "undefined" && window.storage) {
            await window.storage.set(key, text);
            return true;
        }
    }
    catch { }
    return false;
}

let storeSaveQueue = Promise.resolve();

function saveStore(data) {
    let text;
    try {
        text = JSON.stringify(data);
        MEM.data = JSON.parse(text);
    }
    catch {
        return Promise.resolve(false);
    }
    const pending = storeSaveQueue.then(() => writeStoreText(KEY, text));
    storeSaveQueue = pending.catch(() => false);
    return pending;
}

const SHEET_GRAB_PX = 72;

const SHEET_DISMISS_PX = 110;

function sheetDragRef(el) {
    if (!el || el.dataset.sheetDrag)
        return;
    el.dataset.sheetDrag = "1";
    // Natural-content sheets stay compact; long sheets use the large detent. The choice is structural,
    // not per-feature styling, so every sheet participates in the same hierarchy automatically.
    requestAnimationFrame(() => {
        try {
            const vh = window.visualViewport?.height || window.innerHeight || 800;
            el.dataset.detent = el.scrollHeight <= vh * .58 ? "content" : "large";
        }
        catch {
            el.dataset.detent = "large";
        }
    });
    let y0 = null, dy = 0, dragging = false, raf = 0, shownY = 0;
    let lastY = 0, lastAt = 0, velocityY = 0;
    const paint = () => {
        raf = 0;
        el.style.transform = shownY > 0 ? `translate3d(0, ${shownY}px, 0)` : "";
    };
    const queuePaint = (nextY) => {
        shownY = nextY;
        if (!raf)
            raf = requestAnimationFrame(paint);
    };
    const reset = (animate) => {
        if (raf) {
            cancelAnimationFrame(raf);
            raf = 0;
        }
        shownY = 0;
        delete el.dataset.dragging;
        el.style.transition = animate ? "transform .26s cubic-bezier(.16,1,.3,1)" : "";
        el.style.transform = "";
        if (animate)
            setTimeout(() => { el.style.transition = ""; el.style.willChange = ""; }, 280);
        else
            el.style.willChange = "";
    };
    el.addEventListener("touchstart", (ev) => {
        if (ev.touches.length !== 1)
            return;
        const t = ev.touches[0];
        const box = el.getBoundingClientRect();
        // top band only, and only when whatever is inside is already scrolled to its top
        if (t.clientY - box.top > SHEET_GRAB_PX)
            return;
        if (el.scrollTop > 0)
            return;
        y0 = t.clientY;
        dy = 0;
        dragging = true;
        lastY = t.clientY;
        lastAt = performance.now();
        velocityY = 0;
        el.dataset.dragging = "1";
        el.style.transition = "";
        el.style.willChange = "transform";
        /* DROP THE ENTRY ANIMATION BEFORE MOVING THE SHEET.
           Every sheet opens with `animation: sheetUp ... both`, whose final keyframe is
           transform: translateY(0) — and `both` keeps that value applied for as long as the animation
           is on the element. A CSS animation sits in a HIGHER CASCADE ORIGIN than an inline style, so
           el.style.transform was being computed, assigned, and then completely ignored by the browser.
           Clearing the animation hands control of transform back to us. The drag itself is then coalesced
           through requestAnimationFrame so 90/120 Hz phones never get multiple style writes in one frame. */
        el.style.animation = "none";
    }, { passive: true });
    el.addEventListener("touchmove", (ev) => {
        if (!dragging || y0 == null)
            return;
        const now = performance.now(), currentY = ev.touches[0].clientY;
        const dt = Math.max(1, now - lastAt);
        const sample = (currentY - lastY) / dt;
        velocityY = velocityY * 0.62 + sample * 0.38; // damp jitter without hiding a deliberate flick
        lastY = currentY;
        lastAt = now;
        dy = currentY - y0;
        if (dy <= 0) {
            queuePaint(0);
            return;
        } // upward is not a dismissal
        /* Resistance past the threshold so the sheet keeps responding without running off the screen —
           the feedback is what tells you the gesture is live before you commit to it. */
        const shown = dy < SHEET_DISMISS_PX ? dy : SHEET_DISMISS_PX + (dy - SHEET_DISMISS_PX) * 0.35;
        queuePaint(shown);
    }, { passive: true });
    el.addEventListener("touchend", () => {
        if (!dragging)
            return;
        dragging = false;
        y0 = null;
        // Native sheets settle from both DISTANCE and INTENT. A quick downward flick may dismiss after
        // less travel than a slow drag, but still needs ~44px so a tap/scroll overshoot cannot close it.
        const flickDismiss = dy >= 44 && velocityY >= 0.62;
        if (dy >= SHEET_DISMISS_PX || flickDismiss) {
            /* Hand the dismissal to the backdrop the sheet already has. Reset first: if that backdrop does
               not close anything, the sheet must not be left sitting a hundred pixels down the screen. */
            reset(false);
            const back = el.parentElement;
            if (back)
                back.click();
            return;
        }
        reset(true);
    }, { passive: true });
    el.addEventListener("touchcancel", () => {
        if (!dragging)
            return;
        dragging = false;
        y0 = null;
        dy = 0;
        reset(true);
    }, { passive: true });
}

const SPLIT_BUILD_CACHE = new Map();

function sessionChoicesFor(splitId) {
    const minS = SPLITS[splitId]?.minSession, maxS = SPLITS[splitId]?.maxSession, order = SESSIONS.map(x => x.id);
    return SESSIONS.filter(x => (!minS || order.indexOf(x.id) >= order.indexOf(minS)) && (!maxS || order.indexOf(x.id) <= order.indexOf(maxS)));
}

const STEP_KEYS = ["units", "name", "experience", "goal", "approach", "days", "split", "session", "equipment", "constraints", "focus", "reduce", "progression", "length", "deload"];

const CYCLE_STEP_KEYS = ["cycletype", "units", "name", "experience", "approach", "days", "split", "session", "equipment", "constraints", "focus", "reduce", "deload"];

function recommendedSplit(config, nextRecommendation) {
    const days = config.days;
    const fitsSession = (k) => {
        const min = SPLITS[k]?.minSession, max = SPLITS[k]?.maxSession;
        if ((!min && !max) || !config.session)
            return true;
        const order = SESSIONS.map(x => x.id), i = order.indexOf(config.session);
        return (!min || i >= order.indexOf(min)) && (!max || i <= order.indexOf(max));
    };
    if (nextRecommendation && SPLITS[nextRecommendation]?.days.includes(days) && fitsSession(nextRecommendation))
        return nextRecommendation;
    // Fallback is compatibility-only if generation cannot form a recommendation (for example, a
    // temporarily impossible equipment selection). It does not rank named programs or maintain a
    // second scoring model.
    const generic = days <= 3 ? "full_body" : days === 4 ? "upper_lower" : days === 5 ? "ulppl" : "ppl";
    if (SPLITS[generic]?.days.includes(days) && fitsSession(generic))
        return generic;
    return Object.entries(SPLITS).find(([, split]) => split.days.includes(days))?.[0] || null;
}

function afterNextPaint() {
    return new Promise(resolve => {
        let first, second;
        const done = () => { clearTimeout(timer); cancelAnimationFrame(first); cancelAnimationFrame(second); resolve(); };
        const timer = setTimeout(done, 250);
        first = requestAnimationFrame(() => { second = requestAnimationFrame(done); });
    });
}

const PHASES = {
    accumulation: { label: "Accumulation", tagline: "Build the work", why: "Volume is high and effort moderate — bank the work that later intensity will sharpen.", color: "#8EBE6B" },
    intensification: { label: "Intensification", tagline: "Add the load", why: "Sets get heavier and closer to failure while volume holds — tension and strength climb.", color: "#D4B85D" },
    peak: { label: "Peak", tagline: "Push the ceiling", why: "The hardest sets of the block. Leave it all here, then back off to recover.", color: "#D45D6C" },
    deload: { label: "Deload", tagline: "Recover & adapt", why: "Volume and load drop so your body catches up to the training — you return fresher and stronger.", color: "#5DB4D4" }
};

function phaseFor(program, weekIndex) {
    const weeks = weeksOf(program);
    if (program.config?.deload && weekIndex > weeks)
        return PHASES.deload;
    const p = blockPhase(program, weekIndex, 1); // honors cycle-block phaseWindow
    if (p >= 0.8)
        return PHASES.peak;
    if (p >= 0.4)
        return PHASES.intensification;
    return PHASES.accumulation;
}

const PV_PAD_TOP = 9;

const PV_ROW_GAP = 8;

const _rot = (hex, deg, satMul = 1) => {
    try {
        const [h, sat, l] = _rgb2hsl(_hx2rgb(hex));
        return _rgb2hx(_hsl2rgb([(h + deg / 360 + 1) % 1, Math.max(0, Math.min(1, sat * satMul)), l]));
    }
    catch {
        return hex;
    }
};

function phaseKind(label, index) {
    const l = String(label || "").toLowerCase();
    if (/deload|recover|rest|taper/.test(l))
        return 4;
    if (/accum|hypertroph|base|found|volume|build/.test(l))
        return 0;
    if (/strength|intens|develop|raise/.test(l))
        return 1;
    if (/peak|specific|sharp|power/.test(l))
        return 2;
    if (/realis|realiz|test|compet|max/.test(l))
        return 3;
    return Math.min(index, 3);
}

const PHASE_ROT = [-34, -12, 14, 40];

const repsLow = (s) => (s == null) ? "" : String(s).includes("-") ? String(s).split("-")[0] : String(s);

let _beepCtx = null;

function beepContext() {
    if (_beepCtx && _beepCtx.state !== "closed")
        return _beepCtx;
    const Ctx = typeof window !== "undefined" && (window.AudioContext || window.webkitAudioContext);
    _beepCtx = Ctx ? new Ctx() : null;
    return _beepCtx;
}

function releaseBeepContext() {
    const ac = _beepCtx;
    _beepCtx = null;
    try { if (ac && ac.state !== "closed") ac.close().catch(() => { }); }
    catch { /* audio is optional */ }
}

function unlockBeep() {
    try {
        const ac = beepContext();
        if (ac && ac.state !== "running")
            ac.resume().catch(() => { });
    }
    catch { /* no audio */ }
}

function beep() {
    try {
        const ac = beepContext();
        if (!ac)
            return;
        if (ac.state !== "running")
            ac.resume().catch(() => { });
        const t = ac.currentTime + 0.01;
        const o = ac.createOscillator(), g = ac.createGain();
        o.type = "sine";
        o.frequency.setValueAtTime(880, t);
        o.frequency.setValueAtTime(660, t + 0.12);
        g.gain.setValueAtTime(0.0001, t);
        g.gain.exponentialRampToValueAtTime(0.06, t + 0.005);
        g.gain.setValueAtTime(0.06, t + 0.2);
        g.gain.exponentialRampToValueAtTime(0.0001, t + 0.3);
        o.connect(g);
        g.connect(ac.destination);
        o.start(t);
        o.stop(t + 0.32);
        o.onended = () => { try { o.disconnect(); g.disconnect(); } catch { } };
    }
    catch (e) { /* no audio */ }
}

const fmtTime = (s) => `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`;

const SHOW_EXERCISE_ANIMATION = false;

function formVideoUrl(ex) {
    if (ex && typeof ex.video === "string" && /^https:\/\//.test(ex.video))
        return ex.video;
    return "https://www.youtube.com/results?search_query=" + encodeURIComponent(`${ex?.name || "exercise"} proper form`);
}

function sessionSnapshotStatus(snapshot, ctx) {
    const s = snapshot;
    if (!s || s.programId !== ctx.programId || s.dayId !== ctx.dayId || s.weekIndex !== ctx.weekIndex
        || !Array.isArray(s.data) || !s.data.length)
        return "none";
    // Storage is untrusted: a null set used to pass this check and crash resume.
    if (!s.data.every(e => e && typeof e.id === "string" && EX_BY_ID[e.id] && Array.isArray(e.sets)
        && e.sets.every(row => row && typeof row === "object" && !Array.isArray(row))))
        return "none";
    if (s.dayExSig != null && s.dayExSig !== ctx.dayExSig)
        return "partial";
    return "resume";
}

function mergeSessionData(snapData, fresh) {
    if (!Array.isArray(fresh))
        return fresh;
    if (!Array.isArray(snapData) || !snapData.length)
        return fresh;
    const byId = new Map();
    for (const e of snapData)
        if (e && e.id != null && !byId.has(e.id))
            byId.set(e.id, e);
    return fresh.map(e => {
        const prev = byId.get(e.id);
        if (!prev || !Array.isArray(prev.sets) || !prev.sets.length)
            return e;
        return { ...e, sets: prev.sets, note: prev.note || e.note };
    });
}

function mergedSetCount(data) {
    return (Array.isArray(data) ? data : []).reduce((n, e) => n + ((e.sets || []).filter(s => s && s.done && !s.warm).length), 0);
}

function userOwnsRuntimeSet(row) {
    if (!row)
        return false;
    if (row.valueOwner != null)
        return row.valueOwner === "user";
    return row.auto === false; // pre-M204 live snapshots
}

function sanitizeWeightInput(v) {
    let s = String(v).replace(/[^0-9.\-]/g, "");
    const neg = s.trim().startsWith("-"); // a single leading minus = assistance (assisted lifts)
    s = s.replace(/-/g, "");
    const i = s.indexOf(".");
    if (i !== -1)
        s = s.slice(0, i + 1) + s.slice(i + 1).replace(/\./g, "");
    if (parseFloat(s) > 2000)
        s = "2000";
    return (neg ? "-" : "") + s;
}

function sanitizeRepsInput(v) {
    let s = String(v).replace(/[^0-9]/g, "");
    if (parseInt(s) > 100)
        s = "100";
    return s;
}

function dayPerfFor(day, perf, history) {
    const out = {};
    // Estimated 1RM of a session's best logged set — used only to pick which recent session anchors
    // the next suggestion, so demonstrated capacity wins over a single off day.
    const anchorE1 = pp => {
        const ss = (pp.sets && pp.sets.length) ? pp.sets : [{ w: parseFloat(pp.weight), r: parseInt(pp.reps), rir: null }];
        let best = 0;
        ss.forEach(s => {
            const w = parseFloat(s.w != null ? s.w : s.weight), r = parseInt(s.r != null ? s.r : s.reps);
            if (w > 0 && r > 0)
                best = Math.max(best, e1rmRIR(w, r, s.rir != null ? s.rir : ASSUMED_RIR));
        });
        return best;
    };
    day.exercises.forEach(id => {
        // Anchor the suggestion on the BEST of the last few sessions OF THIS DAY that logged this lift,
        // not strictly the most recent one. A single fatigued / readiness-reduced light day shouldn't
        // drop your working load and keep it there once you've recovered — your demonstrated capacity
        // should hold. A genuine multi-session decline still pulls the best-of-recent down with it, and
        // ties favor the most recent session (so real progression is reflected immediately). `reps !=
        // null` (not weight > 0) keeps assisted/bodyweight lifts tracked per-day.
        const recent = [];
        for (const h of history) {
            if (h.dayId === day.id && h.perf && h.perf[id] && h.perf[id].reps != null) {
                recent.push(h.perf[id]);
                if (recent.length >= 3)
                    break;
            }
        }
        if (recent.length)
            out[id] = recent.reduce((a, b) => anchorE1(b) > anchorE1(a) ? b : a);
        else if (perf && perf[id])
            out[id] = perf[id];
    });
    return out;
}

function suggestionLoadLabel(suggestion) {
    const weights = (suggestion.setTargets || []).map(s => s.weight).filter(Number.isFinite);
    return weights.length && Math.min(...weights) !== Math.max(...weights)
        ? `${Math.min(...weights)}–${Math.max(...weights)}` : suggestion.weight;
}

function sessionSuggestion(program, day, slot, dayPerf, unit, weekIndex, history) {
    void dayPerf;
    if (program?.custom === true && program?.engineSource !== "pursuit-next")
        return customProgramSuggestion(program, day, slot, unit, weekIndex, history);
    const original = nextWorkoutSuggestionForShell(program, history || [], EXERCISES, day, slot, weekIndex);
    const convert = value => convertHistoryLoad(value, program.config?.unit, unit);
    const s = original ? { ...original, weight: convert(original.weight),
        last: original.last,
        setTargets: original.setTargets?.map(t => ({ ...t, weight: convert(t.weight) })),
        reason: historyLoadReason(original.reason, unit) } : null;
    /* ⚠ WHAT IS LOADABLE HAS ONE OWNER: the app's loadStep / gymRackFor — the same rule the workout's "load-not-loadable" check
       uses. The engine rounds against its own loading inventory, which can allow a weight the app's implement cannot make (45 lb on
       a 10 lb machine stack). Measured: the week-aware re-prescription produced 1,160 such loads in the self-test. Every suggestion
       is snapped DOWN to a loadable weight here, whichever engine path produced it. */
    if (!s || !(Number(s.weight) > 0))
        return s;
    const ex = EX_BY_ID[day?.exercises?.[slot]], eng = Number(s.weight);
    /* ⚠ DIRECTION MATTERS. An EARNED increase rounds UP to the next weight the equipment can make; everything else rounds DOWN
       (conservative). Rounding an increase down erased it: the engine said "Increase from 140 lb to 145 lb", the 10 lb machine stack
       snapped it back to 140, and the lift could never progress (found by m158-adaptive-loop-check's simulated lifter). */
    const current = convertHistoryLoad(s.last?.weight, s.lastUnit || program.config?.unit, unit) || 0;
    let w = loadableAtOrBelow(ex, eng, unit);
    if (s.action === "increase_load" && w <= current)
        w = loadableAbove(ex, current, unit) ?? w;
    const cell = computeCell(program, day, ex.id, slot, weekIndex);
    const floor = cellRepRange(cell, program, ex, slot === day.primaryIndex)[0];
    const setTargets = s.setTargets?.map(target => {
        const weight = s.action === "increase_load" ? w : loadableAtOrBelow(ex, target.weight, unit);
        return { ...target, weight, reps: weight < target.weight ? floor : target.reps };
    });
    const reason = typeof s.reason === "string" ? s.reason.replace(new RegExp(`\\bto ${eng}( ?${unit})`), `to ${w}$1`) : s.reason;
    return { ...s, weight: w, setTargets, reason, target: w < eng ? floor : s.target,
        dir: current && w > current ? "up" : current && w < current ? "down" : s.dir };

}

function loadableAbove(ex, w, unit) {
    if (!ex || !(w > 0))
        return null;
    const rack = gymRackFor(ex, unit);
    if (rack && rack.length) {
        const up = rack.filter(x => x > w + 1e-9);
        return up.length ? Math.min(...up) : null;
    }
    const step = loadStep(ex, unit) || 0;
    return step > 0 ? (Math.floor(w / step + 1e-9) + 1) * step : null;
}

function customExerciseHistory(program, day, id, history) {
    const entries = (history || []).filter(h => validHistoryDate(h) && h?.perf?.[id]).slice().sort((a, b) => (Number(b.date) || 0) - (Number(a.date) || 0));
    const own = entries.filter(h => h.programId === program.id);
    const exact = own.find(h => h.dayId === day.id);
    if (exact)
        return exact;
    // A regenerated day ID can still be matched by its stable authored label. Do not use a different
    // day merely because it contains the same exercise: set count, rep target and progression method
    // are properties of this day slot, not of the movement globally.
    const label = String(day?.label || '').trim();
    if (label) {
        const byLabel = own.find(h => String(h.dayLabel || '').trim() === label);
        if (byLabel)
            return byLabel;
    }
    // Old custom logs may predate day identity. They are comparable only when this exercise occurs on
    // exactly one authored day, making the ownership unambiguous. Otherwise they are reference-only.
    const authoredDays = (program?.days || []).filter(d => Array.isArray(d?.exercises) && d.exercises.includes(id));
    if (authoredDays.length === 1)
        return own.find(h => !h.dayId && !h.dayLabel) || null;
    return null;
}

function customExerciseReferenceHistory(program, id, history) {
    const entries = (history || []).filter(h => validHistoryDate(h) && h?.perf?.[id]).slice().sort((a, b) => (Number(b.date) || 0) - (Number(a.date) || 0));
    return entries.find(h => h.programId === program.id) || entries[0] || null;
}

function representativeCustomLoad(perf) {
    const direct = historyNumber(perf?.weight);
    if (direct !== null && direct > 0)
        return direct;
    const loads = completedHistorySets(perf).map(set => historyNumber(set?.w)).filter(value => value !== null && value > 0);
    return loads.length ? Math.max(...loads) : null;
}

function customProgramSuggestion(program, day, slot, unit, weekIndex, history) {
    const ex = EX_BY_ID[day?.exercises?.[slot]];
    if (!ex)
        return null;
    const last = customExerciseHistory(program, day, ex.id, history);
    if (!last) {
        const reference = customExerciseReferenceHistory(program, ex.id, history);
        const rawLoad = representativeCustomLoad(reference?.perf?.[ex.id]);
        if (!reference || rawLoad === null)
            return null;
        const currentCell = computeCell(program, day, ex.id, slot, weekIndex);
        const [lo] = cellRepRange(currentCell, program, ex, slot === day?.primaryIndex);
        const weight = convertHistoryLoad(rawLoad, reference.unit, unit);
        return { weight, dir: 'hold', action: 'initial', reps: currentCell?.range ?? currentCell?.reps,
            target: lo, last: reference.perf[ex.id], confidence: 'low', referenceOnly: true,
            reason: 'Use the last known load only as a starting reference. This program day has no comparable completed history yet, so reps/load are not progressed from another day or program.' };
    }
    const conv = w => convertHistoryLoad(w, last.unit, unit);
    const currentCell = computeCell(program, day, ex.id, slot, weekIndex);
    const saved = last.perf[ex.id].prescription;
    const validSaved = saved?.schemaVersion === 1 && saved.exerciseId === ex.id
        && Number.isInteger(saved.sets) && saved.sets > 0 && saved.sets <= 20
        && effortBounds(saved.reps)?.[0] > 0 && effortBounds(saved.rir)?.[0] >= 0;
    const cell = validSaved ? { ...currentCell, ...saved, range: Array.isArray(saved.reps) ? saved.reps.join('-') : saved.reps,
        reps: Array.isArray(saved.reps) ? saved.reps.join('-') : saved.reps,
        rir: Array.isArray(saved.rir) ? saved.rir.join('-') : saved.rir } : currentCell;
    const [lo, hi] = cellRepRange(cell, program, ex, slot === day?.primaryIndex);
    const rir = effortBounds(cell.rir) || [2, 2];
    const work = (Array.isArray(last.perf[ex.id].sets) ? last.perf[ex.id].sets : [])
        .filter(x => x && !x.warm && !x.sub && x.done !== false && historyNumber(x.w) !== null && historyNumber(x.w) >= 0
            && historyNumber(x.r) > 0);
    if (!work.length) return null;
    const style = resolveStyle(program, ex, slot === day.primaryIndex, weekIndex, null, history, day.id);
    const rack = gymRackFor(ex, unit), step = loadStep(ex, unit);
    const loadingInventory = { unit, exerciseOverrides: { [ex.id]: rack?.length
        ? { availableLoads: rack } : { increment: step, minimum: 0 } } };
    const performed = work.map((x, i) => ({ exerciseId: ex.id, setIndex: i, load: conv(x.w), reps: Number(x.r),
        ...historyExposureContext(x), rir: observedHistoryRIR(x) }));
    const exercise = { exerciseId: ex.id, name: ex.name, role: cell.role, sets: Number(cell.sets),
        progressionStyle: style, prescription: { reps: [lo, hi], rir } };
    const exposure = progressionExposureContext([last.perf[ex.id], ...performed], last);
    const result = evaluateWorkoutProgression({ exercises: [exercise] }, performed, {
        loadingInventory, equipmentAvailable: program.config?.equipment || [],
        ...historyExposureContext(last), badDay: exposure.badDay, interrupted: exposure.interrupted,
        prescriptionEdited: exposure.nonComparable
    })[0];
    const weight = result.suggestedLoad ?? result.currentLoad;
    return { weight, dir: weight > result.currentLoad ? "up" : weight < result.currentLoad ? "down" : "hold",
        action: result.action, reps: cell.range, target: result.suggestedReps ?? lo,
        setTargets: result.setTargets, last: last.perf[ex.id], confidence: result.confidence, reason: result.reason };

}

function loadableAtOrBelow(ex, w, unit) {
    if (!ex || !(w > 0))
        return w;
    const rack = gymRackFor(ex, unit);
    if (rack && rack.length) {
        const fit = rack.filter(x => x <= w + 1e-9);
        return fit.length ? Math.max(...fit) : Math.min(...rack);
    }
    const step = loadStep(ex, unit) || 0;
    return step > 0 ? Math.max(step, Math.floor(w / step + 1e-9) * step) : w;
}

const EFFORT_MIN_PAIRS = 6;

const EFFORT_MAX_BIAS = 2.5;

const EFFORT_REP_CAP = 15;

function effortCalibration(history) {
    // CRITICAL: reference and observations must come from the SAME TIME WINDOW.
    // The first version built the reference from RECENT maximal sets but drew observations from the
    // WHOLE history. A set performed 14 weeks ago — when the lifter was genuinely weaker — was then
    // judged against today's stronger reference, which made it look as though they'd had far more in
    // reserve than they claimed. That manufactured a NEGATIVE bias in exact proportion to how much the
    // lifter had gained (measured: slow gainer 0.0, average −0.87, fast gainer −1.0, all of whom
    // report honestly), and the engine "corrected" for it by pushing their loads UP until they started
    // missing reps. Strength drift is not a reporting bias. Windowing both sides to the same recent
    // sessions holds strength approximately constant, which is the only condition under which the
    // comparison means anything.
    const WINDOW = 8; // recent sessions per lift on both sides of the comparison
    const hs = history || [];
    const bySession = {}; // exId -> [{sets, date}] newest-first
    for (const h of hs) {
        for (const [id, p] of Object.entries(h.perf || {})) {
            // setsOf, not p.sets: a corrupt backup can put nulls (or a string) in here, and this function is
            // now reached from muscleRecovery — so a malformed entry would take out the whole session view.
            const ss = completedHistorySets(p).map(x => ({ ...x, w: convertHistoryLoad(x.w, h.unit, "kg"), rir: observedHistoryRIR(x) }));
            if (!ss.length)
                continue;
            (bySession[id] = bySession[id] || []).push(ss);
        }
    }
    const obs = [];
    for (const [, sessions] of Object.entries(bySession)) {
        const win = sessions.slice(0, WINDOW); // newest-first → the most recent WINDOW sessions
        // pass 1: reference, from sets the lifter was ASKED to take to maximum, within the window
        const maximal = [];
        for (const sets of win) {
            for (const s of sets) {
                const w = parseFloat(s.w), r = parseInt(s.r);
                if (!(w > 0) || !(r > 0))
                    continue;
                // Only a set PRESCRIBED as maximal (AMRAP, or RIR 0 asked and reported) is a trustworthy 1RM
                // reference. A bare "0" tapped on a set nobody asked to be maximal is ambiguous, and feeding
                // it in as ground truth poisons the reference.
                if (!(s.amrap || (s.rir === 0 && s.tr != null && s.tr <= 0)))
                    continue;
                maximal.push(e1rmRIR(w, r, 0, EFFORT_REP_CAP));
            }
        }
        if (!maximal.length)
            continue;
        // A robust CENTRE, never a maximum: the max of a noisy sample is biased upward by construction —
        // it selects for the luckiest day — and that bias flows straight into every implied reserve.
        const sorted = maximal.slice().sort((a, b) => a - b);
        const m = Math.floor(sorted.length / 2);
        const ref = sorted.length % 2 ? sorted[m] : (sorted[m - 1] + sorted[m]) / 2;
        if (!(ref > 0))
            continue;
        // pass 2: ordinary stated-reserve sets from the SAME window, against that reference
        for (const sets of win) {
            for (const s of sets) {
                const w = parseFloat(s.w), r = parseInt(s.r);
                if (!(w > 0) || !(r > 0) || s.rir == null || s.amrap)
                    continue;
                if (s.rir === 0 && (s.tr == null || s.tr <= 0))
                    continue; // reference set / unknown intent
                if (w >= ref)
                    continue; // no reserve to infer at/above ref
                const implied = 30 * (ref / w - 1) - Math.min(r, EFFORT_REP_CAP);
                if (!isFinite(implied) || implied < -3 || implied > 8)
                    continue;
                obs.push(s.rir - implied);
            }
        }
    }
    if (obs.length < EFFORT_MIN_PAIRS)
        return { bias: 0, confidence: 0, n: obs.length };
    // median is the right centre here — one mis-tapped RIR shouldn't move the calibration
    const sorted = obs.slice().sort((a, b) => a - b);
    const mid = Math.floor(sorted.length / 2);
    const med = sorted.length % 2 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2;
    const confidence = clamp((obs.length - EFFORT_MIN_PAIRS) / 18, 0, 1);
    // ramp the correction in with confidence, so it never lurches on the 6th observation
    const bias = clamp(med, -EFFORT_MAX_BIAS, EFFORT_MAX_BIAS) * confidence;
    return { bias: Math.round(bias * 100) / 100, confidence: Math.round(confidence * 100) / 100, n: obs.length };
}

function lastDayPerf(day, perf, history, program = null) {
    const out = {};
    for (const id of day.exercises) {
        if (program?.custom === true && program?.engineSource !== "pursuit-next") {
            const comparable = customExerciseHistory(program, day, id, history);
            if (comparable?.perf?.[id])
                out[id] = comparable.perf[id];
            continue;
        }
        for (const h of (history || [])) { // history is newest-first
            if (h.dayId === day.id && h.perf?.[id] && h.perf[id].reps != null) {
                out[id] = h.perf[id];
                break;
            }
        }
        if (!out[id] && perf?.[id])
            out[id] = perf[id]; // never trained on this day → carry global
    }
    return out;
}

function anchorPerfFor(program, day, perf, history, weekIndex) {
    const best = dayPerfFor(day, perf, history);
    if (!day || !Array.isArray(day.exercises))
        return best;
    let last = null; // computed lazily — most days have no DDP lift
    const out = { ...best };
    day.exercises.forEach((id, slot) => {
        const ex = EX_BY_ID[id];
        if (!ex)
            return;
        if (resolveStyle(program, ex, slot === day.primaryIndex, weekIndex, perf, history, day?.id) !== "dynamic")
            return;
        if (!last)
            last = lastDayPerf(day, perf, history);
        if (last[id])
            out[id] = last[id];
    });
    return out;
}

function calibratedAnchorPerf(program, day, perf, history, weekIndex, effort) {
    return calibrateDayPerf(anchorPerfFor(program, day, perf, history, weekIndex), effort);
}

function calibrateDayPerf(dayPerf, effort) {
    if (!effort || !effort.bias || !dayPerf)
        return dayPerf;
    const out = {};
    for (const [id, p] of Object.entries(dayPerf)) {
        if (!p || !p.sets || !p.sets.length) {
            out[id] = p;
            continue;
        }
        // keep the record's own shape here — do NOT synthesise sets it never had
        out[id] = { ...p, sets: Array.isArray(p.sets) ? p.sets.map(s => (s && s.rir == null ? s : { ...s, rir: clamp(s.rir - effort.bias, 0, 6) })) : p.sets };
    }
    return out;
}

const PLANNED_ROOM_RIR = 2.0;

function readinessBand(readiness, ctx) {
    if (readiness == null)
        return { band: "green", trim: 0, why: "" };
    if (readiness >= 80)
        return { band: "green", trim: 0, why: "" };
    /* Name the CAUSE. "Still recovering" on a day the program itself scheduled reads as the app being
       arbitrary, and the lifter has no way to act on it. The usual driver is not the calendar but the
       effort: taking sets to failure when the prescription asked for reps in reserve raises the
       estimated recovery cost, so it is the RIR — something they control — that should be named. */
    const days = ctx && ctx.hoursSince != null ? Math.max(1, Math.round(ctx.hoursSince / 24)) : null;
    const gap = days ? `${days} day${days === 1 ? "" : "s"}` : "this gap";
    /* WHOSE DOING WAS IT. `overBy` is how far under the prescribed reserve the lifter finished, and on
       its own it cannot answer that. An overshoot of 0.75 against a plan asking RIR 4 is a lifter
       ignoring the plan; the same 0.75 against a plan asking RIR 0.76 — which is precisely what week 4
       of an intensification block prescribes — is a quarter of a rep, and the plan was already at the
       edge on purpose. The old threshold read them identically, so a hypertrophy block doing exactly
       what it was designed to do got told it had "taken most of the session to failure" and had volume
       trimmed for it. The block IS the intensity; being surprised by it is the app failing to read its
       own programming.
       So the accusatory wording now requires that the plan actually left room to overshoot
       (PLANNED_ROOM_RIR). Below that the work was near-failure BY DESIGN: still trim on a short gap —
       autoregulation is exactly the right response to intense work on one day's rest — but name the
       block rather than the lifter, because there is nothing for them to correct. */
    const overshot = ctx && ctx.overBy >= 0.75;
    const planNearFailure = ctx && ctx.plannedRIR != null && ctx.plannedRIR < PLANNED_ROOM_RIR;
    const hard = overshot && !planNearFailure;
    const byDesign = planNearFailure && !!days;
    if (readiness >= 50) {
        return { band: "yellow", trim: 1, why: hard
                ? `You took most of your last session here to failure — ${gap} is short for that, so one accessory set is trimmed.`
                : byDesign
                    ? `This block programs near-failure work and it has only been ${gap} — one accessory set is trimmed to pay for it. The main lift is untouched.`
                    : "Still recovering — one set trimmed from accessory work." };
    }
    return { band: "red", trim: 1, why: hard
            ? `Last session here was taken to failure and it has only been ${gap}. Consider a fresher day, or keep it light.`
            : byDesign
                ? `Near-failure work as programmed, ${gap} ago. That's the block working — but consider a fresher day, or keep this one light.`
                : "This muscle group is under-recovered. Consider a fresher day, or keep it light." };
}

function classifyPlateau(trend, sets, muscleReadiness, ctx) {
    const base = plateauOf(trend);
    if (!base)
        return null;
    const recent = (sets || []).slice(-6); // oldest→newest per-session summaries { r, rir }
    let kind = "variance";
    if (recent.length >= 3) {
        const half = Math.floor(recent.length / 2);
        const avg = (a, f) => a.length ? a.reduce((s, x) => s + f(x), 0) / a.length : 0;
        const repsEarly = avg(recent.slice(0, half), x => x.r);
        const repsLate = avg(recent.slice(half), x => x.r);
        const rirEarly = avg(recent.slice(0, half).filter(x => x.rir != null), x => x.rir);
        const rirLate = avg(recent.slice(half).filter(x => x.rir != null), x => x.rir);
        const repsFalling = repsLate < repsEarly - 0.4;
        const grinding = rirLate < rirEarly - 0.3; // less in reserve for the same work
        const beatUp = muscleReadiness != null && muscleReadiness < 65;
        if (repsFalling && (grinding || beatUp))
            kind = "fatigue";
        else if (Math.abs(repsLate - repsEarly) <= 0.4)
            kind = "true";
    }
    /* ── TWO CAUSES A SINGLE LIFT'S OWN NUMBERS CANNOT SEE ───────────────────────────────────────
     * The three kinds above are all read from THIS lift's reps and effort, which is the right place to
     * look for fatigue and for noise. But a lift can also stop moving for reasons that live outside its
     * own series entirely, and calling those a "strength ceiling" sends the lifter to deload when the
     * answer is the opposite.
     *
     * VOLUME-LIMITED outranks a true plateau, and it is the one worth catching. A muscle sitting under
     * its weekly floor is not at a ceiling — it is under-stimulated, and "deload this lift, then
     * rebuild" is precisely the wrong instruction. Read through `volumeLedger`/`landmarkOf` so parts and
     * sub-regions answer the same way; the delt heads are exactly where this shows up, and reading the
     * part-level accessor for a head returns undefined, which used to look like zero.
     *
     * EXERCISE-SPECIFIC needs the lifter's other work for the same muscle. If the bench has stalled
     * while their incline and dip both keep climbing, the muscle is fine and the movement is stale —
     * rotate the exercise rather than change the program. Requires at least two SIBLINGS still moving,
     * because one other lift progressing is as easily noise as signal.
     *
     * Ordered deliberately: an under-fed muscle explains a stall better than a stale movement does, and
     * both explain it better than "you have hit your ceiling". Neither overrides `fatigue` — a lifter
     * whose reps are falling while effort climbs is beaten up regardless of how much volume the
     * spreadsheet says the muscle got. */
    if (ctx && kind !== "fatigue") {
        const floor = ctx.floor, vol = ctx.volume;
        if (floor > 0 && vol != null && vol < floor - 0.5)
            kind = "volume";
        else if (Array.isArray(ctx.siblingsProgressing) && ctx.siblingsProgressing.length >= 2)
            kind = "exercise";
    }
    const advice = kind === "volume"
        ? `No PR in ${base.since} sessions, and ${ctx.partLabel || "this muscle"} is getting ${Math.round(ctx.volume)} sets a week against a target of ${Math.round(ctx.floor)}. That's under-training, not a ceiling — add volume before you deload.`
        : kind === "exercise"
            ? `No PR in ${base.since} sessions, but your other ${ctx.partLabel || "work for this muscle"} is still climbing. The muscle is fine; this movement has gone stale. Swap it for a variation.`
            : kind === "fatigue"
                ? `Reps are slipping and effort is climbing over ${base.since} sessions — that's fatigue, not a strength ceiling. Deload this lift, then rebuild.`
                : kind === "true"
                    ? base.advice
                    : `No PR in ${base.since} sessions, but your numbers are just noisy rather than trending down. Hold the course — no change needed yet.`;
    return { ...base, kind, advice };
}

let _lmCache = { key: null, val: null };

function lifterModelKey(hs) {
    let h = 5381;
    const n = hs.length;
    for (let i = 0; i < n; i++) {
        const e = hs[i];
        if (!e)
            continue;
        /* Editing history must invalidate every model that consumes it. Volume/setsDone alone miss
           RIR-only corrections, set-order corrections and a changed load/reps pair whose tonnage happens
           to stay equal. Fingerprint the performed-set evidence itself, including target-vs-observed RIR
           provenance, so the coach/readiness/progression views cannot keep serving the pre-edit model. */
        let sig = `${e.id}|${e.date}|${e.volume}|${e.setsDone}`;
        const perf = e.perf || {};
        for (const exId of Object.keys(perf).sort()) {
            const p = perf[exId] || {};
            sig += `|${exId}:${p.weight ?? ""}:${p.reps ?? ""}`;
            for (const st of (Array.isArray(p.sets) ? p.sets : []))
                sig += `;${st?.w ?? ""},${st?.r ?? ""},${st?.rir ?? ""},${st?.tr ?? ""},${st?.sub ? 1 : 0}`;
        }
        for (let j = 0; j < sig.length; j++)
            h = ((h * 33) ^ sig.charCodeAt(j)) >>> 0;
    }
    return `${n}:${h}`;
}

function buildLifterModel(history, perf, program) {
    const hs = history || [];
    const key = lifterModelKey(hs) + "|" + (program ? `${program.id}:${program.updatedAt || ""}:${program.weeks || ""}` : "-");
    if (_lmCache.key === key && _lmCache.val)
        return _lmCache.val;
    const effort = effortCalibration(hs);
    const trends = exerciseTrends(hs);
    const recovery = muscleRecovery(hs);
    // per-session summaries per lift, oldest→newest, for slope + plateau classification.
    // perLiftDay carries the SAME summaries split by the day they were logged on — plateau is judged
    // per day (see the plateau assembly below), because a lift trained on two days is really two
    // progressions and merging them invents stalls that neither day has.
    const perLift = {};
    const perLiftDay = {};
    for (let i = hs.length - 1; i >= 0; i--) { // history is newest-first
        const h = hs[i];
        for (const [id, p] of Object.entries(h.perf || {})) {
            const ss = Array.isArray(p.sets) && p.sets.length ? p.sets : [{ w: parseFloat(p.weight), r: parseInt(p.reps), rir: null }];
            let best = 0, bestR = 0, bestRir = null;
            for (const s of ss) {
                const w = parseFloat(s.w != null ? s.w : s.weight), r = parseInt(s.r != null ? s.r : s.reps);
                if (!(w > 0) || !(r > 0))
                    continue;
                const observedRir = observedHistoryRIR(s);
                const rirRaw = observedRir ?? 2;
                const rirAdj = clamp(rirRaw - effort.bias, 0, 6); // interpret through the calibration
                const e = e1rmRIR(convertHistoryLoad(w, h.unit, program?.config?.unit || hs[0]?.unit || "kg"), r, rirAdj);
                if (e > best) {
                    best = e;
                    bestR = r;
                    bestRir = observedRir;
                }
            }
            if (best > 0) {
                const rec = { e1rm: best, r: bestR, rir: bestRir, date: h.date };
                (perLift[id] = perLift[id] || []).push(rec);
                if (h.dayId)
                    ((perLiftDay[id] = perLiftDay[id] || {})[h.dayId] = perLiftDay[id][h.dayId] || []).push(rec);
            }
        }
    }
    const muscles = {};
    for (const m of recovery) {
        muscles[m.part] = { readiness: m.readiness, status: m.status, daysSince: m.daysSince, hoursSince: m.hoursSince, measured: m.measured, overBy: m.overBy, plannedRIR: m.plannedRIR, sets: m.sets, ...readinessBand(m.readiness, m) };
    }
    const lifts = {};
    const trendById = Object.fromEntries(trends.map(t => [t.id, t]));
    /* A lift trained on more than one day is more than one progression. See plateauSplitByDay() — the
       merged e1RM series interleaves them, and the day that structurally CANNOT set a PR (the lighter,
       higher-rep slot of a lift that also has a heavy slot) pads plateauOf()'s "sessions since PR"
       counter on every appearance. Judge each day on its own series, and keep the per-day summaries
       aligned with the per-day trends so classifyPlateau sees one day's reps/RIR, not two days' mixed. */
    /* ── CONTEXT FOR THE TWO OFF-LIFT PLATEAU CAUSES ────────────────────────────────────────────
     * Built once per model rather than per lift: `volumeLedger` walks the whole program, and calling it
     * inside a loop over every lift would turn an O(history) model build into an O(history x lifts) one.
     * Silently absent when there is no active program to read a floor from — classifyPlateau then
     * returns exactly the three kinds it always did. */
    const ledger = program ? (() => { try {
        return volumeLedger(program, Math.max(0, weeksOf(program) - 2));
    }
    catch {
        return null;
    } })() : null;
    /* Which lifts for a given muscle are still MOVING. `trend.dir` is the app's own read of a lift's
       direction, so this asks the same question the trend display answers rather than a second one. */
    /* ⚠ DERIVED FROM THE e1RM SERIES, NOT FROM A `dir` FIELD. My first version filtered on `t.dir ===
       "up"`, which does not exist on a trend — exerciseTrends returns {id, name, part, sessions[]} and
       nothing else. Every lift silently failed the filter, so `siblingsProgressing` was always empty
       and the exercise-specific kind could never fire. It compiled, ran, and did nothing.
       A lift counts as still moving when its most recent session beats the best of the window before
       it: the same e1RM series plateauOf reads, asked the opposite question. Six sessions to match the
       window plateauOf uses, so a lift and its siblings are judged over the same span. */
    const progressingByPart = {};
    trends.forEach(t => {
        const px = EX_BY_ID[t.id];
        const ss = (t && t.sessions) || [];
        if (!px || ss.length < 3)
            return;
        const win = ss.slice(-6);
        const latest = win[win.length - 1] && win[win.length - 1].best;
        const priorBest = Math.max(...win.slice(0, -1).map(x => x.best || 0));
        if (!(latest > priorBest))
            return; // no recent PR — not moving
        (progressingByPart[px.part] = progressingByPart[px.part] || []).push(t.id);
    });
    const plateauCtx = (id) => {
        const px = EX_BY_ID[id];
        if (!px || !ledger)
            return null;
        const L = landmarkOf(px.part) || {};
        return {
            volume: ledger[px.part],
            floor: L.mev,
            partLabel: (PART_LABEL[px.part] || px.part).toLowerCase(),
            siblingsProgressing: (progressingByPart[px.part] || []).filter(x => x !== id)
        };
    };
    const plateauFor = (id, mergedSets, readiness) => plateauSplitByDay(hs, id, (t, d) => classifyPlateau(t, (perLiftDay[id] || {})[d] || [], readiness, plateauCtx(id)), () => classifyPlateau(trendById[id], mergedSets, readiness, plateauCtx(id)));
    const now = Date.now();
    for (const [id, sess] of Object.entries(perLift)) {
        const ex = EX_BY_ID[id];
        const last = sess[sess.length - 1];
        // gain velocity: least-squares slope of e1RM over the trailing 6 sessions, per week
        const win = sess.slice(-6);
        let slope = 0, conf = 0;
        if (win.length >= 3) {
            const t0 = win[0].date;
            const xs = win.map(s => (s.date - t0) / (7 * 86400000)); // weeks
            const ys = win.map(s => s.e1rm);
            const mx = xs.reduce((a, b) => a + b, 0) / xs.length;
            const my = ys.reduce((a, b) => a + b, 0) / ys.length;
            let num = 0, den = 0;
            for (let i = 0; i < xs.length; i++) {
                num += (xs[i] - mx) * (ys[i] - my);
                den += (xs[i] - mx) ** 2;
            }
            slope = den > 0 ? num / den : 0;
            conf = clamp((win.length - 2) / 4, 0, 1);
        }
        lifts[id] = {
            e1rm: Math.round(last.e1rm * 10) / 10,
            e1rmSlope: Math.round(slope * 100) / 100,
            slopeConfidence: Math.round(conf * 100) / 100,
            sessions: sess.length,
            /* A history entry with no usable date yields no staleness — NOT NaN. Every entry this app
               writes carries `date` (finish() stamps Date.now()), but an IMPORTED backup is arbitrary JSON
               from another device, a hand-edit, or an older schema, and `migrateStore` deliberately does not
               drop entries for missing fields — losing a logged workout is worse than carrying an odd one.
               So a dateless entry does reach here, and `now - undefined` is NaN, which then propagates into
               anything that later reads this field. Found by fuzzing the model with corrupt-but-plausible
               history; nothing consumes `staleness` yet, so it was inert — but a NaN sitting in a model
               waiting for its first consumer is a bug with a delay on it, and "N days since you trained
               this" rendering as "NaN days" is exactly the kind of thing that ships. null means unknown,
               which is the truth. */
            staleness: Number.isFinite(last?.date) ? Math.round((now - last.date) / 86400000) : null,
            plateau: plateauFor(id, sess, ex ? muscles[ex.part]?.readiness : null)
        };
    }
    const val = { effort, lifts, muscles, builtAt: now };
    _lmCache = { key, val };
    return val;
}

const __doseMemo = new WeakMap();

function volumeResponse(history, part) {
    if (!history || !part)
        return null;
    let byPart = __doseMemo.get(history);
    if (!byPart) {
        byPart = new Map();
        __doseMemo.set(history, byPart);
    }
    if (byPart.has(part))
        return byPart.get(part);
    const WEEK = 7 * 86400000;
    const weeks = new Map(); // weekIndex -> { sets, e1: best flat-Epley e1RM }
    for (const h of history) {
        if (!h || !h.date)
            continue;
        const wk = Math.floor(h.date / WEEK);
        let rec = weeks.get(wk);
        if (!rec) {
            rec = { sets: 0, e1: 0 };
            weeks.set(wk, rec);
        }
        for (const [id, pf] of Object.entries(h.perf || {})) {
            const ex = EX_BY_ID[id];
            if (!ex || ex.part !== part)
                continue;
            const ss = (pf.sets && pf.sets.length) ? pf.sets : [{ w: pf.weight, r: pf.reps }];
            for (const t of ss) {
                if (!(t.w > 0) || !(t.r > 0) || t.warm)
                    continue;
                rec.sets++;
                const e = e1rmRIR(t.w, t.r, 0);
                if (e > rec.e1)
                    rec.e1 = e;
            }
        }
    }
    const idx = [...weeks.keys()].sort((a, b) => a - b);
    // Pair over TWO-WEEK windows, not single weeks. A week of honest training moves e1RM by well under
    // 1%, while the measurement moves in quanta — a 2.5kg plate at 80% is ~2%, one rep at the same load
    // ~2.5% — so week-over-week deltas are mostly zeros punctuated by rounding ticks, and the terciles
    // all median to nothing. Two-week windows put the signal at or above the instrument's resolution
    // while volume attribution stays clean (the window's own average weekly sets). Halves the sample
    // count, which the identifiability floor below already respects.
    const pairs = []; // { sets: avg weekly sets of window A, gainPct: e1RM change into window B, per week }
    for (let i = 0; i + 3 < idx.length; i += 2) {
        if (idx[i + 1] !== idx[i] + 1 || idx[i + 2] !== idx[i] + 2 || idx[i + 3] !== idx[i] + 3)
            continue;
        const a1 = weeks.get(idx[i]), a2 = weeks.get(idx[i + 1]);
        const b1 = weeks.get(idx[i + 2]), b2 = weeks.get(idx[i + 3]);
        const aE = Math.max(a1.e1, a2.e1), bE = Math.max(b1.e1, b2.e1);
        const aSets = (a1.sets + a2.sets) / 2;
        if (!(aSets > 0) || !(aE > 0) || !(bE > 0))
            continue;
        pairs.push({ sets: aSets, gainPct: 100 * (bE - aE) / aE / 2 }); // per-week rate
    }
    const spread = pairs.length ? Math.max(...pairs.map(p => p.sets)) - Math.min(...pairs.map(p => p.sets)) : 0;
    let out = null;
    if (pairs.length >= 6 && spread >= 5) { // ≥6 window-pairs ≈ 6 months of training
        const bySets = pairs.slice().sort((x, y) => x.sets - y.sets);
        const cut = Math.floor(bySets.length / 3);
        const terc = [bySets.slice(0, cut), bySets.slice(cut, bySets.length - cut), bySets.slice(bySets.length - cut)];
        const med = (a) => { const v = a.map(x => x.gainPct).sort((x, y) => x - y); const m = Math.floor(v.length / 2); return v.length % 2 ? v[m] : (v[m - 1] + v[m]) / 2; };
        const bins = terc.filter(t => t.length >= 2).map(t => ({
            lo: Math.min(...t.map(x => x.sets)), hi: Math.max(...t.map(x => x.sets)), gain: med(t), n: t.length
        }));
        if (bins.length >= 2) {
            const ranked = bins.slice().sort((x, y) => y.gain - x.gain);
            const clear = ranked[0].gain - ranked[1].gain >= 0.15;
            out = { part, weeks: pairs.length, spread, bins,
                best: clear ? { lo: ranked[0].lo, hi: ranked[0].hi, gain: ranked[0].gain } : null,
                confidence: Math.min(1, (pairs.length - 8) / 16) };
        }
    }
    byPart.set(part, out);
    return out;
}

function personalMav(history, part) {
    const groupMav = mavFor(part);
    if (!history || !history.length)
        return groupMav;
    const L = landmarkFor(part);
    const dr = volumeResponse(history, part);
    if (!dr || !dr.best)
        return groupMav;
    const signal = clamp(dr.best.hi, L.mev, L.mrv); // top of the lifter's best-responding band
    const c = clamp(dr.confidence || 0, 0, 1);
    return clamp(groupMav + c * (signal - groupMav), L.mev, L.mrv);
}

function volumeVerdicts(history, volume, parts) {
    if (!history || !history.length || !volume)
        return [];
    const out = [];
    for (const part of parts) {
        const planned = volume[part] || 0;
        if (planned <= 0.05)
            continue;
        const vr = volumeResponse(history, part);
        if (!vr || !vr.best)
            continue;
        const { lo, hi } = vr.best;
        const status = planned < lo - 0.5 ? "under" : planned > hi + 0.5 ? "over" : "in";
        out.push({ part, planned, lo, hi, status, weeks: vr.weeks, confidence: vr.confidence });
    }
    // the actionable ones first — a mismatch is worth a glance, a match is worth a nod
    const rank = { over: 0, under: 1, in: 2 };
    out.sort((a, b) => rank[a.status] - rank[b.status] || b.weeks - a.weeks);
    return out.slice(0, 3);
}

function coachFacts(lm, day, history) {
    const facts = [];
    if (!lm || !day)
        return facts;
    const exs = (day.exercises || []).map(id => EX_BY_ID[id]).filter(Boolean);
    // Coach copy is written for the lifter, not for the model debugger. Keep the underlying signals,
    // but translate each one into: WHAT TO DO -> WHY -> optional evidence. Facts also carry lightweight
    // applicability metadata (`exId`, `part`, `scope`, `priority`) so the live workout can show only the
    // one that matters for the exercise/set in front of you instead of dumping the whole session model.
    for (const ex of exs) {
        const lift = lm.lifts && lm.lifts[ex.id];
        const pl = lift && lift.plateau;
        if (!pl)
            continue;
        if (pl.kind === "fatigue") {
            facts.push({
                icon: "deload",
                key: `plateau-fatigue:${ex.id}`,
                exId: ex.id,
                priority: 100,
                short: `${ex.name}: back off today`,
                title: `Back off on ${ex.name} today`,
                text: "Recent sessions suggest fatigue is building. Keep the reps clean and take a small load reduction if the warm-up feels unusually heavy.",
                evidence: "Recent performance trend"
            });
        }
        else if (pl.kind === "true") {
            facts.push({
                icon: "recal",
                key: `plateau:${ex.id}`,
                exId: ex.id,
                priority: 85,
                short: `${ex.name}: use today's recalculated load`,
                title: `Use today's load on ${ex.name}`,
                text: "Progress has stalled, so today's target is based on your current strength instead of simply repeating the last session.",
                evidence: `${pl.since} sessions without a new best`
            });
        }
    }
    const tired = [];
    for (const part of new Set(exs.map(e => e.part))) {
        const m = lm.muscles && lm.muscles[part];
        if (m && m.readiness < 60)
            tired.push({ part, r: m.readiness });
    }
    tired.sort((a, b) => a.r - b.r);
    for (const t of tired) {
        const label = PART_LABEL[t.part] || t.part;
        facts.push({
            icon: "fatigue",
            key: `readiness:${t.part}`,
            part: t.part,
            priority: 92,
            short: `${label}: use the lighter target`,
            title: `Give ${label.toLowerCase()} a little more recovery`,
            text: "Today's target is already reduced. Follow it as written and don't add the weight back just because the first set feels easy.",
            evidence: `${label} recovery ${t.r}%`
        });
    }
    const eff = lm.effort;
    if (eff && eff.confidence > 0 && Math.abs(eff.bias) >= 0.4) {
        const harderThanLogged = eff.bias > 0;
        facts.push({
            icon: "cal",
            key: "effort-bias",
            scope: "session",
            priority: 60,
            short: "Trust today's load",
            title: "Trust today's prescribed load",
            text: harderThanLogged
                ? "Your sets usually end up harder than you rate them. Today's load already accounts for that, so avoid adding extra weight early."
                : "You usually have a little more left than you rate. Today's load already accounts for that, so use the listed target instead of deliberately holding back.",
            evidence: eff.n ? `Learned from ${eff.n} logged sets` : null
        });
    }
    const primary = exs.find(e => e.type === "compound") || exs[0];
    if (primary && history) {
        const sl = personalRepSlope(history, primary);
        if (Math.abs(sl - 30) >= 2.5) {
            const holdsWell = sl > 30;
            facts.push({
                icon: "curve",
                key: `rep-curve:${primary.id}`,
                exId: primary.id,
                priority: 70,
                short: `${primary.name}: use the listed reps`,
                title: holdsWell ? "You hold reps well as weight climbs" : "Your reps drop sooner as weight climbs",
                text: `The ${primary.name} target uses your own lifting history, so follow the listed reps and load instead of converting from a generic percentage chart.`,
                evidence: "Your logged sets"
            });
        }
    }
    // Volume response is valuable context in Progress, but it usually does not change the NEXT SET.
    // Keep the evidence available to diagnostics/other surfaces while explicitly keeping it out of the
    // live workout feed. The live Coach should earn screen space by changing an action now.
    if (history) {
        for (const part of new Set(exs.map(e => e.part))) {
            const vr = volumeResponse(history, part);
            if (vr && vr.best) {
                const label = PART_LABEL[part] || part;
                facts.push({
                    icon: "dose",
                    key: `volume:${part}`,
                    part,
                    live: false,
                    priority: 0,
                    short: `${label}: current volume is working`,
                    title: `${label} volume looks productive`,
                    text: `Your best recent progress has happened around ${vr.best.lo}–${vr.best.hi} hard sets per week. This plan is keeping ${label.toLowerCase()} near that range.`,
                    evidence: `${vr.weeks} weeks of your training`
                });
            }
        }
    }
    return facts;
}

function workoutPlanCoachFact(program, day, weekIndex) {
    if (!program || program.quick)
        return null;
    const cfg = program.config || {};
    const phaseRaw = String(program.nextEngine?.phase || program.nextEngine?.program?.phase || "").replace(/_/g, " ").toLowerCase();
    if (cfg.deload && weekIndex > weeksOf(program) || phaseRaw.includes("deload") || phaseRaw.includes("recovery")) {
        return { icon: "plan", short: "Today: recover", title: "Today is a recovery session", text: "Keep the work easy, leave plenty in reserve, and finish feeling better than you started." };
    }
    if (phaseRaw.includes("peak")) {
        return { icon: "plan", short: "Today: crisp heavy reps", title: "Today: prioritize crisp heavy reps", text: "Take the full rest, keep technique tight, and stop before reps turn into grinders." };
    }
    if (phaseRaw.includes("strength") || cfg.goal === "strength") {
        return { icon: "plan", short: "Today: quality strength work", title: "Today: build strength", text: "Use the listed load and rest. Keep every rep controlled and save your hardest effort for the sets that call for it." };
    }
    if (phaseRaw.includes("hypertrophy") || phaseRaw.includes("accumulation") || cfg.goal === "muscle") {
        return { icon: "plan", short: "Today: build quality volume", title: "Today: build quality volume", text: "Use the listed reps, rest, and effort. Keep the early sets controlled so you can finish the exercise strong." };
    }
    const ph = phaseFor(program, weekIndex);
    if (ph === PHASES.peak)
        return { icon: "plan", short: "Today: crisp heavy reps", title: "Today: prioritize crisp heavy reps", text: "Take the full rest, keep technique tight, and stop before reps turn into grinders." };
    if (ph === PHASES.intensification)
        return { icon: "plan", short: "Today: heavier, controlled work", title: "Today: push the working sets", text: "Loads are heavier now. Keep form clean and avoid spending extra effort on the early sets." };
    return { icon: "plan", short: "Today: build quality volume", title: "Today: build quality volume", text: "Keep the prescribed reps controlled and leave enough in reserve to maintain quality across the whole session." };
}

function liveCoachForContext(facts, row, ex, sessionDone) {
    if (!row || !ex)
        return [];
    const work = (row.sets || []).filter(s => !s.warm && !s.sub);
    const done = work.filter(s => s.done);
    const next = work.find(s => !s.done);
    if (done.length && next) {
        const last = [...work].reverse().find(s => s.done && s.actualRIR != null);
        if (last) {
            const actual = Number(last.actualRIR);
            const target = prescribedRIRof(last);
            if (Number.isFinite(actual) && Number.isFinite(target)) {
                if (actual <= target - 1) {
                    return [{
                            icon: "fatigue",
                            key: `set-hard:${ex.id}:${done.length}`,
                            context: "next-set",
                            short: "Next set: don't add weight",
                            title: "That set was harder than planned",
                            text: "Keep the next set at the listed load. If your form or rep speed drops, take a small reduction instead of forcing it.",
                            evidence: `You logged ${actual} RIR · target ${target}`
                        }];
                }
                if (actual >= target + 2) {
                    return [{
                            icon: "cal",
                            key: `set-easy:${ex.id}:${done.length}`,
                            context: "next-set",
                            short: "Next set: stay with the plan",
                            title: "That set was easier than planned",
                            text: "Use the next set as listed rather than jumping ahead. If it stays this easy, that is useful evidence for your next comparable workout.",
                            evidence: `You logged ${actual} RIR · target ${target}`
                        }];
                }
            }
        }
        return [];
    }
    if (done.length || !next)
        return [];
    const relevant = (facts || []).filter(f => {
        if (!f || f.live === false)
            return false;
        if (f.exId)
            return f.exId === ex.id;
        if (f.part)
            return f.part === ex.part;
        return f.scope === "session" && sessionDone === 0;
    }).sort((a, b) => (b.priority || 0) - (a.priority || 0));
    return relevant.length ? [{ ...relevant[0], context: "exercise-start" }] : [];
}

const PROG_POLICIES = Object.freeze({ next: Object.freeze({ id: "pursuit-next" }) });

const MYO_MINI_SETS = 3;

const MYO_MAX_SETS = 5;

const MYO_MINI_REPS = 5;

const MYO_MIN_REPS = 3;

const MYO_TARGET_REPS = `${MYO_MIN_REPS}-${MYO_MINI_REPS}`;

function makeMyoMini(w, prescribed = true) {
    const ws = w != null && w !== "" ? String(w) : "";
    return { weight: ws, reps: String(MYO_MINI_REPS), myo: true, sub: true, kind: "myo", done: false,
        ...(prescribed ? { prescribed: true } : {}),
        target: { w: ws || "\u2014", reps: MYO_TARGET_REPS, myo: true } };
}

function growMyoSets(sets) {
    if (!Array.isArray(sets) || !sets.length)
        return sets;
    let lastMyo = -1;
    let count = 0;
    sets.forEach((s, i) => { if (s && s.sub && s.kind === "myo") {
        lastMyo = i;
        count++;
    } });
    if (lastMyo < 0 || count >= MYO_MAX_SETS)
        return sets;
    const last = sets[lastMyo];
    if (!last.done)
        return sets;
    const reps = parseInt(last.reps);
    if (!(reps >= MYO_MIN_REPS))
        return sets; // couldn't hold the range — the extension is over
    /* NO LOAD GUARD. An earlier version refused to grow unless the last mini carried a positive
       weight, which silently disabled the whole rule for BODYWEIGHT and ASSISTED work — and for any
       loaded lift with no history yet, where the row opens blank because there is nothing to suggest.
       The DOM gate caught it: three minis went in, the last was logged at the target reps, and nothing
       appended. A blank load is a load the lifter has not typed yet, not a reason to end the set.
       syncSubSets fills the new row from the activation set on the same pass anyway, so carrying the
       last mini's weight through — blank or not — is both correct and self-correcting. */
    return [...sets.slice(0, lastMyo + 1), makeMyoMini(last.weight, !!last.prescribed), ...sets.slice(lastMyo + 1)];
}

function syncSubSets(sets, ex, unit) {
    if (!Array.isArray(sets) || !sets.length)
        return sets;
    const step = loadStep(ex, unit);
    let actW = null; // load of the working set these extensions hang off
    let changed = false;
    const out = sets.map(s => {
        if (!s)
            return s;
        if (!s.sub) {
            if (!s.warm) {
                const w = parseFloat(s.weight);
                if (w > 0)
                    actW = w;
            }
            return s;
        }
        if (s.done || userOwnsRuntimeSet(s) || !(actW > 0))
            return s;
        let want;
        if (s.kind === "drop") {
            if (!(s.dropF > 0))
                return s; // legacy row with no recorded fraction — don't invent one
            want = String(Math.max(step, roundTo(actW * s.dropF, step)));
        }
        else {
            want = String(actW); // myo mini: the activation load, unchanged
        }
        if (String(s.weight) === want && String(s.target?.w) === want)
            return s;
        changed = true;
        return { ...s, weight: want, target: { ...(s.target || {}), w: want } };
    });
    return changed ? out : sets;
}

function prescribeSets(program, day, ex, slot, weekIndex, unit, sug, dayPerf, perf, history, withWarm) {
    void dayPerf;
    void perf;
    const cell = computeCell(program, day, ex.id, slot, weekIndex);
    if (!cell || cell.missing || !(Number(cell.sets) > 0))
        return [];
    const request = program?.nextEngine?.request || program?.nextEngine?.baseRequest;
    const percentage = percentagePlanFor(program, day, ex, slot, weekIndex, unit, history);
    const targets = buildRuntimeSetTargets({
        exerciseId: resolveNextShellExerciseId(program, day, slot, ex) || ex.id,
        cell,
        workingLoad: percentage?.sets[0]?.weight ?? sug?.weight ?? null,
        suggestedReps: sug?.target ?? null,
        setTargets: sug?.setTargets,
        includeWarmups: !!withWarm,
        loadingInventory: request?.equipment?.loading,
        equipmentAvailable: request?.equipment?.available,
        snapLoad: value => loadableAtOrBelow(ex, value, unit)
    });
    const range = cellRepRange(cell, program, ex, slot === day.primaryIndex);
    const rangeText = range[0] === range[1] ? String(range[0]) : `${range[0]}-${range[1]}`;
    const rirText = cell.rir ?? null;
    let rows = targets.map(t => ({
        weight: t.weight == null ? "" : String(t.weight),
        reps: String(t.reps),
        warm: t.kind === "warmup",
        done: false,
        auto: true,
        valueOwner: "prescription",
        target: t.kind === "warmup"
            ? { w: t.weight == null ? "—" : String(t.weight), reps: String(t.reps), rir: null }
            : { w: t.weight == null ? "—" : String(t.weight), reps: rangeText, rir: rirText, nextAction: sug?.action || "initial", confidence: sug?.confidence || null, prefillReps: String(t.reps) }
    }));
    if (percentage) {
        rows = [...rows.filter(r => r.warm), ...percentage.sets.map(t => ({
            weight: String(t.weight), reps: String(t.reps).replace('+', ''), warm: false, done: false,
            auto: true, valueOwner: 'prescription',
            target: { w: String(t.weight), reps: String(t.reps).replace('+', ''),
                rir: t.amrap ? '0' : rirText, amrap: !!t.amrap,
                nextAction: 'percentage', confidence: null, prefillReps: String(t.reps).replace('+', '') }
        }))];
    }
    // Advanced-technique selection is engine-owned; this shell helper only realizes the engine's cue
    // as loggable rows. The protocol itself is now defined in next-engine/workout-runtime.ts.
    const protocol = techniqueProtocolFromCell(cell);
    if (protocol.type === 'myo_reps') {
        let lastWork = -1;
        for (let i = rows.length - 1; i >= 0; i--)
            if (!rows[i].warm) {
                lastWork = i;
                break;
            }
        if (lastWork >= 0) {
            const base = rows[lastWork];
            const mini = protocol.miniSets;
            for (let i = 0; i < (mini?.minimum || 0); i++)
                rows.push({
                    weight: base.weight, reps: String(mini?.targetReps || 5), warm: false, sub: true, myo: true,
                    kind: 'myo', prescribed: true, done: false, auto: true, valueOwner: "prescription",
                    target: { w: base.weight || '—', reps: String(mini?.targetReps || 5), rir: null, rest: mini?.restSeconds || 15 }
                });
        }
    }
    else if (protocol.type === 'drop_set') {
        let lastWork = -1;
        for (let i = rows.length - 1; i >= 0; i--)
            if (!rows[i].warm) {
                lastWork = i;
                break;
            }
        if (lastWork >= 0) {
            const base = Number(rows[lastWork].weight) || 0;
            for (const drop of protocol.drops || [])
                rows.push({
                    weight: base > 0 ? String(Math.round(base * drop.fraction * 100) / 100) : '', reps: '', warm: false,
                    sub: true, kind: 'drop', dropF: drop.fraction, prescribed: true, done: false, auto: true, valueOwner: "prescription",
                    target: { w: base > 0 ? String(Math.round(base * drop.fraction * 100) / 100) : '—', reps: 'to failure', rir: null, rest: drop.restSeconds }
                });
        }
    }
    return rows;
}

function loggedExercisePerformance(program, day, e, weekIndex, unit, perf, history) {
    const failedRows = program.config?.percentScheme === 'gzclp' ? e.sets.filter(x => x.done
        && !x.warm && !x.sub && parseInt(x.reps) === 0 && parseFloat(x.weight) > 0) : [];
    const s = summarizeSets(e.sets, EX_BY_ID[e.id], unit) || (failedRows.length
        ? { weight: Math.max(...failedRows.map(x => parseFloat(x.weight))), reps: 0 } : null);
    const note = e.note.trim() || (perf[e.id] && perf[e.id].note) || undefined;
    if (s) {
        const doneWork = e.sets.filter(x => x.done && !x.warm && (parseInt(x.reps) > 0 || (failedRows.includes(x))));
        const hasPos = doneWork.some(x => parseFloat(x.weight) > 0);
        const wv = x => { const w = parseFloat(x.weight); return isNaN(w) ? 0 : w; };
        // Persist the amrap flag from the prescription onto each logged set. Percent-scheme
        // progression (5/3/1 TM projection, GZCLP stage advancement) needs to identify the
        // AMRAP set directly rather than inferring it from being the unique top-weight set —
        // that heuristic silently breaks if a manually-edited weight ties with another set.
        // RIR: prefer the explicitly-logged actual RIR; otherwise fall back to the set's target
        // RIR so the LAST TIME column still shows the intended effort (most users just tick the
        // set done without tapping an RIR, which previously left the history with no effort at all).
        const setRIR = (x) => {
            if (x.actualRIR != null)
                return x.actualRIR;
            const t = x.target?.rir;
            const n = typeof t === "number" ? t : parseRIRNum(t);
            return Number.isFinite(n) ? n : null;
        };
        const logged = (hasPos ? doneWork.filter(x => parseFloat(x.weight) > 0) : doneWork)
            .map(x => {
            const r = setRIR(x);
            // Snapshot what was ASKED for alongside what was done: pw = prescribed weight, pt = the
            // prescribed rep target. Self-contained per set, so a logged session can be replayed and
            // scored against a different progression without needing the program that produced it.
            // Omitted for manual/freestyle sets, where nothing was prescribed.
            const pw = parseFloat(x.target?.w);
            const pt = x.target?.reps != null ? String(x.target.reps) : null;
            // PROVENANCE TRAVELS WITH THE SET. `sub` marks a drop set or myo mini — an extension of
            // the set above, not a working set. Every reader downstream already filters on it
            // (`!s.warm && !s.sub`), but the flag was never PERSISTED, so on replayed history those
            // filters matched nothing and extensions counted as full sets all over again — the exact
            // bug v493 fixed in the live session, surviving in the log.
            return { ...historyExposureContext(x), w: wv(x), r: parseInt(x.reps), ...(failedRows.includes(x) ? { failedAttempt: true } : {}), ...(r != null ? { rir: r, rirReported: x.actualRIR != null } : {}), ...(x.sub ? { sub: true, ...(x.kind ? { kind: x.kind } : {}) } : {}), ...(prescribedRIRof(x) != null ? { tr: prescribedRIRof(x) } : {}), ...(x.target?.amrap ? { amrap: true } : {}), ...(x.auto && pw > 0 ? { pw } : {}), ...(!x.target?.freestyle && pt ? { pt } : {}) };
        });
        const prescription = snapshotNextShellPrescription(program, day, e.slot, EX_BY_ID[e.id], weekIndex)
            || { schemaVersion: 1, exerciseId: e.id, ...computeCell(program, day, e.id, e.slot, weekIndex) };
        const percentage = percentagePlanFor(program, day, EX_BY_ID[e.id], e.slot, weekIndex, unit, history);
        if (percentage) prescription.protocol = { scheme: program.config.percentScheme,
            ...(percentage.tier ? { tier: percentage.tier, stage: percentage.stage } : {}) };
        if (percentage) prescription.setTargets = percentage.sets.map(t => ({
            reps: Number.parseInt(t.reps), weight: t.weight, unit, amrap: !!t.amrap,
            rir: t.amrap ? 0 : parseRIRNum(prescription.rir)
        }));
        return { ...historyExposureContext(e), weight: s.weight, reps: s.reps, date: Date.now(), sets: logged, note,
            ...(prescription ? { prescription } : {}) };
    }
    return null;
}

function loggedWorkoutPerformance(program, day, data, weekIndex, unit, perf, history) {
    return Object.fromEntries(data.map(e => [e.id, loggedExercisePerformance(program, day, e, weekIndex, unit, perf, history)])
        .filter(([, value]) => value !== null));
}

function auditSets(o) {
    const { program, day, ex, slot, weekIndex, unit, perf, history, dayPerf } = o;
    const out = [];
    const at = (rule, severity, detail, setIndex = null) => out.push({ rule, severity, detail, setIndex, exId: ex.id, exName: ex.name, dayId: day.id, dayLabel: day.label, weekIndex });
    const isPrimary = slot === day.primaryIndex;
    let sets, sug, range;
    /* `__sets` is a TEST HOOK, following the `__poseJoints` precedent already in the renderer: it lets
       gates/setaudit.mjs re-run these rules over a deliberately DAMAGED copy of a real prescription, so
       each rule is proven to catch its own defect without mutating the engine. Never set in the app —
       when absent, the prescription is computed exactly as before. A clean audit that has never been
       shown to fail is worth nothing, and this is what makes the proof cheap enough to keep. */
    try {
        /* ⚠ THE PHASE RULE IS COMPUTED IN TWO PLACES AND THEY DISAGREED. `computeCell` opts percent-scheme
           and manual programs OUT of block periodization (`phase = null` -> static range); this line asked
           `blockPhase` unconditionally. So a lifter on Main-Lift Waves was PRESCRIBED from one range and
           SHOWN another: Triceps Dip target 4 reps against a printed range of 6-10, and the in-app
           self-test reported it as 56 findings on that template alone.
           The prescription is the one that must win — it is what the lifter performs — so the display
           follows the same opt-out. Present since the block-phase work, not a recent regression. */
        const cell = computeCell(program, day, ex.id, slot, weekIndex);
        range = cellRepRange(cell, program, ex, isPrimary);
        sug = sessionSuggestion(program, day, slot, dayPerf || perf, unit, weekIndex, history);
        sets = o.__sets || prescribeSets(program, day, ex, slot, weekIndex, unit, sug, dayPerf || perf, perf, history, true);
    }
    catch (e) {
        at("threw", "error", `prescribing this exercise threw: ${e.message}`);
        return out;
    }
    if (!Array.isArray(sets) || !sets.length) {
        at("no-sets", "error", "no sets were prescribed at all");
        return out;
    }
    const step = loadStep(ex, unit) || 0;
    const rack = gymRackFor(ex, unit);
    const ceiling = gymCapFor(ex, unit);
    const num = (v) => (v === "" || v == null ? null : Number(v));
    const work = sets.filter(isWorkSet);
    const workW = work.map(s => num(s.weight)).filter(w => w != null && w > 0);
    const topWork = workW.length ? Math.max(...workW) : null;
    sets.forEach((s, i) => {
        const w = num(s.weight), r = num(s.reps);
        /* A LOAD MUST BE A NUMBER. NaN and Infinity reach the screen as blank or "Infinity" and are the
           single most visible class of engine failure. */
        if (w != null && !Number.isFinite(w))
            at("load-not-finite", "error", `weight is ${s.weight}`, i);
        if (w != null && Number.isFinite(w) && w < 0)
            at("load-negative", "error", `weight is ${w}`, i);
        if (r != null && !Number.isFinite(r))
            at("reps-not-finite", "error", `reps is ${s.reps}`, i);
        if (r != null && Number.isFinite(r) && r <= 0 && !s.amrap)
            at("reps-nonpositive", "error", `reps is ${r}`, i);
        /* A LOAD MUST BE LOADABLE. If the smallest jump on this implement is 5, a prescription of 137.5
           is a number the lifter cannot put on the bar. Rack-based implements are exempt because their
           available loads are a fixed list, not a multiple — checked separately below. */
        if (w != null && Number.isFinite(w) && w > 0 && step > 0 && !(rack && rack.length)) {
            const rem = Math.abs(w / step - Math.round(w / step));
            if (rem > 1e-6)
                at("load-not-loadable", "error", `${w} ${unit} is not a multiple of the ${step} ${unit} increment`, i);
        }
        if (w != null && Number.isFinite(w) && w > 0 && rack && rack.length) {
            if (!rack.some(v => Math.abs(v - w) < 1e-6))
                at("load-not-on-rack", "warn", `${w} ${unit} is not one of the available weights`, i);
        }
        if (w != null && Number.isFinite(ceiling) && ceiling > 0 && w > ceiling + 1e-6) {
            at("load-over-gym-ceiling", "error", `${w} ${unit} exceeds the heaviest available ${ceiling} ${unit}`, i);
        }
        /* A REP TARGET MUST SIT IN THE RANGE PRINTED BESIDE IT. Warm-ups, myo minis and drop sets are
           short by design and an AMRAP has no ceiling — those are intended and are not findings. */
        /* ⚠ A NAMED PERCENT SCHEME OWNS ITS OWN REPS, AND THE GENERIC RANGE DOES NOT APPLY TO IT. 5's PRO
           prescribes FIVE reps on every main set, every week, at rising percentages of training max —
           that is the programme, not a mistake. The generic target narrows to 2-3 in the peak week, so
           this check reported the template doing exactly what it is supposed to do: "Deadlift wk4 — asks
           for 5 reps but the target range is 2-3". A scheme-governed main lift is exempt; its accessories
           are NOT, and neither is any lift on a program without a scheme. */
        const schemeOwned = !!program?.config?.percentScheme
            && (isPrimary || slot === day?.t2Index);
        if (isWorkSet(s) && !s.amrap && !schemeOwned && r != null && Number.isFinite(r) && range && range.length === 2) {
            const [lo, hi] = range;
            if (r < lo || r > hi)
                at("reps-outside-range", "error", `asks for ${r} reps but the target range is ${lo}-${hi}`, i);
        }
        /* RIR MUST BE SANE. A target of 12 reps in reserve is not a working set.
           PARSED WITH `parseRIRNum`, NOT `Number` — a target RIR is legitimately a RANGE string ("0-1"),
           which `Number` turns into NaN. My first version of this rule used Number and reported 55,782
           findings across 2,484 programs, every one of them my own bug rather than the app's. A rule that
           fires on almost everything is a broken rule, not a discovery: check the shape of the value
           before believing the count. */
        const rirRaw = s.target && s.target.rir != null ? s.target.rir : null;
        const rir = rirRaw != null ? parseRIRNum(rirRaw) : null;
        if (rirRaw != null && (rir == null || !Number.isFinite(rir) || rir < 0 || rir > 6))
            at("rir-implausible", "error", `target RIR is ${rirRaw}`, i);
        /* WHAT IS SHOWN MUST BE WHAT IS TARGETED. The set's own target block is what the UI prints under
           the load; if it disagrees with the load itself, two numbers on one screen describe one set. */
        if (s.target && s.target.w != null && w != null && Number.isFinite(w)) {
            const tw = num(s.target.w);
            if (tw != null && Number.isFinite(tw) && Math.abs(tw - w) > 1e-6)
                at("target-disagrees-with-load", "error", `set shows ${w} but its target says ${tw}`, i);
        }
    });
    /* A WARM-UP RAMP MUST CLIMB, AND MUST STAY UNDER THE WORK SET. A warm-up at or above the working
       load is the clearest possible sign the ramp was built from the wrong number. */
    const warms = sets.filter(s => s.warm).map(s => num(s.weight)).filter(w => w != null && w > 0);
    for (let i = 1; i < warms.length; i++) {
        if (warms[i] < warms[i - 1])
            at("warmup-not-ascending", "error", `warm-up drops from ${warms[i - 1]} to ${warms[i]}`, i);
    }
    if (topWork != null && warms.some(w => w > topWork + 1e-6)) {
        at("warmup-heavier-than-work", "error", `a warm-up (${Math.max(...warms)}) is heavier than the work set (${topWork})`);
    }
    /* AN EXTENSION HANGS OFF THE SET BEFORE IT. A myo mini or drop set carrying MORE load than the set
       that produced it describes something that cannot happen. */
    sets.forEach((s, i) => {
        if (!s.sub || i === 0)
            return;
        let j = i - 1;
        while (j >= 0 && sets[j].sub)
            j--;
        const parent = j >= 0 ? num(sets[j].weight) : null, mine = num(s.weight);
        if (parent != null && mine != null && Number.isFinite(parent) && Number.isFinite(mine) && mine > parent + 1e-6) {
            at("extension-heavier-than-parent", "error", `an extension set carries ${mine} but hangs off a set of ${parent}`, i);
        }
    });
    /* WORK SETS MUST EXIST. A prescription that is entirely warm-up is not a workout. */
    if (!work.length)
        at("no-work-sets", "error", "every prescribed set is a warm-up or an extension");
    return out;
}

function auditProgramWeek(program, weekIndex, opts) {
    const out = [];
    const o = opts || {};
    const add = (rule, detail, severity = "advisory") => out.push({ rule, severity, detail, weekIndex });
    let vol;
    try {
        vol = weeklyVolume(program, weekIndex);
    }
    catch (e) {
        add("volume-threw", e.message, "error");
        return out;
    }
    /* A WEEK WITH NO PULLING IN IT. Measured across 210 generated programs: 25 of them — 12% — contain
       ZERO volume for an entire movement pattern, and it is ALWAYS pull. Concentrated in short sessions
       (15 at s20, 8 at s40, 2 at s60) but not confined to them: a 2-day full body at s60 trains no back
       at all for the whole week. That is not a volume shortfall to be argued about, it is a program
       missing half of training, and nothing in the app noticed. This is the check that notices.

       GROUPS, THE THRESHOLD AND THE TRAINABILITY TEST NOW LIVE IN ONE PLACE (`PATTERN_GROUPS`,
       `PATTERN_MIN_SETS`, `patternTrainable`) because the GENERATOR reads them too as of engine 6.
       They were written here first; a private copy on each side is the v584 divergence class, and it
       matters more than usual here — a generator that fills to one definition while the audit reports
       against another produces a program that is green on screen and broken in the file, or the
       reverse. Behaviour is unchanged from the inlined version. */
    const kit = (() => { try {
        return expandEquipment((program.config && program.config.equipment) || []);
    }
    catch {
        return null;
    } })();
    Object.entries(PATTERN_GROUPS).forEach(([name, parts]) => {
        const total = parts.reduce((s, m) => s + (vol[m] || 0), 0);
        if (total >= PATTERN_MIN_SETS)
            return;
        if (!patternTrainable(parts, kit)) {
            add("pattern-unavailable", `no ${name} volume — this equipment offers no way to train it`, "info");
            return;
        }
        add("pattern-absent", `the whole week contains no ${name} volume`, "error");
    });
    /* A MUSCLE THE LIFTER ASKED FOR MUST ACTUALLY BE TRAINED. Muscles nobody prioritised falling under
       MEV is arithmetic, not a defect — a 40-minute session cannot give seventeen muscles eight sets
       each, and the app already tracks that trade-off as partsUnderMEV. A muscle the lifter explicitly
       FOCUSED is a different claim: the app said it would emphasise it. */
    const focused = Array.isArray(o.focusList) ? o.focusList : Object.keys((program.config && program.config.focus) || {});
    focused.forEach(part => {
        const mev = landmarkFor(part).mev, sets = vol[part] || 0;
        if (sets < mev)
            add("focus-under-mev", `${PART_LABEL[part] || part} is emphasised but gets ${sets.toFixed(1)} sets, under its ${mev}-set minimum`);
    });
    /* AND NOTHING MAY EXCEED THE CEILING THE ENGINE ITSELF ENFORCES. compositeMrv, not the raw
       landmark — see the note above. */
    Object.entries(vol).forEach(([part, sets]) => {
        if (!Number.isFinite(sets) || sets <= 0)
            return;
        if (!landmarkFor(part))
            return;
        const ceiling = compositeMrv(part);
        if (Number.isFinite(ceiling) && sets > ceiling + 0.5)
            add("over-engine-ceiling", `${PART_LABEL[part] || part} at ${sets.toFixed(1)} sets exceeds the planned ${ceiling}-set ceiling`);
    });
    return out;
}

function simulateAndAudit(o) {
    const { program, unit = "lb", weeks = null, behaviour = "hit" } = o || {};
    const findings = [];
    const history = [];
    let perf = {};
    const total = weeks || weeksOf(program);
    for (let w = 1; w <= total; w++) {
        program.days.forEach(day => {
            /* ⚠ ASK WHAT THE SLOT HOLDS **THIS WEEK**. A paired slot trains a different exercise on
               alternate weeks, so reading `day.exercises[slot]` audits and logs the wrong lift on half of
               them. `exerciseAt` is the one answer to that question; it returns the slot's own exercise
               when no pair is set, so this is inert until pairing ships. */
            day.exercises.forEach((_id, slot) => {
                const id = exerciseAt(program, day, slot, w);
                const ex = EX_BY_ID[id];
                if (!ex)
                    return;
                findings.push(...auditSets({ program, day, ex, slot, weekIndex: w, unit, perf, history, dayPerf: perf }));
            });
            // Log the day, so the NEXT week is prescribed from something the lifter did.
            const logged = {};
            day.exercises.forEach((_id, slot) => {
                const id = exerciseAt(program, day, slot, w);
                const ex = EX_BY_ID[id];
                if (!ex)
                    return;
                let sets = null, sug = null;
                try {
                    sug = sessionSuggestion(program, day, slot, perf, unit, w, history);
                    sets = prescribeSets(program, day, ex, slot, w, unit, sug, perf, perf, history, false);
                }
                catch {
                    return;
                }
                const work = (sets || []).filter(isWorkSet);
                if (!work.length)
                    return;
                /* SEED A STARTING LOAD ON THE FIRST SESSION. Without this the simulation is VACUOUS and looks
                   clean: with no history the engine has nothing to progress from and prescribes a blank load,
                   the simulated lifter logs 0, and every later week reads that 0 as the last performance — so
                   the lifter never lifts anything, no warm-up ramp is ever built, and the audit sweeps a
                   program in which no weight was ever prescribed. It reported ZERO findings across 2,484
                   programs and 1.5 million sets while three separate injected defects walked straight past it.
                   Caught by mutation testing, which is the only reason it was caught at all. */
                const rawSeed = ex.equip.includes("barbell") || ex.equip.includes("ezbar") || ex.equip.includes("smith") ? 95
                    : ex.equip.includes("dumbbell") ? 30
                        : ex.equip.includes("machine") || ex.equip.includes("cable") ? 60
                            : ex.equip.length === 0 ? 0 : 45;
                /* THE SEED MUST ITSELF BE LOADABLE. The first version used a flat 45, which is not a multiple
                   of the 10 lb step on a hack squat or a leg press — so the harness fabricated a load no
                   lifter could set, the engine faithfully carried it forward, and the audit reported 40
                   "load-not-loadable" findings that were entirely its own doing. An auditor must not
                   manufacture inputs that violate the invariant it is checking; that is how a sweep produces
                   confident nonsense. Snapped to the exercise's own increment. */
                const seedStep = loadStep(ex, unit) || 0;
                const seed = seedStep > 0 ? Math.max(seedStep, Math.round(rawSeed / seedStep) * seedStep) : rawSeed;
                const wt = Number(work[0].weight) || (perf[id] ? Number(perf[id].weight) : 0) || seed;
                const target = Number(work[0].reps) || 8;
                const reps = behaviour === "miss" ? Math.max(1, target - 3) : behaviour === "stall" ? target : target;
                const useW = behaviour === "stall" ? (perf[id] ? Number(perf[id].weight) || wt : wt) : wt;
                logged[id] = { weight: useW, reps, sets: work.map(() => ({ w: useW, r: reps, rir: 2 })), date: Date.now() };
            });
            if (Object.keys(logged).length) {
                history.unshift({ id: `sim-${w}-${day.id}`, programId: program.id, dayId: day.id, dayLabel: day.label, weekIndex: w, date: Date.now() - (total - w) * 86400000, perf: logged, volume: 1000, setsDone: 3 });
                perf = { ...perf, ...logged };
            }
        });
    }
    return findings;
}

function weekKeyOf(d) {
    const dt = new Date(d);
    const off = (dt.getDay() + 6) % 7; // Monday-based: Sunday is 6, not 0
    const m = new Date(dt);
    m.setDate(dt.getDate() - off);
    m.setHours(0, 0, 0, 0);
    return m.getTime();
}

function sessionE1RM(p) {
    if (!p)
        return 0;
    const sets = Array.isArray(p.sets) ? p.sets.filter(s => s && s.w > 0 && s.r > 0) : [];
    if (sets.length)
        return Math.max(...sets.map(s => e1rm(s.w, s.r)));
    return (p.weight > 0 && p.reps > 0) ? e1rm(p.weight, p.reps) : 0;
}

function setsOf(p) {
    if (!p)
        return [];
    if (Array.isArray(p.sets) && p.sets.length)
        return p.sets.filter(x => x && typeof x === "object");
    return p.weight != null ? [{ w: p.weight, r: p.reps }] : [];
}

function e1rm(w, reps) { return Math.round(e1rmRIR(w, reps || 0, 0)); }

function lastTopSet(p) {
    const sets = setsOf(p).filter(s => s && s.w > 0 && !s.warm && !s.sub);
    if (!sets.length)
        return p && p.weight > 0 ? { w: p.weight, r: p.reps != null ? p.reps : null, rir: null, summary: true } : null;
    const score = s => (s.r > 0 ? e1rm(s.w, s.r) : s.w);
    const best = sets.reduce((m, s) => (score(s) > score(m) ? s : m), sets[0]);
    return { w: best.w, r: best.r != null ? best.r : null, rir: best.rir != null ? best.rir : null };
}

function betterTopSet(a, b) {
    if (!a)
        return b;
    if (!b)
        return a;
    if ((b.w || 0) > (a.w || 0))
        return b;
    if ((b.w || 0) === (a.w || 0) && (b.r || 0) > (a.r || 0))
        return b;
    return a;
}

const EPLEY_SLOPE = 30;

const E1RM_REP_CAP = 12;

const ASSUMED_RIR = 2;

function e1rmRIR(w, reps, rir, cap = E1RM_REP_CAP, slope = EPLEY_SLOPE) { return w * (1 + (Math.min(reps, cap) + rir) / slope); }

function isWorkSet(s) { return !!s && !s.warm && !s.sub; }

function exRecords(history, id, unit = "kg") {
    let maxWeight = null, maxE1rm = null, maxVol = null;
    const perRep = {}; // reps → { w, date }
    (Array.isArray(history) ? history : []).forEach(h => {
        const p = h.perf?.[id];
        if (!(p && p.weight > 0))
            return;
        const u = unit;
        const sets = completedHistorySets(p).map(x => ({ ...x, w: convertHistoryLoad(x.w, h.unit, unit) }));
        sets.forEach(s => {
            if (!(s.w > 0) || s.sub)
                return; // exclude sub-sets (myo-reps, drop sets) from records
            if (betterTopSet(maxWeight, { w: s.w, r: s.r }) !== maxWeight)
                maxWeight = { w: s.w, r: s.r, date: h.date, unit: u, sourceUnit: h.unit || unit };
            if (s.r != null) {
                const e = e1rm(s.w, s.r);
                if (!maxE1rm || e > maxE1rm.v)
                    maxE1rm = { v: e, date: h.date, unit: u, sourceUnit: h.unit || unit };
                const vol = s.w * s.r;
                if (!maxVol || vol > maxVol.v)
                    maxVol = { v: vol, date: h.date, unit: u, sourceUnit: h.unit || unit };
                if (!perRep[s.r] || s.w > perRep[s.r].w)
                    perRep[s.r] = { w: s.w, date: h.date, unit: u, sourceUnit: h.unit || unit };
            }
        });
    });
    const originalUnits = record => {
        if (!record) return record;
        const { sourceUnit, ...out } = record;
        if (out.w != null) out.w = convertHistoryLoad(out.w, unit, sourceUnit);
        if (out.v != null) out.v = convertHistoryLoad(out.v, unit, sourceUnit);
        return { ...out, unit: sourceUnit };
    };
    return { maxWeight: originalUnits(maxWeight), maxE1rm: originalUnits(maxE1rm),
        maxVol: originalUnits(maxVol), perRep: Object.fromEntries(Object.entries(perRep).map(([r, v]) => [r, originalUnits(v)])) };
}

const EX_WINDOWS = [
    { id: "1W", label: "1W", days: 7 },
    { id: "1M", label: "1M", days: 30 },
    { id: "3M", label: "3M", days: 91 },
    { id: "6M", label: "6M", days: 182 },
    { id: "1Y", label: "1Y", days: 365 },
    { id: "all", label: "All", days: null },
];

const EX_METRICS = [
    { id: "e1rm", label: "1-RM", reps: 1, kind: "load", title: "Estimated 1-rep max", blurb: "Your best set each session, converted to a one-rep max — the cleanest single measure of strength." },
    { id: "e3rm", label: "3-RM", reps: 3, kind: "load", title: "Estimated 3-rep max", blurb: "What your best set implies you could hold for three reps. An estimate, not a set you performed." },
    { id: "e10rm", label: "10-RM", reps: 10, kind: "load", title: "Estimated 10-rep max", blurb: "What your best set implies you could hold for ten reps — the load your hypertrophy work sits near." },
    { id: "top", label: "Heaviest weight", kind: "load", title: "Heaviest weight", blurb: "The heaviest load you actually put in your hands each session. No estimate involved." },
    { id: "vol", label: "Total volume", kind: "vol", title: "Total volume", blurb: "Weight × reps across every working set of the session — how much work the lift did." },
    { id: "volset", label: "Volume per set", kind: "vol", title: "Volume per set", blurb: "Session volume divided by working sets — whether each set is doing more work, independent of how many you did." },
];

const EX_METRIC_BY_ID = Object.fromEntries(EX_METRICS.map(m => [m.id, m]));

const exWindow = id => EX_WINDOWS.find(w => w.id === id) || EX_WINDOWS[EX_WINDOWS.length - 1];

function rmAt(est, reps) { const r = Math.min(Math.max(reps || 1, 1), E1RM_REP_CAP); return r <= 1 ? est : est / (1 + r / EPLEY_SLOPE); }

function exerciseSeries(history, id, metricId = "e1rm", windowId = "all", now = Date.now()) {
    const metric = EX_METRIC_BY_ID[metricId] || EX_METRICS[0];
    const win = exWindow(windowId);
    const since = win.days == null ? -Infinity : now - win.days * 86400000;
    const rows = [];
    let unit = (Array.isArray(history) ? history : []).filter(h => validHistoryDate(h) && h.perf?.[id])
        .sort((a, b) => Number(b.date) - Number(a.date))[0]?.unit || 'kg';
    for (const h of (Array.isArray(history) ? history : [])) {
        if (!h || typeof h !== "object" || !h.perf)
            continue;
        if (!(h.date >= since))
            continue;
        const p = h.perf[id];
        if (!p)
            continue;
        const sets = completedHistorySets(p).filter(s => s.w > 0).map(s => ({ ...s, w: convertHistoryLoad(s.w, h.unit, unit) }));
        if (!sets.length)
            continue;

        const withReps = sets.filter(s => s.r > 0);
        const bestE = withReps.length ? Math.max(...withReps.map(s => e1rm(s.w, s.r))) : Math.max(...sets.map(s => s.w));
        const topW = Math.max(...sets.map(s => s.w));
        const vol = withReps.reduce((n, s) => n + s.w * s.r, 0);
        let v = null;
        if (metric.kind === "load")
            v = metric.reps ? rmAt(bestE, metric.reps) : topW;
        else if (metric.id === "vol")
            v = vol;
        else
            v = sets.length ? vol / sets.length : 0;
        if (!(v > 0))
            continue;
        rows.push({ date: h.date, v: Math.round(v * 10) / 10, sets: sets.length, reps: withReps.reduce((n, s) => n + s.r, 0), top: topW, e1rm: bestE, unit });
    }
    rows.sort((a, b) => a.date - b.date);
    if (!rows.length)
        return { ok: false, metric, window: win, points: [], sessions: 0, unit: unit || "kg" };
    const vals = rows.map(r => r.v);
    const bestRow = rows.reduce((m, r) => (r.v > m.v ? r : m), rows[0]);
    return {
        ok: true, metric, window: win, unit: unit || "kg", points: rows, sessions: rows.length,
        avg: Math.round((vals.reduce((a, b) => a + b, 0) / vals.length) * 10) / 10,
        best: bestRow.v, bestDate: bestRow.date,
        first: rows[0].v, last: rows[rows.length - 1].v,
        delta: Math.round((rows[rows.length - 1].v - rows[0].v) * 10) / 10,
        from: rows[0].date, to: rows[rows.length - 1].date
    };
}

function exerciseTrends(history, dayId = null) {
    const map = {}, units = {};
    const ordered = (Array.isArray(history) ? history : []).filter(validHistoryDate).slice().sort((a, b) => Number(a.date) - Number(b.date));
    for (const h of ordered) if (!dayId || h.dayId === dayId)
        for (const id of Object.keys(h.perf || {})) units[id] = h.unit || units[id] || 'kg';
    ordered.forEach(h => {
        if (!h || typeof h !== "object" || !h.perf)
            return; // a corrupt entry skips, never throws
        if (dayId && h.dayId !== dayId)
            return;
        Object.entries(h.perf).forEach(([id, p]) => {
            if (!(p && p.weight > 0))
                return;
            const unit = units[id];
            const raw = completedHistorySets(p).map(s => ({ ...s, w: convertHistoryLoad(s.w, h.unit, unit) }));
            const sets = raw.filter(s => s.w > 0).map(s => ({ w: s.w, r: s.r ?? null, e1rm: s.r != null ? e1rm(s.w, s.r) : s.w }));
            if (!sets.length)
                return;
            const best = sets.reduce((m, s) => (s.e1rm > m.e1rm ? s : m), sets[0]);
            /* `top` is the set this session is REMEMBERED BY — what shows as the record. It must come from
               the sets that were actually logged, NOT from the perf summary (p.weight/p.reps).
               The summary's reps are deliberately the MINIMUM across the sets at the top weight: it exists to
               anchor the next session's load on something you held for every set, which is the right, cautious
               number for PROGRESSION. It is the wrong number for a RECORD. Bench 225×6 then 225×5 and the
               summary says 225×5 — accurate as "what you held throughout", but shown as your record it reports
               a set you beat. `best` (highest e1RM) was already computed here from the real sets and is the
               set that deserves the billing; reuse it so the record and the number driving the PR line can
               never disagree again. */
            (map[id] = map[id] || []).push({ date: h.date, unit, sets, best: best.e1rm, top: { w: best.w, r: best.r } });
        });
    });
    return Object.entries(map).map(([id, sessions]) => {
        const ex = EX_BY_ID[id];
        if (!ex)
            return null;
        const last = sessions[sessions.length - 1];
        const prBest = Math.max(...sessions.map(s => s.best));
        /* The heaviest load actually put on the bar, as distinct from prBest, which is an ESTIMATE off a
           submaximal set. A 5-rep 245 estimates ~275; only one of those is a number you have lifted.
           Both are legitimate and they answer different questions, so both are carried. */
        const prTopW = Math.max(...sessions.map(s => (s.top && s.top.w > 0 ? s.top.w : 0)));
        return {
            id, name: ex.name, part: ex.part, sessions, first: sessions[0], last,
            sessionsCount: sessions.length, pts: sessions.map(s => s.best),
            prBest, prTopW, unit: last.unit, delta: last.best - sessions[0].best
        };
    }).filter(Boolean).sort((a, b) => b.last.date - a.last.date);
}

function blockRetro(history, opts = {}) {
    const WEEK = 7 * 86400000, now = Date.now();
    const weeks = opts.weeks || 6;
    const since = opts.sinceDate || (now - weeks * WEEK);
    const until = opts.untilDate || now;
    const win = (history || []).filter(h => h && h.date && h.date >= since && h.date <= until);
    const spanWeeks = Math.max(1, (until - since) / WEEK);
    if (win.length < 3)
        return { ok: false, reason: "Not enough logged sessions in this window to say anything honest.", sessions: win.length, weeks: Math.round(spanWeeks) };
    const r1 = v => Math.round(v * 10) / 10, r2 = v => Math.round(v * 100) / 100;
    const median = a => { if (!a.length)
        return null; const v = a.slice().sort((x, y) => x - y); const m = Math.floor(v.length / 2); return v.length % 2 ? v[m] : (v[m - 1] + v[m]) / 2; };
    const slopePctWk = ser => {
        if (ser.length < 3)
            return 0;
        const t0 = ser[0].date, xs = ser.map(s => (s.date - t0) / WEEK), ys = ser.map(s => s.e);
        const mx = xs.reduce((a, b) => a + b, 0) / xs.length, my = ys.reduce((a, b) => a + b, 0) / ys.length;
        let num = 0, den = 0;
        for (let i = 0; i < xs.length; i++) {
            num += (xs[i] - mx) * (ys[i] - my);
            den += (xs[i] - mx) ** 2;
        }
        return my > 0 && den > 0 ? 100 * (num / den) / my : 0; // %/wk relative to mean e1RM
    };
    // Per-lift e1RM series within the window
    const byLift = {};
    for (const h of [...win].sort((a, b) => a.date - b.date)) {
        for (const [id, p] of Object.entries(h.perf || {})) {
            const ex = EX_BY_ID[id];
            if (!ex)
                continue;
            const ss = setsOf(p);
            let best = 0;
            for (const s of ss) {
                const w = +(s.w != null ? s.w : s.weight), r = +(s.r != null ? s.r : s.reps);
                if (w > 0 && r > 0 && isWorkSet(s)) {
                    const e = e1rm(convertHistoryLoad(w, h.unit, "kg"), r);
                    if (e > best)
                        best = e;
                }
            }
            if (best > 0)
                (byLift[id] = byLift[id] || []).push({ date: h.date, e: best });
        }
    }
    const lifts = Object.entries(byLift).map(([id, ser]) => {
        const ex = EX_BY_ID[id];
        const deltaPct = 100 * (ser[ser.length - 1].e - ser[0].e) / ser[0].e;
        const state = ser.length < 3 ? "thin" : (deltaPct >= 1.5 ? "progressed" : deltaPct <= -1.5 ? "regressed" : "flat");
        return { id, name: ex.name, part: ex.part, sessions: ser.length, deltaPct: r1(deltaPct), slopePctWk: r2(slopePctWk(ser)), state };
    });
    // Per-muscle: direct working sets/wk in the window, paired with the muscle's aggregate e1RM trend
    const setsByPart = {};
    for (const h of win)
        for (const [id, p] of Object.entries(h.perf || {})) {
            const ex = EX_BY_ID[id];
            if (!ex || !p.sets)
                continue;
            const n = p.sets.filter(s => isWorkSet(s)).length;
            if (n)
                setsByPart[ex.part] = (setsByPart[ex.part] || 0) + n;
        }
    const muscles = Object.keys(setsByPart).map(part => {
        const avgWk = setsByPart[part] / spanWeeks;
        const L = landmarkFor(part), mav = personalMav(history, part), gmav = mavFor(part);
        const partLifts = lifts.filter(l => l.part === part && l.sessions >= 3);
        const prog = partLifts.length ? median(partLifts.map(l => l.deltaPct)) : null;
        const trend = prog == null ? "unknown" : (prog >= 1.5 ? "progressed" : prog <= -1.5 ? "regressed" : "flat");
        const zone = avgWk < L.mev ? "under MEV" : avgWk >= L.mrv ? "at/over MRV" : avgWk >= mav ? "near MAV" : "productive";
        let rec = null, drove = null;
        if (trend === "progressed") {
            drove = `up ${prog > 0 ? "+" : ""}${r1(prog)}% at ${Math.round(avgWk)} sets/wk (${zone})`;
            rec = avgWk < mav ? `Working — and you're below your MAV (~${Math.round(mav)}). Hold, or add 1–2 sets to press the advantage.` : `Working near your MAV. Hold volume; let load keep climbing.`;
        }
        else if (trend === "flat" || trend === "regressed") {
            drove = `${trend}, ${Math.round(avgWk)} sets/wk (${zone})`;
            rec = zone === "under MEV" ? `Under-dosed at ${Math.round(avgWk)} sets/wk — add volume toward MEV ${L.mev}${avgWk < mav ? `–MAV ~${Math.round(mav)}` : ""} next block.`
                : (zone === "at/over MRV" || zone === "near MAV") ? `High volume (${Math.round(avgWk)} sets/wk) with no progress — volume isn't the lever. Deload, then reduce or vary the exercise.`
                    : `Stalled at productive volume — deload or swap the exercise; chase reps before adding load.`;
        }
        return { part, avgSets: r1(avgWk), mev: L.mev, mav: r1(mav), mavPersonal: Math.abs(mav - gmav) > 0.05, mrv: L.mrv, zone, trend, drove, rec };
    }).sort((a, b) => ({ regressed: 0, flat: 1, unknown: 2, progressed: 3 }[a.trend] - { regressed: 0, flat: 1, unknown: 2, progressed: 3 }[b.trend]) || a.avgSets - b.avgSets);
    const gained = lifts.filter(l => l.state === "progressed").length;
    const stalled = lifts.filter(l => l.state === "flat" || l.state === "regressed").length;
    return {
        ok: true, weeks: Math.round(spanWeeks), sessions: win.length,
        headline: `${win.length} sessions over ~${Math.round(spanWeeks)} wk · ${gained} lift${gained !== 1 ? "s" : ""} up, ${stalled} flat`,
        lifts: lifts.sort((a, b) => b.deltaPct - a.deltaPct),
        muscles,
        recs: muscles.filter(m => m.rec).map(m => ({ part: m.part, trend: m.trend, rec: m.rec }))
    };
}

const MIN_DAY_SESSIONS = 3;

let _dstCache = { key: null, val: null };

function dayScopedTrends(history) {
    const hs = history || [];
    // Content fingerprint, not array identity: identity alone would serve a stale index to any caller
    // that mutates history in place. EXERCISES.length is folded in because exerciseTrends() drops ids
    // missing from EX_BY_ID — a custom exercise registered after the first call would otherwise stay
    // invisible to every later one.
    const key = `${lifterModelKey(hs)}:${EXERCISES.length}`;
    if (_dstCache.key === key && _dstCache.val)
        return _dstCache.val;
    const dayIds = [...new Set(hs.filter(h => h && h.dayId).map(h => h.dayId))];
    const val = {};
    // One pass per DAY, not per lift — days are few, history is not.
    dayIds.forEach(d => { val[d] = Object.fromEntries(exerciseTrends(hs, d).map(t => [t.id, t])); });
    _dstCache = { key, val };
    return val;
}

function plateauSplitByDay(history, id, judge, fallback) {
    const byDay = dayScopedTrends(history);
    const days = Object.keys(byDay).filter(d => byDay[d][id] && byDay[d][id].sessionsCount >= MIN_DAY_SESSIONS);
    // Under two judgeable days there is nothing to split — including a history with no dayId at all,
    // which is exactly the pre-existing behaviour and the best available for that data.
    if (days.length < 2)
        return fallback();
    const perDay = days.map(d => ({ dayId: d, pl: judge(byDay[d][id], d) }));
    if (perDay.some(x => !x.pl))
        return null; // progressing on some day → not stalled
    const soonest = perDay.reduce((m, x) => (x.pl.since < m.pl.since ? x : m), perDay[0]);
    return { ...soonest.pl, high: soonest.pl.since >= 3, perDay: Object.fromEntries(perDay.map(x => [x.dayId, x.pl])) };
}

function plateauOfLift(history, trend) {
    if (!trend)
        return null;
    return plateauSplitByDay(history, trend.id, (t) => plateauOf(t), () => plateauOf(trend));
}

function plateauOf(trend) {
    if (!trend || trend.sessionsCount < 3)
        return null;
    const pts = trend.pts;
    /* THE LAST SESSION THAT STRICTLY BEAT EVERYTHING BEFORE IT — not `lastIndexOf(Math.max(...pts))`.
     *
     * This is the same tie-breaking hole that `stallCountFor` was fixed for, still living in its
     * sibling. `pts` runs oldest→newest, so on a DEAD-FLAT run every value ties the max, lastIndexOf
     * returns the NEWEST index, `since` computes to 0, and the lift reads as "PR today, no stall".
     * Measured on [100,100,100,100,100,100]: since = 0 — byte-identical to a genuinely RISING run.
     * The detector could only see a plateau once you had got WEAKER; merely going nowhere, which is
     * what a plateau overwhelmingly looks like, was invisible.
     *
     * It mattered because two detectors were answering one question and disagreeing: on six flat
     * sessions `stallCountFor` correctly reported 5 and the session badge announced "e1RM
     * autoregulation · temporary — recalibrating", while the coach card, reading this function, had
     * nothing to say at all. The lifter is told their programming changed and given no reason.
     *
     * Walking forward, a session counts as the PR only if it strictly beats every session older than
     * it, so a flat run scores its full length and a rising one still scores 0. The 2% work band below
     * is unchanged and still does its own job — it is what stops double progression (which adds reps
     * and sets at a fixed load, invisible to an e1RM series capped at 12 reps) from reading as a
     * stall. */
    let prIdx = 0, best = -Infinity;
    for (let i = 0; i < pts.length; i++)
        if (pts[i] > best + 1e-9) {
            best = pts[i];
            prIdx = i;
        }
    const since = (pts.length - 1) - prIdx;
    if (since < 2)
        return null;
    /* Double progression deliberately HOLDS the load and adds reps, then sets, until every set sits at
       the top of the range — and the e1RM series cannot see any of it. e1rm() caps reps at 12, so on a
       12–20 rep accessory the estimate literally cannot move; and `best` reads only the single best set,
       so adding a third and fourth top-range set changes nothing. A lifter doing strictly more work each
       week was being told they had plateaued. Rising work is progress; only flag a stall when the work
       has stopped growing too. */
    const workAt = (i) => ((trend.sessions[i] && trend.sessions[i].sets) || [])
        .reduce((sum, x) => sum + (x.w > 0 && x.r > 0 ? x.w * x.r : 0), 0);
    const prWork = workAt(prIdx), lastWork = workAt(pts.length - 1);
    if (prWork > 0 && lastWork > prWork * 1.02)
        return null; // 2% band so logging noise isn't "progress"
    const high = since >= 3;
    return {
        since, high,
        advice: high
            ? `No estimated-1RM PR in ${since} sessions. Swap in a fresh variation or run a deload to shed fatigue, then rebuild.`
            : `No PR in ${since} sessions. Add a set or chase 1–2 more reps at this load before adding weight.`
    };
}

const STD_LEVELS = ["Untrained", "Beginner", "Novice", "Intermediate", "Advanced", "Elite"];

const SEX_FACTOR = { male: 1, female: 0.72 };

const STANDARDS = {
    "bb-bench": [0.5, 0.75, 1.0, 1.5, 2.0], "inc-bb-bench": [0.4, 0.6, 0.85, 1.25, 1.6], "low-inc-bb-bench": [0.4, 0.6, 0.85, 1.25, 1.6], "high-inc-bb-bench": [0.35, 0.56, 0.75, 1.1, 1.45], "smith-bench": [0.5, 0.75, 1.0, 1.45, 1.9],
    "back-squat": [0.75, 1.25, 1.5, 2.25, 2.75], "front-squat": [0.6, 1.0, 1.3, 1.85, 2.25],
    "deadlift": [1.0, 1.5, 2.0, 2.5, 3.0], "sumo-dl": [1.0, 1.5, 2.0, 2.5, 3.0],
    "ohp": [0.35, 0.56, 0.8, 1.1, 1.4], "bb-row": [0.5, 0.75, 1.0, 1.35, 1.7], "cgbp": [0.45, 0.7, 0.95, 1.4, 1.85],
    "bb-curl": [0.2, 0.35, 0.5, 0.65, 0.85], "ez-curl": [0.2, 0.35, 0.5, 0.65, 0.85], "hip-thrust": [1.0, 1.5, 2.0, 2.75, 3.5],
    "decline-bench": [0.5, 0.75, 1.0, 1.5, 2.0], "seated-ohp": [0.35, 0.56, 0.8, 1.1, 1.4], "pendlay-row": [0.5, 0.75, 1.0, 1.35, 1.7],
    "box-squat": [0.75, 1.25, 1.5, 2.25, 2.75], "stiff-deadlift": [0.9, 1.35, 1.8, 2.3, 2.8], "rack-pull": [1.1, 1.6, 2.1, 2.7, 3.2]
};

const toUnit = (v, from, to) => from === to ? v : (to === "lb" ? v * 2.2046226 : v / 2.2046226);

const todayISO = () => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`; };

const BIRTH_RE = /^\d{4}-\d{2}-\d{2}$/;

function birthParts(b) {
    if (typeof b !== "string" || !BIRTH_RE.test(b))
        return null;
    const [y, m, d] = b.split("-").map(Number);
    if (!(y >= 1900) || !(m >= 1 && m <= 12) || !(d >= 1 && d <= 31))
        return null;
    const dt = new Date(y, m - 1, d);
    // rejects impossible calendar dates that the regex lets through (2023-02-30 rolls over to Mar 2)
    if (dt.getFullYear() !== y || dt.getMonth() !== m - 1 || dt.getDate() !== d)
        return null;
    return { y, m, d };
}

function ageFrom(birth, now = Date.now()) {
    const p = birthParts(birth);
    if (!p)
        return "";
    const t = new Date(now);
    let a = t.getFullYear() - p.y;
    const mo = t.getMonth() + 1, day = t.getDate();
    if (mo < p.m || (mo === p.m && day < p.d))
        a -= 1; // birthday hasn't come round yet this year
    return a >= 0 && a <= 120 ? a : ""; // future dates and implausible ones score as unknown
}

function birthFromAge(age, asOf) {
    const a = Math.round(Number(age));
    if (!(a > 0 && a <= 120))
        return null;
    const y = new Date(asOf || Date.now()).getFullYear() - a;
    return `${y}-07-01`;
}

function ageFactor(age) {
    const a = Number(age);
    if (!(a > 0))
        return 1;
    if (a < 14)
        return 0.80;
    if (a < 17)
        return 0.90;
    if (a < 19)
        return 0.96;
    if (a <= 33)
        return 1.00;
    if (a <= 39)
        return 0.97;
    if (a <= 44)
        return 0.93;
    if (a <= 49)
        return 0.89;
    if (a <= 54)
        return 0.84;
    if (a <= 59)
        return 0.79;
    if (a <= 64)
        return 0.74;
    if (a <= 69)
        return 0.68;
    if (a <= 74)
        return 0.62;
    if (a <= 79)
        return 0.56;
    return 0.50;
}

function strengthLevel(exId, e1rmVal, e1rmUnit, bw, bwUnit, sex, age) {
    const base = STANDARDS[exId];
    if (!base || !(bw > 0) || !(e1rmVal > 0))
        return null;
    const bwL = toUnit(bw, bwUnit, e1rmUnit); // bodyweight in the lift's unit
    const af = ageFactor(age);
    const thresh = base.map(m => m * (SEX_FACTOR[sex] ?? 1) * af * bwL);
    let idx = 0;
    thresh.forEach((t, i) => { if (e1rmVal >= t)
        idx = i + 1; });
    const prevT = idx > 0 ? thresh[idx - 1] : 0;
    const nextT = idx < thresh.length ? thresh[idx] : thresh[thresh.length - 1];
    const within = idx >= thresh.length ? 1 : clamp((e1rmVal - prevT) / ((nextT - prevT) || 1), 0, 1);
    return { level: STD_LEVELS[idx], idx, ratio: e1rmVal / bwL, within, thresh, unit: e1rmUnit };
}

function strengthSnapshot(history, bw, bwUnit, sex, age) {
    if (!(bw > 0))
        return null;
    const trends = exerciseTrends(history);
    const lifts = trends.map(t => {
        const direct = STANDARDS[t.id] ? { id: t.id, val: t.prBest, est: false } : null;
        const use = direct || scoreEquivalent(t.id, t.prBest, t.unit, bw, bwUnit);
        if (!use)
            return null;
        const lvl = strengthLevel(use.id, use.val, t.unit, bw, bwUnit, sex, age);
        return lvl ? { id: t.id, name: t.name, prBest: t.prBest, unit: t.unit, lvl,
            est: !!use.est, via: use.id, pat: SCORE_PATTERN[use.id] || null } : null;
    }).filter(Boolean);
    if (!lifts.length)
        return null;
    const score = lifts.reduce((s, l) => s + l.lvl.idx + (l.lvl.idx < 5 ? l.lvl.within : 0), 0) / lifts.length;
    const overallIdx = Math.max(0, Math.min(5, Math.round(score)));
    lifts.sort((a, b) => (b.lvl.idx - a.lvl.idx) || (b.lvl.within - a.lvl.within));
    return { lifts, overallIdx, overallLabel: STD_LEVELS[overallIdx], score };
}

const SCORE_PATTERN = {
    "bb-bench": "push", "inc-bb-bench": "push", "low-inc-bb-bench": "push", "high-inc-bb-bench": "push", "smith-bench": "push", "decline-bench": "push",
    "ohp": "push", "seated-ohp": "push", "cgbp": "push",
    "bb-row": "pull", "pendlay-row": "pull", "bb-curl": "pull", "ez-curl": "pull",
    "back-squat": "squat", "front-squat": "squat", "box-squat": "squat",
    "deadlift": "hinge", "sumo-dl": "hinge", "stiff-deadlift": "hinge", "rack-pull": "hinge", "hip-thrust": "hinge"
};

const SCORE_PATTERN_LABEL = { push: "Push", pull: "Pull", squat: "Squat", hinge: "Hinge" };

const SCORE_EQUIV = {
    "db-bench": { to: "bb-bench", k: 2.3 }, "inc-db-press": { to: "inc-bb-bench", k: 2.3 },
    "decline-db-press": { to: "decline-bench", k: 2.3 },
    "db-shoulder": { to: "ohp", k: 2.2 }, "seated-db-press": { to: "seated-ohp", k: 2.2 },
    "arnold": { to: "ohp", k: 2.1 },
    "db-row": { to: "bb-row", k: 1.8 }, "inc-db-row": { to: "bb-row", k: 1.9 }, "kroc-row": { to: "bb-row", k: 1.6 },
    "db-rdl": { to: "stiff-deadlift", k: 2.0 },
    "pullup": { to: "bb-row", k: 1.0, bw: true }, "chinup": { to: "bb-row", k: 1.0, bw: true },
    "dips-chest": { to: "bb-bench", k: 0.95, bw: true }
};

function scoreTargetFor(id) { return STANDARDS[id] ? id : (SCORE_EQUIV[id] ? SCORE_EQUIV[id].to : null); }

function scorePatternFor(id) { const t = scoreTargetFor(id); return t ? SCORE_PATTERN[t] : null; }

const SCORE_BANDS = STD_LEVELS.length - 1;

const scoreContin = (lvl) => lvl.idx + (lvl.idx < SCORE_BANDS ? lvl.within : 0);

const scoreTo100 = (c) => Math.round(clamp(c / SCORE_BANDS, 0, 1) * 100);

const scoreMatur = (n) => clamp(((n || 0) - 1) / 4, 0, 1);

function scoreLevelIdx(overall) { return clamp(Math.floor((Number(overall) || 0) / (100 / SCORE_BANDS)), 0, SCORE_BANDS); }

function scoreLevelLabel(overall) { return STD_LEVELS[scoreLevelIdx(overall)]; }

function scoreCompose(entries, liftSessions, patSessions) {
    const byPat = {};
    entries.forEach(l => {
        const pat = l.pat;
        if (!pat)
            return;
        const c = scoreContin(l.lvl);
        /* A MEASURED lift always wins its pattern over an estimated one — not merely on a tie. The
           conversions carry real error, and a dumbbell press converting to a bigger number than the
           bench the lifter actually performed would let that error overwrite data the app is certain
           about. Estimates exist to cover a pattern with NO barbell lift in it, not to outbid one that
           has. */
        const cur = byPat[pat];
        if (!cur) {
            byPat[pat] = { c, lift: l };
            return;
        }
        if (cur.lift.est !== l.est) {
            if (cur.lift.est && !l.est)
                byPat[pat] = { c, lift: l };
            return;
        }
        if (c > cur.c)
            byPat[pat] = { c, lift: l };
    });
    const subs = Object.keys(SCORE_PATTERN_LABEL)
        .filter(p => byPat[p])
        .map(p => ({ key: p, label: SCORE_PATTERN_LABEL[p], score: scoreTo100(byPat[p].c), lift: byPat[p].lift, idx: byPat[p].lift.lvl.idx }));
    // The unweighted mean is the fallback for both means, used only when nothing has reached any
    // maturity at all (every movement on its first session).
    const flat = entries.length ? scoreTo100(entries.reduce((s, l) => s + scoreContin(l.lvl), 0) / entries.length) : 0;
    let aw = 0, awt = 0;
    entries.forEach(l => { const w = scoreMatur(liftSessions[l.id]); aw += scoreContin(l.lvl) * w; awt += w; });
    const allMean = awt > 0 ? scoreTo100(aw / awt) : flat;
    let pw = 0, pwt = 0;
    subs.forEach(x => { const w = scoreMatur(patSessions[x.key]); pw += x.score * w; pwt += w; });
    const patMean = pwt > 0 ? pw / pwt : (subs.length ? subs.reduce((s, x) => s + x.score, 0) / subs.length : allMean);
    return { overall: Math.round(patMean * 0.7 + allMean * 0.3), subs, byPat };
}

function scoreEquivalentInverse(id, val, unit, bw, bwUnit) {
    const eq = SCORE_EQUIV[id];
    if (!eq)
        return val; // a real standard lift: already its own unit
    return eq.bw ? (val / eq.k) - toUnit(bw, bwUnit, unit) : val / eq.k;
}

function scoreEquivalent(id, e1rmVal, unit, bw, bwUnit) {
    const eq = SCORE_EQUIV[id];
    if (!eq || !(e1rmVal > 0))
        return null;
    const load = eq.bw ? (toUnit(bw, bwUnit, unit) + e1rmVal) * eq.k : e1rmVal * eq.k;
    return load > 0 ? { id: eq.to, val: load, est: true } : null;
}

function shareSession(entry, theme, history = []) {
    if (!entry || typeof document === "undefined")
        return;
    const C = theme || {};
    const unit = entry.unit || "kg";
    /* The estimated-1RM line and the PR list are the two things that make this card worth posting, and
       a history entry doesn't carry either — they have to be derived, which is why `history` is passed
       in. Best e1RM per lift in THIS session, against the best across every session strictly before it:
       that's what makes a PR a PR. A lift with no earlier session isn't a PR, it's a first attempt, so
       it needs a prior to beat (the same rule the end-of-session card uses). Sessions sharing a date
       are treated as not-prior — with no ordering within a day there's no honest way to rank them, and
       over-claiming a PR is worse than missing one. */
    const bestOf = (perf) => {
        const out = {};
        Object.entries(perf || {}).forEach(([id, p]) => {
            setsOf(p).forEach(x => {
                if (!(x.w > 0) || !(x.r > 0))
                    return;
                const v = e1rm(x.w, x.r);
                if (v > (out[id] || 0))
                    out[id] = v;
            });
        });
        return out;
    };
    const nowBest = bestOf(entry.perf);
    const entryTime = new Date(entry.date).getTime();
    const priorBest = {};
    (history || []).forEach(h => {
        if (!h || h === entry || !(new Date(h.date).getTime() < entryTime))
            return;
        const b = bestOf(h.perf);
        Object.entries(b).forEach(([id, v]) => { if (v > (priorBest[id] || 0))
            priorBest[id] = v; });
    });
    const prs = Object.entries(nowBest)
        .filter(([id, v]) => priorBest[id] != null && v > priorBest[id] + 1e-9 && EX_BY_ID[id])
        .sort((a, b) => b[1] - a[1])
        .map(([id]) => ({ name: EX_BY_ID[id].name }));
    // Headline lift = the session's highest estimated 1RM. A logged entry has no primaryIndex to read,
    // and the heaviest single effort is the closest honest stand-in for "the main lift".
    const mainId = Object.keys(nowBest).sort((a, b) => nowBest[b] - nowBest[a])[0];
    const mainE1rm = mainId && EX_BY_ID[mainId]
        ? { name: EX_BY_ID[mainId].name, est: Math.round(nowBest[mainId]) }
        : null;
    const lifts = Object.entries(entry.perf || {})
        .map(([id, p]) => {
        const ex = EX_BY_ID[id];
        if (!ex)
            return null;
        const done = setsOf(p).filter(x => x.w != null && x.r > 0);
        if (!done.length)
            return null;
        const top = pickTopSet(done, x => x.w, x => x.r) || done[0];
        return { name: ex.name, sets: done.length, top };
    }).filter(Boolean); // no fixed cap: the card measures how many rows actually fit
    const W = 1080, H = 1350, cnv = document.createElement("canvas");
    cnv.width = W;
    cnv.height = H;
    const ctx = cnv.getContext("2d");
    if (!ctx)
        return;
    /* Renders through drawRecapCard — the same card the end-of-session screen produces. This used to
       be a second, separately-maintained layout (its own "PURSUIT IRON" eyebrow, a bare stats row
       instead of tiles, "6 × 225lb · 5" lift lines) which drifted from the recap card it sits beside,
       and carried the same class of bug: .slice(22) on the title, .slice(26) on every lift name and
       .slice(52) on the subtitle, all cutting mid-word. One renderer, one look, and the measured text
       fitting applies here too.
       A history entry has no estimated 1RM or PR list attached, so those sections are simply absent —
       drawRecapCard omits them and gives the rows the space instead. */
    drawRecapCard(ctx, {
        C,
        label: String(entry.dayLabel || "Workout"),
        dateStr: `${entry.programName ? entry.programName + " · " : ""}${new Date(entry.date).toLocaleDateString(undefined, { weekday: "long", month: "long", day: "numeric" })}`,
        durMin: entry.durationMin || 0,
        doneSets: entry.setsDone ?? 0,
        volume: Number(entry.volume || 0),
        unit,
        mainE1rm,
        prs,
        tops: lifts.map(l => ({ name: l.name, set: `${l.top.w}${unit}×${l.top.r}` }))
    }, W, H);
    const done = (blob) => {
        const file = typeof File !== "undefined" ? new File([blob], "pursuit-iron-workout.png", { type: "image/png" }) : null;
        try {
            if (file && navigator.canShare && navigator.canShare({ files: [file] })) {
                navigator.share({ files: [file], title: entry.dayLabel || "My workout" }).catch(() => { });
                return;
            }
        }
        catch { }
        try {
            const url = URL.createObjectURL(blob);
            const a = document.createElement("a");
            a.href = url;
            a.download = "pursuit-iron-workout.png";
            document.body.appendChild(a);
            a.click();
            a.remove();
            setTimeout(() => URL.revokeObjectURL(url), 1500);
        }
        catch { }
    };
    if (cnv.toBlob)
        cnv.toBlob(b => b && done(b), "image/png");
    else {
        const a = document.createElement("a");
        a.href = cnv.toDataURL("image/png");
        a.download = "pursuit-iron-workout.png";
        a.click();
    }
}

function shareStrengthScore(sscore, theme) {
    if (!sscore || typeof document === "undefined")
        return;
    const C = theme || {};
    const W = 1080, H = 1350, PAD = 104, CW = W - PAD * 2;
    const cnv = document.createElement("canvas");
    cnv.width = W;
    cnv.height = H;
    const ctx = cnv.getContext("2d");
    if (!ctx)
        return;
    const rr = (x, y, w, h, r) => { ctx.beginPath(); if (ctx.roundRect)
        ctx.roundRect(x, y, w, h, r);
    else
        ctx.rect(x, y, w, h); };
    const font = (weight, px) => { ctx.font = `${weight} ${px}px ${SHARE_FONT}`; };
    /* Same measured-text treatment as the recap card. This one never used .slice(), so nothing was
       being severed mid-word — but none of it was measured either, which is the same exposure by a
       different route: a long level name, a four-pattern breakdown squeezing the tiles, or a
       "Strongest X · building Y" line with two long labels could all run past the card edge with no
       guard anywhere. Sizes and positions below are unchanged; they simply can't overflow now. */
    const elite = sscore.levelIdx >= 4;
    const hl = elite ? (C.warn || "#F0B429") : (C.accent || "#2563EB");
    ctx.fillStyle = C.bg || "#070A0F";
    ctx.fillRect(0, 0, W, H);
    ctx.fillStyle = C.card || "#11161F";
    rr(56, 56, W - 112, H - 112, 44);
    ctx.fill();
    ctx.fillStyle = hl;
    rr(56, 56, W - 112, 18, 9);
    ctx.fill();
    ctx.textBaseline = "top";
    ctx.fillStyle = hl;
    font(800, 30);
    ctx.fillText(fitText(ctx, "PURSUIT IRON · STRENGTH SCORE", CW), PAD, 132);
    // The score and its "/100" suffix are positioned from the measured number, then the suffix is
    // clamped to the margin so a three-digit score can't push it off the card.
    const num = String(sscore.overall);
    ctx.fillStyle = C.text || "#E6EBF2";
    font(900, 260);
    const numW = ctx.measureText(num).width;
    ctx.fillText(num, 96, 200);
    ctx.fillStyle = C.faint || "#646D7B";
    font(800, 70);
    ctx.fillText("/100", Math.min(110 + numW, W - PAD - ctx.measureText("/100").width), 380);
    ctx.fillStyle = hl;
    fitFont(ctx, sscore.level, CW, 800, 56, 34);
    ctx.fillText(fitText(ctx, sscore.level, CW), PAD, 500);
    ctx.fillStyle = C.muted || "#8C97A8";
    font(500, 32);
    ctx.fillText(fitText(ctx, `across ${sscore.liftCount} lift${sscore.liftCount === 1 ? "" : "s"} · scaled to age & bodyweight`, CW), PAD, 576);
    let y = 700;
    ctx.fillStyle = C.faint || "#646D7B";
    font(700, 28);
    ctx.fillText("BREAKDOWN", PAD, y);
    y += 56;
    const cols = Math.max(1, sscore.subs.length);
    const gap = 24, cw = (CW - gap * (cols - 1)) / cols;
    sscore.subs.forEach((s, i) => {
        const x = PAD + i * (cw + gap);
        ctx.fillStyle = C.bg2 || "#19202C";
        rr(x, y, cw, 200, 26);
        ctx.fill();
        const sHl = s.idx >= 4 ? (C.warn || "#F0B429") : (C.accent || "#2563EB");
        // Tiles narrow as patterns are added; the score and label size to the tile they landed in.
        ctx.fillStyle = sHl;
        fitFont(ctx, String(s.score), cw - 24, 900, 80, 44);
        ctx.textAlign = "center";
        ctx.fillText(String(s.score), x + cw / 2, y + 40);
        ctx.fillStyle = C.muted || "#8C97A8";
        font(700, 28);
        ctx.fillText(fitText(ctx, String(s.label).toUpperCase(), cw - 20), x + cw / 2, y + 140);
        ctx.textAlign = "left";
    });
    y += 260;
    if (sscore.weakest && sscore.strongest && sscore.subs.length > 1 && sscore.weakest.key !== sscore.strongest.key) {
        ctx.fillStyle = C.text || "#E6EBF2";
        font(600, 34);
        ctx.fillText(fitText(ctx, `Strongest ${sscore.strongest.label} · building ${sscore.weakest.label}`, CW), PAD, y);
    }
    ctx.fillStyle = C.faint || "#646D7B";
    font(600, 28);
    drawShareBrand(ctx, C, PAD, H - 150, CW, "evidence-based · on-device");
    const done = (blob) => {
        try {
            if (navigator.canShare && typeof File !== "undefined") {
                const file = new File([blob], "pursuit-iron-strength-score.png", { type: "image/png" });
                if (navigator.canShare({ files: [file] })) {
                    navigator.share({ files: [file], title: "My Strength Score" }).catch(() => { });
                    return;
                }
            }
        }
        catch { }
        try {
            const url = URL.createObjectURL(blob);
            const a = document.createElement("a");
            a.href = url;
            a.download = "pursuit-iron-strength-score.png";
            document.body.appendChild(a);
            a.click();
            a.remove();
            setTimeout(() => URL.revokeObjectURL(url), 1500);
        }
        catch { }
    };
    if (cnv.toBlob)
        cnv.toBlob(b => b && done(b), "image/png");
    else {
        const a = document.createElement("a");
        a.href = cnv.toDataURL("image/png");
        a.download = "pursuit-iron-strength-score.png";
        a.click();
    }
}

function strengthScore(history, bw, bwUnit, sex, age) {
    const snap = strengthSnapshot(history, bw, bwUnit, sex, age);
    if (!snap)
        return null;
    // Training maturity per lift / pattern: a movement you've only just started shouldn't yank the
    // overall down. Its weight ramps from a fraction up to full over the first ~4 sessions, so trying
    // a new lift eases into the score instead of tanking it; as you keep training it, it counts fully.
    const liftSessions = {}, patSessions = {};
    (history || []).forEach(h => {
        if (!h || typeof h !== "object" || !h.perf)
            return;
        const lifts = new Set(), pats = new Set();
        Object.keys(h.perf).forEach(id => { if (!scoreTargetFor(id))
            return; lifts.add(id); const pt = scorePatternFor(id); if (pt)
            pats.add(pt); });
        lifts.forEach(id => liftSessions[id] = (liftSessions[id] || 0) + 1);
        pats.forEach(p => patSessions[p] = (patSessions[p] || 0) + 1);
    });
    /* overall = pattern mean blended with the all-lift average, each weighted by training maturity.
       Composed by the ONE OWNER above, which the trend line also runs — see its header for why. */
    const entries = snap.lifts.map(l => ({ ...l, pat: l.pat || SCORE_PATTERN[l.id] }));
    const { overall, subs } = scoreCompose(entries, liftSessions, patSessions);
    const weakest = subs.length ? subs.reduce((a, b) => b.score < a.score ? b : a) : null;
    const strongest = subs.length ? subs.reduce((a, b) => b.score > a.score ? b : a) : null;
    /* A score tells you where you stand and nothing about what to do next. The thresholds that produced
       it already know: strengthLevel returns the full ladder for the lift, so the load that reaches the
       next band is read straight off it rather than re-derived — one table, so the milestone can never
       disagree with the level shown beside it. Taken from the WEAKEST pattern because that is where a
       given pound moves the overall number most, and returns null at Elite, where there is no next
       band and inventing one would be a lie. */
    let nextMilestone = null;
    if (weakest && weakest.lift && weakest.lift.lvl) {
        const l = weakest.lift, idx = l.lvl.idx;
        if (idx < l.lvl.thresh.length) {
            /* The ladder is in BARBELL-EQUIVALENT load, so for an estimated lift the threshold must be
               converted back into what the lifter actually loads before it is shown beside their own
               numbers. Without this the card read "Dumbbell RDL 270 lb · now 114" — a target in barbell
               terms sitting next to a per-hand best, which is not a harder goal but a different unit, and
               would send someone after more than double the weight they need. */
            const target = Math.ceil(scoreEquivalentInverse(l.id, l.lvl.thresh[idx], l.unit, bw, bwUnit));
            if (target > l.prBest)
                nextMilestone = { id: l.id, name: l.name, pattern: weakest.label,
                    current: Math.round(l.prBest), target, unit: l.unit, est: !!l.est,
                    level: STD_LEVELS[idx + 1] || STD_LEVELS[STD_LEVELS.length - 1] };
        }
    }
    /* `estimated` is true only when NOTHING in the score came off a barbell — the dumbbell-only case,
       where the whole number rests on conversions and the UI must say so. A lifter with one real
       standard lift plus some estimated ones is not shown a blanket caveat over a score that is
       partly measured; `subs[].lift.est` carries the per-pattern detail for that. */
    const estimated = snap.lifts.length > 0 && snap.lifts.every(l => l.est);
    /* ⚠ THE LEVEL IS THE LEVEL OF `overall`, NOT OF A SECOND QUANTITY. It used to be
       `snap.overallLabel` — round(mean of raw level indices), unweighted, on 0–5 — printed beside a
       maturity-weighted 0–100 number it had no relationship to. `snapLevelIdx` is kept so a caller
       that genuinely wants the per-lift-average standing can still ask for it by name. */
    return { overall, level: scoreLevelLabel(overall), levelIdx: scoreLevelIdx(overall),
        snapLevelIdx: snap.overallIdx, snapLevel: snap.overallLabel,
        subs, weakest, strongest, liftCount: snap.lifts.length, nextMilestone, estimated };
}

function scoreAttribution(history, bwLog, bwNow, bwUnit, sex, age, days = 90) {
    if (!(bwNow > 0) || !history || !history.length)
        return null;
    const cutoff = Date.now() - days * 86400000;
    const log = normalizeBwLog(bwLog, bwUnit);
    const older = log.filter(e => e.date <= cutoff);
    const bwThen = older.length ? older[older.length - 1].w : (log.length ? log[0].w : null);
    if (!(bwThen > 0))
        return null;
    const past = history.filter(h => h && h.date && h.date <= cutoff);
    if (!past.length)
        return null;
    const now = strengthScore(history, bwNow, bwUnit, sex, age);
    const then = strengthScore(past, bwThen, bwUnit, sex, age);
    if (!now || !then)
        return null;
    // same lifts as `then`, weighed today: isolates the scale from the training
    const counter = strengthScore(past, bwNow, bwUnit, sex, age);
    if (!counter)
        return null;
    const fromBw = counter.overall - then.overall;
    const fromLifts = now.overall - counter.overall;
    return { total: now.overall - then.overall, fromLifts, fromBw,
        bwThen: Math.round(bwThen * 10) / 10, bwNow: Math.round(bwNow * 10) / 10, unit: bwUnit, days,
        scoreThen: then.overall, scoreNow: now.overall };
}

function strengthScoreHistory(history, bw, bwUnit, sex, age) {
    if (!(bw > 0) || !history || !history.length)
        return null;
    // chronological sessions, each with the lifts performed and their best e1RM that day
    const sessions = [...(history || [])].filter(h => h && h.date && h.perf).sort((a, b) => a.date - b.date);
    if (!sessions.length)
        return null;
    const bestById = {}; // running max e1RM per lift id (in that lift's unit)
    const overall = [], byPat = { push: [], pull: [], squat: [], hinge: [] };
    let changed = false;
    /* THE WEIGHTS ARE FIXED FOR THE WHOLE SERIES. THIS IS THE POINT OF THE FUNCTION.
     *
     * strengthScore() weights each lift and pattern by how many sessions you have ON IT SO FAR, so a
     * movement you just started eases into today's number instead of tanking it. Correct for a single
     * snapshot. Applied to a TIME SERIES it is a trap: the weights move between points, so the line
     * moves even when nothing about your strength does.
     *
     * Patterns are not trained at equal frequency. Train push every session and squat every fourth,
     * and push reaches full maturity while squat is still near zero — the early points are effectively
     * a push-only score, and squat and hinge drag the line down as they mature. Measured on a fixture
     * of exactly that shape: the curve fell 78 -> 56 while every individual lift ROSE, and the card
     * announced a 22-point loss. Nothing had got weaker; the weighting had rebalanced.
     *
     * So the counts are taken over the WHOLE history once, up front, and every point is scored with
     * those same weights. The only thing left that can move the line is strength, which is what a
     * trend is for. DO NOT switch these back to running counts. */
    const liftSessions = {}, patSessions = {};
    sessions.forEach(h => {
        const lifts = new Set(), pats = new Set();
        Object.entries(h.perf || {}).forEach(([id, pf]) => {
            /* ⚠ COUNT THE SAME LIFTS THE HEADLINE COUNTS. This filtered on `STANDARDS[id]` — barbell
               lifts only — while strengthScore counts through `scoreTargetFor`, which also admits the
               movements that convert to a standard. So a dumbbell-press lifter's maturity weights, and
               therefore the whole line, were computed over a different set of lifts than the number
               printed above it. */
            if (!scoreTargetFor(id) || !EX_BY_ID[id] || !(pf && pf.weight > 0))
                return;
            lifts.add(id);
            const pt = scorePatternFor(id);
            if (pt)
                pats.add(pt);
        });
        lifts.forEach(id => liftSessions[id] = (liftSessions[id] || 0) + 1);
        pats.forEach(pt => patSessions[pt] = (patSessions[pt] || 0) + 1);
    });
    /* A point is only comparable once every pattern that CARRIES WEIGHT has data behind it; before
     * that the mean is over a smaller set of patterns and is a different quantity. */
    const weighted = Object.keys(patSessions).filter(pt => scoreMatur(patSessions[pt]) > 0);
    const seenPats = new Set();
    sessions.forEach(h => {
        const unit = bwUnit || "kg";
        let touched = false;
        const liftsThis = new Set(), patsThis = new Set();
        Object.entries(h.perf).forEach(([id, p]) => {
            if (!scoreTargetFor(id) || !EX_BY_ID[id] || !(p && p.weight > 0))
                return;
            liftsThis.add(id);
            const pt = scorePatternFor(id);
            if (pt)
                patsThis.add(pt);
            const raw = completedHistorySets(p).map(s => ({ ...s, w: convertHistoryLoad(s.w, h.unit, unit) }));
            const e = Math.max(...raw.filter(s => s.w > 0).map(s => s.r != null ? e1rm(s.w, s.r) : s.w));
            if (!(e > 0))
                return;
            const cur = bestById[id];
            /* Keyed by the lift AS LOGGED, never by what it converts to: maturity is counted per movement
               you actually perform, and two different dumbbell presses both converting to `bb-bench` are
               two movements, not one. The conversion happens at scoring time, below. */
            if (!cur || e > cur.e || unit !== cur.unit) {
                bestById[id] = { e, unit };
                touched = true;
            }
        });
        patsThis.forEach(pt => seenPats.add(pt));
        if (weighted.some(pt => !seenPats.has(pt)))
            return; // not yet comparable — see the note above
        if (!touched && changed)
            return; // no new PR on a scorable lift → score unchanged, skip a point
        changed = true;
        /* ⚠ THE LINE AND THE NUMBER ABOVE IT ARE THE SAME FUNCTION. This block used to re-implement the
           blend — and it re-implemented a DIFFERENT one: barbell lifts only, no estimated conversions,
           so a lifter whose score included converted work saw a final point that disagreed with the
           headline on the same card ("56/100" above "· 47/100 now"). `scoreCompose` is now the only
           place that blend exists, so the last point IS the headline by construction. */
        const entries = [];
        Object.entries(bestById).forEach(([id, { e, unit }]) => {
            const use = STANDARDS[id] ? { id, val: e, est: false } : scoreEquivalent(id, e, unit, bw, bwUnit);
            if (!use)
                return;
            const lvl = strengthLevel(use.id, use.val, unit, bw, bwUnit, sex, age);
            if (!lvl)
                return;
            entries.push({ id, pat: SCORE_PATTERN[use.id] || null, est: !!use.est, lvl });
        });
        if (!entries.length)
            return;
        const composed = scoreCompose(entries, liftSessions, patSessions);
        overall.push({ date: h.date, v: composed.overall });
        Object.keys(byPat).forEach(p => { if (composed.byPat[p])
            byPat[p].push({ date: h.date, v: scoreTo100(composed.byPat[p].c) }); });
    });
    if (overall.length < 2)
        return null; // need at least two points to draw a trend
    /* NO BASELINE OFFSET IS NEEDED ANY MORE, and startIdx stays for the caller's sake.
     *
     * There used to be a "coverage-fair baseline" here that hunted for the first point at which every
     * pattern was present, because early points were computed over fewer patterns than later ones and
     * comparing across them read as a loss. That was a correction applied downstream of the real
     * problem. The weights are now fixed across the series and no point is emitted until every pattern
     * that carries weight has data, so every point in `overall` is already measured the same way and
     * index 0 is a fair comparison. */
    const startIdx = 0;
    return { overall, byPat, startIdx };
}

function underRecoveredWeekly(history, part) {
    const sess = [];
    for (const h of (history || []).filter(h => h && h.date).sort((a, b) => a.date - b.date)) {
        let n = 0;
        for (const [id, p] of Object.entries(h.perf || {})) {
            const ex = EX_BY_ID[id];
            if (ex && ex.part === part && p.sets)
                n += p.sets.filter(s => isWorkSet(s)).length;
        }
        if (n > 0)
            sess.push({ date: h.date, sets: n });
    }
    if (sess.length < 3)
        return false;
    const recent = sess.slice(-5);
    const avgSets = recent.reduce((a, s) => a + s.sets, 0) / recent.length;
    const clock = 30 + clamp(avgSets, 0, 28) * 2.3;
    const rec = personalRecoveryHours(history, part, clock) ?? clock;
    const gaps = [];
    for (let i = 1; i < recent.length; i++) {
        const g = (recent[i].date - recent[i - 1].date) / 3600000;
        if (g > 0 && g <= 240)
            gaps.push(g);
    }
    if (!gaps.length)
        return false;
    return gaps.filter(g => g < rec * 0.85).length >= Math.ceil(gaps.length / 2);
}

function volumeAdvice(history, opts) {
    history = (Array.isArray(history) ? history : []).filter(h => h && h.date); // defensive: a corrupt sync/import can't crash the weekly loop
    const program = opts?.program || null;
    /* Read the plan at the week the lifter is actually training, defaulting to the peak — the same
       week `weeklyVolume` is audited at everywhere else, so the two sides are the same owner. */
    const planWeek = program ? (opts?.weekIndex ?? Math.max(1, weeksOf(program) - (program.config?.deload ? 1 : 0))) : null;
    let planned = null;
    if (program) {
        try {
            planned = weeklyVolume(program, planWeek);
        }
        catch {
            planned = null;
        }
    }
    const vol = loggedVolume(history, 7); // ONE logged-set counter — see loggedVolume
    const subVol = loggedSubVolume(history, 7);
    const trends = exerciseTrends(history);
    const out = [];
    PART_ORDER.forEach(part => {
        const v = vol[part] || 0;
        if (v < 0.5)
            return;
        const { mev } = landmarkFor(part);
        const mrv = compositeMrv(part); // composite parts (shoulders) use the sum of their heads, not one flat ceiling that indirect credit routinely (and harmlessly) exceeds
        const mav = personalMav(history, part); // lifter's own productive ceiling when earned, else the group prior
        const mavPersonal = Math.abs(mav - mavFor(part)) > 0.05;
        const zone = partZone(part, v, subVol); // per-head aware — only trims volume when a real head is overbuilt, not just the lumped composite total
        const ms = trends.filter(t => t.part === part);
        const stalled = ms.length > 0 && ms.every(t => plateauOfLift(history, t));
        const recLimited = underRecoveredWeekly(history, part);
        let delta, reason, planOverMrv = false;
        if (v < mev) {
            delta = 2;
            reason = `Below MEV (${fmtSets(v)}/${mev} sets) — room to add volume`;
        }
        else if (zone.label.includes("over MRV")) {
            /* THE ON-PLAN TEST, and the tolerance is in SETS rather than a percentage on purpose. The
               question is not "is this close" but "did the lifter add work the plan did not ask for", and
               the smallest real addition is one set of a movement for which this part is secondary —
               half a set of credit. A quarter of a set sits safely under that, so any genuine extra
               session, extra set or second program still reads as a deviation and still trims. */
            const plan = planned ? (planned[part] || 0) : null;
            if (plan != null && plan > 0 && v <= plan + 0.25) {
                delta = 0;
                planOverMrv = true;
                reason = `Over MRV (${fmtSets(v)}/${mrv}) — but this is what your program prescribed and you followed it. The plan is above its own ceiling; fix it in the program rather than cutting sets each session.`;
            }
            else {
                delta = -2;
                reason = `Over MRV (${fmtSets(v)}/${mrv}) — pull volume back to recover`;
            }
        }
        else if (recLimited) {
            /* ⚠ NEVER CUT A MUSCLE THAT IS ALREADY AT ITS MINIMUM EFFECTIVE DOSE. Recovery-limited and
               stalling used to trim a set regardless of where the volume sat. But MEV is the floor below
               which training stops producing adaptation at all, so cutting under it does not solve the
               recovery problem — it trades a fatigue problem for a dosing one and leaves the lifter doing
               work that no longer earns anything. The landmark model this app runs on is explicit that
               fatigue is managed with rest, frequency and load, not by dropping beneath MEV.
               MEASURED: gates/plantrim check 1 — "a lifter who did exactly what the plan prescribed is
               never told to cut volume" — failed on texas/3 at s60, s90 and s120 with quads -1. Texas
               squats on all three days and puts quads at exactly MEV (8.0/8); the fixture logs those
               sessions one day apart, so recovery-limited is a fair reading. The advice was right that
               something is wrong and wrong about which lever to pull: the answer there is spacing, which
               is what the reason now says.
               At or below MEV the advice holds volume and points at rest. Above it, the old trim stands. */
            const atFloor = v <= mev;
            delta = (stalled && !atFloor) ? -1 : 0;
            reason = stalled
                ? (atFloor
                    ? `Recovery-limited and stalling at only ${fmtSets(v)}/${mev} sets — already at your minimum effective dose, so spread ${part} across more rest days rather than cutting sets`
                    : `Recovery-limited and stalling — you're retraining ${part} before it recovers; ease volume and add rest`)
                : `Recovery-limited — you're retraining ${part} before it recovers; hold volume, protect rest`;
        }
        else if (stalled) {
            delta = v >= mav ? -1 : 1;
            reason = v >= mav ? `Stalled at your productive ceiling (~${Math.round(mav)}${mavPersonal ? " for you" : ""}) — hold/reduce, push intensity & sleep` : `Stalled at only ${fmtSets(v)} sets — likely under-dosed, build toward ~${Math.round(mav)}`;
        }
        else {
            delta = v >= mav ? 0 : 1;
            reason = v >= mav ? `Progressing at your productive volume (~${Math.round(mav)}${mavPersonal ? " for you" : ""}) — hold here, let load drive` : `Productive & progressing (${fmtSets(v)}/${Math.round(mav)}${mavPersonal ? " your MAV" : ""}) — small bump to keep overloading`;
        }
        out.push({ part, v, mev, mrv, mav: Math.round(mav * 10) / 10, mavPersonal, recLimited, planOverMrv, delta, reason });
    });
    return out.sort((a, b) => b.delta - a.delta);
}

function volumeAudit(program) {
    if (program?.engineSource === "pursuit-next")
        return auditShellVolume(program, EXERCISES);
    if (!program || !program.days?.length)
        return { issues: [], volBias: {} };
    const peak = weeksOf(program); // peak training week (before any deload) carries the most volume
    const vol = weeklyVolume(program, peak);
    const perDayBudget = (SESSIONS.find(s => s.id === program.config?.session)?.count) || 5;
    // whether any day has room to add a movement
    const dayRoom = program.days.some(d => d.exercises.length < perDayBudget);
    const SET_CEIL_PER_EX = 5; // a single exercise much past this is junk volume; prefer a new movement
    const issues = [];
    const volBias = {};
    PART_ORDER.forEach(part => {
        const v = vol[part] || 0;
        const mev = landmarkFor(part).mev;
        const mrv = compositeMrv(part);
        // how many direct slots already train this muscle, and their current set load
        const slots = [];
        program.days.forEach(d => d.exercises.forEach((id, si) => {
            if (EX_BY_ID[id]?.part === part) {
                const sets = Number(computeCell(program, d, id, si, peak).sets) || 0;
                slots.push({ sets });
            }
        }));
        /* ⚠ v634 — ADVISE ONLY ON WHAT THE PROGRAM COMMITS TO. `COVERED_MUSCLES` is the set every
           coverage pass holds at MEV; the six landmarked parts outside it (traps, lower_back, adductors,
           abductors, forearms, neck) are accessory work a lifter opts into, and the generator has never
           targeted them. Measured across 234 generated programs, 92 of 409 issues (22%) were for those
           parts — "add 3 sets to your traps" against a plan whose design never included direct trap work,
           which the one-click fix then thickens some other slot to satisfy. Same class as the v586 forearm
           finding: advice the app cannot honestly act on is worse than silence. Over-MRV is UNCHANGED and
           still reported for every part — too much work is always the generator's doing, whatever the
           muscle. A lifter who adds their own trap work still sees it in the volume chart. */
        /* ⚠ THE OPTIONAL-MUSCLE RULE IS STATED HERE, NOT LEFT TO A COINCIDENCE. Two rules meet in this
           branch and until engine 22 both were enforced by accident:
             v586 — a REQUIRED REGION the program never trains is a missing MOVEMENT, and the audit must
                    say so ("your only forearm work is grip — add a wrist curl"), never "add a set".
             v634 — a SET SHORTFALL on an optional part (traps, forearms, neck, ...) is advice the app
                    cannot honestly act on, because the plan never included that work; never offer it.
           The v634 guard only tested `v > 0`, which held because the optional parts sat at exactly zero.
           Engine 22 put a hammer curl in the week, its 0.4-set forearm credit lifted forearms off zero,
           and 126 programs the lifter never asked forearm work of started advising it. The first repair
           scoped the WHOLE branch to COVERED_MUSCLES — which deleted the v586 advice with the padding
           (gates/regionfix 0 and 3 caught it). So the rule, by membership and by opt-in:
             • a covered part      → gap advice, then set-shortfall advice, as before.
             • an optional part    → gap advice ONLY when the program holds a DIRECT slot for it (the
                                     lifter or a focus opted in and the coverage is incomplete); never
                                     set-shortfall advice; incidental credit alone never produces a
                                     finding. Over-MRV stays unscoped, as before.
           `slots` is the list of direct slots for `part`, built above from EX_BY_ID[id].part. */
        if (v > 0 && v < mev - 0.5) {
            const need = Math.ceil(mev - v); // sets to reach MEV
            /* A DIRECTION THE PROGRAM NEVER TRAINS CANNOT BE FIXED BY ADDING SETS TO ONE IT DOES. See
               regionGapsFor: measured 21 of 21 forearm findings offered "add a set" against a program whose
               only forearm work was grip. The set went onto a carry — volume delivered, coverage unchanged,
               and the button claimed a fix it had not made. When a required region is missing, the honest
               answer is an EXERCISE, and volBias stays unset so the one-click fix does not fire at all
               (distributeVolBias only ever thickens existing slots; it cannot add a movement). */
            const gaps = regionGapsFor(program, part);
            const optedIn = COVERED_MUSCLES.has(part) || slots.length > 0;
            if (gaps.length && optedIn) {
                issues.push({ part, label: PART_LABEL[part], v, mev, mrv, status: "under", need,
                    fix: regionGapFix(part, gaps), fits: false, regionGaps: gaps });
                return;
            }
            /* The gap check runs for every opted-in part ABOVE this line; only the set-shortfall advice
               below is scoped to the parts the generator commits to. gates/coveragemodel check 3 pins the
               order from source; gates/regionfix pins the behaviour. */
            if (!COVERED_MUSCLES.has(part))
                return;
            // headroom = how many sets we can still add across existing slots before any one exercise hits
            // the junk-volume ceiling. We add as many as fit (a PARTIAL fix is still worth doing), and only
            // tell the user to add an exercise when there's no set-level headroom left at all.
            const headroom = slots.reduce((s, sl) => s + Math.max(0, SET_CEIL_PER_EX - sl.sets), 0);
            const addable = Math.min(need, headroom);
            const fix = addable > 0
                ? (addable >= need
                    ? `Add ${need} set${need === 1 ? "" : "s"} to your ${PART_LABEL[part].toLowerCase()} work`
                    : `Add ${addable} set${addable === 1 ? "" : "s"} to existing ${PART_LABEL[part].toLowerCase()} work (then add an exercise for the rest)`)
                : dayRoom
                    ? `Add a ${PART_LABEL[part].toLowerCase()} exercise (a day has room within your time budget)`
                    : slots.length === 0
                        ? `No direct ${PART_LABEL[part].toLowerCase()} work — add an exercise (sessions are at your time cap, consider a longer session length)`
                        : `${PART_LABEL[part]} exercises are at their set ceiling — add an exercise or raise session length`;
            if (addable > 0)
                volBias[part] = addable;
            issues.push({ part, label: PART_LABEL[part], v, mev, mrv, status: "under", need, fix, fits: addable >= need });
        }
        else if (v > mrv + 0.5) {
            const cut = Math.ceil(v - mrv);
            // How much of this muscle's volume is DIRECT (removable) vs secondary credit from compounds?
            // A muscle slightly over MRV is often there from spillover — e.g. triceps from all your
            // pressing — which can't be honestly fixed by cutting its isolation work (and you shouldn't
            // cut pressing to "fix" triceps). Only offer an auto-cut when there's removable direct volume
            // above a single working set; otherwise explain it's compound spillover.
            const directSets = slots.reduce((s, sl) => s + sl.sets, 0);
            const removable = slots.reduce((s, sl) => s + Math.max(0, sl.sets - 1), 0); // can't drop below 1/exercise
            const secondaryShare = v > 0 ? 1 - directSets / v : 0;
            // The spillover floor: volume this muscle gets from compounds even if we cut ALL removable
            // direct sets. If that floor alone is already over MRV, cutting isolation can't fix it — the
            // excess is structural pressing/pulling spillover. Offering a cut here creates the loop the
            // user hit: drop a triceps set, still over MRV next audit, button never goes away.
            const flooredVol = v - removable; // volume remaining if every removable direct set is cut
            const cutCanClear = flooredVol <= mrv + 0.5;
            if (removable >= cut && secondaryShare < 0.6 && cutCanClear) {
                volBias[part] = -cut;
                issues.push({ part, label: PART_LABEL[part], v, mev, mrv, status: "over", need: cut, fix: `Drop ${cut} set${cut === 1 ? "" : "s"} from ${PART_LABEL[part].toLowerCase()} accessory work to stay recoverable`, fits: true });
            }
            else if (secondaryShare >= 0.6 || !cutCanClear) {
                // mostly spillover, or cutting all direct work still wouldn't clear MRV — no auto-fix; the
                // excess comes from compound lifts feeding this muscle, not its isolation work.
                issues.push({ part, label: PART_LABEL[part], v, mev, mrv, status: "over", need: cut, fix: `Mostly spillover from your compound lifts — only ${fmtSets(directSets)} direct set${directSets === 1 ? "" : "s"}. A little over MRV here is usually fine; if it's a recovery problem, trim pressing/pulling frequency rather than ${PART_LABEL[part].toLowerCase()} isolation.`, fits: false });
            }
            else {
                // some removable, partial cut helps but won't fully clear — offer what it can
                if (removable > 0)
                    volBias[part] = -removable;
                issues.push({ part, label: PART_LABEL[part], v, mev, mrv, status: "over", need: cut, fix: removable > 0 ? `Drop ${removable} set${removable === 1 ? "" : "s"} from ${PART_LABEL[part].toLowerCase()} isolation (the rest is compound spillover — trim pressing/pulling if recovery suffers)` : `Driven by compound spillover — trim pressing/pulling frequency if recovery suffers`, fits: false });
            }
        }
    });
    // under-volume first (more actionable), then over
    issues.sort((a, b) => (a.status === b.status ? 0 : a.status === "under" ? -1 : 1));
    return { issues, volBias };
}

function previewVolumeNudge(program, history, part, fbDelta) {
    /* THE PROGRAM IS RIGHT THERE — pass it. This function exists so the badge and the engine can never
       describe different outcomes, and it would have started doing exactly that the moment the
       mutating path learned to defer to the plan and this preview did not. */
    const advice = (volumeAdvice(history || [], { program }).find(a => a.part === part) || {}).delta || 0;
    const want = clamp(Math.round(advice + (fbDelta || 0)), -2, 2);
    const out = { feedback: fbDelta || 0, advice, want, applied: 0, blocked: null, why: null };
    if (!program || !Array.isArray(program.days))
        return out;
    if (want === 0) {
        out.why = advice !== 0 || fbDelta ? "holding — the dose is landing" : null;
        return out;
    }
    const clone = { ...program, autoBias: undefined };
    try {
        distributeVolBias(clone, { [part]: want }, "autoBias");
    }
    catch {
        return out;
    }
    out.applied = Object.values(clone.autoBias || {}).reduce((a, b) => a + b, 0);
    if (out.applied === want)
        return out;
    /* NAME THE REASON RATHER THAN SHOWING A SMALLER NUMBER. "No room" is actionable — it means the
       program, not the rating, is the constraint — and it is the difference between a feature that
       looks broken and one the lifter can reason about. */
    const peak = weeksOf(program);
    const slots = [];
    program.days.forEach(d => d.exercises.forEach((id, si) => {
        if (EX_BY_ID[id]?.part === part)
            slots.push(Number(computeCell(program, d, id, si, peak).sets) || 0);
    }));
    const headroom = Math.floor(compositeMrv(part) - (weeklyVolume({ ...program, autoBias: undefined }, peak)[part] || 0));
    out.blocked = !slots.length ? "no-slot" : want > 0 && headroom <= 0 ? "at-mrv" : want > 0 ? "at-set-ceiling" : "at-floor";
    out.why = out.blocked === "no-slot" ? `nothing in this program trains ${PART_LABEL[part].toLowerCase()} directly`
        : out.blocked === "at-mrv" ? `already at the top of what you can recover from`
            : out.blocked === "at-set-ceiling" ? `every ${PART_LABEL[part].toLowerCase()} exercise is at its set ceiling — add a movement instead`
                : `already down to one working set per exercise`;
    return out;
}

function prescribedRIRofAny(s) {
    if (!s)
        return null;
    if (s.target)
        return prescribedRIRof(s);
    return Number.isFinite(s.tr) ? s.tr : null;
}

function rirTrend(history) {
    const MIN_SETS = 4; // a week needs this many RIR-logged sets to be a point at all
    const BASELINE_WEEKS = 4; // "prior" compares against recent weeks, not a lifetime mean
    const weekKey = weekKeyOf;
    const byWeek = {}, byWeekDev = {}, byWeekE = {};
    (history || []).forEach(h => Object.values(h.perf || {}).forEach(p => (p.sets || []).forEach(s => {
        const observedRir = observedHistoryRIR(s);
        if (observedRir == null || s.warm || s.sub || s.done === false || !(s.r > 0))
            return;
        const k = weekKey(h.date);
        (byWeek[k] = byWeek[k] || []).push(observedRir);
        const rx = prescribedRIRofAny(s);
        if (rx != null)
            (byWeekDev[k] = byWeekDev[k] || []).push(observedRir - rx);
        if (s.w > 0) {
            const e = e1rmRIR(convertHistoryLoad(s.w, h.unit, "kg"), s.r, 0);
            byWeekE[k] = Math.max(byWeekE[k] || 0, e);
        }
    })));
    const avg = a => a.reduce((x, y) => x + y, 0) / a.length;
    const weeks = Object.keys(byWeek).map(Number).sort((a, b) => a - b)
        .filter(k => byWeek[k].length >= MIN_SETS);
    const rawPts = weeks.map(k => avg(byWeek[k]));
    // Deviation is only usable where the plan is actually known for most of the week's sets. Sessions
    // logged before `tr` was persisted have no prescription on record; those weeks fall back to raw.
    const devCover = weeks.map(k => (byWeekDev[k] ? byWeekDev[k].length : 0) / byWeek[k].length);
    const haveDev = weeks.length > 0 && devCover.every(c => c >= 0.5);
    const devPts = haveDev ? weeks.map(k => avg(byWeekDev[k])) : [];
    const ePts = weeks.map(k => byWeekE[k] || 0);
    const n = weeks.reduce((a, k) => a + byWeek[k].length, 0);
    const mode = haveDev ? "vs-plan" : "raw";
    // The series the verdict reasons over: deviation when the plan is known, raw otherwise.
    const sig = haveDev ? devPts : rawPts;
    let declining2 = false, onPlanTaper = false;
    if (sig.length >= 3 && n >= 9) {
        const a = sig[sig.length - 3], b = sig[sig.length - 2], c = sig[sig.length - 1];
        const dropping = (b < a - 0.2 && c < b - 0.2) || (c <= a - 1.0);
        // Reserve falling while top-end loads climb is planned intensification, not fatigue outrunning
        // recovery — the same guard as before, and still worth keeping once deviation removes the
        // programming component.
        const eA = ePts[sig.length - 3], eC = ePts[sig.length - 1];
        const strengthStalled = !(eA > 0 && eC > 0) || eC < eA * 1.005;
        if (haveDev) {
            // With the plan removed, fatigue means genuinely grinding BELOW what was asked — a deviation
            // that is both falling and now meaningfully negative. Following a taper exactly sits at ~0 and
            // can never trip this, which is the whole point.
            declining2 = dropping && strengthStalled && c <= -0.5;
            // Raw reserve falling while deviation holds steady: the block asked for it. Worth saying out
            // loud, because the old card called this fatigue.
            const rawDrop = rawPts.length >= 3 && rawPts[rawPts.length - 1] <= rawPts[rawPts.length - 3] - 0.5;
            onPlanTaper = rawDrop && !declining2;
        }
        else {
            declining2 = dropping && strengthStalled;
        }
    }
    const last = arr => (arr.length ? arr[arr.length - 1] : null);
    const baseline = arr => {
        if (arr.length < 2)
            return null;
        const prev = arr.slice(Math.max(0, arr.length - 1 - BASELINE_WEEKS), arr.length - 1);
        return prev.length ? avg(prev) : null;
    };
    return {
        pts: rawPts, n, declining2, onPlanTaper, mode,
        latest: last(rawPts), prior: baseline(rawPts),
        devPts, devLatest: last(devPts), devPrior: baseline(devPts)
    };
}

function constantLoadDecay(history, exId) {
    // Collect (date, weight, reps, rir) for the TOP working set of each session of this lift.
    const sess = [];
    for (let i = (history || []).length - 1; i >= 0; i--) { // oldest → newest
        const p = history[i]?.perf?.[exId];
        if (!p)
            continue;
        const sets = (p.sets && p.sets.length) ? p.sets : [{ w: p.weight, r: p.reps, rir: null }];
        let best = null;
        for (const s of sets) {
            const w = parseFloat(s.w != null ? s.w : s.weight), r = parseInt(s.r != null ? s.r : s.reps);
            if (!(w > 0) || !(r > 0))
                continue;
            if (!best || w > best.w || (w === best.w && r > best.r))
                best = { w, r, rir: s.rir != null ? s.rir : null };
        }
        if (best)
            sess.push({ ...best, date: history[i].date });
    }
    if (sess.length < 3)
        return null;
    // Compare only sessions at the SAME load — that's what makes this identifiable. A rep drop at a
    // heavier weight tells you nothing; a rep drop at the same weight tells you everything.
    const recent = sess.slice(-6);
    const byWeight = new Map();
    recent.forEach(s => { const k = String(s.w); if (!byWeight.has(k))
        byWeight.set(k, []); byWeight.get(k).push(s); });
    // the load with the most repeat exposures is the one we can actually say something about
    let bestGroup = null;
    for (const g of byWeight.values())
        if (g.length >= 2 && (!bestGroup || g.length > bestGroup.length))
            bestGroup = g;
    if (!bestGroup)
        return null;
    const first = bestGroup[0], last = bestGroup[bestGroup.length - 1];
    const repDelta = last.r - first.r; // negative = decaying
    const rirDelta = (last.rir != null && first.rir != null) ? last.rir - first.rir : null; // negative = grinding harder
    return {
        weight: last.w, exposures: bestGroup.length, repDelta, rirDelta,
        // Overreaching on this lift = doing LESS work for the SAME or MORE effort at an unchanged load.
        decaying: repDelta <= -1 && (rirDelta == null || rirDelta <= 0)
    };
}

function overreachSignal(history, _program) {
    const hs = history || [];
    if (hs.length < 4)
        return { level: "none", lifts: [], why: "" };
    // only judge lifts trained often enough to compare
    const ids = new Set();
    hs.slice(0, 12).forEach(h => Object.keys(h.perf || {}).forEach(id => ids.add(id)));
    const decaying = [];
    let checked = 0;
    for (const id of ids) {
        const d = constantLoadDecay(hs, id);
        if (!d)
            continue;
        checked++;
        if (d.decaying)
            decaying.push({ id, name: EX_BY_ID[id]?.name || id, ...d });
    }
    if (checked < 2)
        return { level: "none", lifts: [], why: "" }; // not enough comparable lifts yet
    const frac = decaying.length / checked;
    // Two or more lifts going backwards at unchanged loads is the signal. One is noise — a bad night's
    // sleep, a missed meal, a heavy work week — and intervening on one lift's bad day is how you talk
    // someone out of training that was working.
    const deep = decaying.filter(d => d.repDelta <= -2).length;
    const level = (decaying.length >= 2 && frac >= 0.4) ? (deep >= 2 ? "high" : "moderate") : "none";
    const names = decaying.slice(0, 3).map(d => d.name).join(", ");
    return {
        level, lifts: decaying, checked,
        why: level === "none" ? ""
            : `${decaying.length} of ${checked} tracked lifts are losing reps at the same load (${names}${decaying.length > 3 ? "…" : ""}). Losing reps at an unchanged weight is fatigue, not a strength ceiling — the load hasn't got heavier, you've got more tired.`
    };
}

function deloadAdvice(history, program = null) {
    if ((history || []).length < 6)
        return null;
    // PHASE 4′: direct, identifiable overreach evidence — lifts losing reps at an UNCHANGED load.
    // This is the strongest signal available and it outranks the proxies below (PR droughts, RIR
    // drift), which can both fire for reasons that have nothing to do with fatigue.
    const over = overreachSignal(history, program);
    const trends = exerciseTrends(history);
    // A "stall" worth weighing toward a deload is a GENUINE multi-session plateau — not a lift that
    // merely failed to set an estimated-1RM PR in the last session or two (you don't PR every
    // session, so at any moment some lift is "2 sessions since a PR"). Require 4+ sessions without a
    // PR to count, 6+ to count as a hard stall.
    let stalled = 0, hardStall = 0;
    trends.forEach(t => { const pl = plateauOfLift(history, t); if (pl && pl.since >= 4) {
        stalled++;
        if (pl.since >= 6)
            hardStall++;
    } });
    const rt = rirTrend(history);
    let smashed = 0;
    (history || []).slice(0, 8).forEach(h => { if (h.feedback)
        smashed += Object.values(h.feedback).filter(v => v < 0).length; });
    // Deloads are earned by PERFORMANCE evidence (real plateaus) and sustained TRENDS (reps-in-reserve
    // falling over weeks, repeated "smashed" self-reports) — NEVER by instantaneous time-based
    // readiness, which is naturally low right after training and sits ~50-60% for anyone training
    // frequently. A momentary low recovery reading can no longer trigger a deload, nor justify one on
    // its own; the trend/self-report signals only escalate an already-evident stall.
    // PHASE 4′: constant-load rep decay is DIRECT evidence of accumulated fatigue, so it both
    // (a) triggers on its own when severe, and (b) lowers the bar for the proxy signals — a PR
    // drought means something quite different when your reps are also falling at the same weight
    // than it does when they aren't. The proxies alone keep their original, stricter thresholds:
    // a PR drought by itself is normal (you don't PR every session) and must not order a deload.
    const advised = over.level === "high"
        || (over.level === "moderate" && stalled >= 1)
        || hardStall >= 2
        || stalled >= 3
        || (stalled >= 2 && rt.declining2)
        || (stalled >= 2 && smashed >= 6);
    if (!advised)
        return null;
    const rec = muscleRecovery(history);
    const major = ["chest", "lats", "upper_back", "shoulders", "quads", "hamstrings", "glutes", "biceps", "triceps"];
    const mt = rec.filter(r => major.includes(r.part) && r.daysSince != null);
    const avgReady = mt.length ? mt.reduce((s, r) => s + r.readiness, 0) / mt.length : 100;
    const reasons = [];
    // Lead with the direct observation. It's the only reason here the lifter can check against their
    // own logbook — "you lost reps at the same weight" is falsifiable; "fatigue is outrunning
    // recovery" is a story we tell about it.
    if (over.why)
        reasons.push(over.why);
    /* Wording tracks what actually trips this now: reserve BELOW the prescription, not merely falling.
       A block that tapers reserve on purpose no longer lands here at all. */
    if (rt.declining2)
        reasons.push(rt.mode === "vs-plan" && rt.devLatest != null
            ? `you've been finishing sets about ${Math.abs(rt.devLatest).toFixed(1)} reps closer to failure than prescribed for 2+ weeks — a sign fatigue is outrunning recovery`
            : `your reps-in-reserve has been falling for 2+ weeks${rt.latest != null && rt.prior != null ? ` (now ~${rt.latest.toFixed(1)} vs ${rt.prior.toFixed(1)} earlier)` : ""} — a sign fatigue is outrunning recovery`);
    if (stalled >= 1)
        reasons.push(`${stalled} ${stalled === 1 ? "lift hasn't" : "lifts haven't"} set a PR in 4+ sessions`);
    if (smashed >= 4)
        reasons.push(`you've flagged muscles as "smashed" repeatedly`);
    return { reasons, stalled, avgReady: Math.round(avgReady), fatigue: rt.declining2, overreach: over.level, overreachLifts: over.lifts };
}

const PATTERN_COLORS = { push: "#3B82F6", pull: "#10B981", squat: "#F59E0B", hinge: "#A855F7" };

const LEVEL_COLORS = ["#8a8f98", "#6ea8fe", "#4db6ac", "#9ccc65", "#ffb74d", "#ff7043"];

const EFFORT_EASY = "#46b59c";

const EFFORT_HARD = "#ff7a45";

const STRENGTH_CLUB_LIFTS = [
    { id: "back-squat", label: "Squat" },
    { id: "bb-bench", label: "Bench" },
    { id: "deadlift", label: "Deadlift" },
];

const STRENGTH_CLUB_TIERS_LB = [
    { lb: 600, name: "600 lb Club" },
    { lb: 800, name: "800 lb Club" },
    { lb: 1000, name: "1,000 lb Club" },
    { lb: 1200, name: "1,200 lb Club" },
    { lb: 1500, name: "1,500 lb Club" },
];

function strengthClubSnapshot(trends, unit, mode = "lifted") {
    const rows = STRENGTH_CLUB_LIFTS.map(l => {
        const t = (trends || []).find(x => x.id === l.id);
        const actual = !!(t && t.prTopW > 0);
        const raw = !t ? 0 : (mode === "lifted" ? (actual ? t.prTopW : t.prBest) : t.prBest);
        const val = t ? toUnit(raw, t.unit, unit) : 0;
        return { ...l, val, has: !!t, actual, sourceUnit: t?.unit || unit };
    });
    const hasAny = rows.some(r => r.has);
    const allLogged = rows.every(r => r.has && r.val > 0);
    const allActual = rows.every(r => r.actual && r.val > 0);
    const complete = allLogged && (mode !== "lifted" || allActual);
    const total = rows.reduce((sum, r) => sum + (r.val || 0), 0);
    const totalLb = toUnit(total, unit, "lb");
    const achieved = complete ? STRENGTH_CLUB_TIERS_LB.filter(t => totalLb >= t.lb) : [];
    const current = achieved.length ? achieved[achieved.length - 1] : null;
    const next = complete
        ? (STRENGTH_CLUB_TIERS_LB.find(t => totalLb < t.lb) || STRENGTH_CLUB_TIERS_LB[STRENGTH_CLUB_TIERS_LB.length - 1])
        : STRENGTH_CLUB_TIERS_LB.find(t => t.lb === 1000);
    const maxedOut = complete && achieved.length === STRENGTH_CLUB_TIERS_LB.length;
    const prevLb = current ? current.lb : 0;
    const span = next.lb - prevLb;
    const pct = maxedOut || span <= 0 ? 100 : Math.max(0, Math.min(100, ((totalLb - prevLb) / span) * 100));
    const nextInUnit = toUnit(next.lb, "lb", unit);
    const remaining = maxedOut ? 0 : Math.max(0, nextInUnit - total);
    const thousandTarget = toUnit(1000, "lb", unit);
    const thousandRemaining = Math.max(0, thousandTarget - total);
    const thousandPct = Math.max(0, Math.min(100, thousandTarget > 0 ? (total / thousandTarget) * 100 : 0));
    const inThousand = complete && totalLb >= 1000;
    return { mode, unit, rows, hasAny, allLogged, allActual, complete, total, totalLb, achieved, current, next, maxedOut, pct, remaining, thousandTarget, thousandRemaining, thousandPct, inThousand };
}

function shareStrengthClub(snapshot, theme) {
    if (!snapshot?.complete || !snapshot?.inThousand || typeof document === "undefined")
        return;
    const C = theme || {};
    const W = 1080, H = 1350, PAD = 96, CW = W - PAD * 2;
    const cnv = document.createElement("canvas");
    cnv.width = W;
    cnv.height = H;
    const ctx = cnv.getContext("2d");
    if (!ctx)
        return;
    const rr = (x, y, w, h, r) => { ctx.beginPath(); if (ctx.roundRect)
        ctx.roundRect(x, y, w, h, r);
    else
        ctx.rect(x, y, w, h); };
    const font = (weight, px) => { ctx.font = `${weight} ${px}px ${SHARE_FONT}`; };
    const fmt = (v) => snapshot.unit === "lb" ? Math.round(v).toLocaleString() : (Math.round(v * 2) / 2).toLocaleString();
    const tier = snapshot.current?.name || "1,000 lb Club";
    const projected = snapshot.mode === "e1rm";
    const title = projected ? `PROJECTED ${tier.toUpperCase()}` : tier.toUpperCase();
    const accent = C.accent || "#2563EB";
    ctx.fillStyle = C.bg || "#070A0F";
    ctx.fillRect(0, 0, W, H);
    ctx.fillStyle = C.card || "#11161F";
    rr(54, 54, W - 108, H - 108, 44);
    ctx.fill();
    ctx.fillStyle = accent;
    rr(54, 54, W - 108, 18, 9);
    ctx.fill();
    ctx.textBaseline = "top";
    ctx.fillStyle = accent;
    font(800, 28);
    ctx.fillText("PURSUIT IRON · STRENGTH CLUB", PAD, 130);
    ctx.fillStyle = C.text || "#E6EBF2";
    fitFont(ctx, title, CW, 900, 68, 38);
    ctx.fillText(fitText(ctx, title, CW), PAD, 194);
    ctx.fillStyle = C.muted || "#8C97A8";
    font(600, 30);
    ctx.fillText(projected ? "BEST-SET ESTIMATED 1RM TOTAL" : "HEAVIEST LIFTED TOTAL", PAD, 286);
    ctx.fillStyle = C.text || "#E6EBF2";
    font(900, 170);
    const totalText = fmt(snapshot.total);
    ctx.fillText(totalText, PAD, 346);
    const totalW = ctx.measureText(totalText).width;
    ctx.fillStyle = C.muted || "#8C97A8";
    font(800, 48);
    ctx.fillText(snapshot.unit, Math.min(PAD + totalW + 18, W - PAD - 80), 446);
    const gap = 18, tileW = (CW - gap * 2) / 3, tileY = 624;
    snapshot.rows.forEach((r, i) => {
        const x = PAD + i * (tileW + gap);
        ctx.fillStyle = C.bg2 || "#19202C";
        rr(x, tileY, tileW, 220, 24);
        ctx.fill();
        ctx.fillStyle = C.muted || "#8C97A8";
        font(750, 26);
        ctx.textAlign = "center";
        ctx.fillText(r.label.toUpperCase(), x + tileW / 2, tileY + 34);
        ctx.fillStyle = C.text || "#E6EBF2";
        fitFont(ctx, fmt(r.val), tileW - 28, 900, 62, 38);
        ctx.fillText(fitText(ctx, fmt(r.val), tileW - 28), x + tileW / 2, tileY + 92);
        ctx.fillStyle = C.faint || "#646D7B";
        font(700, 24);
        ctx.fillText(snapshot.unit, x + tileW / 2, tileY + 164);
        ctx.textAlign = "left";
    });
    ctx.fillStyle = C.muted || "#8C97A8";
    font(500, 28);
    const foot = projected ? "Projected from the best logged set on squat, bench and deadlift." : "Heaviest logged squat + bench + deadlift.";
    ctx.fillText(fitText(ctx, foot, CW), PAD, 910);
    if (projected) {
        ctx.fillStyle = C.faint || "#646D7B";
        font(650, 24);
        ctx.fillText("Traditional club membership is based on weight actually lifted.", PAD, 958);
    }
    drawShareBrand(ctx, C, PAD, H - 150, CW, "squat · bench · deadlift");
    const done = (blob) => {
        const fileName = projected ? "pursuit-iron-projected-strength-club.png" : "pursuit-iron-1000-club.png";
        try {
            if (navigator.canShare && typeof File !== "undefined") {
                const file = new File([blob], fileName, { type: "image/png" });
                if (navigator.canShare({ files: [file] })) {
                    navigator.share({ files: [file], title: projected ? `Projected ${tier}` : tier }).catch(() => { });
                    return;
                }
            }
        }
        catch { }
        try {
            const url = URL.createObjectURL(blob);
            const a = document.createElement("a");
            a.href = url;
            a.download = fileName;
            document.body.appendChild(a);
            a.click();
            a.remove();
            setTimeout(() => URL.revokeObjectURL(url), 1500);
        }
        catch { }
    };
    if (cnv.toBlob)
        cnv.toBlob(b => b && done(b), "image/png");
    else {
        const a = document.createElement("a");
        a.href = cnv.toDataURL("image/png");
        a.download = "pursuit-iron-1000-club.png";
        a.click();
    }
}

const BIG_THREE = { bench: "bb-bench", squat: "back-squat", deadlift: "deadlift" };

function computeMilestones(history, opts = {}) {
    const unit = opts.unit || "kg";
    const bw = parseFloat(opts.bodyweight) > 0 ? parseFloat(opts.bodyweight) : 0; // already in `unit`
    const n = history.length;
    // Sessions are stored in the unit they were logged in. Summing them raw double-counts anyone who
    // has ever switched kg <-> lb, so normalise into the unit the lifter is reading right now.
    const totalVol = history.reduce((a, h) => a + toUnit(h.volume || 0, h.unit || unit, unit), 0);
    const weekKey = weekKeyOf;
    const byWeek = {}, partsByWeek = {}, idsByWeek = {};
    history.forEach(h => {
        const k = weekKey(h.date);
        byWeek[k] = (byWeek[k] || 0) + 1;
        // Distinct muscle groups touched in the week, for the coverage badge. Unknown ids (a custom
        // exercise, a lift retired from the library) simply don't contribute rather than counting as
        // a mystery group.
        const set = partsByWeek[k] || (partsByWeek[k] = new Set());
        const ids = idsByWeek[k] || (idsByWeek[k] = new Set());
        Object.keys(h.perf || {}).forEach(id => { ids.add(id); const ex = EX_BY_ID[id]; if (ex && ex.part)
            set.add(ex.part); });
    });
    // Squat, bench and deadlift all inside one training week — the frequency the big lifts actually
    // want. A week, not a session: pressing and pulling heavy on the same day is a different thing.
    const bigThreeWeek = Object.values(idsByWeek).some(x => x.has(BIG_THREE.bench) && x.has(BIG_THREE.squat) && x.has(BIG_THREE.deadlift)) ? 1 : 0;
    const weeks = Object.keys(byWeek).map(Number).sort((a, b) => a - b);
    const maxInWeek = weeks.length ? Math.max(...Object.values(byWeek)) : 0;
    const bestPartsWeek = weeks.length ? Math.max(...Object.values(partsByWeek).map(x => x.size)) : 0;
    // Longest run of CONSECUTIVE weeks that each held 3+ sessions. Stricter than the plain week streak
    // above, which a single session a week satisfies.
    let fullRun = 0, bestFullRun = 0;
    for (let i = 0; i < weeks.length; i++) {
        const consec = i > 0 && Math.round((weeks[i] - weeks[i - 1]) / 86400000) === 7;
        fullRun = byWeek[weeks[i]] >= 3 ? (consec ? fullRun + 1 : 1) : 0;
        bestFullRun = Math.max(bestFullRun, fullRun);
    }
    let streak = 0, best = 0;
    // Rounded week-count, not exact millisecond equality — the same DST-transition fragility as the
    // daily streak above (a week spanning a DST change is ±1 hour off 7*86400000ms exactly).
    for (let i = 0; i < weeks.length; i++) {
        streak = (i > 0 && Math.round((weeks[i] - weeks[i - 1]) / 86400000) === 7) ? streak + 1 : 1;
        best = Math.max(best, streak);
    }
    // Walk the log oldest-first: personal records, exercise breadth, training hours, and the best e1RM
    // reached on each of the big three.
    const chrono = history.slice().sort((a, b) => a.date - b.date);
    const bestE1 = {}; // exercise -> best e1RM so far, in `unit`
    let prs = 0, bestPrDay = 0, earlyBird = 0, nightOwl = 0, comeback = 0, prevDate = null;
    // Hours under the bar, weekend sessions, and the single biggest session — the comment above has
    // always claimed this walk collects training hours; now it actually does.
    let totalMin = 0, weekendCount = 0, maxSessionVol = 0, totalSets = 0, totalReps = 0;
    const seenExercises = new Set();
    const sessionsPerLift = {}; // exercise -> how many separate sessions it has appeared in
    for (const h of chrono) {
        const hUnit = h.unit || unit;
        const hour = new Date(h.date).getHours();
        if (hour < 7)
            earlyBird++;
        if (hour >= 21)
            nightOwl++;
        // A gap of two weeks or more, followed by this session: they came back. That deserves a badge,
        // not a broken streak and silence.
        if (prevDate && h.date - prevDate >= 14 * 86400000)
            comeback = 1;
        prevDate = h.date;
        // A missing or junk duration counts as zero rather than NaN-ing the running total.
        totalMin += Math.max(0, parseFloat(h.durationMin) || 0);
        const dow = new Date(h.date).getDay();
        if (dow === 0 || dow === 6)
            weekendCount++;
        maxSessionVol = Math.max(maxSessionVol, toUnit(h.volume || 0, hUnit, unit));
        let prsToday = 0;
        for (const [id, p] of Object.entries(h.perf || {})) {
            seenExercises.add(id);
            sessionsPerLift[id] = (sessionsPerLift[id] || 0) + 1;
            /* Counted on reps, not weight. A set of chin-ups or planks logs no load, and gating on w > 0
               would quietly decide that bodyweight work isn't work — the one population most likely to be
               training entirely without a barbell would earn nothing here. */
            setsOf(p).forEach(x => {
                const r = parseInt(x.r != null ? x.r : x.reps);
                if (r > 0) {
                    totalSets++;
                    totalReps += r;
                }
            });
            const e1 = toUnit(sessionE1RM(p), hUnit, unit);
            if (!(e1 > 0))
                continue;
            // Only counts as a PR if we've seen the lift before — otherwise every new exercise would hand
            // out a free record on its first outing.
            if (bestE1[id] != null && e1 > bestE1[id]) {
                prs++;
                prsToday++;
            }
            if (bestE1[id] == null || e1 > bestE1[id])
                bestE1[id] = e1;
        }
        bestPrDay = Math.max(bestPrDay, prsToday);
    }
    // Bodyweight-relative strength. Locked (value 0) until a bodyweight is set in Settings, rather than
    // silently comparing against nothing.
    const ratio = (id) => (bw > 0 && bestE1[id] > 0) ? bestE1[id] / bw : 0;
    const benchR = ratio(BIG_THREE.bench), squatR = ratio(BIG_THREE.squat), deadR = ratio(BIG_THREE.deadlift);
    /* Combined big-three total, as a multiple of bodyweight. Requires ALL THREE to have been logged —
       summing whatever exists would let a strong bench and squat alone clear a total that is supposed
       to represent three lifts, which is exactly the "lie about progress" this board is meant to
       avoid. Two lifts and a blank is not a total. */
    const bigThreeR = (benchR > 0 && squatR > 0 && deadR > 0) ? benchR + squatR + deadR : 0;
    const totalHours = totalMin / 60;
    const maxLiftSessions = Object.keys(sessionsPerLift).length ? Math.max(...Object.values(sessionsPerLift)) : 0;
    // Elapsed days between first and last logged session. Deliberately NOT a streak: time served
    // counts even across breaks, so a layoff never erases it.
    const spanDays = chrono.length > 1 ? Math.round((chrono[chrono.length - 1].date - chrono[0].date) / 86400000) : 0;
    const tiers = [
        // showing up
        { id: "first", cat: "Showing up", icon: "🌱", label: "First Step", desc: "Log your first workout", target: 1, value: n },
        { id: "five", cat: "Showing up", icon: "🔥", label: "Getting Going", desc: "5 workouts logged", target: 5, value: n },
        { id: "fifteen", cat: "Showing up", icon: "💪", label: "Committed", desc: "15 workouts logged", target: 15, value: n },
        { id: "fifty", cat: "Showing up", icon: "🏋️", label: "Iron Habit", desc: "50 workouts logged", target: 50, value: n },
        { id: "century", cat: "Showing up", icon: "🏆", label: "Century", desc: "100 workouts logged", target: 100, value: n },
        { id: "veteran", cat: "Showing up", icon: "🗿", label: "Veteran", desc: "250 workouts logged", target: 250, value: n },
        { id: "halfk", cat: "Showing up", icon: "🛡️", label: "Ironclad", desc: "500 workouts logged", target: 500, value: n },
        // consistency
        { id: "week3", cat: "Consistency", icon: "📅", label: "Week Warrior", desc: "3 workouts in one week", target: 3, value: maxInWeek },
        { id: "week5", cat: "Consistency", icon: "🗓️", label: "Five in Seven", desc: "5 workouts in one week", target: 5, value: maxInWeek },
        { id: "streak3", cat: "Consistency", icon: "⚡", label: "On a Roll", desc: "Train 3 weeks straight", target: 3, value: best },
        { id: "streak8", cat: "Consistency", icon: "🔗", label: "Unbroken", desc: "Train 8 weeks straight", target: 8, value: best },
        { id: "streak26", cat: "Consistency", icon: "🧱", label: "Half a Year", desc: "Train 26 weeks straight", target: 26, value: best },
        { id: "streak52", cat: "Consistency", icon: "👑", label: "Year of Iron", desc: "Train 52 weeks straight", target: 52, value: best },
        { id: "fullmonth", cat: "Consistency", icon: "📆", label: "Solid Month", desc: "4 straight weeks with 3+ workouts each", target: 4, value: bestFullRun },
        { id: "comeback", cat: "Consistency", icon: "🔄", label: "Back at It", desc: "Return to training after a break of 2 weeks or more", target: 1, value: comeback },
        // Time served, not time unbroken — these survive a layoff on purpose.
        { id: "year1", cat: "Consistency", icon: "🎂", label: "One Year In", desc: "A year between your first and latest workout", target: 365, value: spanDays },
        { id: "year2", cat: "Consistency", icon: "🌳", label: "Two Years In", desc: "Two years between your first and latest workout", target: 730, value: spanDays },
        // getting stronger
        { id: "pr1", cat: "Getting stronger", icon: "🎯", label: "New Best", desc: "Beat a previous best on any lift", target: 1, value: prs },
        { id: "pr10", cat: "Getting stronger", icon: "💥", label: "Record Breaker", desc: "Set 10 personal records", target: 10, value: prs },
        { id: "pr50", cat: "Getting stronger", icon: "🚀", label: "PR Machine", desc: "Set 50 personal records", target: 50, value: prs },
        { id: "pr100", cat: "Getting stronger", icon: "🌟", label: "Unstoppable", desc: "Set 100 personal records", target: 100, value: prs },
        { id: "prday3", cat: "Getting stronger", icon: "🎆", label: "Big Day", desc: "Set 3 personal records in one session", target: 3, value: bestPrDay },
        { id: "prday5", cat: "Getting stronger", icon: "🎇", label: "Perfect Storm", desc: "Set 5 personal records in one session", target: 5, value: bestPrDay },
        { id: "bwBench", cat: "Getting stronger", icon: "🏅", label: "Bodyweight Bench", desc: "Bench press your own bodyweight", target: 1, value: benchR },
        { id: "bwSquat", cat: "Getting stronger", icon: "🦿", label: "Squat 1.5×", desc: "Squat 1.5× your bodyweight", target: 1.5, value: squatR },
        { id: "bwDead", cat: "Getting stronger", icon: "🐘", label: "Deadlift 2×", desc: "Deadlift 2× your bodyweight", target: 2, value: deadR },
        { id: "bwBench15", cat: "Getting stronger", icon: "🥇", label: "Bench 1.5×", desc: "Bench press 1.5× your bodyweight", target: 1.5, value: benchR },
        { id: "bwSquat2", cat: "Getting stronger", icon: "🦵", label: "Squat 2×", desc: "Squat 2× your bodyweight", target: 2, value: squatR },
        { id: "bwDead25", cat: "Getting stronger", icon: "🐋", label: "Deadlift 2.5×", desc: "Deadlift 2.5× your bodyweight", target: 2.5, value: deadR },
        { id: "bigThree4", cat: "Getting stronger", icon: "🏛️", label: "Total Package", desc: "Bench + squat + deadlift equal to 4× your bodyweight", target: 4, value: bigThreeR },
        { id: "bigThree5", cat: "Getting stronger", icon: "⚜️", label: "Elite Total", desc: "Bench + squat + deadlift equal to 5× your bodyweight", target: 5, value: bigThreeR },
        // breadth
        { id: "variety25", cat: "Breadth", icon: "🧭", label: "Explorer", desc: "Train 25 different exercises", target: 25, value: seenExercises.size },
        { id: "variety50", cat: "Breadth", icon: "🗺️", label: "Well Rounded", desc: "Train 50 different exercises", target: 50, value: seenExercises.size },
        { id: "variety100", cat: "Breadth", icon: "🌐", label: "Cartographer", desc: "Train 100 different exercises", target: 100, value: seenExercises.size },
        { id: "parts8", cat: "Breadth", icon: "🧩", label: "Full Coverage", desc: "Train 8 different muscle groups in one week", target: 8, value: bestPartsWeek },
        // when you train
        { id: "earlybird", cat: "When you train", icon: "🌅", label: "Early Bird", desc: "10 workouts started before 7am", target: 10, value: earlyBird },
        { id: "nightowl", cat: "When you train", icon: "🌙", label: "Night Owl", desc: "10 workouts started after 9pm", target: 10, value: nightOwl },
        { id: "weekend25", cat: "When you train", icon: "🎽", label: "Weekend Warrior", desc: "25 workouts on a Saturday or Sunday", target: 25, value: weekendCount },
        // tonnage
        { id: "vol1", cat: "Tonnage", icon: "🪨", label: "Tonnage", desc: "50k total volume lifted", target: 50000, value: totalVol },
        { id: "vol2", cat: "Tonnage", icon: "⛰️", label: "Heavy Mover", desc: "250k total volume lifted", target: 250000, value: totalVol },
        { id: "vol3", cat: "Tonnage", icon: "🌋", label: "Million Club", desc: "1M total volume lifted", target: 1000000, value: totalVol },
        { id: "vol4", cat: "Tonnage", icon: "🌌", label: "Mountain Mover", desc: "5M total volume lifted", target: 5000000, value: totalVol },
        { id: "vol5", cat: "Tonnage", icon: "🪐", label: "Continental", desc: "10M total volume lifted", target: 10000000, value: totalVol },
        { id: "bigSession", cat: "Tonnage", icon: "💣", label: "Monster Session", desc: "15k volume in a single workout", target: 15000, value: maxSessionVol },
        // Hours under the bar. Rewards the work itself rather than how it was scheduled — an hour is
        // an hour whether it came in a streak or after six months off.
        { id: "hours10", cat: "Time under the bar", icon: "🕐", label: "Ten Hours", desc: "10 hours of logged training", target: 10, value: totalHours },
        { id: "hours50", cat: "Time under the bar", icon: "⏳", label: "Fifty Hours", desc: "50 hours of logged training", target: 50, value: totalHours },
        { id: "hours100", cat: "Time under the bar", icon: "🕰️", label: "Hundred Hours", desc: "100 hours of logged training", target: 100, value: totalHours },
        { id: "hours500", cat: "Time under the bar", icon: "🗼", label: "Five Hundred Hours", desc: "500 hours of logged training", target: 500, value: totalHours },
        // Sets and reps — the unit of work that isn't load. Counts bodyweight training as fully as
        // barbell training, which the tonnage badges above structurally cannot.
        { id: "sets1k", cat: "Sets & reps", icon: "🧮", label: "Set Collector", desc: "1,000 sets logged", target: 1000, value: totalSets },
        { id: "sets10k", cat: "Sets & reps", icon: "🏗️", label: "Ten Thousand Sets", desc: "10,000 sets logged", target: 10000, value: totalSets },
        { id: "reps10k", cat: "Sets & reps", icon: "🔁", label: "Ten Thousand Reps", desc: "10,000 reps logged", target: 10000, value: totalReps },
        { id: "reps100k", cat: "Sets & reps", icon: "♾️", label: "Hundred Thousand Reps", desc: "100,000 reps logged", target: 100000, value: totalReps },
        // Devotion — sticking with a lift long enough to actually get good at it.
        { id: "lift50", cat: "Devotion", icon: "🤝", label: "Old Faithful", desc: "Train the same exercise in 50 sessions", target: 50, value: maxLiftSessions },
        { id: "lift100", cat: "Devotion", icon: "🪢", label: "Lifelong Lift", desc: "Train the same exercise in 100 sessions", target: 100, value: maxLiftSessions },
        { id: "bigThreeWeek", cat: "Devotion", icon: "🎳", label: "The Big Three", desc: "Squat, bench and deadlift all in one week", target: 1, value: bigThreeWeek },
    ];
    return tiers.map(t => ({ ...t, done: t.value >= t.target, progress: Math.min(1, t.value / t.target) }));
}

const MILESTONE_XP = {
    first: 10, five: 20, fifteen: 40, fifty: 100, century: 250, veteran: 600, halfk: 1500,
    week3: 15, week5: 30, streak3: 40, streak8: 120, streak26: 400, streak52: 900, comeback: 20,
    fullmonth: 60, year1: 300, year2: 700,
    pr1: 15, pr10: 60, pr50: 250, pr100: 500, prday3: 80, prday5: 150,
    bwBench: 120, bwSquat: 150, bwDead: 180, bwBench15: 220, bwSquat2: 260, bwDead25: 300,
    bigThree4: 350, bigThree5: 800,
    variety25: 60, variety50: 150, variety100: 300, parts8: 80,
    earlybird: 50, nightowl: 50, weekend25: 70,
    vol1: 40, vol2: 150, vol3: 500, vol4: 1500, vol5: 3000, bigSession: 90,
    hours10: 30, hours50: 100, hours100: 200, hours500: 800,
    sets1k: 80, sets10k: 400, reps10k: 80, reps100k: 500,
    lift50: 100, lift100: 250, bigThreeWeek: 60
};

const LEVEL_TITLES = [
    [1, "Newcomer"], [5, "Regular"], [10, "Dedicated"], [16, "Iron Disciple"], [22, "Forged"],
    [30, "Relentless"], [40, "Iron Veteran"], [50, "Legend"], [65, "Titan"], [80, "Mythic"],
];

function computeLevel(history, milestones) {
    const n = (history || []).length;
    const K = 8; // tuned so level 2 lands on your first workout and a maxed multi-year veteran lands ~level 35-40
    const milestoneXP = (milestones || []).filter(m => m.done).reduce((s, m) => s + (MILESTONE_XP[m.id] ?? 15), 0);
    const xp = n * 8 + milestoneXP;
    const level = Math.max(1, Math.floor(1 + Math.sqrt(xp / K)));
    const xpAtLevel = K * (level - 1) * (level - 1);
    const xpForNext = K * level * level;
    const progress = xpForNext > xpAtLevel ? clamp((xp - xpAtLevel) / (xpForNext - xpAtLevel), 0, 1) : 1;
    const title = LEVEL_TITLES.filter(([lvl]) => lvl <= level).slice(-1)[0]?.[1] || "Newcomer";
    return { level, xp, xpIntoLevel: xp - xpAtLevel, xpForLevel: xpForNext - xpAtLevel, progress, title };
}

function normalizeBwLog(bwLog, to) {
    return (bwLog || [])
        .filter(e => e && e.date != null && Number(e.w) > 0)
        .map(e => ({ ...e, w: e.unit && to && e.unit !== to ? Math.round(toUnit(Number(e.w), e.unit, to) * 10) / 10 : Number(e.w), unit: to || e.unit }))
        .sort((a, b) => a.date - b.date);
}

let _planOverviewMemo = null;

function planOverviewMemo(program, weekIndex, cycle, history, saved) {
    const m = _planOverviewMemo;
    if (m && m.program === program && m.weekIndex === weekIndex && m.cycle === cycle && m.history === history
        && m.saved === saved && m.restScale === REST_SCALE && m.exercises.length === EXERCISES.length
        && m.exercises.every((ex, i) => ex === EXERCISES[i]))
        return m.value;
    const value = planOverview(program, weekIndex, cycle, history, saved);
    _planOverviewMemo = { program, weekIndex, cycle, history, saved, restScale: REST_SCALE,
        exercises: EXERCISES.slice(), value };
    return value;
}

function planOverview(program, weekIndex = 1, cycle = null, history = [], saved = []) {
    if (!program || !Array.isArray(program.days) || !program.days.length)
        return null;
    const days = program.days;
    const accum = weeksOf(program);
    const hasDeload = !!(program.config && program.config.deload);
    const total = accum + (hasDeload ? 1 : 0);
    /* WEEKS × DAYS. Each cell is that day at that week — total working sets and the estimated length,
       both already week-aware, so a deload week visibly shrinks instead of being a label on identical
       numbers. Storing dayId per cell lets the UI open the real day at the real week on tap. */
    /* SESSIONS ALREADY LOGGED, per week. History entries carry `weekIndex`, so the plan can say how
       much of each week you actually did rather than only what it prescribes — which is the difference
       between a plan you read and a plan you are inside of. */
    const doneByWeek = {}, doneDaysByWeek = {};
    for (const h of history || []) {
        if (!h || h.programId !== program.id)
            continue;
        const w = Number(h.weekIndex);
        if (!Number.isFinite(w) || w < 1)
            continue;
        doneByWeek[w] = (doneByWeek[w] || 0) + 1;
        if (h.dayId)
            (doneDaysByWeek[w] = doneDaysByWeek[w] || new Set()).add(h.dayId);
    }
    const weeks = [];
    for (let w = 1; w <= total; w++) {
        const isDeload = hasDeload && w === total;
        const done = Math.min(doneByWeek[w] || 0, days.length);
        /* status drives the badge. Exactly one week is NOW and at most one is NEXT, so the list has a
           single obvious entry point rather than several competing highlights. */
        const status = w === weekIndex ? "now" : w === weekIndex + 1 ? "next" : w < weekIndex ? "past" : "todo";
        const row = { week: w, isDeload, current: w === weekIndex, status, done, total: days.length, days: [], sets: 0, minutes: 0 };
        for (const d of days) {
            let sets = 0;
            (d.exercises || []).forEach((id, si) => {
                const c = computeCell(program, d, id, si, w);
                sets += Number(c && c.sets) || 0;
            });
            const mins = estimateMinutes(program, d, w) || 0;
            /* WHAT THE DAY ACTUALLY IS, not just how long it takes. A row reading "Push · Chest · 27 sets
               · 62m" describes a container; the lifter wants to know what is IN it. The lead lift and the
               muscles it works are the two things that distinguish one day from another at a glance, and
               both are already derivable — no new stored state. */
            const exIds = d.exercises || [];
            const leadId = exIds[d.primaryIndex] || exIds[0];
            const leadEx = leadId ? EX_BY_ID[leadId] : null;
            const focus = dayMuscleVolume(program, d, w).slice(0, 3).map(([part]) => part);
            row.days.push({ dayId: d.id, label: d.label || "Day", sets, minutes: mins,
                exCount: exIds.length, lead: leadEx ? leadEx.name : null, focus,
                lifts: exIds.map(id => (EX_BY_ID[id] ? EX_BY_ID[id].name : null)).filter(Boolean),
                done: !!(doneDaysByWeek[w] && doneDaysByWeek[w].has(d.id)) });
            row.sets += sets;
            row.minutes += mins;
        }
        weeks.push(row);
    }
    /* PER-LIFT PROGRESSION. One row per exercise that appears anywhere in the block, carrying its
       prescription for every week. A lift can sit in different slots on different days, so it is keyed
       by exercise id and takes its FIRST appearance — the progression is a property of the lift, and
       showing one row per (day, slot) would turn a readable table into noise. */
    const liftMap = new Map();
    days.forEach(d => (d.exercises || []).forEach((id, si) => {
        if (liftMap.has(id))
            return;
        const ex = EX_BY_ID[id];
        if (!ex)
            return;
        liftMap.set(id, {
            id, name: ex.name, dayLabel: d.label || "Day", primary: si === d.primaryIndex,
            cells: Array.from({ length: total }, (_, i) => {
                const c = computeCell(program, d, id, si, i + 1) || {};
                return { week: i + 1, sets: c.sets, reps: c.reps, rir: c.rir, note: c.note,
                    isDeload: hasDeload && i + 1 === total };
            })
        });
    }));
    /* primaries first, then the order they appear — the lift you build the block around reads first */
    const lifts = [...liftMap.values()].sort((a, b) => (a.primary === b.primary ? 0 : a.primary ? -1 : 1));
    /* PHASES. A cycle's blocks are the real phases and are labelled; a standalone program still HAS
       phases — accumulation, then the deload — and saying so is more honest than showing nothing. */
    let phases;
    if (cycle && Array.isArray(cycle.blockMeta) && cycle.blockMeta.length) {
        const here = cycle.blockIds ? cycle.blockIds.indexOf(program.id) : -1;
        phases = cycleBlockMetadata(cycle, [...saved.filter(p => p.id !== program.id), program]).map((m, i) => ({
            key: m.id || `b${i}`, label: m.label || `Block ${i + 1}`, goal: m.goal || null,
            weeks: m.weeks || null, deload: m.deload, current: i === here, done: here >= 0 && i < here, isBlock: true
        }));
    }
    else {
        phases = [{ key: "accum", label: "Accumulation", goal: program.config?.goal || null, weeks: accum, current: weekIndex <= accum, done: weekIndex > accum, isBlock: false }];
        if (hasDeload)
            phases.push({ key: "deload", label: "Deload", goal: null, weeks: 1, current: weekIndex > accum, done: false, isBlock: false });
    }
    /* THE NEXT THING TO DO. A plan that cannot answer "what now" is a document, not a plan. It is the
       first unlogged day of the CURRENT week; if the week is complete it rolls to the next week's
       first day, and at the end of the block there is nothing left to point at. */
    let upNext = null;
    for (const w of weeks) {
        if (w.week < weekIndex)
            continue;
        const d = w.days.find(x => !x.done);
        if (d) {
            upNext = { ...d, week: w.week };
            break;
        }
    }
    const sessionsTotal = total * days.length;
    const sessionsDone = weeks.reduce((n, w) => n + w.done, 0);
    /* WEEKS BELONG TO PHASES. Without this the list is a flat run of weeks and the phase strip is
       decoration; with it the plan reads as structure — Foundation weeks 1-5, then Development. */
    /* A CYCLE'S PHASES ARE OTHER PROGRAMS, NOT OTHER WEEKS OF THIS ONE.
       The first version walked a cursor across THIS program's weeks and clamped each phase to its
       length, so on a 6-week block inside a 3-block cycle the strip printed "Strength W6-6" and
       "Peak W10-6" — ranges that are empty, and wrong. Block spans are cumulative over the CYCLE, so
       they must be summed from blockMeta and never clamped to the current block's length.
       Every week of this program belongs to the block it IS. Weeks are only distributed across phases
       in the standalone case, where the phases really are stretches of one program. */
    {
        let cursor = 0;
        for (const ph of phases) {
            const span = (ph.weeks || (ph.key === "deload" ? 1 : ph.isBlock ? 0 : total)) + (ph.isBlock && ph.deload ? 1 : 0);
            ph.weekFrom = cursor + 1;
            ph.weekTo = cursor + Math.max(span, 1);
            cursor += Math.max(span, 1);
        }
        const currentPhaseKey = (phases.find(p => p.current) || phases[0] || {}).key || null;
        for (const w of weeks) {
            if (phases.some(p => p.isBlock)) {
                w.phaseKey = currentPhaseKey;
                continue;
            }
            const ph = phases.find(p => w.week >= p.weekFrom && w.week <= p.weekTo) || phases[phases.length - 1];
            w.phaseKey = ph ? ph.key : null;
        }
    }
    return { weeks, lifts, phases, totalWeeks: total, accumWeeks: accum, hasDeload, currentWeek: weekIndex,
        dayLabels: days.map(d => d.label || "Day"), sessionsTotal, sessionsDone, upNext,
        percentDone: sessionsTotal ? Math.round((sessionsDone / sessionsTotal) * 100) : 0,
        /* the phase you are in right now, for the header tile */
        currentPhase: (phases.find(p => p.current) || phases[0] || null) };
}

function historyVolumeIn(h, unit) {
    let volume = 0, hasLedger = false;
    for (const p of Object.values(h?.perf || {})) {
        const work = setsOf(p).filter(isWorkSet);
        if (!work.length)
            continue;
        hasLedger = true;
        for (const st of work) {
            const w = Number(st?.w ?? st?.weight), r = Number(st?.r ?? st?.reps);
            if (Number.isFinite(w) && w > 0 && Number.isFinite(r) && r > 0)
                volume += w * r;
        }
    }
    /* Old imports may predate per-set ledgers. Preserve their cached total only when there is no
       working-set evidence to recompute; a stale cached aggregate can never overrule real sets. */
    if (!hasLedger) {
        volume = Number(h?.volume);
        if (!Number.isFinite(volume) || volume <= 0)
            return 0;
    }
    const from = h?.unit || "kg";
    return !unit || from === unit ? volume : from === "lb" && unit === "kg" ? volume / 2.2046226218 : from === "kg" && unit === "lb" ? volume * 2.2046226218 : volume;
}

function filterWorkoutHistory(history, saved, query, programId, period, now = Date.now()) {
    const names = new Map((saved || []).map(p => [p.id, p.name]));
    const words = query.trim().toLocaleLowerCase().split(/\s+/).filter(Boolean);
    const cutoff = period === "all" ? -Infinity : now - Number(period) * 86400000;
    return (history || []).filter(h => {
        if (!h || h.date == null || !Number.isFinite(new Date(h.date).getTime()))
            return false;
        if (programId !== "all" && String(h.programId || "none") !== programId)
            return false;
        if (new Date(h.date).getTime() < cutoff)
            return false;
        const text = [h.dayLabel, h.programName, names.get(h.programId), h.note, ...Object.entries(h.perf || {}).flatMap(([id, p]) => [EX_BY_ID[id]?.name || p?.name || id, p?.note])].filter(Boolean).join(" ").toLocaleLowerCase();
        return words.every(word => text.includes(word));
    }).slice().sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime());
}

function groupSessionsByCycle(history, cycles, saved, unit) {
    const progById = new Map((saved || []).map(p => [p.id, p]));
    const cycleById = new Map((cycles || []).map(c => [c.id, c]));
    const groups = new Map();
    for (const h of history || []) {
        if (!h || h.date == null || isNaN(new Date(h.date)))
            continue;
        const prog = progById.get(h.programId);
        const cycle = prog && prog.cycleId ? cycleById.get(prog.cycleId) : null;
        /* Key on the CYCLE, and carry the block within it. A deleted program leaves history behind, so
           every lookup above can miss — that is normal, not an error, and it lands in "other". */
        const key = cycle ? `c:${cycle.id}` : `p:${h.programId || "none"}`;
        let g = groups.get(key);
        if (!g) {
            g = {
                key, cycleId: cycle ? cycle.id : null,
                label: cycle ? (cycle.name || "Training cycle") : (prog ? prog.name : h.programName || "Earlier sessions"),
                isCycle: !!cycle, blocks: new Map(), items: [], count: 0, volume: 0, newest: 0
            };
            groups.set(key, g);
        }
        g.items.push(h);
        g.count++;
        if (h.date > g.newest)
            g.newest = h.date;
        const v = historyVolumeIn(h, unit);
        if (Number.isFinite(v) && v > 0)
            g.volume += v;
        /* Blocks only mean something inside a cycle. Prefer the cycle's own blockMeta label over the
           copy stamped on the program, so renaming a block in one place doesn't split the group. */
        if (cycle) {
            const meta = (cycle.blockMeta || []).find(m => m.id === h.programId);
            const bLabel = (meta && meta.label) || (prog && prog.blockLabel) || (prog && prog.name) || "Block";
            const bKey = h.programId || bLabel;
            let b = g.blocks.get(bKey);
            if (!b) {
                b = { key: bKey, label: bLabel, index: (cycle.blockIds || []).indexOf(h.programId), items: [], count: 0, volume: 0, newest: 0 };
                g.blocks.set(bKey, b);
            }
            b.items.push(h);
            b.count++;
            if (h.date > b.newest)
                b.newest = h.date;
            if (Number.isFinite(v) && v > 0)
                b.volume += v;
        }
    }
    return [...groups.values()]
        .map(g => ({
        ...g,
        /* blocks in PROGRAMME order, which is how the cycle was planned and read; a block with no
           position (its program was deleted) sorts last rather than jumping to the front on -1 */
        blocks: [...g.blocks.values()].sort((a, b) => (a.index < 0 ? 1 : b.index < 0 ? -1 : a.index - b.index))
    }))
        .sort((a, b) => b.newest - a.newest);
}

function groupSessionsByMonth(history, unit) {
    const groups = new Map();
    const thisYear = new Date().getFullYear();
    for (const h of history || []) {
        if (!h || h.date == null)
            continue;
        const d = new Date(h.date);
        if (isNaN(d))
            continue;
        const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
        let g = groups.get(key);
        if (!g) {
            g = { key, label: d.toLocaleDateString(undefined, d.getFullYear() === thisYear ? { month: "long" } : { month: "long", year: "numeric" }), items: [], count: 0, volume: 0 };
            groups.set(key, g);
        }
        g.items.push(h);
        g.count++;
        const v = historyVolumeIn(h, unit);
        if (Number.isFinite(v) && v > 0)
            g.volume += v;
    }
    // newest month first, matching the order the sessions themselves are already in
    return [...groups.values()].sort((a, b) => (a.key < b.key ? 1 : -1));
}

function weeklyBodyweightTrend(bwLog, unit) {
    const entries = normalizeBwLog(bwLog, unit);
    if (entries.length < 2)
        return null;
    const last = entries[entries.length - 1].date;
    const DAY = 86400000, WEEK = 7 * DAY;
    // bucket[0] = most recent 7 days, bucket[1] = the 7 days before that, etc.
    const buckets = [];
    entries.forEach(e => {
        const idx = Math.floor((last - e.date) / WEEK);
        (buckets[idx] || (buckets[idx] = [])).push(e.w);
    });
    const avg = (arr) => arr.reduce((s, v) => s + v, 0) / arr.length;
    const weeks = buckets.map((b, i) => b && b.length ? { weekIndex: i, avg: avg(b), n: b.length } : null).filter(Boolean);
    if (weeks.length < 2)
        return null; // everything logged fell inside one 7-day window
    const current = weeks[0];
    const prior = weeks[weeks.length - 1]; // oldest populated bucket — the longest-baseline comparison available
    const spanWeeks = prior.weekIndex - current.weekIndex;
    const changeAbs = current.avg - prior.avg;
    const changePerWeek = spanWeeks > 0 ? changeAbs / spanWeeks : 0;
    const pctPerWeek = prior.avg > 0 ? (changePerWeek / prior.avg) * 100 : 0;
    return {
        currentWeekAvg: Math.round(current.avg * 10) / 10,
        priorWeekAvg: Math.round(prior.avg * 10) / 10,
        changeAbs: Math.round(changeAbs * 100) / 100,
        changePerWeek: Math.round(changePerWeek * 100) / 100,
        pctPerWeek: Math.round(pctPerWeek * 100) / 100,
        spanWeeks,
        weeksOfData: weeks.length
    };
}

function summarizeHistoryLoggedSets(sets, ex, unit) {
    const work = (sets || []).filter(st => st && !st.sub && Number(st.r) > 0);
    if (!work.length)
        return null;
    const numW = st => Number.isFinite(Number(st.w)) ? Number(st.w) : 0;
    const hasPos = work.some(st => numW(st) > 0);
    const pool = hasPos ? work.filter(st => numW(st) > 0) : work;
    if (!pool.length)
        return null;
    const weights = pool.map(numW);
    const maxW = Math.max(...weights);
    if (weights.every(w => w === weights[0])) {
        const atTop = pool.filter(st => numW(st) === maxW);
        return { weight: maxW, reps: Math.min(...atTop.map(st => Math.max(1, Math.round(Number(st.r) || 1)))) };
    }
    const step = ex ? loadStep(ex, unit) : .5;
    const avgW = roundTo(weights.reduce((a, b) => a + b, 0) / weights.length, step || .5);
    const reps = Math.round(pool.reduce((a, st) => a + Math.max(1, Math.round(Number(st.r) || 1)), 0) / pool.length);
    return { weight: avgW, reps };
}

function normalizeEditedHistoryEntry(original, draft) {
    if (!original || !draft)
        return original;
    const out = { ...original };
    const date = Number(draft.date);
    if (Number.isFinite(date) && date > 0)
        out.date = date;
    const duration = Number(draft.durationMin);
    if (Number.isFinite(duration) && duration >= 0)
        out.durationMin = Math.round(duration);
    const perf = {};
    for (const [exId, rawPerf] of Object.entries(draft.perf || {})) {
        const prior = original.perf?.[exId] || {};
        const sourceSets = Array.isArray(rawPerf?.sets) && rawPerf.sets.length
            ? rawPerf.sets
            : (rawPerf?.reps != null ? [{ w: rawPerf.weight ?? 0, r: rawPerf.reps }] : []);
        const sets = [];
        for (const [setIndex, raw] of sourceSets.entries()) {
            if (!raw)
                continue;
            const reps = Math.round(Number(raw.r));
            if (!Number.isFinite(reps) || reps <= 0)
                continue;
            const w0 = raw.w === "" || raw.w == null ? 0 : Number(raw.w);
            if (!Number.isFinite(w0))
                continue;
            const st = { ...raw, w: w0, r: reps };
            if (raw.rir === "" || raw.rir == null || !Number.isFinite(Number(raw.rir))) {
                delete st.rir;
                delete st.rirReported;
            }
            else {
                st.rir = Math.max(0, Math.min(10, Number(raw.rir)));
                if (Number(raw.rir) !== Number(prior.sets?.[setIndex]?.rir)
                    || prior.sets?.[setIndex]?.rir == null) st.rirReported = true;
            }
            sets.push(st);
        }
        const summary = summarizeHistoryLoggedSets(sets, EX_BY_ID[exId], original.unit || "lb");
        if (!summary)
            continue;
        perf[exId] = { ...prior, ...rawPerf, weight: summary.weight, reps: summary.reps, sets, date: out.date };
    }
    out.perf = perf;
    let volume = 0, setsDone = 0;
    for (const p of Object.values(perf)) {
        for (const st of (p.sets || [])) {
            if (!st.sub) {
                volume += (Number(st.w) || 0) * (Number(st.r) || 0);
                setsDone++;
            }
        }
    }
    out.volume = Math.round(volume);
    out.setsDone = setsDone;
    return out;
}

function perfAfterHistoryReplace(history, perf, histId, replacement) {
    const old = (history || []).find(h => h && h.id === histId);
    if (!old || !replacement)
        return perf || {};
    const nextHistory = (history || []).map(h => h && h.id === histId ? replacement : h)
        .slice().sort((a, b) => (Number(b?.date) || 0) - (Number(a?.date) || 0));
    const affected = new Set([...Object.keys(old.perf || {}), ...Object.keys(replacement.perf || {})]);
    const next = { ...(perf || {}) };
    for (const exId of affected) {
        const latest = nextHistory.find(h => h?.perf?.[exId]);
        if (latest)
            next[exId] = latest.perf[exId];
        else
            delete next[exId];
    }
    return next;
}

function localHistoryDateValue(ms) {
    const d = new Date(Number(ms) || Date.now());
    const shifted = new Date(d.getTime() - d.getTimezoneOffset() * 60000);
    return shifted.toISOString().slice(0, 16);
}

const iconBtn = () => ({ width: 38, height: 38, borderRadius: 12, borderWidth: 1, borderStyle: "solid", borderColor: C.border, background: C.card, color: C.text, display: "flex", alignItems: "center", justifyContent: "center", cursor: "pointer", flexShrink: 0 });

const tinyBtn = () => ({ width: 30, height: 30, borderRadius: 8, borderWidth: 1, borderStyle: "solid", borderColor: C.border, background: C.card, color: C.muted, display: "flex", alignItems: "center", justifyContent: "center", cursor: "pointer" });

const miniInput = (w) => ({ width: w, padding: "7px 6px", textAlign: "center", borderRadius: 8, border: `1px solid ${C.border}`, background: C.card, color: C.text, fontSize: 15, fontWeight: 600 });

const HOME_INSIGHT_BUDGET = 2;

function homeCardPlan(history, { bodyweight } = {}) {
    const h = Array.isArray(history) ? history : [];
    if (!h.length)
        return [];
    const recap = weeklyRecap(h);
    const rec = muscleRecovery(h);
    const major = ["chest", "lats", "upper_back", "shoulders", "quads", "hamstrings", "glutes", "biceps", "triceps"];
    /* `restAdvised` is re-derived from the SAME rule MuscleRecoveryCard prints, because that is the
       card's actionable state and the reason it outranks everything else here. Kept in step by
       gates/homecards check 4, which fails if the card and the plan ever disagree about it. */
    const majorTrained = rec.filter(r => major.includes(r.part) && r.daysSince != null);
    const avgReady = majorTrained.length ? majorTrained.reduce((s, r) => s + r.readiness, 0) / majorTrained.length : 100;
    const fatigued = majorTrained.filter(r => r.readiness < 50).length;
    const restAdvised = majorTrained.length >= 3 && (avgReady < 50 || fatigued >= Math.ceil(majorTrained.length * 0.6));
    /* An unset bodyweight is an ONBOARDING PROMPT, not an evergreen stat — the card renders an
       "add your bodyweight to unlock this" tile, which is exactly the kind of thing a lifter should
       see. It ranks with the PR case rather than being budgeted away. */
    const needsBw = !(parseFloat(bodyweight) > 0);
    const RANKED = [
        ["recovery", restAdvised], // actionable now
        ["strength", recap.prs > 0 || needsBw], // a PR landed, or the score is still locked
        ["recap", recap.count > 0], // you trained this week; the numbers moved
        ["recovery", h.length > 0 && Date.now() - h[0].date < 2 * 86400000], // freshness is actually moving
    ];
    const out = [];
    for (const [id, news] of RANKED) {
        if (!news || out.includes(id))
            continue;
        if (out.length >= HOME_INSIGHT_BUDGET)
            break;
        out.push(id);
    }
    return out;
}

const WHATS_NEW_MAX = 10;

const WHATS_NEW_WORDS = 24;

function capWords(text, n = WHATS_NEW_WORDS) {
    const s = String(text == null ? "" : text).trim();
    if (!s)
        return "";
    const w = s.split(/\s+/);
    if (w.length <= n)
        return s;
    return w.slice(0, n).join(" ").replace(/[,;:.!?—–-]+$/, "") + "…";
}

const READY_PHRASES = ["Ready to train", "Let's lift", "Time to work", "Back at it", "Let's get after it", "Iron's waiting", "Make it count", "Let's move some weight", "Time to build"];

function homeProgramGroups(saved, cycles, activeId) {
    const byCycle = new Map(cycles.map(c => [c.id, c]));
    const groups = new Map();
    for (const p of saved) {
        const key = p.cycleId ? `cycle:${p.cycleId}` : `program:${p.id}`;
        if (!groups.has(key)) groups.set(key, { key, cycle: byCycle.get(p.cycleId), cycleId: p.cycleId, programs: [] });
        groups.get(key).programs.push(p);
    }
    return [...groups.values()].map(g => {
        g.programs.sort((a, b) => (a.cycleIndex ?? 0) - (b.cycleIndex ?? 0));
        g.current = g.programs.some(p => p.id === activeId);
        g.done = !!g.cycle?.done;
        g.name = g.cycle?.name || (g.cycleId ? 'Training cycle' : g.programs[0].name);
        g.createdAt = Math.max(...g.programs.map(p => Number(p.createdAt) || 0));
        return g;
    }).sort((a, b) => Number(b.current) - Number(a.current) || Number(a.done) - Number(b.done) || b.createdAt - a.createdAt);
}

const SELF_TEST_SUPPORTED_ENGINES = new Set(ENGINE_COMPATIBLE_VERSIONS);

const SELF_TEST_HISTORY_LIMIT = 160;

const SELF_TEST_FRAME_BUDGET_MS = 10;

function selfTestClock() {
    try {
        return typeof performance !== "undefined" && performance.now ? performance.now() : Date.now();
    }
    catch {
        return Date.now();
    }
}

async function yieldSelfTestThread() {
    // scheduler.yield() is purpose-built for breaking up long tasks on supporting Chromium builds.
    // setTimeout(0) is the compatibility path and, unlike requestAnimationFrame, still settles when the
    // PWA is backgrounded or the Settings tab is not visible.
    try {
        if (typeof globalThis !== "undefined" && globalThis.scheduler?.yield) {
            await globalThis.scheduler.yield();
            return;
        }
    }
    catch { }
    await new Promise(resolve => setTimeout(resolve, 0));
}

async function runSelfTest(saved = [], history = [], perf = {}, options = {}) {
    const ALL = EQUIPMENT.map(e => e.id);
    const fails = [];
    const audit = [];
    const advisories = [];
    const onProgress = typeof options.onProgress === "function" ? options.onProgress : null;
    const shouldCancel = typeof options.shouldCancel === "function" ? options.shouldCancel : (() => false);
    const startedAt = selfTestClock();
    let lastYieldAt = startedAt;
    let completedUnits = 0;
    // Current engine + previous four only. Everything older remains readable/archiveable but is not
    // repeatedly re-executed by a phone diagnostic. M61 calculated this list but accidentally used the
    // original `saved` array in both expensive saved-program passes, defeating the intended bound.
    const supportedSaved = (saved || []).filter(sp => sp?.engineSource === "pursuit-next" && SELF_TEST_SUPPORTED_ENGINES.has(sp.engineSourceVersion || sp.nextEngine?.version));
    const skippedArchived = Math.max(0, (saved || []).length - supportedSaved.length);
    // Progression helpers sort/filter history many times. Diagnostic correctness only needs the recent
    // evidence window that can influence current prescriptions, not an unlimited lifetime log scanned
    // again for every exercise/week. Normal training progression still receives the full history.
    const diagnosticHistory = [...(history || [])]
        .filter(h => h && Number.isFinite(Number(h.date)))
        .sort((a, b) => Number(b.date) - Number(a.date))
        .slice(0, SELF_TEST_HISTORY_LIMIT);
    const splitKeys = Object.keys(SPLITS);
    const GYMS = { "full gym": ALL, "dumbbells + bench": ["dumbbell", "bench"], "home barbell": ["barbell", "bench", "pullup"], "bodyweight": [] };
    /* ⚠ QUICK BY DEFAULT. The engine part of this test is a fixed, seeded matrix — ~850 program generations and simulations that
       give the same answer on every device for a given build (the build's own checks already run all of it). On a phone it took
       minutes (47 s even on a desktop-class machine). Default ("quick"): the lifter's OWN programs and history are checked in full,
       and the engine matrix is a deterministic sample that still touches every gym, every goal/behaviour and every pipeline
       stage, rotating which splits each gym gets. scope: "full" runs the whole matrix (Settings offers it separately). */
    const full = options.scope === "full";
    const every = (arr, n, offset = 0) => full ? arr : arr.filter((_, i) => (i + offset) % Math.max(1, Math.ceil(arr.length / n)) === 0);
    const tmplSet = every(TEMPLATES, 8), splitSet = every(splitKeys, 6);
    const simSplits = (gi) => every(splitKeys, 1, gi * 7), advSplits = (gi) => every(splitKeys, 3, gi * 5);
    const advDays = (k) => full ? SPLITS[k].days : SPLITS[k].days.slice(0, 1);
    const gymNames = Object.keys(GYMS);
    const structuralUnits = tmplSet.length + splitSet.length;
    const simulationUnits = gymNames.reduce((n, g, gi) => n + simSplits(gi).length * 3, 0);
    const advisoryUnits = gymNames.reduce((n, g, gi) => n + advSplits(gi).reduce((m, k) => m + advDays(k).length, 0), 0);
    const cycleRegressionUnits = 3;
    const totalUnits = structuralUnits + simulationUnits + advisoryUnits + supportedSaved.length * 2 + 2 + cycleRegressionUnits;
    const cancelledError = () => { const e = new Error("Self-test cancelled"); e.name = "SelfTestCancelled"; return e; };
    const checkpoint = async (phase, increment = 0, force = false) => {
        completedUnits += increment;
        if (shouldCancel())
            throw cancelledError();
        const now = selfTestClock();
        if (force || now - lastYieldAt >= SELF_TEST_FRAME_BUDGET_MS) {
            onProgress?.({ phase, completed: Math.min(completedUnits, totalUnits), total: totalUnits, elapsedMs: Math.round(now - startedAt) });
            await yieldSelfTestThread();
            lastYieldAt = selfTestClock();
            if (shouldCancel())
                throw cancelledError();
        }
    };
    let programs = 0, cells = 0;
    const check = (ctx, program, expectDays, equip) => {
        programs++;
        if (!program || !Array.isArray(program.days)) {
            fails.push(`${ctx}: no program`);
            return;
        }
        const equipSet = expandEquipment(equip); /* implied equipment too (a barbell implies a rack unless "no-rack") — a raw set failed every rack lift: 1,327 false failures */
        const scheme = program.config?.percentScheme;
        if (expectDays != null && program.days.length !== expectDays)
            fails.push(`${ctx}: ${program.days.length} days ≠ ${expectDays}`);
        const wks = weeksOf(program);
        const weeks = [];
        for (let w = 1; w <= wks; w++)
            weeks.push(w);
        if (program.config?.deload)
            weeks.push(wks + 1);
        program.days.forEach((d, di) => {
            const dc = `${ctx} d${di}`;
            if (!d.exercises?.length) {
                fails.push(`${dc}: empty day`);
                return;
            }
            if (d.primaryIndex < 0 || d.primaryIndex >= d.exercises.length)
                fails.push(`${dc}: primaryIndex OOB`);
            const seen = new Set();
            d.exercises.forEach((id, si) => {
                const ex = EX_BY_ID[id];
                if (!ex) {
                    fails.push(`${dc}.${si}: unknown ${id}`);
                    return;
                }
                if (seen.has(id))
                    fails.push(`${dc}.${si}: duplicate ${id}`);
                seen.add(id);
                if (ex.equip?.some(q => !equipSet.has(q)))
                    fails.push(`${dc}.${si}: ${ex.name} needs missing equip`);
                weeks.forEach(w => {
                    cells++;
                    let c;
                    try {
                        c = computeCell(program, d, id, si, w);
                    }
                    catch (e) {
                        fails.push(`${dc}.${si} wk${w}: computeCell threw`);
                        return;
                    }
                    if (!(Number(c?.sets) >= 1))
                        fails.push(`${dc}.${si} wk${w}: bad sets`);
                    if (!c.reps && c.reps !== 0 && !c.range)
                        fails.push(`${dc}.${si} wk${w}: no reps`);
                });
            });
            if (["gzclp", "rippler", "jt"].includes(scheme) && d.t2Index != null && d.t2Index === d.primaryIndex)
                fails.push(`${dc}: T2 == T1`);
        });
        try {
            weeklyVolume(program, 1);
        }
        catch (e) {
            fails.push(`${ctx}: weeklyVolume threw`);
        }
    };
    onProgress?.({ phase: "Checking templates", completed: 0, total: totalUnits, elapsedMs: 0 });
    await yieldSelfTestThread();
    lastYieldAt = selfTestClock();
    for (const t of tmplSet) {
        try {
            check(`tmpl:${t.id}`, generateNextProgramForShell({ config: templateConfig(t, ALL), banned: [], legacyExercises: EXERCISES, seed: 606 }).program, t.cfg.days, ALL);
        }
        catch (e) {
            fails.push(`tmpl:${t.id}: generate threw`);
        }
        await checkpoint("Checking templates", 1);
    }
    for (const k of splitSet) {
        const sp = SPLITS[k], days = sp.days[0];
        const cfg = { name: k, experience: "intermediate", goal: "both", split: k, days, session: "s90", weeks: 6, equipment: ALL, focus: {}, focusList: [], reduce: [], progression: "auto", deload: true, barbellCap: null, percentScheme: null };
        try {
            check(`split:${k}`, generateNextProgramForShell({ config: cfg, banned: [], legacyExercises: EXERCISES, seed: 606 }).program, days, ALL);
        }
        catch (e) {
            fails.push(`split:${k}: generate threw`);
        }
        await checkpoint("Checking splits", 1);
    }
    // Function-level arm coverage: enough direct arm/forearm volume must diversify elbow flexion
    // between a biceps-biased and brachialis/brachioradialis-biased curl, and direct forearm work must
    // include both wrist flexion and wrist extension. This is deliberately one focused case so the
    // phone self-test gains coverage without returning to the long blocking sweep M62 removed.
    try {
        const armCfg = { name: "arm coverage", experience: "intermediate", goal: "hypertrophy", split: "ulppl", days: 5, session: "s90", weeks: 4, equipment: ALL, focus: { biceps: 1, forearms: 1 }, focusList: ["biceps", "forearms"], reduce: [], progression: "auto", deload: true, barbellCap: null, percentScheme: null };
        const armResult = generateNextProgramForShell({ config: armCfg, banned: [], legacyExercises: EXERCISES, seed: 63063 });
        const arm = deriveArmCoverage(armResult.nextProgram.sessions, NEXT_EXERCISE_MAP);
        if (arm.directElbowFlexionSets >= 5 && arm.bicepsBiasedSets < 1)
            fails.push("arm coverage: missing biceps-biased elbow flexion");
        if (arm.directElbowFlexionSets >= 5 && arm.brachialisBiasedSets < 1)
            fails.push("arm coverage: missing brachialis/brachioradialis-biased elbow flexion");
        if (arm.directWristFlexionSets < 1)
            fails.push("arm coverage: missing wrist flexion");
        if (arm.directWristExtensionSets < 1)
            fails.push("arm coverage: missing wrist extension");
    }
    catch (e) {
        fails.push(`arm coverage: generate/audit threw — ${e.message || e}`);
    }
    await checkpoint("Checking arm functions", 1, true);
    // Whole-body function coverage uses the same additive contract as the release audit. This one
    // high-dose fixture verifies that broad parent-muscle volume did not collapse hamstrings, calves,
    // triceps, or back onto only one repeated function.
    try {
        const functionCfg = { name: "functional coverage", experience: "intermediate", goal: "hypertrophy", split: "ulppl", days: 5, session: "s90", weeks: 4, equipment: ALL, focus: { hamstrings: 1, calves: 1, triceps: 1, back: 1 }, focusList: ["hamstrings", "calves", "triceps", "back"], reduce: [], progression: "auto", deload: true, barbellCap: null, percentScheme: null };
        const functionResult = generateNextProgramForShell({ config: functionCfg, banned: [], legacyExercises: EXERCISES, seed: 65065 });
        const coverage = deriveFunctionalCoverage(functionResult.nextProgram.sessions, NEXT_EXERCISE_MAP);
        if (coverage.hamstrings.totalDirectSets >= 5 && coverage.hamstrings.hipExtensionSets < 1)
            fails.push("functional coverage: hamstrings missing hip extension/hinge");
        if (coverage.hamstrings.totalDirectSets >= 5 && coverage.hamstrings.kneeFlexionSets < 1)
            fails.push("functional coverage: hamstrings missing knee flexion");
        if (coverage.calves.totalDirectSets >= 5 && coverage.calves.straightKneeSets < 1)
            fails.push("functional coverage: calves missing straight-knee work");
        if (coverage.calves.totalDirectSets >= 5 && coverage.calves.bentKneeSets < 1)
            fails.push("functional coverage: calves missing bent-knee work");
        if (coverage.triceps.totalDirectSets >= 5 && coverage.triceps.lengthenedSets < 1)
            fails.push("functional coverage: triceps missing lengthened extension");
        if (coverage.triceps.totalDirectSets >= 5 && coverage.triceps.nonOverheadSets < 1)
            fails.push("functional coverage: triceps missing non-overhead extension");
        if (coverage.back.totalDirectSets >= 6 && coverage.back.verticalPullSets < 1)
            fails.push("functional coverage: back missing vertical pull");
        if (coverage.back.totalDirectSets >= 6 && coverage.back.horizontalRowSets < 1)
            fails.push("functional coverage: back missing horizontal row");
    }
    catch (e) {
        fails.push(`functional coverage: generate/audit threw — ${e.message || e}`);
    }
    await checkpoint("Checking whole-body functions", 1, true);
    // Cycle transitions are generation events, not ordinary single-program templates. M69 could pass
    // the entire program/progression diagnostic while a real Powerbuilding transition still rejected
    // Strength. Keep three small, high-value transition fixtures here: adaptive arm-priority coverage,
    // adaptive Minimalist coverage, and a locked short-session skeleton whose longer Strength rests
    // previously pushed a valid Hypertrophy day over its time cap.
    const cycleRegressionCases = [
        { label: "adaptive arm-priority", adaptBetweenBlocks: true, cfg: { name: "cycle arm priority", experience: "intermediate", goal: "both", split: "full_body", days: 5, session: "s90", weeks: 4, equipment: ALL, focus: { biceps: 2, triceps: 2, forearms: 1 }, focusList: ["biceps", "triceps", "forearms"], reduce: [], progression: "auto", deload: true, barbellCap: null, percentScheme: null, volumeApproach: "standard", noSupersets: true, noBodyweight: true } },
        { label: "adaptive minimalist arm-priority", adaptBetweenBlocks: true, cfg: { name: "cycle arm minimalist", experience: "intermediate", goal: "both", split: "ulppl", days: 5, session: "s90", weeks: 4, equipment: ALL, focus: { biceps: 1, forearms: 1 }, focusList: ["biceps", "forearms"], reduce: [], progression: "auto", deload: true, barbellCap: null, percentScheme: null, volumeApproach: "minimalist", noSupersets: true, noBodyweight: true } },
        { label: "locked short Strength retarget", adaptBetweenBlocks: false, cfg: { name: "cycle locked short", experience: "intermediate", goal: "both", split: "ppl", days: 5, session: "s40", weeks: 4, equipment: ALL, focus: {}, focusList: [], reduce: [], progression: "auto", deload: true, barbellCap: null, percentScheme: null, volumeApproach: "standard", noSupersets: true, noBodyweight: false } }
    ];
    for (const test of cycleRegressionCases) {
        try {
            const built = generateNextCycleForShell({ templateId: "powerbuilding", config: test.cfg, banned: [], legacyExercises: EXERCISES, seed: 61070, adaptBetweenBlocks: test.adaptBetweenBlocks });
            if (!Array.isArray(built.blocks) || built.blocks.length !== 3)
                fails.push(`cycle ${test.label}: expected 3 blocks`);
            const rejected = (built.blocks || []).find(block => block?.nextEngine?.program?.audit?.result !== "pass");
            if (rejected)
                fails.push(`cycle ${test.label}: ${rejected.blockLabel || "block"} did not pass its engine audit`);
        }
        catch (e) {
            const finding = e?.recovery?.findings?.[0]?.message;
            fails.push(`cycle ${test.label}: ${e?.message || e}${finding ? ` — ${finding}` : ""}`);
        }
        await checkpoint("Checking cycle transitions", 1, true);
    }
    /* SIMULATED LIFTER. Each case remains the same deep progression audit as before, but cases are
       separated by cooperative yields so Android never receives one multi-second main-thread task. */
    let auditedSets = 0;
    for (const [gi, [gymName, equip]] of Object.entries(GYMS).entries()) {
        for (const k of simSplits(gi)) {
            for (const behaviour of ["hit", "miss", "stall"]) {
                const cfg = { name: k, experience: "intermediate", goal: "hypertrophy", split: k, days: SPLITS[k].days[0], session: "s90", weeks: 3, equipment: equip, focus: {}, focusList: [], reduce: [], progression: "auto", deload: true, barbellCap: null, percentScheme: null };
                let p = null;
                try {
                    p = generateNextProgramForShell({ config: cfg, banned: [], legacyExercises: EXERCISES, seed: 606 }).program;
                    p.id = "selftest";
                }
                catch {
                    await checkpoint(`Simulating ${gymName}`, 1, true);
                    continue;
                }
                try {
                    const f = simulateAndAudit({ program: p, unit: "lb", behaviour });
                    auditedSets += p.days.reduce((n, d) => n + d.exercises.length, 0) * 3 * 5;
                    f.forEach(x => audit.push(`${gymName}/${k}/${behaviour}: ${x.exName} wk${x.weekIndex} — ${x.detail}`));
                }
                catch (e) {
                    audit.push(`${gymName}/${k}/${behaviour}: audit threw — ${e.message}`);
                }
                await checkpoint(`Simulating ${gymName}`, 1, true);
            }
        }
    }
    // The lifter's own supported programs, with a recent relevant evidence window. Yield during large
    // programs as well as between them so one long block cannot monopolize the UI thread.
    for (const sp of supportedSaved) {
        if (!sp || !Array.isArray(sp.days)) {
            await checkpoint("Checking your programs", 1);
            continue;
        }
        const programHistory = diagnosticHistory.filter(h => !sp.id || h.programId === sp.id);
        const wks = weeksOf(sp);
        let cellBudget = 0;
        for (let w = 1; w <= wks; w++) {
            for (const d of sp.days) {
                for (let slot = 0; slot < (d.exercises || []).length; slot++) {
                    const id = d.exercises[slot];
                    const ex = EX_BY_ID[id];
                    if (!ex)
                        continue;
                    auditedSets++;
                    try {
                        auditSets({ program: sp, day: d, ex, slot, weekIndex: w, unit: "lb", perf, history: programHistory, dayPerf: perf })
                            .forEach(x => audit.push(`your "${sp.name}": ${x.exName} wk${x.weekIndex} — ${x.detail}`));
                    }
                    catch (e) {
                        audit.push(`your "${sp.name}": ${ex.name} audit threw — ${e.message}`);
                    }
                    if (++cellBudget % 8 === 0)
                        await checkpoint("Checking your programs");
                }
            }
        }
        await checkpoint("Checking your programs", 1, true);
    }
    /* PROGRAM-LEVEL ADVISORIES remain separate from failures. */
    for (const [gi, [gymName, equip]] of Object.entries(GYMS).entries()) {
        for (const k of advSplits(gi)) {
            for (const dcount of advDays(k)) {
                const cfg = { name: k, experience: "intermediate", goal: "hypertrophy", split: k, days: dcount, session: "s60", weeks: 3, equipment: equip, focus: {}, focusList: [], reduce: [], progression: "auto", deload: true, barbellCap: null, percentScheme: null };
                let p = null;
                try {
                    p = generateNextProgramForShell({ config: cfg, banned: [], legacyExercises: EXERCISES, seed: 606 }).program;
                }
                catch {
                    await checkpoint("Checking weekly structure", 1);
                    continue;
                }
                try {
                    auditProgramWeek(p, weeksOf(p), {}).forEach(x => {
                        const line = `${gymName}/${k}/${dcount}d: ${x.detail}`;
                        if (x.severity === "error")
                            fails.push(line);
                        else if (x.severity !== "info")
                            advisories.push(line);
                    });
                }
                catch { /* an advisory pass must never break the self-test */ }
                await checkpoint("Checking weekly structure", 1);
            }
        }
    }
    for (const sp of supportedSaved) {
        if (!sp || !Array.isArray(sp.days)) {
            await checkpoint("Checking your weekly structure", 1);
            continue;
        }
        try {
            auditProgramWeek(sp, weeksOf(sp), { focusList: Object.keys((sp.config && sp.config.focus) || {}) })
                .forEach(x => { const line = `your "${sp.name}": ${x.detail}`; if (x.severity === "error")
                fails.push(line);
            else if (x.severity !== "info")
                advisories.push(line); });
        }
        catch { /* ignore */ }
        await checkpoint("Checking your weekly structure", 1);
    }
    await checkpoint("Finishing", 0, true);
    return {
        programs, cells, auditedSets, failures: fails.concat(audit), advisories, skippedArchived,
        historyEntriesAudited: diagnosticHistory.length,
        elapsedMs: Math.round(selfTestClock() - startedAt),
        scope: full ? "full" : "quick"
    };
}

function cycleProgress(cycle, saved = [], history = [], cycleInfo = null) {
    const meta = cycleBlockMetadata(cycle, saved);
    const done = !!(cycle && cycle.done);
    const activeIdx = done ? meta.length : (cycle?.activeBlock || 0);
    const isActiveCycle = !!(cycleInfo && cycleInfo.cycle && cycle && cycleInfo.cycle.id === cycle.id);
    const progOf = b => (b ? saved.find(s => s.id === b.id) : null);
    const totalOf = b => (b?.weeks || 0) + (b?.deload ? 1 : 0);
    const totalWeeks = meta.reduce((n, b) => n + totalOf(b), 0);
    const startMs = cycle?.startedAt || cycle?.createdAt || null;
    let doneWeeks = 0, acc = 0;
    const blocks = meta.map((b, i) => {
        const total = totalOf(b);
        const range = [acc + 1, acc + total];
        acc += total;
        const state = done || i < activeIdx ? "done" : i === activeIdx ? "active" : "upcoming";
        let weeksIn = 0;
        if (state === "done")
            weeksIn = total;
        else if (state === "active" && isActiveCycle)
            weeksIn = Math.max(0, Math.min(total, cycleInfo.isDeload ? b.weeks : (cycleInfo.weekIndex || 1) - 1));
        doneWeeks += weeksIn;
        const prog = progOf(b);
        const sessions = (history || []).filter(h => h && h.programId === b.id).length;
        return {
            ...b, index: i, state, total, weeksIn, range, sessions,
            exists: !!prog,
            days: prog?.days?.length || prog?.config?.days || null,
            deload: !!prog?.config?.deload,
            split: prog && Array.isArray(prog.days) ? prog.days.map(d => d.label).filter(Boolean) : [],
            dates: startMs ? [startMs + (range[0] - 1) * 7 * 86400000, startMs + range[1] * 7 * 86400000 - 86400000] : null
        };
    });
    const ids = cycle?.blockIds || meta.map(b => b.id);
    const sessions = (history || []).filter(h => h && ids.includes(h.programId)).length;
    /* PLANNED-TO-DATE counts only ELAPSED weeks, so "12 of 12 logged" means you have kept up, not that
       you have finished the cycle. Comparing against the whole cycle would show every lifter permanently
       behind, which is not information. */
    const plannedToDate = blocks.reduce((n, b) => n + (b.days || 0) * b.weeksIn, 0);
    return {
        blocks, done, activeIdx, totalWeeks, doneWeeks,
        pct: totalWeeks ? Math.round(100 * doneWeeks / totalWeeks) : 0,
        curWeek: done ? totalWeeks : Math.min(totalWeeks, doneWeeks + 1),
        weeksLeft: Math.max(0, totalWeeks - doneWeeks),
        sessions, plannedToDate,
        startMs, endMs: startMs && totalWeeks ? startMs + totalWeeks * 7 * 86400000 : null,
        active: blocks[activeIdx] || null
    };
}

function blockReview(history, cycle, i) {
    const meta = (cycle?.blockMeta || [])[i] || null;
    const id = (cycle?.blockIds || [])[i] || meta?.id || null;
    const hs = (history || []).filter(h => h && h.programId === id && h.date);
    if (hs.length < 3)
        return { ok: false, block: meta, sessions: hs.length, reason: hs.length ? `Only ${hs.length} session${hs.length === 1 ? "" : "s"} logged in this block — not enough to say anything honest about it.` : "No sessions were logged against this block." };
    const dates = hs.map(h => h.date);
    const from = Math.min(...dates), to = Math.max(...dates);
    const r = blockRetro(hs, { sinceDate: from - 1, untilDate: to + 1 });
    return { ...r, block: meta, from, to };
}

function blockChangeSummary(before, after) {
    if (!before || !after)
        return null;
    const ids = p => new Set((p.days || []).flatMap(d => d.exercises || []));
    const oldIds = ids(before), newIds = ids(after);
    const retained = [...newIds].filter(id => oldIds.has(id)).length;
    const added = [...newIds].filter(id => !oldIds.has(id)).map(id => EX_BY_ID[id]?.name || id);
    const removed = [...oldIds].filter(id => !newIds.has(id)).map(id => EX_BY_ID[id]?.name || id);
    const opening = p => {
        const rows = Object.values(p.nextWeekPrescriptions || {}).map(weeks => weeks?.[1] ?? weeks?.["1"]).filter(Boolean);
        if (!rows.length)
            return null;
        const reps = rows.flatMap(row => row.reps || []).filter(Number.isFinite);
        return { sets: rows.reduce((n, row) => n + (Number(row.sets) || 0), 0), repRange: reps.length ? `${Math.min(...reps)}–${Math.max(...reps)}` : "—" };
    };
    return { retained, added, removed, before: opening(before), after: opening(after) };
}

const INTRO_VERSION = 5;

const WHATS_NEW_VERSION = 226;

const HISTORY_CAP = 2000;

const HISTORY_BYTES = 1600000;

const HISTORY_BYTE_CHECK_FROM = 700;

function capHistory(list) {
    const arr = (Array.isArray(list) ? list : []).filter(Boolean);
    let out = arr.slice().sort((x, y) => (y.date || 0) - (x.date || 0)).slice(0, HISTORY_CAP);
    // The byte pass costs a JSON.stringify, so it's skipped entirely below the threshold where the
    // budget is unreachable — which is where almost every user lives. Above it, one proportional cut
    // lands close, then a couple of bounded refinements settle it: ~3 serialisations worst case, never
    // the O(n²) of dropping one session at a time.
    if (out.length >= HISTORY_BYTE_CHECK_FROM) {
        let bytes = JSON.stringify(out).length;
        if (bytes > HISTORY_BYTES) {
            out = out.slice(0, Math.max(1, Math.floor(out.length * (HISTORY_BYTES / bytes))));
            let guard = 0;
            while (out.length > 1 && guard++ < 4 && JSON.stringify(out).length > HISTORY_BYTES) {
                out = out.slice(0, Math.max(1, Math.floor(out.length * 0.95)));
            }
        }
    }
    return out;
}

const RELEASE_DIAG_KEY = "wpb:release-diag";

const RELEASE_DIAG_CAP = 40;

const RELEASE_DIAG_FIELDS = new Set(["code", "source", "action", "phase", "engine", "reason", "status", "build"]);

function readReleaseDiagnostics() {
    try {
        const raw = localStorage.getItem(RELEASE_DIAG_KEY);
        const rows = raw ? JSON.parse(raw) : [];
        return Array.isArray(rows) ? rows.filter(x => x && typeof x === "object").slice(-RELEASE_DIAG_CAP) : [];
    }
    catch {
        return [];
    }
}

function recordReleaseDiag(code, detail = {}) {
    try {
        const clean = {};
        Object.entries(detail && typeof detail === "object" ? detail : {}).forEach(([k, v]) => {
            if (!RELEASE_DIAG_FIELDS.has(k))
                return;
            if (!["string", "number", "boolean"].includes(typeof v))
                return;
            clean[k] = typeof v === "string" ? v.slice(0, 180) : v;
        });
        const rows = readReleaseDiagnostics();
        rows.push({ at: Date.now(), event: String(code || "unknown").slice(0, 80), ...clean });
        localStorage.setItem(RELEASE_DIAG_KEY, JSON.stringify(rows.slice(-RELEASE_DIAG_CAP)));
        return true;
    }
    catch {
        return false;
    }
}

function refusalMessage(err, config, code = "NEXT_ENGINE_ERROR") {
    let gaps = [];
    try {
        gaps = splitContractGaps(config, EXERCISES);
    }
    catch { }
    if (gaps.length)
        return `${SPLITS[config.split]?.name || "This program"} is built around lifts your gym can't do (${gaps.map(g => String(g).replace(/_/g, " ")).join(", ")}). Pick another split, or add the equipment in your gym settings. Nothing was generated.`;
    let fixes = [];
    try {
        fixes = refusalFixes(err);
    }
    catch { }
    if (fixes.length)
        return `This combination couldn't be built safely. Suggested: ${fixes.join(" · ")}. Nothing was generated; your answers are still here to adjust.`;
    return `This combination couldn't be built safely — try a different split, fewer days, or more equipment. Nothing was generated; your answers are still here to adjust. (${code})`;
}

function isRuntimeProgram(p) {
    return !!p && (p.engineSource === "pursuit-next" || p.artifactType === "freestyle" || p.custom === true);
}
setShellEquipmentExpander(expandEquipment);
export { ALL_EQUIP_IDS, ALL_PLATES, APP_VERSION, ASSIST_IDS, ASSUMED_RIR, AVAIL_PLATES, AXIAL_ADJACENT_COST, BARS, BAR_EQUIP, BAR_LABEL, BASE_SET_COST, BIG_THREE, BIRTH_RE, BUG_EMAIL, BUILD_NUM, BW_LOG_CAP, C, CODE_ALPHA, CODE_ASSIST, CODE_CARRIED_KEYS, CODE_EQUIP, CODE_EXERCISES, CODE_EXP, CODE_GOALS, CODE_IGNORED_KEYS, CODE_PROG, CODE_SCHEMES, CODE_SESSION, CODE_SPLITS, COMPOSITE_HEADS, COVERED_MUSCLES, CYCLE_STEP_KEYS, DELT_HEAD, E1RM_HOLD, E1RM_REP_CAP, EFFORT_EASY, EFFORT_HARD, EFFORT_MAX_BIAS, EFFORT_MIN_PAIRS, EFFORT_REP_CAP, EMPHASIS_BY_SPLIT, EMPHASIS_LABEL, ENGINES, ENGINE_COMPATIBLE_VERSIONS, ENGINE_RULES, ENGINE_V, ENGINE_VERSION, EPLEY_SLOPE, EQUIPMENT, EQUIP_CATS, EQUIP_IMPLIES, EXERCISES, EXP, EXP_BAND, EX_ABBREV, EX_BY_ID, EX_FAMILY, EX_LOAD_INC, EX_METRICS, EX_METRIC_BY_ID, EX_SPECIFIC, EX_WINDOWS, FIGURE_GENERIC_OK, FIGURE_VARIANTS, FOCUS_CAVEAT, GAP_FRACTION, GENERATION_ROUTE, GYM_LIMITS, GYM_LIMIT_KEYS, GYM_PRESETS, GZ_T1_STAGES, GZ_T2_STAGES, HAPTIC, HAPTICS_ON, HISTORY_BYTES, HISTORY_BYTE_CHECK_FROM, HISTORY_CAP, HOME_INSIGHT_BUDGET, ICON_COLORS, ICON_COLOR_KEYS, IDEAL_SLOTS, INK_AA, INK_SECONDARY, INTENT_FROM_CAT, INTRO_VERSION, INVENTORY_KEYS, KEY, LANDMARKS, LEGACY_EQUIP_IMPLIES, LEGACY_EX_TYPICAL_KEYS, LEVEL_COLORS, LEVEL_TITLES, LIVE_KEY, LOAD_INC, LOWER_PARTS, MACHINE_EQUIP, MACHINE_SETUP, MEM, MILESTONE_XP, MIN_DAY_SESSIONS, MOBILITY, MRV_GRAIN, MUSCLE_SYNONYM, MYO_MAX_SETS, MYO_MINI_REPS, MYO_MINI_SETS, MYO_MIN_REPS, MYO_PARTS, MYO_TARGET_REPS, NAV_TABS, NEXT_EXERCISE_MAP, NOVICE_INELIGIBLE_SPLITS, NextShellAdapterError, PART_LABEL, PART_ORDER, PATTERNS, PATTERN_BY_ID, PATTERN_COLORS, PATTERN_GROUPS, PATTERN_MIN_SETS, PCT_SCHEMES, PHASES, PHASE_ROT, PLANNED_ROOM_RIR, PLATES, PROG_POLICIES, PROG_STYLES, PV_PAD_TOP, PV_ROW_GAP, RACK_PRESETS, RAW, READY_PHRASES, REDUCE_FRACTION, REFERENCES, REGION_MOVEMENT, REGION_REQUIRED, RELEASE_DIAG_CAP, RELEASE_DIAG_FIELDS, RELEASE_DIAG_KEY, REMINDER_ID_BASE, REST_SCALE, SCORE_BANDS, SCORE_EQUIV, SCORE_PATTERN, SCORE_PATTERN_LABEL, SECONDARY, SELF_TEST_FRAME_BUDGET_MS, SELF_TEST_HISTORY_LIMIT, SELF_TEST_SUPPORTED_ENGINES, SESSIONS, SESSION_BOUNDS, SESSION_EX_TYPICAL, SETUP_FIELD_ORDER, SETUP_LABELS, SET_ROW_BLEED, SEX_FACTOR, SHARE_BRAND, SHARE_FONT, SHEET_DISMISS_PX, SHEET_GRAB_PX, SHOW_EXERCISE_ANIMATION, SPLITS, SPLIT_BUILD_CACHE, STALL_ENGAGE, STALL_WINDOW, STANDARDS, STD_LEVELS, STEP_KEYS, STORE_MIGRATIONS, STORE_VERSION, STRENGTH_CLUB_LIFTS, STRENGTH_CLUB_TIERS_LB, STRETCH_FOCUS_E2, STRETCH_FOCUS_E3, STRETCH_PARTIAL_PARTS, SUBMUSCLE_LABEL, SUB_LANDMARKS, SUPPORT_BLURB, SUPPORT_URL, TECHNICAL_LIFTS, TEMPLATES, TEMPLATE_CATS, TEMPLATE_FILTER_GROUPS, THEMES, TOK, TOTAL_WEEKS, UNIFORM_LANDMARK, VARIANT_PRESENTATION, VIEW_DEPTH, WARMUP_PYRAMIDS, WARMUP_SET_SEC, WEIGHTED_SWAP, WHATS_NEW_MAX, WHATS_NEW_VERSION, WHATS_NEW_WORDS, __APP_VERSION__, __BUILD__, __doseMemo, __recovFitMemo, __recoveryMemo, __slopeMemo, __volKey, __weeklyVolMemo, _alphaOf, _beepCtx, _blend, _catInkCache, _dstCache, _fillLum, _hsl2rgb, _hx2rgb, _ink, _lmCache, _onFill, _planOverviewMemo, _ratio, _rgb2hsl, _rgb2hx, _rgbLum, _rot, _worst, activeBackdrop, adaptPercentageSetBudget, addedMinutes, addedSeconds, advanceLegacyFirstCycleBlock, afterNextPaint, ageFactor, ageFrom, anchorPerfFor, applyCustomProgramSettings, applyTheme, asLengthUnit, assertExerciseData, assertFigureCoverage, attachmentLabel, auditProgramWeek, auditSets, auditShellVolume, autoStyleDetail, autoStyleFor, availableFor, avoidableExerciseOverlap, axialCost, backdropFocus, backdropTabStops, backupRecordImpact, barFor, baseSetsFor, beep, beepContext, betterTopSet, birthFromAge, birthParts, bitsToCode, blockChangeSummary, blockPhase, blockPlanFor, blockRetro, blockReview, budgetIncludesTransitions, buildLifterModel, buildRuntimeSetTargets, buildUserAddedSlotPrescriptions, buildWeekPlan, buzz, calibrateDayPerf, calibratedAnchorPerf, canUndoProgramSave, canonicalShellSetCount, capHistory, capWeeklyVolume, capWords, captureShellVolumeSnapshot, catInk, cellRepRange, clamp, classifyPlateau, clearSetup, clearUserPrescriptionOverride, cloneNextDayPrescriptions, closeTopBackdrop, coachFacts, codeToBits, completedHistorySets, completionRate, compositeMrv, computeCell, computeLevel, computeMilestones, constantLoadDecay, containBackdropFocus, convertHistoryLoad, convertProgramToNextCycleForShell, coverageGaps, coverageRelief, cuesFor, customAuthoredSetCount, customExerciseHistory, customExerciseReferenceHistory, customLastSetTechnique, customProgramProgressionStyle, customProgramSuggestion, cycleBlockMetadata, cycleConfigForStandaloneProgram, cycleProgress, dayMuscleBreakdown, dayMuscleLoad, dayMuscleVolume, dayOverlap, dayPerfFor, dayScopedTrends, daySeconds, daysInWeek, decodeGallery, decodeProgramCode, deloadAdvice, deriveArmCoverage, deriveFunctionalCoverage, deriveTieredLinearState, dialogTitleId, distributeVolBias, drawRecapCard, drawShareBrand, e1rm, e1rmRIR, effectiveRest, effortBounds, effortCalibration, effortLabel, effortValueLabel, emptyRetiredRolloutData, emptyRetiredTrialData, encodeProgramCode, endlessStalled, engHas, engLacks, engineInfo, engineWeekOneStyles, ensureNotifyPermission, estimateMinutes, estimateMinutesFor, evaluateWorkoutProgression, exMatches, exRecords, exWindow, exerciseAt, exerciseProfile, exerciseSeries, exerciseSlotSec, exerciseTrends, expandEquipment, expandLegacyEquipment, explainPrescription, eyebrow, eyebrowAccent, feedbackDelta, figurePose, filterWorkoutHistory, fitChip, fitChipFor, fitFont, fitText, fmtSets, fmtTime, formVideoUrl, formatSetup, freestyleCellForRepRange, friendlyEquipment, galleryIssueBody, gallerySubmission, generateNextCycleForShell, generateNextProgramForShell, getBits, getNextShellCell, goalForDay, groupPctSets, groupSessionsByCycle, groupSessionsByMonth, growMyoSets, gymById, gymCapFor, gymRackFor, gzPlanFor, gzTierOf, gzWorkWeight, hapticsSupported, historyDayIndex, historyExposureContext, historyForProgram, historyLoadReason, historyNumber, historyVolumeIn, holdWorkoutScreenAwake, homeCardPlan, homeProgramGroups, iconBtn, iconColorOf, isAssistedEx, isAxialLoad, isBarLike, isMachineLike, isNativeApp, isRuntimeProgram, isStretchFocus, isWorkSet, landmarkFor, landmarkForLegacy, landmarkOf, lastDayPerf, lastSetEffort, lastSetTech, lastTopSet, legacyCustomSetCount, lengthUnitFor, lifterModelKey, linearInc, linearStalled, liveCoachForContext, loadStep, loadStore, loadableAbove, loadableAtOrBelow, localHistoryDateValue, logWindow, loggedExercisePerformance, loggedSubVolume, loggedVolume, loggedWorkoutPerformance, lowerBodyLift, makeMyoMini, markUserPrescriptionOverride, mavFor, mergeSessionData, mergeStandaloneIntoGeneratedCycle, mergeStores, mergedSetCount, migrateStore, miniInput, movePattern, movementFamilyDescription, movementFamilyOptions, movementFamilyTitle, muscleRecovery, muscleRecoveryUncached, nativeNotifier, navPop, navPush, newGymId, nextCycleTemplatesForShell, nextDueDayId, nextEngineStyleFor, nextExerciseIdForShellExercise, nextScheduledIndex, nextSessionCursor, nextTMEvidence, nextWorkoutSuggestionForShell, nextWorkoutSuggestionFromPerformedShell, normalizeBwLog, normalizeCycleLinks, normalizeEditedHistoryEntry, normalizeGyms, normalizeHistoryDayIds, normalizeHistoryEntries, normalizeMeasureLog, observedHistoryRIR, openBackdrops, openDismissLayers, overreachSignal, paceFactor, parseRIRNum, parseStoredData, partZone, patternCues, patternTrainable, pctSetsFor, perWeekOf, percentagePlanFor, percentageProtocolFor, perfAfterDelete, perfAfterHistoryReplace, personalMav, personalRecoveryHours, personalRepSlope, phaseFor, phaseKind, pickActiveProgram, pickTopSet, planOverview, planOverviewMemo, plannedWeek, plateauOf, plateauOfLift, plateauSessions, plateauSplitByDay, platesPerSide, poolOf, preferenceFloor, prescribeSets, prescribedRIRof, prescribedRIRofAny, preserveRetiredRolloutData, preserveRetiredTrialData, previewVolumeNudge, programChangeLabels, programFingerprint, programSavePlan, programWorkingWeeks, progressionExposureContext, progressionHistoryForProgram, projectNextTM, propagateCycleEditsPure, publicTrainingCopy, putBits, rankSwapAlts, readReleaseDiagnostics, readinessBand, recommendNextSplitForShell, recommendedSplit, reconcilePendingRepTargets, recordReleaseDiag, refreshPendingSetTargets, refusalFixes, refusalMessage, regionGapFix, regionGapsFor, releaseBeepContext, remapNextShellRoster, remindersAreReliable, removeNextSlotPrescription, repRange, repairShellVolume, representativeCustomLoad, repsLow, resolveNextShellExerciseId, resolveStyle, restSec, rirTrend, rmAt, rotationOf, roundTo, ruleEngine, runSelfTest, sameProgramContent, sanitizeInventory, sanitizeLimits, sanitizeRepsInput, sanitizeWeightInput, saveStore, scheduleNativeReminders, scoreAttribution, scoreCompose, scoreContin, scoreEquivalent, scoreEquivalentInverse, scoreLevelIdx, scoreLevelLabel, scoreMatur, scorePatternFor, scoreTargetFor, scoreTo100, secondaryOf, selfTestClock, sessionChoicesFor, sessionE1RM, sessionExercisePlan, sessionSnapshotStatus, sessionSuggestion, setAvailPlates, setExLoadInc, setGymLimits, setHapticsEnabled, setLoadInc, setRestScaleGlobal, setShellEquipmentExpander, setsOf, setupFieldsFor, setupFieldsWithStored, setupLabel, shareSession, shareStrengthClub, shareStrengthScore, sheetDragRef, shellDayMuscleBreakdown, shellVolumeTargets, simulateAndAudit, snapshotNextShellPrescription, sortVisualLayers, splitBuildability, splitContractGaps, squatHamCredit, stallCountFor, starvedRegions, storeSaveQueue, strengthClubSnapshot, strengthLevel, strengthScore, strengthScoreHistory, strengthSnapshot, stretchTable, styleFor, styleOverride, subRegionOf, suggestionLoadLabel, summarizeHistoryLoggedSets, summarizeSets, swapNextSlotPrescriptions, swapOverlapNames, syncBackdropAria, syncBackdropLock, syncSubSets, techExplain, techSetTag, techniqueProtocolFromCell, templateConfig, templateEmphasis, templateFacets, templateIntent, tempoFor, tierOf, tinyBtn, toLength, toUnit, todayISO, trainingMaxForUnit, uid, underRecoveredWeekly, uniformLandmarkFor, unlockBeep, userOwnsRuntimeSet, validHistoryDate, variantDisplayName, variantMeta, volumeAdvice, volumeAudit, volumeLedger, volumeResponse, volumeVerdicts, volumeZone, warmupCount, warmupPlan, warmupRoutine, warmupSets, weekIntent, weekKeyOf, weekMuscleBreakdown, weeklyBodyweightTrend, weeklyRecap, weeklySubVolume, weeklyVolume, weeksOf, withTrainingMax, workoutPlanCoachFact, wrapList, writeStoreText, yieldSelfTestThread };
