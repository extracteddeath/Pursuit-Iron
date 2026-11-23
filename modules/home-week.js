// Home's week strip and rolling 7-day recap, ported from the M3 prototype. Presentation only: the
// numbers come from engine-api's weeklyRecap and the lifter's own history and program schedule.
import { jsx as _jsx, jsxs as _jsxs } from 'react/jsx-runtime';
import { useMemo, useState } from 'react';
import { Check, Flame, Share2, BarChart3 } from 'lucide-react';
import { weeklyRecap, PART_LABEL } from './engine-api.js';
import { UiCard, UiAction } from './ui-primitives.js';

const DAY_MS = 86400000;
const startOfDay = (t) => { const d = new Date(t); d.setHours(0, 0, 0, 0); return d.getTime(); };
/* Monday-first: the prototype's strip, and a training week that ends with the weekend. */
const mondayOf = (t) => { const d = new Date(startOfDay(t)); const back = (d.getDay() + 6) % 7; d.setDate(d.getDate() - back); return d.getTime(); };
const dayKey = (t) => { const d = new Date(t); return `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`; };

/**
 * The current Monday–Sunday week for the strip, plus the weekly streak.
 * `scheduled` reads program.schedule (weekday "0" = Sunday → day id), which generation always emits.
 * The streak counts consecutive calendar weeks with at least one logged session. A week still in
 * progress with nothing logged does not break it yet; it is reported as `atRisk` instead.
 */
export function weekStrip(history = [], program = null, now = Date.now()) {
    const trained = new Set();
    const weeks = new Set();
    for (const h of history || []) {
        const t = Number(h?.date);
        if (!Number.isFinite(t) || t <= 0 || t > now + DAY_MS) continue;
        trained.add(dayKey(t));
        weeks.add(mondayOf(t));
    }
    const monday = mondayOf(now), today = startOfDay(now);
    const schedule = program?.schedule && typeof program.schedule === 'object' ? program.schedule : {};
    const labels = ['M', 'T', 'W', 'T', 'F', 'S', 'S'];
    const days = labels.map((label, i) => {
        const d = new Date(monday); d.setDate(d.getDate() + i);
        const t = d.getTime();
        return {
            t, label, date: d.getDate(),
            name: d.toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric' }),
            trained: trained.has(dayKey(t)),
            scheduled: !!schedule[String(d.getDay())],
            today: t === today,
            future: t > today
        };
    });
    let streak = 0, cursor = monday;
    const thisWeek = weeks.has(monday);
    if (!thisWeek) cursor -= 7 * DAY_MS;
    while (weeks.has(cursor)) { streak++; const d = new Date(cursor); d.setDate(d.getDate() - 7); cursor = d.getTime(); }
    return { days, streak, atRisk: !thisWeek && streak > 0, trainedThisWeek: days.filter(d => d.trained).length };
}

const fmtMinutes = (m) => m < 60 ? `${m}m` : `${Math.floor(m / 60)}h${m % 60 ? ` ${m % 60}m` : ''}`;
const fmtVolume = (v) => v >= 100000 ? `${Math.round(v / 1000)}k` : v >= 10000 ? `${(v / 1000).toFixed(1)}k` : v.toLocaleString();
const fmtLoad = (w) => (Math.round(w * 10) / 10).toString();

export function HomeWeekStrip({ history, program, weekLabel }) {
    const strip = useMemo(() => weekStrip(history, program), [history, program]);
    const streakLine = strip.streak === 0
        ? 'Log a workout this week to start a streak'
        : strip.atRisk
            ? `${strip.streak}-week streak · train this week to keep it`
            : `${strip.streak}-week streak · one a week keeps it`;
    return _jsxs(UiCard, { className: 'wpb-home-strip', 'aria-label': 'This week', children: [
        weekLabel && _jsx('div', { className: 'wpb-home-strip-title', children: weekLabel }),
        _jsx('ol', { className: 'wpb-home-strip-days', children: strip.days.map(d => _jsxs('li', {
            className: 'wpb-home-strip-day', 'data-trained': d.trained ? 'true' : 'false', 'data-today': d.today ? 'true' : 'false',
            'data-scheduled': d.scheduled ? 'true' : 'false', 'data-future': d.future ? 'true' : 'false',
            'aria-label': `${d.name}${d.today ? ', today' : ''}: ${d.trained ? 'trained' : d.scheduled ? (d.future || d.today ? 'scheduled' : 'scheduled, not logged') : 'rest'}`,
            children: [
                _jsx('span', { className: 'wpb-home-strip-weekday', 'aria-hidden': true, children: d.label }),
                _jsx('span', { className: 'wpb-home-strip-date', 'aria-hidden': true, children: d.trained ? _jsx(Check, { size: 18, strokeWidth: 3 }) : d.date }),
                _jsx('span', { className: 'wpb-home-strip-mark', 'aria-hidden': true })
            ] }, d.t)) }),
        _jsxs('p', { className: 'wpb-home-strip-streak', 'data-at-risk': strip.atRisk ? 'true' : 'false', children: [
            _jsx(Flame, { size: 16, 'aria-hidden': true }), _jsx('span', { children: streakLine })
        ] })
    ] });
}

