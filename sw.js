/* Workout Builder — service worker
   Precaches the app shell so it loads instantly and works fully offline, and
   serves navigations from the cached shell. A registered SW with a fetch handler
   is what makes the app installable ("Install app") on Android/Chrome. */

const CACHE = "wpb-shell-v432-8124db8c";
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
  event.waitUntil(caches.open(CACHE).then((cache) => cache.addAll(SHELL)));
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

  // Navigations → serve the cached app shell (SPA), fall back to network.
  if (req.mode === "navigate") {
    event.respondWith(
      caches.match("./index.html").then((cached) => cached || fetch(req)).catch(() => caches.match("./index.html"))
    );
    return;
  }

  // Everything else → cache-first, then network (and cache the result).
  event.respondWith(
    caches.match(req).then((cached) => {
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
