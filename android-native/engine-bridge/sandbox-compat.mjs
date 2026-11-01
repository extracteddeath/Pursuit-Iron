/**
 * AndroidX isolated JS doesn't provide the Web structuredClone global.
 * The canonical loading defaults need copies of PLAIN inventory data only.
 * Never replace a real structuredClone or emulate exotic object classes.
 */
export function clonePlainSandboxData(value) {
  const seen = new Map();
  let visited = 0;
  function clone(input, depth) {
    if (++visited > 20000 || depth > 48)
      throw new TypeError('Sandbox clone exceeds bounded data limits');
    if (input === null || typeof input === 'string' ||
        typeof input === 'boolean' || typeof input === 'number' ||
        typeof input === 'bigint' || typeof input === 'undefined') return input;
    if (typeof input !== 'object')
      throw new TypeError('Sandbox clone supports data only');
    if (seen.has(input)) return seen.get(input);
    const prototype = Object.getPrototypeOf(input);
    if (!Array.isArray(input) && prototype !== null && prototype !== Object.prototype)
      throw new TypeError('Sandbox clone rejects non-plain records');
    const result = Array.isArray(input) ? new Array(input.length)
      : Object.create(prototype);
    seen.set(input, result);
    for (const key of Reflect.ownKeys(input)) {
      if (Array.isArray(input) && key === 'length') continue;
      if (typeof key !== 'string')
        throw new TypeError('Sandbox clone rejects symbol properties');
      const descriptor = Object.getOwnPropertyDescriptor(input,key);
      if (!descriptor || !Object.prototype.hasOwnProperty.call(descriptor,'value'))
        throw new TypeError('Sandbox clone rejects accessor properties');
      Object.defineProperty(result,key,{
        value: clone(descriptor.value,depth+1),
        enumerable:descriptor.enumerable,writable:true,configurable:true
      });
    }
    return result;
  }
  return clone(value,0);
}

export function installCanonicalSandboxCompat(environment = globalThis) {
  if (typeof environment.structuredClone === 'function') return 'native';
  if (Object.prototype.hasOwnProperty.call(environment,'structuredClone') &&
      environment.structuredClone !== undefined)
    throw new TypeError('Untrusted structuredClone implementation');
  Object.defineProperty(environment,'structuredClone',{
    value:clonePlainSandboxData,writable:false,configurable:false
  });
  return 'plain-data-only';
}
