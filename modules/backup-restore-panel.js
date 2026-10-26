// Backup UI owns staged previews and transfers. The App callbacks retain all persisted
// training, merge, compatibility and Undo decisions; this module never writes storage.
import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from 'react/jsx-runtime';
import { useEffect, useMemo, useRef, useState } from 'react';
import { Activity, BarChart3, RefreshCw, Save, Share2, Smartphone, Upload } from 'lucide-react';
import { UiAction, UiChoice } from './ui-primitives.js';

// Counts and omissions come directly from the importer. Ask about units only when
// the source omits them; choosing a unit re-runs the existing preview callback.
function CsvImportSummary({ summary, unit, csvUnit, onUnit }) {
    const fmtDate = ms => { try {
        return new Date(ms).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
    } catch { return ''; } };
    const chosen = csvUnit || summary.weightUnit || unit;
    const list = names => names.slice(0, 6).join(', ') + (names.length > 6 ? ` and ${names.length - 6} more` : '');
    return _jsxs('div', { 'data-csv-import-summary': summary.source, className: 'wpb-backup-csv-summary', children: [
        _jsxs('div', { children: [
            _jsx('strong', { className: 'mono', children: summary.workouts }), ` workout${summary.workouts === 1 ? '' : 's'} · `,
            _jsx('strong', { className: 'mono', children: summary.sets }), ` working set${summary.sets === 1 ? '' : 's'}`,
            summary.first ? ` · ${fmtDate(summary.first)} – ${fmtDate(summary.last)}` : ''
        ] }),
        summary.unitAssumed && _jsxs('div', { role: 'group', 'aria-label': 'Weight unit used in this file', className: 'wpb-backup-unit-question', children: [
            _jsx('span', { children: "This file doesn't say whether weights are kg or lb." }),
            _jsx('div', { className: 'wpb-backup-unit-choices', children: ['kg', 'lb'].map(u =>
                _jsx(UiChoice, { 'data-csv-unit': u, selected: chosen === u, onClick: () => onUnit(u),
                    className: 'wpb-backup-unit', children: u }, u)) })
        ] }),
        !!summary.customCreated.length && _jsxs('div', { className: 'wpb-backup-csv-detail', children: [
            summary.customCreated.length, ' lift', summary.customCreated.length === 1 ? " isn't" : "s aren't",
            ' in the library and will be added as custom exercises: ', list(summary.customCreated), '.'
        ] }),
        !!summary.unmatched.length && _jsxs('div', { 'data-csv-unmatched': true, className: 'wpb-backup-csv-detail', children: [
            'Skipped, because the muscle they train is unclear: ', list(summary.unmatched.map(u => `${u.name} (${u.sets} set${u.sets === 1 ? '' : 's'})`)), '.'
        ] }),
        !!(summary.warmupsSkipped || summary.cardioSkipped) && _jsx('div', { className: 'wpb-backup-csv-detail', children: [
            summary.warmupsSkipped ? `${summary.warmupsSkipped} warm-up set${summary.warmupsSkipped === 1 ? '' : 's'} left out, as in workouts logged here` : '',
            summary.warmupsSkipped && summary.cardioSkipped ? '; ' : '',
            summary.cardioSkipped ? `${summary.cardioSkipped} cardio entr${summary.cardioSkipped === 1 ? 'y' : 'ies'} skipped` : '', '.'
        ].join('') })
    ] });
}

