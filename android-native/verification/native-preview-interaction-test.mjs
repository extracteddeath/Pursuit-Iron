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
assert.equal((screens.match(/PreviewSettingSwitch\\(/g) || []).length, 2,
  'Plan adaptation switches must be composed from one shared native control');
assert.match(components, /toggleable\\(value = checked, role = Role\\.Switch/,
  'The entire row, not only the switch thumb, owns the accessible action');
assert.match(components, /Switch\\(checked = checked, onCheckedChange = null\\)/,
  'Avoid a second nested switch click owner');
assert.match(components, /modifier = Modifier\\.weight\\(1f\\)/,
  'Section headings must yield room for narrow-screen trailing labels');
assert.match(components, /overflow = TextOverflow\\.Ellipsis/,
  'Compact screen section metadata must not crowd the title');

const outline = fs.readFileSync(root + 'WorkoutOutlinePanel.kt', 'utf8');
const outlinePolicy = fs.readFileSync(
  'android-native/domain-contract/src/main/java/io/github/extracteddeath/pursuitiron/domain/OutlinePresentation.kt',
  'utf8'
);
const nativeUiWorkflow = fs.readFileSync(
  '.github/workflows/android-compose-ui-compile.yml', 'utf8'
);
assert.match(screens, /WorkoutOutlinePanel\(outline = null\)/,
  'Native Plan must default to unconnected read-only outline');
assert.ok(outline.includes('OutlinePresentationPolicy.inspect(outline)'),
  'Native outline must pass through the presentation gate');
assert.ok(outline.includes('ILLUSTRATIVE UI ONLY · NOT GENERATED OR SAVED'),
  'Layout-only data must not be mislabeled as canonical workout data');
assert.ok(outline.includes('SYNTHETIC ENGINE TEST · NOT YOUR PROGRAM'),
  'No synthetic engine session may be presented as a real user session');
assert.ok(outlinePolicy.includes('!outline.readOnly || !outline.syntheticOnly'),
  'Synthetic-only and read-only guards must both be required');
assert.ok(!outline.includes('deleteHistory(') &&
  !outline.includes('generateNextProgramForShell(') &&
  !outline.includes('previewAsync('),
  'Composable cannot directly mutate user data or invoke the training engine');
assert.match(nativeUiWorkflow, /:app:compileDebugKotlin/);
assert.doesNotMatch(nativeUiWorkflow, /assembleDebug/,
  'Routine Compose source commits must not assemble a preview APK');

console.log('PASS native preview context preservation, focus parity and touch accessibility source contracts');
