// Keyboard navigation for button-based choice rows (not ARIA tablists).
// Selection remains owned by the parent and uses the same callback as tapping.
export function navigateChoiceRow(event, onSelect) {
    if (event.defaultPrevented || event.altKey || event.ctrlKey || event.metaKey || event.shiftKey)
        return false;
    const delta = event.key === 'ArrowRight' ? 1 : event.key === 'ArrowLeft' ? -1 : 0;
    if (!delta && event.key !== 'Home' && event.key !== 'End') return false;

    const group = event.currentTarget;
    const target = event.target?.closest?.('button');
    if (!target || !group.contains(target)) return false;
    const all = [...group.children].filter(el => el.tagName === 'BUTTON');
    const choices = all.map((button, index) => ({ button, index }))
        .filter(({ button }) => !button.disabled && button.getAttribute('aria-disabled') !== 'true'
            && !button.hidden && !button.inert && button.getClientRects().length > 0);
    if (!choices.length) return false;

    const at = choices.findIndex(({ button }) => button === target);
    if (at < 0) return false;
    const next = event.key === 'Home' ? 0
        : event.key === 'End' ? choices.length - 1
        : (at + delta + choices.length) % choices.length;
    const choice = choices[next];
    event.preventDefault(); // Arrow keys must not scroll the entire workout page.
    choice.button.focus({ preventScroll: true });
    if (choice.button !== target) onSelect(choice.index);
    return true;
}
