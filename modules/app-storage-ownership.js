// One live app document may own this origin's training and workout-recovery keys.
// The browser releases the exclusive lock when that document closes, navigates or
// crashes. A second document never hydrates stale state while waiting for retry.
// No training data, merge policy, timers or storage writes live in this module.
export function createAppStorageOwnership(locks = globalThis.navigator?.locks) {
    let required = false, owned = false, pending = null, release = null;
    const unavailable = () => Object.assign(new Error('This browser could not protect your device storage. Use a current browser and try again.'), { code: 'APP_STORAGE_UNAVAILABLE' });
    return {
        require() { required = true; },
        writable() { return !required || owned; },
        acquire() {
            required = true;
            if (pending) return pending;
            if (owned) return Promise.resolve();
            if (!locks?.request) return Promise.reject(unavailable());
            const result = new Promise((resolve, reject) => {
                try {
                    Promise.resolve(locks.request('pursuit-iron:wpb-storage', { mode: 'exclusive', ifAvailable: true }, lock => {
                        if (!lock) {
                            reject(Object.assign(new Error('Pursuit Iron is already open in another tab or app window. Use that window, or close it and try again here.'), { code: 'APP_STORAGE_BUSY' }));
                            return;
                        }
                        owned = true;
                        const held = new Promise(done => { release = done; });
                        resolve();
                        return held.finally(() => { owned = false; release = null; });
                    })).catch(() => { owned = false; reject(unavailable()); });
                } catch { reject(unavailable()); }
            });
            pending = result.finally(() => { pending = null; });
            return pending;
        },
        release() { owned = false; release?.(); }
    };
}

export const appStorageOwnership = createAppStorageOwnership();
// A cached document must hydrate again after returning; its in-memory training
// may predate work done while it was away. Ordinary in-app navigation is unchanged.
globalThis.addEventListener?.('pageshow', event => { if (event.persisted) globalThis.location.reload(); });
