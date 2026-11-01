/** Measured staging limits: decrease only after a verified owner cleanup.
 * The B1011 family is isolated after original B1009/B1010 budgets. No new !important debt.
 */
export const uiStyleBudget=Object.freeze({
  cssCharacters:656668,
  // Monotonic priority debt ceiling: reductions are welcome, additions are not.
  cssPriorityDeclarations:2016,
  appInlineObjects:801,
  settingsInlineObjects:6,
  b1011CssCharacters:8577,
  b1012CssCharacters:4564
});