function RestorePreview({ preview, unit, csvUnit, onUnit }) {
    return _jsx('div', { role: 'status', className: 'wpb-backup-review', 'data-ok': preview.ok, children: preview.ok ? _jsxs(_Fragment, { children: [
        _jsx('strong', { children: preview.csv ? `Review your ${preview.csv.label} history` : 'Review before restoring' }),
        preview.csv && _jsx(CsvImportSummary, { summary: preview.csv, unit, csvUnit, onUnit }),
        _jsx('div', { className: 'wpb-backup-review-description', children: preview.csv
            ? 'Changes to this device after merging the imported workouts:' : 'Changes to this device after merging and checking the backup:' }),
        _jsxs('table', { 'aria-label': 'Backup merge impact', className: 'wpb-backup-impact', children: [
            _jsx('thead', { children: _jsxs('tr', { children: ['Records', 'Add', 'Update', 'Remove'].map(label =>
                _jsx('th', { scope: 'col', children: label }, label)) }) }),
            _jsx('tbody', { children: (preview.csv ? preview.impact.filter(row => row.key === 'history' || row.key === 'custom') : preview.impact).map(row =>
                _jsxs('tr', { 'data-backup-row': row.key, children: [
                    _jsx('th', { scope: 'row', children: row.label }),
                    ...[['Add', row.added], ['Update', row.updated], ['Remove', row.removed]].map(([label, count]) =>
                        _jsxs('td', { 'data-removals': label === 'Remove' && count > 0, children: [
                            _jsx('span', { className: 'wpb-backup-impact-label', 'aria-hidden': true, children: label }),
                            _jsx('span', { children: count })
                        ] }, label))
                ] }, row.key)) })
        ] }),
        _jsxs('div', { className: 'wpb-backup-review-note', children: [preview.incomingPreferences
            ? 'This backup is newer: matching preferences may change.' : 'This device is newer: current preferences take priority.',
            ' Matching records use the newest edit.'] }),
        !!preview.historyDropped && _jsxs('div', { role: 'alert', className: 'wpb-backup-review-warning', children: [
            preview.historyDropped, ' oldest workouts exceed the storage limit and will not be kept.'
        ] }),
        !!(preview.dropped || preview.skippedPrograms) && _jsxs('div', { className: 'wpb-backup-review-note', children: [
            preview.dropped, ' unknown exercise slots and ', preview.skippedPrograms, ' unsupported or empty programs will be skipped.'
        ] }),
        !!preview.draftConflicts?.length && _jsxs('div', { role: 'alert', className: 'wpb-backup-review-warning', children: [
            'Save or discard these drafts before restoring: ', preview.draftConflicts.join(', '), '. Your drafts have not changed.'
        ] }),
        _jsx('div', { className: 'wpb-backup-review-help', children: 'Removals can come from synced deletions or compatibility checks. Undo restores the state before this import and is available here until you close this panel or leave Settings.' })
    ] }) : preview.msg });
}

