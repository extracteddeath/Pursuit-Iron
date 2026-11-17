/**
 * Source-name facets for web exercise browsing ONLY.
 * A facet labels a named exercise variant; it is NEVER proof that a bench
 * has a numeric angle, an attachment exists in the user's gym, or that
 * load/history/PR can be carried from a different exercise or physical machine.
 */
const normalized = value => String(value || "").toLowerCase().replace(/[-_]/g, " ");
const CLEAN = text => normalized(text).replace(/\s+/g, " ").trim();

export function exerciseBenchLevel(ex) {
    const name = CLEAN(ex?.name);
    if (!name) return null;
    // Only explicit incline/decline descriptions qualify; low-to-high
    // cable fly and low cable positions must never become bench angles.
    if (/\bhigh incline\b/.test(name)) return "high-incline";
    if (/\blow incline\b/.test(name)) return "low-incline";
    if (/\bdecline\b/.test(name)) return "decline";
    if (/\bincline\b/.test(name)) return "incline";
    if (/\bflat\b/.test(name)) return "flat";
    return null;
}

const explicitCableAttachments = new Map([
    ["pushdown","straight-bar"],
    ["oh-cable-ext","rope"],
    ["rope-pushdown","rope"],
    ["ez-pushdown","ez-bar"],
    ["straight-bar-pushdown","straight-bar"],
    ["dual-rope-pushdown","dual-rope"],
    ["v-bar-pushdown","v-bar"],
    ["single-pushdown","single-handle"]
]);
export function exerciseAttachmentFacet(ex) {
    if (!ex) return null;
    if (explicitCableAttachments.has(ex.id))
        return explicitCableAttachments.get(ex.id);
    const name = CLEAN(ex.name);
    const id = CLEAN(ex.id);
    const gear = Array.isArray(ex.equip) ? ex.equip.map(CLEAN) : [];
    if (!(gear.some(x=>x.includes("cable")) ||
        /\bcable\b|\bpushdown\b|\bpulldown\b/.test(name)))
        return null;
    const text = name + " " + id;
    if (/\bdual rope\b/.test(text))return "dual-rope";
    if (/\bv bar\b/.test(text))return "v-bar";
    if (/\bez bar\b/.test(text))return "ez-bar";
    if (/\bstraight bar\b/.test(text))return "straight-bar";
    if (/\bwrist cuff\b|\bcuff\b/.test(text))return "cuff";
    if (/\bankle strap\b/.test(text))return "ankle-strap";
    if (/\bdual handle\b/.test(text))return "dual-handles";
    if (/\bneutral grip\b/.test(text))return "neutral-grip";
    if (/\bwide grip\b/.test(text))return "wide-grip";
    if (/\bsingle arm\b|\bsingle handle\b/.test(text))return "single-handle";
    if (/\brope\b/.test(text))return "rope";
    // Unknown attachment is explicitly unlabeled; never infer from 'cable'.
    return null;
}

const angleNames=new Map([
    ["flat","Flat"],["low-incline","Low incline"],["incline","Incline"],
    ["high-incline","High incline"],["decline","Decline"]
]);
const attachmentNames=new Map([
    ["straight-bar","Straight bar"],["v-bar","V-bar"],["ez-bar","EZ bar"],
    ["rope","Rope"],["dual-rope","Dual rope"],["single-handle","Single handle"],
    ["dual-handles","Dual handles"],["cuff","Wrist cuff"],
    ["ankle-strap","Ankle strap"],["neutral-grip","Neutral grip"],
    ["wide-grip","Wide grip"]
]);
export function availableVariantFacets(exercises) {
    const scoped=Array.isArray(exercises) ? exercises : [];
    const levels=new Set(scoped.map(exerciseBenchLevel).filter(Boolean));
    const attachments=new Set(scoped.map(exerciseAttachmentFacet).filter(Boolean));
    return {
        levels:[...angleNames].filter(([id])=>levels.has(id))
            .map(([id,label])=>({id,label})),
        attachments:[...attachmentNames].filter(([id])=>attachments.has(id))
            .map(([id,label])=>({id,label}))
    };
}
export function matchesVariantFacets(ex,{level="all",attachment="all"}={}) {
    return (level==="all" || exerciseBenchLevel(ex)===level) &&
        (attachment==="all" || exerciseAttachmentFacet(ex)===attachment);
}
