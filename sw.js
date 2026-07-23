/* Workout Builder — service worker
   Precaches the app shell so it loads instantly and works fully offline, and
   serves navigations from the cached shell. A registered SW with a fetch handler
   is what makes the app installable ("Install app") on Android/Chrome. */

const CACHE = "wpb-shell-v439-fb6259ac";
const SHELL = [
  "./",
  "./index.html",
  "./app.js",
  "./app.css",
  "./manifest.webmanifest",
  "./favicon.ico",
  "./apple-touch-icon.png",
  "./icons/icon-192.png",
  "./icons/icon-512.png",
  "./icons/icon-maskable-512.png",
  "./icons/icon.svg",
];

self.addEventListener("install", (event) => {
  // No automatic skipWaiting: the new worker waits until the page tells it to take over
  // (via the in-app "Update ready — Restart" prompt), so users update deliberately instead
  // of needing to force-close the browser to pick up a new version.
  //
  // cache:"reload" on every shell request is load-bearing. updateViaCache:"none" at registration
  // only bypasses the HTTP cache for sw.js ITSELF — the resources fetched in here still obey normal
  // HTTP cache rules. Because ./app.js has a stable URL with no content hash, a plain addAll() could
  // be served the PREVIOUS build's bytes straight from the browser/CDN cache and store them under
  // the NEW cache name. The update then "succeeded" while the app still ran the old build, and the
  // stale copy was pinned until the next deploy. Forcing a network fetch here is what makes the
  // cache name actually correspond to the build inside it.
  event.waitUntil(
    caches.open(CACHE).then((cache) =>
      cache.addAll(SHELL.map((u) => new Request(u, { cache: "reload" })))
    )
  );
});

self.addEventListener("message", (event) => {
  if (event.data && event.data.type === "SKIP_WAITING") self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", (event) => {
  const req = event.request;
  if (req.method !== "GET") return;

  /* Every lookup below is scoped to THIS build's cache via caches.open(CACHE).
     The bare caches.match(req) used before searched every cache in the origin and returns the
     first hit in CREATION order — i.e. the OLDEST cache wins. Any moment both a previous and the
     current shell exist (a new worker precaches while the old one still controls, or an activate
     that failed to finish its cleanup), the stale copy was served in preference to the current
     one, and it stayed sticky. Scoping makes "what this worker serves" and "what this worker
     installed" the same set by construction. */

  // Navigations → serve the cached app shell (SPA), fall back to network.
  if (req.mode === "navigate") {
    event.respondWith(
      caches.open(CACHE)
        .then((c) => c.match("./index.html"))
        .then((cached) => cached || fetch(req))
        .catch(() => caches.open(CACHE).then((c) => c.match("./index.html")))
    );
    return;
  }

  // Everything else → cache-first, then network (and cache the result).
  event.respondWith(
    caches.open(CACHE).then((c) => c.match(req)).then((cached) => {
      if (cached) return cached;
      return fetch(req).then((res) => {
        if (res && res.status === 200 && res.type === "basic") {
          const copy = res.clone();
          caches.open(CACHE).then((cache) => cache.put(req, copy)).catch(() => {});
        }
        return res;
      }).catch(() => cached);
    })
  );
});
