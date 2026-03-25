const CACHE = "feeddrop-v1";
const PRECACHE = ["./index.html", "./manifest.json"];

// Install – cache shell
self.addEventListener("install", (e) => {
  e.waitUntil(
    caches
      .open(CACHE)
      .then((c) => c.addAll(PRECACHE))
      .then(() => self.skipWaiting()),
  );
});

// Activate – clean old caches
self.addEventListener("activate", (e) => {
  e.waitUntil(
    caches
      .keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim()),
  );
});

// Fetch – network-first for API calls, cache-first for app shell
self.addEventListener("fetch", (e) => {
  const url = new URL(e.request.url);

  // Let FreshRSS API calls go straight to network
  if (!url.origin.includes(self.location.origin)) {
    return; // don't intercept cross-origin (API) requests
  }

  // App shell: cache-first
  e.respondWith(
    caches.match(e.request).then((cached) => {
      if (cached) return cached;
      return fetch(e.request).then((res) => {
        if (res && res.status === 200 && res.type === "basic") {
          const clone = res.clone();
          caches.open(CACHE).then((c) => c.put(e.request, clone));
        }
        return res;
      });
    }),
  );
});