/** Text for the share sheet / clipboard; pure so the recap and its tests agree on one wording. */
export function recapShareText(r, planned) {
    const parts = [`${r.count} ${r.count === 1 ? 'session' : 'sessions'}${planned ? ` of ${planned}` : ''}`, `${r.sets} sets`, `${fmtVolume(r.vol)} ${r.unit} volume`];
    if (r.prs) parts.push(`${r.prs} ${r.prs === 1 ? 'PR' : 'PRs'}`);
    if (r.minutes) parts.push(`${fmtMinutes(r.minutes)} trained`);
    const lines = [`My last 7 days on Pursuit Iron: ${parts.join(' · ')}.`];
    if (r.best) lines.push(`Best set: ${r.best.name} ${fmtLoad(r.best.w)} ${r.unit} × ${r.best.r}.`);
    if (r.topMuscle) lines.push(`Most trained: ${PART_LABEL[r.topMuscle[0]] || r.topMuscle[0]}.`);
    return lines.join('\n');
}

export function HomeRecapCard({ history, unit, program }) {
    const r = useMemo(() => weeklyRecap(history, unit), [history, unit]);
    const [shared, setShared] = useState('');
    if (!r.count) return null;
    const planned = program?.schedule ? Object.keys(program.schedule).length : (program?.days?.length || 0);
    const end = new Date(), start = new Date(Date.now() - 6 * DAY_MS);
    const fmt = (d, withMonth) => d.toLocaleDateString(undefined, withMonth ? { month: 'short', day: 'numeric' } : { day: 'numeric' });
    const range = start.getMonth() === end.getMonth() ? `${fmt(start, true)}–${fmt(end, false)}` : `${fmt(start, true)} – ${fmt(end, true)}`;
    const full = planned > 0 && r.count >= planned;
    const headline = planned > 0
        ? `${r.count} of ${planned} ${planned === 1 ? 'session' : 'sessions'}`
        : `${r.count} ${r.count === 1 ? 'session' : 'sessions'}`;
    const delta = (cur, prev) => prev ? Math.round((cur - prev) / prev * 100) : null;
    const stats = [
        { k: 'sets', v: String(r.sets), d: delta(r.sets, r.prevSets) },
        { k: `${r.unit} volume`, v: fmtVolume(r.vol), d: delta(r.vol, r.prevVol) },
        { k: r.prs === 1 ? 'PR' : 'PRs', v: String(r.prs) }
    ];
    const pace = r.prevCount ? (r.count > r.prevCount ? `ahead of the 7 days before (${r.prevCount})` : r.count === r.prevCount ? 'on pace with the 7 days before' : `behind the 7 days before (${r.prevCount})`) : '';
    const meta = [r.minutes ? `${fmtMinutes(r.minutes)} trained` : '', pace].filter(Boolean).join(' · ');
    const share = async () => {
        const text = recapShareText(r, planned);
        try {
            if (navigator.share) { await navigator.share({ title: 'My last 7 days', text }); setShared('Shared'); }
            else { await navigator.clipboard.writeText(text); setShared('Copied'); }
        } catch (e) {
            if (e?.name === 'AbortError') return;
            try { await navigator.clipboard.writeText(text); setShared('Copied'); } catch { setShared('Couldn’t share'); }
        }
        setTimeout(() => setShared(''), 2500);
    };
    return _jsxs(UiCard, { className: 'wpb-home-week', 'aria-label': 'Last 7 days', children: [
        _jsxs('div', { className: 'wpb-home-recap-head', children: [
            _jsx(BarChart3, { size: 16, 'aria-hidden': true }),
            _jsx('span', { className: 'wpb-home-recap-kicker', children: `Last 7 days · ${range}` })
        ] }),
        _jsxs('h3', { className: 'wpb-home-recap-title', children: [headline, full && _jsx('span', { className: 'wpb-home-recap-badge', children: 'Full week' })] }),
        meta && _jsx('p', { className: 'wpb-home-recap-meta', children: meta }),
        _jsx('dl', { className: 'wpb-home-recap-stats', children: stats.map(s => _jsxs('div', { className: 'wpb-home-recap-stat', children: [
            _jsx('dt', { children: s.k }),
            _jsxs('dd', { className: 'mono', children: [s.v, s.d != null && s.d !== 0 && _jsxs('span', { className: 'wpb-home-recap-delta', 'data-up': s.d > 0 ? 'true' : 'false',
                'aria-label': `${Math.abs(s.d)}% ${s.d > 0 ? 'more' : 'less'} than the 7 days before`, children: [s.d > 0 ? '▲' : '▼', Math.abs(s.d), '%'] })] })
        ] }, s.k)) }),
        (r.best || r.topMuscle) && _jsxs('p', { className: 'wpb-home-recap-lines', children: [
            r.best && _jsxs('span', { children: [_jsx('b', { children: 'Best: ' }), `${r.best.name} ${fmtLoad(r.best.w)} ${r.unit} × ${r.best.r}${r.best.rir != null ? ` @${r.best.rir}` : ''}, est. 1RM ${Math.round(r.best.e1rm)} ${r.unit}.`] }),
            r.topMuscle && _jsxs('span', { children: [_jsx('b', { children: 'Most trained: ' }), `${PART_LABEL[r.topMuscle[0]] || r.topMuscle[0]}, ${Math.round(r.topMuscle[1])} sets.`] })
        ] }),
        _jsx(UiAction, { variant: 'tonal', className: 'wpb-home-recap-share', onClick: share, 'aria-live': 'polite',
            children: [_jsx(Share2, { size: 16, 'aria-hidden': true }), shared || 'Share recap'] })
    ] });
}
