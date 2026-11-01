import assert from 'node:assert/strict';
import fs from 'node:fs';

const root = 'android-native/app/src/main/java/io/github/extracteddeath/pursuitiron/';
const components = fs.readFileSync(root + 'NativeComponents.kt', 'utf8');
const screens = fs.readFileSync(root + 'NativeScreens.kt', 'utf8');
const shell = fs.readFileSync(root + 'NativePreviewApp.kt', 'utf8');

// A feature-first UI must not destroy working drafts when switching context.
assert.match(shell, /rememberSaveableStateHolder\(\)/);
assert.match(shell, /destinationStates\.SaveableStateProvider\(current\)/);
for (const name of ['sampleWeights', 'sampleReps', 'sampleRirs']) {
  assert.match(screens, new RegExp('var ' + name + ' by rememberSaveable'));
  assert.match(screens, new RegExp(name + '\\[exerciseIndex\\]'));
}
assert.match(screens, /values\.copyOf\(\)\.also/);
assert.doesNotMatch(screens, /weight = listOf\(185, 120, 185\)\[index\]/);

// Focus must never fork a logger or remove essential exercise/session controls.
assert.match(screens, /\/\/ Focus changes only the presentation; the session note remains available/);
assert.match(screens, /OutlinedTextField\(value = note/);
assert.match(screens, /Text\("Complete set · engine not connected"\)/);
assert.match(screens, /Text\("Generate real program · unavailable"\)/);

// Shared controls have understandable actions and enforce bounded demo inputs.
assert.match(components, /contentDescription = "Decrease \$title"/);
assert.match(components, /contentDescription = "Increase \$title"/);
assert.match(components, /minWidth = 48\.dp, minHeight = 48\.dp/);
assert.match(components, /enabled = canSubtract/);
assert.match(components, /enabled = canAdd/);
assert.match(screens, /canSubtract = reps > 1/);
assert.match(screens, /canSubtract = days > 3/);
assert.match(screens, /canAdd = days < 6/);
assert.match(screens, /Adapt exercises between blocks/);

console.log('PASS native preview context preservation, focus parity and touch accessibility source contracts');