export function BackupRestorePanel({ unit, initialFocus, onFocusHandled, onExport, onBackupSaved, onExportCSV, onExportBodyCSV, onImport, onPreviewImport }) {
    const [transfer, setTransfer] = useState(null);
    const [transferBusy, setTransferBusy] = useState(false);
    const transferBusyRef = useRef(false);
    const fileReadRef = useRef(0);
    const [readingFile, setReadingFile] = useState(false);
    useEffect(() => () => { fileReadRef.current += 1; }, []);
    const clearFileRead = () => { fileReadRef.current += 1; setReadingFile(false); };
    const closePanel = () => {
        clearFileRead(); setBackup(null); setImportMsg(null); setImportText("");
        setUndoSnap(null); setCsvUnit(null); setTransfer(null);
    };
    const [backup, setBackup] = useState(null); // "export" | "import" | null
    const [exportText, setExportText] = useState("");
    const [importText, setImportText] = useState("");
    const [importMsg, setImportMsg] = useState(null);
    const [csvUnit, setCsvUnit] = useState(null); // weight unit for a Strong export that does not state one
    const importPreview = useMemo(() => {
        if (!importText.trim())
            return null;
        try {
            if (importText.length > 20 * 1024 * 1024)
                throw new Error("Backup exceeds the 20 MB restore limit.");
            return onPreviewImport ? onPreviewImport(importText, { csvWeightUnit: csvUnit }) : { ok: false, msg: "Restore preview is unavailable. Reopen Settings and try again." };
        }
        catch (e) {
            return { ok: false, msg: e.message || "Backup is unreadable." };
        }
    }, [importText, onPreviewImport, csvUnit]);
    // The snapshot of the store as it was immediately before the last import, handed back by importData.
    // A merge can update records, apply synced deletions and hit storage limits. Undo needs the
    // complete pre-import snapshot; merging again cannot restore the previous state.
    const [undoSnap, setUndoSnap] = useState(null);
    const undoImport = () => {
        if (!undoSnap)
            return;
        const result = onImport(undoSnap, { replace: true });
        if (!result?.ok) {
            setImportMsg(result || { ok: false, msg: "Undo could not be completed." });
            return;
        }
        setUndoSnap(null);
        setImportMsg({ ok: true, msg: "Reverted to how things were before that restore." });
    };
    // Opening a preview or copying a partial CSV does not create a restorable backup.
    const openExport = () => { setExportText(onExport()); setBackup("export"); setTransfer(null); };
    // Deep link from Home's backup nudge: land with the export sheet already open and scrolled into
    // view, so "Back up" is one tap instead of navigate-then-hunt. Cleared after handling so the
    // sheet doesn't re-open every time Settings is revisited.
    const backupRef = useRef(null);
    useEffect(() => {
        if (initialFocus !== "export")
            return;
        openExport();
        const t = setTimeout(() => { try {
            backupRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
        }
        catch { } onFocusHandled?.(); }, 80);
        return () => clearTimeout(t);
    }, [initialFocus]);
    // A ref closes the same-tick double-tap window before React disables the buttons.
    const transferOnce = async (operation) => {
        if (transferBusyRef.current) return;
        transferBusyRef.current = true; setTransferBusy(true); setTransfer(null);
        try { await operation(); }
        finally { transferBusyRef.current = false; setTransferBusy(false); }
    };
    const copyExport = () => transferOnce(async () => {
        try {
            await navigator.clipboard.writeText(exportText);
            if (backup === "export") onBackupSaved?.();
            setTransfer({ ok: true, msg: backup === "csv" ? "CSV copied. Save it in your spreadsheet. Use Save backup for a full restore copy." : "Backup copied. Paste it into a file or another safe place." });
        } catch {
            setTransfer({ ok: false, msg: "Couldn't copy this text. Select and copy it manually, or download the backup file." });
        }
    });
    const fileInputRef = useRef(null);
    // Whether this device/browser can share a FILE (not just text) — gates showing the "Share to
    // cloud" button. Probed with the SAME mime type we actually share (text/plain — see shareBackupFile
    // for why), since canShare() answering "yes" for one mime doesn't mean it'll accept another.
    const [canShareFiles, setCanShareFiles] = useState(false);
    useEffect(() => {
        try {
            if (navigator.canShare && typeof File !== "undefined") {
                const probe = new File(["backup"], "probe.txt", { type: "text/plain" });
                setCanShareFiles(navigator.canShare({ files: [probe] }));
            }
        }
        catch { }
    }, []);
    // The backup is real JSON, but it's shared with a .txt name / text/plain mime rather than
    // application/json: several mobile OS share sheets (notably a number of Android Chrome / share
    // -target combinations) report canShare({files})=true for JSON and then have the actual share()
    // call silently reject or hand back zero compatible targets, which our catch below was previously
    // swallowing as "just download it" — invisible and confusing. text/plain is far more consistently
    // accepted by cloud-storage share targets, and it's byte-for-byte the same JSON content, so Restore
    // reads it back fine regardless of the .txt name. Plain "Download" still saves the conventional .json.
    const backupFileName = (forShare) => `pursuit-iron-backup-${new Date().toISOString().slice(0, 10)}.${forShare ? "txt" : "json"}`;
    // A download request is not proof that Android saved a file; tell the user to check Files.
    const downloadFile = (fallback = false) => {
        let url, anchor;
        try {
            url = URL.createObjectURL(new Blob([exportText], { type: "application/json" }));
            anchor = document.createElement("a");
            anchor.href = url; anchor.download = backupFileName(false);
            document.body.appendChild(anchor); anchor.click();
            onBackupSaved?.();
            setTransfer({ ok: true, msg: (fallback ? "Sharing unavailable. Download requested instead. " : "Download requested. ") + "Check Files or Downloads before leaving." });
        } catch {
            setTransfer({ ok: false, msg: "Couldn't download the backup. Try Copy text, or share the file if available." });
        } finally {
            anchor?.remove();
            if (url) setTimeout(() => URL.revokeObjectURL(url), 1500);
        }
    };
    const downloadBackupFile = () => transferOnce(() => downloadFile());
    const shareBackupFile = () => transferOnce(async () => {
        try {
            const file = new File([exportText], backupFileName(true), { type: "text/plain" });
            if (navigator.canShare?.({ files: [file] })) {
                await navigator.share({ files: [file], title: "Pursuit Iron backup" });
                onBackupSaved?.();
                setTransfer({ ok: true, msg: "Backup handed to your chosen app. Check that it saved the file." });
                return;
            }
        } catch (e) {
            if (e?.name === "AbortError") {
                setTransfer({ ok: true, msg: "Sharing cancelled. Your backup is ready to try again." });
                return;
            }
        }
        downloadFile(true);
    });
    const onPickFile = async (e) => {
        const file = e.target.files?.[0];
        e.target.value = "";
        if (!file) return;
        const requestId = ++fileReadRef.current;
        setImportText(""); setCsvUnit(null); setImportMsg(null); setReadingFile(false);
        if (file.size > 20 * 1024 * 1024) {
            setImportMsg({ ok: false, msg: "Backup exceeds the 20 MB restore limit." });
            return;
        }
        setReadingFile(true);
        try {
            const text = await file.text();
            if (requestId === fileReadRef.current) setImportText(text);
        } catch {
            if (requestId === fileReadRef.current) setImportMsg({ ok: false, msg: "Couldn't read that file. Choose it again or paste the backup text." });
        } finally {
            if (requestId === fileReadRef.current) setReadingFile(false);
        }
    };
    const doImport = () => {
        if (readingFile || !importPreview?.ok || importPreview.draftConflicts?.length)
            return;
        try {
            const r = onImport(importText, { csvWeightUnit: csvUnit });
            setImportMsg(r);
            if (r.ok) {
                setImportText("");
                setUndoSnap(r.undo || null);
            }
        }
        catch (e) {
            setImportMsg({ ok: false, msg: "Restore failed: " + (e.message || "unreadable backup") });
        }
    };
    return _jsxs('section', { className: 'wpb-backup', 'aria-busy': transferBusy || readingFile, 'aria-label': 'Backup and restore', children: [
        _jsx('div', { ref: backupRef, 'data-settings-anchor': 'backup', className: 'wpb-backup-heading', children:
            _jsx('h2', { className: 'wpb-settings-title', children: 'Backup & restore' }) }),
        _jsx('p', { className: 'wpb-backup-intro', children: 'Your training data stays on this device. Save a backup periodically so you can recover it or move it to another device.' }),
        backup === null && _jsxs(_Fragment, { children: [
            _jsxs('div', { className: 'wpb-backup-device-note', children: [
                _jsxs('div', { className: 'wpb-backup-device-title', children: [_jsx(Smartphone, { size: 15, 'aria-hidden': true }), 'Using two devices?'] }),
                _jsx('div', { children: 'Restores merge safely instead of replacing your log. Move the backup between devices whenever you want them to share the same history.' })
            ] }),
            _jsxs('div', { className: 'wpb-backup-actions', children: [
                _jsxs(UiAction, { onClick: openExport, variant: 'tonal', children: [_jsx(Save, { size: 15 }), ' Save backup'] }),
                _jsxs(UiAction, { onClick: () => { setBackup('import'); setImportMsg(null); setTransfer(null); }, variant: 'tonal', children: [_jsx(RefreshCw, { size: 15 }), ' Restore backup'] })
            ] }),
            onExportCSV && _jsxs(UiAction, { onClick: () => { setExportText(onExportCSV()); setBackup('csv'); setTransfer(null); }, variant: 'tonal',
                className: 'wpb-backup-wide-action', children: [_jsx(BarChart3, { size: 15 }), ' Export workout log (CSV)'] }),
            onExportBodyCSV && _jsxs(UiAction, { onClick: () => { setExportText(onExportBodyCSV()); setBackup('csv'); setTransfer(null); }, variant: 'tonal',
                className: 'wpb-backup-wide-action', children: [_jsx(Activity, { size: 15 }), ' Export body metrics (CSV)'] })
        ] }),
        (backup === 'export' || backup === 'csv') && _jsxs('div', { children: [
            _jsx('p', { className: 'wpb-backup-description', children: backup === 'csv'
                ? 'Copy this CSV into a spreadsheet (Excel, Google Sheets, Numbers).'
                : canShareFiles ? 'Send this file to your other device — Drive, Dropbox, email, anything. Review additions, updates and removals before restoring it there.'
                    : 'Copy this backup somewhere safe — or send it to your other device. Review additions, updates and removals before confirming a restore.' }),
            transfer && _jsx('div', { role: 'status', className: 'wpb-backup-status', 'data-ok': transfer.ok, children: transfer.msg }),
            _jsx('textarea', { 'aria-label': 'Backup or CSV text to copy', readOnly: true, value: exportText, onFocus: e => e.target.select(), className: 'wpb-scroll mono wpb-backup-text' }),
            backup === 'export' && canShareFiles && _jsxs(UiAction, { onClick: shareBackupFile, disabled: transferBusy, variant: 'primary',
                className: 'wpb-backup-wide-action', children: [_jsx(Share2, { size: 16 }), ' Share backup'] }),
            _jsxs('div', { className: 'wpb-backup-actions wpb-backup-transfer-actions', children: [
                backup === 'export' && _jsx(UiAction, { onClick: downloadBackupFile, disabled: transferBusy, variant: canShareFiles ? 'tonal' : 'primary', children: canShareFiles ? 'Download file' : 'Download backup file' }),
                _jsx(UiAction, { onClick: copyExport, disabled: transferBusy, variant: backup === 'csv' ? 'primary' : 'tonal', children: 'Copy text' }),
                _jsx(UiAction, { onClick: closePanel, disabled: transferBusy, variant: 'tonal', children: 'Done' })
            ] }),
            _jsx('p', { className: 'wpb-backup-help', children: backup === 'csv' ? 'Tip: paste this into a spreadsheet.'
                : 'Includes saved programs, cycles, completed workout history, body log, settings, custom exercises, gyms and notes. Unfinished workouts and unsaved program drafts remain on this device; finish or save those before moving to another device.' })
        ] }),
        backup === 'import' && _jsxs('div', { children: [
            _jsxs(UiAction, { onClick: () => fileInputRef.current?.click(), variant: 'tonal', className: 'wpb-backup-pick-file',
                children: [_jsx(Upload, { size: 16 }), ' Choose backup or Strong/Hevy CSV…'] }),
            _jsx('input', { 'aria-label': 'Choose a backup file or a Strong or Hevy CSV export', ref: fileInputRef, type: 'file',
                accept: 'application/json,.json,.txt,.csv,text/csv,text/comma-separated-values', hidden: true, onChange: onPickFile }),
            _jsx('p', { 'data-import-switch-hint': true, className: 'wpb-backup-switch-hint', children: 'Switching from Strong or Hevy? Export your workouts as CSV in that app and choose the file here. Review how it merges with your existing history before confirming.' }),
            _jsx('p', { className: 'wpb-backup-paste-hint', children: '— or paste the backup text —' }),
            _jsx('textarea', { 'aria-label': 'Backup text to restore', value: importText,
                onChange: e => { clearFileRead(); setImportMsg(null); setImportText(e.target.value); }, placeholder: 'Paste backup text from your other device…', className: 'wpb-scroll mono wpb-backup-text' }),
            readingFile && _jsx('div', { role: 'status', className: 'wpb-backup-status', children: 'Reading file…' }),
            importPreview && _jsx(RestorePreview, { preview: importPreview, unit, csvUnit, onUnit: setCsvUnit }),
            importMsg && _jsx('div', { role: 'status', className: 'wpb-backup-import-status', 'data-ok': importMsg.ok, children: importMsg.msg }),
            undoSnap && _jsx(UiAction, { onClick: undoImport, variant: 'tonal', className: 'wpb-backup-wide-action', children: 'Undo this restore' }),
            _jsxs('div', { className: 'wpb-backup-actions wpb-backup-transfer-actions', children: [
                _jsx(UiAction, { onClick: doImport, disabled: readingFile || !importPreview?.ok || !!importPreview.draftConflicts?.length,
                    variant: 'primary', children: importPreview?.csv ? 'Import workouts' : 'Confirm restore' }),
                _jsx(UiAction, { onClick: closePanel, disabled: transferBusy, variant: 'tonal', children: 'Cancel' })
            ] }),
            _jsx('p', { className: 'wpb-backup-help', children: 'Check additions, updates and removals before confirming.' })
        ] })
    ] });
}
