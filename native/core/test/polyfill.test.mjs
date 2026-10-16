// The polyfill must match the native structuredClone on the shapes the engine clones.
// Inputs are built INSIDE the sandbox: objects from another realm fail instanceof checks.
import vm from 'node:vm';
import { readFileSync } from 'node:fs';
const ctx = vm.createContext({});
vm.runInContext(readFileSync(new URL('../src/polyfills.js', import.meta.url), 'utf8'), ctx);
const r = JSON.parse(vm.runInContext(`(() => {
  const a = { n: 1, s: 'x', arr: [1, { y: 2 }, , 4], d: new Date(5), m: new Map([['k', { z: 1 }]]), set: new Set([1, 2]), u: undefined, nul: null, re: /ab+/gi };
  a.self = a; a.shared = [a.arr, a.arr];
  const b = structuredClone(a);
  let threw = false; try { structuredClone({ f() {} }); } catch { threw = true; }
  return JSON.stringify({
    cycle: b !== a && b.self === b,
    shared: b.shared[0] === b.shared[1] && b.shared[0] !== a.arr,
    date: b.d instanceof Date && b.d.getTime() === 5 && b.d !== a.d,
    map: b.m instanceof Map && b.m.get('k').z === 1 && b.m.get('k') !== a.m.get('k'),
    set: b.set instanceof Set && b.set.has(2) && b.set.size === 2,
    holes: !(2 in b.arr) && b.arr.length === 4,
    undef: 'u' in b && b.u === undefined,
    regex: b.re instanceof RegExp && b.re.source === 'ab+' && b.re.flags === 'gi',
    fn: threw
  });
})()`, ctx));
let fails = 0;
for (const [k, v] of Object.entries(r)) { console.log((v ? 'PASS ' : 'FAIL ') + k); if (!v) fails++; }
console.log(fails ? fails + ' FAILED' : 'ALL PASS'); process.exit(fails ? 1 : 0);
